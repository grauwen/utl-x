# Your First Transformation

Let's create your first UTL-X transformation! This tutorial will guide you through transforming XML data to JSON step by step.

---

## What You'll Learn

By the end of this tutorial, you'll know how to:
- ✅ Convert between formats instantly (no script needed)
- ✅ Write a basic UTL-X transformation script
- ✅ Transform XML to JSON
- ✅ Navigate input data
- ✅ Create output structures
- ✅ Run transformations from the command line

**Time:** 10-15 minutes

---

## Prerequisites

Before starting, make sure you have:
- ✅ UTL-X installed (see [Installation Guide](installation.md))
- ✅ A text editor (VS Code, IntelliJ, or any editor)
- ✅ Basic understanding of XML and JSON

---

## Step 0: Instant Format Conversion (No Script Needed)

Before writing any scripts, try UTL-X's identity mode — just pipe data and it converts automatically:

```bash
# XML to JSON (auto-detected)
echo '<person><name>Alice</name><age>30</age></person>' | utlx

# JSON to XML (auto-detected)
echo '{"greeting":"Hello World"}' | utlx

# Override with --to
echo '<data><value>42</value></data>' | utlx --to yaml
```

UTL-X detects the input format and flips to the opposite: XML becomes JSON, JSON becomes XML. Use `--to` to override.

Now let's write a proper transformation script for more control.

---

## Step 1: Create Your Input File

First, let's create a simple XML file with order data.

**Create a file named `order.xml`:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Order id="12345" date="2026-01-15">
  <Customer>
    <Name>Alice Johnson</Name>
    <Email>alice@example.com</Email>
  </Customer>
  <Items>
    <Item sku="WIDGET-001" quantity="2" price="25.00"/>
    <Item sku="GADGET-002" quantity="1" price="50.00"/>
  </Items>
</Order>
```

**What we have:**
- An `Order` with ID and date attributes
- Customer information (name and email)
- Two order items with SKU, quantity, and price

---

## Step 2: Create Your First Transformation

Now let's write a UTL-X script to transform this XML to JSON.

**Create a file named `transform.utlx`:**

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  orderDate: $input.Order.@date,
  customerName: $input.Order.Customer.Name,
  customerEmail: $input.Order.Customer.Email
}
```

**Let's break this down:**

### Line 1: Version Declaration
```utlx
%utlx 1.0
```
Every UTL-X script starts with the version declaration.

### Lines 2-3: Input/Output Directives
```utlx
input xml
output json
```
- `input xml` tells UTL-X to parse the input as XML
- `output json` tells UTL-X to produce JSON output

### Line 4: Separator
```utlx
---
```
Separates directives from the transformation body.

### Lines 5-10: Transformation Body
```utlx
{
  orderId: $input.Order.@id,
  orderDate: $input.Order.@date,
  customerName: $input.Order.Customer.Name,
  customerEmail: $input.Order.Customer.Email
}
```
Creates a JSON object with:
- `orderId` from the Order's `id` attribute (`@id`)
- `orderDate` from the Order's `date` attribute
- `customerName` from the Customer's Name element
- `customerEmail` from the Customer's Email element

---

## Step 3: Run the Transformation

Now let's run the transformation!

**Open your terminal and run:**

```bash
utlx transform transform.utlx order.xml
```

**You should see:**

```json
{
  "orderId": "12345",
  "orderDate": "2026-01-15",
  "customerName": "Alice Johnson",
  "customerEmail": "alice@example.com"
}
```

**🎉 Congratulations!** You just created your first UTL-X transformation!

---

## Step 4: Save Output to a File

To save the output to a file instead of printing to console:

```bash
utlx transform transform.utlx order.xml -o output.json
```

This creates an `output.json` file with the transformed data.

**Verify it worked:**

```bash
cat output.json
```

---

## Step 5: Add More Complex Logic

Let's enhance our transformation to include the order items.

**Update `transform.utlx`:**

