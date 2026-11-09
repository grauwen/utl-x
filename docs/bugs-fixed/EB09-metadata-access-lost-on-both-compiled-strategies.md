# EB09: `^metadata` access is lost on both compiled strategies

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB08. This is **EB09**.

**Status:** **FIXED.**
**Priority:** High. Silent wrong answers on the engine's default strategy, across every
schema-describing format.
**Component:** `:engine` — `ASTCompiler.compileMemberAccess` and `CopyStrategy`
(`mergeDataIntoSkeleton`, `deepClone`, `buildEmptySkeleton`).

> **One-line:** `$input.^schemaType` evaluated to null on COPY and COMPILED, so every XSD and JSON
> Schema transformation that asks what it is reading got nothing back.

```
TEMPLATE   { "schemaType": "xsd-schema", "version": "1.0", "targetNamespace": "http://example.com/test" }
COMPILED   { "schemaType": null,         "version": null,  "targetNamespace": null }
```

## Two independent causes

`UDM.Object` carries three things: `properties`, `attributes` and **`metadata`**. Metadata is how
the schema formats expose their own identity — `^schemaType`, `^xsdVersion`, `^targetNamespace` —
and how XML carries its namespace context.

**1. The compiler had no branch for it.** `MemberAccess` carries `isAttribute` *and* `isMetadata`;
`compileMemberAccess` handled the first and not the second, so `^name` compiled as an ordinary
property lookup:

```kotlin
if (expr.isAttribute) { getAttribute(...) } else { getProperty(...) }   // ← no isMetadata
```

**2. `CopyStrategy` dropped it when rebuilding objects.** It constructs `UDM.Object` in three
places, and each passed `properties`, `attributes` and `name` while omitting `metadata` — which
defaults to empty. So even once the compiler could read metadata, COPY had already discarded it.

The second cause was only visible *after* the first was fixed: with the compiler corrected, COPY
became the sole remaining difference on the same fixtures. Fixing one of two silent failures of the
same value is indistinguishable from fixing neither, until you look again.

Both failed by returning **null** rather than raising, which is why this surfaced as a value
difference across 15 fixtures and never as an exception.

## Effect

| | before | after |
|---|---|---|
| known divergences | 35 | **20** |
| `BOTH_OUTPUT` | 26 | 12 |
| `COPY_OUTPUT` | 1 | **0** |
| xsd fixtures diverging | 10 | **1** (`RUNS_ONLY_ON_TEMPLATE`) |
| jsch fixtures diverging | 6 | 1 |

The compiler half accounted for 4 fixtures and the COPY half for 11 more.

## Tests

`EB09MetadataAccessTest` (`:engine`) drives the three strategies over a minimal XSD, because XSD is
where metadata actually originates. Every case asserts the three agree **and** that the value is
present — three nulls agree perfectly and would prove nothing, which is the trap this whole class of
defect sets.

Cases: a metadata key readable on every strategy; the `__`-prefixed internal keys resolving through
the bare name (both paths must go through `UDM.getMetadata`, not the raw map); several keys in one
object; an absent key yielding null rather than an error; metadata and a property read from the same
object, which guards the branch order so the new case cannot shadow property access; and a nested
read.

Revert-verified, and the result is worth recording precisely because it is uneven:

| reverted | tests failing |
|---|---|
| compiler `isMetadata` branch | **5 of 6** |
| `CopyStrategy.mergeDataIntoSkeleton` | **5 of 6** |
| `CopyStrategy.deepClone` | 0 |
| `CopyStrategy.buildEmptySkeleton` | 0 |

So **`mergeDataIntoSkeleton` is the load-bearing COPY site.** The other two are fixed for
consistency — dropping metadata there is latently wrong for exactly the same reason — but **no
current test exercises them**, because both only run when a transformation has a configured schema
and this test has none. That is a known coverage gap, not a claim of coverage; closing it needs a
fixture with a `{schema: "…"}` option.

Full suite **4488 tests, 0 failed**; conformance **563/563**.

## Related

- [EB08](EB08-compiled-object-literals-drop-xml-attributes.md) — the same family one field over:
  attributes rather than metadata, also silent, also in the shared compiler.
- [EB07](EB07-compiled-index-access-ignores-string-keys.md) — the previous largest class; its
  diagnosis also had to be corrected before it could be fixed.
- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
