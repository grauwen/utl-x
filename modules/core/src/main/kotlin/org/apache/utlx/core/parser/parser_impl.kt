package org.apache.utlx.core.parser

import org.apache.utlx.core.ast.*
import org.apache.utlx.core.lexer.Token
import org.apache.utlx.core.lexer.TokenType

/**
 * Recursive descent parser for UTL-X
 * 
 * Grammar (simplified):
 * program     -> header TRIPLE_DASH expression
 * header      -> PERCENT_DIRECTIVE version input output
 * expression  -> assignment
 * assignment  -> IDENTIFIER ASSIGN assignment | pipe
 * pipe        -> conditional (PIPE conditional)*
 * conditional -> logical_or (IF logical_or ELSE conditional)?
 * logical_or  -> logical_and (OR logical_and)*
 * logical_and -> equality (AND equality)*
 * equality    -> comparison ((EQ | NE) comparison)*
 * comparison  -> term ((LT | LE | GT | GE) term)*
 * term        -> factor ((PLUS | MINUS) factor)*
 * factor      -> unary ((STAR | SLASH | PERCENT) unary)*
 * unary       -> (NOT | MINUS) unary | postfix
 * postfix     -> primary (DOT IDENTIFIER | LBRACKET expression RBRACKET | LPAREN arguments RPAREN)*
 * primary     -> NUMBER | STRING | BOOLEAN | NULL | IDENTIFIER
 *              | LPAREN expression RPAREN
 *              | LBRACE properties RBRACE
 *              | LBRACKET elements RBRACKET
 */
class Parser(private val tokens: List<Token>) {
    private var current = 0
    private val errors = mutableListOf<ParseError>()
    
    /**
     * Parse the token stream into an AST
     */
    fun parse(): ParseResult {
        return try {
            val program = parseProgram()
            if (errors.isEmpty()) {
                ParseResult.Success(program)
            } else {
                ParseResult.Failure(errors)
            }
        } catch (e: ParseException) {
            ParseResult.Failure(errors + ParseError(e.message ?: "Parse error", e.location))
        }
    }
    
    private fun parseProgram(): Program {
        val startToken = peek()
        val header = parseHeader()
        
        // Expect --- separator
        if (!match(TokenType.TRIPLE_DASH)) {
            error("Expected '---' separator after header")
        }
        
        val body = parseExpression()
        
        if (!isAtEnd()) {
            error("Unexpected tokens after program body")
        }
        
        return Program(header, body, Location.from(startToken))
    }
    
    private fun parseHeader(): Header {
        val startToken = peek()
        
        // %utlx 1.0
        if (!match(TokenType.PERCENT_DIRECTIVE)) {
            error("Expected UTL-X directive (e.g., %utlx 1.0)")
        }
        val version = previous().lexeme.substringAfter(' ').trim()
        
        // input <format>
        if (!match(TokenType.INPUT)) {
            error("Expected 'input' declaration")
        }
        val inputFormat = parseFormatSpec()
        
        // output <format>
        if (!match(TokenType.OUTPUT)) {
            error("Expected 'output' declaration")
        }
        val outputFormat = parseFormatSpec()
        
        return Header(version, inputFormat, outputFormat, Location.from(startToken))
    }
    
    private fun parseFormatSpec(): FormatSpec {
        val startToken = peek()
        
        val formatType = when {
            match(TokenType.AUTO) -> FormatType.AUTO
            match(TokenType.XML) -> FormatType.XML
            match(TokenType.JSON) -> FormatType.JSON
            match(TokenType.CSV) -> FormatType.CSV
            match(TokenType.YAML) -> FormatType.YAML
            else -> {
                error("Expected format type (auto, xml, json, csv, yaml)")
                FormatType.AUTO
            }
        }
        
        // Optional format options: { key: value, ... }
        val options = if (check(TokenType.LBRACE)) {
            parseFormatOptions()
        } else {
            emptyMap()
        }
        
        return FormatSpec(formatType, options, Location.from(startToken))
    }
    
    private fun parseFormatOptions(): Map<String, Any> {
        consume(TokenType.LBRACE, "Expected '{'")
        
        val options = mutableMapOf<String, Any>()
        
        if (!check(TokenType.RBRACE)) {
            do {
                val key = consume(TokenType.IDENTIFIER, "Expected option name").lexeme
                consume(TokenType.COLON, "Expected ':'")
                
                val value = when {
                    match(TokenType.STRING) -> previous().literal as String
                    match(TokenType.NUMBER) -> previous().literal as Double
                    match(TokenType.BOOLEAN) -> previous().literal as Boolean
                    else -> error("Expected option value")
                }
                
                options[key] = value!!
            } while (match(TokenType.COMMA))
        }
        
        consume(TokenType.RBRACE, "Expected '}'")
        return options
    }
    
    private fun parseExpression(): Expression {
        return parsePipe()
    }
    
