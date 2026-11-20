= Quick Start

This chapter gets you from zero to a running transformation in under fifteen minutes. You will deploy UTLXe from the Azure Marketplace, upload a transformation, test it, and send your first real message.

== What You Get

When you deploy UTLXe from the Azure Marketplace, Azure creates a Container App running two containers side by side:

- *`utlxe`* --- the transformation engine. It listens on port 8085 for messages (the data plane) and on port 8081 for administration, health and metrics.
- *`utlxe-ui`* --- an nginx container serving the Admin Web UI on port 8088.

Container Apps ingress publishes exactly one port, and that port is *8088* --- nginx, in the `utlxe-ui` container. Nothing else is reachable from outside the replica. Everything the deployment offers therefore arrives through that one port, and what you can reach is exactly what nginx routes:

#table(
  columns: (auto, auto, 1fr),
  [*Path*], [*Credential*], [*Goes to*],
  [`/`], [none], [the Admin Web UI's static files],
  [`/admin/...`], [`X-Admin-Key`], [the engine's admin port, 8081 --- deploy, delete, list, bundles, config, logs],
  [`/health`, `/metrics`], [none], [the engine's admin port, 8081],
  [`/api/transformations/{id}/run`], [`X-Api-Key`], [the engine's *data plane*, 8085 --- send a document, get the result],
)

Three consequences follow, and the first two are easy to assume the other way round.

*The Admin API is reachable from the public internet.* It is proxied through the same public ingress as the Web UI and is protected only by the admin key you choose at deployment. Treat that key as a production credential, not a convenience.

*The data plane is published through exactly one path.* Since 1.4.4 an application can POST a document to `/api/transformations/{id}/run` and read the transformed result from the response. That single path is all of port 8085 that nginx routes --- the engine's other data-plane endpoints, including the one that *unloads* a transformation and the one that lists them, stay unreachable from outside the replica. See *Posting your first message over HTTP* below, and Chapter 11 for why the key is a different one.

*gRPC is not offered.* The engine can serve it, on port 9090, but a Container App publishes one port with one protocol and that port is carrying HTTP for the UI. Offering gRPC as well would need a second container app --- see Chapter 10.

The container starts empty --- no transformations are loaded. Whether they survive a restart depends on the *Persistent storage* option you choose at deployment. With it enabled, an Azure Files share is mounted at `/utlxe/data` and uploads persist across restarts; without it the engine holds transformations in memory only and you re-upload after every restart. A `.utlar` bundle placed on that share starts the engine in *locked mode* instead --- see Chapter 6.

== Before You Start

Two subscription-level prerequisites are easy to miss, and both produce errors that look like
a problem with UTLXe rather than with the subscription.

*Register the resource providers.* A subscription that has never run Azure Container Apps may
not have them enabled:

```bash
az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait
```

Azure often registers these automatically on first use, but when it does not, the deployment
fails with `MissingSubscriptionRegistration`.

*Check your Container Apps environment quota.* UTLXe creates its own Container Apps
environment. Azure limits how many you may have per region, and the limit depends on your
subscription type --- some carry a limit of one:

```bash
az rest --method get \
  --url "https://management.azure.com/subscriptions/SUBSCRIPTION_ID/providers/Microsoft.App/locations/westeurope/usages?api-version=2024-03-01" \
  --query "value[?name.value=='ManagedEnvironmentCount'].{used:currentValue,limit:limit}" \
  -o table
```

If `used` already equals `limit`, deploy to a different region --- the quota is per region ---
or request an increase through Azure support before you start. The failure otherwise appears
only at deployment time, as `MaxNumberOfRegionalEnvironmentsInSubExceeded`.

== Deploy from the Azure Marketplace

+ Open the Azure Portal and search for "UTLXe" in the Marketplace.
+ Select your plan. The plans differ only in how much load they carry:
  - *Starter* --- defaults to 1 vCPU and 2 GB, selectable up to 2 vCPU and 4 GB. Development and light production workloads.
  - *Professional* --- defaults to 2 vCPU and 4 GB, selectable up to 4 vCPU and 8 GB. Production workloads with higher throughput.
+ Configure the deployment:
  - Resource group and region.
  - Name prefix for the created resources.
  - *Data path API key* --- a *second* key, for the HTTP data path. An application holding it can run your transformations and nothing else: it cannot deploy, delete, or list them. Use a different value from the admin key, so whichever one leaks the other still holds.
  - *Admin key* --- the wizard generates a strong UUID for you. Copy it and save it in a password manager. You can also enter your own key (minimum 12 characters), but a UUID is recommended. The Admin API is reachable over the public internet, so this key is the only thing protecting it.
  - CPU and memory per instance, and the minimum instance count.
  - *Persistent storage* --- enabled by default. Creates a storage account and a file share in the same resource group, mounted at `/utlxe/data`, so uploaded transformations survive container restarts.
  - *Message source* --- *HTTP* or *Azure Service Bus*. With HTTP, applications POST a document to the published data path and get the transformed result back in the response; see below. With Service Bus, the wizard asks for a connection string, an input queue and an output topic, and switches auto-scaling to queue depth --- see Chapter 5. The two are not exclusive: a Service Bus deployment keeps the HTTP data path.
+ Click *Create*. Expect *15 minutes to an hour*, almost all of it Azure provisioning the Container Apps environment rather than starting UTLXe itself. Observed times vary widely for the same template in the same region.

When the deployment finishes, its *Outputs* tab gives you the addresses you need:

#table(
  columns: (auto, 1fr),
  [`utlxeUrl` / `adminUiUrl`], [The Web UI. Open it in a browser and log in with the admin key.],
  [`healthUrl`], [Health and readiness, unauthenticated.],
  [`dataPlaneUrl`], [The HTTP data path. Append `{transformationId}/run` and POST your document to it.],
  [`metricsNote`], [Prometheus metrics, unauthenticated.],
  [`containerAppName`], [The Container App name, for `az containerapp` commands.],
)

Once complete, you need the admin key to log in to the Web UI. If you used the generated UUID, make sure you saved it --- it cannot be retrieved after deployment. The same is true of the data path key.

=== Posting your first message over HTTP

Upload a transformation through the Web UI, then send it a document. The body is the document itself --- there is no envelope to build, and `Content-Type` tells the engine what format it is in:

```bash
curl -X POST "https://<dataPlaneUrl>/my-map/run" \
  -H "X-Api-Key: <your data path key>" \
  -H "Content-Type: application/xml" \
  --data-binary @order.xml
```

The response is the transformed output in the transformation's own output format --- JSON if the script says `output json`, CSV if it says `output csv` --- not a wrapper object with the result nested inside it. A transformation that fails answers `422`; an unknown id answers `404`.

Two things this path deliberately will not do. It will not accept a transformation script: the caller sends *data*, never code. And it will not accept the admin key --- that is the point of there being two.

#block(fill: luma(240), inset: 8pt, radius: 4pt, width: 100%)[
  *What the plans actually differ in.* vCPU and memory, and nothing else.

  Both plans run *one* UTLXe instance. Neither plan gives you more instances, and the plan you pick
  does not change how many messages that instance works on at once: on the Service Bus path that is
  the Dapr input component's `maxConcurrentHandlers` --- how many messages are in flight *inside the
  single engine process*, handled by its own threads --- and the templates set it to `8` in *both*
  plans. On the HTTP path it is bounded by the container's CPU allocation.

  What Professional buys is a bigger machine for the same eight messages: twice the CPU, so each one
  is transformed faster, and twice the JVM heap, so each one may be larger. The memory dropdown in
  the wizard says so directly --- 2 GB for messages up to 200 KB, 8 GB for 1 MB and above. It is a
  difference in message *size* and *speed*, not in message *count*.

  Earlier editions of this chapter described the plans as "8 worker threads" and "32 worker
  threads". Those figures come from the `workers` parameter, which the offering passes to an engine
  running in HTTP mode, where it is not read at all. Chapter 6 (Architecture) sets out the three
  concurrency knobs and which one applies where.
]

== Running Azure CLI Commands

Several steps in this book use `az` commands (the Azure CLI). There are three ways to run them:

*Option 1 --- Azure Cloud Shell (easiest, no install):*

Open #link("https://shell.azure.com")[shell.azure.com] in your browser. Azure Cloud Shell runs in the Azure Portal --- it has `az` pre-installed, already authenticated with your account. Choose *Bash* when prompted. Paste commands directly.

You can also open it from the Azure Portal: click the `>_` icon in the top menu bar.

*Option 2 --- Azure CLI on your laptop:*

Install from #link("https://docs.microsoft.com/en-us/cli/azure/install-azure-cli")[docs.microsoft.com/cli/azure]. Then authenticate:

```bash
az login
az account set --subscription <your-subscription-id>
```

*Option 3 --- Azure Portal UI:*

Most operations have a Portal equivalent. Where applicable, this book notes the Portal path alongside the CLI command.

== Open the Web UI

After deployment, find your UTLXe URL:

```bash
az containerapp show \
  --name utlxe --resource-group myResourceGroup \
  --query "properties.configuration.ingress.fqdn" -o tsv
```

Open `https://<the-fqdn>` in your browser. Enter the admin key you set during deployment. The dashboard loads.

If you can't find the URL, open the Azure Portal > your resource group > the Container App > *Overview* > look for the *Application URL*.

== Verify the Deployment

If the Web UI dashboard loaded in the previous step, the deployment is working. The dashboard shows `Transformations: 0` and `ready: false` --- the engine is alive but has no transformations yet.

You can also verify from Azure Cloud Shell (or any terminal with `az` installed):

```bash
# Check the health endpoint (use the FQDN from the previous step)
curl -s https://<your-fqdn>/health
```

Or via the Azure Portal: open the Container App > *Log stream* to see UTLXe startup logs in real time.

== Write Your First Transformation

Create a file called `hello.utlx`:

```
%utlx 1.0
input json
output json
---
{
  greeting: concat("Hello, ", $input.name, "!"),
  timestamp: now()
}
```

This transformation reads a JSON input and produces a JSON output. Here is what goes in and what comes out:

*Input:*
```json
{"name": "World"}
```

*Output:*
```json
{
  "greeting": "Hello, World!",
  "timestamp": "2026-05-05T14:30:00Z"
}
```

`$input.name` reads the `name` field from the input. `concat(...)` joins strings. `now()` adds the current timestamp. The header (`%utlx 1.0 / input json / output json`) tells the engine the formats. Everything after `---` is the transformation rule.

== Upload It

Upload the `.utlx` file to UTLXe. This does not transform any data yet --- it installs the rule so UTLXe knows how to transform messages later.

*Option A --- via the Web UI (easiest):*

Open the Web UI in your browser (section 2.3). Click *Upload* in the navigation. Paste the `hello.utlx` source into the text area, enter `hello` as the name, click *Upload*. UTLXe confirms: deployed.

*Option B --- via curl (from Azure Cloud Shell or your laptop):*

```bash
curl -X POST \
  -H "X-Admin-Key: <your-admin-key>" \
  -H "Content-Type: text/plain" \
  --data-binary @hello.utlx \
  https://<your-fqdn>/admin/transformations/hello
```

Replace `<your-fqdn>` with the URL from section 2.3. Replace `<your-admin-key>` with the key you set during deployment.

UTLXe compiles the rule and confirms: `"status": "deployed"`. The transformation is now installed under the name `hello`.

== Run It

Now send data through the transformation. You provide the input JSON, UTLXe applies the rule, and returns the output.

*Option A --- via the Web UI:*

Click `hello` in the dashboard. In the *Test* panel, paste `{"name": "interested reader"}` and click *Run Test*. The result appears below.

*Option B --- via curl (from Azure Cloud Shell or your laptop):*

```bash
curl -X POST \
  -H "X-Admin-Key: <your-admin-key>" \
  -H "Content-Type: application/json" \
  -d '{"name": "interested reader"}' \
  https://<your-fqdn>/admin/transformations/hello/test
```

Result:

```json
{
  "greeting": "Hello, interested reader!",
  "timestamp": "2026-05-05T14:30:00Z"
}
```

The input `"interested reader"` flowed through `$input.name` into `concat("Hello, ", "interested reader", "!")` and produced `"Hello, interested reader!"`. Change the name to anything --- the transformation applies the same rule to whatever you send.

Check the health endpoint --- the container is now ready to process traffic:

```json
{"status": "UP", "transformations": 1, "ready": true}
```

Now `ready: true` --- the container will accept traffic.

== What Just Happened

+ You deployed UTLXe from the Azure Marketplace --- Azure created the container, the Web UI, and the Dapr sidecar.
+ You opened the Web UI in your browser and logged in with the admin key.
+ You uploaded `hello.utlx` --- UTLXe compiled it into an optimized in-memory representation.
+ You tested it with sample input --- the transformation produced the expected output.

The transformation is now ready for real traffic. When you connect Azure Service Bus (Chapter 5), Dapr delivers messages to UTLXe automatically, and the same compiled transformation processes them at 500--4,000 messages per second.

== Next Steps

- *Chapter 3* explains how to write more complex transformations --- conditionals, array operations, multi-format output.
- *Chapter 4* covers the full Admin API --- bundles, schemas, testing, and operational management.
- *Chapter 5* shows how to connect UTLXe to Azure Service Bus and Event Hub via Dapr.
- *Chapter 6* dives into the architecture --- how Dapr, Service Bus, and UTLXe work together under the hood.
