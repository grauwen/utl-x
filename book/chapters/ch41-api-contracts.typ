= API Contracts and Data Contracts

In enterprise integration, the most expensive bugs come from mismatched expectations: the producer sends data in one shape, the consumer expects another. API contracts and data contracts formalize these expectations — they are the agreements that prevent "it works on my machine" from becoming "it broke in production."

This chapter covers the two dimensions of contracts, the major standards, and how UTL-X fits as the transformation layer between contract boundaries.

== Two Dimensions of Contracts

=== Hierarchical Contracts (API-Centric)

Defined from the API perspective: "Here is my API, here are its operations."

```
API Specification
├── Endpoints / Operations
│   ├── Request schema
│   └── Response schema
├── Authentication
├── Error definitions
└── Documentation
```

Examples: OpenAPI, RAML, SOAP/WSDL, AsyncAPI, gRPC `.proto`

Hierarchical contracts describe *how to access data* — endpoints, methods, authentication, protocols.

=== Relational Contracts (Data-Centric)

Defined from the data perspective: "Here is my data, here are its quality guarantees."

```
Data Contract
├── Schema (structure of the data)
├── Quality rules (semantic validation)
├── SLA (freshness, availability, completeness)
├── Ownership (team, domain, contact)
└── Lineage (where data comes from)
```

Examples: Data Contract (datacontract.com), ODCS (PayPal), Data Mesh contracts

Relational contracts describe *what the data is and its guarantees* — independent of how it's delivered.

=== The Key Difference

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*Hierarchical (API)*], [*Relational (Data)*],
  [Perspective], [How to access data], [What the data is],
  [Transport], [Tied to protocol (HTTP, gRPC, SOAP)], [Transport-agnostic],
  [Focus], [Operations and endpoints], [Quality and governance],
  [Schema], [Embedded in API spec], [Standalone, reusable],
  [SLA], [Uptime and latency], [Freshness and completeness],
  [Ownership], [API team], [Data domain team],
  [Example], [GET /orders returns OrderResponse], [Orders dataset has 99.9% completeness],
)

Both are needed: hierarchical for API integration (calling services), relational for data pipelines (data quality, governance).

== Hierarchical API Contract Standards

=== OpenAPI (REST APIs)

The dominant REST API specification standard (formerly Swagger). Versions 2.0, 3.0, and 3.1.

OpenAPI defines endpoints, operations, parameters, request/response bodies, security schemes, and servers. The data schema language is JSON Schema, embedded in the OpenAPI spec under `components/schemas`.

UTL-X can process OpenAPI specs as YAML input — extract endpoints, schemas, and parameters for transformation or documentation:

```utlx
%utlx 1.0
input yaml
output json
---
// Extract all endpoints from an OpenAPI 3.0 spec
flatten(map(keys($input.paths), (path) ->
  map(keys($input.paths[path]), (method) -> {
    let op = $input.paths[path][method]
    path: path,
    method: toUpperCase(method),
    operationId: op.operationId,
    summary: op.summary ?? "",
    tags: op.tags ?? []
  })
))
```

=== SOAP / WSDL (XML Web Services)

The original API contract. WSDL defines operations, messages, port types, and bindings in XML. The schema language is XSD.

Still dominant in banking, insurance, government, and healthcare. UTL-X handles WSDL's XSD types directly — parse the types section, transform messages against the schema. See Chapter 22 for SOAP handling and Chapter 29 for XSD patterns.

=== AsyncAPI (Event-Driven APIs)

OpenAPI for event-driven architectures. Defines channels, messages, servers, and protocol bindings for Kafka, AMQP, MQTT, WebSocket, and Service Bus.

AsyncAPI supports multiple schema languages: JSON Schema, Avro, and Protobuf. UTL-X handles all three — making it suitable for transforming messages across event-driven systems regardless of the schema format.

=== gRPC / Protocol Buffers

Google's RPC framework. The contract is the `.proto` file that defines services, methods, and message types. UTL-X can read and write Protobuf schemas (Chapter 28), and the Go and Java wrappers (Chapter 34) communicate with UTLXe via protobuf over stdio.

== Abstract vs Concrete Contracts

Every API contract has two layers:

=== Abstract Contracts (Schema-Only)

Define the *shape* of data without API-specific details. Reusable across multiple APIs and protocols:

- JSON Schema: `{"type": "object", "properties": {"name": {"type": "string"}}}`
- XSD: `<xs:complexType name="CustomerType">...</xs:complexType>`
- Avro: `{"type": "record", "name": "Customer", "fields": [...]}`
- Protobuf: `message Customer { string name = 1; }`
- RAML DataType Fragment: standalone type definition

Abstract contracts answer: *"What does the data look like?"*

=== Concrete Contracts (Full API Specification)

Tie abstract schemas to specific endpoints, protocols, and operations:

- OpenAPI: `GET /customers` returns `CustomerResponse` (references JSON Schema)
- WSDL: `GetCustomer` operation returns `CustomerType` (references XSD)
- AsyncAPI: `orders-topic` carries `OrderCreated` message (references Avro)
- gRPC: `service CustomerService { rpc GetCustomer(...) returns (...); }`

Concrete contracts answer: *"How do I call this API and what do I get back?"*

=== The Relationship

Concrete contracts *reference* abstract contracts:

```
OpenAPI spec → $ref: "#/components/schemas/Customer" → JSON Schema (abstract)
WSDL         → <xsd:import .../>                     → XSD (abstract)
AsyncAPI     → $ref: "./schemas/Order.avsc"           → Avro schema (abstract)
```

