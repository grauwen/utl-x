# F18: Velocity Template Rendering

**Status:** Will not implement — `$` syntax conflict with UTL-X  
**Priority:** ~~Low~~ N/A  
**Related:** [F14: FreeMarker](F14-freemarker-template-rendering.md) (same conflict), [F16: Mustache](F16-mustache-template-rendering.md) (no conflict)  
**Created:** May 2026  
**Decision:** May 2026

---

## Summary

Add a `velocity-template-action` that renders [Apache Velocity](https://velocity.apache.org/) templates within UTL-X transformations. Velocity is a legacy-but-still-active Java template engine. While FreeMarker (F14) is its modern successor, Velocity templates remain widespread in enterprise systems, ESBs, and code generators that UTL-X may need to integrate with.

## Background

Apache Velocity (VTL — Velocity Template Language) was the dominant Java template engine from 2001–2012 before FreeMarker superseded it. It remains in active maintenance (Velocity 2.x, latest 2.4, 2024) and is embedded in many enterprise tools.

### VTL Syntax

```velocity
## Variable substitution
Hello $customer.name!

## Quiet reference (no output if null)
Hello $!{customer.middleName}

## Conditional
#if($order.total > 1000)
  Premium order!
#elseif($order.total > 100)
  Standard order.
#else
  Small order.
#end

## Loop
#foreach($item in $order.items)
  - $item.name: $item.qty x $item.price
#end

## Set variable
#set($discount = $order.total * 0.1)

## Include / Parse
#include("header.txt")
#parse("menu.vm")

## Macro
#macro(tableRow $item)
  <tr><td>$item.name</td><td>$item.value</td></tr>
#end
```

### Velocity vs FreeMarker vs Mustache vs ST4

| Aspect | Velocity (F18) | FreeMarker (F14) | Mustache (F16) | ST4 (F17) |
|--------|---------------|------------------|----------------|-----------|
| Era | 2001–present | 2004–present | 2009–present | 2003–present |
| Status | Maintenance mode | Actively developed | Stable spec | Stable |
| Philosophy | Simple scripting in templates | Full-featured | Logic-less | Strict separation |
| Syntax | `$var`, `#if`, `#foreach` | `${var}`, `<#if>`, `<#list>` | `{{var}}`, `{{#section}}` | `<var>`, `<items:{}>` |
| Logic in templates | Yes (`#set`, expressions) | Yes (rich) | No | No |
| Null handling | Quiet refs `$!{var}` | `!` operator, `??` | Falsy = no output | No output if missing |
| Macros | `#macro` | `<#macro>` | Partials only | Sub-templates |
| Size | ~500 KB | ~1.7 MB | ~100 KB | ~300 KB |
| JVM only | Yes | Yes | No (50+ langs) | No (Java, C#, Python, JS) |

### Where Velocity Is Still Used

| System | Usage |
|--------|-------|
| **Apache James** | Email templates |
| **Apache Turbine** | Web framework (legacy) |
| **Maven Archetypes** | Project template generation |
| **Confluence** | User macros and email notifications |
| **JIRA / Atlassian** | Email templates, notification formatting |
| **MuleSoft / Anypoint** | DataWeave alternative for simple templating |
| **WSO2 ESB** | Mediation templates |
| **Spring (legacy)** | View layer (before Thymeleaf) |
| **Code generators** | MyBatis Generator, JOOQ, older Swagger codegen |
| **SAP** | Some integration flows use Velocity for message formatting |

### Why Support Velocity?

The primary argument is **legacy template reuse**, not new development:

| Reason | Detail |
|--------|--------|
| **Migration path** | Organizations moving from MuleSoft/WSO2/legacy ESBs to UTL-X may have hundreds of `.vm` templates |
| **Atlassian ecosystem** | Confluence/JIRA notification templates are Velocity — UTL-X integrating with Atlassian can reuse them |
| **Maven archetypes** | UTL-X project scaffolding could consume existing Maven archetype templates |
| **Coexistence** | During migration, run existing Velocity templates unchanged while gradually rewriting in UTL-X |

## What UTL-X Can Do Today

```
// Variable substitution
"Hello " + $input.customer.name + "!"

// Conditional
if $input.order.total > 1000 then "Premium" else "Standard"

// Loop with separator
join($input.order.items[*].("- " + .name + ": " + string(.qty) + " x " + string(.price)), "\n")

// Null-safe access
$input.customer.middleName ?? ""
```

### What's Missing

| Velocity Feature | UTL-X Equivalent | Gap |
|-----------------|------------------|-----|
| `$variable` | String concat | Verbose |
| `$!{var}` (quiet null) | `?? ""` | Different syntax |
| `#foreach` | `[*].()` | Not template-embeddable |
| `#if/#elseif/#else` | `if/then/else` | Not template-embeddable |
| `#set` | `let` | Different context |
| `#macro` | No inline macro equivalent | No template macro system |
| `#parse` / `#include` | No equivalent | No template includes |
| `.vm` file reuse | Not possible | **Core gap** |

## Proposed Functions

### Core

```
renderVelocity(template, data)                   -> rendered string
renderVelocityFile(templatePath, data)            -> rendered string
```

### With Context Variables

```
renderVelocity(template, data, contextVars?)     -> rendered string
```

Where `contextVars` adds extra variables to the Velocity context beyond the main data model:

```
renderVelocity(
  "Generated by $tool on $date\n#foreach($item in $items)\n$item.name\n#end",
  $input,
  { tool: "UTL-X", date: now() }
)
```

### Options

```
renderVelocity(template, data, contextVars?, options?)
```

- `resourcePath` — directory for `#parse` / `#include` resolution
- `encoding` — output encoding (default: UTF-8)
- `strictMode` — fail on undefined references instead of rendering empty (default: false)

## UTL-X Integration Examples

### Simple variable rendering

```
%input json
%output text
---
renderVelocity("Dear $customer.name,\n\nYour order #$order.id has been $order.status.", $input)
```

### Loop and conditional

```
%input json
%output text
---
renderVelocity(
  "#if($order.items.size() > 0)\nOrder contains:\n#foreach($item in $order.items)\n  - $item.name ($item.qty)\n#end\n#else\nEmpty order.\n#end",
  $input
)
```

### Reuse existing Velocity templates from legacy ESB

```
%input json
%output text
---
// Reuse existing MuleSoft/WSO2 Velocity templates during migration
renderVelocityFile("legacy-templates/notification-email.vm", {
  customer: $input.customer,
  order: $input.order,
  supportUrl: $config.supportUrl
})
```

### Maven archetype template

```
%input json
%output text
---
renderVelocityFile("archetype-resources/pom.xml.vm", {
  groupId: $input.groupId,
  artifactId: $input.artifactId,
  version: $input.version
})
```

## Implementation

### Module Location

`stdlib-templates` (alongside F14/F16/F17 if they exist).

### Dependencies

```kotlin
// Apache Velocity Engine 2.x
implementation("org.apache.velocity:velocity-engine-core:2.4")
```

Velocity Engine 2.x has minimal transitive dependencies: `slf4j-api` and `commons-lang3` (both likely already present in UTL-X).

### Architecture

```
UDM data model
     |
     v
VelocityContext population (recursive UDM -> Java objects)
     |
     v
Velocity Engine evaluate/mergeTemplate
     |
     v
StringWriter -> UDM.Scalar (rendered text)
```

### UDM to Velocity Context

```kotlin
fun populateContext(context: VelocityContext, udm: UDM, rootKey: String? = null) {
    when (udm) {
        is UDM.Object -> {
            if (rootKey != null) {
                context.put(rootKey, udmToMap(udm))
            } else {
                // Spread top-level properties as context variables
                udm.properties.forEach { (key, value) ->
                    context.put(key, udmToJava(value))
                }
            }
        }
        else -> if (rootKey != null) context.put(rootKey, udmToJava(udm))
    }
}

fun udmToJava(udm: UDM): Any? = when (udm) {
    is UDM.Scalar -> udm.value
    is UDM.Object -> udm.properties.mapValues { udmToJava(it.value) }
    is UDM.Array  -> udm.elements.map { udmToJava(it) }
    else -> null
}
```

### GraalVM Native Image

| Risk | Detail |
|------|--------|
| Medium | Velocity uses reflection for method/property access on context objects |
| Mitigation | Since UTL-X passes `Map`/`List`/primitives (not arbitrary POJOs), reflection targets are limited and known |
| Config needed | Register `java.util.HashMap`, `java.util.ArrayList`, standard wrappers in `reflect-config.json` |

### Security Considerations

- **Method access:** Velocity can call methods on context objects (`$runtime.exec()`). Must use `SecureUberspector` or a custom `Uberspect` that restricts method calls to safe operations (`.size()`, `.isEmpty()`, `.get()`, property access only).
- **`#include` / `#parse`:** Must restrict to a configured template directory. Block path traversal (`../`).
- **`#set` with side effects:** Velocity's `#set` is local to the template — no external side effects. Safe.
- **Class loading:** Disable `Class.forName` and `Runtime.exec` via the Uberspect security layer.

```kotlin
// Secure configuration
val velocity = VelocityEngine()
velocity.setProperty("introspector.uberspect.class",
    "org.apache.velocity.util.introspection.SecureUberspector")
velocity.setProperty("introspector.restrict.packages",
    "java.lang.reflect,java.lang.Runtime,java.io,java.net")
velocity.setProperty("introspector.restrict.classes",
    "java.lang.Class,java.lang.System,java.lang.Runtime")
```

## Migration Story

Velocity support positions UTL-X as a migration target for legacy integration platforms:

```
Legacy ESB (MuleSoft/WSO2/Spring Integration)
  └── Velocity templates (.vm files)
       │
       ├── Phase 1: Reuse as-is via renderVelocityFile()
       │
       ├── Phase 2: Gradually rewrite simple templates as UTL-X native
       │
       └── Phase 3: Complex templates -> FreeMarker (F14) or native UTL-X
```

This reduces migration risk — no need to rewrite all templates on day one.

## Template Engine Decision Matrix

| Need | Recommendation |
|------|---------------|
| Simple string building | UTL-X native (`+`, `formatString`) |
| **Reuse legacy `.vm` files** from ESB/Atlassian/Maven | **F18 — Velocity** |
| Portable templates (multi-language) | [F16 — Mustache](F16-mustache-template-rendering.md) |
| Code generation with auto-indentation | [F17 — StringTemplate](F17-stringtemplate-rendering.md) |
| Complex rendering with macros, i18n, formatting | [F14 — FreeMarker](F14-freemarker-template-rendering.md) |
| New template development on JVM | [F14 — FreeMarker](F14-freemarker-template-rendering.md) (not Velocity) |

## Effort Estimate

| Task | Effort |
|------|--------|
| UDM-to-Velocity context converter | 0.5 day |
| `renderVelocity` / `renderVelocityFile` | 1 day |
| Security hardening (SecureUberspector, path restriction) | 1 day |
| `#parse` / `#include` with sandboxed resource loader | 0.5 day |
| GraalVM native image config | 0.5 day |
| Tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **4-5 days** |

## See Also

- [Apache Velocity Engine](https://velocity.apache.org/engine/)
- [Velocity Template Language (VTL) Reference](https://velocity.apache.org/engine/2.4/vtl-reference.html)
- [F14: FreeMarker Template Rendering](F14-freemarker-template-rendering.md) — modern successor
- [F16: Mustache Template Rendering](F16-mustache-template-rendering.md) — logic-less alternative
- [F17: StringTemplate Rendering](F17-stringtemplate-rendering.md) — code generation focus
- [stdlib function reference](../reference/stdlib-reference.md)

---

## Decision: Will Not Implement

**Date:** May 2026

### Problem: `$` syntax conflict

Velocity uses `$variableName` and `${variableName}` for variable references — the same `$` prefix that UTL-X uses. The UTL-X parser would interpret Velocity placeholders as UTL-X variable references before the string reaches the Velocity engine.

This is the same conflict that blocked F14 (FreeMarker). Both FreeMarker and Velocity share the `${}` interpolation syntax.

Additionally, Velocity is effectively end-of-life — Apache Velocity 2.x had its last release in 2020. FreeMarker is the recommended successor, and it has the same conflict. There is no reason to implement Velocity when FreeMarker (its modern replacement) is also blocked.

### Template engine summary

| Engine | Sigil | UTL-X Conflict | Status |
|---|---|---|---|
| FreeMarker | `${var}` | **Yes** | Will not implement (F14) |
| **Velocity** | `$var`, `${var}` | **Yes** | **Will not implement** |
| Mustache | `{{var}}` | **No** | Implementable (F16) |
| StringTemplate | `<var>` | Partial | Parked (F17) |

### Recommendation

For customers migrating from Velocity-based systems (Atlassian, older ESB platforms):
- Convert Velocity templates to Mustache (`{{var}}` syntax) — straightforward for simple templates
- Or handle template rendering at the pipeline layer, outside UTL-X

---

*Feature F18. May 2026. Decision: will not implement — same `$` conflict as FreeMarker, plus Velocity is end-of-life.*
