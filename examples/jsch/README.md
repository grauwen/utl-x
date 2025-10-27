# JSON Schema Examples for UTL-X

This directory contains 12 real-world business JSON Schema examples demonstrating various industries and use cases. Each example includes both a JSON Schema definition (`.schema.json`) and corresponding sample JSON data (`.json`) that validates against the schema.

## About JSON Schema

JSON Schema is a vocabulary that allows you to annotate and validate JSON documents. It provides a contract for JSON data, describing:
- Required and optional properties
- Data types and formats
- Validation constraints (patterns, min/max values, etc.)
- Documentation and metadata
- Reusable schema components

JSON Schema is widely used for:
- API request/response validation
- Configuration file validation
- Data interchange format definition
- Documentation generation
- Code generation (TypeScript, Java, etc.)

## Example Catalog

| # | Example | Schema File | Data File | Description |
|---|---------|-------------|-----------|-------------|
| 1 | Customer Profile | `01_customer_profile.schema.json` | `01_customer_profile.json` | CRM customer master data with loyalty tiers, preferences, and conditional validation |
| 2 | Product Catalog | `02_product_catalog.schema.json` | `02_product_catalog.json` | E-commerce product catalog with pricing, inventory, and ratings |
| 3 | E-commerce Order | `03_ecommerce_order.schema.json` | `03_ecommerce_order.json` | Complete order processing with line items, shipping, and payment |
| 4 | API Request/Response | `04_api_request_response.schema.json` | `04_api_request_response.json` | Generic REST API envelope with pagination and error handling |
| 5 | Configuration File | `05_configuration_file.schema.json` | `05_configuration_file.json` | Application configuration with database, server, logging, and monitoring |
| 6 | Employee Record | `06_employee_record.schema.json` | `06_employee_record.json` | HR employee master data with position, compensation, and benefits |
| 7 | Invoice | `07_invoice.schema.json` | `07_invoice.json` | Business invoice with reusable party definitions and payment tracking |
| 8 | Event Log | `08_event_log.schema.json` | `08_event_log.json` | Application event logging for audit trails and monitoring |
| 9 | IoT Telemetry | `09_iot_telemetry.schema.json` | `09_iot_telemetry.json` | IoT device telemetry with measurements, alerts, and geolocation |
| 10 | Healthcare Appointment | `10_healthcare_appointment.schema.json` | `10_healthcare_appointment.json` | Healthcare appointment scheduling with patient, provider, and insurance |
| 11 | Real Estate Listing | `11_real_estate_listing.schema.json` | `11_real_estate_listing.json` | MLS property listing with features, pricing, and open houses |
| 12 | Webhook Event | `12_webhook_event.schema.json` | `12_webhook_event.json` | Generic webhook event for third-party integrations with signatures |

## Detailed Examples

### 1. Customer Profile (CRM)

**Use Case:** Customer master data for CRM and e-commerce platforms

