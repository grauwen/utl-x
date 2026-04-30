= UTL-X on the Command Line

UTL-X was designed to feel at home in a terminal. If you've used `jq` for JSON processing, UTL-X will feel familiar — but it works with XML, CSV, YAML, and OData too. This chapter covers the three CLI modes, shell scripting patterns, and when to use each.

== Three Ways to Use the CLI

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Mode*], [*Syntax*], [*Best for*],
  [Transform], [`utlx transform script.utlx input.xml`], [Complex transformations with a .utlx file],
  [Expression (`-e`)], [`echo data | utlx -e '.name'`], [Quick one-liners, field extraction, piping],
  [Identity (flip)], [`cat data.xml | utlx`], [Instant format conversion, no script needed],
)

== Expression Mode: The jq Experience

Expression mode (`-e`) is the most powerful way to use UTL-X interactively. Instead of writing a `.utlx` file, you pass the transformation as a command-line argument:

```bash
echo '{"name": "Alice", "age": 30}' | utlx -e '.name'
# "Alice"
```

The dot (`.`) is shorthand for `$input`. So `.name` means `$input.name`, and `.` alone means the entire input — the identity expression. This mirrors jq's syntax deliberately: `utlx -e '.'` is the UTL-X equivalent of `jq '.'`.

=== Raw Output (-r)

By default, string output includes quotes. Use `-r` (raw) to strip them — essential for piping to other commands:

```bash
# With quotes (default)
echo '{"name": "Alice"}' | utlx -e '.name'
# "Alice"

# Raw (no quotes) — pipe-friendly
echo '{"name": "Alice"}' | utlx -e '.name' -r
# Alice

# Use in shell variables
NAME=$(echo '{"name": "Alice"}' | utlx -e '.name' -r)
echo "Hello, $NAME"
# Hello, Alice
```

=== Expressions with Any Format

Unlike jq, expression mode works with XML, CSV, and YAML too:

```bash
# Extract from XML
echo '<User><Name>Alice</Name></User>' | utlx --from xml -e '.User.Name' -r
# Alice

# Extract from XML attribute
echo '<Product id="P-001"/>' | utlx --from xml -e '.Product.@id' -r
# P-001

# Extract from YAML
echo 'server: prod-01\nport: 8080' | utlx --from yaml -e '.port' -r
# 8080

# Extract from CSV (first row)
echo 'name,age\nAlice,30\nBob,25' | utlx --from csv -e '.[0].name' -r
# Alice
```

=== Complex Expressions

Expressions can be as simple or complex as needed:

```bash
# Object construction
echo '{"first": "Alice", "last": "Johnson"}' | utlx -e '{fullName: concat(.first, " ", .last)}'
# {"fullName": "Alice Johnson"}

# Array operations
echo '[3, 1, 4, 1, 5, 9]' | utlx -e 'sort($input)'
# [1, 1, 3, 4, 5, 9]

# Filter and map
echo '{"users": [{"name": "Alice", "active": true}, {"name": "Bob", "active": false}]}' \
  | utlx -e 'map(filter(.users, (u) -> u.active), (u) -> u.name)'
# ["Alice"]

# Aggregation
echo '[{"amount": 10}, {"amount": 20}, {"amount": 30}]' | utlx -e 'sum(map($input, (x) -> x.amount))'
# 60
```

== Identity Mode: The Flip

The simplest way to use UTL-X — pipe data in, get a different format out. No script, no expression, no arguments:

```bash
# XML → JSON (automatic)
cat order.xml | utlx
# {"Order": {"Customer": "Alice", "Total": 299.99}}

# JSON → XML (automatic)
echo '{"name": "Alice"}' | utlx
# <?xml version="1.0" encoding="UTF-8"?><name>Alice</name>
```

UTL-X auto-detects the input format and "flips" to the most useful output format:

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Input detected as*], [*Output format (default flip)*],
  [XML], [JSON],
  [JSON], [XML],
  [CSV], [JSON],
  [YAML], [JSON],
)

Override the output format with `--to`:

```bash
# XML → YAML (instead of default JSON)
cat order.xml | utlx --to yaml

# JSON → CSV
echo '[{"name":"Alice","age":30},{"name":"Bob","age":25}]' | utlx --to csv

# CSV → YAML
cat report.csv | utlx --to yaml
```

Override the input format with `--from` (when auto-detection fails):

```bash
# Force input as CSV (e.g., when piped from another command)
some-command | utlx --from csv --to json
```

== Shell Scripting Patterns

