# Working with YAML (v1.1+)

YAML support is planned for version 1.1.

## Basic YAML Transformation

### Input YAML

```yaml
order:
  id: ORD-001
  date: 2025-10-09
  customer:
    name: Alice Johnson
    email: alice@example.com
  items:
    - sku: WIDGET-A
      quantity: 2
      price: 29.99
    - sku: GADGET-B
      quantity: 1
      price: 149.99
```

### UTL-X Transformation

```utlx
%utlx 1.0
input yaml
output json
---
{
  orderId: input.order.id,
  customerName: input.order.customer.name,
  total: sum(input.order.items.(quantity * price))
}
```

## YAML Features

### Accessing YAML Data

Same as JSON:

```utlx
input.order.customer.name
input.order.items[0].sku
input.order.items.*.price
```

### YAML to JSON/XML

Identical to JSON transformations.

### JSON/XML to YAML

```utlx
%utlx 1.0
input json
output yaml
---
{
  order: {
    id: input.orderId,
    customer: input.customerName
  }
}
```

## YAML Configuration

```utlx
input yaml {
  strict: true,
  version: "1.2"
}

output yaml {
  indent: 2,
  flowLevel: 2
}
```

---

## FILE: docs/formats/custom-formats.md

# Custom Formats

Create custom format parsers and serializers for UTL-X.

## Format Plugin Architecture

```kotlin
interface FormatParser {
    fun canParse(input: InputStream): Boolean
    fun parse(input: InputStream): UDM
}

interface FormatSerializer {
    fun canSerialize(format: String): Boolean
    fun serialize(udm: UDM, output: OutputStream)
}
```

## Creating a Custom Parser

### Example: Properties File Parser

```kotlin
class PropertiesParser : FormatParser {
    override fun canParse(input: InputStream): Boolean {
        // Check if input is a properties file
        return input.markSupported() && 
               input.peek().startsWith("#") || 
               input.peek().contains("=")
    }
    
    override fun parse(input: InputStream): UDM {
        val props = Properties()
        props.load(input)
        
        // Convert to UDM Object
        return UDM.Object(
            props.entries.associate { 
                it.key.toString() to UDM.Scalar(it.value.toString())
            }
        )
    }
}
```

### Registering Custom Parser

```kotlin
UTLXEngine.registerParser("properties", PropertiesParser())
```

### Using Custom Format

```utlx
%utlx 1.0
input properties
output json
---
{
  database: {
    host: input.db_host,
    port: parseNumber(input.db_port)
  }
}
```

## Example Custom Formats

### Fixed-Width Files
### Protocol Buffers
### Apache Avro
### EDI (Electronic Data Interchange)
### HL7 (Healthcare)

See [examples/custom-formats/](../../examples/custom-formats/) for implementations.

---
