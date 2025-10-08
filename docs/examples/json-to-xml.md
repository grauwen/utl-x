# JSON to XML Examples

Practical examples of transforming JSON to XML using UTL-X.

---

## Example 1: Simple Object

### Input JSON

```json
{
  "orderId": "12345",
  "customer": "Alice Johnson",
  "total": 150.00
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: input.orderId,
    Customer: input.customer,
    Total: input.total
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Order id="12345">
  <Customer>Alice Johnson</Customer>
  <Total>150.00</Total>
</Order>
```

**Key points:**
- `@id` creates an XML attribute
- Properties become XML elements
- Numeric values converted to strings in XML

---

## Example 2: Nested Objects

### Input JSON

```json
{
  "order": {
    "id": "67890",
    "date": "2026-01-15",
    "customer": {
      "name": "Bob Smith",
      "email": "bob@example.com",
      "address": {
        "street": "456 Oak Ave",
        "city": "Portland",
        "zip": "97201"
      }
    }
  }
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Order: {
    @id: input.order.id,
    @date: input.order.date,
    Customer: {
      Name: input.order.customer.name,
      Email: input.order.customer.email,
      Address: {
        Street: input.order.customer.address.street,
        City: input.order.customer.address.city,
        Zip: input.order.customer.address.zip
      }
    }
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Order id="67890" date="2026-01-15">
  <Customer>
    <n>Bob Smith</n>
    <Email>bob@example.com</Email>
    <Address>
      <Street>456 Oak Ave</Street>
      <City>Portland</City>
      <Zip>97201</Zip>
    </Address>
  </Customer>
</Order>
```

---

## Example 3: Arrays to Repeated Elements

### Input JSON

```json
{
  "products": [
    {
      "id": "P001",
      "name": "Widget",
      "price": 25.00
    },
    {
      "id": "P002",
      "name": "Gadget",
      "price": 50.00
    },
    {
      "id": "P003",
      "name": "Doohickey",
      "price": 15.00
    }
  ]
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Products: {
    Product: input.products |> map(p => {
      @id: p.id,
      Name: p.name,
      Price: p.price
    })
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Products>
  <Product id="P001">
    <n>Widget</n>
    <Price>25.00</Price>
  </Product>
  <Product id="P002">
    <n>Gadget</n>
    <Price>50.00</Price>
  </Product>
  <Product id="P003">
    <n>Doohickey</n>
    <Price>15.00</Price>
  </Product>
</Products>
```

**Key points:**
- JSON arrays become repeated XML elements
- Use `map` to transform each array element
- Element name (`Product`) is singular, wraps each item

---

## Example 4: Mixed Content

### Input JSON

```json
{
  "article": {
    "title": "Understanding UTL-X",
    "author": "Alice Johnson",
    "published": "2026-01-15",
    "tags": ["tutorial", "programming", "data"],
    "content": {
      "paragraphs": [
        "UTL-X is a powerful transformation language.",
        "It works with multiple data formats.",
        "Learn more by trying the examples."
      ]
    }
  }
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Article: {
    @published: input.article.published,
    Title: input.article.title,
    Author: input.article.author,
    Tags: {
      Tag: input.article.tags
    },
    Content: {
      Paragraph: input.article.content.paragraphs
    }
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Article published="2026-01-15">
  <Title>Understanding UTL-X</Title>
  <Author>Alice Johnson</Author>
  <Tags>
    <Tag>tutorial</Tag>
    <Tag>programming</Tag>
    <Tag>data</Tag>
  </Tags>
  <Content>
    <Paragraph>UTL-X is a powerful transformation language.</Paragraph>
    <Paragraph>It works with multiple data formats.</Paragraph>
    <Paragraph>Learn more by trying the examples.</Paragraph>
  </Content>
</Article>
```

---

## Example 5: Attributes vs Elements

### Input JSON

