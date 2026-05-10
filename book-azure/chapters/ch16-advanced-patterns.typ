= Advanced Integration Patterns

This chapter covers patterns that go beyond simple queue-to-queue transformation: multi-input aggregation, mixed-format inputs, and combining UTLXe with Azure Durable Functions.

== Multi-Input Transformations Same Format

UTL-X supports transformations with multiple named inputs. Each input can have its own definition but are both json format:

```
%utlx 1.0
input: order json, customer json
output json
---
{
  invoiceId: concat("INV-", $order.orderId),
  buyer: $customer.name,
  discount: if $customer.tier == "gold" then $order.amount * 0.10 else 0,
  total: $order.amount - (if $customer.tier == "gold" then $order.amount * 0.10 else 0)
}
```

This transformation needs two inputs simultaneously: an order and a customer record. But Azure Service Bus delivers one message at a time. How do you provide both?

=== The Envelope Pattern

The sender combines all inputs into a single JSON envelope before sending to Service Bus:

```json
{
  "order": {"orderId": "ORD-001", "amount": 499.95, "items": 3},
  "customer": {"name": "Contoso Ltd", "tier": "gold", "country": "NL"}
}
```

UTLXe's multi-input parser splits the envelope by key name. The key names must match the input names in the header (`order`, `customer`). Each input is parsed according to its declared format.

The `transform.yaml` is standard --- no special multi-input configuration:

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
Queue "orders"    → Azure Function trigger → Durable Entity "ORD-001"
                                               ├── stores order data
                                               └── both inputs? No → wait

Queue "customers" → Azure Function trigger → Durable Entity "ORD-001"
                                               ├── stores customer data
                                               └── both inputs? Yes → combine
                                                      ↓
                                               Send envelope to queue "enriched-orders"
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

The aggregator is a Durable Entity --- a stateful actor keyed by the business ID (e.g., `orderId`). It accumulates inputs until all are present, then emits the envelope.

```csharp
// C# — Durable Entity aggregator (~30 lines)

public class OrderAggregator
{
    public string Order { get; set; }
    public string Customer { get; set; }

    public void AddOrder(string data) => Order = data;
    public void AddCustomer(string data) => Customer = data;

    public bool IsComplete => Order != null && Customer != null;

    [FunctionName("OrderAggregator")]
    public static Task Run(
        [EntityTrigger] IDurableEntityContext ctx)
        => ctx.DispatchAsync<OrderAggregator>();
}

// Trigger: listens on "orders" queue
[FunctionName("OrderTrigger")]
public static async Task OrderTrigger(
    [ServiceBusTrigger("orders")] string message,
    [DurableClient] IDurableEntityClient client)
{
    var order = JObject.Parse(message);
    var entityId = new EntityId("OrderAggregator", order["orderId"].ToString());
    await client.SignalEntityAsync(entityId, "AddOrder", message);
}

// Trigger: listens on "customers" queue
[FunctionName("CustomerTrigger")]
public static async Task CustomerTrigger(
    [ServiceBusTrigger("customers")] string message,
    [DurableClient] IDurableEntityClient client)
{
    var customer = JObject.Parse(message);
    var entityId = new EntityId("OrderAggregator", customer["orderId"].ToString());
    await client.SignalEntityAsync(entityId, "AddCustomer", message);

    // Check if complete — if so, send envelope
    var state = await client.ReadEntityStateAsync<OrderAggregator>(entityId);
    if (state.EntityState?.IsComplete == true)
    {
        var envelope = $"{{\"order\":{state.EntityState.Order},\"customer\":{state.EntityState.Customer}}}";
        // Send to Service Bus queue "enriched-orders"
        await serviceBusClient.SendMessageAsync(new ServiceBusMessage(envelope));
        await client.SignalEntityAsync(entityId, "Delete");
    }
}
```

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

What if one input never arrives?

- *Durable Entity timeout:* configure a timer that fires after N minutes. If the entity is still incomplete, send the partial data to a dead-letter queue or log a warning.
- *Service Bus message lock:* if the aggregator crashes mid-processing, Service Bus retries delivery. Durable Entities are persistent --- state survives restarts.
- *Duplicate detection:* Service Bus has built-in duplicate detection. If the same message is delivered twice, the entity ignores the duplicate (idempotent `Add` operations).

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
