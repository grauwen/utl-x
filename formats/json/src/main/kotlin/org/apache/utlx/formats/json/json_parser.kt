package org.apache.utlx.formats.json

import org.apache.utlx.core.udm.UDM
import java.io.Reader
import java.io.StringReader

/**
 * JSON Parser - Converts JSON strings to UDM
 * 
 * Supports all JSON types:
 * - null → UDM.Scalar(null)
 * - boolean → UDM.Scalar(boolean)
 * - number → UDM.Scalar(number)
 * - string → UDM.Scalar(string)
 * - array → UDM.Array
 * - object → UDM.Object
 */
class JSONParser(private val source: Reader) {
    private var current = 0
    private var line = 1
    private var column = 1
    private val text = source.readText()
    
    constructor(json: String) : this(StringReader(json))
    
    /**
     * Parse JSON to UDM
     */
    fun parse(): UDM {
        // RFC 8259: JSON text MAY begin with a BOM (U+FEFF)
        // Strip it if present (though discouraged, parsers must tolerate it)
        if (!isAtEnd() && peek() == '\uFEFF') {
            advance()
        }

        skipWhitespace()
        val result = parseValue()
        skipWhitespace()

        if (!isAtEnd()) {
            throw JSONParseException("Unexpected content after JSON value", line, column)
        }

        return result
    }
    
