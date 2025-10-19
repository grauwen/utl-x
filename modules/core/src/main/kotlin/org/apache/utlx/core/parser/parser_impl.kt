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

        // Parse body: can be multiple let bindings/function definitions followed by a final expression
        val allExpressions = mutableListOf<Expression>()

        // Collect all expressions (let bindings, function definitions, and final expression)
        while (!isAtEnd()) {
            val expr = parseExpression()
            allExpressions.add(expr)
        }

        // Separate let bindings (including function definitions) from other expressions
        // This allows function definitions to appear after their use (hoisting)
        val letBindings = allExpressions.filterIsInstance<Expression.LetBinding>()
        val otherExpressions = allExpressions.filter { it !is Expression.LetBinding }

        // Reorder: let bindings first, then other expressions
        val expressions = letBindings + otherExpressions

        // The body is either all expressions wrapped in a Block, or a single expression
        val body = if (expressions.size == 1) {
            expressions[0]
        } else {
            Expression.Block(expressions, Location.from(startToken))
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
            match(TokenType.DEF, TokenType.FUNCTION) -> {
                parseFunctionDefinition()
            }
            match(TokenType.IF) -> {
                parsePrefixIfExpression()
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

                // Check for attribute syntax (@key or "@key")
                var isAttribute = match(TokenType.AT)
                var key: String

                if (check(TokenType.IDENTIFIER)) {
                    key = advance().lexeme
                } else if (check(TokenType.STRING)) {
                    // Handle quoted property names like "@id" or "name"
                    key = advance().lexeme
                    // Remove quotes
                    if (key.startsWith("\"") && key.endsWith("\"")) {
                        key = key.substring(1, key.length - 1)
                    }
                    // Check if it's an attribute (starts with @)
                    if (key.startsWith("@")) {
                        isAttribute = true
                        key = key.substring(1)  // Remove @ prefix
                    }
                } else {
                    throw error("Expected property name")
                }

                consume(TokenType.COLON, "Expected ':' after property name")
                val value = parseExpression()

                properties.add(Property(key, value, Location.from(previous()), isAttribute))
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

        // Check for "in" keyword for scoped let binding: let x = value in body
        if (match(TokenType.IN)) {
            // Parse the body expression
            val body = parseExpression()

            // Desugar to lambda application: ((x) => body)(value)
            val parameter = Parameter(name, null, Location.from(startToken))
            val lambda = Expression.Lambda(listOf(parameter), body, Location.from(startToken))
            return Expression.FunctionCall(lambda, listOf(value), Location.from(startToken))
        }

        return Expression.LetBinding(name, value, Location.from(startToken))
    }

    private fun parseFunctionDefinition(): Expression {
        val startToken = previous() // DEF or FUNCTION
        val name = consume(TokenType.IDENTIFIER, "Expected function name").lexeme

        // Parse parameter list
        consume(TokenType.LPAREN, "Expected '(' after function name")
        val parameters = mutableListOf<Parameter>()

        if (!check(TokenType.RPAREN)) {
            do {
                val paramName = consume(TokenType.IDENTIFIER, "Expected parameter name").lexeme
                parameters.add(Parameter(paramName, null, Location.from(previous())))
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Expected ')' after parameters")

        // Parse body - expect { expressions... }
        consume(TokenType.LBRACE, "Expected '{' before function body")

        // Parse multiple expressions in the function body
        val bodyExpressions = mutableListOf<Expression>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            bodyExpressions.add(parseExpression())
        }

        consume(TokenType.RBRACE, "Expected '}' after function body")

        // If multiple expressions, wrap in Block; otherwise use single expression
        val body = if (bodyExpressions.size == 1) {
            bodyExpressions[0]
        } else {
            Expression.Block(bodyExpressions, Location.from(startToken))
        }

        // Desugar to: let name = (parameters) => body
        val lambda = Expression.Lambda(parameters, body, Location.from(startToken))

        return Expression.LetBinding(name, lambda, Location.from(startToken))
    }

    private fun parsePrefixIfExpression(): Expression {
        val startToken = previous() // IF
        consume(TokenType.LPAREN, "Expected '(' after 'if'")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Expected ')' after condition")
        val thenBranch = parsePrimary() // Don't use parseExpression to avoid operator precedence issues
        val elseBranch = if (match(TokenType.ELSE)) {
            parsePrefixIfOrPrimary()
        } else {
            null
        }

        return Expression.Conditional(condition, thenBranch, elseBranch, Location.from(startToken))
    }

    private fun parsePrefixIfOrPrimary(): Expression {
        return if (check(TokenType.IF)) {
            advance()
            parsePrefixIfExpression()
        } else {
            parsePrimary()
        }
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
        // Check for multi-parameter lambda: (param1, param2) -> body
        if (check(TokenType.LPAREN)) {
            val checkpoint = current
            advance() // consume '('
            
            // Try to parse parameter list
            val parameters = mutableListOf<Parameter>()
            if (!check(TokenType.RPAREN)) {
                do {
                    if (check(TokenType.IDENTIFIER)) {
                        val paramName = advance().lexeme
                        parameters.add(Parameter(paramName, null, Location.from(previous())))
                    } else {
                        // Not a parameter list, backtrack
                        current = checkpoint
                        return parseExpression()
                    }
                } while (match(TokenType.COMMA))
            }
            
            if (match(TokenType.RPAREN) && match(TokenType.ARROW)) {
                // This is a multi-parameter lambda: (param1, param2) -> body
                val body = parseExpression()
                return Expression.Lambda(parameters, body, Location.from(tokens[checkpoint]))
            } else {
                // Not a lambda, backtrack and parse as normal expression
                current = checkpoint
                return parseExpression()
            }
        }
        
        // Check if this could be a single-parameter lambda: param -> body
        if (check(TokenType.IDENTIFIER)) {
            val checkpoint = current
            val paramName = advance().lexeme
            
            if (match(TokenType.ARROW)) {
                // This is a single-parameter lambda: param -> body
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
