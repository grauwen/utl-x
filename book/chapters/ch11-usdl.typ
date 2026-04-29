= USDL: Universal Schema Definition Language

If UDM is the universal representation for _data_, USDL is the universal representation for _schemas_. Just as UDM lets you write format-agnostic transformations, USDL lets you write format-agnostic schema definitions — one schema that can be converted to XSD, JSON Schema, Avro, Protobuf, or any other schema format.

This chapter introduces USDL's syntax, type system, and constraints, and shows how it serves as the bridge between all Tier 2 schema formats.

== Why USDL?

Each schema format has its own syntax, its own type system, and its own way of expressing constraints:

- XSD uses XML syntax with `xs:complexType`, `xs:element`, `xs:restriction`
- JSON Schema uses JSON with `type`, `properties`, `required`, `pattern`
- Avro uses JSON with `type: "record"`, `fields`, `logicalType`
- Protobuf uses its own IDL with `message`, `repeated`, `oneof`

These formats express similar concepts — "this field is a required string" — but in incompatible syntax. Converting between them requires understanding each format's semantics and mapping them correctly.

USDL provides one syntax for all of these:

```
%usdl 1.0

type Customer {
  id: string @required
  name: string @required @minLength(1)
  email: string @pattern("^[\\w.-]+@[\\w.-]+$")
  age: integer @min(0) @max(150)
  orders: [Order] @optional
}

type Order {
  orderId: string @required
  total: decimal @min(0)
  currency: string @enum("EUR", "USD", "GBP")
  orderDate: date @required
}
```

This single USDL definition can generate:
- An XSD with `xs:complexType` definitions
- A JSON Schema with `\$ref` definitions
- An Avro schema with `record` types
- A Protobuf `.proto` file with `message` definitions
- A Table Schema for CSV validation
- An OData EDMX with `EntityType` definitions

Write once, generate for any format.

== USDL Syntax

=== Header

Every USDL file starts with a version declaration:

```
%usdl 1.0
```

=== Type Definitions

Types are defined with the `type` keyword:

```
type TypeName {
  fieldName: fieldType @constraint1 @constraint2
}
```

Types can reference other types by name, creating a type graph — just like `\$ref` in JSON Schema or named types in XSD.

=== Field Types

USDL supports these primitive types:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*USDL Type*], [*Description*], [*Maps to*],
  [string], [Text value], [xs:string, JSON string, Avro string],
  [integer], [Whole number], [xs:integer, JSON integer, Avro int/long],
  [decimal], [Decimal number], [xs:decimal, JSON number, Avro bytes (logical)],
  [boolean], [True or false], [xs:boolean, JSON boolean, Avro boolean],
  [date], [Date only], [xs:date, JSON string (format: date)],
  [datetime], [Date + time + timezone], [xs:dateTime, JSON string (format: date-time)],
  [time], [Time only], [xs:time, JSON string (format: time)],
  [binary], [Raw bytes], [xs:base64Binary, JSON string (contentEncoding)],
)

Complex types are defined inline or by reference:

```
type Address {
  street: string
  city: string
  country: string @enum("NL", "DE", "FR", "BE")
}

type Customer {
  name: string
  homeAddress: Address       // reference to another type
  workAddress: Address       // same type, different field
}
```

=== Arrays

Arrays use square bracket notation:

```
type Order {
  items: [LineItem]          // array of LineItem objects
  tags: [string]             // array of strings
}
```

=== Enumerations

```
type PaymentMethod {
  @enum("CREDIT_CARD", "BANK_TRANSFER", "INVOICE", "CASH")
}
```

Or as a field constraint:

```
type Order {
  status: string @enum("NEW", "PROCESSING", "SHIPPED", "DELIVERED")
}
```

== USDL Constraints

Constraints are annotations on fields that express validation rules:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Constraint*], [*Applies to*], [*Example*],
  [\@required], [Any field], [\@required],
  [\@optional], [Any field], [\@optional (default)],
  [\@minLength(n)], [string], [\@minLength(1)],
  [\@maxLength(n)], [string], [\@maxLength(255)],
  [\@pattern(regex)], [string], [\@pattern("[A-Z]\{2\}")],
  [\@min(n)], [integer, decimal], [\@min(0)],
  [\@max(n)], [integer, decimal], [\@max(999999)],
  [\@enum(values)], [string, integer], [\@enum("A", "B", "C")],
  [\@default(value)], [Any field], [\@default("EUR")],
  [\@deprecated], [Any field], [\@deprecated],
  [\@description(text)], [Any field], [\@description("Customer ID")],
  [\@example(value)], [Any field], [\@example("C-12345")],
)

