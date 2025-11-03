# UTL-X Conformance Test Suites

UTL-X maintains four distinct conformance test suites to ensure correctness, quality, and standards compliance across different aspects of the language implementation.

## Overview

| Suite | Purpose | Test Count | Runners | Status |
|-------|---------|------------|---------|--------|
| **Runtime/Transform Conformance** | Language runtime, transformations, stdlib | 465 tests | Python (CLI), Kotlin | ✅ 100% |
| **Validation Conformance** | `utlx validate` command (3 levels) | TBD | Python | 🚧 In Development |
| **Lint Conformance** | `utlx lint` command (code quality) | TBD | Python | 🚧 In Development |
| **LSP Conformance** | Language Server Protocol daemon | TBD | Kotlin | ✅ Active |

---

## 1. Runtime/Transform Conformance Suite

**Primary conformance suite for UTL-X language runtime and transformations.**

### Location
```
conformance-suite/utlx/tests/
```

### What It Tests
- Core language syntax and operators
- Array, string, math, object operations
- Format parsing (Avro, JSON Schema, Protobuf, XML, XSD, CSV, YAML)
- Standard library functions (635 functions across 16 categories)
- Multi-input scenarios
- Regional formatting
- Schema generation
- Integration scenarios
- Edge cases (division by zero, array bounds, etc.)

### Test Categories
```
tests/
├── core/                    # Core language features
│   ├── operators/           # Arithmetic, logical, comparison
│   ├── object/              # Object construction
│   └── syntax/              # Basic literals, input binding
├── formats/                 # Format parsing & serialization
│   ├── avro/               # Apache Avro
│   ├── jsch/               # JSON Schema
│   ├── protobuf/           # Protocol Buffers
│   ├── xml/                # XML & XPath
│   ├── xsd/                # XML Schema
│   ├── csv/                # CSV with regional formats
│   └── yaml/               # YAML
├── stdlib/                  # Standard library functions
│   ├── array/              # Array operations
│   ├── string/             # String manipulation
│   ├── math/               # Mathematical functions
│   ├── date/               # Date/time operations
│   ├── encoding/           # Base64, URL encoding
│   ├── serialization/      # JSON/YAML/CSV parsing
│   └── ...                 # 16 categories total
├── integration/            # Real-world scenarios
├── multi-input/            # Multiple input sources
├── schema-generation/      # Schema inference
├── edge-cases/             # Error handling
├── performance/            # Performance benchmarks
├── auto-captured/          # Auto-generated from usage
└── tutorial-examples/      # Documentation examples
```

### Runners

#### Python CLI Runner (Primary)
```bash
# Run all tests
cd conformance-suite/utlx
python3 runners/cli-runner/simple-runner.py

# Run specific category
python3 runners/cli-runner/simple-runner.py tests/core/

# Run specific test
python3 runners/cli-runner/simple-runner.py tests/core arithmetic_basic
```

#### Kotlin Runner (Secondary)
```bash
cd conformance-suite/utlx
./runners/kotlin-runner/run-tests.sh

# Run specific test
./runners/kotlin-runner/run-tests.sh core/operators arithmetic_basic
```

### Test Results
```
==================================================
Results: 465/465 tests passed
Success rate: 100.0%
✅ All tests passed!
```

---

## 2. Validation Conformance Suite

**Tests the `utlx validate` command across three validation levels.**

### Location
```
conformance-suite/utlx/validation-tests/
```

### What It Tests
- **Level 1**: Syntactic validation (parse errors, missing separator, malformed headers)
- **Level 2**: Semantic validation (type errors, undefined variables, function signatures)
- **Level 3**: Schema validation (input schema compliance)

### Test Structure
```
validation-tests/
├── level1-syntax/          # Parse errors, syntax issues
│   ├── missing_comma_in_object.yaml
│   ├── missing_separator.yaml
│   └── invalid_header.yaml
├── level2-semantic/        # Type errors, undefined references
│   ├── undefined_variable.yaml
│   ├── type_mismatch.yaml
│   └── invalid_function.yaml
├── level3-schema/          # Schema validation
│   └── schema_mismatch.yaml
└── valid/                  # Valid scripts (should pass)
    └── valid_script.yaml
```

### Runner

#### Python Validation Runner
```bash
cd conformance-suite/utlx
python3 runners/validation-runner.py validation-tests

# Run specific level
python3 runners/validation-runner.py validation-tests/level1-syntax
```

### Command Being Tested
```bash
utlx validate <script-file> [--schema <schema-file>] [--strict] [--verbose]
```

---

## 3. Lint Conformance Suite

**Tests the `utlx lint` command for code quality and best practices.**

### Location
```
conformance-suite/utlx/lint-tests/
```

### What It Tests
- Code style violations
- Complexity warnings
- Dead code detection
- Unused variable detection
- Best practice recommendations

### Test Structure
```
lint-tests/
├── style/                  # Style violations
│   ├── inconsistent_naming.yaml
│   └── poor_formatting.yaml
├── complexity/             # Cyclomatic complexity
│   └── deeply_nested.yaml
├── dead-code/              # Unreachable code
│   └── unused_function.yaml
├── unused-variables/       # Unused bindings
│   └── unused_let.yaml
└── clean/                  # Clean code (should pass)
    └── well_written.yaml
```

### Runner

