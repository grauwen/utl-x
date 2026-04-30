= AI and UTL-X

== The Natural Language Round-Trip

Most programming languages have a large gap between what you _say_ and what you _write_. A business analyst says "nest the order lines under each order by order ID" — and the developer writes 20 lines of imperative Java with nested loops, hash maps, and null checks. The analyst cannot read the code back and verify it matches their intent.

UTL-X was designed to minimize this gap:

```
Business analyst says:
  "Nest the order lines under each order by order ID,
   look up the customer name from the customer table,
   and calculate the total per order."

Developer writes:
  let orders = nestBy($input.orders, $input.lines,
    (o) -> o.orderId, (l) -> l.orderId, "lines")

  map(orders, (order) -> {
    let customer = lookupBy(order.customerId, $input.customers, (c) -> c.id)
    orderId: order.orderId,
    customerName: customer?.name ?? "Unknown",
    total: sum(map(order.lines, (l) -> l.qty * l.price))
  })

Anyone reads back:
  "Nest lines into orders by orderId.
   Look up customer by customerId.
   Map orders to orderId, customerName, and total."
```

The source code and the specification are almost the same sentence. This is not an accident — it's the result of deliberate naming decisions: `nestBy` reads as "nest by", `lookupBy` reads as "look up by", `filter` reads as "filter where", `sortBy` reads as "sort by". The function names ARE the natural language.

This has a profound implication for AI.

== Why UTL-X Is Uniquely Suited for AI

The distance between natural language and code determines how well an AI can generate that code — and how easily a human can verify the result. UTL-X has the shortest distance of any transformation language:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Language*], [*"Nest lines under orders by key"*], [*Gap*],
  [Java], [20+ lines: HashMap, for-loops, null checks, generics], [Huge],
  [XSLT], [15+ lines: xsl:for-each-group, xsl:key, templates], [Large],
  [DataWeave], [3-5 lines: groupBy + mapObject], [Medium],
  [jq], [Not possible (JSON only, no multi-array join)], [N/A],
  [UTL-X], [`nestBy(orders, lines, ..., "lines")`], [Minimal],
)

A smaller gap means:
- *LLMs generate more accurate code* — less translation needed
- *Fewer iteration rounds* — the first attempt is closer to correct
- *Humans can verify the output* — a business analyst can read `nestBy(..., "lines")` and confirm "yes, that's what I meant"
- *Errors are obvious* — wrong function name or wrong key is visible to non-programmers

=== The Constrained Domain Advantage

General-purpose languages have millions of APIs, design patterns, and architectural choices. An LLM must choose between Java Streams, for-loops, Apache Commons, Guava, and a dozen other approaches for the same task. The search space is enormous.

UTL-X has:
- 652 stdlib functions (not 50,000 APIs)
- One paradigm: functional, expression-based (not OOP + functional + imperative)
- One input: `$input` (not dependency injection, configuration objects, database connections)
- One output: the body expression (not return statements, print, side effects)
- Declarative intent: "what to produce" not "how to produce it"

This makes UTL-X one of the most LLM-friendly languages in existence. The model has fewer choices to make, and more of them are correct.

== What Works Today

General-purpose LLMs (Claude, GPT-4, Gemini) can already generate UTL-X code when given context about the language:

```
Human: "Convert this XML order to JSON. Include the order ID,
        customer name, and a lines array with product and quantity."

LLM generates:
  %utlx 1.0
  input xml
  output json
  ---
  {
    orderId: $input.Order.@id,
    customer: $input.Order.Customer,
    lines: map($input.Order.Lines.Line, (line) -> {
      product: line.Product,
      quantity: toNumber(line.Quantity)
    })
  }
```

This works because UTL-X syntax resembles JSON and JavaScript — familiar territory for any LLM trained on code. Quality is 60-80% correct on first attempt for simple transformations. The main failure modes: hallucinated function names, invented syntax, and missed edge cases (null handling, array-vs-single-element).

=== The MCP Server

UTL-X includes an MCP (Model Context Protocol) server that provides LLMs with the language reference, function signatures, and examples as structured context. This bridges the gap without fine-tuning — the LLM receives exactly the information it needs to generate correct UTL-X.

The MCP server turns any general-purpose LLM into a UTL-X-aware assistant. Combined with the IDE's live preview, the workflow becomes:

+ Describe what you want in natural language
+ LLM generates a `.utlx` transformation via MCP
+ IDE shows the output instantly (live preview)
+ Adjust the description or edit the code directly
+ Iterate until the output matches expectations

== The Vision: AI-Native Transformation Development

=== Token Efficiency

