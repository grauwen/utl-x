# Theia MCP Server Integration Architecture

## Overview

This document describes the hybrid multi-MCP architecture for UTLX, integrating Theia's built-in MCP server alongside existing specialized MCP servers.

## Architecture Diagram

```
Claude Desktop (or other AI client)
    │
    ├─→ Theia Built-in MCP Server
    │       ↓
    │   • Execute IDE commands
    │   • File system operations
    │   • Workspace navigation
    │   • Terminal access
    │
    ├─→ UTLX MCP Server (port 3001)
    │       ↓
    │   • UTL-X validation/execution
    │   • Schema operations
    │   • LLM code generation
    │   • USDL directive registry
    │       ↓
    │   UTLXD Daemon (port 7779)
    │
    └─→ Playwright MCP Server (CDP 9223)
            ↓
        • Browser console logs
        • Network debugging
        • Screenshots/traces
            ↓
        Chrome Browser
```

## MCP Server Comparison

### 1. UTLX MCP Server (Existing)

**Purpose**: UTL-X domain-specific transformation assistance

**Location**: `/Users/magr/data/mapping/github-git/utl-x/mcp-server/`

**Capabilities**:
- 10 specialized tools for UTL-X development:
  - `get_input_schema` - Parse input schemas (XSD, JSON Schema, CSV)
  - `get_stdlib_functions` - Retrieve UTL-X standard library function registry
  - `get_operators` - Retrieve UTL-X operator registry
  - `get_usdl_directives` - Retrieve USDL directive registry (119 directives)
  - `validate_utlx` - Validate UTLX transformation code
  - `infer_output_schema` - Infer output schema from UTLX code
  - `execute_transformation` - Execute UTLX transformations
  - `get_examples` - Search conformance suite tests
  - `generate_utlx_from_prompt` - Generate UTLX code from natural language
  - `check_llm_status` - Check LLM provider availability

**Integration**:
- HTTP transport on port 3001 (for Theia extension)
- Stdio transport (for Claude Desktop)
- Connects to UTLXD daemon on port 7779
- Built-in LLM integration (Claude/Ollama)

**Lifecycle**:
- Managed by `ServiceLifecycleManager` in Theia backend
- Starts automatically when Theia backend initializes
- Health checks via HTTP endpoint

### 2. Theia Built-in MCP Server (New)

**Purpose**: Generic IDE control and automation

**Availability**: Theia 1.57+ (UTLX is on 1.64.0)

