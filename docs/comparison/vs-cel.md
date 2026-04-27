# UTL-X vs CEL (Common Expression Language)

> **TL;DR** — CEL decides things *about* data. UTL-X reshapes data. They look similar in a five-line snippet and diverge completely the moment you do real work. CEL is a sandboxed predicate evaluator embedded in policy engines (Kubernetes admission, IAM, Envoy, Firebase). UTL-X is a transformation language that owns the full pipeline: parse, restructure, emit — across XML, JSON, CSV, YAML, OData, and back. They are not competitors. They are neighbors solving different problems, and this page is mostly about making that boundary clear so you pick the right tool.

---

## Quick comparison

| Dimension | CEL | UTL-X |
|---|---|---|
| **Primary purpose** | Evaluate a predicate or compute a small value | Transform a document into another document |
| **Origin** | Google, ~2018 | Glomidco B.V., 2025 |
| **Paradigm** | Expression language (non-Turing-complete) | Functional, declarative (Turing-complete) |
| **Input model** | Protobuf-shaped structured data | XML, JSON, CSV, YAML, OData, EDMX |
| **Output** | A single value (bool, number, string, list, map) | A serialized document in any supported format |
| **Format-aware** (attributes, namespaces, mixed content) | No | Yes — that's the design center |
| **Side effects / I/O** | Forbidden by design | Reads/writes streams, files, stdin/stdout |
| **Termination** | Guaranteed (linear time, bounded) | Not guaranteed (full language) |
| **Schema transformation** (XSD ↔ JSON Schema ↔ Avro ↔ Protobuf ↔ OData) | Out of scope | First-class |
| **Embedding model** | Library inside a host program | CLI + scriptable + embeddable |
| **Type system** | Static, gradual, protobuf-derived | Static, format-agnostic, compile-time checked |
| **Ecosystem position** | Sandboxed expression layer for hosts | Standalone transformation tool |
| **Hot path** | Microseconds, called millions of times/sec | Per-document transformation |
| **Where you find it** | Kubernetes CRDs, IAM conditions, Envoy RBAC, Cloud Armor, Firebase rules | Integration pipelines, ETL, schema migration, format conversion |
| **License** | Apache 2.0 | AGPL-3.0 (dual license available) |

---

## Where they overlap

The overlap is narrow but real: **filter expressions and field projections over a single input.** This is the slice that creates the confusion.

```
# CEL
orders.filter(o, o.status == "active").map(o, o.id)
```

```utlx
# UTL-X (-e mode, inline)
.orders |> filter(o => o.status == "active") |> map(o => o.id)
```

In this slice they *do* look interchangeable. Both are functional, both have `filter`/`map`, both produce a list of IDs. If you only ever do this, the choice between them is essentially aesthetic — and you should pick CEL, because it's smaller and has broader policy-engine integration.

The divergence shows up the instant the work expands beyond "look at one input, return a value."

---

## Where they diverge

### 1. Format awareness

CEL has no notion of XML attributes, namespaces, mixed content, CDATA sections, comments, processing instructions, or document order. Its world is protobuf — or, by extension, JSON-shaped data that maps cleanly onto protobuf. Hand CEL an XML document and you first have to lower it into some host-defined map structure, losing every distinction the XML model carries.

UTL-X is built around the format problem. `.Order.@id` accesses an XML attribute. `.Order.Item[0]` works on XML, JSON, and YAML alike. The same script can read XML and emit JSON by changing one line of the header.

```utlx
%utlx 1.0
input xml
output json
---
{
  customers: $input.Orders.Order
    |> filter(o => o.@status == "active")
    |> map(o => { id: o.@id, total: parseNumber(o.Total) })
    |> sortBy(o => -o.total)
}
```

Change `input xml` to `input json` and the script keeps working. CEL has no equivalent — there is nothing in CEL to change because CEL never had a parser to begin with.

### 2. Output

CEL returns a value. UTL-X emits a document.

This sounds like a small distinction. It isn't. "Returning a value" means CEL is always a sub-expression inside something else — a host program decides what to do with the boolean. "Emitting a document" means UTL-X is the program; it produces stdout, a file, a stream that some downstream consumer ingests. The two languages sit on opposite sides of the same boundary.