**Schema Highlights:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schemas/customer-profile.schema.json",
  "title": "Customer Profile",
  "type": "object",
  "required": ["customerId", "email", "customerType", "createdAt"],
  "properties": {
    "customerId": {
      "type": "string",
      "pattern": "^CUST-[0-9]{4}-[0-9]{6}$"
    },
    "email": {
      "type": "string",
      "format": "email"
    },
    "loyaltyTier": {
      "type": "string",
      "enum": ["bronze", "silver", "gold", "platinum"]
    }
  },
  "if": {
    "properties": { "customerType": { "const": "business" } }
  },
  "then": {
    "required": ["companyName", "taxId"]
  }
}
```

**Key Features:**
- Pattern validation for customer IDs
- Email format validation
- Enum constraints for loyalty tiers
- Conditional required fields (if/then/else) based on customer type
- Address and contact information

**Business Value:**
- Ensures data consistency across systems
- Validates customer data at API boundaries
- Documents customer data structure for integrations

---

### 2. Product Catalog (E-commerce)

**Use Case:** Product catalog entries for e-commerce platforms and inventory systems

**Schema Highlights:**
```json
{
  "price": {
    "type": "object",
    "required": ["amount", "currency"],
    "properties": {
      "amount": {
        "type": "number",
        "minimum": 0,
        "exclusiveMinimum": true
      },
      "currency": {
        "type": "string",
        "pattern": "^[A-Z]{3}$"
      }
    }
  },
  "inventory": {
    "type": "object",
    "properties": {
      "available": {
        "type": "integer",
        "minimum": 0
      },
      "reserved": {
        "type": "integer",
        "minimum": 0,
        "default": 0
      }
    }
  }
}
```

**Key Features:**
- Nested objects for price and inventory
- Minimum/maximum constraints for numeric values
- Pattern validation for currency codes
- Default values for optional fields
- Arrays for categories and images

**Business Value:**
- Prevents invalid pricing (e.g., negative prices)
- Standardizes currency representation
- Ensures inventory data integrity

---

### 3. E-commerce Order

**Use Case:** Complete order processing for e-commerce platforms

**Schema Highlights:**
```json
{
  "$defs": {
    "address": {
      "type": "object",
      "required": ["street", "city", "state", "postalCode", "country"],
      "properties": {
        "street": { "type": "string", "minLength": 1 },
        "city": { "type": "string", "minLength": 1 },
        "state": { "type": "string", "minLength": 2, "maxLength": 2 },
        "postalCode": { "type": "string" },
        "country": { "type": "string", "pattern": "^[A-Z]{2,3}$" }
      }
    }
  },
  "properties": {
    "shippingAddress": { "$ref": "#/$defs/address" },
    "billingAddress": { "$ref": "#/$defs/address" }
  }
}
```

**Key Features:**
- Reusable schema definitions with `$defs` and `$ref`
- Order status progression with enum
- Line items array with quantity and pricing
- Separate shipping and billing addresses
- Payment information

**Business Value:**
- Ensures address data consistency through reusable definitions
- Validates order totals and calculations
- Documents order lifecycle states

---

### 4. API Request/Response

**Use Case:** Generic REST API envelope for standardized API communication

**Schema Highlights:**
```json
{
  "properties": {
    "request": {
      "type": "object",
      "properties": {
        "method": {
          "type": "string",
          "enum": ["GET", "POST", "PUT", "PATCH", "DELETE"]
        },
        "queryParams": {
          "type": "object",
          "additionalProperties": {
            "oneOf": [
              { "type": "string" },
              { "type": "number" },
              { "type": "boolean" }
            ]
          }
        },
        "ipAddress": {
          "type": "string",
          "oneOf": [
            { "format": "ipv4" },
            { "format": "ipv6" }
          ]
        }
      }
    },
    "response": {
      "type": "object",
      "properties": {
        "statusCode": {
          "type": "integer",
          "minimum": 100,
          "maximum": 599
        }
      }
    }
  }
}
```

**Key Features:**
- HTTP method validation with enum
- IP address format validation (IPv4/IPv6)
- UUID format validation for request IDs
- Flexible query parameter types with oneOf
- Pagination support
- Error array for multiple error messages

**Business Value:**
- Standardizes API request/response structure
- Enables automatic API documentation
- Validates HTTP status codes and methods
- Supports API monitoring and logging

---

### 5. Configuration File

**Use Case:** Application configuration for database, server, logging, and monitoring

**Schema Highlights:**
```json
{
  "properties": {
    "environment": {
      "type": "string",
      "enum": ["development", "staging", "production"]
    },
    "database": {
      "type": "object",
      "required": ["host", "port", "name", "type"],
      "properties": {
        "type": {
          "type": "string",
          "enum": ["postgresql", "mysql", "mongodb", "redis"]
        },
        "port": {
          "type": "integer",
          "minimum": 1,
          "maximum": 65535
        },
        "ssl": {
          "type": "boolean",
          "default": true
        }
      }
    },
    "featureFlags": {
      "type": "object",
      "additionalProperties": {
        "type": "boolean"
      }
    }
  }
}
```

**Key Features:**
- Environment-specific validation
- Database connection settings with type enum
- Port range validation (1-65535)
- Logging levels with enum
- Feature flags with flexible boolean properties
- Default values for optional settings

**Business Value:**
- Prevents configuration errors before deployment
- Documents configuration schema
- Enables environment-specific validation
- Supports feature flag management

---

### 6. Employee Record (HR)

**Use Case:** Employee master data for HR and payroll systems

**Schema Highlights:**
```json
{
  "properties": {
    "personalInfo": {
      "type": "object",
      "required": ["firstName", "lastName", "dateOfBirth", "ssn"],
      "properties": {
        "ssn": {
          "type": "string",
          "pattern": "^[0-9]{3}-[0-9]{2}-[0-9]{4}$"
        },
        "email": {
          "type": "string",
          "format": "email"
        }
      }
    },
    "compensation": {
      "type": "object",
      "properties": {
        "salary": {
          "type": "number",
          "minimum": 0,
          "exclusiveMinimum": true
        },
        "currency": {
          "type": "string",
          "pattern": "^[A-Z]{3}$",
          "default": "USD"
        }
      }
    }
  }
}
```

**Key Features:**
- SSN pattern validation (XXX-XX-XXXX)
- Nested objects for personal info, employment, position, compensation
- Employment status enum (full-time, part-time, contractor)
- Benefits array
- Email format validation

**Business Value:**
- Protects sensitive personal information with validation
- Ensures consistent employee data structure
- Validates compensation and benefit enrollment
- Documents HR data for HRIS integrations

---

### 7. Invoice

**Use Case:** Business invoices for accounting and billing systems

**Schema Highlights:**
```json
{
  "$defs": {
    "party": {
      "type": "object",
      "required": ["name", "address", "email"],
      "properties": {
        "taxId": { "type": "string" },
        "email": { "type": "string", "format": "email" },
        "address": { "type": "string" }
      }
    }
  },
  "properties": {
    "seller": { "$ref": "#/$defs/party" },
    "buyer": { "$ref": "#/$defs/party" },
    "lineItems": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["description", "quantity", "unitPrice", "total"],
        "properties": {
          "quantity": {
            "type": "number",
            "minimum": 0,
            "exclusiveMinimum": true
          }
        }
      }
    }
  }
}
```

**Key Features:**
- Reusable party definition for seller and buyer
- Line items array with quantity and pricing
- Tax calculation per line item
- Payment terms and status
- Payment tracking with amounts and methods

**Business Value:**
- Ensures invoice data consistency
- Validates calculations (line totals, tax, total)
- Reusable party schema reduces duplication
- Tracks payment status and methods

---

### 8. Event Log (Audit Trail)

**Use Case:** Application event logging for audit trails, monitoring, and compliance

**Schema Highlights:**
```json
{
  "properties": {
    "eventType": {
      "type": "string",
      "enum": ["user_action", "system_event", "data_change", "error", "security", "performance"]
    },
    "severity": {
      "type": "string",
      "enum": ["debug", "info", "warning", "error", "critical"]
    },
    "source": {
      "type": "object",
      "properties": {
        "service": { "type": "string" },
        "environment": {
          "type": "string",
          "enum": ["development", "staging", "production"]
        }
      }
    },
    "actor": {
      "type": "object",
      "properties": {
        "userId": { "type": "string" },
        "ipAddress": { "type": "string", "format": "ipv4" }
      }
    }
  }
}
```

**Key Features:**
- Event type classification with enum
- Severity levels for filtering and alerting
- Source tracking (service, host, environment)
- Actor identification (user, IP, session)
- Action details with before/after state
- Error information for failure events
- Performance metrics (duration, memory)

**Business Value:**
- Standardizes event logging across services
- Enables compliance and audit requirements
- Supports debugging and troubleshooting
- Provides security event tracking

---

### 9. IoT Telemetry

**Use Case:** IoT device telemetry data for manufacturing, smart buildings, and industrial IoT

**Schema Highlights:**
```json
{
  "properties": {
    "deviceType": {
      "type": "string",
      "enum": ["temperature", "pressure", "humidity", "motion", "energy", "vibration"]
    },
    "measurements": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name", "value", "unit"],
        "properties": {
          "value": { "type": "number" },
          "unit": { "type": "string" },
          "quality": {
            "type": "integer",
            "minimum": 0,
            "maximum": 100
          }
        }
      }
    },
    "deviceStatus": {
      "type": "object",
      "properties": {
        "health": {
          "type": "string",
          "enum": ["healthy", "warning", "critical", "offline"]
        },
        "batteryLevel": {
          "type": "number",
          "minimum": 0,
          "maximum": 100
        }
      }
    }
  }
}
```

**Key Features:**
- Device type classification
- Measurements array with quality indicators
- Device health status and battery level
- Alert detection and tracking
- Geolocation support

**Business Value:**
- Validates sensor data quality
- Standardizes telemetry across device types
- Supports predictive maintenance with health status
- Enables geospatial analysis

---

### 10. Healthcare Appointment

**Use Case:** Healthcare appointment scheduling for clinics and hospitals

**Schema Highlights:**
```json
{
  "properties": {
    "provider": {
      "type": "object",
      "properties": {
        "npi": {
          "type": "string",
          "pattern": "^[0-9]{10}$",
          "description": "National Provider Identifier (10 digits)"
        },
        "specialty": { "type": "string" }
      }
    },
    "appointmentType": {
      "type": "string",
      "enum": ["annual-physical", "follow-up", "consultation", "emergency", "telehealth"]
    },
    "duration": {
      "type": "integer",
      "minimum": 15,
      "multipleOf": 15,
      "description": "Duration in minutes (15-minute increments)"
    },
    "status": {
      "type": "string",
      "enum": ["scheduled", "confirmed", "checked-in", "in-progress", "completed", "cancelled", "no-show"]
    }
  }
}
```

**Key Features:**
- NPI pattern validation (10-digit provider identifier)
- Appointment type classification
- Duration validation (15-minute increments)
- Patient and provider information
- Facility and insurance details
- Reminder tracking

**Business Value:**
- Ensures HIPAA-compliant data structure
- Validates provider credentials (NPI)
- Standardizes appointment scheduling
- Tracks insurance information

---

### 11. Real Estate Listing

**Use Case:** Property listings for MLS and real estate platforms

**Schema Highlights:**
```json
{
  "properties": {
    "listingId": {
      "type": "string",
      "pattern": "^MLS-[0-9]{8}$"
    },
    "property": {
      "type": "object",
      "required": ["address", "propertyType"],
      "properties": {
        "propertyType": {
          "type": "string",
          "enum": ["single-family", "condo", "townhouse", "multi-family", "land", "commercial"]
        },
        "bathrooms": {
          "type": "number",
          "minimum": 0,
          "multipleOf": 0.5
        },
        "yearBuilt": {
          "type": "integer",
          "minimum": 1800,
          "maximum": 2100
        }
      }
    },
    "price": {
      "type": "object",
      "properties": {
        "amount": {
          "type": "number",
          "minimum": 0,
          "exclusiveMinimum": true
        }
      }
    }
  }
}
```

**Key Features:**
- MLS ID pattern validation
- Property type classification
- Bathroom count in 0.5 increments
- Year built range validation (1800-2100)
- Features arrays (interior, exterior, amenities)
- Image gallery with primary image flag
- Open house scheduling

**Business Value:**
- Standardizes MLS data format
- Validates property details
- Ensures pricing data integrity
- Supports property search and filtering

---

### 12. Webhook Event

**Use Case:** Generic webhook events for third-party integrations and event-driven architectures

**Schema Highlights:**
```json
{
  "required": ["webhookId", "event", "timestamp", "source"],
  "properties": {
    "webhookId": {
      "type": "string",
      "format": "uuid"
    },
    "event": {
      "type": "string",
      "pattern": "^[a-z0-9._-]+$",
      "description": "Event type identifier (e.g., 'customer.created', 'order.shipped')"
    },
    "eventVersion": {
      "type": "string",
      "pattern": "^v[0-9]+$",
      "default": "v1"
    },
    "source": {
      "type": "object",
      "required": ["system", "environment"],
      "properties": {
        "environment": {
          "type": "string",
          "enum": ["development", "staging", "production"]
        }
      }
    },
    "actor": {
      "type": "object",
      "properties": {
        "type": {
          "type": "string",
          "enum": ["user", "system", "api-client", "service"]
        }
      }
    },
    "signature": {
      "type": "object",
      "properties": {
        "algorithm": {
          "type": "string",
          "enum": ["sha256", "sha512"]
        }
      }
    }
  }
}
```

**Key Features:**
- Event naming convention validation (lowercase, dots, hyphens)
- Event versioning support
- Source system tracking
- Actor identification (user, system, API client)
- Flexible event payload
- Previous state for update events
- Signature verification for security
- Retry tracking

**Business Value:**
- Standardizes webhook event structure
- Enables event versioning for API evolution
- Supports webhook signature verification
- Tracks delivery and retry attempts
- Documents event payload structure

---

## Common JSON Schema Patterns

### 1. Format Validators

JSON Schema provides built-in format validators for common data types:

```json
{
  "email": { "type": "string", "format": "email" },
  "uri": { "type": "string", "format": "uri" },
  "dateTime": { "type": "string", "format": "date-time" },
  "date": { "type": "string", "format": "date" },
  "time": { "type": "string", "format": "time" },
  "uuid": { "type": "string", "format": "uuid" },
  "ipv4": { "type": "string", "format": "ipv4" },
  "ipv6": { "type": "string", "format": "ipv6" },
  "hostname": { "type": "string", "format": "hostname" }
}
```

### 2. Pattern Matching

Use regular expressions for custom validation:

```json
{
  "customerId": {
    "type": "string",
    "pattern": "^CUST-[0-9]{4}-[0-9]{6}$"
  },
  "ssn": {
    "type": "string",
    "pattern": "^[0-9]{3}-[0-9]{2}-[0-9]{4}$"
  },
  "currency": {
    "type": "string",
    "pattern": "^[A-Z]{3}$"
  }
}
```

### 3. Numeric Constraints

Validate numeric ranges and increments:

```json
{
  "price": {
    "type": "number",
    "minimum": 0,
    "exclusiveMinimum": true
  },
  "port": {
    "type": "integer",
    "minimum": 1,
    "maximum": 65535
  },
  "bathrooms": {
    "type": "number",
    "minimum": 0,
    "multipleOf": 0.5
  },
  "duration": {
    "type": "integer",
    "minimum": 15,
    "multipleOf": 15
  }
}
```

### 4. Enum Constraints

Define fixed sets of allowed values:

```json
{
  "status": {
    "type": "string",
    "enum": ["pending", "active", "completed", "cancelled"]
  },
  "environment": {
    "type": "string",
    "enum": ["development", "staging", "production"]
  }
}
```

### 5. Reusable Definitions ($defs and $ref)

Define reusable schema components:

```json
{
  "$defs": {
    "address": {
      "type": "object",
      "required": ["street", "city", "state", "postalCode"],
      "properties": {
        "street": { "type": "string" },
        "city": { "type": "string" },
        "state": { "type": "string" },
        "postalCode": { "type": "string" }
      }
    }
  },
  "properties": {
    "shippingAddress": { "$ref": "#/$defs/address" },
    "billingAddress": { "$ref": "#/$defs/address" }
  }
}
```

### 6. Conditional Schemas (if/then/else)

Apply different validation rules based on conditions:

```json
{
  "if": {
    "properties": {
      "customerType": { "const": "business" }
    }
  },
  "then": {
    "required": ["companyName", "taxId"]
  },
  "else": {
    "required": ["firstName", "lastName"]
  }
}
```

### 7. Arrays with Constraints

Validate array items and lengths:

```json
{
  "items": {
    "type": "array",
    "minItems": 1,
    "maxItems": 100,
    "items": {
      "type": "object",
      "required": ["sku", "quantity"],
      "properties": {
        "sku": { "type": "string" },
        "quantity": { "type": "integer", "minimum": 1 }
      }
    }
  }
}
```

### 8. oneOf for Alternative Types

Allow one of several alternative schemas:

```json
{
  "queryParam": {
    "oneOf": [
      { "type": "string" },
      { "type": "number" },
      { "type": "boolean" }
    ]
  },
  "ipAddress": {
    "type": "string",
    "oneOf": [
      { "format": "ipv4" },
      { "format": "ipv6" }
    ]
  }
}
```

---

## Validating JSON Data

### Using Node.js (ajv)

```bash
npm install ajv ajv-formats
```

```javascript
const Ajv = require('ajv');
const addFormats = require('ajv-formats');

