= The Mapping as a Formal Object

So far a mapping has been a collection of correspondences — pairings of source nodes to target nodes, found in the graph and scored. That is enough to *describe* a mapping but not to *reason* about one. To ask whether a mapping is complete, whether it is sound, whether it produces a minimal output, the mapping itself must be a formal object with a definition. The data-exchange literature supplies exactly such an object, and this chapter introduces it — together with a clear statement of where UTL-X transformations outrun it.

== A Schema Mapping Is a Triple

In data-exchange theory a schema mapping is written

$ M = (S, T, Sigma) $

where $S$ is the source schema, $T$ is the target schema, and $Sigma$ is a set of logical constraints that relate them. The mapping is not the data and not the transformation code; it is this triple — a specification of *what must hold* between source and target, against which any concrete transformation can be checked.

The N:1 case, which a casual reading of the matching literature would think exotic, is already contained here. Nothing in the definition requires $S$ to be a single schema. In the relational setting $S$ is a *set* of relations to begin with, and the constraints in $Sigma$ range freely over all of them. For UTL-X this means $S$ is simply the union of the input schema graphs; the dependencies still span source to target. The many-inputs problem does not break the formalism — it is the formalism's default, briefly disguised by a tradition of drawing one source box.

== Source-to-Target Dependencies

The constraints in $Sigma$ are *source-to-target tuple-generating dependencies* — tgds. A tgd has the shape

$ forall x: P(x) arrow.r exists y: Q(x, y) $

read as: for every tuple in the source matching pattern $P$, there must exist a tuple in the target matching $Q$. The value the target receives is built from $x$ by projection and renaming — moving a value, possibly to a new name, possibly into a new structural position.

This is the formal core of "joins and nestings come from keys, not names." A tgd whose source pattern follows a foreign-key path expresses precisely a join; a tgd whose target pattern nests one entity inside another expresses precisely a structural reshaping. The Clio observation of Chapter 4 — that mappings are reachability under constraints — is the statement that the right tgds are *derivable from the schema graph*, not authored independently of it.

== Value Invention: The Boundary of Copying

A tgd's existential, $exists y$, hides a deep point. Some target values are projections of source values; others are *invented* — they exist in the target but correspond to nothing in the source. Data-exchange theory models invented values with *labelled nulls* and *Skolem functions*: placeholders, parameterised by the source tuple, standing for "a value that must exist here but is not copied from there."

This is the formal line between *copying* and *deriving*, and it is the spine of the output analysis to come. A target field filled by projection is a copy. A target field that is a labelled null — a value that must exist but is not present in any source — is a derivation: it must be computed, looked up, generated, or supplied as a constant. The existential quantifier is the theory's quiet admission that not everything in the output comes from the input, which is exactly the situation an invoice's `orderTotal` puts us in.

== Three Kinds of Dependency for N Inputs

The heterogeneous inputs of Chapter 1 are not symmetric, and the formal object must reflect that. The tgds fall into three classes.

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Class*], [*Form and meaning*],
  [primary], [$S_"payload" arrow.r T$ — the core structural mapping; full schema-graph treatment applies],
  [enrichment], [$S_"prior-step" arrow.r T$ — augmenting or overriding from a previous pipeline step; same structural class, with temporal provenance],
  [lookup binding], [$S_"config/vars" arrow.r T$ — constants injected as values; no structural alignment, no scoring],
)

The third class earns its separation. A name–value input — a configuration set, a bag of pipeline variables — carries no structure to align: no entities, no keys, no foreign keys. It is not a source schema in the matching sense at all; it is what data-exchange theory would call a set of *constants* and what a programming language would call parameters. Running structural matching over it is meaningless. Such inputs are recorded as *bindings* — direct value injections — not as scored correspondences. The formal object keeps them in a separate compartment, and so will the artifact of Chapter 7.

== Where UTL-X Exceeds the Theory

It would be convenient if every UTL-X transformation were expressible as a set of tgds. It is not, and the gap is precisely the interesting part. A tgd moves and reshapes values; by explicit design in the data-exchange literature, it does *not* compute, aggregate, or invent values by calculation. Lay the UTL-X function space against that boundary:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Function class*], [*Within tgds?*],
  [field reference, `pick`, `omit`], [yes — projection],
  [`...spread` / merge], [yes — a tgd over several source relations],
  [`map`, `mapBy` over a collection], [yes — a tgd over a sequence],
  [`findBy`, `filterBy` (keyed lookup)], [partly — a foreign-key join, the Clio pattern],
  [`filter`], [partly — a selection predicate in the source pattern],
  [`groupBy`], [no — grouping has no tgd equivalent],
  [`sumBy`, `countBy`, `reduce`], [no — aggregation is beyond tgds],
  [derived fields (`total = sum(qty * price)`)], [no — computation is beyond tgds],
  [`orderBy`], [no — ordering is outside relational semantics],
)

The tgds cover the structural skeleton of a mapping — the copies, the joins, the reshapings, the iterations. Everything below the line — grouping, aggregation, derivation, ordering — lies outside what the schema-matching and data-exchange literature models. These are not gaps in UTL-X; they are gaps in the *theory*, and naming them is what lets the analysis be honest. A target field that needs a `sum` is, formally, a labelled null the tgds cannot fill: a *derivation gap*. The next chapters give those gaps a first-class place in the analysis rather than letting them hide as false matches.

== What the Formal Object Buys

Treating a mapping as $(S, T, Sigma)$ rather than as an ad-hoc list of assignments is not formalism for its own sake. It buys three things the rest of the book spends.

It buys a *definition of completeness*: every required target obligation is discharged by some dependency or honestly marked as an unfillable gap — the schema-completeness of Chapter 1, now precise. It buys a *separation of structure from computation*: the tgd-expressible skeleton is exactly the part a deterministic algorithm can derive from the graph, and the part beyond it is exactly where derivation, and human or AI judgement, must enter. And it buys an *account of the N inputs*: primary, enrichment, and binding are not informal labels but distinct classes of dependency, with distinct treatment. With the object defined, Chapter 7 can finally write the analysis down.
