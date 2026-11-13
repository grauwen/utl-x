// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/xml/XmlUtilityFunctions.kt
package com.glomidco.utlx.stdlib.xml

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.FunctionArgumentException
import com.glomidco.utlx.stdlib.annotations.UTLXFunction

/**
 * Additional XML utility functions for common XML operations
 */
object XmlUtilityFunctions {
    
    @UTLXFunction(
        description = "Get node type (element, attribute, text, etc.)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "nodeType(node) => \"element\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get node type (element, attribute, text, etc.)
     * Usage: nodeType(node) => "element"
     */
    fun nodeType(args: List<UDM>): UDM {
        requireArgs(args, 1, "nodeType")
        val node = args[0]

        // nodeType only works on Objects (XML nodes)
        if (node !is UDM.Object) {
            throw FunctionArgumentException(
                "nodeType expects an Object (XML node), but got ${getTypeDescription(node)}. " +
                "Hint: This function is for XML nodes represented as UDM.Object."
            )
        }

        // Check for __nodeType property first (test format)
        val explicitType = node.properties["__nodeType"] as? UDM.Scalar
        val type = if (explicitType != null) {
            explicitType.value?.toString() ?: "unknown"
        } else if (node.properties.containsKey("__nodeName") || node.name != null) {
            "element"
        } else if (node.attributes.isNotEmpty() || node.properties.any { it.key.startsWith("__") }) {
            // Has XML-specific markers (attributes or __ properties)
            "element"
        } else {
            // Plain object with no XML markers
            "unknown"
        }

        return UDM.Scalar(type)
    }
    
    @UTLXFunction(
        description = "Get text content from XML element (all text nodes concatenated)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value",
        "attrName: Attrname value"
        ],
        returns = "Result of the operation",
        example = "textContent(element) => \"Hello World\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get text content from XML element (all text nodes concatenated)
     * Usage: textContent(element) => "Hello World"
     */
    fun textContent(args: List<UDM>): UDM {
        requireArgs(args, 1, "textContent")
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
    
    @UTLXFunction(
        description = "Get all attributes from XML element as object",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value",
        "attrName: Attrname value"
        ],
        returns = "Result of the operation",
        example = "attributes(element) => { id: \"123\", type: \"order\" }",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get all attributes from XML element as object
     * Usage: attributes(element) => { id: "123", type: "order" }
     */
    fun attributes(args: List<UDM>): UDM {
        requireArgs(args, 1, "attributes")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Check for __attributes property first (test format)
                val attrsProperty = element.properties["__attributes"]
                if (attrsProperty is UDM.Object) {
                    return attrsProperty
                }

                // Otherwise use actual attributes map (parser format)
                val attrs = element.attributes
                    .filterKeys { !it.startsWith("__") && !it.startsWith("xmlns") }
                    .mapValues { (_, v) -> UDM.Scalar(v) }

                UDM.Object(attrs, emptyMap())
            }
            else -> UDM.Object(emptyMap(), emptyMap())
        }
    }
    