#### Python Lint Runner
```bash
cd conformance-suite/utlx
python3 runners/lint-runner.py lint-tests

# Run specific category
python3 runners/lint-runner.py lint-tests/style
```

### Command Being Tested
```bash
utlx lint <script-file> [--fix] [--severity <level>] [--verbose]
```

---

## 4. LSP Conformance Suite

**Tests the Language Server Protocol daemon for IDE integration.**

### Location
```
conformance-suite/lsp/
```

### What It Tests
- LSP protocol compliance (JSON-RPC 2.0)
- Server initialization & lifecycle
- Document synchronization
- Language features:
  - Autocomplete/completion
  - Hover information
  - Error/warning diagnostics
  - Go to definition
  - Find references
- Transport layers (STDIO, Socket)
- Multi-step workflows
- Edge cases and error handling

### Test Structure
```
lsp/
├── tests/
│   ├── protocol/           # LSP protocol compliance
│   │   ├── initialization/
│   │   ├── lifecycle/
│   │   ├── json-rpc/
│   │   └── transport/
│   ├── document-sync/      # Document synchronization
│   ├── features/           # Language features
│   │   ├── completion/
│   │   ├── hover/
│   │   └── diagnostics/
│   ├── workflows/          # Multi-step scenarios
│   └── edge-cases/         # Error handling
├── runners/
│   └── kotlin-runner/      # Kotlin-based test runner
├── fixtures/
│   ├── schemas/            # Sample type definitions
│   └── documents/          # Sample UTL-X documents
└── lib/                    # Shared test utilities
```

### Runner

#### Kotlin LSP Runner
```bash
cd conformance-suite/lsp
./runners/kotlin-runner/run-lsp-tests.sh

# Run specific category
./runners/kotlin-runner/run-lsp-tests.sh tests/features/completion
```

### Daemon Being Tested
```bash
utlxd design daemon [--stdio|--socket <port>] [--verbose]
```

**Note**: The LSP daemon is now part of the `utlxd` server executable (not `utlx` CLI).

---

## Test File Format

All conformance tests use a standardized YAML format:

```yaml
name: test_name
description: What this test validates
version: 1.0

# Test metadata
metadata:
  category: core/operators
  tags: [arithmetic, basic]

# Script to execute
script: |
  %utlx 1.0
  input json
  output json
  ---
  {
    result: $input.a + $input.b
  }

# Input data
input:
  a: 5
  b: 3

# Expected output
expected:
  result: 8

# Alternative: Expected error
expected_error:
  pattern: "Division by zero"
  code: "RUNTIME_ERROR"
```

---

## Running All Conformance Suites

To verify full conformance across all suites:

```bash
# 1. Runtime/Transform Conformance
cd conformance-suite/utlx
python3 runners/cli-runner/simple-runner.py
# Expected: 465/465 tests passed (100%)

# 2. Validation Conformance
python3 runners/validation-runner.py validation-tests
# Expected: All validation levels pass

# 3. Lint Conformance
python3 runners/lint-runner.py lint-tests
# Expected: All lint rules validated

# 4. LSP Conformance
cd ../lsp
./runners/kotlin-runner/run-lsp-tests.sh
# Expected: All LSP features working
```

---

## CI/CD Integration

All conformance suites are integrated into the CI/CD pipeline:

```yaml
# .github/workflows/conformance.yml
jobs:
  runtime-conformance:
    runs-on: ubuntu-latest
    steps:
      - name: Run Runtime Conformance
        run: |
          cd conformance-suite/utlx
          python3 runners/cli-runner/simple-runner.py

  validation-conformance:
    runs-on: ubuntu-latest
    steps:
      - name: Run Validation Conformance
        run: |
          cd conformance-suite/utlx
          python3 runners/validation-runner.py validation-tests

  lint-conformance:
    runs-on: ubuntu-latest
    steps:
      - name: Run Lint Conformance
        run: |
          cd conformance-suite/utlx
          python3 runners/lint-runner.py lint-tests

  lsp-conformance:
    runs-on: ubuntu-latest
    steps:
      - name: Run LSP Conformance
        run: |
          cd conformance-suite/lsp
          ./runners/kotlin-runner/run-lsp-tests.sh
```

---

## Naming Convention

When referring to conformance suites in documentation, issues, or discussions:

| Context | Recommended Name |
|---------|-----------------|
| General discussion | "runtime conformance" or just "conformance" |
| Validation testing | "validation conformance" |
| Lint testing | "lint conformance" |
| LSP testing | "LSP conformance" |
| All suites | "full conformance" or "all conformance suites" |

**Default**: When someone says "conformance" without a qualifier, they typically mean the **Runtime/Transform Conformance Suite** (the 465-test main suite).

---

## Contributing New Tests

See individual suite README files for contribution guidelines:
- Runtime: `conformance-suite/utlx/tests/README.md`
- Validation: `conformance-suite/utlx/validation-tests/README.md`
- Lint: `conformance-suite/utlx/lint-tests/README.md`
- LSP: `conformance-suite/lsp/README.md`

---

## Historical Context

The conformance suite structure evolved to support different testing needs:

1. **Runtime Conformance** (original): Started as the primary test suite for language features
2. **LSP Conformance** (added): Separated IDE/tooling tests from runtime tests
3. **Validation Conformance** (added): Dedicated tests for the `validate` command
4. **Lint Conformance** (added): Dedicated tests for code quality tooling

This separation ensures each component can be tested independently while maintaining comprehensive coverage.
