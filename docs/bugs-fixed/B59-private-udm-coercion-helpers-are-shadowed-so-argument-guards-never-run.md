# B59 — 36 files' private UDM coercion helpers are shadowed, so their argument-type guards never run

**Status:** Fixed — 2026-11-16
**Priority:** High — wrong output rather than an error; the internal UDM representation reached user data
**Component:** `:core` — `udm/udm_core.kt` (the shadowing member) and
`:modules:engine` — `strategy/compiled/RuntimeOps.kt` (the compiled path's copy).
The 36 `:stdlib` files that wrote the dead guards are untouched.
**Found:** 2026-11-14, while clearing compiler warnings after the 1.4.0 release

## Summary

36 stdlib files each declare their own `private fun UDM.asString()` (and `asNumber`,
`asBoolean`, `asArray`, `asObject`, `asBinary`, `toNative`) to coerce an argument and
reject the wrong type. Every one of them is shadowed by a member of the same name on
`UDM`, so **none of them is ever called**. The member is permissive where the extensions
were strict, so the type guards written throughout the standard library do not exist at
runtime, and a wrong-typed argument is stringified instead of rejected.

## Repro

```bash
echo '{"a":1}' | ./utlx -e 'upperCase($input)'
```

```
"OBJECT(PROPERTIES={A=SCALAR(VALUE=1)}, ATTRIBUTES={}, NAME=NULL, METADATA={})"
```

The internal Kotlin data-class rendering of the UDM, uppercased, as the transformation's
output. It gets worse where the value is then encoded:

```bash
echo '{"a":1}' | ./utlx -e 'convertXMLEncoding($input, "UTF-8", "UTF-16")'
```

```
<?xml version="1.0" encoding="UTF-16"?>
佢橥捴⡰牯灥牴楥猽筡㵓捡污爨癡汵攽ㄩ紬 ...
```

## Root cause

`StringFunctions.kt` intends to reject a non-scalar with a usable message:

```kotlin
private fun UDM.asString(): String = when (this) {
    is UDM.Scalar -> { /* ... */ }
    else -> throw FunctionArgumentException(
        "Expected string value, but got ${getTypeDescription(this)}. " +
        "Hint: Use toString() to convert values to strings."
    )
}
```

`XMLEncodingBomFunctions.kt` intends the nullable form, so each call site can raise its own
error:

```kotlin
private fun UDM.asString(): String? = (this as? UDM.Scalar)?.value as? String
// ...
val xml = args[0].asString() ?: throw FunctionArgumentException(
    "convertXMLEncoding() first argument must be a string")
```

Neither runs. `UDM` has a member of the same name (`modules/core/.../udm/udm_core.kt`),
and in Kotlin **a member always wins over an extension** — the compiler says so, 56 times:

```
w: ...StringFunctions.kt:451 Extension is shadowed by a member: public final fun asString(): String
```

The member never fails and never returns null:

```kotlin
fun asString(): String {
    val unwrapped = unwrapXmlTextNode()
    return when (unwrapped) {
        is Scalar -> /* ... */
        else -> this.toString()      // <-- the data-class dump
    }
}
```

So `?: throw` can never fire. The compiler reports that too, 14 times in the one file that
used the nullable form:

```
w: ...XMLEncodingBomFunctions.kt:96 Elvis operator (?:) always returns the left operand of non-nullable type String
```

`asNumber()` behaves the same way — it answers `0.0` for an object rather than rejecting it —
which is where `JoinFunctions.kt:459` (`Condition 'propName != null' is always 'true'`) and
`JWTVerification.kt:137` (`Unnecessary safe call on a non-null receiver of type Double`) come
from. They are the same defect seen from the call site.

## Why it was not noticed

Three reasons, each worth keeping in mind:

1. **The code reads as correct.** Every call site has a visible guard. Nothing about
   `args[0].asString() ?: throw FunctionArgumentException(...)` suggests the throw is dead.
2. **The compiler did say so** — 70 warnings across the two families — in a build that emits
   256 warnings, where nobody reads them.
3. **No test passes a wrong type.** The stdlib test suite exercises each function with the
   types it documents, so the guards are never reached in anger. 4867 tests pass with the
   guards dead.

## Scope

- 56 shadowed declarations across 36 files (`asString` 26, `asNumber` 12, `asBoolean` 8,
  `asArray` 5, `asObject` 2, `asBinary`, `toNative`, `effectiveType` 1 each)
- 14 dead `?:` guards in `XMLEncodingBomFunctions.kt`
- one dead `!= null` condition (`JoinFunctions.kt:459`) and one dead safe call
  (`JWTVerification.kt:137`)

Reproduce the inventory with:

```bash
./gradlew compileKotlin --rerun-tasks 2>&1 | grep "shadowed by a member" | sort -u
```

## The fix — two lines, not thirty-six files

This report originally proposed renaming the 56 shadowed helpers. **That was never necessary.**
All ~289 call sites funnel through one member, and the damage came from its fallback — duplicated
once per execution path:

```
modules/core/.../udm/udm_core.kt                       else -> this.toString()
modules/engine/.../strategy/compiled/RuntimeOps.kt     else -> udm.toString()
```

Both now reject a value that has no text form, and both word it identically — **semantics live in
two places**, and letting them diverge is precisely B34/B35/EB03/B48.

```
upperCase($input)   =>  Error: Expected a text value but got an object.
                        Hint: select a field first (e.g. $input.name), or render the whole
                        value with toString() or renderJson().
upperCase("hi")     =>  "HI"            unchanged
```

Both suggestions in the hint were verified to work on an object before being offered —
`toString($input)` gives `{a: 1}` and `renderJson($input)` gives `{"a":1}`. A hint that cannot
deliver is worse than none (the lesson from CB06, where the first draft promised relief a parse
profile could not give).

### Temporal values gained a text form rather than an error

`DateTime`, `Date`, `LocalDateTime` and `Time` carry `toISOString()` but do **not** override
`toString()`, so they hit the same broken fallback: `upperCase(now())` returned
`"DATETIME(INSTANT=2026-11-16T…)"`. Erroring on them would have been a regression dressed as a
fix. They now render ISO:

```
upperCase(now())                 =>  "2026-11-16T08:32:32.657311Z"
upperCase(parseDate("2026-01-15")) =>  "2026-01-15"
```

### Why the blast radius was zero

`FunctionArgumentException` lives in **`:core`** (`modules/core/.../core/FunctionArgumentException.kt`),
not `:stdlib`, so the UDM member can throw the very type the stdlib's own argument errors already
use. Callers that catch it keep working.

That detail is the whole story of the cost. An exploratory version throwing
`IllegalArgumentException` broke two tests — `JWTFunctionsTest.testInvalidArguments` and
`JWSBasicFunctionsTest.testDecodeJWSInvalidType`, both asserting `FunctionArgumentException` for a
wrong-typed argument. With the right type, **0 of 4942 tests needed changing**.

Worth noting *why* those two passed before: `decodeJWT` called `args[0].asString()`, got the dump
string back, and the JWT **format** check then rejected it for not containing two dots. The type
was never checked — the test passed for the wrong reason. It now passes for the right one.

## What the 56 shadowed helpers do now

Nothing — they remain dead, and harmless. The guard they were written to provide now lives in the
one place every caller reaches. Deleting them is tidying, not a fix, and can happen whenever those
files are next touched.

## Tests

`B59AsStringNonScalarTest` (10 tests, `:modules:core`): the object and array failures, the message
naming the kind and the element name, the three temporal ISO renderings, and the unchanged paths —
scalars, XML `_text` unwrapping, and B35 exact decimal scale (`10.50` must not stringify through
Double).

`conformance-suite/utlx/tests/stdlib/string/b59_non_scalar_to_text.yaml` carries the half that must
keep working, and earns the cross-strategy check: `ConformanceStrategyParityTest` runs it through
TEMPLATE, COPY and COMPILED, which is what proves the two copies of the fallback agree.

**Revert-verified:** restoring `else -> this.toString()` fails seven of the ten; the three
unchanged-behaviour tests pass either way, as they should.

Full regression: **4942 JVM tests, 0 failures**; **582/582 conformance on the JVM and the GraalVM
native binary**.

## Breaking change — for the release notes

A script that passed a non-scalar to a text function used to receive the internal dump and now
gets an error. Nothing in the repository relied on it, but a user script might have — most likely
without realising, since the value it received was never usable. The fix turns silent corruption
into a loud failure naming the fix.