const ajv = new Ajv();
addFormats(ajv);

const schema = require('./01_customer_profile.schema.json');
const data = require('./01_customer_profile.json');

const validate = ajv.compile(schema);
const valid = validate(data);

if (!valid) {
  console.log('Validation errors:', validate.errors);
} else {
  console.log('Valid!');
}
```

### Using Python (jsonschema)

```bash
pip install jsonschema
```

```python
import json
from jsonschema import validate, ValidationError

with open('01_customer_profile.schema.json') as f:
    schema = json.load(f)

with open('01_customer_profile.json') as f:
    data = json.load(f)

try:
    validate(instance=data, schema=schema)
    print("Valid!")
except ValidationError as e:
    print(f"Validation error: {e.message}")
```

### Using Command Line (ajv-cli)

```bash
npm install -g ajv-cli ajv-formats

# Validate a single file
ajv validate -s 01_customer_profile.schema.json -d 01_customer_profile.json

# Validate multiple files
ajv validate -s 01_customer_profile.schema.json -d "*.json"
```

---

## UTL-X Integration Examples

### 1. Reading and Validating JSON

```utlx
%utlx 1.0
input json
output json
---
{
  // Parse JSON and validate against schema
  validatedCustomer: parseJson($input) |> validateJsonSchema(
    schema: readFile("01_customer_profile.schema.json")
  ),

  // Transform if valid
  result: if (validatedCustomer.valid) {
    processed: true,
    customer: validatedCustomer.data
  } else {
    processed: false,
    errors: validatedCustomer.errors
  }
}
```

### 2. Transforming Between Schemas

```utlx
%utlx 1.0
input json
output json
---
{
  // Transform from one schema to another
  // Input: 01_customer_profile.json
  // Output: 06_employee_record.json

  employee: {
    employeeId: "EMP-" + substring($input.customerId, 5),
    personalInfo: {
      firstName: $input.firstName,
      lastName: $input.lastName,
      dateOfBirth: $input.dateOfBirth,
      email: $input.email,
      phone: $input.phone,
      address: $input.address
    },
    employment: {
      status: "active",
      hireDate: now(),
      department: "Sales"
    },
    createdAt: now(),
    updatedAt: now()
  }
}
```

### 3. Generating Schema Documentation

```utlx
%utlx 1.0
input json  // JSON Schema file
output markdown
---
{
  // Generate Markdown documentation from JSON Schema

  title: "# " + $input.title,
  description: $input.description,

  properties: $input.properties |> mapEntries((key, value) => {
    propertyName: "### " + key,
    type: "**Type:** " + value.type,
    required: if (contains($input.required, key)) "**Required:** Yes" else "**Required:** No",
    description: if (hasKey(value, "description")) value.description else "",
    format: if (hasKey(value, "format")) "**Format:** " + value.format else "",
    pattern: if (hasKey(value, "pattern")) "**Pattern:** `" + value.pattern + "`" else "",
    enum: if (hasKey(value, "enum")) "**Allowed values:** " + join(value.enum, ", ") else ""
  })
}
```

### 4. API Response Validation

```utlx
%utlx 1.0
input json
output json
---
{
  // Validate API response against schema before processing

  let response = parseJson($input),
  let schema = parseJson(readFile("04_api_request_response.schema.json")),
  let validation = validateJsonSchema(response, schema),

  result: if (validation.valid) {
    // Process valid response
    {
      success: true,
      data: response.response.data,
      statusCode: response.response.statusCode
    }
  } else {
    // Return validation errors
    {
      success: false,
      errors: validation.errors,
      message: "Response validation failed"
    }
  }
}
```

### 5. Configuration File Processing

```utlx
%utlx 1.0
input json  // Configuration file
output json
---
{
  // Validate and transform configuration

  let config = parseJson($input),
  let schema = parseJson(readFile("05_configuration_file.schema.json")),
  let validation = validateJsonSchema(config, schema),

  processedConfig: if (validation.valid) {
    {
      environment: config.environment,
      database: {
        connectionString: buildConnectionString(config.database),
        poolSize: config.database.poolSize,
        ssl: config.database.ssl
      },
      server: {
        url: "http://" + config.server.host + ":" + toString(config.server.port),
        cors: config.server.cors
      },
      enabledFeatures: config.featureFlags |> filter((key, value) => value == true) |> keys()
    }
  } else {
    throw("Configuration validation failed: " + join(validation.errors, ", "))
  }
}

