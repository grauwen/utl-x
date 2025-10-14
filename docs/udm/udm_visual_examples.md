# UDM Visual Guide & Real-World Examples

## Visual Architecture

### Overall Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         UTL-X Engine                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐      ┌─────────┐      ┌─────────────┐              │
│  │  Input  │─────▶│  Parse  │─────▶│  UDM Tree   │              │
│  │  Data   │      │ to UDM  │      │             │              │
│  └─────────┘      └─────────┘      └──────┬──────┘              │
│                                            │                    │
│      XML                                   │                    │
│      JSON                                  ▼                    │
│      CSV                          ┌─────────────────┐           │
│      YAML                         │  Transform      │           │
│                                   │  (UTL-X Logic)  │           │
│                                   └────────┬────────┘           │
│                                            │                    │
│                                            ▼                    │
│                                   ┌─────────────────┐           │
│                                   │  Transformed    │           │
│                                   │  UDM Tree       │           │
│                                   └────────┬────────┘           │
│                                            │                    │
│  ┌─────────┐      ┌──────────┐             │                    │
│  │ Output  │◀─────│ Serialize│◀────────────┘                    │
│  │  Data   │      │ from UDM │                                  │
│  └─────────┘      └──────────┘                                  │
│                                                                 │
│      XML                                                        │
│      JSON                                                       │
│      CSV                                                        │
│      YAML                                                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### UDM Node Hierarchy

```
                    UDMNode (abstract)
                         │
          ┌──────────────┼──────────────┬──────────┐
          │              │              │          │
     ScalarNode     ArrayNode      ObjectNode   NullNode
          │              │              │
    ┌─────┴─────┐        │              │
  value      type     elements      properties
(Any?)   (ValueType)  (List)         (Map)
                         │              │
              ┌──────────┴────┐  ┌──────┴───────┐
              │               │  │              │
           UDMNode ...     UDMNode         String → UDMNode
                                              │
                                           Recursive
```

### Metadata Structure

```
ObjectNode
    │
    ├── properties: Map<String, UDMNode>
    │       │
    │       ├── "name" → ScalarNode("Alice")
    │       ├── "age" → ScalarNode(30)
    │       └── "orders" → ArrayNode([...])
    │
    └── metadata: Metadata
            │
            ├── attributes: Map<String, String>
            │       ├── "id" → "USER-001"
            │       └── "status" → "active"
            │
            ├── namespace: String?
            │       └── "http://example.com/users"
            │
            ├── namespacePrefix: String?
            │       └── "usr"
            │
            └── custom: Map<String, Any>
                    ├── "lineNumber" → 42
                    └── "sourceFile" → "input.xml"
```

---

## Real-World Example 1: E-Commerce Order Processing

### Scenario
Transform XML order data from a legacy system to JSON for a modern API.

### Input XML
```xml
<?xml version="1.0"?>
<orders xmlns="http://shop.example.com/orders">
    <order id="ORD-2025-001" date="2025-01-15T10:30:00Z" status="confirmed">
        <customer custId="CUST-789">
            <name>Alice Johnson</name>
            <email>alice@example.com</email>
            <tier>GOLD</tier>
        </customer>
        <items>
            <item sku="LAPTOP-X1" quantity="1" unitPrice="1299.99">
                <name>UltraBook Pro X1</name>
                <category>Electronics</category>
            </item>
            <item sku="MOUSE-Z2" quantity="2" unitPrice="29.99">
                <name>Wireless Mouse Z2</name>
                <category>Accessories</category>
            </item>
        </items>
        <shipping>
            <method>EXPRESS</method>
            <address>
                <street>123 Main St</street>
                <city>San Francisco</city>
                <state>CA</state>
                <zip>94102</zip>
            </address>
        </shipping>
        <payment>
            <method>CREDIT_CARD</method>
            <last4>4242</last4>
        </payment>
    </order>
</orders>
```

### UDM Representation (Simplified)

