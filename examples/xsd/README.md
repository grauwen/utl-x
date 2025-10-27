# XML Schema (XSD) Examples for UTL-X

This directory contains 12 real-world business XSD (XML Schema Definition) examples demonstrating various industries and use cases. Each example includes both an XSD schema file (`.xsd`) and corresponding sample XML data (`.xml`) that validates against the schema.

## About XML Schema (XSD)

XML Schema Definition (XSD) is a W3C recommendation that describes the structure and constraints of XML documents. It provides a contract for XML data, describing:
- Element and attribute definitions
- Data types and constraints
- Required and optional elements
- Occurrence constraints (minOccurs, maxOccurs)
- Pattern matching with regular expressions
- Enumerated values
- Complex type definitions
- Namespaces

XSD is widely used for:
- SOAP web services
- Enterprise data interchange
- Configuration file validation
- Document structure definition
- Code generation (Java, C#, etc.)

## Example Catalog

| # | Example | Schema File | Data File | Description |
|---|---------|-------------|-----------|-------------|
| 1 | Purchase Order | `01_purchase_order.xsd` | `01_purchase_order.xml` | Supply chain procurement with vendor/buyer parties, line items, and payment terms |
| 2 | Customer Record | `02_customer_record.xsd` | `02_customer_record.xml` | CRM customer master data with personal/company info, loyalty tiers, and preferences |
| 3 | Invoice | `03_invoice.xsd` | `03_invoice.xml` | Business invoice with seller/buyer parties, line items, and payment tracking |
| 4 | Product Catalog | `04_product_catalog.xsd` | `04_product_catalog.xml` | E-commerce product catalog with pricing, inventory, images, and attributes |
| 5 | Employee Record | `05_employee_record.xsd` | `05_employee_record.xml` | HR employee master data with personal info, employment, compensation, and benefits |
| 6 | Insurance Claim | `06_insurance_claim.xsd` | `06_insurance_claim.xml` | Insurance claim processing with incident details, damage assessment, and adjuster |
| 7 | Healthcare Patient | `07_healthcare_patient.xsd` | `07_healthcare_patient.xml` | HIPAA-compliant patient record with demographics, allergies, medications, and vitals |
| 8 | Shipping Manifest | `08_shipping_manifest.xsd` | `08_shipping_manifest.xml` | Logistics freight manifest with shipper/consignee, packages, and customs info |
| 9 | Bank Transaction | `09_bank_transaction.xsd` | `09_bank_transaction.xml` | Banking transaction with account details, merchant info, and location tracking |
| 10 | Configuration File | `10_configuration.xsd` | `10_configuration.xml` | Application configuration with database, server, logging, and security settings |
| 11 | SOAP API | `11_soap_api.xsd` | `11_soap_api.xml` | SOAP web service request/response definitions with customer and order operations |
| 12 | Real Estate Listing | `12_real_estate.xsd` | `12_real_estate.xml` | MLS property listing with property details, agent info, and open houses |

## Detailed Examples

### 1. Purchase Order (Supply Chain)

**Use Case:** Procurement and supply chain management for purchase order processing

**Schema Highlights:**
```xml
<xs:complexType name="PurchaseOrderType">
  <xs:sequence>
    <xs:element name="OrderInfo" type="po:OrderInfoType"/>
    <xs:element name="Vendor" type="po:PartyType"/>
    <xs:element name="Buyer" type="po:PartyType"/>
    <xs:element name="LineItems" type="po:LineItemsType"/>
    <xs:element name="PaymentTerms" type="po:PaymentTermsType"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="PONumberType">
  <xs:restriction base="xs:string">
    <xs:pattern value="PO-[0-9]{4}-[0-9]{6}"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="PriorityType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="low"/>
    <xs:enumeration value="normal"/>
    <xs:enumeration value="high"/>
    <xs:enumeration value="urgent"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Pattern validation for PO numbers (PO-YYYY-NNNNNN)
- Reusable Party type for vendor and buyer
- Enumeration for priority and status values
- Money type with decimal precision constraints
- Unit of measure enumeration (EA, PCS, BOX, KG, etc.)
- Payment terms with discount options

**Business Value:**
- Ensures consistent purchase order structure
- Validates critical fields (amounts, dates, parties)
- Documents data exchange format for B2B integration
- Supports automated PO processing

---

### 2. Customer Record (CRM)

**Use Case:** Customer master data for CRM and customer management systems

**Schema Highlights:**
```xml
<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="CustomerID" type="crm:CustomerIDType"/>
    <xs:element name="CustomerType" type="crm:CustomerTypeEnum"/>
    <xs:element name="PersonalInfo" type="crm:PersonalInfoType" minOccurs="0"/>
    <xs:element name="CompanyInfo" type="crm:CompanyInfoType" minOccurs="0"/>
    <xs:element name="LoyaltyInfo" type="crm:LoyaltyInfoType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="CustomerIDType">
  <xs:restriction base="xs:string">
    <xs:pattern value="CUST-[0-9]{4}-[0-9]{6}"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="LoyaltyTierType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="bronze"/>
    <xs:enumeration value="silver"/>
    <xs:enumeration value="gold"/>
    <xs:enumeration value="platinum"/>
    <xs:enumeration value="diamond"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Separate personal and company information types
- Customer type enumeration (individual, business, government)
- Loyalty tier system
- Multiple address support with type classification
- Communication preferences and channel selection
- Account status and credit management

**Business Value:**
- Supports both B2C and B2B customer types
- Standardizes customer data across systems
- Enables loyalty program management
- Validates contact information formats

---

### 3. Invoice (Accounting)

**Use Case:** Business invoices for accounting and billing systems

**Schema Highlights:**
```xml
<xs:complexType name="PartyType">
  <xs:sequence>
    <xs:element name="PartyID" type="xs:string"/>
    <xs:element name="Name" type="xs:string"/>
    <xs:element name="TaxID" type="xs:string" minOccurs="0"/>
    <xs:element name="Address" type="inv:AddressType"/>
    <xs:element name="Email" type="inv:EmailType"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="InvoiceSummaryType">
  <xs:sequence>
    <xs:element name="Subtotal" type="inv:MoneyType"/>
    <xs:element name="TotalTax" type="inv:MoneyType"/>
    <xs:element name="TotalAmount" type="inv:MoneyType"/>
    <xs:element name="AmountPaid" type="inv:MoneyType"/>
    <xs:element name="AmountDue" type="inv:MoneyType"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="InvoiceStatusType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="draft"/>
    <xs:enumeration value="sent"/>
    <xs:enumeration value="viewed"/>
    <xs:enumeration value="partially-paid"/>
    <xs:enumeration value="paid"/>
    <xs:enumeration value="overdue"/>
    <xs:enumeration value="cancelled"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Reusable Party type for seller and buyer
- Line item details with tax calculation
- Invoice summary with payment tracking
- Payment method enumeration
- Bank account information with IBAN/SWIFT
- Document attachments support

**Business Value:**
- Ensures accurate invoice calculations
- Tracks payment status lifecycle
- Supports international transactions (IBAN, SWIFT)
- Documents invoice data for accounting systems

---

### 4. Product Catalog (E-commerce)

**Use Case:** Product catalog entries for e-commerce platforms and inventory systems

**Schema Highlights:**
```xml
<xs:complexType name="PricingType">
  <xs:sequence>
    <xs:element name="Currency" type="prd:CurrencyCodeType"/>
    <xs:element name="BasePrice" type="prd:MoneyType"/>
    <xs:element name="SalePrice" type="prd:MoneyType" minOccurs="0"/>
    <xs:element name="MSRP" type="prd:MoneyType" minOccurs="0"/>
    <xs:element name="SaleStartDate" type="xs:date" minOccurs="0"/>
    <xs:element name="SaleEndDate" type="xs:date" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="InventoryType">
  <xs:sequence>
    <xs:element name="Warehouse" type="prd:WarehouseInventoryType" maxOccurs="unbounded"/>
    <xs:element name="TotalAvailable" type="xs:nonNegativeInteger"/>
    <xs:element name="ReorderPoint" type="xs:positiveInteger"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="ProductStatusType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="active"/>
    <xs:enumeration value="inactive"/>
    <xs:enumeration value="discontinued"/>
    <xs:enumeration value="out-of-stock"/>
    <xs:enumeration value="coming-soon"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Comprehensive pricing with sale price support
- Multi-warehouse inventory tracking
- Product dimensions and weight
- Image gallery with primary image flag
- Dynamic attributes (color, size, etc.)
- Related products with relationship types

**Business Value:**
- Supports complex pricing strategies
- Tracks inventory across multiple warehouses
- Enables product search and filtering
- Documents product data for e-commerce integrations

---

### 5. Employee Record (HR)

**Use Case:** Employee master data for HR and payroll systems

**Schema Highlights:**
```xml
<xs:complexType name="EmployeeType">
  <xs:sequence>
    <xs:element name="EmployeeID" type="hr:EmployeeIDType"/>
    <xs:element name="PersonalInfo" type="hr:PersonalInfoType"/>
    <xs:element name="Employment" type="hr:EmploymentType"/>
    <xs:element name="Compensation" type="hr:CompensationType"/>
    <xs:element name="Benefits" type="hr:BenefitsType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="SSNType">
  <xs:restriction base="xs:string">
    <xs:pattern value="[0-9]{3}-[0-9]{2}-[0-9]{4}"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="EmploymentTypeEnum">
  <xs:restriction base="xs:string">
    <xs:enumeration value="full-time"/>
    <xs:enumeration value="part-time"/>
    <xs:enumeration value="contractor"/>
    <xs:enumeration value="temporary"/>
    <xs:enumeration value="intern"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- SSN pattern validation (XXX-XX-XXXX)
- Employment type classification
- Compensation with bonus eligibility
- Benefits enrollment tracking
- Emergency contact information
- Manager relationship

**Business Value:**
- Protects sensitive employee information
- Validates SSN and other critical data
- Tracks compensation and benefits
- Supports HRIS integration

---

### 6. Insurance Claim (Insurance)

**Use Case:** Insurance claim processing for auto, home, health, and property insurance

**Schema Highlights:**
```xml
<xs:complexType name="InsuranceClaimType">
  <xs:sequence>
    <xs:element name="ClaimNumber" type="ins:ClaimNumberType"/>
    <xs:element name="PolicyNumber" type="xs:string"/>
    <xs:element name="ClaimType" type="ins:ClaimTypeEnum"/>
    <xs:element name="IncidentDetails" type="ins:IncidentDetailsType"/>
    <xs:element name="DamageAssessment" type="ins:DamageAssessmentType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="ClaimTypeEnum">
  <xs:restriction base="xs:string">
    <xs:enumeration value="auto"/>
    <xs:enumeration value="home"/>
    <xs:enumeration value="health"/>
    <xs:enumeration value="life"/>
    <xs:enumeration value="property"/>
    <xs:enumeration value="liability"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="SeverityType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="minor"/>
    <xs:enumeration value="moderate"/>
    <xs:enumeration value="major"/>
    <xs:enumeration value="total-loss"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Multiple claim types (auto, home, health, etc.)
- Incident details with witness statements
- Damage assessment with severity levels
- Claim amount tracking (requested, approved, paid)
- Adjuster assignment
- Document attachments (photos, police reports, estimates)

**Business Value:**
- Standardizes claim processing workflow
- Tracks claim status lifecycle
- Documents incident details for investigation
- Supports insurance claim automation

---

### 7. Healthcare Patient Record

**Use Case:** HIPAA-compliant patient records for healthcare systems

**Schema Highlights:**
```xml
<xs:complexType name="PatientRecordType">
  <xs:sequence>
    <xs:element name="MedicalRecordNumber" type="hl7:MRNType"/>
    <xs:element name="Demographics" type="hl7:DemographicsType"/>
    <xs:element name="Allergies" type="hl7:AllergiesType" minOccurs="0"/>
    <xs:element name="Conditions" type="hl7:ConditionsType" minOccurs="0"/>
    <xs:element name="Medications" type="hl7:MedicationsType" minOccurs="0"/>
    <xs:element name="VitalSigns" type="hl7:VitalSignsType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="AllergyType">
  <xs:sequence>
    <xs:element name="Allergen" type="xs:string"/>
    <xs:element name="Reaction" type="xs:string"/>
    <xs:element name="Severity" type="hl7:SeverityType"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="ConditionType">
  <xs:sequence>
    <xs:element name="ConditionName" type="xs:string"/>
    <xs:element name="ICD10Code" type="xs:string"/>
    <xs:element name="DiagnosisDate" type="xs:date"/>
    <xs:element name="Status" type="hl7:ConditionStatusType"/>
  </xs:sequence>
</xs:complexType>
```

**Key Features:**
- Medical Record Number (MRN) validation
- Allergy tracking with severity levels
- Conditions with ICD-10 codes
- Medication tracking with prescriber
- Immunization records with CVX codes
- Vital signs (height, weight, blood pressure, etc.)

**Business Value:**
- Ensures HIPAA-compliant data structure
- Supports clinical decision support
- Validates medical codes (ICD-10, CVX)
- Enables EHR/EMR integration

---

### 8. Shipping Manifest (Logistics)

**Use Case:** Freight shipping manifests for logistics and warehouse management

**Schema Highlights:**
```xml
<xs:complexType name="ShippingManifestType">
  <xs:sequence>
    <xs:element name="ManifestNumber" type="ship:ManifestNumberType"/>
    <xs:element name="Shipper" type="ship:PartyType"/>
    <xs:element name="Consignee" type="ship:PartyType"/>
    <xs:element name="Carrier" type="ship:CarrierType"/>
    <xs:element name="Packages" type="ship:PackagesType"/>
    <xs:element name="CustomsInfo" type="ship:CustomsInfoType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:complexType name="PackageType">
  <xs:sequence>
    <xs:element name="Description" type="xs:string"/>
    <xs:element name="Quantity" type="xs:positiveInteger"/>
    <xs:element name="Weight" type="ship:WeightType"/>
    <xs:element name="Dimensions" type="ship:DimensionsType" minOccurs="0"/>
    <xs:element name="HazardousMaterial" type="xs:boolean" default="false"/>
  </xs:sequence>
</xs:complexType>
```

**Key Features:**
- Shipper and consignee party information
- Carrier and service level selection
- Package weight and dimensions
- Shipping cost breakdown (base rate, fuel surcharge, insurance)
- Customs information for international shipments
- Hazardous material flag

**Business Value:**
- Standardizes shipping manifest format
- Supports international shipping (customs)
- Calculates shipping costs accurately
- Tracks packages with carrier integration

---

### 9. Bank Transaction (Finance)

**Use Case:** Bank account transactions for banking and financial systems

**Schema Highlights:**
```xml
<xs:complexType name="BankTransactionType">
  <xs:sequence>
    <xs:element name="TransactionID" type="bank:TransactionIDType"/>
    <xs:element name="TransactionType" type="bank:TransactionTypeEnum"/>
    <xs:element name="Amount" type="bank:MoneyType"/>
    <xs:element name="FromAccount" type="bank:AccountType"/>
    <xs:element name="ToAccount" type="bank:AccountType" minOccurs="0"/>
    <xs:element name="Merchant" type="bank:MerchantType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="TransactionTypeEnum">
  <xs:restriction base="xs:string">
    <xs:enumeration value="debit"/>
    <xs:enumeration value="credit"/>
    <xs:enumeration value="transfer"/>
    <xs:enumeration value="payment"/>
    <xs:enumeration value="withdrawal"/>
    <xs:enumeration value="deposit"/>
    <xs:enumeration value="refund"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="ChannelType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="atm"/>
    <xs:enumeration value="online-banking"/>
    <xs:enumeration value="mobile-app"/>
    <xs:enumeration value="branch"/>
    <xs:enumeration value="pos"/>
    <xs:enumeration value="wire-transfer"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Transaction type classification (debit, credit, transfer, etc.)
- Account information with routing/IBAN support
- Merchant details with MCC (Merchant Category Code)
- Transaction channel tracking
- Location tracking (latitude, longitude)
- Transaction status lifecycle

**Business Value:**
- Standardizes transaction data format
- Tracks transaction channels (ATM, online, mobile, etc.)
- Supports fraud detection (merchant info, location)
- Enables transaction reporting and analysis

---

### 10. Configuration File (System)

**Use Case:** Application configuration for system configuration management

**Schema Highlights:**
```xml
<xs:complexType name="ConfigurationType">
  <xs:sequence>
    <xs:element name="Environment" type="cfg:EnvironmentType"/>
    <xs:element name="Database" type="cfg:DatabaseType"/>
    <xs:element name="Server" type="cfg:ServerType"/>
    <xs:element name="Logging" type="cfg:LoggingType"/>
    <xs:element name="Security" type="cfg:SecurityType"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="EnvironmentType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="development"/>
    <xs:enumeration value="staging"/>
    <xs:enumeration value="production"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="PortType">
  <xs:restriction base="xs:integer">
    <xs:minInclusive value="1"/>
    <xs:maxInclusive value="65535"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- Environment-specific configuration
- Database connection settings
- Server configuration (host, port, threads)
- Logging levels and output settings
- Cache configuration (provider, TTL, max size)
- Security settings (JWT, HTTPS)
- Feature flags

**Business Value:**
- Prevents configuration errors before deployment
- Validates port numbers, database types, etc.
- Documents configuration schema
- Supports environment-specific settings

---

### 11. SOAP API (Web Services)

**Use Case:** SOAP web service request/response definitions

**Schema Highlights:**
```xml
<xs:element name="GetCustomerRequest">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="CustomerID" type="xs:string"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="GetCustomerResponse">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="Customer" type="soap:CustomerType"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>

<xs:element name="ServiceFault">
  <xs:complexType>
    <xs:sequence>
      <xs:element name="FaultCode" type="xs:string"/>
      <xs:element name="FaultMessage" type="xs:string"/>
      <xs:element name="Details" type="xs:string" minOccurs="0"/>
    </xs:sequence>
  </xs:complexType>
</xs:element>
```

**Key Features:**
- Request/response message definitions
- Customer and order operations
- Fault/error handling
- Reusable complex types
- SOAP envelope structure

**Business Value:**
- Defines SOAP web service contract
- Enables WSDL generation
- Supports client code generation
- Documents API request/response structure

---

### 12. Real Estate Listing (MLS)

**Use Case:** Property listings for MLS and real estate platforms

**Schema Highlights:**
```xml
<xs:complexType name="PropertyListingType">
  <xs:sequence>
    <xs:element name="ListingID" type="re:ListingIDType"/>
    <xs:element name="PropertyType" type="re:PropertyTypeEnum"/>
    <xs:element name="Price" type="re:PriceType"/>
    <xs:element name="PropertyDetails" type="re:PropertyDetailsType"/>
    <xs:element name="ListingAgent" type="re:AgentType"/>
    <xs:element name="OpenHouses" type="re:OpenHousesType" minOccurs="0"/>
  </xs:sequence>
</xs:complexType>

<xs:simpleType name="PropertyTypeEnum">
  <xs:restriction base="xs:string">
    <xs:enumeration value="single-family"/>
    <xs:enumeration value="condo"/>
    <xs:enumeration value="townhouse"/>
    <xs:enumeration value="multi-family"/>
    <xs:enumeration value="land"/>
    <xs:enumeration value="commercial"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="ListingIDType">
  <xs:restriction base="xs:string">
    <xs:pattern value="MLS-[0-9]{8}"/>
  </xs:restriction>
</xs:simpleType>
```

**Key Features:**
- MLS ID pattern validation
- Property type classification
- Property details (bedrooms, bathrooms, square feet, etc.)
- Pricing with price per square foot
- Agent information with license number
- Photo gallery
- Open house scheduling

**Business Value:**
- Standardizes MLS data format
- Validates property details
- Supports property search and filtering
- Documents listing data for real estate platforms

---

## Common XSD Patterns

### 1. Pattern Validation

Validate string formats with regular expressions:

```xml
<!-- Customer ID: CUST-YYYY-NNNNNN -->
<xs:simpleType name="CustomerIDType">
  <xs:restriction base="xs:string">
    <xs:pattern value="CUST-[0-9]{4}-[0-9]{6}"/>
  </xs:restriction>
</xs:simpleType>

<!-- SSN: XXX-XX-XXXX -->
<xs:simpleType name="SSNType">
  <xs:restriction base="xs:string">
    <xs:pattern value="[0-9]{3}-[0-9]{2}-[0-9]{4}"/>
  </xs:restriction>
</xs:simpleType>

<!-- Email Address -->
<xs:simpleType name="EmailType">
  <xs:restriction base="xs:string">
    <xs:pattern value="[^@]+@[^\.]+\..+"/>
  </xs:restriction>
</xs:simpleType>

<!-- Phone Number: +[country][number] -->
<xs:simpleType name="PhoneType">
  <xs:restriction base="xs:string">
    <xs:pattern value="\+[0-9]{1,15}"/>
  </xs:restriction>
</xs:simpleType>

<!-- Currency Code: ISO 4217 (3 letters) -->
<xs:simpleType name="CurrencyCodeType">
  <xs:restriction base="xs:string">
    <xs:pattern value="[A-Z]{3}"/>
  </xs:restriction>
</xs:simpleType>

<!-- Country Code: ISO 3166 (2-3 letters) -->
<xs:simpleType name="CountryCodeType">
  <xs:restriction base="xs:string">
    <xs:pattern value="[A-Z]{2,3}"/>
  </xs:restriction>
</xs:simpleType>
```

### 2. Enumerations

Define fixed sets of allowed values:

```xml
<xs:simpleType name="OrderStatusType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="draft"/>
    <xs:enumeration value="submitted"/>
    <xs:enumeration value="approved"/>
    <xs:enumeration value="shipped"/>
    <xs:enumeration value="delivered"/>
    <xs:enumeration value="cancelled"/>
  </xs:restriction>
</xs:simpleType>

<xs:simpleType name="EnvironmentType">
  <xs:restriction base="xs:string">
    <xs:enumeration value="development"/>
    <xs:enumeration value="staging"/>
    <xs:enumeration value="production"/>
  </xs:restriction>
</xs:simpleType>
```

### 3. Numeric Constraints

Validate numeric ranges and precision:

```xml
<!-- Money with 2 decimal places, minimum 0 -->
<xs:simpleType name="MoneyType">
  <xs:restriction base="xs:decimal">
    <xs:fractionDigits value="2"/>
    <xs:minInclusive value="0"/>
  </xs:restriction>
</xs:simpleType>

<!-- Percentage: 0-100 with 4 decimal places -->
<xs:simpleType name="PercentType">
  <xs:restriction base="xs:decimal">
    <xs:fractionDigits value="4"/>
    <xs:minInclusive value="0"/>
    <xs:maxInclusive value="100"/>
  </xs:restriction>
</xs:simpleType>

<!-- Port number: 1-65535 -->
<xs:simpleType name="PortType">
  <xs:restriction base="xs:integer">
    <xs:minInclusive value="1"/>
    <xs:maxInclusive value="65535"/>
  </xs:restriction>
</xs:simpleType>

<!-- Positive integer (greater than 0) -->
<xs:element name="Quantity" type="xs:positiveInteger"/>

<!-- Non-negative integer (0 or greater) -->
<xs:element name="Available" type="xs:nonNegativeInteger"/>
```

### 4. Complex Types

Define reusable complex structures:

```xml
<!-- Address Type (reusable) -->
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element name="Street" type="xs:string"/>
    <xs:element name="City" type="xs:string"/>
    <xs:element name="State" type="xs:string"/>
    <xs:element name="PostalCode" type="xs:string"/>
    <xs:element name="Country" type="CountryCodeType"/>
  </xs:sequence>
</xs:complexType>

<!-- Usage -->
<xs:element name="ShippingAddress" type="AddressType"/>
<xs:element name="BillingAddress" type="AddressType"/>
```

### 5. Optional and Required Elements

Control element occurrence:

```xml
<xs:complexType name="PersonType">
  <xs:sequence>
    <!-- Required elements (default minOccurs="1") -->
    <xs:element name="FirstName" type="xs:string"/>
    <xs:element name="LastName" type="xs:string"/>

    <!-- Optional elements (minOccurs="0") -->
    <xs:element name="MiddleName" type="xs:string" minOccurs="0"/>
    <xs:element name="Email" type="EmailType" minOccurs="0"/>

    <!-- Multiple occurrences -->
    <xs:element name="Phone" type="PhoneType" maxOccurs="unbounded"/>
  </xs:sequence>
</xs:complexType>
```

### 6. Default Values

Specify default values for elements:

```xml
<xs:element name="SSL" type="xs:boolean" default="true"/>
<xs:element name="IsPrimary" type="xs:boolean" default="false"/>
<xs:element name="Currency" type="CurrencyCodeType" default="USD"/>
```

### 7. Namespaces

Use namespaces to avoid naming conflicts:

```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:po="http://example.com/schemas/purchase-order"
           targetNamespace="http://example.com/schemas/purchase-order"
           elementFormDefault="qualified">

  <!-- Elements are in the targetNamespace -->
  <xs:element name="PurchaseOrder" type="po:PurchaseOrderType"/>
</xs:schema>
```

### 8. Documentation

Add documentation to schemas:

```xml
<xs:annotation>
  <xs:documentation>
    Purchase Order Schema for procurement and supply chain management.
    Version: 1.0
    Last Updated: 2024-10-27
  </xs:documentation>
</xs:annotation>

<xs:element name="TTL" type="xs:positiveInteger">
  <xs:annotation>
    <xs:documentation>Time to live in seconds</xs:documentation>
  </xs:annotation>
</xs:element>
```

---

## Validating XML Data

### Using xmllint (Linux/macOS)

```bash
# Validate XML against XSD
xmllint --schema 01_purchase_order.xsd 01_purchase_order.xml --noout

# If valid, no output
# If invalid, error messages are displayed
```

### Using xmlstarlet (Linux/macOS/Windows)

```bash
# Install xmlstarlet
# macOS: brew install xmlstarlet
# Linux: apt-get install xmlstarlet

# Validate XML
xmlstarlet val -e -s 01_purchase_order.xsd 01_purchase_order.xml
```

### Using Java

```java
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class XSDValidator {
    public static void main(String[] args) {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new StreamSource("01_purchase_order.xsd"));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource("01_purchase_order.xml"));
            System.out.println("XML is valid!");
        } catch (Exception e) {
            System.out.println("XML is NOT valid!");
            e.printStackTrace();
        }
    }
}
```

### Using Python (lxml)

```python
from lxml import etree

# Load XSD
with open('01_purchase_order.xsd', 'rb') as f:
    schema_root = etree.XML(f.read())
    schema = etree.XMLSchema(schema_root)

# Load XML
with open('01_purchase_order.xml', 'rb') as f:
    doc = etree.parse(f)

# Validate
if schema.validate(doc):
    print("XML is valid!")
else:
    print("XML is NOT valid!")
    print(schema.error_log)
```

### Using C# (.NET)

```csharp
using System;
using System.Xml;
using System.Xml.Schema;

class XSDValidator
{
    static void Main()
    {
        XmlSchemaSet schemas = new XmlSchemaSet();
        schemas.Add("http://example.com/schemas/purchase-order", "01_purchase_order.xsd");

        XmlReaderSettings settings = new XmlReaderSettings();
        settings.ValidationType = ValidationType.Schema;
        settings.Schemas = schemas;
        settings.ValidationEventHandler += ValidationCallback;

        XmlReader reader = XmlReader.Create("01_purchase_order.xml", settings);

        while (reader.Read()) { }

        Console.WriteLine("Validation complete!");
    }

    static void ValidationCallback(object sender, ValidationEventArgs e)
    {
        Console.WriteLine($"Validation error: {e.Message}");
    }
}
```

---

## UTL-X Integration Examples

### 1. Reading and Validating XML

```utlx
%utlx 1.0
input xml
output json
---
{
  // Parse XML and extract data
  purchaseOrder: {
    poNumber: $input.PurchaseOrder.OrderInfo.PONumber,
    orderDate: $input.PurchaseOrder.OrderInfo.OrderDate,
    vendor: {
      name: $input.PurchaseOrder.Vendor.Name,
      email: $input.PurchaseOrder.Vendor.Email
    },
    lineItems: $input.PurchaseOrder.LineItems.LineItem |> map(item => {
      productId: item.ProductID,
      description: item.Description,
      quantity: toNumber(item.Quantity),
      unitPrice: toNumber(item.UnitPrice),
      total: toNumber(item.LineTotal)
    })
  }
}
```

### 2. Transforming Between Schemas

```utlx
%utlx 1.0
input xml  // Customer XML
output xml // Employee XML
---
<Employee xmlns="http://example.com/schemas/employee">
  <EmployeeID>EMP-{substring($input.Customer.CustomerID, 5)}</EmployeeID>
  <PersonalInfo>
    <FirstName>{$input.Customer.PersonalInfo.FirstName}</FirstName>
    <LastName>{$input.Customer.PersonalInfo.LastName}</LastName>
    <DateOfBirth>{$input.Customer.PersonalInfo.DateOfBirth}</DateOfBirth>
    <Email>{$input.Customer.ContactInfo.PrimaryEmail}</Email>
    <Phone>{$input.Customer.ContactInfo.PrimaryPhone}</Phone>
    <Address>
      <Street>{$input.Customer.Addresses.Address[1].Street}</Street>
      <City>{$input.Customer.Addresses.Address[1].City}</City>
      <State>{$input.Customer.Addresses.Address[1].State}</State>
      <PostalCode>{$input.Customer.Addresses.Address[1].PostalCode}</PostalCode>
      <Country>{$input.Customer.Addresses.Address[1].Country}</Country>
    </Address>
  </PersonalInfo>
</Employee>
```

### 3. Namespace Handling

```utlx
%utlx 1.0
input xml {
  namespaces: {
    "po": "http://example.com/schemas/purchase-order",
    "inv": "http://example.com/schemas/invoice"
  }
}
output json
---
{
  // Access namespaced elements
  invoice: {
    invoiceId: "INV-" + $input.{"po:PurchaseOrder"}.OrderInfo.PONumber,
    vendor: $input.{"po:PurchaseOrder"}.Vendor.Name,
    total: sum($input.{"po:PurchaseOrder"}.LineItems.LineItem.(toNumber(LineTotal)))
  }
}
```

### 4. SOAP API Processing

```utlx
%utlx 1.0
input xml  // SOAP Request
output xml // SOAP Response
---
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:api="http://example.com/schemas/soap-api">
  <soap:Body>
    <api:CreateOrderResponse>
      <api:OrderID>ORD-{generateUUID()}</api:OrderID>
      <api:Status>success</api:Status>
      <api:Message>Order created successfully</api:Message>
    </api:CreateOrderResponse>
  </soap:Body>
</soap:Envelope>
```

### 5. Configuration File Processing

```utlx
%utlx 1.0
input xml  // Configuration XML
output json
---
{
  // Transform XML config to JSON config
  environment: $input.Configuration.Environment,
  database: {
    connectionString: buildConnectionString($input.Configuration.Database),
    poolSize: toNumber($input.Configuration.Database.PoolSize),
    ssl: toBoolean($input.Configuration.Database.SSL)
  },
  server: {
    url: "http://" + $input.Configuration.Server.Host + ":" + $input.Configuration.Server.Port,
    maxThreads: toNumber($input.Configuration.Server.MaxThreads)
  },
  featureFlags: $input.Configuration.FeatureFlags.Feature |> reduce({}, (acc, feature) => {
    ...acc,
    [feature.Name]: toBoolean(feature.Enabled)
  })
}

function buildConnectionString(db: Object): String {
  let type = db.Type,
  let protocol = match type {
    "postgresql" => "postgresql",
    "mysql" => "mysql",
    "mongodb" => "mongodb",
    _ => type
  },
  protocol + "://" + db.Host + ":" + db.Port + "/" + db.DatabaseName
}
```

---

## Best Practices

### 1. Schema Design

**Use Clear Namespace URIs:**
```xml
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           xmlns:po="http://example.com/schemas/purchase-order"
           targetNamespace="http://example.com/schemas/purchase-order"
           elementFormDefault="qualified">
```

**Define Reusable Types:**
```xml
<!-- Define once -->
<xs:complexType name="AddressType">
  <xs:sequence>
    <xs:element name="Street" type="xs:string"/>
    <xs:element name="City" type="xs:string"/>
    <xs:element name="State" type="xs:string"/>
    <xs:element name="PostalCode" type="xs:string"/>
    <xs:element name="Country" type="CountryCodeType"/>
  </xs:sequence>
</xs:complexType>

<!-- Use multiple times -->
<xs:element name="ShippingAddress" type="AddressType"/>
<xs:element name="BillingAddress" type="AddressType"/>
```

**Use Appropriate Constraints:**
```xml
<!-- Enforce data quality -->
<xs:element name="Email" type="EmailType"/>
<xs:element name="Phone" type="PhoneType"/>
<xs:element name="Amount" type="MoneyType"/>
<xs:element name="Status" type="StatusEnum"/>
```

### 2. Schema Evolution

**Add Optional Elements (Backward Compatible):**
```xml
<!-- Version 1.0 -->
<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="CustomerID" type="xs:string"/>
    <xs:element name="Name" type="xs:string"/>
  </xs:sequence>
</xs:complexType>

<!-- Version 2.0 - Add optional element -->
<xs:complexType name="CustomerType">
  <xs:sequence>
    <xs:element name="CustomerID" type="xs:string"/>
    <xs:element name="Name" type="xs:string"/>
    <xs:element name="LoyaltyTier" type="xs:string" minOccurs="0"/><!-- NEW -->
  </xs:sequence>
</xs:complexType>
```

**Use Default Values:**
```xml
<xs:element name="Currency" type="CurrencyCodeType" default="USD"/>
<xs:element name="SSL" type="xs:boolean" default="true"/>
```

**Version Your Schemas:**
```xml
<xs:annotation>
  <xs:documentation>
    Purchase Order Schema
    Version: 2.0
    Last Updated: 2024-10-27
    Changes: Added optional LoyaltyTier element
  </xs:documentation>
</xs:annotation>
```

### 3. Validation Messages

**Add Documentation for Better Error Messages:**
```xml
<xs:element name="PONumber" type="PONumberType">
  <xs:annotation>
    <xs:documentation>
      Purchase Order Number in format PO-YYYY-NNNNNN
      Example: PO-2024-123456
    </xs:documentation>
  </xs:annotation>
</xs:element>

<xs:simpleType name="PONumberType">
  <xs:restriction base="xs:string">
    <xs:pattern value="PO-[0-9]{4}-[0-9]{6}"/>
  </xs:restriction>
</xs:simpleType>
```

### 4. Performance

**Avoid Deep Nesting:**
```xml
<!-- Good - Flat structure with type references -->
<xs:complexType name="OrderType">
  <xs:sequence>
    <xs:element name="OrderID" type="xs:string"/>
    <xs:element name="Customer" type="CustomerType"/><!-- Reference -->
    <xs:element name="Items" type="OrderItemsType"/><!-- Reference -->
  </xs:sequence>
</xs:complexType>

<!-- Avoid - Deep nesting -->
<xs:complexType name="OrderType">
  <xs:sequence>
    <xs:element name="OrderID" type="xs:string"/>
    <xs:element name="Customer">
      <xs:complexType>
        <xs:sequence>
          <xs:element name="Address">
            <xs:complexType>
              <!-- Too deep! -->
            </xs:complexType>
          </xs:element>
        </xs:sequence>
      </xs:complexType>
    </xs:element>
  </xs:sequence>
</xs:complexType>
```

**Use minOccurs/maxOccurs Wisely:**
```xml
<!-- Good - Specific constraints -->
<xs:element name="LineItem" minOccurs="1" maxOccurs="100"/>

<!-- Avoid - Unbounded when not necessary -->
<xs:element name="LineItem" maxOccurs="unbounded"/>
```

---

## Integration Scenarios

### 1. SOAP Web Services

**Use XSD to define WSDL types:**
```xml
<!-- In WSDL -->
<types>
  <xs:schema>
    <xs:import namespace="http://example.com/schemas/soap-api"
               schemaLocation="11_soap_api.xsd"/>
  </xs:schema>
</types>

<message name="GetCustomerRequest">
  <part name="parameters" element="api:GetCustomerRequest"/>
</message>

<message name="GetCustomerResponse">
  <part name="parameters" element="api:GetCustomerResponse"/>
</message>
```

### 2. Enterprise Data Exchange

**Use XSD for B2B data interchange:**
```xml
<!-- Purchase Order Exchange -->
<xs:schema targetNamespace="http://example.com/edi/purchase-order">
  <xs:element name="PurchaseOrder" type="PurchaseOrderType"/>

  <!-- Define EDI-specific constraints -->
  <xs:simpleType name="PONumberType">
    <xs:restriction base="xs:string">
      <xs:pattern value="PO-[0-9]{4}-[0-9]{6}"/>
    </xs:restriction>
  </xs:simpleType>
</xs:schema>
```

### 3. Configuration Management

**Use XSD to validate application configurations:**
```bash
# Validate config before deployment
xmllint --schema 10_configuration.xsd config/production.xml --noout

if [ $? -eq 0 ]; then
  echo "Configuration is valid, deploying..."
  deploy-app config/production.xml
else
  echo "Configuration validation failed!"
  exit 1
fi
```

### 4. Code Generation

**Generate Java classes from XSD:**
```bash
# Using JAXB xjc tool
xjc -d src/main/java -p com.example.model 01_purchase_order.xsd

# Using Maven plugin
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>jaxb2-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>xjc</goal>
      </goals>
      <configuration>
        <sources>
          <source>src/main/xsd/01_purchase_order.xsd</source>
        </sources>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Generate C# classes from XSD:**
```bash
# Using xsd.exe tool
xsd.exe 01_purchase_order.xsd /classes /language:CS /namespace:Example.Model
```

### 5. Documentation Generation

**Generate HTML documentation from XSD:**
```bash
# Using xs3p (XSLT-based tool)
xsltproc xs3p.xsl 01_purchase_order.xsd > purchase_order_doc.html
```

---

## Resources

### XML Schema Specification
- [W3C XML Schema Part 1: Structures](https://www.w3.org/TR/xmlschema-1/)
- [W3C XML Schema Part 2: Datatypes](https://www.w3.org/TR/xmlschema-2/)
- [XML Schema Tutorial (W3Schools)](https://www.w3.org/TR/xmlschema11-1/)

### Validation Tools
- [xmllint](http://xmlsoft.org/xmllint.html) - Command-line XML validator (libxml2)
- [xmlstarlet](http://xmlstar.sourceforge.net/) - Command-line XML toolkit
- [Xerces](https://xerces.apache.org/) - XML parser (Java/C++)
- [Saxon](https://www.saxonica.com/) - XSLT and XQuery processor

### Code Generation Tools
- [JAXB](https://javaee.github.io/jaxb-v2/) - Java Architecture for XML Binding
- [xsd.exe](https://docs.microsoft.com/en-us/dotnet/standard/serialization/xml-schema-definition-tool-xsd-exe) - .NET XML Schema Definition Tool
- [xjc](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/xjc.html) - JAXB Binding Compiler
- [xmlbeans](https://xmlbeans.apache.org/) - XML-Java binding framework

### Learning Resources
- [XML Schema Tutorial (W3Schools)](https://www.w3schools.com/xml/schema_intro.asp)
- [XML Schema Best Practices](https://www.ibm.com/docs/en/integration-bus/10.0?topic=schemas-xml-schema-design-best-practices)
- [Definitive XML Schema (O'Reilly)](https://www.oreilly.com/library/view/definitive-xml-schema/0596002521/)

### UTL-X Resources
- [UTL-X Documentation](../../docs/)
- [UTL-X Standard Library](../../stdlib/)
- [UTL-X Examples](../)

---

## Contributing

To add new XSD examples:

1. Create a schema file: `NN_example_name.xsd`
2. Create sample XML data: `NN_example_name.xml`
3. Validate the XML against the schema
4. Update this README with the new example
5. Add UTL-X integration examples if applicable

**Schema File Naming Convention:**
- Use descriptive names with underscores
- Prefix with number (01-99) for ordering
- Use `.xsd` extension

**Sample Data Naming Convention:**
- Match the schema file name
- Use `.xml` extension
- Include realistic, production-like data

---

## License

These examples are provided as part of the UTL-X project and follow the project's licensing terms (AGPL-3.0 / Commercial).
