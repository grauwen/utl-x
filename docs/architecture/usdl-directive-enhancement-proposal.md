# USDL Directive Registry Enhancement Proposal

**Date:** 2025-11-18
**Status:** Proposal
**Purpose:** Enhance USDL Directive Registry with comprehensive guidance for learning resources

---

## Current Structure Analysis

### Existing Fields in `DirectiveInfo`

```kotlin
data class DirectiveInfo(
    val name: String,              // e.g., "%namespace"
    val tier: String,              // "core", "common", "format_specific", "reserved"
    val scopes: List<String>,      // ["TOP_LEVEL", "FIELD_DEFINITION"]
    val valueType: String,         // "String", "Object", "Boolean", etc.
    val required: Boolean,         // Is this directive required?
    val description: String,       // Short description
    val supportedFormats: List<String>, // ["xsd", "jsch", "proto", "avro", ...]
    val examples: List<String>,    // Short inline examples
    val syntax: String,            // Syntax pattern
    val tooltip: String,           // IDE hover tooltip (auto-generated)
    val seeAlso: List<String>      // Related directives
)
```

### Strengths
✅ Good coverage of basic metadata
✅ Format compatibility tracking
✅ Multi-scope support
✅ Examples included
✅ JSON-serializable for REST API

### Limitations for Learning Resources
❌ **Limited examples** - Only short inline string examples
❌ **No guidance text** - Missing "when to use", "common pitfalls", "best practices"
❌ **No categorization** - Can't group related directives (e.g., "all constraint directives")
❌ **No difficulty level** - Beginner vs advanced directives not distinguished
❌ **No usage patterns** - Missing common combinations (e.g., "%name + %type + %required")
❌ **No anti-patterns** - What NOT to do
❌ **No format-specific notes** - Why a directive behaves differently in XSD vs Avro
❌ **No complete examples** - Missing full schema context showing directive in use

---

## Proposed Enhancement Strategy

### Option 1: **Extend Directive Data Class** (Recommended)

Add new fields to `DirectiveInfo` without breaking existing structure:

```kotlin
data class DirectiveInfo(
    // === EXISTING FIELDS (unchanged) ===
    val name: String,
    val tier: String,
    val scopes: List<String>,
    val valueType: String,
    val required: Boolean,
    val description: String,
    val supportedFormats: List<String>,
    val examples: List<String>,        // Keep for backward compatibility (short snippets)
    val syntax: String,
    val tooltip: String,
    val seeAlso: List<String>,

    // === NEW GUIDANCE FIELDS ===
    val longDescription: String = "",   // Extended explanation (markdown)
    val usageGuidance: String = "",     // When to use this directive
    val bestPractices: List<String> = emptyList(),  // Best practice tips
    val commonPitfalls: List<String> = emptyList(), // Common mistakes
    val antiPatterns: List<String> = emptyList(),   // What NOT to do

    // === COMPLETE EXAMPLES ===
    val completeExamples: List<CompleteExample> = emptyList(),

    // === CATEGORIZATION ===
    val categories: List<String> = emptyList(),  // ["constraint", "validation", "structure"]
    val difficultyLevel: String = "beginner",    // "beginner", "intermediate", "advanced"
    val tags: List<String> = emptyList(),        // Searchable tags

    // === FORMAT-SPECIFIC NOTES ===
    val formatNotes: Map<String, String> = emptyMap(),  // Format-specific behavior

    // === USAGE PATTERNS ===
    val commonCombinations: List<DirectiveCombination> = emptyList(),
    val requiredWith: List<String> = emptyList(),   // Directives that must accompany this one
    val incompatibleWith: List<String> = emptyList() // Directives that conflict
)

/**
 * Complete example showing directive in context
 */
data class CompleteExample(
    val format: String,          // "xsd", "avro", "proto", "common", etc.
    val title: String,           // "Simple Customer Schema"
    val description: String,     // What this example demonstrates
    val code: String,            // Full USDL schema code
    val highlightDirective: Boolean = true  // Whether to highlight this directive
)

/**
 * Common directive combination pattern
 */
data class DirectiveCombination(
    val directives: List<String>,  // ["%name", "%type", "%required"]
    val description: String,       // "Standard field definition"
    val example: String           // Complete example
)
```

### Option 2: **Companion Guidance Object** (Alternative)

Keep `DirectiveInfo` lean, create separate guidance structure:

```kotlin
// Existing DirectiveInfo stays unchanged

/**
 * Extended guidance for a directive (separate from core metadata)
 */
data class DirectiveGuidance(
    val directiveName: String,     // Links to DirectiveInfo.name
    val longDescription: String,
    val usageGuidance: String,
    val bestPractices: List<String>,
    val commonPitfalls: List<String>,
    val antiPatterns: List<String>,
    val exampleFiles: List<ExampleFileReference>,
    val categories: List<String>,
    val difficultyLevel: String,
    val tags: List<String>,
    val formatNotes: Map<String, String>,
    val commonCombinations: List<DirectiveCombination>,
    val requiredWith: List<String>,
    val incompatibleWith: List<String>
)

// New registry method
object DirectiveRegistry {
    fun getDirectiveGuidance(name: String): DirectiveGuidance?
}
```

