= CI/CD Integration

_Automate transformation deployment with Azure DevOps, GitHub Actions, or any pipeline that can call HTTP._

== The Deployment Model

// Source of truth: git repository with .utlx files + schemas
// Build step: assemble bundle.zip
// Deploy step: POST /admin/bundle to UTLXe
// Verify step: POST /admin/transformations/{name}/test with sample input

== Bundle Repository Structure

// my-transformations/
//   schemas/
//     order.xsd
//     invoice.json
//   transformations/
//     invoice-to-ubl/
//       invoice-to-ubl.utlx
//       transform.yaml
//     validate-order/
//       validate-order.utlx
//   test-data/
//     invoice-to-ubl-sample.json
//   build.sh

== GitHub Actions Workflow

// name: Deploy Transformations
// on: push to main
// steps:
//   1. Checkout
//   2. zip -r bundle.zip schemas/ transformations/
//   3. curl POST /admin/bundle/validate (dry run)
//   4. curl POST /admin/bundle (deploy)
//   5. curl POST /admin/transformations/{name}/test (verify)

== Azure DevOps Pipeline

// Similar structure with az containerapp commands
// Use Azure CLI task for authentication
// Pipeline variables for UTLXE_ADMIN_KEY (secret)

== Rollback

// Keep previous bundle.zip as artifact
// Rollback = re-deploy previous ZIP
// POST /admin/bundle with old bundle → atomic replace

== Multi-Environment Promotion

// Dev → Staging → Production
// Same bundle, different Container App instances
// Environment-specific config via transform.yaml or validation overrides
// Schema validation strict in prod, warn in staging, off in dev

== Testing in the Pipeline

// POST /admin/bundle/validate → dry run (does it compile?)
// POST /admin/transformations/{name}/test → functional test (does it produce correct output?)
// Both happen before real traffic touches the new version

== GitOps Pattern

// Git push triggers pipeline
// Pipeline deploys to UTLXe
// UTLXe health shows ready=true
// Kubernetes/Container Apps routes traffic
// Full cycle: commit → deploy → verify → traffic in < 2 minutes
