= Proposing a Mapping: Two Roles for AI

The previous chapters have built, piece by piece, everything needed to ask a model for a mapping: typed inputs, a schema graph, a scored correspondence set, output-field kinds, inferred functions. This chapter assembles those pieces into the concrete workflow that produces an AI mapping *proposal* in Message Contract mode — and, in doing so, makes explicit a distinction the earlier chapters left implicit. "AI helps with the mapping" hides two very different jobs. AI is used *within the analysis*, to prepare; and AI is used *for the proposal*, to author. Separating those two roles is the key to using AI both safely and well, and it is the subject of this chapter.

== The Two Roles

Picture the moment a model is asked to help. There are, in fact, two such moments, and they are not alike.

#strong[Role one — AI as assistant in the analysis (prepare).] Long before any mapping is drafted, the analysis runs into small, local questions that deterministic rules cannot settle: is this bare `customerId` an implicit foreign key? which branch of this choice is the intended target? does `netDue` denote an aggregate? is `clientRef` the same thing as `customerId`? Each is a narrow question about a single node, with a checkable answer, and each is exactly where a model's world knowledge earns its keep. Used this way, AI does not write the mapping; it *sharpens the picture the mapping will be drawn from*. Its answers feed back into the typed inputs and the correspondence set, each tagged `ai-inferred` so its provenance is never lost.

#strong[Role two — AI as author of the proposal (propose).] Once the analysis is complete, there is a second, larger request: given everything now known, draft the mapping. The model receives the typed inputs, the scored correspondences, the output-field kinds, and the explicit list of gaps, and it returns a transformation — filling the derivation gaps, resolving the residual ambiguities, and drafting idiomatic UTLX. This is one request, about the whole mapping, not many requests about individual fields.

The two roles differ on every axis that matters. Role one is *narrow* (one node), *frequent* (many small calls), and *validated locally* (does this single inference hold?). Role two is *broad* (the whole mapping), *singular* (one grounded call), and *validated globally* (does the proposal satisfy the contract?). They take different prompts, carry different risks, and demand different checks. Collapsing them — treating "AI in the pipeline" as one thing — is how teams end up either trusting too much or using AI too little.

== The Pipeline, End to End

With the two roles named, the full Message Contract workflow enumerates as nine steps, from raw inputs to a validated proposal.

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Step*], [*Produces*], [*AI?*],
  [1 · parse + type each input], [a typed schema document (role: payload / lookup / nvp …) (Ch. 8)], [det.; role-one assist at the chain edge],
  [2 · build the schema graph], [nodes + PK/FK edges (structurally-rich inputs only) (Ch. 4)], [deterministic],
  [3 · analyse the output], [each target field's derivation kind (Ch. 9)], [det.; role-one assist (semantic names)],
  [4 · structural alignment], [scored candidate correspondences (Ch. 5, 7)], [det.; role-one assist (implicit FK, choice)],
  [5 · name refinement], [residual semantic correspondences (Ch. 5)], [role one — AI-primary],
  [6 · function inference], [a mapping class per output field (Ch. 10)], [det.; AI proposes gap expressions],
  [7 · assemble the request], [structured context: typed inputs + correspondences + output kinds + gaps (Ch. 7)], [no AI — pure assembly],
  [8 · propose], [a draft mapping in idiomatic UTLX], [role two — the proposal],
  [9 · ground & validate], [a verified mapping; gaps flagged for review], [no AI — verification],
)

The shape to notice is that AI appears at the *edges* of the analysis (role one, sharpening individual facts) and once at its *culmination* (role two, drafting from the whole), with deterministic work — graph construction, scoring, assembly, validation — carrying everything between. The pipeline is deterministic-first; AI is a targeted enrichment at the two points where it is genuinely needed, never a general overlay.

== Why This Order Is Forced

The nine steps are not a matter of taste; they are a dependency chain, and almost none of them can be reordered, because each consumes what the one before it produced. Its root is axiomatic. That an input's type gates whether it has a graph at all is axiom A2 (schema is separable metadata); that every step serves one fixed target is axiom A4 (the asymmetry of the contract); that the whole chain is schema-reasoning prior to any run is axiom A6 (the separation of times). The order below is forced by those commitments of the Introduction, not chosen here — which is why the argument can be made by appeal to dependency alone.

Typing comes first because it is the *gate*. Until an input's role is known, there is no deciding whether to build a graph for it at all — and handing a name–value parameter set to a structural matcher (step 4) is meaningless. Type, then decide what to do with the type. The graph (step 2) must precede structural alignment (step 4) for the plainest of reasons: alignment is reachability *over* the graph, and there is nothing to traverse until the graph exists.