    private fun parseValue(): UDM {
        skipWhitespace()
        
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw JSONParseException("Unexpected character: ${peek()}", line, column)
        }
    }
    
    private fun parseObject(): UDM.Object {
        val properties = mutableMapOf<String, UDM>()
        
        consume('{', "Expected '{'")
        skipWhitespace()
        
        if (peek() != '}') {
            do {
                skipWhitespace()
                
                // Parse key (must be string)
                if (peek() != '"') {
                    throw JSONParseException("Object key must be a string", line, column)
                }
                val key = parseStringValue()
                
                skipWhitespace()
                consume(':', "Expected ':' after object key")
                skipWhitespace()
                
                // Parse value
                val value = parseValue()
                properties[key] = value
                
                skipWhitespace()
            } while (match(','))
        }
        
        skipWhitespace()
        consume('}', "Expected '}'")
        
        return UDM.Object(properties)
    }
    
    private fun parseArray(): UDM.Array {
        val elements = mutableListOf<UDM>()
        
        consume('[', "Expected '['")
        skipWhitespace()
        
        if (peek() != ']') {
            do {
                skipWhitespace()
                elements.add(parseValue())
                skipWhitespace()
            } while (match(','))
        }
        
        skipWhitespace()
        consume(']', "Expected ']'")
        
        return UDM.Array(elements)
    }
    
    private fun parseString(): UDM.Scalar {
        return UDM.Scalar.string(parseStringValue())
    }
    
    private fun parseStringValue(): String {
        consume('"', "Expected '\"'")
        val sb = StringBuilder()
        
        while (peek() != '"' && !isAtEnd()) {
            when (val c = peek()) {
                '\\' -> {
                    advance()
                    when (peek()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            advance()
                            val hex = buildString {
                                repeat(4) {
                                    if (!isAtEnd()) append(advance())
                                }
                            }
                            try {
                                val codePoint = hex.toInt(16)
                                sb.append(codePoint.toChar())
                            } catch (e: NumberFormatException) {
                                throw JSONParseException("Invalid unicode escape: \\u$hex", line, column)
                            }
                            continue
                        }
                        else -> throw JSONParseException("Invalid escape sequence: \\${peek()}", line, column)
                    }
                    advance()
                }
                else -> {
                    sb.append(c)
                    advance()
                }
            }
        }
        
        if (isAtEnd()) {
            throw JSONParseException("Unterminated string", line, column)
        }
        
        consume('"', "Expected closing '\"'")
        return sb.toString()
    }
    
    private fun parseNumber(): UDM.Scalar {
        val start = current
        
        // Optional minus
        if (peek() == '-') advance()
        
        // Integer part
        if (peek() == '0') {
            advance()
        } else if (peek() in '1'..'9') {
            while (peek() in '0'..'9') advance()
        } else {
            throw JSONParseException("Invalid number", line, column)
        }
        
        // Fractional part
        if (peek() == '.') {
            advance()
            if (peek() !in '0'..'9') {
                throw JSONParseException("Digit expected after decimal point", line, column)
            }
            while (peek() in '0'..'9') advance()
        }
        
        // Exponent part
        if (peek() in setOf('e', 'E')) {
            advance()
            if (peek() in setOf('+', '-')) advance()
            if (peek() !in '0'..'9') {
                throw JSONParseException("Digit expected in exponent", line, column)
            }
            while (peek() in '0'..'9') advance()
        }
        
        val numStr = text.substring(start, current)

        // Try integer first (preserves Int64 precision)
        if (!numStr.contains('.') && !numStr.contains('e') && !numStr.contains('E')) {
            numStr.toLongOrNull()?.let { return UDM.Scalar.number(it) }
        }

        // Fall back to Double for decimals and scientific notation
        val value = numStr.toDoubleOrNull()
            ?: throw JSONParseException("Invalid number: $numStr", line, column)

        return UDM.Scalar.number(value)
    }
    
    private fun parseBoolean(): UDM.Scalar {
        return when {
            matchWord("true") -> UDM.Scalar.boolean(true)
            matchWord("false") -> UDM.Scalar.boolean(false)
            else -> throw JSONParseException("Invalid boolean", line, column)
        }
    }
    
    private fun parseNull(): UDM.Scalar {
        if (!matchWord("null")) {
            throw JSONParseException("Invalid null", line, column)
        }
        return UDM.Scalar.nullValue()
    }
    
    private fun skipWhitespace() {
        while (!isAtEnd()) {
            when (peek()) {
                ' ', '\r', '\t' -> advance()
                '\n' -> {
                    advance()
                    line++
                    column = 1
                }
                else -> return
            }
        }
    }
    
    private fun match(expected: Char): Boolean {
        if (peek() != expected) return false
        advance()
        return true
    }
    
    private fun matchWord(word: String): Boolean {
        for (i in word.indices) {
            if (current + i >= text.length || text[current + i] != word[i]) {
                return false
            }
        }
        current += word.length
        column += word.length
        return true
    }
    
    private fun consume(expected: Char, message: String) {
        if (peek() != expected) {
            throw JSONParseException(message, line, column)
        }
        advance()
    }
    
    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return text[current]
    }
    
    private fun advance(): Char {
        val c = text[current]
        current++
        column++
        return c
    }
    
    private fun isAtEnd(): Boolean = current >= text.length
}

/**
 * JSON parse exception
 */
class JSONParseException(
    message: String,
    val line: Int,
    val column: Int
) : Exception("JSON parse error at $line:$column - $message")

/**
 * Streaming JSON parser for large files
 * Parses JSON incrementally to avoid loading entire file into memory
 */
class StreamingJSONParser(private val reader: Reader) {
    private val buffer = CharArray(8192)
    private var bufferPos = 0
    private var bufferSize = 0
    private var line = 1
    private var column = 1
    
    /**
     * Parse streaming JSON
     * Currently delegates to regular parser, but can be optimized for streaming
     */
    fun parse(): UDM {
        // For large files, we could implement true streaming
        // For now, read all and use regular parser
        val content = reader.readText()
        return JSONParser(content).parse()
    }
    
    // Future: implement true streaming with events
    // fun parseStream(handler: JSONStreamHandler)
}

/**
 * JSON stream handler for event-based parsing
 */
interface JSONStreamHandler {
    fun onObjectStart()
    fun onObjectEnd()
    fun onArrayStart()
    fun onArrayEnd()
    fun onKey(key: String)
    fun onValue(value: UDM)
}
