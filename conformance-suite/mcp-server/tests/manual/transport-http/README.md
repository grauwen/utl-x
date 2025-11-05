# HTTP Transport Tests (Manual)

These tests validate the MCP Server when running in HTTP transport mode instead of the default stdio mode.

## Why Manual?

HTTP transport tests are excluded from automated test runs because:
- The automated test runner uses stdio transport by default
- HTTP mode requires the server to listen on a TCP port (default: 3000)
- Supporting both transport modes in the runner would add significant complexity
- These tests are primarily for manual validation and debugging

## Running HTTP Transport Tests Manually

### 1. Start the MCP Server in HTTP mode

```bash
cd /Users/magr/data/mapping/github-git/utl-x/mcp-server
UTLX_MCP_TRANSPORT=http UTLX_MCP_PORT=3000 npm start
```

### 2. Run the tests

In a separate terminal:

```bash
cd /Users/magr/data/mapping/github-git/utl-x/conformance-suite/mcp-server
# Run with HTTP endpoint
python3 runners/python-runner/mcp-server-runner.py tests/manual/transport-http --http http://localhost:3000 -v
```

## Available Tests

- `http_initialize.yaml` - Test initialization over HTTP
- `http_tools_list.yaml` - Test tools/list method over HTTP
- `http_tool_call.yaml` - Test tools/call method over HTTP
- `connection_error.yaml` - Test connection error handling

## Integration with CI/CD

For CI/CD pipelines, these tests can be added as a separate job that:
1. Starts the MCP server in HTTP mode
2. Runs the HTTP transport tests
3. Shuts down the server

Example GitHub Actions workflow:

```yaml
http-transport-tests:
  runs-on: ubuntu-latest
  steps:
    - name: Start MCP Server (HTTP mode)
      run: |
        cd mcp-server
        UTLX_MCP_TRANSPORT=http UTLX_MCP_PORT=3000 npm start &
        sleep 5

    - name: Run HTTP transport tests
      run: |
        cd conformance-suite/mcp-server
        ./runners/python-runner/run-mcp-server-tests.sh tests/manual/transport-http -v
```