The load-bearing edge is the next one. Structural alignment (step 4) must precede name refinement (step 5), never the reverse, because names are a tie-breaker *among structurally compatible candidates* — never a way to promote a structurally incompatible one. The structural candidates must be in hand before a name is allowed to choose between them. Reverse these two steps and the most common, least visible error in all of mapping — a confident match on a shared name between unrelated fields — walks straight in the front door.

Output analysis (step 3) is the one step with slack. It reads only the output contract, so it depends on nothing upstream and may run in parallel with steps 1 and 2. But it cannot be skipped or deferred past *function inference* (step 6), because a mapping class is the product of an output field's *kind* and the *availability of a source* for it; neither factor alone fixes the construct, so both must be ready before inference can name it.

From there the order is strict again, and for the same reason each time. Function inference (step 6) needs the correspondences of steps 4–5 and the output kinds of step 3. Assembling the request (step 7) needs the completed analysis, because the analysis *is* the context — there is nothing to assemble before it exists. The proposal (step 8) needs the assembled request, which is the entire point of preparing: a grounded request, not a cold one. And validation (step 9) needs the proposal, because one can only check a thing that has been produced.

The payoff of this is a guarantee, not merely tidiness. Because every step is fed only by completed earlier steps, the pipeline is a directed acyclic graph: it runs once, forward, with no cycles and no hidden back-channels. And where it places its AI calls — at the semantic edges of steps 1 through 5, and at the single proposal of step 8 — is *determined by the dependencies*, not chosen for convenience. To the reader who asks why the steps run in this order, every arrow gives the same one-word answer: *dependency*.

== Why Preparing Beats Asking Cold

The temptation, and the industry default, is to skip all of this: hand a model two raw schemas and the instruction "write the mapping." That is the single-prompt approach of Chapter 11, and it fails the same three ways — it enumerates instead of using the language, it flattens the inputs, and, worst of all, it asks the model to re-derive the structural skeleton it is *worst* at while doing nothing with the structure that is *known*.

Preparing the request inverts this. The deterministic analysis builds the structural skeleton — the typing, the foreign-key joins, the cardinalities, the copies — which it can do reliably and cheaply. The model is then asked only to supply what structure cannot: the semantic bridges and the derivation expressions. This is the principle the correspondence set was designed for in Chapter 7 — *structured context with targeted gaps* rather than an open-ended schema pair. The proposal arrives grounded (it builds on a skeleton known to be sound), scoped (the model fills gaps, it does not invent the frame), and checkable (every gap it closes is a discrete, reviewable claim).

The role-one assists compound this advantage. By resolving the implicit foreign keys and bridging the names *during the analysis* — before the proposal request is even assembled — they hand the proposer a cleaner context with fewer unknowns. Preparation is not overhead before the real AI call; it is what makes the real AI call small, grounded, and good. The better the preparation, the less the proposer must guess, and the less there is to guess, the more the proposal can be trusted.

The same fact-then-meaning order governs how an input is *understood* before it is mapped, and it is worth seeing concretely because both halves already ship. A deterministic *abstract* of each input (Chapter 3) supplies the structural facts — root entity, sections, array cardinalities, depth — cheaply and with no risk of hallucination. A second, AI step — a *reversed prompt* — then reads that structure and supplies what it cannot: the *semantics*. What is this document about? What does a cryptic `KUNNR` denote? Which section is the billable detail rather than an audit envelope? That step contributes exactly on the axis deterministic reading cannot reach — the semantic heterogeneity named in the Introduction — and it earns its place. But its output is *hypothesis*, not fact: it is tagged `ai-inferred`, it is *grounded on the deterministic abstract* rather than asked cold, and it is validated before it is allowed to shape a correspondence. Hand the model the structural facts and ask only "what does this mean?", and its answer is scoped and checkable; ask it "what is this document?" cold, and you are back to guessing. The deterministic abstract and the AI reversed prompt are this whole chapter in miniature, applied to a single input: structure first, as fact; meaning second, as a hypothesis to be tested.

== Seeding from a Human Specification

There is a richer grounding still, and it predates the pipeline by decades. In a great many enterprise integrations the mapping was *already written down by a person* before any tool was asked to help. A business analyst, handed the fixed output contract — an SAP RFC, an IDOC, a partner's invoice schema — produces a mapping document, almost always a free-format spreadsheet: source field beside target field, a rule or a lookup in the next column, a note where the logic is subtle. The developer then writes the transformation with that spreadsheet open as a reference. The document never executes; it is not an input to the mapping in the run-time sense. But it is a treasure for the *authoring* of one.

