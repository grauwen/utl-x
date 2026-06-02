# 10 — 3 mixed-format inputs → XML/XSD output: Logistics (CLEAN / SMALL / LARGE)

**Formats exercised:** input1 **XML (xsd)**, input2 **CSV (tsch)**, input3 **JSON (jsch)**;
output **XML (xsd)**. One shared input set, three output contracts of escalating gap.

- **Inputs (shared by all three outputs):**
  - `input1.shipment.xsd` (xml) — Shipment header · sample `sample.shipment.xml`
  - `input2.packages.tsch.json` (csv) — Package lines · sample `sample.packages.csv`
  - `input3.customer.schema.json` (json) — Consignee · sample `sample.customer.json`
- **Outputs:** `output.clean.*` · `output.small-gap.*` · `output.large-gap.*`

## Verified coverage

**CLEAN** — `output.clean.shipping-manifest.xsd` → **9 direct, delta none**
(`shipmentId/origin/destination/carrier/shipDate` ← Shipment; `customerName/email` ← Customer;
`packageId/weightKg` ← Packages).

**SMALL GAP** — `output.small-gap.shipping-manifest.xsd` → **9 direct, 2 gap**
delta: `trackingNumber`, `estimatedDelivery` (assigned later — no source).

**LARGE GAP** — `output.large-gap.customs-declaration.xsd` → **4 direct, 1 derivable, 9 gap**
- direct: `shipmentId`, `customerName`, `destination`, `carrier`
- derivable (fuzzy — review): `countryOfOrigin` ← `Shipment.origin` *(note: origin = ship-from,
  not goods origin — a deliberate example of a fuzzy match that needs human review)*
- delta (9): `exporterEORI, importerEORI, hsCode, incoterms, dutyAmount, vatAmount,
  customsProcedureCode, grossWeight, netWeight`

> Demonstrates coverage across **three different schema formats at once** (xsd + tsch +
> jsch) feeding a single **xsd** contract, plus a fuzzy match worth reviewing.
