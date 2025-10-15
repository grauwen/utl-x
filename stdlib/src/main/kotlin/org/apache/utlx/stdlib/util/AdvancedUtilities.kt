package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import java.util.*

/**
 * Remaining DataWeave Utility Modules:
 * - dw::util::Values - Value utilities (update, mask, etc.)
 * - dw::util::Diff - Structure comparison and diffing
 * - dw::module::Mime - MIME type handling
 * - dw::module::Multipart - Multipart data handling
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/util/AdvancedUtilities.kt
 */

// ==================== VALUES FUNCTIONS ====================

/**
 * Value Utilities
 * Similar to DataWeave's dw::util::Values module.
 */
object ValueFunctions {
    
    /**
     * Update value at path in structure
     * 
     * Usage: update(obj, ["user", "name"], "New Name")
     * 
     * Updates nested value, creating intermediate structures if needed.
     */
    fun update(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw IllegalArgumentException("update expects 3 arguments (structure, path, value), got ${args.size}")
        }
        
        val structure = args[0]
        val path = args[1]
        val newValue = args[2]
        
        if (path !is UDM.Array) {
            throw IllegalArgumentException("Path must be an array")
        }
        
        if (path.elements.isEmpty()) {
            return newValue
        }
        
        return updateRecursive(structure, path.elements, newValue)
    }
    
    private fun updateRecursive(current: UDM, path: List<UDM>, newValue: UDM): UDM {
        if (path.isEmpty()) {
            return newValue
        }
        
        val key = path[0]
        if (key !is UDM.Scalar) {
            throw IllegalArgumentException("Path keys must be scalars")
        }
        
        return when (current) {
            is UDM.Object -> {
                val keyStr = key.value.toString()
                val existingValue = current.properties[keyStr] ?: UDM.Object(emptyMap(), emptyMap())
                val updatedValue = updateRecursive(existingValue, path.drop(1), newValue)
                
                val newProps = current.properties.toMutableMap()
                newProps[keyStr] = updatedValue
                
                UDM.Object(newProps, current.attributes)
            }
            is UDM.Array -> {
                val index = (key.value as? Number)?.toInt() 
                    ?: throw IllegalArgumentException("Array index must be number")
                
                val newElements = current.elements.toMutableList()
                
                // Ensure array is large enough
                while (newElements.size <= index) {
                    newElements.add(UDM.Scalar(null))
                }
                
                newElements[index] = updateRecursive(newElements[index], path.drop(1), newValue)
                
                UDM.Array(newElements)
            }
            else -> {
                // Can't update scalar, return new value
                newValue
            }
        }
    }
    
    /**
     * Mask sensitive values in structure
     * 
     * Usage: mask(obj, ["password", "ssn", "creditCard"])
     * Result: Replaces specified fields with "***"
     * 
     * Useful for logging/debugging without exposing sensitive data.
     */
    fun mask(args: List<UDM>): UDM {
        if (args.size !in 2..3) {
            throw IllegalArgumentException("mask expects 2-3 arguments, got ${args.size}")
        }
        
        val structure = args[0]
        val fieldsToMask = args[1]
        val maskString = if (args.size == 3) {
            (args[2] as? UDM.Scalar)?.value?.toString() ?: "***"
        } else {
            "***"
        }
        
        if (fieldsToMask !is UDM.Array) {
            throw IllegalArgumentException("Fields to mask must be array")
        }
        
        val fieldSet = fieldsToMask.elements
            .mapNotNull { (it as? UDM.Scalar)?.value?.toString() }
            .toSet()
        
        return maskRecursive(structure, fieldSet, maskString)
    }
    
    private fun maskRecursive(node: UDM, fieldsToMask: Set<String>, maskString: String): UDM {
        return when (node) {
            is UDM.Object -> {
                val maskedProps = node.properties.mapValues { (key, value) ->
                    if (key in fieldsToMask) {
                        UDM.Scalar(maskString)
                    } else {
                        maskRecursive(value, fieldsToMask, maskString)
                    }
                }
                UDM.Object(maskedProps, node.attributes)
            }
            is UDM.Array -> {
                val maskedElements = node.elements.map { 
                    maskRecursive(it, fieldsToMask, maskString) 
                }
                UDM.Array(maskedElements)
            }
            else -> node
        }
    }
    
    /**
     * Pick specific fields from object (recursive)
     * 
     * Usage: pick(obj, ["name", "email"])
     * Result: New object with only specified fields
     */
    fun pick(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("pick expects 2 arguments (object, fields), got ${args.size}")
        }
        
        val obj = args[0]
        val fields = args[1]
        
        if (obj !is UDM.Object) {
            return obj
        }
        
        if (fields !is UDM.Array) {
            throw IllegalArgumentException("Fields must be array")
        }
        
        val fieldSet = fields.elements
            .mapNotNull { (it as? UDM.Scalar)?.value?.toString() }
            .toSet()
        
        val picked = obj.properties.filterKeys { it in fieldSet }
        
        return UDM.Object(picked, obj.attributes)
    }
    
    /**
     * Omit specific fields from object (recursive)
     * 
     * Usage: omit(obj, ["password", "internal"])
     * Result: New object without specified fields
     */
    fun omit(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("omit expects 2 arguments (object, fields), got ${args.size}")
        }
        
        val obj = args[0]
        val fields = args[1]
        
        if (obj !is UDM.Object) {
            return obj
        }
        
        if (fields !is UDM.Array) {
            throw IllegalArgumentException("Fields must be array")
        }
        
        val fieldSet = fields.elements
            .mapNotNull { (it as? UDM.Scalar)?.value?.toString() }
            .toSet()
        
        val filtered = obj.properties.filterKeys { it !in fieldSet }
        
        return UDM.Object(filtered, obj.attributes)
    }
    
    /**
     * Get default value if null or undefined
     * 
     * Usage: defaultValue(input.optional, "default")
     */
    fun defaultValue(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("defaultValue expects 2 arguments, got ${args.size}")
        }
        
        val value = args[0]
        val default = args[1]
        
        return if (value is UDM.Scalar && value.value == null) {
            default
        } else {
            value
        }
    }
}

