# Protocol Buffers Business Examples

This directory contains a comprehensive collection of real-world Protocol Buffers (protobuf) schema definitions covering common business domains. These examples demonstrate best practices for modeling complex business entities using protobuf's type system.

## Overview

Protocol Buffers is a language-neutral, platform-neutral, extensible mechanism for serializing structured data. These examples showcase how to model real-world business scenarios using proto3 syntax.

## Examples Collection

### 1. E-commerce Order Management (`01_ecommerce_order.proto`)
**Use Case:** Processing customer orders in online stores

**Key Features:**
- Order lifecycle management (pending → confirmed → processing → shipped → delivered)
- Order items with pricing, tax, and discount calculations
- Shipping and billing addresses
- Payment processing integration
- Multi-currency support

**Main Messages:**
- `Order` - Complete order with status, items, addresses, payment
- `OrderItem` - Product details with quantity, pricing, attributes
- `PaymentInfo` - Payment method, transaction tracking, status
- `Pricing` - Subtotal, tax, shipping, discounts, total

**Business Scenarios:**
- Online retail checkout flow
- Order fulfillment systems
- Customer order history
- Returns and refunds processing

---

### 2. Payment Transaction Processing (`02_payment_transaction.proto`)
**Use Case:** Payment gateway processing, fraud detection, reconciliation

**Key Features:**
- Transaction lifecycle (initiated → processing → success/failed/declined)
- Multiple payment types (card, bank account, digital wallet, cryptocurrency)
- Card details with masking and security
- Risk assessment and fraud detection
- Geo-location tracking

**Main Messages:**
- `PaymentTransaction` - Complete transaction with risk assessment
- `Card` - Card details with masked number, brand, expiration
- `RiskAssessment` - Fraud detection with risk score, flags, geo-location
- `Money` - Currency and amount in both cents and decimal

**Business Scenarios:**
- Credit card payment processing
- Fraud prevention systems
- Payment reconciliation
- Chargeback management

---

### 3. Customer Profile Management (`03_customer_profile.proto`)
**Use Case:** CRM systems, customer 360 view, personalization

**Key Features:**
- Complete customer demographics
- Contact information with verification
- Customer segmentation and tiering (Bronze → Silver → Gold → Platinum → VIP)
- Communication preferences and consent management (GDPR compliant)
- Loyalty program integration
- Customer metrics (lifetime value, total orders, satisfaction score)

**Main Messages:**
- `CustomerProfile` - Complete customer view with all attributes
- `PersonalInfo` - Demographics, birth date, gender, nationality
- `LoyaltyInfo` - Points, tier, membership details
- `CustomerMetrics` - Purchase history, lifetime value, behavior metrics
- `Consent` - GDPR/privacy compliance tracking

**Business Scenarios:**
- Customer relationship management
- Marketing personalization
- Loyalty program management
- Customer analytics and segmentation

---

### 4. Product Catalog Management (`04_product_catalog.proto`)
**Use Case:** E-commerce platforms, inventory systems, PIM (Product Information Management)

**Key Features:**
- Product variants (size, color, material)
- Multi-tiered pricing (base, sale, cost, volume discounts)
- Inventory tracking across warehouses
- Media assets (images, videos, 3D models)
- SEO metadata
- Category hierarchies

**Main Messages:**
- `Product` - Complete product with variants, pricing, inventory, media
- `ProductVariant` - SKU, attributes, pricing, inventory per variant
- `Pricing` - Base price, sale price, tax info, volume tiers
- `Inventory` - Stock levels, warehouse locations, reorder points
- `Media` - Images, videos, documents with dimensions

**Business Scenarios:**
- Product information management
- E-commerce catalogs
- Inventory management
- Multi-channel retail

---

### 5. Inventory & Warehouse Management (`05_inventory_management.proto`)
**Use Case:** Supply chain, warehouse operations, stock tracking

**Key Features:**
- Inventory transactions (receipt, shipment, adjustment, transfer)
- Stock level tracking (on-hand, available, reserved, in-transit)
- Warehouse structure (zones, locations, capacity)
- Temperature-controlled storage
- Stock alerts (low stock, out of stock, expiry warnings)
- Operating hours and holiday schedules

