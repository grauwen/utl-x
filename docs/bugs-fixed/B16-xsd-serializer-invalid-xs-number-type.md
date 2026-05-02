# B16: XSD Serializer Produces Invalid `xs:number` Type

**Status:** Open  
**Priority:** High  
**Created:** May 2026  
**Related:** F10 (decimal type mapping)

---

## Problem

When converting JSON Schema `"type": "number"` to XSD, the serializer produces `xs:number` — which is not a valid W3C XML Schema built-in type. Any XSD validator will reject the output.

```
JSON Schema input:   {"type": "number"}
XSD output:          <xs:element name="total" type="xs:number"/>
                                                     ^^^^^^^^^^
                                                     NOT A VALID XSD TYPE
```

## Valid XSD Numeric Types

The W3C XML Schema specification defines these numeric types:

| XSD Type | Range | Precision |
|----------|-------|-----------|
| `xs:decimal` | Arbitrary precision | Exact |
| `xs:double` | IEEE 754 64-bit | ~15-17 significant digits |
| `xs:float` | IEEE 754 32-bit | ~6-9 significant digits |
| `xs:integer` | Arbitrary precision integer | Exact (no fraction) |
| `xs:int` | -2,147,483,648 to 2,147,483,647 | 32-bit |
| `xs:long` | -9,223,372,036,854,775,808 to ... | 64-bit |
| `xs:short` | -32,768 to 32,767 | 16-bit |
| `xs:byte` | -128 to 127 | 8-bit |

There is no `xs:number`. The type does not exist.

## Fix

Map JSON Schema `"type": "number"` to `xs:double` (both are IEEE 754 floating-point):

```kotlin
// In XSDSerializer — type mapping:
"number" -> "xs:double"    // was: "xs:number" (invalid)
"integer" -> "xs:integer"  // unchanged (correct)
```

## File to Modify

| File | Change |
|------|--------|
| `formats/xsd/src/main/kotlin/.../XSDSerializer.kt` | Change `"number" -> "xs:number"` to `"number" -> "xs:double"` |

## Impact

Any XSD output containing `xs:number` is invalid and will fail:
- XSD validation tools (Oxygen, XMLSpy, xmllint)
- WSDL generators that consume the XSD
- Enterprise middleware that loads XSD for schema validation (Tibco BW, SAP CPI)
- UTL-X's own XSD parser when re-reading the output

## Effort

~10 minutes. Single line change + update conformance test expected output.

---

*Bug B16. May 2026.*
*`xs:number` does not exist in W3C XML Schema. Must be `xs:double`.*
