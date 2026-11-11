# Enhanced UTL-X Function Annotations (v2)

## Overview

The `@UTLXFunction` annotation provides comprehensive metadata extracted from KDoc comments for function documentation, IDE tooling, and automated registry generation.

## Annotation Fields

### Core Fields (Required)
- `description: String` - Concise description of what the function does (from first line of KDoc)
- `category: String` - Function category for grouping (Array, String, Math, XML, JSON, etc.)
- `minArgs: Int` - Minimum number of arguments
- `maxArgs: Int` - Maximum number of arguments

### Parameter Documentation
- `parameters: Array<String>` - Descriptions of each parameter in format "name: type and purpose"
  - Example: `"array: Array to search through"`
  - Example: `"predicate: Function that tests each element (element) => boolean"`
- `returns: String` - Description of return value, type, and special cases
  - Example: `"Index of first matching element, or -1 if none found"`
  - Example: `"New array with filtered elements"`

### Usage Examples (Extracted from KDoc "Usage:" lines)
- `example: String` - Primary usage example showing successful operation
  - Format: `"functionCall(args) => result"`
  - Example: `"compact([1, null, 2, '', 3]) => [1, 2, 3]"`
- `additionalExamples: Array<String>` - Additional usage examples for different scenarios
  - Example: `["findIndex([1, 2, 3, 4], n => n > 2) => 2", "findIndex([{id: 1}, {id: 2}], item => item.id == 2) => 1"]`

### Behavior Notes (Extracted from KDoc)
- `notes: String` - Important behavioral notes (from "Filters out:", "Keeps:", "Returns:", etc. sections)
  - Multi-line notes separated by newlines
  - Example for compact():
    ```
    Filters out: null values, empty strings, undefined
    Keeps: 0, false (valid values), empty arrays/objects (valid structures)
    ```

### Metadata and Navigation
- `tags: Array<String>` - Tags for search and classification
  - Derived from category, function name patterns, and behavior
  - Example: `["array", "filter", "null-handling"]`
- `seeAlso: Array<String>` - Related function names (from "Complement to:", "This complements", etc.)
  - Example: `["find", "filter", "indexOf"]`
- `since: String` - Version when function was introduced (default: "1.0")

### Deprecation (Optional)
- `deprecated: Boolean` - Whether function is deprecated (default: false)
- `deprecationMessage: String` - Deprecation message with migration path

## Complete Example

Based on the `compact` function from Critical ArrayFunctions.kt:

```kotlin
@UTLXFunction(
    description = "Remove null, undefined, and empty values from array",
    minArgs = 1,
    maxArgs = 1,
    category = "Array",
    parameters = [
        "array: Array to filter, removing null and empty values"
    ],
    returns = "New array with null values and empty strings removed",
    example = "compact([1, null, 2, '', 3, false, 4]) => [1, 2, 3, false, 4]",
    notes = """Filters out: null values, empty strings, undefined (represented as null in UDM)
Keeps: 0, false (they're valid values), empty arrays/objects (they're valid structures)""",
    tags = ["array", "filter", "null-handling", "cleanup"],
    seeAlso = ["filter", "reject"],
    since = "1.0"
)
fun compact(args: List<UDM>): UDM {
    // Implementation...
}
```

## Example with Multiple Usage Patterns

```kotlin
@UTLXFunction(
    description = "Find index of first element matching condition",
    minArgs = 2,
    maxArgs = 2,
    category = "Array",
    parameters = [
        "array: Array to search through",
        "predicate: Function that tests each element (element) => boolean"
    ],
    returns = "Index of first matching element, or -1 if no match found",
    example = "findIndex([{id: 1}, {id: 2}, {id: 3}], item => item.id == 2) => 1",
    additionalExamples = [
        "findIndex([1, 2, 3, 4], n => n > 2) => 2  // index of 3",
        "findIndex(['a', 'b', 'c'], s => s == 'x') => -1  // not found"
    ],
    notes = "This complements find() which returns the element itself rather than its index",
    tags = ["array", "search", "index", "predicate"],
    seeAlso = ["find", "indexOf", "findLastIndex"],
    since = "1.0"
)
fun findIndex(args: List<UDM>): UDM {
    // Implementation...
}
```

