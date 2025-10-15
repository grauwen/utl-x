// stdlib/src/test/kotlin/org/apache/utlx/stdlib/logical/LogicalFunctionsTest.kt
package org.apache.utlx.stdlib.logical

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Comprehensive test suite for Logical functions.
 * 
 * Tests cover:
 * - Basic logical operations (not, and, or, xor)
 * - Advanced logical gates (nand, nor, xnor, implies)
 * - Array boolean operations (all, any, none)
 * - Truth tables validation
 * - Real-world logical scenarios
 */
class LogicalFunctionsTest {

    // ==================== NOT Tests ====================
    
    @Test
    fun `test not - true becomes false`() {
        val value = UDM.Scalar(true)
        
        val result = LogicalFunctions.not(listOf(value))
        val notValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(notValue)
    }
    
    @Test
    fun `test not - false becomes true`() {
        val value = UDM.Scalar(false)
        
        val result = LogicalFunctions.not(listOf(value))
        val notValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(notValue)
    }
    
    @Test
    fun `test not - double negation`() {
        val value = UDM.Scalar(true)
        
        val firstNot = LogicalFunctions.not(listOf(value))
        val secondNot = LogicalFunctions.not(listOf(firstNot))
        val finalValue = (secondNot as UDM.Scalar).value as Boolean
        
        assertTrue(finalValue, "not(not(true)) should be true")
    }

    // ==================== AND Tests ====================
    
