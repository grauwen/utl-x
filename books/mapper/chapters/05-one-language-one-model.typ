= One Language, One Model

Everything the Mapper does rests on two foundations: a small declarative language, and one internal
model every format flows through. This chapter introduces both — enough to read every mapping in the
book.

== The shape of a mapping

A UTL-X mapping is a document with two parts: a *header* declaring the version and the input/output
formats, and a *body* describing the output as an expression over the input.

```
%utlx 1.1
input  ais
output cot
---
{
  event: {
    "@uid":   $input.mmsi,
    "@type":  "a-f-S",          // a surface track
    point: { "@lat": $input.lat, "@lon": $input.lon }
  }
}
```

The header's `%utlx 1.1` fixes the language version; `input`/`output` name the formats (resolved to
readers and writers, including BINF packs — Chapter 6). The body is a single expression: no
statements, no mutation, no control flow in the imperative sense — you write *what the output is*,
not *how to build it step by step*.

== The four guarantees

UTL-X 1.0 holds a mapping to four properties, and the Mapper's assurance story depends on all four:

#table(
  columns: (auto, 1fr),
  [*Pure*], [a mapping has no side effects — no file, no network, no clock; output depends only on input],
  [*Stateless*], [no memory between messages; the thousandth message maps exactly as the first],
  [*Deterministic*], [the same input always produces the same output — so a mapping can be tested exhaustively and its result reproduced in audit],
  [*Single-pass*], [a mapping reads its input once, in one pass — bounded time and memory, suitable for line-rate and for a hardware fast-path],
)

Together these make a mapping a *mathematical function*: safe to run inline on a connection, safe to
certify, and impossible to turn into a covert channel. UTL-X 1.1 adds validation (Chapter 7) without
breaking any of them.

== The core constructs

The body language is small. These are the constructs that appear throughout the book:

#table(
  columns: (auto, 1fr),
  [`let`], [bind a name to a sub-expression for reuse and readability],
  [`map` / `filter`], [transform and select over arrays — the workhorses of record-to-record mapping],
  [`|>`], [the pipe: feed a value through a sequence of transformations left to right],
  [`match`], [branch on shape or value — e.g. dispatch on an ADS-B type code or an ASTERIX category],
  [`??`], [the default operator: supply a fallback when a value is absent],
  [`reduce`], [fold an array to a single value — totals, concatenations, aggregates],
)

```
%utlx 1.1
input  asterix
output json
---
{
  tracks: $input.records
    |> filter(r => r.category == 48)        // radar tracks only
    |> map(r => {
         id:  r.trackNumber,
         alt: r.flightLevel ?? 0,           // default when absent
         kind: match r.type {
           0 => "unknown",
           1 => "primary",
           2 => "secondary"
         }
       })
}
```

== The Universal Data Model

Every format, once read, becomes a *UDM tree* — one model with a handful of node kinds. The mapping
body never touches JSON or bits or EDI segments; it only ever navigates a UDM tree. That is the
whole trick behind "every format, one rule set."

#table(
  columns: (auto, 1fr),
  [*Scalar*], [a string, number, boolean, or null — a leaf value],
  [*Object*], [an ordered set of named children],
  [*Array*], [an ordered sequence of nodes],
  [*Binary*], [a raw byte payload (e.g. an opaque sub-field) carried without interpretation],
  [*DateTime*], [a first-class instant, so date logic is uniform across formats],
)

== Three accessors

UDM nodes carry three kinds of information, each with its own accessor — a distinction that matters
enormously for sensitive data, where *about-the-data* (the provenance, the source) is as important
as the data:

#table(
  columns: (auto, 1fr),
  [`.` — data], [the value itself: `$input.lat`, `track.id`],
  [`@` — attribute], [structural attributes native to the format: an XML attribute, a CoT `@type`],
  [`^` — metadata], [out-of-band facts *about* a node: provenance, confidence, and the security label that drives releasability (Chapter 7)],
)

The `^` metadata channel is how a classification caveat, a source sensor, or a confidence score
rides alongside a value through the whole mapping — present for validation and policy, never
silently promoted into the data. With the language and the model in hand, Chapter 6 shows how even a
bit-packed binary frame becomes one of these trees.
