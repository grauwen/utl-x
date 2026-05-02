# DB01: UTLXd — `let` bindings outside object body fail

**Status:** Open  
**Priority:** Medium  
**Created:** May 2026  
**Component:** utlxd (IDE daemon)

---

## Problem

When a transformation body has `let` bindings outside (before) the output object, utlxd fails with:

```
Execution failed: HTTP 500: { "success": false, "error": "Execution failed: map() requires array as first argument", "executionTimeMs": 12 }
```

Side-effect statements like `info()` outside the object work fine. The same transformation (with `let` outside) works correctly in the CLI (utlx) and engine (utlxe).

## Reproduction

```utlx
%utlx 1.0
input json
output json
---
info("Starting transformation")
let result = map($input.items, (i) -> i.name)
{
  items: result
}
```

Input: `{"items": [{"name": "A"}, {"name": "B"}]}`

- **utlx CLI:** ✅ works — logs message, outputs `{"items": ["A", "B"]}`
- **utlxd IDE:** ✗ fails — `map() requires array as first argument`
- **`info()` alone (no `let`):** ✅ works in utlxd

## Analysis

The error suggests utlxd does not properly scope `$input` within `let` bindings that appear outside the output object. The IDE's execution path likely evaluates `let` bindings in a context where `$input` is not yet available or is null.

## Workaround

Move `info()` calls inside a `let` or after the `let` bindings:

```utlx
let result = map($input.items, (i) -> i.name)
{
  items: result
}
```

Or use `info()` only within the object body (returns `null` for that field):

```utlx
let result = map($input.items, (i) -> i.name)
{
  _log: info(concat("Processed ", toString(count(result)), " items")),
  items: result
}
```

---

*Bug DB01. May 2026. UTLXd daemon only — CLI and engine unaffected.*
