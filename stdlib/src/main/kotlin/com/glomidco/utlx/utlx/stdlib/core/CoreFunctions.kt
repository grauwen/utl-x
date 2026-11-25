// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/core/CoreFunctions.kt
package com.glomidco.utlx.stdlib.core

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

/**
 * Core control flow and utility functions
 */
object CoreFunctions {
    
    @UTLXFunction(
        description = "Selects one of two values according to a condition",
        minArgs = 3,
        maxArgs = 3,
        category = "Core",
        parameters = [
            "condition: Value tested for truthiness",
            "whenTrue: Returned when the condition holds",
            "whenFalse: Returned when it does not"
        ],
        returns = "whenTrue or whenFalse",
        example = "conditional(price > 100, \"expensive\", \"affordable\")",
        notes = "Selects between two VALUES; it is not the conditional statement. Both branches " +
                "are arguments, so both are evaluated before the call: " +
                "`conditional(d != 0, 1 / d, -1)` fails on d = 0, where the `if (c) a else b` " +
                "syntax yields -1 because it evaluates only the branch it takes. Use the syntax " +
                "whenever a branch must not run. (Registered as `if` until 1.4.5, where it was " +
                "unreachable - the parser owns that keyword. See B70.)",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Selects one of two values according to a condition.
     * Usage: conditional(price > 100, "expensive", "affordable")
     *
     * Eager: both branches are arguments and are evaluated before this is called. The `if (c) a else b`
     * syntax short-circuits; this does not.
     */
    fun conditional(args: List<UDM>): UDM {
        requireArgs(args, 3, "conditional")
        
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
    
    // `generateUuid` was the original v4 generator and lived here. When UUIDFunctions was added
    // for UUID v7 it brought its own v4 along — its header still says "UUID v4: Random UUID
    // (existing in CoreFunctions)" — and the registration was re-pointed at the newer class:
    //
    //     register("generateUuid", UUIDFunctions::generateUuidV4)     // Functions.kt
    //
    // Both produced `UUID.randomUUID().toString()`, so the behaviour is unchanged; the live one
    // returns it via `UDM.fromNative`, which for a String is `Scalar.string` — identical output.
    // The one difference is arity: the version removed here called `requireArgs(args, 0, …)` and
    // rejected arguments, whereas `generateUuidV4` ignores them, so `generateUuid(1,2,3)` now
    // silently returns a UUID.
    //
    // Nothing referenced this function. It remained with an `@UTLXFunction` annotation documenting
    // it as `generate-uuid()`, a name that never resolved (B55). Removed 2026-11-12.

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
        example = "contains(\"hello world\", \"world\") => true",
        notes = "Works with:\n- Strings: checks if string contains substring\n- Arrays: checks if array contains element\n- Objects: checks if object contains key (key existence only, not value)\nRules:\n- null finds nothing: contains(\"abc\", null) and contains(null, \"a\") are false\n- in a string or an object's keys, the value searched for must be a single value; a list or object is an error\n- numbers are compared as text: contains(12345, \"23\") is true\nExamples:\n```\ncontains(\"hello world\", \"world\") // true\ncontains(\"hello\", \"xyz\") // false\ncontains([1, 2, 3], 2) // true\ncontains([1, 2, 3], 5) // false\ncontains({\"name\": \"John\", \"age\": 30}, \"name\") // true (key exists)\ncontains({\"name\": \"John\"}, \"email\") // false (key doesn't exist)\n```",
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
            throw FunctionArgumentException(
                "contains expects 2 arguments, got ${args.size}. " +
                "Hint: Usage is contains(collection, element) - e.g., contains([1,2,3], 2) or contains(\"hello\", \"lo\")"
            )
        }
        
        val collection = args[0]
        val element = args[1]

        // B72: substring and key lookups need a single value to look for. Both branches used to
        // read it as `(element as? UDM.Scalar)?.value?.toString() ?: ""`, so a null or a
        // non-scalar became "" — and every string contains "". contains("TX,OK,NM,AZ", $state)
        // was therefore TRUE for a missing state. Now: null finds nothing, a list or object is a
        // script error. (Arrays are unaffected: they compare elements, and a list may hold lists.)
        fun searchText(lookingIn: String): String? = when {
            element is UDM.Scalar && element.value == null -> null
            element is UDM.Scalar -> element.value.toString()
            else -> throw FunctionArgumentException(
                "contains cannot search $lookingIn for ${element.javaClass.simpleName.lowercase()}: " +
                "the second argument must be a single value. " +
                "Hint: to test list membership, put the list first - contains(list, value)"
            )
        }

        val result = when (collection) {
            is UDM.Scalar -> {
                // String contains substring. Numbers are compared as text, on purpose:
                // contains(12345, "23") is true, so digits read from XML/CSV (text) and from
                // JSON (numbers) give the same answer.
                // B72: a null value contains nothing, and nothing is found in it.
                val search = searchText("text")
                if (collection.value == null || search == null) false
                else collection.value.toString().contains(search)
            }
            is UDM.Array -> {
                // Array contains element
                collection.elements.any { it == element }
            }
            is UDM.Object -> {
                // Object contains key (convention: check keys only)
                val key = searchText("an object's keys")
                key != null && collection.properties.containsKey(key)
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
        description = "Concatenates values into a single string",
        minArgs = 1,
        maxArgs = 4,
        category = "Core",
        parameters = [
            "value: First value, rendered as text",
            "more...: Further values, appended in order (variadic)"
        ],
        returns = "A string",
        example = "concat(\"Hello\", \" \", \"World\") => \"Hello World\"",
        notes = "Strings only, as in XPath/XSLT: every argument is rendered as text and joined, " +
                "so concat(\"ORD-\", 42) is \"ORD-42\". For other types use the function that " +
                "says so - arrayConcat for arrays, merge for objects, binaryConcat for binary, " +
                "sum to add numbers. Until 1.4.5 this one function did all four, dispatching on " +
                "the first argument's type, which made concat(42, 58) return 100 rather than " +
                "\"4258\" (B71).",
        tags = ["core", "string"],
        since = "1.0"
    )
    /**
     * Concatenates values into a single string.
     * Usage: concat("Hello", " ", "World") => "Hello World"
     *
     * B71: strings only. This used to dispatch on the first argument's type and so served as
     * string-concat, array-concat, object-merge AND number-addition - four unrelated operations
     * behind one name, three of which already had a proper name (`arrayConcat`, `merge`, `sum`).
     * Every call site in the repository - all 70 - used the string form.
     *
     * Rendering goes through `asString()`, which is the same path `+` uses, so B35's exact
     * numerics survive: `concat("x", 100.10)` is "x100.10", not "x100.1".
     */
    fun concat(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "concat expects at least 1 argument, got 0. " +
                "Hint: Provide values to concatenate - e.g., concat(\"Hello\", \" \", \"World\")"
            )
        }

        // A structured argument is almost always someone reaching for the operation this
        // function used to perform, so name the one that does it rather than just refusing.
        for (arg in args) {
            val alternative = when (arg) {
                is UDM.Array -> "arrayConcat(...) joins arrays; joinBy(array, separator) makes a string from one"
                is UDM.Object -> "merge(...) combines objects"
                is UDM.Binary -> "binaryConcat(...) joins binary data"
                is UDM.Lambda -> "a function has no text form"
                else -> null
            }
            if (alternative != null) {
                throw FunctionArgumentException(
                    "concat builds a string, but got ${getTypeDescription(arg)}. Hint: $alternative."
                )
            }
        }

        return UDM.Scalar(args.joinToString("") { it.asString() })
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
            ?: throw FunctionArgumentException(
                "filter requires a lambda as second argument, but got ${getTypeDescription(args[1])}. " +
                "Hint: Use x => expression syntax to create a lambda function, e.g., filter([1,2,3], x => x > 1)"
            )

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
                    throw FunctionArgumentException(
                        "filter requires first argument to be an array, object, or string, but got ${getTypeDescription(value)}. " +
                        "Hint: filter() works on collections and strings. Use it to select matching elements."
                    )
                }
            }
            else -> throw FunctionArgumentException(
                "filter requires first argument to be an array, object, or string, but got ${getTypeDescription(value)}. " +
                "Hint: filter() works on collections and strings. Use it to select matching elements."
            )
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

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }
}
