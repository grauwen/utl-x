# Conformance across execution strategies

**Status:** implemented — `ConformanceStrategyParityTest` in `:engine`.
**Closes the gap recorded in** [EB03](../bugs-fixed/EB03-compiled-comparison-coerces-types-so-strategies-disagree.md)
§"The structural gap this exposed".

> **One-line:** the conformance corpus now runs on every execution strategy, not just the
> interpreted one, and the strategies' disagreements are an asserted inventory rather than an
> unknown.

## The gap

`ch06` states the contract: *"A transformation that works in one executable works identically in all
three... the conformance suite (500+ tests) validates behavior across executables."*

The second clause was not true. The Python conformance runner shells out to the `utlx` CLI, which
executes through `Interpreter`, and the transform command has no strategy option. So the corpus
exercised **one** execution path:

| path | engine | was it covered? |
|---|---|---|
| `utlx` CLI | `ExtendedInterpreter` → `Interpreter` | ✅ this *is* the suite |
| engine **TEMPLATE** | `Interpreter` | ✅ same code |
| engine **COPY** | `ASTCompiler` + interpreter fallback | ❌ never |
| engine **COMPILED** | `ASTCompiler` + interpreter fallback | ❌ never — **and it is the engine default** |

Three bugs came out of that, each fixed in the interpreter while the compiled path silently
diverged, and each found by probing rather than by a suite:

- **B34** — `&&`/`||` returned the deciding operand compiled, a coerced boolean interpreted.
- **B35** — literals and arithmetic made exact in the interpreter; COMPILED stayed lossy.
- **EB03** — `"5" == 5` true compiled, false interpreted; seven comparisons disagreed.

## This is not the conformance suite

`ch39` asks "Why Python, Not Kotlin?" and answers it well — the suite tests the **binary** a user
installs, has **no internal access**, is **language-independent** (a future WASM or Rust UTL-X could
run it), and is **extended by adding a YAML file**. All four hold, and none of them is weakened here.

What this test does is reuse the **corpus**. The YAML fixtures are the language-independent asset;
runners are implementation details, and there can be more than one:

| | Python suite | this test |
|---|---|---|
| asks | is the output **correct**? | do the strategies **agree**? |
| sees | the installed binary | three in-process strategies |
| reads `expected:` | yes | never |
| portable to another UTL-X implementation | yes | no |

It sits in `ch39`'s existing JVM layers and reads the Layer-5 corpus. It does not replace Layer 5
and the 556 figure still means what it meant.

Note that `ch39`'s second reason is precisely *why* a black-box runner cannot do this job: the three
strategies **are** internal state as far as the binary is concerned. `utlx transform` exposes no
strategy selector, so nothing driving the CLI can reach COPY or COMPILED.

### The Python route that does exist, and why it is a follow-up rather than this

`utlxe` exposes HTTP, gRPC and stdio-proto, and a registered transformation carries its own
`strategy` (`AdminEndpoint` both reports and sets it). So a Python suite *could* deploy each fixture
three times and compare — genuinely black-box, and keeping the language-independence property.

It is worth doing, for a reason this test does nothing about: it would also cover `ch39`'s
**transport parity** axis, the last claimed-but-untested one. But it cannot replace this test:

- **It cannot produce the number that makes the result meaningful.** "313 of 354 genuinely compiled"
  requires asking `ASTCompiler.compile` in-process. Over a transport, a compiled run and a silent
  interpreter fallback are indistinguishable — so a green parity run would carry no evidence that
  the compiled path was exercised at all. That is the exact hazard being guarded against.
- It needs a running engine per run: process lifecycle and port management (the project's own rule
  is `ServerSocket(0)`, never a literal), and it is slower and more fragile.
- It does not gate the Gradle build.

The two answer different questions and both are worth having.

## Why this, and not a `--strategy` flag on the CLI

A CLI flag looked like the cheap option and is not: `modules/cli` does **not** depend on
`modules:engine`. Adding it would make the open-source CLI bundle the engine, which is a packaging
and licensing decision about a separately distributed product rather than a test-coverage one.

Driving the strategies from inside `:engine` needs no new dependency at all — the strategies live
there, `StrategyParityTest` already constructs all three directly, and `:engine` already has
`jackson-dataformat-yaml` on the classpath for its own config loading.

## How it works

