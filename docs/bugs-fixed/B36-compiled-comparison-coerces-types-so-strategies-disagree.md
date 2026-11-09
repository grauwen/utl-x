# B36: the compiled path coerces types in comparison, so the strategies disagree

> **B = core bug** (`:core` interpreter / parser / AST and the execution strategies built on it),
> the family of B01–B35. This is **B36**.

**Status:** **FIXED.** The compiled path now compares exactly as the interpreter does.
**Priority:** High. The divergence is between the **production** execution path and the one every
test and CLI invocation uses, so it is invisible until deployment.
**Component:** `:engine` — `RuntimeOps.equal` / `notEqual` / `greaterThan` / `greaterEqual` /
`lessThan` / `lessEqual` and the private `compareValues` they shared.

> **One-line:** `"5" == 5` was **true** on the COMPILED path and **false** in the interpreter, and
> `"5" < 6` returned a verdict compiled where the interpreter raised an error — so the same
> transformation gave different answers depending on which executable ran it.

## The contract this broke

The guide states it without qualification:

> A transformation that works in one executable works identically in all three. A bug fix in the
> shared core fixes all three simultaneously. This is by design — the conformance suite (500+ tests)
> validates behavior across executables.

The second half is what let this survive. The conformance suite drives the `utlx` CLI, which
executes through `Interpreter`; there is no strategy flag on the transform command. So the suite
validates **one** path, while `utlxe` — the production engine, whose default strategy is COMPILED —
was only ever checked by hand-written unit tests. The claim that behaviour is validated *across*
executables was not true of the compiled strategy.

## Measured divergence

Driving `ASTCompiler` and `Interpreter` over the same program and payload:

| expression | COMPILED | interpreted |
|---|---|---|
| `"5" == 5` | `true` | **`false`** |
| `5 == "5"` | `true` | **`false`** |
| `"5" != 5` | `false` | **`true`** |
| `"5" < 6` | `true` | **error** |
| `"5" > 4` | `true` | **error** |
| `true == 1` | `true` | **`false`** |
| `true == "true"` | `true` | **`false`** |
| `null == 0` | `false` | `false` |
| `"abc" == 0` | `false` | `false` |
| `"abc" < "abd"` | `true` | `true` |

Seven of twelve probed comparisons disagreed.

## Root cause

All six comparison operators were expressed in terms of one ordering function:

```kotlin
fun equal(left: UDM, right: UDM): UDM = UDM.Scalar(compareValues(left, right) == 0)
```

and that function coerced before comparing:

```kotlin
private fun toNumber(udm: UDM): Double? = when (udm) {
    is UDM.Scalar -> when (val v = udm.value) {
        is Number  -> v.toDouble()
        is String  -> v.toDoubleOrNull()      // ← a numeric string became a number
        is Boolean -> if (v) 1.0 else 0.0     // ← a boolean became 1 or 0
        else -> null
    }
    else -> null
}
```

with a final fallback that compared the two values' `toString()` forms — which is how
`true == "true"` came to be true.

Two distinct mistakes, and the second is the structural one:

1. **Coercion in equality.** `toNumber` is a reasonable helper for *arithmetic*, where an operand
   has to become a number or fail. Reusing it for equality turned `==` into a coercing comparison.
2. **Equality defined as "ordering says zero".** Equality and ordering are not the same relation.
   Ordering must either produce a total order or refuse; equality must answer for *any* two values.
   Deriving one from the other forced equality to accept every coercion ordering needed, and forced
   ordering to invent an answer for pairs it could not order.

## Why the interpreter's semantics won

The looser side was the compiled one, and nothing argued for it:

- **It is type confusion, not imprecision.** A rule written `check: p.id == 5` admitted the *string*
  `"5"` in production while rejecting it on the developer's machine. For a component whose verdict
  decides whether a message is forwarded, silently accepting a differently-typed value is the
  failure mode that matters.
- **It contradicts the validation functions.** The shared canonicaliser says so in its own comment:
  the string `"50"` is **not** the number 50, and a controlled-value check is not a coercion.
- **The conformance fixtures pin the interpreter.** 554 tests encode its behaviour; none encode the
  compiled coercion. Aligning the other way would have invalidated the suite.
- **Fail-closed beats a confident wrong answer.** For ordering across unlike types the interpreter
  raises "Cannot compare these types". The compiled path returned `true`. A rejected transformation
  is recoverable; a wrong verdict that forwards a message is not.

