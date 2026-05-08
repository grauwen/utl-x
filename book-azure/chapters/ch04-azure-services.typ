= Connecting to Azure Services

This chapter walks through every step of connecting UTLXe to Azure Service Bus and Event Hub. Each section is a complete, self-contained walkthrough --- from creating the Azure resources to sending the first message through UTLXe.

== Prerequisites

Before you begin, you need:

- An Azure subscription.
- The Azure CLI installed (`az` command). Install from `https://docs.microsoft.com/en-us/cli/azure/install-azure-cli`.
- A deployed UTLXe Container App (see Chapter 2: Quick Start).
- The UTLXe admin key (`UTLXE_ADMIN_KEY`) configured on the container.

All commands in this chapter use the Azure CLI. Where relevant, the equivalent Azure Portal steps are noted.

== Walkthrough: Service Bus Queue to Queue

This walkthrough connects an Azure Service Bus input queue to UTLXe, transforms messages, and sends results to an output queue.

=== Step 1: Create the Service Bus Namespace and Queues

```bash
# Variables — adjust to your environment
RESOURCE_GROUP="myResourceGroup"
LOCATION="westeurope"
SB_NAMESPACE="sb-utlxe-demo"

# Create the Service Bus namespace
az servicebus namespace create \
  --name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard

# Create the input queue
az servicebus queue create \
  --name incoming-orders \
  --namespace-name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP

# Create the output queue
az servicebus queue create \
  --name processed-orders \
  --namespace-name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP
```

_Portal alternative:_ In the Azure Portal, go to "Create a resource" > "Service Bus" > fill in namespace name, resource group, region, and Standard tier. Then open the namespace and click "+ Queue" twice to create `incoming-orders` and `processed-orders`.

=== Step 2: Get the Connection String

```bash
# Get the connection string for the Dapr component
az servicebus namespace authorization-rule keys list \
  --name RootManageSharedAccessKey \
  --namespace-name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --query primaryConnectionString -o tsv
```

Copy the connection string. You will store it as a secret in the Container App environment.

=== Step 3: Store the Connection String as a Secret

```bash
CONTAINER_APP="utlxe"
CONTAINER_ENV="my-container-env"

# Store as a secret in the Container App
az containerapp secret set \
  --name $CONTAINER_APP \
  --resource-group $RESOURCE_GROUP \
  --secrets servicebus-connection="<paste connection string>"
```

_Portal alternative:_ Open the Container App > "Secrets" > "+ Add" > name: `servicebus-connection`, value: paste the connection string.

=== Step 4: Configure the Dapr Input Component

Enable Dapr on the Container App and add the input binding component:

```bash
# Enable Dapr on the Container App (if not already enabled)
az containerapp dapr enable \
  --name $CONTAINER_APP \
  --resource-group $RESOURCE_GROUP \
  --dapr-app-id utlxe \
  --dapr-app-port 8085

# Create the Dapr component for the input queue
az containerapp env dapr-component set \
  --name $CONTAINER_ENV \
  --resource-group $RESOURCE_GROUP \
  --dapr-component-name orders-in \
  --yaml dapr-input.yaml
```

Create `dapr-input.yaml`:

```yaml
componentType: bindings.azure.servicebusqueues
version: v1
metadata:
  - name: connectionString
    secretRef: servicebus-connection
  - name: queueName
    value: "incoming-orders"
scopes:
  - utlxe
```

_Portal alternative:_ Open the Container App Environment > "Dapr components" > "+ Add" > component name: `orders-in`, type: `bindings.azure.servicebusqueues`. Add metadata: `connectionString` (secret reference: `servicebus-connection`), `queueName`: `incoming-orders`. Scope to `utlxe`.

=== Step 5: Configure the Dapr Output Component

```bash
az containerapp env dapr-component set \
  --name $CONTAINER_ENV \
  --resource-group $RESOURCE_GROUP \
  --dapr-component-name orders-out \
  --yaml dapr-output.yaml
```

Create `dapr-output.yaml`:

```yaml
componentType: bindings.azure.servicebusqueues
version: v1
metadata:
  - name: connectionString
    secretRef: servicebus-connection
  - name: queueName
    value: "processed-orders"
scopes:
  - utlxe
```

=== Step 6: Upload the Transformation

Create `orders-in.utlx` --- the transformation name must match the Dapr input component name (`orders-in`):

```
%utlx 1.0
input json
output json
---
{
  processedOrderId: concat("PROC-", $input.orderId),
  customer: upperCase($input.customerName),
  total: round($input.quantity * $input.unitPrice, 2),
  processedAt: now()
}
```

Upload it with the output binding configured:

```bash
# Upload the transformation
curl -X POST \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -F "source=@orders-in.utlx" \
  http://<admin-ip>:8081/admin/transformations/orders-in

# Optionally set the output binding via config
curl -X POST \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"outputBinding": "orders-out"}' \
  http://<admin-ip>:8081/admin/transformations/orders-in/config
```

