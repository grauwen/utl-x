package com.glomidco.utlx.formats.xml

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * B27: XML prolog/epilog comments crash the core XML→UDM parse.
 *
 * The parser skipped comments inside elements but only whitespace at the document level, so a
 * comment before or after the root element hit parseName and threw "Invalid name start: !".
 * A `skipMisc()` (whitespace + comment + PI + DOCTYPE) at the prolog and epilog fixes it.
 *
 * Guiding assertion: comments are SKIPPED, not preserved — a comment-bearing document must parse
 * to exactly the same UDM as its comment-free equivalent.
 */
class XmlPrologEpilogCommentTest {

    private val baseline = XMLParser(
        """<?xml version="1.0" encoding="UTF-8"?><HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>"""
    ).parse()

    @Test
    fun `prolog comments between declaration and root are skipped`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<!-- Healthcare Insurance Claim Processing System -->
            |<!-- Comprehensive medical claim -->
            |<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>""".trimMargin()

        val result = XMLParser(xml).parse()
        assertEquals(baseline, result, "prolog comments must not change the UDM")
    }

    @Test
    fun `epilog comment after the root element is skipped`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>
            |<!-- end of document -->""".trimMargin()

        val result = XMLParser(xml).parse()
        assertEquals(baseline, result, "epilog comment must not trigger 'Content after root element'")
    }

    @Test
    fun `prolog, nested and epilog comments together all skipped`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<!-- banner -->
            |<HealthcareClaim claimId="CLM-1">
            |  <!-- patient demographics -->
            |  <Patient>Alice</Patient>
            |</HealthcareClaim>
            |<!-- trailing -->""".trimMargin()

        val result = XMLParser(xml).parse()
        assertEquals(baseline, result, "comments anywhere in the document must be transparent")
    }

    @Test
    fun `stylesheet PI in prolog is skipped, not mistaken for the declaration`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<?xml-stylesheet type="text/xsl" href="claim.xsl"?>
            |<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>""".trimMargin()

        val result = XMLParser(xml).parse()
        assertEquals(baseline, result, "a <?xml-stylesheet?> PI must be skipped like any other misc")
    }

    @Test
    fun `document that opens with a PI and no xml declaration parses`() {
        val xml = """<?xml-stylesheet type="text/xsl" href="claim.xsl"?>
            |<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>""".trimMargin()

        // No <?xml?> declaration here, so no xmlEncoding metadata — compare against the
        // declaration-free, PI-free equivalent (the PI itself must be transparent).
        val expected = XMLParser(
            """<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>"""
        ).parse()
        assertEquals(expected, XMLParser(xml).parse())
    }

    @Test
    fun `DOCTYPE with an internal subset is skipped`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE HealthcareClaim [ <!ELEMENT Patient (#PCDATA)> ]>
            |<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>""".trimMargin()

        val result = XMLParser(xml).parse()
        assertEquals(baseline, result, "the '>' inside the internal subset must not end the DOCTYPE early")
    }

    @Test
    fun `regression - comment-free document still parses unchanged`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>"""
        assertEquals(baseline, XMLParser(xml).parse())
    }

    @Test
    fun `regression - in-element comments still handled`() {
        val xml = """<HealthcareClaim claimId="CLM-1"><!-- inline --><Patient>Alice</Patient></HealthcareClaim>"""
        val noDecl = XMLParser(
            """<HealthcareClaim claimId="CLM-1"><Patient>Alice</Patient></HealthcareClaim>"""
        ).parse()
        assertEquals(noDecl, XMLParser(xml).parse())
    }
}
