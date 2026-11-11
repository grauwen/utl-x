# XSD and JSON Schema Conformance Tests

This document summarizes the comprehensive conformance test suite for XSD and JSON Schema (JSCH) format support in UTL-X.

## Test Organization

Tests are organized into systematic tests (permutations and edge cases) and real-world business examples.

---

## XSD Tests (7 tests)

### Basic Parsing (2 tests)
Location: `tests/formats/xsd/basic/`

1. **parse_xsd_1_0.yaml** - Parse XSD 1.0 schema and extract metadata
   - Verifies schema type, version, target namespace extraction
   - Tests basic element counting

2. **parse_xsd_1_1.yaml** - Parse XSD 1.1 schema and detect version
   - Verifies XSD 1.1 version detection via `vc:minVersion`
   - Tests assertion support detection

### Scope Detection (2 tests)
Location: `tests/formats/xsd/scope/`

3. **global_elements.yaml** - Verify global elements tagged with scope=global
   - Tests global element detection
   - Verifies `__scope` and `__schemaType` metadata

4. **global_types.yaml** - Verify global complex types tagged with scope=global
   - Tests global type detection
   - Verifies complex type scope tagging

### Design Patterns (2 tests)
Location: `tests/formats/xsd/patterns/`

5. **venetian_blind.yaml** - Venetian Blind pattern (global types, local elements)
   - Tests detection of global type with local element references
   - Common pattern in enterprise schemas

6. **russian_doll.yaml** - Russian Doll pattern (inline nested types)
   - Tests detection of all-inline type definitions
   - Single global element with nested local types

### Features (1 test)
Location: `tests/formats/xsd/features/`

7. **annotations.yaml** - Extract annotations and documentation
   - Tests `xs:annotation` and `xs:documentation` extraction
   - Verifies documentation text preservation

### Real-World Business Examples (3 tests)
Location: `tests/formats/xsd/real-world/`

8. **ecommerce_product_catalog.yaml** - E-commerce product catalog schema
   - **Business Context:** Online retail product management
   - **Features Demonstrated:**
     - Product catalog with SKU, pricing, inventory
     - Category enumerations
     - Multi-currency support
     - Image management
     - Manufacturer tracking
   - **Schema Complexity:** 8 complex types, 3 simple types
   - **Real-world Use Case:** Syncing product data across web, mobile, and POS systems

9. **hr_employee_schema.yaml** - HR Employee Management System
   - **Business Context:** Human Resources Information System (HRIS)
   - **Features Demonstrated:**
     - Employee personal information (GDPR/CCPA compliant)
     - Employment details and compensation
     - Benefits tracking (health, dental, 401k, PTO)
     - Emergency contacts
     - Department codes and employment types
     - SSN pattern validation
   - **Schema Complexity:** 9 complex types, 7 simple types
   - **Real-world Use Case:** Employee directory, payroll integration, benefits administration

10. **financial_invoice.yaml** - Financial invoice/billing system
    - **Business Context:** B2B/B2C invoicing (UBL-inspired)
    - **Features Demonstrated:**
      - Invoice header with purchase order reference
      - Seller and buyer party information
      - Line items with tax calculations
      - Multi-currency transactions
      - Payment terms and methods
      - Discount handling
      - Tax compliance (VAT, Sales Tax, GST)
    - **Schema Complexity:** 12 complex types, 6 simple types
    - **Real-world Use Case:** Electronic invoicing, accounts receivable, SOX/IFRS/GAAP compliance

---

## JSON Schema Tests (7 tests)

### Basic Parsing (2 tests)
Location: `tests/formats/jsch/basic/`

1. **parse_draft_07.yaml** - Parse JSON Schema draft-07
   - Verifies draft-07 version detection
   - Tests schema metadata extraction
   - Validates properties detection

2. **parse_2020_12.yaml** - Parse JSON Schema 2020-12 with $defs
   - Verifies 2020-12 version detection
   - Tests `$defs` support (replaces `definitions`)
   - Validates definition counting

### Validation Keywords (2 tests)
Location: `tests/formats/jsch/validation/`

3. **string_validation.yaml** - String validation constraints
   - Tests format, pattern, minLength, maxLength extraction
   - Verifies email format validation
   - Real-world regex pattern support

4. **numeric_validation.yaml** - Numeric validation constraints
   - Tests integer and number types
   - Verifies minimum, maximum, exclusiveMinimum
   - Tests multipleOf constraint

### Composition (1 test)
Location: `tests/formats/jsch/composition/`

5. **ref_definitions.yaml** - $ref references to definitions
   - Tests `$ref` reference detection
   - Verifies definitions block parsing
   - Validates reference reuse

### Real-World Business Examples (3 tests)
Location: `tests/formats/jsch/real-world/`

