# UTL-X CLI Ergonomics Improvements

## Motivation: jq vs utlx comparison

**jq** is the de-facto standard for command-line JSON processing. Its success comes from
minimal ceremony — you can do useful work with just 2 tokens:

```bash
echo '{"name":"world"}' | jq '.name'
```

**utlx** has a much richer feature set (format-agnostic, multi-input, type system), but
the current CLI requires significantly more ceremony for simple tasks:

```bash
# Step 1: Create a script file (identity.utlx)
#   %utlx 1.0
#   input json
#   output json
#   ---
#   $input

# Step 2: Run it
echo '{"name":"world"}' | utlx transform identity.utlx
```

This document proposes backward-compatible improvements to close the ergonomics gap,
particularly for the very common use case of **format conversion** (JSON/XML/CSV/YAML).

---

## Current State Analysis

### What utlx already does well (like jq)
- Reads from **stdin** when no input file given
- Writes to **stdout** when no `-o` file given
- **Auto-detects** input format from content
- **Pretty-prints** by default
- Has short alias `t` for `transform`

### What requires unnecessary ceremony

| Aspect | jq | utlx (current) | Gap |
|--------|-----|----------------|-----|
| Identity transform | `jq .` | Requires 5-line script file | Critical |
| Inline expression | `jq '.name'` | Not supported | Critical |
| Subcommand | N/A (`jq` = transform) | `utlx transform` required | Medium |
| Format conversion | N/A (JSON-only) | Requires script file with header | Critical |
| Input format | Implicit (JSON) | `input json` in script header | Low (auto-detect works) |
| Output format | Implicit (JSON) | `output json` in script header | Low (`--output-format` works) |

### Flag inventory

| Short | Long | Meaning | Used by | Status |
|-------|------|---------|---------|--------|
| `-i` | `--input` | Input **file** (or name=file) | transform | existing |
| `-o` | `--output` | Output **file** (or name=file) | transform | existing |
| `-v` | `--verbose` | Verbose mode | transform, validate, lint | existing |
| `-h` | `--help` | Show help | all commands | existing |
| `-f` | `--format` | Output **display** format (human/json/compact) | validate, lint | existing |
| `-e` | — | **Not used** (available for Phase 2) | — | available |
| `-t` | — | **Not used** (available) | — | available |
| `-s` | `--schema` | Schema file | validate | existing |
| `-r` | `--rules` | Rules file | lint | existing |
| `-c` | `--compact` | Compact output | udm format | existing |
| — | `--input-format` | Force input parse format | transform | existing |
| — | `--from` | Alias for `--input-format` | transform | **new (Phase 1)** |
| — | `--output-format` | Force output serialization format | transform | existing |
| — | `--to` | Alias for `--output-format` | transform | **new (Phase 1)** |

---

## Improvements

### Improvement 1: Identity / Passthrough Mode with Smart Format Flip — IMPLEMENTED

**Priority: Critical** — Enables format conversion with zero flags and zero script files

When no script file is provided and stdin has data, utlx performs an **identity transform**
(pass through `$input` unchanged) with **intelligent output format selection**:

**Smart Format Flip — the core idea:**

The auto-detection engine already identifies the input format from content. In identity mode,
the output format is automatically chosen as the most useful "opposite":

| Detected input | Default output | Rationale |
|---------------|----------------|-----------|
| **XML** | **JSON** | Most common conversion direction |
| **JSON** | **XML** | Most common conversion direction |
| CSV | JSON | JSON is the universal interchange |
| YAML | JSON | JSON is the universal interchange |
| anything else | JSON | Safe default |

This means **the two most common conversions require zero flags:**

```bash
# XML to JSON — just pipe it
cat data.xml | utlx

# JSON to XML — just pipe it
cat data.json | utlx

# CSV to JSON — auto-detected
cat data.csv | utlx

# YAML to JSON — auto-detected
cat config.yaml | utlx
```