function buildConnectionString(db: Object): String {
  let protocol = match db.type {
    "postgresql" => "postgresql",
    "mysql" => "mysql",
    "mongodb" => "mongodb",
    _ => db.type
  },
  protocol + "://" + db.host + ":" + toString(db.port) + "/" + db.name
}
```

---

## Best Practices

### 1. Schema Design

**Use Clear Identifiers:**
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://yourdomain.com/schemas/resource-name.schema.json",
  "title": "Descriptive Title",
  "description": "Clear description of what this schema represents"
}
```

**Define Required Fields:**
```json
{
  "type": "object",
  "required": ["id", "name", "email"],
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string" },
    "email": { "type": "string", "format": "email" }
  }
}
```

**Use Appropriate Constraints:**
```json
{
  "quantity": {
    "type": "integer",
    "minimum": 1,
    "maximum": 1000,
    "description": "Order quantity (1-1000)"
  },
  "price": {
    "type": "number",
    "minimum": 0,
    "exclusiveMinimum": true,
    "description": "Price must be greater than 0"
  }
}
```

### 2. Schema Reusability

**Extract Common Definitions:**
```json
{
  "$defs": {
    "address": { /* address schema */ },
    "phoneNumber": {
      "type": "string",
      "pattern": "^\\+[1-9]\\d{1,14}$"
    },
    "email": {
      "type": "string",
      "format": "email"
    }
  },
  "properties": {
    "contactEmail": { "$ref": "#/$defs/email" },
    "supportEmail": { "$ref": "#/$defs/email" }
  }
}
```

