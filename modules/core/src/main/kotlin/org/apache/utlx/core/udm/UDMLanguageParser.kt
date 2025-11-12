package org.apache.utlx.core.udm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Simple recursive descent parser for UDM Language (.udm files)
 *
 * Parses .udm format back into UDM objects, enabling round-trip serialization:
 * UDM → .udm (via UDMLanguageSerializer) → UDM (via this parser)
 *
 * This is a hand-written parser (not using ANTLR) for simplicity and better error messages.
 *
 * Usage:
 * ```kotlin
 * val udmString = File("example.udm").readText()
 * val udm = UDMLanguageParser.parse(udmString)
 * ```
 */
object UDMLanguageParser {

    /**
     * Parse a .udm format string into a UDM object
     *
     * @param input The .udm format string
     * @return The parsed UDM object
     * @throws UDMParseException if parsing fails
     */
    fun parse(input: String): UDM {
        val tokens = tokenize(input)
        val parser = Parser(tokens)
        return parser.parseFile()
    }

    /**
     * Tokenize the input string
     */
    private fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        var line = 1
        var col = 1

        fun peek(offset: Int = 0): Char? =
            if (pos + offset < input.length) input[pos + offset] else null

        fun advance(): Char {
            val ch = input[pos]
            pos++
            if (ch == '\n') {
                line++
                col = 1
            } else {
                col++
            }
            return ch
        }

        while (pos < input.length) {
            val ch = peek()!!

            when {
                // Whitespace
                ch.isWhitespace() -> {
                    advance()
                }

                // Comments
                ch == '/' && peek(1) == '/' -> {
                    while (peek() != null && peek() != '\n') advance()
                }

                // Strings
                ch == '"' -> {
                    val start = pos
                    advance() // skip "
                    val sb = StringBuilder()
                    while (peek() != '"') {
                        if (peek() == null) throw UDMParseException("Unterminated string at line $line:$col")
                        if (peek() == '\\') {
                            advance()
                            when (peek()) {
                                'n' -> { advance(); sb.append('\n') }
                                'r' -> { advance(); sb.append('\r') }
                                't' -> { advance(); sb.append('\t') }
                                '\\' -> { advance(); sb.append('\\') }
                                '"' -> { advance(); sb.append('"') }
                                else -> throw UDMParseException("Invalid escape sequence at line $line:$col")
                            }
                        } else {
                            sb.append(advance())
                        }
                    }
                    advance() // skip closing "
                    tokens.add(Token(TokenType.STRING, sb.toString(), line, col))
                }

                // Numbers
                ch.isDigit() || (ch == '-' && peek(1)?.isDigit() == true) -> {
                    val sb = StringBuilder()
                    if (ch == '-') sb.append(advance())
                    while (peek()?.isDigit() == true) sb.append(advance())
                    if (peek() == '.') {
                        sb.append(advance())
                        while (peek()?.isDigit() == true) sb.append(advance())
                    }
                    if (peek() == 'e' || peek() == 'E') {
                        sb.append(advance())
                        if (peek() == '+' || peek() == '-') sb.append(advance())
                        while (peek()?.isDigit() == true) sb.append(advance())
                    }
                    tokens.add(Token(TokenType.NUMBER, sb.toString(), line, col))
                }

                // Identifiers and keywords
                ch.isLetter() || ch == '@' || ch == '_' -> {
                    val sb = StringBuilder()
                    while (peek()?.let { it.isLetterOrDigit() || it == '_' || it == '-' || it == '@' } == true) {
                        sb.append(advance())
                    }
                    val text = sb.toString()
                    val type = when (text) {
                        "true", "false" -> TokenType.BOOLEAN
                        "null" -> TokenType.NULL
                        else -> TokenType.IDENTIFIER
                    }
                    tokens.add(Token(type, text, line, col))
                }

                // Punctuation
                ch == '{' -> { advance(); tokens.add(Token(TokenType.LEFT_BRACE, "{", line, col)) }
                ch == '}' -> { advance(); tokens.add(Token(TokenType.RIGHT_BRACE, "}", line, col)) }
                ch == '[' -> { advance(); tokens.add(Token(TokenType.LEFT_BRACKET, "[", line, col)) }
                ch == ']' -> { advance(); tokens.add(Token(TokenType.RIGHT_BRACKET, "]", line, col)) }
                ch == '(' -> { advance(); tokens.add(Token(TokenType.LEFT_PAREN, "(", line, col)) }
                ch == ')' -> { advance(); tokens.add(Token(TokenType.RIGHT_PAREN, ")", line, col)) }
                ch == ':' -> { advance(); tokens.add(Token(TokenType.COLON, ":", line, col)) }
                ch == ',' -> { advance(); tokens.add(Token(TokenType.COMMA, ",", line, col)) }
                ch == '<' -> { advance(); tokens.add(Token(TokenType.LEFT_ANGLE, "<", line, col)) }
                ch == '>' -> { advance(); tokens.add(Token(TokenType.RIGHT_ANGLE, ">", line, col)) }

                else -> throw UDMParseException("Unexpected character '$ch' at line $line:$col")
            }
        }

