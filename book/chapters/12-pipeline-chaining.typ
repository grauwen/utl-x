= Pipeline Chaining and Multi-Hop Transformations

== What Is Pipeline Chaining?
// - Multiple transformation steps executed in sequence
// - In-process UDM hand-off (no serialization between steps)
// - Like a Unix pipe but for data transformations
// - Each step is a separate .utlx transformation
// - Output of step N becomes input of step N+1

== Why Pipelines?
// - Separation of concerns: each step does ONE thing
//   - Step 1: parse and normalize
//   - Step 2: validate
//   - Step 3: enrich with lookup data
//   - Step 4: transform to target format
//   - Step 5: validate output
// - Reusability: the "validate" step can be reused across different pipelines
// - Testability: each step can be tested independently
// - Maintainability: change one step without affecting others
// - Debugging: inspect the intermediate UDM between steps

== Single-Hop vs Multi-Hop

=== Single-Hop (Simple Transformation)
// One input → one transformation → one output
//
// ┌───────┐     ┌──────────┐     ┌────────┐
// │ Input │────→│ Transform│────→│ Output │
// │ (XML) │     │ (.utlx)  │     │ (JSON) │
// └───────┘     └──────────┘     └────────┘
//
// Use when: simple field mapping, format conversion, no enrichment needed
// Example: cat order.xml | utlx transform order-to-json.utlx

=== Multi-Hop (Pipeline)
// One input → step 1 → step 2 → step 3 → ... → output
//
// ┌───────┐   ┌─────────┐   ┌──────────┐   ┌─────────┐   ┌────────┐
// │ Input │──→│ Step 1  │──→│ Step 2   │──→│ Step 3  │──→│ Output │
// │ (XML) │   │normalize│   │ validate │   │ enrich  │   │ (JSON) │
// └───────┘   └─────────┘   └──────────┘   └─────────┘   └────────┘
//              UDM hand-off   UDM hand-off   UDM hand-off
//              (no serialize) (no serialize) (no serialize)
//
// Use when: complex processing that benefits from separation of concerns
// Key advantage: NO serialization between steps — UDM passes in-memory

== Multi-Hop with Additional Inputs per Stage

=== The Problem
// In real integration flows, later stages often need ADDITIONAL data
// that wasn't in the original input:
//
// - Step 1: Parse incoming order (XML)
// - Step 2: Validate → needs NOTHING extra
// - Step 3: Enrich with customer data → needs CUSTOMER LOOKUP (JSON from API)
// - Step 4: Calculate pricing → needs PRICE LIST (CSV from database)
// - Step 5: Generate invoice → needs TEMPLATE SETTINGS (YAML config)
//
// Each step needs the RESULT of the previous step PLUS additional inputs.

=== The Solution: Additional Inputs per Hop
// Each pipeline stage can declare additional inputs alongside the pipeline flow:
//
// ┌───────┐   ┌─────────┐   ┌──────────────────┐   ┌──────────────┐   ┌────────┐
// │ Order │──→│ Step 1  │──→│ Step 2           │──→│ Step 3       │──→│ Output │
// │ (XML) │   │normalize│   │ enrich           │   │ generate inv │   │ (XML)  │
// └───────┘   └─────────┘   │                  │   │              │   └────────┘
//                            │ + Customer (JSON)│   │ + Config (YML)│
//                            │ + Pricing (CSV)  │   │              │
//                            └──────────────────┘   └──────────────┘
//
// Each step receives:
//   $input = output of previous step (the pipeline flow)
//   $customer = additional input (from API, file, or inline)
//   $pricing = additional input (from database export)

=== When Is Multi-Hop with Additional Inputs Handy?

==== 1. Data Enrichment Pipelines
// - Order arrives with customer ID but no customer details
// - Step 1: normalize order
// - Step 2: enrich with customer data (lookup by ID from CRM API)
// - Step 3: enrich with product details (lookup from catalog)
// - Step 4: calculate totals with tax rules (lookup from tax table)
//
// Without multi-hop inputs: you'd have to pre-join ALL data before step 1
// (wasteful — you may not need all data if validation fails in step 2)

