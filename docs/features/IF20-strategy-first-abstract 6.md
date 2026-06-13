# IF20: Strategy-First MC Generation — Abstract

## Overview

IF20 formalizes the strategy layer for Message Contract (MC) generation in UTL-X. The current `strategy.ts` is a name-heuristic stand-in that operates on element names as surface syntax. This is architecturally insufficient: names carry no structural or relational semantics, producing noise precisely at the junctions that matter most — FK-anchored joins, flattened repeating groups, and polymorphic subtypes.

## Core Design Thesis

A credible mapping strategy requires the **schema graph as its substrate**. Three axes, grounded in Rahm & Bernstein (2001), define the formalized approach:

### 1. Schema Graph + Keys/FKs (Clio-style Associations)

Following Clio’s foundational observation that schema mapping is graph reachability under integrity constraints, strategy derivation begins from the schema topology — not from names. PK elements define identity anchors; FK relationships define navigable association edges; cardinality signatures characterize repeating groups and optional subtrees. Structural candidate correspondences are derived from graph reachability, scored by type compatibility, cardinality match, FK pairing, and depth similarity.

### 2. Element-Level vs. Structure-Level Split

Schema matching decomposes into two distinct coverage layers:

|Level          |Question answered                                   |Signal source                                                 |
|---------------|----------------------------------------------------|--------------------------------------------------------------|
|Element-level  |Can this source element map to this target element? |Name similarity, type compatibility, value domain             |
|Structure-level|Can this source subtree satisfy this target subtree?|Cardinality, nesting depth, FK reachability, graph isomorphism|

The current `strategy.ts` operates exclusively at the element level. Structure-level analysis requires the schema graph to precede it — which is why IF20 is *strategy-first*.

### 3. MC (Schema) vs. Execution (Instance) as Distinct Analyses

These two modes must remain cleanly separated:

- **MC / schema-level**: Is this mapping structurally possible? What coverage gaps exist?
- **Execution / instance-level**: Given a concrete input, what does the transformation actually produce?

A mapping can be schema-complete but instance-incomplete, or instance-adequate but schema-unsound. Conflating the two — which name-pattern heuristics tend to do — produces false confidence in coverage analysis.

## Proposed Replacement: Four-Phase Strategy Pipeline

A schema typing phase (Phase 0) must precede all other work. The type of each input determines whether and how it enters the matching pipeline at all.

