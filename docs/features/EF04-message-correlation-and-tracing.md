# EF04: Message Correlation and Tracing

**Status:** Must-have (hygiene factor — required before go-live)  
**Priority:** Critical  
**Created:** May 2026

---

## Summary

UTLXe does not currently propagate message correlation metadata from input to output. When a message arrives from Azure Service Bus via Dapr, the output message on the output queue has no link back to the input message. In a production scenario with thousands of messages, you cannot trace which input produced which output.

This feature fixes that by:
1. Reading incoming Dapr/Service Bus metadata (MessageId, CorrelationId, custom properties)
2. Propagating correlation metadata to the output binding call
3. Forwarding W3C Trace Context (OpenTelemetry `traceparent`) across the transformation
4. Logging the correlation chain for operational visibility

## The Problem

### Current state

When UTLXe sends a transformed message to the Dapr output binding, it includes only:

```kotlin
"metadata" to mapOf(
    "source-binding" to bindingName,    // "orders-in"
    "transform-id" to transformId       // "orders-in"
)
```

Missing:
- `MessageId` from the input Service Bus message — not forwarded
- `CorrelationId` from the input message — not forwarded
- `traceparent` / `tracestate` (W3C Trace Context) — Dapr adds it to the inbound call, but UTLXe does not propagate it to the output binding call
- No log entry linking input message to output message

### What this means in production

- Cannot trace which input message produced which output message
- Cannot correlate errors to specific input messages
- Distributed tracing (Azure Monitor, Jaeger, Zipkin) breaks at the UTLXe boundary — the trace span stops when Dapr calls UTLXe and doesn't resume on the output side
- Audit requirements (e.g., Peppol e-invoicing) that demand end-to-end traceability cannot be met

## What Each Layer Provides

### Azure Service Bus message properties

| Property | Type | Purpose |
|----------|------|---------|
| `MessageId` | String | Unique ID for this message (set by producer, UUID if not set) |
| `CorrelationId` | String | Links related messages (request → response, saga steps) |
| `SessionId` | String | Ordered processing within a session |
| `SequenceNumber` | Long | Broker-assigned sequence number (immutable) |
| `EnqueuedTimeUtc` | DateTime | When the message entered the queue |
| Custom properties | Map | Arbitrary key-value pairs (business metadata) |

### What Dapr forwards to UTLXe

When Dapr delivers a Service Bus message to UTLXe via HTTP POST, it includes:

- **HTTP body**: the message payload (data)
- **HTTP headers**:
  - Standard headers (`Content-Type`, etc.)
  - `traceparent` — W3C Trace Context trace ID (added by Dapr automatically)
  - `tracestate` — W3C Trace Context state
