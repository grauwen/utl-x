# UTL-X VS Code Extension Documentation

## Overview

The UTL-X VS Code extension provides comprehensive language support for UTL-X transformation files by integrating with the UTLXD Language Server Protocol (LSP) implementation. This document explains the architecture, setup, and usage of the extension.

## Table of Contents

- [Architecture](#architecture)
- [How It Works](#how-it-works)
- [Installation](#installation)
- [Building from Source](#building-from-source)
- [Configuration](#configuration)
- [Features](#features)
- [Troubleshooting](#troubleshooting)
- [Comparison with Direct Monaco Providers](#comparison-with-direct-monaco-providers)

---

## Architecture

### High-Level Overview

```
┌─────────────────────────────────────────┐
│  VS Code / Theia IDE                    │
│                                         │
│  ┌───────────────────────────────────┐ │
│  │  UTL-X Extension (TypeScript)     │ │
│  │  - LSP Client                     │ │
│  │  - Language Registration          │ │
│  │  - Syntax Highlighting            │ │
│  └───────────────┬───────────────────┘ │
│                  │                       │
└──────────────────┼───────────────────────┘
                   │ LSP Protocol
                   │ (Socket on port 7777)
                   │
┌──────────────────▼───────────────────────┐
│  UTLXD Daemon (Java/Kotlin)              │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  LSP Server                        │ │
│  │  - Completion Provider             │ │
│  │  - Hover Provider                  │ │
│  │  - Diagnostics Provider            │ │
│  │  - Semantic Tokens Provider        │ │
│  │  - Custom Methods (schema, etc.)   │ │
│  └────────────────────────────────────┘ │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  UTL-X Runtime                     │ │
│  │  - Parser                          │ │
│  │  - Type Checker                    │ │
│  │  - Standard Library (650+ funcs)  │ │
│  │  - Execution Engine                │ │
│  └────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

### Component Responsibilities

**VS Code Extension (Client):**
- Thin glue code (~150 lines)
- Establishes socket connection to UTLXD
- Forwards LSP requests/responses
- Registers .utlx file association
- Provides basic syntax highlighting (TextMate grammar)

**UTLXD Daemon (Server):**
- All language intelligence
- Function registry (650+ stdlib functions)
- Type inference and validation
- Schema support (XSD, JSON Schema, Avro)
- Execution capabilities

---

## How It Works

### LSP Communication Flow

```
User types in editor
       ↓
1. textDocument/didChange
       ↓ (via socket)
UTLXD parses and validates
       ↓
2. textDocument/publishDiagnostics
       ↓ (via socket)
Errors shown in editor
       ↓
User requests completion (Ctrl+Space)
       ↓
3. textDocument/completion
       ↓ (via socket)
UTLXD provides suggestions
       ↓
4. CompletionList response
       ↓ (via socket)
Suggestions shown in editor
```

### LSP Protocol Details

**Transport:** TCP Socket (not HTTP REST)
- Connection: `localhost:7777`
- Protocol: JSON-RPC 2.0 over socket
- Messages: LSP 3.17 format

**Supported LSP Methods:**
- `textDocument/didOpen` - Document opened
- `textDocument/didChange` - Document edited
- `textDocument/didClose` - Document closed
- `textDocument/completion` - Code completion
- `textDocument/hover` - Hover information
- `textDocument/publishDiagnostics` - Error/warning reporting
- `textDocument/semanticTokens/full` - Semantic highlighting

**Custom Methods (UTLX-specific):**
- `utlx/loadSchema` - Load XSD/JSON Schema/Avro
- `utlx/setMode` - Switch design-time vs runtime mode
- `utlx/inferOutputSchema` - Infer output schema from transformation

---

## Installation

### For Users

**Option 1: Install from VSIX**
1. Download `utlx-language-support-1.0.0.vsix`
2. In VS Code: `Extensions → ... → Install from VSIX`
3. Select the downloaded .vsix file
4. Reload VS Code

**Option 2: Install in Theia**
1. Copy .vsix to Theia extensions directory:
   ```bash
   cp utlx-language-support-1.0.0.vsix ~/theia-extensions/
   ```
2. Restart Theia application

### Prerequisites

**UTLXD Daemon must be running:**

```bash
# Start UTLXD with LSP enabled
java -jar utlxd.jar start --lsp --lsp-port 7777
```

Or if starting programmatically:
```typescript
const daemon = spawn('java', [
    '-jar', daemonJarPath,
    'start',
    '--lsp',
    '--lsp-transport', 'socket',  // Use socket transport
    '--lsp-port', '7777'
]);
```

---

## Building from Source

### Prerequisites

- Node.js 18+ and npm
- TypeScript 5.0+
- VS Code Extension Manager (`vsce`)

### Build Steps

```bash
# Navigate to extension directory
cd vscode-extension

# Install dependencies
npm install

# Compile TypeScript
npm run compile

# Package extension
npm run package

# Output: utlx-language-support-1.0.0.vsix
```

### Development Mode

```bash
# Open extension in VS Code
code vscode-extension/

# Press F5 to launch Extension Development Host
# Make changes and reload (Ctrl+R) to test
```

---

## Configuration

### Extension Settings

Configure in VS Code Settings (Ctrl+,) or Theia preferences:

```json
{
  "utlx.lsp.host": "localhost",
  "utlx.lsp.port": 7777,
  "utlx.trace.server": "off"  // "off" | "messages" | "verbose"
}
```

**Settings:**

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `utlx.lsp.host` | string | `localhost` | Hostname of UTLXD LSP server |
| `utlx.lsp.port` | number | `7777` | Port number for LSP connection |
| `utlx.trace.server` | string | `off` | LSP communication trace level (for debugging) |

### Trace Levels

- `off` - No tracing (production)
- `messages` - Log LSP message headers
- `verbose` - Log full LSP messages (debug only)

View traces: `View → Output → UTL-X LSP Trace`

---

## Features

### 1. Syntax Highlighting

**Two-Level Highlighting:**

**Level 1: TextMate Grammar (Instant)**
- Applied immediately when file opens
- Header keywords (`input`, `output`, `%utlx`)
- Format types (`json`, `xml`, `csv`, `yaml`)
- Comments, strings, numbers
- Basic operators

**Level 2: Semantic Tokens (LSP)**
- Applied after LSP connection
- Function names (colored by type)
- Variable references
- Type-accurate highlighting
- Context-aware coloring

### 2. Code Completion (IntelliSense)

**Triggered by:**
- Typing (automatic)
- `Ctrl+Space` (manual)
- `.` after input reference (`$input.`)
- `(` after function name

**Completion Types:**

| Type | Example | Description |
|------|---------|-------------|
| **Functions** | `upperCase(` | 650+ stdlib functions |
| **Input Fields** | `$customers.name` | Fields from input data/schema |
| **Keywords** | `let`, `if`, `match` | Language keywords |
| **Operators** | `==`, `&&`, `+` | Available operators |

**Function Completion Details:**
```
upperCase(str: String): String

Convert string to uppercase

Example: upperCase("hello") => "HELLO"

Parameters:
  str: String to convert

Returns: Uppercased string

Category: String
Since: 1.0
```

### 3. Hover Information

**Hover over:**
- Function names → Shows signature, documentation, examples
- Variables → Shows type and value (if available)
- Keywords → Shows syntax help
- Input references → Shows schema information

**Example:**
```utlx
$customers.forEach(c => upperCase(c.name))
             ↑ hover          ↑ hover
       Schema: Array<Object>  Function documentation
```

### 4. Diagnostics (Error Checking)

**Real-time error detection:**
- Syntax errors (parse errors)
- Type errors (incompatible types)
- Unknown functions
- Invalid input references
- Schema violations (design-time mode)

**Error Levels:**
- 🔴 Error - Prevents execution
- 🟡 Warning - May cause issues
- 🔵 Info - Suggestions

### 5. Semantic Highlighting

**Function Name Coloring:**

```utlx
// Stdlib functions (lowercase start) - Yellow/Gold
upperCase("hello")
map(data, x => x)
filter(list, predicate)

// Custom functions (uppercase start) - Different shade
ProcessData(input)
MyTransform(value)
```

**Input References:**
```utlx
$input         // Variable color (blue/cyan)
$input.field   // Field access
```

---

## Troubleshooting

### Extension Not Activating

**Symptoms:** No syntax highlighting, no completion

**Solutions:**
1. Check file extension is `.utlx`
2. Verify extension is installed: `Extensions → UTL-X Language Support`
3. Reload VS Code: `Ctrl+Shift+P → Reload Window`

### LSP Connection Failed

**Error:** "Failed to connect to UTLXD LSP server at localhost:7777"

**Solutions:**
1. **Verify UTLXD is running:**
   ```bash
   lsof -i :7777
   # Should show java process listening
   ```

2. **Check UTLXD logs:**
   ```bash
   # Look for LSP server startup message
   [LSP] Server listening on port 7777
   ```

3. **Verify port configuration:**
   - Extension setting: `utlx.lsp.port` matches daemon port
   - Firewall not blocking localhost:7777

4. **Test socket connection:**
   ```bash
   telnet localhost 7777
   # Should connect (press Ctrl+C to exit)
   ```

### No Completion Suggestions

**Symptoms:** Ctrl+Space shows nothing

**Solutions:**
1. Check LSP connection (see above)
2. Enable trace: `"utlx.trace.server": "messages"`
3. View output: `View → Output → UTL-X Language Server`
4. Verify UTLXD loaded standard library:
   ```
   [LSP] Loaded 650 functions
   ```

### Incorrect Function Highlighting

**Symptoms:** Stdlib functions not colored correctly

**Possible causes:**
- LSP not connected (using TextMate grammar only)
- Semantic tokens not supported (older VS Code)
- Theme doesn't define function colors

**Solutions:**
1. Verify LSP connection
2. Update VS Code to 1.75+
3. Try different color theme

---

## Comparison with Direct Monaco Providers

### Current Theia Extension (Direct Providers)

**Architecture:**
```
Theia Extension
  ↓ HTTP REST API (port 7779)
UTLXD Daemon
```

**Pros:**
- Works in Theia without VS Code extension
- Custom UI widgets (input panel, output panel, etc.)
- Direct control over completion behavior

**Cons:**
- Uses REST API, not LSP protocol
- Higher latency (HTTP request/response)
- Doesn't leverage UTLXD LSP capabilities
- Can't share with VS Code users

### VS Code Extension (LSP Client)

**Architecture:**
```
VS Code Extension
  ↓ LSP Protocol (socket 7777)
UTLXD Daemon
```

**Pros:**
- Standard LSP protocol (lower latency)
- Works in both VS Code and Theia
- Full LSP features (semantic tokens, etc.)
- Utilizes UTLXD LSP server implementation
- Can be distributed on marketplace

**Cons:**
- Requires separate extension package
- Less control over specific UI behavior
- Theia-specific widgets need separate extension

### Recommended Approach

**Use BOTH:**
1. **VS Code Extension** - For language features (completion, hover, diagnostics)
2. **Theia Extension** - For custom UI (input/output panels, function builder)

They can coexist:
- VS Code extension provides LSP features
- Theia extension provides custom widgets
- Both connect to same UTLXD daemon

---

## Advanced Topics

### Custom LSP Methods

The extension supports UTLX-specific LSP methods:

```typescript
// Load schema for design-time mode
workspace/executeCommand: {
    command: "utlx.loadSchema",
    arguments: [schemaContent, schemaType]
}

// Switch modes
workspace/executeCommand: {
    command: "utlx.setMode",
    arguments: [{ mode: "design-time" }]
}
```

### Semantic Token Types

UTLX defines custom semantic token types:

| Token Type | Example | Color (vs-dark theme) |
|------------|---------|---------------------|
| `function.stdlib` | `upperCase` | Yellow (#DCDCAA) |
| `function.custom` | `MyFunc` | Yellow variant |
| `variable.input` | `$input` | Light blue (#9CDCFE) |
| `keyword.utlx.header` | `input`, `output` | Blue (#569CD6) |
| `type.format` | `json`, `xml` | Purple (#C586C0) |

### Extension Commands

Available via Command Palette (Ctrl+Shift+P):

| Command | Description |
|---------|-------------|
| `UTL-X: Reload Language Server` | Restart LSP connection |
| `UTL-X: Show Status` | Display connection status |

---

## Contributing

### Project Structure

```
vscode-extension/
├── src/
│   └── extension.ts           # Main entry point
├── syntaxes/
│   └── utlx.tmLanguage.json  # TextMate grammar
├── language-configuration.json # Brackets, comments config
├── package.json               # Extension manifest
├── tsconfig.json             # TypeScript config
└── README.md                 # User-facing docs
```

### Adding Features

1. **Add completion types:** Modify UTLXD LSP server
2. **Add semantic tokens:** Update UTLXD token provider
3. **Add commands:** Register in `extension.ts` and `package.json`

### Testing

```bash
# Unit tests (if added)
npm test

# Manual testing
# Press F5 in VS Code to launch Extension Development Host
```

---

## License

AGPL-3.0 - See LICENSE file for details

---

## Resources

- [VS Code Extension API](https://code.visualstudio.com/api)
- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [vscode-languageclient Documentation](https://www.npmjs.com/package/vscode-languageclient)
- [Theia Extension Development](https://theia-ide.org/docs/extensions/)

---

**Last Updated:** 2025-01-15
**Version:** 1.0.0
