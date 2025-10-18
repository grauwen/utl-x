// stdlib/src/main/kotlin/org/apache/utlx/stdlib/annotations/UTLXFunction.kt
package org.apache.utlx.stdlib.annotations

/**
 * Annotation for UTL-X standard library functions
 * 
 * This annotation provides metadata for function registry generation and IDE tooling.
 * All stdlib functions should be annotated with this to enable automatic discovery.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UTLXFunction(
    /**
     * Human-readable description of what the function does
     */
    val description: String,
    
    /**
     * Minimum number of arguments this function accepts
     */
    val minArgs: Int = -1,
    
    /**
     * Maximum number of arguments this function accepts  
     */
    val maxArgs: Int = -1,
    
    /**
     * Function category for grouping (Array, String, Math, etc.)
     */
    val category: String = "",
    
    /**
     * Descriptions of each parameter in order
     * Example: ["array: The input array", "predicate: Function to test each element"]
     */
    val parameters: Array<String> = [],
    
    /**
     * Description of the return value and its type
     * Example: "Returns the filtered array with elements matching the predicate"
     */
    val returns: String = "",
    
    /**
     * Positive example showing successful usage
     */
    val example: String = "",
    
    /**
     * Additional positive examples (for complex functions)
     */
    val additionalExamples: Array<String> = [],
    
    /**
     * Negative example showing what happens with invalid input
     * Example: "filter(null, x => true) => throws FunctionArgumentException"
     */
    val negativeExample: String = "",
    
    /**
     * Additional negative examples showing edge cases
     */
    val additionalNegativeExamples: Array<String> = [],

    /**
     * Important behavioral notes extracted from KDoc
     * Multi-line notes about special behaviors, edge cases, what is filtered/kept, etc.
     * Example: "Filters out: null values, empty strings\nKeeps: 0, false (valid values)"
     */
    val notes: String = "",

    /**
     * Whether this function is deprecated
     */
    val deprecated: Boolean = false,

    /**
     * Deprecation message if deprecated = true
     */
    val deprecationMessage: String = "",

    /**
     * Tags for additional classification
     */
    val tags: Array<String> = [],

    /**
     * Performance characteristics or complexity notes (DEPRECATED - use notes instead)
     * Example: "O(n) time complexity", "Memory usage scales with input size"
     */
    @Deprecated("Use notes field instead", ReplaceWith("notes"))
    val performance: String = "",

    /**
     * Notes about thread safety (DEPRECATED - not relevant for transformation language)
     * Example: "Thread-safe", "Not thread-safe - use synchronization"
     */
    @Deprecated("Not relevant for transformation language", ReplaceWith(""))
    val threadSafety: String = "",

    /**
     * References to related functions
     * Example: ["map", "reduce", "forEach"]
     */
    val seeAlso: Array<String> = [],

    /**
     * Version when this function was introduced
     * Example: "1.0", "1.2.3"
     */
    val since: String = ""
)