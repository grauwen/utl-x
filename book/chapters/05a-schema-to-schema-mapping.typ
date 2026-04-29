= Schema-to-Schema Mapping

== Why Schema Mapping Matters
// - Professional integration starts with schemas, not data
// - The schema defines the contract: "what fields, what types, what constraints"
// - Without schema mapping: ad-hoc, brittle, undocumented transformations
// - With schema mapping: structured, validated, maintainable transformations
// - The mapping IS the documentation — it shows exactly what goes where

== How Traditional Tools Do It (and Why It's Broken)

=== The XSLT Approach (Tibco BW, SAP CPI, Azure)
// - Source: XML (always XML — everything must be XML first)
// - Tool loads source XSD and target XSD
// - Developer draws mapping lines between XSD elements
// - Tool generates XSLT stylesheet
// - Runtime: XML → XSLT processor → XML
//
// Diagram:
// ┌──────────┐     ┌──────────────┐     ┌──────────┐
// │ Source    │     │   XSLT       │     │ Target   │
// │ XSD      │────→│   Stylesheet │────→│ XSD      │
// │ (XML)    │     │   (generated)│     │ (XML)    │
// └──────────┘     └──────────────┘     └──────────┘
//
// This works for XML-to-XML. But what about JSON?

=== The JSON Problem: Why JSON→XML→XSLT→XML→JSON Is Wrong
// - Most modern APIs use JSON, not XML
// - Traditional tools force this anti-pattern:
//
//   JSON input
//     ↓ convert to XML (lossy — JSON has no attributes, no namespaces)
//     ↓ apply XSLT transformation
//     ↓ XML output
//     ↓ convert back to JSON (lossy — XML attributes become properties? _text?)
//   JSON output
//
// FOUR conversion steps. TWO of them lossy. Performance penalty at each step.
//
// Diagram (the anti-pattern):
// ┌──────┐   ┌─────────┐   ┌──────┐   ┌─────────┐   ┌──────┐
// │ JSON │──→│ XML     │──→│ XSLT │──→│ XML     │──→│ JSON │
// │ input│   │(convert)│   │      │   │(result) │   │output│
// └──────┘   └─────────┘   └──────┘   └─────────┘   └──────┘
//                ▲                          │
//                └── lossy! ────────────────┘
//
// Who does this:
// - Azure Logic Apps Data Mapper: converts JSON to XML infoset internally
// - SAP CPI: Message Mapping uses XML DOM internally
// - Tibco BW: always XML, JSON adapter converts at boundaries
// - MuleSoft: DataWeave avoids this (format-agnostic, like UTL-X)
//
// Why it's wrong:
// 1. Performance: 4 conversions instead of 1
// 2. Data loss: JSON arrays ↔ XML repeated elements is ambiguous
// 3. Attributes: JSON has no attributes — round-trip is lossy
// 4. Types: JSON numbers/booleans → XML strings → JSON strings (type lost)
// 5. Complexity: developer must think in XML even when data is JSON

=== The UTL-X Approach: Format-Agnostic Schema Mapping
// - Source schema: ANY format (JSON Schema, XSD, Avro, Protobuf, TSCH, OSCH)
// - Target schema: ANY format (same list)
// - UTL-X transformation: maps between schemas natively
// - NO intermediate XML conversion
// - NO XSLT
//
// JSON input → UTL-X (UDM) → JSON output (1 step, lossless)
// XML input → UTL-X (UDM) → JSON output (1 step, lossless)
// CSV input → UTL-X (UDM) → XML output (1 step, lossless)

== Tier 1: Instance Data Mapping

=== Every Data Format Has an Accompanying Schema Format
//
// | Data Format (Tier 1) | Schema Format (Tier 2) | Standard |
// |---------------------|------------------------|----------|
// | JSON | JSON Schema | IETF draft-04 through 2020-12 |
// | XML | XSD | W3C XML Schema Definition |
// | CSV | Table Schema (TSCH) | Frictionless Data |
// | YAML | JSON Schema (shared with JSON) | Same as JSON |
// | OData | EDMX/CSDL (OSCH) | OASIS OData |
//
// The schema describes the STRUCTURE of the data.
// UTL-X can validate input/output against these schemas.
// The mapping operates on instances (actual data), guided by schemas.

=== Instance-to-Instance Mapping Examples
// - JSON → JSON: REST API v1 response → REST API v2 request
// - XML → JSON: SOAP response → REST API request
// - CSV → XML: bank statement → payment instruction (SEPA pain.001)
// - JSON → CSV: API data → Excel-compatible report
// - XML → XML: UBL invoice → local tax authority format
// - OData → JSON: Dynamics 365 → internal canonical model

== Tier 2: Schema-to-Schema (Metadata) Mapping

=== Why Metadata Mapping Is Powerful
// - Map between schema DEFINITIONS, not data instances
// - Generate one schema format from another
// - Use case: "I have an XSD but my consumer needs a JSON Schema"
// - Use case: "I have an Avro schema but need an OpenAPI spec"
// - UTL-X handles this because schemas are just structured data (XML, JSON)

=== All Possible Schema-to-Schema Combinations
//
// | From ↓ / To → | XSD | JSCH | Avro | Protobuf | OSCH | TSCH |
// |---------------|-----|------|------|----------|------|------|
// | **XSD** | — | ✅ | ✅ | ✅ | ✅ | ✅ |
// | **JSCH** | ✅ | — | ✅ | ✅ | ✅ | ✅ |
// | **Avro** | ✅ | ✅ | — | ✅ | ⚠️ | ✅ |
// | **Protobuf** | ✅ | ✅ | ✅ | — | ⚠️ | ✅ |
// | **OSCH** | ✅ | ✅ | ⚠️ | ⚠️ | — | ✅ |
// | **TSCH** | ✅ | ✅ | ✅ | ✅ | ✅ | — |
//
// ✅ = supported, ⚠️ = partial (semantic gaps between formats)
//
// This is unique to UTL-X — no other tool can map between ALL schema formats.

=== Why This Matters in Practice
// - API gateway migration: WSDL/XSD → OpenAPI/JSON Schema
// - Kafka pipeline: JSON Schema → Avro (schema registry)
// - Data contract generation: XSD → Table Schema (for data quality)
// - Documentation: any schema → human-readable documentation
// - Code generation: schema → TypeScript interfaces, Java classes

== JSONATA: JSON Schema to JSON Schema Mapping
// - A special case: mapping between JSON Schema versions
// - draft-04 → 2020-12 migration
// - Adding/removing fields, changing types, renaming properties
// - Preserving constraints ($ref, allOf, oneOf, required)
// - Use case: API versioning — evolve the schema while maintaining compatibility

== Nested Schemas: Include and Import
// - XSD: <xs:include> (same namespace) and <xs:import> (different namespace)
// - JSON Schema: $ref (local and remote references)
// - Avro: named types referenced by name across schemas
// - Protobuf: import "other.proto"
// - RAML: !include fragments, libraries, overlays
// - OpenAPI: $ref to components/schemas, external files
//
// UTL-X handles nested schemas by:
// 1. Resolving references during parsing
// 2. Building a complete type graph
// 3. Making all types available for mapping
// 4. Preserving reference structure in output (for round-trip fidelity)

== USDL: The Universal Schema Definition Language
// - UTL-X's own schema language
// - Format-agnostic: one schema representation for all formats
// - Bridge between XSD, JSON Schema, Avro, Protobuf, OSCH, TSCH
// - Deserves its own chapter? (see Chapter XX)
//
// USDL represents:
// - Types: string, number, boolean, date, datetime, binary, array, object
// - Constraints: required, optional, pattern, min, max, enum
// - References: named types, composition (allOf/oneOf equivalent)
// - Metadata: descriptions, examples, deprecated markers
//
// Convert any schema to USDL → transform USDL → convert to target schema
// This enables the full matrix of schema-to-schema conversions.
