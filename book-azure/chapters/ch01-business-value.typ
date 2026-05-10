= What UTLXe Does for Your Business

== The Problem

Every enterprise connects systems that speak different data languages. An ERP sends invoices in JSON. A logistics partner expects XML. A warehouse system needs CSV. A government portal requires UBL 2.1. An IoT platform streams Event Hub telemetry that must be normalized before analysis.

Today, this translation is done by:

- *Custom code* --- developers write integration scripts per connection. Each one is unique, untested, undocumented, and maintained by the person who wrote it.
- *Enterprise middleware* --- MuleSoft, Tibco, SAP CPI, BizTalk. Powerful but expensive (six-figure licenses), complex to operate, and slow to change.
- *Manual processes* --- someone downloads a file, opens Excel, reformats it, and uploads it elsewhere. Error-prone, unscalable, invisible.

The cost is not just the license or the developer hours. It is the *invisible cost*: messages lost between systems, invoices rejected because a field was in the wrong format, shipments delayed because the warehouse system could not parse the order.

== What UTLXe Is

UTLXe is a *data transformation engine* that runs on Azure. You tell it how to translate one format to another using a simple, readable language (UTL-X). It does the rest --- receiving messages, transforming them, and sending the results to the next system.

#table(
  columns: (auto, 1fr),
  [*What you do*], [*What UTLXe does*],
  [Write a transformation rule (10-50 lines)], [Compile it into an optimized runtime],
  [Connect a Service Bus queue], [Receive messages automatically via Dapr],
  [Deploy via Azure Marketplace], [Run as a managed container --- no servers to maintain],
  [Monitor via dashboard], [Track messages, errors, latency in real time],
)

A transformation looks like this:

```
%utlx 1.0
input json
output xml
---
{
  Invoice: {
    ID: $input.invoiceId,
    BuyerName: upperCase($input.customer.name),
    Total: round($input.amount * 1.21, 2)
  }
}
```

That is the entire program. JSON in, XML out. No boilerplate, no framework, no build step. Upload it, and messages start transforming. The engine converts the object structure to XML automatically:

```xml
<Invoice>
  <ID>INV-001</ID>
  <BuyerName>ACME CORP</BuyerName>
  <Total>121.00</Total>
</Invoice>
```

== Who Uses UTLXe

#table(
  columns: (auto, 1fr, 1fr),
  [*Role*], [*What they care about*], [*What UTLXe gives them*],
  [*IT Manager*], [Cost, reliability, time-to-deploy], [Azure Marketplace deploy in minutes. No license negotiation. Pay per container.],
  [*Integration Developer*], [Productivity, correctness, debugging], [Simple language, instant test, error ring buffer, hot-swap without restart.],
  [*Operations*], [Uptime, monitoring, incident response], [Prometheus metrics, Grafana dashboards, pause/resume per transformation.],
  [*Architect*], [Standards, security, scalability], [Managed Identity, VNet isolation, immutable production bundles, CI/CD integration.],
  [*Business Analyst*], [Understanding what transforms happen], [Readable `.utlx` rules --- not code hidden in a Java class.],
)

== Compared to Alternatives

#table(
  columns: (auto, auto, auto, auto, auto),
  [*Aspect*], [*Custom code*], [*MuleSoft / Tibco*], [*Azure Logic Apps*], [*UTLXe*],
  [Deploy time], [Weeks], [Days--weeks], [Hours], [*Minutes*],
  [Format support], [Whatever you code], [Broad], [JSON + XML], [*JSON, XML, CSV, YAML, OData, Avro, Protobuf*],
  [Cost], [Developer hours], [License + infra], [Per execution], [*Per container (fixed)*],
  [Language], [Java / C\# / Python], [DataWeave / XSLT], [Visual designer], [*UTL-X (readable, auditable)*],
  [Testing], [Write your own], [Studio], [Limited], [*Built-in test endpoint*],
  [Hot-swap], [Redeploy], [Redeploy], [Save], [*Instant, zero-downtime*],
  [Monitoring], [Build it], [Included], [Basic], [*Prometheus + Grafana + Admin API*],
  [Lock-down], [Custom], [Platform], [None], [*Immutable .utlar bundles*],
)

== Open Source, No Vendor Lock-In

UTL-X is open source under the AGPL 3.0 license. The language specification, compiler, standard library, and CLI tool are freely available. This matters for three reasons:

*No vendor lock-in.* Your transformation rules are portable text files. They are not compiled into a proprietary binary, not stored in a vendor-specific format, and not locked behind a license server. If you stop using UTLXe tomorrow, your `.utlx` files remain yours --- readable, versioned in git, and executable by the open-source CLI.

*Auditable.* Compliance and security teams can inspect the source code. There is no black box. The transformation language is deterministic --- the same input always produces the same output. Auditors can read a `.utlx` file and understand what it does, unlike XSLT stylesheets or Java integration code.

*Community and longevity.* Open-source projects outlive vendor products. MuleSoft was acquired by Salesforce. Tibco was acquired by private equity. BizTalk was deprecated. Your investment in UTL-X transformations is protected by the open-source license --- even if the company behind it changes, the code and the language survive.

The Azure Marketplace offering (UTLXe) is the *managed, production-hardened* runtime for the open-source language. You pay for the Azure container resources and the operational convenience (Dapr integration, Admin API, monitoring, CI/CD support). You do not pay for the language or the transformation engine itself.

