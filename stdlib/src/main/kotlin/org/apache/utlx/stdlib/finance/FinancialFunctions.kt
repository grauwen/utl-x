// stdlib/src/main/kotlin/org/apache/utlx/stdlib/finance/FinancialFunctions.kt
package org.apache.utlx.stdlib.finance

import org.apache.utlx.core.udm.UDM
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

/**
 * Financial Functions Module
 * 
 * UNIQUE DIFFERENTIATOR: The only transformation language with built-in financial functions!
 * 
 * Provides currency formatting, tax calculations, financial mathematics,
 * and business-oriented number operations.
 * 
 * Categories:
 * - Currency: Formatting and parsing
 * - Calculations: Tax, discount, percentage
 * - Time Value of Money: PV, FV, compound interest
 * - Rounding: Banker's rounding, decimal places
 * - Validation: Currency amounts, valid ranges
 * 
 * @since UTL-X 1.1
 */
object FinancialFunctions {
    
    // ============================================
    // CURRENCY FORMATTING
    // ============================================
    
    /**
     * Formats a number as currency with locale-specific formatting.
     * 
     * Supports all ISO 4217 currency codes and locales.
     * 
     * @param args [0] amount (Number)
     *             [1] currency code (String, optional, default: "USD")
     *             [2] locale (String, optional, default: "en_US")
     * @return formatted currency string
     * 
     * Example:
     * ```
     * formatCurrency(1234.56) → "$1,234.56"
     * formatCurrency(1234.56, "EUR") → "€1,234.56"
     * formatCurrency(1234.56, "EUR", "de_DE") → "1.234,56 €"
     * formatCurrency(1234.56, "JPY") → "¥1,235" (no decimals)
     * formatCurrency(1234.56, "GBP", "en_GB") → "£1,234.56"
     * ```
     */
    fun formatCurrency(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("formatCurrency() requires at least 1 argument: amount")
        }
        
        val amount = args[0].asNumber()
        val currencyCode = if (args.size > 1) args[1].asString() else "USD"
        val localeTag = if (args.size > 2) args[2].asString() else "en_US"
        