**Main Messages:**
- `InventoryTransaction` - All inventory movements with before/after levels
- `StockLevel` - Detailed stock quantities by status
- `Warehouse` - Complete facility with zones, capacity, operating hours
- `Zone` - Storage zones with temperature control
- `StockAlert` - Automated alerts for stock issues

**Business Scenarios:**
- Warehouse management systems (WMS)
- Supply chain optimization
- Inventory cycle counting
- Multi-location stock management

---

### 6. Shipping & Logistics (`06_shipping_logistics.proto`)
**Use Case:** Package tracking, carrier management, delivery optimization

**Key Features:**
- Multi-carrier support (FedEx, UPS, DHL, USPS)
- Service levels (Ground, Express, Overnight)
- Package tracking with event history
- Customs and international shipping
- Delivery cost breakdown
- Shipping documents (labels, invoices, BOL)

**Main Messages:**
- `Shipment` - Complete shipment with tracking, carrier, package details
- `TrackingEvent` - Shipment status updates with location and timestamp
- `Package` - Dimensions, weight, items, hazmat info
- `CustomsInfo` - International shipping declarations
- `ShipmentCost` - Rates, surcharges, duties, taxes

**Business Scenarios:**
- Package tracking systems
- Carrier integration
- International shipping
- Logistics optimization

---

### 7. Employee & HR Management (`07_employee_hr.proto`)
**Use Case:** HRIS systems, payroll processing, benefits administration

**Key Features:**
- Complete employee profiles with personal and employment data
- Compensation management (salary, bonuses, commissions, deductions)
- Benefits enrollment (health, dental, vision, retirement, life insurance)
- Time-off tracking (vacation, sick, personal, maternity/paternity)
- Performance reviews and goals
- Position history tracking

**Main Messages:**
- `Employee` - Complete employee record with all HR data
- `Compensation` - Salary, pay frequency, bonuses, commissions, deductions
- `Benefits` - Health insurance, retirement plans, additional benefits
- `TimeOff` - Balances and requests for all time-off types
- `Performance` - Reviews, ratings, goals, training

**Business Scenarios:**
- Human resources information systems (HRIS)
- Payroll processing
- Benefits administration
- Performance management

---

### 8. Healthcare Records (`08_healthcare_records.proto`)
**Use Case:** EHR/EMR systems, patient records, medical appointments, HIPAA compliance

**Key Features:**
- HIPAA-compliant patient records
- Medical history (conditions, surgeries, hospitalizations)
- Appointment scheduling and tracking
- Prescription management
- Lab results with reference ranges
- Vitals tracking
- Allergy and diagnosis management

**Main Messages:**
- `PatientRecord` - Complete medical record with demographics, insurance, history
- `MedicalHistory` - Conditions, surgeries, family history, social history
- `Prescription` - Medication details, dosage, refills, status
- `LabResult` - Test results with values, units, reference ranges
- `Vitals` - Temperature, blood pressure, heart rate, weight, BMI

**Business Scenarios:**
- Electronic health records (EHR)
- Practice management systems
- Patient portals
- Medical billing

---

### 9. Financial Services & Banking (`09_financial_services.proto`)
**Use Case:** Banking systems, account management, transactions, loans, investments

**Key Features:**
- Multiple account types (checking, savings, investment, retirement)
- Transaction processing with merchant categorization
- Card management (debit, credit, prepaid)
- Loan origination and servicing
- Investment portfolio tracking
- KYC/AML compliance

**Main Messages:**
- `Account` - Bank account with balance, interest, holders
- `Transaction` - Financial transactions with merchant and location data
- `Card` - Debit/credit card with limits and security
- `Loan` - Loan details with payment schedule and collateral
- `Investment` - Securities holdings with cost basis and performance
- `Customer` - Customer profile with credit score and risk assessment

**Business Scenarios:**
- Core banking systems
- Digital banking platforms
- Loan origination systems
- Investment management

