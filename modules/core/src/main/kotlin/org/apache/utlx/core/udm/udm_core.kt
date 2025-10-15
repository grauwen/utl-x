package org.apache.utlx.core.udm

import java.time.Instant

/**
 * Universal Data Model (UDM) - Format-agnostic internal representation
 * 
 * This is the core abstraction that allows UTL-X to work with XML, JSON, CSV,
 * and other formats uniformly. All input formats are parsed into UDM, 
 * transformations operate on UDM, and UDM is serialized to output formats.
 */
sealed class UDM {
    /**
     * Scalar values: strings, numbers, booleans, null
     */
    data class Scalar(val value: Any?) : UDM() {
        companion object {
            fun string(s: String) = Scalar(s)
            fun number(n: Number) = Scalar(n)
            fun boolean(b: Boolean) = Scalar(b)
            fun nullValue() = Scalar(null)
        }
        
        fun asString(): String? = value?.toString()
        fun asNumber(): Number? = when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }
        fun asBoolean(): Boolean? = when (value) {
            is Boolean -> value
            is String -> value.lowercase() in setOf("true", "1", "yes")
            is Number -> value.toDouble() != 0.0
            else -> null
        }
        fun isNull(): Boolean = value == null
    }
    
    /**
     * Array of elements (JSON arrays, XML repeated elements, CSV rows)
     */
    data class Array(val elements: List<UDM>) : UDM() {
        fun size(): Int = elements.size
        fun isEmpty(): Boolean = elements.isEmpty()
        fun get(index: Int): UDM? = elements.getOrNull(index)
        fun first(): UDM? = elements.firstOrNull()
        fun last(): UDM? = elements.lastOrNull()
        
        companion object {
            fun empty() = Array(emptyList())
            fun of(vararg elements: UDM) = Array(elements.toList())
        }
    }
    
    /**
     * Object with properties (JSON objects, XML elements with children)
     * 
     * Attributes are separate from properties to preserve XML semantics
     * but can be accessed uniformly in transformations
     */
    data class Object(
        val properties: Map<String, UDM>,
        val attributes: Map<String, String> = emptyMap(),
        val name: String? = null  // Element/object name (for XML)
    ) : UDM() {
        fun get(key: String): UDM? = properties[key]
        fun getAttribute(key: String): String? = attributes[key]
        fun hasProperty(key: String): Boolean = properties.containsKey(key)
        fun hasAttribute(key: String): Boolean = attributes.containsKey(key)
        fun keys(): Set<String> = properties.keys
        fun attributeKeys(): Set<String> = attributes.keys
        
        companion object {
            fun empty() = Object(emptyMap())
            fun of(vararg pairs: Pair<String, UDM>) = Object(pairs.toMap())
            fun withAttributes(
                properties: Map<String, UDM>,
                attributes: Map<String, String>
            ) = Object(properties, attributes)
        }
    }
    
    /**
     * Date/time values with timezone support
     */
    data class DateTime(val instant: Instant) : UDM() {
        fun toISOString(): String = instant.toString()
        fun toEpochMillis(): Long = instant.toEpochMilli()
        
        companion object {
            fun now() = DateTime(Instant.now())
            fun fromEpochMillis(millis: Long) = DateTime(Instant.ofEpochMilli(millis))
            fun parse(iso8601: String) = DateTime(Instant.parse(iso8601))
        }
    }

    /**
    * Binary data type (byte arrays)
    */
    data class Binary(val data: ByteArray) : UDM() {
        fun toJSON(): String = "\"<binary:${data.size} bytes>\""
        
        override fun toString(): String = "Binary(${data.size} bytes)"
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Binary) return false
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int = data.contentHashCode()
    }
    
    // Utility methods available on all UDM nodes
    fun isScalar(): Boolean = this is Scalar
    fun isArray(): Boolean = this is Array
    fun isObject(): Boolean = this is Object
    fun isDateTime(): Boolean = this is DateTime
    
    fun asScalar(): Scalar? = this as? Scalar
    fun asArray(): Array? = this as? Array
    fun asObject(): Object? = this as? Object
    fun asDateTime(): DateTime? = this as? DateTime
}

/**
 * Navigator provides path-based access to UDM structures
 * Implements the selector syntax: input.Order.Customer.Name
 */