**It is a differential test, not an expectation test.** It never reads the fixtures' `expected:`
blocks. Whether output is *correct* is the Python suite's job and is already covered; the question
here is only whether the strategies **agree with each other**. Three consequences:

- the assertion is a plain string comparison, with no format-aware logic to reimplement;
- it is immune to the trap that a `format: json` fixture is compared by *parsed value*, where
  `100.10` and `100.1` are indistinguishable — the defect that B35's fixtures could not detect;
- a fixture's correctness and the strategies' agreement can fail independently, which is right,
  because they are different properties.

From each fixture it takes two fields — the `transformation:` block and `input.data` — and runs
TEMPLATE, COPY and COMPILED over them. Output format comes from the `%utlx` header, so nothing else
is needed. `data` appears in the corpus both as a `|` string block and as inline YAML, and both are
handled.

**Agreement on failure counts too.** If one strategy throws and another succeeds, that is a
divergence; only unanimous failure is consistent.

**No `inputs` is passed in `TransformConfig`,** deliberately. Both strategies prefer
`transformConfig.inputs.first().name` over the name declared in the header, so supplying one would
override whatever the fixture declares.

### The compiled-coverage floor is not optional

`CompiledStrategy` and `CopyStrategy` both **fall back to the interpreter without complaint** when
`ASTCompiler.compile` returns null. A parity run can therefore pass while measuring the interpreter
three times over and proving nothing — the hazard `B34CompiledShortCircuitTest` records in its own
header.

So the test also counts how many fixtures genuinely reach bytecode, asked of `ASTCompiler` directly
rather than inferred from logs, and asserts a floor. Narrowing `isCompilable` then shows up as a
failure instead of as silently thinner coverage.

### The baseline is locked in both directions

Retrofitting a differential test onto an existing codebase surfaces existing divergences. Those are
recorded in `KNOWN_DIVERGENCES` with a classified reason, and the list is asserted **both ways**:

- a fixture that starts diverging fails — the point of the test;
- a listed fixture that *stops* diverging also fails, with "delete the entry".

Without the second direction the list rots into a blanket exemption and the next real divergence
hides behind it.

## Measured results

```
fixtures driven       : 357
skipped               :  43
    multi-input                     39
    non-deterministic                4
known divergences     :   5
    COMPILED_UNDEFINED_FUNCTION      3
    BOTH_OUTPUT                      1
    RUNS_ONLY_ON_TEMPLATE            1
genuinely COMPILED    : 325   (91% of those driven)
fell back to interp.  :  32
```

The first measurement was 101 divergences and 313 compiled. The difference is
[EB04](../bugs-fixed/EB04-nested-lambdas-emit-duplicate-method-names.md), the first defect this
harness found on its own: a lambda nested in another lambda emitted a duplicate method name, the
class was rejected, and the compiled strategy fell back to the interpreter — silently, with correct
output. Eight fixtures were affected. Fixing it delisted three, and **exposed two further defects
that had been masked** by the compilation failure, which is why `COPY_OUTPUT` and the invokeinterface
count went up while the total went down.

354 tests, **0.7 seconds** — the whole corpus on three strategies costs nothing, so there is no
reason to exclude it from the normal loop.

### A fixture that reads the clock cannot be compared this way

Three entries in the baseline turned out not to be divergences at all. A differential test runs each
fixture **once per strategy**, so a transformation calling `now()` differs from itself:

```
stdlib/datetime/now_basic            now()
examples/real-world/sap_integration  now()
auto-captured/.../sum_auto_a4eced76  now()
```

Worse, it made the test **flaky in the other direction**: had two executions landed in the same
millisecond the outputs would have matched, and the baseline check would then have failed with
"now agree — delete the entry".

These are now skipped with a named reason, detected by what the transformation *calls* rather than
by its name — `now`, `random`, `currentDate`, `currentTime`, taken from the stdlib registry. Name
matching, which is what the Python runner uses (`validate_dynamic_output` looks for `now`,
`parsedate`, `timestamp` in the test name), would have missed `sap_integration` entirely.

The Python suite can do better than skipping, because it checks a timestamp is merely *in range*
against a recorded expectation. A strategy comparison has no expectation to range-check against, so
skipping is the honest option, and the count is reported rather than hidden.

That is the second measurement flaw this harness has had — the first being the masking described
above. Both produced *confident* numbers, which is the part worth remembering: a differential test
can be wrong about what it is measuring while looking perfectly healthy.

