package org.apache.utlx.stdlib.crypto

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * F11: XML Digital Signature (XMLDSig) Verification Functions.
 *
 * Verifies XML signatures per W3C XMLDSig specification (RFC 3275).
 * Required for:
 * - Peppol e-invoicing (XAdES-BES signatures on UBL invoices)
 * - SOAP WS-Security message validation
 * - HL7 FHIR document verification
 *
 * Uses Apache XML Security (xmlsec) library — already a dependency for C14N.
 */
object XMLDSigFunctions {

    init {
        org.apache.xml.security.Init.init()
    }

    // =========================================================================
    // verifyXMLSignature(xml) → boolean
    // =========================================================================

    /**
     * Verify an XML digital signature.
     * Returns true if the signature is valid, false otherwise.
     *
     * The XML must contain a ds:Signature element. The function:
     * 1. Parses the XML document
     * 2. Locates the Signature element
     * 3. Validates the signature using the embedded key info
     *
     * @param args [xml] — XML string containing a ds:Signature element
     * @return UDM.Scalar(true/false)
     */
    @UTLXFunction(
        description = "Verify an XML digital signature using the embedded KeyInfo public key. Returns true if valid.",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        returns = "Boolean - true if the XML signature is valid, false otherwise",
        example = "verifyXMLSignature(\$signedXml) => true",
        tags = ["xmldsig", "verification", "xml", "security", "peppol"],
        since = "1.2"
    )
    fun verifyXMLSignature(args: List<UDM>): UDM {
        if (args.isEmpty()) throw FunctionArgumentException("verifyXMLSignature expects 1 argument (xml), got 0")

        val xml = asString(args[0])

        return try {
            val doc = parseXML(xml)
            val signatureElement = findSignatureElement(doc)
                ?: return UDM.Scalar(false)

            val signature = org.apache.xml.security.signature.XMLSignature(signatureElement, "")
            val keyInfo = signature.keyInfo

            // Try to get the verification key from KeyInfo
            val key = when {
                keyInfo != null && keyInfo.containsX509Data() -> {
                    keyInfo.x509Certificate?.publicKey
                }
                keyInfo != null -> {
                    keyInfo.publicKey
                }
                else -> null
            }

            if (key == null) {
                return UDM.Scalar(false)
            }

            UDM.Scalar(signature.checkSignatureValue(key))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    // =========================================================================
    // getXMLSignatureInfo(xml) → object
    // =========================================================================

    /**
     * Extract information from an XML digital signature without verifying it.
     * Returns: {
     *   found: boolean,
     *   algorithm: "...",
     *   canonicalizationMethod: "...",
     *   referenceCount: N,
     *   references: [{uri, digestMethod, digestValue}, ...],
     *   certificate: {subject, issuer, serial, notBefore, notAfter} | null,
     *   keyInfo: boolean
     * }
     */
    @UTLXFunction(
        description = "Extract metadata from an XML digital signature without verifying it. Returns algorithm, references, and certificate info.",
        minArgs = 1,
        maxArgs = 1,
        category = "Security",
        returns = "Object - {found, algorithm, canonicalizationMethod, referenceCount, references, keyInfo, certificate?}",
        example = "getXMLSignatureInfo(\$signedXml) => {found: true, algorithm: '...rsa-sha256', ...}",
        tags = ["xmldsig", "inspection", "xml", "security"],
        since = "1.2"
    )
    fun getXMLSignatureInfo(args: List<UDM>): UDM {
        if (args.isEmpty()) throw FunctionArgumentException("getXMLSignatureInfo expects 1 argument (xml), got 0")

        val xml = asString(args[0])

        return try {
            val doc = parseXML(xml)
            val signatureElement = findSignatureElement(doc)
                ?: return UDM.Object.of("found" to UDM.Scalar(false))

            val signature = org.apache.xml.security.signature.XMLSignature(signatureElement, "")

            val props = mutableMapOf<String, UDM>(
                "found" to UDM.Scalar(true),
                "algorithm" to UDM.Scalar(signature.signedInfo.signatureMethodURI ?: "unknown"),
                "canonicalizationMethod" to UDM.Scalar(signature.signedInfo.canonicalizationMethodURI ?: "unknown"),
                "referenceCount" to UDM.Scalar(signature.signedInfo.length)
            )

            // Extract references
            val refs = mutableListOf<UDM>()
            for (i in 0 until signature.signedInfo.length) {
                val ref = signature.signedInfo.item(i)
                refs.add(UDM.Object.of(
                    "uri" to UDM.Scalar(ref.uri ?: ""),
                    "digestMethod" to UDM.Scalar(ref.messageDigestAlgorithm?.algorithmURI ?: "unknown"),
                    "digestValue" to UDM.Scalar(Base64.getEncoder().encodeToString(ref.digestValue))
                ))
            }
            props["references"] = UDM.Array(refs)

            // Extract certificate info if present
            val keyInfo = signature.keyInfo
            props["keyInfo"] = UDM.Scalar(keyInfo != null)

            if (keyInfo != null && keyInfo.containsX509Data()) {
                try {
                    val cert = keyInfo.x509Certificate
                    if (cert != null) {
                        props["certificate"] = UDM.Object.of(
                            "subject" to UDM.Scalar(cert.subjectX500Principal.name),
                            "issuer" to UDM.Scalar(cert.issuerX500Principal.name),
                            "serial" to UDM.Scalar(cert.serialNumber.toString()),
                            "notBefore" to UDM.Scalar(cert.notBefore.toInstant().toString()),
                            "notAfter" to UDM.Scalar(cert.notAfter.toInstant().toString())
                        )
                    }
                } catch (_: Exception) {
                    // Certificate extraction failed — not critical
                }
            }

            UDM.Object(props)
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            UDM.Object.of(
                "found" to UDM.Scalar(false),
                "error" to UDM.Scalar("Failed to parse signature: ${e.message}")
            )
        }
    }

    // =========================================================================
    // verifyXMLSignatureWithCert(xml, certPem) → object
    // =========================================================================

    /**
     * Verify an XML digital signature against a specific certificate.
     * Use this when you have the signer's certificate and want to verify
     * against it explicitly (rather than using the embedded KeyInfo).
     *
     * @param args [xml, certificatePEM] — XML string and PEM-encoded X.509 certificate
     * @return {valid: boolean, error?: "..."}
     */
    @UTLXFunction(
        description = "Verify an XML digital signature against a specific PEM-encoded X.509 certificate.",
        minArgs = 2,
        maxArgs = 2,
        category = "Security",
        returns = "Object - {valid: boolean, error?: string}",
        example = "verifyXMLSignatureWithCert(\$signedXml, \$certPem) => {valid: true}",
        tags = ["xmldsig", "verification", "certificate", "xml", "security"],
        since = "1.2"
    )
    fun verifyXMLSignatureWithCert(args: List<UDM>): UDM {
        if (args.size < 2) throw FunctionArgumentException("verifyXMLSignatureWithCert expects 2 arguments (xml, certificatePEM), got ${args.size}")

        val xml = asString(args[0])
        val certPem = asString(args[1])

        return try {
            val doc = parseXML(xml)
            val signatureElement = findSignatureElement(doc)
                ?: return UDM.Object.of(
                    "valid" to UDM.Scalar(false),
                    "error" to UDM.Scalar("No Signature element found in XML")
                )

            // Parse the PEM certificate
            val certBytes = pemToBytes(certPem)
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = certFactory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate

            val signature = org.apache.xml.security.signature.XMLSignature(signatureElement, "")
            val valid = signature.checkSignatureValue(cert)

            UDM.Object.of("valid" to UDM.Scalar(valid))
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            UDM.Object.of(
                "valid" to UDM.Scalar(false),
                "error" to UDM.Scalar("Verification failed: ${e.message}")
            )
        }
    }

    // =========================================================================
    // signXML(xml, privateKeyBase64, publicKeyBase64, algorithm?) → signed XML string
    // =========================================================================

    /**
     * Create an enveloped XML digital signature.
     *
     * Signs the entire XML document using RSA and appends a ds:Signature element
     * to the document root (enveloped signature pattern).
     *
     * @param args [xml, privateKeyBase64, publicKeyBase64, algorithm?]
     *   - xml: XML string to sign
     *   - privateKeyBase64: Base64-encoded PKCS8 RSA private key (from generateRSAKeyPair)
     *   - publicKeyBase64: Base64-encoded X509 RSA public key (from generateRSAKeyPair)
     *   - algorithm: signing algorithm URI (default: "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256")
     * @return UDM.Scalar containing the signed XML string
     */
    @UTLXFunction(
        description = "Create an enveloped XML digital signature (XMLDSig) using RSA. Appends ds:Signature to the document root.",
        minArgs = 3,
        maxArgs = 4,
        category = "Security",
        returns = "String - Signed XML document with embedded ds:Signature element",
        example = "signXML(\$input, \$privateKey, \$publicKey) => '<Invoice>...<ds:Signature>...</ds:Signature></Invoice>'",
        tags = ["xmldsig", "signing", "xml", "security", "peppol"],
        since = "1.2"
    )
    fun signXML(args: List<UDM>): UDM {
        if (args.size < 3) throw FunctionArgumentException(
            "signXML expects 3-4 arguments (xml, privateKey, publicKey, algorithm?), got ${args.size}"
        )

        val xml = asString(args[0])
        val privateKeyB64 = asString(args[1])
        val publicKeyB64 = asString(args[2])
        val algorithm = if (args.size > 3) asString(args[3])
            else org.apache.xml.security.signature.XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256

        return try {
            val doc = parseXML(xml)

            // Reconstruct RSA keys from Base64
            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyB64))
            )
            val publicKey = keyFactory.generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyB64))
            )

            // Create XMLSignature
            val sig = org.apache.xml.security.signature.XMLSignature(doc, "", algorithm)

            // Append signature element to document root
            doc.documentElement.appendChild(sig.element)

            // Add enveloped signature transform + C14N
            val transforms = org.apache.xml.security.transforms.Transforms(doc)
            transforms.addTransform(org.apache.xml.security.transforms.Transforms.TRANSFORM_ENVELOPED_SIGNATURE)
            transforms.addTransform(org.apache.xml.security.transforms.Transforms.TRANSFORM_C14N_OMIT_COMMENTS)
            sig.addDocument("", transforms, "http://www.w3.org/2001/04/xmlenc#sha256")

            // Add public key to KeyInfo so verifiers can extract it
            sig.addKeyInfo(publicKey)

            // Sign with private key
            sig.sign(privateKey)

            // Serialize to string
            val transformer = TransformerFactory.newInstance().newTransformer()
            val writer = StringWriter()
            transformer.transform(DOMSource(doc), StreamResult(writer))

            UDM.Scalar(writer.toString())
        } catch (e: FunctionArgumentException) {
            throw e
        } catch (e: Exception) {
            throw FunctionArgumentException("signXML failed: ${e.message}")
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private fun parseXML(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // Security: disable external entities
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun findSignatureElement(doc: Document): Element? {
        val signatureNodes: NodeList = doc.getElementsByTagNameNS(
            "http://www.w3.org/2000/09/xmldsig#", "Signature"
        )
        return if (signatureNodes.length > 0) signatureNodes.item(0) as Element else null
    }

    private fun pemToBytes(pem: String): ByteArray {
        val cleaned = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\\s".toRegex(), "")
        return Base64.getDecoder().decode(cleaned)
    }

    private fun asString(udm: UDM): String = when (udm) {
        is UDM.Scalar -> udm.value?.toString() ?: throw FunctionArgumentException("Expected string, got null")
        else -> throw FunctionArgumentException("Expected string, got ${udm::class.simpleName}")
    }
}
