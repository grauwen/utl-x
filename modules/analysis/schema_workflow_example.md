# Complete Schema Generation Workflow Example

This example demonstrates the full design-time to runtime workflow with UTL-X schema generation.

## Scenario: E-commerce Order to Invoice Transformation

### Step 1: Design Time - Define Input Schema

**order-schema.xsd** (XML Schema for input):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="Order">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="OrderID" type="xs:string"/>
                <xs:element name="OrderDate" type="xs:date"/>
                <xs:element name="Customer">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="CustomerID" type="xs:string"/>
                            <xs:element name="Name" type="xs:string"/>
                            <xs:element name="Email" type="xs:string"/>
                            <xs:element name="VIPStatus" type="xs:boolean"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
                <xs:element name="Items">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Item" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="SKU" type="xs:string"/>
                                        <xs:element name="ProductName" type="xs:string"/>
                                        <xs:element name="Quantity" type="xs:integer"/>
                                        <xs:element name="UnitPrice" type="xs:decimal"/>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
```

### Step 2: Design Time - Write Transformation

**order-to-invoice.utlx**:
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    invoiceNumber: "INV-" + input.Order.OrderID,
    invoiceDate: now(),
    orderDate: parseDate(input.Order.OrderDate, "yyyy-MM-dd"),
    
    customer: {
      id: input.Order.Customer.CustomerID,
      name: input.Order.Customer.Name,
      email: input.Order.Customer.Email,
      vip: input.Order.Customer.VIPStatus
    },
    
    lineItems: input.Order.Items.Item |> map(item => {
      sku: item.SKU,
      description: item.ProductName,
      quantity: parseInt(item.Quantity),
      unitPrice: parseDecimal(item.UnitPrice),
      lineTotal: parseDecimal(item.UnitPrice) * parseInt(item.Quantity)
    }),
    
    let subtotal = sum(input.Order.Items.Item.(parseDecimal(UnitPrice) * parseInt(Quantity))),
    let discount = if (input.Order.Customer.VIPStatus == "true") 
                     subtotal * 0.20 
                   else 
                     0,
    let tax = (subtotal - discount) * 0.08,
    
    summary: {
      subtotal: subtotal,
      discount: discount,
      tax: tax,
      total: subtotal - discount + tax
    },
    
    metadata: {
      processedAt: now(),
      transformVersion: "1.0",
      vipApplied: input.Order.Customer.VIPStatus == "true"
    }
  }
}
```

### Step 3: Design Time - Generate Output Schema

```bash
# Generate JSON Schema for the invoice output
utlx schema generate \
  --input-schema order-schema.xsd \
  --transform order-to-invoice.utlx \
  --output-format json-schema \
  --output invoice-schema.json
```

