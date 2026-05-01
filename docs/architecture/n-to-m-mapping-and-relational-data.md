# N-to-M Mapping and Relational Data in UTL-X

**Design analysis: how UTL-X handles flat/relational data and the case for an intermediate card**  
*April 2026*

---

## The Problem: Hierarchical vs Relational Data

### Hierarchical Data (XML, JSON)

Parent-child relationships are *structural* — a child lives inside its parent:

```xml
<Order id="ORD-001">
  <OrderLine product="Widget" qty="2"/>
  <OrderLine product="Gadget" qty="1"/>
</Order>
<Order id="ORD-002">
  <OrderLine product="Gizmo" qty="5"/>
</Order>
```

To get "all lines for order ORD-001", you navigate: `$input.Order[0].OrderLine`. The relationship is implicit in the structure.

### Relational/Flat Data (IDoc, CSV, database rows)

Parent-child relationships are via *reference keys* — everything is flat:

```
Segment E1EDK01 (Order Header)
  BELNR = ORD-001
  CURRENCY = EUR

Segment E1EDP01 (Order Line)
  BELNR = ORD-001    ← foreign key to header
  MATNR = Widget
  MENGE = 2

Segment E1EDP01 (Order Line)
  BELNR = ORD-001    ← same order
  MATNR = Gadget
  MENGE = 1

Segment E1EDK01 (Order Header)
  BELNR = ORD-002
  CURRENCY = USD

Segment E1EDP01 (Order Line)
  BELNR = ORD-002    ← different order
  MATNR = Gizmo
  MENGE = 5
```

To get "all lines for order ORD-001", you must *filter by key*: find all E1EDP01 segments where BELNR == "ORD-001". The relationship is explicit in the data, not in the structure.

### The SAP IDoc XML Variant

SAP often exports IDocs as XML, but the XML preserves the *flat segment structure* rather than nesting children under parents:

```xml
<IDOC>
  <E1EDK01>
    <BELNR>ORD-001</BELNR>
    <CURRENCY>EUR</CURRENCY>
  </E1EDK01>
  <E1EDP01>
    <BELNR>ORD-001</BELNR>
    <MATNR>Widget</MATNR>
    <MENGE>2</MENGE>
  </E1EDP01>
  <E1EDP01>
    <BELNR>ORD-001</BELNR>
    <MATNR>Gadget</MATNR>
    <MENGE>1</MENGE>
  </E1EDP01>
  <E1EDK01>
    <BELNR>ORD-002</BELNR>
    <CURRENCY>USD</CURRENCY>
  </E1EDK01>
  <E1EDP01>
    <BELNR>ORD-002</BELNR>
    <MATNR>Gizmo</MATNR>
    <MENGE>5</MENGE>
  </E1EDP01>
</IDOC>
```

This is XML that looks hierarchical but is semantically *flat*. The `E1EDP01` segments are siblings of `E1EDK01`, not children. The parent-child relationship is in the `BELNR` key, not in the XML nesting.

---

## How UTL-X Handles This Today

### Approach 1: groupBy + map (functional)

```utlx
let headers = filter($input.IDOC.*, (seg) -> seg._name == "E1EDK01")
let lines = filter($input.IDOC.*, (seg) -> seg._name == "E1EDP01")

map(headers, (header) -> {
  orderId: header.BELNR,
  currency: header.CURRENCY,
  lines: map(
    filter(lines, (line) -> line.BELNR == header.BELNR),
    (line) -> {
      product: line.MATNR,
      quantity: toNumber(line.MENGE)
    }
  )
})
```

This works but has **O(N × M) complexity** — for each header, it scans all lines. With 1,000 orders and 10,000 lines, that's 10 million comparisons.

### Approach 2: groupBy for pre-indexing

```utlx
let linesByOrder = groupBy(
  filter($input.IDOC.*, (seg) -> seg._name == "E1EDP01"),
  (line) -> line.BELNR
)

map(
  filter($input.IDOC.*, (seg) -> seg._name == "E1EDK01"),
  (header) -> {
    orderId: header.BELNR,
    currency: header.CURRENCY,
    lines: map(linesByOrder[header.BELNR] ?? [], (line) -> {
      product: line.MATNR,
      quantity: toNumber(line.MENGE)
    })
  }
)
```

