# B57 — projection throws on a non-object member but skips a missing key

**Status:** Fixed — 2026-11-13
**Component:** `:core` — `interpreter/interpreter.kt`, `projectOverSequence` / `accessMember`
(and the matching `RuntimeOps` path in `:modules:engine`)
**Found:** 2026-11-13, checking whether `$input.*.name` finding children and not grandchildren
was a defect — it is not, but this is
**Severity:** Medium — a wildcard projection fails outright on an ordinary document shape

> The number was briefly used in this session for a recursive-descent report that was withdrawn
> as a duplicate of [B49](B49-documented-wildcard-and-descent-selectors-are-parse-errors.md)
> before it was ever committed. B57 means only what is below.

## Summary

[B48](B48-path-steps-do-not-project-across-a-sequence.md) made a path step distribute over a
sequence, and chose a **skip** policy for members that cannot contribute — `projectOverSequence`
flat-maps and drops a `NullValue`:

```kotlin
when (val result = accessMember(element, expr)) {
    is RuntimeValue.ArrayValue -> result.elements
    is RuntimeValue.NullValue  -> emptyList()      // absent → contribute nothing
    else                       -> listOf(result)
}
```

That works for an object missing the key, but a **scalar** member throws before it can be
dropped:

```bash
# {"a": {"name":"A"}, "b": {"other":1}}          — b lacks "name"
$input.*.name     # ["A"]                         skipped, as designed

# {"a": {"name":"A"}, "b": {"name":"B"}, "title": "ROOT"}   — title is a scalar
$input.*.name     # Error: Cannot access property on non-object UDM type
```

Both members are "this element cannot contribute a value". Treating one as a skip and the
other as fatal is an inconsistency inside B48's own rule, not a considered distinction.

## Why the failing shape is ordinary

`{ "a": {...}, "b": {...}, "title": "ROOT" }` — a container holding both child records and a
scalar label — is an everyday JSON shape, and XML mixed content is the same thing: an element
whose children are a mix of elements and text. So `.*` followed by a named step is fragile in
exactly the documents the projection was built for. The workaround is to know in advance that
the document is homogeneous, which is the assumption a format-agnostic language cannot make.

`?.` does not rescue it either — `$input.*?.name` returns `null` for the whole expression
rather than projecting, and `$input.*.name?` is a parse error.

## Suggested fix

Inside **projection only**, a member that cannot be accessed contributes nothing, exactly as
an absent key does. Direct access keeps throwing:

```bash
$input.title.name     # still an error — a scalar has no properties, and this is a
                      # programmer mistake rather than a shape of the data
```

The split is the point: `$input.*.name` asks "the name of each of these", where a member with
no name is simply not an answer; `$input.title.name` asks for one specific thing that cannot
exist. Loud where the author is wrong, quiet where the data is heterogeneous — the same
reasoning B48 applied to the missing-key case.

## The fix

`projectOverSequence` asks whether a member is *capable* of answering before handing it to
`accessMember`:

```kotlin
private fun canHoldProperties(value: RuntimeValue): Boolean = when (value) {
    is RuntimeValue.ObjectValue -> true
    is RuntimeValue.ArrayValue  -> true
    is RuntimeValue.UDMValue    -> value.udm is UDM.Object || value.udm is UDM.Array
    else -> false
}
```

Asked **ahead of** the access rather than catching its exception, so a genuine failure inside
`accessMember` still propagates rather than being silently swallowed as "no contribution".

`accessMember`'s contract is unchanged, which is what keeps direct access loud:
`$input.title.name` still throws.

### It removed a strategy divergence rather than creating one

`RuntimeOps.getProperty` on the compiled path returns `UDM.Scalar.nullValue()` for a
non-object target, and its array branch already dropped nulls — so **COMPILED and COPY were
skipping scalars all along while TEMPLATE threw**. The comment there claims the branch is
"matching the interpreter's `projectOverSequence`"; it was not. No engine change was needed:
the interpreter was the outlier, and this aligns it with the behaviour the other two already
had.

That also means the divergence was live and unnoticed, because no fixture exercised a
projection over mixed content. The new one closes that.

## Verification

| | |
|---|---|
| `:modules:core:test` | **358**, 0 failures (incl. `B57MixedSequenceProjectionTest`, 9) |
| `:modules:cli:test` | 148, 0 failures |
| `:stdlib:test` | 2328, 0 failures |
| `:modules:engine:test` | **1015**, 0 failures |
| conformance (JVM) | **576**/576 |
| conformance (**native**) | 576/576 |

```bash
# <order><title>ACME</title><line><sku>A1</sku><qty>2</qty></line><line><sku>B2</sku></line></order>
$input.order.*.sku    # ["A1","B2"]   <title> is a text child — skipped, was fatal
$input.order.*.qty    # [2]           the second <line> has no <qty> — skipped (B48)
$input.order.line.sku # ["A1","B2"]   naming the element still projects

$input.title.name     # Error: Cannot access property on non-object UDM type   — still loud
```

Both halves are asserted, because a fix that silenced the second would have hidden real
mistakes. `B57MixedSequenceProjectionTest` also pins the case where *every* member is a scalar
(an empty sequence, not an error) and re-asserts B48's identity rule for `[*]`, which a fix
routing everything through the projecting path would break.

`language/selectors/b57_mixed_content_projection.yaml` carries it into the corpus;
`ConformanceStrategyParityTest` therefore checks it on TEMPLATE, COPY and COMPILED. Engine
count 1014 → 1015 confirms it was picked up.

## Verification needed with the fix


- a conformance fixture over a container mixing child objects with a scalar, asserting the
  projection yields only the object members' values
- the same for XML mixed content, which is the format where this is commonest
- `$input.title.name` asserted as **still** an error, so the fix does not silence a genuine
  mistake
- `./gradlew :modules:engine:test` — `RuntimeOps.getProperty` carries the compiled path's copy
  of this logic, and B48 shipped with that half missing, which the parity suite caught