    @UTLXFunction(
        description = "Get specific attribute value",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "attrName: Attrname value"
        ],
        returns = "Result of the operation",
        example = "attribute(element, \"id\") => \"123\"",
        tags = ["xml"],
        since = "1.0"
    )
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
                // Check for __attributes property first (test format)
                val attrsProperty = element.properties["__attributes"]
                if (attrsProperty is UDM.Object) {
                    return attrsProperty.properties[attrName] ?: UDM.Scalar(null)
                }

                // Otherwise use actual attributes map (parser format)
                val attrValue = element.attributes[attrName]
                UDM.Scalar(attrValue)
            }
            else -> UDM.Scalar(null)
        }
    }
    
    @UTLXFunction(
        description = "Check if element has attribute",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "attrName: Attrname value"
        ],
        returns = "Boolean indicating the result",
        example = "hasAttribute(element, \"id\") => true",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if element has attribute
     * Usage: hasAttribute(element, "id") => true
     */
    fun hasAttribute(args: List<UDM>): UDM {
        requireArgs(args, 2, "hasAttribute")
        val element = args[0]
        val attrName = args[1].asString()

        return when (element) {
            is UDM.Object -> {
                // Check for __attributes property first (test format)
                val attrsProperty = element.properties["__attributes"]
                if (attrsProperty is UDM.Object) {
                    return UDM.Scalar(attrsProperty.properties.containsKey(attrName))
                }

                // Otherwise use actual attributes map (parser format)
                UDM.Scalar(element.attributes.containsKey(attrName))
            }
            else -> UDM.Scalar(false)
        }
    }
    
    @UTLXFunction(
        description = "Count child elements",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "childCount(element) => 5",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Count child elements
     * Usage: childCount(element) => 5
     */
    fun childCount(args: List<UDM>): UDM {
        requireArgs(args, 1, "childCount")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Check for __children property first (test format)
                val childrenProperty = element.properties["__children"]
                if (childrenProperty is UDM.Array) {
                    return UDM.Scalar(childrenProperty.elements.size.toDouble())
                }

                // Otherwise count non-metadata properties
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
    
    @UTLXFunction(
        description = "Get child element names",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "childNames(element) => [\"Order\", \"Customer\", \"Items\"]",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get child element names
     * Usage: childNames(element) => ["Order", "Customer", "Items"]
     */
    fun childNames(args: List<UDM>): UDM {
        requireArgs(args, 1, "childNames")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Check for __children property first (test format)
                val childrenProperty = element.properties["__children"]
                if (childrenProperty is UDM.Array) {
                    val names = childrenProperty.elements
                        .mapNotNull { child ->
                            if (child is UDM.Object) {
                                val nodeName = child.properties["__nodeName"] as? UDM.Scalar
                                nodeName?.value?.toString()
                            } else null
                        }
                        .distinct()
                        .map { UDM.Scalar(it) }
                    return UDM.Array(names)
                }

                // Otherwise use property names
                val names = element.properties
                    .filterKeys { !it.startsWith("__") }
                    .keys
                    .map { UDM.Scalar(it) }

                UDM.Array(names)
            }
            else -> UDM.Array(emptyList())
        }
    }
    
    // `parent(element)` was removed in 2026-11-13. It read a `__parent` property, and **no
    // parser has ever written one** — the readers set `__nodeName`, `__nsContext`,
    // `__schemaType` and the `__xsd*` keys, never `__parent` — so it returned null for every
    // document UTL-X can parse, from its first commit (2025-10-14) onwards. It had no callers.
    //
    // Its unit test passed because it hand-built the property:
    //
    //     val element = UDM.Object(mutableMapOf("__parent" to parent))
    //
    // which is why a registered, documented, green-tested function could be dead for a year.
    //
    // Implementing it properly would mean parent pointers in the UDM — a back-reference in a
    // tree that is currently cheap to copy and share, and that would have to survive every
    // transformation that rebuilds a node. That is a real design decision, not an oversight to
    // patch, and nothing has asked for it.
    //
    // For the case people actually have — flat records linked by a parent key, as in SAP IDoc
    // segments or EDI — use `nestBy()`, which was built for exactly that (F03):
    //
    //     nestBy($input.orders, $input.lines, (o) -> o.orderId, (l) -> l.orderId, "lines")
    //
    // IDoc parent/child is a key join, not structural containment, so a tree pointer would not
    // have served it even if implemented. See B58.

    @UTLXFunction(
        description = "Get element path (like XPath)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "Result of the operation",
        example = "elementPath(element) => \"/Order/Customer/Name\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Get element path (like XPath)
     * Usage: elementPath(element) => "/Order/Customer/Name"
     */
    fun elementPath(args: List<UDM>): UDM {
        requireArgs(args, 1, "elementPath")
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
    
    @UTLXFunction(
        description = "Check if element is empty (no children, no text)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: Element value"
        ],
        returns = "Boolean indicating the result",
        example = "isEmptyElement(element) => false",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if element is empty (no children, no text)
     * Usage: isEmptyElement(element) => false
     */
    fun isEmptyElement(args: List<UDM>): UDM {
        requireArgs(args, 1, "isEmptyElement")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Check for __children property (test format)
                val childrenProperty = element.properties["__children"]
                if (childrenProperty is UDM.Array) {
                    return UDM.Scalar(childrenProperty.elements.isEmpty())
                }

                // Otherwise check regular properties
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
    
    @UTLXFunction(
        description = "Escape XML special characters",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "text: Text value"
        ],
        returns = "Result of the operation",
        example = "xmlEscape(\"<tag>\") => \"&lt;tag&gt;\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Escape XML special characters
     * Usage: xmlEscape("<tag>") => "&lt;tag&gt;"
     */
    fun xmlEscape(args: List<UDM>): UDM {
        requireArgs(args, 1, "xmlEscape")
        val text = args[0].asString()
        
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        
        return UDM.Scalar(escaped)
    }
    
    @UTLXFunction(
        description = "Check if XML element has any content (child elements or text)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "element: XML element to check"
        ],
        returns = "Boolean indicating whether element has content",
        example = "hasContent(element) => true",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Check if XML element has any content (child elements or text)
     * Usage: hasContent(element) => true
     */
    fun hasContent(args: List<UDM>): UDM {
        requireArgs(args, 1, "hasContent")
        val element = args[0]

        return when (element) {
            is UDM.Object -> {
                // Has content if it has properties (excluding _text if empty)
                val hasProperties = element.properties.filterKeys { key ->
                    if (key == "_text") {
                        val textVal = element.properties[key]
                        (textVal as? UDM.Scalar)?.value?.toString()?.isNotEmpty() ?: false
                    } else {
                        true
                    }
                }.isNotEmpty()
                UDM.Scalar(hasProperties)
            }
            is UDM.Scalar -> {
                // Scalar has content if non-empty
                UDM.Scalar(element.value?.toString()?.isNotEmpty() ?: false)
            }
            is UDM.Array -> {
                // Array has content if non-empty
                UDM.Scalar(element.elements.isNotEmpty())
            }
            else -> UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Unescape XML special characters",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "text: Text value"
        ],
        returns = "Result of the operation",
        example = "xmlUnescape(\"&lt;tag&gt;\") => \"<tag>\"",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Unescape XML special characters
     * Usage: xmlUnescape("&lt;tag&gt;") => "<tag>"
     */
    fun xmlUnescape(args: List<UDM>): UDM {
        requireArgs(args, 1, "xmlUnescape")
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
}
