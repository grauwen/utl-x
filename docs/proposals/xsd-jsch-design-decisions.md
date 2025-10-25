# XSD and JSON Schema (JSCH) Format Support - Design Decisions

**Version:** 1.0
**Status:** Approved
**Created:** 2025-10-25
**Related:** [`xsd-jsch-format-support.md`](./xsd-jsch-format-support.md)

---

## Overview

This document captures the design decisions made for XSD and JSON Schema (JSCH) format support in UTL-X, answering the open questions from the main proposal.

---

## Decision Summary

| Question | Decision | Rationale |
|----------|----------|-----------|
| **XSD Version** | Support XSD 1.1 | Modern features (assertions, type alternatives) |
| **Schema Validation** | No built-in validation | Validation done outside UTL-X (existing tools) |
| **JSON Schema Version** | Default: draft-07, Opt-in: 2020-12 | draft-07 most widely supported, 2020-12 for future |
| **Pattern Detection** | Yes - auto-detect input patterns | Enables pattern conversion workflows |
| **Config Override** | Yes - `.utlxrc` global defaults | Enterprise standardization support |
| **Default Pattern** | Venetian Blind | Industry best practice (balanced reusability) |

---

## Detailed Decisions

### D1: XSD Version Support ✅

**Decision:** Support both XSD 1.0 and XSD 1.1

**Implementation:**
- Parse both XSD 1.0 and XSD 1.1 files
- Default output: XSD 1.1 (modern standard)
- Format option to output XSD 1.0 for legacy compatibility

```utlx
# Default: XSD 1.1
output xsd

# Legacy compatibility
output xsd {version: "1.0"}
```

**XSD 1.1 Features to Support:**

| Feature | Description | Example Use Case |
|---------|-------------|------------------|
| **Assertions** | `xs:assert` constraints | Business rule validation: `<xs:assert test="@price > 0"/>` |
| **Type Alternatives** | Conditional types | Different types based on element value |
| **`xs:all` with unbounded** | Flexible occurrence | Unordered repeating elements |
| **`xs:override`** | Schema refinement | Modify imported schemas without editing |
| **`xs:openContent`** | Extensibility control | Allow specific extensions to closed types |

**Rationale:**
- XSD 1.1 is W3C Recommendation (since 2012)
- Modern schemas use assertions for business rules
- Type alternatives enable context-dependent validation
- Backward compatible (XSD 1.1 parsers handle 1.0 schemas)

**Backward Compatibility:**
- Parser auto-detects version from `<xs:schema vc:minVersion="1.1">`
- Default output is 1.1, but 1.0 available via format option
- XSD 1.0 constructs preserved during round-trip

---

### D2: Schema Validation ❌ (No Built-in Support)

**Decision:** No built-in JSON Schema validation library

**Rationale:**
1. **External Tool Philosophy:** UTL-X focuses on transformation, not validation
2. **Avoid Bloat:** JSON Schema validators add 2-5MB dependencies
3. **Existing Tools:** Users already have validation tools:
   - JSON Schema: `ajv`, `jsonschema` (Python), online validators
   - XSD: `xmllint`, `Xerces`, IDE validators
4. **Workflow Separation:**
   ```bash
   # Transform first
   utlx transform data.json schema.utlx -o validated.json

   # Then validate with dedicated tool
   ajv validate -s schema.json -d validated.json
   ```

**What We DO Provide:**

| Function | Purpose | Example |
|----------|---------|---------|
| `jschGetSchema()` | Extract $schema version | `jschGetSchema($input) => "draft-07"` |
| `jschValidateSchema()` | **Syntax validation only** | Check schema is well-formed JSON |
| `xsdValidateSchema()` | **Syntax validation only** | Check XSD is well-formed XML |

**NOT Provided:**
- ❌ `jschValidateData(data, schema)` - Use external tools
- ❌ Schema compilation/caching
- ❌ Custom format validators
- ❌ Error reporting infrastructure

**Alternative:** Users can call external validators via Bash:

```utlx
%utlx 1.0
input json
output json
---
{
  transformed: transform($input),

  # Note: Validation should be done externally, not in transform
  # This is just to demonstrate the pattern
}
```

Then validate externally:
```bash
utlx transform data.json transform.utlx -o output.json
ajv validate -s schema.json -d output.json
```

---