        try {
            val locale = Locale.forLanguageTag(localeTag.replace("_", "-"))
            val format = NumberFormat.getCurrencyInstance(locale)
            format.currency = Currency.getInstance(currencyCode)
            
            return UDM.Scalar(format.format(amount))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid currency or locale: $currencyCode, $localeTag", e)
        }
    }
    
    /**
     * Parses a currency string to a number.
     * 
     * Handles locale-specific formatting and currency symbols.
     * 
     * @param args [0] currency string (String)
     *             [1] locale (String, optional, default: "en_US")
     * @return numeric value
     * 
     * Example:
     * ```
     * parseCurrency("$1,234.56") → 1234.56
     * parseCurrency("€1.234,56", "de_DE") → 1234.56
     * parseCurrency("¥1,235") → 1235.0
     * parseCurrency("£1,234.56", "en_GB") → 1234.56
     * ```
     */
    fun parseCurrency(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("parseCurrency() requires at least 1 argument: currencyString")
        }
        
        val currencyStr = args[0].asString()
        val localeTag = if (args.size > 1) args[1].asString() else "en_US"
        
        try {
            val locale = Locale.forLanguageTag(localeTag.replace("_", "-"))
            val format = NumberFormat.getCurrencyInstance(locale)
            val number = format.parse(currencyStr)
            
            return UDM.Scalar(number.toDouble())
        } catch (e: Exception) {
            throw IllegalArgumentException("Cannot parse currency string: $currencyStr", e)
        }
    }
    
    // ============================================
    // ROUNDING & PRECISION
    // ============================================
    
    /**
     * Rounds a number to a specified number of decimal places using banker's rounding.
     * 
     * Banker's rounding (round half to even) minimizes bias in financial calculations.
     * Also known as "round half to even" or "statistical rounding."
     * 
     * @param args [0] number to round (Number)
     *             [1] decimal places (Number)
     * @return rounded number
     * 
     * Example:
     * ```
     * roundToDecimalPlaces(1.2349, 2) → 1.23
     * roundToDecimalPlaces(1.2351, 2) → 1.24
     * roundToDecimalPlaces(1.235, 2) → 1.24 (banker's rounding: round to even)
     * roundToDecimalPlaces(1.245, 2) → 1.24 (banker's rounding: round to even)
     * roundToDecimalPlaces(123.456, 0) → 123.0
     * ```
     */
    fun roundToDecimalPlaces(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("roundToDecimalPlaces() requires 2 arguments: number, places")
        }
        
        val number = args[0].asNumber()
        val places = args[1].asNumber().toInt()
        
        if (places < 0) {
            throw IllegalArgumentException("Decimal places must be non-negative, got: $places")
        }
        
        val bd = BigDecimal.valueOf(number)
        val rounded = bd.setScale(places, RoundingMode.HALF_EVEN)
        
        return UDM.Scalar(rounded.toDouble())
    }
    
    /**
     * Rounds up to the nearest cent (2 decimal places).
     * 
     * Common in pricing to avoid fractions of a cent.
     * 
     * @param args [0] amount (Number)
     * @return amount rounded to 2 decimals
     * 
     * Example:
     * ```
     * roundToCents(1.234) → 1.23
     * roundToCents(1.235) → 1.24
     * roundToCents(9.999) → 10.00
     * ```
     */
    fun roundToCents(args: List<UDM>): UDM {
        return roundToDecimalPlaces(listOf(args[0], UDM.Scalar(2)))
    }
    
    // ============================================
    // TAX & DISCOUNT CALCULATIONS
    // ============================================
    
    /**
     * Calculates tax amount for a given amount and rate.
     * 
     * @param args [0] amount before tax (Number)
     *             [1] tax rate as decimal (Number, e.g., 0.08 for 8%)
     * @return tax amount
     * 
     * Example:
     * ```
     * calculateTax(100, 0.08) → 8.0
     * calculateTax(250, 0.10) → 25.0
     * calculateTax(99.99, 0.0625) → 6.249375
     * ```
     */
    fun calculateTax(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("calculateTax() requires 2 arguments: amount, rate")
        }
        
        val amount = args[0].asNumber()
        val rate = args[1].asNumber()
        
        val tax = amount * rate
        return UDM.Scalar(tax)
    }
    
    /**
     * Calculates the total amount including tax.
     * 
     * @param args [0] amount before tax (Number)
     *             [1] tax rate as decimal (Number)
     * @return total amount with tax
     * 
     * Example:
     * ```
     * addTax(100, 0.08) → 108.0
     * addTax(250, 0.10) → 275.0
     * ```
     */
    fun addTax(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("addTax() requires 2 arguments: amount, rate")
        }
        
        val amount = args[0].asNumber()
        val rate = args[1].asNumber()
        
        val total = amount * (1 + rate)
        return UDM.Scalar(total)
    }
    
    /**
     * Calculates the original amount from a total that includes tax.
     * 
     * @param args [0] total amount with tax (Number)
     *             [1] tax rate as decimal (Number)
     * @return original amount before tax
     * 
     * Example:
     * ```
     * removeTax(108, 0.08) → 100.0
     * removeTax(275, 0.10) → 250.0
     * ```
     */
    fun removeTax(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("removeTax() requires 2 arguments: totalWithTax, rate")
        }
        
        val totalWithTax = args[0].asNumber()
        val rate = args[1].asNumber()
        
        val originalAmount = totalWithTax / (1 + rate)
        return UDM.Scalar(originalAmount)
    }
    
    /**
     * Calculates the price after applying a discount.
     * 
     * @param args [0] original price (Number)
     *             [1] discount as decimal (Number, e.g., 0.10 for 10% off)
     * @return discounted price
     * 
     * Example:
     * ```
     * calculateDiscount(100, 0.10) → 90.0
     * calculateDiscount(250, 0.25) → 187.5
     * calculateDiscount(49.99, 0.15) → 42.4915
     * ```
     */
    fun calculateDiscount(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("calculateDiscount() requires 2 arguments: price, discount")
        }
        
        val price = args[0].asNumber()
        val discount = args[1].asNumber()
        
        if (discount < 0 || discount > 1) {
            throw IllegalArgumentException("Discount must be between 0 and 1, got: $discount")
        }
        
        val discountedPrice = price * (1 - discount)
        return UDM.Scalar(discountedPrice)
    }
    
    /**
     * Calculates the percentage change between two values.
     * 
     * @param args [0] old value (Number)
     *             [1] new value (Number)
     * @return percentage change (positive = increase, negative = decrease)
     * 
     * Example:
     * ```
     * percentageChange(100, 110) → 10.0 (10% increase)
     * percentageChange(100, 90) → -10.0 (10% decrease)
     * percentageChange(50, 100) → 100.0 (doubled)
     * percentageChange(100, 50) → -50.0 (halved)
     * ```
     */
    fun percentageChange(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException("percentageChange() requires 2 arguments: oldValue, newValue")
        }
        
        val oldValue = args[0].asNumber()
        val newValue = args[1].asNumber()
        
        if (oldValue == 0.0) {
            throw IllegalArgumentException("Cannot calculate percentage change from zero")
        }
        
        val change = ((newValue - oldValue) / oldValue) * 100
        return UDM.Scalar(change)
    }
    
    // ============================================
    // TIME VALUE OF MONEY
    // ============================================
    
    /**
     * Calculates the present value of a future amount.
     * 
     * PV = FV / (1 + r)^n
     * 
     * @param args [0] future value (Number)
     *             [1] interest rate per period (Number, as decimal)
     *             [2] number of periods (Number)
     * @return present value
     * 
     * Example:
     * ```
     * presentValue(1000, 0.05, 1) → 952.38 (what $1000 next year is worth today)
     * presentValue(10000, 0.08, 5) → 6805.83
     * presentValue(5000, 0.03, 10) → 3720.46
     * ```
     */
    fun presentValue(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("presentValue() requires 3 arguments: futureValue, rate, periods")
        }
        
        val futureValue = args[0].asNumber()
        val rate = args[1].asNumber()
        val periods = args[2].asNumber()
        
        val pv = futureValue / Math.pow(1 + rate, periods)
        return UDM.Scalar(pv)
    }
    
    /**
     * Calculates the future value of a present amount.
     * 
     * FV = PV * (1 + r)^n
     * 
     * @param args [0] present value (Number)
     *             [1] interest rate per period (Number, as decimal)
     *             [2] number of periods (Number)
     * @return future value
     * 
     * Example:
     * ```
     * futureValue(1000, 0.05, 1) → 1050.0 ($1000 today will be worth $1050 next year)
     * futureValue(1000, 0.08, 5) → 1469.33
     * futureValue(5000, 0.03, 10) → 6719.58
     * ```
     */
    fun futureValue(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("futureValue() requires 3 arguments: presentValue, rate, periods")
        }
        
        val presentValue = args[0].asNumber()
        val rate = args[1].asNumber()
        val periods = args[2].asNumber()
        
        val fv = presentValue * Math.pow(1 + rate, periods)
        return UDM.Scalar(fv)
    }
    
    /**
     * Calculates compound interest.
     * 
     * A = P * (1 + r/n)^(n*t)
     * where:
     * - A = final amount
     * - P = principal
     * - r = annual interest rate
     * - n = compounding frequency per year
     * - t = time in years
     * 
     * @param args [0] principal amount (Number)
     *             [1] annual interest rate (Number, as decimal)
     *             [2] time in years (Number)
     *             [3] compounding frequency per year (Number, optional, default: 1)
     * @return final amount
     * 
     * Example:
     * ```
     * compoundInterest(1000, 0.05, 1) → 1050.0 (annual)
     * compoundInterest(1000, 0.05, 1, 12) → 1051.16 (monthly)
     * compoundInterest(1000, 0.05, 1, 365) → 1051.27 (daily)
     * compoundInterest(10000, 0.08, 10, 4) → 22080.40 (quarterly over 10 years)
     * ```
     */
    fun compoundInterest(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("compoundInterest() requires at least 3 arguments: principal, rate, time")
        }
        
        val principal = args[0].asNumber()
        val annualRate = args[1].asNumber()
        val timeYears = args[2].asNumber()
        val frequency = if (args.size > 3) args[3].asNumber() else 1.0
        
        val amount = principal * Math.pow(1 + annualRate / frequency, frequency * timeYears)
        return UDM.Scalar(amount)
    }
    
    /**
     * Calculates simple interest.
     * 
     * I = P * r * t
     * 
     * @param args [0] principal amount (Number)
     *             [1] interest rate per period (Number, as decimal)
     *             [2] number of periods (Number)
     * @return interest amount
     * 
     * Example:
     * ```
     * simpleInterest(1000, 0.05, 1) → 50.0
     * simpleInterest(1000, 0.08, 3) → 240.0
     * simpleInterest(5000, 0.06, 5) → 1500.0
     * ```
     */
    fun simpleInterest(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException("simpleInterest() requires 3 arguments: principal, rate, time")
        }
        
        val principal = args[0].asNumber()
        val rate = args[1].asNumber()
        val time = args[2].asNumber()
        
        val interest = principal * rate * time
        return UDM.Scalar(interest)
    }
    
    // ============================================
    // VALIDATION & UTILITIES
    // ============================================
    
    /**
     * Validates if a currency code is valid (ISO 4217).
     * 
     * @param args [0] currency code (String)
     * @return true if valid, false otherwise
     * 
     * Example:
     * ```
     * isValidCurrency("USD") → true
     * isValidCurrency("EUR") → true
     * isValidCurrency("ABC") → false
     * isValidCurrency("usd") → false (must be uppercase)
     * ```
     */
    fun isValidCurrency(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isValidCurrency() requires 1 argument: currencyCode")
        }
        
        val code = args[0].asString()
        
        return try {
            Currency.getInstance(code)
            UDM.Scalar(true)
        } catch (e: IllegalArgumentException) {
            UDM.Scalar(false)
        }
    }
    
    /**
     * Gets the number of decimal places for a currency.
     * 
     * @param args [0] currency code (String)
     * @return number of decimal places (0 for JPY, 2 for USD, 3 for KWD, etc.)
     * 
     * Example:
     * ```
     * getCurrencyDecimals("USD") → 2
     * getCurrencyDecimals("JPY") → 0
     * getCurrencyDecimals("KWD") → 3 (Kuwaiti Dinar)
     * ```
     */
    fun getCurrencyDecimals(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("getCurrencyDecimals() requires 1 argument: currencyCode")
        }
        
        val code = args[0].asString()
        
        return try {
            val currency = Currency.getInstance(code)
            UDM.Scalar(currency.defaultFractionDigits)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid currency code: $code", e)
        }
    }
    
    /**
     * Validates if an amount is within acceptable range.
     * 
     * @param args [0] amount (Number)
     *             [1] minimum (Number, optional, default: 0)
     *             [2] maximum (Number, optional, default: infinity)
     * @return true if within range, false otherwise
     * 
     * Example:
     * ```
     * isValidAmount(100) → true (positive)
     * isValidAmount(-10) → false (negative)
     * isValidAmount(50, 10, 100) → true (within range)
     * isValidAmount(150, 10, 100) → false (exceeds max)
     * ```
     */
    fun isValidAmount(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isValidAmount() requires at least 1 argument: amount")
        }
        
        val amount = args[0].asNumber()
        val min = if (args.size > 1) args[1].asNumber() else 0.0
        val max = if (args.size > 2) args[2].asNumber() else Double.MAX_VALUE
        
        val valid = amount >= min && amount <= max
        return UDM.Scalar(valid)
    }
}
