# UTL-X CLI Reference

Complete reference for the UTL-X command-line interface.

## Overview

UTL-X CLI operates in three modes:

| Mode | When | Example |
|------|------|---------|
| **Identity** | No script, no expression | `cat data.xml \| utlx` |
| **Expression** | `-e` flag with inline expression | `cat data.json \| utlx -e '.name'` |
| **Script** | `.utlx` file provided | `utlx transform script.utlx input.xml` |

---

## Identity Mode (Format Conversion)

Pipe data in with no script or expression — UTL-X auto-detects the input format and smart-flips the output:

```bash
cat data.xml | utlx              # XML → JSON (smart flip)
cat data.json | utlx             # JSON → XML (smart flip)
cat data.csv | utlx              # CSV → JSON
cat data.yaml | utlx             # YAML → JSON
```

**Smart flip rules:**

| Detected input | Default output |
|---------------|----------------|
| XML | JSON |
| JSON | XML |
| CSV | JSON |
| YAML | JSON |
| Everything else | JSON |

Override with `--to`:

```bash
cat data.xml | utlx --to yaml     # XML → YAML
cat data.json | utlx --to csv     # JSON → CSV
cat data.csv | utlx --from csv --to xml  # explicit both
```

---

## Expression Mode (`-e`)

Inline UTL-X expressions — no script file needed. The expression is evaluated against the piped input.

### Basic syntax

```bash
echo '{"name":"Alice","age":30}' | utlx -e '.name'
```

**Short form:** `-e`
**Long form:** `--expression`

### Input reference

In `-e` mode, `.` is shorthand for `$input`:

| Expression | Equivalent | Description |
|-----------|------------|-------------|
| `.` | `$input` | Identity (entire input) |
| `.name` | `$input.name` | Access property |
| `.order.items[0]` | `$input.order.items[0]` | Nested access |
| `..productCode` | *(not supported)* | No recursive descent in UTL-X; name the steps |
| `.@id` | `$input.@id` | XML attribute |
| `$input.name` | `$input.name` | Explicit (always works) |

### Output format

In `-e` mode, the output format **matches the input format** by default (no flip):

```bash
# JSON in → JSON out
echo '{"name":"Alice"}' | utlx -e '{greeting: "Hello " + .name}'
# {"greeting":"Hello Alice"}

# XML in → XML out
echo '<person><name>Alice</name></person>' | utlx -e '{greeting: .person.name}'
# <root><greeting>Alice</greeting></root>
```

Override with `--to`:

```bash
echo '{"name":"Alice"}' | utlx -e '.name' --to xml
```

### Raw output (`-r`)

Strip JSON quotes from string values — essential for shell scripting:

```bash
# Without -r: JSON string (with quotes)
echo '{"name":"Alice"}' | utlx -e '.name'
# "Alice"

# With -r: raw string (no quotes)
echo '{"name":"Alice"}' | utlx -e '.name' -r
# Alice
```

**Short form:** `-r`
**Long form:** `--raw-output`

Use in shell variable assignment:

```bash
NAME=$(echo '{"name":"Alice"}' | utlx -e '.name' -r)
echo "Hello $NAME"   # Hello Alice
```

### Expression examples

**Extract field:**
```bash
echo '{"user":{"name":"Alice"}}' | utlx -e '.user.name'
```

**Filter array:**
```bash
cat orders.json | utlx -e '. |> filter(o => o.status == "active")'
```

**Transform and map:**
```bash
cat users.json | utlx -e '. |> map(u => {name: u.name, email: u.email})'
```

**Aggregate:**
```bash
cat sales.json | utlx -e '{
  total: sum(. |> map(s => s.amount)),
  count: count(.),
  average: avg(. |> map(s => s.amount))
}'
```

**Sort:**
```bash
cat products.json | utlx -e '. |> sortBy(p => p.price)'
```

**Count with condition:**
```bash
cat events.json | utlx -e 'count(. |> filter(e => e.type == "error"))'
```

**String manipulation:**
```bash
echo '{"name":"alice"}' | utlx -e 'upper(.name)' -r
# ALICE
```

**Convert format on the fly:**
```bash
# Extract from XML, output as JSON
cat orders.xml | utlx -e '.Orders.Order |> map(o => {id: o.@id, total: o.Total})' --to json

# Extract from JSON, output as CSV
cat users.json | utlx -e '. |> map(u => {name: u.name, email: u.email})' --to csv
```

---

## Script Mode

Full UTL-X scripts with headers for complex transformations.

### Explicit subcommand

```bash
utlx transform script.utlx input.xml -o output.json
```

### Implicit (no `transform` keyword)

```bash
utlx script.utlx input.xml -o output.json
```

### From stdin

```bash
cat data.xml | utlx transform script.utlx -o output.json
```

---

## Commands

