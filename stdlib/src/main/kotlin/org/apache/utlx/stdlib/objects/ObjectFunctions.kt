// stdlib/src/main/kotlin/org/apache/utlx/stdlib/objects/ObjectFunctions.kt
package org.apache.utlx.stdlib.objects

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

object ObjectFunctions {

    @UTLXFunction(
        description = "Performs keys operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "keys(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun keys(args: List<UDM>): UDM {
        requireArgs(args, 1, "keys")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "keys requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val keysList = obj.properties.keys.map { UDM.Scalar(it) }
        return UDM.Array(keysList)
    }

    @UTLXFunction(
        description = "Performs values operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "values(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun values(args: List<UDM>): UDM {
        requireArgs(args, 1, "values")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "values requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        return UDM.Array(obj.properties.values.toList())
    }

    @UTLXFunction(
        description = "Performs entries operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "entries(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun entries(args: List<UDM>): UDM {
        requireArgs(args, 1, "entries")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "entries requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val entriesList = obj.properties.map { (key, value) ->
            UDM.Array(listOf(UDM.Scalar(key), value))
        }
        return UDM.Array(entriesList)
    }

    @UTLXFunction(
        description = "Performs merge operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "merge(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun merge(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            return UDM.Object(emptyMap(), emptyMap())
        }
        
        val mergedProps = mutableMapOf<String, UDM>()
        val mergedAttrs = mutableMapOf<String, String>()
        
        for (arg in args) {
            val obj = arg.asObject() ?: throw FunctionArgumentException(
                "merge requires all arguments to be objects, but got ${getTypeDescription(arg)}. " +
                "Hint: Ensure all values passed to merge are objects."
            )
            mergedProps.putAll(obj.properties)
            mergedAttrs.putAll(obj.attributes)
        }
        
        return UDM.Object(mergedProps, mergedAttrs)
    }

    @UTLXFunction(
        description = "Performs pick operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process",
        "key: Key value"
        ],
        returns = "Result of the operation",
        example = "pick(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun pick(args: List<UDM>): UDM {
        requireArgs(args, 2, "pick")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "pick requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val keys = (args[1].asArray() ?: throw FunctionArgumentException(
            "pick requires an array as second argument, but got ${getTypeDescription(args[1])}. " +
            "Hint: Pass an array of key names to pick."
        )).elements.map { it.asString() }
        
        val picked = obj.properties.filterKeys { it in keys }
        return UDM.Object(picked, emptyMap())
    }

    @UTLXFunction(
        description = "Performs omit operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process",
        "key: Key value"
        ],
        returns = "Result of the operation",
        example = "omit(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    
    fun omit(args: List<UDM>): UDM {
        requireArgs(args, 2, "omit")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "omit requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val keys = (args[1].asArray() ?: throw FunctionArgumentException(
            "omit requires an array as second argument, but got ${getTypeDescription(args[1])}. " +
            "Hint: Pass an array of key names to omit."
        )).elements.map { it.asString() }
        
        val omitted = obj.properties.filterKeys { it !in keys }
        return UDM.Object(omitted, obj.attributes)
    }
    
    @UTLXFunction(
        description = "Checks if an object contains a specific key",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process",
        "key: Key value"
        ],
        returns = "Result of the operation",
        example = "containsKey(...) => result",
        notes = "Example:\n```\ncontainsKey({\"name\": \"John\", \"age\": 30}, \"name\") // true\ncontainsKey({\"name\": \"John\"}, \"email\") // false\n```",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Checks if an object contains a specific key
     * 
     * @param obj The object to check
     * @param key The key to look for
     * @return true if the object contains the key, false otherwise
     * 
     * Example:
     * ```
     * containsKey({"name": "John", "age": 30}, "name") // true
     * containsKey({"name": "John"}, "email") // false
     * ```
     */
    fun containsKey(args: List<UDM>): UDM {
        requireArgs(args, 2, "containsKey")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "containsKey requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val key = args[1].asString()
        
        return UDM.Scalar(obj.properties.containsKey(key))
    }
    
    @UTLXFunction(
        description = "Checks if an object contains a specific value",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "containsValue(...) => result",
        notes = "Example:\n```\ncontainsValue({\"name\": \"John\", \"age\": 30}, \"John\") // true\ncontainsValue({\"name\": \"John\", \"age\": 30}, \"Jane\") // false\ncontainsValue({\"name\": \"John\", \"age\": 30}, 30) // true\n```",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Checks if an object contains a specific value
     * 
     * @param obj The object to check
     * @param value The value to look for
     * @return true if the object contains the value, false otherwise
     * 
     * Example:
     * ```
     * containsValue({"name": "John", "age": 30}, "John") // true
     * containsValue({"name": "John", "age": 30}, "Jane") // false
     * containsValue({"name": "John", "age": 30}, 30) // true
     * ```
     */
    fun containsValue(args: List<UDM>): UDM {
        requireArgs(args, 2, "containsValue")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "containsValue requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val value = args[1]

        return UDM.Scalar(obj.properties.containsValue(value))
    }

    @UTLXFunction(
        description = "Check if object has a specific key/property",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "object: Object to check",
            "key: Property key to look for"
        ],
        returns = "Boolean indicating whether the key exists",
        example = "hasKey({name: \"John\", age: 30}, \"name\") => true",
        tags = ["other", "objects"],
        since = "1.0"
    )
    /**
     * Check if object has a specific key/property
     * Usage: hasKey({name: "John", age: 30}, "name") => true
     */
    fun hasKey(args: List<UDM>): UDM {
        requireArgs(args, 2, "hasKey")
        val obj = args[0].asObject() ?: throw FunctionArgumentException(
            "hasKey requires an object as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Check if your input is an object."
        )
        val key = args[1].asString()

        return UDM.Scalar(obj.properties.containsKey(key))
    }

    @UTLXFunction(
        description = "Create object from array of [key, value] pairs (inverse of entries)",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Array of [key, value] pairs"
        ],
        returns = "Object created from the key-value pairs",
        example = "fromEntries([[\"name\", \"John\"], [\"age\", 30]]) => {name: \"John\", age: 30}",
        tags = ["other", "objects"],
        since = "1.0"
    )
    /**
     * Create object from array of [key, value] pairs (inverse of entries)
     * Usage: fromEntries([["name", "John"], ["age", 30]]) => {name: "John", age: 30}
     */
    fun fromEntries(args: List<UDM>): UDM {
        requireArgs(args, 1, "fromEntries")
        val array = args[0].asArray() ?: throw FunctionArgumentException(
            "fromEntries requires an array as first argument, but got ${getTypeDescription(args[0])}. " +
            "Hint: Pass an array of [key, value] pairs."
        )

        val properties = mutableMapOf<String, UDM>()

        for (element in array.elements) {
            val pair = element.asArray()
                ?: throw FunctionArgumentException(
                    "fromEntries requires each element to be a [key, value] array, but got ${getTypeDescription(element)}. " +
                    "Hint: Each element should be an array like [\"name\", \"John\"]."
                )

            if (pair.elements.size != 2) {
                throw FunctionArgumentException(
                    "fromEntries requires each element to have exactly 2 items [key, value], but got ${pair.elements.size} items. " +
                    "Hint: Use arrays with exactly 2 elements: [key, value]."
                )
            }

            val key = pair.elements[0].asString()
            val value = pair.elements[1]

            properties[key] = value
        }

        return UDM.Object(properties)
    }

    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asObject(): UDM.Object? {
        return this as? UDM.Object
    }
    
    private fun UDM.asArray(): UDM.Array? {
        return this as? UDM.Array
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${this::class.simpleName}. " +
            "Hint: Use toString() to convert values to strings."
        )
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
