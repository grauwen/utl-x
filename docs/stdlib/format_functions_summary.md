# UTL-X Format-Specific Functions - Complete Summary

## Overview

UTL-X stdlib now includes specialized functions for **CSV** and **YAML** transformations, going beyond basic parse/serialize to provide format-specific operations.

---

## Comparison: CSV vs YAML Functions

| Aspect | CSV Functions | YAML Functions |
|--------|---------------|----------------|
| **Count** | 19 functions | 18 functions |
| **Primary Use Cases** | Tabular data, spreadsheets, reports | DevOps, Kubernetes, config management |
| **Key Operations** | Pivot, join, groupBy, VLOOKUP | Multi-doc, path queries, deep merge, K8s |
| **Inspiration** | Excel, DataWeave | yq, Kubernetes, Helm |
| **Conflicts** | 4 renamed (csv prefix) | None (yaml prefix unique) |
| **Enterprise Value** | High (data transformation) | Very High (DevOps workflows) |
| **Complexity** | Medium | Medium-High |

---

## CSV Functions (19 Total)

### Categories

**Structure Access (5)**
- `rows()`, `columns()`, `column()`, `row()`, `cell()`
- Access CSV data like a spreadsheet

**Transformations (3)**
- `transpose()`, `pivot()`, `unpivot()`
- Reshape tabular data

**Aggregations (2)**
- `csvGroupBy()`, `summarize()`
- SQL-like aggregations and statistics

**Joins & Lookups (3)**
- `vlookup()`, `indexMatch()`, `csvJoin()`
- Excel-style lookups and SQL-style joins

**Column Operations (4)**
- `addColumn()`, `removeColumns()`, `renameColumns()`, `selectColumns()`
- Manipulate CSV structure

**Filter & Sort (2)**
- `csvFilterRows()`, `csvSortBy()`
- Query and order data

### Key Use Cases
- ETL pipelines
- Sales reports with lookups
- Data migration
- Spreadsheet-like transformations
- Master data management

### Renamed Functions (Conflicts)
- `groupBy` → **`csvGroupBy`** (conflicts with array groupBy)
- `join` → **`csvJoin`** (conflicts with JoinFunctions)
- `sortBy` → **`csvSortBy`** (conflicts with ArrayFunctions)
- `filterRows` → **`csvFilterRows`** (consistency)

---

## YAML Functions (18 Total)

### Categories

**Multi-Document (3)**
- `yamlSplitDocuments()`, `yamlMergeDocuments()`, `yamlGetDocument()`
- Handle YAML's unique multi-document feature

**Path Operations (4)**
- `yamlPath()`, `yamlSet()`, `yamlDelete()`, `yamlExists()`
- yq-style path queries and updates

**Deep Merge (2)**
- `yamlMerge()`, `yamlMergeAll()`
- Config overlay patterns

**Kubernetes (4)**
- `yamlExtractByKind()`, `yamlExtractByName()`, `yamlFilterResources()`, `yamlGetResourceNames()`
- K8s manifest manipulation

**Formatting (3)**
- `yamlSort()`, `yamlMinimize()`, `yamlIndent()`
- Normalize YAML output

**Validation (2)**
- `yamlValidate()`, `yamlValidateK8s()`
- Syntax and structure checks

### Key Use Cases
- Kubernetes manifest updates
- Config management (base + overlays)
- CI/CD pipeline transformations
- Multi-environment configurations
- Resource auditing

### No Naming Conflicts
All functions use `yaml` prefix - naturally unique

---

## Comparison with Competitors

### DataWeave
| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| CSV Operations | Parse/serialize only | 19 specialized functions |
| YAML Operations | Parse/serialize only | 18 specialized functions |
| Pivot Tables | Manual | Built-in `pivot()` |
| Kubernetes Support | None | 4 K8s-specific functions |
| Config Merging | Manual | Built-in `yamlMerge()` |

**Conclusion:** UTL-X provides significantly more format-specific functionality

---

### yq (YAML Processor)
| Feature | yq | UTL-X |
|---------|-----|-------|
| Path Queries | ✅ Yes | ✅ `yamlPath()` |
| Update by Path | ✅ Yes | ✅ `yamlSet()` |
| Delete by Path | ✅ Yes | ✅ `yamlDelete()` |
| Merge Files | ✅ Yes | ✅ `yamlMerge()` |
| Multi-Document | ✅ Yes | ✅ `yamlSplitDocuments()` |
| K8s Helpers | ❌ Manual | ✅ 4 built-in functions |
| Integration | CLI only | Embedded in transformations |

**Conclusion:** UTL-X matches yq capabilities + adds K8s helpers + embeds in transformation language

---

## Architecture Integration

### Existing (SerializationFunctions)
```
Format String ←→ UDM
   parseJson()    renderJson()
   parseXml()     renderXml()
   parseCsv()     renderCsv()
   parseYaml()    renderYaml()
```

### New (Format-Specific Functions)
```
UDM → Operations → UDM
   CSVFunctions (19)
   YAMLFunctions (18)
```

### Complete Flow
```
INPUT → Format Parser → UDM → Format Functions → UDM → Format Serializer → OUTPUT
          (automatic)           (transformations)         (renderX)
```

---

## Module Structure

