# EF21: Per-Transformation Back-Pressure

**Status:** Proposed  
**Priority:** High (production fairness — one heavy transform should not starve others)  
**Created:** May 2026  
**Depends on:** EF15 (Heap Back-Pressure — already implemented)

---

## Problem

UTLXe's current back-pressure is global: when heap usage exceeds 92%, **all** requests are rejected with 503. This is unfair when multiple transformations share an engine instance:

```
Transformation A: 10MB XML invoices → complex restructure (heavy)
Transformation B: 200B JSON events → simple rename (lightweight)

A fills the heap → B gets 503 too → lightweight traffic unnecessarily blocked
```

In production (Azure Container Apps, Docker), a single UTLXe instance typically hosts 5-50 transformations. One misbehaving or heavy transformation should not block the rest.

## Current State (EF15)

| Check | Where | Scope | Effect |
|-------|-------|-------|--------|
| Heap > 92% | TransportHandlers (execute, batch, pipeline) | Global | Reject ALL with 503 |
| Heap > 92% | HttpTransport (Dapr binding, pub/sub) | Global | Reject ALL with 503 |
| `maxInputSize` | TransportHandlers | Per-transformation | Reject oversized payload |
| `paused` flag | TransportHandlers + HttpTransport | Per-transformation | Operator-initiated 503 |

Missing: **per-transformation concurrency control** that prevents one transform from consuming all resources.

## Proposed Solution: Three Levels

### Level 1: Per-Transformation Concurrency Limit (recommended first)

Add `maxConcurrent` to each transformation's configuration. Track in-flight executions with an `AtomicInteger` on `TransformationInstance`.

**Configuration:**
```yaml
# Via admin API upload config
maxConcurrent: 10   # max 10 simultaneous executions for this transformation
```

**Behavior:**
- When a transformation reaches its `maxConcurrent` limit, new requests for that transformation return 503 with `error_code: CONCURRENCY_LIMIT`
- Other transformations are unaffected
- Global heap check (EF15) remains as emergency brake
- Default: not set (unlimited) — operators opt in per transformation

**Implementation:**
- Add `inFlight: AtomicInteger` to `TransformationInstance`
- In `TransportHandlers.handleExecute()`: increment before execute, decrement in `finally`
- Check `if (instance.inFlight.get() >= instance.config.maxConcurrent) return 503`
- Same for batch (count all items) and pipeline (check each stage)
- ~20-30 lines of code in TransportHandlers

**Error response:**
```json
{
  "success": false,
  "error": "Transformation 'heavy-invoice' at concurrency limit (10/10)",
  "error_code": "CONCURRENCY_LIMIT",
  "error_class": "TRANSIENT"
}
```

**Effort:** Small (half day)

### Level 2: Adaptive Heap Fairness (future)

Track average memory cost per transformation using a rolling window of recent executions. When heap reaches a warning threshold, start throttling the heaviest transformations first while letting lightweight ones through.

| Heap usage | Behavior |
|-----------|----------|
| < 80% | All transformations accepted |
| 80-92% | Reject transformations with highest average memory cost first |
| > 92% | Reject everything (current EF15 behavior) |

**How it works:**
- After each execution, record `Runtime.freeMemory()` delta as approximate cost
- Maintain per-transformation rolling average (last 100 executions)
- At 80% heap, sort transformations by avg cost, reject top N until pressure drops

**Why 80% as the adaptive threshold (not 70%):**
- 70% is too aggressive — ZGC performs well up to 90%+, rejecting at 70% wastes 22% of available heap
- On a 3GB Starter tier, 70% means rejecting when 900MB is free — unnecessary
- 80% gives 12% headroom for adaptive throttling before the hard 92% cutoff kicks in
- The window between 80-92% is where selective rejection matters most

**Effort:** Medium (2-3 days). Requires memory profiling heuristics that may not be accurate on JVM (GC timing makes deltas unreliable).

### Level 3: Weighted Fair Queue (future, if needed)

Global bounded execution queue with per-transformation fairness slots. Like network QoS — each transformation gets a guaranteed minimum share of capacity.

**How it works:**
- Global queue with capacity = workers x 4
- Each transformation gets `floor(capacity / numTransformations)` guaranteed slots
- Remaining slots are shared first-come-first-served
- When queue is full, reject the transformation with the most in-flight requests

**Effort:** Large (3-5 days). Adds complexity to the execution path. Only needed if Level 1+2 prove insufficient.

## Hysteresis: High-Water / Low-Water Back-Pressure

A single threshold creates oscillation under sustained heavy load:

```
Without hysteresis (single threshold at 92%):
  heap: 91% → accept → 93% → REJECT → GC → 90% → accept → 93% → REJECT → ...
  Result: sawtooth pattern, Dapr retries add load during recovery, unstable
```

UTLXe uses a **two-threshold hysteresis model** (like a float valve):