### D3: JSON Schema Version Strategy ✅

**Decision:** Default to draft-07, support 2020-12 as opt-in

**Default Behavior:**
```utlx
# Defaults to draft-07
output jsch
```

**Opt-in to 2020-12:**
```utlx
output jsch {version: "2020-12"}
```

**Version Detection on Input:**
- Auto-detect from `"$schema"` field:
  - `"http://json-schema.org/draft-07/schema#"` → draft-07
  - `"https://json-schema.org/draft/2020-12/schema"` → 2020-12
  - `"http://json-schema.org/draft-04/schema#"` → draft-04 (best-effort)

**Rationale:**

| Aspect | draft-07 | 2020-12 |
|--------|----------|---------|
| **Adoption** | Very high (90%+ tools) | Growing (50%+ tools) |
| **Tool Support** | Universal | Limited (ajv, newer validators) |
| **Complexity** | Moderate | High (`$dynamicRef`, `prefixItems`) |
| **Migration Path** | Stable baseline | Modern features as needed |

**Supported Versions:**

| Version | Input | Output | Notes |
|---------|-------|--------|-------|
| **draft-04** | ✅ Parse | ❌ No output | Legacy, best-effort conversion to draft-07 |
| **draft-07** | ✅ Parse | ✅ Default | Most widely supported |
| **2020-12** | ✅ Parse | ✅ Opt-in | Modern features (`$dynamicRef`, `unevaluatedProperties`) |

**Version-Specific Features:**

**draft-07:**
- `$ref`, `$id`, `$schema`
- `type`, `properties`, `required`, `additionalProperties`
- `anyOf`, `allOf`, `oneOf`, `not`
- `enum`, `const`
- String formats: `date-time`, `email`, `uri`, etc.

**2020-12 Additions:**
- `$dynamicRef`, `$dynamicAnchor` (dynamic scope references)
- `prefixItems` (typed tuple validation)
- `unevaluatedProperties`, `unevaluatedItems` (stricter validation)
- `dependentSchemas`, `dependentRequired` (replace `dependencies`)
- `$defs` (replaces `definitions`)

**Conversion Strategy:**
- 2020-12 → draft-07: Degrade gracefully (lose some constraints)
- draft-07 → 2020-12: Upgrade (use modern equivalents)

---

### D4: XSD Pattern Detection ✅

**Decision:** Auto-detect input XSD pattern (Element/Type Declaration analysis)

**Implementation:**

Add stdlib function `xsdGetPattern()`:

```kotlin
@UTLXFunction(
    description = "Detect XSD design pattern (russian-doll, salami-slice, venetian-blind, garden-of-eden)",
    category = "XSD"
)
fun xsdGetPattern(schema: UDM): String {
    val elements = xsdGetElements(schema)
    val types = xsdGetTypes(schema)

    // Count global vs local elements
    val globalElements = elements.filter { it.metadata["__scope"] == "global" }
    val localElements = elements.filter { it.metadata["__scope"] == "local" }

    // Count global vs local types
    val globalTypes = types.filter { it.metadata["__scope"] == "global" }
    val localTypes = types.filter { it.metadata["__scope"] == "local" }

    val elementDeclaration = if (globalElements.size > localElements.size) "global" else "local"
    val typeDeclaration = if (globalTypes.size > localTypes.size) "global" else "local"

    return when {
        elementDeclaration == "local" && typeDeclaration == "local" -> "russian-doll"
        elementDeclaration == "global" && typeDeclaration == "local" -> "salami-slice"
        elementDeclaration == "local" && typeDeclaration == "global" -> "venetian-blind"
        elementDeclaration == "global" && typeDeclaration == "global" -> "garden-of-eden"
        else -> "mixed" // No clear pattern
    }
}
```

**Usage in Transformations:**

```utlx
%utlx 1.0
input xsd
output xsd {pattern: "venetian-blind"}
---
let inputPattern = xsdGetPattern($input);
let outputPattern = "venetian-blind";

{
  __metadata: {
    conversionNote: "Converting from " + inputPattern + " to " + outputPattern
  },
  schema: $input
}
```

**Detection Algorithm:**

1. **Parse XSD** and tag each element/type with `__scope` metadata:
   ```kotlin
   metadata = mapOf(
       "__scope" to if (isTopLevelDeclaration) "global" else "local"
   )
   ```