Better — `groupBy` creates a lookup map (O(M) to build), then each header does an O(1) lookup. Total: O(N + M) instead of O(N × M).

### What's Missing

Both approaches work but require the developer to:

1. **Know which fields are keys** (BELNR is the join key)
2. **Write the groupBy/filter logic** manually
3. **Handle multiple levels** of nesting (header → line → schedule line → ...)
4. **Think relationally** even though UTL-X is designed for hierarchical data

---

## The Mercator/WTX "Intermediate Card" Concept

IBM's Mercator (later WebSphere Transformation Extender, WTX) solved this elegantly with the **Type Tree** and **intermediate card**:

### How it works:

1. **Input Type Tree**: defines the flat structure (segments, fields, keys)
2. **Output Type Tree**: defines the hierarchical structure (nested orders with lines)
3. **Intermediate Card**: a mapping specification that declares:
   - Which segments are parents, which are children
   - Which fields are the join keys
   - The cardinality (1:N, N:M)
   - The nesting depth

The intermediate card is NOT code — it's a declarative mapping definition. The engine uses it to automatically:
- Group flat records by key
- Build the hierarchical output
- Handle multi-level nesting

### Why this is elegant:

- No O(N × M) — the engine builds an indexed lookup internally
- No manual groupBy/filter — the developer declares relationships, the engine executes them
- Multi-level nesting is trivial — just add more levels to the card
- The card IS the documentation — you can read it and understand the relationship structure

---

## Should UTL-X Have an Intermediate Card?

### Arguments For:

1. **SAP integration is a huge market** — every SAP project needs IDoc-to-hierarchical transformation. If UTL-X makes this easy, it's a competitive advantage over tools that don't.

2. **The current approach is verbose** — `groupBy` + `filter` + `map` for each level of nesting. Three levels deep becomes unreadable.

3. **Performance** — the engine could optimize the join internally (hash-based grouping) rather than relying on the developer to use `groupBy` correctly.

4. **Correctness** — a declarative join is harder to get wrong than imperative filter logic. Off-by-one errors, null key handling, missing parent handling — all automated.

### Arguments Against:

1. **UTL-X's strength is simplicity** — adding a declarative join DSL makes the language more complex. The `groupBy` + `map` approach works and uses existing concepts.

2. **Scope creep** — UTL-X is a transformation language, not a database query engine. SQL already solves relational joins. If the data is relational, maybe it should be joined before it reaches UTL-X.

3. **The pre-processing pattern** — in most integration architectures, flat data (IDoc, CSV) is pre-processed into hierarchical format before transformation. UTL-X receives already-nested data.

4. **The 80/20 rule** — 80% of transformations are hierarchical-to-hierarchical (XML→JSON, JSON→XML). The flat-to-hierarchical case is important but not dominant.

### Recommendation: Not an intermediate card, but a `nestBy()` function

Instead of a full declarative card system, add a `nestBy()` stdlib function:

```utlx
let orders = nestBy(
  $input.IDOC.E1EDK01,          // parent records
  $input.IDOC.E1EDP01,          // child records
  (header) -> header.BELNR,      // parent key
  (line) -> line.BELNR,          // child key
  "lines"                        // name for the NEW property added to each parent
)
```

**How the 5th parameter works:** The string `"lines"` tells `nestBy()` what to **name** the new property it creates on each parent. After `nestBy()`, each E1EDK01 object gains a `.lines` property containing its matched E1EDP01 records:

```
BEFORE nestBy():   {"BELNR": "ORD-001", "CURRENCY": "EUR"}
AFTER  nestBy():   {"BELNR": "ORD-001", "CURRENCY": "EUR", "lines": [{...}, {...}]}
                                                            ^^^^^^
                                                            Created by nestBy().
                                                            Name comes from "lines" parameter.
```

You then use `.lines` in subsequent expressions like any other property:

