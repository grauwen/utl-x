# VB08: the value rule kinds cannot read an XML field

> **VB = `validate.*` semantic validation** (`stdlib/validate/ValidateFunctions.kt`). Sits beside
> [VB03](VB03-validate-oneOf-numeric-membership-fails.md), which fixed the other way the same
> validators misread a value — the numeric box it arrived in.

**Status:** **FIXED (2026-11-23)** — the scalar readers route through `UDM.unwrapXmlTextNode()`,
`:core`'s existing definition of that unwrapping.
**Priority:** High — it contradicted the premise the namespace exists for, and it did so silently.
**Component:** `stdlib` — `validate/ValidateFunctions.kt`, the `asString` / `asNumber` /
`scalarValue` helpers
**Found:** 2026-11-23, while building a guard-policy demonstration: the same rule block that passed
a JSON message rejected the equivalent XML message with a type complaint.

## Summary

`xml_parser` wraps every text-only element "for consistent structure":

```
<qty>500</qty>   →   UDM.Object(mapOf("_text" to Scalar(500)))
```

Member access unwraps that, so `$input.order.qty` is a number to the interpreter and `isNumber`
agrees. The validators read `properties["qty"]` **directly** and saw the wrapper, so every rule kind
that reads a *value* failed on XML input:

```
{ rule: "qty-in-range", inRange: { field: "qty", min: 1, max: 100 } }

  XML  → "Field 'qty' is not a number (inRange)"
  JSON → "Field 'qty' (500.0) is above maximum 100.0"
```

`inRange`, `oneOf`, `matches`, `isoDate` and `unique`'s key extraction were all affected.

## Why it hid

Two reasons, and both are worth keeping in mind for the next one:

1. **The verdict was still invalid.** A failing rule failed either way, so any test asserting only
   `valid` passed against the bug. What was wrong was the **message** — and a correct, attributable
   message is the entire value of a named rule. A guard's audit record and a hardware failed-rule
   table both consume `issues[]`, not the boolean.
2. **Key-based rules were unaffected.** `required`, `onlyFields` and `absent` ask about keys, never
   values, so a policy built from those worked perfectly on XML and gave no hint. The conformance
   corpus for `validate.*` was JSON-shaped throughout, so nothing crossed the two.

## Why it matters more than a wrong message

It falsified the claim the namespace is for. From the rule library: *"switch `input json` to
`input binf {definition: …}` and the **same** rule block polices a binary message — because the
rules run on UDM, not on the wire format."*

The rules did run on UDM. They read **one shape** of it. For a design where the JVM
`ValidationResult` is the **oracle** a hardware verifier's failed-rule bitmask is proved against,
"same verdict, different message, depending on the input format" is not a cosmetic defect — the
oracle disagreed with itself across formats.

## The fix

```kotlin
private fun unwrapped(udm: UDM?): UDM? = udm?.unwrapXmlTextNode()

private fun asString(udm: UDM?): String? = (unwrapped(udm) as? UDM.Scalar)?.value?.toString()
private fun asNumber(udm: UDM?): Double? = ((unwrapped(udm) as? UDM.Scalar)?.value as? Number)?.toDouble()
private fun scalarValue(udm: UDM?): Any? = (unwrapped(udm) as? UDM.Scalar)?.value
```

`UDM.unwrapXmlTextNode()` (`udm_core.kt`) already existed and is what member access uses, so the
validators now share one opinion about the shape instead of keeping a second. Three helpers, so
every current and future rule kind that reads a scalar inherits it.

`isSet` is deliberately **not** changed: an empty XML element is `Object(emptyMap())`, which the
unwrapping does not match anyway, and counting it as present is the fail-closed answer for
`absent`.

## Verification

- `stdlib/src/test/kotlin/.../validate/VB08XmlFieldReadTest.kt` — 7 tests, building the wrapper
  shape the parser actually emits.
- `conformance-suite/.../language/validate/value_rules_on_xml_input.yaml` — picked up by
  `ConformanceStrategyParityTest`, so it runs on TEMPLATE, COPY and COMPILED.

Proven load-bearing by mutation — reverting `unwrapped()` to the identity:

| | result |
|---|---|
| `VB08XmlFieldReadTest` | **5 of 7 fail** |
| `language/validate` fixtures | **24 → 23** |

The two unit tests that still pass are the ones not meant to be sensitive: `key-based rules were
unaffected` and `unwrapping only applies to the single-underscore-text shape`.

The load-bearing assertions are deliberately about the **message**, not the verdict:

- `inRange reads a wrapped number and reports the value, not a type error` — asserts
  `"above maximum"` is present **and** `"is not a number"` is absent. Asserting `valid == false`
  would have passed against the bug.
- `matches reads a wrapped string and can PASS` — the strongest direction, since a rule that cannot
  read its field fails; passing proves a read happened.
- `the same rule gives the same verdict AND the same message on XML and JSON` — the namespace's
  premise as a test, over three rule kinds.
- `unwrapping only applies to the single-underscore-text shape` — a vacuity guard on the fix: a
  mixed-content element (`_text` plus a sibling property) must **not** collapse to its text.
