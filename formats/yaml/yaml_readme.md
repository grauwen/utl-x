# UTL-X YAML Format Support

YAML (YAML Ain't Markup Language) parser and serializer for the Universal Transformation Language Extended (UTL-X).

## Overview

This module provides bidirectional conversion between YAML documents and the Universal Data Model (UDM), enabling format-agnostic transformations with UTL-X.

## Features

- ✅ **Complete YAML Support**: Parse and serialize YAML 1.2 documents
- ✅ **Multi-document**: Support for YAML files with multiple documents (separated by `---`)
- ✅ **Anchors & Aliases**: Automatic handling of YAML anchors and aliases
- ✅ **Type Preservation**: Maintains distinction between integers, floats, strings, booleans
- ✅ **Date/Time Support**: Handles YAML timestamp types
- ✅ **Multiple Styles**: Block style and flow style formatting
- ✅ **Streaming**: Efficient memory usage for large documents
- ✅ **Round-trip**: Parse → Transform → Serialize with fidelity

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.apache.utlx.formats:yaml:0.9.0-beta")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'org.apache.utlx.formats:yaml:0.9.0-beta'
}
```

### Maven

```xml
<dependency>
    <groupId>org.apache.utlx.formats</groupId>
    <artifactId>yaml</artifactId>
    <version>0.9.0-beta</version>
</dependency>
```

## Quick Start

### Parsing YAML

```kotlin
import org.apache.utlx.formats.yaml.*

// Simple parsing
val yaml = """
    name: John Doe
    age: 30
    email: john@example.com
""".trimIndent()

val udm = YAMLParser.parseYAML(yaml)
```

### Serializing to YAML

```kotlin
import org.apache.utlx.core.udm.*
import org.apache.utlx.formats.yaml.*

// Create UDM structure
val udm = UDMObject(mapOf(
    "name" to UDMString("John Doe"),
    "age" to UDMNumber(30.0, 30),
    "email" to UDMString("john@example.com")
))

// Serialize to YAML
val yaml = YAMLSerializer.toYAML(udm)
println(yaml)
```

### Extension Functions

```kotlin
// Parse YAML string
val udm = "name: John".parseAsYAML()

// Serialize UDM to YAML
val yaml = udm.toYAML(pretty = true)

// Compact YAML output
val compactYaml = udm.toCompactYAML()
```

## Usage Examples

### Basic Parsing

```kotlin
val yaml = """
    person:
      name: Alice
      age: 25
      skills:
        - Kotlin
        - Java
        - Python
""".trimIndent()

val udm = YAMLParser.parseYAML(yaml)
val person = (udm as UDMObject).properties["person"] as UDMObject

val name = (person.properties["name"] as UDMString).value
val age = (person.properties["age"] as UDMNumber).longValue
val skills = (person.properties["skills"] as UDMArray).elements
```

### Multi-document YAML

```kotlin
val multiDoc = """
    ---
    title: Document 1
    ---
    title: Document 2
    ---
    title: Document 3
""".trimIndent()

val options = YAMLParser.ParseOptions(multiDocument = true)
val udm = YAMLParser().parse(multiDoc, options)

// Result is a UDMArray with 3 documents
val documents = (udm as UDMArray).elements
```

### Custom Serialization Options

```kotlin
val options = YAMLSerializer.SerializeOptions(
    pretty = true,
    indent = 4,
    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK,
    explicitStart = true,
    width = 100
)

val yaml = YAMLSerializer().serialize(udm, options)
```

### Format Conversion (YAML → UDM → JSON)

```kotlin
// Parse YAML
val yamlInput = """
    order:
      id: 12345
      items:
        - name: Widget
          quantity: 2
""".trimIndent()

val udm = YAMLParser.parseYAML(yamlInput)

// Convert to JSON (using JSON serializer)
val json = JSONSerializer.toJSON(udm)

// Or convert to XML
val xml = XMLSerializer.toXML(udm)
```

## Parser Options

```kotlin
data class ParseOptions(
    val multiDocument: Boolean = false,        // Parse multiple documents
    val preserveOrder: Boolean = true,         // Maintain key order
    val parseTimestamps: Boolean = true,       // Parse dates as UDMDate
    val allowDuplicateKeys: Boolean = false    // Allow duplicate keys
)
```

### Example with Options

```kotlin
val options = YAMLParser.ParseOptions(
    multiDocument = true,
    preserveOrder = true,
    parseTimestamps = true,
    allowDuplicateKeys = false
)

