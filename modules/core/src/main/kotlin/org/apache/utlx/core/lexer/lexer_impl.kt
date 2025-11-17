package org.apache.utlx.core.lexer

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Lexer - Converts UTL-X source code into tokens
 *
 * Example input:
 * ```
 * %utlx 1.0
 * input xml
 * output json
 * ---
 * {
 *   name: input.Customer.Name
 * }
 * ```
 */
class Lexer(private val source: String) {
    private var current = 0
    private var line = 1
    private var column = 1
    private val tokens = mutableListOf<Token>()

    /**
     * Tokenize the entire source
     */
    fun tokenize(): List<Token> {
        logger.debug { "Starting tokenization, source length: ${source.length}" }

        while (!isAtEnd()) {
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line, column))
        logger.debug { "Tokenization complete, generated ${tokens.size} tokens" }
        return tokens
    }
    
    private fun scanToken() {
        val start = current
        val startColumn = column
        
        val c = advance()
        
        when (c) {
            // Whitespace
            ' ', '\r', '\t' -> { /* ignore */ }
            '\n' -> {
                line++
                column = 1
            }
            
            // Single character tokens
            '(' -> addToken(TokenType.LPAREN, start, startColumn)
            ')' -> addToken(TokenType.RPAREN, start, startColumn)
            '{' -> addToken(TokenType.LBRACE, start, startColumn)
            '}' -> addToken(TokenType.RBRACE, start, startColumn)
            '[' -> addToken(TokenType.LBRACKET, start, startColumn)
            ']' -> addToken(TokenType.RBRACKET, start, startColumn)
            ',' -> addToken(TokenType.COMMA, start, startColumn)
            ':' -> addToken(TokenType.COLON, start, startColumn)
            ';' -> addToken(TokenType.SEMICOLON, start, startColumn)
            '@' -> addToken(TokenType.AT, start, startColumn)
            '^' -> addToken(TokenType.CARET, start, startColumn)
            '$' -> {
                // $ can be followed by an identifier for input bindings ($input, $input1, etc.)
                if (peek().isLetter() || peek() == '_') {
                    current-- // back up to include $ in identifier
                    column--
                    dollarIdentifier(start, startColumn)
                } else {
                    error(start, startColumn, "Unexpected character: $ (must be followed by identifier)")
                }
            }
            '+' -> addToken(TokenType.PLUS, start, startColumn)
            '*' -> {
                if (match('*')) {
                    addToken(TokenType.STAR_STAR, start, startColumn)  // **
                } else {
                    addToken(TokenType.STAR, start, startColumn)        // *
                }
            }
            '?' -> {
                when {
                    match('.') -> addToken(TokenType.QUESTION_DOT, start, startColumn)      // ?.
                    match('?') -> addToken(TokenType.QUESTION_QUESTION, start, startColumn) // ??
                    else -> addToken(TokenType.QUESTION, start, startColumn)                // ?
                }
            }
            '%' -> {
                // Could be % operator or %utlx directive
                if (start == 0 || (start > 0 && source[start - 1] == '\n')) {
                    // Start of line, might be directive
                    if (matchWord("utlx") || matchWord("dw")) {
                        // Read the rest of the line
                        while (peek() != '\n' && !isAtEnd()) advance()
                        val directive = source.substring(start, current)
                        addToken(TokenType.PERCENT_DIRECTIVE, start, startColumn, directive)
                    } else {
                        addToken(TokenType.PERCENT, start, startColumn)
                    }
                } else {
                    addToken(TokenType.PERCENT, start, startColumn)
                }
            }
            
            // Two-character tokens
            '-' -> {
                if (match('-') && match('-')) {
                    addToken(TokenType.TRIPLE_DASH, start, startColumn)
                } else if (match('>')) {
                    addToken(TokenType.ARROW, start, startColumn)
                } else if (peek() in '0'..'9') {
                    // Negative number
                    current-- // back up
                    column--
                    number(start, startColumn)
                } else {
                    addToken(TokenType.MINUS, start, startColumn)
                }
            }
            '/' -> {
                when {
                    match('/') -> {
                        // Single-line comment
                        while (peek() != '\n' && !isAtEnd()) advance()
                        // Don't add comment tokens
                    }
                    match('*') -> {
                        // Multi-line comment
                        while (!isAtEnd()) {
                            if (peek() == '*' && peekNext() == '/') {
                                advance() // *
                                advance() // /
                                break
                            }
                            if (advance() == '\n') {
                                line++
                                column = 1
                            }
                        }
                    }
                    else -> addToken(TokenType.SLASH, start, startColumn)
                }
            }
            '.' -> {
                // Check for ... (spread) or .. (dotdot) or . (dot)
                if (peek() == '.' && peekNext() == '.') {
                    advance() // second .
                    advance() // third .
                    addToken(TokenType.SPREAD, start, startColumn)   // ...
                } else if (peek() == '.') {
                    advance() // second .
                    addToken(TokenType.DOTDOT, start, startColumn)   // ..
                } else {
                    addToken(TokenType.DOT, start, startColumn)       // .
                }
            }
            '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQ, start, startColumn)
                } else if (match('>')) {
                    addToken(TokenType.ARROW, start, startColumn)
                } else {
                    addToken(TokenType.ASSIGN, start, startColumn)
                }
            }
            '!' -> {
                if (match('=')) {
                    addToken(TokenType.NE, start, startColumn)
                } else {
                    addToken(TokenType.NOT, start, startColumn)
                }
            }
            '<' -> {
                if (match('=')) {
                    addToken(TokenType.LE, start, startColumn)
                } else {
                    addToken(TokenType.LT, start, startColumn)
                }
            }
            '>' -> {
                if (match('=')) {
                    addToken(TokenType.GE, start, startColumn)
                } else {
                    addToken(TokenType.GT, start, startColumn)
                }
            }
            '&' -> {
                if (match('&')) {
                    addToken(TokenType.AND, start, startColumn)
                } else {
                    error(start, startColumn, "Unexpected character: $c")
                }
            }
            '|' -> {
                when {
                    match('|') -> addToken(TokenType.OR, start, startColumn)
                    match('>') -> addToken(TokenType.PIPE, start, startColumn)
                    else -> error(start, startColumn, "Unexpected character: $c")
                }
            }
            
            // String literals
            '"', '\'' -> string(c, start, startColumn)
            
            // Number literals
            in '0'..'9' -> {
                current-- // back up
                column--
                number(start, startColumn)
            }
            
            // Identifiers and keywords
            in 'a'..'z', in 'A'..'Z', '_' -> {
                current-- // back up
                column--
                identifier(start, startColumn)
            }
            
            else -> error(start, startColumn, "Unexpected character: $c")
        }
    }
    
    private fun string(quote: Char, start: Int, startColumn: Int) {
        val value = StringBuilder()
        
        while (peek() != quote && !isAtEnd()) {
            if (peek() == '\n') {
                line++
                column = 1
            }
            
            if (peek() == '\\') {
                advance() // consume \
                when (val escaped = advance()) {
                    'n' -> value.append('\n')
                    't' -> value.append('\t')
                    'r' -> value.append('\r')
                    '\\' -> value.append('\\')
                    '"' -> value.append('"')
                    '\'' -> value.append('\'')
                    else -> {
                        error(start, startColumn, "Invalid escape sequence: \\$escaped")
                        value.append(escaped)
                    }
                }
            } else {
                value.append(advance())
            }
        }
        
        if (isAtEnd()) {
            error(start, startColumn, "Unterminated string")
            return
        }
        
        advance() // closing quote
        
        val lexeme = source.substring(start, current)
        addToken(TokenType.STRING, start, startColumn, value.toString())
    }
    
    private fun number(start: Int, startColumn: Int) {
        // Optional negative sign
        if (peek() == '-') advance()
        
        // Integer part
        while (peek() in '0'..'9') advance()
        
        // Fractional part
        if (peek() == '.' && peekNext() in '0'..'9') {
            advance() // consume .
            while (peek() in '0'..'9') advance()
        }
        
        // Exponent part
        if (peek() in setOf('e', 'E')) {
            advance() // consume e/E
            if (peek() in setOf('+', '-')) advance()
            while (peek() in '0'..'9') advance()
        }
        
        val lexeme = source.substring(start, current)
        val value = lexeme.toDoubleOrNull() ?: run {
            error(start, startColumn, "Invalid number: $lexeme")
            0.0
        }
        
        addToken(TokenType.NUMBER, start, startColumn, value)
    }
    
    private fun identifier(start: Int, startColumn: Int) {
        // Allow hyphens in identifiers: input-name, my-variable, etc.
        while (peek().isLetterOrDigit() || peek() == '_' || peek() == '-') advance()

        val lexeme = source.substring(start, current)
        val type = Keywords.get(lexeme) ?: TokenType.IDENTIFIER

        val literal = when (type) {
            TokenType.BOOLEAN -> lexeme == "true"
            TokenType.NULL -> null
            else -> null
        }

        addToken(type, start, startColumn, literal)
    }

    private fun dollarIdentifier(start: Int, startColumn: Int) {
        // Consume the $ character
        advance()

        // Consume the identifier part (must start with letter or _)
        if (!peek().isLetter() && peek() != '_') {
            error(start, startColumn, "$ must be followed by identifier")
            return
        }

        // Allow hyphens in dollar identifiers: $input-name, $my-variable, etc.
        while (peek().isLetterOrDigit() || peek() == '_' || peek() == '-') advance()

        val lexeme = source.substring(start, current)
        // $identifier is always treated as an IDENTIFIER token (not a keyword)
        // The lexeme includes the $ prefix
        addToken(TokenType.IDENTIFIER, start, startColumn, null)
    }
    
    private fun matchWord(word: String): Boolean {
        for (i in word.indices) {
            if (current + i >= source.length || source[current + i] != word[i]) {
                return false
            }
        }
        current += word.length
        column += word.length
        return true
    }
    
    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        
        current++
        column++
        return true
    }
    
    private fun advance(): Char {
        val c = source[current]
        current++
        column++
        return c
    }
    
    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current]
    }
    
    private fun peekNext(): Char {
        if (current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }
    
    private fun isAtEnd(): Boolean = current >= source.length
    
    private fun addToken(type: TokenType, start: Int, startColumn: Int, literal: Any? = null) {
        val lexeme = source.substring(start, current)
        tokens.add(Token(type, lexeme, literal, line, startColumn))
    }
    
    private fun error(start: Int, startColumn: Int, message: String) {
        val lexeme = if (current <= source.length) {
            source.substring(start, minOf(current + 1, source.length))
        } else {
            ""
        }
        tokens.add(Token(TokenType.ERROR, lexeme, message, line, startColumn))
    }
}