### 3. Termination guarantees

CEL is **non-Turing-complete by design**. It evaluates in linear time relative to expression and input size. There is no recursion, no general loops — only bounded macros (`all`, `exists`, `filter`, `map`). This is what lets Google embed CEL in the Kubernetes API server and IAM: no expression a user writes can hang the control plane.

UTL-X is a full transformation language. It has user-defined functions, recursion, and unbounded iteration. You *can* write a UTL-X script that loops forever. In an integration pipeline this is fine — you control the inputs and timeouts. Inside an admission controller it would be a disaster.

If you're choosing between them and the work will run inside a host that calls the expression millions of times per second on data it already has in memory, CEL is correct. If the work is the data movement itself, UTL-X is correct.

### 4. Side effects

CEL forbids them. There is no I/O, no clock-reading except via host-injected variables, no randomness, no logging. Every CEL evaluation is a pure function from inputs to output.

UTL-X reads stdin, writes stdout, reads files, and integrates with pipelines (Kafka, HTTP, file watchers). It is a working tool, not an embedded predicate evaluator.

### 5. Schema transformation

This is the largest gap. UTL-X v1.0 ships schema-to-schema transformation: XSD ↔ JSON Schema ↔ Avro ↔ Protobuf ↔ OData/EDMX. Take an XSD, get a JSON Schema. Take an OData EDMX, get a Protobuf definition. CEL doesn't address this problem at all — schema transformation isn't expression evaluation, it's compilation.

### 6. The CLI

CEL is a library. You embed it in Go, Java, C++, or Python; you give it a context; you evaluate expressions. There is a `cel` CLI for testing, but no one's pipeline runs through it.

UTL-X is a CLI first. `cat data.xml | utlx` does an instant conversion. `utlx -e '.users |> filter(u => u.active)'` is a one-liner. The command-line ergonomics are deliberately jq-shaped because that's where transformation actually lives in most engineers' day.

---

## When to use CEL

CEL is the right choice when **all** of the following are true:

- The expression evaluates a **decision** — boolean, predicate, score — not a transformation
- The host system already has the data in memory in a structured form
- The expression will be called extremely often (often per-request, per-row, per-event)
- You need **guaranteed termination** because untrusted users may write the expression
- You need cross-language portability (Go, Java, C++, Python implementations exist)
- The integration target is something CEL already plugs into: Kubernetes, IAM, Envoy, Cloud Armor, Firebase, gRPC authz

Canonical CEL use cases:

```cel
// Kubernetes ValidatingAdmissionPolicy
object.spec.replicas <= 5

// IAM condition
request.time < timestamp("2026-12-31T00:00:00Z") &&
  resource.name.startsWith("projects/_/buckets/public-")

// Envoy RBAC
request.headers["x-api-key"] == "secret" &&
  source.address.ip in allowed_cidrs
```

These are not transformations. They're decisions. CEL is excellent at them and UTL-X would be the wrong tool.

---

## When to use UTL-X

UTL-X is the right choice when:

- The work **is** the data movement — input format → output format
- The input or output is XML, CSV, YAML, or OData (where CEL has no useful model)
- You need to filter, map, join, aggregate, restructure, or pivot a document
- You need schema-to-schema migration (XSD → JSON Schema, etc.)
- You need a CLI tool for ETL, integration, or pipeline work
- You'd otherwise reach for jq + a separate XSLT script + a custom CSV parser

Canonical UTL-X use cases:

```utlx
%utlx 1.0
input xml
output json
---
{
  invoices: $input.Invoices.Invoice
    |> filter(i => parseNumber(i.Total) > 1000)
    |> groupBy(i => i.Customer.@id)
    |> map((customerId, invs) => {
        customer: customerId,
        count: count(invs),
        total: sum(invs |> map(i => parseNumber(i.Total)))
      })
}
```

There is no realistic way to do this in CEL. There's no XML parser, no aggregation idiom, no document emitter, and no place to put the result.

---

## The honest mental model

