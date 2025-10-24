# XML to JSON Examples

Practical examples of transforming XML to JSON using UTL-X.

---

## Example 1: Simple Transformation

### Input XML

```xml
<Order id="12345">
  <Customer>John Doe</Customer>
  <Total>150.00</Total>
</Order>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  customer: $input.Order.Customer,
  total: parseNumber($input.Order.Total)
}
```

### Output JSON

```json
{
  "orderId": "12345",
  "customer": "John Doe",
  "total": 150.00
}
```

---

## Example 2: Nested Structure

### Input XML

```xml
<Order id="67890" date="2026-01-15">
  <Customer>
    <Name>Alice Johnson</Name>
    <Email>alice@example.com</Email>
    <Address>
      <Street>123 Main St</Street>
      <City>Springfield</City>
      <Zip>12345</Zip>
    </Address>
  </Customer>
  <Items>
    <Item sku="WIDGET-001" quantity="2" price="25.00"/>
    <Item sku="GADGET-002" quantity="1" price="100.00"/>
  </Items>
</Order>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  order: {
    id: $input.Order.@id,
    date: $input.Order.@date,
    customer: {
      name: $input.Order.Customer.Name,
      email: $input.Order.Customer.Email,
      address: {
        street: $input.Order.Customer.Address.Street,
        city: $input.Order.Customer.Address.City,
        zip: $input.Order.Customer.Address.Zip
      }
    },
    items: $input.Order.Items.Item |> map(item => {
      sku: item.@sku,
      quantity: parseNumber(item.@quantity),
      price: parseNumber(item.@price),
      subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
    })
  }
}
```

### Output JSON

```json
{
  "order": {
    "id": "67890",
    "date": "2026-01-15",
    "customer": {
      "name": "Alice Johnson",
      "email": "alice@example.com",
      "address": {
        "street": "123 Main St",
        "city": "Springfield",
        "zip": "12345"
      }
    },
    "items": [
      {
        "sku": "WIDGET-001",
        "quantity": 2,
        "price": 25.00,
        "subtotal": 50.00
      },
      {
        "sku": "GADGET-002",
        "quantity": 1,
        "price": 100.00,
        "subtotal": 100.00
      }
    ]
  }
}
```

---

## Example 3: Array Transformation

### Input XML

```xml
<Products>
  <Product id="1" category="Electronics">
    <Name>Laptop</Name>
    <Price>999.99</Price>
    <InStock>true</InStock>
  </Product>
  <Product id="2" category="Electronics">
    <Name>Mouse</Name>
    <Price>29.99</Price>
    <InStock>false</InStock>
  </Product>
  <Product id="3" category="Books">
    <Name>Programming Guide</Name>
    <Price>49.99</Price>
    <InStock>true</InStock>
  </Product>
</Products>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  products: $input.Products.Product |> map(product => {
    id: parseNumber(product.@id),
    category: product.@category,
    name: product.Name,
    price: parseNumber(product.Price),
    inStock: product.InStock == "true"
  })
}
```

### Output JSON

```json
{
  "products": [
    {
      "id": 1,
      "category": "Electronics",
      "name": "Laptop",
      "price": 999.99,
      "inStock": true
    },
    {
      "id": 2,
      "category": "Electronics",
      "name": "Mouse",
      "price": 29.99,
      "inStock": false
    },
    {
      "id": 3,
      "category": "Books",
      "name": "Programming Guide",
      "price": 49.99,
      "inStock": true
    }
  ]
}
```

---

## Example 4: Aggregation

### Input XML

```xml
<Sales>
  <Sale region="North" amount="1000.00"/>
  <Sale region="South" amount="1500.00"/>
  <Sale region="North" amount="800.00"/>
  <Sale region="East" amount="1200.00"/>
  <Sale region="South" amount="900.00"/>
</Sales>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  let sales = $input.Sales.Sale,
  
  summary: {
    total: sum(sales |> map(s => parseNumber(s.@amount))),
    count: count(sales),
    average: avg(sales |> map(s => parseNumber(s.@amount))),
    max: max(sales |> map(s => parseNumber(s.@amount))),
    min: min(sales |> map(s => parseNumber(s.@amount)))
  },
  
  byRegion: sales 
    |> groupBy(sale => sale.@region)
    |> map((region, regionSales) => {
        region: region,
        total: sum(regionSales |> map(s => parseNumber(s.@amount))),
        count: count(regionSales)
      })
}
```

