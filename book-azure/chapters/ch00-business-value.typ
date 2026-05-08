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
<Invoice>
  <ID>{$input.invoiceId}</ID>
  <BuyerName>{upperCase($input.customer.name)}</BuyerName>
  <Total>{round($input.amount * 1.21, 2)}</Total>
</Invoice>
```

That is the entire program. No boilerplate. No framework. No build step. Upload it, and messages start transforming.

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
  [Format support], [Whatever you code], [Broad], [JSON + XML], [*JSON, XML, CSV, YAML, Avro, Protobuf*],
  [Cost], [Developer hours], [License + infra], [Per execution], [*Per container (fixed)*],
  [Language], [Java / C\# / Python], [DataWeave / XSLT], [Visual designer], [*UTL-X (readable, auditable)*],
  [Testing], [Write your own], [Studio], [Limited], [*Built-in test endpoint*],
  [Hot-swap], [Redeploy], [Redeploy], [Save], [*Instant, zero-downtime*],
  [Monitoring], [Build it], [Included], [Basic], [*Prometheus + Grafana + Admin API*],
  [Lock-down], [Custom], [Platform], [None], [*Immutable .utlar bundles*],
)

== Azure Marketplace: What You Get

When you deploy UTLXe from the Azure Marketplace, the following is provisioned automatically:

+ *UTLXe container* --- the transformation engine, ready to receive rules.
+ *Dapr sidecar* --- connects to Azure Service Bus, Event Hub, or any Dapr-supported broker. No custom networking.
+ *Admin Web UI* (optional) --- browser-based interface for uploading transformations, testing, and monitoring.
+ *Persistent storage* (optional) --- Azure Files mount so transformations survive container restarts.
+ *Health and metrics* --- Prometheus endpoint for Grafana or Azure Monitor.

The wizard asks three questions: resource group, Service Bus connection, and an admin password. Five minutes later, you have a running transformation engine.

== Business Scenarios

=== E-Invoicing (Peppol / UBL)

European e-invoicing regulations require invoices in UBL 2.1 XML format. Your ERP (Dynamics 365, SAP, or a custom system) produces JSON. UTLXe transforms JSON invoices to UBL XML in real time, ready for Peppol delivery.

=== EDI Modernization

Legacy EDI (EDIFACT, X12) can be converted to modern JSON/XML formats for downstream systems. Instead of maintaining decades-old EDI translators, write a 30-line UTL-X rule.

=== IoT Data Normalization

Sensors publish telemetry in varying formats via Event Hub. UTLXe normalizes the data into a consistent schema before it reaches your analytics platform.

=== Multi-System Order Processing

An e-commerce order arrives as JSON. The warehouse needs CSV. The accounting system needs XML. The shipping partner needs a specific JSON structure. UTLXe runs four transformations in parallel --- one input, four outputs.

=== API Gateway Transformation

An external API returns data in a format your frontend cannot consume. UTLXe sits behind Azure API Management and transforms the response on the fly.

== Pricing Model

UTLXe runs as an Azure Container App. You pay for the container resources (CPU + memory), not per message or per transformation. This makes costs predictable:

#table(
  columns: (auto, auto, auto),
  [*Plan*], [*Container*], [*Suitable for*],
  [Starter], [1 vCPU, 2 GB RAM], [Up to ~50 messages/sec, small payloads],
  [Professional], [2 vCPU, 4 GB RAM], [Up to ~200 messages/sec, larger payloads],
)

Scale horizontally by adding more container instances. Each instance handles its own set of queues --- no shared state, no coordination overhead.

No license fees. No per-message charges. No minimum commitment. Stop the container, stop paying.

== What This Book Covers

The rest of this book is a hands-on guide for deploying and operating UTLXe on Azure:

- *Chapters 2--5*: Getting started --- quick deploy, write transformations, connect to Azure services.
- *Chapters 6--8*: Automation --- environment strategy (Dev/Test/Acc/Prd), infrastructure as code, CI/CD pipelines.
- *Chapters 9--13*: Production --- persistence, security, operations, monitoring, troubleshooting.
- *Chapter 14*: Roadmap --- control plane, .NET SDK, and what is coming next.
- *Appendices*: Complete API reference, configuration reference, UTL-X quick reference.

Start with Chapter 2 (Quick Start) to see UTLXe running in five minutes.
