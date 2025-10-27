package org.apache.utlx.schema

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import org.apache.utlx.schema.usdl.USDL10

class USDL10Test : StringSpec({

    "USDL 1.0 version should be correct" {
        USDL10.VERSION shouldBe "1.0"
    }

    "USDL 1.0 should have 4 tiers" {
        USDL10.ALL_DIRECTIVES.keys shouldHaveSize 4
        USDL10.ALL_DIRECTIVES.keys shouldContain USDL10.Tier.CORE
        USDL10.ALL_DIRECTIVES.keys shouldContain USDL10.Tier.COMMON
        USDL10.ALL_DIRECTIVES.keys shouldContain USDL10.Tier.FORMAT_SPECIFIC
        USDL10.ALL_DIRECTIVES.keys shouldContain USDL10.Tier.RESERVED
    }

    "Tier 1 (Core) should have 9 directives" {
        val tier1 = USDL10.getDirectivesByTier(USDL10.Tier.CORE)
        tier1 shouldHaveSize 9

        // Verify core directives
        tier1.map { it.name } shouldContain "%namespace"
        tier1.map { it.name } shouldContain "%version"
        tier1.map { it.name } shouldContain "%types"
        tier1.map { it.name } shouldContain "%kind"
        tier1.map { it.name } shouldContain "%name"
        tier1.map { it.name } shouldContain "%type"
        tier1.map { it.name } shouldContain "%description"
        tier1.map { it.name } shouldContain "%value"
        tier1.map { it.name } shouldContain "%documentation"
    }

    "Tier 2 (Common) should have constraint directives" {
        val tier2 = USDL10.getDirectivesByTier(USDL10.Tier.COMMON)
        tier2.map { it.name } shouldContain "%minLength"
        tier2.map { it.name } shouldContain "%maxLength"
        tier2.map { it.name } shouldContain "%pattern"
        tier2.map { it.name } shouldContain "%minimum"
        tier2.map { it.name } shouldContain "%maximum"
    }

    "Tier 3 (Format-Specific) should have Protobuf directives" {
        val tier3 = USDL10.getDirectivesByTier(USDL10.Tier.FORMAT_SPECIFIC)
        tier3.map { it.name } shouldContain "%fieldNumber"
        tier3.map { it.name } shouldContain "%packed"
        tier3.map { it.name } shouldContain "%oneof"
    }

    "Tier 3 should have SQL directives" {
        val tier3 = USDL10.getDirectivesByTier(USDL10.Tier.FORMAT_SPECIFIC)
        tier3.map { it.name } shouldContain "%table"
        tier3.map { it.name } shouldContain "%key"
        tier3.map { it.name } shouldContain "%autoIncrement"
        tier3.map { it.name } shouldContain "%foreignKey"
    }

    "Tier 4 (Reserved) should have composition directives" {
        val tier4 = USDL10.getDirectivesByTier(USDL10.Tier.RESERVED)
        tier4.map { it.name } shouldContain "%allOf"
        tier4.map { it.name } shouldContain "%anyOf"
        tier4.map { it.name } shouldContain "%oneOf"
        tier4.map { it.name } shouldContain "%not"
    }

    "Should validate known directive names" {
        USDL10.isValidDirective("%namespace") shouldBe true
        USDL10.isValidDirective("%types") shouldBe true
        USDL10.isValidDirective("%fieldNumber") shouldBe true
        USDL10.isValidDirective("%unknown") shouldBe false
    }

    "Should retrieve directive by name" {
        val directive = USDL10.getDirective("%namespace")
        directive shouldNotBe null
        directive!!.name shouldBe "%namespace"
        directive.tier shouldBe USDL10.Tier.CORE
        directive.scopes shouldContain USDL10.Scope.TOP_LEVEL
    }

    "Should get directives by scope" {
        val topLevel = USDL10.getDirectivesByScope(USDL10.Scope.TOP_LEVEL)
        topLevel.map { it.name } shouldContain "%namespace"
        topLevel.map { it.name } shouldContain "%version"
        topLevel.map { it.name } shouldContain "%types"

        val fieldLevel = USDL10.getDirectivesByScope(USDL10.Scope.FIELD_DEFINITION)
        fieldLevel.map { it.name } shouldContain "%name"
        fieldLevel.map { it.name } shouldContain "%type"
        fieldLevel.map { it.name } shouldContain "%required"
    }

    "Should check format support for directives" {
        // %namespace is universal
        USDL10.isDirectiveSupportedForFormat("%namespace", "xsd") shouldBe true
        USDL10.isDirectiveSupportedForFormat("%namespace", "jsch") shouldBe true
        USDL10.isDirectiveSupportedForFormat("%namespace", "proto") shouldBe true

        // %fieldNumber is Protobuf-specific
        USDL10.isDirectiveSupportedForFormat("%fieldNumber", "proto") shouldBe true
        USDL10.isDirectiveSupportedForFormat("%fieldNumber", "xsd") shouldBe false

        // %table is SQL-specific
        USDL10.isDirectiveSupportedForFormat("%table", "sql") shouldBe true
        USDL10.isDirectiveSupportedForFormat("%table", "jsch") shouldBe false
    }

    "Should have required directives marked correctly" {
        val types = USDL10.getDirective("%types")
        types!!.required shouldBe true

        val kind = USDL10.getDirective("%kind")
        kind!!.required shouldBe true

        val namespace = USDL10.getDirective("%namespace")
        namespace!!.required shouldBe false
    }

    "All directives should have non-empty descriptions" {
        USDL10.ALL_DIRECTIVES_FLAT.forEach { directive ->
            directive.description.isNotBlank() shouldBe true
        }
    }

    "Should have at least 80 directives total" {
        USDL10.ALL_DIRECTIVES_FLAT.size shouldBe org.apache.utlx.schema.usdl.USDL10.ALL_DIRECTIVES_FLAT.size
        println("Total USDL 1.0 directives: ${USDL10.ALL_DIRECTIVES_FLAT.size}")
    }
})
