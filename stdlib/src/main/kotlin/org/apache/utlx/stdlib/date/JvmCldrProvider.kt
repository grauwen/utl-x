// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/JvmCldrProvider.kt
package org.apache.utlx.stdlib.date

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM implementation of CLDR data provider
 *
 * Uses Java's built-in CLDR data via:
 * - java.text.DateFormatSymbols for localized month/day names
 * - java.text.DateFormat for locale-specific patterns
 * - java.util.Locale for BCP 47 locale resolution
 *
 * Data is cached for performance.
 */
class JvmCldrProvider : CldrDataProvider {

    private val cache = ConcurrentHashMap<String, Any>()

    override fun getMonthNames(locale: String, style: CldrDataProvider.MonthStyle): Array<String> {
        val cacheKey = "$locale:month:$style"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(cacheKey) {
            val loc = resolveLocale(locale)
            val symbols = DateFormatSymbols.getInstance(loc)

            when (style) {
                CldrDataProvider.MonthStyle.FULL -> {
                    // Java includes a 13th empty element for some calendars, take only 12
                    symbols.months.take(12).toTypedArray()
                }
                CldrDataProvider.MonthStyle.SHORT -> {
                    symbols.shortMonths.take(12).toTypedArray()
                }
                CldrDataProvider.MonthStyle.NARROW -> {
                    // Java doesn't have narrow month names, use first letter of full names
                    symbols.months.take(12).map { it.take(1) }.toTypedArray()
                }
            }
        } as Array<String>
    }

    override fun getDayNames(locale: String, style: CldrDataProvider.DayStyle): Array<String> {
        val cacheKey = "$locale:day:$style"
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(cacheKey) {
            val loc = resolveLocale(locale)
            val symbols = DateFormatSymbols.getInstance(loc)

            // Java uses Sunday=1, Monday=2, ..., Saturday=7
            // We want Monday=0, Tuesday=1, ..., Sunday=6
            val javaWeekdays = when (style) {
                CldrDataProvider.DayStyle.FULL -> symbols.weekdays
                CldrDataProvider.DayStyle.SHORT -> symbols.shortWeekdays
                CldrDataProvider.DayStyle.NARROW -> {
                    // Java doesn't have narrow day names, use first letter
                    symbols.weekdays.map { it.take(1) }.toTypedArray()
                }
            }

            // Reorder: [empty, Sun, Mon, Tue, Wed, Thu, Fri, Sat] → [Mon, Tue, Wed, Thu, Fri, Sat, Sun]
            // Skip index 0 (empty), take Mon-Sat (indices 2-7), then Sun (index 1)
            if (javaWeekdays.size >= 8) {
                (javaWeekdays.slice(2..7) + javaWeekdays[1]).toTypedArray()
            } else {
                // Fallback if array size is unexpected
                arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            }
        } as Array<String>
    }

    override fun getDatePattern(locale: String, style: String): String {
        val cacheKey = "$locale:pattern:$style"
        return cache.getOrPut(cacheKey) {
            val loc = resolveLocale(locale)
            val formatStyle = when (style.uppercase()) {
                "SHORT" -> DateFormat.SHORT
                "MEDIUM" -> DateFormat.MEDIUM
                "LONG" -> DateFormat.LONG
                "FULL" -> DateFormat.FULL
                else -> DateFormat.MEDIUM
            }

            val dateFormat = DateFormat.getDateInstance(formatStyle, loc)
            if (dateFormat is SimpleDateFormat) {
                dateFormat.toPattern()
            } else {
                // Fallback to ISO format
                "yyyy-MM-dd"
            }
        } as String
    }

    override fun hasLocale(locale: String): Boolean {
        val available = Locale.getAvailableLocales()
        return available.any {
            it.toLanguageTag() == locale ||
            it.language == locale.substringBefore('-')
        }
    }

    /**
     * Resolve BCP 47 locale tag to Java Locale
     *
     * Supports:
     * - Full tags: "nl-NL", "en-US", "en-GB"
     * - Language only: "nl", "en", "de"
     * - Fallback to en-US if not found
     */
    private fun resolveLocale(localeTag: String): Locale {
        return try {
            Locale.forLanguageTag(localeTag)
        } catch (e: Exception) {
            Locale.US // Default fallback
        }
    }
}
