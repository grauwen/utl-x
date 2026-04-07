package org.apache.utlx.formats.odata

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.json.JSONSerializer

/**
 * OData JSON Serializer - Converts UDM to OData JSON format
 *
 * Extends the standard JSON serializer with OData annotation generation:
 * - Adds @odata.context from options or UDM root attributes
 * - Adds @odata.type, @odata.id per entity (in "full" mode)
 * - Wraps arrays in { "value": [...] } when wrapCollection is true
 *
 * Metadata levels:
 * - "minimal": Only @odata.context (if context option set)
 * - "full": Add @odata.context, @odata.type, @odata.id where applicable
 * - "none": Plain JSON output (no annotations)
 */
class ODataJSONSerializer(
    private val options: Map<String, Any> = emptyMap()
) {
    private val metadataLevel: String = (options["metadata"] as? String) ?: "minimal"
    private val contextUrl: String? = options["context"] as? String
    private val wrapCollection: Boolean = (options["wrapCollection"] as? Boolean) ?: true
    private val prettyPrint: Boolean = (options["prettyPrint"] as? Boolean) ?: true

    fun serialize(udm: UDM): String {
        // If metadata=none, serialize as plain JSON
        if (metadataLevel == "none") {
            return JSONSerializer(prettyPrint = prettyPrint).serialize(udm)
        }

        // Build the OData-annotated UDM
        val annotated = annotateNode(udm, isRoot = true)

        // Serialize to JSON
        return JSONSerializer(prettyPrint = prettyPrint).serialize(annotated)
    }

    /**
     * Annotate a UDM node with OData metadata
     */
    private fun annotateNode(node: UDM, isRoot: Boolean = false): UDM {
        return when (node) {
            is UDM.Object -> annotateObject(node, isRoot)
            is UDM.Array -> annotateArray(node, isRoot)
            else -> node
        }
    }

    /**
     * Annotate an object with @odata.* properties
     */
    private fun annotateObject(obj: UDM.Object, isRoot: Boolean): UDM {
        val properties = linkedMapOf<String, UDM>()

        // Add @odata.context at root level
        if (isRoot) {
            val context = contextUrl ?: obj.attributes["odata.context"]
            if (context != null) {
                properties["@odata.context"] = UDM.Scalar.string(context)
            }
        }

        // In full mode, add @odata.type and @odata.id from attributes
        if (metadataLevel == "full") {
            obj.attributes["odata.type"]?.let {
                properties["@odata.type"] = UDM.Scalar.string(it)
            }
            obj.attributes["odata.id"]?.let {
                properties["@odata.id"] = UDM.Scalar.string(it)
            }
            obj.attributes["odata.etag"]?.let {
                properties["@odata.etag"] = UDM.Scalar.string(it)
            }
        }

        // Add regular properties (recursively annotated)
        for ((key, value) in obj.properties) {
            properties[key] = annotateNode(value)
        }

        // Re-emit per-property annotations from attributes in full mode
        if (metadataLevel == "full") {
            for ((key, value) in obj.properties) {
                val propNode = value
                if (propNode is UDM.Object) {
                    for ((attrKey, attrValue) in propNode.attributes) {
                        if (attrKey.startsWith("odata.")) {
                            properties["$key@$attrKey"] = UDM.Scalar.string(attrValue)
                        }
                    }
                }
            }
        }

        return UDM.Object(
            properties = properties,
            name = obj.name,
            metadata = obj.metadata
        )
    }

    /**
     * Annotate an array, optionally wrapping in { "value": [...] }
     */
    private fun annotateArray(array: UDM.Array, isRoot: Boolean): UDM {
        val processedElements = array.elements.map { annotateNode(it) }

        if (wrapCollection) {
            // Wrap in { "value": [...] } with optional @odata.context
            val wrapperProps = linkedMapOf<String, UDM>()

            // Add @odata.context from options
            val context = if (isRoot) contextUrl else null
            if (context != null) {
                wrapperProps["@odata.context"] = UDM.Scalar.string(context)
            }

            // In full mode, add @odata.count if we know the count
            if (metadataLevel == "full") {
                wrapperProps["@odata.count"] = UDM.Scalar.number(processedElements.size.toDouble())
            }

            wrapperProps["value"] = UDM.Array(processedElements)

            return UDM.Object(properties = wrapperProps)
        }

        return UDM.Array(processedElements)
    }
}
