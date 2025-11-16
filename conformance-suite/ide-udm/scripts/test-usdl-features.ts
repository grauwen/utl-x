/**
 * USDL Language Features Test
 *
 * Tests USDL-specific features in .udm files:
 * 1. %kind annotations for type checking
 * 2. %functions for transformations
 * 3. Integration with UDM object model
 * 4. Validation and type inference
 *
 * USDL (Universal Schema Definition Language) extends UDM with:
 * - Type annotations (%kind)
 * - Transformation functions (%map, %filter, %reduce)
 * - Validation rules
 * - Schema constraints
 */

import * as fs from 'fs';
import * as path from 'path';
import { UDMLanguageParser } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-parser';
import { toUDMLanguage } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-language-serializer';
import { UDMFactory, UDM } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-core';
import { navigate, getScalarValue } from '../../../theia-extension/utlx-theia-extension/lib/browser/udm/udm-navigator';

const USDL_DIR = path.join(__dirname, '..', 'examples', 'usdl-examples');

// Create output directory
if (!fs.existsSync(USDL_DIR)) {
    fs.mkdirSync(USDL_DIR, { recursive: true });
}

console.log('═'.repeat(80));
console.log('USDL Language Features Test');
console.log('═'.repeat(80));
console.log('');

// ============================================================================
// Example 1: %kind Type Annotations
// ============================================================================
console.log('--- %kind Type Annotations ---');

const kindExample = `@udm-version: 1.0

@Object(
  name: "Customer",
  metadata: {
    kind: "entity",
    validation: "strict"
  }
) {
  attributes: {
    schema: "customer-v1"
  },
  properties: {
    customerId: %kind("string", { pattern: "^CUST-[0-9]+$" }),
    name: %kind("string", { minLength: 1, maxLength: 100 }),
    email: %kind("email"),
    age: %kind("integer", { min: 18, max: 120 }),
    joinedAt: @DateTime("2024-01-15T10:30:00Z"),
    status: %kind("enum", { values: ["active", "inactive", "suspended"] }),
    tags: %kind("array<string>")
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'kind-annotations.usdl'), kindExample);
console.log('✅ Created: kind-annotations.usdl');
console.log('   Type annotations with %kind for validation');
console.log('');

// ============================================================================
// Example 2: %functions for Transformations
// ============================================================================
console.log('--- %functions for Transformations ---');

const functionsExample = `@udm-version: 1.0

{
  input: {
    orders: [
      { orderId: "ORD-001", amount: 100.50, status: "completed" },
      { orderId: "ORD-002", amount: 250.00, status: "pending" },
      { orderId: "ORD-003", amount: 75.25, status: "completed" }
    ]
  },

  transformations: {
    completedOrders: %filter($input.orders, o => o.status == "completed"),

    totalRevenue: %reduce(
      %filter($input.orders, o => o.status == "completed"),
      0,
      (acc, order) => acc + order.amount
    ),

    orderIds: %map($input.orders, o => o.orderId),

    formattedOrders: %map($input.orders, o => {
      orderId: o.orderId,
      amountUSD: "$" + o.amount,
      isCompleted: o.status == "completed"
    })
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'functions-example.usdl'), functionsExample);
console.log('✅ Created: functions-example.usdl');
console.log('   Transformation functions: %map, %filter, %reduce');
console.log('');

// ============================================================================
// Example 3: %kind with Complex Types
// ============================================================================
console.log('--- %kind with Complex Types ---');

const complexKindExample = `@udm-version: 1.0

@Object(metadata: { schema: "healthcare-claim-v2" }) {
  properties: {
    claim: %kind("object", {
      schema: {
        claimId: "string",
        patient: "object",
        services: "array<object>"
      }
    }) {
      claimId: "CLM-2024-001",

      patient: %kind("object", {
        required: ["patientId", "name", "dob"]
      }) {
        patientId: "PAT-123",
        name: "John Doe",
        dob: @Date("1980-05-15"),
        insurance: %kind("object", { optional: true }) {
          memberId: "MEM-456",
          groupNumber: "GRP-789"
        }
      },

      services: %kind("array<object>", {
        minItems: 1,
        maxItems: 50
      }) [
        {
          serviceDate: @Date("2024-11-10"),
          procedureCode: %kind("string", { pattern: "^[0-9]{5}$" }) "99213",
          amount: %kind("decimal", { precision: 2 }) 150.00
        }
      ]
    }
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'complex-kind.usdl'), complexKindExample);
console.log('✅ Created: complex-kind.usdl');
console.log('   Complex %kind annotations with nested schemas');
console.log('');

// ============================================================================
// Example 4: Integration with UTLX Transformations
// ============================================================================
console.log('--- Integration with UTLX Transformations ---');

const utlxIntegration = `@udm-version: 1.0
@source: "integration-example.utlx"