=== Step 7: Test the Transformation

Before sending real messages, test with sample input:

```bash
curl -X POST \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerName":"Acme Corp","quantity":5,"unitPrice":24.50}' \
  http://<admin-ip>:8081/admin/transformations/orders-in/test
```

Expected response:

```json
{
  "status": "ok",
  "output": {
    "processedOrderId": "PROC-ORD-001",
    "customer": "ACME CORP",
    "total": 122.5,
    "processedAt": "2026-05-05T14:30:00Z"
  },
  "duration_ms": 2
}
```

=== Step 8: Send a Message to Service Bus

```bash
# Send a test message to the input queue
az servicebus queue send \
  --name incoming-orders \
  --namespace-name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --body '{"orderId":"ORD-001","customerName":"Acme Corp","quantity":5,"unitPrice":24.50}'
```

_Portal alternative:_ Open the Service Bus namespace > "incoming-orders" queue > "Service Bus Explorer" > "Send message" > paste the JSON body > click "Send".

=== Step 9: Check the Output Queue

```bash
# Peek at the output queue
az servicebus queue peek \
  --name processed-orders \
  --namespace-name $SB_NAMESPACE \
  --resource-group $RESOURCE_GROUP
```

You should see the transformed message:

```json
{
  "processedOrderId": "PROC-ORD-001",
  "customer": "ACME CORP",
  "total": 122.5,
  "processedAt": "2026-05-05T14:30:05Z"
}
```

The complete flow is now working: Service Bus input queue → Dapr → UTLXe → Dapr → Service Bus output queue.

=== What Happens on Failure

If the transformation fails (for example, a required field is missing), UTLXe returns HTTP 500 to Dapr. Dapr does not acknowledge the message on Service Bus. Service Bus retries delivery based on the queue's retry policy. After the maximum number of retries, the message moves to the dead-letter queue.

Check recent errors:

```bash
curl -H "X-Admin-Key: $ADMIN_KEY" \
  http://<admin-ip>:8081/admin/transformations/orders-in/errors
```

== Walkthrough: Event Hub

Event Hub uses the same pattern with a different Dapr component type. Only the differences from the Service Bus walkthrough are shown.

=== Create the Event Hub

```bash
EH_NAMESPACE="eh-utlxe-demo"

az eventhubs namespace create \
  --name $EH_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard

az eventhubs eventhub create \
  --name incoming-events \
  --namespace-name $EH_NAMESPACE \
  --resource-group $RESOURCE_GROUP \
  --partition-count 4

# Storage account for checkpointing
az storage account create \
  --name stutlxecheckpoint \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS

az storage container create \
  --name checkpoints \
  --account-name stutlxecheckpoint
```

=== Dapr Component

Create `dapr-eventhub-input.yaml`:

```yaml
componentType: bindings.azure.eventhubs
version: v1
metadata:
  - name: connectionString
    secretRef: eventhub-connection
  - name: consumerGroup
    value: "utlxe-consumer"
  - name: storageAccountName
    value: "stutlxecheckpoint"
  - name: storageContainerName
    value: "checkpoints"
  - name: storageAccountKey
    secretRef: storage-account-key
scopes:
  - utlxe
```

The transformation is identical to the Service Bus example --- UTLXe does not know or care whether the message came from Service Bus or Event Hub.

== Direct HTTP (No Dapr)

For synchronous request/response patterns, clients call UTLXe directly on port 8085. No Dapr configuration is needed.

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerName":"Acme Corp","quantity":5,"unitPrice":24.50}' \
  http://<ingress-url>:8085/api/transform/orders-in
```

The response is the transformed message, returned synchronously. This is suitable for:

- API gateway integration --- transform requests or responses inline.
- Batch processing --- send messages from a script or pipeline.
- Testing and development.

No Dapr component, no Service Bus, no queue configuration. Just HTTP in, HTTP out.

== Worked Example: Dynamics 365 to Peppol

A complete end-to-end flow for European e-invoicing:

+ *Dynamics 365 Business Central* publishes an invoice as OData JSON to a Service Bus queue (using D365's built-in Service Bus integration).
+ *Dapr* delivers the message to UTLXe.
+ *UTLXe* runs the `invoice-to-ubl` transformation --- converting D365 JSON to UBL 2.1 XML with Peppol BIS 3.0 compliance.
+ *Dapr* publishes the UBL XML to an output Service Bus topic.
+ A *Peppol Access Point* subscribes to the topic and delivers the invoice via AS4.

The transformation, Dapr components, and Service Bus resources are configured following the same steps shown in this chapter. The only difference is the `.utlx` content --- which maps D365 fields to UBL elements.

== Bindings vs. Pub/Sub: Which Pattern to Use

Dapr offers two building blocks for messaging. Understanding the difference is essential for choosing the right pattern.

=== Bindings (Queues --- Point-to-Point)

```
Producer → Queue → ONE Consumer
```

- One Dapr component per queue. Static --- you create the component YAML, Dapr connects.
- The message is consumed exactly once, then removed from the queue.
- Use case: "Process this order" --- exactly one handler.
- Dapr component type: `bindings.azure.servicebusqueues`
- UTLXe receives messages via `POST /{binding-name}` on port 8085.

=== Pub/Sub (Topics --- Fan-out)

```
Producer → Topic → Subscription A → Consumer A
                 → Subscription B → Consumer B
