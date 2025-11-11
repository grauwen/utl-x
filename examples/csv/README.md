# UTLX CSV Examples

This directory contains 36 real-world business examples demonstrating CSV transformations with UTLX.

## Examples Overview

### Examples 1-3: Default CSV Settings (Comma delimiter, Headers)
- **01-employee-roster** - Employee management with departments and salaries
- **02-sales-transactions** - E-commerce transaction processing
- **03-product-inventory** - Warehouse inventory tracking with stock levels

### Examples 4-8: Different Delimiters
- **04-customer-orders-semicolon** (`;`) - European customer orders
- **05-server-logs-tab** (`\t`) - System log analysis
- **06-database-metrics-pipe** (`|`) - Database performance monitoring
- **07-shipping-manifest-semicolon** (`;`) - Logistics and tracking
- **08-iot-sensor-data-tab** (`\t`) - IoT telemetry and monitoring

### Examples 9-12: CSV with BOM (UTF-8 Byte Order Mark)
- **09-customer-feedback-bom** - Product review analysis
- **10-vendor-contacts-bom** - International vendor directory
- **11-project-tasks-bom** - Project management tracking
- **12-sales-leads-bom** - Sales pipeline management

### Examples 13-16: CSV without Headers
- **13-sensor-readings-no-headers** - Environmental sensor data
- **14-stock-prices-no-headers** - Financial market data (OHLCV format)
- **15-network-traffic-no-headers** - Network monitoring logs
- **16-weather-observations-no-headers** - Meteorological data

### Examples 17-24: Regional Number Formats

#### Swiss Format (apostrophe thousands, period decimal)
- **17-swiss-financial-report** - Bank account balances (`125'450.75`)
- **20-swiss-invoice-data** - VAT invoices with Swiss formatting
- **23-swiss-real-estate** - Property listings and valuations

#### French Format (space thousands, comma decimal)
- **18-french-sales-report** - Product sales data (`1 299,99`)
- **21-french-budget-report** - Department budget analysis

#### European Format (period thousands, comma decimal, semicolon delimiter)
- **19-european-payroll-semicolon** - Multi-country payroll (`8.500,00`)
- **22-european-quarterly-sales-semicolon** - Regional sales reporting
- **24-european-expense-report-semicolon** - Expense tracking and reimbursement

### Examples 25-30: Other Formats to CSV

#### JSON to CSV
- **25-crm-customers-json-to-csv** - CRM system export (nested JSON to flat CSV)
- **27-api-response-json-to-csv** - GitHub-style API response to CSV report
- **29-web-analytics-json-to-csv** - Web analytics dashboard data export

#### XML to CSV
- **26-product-catalog-xml-to-csv** - Product catalog to price list
- **28-order-history-xml-to-csv** - Order history for accounting (line-item detail)

#### YAML to CSV
- **30-infrastructure-yaml-to-csv** - Infrastructure config to inventory report

### Examples 31-36: Metadata to Schema (Integration Architecture)

These examples demonstrate real-world integration scenarios where architects deliver metadata in CSV format and integration specialists generate schemas (JSON Schema or XSD).

#### CSV to JSON Schema (JSCH)
- **31-rest-api-metadata-csv-to-jsch** - REST API endpoint definitions to JSON Schema
- **32-database-schema-csv-to-jsch** - Database table metadata to JSON Schema
- **33-sap-rfc-metadata-csv-to-jsch** - SAP RFC function (BAPI_SALESORDER_CREATEFROMDAT2) to JSON Schema

#### CSV to XSD
- **34-edi-x12-850-csv-to-xsd** - EDI X12 850 Purchase Order format to XML Schema
- **35-soap-service-contract-csv-to-xsd** - SOAP web service contract to XML Schema
- **36-b2b-invoice-format-csv-to-xsd** - B2B electronic invoice format to XML Schema

## CSV Parameters Demonstrated

### Delimiter Options
- `,` (comma) - Default, most common
- `;` (semicolon) - European standard
- `\t` (tab) - Tab-separated values
- `|` (pipe) - Alternative delimiter

### Header Options
- `headers: true` - CSV includes column headers (default)
- `headers: false` - No headers, data only

### Encoding & BOM
- UTF-8 BOM - Examples 9-12 demonstrate BOM handling
- Auto-detection - All other examples rely on automatic encoding detection

### Regional Number Formatting
- **Swiss**: `125'450.75` (apostrophe thousands separator)
- **French**: `1 299,99` (space thousands, comma decimal)
- **European**: `8.500,00` (period thousands, comma decimal)

## UTLX Header Syntax Examples

### Input CSV
```utlx
input csv                          # Default: headers=true, delimiter=","
input csv {headers: false}         # No headers
input csv {delimiter: ";"}         # Semicolon delimiter
input csv {delimiter: "\t"}        # Tab delimiter
```

### Output CSV
```utlx
output csv                                      # Default settings
output csv {headers: false}                     # No headers in output
output csv {delimiter: ";"}                     # Semicolon delimiter
output csv {delimiter: ";", headers: false}     # Both options
output csv {bom: true}                          # Include UTF-8 BOM
```

## Business Domains Covered

- **Human Resources**: Employee rosters, payroll, project tasks
- **Sales & Marketing**: Transactions, leads, customer feedback
- **Finance & Accounting**: Invoices, budgets, financial reports
- **E-commerce**: Orders, products, inventory
- **Logistics**: Shipping manifests, tracking
- **IT Operations**: Server logs, database metrics, network traffic, infrastructure
- **Analytics**: Web analytics, API data, repository statistics
- **Real Estate**: Property listings and valuations
- **IoT & Monitoring**: Sensor data, weather observations
- **Integration Architecture**: REST APIs, SOAP services, SAP RFC, EDI X12, B2B formats, database schemas

## Running the Examples

Each example consists of two files:
1. Input file (`.csv`, `.json`, `.xml`, or `.yaml`)
2. UTLX transformation (`.utlx`)

To run an example:
```bash
utlxd transform <example-name>.utlx --input <input-name>:<input-file>
```

For example:
```bash
utlxd transform 01-employee-roster.utlx --input employees:01-employee-roster.csv
```

## Notes

- Examples 1-3 use default settings for easy testing
- Examples with regional formats demonstrate real-world international data handling
- BOM examples (9-12) show proper handling of UTF-8 Byte Order Marks (common in Excel exports)
- No-header examples (13-16) demonstrate array-based access to CSV data
- Examples 25-30 show CSV as an export/reporting format from other data sources
- Examples 31-36 demonstrate metadata-driven schema generation for integration projects (architect delivers CSV metadata, integration specialist generates schemas)
