package org.apache.utlx.core.lexer

/**
 * Token types for UTL-X language
 */
enum class TokenType {
    // Literals
    STRING,          // "hello", 'world'
    NUMBER,          // 42, 3.14, 1e10
    BOOLEAN,         // true, false
    NULL,            // null
    
    // Identifiers and keywords
    IDENTIFIER,      // variable names, function names
    
    // Keywords
    INPUT,           // input
    OUTPUT,          // output
    TEMPLATE,        // template
    MATCH,           // match
    FUNCTION,        // function
    LET,             // let
    IF,              // if
    ELSE,            // else
    MAP,             // map (can also be identifier)
    FILTER,          // filter
    REDUCE,          // reduce
    APPLY,           // apply
    IMPORT,          // import
    AS,              // as
    AUTO,            // auto (for format detection)
    
    // Format types
    XML,             // xml
    JSON,            // json
    CSV,             // csv
    YAML,            // yaml
    
    // Operators
    PLUS,            // +
    MINUS,           // -
    STAR,            // *
    SLASH,           // /
    PERCENT,         // %
    
    // Comparison
    EQ,              // ==
    NE,              // !=
    LT,              // <
    LE,              // <=
    GT,              // >
    GE,              // >=
    
    // Logical
    AND,             // &&
    OR,              // ||
    NOT,             // !
    
    // Assignment and pipes
    ASSIGN,          // =
    ARROW,           // =>
    PIPE,            // |>
    
    // Delimiters
    LPAREN,          // (
    RPAREN,          // )
    LBRACE,          // {
    RBRACE,          // }
    LBRACKET,        // [
    RBRACKET,        // ]
    
    // Punctuation
    COMMA,           // ,
    DOT,             // .
    COLON,           // :
    SEMICOLON,       // ;
    AT,              // @ (for attributes)
    DOTDOT,          // .. (recursive descent)
    
    // Special
    PERCENT_DIRECTIVE, // %utlx, %dw (at start of file)
    TRIPLE_DASH,     // --- (separator between header and body)
    COMMENT,         // // comment
    
    // End of file
    EOF,
    
    // Error token
    ERROR
}

/**
 * Token with position information
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int,
    val column: Int
) {
    override fun toString(): String {
        return if (literal != null) {
            "Token($type, '$lexeme', $literal, $line:$column)"
        } else {
            "Token($type, '$lexeme', $line:$column)"
        }
    }
    
    fun isKeyword(): Boolean = type in setOf(
        TokenType.INPUT, TokenType.OUTPUT, TokenType.TEMPLATE, TokenType.MATCH,
        TokenType.FUNCTION, TokenType.LET, TokenType.IF, TokenType.ELSE,
        TokenType.MAP, TokenType.FILTER, TokenType.REDUCE, TokenType.APPLY,
        TokenType.IMPORT, TokenType.AS, TokenType.AUTO,
        TokenType.XML, TokenType.JSON, TokenType.CSV, TokenType.YAML
    )
    
    fun isOperator(): Boolean = type in setOf(
        TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT,
        TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE,
        TokenType.AND, TokenType.OR, TokenType.NOT
    )
    
    fun isDelimiter(): Boolean = type in setOf(
        TokenType.LPAREN, TokenType.RPAREN, TokenType.LBRACE, TokenType.RBRACE,
        TokenType.LBRACKET, TokenType.RBRACKET
    )
}

/**
 * Keywords map for efficient lookup
 */
object Keywords {
    private val keywords = mapOf(
        "input" to TokenType.INPUT,
        "output" to TokenType.OUTPUT,
        "template" to TokenType.TEMPLATE,
        "match" to TokenType.MATCH,
        "function" to TokenType.FUNCTION,
        "let" to TokenType.LET,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "map" to TokenType.MAP,
        "filter" to TokenType.FILTER,
        "reduce" to TokenType.REDUCE,
        "apply" to TokenType.APPLY,
        "import" to TokenType.IMPORT,
        "as" to TokenType.AS,
        "true" to TokenType.BOOLEAN,
        "false" to TokenType.BOOLEAN,
        "null" to TokenType.NULL,
        "auto" to TokenType.AUTO,
        "xml" to TokenType.XML,
        "json" to TokenType.JSON,
        "csv" to TokenType.CSV,
        "yaml" to TokenType.YAML
    )
    
    fun get(identifier: String): TokenType? = keywords[identifier]
    
    fun isKeyword(identifier: String): Boolean = keywords.containsKey(identifier)
}