**Capabilities** (based on GitHub issue #15824):
- Execute any Theia command programmatically
- Query Theia command registry
- File system operations (read/write/navigate)
- Workspace access
- Terminal output reading
- IDE state control
- Extension contribution points for custom tools

**Integration**:
- Stdio transport for MCP clients
- Part of Theia core framework
- Uses official MCP SDK

**Architecture**:
```
External AI Client → MCP Protocol → Theia MCP Server → Theia IDE
```

### 3. Playwright MCP Server (Existing)

**Purpose**: Real-time browser debugging for UTLX IDE

**Location**: `/Users/magr/data/mapping/github-git/utl-x/playwright-mcp-server/`

**Capabilities**:
- 6 browser debugging tools:
  - `get_console_logs` - Browser console output with filtering
  - `get_errors` - JavaScript errors with stack traces
  - `get_network_logs` - HTTP requests/responses
  - `take_screenshot` - On-demand screenshots
  - `get_page_info` - Current URL and page info
  - `capture_trace` - Full Playwright traces

**Integration**:
- Chrome DevTools Protocol (CDP) on port 9223
- Stdio transport (for Claude Desktop)
- Connects to Chrome browser with remote debugging

**Technology Stack**:
- Uses official `@modelcontextprotocol/sdk` package
- Playwright for browser automation
- CDP for Chrome connection

## Comparison Matrix

| Aspect | UTLX MCP Server | Theia Built-in MCP | Playwright MCP |
|--------|-----------------|-------------------|----------------|
| **Scope** | UTL-X transformation domain | Generic IDE control | Browser debugging |
| **Integration** | UTLXD daemon REST API | Theia command registry | Chrome CDP |
| **Tools** | 10 UTL-X-specific tools | Generic command execution | 6 browser tools |
| **LLM** | Built-in (Claude/Ollama) | No built-in LLM | None |
| **Transport** | HTTP + stdio | Stdio | Stdio |
| **Purpose** | AI-assisted transformation coding | External IDE automation | Browser debugging |
| **Lifecycle** | Managed by Theia extension | Part of Theia core | Standalone process |
| **Port** | 3001 (HTTP) | N/A | 9223 (CDP) |

## Recommendation: Hybrid Approach

### Why Keep All Three MCP Servers?

**1. UTLX MCP Server** ✅ **KEEP**
- **Domain expertise**: 10 specialized UTL-X tools that Theia MCP can't provide
- **LLM integration**: Built-in code generation capability
- **UTLXD coupling**: Direct daemon integration for validation/execution
- **Proven value**: Already production-ready with 119 USDL directives
- **Unique capabilities**: Schema inference, transformation execution, example search

**2. Theia Built-in MCP Server** ✅ **ADD**
- **IDE control**: File operations, command execution, workspace navigation
- **Complementary**: Different use case than UTLX tools
- **External automation**: Allows external AI tools to control the IDE
- **Standard protocol**: Uses official MCP SDK and Theia framework

**3. Playwright MCP Server** ✅ **KEEP**
- **Browser debugging**: Console logs, network traffic, screenshots
- **Different scope**: Browser automation vs. IDE automation vs. transformation assistance
- **Real-time monitoring**: Live debugging during development

### Tool Distribution by Use Case

| If you need... | Use... |
|----------------|--------|
| UTL-X validation/execution | UTLX MCP Server |
| Generate transformation code | UTLX MCP Server (has LLM) |
| Parse schemas (XSD/JSON/CSV) | UTLX MCP Server |
| USDL directive documentation | UTLX MCP Server |
| Search conformance examples | UTLX MCP Server |
| Open/close files in IDE | Theia MCP Server |
| Execute IDE commands | Theia MCP Server |
| Navigate workspace | Theia MCP Server |
| Read terminal output | Theia MCP Server |
| Debug browser console | Playwright MCP Server |
| Monitor network traffic | Playwright MCP Server |
| Take screenshots | Playwright MCP Server |

## Integration Workflows

### Workflow 1: AI-Assisted UTL-X Development

**User Request**: "Generate a transformation to convert this XML to JSON"

**AI uses multiple MCP servers**:

1. **Theia MCP**: Read input schema file from workspace
   ```
   Tool: execute_command
   Command: workbench.action.files.openFile
   Args: { path: "schemas/input.xsd" }
   ```

2. **UTLX MCP**: Generate transformation code via LLM
   ```
   Tool: generate_utlx_from_prompt
   Args: {
     prompt: "Convert XML to JSON",
     inputs: [{ format: "XML", definition: "..." }],
     outputFormat: "JSON"
   }
   ```

3. **Theia MCP**: Write generated code to .utl file
   ```
   Tool: execute_command
   Command: workbench.action.files.saveAs
   Args: { path: "transformations/xml-to-json.utl", content: "..." }
   ```

4. **UTLX MCP**: Validate the transformation
   ```
   Tool: validate_utlx
   Args: { code: "..." }
   ```

5. **Theia MCP**: Open the file in editor
   ```
   Tool: execute_command
   Command: vscode.open
   Args: { resource: "transformations/xml-to-json.utl" }
   ```

6. **UTLX MCP**: Execute transformation with sample data
   ```
   Tool: execute_transformation
   Args: {
     code: "...",
     inputs: [{ format: "XML", data: "..." }]
   }
   ```

7. **Playwright MCP**: Check browser console for any errors
   ```
   Tool: get_console_logs
   Args: { level: "error" }
   ```

### Workflow 2: Debugging Workflow

**User Request**: "Why is my transformation failing?"

**AI uses**:

1. **Theia MCP**: Read the .utl transformation file
   ```
   Tool: execute_command
   Command: workbench.action.files.readFile
   Args: { path: "transformations/failing.utl" }
   ```

2. **UTLX MCP**: Validate for syntax/type errors
   ```
   Tool: validate_utlx
   Args: { code: "..." }
   ```

3. **Playwright MCP**: Get browser console errors
   ```
   Tool: get_errors
   Args: { limit: 20 }
   ```

4. **UTLX MCP**: Search conformance suite for similar examples
   ```
   Tool: get_examples
   Args: { query: "XML transformation error handling" }
   ```

5. **Theia MCP**: Suggest fixes by editing the file
   ```
   Tool: execute_command
   Command: workbench.action.files.edit
   Args: { path: "transformations/failing.utl", edits: [...] }
   ```

### Workflow 3: Comprehensive Testing

**User Request**: "Test my transformation end-to-end"

**AI orchestration**:

1. **UTLX MCP**: Validate transformation code
2. **UTLX MCP**: Execute with test data
3. **Theia MCP**: Save output to file
4. **Playwright MCP**: Monitor browser network requests
5. **Playwright MCP**: Take screenshot of results
6. **Theia MCP**: Open test report in editor
7. **UTLX MCP**: Compare output with expected schema

## Implementation Plan

### Step 1: Add Theia AI Packages

**Dependencies to add** (in `browser-app/package.json`):

```json
{
  "dependencies": {
    "@theia/ai-core": "^1.67.0",
    "@theia/ai-mcp": "^1.67.0",
    "@theia/ai-chat": "^1.67.0",
    "@theia/ai-history": "^1.67.0"
  }
}
```

**Installation**:
```bash
cd /Users/magr/data/mapping/github-git/utl-x/theia-extension/browser-app
yarn add @theia/ai-core @theia/ai-mcp @theia/ai-chat @theia/ai-history
yarn install
```

### Step 2: Configure Theia MCP Server

**Enable in backend module** (`utlx-theia-extension/src/node/backend-module.ts`):

```typescript
import { ContainerModule } from 'inversify';
import { McpServerContribution } from '@theia/ai-mcp';

export default new ContainerModule(bind => {
    // Existing bindings...

    // Enable Theia MCP Server
    bind(McpServerContribution).to(UtlxTheiaaMcpContribution).inSingletonScope();
});
```

**Create MCP contribution** (`utlx-theia-extension/src/node/mcp/theia-mcp-contribution.ts`):

```typescript
import { injectable } from 'inversify';
import { McpServerContribution, McpToolRegistry } from '@theia/ai-mcp';

@injectable()
export class UtlxTheiaMcpContribution implements McpServerContribution {

    registerTools(registry: McpToolRegistry): void {
        // Optional: Register UTLX-specific tools that leverage Theia commands

        registry.registerTool({
            name: 'utlx_open_transformation',
            description: 'Open a UTL transformation file in the editor',
            inputSchema: {
                type: 'object',
                properties: {
                    path: { type: 'string', description: 'Path to .utl file' }
                },
                required: ['path']
            },
            execute: async ({ path }: { path: string }) => {
                // Execute Theia command to open file
                // This bridges Theia MCP with UTLX workflows
            }
        });
    }
}
```

### Step 3: Update Service Lifecycle Manager

**Modify** (`utlx-theia-extension/src/node/services/service-lifecycle-manager.ts`):

```typescript
// Add logging for Theia MCP server detection
async onStart(): Promise<void> {
    console.log('[ServiceLifecycleManager] Starting UTLX services...');

    // Check if Theia MCP is available
    const theiaMcpAvailable = this.isTheiaMcpAvailable();
    if (theiaMcpAvailable) {
        console.log('[ServiceLifecycleManager] Theia MCP server is available');
    } else {
        console.log('[ServiceLifecycleManager] Theia MCP server not available (requires @theia/ai-mcp)');
    }

    // Start UTLXD daemon
    await this.startUtlxDaemon();

    // Start UTLX MCP server (existing)
    await this.startMcpServer();

    console.log('[ServiceLifecycleManager] All services started successfully');
}

private isTheiaMcpAvailable(): boolean {
    try {
        require.resolve('@theia/ai-mcp');
        return true;
    } catch {
        return false;
    }
}
```

### Step 4: Configure Claude Desktop

**Update Claude Desktop configuration** (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "utlx-transformation": {
      "command": "node",
      "args": [
        "/Users/magr/data/mapping/github-git/utl-x/mcp-server/dist/index.js"
      ],
      "env": {
        "UTLX_DAEMON_URL": "http://localhost:7779",
        "UTLX_MCP_TRANSPORT": "stdio",
        "UTLX_LOG_LEVEL": "info"
      }
    },
    "theia-ide": {
      "command": "node",
      "args": [
        "/Users/magr/data/mapping/github-git/utl-x/theia-extension/browser-app/node_modules/@theia/ai-mcp/lib/server/index.js"
      ],
      "env": {
        "THEIA_WORKSPACE": "/Users/magr/data/utlx-workspace"
      }
    },
    "playwright-debug": {
      "command": "node",
      "args": [
        "/Users/magr/data/mapping/github-git/utl-x/playwright-mcp-server/dist/index.js"
      ],
      "env": {
        "CDP_URL": "http://localhost:9223"
      }
    }
  }
}
```

### Step 5: Build and Test

**Build steps**:

```bash
# 1. Install Theia AI dependencies
cd /Users/magr/data/mapping/github-git/utl-x/theia-extension/browser-app
yarn install