class UDMNavigator(private val root: UDM) {
    /**
     * Navigate using dot notation path
     * Examples:
     *   navigate("Order.Customer.Name")
     *   navigate("Items[0].price")
     */
    fun navigate(path: String): UDM? {
        val segments = parsePath(path)
        return navigateSegments(root, segments)
    }
    
    /**
     * Navigate using path segments
     */
    private fun navigateSegments(current: UDM, segments: List<PathSegment>): UDM? {
        if (segments.isEmpty()) return current
        
        val segment = segments.first()
        val remaining = segments.drop(1)
        
        return when (segment) {
            is PathSegment.Property -> {
                val obj = current.asObject() ?: return null
                val next = obj.get(segment.name) ?: return null
                navigateSegments(next, remaining)
            }
            is PathSegment.Index -> {
                val arr = current.asArray() ?: return null
                val next = arr.get(segment.index) ?: return null
                navigateSegments(next, remaining)
            }
            is PathSegment.Attribute -> {
                val obj = current.asObject() ?: return null
                val value = obj.getAttribute(segment.name) ?: return null
                if (remaining.isEmpty()) {
                    UDM.Scalar(value)
                } else {
                    null // Can't navigate beyond attribute
                }
            }
            is PathSegment.Wildcard -> {
                // Collect all matching results
                when (current) {
                    is UDM.Array -> {
                        val results = current.elements.mapNotNull { 
                            navigateSegments(it, remaining) 
                        }
                        if (remaining.isEmpty()) current
                        else UDM.Array(results)
                    }
                    is UDM.Object -> {
                        val results = current.properties.values.mapNotNull {
                            navigateSegments(it, remaining)
                        }
                        UDM.Array(results)
                    }
                    else -> null
                }
            }
        }
    }
    
    /**
     * Parse path string into segments
     * Examples:
     *   "Order.Customer.Name" -> [Property("Order"), Property("Customer"), Property("Name")]
     *   "Items[0].price" -> [Property("Items"), Index(0), Property("price")]
     *   "Order.@id" -> [Property("Order"), Attribute("id")]
     */
    private fun parsePath(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        var current = ""
        var i = 0
        
        while (i < path.length) {
            when (val ch = path[i]) {
                '.' -> {
                    if (current.isNotEmpty()) {
                        segments.add(PathSegment.Property(current))
                        current = ""
                    }
                    i++
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        segments.add(PathSegment.Property(current))
                        current = ""
                    }
                    // Parse index
                    val endBracket = path.indexOf(']', i)
                    if (endBracket == -1) throw IllegalArgumentException("Unclosed bracket in path: $path")
                    val indexStr = path.substring(i + 1, endBracket)
                    if (indexStr == "*") {
                        segments.add(PathSegment.Wildcard)
                    } else {
                        val index = indexStr.toIntOrNull() 
                            ?: throw IllegalArgumentException("Invalid index: $indexStr")
                        segments.add(PathSegment.Index(index))
                    }
                    i = endBracket + 1
                }
                '@' -> {
                    // Attribute reference
                    i++
                    val attrName = buildString {
                        while (i < path.length && path[i] != '.' && path[i] != '[') {
                            append(path[i])
                            i++
                        }
                    }
                    segments.add(PathSegment.Attribute(attrName))
                }
                else -> {
                    current += ch
                    i++
                }
            }
        }
        
        if (current.isNotEmpty()) {
            segments.add(PathSegment.Property(current))
        }
        
        return segments
    }
    
    /**
     * Recursive descent: find all nodes matching the property name
     * Implements: input..ProductCode
     */
    fun recursiveDescent(propertyName: String): List<UDM> {
        val results = mutableListOf<UDM>()
        collectRecursive(root, propertyName, results)
        return results
    }
    
    private fun collectRecursive(node: UDM, propertyName: String, results: MutableList<UDM>) {
        when (node) {
            is UDM.Object -> {
                node.get(propertyName)?.let { results.add(it) }
                node.properties.values.forEach { collectRecursive(it, propertyName, results) }
            }
            is UDM.Array -> {
                node.elements.forEach { collectRecursive(it, propertyName, results) }
            }
            else -> { /* Scalars and DateTime are leaf nodes */ }
        }
    }
}

/**
 * Path segments for navigation
 */
sealed class PathSegment {
    data class Property(val name: String) : PathSegment()
    data class Index(val index: Int) : PathSegment()
    data class Attribute(val name: String) : PathSegment()
    object Wildcard : PathSegment()
}
