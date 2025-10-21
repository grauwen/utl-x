# UTL-X Language Guide

Welcome to the UTL-X Language Guide. This documentation covers all aspects of the Universal Transformation Language Extended (UTL-X).

## Quick Start

- 📖 [Language Overview](overview.md) - Start here if you're new to UTL-X
- 🚀 [Syntax Guide](syntax.md) - Core language syntax and structure
- 🔍 [Quick Reference - Multiple Inputs](quick-reference-multi-input.md) - Fast lookup for multi-input scenarios

## Core Concepts

### Language Fundamentals

- [Operators](operators.md) - Arithmetic, logical, and comparison operators
- [Functions](functions.md) - Built-in and user-defined functions
- [Control Flow](control-flow.md) - Conditionals, loops, and pattern matching
- [Selectors](selectors.md) - Navigating and accessing data structures

### Advanced Features

- ⭐ [Multiple Inputs and Outputs](multiple-inputs-outputs.md) - **NEW: Combine data from multiple sources**
- [Templates](templates.md) - Declarative transformation templates

## Format Support

- [XML Format](../formats/xml.md)
- [JSON Format](../formats/json.md)
- [CSV Format](../formats/csv.md)
- [YAML Format](../formats/yaml.md)
- [Custom Formats](../formats/custom-format.md)

## Feature Status

### ✅ Implemented Features

- **Single Input/Output** - Full support
- **Multiple Named Inputs** - ✅ **NEW (2025-10-21)** - [Documentation](multiple-inputs-outputs.md)
- **Per-Input Format Options** - ✅ Supported
- **Encoding Detection** - ✅ Per-input support
- **Functional Operators** - map, filter, reduce, pipe
- **Let Bindings** - Variable declarations
- **Conditional Expressions** - if/else
- **Lambda Functions** - Anonymous functions
- **XML, JSON, CSV, YAML** - All formats supported

### 📋 Planned Features

- **Multiple Named Outputs** - Syntax designed, implementation pending
- **Template Matching** - XSLT-style pattern matching
- **Pattern Matching** - Full match expressions
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
    Name: @input.customer.name,
    Email: @input.customer.email
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
    fromXML: @data1.Customer,
    fromJSON: @data2.order,
    fromCSV: @data3.rows[0]
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
    Materials: @sapData.Materials.Material,
    Pricing: @apiData.prices,
    Metadata: {
      sapEncoding: detectXMLEncoding(@sapData)
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
  Customers: @crm.customers |> map(customer => {
    let erpData = @erp.Customers.Customer
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
| Formats | XML only | XML, JSON, CSV, YAML |
| Syntax | XML-based | JSON-like |
| Templates | ✅ Full support | 📋 Planned |
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
│   ├── templates.md
│   ├── multiple-inputs-outputs.md ⭐ NEW
│   └── quick-reference-multi-input.md ⭐ NEW
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

## Release Notes

- [Multiple Inputs Release (v0.2.0)](../RELEASE-NOTES-MULTI-INPUT.md) - 2025-10-21

## See Also

- [Project Overview (CLAUDE.md)](../../CLAUDE.md)
- [UDM Documentation](../udm/udm_documentation_index.md)
- [Function Reference](../../stdlib/README.md)

---

**Last Updated:** 2025-10-21
**UTL-X Version:** v0.2.0
**License:** AGPL-3.0 / Commercial Dual-License
