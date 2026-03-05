# Graphical Mapping Editor - Implementation Plan

**Document Version:** 1.0
**Created:** 2026-03-04
**Status:** Implementation Plan

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [File Structure](#file-structure)
5. [Implementation Phases](#implementation-phases)
6. [Phase 1: Core Infrastructure](#phase-1-core-infrastructure)
7. [Phase 2: Schema Nodes & Edges](#phase-2-schema-nodes--edges)
8. [Phase 3: Function & Operator Nodes](#phase-3-function--operator-nodes)
9. [Phase 4: Code Generation](#phase-4-code-generation)
10. [Phase 5: Theia Integration](#phase-5-theia-integration)
11. [Phase 6: Code-to-Canvas Visualization](#phase-6-code-to-canvas-visualization)
12. [State & Layout Persistence](#state--layout-persistence)
13. [Data Model](#data-model)
14. [Component Specifications](#component-specifications)
15. [API Integration](#api-integration)
16. [CSS & Theming](#css--theming)
17. [Testing Strategy](#testing-strategy)
18. [Known Limitations](#known-limitations)

---

## Overview

A visual mapping canvas where input and output schemas are rendered as ERD-like node structures with expandable field trees. Users draw connections between fields, optionally inserting stdlib functions/operators as intermediate transformation nodes. The canvas generates UTLX code and visualizes existing simple mappings.

### Design Approach: Option C+ (Hybrid with Generation)

- **Canvas generates code** for mappings created visually (new mappings)
- **Canvas visualizes** existing simple mappings from parsed UTLX (read-only for complex ones)
- **Code remains editable** — user can always switch to code and hand-edit
- **Unsupported constructs** show as opaque "code block" nodes on the canvas

This approach covers ~80% of mapping patterns visually while preserving full code access for complex logic.

### Canvas Layout

```
┌─────────────────────────────────────────────────────────┐
│ [Function Palette]              [Auto-layout] [Zoom]    │
├──────────┬──────────────────────────────┬───────────────┤
│          │                              │               │
│  INPUT   │    Transformation Canvas     │    OUTPUT     │
│  SCHEMA  │                              │    SCHEMA     │
│          │     ┌──────────────┐         │               │
│ ┌Order   │  ┌──│ toUpperCase  │──┐      │  ┌Invoice     │
│ │ Id ────────┘ └──────────────┘  │      │  │ Number     │
│ │ Name ──────────────────────────┼──────── │ Customer   │
│ │ Date ──────── ┌──────────┐    │      │  │ Date       │
│ │ Items[]│      │ formatDt │────┼──────── │ LineItems[]│
│ │ │ Sku  │      └──────────┘    │      │  │ │ Product  │
│ │ │ Qty  │                      └──────── │ │ Amount   │
│ │ │ Price│                         │    │  └───────────│
│ └────────│                         │    │              │
└──────────┴──────────────────────────┴───────────────────┘
```

---

## Architecture

### Synchronization Model

```
Visual Canvas (React Flow graph)
    ↕ bidirectional sync
Internal Mapping Model (Zustand store)
    ↕ one-way generation (model → code)
    ↕ partial reverse (simple code → model)
UTLX Code (Monaco editor)
```

The mapping model is the single source of truth when in canvas mode. The model stores:
- All nodes (schema, function, operator, literal, conditional, code-block)
- All edges (connections between field handles)
- Node positions and layout state

### Integration with Current IDE

The graphical editor is a **new panel/view** toggling with the existing code editor:

```
Current:  [Input] [Code Editor] [Output]
New:      [Input] [Code Editor | Mapping Canvas] [Output]
                   ↑ toggle between views
```

The `UTLXEditorWidget` gains a toggle button to switch between Monaco editor and the mapping canvas. Both views share the same underlying UTLX content — switching from canvas to code shows the generated UTLX, switching from code to canvas attempts to parse it back into a graph.

### Widget Hierarchy

```
UTLXEditorWidget (existing, modified)
├── Monaco Editor (existing, shown in code mode)
└── MappingCanvasWidget (NEW, shown in canvas mode)
    ├── FunctionPalette (searchable sidebar)
    ├── ReactFlowCanvas
    │   ├── InputSchemaNode (custom node)
    │   ├── OutputSchemaNode (custom node)
    │   ├── FunctionNode (custom node)
    │   ├── OperatorNode (custom node)
    │   ├── LiteralNode (custom node)
    │   ├── ConditionalNode (custom node)
    │   └── CodeBlockNode (custom node, opaque UTLX)
    └── CanvasToolbar (auto-layout, zoom, toggle)
```

---

## Technology Stack

| Library | Version | Role | New? |
|---|---|---|---|
| `@xyflow/react` | ^12.x | Interactive node-based canvas | Yes |
| `dagre` | ^0.8.5 | Auto-layout (left-to-right) | Yes |
| `@types/dagre` | ^0.7.x | TypeScript types for dagre | Yes |
| `zustand` | ^4.x | Lightweight state management for mapping model | Yes |
| React 18+ | (existing) | UI framework | No |
| Monaco | (existing) | UTLX code editor | No |

**Note on @dnd-kit:** Initially planned for drag-from-palette, but React Flow v12 has built-in drag support. We'll use React Flow's native drag events instead. If more complex DnD is needed later, `@dnd-kit` can be added.

---

## File Structure

All new files live under the existing Theia extension source:

```
theia-extension/utlx-theia-extension/src/browser/
├── mapping-editor/                          # NEW directory
│   ├── mapping-canvas-widget.tsx            # Main canvas React component
│   ├── mapping-store.ts                     # Zustand store for mapping model
│   ├── mapping-types.ts                     # Type definitions for mapping model
│   ├── mapping-code-generator.ts            # Graph → UTLX code generation
│   ├── mapping-code-parser.ts               # UTLX code → graph (simple mappings)
│   ├── mapping-layout.ts                    # dagre auto-layout logic
│   ├── nodes/                               # Custom React Flow nodes
│   │   ├── input-schema-node.tsx            # Input schema with field tree
│   │   ├── output-schema-node.tsx           # Output schema with field tree
│   │   ├── function-node.tsx                # Stdlib function node
│   │   ├── operator-node.tsx                # Operator node
│   │   ├── literal-node.tsx                 # Constant value node
│   │   ├── conditional-node.tsx             # if/then/else node
│   │   └── code-block-node.tsx              # Opaque UTLX expression node
│   ├── edges/                               # Custom React Flow edges
│   │   └── mapping-edge.tsx                 # Styled edge with type indicators
│   ├── palette/                             # Function palette sidebar
│   │   └── function-palette.tsx             # Searchable function/operator list
│   └── toolbar/                             # Canvas-specific toolbar
│       └── canvas-toolbar.tsx               # Layout, zoom, view toggle controls
├── style/
│   └── mapping-editor.css                   # NEW styles for mapping canvas
```

---

## Implementation Phases

### Phase 1: Core Infrastructure
**Goal:** React Flow canvas renders, dagre layouts, Zustand store works.

| Step | Task | Files |
|---|---|---|
| 1.1 | Install `@xyflow/react`, `dagre`, `@types/dagre`, `zustand` | `package.json` |
| 1.2 | Define mapping type system (`MappingNode`, `MappingEdge`, `MappingModel`) | `mapping-types.ts` |
| 1.3 | Create Zustand store with node/edge CRUD operations | `mapping-store.ts` |
| 1.4 | Create `MappingCanvasWidget` with React Flow provider, empty canvas | `mapping-canvas-widget.tsx` |
| 1.5 | Implement dagre auto-layout (left-to-right, input→functions→output) | `mapping-layout.ts` |
| 1.6 | Add basic CSS for canvas container | `mapping-editor.css` |

### Phase 2: Schema Nodes & Edges
**Goal:** Input/output schemas render as expandable field trees with connection handles.

| Step | Task | Files |
|---|---|---|
| 2.1 | Create `InputSchemaNode` — expandable field tree, handles on right side | `input-schema-node.tsx` |
| 2.2 | Create `OutputSchemaNode` — expandable field tree, handles on left side | `output-schema-node.tsx` |
| 2.3 | Create `MappingEdge` with type-colored styling | `mapping-edge.tsx` |
| 2.4 | Wire schema data from existing `UdmField[]` / `SchemaFieldInfo[]` into nodes | `mapping-store.ts` |
| 2.5 | Implement edge creation: drag from input field handle to output field handle | `mapping-canvas-widget.tsx` |
| 2.6 | Implement edge deletion (click + Delete key, or right-click context menu) | `mapping-canvas-widget.tsx` |
| 2.7 | Handle nested fields: expand/collapse toggles within nodes | `input-schema-node.tsx`, `output-schema-node.tsx` |

### Phase 3: Function & Operator Nodes
**Goal:** Users can add transformation functions between input and output.

| Step | Task | Files |
|---|---|---|
| 3.1 | Create `FunctionNode` — shows name, typed input ports, output port | `function-node.tsx` |
| 3.2 | Create `OperatorNode` — compact view, input/output ports | `operator-node.tsx` |
| 3.3 | Create `LiteralNode` — editable constant value with output port | `literal-node.tsx` |
| 3.4 | Create `ConditionalNode` — condition/true/false inputs, one output | `conditional-node.tsx` |
| 3.5 | Create `FunctionPalette` — searchable sidebar using `/api/functions` | `function-palette.tsx` |
| 3.6 | Implement drag-from-palette to canvas (React Flow native drag) | `function-palette.tsx`, `mapping-canvas-widget.tsx` |
| 3.7 | Implement inline parameter editing on function nodes | `function-node.tsx` |
| 3.8 | Add port type compatibility checking (warn on type mismatch) | `mapping-store.ts` |

### Phase 4: Code Generation
**Goal:** The graph generates valid UTLX code.

| Step | Task | Files |
|---|---|---|
| 4.1 | Implement graph traversal: for each output field, trace back through edges | `mapping-code-generator.ts` |
| 4.2 | Generate direct mappings: `OutputField: $input.InputField` | `mapping-code-generator.ts` |
| 4.3 | Generate function-wrapped mappings: `OutputField: fn($input.Field)` | `mapping-code-generator.ts` |
| 4.4 | Generate chained functions: `OutputField: fn2(fn1($input.Field))` | `mapping-code-generator.ts` |
| 4.5 | Generate operator expressions: `OutputField: $input.A + $input.B` | `mapping-code-generator.ts` |
| 4.6 | Generate conditional expressions: `OutputField: if (cond) then a else b` | `mapping-code-generator.ts` |
| 4.7 | Handle literal values in expressions | `mapping-code-generator.ts` |
| 4.8 | Generate complete UTLX with headers (preserve existing headers) | `mapping-code-generator.ts` |
| 4.9 | Create `CodeBlockNode` for non-visual UTLX expressions | `code-block-node.tsx` |

### Phase 5: Theia Integration
**Goal:** Canvas is embedded in the existing IDE, toggles with Monaco editor.

| Step | Task | Files |
|---|---|---|
| 5.1 | Add toggle button to `UTLXEditorWidget` header (Code / Canvas) | `utlx-editor-widget.tsx` |
| 5.2 | Conditionally render Monaco or MappingCanvasWidget based on mode | `utlx-editor-widget.tsx` |
| 5.3 | Wire event service: `onInputUdmUpdated` → update input schema nodes | `mapping-canvas-widget.tsx` |
| 5.4 | Wire event service: `onOutputFormatChanged` → update output schema node | `mapping-canvas-widget.tsx` |
| 5.5 | Wire event service: `onInputSchemaFieldTree` → update schema-aware nodes | `mapping-canvas-widget.tsx` |
| 5.6 | Sync toggle: when switching code→canvas, attempt to parse UTLX into graph | `mapping-code-parser.ts` |
| 5.7 | Sync toggle: when switching canvas→code, generate UTLX and set in Monaco | `mapping-code-generator.ts` |
| 5.8 | Add `CanvasToolbar` with auto-layout button, zoom controls, fit-view | `canvas-toolbar.tsx` |
| 5.9 | Register CSS in `frontend-module.ts` style import | `frontend-module.ts` |

### Phase 6: Code-to-Canvas Visualization
**Goal:** Existing UTLX code renders as a visual graph (for simple mappings).

| Step | Task | Files |
|---|---|---|
| 6.1 | Parse simple field-to-field mappings: `Key: $input.field` | `mapping-code-parser.ts` |
| 6.2 | Parse single-function mappings: `Key: fn($input.field)` | `mapping-code-parser.ts` |
| 6.3 | Parse chained functions: `Key: fn2(fn1($input.field))` | `mapping-code-parser.ts` |
| 6.4 | Parse operator expressions: `Key: $input.a + $input.b` | `mapping-code-parser.ts` |
| 6.5 | Wrap unparseable expressions in `CodeBlockNode` | `mapping-code-parser.ts` |
| 6.6 | Request new daemon API `POST /api/decompose` for complex expression parsing | Backend (Kotlin) |

---

## State & Layout Persistence

**Decision: Zustand in-memory only — no browser or file persistence.**

All graphical state (node positions, expanded/collapsed fields, viewport zoom/pan, selected edges) lives exclusively in the Zustand store. When the user closes the tab or navigates away, layout state is lost. When a UTLX file is loaded or the user switches from code to canvas, dagre auto-layout rebuilds the graph from scratch.

### Rationale

- **Deployment flexibility** — UTLX IDE may run as SaaS, embedded in a 3rd party IDE, or as an Electron app. Browser storage APIs (localStorage, sessionStorage, IndexedDB) behave differently across these contexts, especially in embedded webviews. In-memory avoids all cross-platform concerns.
- **No stale data** — No cleanup, eviction, or key-management logic needed.
- **Simplicity** — Zustand is already the store for the mapping model (nodes, edges). Layout state is just additional fields in the same store. Zero additional dependencies.
- **Deterministic layout** — dagre produces consistent left-to-right layouts. Users get a clean starting point every time, which is predictable and debuggable.
- **Matches existing patterns** — All other IDE widgets (input panel, output panel, editor) use in-memory React state with no persistence. Only prompt history uses localStorage.

### What the Zustand Store Tracks

| State | Type | Lifetime |
|---|---|---|
| Node positions (x, y) | `Record<string, {x, y}>` | Session (in-memory) |
| Expanded field paths | `Set<string>` | Session (in-memory) |
| Viewport (zoom, pan offset) | `{x, y, zoom}` | Session (in-memory) |
| Selected node/edge IDs | `string[]` | Session (in-memory) |
| View mode (code/canvas) | `'code' \| 'canvas'` | Session (in-memory) |
| Function palette search query | `string` | Session (in-memory) |

### Future Option

If user feedback indicates that losing layout on refresh is a problem, **sessionStorage** (keyed by UTLX content hash) can be added later with minimal changes — just serialize/deserialize the layout slice of the Zustand store. This would survive page refreshes within the same tab without persisting across sessions.

---

## Data Model

### mapping-types.ts

```typescript
// ─── Node Types ───

export type MappingNodeType =
  | 'inputSchema'
  | 'outputSchema'
  | 'function'
  | 'operator'
  | 'literal'
  | 'conditional'
  | 'codeBlock';

/** A field within a schema node, with a unique handle ID */
export interface SchemaField {
  id: string;              // unique handle ID: "input.order.id"
  name: string;            // display name: "id"
  path: string;            // full dot path: "order.id"
  type: string;            // "string" | "number" | "object" | "array" | ...
  children?: SchemaField[];
  isRequired?: boolean;
  schemaType?: string;     // original schema type (xs:string, etc.)
  constraints?: string;
}

/** Data carried by an input schema node */
export interface InputSchemaNodeData {
  kind: 'inputSchema';
  inputName: string;       // e.g. "input", "orders"
  format: string;          // "json", "xml", "csv", ...
  fields: SchemaField[];
  isArray: boolean;
}

/** Data carried by an output schema node */
export interface OutputSchemaNodeData {
  kind: 'outputSchema';
  format: string;
  fields: SchemaField[];
}

/** Data carried by a function node */
export interface FunctionNodeData {
  kind: 'function';
  functionName: string;    // e.g. "toUpperCase"
  category: string;
  parameters: FunctionParam[];
  returnType: string;
  signature: string;
}

export interface FunctionParam {
  name: string;
  type: string;
  handleId: string;        // unique handle ID for this parameter port
  literalValue?: string;   // inline literal value (if user typed it)
  optional?: boolean;
}

/** Data carried by an operator node */
export interface OperatorNodeData {
  kind: 'operator';
  symbol: string;          // "+", "==", "? :"
  name: string;
  arity: number;           // 1 (unary) or 2 (binary) or 3 (ternary)
}

/** Data carried by a literal node */
export interface LiteralNodeData {
  kind: 'literal';
  value: string;
  valueType: 'string' | 'number' | 'boolean' | 'null';
}

/** Data carried by a conditional node */
export interface ConditionalNodeData {
  kind: 'conditional';
  // Three input handles: condition, trueBranch, falseBranch
  // One output handle
}

/** Data carried by a code block node (opaque UTLX) */
export interface CodeBlockNodeData {
  kind: 'codeBlock';
  expression: string;      // raw UTLX expression
  outputFieldName: string; // the output field this maps to
}

export type MappingNodeData =
  | InputSchemaNodeData
  | OutputSchemaNodeData
  | FunctionNodeData
  | OperatorNodeData
  | LiteralNodeData
  | ConditionalNodeData
  | CodeBlockNodeData;

// ─── Edge Types ───

export interface MappingEdgeData {
  sourceType?: string;     // type flowing through this edge
  label?: string;          // optional label shown on edge
}

// ─── Mapping Model ───

export interface MappingModel {
  nodes: MappingNode[];
  edges: MappingEdge[];
}

// Re-export React Flow types with our data
import type { Node, Edge } from '@xyflow/react';

export type MappingNode = Node<MappingNodeData>;
export type MappingEdge = Edge<MappingEdgeData>;
```

### mapping-store.ts (Zustand)

```typescript
interface MappingStore {
  // State
  nodes: MappingNode[];
  edges: MappingEdge[];
  viewMode: 'code' | 'canvas';

  // Node CRUD
  addNode: (node: MappingNode) => void;
  removeNode: (nodeId: string) => void;
  updateNodeData: (nodeId: string, data: Partial<MappingNodeData>) => void;
  updateNodePosition: (nodeId: string, position: { x: number; y: number }) => void;

  // Edge CRUD
  addEdge: (edge: MappingEdge) => void;
  removeEdge: (edgeId: string) => void;

  // Schema management
  setInputSchema: (inputName: string, format: string, fields: SchemaField[], isArray: boolean) => void;
  setOutputSchema: (format: string, fields: SchemaField[]) => void;

  // Layout
  autoLayout: () => void;

  // Code sync
  generateUtlx: (headers: string) => string;
  parseUtlxToGraph: (utlx: string, inputFields: SchemaField[], outputFields: SchemaField[]) => void;

  // View toggle
  setViewMode: (mode: 'code' | 'canvas') => void;

  // React Flow callbacks
  onNodesChange: OnNodesChange;
  onEdgesChange: OnEdgesChange;
  onConnect: OnConnect;
}
```

---

## Component Specifications

### InputSchemaNode

Renders an input schema as an expandable field tree. Each leaf field has a **source handle** (small circle) on its right edge. Nested objects/arrays are collapsible.

**Handle IDs:** `input.{inputName}.{field.path}` (e.g., `input.order.items[].sku`)

**Reuses:** Field rendering logic from `field-tree.tsx` — same icons, type badges, expand/collapse behavior. Adapted to render handles instead of insert buttons.

### OutputSchemaNode

Same as InputSchemaNode but mirrored: handles on the **left** edge (target handles). Users connect edges to these.

**Handle IDs:** `output.{field.path}` (e.g., `output.invoice.lineItems[].product`)

### FunctionNode

Compact box showing:
- Function name (header)
- Input ports on left (one per parameter, labeled with param name + type)
- Output port on right (labeled with return type)
- Optional inline text inputs for literal parameter values

**Handle IDs:** `fn.{nodeId}.param.{paramName}` (inputs), `fn.{nodeId}.out` (output)

### OperatorNode

Minimal box:
- Operator symbol displayed prominently (e.g., `+`, `==`)
- Binary: two input ports (left), one output port (right)
- Unary: one input port, one output port
- Ternary (`? :`): three input ports (condition, true, false), one output

### LiteralNode

Small inline-editable box:
- Text input for value
- Dropdown or auto-detect for type (string/number/boolean/null)
- One output port on right

### ConditionalNode

Box with:
- Three labeled input ports: "condition", "then", "else"
- One output port
- Visually distinct (different color/border)

### CodeBlockNode

Opaque box for expressions that can't be visually decomposed:
- Shows the UTLX expression as monospace text
- One output port connecting to the output field
- Read-only — user must edit in code mode
- Visually marked as "code only" (dashed border, muted color)

### FunctionPalette

Sidebar within the canvas view:
- Search input at top
- Categorized list (String, Math, Date, Array, etc.) from `/api/functions`
- Each function is draggable onto the canvas
- Operators section at bottom
- Tooltip on hover showing signature + description

### CanvasToolbar

Horizontal bar above the canvas:
- **Auto-layout** button (triggers dagre re-layout)
- **Fit view** button (zoom to fit all nodes)
- **Zoom in/out** buttons
- **Code/Canvas toggle** (switches view mode)
- **Generate UTLX** button (explicit code generation from canvas)

---

## API Integration

### Existing APIs Used

| API | Used By | Purpose |
|---|---|---|
| `GET /api/functions` | `FunctionPalette` | Load stdlib function registry |
| `GET /api/operators` | `FunctionPalette` | Load operator list |
| `POST /api/validate` | `mapping-code-generator.ts` | Validate generated UTLX |
| `POST /api/infer-schema` | `OutputSchemaNode` | Infer output schema when not provided |

### Existing Frontend Data Reused

| Source | Data | Used By |
|---|---|---|
| `UTLXEventService.onInputUdmUpdated` | Input UDM data | Populate `InputSchemaNode` fields |
| `UTLXEventService.onInputSchemaFieldTree` | Schema field trees | Populate `InputSchemaNode` with type info |
| `UTLXEventService.onOutputFormatChanged` | Output format | Configure `OutputSchemaNode` |
| `parseUdmToTree()` from `udm-parser-new.ts` | Parsed `UdmField[]` | Convert to `SchemaField[]` for nodes |
| `SchemaFieldInfo` from `schema-field-tree-parser.ts` | Schema metadata | Type info for schema-aware nodes |
| `FunctionInfo`, `OperatorInfo` from `protocol.ts` | Stdlib signatures | Populate `FunctionNode` and `FunctionPalette` |

### New Daemon API (Phase 6)

```
POST /api/decompose
Content-Type: application/json

{
  "utlx": "toUpperCase(trim($input.name))",
  "inputSchemas": { "input": { "name": "string" } }
}

Response:
{
  "steps": [
    { "type": "inputRef", "path": "$input.name", "resultType": "string" },
    { "type": "functionCall", "name": "trim", "args": ["$input.name"], "resultType": "string" },
    { "type": "functionCall", "name": "toUpperCase", "args": ["trim($input.name)"], "resultType": "string" }
  ],
  "outputType": "string"
}
```

This API enables Phase 6 (Code→Canvas) without implementing full UTLX AST parsing in TypeScript.

---

## CSS & Theming

New styles in `mapping-editor.css` follow existing Theia dark theme variables:

```css
/* Key CSS custom properties used (from Theia) */
--theia-foreground
--theia-editor-background
--theia-panel-border
--theia-descriptionForeground
--monaco-monospace-font

/* Node type colors (consistent with existing field-tree colors) */
--mapping-input-color: #50fa7b;      /* green - input fields */
--mapping-output-color: #8be9fd;     /* cyan - output fields */
--mapping-function-color: #bd93f9;   /* purple - functions */
--mapping-operator-color: #ffb86c;   /* orange - operators */
--mapping-literal-color: #f1fa8c;    /* yellow - literals */
--mapping-edge-color: #6272a4;       /* muted blue - edges */
--mapping-edge-active: #ff79c6;      /* pink - selected edge */
```

React Flow's default styles are overridden to match the dark theme. Node styling uses the same border-radius, font-size, and padding patterns as the existing function builder dialog.

---

## Testing Strategy

### Unit Tests
- `mapping-code-generator.test.ts` — graph→UTLX for all expression patterns
- `mapping-code-parser.test.ts` — UTLX→graph for supported patterns
- `mapping-store.test.ts` — Zustand store operations (add/remove nodes/edges)
- `mapping-layout.test.ts` — dagre layout produces valid positions

### Integration Tests
- Canvas renders with sample input/output schemas
- Edge creation between compatible field types
- Function node drag-from-palette creates correctly typed node
- Generated UTLX validates against daemon `/api/validate`
- Toggle between code/canvas preserves mapping information

### Manual Test Scenarios
- JSON→JSON simple field mapping (5 fields)
- JSON→XML with function transformations
- XSD→JSON Schema with type-aware connections
- OData→JSON with nested objects and arrays
- Load existing UTLX file → canvas visualizes mappings
- Create mapping visually → switch to code → edit → switch back

---

## Known Limitations

### Out of Scope (v1)

1. **Full bidirectional code↔canvas sync** — Complex UTLX (loops, recursion, lambdas, `map`/`filter`/`reduce`) cannot be visually represented. These show as `CodeBlockNode`.

2. **Array mapping visualization** — `$input.Items |> map(item => { ... })` requires sub-graph/nested canvas. v1 wraps these in CodeBlockNode.

3. **Undo/redo beyond React Flow built-in** — React Flow provides basic undo. Deep undo (cross code/canvas) is deferred.

4. **Collaborative editing** — Single-user only.

5. **Drag-and-drop reordering of function chains** — Users disconnect and reconnect edges manually.

6. **XML namespace handling in visual mode** — Namespaces are handled in generated code but not visually configurable on the canvas.

### Technical Constraints

- React Flow must coexist with Theia's widget system (both use React, but Theia manages the DOM container)
- `@xyflow/react` v12 requires React 17+ (project has React 18, compatible)
- dagre layout is synchronous and may be slow for very large schemas (>200 fields). Mitigation: collapse nested fields by default.
- Zustand is framework-agnostic and lightweight (~1KB), chosen over Redux/MobX for simplicity

---

## Dependencies Between Phases

```
Phase 1 (Infrastructure) ──┐
                            ├── Phase 2 (Schema Nodes) ──┐
                            │                             ├── Phase 4 (Code Gen)
                            ├── Phase 3 (Function Nodes) ─┘        │
                            │                                      │
                            └── Phase 5 (Theia Integration) ───────┘
                                                                   │
                                                    Phase 6 (Code→Canvas) ─── requires daemon API
```

Phases 2 and 3 can proceed in parallel after Phase 1. Phase 4 requires both. Phase 5 can start alongside Phase 4. Phase 6 is independent and requires a new daemon endpoint.