2. **Count declarations:**
   - Global elements: Top-level `<xs:element>` declarations
   - Local elements: Nested `<xs:element>` within types
   - Global types: Top-level `<xs:complexType name="...">` or `<xs:simpleType name="...">`
   - Local types: Anonymous types within elements

3. **Classify pattern:**
   - Majority global elements + majority local types → Salami Slice
   - Majority local elements + majority global types → Venetian Blind
   - Majority global elements + majority global types → Garden of Eden
   - Majority local elements + majority local types → Russian Doll
   - Mixed → "mixed" (inconsistent pattern)

**Use Cases:**

**1. Pattern Conversion Logging:**
```utlx
%utlx 1.0
input xsd
output xsd {pattern: "venetian-blind"}
---
let detectedPattern = xsdGetPattern($input);
debug("Converting XSD from " + detectedPattern + " to venetian-blind");
$input
```

**2. Conditional Transformation:**
```utlx
%utlx 1.0
input xsd
output xsd
---
match xsdGetPattern($input) {
  "russian-doll" => {
    # Keep as-is for simple schemas
    output: $input,
    pattern: "russian-doll"
  },
  _ => {
    # Normalize to Venetian Blind
    output: $input,
    pattern: "venetian-blind"
  }
}
```

**3. Migration Reports:**
```utlx
%utlx 1.0
input: legacy xsd, modern xsd
output json
---
{
  legacy: {
    pattern: xsdGetPattern($legacy),
    elementCount: count(xsdGetElements($legacy))
  },
  modern: {
    pattern: xsdGetPattern($modern),
    elementCount: count(xsdGetElements($modern))
  },
  recommendation: if (xsdGetPattern($legacy) == "russian-doll") {
    "Migrate to venetian-blind for better maintainability"
  } else {
    "Pattern is acceptable"
  }
}
```

---

### D5: Configuration Override via `.utlxrc` ✅

**Decision:** Allow `.utlxrc` configuration file for global defaults

**File Format:** YAML (consistent with DataContract, OpenAPI, etc.)

**Location Priority:**
1. `$PWD/.utlxrc` (project-specific)
2. `$HOME/.utlxrc` (user-specific)
3. Built-in defaults (if no config found)

**Example `.utlxrc`:**

```yaml
# UTL-X Configuration File
# Location: .utlxrc (project root) or ~/.utlxrc (user home)

# XSD Format Defaults
xsd:
  defaultPattern: venetian-blind   # russian-doll | salami-slice | venetian-blind | garden-of-eden
  defaultVersion: "1.1"             # 1.0 | 1.1
  includeAnnotations: true
  includeAppInfo: true
  prettyPrint: true
  indent: "  "

# JSON Schema Defaults
jsch:
  defaultVersion: draft-07          # draft-04 | draft-07 | 2020-12
  includeExamples: true
  includeDefaults: true
  includeDescription: true
  prettyPrint: true
  indent: 2

# XML Format Defaults
xml:
  prettyPrint: true
  indent: "  "

# JSON Format Defaults
json:
  prettyPrint: true
  indent: 2

# YAML Format Defaults
yaml:
  prettyPrint: true
  indent: 2

# CSV Format Defaults
csv:
  delimiter: ","
  quote: "\""
  headers: true

# Global Settings
global:
  verbose: false
  debugLevel: info              # debug | info | warn | error
```

**Override Priority (highest to lowest):**

1. **CLI flags:** `--output-format xsd --xsd-pattern garden-of-eden`
2. **Script format options:** `output xsd {pattern: "salami-slice"}`
3. **Project `.utlxrc`:** `$PWD/.utlxrc`
4. **User `.utlxrc`:** `$HOME/.utlxrc`
5. **Built-in defaults:** Venetian Blind, draft-07, etc.

**CLI Flag Support:**

```bash
# Override default pattern
utlx transform schema.xsd transform.utlx -o output.xsd --xsd-pattern salami-slice

# Override JSON Schema version
utlx transform api.json schema.utlx -o schema.json --jsch-version 2020-12

# Override XSD version
utlx transform data.xml transform.utlx -o schema.xsd --xsd-version 1.0
```

**Implementation:**

**File:** `modules/core/src/main/kotlin/org/apache/utlx/core/config/ConfigLoader.kt`

