= DTAP: From Development to Production

Enterprise deployments follow a promotion path: Development → Test → Acceptance → Production. UTLXe's two-mode architecture (open and locked) maps directly to this lifecycle. This chapter explains how to set up each environment and promote transformations through the stages.

== The Two Modes

#table(
  columns: (auto, auto, 1fr),
  [*What's on disk*], [*Mode*], [*Behavior*],
  [Directory structure (no `.utlar`)], [*Open* (Dev/Test)], [Full Admin API. Upload, edit, delete, test interactively.],
  [Any `.utlar` file (e.g., `sales.utlar`)], [*Locked* (Acc/Prd)], [Admin API read-only. Changes via CI/CD only. Operational endpoints (pause, resume, logs) remain available.],
)

The mode is determined automatically by the presence of any `.utlar` file on the data volume. Name it after the business flow it serves --- `sales.utlar`, `orders.utlar`, `website.utlar`. No CLI flag, no environment variable --- just the file.

== Environment Layout

A typical DTAP setup uses four Azure Container App environments, each with its own Service Bus namespace and UTLXe instance:

#table(
  columns: (auto, auto, auto, auto, auto),
  [*Environment*], [*Mode*], [*Service Bus*], [*Persistence*], [*Who deploys*],
  [Development], [Open], [`sb-utlxe-dev`], [Azure Files (optional)], [Developer via Admin API],
  [Test], [Open], [`sb-utlxe-tst`], [Azure Files], [Developer or CI/CD],
  [Acceptance], [Locked], [`sb-utlxe-acc`], [Azure Files + `.utlar`], [CI/CD pipeline],
  [Production], [Locked], [`sb-utlxe-prd`], [Azure Files + `.utlar`], [CI/CD pipeline (with approval gate)],
)

Each environment connects to its own Service Bus namespace. The same `.utlar` bundle deploys to all locked environments --- the bundle contains transformation logic and queue/topic names, but *not* connection strings or credentials.

== Development Workflow (Open Mode)

In development, the operator (or developer) uses the Admin API interactively:

```bash
# Upload a transformation
curl -X POST -H "X-Admin-Key: $KEY" \
  -d @invoice-normalize.utlx \
  http://utlxe-dev:8081/admin/transformations/invoice-normalize

# Configure messaging (staged as draft)
curl -X POST -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"input": {"queue": "raw-invoices"}, "output": {"queue": "normalized"}}' \
  http://utlxe-dev:8081/admin/transformations/invoice-normalize/messaging

# Test with sample data (no queue impact)
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"invoiceId": "INV-001", "amount": 100}' \
  http://utlxe-dev:8081/admin/transformations/invoice-normalize/test

# When satisfied, sync to Dapr (messages start flowing)
curl -X POST -H "X-Admin-Key: $KEY" \
  http://utlxe-dev:8081/admin/transformations/invoice-normalize/sync
```

The stage-then-sync model means you can test transformations via HTTP before connecting them to real queues. Sync is the "go live" switch for the development environment.

When the transformation works correctly, export the full bundle:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/bundle -o orders.utlar
```

This `.utlar` file contains everything: transformations, schemas, transform.yaml with messaging config. Name it after the business flow (`orders.utlar`, `sales.utlar`) --- not `orders.utlar`. It is the artifact that moves through the promotion pipeline.

== Promotion Pipeline

```
Development (open)         Test (open)           Acceptance (locked)     Production (locked)
     │                         │                       │                       │
     │  developer exports      │                       │                       │
     │  orders.utlar           │                       │                       │
     └────────┬────────────────┘                       │                       │
              │                                        │                       │
              ▼                                        │                       │
    ┌──────────────────┐                               │                       │
    │ Git repository   │                               │                       │
    │ (version control │                               │                       │
    │  for .utlar)     │                               │                       │
    └────────┬─────────┘                               │                       │
             │ CI/CD pipeline triggers                 │                       │
             ├─────────────────────────────────────────┤                       │
             │                                         │                       │
             ▼                                         ▼                       │
    Upload orders.utlar                      Upload orders.utlar               │
    to test Azure Files                      to acc Azure Files                │
    Restart container                        Restart container                 │
             │                                         │                       │
             ▼                                         ▼                       │
    Run integration tests                    Run smoke tests                   │
             │                                         │                       │
             │ (auto)                                  │ (manual approval)     │
             │                                         ├───────────────────────┘
             │                                         │
             │                                         ▼
             │                               Upload orders.utlar
             │                               to prd Azure Files
             │                               Restart container
```

== CI/CD: GitHub Actions Example

```yaml
name: Deploy UTLXe Bundle
on:
  push:
    paths: ['bundles/**']
    branches: [main]

