package org.apache.utlx.core.udm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.LocalTime

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
        
        // Type-specific methods for Scalar values
        
        // Safe value access that works better with smart casting
        fun getValueSafe(): Any? = value
        
        // Type-specific getters that avoid smart cast issues
        fun getStringValue(): String? = value as? String
        fun getNumberValue(): Number? = value as? Number
        fun getBooleanValue(): Boolean? = value as? Boolean
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
        val name: String? = null,  // Element/object name (for XML)
        val metadata: Map<String, String> = emptyMap()  // Internal metadata (not serialized)
    ) : UDM() {
        fun get(key: String): UDM? = properties[key]
        fun getAttribute(key: String): String? = attributes[key]
        fun getMetadata(key: String): String? {
            // Try key as-is first, then try with __ prefix for internal metadata keys
            return metadata[key] ?: metadata["__$key"]
        }
        fun hasProperty(key: String): Boolean = properties.containsKey(key)
        fun hasAttribute(key: String): Boolean = attributes.containsKey(key)
        fun hasMetadata(key: String): Boolean = metadata.containsKey(key) || metadata.containsKey("__$key")
        fun keys(): Set<String> = properties.keys
        fun attributeKeys(): Set<String> = attributes.keys
        fun metadataKeys(): Set<String> = metadata.keys

        companion object {
            fun empty() = Object(emptyMap())
            fun of(vararg pairs: Pair<String, UDM>) = Object(pairs.toMap())
            fun withAttributes(
                properties: Map<String, UDM>,
                attributes: Map<String, String>
            ) = Object(properties, attributes)
            fun withMetadata(
                properties: Map<String, UDM>,
                attributes: Map<String, String> = emptyMap(),
                name: String? = null,
                metadata: Map<String, String>
            ) = Object(properties, attributes, name, metadata)
        }
    }
    
    /**
     * Full timestamp with timezone (e.g., "2020-03-15T10:30:00Z")
     * Use for: events, logs, timestamps
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
     * Date only (no time component) (e.g., "2020-03-15")
     * Use for: birth dates, due dates, calendar dates
     * Serializes without time component: "2020-03-15"
     */
    data class Date(val date: LocalDate) : UDM() {
        fun toISOString(): String = date.toString()  // "2020-03-15"
        fun toEpochDay(): Long = date.toEpochDay()

        companion object {
            fun now() = Date(LocalDate.now())
            fun parse(dateStr: String) = Date(LocalDate.parse(dateStr))
            fun of(year: Int, month: Int, day: Int) = Date(LocalDate.of(year, month, day))
        }
    }

    /**
     * Date and time without timezone (e.g., "2020-03-15T10:30:00")
     * Use for: scheduled events, appointments (timezone handled separately)
     */
    data class LocalDateTime(val dateTime: JavaLocalDateTime) : UDM() {
        fun toISOString(): String = dateTime.toString()  // "2020-03-15T10:30:00"

        companion object {
            fun now() = LocalDateTime(JavaLocalDateTime.now())
            fun parse(dateTimeStr: String) = LocalDateTime(JavaLocalDateTime.parse(dateTimeStr))
            fun of(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0) =
                LocalDateTime(JavaLocalDateTime.of(year, month, day, hour, minute, second))
        }
    }

    /**
     * Time only (no date component) (e.g., "14:30:00")
     * Use for: opening hours, time durations
     */
    data class Time(val time: LocalTime) : UDM() {
        fun toISOString(): String = time.toString()  // "14:30:00"
        fun toSecondOfDay(): Int = time.toSecondOfDay()

        companion object {
            fun now() = Time(LocalTime.now())
            fun parse(timeStr: String) = Time(LocalTime.parse(timeStr))
            fun of(hour: Int, minute: Int, second: Int = 0) = Time(LocalTime.of(hour, minute, second))
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
    
    /**
     * Lambda/Function values for functional programming support
     * Used for map, filter, reduce operations
     */
    data class Lambda(val apply: (List<UDM>) -> UDM) : UDM() {
        override fun toString(): String = "Lambda(function)"
        
        // Note: Lambda equality is based on reference equality since functions don't have structural equality
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
        
        companion object {
            /**
             * Create a lambda from a function that takes a single UDM parameter
             */
            fun of(fn: (UDM) -> UDM): Lambda = Lambda { args -> fn(args.firstOrNull() ?: Scalar.nullValue()) }
            
            /**
             * Create a lambda from a function that takes two UDM parameters
             */
            fun of2(fn: (UDM, UDM) -> UDM): Lambda = Lambda { args -> 
                fn(
                    args.getOrNull(0) ?: Scalar.nullValue(),
                    args.getOrNull(1) ?: Scalar.nullValue()
                )
            }
        }
    }
    
    // Utility methods available on all UDM nodes
    fun isScalar(): Boolean = this is Scalar
    fun isArray(): Boolean = this is Array
    fun isObject(): Boolean = this is Object
    fun isDateTime(): Boolean = this is DateTime
    fun isDate(): Boolean = this is Date
    fun isLocalDateTime(): Boolean = this is LocalDateTime
    fun isTime(): Boolean = this is Time
    fun isBinary(): Boolean = this is Binary
    fun isLambda(): Boolean = this is Lambda

    fun asScalar(): Scalar? = this as? Scalar
    fun asArray(): Array? = this as? Array
    fun asObject(): Object? = this as? Object
    fun asDateTime(): DateTime? = this as? DateTime
    fun asDate(): Date? = this as? Date
    fun asLocalDateTime(): LocalDateTime? = this as? LocalDateTime
    fun asTime(): Time? = this as? Time
    fun asBinary(): Binary? = this as? Binary
    fun asLambda(): Lambda? = this as? Lambda
    
    // Additional type checking methods for compatibility
    fun isNull(): Boolean = this is Scalar && this.value == null
    fun isString(): Boolean = this is Scalar && this.value is String
    fun isNumber(): Boolean = this is Scalar && this.value is Number
    fun isBoolean(): Boolean = this is Scalar && this.value is Boolean
    
    // Safe casting methods with better error handling
    fun asString(): String {
        return when (this) {
            is Scalar -> when (value) {
                is String -> value
                is Number -> value.toString()
                is Boolean -> value.toString()
                null -> ""
                else -> value.toString()
            }
            else -> this.toString()
        }
    }
    
    fun asNumber(): Double {
        return when (this) {
            is Scalar -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: 0.0
                is Boolean -> if (value) 1.0 else 0.0
                null -> 0.0
                else -> 0.0
            }
            else -> 0.0
        }
    }
    
    fun asBoolean(): Boolean {
        return when (this) {
            is Scalar -> when (value) {
                is Boolean -> value
                is Number -> value.toDouble() != 0.0
                is String -> value.isNotEmpty() && value.lowercase() in listOf("true", "yes", "1")
                null -> false
                else -> true
            }
            is Array -> elements.isNotEmpty()
            is Object -> properties.isNotEmpty()
            else -> true
        }
    }
    
    /**
     * Get property from object or element from array using string key/index
     */
    fun getProperty(key: String): UDM? {
        return when (this) {
            is Object -> get(key)
            is Array -> {
                val index = key.toIntOrNull()
                if (index != null) get(index) else null
            }
            else -> null
        }
    }
    
    /**
     * Convert UDM to native Kotlin/Java values
     */
    fun toNative(): Any? {
        return when (this) {
            is Scalar -> value
            is Array -> elements.map { it.toNative() }
            is Object -> properties.mapValues { it.value.toNative() }
            is DateTime -> instant
            is Date -> date
            is LocalDateTime -> dateTime
            is Time -> time
            is Binary -> data
            is Lambda -> this // Functions can't be converted to native values
        }
    }
    
    companion object {
        /**
         * Convert native Kotlin/Java values to UDM
         */
        fun fromNative(value: Any?): UDM {
            return when (value) {
                null -> Scalar.nullValue()
                is String -> Scalar.string(value)
                is Number -> Scalar.number(value)
                is Boolean -> Scalar.boolean(value)
                is List<*> -> Array(value.map { fromNative(it) })
                is Map<*, *> -> {
                    val properties = value.entries.associate { (k, v) ->
                        k.toString() to fromNative(v)
                    }
                    Object(properties)
                }
                is ByteArray -> Binary(value)
                is kotlin.Array<*> -> Array(value.map { fromNative(it) })
                else -> Scalar(value) // Fallback for other types
            }
        }
    }
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
            else -> { /* Scalars, DateTime, Date, LocalDateTime, Time, Binary, Lambda are leaf nodes */ }
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
