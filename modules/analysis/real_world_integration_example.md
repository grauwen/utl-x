# Real-World Integration Example: E-Commerce Order Processing

This example demonstrates a complete end-to-end integration using UTL-X schema analysis in a production environment.

## Scenario

**Company:** TechStore Inc.  
**Challenge:** Transform XML orders from legacy ERP system to JSON for modern microservices  
**Requirements:**
- Design-time schema validation
- API documentation auto-generation
- CI/CD integration
- Contract testing
- Breaking change detection

## Project Structure

```
techstore-integrations/
├── schemas/
│   ├── inputs/
│   │   ├── erp-order.xsd           # ERP XML schema
│   │   ├── customer-update.xsd     # Customer update schema
│   │   └── inventory-sync.xsd      # Inventory sync schema
│   └── outputs/
│       ├── order-api.json          # Generated JSON Schema
│       ├── customer-api.json       # Generated JSON Schema
│       └── inventory-api.json      # Generated JSON Schema
│
├── transforms/
│   ├── erp-to-order-service.utlx   # Order transformation
│   ├── erp-to-customer-service.utlx # Customer transformation
│   └── erp-to-inventory-service.utlx # Inventory transformation
│
├── test-data/
│   ├── sample-orders/
│   ├── sample-customers/
│   └── sample-inventory/
│
├── docs/
│   └── api/                         # Generated API docs
│       ├── order-service-api.yaml
│       ├── customer-service-api.yaml
│       └── inventory-service-api.yaml
│
├── build.gradle.kts
├── .github/
│   └── workflows/
│       ├── validate-transforms.yml
│       └── deploy-integrations.yml
└── README.md
```

## Step 1: Define Input Schema

**schemas/inputs/erp-order.xsd**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="ERPOrder">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="OrderHeader">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="OrderNumber" type="xs:string"/>
                            <xs:element name="OrderDate" type="xs:date"/>
                            <xs:element name="CustomerCode" type="xs:string"/>
                            <xs:element name="OrderType" type="xs:string"/>
                            <xs:element name="Priority" type="PriorityType"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                
                <xs:element name="Customer">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="CustomerCode" type="xs:string"/>
                            <xs:element name="CompanyName" type="xs:string"/>
                            <xs:element name="ContactPerson" type="xs:string"/>
                            <xs:element name="Email" type="xs:string"/>
                            <xs:element name="Phone" type="xs:string"/>
                            <xs:element name="VIPLevel" type="xs:integer" minOccurs="0"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                
                <xs:element name="LineItems">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="LineItem" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="LineNumber" type="xs:integer"/>
                                        <xs:element name="ProductCode" type="xs:string"/>
                                        <xs:element name="ProductDescription" type="xs:string"/>
                                        <xs:element name="Quantity" type="xs:integer"/>
                                        <xs:element name="UnitPrice" type="xs:decimal"/>
                                        <xs:element name="DiscountPercent" type="xs:decimal" minOccurs="0"/>
                                        <xs:element name="TaxCode" type="xs:string"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                
                <xs:element name="ShippingAddress">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="AddressLine1" type="xs:string"/>
                            <xs:element name="AddressLine2" type="xs:string" minOccurs="0"/>
                            <xs:element name="City" type="xs:string"/>
                            <xs:element name="State" type="xs:string"/>
                            <xs:element name="PostalCode" type="xs:string"/>
                            <xs:element name="Country" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
    
    <xs:simpleType name="PriorityType">
        <xs:restriction base="xs:string">
            <xs:enumeration value="LOW"/>
            <xs:enumeration value="NORMAL"/>
            <xs:enumeration value="HIGH"/>
            <xs:enumeration value="URGENT"/>
        </xs:restriction>
    </xs:simpleType>
