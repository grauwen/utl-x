// stdlib/src/main/kotlin/org/apache/utlx/stdlib/xml/XMLCanonicalizationFunctions.kt
package org.apache.utlx.stdlib.xml

import org.apache.utlx.core.udm.*
import org.w3c.dom.*
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.apache.xml.security.c14n.Canonicalizer
import org.apache.xml.security.c14n.InvalidCanonicalizerException
import java.io.ByteArrayInputStream

/**
 * XML Canonicalization (C14N) functions for UTL-X
 * 
 * Implements all W3C XML Canonicalization specifications:
 * - Canonical XML 1.0 (C14N)
 * - Exclusive Canonical XML (Exc-C14N)
 * - Canonical XML 1.1 (C14N 1.1)
 * 
 * Essential for:
 * - XML Digital Signatures (XMLDSig)
 * - XML comparison and hashing
 * - SOAP/WS-Security
 * - SAML authentication
 * - Financial transactions (ISO 20022)
 * 
 * @since 1.0.0
 */
object XMLCanonicalizationFunctions {
    
    // Initialize Apache XML Security library
    init {
        org.apache.xml.security.Init.init()
    }
    
    // ============================================
    // C14N 1.0 - CANONICAL XML 1.0
    // ============================================
    
    /**
     * Canonicalizes XML using Canonical XML 1.0 (without comments)
     * 
     * Algorithm: http://www.w3.org/TR/2001/REC-xml-c14n-20010315
     * 
     * Transforms XML into a canonical form by:
     * - Normalizing whitespace in attribute values
     * - Sorting namespace declarations and attributes
     * - Removing XML/DOCTYPE declarations
     * - Normalizing empty elements
     * - Removing comments (in this variant)
     * 
     * @param xml XML string or document
     * @return Canonicalized XML string
     * 
     * Example:
     * ```
     * c14n(xmlDoc) // Canonical form without comments
     * ```
     */
    fun c14n(xml: UDMValue): UDMValue {
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS)
    }
    
    /**
     * Canonicalizes XML using Canonical XML 1.0 (with comments)
     * 
     * Algorithm: http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments
     * 
     * Same as c14n() but preserves XML comments in canonical form.
     * 
     * @param xml XML string or document
     * @return Canonicalized XML string with comments
     * 
     * Example:
     * ```
     * c14nWithComments(xmlDoc) // Preserves comments
     * ```
     */
    fun c14nWithComments(xml: UDMValue): UDMValue {
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS)
    }
    
    // ============================================
    // EXC-C14N - EXCLUSIVE CANONICAL XML
    // ============================================
    
    /**
     * Canonicalizes XML using Exclusive Canonical XML (without comments)
     * 
     * Algorithm: http://www.w3.org/2001/10/xml-exc-c14n#
     * 
     * Exclusive canonicalization only includes namespace declarations
     * that are actually used, making it ideal for XML signatures in
     * SOAP envelopes where the document is embedded in other XML.
     * 
     * @param xml XML string or document
     * @param inclusiveNamespaces Optional space-separated namespace prefixes to include
     * @return Canonicalized XML string
     * 
     * Example:
     * ```
     * excC14n(xmlDoc) // Exclusive canonical form
     * excC14n(xmlDoc, "ds xenc") // Include ds and xenc namespaces
     * ```
     */
    fun excC14n(xml: UDMValue, inclusiveNamespaces: UDMValue = UDMNull): UDMValue {
        val inclusivePrefixes = (inclusiveNamespaces as? UDMString)?.value
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS, inclusivePrefixes)
    }
    
    /**
     * Canonicalizes XML using Exclusive Canonical XML (with comments)
     * 
     * Algorithm: http://www.w3.org/2001/10/xml-exc-c14n#WithComments
     * 
     * Same as excC14n() but preserves comments.
     * 
     * @param xml XML string or document
     * @param inclusiveNamespaces Optional space-separated namespace prefixes
     * @return Canonicalized XML string with comments
     * 
     * Example:
     * ```
     * excC14nWithComments(xmlDoc, "ds")
     * ```
     */
    fun excC14nWithComments(xml: UDMValue, inclusiveNamespaces: UDMValue = UDMNull): UDMValue {
        val inclusivePrefixes = (inclusiveNamespaces as? UDMString)?.value
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS, inclusivePrefixes)
    }
    
    // ============================================
    // C14N 1.1 - CANONICAL XML 1.1
    // ============================================
    
    /**
     * Canonicalizes XML using Canonical XML 1.1 (without comments)
     * 
     * Algorithm: http://www.w3.org/2006/12/xml-c14n11
     * 
     * C14N 1.1 improves upon 1.0 by:
     * - Better handling of xml:id attributes
     * - Improved namespace handling
     * - Better support for XML 1.1 features
     * 
     * @param xml XML string or document
     * @return Canonicalized XML string
     * 
     * Example:
     * ```
     * c14n11(xmlDoc) // Canonical XML 1.1
     * ```
     */
    fun c14n11(xml: UDMValue): UDMValue {
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS)
    }
    
    /**
     * Canonicalizes XML using Canonical XML 1.1 (with comments)
     * 
     * Algorithm: http://www.w3.org/2006/12/xml-c14n11#WithComments
     * 
     * @param xml XML string or document
     * @return Canonicalized XML string with comments
     * 
     * Example:
     * ```
     * c14n11WithComments(xmlDoc)
     * ```
     */
    fun c14n11WithComments(xml: UDMValue): UDMValue {
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS)
    }
    
    // ============================================
    // PHYSICAL CANONICAL XML (NON-STANDARD)
    // ============================================
    
    /**
     * Canonicalizes XML using Physical Canonical XML
     * 
     * Physical canonicalization preserves the physical structure
     * of the XML document, including insignificant whitespace.
     * 
     * This is a non-standard algorithm useful for debugging
     * and preserving the exact structure of source documents.
     * 
     * @param xml XML string or document
     * @return Canonicalized XML string
     * 
     * Example:
     * ```
     * c14nPhysical(xmlDoc) // Preserves physical structure
     * ```
     */
    fun c14nPhysical(xml: UDMValue): UDMValue {
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_PHYSICAL)
    }
    
    // ============================================
    // GENERIC CANONICALIZATION
    // ============================================
    
    /**
     * Canonicalizes XML using specified algorithm
     * 
     * @param xml XML string or document
     * @param algorithm C14N algorithm identifier
     * @param inclusiveNamespaces Optional namespace prefixes (for Exc-C14N)
     * @return Canonicalized XML string
     * 
     * Supported algorithms:
     * - "c14n" - Canonical XML 1.0 without comments
     * - "c14n-with-comments" - Canonical XML 1.0 with comments
     * - "exc-c14n" - Exclusive C14N without comments
     * - "exc-c14n-with-comments" - Exclusive C14N with comments
     * - "c14n11" - Canonical XML 1.1 without comments
     * - "c14n11-with-comments" - Canonical XML 1.1 with comments
     * - "physical" - Physical C14N
     * 
     * Example:
     * ```
     * canonicalize(xmlDoc, "exc-c14n", "ds xenc")
     * ```
     */
    fun canonicalizeWithAlgorithm(
        xml: UDMValue, 
        algorithm: UDMValue,
        inclusiveNamespaces: UDMValue = UDMNull
    ): UDMValue {
        val algo = (algorithm as? UDMString)?.value ?: return UDMNull
        
        val algoId = when (algo.lowercase()) {
            "c14n" -> Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
            "c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS
            "exc-c14n" -> Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            "exc-c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS
            "c14n11" -> Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS
            "c14n11-with-comments" -> Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS
            "physical" -> Canonicalizer.ALGO_ID_C14N_PHYSICAL
            else -> return UDMNull
        }
        
        val inclusivePrefixes = (inclusiveNamespaces as? UDMString)?.value
        return canonicalize(xml, algoId, inclusivePrefixes)
    }
    
    // ============================================
    // XPATH SUBSET CANONICALIZATION
    // ============================================
    
    /**
     * Canonicalizes a subset of XML selected by XPath
     * 
     * Useful for signing or hashing specific parts of an XML document.
     * 
     * @param xml XML string or document
     * @param xpathExpr XPath expression to select nodes
     * @param algorithm C14N algorithm (default: c14n)
     * @return Canonicalized XML string of selected nodes
     * 
     * Example:
     * ```
     * // Canonicalize only the body element
     * c14nSubset(soapEnvelope, "//soap:Body", "exc-c14n")
     * 
     * // Canonicalize specific elements
     * c14nSubset(doc, "/root/data[@type='sensitive']")
     * ```
     */
    fun c14nSubset(
        xml: UDMValue,
        xpathExpr: UDMValue,
        algorithm: UDMValue = UDMString("c14n")
    ): UDMValue {
        val xpath = (xpathExpr as? UDMString)?.value ?: return UDMNull
        val algo = (algorithm as? UDMString)?.value ?: "c14n"
        
        return try {
            val doc = parseXML(xml) ?: return UDMNull
            
            // Select nodes using XPath
            val xpathFactory = javax.xml.xpath.XPathFactory.newInstance()
            val xpathObj = xpathFactory.newXPath()
            val nodeList = xpathObj.evaluate(xpath, doc, javax.xml.xpath.XPathConstants.NODESET) as NodeList
            
            if (nodeList.length == 0) {
                return UDMString("")
            }
            
            // Canonicalize each selected node
            val algoId = mapAlgorithmName(algo)
            val canonicalizer = Canonicalizer.getInstance(algoId)
            val output = ByteArrayOutputStream()
            
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                val canonical = canonicalizer.canonicalizeSubtree(node)
                output.write(canonical)
            }
            
            UDMString(output.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    // ============================================
    // HASH AND SIGN HELPERS
    // ============================================
    
    /**
     * Canonicalizes XML and computes its hash
     * 
     * Useful for XML comparison, caching, and digital signatures.
     * 
     * @param xml XML string or document
     * @param hashAlgorithm Hash algorithm (md5, sha1, sha256, sha512)
     * @param c14nAlgorithm C14N algorithm (default: c14n)
     * @return Hex-encoded hash of canonical XML
     * 
     * Example:
     * ```
     * // Compute SHA-256 hash of canonical XML
     * c14nHash(xmlDoc, "sha256", "exc-c14n")
     * 
     * // Quick hash for comparison
     * c14nHash(doc1, "sha256") == c14nHash(doc2, "sha256")
     * ```
     */
    fun c14nHash(
        xml: UDMValue,
        hashAlgorithm: UDMValue = UDMString("sha256"),
        c14nAlgorithm: UDMValue = UDMString("c14n")
    ): UDMValue {
        // First canonicalize
        val canonical = canonicalizeWithAlgorithm(xml, c14nAlgorithm)
        if (canonical is UDMNull) return UDMNull
        
        // Then hash
        val algo = (hashAlgorithm as? UDMString)?.value?.lowercase() ?: "sha256"
        
        return when (algo) {
            "md5" -> {
                val digest = java.security.MessageDigest.getInstance("MD5")
                val bytes = digest.digest((canonical as UDMString).value.toByteArray())
                UDMString(bytesToHex(bytes))
            }
            "sha1" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val bytes = digest.digest((canonical as UDMString).value.toByteArray())
                UDMString(bytesToHex(bytes))
            }
            "sha256" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest((canonical as UDMString).value.toByteArray())
                UDMString(bytesToHex(bytes))
            }
            "sha512" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-512")
                val bytes = digest.digest((canonical as UDMString).value.toByteArray())
                UDMString(bytesToHex(bytes))
            }
            else -> UDMNull
        }
    }
    
    /**
     * Compares two XML documents for canonical equivalence
     * 
     * Returns true if the canonical forms are byte-for-byte identical,
     * even if the original documents differ in formatting, attribute order,
     * namespace prefixes, etc.
     * 
     * @param xml1 First XML document
     * @param xml2 Second XML document
     * @param algorithm C14N algorithm to use (default: c14n)
     * @return true if canonically equivalent
     * 
     * Example:
     * ```
     * // These are canonically equivalent:
     * let xml1 = '<root a="1" b="2"/>'
     * let xml2 = '<root b="2" a="1"></root>'
     * c14nEquals(xml1, xml2) // true
     * ```
     */
    fun c14nEquals(
        xml1: UDMValue,
        xml2: UDMValue,
        algorithm: UDMValue = UDMString("c14n")
    ): UDMValue {
        val canonical1 = canonicalizeWithAlgorithm(xml1, algorithm)
        val canonical2 = canonicalizeWithAlgorithm(xml2, algorithm)
        
        if (canonical1 is UDMNull || canonical2 is UDMNull) {
            return UDMBoolean(false)
        }
        
        val str1 = (canonical1 as UDMString).value
        val str2 = (canonical2 as UDMString).value
        
        return UDMBoolean(str1 == str2)
    }
    
    /**
     * Creates a normalized fingerprint of XML for deduplication
     * 
     * Returns a short hash that can be used to detect duplicate
     * XML documents regardless of formatting differences.
     * 
     * @param xml XML string or document
     * @return Short hash fingerprint (16 characters)
     * 
     * Example:
     * ```
     * let fingerprint = c14nFingerprint(xmlDoc)
     * // Use for caching or deduplication
     * cache.put(fingerprint, transformedData)
     * ```
     */
    fun c14nFingerprint(xml: UDMValue): UDMValue {
        val hash = c14nHash(xml, UDMString("sha256"), UDMString("c14n"))
        if (hash is UDMNull) return UDMNull
        
        // Return first 16 characters of hash as fingerprint
        val fullHash = (hash as UDMString).value
        return UDMString(fullHash.take(16))
    }
    
    // ============================================
    // DIGITAL SIGNATURE PREPARATION
    // ============================================
    
    /**
     * Prepares XML for digital signature (XMLDSig)
     * 
     * Canonicalizes XML using Exclusive C14N (the recommended algorithm
     * for XML signatures) and returns both the canonical form and its hash.
     * 
     * @param xml XML string or document
     * @param digestAlgorithm Digest algorithm (default: sha256)
     * @return Object with 'canonical' and 'digest' properties
     * 
     * Example:
     * ```
     * let prepared = prepareForSignature(xmlDoc)
     * // prepared.canonical - Canonical XML
     * // prepared.digest - SHA-256 digest
     * 
     * // Use digest with HMAC or RSA signing
     * let signature = hmacSHA256(prepared.digest, secretKey)
     * ```
     */
    fun prepareForSignature(
        xml: UDMValue,
        digestAlgorithm: UDMValue = UDMString("sha256")
    ): UDMValue {
        // Canonicalize using Exc-C14N (recommended for signatures)
        val canonical = excC14n(xml)
        if (canonical is UDMNull) return UDMNull
        
        // Compute digest
        val digest = c14nHash(xml, digestAlgorithm, UDMString("exc-c14n"))
        if (digest is UDMNull) return UDMNull
        
        return UDMObject(mapOf(
            "canonical" to canonical,
            "digest" to digest,
            "algorithm" to UDMString("exc-c14n"),
            "digestAlgorithm" to digestAlgorithm
        ))
    }
    
    /**
     * Validates that XML digest matches expected value
     * 
     * Used to verify XML digital signatures by comparing the digest
     * of canonical XML against a known reference value.
     * 
     * @param xml XML string or document
     * @param expectedDigest Expected digest value (hex string)
     * @param digestAlgorithm Digest algorithm (default: sha256)
     * @param c14nAlgorithm C14N algorithm (default: exc-c14n)
     * @return true if digest matches
     * 
     * Example:
     * ```
     * // Verify signature
     * let valid = validateDigest(
     *   receivedXML, 
     *   signatureDigest, 
     *   "sha256",
     *   "exc-c14n"
     * )
     * ```
     */
    fun validateDigest(
        xml: UDMValue,
        expectedDigest: UDMValue,
        digestAlgorithm: UDMValue = UDMString("sha256"),
        c14nAlgorithm: UDMValue = UDMString("exc-c14n")
    ): UDMValue {
        val expected = (expectedDigest as? UDMString)?.value ?: return UDMBoolean(false)
        
        val actualDigest = c14nHash(xml, digestAlgorithm, c14nAlgorithm)
        if (actualDigest is UDMNull) return UDMBoolean(false)
        
        val actual = (actualDigest as UDMString).value
        
        // Case-insensitive comparison
        return UDMBoolean(actual.equals(expected, ignoreCase = true))
    }
    
    // ============================================
    // INTERNAL HELPERS
    // ============================================
    
    /**
     * Internal canonicalization implementation
     */
    private fun canonicalize(
        xml: UDMValue, 
        algorithm: String,
        inclusiveNamespaces: String? = null
    ): UDMValue {
        return try {
            val doc = parseXML(xml) ?: return UDMNull
            
            val canonicalizer = Canonicalizer.getInstance(algorithm)
            
            val canonical = if (inclusiveNamespaces != null && algorithm.contains("EXCL")) {
                // Exclusive C14N with inclusive namespaces
                canonicalizer.canonicalizeSubtree(doc, inclusiveNamespaces)
            } else {
                // Standard C14N
                canonicalizer.canonicalizeSubtree(doc)
            }
            
            UDMString(String(canonical, Charsets.UTF_8))
        } catch (e: InvalidCanonicalizerException) {
            UDMNull
        } catch (e: Exception) {
            UDMNull
        }
    }
    
    /**
     * Parse XML string to DOM Document
     */
    private fun parseXML(xml: UDMValue): Document? {
        return try {
            val xmlString = when (xml) {
                is UDMString -> xml.value
                is UDMObject -> {
                    // Convert UDM Object to XML string
                    // This would require XML serialization logic
                    return null
                }
                else -> return null
            }
            
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val inputStream = ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8))
            builder.parse(inputStream)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Map algorithm name to constant
     */
    private fun mapAlgorithmName(algo: String): String {
        return when (algo.lowercase()) {
            "c14n" -> Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
            "c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS
            "exc-c14n" -> Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            "exc-c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS
            "c14n11" -> Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS
            "c14n11-with-comments" -> Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS
            "physical" -> Canonicalizer.ALGO_ID_C14N_PHYSICAL
            else -> Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
        }
    }
    
    /**
     * Convert byte array to hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
