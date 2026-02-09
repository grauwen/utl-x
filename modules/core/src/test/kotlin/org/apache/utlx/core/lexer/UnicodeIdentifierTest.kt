package org.apache.utlx.core.lexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Unicode identifier support in the UTL-X lexer.
 *
 * JSON supports Unicode keys per RFC 8259, so UTL-X must be able to access them.
 * Examples: $input.år (Swedish), $input.løn (Danish), $input.größe (German)
 */
class UnicodeIdentifierTest {

    @Test
    fun `tokenize Swedish identifier with ring above`() {
        val lexer = Lexer("år")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'år' as identifier")
        assertEquals("år", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Danish identifier with slashed o`() {
        val lexer = Lexer("løn")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'løn' as identifier")
        assertEquals("løn", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize German identifier with umlaut`() {
        val lexer = Lexer("größe")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'größe' as identifier")
        assertEquals("größe", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Turkish identifier with dotless i`() {
        val lexer = Lexer("satıcı")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'satıcı' as identifier")
        assertEquals("satıcı", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Turkish identifier with umlauted u`() {
        val lexer = Lexer("ünvan")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'ünvan' as identifier")
        assertEquals("ünvan", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize mixed ASCII and Unicode identifier`() {
        val lexer = Lexer("orderÅr2025")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'orderÅr2025' as identifier")
        assertEquals("orderÅr2025", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize property access with Unicode identifier`() {
        val lexer = Lexer("\$input.år")
        val tokens = lexer.tokenize()

        // Should have: $input (as one IDENTIFIER token), DOT, år (IDENTIFIER), EOF
        val identifiers = tokens.filter { it.type == TokenType.IDENTIFIER }
        assertEquals(2, identifiers.size, "Should have two identifiers")
        assertEquals("\$input", identifiers[0].lexeme)
        assertEquals("år", identifiers[1].lexeme)
    }

    @Test
    fun `tokenize nested property access with Unicode identifiers`() {
        val lexer = Lexer("\$input.period.år")
        val tokens = lexer.tokenize()

        val identifiers = tokens.filter { it.type == TokenType.IDENTIFIER }
        assertEquals(3, identifiers.size, "Should have three identifiers")
        assertEquals("\$input", identifiers[0].lexeme)
        assertEquals("period", identifiers[1].lexeme)
        assertEquals("år", identifiers[2].lexeme)
    }

    @Test
    fun `tokenize Danish salary structure with Unicode`() {
        val lexer = Lexer("\$input.løn.grundløn")
        val tokens = lexer.tokenize()

        val identifiers = tokens.filter { it.type == TokenType.IDENTIFIER }
        assertEquals(3, identifiers.size, "Should have three identifiers")
        assertEquals("\$input", identifiers[0].lexeme)
        assertEquals("løn", identifiers[1].lexeme)
        assertEquals("grundløn", identifiers[2].lexeme)
    }

    @Test
    fun `tokenize German property with eszett`() {
        val lexer = Lexer("straße")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'straße' as identifier")
        assertEquals("straße", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize French identifier with accents`() {
        val lexer = Lexer("référence")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'référence' as identifier")
        assertEquals("référence", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Spanish identifier with tilde`() {
        val lexer = Lexer("año")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'año' as identifier")
        assertEquals("año", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Polish identifier with special characters`() {
        val lexer = Lexer("żółw")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'żółw' as identifier")
        assertEquals("żółw", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Czech identifier with caron`() {
        val lexer = Lexer("člověk")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'člověk' as identifier")
        assertEquals("člověk", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Greek identifier`() {
        val lexer = Lexer("ελληνικά")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'ελληνικά' as identifier")
        assertEquals("ελληνικά", identifierToken?.lexeme)
    }

    @Test
    fun `tokenize Cyrillic identifier`() {
        val lexer = Lexer("имя")
        val tokens = lexer.tokenize()

        val identifierToken = tokens.find { it.type == TokenType.IDENTIFIER }
        assertNotNull(identifierToken, "Should tokenize 'имя' as identifier")
        assertEquals("имя", identifierToken?.lexeme)
    }
}
