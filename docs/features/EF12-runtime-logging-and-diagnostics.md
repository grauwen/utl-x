# EF12: Runtime Logging and Diagnostics

**Status:** Implemented  
**Priority:** High (production debugging without restarts or Azure portal)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API)

---

## Summary

Production debugging requires two capabilities that UTLXe previously lacked:

1. **Runtime log level change** — switch to DEBUG without restarting the container
2. **Log access via Admin API** — view recent logs without Azure portal or `kubectl logs`

EF12 adds both, plus enhanced DEBUG-level logging for Dapr traffic tracing. All logging infrastructure is zero-I/O (in-memory ring buffer) and O(1) per log event.

## Admin API Endpoints

| Method | Path | Description | Locked mode? |
|--------|------|-------------|:---:|
| `GET` | `/admin/log/level` | Current root log level | Allowed |
| `POST` | `/admin/log/level` | Change log level (with optional auto-revert) | Allowed |
| `GET` | `/admin/logs` | Recent log entries from memory buffer | Allowed |
| `DELETE` | `/admin/logs` | Clear the log buffer | Allowed |

All log endpoints are **allowed in locked mode** — they are operational/diagnostic, not configuration changes.

### Change log level

```bash
# Check current level
curl -H "X-Admin-Key: $KEY" http://admin:8081/admin/log/level
→ {"level": "INFO"}

# Switch to DEBUG
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"level": "DEBUG"}' \
  http://admin:8081/admin/log/level
→ {"previous_level": "INFO", "level": "DEBUG"}

# Switch to DEBUG with auto-revert (safety — reverts even if operator forgets)
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"level": "DEBUG", "revert_after_minutes": 30}' \
  http://admin:8081/admin/log/level
→ {"previous_level": "INFO", "level": "DEBUG", "revert_after_minutes": 30}
```

Valid levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.

Auto-revert is recommended for production: operator sets DEBUG, captures the issue, and even if they forget to reset, it auto-reverts to the previous level after N minutes.

### View logs

```bash
# Last 100 entries (default)
curl -H "X-Admin-Key: $KEY" http://admin:8081/admin/logs

# Last 50 ERROR entries
curl -H "X-Admin-Key: $KEY" "http://admin:8081/admin/logs?level=ERROR&limit=50"

# Search for a specific MessageId
curl -H "X-Admin-Key: $KEY" "http://admin:8081/admin/logs?contains=msg-abc-123"

# Entries since a timestamp
curl -H "X-Admin-Key: $KEY" "http://admin:8081/admin/logs?since=2026-05-08T10:00:00Z"

# Clear the buffer
curl -X DELETE -H "X-Admin-Key: $KEY" http://admin:8081/admin/logs
```

Response format:

```json
{
  "entries": [
    {
      "timestamp": "2026-05-08T10:15:30.123Z",
      "level": "INFO",
      "logger": "HttpTransport",
      "message": "[orders-in] MessageId=abc-123 CorrelationId=xyz binding=orders-in payload=1234B",
      "thread": "ktor-netty-worker-3"
    }
  ],
  "total_buffered": 2847,
  "showing": 100,
  "current_level": "INFO"
}
```

## In-Memory Ring Buffer

The log buffer is a custom Logback `Appender` that writes to a bounded `ConcurrentLinkedDeque`. No disk I/O. No blocking.

### Performance characteristics

| Operation | Complexity | Blocking? |
|---|---|---|
| Append (every log event) | O(1) | No — lock-free CAS |
| Size check (cap enforcement) | O(1) | No — atomic counter |
| Read (GET /admin/logs) | O(limit) | No — non-blocking iteration |
| Clear | O(n) | No |

**Memory footprint:** 5000 entries × ~200 bytes = ~1MB. Negligible.

**Implementation detail:** The buffer uses an `AtomicInteger` counter instead of `ConcurrentLinkedDeque.size()` (which is O(n)) to enforce the cap. Every append is: `addFirst()` → `AtomicInteger.incrementAndGet()` → conditional `pollLast()` + `decrementAndGet()`. Fully O(1), no traversal.

### Configuration

| Setting | Default | Description |
|---|---|---|
| Buffer max entries | 5000 | Configurable via `LogBuffer.maxEntries` |
| Auto-revert default | none | Set per `POST /admin/log/level` call |

## Enhanced DEBUG Logging for Dapr Traffic

When log level is set to DEBUG, UTLXe logs the full request/response flow for Dapr:

### Binding input (POST /{bindingName})

```
INFO:  [orders-in] MessageId=abc CorrelationId=xyz binding=orders-in payload=1234B
DEBUG: [orders-in] Dapr input headers: metadata.MessageId=abc, traceparent=00-..., Content-Type=application/json
DEBUG: [orders-in] CausationId=prev-msg contentType=application/json outputBinding=orders-out isPubSub=false
```

### Dapr output forwarding

```
DEBUG: [orders-in] Output → POST http://localhost:3500/v1.0/bindings/orders-out (567 bytes)
DEBUG: [orders-in] Output → binding 'orders-out' OK (5ms) OutputMessageId=def-456
```

### Output failure (with Dapr response body)

```
WARN:  Dapr output binding 'orders-out' returned 500 (12ms) body={"errorCode":"ERR_INVOKE_OUTPUT_BINDING","message":"..."}
```

### Pub/sub subscribe

