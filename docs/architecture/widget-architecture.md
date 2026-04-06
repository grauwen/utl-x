# UTLX Theia Extension - Widget Architecture

**Document Version:** 1.0
**Last Updated:** 2025-11-08
**Status:** Current State Analysis + Proposed Architecture

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [All Widgets in the System](#all-widgets-in-the-system)
3. [Current Architecture](#current-architecture)
4. [Architecture Problems](#architecture-problems)
5. [Proposed Architecture](#proposed-architecture)
6. [Widget Communication Patterns](#widget-communication-patterns)
7. [Widget Lifecycle](#widget-lifecycle)
8. [Immediate Action Items](#immediate-action-items)
9. [Long-term Recommendations](#long-term-recommendations)

---

## Executive Summary

The UTLX Theia extension currently has **8 registered widgets**, but only **4 are actively displayed**. The architecture has evolved from a **workbench-container approach** to a **direct shell layout** approach, but legacy code remains, causing confusion and bugs.

**Critical Issues:**
- ✅ Widgets are bound and created correctly
- ❌ Black screen caused by CSS bug (vertical tabs only 20px wide)
- ❌ Duplicate architectures (Direct Shell vs Workbench Container)
- ❌ Dead code in UTLXWorkbenchWidget.render() method
- ❌ Unused ModeSelectorWidget (bound but never opened)
- ❌ Timing issues with async widget loading

**Recommended Approach:**
- Use **Direct Shell Layout** (already active)
- Keep **UTLXWorkbenchWidget** for coordination only
- Remove unused widgets and dead code
- Fix CSS issues immediately

---

## All Widgets in the System

### 1. MultiInputPanelWidget
**File:** `src/browser/input-panel/multi-input-panel-widget.tsx`
**ID:** `INPUT_PANEL_ID` (from protocol)
**Status:** ✅ Active (displayed in left panel)
**Build Number:** 4

**Purpose:**
- Manages multiple input documents with vertical tabs
- Supports Design-Time mode (Instance/Schema tabs)
- Format selection (JSON, XML, YAML, CSV)
- Load from file, clear, rename functionality

**Location:** Theia Shell → Left Panel Area

**Key Features:**
- Vertical tabs on left (20px wide - **BUG: too narrow!**)
- Content area on right with editor
- Horizontal tabs for Instance/Schema in Design-Time mode
- Dispatches `utlx-input-format-changed` events

---

### 2. OutputPanelWidget
**File:** `src/browser/output-panel/output-panel-widget.tsx`
**ID:** `OUTPUT_PANEL_ID` (from protocol)
**Status:** ✅ Active (displayed in right panel)
**Build Number:** 2

**Purpose:**
- Displays transformation output or inferred schema
- Supports Design-Time mode (Instance/Schema tabs)
- Format selection and viewing modes
- Copy, save, and clear functionality

**Location:** Theia Shell → Right Panel Area

**Key Features:**
- Horizontal tabs for Instance/Schema
- Format dropdown (JSON, XML, YAML, CSV)
- View mode toggle (formatted/raw)
- Dispatches `utlx-output-format-changed` events

---

### 3. UTLXEditorWidget
**File:** `src/browser/editor/utlx-editor-widget.tsx`
**ID:** `utlx-editor`
**Status:** ✅ Active (displayed in main area)
**Build Number:** 1
**Binding:** Singleton

**Purpose:**
- Monaco-based code editor for UTLX transformations
- Read-only headers (auto-generated from input/output formats)
- Drag-and-drop file support
- Syntax highlighting and LSP integration (future)

**Location:** Theia Shell → Main Area (Center)

**Key Features:**
- Auto-updating headers based on input/output formats
- Read-only header lines enforcement
- Drag-and-drop file loading
- Dispatches `utlx-content-changed` events (on widget node)

---

### 4. UTLXToolbarWidget
**File:** `src/browser/toolbar/utlx-toolbar-widget.tsx`
**ID:** `utlx-toolbar`
**Status:** ✅ Active (displayed in top area)
**Build Number:** N/A

**Purpose:**
- Top toolbar with mode indicator
- Mode toggle switch (Runtime ↔ Design-Time)
- AI Assist button (MCP integration)
- Execute button

**Location:** Theia Shell → Top Area

**Key Features:**
- Mode indicator badge
- Toggle switch with labels
- MCP dialog for AI-assisted code generation
- Dispatches `utlx-mode-changed` events

---

### 5. UTLXWorkbenchWidget
**File:** `src/browser/workbench/utlx-workbench-widget.tsx`
**ID:** `utlx-workbench`
**Status:** ⚠️ Created but NOT displayed (coordination only)
**Build Number:** 2

**Purpose:**
- **Originally:** Container widget for 3-pane layout
- **Currently:** Coordination widget for event handling and header updates
- Registers command handlers
- Manages widget references

**Location:** Created but NOT added to Theia Shell

**Key Features:**
- Holds references to input, editor, output, mode selector widgets
- Listens for format change events
- Updates editor headers based on input/output formats
- **Problem:** Has render() method that creates 3-pane UI, but it's never displayed!

**Critical Issue:**
```typescript
// This render() method returns JSX that is NEVER shown to the user!
protected render(): React.ReactNode {
    // Lines 130-229 create a full 3-pane layout with header/actions
    // BUT this widget is never added to the shell!
    // This is DEAD CODE
}
```

---

### 6. ModeSelectorWidget
**File:** `src/browser/mode-selector/mode-selector-widget.tsx`
**ID:** `MODE_SELECTOR_ID` (from protocol)
**Status:** ❌ Bound but NEVER opened (unused)
**Build Number:** 2

**Purpose:**
- Mode selection UI (Design-Time ↔ Runtime)
- Auto-infer schema checkbox
- Type checking toggle
- Mode description and info boxes

**Location:** Bound in DI container, but never opened

**Problem:**
- This widget provides mode selection UI
- BUT: UTLXToolbarWidget ALSO has mode selection
- Only toolbar version is used
- ModeSelectorWidget is orphaned/unused

**Decision Needed:** Remove or integrate into toolbar

---

### 7. HealthMonitorWidget
**File:** `src/browser/health-monitor/health-monitor-widget.tsx`
**ID:** `utlx-health-monitor`
**Status:** ❌ Bound but NEVER opened

**Purpose:**
- Monitor UTLXD and MCP server health
- Real-time pinging (2-second interval)
- Status indicators with icons

**Location:** Bound in DI container, but never opened

**Current Alternative:**
- Health status is shown in Theia **status bar** (bottom)
- Uses `initializeHealthStatus()` in frontend-contribution

**Decision Needed:** Keep status bar only, or show widget in bottom panel?

---

### 8. TestWidget
**File:** `src/browser/test-widget.tsx`
**ID:** `test-widget`
**Status:** ❌ Bound but NEVER opened (debugging only)

**Purpose:**
- Simple test widget for debugging Theia integration
- Displays "This is a test widget" message

**Location:** Bound in DI container, can be opened manually

**Status:** Development/debugging tool only

---

## Current Architecture

### Active Layout: Direct Shell Approach

```
┌─────────────────────────────────────────────────────────────────┐
│  Theia Application Shell                                        │
├─────────────────────────────────────────────────────────────────┤
│  TOP AREA: UTLXToolbarWidget                                    │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ [Runtime Mode] [Toggle] [AI Assist] [Execute]             │ │
│  └───────────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┬─────────────────────────┬──────────────────┐ │
│  │ LEFT PANEL  │   MAIN AREA (Center)    │  RIGHT PANEL     │ │
│  │             │                         │                  │ │
│  │ MultiInput  │   UTLXEditorWidget      │  OutputPanel     │ │
│  │ PanelWidget │                         │  Widget          │ │
│  │             │   ┌──────────────────┐  │                  │ │
│  │ [Tabs]      │   │ Monaco Editor    │  │  [Instance] ◄──┐ │ │
│  │ ┌─┐         │   │                  │  │  [Schema]      │ │ │
│  │ │I│ Input1  │   │  %utlx 1.0       │  │                │ │ │
│  │ └─┘         │   │  input json      │  │  Format: JSON  │ │ │
│  │ ┌─┐         │   │  output json     │  │  ┌───────────┐ │ │ │
│  │ │I│ Input2  │   │                  │  │  │           │ │ │ │
│  │ └─┘         │   │  // transform    │  │  │  Output   │ │ │ │
│  │ [+]         │   │                  │  │  │  Content  │ │ │ │
│  │             │   └──────────────────┘  │  │           │ │ │ │
│  │ Format:JSON │                         │  └───────────┘ │ │ │
│  │ ┌─────────┐ │                         │                │ │ │
│  │ │ Content │ │                         │  [Copy] [Save] │ │ │
│  │ └─────────┘ │                         │                │ │ │
│  │             │                         │                │ │ │
│  └─────────────┴─────────────────────────┴──────────────────┘ │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  BOTTOM: Status Bar (Health Status shown here)                  │
│  UTLXD: ✓ Connected | MCP: ✓ Connected                         │
└─────────────────────────────────────────────────────────────────┘

BEHIND THE SCENES (Not Displayed):
  UTLXWorkbenchWidget
    - Coordinates header updates
    - Listens to format change events
    - Updates editor headers
    - Registers commands
```

### Widget Opening Sequence

**Frontend Contribution `onStart()` method:**

```typescript
async onStart(app: FrontendApplication): Promise<void> {
    // 1. Initialize health status in status bar
    await this.initializeHealthStatus();

    // 2. Open toolbar in top area
    await this.openToolbar();

    // 3. Open 3-column layout
    await this.open3ColumnLayout();
        // - Create MultiInputPanelWidget → add to 'left'
        // - Create UTLXEditorWidget → add to 'main'
        // - Create OutputPanelWidget → add to 'right'
        // - Activate all three
        // - Expand left/right panel handlers

    // 4. Initialize workbench coordination (NOT displayed!)
    await this.initializeWorkbenchCoordination();
        // - Create UTLXWorkbenchWidget
        // - Does NOT add to shell
        // - Widget loads child widgets asynchronously
        // - Sets up event listeners for coordination
}
```

### Inactive Layout: Workbench Container Approach

**This layout is defined in UTLXWorkbenchWidget.render() but NEVER displayed:**

```typescript
// UTLXWorkbenchWidget.render() - DEAD CODE!
return (
    <div className='utlx-workbench-container'>
        <div className='utlx-workbench-header'>
            <h2>UTL-X Transformation Workbench</h2>
            <div className='utlx-workbench-actions'>
                <button>Validate</button>
                <button>Execute/Infer Schema</button>
                <button>Clear All</button>
            </div>
        </div>
        <div className='utlx-workbench-body'>
            <div className='utlx-workbench-panes'>
                <div className='utlx-workbench-pane left'>
                    {/* InputPanel DOM node appended here */}
                </div>
                <div className='utlx-workbench-pane middle'>
                    {/* EditorWidget DOM node appended here */}
                </div>
                <div className='utlx-workbench-pane right'>
                    {/* OutputPanel DOM node appended here */}
                </div>
            </div>
        </div>
    </div>
);
```

**Why this exists:**
- Original design: Workbench as container
- Refactored to use Theia's native panel system
- Workbench kept for coordination logic
- Render method never removed (dead code)

---

## Architecture Problems

### Problem 1: Black Screen - Vertical Tabs CSS Bug

**File:** `src/browser/style/index.css`
**Lines:** 615-625

```css
.utlx-vertical-tabs {
    display: flex;
    flex-direction: column;
    width: 20px;      /* ⚠️ CRITICAL BUG: TOO NARROW! */
    min-width: 20px;
    /* ... */
}
```

**Impact:**
- Vertical tabs are only 20 pixels wide
- Tab text is cut off or invisible
- Users see mostly black/empty left panel
- Content area takes up 99% of width

**Fix:**
```css
.utlx-vertical-tabs {
    width: 120px;    /* Enough for tab labels */
    min-width: 100px;
}
```

---

### Problem 2: Duplicate/Conflicting Architectures

**Two layout systems coexist:**

| Approach | Status | Location | Widgets |
|----------|--------|----------|---------|
| **Direct Shell Layout** | ✅ ACTIVE | `utlx-frontend-contribution.ts` | Input, Editor, Output added to Theia shell areas |
| **Workbench Container** | ❌ INACTIVE | `UTLXWorkbenchWidget.render()` | Same widgets referenced via DOM nodes |

**Why this is confusing:**
- UTLXWorkbenchWidget has a full render() method (130+ lines)
- It creates a 3-pane layout with header/actions
- BUT: Widget is never added to shell, so UI never shows
- Developer might think workbench widget controls the layout
- In reality, frontend-contribution controls the layout

**Solution:** Remove render() method from UTLXWorkbenchWidget

---

### Problem 3: Unused ModeSelectorWidget

**Duplication:**
- `ModeSelectorWidget`: Bound in DI, never opened, full-featured UI
- `UTLXToolbarWidget`: Has its own mode toggle, actively used

**Why ModeSelectorWidget exists:**
- More feature-rich (checkboxes for auto-infer schema, type checking)
- Better UX for mode details
- Original mode selection widget

**Why it's not used:**
- Toolbar approach is simpler
- Mode toggle always visible in top bar
- No need for separate widget

**Solution:**
- Option A: Remove ModeSelectorWidget entirely
- Option B: Use ModeSelectorWidget in toolbar (replace toggle)
- Option C: Clicking toolbar toggle opens ModeSelectorWidget for details

---

### Problem 4: Timing Issues with loadWidgets()

**File:** `src/browser/workbench/utlx-workbench-widget.tsx`
**Lines:** 84-127

```typescript
@postConstruct()
protected init(): void {
    this.update();
    this.registerCommands();

    // loadWidgets() is async!
    this.loadWidgets().catch(error => {
        console.error('[Workbench] loadWidgets() failed:', error);
    });
}

private async loadWidgets(): Promise<void> {
    // These widgets are already created by open3ColumnLayout()!
    this.inputPanel = await this.widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID);
    this.editorWidget = await this.widgetManager.getOrCreateWidget(UTLXEditorWidget.ID);
    this.outputPanel = await this.widgetManager.getOrCreateWidget(OutputPanelWidget.ID);
    this.modeSelector = await this.widgetManager.getOrCreateWidget(ModeSelectorWidget.ID);

    // Event listeners set up AFTER widgets might already be shown
    // Initial events might be missed!
}
```

**Race Condition:**
1. `open3ColumnLayout()` creates Input/Editor/Output widgets
2. Widgets are attached to shell and shown
3. `initializeWorkbenchCoordination()` creates UTLXWorkbenchWidget
4. Workbench widget calls `loadWidgets()` asynchronously
5. Event listeners set up after widgets are already active
6. Initial format changes might not trigger header updates

**Solution:**
- Option A: Move event listeners to frontend-contribution
- Option B: Set up listeners synchronously, load widgets async
- Option C: Trigger initial header update after loadWidgets() completes

---

### Problem 5: Dead Code in render() Method

**File:** `src/browser/workbench/utlx-workbench-widget.tsx`
**Lines:** 130-229

```typescript
protected render(): React.ReactNode {
    // 100 lines of JSX that create a full UI
    // Header, buttons, 3-pane layout, etc.
    // BUT: This widget is NEVER added to the shell!
    // User never sees this UI
    // This is DEAD CODE that should be removed
}
```

**Confusion caused:**
- Developers might think this UI is shown
- Makes debugging harder
- Code maintenance overhead
- Unclear what workbench widget actually does

**Solution:** Remove render() method entirely, keep coordination logic only

---

## Proposed Architecture

### Architectural Principles

1. **Single Responsibility:** Each widget has ONE clear purpose
2. **Direct Shell Layout:** Use Theia's native panel system
3. **Event-Driven Coordination:** Widgets communicate via events
4. **No Duplication:** Remove redundant widgets and dead code
5. **Clear Lifecycle:** Synchronous setup, async loading as needed

### Recommended Widget Structure

```
DISPLAYED WIDGETS (User-Visible):
├── UTLXToolbarWidget (Top Area)
│   - Mode indicator + toggle
│   - AI Assist button
│   - Execute button
│
├── MultiInputPanelWidget (Left Panel)
│   - Vertical tabs (FIXED: 120px width)
│   - Input content editor
│   - Format selection
│
├── UTLXEditorWidget (Main Area)
│   - Monaco editor
│   - Auto-updating headers
│   - Drag-and-drop support
│
└── OutputPanelWidget (Right Panel)
    - Output display
    - Format selection
    - Copy/save actions

COORDINATION WIDGETS (Behind-the-Scenes):
├── UTLXWorkbenchWidget
│   - Listens for format change events
│   - Updates editor headers
│   - Registers command handlers
│   - NO render() method (coordination only)
│
└── UTLXFrontendContribution
    - Creates and opens widgets
    - Manages lifecycle
    - Initializes health status

REMOVED WIDGETS:
├── ModeSelectorWidget (redundant with toolbar)
└── HealthMonitorWidget (use status bar only)
```

### Widget Communication Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  Event Flow: Input Format Change → Editor Header Update         │
└─────────────────────────────────────────────────────────────────┘

1. User changes format in MultiInputPanelWidget
   ↓
2. MultiInputPanelWidget.handleFormatChange()
   ↓
3. Dispatches window event: 'utlx-input-format-changed'
   {
     format: 'json',
     isSchema: false,
     inputId: 'input-1',
     inputName: 'Customer Data'
   }
   ↓
4. UTLXWorkbenchWidget listens for event
   ↓
5. UTLXWorkbenchWidget.updateEditorHeaders()
   ↓
6. Reads all input documents from inputPanel
   ↓
7. Reads output format from outputPanel
   ↓
8. Constructs header lines:
   input json name=CustomerData
   output json
   ↓
9. Calls editorWidget.updateHeaders(inputLines, outputFormat)
   ↓
10. UTLXEditorWidget updates Monaco model content
    ↓
11. Headers are marked read-only
    ↓
12. User sees updated headers in editor!
```

### Mode Change Event Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  Event Flow: Mode Toggle → All Widgets Update                   │
└─────────────────────────────────────────────────────────────────┘

1. User clicks mode toggle in UTLXToolbarWidget
   ↓
2. UTLXToolbarWidget.toggleMode()
   ↓
3. Updates internal state: currentMode = DESIGN_TIME
   ↓
4. Dispatches window event: 'utlx-mode-changed'
   { mode: UTLXMode.DESIGN_TIME }
   ↓
5. Listeners update:
   ├─ MultiInputPanelWidget → Shows Instance/Schema tabs
   ├─ OutputPanelWidget → Shows Instance/Schema tabs
   └─ UTLXWorkbenchWidget → Calls handleModeChange()
       ↓
       └─ Notifies input/output panels (if needed)
       └─ Clears output
```

---

## Widget Communication Patterns

### Event-Based Communication (Window Events)

**Standard Pattern:**
```typescript
// Dispatcher (e.g., MultiInputPanelWidget)
window.dispatchEvent(new CustomEvent('utlx-input-format-changed', {
    detail: {
        format: this.state.format,
        inputId: this.state.activeInputId,
        inputName: activeInput.name
    }
}));

// Listener (e.g., UTLXWorkbenchWidget)
window.addEventListener('utlx-input-format-changed', (event: Event) => {
    const customEvent = event as CustomEvent;
    const { format, inputId } = customEvent.detail;
    this.handleInputFormatChange(format, inputId);
});
```

**All Events:**

| Event Name | Dispatched By | Listened By | Payload |
|------------|---------------|-------------|---------|
| `utlx-mode-changed` | UTLXToolbarWidget | Input, Output, Workbench | `{ mode: UTLXMode }` |
| `utlx-input-format-changed` | MultiInputPanelWidget | Workbench | `{ format, isSchema, inputId, inputName }` |
| `utlx-output-format-changed` | OutputPanelWidget | Workbench | `{ format, tab }` |
| `utlx-content-changed` | UTLXEditorWidget | Workbench | `{ content }` (on widget.node, not window!) |
| `utlx-generated` | UTLXToolbarWidget | (future: Editor) | `{ prompt, utlx }` |

### Service-Based Communication

**All widgets can inject:**
- `UTLXService` - Backend RPC calls
- `MessageService` - User notifications
- `CommandRegistry` - Register/execute commands
- `WidgetManager` - Create/get widgets

**Backend Service Calls:**
```typescript
// Execute transformation
const result = await this.utlxService.execute(code, inputs);

// Infer output schema
const schema = await this.utlxService.inferSchema(code, inputSchema);

// Validate UTLX code
const validation = await this.utlxService.validate(code);

// Get/Set mode
const config = await this.utlxService.getMode();
await this.utlxService.setMode({ mode, autoInferSchema, enableTypeChecking });
```

### Direct Widget References (Workbench Only)

**UTLXWorkbenchWidget holds references:**
```typescript
protected inputPanel?: MultiInputPanelWidget;
protected editorWidget?: UTLXEditorWidget;
protected outputPanel?: OutputPanelWidget;
protected modeSelector?: ModeSelectorWidget;

// Loaded via:
this.inputPanel = await this.widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID);
```

**Used for:**
- Calling widget public methods
- Reading widget state
- Coordinating header updates

---

## Widget Lifecycle

### Initialization Sequence Diagram

```
┌─────────────┐
│ Application │
│   Start     │
└──────┬──────┘
       │
       v
┌──────────────────────────────────────────────┐
│ FrontendApplicationContribution.onStart()    │
└──────┬───────────────────────────────────────┘
       │
       ├─ 1. initializeHealthStatus()
       │    └─> Add UTLXD/MCP indicators to status bar
       │
       ├─ 2. openToolbar()
       │    ├─> Create UTLXToolbarWidget
       │    ├─> Add to 'top' area
       │    └─> Activate widget
       │
       ├─ 3. open3ColumnLayout()
       │    ├─> Create MultiInputPanelWidget
       │    │    ├─> @postConstruct init()
       │    │    ├─> update() called
       │    │    └─> Adds to DI container
       │    ├─> Add to 'left' area
       │    │
       │    ├─> Create UTLXEditorWidget
       │    │    ├─> @postConstruct init()
       │    │    ├─> Monaco editor NOT yet created
       │    │    └─> Adds to DI container
       │    ├─> Add to 'main' area
       │    │
       │    ├─> Create OutputPanelWidget
       │    │    ├─> @postConstruct init()
       │    │    ├─> update() called
       │    │    └─> Adds to DI container
       │    ├─> Add to 'right' area
       │    │
       │    ├─> Activate all three widgets
       │    │    ├─> onAfterAttach() called
       │    │    ├─> onAfterShow() called
       │    │    └─> render() called
       │    │
       │    └─> Expand left/right panel handlers
       │
       └─ 4. initializeWorkbenchCoordination()
            ├─> Create UTLXWorkbenchWidget
            │    ├─> @postConstruct init()
            │    ├─> registerCommands()
            │    └─> loadWidgets() async (⚠️ Race condition!)
            │
            └─> Widget NOT added to shell
                 ├─> onAfterAttach() NEVER called
                 ├─> onAfterShow() NEVER called
                 └─> render() NEVER called (dead code!)

┌──────────────────────────────────────────────┐
│ After Layout Restore                         │
└──────┬───────────────────────────────────────┘
       │
       └─> UTLXEditorWidget.createEditor()
            ├─> Monaco editor created
            ├─> Container attached
            └─> Read-only headers enforced
```

### Widget Lifecycle Methods (ReactWidget)

**Standard Theia ReactWidget Lifecycle:**

```typescript
constructor()
    ↓
@postConstruct init()
    ↓
onBeforeAttach(msg)     // Before added to DOM
    ↓
onAfterAttach(msg)      // After added to DOM
    ↓
onBeforeShow(msg)       // Before made visible
    ↓
onAfterShow(msg)        // After made visible
    ↓
render()                // React render method (called by update())
    ↓
onUpdateRequest(msg)    // When update() is called
    ↓
onResize(msg)           // When widget resized
    ↓
onActivateRequest(msg)  // When widget activated
    ↓
onBeforeHide(msg)       // Before hidden
    ↓
onAfterHide(msg)        // After hidden
    ↓
onBeforeDetach(msg)     // Before removed from DOM
    ↓
onAfterDetach(msg)      // After removed from DOM
```

**IMPORTANT:**
- `render()` is ONLY called if widget is attached to DOM
- UTLXWorkbenchWidget is NEVER attached, so render() is NEVER called
- This is why the workbench render() method is dead code

---

## Immediate Action Items

### Priority 1: Fix Black Screen (CSS Bug)

**File:** `src/browser/style/index.css`
**Lines:** 615-625

**Current Code:**
```css
.utlx-vertical-tabs {
    display: flex;
    flex-direction: column;
    width: 20px;         /* ⚠️ BUG */
    min-width: 20px;     /* ⚠️ BUG */
    background: var(--theia-sideBar-background);
    border-right: 1px solid var(--theia-panel-border);
    padding: 8px 2px;
    gap: 4px;
    overflow-y: auto;
}
```

**Fixed Code:**
```css
.utlx-vertical-tabs {
    display: flex;
    flex-direction: column;
    width: 120px;         /* ✅ FIXED: Enough for tab labels */
    min-width: 100px;     /* ✅ FIXED: Allow some flexibility */
    background: var(--theia-sideBar-background);
    border-right: 1px solid var(--theia-panel-border);
    padding: 8px 4px;     /* ✅ FIXED: More padding */
    gap: 4px;
    overflow-y: auto;
}
```

**Expected Result:**
- Vertical tabs visible with full labels
- Input names readable
- Left panel functional

---

### Priority 2: Remove Dead Code

**File:** `src/browser/workbench/utlx-workbench-widget.tsx`
**Lines:** 130-229

**Action:** Delete the entire `render()` method

**Current Code:**
```typescript
protected render(): React.ReactNode {
    // 100 lines of JSX creating 3-pane UI
    // DELETE ALL OF THIS
}
```

**Replacement:**
```typescript
// render() method removed - this widget is coordination-only
// It is never attached to the DOM, so no UI is needed
```

**Expected Result:**
- Clearer code
- No confusion about workbench widget purpose
- Smaller bundle size

---

### Priority 3: Fix Timing Issues

**File:** `src/browser/workbench/utlx-workbench-widget.tsx`
**Lines:** 72-82

**Current Code:**
```typescript
@postConstruct()
protected init(): void {
    this.update();
    this.registerCommands();

    // Async widget loading - event listeners set up late!
    this.loadWidgets().catch(error => {
        console.error('[Workbench] loadWidgets() failed:', error);
    });
}
```

**Fixed Code:**
```typescript
@postConstruct()
protected init(): void {
    this.registerCommands();

    // Set up event listeners SYNCHRONOUSLY
    this.setupEventListeners();

    // Load widgets asynchronously (non-blocking)
    this.loadWidgets().catch(error => {
        console.error('[Workbench] loadWidgets() failed:', error);
    });
}

private setupEventListeners(): void {
    // Input format changes
    window.addEventListener('utlx-input-format-changed', (event: Event) => {
        const customEvent = event as CustomEvent;
        this.handleInputFormatChange(customEvent.detail);
    });

    // Output format changes
    window.addEventListener('utlx-output-format-changed', (event: Event) => {
        const customEvent = event as CustomEvent;
        this.handleOutputFormatChange(customEvent.detail);
    });

    // Mode changes
    window.addEventListener('utlx-mode-changed', (event: Event) => {
        const customEvent = event as CustomEvent;
        this.handleModeChange(customEvent.detail.mode);
    });
}

private async loadWidgets(): Promise<void> {
    // Load widget references asynchronously
    this.inputPanel = await this.widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID);
    this.editorWidget = await this.widgetManager.getOrCreateWidget(UTLXEditorWidget.ID);
    this.outputPanel = await this.widgetManager.getOrCreateWidget(OutputPanelWidget.ID);

    // Trigger initial header update after all widgets loaded
    this.updateEditorHeaders();
}
```

**Expected Result:**
- Event listeners ready immediately
- No missed events
- Initial header update works

---

### Priority 4: Remove Unused Widgets

**Files:**
- `src/browser/mode-selector/mode-selector-widget.tsx`
- `src/browser/health-monitor/health-monitor-widget.tsx`
- `src/browser/test-widget.tsx`

**Action:**
1. Remove widget class files
2. Remove bindings from `frontend-module.ts`
3. Update imports in workbench widget

**OR Keep but Document:**
If widgets might be used in future, add clear comments:
```typescript
// ModeSelectorWidget: Bound but not currently used
// Could be integrated into toolbar for advanced mode options
// For now, use UTLXToolbarWidget.toggleMode() instead
```

---

## Long-term Recommendations

### 1. Establish Clear Widget Roles

**Display Widgets** (user-visible UI):
- Must have meaningful `render()` method
- Added to Theia shell areas
- Lifecycle methods (onAfterAttach, onAfterShow) are called
- Examples: Input, Editor, Output, Toolbar

**Coordination Widgets** (behind-the-scenes):
- No `render()` method (or minimal/stub)
- NOT added to shell
- Only used for event handling, command registration
- Examples: UTLXWorkbenchWidget (coordination only)

**Service Classes** (not widgets):
- No UI at all
- Pure logic/coordination
- Injected as services
- Example: Could extract coordination logic from workbench into UTLXCoordinationService

### 2. Consider Extracting Coordination into Service

**Current:**
```
UTLXWorkbenchWidget
  - ReactWidget (unnecessary overhead)
  - Has render() method (dead code)
  - Coordinates header updates
```

**Proposed:**
```
UTLXCoordinationService
  - Plain TypeScript class
  - @injectable()
  - Sets up event listeners
  - Coordinates header updates
  - No React/widget overhead
```

**Benefits:**
- Clearer separation of concerns
- No confusion about widget vs coordination
- Smaller bundle (no React for coordination)
- Easier to test

### 3. Improve Event System

**Current:** Global window events

**Proposed:** Theia Event Emitters

```typescript
// Define event emitters
export const UTLXModeChangedEvent = Symbol('UTLXModeChangedEvent');
export interface UTLXModeChangedEvent {
    mode: UTLXMode;
}

// Dispatcher
@injectable()
export class ModeEventDispatcher {
    @inject(IEventBus)
    protected readonly eventBus: IEventBus;

    dispatchModeChanged(mode: UTLXMode): void {
        this.eventBus.fire(UTLXModeChangedEvent, { mode });
    }
}

// Listener
this.eventBus.onEvent(UTLXModeChangedEvent, (event) => {
    this.handleModeChange(event.mode);
});
```

**Benefits:**
- Type-safe events
- Better IDE support
- Follows Theia patterns
- Easier to debug

### 4. Add Widget Unit Tests

**Test Files:**
```
src/browser/input-panel/multi-input-panel-widget.spec.tsx
src/browser/output-panel/output-panel-widget.spec.tsx
src/browser/editor/utlx-editor-widget.spec.tsx
src/browser/toolbar/utlx-toolbar-widget.spec.tsx
```

**Test Scenarios:**
- Widget initialization
- Event dispatching/listening
- Format changes
- Mode switching
- Command registration
- Header updates

### 5. Document Widget API

**Create:**
- `docs/api/widgets.md` - Public API for each widget
- `docs/api/events.md` - All events and their contracts
- `docs/api/services.md` - Backend service methods

**Example:**
```markdown
# MultiInputPanelWidget API

## Public Methods

### `getInputDocuments(): InputDocument[]`
Returns all input documents managed by the panel.

### `setMode(mode: UTLXMode): void`
Switches between Design-Time and Runtime modes.

## Events Dispatched

### `utlx-input-format-changed`
Fired when user changes input format.
**Payload:** `{ format: string, inputId: string, isSchema: boolean }`

## Events Listened

### `utlx-mode-changed`
Updates UI when mode changes.
**Payload:** `{ mode: UTLXMode }`
```

### 6. Create Architecture Decision Records (ADRs)

**Track key decisions:**
- ADR-001: Why Direct Shell Layout over Workbench Container
- ADR-002: Window Events vs Theia Event Bus
- ADR-003: Coordination Widget vs Service
- ADR-004: Single vs Multiple Input Panels

**Template:**
```markdown
# ADR-001: Use Direct Shell Layout

## Status
Accepted

## Context
Two approaches existed: container widget vs direct shell layout.

## Decision
Use direct Theia shell layout with separate widgets.

## Consequences
+ Leverages Theia's native panel system
+ Collapsible panels work automatically
+ Cleaner separation
- More widgets to manage
- Coordination needed between widgets
```

---

## Summary

### Current State
- ✅ 8 widgets bound, 4 actively displayed
- ✅ Direct shell layout works
- ✅ Event-based communication in place
- ❌ Black screen due to CSS bug (20px tabs)
- ❌ Dead code in workbench render()
- ❌ Timing issues with async loading
- ❌ Unused widgets taking up space

### Immediate Fixes (This Week)
1. Fix vertical tabs CSS (20px → 120px)
2. Remove UTLXWorkbenchWidget.render() method
3. Fix timing by setting up event listeners synchronously
4. Test and verify header updates work

### Short-term Improvements (Next Sprint)
1. Remove or document unused widgets (ModeSelectorWidget, HealthMonitorWidget, TestWidget)
2. Add comprehensive logging for debugging
3. Write widget integration tests
4. Document widget APIs

### Long-term Enhancements (Future)
1. Extract coordination into service class
2. Migrate to Theia Event Bus
3. Create Architecture Decision Records
4. Add comprehensive documentation
5. Consider consolidating input/output panel patterns

---

## Appendix: File Structure

```
src/browser/
├── editor/
│   └── utlx-editor-widget.tsx        [ACTIVE - Main editor]
├── input-panel/
│   ├── input-panel-widget.tsx        [DEPRECATED - old version]
│   ├── input-panel-widget-enhanced.tsx [EXPERIMENTAL]
│   └── multi-input-panel-widget.tsx  [ACTIVE - Left panel]
├── output-panel/
│   └── output-panel-widget.tsx       [ACTIVE - Right panel]
├── mode-selector/
│   └── mode-selector-widget.tsx      [BOUND BUT UNUSED]
├── toolbar/
│   └── utlx-toolbar-widget.tsx       [ACTIVE - Top toolbar]
├── workbench/
│   └── utlx-workbench-widget.tsx     [COORDINATION ONLY]
├── health-monitor/
│   └── health-monitor-widget.tsx     [BOUND BUT UNUSED]
├── style/
│   ├── index.css                     [Main CSS - HAS BUGS]
│   └── toolbar.css                   [Toolbar styles]
├── test-widget.tsx                   [DEBUG ONLY]
├── frontend-module.ts                [Widget bindings]
└── utlx-frontend-contribution.ts     [Widget lifecycle]
```

---

**End of Document**