| Command | Alias | Description |
|---------|-------|-------------|
| `transform` | `t` | Transform data using a script |
| `validate` | `v` | Validate script syntax (Levels 1-3) |
| `lint` | `l` | Check code quality (Level 4) |
| `functions` | `fn` | List stdlib functions |
| `repl` | `r` | Interactive REPL |
| `capture` | — | Manage test capture |
| `udm` | — | Work with UDM files |
| `version` | — | Show version |
| `help` | — | Show help |

---

## Global Flags

### Input/Output

| Flag | Alias | Description | Default |
|------|-------|-------------|---------|
| `-e EXPR` | `--expression EXPR` | Inline expression | — |
| `-r` | `--raw-output` | Strip quotes from string output | off |
| `-i FILE` | `--input FILE` | Input file (or `name=FILE` for multi-input) | stdin |
| `-o FILE` | `--output FILE` | Output file (or `name=FILE`) | stdout |
| `--from FORMAT` | `--input-format FORMAT` | Force input format | auto-detect |
| `--to FORMAT` | `--output-format FORMAT` | Force output format | depends on mode |

### Formatting

| Flag | Description | Default |
|------|-------------|---------|
| `--no-pretty` | Disable pretty-printing | pretty on |

### Type checking

| Flag | Description | Default |
|------|-------------|---------|
| `--strict-types` | Fail on type errors | off (warnings only) |

### Input strictness (`--parse-profile`)

| Flag | Description | Default |
|------|-------------|---------|
| `--parse-profile strict\|standard\|lenient` | How much malformed input to tolerate | `standard` |

A transformation often has to read data from a system you do not control. The profile decides
whether questionable input is **rejected loudly** or **accepted on a documented rule** — the same
policy for JSON, XML and YAML (B31).

| | `strict` | `standard` (default) | `lenient` |
|---|---|---|---|
| duplicate keys (JSON/YAML) | reject | reject | **accept** — last value wins |
| max nesting depth | 64 | 256 | 256 |
| XML DOCTYPE | reject | skip | skip |
| undefined XML entities | reject | reject | pass through as text |

```bash
# a feed you cannot control: take it as-is
utlx transform --parse-profile lenient script.utlx messy.json

# a contract you want enforced at the door
utlx transform --parse-profile strict script.utlx partner.xml
```

**The flag must come before the file arguments.** `utlx transform script.utlx in.json
--parse-profile lenient` prints usage instead of running.

When input is rejected for a reason a profile governs, the error says which profile would accept
it:

```
Error: Error parsing input: JSON parse error at 1:28 - Duplicate object key: 'id'
       Hint: this input is rejected under the 'standard' parse profile. Re-run with
       --parse-profile lenient to accept it (the last value wins).
```

No hint appears when no profile would help — a truncated document is a syntax error at every
level, and nesting beyond 256 is refused by `standard` and `lenient` alike.

Note the asymmetry worth knowing: depth is a **resource bound**, not a grammar rule, so `lenient`
does not relax it beyond `standard`. Only `strict` is tighter.

### Lexical fidelity (`--preserve-numeric-lexeme`)

| Flag | Description | Default |
|------|-------------|---------|
| `--preserve-numeric-lexeme` | Keep a numeric value whose written form the numeric model cannot reproduce | off |

Numbers are read exactly, so `100.10` keeps its scale and `9007199254740993` survives (B35). Three
written forms still cannot be rebuilt, because the loss is notation rather than precision and no
numeric type can hold it:

| written | rebuilt by default | with the flag |
|---|---|---|
| `-0`, `-0.00` | `0`, `0.00` | `-0`, `-0.00` |
| `1e3`, `1.5e2` | `1000`, `150` | `1e3`, `1.5e2` |
| `+2` *(YAML only)* | `2` | `+2` |

```bash
# a canonical rebuild that must match the bytes it received
utlx transform --preserve-numeric-lexeme canon.utlx order.xml
```

Use it where the **written form is itself the information** — canonicalisation, a signature over
rebuilt output, a guard comparing what it emitted against what it received. Three things to know
before turning it on:

- **A preserved value is a string.** Arithmetic on one needs `toNumber()`, exactly as it already
  does for a zero-padded `007`. That is why the flag is off by default.
- **It is independent of `--parse-profile`.** Strictness and fidelity are separate axes, so
  `--parse-profile strict --preserve-numeric-lexeme` is a sensible combination and no profile turns
  fidelity on by itself.
- **XML, CSV and YAML only.** JSON's grammar has already typed the token, so preserving the lexeme
  there would emit `"1e3"` *with quotes* — a changed type rather than a preserved form. A JSON
  round-trip of `1e3` is still `1000`. See
  [B65](../limitations/B65-numeric-and-lexical-fidelity-on-a-round-trip.md) for the full boundary
  and [F31](../features/F31-lexical-fidelity-for-numeric-values.md) for the design.

