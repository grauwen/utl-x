# Theia Widget Communication Architecture with MCP Integration

**Document Version:** 1.0
**Last Updated:** 2025-11-08
**Purpose:** Explain how widgets communicate in Theia and how MCP fits into the architecture

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Theia Architecture Fundamentals](#theia-architecture-fundamentals)
3. [Container vs Independent Widgets](#container-vs-independent-widgets)
4. [Three Ways Widgets Communicate](#three-ways-widgets-communicate)
5. [Your Current Architecture](#your-current-architecture)
6. [MCP Integration Architecture](#mcp-integration-architecture)
7. [Complete Request Flow Examples](#complete-request-flow-examples)
8. [Recommended Architecture](#recommended-architecture)
9. [Implementation Plan](#implementation-plan)

---

## Executive Summary

### Your Question
> "Should there be a first empty widget which is used to pass on events, and in the widget sub-widgets? Is that possible in Theia, desirable? How should all the widgets communicate to each other in Theia? How does MCP play a role?"

### Answer

**Container Widget Approach: ❌ NOT Recommended**
- Possible in Theia but breaks the panel system
- You lose collapsible panels, drag-and-drop, Theia layouts
- All widgets forced into one area

**Independent Widgets + Coordination: ✅ RECOMMENDED**
- What you currently have (4 independent widgets)
- Use Theia's native panel system (left, main, right areas)
- Communication via:
  1. **Frontend**: Coordination Service (manages widget interactions)
  2. **Backend**: UTLXService (RPC to backend)
  3. **MCP**: Via UTLXService → UTLXD → MCP servers

### Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│ LAYER 1: Theia Frontend (Browser)                          │
│                                                             │
│  User Widgets (UI)                                          │
│  ├── UTLXToolbarWidget                                      │
│  ├── MultiInputPanelWidget                                  │
│  ├── UTLXEditorWidget                                       │
│  └── OutputPanelWidget                                      │
│         ↕ events                                            │
│  Coordination Service                                       │
│  └── Coordinates widget interactions                        │
│         ↕ RPC calls                                         │
│  UTLXService (Frontend Proxy)                               │
│  └── Proxies calls to backend                               │
└─────────────────────────────────────────────────────────────┘
                          ↕
          JSON-RPC over WebSocket/HTTP
                          ↕
┌─────────────────────────────────────────────────────────────┐
│ LAYER 2: Theia Backend (Node.js)                           │
│                                                             │
│  UTLXServiceImpl (Backend Service)                          │
│  ├── validate(code)                                         │
│  ├── execute(code, inputs)                                  │
│  ├── inferSchema(code, schema)                              │
│  └── lint(code)                                             │
│         ↕ spawns/communicates                               │
│  UTLXD Process Manager                                      │
│  └── Manages utlxd daemon lifecycle                         │
└─────────────────────────────────────────────────────────────┘
                          ↕
            LSP/Custom Protocol
                          ↕
┌─────────────────────────────────────────────────────────────┐
│ LAYER 3: UTLXD Daemon (Rust/Go)                            │
│                                                             │
│  UTLXD Core                                                 │
│  ├── LSP Server (language features)                         │
│  ├── Transformation Engine                                  │
│  └── MCP Client                                             │
│         ↕ MCP Protocol                                      │
│  MCP Servers                                                │
│  ├── AI/LLM Server (code generation)                        │
│  ├── Schema Server (validation)                             │
│  └── Custom Servers                                         │
└─────────────────────────────────────────────────────────────┘
```

**Key Point:** MCP is accessed through the backend service layer, NOT directly from widgets.

---

## Theia Architecture Fundamentals

### Theia's Multi-Process Architecture

```
Browser Process (Frontend)
  ↕ WebSocket/HTTP
Node.js Process (Backend)
  ↕ IPC/Sockets
External Processes (UTLXD, LSP servers, etc.)
```

### Theia Shell Areas

Theia provides predefined areas where widgets can be placed:

```
┌──────────────────────────────────────────────────┐
│ Top Area (notifications, toolbars)              │
├──────────────────────────────────────────────────┤
│ ┌────────┬─────────────────────┬──────────────┐ │
│ │ Left   │ Main Area           │ Right        │ │
│ │ Panel  │ (Editor, Terminal)  │ Panel        │ │
│ │        │                     │              │ │
│ └────────┴─────────────────────┴──────────────┘ │
├──────────────────────────────────────────────────┤
│ Bottom Panel (Problems, Output, Debug Console)  │
├──────────────────────────────────────────────────┤
│ Status Bar                                       │
└──────────────────────────────────────────────────┘
```

**Your Current Layout:**
- Top: UTLXToolbarWidget
- Left: MultiInputPanelWidget
- Main: UTLXEditorWidget
- Right: OutputPanelWidget
- Status Bar: Health indicators

### Widget Types in Theia

1. **ReactWidget** (your widgets)
   - React-based UI
   - Has `render()` method
   - Lifecycle: constructor → @postConstruct → attach → show → render

2. **BaseWidget** (simpler)
   - Pure DOM manipulation
   - No React overhead

3. **Container Widgets** (possible but not recommended)
   - Can contain other widgets as DOM children
   - Example: Theia's Explorer (tree view + actions)

---

## Container vs Independent Widgets

### Approach 1: Container Widget (❌ NOT Recommended for Your Case)

**Structure:**
```typescript
@injectable()
export class UTLXContainerWidget extends ReactWidget {
    private inputPanel?: MultiInputPanelWidget;
    private editorWidget?: UTLXEditorWidget;
    private outputPanel?: OutputPanelWidget;

    protected render(): React.ReactNode {
        return (
            <div className="utlx-container">
                <div className="left-pane">
                    {/* InputPanel DOM node appended here */}
                    <div ref={el => el?.appendChild(this.inputPanel!.node)} />
                </div>
                <div className="middle-pane">
                    {/* EditorWidget DOM node appended here */}
                    <div ref={el => el?.appendChild(this.editorWidget!.node)} />
                </div>
                <div className="right-pane">
                    {/* OutputPanel DOM node appended here */}
                    <div ref={el => el?.appendChild(this.outputPanel!.node)} />
                </div>
            </div>
        );
    }
}

// Usage in frontend-contribution
const container = await widgetManager.getOrCreateWidget(UTLXContainerWidget.ID);
shell.addWidget(container, { area: 'main' }); // All in main area!
```

**Problems:**

❌ **Loses Theia's Panel System**
- Can't use left/right collapsible panels
- Can't drag panels to different areas
- No integration with Theia's layout restore

❌ **All Widgets in One Area**
- Container must go in one shell area (e.g., 'main')
- Can't have input in left, editor in main, output in right

❌ **Complex Lifecycle Management**
- Container owns child widget lifecycle
- Must manually attach/detach child widgets
- Complicated event handling

❌ **Breaks User Expectations**
- Users expect Theia's standard panel behavior
- Can't customize layout easily

**When to Use Container Widgets:**
- Custom composite components (e.g., tree + toolbar)
- Tightly coupled UI elements
- When you DON'T want separate panels

**Examples in Theia:**
- Explorer: Tree view + refresh button
- Git view: Staged/unstaged lists + buttons
- Tabs widget: Tab bar + content area

---

### Approach 2: Independent Widgets (✅ RECOMMENDED - Your Current Approach)

**Structure:**
```typescript
// Each widget is independent
@injectable()
export class MultiInputPanelWidget extends ReactWidget { ... }

@injectable()
export class UTLXEditorWidget extends ReactWidget { ... }

@injectable()
export class OutputPanelWidget extends ReactWidget { ... }

// Frontend contribution opens them separately
const inputPanel = await widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID);
const editorWidget = await widgetManager.getOrCreateWidget(UTLXEditorWidget.ID);
const outputPanel = await widgetManager.getOrCreateWidget(OutputPanelWidget.ID);

// Add to different shell areas
shell.addWidget(inputPanel, { area: 'left' });   // Left panel
shell.addWidget(editorWidget, { area: 'main' }); // Center area
shell.addWidget(outputPanel, { area: 'right' }); // Right panel
```

**Benefits:**

✅ **Native Theia Panels**
- Left/right panels are collapsible
- Users can resize panels
- Layout is saved/restored

✅ **Flexible Layout**
- Each widget in its natural area
- Users can move widgets around
- Follows Theia conventions

✅ **Simple Lifecycle**
- Each widget manages itself
- Theia handles attach/detach
- Clear responsibility

✅ **Coordination Separated**
- Widgets communicate via events/services
- Coordination logic in separate service
- Easy to test

**Coordination Options:**

1. **Window Events** (your current approach - simple but limited)
2. **Theia Event Bus** (better - type-safe)
3. **Coordination Service** (best - clean architecture)

---

## Three Ways Widgets Communicate

### Option 1: Window Events (Current Approach)

**How It Works:**
```typescript
// Dispatcher: MultiInputPanelWidget
window.dispatchEvent(new CustomEvent('utlx-input-format-changed', {
    detail: {
        format: 'json',
        inputId: 'input-1',
        isSchema: false
    }
}));

// Listener: UTLXWorkbenchWidget (if it existed!)
window.addEventListener('utlx-input-format-changed', (event: Event) => {
    const customEvent = event as CustomEvent;
    this.updateEditorHeaders(customEvent.detail);
});
```

**Pros:**
✅ Simple to implement
✅ Works across widgets
✅ No dependency injection needed

**Cons:**
❌ Not type-safe (detail is `any`)
❌ Global scope pollution
❌ Hard to debug (who's listening?)
❌ No compile-time checks

**Current Problem:**
Your widgets dispatch events, but there's NO listener because UTLXWorkbenchWidget is NOT registered!

---

### Option 2: Theia Event Bus (Recommended)

**How It Works:**
```typescript
// Define event type
export const UTLXInputFormatChangedEvent = Symbol('UTLXInputFormatChangedEvent');
export interface UTLXInputFormatChangedEvent {
    format: string;
    inputId: string;
    isSchema: boolean;
}

// Dispatcher: MultiInputPanelWidget
@injectable()
export class MultiInputPanelWidget extends ReactWidget {
    @inject(IEventBus)
    protected readonly eventBus!: IEventBus;

    private handleFormatChange(format: string) {
        this.eventBus.fire(UTLXInputFormatChangedEvent, {
            format,
            inputId: this.state.activeInputId,
            isSchema: false
        });
    }
}

// Listener: Coordination Service
@injectable()
export class UTLXCoordinationService {
    @inject(IEventBus)
    protected readonly eventBus!: IEventBus;

    @postConstruct()
    protected init() {
        this.eventBus.onEvent(UTLXInputFormatChangedEvent, event => {
            this.updateEditorHeaders(event);
        });
    }
}
```

**Pros:**
✅ Type-safe (TypeScript interfaces)
✅ Better IDE support (autocomplete)
✅ Easier to debug (can trace events)
✅ Follows Theia patterns

**Cons:**
⚠️ Slightly more boilerplate
⚠️ Need to define event types

---

### Option 3: Coordination Service (Best Practice)

**How It Works:**
```typescript
// Service holds widget references and coordinates
@injectable()
export class UTLXCoordinationService {
    @inject(WidgetManager)
    protected readonly widgetManager!: WidgetManager;

    @inject(IEventBus)
    protected readonly eventBus!: IEventBus;

    private inputPanel?: MultiInputPanelWidget;
    private editorWidget?: UTLXEditorWidget;
    private outputPanel?: OutputPanelWidget;

    @postConstruct()
    protected async init() {
        // Load widget references
        this.inputPanel = await this.widgetManager.getOrCreateWidget(
            MultiInputPanelWidget.ID
        );
        this.editorWidget = await this.widgetManager.getOrCreateWidget(
            UTLXEditorWidget.ID
        );
        this.outputPanel = await this.widgetManager.getOrCreateWidget(
            OutputPanelWidget.ID
        );

        // Set up event listeners
        this.eventBus.onEvent(UTLXInputFormatChangedEvent, event => {
            this.handleInputFormatChange(event);
        });

        this.eventBus.onEvent(UTLXOutputFormatChangedEvent, event => {
            this.handleOutputFormatChange(event);
        });

        // Trigger initial header update
        this.updateEditorHeaders();
    }

    private handleInputFormatChange(event: UTLXInputFormatChangedEvent) {
        // Get all inputs
        const inputs = this.inputPanel!.getInputDocuments();

        // Get output format
        const outputFormat = this.outputPanel!.getCurrentFormat();

        // Update editor headers
        this.updateEditorHeaders(inputs, outputFormat);
    }

    private updateEditorHeaders(inputs, outputFormat) {
        // Build header lines
        const inputLines = inputs.map(input =>
            `input ${input.format} name=${input.name}`
        );

        // Update editor
        this.editorWidget!.updateHeaders(inputLines, outputFormat);
    }
}

// Register in frontend-module.ts
bind(UTLXCoordinationService).toSelf().inSingletonScope();
bind(FrontendApplicationContribution).toService(UTLXCoordinationService);
```

**Pros:**
✅ Clear separation of concerns
✅ Testable (can mock widgets)
✅ No "widget" overhead (plain service)
✅ Centralized coordination logic
✅ Easy to extend

**Cons:**
⚠️ More code initially
⚠️ Need to manage service lifecycle

---

## Your Current Architecture

### What You Have Now

```
Frontend-Module.ts (Registered Widgets):
├── MultiInputPanelWidget ✓
├── OutputPanelWidget ✓
├── UTLXEditorWidget ✓
├── UTLXToolbarWidget ✓
├── ModeSelectorWidget ✓ (but never opened)
├── HealthMonitorWidget ✓ (but never opened)
└── TestWidget ✓ (but never opened)

❌ UTLXWorkbenchWidget - NOT REGISTERED!
```

### Why UTLXWorkbenchWidget is NOT Registered

**Current State:**
- File exists: `src/browser/workbench/utlx-workbench-widget.tsx`
- Has coordination logic
- Has render() method that creates 3-pane UI
- BUT: Not bound in `frontend-module.ts`

**Why:**
- You're using **Direct Shell Layout** (widgets in separate areas)
- Workbench widget was meant for coordination
- But it was never properly set up
- Events are dispatched, but no one is listening!

### The Problem

```
┌─────────────────────────────────────────────────────────┐
│ MultiInputPanelWidget                                   │
│   ↓ dispatches 'utlx-input-format-changed' event       │
│   ↓ window.dispatchEvent(...)                          │
│   ↓                                                     │
│   ? WHO IS LISTENING? NO ONE!                          │
│   ↓                                                     │
│   ✗ UTLXWorkbenchWidget NOT registered                 │
│   ✗ No coordination service                            │
│   ✗ Events go into the void                            │
└─────────────────────────────────────────────────────────┘

Result: Editor headers never update!
```

---

## MCP Integration Architecture

### MCP Overview

**Model Context Protocol (MCP):**
- Protocol for AI/LLM integration
- Allows communication between applications and AI services
- Used for code generation, validation, suggestions, etc.

### Architecture Layers with MCP

```
┌─────────────────────────────────────────────────────────────┐
│ LAYER 1: Frontend (Browser) - Theia Widgets                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  UTLXToolbarWidget                                          │
│  ├── User clicks "AI Assist" button                         │
│  ├── Opens MCP dialog                                       │
│  └── User enters prompt: "Transform XML to JSON"           │
│         ↓                                                   │
│  Coordination Service                                       │
│  └── Handles MCP requests from toolbar                      │
│         ↓                                                   │
│  UTLXService (Frontend Proxy)                               │
│  └── generateCodeFromPrompt(prompt)                         │
│         ↓ JSON-RPC                                          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ LAYER 2: Backend (Node.js) - Theia Backend Services        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  UTLXServiceImpl                                            │
│  ├── async generateCodeFromPrompt(prompt: string)           │
│  ├── Sends request to UTLXD via LSP/Custom Protocol        │
│  └── Returns generated code                                 │
│         ↓                                                   │
│  UTLXD Process Manager                                      │
│  └── Manages utlxd daemon connection                        │
│         ↓ LSP/IPC                                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ LAYER 3: UTLXD Daemon (External Process)                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  UTLXD Core (Rust/Go)                                       │
│  ├── LSP Server (language features)                         │
│  ├── Transformation Engine (execute, validate)              │
│  └── MCP Client                                             │
│      ├── Connects to MCP servers                            │
│      ├── Sends prompts to AI                                │
│      └── Receives generated code                            │
│         ↓ MCP Protocol (JSON-RPC)                           │
│                                                             │
│  MCP Servers (External Services)                            │
│  ├── AI/LLM Server                                          │
│  │   ├── OpenAI GPT                                         │
│  │   ├── Claude                                             │
│  │   └── Local LLM                                          │
│  ├── Schema Validation Server                               │
│  └── Custom Extension Servers                               │
└─────────────────────────────────────────────────────────────┘
```

### Key Points about MCP

1. **MCP is NOT accessed directly from widgets**
   - Widgets call backend services
   - Backend calls UTLXD
   - UTLXD communicates with MCP servers

2. **All operations go through UTLXService**
   - Validate: `utlxService.validate(code)`
   - Execute: `utlxService.execute(code, inputs)`
   - AI Generate: `utlxService.generateCodeFromPrompt(prompt)`
   - Lint: `utlxService.lint(code)`

3. **Any widget can trigger MCP operations**
   - Toolbar: AI Assist button
   - Editor: Inline suggestions
   - Input Panel: Auto-format requests
   - Output Panel: Schema inference

---

## Complete Request Flow Examples

### Example 1: User Clicks "Validate" Button

```
┌─────────────────────────────────────────────────────────────┐
│ 1. USER ACTION                                              │
├─────────────────────────────────────────────────────────────┤
│ User clicks "Validate" button in UTLXToolbarWidget          │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. WIDGET EVENT                                             │
├─────────────────────────────────────────────────────────────┤
│ UTLXToolbarWidget.handleValidate()                          │
│   ├── Gets editor content from EditorWidget                 │
│   ├── OR: Fires 'utlx-validate-requested' event             │
│   └── Coordination service handles it                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. COORDINATION SERVICE                                     │
├─────────────────────────────────────────────────────────────┤
│ UTLXCoordinationService.handleValidate()                    │
│   ├── Gets code from editorWidget.getContent()              │
│   └── Calls utlxService.validate(code)                      │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC
┌─────────────────────────────────────────────────────────────┐
│ 4. BACKEND SERVICE                                          │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl.validate(code: string)                      │
│   ├── Sends LSP request to UTLXD                            │
│   ├── Request: { method: 'validate', params: { code } }     │
│   └── Waits for response                                    │
└─────────────────────────────────────────────────────────────┘
                          ↓ LSP/IPC
┌─────────────────────────────────────────────────────────────┐
│ 5. UTLXD DAEMON                                             │
├─────────────────────────────────────────────────────────────┤
│ UTLXD receives validate request                             │
│   ├── Parses UTLX code                                      │
│   ├── Checks syntax                                         │
│   ├── Runs semantic validation                              │
│   ├── MAY call MCP for advanced validation                  │
│   └── Returns diagnostics                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓ (optional) MCP
┌─────────────────────────────────────────────────────────────┐
│ 6. MCP SERVER (Optional)                                    │
├─────────────────────────────────────────────────────────────┤
│ Schema Validation MCP Server                                │
│   ├── Receives validation request via MCP                   │
│   ├── Performs deep schema analysis                         │
│   └── Returns additional diagnostics                        │
└─────────────────────────────────────────────────────────────┘
                          ↓ Response flows back
┌─────────────────────────────────────────────────────────────┐
│ 7. BACKEND RETURNS RESULT                                   │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl returns validation result                   │
│   └── { valid: false, diagnostics: [...] }                  │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC response
┌─────────────────────────────────────────────────────────────┐
│ 8. COORDINATION SERVICE UPDATES UI                          │
├─────────────────────────────────────────────────────────────┤
│ UTLXCoordinationService receives result                     │
│   ├── If errors: Show in editor (red squiggles)             │
│   ├── If errors: Update output panel                        │
│   └── Show notification to user                             │
└─────────────────────────────────────────────────────────────┘
```

---

### Example 2: AI Assist (MCP Heavy Flow)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. USER ACTION                                              │
├─────────────────────────────────────────────────────────────┤
│ User clicks "AI Assist" in UTLXToolbarWidget                │
│ Opens dialog, enters: "Convert XML customer data to JSON"   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. TOOLBAR WIDGET                                           │
├─────────────────────────────────────────────────────────────┤
│ UTLXToolbarWidget.submitMCPPrompt()                         │
│   ├── Gets prompt from dialog                               │
│   ├── Gets current editor context (optional)                │
│   └── Calls utlxService.generateCodeFromPrompt(prompt)      │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC
┌─────────────────────────────────────────────────────────────┐
│ 3. BACKEND SERVICE                                          │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl.generateCodeFromPrompt(prompt: string)      │
│   ├── Sends request to UTLXD                                │
│   ├── Request includes:                                     │
│   │   - User prompt                                         │
│   │   - Current workspace context                           │
│   │   - Input schema (if available)                         │
│   └── Waits for generated code                              │
└─────────────────────────────────────────────────────────────┘
                          ↓ LSP/IPC
┌─────────────────────────────────────────────────────────────┐
│ 4. UTLXD DAEMON                                             │
├─────────────────────────────────────────────────────────────┤
│ UTLXD.handleMCPRequest()                                    │
│   ├── Prepares MCP request                                  │
│   ├── Adds context (schemas, examples)                      │
│   └── Sends to MCP AI server                                │
└─────────────────────────────────────────────────────────────┘
                          ↓ MCP Protocol
┌─────────────────────────────────────────────────────────────┐
│ 5. MCP AI SERVER                                            │
├─────────────────────────────────────────────────────────────┤
│ AI/LLM Server (GPT-4, Claude, etc.)                         │
│   ├── Receives prompt + context                             │
│   ├── Generates UTLX transformation code                    │
│   ├── Returns:                                              │
│   │   - Generated code                                      │
│   │   - Explanation                                         │
│   │   - Example usage                                       │
│   └── Sends response back                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓ Response flows back
┌─────────────────────────────────────────────────────────────┐
│ 6. UTLXD VALIDATES GENERATED CODE                           │
├─────────────────────────────────────────────────────────────┤
│ UTLXD receives AI-generated code                            │
│   ├── Validates syntax                                      │
│   ├── Checks for common errors                              │
│   └── Returns to backend                                    │
└─────────────────────────────────────────────────────────────┘
                          ↓ LSP response
┌─────────────────────────────────────────────────────────────┐
│ 7. BACKEND RETURNS TO FRONTEND                              │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl returns result                              │
│   └── { code: "...", explanation: "...", valid: true }      │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC response
┌─────────────────────────────────────────────────────────────┐
│ 8. TOOLBAR WIDGET UPDATES EDITOR                            │
├─────────────────────────────────────────────────────────────┤
│ UTLXToolbarWidget receives generated code                   │
│   ├── Fires 'utlx-generated' event                          │
│   └── Coordination service inserts into editor              │
│         OR                                                   │
│   └── Direct call: editorWidget.setContent(code)            │
└─────────────────────────────────────────────────────────────┘
```

---

### Example 3: Execute Transformation (Full Stack)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. USER ACTION                                              │
├─────────────────────────────────────────────────────────────┤
│ User clicks "Execute" button in UTLXToolbarWidget           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. COORDINATION SERVICE                                     │
├─────────────────────────────────────────────────────────────┤
│ UTLXCoordinationService.handleExecute()                     │
│   ├── Gets code from editorWidget.getContent()              │
│   ├── Gets inputs from inputPanel.getInputDocuments()       │
│   │   Returns: [                                            │
│   │     { name: "Customer", format: "xml", content: "..." },│
│   │     { name: "Orders", format: "json", content: "..." }  │
│   │   ]                                                     │
│   └── Calls utlxService.execute(code, inputs)               │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC
┌─────────────────────────────────────────────────────────────┐
│ 3. BACKEND SERVICE                                          │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl.execute(code, inputs)                       │
│   ├── Validates inputs                                      │
│   ├── Sends to UTLXD                                        │
│   └── Request: {                                            │
│        method: 'execute',                                   │
│        params: { code, inputs }                             │
│      }                                                      │
└─────────────────────────────────────────────────────────────┘
                          ↓ LSP/IPC
┌─────────────────────────────────────────────────────────────┐
│ 4. UTLXD EXECUTION ENGINE                                   │
├─────────────────────────────────────────────────────────────┤
│ UTLXD.execute(code, inputs)                                 │
│   ├── Compiles UTLX code                                    │
│   ├── Runs transformation                                   │
│   ├── For each input:                                       │
│   │   ├── Parse (XML/JSON/YAML/CSV)                         │
│   │   ├── Apply transformation                              │
│   │   └── Generate output                                   │
│   ├── MAY call MCP for:                                     │
│   │   ├── Complex transformations                           │
│   │   ├── External data lookups                             │
│   │   └── Validation checks                                 │
│   └── Returns execution result                              │
└─────────────────────────────────────────────────────────────┘
                          ↓ Response
┌─────────────────────────────────────────────────────────────┐
│ 5. BACKEND RETURNS RESULT                                   │
├─────────────────────────────────────────────────────────────┤
│ UTLXServiceImpl returns                                     │
│   └── {                                                     │
│        success: true,                                       │
│        outputs: [ { format: "json", content: "..." } ],     │
│        executionTimeMs: 45,                                 │
│        diagnostics: []                                      │
│      }                                                      │
└─────────────────────────────────────────────────────────────┘
                          ↓ JSON-RPC response
┌─────────────────────────────────────────────────────────────┐
│ 6. COORDINATION SERVICE UPDATES OUTPUT                      │
├─────────────────────────────────────────────────────────────┤
│ UTLXCoordinationService receives result                     │
│   ├── Calls outputPanel.displayExecutionResult(result)      │
│   ├── OutputPanel shows:                                    │
│   │   ├── Generated output                                  │
│   │   ├── Execution time                                    │
│   │   └── Any warnings/errors                               │
│   └── Shows success notification                            │
└─────────────────────────────────────────────────────────────┘
```

---

## Recommended Architecture

### Proposed Structure

```
src/browser/
├── widgets/ (UI Components)
│   ├── toolbar/
│   │   └── utlx-toolbar-widget.tsx
│   ├── input-panel/
│   │   └── multi-input-panel-widget.tsx
│   ├── editor/
│   │   └── utlx-editor-widget.tsx
│   └── output-panel/
│       └── output-panel-widget.tsx
│
├── services/ (Business Logic)
│   ├── utlx-coordination-service.ts        ← NEW!
│   ├── utlx-service-proxy.ts                (RPC proxy to backend)
│   └── utlx-event-types.ts                  ← NEW! (Event definitions)
│
├── commands/ (Command Handlers)
│   └── utlx-command-contribution.ts         ← NEW! (Extract from widgets)
│
├── frontend-module.ts (DI Bindings)
└── utlx-frontend-contribution.ts (Lifecycle)

src/node/ (Backend)
├── utlx-service-impl.ts (Backend RPC service)
└── utlxd-process-manager.ts (Manages UTLXD daemon)
```

### UTLXCoordinationService

```typescript
// src/browser/services/utlx-coordination-service.ts

import { injectable, inject, postConstruct } from 'inversify';
import { IEventBus, FrontendApplicationContribution } from '@theia/core/lib/browser';
import { WidgetManager } from '@theia/core/lib/browser';
import { MessageService } from '@theia/core';
import {
    UTLXInputFormatChangedEvent,
    UTLXOutputFormatChangedEvent,
    UTLXModeChangedEvent,
    UTLXValidateRequestedEvent,
    UTLXExecuteRequestedEvent
} from './utlx-event-types';
import { MultiInputPanelWidget } from '../widgets/input-panel/multi-input-panel-widget';
import { UTLXEditorWidget } from '../widgets/editor/utlx-editor-widget';
import { OutputPanelWidget } from '../widgets/output-panel/output-panel-widget';
import { UTLX_SERVICE_SYMBOL, UTLXService } from '../../common/protocol';

/**
 * Coordination service that manages interactions between UTL-X widgets.
 *
 * Responsibilities:
 * - Listen to events from widgets
 * - Coordinate header updates
 * - Handle validation/execution requests
 * - Communicate with backend via UTLXService
 */
@injectable()
export class UTLXCoordinationService implements FrontendApplicationContribution {

    @inject(WidgetManager)
    protected readonly widgetManager!: WidgetManager;

    @inject(IEventBus)
    protected readonly eventBus!: IEventBus;

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    private inputPanel?: MultiInputPanelWidget;
    private editorWidget?: UTLXEditorWidget;
    private outputPanel?: OutputPanelWidget;

    @postConstruct()
    protected init(): void {
        console.log('[UTLXCoordinationService] Initializing...');

        // Set up event listeners immediately (synchronous)
        this.setupEventListeners();

        // Load widget references asynchronously
        this.loadWidgets();
    }

    private setupEventListeners(): void {
        // Input format changed
        this.eventBus.onEvent(UTLXInputFormatChangedEvent, event => {
            console.log('[Coordination] Input format changed:', event);
            this.updateEditorHeaders();
        });

        // Output format changed
        this.eventBus.onEvent(UTLXOutputFormatChangedEvent, event => {
            console.log('[Coordination] Output format changed:', event);
            this.updateEditorHeaders();
        });

        // Mode changed
        this.eventBus.onEvent(UTLXModeChangedEvent, event => {
            console.log('[Coordination] Mode changed:', event.mode);
            this.handleModeChange(event.mode);
        });

        // Validate requested
        this.eventBus.onEvent(UTLXValidateRequestedEvent, event => {
            console.log('[Coordination] Validate requested');
            this.handleValidate();
        });

        // Execute requested
        this.eventBus.onEvent(UTLXExecuteRequestedEvent, event => {
            console.log('[Coordination] Execute requested');
            this.handleExecute();
        });
    }

    private async loadWidgets(): Promise<void> {
        try {
            this.inputPanel = await this.widgetManager.getOrCreateWidget(
                MultiInputPanelWidget.ID
            );
            this.editorWidget = await this.widgetManager.getOrCreateWidget(
                UTLXEditorWidget.ID
            );
            this.outputPanel = await this.widgetManager.getOrCreateWidget(
                OutputPanelWidget.ID
            );

            console.log('[UTLXCoordinationService] All widgets loaded');

            // Trigger initial header update
            this.updateEditorHeaders();
        } catch (error) {
            console.error('[UTLXCoordinationService] Failed to load widgets:', error);
        }
    }

    private updateEditorHeaders(): void {
        if (!this.inputPanel || !this.editorWidget || !this.outputPanel) {
            console.warn('[Coordination] Widgets not ready yet');
            return;
        }

        // Get all input documents
        const inputs = this.inputPanel.getInputDocuments();

        // Build input header lines
        const inputLines: string[] = [];
        if (inputs.length === 1) {
            inputLines.push(`input ${inputs[0].format}`);
        } else if (inputs.length > 1) {
            inputs.forEach(input => {
                inputLines.push(`input ${input.format} name=${input.name}`);
            });
        }

        // Get output format
        const outputFormat = this.outputPanel.getCurrentFormat();

        // Update editor headers
        this.editorWidget.updateHeaders(inputLines, outputFormat);

        console.log('[Coordination] Editor headers updated:', { inputLines, outputFormat });
    }

    private handleModeChange(mode: UTLXMode): void {
        if (!this.inputPanel || !this.outputPanel) return;

        // Notify panels (they listen to event bus too, but can also call directly)
        this.inputPanel.setMode(mode);
        this.outputPanel.setMode(mode);

        // Clear output when mode changes
        this.outputPanel.clear();
    }

    private async handleValidate(): Promise<void> {
        if (!this.editorWidget || !this.outputPanel) return;

        const code = this.editorWidget.getContent();
        if (!code) {
            this.messageService.warn('No code to validate');
            return;
        }

        try {
            const result = await this.utlxService.validate(code);

            if (result.valid) {
                this.messageService.info('✓ Code is valid');
            } else {
                const errorCount = result.diagnostics.filter(d => d.severity === 1).length;
                const warningCount = result.diagnostics.filter(d => d.severity === 2).length;
                this.messageService.warn(
                    `Found ${errorCount} error(s) and ${warningCount} warning(s)`
                );
            }
        } catch (error) {
            this.messageService.error(`Validation error: ${error}`);
        }
    }

    private async handleExecute(): Promise<void> {
        if (!this.inputPanel || !this.editorWidget || !this.outputPanel) return;

        const code = this.editorWidget.getContent();
        const inputs = this.inputPanel.getInputDocuments();

        if (!code) {
            this.messageService.warn('No transformation code');
            return;
        }

        if (inputs.length === 0) {
            this.messageService.warn('Please load input data first');
            return;
        }

        try {
            this.messageService.info('Executing transformation...');

            const result = await this.utlxService.execute(code, inputs);

            this.outputPanel.displayExecutionResult(result);

            if (result.success) {
                this.messageService.info(`✓ Completed in ${result.executionTimeMs}ms`);
            } else {
                this.messageService.error('Transformation failed');
            }
        } catch (error) {
            this.messageService.error(`Execution error: ${error}`);
            this.outputPanel.displayError(String(error));
        }
    }

    // FrontendApplicationContribution
    async onStart(): Promise<void> {
        // Already initialized in @postConstruct
    }
}
```

### Event Type Definitions

```typescript
// src/browser/services/utlx-event-types.ts

import { UTLXMode } from '../../common/protocol';

// Input format changed
export const UTLXInputFormatChangedEvent = Symbol('UTLXInputFormatChangedEvent');
export interface UTLXInputFormatChangedEvent {
    format: string;
    inputId: string;
    isSchema: boolean;
}

// Output format changed
export const UTLXOutputFormatChangedEvent = Symbol('UTLXOutputFormatChangedEvent');
export interface UTLXOutputFormatChangedEvent {
    format: string;
    tab: 'instance' | 'schema';
}

// Mode changed
export const UTLXModeChangedEvent = Symbol('UTLXModeChangedEvent');
export interface UTLXModeChangedEvent {
    mode: UTLXMode;
}

// Validate requested
export const UTLXValidateRequestedEvent = Symbol('UTLXValidateRequestedEvent');
export interface UTLXValidateRequestedEvent {
    // no payload
}

// Execute requested
export const UTLXExecuteRequestedEvent = Symbol('UTLXExecuteRequestedEvent');
export interface UTLXExecuteRequestedEvent {
    // no payload
}

// MCP code generated
export const UTLXCodeGeneratedEvent = Symbol('UTLXCodeGeneratedEvent');
export interface UTLXCodeGeneratedEvent {
    code: string;
    prompt: string;
    explanation?: string;
}
```

### Update Widgets to Fire Events

```typescript
// MultiInputPanelWidget - fire event instead of window.dispatchEvent
private handleFormatChange(format: string): void {
    // ... update state ...

    // Fire typed event
    this.eventBus.fire(UTLXInputFormatChangedEvent, {
        format,
        inputId: this.state.activeInputId,
        isSchema: this.state.mode === UTLXMode.DESIGN_TIME && this.state.activeSubTab === 'schema'
    });
}
```

### Register in Frontend Module

```typescript
// src/browser/frontend-module.ts

import { UTLXCoordinationService } from './services/utlx-coordination-service';

export default new ContainerModule(bind => {
    // ... existing widget bindings ...

    // Register coordination service
    bind(UTLXCoordinationService).toSelf().inSingletonScope();
    bind(FrontendApplicationContribution).toService(UTLXCoordinationService);
});
```

---

## Implementation Plan

### Phase 1: Fix Current Issues (Immediate)

1. **Register UTLXWorkbenchWidget OR Create Coordination Service**

   **Option A: Quick Fix (Register Workbench)**
   ```typescript
   // frontend-module.ts
   bind(UTLXWorkbenchWidget).toSelf();
   bind(WidgetFactory).toDynamicValue(ctx => ({
       id: UTLXWorkbenchWidget.ID,
       createWidget: () => ctx.container.get(UTLXWorkbenchWidget)
   })).inSingletonScope();
   ```

   **Option B: Better Fix (Create Service)**
   - Create `UTLXCoordinationService` (see above)
   - Register in frontend-module
   - Remove UTLXWorkbenchWidget

2. **Fix Event Listeners**
   - Set up listeners synchronously in @postConstruct
   - Load widget references asynchronously

3. **Fix CSS (Vertical Tabs)**
   ```css
   .utlx-vertical-tabs {
       width: 120px;  /* Was 20px */
       min-width: 100px;
   }
   ```

### Phase 2: Migrate to Theia Event Bus (Better)

1. **Define Event Types**
   - Create `utlx-event-types.ts`
   - Define all event interfaces

2. **Update Widgets**
   - Replace `window.dispatchEvent()` with `eventBus.fire()`
   - Replace `window.addEventListener()` with `eventBus.onEvent()`

3. **Update Coordination**
   - Use typed events in coordination service
   - Remove window event listeners

### Phase 3: MCP Integration (Complete)

1. **Backend Service Methods**
   ```typescript
   // UTLXServiceImpl (backend)
   async generateCodeFromPrompt(prompt: string): Promise<GeneratedCode> {
       // Call UTLXD which calls MCP
   }

   async validate(code: string): Promise<ValidationResult> {
       // May use MCP for advanced validation
   }

   async execute(code: string, inputs: Input[]): Promise<ExecutionResult> {
       // Execution via UTLXD
   }
   ```

2. **Toolbar MCP Integration**
   - AI Assist button calls `utlxService.generateCodeFromPrompt()`
   - Receives generated code
   - Fires `UTLXCodeGeneratedEvent`
   - Coordination service inserts into editor

3. **UTLXD MCP Client**
   - Connect to configured MCP servers
   - Handle MCP requests/responses
   - Cache MCP results where appropriate

### Phase 4: Testing & Documentation

1. **Unit Tests**
   - Test coordination service
   - Test event flows
   - Mock backend services

2. **Integration Tests**
   - End-to-end validation flow
   - End-to-end execution flow
   - MCP integration tests

3. **Documentation**
   - API documentation for widgets
   - Event bus documentation
   - MCP integration guide

---

## Summary

### Answer to Your Questions

**Q: Should there be a first empty widget which passes events, with sub-widgets?**
**A:** ❌ NO. Use independent widgets + coordination service.

**Q: Is container widget possible in Theia?**
**A:** ✅ YES, but NOT recommended for your case. You'd lose panel system.

**Q: Is it desirable?**
**A:** ❌ NO. Independent widgets with coordination service is better.

**Q: How should widgets communicate?**
**A:** Use **Theia Event Bus** (type-safe events) coordinated by a **service** (not a widget).

**Q: How does MCP play a role?**
**A:** MCP is accessed through the backend service layer:
- Widgets → Coordination Service → UTLXService (frontend proxy)
- → Backend Service → UTLXD → MCP Servers

### Recommended Architecture

```
✅ Independent Widgets (current - keep it!)
   ├── Toolbar, Input, Editor, Output in separate shell areas
   └── Each widget manages its own UI

✅ Coordination Service (create this!)
   ├── Plain TypeScript class (not a widget)
   ├── Listens to events from widgets
   ├── Coordinates header updates
   └── Calls backend services

✅ Theia Event Bus (migrate to this)
   ├── Type-safe events
   ├── Better debugging
   └── Follows Theia patterns

✅ Backend Services (already have this!)
   ├── UTLXService frontend proxy
   ├── UTLXServiceImpl backend implementation
   └── Calls UTLXD

✅ UTLXD + MCP (external layer)
   ├── UTLXD daemon handles transformations
   └── MCP servers provide AI/validation
```

**Next Steps:**
1. Create `UTLXCoordinationService`
2. Register it in `frontend-module.ts`
3. Test header updates work
4. Migrate to Theia Event Bus
5. Integrate MCP operations

---

**End of Document**
