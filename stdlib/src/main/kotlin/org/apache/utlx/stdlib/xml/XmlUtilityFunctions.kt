// stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XmlUtilityFunctions.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException

/**
 * Additional XML utility functions for common XML operations
 */
object XmlUtilityFunctions {
    
    /**
     * Get node type (element, attribute, text, etc.)
     * Usage: node-type(node) => "element"
     */
    fun nodeType(args: List<UDM>): UDM {
        requireArgs(args, 1, "node-type")
        val node = args[0]
        
        val type = when (node) {
            is UDM.Scalar -> "text"
            is UDM.Array -> "array"
            is UDM.Object -> {
                if (node.properties.containsKey("__nodeName")) "element" else "object"
            }
            else -> "unknown"
        }
        
        return UDM.Scalar(type)
    }
    
    /**
     * Get text content from XML element (all text nodes concatenated)
     * Usage: text-content(element) => "Hello World"
     */
    fun textContent(args: List<UDM>): UDM {
        requireArgs(args, 1, "text-content")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                // Look for text content in properties
                val textContent = element.properties["__text"] as? UDM.Scalar
                UDM.Scalar(textContent?.value?.toString() ?: "")
            }
            is UDM.Scalar -> element
            else -> UDM.Scalar("")
        }
    }
    
    /**
     * Get all attributes from XML element as object
     * Usage: attributes(element) => { id: "123", type: "order" }
     */
    fun attributes(args: List<UDM>): UDM {
        requireArgs(args, 1, "attributes")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                val attrs = element.attributes
                    .filterKeys { !it.startsWith("__") && !it.startsWith("xmlns") }
                    .mapValues { (_, v) -> UDM.Scalar(v) }
                
                UDM.Object(attrs, emptyMap())
            }
            else -> UDM.Object(emptyMap(), emptyMap())
        }
    }
    
    /**
     * Get specific attribute value
     * Usage: attribute(element, "id") => "123"
     */
    fun attribute(args: List<UDM>): UDM {
        requireArgs(args, 2, "attribute")
        val element = args[0]
        val attrName = args[1].asString()
        
        return when (element) {
            is UDM.Object -> {
                UDM.Scalar(element.attributes[attrName] ?: "")
            }
            else -> UDM.Scalar("")
        }
    }
    
    /**
     * Check if element has attribute
     * Usage: has-attribute(element, "id") => true
     */
    fun hasAttribute(args: List<UDM>): UDM {
        requireArgs(args, 2, "has-attribute")
        val element = args[0]
        val attrName = args[1].asString()
        
        return when (element) {
            is UDM.Object -> {
                UDM.Scalar(element.attributes.containsKey(attrName))
            }
            else -> UDM.Scalar(false)
        }
    }
    
    /**
     * Count child elements
     * Usage: child-count(element) => 5
     */
    fun childCount(args: List<UDM>): UDM {
        requireArgs(args, 1, "child-count")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                // Count non-metadata properties
                val count = element.properties
                    .filterKeys { !it.startsWith("__") }
                    .size
                UDM.Scalar(count.toDouble())
            }
            is UDM.Array -> {
                UDM.Scalar(element.elements.size.toDouble())
            }
            else -> UDM.Scalar(0.0)
        }
    }
    
    /**
     * Get child element names
     * Usage: child-names(element) => ["Order", "Customer", "Items"]
     */
    fun childNames(args: List<UDM>): UDM {
        requireArgs(args, 1, "child-names")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                val names = element.properties
                    .filterKeys { !it.startsWith("__") }
                    .keys
                    .map { UDM.Scalar(it) }
                
                UDM.Array(names)
            }
            else -> UDM.Array(emptyList())
        }
    }
    
    /**
     * Get parent element (if metadata available)
     * Usage: parent(element) => parentElement
     */
    fun parent(args: List<UDM>): UDM {
        requireArgs(args, 1, "parent")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                element.properties["__parent"] ?: UDM.Scalar(null)
            }
            else -> UDM.Scalar(null)
        }
    }
    
    /**
     * Get element path (like XPath)
     * Usage: element-path(element) => "/Order/Customer/Name"
     */
    fun elementPath(args: List<UDM>): UDM {
        requireArgs(args, 1, "element-path")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                // Build path from metadata if available
                val path = element.properties["__path"] as? UDM.Scalar
                UDM.Scalar(path?.value?.toString() ?: "/")
            }
            else -> UDM.Scalar("/")
        }
    }
    
    /**
     * Check if element is empty (no children, no text)
     * Usage: is-empty-element(element) => false
     */
    fun isEmptyElement(args: List<UDM>): UDM {
        requireArgs(args, 1, "is-empty-element")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                val hasChildren = element.properties
                    .filterKeys { !it.startsWith("__") }
                    .isNotEmpty()
                
                val hasText = (element.properties["__text"] as? UDM.Scalar)
                    ?.value?.toString()?.isNotEmpty() == true
                
                UDM.Scalar(!hasChildren && !hasText)
            }
            else -> UDM.Scalar(true)
        }
    }
    
    /**
     * Escape XML special characters
     * Usage: xml-escape("<tag>") => "&lt;tag&gt;"
     */
    fun xmlEscape(args: List<UDM>): UDM {
        requireArgs(args, 1, "xml-escape")
        val text = args[0].asString()
        
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        
        return UDM.Scalar(escaped)
    }
    
    /**
     * Unescape XML special characters
     * Usage: xml-unescape("&lt;tag&gt;") => "<tag>"
     */
    fun xmlUnescape(args: List<UDM>): UDM {
        requireArgs(args, 1, "xml-unescape")
        val text = args[0].asString()
        
        val unescaped = text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        
        return UDM.Scalar(unescaped)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
