# UTL-X Theia Extension

A comprehensive IDE extension for [Eclipse Theia](https://theia-ide.org) that provides a three-panel workbench for developing and testing UTL-X transformations with both **Design-Time** (schema-based) and **Runtime** (data-based) modes.

## Features

### 🎨 Design-Time Mode
- Load input schemas (XSD, JSON Schema, Avro Schema, Protobuf)
- Type-check transformations against schemas
- Infer output schemas automatically
- Catch type errors before deployment
- Generate API documentation

### ▶️ Runtime Mode
- Execute transformations with actual data
- Support for XML, JSON, CSV, YAML formats
- Real-time output display
- Performance metrics
- Error diagnostics

### 🔄 Advanced Features
- Monaco editor with syntax highlighting
- LSP integration for autocomplete and hover hints
- Real-time validation
- Mode switching with preserved state
- MCP (Model Context Protocol) integration
- 635+ standard library functions

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Theia Frontend (Browser)                   │
│  ┌────────────┬──────────────────┬──────────────────────┐    │
│  │  Input     │   Monaco Editor  │      Output          │    │
│  │  Panel     │   with LSP       │      Panel           │    │
│  └────────────┴──────────────────┴──────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
                            ↕ WebSocket (JSON-RPC)
┌──────────────────────────────────────────────────────────────┐
│                    Theia Backend (Node.js)                    │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  UTLXService → UTLXDaemonClient → UTLXD Process      │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
                            ↕ stdio (JSON-RPC)
┌──────────────────────────────────────────────────────────────┐
│                    UTLXD Daemon (JVM/Kotlin)                  │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  LSP Server | REST API | Parser | Type Checker       │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

## Prerequisites

### Required
- **Node.js** 18+ (LTS recommended)
- **Yarn** or **npm** 7+
- **UTLXD** daemon binary (built from `modules/server`)
- **Java** 11+ (for UTLXD daemon)

### Optional
- **Theia** development environment (for testing)
- **VS Code** (for extension development)

## Installation

### 1. Build UTLXD Daemon

First, build the UTLXD daemon from the main project:

```bash
cd /path/to/utl-x
./gradlew :modules:server:build
```

This creates `utlxd` binary in `modules/server/build/libs/`.

### 2. Install Extension Dependencies

```bash
cd theia-extension/utlx-theia-extension
yarn install
```

### 3. Build Extension

```bash
yarn build
```

### 4. Link Extension (for development)

```bash
yarn link
```

## Usage in Theia Application

### 1. Add Extension to Your Theia Application

In your Theia application's `package.json`:

```json
{
  "dependencies": {
    "utlx-theia-extension": "^0.1.0",
    ...
  }
}
```

### 2. Start Theia with Extension

```bash
cd your-theia-app
yarn start
```

### 3. Open UTL-X Workbench

- From menu: **View → UTL-X Workbench**
- Or use command palette: `UTL-X: Open Workbench`

## Using the Extension

### Design-Time Mode (Schema-Based Type Checking)

1. **Switch to Design-Time Mode**
   - Click the "🎨 Design-Time" button in the mode selector

2. **Load Input Schema**
   - In the left panel, click "📁 Load"
   - Select format (XSD, JSON Schema, etc.)
   - Paste or load your schema

3. **Write Transformation**
   - In the middle panel, write your UTL-X code
   - Autocomplete shows schema-aware suggestions
   - Hover for type information

4. **Infer Output Schema**
   - Click "🔍 Infer Schema" button
   - Right panel shows inferred JSON Schema
   - Review type errors if any

### Runtime Mode (Data Transformation)

1. **Switch to Runtime Mode**
   - Click the "▶️ Runtime" button in the mode selector

2. **Load Input Data**
   - In the left panel, click "📁 Load"
   - Select format (XML, JSON, CSV, YAML)
   - Paste or load your data

3. **Write Transformation**
   - In the middle panel, write your UTL-X code
   - Real-time validation as you type

4. **Execute Transformation**
   - Click "▶️ Execute" button
   - Right panel shows transformation output
   - View execution time and diagnostics

## Keyboard Shortcuts

- **Execute Transformation**: `Ctrl+Shift+E` (Mac: `Cmd+Shift+E`)
- **Validate Code**: `Ctrl+Shift+V` (Mac: `Cmd+Shift+V`)
- **Toggle Mode**: `Ctrl+Shift+M` (Mac: `Cmd+Shift+M`)
- **Clear Panels**: `Ctrl+Shift+C` (Mac: `Cmd+Shift+C`)

## Configuration

The extension can be configured via Theia preferences:

```json
{
  "utlx.daemon.path": "/path/to/utlxd",
  "utlx.daemon.logFile": "/tmp/utlxd-theia.log",
  "utlx.autoExecute": false,
  "utlx.autoExecuteDelay": 1000,
  "utlx.defaultMode": "runtime",
  "utlx.enableTypeChecking": true
}
```

## Development

### Project Structure

```
utlx-theia-extension/
├── src/
│   ├── browser/                # Frontend code
│   │   ├── input-panel/        # Input panel widget
│   │   ├── output-panel/       # Output panel widget
│   │   ├── mode-selector/      # Mode switching UI
│   │   ├── workbench/          # Main workbench
│   │   ├── style/              # CSS styles
│   │   ├── frontend-module.ts  # Frontend DI bindings
│   │   └── utlx-frontend-contribution.ts
│   ├── node/                   # Backend code
│   │   ├── daemon/             # Daemon client
│   │   ├── services/           # Backend services
│   │   └── backend-module.ts   # Backend DI bindings
│   └── common/                 # Shared types
│       └── protocol.ts         # API definitions
├── package.json
├── tsconfig.json
└── README.md
```

### Building

```bash
# Clean build
yarn clean && yarn build

# Watch mode (for development)
yarn watch
```

### Testing

```bash
# Run in test Theia application
cd ../test-app
yarn start
```

### Debugging

1. Set `utlx.daemon.logFile` in preferences
2. Check daemon logs: `tail -f /tmp/utlxd-theia.log`
3. Use browser DevTools for frontend debugging
4. Use Node.js debugger for backend

## MCP Integration

The extension integrates with the UTL-X MCP Server for enhanced functionality:

- **get_input_schema**: Parse schemas from multiple formats
- **validate_utlx**: Syntax and semantic validation
- **infer_output_schema**: Schema inference with type checking
- **execute_transformation**: Multi-format transformations
- **get_stdlib_functions**: Access to 635+ stdlib functions
- **get_examples**: TF-IDF-based example search

MCP tools are automatically available when UTLXD is running.

## Troubleshooting

### Daemon Not Starting

**Error**: `Failed to start UTL-X daemon`

**Solutions**:
1. Check if `utlxd` is in PATH or set `utlx.daemon.path`
2. Verify UTLXD builds successfully: `utlxd --version`
3. Check daemon logs for errors
4. Ensure no other process is using ports 7779 (REST API)

### Connection Timeout

**Error**: `Request timed out after 30s`

**Solutions**:
1. Restart daemon: Use command "UTL-X: Restart Daemon"
2. Check if daemon process is running: `ps aux | grep utlxd`
3. Increase timeout in configuration
4. Check network/firewall settings

### Type Checking Not Working

**Error**: No autocomplete or type errors in Design-Time mode

**Solutions**:
1. Verify input schema is valid
2. Check schema format matches input type
3. Enable type checking in mode selector
4. Restart extension

### Compilation Errors

**Error**: `Cannot find module '@theia/core'`

**Solutions**:
1. Run `yarn install` to install dependencies
2. Clear node_modules and reinstall: `rm -rf node_modules && yarn install`
3. Check Theia version compatibility

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** the repository
2. **Create** a feature branch
3. **Write** tests for new features
4. **Follow** TypeScript best practices
5. **Submit** a pull request

## License

AGPL-3.0 - See LICENSE file for details

## Support

- **Documentation**: [docs.utlx-lang.org](https://docs.utlx-lang.org)
- **Issues**: [GitHub Issues](https://github.com/utl-x/utl-x/issues)
- **Discussions**: [GitHub Discussions](https://github.com/utl-x/utl-x/discussions)

## Related Projects

- **UTL-X Core**: Main transformation language implementation
- **UTLXD Daemon**: LSP and REST API server
- **MCP Server**: Model Context Protocol integration
- **CLI Tools**: Command-line transformation tools

## Credits

Developed by the UTL-X team with contributions from the open-source community.

Special thanks to:
- Eclipse Theia project
- Monaco Editor team
- Language Server Protocol community

---

**Version**: 0.1.0
**Last Updated**: 2025-11-05
**Status**: Alpha (Phase 1 - Runtime Mode Foundation Complete)
