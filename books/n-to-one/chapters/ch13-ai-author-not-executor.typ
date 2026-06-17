= AI: Author, Not Executor

No book written in this decade about data transformation can avoid the question, so let it be met directly: will artificial intelligence replace integration? The confident answer heard at conferences is yes — that a sufficiently capable model will simply *do the mapping*, taking data in at one end and producing the desired shape at the other, and that the whole apparatus of schemas, correspondences, and formal mappings is about to become a quaint relic. This book takes the opposite view, and the reason is not technophobia. It is that the claim confuses two acts that the preceding chapters have worked to keep apart: *authoring* a mapping and *executing* one. AI is poised to transform the first. It has no business doing the second.

== What AI Changes, and What It Does Not

It helps to be precise about what is actually being claimed. AI changes *who writes a mapping, and how fast*. A task that took an integration developer a morning — read two schemas, find the correspondences, write the transformation — a model can now draft in seconds. That is a genuine and large change, and the rest of this chapter is about how to use it well.

What AI does not change is *what a correct transformation is*. An invoice's total must still be the sum of its line amounts; a product code must still resolve to the right product; a required field must still be filled or honestly flagged. These are properties of the mapping, not of the tool that wrote it, and they are exactly as demanding whether a human or a model authored the result. The integration problem — move data between systems correctly, verifiably, and repeatably — is untouched by who holds the pen. What a faster author buys is *speed of authorship*, not a dispensation from correctness.

So the interesting question is not "can AI map?" but "where, in the life of a mapping, does AI belong?" The three-times distinction of Chapter 3 answers it.

== AI at Design Time: The Right Place

At design time, AI is not merely acceptable; it is powerful, and for three reasons that the earlier chapters have already laid down.

First, design-time AI operates on *metadata, not live data*. It reads schemas, the correspondence set, the typed inputs — the shape reading of Chapter 2 — and never touches a customer's actual order. The blast radius of a mistake is a proposed mapping a human will review, not a corrupted message in production.

Second, its output is *recorded and frozen before it runs*. An AI proposal enters the correspondence set tagged `ai-inferred` (Chapter 7), is reviewed where it matters, and becomes part of a formal mapping that is versioned and testable. The model's cleverness is captured as an artifact, not exercised live.

Third, it is aimed at exactly the points where determinism runs out. The pipeline of Part III is *deterministic-first*: schema typing, graph construction, structural alignment, and most function inference are computed without an oracle. AI is invoked only at the genuine semantic gaps — bridging `customerId` to `clientRef`, resolving a choice group, proposing the expression for a derivation gap — and the deterministic machinery grounds and validates whatever it returns. This is the multi-step design the book has built: analyse the inputs, analyse the output, and use prompt engineering to steer the model toward UTLX's family of grouped functions — `map`, `groupBy`, `reduce`, the `xxxBy` operators — so that what it drafts is idiomatic and checkable rather than a wall of field assignments. AI proposes; the formal machinery disposes.

There is a name for this shape, older than computing. A model's contribution — a bridged name, a resolved choice, a drafted derivation, an entire proposed mapping — is a *conjecture*: a hypothesis about how the inputs satisfy the contract, advanced on evidence but not proven by it. This is the same fact-versus-hypothesis line the correspondence set draws with its `basis` field (Chapter 7); here it is paid out as a method. The deterministic machinery that grounds and validates the conjecture is the *refutation*: the typecheck, the coverage count, the dry run, the structural facts that a false conjecture cannot survive. What withstands the test is confirmed and frozen into the mapping; what does not is flagged for a human, not run. Casting design-time AI as conjecture-and-refutation is the precise reason it is safe — a hypothesis tested before it executes is a wholly different thing from a guess that executes untested. The `basis` field is where each conjecture's evidence is recorded; the gate later in this chapter is where it is tried.

== The Refutation Is a Gradient

The loop has a part the framing so far has named but not weighed. A real generator does not propose once; it proposes, validates, and — where validation fails — feeds the errors into a fresh, sharper prompt and proposes again, repeating to a budget before it either succeeds or surrenders the best it has. This is the self-correction Chapter 11 admitted as a *safety net*: conjecture-and-refutation, run in a loop. What governs whether the loop *converges* is easy to miss and decisive — the model can repair only what the refutation tells it is wrong. The feedback is not a verdict; it is a *gradient*. A refutation that says only "invalid" gives no direction, and the loop wanders until its budget runs out; one that says "`orderTotal` expects a number, your expression yields a string, and this field is a derivation — a sum — you have written as a copy" points straight downhill, and the loop converges in a step or two. Feedback quality *is* the convergence rate, and a generator that leaves it to chance has left its outcome to chance.

The strength of that gradient has two preconditions. The first is the *reach* of the validator, because a loop converges only to the errors its validation can see. A validator that checks merely that the transformation parses and runs on one sample will terminate, satisfied, on a mapping that is *instance-adequate but schema-unsound* (Chapter 3) — a confident false success. Maximal feedback presupposes maximal *validation*, in layers: syntax, then types against the contract, then schema-completeness — is every required field covered? — then conformance of the produced output to the schema, then adequacy on sample data. The loop can chase only the correctness the validator is able to refute.

