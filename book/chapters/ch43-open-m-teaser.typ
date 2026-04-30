= Open-M: The Integration Controller (Teaser)

== What Is Open-M?
// - Open-M is an open-source integration controller
// - Manages integration flows across multiple systems
// - UTL-X is the transformation engine WITHIN Open-M
// - Open-M handles: orchestration, routing, error handling, monitoring
// - UTL-X handles: data transformation between formats
//
// Think of it as:
//   Open-M = the orchestra conductor
//   UTL-X = the musicians (transformation specialists)

== How UTL-X Fits in Open-M
// - Open-M flow:
//   1. Receive message (HTTP, Kafka, Service Bus, file)
//   2. Route to appropriate transformation
//   3. UTLXe transforms the data (via Go wrapper, stdio-proto)
//   4. Validate output
//   5. Deliver to target system
//   6. Handle errors, retries, dead letters
//
// ┌─────────────────────────────────────────────────┐
// │              Open-M Controller (Go)              │
// │                                                  │
// │  Receive → Route → ┌──────────┐ → Validate →   │
// │                     │ UTLXe    │   Deliver       │
// │                     │ (via Go  │                 │
// │                     │ wrapper) │                 │
// │                     └──────────┘                 │
// └─────────────────────────────────────────────────┘

== Why Open-M + UTL-X?
// - Separation of concerns:
//   - Open-M: WHERE data goes (routing, orchestration, error handling)
//   - UTL-X: WHAT happens to data (transformation, validation)
// - Each does one thing well
// - UTL-X can be used WITHOUT Open-M (standalone CLI, engine, Docker)
// - Open-M can use OTHER transformation engines (but UTL-X is the default)

== Open-M Architecture (Preview)
// - Go-based controller
// - Flow definitions in YAML
// - Plugin architecture for connectors
// - UTL-X integration via Go wrapper (stdio-proto)
// - Kubernetes-native deployment
// - Prometheus + Grafana monitoring
// - More details: github.com/open-m (coming soon)

== MPPM: Message Processing Pipeline Model

=== What Is MPPM?
// - Open-M's core concept: every integration is a MESSAGE PIPELINE
// - MPPM (Message Processing Pipeline Model) defines:
//   - How messages flow through processing stages
//   - What happens at each stage (transform, validate, route, enrich)
//   - How errors are handled (retry, dead-letter, compensate)
//   - How stages compose into flows
//
// Think of MPPM as: "a standard way to describe integration flows"
// Like BPMN standardizes business processes, MPPM standardizes message flows.

=== MPPM Stages
// A pipeline consists of ordered stages:
//
// ┌─────────┐  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌─────────┐
// │ Receive │→│ Transform │→│  Validate  │→│  Route   │→│ Deliver │
// │ (input) │  │ (UTL-X)  │  │ (schema)  │  │ (logic)  │  │(output) │
// └─────────┘  └──────────┘  └───────────┘  └──────────┘  └─────────┘
//       │            │             │              │              │
//       └────────────┴─────────────┴──────────────┴──────────────┘
//                           Error handler (dead-letter, retry, alert)
//
// Each stage is a PLUGIN — swap implementations without changing the pipeline.
// UTL-X is the default Transform stage plugin.

=== MPPM Message Envelope
// Every message in Open-M travels in a standard envelope:
// {
//   "messageId": "msg-001",
//   "correlationId": "flow-xyz",
//   "timestamp": "2026-04-28T10:30:00Z",
//   "source": "erp-system",
//   "contentType": "application/xml",
//   "headers": {
//     "transformId": "order-to-invoice",
//     "priority": "normal"
//   },
//   "payload": "<Order>...</Order>"
// }
//
// The envelope carries METADATA about the message.
// The payload carries the actual DATA.
// UTL-X transforms the PAYLOAD. Open-M manages the ENVELOPE.

=== How MPPM Maps to UTL-X
//
// | MPPM Concept | UTL-X Equivalent |
// |-------------|------------------|
// | Pipeline | Pipeline chaining (multi-hop) |
// | Transform stage | .utlx transformation |
// | Validate stage | Schema validation (pre/post) |
// | Message payload | $input |
// | Message headers | Accessible via Dapr headers or HTTP headers |
// | Error handling | try/catch in .utlx, dead-letter in pipeline |
// | Stage config | transform.yaml / output options |
//
// A single UTL-X transformation IS an MPPM Transform stage.
// An UTLXe pipeline IS an MPPM pipeline (restricted to transformation).
// Open-M adds: routing, error handling, monitoring, and non-transformation stages.

=== Why MPPM Matters for UTL-X Users
// - If you outgrow "just transformation" and need full orchestration → Open-M
// - Your .utlx files work unchanged in Open-M (they're pipeline stages)
// - Your UTLXe deployment works unchanged (Open-M calls it via Go wrapper)
// - MPPM provides: retry logic, dead-letter handling, flow monitoring, audit trail
// - You don't need to rewrite anything — just wrap your transformations in MPPM

== What This Means for UTL-X Users
// - If you're using UTL-X standalone: nothing changes
// - If you need orchestration around UTL-X: Open-M provides it
// - Open-M is optional — UTL-X is fully functional on its own
// - The Go wrapper we built (Chapter SDKs) is the bridge
// - MPPM is the contract between Open-M and UTL-X

// Note: Open-M is in active development.
// This chapter will be expanded in future editions of this book.
