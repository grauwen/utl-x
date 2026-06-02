# 02 — Finance: Payment Instruction → Payment Advice (GAP)

**Scenario.** An incoming SEPA-style `PaymentInstruction` must be mapped to a downstream
`PaymentAdvice`. The advice carries enrichment the instruction doesn't have — bank BICs,
the currency *name* (not just the code), a value date, and a charge-bearer code. So the
contract **cannot be fully satisfied from the input alone**: the gaps point to a lookup
table, a derivation, or a default.

- **Input schema:** `input.payment-instruction.schema.json`
- **Output schema:** `output.payment-advice.schema.json`
- **Sample instance:** `sample.payment-instruction.json`

## Expected coverage

| Target field     | Status        | Source / why |
|------------------|---------------|--------------|
| `paymentId`      | ✓ direct      | `paymentId` |
| `amount`         | ✓ direct      | `amount` |
| `currencyCode`   | ✓ direct      | `currencyCode` |
| `currencyName`   | ✗ **gap**     | only the ISO code exists → **currency lookup** |
| `debtorName`     | ✓ direct      | `debtorName` |
| `debtorIBAN`     | ✓ direct      | `debtorIBAN` |
| `debtorBIC`      | ✗ **gap**     | no BIC in source → **derive/lookup from IBAN** |
| `creditorName`   | ✓ direct      | `creditorName` |
| `creditorIBAN`   | ✓ direct      | `creditorIBAN` |
| `creditorBIC`    | ✗ **gap**     | **lookup from creditor IBAN** |
| `valueDate`      | ✗ **gap**     | **derivable** from `executionDate` (LLM refine) |
| `chargeBearer`   | ✗ **gap**     | no source → **default** (e.g. `SHAR`) |
| `remittanceInfo` | ✓ direct      | `remittanceInfo` |

**Delta (required, no source): `currencyName, debtorBIC, creditorBIC, valueDate, chargeBearer`.**

After **"✨ Refine gaps (AI)"** you'd expect `valueDate` to resolve to *derivable*
(`executionDate`), and the BIC/currencyName/chargeBearer gaps to stay flagged as needing
a lookup table / additional input / default — i.e. the real integration work.
