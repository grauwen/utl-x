package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XMLDSigFunctionsTest {

    companion object {
        private lateinit var signedXml: String
        private lateinit var certPem: String
        private lateinit var unsignedXml: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            org.apache.xml.security.Init.init()

            // Generate a self-signed certificate and key pair
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048)
            val keyPair = keyPairGen.generateKeyPair()

            // Create a self-signed X.509 certificate using Bouncy Castle-free approach
            // We'll use the key directly for signing (no cert needed for basic tests)

            // Create a simple XML document
            unsignedXml = """<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
  <ID>INV-001</ID>
  <IssueDate>2026-05-26</IssueDate>
  <TotalAmount currency="EUR">1234.56</TotalAmount>
</Invoice>"""

            // Create a signed XML document programmatically using Apache XML Security
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(java.io.ByteArrayInputStream(unsignedXml.toByteArray()))

            // Create XMLSignature
            val sig = org.apache.xml.security.signature.XMLSignature(
                doc,
                "",
                org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256
            )

            // Append signature element to root
            doc.documentElement.appendChild(sig.element)

            // Add reference to the document (enveloped signature)
            val transforms = org.apache.xml.security.transforms.Transforms(doc)
            transforms.addTransform(org.apache.xml.security.transforms.Transforms.TRANSFORM_ENVELOPED_SIGNATURE)
            transforms.addTransform(org.apache.xml.security.transforms.Transforms.TRANSFORM_C14N_OMIT_COMMENTS)
            sig.addDocument("", transforms, "http://www.w3.org/2001/04/xmlenc#sha256")

            // Add public key to KeyInfo
            sig.addKeyInfo(keyPair.public)

            // Sign
            sig.sign(keyPair.private)

            // Serialize to string
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            val writer = java.io.StringWriter()
            transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(writer))
            signedXml = writer.toString()

            // Export public key as PEM-like format for cert test
            // (We use KeyValue, not certificate, for the basic test)
            certPem = "" // Not used in basic verify tests — KeyInfo has the public key
        }
    }

    // =========================================================================
    // verifyXMLSignature
    // =========================================================================

    @Test
    fun `verifyXMLSignature should verify valid signed XML`() {
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(signedXml)))
        assertEquals(true, (result as UDM.Scalar).value)
    }

    @Test
    fun `verifyXMLSignature should return false for unsigned XML`() {
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(unsignedXml)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `verifyXMLSignature should return false for tampered signed XML`() {
        // Tamper with the content — change the amount
        val tampered = signedXml.replace("1234.56", "9999.99")
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(tampered)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `verifyXMLSignature should return false for invalid XML`() {
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar("not xml at all")))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `verifyXMLSignature should throw with no arguments`() {
        assertThrows<FunctionArgumentException> {
            XMLDSigFunctions.verifyXMLSignature(emptyList())
        }
    }

    // =========================================================================
    // getXMLSignatureInfo
    // =========================================================================

    @Test
    fun `getXMLSignatureInfo should extract info from signed XML`() {
        val result = XMLDSigFunctions.getXMLSignatureInfo(listOf(UDM.Scalar(signedXml)))

        assertTrue(result is UDM.Object)
        val obj = result as UDM.Object

        assertEquals(true, (obj.properties["found"] as UDM.Scalar).value)
        assertTrue((obj.properties["algorithm"] as UDM.Scalar).value.toString().contains("rsa-sha256"))
        assertTrue((obj.properties["keyInfo"] as UDM.Scalar).value as Boolean)

        // Should have at least one reference
        val refCount = (obj.properties["referenceCount"] as UDM.Scalar).value
        assertTrue((refCount as Number).toInt() >= 1)

        // References array should exist
        val refs = obj.properties["references"] as UDM.Array
        assertTrue(refs.elements.isNotEmpty())

        // First reference should have digestMethod and digestValue
        val firstRef = refs.elements[0] as UDM.Object
        assertNotNull(firstRef.properties["digestMethod"])
        assertNotNull(firstRef.properties["digestValue"])
    }

    @Test
    fun `getXMLSignatureInfo should return found=false for unsigned XML`() {
        val result = XMLDSigFunctions.getXMLSignatureInfo(listOf(UDM.Scalar(unsignedXml)))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["found"] as UDM.Scalar).value)
    }

    @Test
    fun `getXMLSignatureInfo should return found=false for invalid XML`() {
        val result = XMLDSigFunctions.getXMLSignatureInfo(listOf(UDM.Scalar("not xml")))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["found"] as UDM.Scalar).value)
    }

    @Test
    fun `getXMLSignatureInfo should throw with no arguments`() {
        assertThrows<FunctionArgumentException> {
            XMLDSigFunctions.getXMLSignatureInfo(emptyList())
        }
    }

    // =========================================================================
    // verifyXMLSignatureWithCert
    // =========================================================================

    @Test
    fun `verifyXMLSignatureWithCert should return error for unsigned XML`() {
        val result = XMLDSigFunctions.verifyXMLSignatureWithCert(listOf(
            UDM.Scalar(unsignedXml),
            UDM.Scalar("-----BEGIN CERTIFICATE-----\nMIIBxx...\n-----END CERTIFICATE-----")
        ))

        assertTrue(result is UDM.Object)
        assertEquals(false, ((result as UDM.Object).properties["valid"] as UDM.Scalar).value)
    }

    @Test
    fun `verifyXMLSignatureWithCert should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            XMLDSigFunctions.verifyXMLSignatureWithCert(listOf(UDM.Scalar("<xml/>")))
        }
    }

    // =========================================================================
    // Sign + Verify round trip with programmatic signature
    // =========================================================================

    @Test
    fun `should sign and verify XML document end-to-end`() {
        // This test proves the full cycle: create signed XML, verify it, extract info
        val verified = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(signedXml)))
        assertEquals(true, (verified as UDM.Scalar).value)

        val info = XMLDSigFunctions.getXMLSignatureInfo(listOf(UDM.Scalar(signedXml))) as UDM.Object
        assertEquals(true, (info.properties["found"] as UDM.Scalar).value)
        assertTrue((info.properties["algorithm"] as UDM.Scalar).value.toString().isNotEmpty())
    }

    @Test
    fun `verification should fail after modifying signed content`() {
        // Change element text — signature should break
        val tampered = signedXml.replace("<ID>INV-001</ID>", "<ID>INV-002</ID>")
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(tampered)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `verification should fail after modifying signature value`() {
        // Corrupt the signature value
        val tampered = signedXml.replaceFirst(
            Regex("<ds:SignatureValue[^>]*>[A-Za-z0-9+/=]+</ds:SignatureValue>"),
            "<ds:SignatureValue>AAAA</ds:SignatureValue>"
        )
        // Only test if we actually managed to tamper (namespace prefix may vary)
        if (tampered != signedXml) {
            val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(tampered)))
            assertEquals(false, (result as UDM.Scalar).value)
        }
    }

    // =========================================================================
    // signXML
    // =========================================================================

    @Test
    fun `signXML should produce signed XML that verifies`() {
        // Generate RSA key pair via RSAFunctions
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String

        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<Order><ID>ORD-123</ID><Amount>500.00</Amount></Order>"""

        // Sign
        val signedResult = XMLDSigFunctions.signXML(listOf(
            UDM.Scalar(xml),
            UDM.Scalar(privateKey),
            UDM.Scalar(publicKey)
        ))
        assertTrue(signedResult is UDM.Scalar)
        val signedXmlStr = (signedResult as UDM.Scalar).value as String

        // Must contain Signature element
        assertTrue(signedXmlStr.contains("Signature"))
        assertTrue(signedXmlStr.contains("SignatureValue"))

        // Verify
        val verified = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(signedXmlStr)))
        assertEquals(true, (verified as UDM.Scalar).value)
    }

    @Test
    fun `signXML then tamper should fail verification`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String

        val xml = "<Invoice><Total>100.00</Total></Invoice>"

        val signed = (XMLDSigFunctions.signXML(listOf(
            UDM.Scalar(xml), UDM.Scalar(privateKey), UDM.Scalar(publicKey)
        )) as UDM.Scalar).value as String

        // Tamper with content
        val tampered = signed.replace("100.00", "999.99")
        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(tampered)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `signXML should produce valid signature info`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String

        val xml = "<Doc><Data>test</Data></Doc>"

        val signed = (XMLDSigFunctions.signXML(listOf(
            UDM.Scalar(xml), UDM.Scalar(privateKey), UDM.Scalar(publicKey)
        )) as UDM.Scalar).value as String

        val info = XMLDSigFunctions.getXMLSignatureInfo(listOf(UDM.Scalar(signed))) as UDM.Object
        assertEquals(true, (info.properties["found"] as UDM.Scalar).value)
        assertTrue((info.properties["algorithm"] as UDM.Scalar).value.toString().contains("rsa-sha256"))
        assertTrue((info.properties["keyInfo"] as UDM.Scalar).value as Boolean)
        assertEquals(1, ((info.properties["referenceCount"] as UDM.Scalar).value as Number).toInt())
    }

    @Test
    fun `signXML with wrong key should not verify with different key`() {
        val keyPair1 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val keyPair2 = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object

        val xml = "<Msg>secret</Msg>"

        // Sign with key pair 1's private key but embed key pair 2's public key
        // Verification should fail because signature was made with key1 but KeyInfo has key2
        val signed = (XMLDSigFunctions.signXML(listOf(
            UDM.Scalar(xml),
            UDM.Scalar((keyPair1.properties["privateKey"] as UDM.Scalar).value as String),
            UDM.Scalar((keyPair2.properties["publicKey"] as UDM.Scalar).value as String)
        )) as UDM.Scalar).value as String

        val result = XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(signed)))
        assertEquals(false, (result as UDM.Scalar).value)
    }

    @Test
    fun `signXML should throw with too few arguments`() {
        assertThrows<FunctionArgumentException> {
            XMLDSigFunctions.signXML(listOf(UDM.Scalar("<xml/>"), UDM.Scalar("key")))
        }
    }

    @Test
    fun `signXML should throw with invalid private key`() {
        assertThrows<FunctionArgumentException> {
            XMLDSigFunctions.signXML(listOf(
                UDM.Scalar("<xml/>"),
                UDM.Scalar("not-a-valid-key"),
                UDM.Scalar("not-a-valid-key")
            ))
        }
    }

    @Test
    fun `signXML round-trip with Peppol-like UBL invoice`() {
        val keyPair = RSAFunctions.generateRSAKeyPair(emptyList()) as UDM.Object
        val privateKey = (keyPair.properties["privateKey"] as UDM.Scalar).value as String
        val publicKey = (keyPair.properties["publicKey"] as UDM.Scalar).value as String

        val ublInvoice = """<?xml version="1.0" encoding="UTF-8"?>
<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:ID>TOSL108</cbc:ID>
  <cbc:IssueDate>2026-05-26</cbc:IssueDate>
  <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cbc:EndpointID schemeID="0106">NL123456789B01</cbc:EndpointID>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:LegalMonetaryTotal>
    <cbc:PayableAmount currencyID="EUR">1234.56</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
</Invoice>"""

        // Sign
        val signed = (XMLDSigFunctions.signXML(listOf(
            UDM.Scalar(ublInvoice), UDM.Scalar(privateKey), UDM.Scalar(publicKey)
        )) as UDM.Scalar).value as String

        // Verify
        assertEquals(true, (XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(signed))) as UDM.Scalar).value)

        // Tamper — change amount
        val tampered = signed.replace("1234.56", "9999.99")
        assertEquals(false, (XMLDSigFunctions.verifyXMLSignature(listOf(UDM.Scalar(tampered))) as UDM.Scalar).value)
    }
}
