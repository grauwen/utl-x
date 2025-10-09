# Complex Transformations

Advanced UTL-X transformation examples for real-world scenarios.

## Multi-Level Aggregation

Transform nested orders data with grouping and calculations.

### Input

```json
{
  "orders": [
    {
      "id": "ORD-001",
      "customer": "Alice",
      "region": "West",
      "items": [
        {"product": "Widget", "quantity": 2, "price": 29.99},
        {"product": "Gadget", "quantity": 1, "price": 149.99}
      ]
    },
    {
      "id": "ORD-002",
      "customer": "Bob",
      "region": "East",
      "items": [
        {"product": "Tool", "quantity": 5, "price": 15.50}
      ]
    },
    {
      "id": "ORD-003",
      "customer": "Charlie",
      "region": "West",
      "items": [
        {"product": "Widget", "quantity": 1, "price": 29.99},
        {"product": "Tool", "quantity": 2, "price": 15.50}
      ]
    }
  ]
}
```

### Transformation

```utlx
%utlx 1.0
input json
output json
---
{
  // Regional summary
  byRegion: input.orders
    |> groupBy(order => order.region)
    |> entries()
    |> map(([region, orders]) => {
         region: region,
         orderCount: count(orders),
         revenue: sum(orders.(sum(items.(quantity * price)))),
         avgOrderValue: avg(orders.(sum(items.(quantity * price)))),
         topCustomers: orders
           |> sortBy(order => -sum(order.items.(quantity * price)))
           |> take(3)
           |> map(order => {
                customer: order.customer,
                total: sum(order.items.(quantity * price))
              })
       }),
  
  // Product summary
  byProduct: input.orders
    |> flatMap(order => order.items |> map(item => {
         product: item.product,
         quantity: item.quantity,
         revenue: item.quantity * item.price
       }))
    |> groupBy(item => item.product)
    |> entries()
    |> map(([product, items]) => {
         product: product,
         totalQuantity: sum(items.*.quantity),
         totalRevenue: sum(items.*.revenue),
         orderCount: count(distinct(items))
       })
    |> sortBy(item => -item.totalRevenue),
  
  // Overall metrics
  summary: {
    totalOrders: count(input.orders),
    totalRevenue: sum(input.orders.(sum(items.(quantity * price)))),
    avgOrderValue: avg(input.orders.(sum(items.(quantity * price)))),
    uniqueCustomers: count(distinct(input.orders.*.customer)),
    uniqueProducts: count(distinct(input.orders.*.items.*.product))
  }
}
```

### Output

```json
{
  "byRegion": [
    {
      "region": "West",
      "orderCount": 2,
      "revenue": 291.45,
      "avgOrderValue": 145.73,
      "topCustomers": [
        {"customer": "Alice", "total": 209.97},
        {"customer": "Charlie", "total": 81.48}
      ]
    },
    {
      "region": "East",
      "orderCount": 1,
      "revenue": 77.50,
      "avgOrderValue": 77.50,
      "topCustomers": [
        {"customer": "Bob", "total": 77.50}
      ]
    }
  ],
  "byProduct": [
    {
      "product": "Gadget",
      "totalQuantity": 1,
      "totalRevenue": 149.99,
      "orderCount": 1
    },
    {
      "product": "Tool",
      "totalQuantity": 7,
      "totalRevenue": 108.50,
      "orderCount": 2
    },
    {
      "product": "Widget",
      "totalQuantity": 3,
      "totalRevenue": 89.97,
      "orderCount": 2
    }
  ],
  "summary": {
    "totalOrders": 3,
    "totalRevenue": 368.95,
    "avgOrderValue": 122.98,
    "uniqueCustomers": 3,
    "uniqueProducts": 3
  }
}
```

## Recursive Hierarchical Transformation

Transform nested category tree with product counts.

### Input

