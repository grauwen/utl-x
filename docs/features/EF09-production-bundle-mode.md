# EF09: Production Bundle Mode (.utlar)

**Status:** Design  
**Priority:** High (production hardening for Azure Marketplace)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF02 (validation wiring)

---

## Summary

In production, transformations should be deployed via CI/CD — not edited at runtime via the Admin API. EF09 introduces a locked deployment mode based on a single `.utlar` archive file on disk. When UTLXe finds a `.utlar` file on the mounted volume, it enters locked mode: the Admin API becomes read-only (except operational endpoints like pause/resume), and all changes must go through CI/CD.

This matches enterprise expectations: MuleSoft, Tibco, SAP CPI, and Azure Logic Apps all enforce immutable production deployments.

## Two modes

| What's on disk | Mode | How determined |
|---|---|---|
| Directory structure (no .utlar) | **Open** (dev/test) | Automatic — no .utlar found |
| `bundle.utlar` file | **Locked** (acc/prd) | Automatic — .utlar found on volume |

No CLI flag needed — the mode is determined by what's on the mounted volume. If CI/CD placed a `.utlar`, it's production. If there's a directory tree (or nothing), it's development.

## Locked mode: Admin API restrictions

| Endpoint | Open mode | Locked mode | Rationale |
|----------|:---------:|:-----------:|-----------|
| `POST /admin/transformations/{name}` | Allowed | **Blocked (403)** | Changes go through CI/CD |
| `POST /admin/bundle` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `DELETE /admin/transformations/{name}` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `DELETE /admin/bundle` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `POST /admin/schemas/{filename}` | Allowed | **Blocked (403)** | Schemas are part of the bundle |
| `DELETE /admin/schemas/{filename}` | Allowed | **Blocked (403)** | Schemas are part of the bundle |
| `POST /admin/transformations/{name}/pause` | Allowed | **Allowed** | Operational — incident response |
| `POST /admin/transformations/{name}/resume` | Allowed | **Allowed** | Operational — resume after fix deployed |
| `POST /admin/transformations/{name}/validation` | Allowed | **Allowed** | Operational — emergency override |
| `DELETE /admin/transformations/{name}/validation` | Allowed | **Allowed** | Operational — revert override |
| `POST /admin/transformations/{name}/test` | Allowed | **Allowed** | Testing doesn't change state |
| `GET /admin/*` (all read endpoints) | Allowed | **Allowed** | Read-only always safe |
| `GET /admin/bundle` (export) | Allowed | **Allowed** | Returns the .utlar directly |

The 403 response in locked mode includes a clear message:

```json
{
  "error": "Production mode — transformations are deployed via CI/CD. Place a new bundle.utlar on the volume and restart.",
  "error_code": "BUNDLE_LOCKED",
  "mode": "locked",
  "bundle_version": "v3.2.1"
}
```

## The .utlar format

A `.utlar` file is a standard ZIP archive with a known directory structure and a manifest:

```
bundle.utlar (ZIP):
  manifest.json
  transformations/
    orders-in/
      orders-in.utlx
      transform.yaml              (optional — links inputs to schemas, sets strategy/policy)
    invoice-to-ubl/
      invoice-to-ubl.utlx
      transform.yaml
  schemas/
    order.json
    invoice.xsd
```

### manifest.json

```json
{
  "format": "utlar",
  "format_version": "1.0",
  "version": "v3.2.1",
  "checksum": "sha256:a1b2c3d4e5f6...",
  "created": "2026-05-07T10:00:00Z",
  "transformations": [
    {
      "name": "orders-in",
      "has_config": true,
      "input_schemas": ["order.json"],
      "output_schemas": ["invoice.xsd"]
    },
    {
      "name": "invoice-to-ubl",
      "has_config": true,
      "input_schemas": [],
      "output_schemas": []
    }
  ],
  "schemas": ["order.json", "invoice.xsd"]
}
```

The manifest enables:
- **Fast startup**: compare manifest version to last loaded → skip reload if same
- **Validation**: verify checksum on startup → reject tampered bundles
- **Visibility**: `GET /admin/info` shows bundle version without unpacking

### transform.yaml per transformation

Links inputs to schemas and declares operational settings:

```yaml
strategy: COMPILED
validationPolicy: strict
inputs:
  - name: input
    schema: order.json
output:
  schema: invoice.xsd
outputBinding: orders-out
maxConcurrent: 4
```

The `schema` field references a file in the `schemas/` directory of the archive. The engine resolves it during loading.

## Startup sequence

```
Container starts
  ↓
Scan mount point (/utlxe/data/)
  ↓
Found bundle.utlar? ──YES──→ LOCKED mode
  │                            ↓
  │                       Read manifest.json
  │                            ↓
  │                       Compare version to last loaded
  │                            ↓
  │                       Same? → skip reload (fast restart)
  │                       Different? → full reload:
  │                            ↓
  │                       Unpack to memory
  │                       Load schemas into SchemaStore
  │                       Compile all transformations
  │                       Create validators from transform.yaml
  │                            ↓
  │                       Admin API: locked mode
  │                       Health: ready=true, mode=locked
  │
  NO
  ↓
Open mode (directory scan as today)
  ↓
Admin API: full access
Health: ready=true, mode=open
```

