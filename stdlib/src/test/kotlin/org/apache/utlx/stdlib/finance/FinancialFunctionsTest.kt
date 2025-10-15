// stdlib/src/test/kotlin/org/apache/utlx/stdlib/finance/FinancialFunctionsTest.kt
package org.apache.utlx.stdlib.finance

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import kotlin.math.abs

/**
 * Comprehensive test suite for Financial functions.
 * 
 * Tests cover:
 * - Currency formatting and parsing
 * - Decimal rounding (financial precision)
 * - Tax calculations (add, remove, calculate)
 * - Discount and percentage calculations
 * - Time value of money (PV, FV, interest)
 * - Currency validation
 */
class FinancialFunctionsTest {

    private fun assertAlmostEquals(expected: Double, actual: Double, delta: Double = 0.01, message: String = "") {
        assertTrue(abs(expected - actual) < delta,
            "$message Expected: $expected, Actual: $actual, Delta: ${abs(expected - actual)}")
    }

    // ==================== Currency Formatting Tests ====================
    
    @Test
    fun `test formatCurrency - USD`() {
        val amount = UDM.Scalar(1234.56)
        val currency = UDM.Scalar("USD")
        
        val result = FinancialFunctions.formatCurrency(listOf(amount, currency))
        val formatted = (result as UDM.Scalar).value as String
        
        assertTrue(formatted.contains("1,234.56") || formatted.contains("1234.56"), 
            "Should format with proper decimal places")
        assertTrue(formatted.contains("$") || formatted.contains("USD"), 
            "Should include currency symbol/code")
    }
    
    @Test
    fun `test formatCurrency - EUR`() {
        val amount = UDM.Scalar(9999.99)
        val currency = UDM.Scalar("EUR")
        
        val result = FinancialFunctions.formatCurrency(listOf(amount, currency))
        val formatted = (result as UDM.Scalar).value as String
        
        assertTrue(formatted.contains("9") && formatted.contains("99"), 
            "Should contain amount digits")
    }
    
    @Test
    fun `test formatCurrency - negative amount`() {
        val amount = UDM.Scalar(-50.00)
        val currency = UDM.Scalar("USD")
        
        val result = FinancialFunctions.formatCurrency(listOf(amount, currency))
        val formatted = (result as UDM.Scalar).value as String
        
        assertTrue(formatted.contains("-") || formatted.contains("("), 
            "Should indicate negative amount")
    }
    
    @Test
    fun `test formatCurrency - zero amount`() {
        val amount = UDM.Scalar(0.00)
        val currency = UDM.Scalar("USD")
        
        val result = FinancialFunctions.formatCurrency(listOf(amount, currency))
        val formatted = (result as UDM.Scalar).value as String
        
        assertTrue(formatted.contains("0"), "Should format zero")
    }

    // ==================== Currency Parsing Tests ====================
    
    @Test
    fun `test parseCurrency - USD with symbol`() {
        val formatted = UDM.Scalar("$1,234.56")
        
        val result = FinancialFunctions.parseCurrency(listOf(formatted))
        val amount = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(1234.56, amount, delta = 0.01)
    }
    
    @Test
    fun `test parseCurrency - EUR with symbol`() {
        val formatted = UDM.Scalar("€999.99")
        
        val result = FinancialFunctions.parseCurrency(listOf(formatted))
        val amount = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(999.99, amount, delta = 0.01)
    }
    
    @Test
    fun `test parseCurrency - negative amount`() {
        val formatted = UDM.Scalar("-$50.00")
        
        val result = FinancialFunctions.parseCurrency(listOf(formatted))
        val amount = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(-50.00, amount, delta = 0.01)
    }
    
    @Test
    fun `test parseCurrency - parentheses for negative`() {
        val formatted = UDM.Scalar("($50.00)")
        
        val result = FinancialFunctions.parseCurrency(listOf(formatted))
        val amount = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(-50.00, amount, delta = 0.01)
    }

    // ==================== Rounding Tests ====================
    
