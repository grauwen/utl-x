= Why One Output?

This book privileges a single output, and the choice is consequential enough to defend in its own right — the more so because the obvious generalisation, many inputs *and* many outputs, is where this language began and what it deliberately abandoned. This chapter is the rationale. It is reflective rather than load-bearing: nothing earlier depends on it, and a reader in a hurry may skip it. But the question it answers — *why not many outputs?* — is the one most likely to nag, and the answer turns out to be a small theorem about how outputs compose, with a clean rule for the one case where more than one is justified.

== The Discipline of a Single Contract

The argument for one output is the argument for one place to stand. With a single target, every question the analysis asks has a single referent: coverage is coverage *of this contract*, completeness is *this contract* satisfied, "is the mapping right?" is one question with one answer. The correspondence set, the gap report, the treeview — all assume one output and are crisp because of it.

Many outputs dissolve that referent. Coverage becomes coverage of *which* output; completeness fragments into a separate question per target; "the mapping" stops being one object and becomes a bundle. This is not a hypothetical worry. An earlier incarnation of this language was genuinely N:M, and its definitions proved hard to hold in one's head for exactly this reason: without a single privileged contract, the mental model has nowhere to rest. Bringing it back to one output restored the place to stand, and most of this book's clarity descends from that decision.

== N:1 Loses No Generality

The decision would be a poor one if it sacrificed expressiveness, but it does not, and the formal object of Chapter 6 shows why. An N:M mapping is not a new kind of problem; it is several N:1 problems sharing a source. If the target is a disjoint union $T = T_1 union.sq T_2$, the dependencies split into $Sigma_1 union Sigma_2$, and the two halves are *independent* whenever no rule has one target in its premise and the other in its conclusion. So $N{:}1$ is the *atom*, and multiple outputs *compose* from it. The only real question is how they compose — and there are exactly three ways.

== A Worked Case: Payload and Configuration

Make it concrete with the case that raises the question most often. An Open-M engine routinely produces two things at once: the data *payload* — the transformed message — and a *dynamic configuration* that overrides the engine's static settings: a route, a binding, a limit. Two outputs, plainly. Should they be one transformation or two?

The instinct to answer "it depends on how complex the config is" is a false lead. A simple, flat configuration may be expressible in a lightweight expression language and a rich one may demand the full mapping machinery, but the *language* a configuration needs does not decide whether it shares a transformation with the payload. What decides it is *coupling* — and coupling sorts the two outputs into one of three relationships.

== Three Ways Outputs Relate

#strong[Independent.] If the configuration shares no logic with the payload and need not agree with it, the two are simply separate $N{:}1$ transformations that happen to read the same inputs. Write two. (Whether the configuration is flat enough for a lightweight expression language or rich enough to want the full language is, here, beside the point — an independent output is its own mapping regardless of how it is written.)

#strong[Co-derived.] If both outputs are projections of the same intermediate — a normalised, enriched view $V$ built once from the inputs — then one transformation that computes $V$ and projects both is the right factoring: $ "inputs" arrow.r [ "let" V = … ] arrow.r { "payload", "config" }. $ Computing $V$ twice in two transformations would be wasteful, and producing both from a single evaluation *guarantees* they agree — the same input snapshot, the same branch decisions. This is the one case where a single transformation legitimately bears more than one output.

#strong[Chained.] If the configuration is derived from the *produced payload* rather than from the inputs, the outputs are not parallel at all. They are stages — compute the payload, then derive the configuration from it — and that is a pipeline of $N{:}1$ steps, the `chain` already in the Open-M model.

== The Guardrail

Notice precisely what keeps the co-derived case from reopening the wound that N:M inflicted. A transformation with two co-derived outputs still has a *single conceptual subject*: the shared derivation $V$, its one understanding of the inputs, of which the outputs are merely projections. "One understanding, projected two ways" remains comprehensible; "two unrelated jobs sharing a file" does not — and that, exactly, is the line the abandoned N:M crossed and this does not.

So the discipline can be stated as a rule, and it is the whole of the matter:

#block(fill: luma(245), inset: 10pt, radius: 4pt, width: 100%)[
  Permit more than one output only as *projections of a shared derivation*. Outputs that share no derivation become *separate transformations*; outputs where one feeds the other become a *chain*. The atom is always $N{:}1$.
]