Constraints map to the equivalent concept in each target schema format:

- `\@required` → XSD: `minOccurs="1"`, JSON Schema: `required` array, Avro: no default, Protobuf: comment
- `\@pattern(...)` → XSD: `xs:pattern`, JSON Schema: `pattern`, Avro: metadata
- `\@min(0)` → XSD: `xs:minInclusive`, JSON Schema: `minimum`, Avro: metadata

Not all constraints map perfectly to all formats. USDL preserves what it can and documents gaps in the generated output as comments.

== USDL ↔ Other Schema Formats

=== USDL → XSD

```
type Customer {
  id: string @required
  name: string @required @minLength(1)
}
```

Generates:

```xml
<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="id" type="xs:string" minOccurs="1"/>
    <xs:element name="name" minOccurs="1">
      <xs:simpleType>
        <xs:restriction base="xs:string">
          <xs:minLength value="1"/>
        </xs:restriction>
      </xs:simpleType>
    </xs:element>
  </xs:sequence>
</xs:complexType>
```

=== USDL → JSON Schema

The same USDL generates:

```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string", "minLength": 1 }
  },
  "required": ["id", "name"]
}
```

=== USDL → Avro

```json
{
  "type": "record",
  "name": "Customer",
  "fields": [
    { "name": "id", "type": "string" },
    { "name": "name", "type": "string" }
  ]
}
```

=== USDL → Protobuf

```proto
message Customer {
  string id = 1;    // required
  string name = 2;  // required, minLength: 1
}
```

Note that Protobuf has no built-in constraint system — `\@minLength` becomes a comment. Validation happens at the application layer, not in the proto definition.

== Reverse: Schema Formats → USDL

USDL also works in reverse. Given an existing XSD, JSON Schema, or Avro schema, UTL-X can parse it into USDL:

```bash
cat customer.xsd | utlx --from xsd --to usdl
```

This is valuable for:
- Understanding an unfamiliar schema in a clean, readable format
- Comparing schemas from different systems (convert both to USDL, diff)
- Migrating schemas between formats (XSD → USDL → JSON Schema)

== USDL in Transformations

USDL schemas can be referenced in transformation headers for validation:

```utlx
%utlx 1.0
input json {schema: "customer.usdl"}
output xml {schema: "invoice.usdl"}
---
// transformation body
```

The validation orchestrator converts the USDL to the appropriate format-specific schema (JSON Schema for JSON input, XSD for XML output) and validates accordingly.

== USDL as Documentation

USDL files are human-readable by design — they serve as documentation without any additional tooling:

```
%usdl 1.0

// European e-invoice line item (Peppol BIS 3.0)
type InvoiceLine {
  id: string @required @description("Line number, starting at 1")
  description: string @required @description("Product or service description")
  quantity: decimal @required @min(0) @description("Number of units")
  unitCode: string @required @enum("EA", "KG", "LTR", "MTR") @description("UN/ECE Rec 20 unit code")
  unitPrice: decimal @required @min(0) @description("Price per unit excluding VAT")
  lineTotal: decimal @required @min(0) @description("quantity * unitPrice")
  vatRate: decimal @required @min(0) @max(100) @description("VAT percentage")
  vatScheme: string @required @enum("S", "Z", "AE", "E") @description("UNCL5305 VAT category code")
}
```

Reading this, you understand the data contract immediately — no need to decode XSD's verbose XML syntax or JSON Schema's nested structure. The field names, types, constraints, and descriptions are all in one place.

== USDL vs Other Schema Languages

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, center, center, center, center),
  [*Feature*], [*USDL*], [*JSON Schema*], [*XSD*], [*Avro*],
  [Syntax], [Custom (clean)], [JSON], [XML], [JSON],
  [Readable], [Very], [Moderate], [Low], [Moderate],
  [Multi-format output], [Yes (all 6)], [JSON only], [XML only], [Avro only],
  [Constraints], [Annotations], [Keywords], [Facets], [Metadata],
  [Nesting/refs], [By name], [\$ref], [type/ref], [By name],
  [Documentation], [Built-in], [description], [xs:annotation], [doc],
  [Validation], [Via conversion], [Native], [Native], [Native],
)

USDL is not a replacement for JSON Schema or XSD — those formats have their own ecosystems and tooling. USDL is the _bridge_ between them: a readable, format-agnostic representation that can generate any of them.

Think of USDL as the Rosetta Stone for schemas. You write it once. You read it easily. You generate whatever format you need.
