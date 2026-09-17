package com.glomidco.utlx.formats.xml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B28: a space inside an XML element name (e.g. `<Purchase Order>`) used to fail with the cryptic
 * `Expected '='` — the message pointed at attribute syntax when the real cause is the space. The
 * parser now names the likely mistake and the correction.
 *
 * The assertions target the error *message* (the DX payload), not the exception type, so they stay
 * valid whether the space-branch throws IllegalStateException (current, via `error(...)`) or is
 * later changed to XMLParseException for consistency with the rest of the parser.
 */
class XmlElementNameTest {

    @Test
    fun `space in element name yields a message naming the cause and the fix`() {
        val ex = assertThrows(Exception::class.java) {
            XMLParser("<Purchase Order>5</Purchase Order>").parse()
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("element names cannot contain spaces"),
            "should name the real cause; got: $msg")
        assertTrue(msg.contains("Purchase") && msg.contains("Order"),
            "should quote the split tokens; got: $msg")
        assertTrue(msg.contains("PurchaseOrder"),
            "should suggest the corrected element name; got: $msg")
    }

    @Test
    fun `regression - a genuine missing-equals still gets the generic (element-qualified) message`() {
        // `<a b c>`: after 'b' the next char is 'c' (not '>'), so the space-branch must NOT fire —
        // it falls through to the generic consume('=') failure, now qualified with element/attr names.
        val ex = assertThrows(Exception::class.java) {
            XMLParser("<a b c>x</a>").parse()
        }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("Expected '='"), "should be the generic missing-= message; got: $msg")
        assertFalse(msg.contains("cannot contain spaces"),
            "the space-branch must not swallow this case; got: $msg")
    }

    @Test
    fun `regression - well-formed element with attributes parses unchanged`() {
        val udm = XMLParser("""<a b="x" c="y">text</a>""").parse()
        assertTrue(udm.toString().contains("text"), "well-formed XML must still parse: $udm")
    }

    @Test
    fun `regression - element with no attributes parses unchanged`() {
        val udm = XMLParser("<Order><Customer>Alice</Customer></Order>").parse()
        assertTrue(udm.toString().contains("Alice"))
    }
}