**Generated invoice-schema.json**:
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Generated Invoice Schema",
  "description": "Auto-generated from order-to-invoice.utlx transformation",
  "type": "object",
  "properties": {
    "invoice": {
      "type": "object",
      "properties": {
        "invoiceNumber": {
          "type": "string",
          "description": "Generated from Order.OrderID with 'INV-' prefix"
        },
        "invoiceDate": {
          "type": "string",
          "format": "date-time",
          "description": "Current timestamp when invoice is generated"
        },
        "orderDate": {
          "type": "string",
          "format": "date",
          "description": "Original order date from Order.OrderDate"
        },
        "customer": {
          "type": "object",
          "properties": {
            "id": {
              "type": "string",
              "description": "From Order.Customer.CustomerID"
            },
            "name": {
              "type": "string",
              "description": "From Order.Customer.Name"
            },
            "email": {
              "type": "string",
              "format": "email",
              "description": "From Order.Customer.Email"
            },
            "vip": {
              "type": "boolean",
              "description": "From Order.Customer.VIPStatus"
            }
          },
          "required": ["id", "name", "email", "vip"]
        },
        "lineItems": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "sku": {
                "type": "string",
                "description": "From Item.SKU"
              },
              "description": {
                "type": "string",
                "description": "From Item.ProductName"
              },
              "quantity": {
                "type": "integer",
                "minimum": 1,
                "description": "From Item.Quantity"
              },
              "unitPrice": {
                "type": "number",
                "minimum": 0,
                "description": "From Item.UnitPrice"
              },
              "lineTotal": {
                "type": "number",
                "description": "Calculated as quantity * unitPrice"
              }
            },
            "required": ["sku", "description", "quantity", "unitPrice", "lineTotal"]
          },
          "minItems": 1
        },
        "summary": {
          "type": "object",
          "properties": {
            "subtotal": {
              "type": "number",
              "minimum": 0,
              "description": "Sum of all line totals"
            },
            "discount": {
              "type": "number",
              "minimum": 0,
              "description": "20% discount if VIP, otherwise 0"
            },
            "tax": {
              "type": "number",
              "minimum": 0,
              "description": "8% tax on (subtotal - discount)"
            },
            "total": {
              "type": "number",
              "minimum": 0,
              "description": "Final total after discount and tax"
            }
          },
          "required": ["subtotal", "discount", "tax", "total"]
        },
        "metadata": {
          "type": "object",
          "properties": {
            "processedAt": {
              "type": "string",
              "format": "date-time"
            },
            "transformVersion": {
              "type": "string"
            },
            "vipApplied": {
              "type": "boolean"
            }
          },
          "required": ["processedAt", "transformVersion", "vipApplied"]
        }
      },
      "required": ["invoiceNumber", "invoiceDate", "orderDate", "customer", "lineItems", "summary", "metadata"]
    }
  },
  "required": ["invoice"]
}
```

### Step 4: Design Time - Validate Transformation

```bash
# Validate that the transformation produces valid output
utlx schema validate \
  --input-schema order-schema.xsd \
  --transform order-to-invoice.utlx \
  --expected-output invoice-schema.json \
  --verbose
```

**Output**:
```
Validating transformation...
  Input schema: order-schema.xsd
  Transform: order-to-invoice.utlx
  Expected output: invoice-schema.json

✓ Validation successful!

Warnings:
  ⚠ Field 'invoice.invoiceDate' uses runtime value (now()) - cannot validate at design time
  ⚠ Field 'invoice.metadata.processedAt' uses runtime value (now()) - cannot validate at design time
```

### Step 5: Design Time - Generate API Documentation

```bash
# Generate OpenAPI spec for the transformation endpoint
utlx schema document \
  --input-schema order-schema.xsd \
  --transform order-to-invoice.utlx \
  --output openapi-spec.yaml \
  --api-path /api/orders/transform \
  --api-method POST
```

**Generated openapi-spec.yaml**:
```yaml
openapi: 3.0.0
info:
  title: Order to Invoice Transformation API
  version: 1.0.0
  description: Auto-generated from UTL-X transformation
paths:
  /api/orders/transform:
    post:
      summary: Transform order XML to invoice JSON
      requestBody:
        required: true
        content:
          application/xml:
            schema:
              $ref: '#/components/schemas/OrderInput'
      responses:
        '200':
          description: Successfully transformed order to invoice
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/InvoiceOutput'
        '400':
          description: Invalid input XML
        '500':
          description: Transformation error
components:
  schemas:
    OrderInput:
      # XSD converted to OpenAPI schema
      type: object
      # ... (from order-schema.xsd)
    InvoiceOutput:
      # JSON Schema used directly
      # ... (from invoice-schema.json)
```

### Step 6: Build Time - Integrate with CI/CD

**.github/workflows/validate-transformations.yml**:
```yaml
name: Validate Transformations

