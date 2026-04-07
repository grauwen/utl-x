# UTL-X Quick Reference

A quick cheat sheet for UTL-X syntax and CLI usage.

---

## CLI Usage

### Identity Mode (No Script Needed)

```bash
cat data.xml | utlx                # XML to JSON (smart flip)
cat data.json | utlx               # JSON to XML (smart flip)
cat data.csv | utlx                # CSV to JSON
cat data.xml | utlx --to yaml     # Override output format
cat data.csv | utlx --from csv --to xml  # Explicit both
```

### Script-Based Transformation

```bash
utlx transform script.utlx input.xml              # Transform
utlx script.utlx input.xml                         # Implicit transform
utlx transform script.utlx input.xml -o out.json   # Save to file
utlx transform script.utlx < input.xml             # Read from stdin
```

### Other Commands

```bash
utlx --version                     # Version
utlx --help                        # Help
utlx validate script.utlx          # Validate syntax
utlx lint script.utlx              # Check code quality
utlx functions                     # List all 652 stdlib functions
utlx functions search xml          # Search functions
utlx functions info map            # Function details
utlx repl                          # Interactive REPL
```

---

## Script Structure

```utlx
%utlx 1.0
input <format>
output <format>
---
<transformation>
```

### Supported Formats

| Format | Keyword | Type |
|--------|---------|------|
| JSON | `json` | Data |
| XML | `xml` | Data |
| CSV | `csv` | Data |
| YAML | `yaml` | Data |
| OData | `odata` | Data |
| Auto-detect | `auto` | Data |
| XSD | `xsd` | Schema |
| JSON Schema | `jsch` | Schema |
| Avro | `avro` | Schema |
| Protobuf | `proto` | Schema |
| OData/EDMX | `osch` | Schema |
| Table Schema | `tsch` | Schema |

---

## Data Types

| Type | Example |
|------|---------|
| **String** | `"Hello"`, `'World'` |
| **Number** | `42`, `3.14`, `1.5e10` |
| **Boolean** | `true`, `false` |
| **Null** | `null` |
| **Array** | `[1, 2, 3]` |
| **Object** | `{name: "Alice", age: 30}` |

---

## Selectors

```utlx
$input.root.child              // Navigate path
$input.item.@attribute         // Access XML attribute
$input.items[0]                // Index access
$input.items[*]                // All elements
$input..recursive              // Recursive search
$input.items[price > 100]      // Filter with predicate
```

---

## Operators

### Arithmetic
```utlx
+  -  *  /  %  **
```

### Comparison
```utlx
==  !=  >  >=  <  <=
```

### Logical
```utlx
&&  ||  !
```

### Pipeline
```utlx
|>   // Chain operations
```

### Null-safe
```utlx
??   // Nullish coalescing
?.   // Safe navigation
```

---

## Control Flow

### If-Else
```utlx
if (condition) value1 else value2
```

### Match
```utlx
match expression {
  pattern1 => result1,
  pattern2 => result2,
  _ => default
}
```

### Ternary
```utlx
condition ? value1 : value2
```

---

## Variables

```utlx
{
  let x = 10,
  let y = 20,
  sum: x + y
}
```

---

## Functions

### User-Defined (PascalCase required)

```utlx
function CalculateTotal(price: Number, qty: Number): Number {
  price * qty
}
```

### Common Stdlib Functions

**Aggregation:**
```utlx
sum(array)  avg(array)  min(array)  max(array)  count(array)
```

**String:**
```utlx
upper(str)  lower(str)  trim(str)  concat(str1, str2)
split(str, delim)  join(array, delim)  replace(str, old, new)
```

**Array:**
```utlx
map(array, fn)  filter(array, fn)  reduce(array, fn, init)
sort(array)  sortBy(array, fn)  first(array)  last(array)
take(array, n)  drop(array, n)  distinct(array)  flatten(array)
groupBy(array, fn)  chunk(array, n)  zip(arr1, arr2)
```

**Date:**
```utlx
now()  parseDate(str, fmt)  formatDate(date, fmt)
addDays(date, n)  diffDays(date1, date2)
```

**Type:**
```utlx
typeOf(val)  isString(val)  isNumber(val)  isArray(val)
parseNumber(str)  toString(val)  toBoolean(val)
```

See `utlx functions` for all 652 functions.

---

## Common Patterns

### Object Construction
```utlx
{
  field1: value1,
  field2: value2
}
```

### Array Mapping
```utlx
$input.items |> map(item => {
  name: item.name,
  price: item.price
})
```

### Filtering
```utlx
$input.items |> filter(item => item.price > 100)
```

### Pipeline Chaining
```utlx
$input.items
  |> filter(item => item.active)
  |> map(item => item.price)
  |> sum()
```

### Default Values
```utlx
$input.customer.name ?? "Unknown"
$input.quantity ?? 0
```

---

## Examples

### XML to JSON
```utlx
%utlx 1.0
input xml
output json
---
{
  id: $input.Order.@id,
  customer: $input.Order.Customer.Name,
  total: sum($input.Order.Items.Item.(parseNumber(@price) * parseNumber(@quantity)))
}
```

### JSON to XML
```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: $input.orderId,
    Customer: {
      Name: $input.customer.name
    }
  }
}
```

### CSV to JSON
```utlx
%utlx 1.0
input csv { headers: true }
output json
---
{
  records: $input |> map(row => {
    name: row.Name,
    age: parseNumber(row.Age)
  })
}
```

### Multi-Input
```utlx
%utlx 1.0
input: orders xml, customers json
output json
---
{
  enriched: $orders.Order |> map(order => {
    id: order.@id,
    customer: $customers[order.customerId]
  })
}
```

---

## Format Options

### CSV
```utlx
input csv {
  headers: true,
  delimiter: ","
}
```

### XML Namespaces
```utlx
input xml {
  namespaces: {
    "ns": "http://example.com"
  }
}
```

---

## Comments

```utlx
// Single-line comment

/*
 * Multi-line
 * comment
 */
```

---

## Useful Links

- [Full Language Guide](../language-guide/)
- [Examples](../examples/)
- [Stdlib Reference (652 functions)](../stdlib/stdlib-complete-reference.md)
