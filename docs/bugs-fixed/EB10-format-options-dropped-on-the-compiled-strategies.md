# EB10: a declared format's options are dropped on the compiled strategies

> **EB = engine bug** (`modules/engine` — the execution strategies and the runtime they
> call), the family of EB01–EB09. This is **EB10**.

**Status:** **FIXED.**
**Priority:** High. `input csv {delimiter: "|"}` was parsed as comma-separated on the engine's
default strategy, so every field came back null.
**Component:** `:engine` — `CompiledStrategy` and `CopyStrategy`, five `parseInputPublic` call sites.

> **One-line:** both compiled strategies read the declared format's *type* and discarded its
> *options*, so the parser ran with defaults.

## Root cause

`FormatSpec` carries three things:

```kotlin
data class FormatSpec(
    val type: FormatType,
    val dialect: Dialect? = null,
    val options: Map<String, Any> = emptyMap(),
    ...
)
```

and `parseInputPublic` accepts an options map. Both strategies took only the type name and let the
options default away:

```kotlin
val declaredFormat = declaredInputs.firstOrNull()?.second?.type?.name?.lowercase()
parseInputPublic(input, declaredFormat ?: "json", parseProfile = parseProfile)
//                                                ↑ options = emptyMap()
```

`TemplateStrategy` was unaffected: it hands the source and the content to `transform()`, which
re-reads the header itself. Only the two strategies that parse input on their own behalf needed
telling, and neither did.

Five sites: single and multi input on COMPILED; single, multi and **schema** parsing on COPY.

## Why the symptom pointed somewhere else

With `{headers: true, delimiter: "|"}` dropped, a pipe-delimited file parses as a single column
whose header is the whole first line. Every `row.order_id` is then null and `group.key` is `""`:

```
TEMPLATE   { "orderId": "ORD-001", "customer": { "name": "Alice Johnson", ... } }
COMPILED   { "orderId": "",        "customer": { "name": null, ... } }
```

Three fixtures diverged this way, and all three used `mapGroups`, `let` bindings inside a lambda's
object literal, and `|>` chains — so that is where I looked. **Nine minimal reproductions of those
constructs all agreed.** The constructs were never the problem; the delimiter was. What gave it away
was `orderId: ""` — an empty string rather than a null, which is what a missing *column* looks like
rather than a missing value.

That is the third diagnosis on this harness that the error's shape pointed away from, after
[EB07](EB07-compiled-index-access-ignores-string-keys.md) and
[EB08](EB08-compiled-object-literals-drop-xml-attributes.md). The lesson is consistent: when the
minimal reproduction of the suspected construct passes, the suspicion is wrong — look at what else
the failing fixture does differently, not harder at the construct.

## Effect

| | before | after |
|---|---|---|
| known divergences | 17 | **11** |
| `BOTH_OUTPUT` | 9 | 6 |
| `RUNS_ONLY_ON_TEMPLATE` | 5 | 2 |

Six fixtures: the three `csv_orders_grouping_{pipe,semicolon,tab}`, both `csv_regional_*_input`, and
`formats/xsd/transformation/map_to_database_schema` — the last diverging xsd fixture.

## Tests

`EB10FormatOptionsTest` (`:engine`) drives the three strategies over CSV with a non-default
delimiter, asserting they agree **and** that the parsed value is present — three nulls agree
perfectly, which is the trap every defect in this family sets.

Cases: pipe, semicolon and tab delimiters; the default comma still working (threading options
through must not disturb the common case); `headers: false` changing the row count, which is a
*structural* option rather than a delimiter; and a format with no options at all.

Revert-verified independently: removing the options from COMPILED fails 4 of the 6, and removing
them from COPY fails 4 — different strategies, same four cases, because each is checked on all three.

## Related

- [EB09](EB09-metadata-access-lost-on-both-compiled-strategies.md) — the previous fix, also two
  independent sites losing the same thing silently.
- [EB07](EB07-compiled-index-access-ignores-string-keys.md),
  [EB08](EB08-compiled-object-literals-drop-xml-attributes.md) — the other two mis-diagnoses.
- [conformance-across-execution-strategies](../architecture/conformance-across-execution-strategies.md)