    @Test
    fun `test roundToDecimalPlaces - 2 places`() {
        val amount = UDM.Scalar(123.456789)
        val places = UDM.Scalar(2)
        
        val result = FinancialFunctions.roundToDecimalPlaces(listOf(amount, places))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(123.46, rounded, 0.001)
    }
    
    @Test
    fun `test roundToDecimalPlaces - 0 places`() {
        val amount = UDM.Scalar(123.789)
        val places = UDM.Scalar(0)
        
        val result = FinancialFunctions.roundToDecimalPlaces(listOf(amount, places))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(124.0, rounded, 0.001)
    }
    
    @Test
    fun `test roundToDecimalPlaces - 4 places`() {
        val amount = UDM.Scalar(3.14159265)
        val places = UDM.Scalar(4)
        
        val result = FinancialFunctions.roundToDecimalPlaces(listOf(amount, places))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(3.1416, rounded, 0.00001)
    }
    
    @Test
    fun `test roundToCents - standard rounding`() {
        val amount = UDM.Scalar(12.345)
        
        val result = FinancialFunctions.roundToCents(listOf(amount))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(12.35, rounded, 0.001)
    }
    
    @Test
    fun `test roundToCents - half-cent rounding`() {
        val amount = UDM.Scalar(10.555)
        
        val result = FinancialFunctions.roundToCents(listOf(amount))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(10.56, rounded, 0.001, "Should round half-cent up")
    }

    // ==================== Tax Calculation Tests ====================
    
    @Test
    fun `test calculateTax - 10 percent`() {
        val amount = UDM.Scalar(100.00)
        val taxRate = UDM.Scalar(0.10)
        
        val result = FinancialFunctions.calculateTax(listOf(amount, taxRate))
        val tax = (result as UDM.Scalar).value as Double
        
        assertEquals(10.00, tax, 0.01)
    }
    
    @Test
    fun `test calculateTax - 8.875 percent (NYC sales tax)`() {
        val amount = UDM.Scalar(100.00)
        val taxRate = UDM.Scalar(0.08875)
        
        val result = FinancialFunctions.calculateTax(listOf(amount, taxRate))
        val tax = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(8.88, tax, delta = 0.01)
    }
    
    @Test
    fun `test addTax - 10 percent`() {
        val amount = UDM.Scalar(100.00)
        val taxRate = UDM.Scalar(0.10)
        
        val result = FinancialFunctions.addTax(listOf(amount, taxRate))
        val total = (result as UDM.Scalar).value as Double
        
        assertEquals(110.00, total, 0.01)
    }
    
    @Test
    fun `test addTax - 20 percent (VAT)`() {
        val amount = UDM.Scalar(50.00)
        val taxRate = UDM.Scalar(0.20)
        
        val result = FinancialFunctions.addTax(listOf(amount, taxRate))
        val total = (result as UDM.Scalar).value as Double
        
        assertEquals(60.00, total, 0.01)
    }
    
    @Test
    fun `test removeTax - 10 percent`() {
        val totalWithTax = UDM.Scalar(110.00)
        val taxRate = UDM.Scalar(0.10)
        
        val result = FinancialFunctions.removeTax(listOf(totalWithTax, taxRate))
        val baseAmount = (result as UDM.Scalar).value as Double
        
        assertEquals(100.00, baseAmount, 0.01)
    }
    
    @Test
    fun `test removeTax - 20 percent (VAT)`() {
        val totalWithTax = UDM.Scalar(120.00)
        val taxRate = UDM.Scalar(0.20)
        
        val result = FinancialFunctions.removeTax(listOf(totalWithTax, taxRate))
        val baseAmount = (result as UDM.Scalar).value as Double
        
        assertEquals(100.00, baseAmount, 0.01)
    }

    // ==================== Discount Tests ====================
    
    @Test
    fun `test calculateDiscount - 20 percent off`() {
        val originalPrice = UDM.Scalar(100.00)
        val discountRate = UDM.Scalar(0.20)
        
        val result = FinancialFunctions.calculateDiscount(listOf(originalPrice, discountRate))
        val finalPrice = (result as UDM.Scalar).value as Double
        
        assertEquals(80.00, finalPrice, 0.01)
    }
    
