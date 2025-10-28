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