```
ObjectNode("orders")
└── metadata.namespace = "http://shop.example.com/orders"
└── ObjectNode("order")
    ├── metadata.attributes
    │   ├── "id" = "ORD-2025-001"
    │   ├── "date" = "2025-01-15T10:30:00Z"
    │   └── "status" = "confirmed"
    │
    ├── ObjectNode("customer")
    │   ├── metadata.attributes["custId"] = "CUST-789"
    │   ├── ScalarNode("name") = "Alice Johnson"
    │   ├── ScalarNode("email") = "alice@example.com"
    │   └── ScalarNode("tier") = "GOLD"
    │
    ├── ObjectNode("items")
    │   └── ArrayNode("item")
    │       ├── ObjectNode[0]
    │       │   ├── metadata.attributes
    │       │   │   ├── "sku" = "LAPTOP-X1"
    │       │   │   ├── "quantity" = "1"
    │       │   │   └── "unitPrice" = "1299.99"
    │       │   ├── ScalarNode("name") = "UltraBook Pro X1"
    │       │   └── ScalarNode("category") = "Electronics"
    │       │
    │       └── ObjectNode[1]
    │           ├── metadata.attributes
    │           │   ├── "sku" = "MOUSE-Z2"
    │           │   ├── "quantity" = "2"
    │           │   └── "unitPrice" = "29.99"
    │           ├── ScalarNode("name") = "Wireless Mouse Z2"
    │           └── ScalarNode("category") = "Accessories"
    │
    ├── ObjectNode("shipping") → ...
    └── ObjectNode("payment") → ...
```