The reason is precise. Strip away the format and the analyst's spreadsheet *is* a correspondence set — an informal, hand-authored draft of the very artifact Chapter 7 sets out to compute. And it carries exactly the part the deterministic machinery is weakest at: the *semantic* correspondences. That `KUNNR` becomes `CustomerId`, that a status code is translated through a named crosswalk, that two source fields concatenate into one target — these are domain facts no schema graph encodes and no structural matcher can recover. The analyst supplied them by hand, from knowledge of the business. Seen this way the spreadsheet is not documentation to be admired and set aside; it is the highest-value grounding a proposer can be given, because it pre-supplies the semantic bridges that role one would otherwise have to *guess*.

Two distinctions keep this honest. First, the specification is *not* a run-time input, and must not be confused with one. A lookup table — say, an SAP-to-Siebel user-id crosswalk — is reference *data*: it flows at execution, joined by key, in the binding compartment of Chapter 6. The analyst's spreadsheet is reference *specification*: it lives entirely at design time, grounding the analysis, and is gone before a message ever flows. Both are "reference," on different planes; collapsing them re-imports the very confusion Chapter 3 was built to prevent.

Second, the spreadsheet is *unformalised*. It is free text in merged cells, with a column convention only its author fully knows — and by the precondition of the Introduction, unformalised knowledge cannot be used mechanically until it is parsed into identifiable parts. So a spec does not enter the pipeline raw. It enters through a role-one step that *formalises* it: extract structured `source → target` correspondences from the free-format document — an extraction task a model is well suited to. Each extracted pairing is recorded with `basis: analyst-spec` and then run through the same gate as any other correspondence: *validate it against the live schema*. Does the named target field exist? Is its type compatible? Is the named lookup present? Where the answer is no, the entry is a *stale-spec flag*, not a mapping — the spreadsheet supplies intent, the schemas supply truth, and the pipeline reconciles the two. This is the same validate-before-trust discipline the next chapter argues in general, applied to a human's draft.

#strong[The analyst is a role, not a person.] This is the unifying point, and it is what keeps the feature an *addition* rather than a fork in the pipeline. Authoring the semantic correspondences is a job the pipeline always needs done; a human analyst with a spreadsheet is merely one way to fill it. In the common case there is *no* spreadsheet — the integration is new, or the analyst's document was never written — and then AI itself plays the analyst, generating the same semantic correspondences from the schemas and its world knowledge, recorded at the lower confidence of `ai-inferred` (this is precisely the name-refinement of step 5). When a specification *does* exist, nothing about the pipeline changes except the provenance and the confidence: AI's job shifts from *inventing* the semantics to *extracting and validating* documented ones — a smaller, safer task. The correspondence set is indifferent to which hand authored its entries; it only records, in `basis`, whose they are.

This is not a new idea so much as an old one returning. The Clio system of Chapter 4 took, as its input, *user-supplied value correspondences* — a human's pairings of source elements to target elements — and derived the full mapping from them using the schema's keys and structure. The analyst's spreadsheet is that input, arriving in the wild: messier, free-format, and decades-deployed. "A human draws the correspondences, the system completes them" is the founding move of schema mapping; the only modern additions are that a model can now *read* the human's draft out of an Excel sheet, and that the draft must be *validated* against schemas the analyst may not have re-checked.

== Formalising the Request

Both roles so far have assumed the request was understood — that "the mapping" everyone is drafting is already agreed. Often it is not. A user arrives not with a spreadsheet but with a sentence: _add Dutch VAT — high, low, and all the levels._ That sentence is a third intent artifact, and the rawest of the three. The output contract is fully formal; the analyst's spreadsheet is semi-structured; a free-text prompt is unstructured intent. By the precondition of the Introduction, it cannot drive the pipeline as it stands. Before role one can prepare or role two can propose, the request itself must be *formalised* into an exact, grounded, checkable specification. This is a front-end stage, logically prior to the nine steps — and, tellingly, it is *itself an N:1 problem*: its inputs are the user's text, the output schema, the current inputs, any existing mapping, and the model's world knowledge; its single output is the specification. The book's own pattern recurs one level up.

