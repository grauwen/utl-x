# UTLXe Proto Reference

**Source of truth:** `proto/utlxe/v1/utlxe.proto`  
**Package:** `utlxe.v1`  
**Updated:** May 2026

This is the single wire-protocol contract for all UTLXe transports: gRPC, stdio-proto, HTTP (JSON derived from proto), and in-process JVM calls. The proto is the source of truth — all transports produce identical request/response shapes.

---

## Messages

### Transformation Lifecycle

#### `LoadTransformationRequest`

Load a single transformation from source code.

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `transformation_id` | 1 | string | Unique name for this transformation |
| `utlx_source` | 2 | string | The `.utlx` source code |
| `strategy` | 3 | string | `"TEMPLATE"`, `"COPY"`, `"COMPILED"`, `"AUTO"` (default: `"AUTO"`) |
| `validation_policy` | 4 | string | `"STRICT"`, `"WARN"`, `"SKIP"` (default: `"SKIP"`) |
| `max_concurrent` | 5 | int32 | Concurrency limit (0 = engine default) |
| `config` | 6 | map\<string, string\> | Additional config (format hints, output binding, etc.) |

#### `LoadTransformationResponse`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `success` | 1 | bool | `true` if compiled successfully |
| `error` | 2 | string | Error message if `success=false` |
| `warnings` | 3 | repeated `ValidationError` | Non-fatal type-check warnings |
| `metrics` | 4 | `LoadMetrics` | Parse + typecheck + total timing |

#### `LoadBundleRequest`

Load an entire bundle (ZIP containing transformations + schemas).

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `bundle_id` | 1 | string | Bundle identifier (e.g., `"orders-v3"`) |
| `bundle_data` | 2 | bytes | ZIP contents (inline upload) |
| `bundle_uri` | 3 | string | URI to fetch from (alternative: `blob://`, `file://`, `http://`) |

#### `LoadBundleResponse`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `success` | 1 | bool | |
| `error` | 2 | string | |
| `version` | 3 | string | Bundle version |
| `checksum` | 4 | string | SHA-256 of the bundle |
| `transformations_loaded` | 5 | int32 | Number of transformations in the bundle |
| `transformation_ids` | 6 | repeated string | Names of all transformations |
| `metrics` | 7 | `LoadMetrics` | Compilation timing |

#### `UnloadTransformationRequest` / `UnloadBundleRequest`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `transformation_id` or `bundle_id` | 1 | string | What to unload |

---

### Execution

#### `ExecuteRequest`

The primary hot-path message. One transformation, one input, one output.

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `transformation_id` | 1 | string | Which transformation to execute |
| `payload` | 2 | bytes | Input message body |
| `content_type` | 3 | string | `"application/json"`, `"application/xml"`, `"text/csv"`, etc. |
| `named_inputs` | 4 | map\<string, bytes\> | Additional inputs for multi-input transformations |
| `correlation_id` | 5 | string | Business correlation ID — preserved across the chain (UUIDv7) |
| `message_id` | 6 | string | Unique ID for this message (UUIDv7 — caller sets, or engine generates) |
| `causation_id` | 7 | string | MessageId of the parent message (UUIDv7) |
| `metadata` | 8 | map\<string, string\> | Custom properties to forward (Service Bus, CPI exchange, Kafka headers) |
| `traceparent` | 9 | string | W3C Trace Context traceparent header |
| `parameters` | 10 | map\<string, string\> | Transformation input parameters (accessible as `$params.key` in .utlx) |
| `metadata_forwarding` | 11 | `MetadataForwarding` | Controls which metadata keys pass through to output |
| `tracestate` | 12 | string | W3C Trace Context tracestate header (vendor extensions) |

**`metadata` vs `parameters` vs `named_inputs`:**

| Field | Purpose | Accessible in .utlx | Forwarded to output |
|-------|---------|:---:|:---:|
| `metadata` | Pass-through properties (tracing, routing) | No | Yes (controlled by `metadata_forwarding`) |
| `parameters` | Transformation input (BizTalk context, CPI exchange) | Yes (`$params.key`) | No |
| `named_inputs` | Additional payload inputs (multi-input transformations) | Yes (`$name`) | No |

