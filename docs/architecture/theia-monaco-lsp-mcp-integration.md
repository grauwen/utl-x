# Theia Monaco Editor, LSP, and MCP Integration Guide

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Integration Architecture

---

## Table of Contents

1. [Monaco Editor & LSP Integration](#monaco-editor--lsp-integration)
2. [MCP vs LSP - Separation of Concerns](#mcp-vs-lsp---separation-of-concerns)
3. [Architecture Overview](#architecture-overview)
4. [VS Code Plugin Compatibility](#vs-code-plugin-compatibility)
5. [Implementation Details](#implementation-details)

---

## Question 1: Monaco Editor & LSP - Does it Redirect Through MCP?

### Short Answer
**NO** - LSP and MCP are **separate, parallel systems** with different responsibilities.

```
Monaco Editor
    ├─→ LSP Client → UTL-X Language Server (Daemon)
    │                  ↓
    │               (UTLX autocomplete, validation, hover)
    │
    └─→ MCP Client → MCP Server → LLM
                       ↓
                   (AI generation, schema analysis)
```

### Detailed Explanation

#### LSP (Language Server Protocol)
**Purpose**: Code intelligence for UTLX editor

**Responsibilities**:
- ✅ Syntax highlighting
- ✅ Autocomplete (IntelliSense)
- ✅ Error diagnostics (red squiggles)
- ✅ Hover information
- ✅ Go to definition
- ✅ Find references
- ✅ Code formatting

**Flow**:
```
Monaco Editor (UTLX file open)
    ↓ LSP Protocol (JSON-RPC)
UTL-X Language Server (Daemon)
    ↓
Type checking, validation, autocomplete
```

---

#### MCP (Model Context Protocol)
**Purpose**: AI-powered features

**Responsibilities**:
- ✅ Generate UTLX from natural language
- ✅ Analyze schema compatibility
- ✅ Suggest transformation variants
- ✅ Provide function recommendations

**Flow**:
```
AI Assistant Panel (user types prompt)
    ↓ MCP Protocol (JSON-RPC)
MCP Server
    ↓ calls tools ↓
UTL-X Daemon (for validation/execution)
LLM API (for generation)
    ↓
Generated UTLX code
```

---

### They Work Together, Not Through Each Other

```
┌────────────────────────────────────────────────────┐
│              Theia IDE (Browser)                   │
│                                                    │
│  ┌──────────────────┐      ┌──────────────────┐  │
│  │  Monaco Editor   │      │  AI Assistant    │  │
│  │  (UTLX code)     │      │  Panel           │  │
│  └────────┬─────────┘      └────────┬─────────┘  │
│           │                          │             │
│           │ LSP                      │ MCP         │
│           │ (code intelligence)      │ (AI gen)    │
└───────────┼──────────────────────────┼─────────────┘
            │                          │
            ↓                          ↓
┌───────────────────┐      ┌──────────────────┐
│  UTL-X Daemon     │      │  MCP Server      │
│  (LSP Server)     │      │                  │
│                   │      │  ┌─────────────┐ │
│  • Autocomplete   │      │  │ LLM Client  │ │
│  • Validation     │◄─────┼──┤(calls tools)│ │
│  • Hover info     │ REST │  └─────────────┘ │
│  • Diagnostics    │ API  │                  │
└───────────────────┘      └──────────────────┘

NOTE: MCP Server → Daemon uses REST API (NOT LSP!)
See: lsp-communication-patterns-clarification.md
```

**Key Insight**: LSP and MCP are **independent** - they both connect to the daemon but for different purposes.

---

## MCP vs LSP - Separation of Concerns

### LSP: Code Editor Intelligence (Always On)

**When**: User is typing UTLX code
**What**: Real-time code assistance
**Examples**:
- User types `$input.` → LSP provides field autocomplete
- User types `map(` → LSP shows parameter hints
- User has syntax error → LSP shows red squiggle

**Protocol**: LSP (Language Server Protocol)
**Connection**: Monaco Editor ↔ UTL-X Daemon (LSP mode)

---

### MCP: AI Generation (On Demand)

**When**: User requests AI assistance
**What**: Generate/analyze UTLX
**Examples**:
- User types prompt: "Convert orders to invoices"
- User clicks "Analyze Schemas"
- User asks "What functions can I use for dates?"

**Protocol**: MCP (Model Context Protocol)
**Connection**: AI Panel ↔ MCP Server ↔ LLM

---

### Why Not Route LSP Through MCP?

**Bad Idea** ❌:
```
Monaco Editor → MCP Server → Daemon (LSP)
```

**Problems**:
- 🐌 **Performance**: Extra hop slows autocomplete (needs <100ms)
- 🔀 **Complexity**: MCP server adds unnecessary layer
- 🎯 **Purpose mismatch**: MCP is for AI, not code intelligence
- 💰 **Cost**: Would trigger LLM calls for every keystroke

**Good Design** ✅:
```
Monaco Editor → Daemon (LSP)     [Fast, direct]
AI Panel      → MCP Server       [Separate, AI-specific]
```

---

## Architecture Overview

### Component Responsibilities

```typescript
// Monaco Editor Component
class UTLXMonacoEditor {
  private lspClient: LanguageClient;  // Direct to daemon
  private mcpClient?: MCPClient;      // NOT used by editor

  async initialize() {
    // Connect to UTL-X daemon for LSP
    this.lspClient = new LanguageClient({
      serverUri: 'ws://localhost:7778/lsp'
    });

    // Register LSP features
    this.lspClient.onCompletion(...);
    this.lspClient.onHover(...);
    this.lspClient.onDiagnostics(...);
  }

  // MCP is NOT involved in editor operations
}

// AI Assistant Panel Component
class AIAssistantPanel {
  private mcpClient: MCPClient;       // To MCP server
  private lspClient?: LanguageClient; // NOT used by AI panel

  async generateTransformation(prompt: string) {
    // Call MCP server for AI generation
    const result = await this.mcpClient.call('generate_transformation', {
      prompt,
      inputSchema: this.inputSchema
    });

    // Insert generated UTLX into Monaco editor
    this.editorRef.insertText(result.transformation);
  }

  // LSP is NOT involved in AI operations
}
```

---

### Data Flow Examples

#### Example 1: User Types Code (LSP Only)

```
User types: $input.Customer.

1. Monaco Editor captures keystroke
   ↓
2. LSP Client sends textDocument/completion request
   ↓
3. UTL-X Daemon (LSP mode) responds with field suggestions:
   ["Name", "Email", "Address"]
   ↓
4. Monaco shows autocomplete dropdown

MCP Server: NOT INVOLVED
```

---

#### Example 2: User Requests AI Generation (MCP Only)

```
User types in AI panel: "Convert XML orders to JSON invoices"

1. AI Assistant Panel captures prompt
   ↓
2. MCP Client sends generate_transformation request
   ↓
3. MCP Server:
   a. Calls get_input_schema(order.xsd)
   b. Calls LLM with prompt + schema context
   c. Gets generated UTLX from LLM
   d. Calls validate_utlx(generated code) via daemon
   ↓
4. Returns generated UTLX to AI Panel
   ↓
5. AI Panel inserts into Monaco Editor
   ↓
6. NOW LSP kicks in: validates the inserted code

LSP: Only involved at step 6 (after insertion)
```

---

## VS Code Plugin Compatibility

### Question 2: Can We Reuse VS Code Plugins in Theia?

**Short Answer**: **PARTIALLY** - Theia has VS Code plugin compatibility, but with considerations.

### Theia's VS Code Extension Support

Theia supports **most** VS Code extensions via:
- **Built-in compatibility layer**
- **VS Code Extension Protocol**
- **Extension host process**

```
Theia IDE
  ├─→ Native Theia Extensions (TypeScript, Theia API)
  └─→ VS Code Extensions (via compatibility layer)
```

---

### What VS Code Extensions Work in Theia?

#### ✅ **Fully Compatible**
- **Language extensions** (syntax highlighting, LSP-based)
- **Theme extensions**
- **Snippet extensions**
- **Formatter extensions** (Prettier, ESLint)
- **Git extensions**
- **Debugger extensions** (most DAP-based)

#### ⚠️ **Partially Compatible**
- **UI-heavy extensions** (may need adaptation)
- **Webview extensions** (some work, some don't)
- **Extensions using VS Code-specific APIs**

#### ❌ **Not Compatible**
- **Extensions using undocumented VS Code APIs**
- **Extensions tightly coupled to VS Code UI**

---

### Relevant for UTL-X: XML/XSD Extensions

If you have documentation about XML/XSD VS Code plugins, here's how they integrate:

#### Example: RedHat XML Extension

**VS Code Extension**:
- `redhat.vscode-xml`
- Provides XML/XSD validation, autocomplete, formatting

**In Theia**:
```typescript
// Theia can load VS Code extensions

// package.json
{
  "extensionDependencies": [
    "redhat.vscode-xml"  // ← VS Code extension!
  ]
}

// Theia will automatically:
1. Download extension from VS Code marketplace
2. Load it via compatibility layer
3. Provide XML/XSD features
```

**Benefits**:
- ✅ Reuse existing XML validation
- ✅ XSD autocomplete in input/output panels
- ✅ Schema validation for loaded schemas
- ✅ No need to reimplement

---

### UTL-X-Specific Theia Extension

For UTLX-specific features, create a **native Theia extension**:

```typescript
// utlx-theia-extension/src/browser/utlx-contribution.ts
import { injectable } from 'inversify';
import { CommandContribution, CommandRegistry } from '@theia/core';
import { LanguageClientContribution } from '@theia/languages/lib/browser';

@injectable()
export class UTLXLanguageClientContribution implements LanguageClientContribution {
  // Connect Monaco to UTL-X LSP daemon
  readonly languageId = 'utlx';

  async createLanguageClient(): Promise<LanguageClient> {
    return new LanguageClient(
      this.languageId,
      'UTL-X Language Server',
      {
        serverOptions: {
          run: {
            command: 'java',
            args: ['-jar', 'utlx-daemon.jar', '--lsp']
          }
        },
        clientOptions: {
          documentSelector: [{ language: 'utlx' }]
        }
      }
    );
  }
}

// AI Assistant Panel (separate)
@injectable()
export class AIAssistantPanelContribution implements FrontendApplicationContribution {
  private mcpClient: MCPClient;

  async initialize() {
    // Connect to MCP server (NOT via LSP!)
    this.mcpClient = new MCPClient('http://localhost:7779');
  }

  async generateTransformation(prompt: string) {
    const result = await this.mcpClient.call('generate', { prompt });
    // Insert into editor
  }
}
```

---

### Recommended Architecture

```
Theia IDE
  ├─→ UTL-X Native Extension (UTLX language support)
  │   └─→ Connects to: UTL-X Daemon (LSP)
  │
  ├─→ AI Assistant Panel (UTLX AI features)
  │   └─→ Connects to: MCP Server
  │
  ├─→ RedHat XML Extension (VS Code, loaded via compatibility)
  │   └─→ Provides: XML/XSD validation, autocomplete
  │
  └─→ Other VS Code Extensions (Prettier, Git, etc.)
```

---

## Implementation Details

### Monaco Editor LSP Setup

```typescript
// monaco-lsp-setup.ts
import * as monaco from 'monaco-editor';
import { MonacoLanguageClient } from 'monaco-languageclient';
import { WebSocketMessageReader, WebSocketMessageWriter, toSocket } from 'vscode-ws-jsonrpc';

export function setupUTLXLanguageClient() {
  // Create WebSocket connection to UTL-X daemon
  const websocket = new WebSocket('ws://localhost:7778/lsp');

  monaco.languages.register({ id: 'utlx' });

  websocket.onopen = () => {
    const socket = toSocket(websocket);
    const reader = new WebSocketMessageReader(socket);
    const writer = new WebSocketMessageWriter(socket);

    const languageClient = new MonacoLanguageClient({
      name: 'UTL-X Language Client',
      clientOptions: {
        documentSelector: ['utlx'],
        errorHandler: {
          error: () => ({ action: ErrorAction.Continue }),
          closed: () => ({ action: CloseAction.DoNotRestart })
        }
      },
      connectionProvider: {
        get: () => Promise.resolve({ reader, writer })
      }
    });

    languageClient.start();
  };
}

// Call this on Theia startup
setupUTLXLanguageClient();
```

---

### MCP Client Setup (Separate!)

```typescript
// mcp-client-setup.ts
export class MCPClient {
  private baseUrl: string;

  constructor(baseUrl: string = 'http://localhost:7779') {
    this.baseUrl = baseUrl;
  }

  async call(method: string, params: any): Promise<any> {
    const response = await fetch(`${this.baseUrl}/mcp`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        jsonrpc: '2.0',
        id: Date.now(),
        method,
        params
      })
    });

    const result = await response.json();
    return result.result;
  }

  // Specific method for AI generation
  async generateTransformation(request: {
    prompt: string;
    inputSchema?: string;
    outputSchema?: string;
  }): Promise<{ transformation: string; analysis?: any }> {
    return this.call('generate_transformation', request);
  }

  // Schema analysis (no LLM needed!)
  async analyzeSchemas(request: {
    inputSchema: string;
    outputSchema: string;
  }): Promise<any> {
    return this.call('analyze_schema_compatibility', request);
  }
}
```

---

### AI Assistant Panel Component

```typescript
// ai-assistant-panel.tsx
import React, { useState } from 'react';
import { MCPClient } from './mcp-client-setup';

export const AIAssistantPanel: React.FC = () => {
  const [prompt, setPrompt] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const mcpClient = new MCPClient();

  const handleGenerate = async () => {
    setLoading(true);
    try {
      const response = await mcpClient.generateTransformation({
        prompt,
        inputSchema: getInputSchemaFromEditor(),
        outputSchema: getOutputSchemaFromEditor()
      });

      setResult(response.transformation);

      // Insert into Monaco editor
      insertIntoEditor(response.transformation);
    } catch (error) {
      console.error('Generation failed:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="ai-assistant-panel">
      <h2>AI Assistant</h2>

      <textarea
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        placeholder="Describe your transformation..."
      />

      <button onClick={handleGenerate} disabled={loading}>
        {loading ? 'Generating...' : 'Generate UTLX'}
      </button>

      {result && (
        <div className="result">
          <h3>Generated Transformation:</h3>
          <pre>{result}</pre>
        </div>
      )}
    </div>
  );
};
```

---

## Summary

### Question 1: Monaco & LSP Through MCP?
**Answer**: **NO** - They are separate systems

- **LSP**: Monaco → Daemon (code intelligence)
- **MCP**: AI Panel → MCP Server → LLM (AI generation)
- **Both** use daemon, but independently

### Question 2: VS Code Plugin Compatibility?
**Answer**: **YES, MOSTLY** - Theia supports VS Code extensions

- ✅ XML/XSD extensions work via compatibility layer
- ✅ Language extensions, themes, snippets all work
- ⚠️ UI-heavy extensions may need adaptation
- 🎯 Create native Theia extension for UTLX-specific features

### Architecture Recommendation

```
Theia IDE
  ├─→ UTLX Native Extension
  │   ├─→ Monaco Editor + LSP → Daemon
  │   └─→ AI Assistant Panel + MCP → MCP Server
  │
  └─→ RedHat XML Extension (VS Code)
      └─→ XML/XSD validation (reused!)
```

**Key Principle**: Keep LSP and MCP **separate and parallel** - they serve different purposes and shouldn't be conflated.