**Pros:** Separation of concerns, backward compatible
**Cons:** Two lookups needed, more complex API

---

## Recommendation: **Option 1 (Extended DirectiveInfo)**

### Reasoning:
1. **Single source of truth** - All directive information in one place
2. **REST API simplicity** - One endpoint `/api/usdl/directives/{name}` returns everything
3. **Frontend simplicity** - One data structure to work with
4. **Gradual migration** - New fields have defaults, existing code unaffected
5. **JSON serialization** - Works seamlessly with existing API

### Implementation Plan

#### Phase 1: Update Data Structures (Week 1)

**File: `/schema/src/main/kotlin/org/apache/utlx/schema/usdl/DirectiveRegistry.kt`**

1. Add new fields to `DirectiveInfo` with defaults
2. Create `CompleteExample` data class
3. Create `DirectiveCombination` data class
4. Update `toDirectiveInfo()` to populate new fields

**File: `/schema/src/main/kotlin/org/apache/utlx/schema/usdl/USDL10.kt`**

5. Add new fields to `Directive` data class
6. Update tier directive definitions with enhanced guidance

#### Phase 2: Populate Guidance Data (Week 2-3)

**Strategy:** Start with Tier 1 (Core) directives, then expand

For each directive in USDL10.kt:
```kotlin
Directive(
    name = "%namespace",
    tier = Tier.CORE,
    scopes = setOf(Scope.TOP_LEVEL),
    valueType = "String",
    description = "Schema namespace or package name",

    // === NEW FIELDS ===
    longDescription = """
        The %namespace directive defines the package, namespace, or URI for your schema.
        This maps to different concepts depending on the output format:
        - XSD: targetNamespace attribute
        - Avro: namespace field
        - Proto: package declaration
        - Java: package name
    """.trimIndent(),

    usageGuidance = """
        Use %namespace to:
        - Organize schemas into logical packages
        - Avoid naming conflicts with other schemas
        - Follow your organization's naming conventions

        Best used at: TOP_LEVEL (first directive in schema)
    """.trimIndent(),

    bestPractices = listOf(
        "Use reverse domain notation for namespaces (e.g., com.company.product)",
        "Keep namespaces consistent across related schemas",
        "Use lowercase for namespace components",
        "Include version in namespace for breaking changes (e.g., com.example.v2)"
    ),

    commonPitfalls = listOf(
        "Forgetting to include namespace in complex multi-schema projects",
        "Using spaces or special characters in namespace",
        "Changing namespace in a way that breaks compatibility"
    ),

    antiPatterns = listOf(
        "DON'T use generic namespaces like 'data' or 'schema'",
        "DON'T mix namespace styles (URI vs package notation) in same project",
        "DON'T include environment names in namespace (use version instead)"
    ),

    completeExamples = listOf(
        CompleteExample(
            format = "common",
            title = "Basic Person Schema",
            description = "Shows namespace in a simple universal schema",
            code = """
                %utlx 1.0
                output xsd
                ---
                %namespace: "com.example.person"
                %version: "1.0"
                %documentation: "Basic person entity"

                %types: {
                  Person: {
                    %kind: "structure",
                    %fields: [
                      {%name: "id", %type: "string", %required: true},
                      {%name: "name", %type: "string", %required: true}
                    ]
                  }
                }
            """.trimIndent()
        ),
        CompleteExample(
            format = "xsd",
            title = "XSD Customer Schema",
            description = "XSD-specific namespace usage (URI format)",
            code = """
                %utlx 1.0
                output xsd
                ---
                %namespace: "http://example.com/customer"
                %version: "1.0"
                %elementFormDefault: "qualified"

                %types: {
                  Customer: {
                    %kind: "structure",
                    %fields: [
                      {%name: "customerId", %type: "string", %required: true},
                      {%name: "name", %type: "string", %required: true}
                    ]
                  }
                }
            """.trimIndent()
        ),
        CompleteExample(
            format = "proto",
            title = "Protobuf Message",
            description = "Protobuf package notation",
            code = """
                %utlx 1.0
                output proto
                ---
                %namespace: "com.example.user"
                %version: "1.0"
                %syntax: "proto3"

                %types: {
                  User: {
                    %kind: "structure",
                    %fields: [
                      {%name: "userId", %type: "string", %fieldNumber: 1},
                      {%name: "username", %type: "string", %fieldNumber: 2}
                    ]
                  }
                }
            """.trimIndent()
        )
    ),

    categories = listOf("metadata", "organization", "top-level"),
    difficultyLevel = "beginner",
    tags = listOf("namespace", "package", "organization", "uri"),

    formatNotes = mapOf(
        "xsd" to "Maps to targetNamespace. Should be a valid URI (http://... or urn:...)",
        "proto" to "Maps to package. Use dot notation without quotes",
        "avro" to "Maps to namespace field in schema",
        "jsch" to "Maps to $id or namespace depending on schema version",
        "odata" to "Maps to schema namespace in EDMX"
    ),

    commonCombinations = listOf(
        DirectiveCombination(
            directives = listOf("%namespace", "%version", "%types"),
            description = "Standard schema header",
            example = """
                %namespace: "com.example.orders"
                %version: "1.0"
                %types: { ... }
            """.trimIndent()
        )
    ),

    requiredWith = listOf(),  // No required companions
    incompatibleWith = listOf(),  // No conflicts

    // Existing fields
    syntax = "%namespace: \"value\"",
    examples = listOf(
        "%namespace: \"http://example.com/customer\"",
        "%namespace: \"com.example.orders\"",
        "%namespace: \"urn:schemas:orders:v1\""
    ),
    seeAlso = listOf("%version", "%types")
)
```

