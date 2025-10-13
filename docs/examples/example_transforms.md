# UTL-X Real-World Example Transformations

Collection of practical transformation examples for common use cases.

## Example 1: E-commerce Order Transformation

### Input (order.xml)
```xml
<?xml version="1.0"?>
<Order id="ORD-2025-001" date="2025-01-15T10:30:00Z" currency="USD">
  <Customer>
    <CustomerID>CUST-12345</CustomerID>
    <n>John Smith</n>
    <Email>john.smith@example.com</Email>
    <Phone>+1-555-0123</Phone>
    <Address>
      <Street>123 Main St</Street>
      <City>New York</City>
      <State>NY</State>
      <ZipCode>10001</ZipCode>
      <Country>USA</Country>
    </Address>
  </Customer>
  <Items>
    <Item sku="LAPTOP-001" quantity="1" unitPrice="999.99" taxRate="0.08"/>
    <Item sku="MOUSE-042" quantity="2" unitPrice="29.99" taxRate="0.08"/>
    <Item sku="KEYBOARD-015" quantity="1" unitPrice="79.99" taxRate="0.08"/>
  </Items>
  <ShippingMethod>Express</ShippingMethod>
  <PaymentMethod>CreditCard</PaymentMethod>
</Order>
```

### Transform (order-to-invoice.utlx)
```utlx
%utlx 1.0
input xml
output json
---
{
  invoice: {
    invoiceNumber: "INV-" + input.Order.@id,
    invoiceDate: now(),
    orderDate: parseDate(input.Order.@date, "yyyy-MM-dd'T'HH:mm:ss'Z'"),
    currency: input.Order.@currency,
    
    customer: {
      id: input.Order.Customer.CustomerID,
      name: input.Order.Customer.Name,
      email: input.Order.Customer.Email,
      phone: input.Order.Customer.Phone,
      billingAddress: {
        street: input.Order.Customer.Address.Street,
        city: input.Order.Customer.Address.City,
        state: input.Order.Customer.Address.State,
        postalCode: input.Order.Customer.Address.ZipCode,
        country: input.Order.Customer.Address.Country
      }
    },
    
    lineItems: input.Order.Items.Item |> map(item => {
      let subtotal = parseFloat(item.@unitPrice) * parseInt(item.@quantity),
      let tax = subtotal * parseFloat(item.@taxRate)
      
      sku: item.@sku,
      quantity: parseInt(item.@quantity),
      unitPrice: parseFloat(item.@unitPrice),
      subtotal: subtotal,
      taxAmount: tax,
      total: subtotal + tax
    }),
    
    summary: {
      let items = input.Order.Items.Item,
      let subtotal = sum(items.(parseFloat(@unitPrice) * parseInt(@quantity))),
      let tax = sum(items.(parseFloat(@unitPrice) * parseInt(@quantity) * parseFloat(@taxRate))),
      let shipping = if (input.Order.ShippingMethod == "Express") 25.00 else 10.00
      
      subtotal: subtotal,
      tax: tax,
      shipping: shipping,
      total: subtotal + tax + shipping
    },
    
    shipping: {
      method: input.Order.ShippingMethod,
      estimatedDelivery: if (input.Order.ShippingMethod == "Express")
        addDays(now(), 1)
      else
        addDays(now(), 5)
    },
    
    payment: {
      method: input.Order.PaymentMethod,
      status: "Pending"
    }
  }
}
```

### Expected Output (invoice.json)
```json
{
  "invoice": {
    "invoiceNumber": "INV-ORD-2025-001",
    "invoiceDate": "2025-01-15T14:30:00Z",
    "orderDate": "2025-01-15T10:30:00Z",
    "currency": "USD",
    "customer": {
      "id": "CUST-12345",
      "name": "John Smith",
      "email": "john.smith@example.com",
      "phone": "+1-555-0123",
      "billingAddress": {
        "street": "123 Main St",
        "city": "New York",
        "state": "NY",
        "postalCode": "10001",
        "country": "USA"
      }
    },
    "lineItems": [
      {
        "sku": "LAPTOP-001",
        "quantity": 1,
        "unitPrice": 999.99,
        "subtotal": 999.99,
        "taxAmount": 79.99,
        "total": 1079.98
      },
      {
        "sku": "MOUSE-042",
        "quantity": 2,
        "unitPrice": 29.99,
        "subtotal": 59.98,
        "taxAmount": 4.80,
        "total": 64.78
      },
      {
        "sku": "KEYBOARD-015",
        "quantity": 1,
        "unitPrice": 79.99,
        "subtotal": 79.99,
        "taxAmount": 6.40,
        "total": 86.39
      }
    ],
    "summary": {
      "subtotal": 1139.96,
      "tax": 91.19,
      "shipping": 25.00,
      "total": 1256.15
    },
    "shipping": {
      "method": "Express",
      "estimatedDelivery": "2025-01-16T14:30:00Z"
    },
    "payment": {
      "method": "CreditCard",
      "status": "Pending"
    }
  }
}
```

