= Monitoring and Observability

_Health probes, Prometheus metrics, and Grafana dashboards for production UTLXe deployments._

== Health Endpoint

// GET /health on port 8081
// {"status":"UP", "transformations":3, "ready":true}
// Liveness: status == "UP" (process is alive)
// Readiness: ready == true (transformations compiled, traffic can flow)

== Kubernetes Probes

// Liveness probe: restart if dead
// Readiness probe: route traffic only when ready
// Container App probe configuration (Bicep)

== Prometheus Metrics

// GET /metrics on port 8081
// utlxe_messages_total{transform="invoice-to-ubl"}
// utlxe_transform_duration_seconds{transform="...", quantile="0.99"}
// utlxe_errors_total{transform="..."}
// utlxe_active_threads
// utlxe_heap_used_bytes

== Setting Up Azure Monitor

// Container App → Azure Monitor integration
// Log Analytics workspace for container logs
// Metrics explorer for Prometheus metrics

== Grafana Dashboard

// Example dashboard JSON
// Panels: messages/sec, latency p50/p95/p99, error rate, heap usage
// Alert rules: error rate > 1%, p99 > 500ms, heap > 80%

== Container Logs

// az containerapp logs show
// UTLXe log format: timestamp, level, transformation name, message
// Filtering by transformation name

== Error Diagnosis

// GET /admin/transformations/{name}/errors
// Ring buffer of last 100 errors with input preview
// Correlating errors with Prometheus counters