```utlx
// After nestBy(), each order has a .lines property you can access:
map(orders, (order) -> {
  orderId: order.BELNR,
  lineCount: count(order.lines),                              // ← .lines exists now
  total: sum(map(order.lines, (l) -> toNumber(l.MENGE) * toNumber(l.PRICE)))
})
```

This gives you:
- Declarative nesting (parent key, child key, result property name)
- O(N + M) performance (hash-based internally)
- Composable — call `nestBy()` multiple times for multi-level nesting
- No new language concept — it's just a function
- Works with any flat data (IDoc, CSV, database exports)

For multi-level nesting:

```utlx
let withLines = nestBy($input.headers, $input.lines,
  (h) -> h.orderId, (l) -> l.orderId, "lines")

// Now each header has .lines — nest schedules INTO each line:
map(withLines, (header) -> {
  ...header,
  lines: nestBy(header.lines, $input.schedules,
    (l) -> l.lineId, (s) -> s.lineId, "schedules")
})
// Result: headers[].lines[].schedules[] — 3-level hierarchy from flat data
```

---

## Relation to UDM

UDM is inherently **hierarchical** — Objects contain Objects which contain Objects. This mirrors XML and JSON naturally. But it can represent flat/relational data too — an Array of Objects with key fields.

The challenge is that UDM doesn't know about *relationships between siblings*. When a flat IDoc is parsed into UDM, the header and line segments are siblings in the same Array. The relationship (BELNR = BELNR) is in the data values, not in the UDM structure.

A `nestBy()` function restructures the UDM tree: it takes sibling arrays and creates a parent-child hierarchy based on key matching. The output is a new UDM tree where the relationships are structural (nested) instead of referential (key-based).

This is essentially what Mercator's intermediate card does — but expressed as a function call rather than a separate mapping artifact.

---

## The Complete Restructuring Toolkit

`nestBy()` solves 1:N parent-child nesting, but real integrations need more. The full set of proposed restructuring functions:

| Function | Pattern | What it does | Feature |
|----------|---------|-------------|---------|
| `nestBy()` | Flat → Hierarchical | Nest children under parents by key (1:N) | F03 |
| `lookupBy()` | Flat → Enriched | Find ONE matching record by key (1:1 enrichment) | F04 |
| `chunkBy()` | Flat → Grouped | Group sequential records by position (no keys) | F05 |
| `unnest()` | Hierarchical → Flat | Expand nested children alongside parents (reverse of nestBy) | F06 |

### Naming convention

Functions that take a **lambda** for key extraction use the `By` suffix. Functions that take a plain value do not:

| Function | Takes lambda? | By suffix? |
|----------|--------------|------------|
| `nestBy()` | Yes | Yes |
| `lookupBy()` | Yes | Yes |
| `chunkBy()` | Yes | Yes |
| `unnest()` | No (string property name) | No |

### How they combine

A typical SAP IDoc integration uses three of these together:

```utlx
// 1. Group sequential segments by position (chunkBy — F05)
let orderGroups = chunkBy($input.segments, (seg) -> seg.type == "E1EDK01")

// 2. Nest lines under headers by key (nestBy — F03)
let orders = nestBy($input.headers, $input.lines,
  (h) -> h.BELNR, (l) -> l.BELNR, "lines")

// 3. Enrich with customer data by key (lookupBy — F04)
map(orders, (order) -> {
  let customer = lookupBy(order.KUNNR, $input.customers, (c) -> c.id)
  orderId: order.BELNR,
  customerName: customer?.name ?? "Unknown",
  lines: order.lines
})

// 4. Flatten for CSV export (unnest — F06)
// unnest(enrichedOrders, "lines")
```

---

## Status

- **Current:** `groupBy` + `map` works but is verbose and O(N × M) without groupBy
- **Proposed:** `nestBy()` (F03), `lookupBy()` (F04), `chunkBy()` (F05), `unnest()` (F06)
- **Not proposed:** full intermediate card system (too complex for the return)
- **Book coverage:** Chapter 21 (Data Restructuring), Chapter 10 (UDM), Chapter 31 (Enterprise Integration)

---

*Design analysis for N-to-M mapping in UTL-X. April 2026.*
