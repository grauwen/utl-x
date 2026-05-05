= Connecting to Azure Services

UTLXe integrates with Azure messaging services through a Dapr sidecar. Dapr handles the connection to Service Bus, Event Hub, and other Azure services, while UTLXe focuses on the transformation logic. This chapter covers the most common integration patterns.

== How Dapr Works with UTLXe

Dapr runs as a sidecar container alongside UTLXe in the same Container App. It acts as a message broker adapter:

+ A message arrives on Azure Service Bus (or Event Hub).
+ Dapr receives the message and forwards it to UTLXe via HTTP: `POST /api/dapr/input/{bindingName}`.
+ UTLXe transforms the message.
+ UTLXe sends the result back to Dapr: `POST http://localhost:3500/v1.0/bindings/{outputBinding}`.
+ Dapr delivers the transformed message to the output queue or topic.
+ UTLXe acknowledges the original message (HTTP 200), and Dapr completes it on Service Bus.

UTLXe never connects directly to Service Bus or Event Hub. Dapr abstracts the messaging infrastructure, which means the same UTLXe container works with different messaging systems by changing only the Dapr configuration.

== Azure Service Bus

=== Receiving from a Queue

Configure a Dapr input binding that delivers messages to UTLXe. The binding name maps to the transformation name.

Dapr component (`servicebus-input.yaml`):

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: orders-in
spec:
  type: bindings.azure.servicebusqueues
  metadata:
    - name: connectionString
      secretKeyRef:
        name: servicebus-connection
        key: connectionString
    - name: queueName
      value: "incoming-orders"
    - name: maxConcurrentHandlers
      value: "10"
```

When a message arrives on the `incoming-orders` queue, Dapr calls `POST /api/dapr/input/orders-in` on UTLXe. UTLXe looks up a transformation named `orders-in` (or uses the `X-UTLXe-Transform` header to specify a different one) and processes the message.

=== Sending to a Queue

Configure a Dapr output binding for the transformed messages:

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: orders-out
spec:
  type: bindings.azure.servicebusqueues
  metadata:
    - name: connectionString
      secretKeyRef:
        name: servicebus-connection
        key: connectionString
    - name: queueName
      value: "processed-orders"
```

In the transformation's `transform.yaml`, specify the output binding:

```yaml
strategy: COMPILED
outputBinding: orders-out
```

After transformation, UTLXe automatically sends the result to Dapr's output binding, which delivers it to the `processed-orders` queue.

=== Topics and Subscriptions

For publish/subscribe patterns, use Service Bus topics instead of queues. The Dapr component type changes to `bindings.azure.servicebustopics`, but the UTLXe integration is identical --- Dapr delivers messages the same way.

=== Dead-Letter Queue

When a transformation fails, UTLXe returns HTTP 500 to Dapr. Dapr abandons the message on Service Bus, which increments its delivery count. After the maximum number of retries (configured on the Service Bus queue), the message moves to the dead-letter queue.

You can inspect recent errors via the Admin API:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/orders-in/errors
```

This returns the last 100 errors with timestamps, error messages, and a preview of the input that caused the failure.

== Azure Event Hub

Event Hub integration follows the same pattern as Service Bus, with a different Dapr component type.

Input binding (`eventhub-input.yaml`):

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: events-in
spec:
  type: bindings.azure.eventhubs
  metadata:
    - name: connectionString
      secretKeyRef:
        name: eventhub-connection
        key: connectionString
    - name: consumerGroup
      value: "utlxe-consumer"
    - name: storageAccountName
      value: "stcheckpoints"
    - name: storageContainerName
      value: "checkpoints"
```

Event Hub uses consumer groups and checkpointing for partition management. Dapr handles checkpointing automatically --- UTLXe does not need to manage offsets.

Output binding:

```yaml
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: events-out
spec:
  type: bindings.azure.eventhubs
  metadata:
    - name: connectionString
      secretKeyRef:
        name: eventhub-connection
        key: connectionString
```

== Direct HTTP (Without Dapr)

Not all integrations require Dapr. For simple request/response patterns, clients can call the data plane directly:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d @order.json \
  http://<ingress>:8085/api/transform/invoice-to-ubl
```

This is suitable for:
- API gateway integration --- transform requests or responses inline.
- Batch processing --- send messages from a script or pipeline.
- Testing and development --- call the transformation directly.

== Worked Example: Dynamics 365 to Peppol

A complete end-to-end flow for the European e-invoicing scenario:

+ *Dynamics 365 Business Central* publishes an invoice as OData JSON to a Service Bus queue.
+ *Dapr* delivers the message to UTLXe.
+ *UTLXe* runs three chained transformations:
  - `invoice-to-ubl` --- converts D365 JSON to UBL 2.1 XML.
  - `validate-ubl` --- validates the UBL against Peppol BIS 3.0 rules.
  - `route-by-country` --- adds country-specific tax rules.
+ *UTLXe* sends the final UBL XML to Dapr's output binding.
+ *Dapr* publishes to an output Service Bus topic.
+ A *Peppol Access Point* consumes from the topic and delivers the invoice via AS4.

All three transformations are uploaded as a single bundle:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@peppol-bundle.zip" \
  http://<admin>:8081/admin/bundle
```

The bundle contains the three `.utlx` files, their `transform.yaml` configs specifying the pipeline order, and the UBL and Peppol schemas for validation.
