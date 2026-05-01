= Schema Formats

UTL-X treats schemas as data. An XSD, a JSON Schema, an Avro schema, a Protobuf definition — they are all inputs that can be parsed, transformed, and serialized to a different format. This is what makes schema-to-schema conversion possible: read a schema in one format, the USDL classification system (Chapter 12) normalizes it, and output it in another format.

Six schema formats are supported, each with a full parser and serializer:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Format*], [*Header keyword*], [*Domain*], [*Used by*],
  [XSD], [`xsd`], [XML], [Enterprise XML: UBL, HL7, ISO 20022, SWIFT],
  [JSON Schema], [`jsch`], [JSON / REST], [REST APIs, OpenAPI, MongoDB, Kafka],
  [Avro Schema], [`avro`], [Data pipelines], [Kafka, Spark, Hadoop, data lakes],
  [Protobuf], [`proto`], [Microservices], [gRPC, Google APIs, mobile apps],
  [OData Schema], [`osch`], [Enterprise REST], [Dynamics 365, SAP Gateway, SharePoint],
  [Table Schema], [`tsch`], [Tabular data], [CSV metadata, Frictionless Data, CKAN],
)

Every format can appear on either side of a transformation — `input xsd` / `output jsch` converts XSD to JSON Schema, `input avro` / `output proto` converts Avro to Protobuf. Any combination works.

== The USDL Bridge

All schema conversions go through USDL (Chapter 12) as the intermediate representation:

```
XSD ──→ USDL ──→ JSON Schema
Avro ──→ USDL ──→ Protobuf
EDMX ──→ USDL ──→ XSD
```

USDL's classification determines what survives the conversion:
- *Core:* types, names, descriptions — every format has these, always converted
- *Shared:* constraints, validation — converted when the target format supports them, otherwise preserved as comments
- *Format-native:* features unique to one format (XSD facets, Protobuf field numbers, Avro logical types) — converted for the matching format, documented for others

This means a schema conversion is never lossy in a destructive way — information that can't be expressed in the target format is preserved as metadata or comments, not silently dropped.

== XSD (XML Schema Definition)

The most complex and feature-rich schema format. XSD defines the structure of XML documents with types, constraints, inheritance, and namespace management.

```utlx
%utlx 1.0
input xsd
output yaml
---
$input
```

This reads an XSD and outputs it as readable YAML with USDL directives — often the fastest way to understand a complex enterprise schema.

=== XSD Features Supported

- Complex types and simple types
- Restrictions (minLength, maxLength, pattern, enumeration, min/maxInclusive)
- Extensions and inheritance
- Element references (`ref`)
- `xs:include` and `xs:import` (same and cross-namespace composition)
- Global and local elements and types
- All five design patterns: Russian Doll, Venetian Blind, Salami Slice, Garden of Eden, Swiss Army Knife (see Chapter 29)
- Annotations and documentation

=== XSD Output Options

```utlx
output xsd                                    // default
output xsd {pattern: "venetian-blind"}        // generate Venetian Blind pattern
output xsd {version: "1.1"}                   // XSD 1.1 output
output xsd {addDocumentation: true}           // include xs:documentation from USDL descriptions
output xsd {elementFormDefault: "qualified"}  // element form
```

=== Stdlib Functions

```utlx
let schema = parseXSDSchema(xsdString)         // XSD XML string → USDL
let xsd = renderXSDSchema(usdlSchema)          // USDL → XSD XML string
let xsd = renderXSDSchema(usdlSchema, true)    // pretty-printed
```

== JSON Schema

The schema format for REST APIs and modern web services. JSON Schema validates JSON data structure, types, and constraints.

```utlx
%utlx 1.0
input jsch
output yaml
---
$input
```

=== Supported Drafts

UTL-X reads draft-07 and 2020-12. Output is always 2020-12 (the current standard). When reading draft-07, keywords like `definitions` are automatically converted to the 2020-12 equivalent `$defs`.

=== JSON Schema Features Supported

- Object types with `properties`, `required`, `additionalProperties`
- Array types with `items`, `minItems`, `maxItems`, `uniqueItems`
- String constraints: `minLength`, `maxLength`, `pattern`, `format`, `enum`
- Number constraints: `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`
- Composition: `$ref`, `$defs`, `allOf`, `anyOf`, `oneOf`, `not`
- `const`, `default`, `examples`, `description`
- Draft 2020-12 additions: `$dynamicRef`, `$dynamicAnchor`, `unevaluatedProperties`

=== Stdlib Functions

```utlx
let schema = parseJSONSchema(jsonSchemaString)   // JSON Schema string → USDL
let jsch = renderJSONSchema(usdlSchema)          // USDL → JSON Schema string
let jsch = renderJSONSchema(usdlSchema, true)    // pretty-printed
```

== Avro Schema

Apache Avro is the schema format for data pipelines — Kafka, Spark, Hadoop, and data lake architectures. Avro schemas are JSON documents that define record structures with strict typing and schema evolution support.

```utlx
%utlx 1.0
input avro
output yaml
---
$input
```

=== Avro Features Supported

