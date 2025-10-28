# Tutorial Examples

This directory contains real-world examples from interactive UTL-X sessions and tutorials. These tests demonstrate practical transformation patterns and serve as learning resources.

## Purpose

- **Learning Resource**: Show complete, working examples of common transformation patterns
- **Interactive Session Captures**: Preserve examples from user interactions and tutorials
- **Real-World Patterns**: Demonstrate practical use cases rather than edge cases
- **Documentation Support**: Examples that can be referenced in tutorials and guides

## Tests

### csv_orders_grouping.yaml

**Pattern**: Convert flat CSV order data into hierarchical JSON with grouping (comma-delimited)

**What it demonstrates**:
- CSV parsing with headers (comma delimiter)
- `groupBy` function to aggregate related rows
- Let bindings for cleaner code
- Pipe operator chaining
- Map transformations on nested arrays
- Aggregation with `sum` for order totals

**Real-world use case**: E-commerce order processing where each order spans multiple CSV rows (one per item)

**Key concepts**:
```utlx
input csv {
  headers: true,
  delimiter: ","
}
---
groupBy(row => row.order_id)  # Groups by order ID
|> map(group => {              # Transform each group
     let orderId = group.key,  # Extract key and value
     let rows = group.value,
     ...
   })
```

### csv_orders_grouping_semicolon.yaml

**Pattern**: Same transformation as above, but with semicolon-delimited CSV

**What it demonstrates**:
- CSV parsing with semicolon delimiter (European format)
- Identical transformation logic works with different delimiters
- UTL-X flexibility in handling regional CSV formats
- Same output despite different input format

**Real-world use case**: Processing CSV files from European systems where semicolon is used as delimiter (common when comma is the decimal separator)

**Key difference**:
```utlx
input csv {
  headers: true,
  delimiter: ";"  # Semicolon instead of comma
}
```

### csv_orders_grouping_pipe.yaml

**Pattern**: Same transformation with pipe-delimited CSV

**What it demonstrates**:
- CSV parsing with pipe delimiter
- Pipe delimiter useful for log files and system exports
- Less likely to conflict with data content
- Common in Unix/Linux system tools

**Real-world use case**: Processing system logs or exports where pipe delimiter is standard (avoids conflicts with commas in data)

**Key difference**:
```utlx
input csv {
  headers: true,
  delimiter: "|"  # Pipe delimiter
}
```

### csv_orders_grouping_tab.yaml

**Pattern**: Same transformation with tab-delimited CSV (TSV format)

**What it demonstrates**:
- CSV parsing with tab delimiter (TSV - Tab-Separated Values)
- Tab delimiter standard in data science and databases
- Avoids most escaping issues
- Excel and spreadsheet application compatibility

**Real-world use case**: Processing database exports or data science datasets that use TSV format

**Key difference**:
```utlx
input csv {
  headers: true,
  delimiter: "\t"  # Tab delimiter
}
```

**Note**: TSV is particularly useful because:
- Tab rarely appears in normal data
- No complex escaping rules needed
- Standard export format for PostgreSQL, MySQL, and other databases
- Native support in Excel, Google Sheets, and data analysis tools

### json_to_csv_with_headers.yaml

**Pattern**: JSON to CSV conversion with headers enabled

**What it demonstrates**:
- CSV output with headers (default behavior)
- Headers automatically extracted from JSON object keys
- Comma delimiter (default)
- Numeric values preserved without unnecessary quoting

**Real-world use case**: Exporting JSON data for spreadsheet applications or legacy systems that require CSV

**Key concepts**:
```utlx
output csv {
  headers: true,
  delimiter: ","
}
```

### json_to_csv_no_headers.yaml

**Pattern**: JSON to CSV conversion with headers disabled

**What it demonstrates**:
- CSV output without header row
- Only data rows in output
- Useful for appending to existing CSV files
- Field order consistency maintained

**Real-world use case**: Appending data to an existing CSV file where headers are already present

### json_to_csv_semicolon_delimiter.yaml

**Pattern**: JSON to CSV with semicolon delimiter (European format)

**What it demonstrates**:
- Custom delimiter specification
- Semicolon delimiter for European CSV format
- Headers included with custom delimiter
- Same transformation logic, different output format

**Real-world use case**: Creating CSV files for European systems where comma is used as decimal separator

### json_to_csv_pipe_delimiter.yaml

**Pattern**: JSON to CSV with pipe delimiter

**What it demonstrates**:
- Pipe delimiter for alternative CSV format
- Useful when data may contain commas and semicolons
- Common in system logs and technical exports
- Less ambiguous than comma or semicolon

**Real-world use case**: System integration where pipe-delimited format is standard

### json_to_csv_semicolon_no_headers.yaml

**Pattern**: Combined options - semicolon delimiter with no headers

**What it demonstrates**:
- Multiple output options combined
- Delimiter and headers options are independent
- European format data-only export
- Option composition

**Real-world use case**: Appending to European-format CSV files

### json_to_csv_with_special_chars.yaml

**Pattern**: CSV escaping with special characters

**What it demonstrates**:
- Proper RFC 4180 CSV field escaping
- Fields with commas are automatically quoted
- Internal quotes are doubled
- Handles apostrophes and special characters correctly
- Standards compliance

**Real-world use case**: Exporting data with complex text content (names with commas, descriptions with quotes)

