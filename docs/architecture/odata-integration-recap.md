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