#table(
  columns: (auto, 1fr),
  [*Component*], [*License*],
  [UTL-X language + CLI + stdlib], [Open source (AGPL 3.0) --- free forever],
  [UTLXe engine], [Open source (AGPL 3.0) --- self-host or use Marketplace],
  [Azure Marketplace offering], [Pay for Azure resources only --- no UTLXe license fee],
  [Your `.utlx` transformations], [Yours --- not subject to any UTL-X license],
)

== Azure Marketplace: What You Get

When you deploy UTLXe from the Azure Marketplace, the following is provisioned automatically:

+ *UTLXe container* --- the transformation engine, ready to receive rules.
+ *Dapr sidecar* --- connects to Azure Service Bus, Event Hub, or any Dapr-supported broker. No custom networking.
+ *Admin Web UI* (optional) --- browser-based interface for uploading transformations, testing, and monitoring.
+ *Persistent storage* (optional) --- Azure Files mount so transformations survive container restarts.
+ *Health and metrics* --- Prometheus endpoint for Grafana or Azure Monitor.

The deployment wizard asks for a resource group, region, and an *admin key* --- the key protects the Web UI and Admin API. Save it securely; you need it to log in after deployment. Five minutes later, you have a running transformation engine. Open the URL in your browser, enter the admin key, and the dashboard loads.

== Business Scenarios

=== E-Invoicing (Peppol / UBL)

European e-invoicing regulations require invoices in UBL 2.1 XML format. Your ERP (Dynamics 365, SAP, or a custom system) produces JSON. UTLXe transforms JSON invoices to UBL XML in real time, ready for Peppol delivery.

=== EDI Modernization

Legacy EDI (EDIFACT, X12) can be converted to modern JSON/XML formats for downstream systems. Instead of maintaining decades-old EDI translators, write a 30-line UTL-X rule.

=== IoT Data Normalization

Sensors publish telemetry in varying formats via Event Hub. UTLXe normalizes the data into a consistent schema before it reaches your analytics platform.

=== Multi-System Order Processing

An e-commerce order arrives as JSON. The warehouse needs CSV. The accounting system needs XML. The shipping partner needs a specific JSON structure. UTLXe runs four transformations in parallel --- one input, four outputs.

=== SAP Integration

SAP comes in two flavors, and UTL-X handles both:

- *SAP S/4HANA and SAP HANA* expose data via OData APIs --- a structured format based on JSON with metadata, entity types, and navigation properties. UTL-X has native OData support: it understands OData entity schemas, validates against `$metadata` definitions, and transforms OData payloads to any target format.

- *SAP R/3 (ECC)* exchanges data via IDoc and BAPI --- XML dialects specific to SAP. UTL-X's XML support handles these dialects directly, mapping SAP-specific XML structures to modern JSON, UBL, or any other format.

A typical flow: SAP S/4HANA publishes a business partner change via OData to Azure Service Bus. UTLXe transforms the OData entity into the JSON structure your CRM or data warehouse expects. Or: SAP R/3 sends an IDoc XML via a middleware adapter to Service Bus. UTLXe transforms the IDoc to a normalized JSON event for downstream systems.

For SAP shops, this eliminates the need for SAP CPI or SAP PI/PO as a transformation layer. UTLXe handles the format translation directly, on Azure infrastructure you already manage.

=== API Gateway Transformation

An external API returns data in a format your frontend cannot consume. UTLXe sits behind Azure API Management and transforms the response on the fly.

== Pricing Model

UTLXe runs as an Azure Container App. You pay for the container resources (CPU + memory), not per message or per transformation. This makes costs predictable:

#table(
  columns: (auto, auto, auto),
  [*Plan*], [*Container*], [*Suitable for*],
  [Starter], [1 vCPU, 4 GB RAM], [500--1,000 msg/sec (simple transforms), 200--500 msg/sec (complex). Messages up to ~100 KB.],
  [Professional], [2 vCPU, 8 GB RAM], [2,000--4,000 msg/sec (simple), 500--1,500 msg/sec (complex). Messages up to ~500 KB.],
)

Throughput depends on transformation complexity and message size. Simple field mappings are fast; cross-format conversion with schema validation is slower. The figures above are for typical business document transformations.

Scale horizontally by adding more container instances. Each instance handles its own set of queues --- no shared state, no coordination overhead. Two Starter instances handle twice the throughput.

*Need more?* For large-scale deployments (large SAP IDocs, high-volume streaming, dedicated infrastructure for compliance), custom plans with higher resources are available on request. UTLXe runs on Azure Container Apps workload profiles, supporting up to 16 vCPU and 128 GB RAM per container. Contact us for Enterprise sizing.

No license fees. No per-message charges. No minimum commitment. On the consumption plan, containers can scale to zero --- when no messages are flowing, compute costs stop. You only pay for Azure infrastructure (container resources when running, storage for persistence).

== What This Book Covers

The rest of this book is a hands-on guide for deploying and operating UTLXe on Azure:

- *Chapters 2--5*: Getting started --- quick deploy, write transformations, Admin API, connect to Azure services.
- *Chapter 6*: Architecture deep dive --- how Dapr, Service Bus, and UTLXe work together.
- *Chapters 7--9*: Automation --- environment strategy (Dev/Test/Acc/Prd), infrastructure as code, CI/CD pipelines.
- *Chapters 10--14*: Production --- persistence, security, operations, monitoring, troubleshooting.
- *Chapter 15*: Roadmap --- control plane, .NET SDK, and what is coming next.
- *Appendices*: Complete API reference, configuration reference, UTL-X quick reference.

Start with Chapter 2 (Quick Start) to see UTLXe running in five minutes.