        tokens.add(Token(TokenType.EOF, "", line, col))
        return tokens
    }

    private enum class TokenType {
        STRING, NUMBER, BOOLEAN, NULL, IDENTIFIER,
        LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
        LEFT_PAREN, RIGHT_PAREN, COLON, COMMA,
        LEFT_ANGLE, RIGHT_ANGLE, EOF
    }

    private data class Token(val type: TokenType, val value: String, val line: Int, val col: Int)

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        private fun peek(offset: Int = 0): Token =
            if (pos + offset < tokens.size) tokens[pos + offset] else tokens.last()

        private fun advance(): Token = tokens[pos++]

        private fun expect(type: TokenType): Token {
            val token = peek()
            if (token.type != type) {
                throw UDMParseException("Expected $type but found ${token.type} at line ${token.line}:${token.col}")
            }
            return advance()
        }

        fun parseFile(): UDM {
            // Parse header - skip all @ directives at file level
            while (peek().type == TokenType.IDENTIFIER && peek().value.startsWith("@")) {
                val directive = peek().value
                // Skip header directives: @udm-version, @source, @parsed-at, etc.
                if (directive.startsWith("@udm") ||
                    directive.startsWith("@source") ||
                    directive.startsWith("@parsed")) {
                    advance() // skip directive
                    if (peek().type == TokenType.COLON) {
                        advance() // skip colon
                        advance() // skip value
                    }
                } else {
                    // Not a header directive, break and parse as value
                    break
                }
            }

            // Parse main value
            return parseValue()
        }

        private fun parseValue(): UDM {
            val token = peek()

            return when (token.type) {
                TokenType.STRING -> UDM.Scalar(advance().value)
                TokenType.NUMBER -> {
                    val text = advance().value
                    UDM.Scalar(if (text.contains('.')) text.toDouble() else text.toInt())
                }
                TokenType.BOOLEAN -> UDM.Scalar(advance().value.toBoolean())
                TokenType.NULL -> { advance(); UDM.Scalar(null) }
                TokenType.LEFT_BRACKET -> parseArray()
                TokenType.LEFT_BRACE -> parseObject()
                TokenType.IDENTIFIER -> {
                    when {
                        token.value.startsWith("@DateTime") -> parseDateTime()
                        token.value.startsWith("@Date") -> parseDate()
                        token.value.startsWith("@LocalDateTime") -> parseLocalDateTime()
                        token.value.startsWith("@Time") -> parseTime()
                        token.value.startsWith("@Binary") -> parseBinary()
                        token.value.startsWith("@Lambda") -> parseLambda()
                        token.value.startsWith("@Object") -> parseAnnotatedObject()
                        token.value.startsWith("@Scalar") -> parseAnnotatedScalar()
                        else -> throw UDMParseException("Unexpected identifier: ${token.value} at line ${token.line}:${token.col}")
                    }
                }
                else -> throw UDMParseException("Unexpected token: ${token.type} at line ${token.line}:${token.col}")
            }
        }

        private fun parseArray(): UDM {
            expect(TokenType.LEFT_BRACKET)
            val elements = mutableListOf<UDM>()

            while (peek().type != TokenType.RIGHT_BRACKET) {
                elements.add(parseValue())
                if (peek().type == TokenType.COMMA) advance()
            }

            expect(TokenType.RIGHT_BRACKET)
            return UDM.Array(elements)
        }

        private fun parseObject(): UDM {
            expect(TokenType.LEFT_BRACE)
            val properties = mutableMapOf<String, UDM>()

            // Check if this has attributes/properties sections
            var hasAttributesSection = false
            var hasPropertiesSection = false
            var lookAheadPos = pos

            while (lookAheadPos < tokens.size && tokens[lookAheadPos].type != TokenType.RIGHT_BRACE) {
                if (tokens[lookAheadPos].value == "attributes") hasAttributesSection = true
                if (tokens[lookAheadPos].value == "properties") hasPropertiesSection = true
                lookAheadPos++
            }

            if (hasAttributesSection || hasPropertiesSection) {
                // Parse structured object
                val attributes = mutableMapOf<String, String>()

                if (peek().value == "attributes") {
                    advance() // skip "attributes"
                    expect(TokenType.COLON)
                    expect(TokenType.LEFT_BRACE)

                    while (peek().type != TokenType.RIGHT_BRACE) {
                        val key = expect(TokenType.IDENTIFIER).value
                        expect(TokenType.COLON)
                        val value = when (peek().type) {
                            TokenType.STRING -> advance().value
                            TokenType.NUMBER -> advance().value
                            TokenType.BOOLEAN -> advance().value
                            else -> "null"
                        }
                        attributes[key] = value
                        if (peek().type == TokenType.COMMA) advance()
                    }

                    expect(TokenType.RIGHT_BRACE)
                    if (peek().type == TokenType.COMMA) advance()
                }

                if (peek().value == "properties") {
                    advance() // skip "properties"
                    expect(TokenType.COLON)
                    expect(TokenType.LEFT_BRACE)

                    while (peek().type != TokenType.RIGHT_BRACE) {
                        val key = expect(TokenType.IDENTIFIER).value
                        expect(TokenType.COLON)
                        properties[key] = parseValue()
                        if (peek().type == TokenType.COMMA) advance()
                    }

                    expect(TokenType.RIGHT_BRACE)
                }

                expect(TokenType.RIGHT_BRACE)
                return UDM.Object(attributes = attributes, properties = properties)
            } else {
                // Parse simple object
                while (peek().type != TokenType.RIGHT_BRACE) {
                    val key = expect(TokenType.IDENTIFIER).value
                    expect(TokenType.COLON)
                    properties[key] = parseValue()
                    if (peek().type == TokenType.COMMA) advance()
                }

                expect(TokenType.RIGHT_BRACE)
                return UDM.Object(properties = properties)
            }
        }

        private fun parseAnnotatedObject(): UDM {
            expect(TokenType.IDENTIFIER) // @Object
            var name: String? = null
            val metadata = mutableMapOf<String, String>()

            if (peek().type == TokenType.LEFT_PAREN) {
                advance()

                while (peek().type != TokenType.RIGHT_PAREN) {
                    val key = expect(TokenType.IDENTIFIER).value
                    expect(TokenType.COLON)

                    when (key) {
                        "name" -> name = expect(TokenType.STRING).value
                        "metadata" -> {
                            expect(TokenType.LEFT_BRACE)
                            while (peek().type != TokenType.RIGHT_BRACE) {
                                val metaKey = expect(TokenType.IDENTIFIER).value
                                expect(TokenType.COLON)
                                val metaValue = expect(TokenType.STRING).value
                                metadata[metaKey] = metaValue
                                if (peek().type == TokenType.COMMA) advance()
                            }
                            expect(TokenType.RIGHT_BRACE)
                        }
                        else -> {
                            // Skip unknown metadata
                            parseValue()
                        }
                    }

                    if (peek().type == TokenType.COMMA) advance()
                }

                expect(TokenType.RIGHT_PAREN)
            }

            // Parse body
            val body = parseObject() as UDM.Object

            return UDM.Object(
                name = name,
                metadata = metadata,
                attributes = body.attributes,
                properties = body.properties
            )
        }

        private fun parseAnnotatedScalar(): UDM {
            advance() // skip @Scalar
            if (peek().type == TokenType.LEFT_ANGLE) {
                advance() // skip <
                advance() // skip type name
                advance() // skip >
            }
            expect(TokenType.LEFT_PAREN)
            val value = parseValue()
            expect(TokenType.RIGHT_PAREN)
            return value
        }

        private fun parseDateTime(): UDM {
            advance() // skip @DateTime
            expect(TokenType.LEFT_PAREN)
            val value = expect(TokenType.STRING).value
            expect(TokenType.RIGHT_PAREN)
            return UDM.DateTime(Instant.parse(value))
        }

        private fun parseDate(): UDM {
            advance() // skip @Date
            expect(TokenType.LEFT_PAREN)
            val value = expect(TokenType.STRING).value
            expect(TokenType.RIGHT_PAREN)
            return UDM.Date(LocalDate.parse(value))
        }

        private fun parseLocalDateTime(): UDM {
            advance() // skip @LocalDateTime
            expect(TokenType.LEFT_PAREN)
            val value = expect(TokenType.STRING).value
            expect(TokenType.RIGHT_PAREN)
            return UDM.LocalDateTime(LocalDateTime.parse(value))
        }

        private fun parseTime(): UDM {
            advance() // skip @Time
            expect(TokenType.LEFT_PAREN)
            val value = expect(TokenType.STRING).value
            expect(TokenType.RIGHT_PAREN)
            return UDM.Time(LocalTime.parse(value))
        }

        private fun parseBinary(): UDM {
            advance() // skip @Binary
            expect(TokenType.LEFT_PAREN)
            // Skip size info
            while (peek().type != TokenType.RIGHT_PAREN) advance()
            expect(TokenType.RIGHT_PAREN)
            return UDM.Binary(ByteArray(0))
        }

        private fun parseLambda(): UDM {
            advance() // skip @Lambda
            expect(TokenType.LEFT_PAREN)
            expect(TokenType.RIGHT_PAREN)
            return UDM.Lambda { _: List<UDM> -> UDM.Scalar(null) }
        }
    }
}

/**
 * Exception thrown when UDM parsing fails
 */
class UDMParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
