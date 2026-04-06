# UTL-X Services Status

## ✅ Currently Running Services

### 1. UTLXD Daemon
- **PID**: 25961
- **Port**: 7779 (REST API)
- **Protocol**: LSP (stdio) + REST
- **Status**: ✅ Running
- **Health Check**:
  ```bash
  curl http://localhost:7779/api/health
  ```
- **Logs**: `/tmp/utlxd.log`
- **Stop**: `kill 25961`

### 2. MCP Server
- **PID**: 26637
- **Port**: 3001 (HTTP)
- **Protocol**: Model Context Protocol (JSON-RPC 2.0)
- **Status**: ✅ Running
- **Test**:
  ```bash
  curl -X POST http://localhost:3001 \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'
  ```
- **Logs**: `/tmp/mcp-server.log`
- **Stop**: `kill 26637`
- **Provides 6 Tools**:
  1. `get_input_schema` - Parse schemas (XSD, JSON Schema, CSV)
  2. `get_stdlib_functions` - UTL-X standard library
  3. `validate_utlx` - Syntax/type validation
  4. `infer_output_schema` - Schema inference
  5. `execute_transformation` - Execute transformations
  6. `get_examples` - Search conformance tests

### 3. Theia IDE
- **Port**: 3000 (when started)
- **Status**: ⏸️ Ready to start
- **Start Command**:
  ```bash
  cd /Users/magr/data/mapping/github-git/utl-x/theia-extension/browser-app
  yarn start
  ```
- **URL**: http://localhost:3000
- **Extension**: UTL-X Workbench (loaded automatically)

## Architecture Diagram

```
┌─────────────────────┐
│   Browser           │
│  (Port 3000)        │
│                     │
│  ┌───────────────┐  │
│  │ Theia IDE     │  │
│  │ UTL-X         │  │
│  │ Workbench     │  │
│  └───────┬───────┘  │
└──────────┼──────────┘
           │ WebSocket
           ▼
┌──────────────────────┐
│  Theia Backend       │
│  (Node.js)           │
└──────┬───────────────┘
       │
       │ JSON-RPC
       ├─────────────────────┐
       │                     │
       ▼                     ▼
┌─────────────┐      ┌──────────────┐
│   UTLXD     │      │  MCP Server  │
│ Port 7779   │      │  Port 3001   │
│             │◄─────┤              │
│ - REST API  │ HTTP │ - AI Tools   │
│ - LSP       │      │ - JSON-RPC   │
└─────────────┘      └──────────────┘
```

## Port Usage

| Service | Port | Protocol | Purpose |
|---------|------|----------|---------|
| Theia IDE | 3000 | HTTP/WebSocket | Web interface |
| MCP Server | 3001 | HTTP (JSON-RPC) | AI tool integration |
| UTLXD REST | 7779 | HTTP | Transformation API |
| UTLXD LSP | stdio | LSP | Language features |

## Quick Commands

### Start All Services
```bash
# 1. Start UTLXD (if not running)
cd /Users/magr/data/mapping/github-git/utl-x
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start \
  --daemon-lsp --daemon-rest --daemon-rest-port 7779 \
  > /tmp/utlxd.log 2>&1 &

# 2. Start MCP Server (if not running)
cd mcp-server
UTLX_DAEMON_URL=http://localhost:7779 \
UTLX_MCP_TRANSPORT=http \
UTLX_MCP_PORT=3001 \
node dist/index.js > /tmp/mcp-server.log 2>&1 &

# 3. Start Theia IDE
cd theia-extension/browser-app
yarn start
```

### Stop All Services
```bash
# Stop Theia (Ctrl+C in terminal or)
lsof -ti:3000 | xargs kill -9

# Stop MCP Server
kill 26637

# Stop UTLXD
kill 25961
```

### Check Service Health
```bash
# UTLXD
curl http://localhost:7779/api/health

# MCP Server
curl -X POST http://localhost:3001 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# Theia (when running)
curl http://localhost:3000
```

## Next Steps

1. **Start Theia IDE**:
   ```bash
   cd /Users/magr/data/mapping/github-git/utl-x/theia-extension/browser-app
   yarn start
   ```

2. **Open Browser**: http://localhost:3000

3. **Test Workbench**:
   - Open **View → UTL-X Workbench**
   - Load example transformation from `examples/transformations/01-simple-json-to-json.utlx`
   - Load example data from `examples/data/01-order.json`
   - Click **▶️ Execute**
   - See result in output panel

4. **Test MCP Integration** (Advanced):
   - Configure Claude Desktop to use MCP server
   - Ask Claude to help write UTL-X transformations
   - Claude can use all 6 MCP tools

## Troubleshooting

### UTLXD Not Responding
```bash
# Check logs
tail -f /tmp/utlxd.log

# Restart
kill 25961
java -jar modules/server/build/libs/utlxd-1.0.0-SNAPSHOT.jar start \
  --daemon-lsp --daemon-rest --daemon-rest-port 7779 \
  > /tmp/utlxd.log 2>&1 &
```

### MCP Server Not Responding
```bash
# Check logs
tail -f /tmp/mcp-server.log

# Check UTLXD connection
curl http://localhost:7779/api/health

# Restart
kill 26637
cd mcp-server
UTLX_DAEMON_URL=http://localhost:7779 \
UTLX_MCP_TRANSPORT=http \
UTLX_MCP_PORT=3001 \
node dist/index.js > /tmp/mcp-server.log 2>&1 &
```

### Theia Won't Start
```bash
# Check if port 3000 is in use
lsof -ti:3000

# Rebuild if needed
cd browser-app
yarn clean
yarn install --ignore-scripts
npm rebuild node-pty drivelist keytar
cd node_modules/@vscode/ripgrep && npm run postinstall && cd ../..
yarn theia build --mode development
```

---

**Date**: 2025-11-05
**Status**: ✅ UTLXD + MCP Server running, ready to start Theia
