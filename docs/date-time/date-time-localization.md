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

### **5. Project Repository Structure**
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