# 2. Rebuild extension
cd ../utlx-theia-extension
npx tsc

# 3. Rebuild browser app
cd ../browser-app
yarn install --check-files
npx theia build --mode production

# 4. Verify MCP servers are built
cd ../../mcp-server
npm run build

cd ../playwright-mcp-server
npm run build
```

**Testing**:

1. Start Theia with all services:
   ```bash
   cd /Users/magr/data/mapping/github-git/utl-x/theia-extension
   ./rebuild-and-start-mcp.sh
   ```

2. Verify services are running:
   - UTLXD daemon: `curl http://localhost:7779/api/health`
   - UTLX MCP: `curl http://localhost:3001/health`
   - Chrome CDP: `curl http://localhost:9223/json`
   - Theia: `curl http://localhost:4000`

3. Test MCP tools in Claude Desktop:
   - Ask Claude to list available tools
   - Try UTLX transformation workflow
   - Try file operations via Theia MCP
   - Try browser debugging via Playwright MCP

## Total Tool Count

**Combined MCP ecosystem**:
- **UTLX MCP Server**: 10 tools
- **Theia MCP Server**: ~15-20 generic IDE tools (estimated)
- **Playwright MCP Server**: 6 tools
- **Total**: ~31-36 tools available to AI assistants

## Port Allocation

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Theia IDE | 4000 | HTTP | Web interface |
| UTLXD Daemon | 7779 | HTTP REST | Transformation engine |
| UTLX MCP Server | 3001 | HTTP | UTLX MCP tools (internal) |
| UTLX MCP Server | stdio | JSON-RPC 2.0 | Claude Desktop integration |
| Theia MCP Server | stdio | MCP | Claude Desktop integration |
| Playwright MCP | stdio | MCP | Claude Desktop integration |
| Chrome CDP | 9223 | Chrome DevTools | Browser debugging |

