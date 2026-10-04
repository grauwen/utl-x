= One Language, One Model

This chapter is a short tour of UTL-X — enough to read every example in this book — and the contract that
makes the language safe to run on a boundary. For the full reference, see _UTL-X: One Language, All
Formats_.

== The shape of a transformation

Every UTL-X program has the same three-part shape: a header, a `---` separator, and a single body
expression.

```
%utlx 1.1
input  json
output json
---
{ /* one functional expression that is the transformation */ }
```

The header declares the language version and the input/output formats (with options, or a *form-class* for
binary). After `---` comes one functional expression — no statements, no mutation, no imperative loops.

== The Universal Data Model

The single most important decision in UTL-X is that *all* inputs are parsed into one internal model — the
Universal Data Model (UDM) — before any expression runs, and all outputs are serialized from it. Format
in, UDM, format out. The body never sees JSON or XML or bytes; it sees a tree.

#table(
  columns: (auto, 1fr),
  [`Scalar`], [string, number, boolean, null],
  [`Object`], [named children (a JSON object, an XML element)],
  [`Array`], [indexed children (a JSON array, repeating XML)],
  [`Binary`], [raw bytes (a leaf)],
  [`DateTime`], [first-class temporal values],
)

Because every format collapses to these few node types, a *rule* written for the UDM polices every format
on one model — the guard of Chapter 2.

== Three accessors: data, attribute, metadata

#table(
  columns: (auto, 1fr),
  [`$input.name` — `.`], [data / content — the ordinary case],
  [`$input.El.@id` — `@`], [an attribute (e.g. an XML attribute)],
  [`$input.x^crcValid` — `^`], [metadata *about* a node: provenance, decode results, the security label],
)

The `^` metadata channel is where a binary decoder records CRC validity or the raw bytes, and where a
label (STANAG 4774) rides alongside the data — present for policy, never silently promoted into the data.

== The contract — four guarantees

The guard rests on four guarantees UTL-X makes about *every* program:

#table(
  columns: (auto, 1fr),
  [*Pure*], [the same input always produces the same output — no side effects, no clock, no network],
  [*Stateless*], [no memory between messages; the thousandth maps exactly as the first],
  [*Deterministic*], [no probability, no randomness, no model weights — so a verdict is reproducible and reviewable],
  [*Single-pass*], [one input (or N correlated inputs) produces one output in bounded time and memory],
)

These are what make a UTL-X program safe to run *inline on a trust boundary* — in process, reviewable,
reproducible. A reviewer can read a transformation and know exactly what it will do with any input,
because it cannot do anything else. Chapter 7 adds validation (`validate.*`) while keeping all four
intact — and draws the line the guard depends on: the probabilistic `ai.*` of UTL-X 2.0 *breaks* purity
and determinism, and is therefore forbidden anywhere inside a guard.
