= Roadmap: What's Next

UTLXe is the data plane — it transforms messages. This chapter describes planned capabilities that extend UTLXe into a complete enterprise integration platform.

== UTLXc: The Control Plane

When you run a dozen UTLXe instances (one per flow or flow group), managing them individually becomes a burden. UTLXc is a separate component — the *control plane* — that provides centralized management for a fleet of UTLXe data planes.

```
┌─────────────────────────────────────────────────┐
│             UTLXc (Control Plane)                │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Fleet    │  │ Central  │  │ Audit    │      │
│  │ Dashboard│  │ Deploy   │  │ Log      │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│         │            │            │              │
└─────────┼────────────┼────────────┼──────────────┘
          │            │            │
    ┌─────┴─────┐ ┌────┴────┐ ┌────┴────┐
    │  UTLXe    │ │  UTLXe  │ │  UTLXe  │
    │  orders   │ │ invoices│ │ returns │
    └───────────┘ └─────────┘ └─────────┘
```

=== What UTLXc Provides

#table(
  columns: (auto, 1fr),
  [*Capability*], [*Description*],
  [Fleet dashboard], [All UTLXe instances at a glance — health, loaded transformations, throughput, errors.],
  [Centralized deployment], [Deploy a `.utlar` bundle to all instances tagged "invoices" with one command.],
  [Aggregate monitoring], [Error rates and throughput across all instances — not per-instance.],
  [Coordinated rollout], [Deploy to one instance, verify health for 5 minutes, then roll to the next.],
  [Audit log], [Who deployed what, when, to which instance.],
  [RBAC via Azure AD], [Viewer, deployer, and admin roles — integrated with Azure Active Directory.],
)

=== How It Works with UTLXe

UTLXc does not replace UTLXe's Admin API — it calls it. Every UTLXe instance remains independently operational. If UTLXc goes down, each UTLXe continues processing messages. UTLXc is a management convenience, not a runtime dependency.

In the first version, UTLXc discovers instances from configuration and calls their Admin APIs. In a future version, UTLXe instances register with UTLXc on startup and send periodic heartbeats — enabling automatic discovery and real-time fleet status.

=== Marketplace Offering

#table(
  columns: (auto, 1fr, 1fr),
  [*Offering*], [*Includes*], [*Target*],
  [UTLXe (standalone)], [One UTLXe instance], [Small teams, 1--3 flows],
  [UTLXe + UTLXc (fleet)], [UTLXc control plane + N UTLXe instances], [Enterprise, 5+ flows],
)

UTLXc is the enterprise upsell. Start with standalone UTLXe, then upgrade when you outgrow manual deployment.

== .NET SDK and BizTalk Integration

UTLXe speaks gRPC alongside HTTP. A .NET SDK will allow C\# applications to call UTLXe transformations directly — without Dapr, without HTTP, without message queues.

Use cases:

- *BizTalk Server migration:* replace BizTalk maps with UTL-X transformations. The .NET SDK calls UTLXe from a BizTalk pipeline component.
- *Azure Functions:* call UTLXe from a C\# Function via gRPC — synchronous request/response.
- *ASP.NET APIs:* transform request or response payloads inline.

The gRPC transport is already implemented (EF07). The .NET SDK wraps it with a typed C\# client:

```csharp
// Future: .NET SDK usage
var client = new UtlxeClient("utlxe.internal:9090");
var result = await client.TransformAsync("invoice-to-ubl", invoiceJson);
Console.WriteLine(result.Output);
```

== Encoding Support (UTF-16, Shift-JIS)

UTLXe currently processes messages as UTF-8 strings. For BizTalk and SAP integrations, UTF-16 and other encodings are common. Planned encoding support will pass byte arrays through the transformation pipeline instead of strings — preserving the original encoding from input to output.

This is required before BizTalk and SAP CPI integrations can handle non-UTF-8 data correctly.

== OpenTelemetry Distributed Tracing

UTLXe currently forwards W3C Trace Context headers (`traceparent`, `tracestate`) from input to output --- the trace chain is preserved, but UTLXe itself is invisible in the distributed trace. Planned OpenTelemetry support (EF14) will add:

*Span creation* --- each transformation execution becomes a span in the distributed trace. Azure Application Insights, Jaeger, or any OpenTelemetry-compatible backend shows UTLXe alongside Dapr and Service Bus:

```
Service Bus → Dapr input → UTLXe transform (2ms) → Dapr output → Service Bus
                              ├── validate input (0.3ms)
                              ├── transform (1.2ms)
                              └── validate output (0.5ms)
```

*Log-trace correlation* --- every log entry is tagged with the trace ID and span ID. Click a span in the trace view to see the corresponding log entries. Click a log entry to see the full trace.

*No metric changes* --- Prometheus metrics stay as they are. OpenTelemetry handles traces and log correlation only. No migration, no disruption.

*Zero overhead when disabled* --- if the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable is not set, no tracing overhead is added. UTLXe continues to forward traceparent/tracestate headers regardless.

*No proto changes required* --- the `traceparent` and `tracestate` fields already exist in the protobuf definition. Spans are created server-side and exported to the collector, not returned to the caller.

== What You Can Do Today

Everything described in this book --- from deployment to monitoring to CI/CD --- works today. The features above are planned extensions. Your investment in UTL-X transformations, Dapr configuration, and CI/CD pipelines carries forward --- nothing changes when these capabilities are added.
