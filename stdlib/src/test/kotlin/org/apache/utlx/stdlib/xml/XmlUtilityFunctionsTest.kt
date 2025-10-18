package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.test.*

class XmlUtilityFunctionsTest {

    @Test
    fun testNodeType_element() {
        val element = UDM.Object(mutableMapOf(
            "__nodeType" to UDM.Scalar("element")
        ))
        val result = XmlUtilityFunctions.nodeType(listOf(element))
        assertEquals("element", (result as UDM.Scalar).value)
    }

    @Test
    fun testNodeType_text() {
        val textNode = UDM.Object(mutableMapOf(
            "__nodeType" to UDM.Scalar("text")
        ))
        val result = XmlUtilityFunctions.nodeType(listOf(textNode))
        assertEquals("text", (result as UDM.Scalar).value)
    }

    @Test
    fun testNodeType_unknown() {
        val element = UDM.Object(mutableMapOf(
            "someProperty" to UDM.Scalar("value")
        ))
        val result = XmlUtilityFunctions.nodeType(listOf(element))
        assertEquals("unknown", (result as UDM.Scalar).value)
    }

    @Test
    fun testTextContent_withText() {
        val element = UDM.Object(mutableMapOf(
            "__text" to UDM.Scalar("Hello World")
        ))
        val result = XmlUtilityFunctions.textContent(listOf(element))
        assertEquals("Hello World", (result as UDM.Scalar).value)
    }

    @Test
    fun testTextContent_noText() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = XmlUtilityFunctions.textContent(listOf(element))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test
    fun testAttributes() {
        val element = UDM.Object(mutableMapOf(
            "__attributes" to UDM.Object(mutableMapOf(
                "id" to UDM.Scalar("123"),
                "class" to UDM.Scalar("test")
            ))
        ))
        val result = XmlUtilityFunctions.attributes(listOf(element))
        val attrs = result as UDM.Object
        assertEquals("123", (attrs.properties["id"] as UDM.Scalar).value)
        assertEquals("test", (attrs.properties["class"] as UDM.Scalar).value)
    }

    @Test
    fun testAttribute_exists() {
        val element = UDM.Object(mutableMapOf(
            "__attributes" to UDM.Object(mutableMapOf(
                "id" to UDM.Scalar("123"),
                "class" to UDM.Scalar("test")
            ))
        ))
        val result = XmlUtilityFunctions.attribute(listOf(element, UDM.Scalar("id")))
        assertEquals("123", (result as UDM.Scalar).value)
    }

    @Test
    fun testAttribute_notExists() {
        val element = UDM.Object(mutableMapOf(
            "__attributes" to UDM.Object(mutableMapOf(
                "id" to UDM.Scalar("123")
            ))
        ))
        val result = XmlUtilityFunctions.attribute(listOf(element, UDM.Scalar("missing")))
        assertNull((result as UDM.Scalar).value)
    }

