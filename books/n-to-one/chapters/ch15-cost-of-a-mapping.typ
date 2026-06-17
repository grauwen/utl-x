= The Cost of a Mapping

A delivered mapping invites one more question, the one this book has so far declined to ask: is it *good* — not merely correct, but efficient? Could the same output be produced with less work, and are some of the language's constructs simply more expensive than others? The question has a precise form, because the mapping is a formal object (Chapter 6), and a formal object has not only a meaning but a *cost*. This chapter sketches the theory of that cost. Like Chapter 14 it is reflective: nothing earlier depends on it, and a reader whose mappings already run may skip it. But "is this the most optimised mapping possible?" is a fair question, and the answer is more structured than _profile it and see_.

== A Mapping Has an Equational Theory

The reason the question has a real answer, rather than a benchmarking ritual, is that a mapping can be *rewritten*. Optimisation is the search, among expressions that mean the same thing, for the one that costs least — and that search needs a supply of *semantics-preserving equivalences* to move through. Relational databases have exactly such a supply, and it is the engine of query optimisation: an algebra in which a selection may be pushed below a join, a projection pulled down to discard columns early, joins reordered — each a rewrite that changes the cost and not the result. A heuristic layer keeps intermediate results small; a cost-based layer estimates and chooses. The whole enterprise rests on relational algebra's equational theory.

UTLX's operators are that algebra and more. `map`, `filter`, `findBy`, `filterBy`, `groupBy`, `reduce`, and `...spread` are the operators of relational algebra extended with the higher-order combinators of functional programming — and *both* traditions arrive with a calculus of equivalences. From the functional side:

```
map f  ∘  map g     =  map (f ∘ g)              -- fusion: two passes collapse to one
filter p ∘ map f    =  map f ∘ filter (p ∘ f)   -- a filter moved before a map
sumBy               =  reduce (+) ∘ map (·)      -- an aggregate is a fused fold
```

The first law is *deforestation* (Wadler): the intermediate collection between the two maps is not merely shrunk but *eliminated*. What the query optimiser states as "keep intermediates small," the functional calculus states as "remove them." These are not heuristics hoped to help; they are theorems, proved once and applied with confidence.

There is even a precise academic home for the case UTLX actually inhabits — transforming *nested* collections, not flat tables. The Nested Relational Calculus (Buneman, Tannen, Wong) and the monoid comprehension calculus (Fegaras and Maier) supply normal forms and fusion laws for exactly this. Mapping optimisation is not a field to be invented; it is relational query optimisation for the flat parts and the comprehension calculus for the nested and aggregating parts, brought to bear on one formal object.

== The Boundary of Chapter 6 Is the Boundary of the Theory

The two calculi divide the work along a line the book has already drawn. Chapter 6 classified UTLX's functions by whether they fit a source-to-target dependency: projection, joins, and nesting fall *within* the tgds; grouping, aggregation, derivation, and ordering fall *beyond* them. That same line decides which optimisation theory governs. The within-tgd operators are relational algebra, and the relational equivalences — selection and projection pushdown, join reordering — apply to them directly. The beyond-tgd operators are folds and groupings, and it is the functional calculus — fold fusion, the comprehension laws — that rewrites them. The boundary that measured *expressiveness* in Chapter 6 measures *optimisability* here: one cut in the formal object, read for cost instead of for completeness.

== A Cost Model: Streaming Versus Blocking

To choose among equivalent forms one must rank them, and the ranking turns on a single distinction familiar from query engines: *streaming* operators versus *blocking* ones. A streaming operator consumes its input element by element and emits as it goes — one pass, bounded memory. A blocking operator must see all of its input before it can produce any output: it *materialises*.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Class*], [*Operators*], [*Cost*],
  [streaming], [field copy, `pick`/`omit`, `map`, `filter`, `...spread`], [linear, one pass, bounded memory — cheap],
  [folding / indexed], [`reduce`, `sumBy`, `countBy`; `findBy` / `filterBy` over an init-time index], [linear, one pass; constant-time probe — moderate],
  [blocking], [`groupBy`, `orderBy`, `distinct`, un-indexed `findBy`, `map` nested over a join], [materialise, sort, or quadratic — expensive],
)

