# F17: StringTemplate (ST4) Rendering

**Status:** Future Enhancement
**Priority:** Low (string interpolation covers most cases)
**Related:** [F14: FreeMarker](F14-freemarker-template-rendering.md), [F16: Mustache](F16-mustache-template-rendering.md)
**Created:** May 2026

---

## Summary

Add a `string-template-action` that renders [StringTemplate 4 (ST4)](https://www.stringtemplate.org/) templates within UTL-X transformations. ST4 is a strictly model-view separated template engine created by Terence Parr (the author of ANTLR). While UTL-X string interpolation covers basic cases, ST4 is the standard for code generation and is deeply embedded in ANTLR-based toolchains.

## Background

StringTemplate enforces strict separation between logic and presentation — even stricter than Mustache. The template cannot modify the data model, perform calculations, or execute side effects. This makes templates provably side-effect free.

### ST4 Syntax

```
// Variable substitution
Hello <name>!

// Attribute expression with format
<price; format="%.2f">

// Conditional
<if(premium)>Welcome VIP<elseif(member)>Welcome back<else>Please register<endif>

// Iteration
<items:{item | <item.name>: <item.price>}; separator=", ">

// Template include
<header()>
<body>
<footer()>

// Map (apply template to each element)
<items:itemRow()>
```

Delimiters are configurable: `<...>` (default) or `$...$` (useful when generating XML/HTML).

### ST4 vs Mustache vs FreeMarker

| Aspect | ST4 (F17) | Mustache (F16) | FreeMarker (F14) |
|--------|-----------|----------------|------------------|
| Philosophy | Strict model-view separation | Logic-less | Full-featured |
| Author/Origin | Terence Parr (ANTLR) | Chris Wanstrath (GitHub) | Apache Foundation |
| Primary use case | **Code generation** | Simple text templates | Complex rendering |
| Side effects | Impossible by design | Impossible by design | Possible (`?new`, assigns) |
| Conditionals | `<if>/<elseif>/<else>` | `{{#flag}}/{{^flag}}` only | Full `<#if>` expressions |
| Iteration | `<items:{x \| ...}>` | `{{#items}}...{{/items}}` | `<#list items as x>` |
| Formatting | `<x; format="...">` | None | Rich built-ins |
| Template groups | Yes (`.stg` files) | Partials only | Macros + includes |
| Template inheritance | Yes | No | Yes |
| Indentation control | **Auto-indentation** | None | Manual whitespace |
| Cross-platform | Java, C#, Python, JS | 50+ languages | JVM only |
| Size | ~300 KB | ~100 KB | ~1.7 MB |

### Why ST4 Matters

ST4's killer feature is **auto-indentation**: when a template call is indented, all output from that template is indented to match. This is essential for generating correctly formatted source code, XML, or YAML — something Mustache and FreeMarker handle poorly.

```
// Template: classBody
<methods:{m | public void <m.name>() {
    // TODO
\}}; separator="\n\n">

// When called with 4-space indent, output is:
    public void doSomething() {
        // TODO
    }

    public void doOther() {
        // TODO
    }
```

### Where ST4 Is Used

- **ANTLR** — all code generation targets (Java, Python, C#, Go, etc.) use ST4
- **Compiler/parser generators** — DSL tooling, language workbenches
- **OpenAPI/Swagger Codegen** — some generators use ST4 alongside Mustache
- **Database tooling** — SQL generation, migration scripts
- **Enterprise code generators** — DTO/POJO generation from schemas

## What UTL-X Can Do Today

```
// Simple interpolation
"Hello " + $input.name + "!"

// formatString
formatString("%-20s %10.2f", $input.name, $input.price)

// Iteration
join($input.items[*].(.name + ": " + string(.price)), ", ")

// Conditional
if $input.premium then "VIP" else "Standard"
```

### What's Missing

| ST4 Feature | UTL-X Equivalent | Gap |
|-------------|------------------|-----|
| `<variable>` | String concat / `formatString` | Verbose for complex templates |
| `<items:{x \| ...}>` | `[*].()` | Different syntax, no separator control |
| `<if>/<elseif>/<else>` | `if/then/else` | Not embeddable in template text |
| Template groups (`.stg`) | No equivalent | Can't reuse ST4 template libraries |
| Auto-indentation | No equivalent | **Critical gap for code generation** |
| Template inheritance | No equivalent | No template composition system |
| `<x; format="...">` | `formatNumber`, `formatDate` | Not inline in templates |
| `; separator=","` | `join()` | Different ergonomics |

The core gaps: **auto-indentation** for code generation and **`.stg` file reuse** from ANTLR-based toolchains.

## Proposed Functions

### Core

```
renderST(template, data)                         -> rendered string
renderST(template, data, options?)               -> rendered string
renderSTFile(templatePath, templateName, data)    -> rendered string
```

### Template Groups

```
renderSTGroup(groupString, templateName, data)    -> rendered string
renderSTGroupFile(groupPath, templateName, data)  -> rendered string
```

ST4 template groups (`.stg`) bundle related templates:

```
// templates/java.stg
classDecl(name, fields) ::= <<
public class <name> {
    <fields:fieldDecl(); separator="\n">
}
>>

fieldDecl(f) ::= <<
private <f.type> <f.name>;
>>
```

### Options

- `startDelimiter` / `endDelimiter` — default `<` / `>`, set to `$` / `$` for XML/HTML output
- `encoding` — output encoding (default: UTF-8)

## UTL-X Integration Examples

### Code generation from schema

```
%input json   // JSON Schema or parsed XSD
%output text
---
let model = {
  className: $input.title,
  fields: entries($input.properties)[*].{
    name: .key,
    type: .value.type,
    javaType: if .value.type == "string" then "String"
              else if .value.type == "integer" then "int"
              else "Object"
  }
}

renderST(
  "public class <className> {\n<fields:{f |    private <f.javaType> <f.name>;\n}>}",
  model
)
```

### Reuse ANTLR-style template group

```
%input json
%output text
---
renderSTGroupFile("templates/java-pojo.stg", "classDecl", {
  className: $input.name,
  fields: $input.fields,
  package: "com.example.generated"
})
```

### SQL generation with dollar delimiters

```
%input json
%output text
---
renderST(
  "INSERT INTO $table$ ($columns:{c | $c$}; separator=\", \"$)\nVALUES ($values:{v | '$v$'}; separator=\", \"$);",
  $input,
  { startDelimiter: "$", endDelimiter: "$" }
)
```

### XML generation (dollar delimiters avoid conflict with `<`/`>`)

```
%input json
%output text
---
renderST(
  "<?xml version=\"1.0\"?>\n<root>\n$items:{item | <entry name=\"$item.name$\">$item.value$</entry>\n}$</root>",
  $input,
  { startDelimiter: "$", endDelimiter: "$" }
)
```

## Implementation

### Module Location

Add to `stdlib-templates` (alongside F14 FreeMarker and/or F16 Mustache if they exist).

If `stdlib-templates` doesn't exist yet, this is a good reason to create it — ST4 at ~300 KB is small but specialized.

### Dependencies

```kotlin
// ST4 — the official StringTemplate 4 library by Terence Parr
implementation("org.antlr:ST4:4.3.4")
```

ST4 has one transitive dependency: `antlr-runtime` (~180 KB). If ANTLR is already in use for UTL-X grammar parsing, this may already be present.

### Architecture

```
UDM data model
     |
     v
UDM -> Map/List converter (recursive, same as Mustache F16)
     |
     v
ST / STGroup compilation
     |
     v
st.add(attributes) -> st.render()
     |
     v
UDM.Scalar (rendered text)
```

### UDM to ST4 Data Model

```kotlin
fun udmToSTModel(udm: UDM): Any? = when (udm) {
    is UDM.Scalar -> udm.value
    is UDM.Object -> udm.properties.mapValues { udmToSTModel(it.value) }
    is UDM.Array  -> udm.elements.map { udmToSTModel(it) }
    else -> null
}
```

Identical to the Mustache converter (F16) — ST4 also works with plain `Map`/`List`/primitives.

### GraalVM Native Image

| Risk | Detail |
|------|--------|
| Medium | ST4 uses ANTLR runtime internally for template parsing, which uses some reflection |
| Mitigation | Register ST4 internal classes in `reflect-config.json`. Limited set — well-known. |
| Alternative | JVM/engine-only initially if native image issues arise |

### Security Considerations

- **No code execution:** ST4 enforces strict model-view separation — templates cannot call arbitrary methods, instantiate objects, or perform I/O
- **Provably safe:** This is ST4's core design principle, making it the safest of the three template engines (F14, F16, F17)
- **File access:** `renderSTFile` / `renderSTGroupFile` must restrict paths to project/template directory
- **No side effects:** Templates are pure functions from data model to string

## When to Use Which Template Engine

| Scenario | Recommendation |
|----------|---------------|
| Simple string building | UTL-X native (`+`, `formatString`) |
| **Code generation** with auto-indentation | **F17 — StringTemplate** (auto-indent) |
| Reuse `.stg` files from ANTLR toolchains | **F17 — StringTemplate** |
| Complex rendering with macros, i18n, formatting | [F14 — FreeMarker](F14-freemarker-template-rendering.md) (power) |
| Portable templates shared across JS/Python/Go/Java | [F16 — Mustache](F16-mustache-template-rendering.md) (simplicity) |
| Reuse legacy `.vm` files from ESB/Atlassian/Maven | [F18 — Velocity](F18-velocity-template-rendering.md) (migration) |
| Structured data -> structured data | UTL-X native mapping |

## Effort Estimate

| Task | Effort |
|------|--------|
| UDM-to-ST4 model converter | 0.5 day |
| `renderST` / `renderSTFile` | 1 day |
| Template group support (`renderSTGroup` / `renderSTGroupFile`) | 1 day |
| Configurable delimiters | 0.5 day |
| GraalVM compatibility | 1 day |
| Tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **5-6 days** |

## See Also

- [StringTemplate 4 documentation](https://www.stringtemplate.org/)
- [ST4 GitHub repository](https://github.com/antlr/stringtemplate4)
- [Terence Parr — "Enforcing Strict Model-View Separation in Template Engines"](https://www.cs.usfca.edu/~parrt/papers/mvc.templates.pdf)
- [F14: FreeMarker Template Rendering](F14-freemarker-template-rendering.md) — full-featured alternative
- [F16: Mustache Template Rendering](F16-mustache-template-rendering.md) — logic-less alternative
- [F18: Velocity Template Rendering](F18-velocity-template-rendering.md) — legacy template reuse
- [stdlib function reference](../reference/stdlib-reference.md)

---

*Feature F17. May 2026.*