**Override with `--to` when the smart default isn't what you want:**

```bash
# XML to YAML (not the default JSON)
cat data.xml | utlx --to yaml

# JSON to CSV
echo '[{"a":1},{"a":2}]' | utlx --to csv

# Force JSON to JSON (pretty-print only, override the XML flip)
echo '{"a":1}' | utlx --to json
```

**Implementation (done):**

1. **`Main.kt`**: when no known command and no `.utlx` file → enter identity mode
   - No args at all + stdin is piped (`System.console() == null`) → identity mode
   - First arg is a flag (`--to`, `--from`, `--output-format`, `--input-format`, etc.) → identity mode

2. **`TransformCommand.kt`**: `scriptFile` is now nullable; when null, synthesizes an
   identity script in memory: `%utlx 1.0\ninput auto\noutput <smart-flip>\n---\n$input`

3. **Smart format flip** (in `TransformCommand.inferOutputFormat()`):
   ```kotlin
   private fun inferOutputFormat(detectedInputFormat: String): String {
       return when (detectedInputFormat) {
           "xml"  -> "json"
           "json" -> "xml"
           else   -> "json"
       }
   }
   ```

4. Format detection reuses existing content-based auto-detect logic (`detectFormatFromContent()`).

**Test coverage:** 6 new tests in `TransformCommandTest.kt`:
- Identity XML→JSON, JSON→XML, CSV→JSON (smart flip)
- Explicit `--to` override of smart flip
- `--to`/`--from` aliases in identity mode
- No-args identity mode parsing

**Backward compatibility:** No existing behavior changes. Currently, missing script = error.
This adds new behavior where an error existed before.

---

### Improvement 2: Short format aliases `--to` and `--from` — IMPLEMENTED

**Priority: High** — Makes format conversion truly concise

`--output-format` is 15 characters. For the most common operation (format conversion),
short aliases are essential.

```bash
echo '{"name":"world"}' | utlx --to xml
cat data.xml | utlx --to json
cat data.csv | utlx --from csv --to yaml
```

**Implementation (done):** Added `"--to"` and `"--from"` as aliases in
`TransformCommand.parseOptions()` alongside existing `"--output-format"` and `"--input-format"`.
Both the long form and short alias map to the same `outputFormat`/`inputFormat` variables.

**Test coverage:** 2 new tests in `TransformCommandTest.kt`:
- `--to` alias with script-based transform
- `--from` alias with script-based transform

**Backward compatibility:** Pure addition. New flags, no existing flags change.

---

### Improvement 3: Inline Expression (`-e`) — NOT YET IMPLEMENTED

**Priority: High** — Enables jq-style one-liners without script files

```bash
# Extract a field
echo '{"user":{"name":"Alice"}}' | utlx -e '$input.user.name'

# Transform with expression
echo '{"a":1,"b":2}' | utlx -e '{sum: $input.a + $input.b}'

# Combine with format conversion
cat data.xml | utlx -e '$input.Orders.Order' --to json

# Filter + convert
cat data.json | utlx -e '$input.items |> filter(i => i.active)' --to csv
```

**Implementation:**
- Add `-e` / `--expression` flag to `TransformCommand.parseOptions()`
- When `-e` is present, synthesize a script in memory:
  ```
  %utlx 1.0
  input auto
  output json
  ---
  <expression>
  ```
- Pass the synthesized script to `TransformationService.transform()` instead of reading a file
- `-e` and script file are mutually exclusive (error if both provided)

**Backward compatibility:** `-e` is currently unused. Pure addition.

---

### Improvement 4: Implicit `transform` Subcommand — IMPLEMENTED

**Priority: Medium** — Reduces typing, feels more natural

When the first argument is a `.utlx` file or a recognized flag, assume `transform`.

```bash
# Current (still works)
utlx transform script.utlx input.json
utlx t script.utlx input.json

# New: implicit transform
utlx script.utlx input.json
utlx script.utlx < input.json
utlx --to xml < input.json
```