#strong[The prompt can demand additions.] This is what sets it apart from the other two artifacts, and it is the whole of the difficulty. The schemas bound what is *possible*; the spreadsheet pairs fields that already *exist*. A prompt can require what is present nowhere: a new output field, a new derivation, and — most sharply — *constants drawn from world knowledge*. That Dutch VAT is 21% standard, 9% reduced, 0% zero-rated is a fact in neither the input nor the output; the model must supply it. This is the value invention of Chapter 6 at its limit: not merely "a value must exist here," but "a value must exist here, and it comes from knowledge the request invoked." A prompt, uniquely, can make $N$ grow.

#strong[Where the injected knowledge lives is a constrained choice — its visibility is not.] It is tempting to rule that such constants must always be materialised as a typed input — a `lookup` or `enumeration` from Chapter 8 — never inlined as a bare `0.21`. That rule is too strong, because the input set is not always open. Some deployments are *sealed*: a fixed roster of slots — Open-M's seven, say — that admits no eighth input. A sealed set, or a stable-enough constant, can make inlining the only available or the most sensible placement; and Chapter 8 already sanctions exactly this, allowing `config` to be inlined at init time. So placement bends to two facts:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [], [*Input set open*], [*Input set sealed*],
  [*volatile* (rates, code maps)], [materialise as `lookup` / `enumeration` — best audit], [carry in an existing binding (e.g. `config`); else *inline + a "revisit on change" flag*],
  [*stable* (a fixed scalar)], [`enumeration` or `config`], [*inline* — a legitimate compile-time constant],
)

What does *not* bend is the discipline. Wherever the constant lands, it must be *surfaced* in the specification, *tagged* with a `basis` (`ai-world-knowledge` for a fact the model supplied, `user-requested` for one the prompt fixed), and *confirmed* by the user. The fault was never a `0.21` in the transformation; it is a `0.21` that is *silent* — unprovenanced, unconfirmed, and unfindable the day the rate changes by law. Inlining is honest when the constant is visible; materialising is worthless if no one reviews it. The placement is negotiable; the provenance is not.

#strong[Surface the interpretation; do not act on a guess.] "All the levels" has a closure the user left implicit, and the front-end must make it *explicit* rather than silently choosing a set. This is the priority-cascade rule of Chapter 8 — _surface, don't guess_ — moved to the front of the pipeline. The formalised request should read back the rates it intends to add, the field it will compute, the input it proposes to inject, and the questions it cannot answer alone (per line or per document? as of which date? which category takes which rate?), and it should wait for confirmation before a single correspondence is drawn.

That last move is borrowed, deliberately, from agentic coding assistants, which have already learned this front-end. The techniques transfer almost directly:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Assistant technique*], [*Request-formalisation analogue*],
  [plan, approve, *then* act], [emit the spec as a plan; confirm before the proposal runs],
  [clarify what is underspecified], [ask the per-line / as-of-date / category questions],
  [gather context before editing], [interpret the prompt *against* the loaded schemas and current mapping, never in a vacuum],
  [decompose into discrete steps], [split "add VAT" into field, lookup, derivation, summary],
  [augment with tools / knowledge], [recall the rates and place them as a provenanced constant],
  [expand a terse instruction], [the "build a prompt first" move: a one-line request becomes a full, grounded specification],
  [verify after acting], [re-run coverage and validation; confirm still schema-complete],
  [reversible, auditable edits], [every injected fact carries a `basis`; the spec is a diffable artifact the user can correct],
)

The unifying move is the first one: turn a vague wish into an *explicit interpretation the user signs off on*. That is plan-mode by another name, and it is the same validate-gate the next chapter argues for — relocated to the very front, so that the request a model is finally asked to satisfy is one a human has already agreed to.

== Keeping It Honest

One discipline governs both roles and is worth stating once, plainly. Every AI step — the narrow assists and the broad proposal alike — is *targeted* (asked a specific question), *grounded* (given the structural facts, never raw schemas alone), and *recorded* (its output tagged with `basis` so a human knows what rests on inference). The pipeline is deterministic-first throughout: AI is consulted only where deterministic methods provably run out. And the proposal is a *proposal* — it is validated against the contract and reviewed before it is allowed to become a mapping. Nothing a model produces, in either role, is trusted without a check.

This is the design-time discipline of the next chapter made concrete. There, AI's place is argued in principle — author, never executor. Here, the authoring itself is laid out as a workflow: AI prepares the analysis and proposes the mapping, in two clearly separated roles, while deterministic analysis carries the structure and validation guards the result. The output is a formal mapping a human confirms. AI made it fast; it did not make it law.
