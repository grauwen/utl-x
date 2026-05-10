= Monitoring and Observability

Production deployments need visibility into what UTLXe is doing. This chapter covers health probes, Prometheus metrics, and how to set up monitoring dashboards.

== Health Endpoint

The health endpoint runs on port 8081 and serves two purposes: Kubernetes probes and operational status checks.

```bash
curl https://<your-fqdn>/health
```

```json
{
  "status": "UP",
  "transformations": 3,
  "ready": true
}
```

The fields:

#table(
  columns: (auto, 1fr),
  [`status`], [`"UP"` if the process is alive. Used by the liveness probe.],
  [`transformations`], [Number of compiled transformations in the registry.],
  [`ready`], [`true` when at least one transformation is loaded and compiled. Used by the readiness probe.],
)

=== Liveness vs. Readiness

#table(
  columns: (auto, auto, 1fr),
  [*Probe*], [*Checks*], [*Action if fails*],
  [Liveness], [`status == "UP"`], [Kubernetes restarts the container],
  [Readiness], [`ready == true`], [Kubernetes stops routing traffic to this instance],
)

A container that just started but has not received its bundle yet is *alive but not ready*. Traffic is held until transformations are uploaded and compiled. This prevents errors during the deployment window.

== Prometheus Metrics

UTLXe exposes Prometheus metrics on the same port:

```bash
curl https://<your-fqdn>/metrics
```

=== Per-Transformation Metrics

```
utlxe_messages_total{transform="invoice-to-ubl"} 12345
utlxe_transform_duration_seconds{transform="invoice-to-ubl",quantile="0.50"} 0.002
utlxe_transform_duration_seconds{transform="invoice-to-ubl",quantile="0.95"} 0.005
utlxe_transform_duration_seconds{transform="invoice-to-ubl",quantile="0.99"} 0.012
utlxe_errors_total{transform="invoice-to-ubl"} 3
```

=== Engine-Level Metrics

```
utlxe_active_threads 4
utlxe_heap_used_bytes 524288000
utlxe_uptime_seconds 86400
```

These metrics are standard Prometheus format and can be scraped by any Prometheus-compatible system.

== Three Monitoring Options

UTLXe's Prometheus endpoint (`GET /metrics`) works with any Prometheus-compatible system. On Azure, there are three practical options:

#table(
  columns: (auto, auto, 1fr),
  [*Option*], [*Effort*], [*Best for*],
  [Azure Monitor + Managed Grafana], [Low --- fully managed], [Azure-native teams, production],
  [Self-hosted Prometheus + Grafana], [Medium --- you manage the stack], [Multi-cloud, existing Prometheus],
  [Admin API only], [Zero --- built into UTLXe], [Quick checks, small deployments],
)

== Option 1: Azure Monitor Managed Prometheus + Managed Grafana

This is the recommended approach for Azure deployments. Fully managed, no infrastructure to maintain.

=== Step 1: Create Azure Monitor Workspace + Managed Grafana

```bash
RESOURCE_GROUP="rg-utlxe-monitoring"
LOCATION="westeurope"

# Create Azure Monitor workspace (stores Prometheus metrics)
az monitor account create \
  --name utlxe-prometheus \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION

# Create Managed Grafana instance
az grafana create \
  --name utlxe-grafana \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION

# Link Grafana to the Azure Monitor workspace
az grafana data-source create \
  --name utlxe-grafana \
  --resource-group $RESOURCE_GROUP \
  --definition '{
    "name": "Azure Managed Prometheus",
    "type": "prometheus",
    "url": "https://utlxe-prometheus-XXXX.westeurope.prometheus.monitor.azure.com"
  }'
```

_Portal alternative:_ Search "Azure Monitor workspace" > Create. Then search "Azure Managed Grafana" > Create. Link them under Grafana > Settings > Data sources.

=== Step 2: Enable Prometheus Scraping on Container Apps

Azure Container Apps can scrape Prometheus endpoints automatically when Azure Monitor is configured:

```bash
# Get the Container App Environment resource ID
ENV_ID=$(az containerapp env show \
  --name utlxe-env --resource-group rg-utlxe-prd \
  --query id -o tsv)

# Enable metrics collection
az monitor account update \
  --name utlxe-prometheus \
  --resource-group $RESOURCE_GROUP \
  --linked-environment "$ENV_ID"
```

Azure Monitor scrapes `GET /metrics` on port 8081 every 30 seconds. No sidecar or annotation needed --- Container Apps has built-in Prometheus scraping support.

=== Step 3: Import the UTLXe Grafana Dashboard

Open Managed Grafana (URL from `az grafana show`) and import the following dashboard.

==== Dashboard Layout

*Row 1 --- Message Throughput (the business view):*

#table(
  columns: (auto, auto, 1fr),
  [*Panel*], [*PromQL*], [*Description*],
  [Messages/sec], [`rate(utlxe_messages_total[5m])`], [Messages processed per second, per transformation],
  [Errors/sec], [`rate(utlxe_errors_total[5m])`], [Errors per second],
  [Error %], [`rate(utlxe_errors_total[5m]) / rate(utlxe_messages_total[5m]) * 100`], [Error rate as percentage],
  [Total messages (24h)], [`increase(utlxe_messages_total[24h])`], [Volume over last 24 hours],
)

*Row 2 --- Latency (the performance view):*

#table(
  columns: (auto, auto, 1fr),
  [*Panel*], [*PromQL*], [*Description*],
  [p50 latency], [`utlxe_transform_duration_seconds{quantile="0.50"}`], [Median --- what most messages experience],
  [p95 latency], [`utlxe_transform_duration_seconds{quantile="0.95"}`], [Tail --- the slow 5%],
  [p99 latency], [`utlxe_transform_duration_seconds{quantile="0.99"}`], [Worst case --- may trigger investigation],
)

*Row 3 --- Engine Health:*

#table(
  columns: (auto, auto, 1fr),
  [*Panel*], [*PromQL / Source*], [*Description*],
  [Transformations loaded], [`utlxe_transformations_loaded`], [Should be > 0 always],
  [Heap usage], [`utlxe_heap_usage_percent`], [JVM heap percentage (cached, updated every 100ms)],
  [Backpressure], [`utlxe_heap_pressure`], [1 = rejecting messages, 0 = normal],
  [Uptime], [`utlxe_uptime_seconds`], [Time since last restart --- drops indicate restarts],
  [Container CPU], [Azure Monitor (Container Insights)], [CPU usage from the platform],
  [Container Memory], [Azure Monitor (Container Insights)], [Memory from the platform],
)

*Row 4 --- Per-Transformation Breakdown:*

A table panel showing each transformation with:
- Name, status (ready/paused)
- Messages processed, errors, error rate
- Average latency

PromQL: `utlxe_messages_total` grouped by `transform` label.

=== Step 4: Configure Alert Rules

```bash
# Error rate alert
az monitor metrics alert create \
  --name utlxe-error-rate \
  --resource-group $RESOURCE_GROUP \
  --condition "avg rate(utlxe_errors_total[5m]) > 0.01" \
  --description "UTLXe error rate exceeds 1%" \
  --severity 2 \
  --action-group oncall-team

# Zero transformations alert (restart without bundle)
az monitor metrics alert create \
  --name utlxe-no-transforms \
  --resource-group $RESOURCE_GROUP \
  --condition "utlxe_transformations_loaded == 0" \
  --description "UTLXe has no transformations loaded" \
  --severity 1 \
  --action-group oncall-team
```

Recommended alert rules:

#table(
  columns: (auto, auto, auto, 1fr),
  [*Alert*], [*Condition*], [*Severity*], [*Action*],
  [Error rate], [> 1% for 5 min], [Warning], [Notify on-call --- check error ring buffer],
  [p99 latency], [> 500ms for 5 min], [Warning], [Investigate --- GC pressure or large messages],
  [Heap usage], [> 80%], [Warning], [Consider scaling up or increasing heap],
  [Backpressure active], [`utlxe_heap_pressure == 1`], [Critical], [UTLXe is rejecting messages --- messages queue in Service Bus. Lower threshold or scale up.],
  [Zero transforms], [== 0 for 2 min], [Critical], [Bundle not loaded --- check persistence/CI/CD],
  [Container restart], [uptime drops], [Info], [Expected during deployment, investigate if unexpected],
)

