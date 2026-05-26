package org.apache.utlx.stdlib.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * JSON Patch (RFC 6902), JSON Pointer (RFC 6901), and JSON Merge Patch (RFC 7396).
 *
 * Pure UDM manipulation — no external dependencies.
 */
object JsonPatchFunctions {

    // =========================================================================
    // JSON Pointer (RFC 6901)
    // =========================================================================

    /**
     * Parse a JSON Pointer string into path segments.
     * "/" separates segments, "~0" escapes "~", "~1" escapes "/".
     * Empty string "" points to the root document.
     */
    internal fun parsePointer(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        if (!pointer.startsWith("/")) throw FunctionArgumentException(
            "Invalid JSON Pointer: '$pointer'. Must start with '/' or be empty string."
        )
        return pointer.substring(1).split("/").map { segment ->
            segment.replace("~1", "/").replace("~0", "~")
        }
    }

    /**
     * Resolve a JSON Pointer to the value at that path in a UDM document.
     */
    internal fun resolvePointer(document: UDM, pointer: String): UDM? {
        val segments = parsePointer(pointer)
        var current: UDM = document
        for (segment in segments) {
            current = when (current) {
                is UDM.Object -> current.get(segment) ?: return null
                is UDM.Array -> {
                    val index = segment.toIntOrNull() ?: return null
                    if (index < 0 || index >= current.elements.size) return null
                    current.elements[index]
                }
                else -> return null
            }
        }
        return current
    }

    /**
     * Set a value at a JSON Pointer path, creating intermediate structure as needed.
     * "-" as the last segment appends to an array.
     */
    internal fun setAtPointer(document: UDM, pointer: String, value: UDM): UDM {
        val segments = parsePointer(pointer)
        if (segments.isEmpty()) return value // replace root

        return setRecursive(document, segments, 0, value)
    }

    private fun setRecursive(node: UDM, segments: List<String>, depth: Int, value: UDM): UDM {
        val segment = segments[depth]
        val isLast = depth == segments.size - 1

        return when (node) {
            is UDM.Object -> {
                if (isLast) {
                    val props = LinkedHashMap(node.properties)
                    props[segment] = value
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                } else {
                    val child = node.get(segment) ?: UDM.Object(emptyMap())
                    val updated = setRecursive(child, segments, depth + 1, value)
                    val props = LinkedHashMap(node.properties)
                    props[segment] = updated
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                }
            }
            is UDM.Array -> {
                if (isLast && segment == "-") {
                    // Append to array
                    UDM.Array(node.elements + value)
                } else {
                    val index = segment.toIntOrNull()
                        ?: throw FunctionArgumentException("Invalid array index: '$segment'")
                    if (isLast) {
                        if (index == node.elements.size) {
                            // Insert at end
                            UDM.Array(node.elements + value)
                        } else if (index in 0 until node.elements.size) {
                            // Insert before index (RFC 6902 "add" inserts, not replaces)
                            val list = node.elements.toMutableList()
                            list.add(index, value)
                            UDM.Array(list)
                        } else {
                            throw FunctionArgumentException("Array index $index out of bounds (size: ${node.elements.size})")
                        }
                    } else {
                        if (index !in 0 until node.elements.size) {
                            throw FunctionArgumentException("Array index $index out of bounds")
                        }
                        val updated = setRecursive(node.elements[index], segments, depth + 1, value)
                        val list = node.elements.toMutableList()
                        list[index] = updated
                        UDM.Array(list)
                    }
                }
            }
            else -> throw FunctionArgumentException("Cannot navigate into ${node::class.simpleName} at segment '$segment'")
        }
    }

    /**
     * Remove a value at a JSON Pointer path.
     */
    internal fun removeAtPointer(document: UDM, pointer: String): UDM {
        val segments = parsePointer(pointer)
        if (segments.isEmpty()) throw FunctionArgumentException("Cannot remove root document")

        return removeRecursive(document, segments, 0)
    }

