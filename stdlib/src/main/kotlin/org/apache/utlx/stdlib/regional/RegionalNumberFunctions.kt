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

    @UTLXFunction(
        description = "Format number as USA/UK/Asia string (comma thousands, period decimal). Example: 1234.56 → '1,234.56'",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in USA/UK/Asia format",
        example = "renderUSNumber(1234.56, 2, true) => \"1,234.56\"",
        tags = ["regional", "numbers", "formatting", "output"],
        since = "1.0"
    )
    fun renderUSNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderUSNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderUSNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderUSNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderUSNumber: First argument must be a number")
        }

        val decimals = if (args.size >= 2) {
            args[1].asNumber().toInt()
        } else {
            2
        }

        val useThousands = if (args.size >= 3) {
            args[2].asBoolean()
        } else {
            true
        }

        val formatted = formatUSNumber(value, decimals, useThousands)
        return UDM.Scalar(formatted)
    }

    @UTLXFunction(
        description = "Format number as European/Latin American string (period thousands, comma decimal). Example: 1234.56 → '1.234,56'",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in European format",
        example = "renderEUNumber(1234.56, 2, true) => \"1.234,56\"",
        tags = ["regional", "numbers", "formatting", "output", "european"],
        since = "1.0"
    )
    fun renderEUNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderEUNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderEUNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderEUNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderEUNumber: First argument must be a number")
        }

        val decimals = if (args.size >= 2) {
            args[1].asNumber().toInt()
        } else {
            2
        }

        val useThousands = if (args.size >= 3) {
            args[2].asBoolean()
        } else {
            true
        }

        val formatted = formatEUNumber(value, decimals, useThousands)
        return UDM.Scalar(formatted)
    }

    @UTLXFunction(
        description = "Parse number in French/Belgian/Quebec format (space thousands, comma decimal). Example: '1 234,56' → 1234.56",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseFrenchNumber(\"1 234,56\") => 1234.56",
        tags = ["regional", "numbers", "parsing", "french"],
        since = "1.0"
    )
    fun parseFrenchNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseFrenchNumber")
        val value = args[0].asString()

        try {
            // French/Belgian/Quebec format: space as thousands separator, comma as decimal
            // "1 234,56" → 1234.56
            val cleaned = value.trim()
                .replace(" ", "")       // Remove regular space
                .replace("\u00A0", "")  // Remove non-breaking space (U+00A0)
                .replace("\u202F", "")  // Remove narrow no-break space (U+202F) - used by French locale
                .replace(",", ".")      // Convert comma decimal to period decimal

            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseFrenchNumber: Cannot parse '$value' as French format number. Expected format: '1 234,56' (space thousands, comma decimal). Hint: Make sure the input uses space as thousands separator and comma as decimal separator"
            )
        }
    }

    @UTLXFunction(
        description = "Format number as Swiss string (apostrophe thousands, period decimal). Example: 1234.56 → \"1'234.56\"",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in Swiss format",
        example = "renderSwissNumber(1234.56, 2, true) => \"1'234.56\"",
        tags = ["regional", "numbers", "formatting", "output", "swiss"],
        since = "1.0"
    )
    fun renderSwissNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderSwissNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderSwissNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderSwissNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderSwissNumber: First argument must be a number")
        }

        val decimals = if (args.size >= 2) {
            args[1].asNumber().toInt()
        } else {
            2
        }

        val useThousands = if (args.size >= 3) {
            args[2].asBoolean()
        } else {
            true
        }

        val formatted = formatSwissNumber(value, decimals, useThousands)
        return UDM.Scalar(formatted)
    }

    @UTLXFunction(
        description = "Format number as French/Belgian/Quebec string (space thousands, comma decimal). Example: 1234.56 → '1 234,56'",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in French format",
        example = "renderFrenchNumber(1234.56, 2, true) => \"1 234,56\"",
        tags = ["regional", "numbers", "formatting", "output", "french"],
        since = "1.0"
    )
    fun renderFrenchNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderFrenchNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderFrenchNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderFrenchNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderFrenchNumber: First argument must be a number")
        }

        val decimals = if (args.size >= 2) {
            args[1].asNumber().toInt()
        } else {
            2
        }

        val useThousands = if (args.size >= 3) {
            args[2].asBoolean()
        } else {
            true
        }

        val formatted = formatFrenchNumber(value, decimals, useThousands)
        return UDM.Scalar(formatted)
    }

    /**
     * Format number in USA/UK/Asia style: comma thousands, period decimal
     * Example: 1234.56 -> "1,234.56"
     */
    private fun formatUSNumber(value: Double, decimals: Int, useThousands: Boolean): String {
        val decimalFormat = if (decimals > 0) {
            "0.${"0".repeat(decimals)}"
        } else {
            "0"
        }

        val pattern = if (useThousands) {
            "#,##$decimalFormat"
        } else {
            "#$decimalFormat"
        }

        val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        return formatter.format(value)
    }

    /**
     * Format number in European style: period thousands, comma decimal
     * Example: 1234.56 -> "1.234,56"
     */
    private fun formatEUNumber(value: Double, decimals: Int, useThousands: Boolean): String {
        val decimalFormat = if (decimals > 0) {
            "0.${"0".repeat(decimals)}"
        } else {
            "0"
        }

        val pattern = if (useThousands) {
            "#,##$decimalFormat"
        } else {
            "#$decimalFormat"
        }

        // Use German locale which has period as thousands separator and comma as decimal
        val symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.GERMANY)
        val formatter = java.text.DecimalFormat(pattern, symbols)
        return formatter.format(value)
    }

    /**
     * Format number in Swiss style: apostrophe thousands, period decimal
     * Example: 1234.56 -> "1'234.56"
     */
    private fun formatSwissNumber(value: Double, decimals: Int, useThousands: Boolean): String {
        val decimalFormat = if (decimals > 0) {
            "0.${"0".repeat(decimals)}"
        } else {
            "0"
        }

        val pattern = if (useThousands) {
            "#,##$decimalFormat"
        } else {
            "#$decimalFormat"
        }

        // Use US locale as base and then replace comma with apostrophe
        val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        val usFormatted = formatter.format(value)
        return usFormatted.replace(',', '\'')
    }

    /**
     * Format number in French style: space thousands, comma decimal
     * Example: 1234.56 -> "1 234,56"
     */
    private fun formatFrenchNumber(value: Double, decimals: Int, useThousands: Boolean): String {
        val decimalFormat = if (decimals > 0) {
            "0.${"0".repeat(decimals)}"
        } else {
            "0"
        }

        val pattern = if (useThousands) {
            "#,##$decimalFormat"
        } else {
            "#$decimalFormat"
        }

        // Use French locale which has space as thousands separator and comma as decimal
        val symbols = java.text.DecimalFormatSymbols.getInstance(java.util.Locale.FRANCE)
        val formatter = java.text.DecimalFormat(pattern, symbols)
        return formatter.format(value)
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
