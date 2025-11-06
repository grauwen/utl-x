# Theia Extension Conformance Suite

Playwright-based end-to-end conformance tests for the UTL-X Theia extension.

## Overview

This conformance suite verifies:
- ✅ UTLXD daemon starts with correct command-line arguments (`--lsp-transport socket`, `--api`, `--api-port 7779`)
- ✅ MCP server starts on port 3001
- ✅ Theia IDE is accessible on port 3000
- ✅ Basic UI elements load correctly

## Prerequisites

- Node.js 18+ and npm/yarn
- UTL-X repository built (modules/server, mcp-server, theia-extension)
- Theia extension compiled

## Installation

```bash
cd conformance-suite/theia-extension
npm install
npx playwright install chromium  # Install browser
```

## Running Tests

### Using the Test Runner (Recommended)

```bash
./runners/typescript-runner/run-theia-tests.sh
```

The runner will:
1. Check if Theia is running (or start it automatically)
2. Wait for services to initialize
3. Run all Playwright tests
4. Clean up (stop Theia if it was started by the runner)

### Manual Testing

If Theia is already running:

```bash
npm test
```

For headed mode (watch tests run):

```bash
npm run test:headed
```

For debug mode:

```bash
npm run test:debug
```

## Test Structure

```
tests/
├── startup/
│   └── services-auto-start.spec.ts   # Service startup and command-line verification
└── ui/
    └── basic-ui.spec.ts               # Basic UI elements
```

## Test Coverage

### Service Auto-Start Tests

- **Theia Accessibility**: Verifies Theia loads on port 3000
- **UTLXD Command-Line Arguments**: Validates correct startup arguments
  - `--lsp` flag present
  - `--lsp-transport socket` (NOT `--transport`)
  - `--api` flag (NOT `--daemon-rest`)
  - `--api-port 7779` (NOT `--daemon-rest-port`)
- **UTLXD REST API**: Confirms health endpoint accessible on port 7779
- **MCP Server**: Verifies MCP server running on port 3001
- **All Services**: Confirms all three services are running

### UI Tests

- **Workbench Loading**: Verifies Theia workbench loads successfully
- **Main Menu**: Confirms menu bar is accessible
- **Status Bar**: Verifies status bar is visible

## CI/CD Integration

The test suite can be integrated into CI pipelines:

```bash
# In your CI script
cd conformance-suite/theia-extension
npm install
npx playwright install --with-deps chromium
./runners/typescript-runner/run-theia-tests.sh
```

## Troubleshooting

### Theia Won't Start

Check the log file:
```bash
cat /tmp/theia-e2e-tests.log
```

### Services Not Ready

The runner waits 10 seconds after Theia starts. If services need more time, modify the sleep in `run-theia-tests.sh`.

### Port Already in Use

If port 3000, 7779, or 3001 is in use:
```bash
lsof -ti:3000,7779,3001 | xargs kill -9
```

## Related Conformance Suites

- **LSP**: `conformance-suite/lsp/` - Language Server Protocol tests
- **Daemon REST API**: `conformance-suite/daemon-rest-api/` - HTTP API tests
- **MCP Server**: `conformance-suite/mcp-server/` - MCP protocol tests
