= Format Support Overview

== Tier 1: Data Formats
// - JSON: the lingua franca of APIs
// - XML: the enterprise workhorse (SOAP, EDI, UBL, HL7)
// - CSV: the universal import/export (Excel, databases, reports)
// - YAML: the configuration standard (Kubernetes, CI/CD, OpenAPI)
// - OData: the Microsoft ecosystem (Dynamics 365, SharePoint, SAP)

== Tier 2: Schema Formats
// - XSD: XML Schema Definition (W3C standard)
// - JSCH: JSON Schema (draft-04 through 2020-12)
// - Avro: Apache Avro schema (Kafka, data pipelines)
// - Protobuf: Protocol Buffers (gRPC, microservices)
// - OSCH: OData Schema / EDMX (Microsoft ecosystem)
// - TSCH: Table Schema (CSV metadata, Frictionless Data)

== Format Detection
// - Auto-detection: how UTL-X identifies the input format
// - Explicit declaration: input xml, input json
// - Mixed inputs: multi-input transformations with different formats

== Format Options
// - Encoding: {encoding: "UTF-8"}, {encoding: "ISO-8859-1"}
// - CSV options: {delimiter: ";", headers: true, regionalFormat: "european"}
// - JSON options: {writeAttributes: true}
// - XML options: {encoding: "UTF-8"}
// - Pretty printing: default on, --no-pretty to disable

== Format Conversion Matrix
// - Table: which input formats can produce which output formats
// - Direct conversions vs transformations needed
// - Identity mode: automatic format flip
