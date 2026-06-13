# IF20 — Delta: Findings Since Last Full Abstract

> This document captures findings developed after the last full abstract write.
> Topics: consumption points for enriched MC output, upfront schema type tagging,
> UDM schema metadata extension, and pipeline simplification.

-----

## 1. Two Consumption Points for the Enriched MC Output

The enriched correspondence set (with `mapping_class`, `inferred_function`, `mc_status`) serves two distinct downstream consumers. Both must be considered when designing the serialization contract.

### Consumption Point A — Mapping Editor / Visualization

The correspondence set is the **data model behind a visual mapping canvas**. Each entry drives:

- A connector line between source and target field
- A function badge on the connector (`map`, `findBy`, `groupBy+mapBy`)
- A status indicator per `mc_status` (`complete`, `derivation-gap`, `ambiguous`, `unmatched`)
- Confidence color coding per `basis` (`fk-reachable` = high confidence, `ai-inferred` = review required, `user-confirmed` = locked)

The `mapping_class` vocabulary maps directly to visual affordances:

- `derivation-gap` → expression editor, not a simple connector
- `lookup-join` → three-party visual: source, lookup schema, target
- `array-transform` → fan-out connector indicating repeating group

### Consumption Point B — AI Chain Input (Agentic Mapping Generation)

The enriched correspondence set is the **structured prompt context** for an AI agent generating the UTL-X script. Rather than presenting the AI with raw schema pairs and an open-ended instruction, the agent receives:

- Pre-classified input schemas with declared types
- Pre-computed correspondences with `mapping_class` and `inferred_function`
- Explicitly flagged derivation gaps with computation context
- Binding registrations for NVP/config inputs

The AI chain’s responsibility is then scoped and grounded: fill derivation gaps and resolve ambiguous correspondences. It does not re-derive the structural skeleton from scratch. This is a fundamentally better AI prompt pattern:

> **Structured context + targeted gaps** rather than **open-ended schema pair**

This makes the JSON correspondence set a **dual-purpose artifact** — persistence format and AI chain interchange format. The serialization contract must serve both consumers.

-----

## 2. Upfront Schema Type Tagging

### The problem

Phase 0 classification tests are a fallback for when schema type is unknown. In Open-M, the type of every non-payload input is known upfront by the platform. Running inference on known-type inputs is wasteful and risks misclassification. Declared types should override inference.

### What the literature offers

No single standard covers schema role tagging in a multi-input transformation pipeline. The closest precedents:

|Standard                |Relevant concept                                      |Limitation                           |
|------------------------|------------------------------------------------------|-------------------------------------|
|XProc 3.0 `p:input kind`|`document` vs `parameter` — pipeline input role       |No `lookup` concept; two kinds only  |
|OpenAPI `x-` extensions |Vendor extension properties for custom schema metadata|JSON Schema only; not portable to XSD|
|VoID (W3C)              |Dataset role tagging at graph root                    |RDF-heavy; ontology-oriented         |
|DCAT `dcat:role`        |Distribution role in a catalogue                      |Catalogue-level, not schema-level    |
|Frictionless `profile`  |Resource descriptor metadata                          |Concept sound; vocabulary too generic|

**Conclusion**: no existing standard is directly applicable. The most pragmatic approach combines:

- `x-utlx-input-type` extension properties for JSON Schema inputs
- `%utlx` header declarations for XSD, TSCH, EDMX inputs
- Platform slot declarations for fixed Open-M inputs

### Proposed tagging mechanisms

**JSON Schema — inline `x-utlx` extension:**

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "x-utlx-input-type": "lookup",
  "x-utlx-input-role": "product-catalog",
  "x-utlx-input-key": "productCode"
}
```

**XSD / TSCH / EDMX — UTL-X header declaration:**

```
%utlx
  input payload    : schemas/sales-order.xsd
  input lookup     : schemas/product-catalog.xsd  key=productCode
  input config     : schemas/global-config.nvp
  input nvp        : schemas/shared-vars.nvp
  input nvp        : schemas/pipeline-vars.nvp
  input chain[1]   : schemas/processed-order.xsd
