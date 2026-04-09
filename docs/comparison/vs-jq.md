## Comparison: UTL-X vs jq

### Core Purpose

**UTL-X** is a format-agnostic functional transformation language that works with XML, JSON, CSV, YAML, and other formats. Write your transformation logic once, apply it to any supported format. 652 built-in functions across 18 categories.

**jq** is a lightweight command-line JSON processor — like sed/awk/grep for JSON data. Focused exclusively on JSON manipulation.

### Key Differences

| Feature | UTL-X | jq |
|---------|-------|-----|
| **Format Support** | XML, JSON, CSV, YAML, OData + 6 schema formats | JSON only |
| **Language** | Written in Kotlin (JVM or GraalVM native binary) | Written in C, single native binary |
| **License** | AGPL-3.0 (copyleft) / Commercial dual-license | MIT (permissive) |
| **Type System** | Strongly typed with compile-time checking | Dynamically typed |
| **Stdlib** | 652 functions across 18 categories | ~50 built-in functions |
| **Inline expressions** | `utlx -e '.name'` (dot shorthand like jq) | `jq '.name'` |
| **Raw output** | `utlx -e '.name' -r` | `jq -r '.name'` |
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

### Where UTL-X wins

- **Format agnostic** — one transformation works with XML, JSON, CSV, YAML; jq is JSON-only
- **Zero-flag format conversion** — `cat data.xml | utlx` converts XML to JSON instantly; jq cannot read XML at all
- **jq-like expressions** — `utlx -e '.name' -r` uses the same dot syntax as jq, with `-r` for raw output
- **652 functions** — string, array, math, date, XML, CSV, YAML, binary, encoding, financial, geospatial, security
- **Strong typing** — catches errors at compile time, not at runtime
- **Multi-input** — join data from multiple files/formats in one transformation
- **OData support** — parse and serialize OData JSON and EDMX metadata; jq has no OData awareness
- **Schema awareness** — XSD, JSON Schema, Avro, Protobuf, OData/EDMX, Table Schema

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

jq is the standard for JSON processing — fast, mature, universal. UTL-X targets a different space: format-agnostic transformation with a rich standard library. For pure JSON work in shell scripts, jq is hard to beat. For anything involving XML, CSV, YAML, or multi-format pipelines, UTL-X does what jq simply cannot. With `-e` expressions and `-r` raw output, UTL-X now matches jq's shell scripting ergonomics while adding format conversion that jq can't do.
