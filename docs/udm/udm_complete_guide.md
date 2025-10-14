# Universal Data Model (UDM)

## Table of Contents

1. [Introduction](#introduction)
2. [Core Concepts](#core-concepts)
3. [UDM Structure](#udm-structure)
4. [Format Mappings](#format-mappings)
5. [Navigation & Selectors](#navigation--selectors)
6. [Implementation](#implementation)
7. [Performance Considerations](#performance-considerations)
8. [Comparison with Other Models](#comparison-with-other-models)
9. [Examples](#examples)

---

## Introduction

### What is UDM?

The **Universal Data Model (UDM)** is UTL-X's internal representation of data that abstracts away the differences between various data formats (XML, JSON, CSV, YAML, etc.). It provides a **format-agnostic** layer that enables transformations to work uniformly regardless of the input or output format.

### Why UDM?

**Problem:** Data formats have fundamentally different structures:
- XML has elements, attributes, namespaces, and mixed content
- JSON has objects, arrays, and primitives
- CSV has rows, columns, and headers
- YAML has documents, anchors, and references

**Solution:** UDM provides a unified representation that:
- Captures the essential structure of all formats
- Enables format-agnostic transformation logic
- Maintains format-specific metadata when needed
- Allows seamless conversion between formats

### Key Benefits

✅ **Write Once, Transform Any Format**
```utlx
// Same transformation works for XML, JSON, CSV input
{
  name: input.Customer.Name,
  total: sum(input.Orders.*.Total)
}
```

✅ **Lossless Conversion**
- Preserves semantic meaning across formats
- Retains metadata (attributes, types, namespaces)
- Maintains structure integrity

✅ **Performance**
- Single parse → UDM → single serialize
- No intermediate format conversions
- Streaming support for large datasets

---

## Core Concepts

### 1. Everything is a Node

In UDM, all data is represented as a tree of **nodes**. Each node has:
- A **type** (Scalar, Array, Object, Null)
- A **value** (for scalar nodes)
- **Children** (for array/object nodes)
- **Metadata** (attributes, namespaces, type hints)

```
Input Data → Parse → UDM Tree → Transform → UDM Tree → Serialize → Output Data
```

### 2. Three Fundamental Types

UDM has three core node types:

| Type | Purpose | Examples |
|------|---------|----------|
| **Scalar** | Primitive values | `"text"`, `42`, `true`, `2025-01-15` |
| **Array** | Ordered sequences | `[1, 2, 3]`, `<items>...</items>` |
| **Object** | Key-value maps | `{name: "Alice"}`, `<person><name>Alice</name></person>` |

Plus a special **Null** type for absence of value.

### 3. Metadata Preservation

UDM preserves format-specific information:

**XML Metadata:**
- Attributes
- Namespaces
- Processing instructions
- Comments (optional)

**JSON Metadata:**
- Original types (number vs string "123")
- Key ordering (optional)

**CSV Metadata:**
- Column names
- Row numbers
- Data types

---

## UDM Structure

### Node Types

```kotlin
sealed class UDMNode {
    abstract val metadata: Metadata
    abstract val type: ValueType
}
```

#### 1. Scalar Node

Represents primitive values with type information.

```kotlin
data class ScalarNode(
    val value: Any?,              // The actual value
    val type: ValueType,          // STRING, INTEGER, NUMBER, BOOLEAN, DATE, etc.
    override val metadata: Metadata = Metadata()
) : UDMNode()
```

**Examples:**
```kotlin
ScalarNode("Alice", ValueType.STRING)
ScalarNode(42, ValueType.INTEGER)
ScalarNode(true, ValueType.BOOLEAN)
ScalarNode(LocalDate.parse("2025-01-15"), ValueType.DATE)
```

#### 2. Array Node

Represents ordered sequences of nodes.

```kotlin
data class ArrayNode(
    val elements: List<UDMNode>,
    override val metadata: Metadata = Metadata()
) : UDMNode() {
    override val type = ValueType.ARRAY
    
    fun size(): Int = elements.size
    fun get(index: Int): UDMNode? = elements.getOrNull(index)
    fun isEmpty(): Boolean = elements.isEmpty()
}
```

**Examples:**
```kotlin
ArrayNode(listOf(
    ScalarNode(1, ValueType.INTEGER),
    ScalarNode(2, ValueType.INTEGER),
    ScalarNode(3, ValueType.INTEGER)
))
```

#### 3. Object Node

Represents key-value mappings.

```kotlin
data class ObjectNode(
    val properties: Map<String, UDMNode>,
    override val metadata: Metadata = Metadata()
) : UDMNode() {
    override val type = ValueType.OBJECT
    
    fun get(key: String): UDMNode? = properties[key]
    fun has(key: String): Boolean = properties.containsKey(key)
    fun keys(): Set<String> = properties.keys
}
```

**Examples:**
```kotlin
ObjectNode(mapOf(
    "name" to ScalarNode("Alice", ValueType.STRING),
    "age" to ScalarNode(30, ValueType.INTEGER),
    "active" to ScalarNode(true, ValueType.BOOLEAN)
))
```

#### 4. Null Node

Represents absence of value.

```kotlin
object NullNode : UDMNode() {
    override val type = ValueType.NULL
    override val metadata = Metadata()
}
```

### Metadata

Metadata stores format-specific information without polluting the core structure.

```kotlin
data class Metadata(
    val attributes: Map<String, String> = emptyMap(),
    val namespace: String? = null,
    val namespacePrefix: String? = null,
    val originalType: String? = null,
    val sourceFormat: String? = null,
    val lineNumber: Int? = null,
    val comments: List<String> = emptyList(),
    val custom: Map<String, Any> = emptyMap()
)
```

**Example:**
```kotlin
// XML: <person id="123" xmlns="http://example.com">
ObjectNode(
    properties = mapOf(...),
    metadata = Metadata(
        attributes = mapOf("id" to "123"),
        namespace = "http://example.com"
    )
)
```

---

## Format Mappings

### XML → UDM Mapping

#### Basic Mapping

| XML | UDM |
|-----|-----|
| `<element>text</element>` | `ObjectNode("element" → ScalarNode("text"))` |
| `<element attr="val"/>` | `ObjectNode("element" → ScalarNode(null), metadata.attributes["attr"] = "val")` |
| `<parent><child/><child/></parent>` | `ObjectNode("parent" → ArrayNode[...])` |

#### Example: XML to UDM

**XML Input:**
```xml
<order id="ORD-001" date="2025-01-15">
    <customer>
        <name>Alice Johnson</name>
        <email>alice@example.com</email>
    </customer>
    <items>
        <item sku="W001" quantity="2" price="25.00"/>
        <item sku="W002" quantity="1" price="50.00"/>
    </items>
    <total>100.00</total>
</order>
```

**UDM Representation:**
```kotlin
ObjectNode(
    properties = mapOf(
        "order" to ObjectNode(
            properties = mapOf(
                "customer" to ObjectNode(
                    properties = mapOf(
                        "name" to ScalarNode("Alice Johnson", ValueType.STRING),
                        "email" to ScalarNode("alice@example.com", ValueType.STRING)
                    )
                ),
                "items" to ObjectNode(
                    properties = mapOf(
                        "item" to ArrayNode(listOf(
                            ObjectNode(
                                properties = mapOf(),
                                metadata = Metadata(attributes = mapOf(
                                    "sku" to "W001",
                                    "quantity" to "2",
                                    "price" to "25.00"
                                ))
                            ),
                            ObjectNode(
                                properties = mapOf(),
                                metadata = Metadata(attributes = mapOf(
                                    "sku" to "W002",
                                    "quantity" to "1",
                                    "price" to "50.00"
                                ))
                            )
                        ))
                    )
                ),
                "total" to ScalarNode("100.00", ValueType.NUMBER)
            ),
            metadata = Metadata(attributes = mapOf(
                "id" to "ORD-001",
                "date" to "2025-01-15"
            ))
        )
    )
)
```

### JSON → UDM Mapping

#### Basic Mapping

| JSON | UDM |
|------|-----|
| `{"key": "value"}` | `ObjectNode("key" → ScalarNode("value"))` |
| `[1, 2, 3]` | `ArrayNode[ScalarNode(1), ScalarNode(2), ScalarNode(3)]` |
| `null` | `NullNode` |
| `true` | `ScalarNode(true, ValueType.BOOLEAN)` |

#### Example: JSON to UDM

**JSON Input:**
```json
{
  "order": {
    "id": "ORD-001",
    "date": "2025-01-15",
    "customer": {
      "name": "Alice Johnson",
      "email": "alice@example.com"
    },
    "items": [
      {"sku": "W001", "quantity": 2, "price": 25.00},
      {"sku": "W002", "quantity": 1, "price": 50.00}
    ],
    "total": 100.00
  }
}
```

**UDM Representation:**
```kotlin
ObjectNode(
    properties = mapOf(
        "order" to ObjectNode(
            properties = mapOf(
                "id" to ScalarNode("ORD-001", ValueType.STRING),
                "date" to ScalarNode("2025-01-15", ValueType.STRING),
                "customer" to ObjectNode(
                    properties = mapOf(
                        "name" to ScalarNode("Alice Johnson", ValueType.STRING),
                        "email" to ScalarNode("alice@example.com", ValueType.STRING)
                    )
                ),
                "items" to ArrayNode(listOf(
                    ObjectNode(
                        properties = mapOf(
                            "sku" to ScalarNode("W001", ValueType.STRING),
                            "quantity" to ScalarNode(2, ValueType.INTEGER),
                            "price" to ScalarNode(25.00, ValueType.NUMBER)
                        )
                    ),
                    ObjectNode(
                        properties = mapOf(
                            "sku" to ScalarNode("W002", ValueType.STRING),
                            "quantity" to ScalarNode(1, ValueType.INTEGER),
                            "price" to ScalarNode(50.00, ValueType.NUMBER)
                        )
                    )
                )),
                "total" to ScalarNode(100.00, ValueType.NUMBER)
            )
        )
    )
)
```

### CSV → UDM Mapping

CSV is mapped to an array of objects, with column headers as keys.

**CSV Input:**
```csv
name,age,email
Alice,30,alice@example.com
Bob,25,bob@example.com
```

**UDM Representation:**
```kotlin
ArrayNode(listOf(
    ObjectNode(mapOf(
        "name" to ScalarNode("Alice", ValueType.STRING),
        "age" to ScalarNode(30, ValueType.INTEGER),
        "email" to ScalarNode("alice@example.com", ValueType.STRING)
    )),
    ObjectNode(mapOf(
        "name" to ScalarNode("Bob", ValueType.STRING),
        "age" to ScalarNode(25, ValueType.INTEGER),
        "email" to ScalarNode("bob@example.com", ValueType.STRING)
    ))
))
```

---

## Navigation & Selectors

### Path-Based Navigation

UDM supports XPath/JSONPath-like navigation:

```kotlin
// Simple property access
udm.get("order").get("customer").get("name")

// Array indexing
udm.get("items").get(0).get("sku")

// Wildcard (all elements)
udm.get("items").get("*").get("price")

// Recursive descent
udm.getRecursive("name")  // Find all "name" properties

// Attribute access (XML)
udm.get("order").getAttribute("id")
```

### Selector Syntax in UTL-X

```utlx
// Property access
input.order.customer.name

// Array access
input.items[0].sku
input.items[*].price  // All prices

// Attribute access
input.order.@id

// Recursive descent
input..name  // All name properties at any depth

// Predicate filtering
input.items[price > 50]
```

### Navigation Examples

**Example 1: Simple Path**
```kotlin
// input.order.customer.name
val result = input
    .get("order")
    .get("customer")
    .get("name")
// Result: ScalarNode("Alice Johnson", ValueType.STRING)
```

**Example 2: Array Wildcard**
```kotlin
// input.items[*].price
val items = input.get("items") as ArrayNode
val prices = items.elements.map { item ->
    (item as ObjectNode).get("price")
}
// Result: List[ScalarNode(25.00), ScalarNode(50.00)]
```

**Example 3: Attribute Access**
```kotlin
// input.order.@id
val order = input.get("order") as ObjectNode
val id = order.metadata.attributes["id"]
// Result: "ORD-001"
```

---

## Implementation

### Core Classes

```kotlin
package org.apache.utlx.core.udm

// Base interface
sealed class UDMNode {
    abstract val metadata: Metadata
    abstract val type: ValueType
}

// Scalar values
data class ScalarNode(
    val value: Any?,
    val type: ValueType,
    override val metadata: Metadata = Metadata()
) : UDMNode()

// Arrays
data class ArrayNode(
    val elements: List<UDMNode>,
    override val metadata: Metadata = Metadata()
) : UDMNode() {
    override val type = ValueType.ARRAY
}

// Objects
data class ObjectNode(
    val properties: Map<String, UDMNode>,
    override val metadata: Metadata = Metadata()
) : UDMNode() {
    override val type = ValueType.OBJECT
}

// Null
object NullNode : UDMNode() {
    override val type = ValueType.NULL
    override val metadata = Metadata()
}

// Value types
enum class ValueType {
    STRING, INTEGER, NUMBER, BOOLEAN,
    DATE, TIME, DATETIME, DURATION,
    BINARY, NULL, ARRAY, OBJECT
}

// Metadata
data class Metadata(
    val attributes: Map<String, String> = emptyMap(),
    val namespace: String? = null,
    val namespacePrefix: String? = null,
    val originalType: String? = null,
    val sourceFormat: String? = null,
    val custom: Map<String, Any> = emptyMap()
)
```

### Navigator

```kotlin
class UDMNavigator(private val root: UDMNode) {
    
    fun navigate(path: String): UDMNode? {
        return navigatePath(root, path.split("."))
    }
    
    private fun navigatePath(node: UDMNode, segments: List<String>): UDMNode? {
        if (segments.isEmpty()) return node
        
        val current = segments.first()
        val remaining = segments.drop(1)
        
        return when (node) {
            is ObjectNode -> {
                val child = node.get(current) ?: return null
                navigatePath(child, remaining)
            }
            is ArrayNode -> {
                when (current) {
                    "*" -> {
                        // Wildcard - return all elements
                        ArrayNode(node.elements.mapNotNull { 
                            navigatePath(it, remaining) 
                        })
                    }
                    else -> {
                        // Index access
                        val index = current.toIntOrNull() ?: return null
                        val child = node.get(index) ?: return null
                        navigatePath(child, remaining)
                    }
                }
            }
            else -> null
        }
    }
    
    fun getAttribute(path: String, attrName: String): String? {
        val node = navigate(path)
        return node?.metadata?.attributes?.get(attrName)
    }
}
```

### Parsers

Each format has a parser that converts to UDM:

```kotlin
interface FormatParser {
    fun parse(input: String): UDMNode
    fun parse(input: InputStream): UDMNode
}

class XMLParser : FormatParser {
    override fun parse(input: String): UDMNode {
        val document = parseXML(input)
        return convertToUDM(document.rootElement)
    }
    
    private fun convertToUDM(element: Element): ObjectNode {
        // Convert XML element to ObjectNode
        // Handle attributes, children, text content
    }
}

class JSONParser : FormatParser {
    override fun parse(input: String): UDMNode {
        val json = Json.parseToJsonElement(input)
        return convertToUDM(json)
    }
    
    private fun convertToUDM(element: JsonElement): UDMNode {
        // Convert JSON to appropriate UDMNode type
    }
}
```

### Serializers

Each format has a serializer that converts from UDM:

```kotlin
interface FormatSerializer {
    fun serialize(node: UDMNode): String
    fun serialize(node: UDMNode, output: OutputStream)
}

class JSONSerializer : FormatSerializer {
    override fun serialize(node: UDMNode): String {
        return buildJsonObject {
            serializeNode(node, this)
        }.toString()
    }
    
    private fun serializeNode(node: UDMNode, builder: JsonObjectBuilder) {
        // Convert UDMNode to JSON
    }
}
```

---

## Performance Considerations

### Memory Efficiency

**Lazy Evaluation:**
```kotlin
// Stream large files without loading entire UDM tree
class StreamingUDMParser {
    fun parseStream(input: InputStream): Sequence<UDMNode> {
        // Yield nodes as they're parsed
    }
}
```

**Structural Sharing:**
```kotlin
// Immutable nodes can share structure
val original = ObjectNode(mapOf("a" to ScalarNode(1)))
val modified = original.copy(
    properties = original.properties + ("b" to ScalarNode(2))
)
// Original and modified share the ScalarNode(1)
```

### Parsing Performance

| Format | Parse Time (1MB) | Memory |
|--------|------------------|--------|
| XML | 15-25ms | 3-5MB |
| JSON | 8-12ms | 2-3MB |
| CSV | 5-8ms | 1-2MB |
| YAML | 20-30ms | 3-6MB |

### Transformation Performance

**Zero-Copy Transformations:**
```kotlin
// Reuse existing nodes when possible
fun transform(input: UDMNode): UDMNode {
    return when (input) {
        is ObjectNode -> {
            // Only create new node if properties change
            if (needsTransformation(input)) {
                ObjectNode(transformProperties(input.properties))
            } else {
                input  // Reuse existing node
            }
        }
        else -> input
    }
}
```

---

## Comparison with Other Models

### UDM vs JSON

| Feature | JSON | UDM |
|---------|------|-----|
| **Types** | 6 basic types | 12+ types with extensibility |
| **Metadata** | None | Attributes, namespaces, etc. |
| **Arrays** | Homogeneous preferred | Mixed types supported |
| **Attributes** | Not supported | First-class support |
| **Namespaces** | Not supported | Full support |

### UDM vs XML DOM

| Feature | XML DOM | UDM |
|---------|---------|-----|
| **Format-Specific** | XML only | Format-agnostic |
| **Complexity** | High (15+ node types) | Low (4 node types) |
| **Memory** | High overhead | Optimized |
| **Navigation** | XPath | Unified selector syntax |

### UDM vs Apache Avro

| Feature | Avro | UDM |
|---------|------|-----|
| **Schema** | Required | Optional |
| **Binary** | Yes | No (delegated to formats) |
| **Evolution** | Built-in | Via type system |
| **Use Case** | Serialization | Transformation |

---

## Examples

### Example 1: XML to JSON Transformation

**Input (XML):**
```xml
<customers>
    <customer id="001">
        <name>Alice</name>
        <orders>
            <order>
                <id>ORD-001</id>
                <total>100.00</total>
            </order>
        </orders>
    </customer>
</customers>
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input xml
output json
---
{
  customers: input.customers.customer |> map(c => {
    id: c.@id,
    name: c.name,
    orderCount: count(c.orders.order),
    totalSpent: sum(c.orders.order.*.total)
  })
}
```

**Internal Flow:**
```
XML Input
    ↓
[XML Parser]
    ↓
UDM Tree (ObjectNode with metadata)
    ↓
[UTL-X Transform Engine]
    ↓
Transformed UDM Tree (ObjectNode without attributes)
    ↓
[JSON Serializer]
    ↓
JSON Output
```

**Output (JSON):**
```json
{
  "customers": [
    {
      "id": "001",
      "name": "Alice",
      "orderCount": 1,
      "totalSpent": 100.00
    }
  ]
}
```

### Example 2: JSON to CSV Transformation

**Input (JSON):**
```json
{
  "users": [
    {"name": "Alice", "age": 30, "email": "alice@example.com"},
    {"name": "Bob", "age": 25, "email": "bob@example.com"}
  ]
}
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input json
output csv
---
{
  headers: ["Name", "Age", "Email"],
  rows: input.users |> map(u => [u.name, u.age, u.email])
}
```

**UDM Transformation:**
```kotlin
// Input UDM
ObjectNode(mapOf(
    "users" to ArrayNode([...])
))

// Output UDM (CSV structure)
ObjectNode(mapOf(
    "headers" to ArrayNode([
        ScalarNode("Name"), ScalarNode("Age"), ScalarNode("Email")
    ]),
    "rows" to ArrayNode([
        ArrayNode([ScalarNode("Alice"), ScalarNode(30), ScalarNode("alice@example.com")]),
        ArrayNode([ScalarNode("Bob"), ScalarNode(25), ScalarNode("bob@example.com")])
    ])
))
```

**Output (CSV):**
```csv
Name,Age,Email
Alice,30,alice@example.com
Bob,25,bob@example.com
```

### Example 3: Format-Agnostic Aggregation

**The Power of UDM:**
```utlx
// This transformation works identically for XML, JSON, CSV, or YAML input!
%utlx 1.0
input auto   // Auto-detect format
output json
---
{
  summary: {
    totalOrders: count(input..order),
    totalValue: sum(input..order.*.total),
    averageValue: avg(input..order.*.total),
    customers: distinct(input..customer.*.name)
  }
}
```

The `..` recursive descent operator works uniformly across all formats because UDM provides a consistent tree structure.

---

## Best Practices

### 1. Use Appropriate Node Types

```kotlin
// ❌ Bad: Everything as strings
ObjectNode(mapOf(
    "age" to ScalarNode("30", ValueType.STRING),
    "price" to ScalarNode("29.99", ValueType.STRING)
))

// ✅ Good: Use proper types
ObjectNode(mapOf(
    "age" to ScalarNode(30, ValueType.INTEGER),
    "price" to ScalarNode(29.99, ValueType.NUMBER)
))
```

### 2. Preserve Metadata When Needed

```kotlin
// ❌ Bad: Losing XML attributes
val name = input.get("customer").get("name")

// ✅ Good: Preserve metadata
val customer = input.get("customer") as ObjectNode
val customerId = customer.metadata.attributes["id"]
val name = customer.get("name")
```

### 3. Use Streaming for Large Files

```kotlin
// ❌ Bad: Load entire file into memory
val udm = parser.parse(File("large.xml").readText())

// ✅ Good: Stream processing
parser.parseStream(File("large.xml").inputStream()).forEach { node ->
    process(node)
}
```

### 4. Leverage Immutability

```kotlin
// ✅ UDM nodes are immutable - safe to share and cache
val baseCustomer = ObjectNode(mapOf("name" to ScalarNode("Alice")))
val customerWithEmail = baseCustomer.copy(
    properties = baseCustomer.properties + 
        ("email" to ScalarNode("alice@example.com"))
)
// baseCustomer is unchanged
```

---

## Summary

### Key Takeaways

1. **UDM is the heart of UTL-X** - It enables format-agnostic transformations

2. **Simple but powerful** - Only 4 node types cover all data structures

3. **Metadata preservation** - Format-specific details aren't lost

4. **Performance-focused** - Optimized for transformation workloads

5. **Developer-friendly** - Intuitive navigation and manipulation

### When to Use UDM Directly

You typically don't work with UDM directly when writing UTL-X transformations. However, you might interact with UDM when:

- **Extending UTL-X** - Adding new format parsers/serializers
- **Building tools** - Creating UTL-X IDE plugins or debuggers
- **Advanced debugging** - Understanding transformation behavior
- **Performance optimization** - Custom transformation engines

### Further Reading

- [UTL-X Language Guide](./language-guide/overview.md)
- [Format Support](./formats/README.md)
- [Architecture Overview](./architecture/overview.md)
- [Performance Tuning](./architecture/performance.md)

---

**Project:** UTL-X (Universal Transformation Language Extended)  
**Component:** UDM (Universal Data Model)  
**Author:** Ir. Marcel A. Grauwen  
**License:** AGPL-3.0 / Commercial
