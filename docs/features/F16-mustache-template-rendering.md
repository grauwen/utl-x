# F16: Mustache Template Rendering

**Status:** Future Enhancement
**Priority:** Low (string interpolation covers most cases)
**Related:** [F14: FreeMarker Template Rendering](F14-freemarker-template-rendering.md)
**Created:** May 2026

---

## Summary

Add a `mustache-template-action` that renders [Mustache](https://mustache.github.io/) templates within UTL-X transformations. Mustache is a logic-less template language available in 50+ languages. While UTL-X string interpolation covers most simple cases, Mustache adds sections (loops/conditionals), partials (includes), and cross-platform template compatibility.

## Background

Mustache is a logic-less template specification — no `if/else`, no `for` loops in the traditional sense. Instead it uses tags:

```mustache
Hello {{name}}!

{{#items}}
  - {{description}}: {{price}}
{{/items}}

{{^items}}
  No items found.
{{/items}}

{{> header}}
```

### Mustache vs FreeMarker (F14)

| Aspect | Mustache | FreeMarker (F14) |
|--------|----------|------------------|
| Philosophy | Logic-less, minimal | Full-featured, powerful |
| Syntax complexity | Very simple (5 tag types) | Rich (macros, built-ins, directives) |
| Learning curve | Minutes | Hours |
| Cross-platform | 50+ language implementations | JVM only |
| Template reuse | Same `.mustache` files work in JS, Python, Go, etc. | Java/Kotlin only |
| Loops | `{{#list}}...{{/list}}` | `<#list items as item>` |
| Conditionals | `{{#flag}}` / `{{^flag}}` (truthy/falsy only) | `<#if>`, `<#elseif>`, `<#else>` |
| Formatting | None built-in | Number, date, string formatting |
| Size | ~100 KB | ~1.7 MB |

**Rule of thumb:** Use Mustache for simple, portable templates. Use FreeMarker (F14) for complex rendering with formatting, macros, and logic.

### Why Mustache Matters for Integration

Many systems already use Mustache templates:
- **GitHub** — issue/PR templates, Actions workflow outputs
- **Swagger/OpenAPI** — code generators (Swagger Codegen, OpenAPI Generator)
- **Kubernetes** — Helm charts use a Mustache-derived syntax (Go templates)
- **Email platforms** — Mailchimp, SendGrid, Postmark
- **CMS systems** — Hugo, Ghost
- **CI/CD** — GitLab CI, Drone

UTL-X as an integration engine often bridges these systems. Being able to render their templates natively avoids round-trips.

## What UTL-X Can Do Today

String interpolation and mapping cover basic cases:

```
// Simple variable substitution
"Hello " + $input.name + "!"

// With formatString
formatString("Hello %s, you have %d items", $input.name, size($input.items))

// Conditional
if $input.items then "Items found" else "No items"

// Iteration
$input.items[*].("- " + .description + ": " + .price)
```

### What's Missing

| Mustache Feature | UTL-X Equivalent | Gap |
|-----------------|------------------|-----|
| `{{variable}}` | String concatenation / `formatString` | Verbose for complex templates |
| `{{#section}}` (loop) | `[*].()` array mapping | Different syntax, not template-based |
| `{{#section}}` (conditional) | `if/then/else` | Not embeddable in template text |
| `{{^inverted}}` | `if not` | Not embeddable in template text |
| `{{> partial}}` | No equivalent | No template include system |
| `{{! comment}}` | `//` comments | Different context (code vs template) |
| Template files (`.mustache`) | No equivalent | Can't reuse templates from other systems |
| HTML escaping | `escapeXml()` / manual | Mustache auto-escapes by default |

The core gap: UTL-X can produce the same output, but not by consuming existing `.mustache` template files from other systems.

## Proposed Functions

### Core

```
renderMustache(template, data)                   -> rendered string
renderMustacheFile(templatePath, data)            -> rendered string
```

### With Partials

```
renderMustache(template, data, partials?)         -> rendered string
```

Where `partials` is an object mapping partial names to template strings:
```
renderMustache(
  "{{> header}} Hello {{name}} {{> footer}}",
  $input,
  { header: "<html><body>", footer: "</body></html>" }
)
```

### Compile (for repeated use)

```
compileMustache(template)                         -> compiled template ref
renderCompiled(compiledRef, data)                  -> rendered string
```

## UTL-X Integration Examples

### Simple variable rendering

```
%input json
%output text
---
renderMustache("Hello {{firstName}} {{lastName}}, welcome to {{company}}.", $input)
```

### List rendering

```
%input json
%output text
---
renderMustache(
  "Order #{{orderId}}\n{{#items}}\n  - {{name}}: {{qty}} x {{price}}\n{{/items}}\nTotal: {{total}}",
  $input
)
```

### Consuming templates from external systems

```
%input json
%output text
---
// Reuse existing Mustache templates from an OpenAPI code generator
renderMustacheFile("templates/api-client.mustache", {
  className: $input.operationId,
  methods: $input.paths[*].{
    name: .operationId,
    httpMethod: .method,
    path: .path,
    parameters: .parameters
  }
})
```

### Conditional and inverted sections

```
%input json
%output text
---
renderMustache(
  "{{#premium}}Welcome back, VIP!{{/premium}}{{^premium}}Sign up for premium.{{/premium}}",
  $input
)
```

### With partials from files

```
%input json
%output text
---
renderMustacheFile("templates/email.mustache", $input, {
  header: readFile("templates/partials/header.mustache"),
  footer: readFile("templates/partials/footer.mustache")
})
```

## Implementation

### Module Location

Two options, depending on whether F14 (FreeMarker) is implemented first:

| Scenario | Location |
|----------|----------|
| F14 exists (`stdlib-templates`) | Add Mustache to `stdlib-templates` alongside FreeMarker |
| F14 not yet implemented | Add to `stdlib` — Mustache is tiny (~100 KB), no heavy dependencies |

Recommended: Add to `stdlib-templates` if it exists, otherwise `stdlib`.

### Dependencies

```kotlin
// JMustache — lightweight, zero-dependency Mustache for Java
implementation("com.samskivert:jmustache:1.16")
```

Alternative: [Mustache.java](https://github.com/spullara/mustache.java) by spullara — more features but heavier.

JMustache is preferred: single JAR, ~100 KB, no transitive dependencies.

### Architecture

```
UDM data model
     |
     v
UDM -> Map<String, Object> converter (recursive)
     |
     v
JMustache Template.compile(templateString)
     |
     v
template.execute(dataMap) -> String
     |
     v
UDM.Scalar (rendered text)
```

### UDM to Mustache Data Model

```kotlin
fun udmToMustacheModel(udm: UDM): Any? = when (udm) {
    is UDM.Scalar -> udm.value
    is UDM.Object -> udm.properties.mapValues { udmToMustacheModel(it.value) }
    is UDM.Array  -> udm.elements.map { udmToMustacheModel(it) }
    else -> null
}
```

Mustache operates on plain `Map`/`List`/`String` — simpler than FreeMarker's `TemplateModel` interface.

### GraalVM Native Image

| Risk | Detail |
|------|--------|
| Low | JMustache is reflection-free. Template compilation is string-based. |
| No config needed | No `reflect-config.json` or `resource-config.json` entries required |

Mustache is one of the safest template libraries for native image compilation.

### Security Considerations

- **No code execution:** Mustache is logic-less — no arbitrary code execution risk (unlike FreeMarker's `?new`)
- **File access:** `renderMustacheFile` must restrict paths to project/template directory
- **Auto-escaping:** HTML escaping is on by default (`{{var}}`). Triple-stache `{{{var}}}` disables escaping — document this clearly
- **Partial injection:** Partials are explicitly provided, not resolved from filesystem by default

## Relation to F14 (FreeMarker) and Native UTL-X

| Need | Best approach |
|------|--------------|
| Simple string building | UTL-X string concatenation / `formatString` |
| Reuse `.mustache` files from other systems | **F16 — Mustache** |
| Portable templates shared across JS/Python/Go/Java | **F16 — Mustache** |
| Complex rendering with formatting, macros, i18n | [F14 — FreeMarker](F14-freemarker-template-rendering.md) |
| Structured data -> structured data | UTL-X native mapping |

F14 and F16 are complementary, not competing. If only one is implemented, Mustache is simpler and more portable; FreeMarker is more powerful.

## Template Engine Decision Matrix

UTL-X offers four template engine integrations, each with a clear use case:

| Need | Recommendation |
|------|---------------|
| Simple string building | UTL-X native (`+`, `formatString`) |
| **Portable templates** shared across JS/Python/Go/Java | **F16 — Mustache** (simplicity) |
| Reuse `.mustache` files from GitHub/Helm/email systems | **F16 — Mustache** |
| Complex rendering with macros, i18n, formatting | [F14 — FreeMarker](F14-freemarker-template-rendering.md) (power) |
| **Code generation** with auto-indentation | [F17 — StringTemplate](F17-stringtemplate-rendering.md) (auto-indent) |
| Reuse legacy `.vm` files from ESB/Atlassian/Maven | [F18 — Velocity](F18-velocity-template-rendering.md) (migration) |
| Structured data -> structured data | UTL-X native mapping |

## Effort Estimate

| Task | Effort |
|------|--------|
| UDM-to-Mustache model converter | 0.5 day |
| `renderMustache` / `renderMustacheFile` | 1 day |
| Partials support | 0.5 day |
| Security hardening (path restriction) | 0.5 day |
| Tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **3-4 days** |

## See Also

- [Mustache specification](https://mustache.github.io/)
- [JMustache (Java implementation)](https://github.com/samskivert/jmustache)
- [F14: FreeMarker Template Rendering](F14-freemarker-template-rendering.md) — full-featured alternative
- [F17: StringTemplate Rendering](F17-stringtemplate-rendering.md) — code generation focus
- [F18: Velocity Template Rendering](F18-velocity-template-rendering.md) — legacy template reuse
- [stdlib function reference](../reference/stdlib-reference.md)

---

*Feature F16. May 2026.*
