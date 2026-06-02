# 04 — 2 inputs → 1 output: Order + Customer → Sales Confirmation (CLEAN)

**Scenario.** A confirmation is built by **joining** an `Order` with the `Customer`
master on `customerId`. Between the two inputs, every required output field has a source
— **CLEAN** coverage (empty delta). This is the canonical 2-input join.

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`
- **Output:** `output.sales-confirmation.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`

## Expected coverage

| Target field     | Status   | Source |
|------------------|----------|--------|
| `orderId`        | ✓ direct | Order.orderId |
| `orderDate`      | ✓ direct | Order.orderDate |
| `customerId`     | ✓ direct | Order.customerId |
| `customerName`   | ✓ direct | Customer.customerName |
| `customerEmail`  | ✓ direct | Customer.customerEmail |
| `currency`       | ✓ direct | Order.currency |
| `totalAmount`    | ✓ direct | Order.totalAmount |
| `billingStreet`  | ✓ direct | Customer.billingStreet |
| `billingCity`    | ✓ direct | Customer.billingCity |
| `billingCountry` | ✓ direct | Customer.billingCountry |
| `lines[].*`      | ✓ direct | Order.lines[] |

**Delta: none.** Output is satisfied entirely from the two inputs combined.