    @Test
    fun `test and - true AND true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.and(listOf(a, b))
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(andValue)
    }
    
    @Test
    fun `test and - true AND false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.and(listOf(a, b))
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(andValue)
    }
    
    @Test
    fun `test and - false AND true`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.and(listOf(a, b))
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(andValue)
    }
    
    @Test
    fun `test and - false AND false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.and(listOf(a, b))
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(andValue)
    }
    
    @Test
    fun `test and - multiple values all true`() {
        val values = listOf(
            UDM.Scalar(true),
            UDM.Scalar(true),
            UDM.Scalar(true)
        )
        
        val result = LogicalFunctions.and(values)
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(andValue, "all true values should result in true")
    }
    
    @Test
    fun `test and - multiple values with one false`() {
        val values = listOf(
            UDM.Scalar(true),
            UDM.Scalar(false),
            UDM.Scalar(true)
        )
        
        val result = LogicalFunctions.and(values)
        val andValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(andValue, "one false value should result in false")
    }

    // ==================== OR Tests ====================
    
    @Test
    fun `test or - true OR true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.or(listOf(a, b))
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(orValue)
    }
    
    @Test
    fun `test or - true OR false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.or(listOf(a, b))
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(orValue)
    }
    
    @Test
    fun `test or - false OR true`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.or(listOf(a, b))
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(orValue)
    }
    
    @Test
    fun `test or - false OR false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.or(listOf(a, b))
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(orValue)
    }
    
    @Test
    fun `test or - multiple values all false`() {
        val values = listOf(
            UDM.Scalar(false),
            UDM.Scalar(false),
            UDM.Scalar(false)
        )
        
        val result = LogicalFunctions.or(values)
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(orValue, "all false values should result in false")
    }
    
    @Test
    fun `test or - multiple values with one true`() {
        val values = listOf(
            UDM.Scalar(false),
            UDM.Scalar(true),
            UDM.Scalar(false)
        )
        
        val result = LogicalFunctions.or(values)
        val orValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(orValue, "one true value should result in true")
    }

    // ==================== XOR Tests ====================
    
    @Test
    fun `test xor - true XOR true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.xor(listOf(a, b))
        val xorValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(xorValue, "same values should result in false")
    }
    
    @Test
    fun `test xor - true XOR false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.xor(listOf(a, b))
        val xorValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(xorValue, "different values should result in true")
    }
    
    @Test
    fun `test xor - false XOR true`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.xor(listOf(a, b))
        val xorValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(xorValue, "different values should result in true")
    }
    
    @Test
    fun `test xor - false XOR false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.xor(listOf(a, b))
        val xorValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(xorValue, "same values should result in false")
    }

    // ==================== NAND Tests ====================
    
    @Test
    fun `test nand - true NAND true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.nand(listOf(a, b))
        val nandValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(nandValue, "NAND is NOT AND")
    }
    
    @Test
    fun `test nand - true NAND false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.nand(listOf(a, b))
        val nandValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(nandValue)
    }
    
    @Test
    fun `test nand - false NAND false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.nand(listOf(a, b))
        val nandValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(nandValue)
    }
    
    @Test
    fun `test nand - equivalence to not and`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val nandResult = LogicalFunctions.nand(listOf(a, b))
        val andResult = LogicalFunctions.and(listOf(a, b))
        val notAndResult = LogicalFunctions.not(listOf(andResult))
        
        assertEquals(
            (nandResult as UDM.Scalar).value,
            (notAndResult as UDM.Scalar).value,
            "NAND should equal NOT(AND)"
        )
    }

    // ==================== NOR Tests ====================
    
    @Test
    fun `test nor - true NOR true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.nor(listOf(a, b))
        val norValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(norValue)
    }
    
    @Test
    fun `test nor - true NOR false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.nor(listOf(a, b))
        val norValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(norValue)
    }
    
    @Test
    fun `test nor - false NOR false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.nor(listOf(a, b))
        val norValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(norValue, "NOR is true only when both are false")
    }
    
    @Test
    fun `test nor - equivalence to not or`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(true)
        
        val norResult = LogicalFunctions.nor(listOf(a, b))
        val orResult = LogicalFunctions.or(listOf(a, b))
        val notOrResult = LogicalFunctions.not(listOf(orResult))
        
        assertEquals(
            (norResult as UDM.Scalar).value,
            (notOrResult as UDM.Scalar).value,
            "NOR should equal NOT(OR)"
        )
    }

    // ==================== XNOR Tests ====================
    
    @Test
    fun `test xnor - true XNOR true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.xnor(listOf(a, b))
        val xnorValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(xnorValue, "XNOR is true when values are the same")
    }
    
    @Test
    fun `test xnor - true XNOR false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.xnor(listOf(a, b))
        val xnorValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(xnorValue, "XNOR is false when values differ")
    }
    
    @Test
    fun `test xnor - false XNOR false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.xnor(listOf(a, b))
        val xnorValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(xnorValue, "XNOR is true when values are the same")
    }
    
    @Test
    fun `test xnor - equivalence to not xor`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val xnorResult = LogicalFunctions.xnor(listOf(a, b))
        val xorResult = LogicalFunctions.xor(listOf(a, b))
        val notXorResult = LogicalFunctions.not(listOf(xorResult))
        
        assertEquals(
            (xnorResult as UDM.Scalar).value,
            (notXorResult as UDM.Scalar).value,
            "XNOR should equal NOT(XOR)"
        )
    }

    // ==================== IMPLIES Tests ====================
    
    @Test
    fun `test implies - true IMPLIES true`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.implies(listOf(a, b))
        val impliesValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(impliesValue)
    }
    
    @Test
    fun `test implies - true IMPLIES false`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.implies(listOf(a, b))
        val impliesValue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(impliesValue, "true implies false is false")
    }
    
    @Test
    fun `test implies - false IMPLIES true`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(true)
        
        val result = LogicalFunctions.implies(listOf(a, b))
        val impliesValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(impliesValue, "false implies anything is true")
    }
    
    @Test
    fun `test implies - false IMPLIES false`() {
        val a = UDM.Scalar(false)
        val b = UDM.Scalar(false)
        
        val result = LogicalFunctions.implies(listOf(a, b))
        val impliesValue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(impliesValue, "false implies anything is true")
    }

    // ==================== ALL Tests (Array Operations) ====================
    
    @Test
    fun `test all - all true`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(true),
            UDM.Scalar(true),
            UDM.Scalar(true)
        ))
        
        val result = LogicalFunctions.all(listOf(array))
        val allTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(allTrue)
    }
    
    @Test
    fun `test all - one false`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(true),
            UDM.Scalar(false),
            UDM.Scalar(true)
        ))
        
        val result = LogicalFunctions.all(listOf(array))
        val allTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(allTrue)
    }
    
    @Test
    fun `test all - all false`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(false),
            UDM.Scalar(false)
        ))
        
        val result = LogicalFunctions.all(listOf(array))
        val allTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(allTrue)
    }
    
    @Test
    fun `test all - empty array`() {
        val array = UDM.Array(emptyList())
        
        val result = LogicalFunctions.all(listOf(array))
        val allTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(allTrue, "all() on empty array should be true (vacuous truth)")
    }

    // ==================== ANY Tests (Array Operations) ====================
    
    @Test
    fun `test any - all true`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(true),
            UDM.Scalar(true),
            UDM.Scalar(true)
        ))
        
        val result = LogicalFunctions.any(listOf(array))
        val anyTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(anyTrue)
    }
    
    @Test
    fun `test any - one true`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(true),
            UDM.Scalar(false)
        ))
        
        val result = LogicalFunctions.any(listOf(array))
        val anyTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(anyTrue)
    }
    
    @Test
    fun `test any - all false`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(false),
            UDM.Scalar(false)
        ))
        
        val result = LogicalFunctions.any(listOf(array))
        val anyTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(anyTrue)
    }
    
    @Test
    fun `test any - empty array`() {
        val array = UDM.Array(emptyList())
        
        val result = LogicalFunctions.any(listOf(array))
        val anyTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(anyTrue, "any() on empty array should be false")
    }

    // ==================== NONE Tests (Array Operations) ====================
    
    @Test
    fun `test none - all false`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(false),
            UDM.Scalar(false)
        ))
        
        val result = LogicalFunctions.none(listOf(array))
        val noneTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(noneTrue, "none true when all are false")
    }
    
    @Test
    fun `test none - one true`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(true),
            UDM.Scalar(false)
        ))
        
        val result = LogicalFunctions.none(listOf(array))
        val noneTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(noneTrue, "none false when at least one is true")
    }
    
    @Test
    fun `test none - all true`() {
        val array = UDM.Array(listOf(
            UDM.Scalar(true),
            UDM.Scalar(true),
            UDM.Scalar(true)
        ))
        
        val result = LogicalFunctions.none(listOf(array))
        val noneTrue = (result as UDM.Scalar).value as Boolean
        
        assertFalse(noneTrue)
    }
    
    @Test
    fun `test none - empty array`() {
        val array = UDM.Array(emptyList())
        
        val result = LogicalFunctions.none(listOf(array))
        val noneTrue = (result as UDM.Scalar).value as Boolean
        
        assertTrue(noneTrue, "none() on empty array should be true")
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - access control`() {
        // User must be authenticated AND authorized
        val isAuthenticated = UDM.Scalar(true)
        val isAuthorized = UDM.Scalar(true)
        
        val hasAccess = LogicalFunctions.and(listOf(isAuthenticated, isAuthorized))
        assertTrue((hasAccess as UDM.Scalar).value as Boolean)
        
        // Deny access if either is false
        val isNotAuthorized = UDM.Scalar(false)
        val noAccess = LogicalFunctions.and(listOf(isAuthenticated, isNotAuthorized))
        assertFalse((noAccess as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - validation rules`() {
        // Form is valid if all fields are valid
        val fieldValidations = UDM.Array(listOf(
            UDM.Scalar(true),   // name is valid
            UDM.Scalar(true),   // email is valid
            UDM.Scalar(true),   // age is valid
            UDM.Scalar(true)    // address is valid
        ))
        
        val isFormValid = LogicalFunctions.all(listOf(fieldValidations))
        assertTrue((isFormValid as UDM.Scalar).value as Boolean)
        
        // Form is invalid if any field is invalid
        val invalidForm = UDM.Array(listOf(
            UDM.Scalar(true),
            UDM.Scalar(false),  // email is invalid
            UDM.Scalar(true),
            UDM.Scalar(true)
        ))
        
        val isInvalidForm = LogicalFunctions.all(listOf(invalidForm))
        assertFalse((isInvalidForm as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - feature flags`() {
        // Show feature if any flag is enabled
        val flags = UDM.Array(listOf(
            UDM.Scalar(false),  // beta_flag
            UDM.Scalar(true),   // admin_flag
            UDM.Scalar(false)   // test_flag
        ))
        
        val showFeature = LogicalFunctions.any(listOf(flags))
        assertTrue((showFeature as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - error checking`() {
        // No errors means system is healthy
        val errors = UDM.Array(listOf(
            UDM.Scalar(false),  // no database error
            UDM.Scalar(false),  // no network error
            UDM.Scalar(false)   // no disk error
        ))
        
        val systemHealthy = LogicalFunctions.none(listOf(errors))
        assertTrue((systemHealthy as UDM.Scalar).value as Boolean)
        
        // System unhealthy if any error exists
        val hasErrors = UDM.Array(listOf(
            UDM.Scalar(false),
            UDM.Scalar(true),   // network error!
            UDM.Scalar(false)
        ))
        
        val systemUnhealthy = LogicalFunctions.none(listOf(hasErrors))
        assertFalse((systemUnhealthy as UDM.Scalar).value as Boolean)
    }
    
    @Test
    fun `test real-world - conditional workflow`() {
        // Process order if: (is_paid AND is_in_stock) OR is_preorder
        val isPaid = UDM.Scalar(true)
        val isInStock = UDM.Scalar(false)
        val isPreorder = UDM.Scalar(true)
        
        val paidAndInStock = LogicalFunctions.and(listOf(isPaid, isInStock))
        val shouldProcess = LogicalFunctions.or(listOf(paidAndInStock, isPreorder))
        
        assertTrue((shouldProcess as UDM.Scalar).value as Boolean,
            "Should process because it's a preorder")
    }

    // ==================== Truth Tables ====================
    
    @Test
    fun `test truth table - AND complete`() {
        data class AndCase(val a: Boolean, val b: Boolean, val expected: Boolean)
        val cases = listOf(
            AndCase(true, true, true),
            AndCase(true, false, false),
            AndCase(false, true, false),
            AndCase(false, false, false)
        )
        
        cases.forEach { case ->
            val result = LogicalFunctions.and(listOf(
                UDM.Scalar(case.a),
                UDM.Scalar(case.b)
            ))
            assertEquals(case.expected, (result as UDM.Scalar).value as Boolean,
                "${case.a} AND ${case.b} should be ${case.expected}")
        }
    }
    
    @Test
    fun `test truth table - OR complete`() {
        data class OrCase(val a: Boolean, val b: Boolean, val expected: Boolean)
        val cases = listOf(
            OrCase(true, true, true),
            OrCase(true, false, true),
            OrCase(false, true, true),
            OrCase(false, false, false)
        )
        
        cases.forEach { case ->
            val result = LogicalFunctions.or(listOf(
                UDM.Scalar(case.a),
                UDM.Scalar(case.b)
            ))
            assertEquals(case.expected, (result as UDM.Scalar).value as Boolean,
                "${case.a} OR ${case.b} should be ${case.expected}")
        }
    }
    
    @Test
    fun `test truth table - XOR complete`() {
        data class XorCase(val a: Boolean, val b: Boolean, val expected: Boolean)
        val cases = listOf(
            XorCase(true, true, false),
            XorCase(true, false, true),
            XorCase(false, true, true),
            XorCase(false, false, false)
        )
        
        cases.forEach { case ->
            val result = LogicalFunctions.xor(listOf(
                UDM.Scalar(case.a),
                UDM.Scalar(case.b)
            ))
            assertEquals(case.expected, (result as UDM.Scalar).value as Boolean,
                "${case.a} XOR ${case.b} should be ${case.expected}")
        }
    }
    
    @Test
    fun `test truth table - IMPLIES complete`() {
        data class ImpliesCase(val a: Boolean, val b: Boolean, val expected: Boolean)
        val cases = listOf(
            ImpliesCase(true, true, true),
            ImpliesCase(true, false, false),
            ImpliesCase(false, true, true),
            ImpliesCase(false, false, true)
        )
        
        cases.forEach { case ->
            val result = LogicalFunctions.implies(listOf(
                UDM.Scalar(case.a),
                UDM.Scalar(case.b)
            ))
            assertEquals(case.expected, (result as UDM.Scalar).value as Boolean,
                "${case.a} IMPLIES ${case.b} should be ${case.expected}")
        }
    }

    // ==================== De Morgan's Laws ====================
    
    @Test
    fun `test De Morgan - NOT(A AND B) equals (NOT A) OR (NOT B)`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        // NOT(A AND B)
        val andResult = LogicalFunctions.and(listOf(a, b))
        val notAnd = LogicalFunctions.not(listOf(andResult))
        
        // (NOT A) OR (NOT B)
        val notA = LogicalFunctions.not(listOf(a))
        val notB = LogicalFunctions.not(listOf(b))
        val orNotANotB = LogicalFunctions.or(listOf(notA, notB))
        
        assertEquals(
            (notAnd as UDM.Scalar).value,
            (orNotANotB as UDM.Scalar).value,
            "De Morgan's Law: NOT(A AND B) = (NOT A) OR (NOT B)"
        )
    }
    
    @Test
    fun `test De Morgan - NOT(A OR B) equals (NOT A) AND (NOT B)`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        
        // NOT(A OR B)
        val orResult = LogicalFunctions.or(listOf(a, b))
        val notOr = LogicalFunctions.not(listOf(orResult))
        
        // (NOT A) AND (NOT B)
        val notA = LogicalFunctions.not(listOf(a))
        val notB = LogicalFunctions.not(listOf(b))
        val andNotANotB = LogicalFunctions.and(listOf(notA, notB))
        
        assertEquals(
            (notOr as UDM.Scalar).value,
            (andNotANotB as UDM.Scalar).value,
            "De Morgan's Law: NOT(A OR B) = (NOT A) AND (NOT B)"
        )
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - chained operations`() {
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        val c = UDM.Scalar(true)
        
        // (A AND B) OR C
        val andResult = LogicalFunctions.and(listOf(a, b))
        val finalResult = LogicalFunctions.or(listOf(andResult, c))
        
        assertTrue((finalResult as UDM.Scalar).value as Boolean,
            "(true AND false) OR true should be true")
    }
    
    @Test
    fun `test edge case - complex boolean expression`() {
        // ((A OR B) AND (C OR D)) XOR (E AND F)
        val a = UDM.Scalar(true)
        val b = UDM.Scalar(false)
        val c = UDM.Scalar(true)
        val d = UDM.Scalar(false)
        val e = UDM.Scalar(false)
        val f = UDM.Scalar(true)
        
        val orAB = LogicalFunctions.or(listOf(a, b))
        val orCD = LogicalFunctions.or(listOf(c, d))
        val andLeft = LogicalFunctions.and(listOf(orAB, orCD))
        
        val andEF = LogicalFunctions.and(listOf(e, f))
        
        val result = LogicalFunctions.xor(listOf(andLeft, andEF))
        
        assertTrue((result as UDM.Scalar).value as Boolean)
    }
}
