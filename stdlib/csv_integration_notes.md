# CSV Functions Integration with Existing Stdlib

## Architecture Overview

The CSV advanced functions build on top of the existing SerializationFunctions module, creating a **two-layer architecture**:

```
┌─────────────────────────────────────────────┐
│   CSV Advanced Functions (NEW)              │
│   - rows(), columns(), column(), row()      │
│   - pivot(), transpose(), groupBy()         │
│   - vlookup(), join(), etc.                 │
│                                             │
│   Location: stdlib/csv/CSVFunctions.kt      │
└─────────────────┬───────────────────────────┘
                  │ uses
                  ↓
┌─────────────────────────────────────────────┐
│   Serialization Functions (EXISTING)        │
│   - parseCsv(csv: String, delimiter)        │
│   - renderCsv(udm: UDM, delimiter)          │
│                                             │
│   Location: stdlib/serialization/           │
│              SerializationFunctions.kt      │
└─────────────────────────────────────────────┘
```

## Critical Distinction: When parseCsv() IS and ISN'T Needed

### ❌ NOT NEEDED: Primary CSV Input

```utlx
%utlx 1.0
input csv  # ← CSV format parser handles this automatically
output json
---
{
  // Input is ALREADY parsed UDM structure
  // { headers: [...], rows: [{...}, ...] }
  
  allRows: rows(input),           // ✅ Correct
  columns: columns(input),        // ✅ Correct
  firstRow: row(input, 0)         // ✅ Correct
}
```

**The CSV format parser automatically converts:**
```csv
name,age,city
Alice,30,NYC
Bob,25,LA
```

**Into UDM structure:**
```javascript
{
  headers: ["name", "age", "city"],
  rows: [
    { name: "Alice", age: "30", city: "NYC" },
    { name: "Bob", age: "25", city: "LA" }
  ]
}
```

### ✅ NEEDED: Embedded CSV Strings

**Scenario 1: CSV embedded in XML**
```utlx
%utlx 1.0
input xml
output json
---
{
  // XML structure:
  // <Document>
  //   <Metadata>...</Metadata>
  //   <DataTable>name,age\nAlice,30\nBob,25</DataTable>
  // </Document>
  
  // DataTable is a STRING, not parsed
  let csvString = input.Document.DataTable,
  
  // NOW we need parseCsv
  let tableData = parseCsv(csvString),
  
  processedData: rows(tableData)
}
```

**Scenario 2: CSV embedded in JSON**
```utlx
%utlx 1.0
input json
output json
---
{
  // JSON structure:
  // {
  //   "metadata": {...},
  //   "csvPayload": "name,age\nAlice,30\nBob,25"
  // }
  
  // csvPayload is a STRING
  let csvString = input.csvPayload,
  
  // Parse the embedded CSV string
  let tableData = parseCsv(csvString),
  
  analysis: {
    rowCount: rows(tableData).length,
    columns: columns(tableData)
  }
}
```

**Scenario 3: API response with nested CSV**
```utlx
%utlx 1.0
input json
output json
---
{
  // API returns:
  // {
  //   "report_id": "123",
  //   "data_format": "csv",
  //   "data": "product,sales,region\nLaptop,1500,US\nPhone,800,EU"
  // }
  
  reportId: input.report_id,
  
  // Parse the CSV string from the "data" field
  let reportData = parseCsv(input.data),
  
  summary: {
    productCount: rows(reportData).length,
    totalSales: column(reportData, "sales") 
      |> map(v => parseFloat(v)) 
      |> sum()
  }
}
```

---

## Data Structure Contract

### parseCsv() Returns:

```kotlin
UDM.Object(
  properties = mapOf(
    "headers" to UDM.Array([
      UDM.String("name"),
      UDM.String("age"),
      UDM.String("city")
    ]),
    "rows" to UDM.Array([
      UDM.Object(properties = mapOf(
        "name" to UDM.String("Alice"),
        "age" to UDM.String("30"),
        "city" to UDM.String("NYC")
      )),
      UDM.Object(properties = mapOf(
        "name" to UDM.String("Bob"),
        "age" to UDM.String("25"),
        "city" to UDM.String("LA")
      ))
    ])
  )
)
```

### CSV Advanced Functions Accept:

1. **UDM.String** (raw CSV) - will call `parseCsv()` internally
2. **UDM.Object** (already parsed) - expects structure above

This allows flexible usage:
```utlx
// Option 1: Pass raw CSV string
rows(input)  // input is CSV string

// Option 2: Parse once, reuse
let parsed = parseCsv(input)
rows(parsed)
columns(parsed)
column(parsed, "age")
```

---

## Function Dependencies

### CSVFunctions.kt Imports:

