package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlin.test.*

class QNameFunctionsTest {

    @Test
    fun testLocalName_withPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("soap:Envelope")
        ))
        val result = QNameFunctions.localName(listOf(element))
        assertEquals("Envelope", (result as UDM.Scalar).value)
    }

    @Test
    fun testLocalName_withoutPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = QNameFunctions.localName(listOf(element))
        assertEquals("root", (result as UDM.Scalar).value)
    }

    @Test
    fun testLocalName_noNodeName() {
        val element = UDM.Object(mutableMapOf(
            "someProperty" to UDM.Scalar("value")
        ))
        val result = QNameFunctions.localName(listOf(element))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test
    fun testLocalName_invalidArg() {
        assertFailsWith<FunctionArgumentException> {
            QNameFunctions.localName(listOf(UDM.Scalar("not an object")))
        }
    }

    @Test
    fun testNamespaceUri_withNamespace() {
        val element = UDM.Object(mutableMapOf(
            "__namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val result = QNameFunctions.namespaceUri(listOf(element))
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", (result as UDM.Scalar).value)
    }

    @Test
    fun testNamespaceUri_withoutNamespace() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = QNameFunctions.namespaceUri(listOf(element))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test
    fun testQualifiedName_withPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("soap:Envelope")
        ))
        val result = QNameFunctions.qualifiedName(listOf(element))
        assertEquals("soap:Envelope", (result as UDM.Scalar).value)
    }

    @Test
    fun testQualifiedName_withoutPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = QNameFunctions.qualifiedName(listOf(element))
        assertEquals("root", (result as UDM.Scalar).value)
    }

    @Test
    fun testNamespacePrefix_withPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("soap:Envelope")
        ))
        val result = QNameFunctions.namespacePrefix(listOf(element))
        assertEquals("soap", (result as UDM.Scalar).value)
    }

    @Test
    fun testNamespacePrefix_withoutPrefix() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = QNameFunctions.namespacePrefix(listOf(element))
        assertEquals("", (result as UDM.Scalar).value)
    }

    @Test
    fun testResolveQName() {
        val qname = "soap:Envelope"
        val nsContext = UDM.Object(mutableMapOf(
            "soap" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val result = QNameFunctions.resolveQName(listOf(UDM.Scalar(qname), nsContext))
        val resolved = result as UDM.Object
        assertEquals("Envelope", (resolved.properties["localName"] as UDM.Scalar).value)
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", (resolved.properties["namespaceUri"] as UDM.Scalar).value)
        assertEquals("soap", (resolved.properties["prefix"] as UDM.Scalar).value)
    }

    @Test
    fun testCreateQName() {
        val localName = "Envelope"
        val namespaceUri = "http://schemas.xmlsoap.org/soap/envelope/"
        val prefix = "soap"
        val result = QNameFunctions.createQName(listOf(
            UDM.Scalar(localName),
            UDM.Scalar(namespaceUri),
            UDM.Scalar(prefix)
        ))
        val qname = result as UDM.Object
        assertEquals("Envelope", (qname.properties["localName"] as UDM.Scalar).value)
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", (qname.properties["namespaceUri"] as UDM.Scalar).value)
        assertEquals("soap", (qname.properties["prefix"] as UDM.Scalar).value)
    }

    @Test
    fun testHasNamespace_true() {
        val element = UDM.Object(mutableMapOf(
            "__namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val result = QNameFunctions.hasNamespace(listOf(element))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testHasNamespace_false() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("root")
        ))
        val result = QNameFunctions.hasNamespace(listOf(element))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testGetNamespaces() {
        val element = UDM.Object(mutableMapOf(
            "__namespaces" to UDM.Object(mutableMapOf(
                "soap" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/"),
                "xs" to UDM.Scalar("http://www.w3.org/2001/XMLSchema")
            ))
        ))
        val result = QNameFunctions.getNamespaces(listOf(element))
        val namespaces = result as UDM.Object
        assertEquals("http://schemas.xmlsoap.org/soap/envelope/", 
                    (namespaces.properties["soap"] as UDM.Scalar).value)
        assertEquals("http://www.w3.org/2001/XMLSchema", 
                    (namespaces.properties["xs"] as UDM.Scalar).value)
    }

    @Test
    fun testMatchesQName_true() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("soap:Envelope"),
            "__namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val qname = UDM.Object(mutableMapOf(
            "localName" to UDM.Scalar("Envelope"),
            "namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val result = QNameFunctions.matchesQName(listOf(element, qname))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun testMatchesQName_false() {
        val element = UDM.Object(mutableMapOf(
            "__nodeName" to UDM.Scalar("soap:Body"),
            "__namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val qname = UDM.Object(mutableMapOf(
            "localName" to UDM.Scalar("Envelope"),
            "namespaceUri" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        val result = QNameFunctions.matchesQName(listOf(element, qname))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun testRequiredArguments() {
        assertFailsWith<FunctionArgumentException> {
            QNameFunctions.localName(emptyList())
        }

        assertFailsWith<FunctionArgumentException> {
            QNameFunctions.createQName(listOf(UDM.Scalar("localName")))
        }
    }
}