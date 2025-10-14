// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/CaseConversionFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM

/**
 * Critical missing string case conversion functions
 */
object CaseConversionFunctions {
    
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
    
    /**
     * Convert from camelCase to separate words
     * 
     * Usage: uncamelize("helloWorld") => "hello world"
     * Usage: uncamelize("firstName") => "first name"
     * 
     * Useful for converting programmatic names to human-readable text
     */
    fun uncamelize(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("uncamelize expects 1 argument, got ${args.size}")
        }
        
        val str = args[0]
        if (str !is UDM.Scalar || str.value !is String) {
            throw IllegalArgumentException("uncamelize expects a string argument")
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
        
        if (input.length <= max) {
            return UDM.Scalar(input)
        }
        
        val truncateAt = max - ellipsis.length
        if (truncateAt < 0) {
            return UDM.Scalar(ellipsis.take(max))
        }
        
        val result = input.take(truncateAt) + ellipsis
        
        return UDM.Scalar(result)
    }
    
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
