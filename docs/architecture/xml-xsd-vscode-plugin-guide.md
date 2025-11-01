# XML/XSD VS Code Plugin for UTL-X (Monaco Editor Compliant)

## Overview

This document provides a comprehensive guide for adding XML/XSD editing support to UTL-X's VS Code extension, ensuring full compatibility with AGPL-3.0 licensing by avoiding EPL-licensed components like LemMinX.

---

## Licensing Context

### The Problem with LemMinX
- **LemMinX** (Red Hat's XML Language Server) is licensed under **EPL 2.0**
- **EPL 2.0 is incompatible with AGPL 3.0** - cannot link or bundle EPL code in AGPL projects
- Projects using EPL 2.0 libraries cannot be licensed under AGPL 3.0

### The Solution: Apache Xerces
- **Apache Xerces** is licensed under **Apache License 2.0**
- **Apache 2.0 is one-way compatible with AGPL 3.0** - Apache software can be included in AGPL projects
- The final combined work must be distributed under AGPL 3.0
- Built into Java (javax.xml.validation), so zero additional dependencies

---

## Recommended Approach: Extend UTL-X LSP Daemon

Since UTL-X already has an LSP daemon (Phase 2 complete, 84/84 tests passing), the best approach is to **extend it to support XML/XSD validation**.

### Architecture

```
┌─────────────────────────────────────┐
│  VS Code Extension (MIT/Apache 2.0) │
│  - Monaco Editor integration        │
│  - LSP client                       │
└─────────────┬───────────────────────┘
              │ LSP Protocol (JSON-RPC)
              │
┌─────────────▼───────────────────────┐
│  UTL-X LSP Daemon (AGPL 3.0)        │
│  - UTLX language support            │
│  - XML validation (Xerces)          │
│  - XSD validation (Xerces)          │
│  - Completion, hover, diagnostics   │
└─────────────────────────────────────┘
```

### Benefits
- ✅ No EPL licensing issues
- ✅ Full control over features
- ✅ Consistent experience across editors (VS Code, IntelliJ, Vim, web)
- ✅ Leverages existing LSP infrastructure
- ✅ Apache Xerces provides full XSD 1.0/1.1 support
- ✅ Zero additional dependencies (built into Java)

---

## Implementation

### 1. Extend LSP Daemon with XML Support

**File:** `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/XmlLanguageFeatures.kt`

```kotlin
package org.apache.utlx.daemon

import javax.xml.XMLConstants
import javax.xml.validation.SchemaFactory
import javax.xml.transform.stream.StreamSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import java.io.StringReader

class XmlLanguageFeatures(private val stateManager: StateManager) {
    
    private val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    
    /**
     * Validate XML against XSD and return LSP diagnostics
     */
    fun validateXml(uri: String, xmlContent: String): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        
        // Find associated XSD schema
        val xsdPath = findSchemaForXml(uri, xmlContent)
        if (xsdPath == null) {
            return emptyList() // No schema found, skip validation
        }
        
        try {
            val schema = schemaFactory.newSchema(StreamSource(xsdPath))
            val validator = schema.newValidator()
            
            validator.setErrorHandler(object : org.xml.sax.ErrorHandler {
                override fun warning(e: SAXParseException) {
                    diagnostics.add(createDiagnostic(e, DiagnosticSeverity.Warning))
                }
                
                override fun error(e: SAXParseException) {
                    diagnostics.add(createDiagnostic(e, DiagnosticSeverity.Error))
                }
                
                override fun fatalError(e: SAXParseException) {
                    diagnostics.add(createDiagnostic(e, DiagnosticSeverity.Error))
                }
            })
            
            validator.validate(StreamSource(StringReader(xmlContent)))
        } catch (e: Exception) {
            diagnostics.add(Diagnostic(
                range = Range(Position(0, 0), Position(0, 0)),
                severity = DiagnosticSeverity.Error,
                message = "Schema validation failed: ${e.message}"
            ))
        }
        
        return diagnostics
    }
    
    /**
     * Provide completion for XML elements based on XSD
     */
    fun provideXmlCompletion(uri: String, position: Position): List<CompletionItem> {
        val document = stateManager.getDocument(uri) ?: return emptyList()
        val xsdPath = findSchemaForXml(uri, document.content) ?: return emptyList()
        
        // Parse XSD and provide completions based on schema
        val xsdElements = parseXsdElements(xsdPath)
        val context = getXmlContext(document.content, position)
        
        return xsdElements
            .filter { it.isValidInContext(context) }
            .map { element ->
                CompletionItem(
                    label = element.name,
                    kind = CompletionItemKind.Property,
                    detail = element.type,
                    documentation = element.documentation
                )
            }
    }
    
    /**
     * Provide hover information for XML elements
     */
    fun provideXmlHover(uri: String, position: Position): Hover? {
        val document = stateManager.getDocument(uri) ?: return null
        val xsdPath = findSchemaForXml(uri, document.content) ?: return null
        
        val elementName = getElementAtPosition(document.content, position)
        val elementDef = parseXsdElement(xsdPath, elementName) ?: return null
        
        return Hover(
            contents = MarkupContent(
                kind = MarkupKind.Markdown,
                value = """
                    **${elementDef.name}**
                    
                    Type: `${elementDef.type}`
                    
                    ${elementDef.documentation}
                    
                    Attributes: ${elementDef.attributes.joinToString(", ")}
                """.trimIndent()
            )
        )
    }
    
    private fun findSchemaForXml(uri: String, xmlContent: String): String? {
        // 1. Check for xsi:schemaLocation in XML
        val schemaLocationPattern = """xsi:schemaLocation="[^"]*\s+([^"\s]+)"""".toRegex()
        schemaLocationPattern.find(xmlContent)?.let { 
            return it.groupValues[1]
        }
        
        // 2. Check for xsi:noNamespaceSchemaLocation
        val noNsPattern = """xsi:noNamespaceSchemaLocation="([^"]+)"""".toRegex()
        noNsPattern.find(xmlContent)?.let {
            return it.groupValues[1]
        }
        
        // 3. Check workspace configuration for XML file associations
        val associations = stateManager.getConfiguration("xml.fileAssociations")
        return associations?.get(uri)
        
        // 4. Look for .xsd file with same base name
        // e.g., order.xml -> order.xsd
    }
    
    private fun createDiagnostic(e: SAXParseException, severity: DiagnosticSeverity): Diagnostic {
        val line = maxOf(0, e.lineNumber - 1)
        val column = maxOf(0, e.columnNumber - 1)
        
        return Diagnostic(
            range = Range(Position(line, column), Position(line, column + 1)),
            severity = severity,
            source = "xml-xsd",
            message = e.message ?: "Validation error"
        )
    }
    
    private fun parseXsdElements(xsdPath: String): List<XsdElement> {
        // Parse XSD schema to extract element definitions
        // Implementation using javax.xml or Apache XmlSchema
        TODO("Parse XSD schema")
    }
    
    private fun getXmlContext(content: String, position: Position): XmlContext {
        // Determine current XML context (parent element, depth, etc.)
        TODO("Analyze XML context")
    }
    
    private fun getElementAtPosition(content: String, position: Position): String? {
        // Extract element name at given position
        TODO("Extract element name")
    }
    
    private fun parseXsdElement(xsdPath: String, elementName: String): XsdElementDef? {
        // Parse specific element definition from XSD
        TODO("Parse XSD element")
    }
}

data class XsdElement(
    val name: String,
    val type: String,
    val documentation: String?,
    val context: String
) {
    fun isValidInContext(xmlContext: XmlContext): Boolean {
        // Check if element is valid in current XML context
        TODO()
    }
}

data class XsdElementDef(
    val name: String,
    val type: String,
    val documentation: String?,
    val attributes: List<String>
)

data class XmlContext(
    val parentElement: String?,
    val depth: Int,
    val namespace: String?
)
```

### 2. Integrate XML Features into LSP Handlers

**File:** `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/UtlxLanguageServer.kt`

```kotlin
class UtlxLanguageServer : LanguageServer {
    
    private val xmlFeatures = XmlLanguageFeatures(stateManager)
    
    override fun textDocument_didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val content = params.textDocument.text
        
        stateManager.openDocument(uri, content)
        
        // If it's an XML file, validate it
        if (uri.endsWith(".xml")) {
            val diagnostics = xmlFeatures.validateXml(uri, content)
            client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }
    
    override fun textDocument_didChange(params: DidChangeTextDocumentParams) {
        val uri = params.textDocument.uri
        val changes = params.contentChanges
        
        stateManager.updateDocument(uri, changes)
        
        // Re-validate XML on change
        if (uri.endsWith(".xml")) {
            val content = stateManager.getDocument(uri)?.content ?: return
            val diagnostics = xmlFeatures.validateXml(uri, content)
            client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }
    
    override fun textDocument_completion(params: CompletionParams): CompletableFuture<CompletionList> {
        val uri = params.textDocument.uri
        
        return CompletableFuture.supplyAsync {
            val items = when {
                uri.endsWith(".utlx") -> utlxFeatures.provideCompletion(uri, params.position)
                uri.endsWith(".xml") -> xmlFeatures.provideXmlCompletion(uri, params.position)
                else -> emptyList()
            }
            CompletionList(isIncomplete = false, items = items)
        }
    }
    
    override fun textDocument_hover(params: HoverParams): CompletableFuture<Hover?> {
        val uri = params.textDocument.uri
        
        return CompletableFuture.supplyAsync {
            when {
                uri.endsWith(".utlx") -> utlxFeatures.provideHover(uri, params.position)
                uri.endsWith(".xml") -> xmlFeatures.provideXmlHover(uri, params.position)
                else -> null
            }
        }
    }
}
```

### 3. Create VS Code Extension (Separate Repository)

**Directory Structure:**
```
utlx-vscode/
├── package.json          # MIT or Apache 2.0 license
├── README.md
├── LICENSE
├── src/
│   ├── extension.ts      # Main extension entry point
│   └── client.ts         # LSP client
├── syntaxes/
│   ├── utlx.tmLanguage.json
│   └── xml.tmLanguage.json
└── language-configuration.json
```

**package.json:**
```json
{
  "name": "utlx",
  "displayName": "UTL-X Language Support",
  "description": "Language support for UTL-X, XML, and XSD with validation and completion",
  "version": "1.0.0",
  "publisher": "utlx",
  "license": "Apache-2.0",
  "engines": {
    "vscode": "^1.75.0"
  },
  "categories": [
    "Programming Languages",
    "Formatters",
    "Linters"
  ],
  "keywords": [
    "utlx",
    "xml",
    "xsd",
    "transformation",
    "validation"
  ],
  "activationEvents": [
    "onLanguage:utlx",
    "onLanguage:xml"
  ],
  "main": "./out/extension.js",
  "contributes": {
    "languages": [
      {
        "id": "utlx",
        "aliases": ["UTL-X", "utlx"],
        "extensions": [".utlx"],
        "configuration": "./language-configuration.json"
      }
    ],
    "grammars": [
      {
        "language": "utlx",
        "scopeName": "source.utlx",
        "path": "./syntaxes/utlx.tmLanguage.json"
      }
    ],
    "configuration": {
      "title": "UTL-X",
      "properties": {
        "utlx.daemon.path": {
          "type": "string",
          "description": "Path to UTL-X language server daemon. If not set, the extension will try to find it in PATH or use the bundled version."
        },
        "utlx.daemon.args": {
          "type": "array",
          "items": {
            "type": "string"
          },
          "default": ["--stdio"],
          "description": "Arguments to pass to the UTL-X daemon"
        },
        "utlx.xml.validation.enabled": {
          "type": "boolean",
          "default": true,
          "description": "Enable XML validation against XSD schemas"
        },
        "utlx.xml.fileAssociations": {
          "type": "object",
          "default": {},
          "description": "Map XML files to XSD schemas. Example: { \"order.xml\": \"schemas/order.xsd\" }",
          "additionalProperties": {
            "type": "string"
          }
        },
        "utlx.trace.server": {
          "type": "string",
          "enum": ["off", "messages", "verbose"],
          "default": "off",
          "description": "Trace communication between VS Code and the UTL-X language server"
        }
      }
    },
    "commands": [
      {
        "command": "utlx.restartServer",
        "title": "Restart UTL-X Language Server",
        "category": "UTL-X"
      },
      {
        "command": "utlx.showOutputChannel",
        "title": "Show Output Channel",
        "category": "UTL-X"
      }
    ]
  },
  "scripts": {
    "vscode:prepublish": "npm run compile",
    "compile": "tsc -p ./",
    "watch": "tsc -watch -p ./",
    "pretest": "npm run compile",
    "test": "node ./out/test/runTest.js"
  },
  "dependencies": {
    "vscode-languageclient": "^8.1.0"
  },
  "devDependencies": {
    "@types/node": "^18.0.0",
    "@types/vscode": "^1.75.0",
    "typescript": "^5.0.0"
  }
}
```

**src/extension.ts:**
```typescript
import * as path from 'path';
import * as vscode from 'vscode';
import * as child_process from 'child_process';
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind
} from 'vscode-languageclient/node';

let client: LanguageClient;

export function activate(context: vscode.ExtensionContext) {
  console.log('UTL-X extension is now active');

  // Get daemon path from configuration
  const config = vscode.workspace.getConfiguration('utlx');
  const daemonPath = config.get<string>('daemon.path') || 
                     findDaemonInPath() || 
                     bundledDaemonPath(context);

  if (!daemonPath) {
    vscode.window.showErrorMessage(
      'UTL-X daemon not found. Please install UTL-X or configure utlx.daemon.path'
    );
    return;
  }

  // Get daemon arguments
  const daemonArgs = config.get<string[]>('daemon.args') || ['--stdio'];

  // Server options
  const serverOptions: ServerOptions = {
    command: daemonPath,
    args: daemonArgs,
    options: {
      env: process.env
    }
  };

  // Client options
  const clientOptions: LanguageClientOptions = {
    documentSelector: [
      { scheme: 'file', language: 'utlx' },
      { scheme: 'file', language: 'xml' },
      { scheme: 'file', pattern: '**/*.xsd' }
    ],
    synchronize: {
      fileEvents: [
        vscode.workspace.createFileSystemWatcher('**/*.utlx'),
        vscode.workspace.createFileSystemWatcher('**/*.xml'),
        vscode.workspace.createFileSystemWatcher('**/*.xsd')
      ],
      configurationSection: 'utlx'
    },
    outputChannelName: 'UTL-X Language Server',
    traceOutputChannel: vscode.window.createOutputChannel('UTL-X Language Server Trace')
  };

  // Create language client
  client = new LanguageClient(
    'utlx',
    'UTL-X Language Server',
    serverOptions,
    clientOptions
  );

  // Register commands
  context.subscriptions.push(
    vscode.commands.registerCommand('utlx.restartServer', async () => {
      if (client) {
        await client.stop();
        await client.start();
        vscode.window.showInformationMessage('UTL-X Language Server restarted');
      }
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand('utlx.showOutputChannel', () => {
      client.outputChannel.show();
    })
  );

  // Start the client
  client.start().then(() => {
    console.log('UTL-X Language Server started successfully');
  }).catch(error => {
    vscode.window.showErrorMessage(`Failed to start UTL-X Language Server: ${error.message}`);
    console.error('Failed to start language server:', error);
  });

  context.subscriptions.push(client);
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}

function findDaemonInPath(): string | undefined {
  try {
    // Try to find utlx-daemon in PATH
    const result = child_process.execSync(
      process.platform === 'win32' ? 'where utlx-daemon' : 'which utlx-daemon',
      { encoding: 'utf8' }
    );
    return result.trim().split('\n')[0];
  } catch (error) {
    return undefined;
  }
}

function bundledDaemonPath(context: vscode.ExtensionContext): string | undefined {
  // Check for bundled daemon binary
  const platform = process.platform;
  const arch = process.arch;
  
  let binaryName = 'utlx-daemon';
  if (platform === 'win32') {
    binaryName += '.exe';
  }
  
  const binaryPath = path.join(
    context.extensionPath,
    'bin',
    `${platform}-${arch}`,
    binaryName
  );
  
  try {
    // Check if file exists and is executable
    require('fs').accessSync(binaryPath, require('fs').constants.X_OK);
    return binaryPath;
  } catch (error) {
    return undefined;
  }
}
```

**syntaxes/xml.tmLanguage.json:**
```json
{
  "name": "XML",
  "scopeName": "text.xml",
  "fileTypes": ["xml", "xsd", "xsl", "xslt"],
  "patterns": [
    {
      "include": "#comment"
    },
    {
      "include": "#processing-instruction"
    },
    {
      "include": "#doctype"
    },
    {
      "include": "#cdata"
    },
    {
      "include": "#tag"
    }
  ],
  "repository": {
    "comment": {
      "name": "comment.block.xml",
      "begin": "<!--",
      "end": "-->",
      "patterns": [
        {
          "name": "invalid.illegal.bad-comments-or-CDATA.xml",
          "match": "--"
        }
      ]
    },
    "processing-instruction": {
      "name": "meta.tag.preprocessor.xml",
      "begin": "(<\\?)\\s*([-_a-zA-Z0-9]+)",
      "captures": {
        "1": {
          "name": "punctuation.definition.tag.xml"
        },
        "2": {
          "name": "entity.name.tag.xml"
        }
      },
      "end": "(\\?>)",
      "patterns": [
        {
          "match": "\\s+([a-zA-Z-]+)",
          "name": "entity.other.attribute-name.xml"
        },
        {
          "include": "#string"
        }
      ]
    },
    "doctype": {
      "name": "meta.tag.sgml.doctype.xml",
      "begin": "<!DOCTYPE",
      "end": ">",
      "patterns": [
        {
          "include": "#string"
        }
      ]
    },
    "cdata": {
      "name": "string.unquoted.cdata.xml",
      "begin": "<!\\[CDATA\\[",
      "end": "\\]\\]>"
    },
    "tag": {
      "patterns": [
        {
          "name": "meta.tag.no-content.xml",
          "begin": "(<)([a-zA-Z_][a-zA-Z0-9_.-]*)(?=\\s|/?>)",
          "beginCaptures": {
            "1": {
              "name": "punctuation.definition.tag.xml"
            },
            "2": {
              "name": "entity.name.tag.xml"
            }
          },
          "end": "(/?>)",
          "endCaptures": {
            "1": {
              "name": "punctuation.definition.tag.xml"
            }
          },
          "patterns": [
            {
              "include": "#attribute"
            }
          ]
        },
        {
          "name": "meta.tag.xml",
          "begin": "(</)([a-zA-Z_][a-zA-Z0-9_.-]*)",
          "beginCaptures": {
            "1": {
              "name": "punctuation.definition.tag.xml"
            },
            "2": {
              "name": "entity.name.tag.xml"
            }
          },
          "end": "(>)",
          "endCaptures": {
            "1": {
              "name": "punctuation.definition.tag.xml"
            }
          }
        }
      ]
    },
    "attribute": {
      "patterns": [
        {
          "match": "\\s+([a-zA-Z_][a-zA-Z0-9_.-]*)\\s*(=)",
          "captures": {
            "1": {
              "name": "entity.other.attribute-name.xml"
            },
            "2": {
              "name": "punctuation.separator.key-value.xml"
            }
          }
        },
        {
          "include": "#string"
        }
      ]
    },
    "string": {
      "patterns": [
        {
          "name": "string.quoted.double.xml",
          "begin": "\"",
          "end": "\"",
          "patterns": [
            {
              "name": "constant.character.entity.xml",
              "match": "&[a-zA-Z0-9]+;|&#[0-9]+;|&#x[0-9a-fA-F]+;"
            }
          ]
        },
        {
          "name": "string.quoted.single.xml",
          "begin": "'",
          "end": "'",
          "patterns": [
            {
              "name": "constant.character.entity.xml",
              "match": "&[a-zA-Z0-9]+;|&#[0-9]+;|&#x[0-9a-fA-F]+;"
            }
          ]
        }
      ]
    }
  }
}
```

---

## Monaco Editor Integration (Web-Based)

For web-based IDEs using Monaco Editor:

```typescript
import * as monaco from 'monaco-editor';
import { MonacoLanguageClient } from 'monaco-languageclient';
import { 
  CloseAction, 
  ErrorAction, 
  WebSocketMessageReader, 
  WebSocketMessageWriter 
} from 'vscode-languageclient';

// Register XML language
monaco.languages.register({ id: 'xml' });

// Set up language configuration
monaco.languages.setLanguageConfiguration('xml', {
  comments: {
    blockComment: ['<!--', '-->']
  },
  brackets: [
    ['<', '>'],
    ['"', '"'],
    ["'", "'"]
  ],
  autoClosingPairs: [
    { open: '<', close: '>' },
    { open: '"', close: '"' },
    { open: "'", close: "'" }
  ],
  surroundingPairs: [
    { open: '<', close: '>' },
    { open: '"', close: '"' },
    { open: "'", close: "'" }
  ]
});

// Register UTLX language
monaco.languages.register({ id: 'utlx' });

monaco.languages.setLanguageConfiguration('utlx', {
  comments: {
    lineComment: '#',
    blockComment: ['/*', '*/']
  },
  brackets: [
    ['{', '}'],
    ['[', ']'],
    ['(', ')']
  ],
  autoClosingPairs: [
    { open: '{', close: '}' },
    { open: '[', close: ']' },
    { open: '(', close: ')' },
    { open: '"', close: '"' },
    { open: "'", close: "'" }
  ]
});

// Connect to UTL-X LSP daemon via WebSocket
function connectToLanguageServer(editorUrl: string) {
  const webSocket = new WebSocket('ws://localhost:8080/utlx-lsp');

  webSocket.onopen = () => {
    const reader = new WebSocketMessageReader(webSocket);
    const writer = new WebSocketMessageWriter(webSocket);
    
    const languageClient = new MonacoLanguageClient({
      name: 'UTL-X Language Client',
      clientOptions: {
        documentSelector: ['utlx', 'xml'],
        errorHandler: {
          error: () => ErrorAction.Continue,
          closed: () => CloseAction.DoNotRestart
        }
      },
      connectionProvider: {
        get: (errorHandler, closeHandler) => {
          return Promise.resolve({
            reader,
            writer
          });
        }
      }
    });
    
    languageClient.start();
    
    console.log('Connected to UTL-X Language Server');
  };

  webSocket.onerror = (error) => {
    console.error('WebSocket error:', error);
  };

  webSocket.onclose = () => {
    console.log('WebSocket connection closed');
  };
}

// Usage
const editor = monaco.editor.create(document.getElementById('container'), {
  value: '<?xml version="1.0"?>\n<root>\n  <element/>\n</root>',
  language: 'xml',
  theme: 'vs-dark'
});

connectToLanguageServer(window.location.href);
```

**WebSocket Server (in LSP Daemon):**

```kotlin
// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/WebSocketServer.kt
package org.apache.utlx.daemon

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

class WebSocketLspServer(private val languageServer: UtlxLanguageServer) {
    
    fun start(port: Int = 8080) {
        embeddedServer(Netty, port = port) {
            install(WebSockets)
            
            routing {
                webSocket("/utlx-lsp") {
                    println("Client connected via WebSocket")
                    
                    try {
                        incoming.consumeEach { frame ->
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                // Parse JSON-RPC message
                                val response = languageServer.handleMessage(message)
                                // Send response
                                send(Frame.Text(response))
                            }
                        }
                    } catch (e: Exception) {
                        println("WebSocket error: ${e.message}")
                    } finally {
                        println("Client disconnected")
                    }
                }
            }
        }.start(wait = true)
    }
}
```

---

## Alternative Options

### Option 1: Use XML Tools Extension (MIT License)

If you want basic XML support without validation:

```json
// In package.json extensionDependencies
"extensionDependencies": [
  "DotJoshJohnson.xml"  // XML Tools - MIT licensed
]
```

**XML Tools provides:**
- XML formatting
- XPath evaluation
- Basic validation
- **License: MIT** (fully compatible with AGPL)

**Limitations:**
- No XSD schema validation
- No completion based on schemas
- Limited to basic XML features

### Option 2: Minimal Syntax Highlighting Only

Provide only syntax highlighting, let users install validation separately:

- Create basic TextMate grammar (shown above)
- No LSP features for XML
- Focus only on UTLX language support
- Document that users can install separate XML extensions

---

## License Compatibility Summary

| Component | License | Compatible with AGPL? | Notes |
|-----------|---------|----------------------|-------|
| **Apache Xerces** | Apache 2.0 | ✅ Yes | Built into Java, zero dependencies |
| **Java stdlib (javax.xml)** | GPL + Classpath Exception | ✅ Yes | Part of JDK, always usable |
| **Saxon-HE** | MPL 2.0 | ✅ Yes | Via secondary license provisions |
| **XMLBeans** | Apache 2.0 | ✅ Yes | Apache project |
| **XML Tools (VS Code)** | MIT | ✅ Yes | Permissive license |
| **LemMinX** | EPL 2.0 | ❌ No | Incompatible with AGPL |
| **VS Code Extension API** | MIT | ✅ Yes | Extension marketplace license |

---

## Implementation Checklist

### Phase 1: LSP Daemon Extensions (1-2 weeks)
- [ ] Add `XmlLanguageFeatures.kt` to daemon module
- [ ] Implement XML validation using Xerces
- [ ] Implement XSD schema parsing
- [ ] Add XML completion provider
- [ ] Add XML hover provider
- [ ] Write unit tests for XML features
- [ ] Add conformance tests for XML validation

### Phase 2: VS Code Extension (1 week)
- [ ] Create `utlx-vscode` repository
- [ ] Set up TypeScript project structure
- [ ] Implement LSP client in `extension.ts`
- [ ] Create XML TextMate grammar
- [ ] Create UTLX TextMate grammar
- [ ] Add configuration options
- [ ] Write extension documentation
- [ ] Test on Windows, macOS, Linux

### Phase 3: Monaco Integration (Optional, 3-5 days)
- [ ] Add WebSocket server to LSP daemon
- [ ] Create Monaco language configurations
- [ ] Implement WebSocket LSP client
- [ ] Test in browser environment
- [ ] Write web integration documentation

### Phase 4: Testing & Documentation (1 week)
- [ ] End-to-end testing with real XML/XSD files
- [ ] Performance testing with large XML files
- [ ] Update user documentation
- [ ] Create video tutorials
- [ ] Prepare marketplace assets
- [ ] Beta testing with users

---

## Benefits of This Approach

### Technical Benefits
1. **Single Codebase**: One LSP daemon serves all editors (VS Code, IntelliJ, Vim, web)
2. **Proven Architecture**: Leverages existing LSP infrastructure (84/84 tests passing)
3. **Standard Compliance**: Uses standard Java XML APIs
4. **Performance**: Built-in Java APIs are optimized and fast
5. **Extensibility**: Easy to add DTD, RelaxNG, Schematron support later

### Licensing Benefits
1. **No EPL Issues**: Apache 2.0 is fully compatible with AGPL 3.0
2. **No Legal Risk**: Clear one-way compatibility
3. **No Dependencies**: Built into Java, zero licensing complexity
4. **Future-Proof**: Won't face licensing issues as project grows

### User Benefits
1. **Consistent Experience**: Same features across all editors
2. **Offline Work**: No external services required
3. **Fast**: Local validation, no network latency
4. **Privacy**: No data sent to external servers
5. **Free**: No API keys or subscriptions needed

---

## Comparison: Build vs Use LemMinX

| Aspect | Build with Xerces | Use LemMinX |
|--------|------------------|-------------|
| **License** | ✅ Apache 2.0 (compatible) | ❌ EPL 2.0 (incompatible) |
| **Distribution** | ✅ Can bundle with AGPL | ❌ Cannot bundle |
| **Control** | ✅ Full control | ⚠️ External dependency |
| **Features** | ⭐⭐⭐ Core features | ⭐⭐⭐⭐⭐ Full features |
| **Development Time** | 2-3 weeks | Already done (but can't use) |
| **Maintenance** | ⚠️ Must maintain | ✅ Maintained by Red Hat |
| **Integration** | ✅ Native to UTL-X | ⚠️ Separate process |

**Verdict:** Build with Xerces. The licensing issue alone makes LemMinX unusable, and the development time is reasonable given you already have LSP infrastructure.

---

## Resources

### Documentation
- [Java XML Validation API](https://docs.oracle.com/javase/8/docs/api/javax/xml/validation/package-summary.html)
- [Apache Xerces Documentation](https://xerces.apache.org/xerces2-j/)
- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [VS Code Extension API](https://code.visualstudio.com/api)
- [Monaco Editor](https://microsoft.github.io/monaco-editor/)

### License Information
- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [AGPL 3.0](https://www.gnu.org/licenses/agpl-3.0.en.html)
- [Apache/GPL Compatibility](https://www.apache.org/licenses/GPL-compatibility.html)

### Example Projects
- [Eclipse LSP4J](https://github.com/eclipse/lsp4j) - Java LSP implementation
- [VS Code Extension Samples](https://github.com/microsoft/vscode-extension-samples)
- [Monaco Language Client](https://github.com/TypeFox/monaco-languageclient)

---

## Conclusion

**Recommended Path Forward:**

1. ✅ Extend your existing UTL-X LSP daemon with XML/XSD support using Apache Xerces
2. ✅ Create a lightweight VS Code extension that connects to the daemon
3. ✅ Support Monaco Editor via WebSocket for web-based scenarios
4. ✅ Maintain clean licensing (Apache 2.0 → AGPL 3.0)

This gives you full XML/XSD editing capabilities while staying 100% compatible with AGPL licensing, leveraging the LSP infrastructure you've already built.

**Total Implementation Time:** ~3-4 weeks for full feature parity with basic XML editors

**Result:** Professional XML/XSD editing support in UTL-X without any licensing complications.
