= Pipeline Chaining and Multi-Hop Transformations

#block(
  fill: rgb("#E3F2FD"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *UTLXe engine feature.* Pipeline chaining is a production engine capability — it requires UTLXe to orchestrate multi-step flows with in-memory UDM hand-off between stages. The CLI (`utlx`) executes single transformations. You can simulate pipelines on the CLI by chaining `utlx transform` calls with Unix pipes, but each step serializes and re-parses — there is no in-process UDM hand-off.
]

Most transformations are a single step: one input, one transformation, one output. But real-world integration flows are rarely that simple. An order arrives as XML, needs to be normalized, enriched with customer data from a CRM, validated against business rules, and finally rendered as a UBL invoice. Each of these concerns is different — and writing them as one monolithic transformation makes the code unreadable, untestable, and unmaintainable.

Pipeline chaining solves this by splitting complex flows into a sequence of small, focused transformations. Each step does one thing well. The output of step N becomes the input of step N+1. And critically: the UDM passes in-memory between steps — no serialization, no parsing, no overhead.

== Single-Hop vs Multi-Hop

=== Single-Hop (Simple Transformation)

One input, one transformation, one output. The vast majority of transformations are single-hop:

```bash
cat order.xml | utlx transform order-to-json.utlx
```

Use single-hop when: the mapping is straightforward, no external enrichment is needed, and the entire logic fits comfortably in one file.

=== Multi-Hop (Pipeline)

Multiple transformation steps executed in sequence:

```
Input → Step 1 (normalize) → Step 2 (validate) → Step 3 (enrich) → Output
         UDM hand-off         UDM hand-off         UDM hand-off
         (no serialize)       (no serialize)       (no serialize)
```

The key advantage: NO serialization between steps. The UDM tree passes in-memory from one step to the next. A 5-step pipeline saves 10-25ms compared to HTTP calls between steps — because each serialization/deserialization cycle costs 2-5ms.

== Why Pipelines?

=== Separation of Concerns

Each step does ONE thing:

- Step 1: Parse and normalize the incoming format
- Step 2: Validate against business rules
- Step 3: Enrich with lookup data
- Step 4: Transform to target format
- Step 5: Validate output against schema

Changing the enrichment logic doesn't touch the normalization. Swapping the output format doesn't affect validation. Each concern is isolated.

=== Reusability

A "validate Dutch VAT ID" step can be reused across every pipeline that processes Dutch invoices. Write it once, use it everywhere.

=== Testability

Each step can be tested independently with its own input and expected output. When a pipeline breaks, you can pinpoint exactly which step failed by inspecting the intermediate UDM between steps.

=== Maintainability

A 500-line monolithic transformation is hard to understand. Five 100-line steps with clear names (`normalize.utlx`, `validate-vat.utlx`, `enrich-customer.utlx`, `calculate-totals.utlx`, `render-ubl.utlx`) are self-documenting.

== Multi-Input Transformations (Single Step)

Before discussing pipelines, it's important to understand multi-input — a single transformation that receives data from multiple sources:

```utlx
%utlx 1.0
input: orders json, customers json
output json
---
map($orders, (order) -> {
  ...order,
  customerName: find($customers, (c) -> c.id == order.customerId)?.name
})
```

Each input gets its own named variable (`$orders`, `$customers`). When using stdin, the engine parses the input as a JSON envelope and splits it by key name into separate named inputs:

```json
{"orders": [...], "customers": [...]}
```

Multi-input is for a *single step* that needs multiple data sources. Pipelines are for *multiple steps* executed in sequence. They complement each other — a pipeline step can itself be a multi-input transformation.

== Defining Pipelines

=== Pipeline via CLI (Unix Pipes)

The simplest way — chain transformations with the shell pipe:

```bash
cat order.xml \
  | utlx transform normalize.utlx \
  | utlx transform validate.utlx \
  | utlx transform enrich.utlx --input customers=customers.json \
  | utlx transform render-invoice.utlx
```

Each `utlx transform` reads from stdin, transforms, and writes to stdout. The shell connects them. This works but involves serialization between steps (JSON on the pipe).

=== Pipeline via UTLXe (In-Process)

UTLXe executes pipeline steps in-process with UDM hand-off — no serialization between steps:

```json
{
  "steps": [
    {"transformationId": "normalize", "payload": "<Order>...</Order>"},
    {"transformationId": "validate"},
    {"transformationId": "enrich"},
    {"transformationId": "render-invoice"}
  ]
}
```