    private fun removeRecursive(node: UDM, segments: List<String>, depth: Int): UDM {
        val segment = segments[depth]
        val isLast = depth == segments.size - 1

        return when (node) {
            is UDM.Object -> {
                if (isLast) {
                    if (segment !in node.properties) throw FunctionArgumentException("Path not found: $segment")
                    val props = LinkedHashMap(node.properties)
                    props.remove(segment)
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                } else {
                    val child = node.get(segment) ?: throw FunctionArgumentException("Path not found: $segment")
                    val updated = removeRecursive(child, segments, depth + 1)
                    val props = LinkedHashMap(node.properties)
                    props[segment] = updated
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                }
            }
            is UDM.Array -> {
                val index = segment.toIntOrNull()
                    ?: throw FunctionArgumentException("Invalid array index: '$segment'")
                if (index !in 0 until node.elements.size) {
                    throw FunctionArgumentException("Array index $index out of bounds")
                }
                if (isLast) {
                    val list = node.elements.toMutableList()
                    list.removeAt(index)
                    UDM.Array(list)
                } else {
                    val updated = removeRecursive(node.elements[index], segments, depth + 1)
                    val list = node.elements.toMutableList()
                    list[index] = updated
                    UDM.Array(list)
                }
            }
            else -> throw FunctionArgumentException("Cannot navigate into ${node::class.simpleName}")
        }
    }

    /**
     * Replace a value at a JSON Pointer path (must exist).
     */
    internal fun replaceAtPointer(document: UDM, pointer: String, value: UDM): UDM {
        val segments = parsePointer(pointer)
        if (segments.isEmpty()) return value

        // Verify path exists first
        resolvePointer(document, pointer)
            ?: throw FunctionArgumentException("Cannot replace — path '$pointer' does not exist")

        return replaceRecursive(document, segments, 0, value)
    }

    private fun replaceRecursive(node: UDM, segments: List<String>, depth: Int, value: UDM): UDM {
        val segment = segments[depth]
        val isLast = depth == segments.size - 1

        return when (node) {
            is UDM.Object -> {
                if (isLast) {
                    val props = LinkedHashMap(node.properties)
                    props[segment] = value
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                } else {
                    val child = node.get(segment)!!
                    val updated = replaceRecursive(child, segments, depth + 1, value)
                    val props = LinkedHashMap(node.properties)
                    props[segment] = updated
                    UDM.Object(props, node.attributes, node.name, node.metadata)
                }
            }
            is UDM.Array -> {
                val index = segment.toIntOrNull()!!
                if (isLast) {
                    val list = node.elements.toMutableList()
                    list[index] = value
                    UDM.Array(list)
                } else {
                    val updated = replaceRecursive(node.elements[index], segments, depth + 1, value)
                    val list = node.elements.toMutableList()
                    list[index] = updated
                    UDM.Array(list)
                }
            }
            else -> throw FunctionArgumentException("Cannot navigate into ${node::class.simpleName}")
        }
    }

    // =========================================================================
    // jsonPatch (RFC 6902) — atomic apply
    // =========================================================================

    @UTLXFunction(
        description = "Apply RFC 6902 JSON Patch operations to a document. Atomic: all-or-nothing.",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = ["document: The document to patch", "patchOps: Array of RFC 6902 patch operations"],
        returns = "Patched document. Throws if any operation fails (including test).",
        example = """jsonPatch(doc, [{"op":"replace","path":"/name","value":"Jan"}])""",
        tags = ["json", "patch", "rfc6902"],
        since = "1.2"
    )
    fun jsonPatch(args: List<UDM>): UDM {
        if (args.size != 2) throw FunctionArgumentException("jsonPatch expects 2 arguments (document, patchOps), got ${args.size}")

        var document = args[0]
        val patchOps = args[1].asArray()
            ?: throw FunctionArgumentException("jsonPatch second argument must be an array of patch operations")

        // Save original for rollback
        val original = document

        try {
            for ((i, op) in patchOps.elements.withIndex()) {
                val opObj = op.asObject()
                    ?: throw FunctionArgumentException("Patch operation $i must be an object")
                document = applyOperation(document, opObj, i)
            }
            return document
        } catch (e: FunctionArgumentException) {
            // Atomic rollback — return error, don't return partial result
            throw FunctionArgumentException("jsonPatch failed at operation: ${e.message}")
        }
    }