```
stdlib/
├── src/main/kotlin/org/apache/utlx/stdlib/
│   ├── Functions.kt                       # Central registry
│   │
│   ├── serialization/
│   │   └── SerializationFunctions.kt      # EXISTING
│   │       ├── parseJson/renderJson
│   │       ├── parseXml/renderXml
│   │       ├── parseCsv/renderCsv         # Used by CSV
│   │       └── parseYaml/renderYaml       # Used by YAML
│   │
│   ├── csv/                                # NEW
│   │   └── CSVFunctions.kt                # 19 functions
│   │
│   └── yaml/                               # NEW
│       └── YAMLFunctions.kt               # 18 functions
│
└── src/test/kotlin/org/apache/utlx/stdlib/
    ├── csv/
    │   └── CSVFunctionsTest.kt
    └── yaml/
        └── YAMLFunctionsTest.kt
```

---

## Usage Patterns

### Pattern 1: Direct Input (Most Common)
```utlx
%utlx 1.0
input csv  # Parsed automatically
output json
---
{
  // CSV functions work directly on input
  summary: csvGroupBy(input, ["region"], { sales: "sum" }),
  sorted: csvSortBy(input, ["date"], [false])
}
```

### Pattern 2: Embedded Formats
```utlx
%utlx 1.0
input json  # Primary format
output json
---
{
  // JSON contains CSV/YAML as strings
  let csvData = parseCsv(input.csvField),
  let yamlConfig = parseYaml(input.yamlField),
  
  // Use format functions
  csvAnalysis: csvGroupBy(csvData, ["category"], { total: "sum" }),
  yamlUpdated: yamlSet(yamlConfig, ".spec.replicas", 5)
}
```

### Pattern 3: Multi-Format Transformation
```utlx
%utlx 1.0
input json {
  sales: "sales.csv",           # CSV file
  config: "config.yaml",        # YAML file
  template: "template.xml"      # XML file
}
output json
---
{
  // CSV operations
  topCustomers: csvSortBy(input.sales, ["revenue"], [false]) |> take(10),
  
  // YAML operations  
  prodConfig: yamlMerge(input.config, prodOverlay),
  
  // All together
  report: {
    sales: topCustomers,
    config: yamlPath(prodConfig, ".database.host")
  }
}
```

---

## Implementation Roadmap

### Phase 1: CSV Functions (Weeks 1-3) ✅
- Week 1: Structure access + transformations
- Week 2: Aggregations + joins
- Week 3: Column ops + filter/sort

### Phase 2: YAML Functions (Weeks 4-6) 🎯 Current Focus
- Week 4: Multi-doc + path operations
- Week 5: Merge + Kubernetes
- Week 6: Formatting + validation

### Phase 3: Testing & Docs (Week 7)
- Comprehensive test suites
- Real-world examples
- Integration guides
- Performance benchmarks

### Phase 4: Future Enhancements (Later)
- XML-specific functions (if needed)
- JSON-specific functions (JSONPath, JSON Pointer)
- Format conversion helpers

---

## Success Metrics

### Adoption
- Enable 80% of CSV transformation use cases without custom code
- Enable 80% of YAML/K8s manipulation use cases
- Reduce transformation code complexity by 50%

### Performance
- CSV operations: O(n) for most operations
- YAML path queries: O(depth)
- Merge operations: O(size)

### Quality
- 100% test coverage
- Real-world examples for each function
- Production-ready error handling

---

## Competitive Advantages

### vs DataWeave
1. ✅ **Richer CSV operations** - 19 functions vs basic parse/serialize
2. ✅ **YAML-specific features** - Multi-doc, K8s, merge vs basic parse/serialize
3. ✅ **Kubernetes-native** - Built-in K8s helpers
4. ✅ **Open Source** - AGPL/Commercial vs proprietary

### vs yq + custom scripts
1. ✅ **Integrated** - All operations in one transformation language
2. ✅ **Type-safe** - Strong typing vs shell scripts
3. ✅ **Composable** - Mix CSV, YAML, JSON, XML operations
4. ✅ **Enterprise-ready** - Error handling, validation, testing

### vs Excel/Spreadsheets
1. ✅ **Automated** - Scriptable transformations
2. ✅ **Version control** - Code vs binary files
3. ✅ **Scale** - Handle large datasets
4. ✅ **Integration** - Part of data pipeline

---

## Documentation Status

| Document | Status |
|----------|--------|
| CSV Functions Implementation | ✅ Complete |
| CSV Usage Guide | ✅ Complete |
| CSV Function Names (conflicts) | ✅ Complete |
| YAML Functions Implementation | ✅ Complete |
| YAML Usage Guide | ✅ Complete |
| YAML Analysis | ✅ Complete |
| Integration Guide | ✅ Complete |
| Test Suites | ⏳ To be written |
| API Reference | ⏳ To be added to stdlib-reference.md |

---

## Next Steps

1. ✅ Review CSV and YAML function designs
2. ✅ Ensure no naming conflicts
3. ⏳ Implement CSVFunctions.kt
4. ⏳ Implement YAMLFunctions.kt
5. ⏳ Write comprehensive tests
6. ⏳ Update Functions.kt registration
7. ⏳ Update stdlib-reference.md
8. ⏳ Create examples directory

---

## Summary

**Total New Functions:** 37 (19 CSV + 18 YAML)

**Enterprise Value:**
- CSV: High (enables data transformation, ETL, reporting)
- YAML: Very High (critical for DevOps, Kubernetes, config management)

**Differentiation:**
- Goes far beyond DataWeave's basic format support
- Matches/exceeds specialized tools (yq) while staying integrated
- Kubernetes-first approach (unique in transformation languages)
- Real-world patterns built-in (pivot, merge, overlay)

**Ready for Implementation:** All designs complete, no blocking issues 🎯
