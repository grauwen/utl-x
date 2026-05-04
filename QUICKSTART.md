# UTL-X Quick Start

Get started with UTL-X in 5 minutes.

## What is UTL-X?

UTL-X is a format-agnostic transformation language. Convert between XML, JSON, CSV, YAML, and more — or write transformation scripts for complex data mapping.

- **Tier 1 — Data formats:** XML, JSON, CSV, YAML, OData
- **Tier 2 — Schema formats:** XSD, JSCH (JSON Schema), Avro, Protobuf, OSCH (OData/EDMX), TSCH (Table Schema)
- **652 stdlib functions** across 18 categories

## Installation

### Build from Source

```bash
# Prerequisites: JDK 17+, Git
git clone https://github.com/grauwen/utl-x.git
cd utl-x
./gradlew :modules:cli:jar

# Verify
./utlx --version
# UTL-X CLI v1.1.0
```

### Native Binary (Optional)

For instant startup (<10ms) without JVM dependency:

```bash
# Requires GraalVM
./gradlew :modules:cli:nativeCompile
# Binary at: modules/cli/build/native/nativeCompile/utlx
```

See [Native Binary Quick Start](docs/getting-started/native-binary-quickstart.md) for details.

## Instant Format Conversion (No Script Needed)

The fastest way to use UTL-X — just pipe your data:

```bash
# XML to JSON (auto-detected)
cat data.xml | ./utlx

# JSON to XML (auto-detected)
cat data.json | ./utlx

# CSV to JSON (auto-detected)
cat data.csv | ./utlx

# Override output format with --to
cat data.xml | ./utlx --to yaml

# Explicit input and output
cat data.csv | ./utlx --from csv --to xml
```

UTL-X detects the input format and picks the most useful output: XML flips to JSON, JSON flips to XML, everything else defaults to JSON. Use `--to` to override.

## Script-Based Transformation

For more control, write a transformation script.

### 1. Create Input Data

**input.xml:**
```xml
<Order id="ORD-001">
    <Customer>Alice Johnson</Customer>
    <Items>
        <Item sku="WIDGET-01" price="50.00" quantity="2"/>
        <Item sku="GADGET-02" price="75.00" quantity="1"/>
    </Items>
</Order>
```

### 2. Create Transformation Script

**transform.utlx:**
```utlx
%utlx 1.0
input xml
output json
---
{
    orderId: $input.Order.@id,
    customer: $input.Order.Customer,
    items: $input.Order.Items.Item |> map(item => {
        sku: item.@sku,
        price: parseNumber(item.@price),
        quantity: parseNumber(item.@quantity),
        subtotal: parseNumber(item.@price) * parseNumber(item.@quantity)
    }),
    total: sum($input.Order.Items.Item |> map(item =>
        parseNumber(item.@price) * parseNumber(item.@quantity)
    ))
}
```

### 3. Run

```bash
./utlx transform transform.utlx input.xml -o output.json
```

Or shorter (implicit transform):
```bash
./utlx transform.utlx input.xml -o output.json
```

### 4. Result

**output.json:**
```json
{
  "orderId": "ORD-001",
  "customer": "Alice Johnson",
  "items": [
    {
      "sku": "WIDGET-01",
      "price": 50.0,
      "quantity": 2,
      "subtotal": 100.0
    },
    {
      "sku": "GADGET-02",
      "price": 75.0,
      "quantity": 1,
      "subtotal": 75.0
    }
  ],
  "total": 175.0
}
```

## More CLI Commands

```bash
# Validate script syntax
./utlx validate transform.utlx

# Lint for code quality
./utlx lint transform.utlx

# List all 652 stdlib functions
./utlx functions

# Search functions
./utlx functions search xml

# Interactive REPL
./utlx repl
```

## Next Steps

- [Your First Transformation](docs/getting-started/your-first-transformation.md) — step-by-step tutorial
- [Basic Concepts](docs/getting-started/basic-concepts.md) — core language concepts
- [Quick Reference](docs/getting-started/quick-reference.md) — syntax cheat sheet
- [Stdlib Reference](docs/stdlib/stdlib-complete-reference.md) — all 652 functions
- [Examples](docs/examples/) — real-world transformations

## License

Dual-licensed:
- **Open Source:** GNU AGPL v3.0
- **Commercial:** Contact licensing@glomidco.com
