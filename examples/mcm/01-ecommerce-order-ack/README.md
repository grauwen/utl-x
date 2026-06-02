# 01 — E-commerce: Shop Order → Order Acknowledgement (CLEAN match)

**Scenario.** A web shop emits a `ShopOrder`; an order-management system requires an
`OrderAcknowledgement`. Both sides agreed on a near-canonical model, so the contract
is fully satisfiable from the input — **no gaps**.

- **Input schema:** `input.shop-order.schema.json`
- **Output schema:** `output.order-ack.schema.json`
- **Sample instance:** `sample.shop-order.json`

## Expected coverage

| Target field        | Status     | Source |
|---------------------|------------|--------|
| `orderId`           | ✓ direct   | `orderId` |
| `orderDate`         | ✓ direct   | `orderDate` |
| `customerName`      | ~ derivable| `customer.name` (fuzzy/flatten) |
| `customerEmail`     | ~ derivable| `customer.email` (fuzzy/flatten) |
| `currency`          | ✓ direct   | `currency` |
| `totalAmount`       | ✓ direct   | `totalAmount` |
| `lines[].sku`       | ✓ direct   | `lines[].sku` |
| `lines[].name`      | ✓ direct   | `lines[].name` |
| `lines[].quantity`  | ✓ direct   | `lines[].quantity` |
| `lines[].unitPrice` | ✓ direct   | `lines[].unitPrice` |

**Delta: none.** Every required target field has a candidate source — the demo of a
contract that *can* be fully mapped. The two `customer.*` fields show the deterministic
**fuzzy/flatten** path (nested → flat), which "Refine gaps (AI)" is not even needed for.
