#!/bin/bash
# Create sample source files to demonstrate structure

echo "📝 Creating sample source files..."

# Core: Lexer sample
cat > modules/core/src/main/kotlin/org/apache/utlx/core/lexer/Token.kt << 'EOF'
package org.apache.utlx.core.lexer

/**
 * Represents a token in the UTL-X language.
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int
)

enum class TokenType {
    // Keywords
    LET, FUNCTION, IF, ELSE, MATCH, TEMPLATE, APPLY,
    
    // Literals
    STRING, NUMBER, BOOLEAN, NULL,
    
    // Identifiers
    IDENTIFIER,
    
    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQUALS_EQUALS, NOT_EQUALS,
    LESS, GREATER, LESS_EQUALS, GREATER_EQUALS,
    
    // Punctuation
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACE, RIGHT_BRACE,
    LEFT_BRACKET, RIGHT_BRACKET,
    COMMA, COLON, SEMICOLON,
    
    // Special
    PIPE, AT, DOT, QUESTION_DOT, QUESTION_QUESTION,
    ARROW, TRIPLE_DASH,
    
    // End of file
    EOF
}
EOF

echo "✅ Created Token.kt"

# JVM: API sample
cat > modules/jvm/src/main/kotlin/org/apache/utlx/jvm/api/UTLXEngine.kt << 'EOF'
package org.apache.utlx.jvm.api

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Main entry point for UTL-X transformations on the JVM.
 */
class UTLXEngine private constructor(
    private val compiledTransform: CompiledTransform
) {
    
    /**
     * Transform string input to string output.
     */
    fun transform(input: String): String {
        // Implementation
        TODO("Not yet implemented")
    }
    
    /**
     * Transform with specific output format.
     */
    fun transform(input: String, outputFormat: Format): String {
        // Implementation
        TODO("Not yet implemented")
    }
    
    /**
     * Transform streams (for large files).
     */
    fun transform(input: InputStream, output: OutputStream) {
        // Implementation
        TODO("Not yet implemented")
    }
    
    /**
     * Transform with multiple named inputs.
     */
    fun transformMultiple(inputs: Map<String, String>): String {
        // Implementation
        TODO("Not yet implemented")
    }
    
    companion object {
        fun builder(): Builder = Builder()
    }
    
    class Builder {
        private var source: String? = null
        
        fun compile(file: File): Builder {
            this.source = file.readText()
            return this
        }
        
        fun compile(source: String): Builder {
            this.source = source
            return this
        }
        
        fun build(): UTLXEngine {
            requireNotNull(source) { "Source must be provided" }
            // Compile the source
            val compiled = CompiledTransform()
            return UTLXEngine(compiled)
        }
    }
}

class CompiledTransform
enum class Format { JSON, XML, CSV, YAML, AUTO }
EOF

echo "✅ Created UTLXEngine.kt"

# CLI: Main sample
cat > modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt << 'EOF'
package org.apache.utlx.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class UTLXCommand : CliktCommand(
    name = "utlx",
    help = "Universal Transformation Language Extended"
) {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    UTLXCommand()
        .subcommands(
            TransformCommand(),
            ValidateCommand(),
            VersionCommand()
        )
        .main(args)
}
EOF

echo "✅ Created Main.kt"

# Test sample
cat > modules/core/src/test/kotlin/org/apache/utlx/core/lexer/TokenTest.kt << 'EOF'
package org.apache.utlx.core.lexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TokenTest {
    
    @Test
    fun `should create token`() {
        val token = Token(
            type = TokenType.IDENTIFIER,
            lexeme = "test",
            line = 1,
            column = 1
        )
        
        assertEquals(TokenType.IDENTIFIER, token.type)
        assertEquals("test", token.lexeme)
    }
}
EOF

echo "✅ Created TokenTest.kt"

# JavaScript sample
cat > modules/javascript/src/api/index.ts << 'EOF'
/**
 * Main entry point for UTL-X JavaScript runtime
 */

export interface Engine {
  transform(input: string): string;
  transformMultiple(inputs: Record<string, string>): string;
}

export interface CompileOptions {
  optimize?: boolean;
}

export function compile(source: string, options?: CompileOptions): Engine {
  // Implementation
  throw new Error('Not yet implemented');
}

export function version(): string {
  return '1.0.0';
}
EOF

echo "✅ Created index.ts"

echo "🎉 Sample files created!"
