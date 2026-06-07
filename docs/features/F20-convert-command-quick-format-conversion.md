# F20: `convert` Command — Quick, Script-Free Format Conversion

**Status:** Implemented (development)
**Priority:** Low (the conversion capability already shipped; this is a discoverable front door)
**Created:** June 2026
**Reported:** GitHub issue #5 (@skin27 / Raymond Meester) — "[FEATURE] Add convert parameter"
**Related:** identity mode / smart flip (CLI `TransformCommand`), B26 (CLI error handling)

---

## Classification: Feature (ergonomic surface over existing capability)

The *conversion engine* this feature exposes already shipped — UTL-X has long supported script-free
format conversion via **identity mode** (`cat data.xml | utlx`, `--from`/`--to`). What was missing is
a **named, discoverable verb** with **file-based I/O flags**, which is exactly what issue #5 asked for:

```
utlx convert --input some.xml --output output.json
```

`convert` is a thin alias that routes to identity mode — **no new conversion logic**. It makes the
existing capability obvious in `--help` and accepts `--input`/`--output` files directly (previously
those flags only applied to `transform`, so file→file conversion needed shell redirection).

## Background

Issue #5 (an "idea, not a hard request") observed that besides fine-grained datamapping (`transform`),
users often just want a quick, generic conversion between data formats (xml/json/yaml/csv), referencing
tools like docconverter, and suggested a `convert` verb plus a broader "swiss-army" set
(validate / convert / prettify).

UTL-X already covered the core:

| Form | Already worked before F20 |
|---|---|
| `cat data.xml \| utlx` | smart flip → JSON |
| `cat data.json \| utlx` | smart flip → XML |
| `cat data.csv \| utlx --to yaml` | explicit target |
| `utlx < some.xml > out.json` | file→file via shell redirection |
| `utlx -e '.' --to yaml` | identity expression |

The only gaps were **discoverability** (no named verb) and **ergonomics** (`--input/--output` files not
wired into identity mode at the top level — `utlx --input x --output y` returned *"Unknown command:
--input"*). F20 closes both.

## What it does

Adds a `convert` (alias `conv`) command:

```
utlx convert [--input FILE | -i FILE] [--output FILE | -o FILE] [--from FORMAT] [--to FORMAT]
```

- **Input:** `--input/-i FILE`, or stdin when omitted.
- **Output:** `--output/-o FILE`, or stdout when omitted.
- **Target format:** `--to`; if omitted, the **smart flip** default applies (XML↔JSON, everything else
  → JSON). `--from` overrides input auto-detection.

### Examples (verified against the built CLI)

```
$ utlx convert --input some.xml --output out.json     # file → file, smart flip to JSON
$ utlx convert -i data.csv --from csv --to yaml -o out.yaml
$ echo '<a><b>1</b></a>' | utlx convert --to yaml      # stdin → stdout
$ echo '{"id":7}' | utlx conv                          # JSON → XML smart flip (alias)
```

## Implementation

A single routing line in `Main.kt`:

```kotlin
"convert", "conv" -> TransformCommand.execute(commandArgs, identityMode = true)
```

Identity mode already (a) reads `--input` files or stdin, (b) auto-detects / smart-flips the format,
and (c) writes to `--output` or stdout — so `convert` inherits all of it. The B26 no-input guard
(fail fast instead of blocking on an interactive TTY) applies here too; its message was made
**verb-neutral** so it reads correctly for both `transform` and `convert`:
`No input provided. Pass an input file (-i/--input FILE) or pipe data via stdin.`

Help/usage updated in `Main.kt` (command list + examples).

## Tests

`modules/cli/src/test/kotlin/org/apache/utlx/cli/ConvertCommandTest.kt` — 4 subprocess integration
cases: the issue's exact `convert --input X --output Y`, explicit `--to`/`-o` file output, stdin →
stdout, and the `conv` alias smart flip. All green; full `:modules:cli:test` passes.

## Out of scope / future

Issue #5 also floated a broader swiss-army tool. Partially covered already (`validate`, `lint` —
though they target UTL-X scripts, not arbitrary data docs; prettify is implicit via same-format
re-emit, e.g. `--from json --to json`). Not in F20:

- a convert-specific `--help` blurb (currently `convert --help` prints `transform`'s usage),
- first-class data-document schema validation / prettify verbs.
