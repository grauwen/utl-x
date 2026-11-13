# B53 — `Any` is assignable to nothing, so `--strict` rejects every real script

**Status:** Fixed — 2026-11-13
**Component:** `:core` — `types/type_system.kt`, `UTLXType.isAssignableTo`
**Found:** 2026-11-12, while checking whether `docs/comparison/vs-jq.md` may still claim
"strong typing — catches errors at compile time"
**Severity:** High — `utlx validate --strict` is unusable on any script that reads its input,
which is all of them

## Summary

A value derived from `$input` is typed `Any`, and `Any` satisfies **no** declared parameter
type. So every one of the type checker's core signatures reports a false
`Argument type mismatch` as soon as the argument comes from the input rather than a literal:

| call | `--strict` | runs? |
|---|---|---|
| `upper("a")` | passes | yes |
| `upper($input.s)` | **Argument type mismatch** | yes |
| `abs(-1)` | passes | yes |
| `abs($input.n)` | **Argument type mismatch** | yes |
| `sum([1,2])` | passes | yes |
| `sum($input.a)` | **Argument type mismatch** | yes |

12 of 12 functions that have a core signature (`upper`, `lower`, `trim`, `length`, `abs`,
`round`, `ceil`, `floor`, `sum`, `count`, `min`, `max`) fail this way; 4 of 4 pass on
literals. A realistic script is rejected outright:

```utlx
{ r: map(filter($input.items, (i) -> i.p > 10), (i) -> i.n) }
```

```
Type Errors:
  ✗ 5:24 - Argument type mismatch
✗ Validation failed            # exit 1, while the script transforms correctly
```

Note the perverse consequence: **a function with no core signature validates cleanly**, so
`groupBy($input.items, …)` passes while `filter($input.items, …)` fails. Adding a signature
makes validation worse, which is the opposite of the incentive the type checker needs.

## Root cause

One missing line. `isAssignableTo` handles `Unknown` symmetrically and `Any` in one
direction only:

```kotlin
fun isAssignableTo(other: UTLXType): Boolean {
    return when {
        this == other -> true
        other is Any -> true                               // anything → Any  ✅
        this is Unknown || other is Unknown -> true        // Unknown, both ways  ✅
        …
        else -> false                                      // Any → String lands HERE  ❌
    }
}
```

There is no `this is Any -> true`. `Any.isAssignableTo(String)` falls through every branch to
`else -> false`.

And `Any` is exactly what input-derived expressions carry — the `MemberAccess` branch returns
it by design:

```kotlin
is UTLXType.Any, is UTLXType.Unknown -> UTLXType.Any
```

So the two halves of the type system disagree about what `Any` means: the member-access path
uses it as "could be anything, don't complain", while `isAssignableTo` treats it as "a
specific type that happens to match nothing".

## The fix

One line. Today the rule reads:

```kotlin
other is Any -> true        // putting anything INTO an "unknown" slot is fine
```

It becomes:

```kotlin
this is Any || other is Any -> true
```

That is the whole change: the mirror half, saying that taking something *out* of an unknown
box and using it is fine too.

### Why that is the right shape and not a loophole

This is how gradual typing works. `Any` is not a type that describes a value — it is the
checker saying **"I don't know, so don't complain."** A type like that has to be permissive in
both directions. Permissive in one direction only does not mean "I don't know"; it means "I
know it is nothing", and it poisons every expression downstream of the input.

The checker already gets this right one line below, for its other wildcard:

```kotlin
this is Unknown || other is Unknown -> true    // symmetric, both directions
```

`Any` simply never got the same treatment, so the fix applies a policy that is already here
rather than inventing one.

### Why one line is enough

`isAssignableTo` is recursive, so the symmetry propagates. `sum` wants `Array<Number>`; given
an `Array<Any>` the rule recurses into the element types and asks `Any → Number`, which the
same new clause answers. Nesting needs no separate handling.

### Where it goes, and the one trap

Alongside the existing `Any` clause, above the `Null` rule — `Null` is not `Any`, so ordering
there is safe.

**Keep both clauses.** They are not interchangeable. A "simplification" that passes whenever
*either* side is `Any`-shaped would let `String → Number` through, trading an over-strict
checker for a useless one. The negative assertion is therefore part of the fix, not an
afterthought: `String.isAssignableTo(Number)` must stay `false`.

