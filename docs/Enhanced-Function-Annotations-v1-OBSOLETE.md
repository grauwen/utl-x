# Enhanced UTL-X Function Annotations

## Overview

The `@UTLXFunction` annotation has been significantly enhanced to provide comprehensive metadata for function documentation, IDE tooling, and automated registry generation.

## New Annotation Fields

### Core Fields
- `description: String` - Human-readable description of what the function does
- `category: String` - Function category for grouping (Array, String, Math, etc.)
- `minArgs: Int` - Minimum number of arguments
- `maxArgs: Int` - Maximum number of arguments

### Parameter Documentation
- `parameters: Array<String>` - Descriptions of each parameter in order
- `returns: String` - Description of the return value and its type

### Examples and Testing
- `example: String` - Primary positive example showing successful usage
- `additionalExamples: Array<String>` - Additional positive examples for complex functions
- `negativeExample: String` - Example showing what happens with invalid input
- `additionalNegativeExamples: Array<String>` - Additional negative examples for edge cases

### Metadata and References
- `tags: Array<String>` - Tags for additional classification
- `seeAlso: Array<String>` - References to related functions
- `since: String` - Version when function was introduced

### Performance and Safety
- `performance: String` - Performance characteristics or complexity notes
- `threadSafety: String` - Notes about thread safety

### Deprecation
- `deprecated: Boolean` - Whether function is deprecated
- `deprecationMessage: String` - Deprecation message if deprecated

## Complete Example

```kotlin
@UTLXFunction(
    description = "Find index of first element matching condition",
    minArgs = 2,
    maxArgs = 2,
    category = "Array",
    parameters = [
        "array: The input array to search",
        "predicate: Function that tests each element (element) => boolean"
    ],
    returns = "Index of first matching element, or null if none found",
    example = "findIndex([1, 2, 3, 4], x => x > 2) => 2",
    additionalExamples = [
        "findIndex([\"a\", \"b\", \"c\"], x => x == \"b\") => 1",
        "findIndex([1, 2, 3], x => x > 10) => null"
    ],
    negativeExample = "findIndex(null, x => true) => throws FunctionArgumentException",
    additionalNegativeExamples = [
        "findIndex([1, 2, 3], \"not a function\") => throws FunctionArgumentException"
    ],
    tags = ["functional", "search", "index"],
    performance = "O(n) time complexity, stops at first match",
    threadSafety = "Thread-safe - read-only operation",
    seeAlso = ["find", "indexOf", "filter", "some"],
    since = "1.0"
)
fun findIndex(args: List<UDM>): UDM {
    // Implementation...
}
```

## Benefits

### For Developers
- **Comprehensive Documentation**: All function details in one place
- **IDE Integration**: Rich tooltips and parameter hints
- **Error Prevention**: Clear examples of what not to do

### For Tools
- **Automated Documentation**: Generate comprehensive docs from annotations
- **Function Discovery**: Rich metadata for function browsers
- **Validation**: Verify function usage against documented parameters

### For Users
- **Learning**: Clear examples and counter-examples
- **Performance Awareness**: Understanding of complexity and thread safety
- **Navigation**: Easy discovery of related functions

## Migration Guide

### From Basic Annotation
```kotlin
// OLD - Basic annotation
@UTLXFunction(
    description = "Filter array elements",
    minArgs = 2,
    maxArgs = 2,
    category = "Array"
)

// NEW - Enhanced annotation
@UTLXFunction(
    description = "Filter array elements that match a condition",
    minArgs = 2,
    maxArgs = 2,
    category = "Array",
    parameters = [
        "array: The input array to filter",
        "predicate: Function that tests each element"
    ],
    returns = "New array containing only matching elements",
    example = "filter([1, 2, 3, 4], x => x > 2) => [3, 4]",
    negativeExample = "filter(null, x => true) => throws FunctionArgumentException",
    tags = ["functional", "filter", "predicate"],
    performance = "O(n) time complexity",
    threadSafety = "Thread-safe - creates new array",
    seeAlso = ["map", "reduce", "find"],
    since = "1.0"
)
```

### Gradual Enhancement
You can enhance annotations incrementally:

1. **Start with core fields**: description, parameters, returns
2. **Add examples**: positive and negative examples
3. **Include metadata**: performance, thread safety, related functions
4. **Full enhancement**: all fields for comprehensive documentation

## Best Practices

### Writing Descriptions
- Be concise but complete
- Focus on what the function does, not how
- Use active voice: "Filters array elements" vs "Array elements are filtered"

### Parameter Descriptions
- Include type information: "array: The input array to filter"
- Describe purpose: "predicate: Function that tests each element"
- Use consistent format: "name: description"

### Examples
- Use realistic, practical examples
- Show both simple and complex use cases
- Include edge cases in additional examples

### Negative Examples
- Show common mistakes
- Include type errors and edge cases
- Use "throws ExceptionType" format

### Performance Notes
- Include Big O notation when relevant
- Mention memory usage if significant
- Note any lazy evaluation behavior

### Thread Safety
- Be explicit: "Thread-safe" or "Not thread-safe"
- Explain why if not obvious
- Suggest alternatives for concurrent use

## Generated Documentation

The enhanced annotations automatically generate:

1. **Function Registry**: JSON/YAML with all metadata
2. **IDE Snippets**: VS Code snippets with parameter hints
3. **CLI Documentation**: Rich command-line help
4. **Web Documentation**: Comprehensive online docs
5. **API References**: Machine-readable function specifications

## Conclusion

The enhanced `@UTLXFunction` annotation transforms function documentation from basic descriptions to comprehensive, machine-readable specifications that benefit developers, tools, and end users.