```
INFO:  Dapr /dapr/subscribe — returning 3 subscription(s)
DEBUG: /dapr/subscribe: [{pubsubname=utlxe-servicebus, topic=incoming-orders, route=/pubsub/orders-in}, ...]
```

### Logging levels summary

| Level | What's logged |
|---|---|
| **ERROR** | Transformation failures, Dapr output failures, startup errors |
| **WARN** | Dapr output non-2xx responses (with response body), schema validation warnings |
| **INFO** | Message received/completed with MessageId, timing. Startup. Config changes. |
| **DEBUG** | Full headers, phase timing, output URLs, payload sizes, Dapr response details |
| **TRACE** | Reserved for future per-expression tracing in transformations |

## Info Endpoint

`GET /admin/info` now includes log state:

```json
{
  "version": "1.0.1",
  "mode": "locked",
  "log_level": "INFO",
  "log_buffer_size": 2847,
  ...
}
```

## Metrics and Counters: what UTLXe provides vs what's external

UTLXe provides raw counters. External monitoring infrastructure (Prometheus, Azure Monitor, Grafana) handles aggregation, rates, percentiles, and dashboards.

### What UTLXe provides (built-in)

| Metric | Location | Type | Reset? |
|---|---|---|---|
| `messages_processed` per transformation | `GET /admin/transformations` | Cumulative counter | No — resets on container restart |
| `errors` per transformation | `GET /admin/transformations` | Cumulative counter | No |
| Error details (last 100) | `GET /admin/transformations/{name}/errors` | Ring buffer | Auto-evicts oldest |
| Prometheus counters | `GET /metrics` | Prometheus text format | No — Prometheus scrapes and computes rates |
| Log entries (last 5000) | `GET /admin/logs` | Ring buffer | `DELETE /admin/logs` to clear |

### What external monitoring provides (not in UTLXe)

| Capability | Tool | Why not in UTLXe |
|---|---|---|
| Message rate (msg/sec, msg/min) | Prometheus `rate()` + Grafana | Rate calculation from counters is what Prometheus does best |
| Latency percentiles (p50, p95, p99) | Prometheus histograms + Grafana | Histograms require significant memory; Prometheus is purpose-built for this |
| Resettable counters | Not needed | Creates confusion ("when was it reset?"). Prometheus `rate()` over a time window is more meaningful |
| Cross-instance aggregation | Azure Monitor / UTLXc (EF11) | Single UTLXe has no view of other instances |
| Alerting (error rate > threshold) | Azure Monitor alerts / Grafana | UTLXe is a data plane, not an alerting system |
| Dashboards and graphs | Grafana / Azure Monitor workbooks | Visualization is not UTLXe's responsibility |
| Long-term metrics storage | Prometheus / Azure Monitor | UTLXe counters reset on restart; external storage is durable |

### Design decision: no resettable counters

Resettable counters were considered and rejected:
- A counter that was "reset 3 hours ago" showing 500 messages tells you less than a Prometheus `rate(messages_processed[5m])` query showing 2.3 msg/sec
- Reset timing is lost metadata — "who reset it and when?" is not tracked
- External tools compute rates from monotonic counters automatically — reset breaks this
- The error ring buffer (last 100 details) serves the "what happened recently?" use case better than a resettable count

### Prometheus endpoint

`GET /metrics` returns Prometheus text format, scraped automatically by Azure Monitor or a Prometheus instance:

```
# HELP utlxe_messages_processed_total Total messages processed per transformation
# TYPE utlxe_messages_processed_total counter
utlxe_messages_processed_total{transformation="orders-in"} 12345
utlxe_messages_processed_total{transformation="invoices-in"} 8901

# HELP utlxe_errors_total Total errors per transformation
# TYPE utlxe_errors_total counter
utlxe_errors_total{transformation="orders-in"} 2
utlxe_errors_total{transformation="invoices-in"} 0
```

Example Grafana/PromQL queries:
- Message rate: `rate(utlxe_messages_processed_total[5m])`
- Error rate: `rate(utlxe_errors_total[5m])`
- Error percentage: `rate(utlxe_errors_total[5m]) / rate(utlxe_messages_processed_total[5m]) * 100`

### For UTLXc (EF11, future)

The control plane aggregates counters across all UTLXe instances — providing the fleet-wide view that individual instances cannot. This is the right place for cross-instance rates, totals, and dashboards.

## Files Implemented

| File | Change |
|------|--------|
| New: `modules/engine/.../admin/LogBuffer.kt` | In-memory ring buffer, Logback appender, level change, auto-revert |
| `modules/engine/.../admin/AdminEndpoint.kt` | `GET/POST /admin/log/level`, `GET/DELETE /admin/logs` endpoints |
| `modules/engine/.../transport/HttpTransport.kt` | Enhanced DEBUG logging for Dapr input/output traffic |
| `modules/engine/.../Main.kt` | `LogBuffer.install()` at startup |

## Relationship to Other Features

- **EF03** (Admin API): log endpoints are part of the Admin API
- **EF09** (locked mode): log endpoints allowed in locked mode (operational, not config)
- **EF04** (tracing): DEBUG logging shows full messaging triad per request
- **EF11** (control plane): UTLXc could aggregate logs from all UTLXe instances

---

*Feature EF12. May 2026. Implemented.*
*Key insight: the ring buffer appender must be O(1) per log event — use AtomicInteger for size tracking, not ConcurrentLinkedDeque.size() which is O(n). Auto-revert on log level change prevents "forgot to reset DEBUG in production."*