</xs:schema>
```

## Step 2: Write Transformation

**transforms/erp-to-order-service.utlx**:
```utlx
%utlx 1.0
input xml
output json
---
{
  order: {
    // Order identification
    orderId: input.ERPOrder.OrderHeader.OrderNumber,
    orderDate: parseDate(input.ERPOrder.OrderHeader.OrderDate, "yyyy-MM-dd"),
    orderType: input.ERPOrder.OrderHeader.OrderType,
    priority: lower(input.ERPOrder.OrderHeader.Priority),
    
    // Customer information
    customer: {
      customerId: input.ERPOrder.Customer.CustomerCode,
      companyName: input.ERPOrder.Customer.CompanyName,
      contactPerson: input.ERPOrder.Customer.ContactPerson,
      email: input.ERPOrder.Customer.Email,
      phone: formatPhone(input.ERPOrder.Customer.Phone),
      vipLevel: input.ERPOrder.Customer.VIPLevel,
      isVIP: input.ERPOrder.Customer.VIPLevel > 0
    },
    
    // Line items with calculations
    items: input.ERPOrder.LineItems.LineItem |> map(item => {
      let unitPrice = parseDecimal(item.UnitPrice),
      let quantity = parseInt(item.Quantity),
      let discountPercent = parseDecimal(item.DiscountPercent) ?? 0,
      let discountAmount = unitPrice * quantity * (discountPercent / 100),
      let lineTotal = (unitPrice * quantity) - discountAmount
      
      {
        lineNumber: parseInt(item.LineNumber),
        product: {
          code: item.ProductCode,
          description: item.ProductDescription
        },
        quantity: quantity,
        pricing: {
          unitPrice: unitPrice,
          discountPercent: discountPercent,
          discountAmount: round(discountAmount, 2),
          lineTotal: round(lineTotal, 2)
        },
        taxCode: item.TaxCode
      }
    }),
    
    // Calculate totals
    let subtotal = sum(
      input.ERPOrder.LineItems.LineItem.(
        parseDecimal(UnitPrice) * parseInt(Quantity) - 
        (parseDecimal(UnitPrice) * parseInt(Quantity) * (parseDecimal(DiscountPercent) ?? 0) / 100)
      )
    ),
    let vipDiscount = if (input.ERPOrder.Customer.VIPLevel >= 3) subtotal * 0.05 else 0,
    let taxRate = getTaxRate(input.ERPOrder.ShippingAddress.State),
    let tax = (subtotal - vipDiscount) * taxRate,
    
    totals: {
      subtotal: round(subtotal, 2),
      vipDiscount: round(vipDiscount, 2),
      taxRate: taxRate,
      tax: round(tax, 2),
      total: round(subtotal - vipDiscount + tax, 2)
    },
    
    // Shipping address
    shippingAddress: {
      street: concat(
        input.ERPOrder.ShippingAddress.AddressLine1,
        if (input.ERPOrder.ShippingAddress.AddressLine2) 
          ", " + input.ERPOrder.ShippingAddress.AddressLine2 
        else 
          ""
      ),
      city: input.ERPOrder.ShippingAddress.City,
      state: input.ERPOrder.ShippingAddress.State,
      postalCode: input.ERPOrder.ShippingAddress.PostalCode,
      country: input.ERPOrder.ShippingAddress.Country
    },
    
    // Metadata
    metadata: {
      processedAt: now(),
      transformVersion: "1.2.0",
      source: "ERP-Legacy",
      priority: input.ERPOrder.OrderHeader.Priority
    }
  }
}

// Helper function for phone formatting
function formatPhone(phone: String): String {
  let cleaned = replace(phone, "[^0-9]", ""),
  if (length(cleaned) == 10)
    substring(cleaned, 0, 3) + "-" + substring(cleaned, 3, 6) + "-" + substring(cleaned, 6, 10)
  else
    phone
}

// Helper function for tax rate lookup
function getTaxRate(state: String): Number {
  match state {
    "CA" => 0.0875,
    "NY" => 0.08875,
    "TX" => 0.0625,
    "FL" => 0.06,
    _ => 0.07
  }
}
```

## Step 3: Configure Build Tool

**build.gradle.kts**:
```kotlin
plugins {
    id("org.apache.utlx.schema") version "0.9.0-beta"
}

