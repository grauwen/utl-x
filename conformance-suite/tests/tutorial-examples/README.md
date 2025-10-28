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