```kotlin
data class UTLXConfig(
    val xsd: XSDConfig = XSDConfig(),
    val jsch: JSCHConfig = JSCHConfig(),
    val xml: XMLConfig = XMLConfig(),
    val json: JSONConfig = JSONConfig(),
    val yaml: YAMLConfig = YAMLConfig(),
    val csv: CSVConfig = CSVConfig(),
    val global: GlobalConfig = GlobalConfig()
)

data class XSDConfig(
    val defaultPattern: String = "venetian-blind",
    val defaultVersion: String = "1.1",
    val includeAnnotations: Boolean = true,
    val includeAppInfo: Boolean = true,
    val prettyPrint: Boolean = true,
    val indent: String = "  "
)

data class JSCHConfig(
    val defaultVersion: String = "draft-07",
    val includeExamples: Boolean = true,
    val includeDefaults: Boolean = true,
    val includeDescription: Boolean = true,
    val prettyPrint: Boolean = true,
    val indent: Int = 2
)

object ConfigLoader {
    fun load(): UTLXConfig {
        // 1. Load built-in defaults
        var config = UTLXConfig()

        // 2. Override with user config (~/.utlxrc)
        val userConfig = File(System.getProperty("user.home"), ".utlxrc")
        if (userConfig.exists()) {
            config = mergeConfig(config, parseYAML(userConfig))
        }

        // 3. Override with project config (./.utlxrc)
        val projectConfig = File(".utlxrc")
        if (projectConfig.exists()) {
            config = mergeConfig(config, parseYAML(projectConfig))
        }

        return config
    }
}
```

**Usage in Serializers:**

```kotlin
class XSDSerializer(
    private val config: XSDConfig = ConfigLoader.load().xsd,
    private val formatOptions: Map<String, Any> = emptyMap()
) {
    fun serialize(udm: UDM): String {
        // CLI/script options override config file
        val pattern = formatOptions["pattern"] as? String
                   ?: formatOptions["ElementDeclaration"]?.let { /* ... */ }
                   ?: config.defaultPattern

        val version = formatOptions["version"] as? String
                   ?: config.defaultVersion

        val generator = when (pattern) {
            "russian-doll" -> RussianDollGenerator(version)
            "salami-slice" -> SalamiSliceGenerator(version)
            "venetian-blind" -> VenetianBlindGenerator(version)
            "garden-of-eden" -> GardenOfEdenGenerator(version)
            else -> VenetianBlindGenerator(version) // fallback
        }

        return generator.generate(udm)
    }
}
```

**Enterprise Use Case:**

**Scenario:** Company standardizes on Garden of Eden pattern for all XSD schemas

**Setup:**
```bash
# Create company-wide config template
cat > ~/.utlxrc <<EOF
xsd:
  defaultPattern: garden-of-eden
  defaultVersion: "1.1"
  includeAnnotations: true
EOF

# Distribute to all developers
# Now all developers output Garden of Eden by default
```

**Override when needed:**
```utlx
# Specific project needs Venetian Blind
output xsd {pattern: "venetian-blind"}
```

---

### D6: Default Pattern - Venetian Blind ✅

**Decision:** Venetian Blind is the default XSD pattern

**Rationale:**

From `docs/architecture/decisions/xsd-discussions-style.md`:

> **Venetian Blind** is generally considered the best practice for most scenarios, as it provides the best balance of reusability, maintainability, and clean namespace management. It's the most commonly recommended pattern in enterprise environments.

**Industry Evidence:**
- XMLSpy defaults to Venetian Blind
- W3C XML Schema Primer recommends it
- Most enterprise schemas use Venetian Blind
- Balances reusability (global types) with encapsulation (local elements)

**Comparison:**

| Aspect | Russian Doll | Venetian Blind ⭐ | Salami Slice | Garden of Eden |
|--------|--------------|-------------------|--------------|----------------|
| **Reusability** | Low | High | Medium | Very High |
| **Encapsulation** | High | Medium | Medium | Low |
| **Namespace Clarity** | High | High | Low | Low |
| **Maintainability** | Low | High | Medium | Low |
| **Tool Support** | Universal | Universal | Universal | Universal |
| **Complexity** | Low | Medium | Medium | High |

**Default Behavior:**

```utlx
# All of these produce Venetian Blind output
output xsd
output xsd {}
output xsd {version: "1.1"}
```

