# VB03: `validate.oneOf` fails numeric membership — `50` is not found in `[50, 60]`

> **VB = Validation bug** (`%utlx 1.1` / `validate.*`), after
> [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) and
> [VB02](VB02-validate-assert-lambda-when-guard-silently-ignored.md). This is **VB03**.

**Status:** **FIXED (2026-10-23, pending commit)** — scalar comparison now canonicalises numbers
instead of relying on `Any?.equals`. First spotted 2026-10-22 as a side note in VB01 ("a possible
`oneOf` numeric-equality quirk. Tracked separately"), carried as a documented caveat in the
[Validation Rules](../language-guide/validation-rules.md) guide, fixed here. See §Implementation.
**Priority:** **High** — a controlled-value check that rejects values that **are** in the controlled
list is a false-positive generator; on a fail-closed boundary guard every conforming numeric payload
is routed to dead-letter.
**Component:** `stdlib/.../validate/ValidateFunctions.kt` — `oneOf` (reported) and `unique` (same
root cause, found while fixing).

> **One-line:** `$input |> validate.oneOf("price", [50, 60])` on `{"price": 50}` reports
> `valid:false`, while `{"price": 50.0}` reports `valid:true` and string membership works fine.

## Evidence (via the built CLI, before the fix)

```
$input |> validate.oneOf("price", [50, 60])
```

| Payload | Result |
|---|---|
| `{"price": 50}` | ❌ **`valid:false`** — "value '50' is not one of [50.0, 60.0]" |
| `{"price": 50.0}` | ✅ `valid:true` |
| `{"price": "50"}` | `valid:false` (correct — a string is not the number) |
| `{"currency": "EUR"}` in `["EUR","USD"]` | ✅ `valid:true` |

The `50` vs `50.0` split is the whole bug, and the pre-fix message gave it away: it rendered the
allowed list as `[50.0, 60.0]`, i.e. the two sides were never the same kind of number.

## Root cause

```kotlin
val allowed = (args.getOrNull(2) as? UDM.Array)?.elements?.map { scalarValue(it) } ?: emptyList()
val actual = scalarValue(props(payload)[field])
val issues = if (actual in allowed) emptyList() else listOf(...)
```

`scalarValue` returns the **raw boxed value** (`Any?`), and `in` is `Any?.equals`, which is
type-sensitive. The two sides reach this point in different boxes:

- `{"price": 50}` — the JSON parser produces an **Int** (`50`).
- `[50, 60]` — the script literal is evaluated by the interpreter, whose numbers are **Double**
  (`50.0`, `60.0`), then converted to `UDM.Scalar`.

`Int(50).equals(Double(50.0))` is `false` in Kotlin/Java, so membership failed. Strings were
unaffected because both sides are `String`. Nothing was wrong with `oneOf`'s logic — only with the
assumption that equal numbers arrive in equal boxes.

**`unique` has the identical defect**, found while fixing this one:

```kotlin
val dupes = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
```

`groupingBy` keys on `equals`/`hashCode`, so an `Int 7` and a `Double 7.0` land in different groups
and a real duplicate goes unreported — a **fail-open** miss rather than a false positive. Exposure is
narrower (values within one JSON array usually parse alike) but the root cause is the same, and
leaving it would be the same mistake as shipping B34's interpreter fix without the compiled path.

## Fix

A single canonicalisation helper, shared by both functions:

```kotlin
private fun canonicalScalar(value: Any?): Any? = when (value) {
    null -> null
    is Boolean -> value          // Boolean is not a Number; keep it out of the numeric path
    is BigDecimal -> value.stripTrailingZeros()
    is Double -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
    is Float -> if (value.isFinite()) BigDecimal(value.toString()).stripTrailingZeros() else value
    is Number -> BigDecimal(value.toString()).stripTrailingZeros()
    else -> value
}
```

- **Via `toString`, not `BigDecimal(double)`** — the latter would introduce binary-float noise
  (`BigDecimal(0.1)` is `0.1000000000000000055511151231257827…`), which is exactly the kind of
  surprise a validator must not have.
- **`stripTrailingZeros`** makes `BigDecimal.equals` scale-insensitive, so `50`, `50.0`, `50.00` and
  `5E1` are one value.
- **Non-finite doubles keep their boxed form** — `BigDecimal` cannot represent NaN or infinity.
- **Booleans are matched before `Number`**, so `true` can never equal `1`.

### Deliberate non-changes

- **No cross-kind coercion.** The string `"50"` still does not match the number `50`. `oneOf` is a
  controlled-value check, not a parser; a payload whose field is the wrong *type* should fail it.
  (If a caller wants coercion that is a different function, not a looser `oneOf`.)
- **The issue-message format is unchanged** — `Field 'x' value 'V' is not one of [A, B]` — because
  `validate_oneOf_fail.yaml` asserts it. Numbers inside it now render via the canonical form, so the
  text reads `[50, 60]` rather than `[50.0, 60.0]`; that is the message *matching the script the
  author wrote*, not a format change. One consequence worth knowing: a string and a number now
  display identically, so `value '50' is not one of [50, 60]` is what a mistyped `"50"` looks like.
  Quoting strings in the message would disambiguate it but would break the asserted format, so it
  is left alone.

## Implementation (2026-10-23)

- **`:stdlib` `ValidateFunctions.kt`** — added `canonicalScalar` plus a `displayScalar` used for
  issue text; `oneOf` compares canonical forms; `unique` groups on the canonical form. `import
  java.math.BigDecimal` added. No signature or result-shape change, so `:core` and the engine are
  untouched.

## Tests

- `conformance .../validate/oneOf_numeric.yaml` — an Int payload against script Double literals, a
  decimal match, a genuine non-match, and the string-is-not-a-number case, in one script. ✅
- `ValidateFunctionsTest` (`:stdlib`) — 9 added tests at the UDM layer, where the box mismatch
  actually lives: Int-vs-Double both directions, `BigDecimal("50.00")` scale-insensitivity, a real
  non-match, no string coercion, booleans kept out of the numeric path, the rendered message, and
  the two `unique` cases.
  Verified to be a true regression guard: against the pre-fix `ValidateFunctions`, **5 of the 9
  fail**, and the 4 that pass are exactly the behaviours VB03 did not change (non-match, no string
  coercion, booleans, distinct `unique` keys).
- Full conformance **540/540** on JVM and the rebuilt GraalVM native binary; `language/validate`
  11/11.
- `:stdlib:test` green (ValidateFunctionsTest 21 tests); `:modules:engine:test` green (451 tests).
  `:modules:core:test` (208 tests) was green on its own run and is untouched by this fix — `:core`
  does not depend on `:stdlib` (the reflection-free lookup indirection), so the change cannot
  reach it.

## Related

- [VB01](VB01-validate-assert-raw-check-crashes-on-missing-or-mistyped-data.md) — flagged this as a
  side note while establishing that the **named** validators are the robust tier. VB03 is that claim
  being made true: a validator that rejects conforming values is not robust, however gracefully it
  avoids crashing.
