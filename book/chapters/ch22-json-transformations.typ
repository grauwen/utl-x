= JSON Transformations

== JSON in UTL-X
// - Direct UDM mapping: objects, arrays, scalars
// - No attributes (JSON has no concept of attributes)
// - Nested access: $input.data.users[0].name

== Reading JSON
// - Property access: dot notation, bracket notation
// - Array operations: indexing, iteration, filtering
// - Nested structures: deep access with safe navigation
// - Dynamic keys: $input[variableName]

== Writing JSON
// - Object construction: {key: value}
// - Array construction: map(), filter(), [spread]
// - Pretty printing (default) vs compact (--no-pretty)

== JSON-Specific Functions
// - parseJson: parse a JSON string within a transformation
// - renderJson: serialize a value to a JSON string
// - JSON Schema validation

== Common JSON Patterns
// - REST API response transformation
// - GraphQL result normalization
// - OpenAPI spec manipulation
// - Configuration file migration (JSON → YAML)
// - MongoDB document transformation
// - Elasticsearch query/result processing

== JSON Lines (JSONL)
// - Line-delimited JSON processing
// - Batch transformation of JSONL streams
// - Combining with CSV output for reporting