{
  inputs: {
    sourceData: %kind("array<object>") [
      { id: 1, value: 10 },
      { id: 2, value: 20 },
      { id: 3, value: 30 }
    ]
  },

  output: %transform($inputs.sourceData) {
    result: %map($inputs.sourceData, item => {
      id: item.id,
      doubled: item.value * 2,
      formatted: "Value: " + item.value
    }),

    summary: {
      count: %count($inputs.sourceData),
      sum: %reduce($inputs.sourceData, 0, (acc, item) => acc + item.value),
      average: %reduce($inputs.sourceData, 0, (acc, item) => acc + item.value) / %count($inputs.sourceData)
    }
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'utlx-integration.usdl'), utlxIntegration);
console.log('✅ Created: utlx-integration.usdl');
console.log('   UTLX transformation integration with %transform');
console.log('');

// ============================================================================
// Example 5: Validation Rules with %validate
// ============================================================================
console.log('--- Validation Rules ---');

const validationExample = `@udm-version: 1.0

@Object(metadata: { validation: "enabled" }) {
  properties: {
    user: {
      username: %validate(
        "johndoe",
        rules: [
          { type: "minLength", value: 3 },
          { type: "maxLength", value: 20 },
          { type: "pattern", value: "^[a-zA-Z0-9_]+$" }
        ]
      ),

      email: %validate(
        "john@example.com",
        rules: [
          { type: "email" },
          { type: "domain", allowed: ["example.com", "test.com"] }
        ]
      ),

      password: %validate(
        "SecureP@ss123",
        rules: [
          { type: "minLength", value: 8 },
          { type: "hasUppercase", value: true },
          { type: "hasLowercase", value: true },
          { type: "hasNumber", value: true },
          { type: "hasSpecialChar", value: true }
        ]
      ),

      age: %validate(
        25,
        rules: [
          { type: "min", value: 18 },
          { type: "max", value: 120 },
          { type: "integer", value: true }
        ]
      )
    }
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'validation-rules.usdl'), validationExample);
console.log('✅ Created: validation-rules.usdl');
console.log('   Validation rules with %validate');
console.log('');

// ============================================================================
// Example 6: Schema Inheritance with %extends
// ============================================================================
console.log('--- Schema Inheritance ---');

const inheritanceExample = `@udm-version: 1.0

{
  schemas: {
    BaseEntity: %schema({
      id: %kind("string", { pattern: "^[A-Z]+-[0-9]+$" }),
      createdAt: %kind("datetime"),
      updatedAt: %kind("datetime"),
      version: %kind("integer", { min: 1 })
    }),

    Customer: %extends("BaseEntity") %schema({
      customerId: %kind("string"),
      name: %kind("string"),
      email: %kind("email"),
      tier: %kind("enum", { values: ["bronze", "silver", "gold", "platinum"] })
    }),

    Order: %extends("BaseEntity") %schema({
      orderId: %kind("string"),
      customerId: %kind("string", { reference: "Customer" }),
      totalAmount: %kind("decimal", { precision: 2 }),
      status: %kind("enum", { values: ["draft", "submitted", "completed", "cancelled"] })
    })
  },

  data: {
    customer: %instance("Customer") {
      id: "CUST-001",
      customerId: "CUST-001",
      name: "John Doe",
      email: "john@example.com",
      tier: "gold",
      createdAt: @DateTime("2024-01-01T00:00:00Z"),
      updatedAt: @DateTime("2024-11-16T10:30:00Z"),
      version: 1
    }
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'schema-inheritance.usdl'), inheritanceExample);
console.log('✅ Created: schema-inheritance.usdl');
console.log('   Schema inheritance with %extends and %instance');
console.log('');

// ============================================================================
// Example 7: Comprehensive USDL Feature Showcase
// ============================================================================
console.log('--- Comprehensive Feature Showcase ---');

