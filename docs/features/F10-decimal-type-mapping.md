# F10: Improve xs:decimal → JSON Schema Type Mapping

**Status:** Open  
**Priority:** Medium  
**Created:** May 2026  
**Related:** F08 (USDL enrichment), Chapter 28 (Schema Formats), Chapter 29 (XSD Patterns)

---

## Problem

When converting XSD to JSON Schema, `xs:decimal` currently maps to `"type": "string"`. This is safe (preserves precision) but loses numeric semantics — downstream consumers cannot perform arithmetic without explicit parsing.

```
XSD:          <xs:element name="total" type="xs:decimal"/>
JSON Schema:  "total": {"type": "string"}       ← current behavior
```

The reverse mapping has a related issue: JSON Schema `"type": "number"` maps to `xs:number` in XSD output, which is not a valid XSD built-in type (should be `xs:decimal` or `xs:double`).

## Why xs:decimal Cannot Safely Map to "number"

### Precision Loss

`xs:decimal` is arbitrary-precision (like Java's `BigDecimal`). JSON Schema `"type": "number"` maps to IEEE 754 double-precision floating point — max ~15-17 significant digits.

```
xs:decimal value:     12345678901234567.89
JSON number value:    12345678901234568       ← last digits corrupted
```

### Trailing Zero Significance

In financial contexts, trailing zeros carry meaning. `100.10` means "one hundred euros and ten cents" — the trailing zero confirms the precision.

```
xs:decimal:    100.10
JSON number:   100.1        ← trailing zero lost
```

### Binary Floating Point Approximation

```
xs:decimal:    0.1
JSON number:   0.1          ← looks fine, but internally stored as 0.1000000000000000055511151231257827021181583404541015625
```

This matters when comparing values: `0.1 + 0.2 == 0.3` is `false` in IEEE 754.

### Real-World Impact

UTL-X is heavily used in enterprise integration where `xs:decimal` appears in:

- **UBL Invoices** (Peppol BIS 3.0): `cbc:PayableAmount`, `cbc:TaxAmount`, `cbc:LineExtensionAmount`
- **ISO 20022** (SWIFT/SEPA): payment amounts, exchange rates
- **HL7 FHIR**: `Quantity.value`, `Money.value`
- **OData**: `Edm.Decimal` properties

Silently introducing floating-point errors in financial amounts during schema conversion would be catastrophic. A Peppol Access Point rejecting invoices because `€299.99` became `€299.9900000000000091` is not theoretical — it's what happens when decimal types are mapped to IEEE 754 floats.

## Current Behavior

### XSD → JSON Schema

| XSD Type | JSON Schema Type | Correct? |
|----------|-----------------|----------|
| `xs:string` | `"type": "string"` | ✅ |
| `xs:integer` | `"type": "integer"` | ✅ |
| `xs:int` | `"type": "integer"` | ✅ |
| `xs:long` | `"type": "integer"` | ✅ |
| `xs:boolean` | `"type": "boolean"` | ✅ |
| `xs:double` | `"type": "number"` | ✅ (both are IEEE 754) |
| `xs:float` | `"type": "number"` | ✅ (both are IEEE 754) |
| `xs:decimal` | `"type": "string"` | ⚠ Safe but loses semantics |
| `xs:date` | `"type": "string"` | ✅ (with `"format": "date"`) |
| `xs:dateTime` | `"type": "string"` | ✅ (with `"format": "date-time"`) |

### JSON Schema → XSD

| JSON Schema Type | XSD Type | Correct? |
|-----------------|----------|----------|
| `"type": "string"` | `xs:string` | ✅ |
| `"type": "integer"` | `xs:integer` | ✅ |
| `"type": "boolean"` | `xs:boolean` | ✅ |
| `"type": "number"` | `xs:number` | ❌ `xs:number` is not a valid XSD type |

## Proposed Change

### XSD → JSON Schema: Use `"format": "decimal"`

```json
// Before (current):
"total": {"type": "string"}

// After (proposed):
"total": {"type": "string", "format": "decimal"}
```

The `"format"` keyword in JSON Schema is a semantic annotation. JSON Schema validators that understand it can enforce decimal syntax; those that don't will treat it as a plain string — which is still safe.

This follows the same pattern already used for dates:
- `xs:date` → `"type": "string", "format": "date"`
- `xs:dateTime` → `"type": "string", "format": "date-time"`
- `xs:decimal` → `"type": "string", "format": "decimal"` (proposed)

### Optional: Add pattern constraint

For stricter validation, also add a `"pattern"` constraint:

```json
"total": {
  "type": "string",
  "format": "decimal",
  "pattern": "^-?\\d+(\\.\\d+)?$"
}
```

### JSON Schema → XSD: Fix xs:number

Map `"type": "number"` to `xs:double` (both are IEEE 754 floating point) instead of the invalid `xs:number`:

```
// Before (current):
"type": "number"  →  xs:number     ← invalid XSD type

// After (proposed):
"type": "number"  →  xs:double     ← correct IEEE 754 mapping
```

If the JSON Schema also has `"format": "decimal"`, map to `xs:decimal` instead:

```
"type": "string", "format": "decimal"  →  xs:decimal    ← round-trip preserved
"type": "number"                       →  xs:double     ← correct fallback
"type": "integer"                      →  xs:integer    ← unchanged
```

## USDL Impact

The USDL `%type` directive should distinguish:

| Source | USDL `%type` | Meaning |
|--------|-------------|---------|
| `xs:decimal` | `"decimal"` | Arbitrary-precision decimal |
| `xs:double` | `"number"` | IEEE 754 double |
| `xs:float` | `"number"` | IEEE 754 float (promoted to double) |
| `xs:integer` | `"integer"` | Arbitrary-precision integer |
| JSON `"type": "number"` | `"number"` | IEEE 754 double |
| JSON `"type": "integer"` | `"integer"` | Arbitrary-precision integer |

This ensures round-trip: `xs:decimal` → USDL `"decimal"` → `xs:decimal` (not `xs:double`).

## Files to Modify

| File | Change |
|------|--------|
| `formats/xsd/src/.../XSDParser.kt` | `toUSDL()`: map `xs:decimal` to `%type: "decimal"` |
| `formats/jsch/src/.../JSONSchemaSerializer.kt` | Map `%type: "decimal"` to `"type": "string", "format": "decimal"` |
| `formats/jsch/src/.../JSONSchemaParser.kt` | `toUSDL()`: detect `"format": "decimal"` → `%type: "decimal"` |
| `formats/xsd/src/.../XSDSerializer.kt` | Map `%type: "decimal"` to `xs:decimal`, `%type: "number"` to `xs:double` |
| All 6 serializers | Ensure `"decimal"` USDL type is handled in each output format |

## Round-Trip Verification

After this change:

```
xs:decimal → USDL %type:"decimal" → JSON Schema "string"+"format":"decimal" → USDL %type:"decimal" → xs:decimal  ✅
xs:double  → USDL %type:"number"  → JSON Schema "number"                   → USDL %type:"number"  → xs:double   ✅
xs:integer → USDL %type:"integer" → JSON Schema "integer"                  → USDL %type:"integer" → xs:integer  ✅
```

## Effort Estimate

| Task | Effort |
|------|--------|
| USDL `"decimal"` type across all parsers/serializers | 1 day |
| JSON Schema `"format": "decimal"` output | 0.5 day |
| JSON Schema `"format": "decimal"` input detection | 0.5 day |
| Fix `xs:number` → `xs:double` in XSD serializer | 0.5 day |
| Conformance tests for decimal round-trip | 0.5 day |
| **Total** | **3 days** |

## Industry Reference

| Tool | xs:decimal mapping | Notes |
|------|-------------------|-------|
| **Microsoft** (Azure Data Factory) | `"type": "number"` | Lossy — known issue in enterprise |
| **Altova XMLSpy** | `"type": "string", "pattern"` | Safe, no format hint |
| **Oxygen XML** | `"type": "number"` | Lossy |
| **UTL-X current** | `"type": "string"` | Safe but no semantic hint |
| **UTL-X proposed** | `"type": "string", "format": "decimal"` | Safe + semantic |

---

*Feature F10. May 2026.*
*Ensures financial precision is preserved in schema conversions while maintaining semantic clarity.*
*Key insight: xs:decimal is not a floating-point type — it must not map to IEEE 754.*
