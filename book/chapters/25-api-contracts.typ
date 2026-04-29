= API Contracts and Data Contracts

== What Is an API Contract?
// - A formal specification of what an API accepts and returns
// - The agreement between producer (API provider) and consumer (API caller)
// - Defines: endpoints, methods, request/response schemas, error formats, authentication
// - The basis for: code generation, documentation, testing, validation, governance

== Two Dimensions of Contracts

=== Hierarchical Contracts (API-Centric)
// - Defined from the API perspective: "Here is my API, here are its operations"
// - Top-down: API → operations → request/response → schemas
// - Tied to transport protocol (HTTP, SOAP, gRPC)
// - Examples: OpenAPI, RAML, SOAP/WSDL, AsyncAPI, gRPC proto
//
// Structure:
// API Specification
// ├── Endpoints / Operations
// │   ├── Request schema
// │   └── Response schema
// ├── Authentication
// ├── Error definitions
// └── Documentation

=== Relational Contracts (Data-Centric)
// - Defined from the data perspective: "Here is my data, here are its quality guarantees"
// - Bottom-up: data → schema → quality rules → SLA → ownership
// - Transport-agnostic: the contract is about the data, not how it's delivered
// - Examples: Data Contract (datacontract.com), ODCS (PayPal), Data Mesh contracts
//
// Structure:
// Data Contract
// ├── Schema (structure of the data)
// ├── Quality rules (semantic validation)
// ├── SLA (freshness, availability, completeness)
// ├── Ownership (team, domain, contact)
// └── Lineage (where data comes from)

=== Key Difference
// Hierarchical: "My API exposes endpoint /orders that returns OrderResponse"
// Relational: "The orders dataset has these fields, this quality, this freshness"
//
// Hierarchical contracts describe HOW to access data.
// Relational contracts describe WHAT the data IS and its GUARANTEES.
//
// Both are needed in enterprise integration:
// - Hierarchical: for API integration (calling services)
// - Relational: for data pipelines (data quality, governance)

== Hierarchical API Contract Standards

=== OpenAPI (REST APIs)
// - The dominant REST API specification standard (formerly Swagger)
// - Versions: 2.0 (Swagger), 3.0, 3.1
// - Defines: paths, operations, parameters, request/response bodies, security
// - Schema language: JSON Schema (embedded in the OpenAPI spec)
// - Tooling: Swagger UI, code generators, API gateways
//
// Abstract vs Concrete:
// - Abstract: JSON Schema $ref references define data shapes
// - Concrete: the full OpenAPI spec with endpoints, auth, servers

=== RAML (REST APIs — MuleSoft)
// - RESTful API Modeling Language
// - Created by MuleSoft, now community-maintained
// - Key feature: RAML Fragments — reusable schema pieces
//   - DataType fragments: standalone schema definitions
//   - Trait fragments: reusable operation patterns
//   - Library fragments: collections of types and traits
// - Schema language: RAML types (superset of JSON Schema)
//
// Abstract: RAML DataType fragments (reusable across APIs)
// Concrete: Full RAML API spec with resources, methods, security

=== SOAP / WSDL (XML Web Services)
// - Web Services Description Language — the original API contract
// - XML-based: defines operations, messages, port types, bindings
// - Schema language: XSD (XML Schema Definition)
// - Still dominant in: banking, insurance, government, healthcare
//
// Abstract: XSD types and elements (reusable across services)
// Concrete: Full WSDL with portType, binding, service, endpoint

=== AsyncAPI (Event-Driven APIs)
// - OpenAPI for event-driven architectures
// - Defines: channels, messages, servers, bindings
// - Supports: Kafka, AMQP, MQTT, WebSocket, Service Bus
// - Schema language: JSON Schema, Avro, Protobuf
//
// Abstract: Message schemas (Avro, JSON Schema, Protobuf)
// Concrete: Full AsyncAPI spec with channels, bindings, servers

=== gRPC / Protocol Buffers
// - Google's RPC framework
// - Contract: .proto files define services, methods, messages
// - Schema language: Protobuf message definitions
// - Binary protocol: efficient serialization
//
// Abstract: Protobuf message definitions (reusable across services)
// Concrete: Full .proto file with service definitions and methods

== Abstract vs Concrete API Contracts

=== Abstract Contracts (Schema-Only)
// - Define the SHAPE of data without API-specific details
// - Reusable across multiple APIs and protocols
// - Examples:
//   - JSON Schema: {"type": "object", "properties": {"name": {"type": "string"}}}
//   - XSD: <xs:complexType name="CustomerType"><xs:element name="name" type="xs:string"/></...>
//   - RAML DataType Fragment: defines a type independently of any API
//   - Avro Schema: {"type": "record", "name": "Customer", "fields": [...]}
//   - Protobuf Message: message Customer { string name = 1; }
//
// Abstract contracts answer: "What does the data look like?"
// They say nothing about: endpoints, HTTP methods, authentication, servers