```json
{
  "book": {
    "isbn": "978-0-123456-78-9",
    "title": "Learning UTL-X",
    "year": 2026,
    "author": {
      "name": "Jane Doe",
      "country": "USA"
    },
    "price": {
      "amount": 39.99,
      "currency": "USD"
    }
  }
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Book: {
    @isbn: input.book.isbn,
    @year: input.book.year,
    Title: input.book.title,
    Author: {
      @country: input.book.author.country,
      _text: input.book.author.name
    },
    Price: {
      @currency: input.book.price.currency,
      _text: input.book.price.amount
    }
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Book isbn="978-0-123456-78-9" year="2026">
  <Title>Learning UTL-X</Title>
  <Author country="USA">Jane Doe</Author>
  <Price currency="USD">39.99</Price>
</Book>
```

**Key points:**
- Use `@property` for XML attributes
- Use `_text` for element text content (when element also has attributes)
- Combines attributes and text in single element

---

## Example 6: Conditional XML Generation

### Input JSON

```json
{
  "orders": [
    {"id": "001", "status": "pending", "total": 150, "express": true},
    {"id": "002", "status": "complete", "total": 75, "express": false},
    {"id": "003", "status": "cancelled", "total": 200, "express": false}
  ]
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  Orders: {
    Order: input.orders |> map(order => {
      @id: order.id,
      @status: order.status,
      @express: if (order.express) "yes" else "no",
      Total: order.total,
      
      // Conditional elements
      ...(if (order.status == "complete") {
        CompletedDate: "2026-01-15"
      } else {}),
      
      ...(if (order.status == "cancelled") {
        CancelReason: "Customer request"
      } else {})
    })
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Orders>
  <Order id="001" status="pending" express="yes">
    <Total>150</Total>
  </Order>
  <Order id="002" status="complete" express="no">
    <Total>75</Total>
    <CompletedDate>2026-01-15</CompletedDate>
  </Order>
  <Order id="003" status="cancelled" express="no">
    <Total>200</Total>
    <CancelReason>Customer request</CancelReason>
  </Order>
</Orders>
```

---

## Example 7: Namespaces

### Input JSON

```json
{
  "envelope": {
    "header": {
      "messageId": "MSG-12345",
      "timestamp": "2026-01-15T10:30:00Z"
    },
    "body": {
      "data": {
        "userId": "U001",
        "action": "login"
      }
    }
  }
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml {
  namespaces: {
    "soap": "http://schemas.xmlsoap.org/soap/envelope/",
    "app": "http://example.com/application"
  }
}
---
{
  "soap:Envelope": {
    @xmlns:soap: "http://schemas.xmlsoap.org/soap/envelope/",
    @xmlns:app: "http://example.com/application",
    
    "soap:Header": {
      "app:MessageId": input.envelope.header.messageId,
      "app:Timestamp": input.envelope.header.timestamp
    },
    
    "soap:Body": {
      "app:Data": {
        @userId: input.envelope.body.data.userId,
        "app:Action": input.envelope.body.data.action
      }
    }
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope 
    xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
    xmlns:app="http://example.com/application">
  <soap:Header>
    <app:MessageId>MSG-12345</app:MessageId>
    <app:Timestamp>2026-01-15T10:30:00Z</app:Timestamp>
  </soap:Header>
  <soap:Body>
    <app:Data userId="U001">
      <app:Action>login</app:Action>
    </app:Data>
  </soap:Body>
</soap:Envelope>
```

---

## Example 8: Grouping and Aggregation

### Input JSON

```json
{
  "sales": [
    {"region": "North", "product": "Widget", "amount": 1000},
    {"region": "South", "product": "Gadget", "amount": 1500},
    {"region": "North", "product": "Gadget", "amount": 800},
    {"region": "East", "product": "Widget", "amount": 1200},
    {"region": "South", "product": "Widget", "amount": 900}
  ]
}
```

### UTL-X Script

```utlx
%utlx 1.0
input json
output xml
---
{
  SalesReport: {
    Summary: {
      TotalSales: sum(input.sales.*.amount),
      TransactionCount: count(input.sales)
    },
    
    ByRegion: {
      Region: input.sales 
        |> groupBy(sale => sale.region)
        |> map((region, sales) => {
            @name: region,
            Total: sum(sales.*.amount),
            Count: count(sales),
            Sale: sales |> map(s => {
              @product: s.product,
              Amount: s.amount
            })
          })
    }
  }
}
```