```xml
<Categories>
  <Category id="1" name="Electronics">
    <Products>
      <Product id="P1" name="Laptop"/>
      <Product id="P2" name="Phone"/>
    </Products>
    <Category id="2" name="Computers">
      <Products>
        <Product id="P3" name="Desktop"/>
      </Products>
      <Category id="3" name="Accessories">
        <Products>
          <Product id="P4" name="Mouse"/>
          <Product id="P5" name="Keyboard"/>
        </Products>
      </Category>
    </Category>
    <Category id="4" name="Mobile">
      <Products>
        <Product id="P6" name="Tablet"/>
      </Products>
    </Category>
  </Category>
</Categories>
```

### Transformation

```utlx
%utlx 1.0
input xml
output json
---

function processCategory(cat: Object): Object {
  let directProducts = count(cat.Products.Product ?? []),
  let subcategories = cat.Category ?? [],
  let subcategoryData = subcategories |> map(sub => processCategory(sub)),
  let childProducts = sum(subcategoryData.*.totalProducts)
  
  {
    id: cat.@id,
    name: cat.@name,
    directProducts: directProducts,
    totalProducts: directProducts + childProducts,
    subcategories: subcategoryData,
    depth: if (isEmpty(subcategories)) 0 else 1 + max(subcategoryData.*.depth)
  }
}

{
  hierarchy: processCategory(input.Categories.Category)
}
```

## Data Enrichment with Lookup

Enrich orders with customer and product data from external sources.

### Input Orders

```json
{
  "orders": [
    {"orderId": "ORD-001", "customerId": "C001", "productId": "P001", "quantity": 2},
    {"orderId": "ORD-002", "customerId": "C002", "productId": "P002", "quantity": 1}
  ]
}
```

### Input Customers (separate file)

```json
{
  "customers": [
    {"id": "C001", "name": "Alice", "tier": "Gold", "discount": 0.10},
    {"id": "C002", "name": "Bob", "tier": "Silver", "discount": 0.05}
  ]
}
```

### Input Products (separate file)

```json
{
  "products": [
    {"id": "P001", "name": "Widget", "price": 29.99, "category": "Tools"},
    {"id": "P002", "name": "Gadget", "price": 149.99, "category": "Electronics"}
  ]
}
```

### Transformation

```utlx
%utlx 1.0
input json
output json
---

// Helper function to lookup customer
function lookupCustomer(customerId: String): Object {
  input.customers
    |> filter(c => c.id == customerId)
    |> first()
    ?? {id: customerId, name: "Unknown", tier: "Standard", discount: 0}
}

// Helper function to lookup product
function lookupProduct(productId: String): Object {
  input.products
    |> filter(p => p.id == productId)
    |> first()
    ?? {id: productId, name: "Unknown", price: 0, category: "Other"}
}

{
  enrichedOrders: input.orders |> map(order => {
    let customer = lookupCustomer(order.customerId),
    let product = lookupProduct(order.productId),
    let subtotal = product.price * order.quantity,
    let discount = subtotal * customer.discount,
    let total = subtotal - discount
    
    {
      orderId: order.orderId,
      customer: {
        id: customer.id,
        name: customer.name,
        tier: customer.tier
      },
      product: {
        id: product.id,
        name: product.name,
        category: product.category
      },
      quantity: order.quantity,
      pricing: {
        unitPrice: product.price,
        subtotal: subtotal,
        discountRate: customer.discount,
        discountAmount: discount,
        total: total
      }
    }
  })
}
```

## Data Validation and Cleansing

Validate and cleanse customer data with error reporting.

### Input

```json
{
  "customers": [
    {"id": "C001", "name": "Alice", "email": "alice@example.com", "age": 30, "phone": "555-0001"},
    {"id": "C002", "name": "", "email": "invalid-email", "age": -5, "phone": ""},
    {"id": "C003", "name": "Bob", "email": "bob@example.com", "age": 150, "phone": "555-0002"},
    {"id": "", "name": "Charlie", "email": "charlie@example.com", "age": 25, "phone": "not-a-phone"}
  ]
}
```

