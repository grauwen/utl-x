# UTL-X Lint Test Suite

This directory contains tests for the `utlx lint` command, which performs **Level 4 validation** (logical/style analysis):

- Detects **unused variables** and **dead code**
- Identifies **complexity issues** (deep nesting, long expressions)
- Enforces **style conventions** (naming, formatting)
- Suggests **best practices** and improvements

**Important**: The lint command **always exits with code 0** (warnings only, never fails builds).

## Directory Structure

```
lint-tests/
├── unused-variables/   # Unused variable warnings
├── dead-code/          # Unreachable code warnings
├── complexity/         # Complexity warnings (nesting depth, expression length)
├── style/              # Style violations (naming conventions, formatting)
└── clean/              # Code that should produce no warnings
```

## YAML Test Schema

### Basic Structure

```yaml
name: "test_name"                    # Unique test identifier
category: "unused-variables"         # Test category (must match directory)
description: "Human-readable description of what this test lints"
tags: ["lint", "unused", "variables"] # Tags for filtering/organization

# The UTL-X script to lint
script: |
  %utlx 1.0
  input json
  output json
  ---
  let unused_variable = 42
  let result = {
    name: input.customerName
  }
  $result

# Expected lint results
lint_expected:
  should_pass: true            # Lint ALWAYS passes (exit code 0)
  exit_code: 0                 # Always 0 for lint

  # Lint never produces errors (only warnings)
  errors: []

  # Expected warnings
  warnings:
    - category: "unused-variable"  # Warning category
      message_pattern: ".*unused.*'unused_variable'.*"  # Regex for message
      line: 5                      # Optional: expected line number
      column: 7                    # Optional: expected column number
      severity: "warning"          # Always "warning" for lint
      suggestion: "Remove unused variable 'unused_variable'"  # Optional

# Optional: Test variants with different flags
variants:
  - name: "with_no_unused_flag"
    description: "Test --no-unused flag suppresses this warning"
    flags: ["--no-unused"]
    lint_expected:
      should_pass: true
      exit_code: 0
      warnings: []  # Should NOT report unused variable warning

  - name: "with_verbose"
    description: "Test --verbose shows more details"
    flags: ["--verbose"]
    lint_expected:
      should_pass: true
      exit_code: 0
      warnings:
        - category: "unused-variable"
          message_pattern: ".*unused.*'unused_variable'.*"
          # Verbose might show additional context

  - name: "json_format"
    description: "Test --format json output"
    flags: ["--format", "json"]
    lint_expected:
      should_pass: true
      exit_code: 0
      output_format: "json"
      warnings:
        - category: "unused-variable"
          message_pattern: ".*unused.*'unused_variable'.*"

# Optional: Performance constraints
performance_limits:
  max_duration_ms: 200
  max_memory_mb: 15

# Optional: Metadata
metadata:
  author: "UTL-X Team"
  created: "2025-11-01"
  references:
    - "validation-and-analysis-study.md#level-4-lint"
```

## Test Categories

### Unused Variables (unused-variables/)

Tests detection of variables that are defined but never used.

**Examples:**
- Simple unused `let` bindings
- Unused function parameters
- Unused intermediate results
- Variables shadowed without use

**Expected warnings:**
- `category: "unused-variable"`
- Should suggest removal or mark with `_` prefix

### Dead Code (dead-code/)

Tests detection of code that can never be executed.

**Examples:**
- Code after `return` statements
- Unreachable branches in conditionals
- Always-false conditions
- Code in disabled features

**Expected warnings:**
- `category: "dead-code"` or `"unreachable-code"`
- Should suggest removal

### Complexity (complexity/)

Tests detection of overly complex expressions.

**Examples:**
- Deep nesting (>5 levels)
- Long expressions (>100 characters)
- Many conditional branches
- Complex boolean logic

**Expected warnings:**
- `category: "complexity"` or `"excessive-nesting"`
- Should suggest refactoring

### Style (style/)

Tests enforcement of style conventions.

**Examples:**
- Non-camelCase variable names (e.g., `MyPascalCase`, `snake_case`)
- Inconsistent formatting
- Missing documentation
- Poor naming choices

**Expected warnings:**
- `category: "style"` or `"naming-convention"`
- Should suggest style fix

### Clean Code (clean/)

Code that follows all best practices and should produce no warnings.

**Expected behavior:**
- `warnings: []`
- All flags should still produce no warnings