## Configuration Files

### Environment Variables

**For UTLX MCP Server**:
```bash
UTLX_DAEMON_URL=http://localhost:7779
UTLX_MCP_TRANSPORT=stdio  # or http
UTLX_MCP_PORT=3001
UTLX_LOG_LEVEL=info
UTLX_LLM_PROVIDER=claude  # or ollama
ANTHROPIC_API_KEY=sk-...
```

**For Playwright MCP Server**:
```bash
CDP_URL=http://localhost:9223
LOG_BUFFER_SIZE=500
```

**For Theia**:
```bash
THEIA_WORKSPACE=/Users/magr/data/utlx-workspace
AUTO_START_SERVICES=true
```

### Service Startup Configuration

**In `service-lifecycle-manager.ts`**:

```typescript
const config = {
    utlxd: {
        jarPath: 'modules/daemon/build/libs/utlxd-1.0.0-SNAPSHOT.jar',
        restPort: 7779,
        logFile: '/tmp/utlxd-theia.log'
    },
    mcpServer: {
        path: 'mcp-server/dist/index.js',
        port: 3001,
        logFile: '/tmp/mcp-server-theia.log',
        transport: 'http'
    },
    autoStart: true,
    shutdownTimeout: 5000
};
```

## Logging and Monitoring

### Log Files