### 3. Schema Evolution

**Add Default Values for New Fields:**
```json
{
  "newField": {
    "type": "string",
    "default": "defaultValue",
    "description": "Added in v2.0"
  }
}
```

**Use Versioning:**
```json
{
  "$id": "https://yourdomain.com/schemas/customer-profile-v2.schema.json",
  "version": "2.0.0",
  "title": "Customer Profile v2"
}
```

**Make New Fields Optional:**
```json
{
  "required": ["id", "name"],  // Only existing required fields
  "properties": {
    "id": { "type": "string" },
    "name": { "type": "string" },
    "newOptionalField": { "type": "string" }  // New field is optional
  }
}
```

### 4. Validation Messages

**Add Descriptions for Better Error Messages:**
```json
{
  "customerId": {
    "type": "string",
    "pattern": "^CUST-[0-9]{4}-[0-9]{6}$",
    "description": "Customer ID in format CUST-YYYY-NNNNNN (e.g., CUST-2024-001234)"
  }
}
```

### 5. Performance

**Avoid Deep Nesting:**
```json
// Good - Flat structure with references
{
  "$defs": {
    "address": { /* ... */ }
  },
  "properties": {
    "shippingAddress": { "$ref": "#/$defs/address" }
  }
}

// Avoid - Deep nesting
{
  "properties": {
    "customer": {
      "properties": {
        "address": {
          "properties": {
            "coordinates": {
              "properties": {
                // Too deep!
              }
            }
          }
        }
      }
    }
  }
}
```

