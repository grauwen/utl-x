# UTL-X Language Support for VS Code

This extension provides comprehensive language support for UTL-X (Universal Transformation Language - eXtended) by connecting to the UTLXD Language Server Protocol (LSP) implementation.

## Features

- **Syntax Highlighting**: Full syntax highlighting for UTL-X transformation files (.utlx)
- **IntelliSense**: Code completion for functions, operators, and input references
- **Hover Information**: Documentation and type information on hover
- **Diagnostics**: Real-time error checking and validation
- **Semantic Highlighting**: Function names colored by type (stdlib vs. custom functions)

## Requirements

**UTLXD Daemon** must be running with LSP enabled:

```bash
java -jar utlxd.jar start --lsp --lsp-port 7777
```

Or start it programmatically from your application.

## Extension Settings

This extension contributes the following settings:

* `utlx.lsp.host`: Host for UTLXD LSP server (default: `localhost`)
* `utlx.lsp.port`: Port number for UTLXD LSP server (default: `7777`)
* `utlx.trace.server`: Traces the communication between VS Code and the language server (for debugging)

## Known Issues

- The extension requires UTLXD daemon to be running - it does not start the daemon automatically
- If connection fails, check that UTLXD is running with `--lsp --lsp-port 7777`

## Release Notes

### 1.0.0

Initial release:
- LSP client integration with UTLXD daemon
- Syntax highlighting via TextMate grammar
- Support for completion, hover, and diagnostics
- Function highlighting (stdlib vs. custom)

## Architecture

This extension is a thin LSP client. All language intelligence is provided by the UTLXD daemon:

```
VS Code Extension (TypeScript)
        ↓ LSP Protocol (Socket)
UTLXD Daemon (Java/Kotlin)
        ↓ Provides language features
```

## For More Information

* [UTL-X Documentation](../docs/)
* [UTLXD LSP Documentation](../docs/vscode-extension/)

**Enjoy!**
