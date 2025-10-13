# Multiple Input Transformations

Examples showing how to combine data from multiple input sources.

## Basic Multi-Input Syntax

UTL-X supports reading from multiple input files using named inputs:

```utlx
%utlx 1.0
input {
  orders: json,
  customers: json,
  products: json
}
output json
---
```

## Example 1: Customer Order Enrichment

### Scenario

Enrich orders with customer and product details from separate data sources.

### Input: orders.json

```json
{
  "orders": [
    {"orderId": "ORD-001", "customerId": "C001", "productId": "P001", "quantity": 2, "date": "2025-10-01"},
    {"orderId": "ORD-002", "customerId": "C002", "productId": "P002", "quantity": 1, "date": "2025-10-02"},
    {"orderId": "ORD-003", "customerId": "C001", "productId": "P003", "quantity": 3, "date": "2025-10-03"}
  ]
}
```

### Input: customers.json

```json
{
  "customers": [
    {"id": "C001", "name": "Alice Johnson", "email": "alice@example.com", "tier": "Gold"},
    {"id": "C002", "name": "Bob Smith", "email": "bob@example.com", "tier": "Silver"}
  ]
}
```

### Input: products.json

```json
{
  "products": [
    {"id": "P001", "name": "Widget", "price": 29.99, "category": "Tools"},
    {"id": "P002", "name": "Gadget", "price": 149.99, "category": "Electronics"},
    {"id": "P003", "name": "Gizmo", "price": 79.99, "category": "Accessories"}
  ]
}
```

### Transformation: enrich-orders.utlx

```utlx
%utlx 1.0
input {
  orders: json,
  customers: json,
  products: json
}
output json
---

// Create lookup maps for efficient access
let customerMap = input.customers.customers 
  |> map(c => [c.id, c])
  |> reduce((acc, [id, customer]) => {
       ...acc,
       [id]: customer
     }, {}),

let productMap = input.products.products
  |> map(p => [p.id, p])
  |> reduce((acc, [id, product]) => {
       ...acc,
       [id]: product
     }, {})

// Enrich orders with customer and product data
{
  enrichedOrders: input.orders.orders |> map(order => {
    let customer = customerMap[order.customerId] ?? {name: "Unknown", tier: "Standard"},
    let product = productMap[order.productId] ?? {name: "Unknown", price: 0, category: "Other"},
    
    let subtotal = product.price * order.quantity,
    let discount = match customer.tier {
      "Gold" => 0.15,
      "Silver" => 0.10,
      _ => 0
    },
    let discountAmount = subtotal * discount,
    let total = subtotal - discountAmount
    
    {
      orderId: order.orderId,
      orderDate: order.date,
      customer: {
        id: order.customerId,
        name: customer.name,
        email: customer.email,
        tier: customer.tier
      },
      product: {
        id: order.productId,
        name: product.name,
        category: product.category,
        unitPrice: product.price
      },
      quantity: order.quantity,
      pricing: {
        subtotal: subtotal,
        discount: discountAmount,
        total: total
      }
    }
  }),
  
  summary: {
    totalOrders: count(input.orders.orders),
    totalRevenue: sum(input.orders.orders |> map(order => {
      let product = productMap[order.productId] ?? {price: 0}
      let customer = customerMap[order.customerId] ?? {tier: "Standard"}
      let subtotal = product.price * order.quantity
      let discount = match customer.tier {
        "Gold" => 0.15,
        "Silver" => 0.10,
        _ => 0
      }
      subtotal * (1 - discount)
    }))
  }
}
```

### Run Command

```bash
utlx transform enrich-orders.utlx \
  --input orders=orders.json \
  --input customers=customers.json \
  --input products=products.json \
  -o enriched-orders.json
```

### Output

```json
{
  "enrichedOrders": [
    {
      "orderId": "ORD-001",
      "orderDate": "2025-10-01",
      "customer": {
        "id": "C001",
        "name": "Alice Johnson",
        "email": "alice@example.com",
        "tier": "Gold"
      },
      "product": {
        "id": "P001",
        "name": "Widget",
        "category": "Tools",
        "unitPrice": 29.99
      },
      "quantity": 2,
      "pricing": {
        "subtotal": 59.98,
        "discount": 8.997,
        "total": 50.983
      }
    }
  ],
  "summary": {
    "totalOrders": 3,
    "totalRevenue": 298.47
  }
}
```

