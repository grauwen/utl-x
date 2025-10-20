// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/CldrDataProvider.kt
package org.apache.utlx.stdlib.date

/**
 * CLDR (Common Locale Data Repository) data provider interface
 *
 * Provides locale-specific date/time formatting data including:
 * - Localized month names
 * - Localized day names
 * - Locale-specific date patterns
 *
 * Implementations should use platform-specific CLDR data sources:
 * - JVM: java.text.DateFormatSymbols
 * - JavaScript: Intl API
 * - Native: ICU library
 */
interface CldrDataProvider {

    /**
     * Get localized month names
     * @param locale BCP 47 locale tag (e.g., "nl-NL", "en-US")
     * @param style Month name style
     * @return Array of 12 month names (January=0)
     */
    fun getMonthNames(locale: String, style: MonthStyle): Array<String>

    /**
     * Get localized day names
     * @param locale BCP 47 locale tag
     * @param style Day name style
     * @return Array of 7 day names (Monday=0, Sunday=6)
     */
    fun getDayNames(locale: String, style: DayStyle): Array<String>

    /**
     * Get locale-specific date pattern for a given style
     * @param locale BCP 47 locale tag
     * @param style Pattern style (SHORT, MEDIUM, LONG, FULL)
     * @return Date pattern string (e.g., "dd-MM-yyyy", "MM/dd/yyyy")
     */
    fun getDatePattern(locale: String, style: String): String

    /**
     * Check if a locale is supported
     * @param locale BCP 47 locale tag
     * @return true if the locale is available
     */
    fun hasLocale(locale: String): Boolean

    /**
     * Month name display styles
     */
    enum class MonthStyle {
        /** Full month name (e.g., "January", "januari") */
        FULL,
        /** Abbreviated month name (e.g., "Jan", "jan") */
        SHORT,
        /** Narrow month name (e.g., "J") */
        NARROW
    }

    /**
     * Day name display styles
     */
    enum class DayStyle {
        /** Full day name (e.g., "Monday", "maandag") */
        FULL,
        /** Abbreviated day name (e.g., "Mon", "ma") */
        SHORT,
        /** Narrow day name (e.g., "M") */
        NARROW
    }
}
