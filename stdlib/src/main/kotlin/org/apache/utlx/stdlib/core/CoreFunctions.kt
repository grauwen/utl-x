// stdlib/src/main/kotlin/org/apache/utlx/stdlib/core/CoreFunctions.kt
package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Core control flow and utility functions
 */
object CoreFunctions {
    
    @UTLXFunction(
        description = "Inline if-then-else conditional",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "if(price > 100, \"expensive\", \"affordable\")",
        notes = "This is CRITICAL for concise transformations",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Inline if-then-else conditional
     * Usage: if(price > 100, "expensive", "affordable")
     * 
     * This is CRITICAL for concise transformations
     */
    fun ifThenElse(args: List<UDM>): UDM {
        requireArgs(args, 3, "if")
        
        val condition = args[0].asBoolean()
        return if (condition) args[1] else args[2]
    }
    
    @UTLXFunction(
        description = "Coalesce - return first non-null value",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "coalesce(input.email, input.contact.email, \"no-email@example.com\")",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Coalesce - return first non-null value
     * Usage: coalesce(input.email, input.contact.email, "no-email@example.com")
     */
    fun coalesce(args: List<UDM>): UDM {
        for (arg in args) {
            if (arg !is UDM.Scalar || arg.value != null) {
                return arg
            }
        }
        return UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Generate UUID/GUID",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "generate-uuid()",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Generate UUID/GUID
     * Usage: generate-uuid()
     */
    fun generateUuid(args: List<UDM>): UDM {
        requireArgs(args, 0, "generate-uuid")
        return UDM.Scalar(java.util.UUID.randomUUID().toString())
    }
    
    @UTLXFunction(
        description = "Default value if undefined or null",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "default(input.optional, \"default-value\")",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Default value if undefined or null
     * Usage: default(input.optional, "default-value")
     */
    fun default(args: List<UDM>): UDM {
        requireArgs(args, 2, "default")
        val value = args[0]
        return if (value is UDM.Scalar && value.value == null) {
            args[1]
        } else {
            value
        }
    }
    
    @UTLXFunction(
        description = "Checks if a value is empty",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Boolean indicating the result",
        example = "isEmpty(...) => result",
        notes = "Works with strings, arrays, objects\nExamples:\n```\nisEmpty(\"\") // true\nisEmpty(\"hello\") // false\nisEmpty([]) // true\nisEmpty([1, 2]) // false\nisEmpty({}) // true\nisEmpty({\"name\": \"John\"}) // false\nisEmpty(null) // true\n```",
        tags = ["cleanup", "core", "null-handling"],
        since = "1.0"
    )
    /**
     * Checks if a value is empty
     * Works with strings, arrays, objects
     * @param value The value to check
     * @return true if empty
     * 
     * Examples:
     * ```
     * isEmpty("") // true
     * isEmpty("hello") // false
     * isEmpty([]) // true
     * isEmpty([1, 2]) // false
     * isEmpty({}) // true
     * isEmpty({"name": "John"}) // false
     * isEmpty(null) // true
     * ```
     */
    fun isEmpty(args: List<UDM>): UDM {
        requireArgs(args, 1, "isEmpty")
        val value = args[0]
        
        val empty = when (value) {
            is UDM.Scalar -> {
                val v = value.value
                when (v) {
                    null -> true
                    is String -> v.isEmpty()
                    is Number -> false  // Numbers are never considered "empty"
                    is Boolean -> false // Booleans are never considered "empty"
                    else -> false
                }
            }
            is UDM.Array -> value.elements.isEmpty()
            is UDM.Object -> value.properties.isEmpty()
            is UDM.DateTime -> false // DateTime values are never considered "empty"
            is UDM.Binary -> value.data.isEmpty()
            is UDM.Lambda -> false // Functions are never considered "empty"
            else -> true
        }
        
        return UDM.Scalar(empty)
    }
    
    @UTLXFunction(
        description = "Checks if a value is not empty (inverse of isEmpty)",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "element: Element value"
        ],
        returns = "Boolean indicating the result",
        example = "isNotEmpty(...) => result",
        notes = "Works with strings, arrays, objects, and null values\nExamples:\n```\nisNotEmpty(\"\") // false\nisNotEmpty(\"hello\") // true\nisNotEmpty([]) // false\nisNotEmpty([1, 2]) // true\nisNotEmpty({}) // false\nisNotEmpty({\"name\": \"John\"}) // true\nisNotEmpty(null) // false\nisNotEmpty(42) // true (numbers are never empty)\nisNotEmpty(true) // true (booleans are never empty)\n```",
        tags = ["cleanup", "core", "null-handling"],
        since = "1.0"
    )
    /**
     * Checks if a value is not empty (inverse of isEmpty)
     * Works with strings, arrays, objects, and null values
     * 
     * @param value The value to check
     * @return true if not empty, false if empty
     * 
     * Examples:
     * ```
     * isNotEmpty("") // false
     * isNotEmpty("hello") // true
     * isNotEmpty([]) // false
     * isNotEmpty([1, 2]) // true
     * isNotEmpty({}) // false
     * isNotEmpty({"name": "John"}) // true
     * isNotEmpty(null) // false
     * isNotEmpty(42) // true (numbers are never empty)
     * isNotEmpty(true) // true (booleans are never empty)
     * ```
     */
    fun isNotEmpty(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNotEmpty")
        val isEmpty = isEmpty(args)
        val isEmptyValue = (isEmpty as UDM.Scalar).value as Boolean
        return UDM.Scalar(!isEmptyValue)
    }
    
    @UTLXFunction(
        description = "Checks if a value contains an element, substring, or key",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "element: Element value"
        ],
        returns = "Result of the operation",
        example = "contains(...) => result",
        notes = "Works with:\n- Strings: checks if string contains substring\n- Arrays: checks if array contains element\n- Objects: checks if object contains key (key existence only, not value)\nExamples:\n```\ncontains(\"hello world\", \"world\") // true\ncontains(\"hello\", \"xyz\") // false\ncontains([1, 2, 3], 2) // true\ncontains([1, 2, 3], 5) // false\ncontains({\"name\": \"John\", \"age\": 30}, \"name\") // true (key exists)\ncontains({\"name\": \"John\"}, \"email\") // false (key doesn't exist)\n```",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Checks if a value contains an element, substring, or key
     * 
     * Works with:
     * - Strings: checks if string contains substring
     * - Arrays: checks if array contains element
     * - Objects: checks if object contains key (key existence only, not value)
     * 
     * @param collection The collection to search in
     * @param element The element/substring/key to search for
     * @return true if found, false otherwise
     * 
     * Examples:
     * ```
     * contains("hello world", "world") // true
     * contains("hello", "xyz") // false
     * contains([1, 2, 3], 2) // true
     * contains([1, 2, 3], 5) // false
     * contains({"name": "John", "age": 30}, "name") // true (key exists)
     * contains({"name": "John"}, "email") // false (key doesn't exist)
     * ```
     */
    fun contains(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw FunctionArgumentException("contains expects 2 arguments, got ${args.size}")
        }
        
        val collection = args[0]
        val element = args[1]
        
        val result = when (collection) {
            is UDM.Scalar -> {
                // String contains substring
                val str = collection.value?.toString() ?: ""
                val searchStr = (element as? UDM.Scalar)?.value?.toString() ?: ""
                str.contains(searchStr)
            }
            is UDM.Array -> {
                // Array contains element
                collection.elements.any { it == element }
            }
            is UDM.Object -> {
                // Object contains key (convention: check keys only)
                val key = (element as? UDM.Scalar)?.value?.toString() ?: ""
                collection.properties.containsKey(key)
            }
            is UDM.Binary -> {
                // Binary data - check if contains byte sequence
                if (element is UDM.Binary) {
                    val searchBytes = element.data
                    val dataBytes = collection.data
                    if (searchBytes.isEmpty()) {
                        true
                    } else {
                        // Simple byte sequence search
                        var found = false
                        for (i in 0..(dataBytes.size - searchBytes.size)) {
                            var matches = true
                            for (j in searchBytes.indices) {
                                if (dataBytes[i + j] != searchBytes[j]) {
                                    matches = false
                                    break
                                }
                            }
                            if (matches) {
                                found = true
                                break
                            }
                        }
                        found
                    }
                } else {
                    false
                }
            }
            else -> false
        }
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Concatenates values of the same type",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "firstArg: Firstarg value"
        ],
        returns = "Result of the operation",
        example = "concat(...) => result",
        notes = "- Strings: Combines into single string\n- Arrays: Combines into single array (preserves duplicates)\n- Objects: Merges properties (right overwrites left)\nExamples:\n```\nconcat(\"Hello\", \" \", \"World\") // \"Hello World\"\nconcat([1, 2], [3, 4], [5]) // [1, 2, 3, 4, 5]\nconcat({\"a\": 1}, {\"b\": 2}, {\"a\": 3}) // {\"a\": 3, \"b\": 2} (right overwrites)\nconcat(42, 58) // 100 (numbers are added)\n```",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Concatenates values of the same type
     * - Strings: Combines into single string
     * - Arrays: Combines into single array (preserves duplicates)
     * - Objects: Merges properties (right overwrites left)
     * 
     * @param values Values to concatenate (must be same type)
     * @return Concatenated result
     * 
     * Examples:
     * ```
     * concat("Hello", " ", "World") // "Hello World"
     * concat([1, 2], [3, 4], [5]) // [1, 2, 3, 4, 5]
     * concat({"a": 1}, {"b": 2}, {"a": 3}) // {"a": 3, "b": 2} (right overwrites)
     * concat(42, 58) // 100 (numbers are added)
     * ```
     */
    fun concat(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("concat expects at least 1 argument")
        }
        
        if (args.size == 1) {
            return args[0]
        }
        
        // Determine the type from the first argument
        val firstArg = args[0]
        
        return when (firstArg) {
            is UDM.Scalar -> {
                when (val firstValue = firstArg.value) {
                    is String -> {
                        // String concatenation
                        val result = args.joinToString("") { arg ->
                            when (arg) {
                                is UDM.Scalar -> arg.value?.toString() ?: ""
                                else -> throw FunctionArgumentException("concat: all arguments must be strings when concatenating strings")
                            }
                        }
                        UDM.Scalar(result)
                    }
                    is Number -> {
                        // Number addition (sum)
                        var sum = firstValue.toDouble()
                        for (i in 1 until args.size) {
                            val arg = args[i]
                            if (arg is UDM.Scalar) {
                                val argValue = arg.value
                                if (argValue is Number) {
                                    sum += argValue.toDouble()
                                } else {
                                    throw FunctionArgumentException("concat: all arguments must be numbers when concatenating numbers")
                                }
                            } else {
                                throw FunctionArgumentException("concat: all arguments must be numbers when concatenating numbers")
                            }
                        }
                        UDM.Scalar(sum)
                    }
                    is Boolean -> {
                        // Boolean logical OR
                        var result: Boolean = firstValue
                        for (i in 1 until args.size) {
                            val arg = args[i]
                            if (arg is UDM.Scalar) {
                                val argValue = arg.value
                                if (argValue is Boolean) {
                                    result = result || argValue
                                } else {
                                    throw FunctionArgumentException("concat: all arguments must be booleans when concatenating booleans")
                                }
                            } else {
                                throw FunctionArgumentException("concat: all arguments must be booleans when concatenating booleans")
                            }
                        }
                        UDM.Scalar(result)
                    }
                    null -> {
                        // Return first non-null value, or null if all are null
                        for (arg in args) {
                            if (arg is UDM.Scalar && arg.value != null) {
                                return arg
                            }
                        }
                        UDM.Scalar(null)
                    }
                    else -> {
                        // Other scalar types - convert to strings and concatenate
                        val result = args.joinToString("") { arg ->
                            when (arg) {
                                is UDM.Scalar -> arg.value?.toString() ?: ""
                                else -> throw FunctionArgumentException("concat: all arguments must be scalars when concatenating scalar values")
                            }
                        }
                        UDM.Scalar(result)
                    }
                }
            }
            is UDM.Array -> {
                // Array concatenation
                val allElements = mutableListOf<UDM>()
                for (arg in args) {
                    if (arg is UDM.Array) {
                        allElements.addAll(arg.elements)
                    } else {
                        throw FunctionArgumentException("concat: all arguments must be arrays when concatenating arrays")
                    }
                }
                UDM.Array(allElements)
            }
            is UDM.Object -> {
                // Object merging (right overwrites left)
                val mergedProps = mutableMapOf<String, UDM>()
                val mergedAttrs = mutableMapOf<String, String>()
                
                for (arg in args) {
                    if (arg is UDM.Object) {
                        mergedProps.putAll(arg.properties)
                        mergedAttrs.putAll(arg.attributes)
                    } else {
                        throw FunctionArgumentException("concat: all arguments must be objects when concatenating objects")
                    }
                }
                UDM.Object(mergedProps, mergedAttrs)
            }
            is UDM.Binary -> {
                // Binary data concatenation
                val allBytes = mutableListOf<Byte>()
                for (arg in args) {
                    if (arg is UDM.Binary) {
                        allBytes.addAll(arg.data.toList())
                    } else {
                        throw FunctionArgumentException("concat: all arguments must be binary when concatenating binary data")
                    }
                }
                UDM.Binary(allBytes.toByteArray())
            }
            else -> {
                throw FunctionArgumentException("concat: unsupported type for concatenation: ${firstArg::class.simpleName}")
            }
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    @UTLXFunction(
        description = "Generic filter function that works on arrays, objects, and strings",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "New array with filtered elements",
        example = "filter(...) => result",
        notes = "For arrays: filters elements based on predicate\nFor objects: filters properties based on predicate (key, value)\nFor strings: filters characters based on predicate\nExamples:\n```\nfilter([1, 2, 3, 4], x -> x > 2) // [3, 4]\nfilter({\"a\": 1, \"b\": 2}, (k, v) -> v > 1) // {\"b\": 2}\nfilter(\"hello\", c -> c != \"l\") // \"heo\"\n```",
        tags = ["core", "filter", "predicate"],
        since = "1.0"
    )
    /**
     * Generic filter function that works on arrays, objects, and strings
     * 
     * For arrays: filters elements based on predicate
     * For objects: filters properties based on predicate (key, value)
     * For strings: filters characters based on predicate
     * 
     * Examples:
     * ```
     * filter([1, 2, 3, 4], x -> x > 2) // [3, 4]
     * filter({"a": 1, "b": 2}, (k, v) -> v > 1) // {"b": 2}
     * filter("hello", c -> c != "l") // "heo"
     * ```
     */
    fun filter(args: List<UDM>): UDM {
        requireArgs(args, 2, "filter")
        val value = args[0]
        val predicate = args[1] as? UDM.Lambda
            ?: throw FunctionArgumentException("filter: second argument must be a lambda")

        return when (value) {
            is UDM.Array -> {
                val filteredElements = value.elements.filter { element ->
                    val result = predicate.apply(listOf(element))
                    result.asBoolean()
                }
                UDM.Array(filteredElements)
            }
            is UDM.Object -> {
                val filteredProperties = value.properties.filter { (key, objValue) ->
                    val result = predicate.apply(listOf(UDM.Scalar(key), objValue))
                    result.asBoolean()
                }
                UDM.Object(filteredProperties, value.attributes)
            }
            is UDM.Scalar -> {
                val scalarValue = value.value
                if (scalarValue is String) {
                    val filteredChars = scalarValue.filter { char ->
                        val result = predicate.apply(listOf(UDM.Scalar(char.toString())))
                        result.asBoolean()
                    }
                    UDM.Scalar(filteredChars)
                } else {
                    throw FunctionArgumentException("filter: first argument must be an array, object, or string")
                }
            }
            else -> throw FunctionArgumentException("filter: first argument must be an array, object, or string")
        }
    }

    private fun UDM.asBoolean(): Boolean = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Boolean -> v
                is Number -> v.toDouble() != 0.0
                is String -> v.isNotEmpty()
                null -> false
                else -> true
            }
        }
        is UDM.Array -> elements.isNotEmpty()
        is UDM.Object -> properties.isNotEmpty()
        else -> true
    }
}
