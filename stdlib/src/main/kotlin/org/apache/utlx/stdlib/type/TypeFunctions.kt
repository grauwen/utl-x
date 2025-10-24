// stdlib/src/main/kotlin/org/apache/utlx/stdlib/type/TypeFunctions.kt
package org.apache.utlx.stdlib.type

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

object TypeFunctions {

    @UTLXFunction(
        description = "Returns the type of a value as a string",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "value: The value to check the type of"
        ],
        returns = "String representing the type (string, number, boolean, null, array, object, date, datetime, localdatetime, time, binary, function)",
        example = "getType(42) => \"number\"",
        tags = ["type"],
        since = "1.0"
    )

    fun getType(args: List<UDM>): UDM {
        requireArgs(args, 1, "getType")
        val value = args[0]
        val typeName = when (value) {
            is UDM.Scalar -> when (value.value) {
                is String -> "string"
                is Number -> "number"
                is Boolean -> "boolean"
                null -> "null"
                else -> "unknown"
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Binary -> "binary"
            is UDM.Lambda -> "function"
        }
        return UDM.Scalar(typeName)
    }

    @UTLXFunction(
        description = "Performs isString operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isString(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isString(args: List<UDM>): UDM {
        requireArgs(args, 1, "isString")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is String
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Performs isNumber operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isNumber(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNumber")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is Number
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Performs isBoolean operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isBoolean(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isBoolean(args: List<UDM>): UDM {
        requireArgs(args, 1, "isBoolean")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value is Boolean
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Performs isArray operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isArray(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isArray(args: List<UDM>): UDM {
        requireArgs(args, 1, "isArray")
        return UDM.Scalar(args[0] is UDM.Array)
    }

    @UTLXFunction(
        description = "Performs isObject operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "arg: Arg value"
        ],
        returns = "Boolean indicating the result",
        example = "isObject(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isObject(args: List<UDM>): UDM {
        requireArgs(args, 1, "isObject")
        return UDM.Scalar(args[0] is UDM.Object)
    }

    @UTLXFunction(
        description = "Performs isNull operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "arg: Arg value"
        ],
        returns = "Boolean indicating the result",
        example = "isNull(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isNull(args: List<UDM>): UDM {
        requireArgs(args, 1, "isNull")
        val arg = args[0]
        val result = arg is UDM.Scalar && arg.value == null
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Performs isDefined operation",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "arg: Arg value"
        ],
        returns = "Boolean indicating the result",
        example = "isDefined(...) => result",
        tags = ["type"],
        since = "1.0"
    )
    
    fun isDefined(args: List<UDM>): UDM {
        requireArgs(args, 1, "isDefined")
        val arg = args[0]
        val result = !(arg is UDM.Scalar && arg.value == null)
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Check if value is a date (date-only, no time)",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "value: Value to check"
        ],
        returns = "Boolean indicating if value is a date",
        example = "isDate(parseDate(\"2020-03-15\")) => true",
        tags = ["type", "date"],
        since = "1.0"
    )

    fun isDate(args: List<UDM>): UDM {
        requireArgs(args, 1, "isDate")
        return UDM.Scalar(args[0] is UDM.Date)
    }

    @UTLXFunction(
        description = "Check if value is a datetime (with timezone)",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "value: Value to check"
        ],
        returns = "Boolean indicating if value is a datetime",
        example = "isDateTime(now()) => true",
        tags = ["type", "date"],
        since = "1.0"
    )

    fun isDateTime(args: List<UDM>): UDM {
        requireArgs(args, 1, "isDateTime")
        return UDM.Scalar(args[0] is UDM.DateTime)
    }

    @UTLXFunction(
        description = "Check if value is a local datetime (no timezone)",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "value: Value to check"
        ],
        returns = "Boolean indicating if value is a local datetime",
        example = "isLocalDateTime(parseLocalDateTime(\"2020-03-15T10:30:00\")) => true",
        tags = ["type", "date"],
        since = "1.0"
    )

    fun isLocalDateTime(args: List<UDM>): UDM {
        requireArgs(args, 1, "isLocalDateTime")
        return UDM.Scalar(args[0] is UDM.LocalDateTime)
    }

    @UTLXFunction(
        description = "Check if value is a time (time-only, no date)",
        minArgs = 1,
        maxArgs = 1,
        category = "Type",
        parameters = [
            "value: Value to check"
        ],
        returns = "Boolean indicating if value is a time",
        example = "isTime(parseTime(\"14:30:00\")) => true",
        tags = ["type", "date"],
        since = "1.0"
    )

    fun isTime(args: List<UDM>): UDM {
        requireArgs(args, 1, "isTime")
        return UDM.Scalar(args[0] is UDM.Time)
    }

    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
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