**Explicit Override:**

```utlx
# Russian Doll (simple schemas)
output xsd {pattern: "russian-doll"}

# Salami Slice (integration scenarios)
output xsd {pattern: "salami-slice"}

# Garden of Eden (maximum reusability)
output xsd {pattern: "garden-of-eden"}
```

**Config Override:**

```yaml
# .utlxrc - Override default to Garden of Eden
xsd:
  defaultPattern: garden-of-eden
```

**Pattern Characteristics:**

**Venetian Blind:**
- ✅ **Elements declared locally** - Clear hierarchy, no namespace pollution
- ✅ **Types declared globally** - Reusable across schema, consistent typing
- ✅ **Best of both worlds** - Encapsulation + reusability
- ✅ **Clean namespaces** - Only types visible globally
- ✅ **Modular design** - Easy to extend and maintain

**Example Venetian Blind Output:**

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">

  <!-- Root element (local) -->
  <xs:element name="order" type="OrderType"/>

  <!-- Global types (reusable) -->
  <xs:complexType name="OrderType">
    <xs:sequence>
      <xs:element name="customer" type="CustomerType"/>
      <xs:element name="items" type="ItemListType"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="CustomerType">
    <xs:sequence>
      <xs:element name="id" type="xs:string"/>
      <xs:element name="name" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="ItemListType">
    <xs:sequence>
      <xs:element name="item" type="ItemType" maxOccurs="unbounded"/>
    </xs:sequence>
  </xs:complexType>

  <xs:complexType name="ItemType">
    <xs:sequence>
      <xs:element name="sku" type="xs:string"/>
      <xs:element name="quantity" type="xs:int"/>
    </xs:sequence>
  </xs:complexType>

