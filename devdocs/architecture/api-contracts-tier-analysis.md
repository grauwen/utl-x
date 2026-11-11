# API Contracts in UTLX Tier Architecture

**Date**: November 7, 2025  
**Topic**: Where do OpenAPI, AsyncAPI, WSDL, and other API contracts fit?  
**Version**: 1.0

---

## The Question

When API contracts like OpenAPI, AsyncAPI, SOAP/WSDL are discussed, is this:
1. Another tier (Tier 5 or Tier 0)?
2. A different dimension entirely?
3. Overlap with existing tiers?

---

## Analysis: API Contracts Are Multi-Tier

**Key Insight**: API contracts are **NOT a separate tier** — they are **composite specifications that span multiple tiers**.

API contracts are **meta-schemas** that describe:
- Data formats (Tier 1)
- Data schemas (Tier 2)
- Validation rules (Tier 3)
- Operations and behavior (NEW: Tier 5 - Protocol/Interface)

---

## The Answer: API Contracts Span Tiers + Add New Tier

### Existing Tiers (Revisited)

```
┌─────────────────────────────────────────┐
│ Tier 4: Expression/Logic                │  ← Business logic
├─────────────────────────────────────────┤
│ Tier 3: Validation                      │  ← Business rules
├─────────────────────────────────────────┤
│ Tier 2: Schema                          │  ← Structure
├─────────────────────────────────────────┤
│ Tier 1: Instance/Data                   │  ← Data
└─────────────────────────────────────────┘
```

### NEW: Add Tier 5 (or Tier 0)

```
┌─────────────────────────────────────────┐
│ Tier 5: Protocol/Interface/Contract    │  ← NEW! API behavior
├─────────────────────────────────────────┤
│ Tier 4: Expression/Logic                │
├─────────────────────────────────────────┤
│ Tier 3: Validation                      │
├─────────────────────────────────────────┤
│ Tier 2: Schema                          │
├─────────────────────────────────────────┤
│ Tier 1: Instance/Data                   │
└─────────────────────────────────────────┘
```

**Tier 5 Characteristics**:
- Describes **protocols** and **operations**
- Defines **endpoints** and **methods**
- Specifies **communication patterns**
- References schemas from Tier 2
- May include validation from Tier 3
- Documents **service interfaces**

---

## API Contract Decomposition

Let's decompose OpenAPI to see how it spans tiers:

### OpenAPI 3.0 Example

```yaml
openapi: 3.0.0
info:                              # ← TIER 5: Metadata
  title: Customer API
  version: 1.0.0

servers:                           # ← TIER 5: Protocol/Endpoints
  - url: https://api.example.com

paths:                             # ← TIER 5: Operations
  /customers:
    get:                           # ← TIER 5: HTTP Method
      summary: List customers
      parameters:                  # ← TIER 2: Parameter schemas
        - name: limit
          in: query
          schema:
            type: integer
      responses:                   # ← TIER 5: Response mapping
        '200':
          description: Success
          content:
            application/json:      # ← TIER 1: Format
              schema:              # ← TIER 2: Data schema
                type: array
                items:
                  $ref: '#/components/schemas/Customer'

components:
  schemas:                         # ← TIER 2: Reusable schemas
    Customer:
      type: object
      required:                    # ← TIER 3: Validation rules
        - id
        - name
      properties:
        id:
          type: integer
        name:
          type: string
          minLength: 1           # ← TIER 3: Validation constraint
        email:
          type: string
          format: email          # ← TIER 3: Semantic validation
```

### Tier Breakdown

| OpenAPI Section | UTLX Tier | Purpose |
|-----------------|-----------|---------|
| `openapi`, `info` | 5 | Contract metadata |
| `servers` | 5 | Protocol endpoints |
| `paths`, operations | 5 | API operations |
| `parameters` | 2 | Parameter schemas |
| `requestBody` | 1, 2 | Request data format/schema |
| `responses` | 1, 2, 5 | Response format/schema/codes |
| `components/schemas` | 2 | Data structure definitions |
| `required`, `minLength` | 3 | Validation rules |
| `security` | 5 | Authentication/authorization |

---

## All API Contract Standards Analysis

### REST API Contracts