### Output JSON

```json
{
  "summary": {
    "total": 5400.00,
    "count": 5,
    "average": 1080.00,
    "max": 1500.00,
    "min": 800.00
  },
  "byRegion": [
    {
      "region": "North",
      "total": 1800.00,
      "count": 2
    },
    {
      "region": "South",
      "total": 2400.00,
      "count": 2
    },
    {
      "region": "East",
      "total": 1200.00,
      "count": 1
    }
  ]
}
```

---

## Example 5: Template Matching

### Input XML

```xml
<Library>
  <Book isbn="123" year="2020">
    <Title>Learning UTL-X</Title>
    <Author>
      <Name>Jane Smith</Name>
      <Country>USA</Country>
    </Author>
    <Price>39.99</Price>
  </Book>
  <Book isbn="456" year="2021">
    <Title>Advanced Transformations</Title>
    <Author>
      <Name>John Doe</Name>
      <Country>UK</Country>
    </Author>
    <Price>49.99</Price>
  </Book>
</Library>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---

template match="Library" {
  library: {
    books: apply(Book)
  }
}

template match="Book" {
  isbn: $isbn,
  year: parseNumber($year),
  title: Title,
  author: apply(Author),
  price: parseNumber(Price)
}

template match="Author" {
  name: Name,
  country: Country
}
```

### Output JSON

```json
{
  "library": {
    "books": [
      {
        "isbn": "123",
        "year": 2020,
        "title": "Learning UTL-X",
        "author": {
          "name": "Jane Smith",
          "country": "USA"
        },
        "price": 39.99
      },
      {
        "isbn": "456",
        "year": 2021,
        "title": "Advanced Transformations",
        "author": {
          "name": "John Doe",
          "country": "UK"
        },
        "price": 49.99
      }
    ]
  }
}
```

---

## Example 6: Filtering and Sorting

### Input XML

```xml
<Inventory>
  <Item id="1" name="Widget" price="15.99" stock="100"/>
  <Item id="2" name="Gadget" price="99.99" stock="5"/>
  <Item id="3" name="Doohickey" price="25.00" stock="50"/>
  <Item id="4" name="Thingamajig" price="5.99" stock="200"/>
  <Item id="5" name="Whatchamacallit" price="150.00" stock="2"/>
</Inventory>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  let items = $input.Inventory.Item,
  
  // Items with low stock (< 10)
  lowStock: items 
    |> filter(item => parseNumber(item.@stock) < 10)
    |> map(item => {
        id: item.@id,
        name: item.@name,
        stock: parseNumber(item.@stock)
      })
    |> sortBy(item => item.stock),
  
  // Expensive items (> $50), sorted by price
  expensive: items
    |> filter(item => parseNumber(item.@price) > 50)
    |> map(item => {
        id: item.@id,
        name: item.@name,
        price: parseNumber(item.@price)
      })
    |> sortBy(item => -item.price),  // Sort descending
  
  // All items sorted by name
  allItems: items
    |> map(item => {
        id: item.@id,
        name: item.@name,
        price: parseNumber(item.@price),
        stock: parseNumber(item.@stock)
      })
    |> sortBy(item => item.name)
}
```

### Output JSON