**Implementation (done)** — detection logic in `Main.kt` `else` branch:
1. If `args[0]` ends with `.utlx` → route to `TransformCommand.execute(args)`
2. If `args[0]` is `--to`, `--from`, `--output-format`, `--input-format`, `--no-pretty`, `--verbose`, or `-v` → route to `TransformCommand.execute(args, identityMode = true)`
3. Otherwise → error as before

**Test coverage:** 1 new test in `TransformCommandTest.kt`:
- Implicit transform with `.utlx` file as first arg

**Backward compatibility:** Currently the `else` branch prints "Unknown command" and exits
with error. This changes an error into useful behavior. All existing `utlx transform ...`
invocations continue to work unchanged.

---

### Improvement 5: Optional Script Header (smart defaults) — NOT YET IMPLEMENTED

**Priority: Medium** — Reduces boilerplate in script files

Allow scripts without the full header. When header lines are missing, apply defaults.

```utlx
// Full header (current, still works)
%utlx 1.0
input json
output json
---
$input.name

// Minimal header: just the separator
---
$input.name

// No header at all (defaults: input auto, output json)
$input.name

// Partial header: only specify what differs from defaults
%utlx 1.0
input xml
output json
---
$input.Order.Customer.Name
```

**Defaults when omitted:**
| Header line | Default |
|-------------|---------|
| `%utlx 1.0` | Assumed version 1.0 |
| `input <format>` | `input auto` (auto-detect) |
| `output <format>` | `output json` |
| `---` | If no `---` found, entire file is the body |

**Implementation:**
- In `parser_impl.kt`: if first token is not `PERCENT_DIRECTIVE`, synthesize default header
- If `---` is found but no `%utlx` before it, synthesize defaults for missing directives
- If no `---` at all, treat entire content as body with all defaults
- CLI `--output-format` and `--input-format` flags override these defaults (already works)

**Backward compatibility:** All existing scripts with full headers parse identically.
This only changes behavior for scripts that currently fail to parse.

---

### Improvement 6: Dot (`.`) as Identity Expression — NOT YET IMPLEMENTED

**Priority: Low** — Familiar to jq users

Allow `.` as shorthand for `$input` in expressions.

```bash
# jq-style identity
echo '{"a":1}' | utlx -e '.'

# jq-style path access  
echo '{"a":{"b":1}}' | utlx -e '.a.b'
```

**Implementation:**
- In the `-e` expression handler: if expression starts with `.`, prepend `$input`
- `.` alone → `$input`
- `.foo.bar` → `$input.foo.bar`
- Only applies to `-e` mode (not script files, to avoid ambiguity)

**Backward compatibility:** Pure addition to new `-e` mode only.

---

### Improvement 7: File Extension-Based Format Detection — NOT YET IMPLEMENTED

**Priority: Low** — Quality of life for file-based workflows

When input/output files are specified with recognized extensions, auto-detect format
from the extension (in addition to current content-based detection).

```bash
# Format inferred from file extension
utlx script.utlx -i data.xml -o result.json     # xml→json
utlx script.utlx -i data.csv -o result.yaml      # csv→yaml

# Identity mode with file extension detection
utlx -i data.xml -o result.json                   # xml→json, no script needed
```

**Extension mapping:**
| Extension | Format |
|-----------|--------|
| `.json` | json |
| `.xml` | xml |
| `.csv` | csv |
| `.yaml`, `.yml` | yaml |
| `.xsd` | xsd |
| `.avsc` | avro |
| `.proto` | protobuf |

**Priority:** `--input-format` flag > file extension > content detection > json fallback

**Implementation:**
- Add `detectFormatFromExtension(file: File): String?` utility
- Use in `TransformCommand.execute()` when format is not explicitly specified
- For output in identity mode: detect from `-o` extension to set output format

