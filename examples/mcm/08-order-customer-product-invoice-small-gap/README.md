# 08 — 3 inputs → 1 output: Order + Customer + Product → Invoice w/ Payment (SMALL GAP)

**Scenario.** Same 3-way invoice join as 07, but the target also carries an
**invoice number** (assigned from a numbering series) and a **due date** (derived from
the order date + payment terms). Both are absent from the inputs — a **small gap**.

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`, `input3.product.schema.json`
- **Output:** `output.invoice-with-payment.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`, `sample.product.json`

## Expected coverage

| Target field    | Status    | Source / why |
|-----------------|-----------|--------------|
| `invoiceNumber` | ✗ **gap** | generated (numbering series) |
| `dueDate`       | ✗ **gap** | derived from `orderDate` + terms |
| `orderId`       | ✓ direct  | Order.orderId |
| `orderDate`     | ✓ direct  | Order.orderDate |
| `customerName`  | ✓ direct  | Customer.customerName |
| `vatNumber`     | ✓ direct  | Customer.vatNumber |
| `billingStreet` / `billingCity` / `billingCountry` | ✓ direct | Customer |
| `currency`      | ✓ direct  | ProductCatalog.currency |
| `lines[].*`     | ✓ direct  | Order + ProductCatalog |

**Delta (required, no source): `invoiceNumber, dueDate`** — one generated value and one
derivation/default.
