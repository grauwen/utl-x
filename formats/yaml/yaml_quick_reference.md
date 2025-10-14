# YAML Quick Reference for UTL-X

Quick reference for working with YAML in UTL-X transformations.

## Table of Contents

- [Basic Syntax](#basic-syntax)
- [Parsing YAML](#parsing-yaml)
- [Serializing YAML](#serializing-yaml)
- [Type Mappings](#type-mappings)
- [Common Patterns](#common-patterns)
- [UTL-X Integration](#utl-x-integration)

## Basic Syntax

### Import

```kotlin
import org.apache.utlx.formats.yaml.*
import org.apache.utlx.core.udm.*
```

## Parsing YAML

### Simple Parse

```kotlin
// String to UDM
val udm = "name: John".parseAsYAML()

// Or explicitly
val udm = YAMLParser.parseYAML("name: John")
```

### With Options

```kotlin
val options = YAMLParser.ParseOptions(
    multiDocument = true,
    preserveOrder = true,
    parseTimestamps = true,
    allowDuplicateKeys = false
)

val udm = YAMLParser().parse(yamlString, options)
```

### From InputStream

```kotlin
fileInputStream.use { stream ->
    val udm = stream.parseAsYAML()
}
```

## Serializing YAML

### Simple Serialize

```kotlin
// UDM to YAML string
val yaml = udm.toYAML()

// With pretty printing
val yaml = udm.toYAML(pretty = true)

// Compact flow style
val yaml = udm.toCompactYAML()
```

### With Options

```kotlin
val options = YAMLSerializer.SerializeOptions(
    pretty = true,
    indent = 4,
    explicitStart = true
)

val yaml = YAMLSerializer().serialize(udm, options)
```

### To OutputStream

```kotlin
fileOutputStream.use { stream ->
    udm.toYAML(stream)
}
```

### Multi-document

```kotlin
val documents = listOf(udm1, udm2, udm3)
val yaml = YAMLSerializer().serializeMultiDocument(documents)
```

## Type Mappings

### YAML → UDM

```kotlin
// String
"name: John"           → UDMString("John")

// Integer
"age: 30"              → UDMNumber(30.0, 30)

// Float
"price: 19.99"         → UDMNumber(19.99, 19)

// Boolean
"active: true"         → UDMBoolean(true)

// Null
"value: null"          → UDMNull
"value: ~"             → UDMNull

// Array
"items: [a, b]"        → UDMArray([UDMString("a"), UDMString("b")])

// Object
"person: {name: John}" → UDMObject(...)
```

### UDM → YAML

```kotlin
UDMString("John")      → "John"
UDMNumber(30.0, 30)    → 30
UDMNumber(19.99, 19)   → 19.99
UDMBoolean(true)       → true
UDMNull                → null
UDMArray([...])        → [...]
UDMObject({...})       → {...}
```

## Common Patterns

### Access Nested Values

```kotlin
val yaml = """
    person:
      name: John
      age: 30
""".trimIndent()

val udm = YAMLParser.parseYAML(yaml)
val person = (udm as UDMObject).properties["person"] as UDMObject
val name = (person.properties["name"] as UDMString).value
val age = (person.properties["age"] as UDMNumber).longValue
```

### Iterate Arrays

```kotlin
val yaml = """
    items:
      - apple
      - banana
      - cherry
""".trimIndent()

val udm = YAMLParser.parseYAML(yaml)
val items = ((udm as UDMObject).properties["items"] as UDMArray).elements

items.forEach { item ->
    val value = (item as UDMString).value
    println(value)
}
```

### Build YAML Structure

```kotlin
val udm = UDMObject(mapOf(
    "person" to UDMObject(mapOf(
        "name" to UDMString("John"),
        "age" to UDMNumber(30.0, 30),
        "skills" to UDMArray(listOf(
            UDMString("Kotlin"),
            UDMString("Java")
        ))
    ))
))

val yaml = YAMLSerializer.toYAML(udm)
```

### Convert Between Formats

```kotlin
// YAML → JSON
val yamlInput = "name: John"
val udm = YAMLParser.parseYAML(yamlInput)
val json = JSONSerializer.toJSON(udm)

// JSON → YAML
val jsonInput = """{"name": "John"}"""
val udm = JSONParser.parseJSON(jsonInput)
val yaml = YAMLSerializer.toYAML(udm)
```

## UTL-X Integration

### YAML Input

```utlx
%utlx 1.0
input yaml
output json
---
{
  result: input.data.value
}
```

### YAML Output

```utlx
%utlx 1.0
input json
output yaml
---
{
  transformed: input.items |> map(i => i.name)
}
```

### Both YAML

```utlx
%utlx 1.0
input yaml
output yaml
---
{
  processed: {
    source: input.source,
    items: input.items |> filter(i => i.active)
  }
}
```

### Format Options

```utlx
%utlx 1.0
input yaml {
  multiDocument: true,
  preserveOrder: true
}
output yaml {
  pretty: true,
  indent: 4,
  explicitStart: true
}
---
{
  documents: input |> map(doc => {
    id: doc.id,
    name: doc.name
  })
}
```

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

**Code:**
```kotlin
val options = YAMLSerializer.SerializeOptions(
    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
)
```

### Flow Style (Compact)

```yaml
person: {name: John, age: 30, skills: [Kotlin, Java]}
```

**Code:**
```kotlin
val options = YAMLSerializer.SerializeOptions(
    defaultFlowStyle = DumperOptions.FlowStyle.FLOW
)
```

## Multi-document YAML

### Parse Multiple Documents

```kotlin
val yaml = """
    ---
    title: Doc 1
    ---
    title: Doc 2
""".trimIndent()

val options = YAMLParser.ParseOptions(multiDocument = true)
val udm = YAMLParser().parse(yaml, options)

// Result is UDMArray with 2 elements
```

### Serialize Multiple Documents

```kotlin
val docs = listOf(
    UDMObject(mapOf("title" to UDMString("Doc 1"))),
    UDMObject(mapOf("title" to UDMString("Doc 2")))
)

val yaml = YAMLSerializer().serializeMultiDocument(docs)
```

## Error Handling

### Parse Errors

```kotlin
try {
    val udm = YAMLParser.parseYAML(yaml)
} catch (e: YAMLParseException) {
    println("Parse error: ${e.message}")
}
```

### Serialize Errors

```kotlin
try {
    val yaml = YAMLSerializer.toYAML(udm)
} catch (e: YAMLSerializeException) {
    println("Serialize error: ${e.message}")
}
```

## Tips & Best Practices

### ✅ DO

- Use `parseAsYAML()` extension for simple cases
- Enable `multiDocument` for multi-doc YAML files
- Set `preserveOrder = true` to maintain key order
- Use block style for readability
- Handle parse exceptions appropriately

### ❌ DON'T

- Don't assume all numbers are integers
- Don't forget to handle null values
- Don't use flow style for complex structures
- Don't parse untrusted YAML without validation
- Don't ignore duplicate key warnings

## Common Tasks Cheatsheet

```kotlin
// Parse YAML string
val udm = "name: John".parseAsYAML()

// Parse YAML file
val udm = File("data.yaml").inputStream().parseAsYAML()

// Serialize to YAML string
val yaml = udm.toYAML()

// Serialize to YAML file
File("output.yaml").outputStream().use { udm.toYAML(it) }

// Round-trip
val yaml2 = YAMLParser.parseYAML(yaml1).toYAML()

// Convert YAML to JSON
val json = YAMLParser.parseYAML(yaml).toJSON()

// Convert JSON to YAML
val yaml = JSONParser.parseJSON(json).toYAML()

// Pretty print
val pretty = udm.toYAML(pretty = true)

// Compact print
val compact = udm.toCompactYAML()

// Multi-document parse
val docs = YAMLParser().parse(
    yaml,
    YAMLParser.ParseOptions(multiDocument = true)
)

// Multi-document serialize
val yaml = YAMLSerializer().serializeMultiDocument(listOf(udm1, udm2))
```

## Performance Tips

1. **Streaming**: Use InputStream/OutputStream for large files
2. **Compact Style**: Use flow style for smaller output
3. **Disable Features**: Turn off unnecessary options
4. **Reuse Parser**: Create parser instance once if parsing multiple files
5. **Buffer Streams**: Use BufferedInputStream/BufferedOutputStream

```kotlin
// Efficient for large files
BufferedInputStream(FileInputStream("large.yaml")).use { input ->
    val udm = input.parseAsYAML()
}
```

## Resources

- [Full YAML Documentation](yaml_readme.md)
- [YAML Specification](https://yaml.org/spec/)
- [SnakeYAML Documentation](https://bitbucket.org/snakeyaml/snakeyaml)
- [UTL-X Core Documentation](../../modules/core/README.md)

---

**Quick Reference** | UTL-X YAML Format Module
