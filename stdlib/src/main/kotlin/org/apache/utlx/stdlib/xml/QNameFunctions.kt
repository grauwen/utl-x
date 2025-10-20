// stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/QNameFunctions.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * XML QName (Qualified Name) functions for namespace handling
 * 
 * These functions are XML-specific and handle namespace operations
 * essential for enterprise XML transformations.
 */
object QNameFunctions {
    
    @UTLXFunction(
        description = "Get local name (without namespace prefix) from XML element",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "Result of the operation",
        example = "local-name(element) => \"Envelope\" (from \"soap:Envelope\")",
        notes = "For non-XML data, returns the property name.",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get local name (without namespace prefix) from XML element
     * Usage: local-name(element) => "Envelope" (from "soap:Envelope")
     * 
     * For non-XML data, returns the property name.
     */
    fun localName(args: List<UDM>): UDM {
        requireArgs(args, 1, "local-name")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Get element name from UDM.Object.name field
                val nameStr = element.name ?: ""

                // Extract local name from qualified name (after colon)
                val localName = if (nameStr.contains(":")) {
                    nameStr.substringAfter(":")
                } else {
                    nameStr
                }

                UDM.Scalar(localName)
            }
            else -> throw FunctionArgumentException("local-name expects an object/element")
        }
    }
    
    @UTLXFunction(
        description = "Get namespace URI from XML element",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "empty string if no namespace.",
        example = "namespace-uri(element) => \"http://schemas.xmlsoap.org/soap/envelope/\"",
        notes = "Returns empty string if no namespace.",
        tags = ["cleanup", "xml"],
        since = "1.0"
    )
    /**
     * Get namespace URI from XML element
     * Usage: namespace-uri(element) => "http://schemas.xmlsoap.org/soap/envelope/"
     * 
     * Returns empty string if no namespace.
     */
    fun namespaceUri(args: List<UDM>): UDM {
        requireArgs(args, 1, "namespace-uri")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Extract prefix from element name
                val nameStr = element.name ?: ""
                val prefix = if (nameStr.contains(":")) {
                    nameStr.substringBefore(":")
                } else {
                    "" // No prefix, check for default namespace
                }

                // Look up namespace URI from xmlns attributes
                val namespaceUri = if (prefix.isNotEmpty()) {
                    element.attributes["xmlns:$prefix"] ?: ""
                } else {
                    element.attributes["xmlns"] ?: ""
                }

                UDM.Scalar(namespaceUri)
            }
            else -> UDM.Scalar("")
        }
    }
    
    @UTLXFunction(
        description = "Get qualified name (with prefix) from XML element",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "Result of the operation",
        example = "name(element) => \"soap:Envelope\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get qualified name (with prefix) from XML element
     * Usage: name(element) => "soap:Envelope"
     */
    fun qualifiedName(args: List<UDM>): UDM {
        requireArgs(args, 1, "name")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Get element name from UDM.Object.name field
                UDM.Scalar(element.name ?: "")
            }
            else -> throw FunctionArgumentException("name expects an object/element")
        }
    }
    
    @UTLXFunction(
        description = "Get namespace prefix from XML element",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value",
        "context: Context value"
        ],
        returns = "empty string if no prefix.",
        example = "namespace-prefix(element) => \"soap\" (from \"soap:Envelope\")",
        notes = "Returns empty string if no prefix.",
        tags = ["cleanup", "xml"],
        since = "1.0"
    )
    /**
     * Get namespace prefix from XML element
     * Usage: namespace-prefix(element) => "soap" (from "soap:Envelope")
     * 
     * Returns empty string if no prefix.
     */
    fun namespacePrefix(args: List<UDM>): UDM {
        requireArgs(args, 1, "namespace-prefix")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Get element name from UDM.Object.name field
                val nameStr = element.name ?: ""

                // Extract prefix (before colon)
                val prefix = if (nameStr.contains(":")) {
                    nameStr.substringBefore(":")
                } else {
                    ""
                }

                UDM.Scalar(prefix)
            }
            else -> UDM.Scalar("")
        }
    }
    
    @UTLXFunction(
        description = "Resolve QName string to full qualified name with namespace",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "qnameStr: Qnamestr value",
        "context: Context value"
        ],
        returns = "Result of the operation",
        example = "resolve-qname(\"soap:Envelope\", context)",
        notes = """=> { localName: "Envelope", prefix: "soap", uri: "http://..." }
Context provides namespace bindings.""",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Resolve QName string to full qualified name with namespace
     * Usage: resolve-qname("soap:Envelope", context) 
     *        => { localName: "Envelope", prefix: "soap", uri: "http://..." }
     * 
     * Context provides namespace bindings.
     */
    fun resolveQName(args: List<UDM>): UDM {
        requireArgs(args, 2, "resolve-qname")
        val qnameStr = args[0].asString()
        val context = args[1]
        
        val (prefix, localName) = if (qnameStr.contains(":")) {
            qnameStr.split(":", limit = 2).let { it[0] to it[1] }
        } else {
            "" to qnameStr
        }
        
        // Look up namespace URI from context
        val namespaceUri = when (context) {
            is UDM.Object -> {
                context.attributes["xmlns:$prefix"] ?: 
                context.attributes["__namespaceUri"] ?: ""
            }
            else -> ""
        }
        
        return UDM.Object(
            mapOf(
                "localName" to UDM.Scalar(localName),
                "prefix" to UDM.Scalar(prefix),
                "namespaceUri" to UDM.Scalar(namespaceUri),
                "qualifiedName" to UDM.Scalar(qnameStr)
            ),
            emptyMap()
        )
    }
    
    @UTLXFunction(
        description = "Create QName string from local name and namespace URI",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "localName: Localname value",
        "namespaceUri: Namespaceuri value"
        ],
        returns = "Result of the operation",
        example = "create-qname(\"Envelope\", \"http://schemas.xmlsoap.org/soap/envelope/\", \"soap\")",
        notes = "=> \"soap:Envelope\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Create QName string from local name and namespace URI
     * Usage: create-qname("Envelope", "http://schemas.xmlsoap.org/soap/envelope/", "soap")
     *        => "soap:Envelope"
     */
    fun createQName(args: List<UDM>): UDM {
        requireArgs(args, 2..3, "create-qname")
        val localName = args[0].asString()
        val namespaceUri = args[1].asString()
        val prefix = if (args.size > 2) args[2].asString() else ""
        
        val qname = if (prefix.isNotEmpty()) {
            "$prefix:$localName"
        } else {
            localName
        }
        
        return UDM.Scalar(qname)
    }
    
    @UTLXFunction(
        description = "Check if element has namespace",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "Boolean indicating the result",
        example = "has-namespace(element) => true",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if element has namespace
     * Usage: has-namespace(element) => true
     */
    fun hasNamespace(args: List<UDM>): UDM {
        requireArgs(args, 1, "has-namespace")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Check if element name has a namespace prefix
                val nameStr = element.name ?: ""
                val hasPrefix = nameStr.contains(":")

                if (!hasPrefix) {
                    // Check for default namespace
                    UDM.Scalar(element.attributes.containsKey("xmlns"))
                } else {
                    // Has prefix, check if xmlns:prefix is declared
                    val prefix = nameStr.substringBefore(":")
                    UDM.Scalar(element.attributes.containsKey("xmlns:$prefix"))
                }
            }
            else -> UDM.Scalar(false)
        }
    }
    
    @UTLXFunction(
        description = "Get all namespace declarations from element",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value",
        "qnamePattern: Qnamepattern value"
        ],
        returns = "Result of the operation",
        example = "get-namespaces(element)",
        notes = "=> { \"soap\": \"http://...\", \"wsa\": \"http://...\" }",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get all namespace declarations from element
     * Usage: get-namespaces(element) 
     *        => { "soap": "http://...", "wsa": "http://..." }
     */
    fun getNamespaces(args: List<UDM>): UDM {
        requireArgs(args, 1, "get-namespaces")
        val element = args[0]
        
        return when (element) {
            is UDM.Object -> {
                val namespaces = mutableMapOf<String, UDM>()
                
                // Extract all xmlns: attributes
                for ((key, value) in element.attributes) {
                    if (key.startsWith("xmlns:")) {
                        val prefix = key.substringAfter("xmlns:")
                        namespaces[prefix] = UDM.Scalar(value)
                    }
                }
                
                // Add default namespace if present
                element.attributes["xmlns"]?.let {
                    namespaces[""] = UDM.Scalar(it)
                }
                
                UDM.Object(namespaces, emptyMap())
            }
            else -> UDM.Object(emptyMap(), emptyMap())
        }
    }
    
    @UTLXFunction(
        description = "Match element by qualified name",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value",
        "qnamePattern: Qnamepattern value"
        ],
        returns = "Result of the operation",
        example = "matches-qname(element, \"soap:Envelope\") => true",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Match element by qualified name
     * Usage: matches-qname(element, "soap:Envelope") => true
     */
    fun matchesQName(args: List<UDM>): UDM {
        requireArgs(args, 2, "matches-qname")
        val element = args[0]
        val qnamePattern = args[1].asString()

        return when (element) {
            is UDM.Object -> {
                // Get element name from UDM.Object.name field
                val actualName = element.name ?: ""
                UDM.Scalar(actualName == qnamePattern)
            }
            else -> UDM.Scalar(false)
        }
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun requireArgs(args: List<UDM>, range: IntRange, functionName: String) {
        if (args.size !in range) {
            throw FunctionArgumentException("$functionName expects ${range.first}..${range.last} arguments, got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException("Expected string value")
    }
}
