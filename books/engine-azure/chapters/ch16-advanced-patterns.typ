= Advanced Integration Patterns

This chapter covers patterns that go beyond simple queue-to-queue transformation: multi-input aggregation, mixed-format inputs, and combining UTLXe with Azure Durable Functions.

== Multi-Input Transformations

UTL-X supports transformations with any number of named inputs, each with its own format. The language is format-agnostic --- JSON, XML, CSV, YAML, or any supported format can be combined freely:

```
%utlx 1.0
input: order json, shipment xml, priceList csv
output json
---
{
  invoiceId: concat("INV-", $order.orderId),
  carrier: $shipment.Carrier,
  trackingId: $shipment.TrackingId,
  unitPrice: $priceList[0].price,
  total: $order.quantity * $priceList[0].price
}
```

This transformation needs three inputs simultaneously: a JSON order, an XML shipping manifest, and a CSV price list. Azure Service Bus delivers one message at a time. How do you provide all three?

=== The Envelope Pattern

The sender combines all inputs into a single JSON envelope before sending to Service Bus. The key names must match the input names in the header:

```json
{
  "order": {"orderId": "ORD-001", "quantity": 5},
  "shipment": "<Shipment><Carrier>DHL</Carrier><TrackingId>DHL-123</TrackingId></Shipment>",
  "priceList": "product,price\nWidget-A,24.50"
}
```

Non-JSON inputs (XML, CSV) are string values inside the JSON envelope. UTLXe parses each according to its declared format: `$order` as JSON, `$shipment` as XML, `$priceList` as CSV.

*Current limitation:* the format-aware re-parsing of non-JSON string values is planned (EF16). Today, all inputs in the envelope must be JSON objects. See the "Mixed Formats" section below for details and workarounds.

The pattern works with any number of inputs --- 2, 3, 5, or more. Each input is a key in the envelope. The `transform.yaml` is standard:

```yaml
strategy: COMPILED
maxInputSize: 500KB
input:
  queue: enriched-orders
output:
  queue: invoices-out
```

=== Mixed Formats (JSON + XML) — Not Yet Supported

The UTL-X language supports declaring different formats per input (`input: order json, shipment xml`). However, this *does not work today* with the JSON envelope.

The reason: the envelope is a JSON document. UTLXe's envelope parser treats all values as JSON objects or strings. When the envelope contains:

```json
{
  "order": {"orderId": "ORD-001", "amount": 499.95},
  "shipment": "<Shipment><Carrier>DHL</Carrier></Shipment>"
}
```

The `shipment` value is a JSON string --- not a parsed XML tree. The transformation declares `input: order json, shipment xml`, but the parser ignores the format declaration. `$shipment.Carrier` fails because `$shipment` is a string, not an XML object.

This is *not a Dapr limitation* --- Dapr delivers whatever bytes are in the Service Bus message. The limitation is in UTLXe's envelope parser, which does not re-parse string values per their declared format.

*Workaround:* convert the XML to JSON before adding it to the envelope. The transformation then works with all-JSON inputs. The conversion can be done by the sender, a preceding UTLXe transformation, or the Durable Functions aggregator.

*Planned fix (EF16):* two approaches are designed:
- *Format-aware JSON parser* --- UTLXe detects non-JSON formats in the declaration and re-parses string values accordingly. No infrastructure change needed.
- *Protobuf envelope via gRPC* --- each input is raw bytes, parsed per format. Requires Dapr gRPC mode. See below.

=== Protobuf Envelope via gRPC (Planned)

For large documents or binary formats (Avro, Protobuf), JSON string escaping adds overhead. An alternative: use Dapr's gRPC mode to deliver protobuf envelopes where each input is raw bytes.

```yaml
# Dapr config change (one line)
dapr:
  appProtocol: grpc
  appPort: 9090
```

With gRPC mode, the producer (e.g., Durable Functions) serializes each input as raw bytes in a protobuf envelope. No JSON escaping. XML stays as XML bytes. Avro stays as Avro bytes. UTLXe parses each according to the declared format.

This is the same `map<string, bytes>` model used by Open-M's MPPM protocol --- transformations are portable between both platforms without modification. See the UTLXe architecture documentation for the full alignment.

== The Aggregation Problem

The envelope pattern works when the sender can combine all inputs before sending. But what if the inputs arrive on *separate queues* at *different times*?

```
09:00:01  Queue "orders":    {"orderId":"ORD-001","amount":499.95}
09:00:03  Queue "customers": {"customerId":"C-100","name":"Contoso","tier":"gold"}
```

UTLXe listens on one queue at a time. It cannot "wait for message A from queue-orders AND message B from queue-customers with matching IDs, then combine them." That is an *aggregation* problem --- a coordination concern, not a transformation concern.

