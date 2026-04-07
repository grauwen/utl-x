## Comparison: UTL-X vs jq

### Core Purpose

**UTL-X** is a format-agnostic functional transformation language that works with XML, JSON, CSV, YAML, and other formats. Write your transformation logic once, apply it to any supported format. 652 built-in functions across 18 categories.

**jq** is a lightweight command-line JSON processor — like sed/awk/grep for JSON data. Focused exclusively on JSON manipulation.

### Key Differences

| Feature | UTL-X | jq |
|---------|-------|-----|
| **Format Support** | XML, JSON, CSV, YAML, XSD, Avro, Protobuf, and more | JSON only |
| **Language** | Written in Kotlin (JVM or GraalVM native binary) | Written in C, single native binary |
| **License** | AGPL-3.0 (copyleft) / Commercial dual-license | MIT (permissive) |
| **Type System** | Strongly typed with compile-time checking | Dynamically typed |
| **Stdlib** | 652 functions across 18 categories | ~50 built-in functions |
| **Piping** | `cat data.xml \| utlx` (smart format flip) | `cat data.json \| jq '.'` |
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

# UTL-X (with -e inline expression)
echo '{"user":{"name":"Alice"}}' | utlx -e '$input.user.name'
```

**Filtering:**

```bash
# jq
jq '.data | map(select(.price > 100))' data.json

# UTL-X (script file)
# filter.utlx:
#   %utlx 1.0
#   input json
#   output json
#   ---
#   $input.data |> filter(item => item.price > 100)

utlx filter.utlx data.json
```

### Where UTL-X wins

- **Format agnostic** — one transformation works with XML, JSON, CSV, YAML; jq is JSON-only
- **Zero-flag format conversion** — `cat data.xml | utlx` converts XML to JSON instantly; jq cannot read XML at all
- **652 functions** — string, array, math, date, XML, CSV, YAML, binary, encoding, financial, geospatial, security
- **Strong typing** — catches errors at compile time, not at runtime
- **Multi-input** — join data from multiple files/formats in one transformation
- **Schema awareness** — XSD, JSON Schema, Avro, Protobuf support

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
- Need strong typing and compile-time validation
- Are building ETL pipelines or data migration tools
- Need rich stdlib (652 functions across 18 categories)

**Choose jq if you:**

- Only work with JSON
- Need zero-dependency single-binary installation
- Want maximum shell scripting integration
- Need a permissive (MIT) license
- Need the smallest possible footprint

### Bottom Line

jq is the standard for JSON processing — fast, mature, universal. UTL-X targets a different space: format-agnostic transformation with a rich standard library. For pure JSON work in shell scripts, jq is hard to beat. For anything involving XML, CSV, YAML, or multi-format pipelines, UTL-X does what jq simply cannot. With the new identity mode (`cat data.xml | utlx`), UTL-X is now as easy to use as jq for the most common conversion tasks.
