package org.apache.utlx.formats.xml

import org.apache.utlx.core.udm.UDM
import java.io.Reader
import java.io.StringReader

/**
 * XML Parser - Converts XML to UDM
 * 
 * Mapping:
 * - XML Elements → UDM.Object
 * - Attributes → UDM.Object attributes map
 * - Text content → UDM.Scalar (when leaf element)
 * - Multiple same-name children → UDM.Array
 * 
 * Example:
 * <Order id="123">
 *   <Customer>Alice</Customer>
 *   <Total>299.99</Total>
 * </Order>
 * 
 * Becomes:
 * UDM.Object(
 *   properties = { "Customer": Scalar("Alice"), "Total": Scalar(299.99) },
 *   attributes = { "id": "123" }
 * )
 */
class XMLParser(private val source: Reader) {
    private var current = 0
    private var line = 1
    private var column = 1
    private val text = source.readText()
    
    constructor(xml: String) : this(StringReader(xml))
    
    /**
     * Parse XML to UDM
     */
    fun parse(): UDM {
        skipWhitespace()

        // Skip XML declaration if present
        if (peek(5) == "<?xml") {
            skipUntil("?>")
            advance() // ?
            advance() // >
            skipWhitespace()
        }

        // Parse root element with empty namespace context
        val root = parseElement(emptyMap())

        skipWhitespace()
        if (!isAtEnd()) {
            throw XMLParseException("Content after root element", line, column)
        }

        return root
    }
    
    private fun parseElement(parentNamespaces: Map<String, String>): UDM {
        consume('<', "Expected '<'")

        val name = parseName()
        val attributes = mutableMapOf<String, String>()

        // Parse attributes
        skipWhitespace()
        while (peek() != '>' && peek() != '/') {
            val attrName = parseName()
            skipWhitespace()
            consume('=', "Expected '=' after attribute name")
            skipWhitespace()
            val attrValue = parseAttributeValue()
            attributes[attrName] = attrValue
            skipWhitespace()
        }

        // Build complete namespace context by merging parent namespaces with element's declarations
        val namespaces = mutableMapOf<String, String>()
        namespaces.putAll(parentNamespaces)  // Inherit from parent

        // Add new namespace declarations from this element
        for ((attrName, attrValue) in attributes) {
            if (attrName == "xmlns" || attrName.startsWith("xmlns:")) {
                namespaces[attrName] = attrValue
            }
        }

        // Merge namespaces into attributes so they're accessible from this element
        val mergedAttributes = attributes.toMutableMap()
        mergedAttributes.putAll(namespaces)

        // Self-closing tag
        if (peek() == '/') {
            advance() // /
            consume('>', "Expected '>'")
            return UDM.Object(emptyMap(), mergedAttributes, name)
        }

        consume('>', "Expected '>'")

        // Parse content (text, child elements, or mixed)
        val children = mutableListOf<Pair<String, UDM>>()
        val textContent = StringBuilder()

        while (true) {
            skipWhitespace()

            if (peek(2) == "</") {
                // End tag
                break
            } else if (peek() == '<') {
                if (peek(4) == "<!--") {
                    skipComment()
                } else if (peek(9) == "<![CDATA[") {
                    val cdata = parseCDATA()
                    textContent.append(cdata)
                } else {
                    // Child element - pass down namespace context
                    val child = parseElement(namespaces)
                    val childName = (child as? UDM.Object)?.name ?: "element"
                    children.add(childName to child)
                }
            } else if (!isAtEnd() && peek() != '<') {
                // Text content
                textContent.append(parseText())
            } else {
                break
            }
        }

        // Parse end tag
        consume('<', "Expected '<'")
        consume('/', "Expected '/'")
        val endName = parseName()
        if (endName != name) {
            throw XMLParseException("End tag </$endName> doesn't match start tag <$name>", line, column)
        }
        skipWhitespace()
        consume('>', "Expected '>'")

        // Build UDM based on content
        val content = textContent.toString().trim()

        // Check if element has real attributes (not just inherited xmlns declarations)
        val hasRealAttributes = attributes.isNotEmpty()

        return when {
            children.isEmpty() && content.isEmpty() -> {
                // Empty element
                UDM.Object(emptyMap(), mergedAttributes, name)
            }
            children.isEmpty() && content.isNotEmpty() -> {
                // Leaf element with text only
                val textValue = tryParseNumber(content) ?: UDM.Scalar.string(content)
                // If there are REAL attributes (not just inherited xmlns), wrap as object with _text property
                // Otherwise, return the text value directly for easier access
                if (hasRealAttributes) {
                    UDM.Object(mapOf("_text" to textValue), mergedAttributes, name)
                } else {
                    // No real attributes - return text directly, but wrap in an object to preserve element name
                    UDM.Object(mapOf("_text" to textValue), mergedAttributes, name)
                }
            }
            else -> {
                // Element with children
                val properties = mutableMapOf<String, UDM>()

                // Group children by name
                val grouped = children.groupBy { it.first }
                grouped.forEach { (childName, childElements) ->
                    properties[childName] = if (childElements.size == 1) {
                        childElements[0].second
                    } else {
                        // Multiple elements with same name → array
                        UDM.Array(childElements.map { it.second })
                    }
                }

                // Add text content if any (mixed content)
                if (content.isNotEmpty()) {
                    properties["_text"] = UDM.Scalar.string(content)
                }

                UDM.Object(properties, mergedAttributes, name)
            }
        }
    }
    
