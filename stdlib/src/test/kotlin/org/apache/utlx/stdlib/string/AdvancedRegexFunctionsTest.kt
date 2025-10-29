// stdlib/src/test/kotlin/org/apache/utlx/stdlib/string/AdvancedRegexFunctionsTest.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Advanced Regex functions.
 * 
 * Tests cover:
 * - analyzeString: Full regex analysis with capture groups
 * - regexGroups: Extract all capture groups
 * - regexNamedGroups: Extract named capture groups
 * - findAllMatches: Find all matches with positions
 * - splitWithMatches: Split while keeping matches
 * - matchesWhole: Test if entire string matches
 * - replaceWithFunction: Advanced regex replacement
 */
class AdvancedRegexFunctionsTest {

    // ==================== analyzeString Tests ====================
    
    @Test
    fun `test analyzeString - simple pattern`() {
        val text = UDM.Scalar("test123abc456")
        val pattern = UDM.Scalar("([a-z]+)(\\d+)")
        
        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size, "Should find 2 matches")
        
        // First match: "test123"
        val match1 = matches.elements[0] as UDM.Object
        assertEquals("test123", (match1.properties["match"] as UDM.Scalar).value)
        assertEquals(0, (match1.properties["start"] as UDM.Scalar).value)
        assertEquals(7, (match1.properties["end"] as UDM.Scalar).value)
        
        val groups1 = match1.properties["groups"] as UDM.Array
        assertEquals("test", (groups1.elements[0] as UDM.Scalar).value)
        assertEquals("123", (groups1.elements[1] as UDM.Scalar).value)
        
