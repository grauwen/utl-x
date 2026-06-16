= Two Modes: Execution and Message Contract

The previous chapter ended on a claim with large consequences: because UDM separates shape from data, a mapping can be reasoned about before any data exists. UTLX turns that claim into an architectural fact by running in two distinct modes. _Execution mode_ transforms real data. _Message Contract mode_ reasons about schemas. They are not two views of one engine; they are two analyses with different inputs, different questions, and different notions of correctness. Conflating them is the single most common way to get confident, wrong answers about a mapping. This chapter keeps them apart.

== The Distinction in One Sentence

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  *Execution mode* asks: _given this concrete input, what output does the transformation produce?_ \
  *Message Contract mode* asks: _given these schemas, is a correct mapping structurally possible, and what does it consist of?_
]

Execution mode operates on the *value* reading of UDM (Chapter 2): instances, real bytes, actual line items. Message Contract mode operates on the *shape* reading: schema graphs, with values abstracted away. One runs the function; the other studies it.

== Why the Two Cannot Be Merged

It is tempting to treat Message Contract mode as "Execution mode without data" — run the transform on a sample and inspect the result. That shortcut is wrong, and the reason is foundational, not incidental.

A mapping can be *schema-complete but instance-incomplete*: structurally every output field has a source, yet on a particular input a lookup finds no matching row and a required field comes out empty. And a mapping can be *instance-adequate but schema-unsound*: it happens to produce valid output for the three samples you tried, while violating the contract for inputs you have not seen. These two failure modes are independent. Neither analysis can detect the other's failures, because they are asking different questions of different objects.

This is not a UTLX peculiarity. It is the oldest distinction in the schema-matching literature — Rahm and Bernstein's _schema-only versus instance-based_ axis — and it is older still in logic, as the difference between a property that holds by form and one that holds in a particular model. The practical upshot is firm: Message Contract analysis must be *schema-based*, computed from the schema graphs and their constraints; it must not smuggle in evidence from a sample instance and call the result a proof.

== What Each Mode Sees

The two modes do not merely ask different questions; they are handed different things to begin with.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [], [*Execution mode*], [*Message Contract mode*],
  [Reading of UDM], [value (instance)], [shape (schema graph)],
  [Inputs present], [real data per message], [the input schemas; sample instances are optional],
  [The output], [a produced instance], [the output contract (a schema); no instance is produced],
  [Runs the transform?], [yes — real execution], [no — analysis only],
  [Correctness asked], [adequacy on this data], [completeness against the contract],
  [Cost model], [per message, on the hot path], [once, when schemas load or change],
)

Two rows deserve emphasis because they are routinely misread.

#strong[Message Contract mode is not "no instances."] An input in Message Contract mode commonly carries a *sample* instance alongside its schema — a representative document, useful for preview and for inferring structure when a formal schema is thin. What Message Contract mode lacks is *execution*: it does not run the transformation to produce output data. The presence of sample data and the act of executing are different things, and only the latter is absent. A sample is evidence about shape; it is never the basis of a completeness claim.

#strong[The output sides differ sharply.] In Execution mode the output is a produced instance — the result of running the function. In Message Contract mode there is no produced output instance, only the output *contract*. If a sample output document is wanted, it should be *synthesised from the contract* — generated from the schema — not obtained by executing the transform. The instinct to "just execute to see the output" quietly crosses the mode boundary and imports instance evidence into a schema question.

== Three Times: Design, Init, and Run

The two modes line up with the phases of a system's life — but the run-time side has *two* phases, not one, and naming the middle phase repays the effort. There are two modes, and three times.

Message Contract mode is *design time*. Its analyses — classifying inputs, building schema graphs, finding correspondences, reporting coverage — happen while a developer authors or validates a mapping, when schemas are loaded or changed. They may be expensive; they run rarely, and never while messages flow.

Execution mode spans the other two times. *Init time* is the moment a transformation is deployed and loaded: the engine compiles the script once, establishes the pipeline's input slots, binds the fixed configuration constants, and loads and indexes any static lookup tables — all before a single message arrives. *Run time* is per message, on the hot path: parse the input data into the model, execute, serialise.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Time*], [*When*], [*Work done*],
  [design], [authoring / validation], [typing, matching, the correspondence set — computed and recorded],
  [init], [deploy / load, once], [compile the script, set up slots, bind config, index static lookups],
  [run], [per message], [parse data into the model, execute, serialise — the hot path],
)

Two consequences make the third phase worth its name.

#strong[It grounds the `config` / `nvp` split.] Chapter 8 attributes the difference between configuration and run-time variables to *lifecycle* rather than structure — and the init/run boundary is exactly that lifecycle. A `config` value is fixed at init time and may be inlined into the compiled transformation; an `nvp` value varies per message and is read at run time. Identical flat shape; different *time*. The temporal model is what makes "lifecycle, not structure" precise.

#strong[It sharpens the hot-path discipline into three verbs.] "No schema analysis on the hot path" becomes: a schema fact is *computed* at design time, *loaded* at init time, and only *used* at run time. The per-message path re-derives nothing, because the work was done earlier and recorded. When Part III folds input typing "into parsing," it means the *schema* parse — a design-time act, repeated at most at init when a bundle loads — never the per-message data parse that feeds Execution.

A caution to close: init time is *not* a third mode. There remain two modes — reason about schemas, or transform data. Init time is the operational seam *inside* the Execution side, between "set up once" and "run per message." Two modes, three times.

== A Shared Coordinate System, Not a Shared Object

If the modes are so separate, how do they relate at all? Through coordinates, not references.

A schema graph (shape) and an instance (data) addressed by the *same paths*. The output contract's `lineItems[*].productName` names a position; an instance's value at that position is found by the same path. This shared coordinate system lets a tool *project* an instance onto its schema on demand — to preview values against the contract, or to colour a schema node by whether a sample populated it — without either object holding a pointer to the other. The relationship between a schema and its instances is "conforms to," established per operation, not an ownership link baked into the data.

This matters for N:1 mapping specifically. In Message Contract mode the analysis lives entirely in the shape reading: input schema graphs on one side, the output contract on the other, correspondences between them. Sample instances, when present, are projected onto those graphs for human insight, but they never define the correspondences. The mapping is a relationship between *shapes*; data is how you check it later, in the other mode.

== Where This Book Stands

Almost everything that follows is Message Contract mode. Part II's schema graphs, matchers, and correspondence sets are design-time objects in the shape reading. Execution mode appears only at the edges: as the thing whose correctness Message Contract mode cannot fully guarantee (instance-adequacy is its province), and as the run-time path that must be kept pristine.

Holding the two modes apart is the discipline that makes an N:1 mapping *analysable* at all. It lets us ask, and answer, "is this mapping complete?" without running it — and to know exactly what that answer does, and does not, promise about the day real data arrives.