```kotlin
package org.apache.utlx.stdlib.csv

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.serialization.parseCsv   // ← Use existing
import org.apache.utlx.stdlib.serialization.renderCsv  // ← Use existing

// Internal helper to convert UDM to CSVData
private fun udmToCSVData(udm: UDM): CSVData {
    return when (udm) {
        is UDM.Object -> {
            val headers = (udm.properties["headers"] as UDM.Array).elements
                .map { (it as UDM.String).value }
            val rows = (udm.properties["rows"] as UDM.Array).elements
                .map { row ->
                    (row as UDM.Object).properties.mapValues { (_, v) ->
                        (v as UDM.String).value
                    }
                }
            CSVData(headers, rows)
        }
        else -> throw IllegalArgumentException("Expected CSV object")
    }
}

// Use existing parseCsv
private fun parseCSVString(csvString: String, delimiter: String = ","): CSVData {
    val udm = parseCsv(csvString, delimiter)
    return udmToCSVData(udm)
}
```

---

## Usage Patterns

### Pattern 1: Parse Once, Use Many Times (Recommended)

```utlx
%utlx 1.0
input csv
output json
---
{
  // Parse once
  let csvData = parseCsv(input),
  
  // Reuse parsed data
  totalRows: rows(csvData).length,
  columnNames: columns(csvData),
  ageValues: column(csvData, "age"),
  firstRow: row(csvData, 0),
  aliceAge: cell(csvData, 0, "age")
}
```

**Why?** Avoids re-parsing the same CSV multiple times (more efficient)

### Pattern 2: Direct Operations on String

```utlx
%utlx 1.0
input csv
output json
---
{
  // Functions parse internally as needed
  summary: {
    rows: rows(input),
    columns: columns(input)
  },
  
  transformed: {
    pivoted: pivot(input, "region", "quarter", "sales"),
    grouped: groupBy(input, ["category"], { total: "sum" })
  }
}
```

**Why?** Simpler syntax for one-off operations

### Pattern 3: Mixed Approach

```utlx
%utlx 1.0
input json {
  customers: "customers.csv",
  orders: "orders.csv"
}
output json
---
{
  // Parse both CSVs once
  let customerData = parseCsv(input.customers),
  let orderData = parseCsv(input.orders),
  
  // Use parsed data
  customerCount: rows(customerData).length,
  orderCount: rows(orderData).length,
  
  // Join (function handles either string or parsed)
  enrichedOrders: join(
    orderData,
    customerData,
    "customer_id",
    "customer_id",
    "left"
  )
}
```

---

## Function Registration in Functions.kt

### Serialization Functions (Already Registered)

```kotlin
// In stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt

// Serialization Functions (already exist)
register("parseCsv") { args ->
    requireString(args[0], "parseCsv")
    val delimiter = (args.getOrNull(1) as? UDM.String)?.value ?: ","
    SerializationFunctions.parseCsv((args[0] as UDM.String).value, delimiter)
}

register("renderCsv") { args ->
    val delimiter = (args.getOrNull(1) as? UDM.String)?.value ?: ","
    UDM.String(SerializationFunctions.renderCsv(args[0], delimiter))
}
```

### CSV Advanced Functions (NEW - To Add)

```kotlin
// NEW CSV Advanced Functions
register("rows") { args ->
    CSVFunctions.rows(args[0])
}

register("columns") { args ->
    CSVFunctions.columns(args[0])
}

register("column") { args ->
    requireString(args[1], "column")
    CSVFunctions.column(args[0], (args[1] as UDM.String).value)
}

register("row") { args ->
    requireNumber(args[1], "row")
    CSVFunctions.row(args[0], (args[1] as UDM.Number).value.toInt())
}

register("cell") { args ->
    requireNumber(args[1], "cell")
    requireString(args[2], "cell")
    CSVFunctions.cell(
        args[0],
        (args[1] as UDM.Number).value.toInt(),
        (args[2] as UDM.String).value
    )
}

// ... (register remaining 25 functions)
```

---

## Module Structure

```
stdlib/
├── src/main/kotlin/org/apache/utlx/stdlib/
│   ├── Functions.kt                    # Central registry
│   │
│   ├── serialization/
│   │   └── SerializationFunctions.kt   # EXISTING
│   │       ├── parseCsv()              # ✅ Already exists
│   │       ├── renderCsv()             # ✅ Already exists
│   │       ├── parseJson()
│   │       ├── renderJson()
│   │       └── ...
│   │
│   └── csv/                            # NEW MODULE
│       ├── CSVFunctions.kt             # ✅ New advanced functions
│       │   ├── rows()
│       │   ├── columns()
│       │   ├── column()
│       │   ├── row()
│       │   ├── cell()
│       │   ├── transpose()
│       │   ├── pivot()
│       │   ├── unpivot()
│       │   ├── groupBy()
│       │   ├── summarize()
│       │   ├── vlookup()
│       │   ├── indexMatch()
│       │   ├── join()
│       │   └── ... (30 total)
│       │
│       └── CSVHelpers.kt               # Optional utilities
│
└── src/test/kotlin/org/apache/utlx/stdlib/
    ├── serialization/
    │   └── SerializationFunctionsTest.kt  # Existing tests
    │
    └── csv/
        └── CSVFunctionsTest.kt            # New tests
```

