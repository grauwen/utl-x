# UTL-X: Universal Transformation Language Extended

**Status:** 🚧 Under Active Development  
**License:** AGPL-3.0 / Commercial Dual License  
**Maintainer:** Glomidco B.V.

## What is UTL-X?

UTL-X is an open-source, format-agnostic transformation language for converting between XML, JSON, CSV, YAML, and other data formats. Think of it as XSLT meets DataWeave, but fully open source.

### Key Features

- 🌐 **Format Agnostic** - Transform between XML, JSON, CSV, YAML seamlessly
- 🔧 **Functional** - Immutable data, pure functions, higher-order operations
- ⚡ **Fast** - Compile-time optimization, efficient runtime
- 🎯 **XSLT-Inspired** - Declarative template matching + modern syntax
- 🆓 **Open Source** - AGPL-3.0 licensed, community-driven

## Project Status

UTL-X is in **early development**. We're building:
- [ ] Language specification v1.0
- [ ] Parser and compiler (Kotlin/JVM)
- [ ] XML and JSON support
- [ ] Standard library
- [ ] CLI tool
- [ ] Documentation

**Want to contribute?** See [CONTRIBUTING.md](CONTRIBUTING.md)

## Quick Example
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    id: input.Order.@id,
    customer: input.Order.Customer.Name,
    total: sum(input.Order.Items.Item.(@price * @quantity))
  }
}
```
[![CLA assistant](https://cla-assistant.io/readme/badge/grauwen/utl-x)](https://cla-assistant.io/grauwen/utl-x)
