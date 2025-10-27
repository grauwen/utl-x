# Apache Avro Examples for UTL-X

This directory contains 12 real-world business examples demonstrating Avro schema design and data modeling patterns. Each example includes a `.avsc` schema file and a sample `.json` data file.

## Overview

Apache Avro is a data serialization framework developed within Apache's Hadoop project. It provides:
- **Rich data structures** - Records, enums, arrays, maps, unions
- **Compact binary format** - Efficient serialization
- **Schema evolution** - Forward and backward compatibility
- **Dynamic typing** - Data is self-describing with schema
- **Code generation** - Generate classes for Java, Python, C++, etc.

## Examples Catalog

| # | Example | Industry | Schema | Data | Description |
|---|---------|----------|--------|------|-------------|
| 01 | [Customer Record](#1-customer-record) | CRM/Retail | `.avsc` | `.json` | Customer master data with loyalty tiers |
| 02 | [Product Catalog](#2-product-catalog) | E-commerce | `.avsc` | `.json` | Product catalog with pricing and inventory |
| 03 | [Order Processing](#3-order-processing) | E-commerce | `.avsc` | `.json` | Complete order with line items and fulfillment |
| 04 | [Payment Transaction](#4-payment-transaction) | FinTech | `.avsc` | `.json` | Financial payment transaction with risk assessment |
| 05 | [Employee Record](#5-employee-record) | HR/Payroll | `.avsc` | `.json` | Employee master data with benefits and compensation |
| 06 | [Invoice](#6-invoice) | Accounting | `.avsc` | `.json` | Business invoice with line items and payment terms |
| 07 | [Inventory Movement](#7-inventory-movement) | Warehouse/Logistics | `.avsc` | `.json` | Warehouse inventory tracking transaction |
| 08 | [IoT Sensor Reading](#8-iot-sensor-reading) | Manufacturing/IoT | `.avsc` | `.json` | Industrial sensor data with alerts |
| 09 | [Bank Transaction](#9-bank-transaction) | Banking | `.avsc` | `.json` | Bank account transaction with merchant info |
| 10 | [Insurance Claim](#10-insurance-claim) | Insurance | `.avsc` | `.json` | Insurance claim processing record |
| 11 | [Healthcare Patient](#11-healthcare-patient-record) | Healthcare | `.avsc` | `.json` | HIPAA-compliant patient record (PHI protected) |
| 12 | [Shipping Manifest](#12-shipping-manifest) | Logistics | `.avsc` | `.json` | Freight shipping manifest with tracking |

---

## Example Details

### 1. Customer Record

**File:** `01_customer_record.avsc` / `01_customer_record.json`

**Industry:** CRM, Retail, E-commerce

**Key Features:**
- Customer types (Individual, Business, Government, Non-Profit)
- Loyalty tier system (Bronze, Silver, Gold, Platinum)
- Lifetime value tracking
- Address with full contact information
- Extensible metadata (tags, custom attributes)

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "Customer",
  "namespace": "com.example.crm",
  "fields": [
    {"name": "customerId", "type": "string"},
    {"name": "customerType", "type": {"type": "enum", "symbols": ["INDIVIDUAL", "BUSINESS", ...]}},
    {"name": "loyaltyTier", "type": {"type": "enum", "symbols": ["BRONZE", "SILVER", "GOLD", "PLATINUM"]}},
    {"name": "lifetimeValue", "type": "double"},
    {"name": "tags", "type": {"type": "array", "items": "string"}},
    {"name": "metadata", "type": {"type": "map", "values": "string"}}
  ]
}
```

**UTL-X Transformation Example:**
```utlx
%utlx 1.0
input json
output xml
---
{
  Customer: {
    "@id": @input.customerId,
    "@tier": @input.loyaltyTier,
    Name: if (@input.customerType == "INDIVIDUAL")
            then @input.firstName + " " + @input.lastName
            else @input.companyName,
    Email: @input.email,
    LTV: @input.lifetimeValue
  }
}
```

---

### 2. Product Catalog

**File:** `02_product_catalog.avsc` / `02_product_catalog.json`

**Industry:** E-commerce, Retail

**Key Features:**
- SKU-based product identification
- Multi-tier categorization (primary, secondary, tags)
- Pricing with MSRP and sale pricing
- Real-time inventory tracking
- Physical dimensions and weight
- Image gallery with alt text
- Extensible attributes (color, size, material, etc.)

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "Product",
  "fields": [
    {"name": "sku", "type": "string"},
    {"name": "price", "type": {
      "type": "record",
      "name": "Price",
      "fields": [
        {"name": "amount", "type": "double"},
        {"name": "currency", "type": "string"},
        {"name": "msrp", "type": ["null", "double"]},
        {"name": "salePrice", "type": ["null", "double"]}
      ]
    }},
    {"name": "inventory", "type": {
      "type": "record",
      "name": "Inventory",
      "fields": [
        {"name": "quantityAvailable", "type": "int"},
        {"name": "reorderLevel", "type": "int"}
      ]
    }}
  ]
}
```

---

### 3. Order Processing

**File:** `03_order_processing.avsc` / `03_order_processing.json`

**Industry:** E-commerce, Retail

**Key Features:**
- Complete order lifecycle (Pending → Delivered)
- Line items with pricing and tax calculation
- Separate shipping and billing addresses
- Multi-carrier fulfillment tracking
- Payment method and status
- Integrated order notes

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "Order",
  "fields": [
    {"name": "status", "type": {"type": "enum", "symbols": ["PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "REFUNDED"]}},
    {"name": "lineItems", "type": {"type": "array", "items": "LineItem"}},
    {"name": "fulfillment", "type": {
      "type": "record",
      "name": "Fulfillment",
      "fields": [
        {"name": "carrier", "type": ["null", "string"]},
        {"name": "trackingNumber", "type": ["null", "string"]}
      ]
    }}
  ]
}
```

---

### 4. Payment Transaction

**File:** `04_payment_transaction.avsc` / `04_payment_transaction.json`

**Industry:** FinTech, Payment Processing

**Key Features:**
- Multiple transaction types (Purchase, Refund, Authorization, Capture, Void, Chargeback)
- Card payment details (brand, last 4 digits, expiry)
- Processor response with AVS/CVV results
- Fraud risk assessment (score, level, rules)
- Fee breakdown (processing, network)
- Transaction lifecycle tracking

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "PaymentTransaction",
  "fields": [
    {"name": "transactionType", "type": {"type": "enum", "symbols": ["PURCHASE", "REFUND", "AUTHORIZATION", "CAPTURE", "VOID", "CHARGEBACK"]}},
    {"name": "riskAssessment", "type": {
      "type": "record",
      "name": "RiskAssessment",
      "fields": [
        {"name": "score", "type": "int"},
        {"name": "level", "type": {"type": "enum", "symbols": ["LOW", "MEDIUM", "HIGH"]}}
      ]
    }},
    {"name": "fees", "type": {
      "type": "record",
      "fields": [
        {"name": "processingFee", "type": "double"},
        {"name": "networkFee", "type": "double"}
      ]
    }}
  ]
}
```

---

### 5. Employee Record

**File:** `05_employee_record.avsc` / `05_employee_record.json`

**Industry:** HR, Payroll

**Key Features:**
- Personal information (PII/sensitive data)
- Employment status and type tracking
- Position and reporting structure
- Compensation with pay frequency
- Benefits enrollment (health, dental, 401k, stock options)
- Emergency contact information
- Performance ratings

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "Employee",
  "fields": [
    {"name": "employment", "type": {
      "type": "record",
      "fields": [
        {"name": "employmentType", "type": {"type": "enum", "symbols": ["FULL_TIME", "PART_TIME", "CONTRACT", "INTERN"]}},
        {"name": "status", "type": {"type": "enum", "symbols": ["ACTIVE", "ON_LEAVE", "TERMINATED", "RETIRED"]}}
      ]
    }},
    {"name": "compensation", "type": {
      "type": "record",
      "fields": [
        {"name": "baseSalary", "type": "double"},
        {"name": "payFrequency", "type": {"type": "enum", "symbols": ["WEEKLY", "BIWEEKLY", "MONTHLY", "ANNUAL"]}}
      ]
    }},
    {"name": "benefits", "type": {"type": "array", "items": "Benefit"}}
  ]
}
```

---

### 6. Invoice

**File:** `06_invoice.avsc` / `06_invoice.json`

**Industry:** Accounting, Billing

**Key Features:**
- Multiple invoice types (Standard, Credit Note, Proforma, Recurring)
- Invoice lifecycle (Draft → Paid)
- Seller and buyer party information
- Line items with tax calculation
- Payment terms (Net 30, discounts, late fees)
- Payment tracking against invoice
- Document attachments

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "Invoice",
  "fields": [
    {"name": "invoiceType", "type": {"type": "enum", "symbols": ["STANDARD", "CREDIT_NOTE", "DEBIT_NOTE", "PROFORMA", "RECURRING"]}},
    {"name": "status", "type": {"type": "enum", "symbols": ["DRAFT", "SENT", "VIEWED", "PARTIAL_PAYMENT", "PAID", "OVERDUE", "CANCELLED", "VOID"]}},
    {"name": "paymentTerms", "type": {
      "type": "record",
      "fields": [
        {"name": "terms", "type": "string"},
        {"name": "discountPercent", "type": "double"},
        {"name": "discountDays", "type": "int"}
      ]
    }}
  ]
}
```

---

### 7. Inventory Movement

**File:** `07_inventory_movement.avsc` / `07_inventory_movement.json`

**Industry:** Warehouse, Logistics

**Key Features:**
- Movement types (Receipt, Issue, Transfer, Adjustment, Return, Damage, Cycle Count)
- Source and destination locations (warehouse, zone, aisle, bin)
- Lot and serial number tracking
- Expiration date management (perishables)
- Reference document linking (PO, SO, Transfer Order)
- Cost tracking
- User and device audit trail

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "InventoryMovement",
  "fields": [
    {"name": "movementType", "type": {"type": "enum", "symbols": ["RECEIPT", "ISSUE", "TRANSFER", "ADJUSTMENT", "RETURN", "DAMAGE", "CYCLE_COUNT"]}},
    {"name": "fromLocation", "type": ["null", {
      "type": "record",
      "fields": [
        {"name": "warehouse", "type": "string"},
        {"name": "bin", "type": ["null", "string"]}
      ]
    }]},
    {"name": "toLocation", "type": ["null", "Location"]},
    {"name": "serialNumbers", "type": {"type": "array", "items": "string"}}
  ]
}
```

---

### 8. IoT Sensor Reading

**File:** `08_iot_sensor_reading.avsc` / `08_iot_sensor_reading.json`

**Industry:** Manufacturing, IoT, Smart Cities

**Key Features:**
- Multiple sensor types (Temperature, Humidity, Pressure, Vibration, Current, Voltage, Flow, Level, Proximity, Motion)
- Multi-metric measurements per reading
- Measurement quality tracking (Good, Uncertain, Bad)
- Device metadata (manufacturer, model, firmware)
- GPS geolocation (for mobile sensors)
- Device health and battery status
- Alert triggering and severity levels

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "SensorReading",
  "fields": [
    {"name": "sensorType", "type": {"type": "enum", "symbols": ["TEMPERATURE", "HUMIDITY", "PRESSURE", "VIBRATION", ...]}},
    {"name": "measurements", "type": {"type": "array", "items": {
      "type": "record",
      "fields": [
        {"name": "metric", "type": "string"},
        {"name": "value", "type": "double"},
        {"name": "quality", "type": {"type": "enum", "symbols": ["GOOD", "UNCERTAIN", "BAD"]}}
      ]
    }}},
    {"name": "alerts", "type": {"type": "array", "items": "Alert"}}
  ]
}
```

---

### 9. Bank Transaction

**File:** `09_bank_transaction.avsc` / `09_bank_transaction.json`

**Industry:** Banking, Finance

**Key Features:**
- Transaction types (Deposit, Withdrawal, Transfer, Payment, Fee, Interest, Dividend, Refund, Reversal)
- Multi-channel support (ATM, Branch, Online, Mobile App, Phone, Wire, ACH, Check)
- Counterparty information
- Merchant information with MCC codes
- Transaction location tracking
- Personal finance tags
- Dispute flag
- Posting date vs transaction date

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "BankTransaction",
  "fields": [
    {"name": "transactionType", "type": {"type": "enum", "symbols": ["DEPOSIT", "WITHDRAWAL", "TRANSFER", "PAYMENT", ...]}},
    {"name": "channel", "type": {"type": "enum", "symbols": ["ATM", "BRANCH", "ONLINE", "MOBILE_APP", ...]}},
    {"name": "merchantInfo", "type": ["null", {
      "type": "record",
      "fields": [
        {"name": "category", "type": "string"},
        {"name": "categoryDescription", "type": "string"}
      ]
    }]},
    {"name": "tags", "type": {"type": "array", "items": "string"}}
  ]
}
```

---

### 10. Insurance Claim

**File:** `10_insurance_claim.avsc` / `10_insurance_claim.json`

**Industry:** Insurance

**Key Features:**
- Claim types (Auto, Home, Health, Life, Dental, Vision, Disability, Workers Comp)
- Claim lifecycle (Submitted → Closed/Appealed)
- Policyholder and claimant information
- Incident details with location
- Damage assessment with severity
- Claimed vs approved vs paid amounts
- Document management (police reports, photos, receipts, estimates)
- Adjuster assignment
- Denial reason tracking
- Communication log

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "InsuranceClaim",
  "fields": [
    {"name": "claimType", "type": {"type": "enum", "symbols": ["AUTO", "HOME", "HEALTH", "LIFE", ...]}},
    {"name": "status", "type": {"type": "enum", "symbols": ["SUBMITTED", "UNDER_REVIEW", "PENDING_INFO", "APPROVED", "PARTIALLY_APPROVED", "DENIED", ...]}},
    {"name": "damageAssessment", "type": {
      "type": "record",
      "fields": [
        {"name": "severity", "type": {"type": "enum", "symbols": ["MINOR", "MODERATE", "MAJOR", "TOTAL_LOSS"]}},
        {"name": "estimatedCost", "type": "double"}
      ]
    }},
    {"name": "documents", "type": {"type": "array", "items": "Document"}}
  ]
}
```

---

### 11. Healthcare Patient Record

**File:** `11_healthcare_patient.avsc` / `11_healthcare_patient.json`

**Industry:** Healthcare (HIPAA-compliant)

**Key Features:**
- Protected Health Information (PHI) - encrypted sensitive data
- Personal information with gender identity options
- Insurance coverage (primary, secondary, tertiary)
- Emergency contact
- Primary care provider
- Allergies with severity levels
- Medical conditions with ICD-10 codes
- Medication tracking (active and historical)
- Immunization history with CVX codes
- Vital signs
- Blood type
- Smoking status
- Advance directives (DNR, Living Will, Power of Attorney)

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "PatientRecord",
  "fields": [
    {"name": "allergies", "type": {"type": "array", "items": {
      "type": "record",
      "fields": [
        {"name": "allergen", "type": "string"},
        {"name": "severity", "type": {"type": "enum", "symbols": ["MILD", "MODERATE", "SEVERE", "LIFE_THREATENING"]}}
      ]
    }}},
    {"name": "conditions", "type": {"type": "array", "items": {
      "type": "record",
      "fields": [
        {"name": "icd10Code", "type": "string"},
        {"name": "status", "type": {"type": "enum", "symbols": ["ACTIVE", "RESOLVED", "CHRONIC", "UNDER_TREATMENT"]}}
      ]
    }}},
    {"name": "medications", "type": {"type": "array", "items": "Medication"}},
    {"name": "immunizations", "type": {"type": "array", "items": "Immunization"}}
  ]
}
```

**HIPAA Compliance Notes:**
- SSN field uses encrypted storage
- Access logging required (not shown in schema)
- Data retention policies apply
- Patient consent required for data sharing

---

### 12. Shipping Manifest

**File:** `12_shipping_manifest.avsc` / `12_shipping_manifest.json`

**Industry:** Logistics, Freight Transportation

**Key Features:**
- Manifest types (Air Freight, Ocean Freight, Ground Transport, Rail, Intermodal, Parcel)
- Shipper and consignee information
- Carrier information with SCAC/IATA codes
- Origin and destination with port codes (UN/LOCODE)
- Package tracking with dimensions and weight
- Shipment totals (packages, weight, volume, value)
- Charges breakdown (base, fuel surcharge, insurance, handling, customs)
- Customs information (value, Incoterms, HS codes, country of origin)
- Special handling instructions
- Hazmat information (UN numbers, hazard class)
- Shipping documents (BOL, Commercial Invoice, Packing List, Certificate of Origin)
- Real-time tracking events

**Schema Highlights:**
```avro
{
  "type": "record",
  "name": "ShippingManifest",
  "fields": [
    {"name": "manifestType", "type": {"type": "enum", "symbols": ["AIR_FREIGHT", "OCEAN_FREIGHT", "GROUND_TRANSPORT", ...]}},
    {"name": "packages", "type": {"type": "array", "items": {
      "type": "record",
      "fields": [
        {"name": "trackingNumber", "type": "string"},
        {"name": "weight", "type": "double"},
        {"name": "dimensions", "type": "Dimensions"}
      ]
    }}},
    {"name": "customsInfo", "type": ["null", {
      "type": "record",
      "fields": [
        {"name": "incoterms", "type": "string"},
        {"name": "hsCode", "type": ["null", "string"]}
      ]
    }]},
    {"name": "hazmat", "type": {"type": "array", "items": "HazmatInfo"}},
    {"name": "events", "type": {"type": "array", "items": "TrackingEvent"}}
  ]
}
```

---

## Common Avro Patterns Used

### 1. Enumerations
Restrict field values to a predefined set:
```avro
{
  "name": "status",
  "type": {
    "type": "enum",
    "name": "Status",
    "symbols": ["PENDING", "ACTIVE", "COMPLETED", "CANCELLED"]
  }
}
```

### 2. Optional Fields (Nullable)
Use union types with `null`:
```avro
{
  "name": "middleName",
  "type": ["null", "string"],
  "default": null
}
```

### 3. Nested Records
Complex data structures:
```avro
{
  "name": "address",
  "type": {
    "type": "record",
    "name": "Address",
    "fields": [
      {"name": "street", "type": "string"},
      {"name": "city", "type": "string"}
    ]
  }
}
```

### 4. Arrays
Repeated elements:
```avro
{
  "name": "tags",
  "type": {
    "type": "array",
    "items": "string"
  },
  "default": []
}
```

### 5. Maps
Key-value pairs:
```avro
{
  "name": "metadata",
  "type": {
    "type": "map",
    "values": "string"
  },
  "default": {}
}
```

### 6. Logical Types
Semantic types built on primitives:

**Dates:**
```avro
{
  "name": "birthDate",
  "type": {
    "type": "int",
    "logicalType": "date"
  }
}
```

**Timestamps:**
```avro
{
  "name": "createdAt",
  "type": {
    "type": "long",
    "logicalType": "timestamp-millis"
  }
}
```

**Decimals:**
```avro
{
  "name": "price",
  "type": {
    "type": "bytes",
    "logicalType": "decimal",
    "precision": 10,
    "scale": 2
  }
}
```

---

## Using Avro with UTL-X

### Read Avro Data

```utlx
%utlx 1.0
input avro {
  schema: "01_customer_record.avsc"
}
output json
---
{
  customer: {
    id: @input.customerId,
    name: if (@input.customerType == "INDIVIDUAL")
            then @input.firstName + " " + @input.lastName
            else @input.companyName,
    tier: @input.loyaltyTier,
    ltv: @input.lifetimeValue
  }
}
```

### Write Avro Data

```utlx
%utlx 1.0
input json
output avro {
  schema: "01_customer_record.avsc",
  codec: "snappy"  # Compression: snappy, deflate, bzip2, or null
}
---
{
  customerId: @input.id,
  customerType: "INDIVIDUAL",
  firstName: @input.firstName,
  lastName: @input.lastName,
  email: @input.email,
  loyaltyTier: "BRONZE",
  lifetimeValue: 0.0,
  registrationDate: now(),
  isActive: true
}
```

### Schema Evolution Example

```utlx
%utlx 1.0
input avro {
  schema: "customer_v1.avsc"
}
output avro {
  schema: "customer_v2.avsc",  # New schema with additional fields
  codec: "snappy"
}
---
{
  ...@input,  # Spread existing fields
  // Add new fields with defaults
  preferredLanguage: "en-US",
  marketingConsent: false
}
```

---

## Schema Validation

All schemas in this directory can be validated using Apache Avro tools:

```bash
# Validate schema
java -jar avro-tools.jar compile schema 01_customer_record.avsc .

# Validate data against schema
java -jar avro-tools.jar jsontofrag \
  --schema-file 01_customer_record.avsc \
  01_customer_record.json

# Convert JSON to Avro binary
java -jar avro-tools.jar fromjson \
  --schema-file 01_customer_record.avsc \
  01_customer_record.json > 01_customer_record.avro

# Convert Avro binary to JSON
java -jar avro-tools.jar tojson 01_customer_record.avro
```

---

## Best Practices

### 1. Namespace Convention
Use reverse domain notation:
```avro
"namespace": "com.example.domain"
```

### 2. Documentation
Always include `doc` fields:
```avro
{
  "name": "field",
  "type": "string",
  "doc": "Clear description of the field's purpose"
}
```

### 3. Default Values
Provide defaults for optional fields:
```avro
{
  "name": "isActive",
  "type": "boolean",
  "default": true
}
```

### 4. Schema Evolution
- **Forward compatible:** New schema can read old data
- **Backward compatible:** Old schema can read new data
- **Full compatible:** Both forward and backward compatible

**Rules:**
- Add fields with defaults for backward compatibility
- Remove optional fields for forward compatibility
- Never change field types
- Enum symbols can be added but not removed

### 5. Field Naming
- Use camelCase for field names
- Use UPPER_SNAKE_CASE for enum symbols
- Use PascalCase for type names

### 6. Performance
- Use compression codecs (Snappy for speed, Deflate for size)
- Keep schemas compact (avoid deeply nested structures)
- Use primitive types where possible
- Consider schema fingerprinting for caching

---

## Integration Scenarios

### Data Lake Ingestion
```
Source Systems → Avro → Apache Kafka → HDFS/S3 → Apache Spark
```

### ETL Pipeline
```
Database → UTL-X → Avro → Data Warehouse → Analytics
```

### Event Streaming
```
Microservices → Avro → Kafka → Stream Processing → Downstream Services
```

### API Integration
```
REST API → JSON → UTL-X → Avro → Data Store
```

---

## Schema Registry Integration

For production use, integrate with a schema registry:

**Confluent Schema Registry:**
```bash
# Register schema
curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data '{"schema": "..."}' \
  http://localhost:8081/subjects/customer-value/versions

# Retrieve schema
curl http://localhost:8081/subjects/customer-value/versions/latest
```

**Benefits:**
- Centralized schema management
- Schema evolution validation
- Version control
- Client compatibility checking

---

## Resources

### Apache Avro
- **Official Site:** https://avro.apache.org/
- **Specification:** https://avro.apache.org/docs/current/spec.html
- **Best Practices:** https://avro.apache.org/docs/current/gettingstartedjava.html

### Tools
- **Avro Tools:** https://avro.apache.org/docs/current/gettingstartedjava.html#download_install
- **Schema Registry:** https://docs.confluent.io/platform/current/schema-registry/index.html
- **Avro Viewer:** https://github.com/gchq/CyberChef (Avro to JSON)

### UTL-X Documentation
- **Avro Format Support:** `docs/formats/avro-format-guide.md`
- **Schema Evolution:** `docs/formats/avro-schema-evolution.md`
- **Performance Tuning:** `docs/formats/avro-performance.md`

---

## Contributing

To add new Avro examples:

1. Create schema file: `##_example_name.avsc`
2. Create sample data: `##_example_name.json`
3. Update this README with:
   - Entry in catalog table
   - Detailed example section
   - Use case description
4. Validate schema and data
5. Add transformation examples (optional)

**Naming Convention:** `##_descriptive_name` where `##` is the example number (01-99).

---

## License

These examples are provided as part of the UTL-X project under the AGPL-3.0 / Commercial dual license.

See the main project LICENSE file for details.

---

**Last Updated:** 2025-10-27
**Example Count:** 12
**Total Schemas:** 12
**Total Sample Files:** 12
