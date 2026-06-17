#heading(numbering: none)[Appendix B · Axioms and Principles]

#show heading: set heading(numbering: none)

This appendix collects, in one place, what the book *assumes* and what it therefore *prescribes*. The axioms are the commitments stated in the Introduction; the principles are the rules of method derived from them across the chapters; the consequences are a few results the combination forces. It is a reference, not an argument — each entry points to where it is developed.

== The Axioms — what is assumed

Assumed truths about the domain, taken as ground and not argued.

#block(
  fill: luma(245), inset: 12pt, radius: 4pt, width: 100%,
)[
  - #strong[A1 · Unity of representation.] Every input and the output can be carried in one data model. _(Ch. 2.)_
  - #strong[A2 · Schema is metadata, separable from data.] Every datum has a shape describable independently of its values. _(Ch. 2.)_
  - #strong[A3 · The formalisation precondition.] Mapping is possible only between a formalised source and a formalised target. _(Introduction.)_
  - #strong[A4 · Asymmetry of the contract.] The output is fixed and privileged; the inputs are the variables. _(Ch. 1.)_
  - #strong[A5 · N:1 is the general form.] The problem is posed with $N >= 1$ inputs and exactly one output; 1:1 is $N = 1$. _(Ch. 1; the choice defended in Ch. 14.)_
  - #strong[A6 · Separation of times.] Reasoning about schemas (design time) is distinct from transforming data (run time). _(Ch. 3.)_
  - #strong[A7 · Total provenance.] Every element of a mapping has a basis — a *fact* derived from structure or a *hypothesis* on fallible evidence. _(Ch. 7.)_
]

== The Principles — what is therefore prescribed

Rules of method. Each is a *consequence* of one or more axioms together with the goal of integration that can be reproduced and audited — never an assumption in its own right.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Principle*], [*Statement*], [*From*],
  [deterministic-first], [compute what structure determines; consult judgement only at the genuine gaps], [A2, A7],
  [structure before names], [a name may *propose* a match, but the schema graph *disposes* of it], [A1, A7],
  [declare, don't infer], [facts the structure does not carry must be declared, not guessed from shape], [A2, A6],
  [surface, don't guess], [under ambiguity, raise the question rather than manufacture confidence], [A7],
  [provenance is visible], [every inferred or injected element carries its `basis`; uncertainty is localised, not averaged], [A7],
  [author, not executor], [AI authors mappings at design time; the engine alone executes them], [A6],
  [validate before trust], [a proposal is a conjecture, tested against the contract before it may run], [A4, A7],
)

== What Follows — a few forced results

Consequences worth naming, each derivable rather than asserted.

- *The pipeline order is forced.* Type, then graph, then structural alignment, then names, then function inference, then proposal, then validation — a directed acyclic chain in which each step consumes the last. The order is not taste; it follows from A2, A4, and A6. _(Ch. 12.)_
- *N:1 needs no new formalism.* A schema mapping $(S, T, Sigma)$ already lets $S$ be many relations; the N-input case is the default, briefly disguised by a tradition of drawing one source box. From A5. _(Ch. 6.)_
- *Some output values are invented, not copied.* The existential in a source-to-target dependency is a labelled null — a value that must exist but is derived, looked up, or supplied. This is the derivation gap the analysis must mark. _(Ch. 6, 9.)_
- *Design-time AI is conjecture-and-refutation.* Because every contribution has a basis (A7) and authoring is separable from execution (A6), an AI proposal is a hypothesis the deterministic machinery refutes or confirms before it runs. _(Ch. 13.)_

These four are the theory in miniature: one model, a fixed contract, provenance on everything, and a clean line between the act of authoring a mapping and the act of running it. From many inputs, one output — assumed in seven axioms, and everything else a consequence.
