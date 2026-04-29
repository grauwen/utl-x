# F07: Pipeline Multi-Input per Stage (Additional Inputs per Hop)

**Status:** Proposed (was task #25 — pending since early development)  
**Priority:** Medium  
**Created:** April 2026  
**Related:** F03 (join), F04 (lookup), Pipeline Chaining (ch19)

---

## Summary

In a multi-hop pipeline, each stage currently receives only the output of the previous stage. This proposal adds the ability to inject **additional inputs** at each stage — enabling enrichment, lookup, and join operations within a pipeline without pre-loading all data upfront.

## The Problem

Consider a 4-step transformation pipeline:

```
Step 1: Parse incoming order (XML)
Step 2: Validate → needs NOTHING extra
Step 3: Enrich with customer data → needs CUSTOMER TABLE
Step 4: Calculate pricing → needs PRICE LIST
```

Today, Steps 3 and 4 can't get their additional data. The pipeline only passes the output of the previous step — there's no mechanism to inject the customer table or price list at a specific stage.

### Current Workarounds

**Workaround A: Pre-join everything before the pipeline**

```utlx
// Load ALL data before the pipeline starts
let enrichedInput = {
  order: $input.order,
  customers: $input.customers,      // must be available upfront
  priceList: $input.priceList        // must be available upfront
}
// Pass the entire blob through every stage
```

Problems:
- All reference data must be available at pipeline start
- Every stage carries data it doesn't need (wasteful memory)
- Stages are coupled to the data loading order

**Workaround B: Do everything in one transformation (no pipeline)**

```utlx
// One massive .utlx file that does everything
let customer = lookup(order.customerId, $input.customers, ...)
let prices = lookup(order.productCode, $input.priceList, ...)
// ... 200 lines of interleaved logic
```

Problems:
- No separation of concerns
- Can't reuse individual stages
- Can't test stages independently
- The .utlx file becomes unmanageable

## Proposed Solution

### How Transformations Get Into the Engine

Before a pipeline can run, the `.utlx` transformations must be available in the engine. There are two ways:

**Production mode: pre-load transformations (separate step)**

Transformations are loaded once at startup (from a bundle) or via the `/api/load` endpoint:

```bash
# Load transformations (once, at startup or deployment)
POST /api/load  {"transformationId": "parse-order",          "utlxSource": "%utlx 1.0\ninput xml\noutput json\n---\n$input"}
POST /api/load  {"transformationId": "validate-order",       "utlxSource": "..."}
POST /api/load  {"transformationId": "enrich-with-customer", "utlxSource": "..."}
POST /api/load  {"transformationId": "calculate-pricing",    "utlxSource": "..."}
```

The pipeline request then references them by ID only. This is the production pattern — transformations are deployed artifacts, the pipeline executes them.

**Ad-hoc / testing mode: inline `.utlx` source per step**

For quick testing or one-off pipelines, each step can include the `.utlx` source inline:

```json
{
  "transformationId": "parse-order",
  "utlxSource": "%utlx 1.0\ninput xml\noutput json\n---\n$input",
  "payload": "<Order>...</Order>"
}
```

When `utlxSource` is present, the engine loads it temporarily, executes, and discards. This mirrors the existing `/api/transform` endpoint (which combines load + execute in one call).

**Rule:** If `utlxSource` is provided → load inline, execute, discard. If only `transformationId` → use pre-loaded transformation from registry.

### Syntax: additionalInputs per Pipeline Step

**Production mode (pre-loaded transformations):**

```json
POST /api/execute-pipeline
{
  "steps": [
    {
      "transformationId": "parse-order",
      "payload": "<Order>...</Order>",
      "contentType": "application/xml"
    },
    {
      "transformationId": "validate-order"
    },
    {
      "transformationId": "enrich-with-customer",
      "additionalInputs": {
        "customers": "{\"customers\": [{\"id\": \"C-42\", ...}]}"
      }
    },
    {
      "transformationId": "calculate-pricing",
      "additionalInputs": {
        "priceList": "{\"items\": [{\"sku\": \"W-01\", \"price\": 25.00}]}"
      }
    }
  ]
}
```

**Ad-hoc mode (inline `.utlx` source per step):**

```json
POST /api/execute-pipeline
{
  "steps": [
    {
      "transformationId": "step1",
      "utlxSource": "%utlx 1.0\ninput xml\noutput json\n---\n$input",
      "payload": "<Order><CustomerId>C-42</CustomerId><Total>299.99</Total></Order>",
      "contentType": "application/xml"
    },
    {
      "transformationId": "step2",
      "utlxSource": "%utlx 1.0\ninput: order json, customers json\noutput json\n---\n{...$order, customerName: find($customers, (c) -> c.id == $order.CustomerId)?.name}",
      "additionalInputs": {
        "customers": "[{\"id\": \"C-42\", \"name\": \"Acme Corp\"}]"
      }
    }
  ]
}
```

Both modes can be mixed in the same pipeline — some steps pre-loaded, others inline.

### How Each Step Receives Its Data

Each step in the pipeline receives:

| Variable | Source | Available when |
|----------|--------|---------------|
| `$input` | Output of previous step (or payload for first step) | Always |
| `$name` | From `additionalInputs` map | When declared in step |

The `.utlx` transformation at each step uses multi-input syntax to access both:

```utlx
%utlx 1.0
input: order json, customers json
output json
---
let customer = lookup($order.customerId, $customers, (c) -> c.id)
{
  ...$order,
  customerName: customer?.name
}
```

Here `$order` is the pipeline flow (output of previous step) and `$customers` is the additional input injected at this step.

Each step can declare `additionalInputs` — a map of name → payload. These become available as named variables in the transformation alongside `$input` (which is the output of the previous step).

### How the Transformation Accesses Additional Inputs

The transformation at step 3 (`enrich-with-customer.utlx`) would use multi-input syntax:

```utlx
%utlx 1.0
input: order json, customers json
output json
---
let customer = lookup($order.customerId, $customers.customers, (c) -> c.id)

{
  ...$order,
  customerName: customer?.name,
  country: customer?.country
}
```

Here:
- `$order` (or `$input`) = the output from the previous pipeline step (the validated order)
- `$customers` = the additional input injected at this stage

### CLI Syntax

```bash
utlx transform parse.utlx order.xml \
  | utlx transform validate.utlx \
  | utlx transform enrich.utlx --input customers=customers.json \
  | utlx transform pricing.utlx --input priceList=prices.json
```

The `--input name=file` flag provides additional inputs at each stage.

## Can Multi-Step Pipelines Solve the N:M Problem?

This is an important design question. Let's reason through it.

### The Argument For: "Pipeline as Join"

Instead of a `join()` function, you could structure the N:M problem as a pipeline:

```
Step 1: Receive flat data (orders + orderLines in one message)
Step 2: Extract and group order lines by orderId (using groupBy)
Step 3: For each order, attach its lines (additional input: the grouped lines)
```

But this **doesn't actually work** because:

1. **Step 3 needs the grouped lines as an additional input** — but the grouped lines come from Step 2, which is part of the same pipeline. The additional input mechanism is for EXTERNAL data (customer tables, price lists), not for data produced by a previous step.

2. **The pipeline is sequential per message.** Step 2's output IS the input to Step 3. You can't split Step 2's output into "main flow" and "additional input for Step 3."

3. **The N:M join is a within-message restructuring**, not a multi-source enrichment. The orders and orderLines are in the SAME message. A pipeline is for combining data from DIFFERENT sources.

### The Argument Against: "Wrong Abstraction"

The N:M mapping patterns (F03-F06) are **data restructuring operations** — they change the shape of data that's already in memory. They're functions that operate on arrays.

Pipeline multi-input (F07) is a **data flow operation** — it brings in data from external sources at specific processing stages. It's about orchestration, not restructuring.

These are fundamentally different concerns:

| Concern | Mechanism | Example |
|---------|-----------|---------|
| Restructure data shape | Functions: join(), unnest(), chunkBy() | Flat orders → nested orders |
| Enrich from external source | Pipeline additional inputs | Add customer name from CRM |
| Combine multiple sources | Multi-input transformation | Order XML + Customer JSON |

### Where They Overlap

There IS an overlap: **enrichment**. Both `lookup()` (F04) and pipeline additional inputs (F07) can solve "add customer name to order."

With `lookup()` (single-step, reference data in the message):
```utlx
let customer = lookup(order.customerId, $input.customers, (c) -> c.id)
```

With pipeline additional input (multi-step, reference data from external source):
```utlx
// Step 3, with customers injected:
let customer = lookup($input.customerId, $customers.customers, (c) -> c.id)
```

The difference: `lookup()` requires the reference data to be IN the message. Pipeline additional inputs let the reference data come from OUTSIDE (a file, an API response, a database query).

### Verdict

**No — multi-step pipelines do NOT solve the N:M problem.** They solve a different problem (external data injection per stage). The N:M problem needs functions (join, lookup, chunkBy, unnest) that operate on in-memory arrays.

**However, F07 COMPLEMENTS F03-F06 beautifully:**

```
Step 1: Parse IDoc → chunkBy() to group segments (F05)
Step 2: join() lines under headers (F03)
Step 3: lookup() customer data from CRM (F04) ← additional input: customer table (F07)
Step 4: Calculate pricing ← additional input: price list (F07)
Step 5: unnest() for CSV report (F06)
```

F03-F06 handle within-message restructuring. F07 handles between-message data injection. Together, they cover the complete integration transformation landscape.

## Implementation

### UTLXe HTTP Transport Changes

In `HttpTransport.kt`, the pipeline endpoint needs to accept `additionalInputs` per step:

```kotlin
data class PipelineStep(
    val transformationId: String,
    val payload: String? = null,           // only for first step
    val contentType: String? = null,
    val additionalInputs: Map<String, String>? = null  // NEW
)
```

### Engine Changes

In `TransportHandlers.kt` or the pipeline executor:

```kotlin
fun executePipeline(steps: List<PipelineStep>): PipelineResult {
    var currentOutput: UDM = parseInput(steps[0].payload, steps[0].contentType)
    
    for (step in steps) {
        val transformation = registry.get(step.transformationId)
        
        // Build input map: $input + additional inputs
        val inputs = mutableMapOf<String, UDM>("input" to currentOutput)
        step.additionalInputs?.forEach { (name, payload) ->
            inputs[name] = parseInput(payload, detectFormat(payload))
        }
        
        currentOutput = transformation.execute(inputs)
    }
    
    return PipelineResult(currentOutput)
}
```

### CLI Changes

Add `--input name=file` support to the transform command when used in pipe mode:

```bash
utlx transform enrich.utlx --input customers=customers.json < previous-step-output.json
```

The `--input` flag already exists for multi-input transformations. It just needs to work alongside stdin (where stdin is `$input` and `--input` flags are additional named inputs).

### Proto Contract Changes

The `ExecutePipelineRequest` message needs an `additional_inputs` field per step:

```protobuf
message PipelineStep {
    string transformation_id = 1;
    string payload = 2;
    string content_type = 3;
    map<string, string> additional_inputs = 4;  // NEW
}
```

## Effort Estimate

| Task | Effort |
|------|--------|
| HTTP transport: parse additionalInputs in pipeline request | 0.5 day |
| Pipeline executor: build multi-input map per step | 1 day |
| CLI: --input flag alongside stdin for pipe mode | 0.5 day |
| Proto contract update | 0.5 day |
| Integration tests | 1 day |
| Conformance suite tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **4-5 days** |

More complex than F03-F06 because it touches the transport layer, not just stdlib.

## Test Plan

1. Basic: 2-step pipeline, step 2 has additional input → accessible as named variable
2. Multiple additional inputs per step: step has 3 additional inputs → all accessible
3. Different formats: step receives JSON additional input while main flow is XML
4. Missing additional input: transformation references $customers but none provided → clear error
5. First step with additional input: no previous output, only additional inputs
6. CLI: --input flag with stdin → both accessible
7. Proto: ExecutePipelineRequest with additional_inputs → correct execution

## Book Impact

- Chapter 19 (Pipeline Chaining): update multi-hop section with additional inputs
- Chapter 19a (Data Restructuring): add combined example showing F03-F07 together
- Chapter 30 (Engine Lifecycle): update pipeline execution flow

---

*Feature document F07. April 2026. Formerly task #25 (pending).*