| Standard | Tier Coverage | Primary Tier | Description |
|----------|--------------|--------------|-------------|
| **OpenAPI/Swagger** | 1, 2, 3, 5 | 5 | REST API specification |
| **RAML** | 1, 2, 3, 5 | 5 | RESTful API Modeling Language |
| **API Blueprint** | 1, 2, 5 | 5 | Markdown-based API docs |

### Async/Event API Contracts

| Standard | Tier Coverage | Primary Tier | Description |
|----------|--------------|--------------|-------------|
| **AsyncAPI** | 1, 2, 3, 5 | 5 | Event-driven/async APIs |
| **CloudEvents** | 1, 2 | 1 | Event data format standard |

### SOAP/Web Services

| Standard | Tier Coverage | Primary Tier | Description |
|----------|--------------|--------------|-------------|
| **WSDL** | 1, 2, 5 | 5 | Web Services Description Language |
| **SOAP** | 1, 5 | 5 | Simple Object Access Protocol |
| **WS-*** | 3, 5 | 5 | Web Services specifications |

### RPC/Service Definition

| Standard | Tier Coverage | Primary Tier | Description |
|----------|--------------|--------------|-------------|
| **gRPC** | 1, 2, 5 | 5 | Google RPC framework |
| **Thrift** | 1, 2, 5 | 5 | Apache Thrift RPC |
| **GraphQL Schema** | 2, 5 | 5 | GraphQL type system + operations |

### Data Contracts (Overlap)

| Standard | Tier Coverage | Primary Tier | Description |
|----------|--------------|--------------|-------------|
| **JSON Schema** | 2, 3 | 2 | Pure schema (no protocol) |
| **Avro Schema** | 2 | 2 | Schema + serialization |
| **Protobuf** | 2, 5 | 2 | Schema + optional gRPC |

---

## Revised Complete Tier Model

### 5-Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ TIER 5: PROTOCOL/INTERFACE/CONTRACT LAYER                   │
│                                                               │
│ OpenAPI | AsyncAPI | WSDL | gRPC | GraphQL | Thrift         │
│ ├─ API operations        ├─ Service definitions             │
│ ├─ Endpoints/paths       ├─ Communication patterns          │
│ ├─ HTTP methods          ├─ Authentication                  │
│ └─ Protocol bindings     └─ Error handling                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 4: EXPRESSION/LOGIC LAYER                              │
│                                                               │
│ UTLX Native | FEEL | XPath | XQuery | SPARQL | JMESPath     │
│ ├─ Transformations       ├─ Business logic                  │
│ └─ Calculations          └─ Decision rules                  │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 3: VALIDATION LAYER                                     │
│                                                               │
│ Schematron | JSON-LD | SHACL | ShEx | OWL                   │
│ ├─ Business rules        ├─ Semantic validation             │
│ └─ Constraints           └─ Domain rules                    │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 2: SCHEMA LAYER                                         │
│                                                               │
│ JSON Schema | XSD | Avro | Proto | Table Schema             │
│ ├─ Structure definition  ├─ Type systems                    │
│ └─ Format specification  └─ Field constraints               │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ TIER 1: INSTANCE/DATA LAYER                                  │
│                                                               │
│ JSON | XML | CSV | YAML | JSON-LD | Protobuf | Avro         │
│ ├─ Actual data           ├─ Serialized values               │
│ └─ Runtime messages      └─ Event payloads                  │
└─────────────────────────────────────────────────────────────┘
```

---

## How API Contracts Reference Lower Tiers

### OpenAPI Example (Full Stack)

```yaml
# TIER 5: API Contract
openapi: 3.0.0
paths:
  /orders:
    post:
      requestBody:
        content:
          application/json:                    # ← TIER 1: Format
            schema:                            # ← TIER 2: Schema
              $ref: '#/components/schemas/Order'

components:
  schemas:                                     # ← TIER 2: Schema definitions
    Order:
      type: object
      required: [id, customerId]              # ← TIER 3: Validation
      properties:
        id:
          type: string
          pattern: '^ORD-[0-9]+$'             # ← TIER 3: Validation rule
        customerId:
          type: string
        items:
          type: array
          items:
            $ref: '#/components/schemas/OrderItem'