#### Phase 3: Validate Examples (Week 3)

**Test complete examples:**

- Ensure all `completeExamples` are valid USDL syntax
- Verify examples compile for their target formats
- Add tests to ensure examples stay current
- Create examples for all Tier 1 and common Tier 2 directives

#### Phase 4: REST API Enhancement (Week 4)

**Endpoint updates:**

```
GET /api/usdl/directives/{name}
Response now includes:
- longDescription
- usageGuidance
- bestPractices
- completeExamples (with embedded code)
- categories
- difficultyLevel
- formatNotes
- commonCombinations

GET /api/usdl/directives/{name}/examples
Returns list of CompleteExample with inline code

GET /api/usdl/directives/categories/{category}
Returns all directives in a category

GET /api/usdl/directives/difficulty/{level}
Returns all directives at a difficulty level
```

#### Phase 5: Frontend Integration (Week 5)

**USDL Directives Tab Updates:**

1. **Enhanced Details Panel:**
   - Show long description (markdown)
   - Display usage guidance
   - List best practices with checkmarks
   - Show common pitfalls with warnings
   - Display anti-patterns with X icons

2. **Example Viewer:**
   - New "Complete Examples" section in details panel
   - Display full USDL schema with syntax highlighting
   - Format selector to switch between formats (common, xsd, avro, etc.)
   - "Copy Example" button to copy to clipboard
   - "Insert Example" button to insert into editor

3. **Filtering/Categorization:**
   - Filter by category
   - Filter by difficulty level
   - Filter by tags

4. **Pattern Suggestions:**
   - "Common Combinations" section
   - Show related directives that work well together
   - One-click insert for patterns

---

## Example Use Cases

### Use Case 1: Beginner Learning Path

User selects "Beginner" difficulty filter:
- Shows only Tier 1 directives
- Displays simple examples
- Highlights best practices
- Shows step-by-step guidance

### Use Case 2: Format-Specific Help

User working with XSD:
- Filter shows XSD-compatible directives
- Format-specific notes explain XSD behavior
- Examples show XSD-specific patterns
- Related XSD directives suggested

### Use Case 3: Problem-Solving

User encounters validation error:
- Search for error-related directives
- See common pitfalls section
- Check anti-patterns
- Review correct examples

---

## Migration Strategy

### Backward Compatibility

All new fields have defaults:
```kotlin
val longDescription: String = "",  // Empty by default
val completeExamples: List<CompleteExample> = emptyList()  // Empty list
val bestPractices: List<String> = emptyList()  // Empty list
```

Existing code continues to work without changes.

### Gradual Enhancement

1. **Week 1-2:** Core structure, Tier 1 directives only
2. **Week 3-4:** Tier 2 common directives
3. **Week 5-6:** Tier 3 format-specific directives
4. **Week 7+:** Community contributions, ongoing refinement

---

## Benefits

### For Users
✅ **Learn faster** - Comprehensive guidance in one place
✅ **Avoid mistakes** - See pitfalls and anti-patterns
✅ **Complete examples** - Full schema context embedded in help
✅ **Context-aware** - Format-specific help when needed
✅ **Copy-paste ready** - Examples ready to use immediately

### For Product
✅ **Reduced support burden** - Self-service learning
✅ **Better onboarding** - Smoother learning curve
✅ **Quality schemas** - Users follow best practices
✅ **Self-contained** - No external file dependencies

### For Developers
✅ **Single source of truth** - One place to maintain
✅ **REST API ready** - All data JSON-serializable
✅ **Extensible** - Easy to add new fields
✅ **Testable** - Clear data structure

---

## Next Steps

1. **Approve approach** - Option 1 (Extended DirectiveInfo) vs Option 2 (Companion object)
2. **Start with Tier 1** - Implement for 9 core directives first
3. **Validate with users** - Test enhanced help with real users
4. **Scale to all tiers** - Expand to all 119 directives
5. **Enable contributions** - Create process for community examples

---

## Open Questions

1. Should categories be predefined enum or freeform tags?
2. How to version guidance data separately from directive definitions?
3. Should we support multiple languages for descriptions/guidance?
4. How many complete examples per directive? (recommended 2-3 covering different formats)
5. Should complete examples be tested/validated automatically in CI/CD?
6. How to keep examples synchronized when directive behavior changes?