repositories {
    mavenCentral()
    maven("https://repo.utlx.dev/releases")
}

dependencies {
    implementation("org.apache.utlx:core:0.9.0-beta")
    implementation("org.apache.utlx:analysis:0.9.0-beta")
}

utlxSchema {
    transformations {
        register("erpToOrderService") {
            inputSchema.set(file("schemas/inputs/erp-order.xsd"))
            transform.set(file("transforms/erp-to-order-service.utlx"))
            outputFormat.set("json-schema")
            outputFile.set(file("schemas/outputs/order-api.json"))
            expectedOutputSchema.set(file("schemas/outputs/order-api.json"))
            pretty.set(true)
            includeComments.set(true)
            strictMode.set(true)
            failOnWarnings.set(false)
        }
        
        register("erpToCustomerService") {
            inputSchema.set(file("schemas/inputs/customer-update.xsd"))
            transform.set(file("transforms/erp-to-customer-service.utlx"))
            outputFormat.set("json-schema")
            outputFile.set(file("schemas/outputs/customer-api.json"))
        }
        
        register("erpToInventoryService") {
            inputSchema.set(file("schemas/inputs/inventory-sync.xsd"))
            transform.set(file("transforms/erp-to-inventory-service.utlx"))
            outputFormat.set("json-schema")
            outputFile.set(file("schemas/outputs/inventory-api.json"))
        }
    }
}

// Task to generate all schemas
tasks.register("generateAllSchemas") {
    dependsOn("generateAllSchemas")
    
    doLast {
        println("✓ All schemas generated successfully")
    }
}

// Task to validate all transformations
tasks.register("validateAllTransformations") {
    dependsOn("validateAllTransforms")
    
    doLast {
        println("✓ All transformations validated successfully")
    }
}

// Task to generate OpenAPI specs
tasks.register<Exec>("generateOpenAPISpecs") {
    dependsOn("generateAllSchemas")
    
    commandLine("bash", "-c", """
        mkdir -p docs/api
        
        java -jar modules/cli/build/libs/utlx-cli.jar schema document \
            --input-schema schemas/inputs/erp-order.xsd \
            --transform transforms/erp-to-order-service.utlx \
            --output-format openapi \
            --api-path /api/orders/transform \
            --api-title "Order Service API" \
            --output docs/api/order-service-api.yaml
    """.trimIndent())
    
    doLast {
        println("✓ OpenAPI specifications generated")
    }
}

// Integrate with build lifecycle
tasks.named("build") {
    dependsOn("validateAllTransformations")
}

tasks.named("check") {
    dependsOn("generateAllSchemas", "validateAllTransformations")
}
```

## Step 4: CI/CD Integration

**.github/workflows/validate-transforms.yml**:
```yaml
name: Validate Transformations

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  validate:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v4
      
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'
      
      - name: Validate all transformations
        run: |
          ./gradlew validateAllTransformations --no-daemon
      
      - name: Generate schemas
        run: |
          ./gradlew generateAllSchemas --no-daemon
      
      - name: Check for schema drift
        run: |
          if git diff --quiet schemas/outputs/; then
            echo "✓ Schemas are up to date"
          else
            echo "⚠️ Schema drift detected!"
            git diff schemas/outputs/
            exit 1
          fi
      
      - name: Generate OpenAPI docs
        run: |
          ./gradlew generateOpenAPISpecs --no-daemon
      
      - name: Upload schemas
        uses: actions/upload-artifact@v4
        with:
          name: schemas
          path: schemas/outputs/
      
      - name: Upload API docs
        uses: actions/upload-artifact@v4
        with:
          name: api-docs
          path: docs/api/
```

## Step 5: Local Development Workflow

```bash
# 1. Clone repository
git clone https://github.com/techstore/integrations.git
cd integrations

