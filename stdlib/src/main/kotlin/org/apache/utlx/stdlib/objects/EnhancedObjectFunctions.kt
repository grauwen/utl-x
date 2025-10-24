// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/EnhancedObjectFunctions.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Enhanced Object Functions
 * 
 * Advanced object introspection and manipulation functions
 * to achieve parity with DataWeave's object operations.
 * 
 * Functions:
 * - divideBy: Split object into chunks
 * - someEntry: Test if any entry matches predicate
 * - everyEntry: Test if all entries match predicate
 * - mapEntries: Transform entries with mapping function
 * - filterEntries: Filter entries by predicate
 * - reduceEntries: Reduce entries to single value
 * 
 * @since UTL-X 1.1
 */
object EnhancedObjectFunctions {
    
    @UTLXFunction(
        description = "Divides an object into sub-objects containing n key-value pairs each.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "divideBy(...) => result",
        notes = "This is useful for batching operations on large objects or\ncreating pages/chunks of object data.\n[1] number of entries per chunk (Number)\nExample:\n```\n{a: 1, b: 2, c: 3, d: 4, e: 5} divideBy 2\n→ [{a: 1, b: 2}, {c: 3, d: 4}, {e: 5}]\n{name: \"Alice\", age: 30, city: \"NYC\"} divideBy 1\n→ [{name: \"Alice\"}, {age: 30}, {city: \"NYC\"}]\n```",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Divides an object into sub-objects containing n key-value pairs each.
     * 
     * This is useful for batching operations on large objects or
     * creating pages/chunks of object data.
     * 
     * @param args [0] object to divide
     *             [1] number of entries per chunk (Number)
     * @return array of objects, each containing n or fewer entries
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3, d: 4, e: 5} divideBy 2
     * → [{a: 1, b: 2}, {c: 3, d: 4}, {e: 5}]
     * 
     * {name: "Alice", age: 30, city: "NYC"} divideBy 1
     * → [{name: "Alice"}, {age: 30}, {city: "NYC"}]
     * ```
     */
    fun divideBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("divideBy() requires 2 arguments: object, chunkSize")
        }
        
        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("divideBy() first argument must be an object")
        }
        
        val n = args[1].asNumber().toInt()
        if (n <= 0) {
            throw IllegalArgumentException("divideBy() chunk size must be positive, got: $n")
        }
        
        val entries = obj.properties.entries.toList()
        val chunks = entries.chunked(n)
        
        val result = chunks.map { chunk ->
            UDM.Object(chunk.associate { it.key to it.value }.toMutableMap())
        }
        
        return UDM.Array(result)
    }
    
    @UTLXFunction(
        description = "The predicate function receives two arguments: key and value.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "predicateArg: Predicatearg value"
        ],
        returns = "true if any entry in the object satisfies the predicate function.",
        example = "someEntry(...) => result",
        notes = "Returns true if any entry in the object satisfies the predicate function.\nReturns true if at least one entry makes the predicate return true.\n[1] predicate function (key, value) => boolean\nExample:\n```\n{a: 5, b: 15, c: 3} someEntry (k, v) => v > 10\n→ true\n{name: \"Alice\", age: 30} someEntry (k, v) => k == \"age\"\n→ true\n{x: 1, y: 2, z: 3} someEntry (k, v) => v > 10\n→ false\n```",
        tags = ["other", "predicate"],
        since = "1.0"
    )
    /**
     * Returns true if any entry in the object satisfies the predicate function.
     * 
     * The predicate function receives two arguments: key and value.
     * Returns true if at least one entry makes the predicate return true.
     * 
     * @param args [0] object to test
     *             [1] predicate function (key, value) => boolean
     * @return true if any entry matches, false otherwise
     * 
     * Example:
     * ```
     * {a: 5, b: 15, c: 3} someEntry (k, v) => v > 10
     * → true
     * 
     * {name: "Alice", age: 30} someEntry (k, v) => k == "age"
     * → true
     * 
     * {x: 1, y: 2, z: 3} someEntry (k, v) => v > 10
     * → false
     * ```
     */
    fun someEntry(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("someEntry() requires 2 arguments: object, predicate")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("someEntry() first argument must be an object")
        }