6. **api_user_profile.yaml** - User Profile API schema
   - **Business Context:** Social media platform user management
   - **Features Demonstrated:**
     - User profile with UUID validation
     - Avatar and bio management
     - Location and timezone support
     - Preferences (language, theme, notifications, privacy)
     - Social media links
     - User statistics (followers, following, posts)
     - Account types and verification
   - **Schema Complexity:** Nested objects, regex patterns, enums
   - **Real-world Use Case:** REST API for user profile CRUD operations

7. **payment_transaction.yaml** - Payment transaction processing
   - **Business Context:** PCI-DSS compliant payment processing
   - **Features Demonstrated:**
     - Tokenized card data (security best practices)
     - Multiple payment methods (cards, wallets, crypto)
     - Money amount with currency validation
     - 3D Secure authentication
     - Fraud risk scoring
     - Refund tracking
     - Gateway response handling
     - Billing address validation
   - **Schema Complexity:** 4 definitions, extensive validation rules
   - **Real-world Use Case:** Payment gateway integration, transaction logging, fraud detection

8. **customer_order.yaml** - E-commerce customer order
   - **Business Context:** Order management system for e-commerce
   - **Features Demonstrated:**
     - Line items with product options
     - Billing and shipping addresses
     - Order totals calculation (subtotal, tax, shipping, discount)
     - Order status workflow
     - Shipping methods and tracking
     - Payment status tracking
     - Discount/promo codes
     - Multi-channel orders (web, mobile, phone, in-store, marketplace)
   - **Schema Complexity:** Uses JSON Schema 2020-12 with $defs
   - **Real-world Use Case:** Order processing pipeline, fulfillment, inventory management

---

## Test Statistics

### XSD Tests
- **Total:** 10 tests
- **Systematic:** 7 tests (70%)
- **Real-world:** 3 tests (30%)
- **Coverage:**
  - Basic parsing: 2
  - Scope detection: 2
  - Design patterns: 2
  - Features: 1
  - Real-world scenarios: 3

### JSON Schema Tests
- **Total:** 8 tests
- **Systematic:** 5 tests (62.5%)
- **Real-world:** 3 tests (37.5%)
- **Coverage:**
  - Basic parsing: 2
  - Validation: 2
  - Composition: 1
  - Real-world scenarios: 3

### Combined
- **Total Tests:** 18
- **Systematic Tests:** 12 (67%)
- **Real-world Business Examples:** 6 (33%)

---

## Business Domains Covered

The real-world examples span multiple business domains:

1. **E-commerce:** Product catalogs, customer orders
2. **Human Resources:** Employee management, HRIS
3. **Finance:** Invoicing, payment processing
4. **Social Media:** User profiles, API management
5. **Payments:** Transaction processing, fraud detection

---

## Running the Tests

### Run all XSD tests:
```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py formats/xsd
```

### Run all JSCH tests:
```bash
cd conformance-suite
python3 runners/cli-runner/simple-runner.py formats/jsch
```

### Run specific test:
```bash
python3 runners/cli-runner/simple-runner.py formats/xsd/basic parse_xsd_1_0
```

### Run all format tests:
```bash
python3 runners/cli-runner/simple-runner.py formats
```

---

## Test Design Philosophy

### Systematic Tests
- **Purpose:** Validate parser correctness and edge cases
- **Coverage:** Core functionality, metadata extraction, scope tagging
- **Value:** Ensures implementation meets specification

### Real-World Examples
- **Purpose:** Demonstrate practical business value
- **Coverage:** Complex, production-ready schemas
- **Value:**
  - Developers can learn from recognizable patterns
  - Validates UTL-X handles enterprise-grade schemas
  - Provides copy-paste starting points for similar use cases
  - Documents best practices for each business domain

### Business Example Selection Criteria
1. **Recognizability:** Readers immediately understand the domain
2. **Complexity:** Demonstrates advanced schema features
3. **Completeness:** Production-ready, not toy examples
4. **Best Practices:** Follows industry standards (UBL, ISO codes, GDPR compliance)
5. **Diversity:** Covers multiple industries and use cases

---

## Notes

- All tests use actual UTL-X transformation syntax
- Expected outputs are minimal to avoid brittle tests
- Real-world schemas include extensive documentation
- Tests verify metadata extraction, not full schema validation
- Focus is on parsing and metadata tagging, not schema enforcement

---

## Future Test Additions

Potential areas for additional test coverage:

### XSD:
- Garden of Eden pattern
- Salami Slice pattern
- Schema imports and includes
- Attribute groups
- Restrictions (minOccurs, maxOccurs, pattern)
- Substitution groups
- Abstract types
- Mixed content

### JSON Schema:
- allOf/anyOf/oneOf composition
- Array validation (minItems, maxItems, uniqueItems)
- Enum types
- Const values
- Conditional schemas (if/then/else)
- Format validators (date-time, uri, email)
- Pattern properties
- Additional properties control

### Real-World:
- Healthcare (HL7 FHIR)
- Logistics (shipment tracking)
- Manufacturing (bill of materials)
- Insurance (policy management)
- Education (student records)
