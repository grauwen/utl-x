# UTL-X and HL7 FHIR: Compatibility Analysis

**Analysis of FHIR XML conventions and what UTL-X needs to support them**  
*Version 1.0 — April 2026*

---

## 1. The FHIR XML Convention

HL7 FHIR (Fast Healthcare Interoperability Resources) uses a **unique XML convention** that differs from traditional XML. Almost every element stores its value in a `value` attribute instead of text content:

```xml
<!-- Traditional XML -->
<birthDate>1955-03-12</birthDate>
<active>true</active>
<family>van der Berg</family>

<!-- FHIR XML -->
<birthDate value="1955-03-12"/>
<active value="true"/>
<family value="van der Berg"/>
```

### Why FHIR uses the `value` attribute

This is a **deliberate design decision** by HL7, not an oversight:

1. **Extensions**: Any FHIR element can be extended with additional metadata. If the value were text content, adding extensions would create mixed content (both text and child elements), which is cumbersome for parsers.

2. **Element IDs**: Even primitives can have `id` attributes for narrative references.

3. **Clean extensibility**: With `value` as an attribute, extensions are simple child elements:
   ```xml
   <birthDate value="1970-03-30">
     <extension url="http://example.org/originalInput">
       <valueString value="March 30, 1970"/>
     </extension>
   </birthDate>
   ```
   If `value` were text content, this would be mixed content — much harder to handle.

4. **Formal rule**: "If an element is present, it SHALL have either a `value` attribute, child elements as defined for its type, or 1 or more extensions."

### FHIR XML → JSON mapping (defined by HL7)

FHIR defines its own XML-to-JSON mapping rules:

| FHIR XML | FHIR JSON | Notes |
|----------|-----------|-------|
| `<id value="12345"/>` | `"id": "12345"` | Primitive → direct value |
| `<active value="true"/>` | `"active": true` | Boolean type-coerced |
| `<birthDate value="1955-03-12"/>` | `"birthDate": "1955-03-12"` | String |
| `<given value="Jan"/>` (repeated) | `"given": ["Jan", "Pieter"]` | Array of values |
| Element with extensions | `"_birthDate": { "extension": [...] }` | `_` prefix for metadata |

This is NOT the same as generic XML-to-JSON. FHIR has its own specification for this conversion.

