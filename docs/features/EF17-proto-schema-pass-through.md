# EF17: Proto Schema Pass-Through

**Status:** Implemented  
**Priority:** Medium  
**Breaking:** No (new fields only)  
**Origin:** Open-M CH-003 assessment

---

## Problem

When the Open-M wrapper loads a transformation via `LoadTransformationRequest`, it has access to schemas from the control plane's schema store (per component port). But there was no way to pass those schemas to UTLXe through the proto. Schemas were either:
- Loaded from the bundle on disk (Azure path)
- Passed as opaque strings in the `config` map (fragile, single-input only)

This meant UTLXe couldn't do schema-based validation or type-aware parsing for transformations loaded dynamically via stdio-proto or gRPC.

## Solution

Add dedicated schema fields to `LoadTransformationRequest`:

```protobuf
message LoadTransformationRequest {
  // ... existing fields (1-6) ...

  // Input schemas — one per named input (N-input mapping support)
  // Keys match the named_inputs keys in ExecuteRequest.
  map<string, bytes> input_schemas = 7;
  map<string, string> input_schema_formats = 8;  // "xsd" / "json-schema" / "avro"

  // Output schema — single (downstream component's expected input)
  bytes output_schema = 9;
  string output_schema_format = 10;              // "xsd" / "json-schema" / "avro"
}
```

### Why maps for input schemas

UTLXe supports N-input mappings via `ExecuteRequest.named_inputs` (`map<string, bytes>`). Each named input can have a **different** schema and format. Example:

| Input name | Source | Format | Schema type |
|---|---|---|---|
| `current` | Main payload (JDBC step) | JSON | JSON Schema |
| `pricing` | Step[-2] pricing service | JSON | JSON Schema |
| `original` | Step[-3] SOAP ingress | XML | XSD |

A single `source_schema` would only cover the primary input. The map covers all N inputs.

## What it enables

- **PRE_VALIDATION**: validate each input against its declared schema
- **POST_VALIDATION**: validate output against the target schema
- **COPY strategy**: build UDM skeletons from real schemas (type-aware structure)
- **Type precision**: BigDecimal from XSD decimal, proper date types, null handling

## Impact on Azure

**None.** The Azure/Dapr path loads transformations via the Admin API (HTTP/JSON) or from bundles on disk. It never uses `LoadTransformationRequest`. The new fields are only consumed by:
- stdio-proto transport (Open-M wrapper)
- gRPC transport (SDK clients)

Both are additive — old callers that don't set these fields get empty maps/bytes, and UTLXe falls back to bundle schemas or no validation (existing behaviour).

## Files changed

| File | Change |
|---|---|
| `proto/utlxe/v1/utlxe.proto` | Added fields 7-10 to `LoadTransformationRequest` |
| `modules/engine/.../transport/TransportHandlers.kt` | Maps `input_schemas` → `InputSlot(schema=...)`, `output_schema` → `OutputSlot(schema=...)`. Primary input schema used for input validation. Falls back to `config` map if proto fields are empty. |

## Backward compatibility

- Proto: new field numbers (7-10), no existing fields changed
- Old callers: send empty maps/bytes → UTLXe behaves exactly as before
- New callers: populate schemas → UTLXe uses them for validation + COPY skeleton
- Config map fallback: still works (for callers using the old `validate_input`/`input_schema` pattern)