---

## Design Decisions

### Why Not Merge Into SerializationFunctions?

**Reason 1: Separation of Concerns**
- SerializationFunctions: Parse/Serialize (I/O)
- CSVFunctions: Operations/Transformations (Business Logic)

**Reason 2: Size Management**
- SerializationFunctions already handles 4+ formats (JSON, XML, YAML, CSV)
- Adding 30 CSV-specific functions would make it too large

**Reason 3: Optional Dependencies**
- Users who just need CSV parsing don't need 30 spreadsheet functions
- Keeps modules focused and maintainable

### Why Not Create Separate Parser?

**Decision:** Reuse `parseCsv()` from SerializationFunctions

**Benefits:**
1. ✅ No code duplication
2. ✅ Consistent CSV parsing logic
3. ✅ Single source of truth for CSV structure
4. ✅ Easier maintenance
5. ✅ Smaller codebase

---

## Migration Guide

### For Users Currently Using parseCsv()

**No Changes Needed!** The existing `parseCsv()` and `renderCsv()` work exactly the same.

**New Capabilities Available:**
```utlx
// Before: Manual operations
let parsed = parseCsv(input)
let headers = parsed.headers
let firstRow = parsed.rows[0]
let nameColumn = parsed.rows |> map(row => row.name)

// After: Use dedicated functions
let parsed = parseCsv(input)
let headers = columns(parsed)        # Cleaner
let firstRow = row(parsed, 0)        # Cleaner  
let nameColumn = column(parsed, "name")  # Much cleaner!
```

### For New Users

**Recommended Pattern:**
```utlx
%utlx 1.0
input csv
output json
---
{
  // 1. Parse CSV (use existing function)
  let csvData = parseCsv(input),
  
  // 2. Use advanced functions
  summary: {
    totalRows: rows(csvData).length,
    columns: columns(csvData)
  },
  
  // 3. Transform data
  enriched: csvData 
    |> filterRows { row => row.status == "active" }
    |> sortBy(["date"], [false])
    |> take(10)
}
```

---

## Testing Strategy

### SerializationFunctions Tests (Existing)

Focus on parse/serialize correctness:
- Various CSV formats (quoted fields, escaped delimiters)
- Different delimiters (`,`, `;`, `\t`)
- Edge cases (empty fields, newlines in quotes)
- Performance (large files)

### CSVFunctions Tests (New)

Focus on operations correctness:
- Structure access (rows, columns, cells)
- Transformations (pivot, transpose)
- Aggregations (groupBy, summarize)
- Joins (vlookup, indexMatch, join)
- Column operations
- Filter & sort

**Integration Tests:**
```kotlin
@Test
fun `test CSV pipeline using both modules`() {
    val csvString = "name,age\nAlice,30\nBob,25"
    
    // Use existing parseCsv
    val parsed = parseCsv(csvString)
    
    // Use new CSV functions
    val rows = CSVFunctions.rows(parsed)
    assertEquals(2, rows.elements.size)
    
    val ages = CSVFunctions.column(parsed, "age")
    assertEquals(listOf("30", "25"), ages.elements.map { (it as UDM.String).value })
}
```

---

## Documentation Updates Needed

### 1. Update stdlib-reference.md

Add new section:
```markdown
## CSV Functions

### Structure Access
- `rows(csv)` - Get all rows as array of objects
- `columns(csv)` - Get column names
- ...

### Transformations  
- `pivot(csv, row, col, val)` - Create pivot table
- ...
```

### 2. Create csv-functions-guide.md

Complete usage guide with examples (already created)

### 3. Update SerializationFunctions docs

Add note:
```markdown
## CSV Parsing

`parseCsv(csv: String, delimiter?: String)` parses CSV to UDM.

For advanced CSV operations (pivot, groupBy, vlookup, etc.), 
see [CSV Functions Guide](../csv/csv-functions-guide.md).
```

---

## Summary

✅ **CSV Advanced Functions integrate cleanly with existing SerializationFunctions**

**Key Points:**
1. Reuses existing `parseCsv()` and `renderCsv()` (no duplication)
2. Two-layer architecture: I/O layer + Operations layer
3. Flexible usage: direct on strings or on parsed UDM
4. No breaking changes to existing code
5. Clear separation of concerns

**Ready for Implementation:**
- ✅ Updated CSVFunctions.kt to use existing parseCsv
- ✅ Integration documentation complete
- ✅ No conflicts with existing stdlib
- ✅ Clear module boundaries

**Next Step:** Review and approve for implementation
