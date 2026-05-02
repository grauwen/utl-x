# EF02: Engine Schema Validation (UTLXe Adaptation for F01)

**Status:** Design — depends on F01  
**Priority:** Medium  
**Created:** May 2026  
**Depends on:** F01 (inline schema validation in header)

---

## Summary

When F01 adds `{schema: "file"}` to the `.utlx` header, the UTLXe engine needs adaptations beyond what core provides. This document covers the engine-specific concerns: precedence, bundle resolution, pre-compilation, metrics, error routing, and startup validation.

## 1. Schema Precedence

Two places can now declare validation schemas:

| Source | Example |
|--------|---------|
| `.utlx` header | `input json {schema: "order.json"}` |
| `TransformConfig` YAML | `inputValidation: {schema: "order.json", policy: "strict"}` |

### Precedence rule: Header wins, config is fallback

```
Effective schema = header.schema ?? config.schema
Effective policy = header.validationPolicy ?? config.validationPolicy ?? "strict"
```

**Rationale:** The header is version-controlled with the transformation — it's the developer's intent. The config is operational override. If both exist, the developer's declaration takes priority. To operationally disable validation without modifying the `.utlx` file, the config can set `validationPolicy: "skip"`.

### Override: config can force policy

```yaml
# TransformConfig — operational override
validation:
  policyOverride: "warn"   # forces all schemas to warn-only (regardless of header)
```

This allows ops to relax validation in non-production environments without touching `.utlx` files.

## 2. Bundle Resolution

In UTLXe bundles, schema files are co-packaged:

```
my-bundle/
├── transform.utlx        ← input json {schema: "schemas/order.json"}
├── schemas/
│   └── order.json
└── config.yaml
```

### Resolution rules:

1. Relative paths resolve from the **bundle root** (not CWD, not the `.utlx` file location)
2. Absolute paths are rejected at bundle compile time (not portable)
3. Paths referencing outside the bundle (`../other.json`) are rejected (security)

```kotlin
fun resolveSchemaPath(bundleRoot: Path, schemaRef: String): Path {
    val resolved = bundleRoot.resolve(schemaRef).normalize()
    require(resolved.startsWith(bundleRoot)) { "Schema path escapes bundle: $schemaRef" }
    return resolved
}
```

## 3. Pre-Compilation of Schema Validators

The CLI creates a validator per invocation (acceptable for single-message processing). UTLXe processes thousands of messages per second — validators must be compiled once at startup.

### Lifecycle:

```
Bundle load → compile transformation → compile schema validators → cache
                                                                     ↓
Message arrival → parse → validate(cached) → transform → validate(cached) → serialize
```

### Cache structure:

```kotlin
class CompiledBundle(
    val transformation: CompiledTransformation,
    val inputValidator: SchemaValidator?,     // null if no schema declared
    val outputValidator: SchemaValidator?,    // null if no schema declared
)
```

Schema validators are compiled during `--validate` and bundle load. No per-message compilation overhead.

## 4. Prometheus Metrics

UTLXe already exposes transformation metrics. Add validation-specific counters:

```
# Input validation
utlxe_input_validation_total{pipeline="...", result="pass|fail|skip"}
utlxe_input_validation_errors_total{pipeline="...", error_type="..."}
utlxe_input_validation_duration_seconds{pipeline="..."}

# Output validation
utlxe_output_validation_total{pipeline="...", result="pass|fail|skip"}
utlxe_output_validation_errors_total{pipeline="...", error_type="..."}
utlxe_output_validation_duration_seconds{pipeline="..."}
```

### Distinction from transformation errors:

| Metric | Meaning |
|--------|---------|
| `utlxe_input_validation_errors_total` | Message rejected before transformation (bad input) |
| `utlxe_transformation_errors_total` | Transformation logic failed (existing metric) |
| `utlxe_output_validation_errors_total` | Transformation produced invalid output (bug in .utlx) |

This distinction is critical for alerting: input validation failures are upstream data quality issues; output validation failures are transformation bugs.

## 5. Error Routing

UTLXe pipelines route errors to dead-letter or retry queues. Validation errors should be classified:

| Error type | Retryable? | Routing |
|-----------|-----------|---------|
| Input validation failure | No | Dead-letter immediately (bad data won't fix itself) |
| Output validation failure | No | Dead-letter + alert (transformation bug) |
| Transformation runtime error | Maybe | Retry queue (transient failures like OOM) |

### In TransformConfig:

```yaml
errorRouting:
  inputValidationFailure: dead-letter    # default: no retry
  outputValidationFailure: dead-letter   # default: alert + dead-letter
  transformationError: retry             # default: retry 3x then dead-letter
```

## 6. `--validate` Flag Enhancement

The existing `--validate` flag compiles the bundle and exits. With F01, it should also:

1. Verify all referenced schema files exist in the bundle
2. Verify schema files are valid (parseable as their declared format)
3. Report schema coverage: which pipelines have input/output validation declared

```
$ utlxe --validate --bundle my-bundle/

Bundle validation:
  Transformations: 3 compiled OK
  Schema validators:
    order-transform.utlx: input ✓ (order.json) output ✓ (invoice.xsd)
    enrich-customer.utlx: input ✓ (customer.json) output — (none)
    passthrough.utlx:     input — (none) output — (none)
  Result: VALID
```

Missing or unparseable schema files → validation fails (exit code 1).

## Effort Estimate

| Task | Effort |
|------|--------|
| Precedence logic (header vs config merge) | 0.5 day |
| Bundle path resolution + security check | 0.5 day |
| Schema pre-compilation at bundle load | 1 day |
| Prometheus metrics (6 new counters) | 0.5 day |
| Error routing classification | 0.5 day |
| `--validate` schema file verification | 0.5 day |
| Tests | 1 day |
| **Total** | **4-5 days** (after F01 is done) |

## Dependencies

- **F01 must land first** — provides the core `{schema: "..."}` parsing and `SchemaValidator` interface
- EF02 wires F01's capability into UTLXe's production infrastructure

---

*Engine feature EF02. May 2026.*
*Adapts F01's inline schema validation for production engine concerns: precedence, performance, observability, error handling.*