## The fix

Equality and ordering are now separate relations, both mirroring the interpreter:

- **`valuesEqual`** — two numbers compare canonically (so B35's representation-independence holds:
  `5 == 5.0`, `100.10 == 100.1`, and two different integers above 2⁵³ are *not* equal); anything
  else compares by raw value with no conversion; a non-scalar is never equal to anything.
- **`orderOf`** — numbers order exactly, strings order lexicographically, and any other pair throws
  the same `RuntimeError("Cannot compare these types")` the interpreter raises. `RuntimeError`'s
  location is optional, so the compiled path raises the identical error without inventing one.

`toNumber` is untouched and still used by arithmetic, where coercion is the correct behaviour.

## Behaviour changes callers see

On the COMPILED path only — the interpreted path is unchanged:

- `"5" == 5` and `true == 1` now return `false`. A deployment relying on either was relying on
  behaviour the CLI never reproduced.
- Ordering a string against a number, or a boolean against anything, now raises instead of
  returning a verdict. This is the fail-closed direction, but it converts a silent wrong answer into
  a visible error, so a transformation that was quietly comparing unlike types will now stop.

**No existing test or fixture changed.** The full `:engine` suite (468 tests) passed unmodified,
which is itself evidence the coercion was accidental rather than relied upon — including
`StrategyParityTest`, whose 26 cases were all already type-consistent.

## Tests

`B36ComparisonParityTest` (`:engine`) asserts, for every case, that the two strategies **agree** and
*what* they agree on — so a later alignment in the wrong direction fails too. It drives
`ASTCompiler` directly and asserts `compile(...) != null` first, because `CompiledStrategy` falls
back to the interpreter when compilation returns null: a test routed through the strategy could pass
while measuring the interpreter and prove nothing about the compiled path.

Coverage: numeric strings, booleans against numbers and against their own spelling, null against
every falsy value, ordering refusal across unlike types, and a group of cases that must keep
working, including the B35 exactness properties so this fix cannot quietly undo that one.

Revert-verified: restoring the coercing implementation fails 5 of the 9 tests; the file was then
restored byte-identical.

## The structural gap this exposed — now closed

> **Update.** This section proposed two routes. The preferred one — a `--strategy` option on the
> transform command — turned out to be the wrong call: `modules/cli` does not depend on
> `modules:engine`, so it would mean bundling the engine into the open-source CLI, which is a
> packaging decision rather than a test-coverage one. The alternative was taken instead, and the
> corpus now runs on all three strategies from inside `:engine`:
> [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md).
>
> It found **101 existing divergences** across 351 fixtures, now an asserted inventory. Notably
> **COMPILED never produced different output** — it agrees or refuses to run — while **COPY differs
> on 44 fixtures**, emitting XML attributes as child elements. It also found that 7 fixtures are
> malformed YAML and have never been executed by either suite. The original text follows.
>
> ---

## The structural gap this exposed — original assessment

The defect existed because **no conformance fixture runs on the compiled strategy.** B34 had the
same shape (its compiled half "was previously verified only by *reading* the emitted bytecode") and
so did B35 (literals and comparison were fixed in the interpreter while COMPILED stayed lossy). That
is three bugs from one gap, which makes it the gap worth closing rather than patching around:

- **Preferred:** a `--strategy` option on the transform command, so the existing suite can be run
  against COMPILED with no new fixtures — `simple-runner.py` already accepts an arbitrary CLI path.
  This makes `utlxe` a first-class conformance target and makes the guide's cross-executable claim
  true.
- **Alternative:** have `StrategyParityTest` load the conformance fixtures directly and assert all
  three strategies agree. No CLI change, but it duplicates the fixture loader.

Until one of these exists, every core semantic fix needs a hand-written parity test, and the
conformance figure quoted for a release describes the interpreter alone.

## Related

- [B35](B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md) — the numeric axis of
  the same parity problem; found while auditing its coverage, and recorded there as an adjacent
  issue deliberately left for its own report.
- [B34](B34-logical-operators-do-not-short-circuit.md) — the first instance of a semantic fix landing
  in the interpreter while the compiled path diverged.
- [VB03](VB03-validate-oneOf-numeric-membership-fails.md) — the source of the doctrine that settled
  the direction: a controlled-value check is not a coercion.
