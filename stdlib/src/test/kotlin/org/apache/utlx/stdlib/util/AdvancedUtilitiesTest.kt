package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvancedUtilitiesTest {

    // ==================== VALUE FUNCTIONS TESTS ====================

    @Test
    fun testUpdate() {
        val obj = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf(
                "name" to UDM.Scalar("John"),
                "age" to UDM.Scalar(30)
            ), emptyMap())
        ), emptyMap())
        
        val path = UDM.Array(listOf(UDM.Scalar("user"), UDM.Scalar("name")))
        val newValue = UDM.Scalar("Jane")
        
        val result = ValueFunctions.update(listOf(obj, path, newValue))
        
        assertTrue(result is UDM.Object)
        val userObj = (result as UDM.Object).properties["user"] as UDM.Object
        assertEquals("Jane", (userObj.properties["name"] as UDM.Scalar).value)
        assertEquals(30, (userObj.properties["age"] as UDM.Scalar).value)
    }

    @Test
    fun testUpdateArrayIndex() {
        val arr = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c")))
        val path = UDM.Array(listOf(UDM.Scalar(1)))
        val newValue = UDM.Scalar("updated")
        
        val result = ValueFunctions.update(listOf(arr, path, newValue))
        
        assertTrue(result is UDM.Array)
        val elements = (result as UDM.Array).elements
        assertEquals("a", (elements[0] as UDM.Scalar).value)
        assertEquals("updated", (elements[1] as UDM.Scalar).value)
        assertEquals("c", (elements[2] as UDM.Scalar).value)
    }

    @Test
    fun testUpdateCreatesIntermediateStructures() {
        val obj = UDM.Object(emptyMap(), emptyMap())
        val path = UDM.Array(listOf(UDM.Scalar("new"), UDM.Scalar("nested"), UDM.Scalar("value")))
        val newValue = UDM.Scalar("test")
        
        val result = ValueFunctions.update(listOf(obj, path, newValue))
        
        assertTrue(result is UDM.Object)
        val nested = ((result as UDM.Object).properties["new"] as UDM.Object)
            .properties["nested"] as UDM.Object
        assertEquals("test", (nested.properties["value"] as UDM.Scalar).value)
    }

    @Test
    fun testMask() {
        val obj = UDM.Object(mapOf(
            "username" to UDM.Scalar("john"),
            "password" to UDM.Scalar("secret123"),
            "email" to UDM.Scalar("john@example.com"),
            "profile" to UDM.Object(mapOf(
                "ssn" to UDM.Scalar("123-45-6789"),
                "name" to UDM.Scalar("John Doe")
            ), emptyMap())
        ), emptyMap())
        
        val fieldsToMask = UDM.Array(listOf(UDM.Scalar("password"), UDM.Scalar("ssn")))
        
        val result = ValueFunctions.mask(listOf(obj, fieldsToMask))
        
        assertTrue(result is UDM.Object)
        val maskedObj = result as UDM.Object
        assertEquals("john", (maskedObj.properties["username"] as UDM.Scalar).value)
        assertEquals("***", (maskedObj.properties["password"] as UDM.Scalar).value)
        assertEquals("john@example.com", (maskedObj.properties["email"] as UDM.Scalar).value)
        
        val profile = maskedObj.properties["profile"] as UDM.Object
        assertEquals("***", (profile.properties["ssn"] as UDM.Scalar).value)
        assertEquals("John Doe", (profile.properties["name"] as UDM.Scalar).value)
    }

    @Test
    fun testMaskWithCustomMaskString() {
        val obj = UDM.Object(mapOf("secret" to UDM.Scalar("hidden")), emptyMap())
        val fields = UDM.Array(listOf(UDM.Scalar("secret")))
        val maskString = UDM.Scalar("[REDACTED]")
        
        val result = ValueFunctions.mask(listOf(obj, fields, maskString))
        
        assertEquals("[REDACTED]", ((result as UDM.Object).properties["secret"] as UDM.Scalar).value)
    }

    @Test
    fun testPick() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "password" to UDM.Scalar("secret"),
            "email" to UDM.Scalar("john@example.com")
        ), emptyMap())
        
        val fields = UDM.Array(listOf(UDM.Scalar("name"), UDM.Scalar("email")))
        
        val result = ValueFunctions.pick(listOf(obj, fields))
        
        assertTrue(result is UDM.Object)
        val picked = result as UDM.Object
        assertEquals(2, picked.properties.size)
        assertEquals("John", (picked.properties["name"] as UDM.Scalar).value)
        assertEquals("john@example.com", (picked.properties["email"] as UDM.Scalar).value)
        assertTrue(!picked.properties.containsKey("password"))
        assertTrue(!picked.properties.containsKey("age"))
    }

    @Test
    fun testOmit() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "password" to UDM.Scalar("secret"),
            "email" to UDM.Scalar("john@example.com")
        ), emptyMap())
        
        val fields = UDM.Array(listOf(UDM.Scalar("password"), UDM.Scalar("age")))
        
        val result = ValueFunctions.omit(listOf(obj, fields))
        
        assertTrue(result is UDM.Object)
        val filtered = result as UDM.Object
        assertEquals(2, filtered.properties.size)
        assertEquals("John", (filtered.properties["name"] as UDM.Scalar).value)
        assertEquals("john@example.com", (filtered.properties["email"] as UDM.Scalar).value)
        assertTrue(!filtered.properties.containsKey("password"))
        assertTrue(!filtered.properties.containsKey("age"))
    }

    @Test
    fun testDefaultValue() {
        val nullValue = UDM.Scalar(null)
        val defaultVal = UDM.Scalar("default")
        
        val result1 = ValueFunctions.defaultValue(listOf(nullValue, defaultVal))
        assertEquals("default", (result1 as UDM.Scalar).value)
        
        val existingValue = UDM.Scalar("existing")
        val result2 = ValueFunctions.defaultValue(listOf(existingValue, defaultVal))
        assertEquals("existing", (result2 as UDM.Scalar).value)
    }

    // ==================== DIFF FUNCTIONS TESTS ====================

    @Test
    fun testDiff() {
        val obj1 = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30),
            "city" to UDM.Scalar("NYC")
        ), emptyMap())
        
        val obj2 = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(31),
            "country" to UDM.Scalar("USA")
        ), emptyMap())
        
        val result = DiffFunctions.diff(listOf(obj1, obj2))
        
        assertTrue(result is UDM.Object)
        val changes = (result as UDM.Object).properties["changes"] as UDM.Array
        assertTrue(changes.elements.size >= 3) // age changed, city removed, country added
    }

    @Test
    fun testDeepEquals() {
        val obj1 = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf("name" to UDM.Scalar("John")), emptyMap())
        ), emptyMap())
        
        val obj2 = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf("name" to UDM.Scalar("John")), emptyMap())
        ), emptyMap())
        
        val obj3 = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf("name" to UDM.Scalar("Jane")), emptyMap())
        ), emptyMap())
        
        val result1 = DiffFunctions.deepEquals(listOf(obj1, obj2))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = DiffFunctions.deepEquals(listOf(obj1, obj3))
        assertEquals(false, (result2 as UDM.Scalar).value)
    }

    @Test
    fun testPatch() {
        val original = UDM.Object(mapOf("name" to UDM.Scalar("John")), emptyMap())
        
        val diff = UDM.Object(mapOf(
            "changes" to UDM.Array(listOf(
                UDM.Object(mapOf(
                    "type" to UDM.Scalar("changed"),
                    "path" to UDM.Array(listOf(UDM.Scalar("name"))),
                    "newValue" to UDM.Scalar("Jane")
                ), emptyMap())
            ))
        ), emptyMap())
        
        val result = DiffFunctions.patch(listOf(original, diff))
        
        assertTrue(result is UDM.Object)
        assertEquals("Jane", ((result as UDM.Object).properties["name"] as UDM.Scalar).value)
    }

    // ==================== MIME FUNCTIONS TESTS ====================

    @Test
    fun testGetMimeType() {
        val result1 = MimeFunctions.getMimeType(listOf(UDM.Scalar("document.pdf")))
        assertEquals("application/pdf", (result1 as UDM.Scalar).value)
        
        val result2 = MimeFunctions.getMimeType(listOf(UDM.Scalar("json")))
        assertEquals("application/json", (result2 as UDM.Scalar).value)
        
        val result3 = MimeFunctions.getMimeType(listOf(UDM.Scalar("unknown.xyz")))
        assertEquals("application/octet-stream", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testGetExtension() {
        val result1 = MimeFunctions.getExtension(listOf(UDM.Scalar("application/json")))
        assertEquals("json", (result1 as UDM.Scalar).value)
        
        val result2 = MimeFunctions.getExtension(listOf(UDM.Scalar("image/png")))
        assertEquals("png", (result2 as UDM.Scalar).value)
        
        val result3 = MimeFunctions.getExtension(listOf(UDM.Scalar("unknown/type")))
        assertEquals("bin", (result3 as UDM.Scalar).value)
    }

    @Test
    fun testParseContentType() {
        val result = MimeFunctions.parseContentType(listOf(UDM.Scalar("application/json; charset=utf-8")))
        
        assertTrue(result is UDM.Object)
        val parsed = result as UDM.Object
        assertEquals("application/json", (parsed.properties["mimeType"] as UDM.Scalar).value)
        assertEquals("utf-8", (parsed.properties["charset"] as UDM.Scalar).value)
    }

    @Test
    fun testBuildContentType() {
        val params = UDM.Object(mapOf(
            "mimeType" to UDM.Scalar("application/json"),
            "charset" to UDM.Scalar("utf-8")
        ), emptyMap())
        
        val result = MimeFunctions.buildContentType(listOf(params))
        
        assertTrue((result as UDM.Scalar).value.toString().contains("application/json"))
        assertTrue(result.value.toString().contains("charset=utf-8"))
    }

    // ==================== MULTIPART FUNCTIONS TESTS ====================

    @Test
    fun testParseBoundary() {
        val contentType = "multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW"
        val result = MultipartFunctions.parseBoundary(listOf(UDM.Scalar(contentType)))
        
        assertEquals("----WebKitFormBoundary7MA4YWxkTrZu0gW", (result as UDM.Scalar).value)
    }

    @Test
    fun testParseBoundaryNotFound() {
        val contentType = "application/json"
        val result = MultipartFunctions.parseBoundary(listOf(UDM.Scalar(contentType)))
        
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testGenerateBoundary() {
        val result = MultipartFunctions.generateBoundary(listOf())
        
        assertTrue(result is UDM.Scalar)
        val boundary = result.value as String
        assertTrue(boundary.startsWith("----WebKitFormBoundary"))
        assertTrue(boundary.length > 20)
    }

    @Test
    fun testCreatePart() {
        val result = MultipartFunctions.createPart(listOf(
            UDM.Scalar("fieldName"),
            UDM.Scalar("fieldValue"),
            UDM.Scalar("text/plain")
        ))
        
        assertTrue(result is UDM.Object)
        val part = result as UDM.Object
        assertEquals("fieldName", (part.properties["name"] as UDM.Scalar).value)
        assertEquals("fieldValue", (part.properties["content"] as UDM.Scalar).value)
        assertEquals("text/plain", (part.properties["contentType"] as UDM.Scalar).value)
    }

    @Test
    fun testBuildMultipart() {
        val parts = UDM.Array(listOf(
            UDM.Object(mapOf(
                "name" to UDM.Scalar("field1"),
                "content" to UDM.Scalar("value1")
            ), emptyMap()),
            UDM.Object(mapOf(
                "name" to UDM.Scalar("file"),
                "filename" to UDM.Scalar("test.txt"),
                "contentType" to UDM.Scalar("text/plain"),
                "content" to UDM.Scalar("file content")
            ), emptyMap())
        ))
        
        val boundary = UDM.Scalar("boundary123")
        
        val result = MultipartFunctions.buildMultipart(listOf(parts, boundary))
        
        assertTrue(result is UDM.Scalar)
        val body = result.value as String
        assertTrue(body.contains("--boundary123"))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"field1\""))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\""))
        assertTrue(body.contains("Content-Type: text/plain"))
        assertTrue(body.contains("value1"))
        assertTrue(body.contains("file content"))
        assertTrue(body.contains("--boundary123--"))
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testUpdateInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            ValueFunctions.update(listOf(UDM.Scalar("test")))
        }
        
        assertThrows<IllegalArgumentException> {
            ValueFunctions.update(listOf(UDM.Object(emptyMap(), emptyMap()), UDM.Scalar("not-array"), UDM.Scalar("value")))
        }
    }

    @Test
    fun testMaskInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            ValueFunctions.mask(listOf(UDM.Object(emptyMap(), emptyMap())))
        }
        
        assertThrows<IllegalArgumentException> {
            ValueFunctions.mask(listOf(UDM.Object(emptyMap(), emptyMap()), UDM.Scalar("not-array")))
        }
    }

    @Test
    fun testPickOmitNonObject() {
        val nonObject = UDM.Scalar("not an object")
        val fields = UDM.Array(listOf(UDM.Scalar("field")))
        
        val pickResult = ValueFunctions.pick(listOf(nonObject, fields))
        assertEquals(nonObject, pickResult)
        
        val omitResult = ValueFunctions.omit(listOf(nonObject, fields))
        assertEquals(nonObject, omitResult)
    }

    @Test
    fun testDiffInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            DiffFunctions.diff(listOf(UDM.Scalar("only-one")))
        }
        
        assertThrows<IllegalArgumentException> {
            DiffFunctions.deepEquals(listOf(UDM.Scalar("only-one")))
        }
    }

    @Test
    fun testMimeTypeInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            MimeFunctions.getMimeType(listOf())
        }
        
        assertThrows<IllegalArgumentException> {
            MimeFunctions.getMimeType(listOf(UDM.Array(emptyList())))
        }
    }

    @Test
    fun testMultipartInvalidArguments() {
        assertThrows<IllegalArgumentException> {
            MultipartFunctions.buildMultipart(listOf(UDM.Scalar("not-array"), UDM.Scalar("boundary")))
        }
        
        assertThrows<IllegalArgumentException> {
            MultipartFunctions.buildMultipart(listOf(UDM.Array(emptyList()), UDM.Array(emptyList())))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testUpdateEmptyPath() {
        val original = UDM.Scalar("original")
        val emptyPath = UDM.Array(emptyList())
        val newValue = UDM.Scalar("new")
        
        val result = ValueFunctions.update(listOf(original, emptyPath, newValue))
        assertEquals("new", (result as UDM.Scalar).value)
    }

    @Test
    fun testMaskEmptyFields() {
        val obj = UDM.Object(mapOf("field" to UDM.Scalar("value")), emptyMap())
        val emptyFields = UDM.Array(emptyList())
        
        val result = ValueFunctions.mask(listOf(obj, emptyFields))
        assertEquals(obj, result)
    }

    @Test
    fun testDiffIdenticalObjects() {
        val obj = UDM.Object(mapOf("field" to UDM.Scalar("value")), emptyMap())
        
        val result = DiffFunctions.diff(listOf(obj, obj))
        val changes = ((result as UDM.Object).properties["changes"] as UDM.Array).elements
        assertEquals(0, changes.size)
    }

    @Test
    fun testContentTypeWithoutParameters() {
        val result = MimeFunctions.parseContentType(listOf(UDM.Scalar("application/json")))
        
        val parsed = result as UDM.Object
        assertEquals("application/json", (parsed.properties["mimeType"] as UDM.Scalar).value)
        assertEquals(1, parsed.properties.size) // Only mimeType
    }
}