    @Test
    fun testHasAttribute_true() {
        val element = UDM.Object(mutableMapOf(
            "__attributes" to UDM.Object(mutableMapOf(
                "id" to UDM.Scalar("123")
            ))
        ))
        val result = XmlUtilityFunctions.hasAttribute(listOf(element, UDM.Scalar("id")))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testHasAttribute_false() {
        val element = UDM.Object(mutableMapOf(
            "__attributes" to UDM.Object(mutableMapOf(
                "id" to UDM.Scalar("123")
            ))
        ))
        val result = XmlUtilityFunctions.hasAttribute(listOf(element, UDM.Scalar("missing")))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testChildCount() {
        val element = UDM.Object(mutableMapOf(
            "__children" to UDM.Array(mutableListOf(
                UDM.Object(mutableMapOf("child1" to UDM.Scalar("value1"))),
                UDM.Object(mutableMapOf("child2" to UDM.Scalar("value2"))),
                UDM.Object(mutableMapOf("child3" to UDM.Scalar("value3")))
            ))
        ))
        val result = XmlUtilityFunctions.childCount(listOf(element))
        assertEquals(3, (result as UDM.Scalar).value)
    }

    @Test
    fun testChildCount_noChildren() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = XmlUtilityFunctions.childCount(listOf(element))
        assertEquals(0, (result as UDM.Scalar).value)
    }

    @Test
    fun testChildNames() {
        val element = UDM.Object(mutableMapOf(
            "__children" to UDM.Array(mutableListOf(
                UDM.Object(mutableMapOf("__nodeName" to UDM.Scalar("child1"))),
                UDM.Object(mutableMapOf("__nodeName" to UDM.Scalar("child2"))),
                UDM.Object(mutableMapOf("__nodeName" to UDM.Scalar("child1")))
            ))
        ))
        val result = XmlUtilityFunctions.childNames(listOf(element))
        val names = result as UDM.Array
        assertEquals(2, names.elements.size) // Unique names only
        assertTrue(names.elements.any { (it as UDM.Scalar).value == "child1" })
        assertTrue(names.elements.any { (it as UDM.Scalar).value == "child2" })
    }

    @Test
    fun testParent() {
        val parent = UDM.Object(mutableMapOf("__nodeName" to UDM.Scalar("parent")))
        val element = UDM.Object(mutableMapOf(
            "__parent" to parent
        ))
        val result = XmlUtilityFunctions.parent(listOf(element))
        assertEquals(parent, result)
    }

    @Test
    fun testParent_noParent() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = XmlUtilityFunctions.parent(listOf(element))
        assertNull((result as UDM.Scalar).value)
    }

    @Test
    fun testElementPath() {
        val element = UDM.Object(mutableMapOf(
            "__path" to UDM.Scalar("/root/child/grandchild")
        ))
        val result = XmlUtilityFunctions.elementPath(listOf(element))
        assertEquals("/root/child/grandchild", (result as UDM.Scalar).value)
    }

    @Test
    fun testIsEmptyElement_true() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("empty")
        ))
        val result = XmlUtilityFunctions.isEmptyElement(listOf(element))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testIsEmptyElement_false() {
        val element = UDM.Object(mutableMapOf(
            "__children" to UDM.Array(mutableListOf(
                UDM.Object(mutableMapOf("child" to UDM.Scalar("value")))
            ))
        ))
        val result = XmlUtilityFunctions.isEmptyElement(listOf(element))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testXmlEscape() {
        val input = "<test>&\"'>"
        val result = XmlUtilityFunctions.xmlEscape(listOf(UDM.Scalar(input)))
        assertEquals("&lt;test&gt;&amp;&quot;&apos;&gt;", (result as UDM.Scalar).value)
    }

    @Test
    fun testXmlUnescape() {
        val input = "&lt;test&gt;&amp;&quot;&apos;"
        val result = XmlUtilityFunctions.xmlUnescape(listOf(UDM.Scalar(input)))
        assertEquals("<test>&\"'", (result as UDM.Scalar).value)
    }

    @Test
    fun testXmlEscapeUnescape_roundTrip() {
        val original = "<root attr=\"value\">&test;</root>"
        val escaped = XmlUtilityFunctions.xmlEscape(listOf(UDM.Scalar(original)))
        val unescaped = XmlUtilityFunctions.xmlUnescape(listOf(escaped))
        assertEquals(original, (unescaped as UDM.Scalar).value)
    }

    @Test
    fun testInvalidArguments() {
        assertFailsWith<FunctionArgumentException> {
            XmlUtilityFunctions.nodeType(emptyList())
        }

        assertFailsWith<FunctionArgumentException> {
            XmlUtilityFunctions.nodeType(listOf(UDM.Scalar("not an object")))
        }

        assertFailsWith<FunctionArgumentException> {
            XmlUtilityFunctions.attribute(listOf(UDM.Object(mutableMapOf())))
        }
    }
}