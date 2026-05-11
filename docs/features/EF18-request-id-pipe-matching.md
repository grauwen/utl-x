# EF18: request_id for Pipe-Multiplexed Response Dispatch

**Status:** Implemented  
**Priority:** High  
**Breaking:** No (new fields only)  
**Origin:** Open-M CH-003 assessment — fan-out scenario

---

## Problem

The UTLXe proto used `correlation_id` for two purposes:
1. **Pipe-level response matching** — UTLXe echoes it back, wrapper dispatches by it
2. **MPPM transaction grouping** — same value for all messages in a pipeline execution

This breaks in **fan-out** (splitter) scenarios. When a splitter produces 3 messages, all share the same MPPM `correlation_id`. If two are processed concurrently by UTLXe on the stdio-proto pipe, the wrapper can't tell which response belongs to which request.

The engine.proto already solved this with a separate `request_id` in `EngineEnvelope`.

## Solution

Add `request_id` to the UTLXe proto — a unique-per-call pipe-level identifier, distinct from the MPPM `correlation_id`.

```protobuf
// ExecuteRequest: field 13
string request_id = 13;   // Unique per call, echoed back on response

// ExecuteResponse: field 15
string request_id = 15;   // Echoed from request

// BatchItem: field 11
string request_id = 11;   // Per batch item

// ExecutePipelineRequest: field 12
string request_id = 12;

// ExecutePipelineResponse: field 16
string request_id = 16;
```

UTLXe echoes `request_id` on every response path (success, error, validation failure, not-found). The wrapper uses it for multiplexed dispatch. `correlation_id` continues to be echoed too — for logging/tracing context.

## The Identity Model (clarified)

| Field | Purpose | Set by | Unique per |
|---|---|---|---|
| `request_id` | Pipe-level response dispatch | Wrapper (per call) | Every UTLXe call |
| `correlation_id` | MPPM transaction grouping UUID | Wrapper at ingress | Pipeline execution (shared) |
| `message_id` | MPPM message identity (UUIDv7) | Wrapper per hop | Every hop |
| `causation_id` | MPPM causal parent | Wrapper per hop | Every hop |
| `OutputMetadata.application_id` | Business document ID | UTLXe rules (from payload) | Per business entity |

None of the MPPM IDs are "business IDs" — they're technical UUIDs. The business ID (e.g. "ORD-42") lives in the payload and is extracted by UTLXe rules into `OutputMetadata.application_id`.

## Impact on Azure / Dapr

**None.** The HTTP transport doesn't use `request_id` — HTTP is inherently request-response with no multiplexing ambiguity. The field is only relevant for:
- stdio-proto transport (pipe-multiplexed, Open-M wrapper)
- gRPC transport (if used in streaming/multiplexed mode)

## Files changed

| File | Change |
|---|---|
| `proto/utlxe/v1/utlxe.proto` | Added `request_id` to ExecuteRequest (13), ExecuteResponse (15), BatchItem (11), ExecutePipelineRequest (12), ExecutePipelineResponse (16). Fixed `correlation_id` comments: "MPPM transaction grouping UUID" not "Business correlation ID". |
| `modules/engine/.../transport/TransportHandlers.kt` | Echo `request_id` on all response paths: handleExecute (3 paths), handleExecuteBatch (2 paths), handleExecutePipeline (4 paths) |

## Backward compatibility

- Old callers: don't set `request_id` → empty string echoed back → no change in behaviour
- New callers: set `request_id` → echoed back → safe fan-out dispatch
- `correlation_id` echo: unchanged — still echoed on all responses