    @Test
    fun `test calculateDiscount - 50 percent off`() {
        val originalPrice = UDM.Scalar(200.00)
        val discountRate = UDM.Scalar(0.50)
        
        val result = FinancialFunctions.calculateDiscount(listOf(originalPrice, discountRate))
        val finalPrice = (result as UDM.Scalar).value as Double
        
        assertEquals(100.00, finalPrice, 0.01)
    }
    
    @Test
    fun `test percentageChange - increase`() {
        val oldValue = UDM.Scalar(100.00)
        val newValue = UDM.Scalar(150.00)
        
        val result = FinancialFunctions.percentageChange(listOf(oldValue, newValue))
        val change = (result as UDM.Scalar).value as Double
        
        assertEquals(50.0, change, 0.01, "Should be +50%")
    }
    
    @Test
    fun `test percentageChange - decrease`() {
        val oldValue = UDM.Scalar(100.00)
        val newValue = UDM.Scalar(75.00)
        
        val result = FinancialFunctions.percentageChange(listOf(oldValue, newValue))
        val change = (result as UDM.Scalar).value as Double
        
        assertEquals(-25.0, change, 0.01, "Should be -25%")
    }
    
    @Test
    fun `test percentageChange - no change`() {
        val oldValue = UDM.Scalar(100.00)
        val newValue = UDM.Scalar(100.00)
        
        val result = FinancialFunctions.percentageChange(listOf(oldValue, newValue))
        val change = (result as UDM.Scalar).value as Double
        
        assertEquals(0.0, change, 0.01)
    }

    // ==================== Time Value of Money Tests ====================
    
    @Test
    fun `test presentValue - simple example`() {
        val futureValue = UDM.Scalar(1100.00)
        val rate = UDM.Scalar(0.10) // 10%
        val periods = UDM.Scalar(1)
        
        val result = FinancialFunctions.presentValue(listOf(futureValue, rate, periods))
        val pv = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(1000.00, pv, delta = 0.50)
    }
    
    @Test
    fun `test presentValue - multiple periods`() {
        val futureValue = UDM.Scalar(1331.00)
        val rate = UDM.Scalar(0.10)
        val periods = UDM.Scalar(3)
        
        val result = FinancialFunctions.presentValue(listOf(futureValue, rate, periods))
        val pv = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(1000.00, pv, delta = 1.0)
    }
    
    @Test
    fun `test futureValue - simple example`() {
        val presentValue = UDM.Scalar(1000.00)
        val rate = UDM.Scalar(0.10)
        val periods = UDM.Scalar(1)
        
        val result = FinancialFunctions.futureValue(listOf(presentValue, rate, periods))
        val fv = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(1100.00, fv, delta = 0.01)
    }
    
    @Test
    fun `test futureValue - multiple periods`() {
        val presentValue = UDM.Scalar(1000.00)
        val rate = UDM.Scalar(0.10)
        val periods = UDM.Scalar(3)
        
        val result = FinancialFunctions.futureValue(listOf(presentValue, rate, periods))
        val fv = (result as UDM.Scalar).value as Double
        
        assertAlmostEquals(1331.00, fv, delta = 0.50)
    }
    
    @Test
    fun `test compoundInterest - annual compounding`() {
        val principal = UDM.Scalar(10000.00)
        val rate = UDM.Scalar(0.05) // 5%
        val periods = UDM.Scalar(10) // 10 years
        val frequency = UDM.Scalar(1) // Annual
        
        val result = FinancialFunctions.compoundInterest(listOf(principal, rate, periods, frequency))
        val amount = (result as UDM.Scalar).value as Double
        
        // Should be approximately $16,288.95
        assertAlmostEquals(16288.95, amount, delta = 10.0)
    }
    
