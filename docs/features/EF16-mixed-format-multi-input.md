# EF16: Mixed-Format Multi-Input via Protobuf Envelope

**Status:** Design  
**Priority:** Medium (required for XML+JSON multi-input, aligns UTLXe with Open-M)  
**Created:** May 2026  
**Depends on:** EF01 (pipeline multi-input), EF07 (parallel transports/gRPC)

---

## Summary

UTLXe supports multi-input transformations (`input: order json, shipment xml`), but the current Dapr/HTTP delivery path uses a JSON envelope — forcing all inputs to be JSON. XML or CSV values become escaped strings, not parsed documents. `$shipment.Carrier` fails because `$shipment` is a string, not an XML tree.

EF16 solves this by supporting protobuf envelopes delivered via Dapr's gRPC callback, where each named input is raw bytes parsed per its declared format. This aligns UTLXe's Azure deployment with Open-M's MPPM design.

## The Problem

### What works today (all-JSON envelope)

```json
{
  "order": {"orderId": "ORD-001", "amount": 499.95},
  "customer": {"name": "Contoso", "tier": "gold"}
}
```

Both inputs are JSON objects inside a JSON envelope. UTLXe splits by key name, parses each as JSON. Works.

### What does NOT work today (mixed JSON + XML)

```json
{
  "order": {"orderId": "ORD-001", "amount": 499.95},
  "shipment": "<Shipment><Carrier>DHL</Carrier><TrackingId>DHL-123</TrackingId></Shipment>"
}
```

The envelope is JSON. The `shipment` value is a JSON string containing XML. UTLXe's envelope parser treats it as a string, not as an XML document. The transformation declares `input: order json, shipment xml`, but the parser ignores the format declaration — it parsed the entire envelope as JSON.

Result: `$shipment` is a string `"<Shipment>..."`, not an object. `$shipment.Carrier` fails.

## Two Solutions

### Solution A: Format-Aware JSON Envelope Parser (simple, no infrastructure change)

Modify UTLXe's JSON envelope parser to respect the format declaration per input:

1. Parse the envelope as JSON (as today)
2. For each named input, check the declared format from the `.utlx` header
3. If the format is `json` and the value is a JSON object → use as-is (current behavior)
4. If the format is `xml` and the value is a JSON string → re-parse the string as XML
5. If the format is `csv` and the value is a JSON string → re-parse the string as CSV

```
Envelope:  {"order": {...}, "shipment": "<Shipment>...</Shipment>"}
Header:    input: order json, shipment xml

Parser:
  "order"    → JSON object → parse as JSON → UDM object ✓
  "shipment" → JSON string → declared xml → re-parse string as XML → UDM object ✓
```

**Pros:** No infrastructure change. No Dapr change. Works with existing HTTP delivery. Simple implementation (~50 lines in the strategy code).

**Cons:** Double parsing (JSON envelope + re-parse per input). XML must be string-escaped inside JSON (ugly for humans, fine for machines). Large XML documents double the message size due to JSON string escaping.

### Solution B: Protobuf Envelope via Dapr gRPC (aligns with Open-M, full binary support)

Use Dapr's gRPC app protocol to deliver messages as protobuf, where each named input is raw bytes:

```
Dapr config:
  appProtocol: grpc
  appPort: 9090

Dapr delivers:
  gRPC call → AppCallback.OnBindingEvent(BindingEventRequest)
    name: "enriched-orders"
    data: <serialized protobuf envelope>

Inside the protobuf envelope:
  named_inputs:
    "order":    bytes → raw JSON bytes
    "shipment": bytes → raw XML bytes
```

Each input is raw bytes. No JSON escaping. No double parsing. The strategy parses each according to its declared format.

**Pros:** True binary — no escaping, no size inflation. Mixed formats work natively. Aligns with Open-M's MPPM design. Efficient for large documents.

**Cons:** Requires Dapr gRPC mode. Requires UTLXe to implement Dapr's `AppCallback` gRPC service. Requires the message producer (Durable Functions) to serialize protobuf.

## Recommendation: Implement Both

Solution A is quick (1 day) and covers the common case (JSON + XML strings). Solution B is the right long-term architecture (aligns with Open-M) but needs more work (3-4 days).

**Phase 1:** Implement Solution A. Customers get mixed-format multi-input immediately via the existing JSON envelope and HTTP/Dapr delivery. No infrastructure change.

**Phase 2:** Implement Solution B. Customers who need binary efficiency or full MPPM alignment switch Dapr to gRPC mode and use protobuf envelopes.

Both solutions are backward compatible. Existing single-input and all-JSON multi-input transformations work unchanged.

---

