# MCP Conformance Test Suite

This test suite validates the MCP (Model Context Protocol) REST API implementation in UTLXD (the UTL-X daemon server).

## Overview

The MCP conformance suite tests the HTTP/REST API endpoints exposed by UTLXD for MCP integration. It verifies:

- **Protocol compliance**: JSON-RPC 2.0 protocol, health checks, tool registration
- **Core endpoints**: Transform, validate, schema generation
- **Error handling**: Proper error responses for invalid inputs
- **Workflows**: Multi-step operations combining validation and transformation

## Directory Structure

```
mcp/
├── tests/
│   ├── protocol/        # Protocol compliance tests (health, tools, JSON-RPC)
│   ├── endpoints/       # REST API endpoint tests (transform, validate, status)
│   ├── sessions/        # Session management tests
│   ├── edge-cases/      # Error handling and edge cases
│   └── workflows/       # Multi-step scenario tests
├── runners/
│   └── python-runner/   # Python test runner
├── fixtures/
│   ├── scripts/         # Sample UTL-X scripts for testing
│   └── inputs/          # Sample input data
└── lib/                 # Shared utilities

## Running Tests

### Prerequisites

- Python 3.7+
- Java 11+ (for running UTLXD)
- Built UTLXD JAR: `modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar`

### Quick Start

From the conformance-suite/mcp directory:

```bash
# Run all tests (auto-starts daemon)
./runners/python-runner/run-mcp-tests.sh

# Run specific category
./runners/python-runner/run-mcp-tests.sh tests/protocol

# Run with verbose output
./runners/python-runner/run-mcp-tests.sh -v

# Filter by tag
./runners/python-runner/run-mcp-tests.sh -t basic

# Filter by name
./runners/python-runner/run-mcp-tests.sh -n transform
```

### Manual Daemon Control

If you want to manage the daemon yourself:

```bash
# Start daemon manually
java -jar ../../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start --rest-api --no-lsp --port 7778

# Run tests without auto-starting daemon
python3 runners/python-runner/mcp-runner.py tests/ --no-auto-start

# Stop daemon
pkill -f "utlxd.*--port 7778"
```

## Test File Format

Tests are defined in YAML files with this structure:

```yaml
name: Test Name
description: What this test verifies
tags:
  - category
  - feature

sequence:
  - description: "Step description"
    method: POST
    endpoint: /mcp/transform
    headers:
      Content-Type: application/json
    body:
      script: "%utlx 1.0\ninput json\noutput json\n---\ninput"
      input:
        name: "John"
    expect:
      status: 200
      body:
        output:
          name: "John"
```

### Placeholders

Use placeholders in `expect.body` for dynamic values:

- `{{ANY}}` - Matches any value
- `{{STRING}}` - Matches any string
- `{{NUMBER}}` - Matches any number
- `{{BOOLEAN}}` - Matches true/false
- `{{TIMESTAMP}}` / `{{ISO8601}}` - Matches ISO 8601 timestamps
- `{{UUID}}` - Matches UUID format
- `{{REGEX:pattern}}` - Matches regex pattern

Example:

```yaml
expect:
  status: 200
  body:
    timestamp: "{{TIMESTAMP}}"
    id: "{{UUID}}"
    count: "{{NUMBER}}"
```

## Test Categories

### Protocol Tests (`tests/protocol/`)

- `health_check.yaml` - Health endpoint verification
- `tools_list.yaml` - MCP tools registration
- `json_rpc_basic.yaml` - JSON-RPC 2.0 request/response
- `json_rpc_errors.yaml` - JSON-RPC error handling

### Endpoint Tests (`tests/endpoints/`)

- `status.yaml` - Server status endpoint
- `transform_basic.yaml` - Basic transformation
- `validate_syntax.yaml` - Syntax validation

### Edge Cases (`tests/edge-cases/`)

- `invalid_script.yaml` - Invalid script handling
- More tests to be added

### Workflows (`tests/workflows/`)

- `validate_then_transform.yaml` - Validate before transform
- More multi-step scenarios to be added

## Runner Features

The Python test runner (`mcp-runner.py`) provides:

- **Auto daemon management**: Starts/stops UTLXD automatically
- **Parallel testing**: Runs tests in sequence per file
- **Deep comparison**: Recursive validation with placeholders
- **Colored output**: Green ✓ for pass, Red ✗ for fail
- **Detailed errors**: Shows exact mismatch path and values
- **Test discovery**: Filter by category, tag, or name
- **Summary stats**: Pass/fail counts and percentages

## Adding New Tests

1. Create a new YAML file in the appropriate `tests/` subdirectory
2. Follow the test file format above
3. Use placeholders for dynamic values
4. Run the test to verify it works:

```bash
./runners/python-runner/run-mcp-tests.sh tests/your-category/your-test.yaml -v
```

## Troubleshooting

### Tests fail with "Connection refused"

The daemon may not have started. Check:
- JAR exists at `modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar`
- Build the JAR: `./gradlew :modules:server:jar`
- Check daemon logs: `tail -f /tmp/utlxd-test.log`

### Tests timeout

Increase timeout in test file:

```yaml
timeout: 30  # seconds
```

### Daemon doesn't stop

Kill manually:

```bash
pkill -f "utlxd.*--port 7778"
# or
lsof -ti:7778 | xargs kill -9
```

## Contributing

When adding tests:
1. Ensure tests are idempotent (can run multiple times)
2. Use descriptive names and descriptions
3. Add appropriate tags for filtering
4. Test both success and failure scenarios
5. Use placeholders for non-deterministic values

## CI/CD Integration

To integrate into CI/CD:

```bash
# Build daemon
./gradlew :modules:server:jar

# Run tests with exit code
cd conformance-suite/mcp
./runners/python-runner/run-mcp-tests.sh || exit 1
```

The runner exits with code 0 for all tests passing, non-zero for any failures.