Sources:
- [FHIR R4 XML Representation](https://www.hl7.org/fhir/R4/xml.html)
- [FHIR R4 JSON Representation](https://hl7.org/fhir/R4/json.html)
- [Attributes Versus Elements in FHIR XML — Firely](https://fire.ly/blog/attributes-versus-elements-in-fhir-xml/)

---

## 2. Current UTL-X Behavior with FHIR XML

### A) Pass-through (`$input`)

```utlx
%utlx 1.0
input xml
output json
---
$input
```

**Input:**
```xml
<Patient xmlns="http://hl7.org/fhir">
  <id value="12345"/>
  <active value="true"/>
  <name>
    <family value="van der Berg"/>
    <given value="Jan"/>
    <given value="Pieter"/>
  </name>
  <birthDate value="1955-03-12"/>
</Patient>
```

**Current UTL-X output (generic XML-to-JSON):**
```json
{
  "Patient": {
    "@xmlns": "http://hl7.org/fhir",
    "id": { "@value": "12345" },
    "active": { "@value": "true" },
    "name": {
      "family": { "@value": "van der Berg" },
      "given": [
        { "@value": "Jan" },
        { "@value": "Pieter" }
      ]
    },
    "birthDate": { "@value": "1955-03-12" }
  }
}
```

**Expected FHIR JSON (per HL7 specification):**
```json
{
  "resourceType": "Patient",
  "id": "12345",
  "active": true,
  "name": [{
    "family": "van der Berg",
    "given": ["Jan", "Pieter"]
  }],
  "birthDate": "1955-03-12"
}
```

### Differences:

| Aspect | UTL-X generic | FHIR expected | Gap |
|--------|--------------|---------------|-----|
| Root element | `"Patient": { ... }` | `"resourceType": "Patient"` | FHIR flattens root, adds `resourceType` |
| Primitive values | `{ "@value": "12345" }` | `"12345"` | FHIR unwraps `value` attribute to direct value |
| Booleans | `"true"` (string) | `true` (boolean) | FHIR type-coerces based on element definition |
| Numbers | `"5.2"` (string) | `5.2` (number) | FHIR type-coerces based on element definition |
| Namespace | `"@xmlns": "http://hl7.org/fhir"` | Absent | FHIR JSON has no namespace |
| Single non-repeating | `"name": { ... }` | `"name": [{ ... }]` | FHIR backbone elements are always arrays |
| Repeated elements | `"given": [{"@value":"Jan"}, ...]` | `"given": ["Jan", "Pieter"]` | FHIR unwraps value from each array element |

### B) Explicit field access

```utlx
%utlx 1.0
input xml
output json
---
{
  id: $input.Patient.id.@value,
  name: $input.Patient.name.family.@value,
  birth: $input.Patient.birthDate.@value
}
```

**Output:**
```json
{
  "id": "12345",
  "name": "van der Berg",
  "birth": "1955-03-12"
}
```

**This works correctly.** When explicitly mapping fields, the `@value` accessor retrieves the attribute value and the output is clean. The UTL-X language is expressive enough to build a correct FHIR JSON output from FHIR XML input through explicit mapping.

---

## 3. Assessment: Is UTL-X Rich Enough?

### A) For explicit mapping: YES

The current UTL-X language can handle FHIR transformations when the user writes explicit mappings:

```utlx
{
  resourceType: "Patient",
  id: $input.Patient.id.@value,
  active: toBoolean($input.Patient.active.@value),
  name: [{
    family: $input.Patient.name.family.@value,
    given: map($input.Patient.name.given, (g) -> g.@value)
  }],
  birthDate: $input.Patient.birthDate.@value
}
```

This is the **recommended approach** for production FHIR integrations. Each field is explicitly mapped, types are converted, and the output matches the FHIR JSON specification exactly.

**What works today:**
- `$input.Patient.id.@value` — access `value` attribute on any element
- `map($input.Patient.name.given, (g) -> g.@value)` — unwrap arrays of `value` attributes
- `toBoolean()`, `toNumber()` — type coercion for FHIR primitives
- `if/else` — conditional logic for FHIR extensions
- Schema validation — JSON Schema can validate FHIR JSON output

### B) For pass-through (`$input`): NO — needs a FHIR-aware mode

A generic `$input` pass-through produces `{"@value": "..."}` objects instead of direct values. This is correct for generic XML-to-JSON (the `@value` attribute IS an attribute), but it's not FHIR-compliant JSON.

To support FHIR pass-through, UTL-X would need a **FHIR-aware XML mode** that knows:
1. Elements with only a `value` attribute should be unwrapped to the attribute value
2. The `value` attribute on FHIR primitives should be type-coerced (string/boolean/number/date)
3. The root element should become `resourceType` instead of a wrapper object
4. `name` elements in FHIR are always arrays (even single occurrences) — requires FHIR schema knowledge
5. Extensions use the `_elementName` convention in JSON
6. Namespace should be stripped (FHIR JSON has no namespace)

This is NOT a simple serializer option — it requires knowledge of the FHIR data model to do correctly.

---

## 4. What Would Need to Change?

### Option 1: FHIR-specific output mode (recommended if FHIR is a target market)

Add a FHIR-aware transformation mode:

```utlx
%utlx 1.0
input xml {format: "fhir"}
output json {format: "fhir"}
---
$input
```

This would:
- Recognize FHIR's `value` attribute convention → unwrap to direct values
- Type-coerce primitives based on FHIR element definitions (boolean, integer, decimal, string)
- Flatten root element → `resourceType` property
- Handle FHIR arrays (backbone elements always array, even single)
- Handle extensions → `_elementName` convention
- Strip namespace from output

**Effort: Significant.** Requires a FHIR element type database or the FHIR StructureDefinition to know which elements are primitives, which are arrays, and which types to coerce to. This is essentially a FHIR-specific serializer.

### Option 2: `value` attribute unwrapping option (simpler, partial solution)

Add a serializer option that unwraps elements with only a `value` attribute:

```utlx
%utlx 1.0
input xml
output json {unwrapValueAttribute: true}
---
$input
```

This would turn `<id value="12345"/>` → `"id": "12345"` without needing FHIR-specific knowledge.

**What it solves:**
- `value` attribute unwrapping (the biggest visual issue)
- Works for any XML that uses the FHIR-like `value` attribute pattern

**What it does NOT solve:**
- Type coercion (boolean `true` vs string `"true"`)
- Root element flattening (`resourceType`)
- Array handling (single elements that should be arrays)
- Extension convention (`_elementName`)
- Namespace stripping

**Effort: Small.** Similar to the `writeAttributes` option — a check in the serializer.

### Option 3: No change — explicit mapping only (current state)

Users write explicit FHIR mappings. This is what every other transformation tool (DataWeave, XSLT, Jsonata) requires for FHIR. There is no generic XML-to-FHIR-JSON converter because the mapping requires FHIR-specific knowledge.

**What works today:**
- All field access via `@value` 
- Type conversion via stdlib functions
- Array construction via `map()`
- Conditional logic for extensions
- Schema validation on output

---

## 5. Recommendation

### Short term: Option 3 (no change)

The current UTL-X language is rich enough for FHIR integration through explicit mapping. This is the same approach used by DataWeave, XSLT, and every other transformation tool in the healthcare space. Nobody does FHIR XML → FHIR JSON through a generic pass-through — it always requires explicit mapping because the FHIR JSON representation has different semantics than generic XML-to-JSON.

**The use case slides show this correctly:** the `hl7v2-to-fhir.utlx` transform explicitly maps each field, which produces correct FHIR JSON.

### Medium term: Option 2 (`unwrapValueAttribute`)

Add a simple serializer option that unwraps `<element value="..."/>` to `"element": "..."`. This improves the pass-through experience for FHIR and other `value`-attribute-heavy XML standards without requiring FHIR-specific knowledge.

Implementation: similar to `writeAttributes` — a boolean option on the serializer, checked during object serialization. When an object has no properties and exactly one attribute named `value`, unwrap to the attribute value.

### Long term: Option 1 (FHIR mode) — only if healthcare is a key market

A full FHIR-aware mode is a significant investment (needs FHIR StructureDefinitions for type knowledge). Only pursue this if healthcare/FHIR becomes a primary market for UTL-X. Even then, most FHIR integrations require explicit mapping because the source data (HL7 v2, proprietary EHR formats) has a completely different structure than FHIR.

---

## 6. FHIR XML Pattern Summary

| Pattern | FHIR XML | Current UTL-X JSON | With explicit mapping |
|---------|----------|-------------------|----------------------|
| Primitive | `<id value="12345"/>` | `{"@value":"12345"}` | `$input.Patient.id.@value` → `"12345"` |
| Boolean | `<active value="true"/>` | `{"@value":"true"}` | `toBoolean($input.Patient.active.@value)` → `true` |
| Number | `<total value="2"/>` | `{"@value":2}` | `$input.Bundle.total.@value` → `2` |
| Nested | `<name><family value="Berg"/></name>` | `{"family":{"@value":"Berg"}}` | `$input.Patient.name.family.@value` → `"Berg"` |
| Repeated | `<given value="Jan"/><given value="Pieter"/>` | `[{"@value":"Jan"},{"@value":"Pieter"}]` | `map(given, (g) -> g.@value)` → `["Jan","Pieter"]` |
| Extension | `<birthDate value="1970-03-30"><extension .../></birthDate>` | Complex object | Explicit mapping needed |
| Coding | `<code><coding><system value="http://loinc.org"/><code value="2093-3"/></coding></code>` | Deeply nested @value | Explicit mapping per level |

---

## 7. Example: Complete FHIR Patient Mapping in UTL-X

```utlx
%utlx 1.0
input xml
output json
---
let patient = $input.Patient
{
  resourceType: "Patient",
  id: patient.id.@value,
  meta: {
    versionId: patient.meta.versionId.@value,
    lastUpdated: patient.meta.lastUpdated.@value,
    profile: [patient.meta.profile.@value]
  },
  identifier: map(patient.identifier, (ident) -> {
    use: ident.use.@value,
    system: ident.system.@value,
    value: ident.value.@value
  }),
  active: toBoolean(patient.active.@value),
  name: map(patient.name, (n) -> {
    use: n.use.@value,
    family: n.family.@value,
    given: map(n.given, (g) -> g.@value),
    prefix: if (n.prefix != null) [n.prefix.@value] else null
  }),
  telecom: map(patient.telecom, (t) -> {
    system: t.system.@value,
    value: t.value.@value,
    use: t.use.@value,
    rank: if (t.rank != null) toNumber(t.rank.@value) else null
  }),
  gender: patient.gender.@value,
  birthDate: patient.birthDate.@value,
  deceasedBoolean: if (patient.deceasedBoolean != null) 
    toBoolean(patient.deceasedBoolean.@value) else null,
  address: map(patient.address, (addr) -> {
    use: addr.use.@value,
    type: addr.type.@value,
    line: map(addr.line, (l) -> l.@value),
    city: addr.city.@value,
    district: addr.district.@value,
    postalCode: addr.postalCode.@value,
    country: addr.country.@value
  }),
  maritalStatus: if (patient.maritalStatus != null) {
    coding: map(patient.maritalStatus.coding, (c) -> {
      system: c.system.@value,
      code: c.code.@value,
      display: c.display.@value
    })
  } else null,
  generalPractitioner: map(patient.generalPractitioner, (gp) -> {
    reference: gp.reference.@value,
    display: gp.display.@value
  })
}
```

This produces **spec-compliant FHIR R4 JSON** from FHIR R4 XML using the current UTL-X language. No engine changes needed.

---

*Analysis document for UTL-X FHIR compatibility. April 2026.*
*Sources: [FHIR R4 XML](https://www.hl7.org/fhir/R4/xml.html), [FHIR R4 JSON](https://hl7.org/fhir/R4/json.html), [Firely: Attributes vs Elements in FHIR](https://fire.ly/blog/attributes-versus-elements-in-fhir-xml/)*