**Use `additionalProperties` Wisely:**
```json
{
  // Strict validation (recommended for APIs)
  "additionalProperties": false,

  // Flexible validation (use for configuration)
  "additionalProperties": true,

  // Typed additional properties
  "additionalProperties": { "type": "string" }
}
```

---

## Integration Scenarios

### 1. API Validation

**Request Validation:**
```javascript
const Ajv = require('ajv');
const addFormats = require('ajv-formats');

const ajv = new Ajv();
addFormats(ajv);

const schema = require('./schemas/api-request.schema.json');
const validate = ajv.compile(schema);

// Express middleware
function validateRequest(req, res, next) {
  const valid = validate(req.body);
  if (!valid) {
    return res.status(400).json({
      error: 'Validation failed',
      details: validate.errors
    });
  }
  next();
}

app.post('/api/customers', validateRequest, createCustomer);
```

### 2. Configuration Management

**Validate Configuration on Startup:**
```javascript
const fs = require('fs');
const Ajv = require('ajv');

function loadConfig(configPath, schemaPath) {
  const ajv = new Ajv();
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'));
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

  const validate = ajv.compile(schema);
  if (!validate(config)) {
    throw new Error(`Invalid configuration: ${JSON.stringify(validate.errors)}`);
  }

  return config;
}

const config = loadConfig(
  './config/production.json',
  './schemas/05_configuration_file.schema.json'
);
```