### UTL-X Transformation

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: input.orders.order.@id,
  orderDate: parseDate(input.orders.order.@date, "ISO8601"),
  status: input.orders.order.@status,
  
  customer: {
    id: input.orders.order.customer.@custId,
    name: input.orders.order.customer.name,
    email: input.orders.order.customer.email,
    vip: input.orders.order.customer.tier == "GOLD"
  },
  
  items: input.orders.order.items.item |> map(item => {
    sku: item.@sku,
    name: item.name,
    category: item.category,
    quantity: toNumber(item.@quantity),
    unitPrice: toNumber(item.@unitPrice),
    subtotal: toNumber(item.@quantity) * toNumber(item.@unitPrice)
  }),
  
  shipping: {
    method: input.orders.order.shipping.method,
    address: {
      street: input.orders.order.shipping.address.street,
      city: input.orders.order.shipping.address.city,
      state: input.orders.order.shipping.address.state,
      zipCode: input.orders.order.shipping.address.zip
    }
  },
  
  payment: {
    method: input.orders.order.payment.method,
    cardLast4: input.orders.order.payment.last4
  },
  
  summary: {
    itemCount: count(input.orders.order.items.item),
    subtotal: sum(input.orders.order.items.item |> map(i => 
      toNumber(i.@quantity) * toNumber(i.@unitPrice)
    )),
    shippingFee: if (input.orders.order.shipping.method == "EXPRESS") 15.00 else 5.00
  }
}
```

### Output JSON

```json
{
  "orderId": "ORD-2025-001",
  "orderDate": "2025-01-15T10:30:00Z",
  "status": "confirmed",
  "customer": {
    "id": "CUST-789",
    "name": "Alice Johnson",
    "email": "alice@example.com",
    "vip": true
  },
  "items": [
    {
      "sku": "LAPTOP-X1",
      "name": "UltraBook Pro X1",
      "category": "Electronics",
      "quantity": 1,
      "unitPrice": 1299.99,
      "subtotal": 1299.99
    },
    {
      "sku": "MOUSE-Z2",
      "name": "Wireless Mouse Z2",
      "category": "Accessories",
      "quantity": 2,
      "unitPrice": 29.99,
      "subtotal": 59.98
    }
  ],
  "shipping": {
    "method": "EXPRESS",
    "address": {
      "street": "123 Main St",
      "city": "San Francisco",
      "state": "CA",
      "zipCode": "94102"
    }
  },
  "payment": {
    "method": "CREDIT_CARD",
    "cardLast4": "4242"
  },
  "summary": {
    "itemCount": 2,
    "subtotal": 1359.97,
    "shippingFee": 15.00
  }
}
```

### Navigation Path Examples

```
Path                                          Result
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
input.orders.order.@id                        "ORD-2025-001"
input.orders.order.customer.name              "Alice Johnson"
input.orders.order.items.item[0].name         "UltraBook Pro X1"
input.orders.order.items.item[*].@sku         ["LAPTOP-X1", "MOUSE-Z2"]
input.orders.order..name                      All "name" nodes recursively
```

---

## Real-World Example 2: CSV to JSON Analytics Dashboard

### Scenario
Convert sales CSV data to JSON for a dashboard API.

### Input CSV
```csv
date,region,product,quantity,revenue,salesperson
2025-01-15,West,Laptop X1,5,6499.95,Alice
2025-01-15,East,Mouse Z2,20,599.80,Bob
2025-01-15,West,Keyboard Y3,10,499.90,Alice
2025-01-16,East,Laptop X1,3,3899.97,Bob
2025-01-16,West,Mouse Z2,15,449.85,Charlie
```

### UDM Representation

```
ArrayNode [
    ObjectNode {
        "date" → ScalarNode("2025-01-15", DATE)
        "region" → ScalarNode("West", STRING)
        "product" → ScalarNode("Laptop X1", STRING)
        "quantity" → ScalarNode(5, INTEGER)
        "revenue" → ScalarNode(6499.95, NUMBER)
        "salesperson" → ScalarNode("Alice", STRING)
    },
    ObjectNode {
        "date" → ScalarNode("2025-01-15", DATE)
        "region" → ScalarNode("East", STRING)
        "product" → ScalarNode("Mouse Z2", STRING)
        "quantity" → ScalarNode(20, INTEGER)
        "revenue" → ScalarNode(599.80, NUMBER)
        "salesperson" → ScalarNode("Bob", STRING)
    },
    // ... more rows
]
```

### UTL-X Transformation

```utlx
%utlx 1.0
input csv
output json
---
{
  summary: {
    totalRevenue: sum(input.*.revenue),
    totalQuantity: sum(input.*.quantity),
    averageRevenue: avg(input.*.revenue),
    uniqueProducts: distinct(input.*.product) |> count(),
    dateRange: {
      start: min(input.*.date),
      end: max(input.*.date)
    }
  },
  
  byRegion: input 
    |> groupBy(row => row.region)
    |> mapValues(rows => {
        region: rows[0].region,
        totalRevenue: sum(rows.*.revenue),
        totalQuantity: sum(rows.*.quantity),
        topProduct: rows 
          |> sortBy(r => r.revenue) 
          |> reverse() 
          |> first() 
          |> get("product")
      }),
  
  bySalesperson: input
    |> groupBy(row => row.salesperson)
    |> mapValues(rows => {
        name: rows[0].salesperson,
        totalSales: sum(rows.*.revenue),
        transactions: count(rows),
        averageSale: avg(rows.*.revenue)
      }),
  
  topProducts: input
    |> groupBy(row => row.product)
    |> mapValues((product, rows) => {
        product: product,
        totalRevenue: sum(rows.*.revenue),
        totalQuantity: sum(rows.*.quantity)
      })
    |> values()
    |> sortBy(p => p.totalRevenue)
    |> reverse()
    |> take(5),
  
  dailyTrends: input
    |> groupBy(row => row.date)
    |> mapValues((date, rows) => {
        date: date,
        revenue: sum(rows.*.revenue),
        transactions: count(rows)
      })
    |> values()
    |> sortBy(d => d.date)
}
```

### Output JSON

```json
{
  "summary": {
    "totalRevenue": 12949.47,
    "totalQuantity": 53,
    "averageRevenue": 2589.89,
    "uniqueProducts": 3,
    "dateRange": {
      "start": "2025-01-15",
      "end": "2025-01-16"
    }
  },
  "byRegion": {
    "West": {
      "region": "West",
      "totalRevenue": 7449.70,
      "totalQuantity": 30,
      "topProduct": "Laptop X1"
    },
    "East": {
      "region": "East",
      "totalRevenue": 5499.77,
      "totalQuantity": 23,
      "topProduct": "Laptop X1"
    }
  },
  "bySalesperson": {
    "Alice": {
      "name": "Alice",
      "totalSales": 6999.85,
      "transactions": 2,
      "averageSale": 3499.93
    },
    "Bob": {
      "name": "Bob",
      "totalSales": 4499.77,
      "transactions": 2,
      "averageSale": 2249.89
    },
    "Charlie": {
      "name": "Charlie",
      "totalSales": 449.85,
      "transactions": 1,
      "averageSale": 449.85
    }
  },
  "topProducts": [
    {
      "product": "Laptop X1",
      "totalRevenue": 10399.92,
      "totalQuantity": 8
    },
    {
      "product": "Mouse Z2",
      "totalRevenue": 1049.65,
      "totalQuantity": 35
    },
    {
      "product": "Keyboard Y3",
      "totalRevenue": 499.90,
      "totalQuantity": 10
    }
  ],
  "dailyTrends": [
    {
      "date": "2025-01-15",
      "revenue": 7599.65,
      "transactions": 3
    },
    {
      "date": "2025-01-16",
      "revenue": 5349.82,
      "transactions": 2
    }
  ]
}
```

---

## Real-World Example 3: Multi-Format Data Integration

### Scenario
Integrate customer data from three sources: XML (CRM), JSON (Web App), CSV (Legacy System).

### Source 1: XML (CRM System)

```xml
<customers>
    <customer id="C001">
        <profile>
            <firstName>Alice</firstName>
            <lastName>Johnson</lastName>
            <email>alice@example.com</email>
        </profile>
        <preferences>
            <newsletter>true</newsletter>
            <notifications>email,sms</notifications>
        </preferences>
    </customer>