The second precondition is the *form* of the feedback, where a handful of levers separate a gradient from a fog:

- *Layer it and fail fast.* Report the failing layer's errors and suppress everything below it — type errors beneath a syntax error are misleading noise.
- *Localise to the correspondence, not the line.* "`lineItems[*].productName` is uncovered" is a direction; "error at line 42" is a riddle. The coverage artifact of Chapter 7 already carries this mapping.
- *Enrich the error with intent.* This is the reversed prompt of Chapter 12 turned on the failure: the engine supplies the deterministic fact ("expected number, got string"); the analysis supplies the meaning ("this field is a `sum`; the source is typed string and needs a cast"). The engine says what broke; the correspondence set and the inferred function of Chapter 10 say what it should have been.
- *Deduplicate to root causes, and lead with leverage.* One wrong strategy throws fifty leaf errors; collapse them to the single root and surface the structural fix first, because fixing the shape fixes the fifty.
- *Re-ground the repair.* Hand the failing slots their context again — correspondence, source and target types, inferred function — rather than appending a bare error string, or the cold-prompt failure of Chapter 12 returns at repair time.
- *Give the loop memory.* Feed the diff between iterations — fixed three, broke one, two remain — so it does not oscillate or re-propose a rejected fix.

The budget should watch the gradient, not the clock. The signal to stop is not a fixed count of iterations but a *flat* gradient: when the deduplicated, root-cause error set stops shrinking, the residue is almost certainly beyond the loop's reach — a genuine derivation gap, a missing input, an ambiguous contract — and further passes merely spend tokens. The honest move then is to escalate, and to escalate *well*: present not the raw partial transformation but the precise residual gaps — the unfilled slots and why each resisted — so a human inherits a scoped, named problem rather than a haystack. A loop that knows how to stop turns a failure into a handoff.

Two cautions keep this from misfiring. Maximal feedback is maximal *signal*, not maximal volume: a two-hundred-line error dump or a cascade of symptoms dilutes the gradient as surely as silence, which is why root-cause and localisation matter more than sheer completeness. And the deepest optimisation is to need the loop less — Chapter 11's insistence that self-correction is a safety net, not the engine, means a loop grinding through ten iterations is usually evidence that the *first* proposal was under-prepared, not that its feedback wants more tuning. Both can be made precise, because the loop has a measurable loss: error-reduction per iteration, and iterations to success or to stall. Feedback is good exactly when each pass strictly shrinks the root-cause error set; when it does not, the fault is in the refutation, or the validation beneath it, not in the model being refuted.

== AI at Run Time: The Wrong Place

Now the other act. To put AI on the hot path — to have a model read each incoming message and emit the transformed result directly — is to surrender, one by one, every property that makes integration trustworthy.

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Property a formal mapping has*], [*What AI-as-runtime-transformer gives instead*],
  [determinism — same input, same output], [variation: the same message may map differently twice],
  [reproducibility and audit], [an output no one can re-derive or explain field by field],
  [bounded correctness — copy, compute, or flag a gap], [hallucination: a plausible, confident, wrong value],
  [throughput and predictable cost], [an LLM call per message — orders of magnitude slower and dearer],
  [testability against a contract], [behaviour that cannot be pinned down or regression-tested],
  [a closed data path], [each message sent to a model — egress, and prompt-injection surface],
)

None of these is a tuning problem to be solved by a better model. They are intrinsic: a system whose output is *generated* rather than *computed* cannot be reproduced, audited, or guaranteed, because generation is not a function. Integration is a trust discipline — it carries payments, prescriptions, shipments — and a transformation you cannot reproduce or explain is not acceptable however clever it is. The risk is real and, this chapter argues, unnecessary. Everything AI is good at here can be had at design time, where its output is frozen into something that *can* be trusted.

== The Hard Case: Unknown Metadata at Run Time

There is, however, a case the book's whole edifice has quietly assumed away, and intellectual honesty requires facing it. Every chapter so far has presumed that the metadata — the schema — is *known at design time*. The analysis, the typing, the correspondence set all reason over schemas that exist before any message flows. What happens when a message arrives whose shape no one anticipated, carrying data whose schema is unknown?

Begin with a careful observation, because it is easy to overstate. Unstructured data is not worthless — an email, a scanned invoice, a paragraph of free text all carry meaning a person reads at a glance. What such data is not, until it is structured, is *mechanically reasoned over*: a deterministic engine cannot tell that this field is the order total and that one the customer reference, and so has nothing to compute against. The classical discipline insists on schemas up front not because unstructured data is valueless, but because a schema is the precondition for the *formal, checkable* treatment the rest of this book is about. (It follows that the position of this chapter is scoped, not absolute: it holds wherever a formal mapping is *possible* and the domain demands *trust*. For low-stakes, high-variety extraction with a human in the loop — pulling a few fields from messy text no one will ever schematise — a model reading each instance directly may simply be the pragmatic tool. The argument below is about systems that must be auditable, which is most of integration, not all of computing.)

