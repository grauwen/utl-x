# IF21: IDE — MC "Treeview" mapper (BW-style inputs ▸ output, Function Builder per output node)

**Status:** Proposed (design). Not implemented.
**Priority:** High — a familiar, scalable visual mapper for Message Contract mode; it's the human-facing
surface for the coverage (IF11) + strategy (IF20) work, and reuses components that already exist.
**Created:** June 2026
**Component:** IDE editor — a third editor **view** alongside classic (Monaco) and canvas. Front-end only
(reuses the mcp-server FB/coverage). No daemon/CLI/engine change.
**Depends on:** IF11 (deterministic coverage + per-output sourcing), IF20 (strategy analysis — needed for
array/loop mapping), the Function Builder (`function-builder/field-tree.tsx`, `function-builder-dialog.tsx`),
the schema field-tree parsers (`utils/schema-field-tree-parser.ts`), the mapping code generator
(`mapping-editor/code-generator.ts`).

> **One-liner:** A Tibco-BW-style mapper for MC mode — **all inputs as trees on the left, the output
> contract as an enfoldable tree on the right** — where clicking an output node opens the existing
> **Function Builder** seeded for that field. The output nodes are colored by **coverage** (✓ direct /
> ~ derivable / ✗ gap) and the tree generates the `%utlx` body. It's the visual front-end for IF11+IF20.

---

## Motivation

The editor has two views: **classic** (Monaco) and **canvas** (node/edge graph). For MC mode (a *fixed
output contract*) neither is ideal:
- Classic is code-only — no view of how inputs cover the contract.
- The canvas (lines between nodes) becomes spaghetti on large schemas.

The **BW mapper paradigm** — inputs tree ▸ output tree, click an output node → function dialog — is the
most familiar model for integration developers and **scales**: each output node shows its *bound
expression* instead of a drawn line, so a 200-field contract stays readable.

## Layout

```
┌─ Available inputs ─────────┐   ┌─ Output contract ──────────────┐
│ ▾ $order (json)            │   │ ▾ ShippingManifest             │
│    ▾ customer              │   │    shipmentId   ✓ $order.id     │
│       name                 │   │    customer     ✓ $order.cust…  │
│       …                    │   │    ▾ address                   │
│ ▾ $roster (csv)            │   │       postalCode ~ derive       │
│    employeeId  …           │   │       country    ✗ gap (TODO)   │
└────────────────────────────┘   └────────────────────────────────┘
        (click an output node → Function Builder, seeded for that field)
```

## It's ~80% reuse

- **Left (all inputs as stacked trees):** `function-builder/field-tree.tsx` (`FieldTree`/`InputNode`/
  `FieldNode`) already renders inputs exactly like this (enfoldable, typed, sample-data pane). Show all
  inputs expanded.
- **Right (output contract tree):** the **same `FieldTree`** component, fed the **output** field tree via
  `utils/schema-field-tree-parser` (`parseJsonSchemaToFieldTree` / `parseXsdToFieldTree` / `parseOSchToFieldTree`
  / `parseTschToFieldTree`) — the parsers already used for coverage. One component, two data sources.
- **Function dialog:** `function-builder/function-builder-dialog.tsx`, invoked **from an output node**
  rather than the toolbar — it already has Available Inputs + Operators + Standard Library + insert wiring.
- **View toggle:** add `editorViewTree` next to the existing `editorViewClassic` / `editorViewCanvas`
  (same toggle pattern + testids).
- **Code generation:** `mapping-editor/code-generator.ts` + `mapping-store.ts` already turn a mapping model
  into `%utlx`; the treeview feeds the same kind of model.

So the FB is **not** a third canvas — the three *views* are classic | canvas | **treeview**; the FB is a
shared **modal** invoked from a node (in treeview and canvas alike).

## Data model = editable coverage

Each output node binds to an expression: `outputPath → UTL-X expression`. That **is** an editable
**coverage entry** (IF11): `direct → $src`, `derivable → expression`, `gap → "" + // TODO`. Therefore:
- The deterministic **coverage** pre-fills most nodes the moment the contract loads → the treeview opens
  **already mostly mapped**, each output node showing a **status icon** (✓ / ~ / ✗) and its source. This is
  the **mapping matrix, visualized**.
- Clicking a node opens the FB **seeded for that field** (its type, its coverage suggestion, the inputs);
  the FB returns an expression that **binds to the node** and updates its status.
- The whole tree → `%utlx` body via the deterministic generator (IF20); the FB only earns its keep on
  gaps / complex fields.

So **Treeview is the human-facing surface for IF11 + IF20**: coverage colors the output tree, the
deterministic generator seeds it, the FB refines per node.

## Hard parts / open questions

1. **Arrays / iteration (the real challenge).** An output *array* needs a loop context — "this output
   collection is produced by `map`-ing input array X." BW expresses this with loop links. This is exactly
   IF20's **strategy analysis** (driver / array-transform / join): per output collection, show *which input
   drives the iteration* and bind child fields relative to the loop element. Flat/object mapping works
   without it; arrays **depend on IF20**.
2. **Round-trip / source of truth.** Treeview→code is clean (generate). Code→treeview (reverse-parsing a
   hand-edited `%utlx` into per-node bindings) is hard. Proposed: the treeview is **generative** (owns the
   model, writes the body); hand-edits happen in classic view; switching back **re-seeds from coverage**
   rather than parsing arbitrary code. Decide source-of-truth per view to avoid fighting Monaco.
3. **Treeview vs canvas.** The canvas is more general but messier; the treeview is contract-shaped and
   cleaner. Likely the **treeview becomes the primary MC mapper** and the canvas stays for free-form /
   Execution — rather than maintaining three overlapping editors. Decide, don't accrete.
4. **Two-pane links?** v1 deliberately shows bound expressions as text per output node (scalable), NOT
   drawn lines. Optional hover-highlight of the source input node is a cheap middle ground.

## Phasing

1. **Read-only treeview** — inputs-left / output-contract-right (both `FieldTree`), output nodes **colored
   by coverage status**. Pure reuse + coverage; instantly useful as a "what maps / what's a gap" view. Low risk.
2. **Click-to-map** — clicking an output node opens the FB seeded for that field; bind the returned
   expression; regenerate `%utlx`. (Flat / object fields first.)
3. **Arrays / loops** — drive iteration from IF20 strategy (driver / array-transform), per-collection loop
   context; bind child fields relative to the loop element.

Phase 1 alone is a compelling demo and validates the layout with almost no new logic.

## Code pointers (reuse targets)

- `theia-extension/.../browser/function-builder/field-tree.tsx` — `FieldTree`/`InputNode`/`FieldNode`
  (both panes).
- `theia-extension/.../browser/function-builder/function-builder-dialog.tsx` — the per-node modal.
- `theia-extension/.../browser/utils/schema-field-tree-parser.ts` — output contract → field tree.
- `theia-extension/.../browser/utils/coverage.ts` — per-output status + source (node colors + seed).
- `theia-extension/.../browser/mapping-editor/code-generator.ts` + `mapping-store.ts` — model → `%utlx`.
- `theia-extension/.../browser/editor/utlx-editor-widget.tsx` — view toggle (add `editorViewTree`).

## Related

- **IF11** — coverage (the per-node status + seed) and the FB itself.
- **IF20** — strategy analysis (required for array/loop mapping; the treeview is its visual surface).
- **IB05** — output **data** vs **schema** format (so generated mappings produce data, not a schema).
