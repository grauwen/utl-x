# Working with XML

This guide covers XML-specific features in UTL-X.

## Basic XML Transformation

### Input XML

```xml
<?xml version="1.0"?>
<Order id="ORD-001" date="2025-10-09">
  <Customer>
    <Name>Alice Johnson</Name>
    <Email>alice@example.com</Email>
  </Customer>
  <Items>
    <Item sku="WIDGET-A" quantity="2" price="29.99"/>
    <Item sku="GADGET-B" quantity="1" price="149.99"/>
  </Items>
</Order>
```

### UTL-X Transformation

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: input.Order.@id,
  orderDate: input.Order.@date,
  customer: {
    name: input.Order.Customer.Name,
    email: input.Order.Customer.Email
  },
  items: input.Order.Items.Item |> map(item => {
    sku: item.@sku,
    quantity: parseNumber(item.@quantity),
    price: parseNumber(item.@price)
  })
}
```

### Output JSON

```json
{
  "orderId": "ORD-001",
  "orderDate": "2025-10-09",
  "customer": {
    "name": "Alice Johnson",
    "email": "alice@example.com"
  },
  "items": [
    {"sku": "WIDGET-A", "quantity": 2, "price": 29.99},
    {"sku": "GADGET-B", "quantity": 1, "price": 149.99}
  ]
}
```

## XML Elements

### Accessing Elements

```utlx
input.Order                      // Root element
input.Order.Customer             // Child element
input.Order.Customer.Name        // Nested element
```

### Multiple Elements

```utlx
input.Order.Items.Item           // All Item elements as array
input.Order.Items.Item[*]        // Explicitly as array
input.Order.Items.Item[0]        // First Item
```

## XML Attributes

### Accessing Attributes

Use `@` prefix:

```utlx
input.Order.@id                  // Attribute "id"
input.Order.@date                // Attribute "date"
input.Order.Items.Item.@sku      // All sku attributes
```

### Attributes vs Elements

**XML:**
```xml
<Product id="123">
  <Name>Widget</Name>
</Product>
```

**UTL-X:**
```utlx
{
  id: input.Product.@id,         // Attribute
  name: input.Product.Name       // Element
}
```

## XML Namespaces

### Declaring Namespaces

```utlx
%utlx 1.0
input xml {
  namespaces: {
    "ord": "http://example.com/orders",
    "cust": "http://example.com/customers"
  }
}
output json
---
```

### Using Namespaced Elements

```xml
<ord:Order xmlns:ord="http://example.com/orders"
           xmlns:cust="http://example.com/customers">
  <cust:Customer>
    <cust:Name>Alice</cust:Name>
  </cust:Customer>
</ord:Order>
```

**UTL-X:**
```utlx
{
  customer: {
    name: input.{"ord:Order"}.{"cust:Customer"}.{"cust:Name"}
  }
}
```

### Default Namespace

```utlx
input xml {
  namespaces: {
    "": "http://example.com/default"
  }
}
```

## XML Text Content

### Simple Text

```xml
<Name>Alice Johnson</Name>
```

```utlx
input.Name                       // "Alice Johnson"
```

### Mixed Content

```xml
<Description>
  This is <b>bold</b> text.
</Description>
```

```utlx
input.Description                // Gets text content
input.Description.*              // Gets all nodes (text + elements)
```

## CDATA Sections

```xml
<Script><![CDATA[
  function test() {
    return x < 5 && y > 3;
  }
]]></Script>
```

```utlx
input.Script                     // Returns content without CDATA wrapper
```

## XML Comments

XML comments are ignored by default:

```xml
<Order>
  <!-- This is a comment -->
  <Customer>Alice</Customer>
</Order>
```

## Empty Elements

```xml
<Item name="Widget"/>
<Item name="Gadget"></Item>
```

Both are equivalent:

```utlx
input.Items.Item.@name           // Works for both styles
```

## XML to JSON

### Simple Mapping

**XML:**
```xml
<Person>
  <Name>Alice</Name>
  <Age>30</Age>
  <Active>true</Active>
</Person>
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output json
---
{
  name: input.Person.Name,
  age: parseNumber(input.Person.Age),
  active: input.Person.Active == "true"
}
```

**JSON:**
```json
{
  "name": "Alice",
  "age": 30,
  "active": true
}
```

### Arrays

**XML:**
```xml
<Products>
  <Product id="1">Widget</Product>
  <Product id="2">Gadget</Product>
  <Product id="3">Tool</Product>