Unlike `--parse-profile`, this flag takes no value, so it works either before or after the file
arguments.

**Where else it can be set.** The engine reads it from `EngineConfig`; the daemon from the
`UTLX_PARSE_PROFILE` environment variable or `-Dutlx.parse.profile` (IB08), so a deployment that
must swallow malformed input can select it once rather than per call.

### Test capture

| Flag | Description | Default |
|------|-------------|---------|
| `--capture` | Force enable test capture | config |
| `--no-capture` | Force disable test capture | config |

### Debug

| Flag | Description |
|------|-------------|
| `--debug` | DEBUG logging for all components |
| `--debug-parser` | DEBUG for parser only |
| `--debug-lexer` | DEBUG for lexer only |
| `--debug-interpreter` | DEBUG for interpreter only |
| `--debug-types` | DEBUG for type system only |
| `--trace` | TRACE level (most verbose) |

### Help

| Flag | Description |
|------|-------------|
| `-h` / `--help` | Show help |
| `-v` / `--version` | Show version |

---

## Supported Formats

### Tier 1 — Data formats

| Format | `--from` / `--to` value | Input | Output |
|--------|------------------------|-------|--------|
| JSON | `json` | Yes | Yes |
| XML | `xml` | Yes | Yes |
| CSV | `csv` | Yes | Yes |
| YAML | `yaml` | Yes | Yes |
| OData | `odata` | Yes | Yes |
| Auto-detect | `auto` | Yes | — |

### Tier 2 — Schema formats

| Format | `--from` / `--to` value | Input | Output |
|--------|------------------------|-------|--------|
| XSD | `xsd` | Yes | Yes |
| JSON Schema | `jsch` | Yes | Yes |
| Avro | `avro` | Yes | Yes |
| Protobuf | `proto` | Yes | Yes |
| OData/EDMX | `osch` | Yes | Yes |
| Table Schema | `tsch` | Yes | Yes |

---

## Mode Summary

```
                    ┌─────────────────────────────────────────┐
                    │              UTL-X CLI                   │
                    └─────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
       ┌──────┴──────┐ ┌─────┴─────┐  ┌──────┴──────┐
       │  Identity   │ │ Expression│  │   Script    │
       │    Mode     │ │   Mode    │  │    Mode     │
       └─────────────┘ └───────────┘  └─────────────┘
       No script,       -e flag        .utlx file
       no expression
                                       
       Smart flip       Preserve       From header
       XML↔JSON         input format   output directive
       else→JSON        
                                       
       cat x.xml|utlx   utlx -e '.n'  utlx t s.utlx
```

---

## Examples by Use Case

### Format conversion (Identity Mode)

```bash
cat data.xml | utlx                          # XML → JSON
cat data.json | utlx                         # JSON → XML
cat data.csv | utlx --to json                # CSV → JSON
cat data.yaml | utlx --to xml                # YAML → XML
```

### Data extraction (Expression Mode)

```bash
cat api.json | utlx -e '.data.users' -r     # Extract and raw output
cat config.xml | utlx -e '.config.db.host' -r  # Extract from XML
```

### Shell scripting (Expression Mode + Raw)

```bash
VERSION=$(cat package.json | utlx -e '.version' -r)
DB_HOST=$(cat config.yaml | utlx -e '.database.host' -r)
COUNT=$(cat events.json | utlx -e 'count(.)' -r)
```

### Complex transformation (Script Mode)

```bash
utlx transform order-to-invoice.utlx order.xml -o invoice.json
utlx order-to-invoice.utlx order.xml -o invoice.json    # implicit
cat order.xml | utlx transform order-to-invoice.utlx     # from stdin
```

### Multi-input (Script Mode)

```bash
utlx transform enrich.utlx \
  --input orders=orders.xml \
  --input customers=customers.json \
  --input rates=rates.csv \
  -o enriched.json
```

### Validation & linting

```bash
utlx validate script.utlx              # Syntax + semantic check
utlx validate script.utlx --strict     # Fail on type warnings
utlx lint script.utlx                  # Code quality check
utlx lint script.utlx --format json    # Machine-readable output
```

### Exploring stdlib

```bash
utlx functions                          # List all 650+ functions
utlx functions search xml               # Search by keyword
utlx functions info map                  # Detailed function info
utlx functions --category Array          # Filter by category
utlx functions stats                     # Category statistics
```

---

## Roadmap

| Feature | Status |
|---------|--------|
| Identity mode (smart flip) | **Implemented** |
| `--to` / `--from` aliases | **Implemented** |
| Implicit `transform` subcommand | **Implemented** |
| `-e` / `--expression` inline mode | **Planned** |
| `-r` / `--raw-output` | **Planned** |
| `--slurp` (read lines into array) | Future |
| `--arg name value` (shell variables) | Future |
