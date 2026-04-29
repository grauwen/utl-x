= Schema Validation

== Why Validate?
// - Catch errors at transformation time, not downstream
// - Contract enforcement between systems
// - Compliance requirements (Peppol, FHIR, EDI)

== Supported Schema Formats
// - JSON Schema (draft-04 through 2020-12)
// - XSD (XML Schema Definition)
// - Avro Schema
// - Protobuf (proto3 definitions)
// - Table Schema (Frictionless Data)
// - OData Schema (EDMX/CSDL)

== Pre-Validation and Post-Validation
// - Pre-validation: validate input before transformation
// - Post-validation: validate output after transformation
// - The validation orchestrator: PRE → TRANSFORM → POST flow
// - Validation policies: STRICT, WARN, SKIP

== JSON Schema Validation
// - Defining schemas: required fields, types, patterns, ranges
// - Example: validating an API response
// - Error messages and how to interpret them

== XSD Validation
// - Validating XML against XSD
// - Namespace-aware validation
// - Example: UBL invoice validation

== Schema-Driven Transformations
// - Using schemas to guide transformation logic
// - AUTO strategy: schema → COPY, no schema → TEMPLATE
// - Schema as documentation for the transformation contract