## Warning Category Reference

| Category | Description | Severity | Can Suppress |
|----------|-------------|----------|--------------|
| unused-variable | Variable defined but never used | warning | --no-unused |
| unused-function | Function defined but never called | warning | --no-unused |
| dead-code | Code that can never be executed | warning | --no-dead-code |
| unreachable-code | Code after return/throw | warning | --no-dead-code |
| excessive-nesting | Nesting depth > 5 levels | warning | --no-complexity |
| complex-expression | Expression too long/complex | warning | --no-complexity |
| naming-convention | Variable/function name style violation | warning | --no-style |
| missing-doc | Missing documentation comment | info | --no-style |
| inconsistent-format | Inconsistent code formatting | info | --no-style |

## Running Lint Tests

### Run all lint tests:
```bash
python3 runners/validation-runner.py lint-tests
```

### Run specific category:
```bash
python3 runners/validation-runner.py lint-tests/unused-variables
```

### Run single test:
```bash
python3 runners/validation-runner.py lint-tests/unused-variables/simple_unused.yaml
```

### Run with verbose output:
```bash
python3 runners/validation-runner.py lint-tests --verbose
```

## Writing New Tests

### Guidelines

1. **Focus on one warning type**: Each test should demonstrate a specific lint issue
2. **Test suppression flags**: Use variants to verify `--no-*` flags work correctly
3. **Include clean variants**: Show how to fix the warning
4. **Use realistic code**: Lint tests should look like real-world mistakes
5. **Test output formats**: Add variants for `--format json` and `--format compact`

### Example: Creating a New Lint Test

```yaml
name: "unused_let_binding_simple"
category: "unused-variables"
description: "Test detection of simple unused let binding"
tags: ["lint", "unused", "let-binding"]

script: |
  %utlx 1.0
  input json
  output json
  ---
  let computedValue = input.price * 1.2  # Unused!
  {
    name: input.productName
  }

lint_expected:
  should_pass: true  # Lint never fails
  exit_code: 0
  errors: []
  warnings:
    - category: "unused-variable"
      message_pattern: ".*unused.*'computedValue'.*"
      line: 5
      suggestion: "Remove unused variable or prefix with '_'"

variants:
  - name: "suppressed_with_flag"
    flags: ["--no-unused"]
    lint_expected:
      warnings: []

  - name: "fixed_version"
    description: "Code with warning fixed"
    script: |
      %utlx 1.0
      input json
      output json
      ---
      let computedValue = input.price * 1.2
      {
        name: input.productName,
        price: computedValue  # Now used!
      }
    lint_expected:
      warnings: []

metadata:
  author: "Your Name"
  created: "2025-11-01"
```

## Lint vs Validate

**Key differences:**

| Aspect | Validate | Lint |
|--------|----------|------|
| **Purpose** | Correctness checking | Best practices & style |
| **Exit code** | 0 (pass) or 1 (fail) | Always 0 (never fails) |
| **Output** | Errors that prevent execution | Warnings & suggestions |
| **Levels** | Levels 1-3 (syntax, semantics, schema) | Level 4 (logical/style) |
| **CI/CD** | Should fail builds on errors | Should not fail builds |
| **Flags** | `--strict`, `--no-typecheck` | `--no-unused`, `--no-style`, etc. |

## Integration with CI/CD

Lint tests run independently and never fail:

```bash
# Validation must pass (exit 1 on failure)
python3 utlx/runners/validation-runner.py validation-tests || exit 1

# Lint produces warnings but doesn't fail build (exit 0 always)
python3 utlx/runners/validation-runner.py lint-tests

# Run all tests
./run-all-tests.sh
```

## Suggested Workflow

```bash
# 1. Run transform tests (must pass)
python3 utlx/runners/cli-runner/simple-runner.py

# 2. Run validation tests (must pass)
python3 utlx/runners/validation-runner.py validation-tests

# 3. Run lint tests (shows warnings, doesn't fail)
python3 utlx/runners/validation-runner.py lint-tests

# 4. Check lint warnings in CI/CD report
#    (warnings visible but don't block deployment)
```

## See Also

- [Validation Test Suite README](../validation-tests/README.md)
- [Transform Test Suite README](../tests/README.md)
- [Validation & Analysis Study](../../../docs/architecture/validation-and-analysis-study.md#level-4-logical-validation-lint)
