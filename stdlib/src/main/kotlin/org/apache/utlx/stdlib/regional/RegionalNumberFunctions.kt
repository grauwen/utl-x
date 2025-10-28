package org.apache.utlx.stdlib.regional

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Regional Number Parsing Functions
 *
 * These functions parse numbers formatted according to regional conventions
 * where decimal and thousands separators differ by locale.
 *
 * All functions convert to standard numeric representation (period as decimal separator)
 * for internal use in UDM (Universal Data Model).
 */
object RegionalNumberFunctions {

    @UTLXFunction(
        description = "Parse number in USA/UK/Asia format (comma thousands, period decimal). Example: '1,234.56' → 1234.56",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseUSNumber(\"1,234.56\") => 1234.56",
        tags = ["regional", "numbers", "parsing"],
        since = "1.0"
    )
    fun parseUSNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseUSNumber")
        val value = args[0].asString()

        try {
            // USA/UK/Asia format: comma as thousands separator, period as decimal
            // "1,234.56" → 1234.56
            val cleaned = value.trim().replace(",", "")
            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseUSNumber: Cannot parse '$value' as USA/UK/Asia format number. Expected format: '1,234.56' (comma thousands, period decimal). Hint: Make sure the input uses comma as thousands separator and period as decimal separator"
            )
        }
    }

    @UTLXFunction(
        description = "Parse number in European/Latin American format (period thousands, comma decimal). Example: '1.234,56' → 1234.56",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseEUNumber(\"1.234,56\") => 1234.56",
        tags = ["regional", "numbers", "parsing", "european"],
        since = "1.0"
    )
    fun parseEUNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseEUNumber")
        val value = args[0].asString()

        try {
            // European/Latin American format: period as thousands separator, comma as decimal
            // "1.234,56" → 1234.56
            val cleaned = value.trim()
                .replace(".", "")      // Remove thousands separator
                .replace(",", ".")     // Convert comma decimal to period decimal

            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseEUNumber: Cannot parse '$value' as European format number. Expected format: '1.234,56' (period thousands, comma decimal). Hint: Make sure the input uses period as thousands separator and comma as decimal separator"
            )
        }
    }

    @UTLXFunction(
        description = "Parse number in Swiss format (apostrophe thousands, period decimal). Example: \"1'234.56\" → 1234.56",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseSwissNumber(\"1'234.56\") => 1234.56",
        tags = ["regional", "numbers", "parsing", "swiss"],
        since = "1.0"
    )
    fun parseSwissNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseSwissNumber")
        val value = args[0].asString()

        try {
            // Swiss format: apostrophe as thousands separator, period as decimal
            // "1'234.56" → 1234.56
            val cleaned = value.trim().replace("'", "")
            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseSwissNumber: Cannot parse '$value' as Swiss format number. Expected format: \"1'234.56\" (apostrophe thousands, period decimal). Hint: Make sure the input uses apostrophe as thousands separator and period as decimal separator"
            )
        }
    }

    /**
     * Helper function to validate argument count
     */
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. Hint: Check the function call syntax"
            )
        }
    }
}
