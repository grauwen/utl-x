= Monitoring and Observability

Production deployments need visibility into what UTLXe is doing. This chapter covers health probes, Prometheus metrics, and how to set up monitoring dashboards.

== Health Endpoint

The health endpoint runs on port 8081 and serves two purposes: Kubernetes probes and operational status checks.

```bash
curl http://<internal-ip>:8081/health
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
curl http://<internal-ip>:8081/metrics
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

== Setting Up Azure Monitor

Azure Container Apps integrates with Azure Monitor out of the box. Container logs are available in the Log Analytics workspace associated with the Container App Environment.

To view container logs:

```bash
az containerapp logs show \
  -n utlxe -g myResourceGroup \
  --follow
```

UTLXe logs in structured format: timestamp, level, transformation name, and message. Filter by transformation name to isolate issues.

For Prometheus metrics, configure Azure Monitor managed Prometheus or deploy a Prometheus instance that scrapes port 8081.

== Grafana Dashboard

A recommended Grafana dashboard layout for UTLXe:

*Row 1 --- Throughput:*
- Messages per second (rate of `utlxe_messages_total`)
- Errors per second (rate of `utlxe_errors_total`)
- Error rate percentage

*Row 2 --- Latency:*
- p50 latency (median transformation time)
- p95 latency (tail latency)
- p99 latency (worst case)

*Row 3 --- Resources:*
- Heap usage (percentage of max)
- Active threads
- Container CPU and memory from Azure Monitor

=== Alert Rules

Configure alerts for:

#table(
  columns: (auto, auto, 1fr),
  [*Metric*], [*Threshold*], [*Action*],
  [Error rate], [> 1% for 5 minutes], [Notify on-call],
  [p99 latency], [> 500ms for 5 minutes], [Investigate --- possible GC pressure or large messages],
  [Heap usage], [> 80% of max], [Warning --- consider upgrading plan],
  [Transformations], [== 0 for 2 minutes], [Container restarted without bundle --- check persistence],
)

== Error Diagnosis

Prometheus gives you counters, but not the actual error messages. For quick diagnosis, use the error ring buffer:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/errors?limit=10
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