```utlx
%utlx 1.0
input xml
output json
---
{
  orderId: $input.Order.@id,
  orderDate: $input.Order.@date,
  
  customer: {
    name: $input.Order.Customer.Name,
    email: $input.Order.Customer.Email
  },
  
  items: $input.Order.Items.Item |> map(item => {
    sku: item.@sku,
    quantity: parseNumber(item.@quantity),
    price: parseNumber(item.@price),
    subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
  }),
  
  total: sum(
    $input.Order.Items.Item |> map(item => 
      parseNumber(item.@quantity) * parseNumber(item.@price)
    )
  )
}
```

**What's new:**

1. **Nested customer object:**
   ```utlx
   customer: {
     name: $input.Order.Customer.Name,
     email: $input.Order.Customer.Email
   }
   ```

2. **Array transformation with `map`:**
   ```utlx
   items: $input.Order.Items.Item |> map(item => {
     sku: item.@sku,
     quantity: parseNumber(item.@quantity),
     price: parseNumber(item.@price),
     subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
   })
   ```
   - `|>` is the pipeline operator (chains operations)
   - `map` transforms each Item element
   - `parseNumber` converts strings to numbers

3. **Calculated total:**
   ```utlx
   total: sum(
     $input.Order.Items.Item |> map(item => 
       parseNumber(item.@quantity) * parseNumber(item.@price)
     )
   )
   ```
   Calculates the sum of all item subtotals.

**Run it again:**

```bash
utlx transform transform.utlx order.xml
```

**New output:**

```json
{
  "orderId": "12345",
  "orderDate": "2026-01-15",
  "customer": {
    "name": "Alice Johnson",
    "email": "alice@example.com"
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
      "price": 50.00,
      "subtotal": 50.00
    }
  ],
  "total": 100.00
}
```

---

## Step 6: Use Variables for Clarity

Let's make the transformation more readable using variables.

**Update `transform.utlx`:**

```utlx
%utlx 1.0
input xml
output json
---
{
  let order = $input.Order,
  let customer = order.Customer,
  let items = order.Items.Item,
  
  orderId: order.@id,
  orderDate: order.@date,
  
  customer: {
    name: customer.Name,
    email: customer.Email
  },
  
  items: items |> map(item => {
    sku: item.@sku,
    quantity: parseNumber(item.@quantity),
    price: parseNumber(item.@price),
    subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
  }),
  
  total: sum(items |> map(item => 
    parseNumber(item.@quantity) * parseNumber(item.@price)
  ))
}
```

**Benefits of using `let`:**
- ✅ More readable
- ✅ Avoid repeating long paths
- ✅ Easier to modify later

---

## Step 7: Add Conditional Logic

Let's add a discount for orders over $75.

**Update `transform.utlx`:**

```utlx
%utlx 1.0
input xml
output json
---
{
  let order = $input.Order,
  let items = order.Items.Item,
  let subtotal = sum(items |> map(item => 
    parseNumber(item.@quantity) * parseNumber(item.@price)
  )),
  
  orderId: order.@id,
  orderDate: order.@date,
  
  customer: {
    name: order.Customer.Name,
    email: order.Customer.Email
  },
  
  items: items |> map(item => {
    sku: item.@sku,
    quantity: parseNumber(item.@quantity),
    price: parseNumber(item.@price),
    subtotal: parseNumber(item.@quantity) * parseNumber(item.@price)
  }),
  
  pricing: {
    subtotal: subtotal,
    discount: if (subtotal > 75) subtotal * 0.10 else 0,
    total: if (subtotal > 75) subtotal * 0.90 else subtotal
  },
  
  message: if (subtotal > 75) 
            "Congratulations! You saved 10%!" 
          else 
            "Add $" + (75 - subtotal) + " more for 10% off"
}
```

**Run it:**

```bash
utlx transform transform.utlx order.xml
```

**Output:**

