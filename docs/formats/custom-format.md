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
