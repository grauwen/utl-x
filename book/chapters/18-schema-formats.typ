= Schema Formats

== XSD (XML Schema Definition)
// - Reading XSD: parse structure, types, constraints
// - XSD patterns: Russian Doll, Venetian Blind, Salami Slice, Swiss Army Knife
// - Pattern detection and conversion
// - XSD → JSON Schema transformation
// - XSD → documentation generation

== JSON Schema
// - Drafts: 04, 06, 07, 2019-09, 2020-12
// - Reading: parse schema structure
// - Writing: generate JSON Schema from data
// - Validation: validate data against schema
// - Schema-to-schema: JSON Schema ↔ XSD conversion

== Avro Schema
// - Apache Avro for data pipelines
// - Primitive types, records, enums, arrays, maps, unions
// - Logical types: date, timestamp, decimal, UUID
// - Avro → JSON Schema, Avro → XSD conversion

== Protobuf (proto3)
// - Protocol Buffers for gRPC/microservices
// - Messages, enums, repeated fields, oneof, maps
// - Protobuf → JSON Schema conversion
// - Namespace and package handling

== OSCH (OData Schema / EDMX)
// - Entity types, complex types, navigation properties
// - CSDL format (XML-based)
// - EDMX → JSON Schema conversion

== TSCH (Table Schema)
// - Frictionless Data Table Schema
// - Field types, constraints, foreign keys
// - CSV metadata companion
// - TSCH → JSON Schema conversion

== USDL (Universal Schema Definition Language)
// - UTL-X's own schema format
// - Cross-format schema representation
// - USDL as the bridge between schema formats