==== 2. Multi-System Integration (Hub-and-Spoke)
// - Central message arrives (e.g., SAP IDoc)
// - Step 1: parse SAP format
// - Step 2: enrich with Dynamics 365 customer data (OData API)
// - Step 3: enrich with Salesforce opportunity data (JSON API)
// - Step 4: generate target format (UBL XML for Peppol)
//
// Each step brings in data from a DIFFERENT source system.
// The pipeline orchestrates the flow; each step handles one source.

==== 3. Validation with External Reference Data
// - Invoice arrives
// - Step 1: normalize
// - Step 2: validate VAT IDs against VIES database (additional input: valid VAT list)
// - Step 3: validate product codes against catalog (additional input: product catalog)
// - Step 4: generate output
//
// Validation rules need reference data that isn't in the original message.

==== 4. Template-Based Output Generation
// - Data flows through normalization and enrichment stages
// - Final step: apply output template
// - Template is an additional input (YAML/JSON config defining layout, branding, language)
// - Same data, different templates → different outputs (invoice, delivery note, packing slip)

==== 5. Configuration-Driven Transformations
// - Mapping rules stored in a configuration file (not hardcoded in .utlx)
// - Step 1: parse input
// - Step 2: apply mapping (additional input: mapping-config.yaml)
// - The same transformation handles different customers by swapping the config
// - Customer A: mapping-config-a.yaml, Customer B: mapping-config-b.yaml

== Defining Pipelines

=== Pipeline via UTLXe HTTP API
// POST /api/execute-pipeline
// {
//   "steps": [
//     { "transformationId": "normalize", "payload": "<Order>...</Order>" },
//     { "transformationId": "validate" },
//     { "transformationId": "enrich", "additionalInputs": {
//         "customer": "{\"id\":\"C-123\",\"name\":\"Acme\"}"
//       }
//     },
//     { "transformationId": "generate-invoice" }
//   ]
// }

=== Pipeline via CLI
// utlx transform normalize.utlx order.xml \
//   | utlx transform validate.utlx \
//   | utlx transform enrich.utlx --input customer=customer.json \
//   | utlx transform generate-invoice.utlx

== Multi-Input Transformations (Single Step)
// - Syntax: input: customer json, order xml
// - Accessing multiple inputs: $customer, $order
// - Joining data from different sources and formats
// - The envelope pattern for multi-input via stdin:
//   { "customer": {...}, "order": {...} } on stdin
//   UTL-X splits by key name into separate named inputs
//
// Multi-input is for a SINGLE step that needs multiple data sources.
// Multi-hop additional inputs is for PIPELINE stages that need extra data.
// They complement each other.

== Pipeline Patterns

=== Enrichment Pipeline
// Input → Normalize → Enrich (+ lookup data) → Transform → Output
// Most common pattern. Each enrichment stage adds context.

=== Validation Sandwich
// Input → Pre-validate → Transform → Post-validate → Output
// Catches errors at both ends. Pre-validation prevents wasted transformation work.

=== Fan-Out
// Input → Transform → [Output A, Output B, Output C]
// One input generates multiple outputs (e.g., invoice + delivery note + packing slip).
// Implemented as: pipeline splits at the last stage into 3 parallel transforms.

=== Fan-In (Aggregation)
// [Input A, Input B, Input C] → Merge → Transform → Output
// Multiple inputs combined into one. Multi-input transformation at the first stage.

=== Conditional Routing
// Input → Classify → if (type == "A") Step A else Step B → Output
// Route messages to different transformation paths based on content.

== Performance Characteristics
// - In-process: no network I/O between steps
// - UDM hand-off: no serialization/deserialization between steps
//   - Serialization cost: ~2-5ms per step (saved by in-process hand-off)
//   - 5-step pipeline saves ~10-25ms vs HTTP calls between steps
// - Back-pressure: controlled throughput under load
// - Worker threads: one worker handles all steps of one message (no context switch)
// - Pipeline latency = sum of step latencies (sequential, not parallel)
//
// Trade-off: pipeline stages are sequential for ONE message,
// but different messages are processed concurrently across workers.
// 32 workers = 32 messages flowing through the pipeline simultaneously.

== Status: Multi-Hop Additional Inputs
// - Single-step multi-input: BUILT and working
// - Pipeline chaining (sequential steps): BUILT and working
// - Additional inputs per pipeline hop: PLANNED (not yet implemented)
// - Current workaround: pre-join data before pipeline, or use HTTP calls between stages
