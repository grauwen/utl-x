= USDL: Universal Schema Definition Language

If UDM is the universal representation for _data_, USDL is the universal representation for _schemas_. Just as UDM lets you write format-agnostic transformations, USDL lets you write format-agnostic schema definitions — one schema that can be converted to XSD, JSON Schema, Avro, Protobuf, or any other schema format.

*Important:* USDL is NOT a separate language or standalone file format. It is a _dialect_ within UTL-X — a modifier on the output (or input) declaration. A USDL transformation is always a `%utlx 1.0` file:

```utlx
%utlx 1.0
input xsd
output json %usdl 1.0
---
$input
```

The `%usdl 1.0` tag tells UTL-X: "serialize the output using USDL conventions." The transformation body uses standard UTL-X expressions. You can also use USDL on the input side — to read a USDL-formatted schema and convert it to another format.

== The USDL Tier System

USDL organizes its directives in four tiers — from universal concepts that every schema format shares, down to features unique to a single standard:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Tier*], [*Name*], [*What it covers*], [*Applies to*],
  [Tier 1], [Core], [Types, names, descriptions — the fundamentals], [All formats],
  [Tier 2], [Common], [Constraints, validation — supported by 80%+], [Most formats],
  [Tier 3], [Format-Specific], [Features unique to one format family], [One format],
  [Tier 4], [Reserved], [Future USDL versions], [Planned],
)

This tiering is the key to understanding USDL. Tier 1 and 2 directives work everywhere. Tier 3 directives target a specific output format. When converting USDL to a format that doesn't support a Tier 3 directive, it's preserved as a comment or metadata.

== Tier 1: Core Directives (Universal)

These directives are supported by every schema format:

- *%namespace* — Schema namespace or package: "com.example.orders"
- *%version* — Schema version: "1.0.0"
- *%types* — Block containing type definitions
- *%kind* — Type kind: "object", "enum", "union"
- *%name* — Type or field name
- *%type* — Field data type: "string", "integer", "decimal", "boolean", "date", "datetime"
- *%description* — Human-readable description
- *%fields* — Block containing field definitions
- *%values* — Enum values list

=== Example: Customer schema using only Tier 1

This works in ALL output formats because every format understands types, names, and descriptions:

```utlx
%utlx 1.0
input auto
output json %usdl 1.0
---
{
  "%namespace": "com.example.crm",
  "%version": "1.0.0",
  "%types": {
    "Customer": {
      "%kind": "object",
      "%description": "A customer record",
      "%fields": {
        "id": { "%type": "string", "%description": "Unique identifier" },
        "name": { "%type": "string", "%description": "Full name" },
        "email": { "%type": "string", "%description": "Email address" },
        "active": { "%type": "boolean" }
      }
    }
  }
}
```

== Tier 2: Common Directives (80%+ Format Support)

These express constraints and validation rules supported by most formats:

- *%required* — Field is mandatory (all formats)
- *%nullable* — Field can be null (JSON Schema, Avro, Proto)
- *%array* — Field is an array (all formats)
- *%default* — Default value (JSON Schema, Avro, XSD)
- *%minLength / %maxLength* — String length limits (JSON Schema, XSD)
- *%pattern* — Regex pattern (JSON Schema, XSD)
- *%minimum / %maximum* — Number range (JSON Schema, XSD)
- *%enum* — Allowed values (all formats)
- *%format* — Semantic format hint like "email" (JSON Schema)
- *%example* — Example value (JSON Schema, OpenAPI)

=== Example: Customer with Tier 1 + Tier 2 constraints

```utlx
%utlx 1.0
input auto
output json %usdl 1.0
---
{
  "%namespace": "com.example.crm",
  "%version": "1.0.0",
  "%types": {
    "Customer": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true, "%pattern": "^C-[0-9]{5}$" },
        "name": { "%type": "string", "%required": true, "%minLength": 1, "%maxLength": 100 },
        "email": { "%type": "string", "%format": "email" },
        "age": { "%type": "integer", "%minimum": 0, "%maximum": 150 },
        "status": { "%type": "string", "%enum": ["ACTIVE", "INACTIVE"], "%default": "ACTIVE" },
        "orders": { "%type": "Order", "%array": true, "%required": false }
      }
    },
    "Order": {
      "%kind": "object",
      "%fields": {
        "orderId": { "%type": "string", "%required": true },
        "total": { "%type": "decimal", "%minimum": 0 },
        "currency": { "%type": "string", "%enum": ["EUR", "USD", "GBP"] }
      }
    }
  }
}
```

Formats that don't support a constraint (e.g., Protobuf has no %pattern) preserve it as a documentation comment.

== Tier 3: Format-Specific Directives

These target a specific schema format. They express features that only make sense in one context.

=== Protobuf-Specific

Directives: %fieldNumber, %packed, %oneof, %map, %reserved

```utlx
%utlx 1.0
input auto
output proto %usdl 1.0
---
{
  "%namespace": "com.example.orders",
  "%types": {
    "Order": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true, "%fieldNumber": 1 },
        "customer": { "%type": "string", "%fieldNumber": 2 },
        "total": { "%type": "double", "%fieldNumber": 3 },
        "items": { "%type": "LineItem", "%array": true, "%fieldNumber": 4 }
      }
    }
  }
}
```

