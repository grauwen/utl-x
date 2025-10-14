# UDM Advanced Implementation Guide

## Table of Contents

1. [Parser Implementation Patterns](#parser-implementation-patterns)
2. [Serializer Implementation Patterns](#serializer-implementation-patterns)
3. [Advanced Navigation](#advanced-navigation)
4. [Type Coercion & Conversion](#type-coercion--conversion)
5. [Streaming & Large Files](#streaming--large-files)
6. [Memory Optimization](#memory-optimization)
7. [Extension Points](#extension-points)
8. [Testing UDM Components](#testing-udm-components)

---

## Parser Implementation Patterns

### XML Parser Deep Dive

```kotlin
package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.*
import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.SAXReader

class XMLParser : FormatParser {
    
    override fun parse(input: String): UDMNode {
        val reader = SAXReader()
        val document = reader.read(StringReader(input))
        return convertElement(document.rootElement)
    }
    
    private fun convertElement(element: Element): ObjectNode {
        val properties = mutableMapOf<String, UDMNode>()
        val attributes = mutableMapOf<String, String>()
        
        // 1. Collect attributes
        element.attributes().forEach { attr ->
            attributes[attr.name] = attr.value
        }
        
        // 2. Group child elements by name
        val childGroups = element.elements().groupBy { it.name }
        
        for ((name, children) in childGroups) {
            properties[name] = when {
                // Single child
                children.size == 1 -> {
                    val child = children[0]
                    if (child.isTextOnly()) {
                        // <name>text</name> -> ScalarNode
                        ScalarNode(child.text, inferType(child.text))
                    } else {
                        // <name><child/></name> -> ObjectNode
                        convertElement(child)
                    }
                }
                // Multiple children with same name
                else -> {
                    ArrayNode(children.map { convertElement(it) })
                }
            }
        }
        
        // 3. Handle text content (mixed content or simple text)
        val textContent = element.text.trim()
        if (textContent.isNotEmpty() && properties.isEmpty()) {
            // Simple text element
            return ObjectNode(
                properties = mapOf(
                    "_text" to ScalarNode(textContent, inferType(textContent))
                ),
                metadata = Metadata(attributes = attributes)
            )
        }
        
        // 4. Handle namespaces
        val namespace = element.namespace?.uri
        val nsPrefix = element.namespace?.prefix
        
        return ObjectNode(
            properties = properties,
            metadata = Metadata(
                attributes = attributes,
                namespace = namespace,
                namespacePrefix = nsPrefix
            )
        )
    }
    
    private fun Element.isTextOnly(): Boolean {
        return elements().isEmpty() && text.isNotBlank()
    }
    
    private fun inferType(value: String): ValueType {
        return when {
            value.toBooleanStrictOrNull() != null -> ValueType.BOOLEAN
            value.toIntOrNull() != null -> ValueType.INTEGER
            value.toDoubleOrNull() != null -> ValueType.NUMBER
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> ValueType.DATE
            else -> ValueType.STRING
        }
    }
}
```

### JSON Parser Deep Dive

```kotlin
package org.apache.utlx.formats.json

import kotlinx.serialization.json.*
import org.apache.utlx.core.udm.*

class JSONParser : FormatParser {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun parse(input: String): UDMNode {
        val element = json.parseToJsonElement(input)
        return convertElement(element)
    }
    
    private fun convertElement(element: JsonElement): UDMNode {
        return when (element) {
            is JsonObject -> convertObject(element)
            is JsonArray -> convertArray(element)
            is JsonPrimitive -> convertPrimitive(element)
            JsonNull -> NullNode
        }
    }
    
    private fun convertObject(obj: JsonObject): ObjectNode {
        val properties = obj.mapValues { (_, value) ->
            convertElement(value)
        }
        
        return ObjectNode(
            properties = properties,
            metadata = Metadata(sourceFormat = "json")
        )
    }
    
    private fun convertArray(arr: JsonArray): ArrayNode {
        val elements = arr.map { convertElement(it) }
        return ArrayNode(elements)
    }
    
    private fun convertPrimitive(prim: JsonPrimitive): ScalarNode {
        return when {
            prim.isString -> {
                val str = prim.content
                // Try to detect special types
                val type = when {
                    str.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")) -> ValueType.DATETIME
                    str.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> ValueType.DATE
                    else -> ValueType.STRING
                }
                ScalarNode(str, type)
            }
            prim.booleanOrNull != null -> {
                ScalarNode(prim.boolean, ValueType.BOOLEAN)
            }
            prim.intOrNull != null -> {
                ScalarNode(prim.int, ValueType.INTEGER)
            }
            prim.longOrNull != null -> {
                ScalarNode(prim.long, ValueType.INTEGER)
            }
            prim.doubleOrNull != null -> {
                ScalarNode(prim.double, ValueType.NUMBER)
            }
            else -> {
                ScalarNode(prim.content, ValueType.STRING)
            }
        }
    }
}
```

### CSV Parser Deep Dive

```kotlin
package org.apache.utlx.formats.csv

import org.apache.utlx.core.udm.*
import java.io.BufferedReader
import java.io.StringReader

class CSVParser : FormatParser {
    
    data class CSVOptions(
        val delimiter: Char = ',',
        val hasHeaders: Boolean = true,
        val quote: Char = '"',
        val inferTypes: Boolean = true
    )
    
    private val options = CSVOptions()
    
    override fun parse(input: String): UDMNode {
        val reader = BufferedReader(StringReader(input))
        val lines = reader.readLines()
        
        if (lines.isEmpty()) {
            return ArrayNode(emptyList())
        }
        
        val headers = if (options.hasHeaders) {
            parseCSVLine(lines[0])
        } else {
            (0 until parseCSVLine(lines[0]).size).map { "column$it" }
        }
        
        val dataLines = if (options.hasHeaders) {
            lines.drop(1)
        } else {
            lines
        }
        
        val rows = dataLines.map { line ->
            val values = parseCSVLine(line)
            val properties = headers.zip(values).toMap().mapValues { (_, value) ->
                if (options.inferTypes) {
                    ScalarNode(value, inferType(value))
                } else {
                    ScalarNode(value, ValueType.STRING)
                }
            }
            ObjectNode(properties)
        }
        
        return ArrayNode(
            elements = rows,
            metadata = Metadata(
                custom = mapOf(
                    "headers" to headers,
                    "delimiter" to options.delimiter
                )
            )
        )
    }
    
    private fun parseCSVLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentField = StringBuilder()
        var inQuotes = false
        
        for (i in line.indices) {
            val ch = line[i]
            
            when {
                ch == options.quote -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == options.quote) {
                        // Escaped quote
                        currentField.append(options.quote)
                        continue
                    }
                    inQuotes = !inQuotes
                }
                ch == options.delimiter && !inQuotes -> {
                    result.add(currentField.toString())
                    currentField = StringBuilder()
                }
                else -> {
                    currentField.append(ch)
                }
            }
        }
        
        result.add(currentField.toString())
        return result
    }
    
    private fun inferType(value: String): ValueType {
        return when {
            value.isEmpty() -> ValueType.NULL
            value.toBooleanStrictOrNull() != null -> ValueType.BOOLEAN
            value.toIntOrNull() != null -> ValueType.INTEGER
            value.toDoubleOrNull() != null -> ValueType.NUMBER
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> ValueType.DATE
            else -> ValueType.STRING
        }
    }
}
```

---

## Serializer Implementation Patterns

### JSON Serializer Deep Dive

```kotlin
package org.apache.utlx.formats.json

import kotlinx.serialization.json.*
import org.apache.utlx.core.udm.*

class JSONSerializer : FormatSerializer {
    
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    
    override fun serialize(node: UDMNode): String {
        val element = convertToJson(node)
        return json.encodeToString(JsonElement.serializer(), element)
    }
    
    private fun convertToJson(node: UDMNode): JsonElement {
        return when (node) {
            is ScalarNode -> convertScalar(node)
            is ArrayNode -> convertArray(node)
            is ObjectNode -> convertObject(node)
            is NullNode -> JsonNull
        }
    }
    
    private fun convertScalar(node: ScalarNode): JsonPrimitive {
        return when (node.type) {
            ValueType.STRING, ValueType.DATE, ValueType.TIME, 
            ValueType.DATETIME, ValueType.DURATION -> {
                JsonPrimitive(node.value.toString())
            }
            ValueType.INTEGER -> {
                JsonPrimitive((node.value as Number).toLong())
            }
            ValueType.NUMBER -> {
                JsonPrimitive((node.value as Number).toDouble())
            }
            ValueType.BOOLEAN -> {
                JsonPrimitive(node.value as Boolean)
            }
            ValueType.NULL -> JsonNull
            else -> JsonPrimitive(node.value.toString())
        }
    }
    
    private fun convertArray(node: ArrayNode): JsonArray {
        return JsonArray(node.elements.map { convertToJson(it) })
    }
    
    private fun convertObject(node: ObjectNode): JsonObject {
        val properties = node.properties.mapValues { (_, value) ->
            convertToJson(value)
        }
        return JsonObject(properties)
    }
}
```

### XML Serializer Deep Dive

```kotlin
package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.*
import org.dom4j.Document
import org.dom4j.DocumentHelper
import org.dom4j.Element
import java.io.StringWriter

class XMLSerializer : FormatSerializer {
    
    override fun serialize(node: UDMNode): String {
        val document = DocumentHelper.createDocument()
        
        when (node) {
            is ObjectNode -> {
                val rootName = node.properties.keys.firstOrNull() ?: "root"
                val rootValue = node.properties[rootName] ?: node
                val rootElement = convertToElement(rootName, rootValue, document)
                document.add(rootElement)
            }
            else -> {
                val root = convertToElement("root", node, document)
                document.add(root)
            }
        }
        
        val writer = StringWriter()
        val format = org.dom4j.io.OutputFormat.createPrettyPrint()
        val xmlWriter = org.dom4j.io.XMLWriter(writer, format)
        xmlWriter.write(document)
        
        return writer.toString()
    }
    
    private fun convertToElement(
        name: String, 
        node: UDMNode, 
        document: Document
    ): Element {
        val element = DocumentHelper.createElement(name)
        
        when (node) {
            is ScalarNode -> {
                element.text = node.value.toString()
            }
            is ObjectNode -> {
                // Add attributes
                node.metadata.attributes.forEach { (key, value) ->
                    element.addAttribute(key, value)
                }
                
                // Add namespace
                if (node.metadata.namespace != null) {
                    element.addNamespace(
                        node.metadata.namespacePrefix ?: "",
                        node.metadata.namespace
                    )
                }
                
                // Add child elements
                node.properties.forEach { (childName, childNode) ->
                    when (childNode) {
                        is ArrayNode -> {
                            // Multiple elements with same name
                            childNode.elements.forEach { item ->
                                val child = convertToElement(childName, item, document)
                                element.add(child)
                            }
                        }
                        else -> {
                            val child = convertToElement(childName, childNode, document)
                            element.add(child)
                        }
                    }
                }
            }
            is ArrayNode -> {
                // Wrap array elements
                node.elements.forEach { item ->
                    val child = convertToElement("item", item, document)
                    element.add(child)
                }
            }
            is NullNode -> {
                // Empty element
            }
        }
        
        return element
    }
}
```

---

## Advanced Navigation

### Path Expression Parser

```kotlin
package org.apache.utlx.core.udm

sealed class PathSegment {
    data class Property(val name: String) : PathSegment()
    data class Index(val index: Int) : PathSegment()
    object Wildcard : PathSegment()
    data class Predicate(val expr: String) : PathSegment()
    object RecursiveDescent : PathSegment()
    data class Attribute(val name: String) : PathSegment()
}

class PathParser {
    fun parse(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        var current = ""
        var i = 0
        
        while (i < path.length) {
            when (val ch = path[i]) {
                '.' -> {
                    if (i + 1 < path.length && path[i + 1] == '.') {
                        // Recursive descent
                        if (current.isNotEmpty()) {
                            segments.add(PathSegment.Property(current))
                            current = ""
                        }
                        segments.add(PathSegment.RecursiveDescent)
                        i += 2
                        continue
                    } else {
                        if (current.isNotEmpty()) {
                            segments.add(PathSegment.Property(current))
                            current = ""
                        }
                    }
                }
                '@' -> {
                    // Attribute
                    val attrEnd = path.indexOf('.', i).takeIf { it != -1 } ?: path.length
                    val attrName = path.substring(i + 1, attrEnd)
                    segments.add(PathSegment.Attribute(attrName))
                    i = attrEnd
                    continue
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        segments.add(PathSegment.Property(current))
                        current = ""
                    }
                    
                    val end = path.indexOf(']', i)
                    val content = path.substring(i + 1, end)
                    
                    segments.add(when {
                        content == "*" -> PathSegment.Wildcard
                        content.toIntOrNull() != null -> PathSegment.Index(content.toInt())
                        else -> PathSegment.Predicate(content)
                    })
                    
                    i = end + 1
                    continue
                }
                else -> {
                    current += ch
                }
            }
            i++
        }
        
        if (current.isNotEmpty()) {
            segments.add(PathSegment.Property(current))
        }
        
        return segments
    }
}

class AdvancedNavigator(private val root: UDMNode) {
    
    private val parser = PathParser()
    
    fun navigate(path: String): List<UDMNode> {
        val segments = parser.parse(path)
        return navigate(listOf(root), segments)
    }
    
    private fun navigate(nodes: List<UDMNode>, segments: List<PathSegment>): List<UDMNode> {
        if (segments.isEmpty()) return nodes
        
        val segment = segments.first()
        val remaining = segments.drop(1)
        
        val nextNodes = nodes.flatMap { node ->
            when (segment) {
                is PathSegment.Property -> navigateProperty(node, segment.name)
                is PathSegment.Index -> navigateIndex(node, segment.index)
                is PathSegment.Wildcard -> navigateWildcard(node)
                is PathSegment.RecursiveDescent -> navigateRecursive(node, remaining.first())
                is PathSegment.Attribute -> navigateAttribute(node, segment.name)
                is PathSegment.Predicate -> navigatePredicate(node, segment.expr)
            }
        }
        
        return if (segment is PathSegment.RecursiveDescent) {
            nextNodes
        } else {
            navigate(nextNodes, remaining)
        }
    }
    
    private fun navigateProperty(node: UDMNode, name: String): List<UDMNode> {
        return when (node) {
            is ObjectNode -> listOfNotNull(node.get(name))
            else -> emptyList()
        }
    }
    
    private fun navigateIndex(node: UDMNode, index: Int): List<UDMNode> {
        return when (node) {
            is ArrayNode -> listOfNotNull(node.get(index))
            else -> emptyList()
        }
    }
    
    private fun navigateWildcard(node: UDMNode): List<UDMNode> {
        return when (node) {
            is ArrayNode -> node.elements
            is ObjectNode -> node.properties.values.toList()
            else -> emptyList()
        }
    }
    
    private fun navigateRecursive(node: UDMNode, nextSegment: PathSegment): List<UDMNode> {
        val results = mutableListOf<UDMNode>()
        
        fun recurse(current: UDMNode) {
            // Check if current node matches
            when (nextSegment) {
                is PathSegment.Property -> {
                    if (current is ObjectNode && current.has(nextSegment.name)) {
                        results.add(current.get(nextSegment.name)!!)
                    }
                }
                else -> {}
            }
            
            // Recurse into children
            when (current) {
                is ObjectNode -> current.properties.values.forEach { recurse(it) }
                is ArrayNode -> current.elements.forEach { recurse(it) }
                else -> {}
            }
        }
        
        recurse(node)
        return results
    }
    
    private fun navigateAttribute(node: UDMNode, name: String): List<UDMNode> {
        return when (node) {
            is ObjectNode -> {
                val value = node.metadata.attributes[name]
                if (value != null) {
                    listOf(ScalarNode(value, ValueType.STRING))
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
    
    private fun navigatePredicate(node: UDMNode, expr: String): List<UDMNode> {
        // Simplified predicate evaluation
        // Full implementation would parse and evaluate the expression
        return when (node) {
            is ArrayNode -> node.elements.filter { evaluatePredicate(it, expr) }
            else -> emptyList()
        }
    }
    
    private fun evaluatePredicate(node: UDMNode, expr: String): Boolean {
        // Simplified - parse and evaluate expression
        // Example: "price > 50"
        return true // Placeholder
    }
}
```

---

## Type Coercion & Conversion

```kotlin
package org.apache.utlx.core.udm

object TypeCoercion {
    
    fun coerce(value: Any?, targetType: ValueType): Any? {
        if (value == null) return null
        
        return when (targetType) {
            ValueType.STRING -> value.toString()
            ValueType.INTEGER -> coerceToInteger(value)
            ValueType.NUMBER -> coerceToNumber(value)
            ValueType.BOOLEAN -> coerceToBoolean(value)
            ValueType.DATE -> coerceToDate(value)
            ValueType.DATETIME -> coerceToDateTime(value)
            else -> value
        }
    }
    
    private fun coerceToInteger(value: Any): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull()
            is Boolean -> if (value) 1 else 0
            else -> null
        }
    }
    
    private fun coerceToNumber(value: Any): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            is Boolean -> if (value) 1.0 else 0.0
            else -> null
        }
    }
    
    private fun coerceToBoolean(value: Any): Boolean {
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.toBoolean() || value == "1" || value.equals("yes", true)
            else -> false
        }
    }
    
    private fun coerceToDate(value: Any): java.time.LocalDate? {
        return when (value) {
            is java.time.LocalDate -> value
            is String -> {
                try {
                    java.time.LocalDate.parse(value)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
    
    private fun coerceToDateTime(value: Any): java.time.LocalDateTime? {
        return when (value) {
            is java.time.LocalDateTime -> value
            is String -> {
                try {
                    java.time.LocalDateTime.parse(value)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
```

---

## Streaming & Large Files

```kotlin
package org.apache.utlx.core.udm

interface StreamingParser {
    fun parseStream(input: InputStream): Sequence<UDMNode>
}

class StreamingXMLParser : StreamingParser {
    override fun parseStream(input: InputStream): Sequence<UDMNode> = sequence {
        val reader = XMLInputFactory.newInstance().createXMLStreamReader(input)
        var currentElement: Element? = null
        
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    currentElement = Element(reader.localName)
                    // Process attributes
                    for (i in 0 until reader.attributeCount) {
                        currentElement.attributes[reader.getAttributeLocalName(i)] = 
                            reader.getAttributeValue(i)
                    }
                }
                XMLStreamConstants.CHARACTERS -> {
                    currentElement?.text = reader.text
                }
                XMLStreamConstants.END_ELEMENT -> {
                    if (currentElement != null) {
                        yield(convertElement(currentElement))
                        currentElement = null
                    }
                }
            }
        }
    }
}

// Usage
val parser = StreamingXMLParser()
parser.parseStream(File("large.xml").inputStream()).forEach { node ->
    // Process one node at a time
    transform(node)
}
```

---

## Memory Optimization

### Interning for Duplicate Values

```kotlin
class InternedUDMBuilder {
    private val stringCache = mutableMapOf<String, String>()
    private val numberCache = mutableMapOf<Number, Number>()
    
    fun buildScalar(value: Any?, type: ValueType): ScalarNode {
        val internedValue = when (value) {
            is String -> stringCache.getOrPut(value) { value }
            is Number -> numberCache.getOrPut(value) { value }
            else -> value
        }
        return ScalarNode(internedValue, type)
    }
}
```

### Flyweight Pattern for Metadata

```kotlin
class MetadataPool {
    private val pool = mutableMapOf<Metadata, Metadata>()
    
    fun intern(metadata: Metadata): Metadata {
        return pool.getOrPut(metadata) { metadata }
    }
}
```

---

## Extension Points

### Custom Value Types

```kotlin
// Add custom types to ValueType enum
enum class ValueType {
    // ... standard types ...
    CUSTOM_UUID,
    CUSTOM_EMAIL,
    CUSTOM_PHONE
}

// Custom validator
interface ValueValidator {
    fun validate(value: Any?, type: ValueType): Boolean
}
```

### Custom Parsers

```kotlin
interface CustomFormatParser {
    fun canParse(format: String): Boolean
    fun parse(input: String): UDMNode
}

// Register custom parser
val parserRegistry = ParserRegistry()
parserRegistry.register(ProtobufParser())
parserRegistry.register(AvroParser())
```

---

## Testing UDM Components

```kotlin
class UDMTest {
    @Test
    fun `should create scalar node`() {
        val node = ScalarNode("Hello", ValueType.STRING)
        assertEquals("Hello", node.value)
        assertEquals(ValueType.STRING, node.type)
    }
    
    @Test
    fun `should navigate object properties`() {
        val udm = ObjectNode(mapOf(
            "user" to ObjectNode(mapOf(
                "name" to ScalarNode("Alice", ValueType.STRING)
            ))
        ))
        
        val navigator = UDMNavigator(udm)
        val result = navigator.navigate("user.name")
        
        assertTrue(result is ScalarNode)
        assertEquals("Alice", (result as ScalarNode).value)
    }
    
    @Test
    fun `should handle wildcard navigation`() {
        val udm = ObjectNode(mapOf(
            "items" to ArrayNode(listOf(
                ObjectNode(mapOf("price" to ScalarNode(10.0, ValueType.NUMBER))),
                ObjectNode(mapOf("price" to ScalarNode(20.0, ValueType.NUMBER)))
            ))
        ))
        
        val navigator = AdvancedNavigator(udm)
        val results = navigator.navigate("items[*].price")
        
        assertEquals(2, results.size)
    }
}
```

---

## Summary

This advanced guide covers:

✅ **Parser patterns** for XML, JSON, CSV  
✅ **Serializer patterns** for all formats  
✅ **Advanced navigation** with path expressions  
✅ **Type coercion** and conversion  
✅ **Streaming** for large files  
✅ **Memory optimization** techniques  
✅ **Extension points** for customization  
✅ **Testing strategies** for UDM components

For more information, see the [UDM Complete Guide](./udm-complete-guide.md).

---

**Project:** UTL-X (Universal Transformation Language Extended)  
**Component:** UDM (Universal Data Model) - Advanced Implementation  
**Author:** Ir. Marcel A. Grauwen  
**License:** AGPL-3.0 / Commercial
