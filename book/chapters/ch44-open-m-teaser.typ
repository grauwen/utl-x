= Open-M: The Integration Controller

UTL-X transforms data. But in a real integration landscape, transformation is one step in a larger flow: receive a message, route it to the right transformation, validate the output, deliver it to the target, handle errors, retry on failure, log for compliance. That orchestration layer is what Open-M provides.

#block(
  fill: rgb("#E3F2FD"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Open-M is in active development.* This chapter previews the architecture and concepts. UTL-X is fully functional without Open-M — you can use it standalone with Docker, Dapr, or any orchestration tool. Open-M is an optional layer on top.
]

== What Is Open-M?

Open-M is an open-source integration controller written in Go. It manages the lifecycle of messages as they flow between systems — receiving, transforming, validating, routing, and delivering.

Think of it as:
- *Open-M* = the orchestra conductor (decides what happens, when, and where)
- *UTL-X* = the musicians (perform the transformation work)

```
┌────────────────────────────────────────────────────┐
│               Open-M Controller (Go)                │
│                                                     │
│  Receive → Route → ┌──────────┐ → Validate →       │
│  (broker)  (rules)  │  UTLXe   │   (schema)         │
│                     │  (via Go │                     │
│                     │  wrapper)│   Deliver → (target)│
│                     └──────────┘                     │
│                                                     │
│  Error handling: retry, dead-letter, alert           │
│  Monitoring: Prometheus, audit trail                 │
└────────────────────────────────────────────────────┘
```

== Why Open-M + UTL-X?

Separation of concerns:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Open-M handles*], [*UTL-X handles*],
  [WHERE data goes (routing)], [WHAT happens to data (transformation)],
  [WHEN to process (scheduling, triggers)], [HOW to transform (expressions, functions)],
  [Error handling (retry, dead-letter)], [Data validation (schema, assertions)],
  [Monitoring and audit trail], [Format conversion and enrichment],
  [Connector management], [652 stdlib functions],
)

Each does one thing well. UTL-X can be used WITHOUT Open-M (standalone CLI, engine, Docker). Open-M can use other transformation engines — but UTL-X is the default.

== MPPM: Message Processing Pipeline Model

=== What Is MPPM?

MPPM (Message Processing Pipeline Model) is Open-M's core concept: every integration is a *message pipeline* — an ordered sequence of processing stages that a message flows through.

Like BPMN standardizes business processes, MPPM standardizes message flows. It defines:
- How messages flow through processing stages
- What happens at each stage (transform, validate, route, enrich)
- How errors are handled (retry, dead-letter, compensate)
- How stages compose into flows

=== Pipeline Stages

```
┌─────────┐  ┌──────────┐  ┌───────────┐  ┌──────────┐  ┌─────────┐
│ Receive │→│ Transform │→│  Validate  │→│  Route   │→│ Deliver │
│ (input) │  │ (UTL-X)  │  │ (schema)  │  │ (logic)  │  │(output) │
└─────────┘  └──────────┘  └───────────┘  └──────────┘  └─────────┘
      │            │             │              │              │
      └────────────┴─────────────┴──────────────┴──────────────┘
                          Error handler (dead-letter, retry, alert)
```

Each stage is a *plugin* — swap implementations without changing the pipeline definition. UTL-X is the default Transform stage plugin. The Validate stage uses UTLXe's schema validators. The Receive and Deliver stages connect to message brokers, APIs, file systems, or databases.

=== The Message Envelope

Every message in Open-M travels in a standard envelope:

```json
{
  "messageId": "msg-550e8400",
  "correlationId": "flow-xyz-001",
  "timestamp": "2026-04-30T10:30:00Z",
  "source": "dynamics365",
  "contentType": "application/json",
  "headers": {
    "transformId": "order-to-invoice",
    "priority": "normal",
    "retryCount": 0
  },
  "payload": "{\"orderId\": \"ORD-001\", ...}"
}
```

The envelope carries *metadata* about the message — ID, correlation, source, content type, processing headers. The payload carries the actual *data*. UTL-X transforms the payload. Open-M manages the envelope.