```json
{
  "lowStock": [
    {
      "id": "5",
      "name": "Whatchamacallit",
      "stock": 2
    },
    {
      "id": "2",
      "name": "Gadget",
      "stock": 5
    }
  ],
  "expensive": [
    {
      "id": "5",
      "name": "Whatchamacallit",
      "price": 150.00
    },
    {
      "id": "2",
      "name": "Gadget",
      "price": 99.99
    }
  ],
  "allItems": [
    {
      "id": "3",
      "name": "Doohickey",
      "price": 25.00,
      "stock": 50
    },
    {
      "id": "2",
      "name": "Gadget",
      "price": 99.99,
      "stock": 5
    },
    {
      "id": "4",
      "name": "Thingamajig",
      "price": 5.99,
      "stock": 200
    },
    {
      "id": "5",
      "name": "Whatchamacallit",
      "price": 150.00,
      "stock": 2
    },
    {
      "id": "1",
      "name": "Widget",
      "price": 15.99,
      "stock": 100
    }
  ]
}
```

---

## Example 7: Conditional Logic

### Input XML

```xml
<Orders>
  <Order id="001" total="150.00" customerType="VIP"/>
  <Order id="002" total="85.00" customerType="Regular"/>
  <Order id="003" total="1200.00" customerType="Regular"/>
  <Order id="004" total="45.00" customerType="VIP"/>
</Orders>
```

### UTL-X Script

```utlx
%utlx 1.0
input xml
output json
---
{
  orders: $input.Orders.Order |> map(order => {
    let total = parseNumber(order.@total),
    let isVIP = order.@customerType == "VIP",
    
    id: order.@id,
    total: total,
    customerType: order.@customerType,
    
    // Calculate discount based on rules
    discount: if (isVIP && total > 100) total * 0.20
              else if (isVIP) total * 0.10
              else if (total > 1000) total * 0.10
              else if (total > 500) total * 0.05
              else 0,
    
    // Free shipping for VIP or orders > $100
    freeShipping: isVIP || total > 100,
    
    // Priority processing for VIP or large orders
    priority: if (isVIP) "High"
              else if (total > 1000) "High"
              else if (total > 500) "Medium"
              else "Standard"
  })
}
```

### Output JSON

```json
{
  "orders": [
    {
      "id": "001",
      "total": 150.00,
      "customerType": "VIP",
      "discount": 30.00,
      "freeShipping": true,
      "priority": "High"
    },
    {
      "id": "002",
      "total": 85.00,
      "customerType": "Regular",
      "discount": 0,
      "freeShipping": false,
      "priority": "Standard"
    },
    {
      "id": "003",
      "total": 1200.00,
      "customerType": "Regular",
      "discount": 120.00,
      "freeShipping": true,
      "priority": "High"
    },
    {
      "id": "004",
      "total": 45.00,
      "customerType": "VIP",
      "discount": 4.50,
      "freeShipping": true,
      "priority": "High"
    }
  ]
}
```

---

## Common Patterns

### Pattern 1: Rename Fields

```utlx
{
  newName: $input.OldName,
  newEmail: $input.OldEmail
}
```

### Pattern 2: Flatten Hierarchy

```utlx
{
  orderId: $input.Order.@id,
  customerName: $input.Order.Customer.Name,
  customerEmail: $input.Order.Customer.Email
}
```

### Pattern 3: Group and Aggregate

```utlx
input.items 
  |> groupBy(item => item.category)
  |> map((cat, items) => {
      category: cat,
      count: count(items),
      total: sum(items.*.price)
    })
```

### Pattern 4: Add Computed Fields

```utlx
input.items |> map(item => {
  ...item,
  subtotal: item.price * item.quantity,
  taxAmount: item.price * item.quantity * 0.08
})
```

---

## Running the Examples

Save each script as a `.utlx` file and run:

```bash
# Transform XML to JSON
utlx transform $input.xml transform.utlx

# Save output to file
utlx transform $input.xml transform.utlx -o output.json

# Pretty print JSON
utlx transform $input.xml transform.utlx --pretty
```

---

## Next Steps

- 📖 [JSON to XML Examples](json-to-xml.md)
- 🔧 [Function Reference](../reference/stdlib-reference.md)
- 💡 [More Examples](cookbook.md)
- 📚 [Language Guide](../language-guide/)

---

**Have questions?** Ask in [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
