= Proposing a Mapping: Two Roles for AI

The previous chapters have built, piece by piece, everything needed to ask a model for a mapping: typed inputs, a schema graph, a scored correspondence set, output-field kinds, inferred functions. This chapter assembles those pieces into the concrete workflow that produces an AI mapping *proposal* in Message Contract mode — and, in doing so, makes explicit a distinction the earlier chapters left implicit. "AI helps with the mapping" hides two very different jobs. AI is used *within the analysis*, to prepare; and AI is used *for the proposal*, to author. Separating those two roles is the key to using AI both safely and well, and it is the subject of this chapter.

== The Two Roles

Picture the moment a model is asked to help. There are, in fact, two such moments, and they are not alike.

#strong[Role one — AI as assistant in the analysis (prepare).] Long before any mapping is drafted, the analysis runs into small, local questions that deterministic rules cannot settle: is this bare `customerId` an implicit foreign key? which branch of this choice is the intended target? does `netDue` denote an aggregate? is `clientRef` the same thing as `customerId`? Each is a narrow question about a single node, with a checkable answer, and each is exactly where a model's world knowledge earns its keep. Used this way, AI does not write the mapping; it *sharpens the picture the mapping will be drawn from*. Its answers feed back into the typed inputs and the correspondence set, each tagged `ai-inferred` so its provenance is never lost.

#strong[Role two — AI as author of the proposal (propose).] Once the analysis is complete, there is a second, larger request: given everything now known, draft the mapping. The model receives the typed inputs, the scored correspondences, the output-field kinds, and the explicit list of gaps, and it returns a transformation — filling the derivation gaps, resolving the residual ambiguities, and drafting idiomatic UTL-X. This is one request, about the whole mapping, not many requests about individual fields.

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
  [8 · propose], [a draft mapping in idiomatic UTL-X], [role two — the proposal],
  [9 · ground & validate], [a verified mapping; gaps flagged for review], [no AI — verification],
)

The shape to notice is that AI appears at the *edges* of the analysis (role one, sharpening individual facts) and once at its *culmination* (role two, drafting from the whole), with deterministic work — graph construction, scoring, assembly, validation — carrying everything between. The pipeline is deterministic-first; AI is a targeted enrichment at the two points where it is genuinely needed, never a general overlay.

== Why This Order Is Forced

The nine steps are not a matter of taste; they are a dependency chain, and almost none of them can be reordered, because each consumes what the one before it produced.

Typing comes first because it is the *gate*. Until an input's role is known, there is no deciding whether to build a graph for it at all — and handing a name–value parameter set to a structural matcher (step 4) is meaningless. Type, then decide what to do with the type. The graph (step 2) must precede structural alignment (step 4) for the plainest of reasons: alignment is reachability *over* the graph, and there is nothing to traverse until the graph exists.

The load-bearing edge is the next one. Structural alignment (step 4) must precede name refinement (step 5), never the reverse, because names are a tie-breaker *among structurally compatible candidates* — never a way to promote a structurally incompatible one. The structural candidates must be in hand before a name is allowed to choose between them. Reverse these two steps and the most common, least visible error in all of mapping — a confident match on a shared name between unrelated fields — walks straight in the front door.

Output analysis (step 3) is the one step with slack. It reads only the output contract, so it depends on nothing upstream and may run in parallel with steps 1 and 2. But it cannot be skipped or deferred past *function inference* (step 6), because a mapping class is the product of an output field's *kind* and the *availability of a source* for it; neither factor alone fixes the construct, so both must be ready before inference can name it.

From there the order is strict again, and for the same reason each time. Function inference (step 6) needs the correspondences of steps 4–5 and the output kinds of step 3. Assembling the request (step 7) needs the completed analysis, because the analysis *is* the context — there is nothing to assemble before it exists. The proposal (step 8) needs the assembled request, which is the entire point of preparing: a grounded request, not a cold one. And validation (step 9) needs the proposal, because one can only check a thing that has been produced.

The payoff of this is a guarantee, not merely tidiness. Because every step is fed only by completed earlier steps, the pipeline is a directed acyclic graph: it runs once, forward, with no cycles and no hidden back-channels. And where it places its AI calls — at the semantic edges of steps 1 through 5, and at the single proposal of step 8 — is *determined by the dependencies*, not chosen for convenience. To the reader who asks why the steps run in this order, every arrow gives the same one-word answer: *dependency*.

== Why Preparing Beats Asking Cold

The temptation, and the industry default, is to skip all of this: hand a model two raw schemas and the instruction "write the mapping." That is the single-prompt approach of Chapter 11, and it fails the same three ways — it enumerates instead of using the language, it flattens the inputs, and, worst of all, it asks the model to re-derive the structural skeleton it is *worst* at while doing nothing with the structure that is *known*.

Preparing the request inverts this. The deterministic analysis builds the structural skeleton — the typing, the foreign-key joins, the cardinalities, the copies — which it can do reliably and cheaply. The model is then asked only to supply what structure cannot: the semantic bridges and the derivation expressions. This is the principle the correspondence set was designed for in Chapter 7 — *structured context with targeted gaps* rather than an open-ended schema pair. The proposal arrives grounded (it builds on a skeleton known to be sound), scoped (the model fills gaps, it does not invent the frame), and checkable (every gap it closes is a discrete, reviewable claim).