</customers>
```

### Source 2: JSON (Web Application)

```json
{
  "users": [
    {
      "userId": "C001",
      "activity": {
        "lastLogin": "2025-01-15T14:30:00Z",
        "loginCount": 47,
        "avgSessionMinutes": 25
      }
    }
  ]
}
```

### Source 3: CSV (Legacy System)

```csv
customerId,accountType,balance,creditLimit,joinDate
C001,PREMIUM,2450.75,5000.00,2022-03-15
```

### Unified UDM Structure

All three sources are parsed into UDM, then merged:

```
┌─────────────────────────────────────────────────────────────┐
│                      Combined UDM                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ObjectNode("customer")                                     │
│    ├── "id" → ScalarNode("C001")                           │
│    │                                                        │
│    ├── "profile" → ObjectNode                              │
│    │     ├── "firstName" → ScalarNode("Alice")             │
│    │     ├── "lastName" → ScalarNode("Johnson")            │
│    │     └── "email" → ScalarNode("alice@example.com")     │
│    │                                                        │
│    ├── "preferences" → ObjectNode                          │
│    │     ├── "newsletter" → ScalarNode(true)               │
│    │     └── "notifications" → ArrayNode[                  │
│    │             ScalarNode("email"),                      │
│    │             ScalarNode("sms")                         │
│    │         ]                                             │
│    │                                                        │
│    ├── "activity" → ObjectNode                             │
│    │     ├── "lastLogin" → ScalarNode(DateTime)            │
│    │     ├── "loginCount" → ScalarNode(47)                 │
│    │     └── "avgSessionMinutes" → ScalarNode(25)          │
│    │                                                        │
│    └── "account" → ObjectNode                              │
│          ├── "type" → ScalarNode("PREMIUM")                │
│          ├── "balance" → ScalarNode(2450.75)               │
│          ├── "creditLimit" → ScalarNode(5000.00)           │
│          └── "joinDate" → ScalarNode(Date)                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### UTL-X Merge Transformation