#### `ExecuteResponse`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `success` | 1 | bool | `true` if transformation succeeded |
| `output` | 2 | bytes | Transformed payload |
| `output_content_type` | 3 | string | Output format |
| `error` | 4 | string | Error message if `success=false` |
| `error_class` | 5 | `ErrorClass` | `PERMANENT` or `TRANSIENT` |
| `validation_errors` | 6 | repeated `ValidationError` | Schema validation details |
| `metrics` | 7 | `ExecuteMetrics` | Execution timing |
| `error_phase` | 8 | `ErrorPhase` | Where the error occurred |
| `correlation_id` | 9 | string | Echoed from request |
| `message_id` | 10 | string | New UUIDv7 for the output message |
| `causation_id` | 11 | string | Set to input's `message_id` (automatic) |
| `metadata` | 12 | map\<string, string\> | Forwarded input properties |
| `error_code` | 13 | `ErrorCode` | Specific, actionable error code |
| `output_metadata` | 14 | `OutputMetadata` | Rule-emitted business metadata |

**The messaging triad (automatic):**
```
Request:  MessageId=UUID-A  CorrelationId=UUID-CORR  CausationId=UUID-PREV
Response: MessageId=UUID-B  CorrelationId=UUID-CORR  CausationId=UUID-A
                    ^^^^                                        ^^^^^^
              new UUIDv7                                  = input's MessageId
```

UTLXe always generates `response.message_id` (new UUIDv7), echoes `correlation_id`, and sets `response.causation_id = request.message_id`.

#### `BatchItem`

Per-item fields for batch execution.

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `payload` | 1 | bytes | |
| `content_type` | 2 | string | |
| `named_inputs` | 3 | map\<string, bytes\> | |
| `correlation_id` | 4 | string | |
| `message_id` | 5 | string | |
| `causation_id` | 6 | string | |
| `metadata` | 7 | map\<string, string\> | |
| `parameters` | 8 | map\<string, string\> | |
| `traceparent` | 9 | string | Per-item trace context (each item = own span) |
| `tracestate` | 10 | string | |

#### `ExecutePipelineRequest`

Chain multiple transformations. Output of step N feeds input of step N+1.

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `transformation_ids` | 1 | repeated string | Ordered list of transformation names |
| `payload` | 2 | bytes | Initial input |
| `content_type` | 3 | string | |
| `correlation_id` | 4 | string | |
| `message_id` | 5 | string | |
| `causation_id` | 6 | string | |
| `metadata` | 7 | map\<string, string\> | |
| `traceparent` | 8 | string | |
| `stage_inputs` | 9 | map\<string, `PipelineStageInputs`\> | EF01: additional inputs per stage, keyed by transformation_id |
| `parameters` | 10 | map\<string, string\> | Shared across all stages |
| `tracestate` | 11 | string | |

#### `ExecutePipelineResponse`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `success` | 1 | bool | |
| `output` | 2 | bytes | Final output (from last stage) |
| `output_content_type` | 3 | string | |
| `error` | 4 | string | |
| `error_class` | 5 | `ErrorClass` | |
| `error_phase` | 6 | `ErrorPhase` | |
| `validation_errors` | 7 | repeated `ValidationError` | |
| `correlation_id` | 8 | string | |
| `stages_completed` | 9 | int32 | How many stages completed before error (or total) |
| `total_duration_us` | 10 | int64 | Wall clock for entire pipeline |
| `message_id` | 11 | string | New UUIDv7 for the final output |
| `causation_id` | 12 | string | Last stage's input MessageId |
| `metadata` | 13 | map\<string, string\> | |
| `error_code` | 14 | `ErrorCode` | |
| `output_metadata` | 15 | `OutputMetadata` | From the last pipeline stage |

---

### Supporting Messages

#### `OutputMetadata`

Rule-emitted business metadata. Distinct from `metadata` (flat pass-through). Host adapters map these fields to host-specific scopes.