- Primitive types: null, boolean, int, long, float, double, bytes, string
- Complex types: records, enums, arrays, maps, unions, fixed
- Logical types: date, time-millis, time-micros, timestamp-millis, timestamp-micros, decimal, uuid
- Namespace and name resolution
- Field defaults and ordering
- Aliases (field and type renaming for schema evolution)
- Documentation strings

=== Schema Evolution

Avro's schema evolution support — adding fields with defaults, renaming via aliases — is preserved through USDL. When converting Avro to JSON Schema or XSD, aliases become documentation annotations.

=== Stdlib Functions

```utlx
let schema = parseAvroSchema(avroSchemaString)   // Avro JSON string → USDL
let avro = renderAvroSchema(usdlSchema)          // USDL → Avro JSON string
let avro = renderAvroSchema(usdlSchema, true)    // pretty-printed
```

== Protobuf (Protocol Buffers)

Protocol Buffers define the message format for gRPC services and Google APIs. UTL-X supports Proto3 (the current version).

```utlx
%utlx 1.0
input proto
output yaml
---
$input
```

=== Protobuf Features Supported

- Messages with typed fields
- Enums (with zero-value requirement)
- Repeated fields (arrays)
- Map fields
- Oneof groups
- Nested messages
- Package and import declarations
- Field numbers (preserved as `%field_number` metadata for round-trip fidelity)
- Reserved fields and ranges

=== Field Number Preservation

Protobuf field numbers are critical for wire compatibility. UTL-X preserves them through USDL's Format-native directives:

```utlx
// Input: message Order { string id = 1; double total = 3; }
// USDL: %fields: { id: {%type: "string", %fieldNumber: 1}, total: {%type: "number", %fieldNumber: 3} }
// Output: message Order { string id = 1; double total = 3; }
```

When converting Protobuf to JSON Schema, field numbers become documentation annotations. When converting back, they're restored.

=== Stdlib Functions

```utlx
let schema = parseProtobufSchema(protoString)    // .proto string → USDL
let proto = renderProtobufSchema(usdlSchema)     // USDL → .proto string
```

== OData Schema (EDMX/CSDL)

OData services publish their model as EDMX metadata documents. UTL-X reads and writes these using the `osch` format. See Chapter 27 for details on OData data transformations and EDMX structure.

```utlx
%utlx 1.0
input osch
output jsch
---
$input
```

This converts an OData entity model to JSON Schema — useful for generating API documentation or validation rules from a Dynamics 365 metadata endpoint.

== Table Schema (TSCH)

The Frictionless Data Table Schema describes the structure of tabular data — column names, types, constraints, and foreign keys. It's the metadata companion for CSV files.

```utlx
%utlx 1.0
input tsch
output yaml
---
$input
```

=== Table Schema Features Supported

- Field definitions with name, type, and description
- Type system: string, number, integer, boolean, date, time, datetime, year, yearmonth, object, array, geopoint, geojson
- Constraints: required, unique, minLength, maxLength, minimum, maximum, pattern, enum
- Primary key and foreign key declarations

Table Schema is particularly useful for documenting CSV data sources and generating validation rules for CSV imports.

== Schema-to-Schema Conversion Examples

=== XSD to JSON Schema

The most common enterprise conversion — XML-first systems moving to REST APIs:

```utlx
%utlx 1.0
input xsd
output jsch
---
$input
```

XSD complex types become JSON Schema object definitions. XSD restrictions become JSON Schema constraints. `xs:include`/`xs:import` chains are resolved and become `$ref`/`$defs`.

=== Avro to Protobuf

Data pipeline to microservice gateway:

```utlx
%utlx 1.0
input avro
output proto
---
$input
```

Avro records become Protobuf messages. Avro unions become Protobuf oneof groups. Field numbers are auto-assigned (sequential) since Avro doesn't have them.

=== JSON Schema to XSD

REST API contract to enterprise XML validation:

```utlx
%utlx 1.0
input jsch
output xsd {pattern: "venetian-blind"}
---
$input
```

JSON Schema `$ref` definitions become named XSD complex types. JSON Schema `required` becomes `minOccurs="1"`. The `pattern` option controls the XSD design pattern.

=== Any Schema to Human-Readable YAML

The quickest way to understand any schema:

```utlx
%utlx 1.0
input xsd          // or jsch, avro, proto, osch, tsch
output yaml
---
$input
```

USDL in YAML is readable without tooling — field names, types, constraints, and descriptions all visible in one place.

== Schema Functions Summary

Four schema formats expose stdlib functions for use inside transformation bodies:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Format*], [*Parse function*], [*Render function*],
  [XSD], [`parseXSDSchema(str)`], [`renderXSDSchema(usdl, pretty?)`],
  [JSON Schema], [`parseJSONSchema(str)`], [`renderJSONSchema(usdl, pretty?)`],
  [Avro], [`parseAvroSchema(str)`], [`renderAvroSchema(usdl, pretty?)`],
  [Protobuf], [`parseProtobufSchema(str)`], [`renderProtobufSchema(usdl)`],
)

These are for programmatic schema manipulation within a transformation — for example, reading an embedded schema string from a message field and extracting type information. For normal schema-to-schema conversion, use `input`/`output` format declarations in the header.
