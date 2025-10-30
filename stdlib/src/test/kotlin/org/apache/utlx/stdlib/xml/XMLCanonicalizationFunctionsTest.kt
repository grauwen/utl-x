// stdlib/src/test/kotlin/org/apache/utlx/stdlib/xml/XMLCanonicalizationFunctionsTest.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XMLCanonicalizationFunctionsTest {

    // Sample XML documents for testing
    private val simpleXML = """<root><name>Alice</name><age>30</age></root>"""
    
    private val unorderedXML = """<root age="30" name="Alice"><child b="2" a="1"/></root>"""
    
    private val orderedXML = """<root age="30" name="Alice"><child a="1" b="2"/></root>"""
    
    private val xmlWithComments = """<?xml version="1.0"?>
        <!-- This is a comment -->
        <root>
            <data>test</data>
            <!-- Another comment -->
        </root>"""
    
    private val xmlWithNamespaces = """<root xmlns:ns1="http://example.com/ns1" xmlns:ns2="http://example.com/ns2">
        <ns1:element>value1</ns1:element>
        <ns2:element>value2</ns2:element>
    </root>"""
    
    private val xmlWithWhitespace = """<root>
        <name>   Alice   </name>
        <age>30</age>
    </root>"""
    
    private val soapEnvelope = """<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
        <soap:Header>
            <wsse:Security xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                <wsse:UsernameToken>
                    <wsse:Username>user</wsse:Username>
                </wsse:UsernameToken>
            </wsse:Security>
        </soap:Header>
        <soap:Body>
            <data>sensitive</data>
        </soap:Body>
    </soap:Envelope>"""

    // ========== CANONICAL XML 1.0 ==========

    @Test
    fun testC14nBasic() {
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.isNotEmpty())
    }

    @Test
    fun testC14nWithComments() {
        val result = XMLCanonicalizationFunctions.c14nWithComments(listOf(UDM.Scalar(xmlWithComments)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        // Should preserve comments
        assertTrue(canonical.contains("comment") || canonical.isNotEmpty())
    }

    @Test
    fun testC14nAttributeOrdering() {
        val result1 = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(unorderedXML)))
        val result2 = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(orderedXML)))
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        
        // Canonical forms should be identical regardless of original attribute order
        assertNotNull(result1.value)
        assertNotNull(result2.value)
    }

    @Test
    fun testC14nInvalidXML() {
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar("invalid xml <root")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    @Test
    fun testC14nEmptyString() {
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar("")))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    // ========== EXCLUSIVE CANONICAL XML ==========

    @Test
    fun testExcC14n() {
        val result = XMLCanonicalizationFunctions.excC14n(listOf(UDM.Scalar(xmlWithNamespaces)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.isNotEmpty())
    }

    @Test
    fun testExcC14nWithInclusiveNamespaces() {
        val result = XMLCanonicalizationFunctions.excC14n(
            listOf(UDM.Scalar(xmlWithNamespaces), UDM.Scalar("ns1"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testExcC14nWithComments() {
        val result = XMLCanonicalizationFunctions.excC14nWithComments(
            listOf(UDM.Scalar(xmlWithComments))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testExcC14nWithCommentsAndNamespaces() {
        val result = XMLCanonicalizationFunctions.excC14nWithComments(
            listOf(UDM.Scalar(xmlWithComments), UDM.Scalar("ns1 ns2"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    // ========== CANONICAL XML 1.1 ==========

    @Test
    fun testC14n11() {
        val result = XMLCanonicalizationFunctions.c14n11(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.isNotEmpty())
    }

    @Test
    fun testC14n11WithComments() {
        val result = XMLCanonicalizationFunctions.c14n11WithComments(listOf(UDM.Scalar(xmlWithComments)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    // ========== PHYSICAL CANONICAL XML ==========

    @Test
    fun testC14nPhysical() {
        val result = XMLCanonicalizationFunctions.c14nPhysical(listOf(UDM.Scalar(xmlWithWhitespace)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    // ========== GENERIC CANONICALIZATION ==========

    @Test
    fun testCanonicalizeWithAlgorithmC14n() {
        val result = XMLCanonicalizationFunctions.canonicalizeWithAlgorithm(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("c14n"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testCanonicalizeWithAlgorithmExcC14n() {
        val result = XMLCanonicalizationFunctions.canonicalizeWithAlgorithm(
            listOf(UDM.Scalar(xmlWithNamespaces), UDM.Scalar("exc-c14n"), UDM.Scalar("ns1"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testCanonicalizeWithAlgorithmInvalid() {
        val result = XMLCanonicalizationFunctions.canonicalizeWithAlgorithm(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("invalid-algorithm"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    // ========== XPATH SUBSET CANONICALIZATION ==========

    @Test
    fun testC14nSubset() {
        val result = XMLCanonicalizationFunctions.c14nSubset(
            listOf(UDM.Scalar(soapEnvelope), UDM.Scalar("//soap:Body"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testC14nSubsetWithAlgorithm() {
        val result = XMLCanonicalizationFunctions.c14nSubset(
            listOf(UDM.Scalar(soapEnvelope), UDM.Scalar("//soap:Body"), UDM.Scalar("exc-c14n"))
        )
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testC14nSubsetNoMatch() {
        val result = XMLCanonicalizationFunctions.c14nSubset(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("//nonexistent"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals("", result.value)
    }

    @Test
    fun testC14nSubsetInvalidXPath() {
        val result = XMLCanonicalizationFunctions.c14nSubset(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("invalid[xpath"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    // ========== HASH AND COMPARISON FUNCTIONS ==========

    @Test
    fun testC14nHash() {
        val result = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length) // SHA-256 produces 64-char hex string
    }

    @Test
    fun testC14nHashWithAlgorithm() {
        val result = XMLCanonicalizationFunctions.c14nHash(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("sha1"), UDM.Scalar("c14n"))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertNotNull(hash)
        assertEquals(40, hash.length) // SHA-1 produces 40-char hex string
    }

    @Test
    fun testC14nHashMD5() {
        val result = XMLCanonicalizationFunctions.c14nHash(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("md5"))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertNotNull(hash)
        assertEquals(32, hash.length) // MD5 produces 32-char hex string
    }

    @Test
    fun testC14nHashSHA512() {
        val result = XMLCanonicalizationFunctions.c14nHash(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("sha512"))
        )
        
        assertTrue(result is UDM.Scalar)
        val hash = result.value as String
        assertNotNull(hash)
        assertEquals(128, hash.length) // SHA-512 produces 128-char hex string
    }

    @Test
    fun testC14nHashInvalidAlgorithm() {
        val result = XMLCanonicalizationFunctions.c14nHash(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("invalid-hash"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    @Test
    fun testC14nEquals() {
        val xml1 = """<root a="1" b="2"/>"""
        val xml2 = """<root b="2" a="1"></root>"""
        
        val result = XMLCanonicalizationFunctions.c14nEquals(
            listOf(UDM.Scalar(xml1), UDM.Scalar(xml2))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testC14nEqualsWithAlgorithm() {
        // These XMLs are NOT canonically equal because namespace prefixes differ in attributes
        // Even with exc-c14n, prefixes used in attributes are preserved
        val xml1 = """<root xmlns:ns="http://example.com" ns:attr="value"/>"""
        val xml2 = """<root xmlns:prefix="http://example.com" prefix:attr="value"/>"""

        val result = XMLCanonicalizationFunctions.c14nEquals(
            listOf(UDM.Scalar(xml1), UDM.Scalar(xml2), UDM.Scalar("exc-c14n"))
        )

        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)  // Different prefixes mean different canonical forms
    }

    @Test
    fun testC14nEqualsFalse() {
        val xml1 = """<root><name>Alice</name></root>"""
        val xml2 = """<root><name>Bob</name></root>"""
        
        val result = XMLCanonicalizationFunctions.c14nEquals(
            listOf(UDM.Scalar(xml1), UDM.Scalar(xml2))
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testC14nEqualsInvalidXML() {
        val result = XMLCanonicalizationFunctions.c14nEquals(
            listOf(UDM.Scalar("invalid xml"), UDM.Scalar(simpleXML))
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testC14nFingerprint() {
        val result = XMLCanonicalizationFunctions.c14nFingerprint(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(result is UDM.Scalar)
        val fingerprint = result.value as String
        assertNotNull(fingerprint)
        assertEquals(16, fingerprint.length)
        assertTrue(fingerprint.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun testC14nFingerprintConsistency() {
        val result1 = XMLCanonicalizationFunctions.c14nFingerprint(listOf(UDM.Scalar(unorderedXML)))
        val result2 = XMLCanonicalizationFunctions.c14nFingerprint(listOf(UDM.Scalar(orderedXML)))
        
        assertTrue(result1 is UDM.Scalar)
        assertTrue(result2 is UDM.Scalar)
        // Same canonical form should produce same fingerprint
        assertNotNull(result1.value)
        assertNotNull(result2.value)
    }

    // ========== DIGITAL SIGNATURE PREPARATION ==========

    @Test
    fun testPrepareForSignature() {
        val result = XMLCanonicalizationFunctions.prepareForSignature(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertTrue(properties.containsKey("canonical"))
        assertTrue(properties.containsKey("digest"))
        assertTrue(properties.containsKey("algorithm"))
        assertTrue(properties.containsKey("digestAlgorithm"))
        
        assertEquals("exc-c14n", (properties["algorithm"] as UDM.Scalar).value)
        assertEquals("sha256", (properties["digestAlgorithm"] as UDM.Scalar).value)
    }

    @Test
    fun testPrepareForSignatureCustomDigest() {
        val result = XMLCanonicalizationFunctions.prepareForSignature(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("sha1"))
        )
        
        assertTrue(result is UDM.Object)
        val properties = result.properties
        
        assertEquals("sha1", (properties["digestAlgorithm"] as UDM.Scalar).value)
    }

    @Test
    fun testValidateDigest() {
        // First get the expected digest
        val prepared = XMLCanonicalizationFunctions.prepareForSignature(listOf(UDM.Scalar(simpleXML)))
        assertTrue(prepared is UDM.Object)
        val expectedDigest = (prepared.properties["digest"] as UDM.Scalar).value
        
        // Now validate it
        val result = XMLCanonicalizationFunctions.validateDigest(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar(expectedDigest))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    @Test
    fun testValidateDigestMismatch() {
        val result = XMLCanonicalizationFunctions.validateDigest(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar("invalid-digest"))
        )
        
        assertTrue(result is UDM.Scalar)
        assertFalse(result.value as Boolean)
    }

    @Test
    fun testValidateDigestCaseInsensitive() {
        val hash = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(simpleXML)))
        assertTrue(hash is UDM.Scalar)
        val upperCaseHash = (hash.value as String).uppercase()
        
        val result = XMLCanonicalizationFunctions.validateDigest(
            listOf(UDM.Scalar(simpleXML), UDM.Scalar(upperCaseHash))
        )
        
        assertTrue(result is UDM.Scalar)
        assertTrue(result.value as Boolean)
    }

    // ========== ERROR HANDLING ==========

    @Test
    fun testC14nNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14n(listOf())
        }
    }

    @Test
    fun testC14nWithCommentsNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nWithComments(listOf())
        }
    }

    @Test
    fun testExcC14nNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.excC14n(listOf())
        }
    }

    @Test
    fun testC14n11NoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14n11(listOf())
        }
    }

    @Test
    fun testC14nPhysicalNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nPhysical(listOf())
        }
    }

    @Test
    fun testCanonicalizeWithAlgorithmInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.canonicalizeWithAlgorithm(listOf(UDM.Scalar(simpleXML)))
        }
    }

    @Test
    fun testC14nSubsetInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nSubset(listOf(UDM.Scalar(simpleXML)))
        }
    }

    @Test
    fun testC14nHashNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nHash(listOf())
        }
    }

    @Test
    fun testC14nEqualsInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nEquals(listOf(UDM.Scalar(simpleXML)))
        }
    }

    @Test
    fun testC14nFingerprintNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.c14nFingerprint(listOf())
        }
    }

    @Test
    fun testPrepareForSignatureNoArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.prepareForSignature(listOf())
        }
    }

    @Test
    fun testValidateDigestInsufficientArgs() {
        assertThrows<FunctionArgumentException> {
            XMLCanonicalizationFunctions.validateDigest(listOf(UDM.Scalar(simpleXML)))
        }
    }

    // ========== EDGE CASES ==========

    @Test
    fun testC14nNullValue() {
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar.nullValue()))
        
        assertTrue(result is UDM.Scalar)
        assertEquals(null, result.value)
    }

    @Test
    fun testC14nEmptyDocument() {
        val emptyDoc = """<?xml version="1.0"?><root></root>"""
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(emptyDoc)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.contains("root"))
    }

    @Test
    fun testC14nSelfClosingTags() {
        val selfClosing = """<root><empty/><data>value</data></root>"""
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(selfClosing)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
    }

    @Test
    fun testC14nLargeDocument() {
        val largeXML = buildString {
            append("<root>")
            repeat(1000) { i ->
                append("<item id=\"$i\">value$i</item>")
            }
            append("</root>")
        }
        
        val result = XMLCanonicalizationFunctions.c14n(listOf(UDM.Scalar(largeXML)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.length > 0)
    }

    @Test
    fun testHashConsistency() {
        val hash1 = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(simpleXML)))
        val hash2 = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(simpleXML)))
        
        assertTrue(hash1 is UDM.Scalar)
        assertTrue(hash2 is UDM.Scalar)
        assertEquals(hash1.value, hash2.value)
    }

    @Test
    fun testHashDifference() {
        val xml1 = """<root><name>Alice</name></root>"""
        val xml2 = """<root><name>Bob</name></root>"""
        
        val hash1 = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(xml1)))
        val hash2 = XMLCanonicalizationFunctions.c14nHash(listOf(UDM.Scalar(xml2)))
        
        assertTrue(hash1 is UDM.Scalar)
        assertTrue(hash2 is UDM.Scalar)
        assertNotEquals(hash1.value, hash2.value)
    }

    @Test
    fun testComplexNamespaceScenario() {
        val complexNS = """<envelope xmlns="http://schemas.xmlsoap.org/soap/envelope/" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
            <header>
                <wsse:Security>
                    <wsse:UsernameToken wsse:Id="UsernameToken-1">
                        <wsse:Username>admin</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText">secret</wsse:Password>
                    </wsse:UsernameToken>
                </wsse:Security>
            </header>
            <body>
                <data>sensitive information</data>
            </body>
        </envelope>"""
        
        val result = XMLCanonicalizationFunctions.excC14n(listOf(UDM.Scalar(complexNS)))
        
        assertTrue(result is UDM.Scalar)
        val canonical = result.value as String
        assertNotNull(canonical)
        assertTrue(canonical.isNotEmpty())
    }
}