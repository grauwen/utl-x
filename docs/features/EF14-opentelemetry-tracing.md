# EF14: OpenTelemetry Distributed Tracing

**Status:** Implemented  
**Priority:** Medium (production observability for enterprise customers)  
**Created:** May 2026  
**Depends on:** EF04 (message correlation), EF05 (Dapr integration)

---

## Summary

UTLXe uses the **Azure Monitor OpenTelemetry Java agent** for distributed tracing. The agent runs inside the same JVM — no extra container, no extra pod, no extra infrastructure. It auto-instruments all HTTP traffic, Dapr calls, and log entries. UTLXe adds custom attributes (transformation name, strategy, message IDs) to the agent-created spans.

When `APPLICATIONINSIGHTS_CONNECTION_STRING` is not set, the agent is not loaded — zero overhead.

## How It Works

```
Azure Container App (same pod, same JVM)
  └── java -javaagent:applicationinsights-agent.jar -jar utlxe.jar
        │
        ├── Agent auto-instruments (no code):
        │     HTTP requests to UTLXe → spans
        │     HTTP calls to Dapr (localhost:3500) → spans
        │     traceparent propagation → parent-child linking
        │     SLF4J/Logback MDC → traceId + spanId in every log entry
        │     JVM metrics → heap, GC, threads
        │
        ├── UTLXe enriches (custom code in Tracing.kt):
        │     utlxe.transformation = "orders-in"
        │     utlxe.strategy = "COMPILED"
        │     utlxe.input.size = 1234
        │     utlxe.output.size = 567
        │     utlxe.message_id = "019e..."
        │     utlxe.correlation_id = "abc..."
        │     utlxe.duration_us = 2100
        │
        └── Exports to Azure Monitor automatically
```

## Enabling / Disabling

| Environment variable | Effect |
|---|---|
| `APPLICATIONINSIGHTS_CONNECTION_STRING` set | Agent loads, spans exported to Azure Monitor |
| Not set | Agent not loaded, zero overhead, zero tracing |

The Dockerfile conditionally loads the agent:

```dockerfile
ENTRYPOINT ["sh", "-c", "AGENT_FLAG=''; \
  if [ -n \"$APPLICATIONINSIGHTS_CONNECTION_STRING\" ]; then \
    AGENT_FLAG='-javaagent:/utlxe/applicationinsights-agent.jar'; \
  fi; \
  java $AGENT_FLAG -Xmx${UTLXE_HEAP_SIZE} $JAVA_OPTS -jar /utlxe/utlxe.jar $@", "--"]
```

## What Appears in Azure Monitor

The distributed trace view shows UTLXe between Dapr input and Dapr output:

```
Service Bus → Dapr input binding → UTLXe transform orders-in (2.1ms) → Dapr output → Service Bus
                                     ├── utlxe.transformation: orders-in
                                     ├── utlxe.strategy: COMPILED
                                     ├── utlxe.input.size: 1234
                                     ├── utlxe.output.size: 567
                                     ├── utlxe.message_id: 019e...
                                     └── utlxe.correlation_id: abc...
```

Click any span → see the related log entries (via traceId correlation).

## Memory Impact

| Component | Size | Impact on 3 GB heap (Starter) |
|---|---|---|
| Agent JAR on disk | ~30 MB | None (disk, not heap) |
| Agent runtime heap | ~30-50 MB | 1-1.5% |
| OpenTelemetry API JAR | ~100 KB | Negligible |
| **Total** | **~50 MB** | **<2% of Starter heap** |

No measurable impact on transformation throughput. The agent exports spans asynchronously in a background thread.

## Why Agent, Not Manual SDK

| Aspect | Manual SDK (original design) | Agent (implemented) |
|---|---|---|
| Code changes | Manual spans in TransportHandlers, context extraction, sub-spans | Two attribute-injection calls |
| Coverage | Only what we explicitly instrumented | All HTTP in, all HTTP out, all JDBC, JVM metrics, log correlation |
| Maintenance | Update span creation code when handlers change | Zero — agent auto-discovers |
| Dependencies | opentelemetry-api + sdk + exporter + autoconfigure (~4 JARs) | opentelemetry-api only (~1 JAR, 100KB) |
| Sub-spans | Manual per-phase (validate, transform, output) | Agent auto-creates for HTTP calls |
| Azure integration | Manual Application Insights exporter | Automatic — agent is Microsoft's official tool |

The agent gives broader coverage with less code. Our custom attributes (`utlxe.transformation`, `utlxe.strategy`, etc.) are the only code — everything else is automatic.

## Proto Impact

None. The proto already has `traceparent` (field 9) and `tracestate` (field 12) on `ExecuteRequest`. The agent handles context propagation at the HTTP layer — before our code sees the request.

## Three Pillars of Observability

| Pillar | Current state | Who handles it |
|---|---|---|
| **Traces** | Spans with custom attributes | Agent (auto) + Tracing.kt (custom attributes) |
| **Metrics** | Prometheus `/metrics` endpoint | UTLXe (custom counters). Agent adds JVM metrics. |
| **Logs** | LogBuffer + SLF4J/Logback | Agent injects traceId/spanId into MDC automatically |

Prometheus stays for application metrics. The agent adds JVM-level metrics (heap, GC, threads) to Azure Monitor. No migration, no duplication, complementary.

## Files Implemented

| File | Change |
|---|---|
| `build.gradle.kts` | `opentelemetry-api:1.40.0` (thin API only, ~100KB) |
| `modules/engine/.../telemetry/Tracing.kt` | `addTransformAttributes()` + `recordResult()` — enriches agent-created spans |
| `modules/engine/.../transport/TransportHandlers.kt` | Two calls in `handleExecute()`: add attributes before, record result after |
| `deploy/docker/Dockerfile.engine` | Downloads agent JAR, conditionally loads via `-javaagent` |

## Relationship to Other Features

- **EF04** (message correlation): MessageId and CorrelationId become span attributes
- **EF05** (Dapr integration): Agent auto-instruments Dapr HTTP calls
- **EF06** (pub/sub): Agent traces pub/sub HTTP delivery automatically
- **EF07** (parallel transports): Agent instruments all HTTP transports; gRPC requires separate interceptor (future)
- **EF12** (logging): Agent injects traceId/spanId into Logback MDC — click trace → see logs

---

*Feature EF14. May 2026. Implemented.*
*Key insight: the Azure Monitor OpenTelemetry agent does 90% of the work automatically. UTLXe only adds custom attributes (transformation name, strategy, message IDs) to the agent-created spans. No manual span creation, no manual context extraction, no manual exporter configuration. One JAR, one env var, full distributed tracing.*