The role-one assists compound this advantage. By resolving the implicit foreign keys and bridging the names *during the analysis* — before the proposal request is even assembled — they hand the proposer a cleaner context with fewer unknowns. Preparation is not overhead before the real AI call; it is what makes the real AI call small, grounded, and good. The better the preparation, the less the proposer must guess, and the less there is to guess, the more the proposal can be trusted.

== Seeding from a Human Specification

There is a richer grounding still, and it predates the pipeline by decades. In a great many enterprise integrations the mapping was *already written down by a person* before any tool was asked to help. A business analyst, handed the fixed output contract — an SAP RFC, an IDOC, a partner's invoice schema — produces a mapping document, almost always a free-format spreadsheet: source field beside target field, a rule or a lookup in the next column, a note where the logic is subtle. The developer then writes the transformation with that spreadsheet open as a reference. The document never executes; it is not an input to the mapping in the run-time sense. But it is a treasure for the *authoring* of one.

The reason is precise. Strip away the format and the analyst's spreadsheet *is* a correspondence set — an informal, hand-authored draft of the very artifact Chapter 7 sets out to compute. And it carries exactly the part the deterministic machinery is weakest at: the *semantic* correspondences. That `KUNNR` becomes `CustomerId`, that a status code is translated through a named crosswalk, that two source fields concatenate into one target — these are domain facts no schema graph encodes and no structural matcher can recover. The analyst supplied them by hand, from knowledge of the business. Seen this way the spreadsheet is not documentation to be admired and set aside; it is the highest-value grounding a proposer can be given, because it pre-supplies the semantic bridges that role one would otherwise have to *guess*.

Two distinctions keep this honest. First, the specification is *not* a run-time input, and must not be confused with one. A lookup table — say, an SAP-to-Siebel user-id crosswalk — is reference *data*: it flows at execution, joined by key, in the binding compartment of Chapter 6. The analyst's spreadsheet is reference *specification*: it lives entirely at design time, grounding the analysis, and is gone before a message ever flows. Both are "reference," on different planes; collapsing them re-imports the very confusion Chapter 3 was built to prevent.

Second, the spreadsheet is *unformalised*. It is free text in merged cells, with a column convention only its author fully knows — and by the precondition of the Introduction, unformalised knowledge cannot be used mechanically until it is parsed into identifiable parts. So a spec does not enter the pipeline raw. It enters through a role-one step that *formalises* it: extract structured `source → target` correspondences from the free-format document — an extraction task a model is well suited to. Each extracted pairing is recorded with `basis: analyst-spec` and then run through the same gate as any other correspondence: *validate it against the live schema*. Does the named target field exist? Is its type compatible? Is the named lookup present? Where the answer is no, the entry is a *stale-spec flag*, not a mapping — the spreadsheet supplies intent, the schemas supply truth, and the pipeline reconciles the two. This is the same validate-before-trust discipline the next chapter argues in general, applied to a human's draft.

#strong[The analyst is a role, not a person.] This is the unifying point, and it is what keeps the feature an *addition* rather than a fork in the pipeline. Authoring the semantic correspondences is a job the pipeline always needs done; a human analyst with a spreadsheet is merely one way to fill it. In the common case there is *no* spreadsheet — the integration is new, or the analyst's document was never written — and then AI itself plays the analyst, generating the same semantic correspondences from the schemas and its world knowledge, recorded at the lower confidence of `ai-inferred` (this is precisely the name-refinement of step 5). When a specification *does* exist, nothing about the pipeline changes except the provenance and the confidence: AI's job shifts from *inventing* the semantics to *extracting and validating* documented ones — a smaller, safer task. The correspondence set is indifferent to which hand authored its entries; it only records, in `basis`, whose they are.

This is not a new idea so much as an old one returning. The Clio system of Chapter 4 took, as its input, *user-supplied value correspondences* — a human's pairings of source elements to target elements — and derived the full mapping from them using the schema's keys and structure. The analyst's spreadsheet is that input, arriving in the wild: messier, free-format, and decades-deployed. "A human draws the correspondences, the system completes them" is the founding move of schema mapping; the only modern additions are that a model can now *read* the human's draft out of an Excel sheet, and that the draft must be *validated* against schemas the analyst may not have re-checked.

== Keeping It Honest

One discipline governs both roles and is worth stating once, plainly. Every AI step — the narrow assists and the broad proposal alike — is *targeted* (asked a specific question), *grounded* (given the structural facts, never raw schemas alone), and *recorded* (its output tagged with `basis` so a human knows what rests on inference). The pipeline is deterministic-first throughout: AI is consulted only where deterministic methods provably run out. And the proposal is a *proposal* — it is validated against the contract and reviewed before it is allowed to become a mapping. Nothing a model produces, in either role, is trusted without a check.

This is the design-time discipline of the next chapter made concrete. There, AI's place is argued in principle — author, never executor. Here, the authoring itself is laid out as a workflow: AI prepares the analysis and proposes the mapping, in two clearly separated roles, while deterministic analysis carries the structure and validation guards the result. The output is a formal mapping a human confirms. AI made it fast; it did not make it law.
