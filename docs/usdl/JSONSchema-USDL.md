# JSONSchemaSerializer USDL Support Implementation 

## Supported USDL Directives

- ✅ `%types` - Type definitions object
- ✅ `%kind` - Type kind ("structure", "enumeration")
- ✅ `%fields` - Field array
- ✅ `%name` - Field/type name
- ✅ `%type` - Field type
- ✅ `%title` - Schema title
- ✅ `%documentation` - Type-level description
- ✅ `%description` - Field-level description
- ✅ `%required` - Boolean indicating required field
- ✅ `%values` - Enumeration values
- ⏳ `%minLength`, `%maxLength` - String constraints
- ⏳ `%pattern` - Regex validation
- ⏳ `%minimum`, `%maximum` - Numeric constraints
- ⏳ Additional JSON Schema-specific directives

---

## Example Usage

### USDL Source
```utlx
%utlx 1.0
input json
output jsch %usdl 1.0
---
{
  %title: "Customer Schema",
  %documentation: "Schema for customer information",

  %types: {
    Customer: {
      %kind: "structure",
      %documentation: "Customer entity",

      %fields: [
        {
          %name: "customerId",
          %type: "string",
          %required: true,
          %description: "Unique customer identifier"
        },
        {
          %name: "email",
          %type: "string",
          %required: false,
          %description: "Customer email address"
        }
      ]
    },

    Status: {
      %kind: "enumeration",
      %documentation: "Customer status",
      %values: ["active", "inactive", "suspended"]
    }
  }
}
```

### Generated JSON Schema
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "Customer Schema",
  "description": "Schema for customer information",
  "$defs": {
    "Customer": {
      "type": "object",
      "description": "Customer entity",
      "properties": {
        "customerId": {
          "type": "string",
          "description": "Unique customer identifier"
        },
        "email": {
          "type": "string",
          "description": "Customer email address"
        }
      },
      "required": ["customerId"]
    },
    "Status": {
      "type": "string",
      "description": "Customer status",
      "enum": ["active", "inactive", "suspended"]
    }
  }
}
```

---

## Technical Details

### Serialization Flow

1. **Mode Detection** - Check for `%types` property to identify USDL mode
2. **USDL Transformation** - Transform USDL directives to JSON Schema UDM:
   - Extract `%title` and `%documentation` metadata
   - Iterate through `%types` object
   - For each type:
     - **Structure**: Create object type with properties and required array
     - **Enumeration**: Create string type with enum values
   - Build `$defs` object with all type definitions
3. **Description Injection** - Convert _description properties to description
4. **Schema Declaration** - Add `$schema` with draft version URI
5. **JSON Serialization** - Use JSONSerializer to output JSON Schema

### JSON Schema Draft Support

The serializer supports multiple JSON Schema drafts:
- **draft-07** - Most widely supported
- **2019-09** - Adds if/then/else, $vocabulary
- **2020-12** - Latest stable (default)

Draft can be configured via constructor:
```kotlin
JSONSchemaSerializer(draft = "2020-12")
```



## Key Decisions

###  $defs vs definitions
**Decision**: Use `$defs` (2020-12 keyword) instead of `definitions` (draft-07)

**Rationale**:
- `$defs` is the modern keyword in 2020-12 draft
- `definitions` still works but is deprecated
- Forward-compatible with future JSON Schema versions
- More semantic clarity ($defs = definitions)

### Enumeration Support
**Decision**: Implement `%kind: "enumeration"` in initial release

**Rationale**:
- Enumerations are common in JSON Schema
- Simple to implement (string type + enum keyword)
- Demonstrates USDL's multi-kind support
- Differentiates from structure-only XSD implementation

### Required Array Management
**Decision**: Only include required array if there are required fields

**Rationale**:
- Cleaner JSON Schema output
- Follows JSON Schema best practices
- Avoids empty required arrays
- More readable generated schemas

### Type Mapping Strategy
**Decision**: Direct 1:1 mapping for primitive types, default to string for unknown

**Rationale**:
- USDL types align with JSON Schema types
- Safe fallback (string) for custom types
- Simple and predictable behavior
- Can be extended later for custom type mappings

---


## Known Limitations

1. **Type Coverage**: Structures and enumerations only
   - No union types (oneOf/anyOf)
   - No composition (allOf)
   - No type references ($ref)

2. **Constraint Support**: Limited validation directives
   - No minLength/maxLength
   - No pattern validation
   - No numeric range constraints
   - No array constraints (minItems, maxItems)

3. **Advanced JSON Schema Features**: Not yet supported
   - No conditional schemas (if/then/else)
   - No schema composition (oneOf, anyOf, allOf)
   - No $ref for type reuse
   - No additionalProperties control

4. **Draft Compatibility**: Generates 2020-12 by default
   - $defs not available in draft-07
   - May need backward compatibility option

---

## Next Steps

### Immediate
1. Add $ref support for type references
2. Implement string/number constraints (%minLength, %maxLength, %pattern)
3. Add array item schema support

### Short-term
4. Implement composition types (anyOf, oneOf, allOf)
5. Add conditional schemas support
6. Create schema-to-schema transformation tests (JSON Schema ↔ XSD)

### Long-term
7. Implement Tier 3 JSON Schema-specific directives
8. Add format validators (email, uri, date-time, etc.)
9. Support for $id and $ref resolution
10. Add discriminator support for polymorphism

---
