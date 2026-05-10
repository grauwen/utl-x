= Quick Start

This chapter gets you from zero to a running transformation in under fifteen minutes. You will deploy UTLXe from the Azure Marketplace, upload a transformation, test it, and send your first real message.

== What You Get

When you deploy UTLXe from the Azure Marketplace, Azure creates a Container App running the UTLXe production engine. The container exposes two ports:

- *Port 8085* — the data plane. Your applications send messages here for transformation. This port is exposed via the Container App ingress.
- *Port 8081* — the admin and health port. You manage transformations here. This port stays internal to your VNet.

The container starts empty --- no transformations are loaded. You deploy them via the Admin API.

== Deploy from the Azure Marketplace

+ Open the Azure Portal and search for "UTLXe" in the Marketplace.
+ Select your plan:
  - *Starter* (1 vCPU, 4 GB) --- suitable for messages up to ~100 KB, development and light production workloads.
  - *Professional* (2 vCPU, 8 GB) --- suitable for messages up to ~500 KB, production workloads with higher throughput.
+ Configure the deployment:
  - Resource group and region.
  - Container App name.
  - Persistent storage toggle --- enable this if you want transformations to survive container restarts.
+ Click *Create*. The deployment takes approximately two minutes.

Once complete, note the admin key you entered during deployment --- you need it to log in to the Web UI.

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

```bash
curl -X POST \
  -H "X-Admin-Key: my-secret-key-here" \
  -H "Content-Type: text/plain" \
  --data-binary @hello.utlx \
  http://<internal-ip>:8081/admin/transformations/hello
```

UTLXe compiles the rule and confirms: `"status": "deployed"`. The transformation is now installed under the name `hello`.

== Run It

Now send data through the transformation. You provide the input JSON, UTLXe applies the rule, and returns the output:

```bash
curl -X POST \
  -H "X-Admin-Key: my-secret-key-here" \
  -H "Content-Type: application/json" \
  -d '{"name": "interested reader"}' \
  http://<internal-ip>:8081/admin/transformations/hello/test
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

== Send a Real Message

Now send a message through the data plane (port 8085) --- the same port that Dapr and client applications use:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"name": "Azure"}' \
  http://<ingress-url>/api/execute/hello
```

```json
{
  "success": true,
  "output": "{\"greeting\":\"Hello, Azure!\",\"timestamp\":\"2026-05-05T14:30:05Z\"}",
  "durationUs": 1850
}
```

That is it. The transformation was compiled once at upload time. Every subsequent message executes the compiled version --- typically one to five milliseconds per message.

== What Just Happened

The following sequence describes the complete flow:

+ You uploaded `hello.utlx` to the Admin API on port 8081.
+ UTLXe parsed and compiled the transformation into an optimized in-memory representation.
+ The compiled transformation was registered in the engine and written to `/utlxe/data/transformations/hello/`.
+ The health endpoint switched to `ready: true`.
+ Your client sent a JSON message to the data plane on port 8085.
+ UTLXe looked up the compiled `hello` transformation, executed it, and returned the result.

The Admin API and the data plane run simultaneously --- there is no "deployment mode" or downtime window.

== Next Steps

- *Chapter 3* explains how to write more complex transformations --- conditionals, array operations, multi-format output.
- *Chapter 4* covers the full Admin API --- bundles, schemas, testing, and operational management.
- *Chapter 5* shows how to connect UTLXe to Azure Service Bus and Event Hub via Dapr.
- *Chapter 6* dives into the architecture --- how Dapr, Service Bus, and UTLXe work together under the hood.
