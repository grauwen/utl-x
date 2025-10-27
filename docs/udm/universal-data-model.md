# Universal Data Model (UDM)

The Universal Data Model is UTL-X's internal representation of data, abstracting over all input/output formats.

## Overview

UDM is a tree structure that can represent data from any format (XML, JSON, CSV, etc.) in a uniform way.

```
UDM
├── Scalar (primitive values)
├── Array (ordered collection)
└── Object (key-value pairs)
```

## Type Hierarchy

```kotlin
sealed class UDM {
    data class Scalar(val value: Any) : UDM()
    data class Array(val elements: List<UDM>) : UDM()
    data class Object(
        val properties: Map<String, UDM>,
        val attributes: Map<String, String> = emptyMap()
    ) : UDM()
}
```

## Scalar Types

```kotlin
sealed class ScalarValue {
    data class StringValue(val value: String) : ScalarValue()
    data class NumberValue(val value: Double) : ScalarValue()
    data class BooleanValue(val value: Boolean) : ScalarValue()
    object NullValue : ScalarValue()
    data class DateValue(val value: Instant) : ScalarValue()
}
```

## Format Mapping

### XML to UDM

**XML:**
```xml
<Order id="123">
  <Customer>Alice</Customer>
  <Total>100.00</Total>
</Order>
```

**UDM:**
```kotlin
UDM.Object(
    properties = mapOf(
        "Customer" to UDM.Scalar(StringValue("Alice")),
        "Total" to UDM.Scalar(StringValue("100.00"))
    ),
    attributes = mapOf(
        "id" to "123"
    )
)
```

### JSON to UDM

**JSON:**
```json
{
  "order": {
    "id": "123",
    "customer": "Alice",
    "total": 100.00
  }
}
```

**UDM:**
```kotlin
UDM.Object(
    properties = mapOf(
        "order" to UDM.Object(
            properties = mapOf(
                "id" to UDM.Scalar(StringValue("123")),
                "customer" to UDM.Scalar(StringValue("Alice")),
                "total" to UDM.Scalar(NumberValue(100.00))
            )
        )
    )
)
```

### CSV to UDM

**CSV:**
```csv
id,customer,total
123,Alice,100.00
```

**UDM:**
```kotlin
UDM.Object(
    properties = mapOf(
        "rows" to UDM.Array(
            elements = listOf(
                UDM.Object(
                    properties = mapOf(
                        "id" to UDM.Scalar(StringValue("123")),
                        "customer" to UDM.Scalar(StringValue("Alice")),
                        "total" to UDM.Scalar(StringValue("100.00"))
                    )
                )
            )
        ),
        "headers" to UDM.Array(
            elements = listOf(
                UDM.Scalar(StringValue("id")),
                UDM.Scalar(StringValue("customer")),
                UDM.Scalar(StringValue("total"))
            )
        )
    )
)
```

## Operations

### Navigation

```kotlin
fun UDM.get(path: String): UDM? {
    return when (this) {
        is UDM.Object -> properties[path]
        else -> null
    }
}

fun UDM.getAttribute(name: String): String? {
    return when (this) {
        is UDM.Object -> attributes[name]
        else -> null
    }
}
```

### Transformation

```kotlin
fun UDM.map(fn: (UDM) -> UDM): UDM {
    return when (this) {
        is UDM.Array -> UDM.Array(elements.map(fn))
        else -> fn(this)
    }
}

fun UDM.filter(predicate: (UDM) -> Boolean): UDM {
    return when (this) {
        is UDM.Array -> UDM.Array(elements.filter(predicate))
        else -> this
    }
}
```

## Memory Model

### Immutability

All UDM nodes are immutable. Modifications create new nodes:

```kotlin
val original = UDM.Object(
    properties = mapOf("x" to UDM.Scalar(NumberValue(42.0)))
)

val modified = original.copy(
    properties = original.properties + ("y" to UDM.Scalar(NumberValue(100.0)))
)

// original unchanged
assert(original.properties.size == 1)
assert(modified.properties.size == 2)
```

### Structural Sharing

To minimize memory usage, unchanged parts are shared:

```kotlin
val array = UDM.Array(listOf(
    UDM.Scalar(NumberValue(1.0)),
    UDM.Scalar(NumberValue(2.0)),
    UDM.Scalar(NumberValue(3.0))
))

val modified = array.copy(
    elements = array.elements + UDM.Scalar(NumberValue(4.0))
)

// First 3 elements are shared
assert(array.elements[0] === modified.elements[0])
```

## Performance

### Time Complexity

- **Access**: O(1) for properties, O(log n) for deep paths
- **Transformation**: O(n) where n = number of nodes
- **Serialization**: O(n)

### Space Complexity

- **Storage**: O(n) where n = data size
- **Shared structure**: Reduces overhead significantly

### Benchmarks

```
Operation               Time (µs)    Memory (KB)
--------------------------------------------------
Parse JSON → UDM        10-50        Proportional
Transform UDM           5-20         Minimal (shared)
Serialize UDM → JSON    10-50        Proportional
```

## Example: Complete Flow

### Input XML

```xml
<Order id="123">
  <Items>
    <Item sku="A" price="10"/>
    <Item sku="B" price="20"/>
  </Items>
</Order>
```

### Parse to UDM

```kotlin
val udm = XMLParser().parse(inputXML)
// UDM.Object(
//   properties = {"Items": UDM.Object(...)},
//   attributes = {"id": "123"}
// )
```

### Transform

```kotlin
val transformed = transform(udm)
// UDM.Object(
//   properties = {
//     "orderId": UDM.Scalar("123"),
//     "items": UDM.Array([...])
//   }
// )
```

### Serialize to JSON

```kotlin
val output = JSONSerializer().serialize(transformed)
// {"orderId": "123", "items": [...]}
```

---
