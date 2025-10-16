// stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/RichDateFunctions.kt
package org.apache.utlx.stdlib.date

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import kotlinx.datetime.*
import kotlin.math.abs
import java.time.Instant as JavaInstant

/**
 * Rich date arithmetic and utility functions
 */
object RichDateFunctions {
    
    // Helper functions to convert between java.time.Instant and kotlinx.datetime.Instant
    private fun toKotlinxInstant(javaInstant: JavaInstant): kotlinx.datetime.Instant {
        return kotlinx.datetime.Instant.fromEpochSeconds(javaInstant.epochSecond, javaInstant.nano)
    }
    
    private fun toJavaInstant(kotlinxInstant: kotlinx.datetime.Instant): JavaInstant {
        return JavaInstant.ofEpochSecond(kotlinxInstant.epochSeconds, kotlinxInstant.nanosecondsOfSecond.toLong())
    }
    
    // ========== PERIOD CALCULATIONS ==========
    
    /**
     * Add weeks to date
     * Usage: addWeeks(now(), 2) => 2 weeks from now
     */
    fun addWeeks(args: List<UDM>): UDM {
        requireArgs(args, 2, "addWeeks")
        val date = extractDateTime(args[0])
        val weeks = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(weeks * 7, DateTimeUnit.DAY, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    /**
     * Add quarters to date
     * Usage: addQuarters(now(), 1) => 3 months from now
     */
    fun addQuarters(args: List<UDM>): UDM {
        requireArgs(args, 2, "addQuarters")
        val date = extractDateTime(args[0])
        val quarters = args[1].asNumber().toInt()
        
        val result = toKotlinxInstant(date).plus(quarters * 3, DateTimeUnit.MONTH, TimeZone.UTC)
        return UDM.DateTime(toJavaInstant(result))
    }
    
    /**
     * Difference in weeks between two dates
     * Usage: diffWeeks(date1, date2) => 2.5
     */
    fun diffWeeks(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffWeeks")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val diff = kotlinxDate2.minus(kotlinxDate1)
        val weeks = diff.inWholeDays / 7.0
        
        return UDM.Scalar(weeks)
    }
    
    /**
     * Difference in months between two dates (approximate)
     * Usage: diffMonths(date1, date2) => 3.5
     */
    fun diffMonths(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffMonths")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val localDate1 = kotlinxDate1.toLocalDateTime(TimeZone.UTC).date
        val localDate2 = kotlinxDate2.toLocalDateTime(TimeZone.UTC).date
        
        val yearDiff = localDate2.year - localDate1.year
        val monthDiff = localDate2.monthNumber - localDate1.monthNumber
        val dayDiff = localDate2.dayOfMonth - localDate1.dayOfMonth
        
        val totalMonths = yearDiff * 12 + monthDiff + (dayDiff / 30.0)
        
        return UDM.Scalar(totalMonths)
    }
    
    /**
     * Difference in years between two dates
     * Usage: diffYears(date1, date2) => 2.5
     */
    fun diffYears(args: List<UDM>): UDM {
        requireArgs(args, 2, "diffYears")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val localDate1 = kotlinxDate1.toLocalDateTime(TimeZone.UTC).date
        val localDate2 = kotlinxDate2.toLocalDateTime(TimeZone.UTC).date
        
        val yearDiff = localDate2.year - localDate1.year
        val monthDiff = localDate2.monthNumber - localDate1.monthNumber
        
        val totalYears = yearDiff + (monthDiff / 12.0)
        
        return UDM.Scalar(totalYears)
    }
    
    // ========== START/END OF PERIOD ==========
    
    /**
     * Get start of day
     * Usage: startOfDay(now()) => 2025-10-14T00:00:00
     */
    fun startOfDay(args: List<UDM>): UDM {
        requireArgs(args, 1, "startOfDay")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        val startOfDay = LocalDateTime(localDate.date, LocalTime(0, 0, 0))
        
        return UDM.DateTime(toJavaInstant(startOfDay.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get end of day
     * Usage: endOfDay(now()) => 2025-10-14T23:59:59.999
     */
    fun endOfDay(args: List<UDM>): UDM {
        requireArgs(args, 1, "endOfDay")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC)
        val endOfDay = LocalDateTime(localDate.date, LocalTime(23, 59, 59, 999_999_999))
        
        return UDM.DateTime(toJavaInstant(endOfDay.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get start of week (Monday)
     * Usage: startOfWeek(now())
     */
    fun startOfWeek(args: List<UDM>): UDM {
        requireArgs(args, 1, "startOfWeek")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val dayOfWeek = localDate.dayOfWeek.ordinal // Monday = 0
        val startOfWeek = localDate.minus(dayOfWeek, DateTimeUnit.DAY)
        val startOfWeekDateTime = LocalDateTime(startOfWeek, LocalTime(0, 0, 0))
        
        return UDM.DateTime(toJavaInstant(startOfWeekDateTime.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get end of week (Sunday)
     * Usage: endOfWeek(now())
     */
    fun endOfWeek(args: List<UDM>): UDM {
        requireArgs(args, 1, "endOfWeek")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val dayOfWeek = localDate.dayOfWeek.ordinal // Monday = 0
        val daysToSunday = 6 - dayOfWeek
        val endOfWeek = localDate.plus(daysToSunday, DateTimeUnit.DAY)
        val endOfWeekDateTime = LocalDateTime(endOfWeek, LocalTime(23, 59, 59, 999_999_999))
        
        return UDM.DateTime(toJavaInstant(endOfWeekDateTime.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get start of month
     * Usage: startOfMonth(now()) => 2025-10-01T00:00:00
     */
    fun startOfMonth(args: List<UDM>): UDM {
        requireArgs(args, 1, "startOfMonth")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val firstDay = LocalDate(localDate.year, localDate.month, 1)
        val startOfMonth = LocalDateTime(firstDay, LocalTime(0, 0, 0))
        
        return UDM.DateTime(toJavaInstant(startOfMonth.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get end of month
     * Usage: endOfMonth(now()) => 2025-10-31T23:59:59
     */
    fun endOfMonth(args: List<UDM>): UDM {
        requireArgs(args, 1, "endOfMonth")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val lastDayOfMonth = localDate.month.length(isLeapYear(localDate.year))
        val lastDay = LocalDate(localDate.year, localDate.month, lastDayOfMonth)
        val endOfMonth = LocalDateTime(lastDay, LocalTime(23, 59, 59, 999_999_999))
        
        return UDM.DateTime(toJavaInstant(endOfMonth.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get start of year
     * Usage: startOfYear(now()) => 2025-01-01T00:00:00
     */
    fun startOfYear(args: List<UDM>): UDM {
        requireArgs(args, 1, "startOfYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val firstDay = LocalDate(localDate.year, 1, 1)
        val startOfYear = LocalDateTime(firstDay, LocalTime(0, 0, 0))
        
        return UDM.DateTime(toJavaInstant(startOfYear.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get end of year
     * Usage: endOfYear(now()) => 2025-12-31T23:59:59
     */
    fun endOfYear(args: List<UDM>): UDM {
        requireArgs(args, 1, "endOfYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val lastDay = LocalDate(localDate.year, 12, 31)
        val endOfYear = LocalDateTime(lastDay, LocalTime(23, 59, 59, 999_999_999))
        
        return UDM.DateTime(toJavaInstant(endOfYear.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get start of quarter
     * Usage: startOfQuarter(now())
     */
    fun startOfQuarter(args: List<UDM>): UDM {
        requireArgs(args, 1, "startOfQuarter")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val quarterMonth = ((localDate.monthNumber - 1) / 3) * 3 + 1
        val firstDay = LocalDate(localDate.year, quarterMonth, 1)
        val startOfQuarter = LocalDateTime(firstDay, LocalTime(0, 0, 0))
        
        return UDM.DateTime(toJavaInstant(startOfQuarter.toInstant(TimeZone.UTC)))
    }
    
    /**
     * Get end of quarter
     * Usage: endOfQuarter(now())
     */
    fun endOfQuarter(args: List<UDM>): UDM {
        requireArgs(args, 1, "endOfQuarter")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val quarterMonth = ((localDate.monthNumber - 1) / 3) * 3 + 3
        val lastDayOfMonth = Month(quarterMonth).length(isLeapYear(localDate.year))
        val lastDay = LocalDate(localDate.year, quarterMonth, lastDayOfMonth)
        val endOfQuarter = LocalDateTime(lastDay, LocalTime(23, 59, 59, 999_999_999))
        
        return UDM.DateTime(toJavaInstant(endOfQuarter.toInstant(TimeZone.UTC)))
    }
    
    // ========== DATE INFORMATION ==========
    
    /**
     * Get day of week (1=Monday, 7=Sunday)
     * Usage: dayOfWeek(now()) => 2 (Tuesday)
     */
    fun dayOfWeek(args: List<UDM>): UDM {
        requireArgs(args, 1, "dayOfWeek")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val dayOfWeek = localDate.dayOfWeek.ordinal + 1 // Monday = 1
        
        return UDM.Scalar(dayOfWeek.toDouble())
    }
    
    /**
     * Get day of week name
     * Usage: dayOfWeekName(now()) => "Tuesday"
     */
    fun dayOfWeekName(args: List<UDM>): UDM {
        requireArgs(args, 1, "dayOfWeekName")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        return UDM.Scalar(localDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() })
    }
    
    /**
     * Get day of year (1-365/366)
     * Usage: dayOfYear(now()) => 287
     */
    fun dayOfYear(args: List<UDM>): UDM {
        requireArgs(args, 1, "dayOfYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        return UDM.Scalar(localDate.dayOfYear.toDouble())
    }
    
    /**
     * Get week of year (ISO week)
     * Usage: weekOfYear(now()) => 42
     */
    fun weekOfYear(args: List<UDM>): UDM {
        requireArgs(args, 1, "weekOfYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        
        // Simple week calculation (ISO week would be more complex)
        val firstDayOfYear = LocalDate(localDate.year, 1, 1)
        val dayOfYear = localDate.dayOfYear
        val weekNumber = ((dayOfYear + firstDayOfYear.dayOfWeek.ordinal) / 7) + 1
        
        return UDM.Scalar(weekNumber.toDouble())
    }
    
    /**
     * Get quarter (1-4)
     * Usage: quarter(now()) => 4
     */
    fun quarter(args: List<UDM>): UDM {
        requireArgs(args, 1, "quarter")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val quarter = ((localDate.monthNumber - 1) / 3) + 1
        
        return UDM.Scalar(quarter.toDouble())
    }
    
    /**
     * Get month name
     * Usage: monthName(now()) => "October"
     */
    fun monthName(args: List<UDM>): UDM {
        requireArgs(args, 1, "monthName")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        return UDM.Scalar(localDate.month.name.lowercase().replaceFirstChar { it.uppercase() })
    }
    
    /**
     * Check if leap year
     * Usage: isLeapYear(now()) => false
     */
    fun isLeapYearFunc(args: List<UDM>): UDM {
        requireArgs(args, 1, "isLeapYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        return UDM.Scalar(isLeapYear(localDate.year))
    }
    
    /**
     * Get days in month
     * Usage: daysInMonth(now()) => 31
     */
    fun daysInMonth(args: List<UDM>): UDM {
        requireArgs(args, 1, "daysInMonth")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val days = localDate.month.length(isLeapYear(localDate.year))
        
        return UDM.Scalar(days.toDouble())
    }
    
    /**
     * Get days in year
     * Usage: daysInYear(now()) => 365
     */
    fun daysInYear(args: List<UDM>): UDM {
        requireArgs(args, 1, "daysInYear")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val days = if (isLeapYear(localDate.year)) 366 else 365
        
        return UDM.Scalar(days.toDouble())
    }
    
    // ========== DATE COMPARISONS ==========
    
    /**
     * Check if date is before another date
     * Usage: isBefore(date1, date2) => true
     */
    fun isBefore(args: List<UDM>): UDM {
        requireArgs(args, 2, "isBefore")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        return UDM.Scalar(date1 < date2)
    }
    
    /**
     * Check if date is after another date
     * Usage: isAfter(date1, date2) => false
     */
    fun isAfter(args: List<UDM>): UDM {
        requireArgs(args, 2, "isAfter")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        return UDM.Scalar(date1 > date2)
    }
    
    /**
     * Check if dates are same day
     * Usage: isSameDay(date1, date2) => true
     */
    fun isSameDay(args: List<UDM>): UDM {
        requireArgs(args, 2, "isSameDay")
        val date1 = extractDateTime(args[0])
        val date2 = extractDateTime(args[1])
        
        val kotlinxDate1 = toKotlinxInstant(date1)
        val kotlinxDate2 = toKotlinxInstant(date2)
        val localDate1 = kotlinxDate1.toLocalDateTime(TimeZone.UTC).date
        val localDate2 = kotlinxDate2.toLocalDateTime(TimeZone.UTC).date
        
        return UDM.Scalar(localDate1 == localDate2)
    }
    
    /**
     * Check if date is between two dates (inclusive)
     * Usage: isBetween(date, startDate, endDate) => true
     */
    fun isBetween(args: List<UDM>): UDM {
        requireArgs(args, 3, "isBetween")
        val date = extractDateTime(args[0])
        val startDate = extractDateTime(args[1])
        val endDate = extractDateTime(args[2])
        
        return UDM.Scalar(date >= startDate && date <= endDate)
    }
    
    /**
     * Check if date is today
     * Usage: isToday(date) => true
     */
    fun isToday(args: List<UDM>): UDM {
        requireArgs(args, 1, "isToday")
        val date = extractDateTime(args[0])
        
        val now = Clock.System.now()
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val today = now.toLocalDateTime(TimeZone.UTC).date
        
        return UDM.Scalar(localDate == today)
    }
    
    /**
     * Check if date is weekend (Saturday or Sunday)
     * Usage: isWeekend(date) => false
     */
    fun isWeekend(args: List<UDM>): UDM {
        requireArgs(args, 1, "isWeekend")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val dayOfWeek = localDate.dayOfWeek
        
        return UDM.Scalar(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)
    }
    
    /**
     * Check if date is weekday (Monday-Friday)
     * Usage: isWeekday(date) => true
     */
    fun isWeekday(args: List<UDM>): UDM {
        requireArgs(args, 1, "isWeekday")
        val date = extractDateTime(args[0])
        
        val localDate = toKotlinxInstant(date).toLocalDateTime(TimeZone.UTC).date
        val dayOfWeek = localDate.dayOfWeek
        
        return UDM.Scalar(dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY)
    }
    
    // ========== AGE CALCULATIONS ==========
    
    /**
     * Calculate age in years from birthdate
     * Usage: age(birthDate) => 30
     */
    fun age(args: List<UDM>): UDM {
        requireArgs(args, 1, "age")
        val birthDate = extractDateTime(args[0])
        
        val now = Clock.System.now()
        val kotlinxBirthDate = toKotlinxInstant(birthDate)
        val birthLocalDate = kotlinxBirthDate.toLocalDateTime(TimeZone.UTC).date
        val nowLocalDate = now.toLocalDateTime(TimeZone.UTC).date
        
        var age = nowLocalDate.year - birthLocalDate.year
        
        // Adjust if birthday hasn't occurred this year
        if (nowLocalDate.monthNumber < birthLocalDate.monthNumber ||
            (nowLocalDate.monthNumber == birthLocalDate.monthNumber && 
             nowLocalDate.dayOfMonth < birthLocalDate.dayOfMonth)) {
            age--
        }
        
        return UDM.Scalar(age.toDouble())
    }
    
    // Helper function
    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
    
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun extractDateTime(udm: UDM): JavaInstant = when (udm) {
        is UDM.DateTime -> udm.instant
        else -> throw FunctionArgumentException("Expected datetime value")
    }
    
    private fun UDM.asNumber(): Double = when (this) {
        is UDM.Scalar -> {
            val v = value
            when (v) {
                is Number -> v.toDouble()
                else -> throw FunctionArgumentException("Expected number value")
            }
        }
        else -> throw FunctionArgumentException("Expected number value")
    }
}