== Azure Durable Functions as Aggregator

Azure Durable Functions is Microsoft's solution for message aggregation. It is serverless, pay-per-execution, and has built-in support for the aggregator pattern.

The architecture:

```
Queue "orders"     → Function trigger → Durable Entity "ORD-001"
                                                ├── stores order (JSON)
                                                └── all 3 inputs? No → wait

Queue "shipments"  → Function trigger → Durable Entity "ORD-001"
                                                ├── stores shipment (XML)
                                                └── all 3 inputs? No → wait

Queue "pricelists" → Function trigger → Durable Entity "ORD-001"
                                                ├── stores priceList (CSV)
                                                └── all 3 inputs? Yes → combine
                                                       ↓
                                                Build envelope (JSON wrapper)
                                                       ↓
                                                Queue "enriched-orders"
                                                       ↓
                                                Dapr → UTLXe (multi-input transformation)
                                                       ↓
                                                Output to queue "invoices-out"
```

=== What each system does

#table(
  columns: (auto, 1fr),
  [*System*], [*Responsibility*],
  [Azure Service Bus], [Delivers messages from producers. Holds messages until consumed.],
  [Azure Durable Functions], [Listens on input queues. Correlates messages by business ID. Waits for all inputs. Combines into an envelope. Sends to the aggregation output queue.],
  [Dapr sidecar], [Picks up the envelope from the output queue. Delivers to UTLXe via HTTP.],
  [UTLXe], [Receives the envelope. Splits by key name. Transforms with all inputs available. Sends output via Dapr.],
)

From UTLXe's perspective: *nothing changes*. It receives a single message that happens to contain all the data it needs. UTLXe does not know about Durable Functions, does not interact with it, and requires no special configuration.

From Dapr's perspective: *nothing changes*. One queue binding (`enriched-orders`), one message delivery.

=== The Durable Functions code

The aggregator is a *Durable Entity* --- a stateful actor keyed by the business ID (e.g., `orderId`). Its state is automatically persisted in Azure Table Storage. When a message arrives, the entity's state is loaded by key, updated, and saved. No manual database code.

==== How state and correlation work

```
09:00:01  Message arrives on queue "orders":
          {"orderId": "ORD-001", "amount": 499.95}
            ↓
          Trigger extracts orderId = "ORD-001"
          Signals entity "ORD-001" → AddOrder(json)
            ↓
          Entity state (Table Storage, key="ORD-001"):
            Order:    '{"orderId":"ORD-001","amount":499.95}'
            Customer: null
            → not complete, wait

09:00:03  Message arrives on queue "customers":
          {"orderId": "ORD-001", "name": "Contoso", "tier": "gold"}
            ↓
          Trigger extracts orderId = "ORD-001"
          Signals entity "ORD-001" → AddCustomer(json)
            ↓
          Entity state (Table Storage, key="ORD-001"):
            Order:    '{"orderId":"ORD-001","amount":499.95}'
            Customer: '{"orderId":"ORD-001","name":"Contoso","tier":"gold"}'
            → COMPLETE! Combine and send envelope
            → Delete entity state (cleanup)
```

The entity ID (`"ORD-001"`) IS the correlation key. Azure Table Storage loads the entity by partition key in O(1) --- no scanning, no index needed. If 10,000 orders are in progress simultaneously, each is a separate entity with its own state.

Messages can arrive in any order. If the customer arrives before the order, the entity stores the customer data and waits. When the order arrives, both are present and the envelope is sent.

==== Complete C\# implementation (generic N-input aggregator)

This aggregator handles any number of input queues. Each input is stored by name. When all expected inputs have arrived, the envelope is sent. The format of each input does not matter --- the aggregator stores raw message strings and lets UTLXe handle parsing.