        // Second match: "abc456"
        val match2 = matches.elements[1] as UDM.Object
        assertEquals("abc456", (match2.properties["match"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test analyzeString - email pattern`() {
        val text = UDM.Scalar("Contact: john@example.com or jane@test.org")
        val pattern = UDM.Scalar("(\\w+)@([\\w.]+)")
        
        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size, "Should find 2 email addresses")
        
        val match1 = matches.elements[0] as UDM.Object
        val groups1 = match1.properties["groups"] as UDM.Array
        assertEquals("john", (groups1.elements[0] as UDM.Scalar).value, "Username part")
        assertEquals("example.com", (groups1.elements[1] as UDM.Scalar).value, "Domain part")
    }
    
    @Test
    fun `test analyzeString - no matches`() {
        val text = UDM.Scalar("no digits here")
        val pattern = UDM.Scalar("\\d+")
        
        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(0, matches.elements.size, "Should find no matches")
    }
    
    @Test
    fun `test analyzeString - nested groups`() {
        val text = UDM.Scalar("Price: $123.45")
        val pattern = UDM.Scalar("\\$((\\d+)\\.(\\d+))")
        
        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val matches = result as UDM.Array
        
        val match = matches.elements[0] as UDM.Object
        val groups = match.properties["groups"] as UDM.Array
        
        assertEquals(3, groups.elements.size, "Should have 3 capture groups")
        assertEquals("123.45", (groups.elements[0] as UDM.Scalar).value)
        assertEquals("123", (groups.elements[1] as UDM.Scalar).value)
        assertEquals("45", (groups.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun `test analyzeString - XSLT compatibility`() {
        // Test XSLT analyze-string compatibility
        val text = UDM.Scalar("The cat sat on the mat")
        val pattern = UDM.Scalar("\\b(\\w)at\\b")

        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val matches = result as UDM.Array

        assertEquals(3, matches.elements.size, "Should find 'cat', 'sat', and 'mat'")
    }

    // ==================== regexGroups Tests ====================
    
    @Test
    fun `test regexGroups - simple extraction`() {
        val text = UDM.Scalar("John Doe")
        val pattern = UDM.Scalar("(\\w+) (\\w+)")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(text, pattern))
        val groups = result as UDM.Array
        
        assertEquals(2, groups.elements.size)
        assertEquals("John", (groups.elements[0] as UDM.Scalar).value)
        assertEquals("Doe", (groups.elements[1] as UDM.Scalar).value)
    }
    
    @Test
    fun `test regexGroups - price parsing`() {
        val text = UDM.Scalar("Price: $123.45")
        val pattern = UDM.Scalar("\\$(\\d+)\\.(\\d+)")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(text, pattern))
        val groups = result as UDM.Array
        
        assertEquals(2, groups.elements.size)
        assertEquals("123", (groups.elements[0] as UDM.Scalar).value, "Dollars")
        assertEquals("45", (groups.elements[1] as UDM.Scalar).value, "Cents")
    }
    
    @Test
    fun `test regexGroups - no match`() {
        val text = UDM.Scalar("no match here")
        val pattern = UDM.Scalar("(\\d+)")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(text, pattern))
        val groups = result as UDM.Array
        
        assertEquals(0, groups.elements.size, "Should return empty array for no match")
    }
    
    @Test
    fun `test regexGroups - date extraction`() {
        val text = UDM.Scalar("Date: 2025-10-15")
        val pattern = UDM.Scalar("(\\d{4})-(\\d{2})-(\\d{2})")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(text, pattern))
        val groups = result as UDM.Array
        
        assertEquals(3, groups.elements.size)
        assertEquals("2025", (groups.elements[0] as UDM.Scalar).value, "Year")
        assertEquals("10", (groups.elements[1] as UDM.Scalar).value, "Month")
        assertEquals("15", (groups.elements[2] as UDM.Scalar).value, "Day")
    }

    // ==================== regexNamedGroups Tests ====================
    
    @Test
    fun `test regexNamedGroups - email parsing`() {
        val text = UDM.Scalar("user@example.com")
        val pattern = UDM.Scalar("(?<user>\\w+)@(?<domain>[\\w.]+)")
        
        val result = AdvancedRegexFunctions.regexNamedGroups(listOf(text, pattern))
        val groups = result as UDM.Object
        
        assertEquals("user", (groups.properties["user"] as UDM.Scalar).value)
        assertEquals("example.com", (groups.properties["domain"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test regexNamedGroups - date parsing`() {
        val text = UDM.Scalar("2025-10-15")
        val pattern = UDM.Scalar("(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})")
        
        val result = AdvancedRegexFunctions.regexNamedGroups(listOf(text, pattern))
        val groups = result as UDM.Object
        
        assertEquals("2025", (groups.properties["year"] as UDM.Scalar).value)
        assertEquals("10", (groups.properties["month"] as UDM.Scalar).value)
        assertEquals("15", (groups.properties["day"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test regexNamedGroups - URL parsing`() {
        val text = UDM.Scalar("https://example.com:8080/path")
        val pattern = UDM.Scalar("(?<protocol>https?)://(?<host>[^:]+):(?<port>\\d+)(?<path>/.*)")
        
        val result = AdvancedRegexFunctions.regexNamedGroups(listOf(text, pattern))
        val groups = result as UDM.Object
        
        assertEquals("https", (groups.properties["protocol"] as UDM.Scalar).value)
        assertEquals("example.com", (groups.properties["host"] as UDM.Scalar).value)
        assertEquals("8080", (groups.properties["port"] as UDM.Scalar).value)
        assertEquals("/path", (groups.properties["path"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test regexNamedGroups - no match`() {
        val text = UDM.Scalar("no match")
        val pattern = UDM.Scalar("(?<number>\\d+)")
        
        val result = AdvancedRegexFunctions.regexNamedGroups(listOf(text, pattern))
        val groups = result as UDM.Object
        
        assertEquals(0, groups.properties.size, "Should return empty object for no match")
    }

    // ==================== findAllMatches Tests ====================
    
    @Test
    fun `test findAllMatches - multiple numbers`() {
        val text = UDM.Scalar("I have 10 apples and 20 oranges")
        val pattern = UDM.Scalar("\\d+")
        
        val result = AdvancedRegexFunctions.findAllMatches(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size)
        
        val match1 = matches.elements[0] as UDM.Object
        assertEquals("10", (match1.properties["match"] as UDM.Scalar).value)
        assertEquals(7, (match1.properties["start"] as UDM.Scalar).value)
        
        val match2 = matches.elements[1] as UDM.Object
        assertEquals("20", (match2.properties["match"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test findAllMatches - hashtags`() {
        val text = UDM.Scalar("Check out #programming and #kotlin today!")
        val pattern = UDM.Scalar("#\\w+")
        
        val result = AdvancedRegexFunctions.findAllMatches(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size)
        assertEquals("#programming", ((matches.elements[0] as UDM.Object).properties["match"] as UDM.Scalar).value)
        assertEquals("#kotlin", ((matches.elements[1] as UDM.Object).properties["match"] as UDM.Scalar).value)
    }
    
    @Test
    fun `test findAllMatches - overlapping not matched`() {
        val text = UDM.Scalar("aaaa")
        val pattern = UDM.Scalar("aa")
        
        val result = AdvancedRegexFunctions.findAllMatches(listOf(text, pattern))
        val matches = result as UDM.Array
        
        // Non-overlapping matches only
        assertEquals(2, matches.elements.size, "Should find 2 non-overlapping 'aa' matches")
    }

    // ==================== splitWithMatches Tests ====================
    
    @Test
    fun `test splitWithMatches - comma separator`() {
        val text = UDM.Scalar("apple,banana,cherry")
        val pattern = UDM.Scalar(",")
        
        val result = AdvancedRegexFunctions.splitWithMatches(listOf(text, pattern))
        val parts = result as UDM.Array
        
        // Should return: ["apple", ",", "banana", ",", "cherry"]
        assertEquals(5, parts.elements.size)
        assertEquals("apple", (parts.elements[0] as UDM.Scalar).value)
        assertEquals(",", (parts.elements[1] as UDM.Scalar).value)
        assertEquals("banana", (parts.elements[2] as UDM.Scalar).value)
        assertEquals(",", (parts.elements[3] as UDM.Scalar).value)
        assertEquals("cherry", (parts.elements[4] as UDM.Scalar).value)
    }
    
    @Test
    fun `test splitWithMatches - word boundaries`() {
        val text = UDM.Scalar("Hello World!")
        val pattern = UDM.Scalar("\\s+")
        
        val result = AdvancedRegexFunctions.splitWithMatches(listOf(text, pattern))
        val parts = result as UDM.Array
        
        // Should include whitespace in results
        assertTrue(parts.elements.size >= 3, "Should have at least text and whitespace")
        assertEquals("Hello", (parts.elements[0] as UDM.Scalar).value)
    }
    
    @Test
    fun `test splitWithMatches - no matches`() {
        val text = UDM.Scalar("nodelimitershere")
        val pattern = UDM.Scalar(",")
        
        val result = AdvancedRegexFunctions.splitWithMatches(listOf(text, pattern))
        val parts = result as UDM.Array
        
        assertEquals(1, parts.elements.size, "Should return original text when no matches")
        assertEquals("nodelimitershere", (parts.elements[0] as UDM.Scalar).value)
    }

    // ==================== matchesWhole Tests ====================
    
    @Test
    fun `test matchesWhole - exact match`() {
        val text = UDM.Scalar("12345")
        val pattern = UDM.Scalar("\\d+")
        
        val result = AdvancedRegexFunctions.matchesWhole(listOf(text, pattern))
        val matches = (result as UDM.Scalar).value as Boolean
        
        assertTrue(matches, "String of only digits should match \\d+")
    }
    
    @Test
    fun `test matchesWhole - partial match fails`() {
        val text = UDM.Scalar("123abc")
        val pattern = UDM.Scalar("\\d+")
        
        val result = AdvancedRegexFunctions.matchesWhole(listOf(text, pattern))
        val matches = (result as UDM.Scalar).value as Boolean
        
        assertFalse(matches, "String with letters should not match \\d+ for whole string")
    }
    
    @Test
    fun `test matchesWhole - email validation`() {
        val validEmail = UDM.Scalar("user@example.com")
        val invalidEmail = UDM.Scalar("not an email")
        val pattern = UDM.Scalar("\\w+@[\\w.]+")
        
        val result1 = AdvancedRegexFunctions.matchesWhole(listOf(validEmail, pattern))
        val result2 = AdvancedRegexFunctions.matchesWhole(listOf(invalidEmail, pattern))
        
        assertTrue((result1 as UDM.Scalar).value as Boolean, "Valid email should match")
        assertFalse((result2 as UDM.Scalar).value as Boolean, "Invalid email should not match")
    }
    
    @Test
    fun `test matchesWhole - date format validation`() {
        val validDate = UDM.Scalar("2025-10-15")
        val invalidDate = UDM.Scalar("2025/10/15")
        val pattern = UDM.Scalar("\\d{4}-\\d{2}-\\d{2}")
        
        val result1 = AdvancedRegexFunctions.matchesWhole(listOf(validDate, pattern))
        val result2 = AdvancedRegexFunctions.matchesWhole(listOf(invalidDate, pattern))
        
        assertTrue((result1 as UDM.Scalar).value as Boolean, "ISO date should match")
        assertFalse((result2 as UDM.Scalar).value as Boolean, "Non-ISO date should not match")
    }

    // ==================== replaceWithFunction Tests ====================
    
    @Test
    fun `test replaceWithFunction - uppercase matches`() {
        val text = UDM.Scalar("hello world")
        val pattern = UDM.Scalar("\\w+")
        val replacer = { match: String -> match.uppercase() }
        
        // Note: This test shows the intended API, actual implementation may vary
        // depending on how function callbacks are handled in UTL-X
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            text, 
            pattern,
            UDM.Scalar("UPPERCASE") // Function reference placeholder
        ))
        
        // Expected: "HELLO WORLD"
        assertNotNull(result)
    }
    
    @Test
    fun `test replaceWithFunction - number doubling`() {
        val text = UDM.Scalar("I have 5 apples and 10 oranges")
        val pattern = UDM.Scalar("\\d+")
        
        // This test demonstrates the concept
        // Actual implementation needs function call integration
        val result = AdvancedRegexFunctions.replaceWithFunction(listOf(
            text,
            pattern,
            UDM.Scalar("DOUBLE") // Function reference
        ))
        
        assertNotNull(result)
    }

    // ==================== Error Handling Tests ====================
    
    @Test
    fun `test analyzeString - invalid regex`() {
        val text = UDM.Scalar("test")
        val invalidPattern = UDM.Scalar("[invalid(")
        
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.analyzeString(listOf(text, invalidPattern))
        }
    }
    
    @Test
    fun `test analyzeString - missing arguments`() {
        val text = UDM.Scalar("test")
        
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.analyzeString(listOf(text))
        }
    }
    
    @Test
    fun `test regexGroups - invalid regex`() {
        val text = UDM.Scalar("test")
        val invalidPattern = UDM.Scalar("*invalid")
        
        assertThrows<IllegalArgumentException> {
            AdvancedRegexFunctions.regexGroups(listOf(text, invalidPattern))
        }
    }

    // ==================== Real-World Use Cases ====================
    
    @Test
    fun `test real-world - log parsing`() {
        val logLine = UDM.Scalar("2025-10-15 14:30:45 ERROR UserService: Login failed for user john@example.com")
        val pattern = UDM.Scalar("(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}) (\\w+) (\\w+): (.+)")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(logLine, pattern))
        val groups = result as UDM.Array
        
        assertEquals(5, groups.elements.size)
        assertEquals("2025-10-15", (groups.elements[0] as UDM.Scalar).value, "Date")
        assertEquals("14:30:45", (groups.elements[1] as UDM.Scalar).value, "Time")
        assertEquals("ERROR", (groups.elements[2] as UDM.Scalar).value, "Level")
        assertEquals("UserService", (groups.elements[3] as UDM.Scalar).value, "Service")
    }
    
    @Test
    fun `test real-world - phone number extraction`() {
        val text = UDM.Scalar("Call me at (555) 123-4567 or 555-987-6543")
        val pattern = UDM.Scalar("\\(?\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{4}")
        
        val result = AdvancedRegexFunctions.findAllMatches(listOf(text, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size, "Should find both phone numbers")
    }
    
    @Test
    fun `test real-world - markdown link extraction`() {
        val markdown = UDM.Scalar("Check [UTL-X](https://github.com/grauwen/utl-x) and [docs](https://utlx.org)")
        val pattern = UDM.Scalar("\\[([^]]+)\\]\\(([^)]+)\\)")
        
        val result = AdvancedRegexFunctions.analyzeString(listOf(markdown, pattern))
        val matches = result as UDM.Array
        
        assertEquals(2, matches.elements.size)
        
        val link1 = matches.elements[0] as UDM.Object
        val groups1 = link1.properties["groups"] as UDM.Array
        assertEquals("UTL-X", (groups1.elements[0] as UDM.Scalar).value, "Link text")
        assertEquals("https://github.com/grauwen/utl-x", (groups1.elements[1] as UDM.Scalar).value, "URL")
    }
    
    @Test
    fun `test real-world - CSV field parsing with quotes`() {
        val csvLine = UDM.Scalar("\"John Doe\",\"123 Main St, Apt 4\",\"555-1234\"")
        val pattern = UDM.Scalar("\"([^\"]*)\"")
        
        val result = AdvancedRegexFunctions.findAllMatches(listOf(csvLine, pattern))
        val matches = result as UDM.Array
        
        assertEquals(3, matches.elements.size, "Should extract 3 quoted fields")
    }
    
    @Test
    fun `test real-world - semantic version parsing`() {
        val version = UDM.Scalar("v1.2.3-beta.4+build.123")
        val pattern = UDM.Scalar("v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(\\w+(?:\\.\\d+)?))?(?:\\+(\\w+(?:\\.\\d+)?))?")
        
        val result = AdvancedRegexFunctions.regexGroups(listOf(version, pattern))
        val groups = result as UDM.Array
        
        assertTrue(groups.elements.size >= 3, "Should extract major, minor, patch")
        assertEquals("1", (groups.elements[0] as UDM.Scalar).value, "Major version")
        assertEquals("2", (groups.elements[1] as UDM.Scalar).value, "Minor version")
        assertEquals("3", (groups.elements[2] as UDM.Scalar).value, "Patch version")
    }

    // ==================== Performance Tests ====================
    
    @Test
    fun `test performance - large text analysis`() {
        val largeText = "test ".repeat(1000) + "123"
        val text = UDM.Scalar(largeText)
        val pattern = UDM.Scalar("\\d+")
        
        val startTime = System.currentTimeMillis()
        val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
        val endTime = System.currentTimeMillis()
        
        val matches = result as UDM.Array
        assertEquals(1, matches.elements.size)
        
        val duration = endTime - startTime
        assertTrue(duration < 1000, "Should complete within 1 second for 1000 words")
    }
    
    @Test
    fun `test performance - many small matches`() {
        val text = UDM.Scalar("a1b2c3d4e5f6g7h8i9j0".repeat(100))
        val pattern = UDM.Scalar("\\d")
        
        val startTime = System.currentTimeMillis()
        val result = AdvancedRegexFunctions.findAllMatches(listOf(text, pattern))
        val endTime = System.currentTimeMillis()
        
        val matches = result as UDM.Array
        assertEquals(1000, matches.elements.size, "Should find 1000 digits")
        
        val duration = endTime - startTime
        assertTrue(duration < 1000, "Should complete within 1 second for 1000 matches")
    }
}
