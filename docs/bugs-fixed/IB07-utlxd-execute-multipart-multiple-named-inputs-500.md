# IB07: utlxd — `/api/execute-multipart` returns 500 for multiple named inputs

**Status:** **OPEN** — pre-existing on `development` (not a regression). Found 2026-10-10 while
validating `feature/utlxd-bundle-api`; proven identical on a clean `development` daemon jar.
**Priority:** Medium (a real multi-input execution path fails; longstanding, single endpoint).
**Created:** October 2026
**Component:** utlxd daemon REST API — `POST /api/execute-multipart`
(`modules/daemon/src/main/kotlin/com/glomidco/utlx/daemon/rest/RestApiServer.kt` routing +
handler). **Root cause not yet localized** — may be the daemon's multipart/named-input wiring
*or* core multi-input transform resolution; triage may reclassify to `EB`/`B` (see
[Suspected cause](#suspected-cause)).

> **One-line:** a `%utlx` script with two `input json` lines referencing inputs **by name**
> (`$customer`, `$order`), posted to `/api/execute-multipart` with matching named parts, returns
> **HTTP 500** instead of **200** with the merged result. Named multi-input execution over the
> multipart endpoint is broken.

---

## Problem

The daemon exposes `/api/execute-multipart` for executing a transformation against **multiple named
inputs** (each input supplied as a named multipart part). A script that declares two inputs and
references them by name fails with a 500 rather than producing output.

This sits in the same family as two already-fixed named-input bugs — worth reading alongside:
- **[IB02](IB02-ide-execute-hardcoded-input-name.md)** — IDE execute hardcoded the input name.
- **[B24](B24-input-silently-aliased-to-first-input.md)** — input silently aliased to the first input.

IB07 is the multipart/REST sibling: it's specifically the `/api/execute-multipart` + *named* inputs
path that still 500s.

## Reproduction

Fixture: `conformance-suite/daemon-rest-api/tests/endpoints/execute_multiple_inputs.yaml`.

`POST /api/execute-multipart` (multipart/form-data) with:

- part `utlx` (text):
  ```
  %utlx 1.0
  input json
  input json
  output json
  ---
  {
    customerName: $customer.name,
    orderNumber:  $order.id,
    orderTotal:   $order.total
  }
  ```
- part `customer` (file, `X-Format: json`): `{ "name": "John Doe", "email": "john@example.com" }`
- part `order` (file, `X-Format: json`): `{ "id": "ORD-12345", "total": 299.99, "items": 3 }`

**Expected:** `200` with `{"customerName":"John Doe","orderNumber":"ORD-12345","orderTotal":299.99}`.
**Actual:** `500`.

Run it in isolation (avoid the port collision in [Related #2](#related-findings-test-side-not-this-bug)):

```bash
python3 conformance-suite/daemon-rest-api/runners/python-runner/daemon-rest-api-runner.py \
    endpoints execute_multiple_inputs --port 7903 -v
```

## Evidence it is pre-existing (not from `feature/utlxd-bundle-api`)

A/B with only the daemon jar swapped, same suite/fixtures, free port:

| Daemon jar | Result | `Execute with Multiple Named Inputs` |
|---|---|---|
| `feature/utlxd-bundle-api` | 12/14 | **500** |
| `development` (baseline) | 12/14 | **500 — identical** |

Corroborated by diff-scope: the IF19 branch touched no `/api/execute*` handler and no fixtures.
⇒ IB07 is a development-baseline defect, independent of the bundle split.

## Suspected cause

The script references inputs **by declared name** (`$customer`, `$order`), and the parts are named
`customer`/`order`. A 500 (not 404, not a clean 400) means the endpoint exists but throws — likely
either (a) the multipart handler not binding each named part to the correspondingly-named input in
the engine's input map, or (b) core multi-input resolution raising instead of resolving named
inputs. Given IB02/B24 history, named-input plumbing is the prime suspect.

## Proposed next steps

1. Reproduce directly (curl the multipart request) and capture the server stack trace (run utlxd
   with `--log-level DEBUG`).
2. Localize: is the throw in `RestApiServer`'s multipart→input mapping, or in the core transform
   when resolving `$customer`/`$order`? Reclassify to `EB`/`B` if the root cause is below the daemon.
3. Fix so named parts bind to same-named inputs; add a daemon unit test mirroring the fixture.

---

## Related findings (test-side, not this bug)

These surfaced in the same run and should be handled as **test maintenance**, not product bugs:

1. **Fixture error — `invalid_script.yaml` step 1 expects the wrong status.**
   `conformance-suite/daemon-rest-api/tests/edge-cases/invalid_script.yaml` step 1 ("Missing utlx
   parameter") posts `/api/execute` with **no `utlx` field** and `expect: status: 500`. The daemon
   correctly returns **400** (missing required parameter = client error) — and the *same fixture's*
   step 2 (empty script) already expects **400**. So step 1 is internally inconsistent and wrong.
   **Fix:** change step 1's expected status `500 → 400`. (No product change.)

2. **Test-runner hardcodes port 7779 → false "all 14 timed out", and can kill your daemon.**
   `daemon-rest-api-runner.py` defaults to port **7779** — the same port a dev daemon uses
   (`utlxd start --api --api-port 7779`). If a daemon is already on 7779, the runner's
   health-check sees it, does **not** start its own, and runs every test against that process; if
   that process is hung/busy, **all 14 time out** (~130s) — an environment artifact, not a daemon
   failure. Worse, the runner's cleanup `pkill -f "utlxd.*--api-port 7779"` will **terminate a
   developer's running daemon** started with `--api-port 7779`.
   **Fix:** bind an **ephemeral port** (the `ServerSocket(0)` pattern used elsewhere in the repo)
   instead of hardcoding 7779. Until then: don't run this suite while your own utlxd is on 7779 —
   pass `--port <free>`.

### Coverage note
The daemon-rest-api suite does **not** exercise `/api/bundle/*` (IF19 CRUD) — the bundle API has
live coverage only via module tests, not this subprocess suite. Adding bundle cases here is a
separate follow-up.