</xs:schema>
```

---

## Implementation Impact

### Phase Adjustments

**No changes to timeline** - decisions align with original 10-week plan

**Phase-Specific Impacts:**

| Phase | Impact | Additional Work |
|-------|--------|-----------------|
| **Phase 1** | Minor | Add XSD 1.1 parsing (assertions, type alternatives) |
| **Phase 2** | None | Pattern generators already planned |
| **Phase 3** | Minor | Add CLI flags for config overrides |
| **Phase 4** | Minor | Add `xsdGetPattern()` function |
| **Phase 5** | Reduction | Remove validation functions (1-2 days saved) |
| **Phase 6** | Minor | Add `.utlxrc` documentation |

**Net Effect:** Slightly reduced scope (no validation library), same timeline.

---

## Updated Feature List

### XSD Features

**Parsing (Input):**
- ✅ XSD 1.0 and 1.1 support
- ✅ All 4 design patterns (auto-detect)
- ✅ Annotations, documentation, appinfo
- ✅ Namespaces, imports, includes
- ✅ Pattern detection via `xsdGetPattern()`

**Serialization (Output):**
- ✅ XSD 1.1 default, XSD 1.0 opt-in
- ✅ All 4 design patterns (configurable)
- ✅ Default: Venetian Blind
- ✅ Config override via `.utlxrc`
- ✅ CLI flag override: `--xsd-pattern`

**Standard Library:**
- ✅ Navigation: `xsdGetElements()`, `xsdGetTypes()`, `xsdGetAnnotations()`
- ✅ Documentation: `xsdExtractDocs()`, `xsdGetDocumentation()`, `xsdGetAppInfo()`
- ✅ Pattern Detection: `xsdGetPattern()`
- ✅ Type Conversion: `xsdTypeToJSONSchemaType()`, `xsdTypeToSQLType()`
- ✅ Transform: `xsdToJSONSchema()`, `xsdToDataContract()`
- ❌ Validation: `xsdValidateData()` - Not implemented (use external tools)

### JSON Schema Features

**Parsing (Input):**
- ✅ draft-04 (best-effort)
- ✅ draft-07 (full support)
- ✅ 2020-12 (full support)
- ✅ `$ref` resolution
- ✅ Auto-detect version from `$schema`

**Serialization (Output):**
- ✅ draft-07 (default)
- ✅ 2020-12 (opt-in via `{version: "2020-12"}`)
- ✅ Config override via `.utlxrc`
- ✅ CLI flag override: `--jsch-version`

**Standard Library:**
- ✅ Navigation: `jschGetProperties()`, `jschGetDefinitions()`, `jschGetRequired()`
- ✅ Documentation: `jschGetDescription()`, `jschGetExamples()`, `jschGetDefault()`
- ✅ Type Conversion: `jschTypeToXSDType()`, `jschTypeToSQLType()`
- ✅ Transform: `jschToXSD()`, `jschToDataContract()`
- ✅ Reference: `jschResolveRef()`
- ❌ Validation: `jschValidateData()` - Not implemented (use external tools)
- ✅ Syntax Check: `jschValidateSchema()` - Well-formedness only

---

## Testing Implications

### Updated Test Requirements

**Conformance Tests (25 total):**

**XSD Tests (12):**
- 01-03: Basic parsing (1.0, 1.1, annotations) ✅
- 04-07: Pattern generation (all 4 patterns) ✅
- 08: XSD → JSON Schema (draft-07) ✅
- 09: Pattern detection (`xsdGetPattern()`) ✅ **NEW**
- 10: XSD 1.1 assertions parsing ✅ **NEW**
- 11: Config override test (`.utlxrc`) ✅ **NEW**
- 12: Real-world customer.xsd ✅

**JSCH Tests (8):**
- 01-02: Parse draft-07, 2020-12 ✅
- 03: Parse with `$ref` ✅
- 04: Generate draft-07 (default) ✅
- 05: Generate 2020-12 (opt-in) ✅
- 06: JSON Schema → XSD ✅
- 07: Syntax validation (`jschValidateSchema()`) ✅
- 08: Real OpenAPI schema subset ✅

**Pattern Tests (5):**
- 11-13: Pattern conversions (Russian→Venetian, etc.) ✅
- 14: Preserve docs during pattern change ✅
- 15: Config default override test ✅ **NEW**

**Validation Tests:**
- ❌ Removed: `jschValidateData()` tests (no validation library)
- ✅ Kept: `jschValidateSchema()` syntax checking

---

## Documentation Updates

### New Documentation Required

**1. Configuration Guide:** `docs/configuration/utlxrc.md`
- `.utlxrc` file format
- Priority and override rules
- CLI flag reference
- Enterprise setup examples

**2. XSD Pattern Guide:** `docs/formats/xsd-patterns.md`
- Detailed explanation of 4 patterns
- When to use each pattern
- Pattern conversion strategies
- Pattern detection function

**3. Version Support:** `docs/formats/xsd-versions.md`
- XSD 1.0 vs 1.1 feature comparison
- JSON Schema draft-07 vs 2020-12
- Migration guides

**4. Validation Guide:** `docs/guides/external-validation.md`
- Why no built-in validation
- Recommended external tools
- Integration workflows
- Examples with `ajv`, `xmllint`

---

## Migration from Proposal

### Changes from Original Proposal

| Aspect | Original Proposal | Updated Decision |
|--------|------------------|------------------|
| **XSD Version** | XSD 1.0 only | XSD 1.0 + 1.1 ✅ |
| **Validation** | Optional library | No library ❌ |
| **JSCH Version** | TBD | draft-07 default, 2020-12 opt-in ✅ |
| **Pattern Detection** | TBD | Yes - `xsdGetPattern()` ✅ |
| **Config Override** | Not mentioned | Yes - `.utlxrc` ✅ |
| **Default Pattern** | Venetian Blind | Confirmed Venetian Blind ✅ |

### No Impact Areas

- ✅ Timeline remains 10 weeks
- ✅ Phase structure unchanged
- ✅ Module architecture unchanged
- ✅ UDM representation unchanged
- ✅ CLI integration plan unchanged
- ✅ Performance targets unchanged

---

## Summary

All open questions from the proposal have been answered with clear, implementable decisions:

1. ✅ **XSD 1.1 Support** - Modern features enabled
2. ✅ **No Validation Library** - External tool philosophy
3. ✅ **JSCH draft-07 Default** - Wide compatibility, 2020-12 opt-in
4. ✅ **Pattern Detection** - `xsdGetPattern()` function
5. ✅ **Config Override** - `.utlxrc` for enterprise standards
6. ✅ **Venetian Blind Default** - Industry best practice

**Next Step:** Proceed with Phase 1 implementation (Core Infrastructure, Week 1-2)

---

**END OF DESIGN DECISIONS**