The blocking operators are the pipeline-breakers, and — crucially — they announce their cost in the schema graph *before any data flows*: a `groupBy`, a sort, an un-indexed lookup are visible at design time. The canonical mapping win follows immediately and is structural: correlating $N$ detail records to their headers by a `filter` *per header* is $O(N^2)$; a single `groupBy` is $O(N)$ — the same output, two cost classes apart. The rest is the query-optimiser's playbook, unchanged: index the lookups (the init-time indexing of Chapter 3 *is* a hash-join build), fuse adjacent maps and filters, push filters ahead of joins, project away unused fields early, and hoist a computation that does not vary out of the loop that repeats it.

== Optimise the Plan, Not the Source

This raises a design question whose answer SQL settled decades ago. Where should optimisation live? Not with the author. The lesson of the declarative query is the separation of the *what* from the *how*: one writes the query that states the result, and an optimiser chooses the plan that computes it. The book has already drawn this very line — Chapter 6's distinction between the *mapping* (the specification) and the *transformation* (its executable realisation), and Chapter 3's between design time and run time. Optimisation is simply the act of realising the same mapping by a cheaper equivalent transformation.

So the instruction "write the most optimal UTLX" is misplaced. The author — human or AI — should write the *clearest correct* mapping; an optimiser pass, a query planner for mappings, should rewrite it into an efficient plan. This dissolves the apparent conflict with the book's other commitment, to idiomatic and readable mappings (Chapters 11 and 13): one need not choose between fast and legible, any more than a SQL author must uglify a query to make it run well. Keep the source the clearest statement of intent; let the planner produce the plan.

== Why It Is Sound: Determinism, Again

Algebraic rewriting is valid under exactly one condition — *referential transparency*. The law $"map" f compose "map" g = "map" (f compose g)$ holds only if $f$ and $g$ are pure: no side effects, the same input always the same output. Otherwise fusing the two passes could change what is observed. UTLX has that purity, and it is the same property Chapter 13 leaned on to make a mapping auditable: an output that is *computed* — deterministic, reproducible. The convergence is worth stating plainly: the determinism that buys *trust* is the determinism that buys *optimisability*. A language you can prove things about is a language you can both audit and rewrite — and the run-time AI of Chapter 13 forfeits both at once, because a generated output is neither reproducible nor a fixed point any algebra can move.

== Three Times of Optimisation

Not every optimisation happens at the same moment, and the three-times model of Chapter 3 sorts them. The heuristic rewrites — fusion, pushdown, group-once — depend only on structure and are always improvements; they belong to *design time*, computed once over the schema graph. Building the index that turns an $O(N dot M)$ lookup into an $O(N)$ one is an *init-time* act, exactly where Chapter 3 placed it. And the cost-based layer — choosing a join order by which input is smaller or which predicate is more selective — needs *statistics* that schemas alone do not carry: it must be handed declared cardinalities, or profiled at *run time* and adapted. Heuristic optimisation is free and early; cost-based optimisation is data-dependent and late. The same ladder, once more.

== The Honest Limit, and the Gate

Two cautions keep the chapter from promising too much. The first: "the most optimised mapping possible" is, in general, neither attainable nor decidable — optimal join ordering is NP-hard, and finding the globally cheapest program equivalent to a given one is undecidable. What an optimiser delivers is what a query planner delivers: a *local* optimum reached by sound rewrite rules and a bounded cost search — a normal form, not a proven global minimum. AI is no different in kind. It tends to generate mappings that are *idiomatically* near-optimal in structure — reaching for `groupBy` over nested filters, as Chapter 13 steers it — but not provably optimal, which is exactly why a deterministic optimiser is a fitting finisher to an AI author.

The second caution is the book's standing discipline. Every rewrite is a *conjecture* that it preserves meaning, and it must be discharged, not assumed — either *by construction*, as an instance of a proven equivalence (the verified-compiler-pass guarantee), or *by refutation*, running the old form and the new against samples and comparing (instance-adequacy, Chapter 3 — a check, not a proof). The first is preferred, for the usual reason: it is certain where the second is merely evidential. And the gate is absolute: an "optimisation" that changes the output is not an optimisation but a defect, and the validation of Chapter 13 is what stands between the two.

== What This Adds

The formal object of Chapter 6 thus carries one more reading. It has a *meaning* — the output it specifies; a *completeness* — whether the contract is covered; and now a *cost* — how dearly that output is bought, measured by the same equational theory that gives databases their query optimisers and functional languages their fusion laws. The cost is governed by the boundary the book already found: relational algebra below the tgd line, the comprehension calculus above it. None of it asks the author to write cleverly; it asks the engine to rewrite faithfully. From many inputs, one output — and, where the theory permits, the same output for less.