This separation means UTL-X never needs to know about routing, retries, or correlation — it receives a payload, transforms it, returns the result. Open-M handles everything else.

=== How MPPM Maps to UTL-X

#table(
  columns: (auto, auto),
  align: (left, left),
  [*MPPM concept*], [*UTL-X equivalent*],
  [Pipeline], [Pipeline chaining (Chapter 20)],
  [Transform stage], [`.utlx` transformation file],
  [Validate stage], [Schema validation (Chapter 19)],
  [Message payload], [`$input`],
  [Message headers], [Accessible via transport headers],
  [Error handling], [`try/catch` in `.utlx`, dead-letter in pipeline],
  [Stage configuration], [`transform.yaml` / output options],
  [Multiple pipelines], [Multiple UTLXe bundles],
)

A single UTL-X transformation IS an MPPM Transform stage. An UTLXe pipeline IS an MPPM pipeline restricted to transformation. Open-M adds routing, error handling, monitoring, and non-transformation stages around it.

== Open-M Architecture

=== Go-Based Controller

Open-M is written in Go — lightweight, fast startup, single binary, native Kubernetes integration. It communicates with UTLXe via the Go wrapper (Chapter 34) using protobuf over stdio.

Why Go for the controller and Kotlin/JVM for the engine?

- *Controller (Go):* needs fast startup, low memory, native Kubernetes client, lightweight HTTP server. Go excels here.
- *Engine (Kotlin/JVM):* needs 652 stdlib functions, schema validation, bytecode compilation, SnakeYAML/Jackson parsers. The JVM ecosystem provides these.

The Go wrapper bridges them — the controller spawns UTLXe as a subprocess and communicates via the uniform SDK interface (Chapter 34).

=== Flow Definitions

Integration flows are defined in YAML:

```yaml
name: order-to-invoice
version: "1.0"
stages:
  - name: receive
    type: azure-service-bus
    config:
      queue: orders-inbound

  - name: transform
    type: utlx
    config:
      transformation: order-to-ubl.utlx
      strategy: COMPILED

  - name: validate
    type: schema
    config:
      schema: ubl-invoice-2.1.xsd
      policy: STRICT

  - name: deliver
    type: http
    config:
      url: https://peppol-ap.example.com/submit
      method: POST

  errorHandler:
    type: dead-letter
    config:
      queue: orders-dlq
      maxRetries: 3
      retryDelay: PT30S
```

This YAML defines a complete integration flow: receive from Service Bus, transform with UTL-X, validate against UBL XSD, deliver to Peppol Access Point, dead-letter on failure with 3 retries. The `.utlx` file is referenced by name — change the transformation without touching the flow definition.

=== Plugin Architecture

Every stage type is a plugin:

- *Receive plugins:* Azure Service Bus, Kafka, GCP Pub/Sub, AWS SQS, HTTP webhook, file watcher, MQTT
- *Transform plugins:* UTL-X (default), pass-through, custom (via HTTP call)
- *Validate plugins:* UTLXe schema validator, custom (via HTTP call)
- *Route plugins:* content-based routing, header-based routing, round-robin
- *Deliver plugins:* HTTP, Service Bus, Kafka, file, SMTP, database insert

Adding a new connector means implementing the plugin interface — not modifying the core controller.

== What This Means for UTL-X Users

=== If You're Using UTL-X Standalone

Nothing changes. UTL-X works perfectly on its own — CLI for development, UTLXe for production, Docker for deployment. Open-M is optional.

=== If You Need Orchestration

When you outgrow "just transformation" and need routing, retry logic, dead-letter handling, flow monitoring, and audit trails — Open-M provides it. Your `.utlx` files work unchanged in Open-M. Your UTLXe deployment works unchanged. You don't rewrite anything — you wrap your transformations in MPPM flow definitions.

=== The Growth Path

```
Level 1: utlx CLI              (development, scripting)
Level 2: UTLXe container        (production, high throughput)
Level 3: UTLXe + Dapr           (broker integration, cloud-native)
Level 4: Open-M + UTLXe         (full orchestration, enterprise-grade)
```

Each level adds capabilities without replacing the previous one. Your `.utlx` files, your conformance tests, your schemas — all carry forward unchanged.
