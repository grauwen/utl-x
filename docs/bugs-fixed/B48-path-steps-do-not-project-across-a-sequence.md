# B48 — A path step that yields a sequence cannot be followed by another step

**Status:** Fixed — 2026-11-11
**Component:** `:core` — selector evaluation
**Found:** 2026-11-11, while replacing `jq` with UTL-X in `scripts/check-action-versions.sh`
**Severity:** High — this is the primary navigation idiom for XML, and it is documented

## Summary

When a path step produces more than one node, the next step fails instead of
projecting across the members. The engine raises

```
Error: Cannot access property on non-object UDM type
Error: Cannot access property 'name' on ArrayValue
```

This is not an edge case. It breaks the most ordinary XML navigation there is:
reaching a child of a repeated element.

## Reproduction

```bash
printf '<r><i><n>a</n></i><i><n>b</n></i></r>' > t.xml
cat > t.utlx <<'X'
%utlx 1.0
input xml
output json
---
input.r.i.n
X
utlx transform t.utlx t.xml
```

Expected (XPath `r/i/n`, DataWeave `payload.r.*i.n`): `["a","b"]`
Actual: `Error: Cannot access property on non-object UDM type`

## What works and what does not

| expression | input shape | result |
|---|---|---|
| `input.*` | object of objects | works — yields an `ArrayValue` |
| `input.*[0]` | object of objects | works |
| `count(input.*)` | object of objects | works — `2` |
| `map(input.*, (t) -> t.name)` | object of objects | works — `["a","b"]` |
| `input.*.name` | object of objects | **fails** — `Cannot access property 'name' on ArrayValue` |
| `input.*` | **array** root | **fails** — `Cannot access property on non-object UDM type` |
| `input.items.*.name` | object holding an array | **fails** |
| `input.r.i.n` | XML, repeated `<i>` | **fails** |
| `input[0].name` | array root | works |
| `input.Order.@*` | XML attributes | works |

So `.*` itself is implemented, and the resulting `ArrayValue` is a first-class
value — indexable, countable, passable to `map()`. The defect is narrow and
specific: **property access does not distribute over it.**

## Why this is a bug rather than a missing feature

`docs/language-guide/selectors.md:171` documents the working behaviour:

```
input.items.*.price         // Same as above (shorthand)
```

with the stated result `[10.00, 25.00, 15.00]`. The documented behaviour has
never worked.

## Root cause

`Interpreter.evaluateMemberAccess` (`modules/core/.../interpreter/interpreter.kt:598`)
switches on the target's runtime type:

```kotlin
return when (target) {
    is RuntimeValue.ObjectValue -> { ... if (expr.property == "*") ... }
    is RuntimeValue.UDMValue    -> { ... if (expr.property == "*") ... }
    else -> throw RuntimeError(...)          // <-- ArrayValue lands here
}
```

There is **no `is RuntimeValue.ArrayValue` branch**. The wildcard itself is
deliberate and implemented — `parser_impl.kt:875` carries the comment
`// Allow wildcard selector: .* or .@* or .^*`, and both branches above evaluate
it — but the `ArrayValue` it produces is a dead end for any further step.

This is not a design decision against wildcards. It is one missing branch.

## Suggested fix

Add an `is RuntimeValue.ArrayValue` branch that maps the member access over the
elements and flattens one level — XPath node-set semantics:

```kotlin
is RuntimeValue.ArrayValue ->
    RuntimeValue.ArrayValue(
        target.elements.flatMap { el ->
            when (val r = evaluateMemberAccess(expr.withTarget(el), env)) {
                is RuntimeValue.ArrayValue -> r.elements
                RuntimeValue.NullValue     -> emptyList()
                else                       -> listOf(r)
            }
        }
    )
```

Dropping `NullValue` is what makes `input.r.i.n` return only the `n` values that
exist, which is the XPath behaviour. Note the COMPILED strategy needs the same
change — `ASTCompiler` already special-cases `expr.property != "*"` (EB16), so
it will silently fall back to the interpreter rather than diverge, but a parity
test belongs with the fix.

## The fix

`modules/core/.../interpreter/interpreter.kt`:

1. `evaluateMemberAccess` split into `evaluateMemberAccess` (evaluates the target) and
   `accessMember(target, expr)` (applies the access to an already-evaluated value), so the
   same access can be re-applied per element without re-evaluating `expr.target`.
2. `projectOverSequence` maps the access over the members, flattens one level, and drops
   `NullValue` misses — XPath node-set semantics.
3. Branches added for `RuntimeValue.ArrayValue` and for `UDM.Array` inside the `UDMValue`
   case (the latter is the XML repeated-element path).
4. `isPlainWildcard` — see below.

`modules/engine/.../strategy/compiled/RuntimeOps.kt`:

5. `getProperty` had the identical shape — `is UDM.Object -> ...; else -> nullValue()` — so a
   sequence target returned null. Same projection added there.

### The trap in the fix

The first attempt projected unconditionally, so `*` on a sequence meant "apply `*` to each
member". `items[*]` then returned `[10.00, 25.00, 15.00]` — the *prices* — which looks
plausible until `items[*].price` fails because it is asking numbers for a `.price`.

`*` applied to a **sequence** is that sequence; applied to an **object** it is every value.
`isPlainWildcard` draws that line, and excludes `@*`/`^*`, which do project (attributes and
metadata gathered across members).

### The half-fix that the parity suite caught

Fixing only the interpreter was **worse than not fixing it**, and this report originally said
the opposite. The note here claimed COMPILED would "silently fall back to the interpreter
rather than diverge", reasoning from EB16's `expr.property != "*"` guard. That guard only
covers the wildcard spellings. `input.r.i.n` contains no wildcard, so `ASTCompiler` compiled
it happily, `RuntimeOps.getProperty` returned null for the array target, and COMPILED and COPY
disagreed with TEMPLATE:

```
TEMPLATE: {"names": ["a","b","c"]}
COPY    : {"names": null}
COMPILED: {"names": null}
```

So the interpreter-only fix turned a loud, consistent error into a silent, strategy-dependent
wrong answer — the failure mode this project has hit repeatedly (B34, B35, B36). The
cross-strategy parity harness caught it on the first run; the `b49_bracket_wildcard` fixture
passed throughout precisely *because* `[*]` is non-compilable and fell back.

## Verification

- `B48SequenceProjectionTest` — 12 tests, including a vacuity guard that the fixtures really
  hold arrays, and an explicit regression test for the trap above.
- Conformance: `language/selectors/b48_projection_over_repeated_xml.yaml`,
  `language/selectors/b49_bracket_wildcard.yaml` (output XML so numeric text is compared
  literally; input supplied as a literal block because inline YAML would strip the scale).
- Cross-strategy parity: `ConformanceStrategyParityTest` 404/404 — TEMPLATE, COPY and
  COMPILED agree on both new fixtures.
- Full suites: core, stdlib, CLI, engine, conformance 571/571.

## Related

- [B49](B49-documented-wildcard-and-descent-selectors-are-parse-errors.md) — the
  `[*]` and `..` forms from the same documentation page do not parse at all.
- The `map()` workaround is what `scripts/utlx/gh-action-runtime.utlx` uses, with
  a comment explaining why the obvious form is not used.