AI changes this precondition, and that is its genuinely new contribution. A capable model can *infer metadata from an instance* — look at an unfamiliar document and recover its likely structure, types, and meaning. This is what an "instance transformation" really is: not a model transforming values by intuition, but a model *recovering the schema* that was missing, turning the uninterpretable into the interpretable. The unknown becomes known.

That recovery poses the chapter's central question sharply. When new data and new metadata arrive together at run time, should AI:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*(a) Transform the values directly*], [*(b) Synthesise a mapping, then execute it*],
  [the model reads the instance and emits the output], [the model infers the schema, writes a formal mapping to the known output contract, and the engine runs that mapping],
  [generation on the hot path], [authoring on the hot path; execution still formal],
  [forfeits every property in the table above], [keeps every one of them],
)

== Synthesise, Then Execute

The answer is (b), and it rescues the whole framework. Faced with novel data, the move is not to let AI *be* the transformer but to let it *write* a transformer: infer the schema from the instance, synthesise a formal mapping against the output contract that is still known and fixed, and hand that mapping to the engine to execute deterministically. The mapping is the artifact; AI is its author; the engine remains its only executor.

This is not a free lunch, and the value of formality must be stated exactly. Moving the work from *the values* to *the mapping* does not, by itself, make the result correct: the first message of a novel shape is still processed by an artifact no human has reviewed. What a formal mapping buys is not that it runs the same way twice — though it does — but that it can be *checked before it runs*. So the pattern carries a gate: synthesise, then *validate the mapping against the known output contract* — typecheck it, dry-run it, confirm its coverage — and only then execute; if it will not validate, the message is quarantined for a human rather than processed on a guess. Determinism makes the mapping reproducible; *validation* is what makes it trustworthy, and the real prize of synthesising a mapping rather than generating values is precisely that there is now an object to validate. One corollary: because synthesis is itself non-deterministic, the synthesised mapping must be *persisted with the message it served*, so the audit trail records not merely "message X was transformed" but "message X ran under mapping M, synthesised at time T" — reproducibility recovered at the one boundary, the synthesis step, where it would otherwise leak.

The decisive reframing is temporal. "AI does the mapping at run time" sounds like a violation of the design/run boundary. It is not — it is a *compression* of it. Authoring and executing remain two distinct acts even when only milliseconds separate them. A mapping synthesised a second ago is still a formal mapping: inspectable, testable, reproducible, and — crucially — *cacheable*. The first message of an unforeseen shape pays for one act of authorship; every subsequent message of that shape reuses the standing mapping, deterministically, with no model in the loop. What began as a one-shot synthesis is promoted to an ordinary, frozen transformation. The design-time boundary of Chapter 3 does not shatter under novel input; it collapses in time and then re-forms.

Two conditions decide whether this is engineering or wishful thinking. The first is *recurrence*. Synthesise-once-then-cache pays off when unforeseen shapes *repeat* — the first message funds an authoring the next thousand reuse for nothing. For input that is genuinely one-off, no two alike, there is nothing to amortise, and the pattern quietly degrades to per-message generation with an extra artifact in the middle; that is a signal to push the problem back to design time, not to synthesise forever. The second is *operational*: runtime synthesis puts a model on the critical path, coupling the engine's latency and availability to it. A system that must keep running when the model is slow or absent needs a fallback — dead-letter the unrecognised message to a design-time queue rather than block the pipeline on an oracle. On-the-fly synthesis is a powerful reach-extender; it is not a licence to make every message wait on a model.

This is why the assumption the book made — metadata known at design time — can be relaxed without abandoning anything. The honest restatement is: *metadata known by execution time, even if inferred moments before.* AI does not let us escape the need for metadata; it lets us *manufacture* it when it was missing. And the instant the metadata exists, the entire formal apparatus of this book applies unchanged — typing, alignment, function inference, a correspondence set, an executable mapping. AI extends the reach of the theory to data it could not previously touch; it does not overturn the theory.

== The Place of AI

So the position, stated plainly. AI will not replace integration, because integration's hard core — correctness that can be reproduced and audited — is not something a generative process can provide on the hot path. AI will, however, change integration profoundly: it will make mappings far faster to author, and it will extend formal mapping to data whose schema arrives unannounced, by recovering that schema on the fly. In both roles it is an *author*. In neither is it the *executor*.

The discipline of the preceding chapters survives intact, and is in fact what makes AI usable rather than dangerous. The formal mapping remains the unit of trust — the thing that is reviewed, recorded, tested, and run. AI proposes it, sometimes in a morning's work compressed to a second; the engine, deterministic and auditable, disposes. From many inputs, one output — and whether a human or a model drew the lines, the mapping that runs is still the disciplined, formal object this book has been about from the first page.
