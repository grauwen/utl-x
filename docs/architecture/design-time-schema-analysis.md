# Design-Time Schema Analysis and Type System

**Version**: 1.0
**Date**: 2025-10-30
**Status**: Design Proposal
**Author**: UTL-X Architecture Team

---

## Executive Summary

This document defines a **design-time type system** and **static schema analysis** capability for UTL-X that enables:

1. **Schema Transformation Analysis**: Given input schema (XSD/JSON Schema/Avro) and UTLX transformation → produce output schema
2. **Contract Validation**: Verify transformations produce expected output schemas at design time
3. **Early Error Detection**: Catch type errors before runtime
4. **API Integration**: Seamless integration with API design tools (ec-api-design)
5. **IDE Support**: Enable autocomplete, type hints, and error highlighting

### Key Distinction

| Aspect | Runtime (Current) | Design-Time (New) |
|--------|------------------|-------------------|
| **Input** | XML/JSON data | XSD/JSON Schema |
| **Process** | Execute transformation | Analyze transformation |
| **Output** | XML/JSON data | XSD/JSON Schema (metadata) |
| **Purpose** | Transform data | Validate contracts, detect errors |
| **When** | Production execution | Development, CI/CD |

### Strategic Value

```
ec-api-design → XSD → UTL-X Analysis → JSON Schema → API Gateway
   (design)      ↓         (validation)         ↓         (runtime)
              XML Data → UTL-X Runtime → JSON Data → API Consumer
```

This enables **design-time validation** of entire transformation pipelines before deployment.

---

## Table of Contents

