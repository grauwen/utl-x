# F01: Inline Schema Validation in Header Declaration

**Status:** Not implemented  
**Priority:** Medium  
**Created:** April 2026

---

## Summary

Schema validation for input and output is currently only configurable through UTLXe's `TransformConfig` (YAML config file) or the ValidationOrchestrator API. There is no way to declare validation schemas inline in the `.utlx` file header.

## The Gap

A developer writing a `.utlx` file cannot express "validate my input against this JSON Schema" or "validate my output against this XSD" within the transformation file itself. The validation configuration lives separately in a YAML config file — disconnected from the transformation.

This means:
- The `.utlx` file doesn't tell you what contract it expects
- The schema reference is not version-controlled alongside the transformation
- The IDE cannot auto-validate based on the header alone
- You need a separate config file to enable validation

## Current Syntax (format options)

The existing header uses `{key: value}` syntax for format options:

```utlx
%utlx 1.0
input xml
output json {writeAttributes: true}
---
$input
```

This is parsed by `parseFormatOptions()` in `parser_impl.kt` (line 574+) as a `Map<String, Any>` stored in `FormatSpec.options`.

Existing options include:
- `{encoding: "UTF-8"}` — XML encoding
- `{writeAttributes: true}` — JSON/YAML attribute handling
- `{delimiter: ";", headers: true}` — CSV options
- `{regionalFormat: "european"}` — CSV number formatting
- `{namespace: "..."}` — OSCH namespace
- `{pattern: "venetian-blind"}` — XSD output pattern

## Proposed Syntax: Two Options

### Option A: Use existing `{key: value}` syntax (consistent)

```utlx
%utlx 1.0
input json {schema: "order-input.json"}
output xml {encoding: "UTF-8", schema: "invoice-output.xsd"}
---
$input
```

**Pros:**
- Consistent with existing option syntax — no parser changes needed for syntax
- `schema` is just another key in the options map (like `encoding`, `writeAttributes`)
- IDE autocompletion already works for `{...}` options

**Cons:**
- `schema` feels different from `encoding` — it's a file reference, not a serializer setting
- Mixing serializer options with validation options in one block

### Option B: Use `@schema(...)` annotation syntax (new syntax)

```utlx
%utlx 1.0
input json @schema("order-input.json")
output xml @schema("invoice-output.xsd") {encoding: "UTF-8"}
---
$input
```

**Pros:**
- Visually distinct from format options (annotations vs configuration)
- Familiar from Java/Kotlin annotations, USDL constraints
- Could support multiple annotations: `@schema("...") @policy("strict")`

**Cons:**
- Requires parser changes (new token type for `@` in header context)
- `@` already means "attribute access" in the transformation body — contextual overloading
- New concept to learn (annotations in headers vs `{options}`)

### Recommendation: Option A

Use the existing `{schema: "path"}` syntax. Reasons:

1. **Zero parser changes.** The `parseFormatOptions()` function already handles arbitrary keys. Adding `schema` is just adding handling in `TransformationService.serializeOutput()`.

2. **Consistency.** Every format option uses the same `{key: value}` pattern. Schema is "just another option."

3. **No `@` confusion.** The `@` prefix means "XML attribute" everywhere in UTL-X. Using it for annotations in headers creates contextual ambiguity.

4. **The book already documents `{writeAttributes: true}`.** Adding `{schema: "file"}` follows the same pattern readers already know.

The `@schema(...)` syntax from the book draft should be corrected to `{schema: "..."}`.

## Implementation Plan

### What needs to change

| File | Change |
|------|--------|
| `TransformationService.kt` | Extract `schema` from `formatSpec.options`, resolve file path, invoke validator |
| `ValidationOrchestrator.kt` | Already exists — wire the schema from options to the orchestrator |
| `SchemaValidatorFactory.kt` | Already exists — create validator from schema file |
| Parser (`parser_impl.kt`) | **No change needed** — `{schema: "..."}` already parses as a string option |
| AST (`ast_nodes.kt`) | **No change needed** — `FormatSpec.options` already holds the value |

### Logic

```kotlin
// In TransformationService.serializeOutput() or a new validateInput():
val inputSchema = inputFormatSpec.options["schema"] as? String
if (inputSchema != null) {
    val validator = SchemaValidatorFactory.create(inputSchema, inputFormat)
    val errors = validator.validate(inputUDM)
    if (errors.isNotEmpty()) throw ValidationException(errors)
}

// Same for output:
val outputSchema = outputFormatSpec.options["schema"] as? String
if (outputSchema != null) {
    val validator = SchemaValidatorFactory.create(outputSchema, outputFormat)
    val errors = validator.validate(outputUDM)
    if (errors.isNotEmpty()) throw ValidationException(errors)
}
```

### Schema format auto-detection

The schema file format should be detected from the file extension:

| Extension | Schema format | Validator |
|-----------|--------------|-----------|
| `.json` | JSON Schema | JsonSchemaValidator |
| `.xsd` | XSD | XsdValidator |
| `.avsc` | Avro | AvroSchemaValidator |
| `.proto` | Protobuf | ProtobufValidator |
| `.usdl` | USDL | Convert to target format, then validate |
| `.tsch.json` | Table Schema | TableSchemaValidator |
| `.edmx` | OData Schema | ODataSchemaValidator |

### Validation policy

An optional `validationPolicy` option controls behavior:

```utlx
input json {schema: "order.json", validationPolicy: "strict"}
output xml {schema: "invoice.xsd", validationPolicy: "warn"}
```

| Policy | Behavior |
|--------|----------|
| `strict` (default) | Validation failure → transformation fails, error returned |
| `warn` | Validation failure → warning logged, transformation continues |
| `skip` | Schema loaded but validation not executed (documentation only) |

## Effort Estimate

- Parser: **0 changes** (existing `{key: value}` syntax)
- TransformationService: **~20 lines** (extract schema, invoke validator)
- Tests: **~5 conformance tests** (input validation, output validation, both, policy modes)
- Documentation: update book chapters 10, 11, 18

**Total: 1-2 days implementation**

## Book Correction

The book (chapters 10 and 11) currently shows `@schema("...")` syntax. This should be corrected to:

```utlx
input json {schema: "order-input.json"}
output xml {schema: "invoice-output.xsd", encoding: "UTF-8"}
```

This is consistent with how `writeAttributes`, `encoding`, `delimiter`, and all other options are specified.

---

*Feature document F01. April 2026.*