```

**Open-M — fixed platform slot declarations:**

```json
{
  "open-m-inputs": {
    "slot-1": { "type": "payload",  "schema": "dynamic" },
    "slot-2": { "type": "chain",    "schema": "dynamic", "chain-index": 1 },
    "slot-3": { "type": "config",   "schema": "schemas/global-config.nvp", "fixed": true },
    "slot-4": { "type": "nvp",      "schema": "schemas/shared-vars.nvp",   "fixed": true },
    "slot-5": { "type": "nvp",      "schema": "schemas/pipeline-vars.nvp", "fixed": true },
    "slot-6": { "type": "lookup",   "schema": "dynamic" }
  }
}
```

`fixed: true` means the slot type is platform-declared and not user-overridable.

### Priority rule for type resolution

```
1. Platform declaration (Open-M slot config)     highest priority — no inference run
2. Inline schema tag (x-utlx-input-type)         user-declared — inference skipped
3. UTL-X header declaration (%utlx input)        user-declared — inference skipped
4. Phase 0 classification tests                  fallback inference
5. Ambiguous / unresolvable                      error surfaced to user
```

The correspondence set records the resolution level under `type_resolution_source`.

-----

## 3. UDM Should Be Extended to Carry Schema Type

### The architectural argument

If schema type tagging lives only in the source format (`x-utlx` on JSON Schema, `%utlx` header for XSD), then the UDM graph loses the tag at parse time. Every downstream consumer — Phase 1, Phase 2, the AI chain, the visualizer — must reach back to the source format to recover declared types. This is an architectural leak: the UDM is no longer self-sufficient.

**The UDM should carry everything the strategy pipeline needs. Schema type is one of those things.**

### Proposed UDM extension

A **metadata envelope** is added at the schema root level — wrapping the existing graph, not polluting it:

```
UDMSchemaDocument                        ← new wrapper
  ├── metadata: UDMSchemaMeta            ← new, schema-wide
  │     ├── format          : xsd | json-schema | tsch | edmx | nvp
  │     ├── schema_type     : payload | chain | lookup | nvp | config |
  │     │                     enumeration | shared-type-library | meta-schema
  │     ├── type_source     : platform | inline-tag | header | inferred | user-override
  │     ├── role            : human-readable label ("product-catalog", "order-payload")
  │     ├── lookup_key      : (if schema_type = lookup) PK field name
  │     ├── chain_index     : (if schema_type = chain) position in pipeline sequence
  │     └── fixed           : boolean — platform-declared, not user-overridable
  └── graph: UDMSchemaGraph              ← existing structure, unchanged
```

This is a shallow extension. The node/edge structure of the UDM graph is not changed.

### The critical design boundary

The tag lives **on the UDM schema object**, not inside the schema’s node graph:

```
❌ Wrong: add a "schema-type" node into the UDM graph
✅ Right: add a metadata envelope around the UDM graph
```

Mixing metadata into the graph means every node query must filter out metadata nodes — pollution of the graph structure.

### Literature support for this pattern

- **VoID / Linked Data**: role metadata attaches at the dataset level, not the triple level — same principle
- **Schema registries (Confluent, AWS Glue)**: metadata (subject, type, version) attaches to the schema registration, not the schema content
- **XProc 3.0 `p:document-properties`**: properties attach to a document as it flows through the pipeline, separate from document content

### Propagation rules

Type resolution is enforced **inside the UDM parser**, not scattered across pipeline phases:

```
Parse source format
  └── extract inline tag if present → type_source = "inline-tag" | "header"

If no inline tag:
  └── check platform slot declaration → type_source = "platform", fixed = true

If no platform declaration:
  └── run Phase 0 classification tests → type_source = "inferred"

User override (mapping editor / tooling):
  └── type_source = "user-override"
  └── only permitted if fixed = false
