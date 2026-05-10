# EF14: OpenTelemetry Distributed Tracing

**Status:** Design  
**Priority:** Medium (production observability for enterprise customers)  
**Created:** May 2026  
**Depends on:** EF04 (message correlation), EF05 (Dapr integration)

---

## Summary

UTLXe currently forwards W3C Trace Context headers (`traceparent`, `tracestate`) from input to output — preserving the trace chain. But it does not create its own spans. In a distributed trace, UTLXe is a gap:

```
Service Bus → Dapr input → [invisible] → Dapr output → Service Bus
```

EF14 adds OpenTelemetry span creation so UTLXe appears in the trace:

```
Service Bus → Dapr input → UTLXe transform (2ms) → Dapr output → Service Bus
```

## What Changes

Each transformation execution creates a span with:

| Attribute | Value |
|---|---|
| `span.name` | `utlxe.transform {transformation-name}` |
| `span.kind` | `CONSUMER` (for Dapr input) or `SERVER` (for HTTP) |
| `utlxe.transformation` | Transformation name (e.g., `orders-in`) |
| `utlxe.strategy` | `COMPILED`, `TEMPLATE`, `COPY` |
| `utlxe.input.size` | Input payload size in bytes |
| `utlxe.output.size` | Output payload size in bytes |
| `utlxe.duration_us` | Transformation duration in microseconds |
| `utlxe.message_id` | UUIDv7 message ID |
| `utlxe.correlation_id` | Correlation ID (constant across chain) |
| `utlxe.validation.policy` | Effective validation policy |
| `utlxe.error` | Error message (if failed) |

Sub-spans for phases:

| Sub-span | When |
|---|---|
| `utlxe.validate.input` | Pre-validation (schema check) |
| `utlxe.transform` | Transformation execution |
| `utlxe.validate.output` | Post-validation |
| `utlxe.output.forward` | Dapr output binding/publish call |

## Implementation

### Dependencies

```kotlin
// OpenTelemetry SDK
implementation("io.opentelemetry:opentelemetry-api:1.40.0")
implementation("io.opentelemetry:opentelemetry-sdk:1.40.0")
implementation("io.opentelemetry:opentelemetry-exporter-otlp:1.40.0")
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.40.0")
```

### Configuration

```bash
# Environment variables (standard OpenTelemetry)
OTEL_SERVICE_NAME=utlxe
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317  # or Azure Monitor endpoint
OTEL_TRACES_EXPORTER=otlp                           # or azure_monitor
```

When `OTEL_EXPORTER_OTLP_ENDPOINT` is not set, tracing is disabled — zero overhead. UTLXe continues to forward traceparent/tracestate headers regardless.

### Azure Monitor Integration

Azure Container Apps has built-in Application Insights support. Set the connection string:

```bash
APPLICATIONINSIGHTS_CONNECTION_STRING=InstrumentationKey=...
```

OpenTelemetry auto-exports spans to Application Insights. The distributed trace view in Azure Monitor shows UTLXe alongside Dapr and Service Bus.

### Where Spans Are Created

| Transport | Span parent | How |
|---|---|---|
| Dapr binding input (`POST /{bindingName}`) | From `traceparent` header (Dapr propagates) | Extract context from headers |
| Dapr pub/sub input (`POST /pubsub/{name}`) | From `ce-traceparent` or CloudEvents header | Extract from CloudEvents |
| Direct HTTP (`POST /api/execute/{id}`) | From `traceparent` header (client propagates) | Extract from headers |
| gRPC | From gRPC metadata | Extract via gRPC interceptor |
| No trace context | New root span | UTLXe creates a new trace |

## Files to Modify

| File | Change |
|---|---|
| `build.gradle.kts` | Add OpenTelemetry SDK dependencies |
| New: `modules/engine/.../telemetry/Tracing.kt` | Tracer setup, span creation helpers, context extraction |
| `modules/engine/.../transport/HttpTransport.kt` | Wrap `handleDaprInput()` and `handlePubSubInput()` in spans |
| `modules/engine/.../transport/TransportHandlers.kt` | Wrap `handleExecute()` in spans with sub-spans per phase |
| `modules/engine/.../validation/ValidationOrchestrator.kt` | Create sub-spans for pre/post validation |
| `modules/engine/.../Main.kt` | Initialize OpenTelemetry SDK on startup (auto-configure) |

## Effort Estimate

| Task | Effort |
|---|---|
| OpenTelemetry SDK setup + auto-configure | 0.5 day |
| Span creation in TransportHandlers | 1 day |
| Sub-spans for validation phases | 0.5 day |
| Context extraction from Dapr/CloudEvents/gRPC | 1 day |
| Azure Monitor integration testing | 0.5 day |
| Tests | 0.5 day |
| **Total** | **~4 days** |

## Relationship to Other Features

- **EF04** (message correlation): MessageId and CorrelationId become span attributes
- **EF05** (Dapr integration): traceparent forwarding already works — EF14 adds span creation
- **EF06** (pub/sub): CloudEvents trace context extraction
- **EF07** (parallel transports): spans on all transports (HTTP, gRPC, stdio)
- **EF12** (logging): span IDs can be added to log entries for log-trace correlation

---

*Feature EF14. May 2026. Design document.*
## Proto Impact

None. The proto already has `traceparent` (field 9) and `tracestate` (field 12) on `ExecuteRequest`. The response does not need trace fields — spans are created server-side and exported to the OpenTelemetry collector, not returned to the caller.

## Three Pillars of Observability

OpenTelemetry defines three pillars. EF14 covers traces; metrics and logs are addressed separately:

| Pillar | Current state | EF14 scope | Future |
|---|---|---|---|
| **Traces** | Forward `traceparent`/`tracestate` only — UTLXe is invisible in traces | **Add span creation** — UTLXe appears in distributed traces | Done with EF14 |
| **Metrics** | Prometheus `/metrics` endpoint (custom counters) | Not changed — Prometheus works | Future: optionally export via OTel Metrics SDK alongside Prometheus |
| **Logs** | LogBuffer (in-memory ring buffer, Admin API) | **Add trace ID + span ID to log entries** — correlate logs with traces | Done with EF14 |

### Log-Trace Correlation (included in EF14)

When OpenTelemetry is active, inject the trace ID and span ID into every log entry:

```
2026-05-10 14:30:05 INFO [traceId=abc123 spanId=def456] HttpTransport: [orders-in] MessageId=msg-789 payload=1234B
```

This allows Azure Monitor / Application Insights to link log entries to the specific trace and span. Click a span in the trace view → see the corresponding log entries.

Implementation: add an MDC (Mapped Diagnostic Context) filter that sets `traceId` and `spanId` from the active OpenTelemetry span context. Logback picks them up via `%X{traceId}` in the pattern.

### Why Not Replace Prometheus with OTel Metrics

Prometheus metrics work today and are scraped by Azure Monitor. Replacing them with the OpenTelemetry Metrics SDK means:
- Running two metric systems during migration (Prometheus + OTel)
- Changing Grafana dashboards and alert rules
- Risk of metric gaps during the transition

Not worth the disruption. Keep Prometheus for metrics. Use OpenTelemetry for traces and log correlation. If a customer specifically needs OTel metrics export, it can be added later as a separate exporter alongside Prometheus — not a replacement.

---

*Feature EF14. May 2026. Design document.*
*Key insight: traceparent forwarding (EF04) keeps the trace chain intact. EF14 makes UTLXe visible in the chain by creating spans and correlating logs. Without EF14, UTLXe is a gap in the distributed trace. With EF14, operators see exactly how long each transformation takes in the context of the full message flow, and can click a span to see the related log entries.*
