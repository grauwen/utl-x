# Multi-Input Conformance Tests

This directory contains comprehensive conformance tests for UTL-X's multiple input feature, covering all permutations of 2-input scenarios with various output formats.

## Test Coverage Matrix

### Tests with JSON Output (Tests 01-10)

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

### Tests with XML Output (Tests 11-13)

| Test | Input 1 | Input 2 | Output | Scenario | Status |
|------|---------|---------|--------|----------|--------|
| `11_xml_xml_to_xml.yaml` | XML | XML | XML | Order Fulfillment Integration | ✅ |
| `12_xml_json_to_xml.yaml` | XML | JSON | XML | SAP Products + API Pricing Catalog | ✅ |
| `13_json_xml_to_xml.yaml` | JSON | XML | XML | Modern API + Legacy System | ✅ |

### Tests with CSV Output (Tests 14-16)

| Test | Input 1 | Input 2 | Output | Scenario | Status |
|------|---------|---------|--------|----------|--------|
| `14_csv_csv_to_csv.yaml` | CSV | CSV | CSV | Employee + Department HR Report | ✅ |
| `15_json_csv_to_csv.yaml` | JSON | CSV | CSV | Product Catalog + Sales Analytics | ✅ |
| `16_xml_csv_to_csv.yaml` | XML | CSV | CSV | Product Master + Inventory Stock Report | ✅ |

### Tests with YAML Output (Tests 17-19)

| Test | Input 1 | Input 2 | Output | Scenario | Status |
|------|---------|---------|--------|----------|--------|
| `17_yaml_yaml_to_yaml.yaml` | YAML | YAML | YAML | Microservices + Deployment Config | ✅ |
| `18_json_yaml_to_yaml.yaml` | JSON | YAML | YAML | K8s Deployment + Helm Values | ✅ |
| `19_xml_yaml_to_yaml.yaml` | XML | YAML | YAML | Service Catalog + Monitoring Config | ✅ |

**Total:** 19 tests covering all 2-input format permutations with multiple output formats

## Format Combinations

### Input Combinations (10 unique pairs)

**XML-based (4 combinations):**
- XML + XML (Tests: 01, 11)
- XML + JSON (Tests: 02, 12)
- XML + CSV (Tests: 03, 16)
- XML + YAML (Tests: 04, 19)

**JSON-based (3 combinations):**
- JSON + JSON (Test: 05)
- JSON + CSV (Tests: 06, 15)
- JSON + YAML (Tests: 07, 18)

**CSV-based (2 combinations):**
- CSV + CSV (Tests: 08, 14)
- CSV + YAML (Test: 09)

**YAML-based (1 combination):**
- YAML + YAML (Tests: 10, 17)

### Output Format Coverage

**JSON Output:** 10 tests (01-10)
- Covers all 10 input combinations
- Best for API integration and data interchange

**XML Output:** 3 tests (11-13)
- XML round-trip scenarios
- SAP integration patterns
- Legacy system compatibility

**CSV Output:** 3 tests (14-16)
- Flat output for reporting
- Excel/BI tool compatibility
- HR and analytics scenarios

**YAML Output:** 3 tests (17-19)
- Configuration management
- Kubernetes/cloud-native deployments
- Infrastructure-as-code patterns

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

## Test Scenarios by Category

### SAP & Enterprise Integration

**1. SAP Product Catalog + API Pricing (XML + JSON → JSON)**
- Test: `02_xml_json_to_json.yaml`
- Features: ISO-8859-1 encoding, German characters, price enrichment

**2. SAP Products + REST API → XML Catalog (XML + JSON → XML)**
- Test: `12_xml_json_to_xml.yaml`
- Features: Encoding normalization, discount calculations, XML round-trip

**3. Order Fulfillment Integration (XML + XML → XML)**
- Test: `11_xml_xml_to_xml.yaml`
- Features: Multiple XML encodings, customer-order merge, shipping addresses

### Inventory & Warehouse Management

**4. Product Master + Inventory Levels (XML + CSV → JSON)**
- Test: `03_xml_csv_to_json.yaml`
- Features: Stock status calculation, low stock detection

**5. Product Master + Inventory → Stock Report (XML + CSV → CSV)**
- Test: `16_xml_csv_to_csv.yaml`
- Features: Reorder recommendations, warehouse reporting, flat output

### Analytics & Sales Reporting

**6. Product Catalog + Sales Transactions (JSON + CSV → JSON)**
- Test: `06_json_csv_to_json.yaml`
- Features: Profit calculation, margin analysis, regional sales

**7. Product Catalog + Sales → Analytics Report (JSON + CSV → CSV)**
- Test: `15_json_csv_to_csv.yaml`
- Features: Revenue/cost/profit calculations, flat output for BI tools

### HR & Employee Management

**8. Employee + Department Data (CSV + CSV → JSON)**
- Test: `08_csv_csv_to_json.yaml`
- Features: Join operations, summary statistics, salary analysis

**9. Employee + Department → HR Report (CSV + CSV → CSV)**
- Test: `14_csv_csv_to_csv.yaml`
- Features: Budget allocation, department joins, flat reporting

