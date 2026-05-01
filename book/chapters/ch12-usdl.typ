= USDL: Universal Schema Definition Language

If UDM is the universal representation for _data_, USDL is the universal representation for _schemas_. Just as UDM lets you write format-agnostic transformations, USDL lets you write format-agnostic schema definitions — one schema that can be read from any schema format, edited, and written to any other schema format.

== What USDL Is

USDL is NOT a separate language or file format. It is a *convention* — a set of property names starting with `%` that represent schema information in a format-agnostic way:

```utlx
{
  "%namespace": "com.example.orders",
  "%types": {
    "Order": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "string", "%required": true },
        "total": { "%type": "number", "%minimum": 0 }
      }
    }
  }
}
```

These `%` properties can live alongside any other data in a UDM tree. The `%` prefix is reserved for USDL — it never collides with raw format properties (XML elements don't start with `%`, JSON keys don't start with `%`, etc.).

== How USDL Works — Automatically

USDL works through three automatic mechanisms. No special tags or declarations are needed.

=== 1. Enrichment (Input — Tier 2 Schema Formats)

When UTL-X reads a schema format (XSD, JSON Schema, Avro, Protobuf, EDMX, Table Schema), the parser automatically *enriches* the UDM with USDL `%` properties alongside the raw structure:

```utlx
%utlx 1.0
input xsd
output json
---
// Both views available on the same $input:
{
  rawElementName: $input["xs:element"].@name,         // raw XSD access
  types: $input["%types"],                             // USDL access
  pattern: $input.^xsdPattern                          // metadata access
}
```

The raw schema structure is preserved AND the USDL abstraction is layered on top. No information loss.

=== 2. Detection (Output — Tier 2 Schema Serializers)

All six Tier 2 serializers automatically detect `%types` in the UDM and generate the native schema format:

```utlx
%utlx 1.0
input json
output xsd
---
// Write USDL in the body — the XSD serializer detects %types and generates XSD
{
  "%namespace": "com.example",
  "%types": {
    "Customer": {
      "%kind": "object",
      "%fields": {
        "name": { "%type": "string", "%required": true },
        "email": { "%type": "string", "%format": "email" }
      }
    }
  }
}
```

The serializer doesn't care HOW `%types` got there — enrichment, data, or literal. It just detects and generates.

=== 3. Carrier (YAML/JSON as Human-Readable Schema)

YAML and JSON naturally carry USDL properties as regular data. This enables a powerful round-trip:

```
1. Read schema:    input xsd → $input has raw + USDL %types
2. Dump to YAML:   output yaml → human-readable YAML with %types
3. Edit:           developer modifies the YAML in a text editor
4. Read back:      input yaml → %types flows through as data
5. Generate:       output xsd → serializer sees %types → new XSD
```

YAML is the human-readable editing layer for schemas. Read any schema as YAML, edit in any text editor, write back as any schema format.

#block(
  fill: rgb("#FFF3E0"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Current status (F08):* the USDL enrichment on input (step 1 above) is not yet implemented — Tier 2 parsers do not yet add `%` properties alongside the raw structure. The output side (step 2) is fully implemented — all 6 serializers detect `%types`. See F08 for the implementation plan.
]

== The USDL Directive Classification

USDL organizes its directives in three levels of portability — from universal concepts that every schema format shares, down to features unique to a single standard:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Level*], [*Scope*], [*What it covers*], [*Applies to*],
  [Core], [Portable], [Types, names, descriptions — the fundamentals], [All formats],
  [Shared], [Widespread], [Constraints, validation — supported by 80%+], [Most formats],
  [Format-native], [Exclusive], [Features unique to one format family], [One format],
  [Reserved], [Future], [Future USDL versions], [Planned],
)

This classification is the key to understanding USDL. Core and Shared directives work everywhere. Format-native directives target a specific output format. When converting USDL to a format that doesn't support a Format-native directive, it's preserved as a comment or metadata.

== Core Directives (Universal)

These directives are supported by every schema format:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it defines*], [*Example*],
  [`%namespace`], [Schema namespace/package], [`%namespace: "com.example.orders"`],
  [`%version`], [Schema version], [`%version: "1.0.0"`],
  [`%types`], [Type definitions block], [`%types: { Customer: {...} }`],
  [`%kind`], [Type kind (object, enum, union)], [`%kind: "object"`],
  [`%name`], [Type or field name], [`%name: "Customer"`],
  [`%type`], [Field data type], [`%type: "string"`],
  [`%description`], [Human-readable description], [`%description: "Customer ID"`],
  [`%fields`], [Field definitions block], [`%fields: { id: {...} }`],
  [`%values`], [Enum values], [`%values: ["ACTIVE", "INACTIVE"]`],
)

