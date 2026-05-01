= Formats Not Yet Covered

UTL-X supports 5 data formats (JSON, XML, CSV, YAML, OData) and 6 schema formats (XSD, JSON Schema, Avro, Protobuf, EDMX, Table Schema). That's 11 formats — more than any competing tool. But the integration world has more formats. This chapter surveys the ones UTL-X does not yet support, explains why, and describes the path to adding them.

== High Priority: EDI / EDIFACT

EDI (Electronic Data Interchange) is the backbone of logistics, retail, automotive, and government procurement. Two standards dominate:

- *UN/EDIFACT:* international standard, dominant in Europe. Used for: purchase orders (ORDERS), invoices (INVOIC), despatch advice (DESADV), customs declarations.
- *ANSI X12:* North American standard. Used for: purchase orders (850), invoices (810), advance ship notices (856).

EDI is segment-based — not XML, not JSON. A typical EDIFACT message:

```
UNH+1+ORDERS:D:96A:UN'
BGM+220+PO-001+9'
DTM+137:20260501:102'
NAD+BY+++Acme Corp'
LIN+1++WIDGET:SA'
QTY+21:100'
UNT+7+1'
```

Every segment has a tag (UNH, BGM, DTM), elements separated by `+`, and sub-elements separated by `:`. The segment terminator is `'`.

*Why not yet:* complex parsing (thousands of message types, version-dependent segment definitions), element-level validation rules, composite data elements. Adding EDI properly requires a segment dictionary — not just a parser.

*Effort:* large (parser + segment definitions + validation rules).

*Current workaround:* convert EDI to XML using an external tool (Bots, Smooks, or a commercial EDI converter), then process with UTL-X. This works but adds a preprocessing step.

*Promising integration path:* reverseXSL (Apache 2.0, Java) is an open-source "anything-to-XML" parser that handles EDIFACT, X12, IATA, SWIFT, and fixed-length records using regex-driven DEF (definition) files. It converts structured text formats to XML via a 4-step process: identify, cut, extract, validate. Since it's Java-based and Apache 2.0 licensed, it could be integrated directly into UTL-X as a pre-parser — `input edi` would invoke reverseXSL with the appropriate DEF file, producing XML that UTL-X transforms further. The DEF file approach is similar in spirit to UTL-X's declarative philosophy: describe the format, let the engine parse it.

*Would enable:* direct EDI-to-JSON transformation — eliminating the XML intermediate step. Huge value for logistics and retail integration.

== High Priority: HL7 v2

HL7 version 2 is the pipe-delimited healthcare messaging standard. Still the most widely deployed health data exchange format in the world — hospitals, labs, pharmacies, and insurance companies send billions of HL7 v2 messages daily.

```
MSH|^~\&|SendApp|SendFac|RecvApp|RecvFac|20260501||ADT^A01|MSG001|P|2.5
PID|||12345^^^MRN||Simpson^Homer^J||19550312|M
NK1|1|Simpson^Marge|SPO
PV1|1|I|ICU^Bed3
```