Today, each LLM conversation requires injecting UTL-X context — language syntax, relevant functions, format conventions. This costs 10,000-15,000 tokens before the actual question. A fine-tuned model or well-configured RAG system would reduce this to under 1,000 tokens — a 90%+ reduction in cost per transformation.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Scenario*], [*Without optimization*], [*With RAG or fine-tuning*],
  [Context per request], [~15,000 tokens], [~500-1,000 tokens],
  [Output quality], [60-80% correct], [85-95% correct],
  [Iterations to correct], [2-4 rounds], [0-1 rounds],
  [Cost per transformation], [\$0.12 - \$0.60], [\$0.01 - \$0.05],
)

=== Three Approaches

*RAG (Retrieval-Augmented Generation):* Index all UTL-X documentation, examples, and stdlib functions. When a user asks a question, retrieve the relevant context and inject it into the prompt. No fine-tuning needed — works with any LLM. Best as an immediate, low-cost starting point.

*LoRA Fine-Tuning:* Train a lightweight adapter on top of an open model (Llama, Mistral) using UTL-X examples. The model learns the language natively — no context injection needed. Cost: \$500-2,000 in compute. Requires 1,000-5,000 hand-crafted example pairs.

*Frontier Model Fine-Tuning:* Fine-tune GPT-4o or Claude using the provider's API. Highest quality but higher cost and vendor dependency. Best for a premium product tier.

The recommended path: start with RAG (immediate value), collect user interactions as training data, fine-tune when enough examples exist.

=== Available Training Data

UTL-X already has a rich corpus for AI training:

- 652 stdlib function definitions with signatures, descriptions, and examples
- 453+ conformance test cases (input + transformation + expected output)
- 100+ real-world examples across JSON, XML, CSV, YAML, OData
- Complete language grammar and parser specification
- USDL schema language specification
- This book: 200+ pages of explanations, patterns, and worked examples

The conformance suite is particularly valuable — it provides _verifiable_ quality metrics. A fine-tuned model's output can be tested against the suite: not "does it look right?" but "do 453 tests pass?"

== Capabilities of an AI-Assisted UTL-X Workflow

What becomes possible when the natural language round-trip is fast and reliable:

=== Generation from Spec

```
"I have a Dynamics 365 sales order in OData JSON format.
 Convert it to a Peppol BIS 3.0 UBL invoice in XML.
 Include all line items, VAT calculation at 21%,
 and the customer's billing address."
```

The LLM generates a complete `.utlx` transformation. The developer reviews it in the IDE with live preview against sample data. Minutes instead of hours.

=== Migration from Other Tools

```
"Here's my XSLT stylesheet that converts orders to invoices.
 Rewrite it in UTL-X."
```

The LLM reads the XSLT, understands the intent, and produces equivalent UTL-X. This is more reliable than generic code translation because UTL-X's function names map closely to XSLT operations: `xsl:for-each` becomes `map()`, `xsl:if` becomes `if/else`, `xsl:sort` becomes `sortBy()`.

=== Explanation and Documentation

```
"Explain what this transformation does in plain English."
```

Because UTL-X code already reads close to English, the LLM's explanation is nearly a paraphrase of the code itself. This is the natural language round-trip completing its circle — code that was generated from English is explained back as English.

=== Test Generation

```
"Generate edge-case test data for this transformation.
 Include: empty arrays, null fields, special characters,
 maximum field lengths, and boundary values."
```

The LLM reads the transformation, infers what inputs it expects, and generates test cases that exercise edge conditions. The conformance suite format (YAML with input + expected output) makes this directly usable.

== AI at Design-Time Only

A critical design principle: *AI assists at design-time, never at runtime.*

The `.utlx` file is the transformation. It is deterministic, inspectable, testable, and version-controlled. There is no AI inference in the production engine's hot path. When UTLXe processes 86,000 messages per second, every message is transformed by the same deterministic code — no LLM call, no API latency, no stochastic variation.

This separation matters for:

- *Compliance:* regulated industries require deterministic, auditable transformations. "An AI decided this field mapping" is not acceptable in banking or healthcare.
- *Performance:* LLM calls take 500ms-5s. Transformation takes 0.01ms. AI in the hot path would reduce throughput by 50,000x.
- *Reliability:* LLMs are stochastic — the same prompt can produce different outputs. Production transformations must produce identical output for identical input, every time.
- *Debugging:* when a transformation produces wrong output, you read the `.utlx` file and find the bug. There is no hidden AI layer to investigate.

AI generates the code. Humans review and test it. The engine executes it deterministically. Each layer does what it's best at.

== The Virtuous Circle

UTL-X's natural language affinity creates a virtuous circle:

```
  Natural Language ──→ LLM generates UTL-X
        ↑                      ↓
  Human reads code ←── UTL-X reads like English
```

The better the function names map to English, the better the LLM generates code, the easier it is to verify, the more trust users place in the AI workflow, the more transformations get built, the more training data exists, the better the LLM becomes.

This circle only works when the programming language is designed — from the ground up — to minimize the distance between human intent and machine instruction. That is what UTL-X is.