### Output XML

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SalesReport>
  <Summary>
    <TotalSales>5400</TotalSales>
    <TransactionCount>5</TransactionCount>
  </Summary>
  <ByRegion>
    <Region name="North">
      <Total>1800</Total>
      <Count>2</Count>
      <Sale product="Widget">
        <Amount>1000</Amount>
      </Sale>
      <Sale product="Gadget">
        <Amount>800</Amount>
      </Sale>
    </Region>
    <Region name="South">
      <Total>2400</Total>
      <Count>2</Count>
      <Sale product="Gadget">
        <Amount>1500</Amount>
      </Sale>
      <Sale product="Widget">
        <Amount>900</Amount>
      </Sale>
    </Region>
    <Region name="East">
      <Total>1200</Total>
      <Count>1</Count>
      <Sale product="Widget">
        <Amount>1200</Amount>
      </Sale>
    </Region>
  </ByRegion>
</SalesReport>
```

---

## Common Patterns

### Pattern 1: Simple Property Mapping

```utlx
// JSON property → XML element
{
  ElementName: input.jsonProperty
}
```

### Pattern 2: Property to Attribute

```utlx
// JSON property → XML attribute
{
  Element: {
    @attributeName: input.jsonProperty
  }
}
```

### Pattern 3: Array to Repeated Elements

```utlx
// JSON array → repeated XML elements
{
  Wrapper: {
    Item: input.jsonArray
  }
}
```

### Pattern 4: Nested Object to Nested Elements

```utlx
// Nested JSON → nested XML
{
  Parent: {
    Child: {
      GrandChild: input.nested.deep.value
    }
  }
}
```

### Pattern 5: Conditional Elements

```utlx
// Include element only if condition met
{
  Root: {
    ...(if (condition) {
      ConditionalElement: value
    } else {})
  }
}
```

---

## Best Practices

### 1. Use Meaningful Element Names

```utlx
// ❌ Bad
{
  d: input.data
}

// ✅ Good
{
  Data: input.data
}
```

### 2. Attributes for Metadata, Elements for Data

```utlx
// ✅ Good practice
{
  Product: {
    @id: input.id,          // Metadata: attribute
    @sku: input.sku,        // Metadata: attribute
    Name: input.name,       // Data: element
    Price: input.price      // Data: element
  }
}
```

### 3. Consistent Naming Convention

```utlx
// Choose either PascalCase or camelCase and stick with it
{
  Order: {                  // PascalCase (recommended for XML)
    OrderId: input.orderId,
    CustomerName: input.customerName
  }
}
```

### 4. Handle Missing Data

```utlx
{
  Customer: {
    Name: input.customer.name || "Unknown",
    Email: input.customer.email || ""
  }
}
```

---

## Troubleshooting

### Issue: Invalid XML Characters

**Problem:** JSON contains characters invalid in XML

**Solution:** Use escaping functions

```utlx
{
  Description: escape(input.description)
}
```

### Issue: Empty Elements

**Problem:** Empty elements appearing in XML

**Solution:** Filter them out

```utlx
{
  Item: input.items 
    |> filter(item => item.name != null && item.name != "")
    |> map(item => {
        Name: item.name
      })
}
```

### Issue: Numeric Type Conversion

**Problem:** Numbers become strings in XML

**Note:** This is expected - XML is text-based. Convert back when reading:

```utlx
// When reading XML back
{
  price: parseNumber(input.Product.Price)
}
```

---

## Running Examples

Save the script and run:

```bash
# Transform JSON to XML
utlx transform input.json transform.utlx

# Save to file
utlx transform input.json transform.utlx -o output.xml

# Pretty print
utlx transform input.json transform.utlx --pretty
```

---

## Next Steps

- 📖 [XML to JSON Examples](xml-to-json.md) - Reverse transformations
- 🔧 [Common Patterns](cookbook.md) - Transformation cookbook
- 📚 [Selectors Guide](../language-guide/selectors.md) - Master selectors
- 💡 [More Examples](real-world-use-cases.md) - Production examples

---

**Questions?** Ask in [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
