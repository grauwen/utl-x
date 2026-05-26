package org.apache.utlx.stdlib.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F15: Comprehensive tests for JSON Patch (RFC 6902), JSON Merge Patch (RFC 7396),
 * JSON Diff, and JSON Pointer (RFC 6901).
 */
class JsonPatchFunctionsTest {

    // =========================================================================
    // JSON Pointer (RFC 6901) — internal but critical
    // =========================================================================

    @Test fun `parsePointer - root`() = assertEquals(emptyList(), JsonPatchFunctions.parsePointer(""))
    @Test fun `parsePointer - simple`() = assertEquals(listOf("foo"), JsonPatchFunctions.parsePointer("/foo"))
    @Test fun `parsePointer - nested`() = assertEquals(listOf("foo", "bar"), JsonPatchFunctions.parsePointer("/foo/bar"))
    @Test fun `parsePointer - array index`() = assertEquals(listOf("items", "0"), JsonPatchFunctions.parsePointer("/items/0"))
    @Test fun `parsePointer - escape tilde`() = assertEquals(listOf("a~b"), JsonPatchFunctions.parsePointer("/a~0b"))
    @Test fun `parsePointer - escape slash`() = assertEquals(listOf("a/b"), JsonPatchFunctions.parsePointer("/a~1b"))
    @Test fun `parsePointer - invalid throws`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.parsePointer("noslash") } }

    @Test fun `resolvePointer - root`() {
        val doc = UDM.Object.of("a" to UDM.Scalar(1))
        assertEquals(doc, JsonPatchFunctions.resolvePointer(doc, ""))
    }
    @Test fun `resolvePointer - simple field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        assertEquals("Alice", (JsonPatchFunctions.resolvePointer(doc, "/name") as UDM.Scalar).value)
    }
    @Test fun `resolvePointer - nested`() {
        val doc = UDM.Object.of("a" to UDM.Object.of("b" to UDM.Scalar(42)))
        assertEquals(42, (JsonPatchFunctions.resolvePointer(doc, "/a/b") as UDM.Scalar).value)
    }
    @Test fun `resolvePointer - array element`() {
        val doc = UDM.Object.of("items" to UDM.Array(listOf(UDM.Scalar("x"), UDM.Scalar("y"))))
        assertEquals("y", (JsonPatchFunctions.resolvePointer(doc, "/items/1") as UDM.Scalar).value)
    }
    @Test fun `resolvePointer - not found`() = assertNull(JsonPatchFunctions.resolvePointer(UDM.Object(emptyMap()), "/missing"))

    // =========================================================================
    // jsonPatch — add
    // =========================================================================

    @Test fun `patch add - new field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Array(listOf(patchOp("add", "/age", UDM.Scalar(30))))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals(30, (result.get("age") as UDM.Scalar).value)
        assertEquals("Alice", (result.get("name") as UDM.Scalar).value)
    }

    @Test fun `patch add - nested field`() {
        val doc = UDM.Object.of("address" to UDM.Object(emptyMap()))
        val patch = UDM.Array(listOf(patchOp("add", "/address/city", UDM.Scalar("Amsterdam"))))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Amsterdam", ((result.get("address") as UDM.Object).get("city") as UDM.Scalar).value)
    }

    @Test fun `patch add - array append`() {
        val doc = UDM.Object.of("items" to UDM.Array(listOf(UDM.Scalar("a"))))
        val patch = UDM.Array(listOf(patchOp("add", "/items/-", UDM.Scalar("b"))))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        val items = result.get("items") as UDM.Array
        assertEquals(2, items.elements.size)
        assertEquals("b", (items.elements[1] as UDM.Scalar).value)
    }

    // =========================================================================
    // jsonPatch — remove
    // =========================================================================

    @Test fun `patch remove - field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"), "phone" to UDM.Scalar("123"))
        val patch = UDM.Array(listOf(patchOp("remove", "/phone")))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertNull(result.get("phone"))
        assertEquals("Alice", (result.get("name") as UDM.Scalar).value)
    }

    @Test fun `patch remove - nonexistent throws`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Array(listOf(patchOp("remove", "/missing")))
        assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonPatch(listOf(doc, patch)) }
    }

    // =========================================================================
    // jsonPatch — replace
    // =========================================================================

    @Test fun `patch replace - existing field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Array(listOf(patchOp("replace", "/name", UDM.Scalar("Bob"))))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Bob", (result.get("name") as UDM.Scalar).value)
    }

    @Test fun `patch replace - nonexistent throws`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Array(listOf(patchOp("replace", "/missing", UDM.Scalar("x"))))
        assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonPatch(listOf(doc, patch)) }
    }

    // =========================================================================
    // jsonPatch — move
    // =========================================================================

    @Test fun `patch move - field`() {
        val doc = UDM.Object.of("old" to UDM.Scalar("value"), "keep" to UDM.Scalar("yes"))
        val patch = UDM.Array(listOf(moveOp("/old", "/new")))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertNull(result.get("old"))
        assertEquals("value", (result.get("new") as UDM.Scalar).value)
    }

    // =========================================================================
    // jsonPatch — copy
    // =========================================================================

    @Test fun `patch copy - field`() {
        val doc = UDM.Object.of("source" to UDM.Scalar("hello"))
        val patch = UDM.Array(listOf(copyOp("/source", "/target")))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals("hello", (result.get("source") as UDM.Scalar).value)
        assertEquals("hello", (result.get("target") as UDM.Scalar).value)
    }

    // =========================================================================
    // jsonPatch — test
    // =========================================================================

    @Test fun `patch test - passes`() {
        val doc = UDM.Object.of("version" to UDM.Scalar(2))
        val patch = UDM.Array(listOf(
            patchOp("test", "/version", UDM.Scalar(2)),
            patchOp("replace", "/version", UDM.Scalar(3))
        ))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals(3, (result.get("version") as UDM.Scalar).value)
    }

    @Test fun `patch test - fails aborts all`() {
        val doc = UDM.Object.of("version" to UDM.Scalar(1))
        val patch = UDM.Array(listOf(
            patchOp("test", "/version", UDM.Scalar(2)),
            patchOp("replace", "/version", UDM.Scalar(3))
        ))
        assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonPatch(listOf(doc, patch)) }
    }

    // =========================================================================
    // jsonPatch — multiple operations (atomic)
    // =========================================================================

    @Test fun `patch multiple ops`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(25))
        val patch = UDM.Array(listOf(
            patchOp("replace", "/name", UDM.Scalar("Bob")),
            patchOp("add", "/city", UDM.Scalar("Amsterdam")),
            patchOp("remove", "/age")
        ))
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Bob", (result.get("name") as UDM.Scalar).value)
        assertEquals("Amsterdam", (result.get("city") as UDM.Scalar).value)
        assertNull(result.get("age"))
    }

    @Test fun `patch empty array - no change`() {
        val doc = UDM.Object.of("x" to UDM.Scalar(1))
        val patch = UDM.Array(emptyList())
        val result = JsonPatchFunctions.jsonPatch(listOf(doc, patch)) as UDM.Object
        assertEquals(1, (result.get("x") as UDM.Scalar).value)
    }

    // =========================================================================
    // jsonPatch — error handling
    // =========================================================================

    @Test fun `patch wrong arg count`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonPatch(listOf(UDM.Scalar(1))) } }
    @Test fun `patch not array`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonPatch(listOf(UDM.Object(emptyMap()), UDM.Scalar("x"))) } }
    @Test fun `patch missing op field`() {
        assertThrows<FunctionArgumentException> {
            JsonPatchFunctions.jsonPatch(listOf(UDM.Object(emptyMap()), UDM.Array(listOf(UDM.Object.of("path" to UDM.Scalar("/x"))))))
        }
    }
    @Test fun `patch unknown op`() {
        assertThrows<FunctionArgumentException> {
            JsonPatchFunctions.jsonPatch(listOf(UDM.Object(emptyMap()), UDM.Array(listOf(patchOp("destroy", "/x")))))
        }
    }

    // =========================================================================
    // jsonMergePatch (RFC 7396)
    // =========================================================================

    @Test fun `mergePatch - add field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Object.of("age" to UDM.Scalar(30))
        val result = JsonPatchFunctions.jsonMergePatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Alice", (result.get("name") as UDM.Scalar).value)
        assertEquals(30, (result.get("age") as UDM.Scalar).value)
    }

    @Test fun `mergePatch - replace field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val patch = UDM.Object.of("name" to UDM.Scalar("Bob"))
        val result = JsonPatchFunctions.jsonMergePatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Bob", (result.get("name") as UDM.Scalar).value)
    }

    @Test fun `mergePatch - null removes field`() {
        val doc = UDM.Object.of("name" to UDM.Scalar("Alice"), "phone" to UDM.Scalar("123"))
        val patch = UDM.Object.of("phone" to UDM.Scalar.nullValue())
        val result = JsonPatchFunctions.jsonMergePatch(listOf(doc, patch)) as UDM.Object
        assertEquals("Alice", (result.get("name") as UDM.Scalar).value)
        assertNull(result.get("phone"))
    }

    @Test fun `mergePatch - nested merge`() {
        val doc = UDM.Object.of("address" to UDM.Object.of("city" to UDM.Scalar("Amsterdam"), "zip" to UDM.Scalar("1000")))
        val patch = UDM.Object.of("address" to UDM.Object.of("city" to UDM.Scalar("Rotterdam")))
        val result = JsonPatchFunctions.jsonMergePatch(listOf(doc, patch)) as UDM.Object
        val address = result.get("address") as UDM.Object
        assertEquals("Rotterdam", (address.get("city") as UDM.Scalar).value)
        assertEquals("1000", (address.get("zip") as UDM.Scalar).value) // preserved
    }

    @Test fun `mergePatch - wrong arg count`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonMergePatch(listOf(UDM.Scalar(1))) } }

    // =========================================================================
    // jsonDiff
    // =========================================================================

    @Test fun `diff - field added`() {
        val before = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val after = UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(30))
        val ops = JsonPatchFunctions.jsonDiff(listOf(before, after)) as UDM.Array
        assertEquals(1, ops.elements.size)
        val op = ops.elements[0] as UDM.Object
        assertEquals("add", (op.get("op") as UDM.Scalar).value)
        assertEquals("/age", (op.get("path") as UDM.Scalar).value)
    }

    @Test fun `diff - field removed`() {
        val before = UDM.Object.of("name" to UDM.Scalar("Alice"), "phone" to UDM.Scalar("123"))
        val after = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val ops = JsonPatchFunctions.jsonDiff(listOf(before, after)) as UDM.Array
        assertEquals(1, ops.elements.size)
        assertEquals("remove", ((ops.elements[0] as UDM.Object).get("op") as UDM.Scalar).value)
    }

    @Test fun `diff - field replaced`() {
        val before = UDM.Object.of("name" to UDM.Scalar("Alice"))
        val after = UDM.Object.of("name" to UDM.Scalar("Bob"))
        val ops = JsonPatchFunctions.jsonDiff(listOf(before, after)) as UDM.Array
        assertEquals(1, ops.elements.size)
        assertEquals("replace", ((ops.elements[0] as UDM.Object).get("op") as UDM.Scalar).value)
    }

    @Test fun `diff - identical`() {
        val doc = UDM.Object.of("x" to UDM.Scalar(1))
        val ops = JsonPatchFunctions.jsonDiff(listOf(doc, doc)) as UDM.Array
        assertEquals(0, ops.elements.size)
    }

    @Test fun `diff - wrong arg count`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.jsonDiff(listOf(UDM.Scalar(1))) } }

    // =========================================================================
    // jsonDiff + jsonPatch round-trip
    // =========================================================================

    @Test fun `diff then patch produces after`() {
        val before = UDM.Object.of("name" to UDM.Scalar("Alice"), "age" to UDM.Scalar(25))
        val after = UDM.Object.of("name" to UDM.Scalar("Bob"), "age" to UDM.Scalar(25), "city" to UDM.Scalar("Amsterdam"))
        val patch = JsonPatchFunctions.jsonDiff(listOf(before, after))
        val result = JsonPatchFunctions.jsonPatch(listOf(before, patch)) as UDM.Object
        assertEquals("Bob", (result.get("name") as UDM.Scalar).value)
        assertEquals("Amsterdam", (result.get("city") as UDM.Scalar).value)
    }

    // =========================================================================
    // validateJsonPatch
    // =========================================================================

    @Test fun `validate - valid patch`() {
        val patch = UDM.Array(listOf(patchOp("add", "/x", UDM.Scalar(1))))
        val result = JsonPatchFunctions.validateJsonPatch(listOf(patch)) as UDM.Object
        assertEquals(true, (result.get("valid") as UDM.Scalar).value)
    }

    @Test fun `validate - missing op`() {
        val patch = UDM.Array(listOf(UDM.Object.of("path" to UDM.Scalar("/x"))))
        val result = JsonPatchFunctions.validateJsonPatch(listOf(patch)) as UDM.Object
        assertEquals(false, (result.get("valid") as UDM.Scalar).value)
    }

    @Test fun `validate - unknown op`() {
        val patch = UDM.Array(listOf(patchOp("destroy", "/x")))
        val result = JsonPatchFunctions.validateJsonPatch(listOf(patch)) as UDM.Object
        assertEquals(false, (result.get("valid") as UDM.Scalar).value)
    }

    @Test fun `validate - add without value`() {
        val patch = UDM.Array(listOf(UDM.Object.of("op" to UDM.Scalar("add"), "path" to UDM.Scalar("/x"))))
        val result = JsonPatchFunctions.validateJsonPatch(listOf(patch)) as UDM.Object
        assertEquals(false, (result.get("valid") as UDM.Scalar).value)
    }

    @Test fun `validate - move without from`() {
        val patch = UDM.Array(listOf(UDM.Object.of("op" to UDM.Scalar("move"), "path" to UDM.Scalar("/x"))))
        val result = JsonPatchFunctions.validateJsonPatch(listOf(patch)) as UDM.Object
        assertEquals(false, (result.get("valid") as UDM.Scalar).value)
    }

    @Test fun `validate - wrong arg count`() { assertThrows<FunctionArgumentException> { JsonPatchFunctions.validateJsonPatch(emptyList()) } }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun patchOp(op: String, path: String, value: UDM? = null): UDM {
        val props = mutableMapOf<String, UDM>("op" to UDM.Scalar(op), "path" to UDM.Scalar(path))
        if (value != null) props["value"] = value
        return UDM.Object(props)
    }

    private fun moveOp(from: String, path: String) = UDM.Object.of(
        "op" to UDM.Scalar("move"), "from" to UDM.Scalar(from), "path" to UDM.Scalar(path)
    )

    private fun copyOp(from: String, path: String) = UDM.Object.of(
        "op" to UDM.Scalar("copy"), "from" to UDM.Scalar(from), "path" to UDM.Scalar(path)
    )
}
