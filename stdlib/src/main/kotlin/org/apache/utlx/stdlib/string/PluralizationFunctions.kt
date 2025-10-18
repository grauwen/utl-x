// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/PluralizationFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.*
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * English pluralization functions for UTL-X
 * 
 * Provides intelligent English language pluralization with support
 * for regular and irregular forms.
 * 
 * @since 1.0.0
 */
object PluralizationFunctions {
    
    // ============================================
    // IRREGULAR PLURALS
    // ============================================
    
    private val irregularPlurals = mapOf(
        // Common irregular nouns
        "man" to "men",
        "woman" to "women",
        "child" to "children",
        "tooth" to "teeth",
        "foot" to "feet",
        "person" to "people",
        "leaf" to "leaves",
        "mouse" to "mice",
        "goose" to "geese",
        "half" to "halves",
        "knife" to "knives",
        "wife" to "wives",
        "life" to "lives",
        "elf" to "elves",
        "loaf" to "loaves",
        "potato" to "potatoes",
        "tomato" to "tomatoes",
        "cactus" to "cacti",
        "focus" to "foci",
        "fungus" to "fungi",
        "nucleus" to "nuclei",
        "syllabus" to "syllabi",
        "analysis" to "analyses",
        "diagnosis" to "diagnoses",
        "oasis" to "oases",
        "thesis" to "theses",
        "crisis" to "crises",
        "phenomenon" to "phenomena",
        "criterion" to "criteria",
        "datum" to "data",
        "ox" to "oxen",
        "axis" to "axes",
        "testis" to "testes",
        "index" to "indices",
        "matrix" to "matrices",
        "vertex" to "vertices",
        "appendix" to "appendices",
        
        // Unchanged plurals
        "sheep" to "sheep",
        "series" to "series",
        "species" to "species",
        "deer" to "deer",
        "fish" to "fish",
        "moose" to "moose",
        "offspring" to "offspring",
        "salmon" to "salmon",
        "trout" to "trout",
        "aircraft" to "aircraft",
        "spacecraft" to "spacecraft",
        "headquarters" to "headquarters",
        "means" to "means",
        "scissors" to "scissors",
        "pants" to "pants",
        "glasses" to "glasses"
    )
    
    private val irregularSingulars = irregularPlurals.entries
        .associate { (k, v) -> v to k }
    
    // ============================================
    // UNCOUNTABLE NOUNS
    // ============================================
    
    private val uncountableNouns = setOf(
        "equipment", "information", "rice", "money", "species", "series",
        "fish", "sheep", "moose", "deer", "news", "music", "furniture",
        "luggage", "baggage", "homework", "software", "hardware", "advice",
        "knowledge", "research", "evidence", "progress", "traffic", "weather",
        "work", "data", "staff", "police", "cattle", "clothing", "jewelry"
    )
    
    // ============================================
    // PLURALIZATION RULES
    // ============================================
    
    @UTLXFunction(
        description = "Converts a singular noun to its plural form",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "word: Word value"
        ],
        returns = "Result of the operation",
        example = "pluralize(...) => result",
        notes = "Handles regular pluralization rules and irregular forms in English.\nExamples:\n```\npluralize(\"cat\") // \"cats\"\npluralize(\"dog\") // \"dogs\"\npluralize(\"child\") // \"children\" (irregular)\npluralize(\"sheep\") // \"sheep\" (unchanged)\npluralize(\"box\") // \"boxes\"\npluralize(\"city\") // \"cities\"\npluralize(\"person\", 1) // \"person\" (count = 1)\npluralize(\"person\", 5) // \"people\" (count > 1)\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Converts a singular noun to its plural form
     * 
     * Handles regular pluralization rules and irregular forms in English.
     * 
     * @param word The singular noun
     * @param count Optional count for conditional pluralization
     * @return Plural form of the noun
     * 
     * Examples:
     * ```
     * pluralize("cat") // "cats"
     * pluralize("dog") // "dogs"
     * pluralize("child") // "children" (irregular)
     * pluralize("sheep") // "sheep" (unchanged)
     * pluralize("box") // "boxes"
     * pluralize("city") // "cities"
     * pluralize("person", 1) // "person" (count = 1)
     * pluralize("person", 5) // "people" (count > 1)
     * ```
     */
    fun pluralize(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("pluralize expects at least 1 argument")
        }
        