**Key escaping rules**:
- Fields containing delimiter → quoted
- Fields containing quotes → quoted, internal quotes doubled
- Fields with newlines → quoted
- RFC 4180 standard compliance

## Regional Number Format Tests

These tests demonstrate handling of regional number formatting conventions where decimal and thousands separators vary by locale.

### csv_regional_usa_input.yaml

**Pattern**: Parse CSV with USA/UK/Asia regional number format

**What it demonstrates**:
- Comma thousands separator, period decimal separator
- Parsing numbers like "1,234.56" and "123,456.00"
- Using standard `parseNumber()` which auto-strips comma thousands
- Alternatively, explicit `parseUSNumber()` for clarity
- JSON output with standard numeric representation

**Real-world use case**: Processing financial data from USA, UK, India, or Asian sources

**Regional format**:
- **Delimiter**: Comma (`,`)
- **Decimal separator**: Period (`.`)
- **Thousands separator**: Comma (`,`) - requires quoting in CSV
- **Example**: 1,234.56 represents one thousand two hundred thirty-four point five six

### csv_regional_europe_input.yaml

**Pattern**: Parse CSV with European/Latin American regional number format

**What it demonstrates**:
- Period thousands separator, comma decimal separator
- Parsing numbers like "1.234,56" and "123.456,00"
- Using `parseEUNumber()` for clean European format conversion
- Semicolon CSV delimiter avoids conflict with comma decimals
- JSON output with standard numeric representation

**Real-world use case**: Processing financial data from Germany, France, Spain, Italy, Brazil, Argentina

**Regional format**:
- **Delimiter**: Semicolon (`;`)
- **Decimal separator**: Comma (`,`)
- **Thousands separator**: Period (`.`)
- **Example**: 1.234,56 represents one thousand two hundred thirty-four comma five six

**Key function**: `parseEUNumber(value)` - Removes period thousands, converts comma to period decimal

### csv_regional_swiss_input.yaml

**Pattern**: Parse CSV with Swiss regional number format

**What it demonstrates**:
- Apostrophe thousands separator, period decimal separator
- Parsing numbers like "1'234.56" and "123'456.00"
- Using `parseSwissNumber()` for Swiss format conversion
- Semicolon CSV delimiter with period decimals
- JSON output with standard numeric representation

**Real-world use case**: Processing financial data from Switzerland and Liechtenstein

**Regional format**:
- **Delimiter**: Semicolon (`;`)
- **Decimal separator**: Period (`.`)
- **Thousands separator**: Apostrophe (`'`)
- **Example**: 1'234.56 represents one thousand two hundred thirty-four point five six

**Key function**: `parseSwissNumber(value)` - Removes apostrophe thousands separators

### csv_regional_usa_output.yaml

**Pattern**: Output CSV with USA/UK/Asia delimiter format

**What it demonstrates**:
- Comma delimiter with standard numeric output
- Numbers output without thousands separators
- Period decimal separator (standard interchange format)
- Headers included

**Real-world use case**: Creating CSV files for USA, UK, Asian systems

### csv_regional_europe_output.yaml

**Pattern**: Output CSV with European delimiter format

**What it demonstrates**:
- Semicolon delimiter for European compatibility
- Numbers output with period decimal (interchange format)
- Note: For true European display format, comma decimals would be needed
- Current output uses standard numeric format for data compatibility

**Real-world use case**: Creating CSV files for European systems (Germany, France, Spain, Italy)

### csv_regional_swiss_output.yaml

**Pattern**: Output CSV with Swiss delimiter format

**What it demonstrates**:
- Semicolon delimiter common in Swiss exports
- Numbers output with period decimal (standard format)
- Note: For true Swiss display format, apostrophe thousands would be needed
- Current output uses standard numeric format for data interchange

**Real-world use case**: Creating CSV files for Swiss systems

## Regional Number Parsing Functions

UTL-X provides three specialized functions for parsing regional number formats:

- **`parseUSNumber(value)`** - Parse USA/UK/Asia format (comma thousands, period decimal)
  - Example: `parseUSNumber("1,234.56")` → `1234.56`
  - Regions: USA, UK, India, most of Asia

- **`parseEUNumber(value)`** - Parse European/Latin American format (period thousands, comma decimal)
  - Example: `parseEUNumber("1.234,56")` → `1234.56`
  - Regions: Germany, France, Spain, Italy, Brazil, Argentina

- **`parseSwissNumber(value)`** - Parse Swiss format (apostrophe thousands, period decimal)
  - Example: `parseSwissNumber("1'234.56")` → `1234.56`
  - Regions: Switzerland, Liechtenstein

All functions convert to standard numeric representation (period decimal) for internal UDM storage and output.

## Adding New Tutorial Examples

When adding examples from tutorials or interactive sessions:

1. Use descriptive names that reflect the pattern (e.g., `csv_orders_grouping.yaml`)
2. Include comprehensive metadata:
   - Set `source` to the session or tutorial identifier
   - Add detailed `notes` explaining what the example demonstrates
   - Tag with relevant concepts (`groupBy`, `aggregation`, etc.)
3. Keep examples focused on one main pattern
4. Ensure examples use real-world data structures
5. Add explanatory comments in the transformation code

## Running Tests

Run all tutorial examples:
```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py tutorial-examples
```

Run a specific example:
```bash
python3 runners/cli-runner/simple-runner.py tutorial-examples csv_orders_grouping
```
