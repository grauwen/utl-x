# JSON Schema Support

UTL-X provides comprehensive support for JSON Schema (draft-07, 2019-09, 2020-12), enabling bidirectional transformations between USDL (Universal Schema Definition Language) and JSON Schema format.

## Overview

JSON Schema is a vocabulary that allows you to annotate and validate JSON documents. UTL-X's JSON Schema module allows you to:

- **Parse** existing JSON Schemas into UTL-X's Universal Data Model (UDM)
- **Serialize** USDL schemas to JSON Schema format (2020-12)
- **Transform** schemas between JSON Schema and other schema formats (XSD, Avro)
- **Validate** JSON Schema structure against specification

## Supported Versions

- **draft-07** - Most widely supported version
- **2019-09** - Adds `if/then/else`, `$vocabulary`
- **2020-12** - Latest stable, adds `prefixItems`, `$dynamicRef`

## Quick Start

### Parsing a JSON Schema

```utlx
%utlx 1.0
input jsch
output json
---
{
  schemaTitle: $input.title,
  schemaType: $input.type,
  propertyCount: count(keys($input.properties))
}
```

### Generating a JSON Schema from USDL

```utlx
%utlx 1.0
input json
output jsch
---
{
  "%types": {
    "User": {
      "%kind": "structure",
      "%documentation": "User profile schema",
      "%fields": [
        {
          "%name": "username",
          "%type": "string",
          "%required": true,
          "%description": "Unique username for the user"
        },
        {
          "%name": "email",
          "%type": "string",
          "%required": true,
          "%description": "Email address"
        },
        {
          "%name": "age",
          "%type": "integer",
          "%required": false,
          "%description": "User's age in years"
        }
      ]
    }
  }
}
```

**Output:**

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$defs": {
    "User": {
      "type": "object",
      "description": "User profile schema",
      "properties": {
        "username": {
          "type": "string",
          "description": "Unique username for the user"
        },
        "email": {
          "type": "string",
          "description": "Email address"
        },
        "age": {
          "type": "integer",
          "description": "User's age in years"
        }
      },
      "required": ["username", "email"]
    }
  }
}
```

## USDL ↔ JSON Schema Mapping

### Type Mappings

| USDL Type | JSON Schema Type |
|-----------|------------------|
| `string` | `"string"` |
| `number` | `"number"` |
| `integer` | `"integer"` |
| `boolean` | `"boolean"` |
| `array` | `"array"` |
| `object` | `"object"` |
| `null` | `"null"` |

### Structure Mapping

**USDL Structure:**
```json
{
  "%types": {
    "Person": {
      "%kind": "structure",
      "%documentation": "A person record",
      "%fields": [
        {
          "%name": "name",
          "%type": "string",
          "%required": true
        }
      ]
    }
  }
}
```

**JSON Schema Equivalent:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$defs": {
    "Person": {
      "type": "object",
      "description": "A person record",
      "properties": {
        "name": {
          "type": "string"
        }
      },
      "required": ["name"]
    }
  }
}
```

### Enumeration Mapping

**USDL Enumeration:**
```json
{
  "%types": {
    "Status": {
      "%kind": "enumeration",
      "%documentation": "Order status values",
      "%values": ["pending", "shipped", "delivered", "cancelled"]
    }
  }
}
```

**JSON Schema Equivalent:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$defs": {
    "Status": {
      "type": "string",
      "description": "Order status values",
      "enum": ["pending", "shipped", "delivered", "cancelled"]
    }
  }
}
```

## Schema Serialization Functions

UTL-X provides built-in functions for round-trip schema transformations:

### `parseJSONSchema(jsonSchemaString)`

Converts a JSON Schema string to USDL format.

```utlx
let jsonSchema = '{"type": "object", "properties": {...}}'
let usdlSchema = parseJSONSchema(jsonSchema)
# usdlSchema now has %types, %title, %documentation, etc.
```

### `renderJSONSchema(usdlSchema, prettyPrint?)`

Converts a USDL schema object to JSON Schema 2020-12 string.

```utlx
let usdlSchema = {
  "%types": {
    "User": {
      "%kind": "structure",
      "%fields": [...]
    }
  }
}
let jsonSchema = renderJSONSchema(usdlSchema)
# jsonSchema is now JSON Schema 2020-12 format
```

**Parameters:**
- `usdlSchema`: USDL schema object with `%types` directive
- `prettyPrint`: Optional boolean for formatted output (default: true)

These functions enable programmatic schema transformations within UTL-X scripts, complementing the I/O boundary format declarations (`input jsch`, `output jsch`).

## Round-Trip Transformations

JSON Schema schemas can be round-tripped through USDL:

```utlx
%utlx 1.0
input json
output json
---
let jsonSchema = renderJSONSchema($input)
let backToUsdl = parseJSONSchema(jsonSchema)
{
  hasTypes: hasKey(backToUsdl, "%types"),
  typeName: keys(backToUsdl["%types"])[0]
}
```

## Features

### Definitions and $defs

Both `definitions` (draft-07) and `$defs` (2020-12) are supported for parsing:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$defs": {
    "Address": {
      "type": "object",
      "properties": {
        "street": {"type": "string"},
        "city": {"type": "string"}
      }
    }
  }
}
```