on:
  pull_request:
    paths:
      - 'transforms/**/*.utlx'
      - 'schemas/**'

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup UTL-X
        run: |
          curl -fsSL https://utlx.dev/install.sh | bash
          
      - name: Validate all transformations
        run: |
          for transform in transforms/*.utlx; do
            echo "Validating $transform"
            utlx schema validate \
              --input-schema schemas/$(basename $transform .utlx)-input.xsd \
              --transform $transform \
              --expected-output schemas/$(basename $transform .utlx)-output.json
          done
          
      - name: Generate updated schemas
        run: |
          for transform in transforms/*.utlx; do
            utlx schema generate \
              --input-schema schemas/$(basename $transform .utlx)-input.xsd \
              --transform $transform \
              --output-format json-schema \
              --output build/schemas/$(basename $transform .utlx)-output.json
          done
          
      - name: Check for schema changes
        run: |
          if git diff --quiet build/schemas/ schemas/; then
            echo "✓ Schemas are up to date"
          else
            echo "✗ Schema changes detected - please regenerate schemas"
            git diff build/schemas/ schemas/
            exit 1
          fi
```

### Step 7: Runtime - Execute Transformation

**Sample input (order.xml)**:
```xml
<?xml version="1.0"?>
<Order>
    <OrderID>12345</OrderID>
    <OrderDate>2025-10-14</OrderDate>
    <Customer>
        <CustomerID>CUST-001</CustomerID>
        <Name>Alice Johnson</Name>
        <Email>alice@example.com</Email>
        <VIPStatus>true</VIPStatus>
    </Customer>
    <Items>
        <Item>
            <SKU>WIDGET-001</SKU>
            <ProductName>Premium Widget</ProductName>
            <Quantity>2</Quantity>
            <UnitPrice>75.00</UnitPrice>
        </Item>
        <Item>
            <SKU>GADGET-002</SKU>
            <ProductName>Deluxe Gadget</ProductName>
            <Quantity>1</Quantity>
            <UnitPrice>150.00</UnitPrice>
        </Item>
    </Items>
</Order>
```

**Execute transformation**:
```bash
utlx transform order-to-invoice.utlx order.xml --output invoice.json
```

**Output (invoice.json)**:
```json
{
  "invoice": {
    "invoiceNumber": "INV-12345",
    "invoiceDate": "2025-10-14T10:30:00Z",
    "orderDate": "2025-10-14",
    "customer": {
      "id": "CUST-001",
      "name": "Alice Johnson",
      "email": "alice@example.com",
      "vip": true
    },
    "lineItems": [
      {
        "sku": "WIDGET-001",
        "description": "Premium Widget",
        "quantity": 2,
        "unitPrice": 75.00,
        "lineTotal": 150.00
      },
      {
        "sku": "GADGET-002",
        "description": "Deluxe Gadget",
        "quantity": 1,
        "unitPrice": 150.00,
        "lineTotal": 150.00
      }
    ],
    "summary": {
      "subtotal": 300.00,
      "discount": 60.00,
      "tax": 19.20,
      "total": 259.20
    },
    "metadata": {
      "processedAt": "2025-10-14T10:30:00Z",
      "transformVersion": "1.0",
      "vipApplied": true
    }
  }
}
```

### Step 8: Validate Runtime Output

```bash
# Validate output against generated schema
utlx validate --schema invoice-schema.json invoice.json
```

**Output**:
```
Validating invoice.json against invoice-schema.json...
✓ Validation successful!
  All required fields present
  All type constraints satisfied
  All format validations passed
```

## Benefits Demonstrated

### Design Time Benefits
✅ **Caught errors early** - Schema validation before deployment  
✅ **Auto-generated docs** - OpenAPI spec from transformation  
✅ **Contract clarity** - Clear input/output contracts  
✅ **Type safety** - Compile-time type checking  

### Runtime Benefits
✅ **Validation** - Ensure output conforms to schema  
✅ **Testing** - Automated contract testing in CI/CD  
✅ **Documentation** - Always-accurate API docs  
✅ **Confidence** - Know transformations work correctly  

### Developer Experience
✅ **IDE support** - Autocomplete and validation  
✅ **Quick feedback** - Instant validation during development  
✅ **Less debugging** - Catch issues at design time  
✅ **Better collaboration** - Clear contracts between teams  

## Workflow Summary

```
Design Time:
  1. Define input schema (XSD/JSON Schema)
  2. Write transformation (.utlx)
  3. Generate output schema ← NEW!
  4. Validate transformation ← NEW!
  5. Generate documentation ← NEW!

Build Time:
  6. CI/CD validates all transformations ← NEW!
  7. Detect breaking schema changes ← NEW!

Runtime:
  8. Execute transformation
  9. Validate output against schema ← NEW!
```

This workflow shows how schema generation at design-time significantly improves the development experience and confidence in UTL-X transformations!