| Field | Tag | Type | Host mapping |
|-------|:---:|------|-------------|
| `application_id` | 1 | string | CPI: `SAP_ApplicationID`. BizTalk: promoted property. CloudEvents: `ce-subject`. |
| `message_type` | 2 | string | CPI: `SAP_MessageType`. CloudEvents: `ce-type`. BizTalk: `MessageType`. |
| `custom_status` | 3 | string | CPI: `SAP_MessageProcessingLogCustomStatus`. Logs: attribute. |
| `custom_identifiers` | 4 | map\<string, string\> | CPI: `addCustomHeaderProperty` per entry. Kafka: headers. |
| `sender` | 5 | string | CPI: `SAP_Sender`. EDI: ISA06. |
| `receiver` | 6 | string | CPI: `SAP_Receiver`. EDI: ISA08. |

Set by the .utlx transformation via a `meta` block:

```
%output {
  body: transformed,
  meta: {
    applicationId: $input.orderNumber,
    messageType: "CanonicalOrder.v3",
    custom: { DeliveryNumber: $input.deliveryId }
  }
}
```

#### `PipelineStageInputs`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `additional_inputs` | 1 | map\<string, bytes\> | Named inputs for this pipeline stage |

#### `LoadMetrics`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `parse_duration_us` | 1 | int64 | Microseconds for lexer + parser |
| `typecheck_duration_us` | 2 | int64 | Microseconds for type checker |
| `total_duration_us` | 3 | int64 | Total init time |

#### `ExecuteMetrics`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `execute_duration_us` | 1 | int64 | Microseconds for this execution |

#### `ValidationError`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `message` | 1 | string | Error description |
| `path` | 2 | string | JSONPath or XPath to the problematic field |
| `severity` | 3 | string | `"ERROR"` or `"WARNING"` |

#### `HealthResponse`

| Field | Tag | Type | Description |
|-------|:---:|------|-------------|
| `state` | 1 | string | `"READY"`, `"RUNNING"`, `"DRAINING"` |
| `uptime_ms` | 2 | int64 | |
| `loaded_transformations` | 3 | int32 | |
| `total_executions` | 4 | int64 | |
| `total_errors` | 5 | int64 | |

---

## Enums

#### `ErrorClass`

| Value | Number | Meaning |
|-------|:------:|---------|
| `ERROR_CLASS_UNSPECIFIED` | 0 | |
| `PERMANENT` | 1 | Missing field, type coercion — do not retry |
| `TRANSIENT` | 2 | Temporary error — retryable |

#### `ErrorPhase`

| Value | Number | Meaning |
|-------|:------:|---------|
| `ERROR_PHASE_UNSPECIFIED` | 0 | |
| `PRE_VALIDATION` | 1 | Input schema validation failed |
| `TRANSFORMATION` | 2 | .utlx execution failed |
| `POST_VALIDATION` | 3 | Output schema validation failed |
| `INTERNAL` | 4 | Engine error (not payload-related) |

#### `ErrorCode`

| Value | Number | Meaning |
|-------|:------:|---------|
| `ERROR_CODE_UNSPECIFIED` | 0 | |
| `TRANSFORMATION_NOT_FOUND` | 1 | transformation_id doesn't exist |
| `BUNDLE_NOT_LOADED` | 2 | bundle_id not loaded |
| `INPUT_PARSE_FAILED` | 3 | Payload can't be parsed |
| `INPUT_VALIDATION_FAILED` | 4 | Input doesn't match schema |
| `TRANSFORMATION_FAILED` | 5 | Runtime error in .utlx |
| `OUTPUT_VALIDATION_FAILED` | 6 | Output doesn't match schema |
| `OUTPUT_SERIALIZATION_FAILED` | 7 | Output can't be serialized |
| `MAX_CONCURRENT_EXCEEDED` | 8 | maxConcurrent limit reached |
| `MAX_INPUT_SIZE_EXCEEDED` | 9 | Payload exceeds maxInputSize |
| `INTERNAL_ERROR` | 10 | Engine-internal error |

