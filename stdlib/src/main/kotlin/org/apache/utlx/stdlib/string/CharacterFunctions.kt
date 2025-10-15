// stdlib/src/main/kotlin/org/apache/utlx/stdlib/string/CharacterFunctions.kt
package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM

/**
 * Character Classification Functions
 * 
 * Provides character class tests for string validation.
 * Achieves parity with DataWeave 2.4+ string validation functions.
 * 
 * Functions:
 * - isAlpha: Test if all characters are alphabetic
 * - isNumeric: Test if all characters are numeric
 * - isAlphanumeric: Test if all characters are letters or digits
 * - isWhitespace: Test if all characters are whitespace
 * - isUpperCase: Test if all characters are uppercase
 * - isLowerCase: Test if all characters are lowercase
 * - isPrintable: Test if all characters are printable
 * - isAscii: Test if all characters are ASCII
 * 
 * @since UTL-X 1.1
 */
object CharacterFunctions {
    
    /**
     * Returns true if all characters in the string are alphabetic letters.
     * 
     * Alphabetic characters include A-Z, a-z, and Unicode letters.
     * Returns false for empty strings.
     * 
     * @param args [0] string to test
     * @return true if all characters are letters, false otherwise
     * 
     * Example:
     * ```
     * isAlpha("Hello") → true
     * isAlpha("Hello123") → false
     * isAlpha("Hello World") → false (space is not alphabetic)
     * isAlpha("Ñoño") → true (Unicode letters)
     * isAlpha("") → false
     * ```
     */
    fun isAlpha(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isAlpha() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { it.isLetter() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all characters in the string are numeric digits.
     * 
     * Only recognizes 0-9 as numeric digits.
     * Returns false for empty strings and decimal points.
     * 
     * @param args [0] string to test
     * @return true if all characters are digits, false otherwise
     * 
     * Example:
     * ```
     * isNumeric("12345") → true
     * isNumeric("123.45") → false (decimal point)
     * isNumeric("123abc") → false
     * isNumeric("0") → true
     * isNumeric("") → false
     * isNumeric("-123") → false (minus sign)
     * ```
     */
    fun isNumeric(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isNumeric() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { it.isDigit() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all characters are alphabetic letters or numeric digits.
     * 
     * Combines isAlpha and isNumeric: characters must be A-Z, a-z, 0-9, or Unicode letters.
     * Returns false for empty strings.
     * 
     * @param args [0] string to test
     * @return true if all characters are alphanumeric, false otherwise
     * 
     * Example:
     * ```
     * isAlphanumeric("Hello123") → true
     * isAlphanumeric("Hello 123") → false (space)
     * isAlphanumeric("abc") → true
     * isAlphanumeric("123") → true
     * isAlphanumeric("abc-123") → false (hyphen)
     * isAlphanumeric("") → false
     * ```
     */
    fun isAlphanumeric(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isAlphanumeric() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { it.isLetterOrDigit() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all characters in the string are whitespace.
     * 
     * Whitespace includes spaces, tabs, newlines, and other Unicode whitespace.
     * Returns false for empty strings (no whitespace to check).
     * 
     * @param args [0] string to test
     * @return true if all characters are whitespace, false otherwise
     * 
     * Example:
     * ```
     * isWhitespace("   ") → true
     * isWhitespace("\t\n") → true
     * isWhitespace("Hello World") → false
     * isWhitespace(" a ") → false
     * isWhitespace("") → false
     * ```
     */
    fun isWhitespace(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isWhitespace() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { it.isWhitespace() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all alphabetic characters in the string are uppercase.
     * 
     * Non-alphabetic characters (digits, spaces, etc.) are ignored.
     * Returns true if there are no alphabetic characters.
     * Returns false for empty strings.
     * 
     * @param args [0] string to test
     * @return true if all letters are uppercase, false otherwise
     * 
     * Example:
     * ```
     * isUpperCase("HELLO") → true
     * isUpperCase("HELLO123") → true (digits ignored)
     * isUpperCase("HELLO WORLD") → true
     * isUpperCase("Hello") → false
     * isUpperCase("123") → true (no letters)
     * isUpperCase("") → false
     * ```
     */
    fun isUpperCase(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isUpperCase() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        // Get all letters
        val letters = str.filter { it.isLetter() }
        
        // If no letters, return true (vacuously true)
        if (letters.isEmpty()) {
            return UDM.Scalar(true)
        }
        
        val result = letters.all { it.isUpperCase() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all alphabetic characters in the string are lowercase.
     * 
     * Non-alphabetic characters (digits, spaces, etc.) are ignored.
     * Returns true if there are no alphabetic characters.
     * Returns false for empty strings.
     * 
     * @param args [0] string to test
     * @return true if all letters are lowercase, false otherwise
     * 
     * Example:
     * ```
     * isLowerCase("hello") → true
     * isLowerCase("hello123") → true (digits ignored)
     * isLowerCase("hello world") → true
     * isLowerCase("Hello") → false
     * isLowerCase("123") → true (no letters)
     * isLowerCase("") → false
     * ```
     */
    fun isLowerCase(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isLowerCase() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        // Get all letters
        val letters = str.filter { it.isLetter() }
        
        // If no letters, return true (vacuously true)
        if (letters.isEmpty()) {
            return UDM.Scalar(true)
        }
        
        val result = letters.all { it.isLowerCase() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all characters in the string are printable.
     * 
     * Printable characters include letters, digits, punctuation, and spaces.
     * Non-printable characters include control characters, null bytes, etc.
     * Returns false for empty strings.
     * 
     * @param args [0] string to test
     * @return true if all characters are printable, false otherwise
     * 
     * Example:
     * ```
     * isPrintable("Hello World!") → true
     * isPrintable("Line1\nLine2") → false (newline)
     * isPrintable("Tab\there") → false (tab)
     * isPrintable("abc123!@#") → true
     * isPrintable("") → false
     * ```
     */
    fun isPrintable(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isPrintable() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { char ->
            // Printable if not a control character
            !char.isISOControl() || char == ' '
        }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if all characters are in the ASCII range (0-127).
     * 
     * ASCII includes standard English letters, digits, and punctuation.
     * Returns false for empty strings and Unicode characters.
     * 
     * @param args [0] string to test
     * @return true if all characters are ASCII, false otherwise
     * 
     * Example:
     * ```
     * isAscii("Hello") → true
     * isAscii("Hello123") → true
     * isAscii("Ñoño") → false (Ñ is not ASCII)
     * isAscii("你好") → false (Chinese characters)
     * isAscii("") → false
     * ```
     */
    fun isAscii(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isAscii() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { it.code <= 127 }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if the string contains only hexadecimal digits (0-9, A-F, a-f).
     * 
     * @param args [0] string to test
     * @return true if all characters are hex digits, false otherwise
     * 
     * Example:
     * ```
     * isHexadecimal("1A2F") → true
     * isHexadecimal("ABCDEF") → true
     * isHexadecimal("123xyz") → false
     * isHexadecimal("0x1A2F") → false (has '0x' prefix)
     * isHexadecimal("") → false
     * ```
     */
    fun isHexadecimal(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("isHexadecimal() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        
        // Empty string returns false
        if (str.isEmpty()) {
            return UDM.Scalar(false)
        }
        
        val result = str.all { 
            it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f'
        }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if the string contains at least one alphabetic character.
     * 
     * @param args [0] string to test
     * @return true if any character is a letter, false otherwise
     * 
     * Example:
     * ```
     * hasAlpha("Hello123") → true
     * hasAlpha("123") → false
     * hasAlpha("123a") → true
     * hasAlpha("") → false
     * ```
     */
    fun hasAlpha(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("hasAlpha() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        val result = str.any { it.isLetter() }
        return UDM.Scalar(result)
    }
    
    /**
     * Returns true if the string contains at least one numeric digit.
     * 
     * @param args [0] string to test
     * @return true if any character is a digit, false otherwise
     * 
     * Example:
     * ```
     * hasNumeric("Hello123") → true
     * hasNumeric("Hello") → false
     * hasNumeric("a1") → true
     * hasNumeric("") → false
     * ```
     */
    fun hasNumeric(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("hasNumeric() requires 1 argument: string")
        }
        
        val str = args[0].asString()
        val result = str.any { it.isDigit() }
        return UDM.Scalar(result)
    }
}
