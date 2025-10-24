package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * CDATA (Character Data) Section Functions
 * 
 * CDATA sections allow embedding text that would normally require escaping:
 * - HTML/XML markup within XML
 * - JavaScript/code with < > & characters
 * - Special characters without entity encoding
 * - Large text blocks with mixed content
 * 
 * Syntax: <![CDATA[content here]]>
 */
object CDATAFunctions {

    @UTLXFunction(
        description = "Creates a CDATA section with the given content",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "content: Content value"
        ],
        returns = "Result of the operation",
        example = "createCDATA(...) => result",
        notes = "Example:\n```\ncreateCDATA(\"Price: <$100 & tax\")\n// Returns: UDM.Scalar(\"<![CDATA[Price: <$100 & tax]]>\")\n```",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Creates a CDATA section with the given content
     * 
     * @param args List containing: [content]
     * @return UDM Scalar with CDATA section string
     * 
     * Example:
     * ```
     * createCDATA("Price: <$100 & tax")
     * // Returns: UDM.Scalar("<![CDATA[Price: <$100 & tax]]>")
     * ```
     */
    fun createCDATA(args: List<UDM>): UDM {
        requireArgs(args, 1, "createCDATA")
        val content = args[0].asString()
        
        if (content.contains("]]>")) {
            throw FunctionArgumentException(
                "CDATA content cannot contain ']]>' sequence. " +
                "Hint: Use splitCDATA() to handle content with this sequence."
            )
        }
        
        return UDM.Scalar("<![CDATA[$content]]>")
    }

    @UTLXFunction(
        description = "Checks if a string is a CDATA section",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "text: Text value",
        "thresholdUDM: Thresholdudm value"
        ],
        returns = "Boolean indicating the result",
        example = "isCDATA(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Checks if a string is a CDATA section
     * 
     * @param args List containing: [text]
     * @return UDM Scalar Boolean indicating if text is wrapped in CDATA markers
     */
    fun isCDATA(args: List<UDM>): UDM {
        requireArgs(args, 1, "isCDATA")
        val text = args[0].asString()
        
        val trimmed = text.trim()
        val result = trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Extracts content from a CDATA section",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "text: Text value",
        "thresholdUDM: Thresholdudm value"
        ],
        returns = "Result of the operation",
        example = "extractCDATA(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Extracts content from a CDATA section
     * 
     * @param args List containing: [text]
     * @return UDM Scalar with content without CDATA markers
     */
    fun extractCDATA(args: List<UDM>): UDM {
        requireArgs(args, 1, "extractCDATA")
        val text = args[0].asString()
        
        val trimmed = text.trim()
        val result = if (isCDATAInternal(trimmed)) {
            trimmed.substring(9, trimmed.length - 3)  // Remove <![CDATA[ and ]]>
        } else {
            text
        }
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Unwraps CDATA if present, otherwise returns original",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "content: Content value",
        "thresholdUDM: Thresholdudm value",
        "thresholdUDM: Thresholdudm value"
        ],
        returns = "Result of the operation",
        example = "unwrapCDATA(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Unwraps CDATA if present, otherwise returns original
     * 
     * @param args List containing: [text]
     * @return UDM Scalar with unwrapped content
     */
    fun unwrapCDATA(args: List<UDM>): UDM = extractCDATA(args)

    @UTLXFunction(
        description = "Determines if content should be wrapped in CDATA",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "content: Content value",
        "thresholdUDM: Thresholdudm value",
        "thresholdUDM: Thresholdudm value"
        ],
        returns = "Result of the operation",
        example = "shouldUseCDATA(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Determines if content should be wrapped in CDATA
     * 
     * @param args List containing: [content, threshold?]
     * @return UDM Scalar Boolean indicating if CDATA would be beneficial
     */
    fun shouldUseCDATA(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException(
                "shouldUseCDATA expects 1 or 2 arguments (content, threshold?), got ${args.size}. " +
                "Hint: Provide content as first argument, optional threshold as second."
            )
        }
        
        val content = args[0].asString()
        val threshold = if (args.size > 1) {
            when (val thresholdUDM = args[1]) {
                is UDM.Scalar -> thresholdUDM.value as? Number ?: 3
                else -> 3
            }
        } else 3
        
