# UTL-X Language Guide

Welcome to the UTL-X Language Guide. This documentation covers all aspects of the Universal Transformation Language Extended (UTL-X).

## Quick Start

- 📖 [Language Overview](overview.md) - Start here if you're new to UTL-X
- 🚀 [Syntax Guide](syntax.md) - Core language syntax and structure
- 🔍 [Quick Reference - Multiple Inputs](quick-reference-multi-$input.md) - Fast lookup for multi-input scenarios

## Core Concepts

### Language Fundamentals

- [Operators](operators.md) - Arithmetic, logical, and comparison operators
- [Functions](functions.md) - Built-in and user-defined functions
- [Control Flow](control-flow.md) - Conditionals, loops, and pattern matching
- [Selectors](selectors.md) - Navigating and accessing data structures

### Advanced Features

- [Multiple Inputs and Outputs](multiple-inputs-outputs.md) - Combine data from multiple sources

## Supported Formats

**Tier 1 — Data formats:** XML, JSON, CSV, YAML, OData

**Tier 2 — Schema formats:** XSD, JSCH (JSON Schema), Avro, Protobuf, OSCH (OData/EDMX), TSCH (Table Schema)

## Implemented Features

- **Single Input/Output** - Full support
- **Multiple Named Inputs** - [Documentation](multiple-inputs-outputs.md)
- **Per-Input Format Options** - Supported
- **Encoding Detection** - Per-input support
- **Functional Operators** - map, filter, reduce, pipe
- **Let Bindings** - Variable declarations
- **Conditional Expressions** - if/else
- **Lambda Functions** - Anonymous functions
- **Pattern Matching** - match expressions
- **Spread Operator** - Object and array spread
- **652 Stdlib Functions** - [Reference](../stdlib/stdlib-complete-reference.md)
- **Identity Mode** - `cat data.xml | utlx` (format conversion without a script)
- **All 11 formats** - XML, JSON, CSV, YAML, OData, XSD, JSCH, Avro, Protobuf, OSCH, TSCH

### Planned Features

- **Multiple Named Outputs** - Syntax designed, implementation pending
- **Module System** - Import/export functionality
- **Streaming Support** - Large file handling

## Quick Reference

### Basic Transformation

```utlx
%utlx 1.0
input json
output xml
---
{
  Customer: {
    Name: $input.customer.name,
    Email: $input.customer.email
  }
}
```

### Multiple Inputs (NEW!)

```utlx
%utlx 1.0
input: data1 xml, data2 json, data3 csv
output json
---
{
  Combined: {
    fromXML: $data1.Customer,
    fromJSON: $data2.order,
    fromCSV: $data3.rows[0]
  }
}
```

**CLI:**
```bash
utlx transform script.utlx \
  --input data1=file1.xml \
  --input data2=file2.json \
  --input data3=file3.csv \
  -o output.json
```

## Real-World Examples

### SAP Integration

```utlx
%utlx 1.0
input: sapData xml {encoding: "ISO-8859-1"}, apiData json
output xml {encoding: "UTF-8"}
---
{
  Integration: {
    Materials: $sapData.Materials.Material,
    Pricing: $apiData.prices,
    Metadata: {
      sapEncoding: detectXMLEncoding($sapData)
    }
  }
}
```

### Customer Data Merge

```utlx
%utlx 1.0
input: crm json, erp xml
output json
---
{
  Customers: $crm.customers |> map(customer => {
    let erpData = $erp.Customers.Customer
      |> filter(c => c.@id == customer.id)
      |> first()

    {
      ...customer,
      creditLimit: erpData.CreditLimit,
      balance: erpData.Balance
    }
  })
}
```

## Comparison with Other Languages

### vs. DataWeave

| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| Multiple Inputs | `%input in0 application/json` | `input: data1 json, data2 xml` |
| Multiple Outputs | ❌ Not supported | ✅ Planned |
| Syntax | Directive-based | Declarative |
| License | Proprietary | AGPL-3.0 / Commercial |

### vs. XSLT

| Feature | XSLT | UTL-X |
|---------|------|-------|
| Formats | XML only | XML, JSON, CSV, YAML, OData + 6 schema formats |
| Syntax | XML-based | JSON-like |
| Stdlib | ~100 XPath functions | 652 functions |
| Learning Curve | Steep | Moderate |

## Documentation Organization

```
docs/
├── language-guide/
│   ├── README.md (this file)
│   ├── overview.md
│   ├── syntax.md
│   ├── operators.md
│   ├── functions.md
│   ├── control-flow.md
│   ├── selectors.md
│   ├── multiple-inputs-outputs.md
│   └── quick-reference-multi-input.md
├── formats/
│   ├── xml.md
│   ├── json.md
│   ├── csv.md
│   ├── yaml.md
│   └── custom-format.md
├── udm/
│   ├── udm_complete_guide.md
│   ├── udm_advanced_guide.md
│   └── udm_visual_examples.md
└── RELEASE-NOTES-MULTI-INPUT.md ⭐ NEW
```

## Getting Help

- **Issues**: Report bugs at [GitHub Issues](https://github.com/apache/utl-x/issues)
- **Discussions**: Join our community discussions
- **Examples**: See `conformance-suite/tests/examples/`

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines on:
- Reporting bugs
- Suggesting features
- Submitting pull requests
- Writing documentation

## See Also

- [Stdlib Reference (652 functions)](../stdlib/stdlib-complete-reference.md)
- [Examples](../examples/)
- [Comparison Guides](../comparison/)

---

**UTL-X Version:** 1.0.0
**License:** AGPL-3.0 / Commercial Dual-License
