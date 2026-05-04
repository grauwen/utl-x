# 25 Real-World jq Examples — Now in UTL-X

Common jq one-liners used in shell scripts, shown side-by-side with UTL-X equivalents. Every UTL-X example can be copied and run directly.

---

## 1. Pretty-print JSON

```bash
# jq
cat data.json | jq .

# utlx (identity mode — auto-detects JSON, flips to XML by default)
cat data.json | utlx --to json
```

---

## 2. Extract a single field

```bash
# jq
echo '{"name":"Alice","age":30}' | jq '.name'

# utlx
echo '{"name":"Alice","age":30}' | utlx -e '$input.name'
```

---

## 3. Extract nested field

```bash
# jq
curl -s https://api.example.com/user | jq '.data.profile.email'

# utlx
curl -s https://api.example.com/user | utlx -e '$input.data.profile.email'
```

---

## 4. Get array length

```bash
# jq
echo '{"items":[1,2,3,4,5]}' | jq '.items | length'

# utlx
echo '{"items":[1,2,3,4,5]}' | utlx -e 'count($input.items)'
```

---

## 5. Select first element from array

```bash
# jq
echo '[{"id":1},{"id":2},{"id":3}]' | jq '.[0]'

# utlx
echo '[{"id":1},{"id":2},{"id":3}]' | utlx -e '$input[0]'
```

---

## 6. Filter array by condition

```bash
# jq
cat orders.json | jq '[.[] | select(.status == "active")]'

# utlx
cat orders.json | utlx -e '$input |> filter(item => item.status == "active")'
```

---

## 7. Map/transform array elements

```bash
# jq
cat users.json | jq '[.[] | {name: .name, email: .email}]'

# utlx
cat users.json | utlx -e '$input |> map(u => {name: u.name, email: u.email})'
```

---

## 8. Sum values in array

```bash
# jq
echo '{"items":[{"price":10},{"price":20},{"price":30}]}' | jq '[.items[].price] | add'

# utlx
echo '{"items":[{"price":10},{"price":20},{"price":30}]}' | utlx -e 'sum($input.items |> map(i => i.price))'
```

---

## 9. Count items matching condition

```bash
# jq
cat events.json | jq '[.[] | select(.type == "error")] | length'

# utlx
cat events.json | utlx -e 'count($input |> filter(e => e.type == "error"))'
```

---

## 10. Sort array by field

```bash
# jq
cat products.json | jq 'sort_by(.price)'

# utlx
cat products.json | utlx -e '$input |> sortBy(p => p.price)'
```

---

## 11. Get unique values

```bash
# jq
echo '["a","b","a","c","b"]' | jq 'unique'

# utlx
echo '["a","b","a","c","b"]' | utlx -e 'distinct($input)'
```

---

## 12. Flatten nested arrays

```bash
# jq
echo '[[1,2],[3,4],[5,6]]' | jq 'flatten'

# utlx
echo '[[1,2],[3,4],[5,6]]' | utlx -e 'flatten($input)'
```

---

## 13. Group by field

```bash
# jq
cat orders.json | jq 'group_by(.region)'

# utlx
cat orders.json | utlx -e '$input |> groupBy(o => o.region)'
```

---

## 14. Convert JSON to CSV (extract fields)

```bash
# jq
cat users.json | jq -r '.[] | [.name, .email, .age] | @csv'

# utlx (outputs proper CSV with headers)
cat users.json | utlx -e '$input |> map(u => {name: u.name, email: u.email, age: u.age})' --to csv
```

---

## 15. JSON to XML

```bash
# jq — CANNOT DO THIS

# utlx (smart flip: JSON auto-converts to XML)
cat data.json | utlx
```

---

## 16. XML to JSON

```bash
# jq — CANNOT DO THIS

# utlx (smart flip: XML auto-converts to JSON)
cat data.xml | utlx
```

---

## 17. CSV to JSON

```bash
# jq — CANNOT DO THIS

# utlx
cat data.csv | utlx --from csv --to json
```

---

## 18. Merge two JSON objects

```bash
# jq
echo '{"a":1}' | jq --argjson b '{"b":2}' '. + $b'

# utlx (using spread operator)
echo '{"base":{"a":1},"extra":{"b":2}}' | utlx -e '{...$input.base, ...$input.extra}'
```

---

## 19. Conditional field extraction

```bash
# jq
cat orders.json | jq '.[] | if .total > 100 then .id else empty end'

# utlx
cat orders.json | utlx -e '$input |> filter(o => o.total > 100) |> map(o => o.id)'
```

---

## 20. String manipulation (uppercase)

```bash
# jq
echo '{"name":"alice"}' | jq '.name | ascii_upcase'

# utlx
echo '{"name":"alice"}' | utlx -e 'upper($input.name)'
```

---

## 21. Date formatting from API response

```bash
# jq
echo '{"created":"2026-04-09T14:30:00Z"}' | jq '.created | split("T")[0]'

# utlx
echo '{"created":"2026-04-09T14:30:00Z"}' | utlx -e 'substring($input.created, 0, 10)'
```

---

## 22. Build a summary from array

```bash
# jq
cat sales.json | jq '{
  total: [.[].amount] | add,
  count: length,
  average: ([.[].amount] | add) / length
}'

# utlx
cat sales.json | utlx -e '{
  total: sum($input |> map(s => s.amount)),
  count: count($input),
  average: avg($input |> map(s => s.amount))
}'
```

---

## 23. Chain in a CI/CD pipeline

```bash
# jq — extract version from package.json
VERSION=$(cat package.json | jq -r '.version')

# utlx
VERSION=$(cat package.json | utlx -e '$input.version' --to json | tr -d '"')
```

---

## 24. Process API response and save

```bash
# jq
curl -s https://api.example.com/products | jq '[.data[] | {id, name, price}]' > products.json

# utlx
curl -s https://api.example.com/products | utlx -e '$input.data |> map(p => {id: p.id, name: p.name, price: p.price})' -o products.json
```

---

## 25. Convert API response to multiple formats

```bash
# jq — only JSON output possible

# utlx — one input, any output format
curl -s https://api.example.com/data > response.json

# To XML
cat response.json | utlx --to xml > data.xml

# To YAML
cat response.json | utlx --to yaml > data.yaml

# To CSV (for tabular data)
cat response.json | utlx -e '$input.records' --to csv > data.csv

# To OData
cat response.json | utlx --to odata > data.odata.json
```

---

## When UTL-X Beats jq

| Use Case | jq | utlx |
|----------|-----|------|
| JSON processing | Yes | Yes |
| XML input/output | No | Yes |
| CSV input/output | No | Yes |
| YAML input/output | No | Yes |
| OData support | No | Yes |
| Format conversion | No | `cat data.xml \| utlx` |
| 652 stdlib functions | ~50 | 652 |
| Type safety | No | Yes |
| Multi-input joins | No | Yes |
| Schema transformation | No | XSD, JSON Schema, Avro, Protobuf |

## When jq Wins

- Single binary, zero dependencies
- Faster startup (native C vs JVM)
- Raw string output (`-r` flag)
- Ubiquitous in existing scripts
- More concise for simple JSON queries

## Quick Install

```bash
# macOS
brew tap grauwen/utlx && brew install utlx

# Linux
curl -L https://github.com/grauwen/utl-x/releases/download/v1.1.0/utlx-linux-x64.bin -o utlx
chmod +x utlx && sudo mv utlx /usr/local/bin/
```
