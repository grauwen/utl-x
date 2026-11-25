# B72: `contains` searched for empty text when given `null`, so it answered `true`

> **B = core/formats/stdlib** — `:stdlib`'s `CoreFunctions`.

**Status:** Fixed — 2026-11-25.
**Priority:** Medium — a silent wrong answer on a common pattern; no crash, no warning.
**Component:** `stdlib` — `core/CoreFunctions.kt` (`contains`)
**Found:** 2026-11-25, while resolving `contains` for
[B69](B69-one-name-several-signatures-in-the-catalogue.md): checking what the surviving
implementation does at its edges, rather than only that it was the one registered.

## Symptom

```
contains("abc", null)      => true      should be false
contains("abc", [1])       => true      a script mistake, hidden
contains(null, "")         => true
```

Harmless-looking in isolation. Not in this pattern, used at four call sites in three example scripts —
a comma-separated list in a string, and a field as the value looked for:

```utlx
Region: if ($input.warehouse.location.state == "CA") "WEST"
        else if (contains("TX,OK,NM,AZ", $input.warehouse.location.state)) "SOUTHWEST"
```
(`examples/json/04-inventory-transaction-to-warehouse-ledger-update.utlx:68`)

| input | before | after |
|---|---|---|
| `{"state": "TX"}` | `true` | `true` |
| `{"state": "CA"}` | `false` | `false` |
| `{}` — no state | **`true`** | `false` |
| `{"state": null}` | **`true`** | `false` |

A warehouse with no state was classified SOUTHWEST.

## Cause

The string branch and the object-key branch both read the value to look for the same way:

```kotlin
val searchStr = (element as? UDM.Scalar)?.value?.toString() ?: ""
```

Two different inputs fall through to the `?: ""`:

- **`null`** — it is a scalar, but its value has no text;
- **a list or object** — `as? UDM.Scalar` fails.

Both became the empty string, and every string contains the empty string (`"abc".contains("")` is
`true` in Kotlin, Java, JavaScript and Python alike). So for those inputs the answer was `true`
whatever the text being searched — the function stopped looking at its first argument at all.
The haystack had the same fallback: `contains(null, "")` searched `""` for `""`.

## Fix

The value looked for is resolved once, explicitly:

| value looked for | in text | in an object's keys | in an array |
|---|---|---|---|
| a single value | as before | as before | as before |
| `null` | **`false`** | **`false`** | as before — an array may hold `null` |
| a list or object | **error** | **error** | as before — an array may hold lists |

And `contains(null, …)` is `false`: nothing is found in a null.

**Why `null` is `false` and not an error:** an absent field is normal data. A guard such as
`contains("TX,OK,NM,AZ", $state)` should answer *no* for a missing state, not stop the
transformation.

**Why a list is an error and not `false`:** searching text for a list is never data — it is a
script that has its arguments the wrong way round (`contains(value, list)` for
`contains(list, value)`), and the message says so. Returning `false` would hide that mistake just as
`true` did. This follows [VB04](VB04-named-validators-fail-open-on-malformed-arguments.md): a
malformed argument fails loudly instead of producing an answer.

**Arrays are untouched.** They compare elements by equality, where `null` and lists are legitimate
members: `contains(["a", null], null)` is still `true`.

## Kept on purpose

Two behaviours that look odd were examined and deliberately **not** changed; the tests pin both, so
changing either later is a visible decision:

- **Numbers are compared as text:** `contains(12345, "23")` is `true`. The same field is a number
  when read from JSON and text when read from XML or CSV; comparing as text gives one answer for
  both, which is what a format-agnostic language needs. Now stated in the function's catalogue notes.
- **Arrays compare strictly:** `contains([1, 2, 3], "2")` is `false`. Loosening it would make
  `[1, "1"]` ambiguous.

## Known limitation — the comma-list pattern

The empty string is still found in every string, because `""` is a real value, not a missing one:
`contains("TX,OK,NM,AZ", "")` remains `true`. A field holding `""` is therefore still classified
as SOUTHWEST by the pattern above. The pattern also matches fragments — `"X,O"` is "in" the list.
The robust form is the array one, which compares whole values:

```utlx
contains(["TX", "OK", "NM", "AZ"], $input.warehouse.location.state)
```

The examples were left as they are; moving them to the array form is a separate clean-up.

## Measurement

Before changing behaviour, every `contains(` call in the repository was classified — all 144, not
a sample ([B71](B71-concat-was-four-operations-behind-one-name.md)'s lesson):

| second argument | sites |
|---|---|
| a literal string | 107 |
| an expression (field, variable, call) | 34 |
| one-argument (other functions named `contains`) | 3 |

**No site passes a literal `null`, list or object.** Of the 34 expressions, those searching an
*array* are unaffected. Those searching a *string* — the four comma-list call sites in the examples and a few doc
snippets — change only when the field is null or missing, which is the bug being fixed.

## Tests

- `stdlib/src/test/.../core/B72ContainsNullAndNonScalarTest.kt`, 7 tests: null in text, text in
  null, null key, list/object as an error in text and in keys, ordinary searches unchanged, and the
  two kept behaviours. The null-key test carries a **vacuity guard**: its object has an `""` key —
  exactly what `null` used to be turned into — so it returned `true` before the fix.
- `conformance-suite/.../stdlib/string/b72_contains_null_and_non_scalar.yaml`: the warehouse
  pattern end to end, plus the error variant. Run against the pre-fix jar it failed on exactly the
  expected points (missing state SOUTHWEST, null found in text, no error for a list); after the
  fix it passes. `ConformanceStrategyParityTest` picks it up, so TEMPLATE, COPY and COMPILED are
  all held to it.

Verification: stdlib 2,400 tests, conformance 590/590, `:modules:engine:test` — see the commit.