Segments (MSH, PID, NK1, PV1) are separated by newlines. Fields are separated by `|`. Components by `^`. Sub-components by `&`. Repetitions by `~`. Escape character `\`.

*Why not yet:* specialized parser needed (4 levels of delimiters), segment definitions vary by message type and version (2.3, 2.4, 2.5, 2.7).

*Effort:* medium (well-documented segment structure, many reference implementations).

*Current workaround:* convert HL7 v2 to JSON using HAPI (Java) or Mirth Connect, then transform with UTL-X to FHIR R4. Alternatively, reverseXSL (see EDI section above) can parse HL7 v2 pipe-delimited messages to XML using DEF files — the same tool that handles EDI and IATA formats.

*Would enable:* direct HL7 v2 to FHIR transformation — the "healthcare holy grail" for modernization projects.

== Medium Priority: Parquet / Arrow

Apache Parquet is the columnar storage format for big data — used by Spark, Databricks, Snowflake, BigQuery, and pandas. Apache Arrow is the in-memory columnar format.

*Why not yet:* binary format requiring native libraries (not pure JVM). Complex internal structure: row groups, column chunks, page headers, compression codecs (Snappy, Zstd, LZ4).

*Effort:* large.

*Current workaround:* export Parquet to CSV or JSON via Spark/pandas, then process with UTL-X.

*Would enable:* direct data lake integration — read Parquet files, transform, write back without leaving UTL-X.

== Medium Priority: TOML

Tom's Obvious Minimal Language — the configuration format of the Rust ecosystem (`Cargo.toml`), Python (`pyproject.toml`), and Hugo.

*Why not yet:* lower priority — YAML covers most configuration use cases. TOML is stricter than YAML (no ambiguous types, explicit table syntax) but less widely used in integration.

*Effort:* small (TOML is simpler than YAML, well-defined spec).

*Would enable:* `Cargo.toml` to `package.json` conversion, `pyproject.toml` processing, configuration format migration.

== Low Priority: JSON-LD

JSON for Linked Data — W3C standard for semantic web data in JSON. Used by Schema.org, Google Knowledge Graph, and ActivityPub (Mastodon/Fediverse).

Key difference from JSON: `@context`, `@id`, `@type`, `@graph` annotations that link data to ontologies via URIs.

*Why not yet:* specialized use case. The JSON is parseable today (`input json`), but `@context` resolution (following URIs to load vocabulary definitions) is complex.

*Effort:* medium (parsing is JSON, but context resolution requires HTTP fetching and RDF reasoning).

*Current workaround:* parse as JSON, access `@context`/`@type` with bracket notation.

== Low Priority: MessagePack / CBOR

Binary JSON formats — smaller and faster than text JSON, same data model.

- *MessagePack:* used by Redis, FluentBit, some game servers
- *CBOR (RFC 8949):* used by IoT devices, WebAuthn, COSE (FIDO2)

*Why not yet:* lower priority — same data model as JSON, just binary encoding.

*Effort:* small (same UDM mapping as JSON, just a different parser/serializer).

*Would enable:* IoT message transformation without JSON round-trip, Redis data processing.

== Low Priority: GraphQL SDL

GraphQL Schema Definition Language — defines types, queries, mutations, and subscriptions.

*Why not yet:* GraphQL responses are JSON (already supported). The SDL is a schema language similar to Protobuf — types, fields, enums, unions.

*Effort:* medium (SDL parser, response flattening for nested queries).

*What could be added:* SDL to JSON Schema conversion, query result normalization.

== Low Priority: ORC (Apache Optimized Row Columnar)

Apache ORC — columnar storage optimized for Hive workloads. Similar to Parquet but with built-in ACID transaction support and predicate pushdown.

*Why not yet:* binary format, Hadoop-specific, overlaps with Parquet use cases.

*Effort:* large.

*Current workaround:* export via Hive/Spark to CSV/JSON.

== Low Priority: RAML

RESTful API Modeling Language by MuleSoft. YAML-based API description with its own type system.

Two versions with important differences:

- *RAML 0.8:* basic `!include` (untyped raw text insertion), `schema:` keyword for external JSON Schema references
- *RAML 1.0:* typed fragments (DataType, Trait, ResourceType, Library), native type system (unions, inheritance, facets), `type: !include user.json` for JSON Schema inclusion

RAML 1.0 DataType fragments are the key migration artifact — standalone type definitions used across MuleSoft projects. Converting these to JSON Schema or USDL is the primary value for UTL-X.

*Why not yet:* declining relative to OpenAPI 3.x, MuleSoft-centric ecosystem.

*Effort:* medium (YAML-based parsing, but the type system with facets and inheritance is complex).

*Current workaround:* convert RAML to OpenAPI 3.0 via `oas-raml-converter`, then process with UTL-X.

== Potential: JSON Sample Generation (Mock Data)

Not a format but a capability: given a schema, generate a conforming sample instance.

Use cases:
- *API mocking:* generate realistic sample responses for testing
- *Documentation:* show "what does a valid message look like?"
- *Test data:* seed test suites with conforming instances
- *Onboarding:* new developers see concrete examples from abstract schemas

How it could work:

```utlx
%utlx 1.0
input jsch                    // or xsd, avro, proto
output json {mode: "sample"}  // generate a sample instance
---
$input
```

The serializer would use `%default` and `%example` values where defined, generate realistic fake data for types (`string` to `"example"`, `integer` to `42`), respect constraints (`minLength`, `pattern`, `enum` picks first value), and build nested structures following the schema hierarchy.

Not yet implemented but a natural extension of the schema-as-data model. Related tools: json-schema-faker, Prism (Stoplight), WireMock.

== Declining: Apache Thrift / FlatBuffers

*Apache Thrift:* Facebook's RPC framework (predecessor to gRPC). Declining usage — gRPC/Protobuf won the mindshare battle. Low priority unless specific customer demand.

*FlatBuffers:* Google's zero-copy serialization format. Used in game engines and performance-critical mobile apps. Very specialized, small user base for integration.

== Format Priority Matrix

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, left, left, left, left),
  [*Format*], [*Demand*], [*Effort*], [*Priority*], [*Workaround*],
  [EDI/EDIFACT], [High], [Large], [Medium], [EDI to XML externally],
  [HL7 v2], [High], [Medium], [Medium], [HL7 to JSON via HAPI/Mirth],
  [Parquet], [High], [Large], [Medium], [Export via Spark/pandas],
  [JSON Sample Gen], [Medium], [Small], [Medium], [Manual transformation],
  [TOML], [Medium], [Small], [Low], [YAML covers most cases],
  [FHIR native mode], [Medium], [Large], [Low], [`@value` accessor works today],
  [JSON-LD], [Low], [Medium], [Low], [Parse as JSON, manual `@context`],
  [MessagePack/CBOR], [Low], [Small], [Low], [JSON round-trip],
  [GraphQL SDL], [Medium], [Medium], [Low], [JSON responses work],
  [ORC], [Medium], [Large], [Low], [Export via Hive/Spark],
  [RAML], [Medium], [Medium], [Low], [RAML to OAS converter],
  [Thrift], [Low], [Medium], [None], [Protobuf preferred],
  [FlatBuffers], [Low], [Large], [None], [Too specialized],
)

The priority reflects both market demand and strategic value. EDI and HL7 v2 would open large markets (logistics and healthcare) currently served by expensive proprietary tools. TOML and MessagePack are small effort with moderate value. The rest have viable workarounds that reduce urgency.
