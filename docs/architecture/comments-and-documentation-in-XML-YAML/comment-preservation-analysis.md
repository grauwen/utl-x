# Comment and Documentation Preservation in UTL-X

**Author:** Analysis by Claude Code
**Date:** 2025-10-25
**Status:** Proposal / Research Document
**Related:** UDM Architecture, Format Handlers (XML, YAML, JSON, CSV)

---

## Executive Summary

This document analyzes the feasibility and design considerations for preserving comments and documentation across format transformations in UTL-X. Currently, all comments are discarded during parsing. This proposal explores options for preserving comments, particularly for XML ↔ YAML transformations.

**Key Findings:**
- ✅ JSON and CSV do NOT support standard comments (user was correct)
- ✅ UDM already has `metadata` field infrastructure (line 64 in `udm_core.kt`)
- ❌ Current parsers actively discard comments (`skipComment()` in XML, SnakeYAML strips YAML comments)
- 🎯 Primary use cases: DataContract documentation, config file templates, round-trip fidelity

---

## 1. Comment Support by Format

### Format Capabilities

| Format | Standard Support | Comment Syntax | Current UTL-X Handling |
|--------|-----------------|----------------|------------------------|
| **YAML** | ✅ Yes | `# line comments` | ❌ **Stripped** (SnakeYAML library discards) |
| **XML** | ✅ Yes | `<!-- block comments -->`, `<?processing instructions?>` | ❌ **Stripped** (`skipComment()` function in parser) |
| **JSON** | ❌ **No** | N/A (RFC 8259 prohibits) | N/A (standard doesn't support) |
| **CSV** | ⚠️ Informal | `# comments` (some implementations) | ❌ Not handled |

### Format-Specific Details

#### YAML Comments
```yaml
# Comment above
key: value  # End-of-line comment

# Multi-line comment
# continues here
nested:
  subkey: value  # inline comment
```

**Features:**
- Line comments only (`#` to end of line)
- Can appear anywhere except inside quoted strings
- Commonly used for documentation in config files
- No block comment syntax

#### XML Comments
```xml
<!-- Comment above element -->
<element attribute="value">
  content
  <!-- Inline comment -->
</element>
<!-- Comment below element -->

<?xml-stylesheet type="text/xsl" href="style.xsl"?>  <!-- Processing instruction -->
```

**Features:**
- Block comments (`<!-- -->`)
- Processing instructions (`<?target data?>`)
- Can appear almost anywhere in document
- Can span multiple lines
- Cannot appear inside tags or attribute values

#### JSON Comments
```json
// Standard JSON does NOT support comments
{
  "key": "value"  // This would be invalid JSON
}
```

**Reality:**
- RFC 8259 prohibits comments
- JSON5 adds `//` and `/* */` comments (non-standard)
- JSONC (JSON with Comments) used by VSCode (non-standard)
- Many parsers accept comments as extension

#### CSV Comments
```csv
# This is sometimes treated as a comment
Name,Age,City
John,30,NYC
# Another comment
Jane,25,LA
```

**Reality:**
- No RFC/standard for CSV comments
- Some tools treat `#` lines as comments
- Others treat it as data
- Highly implementation-dependent

---

## 2. Current UDM Architecture

### Existing Infrastructure

The `UDM.Object` type already has a `metadata` field:

```kotlin
// File: modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt
// Line: 60-64

data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val metadata: Map<String, String> = emptyMap()  // ✅ Already exists!
) : UDM()
```

**Current State:**
- ✅ `metadata` field exists
- ✅ Accessor methods: `getMetadata(key)`, `hasMetadata(key)`, `metadataKeys()`
- ❌ **Marked as "Internal metadata (not serialized)"** (line 64 comment)
- ❌ Not populated by any format parser
- ❌ Not used by any serializer

**Implication:** Infrastructure exists but is unused and non-serializable.

---

## 3. Current Parser Behavior

### XML Parser - Active Comment Removal

**File:** `formats/xml/src/main/kotlin/org/apache/utlx/formats/xml/xml_parser.kt`

```kotlin
// Line 156: Comment detection
if (peek(4) == "<!--") {
    skipComment()  // ❌ Actively discards comments
}

// Line 344-357: skipComment() implementation
private fun skipComment() {
    // <!--...-->
    repeat(4) { advance() } // Skip <!--

    while (!isAtEnd() && peek(3) != "-->") {
        if (isAtEnd()) {
            throw XMLParseException("Unterminated comment", line, column)
        }
        if (advance() == '\n') {
            line++
            column = 0
        }
    }

    repeat(3) { advance() } // Skip -->
}
```

**Behavior:** Comments are detected, parsed for syntax validation, then discarded.

### YAML Parser - Library Limitation

**File:** `formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLParser.kt`

```kotlin
// Line 32: Uses SnakeYAML library
private val yaml = Yaml()

// Line 79: Parse delegated to SnakeYAML
val yamlObject = yaml.load<Any?>(reader)
```

**Behavior:** SnakeYAML library strips comments automatically before returning parsed structure. No API to access comments.

**Options to Fix:**
1. Fork SnakeYAML and modify to preserve comments
2. Switch to different YAML library (SnakeYAML Engine, Jackson YAML)
3. Implement custom YAML parser (high effort)

### JSON/CSV Parsers

**JSON:** No comment support in standard, so N/A.

**CSV:** Currently no comment handling. Could add `#` line detection if desired.

---

## 4. Use Cases for Comment Preservation

### High-Value Use Cases

#### 1. DataContract Documentation
```yaml
servers:
  production:  # Primary production database - DO NOT modify without approval
    type: postgres
    host: prod.db.example.com
    port: 5432  # Standard PostgreSQL port

  staging:  # Safe environment for testing schema changes
    type: postgres
    host: staging.db.example.com
    port: 5432

models:
  orders:  # Core order processing table
    type: table
    fields:
      order_id:  # Auto-incrementing primary key
        type: integer
        required: true
        primaryKey: true
```

**Desired:** Preserve field descriptions when transforming DataContract files.

#### 2. Configuration File Templates
```xml
<!-- Database Configuration Template -->
<!-- Generated by UTL-X on 2025-10-25 -->
<configuration>
  <!-- Production Settings - Modify carefully -->
  <database>
    <host>prod.db.example.com</host>
    <!-- Port 5432 is standard for PostgreSQL -->
    <port>5432</port>
  </database>
</configuration>
```

**Desired:** Generate configs with explanatory comments from templates.

#### 3. Round-Trip Transformation Fidelity
```
YAML (with comments)
  ↓ parse
UDM (comments preserved)
  ↓ transform
UDM (comments still attached)
  ↓ serialize
YAML (comments restored)
```

**Desired:** Transform data without losing human documentation.

#### 4. Documentation Extraction
```yaml
# @doc: This server configuration defines database connection parameters
# @author: DevOps Team
# @version: 2.1
servers:
  production:
    type: postgres
```

**Desired:** Extract structured documentation for external tools.

---

## 5. Technical Challenges

### Challenge 1: Storage Location in UDM

**Question:** Where should comments be stored in the UDM tree?

#### Option A: Extend Metadata Field (Current)

**Approach:** Use existing `metadata` field with special keys:

```kotlin
UDM.Object(
    properties = mapOf("host" to UDM.Scalar("prod.db.example.com")),
    metadata = mapOf(
        "comment.above" to "# Primary production database",
        "comment.inline.host" to "# Standard port",
        "comment.below" to "# End of production section"
    )
)
```

**Pros:**
- ✅ No UDM changes needed
- ✅ Quick to implement
- ✅ Backwards compatible

**Cons:**
- ❌ String-based keys are fragile
- ❌ Difficult to handle multiple comments per element
- ❌ No type safety

#### Option B: Add Comment List to UDM.Object

**Approach:** Add explicit `comments` field:

```kotlin
data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val comments: List<Comment> = emptyList()  // ✅ New field
) : UDM()

data class Comment(
    val text: String,
    val position: CommentPosition,
    val associatedKey: String? = null  // Which property this comment relates to
)

enum class CommentPosition {
    ABOVE,      // Line(s) above the element
    INLINE,     // Same line as element
    BELOW,      // Line(s) after element
    INTERNAL    // Inside element (for XML)
}
```

**Pros:**
- ✅ Type-safe
- ✅ Explicit structure
- ✅ Easy to serialize/deserialize
- ✅ Supports multiple comments per element

**Cons:**
- ❌ Requires UDM changes
- ❌ Increases memory footprint
- ❌ Need to update all UDM creation sites

#### Option C: Separate Comment AST

**Approach:** Parallel tree structure:

```kotlin
data class UDMWithComments(
    val node: UDM,
    val commentTree: CommentTree
)

data class CommentTree(
    val nodeComments: Map<NodePath, List<Comment>>,
    val documentComments: List<Comment>  // Top-level comments
)
```

**Pros:**
- ✅ UDM remains unchanged
- ✅ Comments completely separate from data
- ✅ Easy to strip comments (just use UDM alone)

**Cons:**
- ❌ Complex to keep in sync during transformations
- ❌ Path-based mapping is brittle
- ❌ Harder to implement transformations

### Challenge 2: Comment Semantics Vary by Format

Different formats have different comment capabilities:

| Feature | XML | YAML | JSON | CSV |
|---------|-----|------|------|-----|
| Block comments | ✅ `<!-- -->` | ❌ | ❌ | ❌ |
| Line comments | ❌ | ✅ `#` | ❌ | ⚠️ `#` |
| End-of-line | ✅ (awkward) | ✅ | ❌ | ⚠️ |
| Inline (within content) | ✅ | ❌ | ❌ | ❌ |
| Processing instructions | ✅ `<? ?>` | ❌ | ❌ | ❌ |
| Multi-line | ✅ | ✅ | ❌ | ⚠️ |

**Problem:** How to map between incompatible formats?

**Example:**
```yaml
# YAML comment above
key: value  # End-of-line comment
```

**Transform to XML - Multiple Options:**

```xml
<!-- Option 1: All comments above -->
<!-- YAML comment above -->
<!-- End-of-line comment -->
<key>value</key>

<!-- Option 2: Try to preserve positioning -->
<!-- YAML comment above -->
<key>value</key><!-- End-of-line comment -->

<!-- Option 3: Use attributes for EOL -->
<key comment="End-of-line comment">value</key>

<!-- Option 4: Processing instructions -->
<!-- YAML comment above -->
<key>value</key>
<?comment End-of-line comment?>
```

**No single "correct" answer - needs semantic mapping rules.**

### Challenge 3: Performance Impact

Estimated overhead for comment preservation:

| Operation | Overhead | Reason |
|-----------|----------|--------|
| Parsing | +10-20% | Need to capture, store comments |
| Memory | +15-30% | Comment storage in UDM |
| Serialization | +5-10% | Position and emit comments |
| Transformation | +0-5% | Most operations unaffected |

**Mitigation:** Make comment preservation opt-in.

### Challenge 4: Transformation Semantics

**Question:** What happens to comments during transformations?

**Scenario 1: Simple mapping**
```yaml
# Comment about old field
oldName: value
```

**Transform:**
```utlx
{ newName: $input.oldName }
```

**Output options:**
```yaml
# Option A: Comment moves with data
# Comment about old field
newName: value

# Option B: Comment discarded (refers to old field name)
newName: value

# Option C: Comment updated
# Comment about newName (was oldName)
newName: value
```

**Scenario 2: Filtering**
```yaml
items:
  - name: A  # Keep this
  - name: B  # Remove this
  - name: C  # Keep this
```

**Transform:**
```utlx
{ items: $input.items |> filter(item => item.name != "B") }
```

**What happens to "Remove this" comment?**

---

## 6. Proposed Architecture Options

### Option 1: Opt-In Comment Preservation (RECOMMENDED)

**Philosophy:** Comments add overhead. Make users explicitly request preservation.

**Implementation:**

```kotlin
// Parser options
data class ParseOptions(
    val preserveComments: Boolean = false,  // Default: discard for performance
    val commentType: CommentType = CommentType.ALL
)

enum class CommentType {
    ALL,            // All comments
    DOCUMENTATION,  // Only @doc style comments
    NONE           // Strip all comments
}

// Serializer options
data class SerializeOptions(
    val includeComments: Boolean = false,
    val commentStyle: CommentStyle = CommentStyle.AUTO
)

enum class CommentStyle {
    AUTO,    // Use format's native style
    XML,     // Force <!-- --> style
    YAML,    // Force # style
    MINIMAL  // Emit as little as possible
}
```

**Usage in UTL-X:**
```utlx
%utlx 1.0
input: datacontract yaml { preserveComments: true }
output yaml { includeComments: true }
---
$datacontract  // Comments flow through
```

**Advantages:**
- ✅ No performance impact for users who don't need comments
- ✅ Explicit intent in transformation scripts
- ✅ Backwards compatible (default behavior unchanged)

**Disadvantages:**
- ❌ More complex API
- ❌ Users must remember to enable feature

### Option 2: Always-On Preservation

**Philosophy:** Comments are data. Preserve by default like other content.

**Implementation:**
- All parsers capture comments
- All serializers emit comments
- Use `stripComments: true` option to discard

**Advantages:**
- ✅ Simpler mental model
- ✅ Least surprising behavior
- ✅ No need to remember options

**Disadvantages:**
- ❌ Performance cost for all users
- ❌ Memory overhead even when not needed
- ❌ Breaking change (behavior changes)

### Option 3: Documentation-Only Preservation (PRAGMATIC)

**Philosophy:** Only preserve special "documentation" comments, discard regular comments.

**Implementation:**
- Define documentation comment syntax:
  - YAML: `#@` or `# @doc:`
  - XML: `<!-- @doc: -->` or `<?doc ?>`
- Only capture and preserve documentation comments
- Regular comments still discarded

**Example:**
```yaml
#@ This is documentation - PRESERVED
# This is a regular comment - DISCARDED
servers:
  production:  #@ Primary database - PRESERVED
    type: postgres  # implementation note - DISCARDED
```

**Advantages:**
- ✅ Lower overhead (fewer comments stored)
- ✅ Clear distinction between documentation and implementation notes
- ✅ Solves primary use case (DataContract documentation)

**Disadvantages:**
- ❌ Requires users to change comment syntax
- ❌ May not cover all use cases
- ❌ "Regular" comments still lost

---

## 7. Semantic Mapping Rules

### XML ↔ YAML Comment Mapping

#### Rule Set 1: Position-Based Mapping

```
XML                              YAML
========================         ========================
<!-- Above comment -->       →   # Above comment
<element>value</element>     →   element: value

<element>                    →   element:
  <!-- Internal -->          →     # Internal
  <sub>value</sub>           →     sub: value
</element>                   →

<!-- Below comment -->       →   # Below comment
```

#### Rule Set 2: Type-Based Mapping

```
Comment Type             XML Representation          YAML Representation
====================     ======================      ===================
Documentation            <!-- @doc: text -->        # @doc: text
Regular                  <!-- text -->              # text
Processing Instruction   <?target data?>           # PI: target data
CDATA (not comment)      <![CDATA[...]]>           (preserved as text)
```

#### Rule Set 3: Positioning Heuristics

**End-of-line comments:**
```yaml
key: value  # EOL comment
```

**Options for XML:**
1. **Inline after element:** `<key>value</key><!-- EOL comment -->`
2. **Attribute:** `<key _comment="EOL comment">value</key>`
3. **Move to above:** `<!-- EOL comment -->\n<key>value</key>`
4. **Discard and warn:** Inform user EOL comments can't be perfectly preserved

**Recommendation:** Option 3 (move to above) for maximum compatibility.

---

## 8. UDM Enhancement Proposal

### Minimal Change Approach

**Change 1: Make metadata serializable**

```kotlin
// udm_core.kt - Line 60-89
data class Object(
    val properties: Map<String, UDM>,
    val attributes: Map<String, String> = emptyMap(),
    val name: String? = null,
    val metadata: Map<String, String> = emptyMap(),  // ✅ Remove "(not serialized)" comment
    val comments: List<Comment> = emptyList()  // ✅ NEW
) : UDM()
```

**Change 2: Add Comment type**

```kotlin
// Add to udm_core.kt

/**
 * Represents a comment or documentation annotation in source data.
 * Used to preserve comments during format transformations.
 */
data class Comment(
    val text: String,
    val position: CommentPosition = CommentPosition.ABOVE,
    val type: CommentType = CommentType.REGULAR,
    val target: String? = null  // Which property/element this comment relates to
) {
    companion object {
        fun documentation(text: String, target: String? = null) =
            Comment(text, CommentPosition.ABOVE, CommentType.DOCUMENTATION, target)

        fun inline(text: String, target: String? = null) =
            Comment(text, CommentPosition.INLINE, CommentType.REGULAR, target)
    }
}

enum class CommentPosition {
    ABOVE,      // Line(s) before the element
    INLINE,     // Same line as element (EOL)
    BELOW,      // Line(s) after element
    INTERNAL    // Inside element (for XML elements with children)
}

enum class CommentType {
    REGULAR,        // Standard comment
    DOCUMENTATION,  // Documentation comment (@doc style)
    PRAGMA         // Processing instruction (XML <?...?>)
}
```

### UDM Helper Methods

```kotlin
// Add to UDM.Object class

fun withComment(comment: Comment): Object =
    copy(comments = comments + comment)

fun withComments(newComments: List<Comment>): Object =
    copy(comments = newComments)

fun getCommentsFor(key: String): List<Comment> =
    comments.filter { it.target == key }

fun getDocumentation(): List<Comment> =
    comments.filter { it.type == CommentType.DOCUMENTATION }

fun stripComments(): Object =
    copy(comments = emptyList())
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Immediate)

**Goals:**
- Add comment infrastructure to UDM
- Enable documentation comment capture
- No breaking changes

**Tasks:**
1. ✅ Add `comments: List<Comment>` to `UDM.Object`
2. ✅ Add `Comment`, `CommentPosition`, `CommentType` types
3. ✅ Add helper methods to `UDM.Object`
4. ✅ Update format parsers to accept `ParseOptions(preserveComments: Boolean)`
5. ✅ Update XML parser to capture comments when enabled
6. ⚠️ YAML parser: Use metadata field temporarily (SnakeYAML limitation)

**Deliverable:** Infrastructure in place, documentation comments work for XML.

### Phase 2: Full XML Comment Support

**Goals:**
- Complete XML comment preservation
- Handle all comment positions
- Support processing instructions

**Tasks:**
1. Capture `<!-- -->` comments at all positions
2. Capture `<?...?>` processing instructions
3. Implement position detection (above/inline/below/internal)
4. XML serializer: emit comments in correct positions
5. Add stdlib functions: `getComments()`, `addComment()`, `stripComments()`

**Deliverable:** Full XML → XML comment preservation.

### Phase 3: YAML Comment Support

**Goals:**
- YAML comment preservation
- XML ↔ YAML comment mapping

**Options:**
- **Option A:** Switch to different YAML library (Jackson YAML, SnakeYAML Engine)
- **Option B:** Fork SnakeYAML and add comment preservation
- **Option C:** Implement custom YAML parser (highest effort)

**Tasks:**
1. Evaluate YAML library options
2. Implement comment capture for YAML
3. Implement semantic mapping rules (XML ↔ YAML)
4. YAML serializer: emit `#` comments
5. Add conformance tests for comment preservation

**Deliverable:** XML ↔ YAML transformations preserve comments.

### Phase 4: Polish & Optimization

**Goals:**
- Performance optimization
- Advanced features
- Documentation

**Tasks:**
1. Lazy comment loading (don't parse if not needed)
2. Comment manipulation functions in stdlib
3. Comment validation and linting
4. Documentation: Best practices for comment preservation
5. Examples: DataContract with comments, config templates

**Deliverable:** Production-ready comment preservation.

---

## 10. Stdlib Function Proposals

### Comment Inspection Functions

```utlx
// Get all comments from an object
getComments(obj) => Comment[]

// Get comments for specific property
getComments(obj, "propertyName") => Comment[]

// Get only documentation comments
getDocumentation(obj) => Comment[]

// Check if object has comments
hasComments(obj) => Boolean
```

### Comment Manipulation Functions

```utlx
// Add comment to object
addComment(obj, comment: String, position: String) => Object
addComment(obj, comment: String, position: String, target: String) => Object

// Remove all comments
stripComments(obj) => Object

// Remove comments from specific property
stripComments(obj, "propertyName") => Object

// Update comment text
updateComment(obj, oldText: String, newText: String) => Object
```

### Comment Transformation Functions

```utlx
// Convert XML comments to YAML style
convertCommentsToYAML(obj) => Object

// Convert YAML comments to XML style
convertCommentsToXML(obj) => Object

// Extract documentation as separate structure
extractDocumentation(obj) => { docs: Map<String, String>, data: Object }
```

---

## 11. Backward Compatibility

### Ensuring No Breaking Changes

**Strategy:**
1. **Default behavior unchanged:** Comments discarded by default
2. **Opt-in activation:** Require `preserveComments: true` option
3. **Empty lists by default:** `comments = emptyList()` for all existing code
4. **Ignore empty comment lists:** Serializers skip empty `comments`

**Example:**
```kotlin
// Old code - still works
val obj = UDM.Object(mapOf("key" to UDM.Scalar("value")))

// New code - explicit comments
val obj = UDM.Object(
    properties = mapOf("key" to UDM.Scalar("value")),
    comments = listOf(Comment("This is a comment", CommentPosition.ABOVE))
)
```

---

## 12. Performance Considerations

### Memory Overhead

**Without comment preservation:**
```
UDM.Object (32 bytes base) +
  properties (24 bytes Map) +
  data (variable)
= ~56+ bytes per object
```

**With comment preservation:**
```
UDM.Object (32 bytes base) +
  properties (24 bytes Map) +
  comments (24 bytes List + comments) +
  data (variable)
= ~80+ bytes per object + comment storage
```

**Overhead:** ~40-50% memory increase per object if comments present.

**Mitigation:**
- Opt-in: Users who don't need comments pay nothing
- Lazy loading: Don't allocate comment list until first comment added
- Shared empty list: Use `emptyList()` singleton for objects without comments

### Parse Time Overhead

**Estimated impact:**
- Comment detection: +5% (already done for validation)
- Comment storage: +5-10% (string allocation, list operations)
- **Total: +10-15% parse time when `preserveComments: true`**

**Mitigation:**
- Only parse comments when requested
- Bulk comment operations (parse all at once)
- Efficient string handling (avoid repeated allocations)

### Serialization Time Overhead

**Estimated impact:**
- Comment positioning: +3-5%
- Comment emission: +2-5%
- **Total: +5-10% serialization time when comments present**

**Mitigation:**
- Skip comment processing if list empty
- Batch comment output
- Optimize comment format conversion

---

## 13. Open Questions

### Questions for User/Stakeholders

1. **Primary Use Case:**
   - DataContract documentation preservation?
   - General config file templates?
   - Round-trip transformation fidelity?
   - Documentation extraction for tools?

2. **Scope of Preservation:**
   - Documentation comments only (@doc style)?
   - All comments with exact positioning?
   - Smart preservation (allow reformatting)?

3. **Default Behavior:**
   - Opt-in (explicit `preserveComments: true`)?
   - Always-on (explicit `stripComments: true` to discard)?
   - Format-specific defaults (XML/YAML preserve, JSON/CSV discard)?

4. **Cross-Format Mapping:**
   - How should EOL comments map XML → YAML?
   - Should processing instructions be preserved as comments?
   - What happens to comments on deleted/filtered elements?

5. **Performance Tolerance:**
   - Is 10-15% parse overhead acceptable?
   - Is 40-50% memory overhead acceptable (when comments present)?
   - Should we provide "fast path" for comment-free documents?

---

## 14. Recommendations

### Immediate Action (Phase 1)

**Recommendation:** Implement **documentation-only** comment preservation with **opt-in** behavior.

**Rationale:**
- ✅ Solves primary use case (DataContract documentation)
- ✅ Minimal performance impact (fewer comments stored)
- ✅ Clear user intent (`preserveDocumentation: true`)
- ✅ Lower implementation complexity
- ✅ Foundation for full comment support later

**Implementation:**
1. Add `comments: List<Comment>` to `UDM.Object`
2. Add `preserveDocumentation: Boolean` to `ParseOptions`
3. XML parser: capture `<!-- @doc: ... -->` comments only
4. YAML parser: capture `# @doc: ...` lines using metadata field (temporary workaround)
5. Serializers: emit documentation comments when `includeDocumentation: true`

**Timeline:** 2-3 weeks for basic implementation

### Long-Term Vision (Phase 2-4)

**Recommendation:** Expand to **full comment preservation** based on user feedback.

**Path:**
1. Gather feedback on documentation-only approach
2. Measure actual performance impact in production
3. Evaluate YAML library options (Jackson YAML vs fork SnakeYAML)
4. Implement full comment support if demand justifies effort
5. Add stdlib functions for comment manipulation

**Timeline:** 3-6 months for complete implementation

---

## 15. References

### Internal References
- `modules/core/src/main/kotlin/org/apache/utlx/core/udm/udm_core.kt` (lines 60-89) - UDM.Object definition
- `formats/xml/src/main/kotlin/org/apache/utlx/formats/xml/xml_parser.kt` (lines 156, 344-357) - XML comment handling
- `formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLParser.kt` (lines 32, 79) - YAML parser
- `stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLCanonicalizationFunctions.kt` (lines 47, 56, 61) - c14n comment handling

### External References
- [RFC 8259 - JSON Specification](https://tools.ietf.org/html/rfc8259) - JSON does not support comments
- [YAML 1.2 Specification](https://yaml.org/spec/1.2/spec.html) - YAML comment syntax
- [XML 1.0 Specification](https://www.w3.org/TR/xml/) - XML comment and PI syntax
- [RFC 4180 - CSV Specification](https://tools.ietf.org/html/rfc4180) - CSV has no comment standard
- [Canonical XML 1.0](http://www.w3.org/TR/2001/REC-xml-c14n-20010315) - Comment handling in canonicalization

### Related Tools
- **DataWeave:** No comment preservation in transformations
- **XSLT:** Can preserve XML comments with special handling (`<xsl:comment>`)
- **JQ:** JSON only, no comments
- **Jackson YAML:** Java library with potential comment support

---

## 16. Conclusion

Comment preservation in UTL-X is **technically feasible** and has **clear use cases**, particularly for DataContract documentation and configuration file templates. The existing UDM architecture already has infrastructure (`metadata` field) that can be enhanced to support comments.

**Key Decision Points:**
1. **Scope:** Documentation-only vs. full comment preservation
2. **Default:** Opt-in vs. always-on
3. **Timeline:** Immediate basic support vs. full implementation

**Recommended Path:**
- Start with **opt-in, documentation-only** preservation
- Use existing `metadata` field as interim solution for YAML
- Enhance UDM with proper `comments: List<Comment>` field
- Expand to full comment support based on user demand

**Next Steps:**
1. Present proposal to stakeholders
2. Gather requirements and priorities
3. Create implementation plan for chosen approach
4. Build prototype for validation

---

**Document Status:** ✅ Complete - Ready for stakeholder review and decision

**Questions to Answer:** See section 13 (Open Questions)

**Implementation Estimate:**
- Phase 1 (Documentation only): 2-3 weeks
- Phase 2-3 (Full XML/YAML support): 2-3 months
- Phase 4 (Polish): 1-2 months
- **Total:** 4-6 months for complete feature
