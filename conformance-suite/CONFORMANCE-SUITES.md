# UTL-X Conformance Test Suites

UTL-X maintains six distinct conformance test suites to ensure correctness, quality, and standards compliance across different aspects of the language implementation.

## Overview

| Suite | Purpose | Test Count | Runners | Status |
|-------|---------|------------|---------|--------|
| **Runtime/Transform Conformance** | Language runtime, transformations, stdlib | 465 tests | Python (CLI), Kotlin | ✅ 100% |
| **Validation Conformance** | `utlx validate` command (3 levels) | TBD | Python | 🚧 In Development |
| **Lint Conformance** | `utlx lint` command (code quality) | TBD | Python | 🚧 In Development |
| **LSP Conformance** | Language Server Protocol daemon | TBD | Kotlin | ✅ Active |
| **Daemon REST API Conformance** | Daemon HTTP REST API endpoints (port 7779) | 9 tests | Python | ✅ Active |
| **MCP Server Conformance** | Model Context Protocol JSON-RPC 2.0 (port 3000) | TBD | Python/TypeScript | 🚧 Planned |

## Architecture Overview

Understanding the relationship between the daemon REST API and MCP Server:

```
┌─────────────────────────────────────────────────────────┐
│                      LLM Clients                        │
│            (Claude Desktop, GPT-4, etc.)                │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ MCP Protocol (JSON-RPC 2.0)
                     │ stdio or HTTP (port 3000)
                     │
┌────────────────────▼────────────────────────────────────┐
│                   MCP Server                            │
│              (TypeScript/Node.js)                       │
│                                                         │
│  • 6 MCP Tools:                                        │
│    - get_input_schema                                  │
│    - get_stdlib_functions                              │
│    - validate_utlx                                     │
│    - infer_output_schema                               │
│    - execute_transformation                            │
│    - get_examples (TF-IDF search)                      │
│                                                         │
│  ✅ Tested by: MCP Server Conformance Suite           │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ HTTP/REST (port 7779)
                     │
┌────────────────────▼────────────────────────────────────┐
│              Daemon REST API                            │
│                 (UTLXD - Kotlin)                        │
│                                                         │
│  • 5 REST Endpoints:                                   │
│    - GET  /api/health                                  │
│    - POST /api/validate                                │
│    - POST /api/execute                                 │
│    - POST /api/infer-schema                            │
│    - POST /api/parse-schema                            │
│                                                         │
│  ✅ Tested by: Daemon REST API Conformance Suite      │
└────────────────────┬────────────────────────────────────┘
                     │
                     │ In-process calls
                     │
┌────────────────────▼────────────────────────────────────┐
│                UTL-X Core Runtime                       │
│                  (Kotlin)                               │
│                                                         │
│  • Lexer, Parser, Type Checker                         │
│  • Interpreter, Standard Library                       │
│  • Format parsers (JSON, XML, CSV, YAML, etc.)         │
│                                                         │
│  ✅ Tested by: Runtime/Transform Conformance Suite    │
└─────────────────────────────────────────────────────────┘
```

**Key Points**:
- **Daemon REST API** (port 7779): Low-level HTTP endpoints for validation, execution, and schema operations
- **MCP Server** (port 3000): High-level JSON-RPC 2.0 protocol adapter for LLM integration
- **Runtime**: Core transformation engine used by both

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

## 5. Daemon REST API Conformance Suite

**Tests the HTTP REST API implementation in UTLXD (the UTL-X daemon server).**

### Location
```
conformance-suite/daemon-rest-api/
```

### What It Tests
- HTTP REST API endpoints on port 7779:
  - Health checks (`/api/health`)
  - Validation operations (`/api/validate`)
  - Transformation execution (`/api/execute`)
  - Schema inference (`/api/infer-schema`)
  - Schema parsing (`/api/parse-schema`)
- Error handling and edge cases
- Multi-step workflows (validate then transform)
- HTTP protocol correctness
- JSON request/response format validation

**Note**: This suite tests the *daemon REST API*, which is the backend service used by the MCP Server and other clients. For testing the MCP Server itself (Model Context Protocol JSON-RPC 2.0), see the separate MCP Server conformance suite.

