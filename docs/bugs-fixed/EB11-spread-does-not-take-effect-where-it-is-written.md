# EB11: a spread does not take effect where it is written

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB10. This is **EB11**.

**Status:** **FIXED.**
**Priority:** High. Wrong element order *and* wrong values, silently, on both compiled strategies.
**Component:** `:engine` — `ASTCompiler.compileObjectLiteral` / `compileArrayLiteral`,
`RuntimeOps.spreadIntoObject` / `spreadIntoArray`.

> **One-line:** both literals were built before the spreads were applied, so a spread always won
> regardless of where the author put it — and an array spread nested instead of flattening.

## Two defects in one operator

**Object literals.** Every keyed property was compiled first and the spreads merged on top:

```
{ ...$input, z: 9 }     interpreter  a, b, z     compiled  z, a, b     ← wrong ORDER
{ ...$input, a: 99 }    interpreter  a = 99      compiled  a = 1       ← wrong VALUE
```

Order is not cosmetic here — XML output *is* element order. And because the interpreter's rule is
**last wins by position**, getting the order wrong gets the value wrong too: a property written
after a spread could not override it.

**Array literals.** A `SpreadElement` was compiled as an ordinary element, so `[...arr]` produced
`[[1, 2, 3]]` — nested instead of flattened.

## The fix

Both literals now fold their segments **left to right in source order**: runs of consecutive plain
members are grouped into one `buildArray` / `buildObjectWithAttributes` call, and each spread is
merged at its position. Grouping keeps a long literal to one build call per run rather than one per
member, and folding reproduces the interpreter's last-wins rule by construction rather than by
coincidence.

`spreadIntoObject` already had the right merge semantics — `LinkedHashMap(target)` then
`putAll(spread)`, so the accumulator's key order is kept and the newer segment wins. It was being
called in the wrong order, not computing the wrong thing. It *was* dropping `metadata`, the same
omission as [EB09](EB09-metadata-access-lost-on-both-compiled-strategies.md), and now carries it.

Spreading a non-array contributes **nothing**, mirroring the interpreter, which evaluates the
expression and silently ignores anything that is not an array. Mirrored rather than improved — the
point is agreement.

## Effect

| | before | after |
|---|---|---|
| known divergences | 11 | **7** |
| `BOTH_OUTPUT` | 6 | 2 |

Four fixtures: the three `examples/xml-encoding/multi_input_*` and `examples/intermediate/spread_operator`.

The xml-encoding trio diverged on object order (`{ Combined: { ...$input, Metadata1: {...} } }`
put `Metadata1` first when compiled); `spread_operator` additionally exercised array spread, which
is why it kept failing after the object half was fixed.

## Tests

`EB11SpreadOrderTest` (`:engine`) — 14 cases, each asserting `compile(...) != null` and then
comparing against the interpreter.

Object: spread before a property, after a property, between two, a later property overriding a
spread (the wrong-value case), a later spread overriding a property, two spreads, a spread alongside
an `@attribute` (so EB08's split survives the regrouping), and an object with no spread at all.
Array: a lone spread flattened, a spread among plain elements, two spreads, spreading a non-array,
an array with no spread, and the empty literals.

Revert-verified independently: making the object fold unconditional again fails 4, and making
`spreadIntoArray` nest instead of flatten fails 4 — different cases each time.

One note on that check, because the first attempt was useless: reverting the array half by compiling
the `SpreadElement` node directly failed **nothing**, because `compileExpression` passes that node
through to its inner expression. The revert has to disable the *flattening*, not the unwrapping — a
revert that changes nothing observable proves nothing about the fix.

## Related

- [EB08](EB08-compiled-object-literals-drop-xml-attributes.md) — the other object-literal defect;
  its attribute split had to keep working through the regrouping, which is why there is a test for
  exactly that combination.
- [EB09](EB09-metadata-access-lost-on-both-compiled-strategies.md) — `spreadIntoObject` dropping
  metadata is the same omission, fixed here.
- conformance-across-execution-strategies *(design notes — development branch)*
