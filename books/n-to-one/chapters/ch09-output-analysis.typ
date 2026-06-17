= Output Analysis

Typing the inputs has a dual that is easy to overlook, and overlooking it is the most common way an N:1 analysis lies. The output contract is not a passive target that correspondences merely point at; it is an object to be *analysed in its own right*. Each output field has an intrinsic kind — copied, calculated, looked-up, aggregated, generated — and much of that kind can be read from the output schema *before any input is consulted*. This chapter develops target-field analysis and grounds it in the literature of data provenance.

== The False-Coverage Problem

Begin with a report that looks reassuring and is wrong:

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  `source: order.lines[*].price` $arrow.r$ `target: invoice.orderTotal` — *matched*.
]

This is not a match. The order total is not the price of a line; it is the sum, over all lines, of quantity times price. The report has dressed a *derivation gap* as a correspondence, and in doing so has told two lies: it has claimed a field is sourced when it must be computed, and it has inflated the coverage percentage with a field that is not, in fact, covered. A mapping analysis that does this gives false confidence exactly where confidence is most expensive.

The same failure has a quieter form. `order.lines[*]` $arrow.r$ `invoice.lineItems[*]` is a genuine correspondence, but reporting it as "matched" omits that it *implies a `map`* — and, if the target is ordered, an `orderBy` before the `map`. That the lines become line items is a schema-level fact; that the transformation needs a `map` to make it so is also a schema-level fact. It belongs in the analysis, not in the reader's head.

The root cause is the value invention of Chapter 6. A target field that is a labelled null — a value that must exist but is copied from nothing — cannot be honestly reported as a match. It must be reported as what it is: a field that needs computation, lookup, generation, or a constant.

== A Taxonomy of Target Fields

Give each output field a kind, read mostly from the output schema's own names, types, constraints, and structural context:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Kind*], [*Signal (mostly schema-local)*], [*Realised by*],
  [copy / direct], [a scalar with a type-compatible source], [a field reference],
  [calculated / derived], [a composite or formatted field (`fullName`, `displayDate`)], [`concat`, formatting, arithmetic],
  [aggregate], [a numeric over a sibling repeating group (`total`, `count`)], [`sumBy`, `reduce`, `countBy`],
  [lookup-required], [a descriptive field beside a code, with a lookup input present], [`findBy`, `filterBy`],
  [constant / literal], [required, no candidate, fixed or enumerated], [a literal, or a config binding],
  [generated / system], [`id`, `uuid`, `timestamp`, `correlationId`], [a generator, or a pipeline-var binding],
  [conditional], [a flag or status that depends on a condition], [`if`, `match`],
  [default / optional], [optional, with a default], [omission or a default],
  [unmapped], [required, with no plausible source or derivation], [— a true gap],
)

The value of this taxonomy is that it is *target-driven*. A numeric field named `total` sitting beside a collection of lines all but announces itself as an aggregate, with no input examined at all. A descriptive field named `productName` beside a `productCode`, in a mapping that has a lookup input, announces itself as a join. The output schema is not silent about how its fields must be produced; it is, read carefully, remarkably talkative.

== Schema-Local Versus Source-Dependent

How much of a field's kind is decidable from the output alone, and how much waits on the inputs? The division is clean.

The *kind* is largely schema-local: name, type, constraint, and sibling cardinality decide whether a field is a copy-candidate, an aggregate, a lookup, a constant, or a generated value. What the source decides is narrower: whether a copy-candidate actually *has* a source (copy) or does not (gap); whether the lookup input the field wants is actually present. The output analysis runs first, at contract-load, with no inputs; alignment then resolves the open question of source availability. The composition is exact:

$ "mapping class" = "output-field kind" times "source availability" $

The mapping class of Chapter 7 — the entry that says "this is a `map`, that is a `findBy`, this is a `sum` no input provides" — is the *product* of the target-field analysis and the source-side correspondences. The `derivation-gap` that Chapter 6 first named is simply the cell where the output-field kind is "aggregate" or "calculated" and no projection can fill it. Output analysis is the target-driven half of function inference; the next chapter is the synthesis.

A small but genuine role remains for AI here. Semantic field names — does `netDue` mean an aggregate? does `clientRef` mean a lookup? — sometimes exceed what name-and-type rules can decide, and a language model can classify them where deterministic rules cannot. Such classifications are marked, as always, by their basis, so a reviewer knows which target kinds were read from structure and which were guessed.

== Constraint Soundness: A Second Completeness

The taxonomy reads a field's *constraints* to help decide its kind, but a constraint carries an obligation that outlives the typing. A target field is not only a name and a type; it is a name, a type, and a *bound* — a `maxLength`, a numeric range, an enumeration, a pattern — and the derivation chosen to fill it must produce a value the bound admits. This opens a second axis of completeness, orthogonal to the one Chapter 1 defined.