#### `MetadataForwarding`

| Value | Number | Meaning |
|-------|:------:|---------|
| `METADATA_FORWARDING_UNSPECIFIED` | 0 | Engine default (forward all) |
| `METADATA_FORWARD_ALL` | 1 | Forward all input metadata |
| `METADATA_FORWARD_NONE` | 2 | Drop all input metadata |
| `METADATA_FORWARD_CORRELATION` | 3 | Forward only correlation/message/causation IDs |

---

## gRPC Service

```protobuf
service UtlxeService {
  // Transformation lifecycle
  rpc LoadTransformation(LoadTransformationRequest) returns (LoadTransformationResponse);
  rpc UnloadTransformation(UnloadTransformationRequest) returns (UnloadTransformationResponse);

  // Bundle lifecycle
  rpc LoadBundle(LoadBundleRequest) returns (LoadBundleResponse);
  rpc UnloadBundle(UnloadBundleRequest) returns (UnloadBundleResponse);

  // Execution
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);
  rpc ExecuteBatch(ExecuteBatchRequest) returns (ExecuteBatchResponse);
  rpc ExecutePipeline(ExecutePipelineRequest) returns (ExecutePipelineResponse);

  // Health
  rpc Health(HealthRequest) returns (HealthResponse);
}
```

## stdio-proto Envelope

For stdin/stdout protobuf mode, messages are wrapped in `StdioEnvelope` with varint-delimited framing:

| MessageType | Value | Direction |
|-------------|:-----:|-----------|
| `LOAD_TRANSFORMATION_REQUEST` | 1 | wrapper → engine |
| `EXECUTE_REQUEST` | 2 | wrapper → engine |
| `EXECUTE_BATCH_REQUEST` | 3 | wrapper → engine |
| `UNLOAD_TRANSFORMATION_REQUEST` | 4 | wrapper → engine |
| `HEALTH_REQUEST` | 5 | wrapper → engine |
| `EXECUTE_PIPELINE_REQUEST` | 6 | wrapper → engine |
| `LOAD_BUNDLE_REQUEST` | 7 | wrapper → engine |
| `UNLOAD_BUNDLE_REQUEST` | 8 | wrapper → engine |
| `LOAD_TRANSFORMATION_RESPONSE` | 11 | engine → wrapper |
| `EXECUTE_RESPONSE` | 12 | engine → wrapper |
| `EXECUTE_BATCH_RESPONSE` | 13 | engine → wrapper |
| `UNLOAD_TRANSFORMATION_RESPONSE` | 14 | engine → wrapper |
| `HEALTH_RESPONSE` | 15 | engine → wrapper |
| `EXECUTE_PIPELINE_RESPONSE` | 16 | engine → wrapper |
| `LOAD_BUNDLE_RESPONSE` | 17 | engine → wrapper |
| `UNLOAD_BUNDLE_RESPONSE` | 18 | engine → wrapper |

---

## Design Principles

1. **Proto is the source of truth.** HTTP JSON responses use the same field names. Error codes serialize as strings in HTTP (`"TRANSFORMATION_NOT_FOUND"`), as enum values in proto.

2. **All IDs are UUIDv7** (RFC 9562). Time-ordered, sortable, timestamp-embedded. Engine generates UUIDv7 for all internally created MessageIds. Callers may send any valid UUID.

3. **Additive evolution only.** New fields use new tag numbers. No field is ever removed or renumbered. Old clients ignore new fields. Old engines never emit new fields.

4. **Host-neutral.** No CPI-specific, BizTalk-specific, or Dapr-specific fields. `OutputMetadata` is the typed contract for business identifiers — each host adapter maps it to host-specific scopes.

5. **Three metadata layers, three purposes:**
   - `metadata` — pass-through (forward input properties to output)
   - `parameters` — transformation input (accessible in .utlx as `$params.*`)
   - `output_metadata` — rule-emitted business identifiers (typed, host-neutral)

---

*Proto reference. May 2026. Single source of truth: `proto/utlxe/v1/utlxe.proto`.*