// ==================== DIFF FUNCTIONS ====================

/**
 * Structure Comparison and Diffing
 * Similar to DataWeave's dw::util::Diff module.
 */
object DiffFunctions {
    
    /**
     * Compare two structures and return differences
     * 
     * Usage: diff(obj1, obj2)
     * Result: {
     *   added: [...],
     *   removed: [...],
     *   changed: [...]
     * }
     */
    fun diff(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("diff expects 2 arguments (old, new), got ${args.size}")
        }
        
        val old = args[0]
        val new = args[1]
        
        val differences = computeDiff(old, new, emptyList())
        
        return UDM.Object(mapOf(
            "changes" to UDM.Array(differences)
        ), emptyMap())
    }
    
    private fun computeDiff(old: UDM, new: UDM, path: List<String>): List<UDM> {
        val diffs = mutableListOf<UDM>()
        
        when {
            old is UDM.Object && new is UDM.Object -> {
                // Find added and changed
                new.properties.forEach { (key, newValue) ->
                    val oldValue = old.properties[key]
                    if (oldValue == null) {
                        diffs.add(createDiff("added", path + key, null, newValue))
                    } else if (!valuesEqual(oldValue, newValue)) {
                        diffs.addAll(computeDiff(oldValue, newValue, path + key))
                    }
                }
                
                // Find removed
                old.properties.forEach { (key, oldValue) ->
                    if (!new.properties.containsKey(key)) {
                        diffs.add(createDiff("removed", path + key, oldValue, null))
                    }
                }
            }
            old is UDM.Array && new is UDM.Array -> {
                val maxSize = maxOf(old.elements.size, new.elements.size)
                for (i in 0 until maxSize) {
                    val oldElem = old.elements.getOrNull(i)
                    val newElem = new.elements.getOrNull(i)
                    
                    when {
                        oldElem == null -> diffs.add(createDiff("added", path + i.toString(), null, newElem))
                        newElem == null -> diffs.add(createDiff("removed", path + i.toString(), oldElem, null))
                        !valuesEqual(oldElem, newElem) -> diffs.addAll(computeDiff(oldElem, newElem, path + i.toString()))
                    }
                }
            }
            !valuesEqual(old, new) -> {
                diffs.add(createDiff("changed", path, old, new))
            }
        }
        
        return diffs
    }
    
    private fun createDiff(type: String, path: List<String>, oldValue: UDM?, newValue: UDM?): UDM {
        val props = mutableMapOf<String, UDM>(
            "type" to UDM.Scalar(type),
            "path" to UDM.Array(path.map { UDM.Scalar(it) })
        )
        
        oldValue?.let { props["oldValue"] = it }
        newValue?.let { props["newValue"] = it }
        
        return UDM.Object(props, emptyMap())
    }
    
    private fun valuesEqual(v1: UDM, v2: UDM): Boolean {
        return when {
            v1 is UDM.Scalar && v2 is UDM.Scalar -> v1.value == v2.value
            v1 is UDM.Array && v2 is UDM.Array -> {
                v1.elements.size == v2.elements.size &&
                v1.elements.zip(v2.elements).all { (a, b) -> valuesEqual(a, b) }
            }
            v1 is UDM.Object && v2 is UDM.Object -> {
                v1.properties.size == v2.properties.size &&
                v1.properties.all { (key, value) ->
                    v2.properties[key]?.let { valuesEqual(value, it) } ?: false
                }
            }
            else -> false
        }
    }
    
    /**
     * Deep equality check
     * 
     * Usage: deepEquals(obj1, obj2) => true/false
     */
    fun deepEquals(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("deepEquals expects 2 arguments, got ${args.size}")
        }
        
        return UDM.Scalar(valuesEqual(args[0], args[1]))
    }
    
    /**
     * Apply diff/patch to structure
     * 
     * Usage: patch(originalObj, diffObj)
     * 
     * Applies changes from diff to create new structure.
     */
    fun patch(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("patch expects 2 arguments (structure, diff), got ${args.size}")
        }
        
        var result = args[0]
        val diff = args[1]
        
        if (diff !is UDM.Object) {
            throw IllegalArgumentException("Diff must be object")
        }
        
        val changes = diff.properties["changes"] as? UDM.Array 
            ?: return result
        
        // Apply each change
        for (change in changes.elements) {
            if (change is UDM.Object) {
                result = applyChange(result, change)
            }
        }
        
        return result
    }
    
    private fun applyChange(structure: UDM, change: UDM.Object): UDM {
        val type = (change.properties["type"] as? UDM.Scalar)?.value as? String
        val path = (change.properties["path"] as? UDM.Array)?.elements ?: return structure
        
        return when (type) {
            "added", "changed" -> {
                val newValue = change.properties["newValue"] ?: return structure
                updateRecursive(structure, path, newValue)
            }
            "removed" -> {
                removeAtPath(structure, path)
            }
            else -> structure
        }
    }
    
    private fun updateRecursive(current: UDM, path: List<UDM>, newValue: UDM): UDM {
        if (path.isEmpty()) return newValue
        
        val key = path[0]
        if (key !is UDM.Scalar) return current
        
        return when (current) {
            is UDM.Object -> {
                val keyStr = key.value.toString()
                val existing = current.properties[keyStr] ?: UDM.Object(emptyMap(), emptyMap())
                val updated = updateRecursive(existing, path.drop(1), newValue)
                
                UDM.Object(current.properties + (keyStr to updated), current.attributes)
            }
            is UDM.Array -> {
                val index = (key.value as? Number)?.toInt() ?: return current
                val newElements = current.elements.toMutableList()
                
                while (newElements.size <= index) {
                    newElements.add(UDM.Scalar(null))
                }
                
                newElements[index] = updateRecursive(newElements[index], path.drop(1), newValue)
                UDM.Array(newElements)
            }
            else -> current
        }
    }
    
    private fun removeAtPath(structure: UDM, path: List<UDM>): UDM {
        if (path.isEmpty()) return UDM.Scalar(null)
        if (path.size == 1) {
            val key = path[0]
            if (key !is UDM.Scalar) return structure
            
            return when (structure) {
                is UDM.Object -> {
                    UDM.Object(
                        structure.properties - key.value.toString(),
                        structure.attributes
                    )
                }
                is UDM.Array -> {
                    val index = (key.value as? Number)?.toInt() ?: return structure
                    UDM.Array(structure.elements.filterIndexed { i, _ -> i != index })
                }
                else -> structure
            }
        }
        
        // Recursive removal
        val key = path[0]
        if (key !is UDM.Scalar) return structure
        
        return when (structure) {
            is UDM.Object -> {
                val keyStr = key.value.toString()
                val existing = structure.properties[keyStr] ?: return structure
                val updated = removeAtPath(existing, path.drop(1))
                
                UDM.Object(structure.properties + (keyStr to updated), structure.attributes)
            }
            is UDM.Array -> {
                val index = (key.value as? Number)?.toInt() ?: return structure
                val newElements = structure.elements.toMutableList()
                if (index < newElements.size) {
                    newElements[index] = removeAtPath(newElements[index], path.drop(1))
                }
                UDM.Array(newElements)
            }
            else -> structure
        }
    }
}

