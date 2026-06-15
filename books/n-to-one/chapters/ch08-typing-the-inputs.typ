= Typing the Inputs

Part II reasoned as though every input were a schema graph waiting to be matched. The inputs of an N:1 mapping are not so uniform. Before any matching can sensibly begin, each input must be *classified* — by the role it plays and by the structure it has — because the type decides whether an input enters the matching pipeline at all, and how. This chapter develops input typing, and grounds it in a body of practice older than schema matching: the classification of enterprise data itself.

== Why Type Before You Match

The argument is short and decisive. Running a structural matcher over a bag of configuration values is not merely wasteful; it is meaningless. A configuration set has no entities, no keys, no foreign keys — nothing for a graph matcher to align. Feeding it to the matcher produces noise, and noise that looks like signal is worse than silence. Conversely, a rich payload and a rich lookup *must* go through the full graph treatment, and treating them as interchangeable misses the join that defines their relationship.

So typing is a *gate*. It routes each input to the treatment its kind deserves: structurally-rich inputs into graph construction and matching, name–value inputs straight to bindings, schema libraries into shared-type resolution, registries into entity extraction. Get the gate wrong and every downstream stage inherits the error; get it right and each stage receives exactly the inputs it can use.

== The Role Taxonomy

An input's *role* is one of a small set:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Type*], [*Role*],
  [`payload`], [primary message contract — rich structure, keys, nesting],
  [`chain`], [a payload from a prior pipeline step — same structural class, temporal role],
  [`lookup`], [reference / enrichment data — structurally rich, but joined rather than driven],
  [`nvp`], [name–value pairs — flat, homogeneous; drives variable bindings],
  [`config`], [an nvp subtype — deployment constants, candidates for compile-time inlining],
  [`enumeration`], [a closed value set — drives validation, not field binding],
  [`shared-type-library`], [an import dependency — provides types to other schemas, no standalone role],
  [`meta-schema`], [a schema registry — requires entity extraction before use],
)

What is striking is that this taxonomy is not invented. It is, almost line for line, the classification that enterprise data management has used for decades.

== Grounding: Master-Data Classification

Master-data management divides enterprise data into a handful of kinds: *master data* (the durable business entities — customers, products), *transactional data* (the events — orders, invoices), *reference data* (the shared code lists), *metadata* (data about data), and *configuration* (the environment). Lay the role taxonomy against it and the correspondence is nearly exact:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Role*], [*Master-data class*],
  [`payload`, `chain`], [transactional data],
  [`lookup`], [master / reference data],
  [`enumeration`], [reference data (code list)],
  [`config`, `nvp`], [configuration],
  [`meta-schema`], [metadata (a registry)],
)

This is the strongest possible evidence that the taxonomy is well-grounded: it was arrived at from the mechanics of mapping, and it lands on the categories an entirely separate discipline reached from the mechanics of data governance. The two converge because they are describing the same thing — the kinds of role data plays in an enterprise — from two directions.

The `meta-schema` row deserves a note in the vocabulary of Chapter 2. If a schema is *metadata* — data about a class of data — then a registry of schemas is *meta-metadata*, sitting one rung higher on the ladder. A `meta-schema` input is therefore not data to map but metadata to *unpack*: its entities must be extracted before any of them can be typed as a payload or lookup in their own right.

== A Concrete Instance: the Open-M Inputs

Theory is easier to trust against a real example. UTL-X's production pipeline, _Open-M_, hands a transformation a fixed set of seven named inputs — and they sort, without forcing, into exactly the two camps the taxonomy predicts.

#figure(
  image("../diagrams/open-m-context.svg", width: 100%),
  caption: [The seven Open-M mapping-context inputs, typed by the taxonomy: two *structural* inputs enter the matching pipeline; five *name–value* inputs become bindings. The mapping is delivered in `SEPARATE` or `COMBINED` mode (Chapter 14).],
)