    private fun applyOperation(document: UDM, op: UDM.Object, index: Int): UDM {
        val opType = (op.get("op") as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("Operation $index missing 'op' field")
        val path = (op.get("path") as? UDM.Scalar)?.value as? String
            ?: throw FunctionArgumentException("Operation $index missing 'path' field")

        return when (opType) {
            "add" -> {
                val value = op.get("value") ?: throw FunctionArgumentException("'add' operation $index missing 'value'")
                setAtPointer(document, path, value)
            }
            "remove" -> removeAtPointer(document, path)
            "replace" -> {
                val value = op.get("value") ?: throw FunctionArgumentException("'replace' operation $index missing 'value'")
                replaceAtPointer(document, path, value)
            }
            "move" -> {
                val from = (op.get("from") as? UDM.Scalar)?.value as? String
                    ?: throw FunctionArgumentException("'move' operation $index missing 'from'")
                val value = resolvePointer(document, from)
                    ?: throw FunctionArgumentException("'move' source path '$from' not found")
                val removed = removeAtPointer(document, from)
                setAtPointer(removed, path, value)
            }
            "copy" -> {
                val from = (op.get("from") as? UDM.Scalar)?.value as? String
                    ?: throw FunctionArgumentException("'copy' operation $index missing 'from'")
                val value = resolvePointer(document, from)
                    ?: throw FunctionArgumentException("'copy' source path '$from' not found")
                setAtPointer(document, path, value)
            }
            "test" -> {
                val expected = op.get("value") ?: throw FunctionArgumentException("'test' operation $index missing 'value'")
                val actual = resolvePointer(document, path)
                    ?: throw FunctionArgumentException("'test' path '$path' not found")
                if (!udmEquals(actual, expected)) {
                    throw FunctionArgumentException("'test' failed at '$path': expected ${udmToString(expected)}, got ${udmToString(actual)}")
                }
                document // test doesn't modify
            }
            else -> throw FunctionArgumentException("Unknown patch operation: '$opType'")
        }
    }

    // =========================================================================
    // jsonMergePatch (RFC 7396)
    // =========================================================================

    @UTLXFunction(
        description = "Apply RFC 7396 JSON Merge Patch. Null values remove keys.",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = ["document: The document to patch", "mergePatch: Partial document to merge"],
        returns = "Merged document",
        example = """jsonMergePatch(doc, {"name":"Jan","phone":null})""",
        tags = ["json", "merge", "rfc7396"],
        since = "1.2"
    )
    fun jsonMergePatch(args: List<UDM>): UDM {
        if (args.size != 2) throw FunctionArgumentException("jsonMergePatch expects 2 arguments, got ${args.size}")
        return mergePatchRecursive(args[0], args[1])
    }

    private fun mergePatchRecursive(target: UDM, patch: UDM): UDM {
        if (patch !is UDM.Object) return patch // non-object patch replaces entirely

        var result = if (target is UDM.Object) target else UDM.Object(emptyMap())

        for ((key, value) in patch.properties) {
            result = if (value is UDM.Scalar && value.value == null) {
                // null removes the key
                val props = LinkedHashMap(result.properties)
                props.remove(key)
                UDM.Object(props, result.attributes, result.name, result.metadata)
            } else {
                val existing = result.get(key) ?: UDM.Object(emptyMap())
                val merged = mergePatchRecursive(existing, value)
                val props = LinkedHashMap(result.properties)
                props[key] = merged
                UDM.Object(props, result.attributes, result.name, result.metadata)
            }
        }
        return result
    }

    // =========================================================================
    // jsonDiff — generate RFC 6902 patch from two documents
    // =========================================================================

    @UTLXFunction(
        description = "Generate RFC 6902 JSON Patch from two documents (before → after).",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = ["before: Original document", "after: Modified document"],
        returns = "Array of RFC 6902 patch operations",
        example = """jsonDiff({"name":"Alice"}, {"name":"Bob"}) => [{"op":"replace","path":"/name","value":"Bob"}]""",
        tags = ["json", "diff", "rfc6902"],
        since = "1.2"
    )
    fun jsonDiff(args: List<UDM>): UDM {
        if (args.size != 2) throw FunctionArgumentException("jsonDiff expects 2 arguments (before, after), got ${args.size}")
        val ops = mutableListOf<UDM>()
        diffRecursive(args[0], args[1], "", ops)
        return UDM.Array(ops)
    }

    private fun diffRecursive(before: UDM, after: UDM, path: String, ops: MutableList<UDM>) {
        if (udmEquals(before, after)) return

        when {
            before is UDM.Object && after is UDM.Object -> {
                // Removed keys
                for (key in before.properties.keys) {
                    if (key !in after.properties) {
                        ops.add(patchOp("remove", "$path/${escapePointer(key)}"))
                    }
                }
                // Added or changed keys
                for ((key, afterValue) in after.properties) {
                    val pointer = "$path/${escapePointer(key)}"
                    val beforeValue = before.get(key)
                    if (beforeValue == null) {
                        ops.add(patchOp("add", pointer, afterValue))
                    } else {
                        diffRecursive(beforeValue, afterValue, pointer, ops)
                    }
                }
            }
            before is UDM.Array && after is UDM.Array -> {
                // Simple approach: replace entire array if different
                // (optimal array diff is complex — LCS algorithm)
                ops.add(patchOp("replace", path, after))
            }
            else -> {
                // Different types or scalar values — replace
                ops.add(patchOp("replace", path, after))
            }
        }
    }

    // =========================================================================
    // validateJsonPatch
    // =========================================================================

    @UTLXFunction(
        description = "Validate that a patch array conforms to RFC 6902 structure.",
        minArgs = 1,
        maxArgs = 1,
        category = "JSON",
        parameters = ["patchOps: Array of patch operations to validate"],
        returns = "Object with 'valid' boolean and optional 'errors' array",
        example = """validateJsonPatch([{"op":"add","path":"/x","value":1}]) => {"valid":true}""",
        tags = ["json", "patch", "validation"],
        since = "1.2"
    )
    fun validateJsonPatch(args: List<UDM>): UDM {
        if (args.size != 1) throw FunctionArgumentException("validateJsonPatch expects 1 argument, got ${args.size}")
        val patchOps = args[0].asArray()
            ?: return UDM.Object.of("valid" to UDM.Scalar(false), "errors" to UDM.Array(listOf(UDM.Scalar("Argument must be an array"))))

        val errors = mutableListOf<UDM>()
        for ((i, op) in patchOps.elements.withIndex()) {
            val obj = op.asObject()
            if (obj == null) {
                errors.add(UDM.Scalar("Operation $i: must be an object"))
                continue
            }
            val opType = (obj.get("op") as? UDM.Scalar)?.value as? String
            if (opType == null) {
                errors.add(UDM.Scalar("Operation $i: missing 'op' field"))
                continue
            }
            if (opType !in listOf("add", "remove", "replace", "move", "copy", "test")) {
                errors.add(UDM.Scalar("Operation $i: unknown op '$opType'"))
            }
            if ((obj.get("path") as? UDM.Scalar)?.value as? String == null) {
                errors.add(UDM.Scalar("Operation $i: missing 'path' field"))
            }
            if (opType in listOf("add", "replace", "test") && obj.get("value") == null) {
                errors.add(UDM.Scalar("Operation $i: '$opType' requires 'value' field"))
            }
            if (opType in listOf("move", "copy") && (obj.get("from") as? UDM.Scalar)?.value as? String == null) {
                errors.add(UDM.Scalar("Operation $i: '$opType' requires 'from' field"))
            }
        }

        return if (errors.isEmpty()) {
            UDM.Object.of("valid" to UDM.Scalar(true))
        } else {
            UDM.Object.of("valid" to UDM.Scalar(false), "errors" to UDM.Array(errors))
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun escapePointer(segment: String): String =
        segment.replace("~", "~0").replace("/", "~1")

    private fun patchOp(op: String, path: String, value: UDM? = null): UDM {
        val props = mutableMapOf<String, UDM>("op" to UDM.Scalar(op), "path" to UDM.Scalar(path))
        if (value != null) props["value"] = value
        return UDM.Object(props)
    }

    private fun udmEquals(a: UDM, b: UDM): Boolean {
        return when {
            a is UDM.Scalar && b is UDM.Scalar -> {
                // Handle numeric comparison: 2 (Int) == 2.0 (Double)
                val av = a.value
                val bv = b.value
                if (av is Number && bv is Number) av.toDouble() == bv.toDouble()
                else av == bv
            }
            a is UDM.Object && b is UDM.Object -> {
                a.properties.size == b.properties.size &&
                a.properties.all { (k, v) -> b.properties[k]?.let { udmEquals(v, it) } == true }
            }
            a is UDM.Array && b is UDM.Array -> {
                a.elements.size == b.elements.size &&
                a.elements.zip(b.elements).all { (x, y) -> udmEquals(x, y) }
            }
            else -> a == b
        }
    }

    private fun udmToString(udm: UDM): String = when (udm) {
        is UDM.Scalar -> udm.value?.toString() ?: "null"
        is UDM.Object -> "{...}"
        is UDM.Array -> "[${udm.elements.size} items]"
        else -> udm.toString()
    }
}