// ==================== MIME FUNCTIONS ====================

/**
 * MIME Type Handling
 * Similar to DataWeave's dw::module::Mime module.
 */
object MimeFunctions {
    
    private val mimeTypes = mapOf(
        // Text
        "txt" to "text/plain",
        "html" to "text/html",
        "css" to "text/css",
        "js" to "text/javascript",
        "xml" to "text/xml",
        "csv" to "text/csv",
        
        // Application
        "json" to "application/json",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        
        // Image
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "webp" to "image/webp",
        
        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        
        // Video
        "mp4" to "video/mp4",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime"
    )
    
    /**
     * Get MIME type from file extension
     * 
     * Usage: getMimeType("document.pdf") => "application/pdf"
     * Usage: getMimeType("json") => "application/json"
     */
    fun getMimeType(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getMimeType expects 1 argument, got ${args.size}")
        }
        
        val input = args[0]
        if (input !is UDM.Scalar || input.value !is String) {
            throw IllegalArgumentException("getMimeType expects string")
        }
        
        val filename = input.value as String
        val extension = if (filename.contains(".")) {
            filename.substringAfterLast(".").lowercase()
        } else {
            filename.lowercase()
        }
        
        val mimeType = mimeTypes[extension] ?: "application/octet-stream"
        
        return UDM.Scalar(mimeType)
    }
    
    /**
     * Get file extension from MIME type
     * 
     * Usage: getExtension("application/json") => "json"
     */
    fun getExtension(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getExtension expects 1 argument, got ${args.size}")
        }
        
        val mimeType = args[0]
        if (mimeType !is UDM.Scalar || mimeType.value !is String) {
            throw IllegalArgumentException("getExtension expects string")
        }
        
        val mime = mimeType.value as String
        val extension = mimeTypes.entries.firstOrNull { it.value == mime }?.key
        
        return UDM.Scalar(extension ?: "bin")
    }
    
    /**
     * Parse Content-Type header
     * 
     * Usage: parseContentType("application/json; charset=utf-8")
     * Result: {mimeType: "application/json", charset: "utf-8"}
     */
    fun parseContentType(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("parseContentType expects 1 argument, got ${args.size}")
        }
        
        val contentType = args[0]
        if (contentType !is UDM.Scalar || contentType.value !is String) {
            throw IllegalArgumentException("parseContentType expects string")
        }
        
        val ct = contentType.value as String
        val parts = ct.split(";").map { it.trim() }
        
        val mimeType = parts[0]
        val params = mutableMapOf<String, UDM>()
        
        parts.drop(1).forEach { param ->
            val (key, value) = param.split("=", limit = 2).map { it.trim() }
            params[key] = UDM.Scalar(value.trim('"'))
        }
        
        params["mimeType"] = UDM.Scalar(mimeType)
        
        return UDM.Object(params, emptyMap())
    }
    
    /**
     * Build Content-Type header
     * 
     * Usage: buildContentType({mimeType: "application/json", charset: "utf-8"})
     * Result: "application/json; charset=utf-8"
     */
    fun buildContentType(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("buildContentType expects 1 argument, got ${args.size}")
        }
        
        val params = args[0]
        if (params !is UDM.Object) {
            throw IllegalArgumentException("buildContentType expects object")
        }
        
        val mimeType = (params.properties["mimeType"] as? UDM.Scalar)?.value?.toString()
            ?: throw IllegalArgumentException("mimeType is required")
        
        val otherParams = params.properties
            .filterKeys { it != "mimeType" }
            .map { (key, value) ->
                "$key=${(value as? UDM.Scalar)?.value}"
            }
        
        val result = if (otherParams.isEmpty()) {
            mimeType
        } else {
            "$mimeType; ${otherParams.joinToString("; ")}"
        }
        
        return UDM.Scalar(result)
    }
}