```
With hysteresis (high=92%, low=80%):
  heap: 91% → accept → 93% → REJECT → GC → 88% → still rejecting → 82% → still rejecting → 79% → ACCEPT
  Result: clean transition, heap fully recovers before accepting again
```

| State | Condition | Action |
|-------|-----------|--------|
| Accepting | heap > 92% (high-water) | Switch to **Rejecting** |
| Rejecting | heap > 80% | **Keep rejecting** — heap hasn't recovered yet |
| Rejecting | heap < 80% (low-water) | Switch to **Accepting** |
| Accepting | heap < 92% | **Keep accepting** — normal operation |

**Why 80% as the low-water mark:**
- 12% gap between high (92%) and low (80%) gives substantial recovery headroom
- ZGC can reclaim 12% of a 3GB heap (~360MB) in under 100ms
- Dapr retry backoff (exponential) means messages queue up during rejection — accepting too early causes a retry flood that pushes heap right back up
- On a 6GB Professional tier, 80% means 1.2GB must be free before accepting — enough to absorb the queued retries

**Implementation:** Single `backpressureActive` boolean flag, checked in `isHeapPressure()`. Zero overhead — one volatile read + one comparison per request.

### Design note: Single transformation loaded

When only one transformation is loaded, hysteresis still applies — it protects against the Dapr retry storm. The Level 2 adaptive throttling (reject heaviest first) only makes sense with `registry.size() > 1`. With a single transformation, there's nothing to be selective about — the global threshold is the only defense.

## Heap Threshold Rationale

The global back-pressure threshold was raised from 85% to **92%** (May 2026). The reasoning:

### Why 92% is safe with ZGC

UTLXe runs ZGC (`-XX:+UseZGC -XX:+ZGenerational`) exclusively. ZGC is designed for:
- Sub-millisecond GC pauses regardless of heap size
- Concurrent collection that doesn't stop application threads
- Efficient operation at high heap utilization (90%+)

G1GC would struggle above 85% (mixed GC pauses grow, full GC risk increases). ZGC does not have this limitation — its pause times are O(1) with respect to heap size.

### Why not higher than 92%

- At 95%+, even ZGC can't keep up if allocation rate exceeds collection rate
- JVM internals need headroom: class metadata, thread stacks (~1MB each), Ktor/Netty buffers, compilation cache
- Azure Container Apps kills containers that exceed their cgroup memory limit — no graceful recovery
- 8% headroom on a 3GB heap = 240MB — enough for ~2400 concurrent 100KB payloads

### Concrete impact per tier

| Tier | Heap | Old reject (85%) | New reject (92%) | Gained |
|------|------|-------------------|-------------------|--------|
| Starter (4GB container) | 3 GB | at 2.55 GB used | at 2.76 GB used | +210 MB |
| Professional (8GB container) | 6 GB | at 5.10 GB used | at 5.52 GB used | +420 MB |

### Configurable at runtime

Operators can tune the threshold via the admin API without restart:

```bash
# View current threshold
curl -H "X-Admin-Key: $KEY" http://localhost:8081/admin/heap-threshold

# Adjust (e.g., lower for G1GC deployments)
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"threshold": 85}' \
  http://localhost:8081/admin/heap-threshold
```

## Recommendation

Implement **Level 1** first. Per-transformation `maxConcurrent` gives operators direct, predictable control. It solves the fairness problem for the common case (one heavy transform starving others) with minimal code and zero performance overhead for transforms without the limit set.

Level 2 adds automatic protection but relies on JVM memory heuristics that may be unreliable. Defer until Level 1 proves insufficient in production.

Level 3 is over-engineering for the current use case. If UTLXe ever needs to guarantee SLAs per transformation (multi-tenant), revisit.

## Interaction with Existing Features

| Feature | Interaction |
|---------|------------|
| EF15 Heap back-pressure | Global safety net — fires after per-transform limits |
| EF03 `maxInputSize` | Size check happens before concurrency increment |
| Pause/resume | Paused check happens before concurrency increment |
| Dapr bindings | Dapr retries 503 automatically — concurrency limit is transparent |
| Auto-scaling (Azure) | 503 responses trigger scale-out — correct behavior |

## Configuration Example

```bash
# Upload with concurrency limit
curl -X POST -H "X-Admin-Key: $KEY" \
  -d @heavy-invoice.utlx \
  "http://localhost:8081/admin/transformations/heavy-invoice?maxConcurrent=5"

# Upload lightweight transform — no limit needed
curl -X POST -H "X-Admin-Key: $KEY" \
  -d @simple-rename.utlx \
  "http://localhost:8081/admin/transformations/simple-rename"
```

## Monitoring

The `/admin/transformations` endpoint should expose:
- `inFlight`: current number of in-flight executions
- `maxConcurrent`: configured limit (null = unlimited)
- `concurrencyRejections`: count of 503s due to concurrency limit

This lets operators tune limits based on observed behavior.

---

*Feature EF21. May 2026. Proposed — Level 1 recommended for immediate implementation.*