```utlx
%utlx 1.0
input auto
output json
---

function mergeCustomer(id: String): Object {
  let crmData = crmInput.customers.customer[id == @id],
  let webData = webInput.users[userId == id],
  let legacyData = legacyInput[customerId == id]
  
  {
    customerId: id,
    
    // From CRM (XML)
    profile: {
      firstName: crmData.profile.firstName,
      lastName: crmData.profile.lastName,
      fullName: crmData.profile.firstName + " " + crmData.profile.lastName,
      email: crmData.profile.email
    },
    
    preferences: {
      newsletter: crmData.preferences.newsletter,
      notifications: split(crmData.preferences.notifications, ",")
    },
    
    // From Web App (JSON)
    activity: webData.activity,
    
    // From Legacy System (CSV)
    account: {
      type: legacyData.accountType,
      balance: legacyData.balance,
      creditLimit: legacyData.creditLimit,
      joinDate: legacyData.joinDate,
      memberYears: diffYears(legacyData.joinDate, now())
    },
    
    // Computed fields
    computed: {
      isPremium: legacyData.accountType == "PREMIUM",
      creditUtilization: (legacyData.balance / legacyData.creditLimit) * 100,
      activeUser: webData.activity.lastLogin > addDays(now(), -30),
      engagementScore: calculateEngagement(webData.activity, crmData.preferences)
    }
  }
}

// Main output
{
  customers: getAllCustomerIds() |> map(id => mergeCustomer(id))
}
```

---

## Transformation Patterns

### Pattern 1: Flattening Nested Structure

```
Before (UDM):                    After (UDM):
ObjectNode                       ObjectNode
  └── "user"                       ├── "userId"
        ├── "id"                   ├── "userName"
        ├── "profile"              ├── "userEmail"
        │     ├── "name"           ├── "addressStreet"
        │     └── "email"          ├── "addressCity"
        └── "address"              └── "addressZip"
              ├── "street"
              ├── "city"
              └── "zip"
```

### Pattern 2: Grouping and Aggregation

```
Before (UDM):                    After (UDM):
ArrayNode [                      ObjectNode
  ObjectNode {region, sales},      └── "byRegion"
  ObjectNode {region, sales},            ├── "West" → total
  ObjectNode {region, sales}             ├── "East" → total
]                                        └── "North" → total
```

### Pattern 3: Denormalization

```
Before (UDM):                         After (UDM):
ObjectNode                            ArrayNode [
  ├── "orders" → ArrayNode              ObjectNode {
  │     └── [orderId, custId]             orderId,
  └── "customers" → ArrayNode             customerName,
        └── [custId, name]                customerEmail
                                         },
                                         ...
                                       ]
```

---

## Performance Visualization

### Memory Comparison

```
Data Size: 1 MB XML

┌─────────────────────────────────────────────┐
│ XML DOM (Traditional):     ~12 MB          │
│ ████████████████████████████████████░       │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ UDM (Optimized):          ~4 MB            │
│ ████████████░                               │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ UDM (Streaming):          ~0.5 MB          │
│ ██░                                         │
└─────────────────────────────────────────────┘
```

### Parse Time Comparison

```
Parsing 10,000 records:

XML → UDM:    ██████████░           ~150ms
JSON → UDM:   █████░                ~75ms
CSV → UDM:    ███░                  ~40ms
```

---

## Summary

### Key Takeaways

1. **UDM provides a unified view** of heterogeneous data formats
2. **Transformations work identically** regardless of input/output format
3. **Metadata preservation** ensures no information loss
4. **Real-world applications** include e-commerce, analytics, and data integration
5. **Performance is optimized** through streaming and structural sharing

### When to Use UDM

✅ **Use UDM when:**
- Converting between data formats
- Processing data from multiple sources
- Building format-agnostic transformation pipelines
- Implementing ETL/ELT workflows
- Creating universal APIs

❌ **Consider alternatives when:**
- Working with a single, stable format
- Performance is more critical than flexibility
- Binary protocol efficiency is required (use Avro/Protobuf)
- Simple templating is sufficient (use Mustache/Handlebars)

---

**Project:** UTL-X (Universal Transformation Language Extended)  
**Component:** UDM (Universal Data Model) - Visual Guide  
**Author:** Ir. Marcel A. Grauwen  
**License:** AGPL-3.0 / Commercial
