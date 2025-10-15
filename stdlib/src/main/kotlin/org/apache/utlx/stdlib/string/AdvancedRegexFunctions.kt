// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/AdvancedRegexFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM

/**
 * Advanced Regular Expression Functions
 * 
 * Provides enhanced regex capabilities including capture groups,
 * named groups, and detailed match analysis.
 * 
 * Achieves parity with XSLT's analyze-string() function and
 * DataWeave's advanced pattern matching.
 * 
 * Functions:
 * - analyzeString: Full regex analysis with capture groups
 * - regexGroups: Extract all capture groups
 * - regexNamedGroups: Extract named capture groups
 * - findAllMatches: Find all matches with positions
 * - splitWithMatches: Split while keeping matches
 * 
 * @since UTL-X 1.1
 */
object AdvancedRegexFunctions {
    
    /**
     * Analyzes a string with a regex pattern and returns detailed match information.
     * 
     * Similar to XSLT's analyze-string() function.
     * Returns an array of match objects, each containing:
     * - match: the full matched text
     * - start: starting index
     * - end: ending index
     * - groups: array of capture group values
     * 
     * @param args [0] text to analyze (String)
     *             [1] regex pattern (String)
     * @return array of match objects with capture groups
     * 
     * Example:
     * ```
     * analyzeString("test123abc456", "([a-z]+)(\\d+)")
     * → [
     *     {match: "test123", start: 0, end: 6, groups: ["test", "123"]},
     *     {match: "abc456", start: 7, end: 12, groups: ["abc", "456"]}
     *   ]
     * 
     * analyzeString("email@example.com", "(\\w+)@([\\w.]+)")
     * → [
     *     {match: "email@example.com", start: 0, end: 16, groups: ["email", "example.com"]}
     *   ]
     * 
     * analyzeString("no match here", "\\d+")
     * → []
     * ```
     */
    fun analyzeString(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("analyzeString() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val matches = regex.findAll(text).map { match ->
                UDM.Object(mutableMapOf(
                    "match" to UDM.Scalar(match.value),
                    "start" to UDM.Scalar(match.range.first),
                    "end" to UDM.Scalar(match.range.last + 1),
                    "groups" to UDM.Array(
                        match.groupValues.drop(1).map { UDM.Scalar(it) }
                    )
                ))
            }.toList()
            
            return UDM.Array(matches)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Extracts all capture groups from the first match of a pattern.
     * 
     * Returns an array of captured strings. If no match is found, returns empty array.
     * Capture groups are numbered from 1 (group 0 is the full match).
     * 
     * @param args [0] text to search (String)
     *             [1] regex pattern with capture groups (String)
     * @return array of captured group values
     * 
     * Example:
     * ```
     * regexGroups("John Doe", "(\\w+) (\\w+)")
     * → ["John", "Doe"]
     * 
     * regexGroups("Price: $123.45", "\\$(\\d+)\\.(\\d+)")
     * → ["123", "45"]
     * 
     * regexGroups("2023-10-15", "(\\d{4})-(\\d{2})-(\\d{2})")
     * → ["2023", "10", "15"]
     * 
     * regexGroups("no match", "(\\d+)")
     * → []
     * ```
     */
    fun regexGroups(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("regexGroups() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val match = regex.find(text)
            
            return if (match != null) {
                // Skip group 0 (the full match)
                val groups = match.groupValues.drop(1).map { UDM.Scalar(it) }
                UDM.Array(groups)
            } else {
                UDM.Array(emptyList())
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Extracts named capture groups from the first match.
     * 
     * Returns an object mapping group names to their captured values.
     * If no match is found, returns empty object.
     * 
     * @param args [0] text to search (String)
     *             [1] regex pattern with named groups (String)
     * @return object with named group values
     * 
     * Example:
     * ```
     * regexNamedGroups("user@example.com", "(?<user>\\w+)@(?<domain>[\\w.]+)")
     * → {user: "user", domain: "example.com"}
     * 
     * regexNamedGroups("2023-10-15", "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})")
     * → {year: "2023", month: "10", day: "15"}
     * 
     * regexNamedGroups("Price: $123.45", "\\$(?<dollars>\\d+)\\.(?<cents>\\d+)")
     * → {dollars: "123", cents: "45"}
     * 
     * regexNamedGroups("no match", "(?<num>\\d+)")
     * → {}
     * ```
     */
    fun regexNamedGroups(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("regexNamedGroups() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val match = regex.find(text)
            
            return if (match != null) {
                val groups = mutableMapOf<String, UDM>()
                
                // Extract named groups
                match.groups.forEach { group ->
                    if (group != null && group is MatchNamedGroup) {
                        groups[group.name] = UDM.Scalar(group.value)
                    }
                }
                
                UDM.Object(groups)
            } else {
                UDM.Object(mutableMapOf())
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Finds all matches of a pattern and returns them with positions.
     * 
     * Similar to analyzeString but simpler - only returns the matched text and positions.
     * 
     * @param args [0] text to search (String)
     *             [1] regex pattern (String)
     * @return array of match objects
     * 
     * Example:
     * ```
     * findAllMatches("The year is 2023 and the month is 10", "\\d+")
     * → [
     *     {match: "2023", start: 12, end: 16},
     *     {match: "10", start: 38, end: 40}
     *   ]
     * 
     * findAllMatches("cat dog cat bird", "cat")
     * → [
     *     {match: "cat", start: 0, end: 3},
     *     {match: "cat", start: 8, end: 11}
     *   ]
     * ```
     */
    fun findAllMatches(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("findAllMatches() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val matches = regex.findAll(text).map { match ->
                UDM.Object(mutableMapOf(
                    "match" to UDM.Scalar(match.value),
                    "start" to UDM.Scalar(match.range.first),
                    "end" to UDM.Scalar(match.range.last + 1)
                ))
            }.toList()
            
            return UDM.Array(matches)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Splits a string by a pattern, but keeps the matched parts.
     * 
     * Returns an array alternating between non-matching and matching parts.
     * Useful for parsing while preserving delimiters.
     * 
     * @param args [0] text to split (String)
     *             [1] regex pattern (String)
     * @return array of {text, isMatch} objects
     * 
     * Example:
     * ```
     * splitWithMatches("hello123world456", "\\d+")
     * → [
     *     {text: "hello", isMatch: false},
     *     {text: "123", isMatch: true},
     *     {text: "world", isMatch: false},
     *     {text: "456", isMatch: true}
     *   ]
     * 
     * splitWithMatches("a,b,c", ",")
     * → [
     *     {text: "a", isMatch: false},
     *     {text: ",", isMatch: true},
     *     {text: "b", isMatch: false},
     *     {text: ",", isMatch: true},
     *     {text: "c", isMatch: false}
     *   ]
     * ```
     */
    fun splitWithMatches(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("splitWithMatches() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val result = mutableListOf<UDM>()
            var lastEnd = 0
            
            regex.findAll(text).forEach { match ->
                // Add non-matching part before this match
                if (match.range.first > lastEnd) {
                    val nonMatch = text.substring(lastEnd, match.range.first)
                    result.add(UDM.Object(mutableMapOf(
                        "text" to UDM.Scalar(nonMatch),
                        "isMatch" to UDM.Scalar(false)
                    )))
                }
                
                // Add matching part
                result.add(UDM.Object(mutableMapOf(
                    "text" to UDM.Scalar(match.value),
                    "isMatch" to UDM.Scalar(true)
                )))
                
                lastEnd = match.range.last + 1
            }
            
            // Add remaining non-matching part
            if (lastEnd < text.length) {
                val nonMatch = text.substring(lastEnd)
                result.add(UDM.Object(mutableMapOf(
                    "text" to UDM.Scalar(nonMatch),
                    "isMatch" to UDM.Scalar(false)
                )))
            }
            
            return UDM.Array(result)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Tests if a string matches a pattern completely (not just contains).
     * 
     * Similar to matches() but returns the match object if successful.
     * 
     * @param args [0] text to test (String)
     *             [1] regex pattern (String)
     * @return match object if successful, null otherwise
     * 
     * Example:
     * ```
     * matchesWhole("123", "\\d+")
     * → {match: "123", groups: []}
     * 
     * matchesWhole("abc123", "\\d+")
     * → null (doesn't match the whole string)
     * 
     * matchesWhole("user@example.com", "(\\w+)@([\\w.]+)")
     * → {match: "user@example.com", groups: ["user", "example.com"]}
     * ```
     */
    fun matchesWhole(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("matchesWhole() requires 2 arguments: text, pattern")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        
        try {
            val regex = Regex(pattern)
            val match = regex.matchEntire(text)
            
            return if (match != null) {
                UDM.Object(mutableMapOf(
                    "match" to UDM.Scalar(match.value),
                    "groups" to UDM.Array(
                        match.groupValues.drop(1).map { UDM.Scalar(it) }
                    )
                ))
            } else {
                UDM.Scalar(null)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
    
    /**
     * Replaces all matches with results from a function.
     * 
     * The function receives the match object and returns replacement text.
     * More powerful than simple string replacement.
     * 
     * @param args [0] text to transform (String)
     *             [1] regex pattern (String)
     *             [2] replacement function (match) => string
     * @return transformed text
     * 
     * Example:
     * ```
     * replaceWithFunction("hello123world456", "\\d+", (m) => "[" + m.match + "]")
     * → "hello[123]world[456]"
     * 
     * replaceWithFunction("user@example.com", "@(\\w+)", (m) => "@" + upper(m.groups[0]))
     * → "user@EXAMPLE.com"
     * ```
     */
    fun replaceWithFunction(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("replaceWithFunction() requires 3 arguments: text, pattern, function")
        }
        
        val text = args[0].asString()
        val pattern = args[1].asString()
        val functionArg = args[2]
        
        try {
            val regex = Regex(pattern)
            
            // TODO: Implement function calling mechanism
            // For now, placeholder implementation
            val result = regex.replace(text) { matchResult ->
                // Would call: function(matchObject).asString()
                matchResult.value // Placeholder
            }
            
            return UDM.Scalar(result)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }
}