=== Example: Customer schema using only Core directives

This works in ALL output formats because every format understands types, names, and descriptions:

```utlx
%utlx 1.0
input auto
output json
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

== Shared Directives (Most Formats) (80%+ Format Support)

These express constraints and validation rules supported by most formats:

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Directive*], [*What it constrains*], [*Supported by*], [*Example value*],
  [`%required`], [Field is mandatory], [All formats], [true],
  [`%nullable`], [Field can be null], [JSON Schema, Avro, Proto], [true],
  [`%array`], [Field is an array], [All formats], [true],
  [`%default`], [Default value], [JSON Schema, Avro, XSD], ["EUR"],
  [`%minLength`], [Min string length], [JSON Schema, XSD], [1],
  [`%maxLength`], [Max string length], [JSON Schema, XSD], [255],
  [`%pattern`], [Regex pattern], [JSON Schema, XSD], [see code examples],
  [`%minimum`], [Min number value], [JSON Schema, XSD], [0],
  [`%maximum`], [Max number value], [JSON Schema, XSD], [999999],
  [`%enum`], [Allowed values], [All formats], [see code examples],
  [`%format`], [Semantic format hint], [JSON Schema], ["email"],
  [`%example`], [Example value], [JSON Schema, OpenAPI], [see code examples],
)


=== Example: Customer with Core + Shared constraints

```utlx
%utlx 1.0
input auto
output json
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

== Format-Native Directives

These target a specific schema format. They express features that only make sense in one context.

=== Protobuf-Specific (`proto`)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%fieldNumber`], [Protobuf field tag number], [1],
  [`%packed`], [Packed encoding for repeated fields], [true],
  [`%oneof`], [Protobuf oneof group name], ["payment_method"],
  [`%map`], [Protobuf map type], [true],
  [`%reserved`], [Reserved field numbers], [see code examples],
)

```utlx
%utlx 1.0
input auto
output proto
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

=== Avro-Specific (`avro`)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%logicalType`], [Avro logical type], ["date"],
  [`%aliases`], [Alternative field names], [see code example],
  [`%precision`], [Decimal precision (digits)], [10],
  [`%scale`], [Decimal scale (decimal places)], [2],
)

```utlx
%utlx 1.0
input auto
output avro
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

=== XSD-Specific (`xsd`)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%elementFormDefault`], [XSD element form], ["qualified"],
  [`%attributeFormDefault`], [XSD attribute form], ["unqualified"],
  [`%choice`], [XSD choice group (xs:choice)], [true],
  [`%all`], [XSD all group (xs:all)], [true],
  [`%xml`], [XML-specific options], [wrapped: true],
)

```utlx
%utlx 1.0
input auto
output xsd
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

=== Database/SQL-Specific (future — no DDL output format yet)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%primaryKey`], [Primary key field], [true],
  [`%autoIncrement`], [Auto-increment], [true],
  [`%foreignKey`], [Foreign key reference], ["customers.id"],
  [`%index`], [Create index on field], [true],
  [`%unique`], [Unique constraint], [true],
  [`%sqlType`], [Override SQL type], ["VARCHAR(50)"],
)

These directives describe database concerns. UTL-X has no `output sql` format — there is no DDL serializer. These directives are preserved as metadata when converting between schema formats, and could be consumed by external code generators or a future DDL output format. They can be included in any USDL schema for documentation purposes. Example:

```utlx
%utlx 1.0
input auto
output json
---
{
  "%types": {
    "Customer": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "integer", "%primaryKey": true, "%autoIncrement": true },
        "name": { "%type": "string", "%maxLength": 100, "%required": true, "%index": true },
        "email": { "%type": "string", "%maxLength": 255, "%unique": true },
        "country": { "%type": "string", "%sqlType": "CHAR(2)" }
      }
    },
    "Order": {
      "%kind": "object",
      "%fields": {
        "id": { "%type": "integer", "%primaryKey": true, "%autoIncrement": true },
        "customerId": { "%type": "integer", "%foreignKey": "Customer.id", "%index": true },
        "total": { "%type": "decimal", "%sqlType": "DECIMAL(10,2)" }
      }
    }
  }
}
```

=== OData-Specific (`osch`)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%entityType`], [OData entity type marker], [true],
  [`%navigation`], [Navigation property], [true],
  [`%target`], [Navigation target entity], ["Orders"],
  [`%cardinality`], [Relationship cardinality], ["many"],
)

