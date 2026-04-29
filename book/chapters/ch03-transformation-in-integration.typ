= Transformation: The Cornerstone of Integration

== Why Transformation Matters
// - Every integration connects systems that speak DIFFERENT languages
// - ERP speaks XML/IDoc, API speaks JSON, legacy speaks CSV, config speaks YAML
// - Without transformation: point-to-point spaghetti, custom code per connection
// - With transformation: standardized data exchange, maintainable, testable

== The Three Pillars of Integration
// 1. MESSAGING: getting data from A to B (Service Bus, Kafka, HTTP, SFTP)
// 2. TRANSFORMATION: converting data from format A to format B
// 3. ROUTING/ORCHESTRATION: deciding WHERE to send and WHEN
//
// Traditional ESB (Enterprise Service Bus):
// ┌─────────────────────────────────────────────────┐
// │                 ESB / iPaaS                      │
// │  ┌──────────┐  ┌──────────────┐  ┌───────────┐  │
// │  │ Messaging│  │Transformation│  │  Routing   │  │
// │  │ (broker) │  │  (mapping)   │  │(orchestr.) │  │
// │  └──────────┘  └──────────────┘  └───────────┘  │
// └─────────────────────────────────────────────────┘
//
// UTL-X focuses on pillar 2 (transformation) — the hardest to do well.
// Messaging (pillar 1) is handled by: Service Bus, Kafka, Pub/Sub, Dapr.
// Routing (pillar 3) is handled by: Logic Apps, Open-M, custom orchestration.

== Transformation in ESB Architecture
// - Tibco BW: transformation is a Mapper activity (XSLT under the hood)
// - MuleSoft: DataWeave is the transformation language
// - SAP CPI: Message Mapping (XSLT) + Groovy scripts
// - Azure Logic Apps: Data Mapper (XSLT) + Liquid templates
// - All of them: transformation is THE critical activity in every flow
//
// A typical integration flow has 3-7 transformation steps:
//   Receive → Parse → Validate → Transform → Enrich → Format → Send
//   Every step that touches data content is a transformation.

== Transformation in Workflow/BPM
// - Business processes often need data transformation between steps
// - Camunda/jBPM: service tasks call transformation services
// - Power Automate: Compose/Parse JSON actions (very limited)
// - UTL-X as a transformation service: called by workflow engines via HTTP

== Why UTLXe Needs Messaging Capabilities
// - In a microservices/event-driven architecture:
//   Messages arrive via broker (Service Bus, Kafka, Pub/Sub)
//   → UTLXe consumes the message
//   → Transforms it
//   → Produces the result to another topic/queue
//
// UTLXe is NOT a message broker — it doesn't store or route messages.
// UTLXe IS a transformation SERVICE that participates in the messaging flow.
//
// Without messaging integration (HTTP-only):
//   Producer → HTTP POST to UTLXe → HTTP response → separate step to publish
//   (synchronous, blocking, the producer must wait)
//
// With messaging integration (Dapr/Pub/Sub):
//   Producer → Broker → UTLXe (automatic consume) → Broker → Consumer
//   (asynchronous, non-blocking, natural event-driven flow)
//
// This is why UTLXe supports Dapr bindings — to be a first-class participant
// in event-driven architectures without requiring custom glue code.

== The Canonical Data Model Pattern
// - Define ONE internal format for your organization
// - Every inbound system transforms TO the canonical model
// - Every outbound system transforms FROM the canonical model
// - N systems: N inbound + N outbound transforms (linear)
//   vs N×(N-1) point-to-point transforms (quadratic)
//
// UTL-X is ideal for canonical model implementations:
// - Format-agnostic: the canonical model can be JSON, XML, or YAML
// - Same language for inbound and outbound transforms
// - Schema validation: canonical model enforced by JSON Schema/XSD
