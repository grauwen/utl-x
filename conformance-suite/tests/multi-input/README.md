# Multi-Input Conformance Tests

This directory contains comprehensive conformance tests for UTL-X's multiple input feature, covering all permutations of 2-input scenarios.

## Test Coverage Matrix

All tests use **JSON output format** with various input format combinations:

| Test | Input 1 | Input 2 | Output | Scenario | Status |
|------|---------|---------|--------|----------|--------|
| `01_xml_xml_to_json.yaml` | XML | XML | JSON | Customer + Orders integration | ✅ |
| `02_xml_json_to_json.yaml` | XML | JSON | JSON | SAP Products + REST API Pricing | ✅ |
| `03_xml_csv_to_json.yaml` | XML | CSV | JSON | Products + Warehouse Inventory | ✅ |
| `04_xml_yaml_to_json.yaml` | XML | YAML | JSON | Services + Configuration | ✅ |
| `05_json_json_to_json.yaml` | JSON | JSON | JSON | User Profiles + Activity Data | ✅ |
| `06_json_csv_to_json.yaml` | JSON | CSV | JSON | Products + Sales Data | ✅ |
| `07_json_yaml_to_json.yaml` | JSON | YAML | JSON | Deployments + Helm Config | ✅ |
| `08_csv_csv_to_json.yaml` | CSV | CSV | JSON | Employees + Departments | ✅ |
| `09_csv_yaml_to_json.yaml` | CSV | YAML | JSON | Servers + Monitoring Config | ✅ |
| `10_yaml_yaml_to_json.yaml` | YAML | YAML | JSON | Microservices + CI/CD Pipelines | ✅ |

**Total:** 10 tests covering all format permutations

## Format Combinations

### 4 Formats × 4 Formats = 16 Possible Combinations
With same-format pairs, we have 10 unique combinations:

**XML-based (4 tests):**
- XML + XML (01)
- XML + JSON (02)
- XML + CSV (03)
- XML + YAML (04)

**JSON-based (3 tests):**
- JSON + JSON (05)
- JSON + CSV (06)
- JSON + YAML (07)

**CSV-based (2 tests):**
- CSV + CSV (08)
- CSV + YAML (09)

**YAML-based (1 test):**
- YAML + YAML (10)

## Running the Tests

### Run All Multi-Input Tests

```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py multi-input
```

### Run Specific Test

```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py multi-input 01_xml_xml_to_json
```

### Run by Tag

```bash
# Run all tests with "integration" tag
python3 runners/cli-runner/simple-runner.py --tag integration

# Run all tests with "sap" tag
python3 runners/cli-runner/simple-runner.py --tag sap
```

## Test Scenarios

### Enterprise Integration Patterns

**1. SAP Integration (XML + JSON)**
- Test: `02_xml_json_to_json.yaml`
- Scenario: SAP product catalog (ISO-8859-1 XML) + REST API pricing (JSON)
- Features: Encoding handling, German characters (Müller, Schröder)

**2. Warehouse Management (XML + CSV)**
- Test: `03_xml_csv_to_json.yaml`
- Scenario: Product master (XML) + Inventory levels (CSV)
- Features: CSV headers, stock status calculation

**3. Microservices Config (XML + YAML)**
- Test: `04_xml_yaml_to_json.yaml`
- Scenario: Service catalog (XML) + Configuration (YAML)
- Features: Service configuration merging

**4. Analytics (JSON + CSV)**
- Test: `06_json_csv_to_json.yaml`
- Scenario: Product catalog (JSON) + Sales transactions (CSV)
- Features: Profit calculation, aggregations

**5. DevOps/Kubernetes (JSON + YAML)**
- Test: `07_json_yaml_to_json.yaml`
- Scenario: Deployment manifests (JSON) + Helm config (YAML)
- Features: Kubernetes patterns, resource allocation

**6. HR Systems (CSV + CSV)**
- Test: `08_csv_csv_to_json.yaml`
- Scenario: Employee master + Department master
- Features: Join operations, summary statistics

