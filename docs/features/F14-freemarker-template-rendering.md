# F14: FreeMarker Template Rendering

**Status:** Will not implement — `${}` syntax conflict with UTL-X  
**Priority:** ~~Medium~~ N/A  
**Created:** May 2026  
**Decision:** May 2026

---

## Summary

Add a `freemarker-template-action` that renders Apache FreeMarker templates within UTL-X transformations. This enables template-driven output generation for scenarios where declarative mapping alone is insufficient — such as generating emails, reports, configuration files, or code from structured data.

## Background

[Apache FreeMarker](https://freemarker.apache.org/) is a mature Java-based template engine widely used in enterprise systems. It processes templates (`.ftl` files) with a data model to produce text output (HTML, XML, plain text, source code, etc.).

### Why FreeMarker?

| Criterion | FreeMarker | Alternatives |
|-----------|-----------|--------------|
| Language | Java/JVM (native fit for UTL-X) | Mustache (multi-lang), Thymeleaf (HTML-only), Velocity (EOL) |
| Maturity | Apache TLP, 20+ years | Varies |
| Output formats | Any text (HTML, XML, JSON, CSV, code, etc.) | Often format-specific |
| Feature set | Macros, includes, built-ins, null handling, i18n | Typically simpler |
| Enterprise adoption | SAP, Spring, Alfresco, Jenkins | Varies |

FreeMarker is the natural choice for a JVM-based transformation engine. It is already an Apache project, aligning with UTL-X's Apache ecosystem.

## Use Cases

### 1. Email/Report Generation
Transform structured data into formatted output:
```
input JSON (order data) -> UTL-X mapping -> FreeMarker template -> HTML email
```

### 2. Configuration File Generation
Generate environment-specific configs from a canonical model:
```
input YAML (parameters) -> UTL-X enrichment -> FreeMarker template -> nginx.conf / Dockerfile / k8s YAML
```

### 3. Code Generation
Produce source code from schema or metadata:
```
input XSD/JSON Schema -> UTL-X schema parsing -> FreeMarker template -> Java classes / TypeScript interfaces
```

### 4. Document Rendering
Generate human-readable documents from integration data:
```
input UBL invoice -> UTL-X mapping -> FreeMarker template -> PDF-ready HTML / LaTeX
```

### 5. Legacy System Integration
Many enterprise systems (SAP, ESB platforms) already use FreeMarker templates. UTL-X can reuse existing templates during migration.

## Proposed Functions

### Core Action

```
renderTemplate(templateString, dataModel)              -> rendered string
renderTemplateFile(templatePath, dataModel)             -> rendered string
```

### With Options

```
renderTemplate(templateString, dataModel, options?)     -> rendered string
```

Options object:
- `locale` — template locale (default: system locale)
- `numberFormat` — number formatting pattern
- `dateFormat` — date formatting pattern
- `encoding` — output encoding (default: UTF-8)
- `autoEscape` — enable/disable auto-escaping (default: true for HTML)
- `outputFormat` — `HTML`, `XML`, `plainText`, `undefined`

### UTL-X Integration Examples

**Inline template:**
```
%output text
---
renderTemplate(
  "Hello ${name}, your order #${orderId} contains ${items?size} items.",
  $input
)
```

**Template file:**
```
%output text
---
renderTemplateFile("templates/invoice.ftl", {
  invoice: $input,
  company: $config.company,
  locale: "nl_NL"
})
```

**With mapping + template pipeline:**
```
%input json
%output text

let mapped = {
  customerName: $input.customer.fullName,
  items: $input.orderLines[*].{
    description: .productName,
    qty: .quantity,
    price: formatNumber(.unitPrice, "#,##0.00")
  },
  total: sum($input.orderLines[*].lineTotal)
}

renderTemplate($template, mapped)
```

## Implementation

### Module Location

**Option A:** `stdlib` — if FreeMarker is a lightweight dependency
**Option B:** `stdlib-templates` — separate module to keep core lean

Recommended: **Option B** (`stdlib-templates`), since FreeMarker adds ~1.7 MB and not all users need template rendering.

### Dependencies

```kotlin
// stdlib-templates/build.gradle.kts
dependencies {
    implementation(project(":modules:core"))
    implementation("org.freemarker:freemarker:2.3.33")
}
```

### GraalVM Native Image

| Risk | Detail |
|------|--------|
| Medium | FreeMarker uses reflection for method introspection on data models |
| Mitigation | UDM-to-FreeMarker adapter using `TemplateHashModel` avoids reflection on arbitrary classes |
| Alternative | Wrap UDM as `SimpleHash`/`SimpleSequence` — these are GraalVM-friendly |

FreeMarker's `DefaultObjectWrapper` uses reflection, but a custom `ObjectWrapper` that maps UDM types directly to FreeMarker model types avoids this entirely.

### Architecture

```
UDM data model
     |
     v
UDMTemplateModel (implements TemplateHashModel)
     |
     v
FreeMarker Configuration + Template
     |
     v
StringWriter -> UDM.Scalar (rendered text)
```

Key implementation class:
```kotlin
// stdlib-templates/src/main/kotlin/org/apache/utlx/stdlib/template/FreeMarkerFunctions.kt

class UDMObjectWrapper : ObjectWrapper {
    fun wrap(udm: UDM): TemplateModel = when (udm) {
        is UDM.Scalar -> SimpleScalar(udm.value?.toString())
        is UDM.Object -> SimpleHash(udm.properties.mapValues { wrap(it.value) })
        is UDM.Array  -> SimpleSequence(udm.elements.map { wrap(it) })
        else -> TemplateModel.NOTHING
    }
}
```

## Security Considerations

- **Template injection:** User-supplied templates can call FreeMarker built-ins like `?new` to instantiate arbitrary Java classes. Must use `Configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER)` to block this.
- **File access:** `renderTemplateFile` must restrict template paths to a configured template directory. No arbitrary filesystem access.
- **Resource limits:** Set `templateExceptionHandler` and processing timeout to prevent infinite loops in templates.
- **Sandbox:** In CLI mode, templates should be restricted to the current project directory. In UTLXe engine mode, template paths are configured per bundle.

## Relation to Existing UTL-X Capabilities

| Approach | Best for | Limitation |
|----------|----------|------------|
| UTL-X declarative mapping | Structured data -> structured data | Not designed for free-form text |
| `renderTemplate` (F14) | Structured data -> formatted text | One-directional (no parsing) |
| `formatString` (stdlib) | Simple string interpolation | No loops, conditionals, macros |

FreeMarker complements UTL-X mapping — it does not replace it. Use UTL-X for data transformation, FreeMarker for presentation/rendering.

## Template Engine Decision Matrix

UTL-X offers four template engine integrations, each with a clear use case:

| Need | Recommendation |
|------|---------------|
| Simple string building | UTL-X native (`+`, `formatString`) |
| **New complex templates** with macros, i18n, formatting | **F14 — FreeMarker** (power) |
| Portable templates shared across JS/Python/Go/Java | [F16 — Mustache](F16-mustache-template-rendering.md) (simplicity) |
| **Code generation** with auto-indentation | [F17 — StringTemplate](F17-stringtemplate-rendering.md) (auto-indent) |
| Reuse legacy `.vm` files from ESB/Atlassian/Maven | [F18 — Velocity](F18-velocity-template-rendering.md) (migration) |
| Structured data -> structured data | UTL-X native mapping |

## Effort Estimate

| Task | Effort |
|------|--------|
| `stdlib-templates` module setup | 0.5 day |
| UDM-to-FreeMarker model wrapper | 1 day |
| `renderTemplate` / `renderTemplateFile` functions | 1-2 days |
| Security hardening (sandbox, class resolver) | 1 day |
| GraalVM native image compatibility | 1 day |
| Tests and documentation | 1 day |
| **Total** | **5-6 days** |

## See Also

- [Apache FreeMarker Documentation](https://freemarker.apache.org/docs/)
- [stdlib function reference](../reference/stdlib-reference.md)
- [F12: XSLT to UTL-X Migration](F12-xslt-to-utlx-migration.md) — related: XSLT also generates text output from XML
- [F16: Mustache Template Rendering](F16-mustache-template-rendering.md) — logic-less, portable alternative
- [F17: StringTemplate Rendering](F17-stringtemplate-rendering.md) — code generation focus
- [F18: Velocity Template Rendering](F18-velocity-template-rendering.md) — legacy template reuse

---

## Decision: Will Not Implement

**Date:** May 2026

### Problem: `${}` syntax conflict

FreeMarker uses `${variableName}` for template interpolation. UTL-X uses `$input`, `$variable` for variable references. The UTL-X parser interprets `${name}` as a UTL-X expression before the string reaches the FreeMarker engine.

This means:
- Inline FreeMarker templates in `.utlx` expressions are impossible without escaping
- The template string must always come from a file or external variable, never from the UTL-X source
- The developer experience is confusing — two `${}` syntaxes with different meanings in the same file

### Same issue affects all template engines

| Engine | Sigil | Conflict with UTL-X |
|---|---|---|
| FreeMarker | `${var}` | **Direct conflict** — same sigil |
| Velocity | `$var` | **Direct conflict** — same sigil |
| Mustache | `{{var}}` | No conflict, but limited (logic-less) |
| StringTemplate | `<var>` | Conflicts with XML in some contexts |

### Recommendation

Template rendering is better handled at the **engine/pipeline layer** (e.g., a Dapr output binding that applies a FreeMarker template to the transformation result) rather than inside the UTL-X expression language. UTL-X should focus on data transformation; template rendering is a presentation concern.

For customers who need template rendering:
- Use UTL-X for data transformation → produce a clean UDM/JSON model
- Apply the FreeMarker/Mustache/Velocity template in a separate pipeline step (Azure Function, Logic Apps action, CPI iFlow step)

This keeps the UTL-X language clean and avoids syntax conflicts.

### Related decisions

- F16 (Mustache): Could be implemented (no `$` conflict) but low demand
- F17 (StringTemplate): Parked — niche use case
- F18 (Velocity): Will not implement — same `$` conflict as FreeMarker

---

*Feature F14. May 2026. Decision: will not implement due to `${}` syntax conflict.*