        val word = args[0]
        val count = if (args.size > 1) args[1] else UDM.Scalar.nullValue()
        
        val singular = (word as? UDM.Scalar)?.value?.toString()?.trim()?.lowercase() 
            ?: return UDM.Scalar.nullValue()
        
        // Check if count is provided and equals 1
        if (count is UDM.Scalar && count.value == 1.0) {
            return word // Return singular form
        }
        
        // Handle empty string
        if (singular.isEmpty()) return UDM.Scalar("")
        
        // Check for uncountable nouns
        if (uncountableNouns.contains(singular)) {
            return word // Return unchanged
        }
        
        // Check for irregular plurals
        irregularPlurals[singular]?.let {
            return UDM.Scalar(matchCase(singular, it, (word as UDM.Scalar).value?.toString() ?: ""))
        }
        
        // Apply regular pluralization rules
        val plural = when {
            // Words ending in s, x, z, ch, sh: add "es"
            singular.endsWith("s") || 
            singular.endsWith("x") || 
            singular.endsWith("z") ||
            singular.endsWith("ch") ||
            singular.endsWith("sh") -> singular + "es"
            
            // Words ending in consonant + y: change y to ies
            singular.length >= 2 &&
            singular.endsWith("y") &&
            !isVowel(singular[singular.length - 2]) -> {
                singular.dropLast(1) + "ies"
            }
            
            // Words ending in vowel + y: add s
            singular.endsWith("y") -> singular + "s"
            
            // Words ending in f or fe: change to ves
            singular.endsWith("f") -> singular.dropLast(1) + "ves"
            singular.endsWith("fe") -> singular.dropLast(2) + "ves"
            
            // Words ending in consonant + o: add es
            singular.length >= 2 &&
            singular.endsWith("o") &&
            !isVowel(singular[singular.length - 2]) -> singular + "es"
            
            // Words ending in vowel + o: add s
            singular.endsWith("o") -> singular + "s"
            
            // Default: add s
            else -> singular + "s"
        }
        