## Solution A: Format-Aware JSON Envelope Parser

### Where to Change

The envelope parsing happens in the execution strategies (`CompiledStrategy`, `TemplateStrategy`, `CopyStrategy`). Each strategy calls `transformationService.parseInputPublic(input, format)` for single input. For multi-input, the envelope is pre-parsed as JSON and the values are passed as JSON nodes.

The fix: after extracting each value from the JSON envelope, check the declared format. If the format is not `json` and the value is a JSON string, re-parse the string content with the declared format's parser.

### Implementation

```kotlin
// In the strategy's multi-input path:
val declaredInputs = program.header.inputs  // [(name, format), ...]

// Parse envelope as JSON (existing code)
val envelope = mapper.readTree(input)

// For each declared input:
val namedInputs = mutableMapOf<String, UDM>()
for ((name, spec) in declaredInputs) {
    val node = envelope.get(name) ?: continue
    val format = spec?.type?.name?.lowercase() ?: "json"
    
    val udm = if (format == "json" || node.isObject || node.isArray) {
        // JSON input — use the parsed JSON node directly
        transformationService.parseInputPublic(mapper.writeValueAsString(node), "json")
    } else if (node.isTextual) {
        // Non-JSON input (xml, csv, yaml) — re-parse the string value
        transformationService.parseInputPublic(node.asText(), format)
    } else {
        // Fallback
        transformationService.parseInputPublic(node.toString(), format)
    }
    namedInputs[name] = udm
}
```

### Example

Transformation:
```
%utlx 1.0
input: order json, shipment xml
output json
---
{
  orderId: $order.orderId,
  carrier: $shipment.Carrier,
  tracking: $shipment.TrackingId
}
```

Envelope (JSON, sent via Service Bus / Dapr HTTP):
```json
{
  "order": {"orderId": "ORD-001", "amount": 499.95},
  "shipment": "<Shipment><Carrier>DHL</Carrier><TrackingId>DHL-123</TrackingId></Shipment>"
}
```

Parser:
- `order` → declared `json`, value is JSON object → parse as JSON → `$order.orderId` works
- `shipment` → declared `xml`, value is JSON string → extract string → parse as XML → `$shipment.Carrier` works

Output:
```json
{"orderId": "ORD-001", "carrier": "DHL", "tracking": "DHL-123"}
```

### Effort

| Task | Effort |
|---|---|
| Modify strategy multi-input parsing | 0.5 day |
| Tests (JSON+XML, JSON+CSV, JSON+YAML) | 0.5 day |
| **Total** | **~1 day** |

---

## Solution B: Protobuf Envelope via Dapr gRPC

### Architecture

```
Durable Functions (C#)
  │
  │ 1. Correlates messages from separate queues
  │ 2. Serializes protobuf envelope:
  │      named_inputs: {
  │        "order":    raw JSON bytes,
  │        "shipment": raw XML bytes
  │      }
  │ 3. Sends to Service Bus as binary message
  │
  ▼
Azure Service Bus (binary content)
  │
  ▼
Dapr sidecar (appProtocol: grpc, appPort: 9090)
  │
  │ Reads message from Service Bus
  │ Calls UTLXe via gRPC:
  │   AppCallback.OnBindingEvent(BindingEventRequest)
  │     name: "enriched-orders"
  │     data: <raw protobuf bytes from Service Bus>
  │
  ▼
UTLXe GrpcTransport
  │
  │ Receives BindingEventRequest
  │ Parses data as protobuf envelope
  │ Extracts named_inputs (map<string, bytes>)
  │ For each input:
  │   "order"    → bytes → parse as JSON (declared format)
  │   "shipment" → bytes → parse as XML  (declared format)
  │ Transformation runs with both inputs as UDM objects
  │
  ▼
Output → Dapr → Service Bus
```

### Dapr Configuration

One change — set the app protocol to gRPC:

```yaml
# Container App Dapr config
dapr:
  appId: utlxe
  appPort: 9090           # UTLXe gRPC port
  appProtocol: grpc       # ← this enables gRPC delivery for bindings + pub/sub
```

With `appProtocol: grpc`, Dapr delivers binding and pub/sub messages via gRPC instead of HTTP. Dapr calls the `AppCallback` gRPC service that UTLXe implements.

### Dapr AppCallback Service

UTLXe needs to implement Dapr's built-in callback interface:

