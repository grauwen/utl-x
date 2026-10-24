# UTL-X Documentation

Welcome to the UTL-X documentation! This guide will help you learn and master UTL-X, the Universal Transformation Language Extended.

---

## 📚 Documentation Sections

### 🚀 Getting Started

New to UTL-X? Start here!

- **[Installation](getting-started/installation.md)** - Set up UTL-X on your system
- **[Your First Transformation](getting-started/your-first-transformation.md)** - Create your first UTL-X script
- **[Basic Concepts](getting-started/basic-concepts.md)** - Understand core concepts
- **[Quick Reference](getting-started/quick-reference.md)** - Syntax cheat sheet

### 📖 Language Guide

Learn the UTL-X language:

- **[Language Overview](language-guide/overview.md)** - What makes UTL-X unique
- **[Syntax](language-guide/syntax.md)** - Basic syntax and structure
- **[Data Types](language-guide/data-types.md)** - Numbers, strings, objects, arrays
- **[Selectors](language-guide/selectors.md)** - Navigate and query data
- **[Functions](language-guide/functions.md)** - Built-in functions reference
- **[Templates](language-guide/templates.md)** - XSLT-style template matching
- **[Control Flow](language-guide/control-flow.md)** - Conditionals and pattern matching
- **[Operators](language-guide/operators.md)** - Arithmetic, logical, comparison
- **[UDM](udm/udm_documentation_index.md)** - Universal Data Model
- **[Schema Support ](https://github.com/grauwen/utl-x/blob/main/modules/analysis/schema_analysis_architecture.md)** - Integration support through design time schema generation (for example Apache Camel)

### 📄 Format Guides

Working with different data formats:

- **[XML](formats/xml.md)** - XML input and output
- **[JSON](formats/json.md)** - JSON input and output
- **[CSV](formats/csv.md)** - CSV input and output
- **[YAML](formats/yaml.md)** - YAML input and output

Also Meta Data formats are supported!

### 💡 Examples

Practical examples and patterns:

- **[XML to JSON](examples/xml-to-json.md)** - Common XML → JSON transformations
- **[JSON to XML](examples/json-to-xml.md)** - Common JSON → XML transformations
- **[CSV Transformations](examples/csv-transformation.md)** - Working with CSV data
- **[Complex Transformations](examples/complex-transformations.md)** - Advanced patterns
- **[Real-World Use Cases](examples/real-world-use-cases.md)** - Production examples
- **[Cookbook](examples/cookbook.md)** - Solutions to common problems

### 📚 Reference

Technical reference documentation:

- **[Language Specification](reference/language-spec.md)** - Formal language specification
- **[Standard Library](reference/stdlib-reference.md)** - Complete function reference
- **[CLI Reference](reference/cli-reference.md)** - Command-line interface
- **[API Reference](reference/api-reference.md)** - Library API documentation
- **[Grammar](reference/grammar.md)** - ANTLR grammar definition

### 🏗️ Architecture

Understanding UTL-X internals:

- **[Architecture Overview](architecture/overview.md)** - High-level design
- **[Compiler Pipeline](architecture/compiler-pipeline.md)** - Compilation stages
- **[Universal Data Model](architecture/universal-data-model.md)** - UDM explained
- **[Runtime](architecture/runtime.md)** - Runtime architecture
- **[Performance](architecture/performance.md)** - Optimization techniques
- **[Validation Enforcement & Observability](architecture/utlxe-validation-enforcement-and-observability.md)** - What the engine does with a validation verdict: enforcement, logging, counters, dead-letter

### 🔄 Comparisons

Compare UTL-X with alternatives:

- **[UTL-X vs DataWeave](comparison/vs-dataweave.md)** - Feature comparison
- **[UTL-X vs XSLT](comparison/vs-xslt.md)** - When to use which
- **[UTL-X vs JSONata](comparison/vs-jsonata.md)** - Differences explained
- **[Migration Guides](comparison/migration-guides.md)** - Migrate from other tools

### 👥 Community

Community resources and information:

- **[Roadmap](community/roadmap.md)** - Project roadmap and milestones
- **[Changelog](community/changelog.md)** - Version history
- **[FAQ](community/faq.md)** - Frequently asked questions
- **[Support](community/support.md)** - Getting help
- **[Contributing](../CONTRIBUTING.md)** - How to contribute

---

## 🎯 Popular Topics

### Quick Links

- [Installation Guide](getting-started/installation.md)
- [Syntax Cheat Sheet](getting-started/quick-reference.md)
- [Function Reference](reference/stdlib-reference.md)
- [Examples Collection](examples/)
- [FAQ](community/faq.md)

### Common Tasks

- [Transform XML to JSON](examples/xml-to-json.md)
- [Filter and Map Arrays](examples/cookbook.md#filtering-and-mapping)
- [Template Matching](language-guide/templates.md)
- [Handle Multiple Formats](formats/)
- [Debug Transformations](getting-started/basic-concepts.md#debugging)

---

## 📝 Documentation Conventions

### Code Examples

All code examples use the following format:

```utlx
%utlx 1.0
input xml
output json
---
{
  message: "Hello, World!"
}
```

### Syntax Notation

- `required` - Required elements
- `[optional]` - Optional elements
- `...` - Can be repeated
- `|` - Or (alternatives)

### Notes and Warnings

> 💡 **Tip:** Helpful hints and best practices

> ⚠️ **Warning:** Important warnings and gotchas

> 📝 **Note:** Additional context and information

---

## 🔍 Searching the Documentation

### By Topic

- **Beginners:** Start with [Getting Started](getting-started/)
- **Language Features:** See [Language Guide](language-guide/)
- **Format-Specific:** Check [Format Guides](formats/)
- **Problem-Solving:** Browse [Examples](examples/) and [Cookbook](examples/cookbook.md)
- **Technical Details:** Read [Reference](reference/) and [Architecture](architecture/)

### By Experience Level

**Beginner:**
1. [Installation](getting-started/installation.md)
2. [Your First Transformation](getting-started/your-first-transformation.md)
3. [Basic Concepts](getting-started/basic-concepts.md)
4. [Simple Examples](examples/xml-to-json.md)

**Intermediate:**
1. [Template Matching](language-guide/templates.md)
2. [Functions](language-guide/functions.md)
3. [Complex Transformations](examples/complex-transformations.md)
4. [Multiple Formats](formats/)

**Advanced:**
1. [Language Specification](reference/language-spec.md)
2. [Architecture](architecture/overview.md)
3. [Performance Optimization](architecture/performance.md)
4. [Custom Formats](formats/custom-formats.md)

---

## 🤝 Contributing to Documentation

Found a typo? Want to improve an explanation? Documentation contributions are welcome!

- **Small fixes:** Edit directly on GitHub and submit a PR
- **New sections:** Open an issue first to discuss
- **Examples:** Add to the [examples/](examples/) directory
- **Guidelines:** Follow the [Contributing Guide](../CONTRIBUTING.md)

---

## 📧 Get Help

- 💬 [GitHub Discussions](https://github.com/grauwen/utl-x/discussions) - Ask questions
- 🐛 [GitHub Issues](https://github.com/grauwen/utl-x/issues) - Report problems
- 📧 [Email](mailto:community@glomidco.com) - General inquiries
- 🐦 [Twitter](https://twitter.com/UTLXLang) - Updates and news

---

## 📄 License

UTL-X is dual-licensed under AGPL-3.0 (open source) and a commercial license.

- [License Information](../LICENSE.md)
- [Commercial Licensing](https://utl-x.com/commercial)

---

**Last Updated:** January 2026  
**Version:** 0.1.0 (Alpha)

[Back to Repository](https://github.com/grauwen/utl-x)