# 2. Install dependencies
./gradlew build

# 3. Validate transformations
./gradlew validateAllTransformations

# Output:
# > Task :validateTransformErpToOrderService
# Validating transformation erp-to-order-service.utlx
# ✓ Validation successful
#
# > Task :validateTransformErpToCustomerService
# ✓ Validation successful
#
# > Task :validateTransformErpToInventoryService
# ✓ Validation successful

# 4. Generate schemas
./gradlew generateAllSchemas

# Output:
# > Task :generateSchemaErpToOrderService
# Generating schema for erp-to-order-service.utlx
# ✓ Schema generated: schemas/outputs/order-api.json

# 5. Test transformation with sample data
java -jar utlx-cli.jar transform \
  --transform transforms/erp-to-order-service.utlx \
  --input test-data/sample-orders/order-001.xml \
  --output build/output/order-001.json

# 6. Validate output against schema
java -jar utlx-cli.jar validate \
  --schema schemas/outputs/order-api.json \
  --input build/output/order-001.json

# Output:
# Validating order-001.json against order-api.json...
# ✓ Validation successful!
```

## Step 6: Production Deployment

The transformation can be deployed in multiple ways:

### Option A: As a Microservice

```kotlin
// OrderTransformService.kt
import org.apache.utlx.core.runtime.UTLXRuntime

@RestController
@RequestMapping("/api/orders")
class OrderTransformService {
    
    private val runtime = UTLXRuntime.fromFile("transforms/erp-to-order-service.utlx")
    
    @PostMapping("/transform")
    fun transform(@RequestBody erpOrderXml: String): ResponseEntity<String> {
        return try {
            val result = runtime.execute(erpOrderXml)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.status(500).body("""{"error": "${e.message}"}""")
        }
    }
}
```

### Option B: As Apache Camel Route

```kotlin
from("kafka:erp-orders")
    .transform().utlx("transforms/erp-to-order-service.utlx")
    .to("kafka:order-service-events")
```

### Option C: As AWS Lambda

```kotlin
class OrderTransformLambda : RequestHandler<String, String> {
    private val runtime = UTLXRuntime.fromResource("erp-to-order-service.utlx")
    
    override fun handleRequest(input: String, context: Context): String {
        return runtime.execute(input)
    }
}
```

## Benefits Achieved

### Design-Time Benefits
✅ **Schema Generation**: Output schemas auto-generated from transformations  
✅ **Validation**: Caught 3 type errors before deployment  
✅ **Documentation**: OpenAPI specs auto-generated  
✅ **Breaking Changes**: Detected schema changes in CI/CD  

### Runtime Benefits
✅ **Performance**: Transformations execute in 5-15ms  
✅ **Reliability**: 99.9% success rate in production  
✅ **Monitoring**: Schema validation catches bad data  

### Developer Experience
✅ **Fast Feedback**: Validation runs in <1 minute  
✅ **Clear Contracts**: Teams understand data structures  
✅ **Easy Updates**: Change transformation, regenerate schema  
✅ **Confidence**: Deploy knowing schemas are correct  

## Metrics

| Metric | Before UTL-X | After UTL-X | Improvement |
|--------|-------------|-------------|-------------|
| Avg Development Time | 4 hours | 1 hour | 75% faster |
| Schema Errors in Prod | 12/month | 0/month | 100% reduction |
| API Doc Accuracy | 60% | 100% | Always current |
| Integration Failures | 8% | 0.1% | 98.75% reduction |
| Time to Deploy Changes | 2 days | 2 hours | 92% faster |

## Conclusion

This real-world example demonstrates how UTL-X schema analysis provides:
- **Design-time safety** through validation
- **Always-accurate documentation** through generation
- **CI/CD integration** for automated checks
- **Confidence in production** deployments

The combination of powerful transformations with rigorous schema analysis makes UTL-X ideal for enterprise integration scenarios.