When serializing, UTL-X always uses `$defs` (2020-12 format).

### Required Fields

JSON Schema's `required` array is automatically distributed to individual fields when parsing:

**JSON Schema:**
```json
{
  "type": "object",
  "properties": {
    "name": {"type": "string"},
    "age": {"type": "integer"}
  },
  "required": ["name"]
}
```

**USDL:**
```json
{
  "%types": {
    "Person": {
      "%kind": "structure",
      "%fields": [
        {
          "%name": "name",
          "%type": "string",
          "%required": true
        },
        {
          "%name": "age",
          "%type": "integer",
          "%required": false
        }
      ]
    }
  }
}
```

### Root Schema Handling

If a JSON Schema has a root `type: "object"` with properties but no `title`, UTL-X synthesizes a type name "Root":

```json
{
  "type": "object",
  "properties": {
    "name": {"type": "string"}
  }
}
```

Converts to:

```json
{
  "%types": {
    "Root": {
      "%kind": "structure",
      "%fields": [{"%name": "name", "%type": "string"}]
    }
  }
}
```

If a `title` is present, it's used as the type name.

### Schema Metadata

- `title` → `%title` (top-level)
- `description` → `%documentation` (type-level) or `%description` (field-level)
- `$schema` → Added automatically when serializing (2020-12 URI)

## Conformance Tests

All JSON Schema features are covered by comprehensive conformance tests:

```bash
# Run all JSON Schema tests
python3 runners/cli-runner/simple-runner.py formats/jsch

# Run specific test
python3 runners/cli-runner/simple-runner.py formats/jsch/basic round_trip_basic
```

Test coverage:
- ✅ Round-trip transformations (USDL → JSON Schema → USDL)
- ✅ Multiple draft version parsing (draft-07, 2019-09, 2020-12)
- ✅ Definitions and $defs
- ✅ Required field distribution
- ✅ Enumeration support
- ✅ Root schema handling with title synthesis
- ✅ Metadata preservation (title, description)

## Current Limitations

The following features are not yet fully supported:

1. **$ref Resolution**: References to definitions are preserved but not resolved
2. **Complex Validation Keywords**: `pattern`, `format`, `minLength`, etc. are preserved but not validated
3. **Composition Keywords**: `anyOf`, `allOf`, `oneOf` are preserved but not converted to USDL equivalents
4. **Array Item Schemas**: Array `items` with complex schemas are partially supported

These features will be addressed in future releases as USDL expands its schema definition capabilities.

## Examples

### Cross-Format Schema Migration

Migrate a JSON Schema to XSD:

```utlx
%utlx 1.0
input jsch
output xsd
---
# Automatic conversion through USDL
$input
```

### Schema Validation

Extract validation rules from a JSON Schema:

```utlx
%utlx 1.0
input jsch
output json
---
{
  requiredFields: $input.required,
  fieldTypes: $input.properties |> map((field, props) => {
    name: field,
    type: props.type,
    hasValidation: hasKey(props, "pattern") || hasKey(props, "minLength")
  })
}
```

### Programmatic Schema Generation

Generate a JSON Schema from data:

```utlx
%utlx 1.0
input json
output jsch
---
{
  "%types": {
    "GeneratedSchema": {
      "%kind": "structure",
      "%fields": $input |> keys() |> map(key => {
        "%name": key,
        "%type": typeOf($input[key]),
        "%required": true
      })
    }
  }
}
```

## See Also

- [Universal Schema DSL Guide](/docs/language-guide/universal-schema-dsl.md)
- [Avro Schema Support](/docs/formats/avro.md)
- [Schema Analysis Architecture](/docs/analysis/schema_analysis_architecture.md)
