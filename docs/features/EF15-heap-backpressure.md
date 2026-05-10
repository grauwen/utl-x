# EF15: Heap Backpressure

**Status:** Implemented  
**Priority:** High (prevents OOM crashes in production)  
**Created:** May 2026  
**Depends on:** EF05 (Dapr integration)

---

## Summary

When heap usage exceeds a configurable threshold, UTLXe rejects incoming Dapr messages with 503. Messages stay in Service Bus and are retried when pressure drops. This prevents OOM crashes without operator intervention.

## The Problem

Without backpressure, a burst of large messages fills the heap until the JVM exceeds the container memory limit. Kubernetes kills the process (exit code 137). All in-flight messages are lost. The container restarts and the cycle repeats.

## The Solution

A background thread monitors heap usage every 100ms and caches the percentage. The hot path (message processing) reads a single `volatile double` and compares against the threshold — one CPU instruction, zero computation.

### Architecture

```
Background thread (daemon, independent):
  loop:
    sleep 100ms
    heapUsage = (totalMemory - freeMemory) / maxMemory    ~20 nanoseconds
    repeat

Message thread (per request):
  if (heapUsage > heapBackpressureThreshold) → 503        ~1 nanosecond
  else → process normally
```

Two completely separate threads. The message thread never triggers, wakes, or interacts with the background thread. It reads one cached number and does one comparison.

### Why a cached number, not a boolean

The background thread stores the **heap usage percentage** (0.0-1.0), not a boolean. The comparison against the threshold happens at read time. This means:

- Changing the threshold takes effect **immediately** on the next message (not after the next 100ms check)
- The Admin API reads the cached number directly for display — no separate calculation
- The same number feeds Prometheus metrics

### Performance impact

| Operation | Cost |
|---|---|
| Per message (read volatile + compare) | ~1 nanosecond |
| Background thread per check | ~20 nanoseconds |
| Background thread wake interval | 100ms |
| At 4,000 msg/sec total overhead | ~4 microseconds/sec |

**Zero measurable impact on throughput.**

## Configuration

| Setting | Default | Range | Runtime-changeable |
|---|---|---|---|
| `heapBackpressureThreshold` | 85% | 50-99% | Yes — `POST /admin/backpressure` |

```bash
# View current status
curl -H "X-Admin-Key: $KEY" https://<fqdn>/admin/backpressure

# Change threshold
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"threshold": 90}' \
  https://<fqdn>/admin/backpressure
```

The Web UI Config page shows a visual heap bar with a threshold dropdown.

## Behavior

| Heap usage | Binding input (Dapr) | Pub/sub input (Dapr) | Direct HTTP | Admin API |
|---|---|---|---|---|
| Below threshold | Process normally | Process normally | Process normally | Always available |
| Above threshold | **503** → Dapr retries | **503** → Dapr retries | Process normally | Always available |

Direct HTTP is not rejected — the client controls its own retry logic. Admin API is never rejected — operators must always be able to manage the engine.

Dapr receives the 503, does not acknowledge the message, and Service Bus retries delivery later. Combined with the Dapr Resiliency circuit breaker (EF09), this prevents dead-lettering during memory pressure.

## Prometheus Metrics

```
# HELP utlxe_heap_usage_percent Heap usage as percentage (0-100)
# TYPE utlxe_heap_usage_percent gauge
utlxe_heap_usage_percent 42

# HELP utlxe_heap_backpressure_threshold Heap backpressure threshold (0-100)
# TYPE utlxe_heap_backpressure_threshold gauge
utlxe_heap_backpressure_threshold 85

# HELP utlxe_heap_pressure Heap backpressure active (1=rejecting, 0=normal)
# TYPE utlxe_heap_pressure gauge
utlxe_heap_pressure 0
```

Grafana alert: `utlxe_heap_pressure == 1` → "UTLXe is rejecting messages due to heap pressure."

## Files Implemented

| File | Change |
|---|---|
| `UtlxEngine.kt` | `heapUsage` (volatile double), `heapBackpressureThreshold` (volatile double), background monitor thread, `isHeapPressure()`, `heapUsagePercent()` |
| `HttpTransport.kt` | 503 rejection before `handleDaprInput()` and `handlePubSubInput()` when `isHeapPressure()` |
| `AdminEndpoint.kt` | `GET /admin/backpressure` (status + heap stats), `POST /admin/backpressure` (set threshold 50-99%), heap stats in `GET /admin/info` |
| `MetricsCollector.kt` | Three Prometheus gauges: `heap_usage_percent`, `heap_backpressure_threshold`, `heap_pressure` |
| `AdminEndpointTest.kt` | 4 tests: get status, set threshold, invalid threshold rejected, info includes heap |
| `app.js` | Config page: heap usage bar (color-coded), threshold dropdown, pressure status, Apply/Refresh |

## Relationship to Other Features

- **EF05** (Dapr integration): 503 response triggers Dapr retry behavior
- **EF09** (production mode): Dapr Resiliency circuit breaker prevents dead-lettering during sustained pressure
- **EF12** (logging): heap pressure events logged at WARN level
- **EF03** (Admin API): backpressure endpoint is part of the Admin API, allowed in locked mode

---

*Feature EF15. May 2026. Implemented.*
*Key insight: the background thread stores a number, not a boolean. The threshold comparison happens at read time — changing the threshold takes effect immediately. The hot path is one volatile read and one comparison: ~1 nanosecond per message.*
