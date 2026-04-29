= CSV Transformations

== CSV in UTL-X
// - Parsed as array of objects (headers become property names)
// - Row access: $input[0], $input[1]
// - Column access: $input[0].CustomerName

== CSV Options
// - Delimiter: comma (default), semicolon, pipe, tab
// - Headers: true (default) / false
// - BOM: byte order mark handling
// - Regional format: USA, European, French, Swiss
// - Decimal places and thousands separators

== Reading CSV
// - Auto-header detection
// - Type inference: numbers, dates, booleans
// - Handling quoted fields and escaped delimiters
// - Empty fields and null handling

== Writing CSV
// - Object array → CSV rows
// - Custom column ordering
// - Header generation
// - Regional number formatting (1,234.56 vs 1.234,56)

== CSV-Specific Functions
// - parseCsv: parse CSV string within transformation
// - renderCsv: serialize to CSV string
// - CSV dialect configuration

== Common CSV Patterns
// - Excel export → JSON API import
// - Database dump → normalized JSON
// - Log file processing (pipe-delimited)
// - Bank statement (semicolon-delimited, European numbers)
// - Multi-format: CSV input → XML output (invoice generation)

== Regional Formats
// - USA: comma decimal, period thousands (1,234.56)
// - European: period decimal, comma thousands (1.234,56)
// - French: comma decimal, space thousands (1 234,56)
// - Swiss: period decimal, apostrophe thousands (1'234.56)
// - How to configure per transformation