```

### AsyncAPI Example (Event-Driven)

```yaml
# TIER 5: Async API Contract
asyncapi: 2.6.0
channels:
  order/created:                              # ← TIER 5: Channel/topic
    subscribe:
      message:
        contentType: application/json         # ← TIER 1: Format
        payload:                              # ← TIER 2: Schema
          $ref: '#/components/schemas/OrderEvent'

components:
  schemas:                                    # ← TIER 2
    OrderEvent:
      type: object
      required: [eventId, orderId]           # ← TIER 3
      properties:
        eventId:
          type: string
        orderId:
          type: string
```

### WSDL Example (SOAP)

```xml
<!-- TIER 5: Service Contract -->
<definitions>
  <types>
    <!-- TIER 2: Schema definitions (XSD) -->
    <xsd:schema>
      <xsd:element name="GetCustomerRequest">
        <xsd:complexType>
          <xsd:sequence>
            <xsd:element name="customerId" type="xsd:string"/>
          </xsd:sequence>
        </xsd:complexType>
      </xsd:element>
    </xsd:schema>
  </types>
  
  <!-- TIER 5: Service operations -->
  <portType name="CustomerService">
    <operation name="GetCustomer">
      <input message="GetCustomerRequest"/>
      <output message="GetCustomerResponse"/>
    </operation>
  </portType>
  
  <!-- TIER 5: Protocol binding -->
  <binding name="CustomerBinding" type="CustomerService">
    <soap:binding transport="http://schemas.xmlsoap.org/soap/http"/>
  </binding>
</definitions>
```

---

## UTLX Integration with API Contracts

### Use Case 1: Generate OpenAPI from UTLX

```utlx
%utlx 1.0
input json
schema customer.json type:jsch
output openapi
---
// UTLX can generate OpenAPI from schema + transformations
{
  openapi: "3.0.0",
  info: {
    title: "Customer API",
    version: "1.0.0"
  },
  paths: generatePaths($schema, $input)
}
```

### Use Case 2: Validate Against OpenAPI

```utlx
%utlx 1.0
input json
schema api.yaml type:openapi        # ← TIER 5: API contract validation
output json
---
// Transform while ensuring API contract compliance
{
  customerId: $input.id,
  name: $input.name
}
```

### Use Case 3: Transform Between API Standards

```utlx
%utlx 1.0
input openapi                       # ← Input: OpenAPI contract
output asyncapi                     # ← Output: AsyncAPI contract
---
// Convert REST API to Event-driven API
{
  asyncapi: "2.6.0",
  channels: convertPathsToChannels($input.paths)
}
```

### Use Case 4: WSDL to OpenAPI Migration

```utlx
%utlx 1.0
input wsdl
output openapi
---
{
  openapi: "3.0.0",
  paths: convertOperationsToPaths($input.portType.operations),
  components: {
    schemas: convertXsdToJsonSchema($input.types.schema)
  }
}
```

---

## API Contract Standards Classification

### Complete Classification with Tier 5

| Standard | Code | Tier | Category | Standard Body |
|----------|------|------|----------|---------------|
| **OpenAPI** | `openapi` | 5 | REST API contract | OpenAPI Initiative |
| **AsyncAPI** | `asyncapi` | 5 | Async API contract | AsyncAPI Initiative |
| **WSDL** | `wsdl` | 5 | SOAP service contract | W3C |
| **RAML** | `raml` | 5 | REST API contract | RAML Workgroup |
| **API Blueprint** | `apiblueprint` | 5 | REST API contract | - |
| **GraphQL Schema** | `graphql` | 5 | GraphQL API contract | GraphQL Foundation |
| **gRPC** | `grpc` | 5 | RPC framework | Google/CNCF |
| **Thrift IDL** | `thrift` | 5 | RPC framework | Apache |
| **SOAP** | `soap` | 5 | Protocol | W3C |
| **JSON-RPC** | `jsonrpc` | 5 | RPC protocol | JSON-RPC Working Group |
| **XML-RPC** | `xmlrpc` | 5 | RPC protocol | - |
| **CloudEvents** | `cloudevents` | 1, 5 | Event format | CNCF |

---

## Key Differences: Tier 2 vs Tier 5

### Tier 2 (Schema) - "What is the data?"

**Focus**: Structure and types
**Scope**: Single document/message
**Examples**: JSON Schema, XSD, Avro Schema

```json
{
  "type": "object",
  "properties": {
    "name": {"type": "string"},
    "age": {"type": "integer"}
  }
}
```

### Tier 5 (Contract) - "How do we communicate?"

**Focus**: Operations and protocols
**Scope**: Service/API interface
**Examples**: OpenAPI, AsyncAPI, WSDL

```yaml
paths:
  /users:
    get:
      summary: "Get users"
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/User'
```

---

## Decision Matrix: What Tier?

| If it defines... | Then it's Tier... | Example |
|------------------|-------------------|---------|
| Actual data values | 1 | JSON file with customer data |
| Data structure/types | 2 | JSON Schema defining customer structure |
| Business rules/constraints | 3 | Schematron rule: age > 18 for adults |
| Transformation logic | 4 | UTLX expression: fullName = first + last |
| **API operations/endpoints** | **5** | **OpenAPI path: GET /customers** |
| **Communication protocols** | **5** | **AsyncAPI channel: order.created** |
| **Service interfaces** | **5** | **WSDL operation: GetCustomer** |

---

## Overlap Analysis

### Standards That Span Multiple Tiers

| Standard | Primary Tier | Also Contains |
|----------|--------------|---------------|
| **OpenAPI** | 5 (Contract) | Tier 2 (schemas), Tier 3 (validation) |
| **AsyncAPI** | 5 (Contract) | Tier 2 (schemas), Tier 3 (validation) |
| **WSDL** | 5 (Contract) | Tier 2 (XSD schemas) |
| **GraphQL Schema** | 5 (Contract) | Tier 2 (type definitions) |
| **Protobuf** | 2 (Schema) | Can be used with Tier 5 (gRPC) |
| **Avro** | 2 (Schema) | Tier 1 (binary format) |

### Why This Matters for UTLX

**UTLX needs to understand the difference**:

1. **Schema files** (Tier 2) → Validate structure
2. **API contracts** (Tier 5) → Validate operations + structure

```utlx
# This validates ONLY structure (Tier 2)
schema customer.json type:jsch

