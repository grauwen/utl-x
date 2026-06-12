# IF21: IDE — MC "Treeview" mapper (BW-style inputs ▸ output, Function Builder per output node)

**Status:** **Phase 1 implemented** (read-only treeview, coverage-colored). Phases 2–3 proposed.
**Design updated (June 2026) — see ["Design update: UTLX-led derivation + 3-column functoid model"](#design-update-june-2026--utlx-led-derivation--3-column-functoid-model) below.** This supersedes the *"editable coverage / treeview is generative"* framing: **UTLX is the lead**, the treeview is a **read-only projection of the UTLX AST**, derived mappings render as **functoid blocks** in a center lane, and coverage is demoted to a **gap/suggestion overlay**.
- **Phase 1 (done):** a third editor view **Treeview** (toggle: Classic | Canvas | Treeview, MC mode only) —
  all inputs as field trees (left) + the output contract tree (right), each output leaf colored by IF11
  coverage (✓ direct / ~ derivable / ✗ gap) with its source; containers show a descendant-gap badge; a
  per-contract summary count. Read-only. Files: `mapping-editor/treeview-widget.tsx`, `style/treeview.css`
  (+ `index.css` import), `mapping-editor/mapping-types.ts` (`ViewMode += 'treeview'`),
  `events/utlx-event-service.ts` (event widened), `editor/utlx-editor-widget.tsx` (toggle button +
  `buildTreeviewData()` reusing `schemaFieldTreeMap` + output preset schema + `buildCoverage`). Typechecks clean.
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

1. **Read-only treeview** — inputs-left / output-contract-right, output nodes **colored by coverage
   status**. Instantly useful as a "what maps / what's a gap" view. **✅ DONE** (uses a lightweight
   read-only tree renderer rather than the FB `FieldTree`, to avoid its insert buttons / sample pane;
   FB reuse comes with Phase 2's interactivity).
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

## Design update (June 2026) — UTLX-led derivation + 3-column functoid model

> Records a design decision that **reverses open question #2** (source of truth) and resolves #3/#4.
> Phase 1 (read-only, coverage-colored) stays as shipped; this is the model for Phase 1.5+.

### The decision: UTLX is the lead; the treeview is a projection of it

The original framing made the treeview **generative** (it owns a mapping model and writes the `%utlx`
body), with coverage as the editable data. We are inverting that: **the `%utlx` is the single source of
truth**, and the treeview is a **read-only projection of what the transform actually does** (might become
editable later — see phasing). Consequences:

- **Lines come from the UTLX AST, not from `buildCoverage`.** Coverage is a name/type *heuristic*
  ("what could map / what's a gap"); it is **not** what the code does. With UTLX present, the authoritative
  mapping is each output field's **binding expression**, which names the real input paths and the
  functions/lets between them.
- **Coverage is demoted to a gap/suggestion overlay** — it colors *unmapped* contract nodes
  ("no derivation yet; likely source = …"), it no longer defines the lines.

### The tree is UDM-shaped (one node type, two providers)

Every node in both panes is a **`UdmField`** (name, type, nested `fields`) — the format-agnostic
abstraction. Only the *provider* differs:

- **shape** (a schema / USDL) → `SchemaFieldInfo` = `UdmField` + schema metadata (`isRequired`,
  `constraints`, `schemaType`);
- **data** (an instance) → plain `UdmField` (via `parseUdmToTree`).

Same node, two sources — which is why the input pane already falls back from schema field tree to
UDM-parsed instance with no conversion (`SchemaFieldInfo extends UdmField`). Keep this invariant: the
mapper renders **UDM-shaped trees**; it never cares which provider filled a branch.

### Data model v2 — a per-output *derivation*, not a coverage string

```
Derivation {
  outputPath: string;                  // target node path; MAY target a container, not only a leaf
  kind: 'direct' | 'function' | 'operator' | 'let' | 'literal'
      | 'conditional' | 'loop' | 'spread' | 'structural' | 'gap';
  cut: 'leaf' | 'sealed';              // the granularity the UTLX maps this branch at (see "cut level"):
                                       //   'leaf'   = mapped down to this scalar
                                       //   'sealed' = whole subtree produced wholesale (spread / subtree
                                       //              copy); ONE cut, no per-leaf lines expected below
  inputs: string[];                    // fully-qualified input paths referenced → the fan-in lines
  letRefs?: string[];                  // names of let-bindings used → route line through the shared block
  label?: string;                      // short display: function/operator name, 'if', 'map', literal value
  code?: string;                       // the UTLX sub-expression (read-only display / hover / later FB seed)
  location?: { line: number; column: number };   // jump-to-code in classic view
  children?: Derivation[];             // loop/structural: nested derivations relative to the element;
                                       //   a 'sealed' cut has none (descendants are inherited, not mapped)
}
```

Render rules (3-column: input trees · derivation lane · output tree):

| kind | visual |
|---|---|
| `direct` | straight line input → output, **no block** (green) |
| `function`/`operator`/`conditional` | input(s) → **functoid block** (label/code) → output; multi-input ⇒ fan-in |
| `let` | a **shared block** in the lane; every output that uses it lines into the same block |
| `literal` | a small constant block (no input line) |
| `loop` | block on the collection (driver from IF20); `children` map relative to the element |
| `spread`/`structural` (`cut: 'sealed'`) | **one line at the container level**; subtree shown as a sealed cut (a "copied wholesale" badge), not drilled per-leaf |
| `gap` | no line; coverage overlay flags it |

### Cut level — the UTLX sets the granularity per branch

A mapping isn't always leaf-to-leaf. `output = { ...$order }` (spread) or `output.addr = $order.shipTo`
(subtree copy) produce a **whole container at once** — the transform never touches the leaves. So each
output branch has a UTLX-derived **cut**: `sealed` (mapped wholesale at a container — one line, subtree
sealed) or `leaf` (mapped down to the scalar). Different branches of the same contract cut at different
levels, and the level is *derived from the AST*, not chosen arbitrarily.

This gives two independent notions of depth:
1. **Semantic cut** — the level the UTLX maps a branch at (from `deriveMapping`).
2. **User folding** — manual expand/collapse on top (the deepest-visible-ancestor rule below).

**The treeview opens at the semantic cut**, not uniformly collapsed: spreads stay folded as one sealed
cut, explicitly-mapped fields expand to their leaves. The *initial shape of the tree itself* then shows
how the transform is structured — where it copies wholesale, computes, or loops — which is the whole
human payoff. User folding adjusts from that starting point.

### Derivation source = a utlxd design-time query (not a client-side parser)

Because **the engine owns the real UTLX parser**, the treeview must **not** reimplement a body parser in
the browser (it would drift). Instead, add a **design-time derivation query** to `utlxd`, beside the
existing `design generate-schema / typecheck / infer`. Then: **build the derivation once, render it twice**
— the **canvas** graph and the **treeview** 3-column are two presentations over the same map (this is how
#3 "treeview vs canvas" resolves: not two mappers, two renderers).

**Endpoint spec — `design deriveMapping`** (LSP method / stdio request; CLI `utlxd design derive`):

Request:
```json
{
  "utlx": "%utlx 1.0\ninput order json\noutput json\n---\n{ ... }",
  "inputs": [
    { "name": "order", "format": "json", "schema": "<schema text>" }
  ],
  "outputFormat": "json"
}
```

Response:
```json
{
  "outputs": [
    { "outputPath": "shipmentId", "kind": "direct", "cut": "leaf",
      "inputs": ["order.id"], "code": "$order.id", "location": { "line": 5, "column": 3 } },

    { "outputPath": "customer.fullName", "kind": "function", "cut": "leaf", "label": "concat",
      "inputs": ["order.customer.firstName", "order.customer.lastName"],
      "code": "concat($order.customer.firstName, \" \", $order.customer.lastName)" },

    { "outputPath": "shipTo", "kind": "spread", "cut": "sealed",
      "inputs": ["order.shippingAddress"], "code": "$order.shippingAddress" },

    { "outputPath": "tax", "kind": "let", "cut": "leaf", "label": "tax", "letRefs": ["tax"],
      "inputs": ["order.total"], "code": "$tax" },

    { "outputPath": "lines", "kind": "loop", "cut": "leaf", "label": "map", "driver": "order.items",
      "inputs": ["order.items"], "code": "$order.items |> map(...)",
      "children": [
        { "outputPath": "lines[].sku", "kind": "direct", "cut": "leaf",
          "inputs": ["order.items[].code"], "code": "$it.code" }
      ] },

    { "outputPath": "address.country", "kind": "gap", "cut": "leaf", "inputs": [] }
  ],
  "lets": [
    { "name": "tax", "code": "$order.total * 0.21", "inputs": ["order.total"] }
  ],
  "diagnostics": []
}
```

Notes: `outputPath` must use the **same path convention as the treeview's output `TreeNode.path`**, and
`inputs[]` the same convention as the input node paths (`inputName.path`), so the
**deepest-visible-ancestor** resolver can match endpoints directly. Resolving that convention against
`coverage.ts`'s `flattenLeaves` paths is the first correctness check before any drawing.

### Folding/aggregation (carried over from the line-drawing design)

One rule covers collapsed trees: **resolve each line endpoint (and each block's owner) to its deepest
*visible* ancestor, then dedup by anchor pair.** A folded input/output aggregates its descendants' lines
onto the visible ancestor; a folded output hides its block and the line aggregates upward. Folding is the
**density control** — start collapsed (already implemented), fan out on expand. Blocks belong to their
output field, so they hide/show with it.

### Read-only now → editable later (still UTLX-led)

- **Now (read-only):** blocks display the function/snippet; hover → full `code`; click → jump to
  `location` in classic view. No write-back.
- **Later (editable):** click a block → Function Builder seeded from `code` → on apply it **writes UTLX**,
  which re-derives the view. UTLX stays the single source of truth; the treeview never holds a divergent
  model. This is the clean answer to old open-question #2 (no reverse-parsing of hand-edits into a separate
  model — the derivation query *is* the parse).

### Execution mode (parked — MC only for now)

Because the tree is UDM-shaped with pluggable providers, an Execution-mode treeview is the *same* tree
with a different mix: input = **UDM** (real instance), output = **shape inferred from the UTLX** (USDL-ish)
optionally annotated with **runtime values** (UDM). No new tree type — just which provider fills each side,
and `deriveMapping` over the same AST. Deferred; noting it so the model doesn't paint us into an
MC-only corner. (To pursue it, first pin USDL's exact role vs UDM for the output "shape + value" overlay.)

### Revised phasing

- **1.5 — read-only lines + functoid blocks** from `design deriveMapping`: direct lines, function/let
  blocks, fan-in, gap overlay, with the folding-aggregation rule. (Loops can show as a single block first.)
- **2 — editable round-trip:** block → FB → writes UTLX → re-derive.
- **3 — loops/arrays in depth:** expand `children` relative to the loop element (IF20 driver/strategy).

### Open decisions

1. **Where `deriveMapping` runs** — recommended: **utlxd design-time** (engine owns the parser). Alterative
   (client-side body parser) is rejected as drift-prone.
2. **Canvas convergence** — adopt the v2 derivation map as the canvas's input too, so both views share it.

---

## Related

- **IF11** — coverage (the per-node status + seed) and the FB itself.
- **IF20** — strategy analysis (required for array/loop mapping; the treeview is its visual surface).
- **IB05** — output **data** vs **schema** format (so generated mappings produce data, not a schema).