        val result = shouldUseCDATAInternal(content, threshold.toInt())
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Automatically wraps content in CDATA if beneficial",
        minArgs = 3,
        maxArgs = 3,
        category = "XML",
        parameters = [
            "content: Content value",
        "forceUDM: Forceudm value",
        "thresholdUDM: Thresholdudm value"
        ],
        returns = "Result of the operation",
        example = "wrapIfNeeded(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Automatically wraps content in CDATA if beneficial
     * 
     * @param args List containing: [content, force?, threshold?]
     * @return UDM Scalar with content wrapped in CDATA if beneficial
     */
    fun wrapIfNeeded(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 3) {
            throw FunctionArgumentException(
                "wrapIfNeeded expects 1-3 arguments (content, force?, threshold?), got ${args.size}. " +
                "Hint: Provide content, optional force boolean, and optional threshold number."
            )
        }
        
        val content = args[0].asString()
        val force = if (args.size > 1) {
            when (val forceUDM = args[1]) {
                is UDM.Scalar -> forceUDM.value as? Boolean ?: false
                else -> false
            }
        } else false
        val threshold = if (args.size > 2) {
            when (val thresholdUDM = args[2]) {
                is UDM.Scalar -> thresholdUDM.value as? Number ?: 3
                else -> 3
            }
        } else 3
        
        return try {
            val result = wrapIfNeededInternal(content, force, threshold.toInt())
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "Failed to wrap CDATA: ${e.message}. " +
                "Hint: Check that content doesn't contain ']]>' sequence."
            )
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

    @UTLXFunction(
        description = "Escapes text for XML without using CDATA",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "text: Text value"
        ],
        returns = "Result of the operation",
        example = "escapeXML(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Escapes text for XML without using CDATA
     * 
     * @param args List containing: [text]
     * @return UDM Scalar with XML-escaped text
     */
    fun escapeXML(args: List<UDM>): UDM {
        requireArgs(args, 1, "escapeXML")
        val text = args[0].asString()
        
        val result = text
            .replace("&", "&amp;")   // Must be first!
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        
        return UDM.Scalar(result)
    }

    @UTLXFunction(
        description = "Unescapes XML entities",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "text: Text value"
        ],
        returns = "Result of the operation",
        example = "unescapeXML(...) => result",
        tags = ["xml"],
        since = "1.0"
    )
    /**
     * Unescapes XML entities
     * 
     * @param args List containing: [text]
     * @return UDM Scalar with unescaped text
     */
    fun unescapeXML(args: List<UDM>): UDM {
        requireArgs(args, 1, "unescapeXML")
        val text = args[0].asString()
        
        var result = text
        
        // Named entities
        result = result.replace("&lt;", "<")
        result = result.replace("&gt;", ">")
        result = result.replace("&quot;", "\"")
        result = result.replace("&apos;", "'")
        
        // Numeric character references (hex)
        result = result.replace(Regex("&#x([0-9A-Fa-f]+);")) { match ->
            val codePoint = match.groupValues[1].toInt(16)
            codePoint.toChar().toString()
        }
        
        // Numeric character references (decimal)
        result = result.replace(Regex("&#([0-9]+);")) { match ->
            val codePoint = match.groupValues[1].toInt()
            codePoint.toChar().toString()
        }
        
        // &amp; must be last!
        result = result.replace("&amp;", "&")
        
        return UDM.Scalar(result)
    }
    
    // Internal helper functions
    private fun isCDATAInternal(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")
    }
    
    private fun shouldUseCDATAInternal(content: String, threshold: Int = 3): Boolean {
        // Already in CDATA
        if (isCDATAInternal(content)) return false
        
        // Check for markup patterns
        if (content.contains(Regex("<[a-zA-Z][^>]*>"))) return true  // HTML/XML tags
        
        // Count special characters
        val specialChars = content.count { it in "<>&\"'" }
        if (specialChars >= threshold) return true
        
        // Check for problematic sequences
        if (content.contains("]]>")) return true  // Would break CDATA
        if (content.contains("&") && content.contains("<")) return true  // Likely code/markup
        
        // Check for control characters (except whitespace)
        if (content.any { it.isISOControl() && it !in "\n\r\t" }) return true
        
        return false
    }
    
    private fun wrapIfNeededInternal(content: String, force: Boolean = false, threshold: Int = 3): String {
        // Already CDATA
        if (isCDATAInternal(content)) return content
        
        // Force wrap
        if (force) {
            if (content.contains("]]>")) {
                throw FunctionArgumentException(
                    "Content contains ']]>' which cannot be used in CDATA. " +
                    "Hint: Use splitCDATA() to handle content with this sequence."
                )
            }
            return "<![CDATA[$content]]>"
        }

        // Auto-detect
        return if (shouldUseCDATAInternal(content, threshold)) {
            if (content.contains("]]>")) {
                throw FunctionArgumentException(
                    "Content contains ']]>' which cannot be used in CDATA. " +
                    "Hint: Use splitCDATA() to handle content with this sequence."
                )
            }
            "<![CDATA[$content]]>"
        } else {
            content
        }
    }
}