# F05: chunkBy() Function — Positional/Sequential Grouping

**Status:** Proposed  
**Priority:** Medium  
**Created:** April 2026  
**Related:** F03 (join), F04 (lookup), B15 (groupBy index bug)

---

## The Problem

Some flat data has parent-child relationships determined by **position**, not by key fields. The rule is: "everything after a header until the next header belongs to that header."

### SAP IDoc Example

```
Segment 1:  E1EDK01  (Order Header)     ← header 1 starts
Segment 2:  E1EDP01  (Order Line)       ← belongs to header 1
Segment 3:  E1EDP01  (Order Line)       ← belongs to header 1
Segment 4:  E1EDP19  (Delivery Info)    ← belongs to header 1
Segment 5:  E1EDK01  (Order Header)     ← header 2 starts
Segment 6:  E1EDP01  (Order Line)       ← belongs to header 2
Segment 7:  E1EDK01  (Order Header)     ← header 3 starts (no lines!)
```

There is NO key field connecting lines to headers. The relationship is purely sequential: lines belong to the header that precedes them. A new header starts a new group.

This pattern appears in:
- SAP IDocs (segment groups)
- EDI/EDIFACT (hierarchical segment groups: UNH...UNT)
- Fixed-width file processing (header record followed by detail records)
- Log files (timestamp line followed by stack trace lines)
- Bank statements (transaction header + detail lines)

### Why nestBy() Can't Do This

`nestBy()` requires a key field on both parent and child to match on. Positional data has no key — the relationship is "I come after the header, so I belong to it." There's nothing to match on except position in the sequence.

### Why reduce() Can But Shouldn't

You can solve this with `reduce()` and manual state tracking:

```utlx
reduce($input.segments, [], (groups, seg) ->
  if (seg.type == "E1EDK01")
    [...groups, {header: seg, children: []}]
  else
    let lastGroup = last(groups)
    [...drop(groups, -1), {...lastGroup, children: [...lastGroup.children, seg]}]
)
```

This works but is:
- Extremely verbose and hard to read
- Error-prone (mutating the last group in an immutable context)
- Rebuilds the entire array on every step (O(N²) allocations)
- Nobody would discover this pattern on their own

## Proposed Function

### Signature

```
chunkBy(array, isNewChunkPredicate) → Array of Arrays
```

### Parameters

| Parameter | Type | What it does |
|-----------|------|-------------|
| `array` | Array | The flat sequence of records |
| `isNewChunkPredicate` | Lambda `(element) → boolean` | Returns true when a new chunk should start |

### Return Value

An Array of Arrays. Each inner array is a "chunk" — a group of consecutive elements. A new chunk starts whenever the predicate returns true.

### How It Works — Step by Step

```utlx
chunkBy($input.segments, (seg) -> seg.type == "E1EDK01")
```

Walk through the segments:
1. Segment 1: `E1EDK01` → predicate returns `true` → **start new chunk** → `[[E1EDK01]]`
2. Segment 2: `E1EDP01` → predicate returns `false` → **add to current chunk** → `[[E1EDK01, E1EDP01]]`
3. Segment 3: `E1EDP01` → `false` → add → `[[E1EDK01, E1EDP01, E1EDP01]]`
4. Segment 4: `E1EDP19` → `false` → add → `[[E1EDK01, E1EDP01, E1EDP01, E1EDP19]]`
5. Segment 5: `E1EDK01` → `true` → **start new chunk** → `[[E1EDK01, E1EDP01, E1EDP01, E1EDP19], [E1EDK01]]`
6. Segment 6: `E1EDP01` → `false` → add → `[..., [E1EDK01, E1EDP01]]`
7. Segment 7: `E1EDK01` → `true` → **start new chunk** → `[..., [E1EDK01, E1EDP01], [E1EDK01]]`

Result: three chunks (three orders), each starting with its header.

### Using the Result

After `chunkBy()`, each chunk is an array where the first element is the header and the rest are children:

```utlx
let orderGroups = chunkBy($input.segments, (seg) -> seg.type == "E1EDK01")

map(orderGroups, (chunk) -> {
  // First element is the header
  orderId: chunk[0].BELNR,
  currency: chunk[0].CURRENCY,

  // Remaining elements are the children (filter by type if mixed)
  lines: filter(drop(chunk, 1), (seg) -> seg.type == "E1EDP01"),
  deliveryInfo: find(drop(chunk, 1), (seg) -> seg.type == "E1EDP19")
})
```

### EDI/EDIFACT Example

EDIFACT messages have segment groups delimited by specific segment types:

```
UNH          ← message header (start new chunk)
BGM          ← beginning of message
DTM          ← date/time
NAD          ← name and address
LIN          ← line item (could also be a sub-chunk boundary)
QTY          ← quantity
PRI          ← price
UNT          ← message trailer
UNH          ← next message starts
...
```

```utlx
let messages = chunkBy($input.segments, (seg) -> seg.type == "UNH")

map(messages, (msg) -> {
  header: find(msg, (s) -> s.type == "BGM"),
  dates: filter(msg, (s) -> s.type == "DTM"),
  parties: filter(msg, (s) -> s.type == "NAD"),
  lines: filter(msg, (s) -> s.type == "LIN")
})
```

## Performance and Memory

```
Input: N segments
Processing: single pass through the array — O(N)
Memory: creates new chunk arrays (references to original elements, no deep copy)
         ~N references + chunk array overhead

For 20,000 segments: O(20,000) — sub-millisecond
```

`chunkBy()` is inherently O(N) — it's a single sequential scan. No indexing, no hashing, no lookups. The simplest of all the proposed functions.

## Edge Cases

| Case | Behavior |
|------|----------|
| First element is NOT a chunk start | First chunk starts with non-matching elements (pre-header data) |
| Empty array | Returns empty array |
| All elements match predicate | Each element becomes its own chunk (array of single-element arrays) |
| No elements match predicate | Entire array is one chunk |
| Last chunk has no children | Chunk contains only the header (valid — header-only group) |

## Implementation

```kotlin
"chunkBy" to { args: List<RuntimeValue> ->
    val array = args[0].asArray()
    val predicate = args[1].asFunction()
    
    val chunks = mutableListOf<MutableList<RuntimeValue>>()
    var currentChunk: MutableList<RuntimeValue>? = null
    
    for (element in array) {
        val isNewChunk = predicate.invoke(listOf(element)).asBoolean()
        if (isNewChunk || currentChunk == null) {
            currentChunk = mutableListOf()
            chunks.add(currentChunk)
        }
        currentChunk.add(element)
    }
    
    RuntimeValue.ArrayValue(chunks.map { RuntimeValue.ArrayValue(it) })
}
```

~15 lines. The simplest implementation of any proposed function.

## Effort Estimate

| Task | Effort |
|------|--------|
| Implement in stdlib | 0.5 day |
| Unit tests | 0.5 day |
| Conformance tests | 0.5 day |
| Documentation | 0.5 day |
| **Total** | **2 days** |

---

*Feature document F05. April 2026.*