val udm = YAMLParser().parse(yamlString, options)
```

## Serializer Options

```kotlin
data class SerializeOptions(
    val pretty: Boolean = true,                                    // Pretty print
    val indent: Int = 2,                                           // Indentation spaces
    val defaultFlowStyle: FlowStyle = FlowStyle.BLOCK,            // Block or flow style
    val lineBreak: LineBreak = LineBreak.UNIX,                    // Line break style
    val explicitStart: Boolean = true,                            // Add --- at start
    val explicitEnd: Boolean = false,                             // Add ... at end
    val canonicalOutput: Boolean = false,                         // Canonical format
    val allowUnicode: Boolean = true,                             // Allow Unicode chars
    val dateTimeFormat: DateTimeFormatter = ISO_INSTANT,          // Date format
    val maxSimpleKeyLength: Int = 128,                            // Max key length
    val splitLines: Boolean = true,                               // Split long lines
    val width: Int = 80                                           // Line width
)
```

## YAML Type Mapping

| YAML Type | UDM Type | Example |
|-----------|----------|---------|
| String | UDMString | `name: John` |
| Integer | UDMNumber | `age: 30` |
| Float | UDMNumber | `price: 19.99` |
| Boolean | UDMBoolean | `active: true` |
| Null | UDMNull | `value: null` or `value: ~` |
| Timestamp | UDMDate | `created: 2025-10-14T10:00:00Z` |
| List/Sequence | UDMArray | `items: [a, b, c]` |
| Map/Dictionary | UDMObject | `person: {name: John}` |

## YAML Styles

### Block Style (Default)

```yaml
person:
  name: John
  age: 30
  skills:
    - Kotlin
    - Java
```

### Flow Style (Compact)

```yaml
person: {name: John, age: 30, skills: [Kotlin, Java]}
```

### Mixed Style

```yaml
person:
  name: John
  age: 30
  skills: [Kotlin, Java, Python]  # Flow style for array
```

## Advanced Features

### Anchors and Aliases

YAML anchors and aliases are automatically handled:

```yaml
defaults: &defaults
  timeout: 30
  retries: 3

service1:
  <<: *defaults
  name: Service 1

service2:
  <<: *defaults
  name: Service 2
```

### Custom Tags

Custom YAML tags are preserved as string values:

```yaml
date: !date 2025-10-14
custom: !mytype special_value
```

### Literal and Folded Strings

```yaml
# Literal (preserves line breaks)
literal: |
  Line 1
  Line 2
  Line 3

# Folded (folds into single line)
folded: >
  This is a long
  text that will be
  folded into one line.
```

## Performance

The YAML parser uses SnakeYAML, a well-tested and performant library:

- **Small files (<100KB)**: ~10-50ms
- **Medium files (100KB-1MB)**: ~50-200ms
- **Large files (1MB-10MB)**: ~200ms-2s

Memory usage is efficient through streaming where possible.

## Error Handling

```kotlin
try {
    val udm = YAMLParser.parseYAML(yaml)
} catch (e: YAMLParseException) {
    println("Failed to parse YAML: ${e.message}")
    e.cause?.printStackTrace()
}

try {
    val yaml = YAMLSerializer.toYAML(udm)
} catch (e: YAMLSerializeException) {
    println("Failed to serialize YAML: ${e.message}")
}
```

## Testing

Run tests with:

```bash
./gradlew :formats:yaml:test
```

## Integration with UTL-X

Use YAML in UTL-X transformations:

```utlx
%utlx 1.0
input yaml
output json
---
{
  invoice: {
    id: input.order.id,
    customer: input.order.customer.name,
    items: input.order.items |> map(item => {
      product: item.name,
      quantity: item.quantity,
      total: item.price * item.quantity
    })
  }
}
```

## Dependencies

- **Kotlin Standard Library**: 1.9+
- **SnakeYAML**: 2.2
- **UTL-X Core**: 0.9.0-beta

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines.

## License

Dual-licensed under:
- GNU Affero General Public License v3.0 (AGPL-3.0) for open source use
- Commercial License for proprietary applications

See [LICENSE.md](../../LICENSE.md) for details.

## Support

- **Documentation**: https://github.com/grauwen/utl-x/docs
- **Issues**: https://github.com/grauwen/utl-x/issues
- **Discussions**: https://github.com/grauwen/utl-x/discussions

## Related

- [XML Format](../xml/README.md)
- [JSON Format](../json/README.md)
- [CSV Format](../csv/README.md)
- [UTL-X Core](../../modules/core/README.md)

---

**UTL-X YAML Format** - Part of the Universal Transformation Language Extended project.
