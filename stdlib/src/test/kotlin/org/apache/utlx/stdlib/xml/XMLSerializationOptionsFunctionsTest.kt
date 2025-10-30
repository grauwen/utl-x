// stdlib/src/test/kotlin/org/apache/utlx/stdlib/xml/XMLSerializationOptionsFunctionsTest.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XMLSerializationOptionsFunctionsTest {

    // ========== ENFORCE NAMESPACE PREFIXES ==========

    @Test
    fun testEnforceNamespacePrefixes() {
        val xml = """<ns1:Element xmlns:ns1="http://example.com" ns1:attr="value"/>"""
        val mappings = UDM.Object(mapOf(
            "http://example.com" to UDM.Scalar("ex")
        ))
        
        val result = XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
            listOf(UDM.Scalar(xml), mappings)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:ex="))
        assertTrue(processedXML.contains("<ex:Element"))
        assertTrue(processedXML.contains("ex:attr="))
    }

    @Test
    fun testEnforceNamespacePrefixesMultiple() {
        val xml = """<root xmlns:a="http://ns1.com" xmlns:b="http://ns2.com">
            <a:element1/>
            <b:element2/>
        </root>"""
        val mappings = UDM.Object(mapOf(
            "http://ns1.com" to UDM.Scalar("first"),
            "http://ns2.com" to UDM.Scalar("second")
        ))
        
        val result = XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
            listOf(UDM.Scalar(xml), mappings)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:first="))
        assertTrue(processedXML.contains("xmlns:second="))
        assertTrue(processedXML.contains("<first:element1"))
        assertTrue(processedXML.contains("<second:element2"))
    }

    @Test
    fun testEnforceNamespacePrefixesNoChange() {
        val xml = """<ex:Element xmlns:ex="http://example.com"/>"""
        val mappings = UDM.Object(mapOf(
            "http://example.com" to UDM.Scalar("ex")
        ))
        
        val result = XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
            listOf(UDM.Scalar(xml), mappings)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertEquals(xml, processedXML)
    }

    @Test
    fun testEnforceNamespacePrefixesEmptyMappings() {
        val xml = """<ns1:Element xmlns:ns1="http://example.com"/>"""
        val mappings = UDM.Object(mapOf())
        
        val result = XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
            listOf(UDM.Scalar(xml), mappings)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertEquals(xml, processedXML)
    }

    @Test
    fun testEnforceNamespacePrefixesInvalidMappings() {
        val xml = """<ns1:Element xmlns:ns1="http://example.com"/>"""
        val mappings = UDM.Object(mapOf(
            "http://example.com" to UDM.Array(listOf())
        ))
        
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
                listOf(UDM.Scalar(xml), mappings)
            )
        }
    }

    @Test
    fun testEnforceNamespacePrefixesInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.enforceNamespacePrefixes(listOf(UDM.Scalar("xml")))
        }
    }

    @Test
    fun testEnforceNamespacePrefixesNonObjectMapping() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
                listOf(UDM.Scalar("xml"), UDM.Scalar("not an object"))
            )
        }
    }

    // ========== FORMAT EMPTY ELEMENTS ==========

    @Test
    fun testFormatEmptyElementsSelfClosing() {
        val xml = """<root><empty></empty><data>value</data></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("self-closing"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<empty/>"))
        assertTrue(processedXML.contains("<data>value</data>"))
    }

    @Test
    fun testFormatEmptyElementsExplicit() {
        val xml = """<root><empty/><data>value</data></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("explicit"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<empty></empty>"))
        assertTrue(processedXML.contains("<data>value</data>"))
    }

    @Test
    fun testFormatEmptyElementsNil() {
        val xml = """<root><empty/><data>value</data></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("nil"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xsi:nil=\"true\""))
        assertTrue(processedXML.contains("<data>value</data>"))
    }

    @Test
    fun testFormatEmptyElementsOmit() {
        val xml = """<root><empty/><data>value</data><another></another></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("omit"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<data>value</data>"))
        assertNotNull(processedXML)
    }

    @Test
    fun testFormatEmptyElementsWithAttributes() {
        val xml = """<root><empty id="123" class="test"/></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("explicit"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("id=\"123\""))
        assertTrue(processedXML.contains("class=\"test\""))
    }

    @Test
    fun testFormatEmptyElementsUnderscoreStyle() {
        val xml = """<root><empty/></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("self_closing"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<empty/>"))
    }

    @Test
    fun testFormatEmptyElementsInvalidStyle() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.formatEmptyElements(
                listOf(UDM.Scalar("<empty/>"), UDM.Scalar("invalid-style"))
            )
        }
    }

    @Test
    fun testFormatEmptyElementsInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.formatEmptyElements(listOf(UDM.Scalar("xml")))
        }
    }

    // ========== ADD NAMESPACE DECLARATIONS ==========

    @Test
    fun testAddNamespaceDeclarations() {
        val xml = """<root><child/></root>"""
        val namespaces = UDM.Object(mapOf(
            "xsi" to UDM.Scalar("http://www.w3.org/2001/XMLSchema-instance"),
            "soap" to UDM.Scalar("http://schemas.xmlsoap.org/soap/envelope/")
        ))
        
        val result = XMLSerializationOptionsFunctions.addNamespaceDeclarations(
            listOf(UDM.Scalar(xml), namespaces)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:xsi="))
        assertTrue(processedXML.contains("xmlns:soap="))
        assertTrue(processedXML.contains("http://www.w3.org/2001/XMLSchema-instance"))
        assertTrue(processedXML.contains("http://schemas.xmlsoap.org/soap/envelope/"))
    }

    @Test
    fun testAddNamespaceDeclarationsWithExistingAttrs() {
        val xml = """<root id="123" class="test"><child/></root>"""
        val namespaces = UDM.Object(mapOf(
            "ns1" to UDM.Scalar("http://example.com")
        ))
        
        val result = XMLSerializationOptionsFunctions.addNamespaceDeclarations(
            listOf(UDM.Scalar(xml), namespaces)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:ns1="))
        assertTrue(processedXML.contains("id=\"123\""))
        assertTrue(processedXML.contains("class=\"test\""))
    }

    @Test
    fun testAddNamespaceDeclarationsSelfClosing() {
        val xml = """<root/>"""
        val namespaces = UDM.Object(mapOf(
            "test" to UDM.Scalar("http://test.com")
        ))
        
        val result = XMLSerializationOptionsFunctions.addNamespaceDeclarations(
            listOf(UDM.Scalar(xml), namespaces)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:test="))
    }

    @Test
    fun testAddNamespaceDeclarationsEmptyNamespaces() {
        val xml = """<root><child/></root>"""
        val namespaces = UDM.Object(mapOf())
        
        val result = XMLSerializationOptionsFunctions.addNamespaceDeclarations(
            listOf(UDM.Scalar(xml), namespaces)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<root ")) // Should add space but no declarations
    }

    @Test
    fun testAddNamespaceDeclarationsInvalidNamespace() {
        val xml = """<root/>"""
        val namespaces = UDM.Object(mapOf(
            "test" to UDM.Array(listOf())
        ))
        
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.addNamespaceDeclarations(
                listOf(UDM.Scalar(xml), namespaces)
            )
        }
    }

    @Test
    fun testAddNamespaceDeclarationsInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.addNamespaceDeclarations(listOf(UDM.Scalar("xml")))
        }
    }

    @Test
    fun testAddNamespaceDeclarationsNonObjectNamespaces() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.addNamespaceDeclarations(
                listOf(UDM.Scalar("xml"), UDM.Scalar("not an object"))
            )
        }
    }

    // ========== CREATE SOAP ENVELOPE ==========

    @Test
    fun testCreateSOAPEnvelopeDefault() {
        val body = UDM.Object(mapOf(
            "request" to UDM.Scalar("test data")
        ))
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("soap:Envelope"))
        assertTrue(soapXML.contains("soap:Header"))
        assertTrue(soapXML.contains("soap:Body"))
        assertTrue(soapXML.contains("http://schemas.xmlsoap.org/soap/envelope/"))
        assertTrue(soapXML.contains("<request>test data</request>"))
    }

    @Test
    fun testCreateSOAPEnvelopeVersion11() {
        val body = UDM.Scalar("simple content")
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(
            listOf(body, UDM.Scalar("1.1"))
        )
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("http://schemas.xmlsoap.org/soap/envelope/"))
        assertTrue(soapXML.contains("simple content"))
    }

    @Test
    fun testCreateSOAPEnvelopeVersion12() {
        val body = UDM.Scalar("soap 1.2 content")
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(
            listOf(body, UDM.Scalar("1.2"))
        )
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("http://www.w3.org/2003/05/soap-envelope"))
        assertTrue(soapXML.contains("soap 1.2 content"))
    }

    @Test
    fun testCreateSOAPEnvelopeComplexBody() {
        val body = UDM.Object(mapOf(
            "user" to UDM.Object(mapOf(
                "name" to UDM.Scalar("Alice"),
                "age" to UDM.Scalar(30)
            )),
            "permissions" to UDM.Array(listOf(
                UDM.Scalar("read"),
                UDM.Scalar("write")
            ))
        ))
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("<user>"))
        assertTrue(soapXML.contains("<name>Alice</name>"))
        assertTrue(soapXML.contains("<age>30</age>"))
        assertTrue(soapXML.contains("read"))
        assertTrue(soapXML.contains("write"))
    }

    @Test
    fun testCreateSOAPEnvelopeWithSpecialCharacters() {
        val body = UDM.Object(mapOf(
            "message" to UDM.Scalar("Hello & <world>")
        ))
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("&amp;"))
        assertTrue(soapXML.contains("&lt;"))
        assertTrue(soapXML.contains("&gt;"))
    }

    @Test
    fun testCreateSOAPEnvelopeInvalidVersion() {
        val body = UDM.Scalar("content")
        
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.createSOAPEnvelope(
                listOf(body, UDM.Scalar("2.0"))
            )
        }
    }

    @Test
    fun testCreateSOAPEnvelopeInvalidArgCount() {
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            XMLSerializationOptionsFunctions.createSOAPEnvelope(
                listOf(UDM.Scalar("body"), UDM.Scalar("1.1"), UDM.Scalar("extra"))
            )
        }
    }

    // ========== ERROR HANDLING ==========

    // Removed testInvalidStringInput - argument type validation handled by @UTLXFunction

    // Removed testNullStringValues - argument validation handled by @UTLXFunction and asString()

    // ========== EDGE CASES ==========

    @Test
    fun testEnforceNamespacePrefixesComplexXML() {
        val xml = """<?xml version="1.0"?>
        <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <soap:Header>
                <wsse:Security>
                    <wsse:UsernameToken wsse:Id="token">
                        <wsse:Username>user</wsse:Username>
                    </wsse:UsernameToken>
                </wsse:Security>
            </soap:Header>
            <soap:Body>
                <request>data</request>
            </soap:Body>
        </soap:Envelope>"""
        
        val mappings = UDM.Object(mapOf(
            "http://schemas.xmlsoap.org/soap/envelope/" to UDM.Scalar("s"),
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" to UDM.Scalar("security")
        ))
        
        val result = XMLSerializationOptionsFunctions.enforceNamespacePrefixes(
            listOf(UDM.Scalar(xml), mappings)
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("xmlns:s="))
        assertTrue(processedXML.contains("xmlns:security="))
        assertTrue(processedXML.contains("<s:Envelope"))
        assertTrue(processedXML.contains("<security:Security"))
    }

    @Test
    fun testFormatEmptyElementsNestedEmpty() {
        val xml = """<root><level1><empty/><level2><another></another></level2></level1></root>"""
        val result = XMLSerializationOptionsFunctions.formatEmptyElements(
            listOf(UDM.Scalar(xml), UDM.Scalar("explicit"))
        )
        
        assertTrue(result is UDM.Scalar)
        val processedXML = result.value as String
        assertTrue(processedXML.contains("<empty></empty>"))
        assertTrue(processedXML.contains("<another></another>"))
    }

    @Test
    fun testAddNamespaceDeclarationsNoRootElement() {
        val xml = """<!-- comment only -->"""
        val namespaces = UDM.Object(mapOf(
            "test" to UDM.Scalar("http://test.com")
        ))
        
        val result = XMLSerializationOptionsFunctions.addNamespaceDeclarations(
            listOf(UDM.Scalar(xml), namespaces)
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(xml, result.value) // Should be unchanged
    }

    @Test
    fun testCreateSOAPEnvelopeEmptyBody() {
        val body = UDM.Scalar("")
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("soap:Envelope"))
        assertTrue(soapXML.contains("soap:Body"))
    }

    @Test
    fun testCreateSOAPEnvelopeNullBody() {
        val body = UDM.Scalar.nullValue()
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("soap:Body"))
    }

    @Test
    fun testXMLEscaping() {
        val body = UDM.Object(mapOf(
            "data" to UDM.Scalar("Contains: & < > \" ' characters")
        ))
        
        val result = XMLSerializationOptionsFunctions.createSOAPEnvelope(listOf(body))
        
        assertTrue(result is UDM.Scalar)
        val soapXML = result.value as String
        assertTrue(soapXML.contains("&amp;"))
        assertTrue(soapXML.contains("&lt;"))
        assertTrue(soapXML.contains("&gt;"))
        assertTrue(soapXML.contains("&quot;"))
        assertTrue(soapXML.contains("&apos;"))
    }
}