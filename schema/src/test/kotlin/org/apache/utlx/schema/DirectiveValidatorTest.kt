package org.apache.utlx.schema

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.collections.shouldHaveSize
import org.apache.utlx.core.udm.UDM
import org.apache.utlx.schema.usdl.DirectiveValidator

class DirectiveValidatorTest : StringSpec({

    "Should validate simple valid USDL schema" {
        val schema = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("http://example.com"),
                "%version" to UDM.Scalar("1.0"),
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%documentation" to UDM.Scalar("Customer information"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(
                                            properties = mapOf(
                                                "%name" to UDM.Scalar("id"),
                                                "%type" to UDM.Scalar("string"),
                                                "%required" to UDM.Scalar(true)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors shouldHaveSize 0
    }

    "Should error on missing %types" {
        val schema = UDM.Object(
            properties = mapOf(
                "%namespace" to UDM.Scalar("http://example.com")
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors shouldHaveSize 1
        errors[0].directive shouldBe "%types"
    }

    "Should error on missing %kind in type definition" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%fields" to UDM.Array(emptyList())
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors.any { it.directive == "%kind" } shouldBe true
    }

    "Should error on missing %name in field" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(
                                            properties = mapOf(
                                                "%type" to UDM.Scalar("string")
                                                // Missing %name!
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors.any { it.directive == "%name" } shouldBe true
    }

    "Should detect typo in directive name with suggestion" {
        val schema = UDM.Object(
            properties = mapOf(
                "%namepsace" to UDM.Scalar("http://example.com"),  // Typo!
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Test" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure")
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        val typoError = errors.find { it.directive == "%namepsace" }
        typoError shouldBe org.apache.utlx.schema.usdl.DirectiveValidator.ValidationResult.Error(
            message = typoError!!.message,
            directive = "%namepsace",
            suggestion = "Did you mean '%namespace'?"
        )
    }

    "Should warn on unsupported directive for target format" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Message" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(
                                            properties = mapOf(
                                                "%name" to UDM.Scalar("id"),
                                                "%type" to UDM.Scalar("int32"),
                                                "%fieldNumber" to UDM.Scalar(1)  // Protobuf-specific
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // Validate for XSD target - should warn about %fieldNumber
        val results = DirectiveValidator.quickValidate(schema, targetFormat = "xsd")
        val warnings = results.filterIsInstance<DirectiveValidator.ValidationResult.Warning>()
        warnings.any { it.directive == "%fieldNumber" } shouldBe true
    }

    "Should error on reserved directive in strict mode" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%allOf" to UDM.Array(emptyList())  // Tier 4 - Reserved
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors.any { it.directive == "%allOf" } shouldBe true
    }

    "Should warn on reserved directive in lenient mode" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%allOf" to UDM.Array(emptyList())  // Tier 4 - Reserved
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.lenientValidate(schema)
        val warnings = results.filterIsInstance<DirectiveValidator.ValidationResult.Warning>()
        warnings.any { it.directive == "%allOf" } shouldBe true
    }

    "Should validate invalid %kind value" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("invalid_kind")  // Invalid!
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors.any { it.message.contains("Invalid %kind value") } shouldBe true
    }

    "Should validate constraints directives in proper scope" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Customer" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("structure"),
                                "%fields" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(
                                            properties = mapOf(
                                                "%name" to UDM.Scalar("email"),
                                                "%type" to UDM.Scalar("string"),
                                                "%constraints" to UDM.Object(
                                                    properties = mapOf(
                                                        "%format" to UDM.Scalar("email"),
                                                        "%maxLength" to UDM.Scalar(255)
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors shouldHaveSize 0
    }

    "Should validate enumeration values" {
        val schema = UDM.Object(
            properties = mapOf(
                "%types" to UDM.Object(
                    properties = mapOf(
                        "Status" to UDM.Object(
                            properties = mapOf(
                                "%kind" to UDM.Scalar("enumeration"),
                                "%values" to UDM.Array(
                                    elements = listOf(
                                        UDM.Object(
                                            properties = mapOf(
                                                "%value" to UDM.Scalar("active"),
                                                "%description" to UDM.Scalar("Active status")
                                            )
                                        ),
                                        UDM.Scalar("inactive")  // Simple value is also OK
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val results = DirectiveValidator.quickValidate(schema)
        val errors = results.filterIsInstance<DirectiveValidator.ValidationResult.Error>()
        errors shouldHaveSize 0
    }
})