### Two vacuity guards

A differential test over zero fixtures passes while proving nothing, so neither the corpus nor the
compiled path is taken on trust:

- the loader fails if fewer than 355 fixtures are loaded, rather than relying on JUnit's "no
  arguments" default — it locates the corpus by walking up from the working directory, so a moved or
  unchecked-out corpus has to be an error;
- the compiled-coverage floor fails if fewer than 325 of the driven fixtures reach bytecode
  (it was 328 before the non-deterministic ones stopped being driven — a smaller
  denominator, not thinner coverage).

Both were verified by breaking them deliberately.

### Fixtures are keyed by path, not name

Three fixtures share a `name:` with another file — `length_basic` (`stdlib/array` and
`stdlib/string`), `toString_basic` (`stdlib/string` and `stdlib/type`) and `transform_auto_43d9da56`
(`auto-captured/xml-to-json` and `auto-captured/xml-transform`). Keying the baseline or the tallies
by name silently conflated them, which is how the first measurement reported 351 driven against 354
loaded. Minor corpus hygiene, worth fixing at some point; keyed by path it does not matter.

### Progress

| | first run | now |
|---|---|---|
| known divergences | 101 | **5** |
| `COMPILED_OUTPUT` | 0 | **0** |
| fixtures driven | 354 | **361** |

Eleven defects found and fixed by this harness so far — [EB04](../bugs-fixed/EB04-nested-lambdas-emit-duplicate-method-names.md),
[EB05](../bugs-fixed/EB05-lambda-bodies-cannot-reach-a-named-input.md),
[CB01](../bugs-fixed/CB01-auto-capture-emits-unparseable-yaml.md) (with
[CB02](../bugs-fixed/CB02-capture-writes-output-text-where-the-runner-compares-a-value.md) and
[CB03](../bugs-fixed/CB03-capture-yaml-writer-loses-data-in-three-ways.md)),
[EB06](../bugs-fixed/EB06-compiled-lambdas-cannot-see-enclosing-bindings.md) and
[EB07](../bugs-fixed/EB07-compiled-index-access-ignores-string-keys.md),
[EB08](../bugs-fixed/EB08-compiled-object-literals-drop-xml-attributes.md) and
[EB09](../bugs-fixed/EB09-metadata-access-lost-on-both-compiled-strategies.md) and
[EB10](../bugs-fixed/EB10-format-options-dropped-on-the-compiled-strategies.md) and
[EB11](../bugs-fixed/EB11-spread-does-not-take-effect-where-it-is-written.md),
[EB12](../bugs-fixed/EB12-compiled-try-catch-emits-unverifiable-bytecode.md) and
[EB13](../bugs-fixed/EB13-compiled-arithmetic-turns-a-non-numeric-operand-into-zero.md) — plus three
diagnoses corrected and **two** flaws found in the harness itself.
Each fix tended to reveal what it had been masking, which is why the total moved in steps rather
than smoothly.

### Correction: the dominant class is the shared compiler, not COPY

This section previously reported that **"COMPILED never produced different output"** and that COPY
was the strategy producing different answers. **Both claims were artifacts of the harness**, not
findings: `describeDivergence` compared COPY first and returned on the first difference, so on any
fixture where both compiled strategies were wrong the COPY entry masked the COMPILED one.

It now reports every axis and labels the shared case `BOTH_OUTPUT`:

| | measured one axis | measuring both |
|---|---|---|
| `COPY_OUTPUT` | 39 | **1** |
| `BOTH_OUTPUT` | — | **38** |
| `COMPILED_OUTPUT` | 0 | 0 |

That is better news than the original reading: the dominant class is the **shared `ASTCompiler`**,
where one fix moves many fixtures — [EB08](../bugs-fixed/EB08-compiled-object-literals-drop-xml-attributes.md)
took 12 of them immediately. `COMPILED_OUTPUT` being empty is still true and still meaningful, but
it means "COMPILED alone", not "COMPILED is never wrong".

The lesson is specific: a differential test must report every axis it compares, or it quietly
becomes a single-axis test — and a plausible class name (`COPY_OUTPUT`) will carry the wrong
diagnosis for as long as nobody checks it.

## The inventory this produced

Each class below is a real defect or gap. None has a bug number yet; they are listed in the order I
would assign them.

