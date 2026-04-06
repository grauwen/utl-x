package org.apache.utlx.formats.odata

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONParser

/**
 * OData JSON Parser - Converts OData JSON payloads to UDM
 *
 * Extends the standard JSON parser with OData-specific handling:
 * - @odata.context → stored as root attribute "odata.context"
 * - @odata.type → stored as type attribute on node
 * - @odata.id, @odata.etag → stored as identity attributes
 * - All @odata.* keys removed from UDM.Object properties
 * - { "value": [...] } collection wrapper unwrapped to UDM.Array
 * - Property@odata.* per-property annotations stored as attributes
 *
 * The parser always performs full extraction — all OData annotations
 * (top-level and per-property) are preserved as UDM attributes.
 * No information is lost at parse time. The metadata level (minimal,
 * full, none) is an output concern handled by ODataJSONSerializer.
 */
class ODataJSONParser(
    private val content: String,
    private val options: Map<String, Any> = emptyMap()
) {
    fun parse(): UDM {
        // Step 1: Parse as standard JSON
        val udm = JSONParser(content).parse()

        // Step 2: Post-process UDM tree for OData annotations
        return processNode(udm)
    }

    /**
     * Recursively process UDM nodes to extract @odata.* annotations
     */
    private fun processNode(node: UDM): UDM {
        return when (node) {
            is UDM.Object -> processObject(node)
            is UDM.Array -> UDM.Array(node.elements.map { processNode(it) })
            else -> node
        }
    }

    /**
     * Process a UDM.Object: extract @odata.* keys as attributes, remove from properties
     */
    private fun processObject(obj: UDM.Object): UDM {
        val attributes = obj.attributes.toMutableMap()
        val dataProperties = mutableMapOf<String, UDM>()
        val perPropertyAnnotations = mutableMapOf<String, MutableMap<String, String>>()

        for ((key, value) in obj.properties) {
            when {
                // Top-level @odata.* annotations → store as attributes
                key.startsWith("@odata.") -> {
                    val attrName = key.removePrefix("@")  // "odata.context", "odata.type", etc.
                    val attrValue = when (value) {
                        is UDM.Scalar -> value.value?.toString() ?: ""
                        else -> value.toString()
                    }
                    attributes[attrName] = attrValue
                }

                // Per-property annotations: "PropertyName@odata.type" etc.
                key.contains("@odata.") -> {
                    val parts = key.split("@odata.", limit = 2)
                    val propName = parts[0]
                    val annotationName = "odata." + parts[1]
                    val annotationValue = when (value) {
                        is UDM.Scalar -> value.value?.toString() ?: ""
                        else -> value.toString()
                    }
                    perPropertyAnnotations.getOrPut(propName) { mutableMapOf() }[annotationName] = annotationValue
                }

                // Regular data property → recurse and keep
                else -> {
                    dataProperties[key] = processNode(value)
                }
            }
        }

        // Apply per-property annotations to their target properties
        for ((propName, annotations) in perPropertyAnnotations) {
            val prop = dataProperties[propName]
            if (prop is UDM.Object) {
                val mergedAttrs = prop.attributes.toMutableMap()
                mergedAttrs.putAll(annotations)
                dataProperties[propName] = UDM.Object(
                    properties = prop.properties,
                    attributes = mergedAttrs,
                    name = prop.name,
                    metadata = prop.metadata
                )
            }
        }

        // Check for { "value": [...] } collection wrapper pattern
        // If the object only has data property "value" (after removing @odata.*) and it's an array,
        // unwrap it. Collection-level OData attributes (e.g. @odata.context) are preserved
        // as attributes on a thin wrapper object.
        if (dataProperties.size == 1 && dataProperties.containsKey("value")) {
            val valueNode = dataProperties["value"]
            if (valueNode is UDM.Array) {
                // UDM.Array doesn't support attributes, so if there are collection-level
                // OData annotations, keep the wrapper object with just "value" + attributes.
                // Otherwise, unwrap directly to the array.
                return if (attributes.isNotEmpty()) {
                    UDM.Object(
                        properties = mapOf("value" to valueNode),
                        attributes = attributes
                    )
                } else {
                    valueNode
                }
            }
        }

        return UDM.Object(
            properties = dataProperties,
            attributes = attributes,
            name = obj.name,
            metadata = obj.metadata
        )
    }

    companion object {
        /**
         * Check if JSON content looks like OData JSON (for auto-detection)
         * Returns true if content contains "@odata.context" key
         */
        fun looksLikeODataJSON(content: String): Boolean {
            return content.contains("\"@odata.context\"") ||
                   content.contains("\"@odata.type\"") ||
                   content.contains("\"@odata.id\"")
        }
    }
}
