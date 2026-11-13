## Comparison: UTL-X vs jq

### Core Purpose

**UTL-X** is a format-agnostic functional transformation language that works with XML, JSON, CSV, YAML, and other formats. Write your transformation logic once, apply it to any supported format. 650+ built-in functions.

**jq** is a lightweight command-line JSON processor — like sed/awk/grep for JSON data. Focused exclusively on JSON manipulation.

> Figures here are measured, not quoted, and deliberately stated as a floor. The UTL-X stdlib
> grows every release; an exact count goes stale in a dozen documents at once, which is what
> happened to the "652 functions across 18 categories" that this page carried for seven
> months. For the current figure run `./utlx functions --format json`. jq:
> `jq -n 'builtins | length'` → 218, of which 189 are distinct names. Comparison run against
> **jq 1.7.1** on 2026-11-12. The same earlier revision put jq at "~50" builtins, which was
> badly wrong in the unflattering direction.

### Key Differences

| Feature | UTL-X | jq |
|---------|-------|-----|
| **Format Support** | XML, JSON, CSV, YAML, OData + 6 schema formats | JSON only |
| **Language** | Written in Kotlin (JVM or GraalVM native binary) | Written in C, single native binary |
| **License** | AGPL-3.0 (copyleft) / Commercial dual-license | MIT (permissive) |
| **Type System** | Static checking before the script runs — names, arity, argument types, operators | Dynamically typed, no static check |
| **Stdlib** | 650+ functions | 189 builtins (218 with arity variants) |
| **Inline expressions** | `utlx -e '.name'` (dot shorthand like jq) | `jq '.name'` |
| **Raw output** | `utlx -e '.name' -r` | `jq -r '.name'` |
| **Piping** | `cat data.xml \| utlx` (smart format flip) | `cat data.json \| jq '.'` |
| **Shell-supplied values** | `--arg name=value`, `--argjson name=<json>` | `--arg`, `--argjson` (same names) |
| **No input** | `-n` / `--null-input` | `-n` |
| **Exit status from the result** | `--exit-status` | `-e` |
| **Script files** | `.utlx` files with header + body | Inline expressions or `.jq` files |
| **Installation** | JVM (JDK 17+) or GraalVM native binary | Single binary, zero dependencies |

### CLI Comparison

**Identity / format conversion:**

```bash
# jq: pretty-print JSON
echo '{"a":1}' | jq .

# UTL-X: same, plus format conversion
echo '{"a":1}' | utlx              # JSON to XML (smart flip)
cat data.xml | utlx                # XML to JSON (smart flip)
cat data.xml | utlx --to yaml     # XML to YAML
cat data.csv | utlx                # CSV to JSON
```

**Field extraction:**

```bash
# jq
echo '{"user":{"name":"Alice"}}' | jq '.user.name'

# UTL-X (-e with dot shorthand — same syntax as jq)
echo '{"user":{"name":"Alice"}}' | utlx -e '.user.name'
```

**Raw output for shell scripts:**

```bash
# jq
NAME=$(echo '{"name":"Alice"}' | jq -r '.name')

# UTL-X
NAME=$(echo '{"name":"Alice"}' | utlx -e '.name' -r)
```

**Filtering:**

```bash
# jq
cat orders.json | jq '[.[] | select(.status == "active")]'

# UTL-X
cat orders.json | utlx -e '. |> filter(o => o.status == "active")'
```

**Aggregation:**

```bash
# jq
cat sales.json | jq '{ total: [.[].amount] | add, count: length }'

# UTL-X
cat sales.json | utlx -e '{ total: sum(. |> map(s => s.amount)), count: count(.) }'
```

**String manipulation:**

```bash
# jq
echo '{"name":"alice"}' | jq -r '.name | ascii_upcase'

# UTL-X
echo '{"name":"alice"}' | utlx -e 'upper(.name)' -r
```

**Format conversion (jq cannot do this):**

```bash
# XML to JSON
cat data.xml | utlx

# JSON to CSV
cat users.json | utlx -e '. |> map(u => {name: u.name, email: u.email})' --to csv

# YAML to JSON
cat config.yaml | utlx -e '.database.host' -r
```

### Shell ergonomics: the jq flag set

Added in [F27](../features/F27-shell-ergonomics-for-inline-queries.md). Each was checked
against jq 1.7.1 and produces the same answer:

```bash
# Pass a value in from the shell
jq   -r --arg min 10 '[.items[] | select(.p > ($min|tonumber)) | .n] | join(",")' j.json
utlx -e 'join(filter(.items, (i) -> i.p > toNumber($min)).*.n, ",")' --arg min=10 -i j.json -r
# both: y

# Structured value in
jq   -c --argjson o '{"k":7}' '.a + $o.k' j.json      # 8
utlx -e '.a + $cfg.k' --argjson cfg='{"k":7}' -i j.json   # 8

# No input at all
jq   -n '1+1'                 # 2
utlx -e '1+1' -n              # 2   (see the note below on flag order)

# Exit status from the result, for use in `if`
jq   -e '.enabled' cfg.json >/dev/null;              echo $?   # 0 true, 1 false
utlx -e '.enabled' --exit-status -i cfg.json >/dev/null; echo $?   # 0 true, 1 false
```

**`-n` cannot come first.** `utlx -e '1+1' -n` and `utlx transform -n -e '1+1'` both work,
but `utlx -n -e '1+1'` fails with `Unknown command: -n` — the CLI infers the `transform`
subcommand from a leading `-e`, and `-n` is not in that set. jq has no subcommands, so a
ported `jq -n …` needs reordering.

**One flag collision to know when porting a script:** in jq, `-e` means *exit status*. In
UTL-X, `-e` means `--expression`, and exit status is the long `--exit-status`. A jq script
with `-e` in it will not mean what it did.

### Numeric fidelity — where UTL-X is actually ahead

jq 1.7 preserves a number's original literal when it passes through untouched, so these
agree:

```bash
echo '{"id":9007199254740993}' | jq   -c '.id'    # 9007199254740993
echo '{"id":9007199254740993}' | utlx -e '.id'    # 9007199254740993
```

The difference appears the moment you compute with it. jq converts to IEEE double;
UTL-X keeps the value exact ([B35](../bugs-fixed/B35-double-based-numeric-model-corrupts-decimals-and-large-integers.md),
[B51](../bugs-fixed/B51-tostring-loses-decimal-scale-b35-incomplete.md)):

| expression | jq 1.7.1 | UTL-X |
|---|---|---|
| `.id + 1` where `id` is `9007199254740993` | `9007199254740992` | `9007199254740994` |
| `.price * 3` where `price` is `10.50` | `31.5` | `31.50` |

The first row is worth a second look: jq's answer to *n + 1* is **lower than n**. For money,
identifiers, or anything that round-trips, this is the difference that matters, and it is not
a flag — it is the number model.

### Style difference: UTL-X spaces its operators

This is the one habit to unlearn coming from jq, and it is a convention rather than a
shortcoming. **UTL-X is written with spaces around operators** — measured across the
conformance corpus, the documentation and every `.utlx` file in the repo, it is 2513 spaced
operators against a single tight one. The IDE's Function Builder inserts every operator
surrounded by spaces, and `docs/language-guide/syntax.md` carries `{let x=100,let
y=x*0.08,total:x+y}` explicitly labelled `// ❌ Bad`.

There is a language reason, not just a stylistic one: `-` is a legal **identifier**
character, so `$input.a-b` is the key `"a-b"`, not `a` minus `b`. On a document holding both,
`.a-b` returns `99` where `.a - .b` returns `6`. Spacing is what disambiguates, which is
exactly why the Function Builder enforces it.

The `-e` dot shorthand used to insist on it too, and compact jq spellings were a parse error.
Since [CB04](../bugs-fixed/CB04-dot-shorthand-needs-whitespace-so-compact-jq-expressions-fail.md)
they work — the expression is expanded conservatively first and only re-expanded with a wider
rule if that fails to parse, so nothing that already worked changed meaning:

```bash
utlx -e '{p:.a}'      # {"p":1}
utlx -e '[.a]'        # [1]
utlx -e '.a+.b'       # 3
utlx -e '.a==.b'      # false
utlx -e '-.a'         # -1
```

Two things to know when porting a jq line:

```bash
utlx -e '1 + .5'      # Error — a leading decimal point is not valid UTL-X; write 0.5
utlx -e '.a-b'        # the KEY "a-b", not a minus b — '-' is an identifier character
```

The first is reported with a hint telling you to write `0.5`; jq accepts both spellings. The
second is why `-` still wants spaces: `.a - .b` subtracts, `.a-b` reads a property. Every other
operator is fine tight or spaced.

