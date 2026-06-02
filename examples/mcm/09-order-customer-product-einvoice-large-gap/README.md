# 09 — 3 inputs → 1 output: Order + Customer + Product → Peppol E-Invoice (LARGE GAP)

**Scenario.** The same operational 3-way join as 07/08 must now produce a **compliance
e-invoice** (Peppol BIS-style). The inputs cover the buyer side and line basics, but the
regulatory envelope — **supplier** identity/registration, **electronic addressing**
(Peppol endpoints), **payment means** + **banking**, **FX rate**, **tax-exemption
reason** — comes from company configuration, registries and services, not from the order
data. A **large gap**: most required fields are unmappable from these inputs.

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`, `input3.product.schema.json`
- **Output:** `output.einvoice.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`, `sample.product.json`

## Expected coverage

| Target field         | Status    | Source / why |
|----------------------|-----------|--------------|
| `orderId`            | ✓ direct  | Order.orderId |
| `customerName`       | ✓ direct  | Customer.customerName |
| `vatNumber`          | ✓ direct  | Customer.vatNumber |
| `billingStreet` / `billingCity` / `billingCountry` | ✓ direct | Customer |
| `currency`           | ✓ direct  | ProductCatalog.currency |
| `lines[].productId`  | ✓ direct  | Order.lines[].productId |
| `lines[].quantity`   | ✓ direct  | Order.lines[].quantity |
| `invoiceNumber`      | ✗ **gap** | numbering series |
| `issueDate`          | ✗ **gap** | derived |
| `supplierName`       | ✗ **gap** | seller (us) — not in inputs |
| `supplierTaxId`      | ✗ **gap** | company config |
| `supplierEndpointId` | ✗ **gap** | Peppol participant id (seller) |
| `buyerEndpointId`    | ✗ **gap** | Peppol registry |
| `paymentMeansCode`   | ✗ **gap** | config / default |
| `payeeIBAN`          | ✗ **gap** | company banking config |
| `payeeBIC`           | ✗ **gap** | company banking config |
| `exchangeRate`       | ✗ **gap** | rates service |
| `taxExemptionReason` | ✗ **gap** | tax-category dependent |

**Delta: 11 of 20 required fields** — `invoiceNumber, issueDate, supplierName,
supplierTaxId, supplierEndpointId, buyerEndpointId, paymentMeansCode, payeeIBAN,
payeeBIC, exchangeRate, taxExemptionReason`.