This is significantly faster for multi-step flows because the UDM tree passes directly from one step to the next in memory.

== Pipeline Patterns

=== Enrichment Pipeline

The most common pattern. Each stage adds context:

```
Input → Normalize → Enrich (+ customer data) → Enrich (+ pricing) → Output
```

The order arrives with IDs. Each enrichment stage resolves an ID to its full record — customer ID to customer name, product code to product description, currency code to exchange rate.

=== Validation Sandwich

Catches errors at both ends:

```
Input → Pre-validate → Transform → Post-validate → Output
```

Pre-validation prevents wasted transformation work on bad input. Post-validation catches mapping bugs before the output leaves. This is the pattern that UTLXe's `ValidationOrchestrator` implements (Chapter 18).

=== Fan-Out

One input generates multiple outputs:

```
Input → Transform → Output A (invoice)
                  → Output B (delivery note)
                  → Output C (packing slip)
```

Same data, different output formats. Implemented as a pipeline that splits at the last stage into parallel transformations.

=== Fan-In (Aggregation)

Multiple inputs combined into one:

```
Input A (orders) ──┐
Input B (returns) ─→ Merge → Transform → Output (report)
Input C (credits) ─┘
```

Use multi-input transformation at the first stage to combine sources, then pipeline the merged data through subsequent steps.

=== Conditional Routing

Route messages to different transformation paths based on content:

```
Input → Classify → if (type == "ORDER") → Order pipeline
                   if (type == "INVOICE") → Invoice pipeline
                   if (type == "RETURN") → Return pipeline
```

The classify step inspects the message and routes it to the appropriate pipeline. One entry point, multiple processing paths.

== Multi-Hop with Additional Inputs per Stage

=== The Problem

In real integration flows, later stages often need ADDITIONAL data that wasn't in the original input:

- Step 1: Parse incoming order (XML) — needs nothing extra
- Step 2: Validate — needs nothing extra
- Step 3: Enrich with customer data — needs a *customer lookup* (JSON from CRM API)
- Step 4: Calculate pricing — needs a *price list* (CSV from database)
- Step 5: Generate invoice — needs *template settings* (YAML config)

Each step needs the result of the previous step PLUS additional inputs from external sources. This is different from multi-input (where all data arrives at step 1) — here, the additional data is injected at specific pipeline stages.

=== Use Cases

*Data enrichment:* Order arrives with customer ID. Step 2 looks up the customer in the CRM. Step 3 looks up product details in the catalog. Step 4 applies tax rules from the tax table. Without per-stage inputs, you'd have to pre-join ALL reference data before step 1 — wasteful if validation fails in step 2.

*Multi-system integration:* A SAP IDoc arrives. Step 1 parses SAP format. Step 2 enriches with Dynamics 365 customer data (OData). Step 3 enriches with Salesforce opportunity data (JSON). Step 4 generates UBL XML for Peppol. Each step brings data from a different source system.

*Configuration-driven output:* The final step applies an output template — a YAML config defining layout, branding, and language. Same data, different templates produce different outputs (invoice, delivery note, packing slip). The template is an additional input to the final stage, not to the entire pipeline.

=== Status

- *Single-step multi-input:* built and working
- *Pipeline chaining (sequential steps):* built and working
- *Additional inputs per pipeline hop:* planned (EF01 — not yet implemented)
- *Current workaround:* pre-join all data before the pipeline, or use multi-input at the first step

== Performance Characteristics

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Characteristic*], [*Value*],
  [Inter-step overhead], [Zero — UDM hand-off in memory (no serialize/parse)],
  [Savings vs HTTP calls], [~10-25ms for a 5-step pipeline (2-5ms per step saved)],
  [Message concurrency], [One worker handles all steps of one message sequentially],
  [Pipeline concurrency], [N workers = N messages flowing through the pipeline simultaneously],
  [Back-pressure], [Controlled throughput — workers block when downstream is slow],
)

Pipeline stages are sequential for a single message — step 2 waits for step 1 to finish. But different messages are processed concurrently across workers. With 32 workers, 32 messages flow through the pipeline simultaneously, each at its own stage.

The trade-off is latency vs throughput: pipeline latency equals the sum of all step latencies (sequential), but pipeline throughput equals one message completion per average-step-time (pipelined across workers).