**7. Infrastructure Monitoring (CSV + YAML)**
- Test: `09_csv_yaml_to_json.yaml`
- Scenario: Server inventory (CSV) + Monitoring config (YAML)
- Features: Alert configuration, nested YAML structures

**8. Cloud Native (YAML + YAML)**
- Test: `10_yaml_yaml_to_json.yaml`
- Scenario: Microservices (YAML) + CI/CD pipelines (YAML)
- Features: Statistics, array operations

## Features Tested

### Language Features

✅ **Multiple Named Inputs**
- All tests use `input: name1 format1, name2 format2` syntax
- Named input references: `@input1`, `@input2`

✅ **Format-Specific Options**
- CSV headers: `csv {headers: true}`
- XML encoding: `xml {encoding: "ISO-8859-1"}`

✅ **Data Navigation**
- XML: `@input.Products.Product`
- JSON: `@input.users |> map(...)`
- CSV: `@input.rows |> filter(...)`
- YAML: `@input.configurations`

✅ **Join Operations**
- Filter + first pattern for lookups
- ID matching across inputs

✅ **Aggregations**
- `sum()`, `count()`, `avg()`
- Grouped calculations

✅ **Transformations**
- `map()`, `filter()`, `|>` pipe operator
- Nested transformations

✅ **Calculations**
- Profit = Revenue - Cost
- Ratios and percentages
- Conditional logic

### Data Patterns

✅ **Master-Detail Relationships**
- Customers + Orders (XML + XML)
- Products + Sales (JSON + CSV)
- Employees + Departments (CSV + CSV)

✅ **Enrichment Patterns**
- Products + Pricing (XML + JSON)
- Servers + Monitoring (CSV + YAML)

✅ **Configuration Merging**
- Services + Config (XML + YAML)
- Deployments + Helm (JSON + YAML)
- Microservices + CI/CD (YAML + YAML)

## Expected Output Structure

All tests produce JSON output with this general structure:

```json
{
  "[Domain]Report|Integration|Architecture": {
    "Source": "[Format1]+[Format2]",
    "[MainData]": [
      {
        "[PrimaryFields]": "...",
        "[EnrichedData]": {
          // Data from second input
        }
      }
    ],
    "Summary|Statistics": {
      // Optional aggregations
    }
  }
}
```

## Performance Expectations

All tests should meet these criteria:
- **Execution Time:** < 200-250ms
- **Memory Usage:** < 20MB
- **No Errors:** 100% pass rate expected

## Tags

Tests are tagged for easy filtering:

- `multi-input` - All tests
- `xml` - Tests with XML input
- `json` - Tests with JSON input
- `csv` - Tests with CSV input
- `yaml` - Tests with YAML input
- `integration` - Enterprise integration scenarios
- `sap` - SAP-related tests
- `analytics` - Analytics/reporting tests
- `devops` - DevOps/infrastructure tests
- `kubernetes` - K8s/cloud-native tests
- `hr` - HR/employee data tests

## Adding New Tests

When adding new multi-input tests:

1. **File Naming:** `##_format1_format2_to_output.yaml`
2. **Required Fields:**
   - `inputs:` section with named inputs
   - `transformation:` with `input: name1 format1, name2 format2`
   - `expected:` output
   - `metadata:` with notes explaining the scenario

3. **Best Practices:**
   - Use realistic business scenarios
   - Include calculations/aggregations
   - Test join/lookup patterns
   - Document encoding if relevant
   - Add meaningful tags

## See Also

- [Multiple Inputs Documentation](../../docs/language-guide/multiple-inputs-outputs.md)
- [Quick Reference](../../docs/language-guide/quick-reference-multi-input.md)
- [Release Notes](../../docs/RELEASE-NOTES-MULTI-INPUT.md)

---

**Created:** 2025-10-21
**Total Tests:** 10
**Coverage:** All 2-input format permutations
**Status:** ✅ All tests passing