== Option 2: Self-Hosted Prometheus + Grafana

If you already have a Prometheus stack or need multi-cloud monitoring:

=== Deploy Prometheus in the Same VNet

```yaml
# prometheus.yml (scrape config)
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'utlxe'
    static_configs:
      - targets:
        - 'utlxe-prd.internal.<env-id>.westeurope.azurecontainerapps.io:8081'
        - 'utlxe-acc.internal.<env-id>.westeurope.azurecontainerapps.io:8081'
    metrics_path: '/metrics'
```

Deploy Prometheus as a Container App in the same environment, or use an external Prometheus that can reach the VNet.

=== Deploy Grafana

```bash
# Deploy Grafana as a Container App
az containerapp create \
  --name grafana \
  --resource-group rg-utlxe-monitoring \
  --environment utlxe-env \
  --image grafana/grafana:latest \
  --target-port 3000 \
  --ingress external \
  --env-vars GF_SECURITY_ADMIN_PASSWORD=secretref:grafana-password
```

Then add the Prometheus data source in Grafana and import the same dashboard panels described above.

== Option 3: Admin API Only (No External Tools)

For small deployments or quick checks, the Admin API provides everything without external infrastructure:

```bash
# Health check
curl https://<your-fqdn>/health

# Transformation metrics
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/transformations
# → messages_processed, errors per transformation

# Recent errors
curl -H "X-Admin-Key: $KEY" \
  "https://<your-fqdn>/admin/transformations/orders-in/errors?limit=10"

# Log entries
curl -H "X-Admin-Key: $KEY" \
  "https://<your-fqdn>/admin/logs?level=ERROR&limit=20"

# Engine info (mode, uptime, bundle version)
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/info
```

This is sufficient for a single UTLXe instance in development. For production with multiple instances, use Option 1 or 2 for aggregate views and alerting.

== Monitoring Multiple UTLXe Instances

When running multiple UTLXe instances (one per flow), each exposes its own `/metrics` endpoint. Prometheus scrapes all of them. Grafana shows a fleet view:

- *Dashboard variable:* add a `$instance` variable that selects the UTLXe instance from the `job` or `instance` label.
- *Aggregate panels:* `sum(rate(utlxe_messages_total[5m]))` shows total throughput across all instances.
- *Per-instance panels:* `rate(utlxe_messages_total{instance=~"$instance"}[5m])` filters to a single instance.

For centralized management of multiple instances, see the UTLXc control plane (Chapter 11 in the feature roadmap, EF11).

== Error Diagnosis

Prometheus gives you counters, but not the actual error messages. For quick diagnosis, use the error ring buffer:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/errors?limit=10
```

```json
{
  "errors": [
    {
      "timestamp": "2026-05-05T14:32:01Z",
      "message": "Null reference: $input.customer.address",
      "line": 14,
      "input_preview": "{\"orderId\":\"12345\",\"customer\":{\"name\":\"Acme\"}}"
    }
  ],
  "total_errors": 47,
  "showing": 10
}
```

The `input_preview` is truncated to 200 characters to avoid exposing full message payloads. This is usually enough to identify the pattern that causes failures.

The typical diagnosis workflow:

+ Notice elevated error rate in Grafana.
+ Check the error ring buffer for the affected transformation.
+ Identify the pattern (missing field, unexpected type, schema mismatch).
+ Pause the transformation if needed (Chapter 6).
+ Fix and re-upload.
+ Test with sample input.
+ Resume.

== Runtime Log Access

For deeper investigation, access log entries directly via the Admin API without opening the Azure portal:

```bash
# Last 50 log entries
curl -H "X-Admin-Key: $KEY" \
  "https://<your-fqdn>/admin/logs?limit=50"

