# Daemon REST API Conformance Test Suite

This test suite validates the daemon REST API implementation in UTLXD (the UTL-X daemon server).

## Overview

The Daemon REST API conformance suite tests the HTTP/REST API endpoints exposed by UTLXD. It verifies:

- **Protocol compliance**: Health checks, JSON responses
- **Core endpoints**: Transform, validate, schema generation, schema parsing
- **Error handling**: Proper error responses for invalid inputs
- **Workflows**: Multi-step operations combining validation and transformation

**Note**: This suite tests the *daemon REST API* (port 7779), which is distinct from the MCP Server (Model Context Protocol JSON-RPC 2.0 server on port 3000). The daemon REST API provides HTTP endpoints used by the MCP server and other clients.

## Directory Structure

```
daemon-rest-api/
├── tests/
│   ├── protocol/        # Protocol compliance tests (health, status)
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
```

## Running Tests

### Prerequisites

- Python 3.7+
- Java 11+ (for running UTLXD)
- Built UTLXD JAR: `modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar`

### Quick Start

From the conformance-suite/daemon-rest-api directory:

```bash
# Run all tests (auto-starts daemon)
./runners/python-runner/run-daemon-rest-api-tests.sh

# Run specific category
./runners/python-runner/run-daemon-rest-api-tests.sh tests/protocol

# Run with verbose output
./runners/python-runner/run-daemon-rest-api-tests.sh -v

# Filter by tag
./runners/python-runner/run-daemon-rest-api-tests.sh -t basic

# Filter by name
./runners/python-runner/run-daemon-rest-api-tests.sh -n transform
```

### Manual Daemon Control

If you want to manage the daemon yourself:

```bash
# Start daemon manually with REST API on port 7779
java -jar ../../modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start --daemon-rest --daemon-rest-port 7779

# Run tests without auto-starting daemon
python3 runners/python-runner/daemon-rest-api-runner.py tests/ --no-auto-start

# Stop daemon
pkill -f "utlxd.*--daemon-rest-port 7779"
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
    endpoint: /api/execute
    headers:
      Content-Type: application/json
    body:
      utlx: "output: input"
      input: "{\"name\": \"John\"}"
      inputFormat: "json"
      outputFormat: "json"
    expect:
      status: 200
      body:
        success: true
        output: "{\"name\": \"John\"}"
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
    executionTimeMs: "{{NUMBER}}"
```

## Test Categories

### Protocol Tests (`tests/protocol/`)

- `health_check.yaml` - Health endpoint verification (`/api/health`)
- `json_rpc_basic.yaml` - JSON response format validation
- `json_rpc_errors.yaml` - JSON error handling

### Endpoint Tests (`tests/endpoints/`)

- `status.yaml` - Server status endpoint
- `transform_basic.yaml` - Basic transformation (`/api/execute`)
- `validate_syntax.yaml` - Syntax validation (`/api/validate`)

### Edge Cases (`tests/edge-cases/`)

- `invalid_script.yaml` - Invalid script handling

### Workflows (`tests/workflows/`)

- `validate_then_transform.yaml` - Validate before transform

## Daemon REST API Endpoints

The daemon exposes these REST API endpoints on port 7779:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/health` | GET | Health check |
| `/api/validate` | POST | Validate UTLX code |
| `/api/execute` | POST | Execute transformation |
| `/api/infer-schema` | POST | Infer output schema |
| `/api/parse-schema` | POST | Parse input schema |

### Example Requests

**Validate:**
```bash
curl -X POST http://localhost:7779/api/validate \
  -H "Content-Type: application/json" \
  -d '{"utlx": "output: input.name", "strict": false}'
```

**Execute:**
```bash
curl -X POST http://localhost:7779/api/execute \
  -H "Content-Type: application/json" \
  -d '{
    "utlx": "output: { fullName: input.firstName + \" \" + input.lastName }",
    "input": "{\"firstName\": \"John\", \"lastName\": \"Doe\"}",
    "inputFormat": "json",
    "outputFormat": "json"
  }'
```

## Runner Features

The Python test runner (`daemon-rest-api-runner.py`) provides:

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
./runners/python-runner/run-daemon-rest-api-tests.sh tests/your-category/your-test.yaml -v
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
pkill -f "utlxd.*--daemon-rest-port 7779"
# or
lsof -ti:7779 | xargs kill -9
```

### Port already in use

Check if another process is using port 7779:

```bash
lsof -i:7779
# Kill the process
kill -9 <PID>
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
cd conformance-suite/daemon-rest-api
./runners/python-runner/run-daemon-rest-api-tests.sh || exit 1
```

The runner exits with code 0 for all tests passing, non-zero for any failures.

## Relationship to MCP Server

This conformance suite tests the **daemon REST API** (HTTP endpoints on port 7779), which is the backend service used by:

1. **MCP Server** (JSON-RPC 2.0 on stdio/HTTP port 3000) - Provides tool invocation interface for LLMs
2. **Direct API clients** - Applications that call the daemon REST API directly

The daemon REST API is a lower-level service. The MCP Server acts as a higher-level protocol adapter that:
- Implements MCP (Model Context Protocol) JSON-RPC 2.0 protocol
- Provides 6 MCP tools (get_input_schema, get_stdlib_functions, validate_utlx, infer_output_schema, execute_transformation, get_examples)
- Translates MCP tool calls to daemon REST API calls
- Adds LLM-specific features (TF-IDF example search, stdlib registry, etc.)

For testing the MCP Server itself, see the separate MCP Server conformance suite (to be created).
