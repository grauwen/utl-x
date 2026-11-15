# B62 — a duplicate key at the same level silently discards a mapping

**Status:** Fixed — 2026-11-15
**Priority:** High — silent data loss: a mapping the author wrote, and can see in the IDE, is
absent from the output with no diagnostic anywhere
**Component:** `:core` — `types/type_system.kt`, the `Expression.ObjectLiteral` branch
**Found:** 2026-11-15, while weighing a shorthand-property proposal for the drag & drop mapper

> The number B62 was used briefly in this session for a bracket-predicate report that was
> withdrawn before it was ever committed — predicates are declined by design, not a defect, so a
> `Bxx` was the wrong classification. B62 means only what is below.

## Summary

A key written twice in the same object literal keeps the **last** one and discards the rest,
with no error, no warning and no lint issue:

```utlx
%utlx 1.0
input json
output json
---
{ id: $input.a.id, id: $input.b.id }
```

```
input   {"a":{"id":"FROM-A"},"b":{"id":"FROM-B"}}
output  {"id":"FROM-B"}

utlx validate  =>  ✓ Validation passed
utlx lint      =>  ✓ No lint issues found
```

Two deliberate mappings, one survives. Nothing in the toolchain says so.

## Why this matters more than it looks

The reporter hit it designing a **drag & drop mapper**. There, this is the worst possible failure
shape: the user drags two fields, sees two mappings in the UI, and one is missing from the output —
with no diagnostic to explain why, and nothing to click on.

It also blocks a feature. Shorthand properties (`{ $input.a.id, $input.b.id }`, where the key comes
from the last path segment) were under consideration for exactly that mapper. Shorthand makes this
hole *easier* to fall into, because the duplicate key is implicit — you cannot see `id` written
twice. Closing this first is the prerequisite for considering that notation at all.

## The fix

Detected in the type checker rather than the parser, for three reasons:

1. **Non-breaking.** A parse error would reject scripts that run today. A type warning does not,
   and `--strict` promotes it for anyone who wants it fatal.
2. **It reaches the mapper.** The daemon already converts type errors into LSP diagnostics, so the
   warning arrives with a line and column at *drop time* — the one moment the user can fix it.
3. **Blast radius is zero.** A scan of the conformance corpus and `examples/` found two candidates,
   both false positives: an `if`/`else` pair and two sibling array elements.

```
utlx validate          ⚠ 5:33 - Duplicate property 'id' in the same object — the earlier
                                one is discarded. Give one of them a different name.
                       exit 0
utlx validate --strict exit 1
POST /api/validate     {"severity":"warning","line":5,"column":33,"source":"type-checker", ...}
```

## Same level only — what is *not* a duplicate

The check tracks only **explicit, non-spread, non-computed** keys, each paired with its attribute
flag. Three legitimate shapes therefore stay silent, and each has a test:

| shape | why it is not a duplicate |
|---|---|
| `{ a: { id: 1 }, b: { id: 2 } }` | different objects — the ordinary shape of a mapping |
| `{ ...base, id: "NEW" }` | a spread then a deliberate override; this is the documented way to rename one field of a copied object |
| `{ @id: "a", id: "b" }` | attributes and properties are separate namespaces (XML attribute vs element content) |
| `{ [k]: 1, [k]: 2 }` | computed keys cannot be resolved statically; silence is the honest answer |
| `{ rows: [ {id: 1}, {id: 2} ] }` | each array element is its own object |

Nesting needs no special handling: each object literal runs the check with its own key set.

## Tests

`B62DuplicateObjectKeyTest` (10 tests, `:modules:core`). Half of them assert the **silences**
above, which is the half that matters — a duplicate detector that is too eager would break the
spread-override idiom that the guide recommends.

**Revert-verified:** removing the check fails four — `a duplicate key at the same level is
reported`, `a duplicate attribute is reported too`, `the warning points at the second occurrence`,
and `three occurrences report twice`. The six "must stay silent" tests pass either way, which is
correct: they assert behaviour this fix must not change.

Full regression: **4925 JVM tests, 0 failures**; **581/581 conformance on the JVM and the GraalVM
native binary**; validation-tests 4/4 and lint-tests 3/3, which exercise `validate`'s own output.

## Still open

- **`lint` does not report it.** The warning comes from the type checker, so `utlx validate` and
  the LSP show it but `utlx lint` still says "No lint issues found". Lint has its own analyzers
  (see `LintCommand`), and wiring this in there is a separate change.
- **Transform still silently last-wins.** `utlx transform` does not run the type checker, so a
  script with a duplicate key still produces output with one mapping missing. Making `transform`
  surface type warnings is a larger decision about how noisy the happy path should be.