        // Match the case of the original word
        return UDM.Scalar(matchCase(singular, plural, (word as UDM.Scalar).value?.toString() ?: ""))
    }
    
    @UTLXFunction(
        description = "Converts a plural noun to its singular form",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "word: Word value"
        ],
        returns = "Result of the operation",
        example = "singularize(...) => result",
        notes = "Handles regular singularization rules and irregular forms.\nExamples:\n```\nsingularize(\"cats\") // \"cat\"\nsingularize(\"dogs\") // \"dog\"\nsingularize(\"children\") // \"child\" (irregular)\nsingularize(\"sheep\") // \"sheep\" (unchanged)\nsingularize(\"boxes\") // \"box\"\nsingularize(\"cities\") // \"city\"\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Converts a plural noun to its singular form
     * 
     * Handles regular singularization rules and irregular forms.
     * 
     * @param word The plural noun
     * @return Singular form of the noun
     * 
     * Examples:
     * ```
     * singularize("cats") // "cat"
     * singularize("dogs") // "dog"
     * singularize("children") // "child" (irregular)
     * singularize("sheep") // "sheep" (unchanged)
     * singularize("boxes") // "box"
     * singularize("cities") // "city"
     * ```
     */
    fun singularize(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("singularize expects 1 argument")
        }
        
        val word = args[0]
        val plural = (word as? UDM.Scalar)?.value?.toString()?.trim()?.lowercase() 
            ?: return UDM.Scalar.nullValue()
        
        // Handle empty string
        if (plural.isEmpty()) return UDM.Scalar("")
        
        // Check for uncountable nouns
        if (uncountableNouns.contains(plural)) {
            return word // Return unchanged
        }
        
        // Check for irregular singulars
        irregularSingulars[plural]?.let {
            return UDM.Scalar(matchCase(plural, it, (word as UDM.Scalar).value?.toString() ?: ""))
        }
        
        // Apply regular singularization rules
        val singular = when {
            // Words ending in ies: change to y
            plural.endsWith("ies") && plural.length > 3 -> {
                plural.dropLast(3) + "y"
            }
            
            // Words ending in ves: change to f or fe
            plural.endsWith("ves") && plural.length > 3 -> {
                // Try both f and fe (prefer f)
                plural.dropLast(3) + "f"
            }
            
            // Words ending in ses: remove es
            plural.endsWith("ses") && plural.length > 3 -> {
                plural.dropLast(2)
            }
            
            // Words ending in xes: remove es
            plural.endsWith("xes") && plural.length > 3 -> {
                plural.dropLast(2)
            }
            
            // Words ending in zes: remove s
            plural.endsWith("zes") && plural.length > 3 -> {
                plural.dropLast(1)
            }
            
            // Words ending in ches: remove es
            plural.endsWith("ches") && plural.length > 4 -> {
                plural.dropLast(2)
            }
            
            // Words ending in shes: remove es
            plural.endsWith("shes") && plural.length > 4 -> {
                plural.dropLast(2)
            }
            
            // Words ending in oes: remove s
            plural.endsWith("oes") && plural.length > 3 -> {
                plural.dropLast(1)
            }
            
            // Words ending in s: remove s
            plural.endsWith("s") && plural.length > 1 -> {
                plural.dropLast(1)
            }
            
            // Default: return as-is
            else -> plural
        }
        
        // Match the case of the original word
        return UDM.Scalar(matchCase(plural, singular, (word as UDM.Scalar).value?.toString() ?: ""))
    }
    
    @UTLXFunction(
        description = "Examples:",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "word: Word value",
        "count: Count value"
        ],
        returns = "the appropriate form (singular or plural) based on count",
        example = "pluralizeWithCount(...) => result",
        notes = "Returns the appropriate form (singular or plural) based on count\n```\npluralizeWithCount(\"cat\", 0) // \"cats\"\npluralizeWithCount(\"cat\", 1) // \"cat\"\npluralizeWithCount(\"cat\", 5) // \"cats\"\npluralizeWithCount(\"child\", 3) // \"children\"\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Returns the appropriate form (singular or plural) based on count
     * 
     * @param word The base noun
     * @param count The count
     * @return Singular if count is 1, plural otherwise
     * 
     * Examples:
     * ```
     * pluralizeWithCount("cat", 0) // "cats"
     * pluralizeWithCount("cat", 1) // "cat"
     * pluralizeWithCount("cat", 5) // "cats"
     * pluralizeWithCount("child", 3) // "children"
     * ```
     */
    fun pluralizeWithCount(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("pluralizeWithCount expects 2 arguments")
        }
        
        val word = args[0]
        val count = args[1]
        val num = (count as? UDM.Scalar)?.value ?: return word
        
        return if (num == 1.0) {
            word
        } else {
            pluralize(listOf(word, count))
        }
    }
    
    @UTLXFunction(
        description = "Checks if a word is in plural form",
        minArgs = 1,
        maxArgs = 1,
        category = "String",
        parameters = [
            "word: Word value"
        ],
        returns = "Boolean indicating the result",
        example = "isPlural(...) => result",
        notes = "Examples:\n```\nisPlural(\"cats\") // true\nisPlural(\"cat\") // false\nisPlural(\"children\") // true\nisPlural(\"sheep\") // false (can be both)\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Checks if a word is in plural form
     * 
     * @param word The word to check
     * @return true if word appears to be plural
     * 
     * Examples:
     * ```
     * isPlural("cats") // true
     * isPlural("cat") // false
     * isPlural("children") // true
     * isPlural("sheep") // false (can be both)
     * ```
     */
    fun isPlural(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("isPlural expects 1 argument")
        }
        
        val word = args[0]
        val text = (word as? UDM.Scalar)?.value?.toString()?.trim()?.lowercase() 
            ?: return UDM.Scalar(false)
        
        // Check if it's an irregular plural
        if (irregularSingulars.containsKey(text)) {
            return UDM.Scalar(true)
        }
        
        // Check if it's an uncountable noun (ambiguous)
        if (uncountableNouns.contains(text)) {
            return UDM.Scalar(false)
        }
        
        // Check common plural patterns
        val seemsPlural = text.endsWith("s") || 
                         text.endsWith("es") ||
                         text.endsWith("ies")
        
        return UDM.Scalar(seemsPlural)
    }
    
    @UTLXFunction(
        description = "Checks if a word is in singular form",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "word: Word value",
        "word: Word value"
        ],
        returns = "Boolean indicating the result",
        example = "isSingular(...) => result",
        notes = "Examples:\n```\nisSingular(\"cat\") // true\nisSingular(\"cats\") // false\nisSingular(\"child\") // true\nisSingular(\"sheep\") // false (can be both)\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Checks if a word is in singular form
     * 
     * @param word The word to check
     * @return true if word appears to be singular
     * 
     * Examples:
     * ```
     * isSingular("cat") // true
     * isSingular("cats") // false
     * isSingular("child") // true
     * isSingular("sheep") // false (can be both)
     * ```
     */
    fun isSingular(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("isSingular expects 1 argument")
        }
        
        val word = args[0]
        val text = (word as? UDM.Scalar)?.value?.toString()?.trim()?.lowercase() 
            ?: return UDM.Scalar(false)
        
        // Check if it's an irregular singular
        if (irregularPlurals.containsKey(text)) {
            return UDM.Scalar(true)
        }
        
        // Check if it's an uncountable noun (ambiguous)
        if (uncountableNouns.contains(text)) {
            return UDM.Scalar(false)
        }
        
        // Check if it doesn't match common plural patterns
        val notPlural = !text.endsWith("s") && 
                       !text.endsWith("es") &&
                       !text.endsWith("ies")
        
        return UDM.Scalar(notPlural)
    }
    
    @UTLXFunction(
        description = "Creates a formatted string with count and correctly pluralized word",
        minArgs = 2,
        maxArgs = 2,
        category = "String",
        parameters = [
            "count: Count value",
        "word: Word value"
        ],
        returns = "Result of the operation",
        example = "formatPlural(...) => result",
        notes = "Examples:\n```\nformatPlural(1, \"cat\") // \"1 cat\"\nformatPlural(5, \"cat\") // \"5 cats\"\nformatPlural(0, \"item\") // \"0 items\"\nformatPlural(3, \"child\") // \"3 children\"\n```",
        tags = ["string"],
        since = "1.0"
    )
    /**
     * Creates a formatted string with count and correctly pluralized word
     * 
     * @param count The count
     * @param word The base noun
     * @return Formatted string like "1 cat" or "5 cats"
     * 
     * Examples:
     * ```
     * formatPlural(1, "cat") // "1 cat"
     * formatPlural(5, "cat") // "5 cats"
     * formatPlural(0, "item") // "0 items"
     * formatPlural(3, "child") // "3 children"
     * ```
     */
    fun formatPlural(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("formatPlural expects 2 arguments")
        }
        
        val count = args[0]
        val word = args[1]
        val num = (count as? UDM.Scalar)?.value ?: return UDM.Scalar.nullValue()
        val text = (word as? UDM.Scalar)?.value?.toString() ?: return UDM.Scalar.nullValue()
        
        val pluralForm = if (num == 1.0) {
            text
        } else {
            (pluralize(listOf(word, count)) as? UDM.Scalar)?.value?.toString() ?: text
        }
        
        val numInt = when (num) {
            is Number -> num.toInt()
            else -> 0
        }
        return UDM.Scalar("$numInt $pluralForm")
    }
    
    // ============================================
    // UTILITY FUNCTIONS
    // ============================================
    
    /**
     * Checks if a character is a vowel
     */
    private fun isVowel(c: Char): Boolean {
        return c in "aeiouAEIOU"
    }
    
    /**
     * Matches the case of the result to the original word
     * 
     * Examples:
     * - "Cat" -> "Cats" (capitalize first)
     * - "CAT" -> "CATS" (all uppercase)
     * - "cat" -> "cats" (all lowercase)
     */
    private fun matchCase(original: String, result: String, originalCase: String): String {
        return when {
            // All uppercase
            originalCase.all { it.isUpperCase() || !it.isLetter() } -> 
                result.uppercase()
            
            // First letter uppercase
            originalCase.firstOrNull()?.isUpperCase() == true -> 
                result.replaceFirstChar { it.uppercase() }
            
            // Default: lowercase
            else -> result
        }
    }
}