```csharp
// ── Entity: generic N-input aggregator ──

public class MessageAggregator
{
    // State — persisted automatically in Azure Table Storage
    public Dictionary<string, string> Inputs { get; set; } = new();
    public HashSet<string> ExpectedInputs { get; set; } = new();
    public DateTime FirstSeen { get; set; }

    // Called from any queue trigger — stores one named input
    public void AddInput((string name, string data) input)
    {
        Inputs[input.name] = input.data;
        if (FirstSeen == default) FirstSeen = DateTime.UtcNow;
    }

    // Configure which inputs are needed (set once on first signal)
    public void SetExpected(HashSet<string> expected)
    {
        if (ExpectedInputs.Count == 0) ExpectedInputs = expected;
    }

    // Complete when all expected inputs have arrived
    public bool IsComplete =>
        ExpectedInputs.Count > 0 &&
        ExpectedInputs.All(name => Inputs.ContainsKey(name));

    // Build UTLXe envelope — each input as a key in the JSON object
    public string BuildEnvelope()
    {
        var parts = Inputs.Select(kv =>
        {
            // If the value looks like JSON object/array, embed directly
            var v = kv.Value.TrimStart();
            if (v.StartsWith("{") || v.StartsWith("["))
                return $"\"{kv.Key}\":{kv.Value}";
            // Otherwise (XML, CSV, etc.) — embed as JSON string
            return $"\"{kv.Key}\":{JsonConvert.SerializeObject(kv.Value)}";
        });
        return "{" + string.Join(",", parts) + "}";
    }

    [FunctionName("MessageAggregator")]
    public static Task Run(
        [EntityTrigger] IDurableEntityContext ctx)
        => ctx.DispatchAsync<MessageAggregator>();
}

// ── Generic queue trigger factory ──
// Create one trigger per input queue. Each extracts the correlation key
// and signals the entity with its input name.

[FunctionName("OrderTrigger")]
public static Task OrderTrigger(
    [ServiceBusTrigger("orders", Connection = "SB")] string msg,
    [DurableClient] IDurableEntityClient client,
    [ServiceBus("enriched-orders", Connection = "SB")]
        IAsyncCollector<ServiceBusMessage> output,
    ILogger log)
    => HandleInput("order", msg, client, output, log);

[FunctionName("ShipmentTrigger")]
public static Task ShipmentTrigger(
    [ServiceBusTrigger("shipments", Connection = "SB")] string msg,
    [DurableClient] IDurableEntityClient client,
    [ServiceBus("enriched-orders", Connection = "SB")]
        IAsyncCollector<ServiceBusMessage> output,
    ILogger log)
    => HandleInput("shipment", msg, client, output, log);

[FunctionName("PriceListTrigger")]
public static Task PriceListTrigger(
    [ServiceBusTrigger("pricelists", Connection = "SB")] string msg,
    [DurableClient] IDurableEntityClient client,
    [ServiceBus("enriched-orders", Connection = "SB")]
        IAsyncCollector<ServiceBusMessage> output,
    ILogger log)
    => HandleInput("priceList", msg, client, output, log);

// ── Shared handler — works for any input ──

private static readonly HashSet<string> Required =
    new() { "order", "shipment", "priceList" };

private static async Task HandleInput(
    string inputName, string message,
    IDurableEntityClient client,
    IAsyncCollector<ServiceBusMessage> output,
    ILogger log)
{
    // Extract correlation key from the message
    // Convention: every message has an "orderId" field (JSON) or
    // an <OrderId> element (XML). Adapt extraction per format.
    var correlationKey = ExtractCorrelationKey(message);
    if (correlationKey == null)
    {
        log.LogWarning("{Input} message without correlation key, skipping", inputName);
        return;
    }

    var entityId = new EntityId("MessageAggregator", correlationKey);

    // Tell the entity which inputs are expected (idempotent)
    await client.SignalEntityAsync(entityId, "SetExpected", Required);

    // Add this input
    await client.SignalEntityAsync(entityId, "AddInput", (inputName, message));

    // Check if all inputs arrived
    await Task.Delay(500);  // eventual consistency
    var state = await client.ReadEntityStateAsync<MessageAggregator>(entityId);
    if (state.EntityState?.IsComplete != true) return;

    // All inputs arrived — send envelope to UTLXe
    var envelope = state.EntityState.BuildEnvelope();
    await output.AddAsync(new ServiceBusMessage(envelope)
    {
        CorrelationId = correlationKey,
        ContentType = "application/json"
    });

    log.LogInformation("Aggregated {Key} ({Count} inputs) — sent to enriched-orders",
        correlationKey, Required.Count);

    await client.SignalEntityAsync(entityId, "Delete");
}

// Extract orderId from JSON or XML message
private static string ExtractCorrelationKey(string message)
{
    var trimmed = message.TrimStart();
    if (trimmed.StartsWith("{"))
    {
        // JSON — look for orderId field
        var json = JObject.Parse(message);
        return json["orderId"]?.ToString();
    }
    if (trimmed.StartsWith("<"))
    {
        // XML — look for <OrderId> element
        var doc = System.Xml.Linq.XDocument.Parse(message);
        return doc.Descendants("OrderId").FirstOrDefault()?.Value
            ?? doc.Root?.Attribute("orderId")?.Value;
    }
    // CSV or other — first field might be the key
    return message.Split(',').FirstOrDefault()?.Trim();
}
```

==== Key design decisions