### Test Structure
```
daemon-rest-api/
├── tests/
│   ├── protocol/           # Protocol compliance
│   │   ├── health_check.yaml
│   │   ├── json_rpc_basic.yaml
│   │   └── json_rpc_errors.yaml
│   ├── endpoints/          # REST API endpoints
│   │   ├── status.yaml
│   │   ├── transform_basic.yaml
│   │   └── validate_syntax.yaml
│   ├── sessions/           # Session management (TBD)
│   ├── edge-cases/         # Error handling
│   │   └── invalid_script.yaml
│   └── workflows/          # Multi-step scenarios
│       └── validate_then_transform.yaml
├── runners/
│   └── python-runner/      # Python test runner
│       ├── daemon-rest-api-runner.py
│       ├── run-daemon-rest-api-tests.sh
│       └── requirements.txt
├── fixtures/
│   ├── scripts/            # Sample UTL-X scripts
│   └── inputs/             # Sample input data
└── lib/                    # Shared utilities
```

### Runner

#### Python Daemon REST API Runner
```bash
cd conformance-suite/daemon-rest-api
./runners/python-runner/run-daemon-rest-api-tests.sh

# Run specific category
./runners/python-runner/run-daemon-rest-api-tests.sh tests/protocol

# Run with verbose output
./runners/python-runner/run-daemon-rest-api-tests.sh -v

# Filter by tag
./runners/python-runner/run-daemon-rest-api-tests.sh -t basic
```

### Daemon Being Tested
```bash
# Start UTLXD with daemon REST API on port 7779
utlxd start --daemon-rest --daemon-rest-port 7779

# Or using Java directly
java -jar modules/server/build/libs/utlxd-1.3.0.jar start --daemon-rest --daemon-rest-port 7779
```

### Features
- **Auto daemon management**: Runner automatically starts/stops UTLXD with daemon REST API
- **Placeholder support**: Dynamic value matching (timestamps, UUIDs, regex)
- **Deep comparison**: Recursive object/array validation
- **HTTP/REST testing**: Full request/response cycle validation
- **Colored output**: Green ✓ for pass, Red ✗ for fail
- **Port 7779**: Tests daemon REST API endpoints (distinct from MCP Server on port 3000)

---

## 6. MCP Server Conformance Suite

**Tests the Model Context Protocol (MCP) JSON-RPC 2.0 server implementation.**

### Location
```
conformance-suite/mcp-server/  (planned)
```

### What It Tests
- MCP protocol compliance (JSON-RPC 2.0)
- Server initialization and capabilities exchange
- Tool invocation for all 6 MCP tools:
  1. **get_input_schema**: Parse XSD, JSON Schema, CSV headers
  2. **get_stdlib_functions**: Retrieve stdlib function registry with filtering
  3. **validate_utlx**: Validate UTLX code for syntax and type errors
  4. **infer_output_schema**: Infer output schema from transformation code
  5. **execute_transformation**: Execute transformations with multiple format support
  6. **get_examples**: TF-IDF similarity search over conformance suite
- Transport modes (stdio and HTTP)
- Error handling and JSON-RPC error codes
- Tool parameter validation
- Response format correctness
- Integration with daemon REST API backend

**Note**: This suite tests the *MCP Server* (port 3000), which is the JSON-RPC 2.0 protocol adapter for LLM integration. It uses the daemon REST API (port 7779) as its backend. This is distinct from the Daemon REST API Conformance Suite which tests the backend directly.

