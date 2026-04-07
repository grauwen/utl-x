# UTL-X Examples

Practical transformation examples organized by category.

## Format Conversion

- [XML to JSON](xml-to-json.md) — Common XML to JSON transformation patterns
- [JSON to XML](json-to-xml.md) — JSON to XML with attributes, namespaces, and nested structures
- [CSV Transformations](csv-transformation.md) — CSV parsing, filtering, grouping, and format conversion

## Multi-Input

- [Multiple Inputs](multiple-inputs.md) — Combining data from XML, JSON, and CSV sources in one transformation

## Real-World Scenarios

- [Real-World Use Cases](real-world-use-cases.md) — E-commerce, financial reconciliation, IoT data aggregation
- [Complex Transformations](complex-transformations.md) — Multi-level aggregation, hierarchical data, conditional logic
- [Cookbook](cookbook.md) — Common patterns and recipes

## Enterprise Integration

- [SAP IDoc Integration](idoc/sap-idoc-integration-study.md) — Transforming SAP IDoc XML to JSON
- [IDoc Metadata](idoc/IDOC-meta-data.md) — SAP IDoc metadata format reference

## Complete Examples

- [Example Transforms](example_transforms.md) — End-to-end transformation examples with input, script, and output

## Quick Start

The fastest way to try UTL-X — no script needed:

```bash
cat data.xml | utlx                # XML to JSON
cat data.json | utlx               # JSON to XML
cat data.csv | utlx                # CSV to JSON
cat data.xml | utlx --to yaml     # XML to YAML
```

See [Getting Started](../getting-started/your-first-transformation.md) for a step-by-step tutorial.

## Sample Data Files

The [`examples/`](../../examples/) directory contains sample data files for all supported formats. Use these to experiment with UTL-X:

| Directory | Format | Description |
|-----------|--------|-------------|
| [`examples/xml/`](../../examples/xml/) | XML | Orders, invoices, healthcare, customs declarations |
| [`examples/json/`](../../examples/json/) | JSON | Shipping, billing, employee data, financial transactions |
| [`examples/csv/`](../../examples/csv/) | CSV | Tabular data samples |
| [`examples/yaml/`](../../examples/yaml/) | YAML | Configuration and structured data |
| [`examples/odata/`](../../examples/odata/) | OData | OData JSON message samples |
| [`examples/xsd/`](../../examples/xsd/) | XSD | XML Schema definitions |
| [`examples/jsch/`](../../examples/jsch/) | JSON Schema | JSON Schema definitions |
| [`examples/avro/`](../../examples/avro/) | Avro | Avro schema samples |
| [`examples/protobuf/`](../../examples/protobuf/) | Protobuf | Protocol Buffer schema samples |
| [`examples/osch/`](../../examples/osch/) | OSCH | OData/EDMX metadata samples |
| [`examples/tsch/`](../../examples/tsch/) | TSCH | Frictionless Table Schema samples |
| [`examples/usdl/`](../../examples/usdl/) | USDL | Universal Schema Definition Language samples |
| [`examples/utlx/`](../../examples/utlx/) | UTL-X | Transformation scripts |
| [`examples/IDOC/`](../../examples/IDOC/) | SAP IDoc | SAP IDoc XML samples |

Try them with identity mode:

```bash
cat examples/xml/00-healthcare-claim.xml | utlx
cat examples/json/01-simple-order.json | utlx
cat examples/csv/01-simple.csv | utlx
```