### 3. Event Schema Registry

**Event-Driven Architecture with Schema Validation:**
```javascript
class EventBus {
  constructor() {
    this.schemas = new Map();
    this.ajv = new Ajv();
  }

  registerEventSchema(eventType, schema) {
    this.schemas.set(eventType, this.ajv.compile(schema));
  }

  publish(eventType, eventData) {
    const validate = this.schemas.get(eventType);
    if (!validate) {
      throw new Error(`No schema registered for event type: ${eventType}`);
    }

    if (!validate(eventData)) {
      throw new Error(`Event validation failed: ${JSON.stringify(validate.errors)}`);
    }

    // Publish validated event
    this.emit(eventType, eventData);
  }
}

// Usage
const eventBus = new EventBus();
eventBus.registerEventSchema('customer.created', customerSchema);
eventBus.publish('customer.created', customerData);
```

### 4. Code Generation

**Generate TypeScript Types from JSON Schema:**
```bash
npm install -g json-schema-to-typescript

json-schema-to-typescript 01_customer_profile.schema.json > customer-profile.d.ts
```

**Generated TypeScript:**
```typescript
export interface CustomerProfile {
  customerId: string;
  email: string;
  customerType: "individual" | "business";
  loyaltyTier?: "bronze" | "silver" | "gold" | "platinum";
  preferences?: {
    newsletter?: boolean;
    smsNotifications?: boolean;
    language?: string;
  };
  createdAt: string;
  updatedAt: string;
}
```

