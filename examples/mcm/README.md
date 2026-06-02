# Message Contract Mode (MCM) examples

Realistic **schema → schema** fixtures for testing/demoing Message Contract Mode, where
the IDE maps **input schema(s)** to a **predefined output schema** (design-time; runtime
is instance → instance). Each example is an input-schema + output-schema pair (plus a
sample instance for later runtime testing), chosen to exercise the **contract coverage**
analysis (IF11): is there a source for every required output field, and what's the
**delta** of what's missing?

All schemas are **JSON Schema** (draft-07), which the IDE loads as format `jsch`.

## The examples

| # | Domain     | Input → Output                              | Coverage | Demonstrates |
|---|------------|---------------------------------------------|----------|--------------|
| [01](01-ecommerce-order-ack/) | E-commerce | ShopOrder → OrderAcknowledgement | **CLEAN** (empty delta) | a contract fully satisfiable from the input; direct + fuzzy/flatten matches |
| [02](02-payment-advice/)      | Finance    | PaymentInstruction → PaymentAdvice | **GAP** (5 missing) | enrichment gaps → currency lookup, BIC lookups, derived value date, default charge-bearer |
| [03](03-insurance-claim/)     | Healthcare | PatientAdmission → InsuranceClaim | **GAP** (8 of 12) | the source has demographics only → eligibility lookup, provider registry, ICD coding, charges |

Each sub-folder's `README.md` has the per-field expected coverage table and the delta.

## How to use in the IDE

1. Switch the toolbar to **📋 Message Contract Mode**.
2. **Input panel** → add an input → **Schema** tab → paste/load the example's
   `input.*.schema.json`, format **JSON Schema (jsch)**.
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