### Run It
```bash
utlx transform order.xml order-to-invoice.utlx -o invoice.json
```

---

## Example 2: API Response Transformation

### Input (api-response.json)
```json
{
  "data": {
    "users": [
      {
        "id": "u001",
        "first_name": "Alice",
        "last_name": "Johnson",
        "email": "alice@example.com",
        "created_at": "2024-01-15T08:30:00Z",
        "is_active": true,
        "roles": ["admin", "developer"]
      },
      {
        "id": "u002",
        "first_name": "Bob",
        "last_name": "Smith",
        "email": "bob@example.com",
        "created_at": "2024-06-22T14:15:00Z",
        "is_active": false,
        "roles": ["viewer"]
      }
    ]
  },
  "meta": {
    "total": 2,
    "page": 1,
    "per_page": 10
  }
}
```

### Transform (api-to-csv.utlx)
```utlx
%utlx 1.0
input json
output csv
---
{
  headers: ["User ID", "Full Name", "Email", "Status", "Roles", "Member Since"],
  rows: input.data.users |> map(user => [
    user.id,
    user.first_name + " " + user.last_name,
    user.email,
    if (user.is_active) "Active" else "Inactive",
    join(user.roles, ";"),
    formatDate(parseDate(user.created_at, "yyyy-MM-dd'T'HH:mm:ss'Z'"), "yyyy-MM-dd")
  ])
}
```

### Expected Output (users.csv)
```csv
User ID,Full Name,Email,Status,Roles,Member Since
u001,Alice Johnson,alice@example.com,Active,admin;developer,2024-01-15
u002,Bob Smith,bob@example.com,Inactive,viewer,2024-06-22
```

### Run It
```bash
utlx transform api-response.json api-to-csv.utlx -o users.csv
```

---

## Example 3: Log Aggregation

### Input (logs.json)
```json
{
  "logs": [
    {"timestamp": "2025-01-15T10:00:00Z", "level": "ERROR", "service": "api", "message": "Database connection failed"},
    {"timestamp": "2025-01-15T10:01:00Z", "level": "WARN", "service": "api", "message": "High memory usage"},
    {"timestamp": "2025-01-15T10:02:00Z", "level": "INFO", "service": "worker", "message": "Job completed"},
    {"timestamp": "2025-01-15T10:03:00Z", "level": "ERROR", "service": "api", "message": "Timeout"},
    {"timestamp": "2025-01-15T10:04:00Z", "level": "ERROR", "service": "worker", "message": "Task failed"}
  ]
}
```

### Transform (aggregate-logs.utlx)
```utlx
%utlx 1.0
input json
output json
---
{
  summary: {
    totalLogs: count(input.logs),
    period: {
      start: first(input.logs).timestamp,
      end: last(input.logs).timestamp
    },
    byLevel: {
      errors: count(input.logs |> filter(log => log.level == "ERROR")),
      warnings: count(input.logs |> filter(log => log.level == "WARN")),
      info: count(input.logs |> filter(log => log.level == "INFO"))
    },
    byService: {
      api: count(input.logs |> filter(log => log.service == "api")),
      worker: count(input.logs |> filter(log => log.service == "worker"))
    }
  },
  
  errors: input.logs 
    |> filter(log => log.level == "ERROR")
    |> map(log => {
         timestamp: log.timestamp,
         service: log.service,
         message: log.message
       }),
       
  recommendations: [
    if (count(input.logs |> filter(log => log.level == "ERROR")) > 2)
      "High error rate detected - investigate immediately"
    else
      "Error rate normal",
      
    if (count(input.logs |> filter(log => log.level == "WARN")) > 0)
      "Warnings present - monitor closely"
    else
      "No warnings"
  ]
}
```

---

## Example 4: Data Enrichment

### Input (products.csv)
```csv
SKU,Name,Price,Category
P001,Laptop,999.99,Electronics
P002,Mouse,29.99,Electronics
P003,Desk,299.99,Furniture
```

### Transform (enrich-products.utlx)
```utlx
%utlx 1.0
input csv
output json
---
{
  products: input.rows |> map(row => {
    let price = parseFloat(row.Price),
    let category = row.Category
    
    sku: row.SKU,
    name: row.Name,
    price: price,
    category: category,
    
    // Enriched fields
    priceCategory: match price {
      p if p < 50 => "Budget",
      p if p < 200 => "Mid-range",
      _ => "Premium"
    },
    
    taxRate: match category {
      "Electronics" => 0.08,
      "Furniture" => 0.06,
      _ => 0.05
    },
    
    priceWithTax: price * (1 + match category {
      "Electronics" => 0.08,
      "Furniture" => 0.06,
      _ => 0.05
    }),
    
    tags: [
      category,
      if (price < 50) "affordable" else if (price > 500) "luxury" else "standard"
    ]
  })
}
```

