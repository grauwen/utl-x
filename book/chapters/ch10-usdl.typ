= USDL: Universal Schema Definition Language

== Why USDL?
// - Each format has its own schema language (XSD, JSON Schema, Avro, Protobuf, ...)
// - Converting between them requires understanding EACH format's semantics
// - USDL is the Rosetta Stone: one intermediate representation for all schemas
// - Like UDM is for data, USDL is for schemas

== USDL Syntax
// - %usdl 1.0 header
// - Type definitions: type, fields, constraints
// - Example:
//   %usdl 1.0
//   type Customer {
//     id: string @required
//     name: string @required @minLength(1)
//     email: string @pattern("^[\\w.-]+@[\\w.-]+$")
//     age: integer @min(0) @max(150)
//     orders: [Order] @optional
//   }
//
//   type Order {
//     orderId: string @required
//     total: decimal @min(0)
//     currency: string @enum("EUR", "USD", "GBP")
//   }

== USDL Type System
// - Primitives: string, integer, decimal, boolean, date, datetime, time, binary
// - Complex: object (fields), array, map
// - Union: oneOf (like JSON Schema oneOf, Avro union, Protobuf oneof)
// - Enum: named value sets
// - Reference: named types (like XSD complexType, JSON Schema $ref)

== USDL Constraints
// - @required / @optional
// - @minLength, @maxLength (strings)
// - @min, @max (numbers)
// - @pattern (regex)
// - @enum (value set)
// - @default (default value)
// - @deprecated (lifecycle)
// - @description (documentation)
// - @example (sample values)

== USDL ↔ Other Schema Formats
// - USDL → XSD: types become complexTypes, constraints become facets
// - USDL → JSON Schema: types become object schemas, constraints become keywords
// - USDL → Avro: types become records, constraints become metadata
// - USDL → Protobuf: types become messages, constraints become comments
// - USDL → TSCH: types become fields with constraints
// - USDL → OSCH: types become EntityTypes
//
// Round-trip: Schema → USDL → different Schema format
// Not all constraints map perfectly (semantic gaps documented per format)

== Using USDL in Transformations
// - Input schema declaration: input json @schema("customer.usdl")
// - Output schema declaration: output xml @schema("invoice.usdl")
// - Validation: pre-validate input, post-validate output against USDL
// - Scaffolding: generate transformation skeleton from USDL schemas

== USDL as Documentation
// - Auto-generate human-readable documentation from USDL
// - Field descriptions, types, constraints in a table
// - Cross-reference between input and output schemas
// - Export to Markdown, HTML, or PDF
