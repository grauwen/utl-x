= The N:1 Mapping Problem

Most writing about data transformation quietly assumes a tidy shape: one source document, one target document, a function between them. That assumption is comfortable and almost always wrong. Real integrations converge. Several inputs — of different kinds, playing different roles — must be folded into a single output that satisfies a contract you do not own. This chapter defines that problem precisely, because everything else in the book is an answer to it.

== One Output, Many Inputs

Call it _N:1 mapping_: $N$ inputs in, one output out. The output is the privileged party. It is fixed — a schema someone else published — and the entire job is to satisfy it. The inputs are the variables. They differ not just in format but in *role*: one carries the primary data, another is a reference table joined by key, a third is a set of environment constants injected at run time.

Consider a concrete case that runs through the whole book. An order-management system emits a purchase order. A downstream system requires an invoice. Between them:

- a *payload* — the purchase order itself, rich and nested: a header, customer details, and a repeating group of line items;
- a *lookup* — a product catalogue, used only to expand each line's product code into a name and description;
- a *prior step* — the output of an earlier pipeline stage that already validated and enriched the order (its approval status, say);
- *configuration* — a few name–value pairs: the billing region, a correlation id, a tenant identifier.

Four inputs. One invoice. The invoice's `lineItems` come from the payload's lines, but each line's `productName` comes from the catalogue; its `orderTotal` appears in *none* of the inputs and must be computed; its `status` comes from the prior step; its `billingRegion` is a constant. No single input is "the source." The output is assembled from all four, each contributing in a structurally different way.

== Why N:1 Is Not Just 1:1 Repeated

The naive view is that an N:1 mapping is simply several 1:1 mappings stacked together — source each output field from whichever input happens to have it. That view fails for three reasons, and naming them frames the rest of the book.

#strong[The inputs are not symmetric.] A 1:1 framing treats every input as an equal candidate source for every output field. But a lookup table is not a co-equal source; it is a dimension joined by a foreign key. Treating the catalogue as "just another place to find fields" misses that the relationship between payload and catalogue is a *join*, with a key, producing exactly one UTLX construct — and the wrong framing will never reach for it.

#strong[Some outputs have no source at all.] `orderTotal` is not present anywhere; it is a *derivation* — a sum over the line items. A field-matching view reports it as an unmatched gap or, worse, as matched to some unrelated numeric field. Neither is correct. The output field is real and producible; it simply requires computation, not copying.

#strong[The shape of the answer varies by sub-problem.] Part of the mapping is a pass-through (copy the header), part is an array transform (map the lines), part is a join (expand product codes), part is an aggregation (sum the totals), part is a constant (stamp the region). A single uniform strategy — "fill every field from a source" — produces verbose, idiomatically poor transformations because it cannot bend to these different shapes.

These three failures — asymmetry, derivation, and shape — are the reason a *theory* is worth having. Each is addressed head-on in later chapters: input roles in Chapter 8, derivation and output analysis in Chapter 9, and mapping shapes in Chapter 11.

== The Inputs Are Not Homogeneous

The inputs of an N:1 mapping are not one uniform thing, and the difference between them organises much of what follows. Some inputs are *structurally rich* — they have nesting, keys, and foreign-key relationships, and they participate in genuine structural mapping. Others are *flat name–value parameters* — constants and run-time variables injected at mapping time, with no structure to align, closer to what a programming language calls parameters than to a source schema.

This split — structural sources versus injected parameters — is the first hint of the architecture the rest of the book builds. The structural inputs feed the matching and alignment machinery of Part II; the parameters bypass it entirely, recorded as direct value bindings. A real production pipeline makes this concrete, with a fixed set of named input slots of each kind, and that concrete model is taken up in Chapter 8, where inputs are typed by role and structure. For now it is enough to see that "the inputs" are plural in *kind*, not only in number.

== What Must Be True of the Output

The output's privilege — being fixed — is also a discipline. A correct N:1 mapping is not merely one that runs without error; it is one that *covers* the contract. Every required field of the output must be accounted for: copied from a source, derived by computation, looked up from a reference, supplied as a constant, or — honestly — flagged as a gap that no input can satisfy.

This gives us our first definition of correctness, which the rest of the book sharpens:

#block(
  fill: luma(245), inset: 10pt, radius: 4pt, width: 100%,
)[
  A mapping is *schema-complete* when every required output field has a defined provenance — copy, derivation, lookup, constant, or an explicit, acknowledged gap.
]

Note what this definition does *not* say. It says nothing about real data. A mapping can be schema-complete and still fail on a particular instance — a lookup that finds no matching row, a number that overflows. That second kind of correctness is a different question, asked of real values rather than schemas, and it belongs to a different mode of the system entirely. Separating those two questions — completeness against the schema, adequacy against the data — is so important that the next chapters give each its own foundation: the Universal Data Model in Chapter 2, and the two modes in Chapter 3.

== The Shape of the Answer

The remainder of the book builds, piece by piece, an answer to the N:1 problem:

- Represent every input and the output in *one* model, so that "all formats" is not a slogan but a fact (Chapter 2).
- Separate reasoning-about-schemas from transforming-data into two clean modes (Chapter 3).
- Treat each schema as a *graph* of entities, keys, and foreign keys (Chapter 4).
- Borrow the discipline of *schema matching* to find candidate correspondences (Chapter 5).
- Make the mapping a *formal object* — a set of source-to-target dependencies — that can be reasoned about (Chapter 6).
- Record the analysis as a scored *correspondence set* (Chapter 7).
- *Type* the inputs by role and the outputs by derivation (Chapters 8 and 9).
- *Infer* the UTLX function each correspondence needs (Chapter 10).
- Generate the transformation *strategy-first*, choosing the shape before filling the fields (Chapter 11).

None of these steps is new to computer science. The contribution is in assembling them into a single, coherent account of how many inputs become one output — and in grounding that account on a data model and a mode distinction that actually ship.

One question is deferred on purpose. *Why* a single output — and how a system that genuinely needs two, such as an engine producing a payload and a dynamic configuration at once, decomposes without abandoning the discipline — is a design-rationale question that depends on the formal object of Chapter 6 and the pipeline of Chapter 8. It is taken up on its own terms in Chapter 14, _Why One Output?_, which a first reader may safely leave for later.