---

## Example 5: XML Namespace Handling

### Input (soap-response.xml)
```xml
<?xml version="1.0"?>
<soap:Envelope 
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:ord="http://example.com/orders"
    xmlns:cust="http://example.com/customers">
  <soap:Body>
    <ord:OrderResponse>
      <ord:OrderId>12345</ord:OrderId>
      <ord:Status>Confirmed</ord:Status>
      <cust:CustomerInfo>
        <cust:Name>Alice Johnson</cust:Name>
        <cust:Email>alice@example.com</cust:Email>
      </cust:CustomerInfo>
    </ord:OrderResponse>
  </soap:Body>
</soap:Envelope>
```

### Transform (soap-to-json.utlx)
```utlx
%utlx 1.0
input xml {
  namespaces: {
    "soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "ord": "http://example.com/orders",
    "cust": "http://example.com/customers"
  }
}
output json
---
{
  order: {
    id: input.{"soap:Envelope"}.{"soap:Body"}.{"ord:OrderResponse"}.{"ord:OrderId"},
    status: input.{"soap:Envelope"}.{"soap:Body"}.{"ord:OrderResponse"}.{"ord:Status"},
    customer: {
      name: input.{"soap:Envelope"}.{"soap:Body"}.{"ord:OrderResponse"}.{"cust:CustomerInfo"}.{"cust:Name"},
      email: input.{"soap:Envelope"}.{"soap:Body"}.{"ord:OrderResponse"}.{"cust:CustomerInfo"}.{"cust:Email"}
    }
  }
}
```

---

## Example 6: Multi-Level Aggregation

### Input (sales-data.json)
```json
{
  "sales": [
    {"region": "North", "product": "Laptop", "quantity": 10, "revenue": 9999.90},
    {"region": "North", "product": "Mouse", "quantity": 50, "revenue": 1499.50},
    {"region": "South", "product": "Laptop", "quantity": 8, "revenue": 7999.92},
    {"region": "South", "product": "Keyboard", "quantity": 30, "revenue": 2399.70},
    {"region": "East", "product": "Mouse", "quantity": 60, "revenue": 1799.40}
  ]
}
```

### Transform (sales-report.utlx)
```utlx
%utlx 1.0
input json
output json
---
{
  report: {
    totalRevenue: sum(input.sales.(revenue)),
    totalQuantity: sum(input.sales.(quantity)),
    
    byRegion: groupBy(input.sales, sale => sale.region) |> map((region, sales) => {
      region: region,
      revenue: sum(sales.(revenue)),
      quantity: sum(sales.(quantity)),
      products: count(unique(sales.(product)))
    }),
    
    byProduct: groupBy(input.sales, sale => sale.product) |> map((product, sales) => {
      product: product,
      revenue: sum(sales.(revenue)),
      quantity: sum(sales.(quantity)),
      regions: count(unique(sales.(region)))
    }),
    
    topProducts: input.sales
      |> groupBy(sale => sale.product)
      |> map((product, sales) => {
           product: product,
           totalRevenue: sum(sales.(revenue))
         })
      |> sortBy(item => -item.totalRevenue)
      |> take(3)
  }
}
```

---

## Running These Examples

### Save the files
```bash
# Create examples directory
mkdir -p examples/real-world

# Save input and transform files
# (copy the content from above)
```

### Run transformations
```bash
# E-commerce order
utlx transform examples/real-world/order.xml \
    examples/real-world/order-to-invoice.utlx \
    -o invoice.json

# API to CSV
utlx transform examples/real-world/api-response.json \
    examples/real-world/api-to-csv.utlx \
    -o users.csv

# Log aggregation
utlx transform examples/real-world/logs.json \
    examples/real-world/aggregate-logs.utlx \
    -o log-summary.json

# Product enrichment
utlx transform examples/real-world/products.csv \
    examples/real-world/enrich-products.utlx \
    -o enriched-products.json

# SOAP to JSON
utlx transform examples/real-world/soap-response.xml \
    examples/real-world/soap-to-json.utlx \
    -o order-response.json

# Sales report
utlx transform examples/real-world/sales-data.json \
    examples/real-world/sales-report.utlx \
    -o sales-report.json
```

---

## Tips for Writing Transformations

1. **Use let bindings** for complex calculations
2. **Use map/filter/reduce** instead of loops
3. **Match expressions** for conditional logic
4. **Type conversion** with parseFloat, parseInt, parseDate
5. **String manipulation** with concat, split, join
6. **Aggregations** with sum, avg, count, min, max
7. **Array operations** with filter, map, take, drop, sortBy
8. **Date operations** with parseDate, formatDate, addDays

## Next Steps

- Try modifying these examples for your use cases
- Combine multiple transformations in pipelines
- Create reusable functions for common patterns
- Share your transformations with the community
