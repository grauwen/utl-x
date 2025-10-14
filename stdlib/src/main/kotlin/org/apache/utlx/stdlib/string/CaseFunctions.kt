// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/CaseFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM

/**
 * String Case Conversion Functions
 * 
 * Provides DataWeave-compatible case conversion functions:
 * - camelize (camelCase)
 * - kebabCase (kebab-case)
 * - snakeCase (snake_case)
 * - pascalCase (PascalCase)
 * - constantCase (CONSTANT_CASE)
 * 
 * Closes the gap with DataWeave's camelize() function.
 */
object CaseFunctions {
    
    /**
     * Convert string to camelCase
     * 
     * Converts first word to lowercase, capitalizes first letter of subsequent words.
     * Removes spaces, hyphens, and underscores.
     * 
     * Examples:
     * - "hello world" => "helloWorld"
     * - "hello-world" => "helloWorld"
     * - "hello_world" => "helloWorld"
     * - "HelloWorld" => "helloWorld"
     * - "HELLO WORLD" => "helloWorld"
     * 
     * @param str Input string
     * @return Camelized string
     */
    fun camelize(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("camelize() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Split on spaces, hyphens, underscores, or camelCase boundaries
        val words = value
            .replace(Regex("([a-z])([A-Z])"), "$1 $2") // Split camelCase
            .split(Regex("[\\s_-]+"))                   // Split on delimiters
            .filter { it.isNotEmpty() }
        
        if (words.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // First word lowercase, rest title case
        val camelized = words.mapIndexed { index, word ->
            if (index == 0) {
                word.lowercase()
            } else {
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
        }.joinToString("")
        
        return UDM.Scalar(camelized)
    }
    
    /**
     * Convert string to PascalCase (also called UpperCamelCase)
     * 
     * Similar to camelCase but first letter is also capitalized.
     * 
     * Examples:
     * - "hello world" => "HelloWorld"
     * - "hello-world" => "HelloWorld"
     * - "hello_world" => "HelloWorld"
     * 
     * @param str Input string
     * @return Pascal-cased string
     */
    fun pascalCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("pascalCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val words = value
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .split(Regex("[\\s_-]+"))
            .filter { it.isNotEmpty() }
        
        if (words.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // All words title case
        val pascalized = words.joinToString("") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
        
        return UDM.Scalar(pascalized)
    }
    
    /**
     * Convert string to kebab-case
     * 
     * Converts to lowercase with hyphens between words.
     * 
     * Examples:
     * - "hello world" => "hello-world"
     * - "helloWorld" => "hello-world"
     * - "hello_world" => "hello-world"
     * - "HelloWorld" => "hello-world"
     * 
     * @param str Input string
     * @return Kebab-cased string
     */
    fun kebabCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("kebabCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val kebab = value
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")  // Split camelCase
            .replace(Regex("[\\s_]+"), "-")              // Replace spaces/underscores
            .replace(Regex("-+"), "-")                   // Collapse multiple hyphens
            .trim('-')                                    // Remove leading/trailing
            .lowercase()
        
        return UDM.Scalar(kebab)
    }
    
    /**
     * Convert string to snake_case
     * 
     * Converts to lowercase with underscores between words.
     * 
     * Examples:
     * - "hello world" => "hello_world"
     * - "helloWorld" => "hello_world"
     * - "hello-world" => "hello_world"
     * - "HelloWorld" => "hello_world"
     * 
     * @param str Input string
     * @return Snake-cased string
     */
    fun snakeCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("snakeCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val snake = value
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")  // Split camelCase
            .replace(Regex("[\\s-]+"), "_")              // Replace spaces/hyphens
            .replace(Regex("_+"), "_")                   // Collapse multiple underscores
            .trim('_')                                    // Remove leading/trailing
            .lowercase()
        
        return UDM.Scalar(snake)
    }
    
    /**
     * Convert string to CONSTANT_CASE
     * 
     * Converts to uppercase with underscores between words.
     * Commonly used for constants and environment variables.
     * 
     * Examples:
     * - "hello world" => "HELLO_WORLD"
     * - "helloWorld" => "HELLO_WORLD"
     * - "hello-world" => "HELLO_WORLD"
     * 
     * @param str Input string
     * @return Constant-cased string
     */
    fun constantCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("constantCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val constant = value
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("[\\s-]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .uppercase()
        
        return UDM.Scalar(constant)
    }
    
    /**
     * Convert string to Title Case
     * 
     * Capitalizes first letter of each word, rest lowercase.
     * 
     * Examples:
     * - "hello world" => "Hello World"
     * - "HELLO WORLD" => "Hello World"
     * - "hello-world" => "Hello-World"
     * 
     * @param str Input string
     * @return Title-cased string
     */
    fun titleCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("titleCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        // Split on spaces but preserve delimiters
        val titled = value.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { part ->
            if (part.isBlank()) {
                part
            } else {
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
        
        return UDM.Scalar(titled)
    }
    
    /**
     * Convert string to dot.case
     * 
     * Converts to lowercase with dots between words.
     * Useful for object paths and property names.
     * 
     * Examples:
     * - "hello world" => "hello.world"
     * - "helloWorld" => "hello.world"
     * - "hello_world" => "hello.world"
     * 
     * @param str Input string
     * @return Dot-cased string
     */
    fun dotCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("dotCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val dotted = value
            .replace(Regex("([a-z])([A-Z])"), "$1.$2")
            .replace(Regex("[\\s_-]+"), ".")
            .replace(Regex("\\.+"), ".")
            .trim('.')
            .lowercase()
        
        return UDM.Scalar(dotted)
    }
    
    /**
     * Convert string to path/case
     * 
     * Converts to lowercase with slashes between words.
     * Useful for URL paths and file paths.
     * 
     * Examples:
     * - "hello world" => "hello/world"
     * - "helloWorld" => "hello/world"
     * - "hello_world" => "hello/world"
     * 
     * @param str Input string
     * @return Path-cased string
     */
    fun pathCase(str: UDM): UDM {
        val value = (str as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("pathCase() requires a string argument")
        
        if (value.isEmpty()) {
            return UDM.Scalar("")
        }
        
        val path = value
            .replace(Regex("([a-z])([A-Z])"), "$1/$2")
            .replace(Regex("[\\s_-]+"), "/")
            .replace(Regex("/+"), "/")
            .trim('/')
            .lowercase()
        
        return UDM.Scalar(path)
    }
}
