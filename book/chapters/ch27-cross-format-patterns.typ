= Cross-Format Patterns

== The Power of Format Agnosticism
// - Same transformation logic, different input/output formats
// - Write once, apply to XML, JSON, CSV, YAML

== XML ↔ JSON Conversion
// - Pass-through: $input (automatic conversion)
// - Attribute handling: @attr in XML → property in JSON
// - writeAttributes option
// - Array detection: repeated XML elements → JSON arrays
// - Namespace handling in JSON output

== CSV ↔ JSON Conversion
// - CSV rows → JSON array of objects
// - JSON array → CSV with auto-headers
// - Column ordering and selection
// - Type preservation (numbers, dates)

== XML → CSV (Flattening)
// - Hierarchical XML → flat CSV rows
// - Selecting which XML elements become columns
// - Handling repeated elements (one row per item)
// - Parent-child denormalization

== JSON → XML (Structuring)
// - Flat JSON → hierarchical XML
// - Creating attributes from JSON properties
// - Namespace injection
// - Mixed content handling

== Multi-Format Pipelines
// - Input: XML order + CSV pricing + JSON customer → Output: UBL invoice
// - Real-world: EDI → XML → validation → JSON → API
// - Pattern: normalize to canonical format, then convert to target

== Data Normalization Patterns
// - Canonical data model: define once, use everywhere
// - Format-independent business logic
// - Reusable transformation libraries