### Test Structure (Planned)
```
mcp-server/
├── tests/
│   ├── protocol/                    # MCP protocol compliance
│   │   ├── initialize.yaml          # Server initialization
│   │   ├── capabilities.yaml        # Capabilities exchange
│   │   ├── json_rpc_format.yaml     # JSON-RPC 2.0 format
│   │   └── error_codes.yaml         # Error code handling
│   ├── tools/                       # Tool invocation tests
│   │   ├── get_input_schema/
│   │   │   ├── json_schema.yaml
│   │   │   ├── xsd.yaml
│   │   │   └── csv_headers.yaml
│   │   ├── get_stdlib_functions/
│   │   │   ├── all_functions.yaml
│   │   │   ├── filter_by_category.yaml
│   │   │   └── search_by_query.yaml
│   │   ├── validate_utlx/
│   │   │   ├── valid_code.yaml
│   │   │   ├── syntax_errors.yaml
│   │   │   └── type_errors.yaml
│   │   ├── infer_output_schema/
│   │   │   ├── simple_transform.yaml
│   │   │   └── with_input_schema.yaml
│   │   ├── execute_transformation/
│   │   │   ├── json_to_json.yaml
│   │   │   ├── xml_to_json.yaml
│   │   │   ├── csv_to_json.yaml
│   │   │   └── error_handling.yaml
│   │   └── get_examples/
│   │       ├── search_basic.yaml
│   │       ├── search_with_filters.yaml
│   │       └── tfidf_ranking.yaml
│   ├── transport/                   # Transport layer tests
│   │   ├── stdio.yaml               # Standard I/O transport
│   │   └── http.yaml                # HTTP transport
│   ├── integration/                 # End-to-end scenarios
│   │   ├── validate_then_execute.yaml
│   │   └── schema_aware_transform.yaml
│   └── edge-cases/                  # Error handling
│       ├── invalid_parameters.yaml
│       ├── malformed_requests.yaml
│       └── daemon_unavailable.yaml
├── runners/
│   ├── python-runner/               # Python test runner (stdio)
│   │   ├── mcp-server-runner.py
│   │   └── run-mcp-server-tests.sh
│   └── typescript-runner/           # TypeScript test runner (HTTP)
│       └── run-mcp-server-tests.ts
├── fixtures/
│   ├── schemas/                     # Sample XSD, JSON Schema files
│   ├── utlx-code/                   # Sample UTLX transformations
│   └── inputs/                      # Sample input data
└── lib/                             # Shared utilities
```

### Runner (Planned)

#### Python MCP Server Runner
```bash
cd conformance-suite/mcp-server
./runners/python-runner/run-mcp-server-tests.sh

# Run specific tool tests
./runners/python-runner/run-mcp-server-tests.sh tests/tools/execute_transformation

# Run with verbose output
./runners/python-runner/run-mcp-server-tests.sh -v

# Test specific transport
./runners/python-runner/run-mcp-server-tests.sh --transport stdio
./runners/python-runner/run-mcp-server-tests.sh --transport http
```

### MCP Server Being Tested
```bash
# Start MCP Server with stdio transport (default)
cd mcp-server
npm start

# Start MCP Server with HTTP transport
UTLX_MCP_TRANSPORT=http UTLX_MCP_PORT=3000 npm start

# Ensure daemon REST API is running (backend dependency)
java -jar modules/server/build/libs/utlxd-1.3.0.jar start --daemon-rest --daemon-rest-port 7779
```

### Features (Planned)
- **Auto server management**: Runner automatically starts/stops MCP server
- **Dual transport testing**: Tests both stdio and HTTP modes
- **JSON-RPC validation**: Validates JSON-RPC 2.0 protocol compliance
- **Tool coverage**: Tests all 6 MCP tools comprehensively
- **Backend integration**: Validates correct usage of daemon REST API
- **TF-IDF validation**: Tests example search ranking quality
- **Colored output**: Green ✓ for pass, Red ✗ for fail
- **Port 3000**: Tests MCP Server (distinct from daemon REST API on port 7779)

### Test Dependencies
The MCP Server conformance suite requires:
1. **MCP Server**: Built TypeScript/Node.js server (`mcp-server/dist/`)
2. **Daemon REST API**: Running UTLXD with `--daemon-rest --daemon-rest-port 7779`
3. **Conformance Suite Tests**: Available for TF-IDF search (`conformance-suite/utlx/tests/`)

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

# 5. Daemon REST API Conformance (formerly "MCP Conformance")
cd ../daemon-rest-api
./runners/python-runner/run-daemon-rest-api-tests.sh
# Expected: All daemon REST API endpoints working

