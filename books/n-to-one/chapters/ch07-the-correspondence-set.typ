= The Correspondence Set

A mapping that exists only in an analyst's head, or only as a generated transformation, cannot be reviewed, diffed, tuned, or trusted. The analysis of Chapters 4 through 6 has to be *written down* — in a form that a human can audit, a version-control system can track, and a machine can consume. That artifact is the _correspondence set_. This chapter specifies it, and in doing so makes concrete everything the formal object of Chapter 6 only defined.

== From Coverage Report to Matrix

The simplest version of the analysis answers a one-input question: for each output field, is it covered by the single source, and if not, where is the gap? Generalise this to N inputs and it becomes a *matrix* — inputs across the top, output leaves down the side, each cell recording how that input contributes to that output field, if at all.

The matrix is the analysis artifact, and it does three jobs at once. It *reveals input roles*: a column touched only as a key is a lookup; a column feeding most leaves is the driver. It *drives strategy selection*: a high proportion of one-to-one mirror cells calls for spread; a key column calls for a join; an array driver calls for `map`. And it *grounds field-level sourcing*: within whatever strategy is chosen, each output field still knows where its value comes from. The correspondence set is this matrix, made precise and given scores.

== Scores, and Why Sub-Scores Are Mandatory

Each correspondence carries a confidence. The naive choice is a single composite number, and it is a trap. A bare $0.87$ is opaque: it cannot be tuned, because there is no knob; it cannot be explained, because there is no reason; and it cannot be trusted in proportion to its basis, because the basis is invisible.

The discipline, taken from the composite matchers of Chapter 5, is to *store the sub-scores beside the composite*. A correspondence does not record $0.87$; it records that the structural signal was $0.91$, the type signal $0.95$, the name signal $0.61$, and that the match was foreign-key reachable. Now the number is legible. A high composite carried by structure and foreign-key reachability is trustworthy; the same composite carried mostly by a name match is a candidate for review. The sub-scores tell you not just *how* confident to be but *why*, and which signal to distrust if the mapping later proves wrong.

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  *Principle.* Always store sub-scores, never only the composite. `{ structural: 0.91, type: 0.95, name: 0.61 }` explains a match; `0.87` only asserts it.
]

== Provenance: The `basis` Field

Beyond *how* confident, the set records *where each correspondence came from*. A `basis` field tags the origin:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*basis*], [*Meaning*],
  [`fk-reachable`], [derived from a foreign-key path in the schema graph — high confidence, deterministic],
  [`name-heuristic`], [proposed by name similarity among structurally plausible candidates — review],
  [`ai-inferred`], [supplied by a language model for a gap deterministic rules could not close],
  [`analyst-spec`], [extracted from a human mapping specification — documented intent, but unverified against the live schema],
  [`user-confirmed`], [accepted or corrected by a human — locked],
)

Provenance is what makes human review *targeted* rather than exhaustive. A reviewer need not re-examine every foreign-key-reachable correspondence; those are structural facts. Attention goes to the `name-heuristic` and `ai-inferred` rows — the correspondences that rest on the fallible signals — which is the highest-value use of a reviewer's time. The set does not hide its uncertainty in an average; it localises it in a field.

This split has an epistemic name worth stating, because it runs through the whole book. The deterministic rows are *facts*: a `fk-reachable` correspondence is derived from the schema graph and is not open to doubt. The fallible rows are *hypotheses*: a `name-heuristic` or `ai-inferred` correspondence is a conjecture about meaning, advanced on partial evidence and awaiting a test. Read this way, `basis` records *what kind of evidence stands behind a correspondence* and `confidence` records *how strong it is* — together, the standing of a hypothesis. This is the vocabulary schema matching has always used, where a matcher emits ranked *candidate* matches, never certainties. The set's discipline is to keep facts and hypotheses apart, to test the hypotheses against structure — Chapter 5's rule that a name may *propose* a match but the graph *disposes* of it — and to mark which is which, so attention falls only where the evidence is thin. A correspondence the deterministic line cannot reach is not a defect in the analysis; it is a hypothesis the analysis is honest enough to label.

The `analyst-spec` row earns a word now and a section later. In many real integrations a human analyst has already written down the mapping — typically a free-format spreadsheet pairing source fields to target fields, with rules and lookups beside them. That document is, informally, a correspondence set authored by hand: it supplies exactly the *semantic* pairings the deterministic graph cannot recover. Such an entry sits between `ai-inferred` and `user-confirmed` in trust — a human meant it, so it outranks a model's guess, but it was written against a schema that may since have drifted, so it is not yet locked. The discipline that turns it into a usable correspondence — formalise the spreadsheet, then validate it against the live schema — is the subject of Chapter 12.

== Function and Status: The Completed Entry

A correspondence is not finished when it has a source and a score. From Chapter 6 we know that some target fields are projections and others are labelled nulls — derivations the tgds cannot fill. The entry therefore also carries the *mapping class* (the shape of the derivation — direct, array-transform, lookup-join, aggregate, and so on), the *inferred function* where one can be named, and an *mc-status*: `complete` when the field is fully accounted for, `derivation-gap` when it needs computation, `ambiguous` when several candidates compete, `unmatched` when no input can supply it.

This is what lifts the correspondence set from a coverage report to a *structural draft of the transformation*. A reviewer reading it does not see "83% covered"; they see, field by field, that this one is a direct copy, that one a `map`, that one a `findBy` against the catalogue, and that one a `sum` that no input provides and a human must confirm. The next two chapters are about computing those last columns; the set is where they are recorded.

== The Serialized Form

The set is persisted as JSON — git-versioned, diffable, dependency-free. Against the alternatives (ontology-alignment formats, tool-internal XML, raw dependency notation), plain JSON wins on exactly the properties that matter for an artifact that lives beside source code: score changes between schema versions show up in a diff; sub-scores and `basis` travel with each entry; and no external toolchain is required to read it.

Two structural features handle the N-input case directly. Each correspondence names its `src_input` and a `tgd_class` — `primary`, `enrichment`, or `lookup` — so the three kinds of dependency from Chapter 6 remain distinguishable in the data. And the name–value inputs do not appear among the correspondences at all; they live in a separate `bindings` array, each a direct value injection with a key and no score fields, because a constant has no structural confidence to report. The shape of the artifact mirrors the shape of the theory: scored correspondences for the structurally-rich inputs, unscored bindings for the constants.

== One Artifact, Two Consumers

The correspondence set is deliberately *dual-purpose*, and recognising this shapes its design. It is a persistence format — the durable, reviewable record of a mapping's analysis. It is also an *interchange* format: the structured context handed to a language model when generation is needed. An AI asked to write the transformation does not receive raw schema pairs and an open instruction; it receives pre-classified inputs, pre-computed correspondences with their mapping classes, and explicitly flagged derivation gaps. Its task is thereby *scoped*: fill the gaps, resolve the ambiguities, and leave the structural skeleton alone. Structured context with targeted gaps is a fundamentally stronger prompt than an open-ended schema pair, and the correspondence set is what makes that prompt possible.

The same artifact also drives the visual mapper: each entry becomes a connector with a function badge, a status colour, and a confidence shade keyed to its `basis`. Persistence, machine interchange, and human visualisation are three readings of one object — which is the strongest possible argument that the analysis of Part II was worth formalising. The set is where the theory becomes something you can hold.