## Example 2: Multi-Format Data Merge

### Scenario

Combine employee data from CSV with department data from XML and roles from JSON.

### Input: employees.csv

```csv
emp_id,name,email,dept_id,salary
E001,Alice Johnson,alice@company.com,D001,75000
E002,Bob Smith,bob@company.com,D002,65000
E003,Charlie Brown,charlie@company.com,D001,70000
```

### Input: departments.xml

```xml
<Departments>
  <Department id="D001" name="Engineering" location="Building A"/>
  <Department id="D002" name="Sales" location="Building B"/>
  <Department id="D003" name="HR" location="Building C"/>
</Departments>
```

### Input: roles.json

```json
{
  "roles": [
    {"empId": "E001", "title": "Senior Engineer", "level": 5},
    {"empId": "E002", "title": "Sales Manager", "level": 4},
    {"empId": "E003", "title": "Engineer", "level": 3}
  ]
}
```

### Transformation: employee-directory.utlx

```utlx
%utlx 1.0
input {
  employees: csv,
  departments: xml,
  roles: json
}
output json
---

// Create department lookup
let deptMap = input.departments.Departments.Department
  |> map(dept => [
       dept.@id,
       {name: dept.@name, location: dept.@location}
     ])
  |> reduce((acc, [id, dept]) => {...acc, [id]: dept}, {}),

// Create role lookup
let roleMap = input.roles.roles
  |> map(role => [role.empId, role])
  |> reduce((acc, [id, role]) => {...acc, [id]: role}, {})

// Combine all data
{
  employees: input.employees.rows |> map(emp => {
    let dept = deptMap[emp.dept_id] ?? {name: "Unknown", location: "N/A"},
    let role = roleMap[emp.emp_id] ?? {title: "Staff", level: 1}
    
    {
      employeeId: emp.emp_id,
      personalInfo: {
        name: emp.name,
        email: emp.email
      },
      department: {
        id: emp.dept_id,
        name: dept.name,
        location: dept.location
      },
      position: {
        title: role.title,
        level: role.level
      },
      compensation: {
        salary: parseNumber(emp.salary)
      }
    }
  }),
  
  statistics: {
    totalEmployees: count(input.employees.rows),
    departmentCounts: input.employees.rows
      |> groupBy(emp => emp.dept_id)
      |> entries()
      |> map(([deptId, emps]) => {
           department: deptMap[deptId]?.name ?? "Unknown",
           count: count(emps)
         }),
    avgSalary: avg(input.employees.rows.(parseNumber(salary)))
  }
}
```

### Run Command

```bash
utlx transform employee-directory.utlx \
  --input employees=employees.csv \
  --input departments=departments.xml \
  --input roles=roles.json
```

## Example 3: Configuration Merge

### Scenario

Merge configuration from multiple sources with priority (defaults < environment < user).

### Input: defaults.json

```json
{
  "database": {
    "host": "localhost",
    "port": 5432,
    "timeout": 30
  },
  "cache": {
    "ttl": 3600,
    "maxSize": 1000
  }
}
```

### Input: environment.json

```json
{
  "database": {
    "host": "prod-db.example.com",
    "port": 5433
  },
  "cache": {
    "ttl": 7200
  }
}
```

### Input: user.json

```json
{
  "database": {
    "timeout": 60
  },
  "cache": {
    "maxSize": 5000
  }
}
```

### Transformation: merge-config.utlx

```utlx
%utlx 1.0
input {
  defaults: json,
  environment: json,
  user: json
}
output json
---

// Deep merge function
function deepMerge(base: Object, override: Object): Object {
  let baseKeys = keys(base),
  let overrideKeys = keys(override),
  let allKeys = distinct([...baseKeys, ...overrideKeys])
  
  allKeys |> reduce((acc, key) => {
    let baseVal = base[key],
    let overrideVal = override[key]
    
    if (overrideVal == null) {
      {...acc, [key]: baseVal}
    } else if (isObject(baseVal) && isObject(overrideVal)) {
      {...acc, [key]: deepMerge(baseVal, overrideVal)}
    } else {
      {...acc, [key]: overrideVal}
    }
  }, {})
}

// Merge with priority: defaults < environment < user
let step1 = deepMerge(input.defaults, input.environment),
let final = deepMerge(step1, input.user)

{
  config: final,
  metadata: {
    sources: ["defaults", "environment", "user"],
    mergedAt: formatDate(now(), "yyyy-MM-dd HH:mm:ss")
  }
}
```