// ==================== MULTIPART FUNCTIONS ====================

/**
 * Multipart Data Handling
 * Similar to DataWeave's dw::module::Multipart module.
 */
object MultipartFunctions {
    
    /**
     * Parse multipart boundary from Content-Type
     * 
     * Usage: parseBoundary("multipart/form-data; boundary=----WebKitFormBoundary")
     * Result: "----WebKitFormBoundary"
     */
    fun parseBoundary(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("parseBoundary expects 1 argument, got ${args.size}")
        }
        
        val contentType = args[0]
        if (contentType !is UDM.Scalar || contentType.value !is String) {
            throw IllegalArgumentException("parseBoundary expects string")
        }
        
        val ct = contentType.value as String
        val boundaryPrefix = "boundary="
        val boundaryIndex = ct.indexOf(boundaryPrefix)
        
        if (boundaryIndex == -1) {
            return UDM.Scalar(null)
        }
        
        val boundary = ct.substring(boundaryIndex + boundaryPrefix.length)
            .split(";")[0]
            .trim()
            .trim('"')
        
        return UDM.Scalar(boundary)
    }
    
    /**
     * Build multipart body from parts
     * 
     * Usage: buildMultipart(parts, boundary)
     * 
     * Creates multipart/form-data body string.
     */
    fun buildMultipart(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("buildMultipart expects 2 arguments (parts, boundary), got ${args.size}")
        }
        
        val parts = args[0]
        val boundary = args[1]
        
        if (parts !is UDM.Array) {
            throw IllegalArgumentException("Parts must be array")
        }
        
        if (boundary !is UDM.Scalar || boundary.value !is String) {
            throw IllegalArgumentException("Boundary must be string")
        }
        
        val boundaryStr = boundary.value as String
        val body = StringBuilder()
        
        parts.elements.forEach { part ->
            if (part is UDM.Object) {
                body.append("--$boundaryStr\r\n")
                
                val name = (part.properties["name"] as? UDM.Scalar)?.value?.toString()
                val filename = (part.properties["filename"] as? UDM.Scalar)?.value?.toString()
                val contentType = (part.properties["contentType"] as? UDM.Scalar)?.value?.toString()
                val content = (part.properties["content"] as? UDM.Scalar)?.value?.toString() ?: ""
                
                if (filename != null) {
                    body.append("Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\n")
                } else {
                    body.append("Content-Disposition: form-data; name=\"$name\"\r\n")
                }
                
                if (contentType != null) {
                    body.append("Content-Type: $contentType\r\n")
                }
                
                body.append("\r\n")
                body.append(content)
                body.append("\r\n")
            }
        }
        
        body.append("--$boundaryStr--\r\n")
        
        return UDM.Scalar(body.toString())
    }
    
    /**
     * Generate random boundary string
     * 
     * Usage: generateBoundary()
     * Result: "----WebKitFormBoundary7MA4YWxkTrZu0gW"
     */
    fun generateBoundary(args: List<UDM>): UDM {
        if (args.isNotEmpty()) {
            throw IllegalArgumentException("generateBoundary expects no arguments, got ${args.size}")
        }
        
        val random = UUID.randomUUID().toString().replace("-", "")
        val boundary = "----WebKitFormBoundary$random"
        
        return UDM.Scalar(boundary)
    }
    
    /**
     * Create multipart part object
     * 
     * Usage: createPart("fieldName", "value", "text/plain")
     */
    fun createPart(args: List<UDM>): UDM {
        if (args.size !in 2..4) {
            throw IllegalArgumentException("createPart expects 2-4 arguments, got ${args.size}")
        }
        
        val name = args[0]
        val content = args[1]
        val contentType = if (args.size >= 3) args[2] else null
        val filename = if (args.size >= 4) args[3] else null
        
        val props = mutableMapOf<String, UDM>(
            "name" to name,
            "content" to content
        )
        
        contentType?.let { props["contentType"] = it }
        filename?.let { props["filename"] = it }
        
        return UDM.Object(props, emptyMap())
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to new registration methods:
 * 
 * private fun registerValueFunctions() {
 *     register("update", ValueFunctions::update)
 *     register("mask", ValueFunctions::mask)
 *     register("pick", ValueFunctions::pick)
 *     register("omit", ValueFunctions::omit)
 *     register("defaultValue", ValueFunctions::defaultValue)
 * }
 * 
 * private fun registerDiffFunctions() {
 *     register("diff", DiffFunctions::diff)
 *     register("deepEquals", DiffFunctions::deepEquals)
 *     register("patch", DiffFunctions::patch)
 * }
 * 
 * private fun registerMimeFunctions() {
 *     register("getMimeType", MimeFunctions::getMimeType)
 *     register("getExtension", MimeFunctions::getExtension)
 *     register("parseContentType", MimeFunctions::parseContentType)
 *     register("buildContentType", MimeFunctions::buildContentType)
 * }
 * 
 * private fun registerMultipartFunctions() {
 *     register("parseBoundary", MultipartFunctions::parseBoundary)
 *     register("buildMultipart", MultipartFunctions::buildMultipart)
 *     register("generateBoundary", MultipartFunctions::generateBoundary)
 *     register("createPart", MultipartFunctions::createPart)
 * }
 * 
 * Then call in init block:
 * init {
 *     registerConversionFunctions()
 *     registerURLFunctions()
 *     registerTreeFunctions()
 *     registerCoercionFunctions()
 *     registerTimerFunctions()
 *     registerValueFunctions()      // ADD!
 *     registerDiffFunctions()        // ADD!
 *     registerMimeFunctions()        // ADD!
 *     registerMultipartFunctions()   // ADD!
 *     // ... rest
 * }
 */