### What this fixes, and what it does not

**Fixes:** the false alarms. `upper($input.s)`, `sum($input.items)` and every other call whose
argument came from the document stop failing `--strict`, so the flag can go in CI.

**Does not fix** — it removes noise rather than adding rigour, and the report should not
pretend otherwise:

- only ~12 functions carry a signature in the core checker, so most calls are still not type
  checked at all;
- `$input` is still not typed from a schema (`--schema` feeds `AdvancedTypeInference` in
  `:modules:analysis`, never this checker), so no property is checked against the document's
  real shape;
- `upper(42)` is still reported although it returns `"42"` — some signatures are stricter than
  the functions they describe.

After this, `--strict` is *usable* but not *thorough*: it catches typos, arity and operators
applied to the wrong kind of value. Making it a real type gate needs the signature coverage
and the schema wiring, which are separate and larger.

## A second, separate issue found alongside

`upper(42)` is reported as a mismatch but **returns `"42"`** — the stdlib coerces. So some of
the 15 core signatures are stricter than the functions they describe, and tightening the
`Any` rule will not fix that class. The signatures need to state coercion (a union, or
`Any`) wherever the implementation accepts it. Listed here rather than filed separately
because the two are discovered by the same test and a fix for one should not claim the other.

## Relationship to B52

Pre-existing, not caused by
[B52](B52-type-checker-reports-every-stdlib-function-as-an-undefined-variable.md) — but
invisible until it. Before B52 a named callee was reported as `Undefined variable` *before*
the signature check ran, so `--strict` already exited 1 on these scripts, just with a message
that sent you somewhere else entirely. B52 made the real diagnostic reachable, and this is
what it says.

The two together are the honest state of static checking: **names and arity are now
trustworthy; argument types are not.**

## Verification

| | |
|---|---|
| `:modules:core:test` | **365**, 0 failures (incl. `B53AnyAssignabilityTest`, 7) |
| `:modules:cli:test` | **151**, 0 failures (3 B53 cases added to `B52ValidateFunctionNamesTest`) |
| `:stdlib:test` | 2328, 0 failures |
| `:modules:engine:test` | 1015, 0 failures |
| conformance (JVM) | 576/576 |
| conformance (**native**) | 576/576 |

All 12 functions carrying a core signature now accept an input-derived argument, and the
realistic script from the Summary passes:

```bash
utlx validate s.utlx --strict     # upper($input.s), sum($input.a), min($input.n, 1) …
✓ Validation passed

# { r: map(filter($input.items, (i) -> i.p > 1), (i) -> i.p) }
✓ Validation passed
```

**And the errors that should fail still do** — this is the half that matters, since the fix
widens a rule:

```bash
upper(42)          ✗   Number where the signature wants String
abs("x")           ✗
"abc" - 1          ✗
(42)[0]            ✗
upper("a","b")     ✗   arity
totallyMadeUp(1)   ✗   undefined function (B52)
nope               ✗   undefined variable
```

`B53AnyAssignabilityTest` asserts the type rule directly and pairs every positive with its
negative — `String -> Number`, `Number -> String`, `Array<String> -> Array<Number>` and
`Null -> String` all stay `false`, and `Null` keeps its own rule below the new clause. The CLI
tests cover the same thing end to end, with the literal spelling of each call kept alongside
the input-derived one so an over-broad fix would fail there too.

Re-verified on a freshly built GraalVM binary: `upper($input.s)` and the realistic script pass,
`upper(42)` and `totallyMadeUp(1)` still fail.

## What this did not change

As set out above — the noise is gone, the rigour is not yet there:

- ~12 functions carry a signature; the rest are still unchecked
- `$input` is still untyped from `--schema`, so no property is checked against the document
- `upper(42)` is still reported although it returns `"42"`

`--strict` is now usable in CI. Treating it as a real type gate still needs those two pieces.

## What the fix needed to show (all done, above)


- a table test over the 12 signatures above, each asserted in both spellings (literal
  argument and `$input`-derived argument) — the literal half is what stops an over-broad fix
  from silencing real mismatches
- `String.isAssignableTo(Number)` must stay `false`
- the realistic `map(filter(…))` script must pass `--strict`
- `./gradlew :modules:engine:test` — `isAssignableTo` is core, and the engine suite is what
  catches a core semantic change diverging across strategies
