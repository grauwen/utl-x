# UTL-X Quick Start

Get started with UTL-X native binary in 5 minutes! ⚡

## What is UTL-X?

Universal Transformation Language Extended (UTL-X) is a format-agnostic transformation language that converts between XML, JSON, CSV, YAML, and more with a single, elegant syntax.

**Why UTL-X?**
- ⚡ Native binary - starts in <10ms
- 📦 Single file - no JVM or dependencies
- 🎯 Format agnostic - one language for all formats
- 🚀 High performance - optimized compilation
- 📖 Easy to learn - familiar syntax

## Installation

### One-Line Install

**macOS / Linux:**
```bash
curl -fsSL https://raw.githubusercontent.com/grauwen/utl-x/main/scripts/install.sh | sh
```

**Windows (PowerShell):**
```powershell
iwr -useb https://raw.githubusercontent.com/grauwen/utl-x/main/scripts/install.ps1 | iex
```

### Manual Install

**Linux (x64):**
```bash
curl -L https://github.com/grauwen/utl-x/releases/latest/download/utlx-linux-x64 -o utlx
chmod +x utlx
sudo mv utlx /usr/local/bin/
```

**macOS (Apple Silicon):**
```bash
curl -L https://github.com/grauwen/utl-x/releases/latest/download/utlx-macos-arm64 -o utlx
chmod +x utlx
sudo mv utlx /usr/local/bin/
```

**macOS (Intel):**
```bash
curl -L https://github.com/grauwen/utl-x/releases/latest/download/utlx-macos-x64 -o utlx
chmod +x utlx
sudo mv utlx /usr/local/bin/
```

**Windows:**
```powershell
Invoke-WebRequest -Uri "https://github.com/grauwen/utl-x/releases/latest/download/utlx-windows-x64.exe" -OutFile "utlx.exe"
Move-Item utlx.exe C:\Windows\System32\
```

### Verify Installation

```bash
utlx --version
```

## Your First Transformation

### 1. Create Input Data

**input.xml:**
```xml
<Order id="ORD-001">
    <Customer>Alice Johnson</Customer>
    <Items>
        <Item sku="WIDGET-01" price="50.00" quantity="2"/>
        <Item sku="GADGET-02" price="75.00" quantity="1"/>
    </Items>
</Order>
```

### 2. Create Transformation

**transform.utlx:**
```utlx
%utlx 1.0
input xml
output json
---
{
    orderId: input.Order.@id,
    customer: input.Order.Customer,
    items: input.Order.Items.Item |> map(item => {
        sku: item.@sku,
        price: parseNumber(item.@price),
        quantity: parseNumber(item.@quantity),
        subtotal: parseNumber(item.@price) * parseNumber(item.@quantity)
    }),
    total: sum(input.Order.Items.Item.(
        parseNumber(@price) * parseNumber(@quantity)
    ))
}
```

### 3. Transform

```bash
utlx transform input.xml transform.utlx -o output.json
```

### 4. Result

**output.json:**
```json
{
  "orderId": "ORD-001",
  "customer": "Alice Johnson",
  "items": [
    {
      "sku": "WIDGET-01",
      "price": 50.0,
      "quantity": 2,
      "subtotal": 100.0
    },
    {
      "sku": "GADGET-02",
      "price": 75.0,
      "quantity": 1,
      "subtotal": 75.0
    }
  ],
  "total": 175.0
}
```

## Common Use Cases

### XML → JSON
```bash
utlx transform data.xml transform.utlx -o result.json
```

### JSON → CSV
```bash
utlx transform data.json transform.utlx -f csv -o result.csv
```

### Pipe from stdin
```bash
cat input.xml | utlx transform - transform.utlx
```

### Watch mode (auto-reload)
```bash
utlx transform input.xml transform.utlx -w -o output.json
```

### Validate syntax
```bash
utlx validate transform.utlx
```

### Benchmark performance
```bash
utlx transform input.xml transform.utlx -b
```

## Performance

```
Startup:  <10ms  (vs 100-500ms for JVM)
Memory:   ~40MB  (vs 150-300MB for JVM)
Size:     ~12MB  (vs 300-500MB for JVM+deps)
```

## Next Steps

📖 **Full Documentation:** [docs/getting-started/native-binary-quickstart.md](docs/getting-started/native-binary-quickstart.md)

🎓 **Learn More:**
- [Language Guide](docs/language-guide/README.md)
- [Examples](examples/README.md)
- [API Reference](docs/reference/README.md)
- [Migration from XSLT](docs/comparison/xslt-migration.md)
- [Migration from DataWeave](docs/comparison/dataweave-migration.md)

🛠️ **For Contributors:**
- [Building from Source](README-NATIVE.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Architecture](docs/architecture/README.md)

💬 **Get Help:**
- [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
- [Issue Tracker](https://github.com/grauwen/utl-x/issues)
- [Discord Community](https://discord.gg/utlx)

## License

Dual-licensed:
- **Open Source:** GNU AGPL v3.0 (free for open source projects)
- **Commercial:** Contact licensing@utlx-lang.org (for proprietary use)

## Quick License Guide

| Use Case | License |
|----------|---------|
| Personal/learning use | ✅ Free (AGPL) |
| Open source project | ✅ Free (AGPL) |
| Internal company tool | ✅ Free (AGPL) |
| SaaS product | 💼 Commercial |
| Embedded in proprietary software | 💼 Commercial |

---

**Built with ❤️ by [Ir. Marcel A. Grauwen](https://github.com/grauwen) and the UTL-X Community**

⭐ **Star us on GitHub:** https://github.com/grauwen/utl-x
