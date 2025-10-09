# Working with YAML (v1.1+)

YAML support is planned for version 1.1.

## Basic YAML Transformation

### Input YAML

```yaml
order:
  id: ORD-001
  date: 2025-10-09
  customer:
    name: Alice Johnson
    email: alice@example.com
  items:
    - sku: WIDGET-A
      quantity: 2
      price: 29.99
    - sku: GADGET-B
      quantity: 1
      price: 149.99
```

### UTL-X Transformation

```utlx
%utlx 1.0
input yaml
output json
---
{
  orderId: input.order.id,
  customerName: input.order.customer.name,
  total: sum(input.order.items.(quantity * price))
}
```

## YAML Features

### Accessing YAML Data

Same as JSON:

```utlx
input.order.customer.name
input.order.items[0].sku
input.order.items.*.price
```

### YAML to JSON/XML

Identical to JSON transformations.

### JSON/XML to YAML

```utlx
%utlx 1.0
input json
output yaml
---
{
  order: {
    id: input.orderId,
    customer: input.customerName
  }
}
```

## YAML Configuration

```utlx
input yaml {
  strict: true,
  version: "1.2"
}

output yaml {
  indent: 2,
  flowLevel: 2
}
```

---
