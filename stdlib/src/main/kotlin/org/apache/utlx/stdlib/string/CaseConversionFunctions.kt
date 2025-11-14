// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/CaseConversionFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Critical missing string case conversion functions
 */
object CaseConversionFunctions {
    
    @UTLXFunction(
        description = "Convert string to camelCase",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "camelize(\"hello-world\") => \"helloWorld\"",
        additionalExamples = [
            "camelize(\"hello_world\") => \"helloWorld\"",
            "camelize(\"hello world\") => \"helloWorld\""
        ],
        notes = """This is the INVERSE of kebabCase/snakeCase
Commonly needed for API field transformations""",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to camelCase
     * 
     * Usage: camelize("hello-world") => "helloWorld"
     * Usage: camelize("hello_world") => "helloWorld"
     * Usage: camelize("hello world") => "helloWorld"
     * 
     * This is the INVERSE of kebabCase/snakeCase
     * Commonly needed for API field transformations
     */
    fun camelize(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("camelize expects 1 argument, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("camelize expects a string argument")
        }
        
        val input = str.value as String
        
        // Handle empty string
        if (input.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Split by common delimiters: space, dash, underscore
        val words = input.split(Regex("[\\s_-]+"))
            .filter { it.isNotEmpty() }
        
        if (words.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // First word lowercase, rest capitalized
        val result = buildString {
            append(words[0].lowercase())
            words.drop(1).forEach { word ->
                append(word.replaceFirstChar { it.uppercase() })
            }
        }
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Convert string to snake_case",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "snakeCase(\"helloWorld\") => \"hello_world\"",
        additionalExamples = [
            "snakeCase(\"HelloWorld\") => \"hello_world\"",
            "snakeCase(\"hello-world\") => \"hello_world\""
        ],
        notes = "Commonly used for database field names",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to snake_case
     * 
     * Usage: snakeCase("helloWorld") => "hello_world"
     * Usage: snakeCase("HelloWorld") => "hello_world"
     * Usage: snakeCase("hello-world") => "hello_world"
     * 
     * Commonly used for database field names
     */
    fun snakeCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("snakeCase expects 1 argument, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("snakeCase expects a string argument")
        }
        
        val input = str.value as String
        
        if (input.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val result = buildString {
            input.forEachIndexed { index, char ->
                when {
                    // Convert uppercase to lowercase with underscore
                    char.isUpperCase() && index > 0 && input[index - 1].isLowerCase() -> {
                        append('_')
                        append(char.lowercase())
                    }
                    // Convert spaces and dashes to underscores
                    char in listOf(' ', '-') -> {
                        if (isEmpty() || last() != '_') {
                            append('_')
                        }
                    }
                    // Keep lowercase and underscores
                    else -> append(char.lowercase())
                }
            }
        }
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Convert string to Title Case",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "titleCase(\"hello world\") => \"Hello World\"",
        additionalExamples = [
            "titleCase(\"hello-world\") => \"Hello World\"",
            "titleCase(\"HELLO WORLD\") => \"Hello World\""
        ],
        notes = "Capitalizes first letter of each word",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to Title Case
     * 
     * Usage: titleCase("hello world") => "Hello World"
     * Usage: titleCase("hello-world") => "Hello World"
     * Usage: titleCase("HELLO WORLD") => "Hello World"
     * 
     * Capitalizes first letter of each word
     */
    fun titleCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("titleCase expects 1 argument, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("titleCase expects a string argument")
        }
        
        val input = str.value as String
        
        if (input.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Split by word boundaries (spaces, dashes, underscores)
        val words = input.split(Regex("[\\s_-]+"))
            .filter { it.isNotEmpty() }
            .map { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        
        val result = words.joinToString(" ")
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Convert from camelCase to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in camelCase format"
        ],
        returns = "Space-separated words",
        example = "fromCamelCase(\"helloWorld\") => \"hello world\"",
        additionalExamples = [
            "fromCamelCase(\"firstName\") => \"first name\"",
            "fromCamelCase(\"XMLHttpRequest\") => \"xml http request\""
        ],
        notes = "Useful for converting programmatic names to human-readable text",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert from camelCase to separate words
     *
     * Usage: fromCamelCase("helloWorld") => "hello world"
     * Usage: fromCamelCase("firstName") => "first name"
     *
     * Useful for converting programmatic names to human-readable text
     */
    fun fromCamelCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromCamelCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromCamelCase expects a string argument")
        }
        
        val input = str.value as String
        
        if (input.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Insert space before uppercase letters
        val result = buildString {
            input.forEachIndexed { index, char ->
                if (char.isUpperCase() && index > 0) {
                    append(' ')
                }
                append(char.lowercase())
            }
        }
        
        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Truncate string with ellipsis",
        minArgs = 3,
        maxArgs = 3,
        category = "String",
        parameters = [
            "str: Str value",
        "maxLength: Maxlength value",
        "ellipsisArg: Ellipsisarg value"
        ],
        returns = "Result of the operation",
        example = "truncate(\"Hello World\", 8) => \"Hello...\"",
        additionalExamples = [
            "truncate(\"Short\", 10) => \"Short\"",
            "truncate(\"Hello World\", 8, \">>\") => \"Hello>>\""
        ],
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Truncate string with ellipsis
     * 
     * Usage: truncate("Hello World", 8) => "Hello..."
     * Usage: truncate("Short", 10) => "Short"
     * Usage: truncate("Hello World", 8, ">>") => "Hello>>"
     * 
     * @param str - String to truncate
     * @param maxLength - Maximum length (including ellipsis)
     * @param ellipsis - Ellipsis string (default: "...")
     */
    fun truncate(args: List<UDM>): UDM {
        if (args.size !in 2..3) {
            throw IllegalArgumentException("truncate expects 2-3 arguments, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("truncate expects string as first argument")
        }
        
        val maxLength = args[1]
        if (maxLength !is UDM.Scalar || maxLength.value !is Number) {
            throw IllegalArgumentException("truncate expects number as second argument")
        }
        
        val ellipsis = if (args.size == 3) {
            val ellipsisArg = args[2]
            if (ellipsisArg !is UDM.Scalar || ellipsisArg.value !is String) {
                throw IllegalArgumentException("truncate expects string as third argument")
            }
            ellipsisArg.value as String
        } else {
            "..."
        }
        
        val input = str.value as String
        val max = (maxLength.value as Number).toInt()

        // Handle zero max length
        if (max == 0) {
            return UDM.Scalar("")
        }

        // Handle negative max length - return ellipsis
        if (max < 0) {
            return UDM.Scalar(ellipsis)
        }

        if (input.length <= max) {
            return UDM.Scalar(input)
        }

        val truncateAt = max - ellipsis.length
        if (truncateAt <= 0) {
            return UDM.Scalar(ellipsis.take(max))
        }

        // Trim trailing whitespace from truncated part before adding ellipsis
        val result = input.take(truncateAt).trimEnd() + ellipsis

        return UDM.Scalar(result)
    }
    
    @UTLXFunction(
        description = "Convert string to URL-safe slug",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: Str value"
        ],
        returns = "Result of the operation",
        example = "slugify(\"Hello World!\") => \"hello-world\"",
        additionalExamples = [
            "slugify(\"  Foo & Bar  \") => \"foo-and-bar\"",
            "slugify(\"C++ Programming\") => \"c-programming\""
        ],
        notes = "Converts to lowercase, replaces spaces with dashes, removes special chars",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Convert string to URL-safe slug
     * 
     * Usage: slugify("Hello World!") => "hello-world"
     * Usage: slugify("  Foo & Bar  ") => "foo-and-bar"
     * Usage: slugify("C++ Programming") => "c-programming"
     * 
     * Converts to lowercase, replaces spaces with dashes, removes special chars
     */
    fun slugify(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("slugify expects 1 argument, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("slugify expects a string argument")
        }
        
        val input = str.value as String
        
        if (input.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Common word replacements
        val replacements = mapOf(
            "&" to "and",
            "+" to "plus",
            "@" to "at"
        )
        
        var result = input.lowercase().trim()
        
        // Replace common symbols with words
        replacements.forEach { (symbol, word) ->
            result = result.replace(symbol, " $word ")
        }
        
        // Remove all non-alphanumeric except spaces and dashes
        result = result.replace(Regex("[^a-z0-9\\s-]"), "")
        
        // Replace multiple spaces/dashes with single dash
        result = result.replace(Regex("[\\s-]+"), "-")
        
        // Remove leading/trailing dashes
        result = result.trim('-')
        
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Convert from PascalCase to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in PascalCase format"
        ],
        returns = "Space-separated words",
        example = "fromPascalCase(\"HelloWorld\") => \"hello world\"",
        additionalExamples = [
            "fromPascalCase(\"FirstName\") => \"first name\"",
            "fromPascalCase(\"XMLHttpRequest\") => \"xml http request\""
        ],
        notes = "Useful for converting class names to human-readable text",
        tags = ["string"],
        since = "1.0"
    )
    fun fromPascalCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromPascalCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromPascalCase expects a string argument")
        }

        val input = str.value as String

        if (input.isEmpty()) {
            return UDM.Scalar("")
        }

        // Same logic as fromCamelCase
        val result = buildString {
            input.forEachIndexed { index, char ->
                if (char.isUpperCase() && index > 0) {
                    append(' ')
                }
                append(char.lowercase())
            }
        }

        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Convert from kebab-case to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in kebab-case format"
        ],
        returns = "Space-separated words",
        example = "fromKebabCase(\"hello-world\") => \"hello world\"",
        additionalExamples = [
            "fromKebabCase(\"first-name\") => \"first name\"",
            "fromKebabCase(\"my-cool-app\") => \"my cool app\""
        ],
        notes = "Converts kebab-case to space-separated words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromKebabCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromKebabCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromKebabCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.replace('-', ' '))
    }

    @UTLXFunction(
        description = "Convert from snake_case to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in snake_case format"
        ],
        returns = "Space-separated words",
        example = "fromSnakeCase(\"hello_world\") => \"hello world\"",
        additionalExamples = [
            "fromSnakeCase(\"first_name\") => \"first name\"",
            "fromSnakeCase(\"my_cool_app\") => \"my cool app\""
        ],
        notes = "Converts snake_case to space-separated words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromSnakeCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromSnakeCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromSnakeCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.replace('_', ' '))
    }

    @UTLXFunction(
        description = "Convert from CONSTANT_CASE to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in CONSTANT_CASE format"
        ],
        returns = "Space-separated lowercase words",
        example = "fromConstantCase(\"HELLO_WORLD\") => \"hello world\"",
        additionalExamples = [
            "fromConstantCase(\"FIRST_NAME\") => \"first name\"",
            "fromConstantCase(\"MY_COOL_APP\") => \"my cool app\""
        ],
        notes = "Converts CONSTANT_CASE to space-separated lowercase words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromConstantCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromConstantCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromConstantCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.replace('_', ' ').lowercase())
    }

    @UTLXFunction(
        description = "Convert from Title Case to separate lowercase words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in Title Case format"
        ],
        returns = "Space-separated lowercase words",
        example = "fromTitleCase(\"Hello World\") => \"hello world\"",
        additionalExamples = [
            "fromTitleCase(\"First Name\") => \"first name\"",
            "fromTitleCase(\"My Cool App\") => \"my cool app\""
        ],
        notes = "Converts Title Case to space-separated lowercase words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromTitleCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromTitleCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromTitleCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.lowercase())
    }

    @UTLXFunction(
        description = "Convert from dot.case to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in dot.case format"
        ],
        returns = "Space-separated words",
        example = "fromDotCase(\"hello.world\") => \"hello world\"",
        additionalExamples = [
            "fromDotCase(\"first.name\") => \"first name\"",
            "fromDotCase(\"my.cool.app\") => \"my cool app\""
        ],
        notes = "Converts dot.case to space-separated words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromDotCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromDotCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromDotCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.replace('.', ' '))
    }

    @UTLXFunction(
        description = "Convert from path/case to separate words",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String in path/case format"
        ],
        returns = "Space-separated words",
        example = "fromPathCase(\"hello/world\") => \"hello world\"",
        additionalExamples = [
            "fromPathCase(\"first/name\") => \"first name\"",
            "fromPathCase(\"my/cool/app\") => \"my cool app\""
        ],
        notes = "Converts path/case to space-separated words",
        tags = ["string"],
        since = "1.0"
    )
    fun fromPathCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("fromPathCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("fromPathCase expects a string argument")
        }

        val input = str.value as String
        return UDM.Scalar(input.replace('/', ' '))
    }

