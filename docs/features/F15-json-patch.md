# F15: JSON Patch (RFC 6902)

**Status:** Future Enhancement
**Priority:** Low (expressible today via UTL-X object manipulation)
**Created:** May 2026

---

## Summary

Add a dedicated `jsonPatch(document, patch)` function that applies RFC 6902 JSON Patch operations to a UDM document. While UTL-X can already express all patch operations through its native object manipulation, a dedicated function provides a standards-compliant interface for systems that exchange JSON Patch documents.

## Background

[RFC 6902 — JSON Patch](https://datatracker.ietf.org/doc/html/rfc6902) defines a JSON format for describing changes to a JSON document. It is used with HTTP PATCH requests (via `Content-Type: application/json-patch+json`) and is widely adopted in REST APIs, OpenAPI specs, and configuration management.

### Related Standards

| RFC | Name | Purpose |
|-----|------|---------|
| RFC 6902 | JSON Patch | Apply operations to a document |
| RFC 6901 | JSON Pointer | Path syntax used by JSON Patch (`/foo/bar/0`) |
| RFC 7396 | JSON Merge Patch | Simpler alternative (partial document replacement) |
| RFC 7386 | JSON Merge Patch (original) | Superseded by RFC 7396 |

### JSON Patch Operations

A patch is an array of operation objects:

```json
[
  { "op": "add",     "path": "/address/city", "value": "Amsterdam" },
  { "op": "remove",  "path": "/phone" },
  { "op": "replace", "path": "/name", "value": "Jan" },
  { "op": "move",    "from": "/old/path", "path": "/new/path" },
  { "op": "copy",    "from": "/source", "path": "/target" },
  { "op": "test",    "path": "/version", "value": 2 }
]
```

| Operation | Description |
|-----------|-------------|
| `add` | Add a value at the target path (or insert into array) |
| `remove` | Remove the value at the target path |
| `replace` | Replace the value at the target path |
| `move` | Remove from `from`, add at `path` |
| `copy` | Copy from `from` to `path` |
| `test` | Assert that the value at `path` equals `value` (fails patch if not) |

## Why a Dedicated Function?

### Already Possible in UTL-X

All six operations can be expressed with UTL-X's native object manipulation:

```
// "add" — add a field
{ ...$input, address: { ...$input.address, city: "Amsterdam" } }

// "remove" — exclude a field
removeKey($input, "phone")

// "replace" — overwrite a field
{ ...$input, name: "Jan" }

// "move" — combine remove + add
let temp = $input.old.path
removeKey($input, "old.path") | { ...$, new: { path: temp } }

// "copy" — read + add
{ ...$input, target: $input.source }

// "test" — conditional check
if $input.version != 2 then error("patch test failed")
```

### Why Add `jsonPatch()` Anyway?

| Reason | Detail |
|--------|--------|
| **Interoperability** | REST APIs send/receive JSON Patch documents. UTL-X needs to consume them as-is, not manually translate each operation. |
| **Atomic semantics** | RFC 6902 requires all-or-nothing: if any operation (including `test`) fails, the entire patch is rolled back. Native UTL-X operations don't have this transactional guarantee. |
| **`test` operation** | The `test` op is a precondition check — it aborts the patch if the document doesn't match expectations. This is a safety mechanism not naturally expressed in mapping. |
| **Patch forwarding** | Integration scenarios where UTL-X receives a patch, transforms it, and forwards it. A dedicated function preserves the patch as a first-class object. |
| **Diff generation** | `jsonDiff(before, after)` can produce a JSON Patch document — useful for change detection and audit trails. |

## Proposed Functions

### Core

```
jsonPatch(document, patchOps)                -> patched document
```

Applies an RFC 6902 patch array to a UDM document. Fails atomically if any operation fails (including `test`).

### Diff (Reverse)

```
jsonDiff(before, after)                      -> patch array (RFC 6902)
```

Generates the minimal JSON Patch that transforms `before` into `after`. Useful for audit trails and change detection.

### Merge Patch (RFC 7396)

```
jsonMergePatch(document, mergePatch)         -> patched document
```

Simpler alternative: the merge patch is a partial document that is recursively merged. `null` values remove keys.

### Validation

```
validateJsonPatch(patchOps)                  -> { valid: boolean, errors?: [] }
```

Validates that a patch array conforms to RFC 6902 structure without applying it.

## UTL-X Integration Examples

### Apply a patch received from a REST API

```
%input json   // the original document
%input json as $patch   // the JSON Patch array
%output json
---
jsonPatch($input, $patch)
```

### Generate a diff between two versions

```
%input json as $before
%input json as $after
%output json
---
jsonDiff($before, $after)
// Output: [{"op":"replace","path":"/status","value":"shipped"}, ...]
```

### Conditional patch with test

```
%input json
%output json
---
jsonPatch($input, [
  { op: "test",    path: "/status",  value: "draft" },
  { op: "replace", path: "/status",  value: "approved" },
  { op: "add",     path: "/approvedAt", value: now() }
])
// Fails if status is not "draft" — atomic rollback
```

### Transform and forward a patch

```
%input json as $patch   // incoming RFC 6902 patch
%output json
---
// Rewrite paths from source schema to target schema
$patch[*].{
  op: .op,
  path: replace(.path, "/sourceField", "/targetField"),
  from: if .from then replace(.from, "/sourceField", "/targetField"),
  value: .value
}
```

## Implementation

### Module Location

**`stdlib`** — no external dependencies needed. JSON Patch is pure object manipulation over UDM. Lightweight enough for core.

### JSON Pointer (RFC 6901)

JSON Patch depends on JSON Pointer for path resolution. Implementation needs:

```kotlin
// Resolve "/foo/bar/0" to a value in UDM
fun resolvePointer(document: UDM, pointer: String): UDM?

// Set a value at a JSON Pointer path
fun setAtPointer(document: UDM, pointer: String, value: UDM): UDM

// Remove a value at a JSON Pointer path
fun removeAtPointer(document: UDM, pointer: String): UDM
```

JSON Pointer rules:
- `/` separates path segments
- `~0` escapes `~`, `~1` escapes `/`
- Array indices are numeric strings (`/items/0`)
- `-` refers to the end of an array (for `add`)

### Architecture

```
jsonPatch(document, patchOps)
     |
     v
  Parse patch array -> List<PatchOperation>
     |
     v
  For each operation:
     |-- resolve JSON Pointer (RFC 6901)
     |-- apply operation (add/remove/replace/move/copy/test)
     |-- on failure: rollback all previous operations
     |
     v
  Return patched UDM document
```

### GraalVM Native Image

No risk. Pure UDM manipulation, no reflection, no external libraries.

## Relation to Existing UTL-X Capabilities

| Capability | Status | Note |
|-----------|--------|------|
| Object spread (`...`) | Exists | Covers `add`, `replace` |
| `removeKey()` | Exists | Covers `remove` |
| Path access (`$input.a.b`) | Exists | Covers `copy`, `move` (manually) |
| Conditional (`if/then`) | Exists | Covers `test` (manually) |
| Atomic rollback | **Missing** | Only `jsonPatch()` provides all-or-nothing semantics |
| JSON Pointer resolution | **Missing** | Needed for RFC 6901 path syntax |
| Diff generation | **Missing** | `jsonDiff()` is new capability |

## Effort Estimate

| Task | Effort |
|------|--------|
| JSON Pointer (RFC 6901) resolver | 1 day |
| `jsonPatch` — 6 operations + atomic rollback | 2 days |
| `jsonDiff` — diff generation | 1-2 days |
| `jsonMergePatch` (RFC 7396) | 0.5 day |
| `validateJsonPatch` | 0.5 day |
| Tests (including RFC 6902 test suite) | 1 day |
| Documentation | 0.5 day |
| **Total** | **6-8 days** |

## See Also

- [RFC 6902 — JSON Patch](https://datatracker.ietf.org/doc/html/rfc6902)
- [RFC 6901 — JSON Pointer](https://datatracker.ietf.org/doc/html/rfc6901)
- [RFC 7396 — JSON Merge Patch](https://datatracker.ietf.org/doc/html/rfc7396)
- [stdlib function reference](../reference/stdlib-reference.md)

---

*Feature F15. May 2026.*
