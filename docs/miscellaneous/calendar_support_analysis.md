# UTL-X Calendar System Support Analysis

## Executive Summary

**Current Status: ❌ NO explicit calendar system support**

UTL-X currently uses **Gregorian calendar only** (the default for Java's datetime APIs). While it has excellent locale support for formatting dates (month names, day names in different languages), it does **NOT** support alternative calendar systems like Buddhist, Chinese, Hebrew, Islamic, or Japanese calendars.

## Current Date/Time Capabilities

### ✅ What UTL-X HAS (Excellent!)

**67 Date/Time Functions** including:

1. **Locale-Aware Formatting**
   ```json
   {
     "us_long": "October 24, 2025",
     "french": "24 octobre 2025",
     "german": "24. Oktober 2025",
     "japanese": "2025年10月24日",
     "arabic": "24/10/2025"
   }
   ```

2. **Comprehensive Date Operations**
   - Current: `now()`, `currentDate()`, `currentTime()`
   - Parsing: `parseDate()` with auto-detection and custom patterns
   - Formatting: `formatDate()` with locale support
   - Arithmetic: `addDays()`, `addMonths()`, `addYears()`, `addWeeks()`, etc.
   - Comparisons: `isBefore()`, `isAfter()`, `isBetween()`, `isSameDay()`
   - Calculations: `diffDays()`, `diffMonths()`, `diffYears()`, `age()`
   - Validation: `isWeekday()`, `isWeekend()`, `isToday()`
   - Timezones: `convertTimezone()`, `toUTC()`, `fromUTC()`

3. **Rich Date Type System**
   - `UDM.Date` - Date only (no time)
   - `UDM.DateTime` - Date + time + timezone
   - `UDM.LocalDateTime` - Date + time (no timezone)
   - `UDM.Time` - Time only (no date)

### ❌ What UTL-X LACKS (Calendar Systems)

**No support for alternative calendar systems:**

- ❌ Buddhist calendar (Thai: พ.ศ. 2568 = 2025 CE)
- ❌ Chinese calendar (农历, 甲辰年)
- ❌ Hebrew calendar (תשפ״ה, 5785)
- ❌ Islamic/Hijri calendar (هجري, 1447)
- ❌ Japanese calendar (令和7年)
- ❌ Persian calendar (جلالی)
- ❌ Indian National calendar (शक संवत्)

## Technical Assessment

### Architecture Analysis

UTL-X uses:
- **kotlinx.datetime** for cross-platform datetime
- **java.time** (Java 8+ Date/Time API) for formatting
- **ISO-8601** as the underlying representation

**Problem:** None of these libraries expose calendar system (Chronology) APIs at the application level.

### Why Calendar Support is Missing

1. **kotlinx.datetime** doesn't expose `java.time.chrono` package
2. No imports of calendar system classes:
   - No `java.time.chrono.ThaiBuddhistDate`
   - No `java.time.chrono.HijrahDate`
   - No `java.time.chrono.JapaneseDate`
   - No `java.time.chrono.MinguoDate`

3. All date operations assume Gregorian calendar
4. No calendar parameter in any date function

## Comparison: Calendar Support in Other Tools

| System | Gregorian | Buddhist | Chinese | Hebrew | Islamic | Japanese |
|--------|-----------|----------|---------|--------|---------|----------|
| **UTL-X** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Java 8+** | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ |
| **ICU4J** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Joda-Time** | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ |
| **JavaScript Intl** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Python** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **DataWeave** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

**Note:** Java 8+ has built-in support for some calendars via `java.time.chrono` package.

## Real-World Impact

### Use Cases Requiring Alternative Calendars

1. **Thai Government Systems**
   - Thailand uses Buddhist calendar officially (BE = Buddhist Era)
   - Documents show "25 ตุลาคม พ.ศ. 2568" not "October 25, 2025"

2. **Islamic Banking & Finance**
   - Interest calculations based on Hijri calendar
   - Ramadan dates for business operations

3. **Hebrew Calendar Applications**
   - Jewish holidays (Rosh Hashanah, Yom Kippur, Passover)
   - Hebrew date conversions for religious applications

4. **Japanese Official Documents**
   - Government uses Japanese imperial calendar
   - "令和7年" (Reiwa 7) instead of "2025"

5. **Multi-Calendar Systems**
   - Iran uses Solar Hijri calendar (جلالی)
   - Ethiopia uses Ethiopian calendar (13 months!)
   - India has multiple regional calendars

### Current Workarounds

**Option 1: Manual Conversion (Error-Prone)**
```utlx
{
  // Buddhist calendar (Thailand): BE = CE + 543
  buddhist_year: toNumber(substring(formatDate($date, "yyyy"), 0, 4)) + 543,
  formatted: "พ.ศ. " + (toNumber(substring(formatDate($date, "yyyy"), 0, 4)) + 543)
}
```

**Problems:**
- Only handles year conversion
- No month/day adjustments for lunar calendars
- No holiday calculations
- No era handling (Japanese Reiwa, Heisei, etc.)

**Option 2: External Service**
```utlx
{
  // Call external calendar conversion API (not ideal)
  hijri_date: parseJson(httpGet("https://api.example.com/convert?date=" + $date))
}
```

**Problems:**
- Network dependency
- Performance overhead
- External service reliability

## Recommendations

### Short-Term: Document Limitations

Add clear documentation stating:
```markdown
**Calendar Support:** UTL-X currently supports Gregorian calendar only.
For alternative calendar systems (Buddhist, Islamic, Hebrew, etc.),
consider external calendar conversion services or libraries.
```

### Medium-Term: Add Calendar System Functions

Implement calendar conversion functions using Java's built-in support:

```kotlin
// Example implementation (Buddhist calendar)
@UTLXFunction(
    description = "Convert Gregorian date to Buddhist calendar",
    category = "Date"
)
fun toB uddhistCalendar(args: List<UDM>): UDM {
    val gregorianDate = args[0].asDate()
    val buddhistDate = java.time.chrono.ThaiBuddhistDate.from(gregorianDate)

    return UDM.Object(mapOf(
        "year" to UDM.Scalar(buddhistDate.get(ChronoField.YEAR)),
        "month" to UDM.Scalar(buddhistDate.monthValue),
        "day" to UDM.Scalar(buddhistDate.dayOfMonth),
        "era" to UDM.Scalar(buddhistDate.era.toString())
    ))
}
```

**Proposed Functions:**

1. **Conversion Functions**
   - `toGregorian(date, calendar)` - Convert from any calendar to Gregorian
   - `fromGregorian(date, calendar)` - Convert from Gregorian to any calendar
   - `toBuddhistCalendar(date)` - Specific Buddhist converter
   - `toHijriCalendar(date)` - Specific Islamic converter
   - `toJapaneseCalendar(date)` - Specific Japanese converter

2. **Formatting Functions**
   - `formatDate(date, pattern, locale, calendar)` - Add calendar parameter
   - `formatBuddhistDate(date, pattern, locale)` - Format in Buddhist calendar
   - `formatHijriDate(date, pattern, locale)` - Format in Hijri calendar

3. **Calendar-Aware Operations**
   - `addMonths(date, months, calendar)` - Add months in specific calendar
   - `diffMonths(date1, date2, calendar)` - Month difference in specific calendar

### Long-Term: Full Calendar System Support

Integrate ICU4J for comprehensive calendar support:

```gradle
dependencies {
    implementation("com.ibm.icu:icu4j:74.2")
}
```

**Benefits:**
- Support for ALL major calendar systems
- Accurate lunar calendar calculations
- Holiday calculations
- Calendar-aware date arithmetic
- Pattern-based formatting with calendar parameter

**Example Usage:**
```utlx
{
  gregorian: parseDate("2025-10-24"),

  // Convert to different calendars
  buddhist: toCalendar(parseDate("2025-10-24"), "buddhist"),
  islamic: toCalendar(parseDate("2025-10-24"), "islamic"),
  hebrew: toCalendar(parseDate("2025-10-24"), "hebrew"),
  japanese: toCalendar(parseDate("2025-10-24"), "japanese"),
  chinese: toCalendar(parseDate("2025-10-24"), "chinese"),

  // Format in different calendars
  thai_format: formatDate(parseDate("2025-10-24"), "dd MMMM yyyy", "th-TH", "buddhist"),
  arabic_format: formatDate(parseDate("2025-10-24"), "dd MMMM yyyy", "ar-SA", "islamic-umalqura"),
  japanese_format: formatDate(parseDate("2025-10-24"), "Gyy年M月d日", "ja-JP", "japanese")
}
```

**Expected Output:**
```json
{
  "thai_format": "24 ตุลาคม พ.ศ. 2568",
  "arabic_format": "21 ربيع الآخر 1447",
  "japanese_format": "令和7年10月24日"
}
```

## Implementation Estimate

### Phase 1: Basic Calendar Conversion (2-3 weeks)
- Add Java chrono package support
- Implement Buddhist, Islamic, Japanese calendar conversions
- Test with real-world date ranges

### Phase 2: Calendar-Aware Formatting (2-3 weeks)
- Extend `formatDate()` with calendar parameter
- Add calendar-specific format patterns
- Locale + calendar combinations

### Phase 3: ICU4J Integration (4-6 weeks)
- Full ICU4J integration
- All calendar systems support
- Holiday calculations
- Calendar-aware arithmetic
- Comprehensive testing

**Total Estimate:** 8-12 weeks for complete calendar system support

## Conclusion

### Current State: Limited but Excellent for Gregorian

UTL-X has **excellent date/time support** for Gregorian calendar:
- ✅ 67 date functions
- ✅ Locale-aware formatting
- ✅ Rich type system
- ✅ Timezone support
- ✅ Date arithmetic

### Missing: Alternative Calendar Systems

UTL-X **lacks support** for alternative calendar systems:
- ❌ No Buddhist calendar (Thai, Southeast Asia)
- ❌ No Islamic/Hijri calendar (Middle East, Islamic banking)
- ❌ No Hebrew calendar (Jewish applications)
- ❌ No Japanese calendar (Japanese government)
- ❌ No Chinese calendar (Chinese holidays)

### Recommendation

**For projects requiring alternative calendars:**
1. **Short-term:** Use external calendar conversion services
2. **Medium-term:** Implement basic calendar conversion functions
3. **Long-term:** Request ICU4J integration for full calendar support

**Priority:**
- **High** if targeting Thai, Middle Eastern, or East Asian markets
- **Medium** if supporting multi-cultural applications
- **Low** if only Western/Gregorian calendar required

---

**Assessment Date:** 2025-10-24
**UTL-X Version:** 1.0
**Functions Module:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/date/`
**Status:** Gregorian-only, no alternative calendar support
