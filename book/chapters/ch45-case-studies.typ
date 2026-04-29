= Case Studies and Real-World Use Cases

== Case Study 1: European E-Invoicing (Peppol / UBL)
// - Company: Dutch wholesale distributor, 200 employees, 850 B2B customers
// - Challenge: EU Directive 2014/55 — all B2G invoices must be UBL 2.1 XML
// - Source: Dynamics 365 Business Central (proprietary JSON)
// - Target: UBL 2.1 XML via Peppol BIS 3.0 network
// - Architecture: D365 → Service Bus → UTL-X (Container App) → Peppol AP
// - Transformations: 3 .utlx files (~400 lines total)
//   - invoice-to-ubl.utlx: JSON → UBL XML (100+ field mappings)
//   - validate-ubl.utlx: Peppol BIS 3.0 business rules
//   - route-by-country.utlx: country-specific tax annexes
// - Results: 2,147 invoices/day, 14ms avg latency, 99.97% Peppol acceptance
// - Cost: $35/month (Starter) + $50/month Azure compute = $85/month
// - Savings: $44,000/year vs custom C# code (12 Azure Functions)
// - Full code walkthrough

== Case Study 2: Healthcare HL7 FHIR Integration
// - Organization: Dutch academic hospital, 1,200 beds
// - Challenge: MedMij/VIPP — exchange patient data via FHIR R4
// - Sources: Lab (HL7 v2), Radiology (DICOM SR), EHR (JSON), Pharmacy (HL7 v2.5)
// - Target: FHIR R4 Bundles via NUTS network
// - Architecture: 4 Service Bus queues → UTL-X (Professional) → FHIR Server
// - Transformations: 8 .utlx files, pipeline chaining (5 steps per message)
// - Key challenge: FHIR value attribute convention (@value access)
// - Compliance: GDPR + NEN 7510 by architecture (data stays in tenant)
// - Results: 8,400 messages/day, 22ms pipeline latency, 32 workers
// - Cost: $105/month (Professional) + $200/month Azure = $305/month
// - Savings: $115,000/year vs Rhapsody integration engine
// - Full code walkthrough

== Case Study 3: Financial Services — SWIFT ISO 20022 Migration
// - Company: European payment processor
// - Challenge: SWIFT MX migration deadline — MT (text) → MX (XML ISO 20022)
// - Source: Legacy MT103 messages (proprietary text format)
// - Target: pain.001 XML (ISO 20022) for SEPA credit transfers
// - Architecture: Payment gateway → UTL-X → SWIFT Alliance Lite2
// - Transformations: MT field parsing → canonical model → pain.001 XML
// - Validation: ISO 20022 XSD + business rules (amount limits, BIC validation)
// - Results: 15,000 payments/day, regulatory compliance achieved
// - Full code walkthrough

== Case Study 4: Retail — Multi-Channel Order Processing
// - Company: Online retailer with 4 sales channels
// - Sources: Shopify (JSON), WooCommerce (JSON), Amazon (XML), Manual (CSV)
// - Target: Canonical order format → ERP (JSON) + Warehouse (XML) + Accounting (CSV)
// - Architecture: per-channel webhook → UTL-X → fan-out to 3 targets
// - Transformations: 4 input normalizers + 3 output formatters = 7 .utlx files
// - Challenge: each channel has different product ID format, address structure
// - Results: unified order processing, single source of truth
// - Full code walkthrough

== Case Study 5: IoT / Manufacturing — Sensor Data Normalization
// - Company: Manufacturing plant with 500+ sensors
// - Sources: OPC-UA (XML), MQTT (JSON), Modbus (CSV), legacy PLC (custom)
// - Target: Azure IoT Hub (JSON) → Time Series Insights
// - Architecture: Edge gateway → UTL-X → IoT Hub
// - Transformations: normalize sensor readings to common telemetry schema
// - Challenge: different units, different timestamp formats, missing values
// - Results: unified telemetry, real-time dashboards
// - Full code walkthrough

== Case Study 6: Government — Tax Filing Integration
// - Organization: National tax authority
// - Challenge: accept tax returns in multiple formats from different software vendors
// - Sources: UBL (XML), SBR/XBRL (XML), custom JSON APIs
// - Target: Internal canonical format + validation + archiving
// - Architecture: API gateway → UTL-X → validation → storage
// - Compliance: tax-specific Schematron rules, digital signature verification
// - Results: multi-vendor interoperability, automated validation
// - Full code walkthrough

== Azure Marketplace: Getting Started Guide
// - Step 1: Find UTL-X on Azure Marketplace
// - Step 2: Choose plan (Starter $35/month or Professional $105/month)
// - Step 3: Configure (region, CPU, memory, workers)
// - Step 4: Deploy (one-click)
// - Step 5: Access the API (health check, load transformation, execute)
// - Step 6: Connect to Service Bus (optional, via Dapr)
// - Screenshots of each step
// - First transformation: curl example
// - Monitoring: where to find metrics and logs

== Recipes: Common Transformation Patterns
// - Recipe 1: REST API response flattening (nested JSON → flat CSV)
// - Recipe 2: XML namespace stripping (complex → clean)
// - Recipe 3: Date format normalization (US/EU/ISO across formats)
// - Recipe 4: Currency conversion with lookup table
// - Recipe 5: Address normalization (different country formats)
// - Recipe 6: Error response standardization (different APIs → common format)
// - Recipe 7: CSV regional format conversion (US → EU number format)
// - Recipe 8: Multi-language content extraction (XML with lang attributes)
// - Recipe 9: Hierarchical → flat (parent-child denormalization)
// - Recipe 10: Flat → hierarchical (CSV import → nested JSON/XML)
