= CI/CD Integration

Automated deployment ensures that transformations are tested, versioned, and deployed consistently. This chapter shows how to integrate UTLXe with GitHub Actions and Azure DevOps.

== The Deployment Model

The transformation source lives in a git repository. The CI/CD pipeline:

+ Assembles a bundle (ZIP) from the repository contents.
+ Validates the bundle against the running UTLXe instance (dry run).
+ Deploys the bundle.
+ Verifies with test inputs.

No custom Docker image is built. The UTLXe container from the Marketplace stays as-is. Only the transformations change.

== Repository Structure

A recommended layout for the transformation repository:

```
my-transformations/
  schemas/
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx
    order-enrichment/
      order-enrichment.utlx
  test-data/
    invoice-to-ubl/
      input.json
      expected-output.xml
  scripts/
    build-bundle.sh
    deploy.sh
```

The `test-data/` directory contains sample inputs and expected outputs for each transformation. The pipeline uses these for post-deployment verification.

== Building the Bundle

A simple script assembles the ZIP:

```bash
#!/bin/bash
# build-bundle.sh
cd "$(dirname "$0")/.."
zip -r bundle.zip schemas/ transformations/
echo "Bundle created: bundle.zip"
```

== GitHub Actions Workflow

```yaml
name: Deploy Transformations
on:
  push:
    branches: [main]

env:
  UTLXE_ADMIN_URL: ${{ secrets.UTLXE_ADMIN_URL }}
  UTLXE_ADMIN_KEY: ${{ secrets.UTLXE_ADMIN_KEY }}
  UTLXE_DATA_URL: ${{ secrets.UTLXE_DATA_URL }}

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build bundle
        run: |
          cd transformations
          zip -r ../bundle.zip schemas/ transformations/

      - name: Validate (dry run)
        run: |
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -X POST \
            -H "X-Admin-Key: $UTLXE_ADMIN_KEY" \
            -F "file=@bundle.zip" \
            "$UTLXE_ADMIN_URL/admin/bundle/validate")
          if [ "$STATUS" != "200" ]; then
            echo "Validation failed with status $STATUS"
            exit 1
          fi

      - name: Deploy
        run: |
          curl -sf \
            -X POST \
            -H "X-Admin-Key: $UTLXE_ADMIN_KEY" \
            -F "file=@bundle.zip" \
            "$UTLXE_ADMIN_URL/admin/bundle"

      - name: Verify
        run: |
          for dir in test-data/*/; do
            NAME=$(basename "$dir")
            INPUT="$dir/input.json"
            EXPECTED="$dir/expected-output.xml"
            if [ -f "$INPUT" ]; then
              RESULT=$(curl -sf \
                -X POST \
                -H "X-Admin-Key: $UTLXE_ADMIN_KEY" \
                -H "Content-Type: application/json" \
                -d @"$INPUT" \
                "$UTLXE_ADMIN_URL/admin/transformations/$NAME/test")
              echo "$NAME: $RESULT"
            fi
          done
```

The workflow validates before deploying, and verifies after. If validation fails, the deployment never happens. If verification fails, the pipeline reports the error --- the transformations are already deployed, but the team is notified.

== Azure DevOps Pipeline

The structure is similar, using Azure CLI tasks for authentication:

```yaml
trigger:
  branches:
    include:
      - main

pool:
  vmImage: 'ubuntu-latest'

variables:
  - group: utlxe-secrets

steps:
  - script: |
      cd transformations
      zip -r $(Build.ArtifactStagingDirectory)/bundle.zip schemas/ transformations/
    displayName: 'Build bundle'

  - script: |
      curl -sf -X POST \
        -H "X-Admin-Key: $(UTLXE_ADMIN_KEY)" \
        -F "file=@$(Build.ArtifactStagingDirectory)/bundle.zip" \
        "$(UTLXE_ADMIN_URL)/admin/bundle/validate"
    displayName: 'Validate'

  - script: |
      curl -sf -X POST \
        -H "X-Admin-Key: $(UTLXE_ADMIN_KEY)" \
        -F "file=@$(Build.ArtifactStagingDirectory)/bundle.zip" \
        "$(UTLXE_ADMIN_URL)/admin/bundle"
    displayName: 'Deploy'
```

Store `UTLXE_ADMIN_KEY` and `UTLXE_ADMIN_URL` in an Azure DevOps variable group marked as secret.

== Rollback

Rollback is straightforward: re-deploy a previous bundle. Keep previous bundles as pipeline artifacts or in a git tag.

```bash
# Download previous bundle from artifact storage
# Re-deploy it
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@bundle-v1.2.3.zip" \
  http://<admin>:8081/admin/bundle
```

The bundle upload is atomic --- the old transformations are replaced in one operation. There is no partial state.

Alternatively, use the export endpoint to create a backup before deploying:

```bash
# Backup current state
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/bundle -o backup-before-deploy.zip

# Deploy new version
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@new-bundle.zip" \
  http://<admin>:8081/admin/bundle

# If something goes wrong, rollback
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@backup-before-deploy.zip" \
  http://<admin>:8081/admin/bundle
```

== Multi-Environment Promotion

Use the same bundle across environments with environment-specific configuration:

#table(
  columns: (auto, auto, auto),
  [*Environment*], [*Validation policy*], [*How*],
  [Development], [`off`], [Runtime override via Admin API],
  [Staging], [`warn`], [`transform.yaml` config],
  [Production], [`strict`], [`transform.yaml` config],
)

The `.utlx` source and schemas are identical across environments. Only the `transform.yaml` varies (or the runtime override via the Admin API).

== The Full Cycle

A commit to `main` triggers the pipeline. Within two minutes:

+ The bundle is built from the repository.
+ It is validated against the running UTLXe (dry run --- does it compile?).
+ It is deployed (atomic replacement of all transformations).
+ Each transformation is tested with sample input.
+ The pipeline reports success or failure.

From commit to verified production deployment in under two minutes, with no custom Docker images, no container restarts, and no downtime.