jobs:
  deploy-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Deploy to Test
        run: |
          az storage file upload \
            --share-name utlxe-data \
            --account-name ${{ secrets.TEST_STORAGE }} \
            --source bundles/orders.utlar \
            --path orders.utlar
          az containerapp revision restart \
            -n utlxe -g rg-utlxe-tst

      - name: Wait for ready
        run: |
          for i in $(seq 1 30); do
            STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
              https://utlxe-tst.internal.example.azurecontainerapps.io/health/ready)
            if [ "$STATUS" = "200" ]; then break; fi
            sleep 2
          done

      - name: Run integration tests
        run: ./tests/integration-tests.sh utlxe-tst

  deploy-acceptance:
    needs: deploy-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to Acceptance
        run: |
          az storage file upload \
            --share-name utlxe-data \
            --account-name ${{ secrets.ACC_STORAGE }} \
            --source bundles/orders.utlar \
            --path orders.utlar
          az containerapp revision restart \
            -n utlxe -g rg-utlxe-acc

  deploy-production:
    needs: deploy-acceptance
    runs-on: ubuntu-latest
    environment: production    # requires manual approval
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to Production
        run: |
          az storage file upload \
            --share-name utlxe-data \
            --account-name ${{ secrets.PRD_STORAGE }} \
            --source bundles/orders.utlar \
            --path orders.utlar
          az containerapp revision restart \
            -n utlxe -g rg-utlxe-prd
```

The key: the *same* `orders.utlar` file moves from Test → Acceptance → Production. Only the Azure infrastructure (Service Bus namespace, connection strings, managed identity) differs per environment.

== What Differs Per Environment

#table(
  columns: (auto, 1fr, 1fr),
  [*Aspect*], [*In the bundle (same everywhere)*], [*In the environment (differs per stage)*],
  [Transformation logic], [`.utlx` source files], [---],
  [Queue/topic names], [`transform.yaml` messaging config], [---],
  [Schemas], [`.json` / `.xsd` files], [---],
  [Validation policy], [`transform.yaml`], [---],
  [Service Bus namespace], [---], [Dapr component YAML / Managed Identity],
  [Connection credentials], [---], [Container App secrets or Managed Identity RBAC],
  [Admin key], [---], [`UTLXE_ADMIN_KEY` environment variable],
  [Log level], [---], [Runtime via Admin API (operational)],
)

This separation means the bundle is environment-agnostic. A transformation that reads from `orders-in` works in dev (where `orders-in` is a queue on `sb-utlxe-dev`) and in production (where `orders-in` is a queue on `sb-utlxe-prd`) --- the queue names are the same, the infrastructure behind them differs.

== Same Queue Names, Different Namespaces

The recommended pattern: use the *same* queue and topic names in all environments. Each environment has its own Service Bus namespace:

#table(
  columns: (auto, auto, auto, auto),
  [*Queue name*], [*Dev namespace*], [*Acc namespace*], [*Prd namespace*],
  [`orders-in`], [`sb-utlxe-dev`], [`sb-utlxe-acc`], [`sb-utlxe-prd`],
  [`orders-out`], [`sb-utlxe-dev`], [`sb-utlxe-acc`], [`sb-utlxe-prd`],
  [`invoices-in`], [`sb-utlxe-dev`], [`sb-utlxe-acc`], [`sb-utlxe-prd`],
)

This way, the bundle's `transform.yaml` says `queue: orders-in` and it works everywhere. The Dapr component (or Managed Identity) points to the correct namespace per environment.

== Operational Capabilities in Locked Mode

Even in production (locked mode), operators have full access to diagnostic and operational endpoints:

#table(
  columns: (auto, 1fr),
  [*Action*], [*How*],
  [Pause a transformation], [`POST /admin/transformations/{name}/pause`],
  [Resume after fix deployed], [`POST /admin/transformations/{name}/resume`],
  [Check errors], [`GET /admin/transformations/{name}/errors`],
  [Override validation (incident)], [`POST /admin/transformations/{name}/validation`],
  [Switch to DEBUG logging], [`POST /admin/log/level` with `{"level":"DEBUG","revert_after_minutes":30}`],
  [View recent logs], [`GET /admin/logs?level=ERROR`],
  [Check bundle version], [`GET /admin/info` → `bundle_version`, `bundle_checksum`],
  [Export current bundle], [`GET /admin/bundle`],
  [Test with sample input], [`POST /admin/transformations/{name}/test`],
)

What is *not* available: uploading, deleting, or modifying transformations, schemas, or messaging config. These return 403 `BUNDLE_LOCKED`.

== Rollback

To rollback in production:

+ Deploy the previous version's `orders.utlar` to Azure Files (from git history or artifact store).
+ Restart the container: `az containerapp revision restart -n utlxe -g rg-utlxe-prd`.
+ UTLXe loads the previous bundle. Done.

The `.utlar` file is the single artifact. Version control it in git alongside the CI/CD pipeline. Every version is a deployable, self-contained unit.

== Infrastructure as Code

All four environments should be defined in Terraform or Bicep. Each environment is identical except for:

- Resource group name and tags
- Service Bus namespace name
- Storage account name
- Container App secrets (admin key, connection strings)
- Managed Identity RBAC role assignments

```bash
# Example: deploy all four environments from Bicep
az deployment group create -g rg-utlxe-dev -f main.bicep -p env=dev
az deployment group create -g rg-utlxe-tst -f main.bicep -p env=tst
az deployment group create -g rg-utlxe-acc -f main.bicep -p env=acc
az deployment group create -g rg-utlxe-prd -f main.bicep -p env=prd
```

The Bicep template is parameterized by environment. The transformation bundle is *not* part of the infrastructure --- it is a separate CI/CD artifact.
