# UTL-X 1.4.0 — Release Notes

**Released:** November 2026
**Previous release:** 1.3.0 (July 2026)

Three artefacts carry this version: the CLI (`utlx`), the engine (`utlxe`) and the IDE
daemon (`utlxd`).

1.4.0 is a correctness release. The headline feature is semantic validation — `%utlx 1.1`
and the `validate.*` namespace — but the changes most likely to affect an existing script
are the removals: three constructs that were documented, or accepted by the parser, and
never actually worked.

---

## Breaking changes

Read these first. Each one replaces a silent wrong answer with a loud one.

### `parent()` is removed

`parent()` read a `__parent` property that nothing in the codebase ever wrote, so it never
returned a parent node — it returned null, or threw. It was broken in three independent
ways: no writer, a mismatch between its implementation (which read `properties`) and its
own description (which claimed `metadata`), and a `metadata` map typed `Map<String, String>`
that cannot hold a node at all. Its only test hand-built the `__parent` property it was
looking for, so the test passed while the function could not work on real data.

The UDM has no parent back-references by design: a node does not know its container, which
is what lets the same subtree be shared and re-emitted without copying.

**Instead:** restructure explicitly with `nestBy()`, or carry the key you need down as you
descend. If you have a flat list of children holding a foreign key — order lines with an
`orderId`, say — `nestBy()` is the function that groups them under their parent.

### `..` (recursive descent) is now a parse error

`..` lexed, parsed, and then did nothing useful. It is now rejected at parse time with a
message naming the alternative, rather than being accepted and silently ignored:

```
Recursive descent ('..') is not supported in UTL-X.
```

The documentation described `..` in several places; those references are gone. See
[B49](../bugs-fixed/B49-documented-wildcard-and-descent-selectors-are-parse-errors.md).

### JSON refuses non-finite numbers

`NaN`, `Infinity` and `-Infinity` are not valid JSON. The serializer used to emit them
anyway, producing output that no conformant JSON parser will read back. It now fails with
an error naming the offending value.

This can only fire on a **computed** value — no document can introduce one, because JSON
rejects the token, and XML, CSV and YAML yield the string. A division or a `sqrt` of a
negative number is the usual source.

Three predicates let a script test before serializing: `isNaN(x)`, `isInfinite(x)`,
`isFinite(x)`. See
[B38](../bugs-fixed/B38-non-finite-numbers-serialise-as-invalid-json.md).

### `$input` is no longer aliased to the first input

When a header declared inputs under their own names, `$input` was silently bound to the
first one. A script could then read `$input` successfully while the input it actually
declared was empty — and the preview and the engine disagreed about the result.

`$input` now resolves only where it is unambiguous: a single input, not named something
else in the header. With multiple named inputs, use the names you declared. A header that
declares an input literally called `input` keeps `$input` meaning that input. See
[B24](../bugs-fixed/B24-input-silently-aliased-to-first-input.md).

---

## New features

- **`%utlx 1.1` — semantic validation.** A `validate.*` namespace for asserting things
  about data rather than transforming it, gated on the `%utlx 1.1` version declaration.
  [F21](../features/F21-utlx-1-1-semantic-validation.md)
  - Quantified rules over collections — `every` and `some`.
    [F24](../features/F24-quantified-validation-rules.md)
  - Declarative rule kinds, so a rule can be stated rather than coded.
    [F25](../features/F25-declarative-validation-rule-kinds.md)
  - Engine-side enforcement and observability, so a verdict survives every execution
    strategy. [EF23](../features/EF23-engine-validation-enforcement-and-observability.md)
- **Parser strictness profiles.** `strict`, `standard` and `lenient` policies, selectable
  per run with `--parse-profile`, enforced per reader. Garbage-tolerant parsing stays the
  default; `strict` is opt-in.
  [F23](../features/F23-parser-strictness-profiles.md)
