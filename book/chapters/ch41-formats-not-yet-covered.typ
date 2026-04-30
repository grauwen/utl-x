= Formats Not Yet Covered (and Why)

== Formats on the Horizon

UTL-X currently supports 5 data formats (Tier 1) and 6 schema formats (Tier 2). Several important formats are NOT yet supported. This chapter explains each, why they matter, and the path to support.

== TOML (Tom's Obvious Minimal Language)
// - Configuration format (Rust ecosystem: Cargo.toml, pyproject.toml)
// - Similar to YAML but stricter (no ambiguous types)
// - Why not yet: lower priority — YAML covers most config use cases
// - Effort to add: small (TOML is simpler than YAML)
// - Would enable: Cargo.toml ↔ package.json ↔ setup.cfg conversions

== JSON-LD (JSON for Linked Data)
// - W3C standard for linked data in JSON
// - Used by: Schema.org, Google Knowledge Graph, ActivityPub (Mastodon)
// - Key difference from JSON: @context, @id, @type, @graph annotations
// - Semantic web: URIs as identifiers, RDF triples under the hood
// - Why not yet: specialized use case, complex semantics (RDF reasoning)
// - Effort to add: medium (parsing is JSON, but @context resolution is complex)
// - Would enable: Schema.org metadata extraction, linked data pipelines

== HL7 FHIR (Fast Healthcare Interoperability Resources)
// - Healthcare data exchange standard (see Chapter on FHIR analysis)
// - XML and JSON representations with different conventions:
//   - XML: value attributes instead of text content
//   - JSON: _element convention for extensions, resourceType root
// - Why not yet as a native format: requires FHIR StructureDefinition knowledge
//   for type coercion, array handling, and extension convention
// - Current support: explicit mapping works perfectly (see FHIR analysis doc)
//   - $input.Patient.id.@value → "12345" (works today)
// - Effort for native FHIR mode: significant (needs StructureDefinition database)
// - Future: output json {format: "fhir"} could handle FHIR-specific conventions

== EDI / EDIFACT (Electronic Data Interchange)
// - UN/EDIFACT: international EDI standard (dominant in EU)
// - ANSI X12: North American EDI standard
// - Used by: logistics, retail, automotive, government procurement
// - Segment-based: UNH+1+ORDERS:D:96A:UN' (not XML, not JSON)
// - Why not yet: complex segment/element/composite parsing, thousands of message types
// - Effort to add: large (parser + segment definitions + validation rules)
// - Current workaround: EDI → XML (via external tool) → UTL-X → target format
// - Would enable: direct EDI-to-JSON transformation (huge for logistics)
// - Competing tools: Smooks (Java), BizTalk (Microsoft), Gentran (IBM)

== HL7 v2 (Health Level Seven Version 2)
// - Pipe-delimited healthcare messaging standard
// - Format: MSH|^~\&|SendApp|SendFac|RecvApp|RecvFac|...
// - Segments: PID (patient), OBR (order), OBX (observation), etc.
// - Why not yet: specialized parser needed (pipe/caret/tilde delimiters)
// - Effort to add: medium (well-documented segment structure)
// - Current workaround: HL7 v2 → JSON (via HAPI/Mirth) → UTL-X → FHIR
// - Would enable: direct HL7 v2 → FHIR transformation (healthcare holy grail)

== Parquet / Arrow (Columnar Data)
// - Apache Parquet: columnar storage format for big data
// - Apache Arrow: in-memory columnar format
// - Used by: Spark, Databricks, Snowflake, BigQuery, pandas
// - Why not yet: binary format, requires native library (not pure JVM)
// - Effort to add: large (binary parsing, schema evolution, compression)
// - Would enable: ETL pipeline integration, data lake transformations

== MessagePack / CBOR (Binary JSON)
// - MessagePack: binary JSON (smaller, faster than JSON)
// - CBOR: Concise Binary Object Representation (IETF RFC 8949)
// - Used by: IoT devices, Redis, embedded systems
// - Why not yet: lower priority — JSON covers the same structure
// - Effort to add: small (same data model as JSON, just binary encoding)
// - Would enable: IoT message transformation without JSON round-trip

== GraphQL
// - Not a data format but a query language with its own schema system
// - GraphQL Schema Definition Language (SDL)
// - Why not yet: GraphQL responses are JSON (already supported)
// - What could be added: SDL → JSON Schema conversion, query result normalization
// - Effort: medium (SDL parser, response flattening)

== Apache Thrift
// - Facebook's RPC framework (predecessor to gRPC)
// - .thrift IDL files define services and types
// - Why not yet: declining usage (gRPC/Protobuf won)
// - Effort: medium (IDL parser similar to Protobuf)
// - Low priority unless specific customer demand

== FlatBuffers
// - Google's serialization format (alternative to Protobuf)
// - Zero-copy deserialization (no parsing step)
// - Used by: game engines, Android, performance-critical applications
// - Why not yet: very specialized, small user base
// - Effort: large (binary format with offset-based access)

== ORC (Apache Optimized Row Columnar)
// - Apache ORC: optimized columnar storage format for Hadoop ecosystem
// - Used by: Apache Hive, Presto, Trino, Spark, data warehouses
// - Features: built-in indexes, statistics, predicate pushdown, ACID transactions
// - Why not yet: binary format, Hadoop-specific, overlaps with Parquet use cases
// - Effort to add: large (binary parsing, stripe/footer structure, compression codecs)
// - Difference from Parquet: ORC is optimized for Hive workloads (predicate pushdown, ACID);
//   Parquet is more general-purpose and widely adopted outside Hadoop
// - Would enable: direct Hive/data warehouse integration, ORC→JSON/CSV for reporting
// - Current workaround: ORC → CSV/JSON (via Hive/Spark) → UTL-X → target format

== RAML (RESTful API Modeling Language)
// - API description language by MuleSoft (now part of Salesforce)
// - Used by: MuleSoft Anypoint, API-first development teams
// - YAML-based syntax but with its own semantics (not just OpenAPI in YAML)
//
// Two versions with important differences:
//
// RAML 0.8 (legacy):
//   - Basic !include for splitting files (untyped — includes raw text)
//   - Schema keyword: `schema:` for inline or external JSON Schema references
//   - Supports including external JSON Schema files: schema: !include user.json
//   - No typed fragments — every include is raw text insertion
//   - No RAML-native type system — relies on JSON Schema for data types
//
// RAML 1.0 (current):
//   - Typed fragments: DataType, Trait, ResourceType, SecurityScheme, Library, etc.
//     Each fragment has a header declaring its type (e.g., #%RAML 1.0 DataType)
//   - Native type system: unions, inheritance, facets, examples, annotations
//     RAML types are more expressive than JSON Schema for common API patterns
//   - Libraries: reusable collections of types, traits, and resource types
//   - Overlays and extensions: modify an API spec without changing the original
//   - JSON Schema inclusion: `type: !include user.json` — RAML 1.0 can include
//     external JSON Schema files as type definitions. The JSON Schema is treated
//     as a RAML type (with limitations — no RAML-specific facets on JSON Schema types)
//   - Both RAML 0.8 and 1.0 support JSON Schema, but 1.0 also has its own type system
//     that is often preferred because RAML types support inheritance and examples natively
//
// RAML fragments (1.0 only) are particularly interesting for UTL-X:
//   - A DataType fragment is essentially a standalone type definition file
//   - It looks like a mini-schema: properties, types, constraints, examples
//   - These are what MuleSoft projects use for defining API contracts
//   - Converting RAML DataType fragments → JSON Schema / USDL is the key migration path
//
// Why not yet: declining relative to OpenAPI 3.x, MuleSoft-centric ecosystem
// Effort to add: medium (YAML-based parsing, but type system is complex)
// Key value: RAML type definitions and fragments → JSON Schema / XSD / USDL conversion
// Would enable: MuleSoft-to-non-MuleSoft API migrations
// Current workaround: RAML → OAS 3.0 (via oas-raml-converter) → UTL-X → target schema

== JSON Sample Generation (Mock Data)
// - Given a schema (JSON Schema, XSD, Avro, USDL), generate a sample JSON instance
// - Use cases:
//   - API mocking: generate realistic sample responses for testing
//   - Documentation: show "what does a valid message look like?"
//   - Test data: seed test suites with conforming instances
//   - Onboarding: new developers see concrete examples from abstract schemas
//
// How it could work in UTL-X:
//   %utlx 1.0
//   input jsch                    // or xsd, avro, proto
//   output json {mode: "sample"}  // generate a sample instance, not transform
//   ---
//   $input
//
// The serializer would:
//   - Use %default values where defined
//   - Use %example values where defined
//   - Generate realistic fake data for types (string → "example", integer → 42)
//   - Respect constraints (minLength, pattern, enum → pick first value)
//   - Generate nested structures following the schema hierarchy
//   - Handle arrays (generate 1-2 sample items)
//
// This is NOT implemented yet but would be a natural extension of the
// schema-as-data model. The schema already contains types, constraints,
// and examples — generating a conforming instance is a transformation
// from schema to data.
//
// Current workaround: write a UTL-X transformation that reads the schema
// and constructs a sample manually:
//   map($input["%types"], (typeName, typeDef) -> {
//     [typeName]: mapValues(typeDef["%fields"], (field) ->
//       field["%default"] ?? field["%example"] ?? sampleForType(field["%type"])
//     )
//   })
//
// Related tools: json-schema-faker, Prism (Stoplight), WireMock, Faker.js

== Summary: Format Priority Matrix

// | Format | Market demand | Effort | Priority | Current workaround |
// |--------|-------------|--------|----------|-------------------|
// | **EDI/EDIFACT** | High (logistics, retail) | Large | Medium | EDI→XML→UTL-X |
// | **HL7 v2** | High (healthcare) | Medium | Medium | HL7→JSON→UTL-X |
// | **FHIR native** | Medium (healthcare) | Large | Low | Explicit @value mapping works |
// | **TOML** | Medium (dev tools) | Small | Low | YAML covers most cases |
// | **JSON-LD** | Low (semantic web) | Medium | Low | JSON parsing works, @context manual |
// | **Parquet** | High (big data) | Large | Medium | External tools for conversion |
// | **MessagePack** | Low (IoT) | Small | Low | JSON round-trip |
// | **GraphQL SDL** | Medium (API) | Medium | Low | JSON responses already work |
// | **ORC** | Medium (data warehouses) | Large | Low | ORC→CSV via Hive/Spark |
// | **RAML** | Medium (MuleSoft) | Medium | Low | RAML→OAS converter |
// | **JSON Sample Gen** | Medium (testing/mocking) | Small | Medium | Manual transformation |
// | **Thrift** | Low (declining) | Medium | None | Protobuf preferred |
// | **FlatBuffers** | Low (games) | Large | None | Too specialized |
