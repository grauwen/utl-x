# UTL-X DateTime Localization Standards

**Date:** October 20, 2025  
**Topic:** Date/Time Localization Standards for UTL-X Project  
**Reference:** [UTL-X GitHub Repository](https://github.com/grauwen/utl-x/)

---

## User Question

stdlib in UTL-X: DateTime might have localizations. Where is there a complete list of locations? Is there an ISO standard for different date/time localizations? How would that fit in the project?

---

## Response: Standards for Date/Time Localization

### 1. Locale Identification: BCP 47 (IETF Language Tags)

BCP 47 is the standardized code for identifying human languages and locales on the Internet. It combines subtags from ISO 639 (language codes), ISO 15924 (script codes), ISO 3166-1 (country codes), and UN M.49 (region codes).

**Format:** `language-script-region-variant`

**Examples:**
- `en-US` (English, United States)
- `es-419` (Latin American Spanish)
- `zh-Hant-TW` (Traditional Chinese, Taiwan)
- `de-DE` (German, Germany)
- `fr-CA` (French, Canada)

**Complete List Location:**

The IANA Language Subtag Registry contains all currently valid locale codes and is the authoritative source. You can access it at:

```
http://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
```

### 2. Locale Data: Unicode CLDR

The Unicode Common Locale Data Repository (CLDR) is the largest and most extensive standard repository of locale data available. It provides locale-specific patterns for formatting dates, times, timezones, numbers, currency values, and translations of names for languages, countries, currencies, months, weekdays, and more.

CLDR is maintained by a technical committee with employees from IBM, Apple, Google, Microsoft, and government organizations, and is used by all major software systems including all mobile phones.

**Access:**
- **GitHub:** https://github.com/unicode-org/cldr
- **Website:** https://cldr.unicode.org/
- **JSON data:** https://github.com/unicode-org/cldr-json

### 3. Date/Time Format: ISO 8601

ISO 8601 defines an internationally recognized format for date and time representation (YYYY-MM-DD, HH:MM:SS), but it doesn't cover localization or locale-specific formatting.

---

## How This Fits Into UTL-X

Based on the UTL-X proposal document and DataWeave's approach, here's how UTL-X should handle date/time localization:

### 1. Standard Library DateTime Functions (stdlib)

```utlx
// Basic formatting with locale support
function formatDate(
  date: DateTime, 
  format: String, 
  locale: String = "en-US"
): String

function parseDate(
  dateStr: String, 
  format: String, 
  locale: String = "en-US"
): DateTime

// Locale-aware formatting
function toLocaleDateString(
  date: DateTime,
  locale: String = "en-US",
  options: Object = {}
): String

function toLocaleTimeString(
  date: DateTime,
  locale: String = "en-US", 
  options: Object = {}
): String
```

### 2. Example Usage in UTL-X

```utlx
%utlx 1.0
input json
output json
---

{
  // Format date for different locales
  usDate: formatDate(input.eventDate, "MMMM dd, yyyy", "en-US"),
  // Output: "October 20, 2025"
  
  deDate: formatDate(input.eventDate, "dd. MMMM yyyy", "de-DE"),
  // Output: "20. Oktober 2025"
  
  frDate: formatDate(input.eventDate, "dd MMMM yyyy", "fr-FR"),
  // Output: "20 octobre 2025"
  
  // Locale-aware with options
  localizedFull: toLocaleDateString(input.eventDate, "es-419", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric"
  })
}
```

### 3. Integration Approach

For UTL-X implementation, recommended approach:

#### Use BCP 47 for locale identifiers
This is the industry standard used by DataWeave, JavaScript, Java, .NET, etc.

#### Leverage existing libraries:
- **JVM runtime:** Use `java.time` package with `Locale` class
- **JavaScript runtime:** Use `Intl.DateTimeFormat` API
- **Native runtime:** Integrate with ICU (International Components for Unicode)

#### Include CLDR data for comprehensive locale support:

```kotlin
// In the UTL-X runtime
class DateTimeFormatter {
    private val cldrData: CldrDataProvider
    
    fun format(
        dateTime: DateTime,
        pattern: String,
        locale: Locale
    ): String {
        val localizedPattern = cldrData.getDatePattern(locale, pattern)
        return formatWithPattern(dateTime, localizedPattern)
    }
}
```

#### Configuration in UTL-X:

```utlx
%utlx 1.0
input json
output json
config {
    defaultLocale: "en-US",
    timezone: "America/New_York",
    calendar: "gregory"  // or "buddhist", "islamic", etc.
}
---
// transformation here
```

### 4. Documentation Structure

Add to the UTL-X specification:

```markdown
## DateTime Localization (stdlib)

### Locale Support
- **Standard:** BCP 47 (RFC 5646)
- **Data Source:** Unicode CLDR v48+
- **Supported Locales:** 700+ language-region combinations

### Locale Identifier Format
`language[-script][-region][-variant]`

Examples:
- `en` - English (generic)
- `en-US` - English (United States)
- `zh-Hant-HK` - Chinese Traditional (Hong Kong)
- `de-AT` - German (Austria)

### DateTime Functions with Locale Support

#### formatDate()
Formats a date/time value according to a pattern and locale.

**Syntax:** `formatDate(date: DateTime, format: String, locale: String = "en-US"): String`

**Format Patterns (based on Unicode LDML):**
- `yyyy` - Year (4 digits)
- `MM` - Month (2 digits)
- `MMM` - Month abbreviated name (locale-aware)
- `MMMM` - Month full name (locale-aware)
- `dd` - Day (2 digits)
- `EEEE` - Weekday full name (locale-aware)
- `HH` - Hour (24-hour format)
- `hh` - Hour (12-hour format)
- `mm` - Minute
- `ss` - Second
- `a` - AM/PM marker (locale-aware)

**Complete Pattern Reference:** [Link to CLDR date patterns]

### Timezone Support
Based on IANA Time Zone Database (tzdata)

### Available Calendars
- `gregory` - Gregorian (default)
- `buddhist` - Buddhist calendar
- `chinese` - Chinese calendar
- `hebrew` - Hebrew calendar
- `islamic` - Islamic calendar
- `japanese` - Japanese calendar
```

### 5. Project Repository Structure

```
utlx-lang/
├── stdlib/
│   ├── datetime/
│   │   ├── formatting.utlx
│   │   ├── parsing.utlx
│   │   ├── timezone.utlx
│   │   └── calendar.utlx
│   └── README.md
├── resources/
│   └── cldr/              # CLDR data files (as dependency)
│       ├── locales/       # Locale-specific data
│       ├── timezones/     # Timezone data
│       └── calendars/     # Calendar data
└── docs/
    └── stdlib/
        └── datetime-localization.md
```

---

## Summary

BCP 47 is the most commonly used standard for locale specification, used by Windows, C#, Java, JavaScript, ICU, and HTML. Combined with CLDR's comprehensive locale data for formatting patterns, UTL-X would have a robust, industry-standard approach to date/time localization that's consistent with DataWeave and other modern transformation languages.

This approach ensures UTL-X can handle the full complexity of international date/time formatting while remaining compatible with existing standards and tooling.

---

## Key Resources

### Standards Documents
- **BCP 47 (RFC 5646):** https://www.rfc-editor.org/info/bcp47
- **ISO 8601:** https://www.iso.org/iso-8601-date-and-time-format.html
- **Unicode LDML (CLDR):** https://cldr.unicode.org/

### Data Repositories
- **IANA Language Subtag Registry:** http://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
- **CLDR GitHub:** https://github.com/unicode-org/cldr
- **CLDR JSON Data:** https://github.com/unicode-org/cldr-json

### Implementation References
- **Java Time API:** https://docs.oracle.com/javase/8/docs/api/java/time/format/package-summary.html
- **JavaScript Intl API:** https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl
- **ICU (International Components for Unicode):** https://icu.unicode.org/

---

## Next Steps for UTL-X Project

1. **Define stdlib datetime module interface** with locale support
2. **Choose CLDR version** to bundle with UTL-X runtime
3. **Implement locale-aware formatting** for each runtime (JVM, JavaScript, Native)
4. **Create comprehensive examples** showing different locale usage patterns
5. **Document supported locales** and their formatting conventions
6. **Add unit tests** for various locale combinations
7. **Consider performance** - CLDR data can be large, may need selective bundling

---

**Project Lead:** Ir. Marcel A. Grauwen  
**License:** Dual-licensed (AGPL-3.0 / Commercial)  
**Repository:** https://github.com/grauwen/utl-x/
