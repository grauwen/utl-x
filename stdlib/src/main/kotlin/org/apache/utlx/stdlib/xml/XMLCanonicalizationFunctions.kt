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
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

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
    
    @UTLXFunction(
        description = "Canonicalizes XML using Canonical XML 1.0 (without comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "c14n(...) => result",
        notes = "Algorithm: http://www.w3.org/TR/2001/REC-xml-c14n-20010315\nTransforms XML into a canonical form by:\n- Normalizing whitespace in attribute values\n- Sorting namespace declarations and attributes\n- Removing XML/DOCTYPE declarations\n- Normalizing empty elements\n- Removing comments (in this variant)\nExample:\n```\nc14n(xmlDoc) // Canonical form without comments\n```",
        tags = ["cleanup", "xml"],
        since = "1.0"
    )
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
    fun c14n(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "c14n expects 1 argument, got ${args.size}. " +
                "Hint: Provide an XML string or document to canonicalize."
            )
        }
        val xml = args[0]
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS)
    }
    
    @UTLXFunction(
        description = "Canonicalizes XML using Canonical XML 1.0 (with comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "c14nWithComments(...) => result",
        notes = "Algorithm: http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments\nSame as c14n() but preserves XML comments in canonical form.\nExample:\n```\nc14nWithComments(xmlDoc) // Preserves comments\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nWithComments(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "c14nWithComments expects 1 argument, got ${args.size}. " +
                "Hint: Provide an XML string or document to canonicalize."
            )
        }
        val xml = args[0]
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS)
    }
    
    // ============================================
    // EXC-C14N - EXCLUSIVE CANONICAL XML
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes XML using Exclusive Canonical XML (without comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "excC14n(...) => result",
        notes = "Algorithm: http://www.w3.org/2001/10/xml-exc-c14n#\nExclusive canonicalization only includes namespace declarations\nthat are actually used, making it ideal for XML signatures in\nSOAP envelopes where the document is embedded in other XML.\nExample:\n```\nexcC14n(xmlDoc) // Exclusive canonical form\nexcC14n(xmlDoc, \"ds xenc\") // Include ds and xenc namespaces\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun excC14n(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "excC14n expects at least 1 argument, got ${args.size}. " +
                "Hint: Provide an XML string or document to canonicalize."
            )
        }
        val xml = args[0]
        val inclusiveNamespaces = if (args.size > 1) args[1] else UDM.Scalar(null)
        val inclusivePrefixes = (inclusiveNamespaces as? UDM.Scalar)?.value?.toString()
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS, inclusivePrefixes)
    }
    
    @UTLXFunction(
        description = "Canonicalizes XML using Exclusive Canonical XML (with comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "excC14nWithComments(...) => result",
        notes = "Algorithm: http://www.w3.org/2001/10/xml-exc-c14n#WithComments\nSame as excC14n() but preserves comments.\nExample:\n```\nexcC14nWithComments(xmlDoc, \"ds\")\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun excC14nWithComments(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("excC14nWithComments expects at least 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val inclusiveNamespaces = if (args.size > 1) args[1] else UDM.Scalar(null)
        val inclusivePrefixes = (inclusiveNamespaces as? UDM.Scalar)?.value?.toString()
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS, inclusivePrefixes)
    }
    
    // ============================================
    // C14N 1.1 - CANONICAL XML 1.1
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes XML using Canonical XML 1.1 (without comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "c14n11(...) => result",
        notes = "Algorithm: http://www.w3.org/2006/12/xml-c14n11\nC14N 1.1 improves upon 1.0 by:\n- Better handling of xml:id attributes\n- Improved namespace handling\n- Better support for XML 1.1 features\nExample:\n```\nc14n11(xmlDoc) // Canonical XML 1.1\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14n11(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("c14n11 expects 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS)
    }
    
    @UTLXFunction(
        description = "Canonicalizes XML using Canonical XML 1.1 (with comments)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "c14n11WithComments(...) => result",
        notes = "Algorithm: http://www.w3.org/2006/12/xml-c14n11#WithComments\nExample:\n```\nc14n11WithComments(xmlDoc)\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14n11WithComments(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("c14n11WithComments expects 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS)
    }
    
    // ============================================
    // PHYSICAL CANONICAL XML (NON-STANDARD)
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes XML using Physical Canonical XML",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "algorithm: Algorithm value"
        ],
        returns = "Result of the operation",
        example = "c14nPhysical(...) => result",
        notes = "Physical canonicalization preserves the physical structure\nof the XML document, including insignificant whitespace.\nThis is a non-standard algorithm useful for debugging\nand preserving the exact structure of source documents.\nExample:\n```\nc14nPhysical(xmlDoc) // Preserves physical structure\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nPhysical(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("c14nPhysical expects 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        return canonicalize(xml, Canonicalizer.ALGO_ID_C14N_PHYSICAL)
    }
    
    // ============================================
    // GENERIC CANONICALIZATION
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes XML using specified algorithm",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "xml: Xml value",
        "algorithm: Algorithm value"
        ],
        returns = "Result of the operation",
        example = "canonicalizeWithAlgorithm(...) => result",
        notes = "Supported algorithms:\n- \"c14n\" - Canonical XML 1.0 without comments\n- \"c14n-with-comments\" - Canonical XML 1.0 with comments\n- \"exc-c14n\" - Exclusive C14N without comments\n- \"exc-c14n-with-comments\" - Exclusive C14N with comments\n- \"c14n11\" - Canonical XML 1.1 without comments\n- \"c14n11-with-comments\" - Canonical XML 1.1 with comments\n- \"physical\" - Physical C14N\nExample:\n```\ncanonicalize(xmlDoc, \"exc-c14n\", \"ds xenc\")\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun canonicalizeWithAlgorithm(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("canonicalizeWithAlgorithm expects at least 2 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val algorithm = args[1]
        val inclusiveNamespaces = if (args.size > 2) args[2] else UDM.Scalar(null)
        val algo = (algorithm as? UDM.Scalar)?.value ?: return UDM.Scalar(null)
        
        val algoId = when (algo.toString().lowercase()) {
            "c14n" -> Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS
            "c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_WITH_COMMENTS
            "exc-c14n" -> Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS
            "exc-c14n-with-comments" -> Canonicalizer.ALGO_ID_C14N_EXCL_WITH_COMMENTS
            "c14n11" -> Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS
            "c14n11-with-comments" -> Canonicalizer.ALGO_ID_C14N11_WITH_COMMENTS
            "physical" -> Canonicalizer.ALGO_ID_C14N_PHYSICAL
            else -> return UDM.Scalar(null)
        }
        
        val inclusivePrefixes = (inclusiveNamespaces as? UDM.Scalar)?.value?.toString()
        return canonicalize(xml, algoId, inclusivePrefixes)
    }
    
    // ============================================
    // XPATH SUBSET CANONICALIZATION
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes a subset of XML selected by XPath",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "xpathExpr: Xpathexpr value"
        ],
        returns = "Result of the operation",
        example = "c14nSubset(...) => result",
        notes = "Useful for signing or hashing specific parts of an XML document.\nExample:\n```\n// Canonicalize only the body element\nc14nSubset(soapEnvelope, \"//soap:Body\", \"exc-c14n\")\n// Canonicalize specific elements\nc14nSubset(doc, \"/root/data[@type='sensitive']\")\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nSubset(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("c14nSubset expects at least 2 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val xpathExpr = args[1]
        val algorithm = if (args.size > 2) args[2] else UDM.Scalar("c14n")
        val xpath = (xpathExpr as? UDM.Scalar)?.value?.toString() ?: return UDM.Scalar(null)
        val algo = (algorithm as? UDM.Scalar)?.value?.toString() ?: "c14n"
        
        return try {
            val doc = parseXML(xml) ?: return UDM.Scalar(null)
            
            // Select nodes using XPath
            val xpathFactory = javax.xml.xpath.XPathFactory.newInstance()
            val xpathObj = xpathFactory.newXPath()
            val nodeList = xpathObj.evaluate(xpath, doc, javax.xml.xpath.XPathConstants.NODESET) as NodeList
            
            if (nodeList.length == 0) {
                return UDM.Scalar("")
            }
            
            // Canonicalize each selected node
            val algoId = mapAlgorithmName(algo)
            val canonicalizer = Canonicalizer.getInstance(algoId)
            val output = ByteArrayOutputStream()
            
            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                canonicalizer.canonicalizeSubtree(node, output)
            }
            
            UDM.Scalar(output.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            UDM.Scalar(null)
        }
    }
    
    // ============================================
    // HASH AND SIGN HELPERS
    // ============================================
    
    @UTLXFunction(
        description = "Canonicalizes XML and computes its hash",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "c14nHash(...) => result",
        notes = "Useful for XML comparison, caching, and digital signatures.\nExample:\n```\n// Compute SHA-256 hash of canonical XML\nc14nHash(xmlDoc, \"sha256\", \"exc-c14n\")\n// Quick hash for comparison\nc14nHash(doc1, \"sha256\") == c14nHash(doc2, \"sha256\")\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nHash(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("c14nHash expects at least 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val hashAlgorithm = if (args.size > 1) args[1] else UDM.Scalar("sha256")
        val c14nAlgorithm = if (args.size > 2) args[2] else UDM.Scalar("c14n")
        // First canonicalize
        val canonical = canonicalizeWithAlgorithm(listOf(xml, c14nAlgorithm))
        if (canonical == UDM.Scalar(null)) return UDM.Scalar(null)
        
        // Then hash
        val algo = (hashAlgorithm as? UDM.Scalar)?.value?.toString()?.lowercase() ?: "sha256"
        
        return when (algo) {
            "md5" -> {
                val digest = java.security.MessageDigest.getInstance("MD5")
                val bytes = digest.digest((canonical as UDM.Scalar).value?.toString()?.toByteArray() ?: ByteArray(0))
                UDM.Scalar(bytesToHex(bytes))
            }
            "sha1" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-1")
                val bytes = digest.digest((canonical as UDM.Scalar).value?.toString()?.toByteArray() ?: ByteArray(0))
                UDM.Scalar(bytesToHex(bytes))
            }
            "sha256" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val bytes = digest.digest((canonical as UDM.Scalar).value?.toString()?.toByteArray() ?: ByteArray(0))
                UDM.Scalar(bytesToHex(bytes))
            }
            "sha512" -> {
                val digest = java.security.MessageDigest.getInstance("SHA-512")
                val bytes = digest.digest((canonical as UDM.Scalar).value?.toString()?.toByteArray() ?: ByteArray(0))
                UDM.Scalar(bytesToHex(bytes))
            }
            else -> UDM.Scalar(null)
        }
    }
    
    @UTLXFunction(
        description = "Compares two XML documents for canonical equivalence",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "xml1: Xml1 value",
        "xml2: Xml2 value"
        ],
        returns = "true if the canonical forms are byte-for-byte identical,",
        example = "c14nEquals(...) => result",
        notes = "Returns true if the canonical forms are byte-for-byte identical,\neven if the original documents differ in formatting, attribute order,\nnamespace prefixes, etc.\nExample:\n```\n// These are canonically equivalent:\nlet xml1 = '<root a=\"1\" b=\"2\"/>'\nlet xml2 = '<root b=\"2\" a=\"1\"></root>'\nc14nEquals(xml1, xml2) // true\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nEquals(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("c14nEquals expects at least 2 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml1 = args[0]
        val xml2 = args[1]
        val algorithm = if (args.size > 2) args[2] else UDM.Scalar("c14n")
        val canonical1 = canonicalizeWithAlgorithm(listOf(xml1, algorithm))
        val canonical2 = canonicalizeWithAlgorithm(listOf(xml2, algorithm))
        
        if (canonical1 == UDM.Scalar(null) || canonical2 == UDM.Scalar(null)) {
            return UDM.Scalar(false)
        }
        
        val str1 = (canonical1 as UDM.Scalar).value
        val str2 = (canonical2 as UDM.Scalar).value
        
        return UDM.Scalar(str1 == str2)
    }
    
    @UTLXFunction(
        description = "Creates a normalized fingerprint of XML for deduplication",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "a short hash that can be used to detect duplicate",
        example = "c14nFingerprint(...) => result",
        notes = "Returns a short hash that can be used to detect duplicate\nXML documents regardless of formatting differences.\nExample:\n```\nlet fingerprint = c14nFingerprint(xmlDoc)\n// Use for caching or deduplication\ncache.put(fingerprint, transformedData)\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun c14nFingerprint(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("c14nFingerprint expects 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val hash = c14nHash(listOf(xml, UDM.Scalar("sha256"), UDM.Scalar("c14n")))
        if (hash == UDM.Scalar(null)) return UDM.Scalar(null)
        
        // Return first 16 characters of hash as fingerprint
        val fullHash = (hash as UDM.Scalar).value?.toString() ?: ""
        return UDM.Scalar(fullHash.take(16))
    }
    
    // ============================================
    // DIGITAL SIGNATURE PREPARATION
    // ============================================
    
    @UTLXFunction(
        description = "Prepares XML for digital signature (XMLDSig)",
        minArgs = 1,
        maxArgs = 1,
        category = "XML",
        parameters = [
            "xml: Xml value"
        ],
        returns = "Result of the operation",
        example = "prepareForSignature(...) => result",
        notes = "Canonicalizes XML using Exclusive C14N (the recommended algorithm\nfor XML signatures) and returns both the canonical form and its hash.\nExample:\n```\nlet prepared = prepareForSignature(xmlDoc)\n// prepared.canonical - Canonical XML\n// prepared.digest - SHA-256 digest\n// Use digest with HMAC or RSA signing\nlet signature = hmacSHA256(prepared.digest, secretKey)\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun prepareForSignature(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException("prepareForSignature expects at least 1 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val digestAlgorithm = if (args.size > 1) args[1] else UDM.Scalar("sha256")
        // Canonicalize using Exc-C14N (recommended for signatures)
        val canonical = excC14n(listOf(xml))
        if (canonical == UDM.Scalar(null)) return UDM.Scalar(null)
        
        // Compute digest
        val digest = c14nHash(listOf(xml, digestAlgorithm, UDM.Scalar("exc-c14n")))
        if (digest == UDM.Scalar(null)) return UDM.Scalar(null)
        
        return UDM.Object(mapOf(
            "canonical" to canonical,
            "digest" to digest,
            "algorithm" to UDM.Scalar("exc-c14n"),
            "digestAlgorithm" to digestAlgorithm
        ))
    }
    
    @UTLXFunction(
        description = "Validates that XML digest matches expected value",
        minArgs = 2,
        maxArgs = 2,
        category = "XML",
        parameters = [
            "array: Input array to process",
        "expectedDigest: Expecteddigest value"
        ],
        returns = "Result of the operation",
        example = "validateDigest(...) => result",
        notes = "Used to verify XML digital signatures by comparing the digest\nof canonical XML against a known reference value.\nExample:\n```\n// Verify signature\nlet valid = validateDigest(\nreceivedXML,\nsignatureDigest,\n\"sha256\",\n\"exc-c14n\"\n)\n```",
        tags = ["xml"],
        since = "1.0"
    )
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
    fun validateDigest(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException("validateDigest expects at least 2 argument(s), got ${args.size}. Hint: Check the function signature and provide the correct number of arguments")
        }
        val xml = args[0]
        val expectedDigest = args[1]
        val digestAlgorithm = if (args.size > 2) args[2] else UDM.Scalar("sha256")
        val c14nAlgorithm = if (args.size > 3) args[3] else UDM.Scalar("exc-c14n")
        val expected = (expectedDigest as? UDM.Scalar)?.value?.toString() ?: return UDM.Scalar(false)
        
        val actualDigest = c14nHash(listOf(xml, digestAlgorithm, c14nAlgorithm))
        if (actualDigest == UDM.Scalar(null)) return UDM.Scalar(false)
        
        val actual = (actualDigest as UDM.Scalar).value?.toString() ?: ""
        
        // Case-insensitive comparison
        return UDM.Scalar(actual.lowercase() == expected.lowercase())
    }
    
    // ============================================
    // INTERNAL HELPERS
    // ============================================
    
    /**
     * Internal canonicalization implementation
     */
    private fun canonicalize(
        xml: UDM, 
        algorithm: String,
        inclusiveNamespaces: String? = null
    ): UDM {
        return try {
            val doc = parseXML(xml) ?: return UDM.Scalar(null)
            
            val canonicalizer = Canonicalizer.getInstance(algorithm)
            
            val output = ByteArrayOutputStream()
            
            if (inclusiveNamespaces != null && algorithm.contains("EXCL")) {
                // Exclusive C14N with inclusive namespaces
                canonicalizer.canonicalizeSubtree(doc, inclusiveNamespaces, output)
            } else {
                // Standard C14N
                canonicalizer.canonicalizeSubtree(doc, output)
            }
            
            UDM.Scalar(output.toString(Charsets.UTF_8))
        } catch (e: InvalidCanonicalizerException) {
            UDM.Scalar(null)
        } catch (e: Exception) {
            UDM.Scalar(null)
        }
    }
    
    /**
     * Parse XML string to DOM Document
     */
    private fun parseXML(xml: UDM): Document? {
        return try {
            val xmlString = when (xml) {
                is UDM.Scalar -> xml.value?.toString() ?: ""
                is UDM.Object -> {
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