- **Metadata** (accessible via Dapr's binding response format): Service Bus properties including `MessageId`, `CorrelationId`, custom properties

### What Dapr accepts on output binding calls

When UTLXe calls `POST localhost:3500/v1.0/bindings/{name}`, the request body is:

```json
{
  "operation": "create",
  "data": "transformed payload",
  "metadata": {
    "MessageId": "output-msg-id",
    "CorrelationId": "preserved-correlation-id",
    "customProperty1": "value1"
  }
}
```

Dapr passes the `metadata` map as Service Bus message properties on the output message. This is the mechanism for preserving correlation.

## Design

### 1. Read incoming metadata

Extract from the Dapr HTTP request:

```kotlin
// W3C Trace Context (OpenTelemetry)
val traceparent = call.request.header("traceparent")
val tracestate = call.request.header("tracestate")

// Service Bus properties forwarded by Dapr
val incomingMessageId = call.request.header("MessageId")
    ?: call.request.header("metadata.MessageId")
val incomingCorrelationId = call.request.header("CorrelationId")
    ?: call.request.header("metadata.CorrelationId")

// All custom properties (for pass-through)
val customProperties = call.request.headers.entries()
    .filter { it.key.startsWith("metadata.") }
    .associate { it.key.removePrefix("metadata.") to it.value.first() }
```

### 2. Propagate to output binding

Extend the output metadata map:

```kotlin
val outputMetadata = mutableMapOf(
    "source-binding" to bindingName,
    "transform-id" to transformId,
)

// Preserve correlation
if (incomingCorrelationId != null) {
    outputMetadata["CorrelationId"] = incomingCorrelationId
}
if (incomingMessageId != null) {
    outputMetadata["source-MessageId"] = incomingMessageId
}

// Forward custom properties (pass-through)
outputMetadata.putAll(customProperties)
```

The output Dapr binding call becomes:

```json
{
  "operation": "create",
  "data": "transformed payload",
  "metadata": {
    "source-binding": "orders-in",
    "transform-id": "orders-in",
    "CorrelationId": "abc-123",
    "source-MessageId": "msg-456",
    "customProperty1": "value1"
  }
}
```

### 3. Forward W3C Trace Context

Propagate `traceparent` and `tracestate` headers on the output binding HTTP call:

```kotlin
val conn = url.openConnection() as HttpURLConnection
conn.requestMethod = "POST"
conn.setRequestProperty("Content-Type", "application/json")

// Forward W3C Trace Context for distributed tracing
if (traceparent != null) conn.setRequestProperty("traceparent", traceparent)
if (tracestate != null) conn.setRequestProperty("tracestate", tracestate)
```

This ensures the OpenTelemetry trace spans the entire flow: producer → Service Bus → Dapr → UTLXe → Dapr → Service Bus → consumer. Azure Monitor, Jaeger, and Zipkin will show the transformation as a span within the distributed trace.

### 4. Log the correlation

Structured log entry for every processed message:

```
INFO  [orders-in] MessageId=msg-456 CorrelationId=abc-123 
      → transformed in 3ms → output-binding=orders-out 
      traceparent=00-trace-id-span-id-01
```

And for errors:

```
ERROR [orders-in] MessageId=msg-456 CorrelationId=abc-123 
      → FAILED at line 14: Null reference: $input.customer.address
      traceparent=00-trace-id-span-id-01
```

The error ring buffer (EF03) should also store the MessageId and CorrelationId per error entry.

## Configuration

### Default behavior (no configuration needed)

- `CorrelationId`: always forwarded if present in input
- `source-MessageId`: always set on output (the input's MessageId)
- `traceparent`/`tracestate`: always forwarded if present
- Custom properties: always forwarded (pass-through)
- Structured log: always includes MessageId and CorrelationId

### Optional: disable metadata forwarding

For transformations where custom property pass-through is not desired (e.g., privacy — input properties might contain sensitive data):

```yaml
# transform.yaml
metadataForwarding: none     # don't forward custom properties
```

Options:
- `all` (default) — forward CorrelationId, source-MessageId, all custom properties
- `correlation` — forward only CorrelationId and source-MessageId, not custom properties
- `none` — forward nothing (UTLXe still adds source-binding and transform-id)

W3C Trace Context (`traceparent`) is always forwarded regardless of this setting — it is infrastructure, not business data.

## Files to modify

| File | Change |
|------|--------|
| `modules/engine/.../transport/HttpTransport.kt` | Read incoming metadata/headers, propagate to output binding call, forward traceparent |
| `modules/engine/.../config/TransformConfig.kt` | Add `metadataForwarding` field (all/correlation/none) |
| `modules/engine/.../admin/ErrorRingBuffer.kt` (EF03) | Store MessageId and CorrelationId per error entry |
| Structured logging throughout | Include MessageId and CorrelationId in all log entries |

## What the output message looks like on Service Bus

Before EF04:
```
Output message properties:
  source-binding: "orders-in"
  transform-id: "orders-in"
  (no link to input message)
```

After EF04:
```
Output message properties:
  source-binding: "orders-in"
  transform-id: "orders-in"
  CorrelationId: "abc-123"           ← preserved from input
  source-MessageId: "msg-456"        ← link to input message
  customProperty1: "value1"          ← forwarded from input
  (traceparent propagated in HTTP headers for distributed tracing)
```

## End-to-end traceability

With EF04, the full correlation chain is:

```
Producer sets:      MessageId=msg-456, CorrelationId=abc-123
                         ↓
Service Bus:        stores message with properties
                         ↓
Dapr → UTLXe:       forwards MessageId, CorrelationId as metadata
                    adds traceparent (W3C Trace Context)
                         ↓
UTLXe log:          INFO [orders-in] MessageId=msg-456 
                    CorrelationId=abc-123 → transformed in 3ms
                         ↓
UTLXe → Dapr:       output metadata includes CorrelationId=abc-123,
                    source-MessageId=msg-456, traceparent forwarded
                         ↓
Output Service Bus: message has CorrelationId=abc-123,
                    source-MessageId=msg-456
                         ↓
Consumer:           can trace back to original message
```

Azure Monitor shows the complete distributed trace: producer → Service Bus → Dapr → UTLXe → Dapr → Service Bus → consumer.

## Effort estimate

| Task | Effort |
|------|--------|
| Read incoming Dapr metadata (headers/body) | 0.5 day |
| Propagate metadata to output binding call | 0.5 day |
| Forward W3C Trace Context (traceparent/tracestate) | 0.5 day |
| metadataForwarding config option | 0.5 day |
| Structured logging with MessageId/CorrelationId | 0.5 day |
| Error ring buffer: add MessageId/CorrelationId | 0.5 day |
| Tests | 1 day |
| **Total** | **~4 days** |

## Relationship to other features

- **EF03** (Bundle Management API): error ring buffer should include MessageId and CorrelationId per error
- **EF02** (Schema Validation): validation failure logs should include MessageId for traceability
- **Prometheus metrics**: consider adding a `correlation_id` label? No — high cardinality, would explode Prometheus. Keep it in logs only.

---

*Feature EF04. May 2026. Must-have before go-live.*
*Key insight: without metadata forwarding, the distributed trace breaks at the UTLXe boundary — making production debugging impossible.*