This separation enables schema reuse across multiple APIs, independent evolution (schema changes without API changes), and cross-protocol compatibility (same schema for HTTP and Kafka).

UTL-X works at the *abstract contract layer* — it reads and writes the schemas (JSON Schema, XSD, Avro, Protobuf, EDMX, Table Schema) that API contracts reference.

== Relational Data Contract Standards

=== Data Contract (datacontract.com)

The open standard for data contracts in Data Mesh architectures:

```yaml
dataContractSpecification: 0.9.3
id: orders-contract
info:
  title: Orders Dataset
  owner: order-team
schema:
  type: object
  properties:
    orderId: {type: string, required: true}
    total: {type: number, minimum: 0}
quality:
  - type: completeness
    field: orderId
    threshold: 99.9%
sla:
  freshness: PT1H    # max 1 hour old (ISO 8601 duration)
  availability: 99.9%
```

Key difference from OpenAPI: no endpoints, no HTTP methods. It describes the *data*, not the API. Quality rules, SLA, and ownership are first-class concerns — not afterthoughts.

=== ODCS (Open Data Contract Standard — PayPal)

PayPal's open-source data contract standard, more detailed than datacontract.com on quality and governance:

- *Quality:* accuracy, completeness, timeliness, uniqueness — with specific rules and thresholds
- *Security:* data classification (PII, sensitive, public), access control
- *Lineage:* source systems, transformations applied
- *Governance:* domain, steward, approvers, review cadence

=== Comparison

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*datacontract.com*], [*ODCS (PayPal)*],
  [Schema], [JSON Schema-like], [Custom + JSON Schema],
  [Quality rules], [Basic (threshold)], [Detailed (accuracy, timeliness)],
  [SLA], [Freshness, availability], [+ Latency, retention],
  [Security], [Minimal], [Classification, PII tagging],
  [Lineage], [Minimal], [Source systems, transforms],
  [Governance], [Owner, team], [+ Domain, steward, approvers],
  [Adoption], [Broader community], [PayPal + enterprises],
)

== How UTL-X Fits in the Contract Landscape

=== UTL-X as the Transformation Layer

UTL-X sits *between* contract boundaries — where data from one contract must be transformed to match another:

```
Producer API                        Consumer API
(OpenAPI spec)                      (OpenAPI spec)
     │                                    ▲
     │ response (schema A)                │ request (schema B)
     ▼                                    │
  ┌──────────────────────────────────────────┐
  │           UTL-X Transformation            │
  │    input: schema A → output: schema B     │
  │    + validation against both schemas      │
  └──────────────────────────────────────────┘
```

The input schema comes from the producer's contract. The output schema comes from the consumer's contract. UTL-X transforms between them — and can validate against both (Chapter 19).

=== Schema Format Coverage

UTL-X supports all major abstract contract schema formats:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*API Contract*], [*Schema Format*], [*UTL-X support*],
  [OpenAPI], [JSON Schema], [Read + write + validate],
  [WSDL/SOAP], [XSD], [Read + write + validate],
  [AsyncAPI], [JSON Schema / Avro / Protobuf], [All three supported],
  [gRPC], [Protobuf], [Read + write],
  [OData], [EDMX/CSDL], [Read + write + validate],
  [Frictionless Data], [Table Schema], [Read + write + validate],
  [datacontract.com], [JSON Schema-like], [Read via YAML input],
  [ODCS], [JSON Schema], [Read via YAML/JSON input],
)

=== Contract-Driven Transformation Development

The ideal workflow:

+ *Receive* both contracts: producer's API spec + consumer's API spec
+ *Extract* schemas from both contracts (JSON Schema, XSD, etc.)
+ *Write* the UTL-X transformation that maps schema A to schema B
+ *Validate* input against schema A (pre-validation) and output against schema B (post-validation)
+ *Test* with sample data generated from the schemas
+ *Deploy* with confidence: the transformation is contract-verified

UTL-X makes this practical because it reads the same schema formats that API contracts reference. The schemas in the OpenAPI spec or WSDL are the same schemas UTLXe validates against.

=== Data Contract Processing

UTL-X can process data contracts as YAML input — extracting schemas, quality rules, and metadata:

```utlx
%utlx 1.0
input yaml
output json
---
// Extract the schema from a datacontract.com specification
{
  contractId: $input.id,
  owner: $input.info.owner,
  fields: entries($input.schema.properties) |> map((entry) -> {
    name: entry[0],
    type: entry[1].type,
    required: entry[1].required ?? false,
    description: entry[1].description ?? ""
  }),
  qualityRules: $input.quality ?? [],
  sla: $input.sla ?? {}
}
```

This transforms a data contract into a structured JSON report — useful for governance dashboards, contract registries, or generating validation rules.

== The Contract-Driven Future

=== Contract Differencing

Given two versions of a contract (v1 and v2), UTL-X could detect schema differences and generate the transformation that bridges them:

```
Contract v1 (schema A) ──→ diff ──→ Contract v2 (schema B)
                            │
                            ▼
              Generated .utlx migration script
```

This is schema evolution automation — detect what changed (renamed field, new required field, type change) and produce the mapping that handles it.

=== Contract as Test Oracle

Data contracts define what valid data looks like — quality thresholds, completeness rules, SLA targets. These can be converted into conformance tests:

- *Completeness:* generate test data with missing fields → verify transformation handles them
- *Type constraints:* generate boundary values → verify no overflow or truncation
- *Quality thresholds:* run against production sample → verify output meets contract SLA

The contract IS the specification. UTL-X transformations are the implementation. Conformance tests verify the implementation matches the specification.