| Service | Log File | Location |
|---------|----------|----------|
| UTLXD Daemon | `utlxd-theia.log` | `/tmp/` |
| UTLX MCP Server | `mcp-server-theia.log` | `/tmp/` |
| Theia Server | `theia-server.log` | `browser-app/` |
| Playwright MCP | `mcp-server.log` | `playwright-mcp-server/` |

### Health Check Endpoints

```bash
# UTLXD Daemon
curl http://localhost:7779/api/health

# UTLX MCP Server (HTTP mode only)
curl http://localhost:3001/health

# Theia IDE
curl http://localhost:4000

# Chrome CDP
curl http://localhost:9223/json
```

## Security Considerations

### Access Control

1. **UTLX MCP Server**:
   - HTTP mode: Bind to localhost only (127.0.0.1)
   - Stdio mode: No network exposure

2. **Theia MCP Server**:
   - Stdio transport only (no network exposure)
   - Controlled via Claude Desktop configuration

3. **Playwright MCP Server**:
   - Stdio transport only
   - CDP port bound to localhost

4. **UTLXD Daemon**:
   - REST API on localhost:7779
   - No external network access

### Authentication

- **Current**: No authentication (localhost-only services)
- **Future**: Consider adding API keys for production deployments

## Troubleshooting

### Common Issues

**Issue 1**: Theia MCP server not starting

**Solution**:
```bash
# Verify @theia/ai-mcp is installed
cd browser-app
yarn list @theia/ai-mcp

# Rebuild if missing
yarn add @theia/ai-mcp
yarn install
npx theia build --mode production
```

**Issue 2**: UTLX MCP server conflicts

**Solution**:
- UTLX MCP uses HTTP (3001) for Theia, stdio for Claude
- Theia MCP uses stdio only
- No conflict expected

**Issue 3**: Port conflicts

**Solution**:
```bash
# Check if ports are in use
lsof -ti:4000,7779,3001,9223

# Kill conflicting processes
lsof -ti:4000 | xargs kill -9
```

**Issue 4**: Claude Desktop not seeing tools

**Solution**:
1. Verify MCP server paths in `claude_desktop_config.json`
2. Check MCP server logs for errors
3. Restart Claude Desktop
4. Test with simple tool invocation

### Debug Commands

```bash
# Check all UTLX processes
ps aux | grep -E "utlxd|mcp-server|theia"

# Monitor MCP server logs
tail -f /tmp/mcp-server-theia.log

# Test UTLX MCP tool invocation
curl -X POST http://localhost:3001 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/list"
  }'

# Check Theia MCP availability
# (Theia MCP uses stdio, not HTTP - must test via Claude Desktop)
```

## Future Enhancements

### Phase 1: Enhanced Integration
- [ ] Bridge UTLX MCP tools through Theia MCP for unified access
- [ ] Add progress notifications for long-running operations
- [ ] Implement tool chaining for complex workflows

### Phase 2: Advanced Features
- [ ] File watcher integration for automatic validation
- [ ] Workspace-aware schema discovery
- [ ] Integrated testing framework with Playwright MCP
- [ ] Visual diff viewer for transformation outputs

### Phase 3: Production Readiness
- [ ] Authentication and authorization
- [ ] Rate limiting and throttling
- [ ] Metrics and observability
- [ ] Multi-workspace support

## References

### Documentation
- [Theia MCP GitHub Issue #15824](https://github.com/eclipse-theia/theia/issues/15824)
- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [Theia AI Documentation](https://theia-ide.org/docs/user_ai/)
- [Eclipse Source: Theia MCP Blog](https://eclipsesource.com/blogs/2024/12/19/theia-ide-and-theia-ai-support-mcp/)

### Related Files
- `mcp-server/src/index.ts` - UTLX MCP server implementation
- `playwright-mcp-server/src/index.ts` - Playwright MCP server
- `utlx-theia-extension/src/node/backend-module.ts` - Theia backend bindings
- `utlx-theia-extension/src/node/services/service-lifecycle-manager.ts` - Service management

### Version Information
- Theia: 1.64.0 (current), 1.67.0 (latest with AI packages)
- UTLX MCP Server: 1.0.0
- Playwright MCP Server: 1.0.0
- Node.js: 18+ recommended
- Chrome/Chrome Canary: Latest

---

**Last Updated**: 2025-01-02
**Author**: AI-assisted documentation
**Status**: Planning phase