- **Epoch timestamp conversion.** `toEpochMillis` and `toEpochMicros`.
  [F26](../features/F26-epoch-timestamp-conversion.md)
- **Shell ergonomics.** Raw output and shell-supplied values, for using `utlx` in a
  pipeline without quoting gymnastics.
  [F27](../features/F27-shell-ergonomics-for-inline-queries.md)

---

## Language and type-system fixes

- **Hyphens are valid in identifiers.** JSON, XML and YAML all permit a hyphen in a key or
  element name, so a transformation language that reads them has to as well. `$input.my-top.an-item`
  works. Where a hyphenated name is genuinely ambiguous with subtraction, the error now
  says so and suggests the bracket form.
  [B54](../bugs-fixed/B54-hyphens-in-identifiers-implementation-and-docs-disagree.md)
- **Reserved words can be read as property names.** `{ input: 1 }` was writable but
  `x.input` was a parse error — a document whose keys happen to be UTL-X keywords was
  unreadable.
  [B56](../bugs-fixed/B56-reserved-words-cannot-be-read-as-property-names.md)
- **The type checker no longer reports every stdlib function as undefined.** It resolved
  function calls against the variable environment only, so `--strict` flagged all 692
  stdlib functions. Locally bound names still shadow the stdlib, as before.
  [B52](../bugs-fixed/B52-type-checker-reports-every-stdlib-function-as-an-undefined-variable.md)
- **`Any` is assignable again.** `Any` was treated as assignable to nothing, so
  `--strict` rejected every script that touched untyped input — which is every script
  without a schema.
  [B53](../bugs-fixed/B53-any-is-assignable-to-nothing-so-strict-types-rejects-every-real-script.md)
- **Projection over a mixed sequence no longer throws.** A missing key was skipped but a
  non-object member threw, so one scalar in a list of objects aborted the transformation.
  [B57](../bugs-fixed/B57-projection-throws-on-a-non-object-member-but-skips-a-missing-key.md)
- **`-e` accepts compact expressions.** `.name` style shorthand needed surrounding
  whitespace, so compact jq-style one-liners failed. The CLI now expands the shorthand
  conservatively first and widens only if that does not parse, which keeps `a-b` and
  `a*b` meaning arithmetic.
  [CB04](../bugs-fixed/CB04-dot-shorthand-needs-whitespace-so-compact-jq-expressions-fail.md)

## Standard library fixes

- **`join` is the string join; `innerJoin` is the relational one.** The two had been
  conflated.
  [B50](../bugs-fixed/B50-join-is-the-string-join-innerjoin-is-relational.md)
- **`toString()` preserves decimal scale**, like every other numeric path.
  [B51](../bugs-fixed/B51-tostring-loses-decimal-scale-b35-incomplete.md)
- **41 documented examples called function names that do not exist.** Every
  `@UTLXFunction` example now names a real function.
  [B55](../bugs-fixed/B55-forty-one-documented-examples-call-function-names-that-do-not-exist.md)
- **New:** `isNaN`, `isInfinite`, `isFinite`.

---

## Upgrading

1. **Search your scripts for `parent(` and `..`** — both now fail loudly. `..` fails at
   parse time, so you will find out immediately; `parent()` is an unknown function.
2. **If you declare named inputs, check every `$input`.** A script that read `$input`
   while declaring `orders` and `customers` was reading the first input by accident; it
   now has to name one.
3. **If you compute ratios or roots, decide what should happen to a non-finite result**
   before it reaches a JSON serializer — guard with `isFinite()`, or substitute a default.
4. Nothing else requires action. The remaining changes make previously-failing scripts
   work, not the reverse.

## Verification

- 4867 JVM tests, 0 failures
- 578/578 conformance tests, on both the JVM and the GraalVM native binary
- The conformance corpus runs through all three execution strategies — TEMPLATE
  (interpreted), COPY and COMPILED — via `ConformanceStrategyParityTest` (411 cases)

For the release procedure itself, see [version-release-plan.md](version-release-plan.md).