Think of CEL as an **intentionally constrained** language. Every feature it doesn't have — recursion, unbounded loops, I/O, format parsing — was left out on purpose. The constraint *is* the value proposition. CEL exists so a host application can let users write rules without those rules being able to break the host. The slogan "small, fast, safe" is not marketing; it's the entire design contract.

Think of UTL-X as an **intentionally expansive** language. Every format it adds, every emitter, every schema dialect is an explicit broadening of scope. The goal is to absorb the variation across XML, JSON, CSV, YAML, OData so engineers stop maintaining four toolchains for one logical job. The slogan "write once, run on any format" is the entire design contract.

These two contracts don't compete. They describe different needs and the right answer to "which should I use" comes from which contract fits the job in front of you.

---

## Could one replace the other?

**Could UTL-X replace CEL?** Technically yes — UTL-X is a strict superset in language power. Practically no. Embedding a full transformation engine inside a hot-path admission controller that has to evaluate ten thousand policies per second on protobuf messages already in memory would be wasteful at best and unsafe at worst. You'd be giving up the termination guarantee that justifies CEL's existence. Wrong trade.

**Could CEL replace UTL-X?** No. CEL has no XML parser, no namespace model, no attribute concept, no document emitter, no schema-to-schema compiler, no streaming, no CLI ergonomics, and no escape hatch when the job genuinely needs Turing completeness (recursive aggregations, custom format adapters, complex joins). The constraints that make CEL good at policy make it unsuitable for transformation.

The correct answer is to use both, in different parts of the system:

- **UTL-X** at the integration layer — moving data between systems, normalizing formats, migrating schemas
- **CEL** at the policy layer — deciding which transformations are allowed, gating access, enforcing guardrails

A real-world example: a UTL-X pipeline transforms inbound XML invoices into the canonical JSON shape your system uses. The Kubernetes admission controller running that pipeline has a CEL ValidatingAdmissionPolicy that requires every Job to set CPU limits below a threshold. Different jobs, different tools, no overlap.

---

## Side-by-side: the same intent in both languages

The deceptive part is how similar a small filter looks in both.

**Goal: list IDs of active orders over $1,000.**

```cel
// CEL — assumes 'orders' is a list of protobuf messages already in scope
orders
  .filter(o, o.status == "active" && o.total > 1000)
  .map(o, o.id)
```

```utlx
# UTL-X — full script, reading XML from stdin, emitting JSON to stdout
%utlx 1.0
input xml
output json
---
$input.Orders.Order
  |> filter(o => o.@status == "active" && parseNumber(o.Total) > 1000)
  |> map(o => o.@id)
```

The CEL version is a one-liner because everything around it — the host, the data binding, the output handling — is already provided by the embedding system (Kubernetes, IAM, etc.). The UTL-X version is a complete program because it *is* the program.

This is the cleanest visual signal of the boundary. If your environment already sets the table for you and you just need to write the rule, you want CEL. If you have to set the table yourself, you want UTL-X.

---

## At a glance

| Question | Answer |
|---|---|
| "I need to validate Kubernetes resources" | **CEL** |
| "I need to transform XML invoices to JSON" | **UTL-X** |
| "I need to filter a list of objects in an IAM rule" | **CEL** |
| "I need to convert XSD to JSON Schema" | **UTL-X** |
| "I need a per-request authorization check" | **CEL** |
| "I need a CLI tool I can `cat \| utlx`" | **UTL-X** |
| "I need predicate eval that can't hang the API server" | **CEL** |
| "I need to migrate schemas across an integration" | **UTL-X** |
| "I need to express a routing rule in Envoy" | **CEL** |
| "I need format-agnostic ETL" | **UTL-X** |

---

## See also

- [vs jq](./vs-jq.md) — the closer comparison, on the JSON-tooling axis
- [vs XSLT](./vs-xslt.md) — the historical predecessor for XML transformation
- [vs DataWeave](./vs-dataweave.md) — the closest commercial peer
- [CEL specification](https://github.com/google/cel-spec) — official Google specification
- [cel-go](https://github.com/google/cel-go) — reference implementation in Go

---

*Last updated: April 2026 — UTL-X v1.0.1*
