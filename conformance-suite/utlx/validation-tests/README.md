# UTL-X Validation Test Suite

This directory contains tests for the `utlx validate` command, which performs Levels 1-3 validation:

- **Level 1**: Syntactic validation (lexer + parser errors)
- **Level 2**: Semantic validation (type errors, undefined variables, scope violations)
- **Level 3**: Schema validation (input/output schema conformance)

## Directory Structure

```
validation-tests/
├── level1-syntax/      # Syntax/parse errors (missing commas, braces, invalid tokens)
├── level2-semantic/    # Semantic errors (type mismatches, undefined variables)
├── level3-schema/      # Schema validation errors (invalid paths, type incompatibilities)
└── valid/              # Scripts that should pass all validation levels
```

## YAML Test Schema

### Basic Structure

```yaml
name: "test_name"                    # Unique test identifier
category: "level1-syntax"            # Test category (must match directory)
description: "Human-readable description of what this test validates"
tags: ["syntax", "parse-error"]      # Tags for filtering/organization

# The UTL-X script to validate
script: |
  %utlx 1.0
  input json
  output json
  ---
  {
    name: input.customerName
    email: input.emailAddress  # Missing comma - should cause parse error
  }

# Optional: Input schema for Level 3 validation tests
input_schema:
  format: "json-schema"  # or "xsd", "avro", etc.
  schema: |
    {
      "type": "object",
      "properties": {
        "customerName": {"type": "string"},
        "emailAddress": {"type": "string"}
      }
    }

# Optional: Output schema for Level 3 validation tests
output_schema:
  format: "json-schema"
  schema: |
    {
      "type": "object",
      "properties": {
        "name": {"type": "string"},
        "email": {"type": "string"}
      }
    }

# Expected validation results
validation_expected:
  should_pass: false           # Should validation succeed? (true/false)
  exit_code: 1                 # Expected exit code (0 for pass, 1 for fail)

  # Expected errors (can be empty array if should_pass: true)
  errors:
    - level: "syntax"          # "syntax", "semantic", or "schema"
      type: "parse_error"      # Error type identifier
      message_pattern: ".*expected.*comma.*"  # Regex pattern for error message
      line: 6                  # Optional: expected line number
      column: 33               # Optional: expected column number
      severity: "error"        # "error" or "warning"

  # Expected warnings (usually empty for validate command)
  warnings: []

# Optional: Test variants with different flags
variants:
  - name: "with_verbose"
    description: "Run with --verbose flag"
    flags: ["--verbose"]
    validation_expected:
      should_pass: false
      exit_code: 1
      errors:
        - level: "syntax"
          type: "parse_error"
          message_pattern: ".*expected.*comma.*"
          # Verbose mode might show additional details

  - name: "with_no_typecheck"
    description: "Run with --no-typecheck flag"
    flags: ["--no-typecheck"]
    validation_expected:
      should_pass: false  # Syntax errors still fail even with --no-typecheck
      exit_code: 1
      errors:
        - level: "syntax"
          type: "parse_error"
          message_pattern: ".*expected.*comma.*"

# Optional: Performance constraints
performance_limits:
  max_duration_ms: 100
  max_memory_mb: 10

# Optional: Metadata
metadata:
  author: "UTL-X Team"
  created: "2025-11-01"
  references:
    - "validation-and-analysis-study.md"
    - "UTL-X Error Handling Specification"
```

## Test Categories

### Level 1: Syntax Errors (level1-syntax/)

Tests that should fail at the parsing stage due to grammar violations.

**Examples:**
- Missing commas in object literals
- Missing braces or parentheses
- Invalid tokens
- Unterminated strings
- Malformed expressions

**Expected behavior:**
- `should_pass: false`
- `exit_code: 1`
- `errors[].level: "syntax"`

### Level 2: Semantic Errors (level2-semantic/)

Tests that parse successfully but have semantic errors (type mismatches, undefined variables, etc.).

**Examples:**
- Undefined variables
- Type mismatches (e.g., string + number)
- Invalid function calls
- Scope violations
- Duplicate variable definitions

**Expected behavior:**
- `should_pass: false`
- `exit_code: 1`
- `errors[].level: "semantic"`

### Level 3: Schema Errors (level3-schema/)

Tests that have valid syntax and semantics but violate input/output schema constraints.

**Examples:**
- Accessing non-existent input fields
- Type incompatibilities with schema
- Missing required output fields
- Invalid data transformations

**Expected behavior:**
- `should_pass: false`
- `exit_code: 1`
- `errors[].level: "schema"`
- Must include `input_schema` and/or `output_schema`

### Valid Scripts (valid/)

Scripts that should pass all validation levels.

**Expected behavior:**
- `should_pass: true`
- `exit_code: 0`
- `errors: []`
- `warnings: []`

## Error Level Reference

| Level | Type | Description |
|-------|------|-------------|
| syntax | lexical_error | Invalid character sequences |
| syntax | parse_error | Grammar violations |
| syntax | malformed_expression | Invalid expression structure |
| semantic | type_error | Type mismatch in operation |
| semantic | undefined_variable | Variable used before definition |
| semantic | undefined_function | Function call to non-existent function |
| semantic | scope_violation | Variable accessed outside scope |
| semantic | duplicate_definition | Variable/function defined multiple times |
| semantic | arity_mismatch | Wrong number of function arguments |
| schema | invalid_path | Accessing non-existent field in schema |
| schema | type_incompatibility | Type doesn't match schema definition |
| schema | missing_required | Required field not present in output |
| schema | schema_violation | General schema constraint violation |

## Running Validation Tests

### Run all validation tests:
```bash
python3 runners/validation-runner.py validation-tests
```

### Run specific category:
```bash
python3 runners/validation-runner.py validation-tests/level1-syntax
```

### Run single test:
```bash
python3 runners/validation-runner.py validation-tests/level1-syntax/missing_comma.yaml
```

### Run with verbose output:
```bash
python3 runners/validation-runner.py validation-tests --verbose
```

## Writing New Tests

### Guidelines

1. **One error per test** (usually): Focus each test on validating detection of a specific error
2. **Clear naming**: Use descriptive names like `undefined_variable_in_let.yaml`
3. **Minimal scripts**: Keep test scripts as small as possible to isolate the error
4. **Use variants** for testing different flags on the same script
5. **Add metadata**: Include author, date, and references for complex tests
6. **Use regex patterns**: For `message_pattern`, use flexible patterns that work across implementations

### Example: Creating a New Syntax Error Test

```yaml
name: "unterminated_string_literal"
category: "level1-syntax"
description: "Test detection of unterminated string in object literal"
tags: ["syntax", "string", "lexical-error"]

script: |
  %utlx 1.0
  input json
  output json
  ---
  {
    name: "John Doe
  }

validation_expected:
  should_pass: false
  exit_code: 1
  errors:
    - level: "syntax"
      type: "lexical_error"
      message_pattern: ".*unterminated.*string.*"
      line: 6

metadata:
  author: "Your Name"
  created: "2025-11-01"
```

## Integration with CI/CD

Validation tests are separate from transform tests and should be run independently:

```bash
# Run transform tests (existing)
cd conformance-suite
python3 utlx/runners/cli-runner/simple-runner.py

# Run validation tests (new)
python3 utlx/runners/validation-runner.py validation-tests

# Run all tests
./run-all-tests.sh
```

## See Also

- [Lint Test Suite README](../lint-tests/README.md)
- [Transform Test Suite README](../tests/README.md)
- [Validation & Analysis Study](../../../docs/architecture/validation-and-analysis-study.md)
