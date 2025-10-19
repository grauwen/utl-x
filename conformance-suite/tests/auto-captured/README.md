# Auto-Captured Tests

This directory contains automatically generated conformance tests captured during CLI usage.

## What Are Auto-Captured Tests?

Every time you run a UTL-X transformation via the CLI, the system automatically:
1. Captures the transformation, input, and output
2. Generates a conformance test YAML file
3. Categorizes it by function (e.g., `upper` → `stdlib/string/`)
4. Deduplicates to prevent redundant tests

## Directory Structure

```
auto-captured/
├── stdlib/
│   ├── string/        # String function tests
│   ├── array/         # Array function tests
│   ├── math/          # Math function tests
│   └── ...
└── auto-captured/
    ├── json-transform/  # JSON transformations
    ├── xml-to-json/     # Format conversions
    └── uncategorized/   # Uncategorized tests
```

## Test Format

Auto-captured tests have this structure:

```yaml
name: "function_auto_<hash>"
category: "stdlib/category"
description: "Auto-captured test for function"
tags: ["auto-generated", "function"]
auto_generated: true      # Marks as auto-generated
captured_at: "..."        # Timestamp

input: ...
transformation: ...
expected: ...            # For passing tests

# OR

known_issue:             # For failing tests
  status: "failing"
  captured_output: "..."
  issue_description: "..."
```

## How It Works

```bash
# Just use the CLI normally
echo '"hello"' | utlx transform my_script.utlx

# Output:
"HELLO"
  ✓ Test captured: stdlib/string/upper_auto_abc123.yaml (passing)
```

## Features

- **Automatic**: No manual test writing
- **Deduplication**: Same transformation = one test
- **Categorization**: Tests filed by function
- **Known Issues**: Failing tests captured for debugging
- **Growing Coverage**: Test suite grows with usage

## Running These Tests

```bash
# Run all auto-captured tests
cd conformance-suite
python3 runners/cli-runner/simple-runner.py auto-captured/

# Run specific category
python3 runners/cli-runner/simple-runner.py auto-captured/stdlib/string

# Run specific test
python3 runners/cli-runner/simple-runner.py auto-captured/stdlib/string upper_auto_abc123
```

## Configuration

Capture settings in `~/.utlx/capture-config.yaml`:

```yaml
enabled: true
capture_location: "conformance-suite/tests/auto-captured/"
deduplicate: true
capture_failures: true
max_tests_per_function: 50
```

## Statistics

```bash
# Count auto-captured tests
find . -name "*.yaml" | wc -l

# Count by category
find . -name "*.yaml" -exec dirname {} \; | sort | uniq -c

# Find known issues
grep -r "known_issue:" .
```

## Maintenance

### Promoting Tests

Good auto-captured tests can be promoted to the official test suite:

1. Review the test quality
2. Add better description and tags
3. Move to appropriate `stdlib/` directory
4. Remove `auto_generated: true` marker

### Cleaning Up

```bash
# Remove redundant tests
find . -name "*_auto_*" -type f

# Remove known issues after fixing bugs
grep -r "known_issue:" . | cut -d: -f1
```

## Learn More

See full documentation: `docs/test-capture-system.md`

---

**These tests are automatically generated. Quality may vary. Review before promoting to official test suite.**
