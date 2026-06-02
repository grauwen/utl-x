# 05 — 2 inputs → 1 output: Order + Customer → Shipping Instruction (SMALL GAP)

**Scenario.** A warehouse shipping instruction is built from `Order` + `Customer`.
Address and order detail are fully covered, but the **carrier** and **warehouse**
routing are decided elsewhere — a **small gap** (2 required fields with no source).

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`
- **Output:** `output.shipping-instruction.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`

## Expected coverage

| Target field     | Status    | Source / why |
|------------------|-----------|--------------|
| `orderId`        | ✓ direct  | Order.orderId |
| `orderDate`      | ✓ direct  | Order.orderDate |
| `customerName`   | ✓ direct  | Customer.customerName |
| `billingStreet`  | ✓ direct  | Customer.billingStreet |
| `billingCity`    | ✓ direct  | Customer.billingCity |
| `billingCountry` | ✓ direct  | Customer.billingCountry |
| `totalAmount`    | ✓ direct  | Order.totalAmount |
| `carrierCode`    | ✗ **gap** | routing decision / carrier lookup |
| `warehouseCode`  | ✗ **gap** | fulfilment-rules lookup |
| `lines[].*`      | ✓ direct  | Order.lines[] |

**Delta (required, no source): `carrierCode, warehouseCode`** — 2 fields needing a
routing/fulfilment lookup or default.
