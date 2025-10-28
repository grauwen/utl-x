# XSD Design Patterns in UTL-X

## Overview

XML Schema Definition (XSD) has four well-known design patterns. Each pattern offers different trade-offs for reusability, maintainability, and complexity.

## The Four XSD Design Patterns

### 1. Russian Doll
**Structure**: Single global element with all types defined inline (anonymous)

**Characteristics**:
- ✅ Simple, self-contained schemas
- ✅ Easy to understand for small schemas
- ❌ No type reuse
- ❌ Difficult to maintain for large schemas

**Example**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="order">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="orderId" type="xs:string"/>
        <xs:element name="customer">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="name" type="xs:string"/>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

### 2. Venetian Blind
**Structure**: Global types, local elements

**Characteristics**:
- ✅ Excellent type reusability
- ✅ Most common pattern in industry
- ✅ Clean separation of types
- ✅ Good for large schemas
- ❌ More verbose than Russian Doll

**Example**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="http://example.com/schema">

  <xs:complexType name="PersonType">
    <xs:sequence>
      <xs:element name="firstName" type="xs:string"/>
      <xs:element name="lastName" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>

  <xs:element name="person" type="PersonType"/>
</xs:schema>
```

### 3. Salami Slice
**Structure**: All elements global, minimal types

**Characteristics**:
- ✅ Maximum element reusability
- ✅ Flexible composition
- ❌ Can become unwieldy
- ❌ Less common

**Example**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="firstName" type="xs:string"/>
  <xs:element name="lastName" type="xs:string"/>

  <xs:element name="person">
    <xs:complexType>
      <xs:sequence>
        <xs:element ref="firstName"/>
        <xs:element ref="lastName"/>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

### 4. Garden of Eden
**Structure**: All elements and types global

**Characteristics**:
- ✅ Maximum reusability (both elements and types)
- ✅ Best for schema composition
- ❌ Most verbose
- ❌ Can be complex

**Example**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="firstName" type="xs:string"/>
  <xs:element name="lastName" type="xs:string"/>

  <xs:complexType name="PersonType">
    <xs:sequence>
      <xs:element ref="firstName"/>
      <xs:element ref="lastName"/>
    </xs:sequence>
  </xs:complexType>

  <xs:element name="person" type="PersonType"/>
</xs:schema>
```

## Pattern Preservation in UTL-X

### USDL Limitation: Named Types Only

**Critical**: USDL (Universal Schema Definition Language) requires all types to be **named**. It does not support inline/anonymous types.

This means:
- ✅ **Venetian Blind** preserves perfectly (global named types)
- ✅ **Garden of Eden** preserves perfectly (all global)
- ⚠️ **Salami Slice** partially preserves (elements, but minimal types)
- ❌ **Russian Doll** CANNOT preserve (inline anonymous types)

### Pattern Transformation Through USDL

| Input Pattern | → USDL → | Output Pattern | Preservation |
|---------------|----------|----------------|--------------|
| Russian Doll | Empty `%types` | Empty schema | ❌ Lost |
| Venetian Blind | Named types in `%types` | Venetian Blind | ✅ Preserved |
| Salami Slice | Types from global elements | Venetian Blind | ⚠️ Partial |
| Garden of Eden | All global types | Venetian Blind | ⚠️ Partial |

**Key Finding**: USDL → XSD **always** produces **Venetian Blind** pattern (global types, local elements).

### Why Russian Doll Cannot Round-Trip

**Russian Doll Input**:
```xml
<xs:element name="order">
  <xs:complexType>  <!-- ANONYMOUS TYPE -->
    <xs:sequence>
      <xs:element name="orderId" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

**Parsed to USDL**:
```json
{
  "%types": {}  // Empty! No named types to extract
}
```

**Back to XSD**:
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"/>
<!-- Empty schema -->
```

### Pattern Conversion (Explicit)

While USDL doesn't preserve Russian Doll, you can **explicitly convert** patterns using the XSDSerializer's pattern parameter:

```kotlin
// Convert any USDL to specific pattern
val serializer = XSDSerializer(
    pattern = XSDSerializer.XSDPattern.VENETIAN_BLIND,
    version = "1.0"
)
```

**Available Patterns**:
- `RUSSIAN_DOLL` - Validates 1 global element, 0 global types
- `VENETIAN_BLIND` - Validates global types exist
- `SALAMI_SLICE` - Validates multiple global elements
- `GARDEN_OF_EDEN` - All elements and types global

**Note**: Pattern parameter is for **validation only**, not transformation. USDL → XSD always produces Venetian Blind structure.

## Recommendations

### For Round-Trip Fidelity
1. **Use Venetian Blind** as your source pattern
2. Avoid Russian Doll if you need to round-trip through USDL
3. Salami Slice and Garden of Eden will convert to Venetian Blind

### For New Schemas
1. **Default to Venetian Blind** - industry standard, good reusability
2. Use Russian Doll only for small, self-contained schemas that don't need transformation
3. Garden of Eden for maximum reusability in schema composition projects

### Pattern Migration
To migrate Russian Doll → Venetian Blind:
1. Extract inline types and give them names
2. Convert to global complexTypes
3. Reference types by name in elements

**Example**:

**Before (Russian Doll)**:
```xml
<xs:element name="person">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="name" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

**After (Venetian Blind)**:
```xml
<xs:complexType name="PersonType">
  <xs:sequence>
    <xs:element name="name" type="xs:string"/>
  </xs:sequence>
</xs:complexType>

<xs:element name="person" type="PersonType"/>
```

## Testing Pattern Behavior

### Test Pattern Preservation
```bash
cd conformance-suite

# Venetian Blind (preserves)
python3 runners/cli-runner/simple-runner.py formats/xsd/patterns pattern_preservation_venetian_blind

# Russian Doll (does not preserve)
python3 runners/cli-runner/simple-runner.py formats/xsd/patterns pattern_preservation_russian_doll
```

### View Pattern Examples
```bash
# Russian Doll example
cat tests/formats/xsd/patterns/russian_doll.yaml

# Venetian Blind example
cat tests/formats/xsd/patterns/venetian_blind.yaml
```

## Future Enhancements

### Planned Features
1. **Anonymous Type Extraction**: Auto-generate names for inline types during parsing
2. **Pattern Conversion**: Explicit Russian Doll → Venetian Blind transformation
3. **Pattern Detection**: Automatic pattern recognition in `parseXSDSchema()`
4. **Pattern Hints**: USDL metadata to preserve original pattern intent

### Workarounds (Current)
For Russian Doll schemas that need transformation:
1. **Manual conversion**: Refactor to Venetian Blind before using UTL-X
2. **Low-level XSD**: Use `input xsd` / `output xsd` for direct manipulation
3. **External tools**: Use XSD refactoring tools to convert patterns first

## See Also

- [XSD Parser Implementation](/formats/xsd/src/main/kotlin/org/apache/utlx/formats/xsd/XSDParser.kt)
- [XSD Serializer Implementation](/formats/xsd/src/main/kotlin/org/apache/utlx/formats/xsd/XSDSerializer.kt)
- [Universal Schema DSL](/docs/language-guide/universal-schema-dsl.md)
- [XSD Conformance Tests](/conformance-suite/tests/formats/xsd/patterns/)
