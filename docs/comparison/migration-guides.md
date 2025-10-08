# Migration Guides

Comprehensive guides for migrating to UTL-X from other transformation languages.

## From XSLT

### Step-by-Step Migration

#### 1. Convert Header

**XSLT:**
```xml
<?xml version="1.0"?>
<xsl:stylesheet version="1.0" 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
```

**UTL-X:**
```utlx
%utlx 1.0
input xml
output xml
---
```

#### 2. Convert Templates

**XSLT:**
```xml
<xsl:template match="/Order">
  <invoice>
    <id><xsl:value-of select="@id"/></id>
  </invoice>
</xsl:template>
```

**UTL-X:**
```utlx
template match="Order" {
  invoice: {
    id: @id
  }
}
```

#### 3. Convert XPath

| XSLT XPath | UTL-X |
|------------|-------|
| `Order/@id` | `Order.@id` |
| `Order/Customer/Name` | `Order.Customer.Name` |
| `Order/Items/Item` | `Order.Items.Item` |

### Automated Migration

```bash
utlx migrate xslt-file.xsl --output utlx-file.utlx
```

## From DataWeave

### Step-by-Step Migration

#### 1. Replace Header

**DataWeave:**
```dataweave
%dw 2.0
output application/json
```

**UTL-X:**
```utlx
%utlx 1.0
output json
```

#### 2. Replace `payload` with `input`

Find and replace throughout.

#### 3. Update Function Syntax

**DataWeave:**
```dataweave
fun double(x: Number): Number = x * 2
```

**UTL-X:**
```utlx
function double(x: Number): Number {
  x * 2
}
```

### Migration Tool

```bash
utlx migrate dataweave-file.dwl --output utlx-file.utlx
```

## From jq

### Conceptual Mapping

| jq | UTL-X |
|----|-------|
| `.field` | `input.field` |
| `.field[]` | `input.field[*]` |
| `map(.price)` | `|> map(item => item.price)` |
| `select(.price > 100)` | `|> filter(item => item.price > 100)` |

### Example Conversion

**jq:**
```jq
.orders[] | select(.total > 1000) | {id: .id, total: .total}
```

**UTL-X:**
```utlx
input.orders
  |> filter(order => order.total > 1000)
  |> map(order => {
       id: order.id,
       total: order.total
     })
```

## From JSONata

### Syntax Mapping

| JSONata | UTL-X |
|---------|-------|
| `Order.id` | `input.Order.id` |
| `$sum(items.price)` | `sum(input.items.*.price)` |
| `$map(items, function($i) {...})` | `items |> map(i => {...})` |

---