**1. ~~COPY emits XML attributes as child elements.~~** **FIXED —
[EB08](../bugs-fixed/EB08-compiled-object-literals-drop-xml-attributes.md)**, and it was never COPY's
own defect: `ASTCompiler` ignored `Property.isAttribute`, so **both** compiled strategies emitted an
attribute as a child element. 12 fixtures. The original note follows:

```
TEMPLATE   <Order id="ORD-123"><Customer>John</Customer></Order>
COPY       <Order><id>ORD-123</id><Customer>John</Customer></Order>
```

The fixtures' own expectations use attributes, so TEMPLATE is right and COPY is wrong. This matters
because `AUTO` selects COPY whenever a schema is present, so it is reachable without anyone asking
for it.

**2. ~~COPY and COMPILED cannot run schema-format transformations (37 fixtures).~~** **FIXED —
[EB07](../bugs-fixed/EB07-compiled-index-access-ignores-string-keys.md), and the diagnosis above was
wrong.** The input UDM was fine. Those transformations address it as `$input["%types"]` — a
*string-keyed subscript* — and the compiled path called `asNumber()` on the key, so a string became
`0`, the subscript returned null, and the failure surfaced one call later as "requires an object as
first argument, but got null". The symptom pointed at parsing; the cause was the subscript. Fixing it
moved **46 fixtures at once**, which no work on `parseInputPublic` would have done.

A plausible explanation that fits the error text is not a diagnosis — which is the whole reason this
harness reports a tally rather than a verdict.

**3. ~~`ASTCompiler` emits duplicate lambda method names (8 fixtures).~~** **FIXED —
[EB04](../bugs-fixed/EB04-nested-lambdas-emit-duplicate-method-names.md).** The lambda-name counter
lived on `CompileContext`, and a fresh context is built for each lambda body, so nesting restarted
it at zero. Was the cheapest of the five, as expected.

**4. ~~`ASTCompiler` emits invokeinterface against a UDM concrete class.~~** **FIXED —
[EB05](../bugs-fixed/EB05-lambda-bodies-cannot-reach-a-named-input.md).** `compileIdentifier` loaded
the named-input map from local slot 1, which is the map in `execute` but the first *lambda
parameter* in a lambda — so a lambda referencing an input called `Map.get` on a `UDM`. The lambda
method now takes the map as a second parameter, putting the inputs at slot 1 in both shapes.

**4b. ~~COPY/COMPILED cannot resolve an out-of-scope name (4 fixtures).~~** Split in two once
examined properly:

- a `let` from an enclosing scope was a **closure-capture** defect that threw at runtime —
  **FIXED, [EB06](../bugs-fixed/EB06-compiled-lambdas-cannot-see-enclosing-bindings.md)**. My earlier
  recommendation to have `isCompilable` reject it would have thrown away a capability that took
  about an hour to add.
- a **user-defined function** genuinely is not visible to the compiled path, because `compile()`
  compiles `program.body` and nothing else. 3 fixtures, now `COMPILED_UNDEFINED_FUNCTION`.

**5. ~~Seven fixtures are malformed YAML and have never run — by either suite.~~** **FIXED —
[CB01](../bugs-fixed/CB01-auto-capture-emits-unparseable-yaml.md).** The capture writer escaped quotes
but not backslashes. Running them for the first time found three real problems, including a
generator/runner disagreement about what `expected.data` means for JSON. Original note follows. All in
`auto-captured/`, so auto-capture emitted invalid YAML. The Python runner's `load_test_case` catches
the parse error, prints `Error loading …` and returns `None`, so they are skipped: they are not in
the 556 count and do not fail the run. The error lines scroll past in a 556-test run. Fixing the
capture output, or the seven files, would be a small change with a disproportionate payoff — these
are the only fixtures in the corpus that assert nothing at all.

## Scope and what remains

- **39 multi-input fixtures are skipped.** `ExecutionStrategy.execute(input: String)` takes a single
  payload; multi-input goes through an envelope-splitting path that has not been traced. They are
  named with a reason rather than quietly absent.
- **Transport parity is still unaddressed.** `ch39` lists it alongside strategy parity (HTTP vs gRPC
  vs stdio-proto), and no test class appears to cover it. Same bug class as EB03: a divergence
  between two paths that are claimed to be equivalent.
- The 556 figure quoted for a release still describes the interpreted path. This test adds the
  strategy axis; it does not change what the Python suite measures.
