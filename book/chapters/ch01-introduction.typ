= Introduction

Every enterprise integration project faces the same fundamental problem: systems don't speak the same language. Your ERP sends XML. Your REST API expects JSON. Your legacy batch process reads CSV. Your DevOps pipeline is configured in YAML. And somewhere, an OData service from Microsoft Dynamics 365 is waiting for its own peculiar JSON dialect.

Traditionally, you'd learn a different tool for each format. XSLT for XML. Liquid templates for JSON. Custom Python scripts for CSV. And for YAML? Good luck — most tools pretend it doesn't exist.

UTL-X changes this. One language. All formats. Write your transformation logic once, and it works whether your input is XML, JSON, CSV, YAML, or OData.

== The Data Transformation Problem

Consider a typical integration scenario. A Dutch wholesale distributor receives purchase orders from 12 European partners. Partner A sends XML via SOAP. Partner B posts JSON to a REST API. Partner C drops CSV files on an SFTP server. Partner D sends YAML webhooks from their Kubernetes-based platform.

Without a format-agnostic tool, this distributor needs:

- An XSLT stylesheet for Partner A's XML
- Custom C\# code for Partner B's JSON
- A Python script for Partner C's CSV
- Yet another script for Partner D's YAML

Four languages. Four maintenance burdens. Four sets of bugs. And when a new partner appears with OData from SAP? A fifth.

With UTL-X, all four partners are handled by the same transformation language. The `.utlx` file doesn't care whether the input is XML or CSV — it operates on the _Universal Data Model_ (UDM), an internal representation that normalizes all formats into one tree structure.

// DIAGRAM: Four partners sending different formats → UTL-X (single engine) → canonical output
// Source: part1-foundation.pptx, slide 1

== What Is UTL-X?

UTL-X — _Universal Transformation Language Extended_ — is a format-agnostic, functional transformation language for data integration. It is:

- *Format-agnostic*: one transformation works with XML, JSON, CSV, YAML, and OData
- *Functional*: declarative, composable, testable — no mutable state, no side effects
- *Open source*: licensed under AGPL-3.0, free for any use (see Chapter 2)
- *Production-ready*: 86,000+ messages per second, schema validation, pipeline chaining
- *Batteries included*: 652 built-in functions across 18 categories

At its core, UTL-X answers a simple question: _"Given this input data, produce that output data."_ Everything else — format parsing, type coercion, attribute handling, namespace resolution — is handled automatically.

== The Swiss Army Knife Analogy

UTL-X is often compared to a Swiss Army knife for data. The analogy is apt:

- Each *blade* is a format: XML, JSON, CSV, YAML, OData
- The *handle* is the UDM — the common core that connects all blades
- The *tools* are the 652 standard library functions: string manipulation, date arithmetic, array operations, cryptographic hashing, and more
- You carry *one tool* instead of five — and it handles any situation

// DIAGRAM: Swiss Army knife with format blades (reuse the UTL-X logo concept)
// Source: existing logo

== UTL-X vs The Alternatives

How does UTL-X compare to existing transformation tools?

#table(
  columns: (auto, auto, auto, auto, auto, auto),
  align: (left, center, center, center, center, center),
  [*Capability*], [*XSLT*], [*DataWeave*], [*jq*], [*JSONata*], [*UTL-X*],
  [JSON native], [No], [Yes], [Yes], [Yes], [Yes],
  [XML native], [Yes], [Yes], [No], [No], [Yes],
  [CSV native], [No], [Yes], [No], [No], [Yes],
  [YAML native], [No], [Yes], [No], [No], [Yes],
  [OData native], [No], [No], [No], [No], [Yes],
  [Schema validation], [XSD], [Limited], [No], [No], [7 formats],
  [Open source], [Standard], [No], [Yes], [Yes], [Yes (AGPL)],
  [Production engine], [Yes], [Yes], [No], [No], [Yes],
  [Functions], [~50], [~200], [~50], [~30], [*652*],
  [Cloud Marketplace], [No], [MuleSoft], [No], [No], [*Azure/GCP/AWS*],
)

XSLT is the 25-year veteran — powerful for XML, but XML-only and verbose. DataWeave (MuleSoft) is the closest commercial competitor — format-agnostic and functional, but proprietary and expensive (\$1,250+/month). jq is the beloved command-line JSON processor — fast and elegant, but JSON-only. JSONata is a compact JSON query language — clean syntax, but limited scope.

UTL-X combines the breadth of DataWeave with the openness of jq, adds production deployment capabilities, and extends to formats that no other tool covers natively.

== Architecture Overview

UTL-X's architecture is built around one central idea: _all data is the same, regardless of format._