#table(
  columns: (auto, 1fr),
  [*Decision*], [*Why*],
  [Entity ID = correlation key (orderId)], [Table Storage loads it in O(1). 10,000 concurrent orders = 10,000 independent entities. No scanning, no manual index.],
  [Generic N-input], [`ExpectedInputs` is a set of names. Add a queue + trigger for each new input. The entity and envelope builder don't change.],
  [Format-agnostic storage], [Entity stores raw message strings. JSON, XML, CSV --- the entity does not parse. UTLXe handles format parsing.],
  [Smart envelope builder], [JSON objects/arrays embedded directly. XML/CSV strings escaped as JSON strings. UTLXe re-parses per declared format (when EF16 ships).],
  [Correlation key extraction], [`ExtractCorrelationKey` handles JSON (field lookup), XML (element search), and CSV (first field). Adapt per your message structure.],
  [State persists automatically], [Durable Entities store in Azure Table Storage. Survives restarts, crashes, and scale events.],
  [Any arrival order], [First input stored, entity waits. Second stored, waits. Nth completes — envelope sent. Order doesn't matter.],
  [Delete after send], [Cleanup prevents stale state. Reprocessing creates a fresh entity.],
  [CorrelationId on output], [Service Bus message carries the orderId. UTLXe preserves it for end-to-end tracing.],
)

=== Setup effort

#table(
  columns: (auto, auto),
  [*Step*], [*Effort*],
  [Create Azure Functions project], [5 minutes],
  [Write entity + two triggers], [30 minutes],
  [Deploy to Azure], [10 minutes],
  [Create Service Bus queues], [Already done],
  [Configure UTLXe transformation], [Already done --- just point at `enriched-orders` queue],
)

Cost: consumption plan --- pay per execution. 10,000 message pairs per day costs less than \$1/month.

=== Timeout and error handling

What if one input never arrives? The entity has a `FirstSeen` timestamp. A separate timer function can scan for stale entities:

- *Timeout:* a scheduled Function runs every 5 minutes, queries entities older than 30 minutes that are still incomplete. It sends the partial data to a dead-letter queue and deletes the entity. The operator investigates why the matching message never arrived.
- *Crash recovery:* if the aggregator crashes mid-processing, Service Bus retries delivery (the message wasn't acknowledged). Durable Entity state survives --- the entity resumes where it left off.
- *Duplicate messages:* if Service Bus delivers the same message twice (at-least-once), the entity's `AddOrder` overwrites the same data. The `IsComplete` check and `Delete` after send ensure the envelope is sent exactly once.
- *Out-of-order:* customer arrives before order? Works --- the entity stores whichever comes first.
- *10,000 concurrent orders:* each is a separate entity with its own state. No contention, no shared locks. Azure Table Storage handles the scale.

== When to Use Each Pattern

#table(
  columns: (auto, 1fr, auto),
  [*Pattern*], [*When*], [*Extra infrastructure*],
  [Single input], [One message → one transformation], [None],
  [Envelope (sender combines)], [Sender has all data and can combine before sending], [None],
  [Durable Functions aggregator], [Inputs arrive on separate queues at different times], [Azure Functions (serverless)],
  [Direct HTTP with multi-input], [Client has all data and calls UTLXe synchronously], [None],
)

The simplest pattern that works is the right pattern. Don't add Durable Functions if the sender can combine the data itself.

== Portability: Azure and Open-M

UTLXe is the transformation engine for two deployment models. The same `.utlx` transformation file works in both --- no modification, no platform flags:

#table(
  columns: (auto, 1fr, 1fr),
  [*Aspect*], [*Azure (this book)*], [*Open-M*],
  [Transport], [HTTP via Dapr sidecar], [stdio-proto pipe to Go wrapper],
  [Message broker], [Azure Service Bus / Event Hub], [Apache Pulsar / Kafka],
  [Multi-input delivery], [JSON envelope or protobuf via gRPC], [`map<string, bytes>` in MPPM protobuf],
  [Mixed formats], [JSON envelope (re-parsed per format) or protobuf via gRPC], [Raw bytes per input (native)],
  [Three IDs], [Dapr HTTP headers + UTLXe code], [Go wrapper manages in MPPM envelope],
  [Distributed tracing], [Azure Monitor agent (Application Insights)], [Go wrapper creates OTel spans (Jaeger/Tempo)],
  [Orchestration], [Durable Functions / Logic Apps], [Pipeline descriptor (YAML)],
  [Same `.utlx` file], [Yes], [Yes],
)

A transformation developed on a developer's laptop, tested via the Admin Web UI on Azure, and deployed to Open-M in production --- uses the same `.utlx` file at every stage. The platform provides the transport, the broker, and the tracing. The transformation expresses only the mapping.