=== Batch File Conversion

Convert all XML files in a directory to JSON:

```bash
for f in *.xml; do
  utlx --from xml --to json < "$f" > "${f%.xml}.json"
done
```

=== Pipeline Integration

UTL-X is pipe-friendly — combine it with standard Unix tools:

```bash
# Extract customer names from XML orders, sort, deduplicate
cat orders.xml | utlx --from xml -e 'map(.Orders.Order, (o) -> o.Customer)' \
  | utlx -e 'sort($input)' \
  | utlx -e 'unique($input)'

# Count items in a JSON array
curl -s https://api.example.com/users | utlx -e 'count($input)' -r

# Filter CSV rows and convert to JSON
cat report.csv | utlx --from csv -e 'filter($input, (row) -> toNumber(row.amount) > 100)' --to json
```

=== Using UTL-X in CI/CD

Validate a configuration file in a GitHub Actions workflow:

```yaml
- name: Validate config
  run: |
    cat config.yaml | utlx --from yaml -e 'if (.version != null && .database != null) "valid" else "invalid"' -r
```

Transform API responses in deployment scripts:

```bash
# Extract image tag from Kubernetes deployment
kubectl get deployment myapp -o json | utlx -e '.spec.template.spec.containers[0].image' -r
```

=== Combining with jq

UTL-X and jq can coexist. Use jq for JSON-only tasks where its syntax is more concise, and UTL-X when you need XML, CSV, or YAML:

```bash
# jq for simple JSON field extraction (shorter syntax)
echo '{"name": "Alice"}' | jq -r '.name'

# UTL-X when input is XML (jq can't do this)
echo '<User><Name>Alice</Name></User>' | utlx --from xml -e '.User.Name' -r

# Combine: UTL-X converts XML to JSON, jq processes further
cat complex.xml | utlx --from xml --to json | jq '.Orders[] | select(.total > 100)'
```

== The UTL-X jq Cheat Sheet

For developers coming from jq, here's how common patterns translate:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Task*], [*jq*], [*UTL-X*],
  [Extract field], [`jq '.name'`], [`utlx -e '.name'`],
  [Raw output], [`jq -r '.name'`], [`utlx -e '.name' -r`],
  [Array length], [`jq '. | length'`], [`utlx -e 'count(.)'`],
  [Map array], [`jq '[.[] | .name]'`], [`utlx -e 'map(., (x) -> x.name)'`],
  [Filter array], [`jq '[.[] | select(.active)]'`], [`utlx -e 'filter(., (x) -> x.active)'`],
  [Sort], [`jq 'sort_by(.name)'`], [`utlx -e 'sortBy(., (x) -> x.name)'`],
  [Keys], [`jq 'keys'`], [`utlx -e 'keys(.)'`],
  [Sum], [`jq '[.[].price] | add'`], [`utlx -e 'sum(map(., (x) -> x.price))'`],
  [Compact], [`jq -c '.'`], [`utlx -e '.' --no-pretty`],
)

The key difference: every UTL-X command above also works with `--from xml`, `--from csv`, or `--from yaml`. jq is JSON-only.

== When to Use Which Mode

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Situation*], [*Use*],
  [Quick field extraction], [`utlx -e '.field' -r`],
  [Format conversion (no logic)], [`cat data.xml | utlx` (identity)],
  [Format conversion (explicit target)], [`cat data.xml | utlx --to yaml`],
  [Complex transformation with reuse], [`utlx transform script.utlx input.xml`],
  [Shell pipeline], [`some-cmd | utlx -e '...' -r | next-cmd`],
  [CI/CD validation], [`utlx -e 'if (...) "ok" else "fail"' -r`],
  [Production service], [Not CLI — use `utlxe` (see Chapter 6)],
)

The CLI (`utlx`) is for humans and scripts. The engine (`utlxe`) is for production services. They share the same language — a transformation that works in `-e` mode works identically in `utlxe`.

== Performance Notes

The native binary (GraalVM) is critical for CLI usage:

- *Startup*: < 10ms (native) vs ~250ms (JVM JAR)
- *For single invocations*: startup time dominates — native is 25x faster end-to-end
- *For shell loops*: `for f in *.xml; do utlx ...; done` — native processes ~100 files/second, JVM ~4 files/second (startup penalty per invocation)
- *For pipes*: `cat data | utlx -e '...'` — native feels instant, JVM has noticeable delay

If you're using UTL-X in scripts or CI/CD, always use the native binary. The JVM JAR is for development only (when you need debugging or hot-reload).