## CI/CD deployment flow

### Terraform / ARM

```hcl
# Terraform: deploy bundle.utlar to Azure Files
resource "azurerm_storage_share_file" "bundle" {
  name             = "bundle.utlar"
  storage_share_id = azurerm_storage_share.utlxe_data.id
  source           = "${path.module}/artifacts/bundle.utlar"
}

# Restart container to pick up new bundle
resource "null_resource" "restart_utlxe" {
  triggers = {
    bundle_hash = filesha256("${path.module}/artifacts/bundle.utlar")
  }
  provisioner "local-exec" {
    command = "az containerapp revision restart -n utlxe -g ${var.resource_group}"
  }
}
```

### GitHub Actions

```yaml
- name: Deploy bundle
  run: |
    # Upload .utlar to Azure Files
    az storage file upload \
      --share-name utlxe-data \
      --source bundle.utlar \
      --path bundle.utlar

    # Restart to pick up new bundle
    az containerapp revision restart -n utlxe -g $RG
```

### Azure DevOps

```yaml
- task: AzureCLI@2
  inputs:
    scriptType: bash
    scriptLocation: inlineScript
    inlineScript: |
      az storage file upload --share-name utlxe-data --source $(Build.ArtifactStagingDirectory)/bundle.utlar --path bundle.utlar
      az containerapp revision restart -n utlxe -g $(ResourceGroup)
```

## Building a .utlar

### From a directory

```bash
# Build .utlar from directory structure
cd my-transformations/
zip -r ../bundle.utlar manifest.json transformations/ schemas/
```

### Via Admin API (export from dev/test)

```bash
# Export current state as .utlar
curl -H "X-Admin-Key: $KEY" http://dev-utlxe:8081/admin/bundle -o bundle.utlar

# Deploy to production
az storage file upload --share-name utlxe-data --source bundle.utlar --path bundle.utlar
az containerapp revision restart -n utlxe -g prod-rg
```

### Via CLI tool (future)

```bash
# Validate and package
utlx bundle pack my-transformations/ -o bundle.utlar --validate
```

## Engine info shows the mode

```json
GET /admin/info

{
  "version": "1.0.1",
  "mode": "locked",
  "bundle_version": "v3.2.1",
  "bundle_checksum": "sha256:a1b2c3...",
  "bundle_created": "2026-05-07T10:00:00Z",
  "transformations": 2,
  "schemas": 2,
  "ready": true,
  "admin_key_set": true,
  "data_dir": "/utlxe/data"
}
```

In open mode:

```json
{
  "version": "1.0.1",
  "mode": "open",
  "bundle_version": null,
  "transformations": 3,
  "schemas": 1,
  "ready": true
}
```

## Atomic writes (open mode Admin API)

When the Admin API is in open mode AND `--bundle-file` flag is set, every write operation rewrites the .utlar atomically:

1. Write `bundle.utlar.tmp` (new archive with the change)
2. Rename `bundle.utlar.tmp` → `bundle.utlar` (atomic on all filesystems)
3. Old archive is replaced in one operation — no partial state

This means even in open mode with a .utlar, the archive is always consistent.

## Files to implement

| File | Change |
|------|--------|
| New: `modules/engine/.../admin/BundleMode.kt` | Mode detection (open/locked), .utlar reading, manifest parsing |
| `modules/engine/.../admin/AdminEndpoint.kt` | Check mode before mutating endpoints, return 403 in locked mode |
| `modules/engine/.../UtlxEngine.kt` | Startup: detect .utlar, load from archive, set mode |
| `modules/engine/.../Main.kt` | `--bundle-file` CLI flag (alternative to `--data-dir`) |
| `modules/engine/.../health/HealthEndpoint.kt` | Show mode in health response |

## Effort estimate

| Task | Effort |
|------|--------|
| .utlar reading (ZIP with manifest) | 1 day |
| Locked mode enforcement in Admin API | 0.5 day |
| Manifest creation/validation | 0.5 day |
| Mode detection on startup | 0.5 day |
| Atomic .utlar writes (open mode) | 0.5 day |
| `--bundle-file` CLI flag | 0.5 day |
| Info endpoint: mode/version/checksum | 0.5 day |
| Tests | 1 day |
| **Total** | **~5 days** |

## Relationship to other features

- **EF03** (Admin API): locked mode disables mutating endpoints
- **EF02** (validation): schemas in .utlar resolved the same way as schemas in directory
- **EF06** (Dapr pub/sub): subscriptions derived from .utlar contents
- **EF08** (.NET SDK): `IBundleStore.EmbeddedBundleStore` can read .utlar format

---

*Feature EF09. May 2026. Design document.*
*Key insight: the presence of bundle.utlar on the volume IS the production lock. No CLI flag, no config — just deploy the file and the engine knows it's production.*
