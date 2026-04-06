# UTL-X MCP Server

Model Context Protocol server for UTL-X transformation assistance. Provides 8 tools for working with UTL-X transformations through LLM integration.

## Features

### Available Tools

1. **get_input_schema** - Parse input schemas (XSD, JSON Schema, CSV) into structured type definitions
2. **get_stdlib_functions** - Retrieve UTL-X standard library function registry with signatures and documentation
3. **get_operators** - Retrieve UTL-X operator registry with precedence, associativity, and usage
4. **get_usdl_directives** - Retrieve USDL directive registry for schema generation assistance (119 directives across 4 tiers)
5. **validate_utlx** - Validate UTLX transformation code for syntax and type errors
6. **infer_output_schema** - Infer output schema from UTLX transformation code
7. **execute_transformation** - Execute UTLX transformations with sample input data
8. **get_examples** - Search conformance suite tests for relevant examples using TF-IDF similarity

## Installation

```bash
cd mcp-server
npm install
npm run build
```

## Usage

### Prerequisites

The MCP server requires the UTL-X daemon to be running:

```bash
# Start the daemon with REST API enabled
cd ..
./gradlew :modules:server:build
java -jar modules/server/build/libs/utlx-server.jar start --daemon-rest --daemon-rest-port 7779
```

### Stdio Transport (Default)

For MCP client integration (e.g., Claude Desktop):

```bash
npm start
# or
UTLX_DAEMON_URL=http://localhost:7779 npm start
```

### HTTP Transport

For HTTP-based integration:

```bash
UTLX_MCP_TRANSPORT=http UTLX_MCP_PORT=3000 npm start
```

### Development Mode

```bash
npm run dev
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `UTLX_DAEMON_URL` | `http://localhost:7779` | URL of the UTL-X daemon REST API |
| `UTLX_MCP_TRANSPORT` | `stdio` | Transport mode: `stdio` or `http` |
| `UTLX_MCP_PORT` | `3000` | HTTP port (when using HTTP transport) |
| `UTLX_LOG_LEVEL` | `info` | Logging level: `debug`, `info`, `warn`, `error` |

## MCP Client Configuration

### Claude Desktop

Add to your Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS):

```json
{
  "mcpServers": {
    "utlx": {
      "command": "node",
      "args": ["/path/to/utl-x/mcp-server/dist/index.js"],
      "env": {
        "UTLX_DAEMON_URL": "http://localhost:7779"
      }
    }
  }
}
```

### Custom MCP Client

For HTTP transport:

```bash
# Start server
UTLX_MCP_TRANSPORT=http UTLX_MCP_PORT=3000 npm start

# Send JSON-RPC request
curl -X POST http://localhost:3000 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {}
  }'
```

## Tool Examples

### 1. Validate UTLX Code

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "validate_utlx",
    "arguments": {
      "utlx": "output: { fullName: input.firstName + \" \" + input.lastName }",
      "strict": false
    }
  }
}
```

### 2. Execute Transformation

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "execute_transformation",
    "arguments": {
      "utlx": "output: { fullName: input.firstName + \" \" + input.lastName }",
      "input": "{\"firstName\": \"John\", \"lastName\": \"Doe\"}",
      "inputFormat": "json",
      "outputFormat": "json"
    }
  }
}
```

### 3. Get Stdlib Functions

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "get_stdlib_functions",
    "arguments": {
      "category": "string"
    }
  }
}
```

### 4. Search Examples

```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "tools/call",
  "params": {
    "name": "get_examples",
    "arguments": {
      "query": "flatten nested arrays",
      "limit": 3
    }
  }
}
```

### 5. Parse Input Schema

```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "tools/call",
  "params": {
    "name": "get_input_schema",
    "arguments": {
      "schema": "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}}",
      "format": "json-schema"
    }
  }
}
```

### 6. Infer Output Schema

```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "tools/call",
  "params": {
    "name": "infer_output_schema",
    "arguments": {
      "utlx": "output: { fullName: input.firstName + \" \" + input.lastName }",
      "format": "json-schema"
    }
  }
}
```

## Architecture

```
┌─────────────────┐
│   LLM Client    │ (Claude, GPT-4, etc.)
└────────┬────────┘
         │ MCP Protocol (JSON-RPC 2.0)
         │
┌────────▼────────┐
│  MCP Server     │
│  (TypeScript)   │
│                 │
│  - 6 Tools      │
│  - JSON-RPC     │
│  - TF-IDF       │
└────────┬────────┘
         │ HTTP/REST
         │
┌────────▼────────┐
│ UTL-X Daemon    │
│   (Kotlin)      │
│                 │
│  - Validation   │
│  - Execution    │
│  - Schema Ops   │
└─────────────────┘
```

## Development

### Build

```bash
npm run build
```

### Test

```bash
npm test
npm run test:watch
```

### Lint

```bash
npm run lint
npm run lint:fix
```

### Format

```bash
npm run format
npm run format:check
```

### Type Check

```bash
npm run typecheck
```

## Troubleshooting

### Daemon Connection Failed

Ensure the UTL-X daemon is running:

```bash
# Check daemon health
curl http://localhost:7779/api/health
```

### Tool Execution Errors

Enable debug logging:

```bash
UTLX_LOG_LEVEL=debug npm start
```

### Conformance Suite Not Found

The `get_examples` tool searches for conformance tests in:
- `../tests/conformance`
- `../tests/formats`
- `${CWD}/tests/conformance`
- `${CWD}/tests/formats`

Ensure tests are available in one of these locations.

## License

Apache-2.0