        val predicate = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("someEntry() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = obj.properties.entries.any { (key, value) ->
            // Call predicate with key and value
            val predicateResult = predicate.apply(listOf(UDM.Scalar(key), value))

            // Check if result is truthy
            when (predicateResult) {
                is UDM.Scalar -> {
                    when (val scalarValue = predicateResult.value) {
                        is Boolean -> scalarValue
                        is Number -> scalarValue.toDouble() != 0.0
                        is String -> scalarValue.isNotEmpty()
                        null -> false
                        else -> true
                    }
                }
                else -> true
            }
        }

        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "The predicate function receives two arguments: key and value.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "predicateArg: Predicatearg value"
        ],
        returns = "true if all entries in the object satisfy the predicate function.",
        example = "everyEntry(...) => result",
        notes = "Returns true if all entries in the object satisfy the predicate function.\nReturns true only if all entries make the predicate return true.\nReturns true for empty objects.\n[1] predicate function (key, value) => boolean\nExample:\n```\n{a: 1, b: 2, c: 3} everyEntry (k, v) => isNumber(v)\n→ true\n{a: 1, b: \"2\", c: 3} everyEntry (k, v) => isNumber(v)\n→ false\n{x: 10, y: 20, z: 30} everyEntry (k, v) => v > 5\n→ true\n{} everyEntry (k, v) => false\n→ true (vacuously true for empty object)\n```",
        tags = ["cleanup", "other", "predicate"],
        since = "1.0"
    )
    /**
     * Returns true if all entries in the object satisfy the predicate function.
     * 
     * The predicate function receives two arguments: key and value.
     * Returns true only if all entries make the predicate return true.
     * Returns true for empty objects.
     * 
     * @param args [0] object to test
     *             [1] predicate function (key, value) => boolean
     * @return true if all entries match, false otherwise
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3} everyEntry (k, v) => isNumber(v)
     * → true
     * 
     * {a: 1, b: "2", c: 3} everyEntry (k, v) => isNumber(v)
     * → false
     * 
     * {x: 10, y: 20, z: 30} everyEntry (k, v) => v > 5
     * → true
     * 
     * {} everyEntry (k, v) => false
     * → true (vacuously true for empty object)
     * ```
     */
    fun everyEntry(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("everyEntry() requires 2 arguments: object, predicate")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("everyEntry() first argument must be an object")
        }

        val predicate = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("everyEntry() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = obj.properties.entries.all { (key, value) ->
            // Call predicate with key and value
            val predicateResult = predicate.apply(listOf(UDM.Scalar(key), value))

            // Check if result is truthy
            when (predicateResult) {
                is UDM.Scalar -> {
                    when (val scalarValue = predicateResult.value) {
                        is Boolean -> scalarValue
                        is Number -> scalarValue.toDouble() != 0.0
                        is String -> scalarValue.isNotEmpty()
                        null -> false
                        else -> true
                    }
                }
                else -> true
            }
        }

        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Transforms each entry in the object using a mapping function.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "mapperArg: Mapperarg value"
        ],
        returns = "Result of the operation",
        example = "mapEntries(...) => result",
        notes = "The mapper function receives two arguments: key and value.\nIt should return an object with 'key' and 'value' properties\nrepresenting the new key and value for that entry.\n[1] mapper function (key, value) => {key: newKey, value: newValue}\nExample:\n```\n{a: 1, b: 2} mapEntries (k, v) => {key: upper(k), value: v * 2}\n→ {A: 2, B: 4}\n{first: \"John\", last: \"Doe\"} mapEntries (k, v) => {key: k + \"_name\", value: upper(v)}\n→ {first_name: \"JOHN\", last_name: \"DOE\"}\n{x: 10, y: 20} mapEntries (k, v) => {key: k, value: v / 10}\n→ {x: 1, y: 2}\n```",
        tags = ["other", "transform"],
        since = "1.0"
    )
    /**
     * Transforms each entry in the object using a mapping function.
     * 
     * The mapper function receives two arguments: key and value.
     * It should return an object with 'key' and 'value' properties
     * representing the new key and value for that entry.
     * 
     * @param args [0] object to transform
     *             [1] mapper function (key, value) => {key: newKey, value: newValue}
     * @return new object with transformed entries
     * 
     * Example:
     * ```
     * {a: 1, b: 2} mapEntries (k, v) => {key: upper(k), value: v * 2}
     * → {A: 2, B: 4}
     * 
     * {first: "John", last: "Doe"} mapEntries (k, v) => {key: k + "_name", value: upper(v)}
     * → {first_name: "JOHN", last_name: "DOE"}
     * 
     * {x: 10, y: 20} mapEntries (k, v) => {key: k, value: v / 10}
     * → {x: 1, y: 2}
     * ```
     */
    fun mapEntries(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("mapEntries() requires 2 arguments: object, mapper")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("mapEntries() first argument must be an object")
        }

        val mapper = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("mapEntries() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = mutableMapOf<String, UDM>()
        obj.properties.entries.forEach { (key, value) ->
            // Call mapper with key and value
            val mapped = mapper.apply(listOf(UDM.Scalar(key), value))

            // Extract {key: newKey, value: newValue} from result
            val mappedObj = mapped as? UDM.Object
                ?: throw IllegalArgumentException(
                    "mapEntries mapper must return an object with 'key' and 'value' properties. " +
                    "Got: ${mapped::class.simpleName}"
                )

            val newKey = mappedObj.properties["key"]?.let { keyUdm ->
                when (keyUdm) {
                    is UDM.Scalar -> keyUdm.value?.toString() ?: key
                    else -> throw IllegalArgumentException("'key' property must be a scalar value")
                }
            } ?: key

            val newValue = mappedObj.properties["value"] ?: value

            result[newKey] = newValue
        }

        return UDM.Object(result)
    }
    
    @UTLXFunction(
        description = "Filters an object to include only entries that satisfy the predicate.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "predicateArg: Predicatearg value"
        ],
        returns = "New array with filtered elements",
        example = "filterEntries(...) => result",
        notes = "The predicate function receives two arguments: key and value.\nOnly entries where the predicate returns true are included.\n[1] predicate function (key, value) => boolean\nExample:\n```\n{a: 1, b: 2, c: 3, d: 4} filterEntries (k, v) => v > 2\n→ {c: 3, d: 4}\n{name: \"Alice\", age: 30, city: \"NYC\"} filterEntries (k, v) => k != \"age\"\n→ {name: \"Alice\", city: \"NYC\"}\n{x: 10, y: 20, z: 5} filterEntries (k, v) => v >= 10\n→ {x: 10, y: 20}\n```",
        tags = ["filter", "other", "predicate"],
        since = "1.0"
    )
    /**
     * Filters an object to include only entries that satisfy the predicate.
     * 
     * The predicate function receives two arguments: key and value.
     * Only entries where the predicate returns true are included.
     * 
     * @param args [0] object to filter
     *             [1] predicate function (key, value) => boolean
     * @return new object containing only matching entries
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3, d: 4} filterEntries (k, v) => v > 2
     * → {c: 3, d: 4}
     * 
     * {name: "Alice", age: 30, city: "NYC"} filterEntries (k, v) => k != "age"
     * → {name: "Alice", city: "NYC"}
     * 
     * {x: 10, y: 20, z: 5} filterEntries (k, v) => v >= 10
     * → {x: 10, y: 20}
     * ```
     */
    fun filterEntries(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("filterEntries() requires 2 arguments: object, predicate")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("filterEntries() first argument must be an object")
        }

        val predicate = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("filterEntries() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = mutableMapOf<String, UDM>()
        obj.properties.entries.forEach { (key, value) ->
            // Call predicate with key and value
            val predicateResult = predicate.apply(listOf(UDM.Scalar(key), value))

            // Check if result is truthy
            val include = when (predicateResult) {
                is UDM.Scalar -> {
                    when (val scalarValue = predicateResult.value) {
                        is Boolean -> scalarValue
                        is Number -> scalarValue.toDouble() != 0.0
                        is String -> scalarValue.isNotEmpty()
                        null -> false
                        else -> true
                    }
                }
                else -> true // Non-scalar values are truthy
            }

            if (include) {
                result[key] = value
            }
        }

        return UDM.Object(result)
    }
    
    @UTLXFunction(
        description = "Reduces all entries in an object to a single value.",
        minArgs = 3,
        maxArgs = 3,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "reducerArg: Reducerarg value"
        ],
        returns = "Result of the operation",
        example = "reduceEntries(...) => result",
        notes = "The reducer function receives three arguments:\n- accumulator: the accumulated value so far\n- key: current entry's key\n- value: current entry's value\n[1] reducer function (acc, key, value) => newAcc\n[2] initial accumulator value\nExample:\n```\n{a: 1, b: 2, c: 3} reduceEntries ((acc, k, v) => acc + v, 0)\n→ 6\n{x: 10, y: 20, z: 30} reduceEntries ((acc, k, v) => acc ++ k, \"\")\n→ \"xyz\"\n{items: 3, price: 10} reduceEntries ((acc, k, v) => acc * v, 1)\n→ 30\n```",
        tags = ["aggregate", "other"],
        since = "1.0"
    )
    /**
     * Reduces all entries in an object to a single value.
     * 
     * The reducer function receives three arguments:
     * - accumulator: the accumulated value so far
     * - key: current entry's key
     * - value: current entry's value
     * 
     * @param args [0] object to reduce
     *             [1] reducer function (acc, key, value) => newAcc
     *             [2] initial accumulator value
     * @return final accumulated value
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3} reduceEntries ((acc, k, v) => acc + v, 0)
     * → 6
     * 
     * {x: 10, y: 20, z: 30} reduceEntries ((acc, k, v) => acc ++ k, "")
     * → "xyz"
     * 
     * {items: 3, price: 10} reduceEntries ((acc, k, v) => acc * v, 1)
     * → 30
     * ```
     */
    fun reduceEntries(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("reduceEntries() requires 3 arguments: object, reducer, initial")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("reduceEntries() first argument must be an object")
        }

        val reducer = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("reduceEntries() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        var accumulator = args[2]

        obj.properties.entries.forEach { (key, value) ->
            // Call reducer with (accumulator, key, value)
            accumulator = reducer.apply(listOf(accumulator, UDM.Scalar(key), value))
        }

        return accumulator
    }
    
    @UTLXFunction(
        description = "[1] predicate function (key, value) => boolean",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "predicate: Function to test each element (element) => boolean",
        "predicateArg: Predicatearg value"
        ],
        returns = "the number of entries in an object that satisfy the predicate.",
        example = "countEntries(...) => result",
        notes = "Returns the number of entries in an object that satisfy the predicate.\nExample:\n```\n{a: 1, b: 2, c: 3, d: 4} countEntries (k, v) => v > 2\n→ 2\n{name: \"Alice\", age: 30, city: \"NYC\"} countEntries (k, v) => isString(v)\n→ 2\n```",
        tags = ["other", "predicate"],
        since = "1.0"
    )
    /**
     * Returns the number of entries in an object that satisfy the predicate.
     * 
     * @param args [0] object to count
     *             [1] predicate function (key, value) => boolean
     * @return count of matching entries
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3, d: 4} countEntries (k, v) => v > 2
     * → 2
     * 
     * {name: "Alice", age: 30, city: "NYC"} countEntries (k, v) => isString(v)
     * → 2
     * ```
     */
    fun countEntries(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("countEntries() requires 2 arguments: object, predicate")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("countEntries() first argument must be an object")
        }

        val predicate = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("countEntries() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val count = obj.properties.entries.count { (key, value) ->
            // Call predicate with key and value
            val predicateResult = predicate.apply(listOf(UDM.Scalar(key), value))

            // Check if result is truthy
            when (predicateResult) {
                is UDM.Scalar -> {
                    when (val scalarValue = predicateResult.value) {
                        is Boolean -> scalarValue
                        is Number -> scalarValue.toDouble() != 0.0
                        is String -> scalarValue.isNotEmpty()
                        null -> false
                        else -> true
                    }
                }
                else -> true
            }
        }

        return UDM.Scalar(count)
    }
    
    @UTLXFunction(
        description = "Transforms an object's keys using a mapping function.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "obj: Obj value",
        "mapperArg: Mapperarg value"
        ],
        returns = "Result of the operation",
        example = "mapKeys(...) => result",
        notes = "[1] mapper function (key) => newKey\nExample:\n```\n{firstName: \"Alice\", lastName: \"Smith\"} mapKeys (k) => snakeCase(k)\n→ {first_name: \"Alice\", last_name: \"Smith\"}\n{a: 1, b: 2, c: 3} mapKeys (k) => upper(k)\n→ {A: 1, B: 2, C: 3}\n```",
        tags = ["other", "transform"],
        since = "1.0"
    )
    /**
     * Transforms an object's keys using a mapping function.
     * 
     * @param args [0] object to transform
     *             [1] mapper function (key) => newKey
     * @return new object with transformed keys
     * 
     * Example:
     * ```
     * {firstName: "Alice", lastName: "Smith"} mapKeys (k) => snakeCase(k)
     * → {first_name: "Alice", last_name: "Smith"}
     * 
     * {a: 1, b: 2, c: 3} mapKeys (k) => upper(k)
     * → {A: 1, B: 2, C: 3}
     * ```
     */
    fun mapKeys(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("mapKeys() requires 2 arguments: object, mapper")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("mapKeys() first argument must be an object")
        }

        val mapper = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("mapKeys() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = mutableMapOf<String, UDM>()
        obj.properties.entries.forEach { (key, value) ->
            // Call mapper with just the key
            val newKeyUdm = mapper.apply(listOf(UDM.Scalar(key)))

            // Extract string key from result
            val newKey = when (newKeyUdm) {
                is UDM.Scalar -> newKeyUdm.value?.toString() ?: key
                else -> throw IllegalArgumentException("mapKeys mapper must return a scalar value for the key")
            }

            result[newKey] = value
        }

        return UDM.Object(result)
    }
    
    @UTLXFunction(
        description = "Transforms an object's values using a mapping function.",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "obj: Obj value",
        "mapperArg: Mapperarg value"
        ],
        returns = "Result of the operation",
        example = "mapValues(...) => result",
        notes = "[1] mapper function (value) => newValue\nExample:\n```\n{a: 1, b: 2, c: 3} mapValues (v) => v * 2\n→ {a: 2, b: 4, c: 6}\n{name: \"alice\", city: \"new york\"} mapValues (v) => upper(v)\n→ {name: \"ALICE\", city: \"NEW YORK\"}\n```",
        tags = ["other", "transform"],
        since = "1.0"
    )
    /**
     * Transforms an object's values using a mapping function.
     * 
     * @param args [0] object to transform
     *             [1] mapper function (value) => newValue
     * @return new object with transformed values
     * 
     * Example:
     * ```
     * {a: 1, b: 2, c: 3} mapValues (v) => v * 2
     * → {a: 2, b: 4, c: 6}
     * 
     * {name: "alice", city: "new york"} mapValues (v) => upper(v)
     * → {name: "ALICE", city: "NEW YORK"}
     * ```
     */
    fun mapValues(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("mapValues() requires 2 arguments: object, mapper")
        }

        val obj = args[0]
        if (obj !is UDM.Object) {
            throw IllegalArgumentException("mapValues() first argument must be an object")
        }

        val mapper = args[1] as? UDM.Lambda
            ?: throw IllegalArgumentException("mapValues() second argument must be a lambda function. Got: ${args[1]::class.simpleName}")

        val result = mutableMapOf<String, UDM>()
        obj.properties.entries.forEach { (key, value) ->
            // Call mapper with just the value
            val newValue = mapper.apply(listOf(value))
            result[key] = newValue
        }

        return UDM.Object(result)
    }
}
