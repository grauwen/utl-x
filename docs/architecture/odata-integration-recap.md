# OData Integration in UTLX — Recap

## Current Status: Tier 2 (Planned)

OData is positioned as a **REST/entity-focused schema format** — not a pure schema like XSD or JSON Schema, but important for SAP Gateway and entity-relationship modeling.

## What's Already Done

| Aspect | Status |
|--------|--------|
| **USDL Directives** | 5 OData-specific directives defined in USDL 1.0 |
| **Documentation** | Syntax rationale, reference docs, EDMX mapping |
| **Example files** | 5 USDL examples in `/examples/usdl/odata/` |
| **Format compatibility** | Rated 60% overall (100% Tier 1, 50% Tier 2/3) |

## The 5 OData Directives (already in USDL 1.0)

| Directive | Scope | Type | Purpose |
|-----------|-------|------|---------|
| `%entityType` | TYPE_DEFINITION | Boolean | Mark entity type vs complex type |
| `%navigation` | TYPE_DEFINITION | Array | Navigation properties |
| `%target` | FIELD_DEFINITION | String | Navigation target entity |
| `%cardinality` | FIELD_DEFINITION | String | Relationship (1:1, 1:N, N:N) |
| `%referentialConstraint` | FIELD_DEFINITION | Object | Foreign key constraints |

## USDL → OData EDMX Mapping (documented)

```
%namespace          → Schema Namespace
%entityType: true   → <EntityType>
%entityType: false  → <ComplexType>
%key: true          → <Key><PropertyRef Name="..."/></Key>
%navigation         → <NavigationProperty>
%cardinality        → Multiplicity attribute
```

## What Remains (Parser + Serializer)

No explicit effort estimates were written down. Based on the Avro study (which documented similar scope):

- **EDMX/CSDL Parser** (OData schema → USDL): ~2-3 days
- **EDMX/CSDL Serializer** (USDL → OData schema): ~2-3 days
- **Estimated total**: ~4-6 days for core implementation

## Why Only 60% Compatibility?

- All Tier 1 directives work
- OData is REST/entity-focused, not pure schema
- Requires many Tier 3 directives (`%entityType`, `%key`, `%navigation`)
- Limited constraint support compared to JSON Schema
- No support for complex unions, oneOf, etc.

## SAP Relevance

Referenced in the SAP IDoc integration study as a transformation path: SAP Gateway/OData JSON representation can be handled via UTLX transformation.

## Key Source Files

- `docs/usdl/USDL-DIRECTIVES-REFERENCE.md` — directive definitions
- `docs/design/usdl-syntax-rationale.md` — most comprehensive analysis (EDMX mapping, examples, compatibility)
- `docs/language-guide/universal-schema-dsl.md` — tier status, directive docs
- `docs/idoc/sap-idoc-integration-study.md` — SAP Gateway context

## OData Tier 1 Implementation Status

**Tier 1 (OData JSON data format) is implemented.** The `odata` format is registered in the parser/serializer dispatch and supports OData JSON payloads as both transformation input and output.

Key implementation details:
- Format name: `odata` (used in UTLX headers as `input odata` / `output odata`)
- Parser: `ODataJSONParser.kt` — parses OData JSON, maps `@odata.*` annotations to UDM attributes
- Serializer: `ODataJSONSerializer.kt` — serializes UDM to OData-compliant JSON with control annotations
- Tree strategy: `odata-tree-strategy.ts` — filters `@odata.*` from property display

**OData annotations in UTLX expressions** use quoted string keys with the `@` prefix. Since `@` sets UDM attributes (the same mechanism used for XML attributes), dotted OData annotation names require quoting:

```utlx
{ "@odata.type": "#Products.Product", "@odata.id": "Products(1)" }
```

The parser (`parser_impl.kt:1118-1134`) handles this — quoted keys starting with `@` are treated as attributes with the prefix stripped. See `docs/architecture/odata-v4-implementation-plan.md` section 1.7 for full details.