**Backward compatibility:** Only activates when no explicit format is specified.
Current content-based detection becomes a fallback.

---

## Combined Effect: Before and After

### Use case 1: XML to JSON (the #1 use case)
```bash
# Before: need a 5-line script file, then:
#   utlx transform identity.utlx < data.xml

# After: just pipe it
cat data.xml | utlx
```

### Use case 2: JSON to XML
```bash
# Before: need script file with output xml header

# After: smart flip detects JSON → outputs XML
echo '{"name":"world"}' | utlx
```

### Use case 3: CSV to JSON
```bash
# Before: jq can't do this. utlx needs script file.

# After: auto-detected, zero flags
cat data.csv | utlx
```

### Use case 4: XML to YAML (override smart default)
```bash
# Smart default would give JSON, override with --to
cat data.xml | utlx --to yaml
```

### Use case 5: Extract field from JSON
```bash
# Before (jq)
echo '{"user":{"name":"Alice"}}' | jq '.user.name'

# After (utlx)
echo '{"user":{"name":"Alice"}}' | utlx -e '.user.name'
```

### Use case 6: XML field extraction to JSON
```bash
# Before: need script file with 5 lines

# After
cat orders.xml | utlx -e '$input.Orders.Order[0].Customer.Name'
```

### Use case 7: JSON pretty-print (override smart flip)
```bash
# jq
echo '{"a":1}' | jq .

# utlx (override the xml flip)
echo '{"a":1}' | utlx --to json
```

### Use case 8: Complex transform (unchanged)
```bash
# This is where script files shine — nothing changes
utlx transform complex-mapping.utlx -i input.xml -o output.json
utlx complex-mapping.utlx -i input.xml -o output.json  # shorter with Improvement 4
```

---

## Implementation Status

| Phase | Improvement | Effort | Value | Status |
|-------|-------------|--------|-------|--------|
| **Phase 1** | 1. Identity mode with smart format flip | Small | Unlocks format conversion | **DONE** |
| **Phase 1** | 2. `--to` / `--from` aliases | Tiny | Makes Phase 1 concise | **DONE** |
| **Phase 1** | 4. Implicit `transform` subcommand | Small | Less typing | **DONE** |
| **Phase 2** | 3. `-e` inline expression | Medium | Unlocks one-liners | Planned |
| **Phase 3** | 5. Optional script header | Medium | Simpler scripts | Planned |
| **Phase 3** | 6. Dot identity (`.`) | Tiny | jq familiarity | Planned |
| **Phase 3** | 7. Extension-based format detection | Small | Quality of life | Planned |

**Phase 1 is complete.** The most common use cases now work with zero flags:
```bash
cat data.xml | utlx          # XML → JSON
cat data.json | utlx         # JSON → XML
cat data.csv | utlx          # CSV → JSON
cat data.xml | utlx --to yaml  # override smart flip
```

**Test coverage:** 8 new tests added to `TransformCommandTest.kt`, all 18 tests passing (10 existing + 8 new).

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Ambiguity: is positional arg a file or subcommand? | Only `.utlx` files trigger implicit transform; known commands take priority |
| `-o` is output **file**, `--to` is output **format** — confusing? | Different names, clear docs. Alternative: use `-O` (uppercase) |
| Headerless scripts harder to understand | Full headers still recommended in docs/tutorials; headerless is for quick scripts |
| `.` dot syntax conflicts with existing UTL-X syntax | Only in `-e` mode, not in script files |
| Breaking the `else` branch in Main.kt | All known commands matched first; only unrecognized args trigger new logic |

---

## Non-Goals

- **Replacing jq for JSON-only work** — jq has 10+ years of JSON-specific features. utlx targets format-agnostic and complex transformation use cases.
- **Changing existing flags** — all current flags keep their exact current behavior.
- **Changing the UTL-X language** — these are CLI-only improvements. The language spec is unchanged.