---

### 10. IoT Device Management & Telemetry (`10_iot_telemetry.proto`)
**Use Case:** Smart devices, sensor networks, industrial IoT, home automation

**Key Features:**
- Device registration and management
- Multi-protocol connectivity (WiFi, cellular, Bluetooth, Zigbee, LoRa)
- Real-time telemetry data collection
- Sensor readings (temperature, humidity, motion, air quality)
- Remote command and control
- Alerting and automation rules

**Main Messages:**
- `Device` - Device metadata, connectivity, power, capabilities
- `TelemetryData` - Sensor measurements with timestamps and quality indicators
- `SensorReading` - Specific sensor types (temperature, humidity, pressure, etc.)
- `Command` - Remote device commands with status tracking
- `Alert` - Device alerts with severity and acknowledgment
- `Automation` - Rule-based automation with triggers and actions

**Business Scenarios:**
- Smart home systems
- Industrial IoT monitoring
- Environmental monitoring
- Asset tracking

---

### 11. Booking & Reservation Systems (`11_booking_reservation.proto`)
**Use Case:** Hotel reservations, flight bookings, event tickets, restaurant reservations

**Key Features:**
- Multi-type reservations (hotel, flight, car rental, restaurant, event)
- Guest management with preferences
- Dynamic pricing with fees, taxes, discounts
- Cancellation policies and penalties
- Loyalty program integration
- Special requests and accessibility needs

**Main Messages:**
- `Reservation` - Universal reservation with dates, guests, payment, pricing
- `HotelReservation` - Property, room, services, check-in/out details
- `FlightReservation` - Flights, passengers, baggage, seats, PNR
- `RestaurantReservation` - Table, party size, occasion
- `CancellationPolicy` - Refund rules and penalties

**Business Scenarios:**
- Hotel property management systems (PMS)
- Airline reservation systems
- Restaurant booking platforms
- Event ticketing

---

### 12. Marketing & Analytics (`12_marketing_analytics.proto`)
**Use Case:** Digital marketing campaigns, user analytics, conversion tracking, attribution

**Key Features:**
- Multi-channel campaign management (email, social, search, display, video)
- Audience targeting (demographics, geographic, behavioral, contextual)
- Performance metrics and KPIs
- Attribution modeling (first-touch, last-touch, linear, time-decay)
- A/B testing and experimentation
- User event tracking and funnel analysis

**Main Messages:**
- `Campaign` - Campaign with budget, targeting, creatives, channels
- `Targeting` - Audience segmentation criteria
- `Performance` - Metrics including impressions, clicks, conversions, revenue
- `UserEvent` - User interactions with properties, device, location
- `Funnel` - Conversion funnel with step-by-step metrics
- `ABTest` - Experiments with variants and statistical results

**Business Scenarios:**
- Marketing automation platforms
- Analytics and attribution
- A/B testing platforms
- Customer data platforms (CDP)

---

## Common Patterns

### Enumerations
All examples use enums extensively for:
- Status fields (with _UNSPECIFIED = 0 as default)
- Type categorizations
- Fixed value sets
- State machines

Example:
```protobuf
enum OrderStatus {
  ORDER_STATUS_UNSPECIFIED = 0;
  PENDING = 1;
  CONFIRMED = 2;
  PROCESSING = 3;
  SHIPPED = 4;
  DELIVERED = 5;
  CANCELLED = 6;
  REFUNDED = 7;
}
```

### Money Representation
Consistent monetary value handling:
```protobuf
message Money {
  string currency = 1;     // ISO 4217 code (USD, EUR, GBP)
  double amount = 2;       // Or int64 amount_cents for precision
}
```

### Timestamps
Using Unix timestamp (int64) for date/time fields:
```protobuf
int64 created_at = 1;      // Milliseconds since epoch
int64 updated_at = 2;
```

### Addresses
Standardized address structure:
```protobuf
message Address {
  string street1 = 1;
  string street2 = 2;
  string city = 3;
  string state = 4;
  string postal_code = 5;
  string country = 6;
}
```

