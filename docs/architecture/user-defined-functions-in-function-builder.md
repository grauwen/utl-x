# User-Defined Functions in Function Builder

## Context

The Function Builder dialog helps users discover and insert functions. Currently it only shows stdlib functions. Users can define their own PascalCase functions in the UTLX body (`function CalculateTax(amount, rate) { ... }`), but these aren't visible in the Function Builder. This makes them hard to discover and reuse.

**Goal:** Add user-defined functions as a "User Functions" category at the top of the existing Standard Library tab, and enable the Expression Editor's "Apply" to auto-route function definitions to the top of the UTLX body.

## Files to Create

### 1. `src/browser/utils/user-function-parser.ts` (NEW)

Regex-based parser to extract user-defined functions from UTLX editor content. Exports:

- **`parseUserFunctions(editorContent: string): { functions: FunctionInfo[], rawBlocks: [...] }`**
  - Finds `---` separator, scans body for `function PascalName(params) { body }`
  - Tracks brace depth for multiline bodies (skip braces inside quoted strings)
  - Returns `FunctionInfo[]` with `category: "User Functions"`, parsed params/return type

- **`splitFunctionDefsAndExpressions(text: string): { functionDefs: string[], expressions: string }`**
  - Used by "Apply" auto-routing. Splits Expression Editor content into function defs vs expression lines
  - Detection: line matches `/^\s*function\s+[A-Z]/`
  - Brace-depth tracking to capture full multiline bodies

- **`USER_FUNCTIONS_CATEGORY = "User Functions"`** — shared constant

Parameter parsing regex: `(\w+)(?:\s*:\s*(\w+))?` extracts name + optional type annotation.

## Files to Modify

### 2. `src/browser/function-builder/function-builder-dialog.tsx`

- **Add prop** `userFunctions: FunctionInfo[]` to `FunctionBuilderDialogProps`
- **Add prop** `onInsertFunctionDefs?: (code: string) => void` for auto-routing
- **Merge user functions into category map** in `functionsByCategory` useMemo:
  - If `userFunctions.length > 0`, add `"User Functions"` entry
  - Sort: "User Functions" always first, then alphabetical
  - Same priority in the search-filtered branch
- **Visual distinction** in category tree rendering:
  - `codicon-symbol-function` icon (vs `codicon-symbol-method` for stdlib)
  - CSS class `user-function-item` for green accent
- **Apply button** logic update:
  - Call `splitFunctionDefsAndExpressions(content)`
  - Function defs → `onInsertFunctionDefs(defs)`
  - Expressions → `onInsert(expressions)` (existing)
  - If only defs and no expressions, only call `onInsertFunctionDefs`

### 3. `src/browser/editor/utlx-editor-widget.tsx`

- **Add property** `functionBuilderUserFunctions: FunctionInfo[] = []`
- **In `openFunctionBuilder()`** — after loading stdlib, parse user functions:
  ```
  const result = parseUserFunctions(this.getContent());
  this.functionBuilderUserFunctions = result.functions;
  ```
- **Pass new props** to `FunctionBuilderDialog`:
  - `userFunctions={this.functionBuilderUserFunctions}`
  - `onInsertFunctionDefs={(code) => this.handleInsertFunctionDefs(code)}`
- **New method `handleInsertFunctionDefs(code)`:**
  - Find body start line: `this.headerEndLine + 2`
  - Insert `code + '\n\n'` at body start (column 1)
  - Uses `editor.executeEdits()` with `forceMoveMarkers: true`

### 4. `src/browser/style/function-builder.css`

Append styles for user function distinction:
- Green accent (`#50fa7b`) matching existing Dracula theme usage
- Category header: left border + subtle background
- Function items: green name + icon color
- Badge in details panel: "User-Defined" label

## Key Interfaces (no changes, reference only)

`FunctionInfo` from `src/common/protocol.ts:211`:
```typescript
{ name, category, signature, description, parameters: ParameterInfo[], returnType, examples? }
```
`ParameterInfo`: `{ name, type, description?, optional?, defaultValue? }`

## Data Flow

```
Opening Function Builder:
  editor.getContent() → parseUserFunctions() → FunctionInfo[] → dialog prop

Standard Library tab:
  ▼ User Functions (2)     ← green accent, top of tree
      CalculateTax
      FormatPrice
  ▶ Aggregation (5)
  ▶ String (15)
  ...

Apply auto-routing:
  Expression Editor content → splitFunctionDefsAndExpressions()
    ├─ functionDefs[] → onInsertFunctionDefs → inserted at body top (after ---)
    └─ expressions   → onInsert → replaces cursor line(s) (existing behavior)
```

## Implementation Order

1. Create `user-function-parser.ts` (standalone, no dependencies)
2. Update `function-builder-dialog.tsx` (new props, category merge, Apply logic)
3. Update `utlx-editor-widget.tsx` (parse + pass + insert handler)
4. Update `function-builder.css` (visual styling)

## Verification

1. Write a UTLX with 2+ user-defined functions and a main expression body
2. Open Function Builder → verify "User Functions" category appears at top with correct functions
3. Click a user function → verify details panel shows signature, params, return type
4. Click "Insert" → verify function call inserted in Expression Editor with param placeholders
5. Search → verify user functions appear in search results
6. In Expression Editor, write a new function def + expression → click Apply → verify function def goes to body top, expression replaces cursor line
7. UTLX with no user functions → verify "User Functions" category doesn't appear
