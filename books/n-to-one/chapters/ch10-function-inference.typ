= Function Inference

To know that an output field maps from a source is half of an answer. The other half is *which UTLX construct* realises the mapping — a field reference, a `map`, a `findBy`, a `sum`. Function inference supplies that half, and the argument of this chapter is that it is not a separate layer bolted onto the analysis but the *completion* of it. A correspondence without an inferred function is an unfinished Message Contract result. The schema graph, read correctly, names most of these functions on its own; where it cannot, it says so.

== The Mapping Class Is the Unit

The carrier of the inference is the *mapping class* introduced in Chapter 7. Each correspondence is assigned one, and each class is bound to a UTLX construct and a completion status:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Mapping class*], [*UTLX*], [*Status*],
  [direct], [field reference], [complete],
  [array-transform], [`map` / `mapBy`], [complete],
  [lookup-join], [`findBy` / `filterBy`], [complete],
  [spread-merge], [`...spread`], [complete],
  [grouped-transform], [`groupBy` + `mapBy`], [complete],
  [sorted-transform], [`orderBy` + `map`], [complete],
  [derivation-gap], [`sumBy` / `reduce` / arithmetic], [incomplete — needs computation],
  [unmatched], [—], [incomplete — needs a human],
  [ambiguous], [—], [incomplete — needs review],
)

The mapping class is the synthesis promised by the last chapter: the product of the output-field kind (target-driven) and the source availability (from alignment). An output-field kind of "aggregate" with a source collection present yields `grouped-transform` or a `derivation-gap`, depending on whether a grouping key exists; a kind of "lookup-required" with the lookup input present yields `lookup-join`; a kind of "copy" with a type-compatible source yields `direct`. The class is where the two halves of the analysis meet.

== Inference From Graph Signals

The remarkable thing — and the reason inference belongs *inside* Message Contract mode rather than in some later AI stage — is how much of it is *deterministic from the schema graph*. The graph's structural signals map almost directly onto function classes:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Graph signal*], [*Inferred function*],
  [a `lookup`-typed input reached by a foreign-key edge], [`findBy` / `filterBy`],
  [a one-to-many containment, source to target], [`map` / `mapBy`],
  [an N-to-M junction entity], [`groupBy` + `mapBy`],
  [a one-to-many collapsing to a one-to-one scalar], [aggregation — a derivation gap],
  [several inputs into one target object], [`...spread`],
  [a one-to-many feeding an *ordered* one-to-many], [`orderBy` + `map`],
  [a required target field with no source and a numeric context], [a derivation gap — computation],
  [a required target field with no source and no numeric context], [unmatched — flag for a human],
)

None of these is a guess. A foreign-key edge into a lookup *is* a join; a one-to-many containment *is* a `map`. These are structural facts of the graph, read off it the way Chapter 4 read off the header–detail pattern. The schema typology of Chapter 8 and the graph of Chapter 4 together have strong predictive power over which construct each correspondence needs — strong enough that the deterministic core of the analysis can name the function for the great majority of fields without consulting an oracle.

== The tgd Boundary, Revisited

Function inference is also where the boundary of Chapter 6 becomes operational. The functions above the line in that chapter's table — references, spreads, maps, keyed lookups — are tgd-expressible, and the graph derives them deterministically. The functions below the line — `groupBy`, `sumBy`, `reduce`, derived arithmetic, `orderBy` — are *not* tgd-expressible; they compute or reorder rather than move and reshape. For these, inference can identify *that* a computation is needed and *what kind* (a sum over this collection, a count of those rows) but cannot, from the graph alone, author the exact expression. They are marked `derivation-gap`, with a description of what is required, and handed onward — to a human, or to the program-synthesis step of Chapter 9's closing.

This is the honest version of the analysis. It does not pretend the graph determines everything; it determines the skeleton, and it labels precisely where the skeleton ends and computation begins. A `derivation-gap` is not a failure of inference — it is inference correctly reporting the limit of what structure can decide.

== The Deterministic / AI Split

The full pipeline is a hybrid, and the value of function inference is that it keeps the hybrid *clean*: deterministic and AI work are separated, not blended, with AI invoked as a targeted enrichment at specific, named points rather than as a general overlay.

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Phase*], [*Deterministic or AI*],
  [schema typing], [mostly deterministic; AI only at the chain/payload boundary],
  [graph construction], [fully deterministic — pure parsing of declared constraints],
  [structural alignment], [mostly deterministic; AI for implicit foreign keys, choice groups, cardinality intent],
  [name refinement], [AI-primary — the semantic bridging no metric achieves],
)

Graph construction must remain fully deterministic: it is the stable foundation everything else depends on, and there is nothing in parsing declared constraints for an oracle to improve. Structural alignment is mostly deterministic, with AI earning its place only at the genuine ambiguities — an implicit foreign key with no declared `keyref`, an `xs:choice` whose intended branch is a semantic judgement, a cardinality mismatch that might be a deliberate flattening or an error. Name refinement is where AI leads, because it is the one phase whose core problem — bridging `customerId` to `clientRef`, or one language to another — is irreducibly semantic.

Every inference, deterministic or otherwise, records its basis. A function derived from a foreign-key edge is marked `fk-reachable`; one proposed by a model for an implicit key is marked `ai-inferred`. The provenance discipline of Chapter 7 thus extends to the inferred functions themselves: a reviewer can see not only which construct was chosen but on what authority, and can spend their scrutiny on the constructs that rest on inference rather than on structural fact.

== Completing the Message Contract Report

The chapter's thesis — that function inference completes the Message Contract analysis rather than following it — has a concrete consequence for what the report *is*. Without inference, a Message Contract report is a coverage percentage and a list of matched fields: a developer reading it must still decide, field by field, what construct each match implies, re-deriving the analysis by hand. With inference, the report is a *first draft of the transformation's structure*: every output field carries not only its source but the construct that realises it, and every gap is labelled with the computation it awaits.

That is the efficiency argument for folding inference into Message Contract mode rather than treating it as a separate stage. The developer no longer translates a coverage report into a transformation; they correct and refine a structural draft. The analysis has done the part that is mechanical — the skeleton the graph determines — and reserved human attention for the part that is genuinely a decision. Generating the transformation from that draft, strategy-first, is the final step, and the subject of the last chapter.