### Contact Information
Reusable contact patterns:
```protobuf
message ContactInfo {
  ContactType type = 1;
  string value = 2;
  bool is_primary = 3;
}

enum ContactType {
  CONTACT_TYPE_UNSPECIFIED = 0;
  PHONE = 1;
  EMAIL = 2;
  MOBILE = 3;
}
```

### Metadata and Custom Fields
Extensibility through maps:
```protobuf
map<string, string> custom_fields = 15;
map<string, string> metadata = 16;
repeated string tags = 17;
```

## Best Practices Demonstrated

### 1. Naming Conventions
- **Messages:** PascalCase (e.g., `CustomerProfile`, `OrderItem`)
- **Fields:** snake_case (e.g., `customer_id`, `created_at`)
- **Enums:** UPPER_SNAKE_CASE (e.g., `ORDER_STATUS_UNSPECIFIED`)
- **Enum types:** PascalCase (e.g., `OrderStatus`, `PaymentMethod`)

### 2. Field Numbering
- Reserve 1-15 for frequently used fields (single-byte encoding)
- Use sequential numbering
- Never reuse field numbers
- Reserve numbers for deprecated fields

### 3. Required vs Optional
- All fields are optional by default in proto3
- Use validation at application layer
- Document business rules in comments

### 4. Repeated Fields
- Use `repeated` for arrays/lists
- No need for wrapper messages for simple lists
- Consider pagination for large lists

### 5. Maps
- Use maps for key-value pairs: `map<string, string>`
- Keys must be scalar types (no enums or messages)
- Values can be any type

### 6. Nested Messages
- Define related messages within parent for namespace clarity
- Reuse common messages across files
- Balance between nesting and top-level definitions

### 7. Versioning
- Add new fields with new numbers
- Never remove or reuse field numbers
- Use reserved keyword for deprecated fields
- Maintain backward compatibility

### 8. Documentation
- Add comments explaining business rules
- Document field constraints
- Include examples in comments
- Specify units for numeric fields

## Integration with UTL-X

These protobuf schemas can be used with UTL-X for:

1. **Schema Validation**: Validate data against protobuf definitions
2. **Data Transformation**: Convert between protobuf and other formats (JSON, XML, CSV)
3. **API Integration**: Parse and generate protobuf messages in transformations
4. **Type Safety**: Leverage protobuf's strong typing in UTL-X transformations

Example UTL-X transformation using protobuf:
```utlx
%utlx 1.0
input protobuf schema=ecommerce_order.proto
output json
---
{
  orderId: $input.order_id,
  customer: $input.customer_id,
  status: $input.status,
  total: $input.pricing.total.amount
}
```

## Building and Using

### Compilation
Compile protobuf definitions to your target language:

```bash
# For Java
protoc --java_out=./generated 01_ecommerce_order.proto

# For Python
protoc --python_out=./generated 01_ecommerce_order.proto

# For Go
protoc --go_out=./generated 01_ecommerce_order.proto

# For JavaScript
protoc --js_out=./generated 01_ecommerce_order.proto

# For C++
protoc --cpp_out=./generated 01_ecommerce_order.proto
```

### Validation
Validate protobuf files:
```bash
protoc --descriptor_set_out=/dev/null --include_imports *.proto
```

## Resources

- [Protocol Buffers Documentation](https://developers.google.com/protocol-buffers)
- [Proto3 Language Guide](https://developers.google.com/protocol-buffers/docs/proto3)
- [Style Guide](https://developers.google.com/protocol-buffers/docs/style)
- [Best Practices](https://developers.google.com/protocol-buffers/docs/dos-and-donts)

## License

These examples are provided under the same license as the UTL-X project (AGPL-3.0 / Commercial dual-license).

## Contributing

When adding new examples:
1. Follow the established patterns and naming conventions
2. Include comprehensive business domain coverage
3. Add detailed comments explaining fields
4. Update this README with the new example
5. Validate protobuf syntax before committing

## Questions and Feedback

For questions about these examples or suggestions for additional business domains, please open an issue in the UTL-X repository.
