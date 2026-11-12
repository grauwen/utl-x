# AGENTS.md — working notes for AI coding agents

Facts an agent needs before touching this repository. Written for any agent
(Claude Code, Codex, Cursor, Copilot); `CLAUDE.md` imports this file.

**Keep this file correct or delete it.** The document it replaced claimed 456 tests
in one place and 465 in another while the real figure was 571, described a flag as
useful that does nothing, and omitted two modules. Stale instructions are worse
than no instructions, because an agent will act on them.

Verify counts before quoting them:
`python3 utlx/runners/cli-runner/simple-runner.py | tail -3`, `./utlx functions --format json`.

---

## What this is

UTL-X is a format-agnostic functional transformation language in Kotlin. One
transformation definition moves data between XML, JSON, CSV, YAML, Avro, Protobuf,
OData and schema formats. A **Universal Data Model (UDM)** sits at the centre: every
parser produces UDM, every serializer consumes it, and transformation logic never
sees the source format.

Dual-licensed **AGPL-3.0 / commercial**, owned by GLOMIDCO BV. Contributions are
AGPL and need a CLA — see `CONTRIBUTING.md`.

## Build and test

```bash
./gradlew build                      # everything
./gradlew :modules:core:test         # one module
./gradlew :modules:core:test --tests "*B48*"
./gradlew :modules:cli:jar           # the jar the ./utlx wrapper runs
./gradlew :modules:cli:nativeCompile # GraalVM binary
```

The repo-root `./utlx` wrapper runs `modules/cli/build/libs/cli-<version>.jar`, so a
code change is invisible to it until you rebuild that jar.

### The conformance suite — and the trap in it

```bash
cd conformance-suite && python3 utlx/runners/cli-runner/simple-runner.py
```

**571 tests** (2026-11-12). Must run from `conformance-suite/`; the runner uses
relative paths. Append a category to narrow: `stdlib/array`, `formats/xml`,
`language/selectors`.

`--show-failures` does **not** run anything — it replays the previous run's results.

**This suite only exercises the INTERPRETED path.** It shells out to the CLI, which
runs `Interpreter`, and the transform command has no strategy flag. UTL-X has three
execution strategies — TEMPLATE (the interpreter), COPY, and COMPILED (the
`ASTCompiler`, and what the UTLXe engine runs by default). A green 571/571 says
nothing about two of them:

```bash
./gradlew :modules:engine:test       # ~997 tests, incl. ConformanceStrategyParityTest (404)
```

**Four bugs have come from this gap** — B34 (`&&`/`||`), B35 (exact numerics),
EB03 (`"5" == 5`), B48 (path steps projecting over a sequence) — each fixed in the
interpreter while the compiled path silently diverged. B48 is the clearest warning:
the interpreter-only fix passed core, stdlib, CLI *and* all 571 conformance tests,
and still returned `null` on COPY and COMPILED where TEMPLATE returned the right
answer. It turned a loud consistent error into a silent strategy-dependent one.

So: **any change under `modules/core` touching member access, comparison, numerics
or evaluation order is not verified until `:modules:engine:test` passes.** Do not
reason about whether the compiled path is affected — run it.

The good news: `ConformanceStrategyParityTest` auto-discovers the conformance corpus
and runs every fixture through all three strategies. Adding a fixture buys
cross-strategy coverage for free; no hand-written parity test needed.

### Writing conformance fixtures

Numeric scale is lost on **both** sides if you are careless:

- **Output:** `format: json` is compared by *parsed value*, so `100.10` vs `100.1` is
  invisible. To pin scale, trailing zeros or large doubles, output **XML or CSV**.
- **Input:** inline YAML `data: {"price": 10.50}` is parsed into a float by the
  runner's YAML loader — the trailing zero is gone before UTL-X sees it. Use a block
  scalar:

  ```yaml
  input:
    format: json
    data: |
      {"items": [{"price": 10.50}]}
  ```

## Layout

