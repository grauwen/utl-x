# Message Contract Mode (MCM) examples

Realistic **schema → schema** fixtures for testing/demoing Message Contract Mode, where
the IDE maps **input schema(s)** to a **predefined output schema** (design-time; runtime
is instance → instance). Each example is an input-schema + output-schema pair (plus a
sample instance for later runtime testing), chosen to exercise the **contract coverage**
analysis (IF11): is there a source for every required output field, and what's the
**delta** of what's missing?

All schemas are **JSON Schema** (draft-07), which the IDE loads as format `jsch`.

## The examples

### Single input → output (01–03)

| # | Domain     | Input → Output                              | Coverage | Demonstrates |
|---|------------|---------------------------------------------|----------|--------------|
| [01](01-ecommerce-order-ack/) | E-commerce | ShopOrder → OrderAcknowledgement | **CLEAN** (empty delta) | a contract fully satisfiable from the input; direct + fuzzy/flatten matches |
| [02](02-payment-advice/)      | Finance    | PaymentInstruction → PaymentAdvice | **GAP** (5 missing) | enrichment gaps → currency lookup, BIC lookups, derived value date, default charge-bearer |
| [03](03-insurance-claim/)     | Healthcare | PatientAdmission → InsuranceClaim | **GAP** (8 of 12) | the source has demographics only → eligibility lookup, provider registry, ICD coding, charges |

### Two inputs → output (04–06) — a join + escalating gaps

| # | Inputs → Output | Coverage | Demonstrates |
|---|-----------------|----------|--------------|
| [04](04-order-customer-confirmation-clean/)   | Order + Customer → SalesConfirmation | **CLEAN** | the canonical 2-input join (output drawn from both) |
| [05](05-order-customer-shipping-small-gap/)   | Order + Customer → ShippingInstruction | **SMALL GAP** (2) | `carrierCode`, `warehouseCode` → routing/fulfilment lookup |
| [06](06-order-customer-customs-large-gap/)    | Order + Customer → CustomsDeclaration | **LARGE GAP** (10 of 14) | EORI/HS/origin/incoterms/duty/weights all missing |

### Three inputs → output (07–09) — a 3-way join + escalating gaps

| # | Inputs → Output | Coverage | Demonstrates |
|---|-----------------|----------|--------------|
| [07](07-order-customer-product-invoice-clean/)      | Order + Customer + Product → Invoice | **CLEAN** | the 3-way join behind invoice generation |
| [08](08-order-customer-product-invoice-small-gap/)  | Order + Customer + Product → InvoiceWithPayment | **SMALL GAP** (2) | `invoiceNumber` (generated), `dueDate` (derived) |
| [09](09-order-customer-product-einvoice-large-gap/) | Order + Customer + Product → Peppol E-Invoice | **LARGE GAP** (11 of 20) | supplier identity, Peppol endpoints, payment/banking, FX, tax-exemption |

There is always **exactly one output**. Multi-input examples (04–09) are self-contained:
each folder carries its own copies of the input schemas + samples, so it loads
independently. Each sub-folder's `README.md` has the per-field expected coverage table
and the delta. All deltas above are **verified** against the coverage analyzer.

## How to use in the IDE

1. Switch the toolbar to **📋 Message Contract Mode**.
2. **Input panel** → add an input → **Schema** tab → paste/load each `input*.schema.json`,
   format **JSON Schema (jsch)**. For the multi-input examples (04–09), add one input per
   `input1…/input2…/input3…` file (the output draws from all of them combined).
3. **Output panel** → **Schema** tab → paste/load the example's `output.*.schema.json`.
4. Open **AI assist (✨ Generate UTLX)**. The dialog shows the **Contract coverage**
   panel: summary counts, the **delta** (required fields with no source), and an
   expandable per-field list. Expand it to see `✓ direct / ~ derivable / ✗ gap`.
5. For the GAP examples, click **"✨ Refine gaps (AI)"** to have the LLM resolve
   name-mismatched gaps semantically (e.g. `serviceDate ← admissionDate`), leaving only
   the genuinely unmappable fields in the delta.

> Coverage matches output **leaf** fields to input leaves by normalized name (exact,
> then fuzzy substring) + type. So renamed/nested fields (e.g. `customer.name` →
> `customerName`) show as **derivable**, and truly absent fields as **gaps** — exactly
> what the per-example tables predict.

## Files per example

```
NN-name/
  input.<source>.schema.json    # the source contract (load into an input's Schema tab)
  output.<target>.schema.json   # the target contract (load into the output Schema tab)
  sample.<source>.json          # a realistic instance (for runtime/round-trip testing)
  README.md                     # scenario + expected coverage table + delta
```

## Related

- `docs/features/IF11-ide-message-contract-ai-assist.md` — coverage analysis, gap
  refinement, archetype matrix, schema-based validation.
- `docs/features/IF08-ide-ai-assist-mode-aware.md` — mode-aware AI assist.