    private fun parseName(): String {
        val sb = StringBuilder()
        
        // XML name: [A-Za-z_:][A-Za-z0-9_:.-]*
        if (!peek().isLetter() && peek() != '_' && peek() != ':') {
            throw XMLParseException("Invalid name start: ${peek()}", line, column)
        }
        
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() in "_:.-")) {
            sb.append(advance())
        }
        
        return sb.toString()
    }
    
    private fun parseAttributeValue(): String {
        val quote = advance() // ' or "
        if (quote != '\'' && quote != '"') {
            throw XMLParseException("Expected quote for attribute value", line, column)
        }
        
        val sb = StringBuilder()
        while (peek() != quote && !isAtEnd()) {
            if (peek() == '&') {
                sb.append(parseEntityReference())
            } else {
                sb.append(advance())
            }
        }
        
        if (isAtEnd()) {
            throw XMLParseException("Unterminated attribute value", line, column)
        }
        
        advance() // closing quote
        return sb.toString()
    }
    
    private fun parseText(): String {
        val sb = StringBuilder()
        
        while (!isAtEnd() && peek() != '<') {
            if (peek() == '&') {
                sb.append(parseEntityReference())
            } else {
                sb.append(advance())
            }
        }
        
        return sb.toString()
    }
    
    private fun parseEntityReference(): String {
        consume('&', "Expected '&'")
        val sb = StringBuilder()
        
        while (peek() != ';' && !isAtEnd()) {
            sb.append(advance())
        }
        
        consume(';', "Expected ';'")
        
        return when (val entity = sb.toString()) {
            "lt" -> "<"
            "gt" -> ">"
            "amp" -> "&"
            "apos" -> "'"
            "quot" -> "\""
            else -> {
                // Numeric character reference
                if (entity.startsWith("#x")) {
                    val hex = entity.substring(2)
                    try {
                        hex.toInt(16).toChar().toString()
                    } catch (e: NumberFormatException) {
                        "&$entity;"
                    }
                } else if (entity.startsWith("#")) {
                    val decimal = entity.substring(1)
                    try {
                        decimal.toInt().toChar().toString()
                    } catch (e: NumberFormatException) {
                        "&$entity;"
                    }
                } else {
                    // Unknown entity - keep as is
                    "&$entity;"
                }
            }
        }
    }
    
    private fun parseCDATA(): String {
        // <![CDATA[...]]>
        repeat(9) { advance() } // Skip <![CDATA[
        
        val sb = StringBuilder()
        while (peek(3) != "]]>") {
            if (isAtEnd()) {
                throw XMLParseException("Unterminated CDATA section", line, column)
            }
            sb.append(advance())
        }
        
        repeat(3) { advance() } // Skip ]]>
        return sb.toString()
    }
    
    private fun skipComment() {
        // <!--...-->
        repeat(4) { advance() } // Skip <!--
        
        while (peek(3) != "-->") {
            if (isAtEnd()) {
                throw XMLParseException("Unterminated comment", line, column)
            }
            if (advance() == '\n') {
                line++
                column = 1
            }
        }
        
        repeat(3) { advance() } // Skip -->
    }
    
    private fun skipUntil(target: String) {
        while (peek(target.length) != target && !isAtEnd()) {
            advance()
        }
    }
    
    private fun skipWhitespace() {
        while (!isAtEnd() && peek().isWhitespace()) {
            if (advance() == '\n') {
                line++
                column = 1
            }
        }
    }
    
    private fun consume(expected: Char, message: String) {
        if (peek() != expected) {
            throw XMLParseException(message, line, column)
        }
        advance()
    }
    
    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return text[current]
    }
    
    private fun peek(n: Int): String {
        val end = minOf(current + n, text.length)
        return text.substring(current, end)
    }
    
    private fun advance(): Char {
        val c = text[current]
        current++
        column++
        return c
    }
    
    private fun isAtEnd(): Boolean = current >= text.length
    
    /**
     * Try to parse string as number, return null if not a number
     */
    private fun tryParseNumber(str: String): UDM.Scalar? {
        return str.toDoubleOrNull()?.let { UDM.Scalar.number(it) }
    }
}

/**
 * XML parse exception
 */
class XMLParseException(
    message: String,
    val line: Int,
    val column: Int
) : Exception("XML parse error at $line:$column - $message")

/**
 * Convenience object for XML operations
 */
object XML {
    /**
     * Parse XML string to UDM
     */
    fun parse(xml: String): UDM {
        return XMLParser(xml).parse()
    }
}
