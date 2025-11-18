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

**Option 1: Install from VSIX (VS Code)**
1. Download `utlx-language-support-1.0.0.vsix`
2. In VS Code: `Extensions → ... → Install from VSIX`
3. Select the downloaded .vsix file
4. Reload VS Code

**Option 2: Bundled with Theia (Recommended for Theia Users)**

The UTL-X Theia application comes with the VS Code extension pre-bundled!

When you build the Theia application, the VS Code extension is:
- Automatically built via `build-vscode-extension.sh`
- Packaged as a VSIX file
- Loaded via the `theiaPlugins` configuration in `browser-app/package.json`
- Available immediately on Theia startup

**No additional installation needed!** Just build and start Theia:
```bash
cd theia-extension/browser-app
yarn install
yarn build
yarn start
```

**Option 3: Manual Install in Theia**
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

## Theia Integration: Dual Extension Architecture

### How Both Extensions Work Together

The UTL-X Theia application uses **BOTH** extensions simultaneously for optimal functionality:

```
┌────────────────────────────────────────────────────────┐
│  Theia IDE (Browser)                                   │
│                                                        │
│  ┌──────────────────────┐  ┌─────────────────────────┐│
│  │ Theia Extension      │  │ VS Code Extension       ││
│  │ (utlx-theia-ext)     │  │ (utlx-language-support) ││
│  │                      │  │                         ││
│  │ - Custom UI widgets  │  │ - LSP client            ││
│  │ - Input/Output panels│  │ - Completion provider   ││
│  │ - Function Builder   │  │ - Hover provider        ││
│  │ - Custom completions │  │ - Diagnostics           ││
│  │   ($input.field)     │  │ - Semantic highlighting ││
│  │ - REST API calls     │  │                         ││
│  └──────────┬───────────┘  └──────────┬──────────────┘│
│             │                         │               │
└─────────────┼─────────────────────────┼───────────────┘
              │                         │
       REST API (7779)             LSP (7777)
              │                         │
              └─────────┬───────────────┘
                        │
              ┌─────────▼──────────┐
              │  UTLXD Daemon      │
              └────────────────────┘
```

### What Each Extension Provides

| Feature | Theia Extension | VS Code Extension |
|---------|----------------|-------------------|
| **UI Widgets** | ✅ Input/Output panels | ❌ |
| **Function Builder** | ✅ Dialog UI | ❌ |
| **Execute Transform** | ✅ REST API | ❌ |
| **Data Completions** | ✅ $input.field (UDM) | ❌ |
| **Function Completions** | ❌ | ✅ LSP-based (650+ funcs) |
| **Hover Info** | ❌ | ✅ LSP-based |
| **Diagnostics** | ❌ | ✅ Real-time errors |
| **Semantic Highlighting** | ❌ | ✅ Function coloring |
| **Schema Support** | ✅ REST API | ✅ LSP protocol |

### How Monaco Benefits

Monaco editor gets **combined capabilities**:

**Completion Suggestions (Merged):**
```
User types: $customers.
├─ Theia Extension provides: field completions from actual data
│  └─ name, address, email (from loaded JSON)
└─ VS Code Extension provides: (not applicable for fields)

User types: upper
├─ Theia Extension provides: (none - doesn't know functions)
└─ VS Code Extension provides: upperCase, upper (from LSP)
    └─ With full documentation, examples, signatures
```

### Why This Architecture?

**Benefits:**
1. ✅ **Best of both worlds** - Custom UI + Standard LSP
2. ✅ **Data-driven completions** - Real input data fields
3. ✅ **Schema-driven completions** - All 650+ functions
4. ✅ **Semantic highlighting** - Functions colored by type
5. ✅ **Works in VS Code** - Extension can be used standalone
6. ✅ **Future-proof** - Standard LSP protocol

**No Conflicts:**
- Both extensions register different providers
- Monaco merges all completion sources
- Each provider handles its domain (data vs schema)

### Comparison with Direct Monaco Providers

### Old Approach: Theia Extension Only

**Architecture:**
```
Theia Extension → REST API (7779) → UTLXD Daemon
```

**Limitations:**
- ❌ No semantic highlighting
- ❌ No LSP-based hover
- ❌ No real-time diagnostics
- ❌ Can't share with VS Code
- ❌ UTLXD LSP server unused

### New Approach: Dual Extension

**Architecture:**
```
Theia Extension → REST API (7779) ──┐
                                    ├→ UTLXD Daemon
VS Code Extension → LSP (7777) ─────┘
```

**Benefits:**
- ✅ Full LSP features
- ✅ Semantic highlighting
- ✅ Real-time diagnostics
- ✅ Works in VS Code too
- ✅ Utilizes UTLXD LSP server

### Migration Impact

**For existing Theia users:**
- No breaking changes
- Custom UI widgets still work
- REST API still functional
- **Added:** LSP features automatically available

**Build process change:**
```bash
# Before
cd theia-extension/browser-app
yarn build

# After (automatic!)
cd theia-extension/browser-app
yarn build  # Automatically builds VS Code extension first
```

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