```

- *One* Dapr component per Service Bus namespace (not per topic). Dynamic --- UTLXe controls which topics it subscribes to.
- Each subscription gets its own copy of every message.
- Use case: "Notify everyone about this order" --- fan-out to multiple systems.
- Dapr component type: `pubsub.azure.servicebus.topics`
- UTLXe receives messages via `POST /pubsub/{transformation-name}` as CloudEvents.

=== When to Use Which

#table(
  columns: (auto, 1fr, 1fr),
  [*Criterion*], [*Binding (Queue)*], [*Pub/Sub (Topic)*],
  [Consumers], [One], [Many (fan-out)],
  [Dapr components], [One per queue], [One per namespace],
  [Dynamic subscriptions], [No --- YAML per queue], [Yes --- UTLXe declares via `/dapr/subscribe`],
  [Message format], [Raw payload], [CloudEvents envelope (UTLXe unwraps automatically)],
  [Recommended for], [Simple queue-to-queue], [Multi-subscriber, flexible routing],
)

Both patterns can be mixed: input from a queue, output to a topic (or vice versa).

=== Configuring Pub/Sub via the Admin API

Instead of creating Dapr component YAML manually, configure messaging through the Admin API:

```bash
# Upload transformation
curl -X POST -H "X-Admin-Key: $ADMIN_KEY" \
  -d @invoice-normalize.utlx \
  http://<admin>:8081/admin/transformations/invoice-normalize

# Set messaging: topic input, topic output
curl -X POST -H "X-Admin-Key: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "input": {"topic": "raw-invoices", "subscription": "utlxe"},
    "output": {"topic": "normalized-invoices"}
  }' \
  http://<admin>:8081/admin/transformations/invoice-normalize/messaging

# Test via HTTP first (no Dapr needed)
curl -X POST -H "X-Admin-Key: $ADMIN_KEY" \
  -d '{"invoiceId": "INV-001"}' \
  http://<admin>:8081/admin/transformations/invoice-normalize/test

# When ready, sync to Dapr (creates component YAML, activates subscription)
curl -X POST -H "X-Admin-Key: $ADMIN_KEY" \
  http://<admin>:8081/admin/transformations/invoice-normalize/sync
```

The messaging field name is the discriminator:

#table(
  columns: (auto, auto, auto),
  [*Field*], [*Azure Service*], [*Dapr Component*],
  [`queue: "name"`], [Service Bus Queue], [`bindings.azure.servicebusqueues`],
  [`topic: "name"`], [Service Bus Topic], [`pubsub.azure.servicebus.topics`],
  [`eventhub: "name"`], [Event Hub], [`bindings.azure.eventhubs`],
)

Input and output can use different services: `queue` in → `topic` out, `eventhub` in → `queue` out, etc.

=== Authentication: Managed Identity (Recommended)

For production, use Azure Managed Identity instead of connection strings. No secrets to manage --- the container's identity authenticates directly with Service Bus or Event Hub.

#table(
  columns: (auto, 1fr),
  [*Auth method*], [*How*],
  [Managed Identity (secretless)], [Set `namespaceName` in Dapr component. No `connectionString` needed. Assign `Azure Service Bus Data Sender` + `Azure Service Bus Data Receiver` RBAC roles to the Container App identity.],
  [Connection string], [Store as Container App secret. Reference via `secretRef` in Dapr component YAML.],
)

The bundle (`.utlar`) never contains credentials. It declares _what_ to connect to (queue/topic names). The _how_ (auth) comes from the environment.

== Summary: What You Configure Where

#table(
  columns: (auto, auto, 1fr),
  [*What*], [*Where*], [*How*],
  [Service Bus / Event Hub], [Azure Portal or CLI], [`az servicebus` / `az eventhubs` commands],
  [Connection string or MI], [Container App Environment], [Secret or Managed Identity RBAC],
  [Dapr binding (queue)], [Container App Environment], [Dapr component YAML --- or auto-generated by UTLXe via sync],
  [Dapr pub/sub (topic)], [Container App Environment], [Dapr component YAML --- or auto-generated by UTLXe via sync],
  [Transformation], [UTLXe Admin API or `.utlar`], [`POST /admin/transformations/{name}` or CI/CD bundle],
  [Messaging config], [UTLXe Admin API or `transform.yaml`], [`POST .../messaging` + `POST .../sync`],
)

The Azure resources and Dapr components are configured once. The transformations can be updated at any time without changing the infrastructure. In dev/test (open mode), the Admin API configures everything dynamically. In production (locked mode), the `.utlar` bundle contains all configuration and is deployed via CI/CD.