    private fun parsePipe(): Expression {
        var expr = parseConditional()
        
        while (match(TokenType.PIPE)) {
            val operator = previous()
            val right = parseConditional()
            expr = Expression.Pipe(expr, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseConditional(): Expression {
        var expr = parseLogicalOr()
        
        if (match(TokenType.IF)) {
            val ifToken = previous()
            val condition = parseLogicalOr()
            
            val elseBranch = if (match(TokenType.ELSE)) {
                parseConditional()
            } else null
            
            expr = Expression.Conditional(condition, expr, elseBranch, Location.from(ifToken))
        }
        
        return expr
    }
    
    private fun parseLogicalOr(): Expression {
        var expr = parseLogicalAnd()
        
        while (match(TokenType.OR)) {
            val operator = previous()
            val right = parseLogicalAnd()
            expr = Expression.BinaryOp(expr, BinaryOperator.OR, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseLogicalAnd(): Expression {
        var expr = parseEquality()
        
        while (match(TokenType.AND)) {
            val operator = previous()
            val right = parseEquality()
            expr = Expression.BinaryOp(expr, BinaryOperator.AND, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseEquality(): Expression {
        var expr = parseComparison()
        
        while (true) {
            val op = when {
                match(TokenType.EQ) -> BinaryOperator.EQUAL
                match(TokenType.NE) -> BinaryOperator.NOT_EQUAL
                else -> break
            }
            val operator = previous()
            val right = parseComparison()
            expr = Expression.BinaryOp(expr, op, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseComparison(): Expression {
        var expr = parseTerm()
        
        while (true) {
            val op = when {
                match(TokenType.LT) -> BinaryOperator.LESS_THAN
                match(TokenType.LE) -> BinaryOperator.LESS_EQUAL
                match(TokenType.GT) -> BinaryOperator.GREATER_THAN
                match(TokenType.GE) -> BinaryOperator.GREATER_EQUAL
                else -> break
            }
            val operator = previous()
            val right = parseTerm()
            expr = Expression.BinaryOp(expr, op, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseTerm(): Expression {
        var expr = parseFactor()
        
        while (true) {
            val op = when {
                match(TokenType.PLUS) -> BinaryOperator.PLUS
                match(TokenType.MINUS) -> BinaryOperator.MINUS
                else -> break
            }
            val operator = previous()
            val right = parseFactor()
            expr = Expression.BinaryOp(expr, op, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseFactor(): Expression {
        var expr = parseUnary()
        
        while (true) {
            val op = when {
                match(TokenType.STAR) -> BinaryOperator.MULTIPLY
                match(TokenType.SLASH) -> BinaryOperator.DIVIDE
                match(TokenType.PERCENT) -> BinaryOperator.MODULO
                else -> break
            }
            val operator = previous()
            val right = parseUnary()
            expr = Expression.BinaryOp(expr, op, right, Location.from(operator))
        }
        
        return expr
    }
    
    private fun parseUnary(): Expression {
        return when {
            match(TokenType.NOT) -> {
                val operator = previous()
                val operand = parseUnary()
                Expression.UnaryOp(UnaryOperator.NOT, operand, Location.from(operator))
            }
            match(TokenType.MINUS) -> {
                val operator = previous()
                val operand = parseUnary()
                Expression.UnaryOp(UnaryOperator.MINUS, operand, Location.from(operator))
            }
            else -> parsePostfix()
        }
    }
    
    private fun parsePostfix(): Expression {
        var expr = parsePrimary()
        
        while (true) {
            expr = when {
                match(TokenType.DOT) -> {
                    val isAttribute = match(TokenType.AT)
                    val property = consume(TokenType.IDENTIFIER, "Expected property name").lexeme
                    Expression.MemberAccess(expr, property, isAttribute, Location.from(previous()))
                }
                match(TokenType.LBRACKET) -> {
                    val index = parseExpression()
                    consume(TokenType.RBRACKET, "Expected ']'")
                    Expression.IndexAccess(expr, index, Location.from(previous()))
                }
                match(TokenType.LPAREN) -> {
                    val args = parseArguments()
                    consume(TokenType.RPAREN, "Expected ')'")
                    Expression.FunctionCall(expr, args, Location.from(previous()))
                }
                else -> break
            }
        }
        
        return expr
    }
    
    private fun parsePrimary(): Expression {
        val token = peek()
        
        return when {
            match(TokenType.NUMBER) -> {
                Expression.NumberLiteral(previous().literal as Double, Location.from(previous()))
            }
            match(TokenType.STRING) -> {
                Expression.StringLiteral(previous().literal as String, Location.from(previous()))
            }
            match(TokenType.BOOLEAN) -> {
                Expression.BooleanLiteral(previous().literal as Boolean, Location.from(previous()))
            }
            match(TokenType.NULL) -> {
                Expression.NullLiteral(Location.from(previous()))
            }
            match(TokenType.IDENTIFIER) -> {
                Expression.Identifier(previous().lexeme, Location.from(previous()))
            }
            // Allow certain keywords to be used as identifiers in expressions
            match(TokenType.INPUT, TokenType.OUTPUT, TokenType.MAP, TokenType.FILTER, TokenType.REDUCE) -> {
                Expression.Identifier(previous().lexeme, Location.from(previous()))
            }
            match(TokenType.LPAREN) -> {
                val expr = parseExpression()
                consume(TokenType.RPAREN, "Expected ')' after expression")
                expr
            }
            match(TokenType.LBRACE) -> {
                parseObjectLiteral()
            }
            match(TokenType.LBRACKET) -> {
                parseArrayLiteral()
            }
            match(TokenType.LET) -> {
                parseLetBinding()
            }
            match(TokenType.AT) -> {
                // Handle @input or @variable
                val atToken = previous()
                val name = when {
                    check(TokenType.IDENTIFIER) -> {
                        advance().lexeme
                    }
                    check(TokenType.INPUT) -> {
                        advance().lexeme
                    }
                    check(TokenType.OUTPUT) -> {
                        advance().lexeme
                    }
                    else -> {
                        error("Expected variable name after '@'")
                    }
                }
                Expression.Identifier(name, Location.from(atToken))
            }
            else -> {
                throw ParseException("Expected expression", Location.from(token))
            }
        }
    }
    
    private fun parseObjectLiteral(): Expression {
        val startToken = previous() // LBRACE
        val properties = mutableListOf<Property>()
        
        if (!check(TokenType.RBRACE)) {
            do {
                // Handle 'let' bindings in object context
                if (check(TokenType.LET)) {
                    val letExpr = parseLetBinding()
                    // Add let binding as a special property
                    properties.add(Property(
                        (letExpr as Expression.LetBinding).name,
                        letExpr.value,
                        letExpr.location
                    ))
                    continue
                }
                
                val key = consume(TokenType.IDENTIFIER, "Expected property name").lexeme
                consume(TokenType.COLON, "Expected ':' after property name")
                val value = parseExpression()
                
                properties.add(Property(key, value, Location.from(previous())))
            } while (match(TokenType.COMMA))
        }
        
        consume(TokenType.RBRACE, "Expected '}' after object properties")
        
        return Expression.ObjectLiteral(properties, Location.from(startToken))
    }
    
    private fun parseArrayLiteral(): Expression {
        val startToken = previous() // LBRACKET
        val elements = mutableListOf<Expression>()
        
        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(parseExpression())
            } while (match(TokenType.COMMA))
        }
        
        consume(TokenType.RBRACKET, "Expected ']' after array elements")
        
        return Expression.ArrayLiteral(elements, Location.from(startToken))
    }
    
    private fun parseLetBinding(): Expression {
        val startToken = previous() // LET
        val name = consume(TokenType.IDENTIFIER, "Expected variable name").lexeme
        consume(TokenType.ASSIGN, "Expected '=' after variable name")
        val value = parseExpression()
        
        return Expression.LetBinding(name, value, Location.from(startToken))
    }
    
    private fun parseArguments(): List<Expression> {
        val args = mutableListOf<Expression>()
        
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(parseArgument())
            } while (match(TokenType.COMMA))
        }
        
        return args
    }
    
    private fun parseArgument(): Expression {
        // Check if this could be a lambda parameter (identifier followed by ->)
        if (check(TokenType.IDENTIFIER)) {
            val checkpoint = current
            val paramName = advance().lexeme
            
            if (match(TokenType.ARROW)) {
                // This is a lambda: param -> body
                val parameter = Parameter(paramName, null, Location.from(tokens[checkpoint]))
                val body = parseExpression()
                return Expression.Lambda(listOf(parameter), body, Location.from(tokens[checkpoint]))
            } else {
                // Not a lambda, backtrack and parse as normal expression
                current = checkpoint
                return parseExpression()
            }
        }
        
        return parseExpression()
    }
    
    // Utility functions
    
    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }
    
    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }
    
    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }
    
    private fun isAtEnd(): Boolean {
        return peek().type == TokenType.EOF
    }
    
    private fun peek(): Token {
        return tokens[current]
    }
    
    private fun previous(): Token {
        return tokens[current - 1]
    }
    
    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw ParseException(message, Location.from(peek()))
    }
    
    private fun error(message: String): Nothing {
        errors.add(ParseError(message, Location.from(peek())))
        throw ParseException(message, Location.from(peek()))
    }
}

/**
 * Parse results
 */
sealed class ParseResult {
    data class Success(val program: Program) : ParseResult()
    data class Failure(val errors: List<ParseError>) : ParseResult()
}

/**
 * Parse error with location
 */
data class ParseError(val message: String, val location: Location)

/**
 * Exception thrown during parsing
 */
class ParseException(message: String, val location: Location) : Exception(message)
