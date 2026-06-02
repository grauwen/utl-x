# 07 — 3 inputs → 1 output: Order + Customer + Product → Invoice (CLEAN)

**Scenario.** An invoice is assembled by joining three sources: `Order` (what/how many),
`Customer` (bill-to + VAT), and the `ProductCatalog` (names, prices, tax). Every required
output field is sourced from one of the three — **CLEAN** coverage (empty delta). The
classic 3-way join behind invoice generation.

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`, `input3.product.schema.json`
- **Output:** `output.invoice.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`, `sample.product.json`

## Expected coverage

| Target field          | Status   | Source |
|-----------------------|----------|--------|
| `orderId`             | ✓ direct | Order.orderId |
| `orderDate`           | ✓ direct | Order.orderDate |
| `customerId`          | ✓ direct | Order.customerId |
| `customerName`        | ✓ direct | Customer.customerName |
| `vatNumber`           | ✓ direct | Customer.vatNumber |
| `billingStreet`       | ✓ direct | Customer.billingStreet |
| `billingCity`         | ✓ direct | Customer.billingCity |
| `billingCountry`      | ✓ direct | Customer.billingCountry |
| `currency`            | ✓ direct | ProductCatalog.currency |
| `lines[].productId`   | ✓ direct | Order.lines[].productId |
| `lines[].quantity`    | ✓ direct | Order.lines[].quantity |
| `lines[].productName` | ✓ direct | ProductCatalog.productName |
| `lines[].unitPrice`   | ✓ direct | ProductCatalog.unitPrice |
| `lines[].taxRate`     | ✓ direct | ProductCatalog.taxRate |

**Delta: none.** The three inputs together fully satisfy the invoice contract.
