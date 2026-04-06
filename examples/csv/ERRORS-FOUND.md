# CSV Examples - Errors Found and Fixes Needed

## Date: 2025-11-11

## Summary

During testing of the CSV examples (examples 01-36), we discovered systematic syntax errors in the generated UTLX transformations. All examples with lambda expressions need to be fixed.

## Root Cause

The examples were generated based on incorrect assumptions about UTLX lambda syntax:

1. **Used `fn()` wrapper**: `fn(e) => expression` instead of `e => expression`
2. **Quoted property names**: Used `"property"` instead of `property`
3. **Missing explicit parameters in some cases**: Used implicit field access

## Error Pattern 1: `fn()` Wrapper in Lambda Expressions

### Files Affected
All examples using `filter()` or `map()` with lambda expressions:
- 01-employee-roster.utlx ✅ FIXED
- 02-sales-transactions.utlx
- 03-product-inventory.utlx
- 04-customer-orders-semicolon.utlx
- (and many more...)

### Error
```utlx
filter($employees, fn(e) => e.Department == "Engineering")
map($employees, fn(emp) => { ... })
```

### Fix
```utlx
filter($employees, e => e.Department == "Engineering")
map($employees, emp => { ... })
```

### Parse Error Message
```
Parse exception: Expected ')' at Location(line=X, column=Y)
```

## Error Pattern 2: Quoted Property Names

### Files Affected
All examples with object literals

### Error
```utlx
{
  "company": "TechCorp",
  "totalEmployees": count($employees)
}
```

### Fix
```utlx
{
  company: "TechCorp",
  totalEmployees: count($employees)
}
```

### Note
This is a stylistic issue that works but is inconsistent with UTLX conventions.

## Error Pattern 3: Implicit Field Access (Context-Dependent)

### Files Affected
Some examples may have used implicit field access in `map()` functions

### Error (if it doesn't work in context)
```utlx
map($employees, {
  id: EmployeeID,
  name: FirstName
})
```

### Fix
```utlx
map($employees, emp => {
  id: emp.EmployeeID,
  name: emp.FirstName
})
```

### Runtime Error Message
```
Runtime error: Undefined variable: EmployeeID
```

## Examples Status

### ✅ Fixed
- 01-employee-roster.utlx

### ⚠️ Need Fixing
- 02-sales-transactions.utlx
- 03-product-inventory.utlx
- 04-customer-orders-semicolon.utlx
- 05-server-logs-tab.utlx
- 06-database-metrics-pipe.utlx
- 07-shipping-manifest-semicolon.utlx
- 08-iot-sensor-data-tab.utlx
- 09-customer-feedback-bom.utlx
- 10-vendor-contacts-bom.utlx
- 11-project-tasks-bom.utlx
- 12-sales-leads-bom.utlx
- 13-sensor-readings-no-headers.utlx
- 14-stock-prices-no-headers.utlx
- 15-network-traffic-no-headers.utlx
- 16-weather-observations-no-headers.utlx
- 17-swiss-financial-report.utlx
- 18-french-sales-report.utlx
- 19-european-payroll-semicolon.utlx
- 20-swiss-invoice-data.utlx
- 21-french-budget-report.utlx
- 22-european-quarterly-sales-semicolon.utlx
- 23-swiss-real-estate.utlx
- 24-european-expense-report-semicolon.utlx
- 25-crm-customers-json-to-csv.utlx
- 26-product-catalog-xml-to-csv.utlx
- 27-api-response-json-to-csv.utlx
- 28-order-history-xml-to-csv.utlx
- 29-web-analytics-json-to-csv.utlx
- 30-infrastructure-yaml-to-csv.utlx
- 31-rest-api-metadata-csv-to-jsch.utlx
- 32-database-schema-csv-to-jsch.utlx
- 33-sap-rfc-metadata-csv-to-jsch.utlx
- 34-edi-x12-850-csv-to-xsd.utlx
- 35-soap-service-contract-csv-to-xsd.utlx
- 36-b2b-invoice-format-csv-to-xsd.utlx

## Fix Strategy

### Automated Fix (Recommended)

Use regex find/replace to fix all files:

1. **Remove `fn()` wrapper**:
   ```bash
   find . -name "*.utlx" -exec sed -i '' 's/fn(\([^)]*\)) =>/\1 =>/g' {} \;
   ```

2. **Remove quotes from property names** (optional):
   ```bash
   find . -name "*.utlx" -exec sed -i '' 's/"\([a-zA-Z_][a-zA-Z0-9_]*\)":/\1:/g' {} \;
   ```

### Manual Fix (For Complex Cases)

For files with complex transformations or implicit field access issues:

1. Open file
2. Locate lambda expressions
3. Change `fn(param) =>` to `param =>`
4. Verify field references include parameter (e.g., `param.field`)
5. Test transformation with sample data

## Testing Procedure

After fixes, test each example:

```bash
#!/bin/bash
for file in *.utlx; do
  echo "Testing $file..."
  base=$(basename "$file" .utlx)
  input_file="${base}.csv"  # or .json, .xml, .yaml

  # Extract input name from UTLX header
  input_name=$(grep "^input " "$file" | awk '{print $2}')

  # Run transformation
  utlx transform "$file" --input "$input_name=$input_file" > "${base}-output.json" 2>&1

  if [ $? -eq 0 ]; then
    echo "✅ $file - OK"
  else
    echo "❌ $file - FAILED"
    cat "${base}-output.json"
  fi
done
```

## Validation Checklist

For each fixed file:
- [ ] No `fn()` wrapper in lambda expressions
- [ ] Lambda parameters explicitly used
- [ ] Property names unquoted (unless required)
- [ ] File parses without errors
- [ ] Transformation executes successfully
- [ ] Output matches expected format

## Documentation Updates Needed

1. ✅ Created syntax guidelines document
2. ✅ Created AI quick reference
3. ✅ Created LSP diagnostics schema
4. ⚠️ Update main README with link to guidelines
5. ⚠️ Add syntax warnings to example README
6. ⚠️ Create test suite for syntax validation

## Lessons Learned

1. **Always test generated code**: Generated examples should be validated before committing
2. **Use correct syntax from day one**: Understanding the language syntax is critical
3. **Reference working examples**: Base new code on known working patterns
4. **Automate validation**: Create CI/CD checks to catch syntax errors
5. **Document common errors**: This document serves as a reference for future work

## Next Steps

1. ⚠️ **URGENT**: Fix remaining 35 .utlx files
2. Create automated test suite
3. Add pre-commit hooks for syntax validation
4. Update IDE LSP to provide real-time error detection
5. Add syntax hints to Monaco editor

## Contact

For questions about these fixes, contact the development team or refer to:
- `/docs/utlx-syntax-guidelines.md`
- `/docs/utlx-ai-quick-reference.md`
- `/docs/lsp-diagnostics-schema.json`