## Example 4: Data Validation Across Sources

### Scenario

Validate referential integrity between orders, customers, and products.

### Transformation: validate-references.utlx

```utlx
%utlx 1.0
input {
  orders: json,
  customers: json,
  products: json
}
output json
---

let customerIds = input.customers.customers.*.id,
let productIds = input.products.products.*.id

{
  validation: {
    orders: input.orders.orders |> map(order => {
      let customerExists = contains(customerIds, order.customerId),
      let productExists = contains(productIds, order.productId)
      
      {
        orderId: order.orderId,
        valid: customerExists && productExists,
        errors: [
          if (!customerExists) "Customer " + order.customerId + " not found",
          if (!productExists) "Product " + order.productId + " not found"
        ] |> filter(e => e != false)
      }
    })
  },
  
  summary: {
    totalOrders: count(input.orders.orders),
    validOrders: count(input.orders.orders |> filter(order =>
      contains(customerIds, order.customerId) &&
      contains(productIds, order.productId)
    )),
    orphanedOrders: count(input.orders.orders |> filter(order =>
      !contains(customerIds, order.customerId) ||
      !contains(productIds, order.productId)
    ))
  },
  
  orphanedCustomerIds: input.orders.orders
    |> filter(order => !contains(customerIds, order.customerId))
    |> map(order => order.customerId)
    |> distinct(),
  
  orphanedProductIds: input.orders.orders
    |> filter(order => !contains(productIds, order.productId))
    |> map(order => order.productId)
    |> distinct()
}
```

## API Usage with Multiple Inputs

### JVM

```kotlin
val engine = UTLXEngine.builder()
    .compile(File("transform.utlx"))
    .build()

val inputs = mapOf(
    "orders" to File("orders.json").readText(),
    "customers" to File("customers.json").readText(),
    "products" to File("products.json").readText()
)

val output = engine.transformMultiple(inputs)
```

### JavaScript

```javascript
const utlx = require('@apache/utlx');
const fs = require('fs');

const engine = utlx.compile(
    fs.readFileSync('transform.utlx', 'utf8')
);

const inputs = {
    orders: fs.readFileSync('orders.json', 'utf8'),
    customers: fs.readFileSync('customers.json', 'utf8'),
    products: fs.readFileSync('products.json', 'utf8')
};

const output = engine.transformMultiple(inputs);
```

## Best Practices

### 1. Use Named Inputs for Clarity

```utlx
// ✅ Good - clear names
input {
  orders: json,
  customers: json,
  products: json
}

// ❌ Bad - unclear names
input {
  file1: json,
  file2: json,
  file3: json
}
```

### 2. Create Lookup Maps for Performance

```utlx
// ✅ Good - O(1) lookups
let customerMap = input.customers |> toMap(c => c.id)
let customer = customerMap[customerId]

// ❌ Bad - O(n) lookups in loop
input.orders |> map(order => {
  let customer = input.customers |> filter(c => c.id == order.customerId) |> first()
})
```

### 3. Validate References

```utlx
// Check that all references exist
let validOrder = contains(customerIds, order.customerId) &&
                 contains(productIds, order.productId)
```

### 4. Document Input Requirements

```utlx
/**
 * Enriches orders with customer and product data.
 * 
 * Inputs:
 *   - orders: JSON file with orders array
 *   - customers: JSON file with customers array (must have id field)
 *   - products: JSON file with products array (must have id field)
 * 
 * Output: JSON with enriched orders
 */
%utlx 1.0
input {
  orders: json,
  customers: json,
  products: json
}
```

## Advanced: Dynamic Input Count

For scenarios where the number of inputs varies:

```utlx
%utlx 1.0
input {
  main: json,
  supplements: array<json>  // Variable number of supplemental files
}
output json
---

{
  mainData: input.main,
  supplementalData: input.supplements |> map(supplement => 
    processSupplemental(supplement)
  )
}
```

---