Two inputs are *structurally rich* and enter the matching pipeline: `$payload`, the current message, and `$step_window`, the payloads of the last few upstream steps — the `chain` of prior outputs. The other five are *flat name–value maps* and become bindings, never touching structural alignment: `$vars` (pipeline variables), `$headers` (envelope headers), `$shared` (cluster-wide variables), `$config` (the downstream component's static configuration), and `$global` (pipeline-wide properties). The split the taxonomy draws on paper is the split the engine draws in fact: two schema-bearing sources, five injected parameter sets.

A second detail in the figure looks ahead. The mapping's output is delivered in one of two modes — *separate*, the mapped payload alone, or *combined*, the payload and a dynamic engine configuration merged into a single document. Whether those two outputs should share one transformation or be kept apart is the question of Chapter 14; the engine, by offering both modes, simply leaves the choice open.

== The Weak Seam: `config` Versus `nvp`

The grounding also exposes the taxonomy's softest joint. Master-data classification puts `config` and `nvp` in one box — configuration — and structurally they are indistinguishable: both are flat maps of keys to scalar values. Any attempt to separate them by *shape* (homogeneous strings on one side, mixed scalar types on the other) is a thin and unreliable test, because the real difference is not structural at all. It is role and lifecycle — and, in the three-times vocabulary of Chapter 3, it is precisely the init/run boundary: `config` is bound once at *init time* and may be inlined; `nvp` is read per message at *run time*. No inspection of shape can recover that distinction reliably, because the difference is one of *time*, not structure.

The lesson generalises into a rule: *do not infer what should be declared.* The split between `config` and `nvp` is a property the platform or the developer knows and the schema does not encode. The typing engine should infer a single "binding" kind from structure and refine to `config` or `nvp` only from a declaration. Inference is for facts the structure carries; declaration is for facts it cannot.

== The Priority Cascade

Declared knowledge, where it exists, must win. The typing engine resolves an input's type through a strict cascade:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Priority*], [*Source of the type*],
  [1 — highest], [platform declaration (a fixed pipeline slot) — no inference run],
  [2], [an inline schema tag — user-declared, inference skipped],
  [3], [a transformation-header declaration — user-declared, inference skipped],
  [4], [structural classification tests — inference, the fallback],
  [5 — lowest], [unresolvable — surface an error rather than guess],
)

The inference tests at level 4 are the fallback, not the first resort. They exist for the case where nothing is declared: a schema arrives with no platform slot, no inline tag, no header. Then, and only then, structural observation decides — nesting depth, the presence of keys, the document root, the namespace relationship. And when even that cannot decide — a set of peer schemas with no entry point, say — the right answer is an *error*, not a guess. A typing engine that guesses under ambiguity manufactures false confidence; one that declines forces the ambiguity to the surface where a human can resolve it.

== Two Levels of Typing

Typing the input as a whole is only half the story, and the smaller half. The granular signal — the one that predicts mapping strategy — lives a level down, *inside* the schema.

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Level*], [*What it types*],
  [schema-level], [the input's role: `payload`, `lookup`, `nvp`, …],
  [node / graph-level], [the structure within: entity, key, reference (foreign key), containment, value],
)

The header–detail relationship of Chapter 4 — the order that contains and is referenced by its lines — is a node-level fact. It is not a new kind of input; the input is a payload. It is a relationship *inside* the payload graph, and it is what tells the mapper to reach for `map` and `groupBy`. A theory that types only whole inputs is blind to it. Typing must reach both levels: the role of the input, and the structure of its insides.

== The Structural Archetype

There is a useful third axis, orthogonal to role, that names the *shape* of a structurally-rich input:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Archetype*], [*Graph signature*], [*Predicts*],
  [single-record], [one entity, no repeating groups], [a field copy or spread],
  [flat-list], [one repeating entity, no nesting], [`map`],
  [header-detail], [a parent entity with a child collection by containment or foreign key], [`map` + `groupBy`],
  [star / snowflake], [a fact entity with foreign keys to dimension lookups], [`findBy` / `filterBy` joins],
  [deeply-nested], [depth three or more, mixed cardinalities], [composed `map` and spread],
)

This is the "kind of message" intuition made explicit, and it has its own pedigree: the fact-and-dimension vocabulary of dimensional modelling, and the weak-entity vocabulary of entity-relationship modelling. An IDOC order is a header-detail message; a transaction joined to reference tables is a star. The archetype is derived from graph topology, it is orthogonal to the input's role, and — crucially — it *predicts the strategy* before a single correspondence is scored. It is the structural cousin of the design patterns a schema serializer already tracks, lifted from serialization into mapping.

== Where the Type Lives

A final, architectural point. An input's type — its role, its archetype, its resolution provenance — is a property of the *schema*, decided at design time. It must be recorded where Chapter 2's discipline says schema facts belong: on the schema document, in a metadata envelope around the schema graph, never smeared across the data nodes of an instance. A datum in a parsed order has no "payload versus lookup" identity; that is the role of the input *slot*, not of any value flowing through it.

Recording the type on a new schema-document envelope — distinct from the runtime data model — has a decisive practical consequence: it touches nothing on the execution hot path. The data model the transformation produces is unchanged; the parsers, the engine, the serializers, the conformance suite all see exactly what they saw before. The type is additive, design-time, and carried by an object that did not exist until the analysis needed it. Typing the inputs costs the run-time nothing, which is precisely what Chapter 3's separation of modes demands.