### Transformation

```utlx
%utlx 1.0
input json
output json
---

function validateCustomer(customer: Object): Object {
  let errors = []
  
  // Validate ID
  let validId = customer.id != null && customer.id != "",
  let idError = if (!validId) "Missing or empty ID" else null,
  
  // Validate name
  let validName = customer.name != null && customer.name != "",
  let nameError = if (!validName) "Missing or empty name" else null,
  
  // Validate email
  let validEmail = matches(customer.email ?? "", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
  let emailError = if (!validEmail) "Invalid email format" else null,
  
  // Validate age
  let ageNum = try { parseNumber(customer.age) } catch { -1 },
  let validAge = ageNum >= 0 && ageNum <= 120,
  let ageError = if (!validAge) "Age must be between 0 and 120" else null,
  
  // Validate phone
  let validPhone = matches(customer.phone ?? "", "^\\d{3}-\\d{4}$"),
  let phoneError = if (!validPhone) "Phone must be in format XXX-XXXX" else null,
  
  let allErrors = [idError, nameError, emailError, ageError, phoneError] 
    |> filter(e => e != null),
  
  {
    original: customer,
    isValid: isEmpty(allErrors),
    errors: allErrors,
    cleaned: if (isEmpty(allErrors)) {
      id: customer.id,
      name: trim(customer.name),
      email: lower(trim(customer.email)),
      age: ageNum,
      phone: customer.phone
    } else null
  }
}

let validated = input.customers |> map(c => validateCustomer(c))

{
  summary: {
    total: count(input.customers),
    valid: count(validated |> filter(v => v.isValid)),
    invalid: count(validated |> filter(v => !v.isValid))
  },
  valid: validated 
    |> filter(v => v.isValid) 
    |> map(v => v.cleaned),
  invalid: validated 
    |> filter(v => !v.isValid) 
    |> map(v => {
         original: v.original,
         errors: v.errors
       })
}
```

## Time-Series Aggregation

Aggregate time-series data with windowing.

### Input

```json
{
  "events": [
    {"timestamp": "2025-10-01T10:00:00Z", "type": "sale", "amount": 100},
    {"timestamp": "2025-10-01T10:15:00Z", "type": "sale", "amount": 150},
    {"timestamp": "2025-10-01T11:00:00Z", "type": "sale", "amount": 200},
    {"timestamp": "2025-10-01T11:30:00Z", "type": "refund", "amount": -50},
    {"timestamp": "2025-10-01T12:00:00Z", "type": "sale", "amount": 300}
  ]
}
```

### Transformation

```utlx
%utlx 1.0
input json
output json
---

function getHour(timestamp: String): String {
  substring(timestamp, 0, 13) + ":00:00Z"
}

{
  hourly: input.events
    |> groupBy(event => getHour(event.timestamp))
    |> entries()
    |> map(([hour, events]) => {
         hour: hour,
         sales: count(events |> filter(e => e.type == "sale")),
         refunds: count(events |> filter(e => e.type == "refund")),
         grossRevenue: sum(events |> filter(e => e.type == "sale") |> map(e => e.amount)),
         refundAmount: abs(sum(events |> filter(e => e.type == "refund") |> map(e => e.amount))),
         netRevenue: sum(events.*.amount)
       })
    |> sortBy(entry => entry.hour),
    
  daily: {
    totalSales: count(input.events |> filter(e => e.type == "sale")),
    totalRefunds: count(input.events |> filter(e => e.type == "refund")),
    grossRevenue: sum(input.events |> filter(e => e.type == "sale") |> map(e => e.amount)),
    refundAmount: abs(sum(input.events |> filter(e => e.type == "refund") |> map(e => e.amount))),
    netRevenue: sum(input.events.*.amount)
  }
}
```

---
