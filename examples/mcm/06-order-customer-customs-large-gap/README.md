# 06 — 2 inputs → 1 output: Order + Customer → Customs Declaration (LARGE GAP)

**Scenario.** An export `CustomsDeclaration` is required, but the only sources at hand
are `Order` + `Customer`. They cover identity and value; everything customs actually
needs — EORI numbers, HS/origin classification, Incoterms, calculated duty/VAT, weights,
procedure code — is **absent**. A **large gap**: the contract cannot be produced without
substantial enrichment from product, trade-party, logistics and tariff systems.

- **Inputs:** `input1.order.schema.json`, `input2.customer.schema.json`
- **Output:** `output.customs-declaration.schema.json`
- **Samples:** `sample.order.json`, `sample.customer.json`

## Expected coverage

| Target field           | Status    | Source / why |
|------------------------|-----------|--------------|
| `orderId`              | ✓ direct  | Order.orderId |
| `customerName`         | ✓ direct  | Customer.customerName |
| `billingCountry`       | ✓ direct  | Customer.billingCountry |
| `totalAmount`          | ✓ direct  | Order.totalAmount |
| `exporterEORI`         | ✗ **gap** | company master |
| `importerEORI`         | ✗ **gap** | trade-party lookup |
| `hsCode`               | ✗ **gap** | product classification |
| `countryOfOrigin`      | ✗ **gap** | product master |
| `incoterms`            | ✗ **gap** | contract terms |
| `dutyAmount`           | ✗ **gap** | calculated |
| `vatAmount`            | ✗ **gap** | calculated |
| `customsProcedureCode` | ✗ **gap** | default / ruleset |
| `grossWeight`          | ✗ **gap** | logistics |
| `netWeight`            | ✗ **gap** | logistics |

**Delta: 10 of 14 required fields** — `exporterEORI, importerEORI, hsCode,
countryOfOrigin, incoterms, dutyAmount, vatAmount, customsProcedureCode, grossWeight,
netWeight`.