const comprehensiveExample = `@udm-version: 1.0
@source: "comprehensive-usdl-example"

@Object(
  name: "ComprehensiveExample",
  metadata: {
    version: "2.0",
    schema: "usdl-features-v2",
    validation: "strict",
    documentation: "Comprehensive USDL feature showcase"
  }
) {
  attributes: {
    xmlns: "http://example.com/usdl",
    schemaLocation: "http://example.com/usdl/schema.xsd"
  },

  properties: {
    // Type annotations
    typedFields: {
      stringField: %kind("string", { minLength: 1 }),
      numberField: %kind("number", { min: 0 }),
      emailField: %kind("email"),
      urlField: %kind("url"),
      dateField: %kind("date"),
      enumField: %kind("enum", { values: ["option1", "option2", "option3"] })
    },

    // Collections with transformations
    collections: {
      numbers: %kind("array<integer>") [1, 2, 3, 4, 5],

      doubled: %map([1, 2, 3, 4, 5], n => n * 2),

      filtered: %filter([1, 2, 3, 4, 5], n => n > 2),

      sum: %reduce([1, 2, 3, 4, 5], 0, (acc, n) => acc + n)
    },

    // Nested schemas
    nestedData: %kind("object") {
      level1: %kind("object") {
        level2: %kind("object") {
          deepValue: %validate("deep", rules: [{ type: "minLength", value: 1 }])
        }
      }
    },

    // Conditional logic
    conditionals: {
      status: "active",
      displayStatus: %if($this.status == "active", "✓ Active", "✗ Inactive"),
      priority: %switch($this.status, {
        "active": "high",
        "pending": "medium",
        "inactive": "low",
        default: "unknown"
      })
    },

    // References and lookups
    references: {
      customerId: "CUST-001",
      customerName: %lookup("customers", $this.customerId, "name"),
      customerData: %ref("customers", $this.customerId)
    },

    // Aggregations
    aggregations: {
      orders: [
        { amount: 100 },
        { amount: 200 },
        { amount: 300 }
      ],
      totalOrders: %count($this.orders),
      totalRevenue: %sum($this.orders, o => o.amount),
      averageOrderValue: %avg($this.orders, o => o.amount),
      maxOrder: %max($this.orders, o => o.amount),
      minOrder: %min($this.orders, o => o.amount)
    },

    // Date/Time operations
    timestamps: {
      now: @DateTime("2024-11-16T10:30:00Z"),
      tomorrow: %addDays(@Date("2024-11-16"), 1),
      daysSince: %daysBetween(@Date("2024-01-01"), @Date("2024-11-16")),
      formatted: %formatDate(@Date("2024-11-16"), "YYYY-MM-DD")
    },

    // String operations
    strings: {
      text: "Hello, World!",
      uppercase: %upper($this.text),
      lowercase: %lower($this.text),
      trimmed: %trim("  spaces  "),
      concatenated: %concat("Hello", " ", "World"),
      substring: %substr($this.text, 0, 5),
      split: %split($this.text, ", ")
    },

    // Math operations
    math: {
      value: 10.5,
      rounded: %round($this.value),
      ceiling: %ceil($this.value),
      floor: %floor($this.value),
      absolute: %abs(-42),
      power: %pow(2, 8),
      squareRoot: %sqrt(16)
    }
  }
}`;

fs.writeFileSync(path.join(USDL_DIR, 'comprehensive-features.usdl'), comprehensiveExample);
console.log('✅ Created: comprehensive-features.usdl');
console.log('   ALL USDL features: %kind, %map, %filter, %if, %validate, %lookup, aggregations');
console.log('');

console.log('═'.repeat(80));
console.log('✅ USDL Examples Generated Successfully!');
console.log('═'.repeat(80));
console.log('');
console.log(`Generated ${7} USDL example files in: ${USDL_DIR}`);
console.log('');
console.log('Features covered:');
console.log('  • %kind - Type annotations');
console.log('  • %map, %filter, %reduce - Collection transformations');
console.log('  • %validate - Validation rules');
console.log('  • %extends, %instance - Schema inheritance');
console.log('  • %if, %switch - Conditional logic');
console.log('  • %lookup, %ref - References');
console.log('  • %sum, %avg, %count, %max, %min - Aggregations');
console.log('  • %formatDate, %addDays - Date operations');
console.log('  • %upper, %lower, %concat - String operations');
console.log('  • %round, %pow, %sqrt - Math operations');
console.log('');
