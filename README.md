# UTL-X - Universal Transformation Language Extended

**An open-source, format-agnostic functional transformation language for data transformation.**

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE.md)
[![Version](https://img.shields.io/badge/version-0.9.0--beta-orange)](https://github.com/grauwen/utl-x/releases)
[![Documentation](https://img.shields.io/badge/docs-utlx.dev-brightgreen)](https://utl-x.org/docs)

## Overview

UTL-X is a modern transformation language that works with XML, JSON, CSV, YAML, and other formats. Write your transformation logic once, and apply it to any supported format.

## Why UTL-X?

- 🔄 **Format Agnostic** - One transformation works with XML, JSON, CSV, YAML
- 🎯 **Functional & Declarative** - Clean, maintainable transformation logic
- 💪 **Strongly Typed** - Catch errors at compile time
- ⚡ **High Performance** - Optimized compilation and execution
- 🔓 **Open Source** - AGPL-3.0, truly free and community-driven
- 🚀 **Multiple Runtimes** - JVM, JavaScript, Native

## Quick Example

### Transform XML to JSON

**Input XML** (`order.xml`):
```xml
<?xml version="1.0"?>
<Order id="ORD-2025-001" date="2025-10-09">
  <Customer>
    <Name>Alice Johnson</Name>
    <Email>alice@example.com</Email>
  </Customer>
  <Items>
    <Item sku="WIDGET-A" quantity="2" price="29.99"/>
    <Item sku="GADGET-B" quantity="1" price="149.99"/>
  </Items>
</Order>
```

**Transformation** (`order-to-invoice.utlx`):
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    id: "INV-" + $input.Order.@id,
    date: $input.Order.@date,
    
    customer: {
      name: $input.Order.Customer.Name,
      email: $input.Order.Customer.Email
    },
    
    items: $input.Order.Items.Item |> map(item => {
      sku: item.@sku,
      quantity: parseNumber(item.@quantity),
      unitPrice: parseNumber(item.@price),
      total: parseNumber(item.@quantity) * parseNumber(item.@price)
    }),
    
    summary: {
      itemCount: count($input.Order.Items.Item),
      subtotal: sum($input.Order.Items.Item |> map(item =>
        parseNumber(item.@quantity) * parseNumber(item.@price)
      )),
      tax: sum($input.Order.Items.Item |> map(item =>
        parseNumber(item.@quantity) * parseNumber(item.@price)
      )) * 0.08,
      grandTotal: sum($input.Order.Items.Item |> map(item =>
        parseNumber(item.@quantity) * parseNumber(item.@price)
      )) * 1.08
    }
  }
}
```

**Run:**
```bash
utlx transform order-to-invoice.utlx order.xml
```

**Output JSON:**
```json
{
  "invoice": {
    "id": "INV-ORD-2025-001",
    "date": "2025-10-09",
    "customer": {
      "name": "Alice Johnson",
      "email": "alice@example.com"
    },
    "items": [
      {
        "sku": "WIDGET-A",
        "quantity": 2,
        "unitPrice": 29.99,
        "total": 59.98
      },
      {
        "sku": "GADGET-B",
        "quantity": 1,
        "unitPrice": 149.99,
        "total": 149.99
      }
    ],
    "summary": {
      "itemCount": 2,
      "subtotal": 209.97,
      "tax": 16.7976,
      "grandTotal": 226.7676
    }
  }
}
```

### The Same Transformation Works with JSON Input!

**Input JSON** (`order.json`):
```json
{
  "Order": {
    "id": "ORD-2025-001",
    "date": "2025-10-09",
    "Customer": {
      "Name": "Alice Johnson",
      "Email": "alice@example.com"
    },
    "Items": {
      "Item": [
        {"sku": "WIDGET-A", "quantity": 2, "price": 29.99},
        {"sku": "GADGET-B", "quantity": 1, "price": 149.99}
      ]
    }
  }
}
```

Just change the input format:
```utlx
%utlx 1.0
input json    // Changed from xml to json
output json
---
// Same transformation code works!
```

**That's the power of format-agnostic transformation.**

## More Examples

### Aggregate and Group Data

```utlx
%utlx 1.0
input json
output json
---
{
  byCategory: $input.products
    |> groupBy(product => product.category)
    |> map(group => {
         category: group.key,
         count: count(group.value),
         avgPrice: avg(group.value |> map(p => p.price)),
         totalValue: sum(group.value |> map(p => p.price))
       })
    |> sortBy(item => -item.totalValue)
}
```

### Filter and Transform

```utlx
%utlx 1.0
input json
output json
---
{
  premiumCustomers: $input.customers
    |> filter(c => c.totalSpent > 10000 && c.active == true)
    |> sortBy(c => -c.totalSpent)
    |> take(10)
    |> map(c => {
         name: c.name,
         email: c.email,
         lifetimeValue: c.totalSpent,
         since: c.memberSince
       })
}
```

### Join Multiple Data Sources

```utlx
%utlx 1.0
input: orders json, customers json, products json
output json
---
{
  enrichedOrders: $orders |> map(order => {
    let customer = $customers
      |> filter(c => c.id == order.customerId)
      |> first(),
    let product = $products
      |> filter(p => p.id == order.productId)
      |> first()

    {
      orderId: order.id,
      customerName: customer.name,
      productName: product.name,
      total: order.quantity * product.price
    }
  })
}
```

**Note:** Multiple named inputs use `$` prefix to access each input. This separates concerns: the transformation logic uses `$orders`, `$customers`, `$products`, while file paths are specified via CLI `--input` flags.

## Installation

### macOS / Linux
```bash
# Homebrew
brew tap grauwen/utlx
brew install utlx

# Or universal installer
curl -fsSL https://utl-x.dev/install.sh | bash
```

### Windows
```powershell
# Chocolatey
choco install utlx
```

### Manual Download
Download from [GitHub Releases](https://github.com/grauwen/utl-x/releases)

### Verify Installation
```bash
utlx --version
# UTL-X version 0.9.0 (beta)
```

### Building from Source

**Prerequisites:**
- JDK 17 or later
- Gradle 8.x (included via wrapper)

**Build Steps:**

1. Clone the repository:
```bash
git clone https://github.com/grauwen/utl-x.git
cd utl-x
```

2. Build the CLI:
```bash
# macOS / Linux
./gradlew :modules:cli:jar

# Windows
gradlew.bat :modules:cli:jar
```

3. Run using the wrapper scripts:

**macOS / Linux:**
```bash
./utlx transform script.utlx input.xml
```

**Windows (Command Prompt):**
```cmd
utlx.bat transform script.utlx input.xml
```

**Windows (PowerShell):**
```powershell
.\utlx.ps1 transform script.utlx input.xml
```

The wrapper scripts automatically locate and run the compiled JAR file at `modules/cli/build/libs/cli-1.0.0-SNAPSHOT.jar`.

## Quick Start

### 1. Create Your First Transformation

Create `hello.utlx`:
```utlx
%utlx 1.0
input json
output json
---
{
  greeting: "Hello, " + $input.name + "!",
  timestamp: formatDate(now(), "yyyy-MM-dd HH:mm:ss")
}
```

### 2. Create Input Data

Create `$input.json`:
```json
{"name": "World"}
```

### 3. Run Transformation

```bash
utlx transform hello.utlx $input.json
```

Output:
```json
{
  "greeting": "Hello, World!",
  "timestamp": "2025-10-09 14:30:00"
}
```

### 4. Save Output to File

```bash
utlx transform hello.utlx $input.json -o output.json
```

## Key Features

### Format Agnostic
```utlx
// Same transformation works with XML, JSON, CSV, YAML
%utlx 1.0
input auto      // Auto-detect format
output json
---
{
  result: $input.data |> map(item => transform(item))
}
```

### Functional Programming
```utlx
$input.items
  |> filter(item => item.price > 100)
  |> map(item => {
       name: item.name,
       discounted: item.price * 0.9
     })
  |> sortBy(item => -item.discounted)
  |> take(10)
```

### Pattern Matching
```utlx
match order.status {
  "pending" => {
    message: "Awaiting Processing",
    priority: "high"
  },
  "shipped" => {
    message: "In Transit",
    priority: "medium"
  },
  "delivered" => {
    message: "Completed",
    priority: "low"
  },
  _ => {
    message: "Unknown",
    priority: "urgent"
  }
}
```

### Strong Typing
```utlx
function calculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

// Type error caught at compile time:
// calculateTax("100", 0.08)  ❌ Error: String not assignable to Number
```

## Use Cases

| Use Case | Description |
|----------|-------------|
| **API Integration** | Transform REST API responses between formats |
| **Data Migration** | Convert legacy XML data to modern JSON |
| **ETL Pipelines** | Extract, transform, load data in any format |
| **Message Transformation** | Transform messages between different systems |
| **Configuration Management** | Convert between config formats (XML ↔ JSON ↔ YAML) |
| **Report Generation** | Transform data into report formats |
| **Schema transformation** | Transform schemas between XSD, JSON Schema, Avro, Protobuf |

## Comparison

### vs XSLT
- ✅ Works with **multiple formats**, not just XML
- ✅ **Modern syntax** - more concise and readable
- ✅ **Functional programming** features (map, filter, reduce)
- ✅ **Multiple runtimes** (JVM, JavaScript, Native)

### vs DataWeave
- ✅ **Open source** (AGPL-3.0) - no vendor lock-in
- ✅ **Community-driven** governance
- ✅ **Free to use** - no licensing costs
- ✅ Similar capabilities but truly open

### vs Custom Code
- ✅ **Declarative** - describe what, not how
- ✅ **Built-in parsers** for XML, JSON, CSV, YAML
- ✅ **Type-safe** transformations
- ✅ **Optimized** performance

## Language Highlights

### Rich Standard Library

```utlx
// String functions
upper("hello")              // "HELLO"
split("a,b,c", ",")        // ["a", "b", "c"]
substring("hello", 0, 3)   // "hel"

// Array functions
map([1,2,3], n => n * 2)   // [2, 4, 6]
filter([1,2,3], n => n > 1) // [2, 3]
sum([1, 2, 3])             // 6

// Date functions
now()                       // Current date/time
formatDate(now(), "yyyy-MM-dd")
addDays(now(), 7)          // 7 days from now

// Math functions
round(3.7)                 // 4
max([1, 5, 3])            // 5
```

### Pattern Matching

```utlx
match order.status {
  "pending" => "Awaiting Processing",
  "shipped" => "In Transit",
  "delivered" => "Completed",
  _ => "Unknown"
}
```

### Safe Navigation

```utlx
// Safely access nested properties
input.customer?.address?.city ?? "Unknown"

// No more null pointer errors!
```

### Powerful Expressions

```utlx
{
  // Conditional logic
  discount: if (customer.type == "VIP") 
              total * 0.20 
            else if (total > 1000) 
              total * 0.10 
            else 
              0,
  
  // Calculations
  tax: total * 0.08,

  // Aggregations
  orderTotal: sum(items |> map(item => item.quantity * item.price))
}
```

## Documentation

- 📖 **[Getting Started Guide](docs/getting-started/installation.md)** - Installation and basics
- 📚 **[Language Guide](docs/language-guide/overview.md)** - Complete language reference
- 💡 **[Examples](docs/examples/)** - Real-world transformation examples
- 🔧 **[API Reference](docs/reference/api-reference.md)** - JVM, JavaScript, Native APIs
- 🏗️ **[Architecture](docs/architecture/overview.md)** - How UTL-X works internally

## Community & Support

- 💬 **[GitHub Discussions](https://github.com/grauwen/utl-x/discussions)** - Ask questions, share ideas
- 🐛 **[Issue Tracker](https://github.com/grauwen/utl-x/issues)** - Report bugs, request features
- 💼 **[Stack Overflow](https://stackoverflow.com/questions/tagged/utl-x)** - Tag: `utl-x`
- 💭 **[Discord Server](https://discord.gg/utlx)** - Real-time community chat
- 📧 **Email**: [support@utl-x.dev](mailto:support@utl-x.dev)

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Quick ways to contribute:
- 🐛 Report bugs and issues
- 💡 Suggest new features
- 📖 Improve documentation
- 💻 Submit pull requests
- ⭐ Star the repository

All contributors must agree to the [AGPL-3.0 license](LICENSE.md) terms.

## Project Status

**Current Version**: 0.9.0 (Beta)  
**Target v1.0**: December 2025

See our [Roadmap](docs/community/roadmap.md) for planned features.

### Stability

- ✅ Core UTLX 1.0 - Uniform Transformation Language Extended: **Stable**
- ✅ UDM - Universal Data Model: **Stable**
- ✅ USDL 1.0 - Uniform Schema Definition Language: **Stable**
- ✅ XML support: **Stable**
- ✅ JSON support: **Stable**
- ✅ CSV support: **Stable**
- ✅ YAML support: **Stable**
- ✅ XSD support: **Stable**
- ✅ JSCH (JSON schema) support: **Stable**
- ✅ Avro schema support: **Stable**
- ✅ Protobuf schema (proto3) support: **Stable**
- ✅ utlx CLI **Stable**
- 🚧 JVM runtime (2 variants): **In Development** 
- 🚧 JavaScript runtime: **In Development** 
- 🚧 Native runtime: **In Development** 
- 🚧 WASM runtime: **In Development** 
- 🚧 VS-code plugin: **In Development** 


## License

UTL-X is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

### What This Means

- ✅ **Free to use** for any purpose, including commercial
- ✅ **Free to modify** and distribute
- ⚠️ **Source disclosure required** if you offer UTL-X as a network service
- ⚠️ **Modifications must remain open source** (copyleft)

See [LICENSE.md](LICENSE.md) for complete terms.

### Why AGPL-3.0?

We chose AGPL-3.0 to ensure UTL-X remains truly open source:
- Prevents proprietary forks
- Ensures cloud providers share improvements
- Protects against vendor lock-in
- Keeps innovation open and collaborative

**Need different licensing?** Contact [licensing@utlx.dev](mailto:licensing@utlx.dev)

## Project Leadership

**Concept Originator & Project Lead**  
Ir. Marcel A. Grauwen - [@grauwen](https://github.com/grauwen)

## Acknowledgments

UTL-X draws inspiration from:
- **XSLT** - Template matching and declarative transformations (25+ years of proven design)
- **DataWeave** - Format abstraction and functional programming approach
- **Haskell** - Type system and functional purity
- **XPath/JSONata** - Path expression syntax
- **jq** - Command line (CLI) JSON processor

Special thanks to the open-source community for inspiration and support.

## Star History

If you find UTL-X useful, please consider starring the repository! ⭐

## Support the Project

- ⭐ **Star the repository** on GitHub
- 📢 **Share** UTL-X with your network
- 📝 **Write** blog posts or tutorials
- 💰 **Sponsor** development: [sponsor@utl-x.dev](mailto:sponsor@utl-x.dev)

---

**Made with ❤️ by the UTL-X community**

[Website](https://utl-x.dev) • [Documentation](https://utl-x.dev/docs) • [GitHub](https://github.com/grauwen/utl-x) • [Discord](https://discord.gg/utlx)