</Products>
```

**UTL-X:**
```utlx
{
  products: input.Products.Product |> map(p => {
    id: p.@id,
    name: p
  })
}
```

**JSON:**
```json
{
  "products": [
    {"id": "1", "name": "Widget"},
    {"id": "2", "name": "Gadget"},
    {"id": "3", "name": "Tool"}
  ]
}
```

## JSON to XML

### Simple Mapping

**JSON:**
```json
{
  "person": {
    "name": "Alice",
    "age": 30
  }
}
```

**UTL-X:**
```utlx
%utlx 1.0
input json
output xml
---
{
  Person: {
    Name: input.person.name,
    Age: input.person.age
  }
}
```

**XML:**
```xml
<?xml version="1.0"?>
<Person>
  <Name>Alice</Name>
  <Age>30</Age>
</Person>
```

### Attributes in Output

Use `@` prefix:

```utlx
{
  Product: {
    @id: "123",
    @category: "Electronics",
    Name: "Widget"
  }
}
```

**Output:**
```xml
<Product id="123" category="Electronics">
  <Name>Widget</Name>
</Product>
```

### Arrays to Elements

```utlx
{
  Products: {
    Product: input.items |> map(item => {
      @id: item.id,
      Name: item.name
    })
  }
}
```

**Output:**
```xml
<Products>
  <Product id="1"><Name>Widget</Name></Product>
  <Product id="2"><Name>Gadget</Name></Product>
</Products>
```

## XML Configuration Options

```utlx
%utlx 1.0
input xml {
  namespaces: {
    "ns": "http://example.com"
  },
  preserveWhitespace: false,
  ignoreComments: true
}
output xml {
  pretty: true,
  indent: 2,
  xmlDeclaration: true,
  encoding: "UTF-8"
}
```

### Input Options

- **namespaces**: Namespace prefix mappings
- **preserveWhitespace**: Keep whitespace (default: false)
- **ignoreComments**: Skip comments (default: true)
- **validateSchema**: Validate against XSD (v1.1+)

### Output Options

- **pretty**: Pretty-print output (default: false)
- **indent**: Indentation spaces (default: 2)
- **xmlDeclaration**: Include `<?xml?>` (default: true)
- **encoding**: Character encoding (default: "UTF-8")
- **namespaces**: Output namespace declarations

## Template Matching with XML

```utlx
%utlx 1.0
input xml
output json
---

template match="Order" {
  {
    id: @id,
    customer: apply(Customer),
    items: apply(Items/Item)
  }
}

template match="Customer" {
  {
    name: Name,
    email: Email
  }
}

template match="Item" {
  {
    sku: @sku,
    quantity: parseNumber(@quantity),
    price: parseNumber(@price)
  }
}
```

## XPath-Like Selectors

UTL-X selectors are similar to XPath:

| XPath | UTL-X |
|-------|-------|
| `/Order` | `input.Order` |
| `/Order/@id` | `input.Order.@id` |
| `/Order/Items/Item` | `input.Order.Items.Item` |
| `/Order/Items/Item[1]` | `input.Order.Items.Item[0]` |
| `/Order/Items/Item[@price>100]` | `input.Order.Items.Item[price>100]` |
| `//ProductCode` | `input..ProductCode` |

## Common XML Patterns

### Handle Optional Elements

```utlx
{
  name: input.Person.Name,
  email: input.Person.Email ?? "no-email@example.com",
  phone: input.Person.Phone ?? null
}
```

### Flatten Nested Structure

```xml
<Order>
  <Shipping>
    <Address>
      <City>Seattle</City>
      <State>WA</State>
    </Address>
  </Shipping>
</Order>
```

```utlx
{
  city: input.Order.Shipping.Address.City,
  state: input.Order.Shipping.Address.State
}
```

### Group by Attribute

```xml
<Items>
  <Item category="Electronics" name="Widget"/>
  <Item category="Electronics" name="Gadget"/>
  <Item category="Tools" name="Hammer"/>
</Items>
```

```utlx
{
  grouped: input.Items.Item 
    |> groupBy(item => item.@category)
    |> entries()
    |> map(([category, items]) => {
         category: category,
         items: items |> map(i => i.@name)
       })
}
```

## Best Practices

### 1. Parse Numeric Attributes

```utlx
// ✅ Good
quantity: parseNumber(item.@quantity)

// ❌ Bad - stays as string
quantity: item.@quantity
```

### 2. Handle Namespaces

```utlx
// ✅ Good - declare namespaces
input xml {
  namespaces: {"ns": "http://example.com"}
}

// ❌ Bad - undeclared namespace errors
```

### 3. Use Safe Navigation

```utlx
// ✅ Good
city: input.Order.Shipping?.Address?.City ?? "Unknown"

// ❌ Bad - might crash
city: input.Order.Shipping.Address.City
```

### 4. Validate Input

```utlx
function processOrder(order: Object): Object {
  if (order.@id == null) {
    return {error: "Missing order ID"}
  }
  
  // Process order...
}
```

---