```

Once the UDM document is constructed, every downstream consumer reads `metadata.schema_type` and gets the correct answer without re-deriving it.

### JSON correspondence set: `type_resolution_source` per input

```json
{
  "inputs": {
    "primary": {
      "schema": "schemas/sales-order.xsd",
      "format": "xsd",
      "schema_type": "payload",
      "type_resolution_source": "header"
    },
    "lookup": [
      {
        "schema": "schemas/product-catalog.json",
        "format": "json-schema",
        "schema_type": "lookup",
        "lookup_key": "productCode",
        "type_resolution_source": "inline-tag"
      }
    ],
    "bindings": {
      "global_config": {
        "schema": "schemas/global-config.nvp",
        "schema_type": "config",
        "type_resolution_source": "platform",
        "fixed": true
      }
    }
  }
}
```

### Resolution (code-grounded) — envelope on the *schema* representation, not the data UDM

Checked against `modules/core/.../udm/udm_core.kt`. The section heading "UDM should be **extended**" needs
one correction: **don't extend the runtime data UDM — define a new design-time schema-document envelope.**
("Design-time" here = **MC mode**: schema-based, no data execution. The data UDM is Execution mode's
runtime output.)

- Core `UDM` is the **runtime data/instance model** (`Scalar | Array | Object | DateTime | Date | …`) — it
  represents *values*, not schemas. `schema_type` (`payload`/`lookup`/…) is an **input role** decided at
  design time (MC mode) and is meaningless on instance data. So the envelope wraps the **schema
  representation**, not the data UDM. The delta's "✅ envelope around the graph, not a node in the graph" is
  right — the clarification is that the graph is the **schema** graph, a *separate* artifact from the data
  UDM:

  ```
  UDMSchemaDocument            ← NEW (design-time / MC); NOT the data UDM
    ├── metadata: UDMSchemaMeta
    └── graph: <UDM-based schema graph>   ← greenfield; no UDMSchema/SchemaGraph exists in core today
  ```

- **Backward compatibility: nothing breaks.** The envelope is additive and design-time; it does not touch
  the runtime data UDM type, format parsers/serializers, the engine, the `.udm` UDM-Language format + its
  roundtrip tests, the conformance suite, or the `utlxe` proto/SDKs. And because the UDM-based schema graph
  is **greenfield** (schemas today parse to *data*-UDM or IDE field-trees), it is *defined with* the
  envelope from the start — **nothing to migrate**. So §4's "Phase 0 folds into UDM parsing" is exactly
  right: parsing yields a *typed* `UDMSchemaDocument`.

- **Do NOT overload the data UDM's existing `metadata`.** `UDM.Object` carries `attributes` + a
  `metadata: Map<String,String>` map — but that map is a **reserved, `__`-prefixed format-fidelity
  channel**, not a general tag bag: it holds XML **namespace** context and the detected **XSD design
  pattern** (`__xsdPattern` = russian-doll / salami-slice / venetian-blind / garden-of-eden), and the
  **UDM-Language serializer writes it to `.udm`** so those survive a roundtrip. Stuffing `schema_type`
  there is wrong on three counts — per-instance/per-node granularity, a reserved *fidelity* channel, and
  persisted to `.udm` (roundtrip-test risk). ⚠️ The `udm_core.kt:102` comment `// Internal metadata (not
  serialized)` is inaccurate (it *is* serialized to `.udm`) and should be fixed.

**On the Open Questions (versioning):** version the envelope with the schema-document / correspondence-set
artifact, **decoupled** from the data-UDM/`.udm` format version (independent cadences).

-----

## 4. Pipeline Simplification: Phase 0 Folds into UDM Parsing

### Consequence of UDM carrying schema type

Phase 0 (schema typing) is no longer a separate pipeline phase. It becomes **part of the UDM parsing step**. The output of parsing is not a raw graph but a typed `UDMSchemaDocument`. Phase 1 (graph construction) receives already-typed inputs.

The four-phase pipeline simplifies to three:

```
Before (four phases):
  Phase 0  ──►  schema typing
  Phase 1  ──►  graph construction  →  annotated UDM graph
  Phase 2  ──►  structural alignment  →  candidate correspondences
  Phase 3  ──►  name refinement  →  scored correspondence set

After (three phases):
  Parse + type  ──►  typed UDMSchemaDocument     [Phase 0 + 1 merged]
  Align         ──►  candidate correspondences    [was Phase 2]
  Refine        ──►  scored correspondence set    [was Phase 3]
```

This is architecturally cleaner: the UDM carries its own type, and no downstream phase ever needs to re-derive or re-ask. The typing decision is made once, at parse time, with full priority-cascade logic, and propagated forward as a first-class UDM property.

-----

## Open Questions

- Should `role` (the human-readable label) be user-editable independently of `schema_type`, or always derived from it?
- Should `lookup_key` support composite keys (multi-field PKs)?
- For `chain` inputs: should `chain_index` be declared or always inferred from pipeline execution order?
- Should the UDM metadata envelope be versioned independently of the UDM graph schema version?