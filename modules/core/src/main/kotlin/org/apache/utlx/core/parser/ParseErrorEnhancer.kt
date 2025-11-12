package org.apache.utlx.core.parser

import org.apache.utlx.core.lexer.Token
import org.apache.utlx.core.lexer.TokenType

/**
 * Enhances parse error messages by analyzing the context and providing
 * more helpful suggestions based on common mistakes.
 */
object ParseErrorEnhancer {

    /**
     * Enhance a parse exception with a more helpful error message
     *
     * @param error The original parse exception
     * @param source The original UTLX source code
     * @param tokens The list of all tokens
     * @param position The position where the error occurred
     * @return Enhanced parse exception with better error message
     */
    fun enhance(error: ParseException, source: String, tokens: List<Token>, position: Int): ParseException {
        // Try to detect common patterns and provide better error messages
        val enhancedMessage = detectCommonMistakes(error.message, source, tokens, position)

        return if (enhancedMessage != null) {
            ParseException(enhancedMessage, error.location)
        } else {
            error // Return original if no enhancement found
        }
    }

    /**
     * Detect common mistake patterns and return enhanced error message
     */
    private fun detectCommonMistakes(originalMessage: String?, source: String, tokens: List<Token>, position: Int): String? {
        if (originalMessage == null) return null

        // Pattern: "Expected ')'" - often caused by fn() wrapper in lambda expressions
        // e.g., filter($data, fn(x) => x.value > 10) should be filter($data, x => x.value > 10)
        if (originalMessage.contains("Expected ')'")) {
            val fnLambdaError = detectFnWrapperInLambda(tokens, position, source)
            if (fnLambdaError != null) return fnLambdaError
        }

        // Pattern: "Expected ':' after property name"
        // Common mistake: Unquoted hyphenated property name (e.g., order-ide instead of "order-ide")
        if (originalMessage.contains("Expected ':' after property name")) {
            return detectHyphenatedPropertyName(tokens, position)
        }

        // Pattern: Using @ instead of $ for multi-input references
        // e.g., @input2.field instead of $input2.field
        if (originalMessage.contains("Undefined variable") ||
            originalMessage.contains("not found") ||
            originalMessage.contains("Expected")) {
            return detectAtSignInsteadOfDollar(tokens, position, originalMessage)
        }

        return null
    }

    /**
     * Detect hyphenated property names that should be quoted
     * Example: { order-ide: value } should be { "order-ide": value }
     */
    private fun detectHyphenatedPropertyName(tokens: List<Token>, errorPosition: Int): String? {
        // Look backward from error position to find IDENTIFIER followed by MINUS
        if (errorPosition < 2) return null

        val prevToken = tokens.getOrNull(errorPosition - 1)
        val currentToken = tokens.getOrNull(errorPosition)

        // Check if we have IDENTIFIER followed by MINUS (indicating hyphenated name)
        if (prevToken?.type == TokenType.IDENTIFIER && currentToken?.type == TokenType.MINUS) {
            // Try to collect the full hyphenated name
            var suggestedName = prevToken.lexeme
            var pos = errorPosition

            // Collect pattern: IDENTIFIER - IDENTIFIER - IDENTIFIER ...
            while (pos < tokens.size - 1) {
                if (tokens[pos].type == TokenType.MINUS &&
                    tokens.getOrNull(pos + 1)?.type == TokenType.IDENTIFIER) {
                    suggestedName += "-${tokens[pos + 1].lexeme}"
                    pos += 2
                } else {
                    break
                }
            }

            return "Property names with hyphens must be quoted. Did you mean \"$suggestedName\"?"
        }

        return null
    }

    /**
     * Detect fn() wrapper in lambda expressions (UTLX-001)
     * Example: filter($data, fn(x) => x.value > 10) should be filter($data, x => x.value > 10)
     *
     * This is one of the most common errors when coming from languages that use 'fn' or 'function' keywords.
     */
    private fun detectFnWrapperInLambda(tokens: List<Token>, errorPosition: Int, source: String): String? {
        // Look backward for pattern: IDENTIFIER("fn") LEFT_PAREN
        // This indicates someone tried to use fn(param) => expression syntax

        var pos = errorPosition - 1
        var foundFn = false
        var paramName: String? = null

        // Search backward up to 10 tokens for the pattern
        while (pos >= 0 && errorPosition - pos < 10) {
            val token = tokens.getOrNull(pos)

            // Found "fn" identifier
            if (token?.type == TokenType.IDENTIFIER && token.lexeme == "fn") {
                foundFn = true

                // Try to extract parameter name (should be after LPAREN)
                var nextPos = pos + 1
                if (tokens.getOrNull(nextPos)?.type == TokenType.LPAREN) {
                    nextPos++
                    val paramToken = tokens.getOrNull(nextPos)
                    if (paramToken?.type == TokenType.IDENTIFIER) {
                        paramName = paramToken.lexeme
                    }
                }
                break
            }

            pos--
        }

        if (foundFn) {
            val suggestion = if (paramName != null) {
                "filter(${'$'}collection, $paramName => ...)"
            } else {
                "filter(${'$'}collection, param => ...)"
            }

            return """
                |Invalid lambda syntax: 'fn()' wrapper not allowed in UTLX (UTLX-001)
                |
                |UTLX uses arrow function syntax without the 'fn' keyword.
                |
                |❌ Incorrect: filter(${'$'}data, fn(x) => x.value > 10)
                |✅ Correct:   filter(${'$'}data, x => x.value > 10)
                |
                |Suggestion: Use 'parameter => expression' syntax
                |Example: $suggestion
                |
                |See: https://utlx-lang.org/docs/functions#lambda-expressions
            """.trimMargin()
        }

        return null
    }

    /**
     * Detect using @ instead of $ for input references in multi-input scenarios
     * Example: @input2.field should be $input2.field
     */
    private fun detectAtSignInsteadOfDollar(tokens: List<Token>, errorPosition: Int, originalMessage: String): String? {
        // Look for AT token followed by IDENTIFIER that looks like an input name
        val token = tokens.getOrNull(errorPosition)

        if (token?.type == TokenType.AT) {
            val nextToken = tokens.getOrNull(errorPosition + 1)
            if (nextToken?.type == TokenType.IDENTIFIER) {
                val name = nextToken.lexeme

                // Common input name patterns: input, input1, input2, data1, etc.
                if (name.matches(Regex("^(input|data|order|customer|product)\\d*$"))) {
                    return "Use '\$' instead of '@' to reference inputs. Did you mean \$$name instead of @$name?\n" +
                           "Hint: '@' is for XML attributes, '\$' is for input references."
                }
            }
        }

        return null
    }
}