# This validates structure + API operations (Tier 5)
schema api.yaml type:openapi
```

---

## Recommendations for UTLX

### 1. Implement Tier 5 as "Contract" or "API" Layer

**Suggested UTLX syntax**:

```utlx
%utlx 1.0
contract api.yaml type:openapi      # ← New keyword for Tier 5
# OR
api api.yaml type:openapi           # ← Alternative keyword
# OR  
schema api.yaml type:openapi        # ← Reuse schema keyword (simpler)
```

### 2. Support Contract-to-Contract Transformations

```utlx
%utlx 1.0
input openapi
output asyncapi
---
// Convert synchronous REST to async events
{
  asyncapi: "2.6.0",
  channels: $input.paths 
    |> map(path => convertToChannel(path))
}
```

### 3. Extract Schemas from Contracts

```utlx
%utlx 1.0
input openapi
output jsch
---
// Extract JSON Schema from OpenAPI
$input.components.schemas.Customer
```

### 4. Validate Data Against API Contracts

```utlx
%utlx 1.0
input json
schema api.yaml type:openapi path:/customers operation:post
output json
---
// Ensure request matches POST /customers definition
$input
```

---

## Conclusion

### The Answer

**API contracts are NOT a separate orthogonal tier** — they are:

1. ✅ **Primarily Tier 5** (Protocol/Interface/Contract layer)
2. ✅ **Composite specifications** that reference Tier 1, 2, and 3
3. ✅ **A new tier to add** to the UTLX architecture

### Updated Tier Model

```
Tier 5: Protocol/Interface (NEW!)
  ↓ references
Tier 4: Expression/Logic
  ↓ uses
Tier 3: Validation
  ↓ validates
Tier 2: Schema
  ↓ describes
Tier 1: Instance/Data
```

### Key Insight

**OpenAPI/AsyncAPI/WSDL are NOT just schemas**. They are **service contracts** that:
- Define **how to interact** with an API (Tier 5)
- Reference **what data looks like** (Tier 2)
- Specify **what rules apply** (Tier 3)

**This is why they need their own tier!**

---

**Document Version**: 1.0  
**Last Updated**: November 7, 2025  
**Author**: API Contract tier analysis for UTL-X project  
**Status**: Architecture clarification