1. **Schema typing** — Classify every input schema by role and structural class before any matching begins. See [Schema Type Classification](#schema-type-classification) below.
1. **Graph construction** — Parse structurally rich inputs (`payload`, `chain`, `lookup`) into typed UDM schema graphs. Annotate nodes (PK, FK, required, optional, repeating, choice) and edges (containment, reference, sequence). Merge `include`-composed fragments; register `import`-composed dependencies as shared-type libraries.
1. **Structural alignment** — Derive candidate correspondences from graph reachability (Clio-style). Score by structural compatibility; produce a ranked correspondence set with confidence scores.
1. **Name refinement** — Apply name similarity as a tie-breaker *within* structurally compatible candidates only. Name heuristics never elevate a structurally incompatible candidate.

`nvp`, `config`, and `enumeration` inputs bypass Phases 1–3 entirely and are registered directly as bindings or constraint annotations.

-----

## Schema Type Classification

Before the strategy pipeline can run, every input schema must be assigned a **schema type**. This is Phase 0 and is a precondition for everything else. The type governs which pipeline phases apply and how the schema participates in the correspondence set.

### Type taxonomy

```
schema_type
├── payload              primary message contract; rich structure, FK/PK, nesting
│   └── chain            payload from a prior pipeline step; same structural class, temporal role
├── lookup               reference/enrichment data; structurally rich but role is join/enrich, not primary mapping
├── nvp                  name-value pairs; flat, homogeneous; drives variable bindings in the mapping
│   └── config           NVP subtype; deployment/environment constants; candidate for compile-time inlining
├── enumeration          closed value set / codelist; drives filter/validation logic, not field binding
├── shared-type-library  import dependency; no standalone role; provides types to other schemas
└── meta-schema          schema registry (EDMX, schema-of-schemas); requires entity extraction before use
```

Payload schemas may be expressed in any supported format: XSD, JSON Schema, TSCH (Frictionless Table Schema), or OSCH (EDMX/OData). The type classification operates on the UDM representation, not the source format.

### Pipeline participation by type

|Schema type          |Phase 0                      |Phase 1                  |Phase 2     |Phase 3 |Registered as                           |
|---------------------|-----------------------------|-------------------------|------------|--------|----------------------------------------|
|`payload`            |✅ classify                   |✅ graph                  |✅ align     |✅ refine|correspondence                          |
|`chain`              |✅ classify                   |✅ graph                  |✅ align     |✅ refine|correspondence (`tgd_class: enrichment`)|
|`lookup`             |✅ classify                   |✅ graph                  |✅ align     |✅ refine|correspondence (`tgd_class: lookup`)    |
|`nvp`                |✅ classify                   |❌                        |❌           |❌       |binding                                 |
|`config`             |✅ classify                   |❌                        |❌           |❌       |binding (compile-time candidate)        |
|`enumeration`        |✅ classify                   |❌                        |❌           |❌       |constraint annotation                   |
|`shared-type-library`|✅ classify                   |✅ merge into parent graph|❌ standalone|❌       |type dependency                         |
|`meta-schema`        |✅ classify → extract entities|✅ graph per entity       |✅ align     |✅ refine|correspondence per extracted entity     |

### Classification tests

Classification runs as an ordered sequence of tests. The first test that fires determines the type.

**Test 0 — Format-level meta-schema detection** *(before structural analysis)*

- Document root is `edmx:Edmx` → `meta-schema` (extract `EntityType` nodes, then re-classify each)
- XSD with no top-level `xs:element` declarations, only `xs:complexType` definitions → `shared-type-library`
- JSON Schema with only `$defs` / `definitions` and no top-level `properties` → `shared-type-library`

**Test 1 — Include vs. import composition**

- Same `targetNamespace` as referencing root (XSD `xs:include`, JSON Schema same-document `$ref`) → merge into root graph; do not classify as standalone
- Different `targetNamespace` (XSD `xs:import`, JSON Schema external URI `$ref`) → `shared-type-library`

**Test 2 — Flat test** *(NVP / config / enumeration candidate)*

All of the following must hold:

- Max nesting depth ≤ 2
- No FK/PK annotations
- No repeating groups (no arrays, no `maxOccurs > 1`)
- All leaf types are scalar primitives

If flat, discriminate further:

- All entries are homogeneous string→string pairs → `nvp`
- Entries are scalar but mixed types (string, integer, boolean) → `config`
- No value side; keys are the values of interest (codelist pattern) → `enumeration`

**Test 3 — Structural richness test** *(payload / lookup candidate)*

Any of the following:

- Nesting depth ≥ 3, OR
- At least one repeating group (`maxOccurs > 1` / JSON array), OR
- At least one FK/PK-like constraint (`xs:key` / `xs:keyref`, `$ref` chain, foreign key annotation)

→ candidate for `payload`, `chain`, or `lookup`. Discriminate further:

**Test 4 — Chain detection**

- Passes Test 3, AND
- Schema fingerprint matches a known prior target schema in the pipeline
  → `chain`

**Test 5 — Lookup detection**

- Passes Test 3, AND
- No FK relationship reachable *to* the target schema, AND
- Self-contained reference structure: one PK field + descriptor fields, shallow nesting (depth ≤ 3), no outbound FKs beyond internal cross-references
  → `lookup`

**Test 6 — Default**

- Passes Test 3, not chain, not lookup
  → `payload`

### Ambiguous cases

**Multi-root XSD suites** — a set of peer XSD files with no single entry point. Cannot be classified without an anchor. The UTL-X `%utlx` header declaration (`input: orders xsd`) acts as the disambiguation: the named input is the root; everything reachable from it is classified relative to that anchor. Without a declaration, the type engine must surface an error rather than guess.

**Chain identity** — chain detection (Test 4) requires a schema fingerprint registry of prior pipeline step outputs. On first run this registry is empty; all inputs default to `payload`. The registry is populated as the pipeline executes and MC analyses are persisted.

### Type annotation in the JSON correspondence set

Each input carries its resolved type in the `inputs` block:

```json
{
  "inputs": {
    "primary": {
      "schema": "schemas/sales-order.json",
      "format": "json-schema",
      "schema_type": "payload"
    },
    "chain": [
      {
        "schema": "schemas/processed-order.xsd",
        "format": "xsd",
        "schema_type": "chain",
        "chain_index": 1
      }
    ],
    "lookup": [
      {
        "schema": "schemas/product-catalog.json",
        "format": "json-schema",
        "schema_type": "lookup"
      }
    ],
    "bindings": {
      "global_config": {
        "schema": "schemas/global-config.nvp",
        "schema_type": "config"
      },
      "shared_vars": {
        "schema": "schemas/shared-vars.nvp",
        "schema_type": "nvp"
      },
      "pipeline_vars": {
        "schema": "schemas/pipeline-vars.nvp",
        "schema_type": "nvp"
      }
    }
  }
}
```

-----

## Key Design Constraint

Graph construction must operate on the **UTL-X Universal Data Model (UDM)** layer, not on raw format-specific ASTs (XSD, JSON Schema, Avro). The Rahm/Bernstein matchers must apply to UDM schema graphs to avoid four diverging per-format strategy implementations.

## Theoretical Foundations

IF20 does not invent new theory — it applies decades of established work in **schema matching & mapping / data exchange** to UTL-X’s MC generation problem.

### Taxonomy: Rahm & Bernstein (2001)

E. Rahm & P. A. Bernstein, *“A Survey of Approaches to Automatic Schema Matching”*, VLDB Journal, 2001.

The definitive taxonomy. Classifies matchers along two orthogonal axes:

- **Element-level vs. structure-level** — directly maps to IF20’s two-phase coverage split.
- **Schema-only vs. instance-based** — directly justifies the MC/Execution separation: MC analysis is schema-only + constraint-based; Execution analysis is instance-based.
- **Linguistic vs. constraint-based** — names are linguistic; PKs, FKs, cardinalities, and types are constraint-based. IF20 elevates the constraint-based signal above the linguistic one.

### Mapping Generation: Clio

- L. Haas, R. Miller, B. Niswonger et al., *“Clio Grows Up: From Research Prototype to Industrial Tool”* (SIGMOD 2005).
- L. Miller, L. Haas, M. Hernández, *“Schema Mapping as Query Discovery”* (VLDB 2000).
- L. Popa, Y. Velegrakis, R. Miller et al., *“Translating Web Data”* (VLDB 2002).
- R. Fagin, P. G. Kolaitis, L. Miller, L. Popa, *“Clio: Schema Mapping Creation and Data Exchange”* (ICDT 2009).

Clio’s core contribution: joins and nestings in a mapping are **derived from key and foreign-key associations in the schema graph**, not from field-by-field name matching. This is the formal grounding for IF20’s “2nd input is a lookup” intuition — a FK-reachable path in the schema graph corresponds directly to a join/lookup in the generated UTL-X transformation. The mapping is a derived artifact of the schema topology, not an independently authored artifact.

### Formal Object: Data Exchange tgds

R. Fagin, P. G. Kolaitis, L. Miller, L. Popa, *“Data Exchange: Semantics and Query Answering”*, PODS 2003 / Theoretical Computer Science, 2005.

Defines a **schema mapping** formally as a set of **source-to-target tuple-generating dependencies (tgds)** — logical sentences of the form “for every tuple in source satisfying condition X, there exists a tuple in target satisfying condition Y.” This is the formal object that IF20’s strategy layer is implicitly generating. Treating it explicitly as a tgd set (rather than an ad hoc name mapping) enables correctness reasoning: completeness, soundness, and chase-based evaluation.

### Composite & Structure-Aware Matchers

- **Cupid** — P. Madhavan, P. Bernstein, E. Rahm, *“Generic Schema Matching with Cupid”* (VLDB 2001). Structure-aware matcher that propagates similarity up and down the schema tree.
- **COMA / COMA++** — H. Do & E. Rahm, *“COMA — A System for Flexible Combination of Schema Matching Approaches”* (VLDB 2002). Composite matcher combining multiple individual matchers with configurable weights; directly models the multi-signal scoring IF20 requires.
- **Similarity Flooding** — S. Melnik, H. Garcia-Molina, E. Rahm, *“Similarity Flooding: A Versatile Graph Matching Algorithm”* (ICDE 2002). Graph-propagation algorithm for structural similarity; applicable to UDM schema graph alignment.

### Best Single Entry Point

A. Bellahsene, A. Bonifati, E. Rahm (eds.), *“Schema Matching and Mapping”*, Springer, 2011.

Surveys all of the above in a single volume. Recommended as the reference for anyone contributing to IF20’s strategy layer implementation.

-----

## Correspondence Score Persistence

### Format options

There is no single dominant standard. The main candidates are:

|Format                             |Origin                                        |Suitable for UTL-X?                                                             |
|-----------------------------------|----------------------------------------------|--------------------------------------------------------------------------------|
|**OAEI Alignment Format** (RDF/XML)|Ontology Alignment Evaluation Initiative, 2004|Academic reference; overkill unless publishing interoperably with ontology tools|
|**COMA internal XML**              |COMA/COMA++ tool                              |Inspiration only; tool-internal, not portable                                   |
|**tgd / GLAV notation**            |Data exchange theory (Fagin et al.)           |Formally precise; requires a tgd evaluator                                      |
|**JSON correspondence set**        |De facto in practical implementations         |✅ Recommended                                                                   |

### Recommendation: JSON correspondence set

For the MC layer — persisted alongside the UTL-X script, versioned in git — a lightweight JSON structure is the pragmatic choice:

- Git-diffable: score changes between schema versions are visible
- Carries **per-signal subscores** alongside the composite (critical for explainability and tuning)
- `basis` field distinguishes machine-generated from human-validated correspondences
- No external toolchain dependency

> **Key principle**: always store subscores, not just the composite score. A flat `0.87` is opaque. `{ structural: 0.91, type: 0.95, name: 0.61 }` shows *why* the match scored high and which signal to distrust if the mapping is wrong.

### Proposed JSON structure

The following example represents a correspondence set for a mapping from a `SalesOrder` JSON schema to an `Invoice` XML schema. It covers a high-confidence FK-driven join, a moderate structural match, a user-confirmed override, and a coverage gap.

```json
{
  "meta": {
    "utlx_version": "1.2",
    "generated": "2026-06-13T10:00:00Z",
    "source_schema": "schemas/sales-order.json",
    "target_schema": "schemas/invoice.xsd",
    "strategy": "clio-structural-v1",
    "status": "draft"
  },
  "correspondences": [
    {
      "id": "c001",
      "src_path": "SalesOrder.customerId",
      "tgt_path": "Invoice.clientRef",
      "relation": "eq",
      "composite_score": 0.87,
      "signals": {
        "structural": 0.91,
        "type_compat": 0.95,
        "name_similarity": 0.61,
        "fk_reachable": true
      },
      "basis": "fk-reachable",
      "phase": "structural-alignment",
      "notes": "Both are FK references to the Customer/Client entity; type string, cardinality 1:1"
    },
    {
      "id": "c002",
      "src_path": "SalesOrder.lines[*].productCode",
      "tgt_path": "Invoice.lineItems[*].itemRef",
      "relation": "eq",
      "composite_score": 0.79,
      "signals": {
        "structural": 0.85,
        "type_compat": 0.90,
        "name_similarity": 0.48,
        "fk_reachable": true
      },
      "basis": "fk-reachable",
      "phase": "structural-alignment",
      "notes": "Repeating group match; FK to Product entity on both sides; name similarity low but structural confidence high"
    },
    {
      "id": "c003",
      "src_path": "SalesOrder.orderDate",
      "tgt_path": "Invoice.issueDate",
      "relation": "eq",
      "composite_score": 0.94,
      "signals": {
        "structural": 0.88,
        "type_compat": 1.0,
        "name_similarity": 0.72,
        "fk_reachable": false
      },
      "basis": "user-confirmed",
      "phase": "name-refinement",
      "confirmed_by": "mgrauwen",
      "confirmed_at": "2026-06-13T11:23:00Z",
      "notes": "Date types match exactly; user confirmed semantic equivalence"
    },
    {
      "id": "c004",
      "src_path": "SalesOrder.lines[*].discountPct",
      "tgt_path": "Invoice.lineItems[*].reduction",
      "relation": "eq",
      "composite_score": 0.51,
      "signals": {
        "structural": 0.72,
        "type_compat": 0.80,
        "name_similarity": 0.22,
        "fk_reachable": false
      },
      "basis": "name-heuristic",
      "phase": "name-refinement",
      "notes": "Low name similarity; structural match plausible; flagged for human review"
    }
  ],
  "coverage": {
    "source_elements": 14,
    "target_elements": 12,
    "matched": 10,
    "unmatched_target": [
      {
        "path": "Invoice.vatNumber",
        "required": true,
        "gap_type": "no-source-candidate",
        "notes": "No structural or name match found in source schema; must be supplied as literal or derived"
      }
    ],
    "coverage_pct": 83.3
  }
}
```

### Field reference

|Field                      |Purpose                                                              |
|---------------------------|---------------------------------------------------------------------|
|`src_path` / `tgt_path`    |Dot-notation UDM paths; `[*]` denotes repeating group                |
|`relation`                 |`eq` (equivalence), `lt` / `gt` (subset), `approx`                   |
|`composite_score`          |Weighted combination of signals; `0.0–1.0`                           |
|`signals.structural`       |Structure-level score (cardinality, depth, graph reachability)       |
|`signals.type_compat`      |Type compatibility score                                             |
|`signals.name_similarity`  |Linguistic similarity (normalized edit distance / synonym match)     |
|`signals.fk_reachable`     |Boolean: was this match derived from a FK path in the schema graph?  |
|`basis`                    |`fk-reachable` | `name-heuristic` | `user-confirmed`                 |
|`phase`                    |Which pipeline phase produced this correspondence                    |
|`coverage.unmatched_target`|Target elements with no source candidate — the MC coverage gap report|

-----

## N-Input Schemas: The Open-M Complication

### What the theory assumes

Rahm & Bernstein and the matcher literature implicitly assume a **1:1 source/target pair**. The data exchange / tgd literature (Fagin et al.) is more nuanced: a schema mapping is formally defined as:

```
M = (S, T, Σ)
```

where S is the source schema, T is the target schema, and Σ is the set of tgds over them. Critically, S in a relational setting is already a **set of relations** — nothing in the formalism prevents S from being a union of multiple input schemas. The theory does not break; S simply becomes the union of all inputs, and the tgds still span source→target. The 1:1 assumption is a simplification of the literature, not a hard constraint of the formal machinery.

### Open-M input roles are not symmetric

UTL-X’s Open-M pipeline introduces N inputs that fall into structurally distinct roles:

|Input                  |Role                                                           |Theory analogue                              |
|-----------------------|---------------------------------------------------------------|---------------------------------------------|
|`payload`              |Primary data; full schema, all FK/PK semantics apply           |Standard source schema                       |
|`payload-1 … payload-N`|Previous step outcomes; chained pipeline results               |Source schema with temporal/pipeline ordering|
|Global config vars     |Name-value pairs, always present                               |Constants / environment — not a schema       |
|Shared variables       |Name-value pairs                                               |Constants / environment                      |
|Pipeline variables     |Name-value pairs; 6 fixed named inputs always present in Open-M|Constants / environment                      |

The name-value pair inputs are **not schemas in the matching theory sense**. They carry no structure to align, no FKs, no hierarchy. They are closer to what data exchange theory calls constants or what XSLT calls `<xsl:param>` — values injected into the mapping at execution time, not structural participants in the tgd. Running Clio-style structural matching over them is meaningless.

### Three-way tgd classification

The N-input structure requires a richer tgd classification than the theory’s default:

- **Primary tgds** — `S_payload → T`: the core structural mapping, full Clio/schema-graph treatment applies.
- **Enrichment tgds** — `S_payload_N → T`: augmenting or overriding fields from prior pipeline steps; same structural class as primary but with temporal provenance — `payload-1` represents what was already produced, not fresh source data.
- **Lookup bindings** — `S_config / S_shared / S_pipeline → T`: constants injected as literal values; no scoring, no structural alignment — represented as bindings, not correspondences.

### Implications for the strategy pipeline

The three-phase strategy pipeline (graph construction → structural alignment → name refinement) applies **only to structurally rich inputs** — payload and its chain. The name-value inputs bypass Phases 1 and 2 entirely and are registered directly as bindings.

The JSON correspondence set must reflect this at the top level:

```json
{
  "inputs": {
    "primary": "schemas/payload.json",
    "chain": [
      "schemas/payload-1.json",
      "schemas/payload-2.json"
    ],
    "bindings": {
      "global_config": "schemas/global-config.nvp",
      "shared_vars":   "schemas/shared-vars.nvp",
      "pipeline_vars": "schemas/pipeline-vars.nvp"
    }
  },
  "correspondences": [
    {
      "id": "c001",
      "src_input": "primary",
      "src_path": "SalesOrder.customerId",
      "tgt_path": "Invoice.clientRef",
      "relation": "eq",
      "composite_score": 0.87,
      "signals": {
        "structural": 0.91,
        "type_compat": 0.95,
        "name_similarity": 0.61,
        "fk_reachable": true
      },
      "basis": "fk-reachable",
      "tgd_class": "primary"
    },
    {
      "id": "c002",
      "src_input": "chain[0]",
      "src_path": "ProcessedOrder.approvalStatus",
      "tgt_path": "Invoice.status",
      "relation": "eq",
      "composite_score": 0.83,
      "signals": {
        "structural": 0.88,
        "type_compat": 1.0,
        "name_similarity": 0.54,
        "fk_reachable": false
      },
      "basis": "fk-reachable",
      "tgd_class": "enrichment",
      "notes": "Sourced from previous pipeline step outcome; augments primary payload mapping"
    }
  ],
  "bindings": [
    {
      "id": "b001",
      "src_input": "global_config",
      "src_key": "region",
      "tgt_path": "Invoice.billingRegion",
      "basis": "explicit-reference",
      "tgd_class": "lookup"
    },
    {
      "id": "b002",
      "src_input": "pipeline_vars",
      "src_key": "correlationId",
      "tgt_path": "Invoice.traceId",
      "basis": "explicit-reference",
      "tgd_class": "lookup"
    }
  ]
}
```

### Extended field reference

|Field                        |Purpose                                                      |
|-----------------------------|-------------------------------------------------------------|
|`inputs.primary`             |The payload schema — subject to full structural alignment    |
|`inputs.chain`               |Ordered array of prior step schemas; index 0 = payload-1     |
|`inputs.bindings`            |Named name-value pair inputs; bypass scoring pipeline        |
|`correspondences[].src_input`|Which input schema this correspondence is derived from       |
|`correspondences[].tgd_class`|`primary` | `enrichment` | `lookup`                          |
|`bindings[]`                 |Injected constants; `src_key` is the NVP key, no score fields|
|`bindings[].tgd_class`       |Always `lookup`                                              |

-----

## AI vs. Deterministic: Phase Analysis

### The core distinction

**Deterministic** — computable from schema structure alone, no ambiguity. Same input always produces the same output. The algorithm either finds it or it does not.

**AI-beneficial** — requires semantic interpretation, world knowledge, or probabilistic reasoning over ambiguous signals. The canonical example: a source field named `firstName` in a `Person` class and a target field named `name-given-at-birth`. No string distance metric, no synonym dictionary, no structural rule will reliably bridge that gap. A human bridges it instantly because they understand what both mean. So does an LLM.

### Phase-by-phase analysis

**Phase 0 — Schema type classification: mostly deterministic**

The six classification tests are structural observations — nesting depth, FK annotations, document root element, namespace comparison. These are fully deterministic.

The one exception is **chain detection** (Test 4). Fingerprint matching against prior pipeline outputs is deterministic when the registry exists and schemas are stable. When schemas evolve between pipeline runs, a `chain` candidate may no longer match its stored fingerprint. AI can bridge this gap — semantic similarity between a candidate schema and a known prior target, even after structural divergence, is a judgment call a deterministic test cannot make reliably.

> Verdict: **mostly deterministic; AI useful at the chain/payload boundary.**

**Phase 1 — Graph construction: fully deterministic**

Pure parsing and annotation. Build nodes from element declarations, annotate PK/FK from formal schema constructs (`xs:key`/`xs:keyref`, JSON Schema `$ref`, EDMX `NavigationProperty`), classify edges, merge includes, register imports. The grammars of XSD, JSON Schema, TSCH, and EDMX are fixed. Either an annotation is present or it is not.

The one edge case worth noting: **implicit FKs** — a field named `customerId` in an XSD with no formal `xs:keyref`. Inferring that this is semantically a FK is AI territory, but it belongs to Phase 2, not Phase 1. Phase 1 extracts only what is formally declared.

> Verdict: **fully deterministic. No AI needed.**

**Phase 2 — Structural alignment: mostly deterministic, AI for implicit semantics**

The Clio-style graph reachability computation is deterministic: given the annotated graph, which source paths can structurally reach which target paths? Type compatibility, cardinality matching, and FK pairing are all computable from the graph.

AI adds value in three specific situations:

- **Implicit FK inference** — `customerId` without a formal keyref. AI infers “this is a FK to the Customer entity” from naming patterns combined with structural position, reliably and across naming convention and language boundaries where deterministic heuristics fail.
- **Choice group resolution** — `xs:choice` / `oneOf`. Deterministically you know a choice exists; which branch is the correct mapping target requires semantic judgment about intent.
- **Cardinality reconciliation** — source `1:N`, target `1:1`. Structurally observable as a mismatch; whether it represents intentional flattening, aggregation, or an error requires context that AI can supply.

> Verdict: **mostly deterministic; AI beneficial for implicit FKs, choice groups, and cardinality intent.**

**Phase 3 — Name refinement: primary AI phase**

This is where deterministic approaches fundamentally break down. The full spectrum:

|Case                                  |Deterministic?                   |AI?                    |
|--------------------------------------|---------------------------------|-----------------------|
|`firstName` vs `first_name`           |✅ normalisation                  |                       |
|`firstName` vs `givenName`            |⚠️ synonym dict, partial          |✅ more reliable        |
|`firstName` vs `name-given-at-birth`  |❌                                |✅ world knowledge      |
|`customerId` vs `clientRef`           |❌                                |✅ domain knowledge     |
|`amt` vs `totalAmount`                |⚠️ abbreviation expansion, partial|✅ more reliable        |
|`street` vs `addressLine1`            |❌                                |✅ structural + semantic|
|`GLD_CD` vs `goldCode` (legacy naming)|❌                                |✅ pattern + context    |
|Dutch field name vs English field name|❌                                |✅ multilingual         |

String distance (Levenshtein, Jaro-Winkler) handles normalisation. Synonym dictionaries cover some domain vocabulary. But world-knowledge bridging across naming conventions, languages, abbreviation styles, and domain jargon is where LLMs are genuinely superior to any deterministic approach.

> Verdict: **AI is the primary engine. Deterministic name normalisation is pre-processing only.**

**Binding registration (NVP/config inputs): AI-beneficial**

When NVP inputs are registered as bindings, the question is which binding key maps to which target field. This is structurally identical to the Phase 3 name matching problem — `correlationId` vs `traceId`, `tenantRegion` vs `billingRegion`. The same reasoning applies.

> Verdict: **AI-beneficial for the same reasons as Phase 3.**

### Summary

|Phase                   |Deterministic       |AI-beneficial                |Primary reason                    |
|------------------------|--------------------|-----------------------------|----------------------------------|
|0 — Schema typing       |✅ mostly            |⚠️ chain detection edge       |Schema fingerprint evolution      |
|1 — Graph construction  |✅ fully             |❌                            |Pure parsing; formal grammar      |
|2 — Structural alignment|✅ mostly            |⚠️ implicit FKs, choice groups|Unannotated relational intent     |
|3 — Name refinement     |⚠️ normalisation only|✅ primary                    |Semantic bridging, world knowledge|
|Binding registration    |⚠️ partial           |✅ beneficial                 |Same as Phase 3                   |

### Architectural implication: hybrid pipeline

Deterministic and AI phases should be **cleanly separated**, not blended. AI is invoked as a targeted enrichment pass at specific points, not as a general-purpose overlay:

```
Phase 0  ──►  deterministic gate
                  └─ [AI assist: chain/payload boundary only]
                  
Phase 1  ──►  fully deterministic
                  └─ annotated UDM schema graph
                  
Phase 2  ──►  deterministic core → candidate correspondence set
                  └─ [AI enrichment pass: implicit FKs, choice groups, cardinality intent]
                  └─ annotated candidate set with ai-inferred flags
                  
Phase 3  ──►  AI primary → name-bridged correspondence set
                  └─ deterministic post-process: score normalisation, deduplication

Bindings ──►  [AI assist: NVP key → target field matching]
```

AI output at every phase feeds back into the scored correspondence set with `basis: "ai-inferred"` alongside `fk-reachable`, `name-heuristic`, and `user-confirmed`. This preserves full explainability and keeps human review targeted at correspondences derived from AI inference rather than deterministic structural analysis — the highest-value use of reviewer time.

-----

## UTL-X Functions, tgds, and the Limits of Schema Matching Theory

### What tgds actually express

A tgd (tuple-generating dependency) says:

```
∀x. S(x) → ∃y. T(f(x), y)
```

For every source tuple matching a pattern, there must exist a target tuple. The value `f(x)` is a **projection or renaming** of source values — no computation, no aggregation, no derivation. tgds model **structural reshaping and navigation**, not **value transformation**. This is an explicit scope decision in Fagin et al.; computation and value invention are out of scope by design.

### UTL-X function classes vs. tgd expressibility

|Function class         |Examples                           |tgd expressible?                                 |
|-----------------------|-----------------------------------|-------------------------------------------------|
|Structural navigation  |field reference, `pick()`, `omit()`|✅ projection                                     |
|Spread / merge         |`...spread`                        |✅ GAV tgd, multiple source relations             |
|Filter                 |`filter()`                         |⚠️ selection predicate in source pattern only     |
|Array transform        |`map()`, `mapBy()`                 |✅ tgd over sequence                              |
|Keyed lookup           |`findBy()`, `filterBy()`           |⚠️ FK lookup — Clio join pattern, not standard tgd|
|First-match            |`find()`, `findIndex()`            |⚠️ selection + cardinality + ordering — beyond tgd|
|Grouping               |`groupBy()`                        |❌ no tgd equivalent                              |
|Aggregation            |`sumBy()`, `countBy()`, `reduce()` |❌ aggregation — strictly beyond tgd              |
|Derived field          |`orderTotal = sum(qty * price)`    |❌ computation — strictly beyond tgd              |
|Structural construction|`zip()`, `unzip()`, `flatten()`    |❌ structural invention — beyond tgd              |
|Sorting                |`orderBy()`                        |❌ ordering — outside relational tgd semantics    |

tgds cover the structural skeleton. The UTL-X function space — particularly the `xxxBy` family, aggregation, and derivation — goes substantially beyond what the schema matching literature models.

### Correlation between schema type and function class

The schema type annotations derived in Phase 0 and Phase 1 have strong predictive power over which UTL-X function class implements a correspondence. This correlation is largely **deterministically inferable from the schema graph**:

|Schema graph signal                                           |Inferred UTL-X function class                      |
|--------------------------------------------------------------|---------------------------------------------------|
|Source: `lookup` type + FK edge to payload                    |`findBy` / `filterBy`                              |
|Source: 1:N containment → target: 1:N containment             |`map` / `mapBy`                                    |
|Source: N:M junction entity                                   |`groupBy` + `mapBy`                                |
|Source: 1:N containment → target: 1:1 scalar                  |`reduce` / `sumBy` / `countBy` — **derivation gap**|
|Multiple source inputs → one target object                    |`spread`                                           |
|Source: 1:N → target: ordered 1:N                             |`orderBy` + `map`                                  |
|Source field absent, target field required + numeric context  |**derivation gap** — computation required          |
|Source field absent, target field required, no numeric context|**unmatched** — flag for AI / human                |

A `lookup` type input almost always implies `findBy` / `filterBy`. A 1:N containment edge almost always implies `map`. These are not guesses — they are structural inferences from the annotated schema graph.

-----

## Function Inference Belongs Inside MC — Not in a Separate Layer

### The false coverage problem

An MC analysis that reports:

```
source: SalesOrder.lines[*].price  →  target: Invoice.orderTotal
mc_status: MATCHED
```

is **wrong**. This is not a match — it is a derivation gap dressed as a match. The target field requires `sum(map(lines, l => l.qty * l.price))`. Without surfacing this, the MC report gives false confidence and an inflated coverage percentage.

Likewise:

```
source: SalesOrder.lines[*]  →  target: Invoice.lineItems[*]
mc_status: MATCHED
```

is incomplete without knowing this implies `map()`. If the target requires sorted output, `map()` alone is insufficient — `orderBy() + map()` is needed. That is still a schema-level observation, not an instance-level one. It belongs in MC.

### The reframing: function inference is MC output, not a separate layer

Layer 2 (function inference) is not a hidden layer between MC and Execution — it is the **completion of Layer 1**. A correspondence entry without a `mapping_class` and `inferred_function` is an incomplete MC result. The enriched form:

```json
{
  "id": "c005",
  "src_path": "SalesOrder.lines[*].price",
  "tgt_path": "Invoice.orderTotal",
  "composite_score": 0.81,
  "mapping_class": "derivation-gap",
  "inferred_function": null,
  "requires": "aggregation — sum over repeating group",
  "mc_status": "incomplete",
  "basis": "structural-analysis"
}
```

```json
{
  "id": "c006",
  "src_path": "SalesOrder.lines[*]",
  "tgt_path": "Invoice.lineItems[*]",
  "composite_score": 0.94,
  "mapping_class": "array-transform",
  "inferred_function": "map",
  "mc_status": "complete",
  "basis": "fk-reachable"
}
```

```json
{
  "id": "c007",
  "src_input": "productCatalog",
  "src_path": "Product.description",
  "tgt_path": "Invoice.lineItems[*].productName",
  "composite_score": 0.89,
  "mapping_class": "lookup-join",
  "inferred_function": "findBy",
  "inferred_key": "productCode",
  "mc_status": "complete",
  "basis": "fk-reachable"
}
```

The `mapping_class` and `inferred_function` fields are **Phase 1 output** — derived deterministically from the schema graph where possible, AI-proposed for derivation gaps. They are what makes the MC report a **first draft of the mapping structure**, not just a coverage percentage.

### Revised two-layer model

Drop the three-layer model. The correct structure is two layers, with MC enriched to include function inference:

```
Layer 1 — MC (schema-level, enriched)
  ├── Schema graph + tgd correspondences        [structural skeleton]
  ├── mapping_class per correspondence           [deterministic from graph]
  ├── inferred_function per correspondence       [deterministic where possible]
  ├── derivation gaps flagged explicitly         [AI-proposed computation]
  └── mc_status: complete | derivation-gap | ambiguous | unmatched

Layer 2 — Execution (instance-level)
  └── Validates Layer 1 function proposals against real data
      Catches instance-incomplete cases Layer 1 cannot see
```

### The efficiency argument

Folding function inference into MC makes the MC report **more efficient to consume, not more complex**. Without it, the developer re-derives the function class manually when writing the UTL-X script — the MC report is a field-matching list they must interpret. With it, the MC report is a **structural draft of the UTL-X mapping** — the developer corrects and refines rather than starting from scratch. That is the efficiency gain IF20 is really after.

### Extended mapping_class vocabulary

|`mapping_class`    |Inferred from                          |UTL-X function(s)              |MC status          |
|-------------------|---------------------------------------|-------------------------------|-------------------|
|`direct`           |1:1 scalar, type-compatible            |field reference                |complete           |
|`array-transform`  |1:N → 1:N containment                  |`map` / `mapBy`                |complete           |
|`lookup-join`      |FK edge to `lookup` schema             |`findBy` / `filterBy`          |complete           |
|`spread-merge`     |N inputs → 1 target object             |`...spread`                    |complete           |
|`grouped-transform`|N:M junction entity                    |`groupBy` + `mapBy`            |complete           |
|`sorted-transform` |1:N → ordered 1:N                      |`orderBy` + `map`              |complete           |
|`derivation-gap`   |1:N scalar → 1:1 scalar (numeric)      |`sumBy` / `reduce` / arithmetic|incomplete — AI    |
|`unmatched`        |No source candidate                    |—                              |incomplete — human |
|`ambiguous`        |Multiple candidates, low discrimination|—                              |incomplete — review|

-----

## Conclusion

`strategy.ts` is valid scaffolding but sits at the wrong architectural level. IF20’s thesis — derive strategy from the schema graph, separate element-level from structure-level coverage, keep MC and Execution as distinct analyses — provides a theoretically grounded and format-agnostic foundation for UTL-X MC generation. The underlying theory is mature, peer-reviewed, and directly applicable; IF20 is an engineering problem of mapping that theory onto UTL-X’s UDM layer.

The Open-M N-input model extends the theory cleanly: the source schema S becomes a typed union of inputs, but only structurally rich schemas (`payload`, `chain`, `lookup`) participate in the Clio-style matching pipeline. Name-value pair inputs (global config, shared vars, pipeline vars) are bindings — constants injected at mapping time — and are explicitly excluded from structural alignment. The `tgd_class` field (`primary`, `enrichment`, `lookup`) captures this distinction throughout the correspondence set.

Schema type classification (Phase 0) is the prerequisite that makes all of this tractable. Without knowing what kind of schema each input is, the pipeline cannot route inputs correctly, cannot distinguish a lookup from a payload, and cannot decide whether a flat input should produce correspondences or bindings. The six-test classification sequence — from format-level meta-schema detection through include/import composition, flat detection, structural richness, chain fingerprinting, and lookup discrimination — gives IF20 a deterministic, format-agnostic gate before any matching work begins.

The pipeline is a hybrid of deterministic and AI-driven phases. Phase 1 (graph construction) is fully deterministic and must remain so — it is the stable foundation everything else depends on. Phase 3 (name refinement) is the primary AI phase, handling the semantic bridging that no deterministic metric can reliably achieve. Phase 2 (structural alignment) is mostly deterministic with targeted AI enrichment for implicit FKs and ambiguous structural patterns. The `basis` field in the correspondence set (`fk-reachable`, `ai-inferred`, `name-heuristic`, `user-confirmed`) makes the provenance of every correspondence explicit, keeping human review focused where it matters most.