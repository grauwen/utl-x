= Strategy-First Generation

Everything in this book has been preparation for one act: producing a transformation. The analysis — typed inputs, a schema graph, a scored correspondence set, inferred functions — is not an end in itself. It exists so that the transformation that finally gets written is not merely correct but *idiomatic*: a mapping that reaches for spread, `map`, joins, and `groupBy` because the analysis told it to, rather than one that fills two hundred fields one at a time. This closing chapter assembles the pipeline that does that, and then steps back to ask what the whole theory was for.

== Why One Fixed Prompt Fails

Imagine generating an N:1 mapping with a single instruction: "fill every output field from a source." It is the obvious approach, and it fails in three ways that this book has spent its chapters diagnosing.

It *enumerates*. Asked to fill each field, a generator writes an assignment per field — two hundred lines where `...$payload` and a handful of overrides would do. The structure of the instruction bakes in per-leaf copying, and the result is verbose where it should be terse.

It *ignores the language*. Spread, pipelines, `map`, `groupBy`, `reduce`, joins — the expressive constructs that make UTL-X worth using — are rarely produced, because the task has been framed as field-filling and field-filling has no place for them.

It *flattens the inputs*. "A source" treats every input as an equal candidate, and so misses that the second input is a lookup joined by key, not a co-equal place to find fields. The join shape — the very thing Chapter 4 worked to recover — never appears.

These are the three failures of Chapter 1 — shape, expressiveness, asymmetry — returning as failures of *generation*. A single prompt cannot avoid them because no single prompt encodes every mapping shape.

== The Planning Layer

The fix is to insert a planning layer before generation, turning a one-step process into a pipeline:

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  `mode` $arrow.r$ `analyse` $arrow.r$ `strategy` $arrow.r$ `strategy-specific generation`
]

A first pass analyses the problem and *chooses a strategy* — a shape for the mapping as a whole. Generation then proceeds with the prompt and the scaffold that fit that strategy, so it reaches for the right constructs by construction. The division of labour is the one the book has built toward: *strategy decides the shape; the correspondence set decides the sources.* A mirror mapping and a join mapping are generated differently not because the model is asked nicely but because they are different problems, recognised as such before a line is written.

== The Strategy Catalogue

The strategies are few, and each carries its own generation shape and its own scaffold:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Strategy*], [*When*], [*Shape*],
  [mirror / pass-through], [the output closely follows one input], [`...$driver` plus overrides — never enumerate],
  [restructure], [the same data in a new shape], [explicit object construction],
  [array-transform], [the driver is a collection], [`map` over the driver],
  [join / lookup], [a driver plus reference tables], [index the lookup, look up per row],
  [aggregate / group], [many rows collapse to few], [`groupBy` / `reduce`],
)

The decisive detail is that the *scaffold* is per-strategy. This is what resolves the enumeration problem at its root: a mirror mapping is seeded with a spread, a restructure with field holes, a join with the index-and-lookup skeleton. The generator is not asked to remember not to enumerate; it is handed a starting point in which enumeration is not the shape. Real mappings compose these — `map` the driver, spread its fields, look up the reference per row, default the gaps — but each strategy contributes a recognisable, idiomatic fragment rather than a wall of assignments.

== Deterministic First, AI Where It Earns Its Place

The analysis that chooses the strategy is *deterministic-first*. Cardinalities, key detection, the mirror ratio, the structural archetype — these come from the schema graph at almost no cost, and they are enough to select a strategy in the common case. A small, targeted call to a language model is reserved for genuine ambiguity: a tie between two strategies, an implicit key, a semantic name gap. This inverts the cost profile of the single-prompt approach, where one large generative call did everything slowly and opaquely. Here the bulk of the work is cheap, fast, and explainable, and the oracle is consulted only where structure genuinely runs out.

Self-correction — validating the generated transformation against the engine and repairing it — remains, but as a *safety net*, not the primary mechanism. A pipeline that relies on generate-then-fix as its main engine has admitted it does not understand the problem; one that analyses, selects, and generates, and only then validates, uses correction to catch the residue rather than to substitute for understanding.

== Validate as the Trigger

All of this is Message Contract mode, and it has a natural home in the tooling. In Message Contract mode the primary action is not *execute* — there is no data to execute on — but *validate*. So the Validate action is exactly the right trigger for the whole pipeline: it runs the analysis, with no execution, and surfaces its stages — classify the inputs, find the correspondences, refine the uncertain ones, report coverage. Where Execution mode runs the data, Validate runs the *analysis*. The symmetry is clean: two actions, two modes, two questions — what does this produce, and is this mapping sound.

== The Visual Surface

The correspondence set, the typed inputs, and the inferred functions are not only fuel for a generator; they are a *picture*. Rendered, they become a mapper in the familiar idiom: the input schemas as trees on one side, the output contract as a tree on the other, and between them the derivations — a direct copy as a plain connector, a function as a labelled block, a gap as a flagged node. Each connector's colour carries its status and its basis, so a structurally certain copy and an AI-inferred guess do not look alike. The same analysis that grounds the generated code grounds the diagram a human reads; persistence, generation, and visualisation are three uses of one object, which has been the recurring sign throughout this book that the formalisation was worth it.

== From Many, One

It is worth stating plainly what has been built, because it is easy to lose in the machinery. An N:1 mapping began, in Chapter 1, as a craft: several inputs, one contract, and a developer's judgement filling the space between. The chapters since have turned that craft into something that can be *reasoned about*. The inputs and output share one model. Schemas are graphs. A mapping is a formal object — a set of dependencies, with a clean line between the structure a graph determines and the computation it cannot. The analysis is a scored, provenance-bearing artifact. The inputs are typed by role and structure; the outputs by derivation. The functions are inferred where structure decides and flagged where it does not. And the transformation is generated strategy-first, idiomatic by construction.

The result is not that mappings get written for you — judgement remains, especially at the derivation gaps and the semantic name bridges where the theory honestly says structure runs out. The result is that you can say, precisely, what a mapping *does*: which fields are copied and which derived, which inputs are sources and which are looked up, where it is complete and where a human must still decide. That is the difference between writing an N:1 mapping and *understanding* one — and it is why, for the architect who wants the second, the theory was worth the book.

From many inputs, one output. From a craft, a discipline. From many, one.