1. [Problem Statement](#problem-statement)
2. [Core Concepts](#core-concepts)
3. [UDMType System](#udmtype-system)
4. [Schema to Type Environment](#schema-to-type-environment)
5. [Type Inference Engine](#type-inference-engine)
6. [Function Type Signatures](#function-type-signatures)
7. [Output Schema Generation](#output-schema-generation)
8. [Architecture and Data Flow](#architecture-and-data-flow)
9. [CLI Integration](#cli-integration)
10. [Use Cases and Examples](#use-cases-and-examples)
11. [Implementation Phases](#implementation-phases)
12. [Challenges and Solutions](#challenges-and-solutions)
13. [Integration Points](#integration-points)
14. [Technical Specifications](#technical-specifications)

---

## Problem Statement

### The Challenge

**Current capability**: UTL-X transforms data at runtime
```
order.xml + transform.utlx → invoice.json
```

**Missing capability**: Analyze transformation structure at design-time
```
order.xsd + transform.utlx → invoice.schema.json
```

### Why This Matters

#### Scenario: API Integration

A developer creates an API transformation:

1. **API Design Tool** (ec-api-design) generates `request.xsd`
2. Developer writes `transform.utlx` to process requests
3. **API Gateway** expects responses matching `response.schema.json`

**Current Problem**:
- ❌ No way to verify transformation produces correct schema until runtime
- ❌ Type errors discovered in production
- ❌ Contract mismatches cause API failures

**With Design-Time Analysis**:
- ✅ Verify at development time: `request.xsd + transform.utlx → response.schema.json`
- ✅ Validate against expected contract
- ✅ Catch errors before deployment
- ✅ Generate documentation automatically

### Real-World Example

**Input Schema (order.xsd)**:
```xml
<xs:schema>
  <xs:element name="Order">
    <xs:complexType>
      <xs:attribute name="id" type="xs:string"/>
      <xs:sequence>
        <xs:element name="Items">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="Item" maxOccurs="unbounded">
                <xs:complexType>
                  <xs:attribute name="sku" type="xs:string"/>
                  <xs:attribute name="quantity" type="xs:int"/>
                  <xs:attribute name="price" type="xs:decimal"/>
                </xs:complexType>
              </xs:element>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Transformation (transform.utlx)**:
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    orderId: input.Order.@id,
    total: sum(input.Order.Items.Item |> map(item =>
      parseNumber(item.@quantity) * parseNumber(item.@price)
    ))
  }
}
```

**Design-Time Analysis** produces **Output Schema**:
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "invoice": {
      "type": "object",
      "properties": {
        "orderId": {"type": "string"},
        "total": {"type": "number"}
      },
      "required": ["orderId", "total"]
    }
  },
  "required": ["invoice"]
}
```

**Without parseNumber()** - Design-Time Error:
```utlx
total: sum(input.Order.Items.Item |> map(item => item.@price))
       ^^^
Type Error at line 8, column 11:
  Function 'sum' expects Array<Number> but got Array<String>
  Hint: item.@price is type 'xs:string' according to order.xsd
  Suggestion: Use parseNumber(item.@price)
```

This catches the error **before deployment**, not in production!

---

## Core Concepts

### Design-Time UDM vs Runtime UDM

#### Runtime UDM (Existing)

**Represents actual data values**:
```kotlin
sealed class UDM {
    data class Scalar(val value: Any?) : UDM()
    data class Array(val elements: List<UDM>) : UDM()
    data class Object(
        val properties: Map<String, UDM>,
        val metadata: Map<String, String> = emptyMap()
    ) : UDM()
}

// Example: Runtime data
val orderData = UDM.Object(mapOf(
    "Order" to UDM.Object(mapOf(
        "@id" to UDM.Scalar("ORD-001"),
        "Customer" to UDM.Object(mapOf(
            "Name" to UDM.Scalar("John Doe")
        ))
    ))
))
```

#### Design-Time UDM (New)

**Represents types, not values**:
```kotlin
sealed class UDMType {
    object StringType : UDMType()
    object NumberType : UDMType()
    object IntegerType : UDMType()
    object BooleanType : UDMType()
    object NullType : UDMType()
    object AnyType : UDMType()  // Unknown/dynamic type

    data class ArrayType(val elementType: UDMType) : UDMType()

    data class ObjectType(
        val properties: Map<String, PropertyInfo>,
        val additionalProperties: Boolean = true
    ) : UDMType()

    data class UnionType(val types: Set<UDMType>) : UDMType()

    data class GenericType(val name: String) : UDMType()  // For function generics

    data class FunctionType(
        val params: List<UDMType>,
        val returnType: UDMType
    ) : UDMType()
}

data class PropertyInfo(
    val type: UDMType,
    val required: Boolean = true,
    val minOccurs: Int = 1,
    val maxOccurs: Int = 1  // -1 for unbounded
)

// Example: Type information
val orderType = UDMType.ObjectType(mapOf(
    "Order" to PropertyInfo(
        type = UDMType.ObjectType(mapOf(
            "@id" to PropertyInfo(
                type = UDMType.StringType,
                required = true
            ),
            "Customer" to PropertyInfo(
                type = UDMType.ObjectType(mapOf(
                    "Name" to PropertyInfo(type = UDMType.StringType)
                )),
                required = true
            )
        ))
    )
))
```

### Type Environment

A **type environment** maps paths to types:

```kotlin
class TypeEnvironment {
    private val bindings = mutableMapOf<String, UDMType>()

    fun bind(path: String, type: UDMType) {
        bindings[path] = type
    }

    fun lookup(path: String): UDMType? {
        return bindings[path]
    }

    fun lookupNested(path: String): UDMType? {
        // Handle paths like "input.Order.Customer.Name"
        val parts = path.split(".")
        var currentType: UDMType? = bindings[parts[0]]

        for (i in 1 until parts.size) {
            currentType = when (currentType) {
                is UDMType.ObjectType -> {
                    val propertyInfo = currentType.properties[parts[i]]
                    propertyInfo?.type
                }
                is UDMType.ArrayType -> {
                    // Array access - return element type
                    currentType.elementType
                }
                else -> null
            }
        }

        return currentType
    }
}

// Example usage:
val env = TypeEnvironment()
env.bind("input", orderType)
env.lookupNested("input.Order.@id")  // Returns: UDMType.StringType
env.lookupNested("input.Order.Customer.Name")  // Returns: UDMType.StringType
```

---

## UDMType System

### Complete Type Hierarchy

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/UDMType.kt

package org.apache.utlx.analysis.types

/**
 * Type-level representation of UDM for static analysis.
 * This is the foundation of design-time schema analysis.
 */
sealed class UDMType {

    // Primitive types
    object StringType : UDMType() {
        override fun toString() = "String"
    }

    object NumberType : UDMType() {
        override fun toString() = "Number"
    }

    object IntegerType : UDMType() {
        override fun toString() = "Integer"
    }

    object BooleanType : UDMType() {
        override fun toString() = "Boolean"
    }

    object NullType : UDMType() {
        override fun toString() = "Null"
    }

    object AnyType : UDMType() {
        override fun toString() = "Any"
    }

    // Complex types
    data class ArrayType(val elementType: UDMType) : UDMType() {
        override fun toString() = "Array<$elementType>"
    }

    data class ObjectType(
        val properties: Map<String, PropertyInfo>,
        val additionalProperties: Boolean = true
    ) : UDMType() {
        override fun toString(): String {
            val props = properties.entries.joinToString(", ") { (k, v) ->
                "$k: ${v.type}${if (!v.required) "?" else ""}"
            }
            return "{$props}"
        }
    }

    data class UnionType(val types: Set<UDMType>) : UDMType() {
        constructor(vararg types: UDMType) : this(types.toSet())

        override fun toString() = types.joinToString(" | ")

        fun isNullable() = types.contains(NullType)
        fun nonNullTypes() = types.filter { it != NullType }.toSet()
    }

    // Generic type parameter (e.g., T in map<T, R>)
    data class GenericType(val name: String) : UDMType() {
        override fun toString() = name
    }

    // Function type
    data class FunctionType(
        val params: List<UDMType>,
        val returnType: UDMType
    ) : UDMType() {
        override fun toString() = "(${params.joinToString(", ")}) -> $returnType"
    }

    // Type operations
    fun isSubtypeOf(other: UDMType): Boolean = when {
        other is AnyType -> true
        this == other -> true
        this is NullType && other is UnionType -> other.isNullable()
        this is IntegerType && other is NumberType -> true
        this is ArrayType && other is ArrayType ->
            elementType.isSubtypeOf(other.elementType)
        this is ObjectType && other is ObjectType -> {
            // Structural subtyping
            other.properties.all { (key, otherProp) ->
                val thisProp = properties[key]
                thisProp != null &&
                thisProp.type.isSubtypeOf(otherProp.type) &&
                (!otherProp.required || thisProp.required)
            }
        }
        this is UnionType -> types.all { it.isSubtypeOf(other) }
        else -> false
    }

    fun merge(other: UDMType): UDMType = when {
        this == other -> this
        this is AnyType || other is AnyType -> AnyType
        this is UnionType && other is UnionType ->
            UnionType(types + other.types)
        this is UnionType ->
            UnionType(types + other)
        other is UnionType ->
            UnionType(other.types + this)
        else -> UnionType(this, other)
    }
}

/**
 * Information about an object property or schema field.
 */
data class PropertyInfo(
    val type: UDMType,
    val required: Boolean = true,
    val minOccurs: Int = 1,
    val maxOccurs: Int = 1,  // -1 for unbounded
    val description: String? = null
) {
    val isOptional get() = !required || minOccurs == 0
    val isArray get() = maxOccurs < 0 || maxOccurs > 1
}
```

### Type Utilities

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/TypeUtils.kt

object TypeUtils {

    /**
     * Make a type nullable (add null to union)
     */
    fun makeNullable(type: UDMType): UDMType = when (type) {
        is UDMType.UnionType ->
            if (type.isNullable()) type
            else UDMType.UnionType(type.types + UDMType.NullType)
        UDMType.NullType -> type
        else -> UDMType.UnionType(type, UDMType.NullType)
    }

    /**
     * Remove null from a type
     */
    fun makeNonNullable(type: UDMType): UDMType = when (type) {
        is UDMType.UnionType -> {
            val nonNull = type.nonNullTypes()
            when (nonNull.size) {
                0 -> UDMType.NullType
                1 -> nonNull.first()
                else -> UDMType.UnionType(nonNull)
            }
        }
        else -> type
    }

    /**
     * Unify two types (find common supertype)
     */
    fun unify(t1: UDMType, t2: UDMType): UDMType = when {
        t1 == t2 -> t1
        t1.isSubtypeOf(t2) -> t2
        t2.isSubtypeOf(t1) -> t1
        t1 is UDMType.IntegerType && t2 is UDMType.NumberType -> t2
        t2 is UDMType.IntegerType && t1 is UDMType.NumberType -> t1
        else -> UDMType.UnionType(t1, t2)
    }

    /**
     * Substitute generic type parameters
     */
    fun substitute(
        type: UDMType,
        substitutions: Map<String, UDMType>
    ): UDMType = when (type) {
        is UDMType.GenericType -> substitutions[type.name] ?: type
        is UDMType.ArrayType ->
            UDMType.ArrayType(substitute(type.elementType, substitutions))
        is UDMType.ObjectType ->
            UDMType.ObjectType(
                type.properties.mapValues { (_, prop) ->
                    prop.copy(type = substitute(prop.type, substitutions))
                },
                type.additionalProperties
            )
        is UDMType.UnionType ->
            UDMType.UnionType(type.types.map { substitute(it, substitutions) }.toSet())
        is UDMType.FunctionType ->
            UDMType.FunctionType(
                type.params.map { substitute(it, substitutions) },
                substitute(type.returnType, substitutions)
            )
        else -> type
    }
}
```

---

## Schema to Type Environment

### XSD to UDMType Conversion

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/XSDAnalyzer.kt

class XSDAnalyzer {

    fun buildTypeEnvironment(xsd: XSDSchema): TypeEnvironment {
        val env = TypeEnvironment()

        // Process top-level elements
        xsd.elements.forEach { element ->
            val path = "input.${element.name}"
            val type = convertElement(element)
            env.bind(path, type)
        }

        return env
    }

    private fun convertElement(element: XSDElement): UDMType {
        return when (val type = element.type) {
            is XSDSimpleType -> convertSimpleType(type)
            is XSDComplexType -> convertComplexType(type, element)
            else -> UDMType.AnyType
        }
    }

    private fun convertSimpleType(simpleType: XSDSimpleType): UDMType {
        return when (simpleType.baseType) {
            "xs:string", "xs:token", "xs:normalizedString" -> UDMType.StringType
            "xs:int", "xs:integer", "xs:long", "xs:short", "xs:byte",
            "xs:positiveInteger", "xs:nonNegativeInteger" -> UDMType.IntegerType
            "xs:decimal", "xs:double", "xs:float" -> UDMType.NumberType
            "xs:boolean" -> UDMType.BooleanType
            "xs:date", "xs:dateTime", "xs:time" -> UDMType.StringType  // Dates as strings
            "xs:anyURI" -> UDMType.StringType
            else -> UDMType.StringType  // Default to string
        }
    }

    private fun convertComplexType(
        complexType: XSDComplexType,
        element: XSDElement
    ): UDMType {
        val properties = mutableMapOf<String, PropertyInfo>()

        // Process attributes
        complexType.attributes.forEach { attr ->
            properties["@${attr.name}"] = PropertyInfo(
                type = convertSimpleType(attr.type as XSDSimpleType),
                required = attr.use == "required",
                minOccurs = if (attr.use == "required") 1 else 0,
                maxOccurs = 1
            )
        }

        // Process child elements
        when (val content = complexType.content) {
            is XSDSequence -> {
                content.elements.forEach { child ->
                    val childType = convertElement(child)

                    // Handle array elements (maxOccurs > 1 or unbounded)
                    val propertyType = if (child.maxOccurs > 1 || child.maxOccurs == -1) {
                        UDMType.ArrayType(childType)
                    } else {
                        childType
                    }

                    properties[child.name] = PropertyInfo(
                        type = propertyType,
                        required = child.minOccurs > 0,
                        minOccurs = child.minOccurs,
                        maxOccurs = child.maxOccurs
                    )
                }
            }
            is XSDChoice -> {
                // Choice creates union types
                val choices = content.elements.map { convertElement(it) }
                val unionType = UDMType.UnionType(choices.toSet())

                // Represent as flexible object
                properties["_choice"] = PropertyInfo(
                    type = unionType,
                    required = content.minOccurs > 0
                )
            }
            is XSDAll -> {
                // All elements can appear in any order
                content.elements.forEach { child ->
                    properties[child.name] = PropertyInfo(
                        type = convertElement(child),
                        required = child.minOccurs > 0
                    )
                }
            }
        }

        return UDMType.ObjectType(properties)
    }
}

// Example usage:
val xsdContent = """
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
  <xs:element name="Order">
    <xs:complexType>
      <xs:attribute name="id" type="xs:string" use="required"/>
      <xs:sequence>
        <xs:element name="Item" maxOccurs="unbounded">
          <xs:complexType>
            <xs:attribute name="sku" type="xs:string"/>
            <xs:attribute name="qty" type="xs:int"/>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
"""

val xsd = XSDParser.parse(xsdContent)
val analyzer = XSDAnalyzer()
val typeEnv = analyzer.buildTypeEnvironment(xsd)

// Type environment contains:
// input.Order → ObjectType
// input.Order.@id → StringType (required)
// input.Order.Item → ArrayType(ObjectType)
// input.Order.Item[].@sku → StringType
// input.Order.Item[].@qty → IntegerType
```

### JSON Schema to UDMType Conversion

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaAnalyzer.kt

class JSONSchemaAnalyzer {

    fun buildTypeEnvironment(schema: JSONSchema): TypeEnvironment {
        val env = TypeEnvironment()
        val rootType = convertSchema(schema)
        env.bind("input", rootType)
        return env
    }

    private fun convertSchema(schema: JSONSchema): UDMType {
        // Handle $ref references
        if (schema.ref != null) {
            return resolveReference(schema.ref)
        }

        // Handle type
        return when {
            schema.type != null -> convertByType(schema)
            schema.oneOf != null -> convertOneOf(schema.oneOf)
            schema.anyOf != null -> convertAnyOf(schema.anyOf)
            schema.allOf != null -> convertAllOf(schema.allOf)
            else -> UDMType.AnyType
        }
    }

    private fun convertByType(schema: JSONSchema): UDMType {
        return when (schema.type) {
            "string" -> UDMType.StringType
            "number" -> UDMType.NumberType
            "integer" -> UDMType.IntegerType
            "boolean" -> UDMType.BooleanType
            "null" -> UDMType.NullType
            "array" -> convertArray(schema)
            "object" -> convertObject(schema)
            else -> UDMType.AnyType
        }
    }

    private fun convertArray(schema: JSONSchema): UDMType {
        val itemType = schema.items?.let { convertSchema(it) } ?: UDMType.AnyType
        return UDMType.ArrayType(itemType)
    }

    private fun convertObject(schema: JSONSchema): UDMType {
        val properties = schema.properties?.mapValues { (name, propSchema) ->
            PropertyInfo(
                type = convertSchema(propSchema),
                required = schema.required?.contains(name) ?: false
            )
        } ?: emptyMap()

        return UDMType.ObjectType(
            properties,
            additionalProperties = schema.additionalProperties != false
        )
    }

    private fun convertOneOf(schemas: List<JSONSchema>): UDMType {
        val types = schemas.map { convertSchema(it) }.toSet()
        return UDMType.UnionType(types)
    }

    private fun convertAnyOf(schemas: List<JSONSchema>): UDMType {
        // anyOf is similar to oneOf for type purposes
        return convertOneOf(schemas)
    }

    private fun convertAllOf(schemas: List<JSONSchema>): UDMType {
        // Merge all schemas (intersection)
        val types = schemas.map { convertSchema(it) }
        return mergeTypes(types)
    }

    private fun mergeTypes(types: List<UDMType>): UDMType {
        if (types.size == 1) return types.first()

        // Merge object types
        val objectTypes = types.filterIsInstance<UDMType.ObjectType>()
        if (objectTypes.size == types.size) {
            val allProperties = objectTypes.flatMap { it.properties.entries }
                .groupBy { it.key }
                .mapValues { (_, props) ->
                    // Merge property infos
                    PropertyInfo(
                        type = TypeUtils.unify(props[0].value.type, props.getOrNull(1)?.value?.type ?: props[0].value.type),
                        required = props.any { it.value.required }
                    )
                }
            return UDMType.ObjectType(allProperties)
        }

        return UDMType.AnyType
    }
}
```

### Avro Schema to UDMType Conversion

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/AvroSchemaAnalyzer.kt

class AvroSchemaAnalyzer {

    fun buildTypeEnvironment(schema: AvroSchema): TypeEnvironment {
        val env = TypeEnvironment()
        val rootType = convertAvroType(schema)
        env.bind("input", rootType)
        return env
    }

    private fun convertAvroType(avroType: AvroSchema): UDMType {
        return when (avroType.type) {
            "null" -> UDMType.NullType
            "boolean" -> UDMType.BooleanType
            "int", "long" -> UDMType.IntegerType
            "float", "double" -> UDMType.NumberType
            "string", "bytes" -> UDMType.StringType
            "array" -> UDMType.ArrayType(convertAvroType(avroType.items!!))
            "map" -> UDMType.ObjectType(emptyMap(), additionalProperties = true)
            "record" -> convertRecord(avroType)
            "enum" -> UDMType.StringType  // Enum as string
            "union" -> convertUnion(avroType.types!!)
            "fixed" -> UDMType.StringType  // Fixed as string
            else -> UDMType.AnyType
        }
    }

    private fun convertRecord(record: AvroSchema): UDMType {
        val properties = record.fields!!.associate { field ->
            field.name to PropertyInfo(
                type = convertAvroType(field.type),
                required = !isNullable(field.type)
            )
        }
        return UDMType.ObjectType(properties)
    }

    private fun convertUnion(types: List<AvroSchema>): UDMType {
        val udmTypes = types.map { convertAvroType(it) }.toSet()
        return UDMType.UnionType(udmTypes)
    }

    private fun isNullable(type: AvroSchema): Boolean {
        return when {
            type.type == "null" -> true
            type.type == "union" -> type.types!!.any { it.type == "null" }
            else -> false
        }
    }
}
```

---

## Type Inference Engine

### Expression Type Inference

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/inference/TypeInferenceEngine.kt

class TypeInferenceEngine(
    private val typeEnv: TypeEnvironment,
    private val functionRegistry: FunctionRegistry
) {

    fun inferExpressionType(expr: Expression): UDMType {
        return when (expr) {
            is LiteralExpression -> inferLiteral(expr)
            is PathExpression -> inferPath(expr)
            is BinaryOperationExpression -> inferBinaryOp(expr)
            is UnaryOperationExpression -> inferUnaryOp(expr)
            is FunctionCallExpression -> inferFunctionCall(expr)
            is ConditionalExpression -> inferConditional(expr)
            is MapExpression -> inferMap(expr)
            is FilterExpression -> inferFilter(expr)
            is ObjectConstructorExpression -> inferObjectConstructor(expr)
            is ArrayConstructorExpression -> inferArrayConstructor(expr)
            is LambdaExpression -> inferLambda(expr)
            else -> UDMType.AnyType
        }
    }

    private fun inferLiteral(expr: LiteralExpression): UDMType {
        return when (expr.value) {
            is String -> UDMType.StringType
            is Int, is Long -> UDMType.IntegerType
            is Float, is Double -> UDMType.NumberType
            is Boolean -> UDMType.BooleanType
            null -> UDMType.NullType
            else -> UDMType.AnyType
        }
    }

    private fun inferPath(expr: PathExpression): UDMType {
        // Lookup path in type environment
        val type = typeEnv.lookupNested(expr.path)

        if (type == null) {
            throw TypeInferenceException(
                "Unknown path: ${expr.path}",
                expr.location
            )
        }

        return type
    }

    private fun inferBinaryOp(expr: BinaryOperationExpression): UDMType {
        val leftType = inferExpressionType(expr.left)
        val rightType = inferExpressionType(expr.right)

        return when (expr.operator) {
            "+", "-", "*", "/", "%" -> {
                // Numeric operations
                when {
                    leftType is UDMType.StringType && expr.operator == "+" ->
                        UDMType.StringType  // String concatenation
                    leftType.isSubtypeOf(UDMType.NumberType) &&
                    rightType.isSubtypeOf(UDMType.NumberType) -> {
                        // Both numeric
                        if (leftType == UDMType.IntegerType &&
                            rightType == UDMType.IntegerType &&
                            expr.operator != "/") {
                            UDMType.IntegerType
                        } else {
                            UDMType.NumberType
                        }
                    }
                    else -> throw TypeInferenceException(
                        "Operator ${expr.operator} requires numeric operands, " +
                        "got $leftType and $rightType",
                        expr.location
                    )
                }
            }
            "==", "!=", "<", ">", "<=", ">=" -> UDMType.BooleanType
            "&&", "||" -> {
                if (leftType != UDMType.BooleanType || rightType != UDMType.BooleanType) {
                    throw TypeInferenceException(
                        "Logical operators require boolean operands",
                        expr.location
                    )
                }
                UDMType.BooleanType
            }
            else -> UDMType.AnyType
        }
    }

    private fun inferConditional(expr: ConditionalExpression): UDMType {
        val condType = inferExpressionType(expr.condition)

        if (condType != UDMType.BooleanType) {
            throw TypeInferenceException(
                "Condition must be boolean, got $condType",
                expr.location
            )
        }

        val thenType = inferExpressionType(expr.thenBranch)
        val elseType = inferExpressionType(expr.elseBranch)

        // Result is union of both branches
        return TypeUtils.unify(thenType, elseType)
    }

    private fun inferFunctionCall(expr: FunctionCallExpression): UDMType {
        val signature = functionRegistry.lookup(expr.functionName)
            ?: throw TypeInferenceException(
                "Unknown function: ${expr.functionName}",
                expr.location
            )

        // Infer argument types
        val argTypes = expr.arguments.map { inferExpressionType(it) }

        // Check arity
        if (argTypes.size < signature.minArgs ||
            argTypes.size > signature.maxArgs) {
            throw TypeInferenceException(
                "Function ${expr.functionName} expects " +
                "${signature.minArgs}-${signature.maxArgs} arguments, " +
                "got ${argTypes.size}",
                expr.location
            )
        }

        // Type check arguments and build substitution map for generics
        val substitutions = mutableMapOf<String, UDMType>()

        signature.params.zip(argTypes).forEach { (paramType, argType) ->
            when {
                paramType is UDMType.GenericType -> {
                    // Bind generic parameter
                    val existing = substitutions[paramType.name]
                    if (existing != null && existing != argType) {
                        // Generic must be consistent
                        substitutions[paramType.name] = TypeUtils.unify(existing, argType)
                    } else {
                        substitutions[paramType.name] = argType
                    }
                }
                !argType.isSubtypeOf(paramType) -> {
                    throw TypeInferenceException(
                        "Function ${expr.functionName} expects ${paramType} " +
                        "but got ${argType}",
                        expr.location
                    )
                }
            }
        }

        // Apply substitutions to return type
        return TypeUtils.substitute(signature.returnType, substitutions)
    }

    private fun inferMap(expr: MapExpression): UDMType {
        // input |> map(item => expr)
        val collectionType = inferExpressionType(expr.collection)

        if (collectionType !is UDMType.ArrayType) {
            throw TypeInferenceException(
                "map requires an array, got $collectionType",
                expr.location
            )
        }

        // Create lambda environment with item binding
        val lambdaEnv = typeEnv.createChild()
        lambdaEnv.bind(expr.lambda.paramName, collectionType.elementType)

        // Infer lambda body type with new environment
        val lambdaEngine = TypeInferenceEngine(lambdaEnv, functionRegistry)
        val resultType = lambdaEngine.inferExpressionType(expr.lambda.body)

        return UDMType.ArrayType(resultType)
    }

    private fun inferFilter(expr: FilterExpression): UDMType {
        // input |> filter(item => condition)
        val collectionType = inferExpressionType(expr.collection)

        if (collectionType !is UDMType.ArrayType) {
            throw TypeInferenceException(
                "filter requires an array, got $collectionType",
                expr.location
            )
        }

        // Create lambda environment
        val lambdaEnv = typeEnv.createChild()
        lambdaEnv.bind(expr.lambda.paramName, collectionType.elementType)

        // Infer lambda body type
        val lambdaEngine = TypeInferenceEngine(lambdaEnv, functionRegistry)
        val predicateType = lambdaEngine.inferExpressionType(expr.lambda.body)

        if (predicateType != UDMType.BooleanType) {
            throw TypeInferenceException(
                "filter predicate must return boolean, got $predicateType",
                expr.location
            )
        }

        // Filter preserves array type
        return collectionType
    }

    private fun inferObjectConstructor(expr: ObjectConstructorExpression): UDMType {
        val properties = expr.properties.mapValues { (_, valueExpr) ->
            PropertyInfo(
                type = inferExpressionType(valueExpr),
                required = true  // Explicitly constructed properties are required
            )
        }

        return UDMType.ObjectType(properties, additionalProperties = false)
    }

    private fun inferArrayConstructor(expr: ArrayConstructorExpression): UDMType {
        if (expr.elements.isEmpty()) {
            return UDMType.ArrayType(UDMType.AnyType)
        }

        // Infer element types and unify them
        val elementTypes = expr.elements.map { inferExpressionType(it) }
        val unifiedType = elementTypes.reduce { acc, type ->
            TypeUtils.unify(acc, type)
        }

        return UDMType.ArrayType(unifiedType)
    }
}

class TypeInferenceException(
    message: String,
    val location: SourceLocation? = null
) : Exception(message)
```

---

## Function Type Signatures

### Function Signature Registry

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/functions/FunctionRegistry.kt

data class FunctionSignature(
    val name: String,
    val params: List<UDMType>,
    val returnType: UDMType,
    val minArgs: Int = params.size,
    val maxArgs: Int = params.size,
    val varargs: Boolean = false
)

class FunctionRegistry {
    private val signatures = mutableMapOf<String, FunctionSignature>()

    init {
        registerStdlibFunctions()
    }

    fun register(signature: FunctionSignature) {
        signatures[signature.name] = signature
    }

    fun lookup(name: String): FunctionSignature? {
        return signatures[name]
    }

    private fun registerStdlibFunctions() {
        // String functions
        register(FunctionSignature(
            name = "upper",
            params = listOf(UDMType.StringType),
            returnType = UDMType.StringType
        ))

        register(FunctionSignature(
            name = "lower",
            params = listOf(UDMType.StringType),
            returnType = UDMType.StringType
        ))

        register(FunctionSignature(
            name = "substring",
            params = listOf(UDMType.StringType, UDMType.IntegerType, UDMType.IntegerType),
            returnType = UDMType.StringType,
            minArgs = 2,
            maxArgs = 3
        ))

        register(FunctionSignature(
            name = "concat",
            params = listOf(UDMType.StringType),
            returnType = UDMType.StringType,
            minArgs = 1,
            maxArgs = Int.MAX_VALUE,
            varargs = true
        ))

        // Number functions
        register(FunctionSignature(
            name = "parseNumber",
            params = listOf(UDMType.UnionType(UDMType.StringType, UDMType.IntegerType)),
            returnType = UDMType.NumberType
        ))

        register(FunctionSignature(
            name = "round",
            params = listOf(UDMType.NumberType),
            returnType = UDMType.IntegerType
        ))

        register(FunctionSignature(
            name = "floor",
            params = listOf(UDMType.NumberType),
            returnType = UDMType.IntegerType
        ))

        register(FunctionSignature(
            name = "ceil",
            params = listOf(UDMType.NumberType),
            returnType = UDMType.IntegerType
        ))

        // Array functions with generics
        val T = UDMType.GenericType("T")
        val R = UDMType.GenericType("R")

        register(FunctionSignature(
            name = "map",
            params = listOf(
                UDMType.ArrayType(T),
                UDMType.FunctionType(listOf(T), R)
            ),
            returnType = UDMType.ArrayType(R)
        ))

        register(FunctionSignature(
            name = "filter",
            params = listOf(
                UDMType.ArrayType(T),
                UDMType.FunctionType(listOf(T), UDMType.BooleanType)
            ),
            returnType = UDMType.ArrayType(T)
        ))

        register(FunctionSignature(
            name = "reduce",
            params = listOf(
                UDMType.ArrayType(T),
                UDMType.FunctionType(listOf(R, T), R),
                R  // Initial value
            ),
            returnType = R
        ))

        register(FunctionSignature(
            name = "count",
            params = listOf(UDMType.ArrayType(T)),
            returnType = UDMType.IntegerType
        ))

        register(FunctionSignature(
            name = "sum",
            params = listOf(UDMType.ArrayType(UDMType.NumberType)),
            returnType = UDMType.NumberType
        ))

        register(FunctionSignature(
            name = "avg",
            params = listOf(UDMType.ArrayType(UDMType.NumberType)),
            returnType = UDMType.NumberType
        ))

        register(FunctionSignature(
            name = "max",
            params = listOf(UDMType.ArrayType(UDMType.NumberType)),
            returnType = UDMType.NumberType
        ))

        register(FunctionSignature(
            name = "min",
            params = listOf(UDMType.ArrayType(UDMType.NumberType)),
            returnType = UDMType.NumberType
        ))

        register(FunctionSignature(
            name = "first",
            params = listOf(UDMType.ArrayType(T)),
            returnType = TypeUtils.makeNullable(T)
        ))

        register(FunctionSignature(
            name = "last",
            params = listOf(UDMType.ArrayType(T)),
            returnType = TypeUtils.makeNullable(T)
        ))

        register(FunctionSignature(
            name = "take",
            params = listOf(UDMType.ArrayType(T), UDMType.IntegerType),
            returnType = UDMType.ArrayType(T)
        ))

        register(FunctionSignature(
            name = "sortBy",
            params = listOf(
                UDMType.ArrayType(T),
                UDMType.FunctionType(listOf(T), UDMType.AnyType)
            ),
            returnType = UDMType.ArrayType(T)
        ))

        register(FunctionSignature(
            name = "groupBy",
            params = listOf(
                UDMType.ArrayType(T),
                UDMType.FunctionType(listOf(T), UDMType.AnyType)
            ),
            returnType = UDMType.ArrayType(
                UDMType.ObjectType(mapOf(
                    "key" to PropertyInfo(UDMType.AnyType),
                    "value" to PropertyInfo(UDMType.ArrayType(T))
                ))
            )
        ))

        // Object functions
        register(FunctionSignature(
            name = "keys",
            params = listOf(UDMType.ObjectType(emptyMap())),
            returnType = UDMType.ArrayType(UDMType.StringType)
        ))

        register(FunctionSignature(
            name = "values",
            params = listOf(UDMType.ObjectType(emptyMap())),
            returnType = UDMType.ArrayType(UDMType.AnyType)
        ))

        register(FunctionSignature(
            name = "entries",
            params = listOf(UDMType.ObjectType(emptyMap())),
            returnType = UDMType.ArrayType(
                UDMType.ArrayType(UDMType.UnionType(UDMType.StringType, UDMType.AnyType))
            )
        ))

        // Date functions
        register(FunctionSignature(
            name = "now",
            params = emptyList(),
            returnType = UDMType.StringType  // ISO 8601 string
        ))

        register(FunctionSignature(
            name = "formatDate",
            params = listOf(UDMType.StringType, UDMType.StringType),
            returnType = UDMType.StringType
        ))

        register(FunctionSignature(
            name = "parseDate",
            params = listOf(UDMType.StringType, UDMType.StringType),
            returnType = UDMType.StringType
        ))

        // ... register all other stdlib functions
    }
}
```

---

## Output Schema Generation

### UDMType to JSON Schema

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaGenerator.kt

class JSONSchemaGenerator(
    private val schemaVersion: String = "https://json-schema.org/draft/2020-12/schema"
) {

    fun generate(type: UDMType, title: String? = null): JSONSchema {
        val schema = convertType(type)

        return JSONSchema(
            schema = schemaVersion,
            title = title,
            type = schema.type,
            properties = schema.properties,
            items = schema.items,
            required = schema.required,
            additionalProperties = schema.additionalProperties,
            oneOf = schema.oneOf,
            anyOf = schema.anyOf
        )
    }

    private fun convertType(type: UDMType): JSONSchemaNode {
        return when (type) {
            UDMType.StringType -> JSONSchemaNode(type = "string")
            UDMType.NumberType -> JSONSchemaNode(type = "number")
            UDMType.IntegerType -> JSONSchemaNode(type = "integer")
            UDMType.BooleanType -> JSONSchemaNode(type = "boolean")
            UDMType.NullType -> JSONSchemaNode(type = "null")
            UDMType.AnyType -> JSONSchemaNode()  // No type constraint

            is UDMType.ArrayType -> JSONSchemaNode(
                type = "array",
                items = convertType(type.elementType)
            )

            is UDMType.ObjectType -> convertObject(type)

            is UDMType.UnionType -> {
                if (type.isNullable() && type.types.size == 2) {
                    // Nullable type: make base type nullable
                    val baseType = type.nonNullTypes().first()
                    val baseSchema = convertType(baseType)
                    baseSchema.copy(type = listOf(baseSchema.type!!, "null"))
                } else {
                    // True union: use anyOf
                    JSONSchemaNode(
                        anyOf = type.types.map { convertType(it) }
                    )
                }
            }

            else -> JSONSchemaNode()
        }
    }

    private fun convertObject(objectType: UDMType.ObjectType): JSONSchemaNode {
        val properties = objectType.properties.mapValues { (_, propInfo) ->
            convertType(propInfo.type)
        }

        val required = objectType.properties
            .filter { (_, propInfo) -> propInfo.required }
            .keys
            .toList()

        return JSONSchemaNode(
            type = "object",
            properties = properties,
            required = if (required.isNotEmpty()) required else null,
            additionalProperties = objectType.additionalProperties
        )
    }
}

data class JSONSchemaNode(
    val type: Any? = null,  // String or List<String>
    val properties: Map<String, JSONSchemaNode>? = null,
    val items: JSONSchemaNode? = null,
    val required: List<String>? = null,
    val additionalProperties: Any? = null,  // Boolean or JSONSchemaNode
    val anyOf: List<JSONSchemaNode>? = null,
    val oneOf: List<JSONSchemaNode>? = null,
    val allOf: List<JSONSchemaNode>? = null
)
```

### UDMType to XSD

```kotlin
// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/XSDGenerator.kt

class XSDGenerator(
    private val targetNamespace: String? = null
) {

    fun generate(type: UDMType, rootElementName: String): String {
        val sb = StringBuilder()

        // XML declaration
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")

        // Schema element
        sb.append("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"")
        if (targetNamespace != null) {
            sb.append(" targetNamespace=\"$targetNamespace\"")
            sb.append(" xmlns=\"$targetNamespace\"")
        }
        sb.appendLine(">")

        // Root element
        sb.appendLine("  <xs:element name=\"$rootElementName\">")
        sb.append(convertType(type, indent = 4))
        sb.appendLine("  </xs:element>")

        sb.appendLine("</xs:schema>")

        return sb.toString()
    }

    private fun convertType(type: UDMType, indent: Int = 0): String {
        val ind = " ".repeat(indent)

        return when (type) {
            UDMType.StringType -> """$ind<xs:simpleType><xs:restriction base="xs:string"/></xs:simpleType>"""
            UDMType.IntegerType -> """$ind<xs:simpleType><xs:restriction base="xs:integer"/></xs:simpleType>"""
            UDMType.NumberType -> """$ind<xs:simpleType><xs:restriction base="xs:decimal"/></xs:simpleType>"""
            UDMType.BooleanType -> """$ind<xs:simpleType><xs:restriction base="xs:boolean"/></xs:simpleType>"""

            is UDMType.ArrayType -> {
                // Array as sequence with maxOccurs="unbounded"
                buildString {
                    appendLine("$ind<xs:complexType>")
                    appendLine("$ind  <xs:sequence>")
                    appendLine("$ind    <xs:element name=\"item\" maxOccurs=\"unbounded\">")
                    append(convertType(type.elementType, indent + 6))
                    appendLine()
                    appendLine("$ind    </xs:element>")
                    appendLine("$ind  </xs:sequence>")
                    append("$ind</xs:complexType>")
                }
            }

            is UDMType.ObjectType -> convertObject(type, indent)

            is UDMType.UnionType -> {
                // XSD doesn't have union types directly
                // Use xs:choice or xs:anyType
                """$ind<xs:simpleType><xs:restriction base="xs:anyType"/></xs:simpleType>"""
            }

            else -> """$ind<xs:simpleType><xs:restriction base="xs:anyType"/></xs:simpleType>"""
        }
    }

    private fun convertObject(objectType: UDMType.ObjectType, indent: Int): String {
        val ind = " ".repeat(indent)

        return buildString {
            appendLine("$ind<xs:complexType>")

            // Attributes (properties starting with @)
            val attributes = objectType.properties.filter { it.key.startsWith("@") }
            val elements = objectType.properties.filter { !it.key.startsWith("@") }

            if (elements.isNotEmpty()) {
                appendLine("$ind  <xs:sequence>")

                elements.forEach { (name, propInfo) ->
                    val minOccurs = if (propInfo.required) 1 else 0
                    val maxOccurs = if (propInfo.isArray) "unbounded" else "1"

                    appendLine("$ind    <xs:element name=\"$name\" minOccurs=\"$minOccurs\" maxOccurs=\"$maxOccurs\">")
                    append(convertType(propInfo.type, indent + 6))
                    appendLine()
                    appendLine("$ind    </xs:element>")
                }

                appendLine("$ind  </xs:sequence>")
            }

            attributes.forEach { (name, propInfo) ->
                val attrName = name.substring(1)  // Remove @
                val use = if (propInfo.required) "required" else "optional"
                val xsdType = when (propInfo.type) {
                    UDMType.StringType -> "xs:string"
                    UDMType.IntegerType -> "xs:integer"
                    UDMType.NumberType -> "xs:decimal"
                    UDMType.BooleanType -> "xs:boolean"
                    else -> "xs:string"
                }
                appendLine("""$ind  <xs:attribute name="$attrName" type="$xsdType" use="$use"/>""")
            }

            append("$ind</xs:complexType>")
        }
    }
}
```

---

## Architecture and Data Flow

### Complete System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        DESIGN-TIME ANALYSIS                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────┐                                                  │
│  │ Input Schema   │                                                  │
│  │ (XSD/JSCH/Avro)│                                                  │
│  └────────┬───────┘                                                  │
│           │                                                          │
│           ▼                                                          │
│  ┌────────────────┐         ┌──────────────────┐                     │
│  │ Schema Parser  │────────▶│ Type Environment │                     │
│  │ (XSDAnalyzer)  │         │ (UDMType tree)   │                     │
│  └────────────────┘         └────────┬─────────┘                     │
│                                      │                               │
│  ┌────────────────┐                  │                               │
│  │ UTLX Script    │                  │                               │
│  └────────┬───────┘                  │                               │
│           │                          │                               │
│           ▼                          │                               │
│  ┌────────────────┐                  │                               │
│  │ UTLX Parser    │                  │                               │
│  │ (AST)          │                  │                               │
│  └────────┬───────┘                  │                               │
│           │                          │                               │
│           ▼                          ▼                               │
│  ┌──────────────────────────────────────┐                            │
│  │   Type Inference Engine              │                            │
│  │   ┌──────────────────────────────┐   │                            │
│  │   │ Expression Type Inference    │   │                            │
│  │   ├──────────────────────────────┤   │                            │
│  │   │ Function Type Checking       │   │                            │
│  │   ├──────────────────────────────┤   │                            │
│  │   │ Generic Type Substitution    │   │                            │
│  │   └──────────────────────────────┘   │                            │
│  └──────────────────┬───────────────────┘                            │
│                     │                                                │
│                     ▼                                                │
│  ┌────────────────────────────┐                                      │
│  │ Output Type (UDMType)      │                                      │
│  └────────────────┬───────────┘                                      │
│                   │                                                  │
│                   ▼                                                  │
│  ┌────────────────────────────┐                                      │
│  │ Schema Generator           │                                      │
│  │ - JSONSchemaGenerator      │                                      │
│  │ - XSDGenerator             │                                      │
│  │ - AvroSchemaGenerator      │                                      │
│  └────────────────┬───────────┘                                      │
│                   │                                                  │
│                   ▼                                                  │
│  ┌────────────────────────────┐                                      │
│  │ Output Schema              │                                      │
│  │ (JSON Schema/XSD/Avro)     │                                      │
│  └────────────────────────────┘                                      │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                          RUNTIME EXECUTION                            │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌────────────────┐                                                  │
│  │ Input Data     │                                                  │
│  │ (XML/JSON/CSV) │                                                  │
│  └────────┬───────┘                                                  │
│           │                                                           │
│           ▼                                                           │
│  ┌────────────────┐         ┌──────────────────┐                    │
│  │ Format Parser  │────────▶│ UDM (data)       │                    │
│  │                │         │                  │                    │
│  └────────────────┘         └────────┬─────────┘                    │
│                                       │                               │
│  ┌────────────────┐                  │                               │
│  │ UTLX Script    │                  │                               │
│  └────────┬───────┘                  │                               │
│           │                           │                               │
│           ▼                           │                               │
│  ┌────────────────┐                  │                               │
│  │ UTLX Runtime   │                  │                               │
│  │ Executor       │◀─────────────────┘                               │
│  └────────┬───────┘                                                  │
│           │                                                           │
│           ▼                                                           │
│  ┌────────────────┐                                                  │
│  │ Output UDM     │                                                  │
│  └────────┬───────┘                                                  │
│           │                                                           │
│           ▼                                                           │
│  ┌────────────────┐                                                  │
│  │ Format         │                                                  │
│  │ Serializer     │                                                  │
│  └────────┬───────┘                                                  │
│           │                                                           │
│           ▼                                                           │
│  ┌────────────────┐                                                  │
│  │ Output Data    │                                                  │
│  │ (XML/JSON/CSV) │                                                  │
│  └────────────────┘                                                  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Flow Example

**Input Schema (order.xsd)**:
```
Order (Object)
  ├─ @id (String, required)
  └─ Items (Object, required)
      └─ Item (Array)
          ├─ @sku (String)
          ├─ @quantity (Integer)
          └─ @price (Decimal)
```

**Type Environment**:
```
input.Order → ObjectType
input.Order.@id → StringType
input.Order.Items → ObjectType
input.Order.Items.Item → ArrayType(ObjectType)
input.Order.Items.Item[].@sku → StringType
input.Order.Items.Item[].@quantity → IntegerType
input.Order.Items.Item[].@price → NumberType
```

**UTLX Expression**:
```utlx
sum(input.Order.Items.Item |> map(item =>
  parseNumber(item.@quantity) * parseNumber(item.@price)
))
```

**Type Inference Steps**:
```
1. input.Order.Items.Item
   → ArrayType(ObjectType)

2. map(item => parseNumber(item.@quantity) * parseNumber(item.@price))
   - Lambda env: item → ObjectType
   - item.@quantity → IntegerType
   - parseNumber(IntegerType) → NumberType
   - item.@price → NumberType
   - parseNumber(NumberType) → NumberType
   - NumberType * NumberType → NumberType
   - map result → ArrayType(NumberType)

3. sum(ArrayType(NumberType))
   - sum signature: Array<Number> → Number
   - Result → NumberType
```

**Output Type**: `NumberType`

**Generated JSON Schema**:
```json
{"type": "number"}
```

---

## CLI Integration

### Command Structure

```bash
# Design-time schema analysis
utlx schema analyze \
  --input-schema <path-to-xsd-or-json-schema> \
  --transform <path-to-utlx-script> \
  --output-format <json-schema|xsd|avro> \
  --output <path-to-output-schema>

# With contract validation
utlx schema analyze \
  --input-schema order.xsd \
  --transform order-to-invoice.utlx \
  --output-format json-schema \
  --output invoice.schema.json \
  --expected expected-invoice.schema.json  # Validate against expected

# Type check only (no schema generation)
utlx schema check \
  --input-schema order.xsd \
  --transform order-to-invoice.utlx

# Generate type environment from schema (for debugging)
utlx schema types \
  --input-schema order.xsd
```

### Implementation

```kotlin
// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/SchemaCommand.kt

object SchemaCommand {

    fun execute(args: Array<String>) {
        val subcommand = args.getOrNull(0) ?: "help"

        when (subcommand) {
            "analyze" -> analyzeSchema(args.drop(1))
            "check" -> typeCheck(args.drop(1))
            "types" -> printTypes(args.drop(1))
            "help", "--help", "-h" -> printHelp()
            else -> {
                System.err.println("Unknown schema subcommand: $subcommand")
                printHelp()
            }
        }
    }

    private fun analyzeSchema(args: List<String>) {
        // Parse arguments
        val inputSchemaPath = getArgValue(args, "--input-schema")
            ?: error("--input-schema required")
        val transformPath = getArgValue(args, "--transform")
            ?: error("--transform required")
        val outputFormat = getArgValue(args, "--output-format")
            ?: "json-schema"
        val outputPath = getArgValue(args, "--output")
        val expectedPath = getArgValue(args, "--expected")

        try {
            // 1. Parse input schema
            println("Parsing input schema: $inputSchemaPath")
            val inputSchema = parseSchema(inputSchemaPath)

            // 2. Build type environment
            println("Building type environment...")
            val typeEnv = buildTypeEnvironment(inputSchema)

            // 3. Parse UTLX script
            println("Parsing transformation: $transformPath")
            val utlxScript = File(transformPath).readText()
            val ast = UTLXParser.parse(utlxScript)

            // 4. Run type inference
            println("Running type inference...")
            val inferenceEngine = TypeInferenceEngine(typeEnv, FunctionRegistry())
            val outputType = inferenceEngine.inferExpressionType(ast.mainExpression)

            println("✓ Type inference successful")
            println("Output type: $outputType")

            // 5. Generate output schema
            println("Generating output schema...")
            val outputSchema = generateOutputSchema(outputType, outputFormat)

            // 6. Write to file or stdout
            if (outputPath != null) {
                File(outputPath).writeText(outputSchema)
                println("✓ Output schema written to: $outputPath")
            } else {
                println("\nOutput Schema:")
                println(outputSchema)
            }

            // 7. Validate against expected if provided
            if (expectedPath != null) {
                println("\nValidating against expected schema...")
                val expected = File(expectedPath).readText()
                val matches = validateSchemas(outputSchema, expected, outputFormat)

                if (matches) {
                    println("✓ Output schema matches expected schema")
                } else {
                    println("✗ Output schema does NOT match expected schema")
                    exitProcess(1)
                }
            }

        } catch (e: TypeInferenceException) {
            System.err.println("Type Error: ${e.message}")
            if (e.location != null) {
                System.err.println("  at ${e.location}")
            }
            exitProcess(1)
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            if (System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }

    private fun typeCheck(args: List<String>) {
        val inputSchemaPath = getArgValue(args, "--input-schema")
            ?: error("--input-schema required")
        val transformPath = getArgValue(args, "--transform")
            ?: error("--transform required")

        try {
            val inputSchema = parseSchema(inputSchemaPath)
            val typeEnv = buildTypeEnvironment(inputSchema)

            val utlxScript = File(transformPath).readText()
            val ast = UTLXParser.parse(utlxScript)

            val inferenceEngine = TypeInferenceEngine(typeEnv, FunctionRegistry())
            val outputType = inferenceEngine.inferExpressionType(ast.mainExpression)

            println("✓ Type check successful")
            println("Output type: $outputType")

        } catch (e: TypeInferenceException) {
            System.err.println("✗ Type Error: ${e.message}")
            if (e.location != null) {
                System.err.println("  at ${e.location}")
            }
            exitProcess(1)
        }
    }

    private fun printTypes(args: List<String>) {
        val inputSchemaPath = getArgValue(args, "--input-schema")
            ?: error("--input-schema required")

        val inputSchema = parseSchema(inputSchemaPath)
        val typeEnv = buildTypeEnvironment(inputSchema)

        println("Type Environment:")
        println("================")
        typeEnv.getAllBindings().forEach { (path, type) ->
            println("$path → $type")
        }
    }

    private fun parseSchema(path: String): Schema {
        val content = File(path).readText()
        val extension = File(path).extension.lowercase()

        return when (extension) {
            "xsd" -> XSDParser.parse(content)
            "json" -> {
                // Check if it's JSON Schema
                if (content.contains("\"\$schema\"")) {
                    JSONSchemaParser.parse(content)
                } else {
                    error("File is JSON but not JSON Schema")
                }
            }
            "avsc" -> AvroSchemaParser.parse(content)
            else -> error("Unsupported schema format: $extension")
        }
    }

    private fun buildTypeEnvironment(schema: Schema): TypeEnvironment {
        return when (schema) {
            is XSDSchema -> XSDAnalyzer().buildTypeEnvironment(schema)
            is JSONSchema -> JSONSchemaAnalyzer().buildTypeEnvironment(schema)
            is AvroSchema -> AvroSchemaAnalyzer().buildTypeEnvironment(schema)
            else -> error("Unsupported schema type")
        }
    }

    private fun generateOutputSchema(
        type: UDMType,
        format: String
    ): String {
        return when (format.lowercase()) {
            "json-schema" -> {
                val generator = JSONSchemaGenerator()
                val schema = generator.generate(type, title = "Generated Output Schema")
                // Convert to JSON string
                jacksonObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(schema)
            }
            "xsd" -> {
                val generator = XSDGenerator()
                generator.generate(type, rootElementName = "output")
            }
            "avro" -> {
                val generator = AvroSchemaGenerator()
                generator.generate(type, recordName = "Output")
            }
            else -> error("Unsupported output format: $format")
        }
    }

    private fun validateSchemas(
        actual: String,
        expected: String,
        format: String
    ): Boolean {
        // Parse both schemas and compare structurally
        return when (format.lowercase()) {
            "json-schema" -> {
                val actualSchema = jacksonObjectMapper().readTree(actual)
                val expectedSchema = jacksonObjectMapper().readTree(expected)
                actualSchema == expectedSchema
            }
            else -> {
                // Simple string comparison for other formats
                actual.trim() == expected.trim()
            }
        }
    }

    private fun getArgValue(args: List<String>, flag: String): String? {
        val index = args.indexOf(flag)
        return if (index >= 0 && index + 1 < args.size) {
            args[index + 1]
        } else {
            null
        }
    }

    private fun printHelp() {
        println("""
            UTL-X Schema Analysis Commands

            utlx schema analyze --input-schema <schema> --transform <utlx> [options]
              Analyze transformation and generate output schema

              Options:
                --input-schema <path>    Input schema (XSD, JSON Schema, Avro)
                --transform <path>       UTLX transformation script
                --output-format <fmt>    Output format: json-schema, xsd, avro (default: json-schema)
                --output <path>          Write output schema to file
                --expected <path>        Validate against expected schema

            utlx schema check --input-schema <schema> --transform <utlx>
              Type check transformation without generating output schema

            utlx schema types --input-schema <schema>
              Print type environment from schema (for debugging)

            Examples:
              utlx schema analyze --input-schema order.xsd --transform order-to-invoice.utlx \\
                --output-format json-schema --output invoice.schema.json

              utlx schema check --input-schema customer.schema.json --transform transform.utlx

              utlx schema types --input-schema product.avsc
        """.trimIndent())
    }
}
```

---

## Use Cases and Examples

### Use Case 1: ec-api-design Integration

**Workflow**:
```
Developer using ec-api-design tool:

1. ec-api-design generates API contract:
   - request.xsd (API request schema)
   - response.schema.json (expected response schema)

2. Developer writes transformation:
   - request-handler.utlx

3. Design-time validation:
   $ utlx schema analyze \\
       --input-schema request.xsd \\
       --transform request-handler.utlx \\
       --output-format json-schema \\
       --expected response.schema.json

   Output:
   ✓ Type inference successful
   ✓ Output schema matches expected schema

4. Deploy with confidence:
   - API Gateway uses response.schema.json for validation
   - Runtime uses request-handler.utlx for transformation
   - Guaranteed contract compliance
```

### Use Case 2: API Contract Validation

**Input Schema (api-request.xsd)**:
```xml
<xs:schema>
  <xs:element name="CreateOrderRequest">
    <xs:complexType>
      <xs:sequence>
        <xs:element name="customerId" type="xs:string"/>
        <xs:element name="items">
          <xs:complexType>
            <xs:sequence>
              <xs:element name="item" maxOccurs="unbounded">
                <xs:complexType>
                  <xs:element name="productId" type="xs:string"/>
                  <xs:element name="quantity" type="xs:int"/>
                </xs:complexType>
              </xs:element>
            </xs:sequence>
          </xs:complexType>
        </xs:element>
      </xs:sequence>
    </xs:complexType>
  </xs:element>
</xs:schema>
```

**Transformation (request-handler.utlx)**:
```utlx
%utlx 1.0
input xml
output json
---
{
  order: {
    customer: input.CreateOrderRequest.customerId,
    items: input.CreateOrderRequest.items.item |> map(i => {
      product: i.productId,
      qty: i.quantity,
      subtotal: lookupPrice(i.productId) * i.quantity
    }),
    total: sum(
      input.CreateOrderRequest.items.item |> map(i =>
        lookupPrice(i.productId) * i.quantity
      )
    )
  }
}
```

**Analysis**:
```bash
$ utlx schema analyze \\
    --input-schema api-request.xsd \\
    --transform request-handler.utlx \\
    --output-format json-schema
```

**Generated Output Schema**:
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "properties": {
    "order": {
      "type": "object",
      "properties": {
        "customer": {"type": "string"},
        "items": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "product": {"type": "string"},
              "qty": {"type": "integer"},
              "subtotal": {"type": "number"}
            },
            "required": ["product", "qty", "subtotal"]
          }
        },
        "total": {"type": "number"}
      },
      "required": ["customer", "items", "total"]
    }
  },
  "required": ["order"]
}
```

### Use Case 3: Early Error Detection

**Buggy Transformation**:
```utlx
{
  total: input.Order.Items.Item |> sum(item => item.@price)
          ^^^^                    ^^^
}
```

**Design-Time Error**:
```
Type Error at line 2, column 11:
  Function 'sum' expects Array<Number> but got Array<Object>

Hint: Did you mean to map the items first?
  sum(input.Order.Items.Item |> map(item => item.@price))

Type Error at line 2, column 35:
  Function 'sum' expects Array<Number> but got Array<String>

Hint: item.@price is type String according to order.xsd
  Use parseNumber(item.@price) to convert to Number
```

**Fixed Transformation**:
```utlx
{
  total: sum(
    input.Order.Items.Item |> map(item => parseNumber(item.@price))
  )
}
```

**Success**:
```
✓ Type check successful
Output type: {total: Number}
```

### Use Case 4: CI/CD Pipeline Integration

**`.github/workflows/validate-transformations.yml`**:
```yaml
name: Validate Transformations

on: [push, pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Setup UTL-X
        run: |
          curl -fsSL https://utl-x.dev/install.sh | bash

      - name: Validate API Transformations
        run: |
          for transform in api/transformations/*.utlx; do
            schema_name=$(basename "$transform" .utlx)
            echo "Validating $schema_name..."

            utlx schema analyze \\
              --input-schema "api/schemas/${schema_name}-input.xsd" \\
              --transform "$transform" \\
              --output-format json-schema \\
              --expected "api/schemas/${schema_name}-output.schema.json" \\
              || exit 1
          done

          echo "✓ All transformations validated successfully"
```

---

## Implementation Phases

### Phase 0: Type System Foundation (4-6 weeks)

**Week 1-2: UDMType System**
- [ ] Define `UDMType` sealed class hierarchy
- [ ] Implement `PropertyInfo` with cardinality
- [ ] Implement type operations (isSubtypeOf, merge, unify)
- [ ] Implement `TypeUtils` helper functions
- [ ] Write comprehensive unit tests

**Week 3-4: Type Environment**
- [ ] Implement `TypeEnvironment` class
- [ ] Path lookup with nesting support
- [ ] Child environment creation for scopes
- [ ] Environment merging and composition
- [ ] Unit tests for environment operations

**Week 5-6: Foundation Integration**
- [ ] Create analysis module structure
- [ ] Set up module dependencies
- [ ] Integration tests
- [ ] Documentation

**Deliverables**:
- Working UDMType system
- Type environment infrastructure
- 90%+ test coverage
- API documentation

### Phase 1: Schema to Type Conversion (3-4 weeks)

**Week 1: XSD Analyzer**
- [ ] Implement `XSDAnalyzer`
- [ ] XSD simple type → UDMType mapping
- [ ] XSD complex type → UDMType mapping
- [ ] Handle attributes, sequences, choices
- [ ] Test with real-world XSD files

**Week 2: JSON Schema Analyzer**
- [ ] Implement `JSONSchemaAnalyzer`
- [ ] JSON Schema → UDMType mapping
- [ ] Handle $ref, oneOf, anyOf, allOf
- [ ] Draft version support
- [ ] Test with JSON Schema Test Suite

**Week 3: Avro Schema Analyzer**
- [ ] Implement `AvroSchemaAnalyzer`
- [ ] Avro types → UDMType mapping
- [ ] Handle records, unions, enums
- [ ] Test with Avro schema examples

**Week 4: Integration & Testing**
- [ ] End-to-end tests with all schema types
- [ ] Edge case handling
- [ ] Error reporting improvements
- [ ] Performance optimization

**Deliverables**:
- Schema parsers for XSD, JSON Schema, Avro
- Type environment generation
- Comprehensive test suite

### Phase 2: Type Inference Engine (4-5 weeks)

**Week 1-2: Expression Type Inference**
- [ ] Implement `TypeInferenceEngine`
- [ ] Literal, path, binary op inference
- [ ] Conditional expression inference
- [ ] Object/array constructor inference
- [ ] Unit tests for each expression type

**Week 3: Function Type Checking**
- [ ] Implement `FunctionRegistry`
- [ ] Define all stdlib function signatures
- [ ] Generic type parameter substitution
- [ ] Varargs handling
- [ ] Function call type inference

**Week 4: Advanced Features**
- [ ] Lambda expression type inference
- [ ] Map/filter/reduce with generics
- [ ] Pipe operator type propagation
- [ ] Pattern matching type inference

**Week 5: Error Reporting**
- [ ] Detailed type error messages
- [ ] Suggestion engine ("Did you mean...")
- [ ] Source location tracking
- [ ] Error recovery strategies

**Deliverables**:
- Complete type inference engine
- Function type registry
- Excellent error messages
- 95%+ inference accuracy

### Phase 3: Output Schema Generation (2-3 weeks)

**Week 1: JSON Schema Generator**
- [ ] Implement `JSONSchemaGenerator`
- [ ] UDMType → JSON Schema conversion
- [ ] Handle union types, nullable fields
- [ ] Draft 2020-12 support
- [ ] Test against JSON Schema validators

**Week 2: XSD Generator**
- [ ] Implement `XSDGenerator`
- [ ] UDMType → XSD conversion
- [ ] Handle complex types, sequences
- [ ] Namespace support
- [ ] Test against XSD validators

**Week 3: Avro Generator & Integration**
- [ ] Implement `AvroSchemaGenerator`
- [ ] UDMType → Avro schema conversion
- [ ] End-to-end pipeline tests
- [ ] Performance optimization
- [ ] Documentation

**Deliverables**:
- Schema generators for all formats
- Bidirectional schema conversion
- Validation against schema validators

### Phase 4: CLI & Tooling (2-3 weeks)

**Week 1: CLI Commands**
- [ ] Implement `schema analyze` command
- [ ] Implement `schema check` command
- [ ] Implement `schema types` command
- [ ] Argument parsing and validation
- [ ] Help text and documentation

**Week 2: Integration & Polish**
- [ ] Integration with existing CLI
- [ ] Error formatting improvements
- [ ] Progress indicators for large schemas
- [ ] Verbose/debug modes
- [ ] Examples and tutorials

**Week 3: Documentation & Release**
- [ ] User documentation
- [ ] Tutorial examples
- [ ] API documentation
- [ ] Migration guide
- [ ] Release notes

**Deliverables**:
- Complete CLI integration
- User-facing documentation
- Tutorial examples
- Beta release

### Total Timeline: ~15-18 weeks (3.5-4 months)

---

## Challenges and Solutions

### Challenge 1: Dynamic Expressions

**Problem**: Output schema varies based on runtime data

```utlx
{
  result: if (input.type == "premium")
    then { level: "gold", bonus: 100 }
    else { level: "silver" }
}
```

**Solution**: Use union types

```
Output Type:
{
  result: {
    level: String,
    bonus?: Integer  // Optional in union
  }
}
```

**JSON Schema**:
```json
{
  "type": "object",
  "properties": {
    "result": {
      "oneOf": [
        {
          "type": "object",
          "properties": {
            "level": {"const": "gold"},
            "bonus": {"type": "integer"}
          },
          "required": ["level", "bonus"]
        },
        {
          "type": "object",
          "properties": {
            "level": {"const": "silver"}
          },
          "required": ["level"]
        }
      ]
    }
  }
}
```

### Challenge 2: External Function Calls

**Problem**: Type of external function results unknown

```utlx
{
  enriched: fetchCustomerData(input.customerId)
}
```

**Solution**: Require type annotations

```utlx
%utlx 1.0
input json
output json

declare function fetchCustomerData(id: String): Object {
  name: String,
  email: String,
  address: Object
}
---
{
  enriched: fetchCustomerData(input.customerId)
}
```

Or use `@type` annotations:
```utlx
{
  enriched: fetchCustomerData(input.customerId) @type(CustomerData)
}
```

### Challenge 3: Incomplete Type Information

**Problem**: No input schema provided

**Solution**: Gradual typing

```
1. If input schema provided → Full static analysis
2. If no schema → Infer from UTLX script
   - Assume `input: Any`
   - Mark assumptions in output schema
   - Warn user about dynamic types

3. Hybrid: Partial schema + inference
   - Use schema where available
   - Infer for unknown paths
```

**Output with warnings**:
```
⚠ Warning: Input schema not provided
  Inferred input type: {Order: {Items: Array<Any>}}
  For better type checking, provide input schema with --input-schema

✓ Output type: {total: Number}
```

### Challenge 4: Recursive Types

**Problem**: Self-referential types

```utlx
// Tree structure
{
  node: {
    value: input.value,
    children: input.children |> map(child =>
      transformNode(child)  // Recursive!
    )
  }
}
```

**Solution**: Recursive type definitions

```kotlin
// Allow recursive UDMTypes
data class RecursiveType(
    val name: String,
    val definition: () -> UDMType
) : UDMType()

// In JSON Schema:
{
  "$defs": {
    "Node": {
      "type": "object",
      "properties": {
        "value": {"type": "string"},
        "children": {
          "type": "array",
          "items": {"$ref": "#/$defs/Node"}
        }
      }
    }
  }
}
```

### Challenge 5: Performance with Large Schemas

**Problem**: XSD with 1000s of elements slow to analyze

**Solution**:
1. **Lazy type loading**: Only load types when accessed
2. **Caching**: Cache type lookups
3. **Parallel analysis**: Analyze independent branches in parallel
4. **Incremental typing**: Only re-analyze changed parts

```kotlin
class LazyTypeEnvironment {
    private val cache = ConcurrentHashMap<String, UDMType>()
    private val schema: XSDSchema

    fun lookup(path: String): UDMType? {
        return cache.getOrPut(path) {
            computeType(path)  // Only compute when needed
        }
    }
}
```

---

## Integration Points

### Integration with ec-api-design

```
ec-api-design Workflow:

1. Design API in ec-api-design tool
   ↓
2. Generate schemas:
   - request.xsd
   - response.schema.json
   ↓
3. Developer creates transformation:
   - request-handler.utlx
   ↓
4. UTL-X validates transformation:
   $ utlx schema analyze \\
       --input-schema request.xsd \\
       --transform request-handler.utlx \\
       --expected response.schema.json
   ↓
5. Deploy to API Gateway:
   - Gateway validates requests against request.xsd
   - UTL-X runtime executes request-handler.utlx
   - Gateway validates responses against response.schema.json
```

### Integration with CI/CD

**GitHub Actions Example**:
```yaml
- name: Validate Transformations
  run: |
    utlx schema analyze \\
      --input-schema api/request.xsd \\
      --transform api/handler.utlx \\
      --expected api/response.schema.json
```

**Result**:
- ✅ PR can only merge if schemas match
- ✅ Breaking changes detected automatically
- ✅ API contract compliance guaranteed

### Integration with IDE (LSP)

**Language Server Protocol Support**:

```typescript
// Language Server provides:

1. Autocomplete:
   input.Order.|
           ↑
   Suggests: @id, Customer, Items

2. Type Hints (hover):
   Hovering over: input.Order.Items.Item
   Shows: Array<{@sku: String, @quantity: Integer, @price: Number}>

3. Error Highlighting:
   sum(input.Order.Items.Item)
   ~~~~~~~~~~~~~~~~~~~~~~~~~~~
   Type Error: sum expects Array<Number>, got Array<Object>

4. Quick Fixes:
   Click on error → "Did you mean: sum(input.Order.Items.Item |> map(i => parseNumber(i.@price)))"

5. Go to Definition:
   Ctrl+Click on input.Order.Customer
   → Jumps to Customer element in order.xsd
```

### Integration with API Gateways

**Kong/Nginx/AWS API Gateway**:

```
1. Design-time: Generate response schema
   $ utlx schema analyze ... → response.schema.json

2. Upload to API Gateway:
   $ aws apigateway create-model \\
       --rest-api-id abc123 \\
       --content-type application/json \\
       --schema file://response.schema.json

3. Configure response validation:
   - Gateway validates all responses against schema
   - Invalid responses rejected before reaching client
   - Guarantees contract compliance
```

---

## Technical Specifications

### Type Inference Rules (Formal)

**Binary Operations**:
```
Γ ⊢ e₁ : Int    Γ ⊢ e₂ : Int    op ∈ {+, -, *, %}
─────────────────────────────────────────────────
Γ ⊢ e₁ op e₂ : Int


Γ ⊢ e₁ : Number    Γ ⊢ e₂ : Number    op ∈ {+, -, *, /}
───────────────────────────────────────────────────────
Γ ⊢ e₁ op e₂ : Number


Γ ⊢ e₁ : String    Γ ⊢ e₂ : String
───────────────────────────────────
Γ ⊢ e₁ + e₂ : String
```

**Function Application**:
```
Γ(f) = (T₁, ..., Tₙ) → R    Γ ⊢ e₁ : S₁    ...    Γ ⊢ eₙ : Sₙ
S₁ <: T₁    ...    Sₙ <: Tₙ
──────────────────────────────────────────────────────────────
Γ ⊢ f(e₁, ..., eₙ) : R[substitutions]
```

**Map Operation**:
```
Γ ⊢ coll : Array<T>    Γ, x : T ⊢ body : R
──────────────────────────────────────────
Γ ⊢ coll |> map(x => body) : Array<R>
```

**Object Constructor**:
```
Γ ⊢ e₁ : T₁    ...    Γ ⊢ eₙ : Tₙ
─────────────────────────────────────────────────
Γ ⊢ {k₁: e₁, ..., kₙ: eₙ} : {k₁: T₁, ..., kₙ: Tₙ}
```

### Error Message Format

```
Type Error at <file>:<line>:<column>:
  <Primary error message>

Context:
  <Code snippet with highlighting>

Expected: <Expected type>
Got: <Actual type>

Hint: <Helpful suggestion>
Suggestion: <Code fix>

Related Information:
  - <Additional context>
```

**Example**:
```
Type Error at transform.utlx:15:23:
  Function 'sum' expects Array<Number> but got Array<String>

Context:
  14 |   total: sum(
  15 |     input.Order.Items.Item |> map(item => item.@price)
     |                                           ^^^^^^^^^^^
  16 |   )

Expected: Array<Number>
Got: Array<String>

Hint: According to order.xsd, 'item.@price' has type String
Suggestion: Use parseNumber() to convert:
  sum(input.Order.Items.Item |> map(item => parseNumber(item.@price)))

Related Information:
  - @price is defined as xs:decimal in order.xsd:25
  - XSD types are treated as strings in UDM until explicitly converted
```

---

## Conclusion

This design-time schema analysis capability represents a **fundamental enhancement** to UTL-X that enables:

1. **Design-Time Validation**: Catch errors before deployment
2. **Contract Compliance**: Guarantee API contracts are met
3. **Tool Integration**: Seamless integration with ec-api-design and other tools
4. **Developer Experience**: IDE support, autocomplete, type hints
5. **CI/CD Integration**: Automated validation in build pipelines

The implementation requires significant effort (~4 months) but provides **exceptional value** for production UTL-X deployments.

**Next Steps**:
1. Review and approve this design
2. Create detailed implementation tickets
3. Begin Phase 0 implementation
4. Set up continuous feedback loop with early adopters

---

**Document Version**: 1.0
**Last Updated**: 2025-10-30
**Review Date**: 2025-11-15
**Status**: Awaiting Approval