### Where UTL-X wins

- **Format agnostic** — one transformation works with XML, JSON, CSV, YAML; jq is JSON-only
- **Zero-flag format conversion** — `cat data.xml | utlx` converts XML to JSON instantly; jq cannot read XML at all
- **jq-like expressions** — `utlx -e '.name' -r` uses the same dot syntax as jq, with `-r` for raw output
- **650+ functions** — string, array, math, date, XML, CSV, YAML, binary, encoding, financial, geospatial, security
- **Static checking before you run** — `utlx validate` catches a misspelled function, a wrong argument count, `"abc" - 1`, and indexing a scalar; jq has no equivalent, so a typo surfaces as `null` deep in a pipeline. See the caveat below: argument *types* currently over-report
- **Multi-input** — join data from multiple files/formats in one transformation
- **OData support** — parse and serialize OData JSON and EDMX metadata; jq has no OData awareness
- **Schema awareness** — XSD, JSON Schema, Avro, Protobuf, OData/EDMX, Table Schema

### What the static checking actually covers

`utlx validate` catches, before the transformation runs, things jq cannot catch at all:

```bash
utlx validate t.utlx --strict
✗ Undefined function: totallyMadeUp        # a misspelled function
✗ Undefined variable: nope
✗ upper expects 1 arguments, got 2         # wrong arity
✗ Argument type mismatch                   # upper(42), abs("x")
✗ Arithmetic operators require numeric operands   # "abc" - 1
```

jq has no equivalent: a typo there surfaces as `null` somewhere downstream, or as a runtime
error on the one input that reaches it.

**`--strict` is safe to put in CI**, as of
[B53](../bugs-fixed/B53-any-is-assignable-to-nothing-so-strict-types-rejects-every-real-script.md).
Until that landed, a value read from `$input` was typed `Any` and `Any` satisfied no declared
parameter type, so every one of the dozen functions with a signature reported a false mismatch
the moment its argument came from the document — `upper("a")` validated and `upper($input.s)`
did not, though both run.

**What it is not, yet:** thorough. Only about a dozen functions carry a type signature, so most
calls are checked for name and arity but not argument type; and `$input` is not typed from a
schema, so no property is verified against the document's real shape — `$input.noSuchField`
validates cleanly. Treat it as a strong lint rather than a type system.

### Where jq wins

- **Zero dependencies** — single native binary, instant install (UTL-X also offers a GraalVM native binary, but jq's is smaller)
- **Mature ecosystem** — battle-tested, widely adopted, extensive documentation
- **Concise syntax** — shorter expressions for JSON-only work
- **Permissive license** — MIT, no copyleft restrictions
- **Performance** — native C, no JVM startup overhead (though UTL-X GraalVM native binary closes this gap)
- **Shell integration** — ubiquitous in scripts, CI/CD pipelines

### Use Case Recommendations

**Choose UTL-X if you:**

- Work with multiple data formats (XML, JSON, CSV, YAML)
- Need format conversion without writing transformation logic
- Want a script checked for typos and arity before it runs
- Are building ETL pipelines or data migration tools
- Need rich stdlib (650+ functions)

**Choose jq if you:**

- Only work with JSON
- Need zero-dependency single-binary installation
- Want maximum shell scripting integration
- Need a permissive (MIT) license
- Need the smallest possible footprint

### Bottom Line

jq is the standard for JSON processing — fast, mature, universal. UTL-X targets a different
space: format-agnostic transformation with a rich standard library. For pure JSON work in
shell scripts, jq is hard to beat, and this page does not claim otherwise.

What has genuinely changed: with `-e`, `-r`, `--arg`, `--argjson`, `-n` and `--exit-status`,
a UTL-X one-liner now fits a shell script the way a jq one-liner does, and it reads XML, CSV
and YAML on the way in. Two honest qualifications: compact expressions still need whitespace
([CB04](../bugs-fixed/CB04-dot-shorthand-needs-whitespace-so-compact-jq-expressions-fail.md)),
and `--strict` type enforcement over-reports on anything derived from input
([B53](../bugs-fixed/B53-any-is-assignable-to-nothing-so-strict-types-rejects-every-real-script.md)),
so use `utlx validate` for names and arity rather than as a type gate.

Where UTL-X is unambiguously ahead is not the syntax — it is the number model (exact
arithmetic, above) and the fact that the input need not be JSON at all.