== The False Economy of Merging

There is a tempting shortcut, taken by more than one middleware platform: merge payload and configuration into a single combined output schema and map to that, recovering "one output" by fiat. It is one output only on paper. The data contract now carries the engine's control knobs; the data mapping can no longer be reused apart from that particular engine; and a reader of the contract can no longer tell the message from the machinery. Merging heterogeneous concerns to hit a single-output count trades a real separation for a cosmetic one. The single output this book privileges is a single *contract* — not a single sink with everything poured into it.

== What This Buys

The one-output choice, then, is not a limitation the theory tolerates but a result it earns. The atom of mapping is $N{:}1$. The only principled aggregate is the co-derived projection — several target-independent outputs drawn from one shared derivation, kept comprehensible by that shared core. Everything else either splits into independent $N{:}1$ transformations or chains into a pipeline of them. A genuine N:M primitive — arbitrary outputs from arbitrary logic in one transformation — is never needed, which is why abandoning it cost nothing and clarified everything. One output was the right place to stand, and it remains so even when an engine, quite reasonably, wants two things at once.

== Explaining It Back: Sub-Maps as Documentation

The decomposition this chapter defends has a second life, and it answers a problem the field has always had: a finished transformation is hard to *read*. A business analyst who must sign off on it, an auditor who must trace a field, a maintainer who inherits it years later — none of them want to read UTLX, and a single flat "source $arrow.r$ target" sheet collapses under a real N:M, where several inputs converge, arrays iterate, and values are derived rather than copied. The mapping that is hard to *build* is exactly the mapping that is hard to *explain*.

The same atom solves both. If $N{:}1$ is the unit of construction, it is also the unit of *explanation*: render the transformation not as one incomprehensible whole but as a composition of $N{:}1$ *sub-maps*, each individually legible, with an overview of how they combine. This is the idea Mercator — later IBM's WebSphere Transformation Extender — used to make large maps tractable: a complex map was decomposed into named sub-maps, each a self-contained unit. Here the sub-maps are not invented for the report; they *are* the chapter's atoms, and the carving lines are the three relations already named — independent sub-maps stand alone, co-derived ones share a stated derivation, chained ones feed one another. The order-to-invoice case decomposes cleanly:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Sub-map*], [*Atom*], [*What it does*],
  [Header], [payload, config $arrow.r$ invoice header], [copy the order id and date; stamp the billing region (a constant)],
  [Line items], [payload, catalogue $arrow.r$ invoice lines], [map each order line; expand its product code through the catalogue lookup],
  [Totals], [invoice lines $arrow.r$ invoice summary], [sum the line amounts into the order total (a derivation)],
)

Three small, readable atoms compose the invoice no one could read as a block. Each becomes a sheet a BA understands: per target field, its *kind* (copy, lookup, derivation, constant — Chapter 9), its *source* path, its *rule* in business language rather than code ("Order Total = sum of all line-item amounts," not `sum(lineItems[*].amount)`), and a *note* carrying provenance where it matters (Chapter 7) — that the billing region is an injected constant, say. An index sheet shows the composition; the workbook mirrors the sub-map tree.

Two properties keep such a report honest. First, it is generated from the *tested artifact* — the UTLX itself, read structurally — not from the intent that preceded generation; a document that describes what was *meant* rather than what *runs* is the preview-versus-engine divergence in paper form. Second, it is the *reversed prompt* of Chapter 12 aimed at the output: the structural table is extracted deterministically and cannot hallucinate, while the business prose and the sub-map naming are an AI layer over those facts — meaning atop structure, the BA confirming the meaning. The report is therefore a lossy projection, honest about it, linking each sheet back to the UTLX for the full truth.

The quiet payoff returns us to the BA we began with. They often cannot *author* a good mapping document — which is why AI writes the UTLX in the first place — but they can *review and correct* one. A generated report flips their role from author to reviewer, the task they are good at; and because the report is one more reading of the correspondence set of Chapter 7, their corrections can travel back through the same channel that ingests a human specification. From many inputs, one output — and the atom that builds it is the atom that, read back, finally explains it.