Generates:

```proto
syntax = "proto3";
package com.example.orders;

message Order {
  string id = 1;
  string customer = 2;
  double total = 3;
  repeated LineItem items = 4;
}
```

The %fieldNumber directive is Protobuf-specific — it has no meaning in JSON Schema or XSD.

=== Avro-Specific

Directives: %logicalType, %aliases, %precision, %scale

```utlx
%utlx 1.0
input auto
output avro %usdl 1.0
---
{
  "%namespace": "com.example.finance",
  "%types": {
    "Transaction": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true },
        "amount": { "%type": "decimal", "%precision": 10, "%scale": 2 },
        "date": { "%type": "date", "%logicalType": "date" },
        "oldId": { "%type": "string", "%aliases": ["legacy_id"] }
      }
    }
  }
}
```

Generates Avro schema with logical types, decimal precision, and field aliases.

=== XSD-Specific

Directives: %elementFormDefault, %attributeFormDefault, %choice, %all

```utlx
%utlx 1.0
input auto
output xsd %usdl 1.0
---
{
  "%namespace": "urn:example:invoicing:v1",
  "%elementFormDefault": "qualified",
  "%types": {
    "Invoice": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true },
        "issueDate": { "%type": "date", "%required": true },
        "paymentMethod": {
          "%choice": true,
          "%fields": {
            "creditCard": { "%type": "CreditCardPayment" },
            "bankTransfer": { "%type": "BankTransferPayment" }
          }
        }
      }
    }
  }
}
```

The %choice directive generates an `xs:choice` group — unique to XSD.

=== Database/SQL-Specific

Directives: %primaryKey, %autoIncrement, %foreignKey, %index, %unique, %sqlType

These generate DDL (CREATE TABLE) or inform ORM code generators.

=== OData-Specific

Directives: %entityType, %navigation, %target, %cardinality

These generate OData EDMX/CSDL metadata documents.

== How Tiers Interact During Conversion

When converting USDL to a specific format, each tier is handled differently:

- *Tier 1 (Core):* Always converted — every format supports these
- *Tier 2 (Common):* Converted when the format supports it, preserved as comment when not
- *Tier 3 (Format-Specific):* Converted only for the matching format, ignored or commented for others

Example: converting a schema with `%fieldNumber: 3` and `%pattern: "^[A-Z]+$"`:

- *To Protobuf:* %fieldNumber becomes `= 3` (native), %pattern becomes a comment
- *To JSON Schema:* %fieldNumber ignored, %pattern becomes `"pattern": "^[A-Z]+$"` (native)
- *To XSD:* %fieldNumber ignored, %pattern becomes `xs:pattern` (native)
- *To Avro:* both become metadata properties

This is the power of the tier system: write ONE schema with ALL the information, and each output format takes what it understands.

== Converting Between Formats via USDL

=== XSD to JSON Schema

```utlx
%utlx 1.0
input xsd
output jsch %usdl 1.0
---
$input
```

=== Avro to Protobuf

```utlx
%utlx 1.0
input avro
output proto %usdl 1.0
---
$input
```

=== Any Schema to Human-Readable YAML

```utlx
%utlx 1.0
input xsd
output yaml %usdl 1.0
---
$input
```

Outputs the schema as clean, readable YAML with USDL directives — useful for documentation and understanding unfamiliar schemas.

== USDL for Validation

USDL schemas can be referenced for transformation validation (see F01 for the planned inline syntax):

```utlx
%utlx 1.0
input json {schema: "customer.usdl.json"}
output xml {schema: "invoice.usdl.yaml"}
---
// transformation body — validated against both schemas
```

The validation orchestrator converts the USDL to the format-appropriate validator.

== USDL as Documentation

USDL output in YAML is human-readable by design — it serves as documentation without additional tooling:

```utlx
%utlx 1.0
input xsd
output yaml %usdl 1.0
---
$input
```

Reading the YAML output, you see field names, types, constraints, and descriptions in one place — no need to decode XSD's verbose XML syntax or JSON Schema's nested structure.

== USDL vs Standalone Schema Languages

#table(
  columns: (auto, auto, auto, auto, auto),
  align: (left, center, center, center, center),
  [*Feature*], [*USDL*], [*JSON Schema*], [*XSD*], [*Avro*],
  [Multi-format output], [Yes (all 6)], [JSON only], [XML only], [Avro only],
  [Tier system], [Yes (4 tiers)], [No], [No], [No],
  [Format-specific features], [Tier 3], [JSON keywords], [XML facets], [Avro types],
  [Human-readable], [Very (YAML)], [Moderate], [Low (XML)], [Moderate],
  [Part of UTL-X], [Yes (dialect)], [Standalone], [Standalone], [Standalone],
)

USDL is not a replacement for JSON Schema or XSD — those have their own ecosystems. USDL is the _bridge_ between them: write schema information once using tiered directives, generate whatever format you need. The Rosetta Stone for schemas — and it lives inside `%utlx 1.0`.