```protobuf
// Dapr's AppCallback service (defined by Dapr, not by us)
service AppCallback {
  // Called when a binding event arrives (Service Bus message)
  rpc OnBindingEvent(BindingEventRequest) returns (BindingEventResponse);
  
  // Called when a pub/sub event arrives (topic message)  
  rpc OnTopicEvent(TopicEventRequest) returns (TopicEventResponse);
  
  // Called at startup to list subscriptions
  rpc ListTopicSubscriptions(google.protobuf.Empty) returns (ListTopicSubscriptionsResponse);
}

message BindingEventRequest {
  string name = 1;                    // binding name (e.g., "enriched-orders")
  bytes data = 2;                     // raw message body (our protobuf envelope)
  map<string, string> metadata = 3;   // Dapr metadata (MessageId, etc.)
}
```

The `data` field contains raw bytes — whatever was in the Service Bus message. If the producer serialized a protobuf envelope, UTLXe deserializes it here.

### Protobuf Envelope for Multi-Input

Define a simple envelope (or reuse MPPM's structure):

```protobuf
// Multi-input envelope (can be a subset of MPPM)
message MultiInputEnvelope {
  map<string, bytes> named_inputs = 1;        // key = input name, value = raw content
  map<string, string> content_types = 2;      // key = input name, value = format hint
  string correlation_id = 3;
  string message_id = 4;
  string causation_id = 5;
}
```

Or directly use `ExecuteRequest.named_inputs` (field 4) which already exists in our proto:

```protobuf
message ExecuteRequest {
  string transformation_id = 1;
  bytes payload = 2;
  string content_type = 3;
  map<string, bytes> named_inputs = 4;    // ← already defined!
  string correlation_id = 5;
  string message_id = 6;
  string causation_id = 7;
  ...
}
```

The `named_inputs` field is already in the proto — it was designed for exactly this use case. Each value is raw bytes, parsed by the strategy according to the declared format.

### Durable Functions Producer (C#)

The customer's Durable Functions writes the protobuf:

```csharp
// C# — serialize multi-input envelope
var request = new ExecuteRequest
{
    TransformationId = "order-enrichment",
    CorrelationId = orderId,
    MessageId = Guid.NewGuid().ToString()
};

// Add JSON input as raw bytes
request.NamedInputs["order"] = ByteString.CopyFromUtf8(orderJson);

// Add XML input as raw bytes  
request.NamedInputs["shipment"] = ByteString.CopyFromUtf8(shipmentXml);

// Serialize and send to Service Bus
var bytes = request.ToByteArray();
await serviceBusClient.SendMessageAsync(new ServiceBusMessage(bytes)
{
    ContentType = "application/protobuf"
});
```

The C# producer uses the generated proto classes (from our `utlxe.proto`). Each input is raw bytes — no JSON escaping, no size inflation. The XML stays as XML. The JSON stays as JSON.

### UTLXe Implementation

Add `AppCallback` to GrpcTransport:

```kotlin
// In GrpcTransport — implement Dapr's AppCallback alongside UtlxeService
class DaprAppCallbackImpl(
    private val engine: UtlxEngine,
    private val registry: TransformationRegistry
) : DaprAppCallbackGrpc.DaprAppCallbackImplBase() {

    override fun onBindingEvent(
        request: BindingEventRequest,
        responseObserver: StreamObserver<BindingEventResponse>
    ) {
        val bindingName = request.name
        val data = request.data  // raw bytes from Service Bus
        
        // Try to parse as ExecuteRequest protobuf
        val execReq = try {
            ExecuteRequest.parseFrom(data)
        } catch (e: Exception) {
            // Fallback: treat as raw payload (single input)
            ExecuteRequest.newBuilder()
                .setTransformationId(bindingName)
                .setPayload(data)
                .setContentType("application/json")
                .build()
        }
        
        // Execute with named_inputs if present
        val response = TransportHandlers.handleExecute(execReq, engine)
        
        responseObserver.onNext(BindingEventResponse.newBuilder().build())
        responseObserver.onCompleted()
    }
}
```

### Relationship to Open-M MPPM

The protobuf envelope for Azure is a **subset** of Open-M's MPPM:

| Feature | MPPM (Open-M) | Azure Protobuf Envelope |
|---|---|---|
| Named inputs (raw bytes) | `map<string, bytes>` in step window | `ExecuteRequest.named_inputs` |
| Three IDs | `PipelineEnvelope` fields | `ExecuteRequest` fields |
| Step history (sliding window) | Yes — last N upstream outputs | No — only current inputs |
| Trace context | `traceparent` + hop audit trail | `traceparent` + `tracestate` |
| Wire format | Protobuf (`PipelineEnvelope`) | Protobuf (`ExecuteRequest`) |

The key alignment: **both use `map<string, bytes>` for named inputs**. A transformation that works with MPPM multi-input in Open-M works with the Azure protobuf envelope — the bytes are the same, only the outer envelope differs.

The step window is not carried in the Azure envelope because there is no pipeline middleware managing the sliding history. If a customer needs step window behavior on Azure, they would implement it in Durable Functions (storing previous outputs in entity state).

### Effort

| Task | Effort |
|---|---|
| Implement Dapr `AppCallback` gRPC service | 1 day |
| Wire `OnBindingEvent` to `TransportHandlers.handleExecute` | 0.5 day |
| Wire `OnTopicEvent` for pub/sub delivery | 0.5 day |
| Wire `ListTopicSubscriptions` (return subscriptions from registry) | 0.5 day |
| Multi-input parsing in strategies (per-format from raw bytes) | 0.5 day |
| Tests | 1 day |
| **Total** | **~4 days** |

---

## When to Use Which

| Scenario | Solution | Why |
|---|---|---|
| All inputs are JSON | JSON envelope (existing) | Simplest, works today |
| JSON + XML mixed, small documents | Solution A (format-aware JSON parser) | No infrastructure change |
| JSON + XML mixed, large documents | Solution B (protobuf via gRPC) | No JSON escaping overhead |
| Binary inputs (Avro, Protobuf) | Solution B only | Binary can't be JSON-escaped |
| Alignment with Open-M | Solution B | Same `map<string, bytes>` pattern |
| Customer uses Durable Functions | Either — A for JSON, B for protobuf | Durable Functions can produce both |

## Files to Modify

### Solution A

| File | Change |
|---|---|
| `CompiledStrategy.kt` | Format-aware envelope parsing for multi-input |
| `TemplateStrategy.kt` | Same |
| `CopyStrategy.kt` | Same |

### Solution B

| File | Change |
|---|---|
| New: `DaprAppCallbackImpl.kt` | Implement Dapr's `AppCallback` gRPC service |
| `GrpcTransport.kt` | Register `DaprAppCallbackImpl` alongside `UtlxeService` |
| `build.gradle.kts` | Add Dapr proto dependency for `AppCallback` |
| `TransportHandlers.kt` | Handle `named_inputs` from `ExecuteRequest` in `handleExecute` |

---

## UTLXe: One Engine, Two Worlds

UTLXe runs in both Open-M and Azure today. EF16 closes the remaining capability gap:

| Capability | Before EF16 | After EF16 Solution A | After EF16 Solution B |
|---|---|---|---|
| JSON multi-input | Both | Both | Both |
| Mixed format (JSON+XML) | Open-M only | Both | Both |
| Binary inputs (Avro, Protobuf) | Open-M only | No | Both |
| Step window (upstream history) | Open-M only | No | No (Durable Functions can emulate) |

The transformation file is **identical** in both worlds:

```
%utlx 1.0
input: order json, shipment xml
output json
---
{
  orderId: $order.orderId,
  carrier: $shipment.Carrier
}
```

No `#ifdef`, no platform flags, no conditional logic. Write once, deploy to Open-M or Azure.

### What each world provides around UTLXe

| Aspect | Open-M | Azure |
|---|---|---|
| Transport | stdio-proto (pipe to Go wrapper) | HTTP (Dapr) or gRPC (Dapr with `appProtocol: grpc`) |
| Multi-input delivery | `named_inputs` as `map<string, bytes>` via proto | JSON envelope (Solution A) or protobuf via gRPC (Solution B) |
| Three IDs | Wrapper manages in MPPM envelope | Dapr headers + UTLXe code |
| Tracing | Wrapper creates OTel spans | Azure Monitor agent creates spans |
| Step window | Go wrapper maintains sliding history in MPPM | Not available (Durable Functions can emulate by storing previous outputs in entity state) |
| Message broker | Pulsar or Kafka | Azure Service Bus or Event Hub |
| Orchestration | Pipeline descriptor (YAML) | Durable Functions or Logic Apps |
| Same `.utlx` file | Yes | Yes |

The only thing Azure cannot do that Open-M can: the **step window** (sliding history of upstream outputs). That is an MPPM concept managed by the Go wrapper — UTLXe does not implement it, it just receives the inputs. On Azure, if a customer needs upstream outputs, they build it in Durable Functions (storing previous outputs in entity state and including them in the envelope).

---

*Feature EF16. May 2026. Design document.*
*Key insight: the `ExecuteRequest.named_inputs` field (map\<string, bytes\>) already exists in the proto — it was designed for multi-input. Solution A (format-aware JSON parser) is a quick fix for the common case. Solution B (protobuf via Dapr gRPC) is the right architecture — it aligns UTLXe's Azure deployment with Open-M's MPPM design, uses the same bytes-per-input model, and avoids JSON escaping for non-JSON formats.*
