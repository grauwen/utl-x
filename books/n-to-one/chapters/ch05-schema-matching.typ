= Schema Matching

The previous chapter turned schemas into graphs and observed that candidate mappings are paths the graph permits. But a permitted path is not yet a chosen one: of the many ways a source node *could* correspond to a target node, which actually do? Deciding that is the problem of _schema matching_, and it has a four-decade literature that applies almost without modification. This chapter borrows that discipline and lands it on UDM schema graphs.

== The Rahm–Bernstein Taxonomy

The organising work is Rahm and Bernstein's survey of automatic schema matching, which classifies every matcher along orthogonal axes. Three of them structure this entire book.

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Axis*], [*The two poles*],
  [granularity], [element-level — matching individual fields — versus structure-level — matching subtrees],
  [evidence], [schema-only — names, types, constraints — versus instance-based — actual values],
  [signal], [linguistic — names — versus constraint-based — keys, foreign keys, cardinalities, types],
)

Each axis has already appeared in this book, which is the point: the classical taxonomy is not an analogy for UTLX's design but its skeleton.

The *granularity* axis is the element/structure split that motivated Chapter 4: a credible matcher must work at the structure level, which is why the schema graph must precede element comparison. The *evidence* axis is the Execution-versus-Message-Contract distinction of Chapter 3: Message Contract analysis is schema-only and constraint-based; instance evidence belongs to the other mode. The *signal* axis is the names-versus-structure judgement of Chapter 4: constraint-based signals outrank linguistic ones. Reading the taxonomy is reading the architecture.

== Element-Level Signals and Their Ceiling

At the element level, three signals compare a single source field to a single target field.

*Name similarity* normalises and compares identifiers. String distance — edit distance, token overlap — handles the easy cases (`firstName` versus `first_name`). Synonym dictionaries stretch a little further (`firstName` versus `givenName`). But there is a hard ceiling. No string metric and no fixed dictionary will bridge `firstName` to `name-given-at-birth`, or `customerId` to `clientRef`, or a Dutch field name to its English counterpart. These require world knowledge — the kind a human supplies instantly and a language model supplies reliably, and no deterministic linguistic rule supplies at all. Element-level name matching is therefore a *pre-processing* step and an *AI* step, with very little useful middle.

*Type compatibility* asks whether the source value could inhabit the target field: a string into a string, an integer into a number, a date into a date — with a normalisation step that collapses the many spellings of "number" and "date-as-string." Type compatibility rarely confirms a match on its own, but it frequently *refutes* one, and refutation is valuable: it prunes the space the structural matcher must search.

*Value domain*, where a sample is available, compares the ranges and enumerations the two fields draw from. In Message Contract mode this is used cautiously — it is instance evidence, and Chapter 3 warned against letting it masquerade as schema proof — but as a tie-breaker it is legitimate.

== Documentation as a Semantic Signal

There is a fourth element-level signal, and it is the strongest of them when present. Schemas often carry *documentation* — an `xs:documentation` annotation in XSD, a `description` or `title` in JSON Schema, a `doc` field in Avro — a human sentence saying what an element means. This is precisely the world knowledge the bare name lacks: where `KUNNR` defeats every string metric, the annotation "Customer number (SAP debtor)" resolves it outright. Documentation is to a schema what the analyst's mapping sheet is to a mapping — human-authored intent, in natural language, sitting on the exact semantic axis the element level otherwise cannot cross.

Three things fix its place in the method. First, it is *preserved*, not lost. A UDM data tree does drop an instance's stray comments — those describe one document, not the contract — but *schema* documentation is lifted into the model as the schema is parsed, and normalised across formats into one representation (USDL's `%documentation` and `%description`). The many ways formats spell documentation collapse to one, which is why the matcher need never know which format a schema came from.

Second, it is a *linguistic* signal, and so it obeys the rule the next sections make strict: it *proposes*, it does not *dispose*. A description is far better evidence than a cryptic identifier — within the linguistic layer it outranks the name — but it never overrides the graph. A sentence claiming a field "holds the customer record" is a hypothesis the foreign keys can still refute. Documentation lifts the ceiling on names; it does not lift names above structure.

Third, it is a *hypothesis*, recorded as such. Documentation can be stale, boilerplate, written in another language, or simply wrong — a comment that outlived the field it describes. A correspondence drawn from it therefore carries its own provenance, `doc-derived` (Chapter 7): stronger than a guess from string similarity, because a human wrote the sentence, yet weaker than a structural fact, and so subject to the same validation as any other claim about meaning. And like all such evidence it is *optional* — present, it is the best shortcut across the semantic gap; absent, the analysis falls back to names, structure, and a model's world knowledge, exactly as before.

== Structure-Level Signals

Structure-level matching asks the larger question: can this source *subtree* satisfy this target *subtree*? Its signals come from the graph.

*Cardinality* must agree, or the mismatch must be explained: a one-to-many source feeding a one-to-one target is a flattening or an aggregation, not a copy. *Nesting depth* and shape similarity favour correspondences that preserve structure over those that contort it. *Foreign-key reachability* — the Clio signal — is the strongest of all: if a target subtree is reachable from a source subtree along key and foreign-key edges, the correspondence is structurally grounded, whatever the names say.

The decisive property of structure-level signals is that they *outrank* names. A repeating group of line items on the source and a repeating group of invoice lines on the target, both reachable by foreign key to a product entity, are a strong correspondence even when their field names share almost nothing. The graph knows what the dictionary cannot.

== Composite Matchers

No single signal is sufficient, and the literature's answer is to *combine* them, which the theory adopts wholesale.

*Cupid* is structure-aware: it computes similarity and then propagates it up and down the schema tree, so that agreement among a node's children reinforces the node, and vice versa. *COMA* and its successor COMA++ make the combination explicit and configurable: several individual matchers, each producing a score, combined under tunable weights. This is the direct model for the scored correspondence set of Chapter 7 — many signals, a weighted composite, and the sub-scores kept visible. *Similarity Flooding* casts the problem as graph propagation: similarity flows along the edges of a combined graph until it settles, a method that maps naturally onto UDM schema-graph alignment.

The shared lesson is that matching is *multi-signal* and the combination must be *legible*. A matcher that emits only a final number is untuneable and unexplainable. One that emits a structural score, a type score, a name score, and a foreign-key flag can be reasoned about, corrected, and trusted in proportion to which signal carried it.

== Structure First, Names as Tie-Breaker

The discipline that falls out of all this is a strict ordering, and it is the rule the whole pipeline obeys: *derive candidate correspondences from structure; apply name similarity only to choose among structurally compatible candidates; never let a name promote a structurally incompatible one.*

The reason is asymmetry of failure. A false structural match is rare and usually visible — the cardinalities or the foreign keys give it away. A false *name* match is common and invisible: two unrelated `id` fields, two `amount` fields in different entities. Letting names lead means importing the most frequent and least detectable error first. Letting structure lead, with names breaking ties, imports the rarest error and uses the most fallible signal only where the safe ones have already agreed.

== Where Matching Ends

Schema matching is mostly deterministic, and that is its strength — the same graphs yield the same correspondences, every time. But it has a boundary, and honesty about that boundary is what keeps the analysis trustworthy. Implicit foreign keys (a `customerId` with no declared `keyref`), choice groups (which branch of an `xs:choice` is the real target?), and the semantic name gaps the element level cannot close — these resist any deterministic rule. They are not failures of the method; they are the precise points where world knowledge is required, to be supplied by a human or a language model and recorded as such. Where that boundary lies, phase by phase, and how the analysis marks which side of it each correspondence came from, is the subject of the chapters on the correspondence set and on function inference.