### DevOps & Kubernetes

**10. Deployment Manifests + Helm Config (JSON + YAML → JSON)**
- Test: `07_json_yaml_to_json.yaml`
- Features: Kubernetes patterns, resource allocation, replica management

**11. K8s Deployment + Helm → Manifest (JSON + YAML → YAML)**
- Test: `18_json_yaml_to_yaml.yaml`
- Features: Complete deployment specs, ingress config, container resources

**12. Microservices + CI/CD Pipelines (YAML + YAML → JSON)**
- Test: `10_yaml_yaml_to_json.yaml`
- Features: Pipeline statistics, test coverage analysis, deployment stages

**13. Microservices + Deployment Config (YAML + YAML → YAML)**
- Test: `17_yaml_yaml_to_yaml.yaml`
- Features: Autoscaling config, resource limits, health checks

### Infrastructure & Monitoring

**14. Server Inventory + Monitoring Config (CSV + YAML → JSON)**
- Test: `09_csv_yaml_to_json.yaml`
- Features: Alert configuration, nested YAML, monitoring policies

**15. Service Catalog + Monitoring → Config (XML + YAML → YAML)**
- Test: `19_xml_yaml_to_yaml.yaml`
- Features: Health check config, alert rules, criticality levels

### Multi-System Integration

**16. Modern API + Legacy System (JSON + XML → XML)**
- Test: `13_json_xml_to_xml.yaml`
- Features: API-legacy merge, permission integration, user directory

**17. Customer + Orders Integration (XML + XML → JSON)**
- Test: `01_xml_xml_to_json.yaml`
- Features: CRM integration, customer enrichment, order aggregation

**18. User Profiles + Activity Data (JSON + JSON → JSON)**
- Test: `05_json_json_to_json.yaml`
- Features: User analytics, engagement scoring, activity aggregation

**19. Service Catalog + Configuration (XML + YAML → JSON)**
- Test: `04_xml_yaml_to_json.yaml`
- Features: Service configuration merge, endpoint validation

## Features Tested

### Language Features

✅ **Multiple Named Inputs**
- All tests use `input: name1 format1, name2 format2` syntax
- Named input references: `$input1`, `$input2`

✅ **Format-Specific Options**
- CSV headers: `csv {headers: true}`
- XML encoding: `xml {encoding: "ISO-8859-1"}`

✅ **Data Navigation**
- XML: `$input.Products.Product`
- JSON: `$input.users |> map(...)`
- CSV: `$input.rows |> filter(...)`
- YAML: `$input.configurations`

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

## Expected Output Structures

### JSON Output (Tests 01-10)
All JSON tests produce output with this general structure:

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

### XML Output (Tests 11-13)
XML tests produce structured documents with attributes and nested elements:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<RootElement attribute="value">
  <SourceInfo>
    <Encoding1>UTF-8</Encoding1>
    <Encoding2>ISO-8859-1</Encoding2>
  </SourceInfo>
  <Data>
    <Item id="...">
      <Field>Value</Field>
    </Item>
  </Data>
</RootElement>
```

### CSV Output (Tests 14-16)
CSV tests produce flat tabular output with headers:

```csv
Header1,Header2,Header3,CalculatedField
Value1,Value2,Value3,ComputedValue
...
```

### YAML Output (Tests 17-19)
YAML tests produce hierarchical configuration documents:

```yaml
apiVersion: v1
kind: ConfigType
metadata:
  generatedDate: '2025-10-21'
spec:
  items:
    - name: item1
      properties:
        key: value
```

## Performance Expectations

All tests should meet these criteria:
- **Execution Time:** < 200-250ms
- **Memory Usage:** < 20MB
- **No Errors:** 100% pass rate expected

## Tags

Tests are tagged for easy filtering:

**Format Tags:**
- `multi-input` - All tests (19 tests)
- `xml` - Tests with XML input or output (11 tests)
- `json` - Tests with JSON input or output (13 tests)
- `csv` - Tests with CSV input or output (9 tests)
- `yaml` - Tests with YAML input or output (11 tests)

**Scenario Tags:**
- `integration` - Enterprise integration scenarios (7 tests)
- `sap` - SAP-related tests (3 tests)
- `analytics` - Analytics/reporting tests (4 tests)
- `devops` - DevOps/infrastructure tests (6 tests)
- `kubernetes` - K8s/cloud-native tests (4 tests)
- `helm` - Helm/K8s deployment tests (2 tests)
- `hr` - HR/employee data tests (2 tests)
- `monitoring` - Infrastructure monitoring (2 tests)
- `warehouse` - Inventory/warehouse management (2 tests)
- `legacy` - Legacy system integration (1 test)
- `round-trip` - Format round-trip tests (XML→XML, YAML→YAML) (2 tests)

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
**Last Updated:** 2025-10-21
**Total Tests:** 19
**Input Combinations:** 10 unique 2-input format pairs
**Output Formats:** JSON (10), XML (3), CSV (3), YAML (3)
**Coverage:** Comprehensive multi-input feature validation
**Status:** ✅ All tests passing