    @Test
    fun `test compoundInterest - monthly compounding`() {
        val principal = UDM.Scalar(10000.00)
        val rate = UDM.Scalar(0.05)
        val periods = UDM.Scalar(10)
        val frequency = UDM.Scalar(12) // Monthly
        
        val result = FinancialFunctions.compoundInterest(listOf(principal, rate, periods, frequency))
        val amount = (result as UDM.Scalar).value as Double
        
        // Monthly compounding should yield slightly more than annual
        assertTrue(amount > 16288.95, "Monthly compounding should exceed annual compounding")
        assertAlmostEquals(16470.09, amount, delta = 10.0)
    }
    
    @Test
    fun `test simpleInterest - one year`() {
        val principal = UDM.Scalar(10000.00)
        val rate = UDM.Scalar(0.05)
        val time = UDM.Scalar(1)
        
        val result = FinancialFunctions.simpleInterest(listOf(principal, rate, time))
        val interest = (result as UDM.Scalar).value as Double
        
        assertEquals(500.00, interest, 0.01)
    }
    
    @Test
    fun `test simpleInterest - multiple years`() {
        val principal = UDM.Scalar(10000.00)
        val rate = UDM.Scalar(0.05)
        val time = UDM.Scalar(3)
        
        val result = FinancialFunctions.simpleInterest(listOf(principal, rate, time))
        val interest = (result as UDM.Scalar).value as Double
        
        assertEquals(1500.00, interest, 0.01)
    }

    // ==================== Validation Tests ====================
    
    @Test
    fun `test isValidCurrency - valid codes`() {
        val validCodes = listOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD")
        
        validCodes.forEach { code ->
            val result = FinancialFunctions.isValidCurrency(listOf(UDM.Scalar(code)))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertTrue(isValid, "$code should be valid currency code")
        }
    }
    
    @Test
    fun `test isValidCurrency - invalid codes`() {
        val invalidCodes = listOf("XXX", "ABC", "US", "EURO", "")
        
        invalidCodes.forEach { code ->
            val result = FinancialFunctions.isValidCurrency(listOf(UDM.Scalar(code)))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertFalse(isValid, "$code should be invalid currency code")
        }
    }
    
    @Test
    fun `test getCurrencyDecimals - standard currencies`() {
        val usd = UDM.Scalar("USD")
        val result = FinancialFunctions.getCurrencyDecimals(listOf(usd))
        val decimals = (result as UDM.Scalar).value as Int
        
        assertEquals(2, decimals, "USD should use 2 decimal places")
    }
    
    @Test
    fun `test getCurrencyDecimals - JPY has no decimals`() {
        val jpy = UDM.Scalar("JPY")
        val result = FinancialFunctions.getCurrencyDecimals(listOf(jpy))
        val decimals = (result as UDM.Scalar).value as Int
        
        assertEquals(0, decimals, "JPY should use 0 decimal places")
    }
    
    @Test
    fun `test isValidAmount - positive amounts`() {
        val amounts = listOf(0.01, 100.00, 9999.99)
        
        amounts.forEach { amount ->
            val result = FinancialFunctions.isValidAmount(listOf(UDM.Scalar(amount)))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertTrue(isValid, "$amount should be valid")
        }
    }
    
    @Test
    fun `test isValidAmount - invalid amounts`() {
        val amounts = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        
        amounts.forEach { amount ->
            val result = FinancialFunctions.isValidAmount(listOf(UDM.Scalar(amount)))
            val isValid = (result as UDM.Scalar).value as Boolean
            
            assertFalse(isValid, "$amount should be invalid")
        }
    }

    // ==================== Real-World Scenarios ====================
    
