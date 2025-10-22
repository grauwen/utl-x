package org.apache.utlx.core.parser

import mu.KotlinLogging
import org.apache.utlx.core.ast.*
import org.apache.utlx.core.lexer.Token
import org.apache.utlx.core.lexer.TokenType

private val logger = KotlinLogging.logger {}

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
        logger.debug { "Starting parse, ${tokens.size} tokens" }

        return try {
            val program = parseProgram()
            if (errors.isEmpty()) {
                logger.debug { "Parse completed successfully" }
                ParseResult.Success(program)
            } else {
                logger.warn { "Parse completed with ${errors.size} error(s)" }
                ParseResult.Failure(errors)
            }
        } catch (e: ParseException) {
            logger.error(e) { "Parse exception: ${e.message}" }
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

        // Parse inputs: either "input xml" or "input: input1 xml, input2 json"
        if (!match(TokenType.INPUT)) {
            error("Expected 'input' declaration")
        }
        val inputs = parseInputsOrOutputs(isInput = true)

        // Parse outputs: either "output json" or "output: summary json, details xml"
        if (!match(TokenType.OUTPUT)) {
            error("Expected 'output' declaration")
        }
        val outputs = parseInputsOrOutputs(isInput = false)

        return Header(version, inputs, outputs, Location.from(startToken))
    }

    /**
     * Parse input or output declarations.
     * Supports:
     * - Single: "xml" → [("input"/"output", FormatSpec)]
     * - Multiple with colon: ": input1 xml, input2 json" → [(name1, spec1), (name2, spec2)]
     */
    private fun parseInputsOrOutputs(isInput: Boolean): List<Pair<String, FormatSpec>> {
        val defaultName = if (isInput) "input" else "output"

        // Check if there's a colon (multiple named inputs/outputs)
        if (match(TokenType.COLON)) {
            val result = mutableListOf<Pair<String, FormatSpec>>()

            // Parse comma-separated list: input1 xml, input2 json, input3 csv
            do {
                // Stop if we hit the output/--- separator
                if (check(TokenType.OUTPUT) || check(TokenType.TRIPLE_DASH) || isAtEnd()) {
                    break
                }

                // Parse: name format {options}
                val name = consume(TokenType.IDENTIFIER, "Expected input/output name").lexeme
                val formatSpec = parseFormatSpec()
                result.add(name to formatSpec)

                // Continue if comma-separated
            } while (match(TokenType.COMMA))

            if (result.isEmpty()) {
                error("Expected at least one input/output declaration after colon")
            }

            return result
        } else {
            // Single input/output (backward compatible): "xml" → [("input", spec)]
            val formatSpec = parseFormatSpec()
            return listOf(defaultName to formatSpec)
        }
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
                    match(TokenType.LBRACKET) -> {
                        // Parse array of strings (for now, only string arrays)
                        val arrayItems = mutableListOf<String>()
                        if (!check(TokenType.RBRACKET)) {
                            do {
                                val item = consume(TokenType.STRING, "Expected string in array").literal as String
                                arrayItems.add(item)
                            } while (match(TokenType.COMMA))
                        }
                        consume(TokenType.RBRACKET, "Expected ']'")
                        arrayItems
                    }
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
        var expr = parseTernary()

        if (match(TokenType.IF)) {
            val ifToken = previous()
            val condition = parseTernary()

            val elseBranch = if (match(TokenType.ELSE)) {
                parseConditional()
            } else null

            expr = Expression.Conditional(condition, expr, elseBranch, Location.from(ifToken))
        }

        return expr
    }

    private fun parseTernary(): Expression {
        var expr = parseNullishCoalesce()

        // Ternary operator: condition ? thenExpr : elseExpr
        if (match(TokenType.QUESTION)) {
            val questionToken = previous()
            val thenExpr = parseNullishCoalesce()  // Parse at same level to avoid : ambiguity with object literals
            consume(TokenType.COLON, "Expected ':' after then-expression in ternary operator")
            val elseExpr = parseTernary()  // Right-associative: a ? b : c ? d : e = a ? b : (c ? d : e)
            expr = Expression.Ternary(expr, thenExpr, elseExpr, Location.from(questionToken))
        }

        return expr
    }

    private fun parseNullishCoalesce(): Expression {
        var expr = parseLogicalOr()

        while (match(TokenType.QUESTION_QUESTION)) {
            val operator = previous()
            val right = parseLogicalOr()
            expr = Expression.BinaryOp(expr, BinaryOperator.NULLISH_COALESCE, right, Location.from(operator))
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
        var expr = parseExponentiation()

        while (true) {
            val op = when {
                match(TokenType.STAR) -> BinaryOperator.MULTIPLY
                match(TokenType.SLASH) -> BinaryOperator.DIVIDE
                match(TokenType.PERCENT) -> BinaryOperator.MODULO
                else -> break
            }
            val operator = previous()
            val right = parseExponentiation()
            expr = Expression.BinaryOp(expr, op, right, Location.from(operator))
        }

        return expr
    }

    private fun parseExponentiation(): Expression {
        var expr = parseUnary()

        // Right-associative: 2^3^2 = 2^(3^2) = 512, not (2^3)^2 = 64
        if (match(TokenType.STAR_STAR)) {
            val operator = previous()
            val right = parseExponentiation()  // Right-recursive for right-associativity
            expr = Expression.BinaryOp(expr, BinaryOperator.EXPONENT, right, Location.from(operator))
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
                match(TokenType.QUESTION_DOT) -> {
                    // Safe navigation: obj?.property
                    val property = consume(TokenType.IDENTIFIER, "Expected property name after '?.'").lexeme
                    Expression.SafeNavigation(expr, property, Location.from(previous()))
                }
                match(TokenType.DOT) -> {
                    val isAttribute = match(TokenType.AT)
                    // Allow wildcard selector: .* or .@*
                    val property = if (match(TokenType.STAR)) {
                        "*"
                    } else {
                        consume(TokenType.IDENTIFIER, "Expected property name").lexeme
                    }
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
            match(TokenType.MATCH) -> {
                parseMatchExpression()
            }
            match(TokenType.TRY) -> {
                parseTryCatchExpression()
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
        val letBindings = mutableListOf<Expression.LetBinding>()

        logger.trace { "parseObjectLiteral: start, current=${peek().type}" }

        if (!check(TokenType.RBRACE)) {
            // First, parse all let bindings and function definitions (they come before properties)
            while (match(TokenType.LET) || match(TokenType.FUNCTION) || match(TokenType.DEF)) {
                val tokenType = previous().type
                logger.trace { "parseObjectLiteral: parsing ${tokenType}" }

                val letExpr = when (tokenType) {
                    TokenType.LET -> parseLetBinding()
                    TokenType.FUNCTION, TokenType.DEF -> parseFunctionDefinition()
                    else -> error("Unexpected token: $tokenType")
                }

                letBindings.add(letExpr as Expression.LetBinding)

                // In blocks, let bindings and function definitions should be terminated with semicolon or comma
                // to prevent ambiguity with subsequent expressions (especially arrays)
                // If there are let bindings and more content follows, require separator
                if (!check(TokenType.RBRACE) &&
                    !check(TokenType.LET) &&
                    !check(TokenType.FUNCTION) &&
                    !check(TokenType.DEF)) {
                    // More content follows that's not another let/function - require separator
                    if (!match(TokenType.SEMICOLON) && !match(TokenType.COMMA)) {
                        throw error("Expected ';' or ',' after let binding or function definition in block expression")
                    }
                } else {
                    // Optional separator between let bindings/functions or before closing brace
                    match(TokenType.COMMA) || match(TokenType.SEMICOLON)
                }
            }

            logger.trace { "parseObjectLiteral: parsed ${letBindings.size} let bindings, current=${peek().type}" }

            // Check if this is a block expression (let bindings + expression) or object literal
            // Block expression: { let x = ...; expression }
            // Object literal: { let x = ...; prop: value }
            if (letBindings.isNotEmpty() && !check(TokenType.RBRACE)) {
                // Lookahead to check if next token pattern is "identifier :"
                // If so, it's an object literal property. Otherwise, it's a block expression.
                val isObjectLiteral = when {
                    check(TokenType.AT) -> true  // @attr: value
                    check(TokenType.STRING) -> {
                        // Could be "prop": value (object) or just a string expression (block)
                        // Need to look ahead for colon
                        val checkpoint = current
                        advance() // consume string
                        val hasColon = check(TokenType.COLON)
                        current = checkpoint // backtrack
                        hasColon
                    }
                    check(TokenType.IDENTIFIER) -> {
                        // Could be prop: value (object) or identifier expression (block)
                        val checkpoint = current
                        advance() // consume identifier
                        val hasColon = check(TokenType.COLON)
                        current = checkpoint // backtrack
                        hasColon
                    }
                    else -> false  // Anything else (array, number, etc.) is a block expression
                }

                if (!isObjectLiteral) {
                    // This is a block expression: { let x = ...; expression }
                    logger.trace { "parseObjectLiteral: detected block expression, parsing final expression" }
                    val finalExpression = parseExpression()
                    consume(TokenType.RBRACE, "Expected '}' after block expression")

                    // Return a Block expression with let bindings + final expression
                    val allExpressions = letBindings + finalExpression
                    logger.debug { "parseObjectLiteral: created block with ${letBindings.size} let bindings + ${finalExpression::class.simpleName}" }
                    return Expression.Block(allExpressions, Location.from(startToken))
                }
            }

            // Then parse properties (if any)
            if (!check(TokenType.RBRACE)) {
                do {
                    // Check for spread syntax: ...expression
                    if (match(TokenType.SPREAD)) {
                        val spreadToken = previous()
                        val spreadExpr = parseExpression()
                        properties.add(Property(null, spreadExpr, Location.from(spreadToken), isAttribute = false, isSpread = true))
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
                        throw error("Expected property name or spread operator")
                    }

                    consume(TokenType.COLON, "Expected ':' after property name")
                    val value = parseExpression()

                    properties.add(Property(key, value, Location.from(previous()), isAttribute))
                } while (match(TokenType.COMMA))
            }
        }

        consume(TokenType.RBRACE, "Expected '}' after object properties")

        return Expression.ObjectLiteral(properties, letBindings, Location.from(startToken))
    }
    
    private fun parseArrayLiteral(): Expression {
        val startToken = previous() // LBRACKET
        val elements = mutableListOf<Expression>()

        if (!check(TokenType.RBRACKET)) {
            do {
                // Check for spread syntax: ...expression
                if (match(TokenType.SPREAD)) {
                    val spreadToken = previous()
                    val spreadExpr = parseExpression()
                    elements.add(Expression.SpreadElement(spreadExpr, Location.from(spreadToken)))
                } else {
                    elements.add(parseExpression())
                }
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RBRACKET, "Expected ']' after array elements")

        return Expression.ArrayLiteral(elements, Location.from(startToken))
    }
    
    private fun parseLetBinding(): Expression {
        val startToken = previous() // LET
        val name = consume(TokenType.IDENTIFIER, "Expected variable name").lexeme

        // Parse optional type annotation: let x: Type = value
        val typeAnnotation = if (match(TokenType.COLON)) {
            parseTypeAnnotation()
        } else {
            null
        }

        consume(TokenType.ASSIGN, "Expected '=' after variable name")
        val value = parseExpression()

        // Check for "in" keyword for scoped let binding: let x = value in body
        if (match(TokenType.IN)) {
            // Parse the body expression
            val body = parseExpression()

            // Desugar to lambda application: ((x) => body)(value)
            val parameter = Parameter(name, typeAnnotation, Location.from(startToken))
            val lambda = Expression.Lambda(listOf(parameter), body, null, Location.from(startToken))
            return Expression.FunctionCall(lambda, listOf(value), Location.from(startToken))
        }

        return Expression.LetBinding(name, value, typeAnnotation, Location.from(startToken))
    }

    private fun parseFunctionDefinition(): Expression {
        val startToken = previous() // DEF or FUNCTION
        val nameToken = consume(TokenType.IDENTIFIER, "Expected function name")
        val name = nameToken.lexeme

        // Validate user-defined function naming: must start with uppercase letter (PascalCase)
        // This prevents collisions with stdlib functions (which all use lowercase/camelCase)
        if (name.isEmpty() || !name[0].isUpperCase()) {
            val suggestion = if (name.isNotEmpty()) {
                name.replaceFirstChar { it.uppercase() }
            } else {
                "MyFunction"
            }
            error(
                "User-defined functions must start with uppercase letter (PascalCase). " +
                "Got: '$name'. Try: '$suggestion'. " +
                "This prevents collisions with stdlib functions which use lowercase/camelCase."
            )
        }

        // Parse parameter list
        consume(TokenType.LPAREN, "Expected '(' after function name")
        val parameters = mutableListOf<Parameter>()

        if (!check(TokenType.RPAREN)) {
            do {
                val paramName = consume(TokenType.IDENTIFIER, "Expected parameter name").lexeme

                // Parse optional type annotation: paramName: Type
                val paramType = if (match(TokenType.COLON)) {
                    parseTypeAnnotation()
                } else {
                    null
                }

                parameters.add(Parameter(paramName, paramType, Location.from(previous())))
            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RPAREN, "Expected ')' after parameters")

        // Parse optional return type annotation: function Foo(): ReturnType
        val returnType = if (match(TokenType.COLON)) {
            parseTypeAnnotation()
        } else {
            null
        }

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
        val lambda = Expression.Lambda(parameters, body, returnType, Location.from(startToken))

        return Expression.LetBinding(name, lambda, returnType, Location.from(startToken))
    }

    private fun parsePrefixIfExpression(): Expression {
        val startToken = previous() // IF
        consume(TokenType.LPAREN, "Expected '(' after 'if'")
        val condition = parseExpression()
        consume(TokenType.RPAREN, "Expected ')' after condition")
        // Parse up to LogicalOr level to allow binary operations but not nested conditionals
        val thenBranch = parseLogicalOr()
        val elseBranch = if (match(TokenType.ELSE)) {
            parsePrefixIfOrLogicalOr()
        } else {
            null
        }

        return Expression.Conditional(condition, thenBranch, elseBranch, Location.from(startToken))
    }

    private fun parsePrefixIfOrLogicalOr(): Expression {
        return if (check(TokenType.IF)) {
            advance()
            parsePrefixIfExpression()
        } else {
            parseLogicalOr()
        }
    }

    private fun parseMatchExpression(): Expression {
        val startToken = previous() // MATCH

        // Parse match value: match (expression) { ... }
        consume(TokenType.LPAREN, "Expected '(' after 'match'")
        val value = parseExpression()
        consume(TokenType.RPAREN, "Expected ')' after match value")

        // Parse match cases: { pattern [if guard] => expression, ... }
        consume(TokenType.LBRACE, "Expected '{' before match cases")

        val cases = mutableListOf<MatchCase>()

        if (!check(TokenType.RBRACE)) {
            do {
                val caseStartToken = peek()

                // Parse pattern
                val pattern = parsePattern()

                // Parse optional guard: if expression
                val guard = if (match(TokenType.IF)) {
                    parseExpression()
                } else {
                    null
                }

                // Parse arrow: =>
                consume(TokenType.ARROW, "Expected '=>' after match pattern")

                // Parse result expression
                val expression = parseExpression()

                cases.add(MatchCase(pattern, guard, expression, Location.from(caseStartToken)))

            } while (match(TokenType.COMMA))
        }

        consume(TokenType.RBRACE, "Expected '}' after match cases")

        return Expression.Match(value, cases, Location.from(startToken))
    }

    private fun parsePattern(): Pattern {
        val token = peek()

        return when {
            match(TokenType.NUMBER) -> {
                val value = previous().literal as Double
                Pattern.Literal(value, Location.from(previous()))
            }
            match(TokenType.STRING) -> {
                val value = previous().literal as String
                Pattern.Literal(value, Location.from(previous()))
            }
            match(TokenType.BOOLEAN) -> {
                val value = previous().literal as Boolean
                Pattern.Literal(value, Location.from(previous()))
            }
            match(TokenType.NULL) -> {
                Pattern.Literal(null, Location.from(previous()))
            }
            match(TokenType.IDENTIFIER) -> {
                val name = previous().lexeme
                // Check if it's a wildcard pattern
                if (name == "_") {
                    Pattern.Wildcard(Location.from(previous()))
                } else {
                    Pattern.Variable(name, Location.from(previous()))
                }
            }
            else -> {
                throw ParseException("Expected pattern (literal, identifier, or '_')", Location.from(token))
            }
        }
    }

    private fun parseTryCatchExpression(): Expression {
        val startToken = previous() // TRY

        // Parse try block: try { expression }
        consume(TokenType.LBRACE, "Expected '{' after 'try'")
        val tryBlock = parseExpression()
        consume(TokenType.RBRACE, "Expected '}' after try block")

        // Parse catch: catch (errorVar) { expression } or catch { expression }
        consume(TokenType.CATCH, "Expected 'catch' after try block")

        // Optional error variable: catch (e) or just catch
        var errorVariable: String? = null
        if (match(TokenType.LPAREN)) {
            errorVariable = consume(TokenType.IDENTIFIER, "Expected error variable name").lexeme
            consume(TokenType.RPAREN, "Expected ')' after error variable")
        }

        // Parse catch block
        consume(TokenType.LBRACE, "Expected '{' before catch block")
        val catchBlock = parseExpression()
        consume(TokenType.RBRACE, "Expected '}' after catch block")

        return Expression.TryCatch(tryBlock, errorVariable, catchBlock, Location.from(startToken))
    }

    /**
     * Parse type annotation
     *
     * Grammar:
     *   type ::= union-type
     *   union-type ::= nullable-type ('|' nullable-type)*
     *   nullable-type ::= primary-type '?'?
     *   primary-type ::= 'String' | 'Number' | 'Boolean' | 'Null' | 'Date' | 'Any'
     *                 | 'Array' '<' type '>'
     */
    private fun parseTypeAnnotation(): Type {
        return parseUnionType()
    }

    private fun parseUnionType(): Type {
        var left = parseNullableType()

        while (match(TokenType.PIPE)) {
            val right = parseNullableType()
            // Flatten unions: Type.Union(List(left, right))
            val types = mutableListOf<Type>()
            if (left is Type.Union) {
                types.addAll(left.types)
            } else {
                types.add(left)
            }
            if (right is Type.Union) {
                types.addAll(right.types)
            } else {
                types.add(right)
            }
            left = Type.Union(types)
        }

        return left
    }

    private fun parseNullableType(): Type {
        val baseType = parsePrimaryType()

        return if (match(TokenType.QUESTION)) {
            Type.Nullable(baseType)
        } else {
            baseType
        }
    }

    private fun parsePrimaryType(): Type {
        if (!check(TokenType.IDENTIFIER)) {
            throw ParseException("Expected type name", Location.from(peek()))
        }

        val typeName = advance().lexeme

        return when (typeName) {
            "String" -> Type.String
            "Number" -> Type.Number
            "Boolean" -> Type.Boolean
            "Null" -> Type.Null
            "Date" -> Type.Date
            "Any" -> Type.Any
            "Array" -> {
                // Array<ElementType>
                consume(TokenType.LT, "Expected '<' after 'Array'")
                val elementType = parseTypeAnnotation()
                consume(TokenType.GT, "Expected '>' after array element type")
                Type.Array(elementType)
            }
            else -> throw ParseException("Unknown type: $typeName", Location.from(previous()))
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

                        // Parse optional type annotation: paramName: Type
                        val paramType = if (match(TokenType.COLON)) {
                            parseTypeAnnotation()
                        } else {
                            null
                        }

                        parameters.add(Parameter(paramName, paramType, Location.from(previous())))
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
                return Expression.Lambda(parameters, body, null, Location.from(tokens[checkpoint]))
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
                return Expression.Lambda(listOf(parameter), body, null, Location.from(tokens[checkpoint]))
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