# 6. MCP Server Conformance (planned)
cd ../mcp-server
./runners/python-runner/run-mcp-server-tests.sh
# Expected: All MCP tools and protocol features working
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

  daemon-rest-api-conformance:
    runs-on: ubuntu-latest
    steps:
      - name: Build UTLXD
        run: ./gradlew :modules:server:jar
      - name: Run Daemon REST API Conformance
        run: |
          cd conformance-suite/daemon-rest-api
          ./runners/python-runner/run-daemon-rest-api-tests.sh

  mcp-server-conformance:
    runs-on: ubuntu-latest
    needs: daemon-rest-api-conformance  # MCP Server depends on daemon
    steps:
      - name: Build UTLXD
        run: ./gradlew :modules:server:jar
      - name: Build MCP Server
        run: |
          cd mcp-server
          npm install --legacy-peer-deps
          npm run build
      - name: Run MCP Server Conformance
        run: |
          cd conformance-suite/mcp-server
          ./runners/python-runner/run-mcp-server-tests.sh
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
| Daemon REST API testing | "daemon REST API conformance" or "daemon conformance" |
| MCP Server testing | "MCP Server conformance" or "MCP conformance" |
| All suites | "full conformance" or "all conformance suites" |

**Default**: When someone says "conformance" without a qualifier, they typically mean the **Runtime/Transform Conformance Suite** (the 465-test main suite).

**Important**: When referring to "MCP conformance," always clarify whether you mean:
- **MCP Server conformance**: Tests the MCP Server (JSON-RPC 2.0 protocol adapter, port 3000)
- **Daemon REST API conformance**: Tests the daemon's REST API (backend endpoints, port 7779) - formerly called "MCP conformance"

---

## Contributing New Tests

See individual suite README files for contribution guidelines:
- Runtime: `conformance-suite/utlx/tests/README.md`
- Validation: `conformance-suite/utlx/validation-tests/README.md`
- Lint: `conformance-suite/utlx/lint-tests/README.md`
- LSP: `conformance-suite/lsp/README.md`
- Daemon REST API: `conformance-suite/daemon-rest-api/README.md`
- MCP Server: `conformance-suite/mcp-server/README.md` (planned)

---

## Historical Context

The conformance suite structure evolved to support different testing needs:

1. **Runtime Conformance** (2023): Started as the primary test suite for language features
2. **LSP Conformance** (2024): Separated IDE/tooling tests from runtime tests
3. **Validation Conformance** (2024): Dedicated tests for the `validate` command
4. **Lint Conformance** (2024): Dedicated tests for code quality tooling
5. **Daemon REST API Conformance** (2024): Tests for the daemon's HTTP REST API endpoints (port 7779)
6. **MCP Server Conformance** (2025 - planned): Tests for Model Context Protocol JSON-RPC 2.0 server

This separation ensures each component can be tested independently while maintaining comprehensive coverage.

### Renaming: "MCP Conformance" → "Daemon REST API Conformance"

**Background**: In November 2024, during the implementation of Phase 2 (MCP Server Foundation), we discovered a naming ambiguity in the conformance suites. The suite located at `conformance-suite/mcp/` was originally created to test the daemon's REST API endpoints, but its name "MCP Conformance" incorrectly suggested it tested the Model Context Protocol itself.

**The Problem**:
- The suite tested daemon REST API endpoints (`/api/validate`, `/api/execute`, etc.) on port 7779
- The suite did NOT test the MCP Server (JSON-RPC 2.0 protocol) on port 3000
- This naming caused confusion about what was actually being tested

**The Solution** (November 2025):
- Renamed directory: `conformance-suite/mcp/` → `conformance-suite/daemon-rest-api/`
- Renamed runners: `mcp-runner.py` → `daemon-rest-api-runner.py`
- Updated port: 7778 → 7779 (standardized daemon REST API port)
- Updated daemon flags: `--rest-api --port` → `--daemon-rest --daemon-rest-port`
- Updated all documentation to clarify the distinction

**Going Forward**:
- **Daemon REST API Conformance**: Tests the backend HTTP REST API (port 7779)
- **MCP Server Conformance** (new): Will test the MCP Server JSON-RPC 2.0 protocol (port 3000)

This clarifies the architecture where the MCP Server (frontend) uses the daemon REST API (backend) to provide LLM integration capabilities.