```
modules/core        lexer, parser, interpreter, UDM, semantic validation
modules/cli         the `utlx` binary
modules/analysis    type inference, schema analysis
modules/engine      UTLXe — execution strategies, the production runtime
modules/bundle      file-level bundle management  (engine DEPENDS on this)
modules/daemon      utlxd — LSP + REST
formats/            xml json csv yaml xsd jsch avro protobuf odata osch tsch
stdlib/             ~690 functions (703 entries, some aliased)
schema/             USDL, schema serializers
wrappers/           .NET and Go clients driving the engine over stdio protobuf
conformance-suite/  the Python-driven corpus
```

`modules:engine` declares `implementation(project(":modules:bundle"))`. Dropping
bundle breaks the engine build — this is easy to miss when deciding what ships.

Key files:

- `modules/core/.../udm/udm_core.kt` — the Universal Data Model
- `modules/core/.../parser/parser_impl.kt` — parser
- `modules/core/.../interpreter/interpreter.kt` — interpreter
- `modules/engine/.../strategy/compiled/ASTCompiler.kt` + `RuntimeOps.kt` — the
  compiled path. **Semantics live in two places**; a core fix usually needs both.

## Documentation: `docs/` vs `devdocs/`

A hard boundary, enforced in CI by `scripts/check-docs-boundary.py`
(`.github/workflows/docs-boundary.yml`):

- **`docs/`** ships to `main`. Must be self-contained.
- **`devdocs/`** is development-only and never reaches `main`.

**No file under `docs/` may link into `devdocs/`** — such a link is broken on `main`,
silently. Main accumulated 22 broken links before this rule existed. The linter also
fails on broken-link count rising above a ratcheting baseline, on de-link markers
stranded inside `devdocs/`, and on stubs in `docs/` whose target is back in `docs/`.

Bug and feature reports live in `docs/bugs-fixed/` and `docs/features/` with prefixes:
**B** core/formats/stdlib · **EB** engine · **CB** cli · **VB** `validate.*` ·
**WB** wrappers · **IB** IDE/daemon · and **F**/**EF**/**IF** for features.
IB and IF stay in `devdocs/` — the IDE and daemon do not ship on `main`.

## Language gotchas that cost time

**Comments are C-style.** `//` and `/* */`. `#` is *not* a comment and produces a
parse error; it appears only in the header directive `%utlx 1.0`.

**Object literals as call arguments need parentheses**, or `{` is parsed as a block:

```utlx
replace(value, ({"\n": "", "\t": ""}))   // correct
replace(value, {"\n": "", "\t": ""})     // parse error
```

**User-defined functions must be PascalCase.** Lowercase/camelCase is reserved for
the stdlib, so `function calculateTax(...)` is rejected; write `CalculateTax`.

**Single input is `$input`; named inputs are `$name`:**

```utlx
%utlx 1.0
input: orders xml, customers json
output json
---
{ a: $orders.Order, b: $customers.list }
```

**One transformation, one output** — by design. Use orchestration for fan-out.

## Conventions

- Tests: JUnit 5 + kotlin.test, `*Test.kt`. A bug fix gets `B<nn><Name>Test.kt`
  with a comment explaining the root cause, not just the symptom — and a vacuity
  guard where a fixture could silently stop proving anything.
- **Never hardcode a port in a test.** Use `ServerSocket(0)` for a free one.
- Match the surrounding code's naming, comment density and idiom.
- Document a fix in `docs/bugs-fixed/` as well as fixing it.

## Environment notes

`grep` may be ugrep with `-I`: a file containing one NUL byte, or a log full of ANSI
colour codes, returns **zero matches silently** — indistinguishable from "no
matches". When an empty result looks suspicious, confirm with `command grep -c`,
`file`, or Python before concluding the symbol does not exist. This has cost real
time twice: a NUL in `xml_parser.kt`, and reading Gradle test logs.

Personal workflow preferences — whether an agent may commit, build, or push —
belong in each contributor's own config (`~/.claude/CLAUDE.md`, a gitignored
`CLAUDE.local.md`, or the equivalent), **not in this file**. They differ per person.

## Further reading

- `docs/language-guide/` — current syntax
- `docs/bugs-fixed/README.md` — the prefix table and the bug record
- `devdocs/proposals/utlx-original-design-proposal-2025.md` — the founding proposal;
  historical, not a guide to present behaviour