```json
{
  "orderId": "12345",
  "orderDate": "2026-01-15",
  "customer": {
    "name": "Alice Johnson",
    "email": "alice@example.com"
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
      "price": 50.00,
      "subtotal": 50.00
    }
  ],
  "pricing": {
    "subtotal": 100.00,
    "discount": 10.00,
    "total": 90.00
  },
  "message": "Congratulations! You saved 10%!"
}
```

---

## Common Mistakes to Avoid

### Mistake 1: Forgetting the Version Declaration

❌ **Wrong:**
```utlx
input xml
output json
---
```

✅ **Correct:**
```utlx
%utlx 1.0
input xml
output json
---
```

### Mistake 2: Missing the Separator

❌ **Wrong:**
```utlx
%utlx 1.0
input xml
output json
{
  result: $input.data
}
```

✅ **Correct:**
```utlx
%utlx 1.0
input xml
output json
---
{
  result: $input.data
}
```

### Mistake 3: Wrong Attribute Syntax

❌ **Wrong:**
```utlx
orderId: $input.Order.id  // Treats 'id' as an element
```

✅ **Correct:**
```utlx
orderId: $input.Order.@id  // Accesses 'id' attribute with @
```

### Mistake 4: Not Converting Strings to Numbers

❌ **Wrong:**
```utlx
total: item.@quantity * item.@price  // String * String = error
```

✅ **Correct:**
```utlx
total: parseNumber(item.@quantity) * parseNumber(item.@price)
```

---

## Quick Troubleshooting

### Problem: "Parse error" message

**Cause:** Syntax error in your UTL-X script

**Solution:**
- Check for missing commas
- Verify all brackets match: `{}`
- Ensure the `---` separator is present

### Problem: "undefined" or null values in output

**Cause:** Incorrect path or missing data

**Solution:**
- Verify the input path is correct
- Use `$input..ElementName` to search recursively
- Add default values: `$input.missing || "default"`

### Problem: Numbers appear as strings

**Cause:** XML attributes and text are strings by default

**Solution:**
- Use `parseNumber()` to convert: `parseNumber(item.@price)`

---

## Next Steps

Great job! You've created your first UTL-X transformation. Now you can:

1. **Learn core concepts:** [Basic Concepts](basic-concepts.md)
2. **Explore more examples:** [XML to JSON Examples](../examples/xml-to-json.md)
3. **Study the syntax:** [Quick Reference](quick-reference.md)
4. **Deep dive:** [Language Guide](../language-guide/overview.md)

---

## Practice Exercise

Try transforming this XML on your own:

**Input (`products.xml`):**
```xml
<Products>
  <Product id="1" category="Electronics">
    <Name>Laptop</Name>
    <Price>999.99</Price>
    <InStock>true</InStock>
  </Product>
  <Product id="2" category="Books">
    <Name>UTL-X Guide</Name>
    <Price>29.99</Price>
    <InStock>false</InStock>
  </Product>
</Products>
```

**Goal:** Create JSON output with:
- Array of products
- Convert price to number
- Convert InStock to boolean
- Add computed field: `onSale` (true if price < 50)

**Solution:** Try it yourself first, then check [examples](../examples/xml-to-json.md)!

---

## Summary

You learned:
- ✅ UTL-X document structure (`%utlx 1.0`, directives, `---`, body)
- ✅ Basic selectors (`$input.Element`, `.@attribute`)
- ✅ Creating JSON objects and arrays
- ✅ Using `map` to transform arrays
- ✅ Pipeline operator (`|>`)
- ✅ Conditional logic (`if-else`)
- ✅ Variables with `let`
- ✅ Built-in functions (`parseNumber`, `sum`)

**Keep experimenting and happy transforming!** 🚀

---

## Get Help

- 💬 [Ask questions in Discussions](https://github.com/grauwen/utl-x/discussions)
- 🐛 [Report issues](https://github.com/grauwen/utl-x/issues)
- 📧 [Email support](mailto:community@glomidco.com)