    @UTLXFunction(
        description = "Convert string to word case (capitalize first letter, rest lowercase)",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "str: String to convert"
        ],
        returns = "Word-cased string",
        example = "wordCase(\"HELLO\") => \"Hello\"",
        additionalExamples = [
            "wordCase(\"hello\") => \"Hello\"",
            "wordCase(\"hELLo\") => \"Hello\""
        ],
        notes = "Capitalizes first letter of string, lowercases the rest",
        tags = ["string"],
        since = "1.0"
    )
    fun wordCase(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("wordCase expects 1 argument, got ${args.size}")
        }

        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("wordCase expects a string argument")
        }

        val input = str.value as String

        if (input.isEmpty()) {
            return UDM.Scalar("")
        }

        return UDM.Scalar(input.lowercase().replaceFirstChar { it.uppercase() })
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to the registerStringFunctions() method:
 * 
 * // Case conversion (critical)
 * register("camelize", CaseConversionFunctions::camelize)
 * register("snake-case", CaseConversionFunctions::snakeCase)
 * register("title-case", CaseConversionFunctions::titleCase)
 * register("uncamelize", CaseConversionFunctions::uncamelize)
 * 
 * // Utilities
 * register("truncate", CaseConversionFunctions::truncate)
 * register("slugify", CaseConversionFunctions::slugify)
 */
