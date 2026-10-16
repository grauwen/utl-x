package com.glomidco.utlx.stdlib.regional

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

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

    @UTLXFunction(
        description = "Parse number in Nordic/SI format (space thousands, comma decimal). Tolerant of regular (U+0020), no-break (U+00A0) and narrow-no-break (U+202F) spaces. Example: '1 234,56' → 1234.56",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseNordicNumber(\"1 234,56\") => 1234.56",
        tags = ["regional", "numbers", "parsing", "nordic", "scandinavian", "si"],
        since = "1.0"
    )
    fun parseNordicNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseNordicNumber")
        val value = args[0].asString()

        try {
            // Nordic/SI format: space as thousands separator, comma as decimal.
            // Accept every space variant seen in the wild (regular/NBSP/narrow-NBSP).
            val cleaned = value.trim()
                .replace(" ", "")       // Remove regular space (U+0020)
                .replace("\u00A0", "")  // Remove non-breaking space (U+00A0)
                .replace("\u202F", "")  // Remove narrow no-break space (U+202F)
                .replace(",", ".")      // Convert comma decimal to period decimal

            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseNordicNumber: Cannot parse '$value' as Nordic format number. Expected format: '1 234,56' (space thousands, comma decimal). Hint: Make sure the input uses space as thousands separator and comma as decimal separator"
            )
        }
    }

    @UTLXFunction(
        description = "Format number in Nordic/SI style (regular-space thousands, comma decimal). Example: 1234.56 → \"1 234,56\". Used by Sweden, Norway, Finland and the SI/ISO international style.",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in Nordic (regular-space) format",
        example = "renderNordicNumber(1234.56, 2, true) => \"1 234,56\"",
        tags = ["regional", "numbers", "formatting", "output", "nordic", "scandinavian", "si"],
        since = "1.0"
    )
    fun renderNordicNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderNordicNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderNordicNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderNordicNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderNordicNumber: First argument must be a number")
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

        val formatted = formatNordicNumber(value, decimals, useThousands)
        return UDM.Scalar(formatted)
    }

    @UTLXFunction(
        description = "Parse number in Indian format (lakh/crore grouping, comma thousands, period decimal). Example: '12,34,567.89' → 1234567.89",
        minArgs = 1,
        maxArgs = 1,
        category = "Regional",
        returns = "Number - The parsed numeric value with standard decimal representation",
        example = "parseIndianNumber(\"12,34,567.89\") => 1234567.89",
        tags = ["regional", "numbers", "parsing", "indian", "lakh", "crore"],
        since = "1.0"
    )
    fun parseIndianNumber(args: List<UDM>): UDM {
        requireArgs(args, 1, "parseIndianNumber")
        val value = args[0].asString()

        try {
            // Indian format: comma as (irregular lakh/crore) thousands separator, period as decimal.
            // Grouping position is irrelevant to parsing — just strip the commas.
            val cleaned = value.trim().replace(",", "")
            val number = if (cleaned.contains(".")) {
                cleaned.toDouble()
            } else {
                cleaned.toLongOrNull()?.toDouble() ?: cleaned.toDouble()
            }
            return UDM.Scalar(number)
        } catch (e: NumberFormatException) {
            throw FunctionArgumentException(
                "parseIndianNumber: Cannot parse '$value' as Indian format number. Expected format: '12,34,567.89' (lakh/crore comma grouping, period decimal). Hint: Make sure the input uses comma as thousands separator and period as decimal separator"
            )
        }
    }

    @UTLXFunction(
        description = "Format number in Indian style (lakh/crore 2-2-3 grouping, comma thousands, period decimal). Example: 1234567.89 → \"12,34,567.89\"",
        minArgs = 1,
        maxArgs = 3,
        category = "Regional",
        returns = "String - The formatted number string in Indian (lakh/crore) format",
        example = "renderIndianNumber(1234567.89, 2, true) => \"12,34,567.89\"",
        tags = ["regional", "numbers", "formatting", "output", "indian", "lakh", "crore"],
        since = "1.0"
    )
    fun renderIndianNumber(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "renderIndianNumber expects 1-3 arguments (value, decimals?, thousands?), got ${args.size}. Hint: Check the function call syntax"
            )
        }

        val value = when (val udm = args[0]) {
            is UDM.Scalar -> when (val v = udm.value) {
                is Number -> v.toDouble()
                is String -> v.toDoubleOrNull() ?: throw FunctionArgumentException(
                    "renderIndianNumber: Cannot convert '$v' to number"
                )
                else -> throw FunctionArgumentException("renderIndianNumber: First argument must be a number")
            }
            else -> throw FunctionArgumentException("renderIndianNumber: First argument must be a number")
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

        val formatted = formatIndianNumber(value, decimals, useThousands)
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

        // Locale-independent (like formatSwissNumber): format with the always-available
        // US locale, then swap separators -> period thousands, comma decimal.
        // Avoids depending on Locale.GERMANY, which is absent in the GraalVM native image
        // (DecimalFormatSymbols.getInstance would silently fall back to US there).
        val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        val usFormatted = formatter.format(value) // e.g. "1,234.56"
        return buildString(usFormatted.length) {
            for (c in usFormatted) append(when (c) { ',' -> '.'; '.' -> ','; else -> c })
        }
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

        // Locale-independent (like formatSwissNumber): format with the always-available
        // US locale, then swap separators -> narrow-no-break-space thousands, comma decimal.
        // Avoids depending on Locale.FRANCE, which is absent in the GraalVM native image.
        // The thousands separator is U+202F (NARROW NO-BREAK SPACE) to exactly reproduce
        // what DecimalFormatSymbols.getInstance(Locale.FRANCE) emitted on the JVM (modern
        // CLDR French data), which is what the conformance fixtures encode.
        val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        val usFormatted = formatter.format(value) // e.g. "1,234.56"
        return buildString(usFormatted.length) {
            for (c in usFormatted) append(when (c) { ',' -> '\u202F'; '.' -> ','; else -> c })
        }
    }

    /**
     * Format number in Nordic/SI style: regular-space thousands, comma decimal
     * Example: 1234.56 -> "1 234,56"
     */
    private fun formatNordicNumber(value: Double, decimals: Int, useThousands: Boolean): String {
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

        // Locale-independent (like formatSwissNumber): format with the always-available
        // US locale, then swap separators -> regular ASCII space (U+0020) thousands, comma decimal.
        // Unlike formatFrenchNumber (which uses U+202F to match CLDR French), this emits a plain
        // space for the Nordic / SI international style (Sweden, Norway, Finland).
        val formatter = java.text.DecimalFormat(pattern, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US))
        val usFormatted = formatter.format(value) // e.g. "1,234.56"
        return buildString(usFormatted.length) {
            for (c in usFormatted) append(when (c) { ',' -> ' '; '.' -> ','; else -> c })
        }
    }

    /**
     * Format number in Indian style: lakh/crore grouping (rightmost group of 3, then groups of 2),
     * comma thousands, period decimal. Example: 1234567.89 -> "12,34,567.89"
     *
     * java.text.DecimalFormat only supports a single (rightmost) grouping size, so the Indian
     * 2-2-3 grouping cannot be expressed as a pattern — it is applied manually here. This keeps the
     * function locale-data-free (US symbols only) and therefore native-image-safe (see B33).
     */
    private fun formatIndianNumber(value: Double, decimals: Int, useThousands: Boolean): String {
        val decimalFormat = if (decimals > 0) {
            "0.${"0".repeat(decimals)}"
        } else {
            "0"
        }

        // Format WITHOUT grouping first (US symbols -> period decimal, 0-9 digits).
        val plain = java.text.DecimalFormat(decimalFormat, java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US)).format(value)
        if (!useThousands) return plain

        val negative = plain.startsWith("-")
        val unsigned = if (negative) plain.substring(1) else plain
        val dotIndex = unsigned.indexOf('.')
        val intPart = if (dotIndex >= 0) unsigned.substring(0, dotIndex) else unsigned
        val fracPart = if (dotIndex >= 0) unsigned.substring(dotIndex) else "" // includes the '.'

        return (if (negative) "-" else "") + groupIndian(intPart) + fracPart
    }

    /**
     * Apply Indian digit grouping to a plain (ungrouped) integer string: the rightmost 3 digits
     * form the first group, then every 2 digits. "1234567" -> "12,34,567".
     */
    private fun groupIndian(intPart: String): String {
        if (intPart.length <= 3) return intPart
        val last3 = intPart.substring(intPart.length - 3)
        val rest = intPart.substring(0, intPart.length - 3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ",")
            sb.insert(0, rest.substring(start, i))
            i = start
        }
        return "$sb,$last3"
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