=== Concrete Contracts (Full API Specification)
// - Define the complete API including transport, operations, and data shapes
// - Tie abstract schemas to specific endpoints and protocols
// - Examples:
//   - OpenAPI: GET /customers → returns CustomerResponse (JSON Schema $ref)
//   - WSDL: GetCustomer operation → CustomerType (XSD $ref)
//   - AsyncAPI: orders-topic → OrderCreated message (Avro schema $ref)
//   - gRPC .proto: service CustomerService { rpc GetCustomer(...) returns (...); }
//   - RAML: /customers: get: responses: 200: body: application/json: type: Customer
//
// Concrete contracts answer: "How do I call this API and what do I get back?"
// They reference abstract contracts (schemas) for the data shape.

=== The Relationship
// Concrete contracts REFERENCE abstract contracts:
//
// OpenAPI spec
// └── $ref: "#/components/schemas/Customer"  ← JSON Schema (abstract)
//
// WSDL
// └── <wsdl:types><xsd:import .../></wsdl:types>  ← XSD (abstract)
//
// AsyncAPI
// └── $ref: "./schemas/Order.avsc"  ← Avro schema (abstract)
//
// This separation enables:
// - Schema reuse across multiple APIs
// - Independent evolution (schema can change without API change)
// - Cross-protocol compatibility (same schema, HTTP and Kafka)

== Relational Data Contract Standards

=== Data Contract (datacontract.com)
// - Open standard for data contracts
// - YAML-based specification
// - Defines: schema, quality, SLA, ownership, terms
// - Used in: Data Mesh architectures, data platform teams
//
// Structure:
// dataContractSpecification: 0.9.3
// id: orders-contract
// info:
//   title: Orders Dataset
//   owner: order-team
// schema:
//   type: object
//   properties:
//     orderId: { type: string, required: true }
//     total: { type: number, minimum: 0 }
// quality:
//   - type: completeness
//     field: orderId
//     threshold: 99.9%
// sla:
//   freshness: PT1H (ISO 8601 duration: max 1 hour old)
//   availability: 99.9%
//
// Key difference from OpenAPI: no endpoints, no HTTP methods.
// It describes the DATA, not the API.

=== ODCS (Open Data Contract Standard — PayPal)
// - PayPal's open-source data contract standard
// - Focus: data quality, governance, lineage, SLA
// - More detailed than datacontract.com on quality rules
// - Includes: data classification (PII, sensitive, public)
//
// Structure:
// - Dataset: name, description, domain, owner
// - Schema: fields with types, descriptions, constraints
// - Quality: accuracy, completeness, timeliness, uniqueness rules
// - SLA: availability, latency, retention
// - Security: classification, access control, encryption
// - Lineage: source systems, transformations applied
//
// ODCS answers: "What guarantees does this data come with?"

=== Comparison: datacontract.com vs ODCS

// | Aspect | datacontract.com | ODCS (PayPal) |
// |--------|------------------|---------------|
// | Format | YAML | YAML/JSON |
// | Schema | JSON Schema-like | Custom + JSON Schema |
// | Quality rules | Basic (threshold) | Detailed (accuracy, timeliness) |
// | SLA | Freshness, availability | + Latency, retention |
// | Security | Minimal | Classification, PII tagging |
// | Lineage | Minimal | Source systems, transforms |
// | Governance | Owner, team | + Domain, steward, approvers |
// | Adoption | Broader community | PayPal + enterprises |

== How UTL-X Relates to API and Data Contracts

=== UTL-X as the Transformation Layer
// UTL-X sits BETWEEN contract boundaries:
//
// Producer API                      Consumer API
// (OpenAPI spec)                    (OpenAPI spec)
//      │                                 ▲
//      │ response (schema A)             │ request (schema B)
//      ▼                                 │
//   ┌─────────────────────────────────────┐
//   │         UTL-X Transformation         │
//   │  input: schema A → output: schema B  │
//   │  + validation against both schemas   │
//   └─────────────────────────────────────┘
//
// The input schema comes from the producer's contract.
// The output schema comes from the consumer's contract.
// UTL-X transforms between them.

=== Schema Formats in UTL-X
// UTL-X already supports the abstract contract schemas:
// - JSON Schema (from OpenAPI, AsyncAPI, datacontract.com)
// - XSD (from WSDL/SOAP)
// - Avro (from AsyncAPI, Kafka)
// - Protobuf (from gRPC)
// - OSCH/EDMX (from OData)
// - TSCH (from Frictionless Data / CSV contracts)
//
// This means UTL-X can validate against ANY abstract contract schema.

=== Future: Data Contract Integration
// - Read a datacontract.com YAML → extract schema → validate transformation output
// - Read an ODCS contract → apply quality rules as semantic validation
// - Generate UTL-X transformations FROM contract differences (schema A → schema B)
// - Contract-driven testing: use contract as test oracle

=== Future: Contract-Driven Transformation
// - Given: producer contract (OpenAPI, WSDL, AsyncAPI)
// - Given: consumer contract (different schema)
// - Generate: UTL-X transformation skeleton that maps between them
// - Validate: output matches consumer contract schema
// - Test: conformance suite generated from both contracts
//
// This is the ultimate vision: contracts drive transformations automatically.