    @Test
    fun `test real-world - e-commerce checkout`() {
        // Product subtotal
        val subtotal = 100.00
        
        // Apply 20% discount
        val discountResult = FinancialFunctions.calculateDiscount(
            listOf(UDM.Scalar(subtotal), UDM.Scalar(0.20))
        )
        val afterDiscount = (discountResult as UDM.Scalar).value as Double
        assertEquals(80.00, afterDiscount, 0.01)
        
        // Add 8.875% tax (NYC sales tax)
        val taxResult = FinancialFunctions.addTax(
            listOf(UDM.Scalar(afterDiscount), UDM.Scalar(0.08875))
        )
        val total = (taxResult as UDM.Scalar).value as Double
        assertAlmostEquals(87.10, total, delta = 0.01)
        
        // Format for display
        val formatted = FinancialFunctions.formatCurrency(
            listOf(UDM.Scalar(total), UDM.Scalar("USD"))
        )
        assertNotNull(formatted)
    }
    
    @Test
    fun `test real-world - savings calculator`() {
        // Monthly savings of $500 for 5 years at 5% annual interest
        val monthlyDeposit = 500.00
        val totalDeposits = monthlyDeposit * 12 * 5  // $30,000
        
        // Using compound interest
        val result = FinancialFunctions.compoundInterest(
            listOf(
                UDM.Scalar(totalDeposits),
                UDM.Scalar(0.05),
                UDM.Scalar(5),
                UDM.Scalar(12) // Monthly compounding
            )
        )
        val finalAmount = (result as UDM.Scalar).value as Double
        
        // Final amount should be greater than deposits due to interest
        assertTrue(finalAmount > totalDeposits, "Should earn interest")
    }
    
    @Test
    fun `test real-world - loan payment calculation`() {
        // Calculate interest on $200,000 mortgage at 4% for 30 years
        val principal = 200000.00
        val annualRate = 0.04
        val years = 30
        
        val simpleInterestResult = FinancialFunctions.simpleInterest(
            listOf(
                UDM.Scalar(principal),
                UDM.Scalar(annualRate),
                UDM.Scalar(years)
            )
        )
        val simpleInterest = (simpleInterestResult as UDM.Scalar).value as Double
        
        assertEquals(240000.00, simpleInterest, 1.0, "Simple interest over 30 years")
    }
    
    @Test
    fun `test real-world - currency conversion with fees`() {
        val baseAmount = 1000.00
        
        // 3% conversion fee
        val fee = baseAmount * 0.03
        val amountAfterFee = baseAmount - fee
        
        // Format in both currencies
        val usdFormatted = FinancialFunctions.formatCurrency(
            listOf(UDM.Scalar(baseAmount), UDM.Scalar("USD"))
        )
        val eurFormatted = FinancialFunctions.formatCurrency(
            listOf(UDM.Scalar(amountAfterFee), UDM.Scalar("EUR"))
        )
        
        assertNotNull(usdFormatted)
        assertNotNull(eurFormatted)
    }

    // ==================== Edge Cases ====================
    
    @Test
    fun `test edge case - zero principal`() {
        val result = FinancialFunctions.simpleInterest(
            listOf(UDM.Scalar(0.0), UDM.Scalar(0.05), UDM.Scalar(10))
        )
        val interest = (result as UDM.Scalar).value as Double
        
        assertEquals(0.0, interest, 0.001)
    }
    
    @Test
    fun `test edge case - zero interest rate`() {
        val result = FinancialFunctions.compoundInterest(
            listOf(UDM.Scalar(1000.0), UDM.Scalar(0.0), UDM.Scalar(10), UDM.Scalar(1))
        )
        val amount = (result as UDM.Scalar).value as Double
        
        assertEquals(1000.0, amount, 0.001, "Zero interest should return principal")
    }
    
    @Test
    fun `test edge case - 100 percent discount`() {
        val result = FinancialFunctions.calculateDiscount(
            listOf(UDM.Scalar(100.0), UDM.Scalar(1.0))
        )
        val finalPrice = (result as UDM.Scalar).value as Double
        
        assertEquals(0.0, finalPrice, 0.001, "100% discount should result in 0")
    }
    
    @Test
    fun `test edge case - very small amounts`() {
        val result = FinancialFunctions.roundToCents(listOf(UDM.Scalar(0.001)))
        val rounded = (result as UDM.Scalar).value as Double
        
        assertEquals(0.00, rounded, 0.001, "Sub-cent amounts should round to 0")
    }
}