These generate OData EDMX/CSDL metadata documents. Example:

```utlx
%utlx 1.0
input auto
output osch
---
{
  "%namespace": "com.example.crm",
  "%types": {
    "Customer": {
      "%kind": "object",
      "%entityType": true,
      "%fields": {
        "id": { "%type": "integer", "%required": true },
        "name": { "%type": "string" },
        "orders": { "%type": "Order", "%navigation": true, "%target": "Orders", "%cardinality": "many" }
      }
    }
  }
}
```

=== JSON Schema-Specific (`jsch`)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%additionalProperties`], [Allow extra properties], [false],
  [`%prefixItems`], [Tuple validation (array positional types)], [see code examples],
  [`%contentEncoding`], [Binary content encoding], ["base64"],
  [`%contentMediaType`], [Content MIME type], ["application/json"],
  [`%discriminator`], [Polymorphism discriminator field], ["type"],
  [`%if` / `%then` / `%else`], [Conditional schema application], [see code examples],
  [`%dependentSchemas`], [Schema dependencies between fields], [see code examples],
  [`%dependentRequired`], [Required field dependencies], [see code examples],
)

These are JSON Schema 2020-12 keywords that have no equivalent in XSD, Avro, or Protobuf. When converting to other formats, they are preserved as documentation comments.

=== Table Schema-Specific (`tsch` — Frictionless Data)

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Directive*], [*What it sets*], [*Example value*],
  [`%missingValues`], [Values treated as null/missing], [see code examples],
  [`%groupChar`], [Thousands separator for number parsing], [","],
  [`%decimalChar`], [Decimal separator], ["."],
  [`%bareNumber`], [Allow non-numeric characters in number fields], [false],
  [`%trueValues`], [Strings treated as boolean true], [see code examples],
  [`%falseValues`], [Strings treated as boolean false], [see code examples],
)

Table Schema directives describe CSV/tabular data conventions — how numbers are formatted, what values mean "missing," and how booleans are represented in text. These are unique to tabular data and have no equivalent in XML or JSON schema formats.

== How Directive Levels Interact During Conversion

When converting USDL to a specific format, each level is handled differently:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Level*], [*Behavior during conversion*],
  [Core], [Always converted — every format supports these],
  [Shared], [Converted when supported, preserved as comment when not],
  [Format-native], [Converted only for the matching format, ignored/commented for others],
  [Reserved], [Passed through as metadata],
)

Example: converting a schema with `%fieldNumber: 3` and `%pattern: "^[A-Z]+$"`:

- *To Protobuf:* %fieldNumber becomes `= 3` (native), %pattern becomes a comment
- *To JSON Schema:* %fieldNumber ignored, %pattern becomes `"pattern": "^[A-Z]+$"` (native)
- *To XSD:* %fieldNumber ignored, %pattern becomes `xs:pattern` (native)
- *To Avro:* both become metadata properties

This is the power of the classification system: write ONE schema with ALL the information, and each output format takes what it understands.

== Converting Between Formats via USDL

=== XSD to JSON Schema

```utlx
%utlx 1.0
input xsd
output jsch
---
$input
```

=== Avro to Protobuf

```utlx
%utlx 1.0
input avro
output proto
---
$input
```

=== Any Schema to Human-Readable YAML

```utlx
%utlx 1.0
input xsd
output yaml
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
output yaml
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
  [Classification system], [Yes (Core/Shared/Format-native)], [No], [No], [No],
  [Format-specific features], [Format-native], [JSON keywords], [XML facets], [Avro types],
  [Human-readable], [Very (YAML)], [Moderate], [Low (XML)], [Moderate],
  [Part of UTL-X], [Yes (dialect)], [Standalone], [Standalone], [Standalone],
)

USDL is not a replacement for JSON Schema or XSD — those have their own ecosystems. USDL is the _bridge_ between them: write schema information once using classified directives, generate whatever format you need. The Rosetta Stone for schemas — and it lives inside `%utlx 1.0`.
