// stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLSerializationOptionsFunctions.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction


/**
 * Advanced XML Serialization with Fine-Grained Control
 * 
 * Handles real-world XML requirements that standard serializers can't:
 * - Namespace prefix enforcement
 * - Empty element formatting control
 * - Non-well-formed XML generation (for legacy systems)
 * - Namespace declaration placement control
 */
object XMLSerializationOptionsFunctions {

    @UTLXFunction(
        description = "Enforces specific namespace prefixes on an XML string",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "mappings: Mappings value"
        ],
        returns = "Result of the operation",
        example = "enforceNamespacePrefixes(...) => result",
        notes = "Example:\n```\nenforceNamespacePrefixes(\"<ns1:Element xmlns:ns1='http://example.com'/>\",\n{\"http://example.com\": \"ex\"})\n// Returns: \"<ex:Element xmlns:ex='http://example.com'/>\"\n```",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Enforces specific namespace prefixes on an XML string
     * 
     * @param args List containing: [xml, namespaceMappings]
     * @return UDM Scalar with XML string using enforced prefixes
     * 
     * Example:
     * ```
     * enforceNamespacePrefixes("<ns1:Element xmlns:ns1='http://example.com'/>", 
     *                         {"http://example.com": "ex"})
     * // Returns: "<ex:Element xmlns:ex='http://example.com'/>"
     * ```
     */
    fun enforceNamespacePrefixes(args: List<UDM>): UDM {
        requireArgs(args, 2, "enforceNamespacePrefixes")
        val xml = args[0].asString()
        val mappings = args[1].asObject() ?: throw FunctionArgumentException("Second argument must be an object with namespace mappings")
        
        return try {
            // Build namespace mapping from UDM object
            val namespaceMappings = mutableMapOf<String, String>()
            mappings.properties.forEach { (uri, prefix) ->
                when (prefix) {
                    is UDM.Scalar -> namespaceMappings[uri] = prefix.value?.toString() ?: ""
                    else -> throw FunctionArgumentException("Namespace prefix must be a string")
                }
            }
            
            val result = enforceNamespacePrefixesInternal(xml, namespaceMappings)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to enforce namespace prefixes: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Formats empty elements according to specified style",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "style: Style value"
        ],
        returns = "Result of the operation",
        example = "formatEmptyElements(...) => result",
        notes = """Styles:
- "self-closing": <element/>
- "explicit": <element></element>
- "nil": <element xsi:nil="true"/>
- "omit": remove empty elements entirely""",
        tags = ["cleanup", "xml"],
        since = "1.0"
    )
    /**
     * Formats empty elements according to specified style
     * 
     * @param args List containing: [xml, style]
     * @return UDM Scalar with formatted XML
     * 
     * Styles:
     * - "self-closing": <element/>
     * - "explicit": <element></element>
     * - "nil": <element xsi:nil="true"/>
     * - "omit": remove empty elements entirely
     */
    fun formatEmptyElements(args: List<UDM>): UDM {
        requireArgs(args, 2, "formatEmptyElements")
        val xml = args[0].asString()
        val style = args[1].asString()
        
        return try {
            val result = when (style.lowercase()) {
                "self-closing", "self_closing" -> formatToSelfClosing(xml)
                "explicit" -> formatToExplicit(xml)
                "nil" -> formatToNil(xml)
                "omit" -> omitEmptyElements(xml)
                else -> throw FunctionArgumentException("Unknown empty element style: $style. Use: self-closing, explicit, nil, omit")
            }
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to format empty elements: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Adds namespace declarations to XML",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "namespaces: Namespaces value"
        ],
        returns = "Result of the operation",
        example = "addNamespaceDeclarations(...) => result",
        notes = "Example:\n```\naddNamespaceDeclarations(\"<root/>\", {\"xsi\": \"http://www.w3.org/2001/XMLSchema-instance\"})\n// Returns: \"<root xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'/>\"\n```",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Adds namespace declarations to XML
     * 
     * @param args List containing: [xml, namespaces]
     * @return UDM Scalar with XML containing namespace declarations
     * 
     * Example:
     * ```
     * addNamespaceDeclarations("<root/>", {"xsi": "http://www.w3.org/2001/XMLSchema-instance"})
     * // Returns: "<root xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'/>"
     * ```
     */
    fun addNamespaceDeclarations(args: List<UDM>): UDM {
        requireArgs(args, 2, "addNamespaceDeclarations")
        val xml = args[0].asString()
        val namespaces = args[1].asObject() ?: throw FunctionArgumentException("Second argument must be an object with namespace mappings")
        
        return try {
            // Build namespace mapping from UDM object
            val namespaceMappings = mutableMapOf<String, String>()
            namespaces.properties.forEach { (prefix, uri) ->
                when (uri) {
                    is UDM.Scalar -> namespaceMappings[prefix] = uri.value?.toString() ?: ""
                    else -> throw FunctionArgumentException("Namespace URI must be a string")
                }
            }
            
            val result = addNamespaceDeclarationsInternal(xml, namespaceMappings)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to add namespace declarations: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Creates SOAP envelope with proper namespace prefixes",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "body: Body value"
        ],
        returns = "Result of the operation",
        example = "createSOAPEnvelope(...) => result",
        notes = "Example:\n```\ncreateSOAPEnvelope(bodyContent, \"1.1\")\n// Returns SOAP 1.1 envelope with proper soap: prefix\n```",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Creates SOAP envelope with proper namespace prefixes
     * 
     * @param args List containing: [body, soapVersion?]
     * @return UDM Scalar with SOAP envelope XML
     * 
     * Example:
     * ```
     * createSOAPEnvelope(bodyContent, "1.1")
     * // Returns SOAP 1.1 envelope with proper soap: prefix
     * ```
     */
    fun createSOAPEnvelope(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("createSOAPEnvelope expects 1 or 2 arguments (body, soapVersion?), got ${args.size}")
        }
        
        val body = args[0]
        val soapVersion = if (args.size > 1) args[1].asString() else "1.1"
        
        return try {
            val result = createSOAPEnvelopeInternal(body, soapVersion)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to create SOAP envelope: ${e.message}")
        }
    }

    // Helper functions
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }

    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: ""
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toString() to convert values to strings."
        )
    }

    private fun UDM.asObject(): UDM.Object? = when (this) {
        is UDM.Object -> this
        else -> null
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }

    // Internal helper functions for XML processing
    private fun enforceNamespacePrefixesInternal(xml: String, namespaceMappings: Map<String, String>): String {
        var result = xml
        
        // Simple regex-based replacement for namespace prefixes
        namespaceMappings.forEach { (uri, newPrefix) ->
            // Find existing prefix for this URI
            val nsDeclarationPattern = Regex("xmlns:([^=]+)=\"${Regex.escape(uri)}\"")
            val match = nsDeclarationPattern.find(xml)
            if (match != null) {
                val oldPrefix = match.groupValues[1]
                if (oldPrefix != newPrefix) {
                    // Replace namespace declaration
                    result = result.replace("xmlns:$oldPrefix=\"$uri\"", "xmlns:$newPrefix=\"$uri\"")
                    // Replace element and attribute prefixes
                    result = result.replace("<$oldPrefix:", "<$newPrefix:")
                    result = result.replace("</$oldPrefix:", "</$newPrefix:")
                    result = result.replace(" $oldPrefix:", " $newPrefix:")
                }
            }
        }
        
        return result
    }

    private fun formatToSelfClosing(xml: String): String {
        return xml.replace(Regex("<([^\\s/>]+)(\\s[^>]*)?>\\s*</\\1>"), "<$1$2/>")
    }
    
    private fun formatToExplicit(xml: String): String {
        return xml.replace(Regex("<([^\\s/>]+)(\\s[^>]*)?/>"), "<$1$2></$1>")
    }
    
    private fun formatToNil(xml: String): String {
        return xml.replace(Regex("<([^\\s/>]+)(\\s[^>]*)?/>"), "<$1$2 xsi:nil=\"true\"/>")
    }
    
    private fun omitEmptyElements(xml: String): String {
        return xml.replace(Regex("<[^\\s/>]+(\\s[^>]*)?(/>|>\\s*</[^>]+>)"), "")
    }

    private fun addNamespaceDeclarationsInternal(xml: String, namespaces: Map<String, String>): String {
        // Match opening tag or self-closing tag (but not comments, processing instructions, etc.)
        val rootElementPattern = Regex("<([a-zA-Z][^\\s/>]*)(\\s[^>]*)?(/>|>)")
        val match = rootElementPattern.find(xml)

        return if (match != null) {
            val element = match.groupValues[1]
            val existingAttrs = match.groupValues[2]
            val closingPart = match.groupValues[3] // Either "/>" or ">"
            val declarations = namespaces.map { (prefix, uri) ->
                "xmlns:$prefix=\"$uri\""
            }.joinToString(" ")

            val newOpeningTag = "<$element$existingAttrs $declarations$closingPart"
            xml.replaceFirst(match.value, newOpeningTag)
        } else {
            xml
        }
    }
    
    private fun createSOAPEnvelopeInternal(body: UDM, soapVersion: String): String {
        val soapNS = when (soapVersion) {
            "1.1" -> "http://schemas.xmlsoap.org/soap/envelope/"
            "1.2" -> "http://www.w3.org/2003/05/soap-envelope"
            else -> throw FunctionArgumentException("Unsupported SOAP version: $soapVersion. Use '1.1' or '1.2'")
        }
        
        // Simple SOAP envelope template
        return """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="$soapNS">
    <soap:Header/>
    <soap:Body>
        ${serializeUDMToXMLString(body)}
    </soap:Body>
</soap:Envelope>"""
    }
    
    private fun serializeUDMToXMLString(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> escapeXMLContent(udm.value?.toString() ?: "")
            is UDM.Object -> {
                udm.properties.map { (key, value) ->
                    "<$key>${serializeUDMToXMLString(value)}</$key>"
                }.joinToString("")
            }
            is UDM.Array -> {
                udm.elements.joinToString("") { serializeUDMToXMLString(it) }
            }
            else -> ""
        }
    }
    
    private fun escapeXMLContent(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}