# Only errors
curl -H "X-Admin-Key: $KEY" \
  "https://<your-fqdn>/admin/logs?level=ERROR"

# Search for a specific message
curl -H "X-Admin-Key: $KEY" \
  "https://<your-fqdn>/admin/logs?contains=orders-in"
```

For Dapr traffic tracing, switch to DEBUG level at runtime:

```bash
# Enable DEBUG with auto-revert after 30 minutes
curl -X POST -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"level": "DEBUG", "revert_after_minutes": 30}' \
  https://<your-fqdn>/admin/log/level
```

At DEBUG level, UTLXe logs the full request/response flow for every Dapr message: input headers, CloudEvents metadata, output URL, response timing, and Dapr response body on failure. This is the primary tool for diagnosing message routing issues.

The log buffer holds 5000 entries in memory. No disk I/O --- performance impact is negligible. The auto-revert ensures DEBUG does not stay on accidentally.

== Metrics Design: What UTLXe provides vs. what's external

UTLXe provides raw counters (messages processed, errors) per transformation. External tools handle everything else:

#table(
  columns: (auto, 1fr),
  [*UTLXe provides*], [*External (Prometheus / Azure Monitor)*],
  [Total message count per transformation], [Message rate (msg/sec) via `rate()`],
  [Total error count per transformation], [Error rate and error percentage],
  [Error details (last 100, ring buffer)], [Latency percentiles (p50, p95, p99)],
  [Prometheus-format counters on `/metrics`], [Dashboards, graphs, alerting],
  [Log buffer (last 5000, Admin API)], [Long-term log storage (Azure Monitor)],
)

Resettable counters were considered and not implemented. Prometheus computes rates from monotonic counters automatically --- a `rate()` query over a time window is more meaningful than a counter that was "reset 3 hours ago."

== Distributed Tracing with Azure Monitor

UTLXe includes the Azure Monitor OpenTelemetry agent. When enabled, every message is traced end-to-end across the full Azure messaging chain:

```
Service Bus → Dapr → UTLXe transform (2ms) → Dapr → Service Bus
```

Every hop is a span in Azure Monitor. Click any span to see the transformation details and related log entries.

=== Enabling

Set the Application Insights connection string as an environment variable:

```bash
az containerapp update --name utlxe -g <rg> \
  --set-env-vars \
    APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=..."
```

When not set, the agent is not loaded --- zero overhead.

=== What you see in Azure Monitor

Open *Application Insights* → *Transaction search* → click a trace:

#table(
  columns: (auto, auto, 1fr),
  [*Span*], [*Duration*], [*Created by*],
  [Service Bus delivery], [—], [Azure / Dapr],
  [HTTP POST /orders-in], [2.1ms], [Agent (auto)],
  [HTTP POST localhost:3500 /v1.0/bindings/orders-out], [5ms], [Agent (auto)],
  [Service Bus send], [—], [Azure / Dapr],
)

The UTLXe span includes custom attributes:

#table(
  columns: (auto, 1fr),
  [*Attribute*], [*Example*],
  [`utlxe.transformation`], [`orders-in`],
  [`utlxe.strategy`], [`COMPILED`],
  [`utlxe.input.size`], [`1234`],
  [`utlxe.output.size`], [`567`],
  [`utlxe.message_id`], [`019e-...`],
  [`utlxe.correlation_id`], [`abc-...`],
  [`utlxe.duration_us`], [`2100`],
)

=== Log-trace correlation

Every log entry is automatically tagged with the trace ID and span ID. In Azure Monitor, click a span → *Logs* tab → see exactly what happened during that transformation. Click a log entry → see the full distributed trace it belongs to.

=== Memory impact

The agent adds ~30--50 MB of heap (~1--2% of Starter plan). Spans are exported asynchronously --- no impact on transformation throughput.

=== Application Map

Azure Monitor's Application Map shows the topology automatically: Service Bus → UTLXe → Service Bus, with average latency and error rates per connection. No configuration needed --- the agent discovers the topology from the traced HTTP calls.