// DIAGRAM: Input → Parse → UDM → Transform → UDM → Serialize → Output
// Source: part1-foundation.pptx, slide 2

The pipeline works in three stages:

+ *Parse*: The input — whether XML, JSON, CSV, YAML, or OData — is parsed into the Universal Data Model (UDM). The UDM is a tree of typed nodes: scalars (strings, numbers, booleans), objects (with properties and optionally XML attributes), and arrays.

+ *Transform*: The `.utlx` transformation operates on the UDM tree. It can access any property, iterate arrays, apply functions, and construct new structures. The transformation is format-agnostic — it doesn't know or care whether the data came from XML or JSON.

+ *Serialize*: The resulting UDM tree is serialized to the target format. XML gets elements and attributes. JSON gets objects and arrays. CSV gets rows and columns. Each serializer handles format-specific concerns (XML namespaces, JSON types, CSV delimiters) transparently.

This three-stage architecture is what makes format-agnosticism possible. The transformation logic never touches raw XML tags or JSON braces — it works with the _meaning_ of the data, not its _syntax_.

== The UTL-X Ecosystem

UTL-X is not a single tool — it's an ecosystem of three executables, each designed for a different stage of the development lifecycle:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Executable*], [*Purpose*], [*When to use*],
  [`utlx`], [CLI — command-line tool], [Development, scripting, CI/CD],
  [`utlxd`], [Daemon — IDE integration], [VS Code extension, live preview],
  [`utlxe`], [Engine — production runtime], [Cloud deployment, Azure Marketplace],
)

All three share the same parser, interpreter, UDM, and standard library. A transformation written in VS Code (via `utlxd`) runs identically on the command line (via `utlx`) and in production (via `utlxe`). Chapter 5 explains each executable in detail.

== A First Example

Let's see UTL-X in action. Given this XML order:

```xml
<Order id="ORD-001">
  <Customer>Alice Johnson</Customer>
  <Items>
    <Item sku="WIDGET-01" price="50.00" qty="2"/>
    <Item sku="GADGET-02" price="75.00" qty="1"/>
  </Items>
</Order>
```

We want to produce this JSON:

```json
{
  "orderId": "ORD-001",
  "customer": "Alice Johnson",
  "total": 175.00
}
```

Here is the UTL-X transformation:

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  customer: $input.Order.Customer,
  total: sum(map($input.Order.Items.Item,
    (item) -> toNumber(item.@price) * toNumber(item.@qty)
  ))
}
```

Let's break it down:

- `%utlx 1.0` — declares this is a UTL-X version 1.0 transformation
- `input xml` — the input data is XML
- `output json` — the output should be JSON
- `---` — separates the header from the body
- `\$input.Order.\@id` — accesses the `id` attribute on the `Order` element
- `\$input.Order.Customer` — accesses the text content of `Customer`
- `map(...)` — iterates over each `Item`, computing price times quantity
- `sum(...)` — adds up all the line totals

Run it:

```bash
cat order.xml | utlx transform order-to-json.utlx
```

Output:

```json
{
  "orderId": "ORD-001",
  "customer": "Alice Johnson",
  "total": 175.0
}
```

That's UTL-X. One transformation file. Input XML, output JSON. All the format details — XML attributes, element text, JSON types — handled automatically.

Now change `output json` to `output yaml`, and the same transformation produces:

```yaml
orderId: ORD-001
customer: Alice Johnson
total: 175.0
```

Or `output csv`:

```csv
orderId,customer,total
ORD-001,Alice Johnson,175.0
```

The transformation logic didn't change. Only the output declaration. That is the power of format-agnosticism.

== What's Ahead

This book is organized in eight parts:

- *Part I* (you are here) lays the foundation: installation, the three executables, the UDM, schema mapping, and format support
- *Part II* teaches the language: expressions, functions, pattern matching, validation, and pipelines
- *Part III* dives deep into each format: XML, JSON, CSV, YAML, OData, and schema formats
- *Part IV* covers real-world applications: enterprise integration, cloud deployment, SDKs, performance, and compliance
- *Part V* looks ahead: semantic validation, API contracts, AI-assisted generation, and why Kotlin was the right choice
- *Part VI* presents case studies from e-invoicing, healthcare, financial services, and more
- *Part VII* provides reference material: grammar, appendices, and glossary
- *Part VIII* is the complete standard library encyclopedia: all 652 functions with signatures and examples

Whether you're an integration developer replacing XSLT, a data engineer building multi-format pipelines, or an architect evaluating transformation technologies — this book will take you from first transformation to production deployment.

Let's get started.
