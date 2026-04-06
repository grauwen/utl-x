# Table Schema Examples for UTL-X

This directory contains 12 real-world business Table Schema examples demonstrating various industries and use cases. Each example includes a CSV data file (`.csv`) and a corresponding Frictionless Table Schema definition (`.tsch.json`).

## About Table Schema

Table Schema is a specification from the [Frictionless Data](https://frictionlessdata.io/) project for describing tabular data. A Table Schema defines:
- Field names, types, and formats
- Validation constraints (required, unique, enum, pattern, minimum/maximum, etc.)
- Primary keys (single and composite)
- Foreign key relationships (including self-referencing)
- Missing value representations
- Locale-aware number parsing (decimalChar, groupChar, bareNumber)
- Custom boolean representations (trueValues, falseValues)
- Semantic types (rdfType)

**Spec reference:** https://specs.frictionlessdata.io/table-schema/

## Example Catalog

| # | Example | Schema File | Data File | Description |
|---|---------|-------------|-----------|-------------|
| 1 | Employee Roster | `01_employee_roster.tsch.json` | `01_employee_roster.csv` | HR directory with yearmonth type and self-referencing foreignKey |
| 2 | Sales Transactions | `02_sales_transactions.tsch.json` | `02_sales_transactions.csv` | Retail POS with UUID format and customer foreignKey |
| 3 | Product Inventory | `03_product_inventory.tsch.json` | `03_product_inventory.csv` | Warehouse stock with trueValues/falseValues and bareNumber |
| 4 | Customer Contacts | `04_customer_contacts.tsch.json` | `04_customer_contacts.csv` | CRM contacts with rdfType and email/URI formats |
| 5 | Invoice Line Items | `05_invoice_line_items.tsch.json` | `05_invoice_line_items.csv` | European invoices with decimalChar/groupChar and strptime dates |
| 6 | Shipping Manifest | `06_shipping_manifest.tsch.json` | `06_shipping_manifest.csv` | Logistics with geopoint (default) and duration types |
| 7 | IoT Sensor Readings | `07_iot_sensor_readings.tsch.json` | `07_iot_sensor_readings.csv` | Telemetry with time, geopoint (array), and missingValues |
| 8 | Healthcare Patients | `08_healthcare_patients.tsch.json` | `08_healthcare_patients.csv` | Patient data with rdfType and date format: any |
| 9 | Financial Trades | `09_financial_trades.tsch.json` | `09_financial_trades.csv` | Trade log with object and array types |
| 10 | Real Estate Listings | `10_real_estate_listings.tsch.json` | `10_real_estate_listings.csv` | Property listings with year type and geopoint (object) |
| 11 | Event Registrations | `11_event_registrations.tsch.json` | `11_event_registrations.csv` | Attendee data with time strptime, duration, datetime any |
| 12 | Supply Chain Orders | `12_supply_chain_orders.tsch.json` | `12_supply_chain_orders.csv` | PO tracking with any type, binary format, multiple foreignKeys |

## Full Spec Feature Coverage

### Types

| Type | Examples | Notes |
|------|----------|-------|
| `string` | all | Default type per spec |
| `integer` | 01, 02, 03, 05, 07, 08, 09, 10, 12 | |
| `number` | 01, 02, 03, 05, 06, 07, 09, 10, 12 | |
| `boolean` | 03, 04, 08, 10, 11, 12 | |
| `date` | 01, 04, 05, 06, 08, 10, 11, 12 | |
| `time` | 07, 11 | Default and strptime formats |
| `datetime` | 02, 06, 07, 09, 11 | Default and "any" formats |
| `year` | 10 | YYYY |
| `yearmonth` | 01 | YYYY-MM |
| `duration` | 06, 11 | ISO 8601 (P5D, PT8H, etc.) |
| `geopoint` | 06, 07, 10 | All 3 formats: default, array, object |
| `object` | 09 | JSON object in CSV cell |
| `array` | 09 | JSON array in CSV cell |
| `any` | 12 | No type coercion |

### Formats

| Format | Type | Examples |
|--------|------|----------|
| `default` | all types | Implicit in most fields |
| `email` | string | 04, 08, 11 |
| `uri` | string | 04, 10 |
| `uuid` | string | 02 |
| `binary` | string | 12 (base64-encoded data) |
| `%d/%m/%Y` | date | 05 (strptime pattern) |
| `any` | date | 08 |
| `any` | datetime | 11 |
| `%I:%M %p` | time | 11 (strptime 12-hour) |
| `default` | geopoint | 06 (`"lon, lat"` string) |
| `array` | geopoint | 07 (`[lon, lat]` JSON array) |
| `object` | geopoint | 10 (`{"lon": n, "lat": n}` JSON object) |

### Field Descriptor Properties

| Property | Examples | Notes |
|----------|----------|-------|
| `name` | all | Required per spec |
| `type` | all | Defaults to "string" if absent |
| `format` | 02, 04, 05, 07, 08, 10, 11, 12 | Defaults to "default" if absent |
| `title` | 01, 02, 03, 04, 05 | Human-readable label |
| `description` | all | Field documentation |
| `example` | 02, 06, 07, 08, 09, 10, 11 | Sample values |
| `rdfType` | 04, 08 | schema.org URIs |
| `constraints` | all | Validation rules object |
| `trueValues` | 03 | Custom boolean parse: `["yes", ...]` |
| `falseValues` | 03 | Custom boolean parse: `["no", ...]` |
| `decimalChar` | 05 | European comma decimals |
| `groupChar` | 05 | European dot thousands separator |
| `bareNumber` | 03, 12 | Strip/reject non-numeric chars |

### Constraints

| Constraint | Examples |
|------------|----------|
| `required` | all |
| `unique` | 03 |
| `enum` | 01, 02, 03, 04, 06, 08, 09, 10, 11, 12 |
| `pattern` | 01, 05, 06, 08, 09, 12 |
| `minimum` | 01, 02, 03, 05, 06, 07, 08, 09, 10, 12 |
| `maximum` | 06, 07, 08, 10 |
| `minLength` | 04 |
| `maxLength` | 04, 11 |

### Top-Level Descriptor Properties

| Property | Examples |
|----------|----------|
| `fields` | all |
| `primaryKey` (single) | 01, 02, 03, 04, 06, 08, 09, 10, 11, 12 |
| `primaryKey` (composite) | 05, 07 |
| `foreignKeys` | 02, 05, 11, 12 |
| `foreignKeys` (self-ref, `resource: ""`) | 01 |
| `missingValues` | 07 |

## Resources

- [Frictionless Table Schema Specification](https://specs.frictionlessdata.io/table-schema/)
- [Frictionless Data](https://frictionlessdata.io/)
- [UTL-X Documentation](../../docs/)
- [UTL-X Examples](../)
