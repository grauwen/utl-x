# USDL Directives Tab Implementation Plan

**Feature:** Add USDL Directives tab to Function Builder for Tier 2+ schema formats

**Status:** Planned
**Date:** 2025-01-18
**Author:** Claude Code

---

## Table of Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Architecture](#architecture)
4. [Architecture Verification](#architecture-verification)
5. [Implementation Plan](#implementation-plan)
6. [File Changes](#file-changes)
7. [Testing Plan](#testing-plan)
8. [Success Criteria](#success-criteria)

---

## Overview

### Problem Statement

Currently, the Function Builder in the Theia extension provides 3 tabs:
1. Standard Library Functions
2. Available Inputs
3. Operators

When users are working with Tier 2+ schema formats (avro, jsch, xsd, proto), they need access to USDL (Universal Schema Definition Language) directives to define schema metadata. These directives are not currently accessible from the Function Builder.

### Proposed Solution

Add a **4th tab "USDL Directives"** to the Function Builder that:
- Conditionally appears when output format is Tier 2+ (avro, jsch, xsd, proto)
- Fetches directives from the existing `/api/usdl/directives` REST endpoint
- Displays directives grouped by tier/scope with the same UX as existing tabs
- Allows insertion of directives into Monaco editor using codicon buttons
- Includes comprehensive logging for debugging

### Benefits

- **Improved Developer Experience**: Easy access to all 130 USDL directives
- **Context-Aware**: Only shows directives supported by current output format
- **Consistency**: Follows same UI/UX patterns as existing tabs
- **Discoverability**: Examples, syntax, and documentation built-in
- **Efficiency**: Quick insertion with proper formatting

---

## Requirements

### Functional Requirements

1. **FR-1**: Tab must appear only when output format is one of: `avro`, `jsch`, `xsd`, `proto`
2. **FR-2**: Tab must hide when output format changes to Tier 1 formats (json, xml, csv, yaml)
3. **FR-3**: Directives must be fetched from `/api/usdl/directives` REST endpoint
4. **FR-4**: Directives must be filtered to show only those supported by current output format
5. **FR-5**: Directives must be grouped by tier (core, common, format_specific, reserved)
6. **FR-6**: Each directive must display: name, description, syntax, examples, scopes, value type
7. **FR-7**: Insert button must add directive template at cursor position in Monaco editor
8. **FR-8**: Copy button must copy directive template to clipboard
9. **FR-9**: Search functionality must filter directives by name/description
10. **FR-10**: Split view between directive list and details panel (similar to functions tab)

### Non-Functional Requirements

1. **NFR-1**: Architecture must follow same pattern as functions/operators tabs
2. **NFR-2**: Response time for tab switch must be < 100ms (directives pre-cached)
3. **NFR-3**: Comprehensive logging at all layers (daemon, service, component)
4. **NFR-4**: Graceful error handling if daemon is unavailable
5. **NFR-5**: UI must be consistent with existing Theia/VSCode design patterns
6. **NFR-6**: TypeScript types must match REST API response schema exactly

---

## Architecture

### System Architecture Diagram

```
╔══════════════════════════════════════════════════════════════════════════════╗
║                           CHROMIUM PROCESS (Frontend)                         ║
║                    Electron/Theia Browser Application Layer                  ║
╠══════════════════════════════════════════════════════════════════════════════╣
║                                                                               ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  UTLX Editor Widget  (utlx-editor-widget.tsx)                       │    ║
║  │  ────────────────────────────────────────────────────────────────   │    ║
║  │                                                                      │    ║
║  │  State:                          Methods:                           │    ║
║  │  • outputFormat: string          • openFunctionBuilder()            │    ║
║  │  • functionBuilderFunctions[]    • handleInsertFromBuilder()        │    ║
║  │  • functionBuilderOperators[]    ┌──────────────────────────────┐   │    ║
║  │  • functionBuilderDirectives ──▶ │ NEW! getUsdlDirectives()     │   │    ║
║  │  • inputUdmMap                   └──────────────────────────────┘   │    ║
║  │  • inputFormatsMap                                                  │    ║
║  │                                                                      │    ║
║  │  ┌────────────────────────────────────────────────────────────┐    │    ║
║  │  │  Monaco Editor (Expression Editor)                         │    │    ║
║  │  │  • UTLX transformation code                                │    │    ║
║  │  │  • Syntax highlighting, autocomplete                       │    │    ║
║  │  └────────────────────────────────────────────────────────────┘    │    ║
║  └──────────────────────────────┬───────────────────────────────────────┘    ║
║                                 │                                            ║
║                                 │ opens                                      ║
║                                 ▼                                            ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  Function Builder Dialog  (function-builder-dialog.tsx)            │    ║
║  │  ─────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  Props:                                                              │    ║
║  │  • functions: FunctionInfo[]                                        │    ║
║  │  • operators: OperatorInfo[]                                        │    ║
║  │  • directiveRegistry: DirectiveRegistry  ◀── NEW!                   │    ║
║  │  • outputFormat: string                  ◀── NEW!                   │    ║
║  │  • inputFormatsMap                                                  │    ║
║  │  • udmMap                                                            │    ║
║  │                                                                      │    ║
║  │  ┌──────────────────────────────────────────────────────────────┐  │    ║
║  │  │  TAB HEADER                                                   │  │    ║
║  │  │  ┌─────────────┬───────────────┬───────────┬──────────────┐  │  │    ║
║  │  │  │ Standard    │  Available    │ Operators │ USDL         │  │  │    ║
║  │  │  │ Library     │  Inputs       │           │ Directives   │  │  │    ║
║  │  │  │  (Tab 1)    │  (Tab 2)      │ (Tab 3)   │ (Tab 4) ◀NEW │  │  │    ║
║  │  │  └─────────────┴───────────────┴───────────┴──────────────┘  │  │    ║
║  │  │                                              ▲                 │  │    ║
║  │  │           Conditional: only if ─────────────┘                 │  │    ║
║  │  │           outputFormat in ['avro','jsch','xsd','proto']       │  │    ║
║  │  └──────────────────────────────────────────────────────────────┘  │    ║
║  │                                                                      │    ║
║  │  ┌──────────────────────────────────────────────────────────────┐  │    ║
║  │  │  TAB CONTENT                                                  │  │    ║
║  │  │                                                               │  │    ║
║  │  │  Tab 1: Functions grouped by category                        │  │    ║
║  │  │         (Array, String, Math, Date, etc.)                    │  │    ║
║  │  │                                                               │  │    ║
║  │  │  Tab 2: FieldTree Component                                  │  │    ║
║  │  │         • Parses UDM from inputs                             │  │    ║
║  │  │         • Shows nested field structure                       │  │    ║
║  │  │         • Sample data preview                                │  │    ║
║  │  │                                                               │  │    ║
║  │  │  Tab 3: OperatorsTree Component                              │  │    ║
║  │  │         • Arithmetic, Comparison, Logical                    │  │    ║
║  │  │         • Operator details (precedence, associativity)       │  │    ║
║  │  │                                                               │  │    ║
║  │  │  Tab 4: DirectivesTree Component  ◀── NEW!                   │  │    ║
║  │  │         ┌─────────────────────────────────────────────────┐  │  │    ║
║  │  │         │ • Grouped by tier (core/common/specific/reserved) │  │    ║
║  │  │         │ • Filtered by outputFormat support              │  │    ║
║  │  │         │ • Split view: list + details panel              │  │    ║
║  │  │         │ • Insert/Copy with codicons                     │  │    ║
║  │  │         │ • Search functionality                          │  │    ║
║  │  │         └─────────────────────────────────────────────────┘  │  │    ║
║  │  └──────────────────────────────────────────────────────────────┘  │    ║
║  │                                                                      │    ║
║  │  Actions:                                                            │    ║
║  │  • Insert → insertIntoMonaco(code)                                  │    ║
║  │  • Copy → navigator.clipboard.writeText()                           │    ║
║  └──────────────────────────────────────────────────────────────────────┘    ║
║                                                                               ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║                                                                               ║
║                         IPC / JSON-RPC BOUNDARY                               ║
║                    (Chromium ◀──▶ Node.js via Electron IPC)                  ║
║                                                                               ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║                             NODE.JS PROCESS (Backend)                         ║
║                         Theia Backend Services Layer                          ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║                                                                               ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  UTLXService Interface  (utlx-service.ts)                           │    ║
║  │  ──────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  Methods (contracts):                                                │    ║
║  │  • parse(source: string): Promise<ParseResult>                      │    ║
║  │  • validate(source: string): Promise<ValidationResult>              │    ║
║  │  • execute(source, inputs): Promise<ExecutionResult>                │    ║
║  │  • getFunctions(): Promise<FunctionInfo[]>                          │    ║
║  │  • getOperators(): Promise<OperatorInfo[]>                          │    ║
║  │  • getUsdlDirectives(): Promise<DirectiveRegistry>  ◀── NEW!        │    ║
║  │  • inferSchema(...): Promise<InferSchemaResult>                     │    ║
║  │  • validateUdm(...): Promise<ValidateUdmResult>                     │    ║
║  └──────────────────────────────┬───────────────────────────────────────┘    ║
║                                 │                                            ║
║                                 │ implements                                 ║
║                                 ▼                                            ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  UTLXServiceImpl  (utlx-service-impl.ts)                            │    ║
║  │  ──────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  Dependencies:                                                       │    ║
║  │  • daemonClient: UTLXDaemonClient  (injected)                       │    ║
║  │                                                                      │    ║
║  │  async getFunctions(): Promise<FunctionInfo[]> {                    │    ║
║  │      try {                                                           │    ║
║  │          return await this.daemonClient.getFunctions();             │    ║
║  │      } catch (error) {                                               │    ║
║  │          console.error('Get functions error:', error);              │    ║
║  │          return [];                                                  │    ║
║  │      }                                                               │    ║
║  │  }                                                                   │    ║
║  │                                                                      │    ║
║  │  async getOperators(): Promise<OperatorInfo[]> {                    │    ║
║  │      try {                                                           │    ║
║  │          return await this.daemonClient.getOperators();             │    ║
║  │      } catch (error) {                                               │    ║
║  │          console.error('Get operators error:', error);              │    ║
║  │          return [];                                                  │    ║
║  │      }                                                               │    ║
║  │  }                                                                   │    ║
║  │                                                                      │    ║
║  │  ┌────────────────────────────────────────────────────────────┐    │    ║
║  │  │ async getUsdlDirectives(): Promise<DirectiveRegistry> {    │    │    ║
║  │  │     try {                                           ◀── NEW!│    │    ║
║  │  │         return await this.daemonClient.getUsdlDirectives();│    │    ║
║  │  │     } catch (error) {                                      │    │    ║
║  │  │         console.error('Get USDL directives error:', error);│    │    ║
║  │  │         return emptyRegistry;                              │    │    ║
║  │  │     }                                                       │    │    ║
║  │  │ }                                                           │    │    ║
║  │  └────────────────────────────────────────────────────────────┘    │    ║
║  └──────────────────────────────┬───────────────────────────────────────┘    ║
║                                 │                                            ║
║                                 │ uses                                       ║
║                                 ▼                                            ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  UTLXDaemonClient  (utlx-daemon-client.ts)                          │    ║
║  │  ──────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  Configuration:                                                      │    ║
║  │  • apiBaseUrl: http://localhost:7779                                │    ║
║  │  • requestTimeout: 30000ms                                          │    ║
║  │                                                                      │    ║
║  │  HTTP Client Methods:                                                │    ║
║  │                                                                      │    ║
║  │  async getFunctions(): Promise<FunctionInfo[]> {                    │    ║
║  │      const resp = await this.httpRequest('/api/functions', 'GET'); │    ║
║  │      return resp.functions || [];                                   │    ║
║  │  }                                                                   │    ║
║  │                                                                      │    ║
║  │  async getOperators(): Promise<OperatorInfo[]> {                    │    ║
║  │      const resp = await this.httpRequest('/api/operators', 'GET'); │    ║
║  │      return resp.operators || [];                                   │    ║
║  │  }                                                                   │    ║
║  │                                                                      │    ║
║  │  ┌────────────────────────────────────────────────────────────┐    │    ║
║  │  │ async getUsdlDirectives(): Promise<DirectiveRegistry> {    │    │    ║
║  │  │     const resp = await this.httpRequest(           ◀── NEW!│    │    ║
║  │  │         '/api/usdl/directives', 'GET'                      │    │    ║
║  │  │     );                                                      │    │    ║
║  │  │     return resp; // Full DirectiveRegistry object          │    │    ║
║  │  │ }                                                           │    │    ║
║  │  └────────────────────────────────────────────────────────────┘    │    ║
║  │                                                                      │    ║
║  │  private async httpRequest(endpoint, method, body?): Promise<any> { │    ║
║  │      const url = `${this.apiBaseUrl}${endpoint}`;                   │    ║
║  │      // HTTP request using Node.js http module                      │    ║
║  │      // Returns parsed JSON response                                │    ║
║  │  }                                                                   │    ║
║  └──────────────────────────────┬───────────────────────────────────────┘    ║
║                                 │                                            ║
╚═════════════════════════════════╪════════════════════════════════════════════╝
                                  │
                                  │ HTTP REST API
                                  │ (GET /api/usdl/directives)
                                  ▼
╔═══════════════════════════════════════════════════════════════════════════════╗
║                         UTLXD DAEMON PROCESS (Java)                           ║
║                   Separate JVM Process (port 7779)                            ║
╠═══════════════════════════════════════════════════════════════════════════════╣
║                                                                               ║
║  Started by: java -jar utlxd-1.0.0-SNAPSHOT.jar start --api --api-port 7779  ║
║                                                                               ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  Ktor REST API Server  (RestApiServer.kt)                           │    ║
║  │  ──────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  Endpoints:                                                          │    ║
║  │                                                                      │    ║
║  │  GET /api/health                → HealthResponse                    │    ║
║  │  GET /api/ping                  → PingResponse                      │    ║
║  │                                                                      │    ║
║  │  GET /api/functions             → FunctionRegistry                  │    ║
║  │      ├─ Call: StandardLibrary.exportRegistry()                      │    ║
║  │      └─ Return: { functions: FunctionInfo[], ... }                  │    ║
║  │                                                                      │    ║
║  │  GET /api/operators             → OperatorRegistry                  │    ║
║  │      ├─ Call: OperatorRegistry.exportRegistry()                     │    ║
║  │      └─ Return: { operators: OperatorInfo[], ... }                  │    ║
║  │                                                                      │    ║
║  │  ┌────────────────────────────────────────────────────────────┐    │    ║
║  │  │ GET /api/usdl/directives    → DirectiveRegistry    ◀── NEW!│    │    ║
║  │  │     ├─ Call: DirectiveRegistry.exportRegistry()            │    │    ║
║  │  │     └─ Return: {                                           │    │    ║
║  │  │           version: "1.0",                                  │    │    ║
║  │  │           totalDirectives: 130,                            │    │    ║
║  │  │           directives: DirectiveInfo[],                     │    │    ║
║  │  │           tiers: {...},                                    │    │    ║
║  │  │           scopes: {...},                                   │    │    ║
║  │  │           formats: {...}                                   │    │    ║
║  │  │       }                                                     │    │    ║
║  │  └────────────────────────────────────────────────────────────┘    │    ║
║  │                                                                      │    ║
║  │  POST /api/validate             → ValidationResponse                │    ║
║  │  POST /api/execute              → ExecutionResponse                 │    ║
║  │  POST /api/execute-multipart    → ExecutionResponse                 │    ║
║  │  POST /api/infer-schema         → InferSchemaResponse               │    ║
║  │  POST /api/parse-schema         → ParseSchemaResponse               │    ║
║  │  POST /api/udm/export           → UDMExportResponse                 │    ║
║  │  POST /api/udm/import           → UDMImportResponse                 │    ║
║  │  POST /api/udm/validate         → UDMValidateResponse               │    ║
║  └──────────────────────────────────────────────────────────────────────┘    ║
║                                                                               ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  Domain Registries (Kotlin Objects - Single Source of Truth)        │    ║
║  │  ──────────────────────────────────────────────────────────────────  │    ║
║  │                                                                      │    ║
║  │  StandardLibrary.kt                                                  │    ║
║  │  • exportRegistry(): FunctionRegistryData                           │    ║
║  │  • 636 functions in 12 categories                                   │    ║
║  │                                                                      │    ║
║  │  OperatorRegistry.kt                                                 │    ║
║  │  • exportRegistry(): OperatorRegistryData                           │    ║
║  │  • Arithmetic, Comparison, Logical, Special operators               │    ║
║  │                                                                      │    ║
║  │  ┌────────────────────────────────────────────────────────────┐    │    ║
║  │  │ DirectiveRegistry.kt                            ◀── NEW!   │    │    ║
║  │  │ • exportRegistry(): DirectiveRegistryData                  │    │    ║
║  │  │ • 130 USDL directives                                      │    │    ║
║  │  │ • 4 tiers: core(9), common(51), specific(53), reserved(17)│    │    ║
║  │  │ • 17 format definitions with compatibility metadata       │    │    ║
║  │  │ • FormatCompatibility.kt - Format support percentages      │    │    ║
║  │  │ • USDL10.kt - Core directive definitions                   │    │    ║
║  │  └────────────────────────────────────────────────────────────┘    │    ║
║  └──────────────────────────────────────────────────────────────────────┘    ║
║                                                                               ║
╚═══════════════════════════════════════════════════════════════════════════════╝


LEGEND:
───────
  ◀── NEW!  = New components/methods for USDL Directives feature
  →         = Data flow direction
  ▼ / ▲     = Process flow
  │         = Connection/dependency
```

### Data Flow Sequence

```
┌─────────┐
│  User   │
└────┬────┘
     │ 1. Opens Function Builder
     │
     ▼
┌────────────────────────┐
│ UTLXEditorWidget       │
│                        │
│ if outputFormat in     │
│ ['avro','jsch','xsd',  │
│  'proto']:             │
│   fetch directives     │
└────────┬───────────────┘
         │ 2. utlxService.getUsdlDirectives()
         │
         ▼
┌────────────────────────┐
│ UTLXServiceImpl        │
│                        │
│ wraps daemon call      │
│ + error handling       │
└────────┬───────────────┘
         │ 3. daemonClient.getUsdlDirectives()
         │
         ▼
┌────────────────────────┐
│ UTLXDaemonClient       │
│                        │
│ HTTP GET to daemon     │
└────────┬───────────────┘
         │ 4. GET http://localhost:7779/api/usdl/directives
         │
         ▼
┌────────────────────────┐
│ UTLXD REST API         │
│ (RestApiServer.kt)     │
│                        │
│ DirectiveRegistry      │
│  .exportRegistry()     │
└────────┬───────────────┘
         │ 5. Returns DirectiveRegistryData JSON
         │
         ▼
┌────────────────────────┐
│ Function Builder Dialog│
│                        │
│ Receives:              │
│ • directiveRegistry    │
│ • outputFormat         │
│                        │
│ Shows 4th tab if       │
│ outputFormat is Tier 2+│
└────────┬───────────────┘
         │ 6. User selects directive
         │
         ▼
┌────────────────────────┐
│ DirectivesTree         │
│                        │
│ Filters by outputFormat│
│ Shows tier groups      │
│ Details panel          │
└────────┬───────────────┘
         │ 7. User clicks Insert
         │
         ▼
┌────────────────────────┐
│ insertIntoMonaco()     │
│                        │
│ Adds directive template│
│ at cursor position     │
└────────────────────────┘
```

---

## Architecture Verification

### Pattern Alignment with Functions & Operators

The proposed architecture follows **exactly the same pattern** as the existing Functions and Operators implementation:

#### Current Pattern (Functions & Operators)

```typescript
// 1. Editor Widget
this.functionBuilderFunctions = await this.utlxService.getFunctions();
this.functionBuilderOperators = await this.utlxService.getOperators();

// 2. Service Implementation
async getFunctions(): Promise<FunctionInfo[]> {
    try {
        return await this.daemonClient.getFunctions();
    } catch (error) {
        console.error('Get functions error:', error);
        return [];
    }
}

// 3. Daemon Client
async getFunctions(): Promise<FunctionInfo[]> {
    const response = await this.httpRequest('/api/functions', 'GET');
    return response.functions || [];
}

// 4. REST Endpoint
GET /api/functions → StandardLibrary.exportRegistry()
GET /api/operators → OperatorRegistry.exportRegistry()
```

#### Proposed Pattern (USDL Directives)

```typescript
// 1. Editor Widget
this.functionBuilderDirectives = await this.utlxService.getUsdlDirectives();

// 2. Service Implementation
async getUsdlDirectives(): Promise<DirectiveRegistry> {
    try {
        return await this.daemonClient.getUsdlDirectives();
    } catch (error) {
        console.error('Get USDL directives error:', error);
        return emptyRegistry;
    }
}

// 3. Daemon Client
async getUsdlDirectives(): Promise<DirectiveRegistry> {
    const response = await this.httpRequest('/api/usdl/directives', 'GET');
    return response; // Full DirectiveRegistry object
}

// 4. REST Endpoint (ALREADY IMPLEMENTED)
GET /api/usdl/directives → DirectiveRegistry.exportRegistry()
```

### Key Architecture Consistency Points

✅ **Layer Separation**: Frontend (React) → Service (Node.js) → Daemon (Java)
✅ **Error Handling**: Try/catch at service layer with graceful fallback
✅ **Caching**: Fetched once on Function Builder open, stored in widget state
✅ **REST API**: GET endpoint returns JSON-serializable registry object
✅ **Type Safety**: TypeScript interfaces match Kotlin data classes
✅ **Logging**: Comprehensive logging at each layer

---

## Implementation Plan

### Phase 1: Type Definitions

**File:** `theia-extension/utlx-theia-extension/src/common/usdl-types.ts` (NEW)

```typescript
/**
 * USDL Directive Registry types matching REST API response
 */

export interface DirectiveInfo {
    name: string;
    tier: 'core' | 'common' | 'format_specific' | 'reserved';
    scopes: string[];
    valueType: string;
    required: boolean;
    description: string;
    supportedFormats: string[];
    examples: string[];
    syntax: string;
    tooltip: string;
    seeAlso: string[];
}

export interface FormatInfo {
    name: string;
    abbreviation: string;
    tier1Support: number;
    tier2Support: number;
    tier3Support: number;
    overallSupport: number;
    supportedDirectives: string[];
    notes: string;
    domain: string;
}

export interface DirectiveRegistry {
    version: string;
    generatedAt: string;
    totalDirectives: number;
    directives: DirectiveInfo[];
    tiers: {
        core: DirectiveInfo[];
        common: DirectiveInfo[];
        format_specific: DirectiveInfo[];
        reserved: DirectiveInfo[];
    };
    scopes: { [scope: string]: DirectiveInfo[] };
    formats: { [abbreviation: string]: FormatInfo };
}
```

### Phase 2: Backend Services

**File:** `utlx-daemon-client.ts` (line ~510, after `getOperators()`)

```typescript
/**
 * Fetch USDL directive registry from daemon
 */
async getUsdlDirectives(): Promise<DirectiveRegistry> {
    const startTime = Date.now();
    console.log('[UTLXDaemonClient] Fetching USDL directives from /api/usdl/directives');

    try {
        const response = await this.httpRequest('/api/usdl/directives', 'GET');
        const elapsed = Date.now() - startTime;

        console.log('[UTLXDaemonClient] USDL directives fetched successfully:', {
            totalDirectives: response.totalDirectives,
            version: response.version,
            tiers: Object.keys(response.tiers).map(tier =>
                `${tier}:${response.tiers[tier].length}`),
            formats: Object.keys(response.formats).length,
            elapsedMs: elapsed
        });

        return response;
    } catch (error) {
        console.error('[UTLXDaemonClient] Failed to fetch USDL directives:', error);
        return createEmptyRegistry();
    }
}
```

**File:** `utlx-service.ts` (line ~50)

```typescript
/**
 * Get USDL directive registry
 */
getUsdlDirectives(): Promise<DirectiveRegistry>;
```

**File:** `utlx-service-impl.ts` (line ~215, after `getOperators()`)

```typescript
async getUsdlDirectives(): Promise<DirectiveRegistry> {
    console.log('[UTLXService] Getting USDL directives...');
    try {
        const registry = await this.daemonClient.getUsdlDirectives();
        console.log('[UTLXService] USDL directives retrieved:', {
            total: registry.totalDirectives,
            core: registry.tiers.core.length,
            common: registry.tiers.common.length
        });
        return registry;
    } catch (error) {
        console.error('[UTLXService] Get USDL directives error:', error);
        return createEmptyRegistry();
    }
}
```

### Phase 3: DirectivesTree Component

**File:** `directives-tree.tsx` (NEW - ~400-500 lines)

**Key Features:**
- Tier-based grouping (collapsible sections)
- Format filtering (only show directives supported by outputFormat)
- Search functionality
- Split view: directive list + details panel
- Insert/Copy buttons with codicon icons
- Full directive details: name, description, syntax, examples, scopes, value type

**Implementation Summary:**
```typescript
export const DirectivesTree: React.FC<DirectivesTreeProps> = ({
    directiveRegistry,
    outputFormat,
    onInsert,
    onCopy
}) => {
    // State
    const [expandedTiers, setExpandedTiers] = useState(new Set(['core']));
    const [selectedDirective, setSelectedDirective] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [splitPosition, setSplitPosition] = useState(50);

    // Filter directives by search + format
    const filteredDirectives = useMemo(() => {
        // Filter by search query and outputFormat support
    }, [directiveRegistry, searchQuery, outputFormat]);

    // Render tier groups, directive list, details panel
};
```

### Phase 4: Function Builder Dialog Updates

**File:** `function-builder-dialog.tsx`

**Changes:**

1. **Props Interface** (line ~50)
```typescript
interface FunctionBuilderDialogProps {
    // ... existing props
    outputFormat: string;  // NEW
    directiveRegistry: DirectiveRegistry | null;  // NEW
}
```

2. **Tab State** (line ~77)
```typescript
const [activeTab, setActiveTab] = useState<
    'functions' | 'inputs' | 'operators' | 'directives'
>('inputs');
```

3. **USDL Format Check** (line ~120)
```typescript
const isUsdlFormat = useMemo(() => {
    const tier2Formats = ['avro', 'jsch', 'xsd', 'proto'];
    return tier2Formats.includes(props.outputFormat?.toLowerCase() || '');
}, [props.outputFormat]);
```

4. **Tab Header** (line ~650)
```typescript
<div className='tab-header'>
    {/* Existing 3 tabs */}

    {/* NEW: USDL Directives Tab - Conditional */}
    {isUsdlFormat && (
        <button
            className={`tab-button ${activeTab === 'directives' ? 'active' : ''}`}
            onClick={() => setActiveTab('directives')}
        >
            <span className='codicon codicon-symbol-property'></span>
            USDL Directives
        </button>
    )}
</div>
```

5. **Tab Content** (line ~920)
```typescript
{activeTab === 'directives' && (
    <DirectivesTree
        directiveRegistry={props.directiveRegistry}
        outputFormat={props.outputFormat}
        onInsert={handleInsertDirective}
        onCopy={handleCopyDirective}
    />
)}
```

### Phase 5: Editor Widget Updates

**File:** `utlx-editor-widget.tsx`

**Changes:**

1. **State** (line ~80)
```typescript
protected functionBuilderDirectives: DirectiveRegistry | null = null;
```

2. **Fetch Directives** (line ~1375)
```typescript
// Fetch USDL directives from daemon
console.log('[UTLXEditor] Fetching USDL directives...');
try {
    this.functionBuilderDirectives = await this.utlxService.getUsdlDirectives();
    console.log('[UTLXEditor] Loaded USDL directive registry:', {
        total: this.functionBuilderDirectives.totalDirectives,
        tiers: Object.keys(this.functionBuilderDirectives.tiers),
        formats: Object.keys(this.functionBuilderDirectives.formats).length
    });
} catch (error) {
    console.error('[UTLXEditor] Failed to load USDL directives:', error);
    this.functionBuilderDirectives = null;
}
```

3. **Pass to Dialog** (line ~1655)
```typescript
<FunctionBuilderDialog
    // ... existing props
    outputFormat={this.outputFormat}
    directiveRegistry={this.functionBuilderDirectives}
/>
```

### Phase 6: Styling

**File:** `function-builder.css`

**Add styles for:**
- 4-tab layout (grid adjustment)
- USDL tab content
- Tier groups (headers, expand/collapse)
- Directive list items
- Details panel
- Format indicator badge
- Search input
- Split divider

---

## File Changes

### Files to Create

| File | Lines | Purpose |
|------|-------|---------|
| `src/common/usdl-types.ts` | ~80 | TypeScript interfaces for directive registry |
| `src/browser/function-builder/directives-tree.tsx` | ~500 | DirectivesTree React component |

### Files to Modify

| File | Location | Changes |
|------|----------|---------|
| `utlx-daemon-client.ts` | Line ~510 | Add `getUsdlDirectives()` method |
| `utlx-service.ts` | Line ~50 | Add interface method |
| `utlx-service-impl.ts` | Line ~215 | Implement method |
| `function-builder-dialog.tsx` | Lines 50, 77, 120, 650, 920 | Add props, state, conditional tab |
| `utlx-editor-widget.tsx` | Lines 80, 1375, 1655 | Add state, fetch, pass props |
| `function-builder.css` | End of file | Add USDL tab styles |

### Estimated Effort

- **Type Definitions**: 30 minutes
- **Backend Services**: 1 hour
- **DirectivesTree Component**: 4-5 hours
- **Function Builder Updates**: 1 hour
- **Editor Widget Updates**: 30 minutes
- **Styling**: 1-2 hours
- **Testing**: 2-3 hours
- **Documentation**: 1 hour

**Total**: ~12-15 hours

---

## Testing Plan

### Unit Tests

1. **DirectivesTree Component**
   - Directive filtering by output format
   - Tier expansion/collapse
   - Search functionality
   - Directive selection
   - Insert/Copy button handlers

2. **Service Layer**
   - `getUsdlDirectives()` success case
   - Error handling (daemon unavailable)
   - Empty registry fallback

### Integration Tests

1. **Tab Visibility**
   - Tab appears when outputFormat = 'avro'
   - Tab appears when outputFormat = 'jsch'
   - Tab appears when outputFormat = 'xsd'
   - Tab appears when outputFormat = 'proto'
   - Tab hidden when outputFormat = 'json'
   - Tab hidden when outputFormat = 'xml'

2. **REST API**
   - Verify HTTP GET to `/api/usdl/directives`
   - Verify response structure matches types
   - Verify error handling

3. **Directive Insertion**
   - Verify directive inserted at cursor
   - Verify template format correct
   - Verify Monaco editor receives update

4. **Clipboard Copy**
   - Verify clipboard contains directive template

### Manual Testing Scenarios

1. **Basic Flow**
   - Open editor with output = jsch
   - Click Function Builder
   - Verify USDL Directives tab visible
   - Click tab, verify directives load
   - Search for "namespace"
   - Select %namespace directive
   - Click Insert
   - Verify appears in Monaco editor

2. **Format Switching**
   - Change output format from jsch → json
   - Reopen Function Builder
   - Verify USDL tab hidden

3. **Error Handling**
   - Stop daemon
   - Open Function Builder
   - Verify graceful "not loaded" message

4. **Filtering**
   - Output = xsd
   - Open USDL tab
   - Verify only XSD-supported directives shown
   - Change to proto
   - Verify list updates

---

## Success Criteria

### Must Have

- ✅ Tab appears only for outputFormat in ['avro', 'jsch', 'xsd', 'proto']
- ✅ Directives fetched from `/api/usdl/directives` on Function Builder open
- ✅ Directives filtered by outputFormat support
- ✅ Grouped by tier with expand/collapse
- ✅ Details panel shows complete directive info
- ✅ Insert button adds to Monaco at cursor
- ✅ Copy button copies to clipboard
- ✅ Search filters directives
- ✅ Codicon icons consistent with existing tabs
- ✅ Comprehensive logging at all layers
- ✅ No TypeScript compilation errors
- ✅ No runtime errors

### Should Have

- Split view with adjustable divider
- Keyboard shortcuts (Enter to insert)
- Highlight search matches
- Related directives clickable

### Could Have

- Recent directives history
- Favorites/bookmarks
- Directive usage examples with preview

---

## Risks & Mitigations

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Daemon not available | High | Low | Graceful error handling, empty state UI |
| Large directive list slow | Medium | Low | Virtual scrolling if needed |
| Format detection incorrect | High | Low | Use existing outputFormat state (well-tested) |
| Tab layout breaks | Medium | Low | Test with different screen sizes |
| TypeScript type mismatch | Medium | Low | Match Kotlin data classes exactly |

---

## Future Enhancements

1. **Directive Validation**: Show warnings if required directives missing
2. **Auto-completion**: Suggest directives based on schema context
3. **Snippet Templates**: Multi-line directive templates with placeholders
4. **Format Migration**: Suggest directives when changing formats
5. **Usage Analytics**: Track most-used directives

---

## References

- REST API Endpoint: `/api/usdl/directives` (already implemented)
- OpenAPI Spec: `/docs/api/openapi.yaml` (lines 96-170, 1019-1199)
- Directive Registry Tests: `/schema/src/test/kotlin/org/apache/utlx/schema/usdl/DirectiveRegistryTest.kt`
- Integration Tests: `/modules/daemon/src/test/kotlin/org/apache/utlx/daemon/rest/RestApiIntegrationTest.kt` (lines 678-903)
- USDL Documentation: `/docs/usdl/USDL-DIRECTIVES-REFERENCE.md`

---

## Appendix: Example Directive Templates

### Core Directives (Tier 1)

```
%namespace: "com.example.orders"
%version: "1.0.0"
%types:
  - Customer
  - Order
%kind: object
%documentation: "Order management schema"
```

### Common Directives (Tier 2)

```
%fields:
  - id
  - customer
  - items
  - total
%required: ["id", "customer"]
%description: "Customer order record"
```

### Format-Specific Directives (Tier 3)

**For Avro:**
```
%logicalType: "timestamp-millis"
%precision: 10
%scale: 2
```

**For Proto:**
```
%fieldNumber: 1
%packed: true
```

**For XSD:**
```
%elementFormDefault: "qualified"
%attributeFormDefault: "unqualified"
```

**For JSON Schema:**
```
%draft: "2020-12"
%additionalProperties: false
```

---

**Document Version**: 1.0
**Last Updated**: 2025-01-18
**Status**: Ready for Implementation