### 5. Documentation Generation

**Generate Markdown Documentation:**
```bash
npm install -g @adobe/jsonschema2md

jsonschema2md -d schemas/ -o docs/
```

---

## Resources

### JSON Schema Specification
- [JSON Schema Official Website](https://json-schema.org/)
- [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-core.html)
- [Understanding JSON Schema](https://json-schema.org/understanding-json-schema/)

### Validators
- [AJV (JavaScript)](https://ajv.js.org/) - The fastest JSON Schema validator
- [jsonschema (Python)](https://python-jsonschema.readthedocs.io/)
- [JSON Schema Validator (Online)](https://www.jsonschemavalidator.net/)

### Tools
- [json-schema-to-typescript](https://github.com/bcherny/json-schema-to-typescript) - Generate TypeScript types
- [quicktype](https://quicktype.io/) - Generate code from JSON Schema
- [jsonschema2md](https://github.com/adobe/jsonschema2md) - Generate Markdown docs

### Learning Resources
- [JSON Schema Tutorial](https://json-schema.org/learn/getting-started-step-by-step)
- [JSON Schema Best Practices](https://json-schema.org/understanding-json-schema/about.html)
- [Schema Store](https://www.schemastore.org/) - Collection of JSON schemas

### UTL-X Resources
- [UTL-X Documentation](../../docs/)
- [UTL-X Standard Library](../../stdlib/)
- [UTL-X Examples](../)

---

## Contributing

To add new JSON Schema examples:

1. Create a schema file: `NN_example_name.schema.json`
2. Create sample data: `NN_example_name.json`
3. Validate the sample data against the schema
4. Update this README with the new example
5. Add UTL-X integration examples if applicable

**Schema File Naming Convention:**
- Use descriptive names with underscores
- Prefix with number (01-99) for ordering
- Use `.schema.json` extension

**Sample Data Naming Convention:**
- Match the schema file name
- Use `.json` extension
- Include realistic, production-like data

---

## License

These examples are provided as part of the UTL-X project and follow the project's licensing terms (AGPL-3.0 / Commercial).