Consider an output `address` constrained to three hundred characters, filled by concatenating house number, street, city, postal code, and state. The field is *sourced* — coverage is satisfied, and the analysis would report it covered — yet a real Indonesian address, concatenated, runs well past three hundred. The mapping is *coverage-complete* and *constraint-unsound* at once. Coverage asks "is every field sourced?"; soundness asks "does every sourced value satisfy the field's bound?" — and the two are independent. A report that tracks only the first dresses an overflow-in-waiting as a clean match: the false coverage of this chapter in a subtler key.

This is the schema-complete-but-instance-incomplete gap of Chapter 3 made concrete — and, unusually, made *partly decidable in advance*. Where the inputs carry bounds of their own, the output's constraint can be *propagated backward* through the derivation. A concatenation's worst-case length is the sum of its parts' maximum lengths plus the separators; compare that against the target's bound and three verdicts follow:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Verdict*], [*Meaning*],
  [provably safe], [the parts' bounds sum within the target's — a static guarantee, no action needed],
  [provably unsafe], [even modest inputs exceed it — the mapping is wrong, flagged before any data flows],
  [unprovable], [a part is unbounded, or the worst case exceeds the bound while real data usually fits — a constraint *risk* to surface],
)

The lesson is the one types already teach. A type system catches some run-time errors at compile time; a *constraint* — a refinement on a type — catches some instance-adequacy failures at design time. This does not erase the mode boundary of Chapter 3; it shows where the metadata lets design-time analysis reach further into what looked like a purely run-time question. Nor is the obligation foreign to the formal object: the target schema $T$ of Chapter 6 carries its constraints, and "does the mapping satisfy the constraints of $T$?" is a question the formalism was always able to ask.

What the analysis does with the verdict is the discipline of the rest of the book. A provable violation is flagged like a derivation gap — surfaced, never silently truncated nor silently overflowed. The remedy is a *decision*, authored by a human or a model and recorded, not defaulted by the engine: truncate explicitly, validate and reject, widen the contract, or accept a rare failure — each a different intent, none safe to guess. And where the verdict is *unprovable*, the obligation does not vanish; it becomes a run-time conformance check, the output validated against the contract's bounds at execution and a violating instance rejected rather than passed downstream.

Two honest limits bound the static half. It is only as strong as the inputs' declared bounds — an unbounded source string yields no guarantee, only a flag, which is one more reason bounds belong in the schema and not in a guess. And the worst case is conservative: it reports a *possibility* of overflow, not a certainty, since most addresses fit and only the exceptional one does not. Length and numeric range propagate cleanly; pattern and regular-expression constraints rarely do, and are better left to the run-time check. The aim is not to prove every constraint statically but to prove the ones that can be, surface the ones that cannot, and let neither pass as a silent match.

== Grounding: Provenance, ETL, and Synthesis

The question "how is this output value produced?" is not new. Four bodies of work answer it, and together they ground the taxonomy.

*Data provenance and lineage* is the formal study of exactly this. How-provenance — the semiring account of how an output value is computed from inputs — and the lineage-tracing literature classify transformations by the output-to-input relationship: a value dispatched unchanged, a value aggregated, a value produced by an opaque computation. The copy / aggregate / calculated distinction is the provenance distinction, recovered from the mapping side.

*ETL practice* is the taxonomy's nearest practical cousin. Decades of data-warehouse engineering classify target columns into precisely these kinds — direct, derived-computed, surrogate-key (generated), lookup-decode, default — and the functoid and component catalogues of mapping tools (constant, autonumber, lookup, aggregate, arithmetic, string, date) are target-field-derivation taxonomies in everything but name. The output taxonomy is what those catalogues have always implied.

*Data-exchange value invention* supplies the formal boundary, already met in Chapter 6: the labelled null is the copy-versus-derive line, and the derived kinds — calculated, aggregate, generated — are exactly the target values that data-exchange theory declares beyond a tgd.

*Program synthesis* is the natural successor. Once a field is typed `calculated`, the remaining task is to *synthesise its expression*, and programming-by-example — generating a transformation from input–output samples — is the literature for that step. Typing the field is the analysis; synthesising its formula is the generation.

== Synthesising a Sample Output

Output analysis also answers a practical temptation flagged back in Chapter 3. A developer often wants a *sample* output document — something concrete to inspect. The wrong way to get one is to switch to Execution mode, run the transform on a sample input, and capture the result; that quietly crosses the mode boundary and obtains the output by execution.

The right way stays in Message Contract mode: *synthesise* a representative instance from the output *contract*. The schema describes the shape; a generator can populate it with representative values. This produces an output sample with no transformation run at all — schema-driven synthesis, not execution. It is the output-side mirror of the principle that has governed the whole book: in Message Contract mode, reason from shape, and let data be how you check the result later, in the other mode.

== What Output Analysis Buys

Folding output analysis into the Message Contract pass does not make the report more complex to read; it makes it more useful. Without it, a developer receives a list of field matches and must, for every field, re-derive in their head whether it is a copy, a sum, or a join — re-doing by hand the analysis the tool declined to do. With it, the report is a *structural draft of the transformation*: each output field already labelled with how it must be produced. The developer corrects and refines a draft rather than starting from a blank page. That is the efficiency the whole pipeline is ultimately after, and it begins by taking the output as seriously as the input.
