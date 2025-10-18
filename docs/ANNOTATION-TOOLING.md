# UTL-X Function Annotation Tooling

## Current Scripts (v3)

### Active Scripts
All annotation work should use the v3 scripts:

- **`scripts/enhance-annotations-v3.py`** - Main annotation generation script
  - Extracts comprehensive metadata from KDoc comments
  - Generates @UTLXFunction annotations with proper formatting
  - Handles code blocks, multi-line notes, and spacing correctly
  - Usage: `python3 scripts/enhance-annotations-v3.py <file.kt>`

- **`scripts/batch-enhance-all-v3.sh`** - Batch processor for all stdlib files
  - Processes all *Functions.kt files in stdlib
  - Reports progress and summary
  - Usage: `./scripts/batch-enhance-all-v3.sh`

- **`scripts/generate-function-docs.py`** - Documentation generator
  - Generates documentation from function registry
  - Creates multiple output formats
  - Usage: `python3 scripts/generate-function-docs.py`

### Documentation
- **`docs/Enhanced-Function-Annotations-v2.md`** - Current specification
  - Describes all annotation fields
  - Explains metadata extraction from KDoc
  - Shows examples and best practices

### Obsolete Files (Removed)
The following files have been removed as they are superseded by v3:
- `add-function-annotations.py` (removed)
- `add-missing-annotations.py` (removed)
- `enhance-annotations-v2.py` (removed)
- `enhance-function-annotations.py` (removed)
- `batch-enhance-all.sh` (removed)
- `docs/Enhanced-Function-Annotations.md` (renamed to v1-OBSOLETE)

## Quick Start

### Annotate a Single File
```bash
python3 scripts/enhance-annotations-v3.py stdlib/src/main/kotlin/org/apache/utlx/stdlib/array/ArrayFunctions.kt
```

### Annotate All Files
```bash
./scripts/batch-enhance-all-v3.sh
```

### Verify Compilation
```bash
./gradlew :stdlib:compileKotlin
```

## Annotation Format

All annotations follow this format:

```kotlin
@UTLXFunction(
    description = "Brief description from KDoc",
    minArgs = 1,
    maxArgs = 2,
    category = "Array",
    parameters = [
        "array: Description of parameter",
        "predicate: Function to test (element) => boolean"
    ],
    returns = "Description of return value",
    example = "functionName([1, 2, 3]) => result",
    additionalExamples = [
        "functionName([]) => []"
    ],
    notes = "Important behavioral notes\nExtracted from KDoc sections",
    tags = ["array", "search", "filter"],
    seeAlso = ["relatedFunction", "anotherFunction"],
    since = "1.0"
)
/**
 * Full KDoc comment
 */
fun functionName(args: List<UDM>): UDM {
    // Implementation
}
```

## Key Features

### Spacing Rules
- Blank line BEFORE annotation (separates from previous code)
- NO blank line AFTER annotation (annotation belongs to function below)

### Code Block Formatting
- Code blocks with triple backticks (```) are escaped as single-line strings
- Format: `"Example:\n```\ncode here\n```"`
- Avoids visual confusion between Markdown and Kotlin triple quotes

### Tag Generation
Tags are auto-generated from:
- Category (always included as lowercase)
- Function name patterns (find→search, map→transform, etc.)
- KDoc content (null handling, cleanup, etc.)

## Status

✅ All 55 stdlib files annotated (600+ functions)
✅ All annotations compile successfully
✅ Clean formatting with proper spacing
✅ Code blocks handled correctly