## Extraction Rules from KDoc

### Description
- Take the first substantive line after `/**`
- Remove any leading/trailing whitespace
- Should be a single sentence ending with what the function does

### Parameters
- Infer from function body argument extraction patterns
- Use parameter names from error messages if available
- Add type information from validation code

### Returns
- Look for `@return` tag or deduce from function behavior
- Include special return values (null, -1, empty array, etc.)
- Mention any conditions that affect the return value

### Examples (Usage lines)
- Extract all lines starting with "Usage:"
- Remove "Usage:" prefix
- Keep format: `functionCall(args) => result`
- First one becomes `example`, rest go to `additionalExamples`

### Notes
- Combine all descriptive sections like:
  - "Filters out:" → List items
  - "Keeps:" → List items
  - "Returns" (non-@return) → Special conditions
  - "Complement to:" → Related function reference
  - Any bullet points or explanatory paragraphs

### See Also
- Extract from "Complement to:", "This complements", "See also" references
- Parse inline mentions of other functions
- Add functions that are conceptually related

### Tags
- Auto-generate from:
  - Category name (lowercase)
  - Function name patterns (e.g., "find" → add "search")
  - Behavioral keywords from notes (e.g., "null" mention → add "null-handling")

## Benefits Over v1

### Removed (Low Value)
- ❌ `performance: String` - Not critical for transformation language; complexity often obvious
- ❌ `threadSafety: String` - UTL transformations are single-threaded by design
- ❌ `negativeExample` - Error cases documented in `notes` instead

### Added (High Value)
- ✅ `notes: String` - Captures rich behavioral documentation from KDoc
- ✅ Better `example` format - Direct extraction from "Usage:" lines
- ✅ Smarter `seeAlso` - Extracted from KDoc relationships

### Improved
- Parameters now include actual param names from code
- Returns includes special cases (-1, null, etc.)
- Tags auto-generated with intelligence
- All extracted automatically from existing KDoc

## Migration from v1

```kotlin
// v1 - Manual fields
@UTLXFunction(
    performance = "O(n) time complexity",
    threadSafety = "Thread-safe - pure function",
    negativeExample = "compact(null) => throws IllegalArgumentException"
)

// v2 - Focus on behavior
@UTLXFunction(
    notes = "Returns -1 if no match found. Throws IllegalArgumentException if array is null."
)
```

## Best Practices

### Writing KDoc for Extraction

```kotlin
/**
 * One-line description of what function does
 *
 * Usage: simpleCall(arg) => result
 * Usage: complexCall(arg1, arg2) => differentResult
 *
 * Additional behavioral notes:
 * - Bullet points about edge cases
 * - Special return values
 * - Important caveats
 *
 * Filters out: (if applicable)
 * - Items that are removed
 *
 * Keeps: (if applicable)
 * - Items that are preserved
 *
 * This complements otherFunction() which does related operation
 */
```

### Description Guidelines
- Start with verb: "Remove", "Find", "Filter", "Convert", etc.
- Be specific about what it operates on
- Keep to one sentence
- Example: "Remove null, undefined, and empty values from array"

### Parameter Names
- Use descriptive names that indicate purpose
- Include type hint: "array:", "predicate:", "index:", etc.
- Example: `"array: Array to search through"`

### Returns
- Include type of return value
- Mention special cases (null, -1, empty, etc.)
- Example: "Index of first match, or -1 if not found"

### Usage Examples
- Show realistic use cases
- Include result after `=>`
- Add inline comments for clarity
- Example: `"findIndex([1, 2, 3], n => n > 2) => 2  // index of 3"`

### Notes
- Capture "why" not just "what"
- Explain edge cases and special behaviors
- Reference related functions
- Use bullet points for lists

## Generated Documentation

The enhanced annotations automatically generate:

1. **Function Registry**: JSON/YAML with all metadata
2. **IDE Tooltips**: Rich parameter hints with examples
3. **CLI Help**: Comprehensive command-line documentation
4. **Web Documentation**: Auto-generated API reference
5. **Code Completion**: Smart suggestions with usage examples

## Conclusion

Version 2 focuses on extracting rich, user-facing documentation from existing KDoc comments, removing low-value metadata (performance, thread safety), and automating the annotation process based on actual code patterns.
