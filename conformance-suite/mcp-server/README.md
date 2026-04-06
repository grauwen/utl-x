# MCP Server Conformance Test Suite

This test suite validates the MCP (Model Context Protocol) Server implementation - a JSON-RPC 2.0 protocol adapter for LLM integration with UTL-X.

## Overview

The MCP Server conformance suite tests the MCP Server's implementation of the Model Context Protocol:

- **Protocol compliance**: JSON-RPC 2.0 format, initialize sequence, capabilities exchange
- **Tool invocation**: All 8 MCP tools with various parameter combinations
- **Transport modes**: stdio (primary) and HTTP
- **Error handling**: Proper JSON-RPC error codes and messages
- **Backend integration**: Correct usage of daemon REST API endpoints

**Note**: This suite tests the *MCP Server* (JSON-RPC 2.0 on port 3000 or stdio), which is distinct from the daemon REST API (HTTP endpoints on port 7779). The MCP Server uses the daemon REST API as its backend.

## Architecture

```
┌──────────────────┐
│   LLM Client     │ (Claude Desktop, etc.)
└────────┬─────────┘
         │ MCP Protocol (JSON-RPC 2.0)
         │ stdio or HTTP
         ↓
┌──────────────────┐
│   MCP Server     │ ← THIS SUITE TESTS THIS
│  (TypeScript)    │
│                  │
│  8 MCP Tools:    │
│  1. get_input_schema
│  2. get_stdlib_functions
│  3. get_operators
│  4. get_usdl_directives
│  5. validate_utlx
│  6. infer_output_schema
│  7. execute_transformation
│  8. get_examples
└────────┬─────────┘
         │ HTTP/REST
         ↓
┌──────────────────┐
│ Daemon REST API  │ (Backend - tested separately)
│   (port 7779)    │
└──────────────────┘
```

## Directory Structure

```
mcp-server/
├── tests/
│   ├── protocol/                    # Protocol compliance
│   │   ├── initialize.yaml
│   │   └── tools_list.yaml
│   ├── tools/                       # Tool invocation
│   │   ├── get_input_schema/
│   │   ├── get_stdlib_functions/
│   │   ├── get_operators/
│   │   ├── get_usdl_directives/
│   │   ├── validate_utlx/
│   │   ├── infer_output_schema/
│   │   ├── execute_transformation/
│   │   └── get_examples/
│   ├── integration/                 # End-to-end scenarios
│   ├── edge-cases/                  # Error handling
│   └── manual/                      # Manual tests (excluded from automated runs)
│       └── transport-http/          # HTTP transport tests (requires HTTP mode)
├── runners/
│   └── python-runner/
│       ├── mcp-server-runner.py     # Python test runner
│       ├── run-mcp-server-tests.sh  # Shell wrapper
│       └── requirements.txt
├── fixtures/
│   ├── schemas/                     # Sample schemas
│   ├── utlx-code/                   # Sample UTLX code
│   └── inputs/                      # Sample input data
└── lib/                             # Shared utilities
```

## Running Tests

### Prerequisites

- Python 3.7+
- Node.js 18+
- Built MCP Server: `mcp-server/dist/index.js`
- Running daemon with REST API on port 7779
- Built daemon JAR: `modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar`

### Quick Start

From the conformance-suite/mcp-server directory:

```bash
# Run all tests (auto-builds and starts dependencies)
./runners/python-runner/run-mcp-server-tests.sh

# Run specific category
./runners/python-runner/run-mcp-server-tests.sh tests/protocol

# Run with verbose output
./runners/python-runner/run-mcp-server-tests.sh -v

# Filter by tag
./runners/python-runner/run-mcp-server-tests.sh -t basic

# Filter by name
./runners/python-runner/run-mcp-server-tests.sh -n validate
```

### Manual Setup

If you want to manage dependencies yourself:

```bash
# 1. Build daemon
cd ../../
./gradlew :modules:server:jar

# 2. Start daemon with REST API
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start \
  --daemon-rest --daemon-rest-port 7779 &

# 3. Build MCP server
cd mcp-server
npm install --legacy-peer-deps
npm run build

# 4. Run tests
cd ../conformance-suite/mcp-server
python3 runners/python-runner/mcp-server-runner.py tests/
```

### Manual Tests (HTTP Transport)

HTTP transport tests are excluded from automated runs because they require the MCP server to run in HTTP mode (instead of stdio). These tests are located in `tests/manual/transport-http/`.

To run HTTP transport tests manually, see: [tests/manual/transport-http/README.md](tests/manual/transport-http/README.md)

## Test File Format

Tests are defined in YAML files with JSON-RPC request/response sequences:

```yaml
name: test_name
description: What this test validates
tags:
  - category
  - feature

sequence:
  - description: "Step description"
    request:
      jsonrpc: "2.0"
      id: 1
      method: "tools/call"
      params:
        name: "validate_utlx"
        arguments:
          utlx: "output: input"
          strict: false
    expect:
      jsonrpc: "2.0"
      id: 1
      result:
        content:
          - type: "text"
            text: "{{JSON}}"
```

### Placeholders

Use placeholders in `expect` for dynamic values:

- `{{ANY}}` - Matches any value
- `{{STRING}}` - Matches any string
- `{{NUMBER}}` - Matches any number
- `{{BOOLEAN}}` - Matches true/false
- `{{JSON}}` - Matches any valid JSON string
- `{{TIMESTAMP}}` / `{{ISO8601}}` - Matches ISO 8601 timestamps
- `{{UUID}}` - Matches UUID format
- `{{REGEX:pattern}}` - Matches regex pattern

Example:

```yaml
expect:
  jsonrpc: "2.0"
  id: 1
  result:
    content:
      - type: "text"
        text: "{{JSON}}"  # Matches any JSON string
```

## Test Categories

### Protocol Tests (`tests/protocol/`)

- `initialize.yaml` - Server initialization handshake
- `tools_list.yaml` - List all 6 available tools

### Tool Tests (`tests/tools/`)

#### validate_utlx
- `valid_code.yaml` - Validate correct UTLX code
- `syntax_errors.yaml` - Handle syntax errors gracefully

#### execute_transformation
- `json_to_json.yaml` - Execute JSON→JSON transformation
- More format combinations (XML, CSV, YAML)

#### get_stdlib_functions
- `all_functions.yaml` - Retrieve all stdlib functions
- Filtering by category and query

#### get_examples
- `search_basic.yaml` - TF-IDF search for examples
- Search with filters and limits

#### infer_output_schema
- Schema inference from UTLX code
- With and without input schema

#### get_input_schema
- Parse JSON Schema, XSD, CSV headers

### Integration Tests (`tests/integration/`)

Multi-step workflows combining multiple tools

### Edge Cases (`tests/edge-cases/`)

Error handling, invalid parameters, daemon unavailable

## MCP Tools

The MCP Server provides 6 tools for LLM integration:

| Tool | Purpose | Backend Endpoint |
|------|---------|------------------|
| `get_input_schema` | Parse input schemas (XSD, JSON Schema, CSV) | `/api/parse-schema` |
| `get_stdlib_functions` | Get stdlib function registry | Local cache |
| `validate_utlx` | Validate UTLX code | `/api/validate` |
| `infer_output_schema` | Infer output schema | `/api/infer-schema` |
| `execute_transformation` | Execute transformation | `/api/execute` |
| `get_examples` | Search examples via TF-IDF | Local conformance suite |

## Runner Features

The Python test runner (`mcp-server-runner.py`) provides:

- **Auto dependency management**: Builds MCP server and starts daemon if needed
- **JSON-RPC 2.0 protocol**: Communicates via stdio with line-delimited JSON
- **Placeholder support**: Dynamic value matching (timestamps, JSON, regex)
- **Deep comparison**: Recursive object/array validation
- **Colored output**: Green ✓ for pass, Red ✗ for fail
- **Detailed errors**: Shows exact mismatch path and values
- **Test discovery**: Filter by category, tag, or name
- **Summary stats**: Pass/fail counts and percentages

## Adding New Tests

1. Create a new YAML file in the appropriate `tests/` subdirectory
2. Follow the test file format above
3. Use placeholders for dynamic values (timestamps, UUIDs, etc.)
4. Run the test to verify it works:

```bash
./runners/python-runner/run-mcp-server-tests.sh tests/your-category/your-test.yaml -v
```

## Troubleshooting

### Tests fail with "MCP server not found"

Build the MCP server:

```bash
cd ../../mcp-server
npm install --legacy-peer-deps
npm run build
```

### Tests fail with "No response from MCP server"

Check MCP server logs. The server may have crashed or encountered an error.

### Tests fail with "Daemon not running"

Start the daemon with REST API:

```bash
cd ../../
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start \
  --daemon-rest --daemon-rest-port 7779
```

### MCP server can't connect to daemon

Ensure daemon is running on port 7779:

```bash
lsof -i:7779
curl http://localhost:7779/api/health
```

### Examples not found (get_examples tool)

The `get_examples` tool requires the conformance suite tests to be present:

```bash
ls ../../conformance-suite/utlx/tests/
```

## Contributing

When adding tests:
1. Ensure tests are idempotent (can run multiple times)
2. Use descriptive names and descriptions
3. Add appropriate tags for filtering
4. Test both success and failure scenarios
5. Use placeholders for non-deterministic values (timestamps, IDs)
6. Document any special dependencies or setup

## CI/CD Integration

To integrate into CI/CD:

```bash
# Build MCP server
cd mcp-server
npm install --legacy-peer-deps
npm run build

# Build daemon
cd ..
./gradlew :modules:server:jar

# Run tests with exit code
cd conformance-suite/mcp-server
./runners/python-runner/run-mcp-server-tests.sh || exit 1
```

The runner exits with code 0 for all tests passing, non-zero for any failures.

## Relationship to Other Conformance Suites

- **Runtime Conformance**: Tests UTL-X core language features
- **Daemon REST API Conformance**: Tests the backend HTTP endpoints (port 7779) that MCP Server uses
- **MCP Server Conformance** (this suite): Tests the MCP Server JSON-RPC 2.0 protocol (port 3000)

The MCP Server acts as a protocol adapter between LLMs and the daemon REST API, adding LLM-specific features like TF-IDF example search and stdlib function registry.
