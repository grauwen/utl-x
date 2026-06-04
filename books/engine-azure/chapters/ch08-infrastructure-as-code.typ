= Infrastructure as Code: Managing UTLXe Deployments

UTLXe is deployed from the Azure Marketplace --- the wizard provisions the Container App, Dapr sidecar, storage, and networking. This chapter covers what happens *after* deployment: managing multiple environments, deploying bundles via CI/CD, and configuring Service Bus resources.

== What the Marketplace Provisions

When you deploy from the Marketplace, the following is created automatically:

#table(
  columns: (auto, 1fr),
  [*Resource*], [*Purpose*],
  [Container App Environment], [Hosting environment with optional VNet integration],
  [Container App (UTLXe + Admin UI)], [The engine + web UI, with Dapr sidecar],
  [Azure Files share], [Persistent storage for transformations (if enabled)],
  [Log Analytics workspace], [Container logs and metrics],
  [Managed Identity], [Secretless authentication to Azure services],
)

You do *not* need to provision these yourself. The Marketplace handles it. Your responsibility is:

+ *Service Bus / Event Hub* --- create the queues, topics, or Event Hubs that UTLXe connects to.
+ *Transformations* --- deploy your `.utlx` rules via the Admin API or `.utlar` bundle.
+ *Dapr components* --- configure the connection between UTLXe and your messaging infrastructure.

== Managing Multiple Environments

For DTAP (Dev/Test/Acc/Prd), deploy UTLXe from the Marketplace once per environment. Each environment has its own:

- Container App (separate resource group)
- Service Bus namespace (separate connection)
- Admin key (separate secret)

The same `.utlar` bundle deploys to all environments --- it contains transformation logic and queue/topic names. The connection to the actual Service Bus namespace differs per environment (via Dapr component configuration or Managed Identity).

See Chapter 7 (DTAP) for the full environment strategy.

== Service Bus Resources

The Marketplace creates the UTLXe container but not your Service Bus queues or topics. Create these with the Azure CLI or Portal:

```bash
# Create Service Bus namespace (one per environment)
az servicebus namespace create \
  --name sb-utlxe-prd \
  --resource-group rg-utlxe-prd \
  --sku Standard

# Create queues
az servicebus queue create --name orders-in \
  --namespace-name sb-utlxe-prd --resource-group rg-utlxe-prd

az servicebus queue create --name orders-out \
  --namespace-name sb-utlxe-prd --resource-group rg-utlxe-prd
```

Then configure the Dapr component to connect UTLXe to these queues (see Chapter 6: Connecting to Azure Services).

== Bundle Deployment via CI/CD

Once the infrastructure is provisioned (via Marketplace), the CI/CD pipeline deploys *only the bundle* --- the transformation rules. The pipeline does not touch the infrastructure.

=== Deploying to Open Mode (Dev/Test)

Upload the bundle via the Admin API:

```bash
curl -X POST \
  -H "X-Admin-Key: $ADMIN_KEY" \
  -F "file=@bundle.zip" \
  "$UTLXE_ADMIN_URL/admin/bundle"
```

=== Deploying to Locked Mode (Acc/Prd)

Upload the `.utlar` file to Azure Files and restart the container:

```bash
# Upload bundle to Azure Files
az storage file upload \
  --share-name utlxe-data \
  --account-name $STORAGE_ACCOUNT \
  --source bundle.utlar \
  --path bundle.utlar

# Restart to pick up the new bundle
az containerapp revision restart \
  -n utlxe -g $RESOURCE_GROUP
```

UTLXe detects `bundle.utlar` on startup, loads it, and enters locked mode. No Admin API upload needed.

=== GitHub Actions Example

```yaml
name: Deploy UTLXe Bundle
on:
  push:
    branches: [main]
    paths: ['transformations/**']

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build bundle
        run: |
          cd transformations
          zip -r ../bundle.utlar manifest.json transformations/ schemas/

      - name: Deploy to Test (open mode)
        run: |
          curl -sf -X POST \
            -H "X-Admin-Key: ${{ secrets.TST_ADMIN_KEY }}" \
            -F "file=@bundle.utlar" \
            "${{ secrets.TST_ADMIN_URL }}/admin/bundle"

      - name: Deploy to Production (locked mode)
        environment: production
        run: |
          az storage file upload \
            --share-name utlxe-data \
            --account-name ${{ secrets.PRD_STORAGE }} \
            --source bundle.utlar --path bundle.utlar
          az containerapp revision restart \
            -n utlxe -g ${{ secrets.PRD_RESOURCE_GROUP }}
```

=== Azure DevOps Example

```yaml
trigger:
  branches: { include: [main] }
  paths: { include: ['transformations/**'] }

stages:
  - stage: Test
    jobs:
      - job: Deploy
        steps:
          - script: |
              cd transformations
              zip -r $(Build.ArtifactStagingDirectory)/bundle.utlar \
                manifest.json transformations/ schemas/
          - script: |
              curl -sf -X POST \
                -H "X-Admin-Key: $(TST_ADMIN_KEY)" \
                -F "file=@$(Build.ArtifactStagingDirectory)/bundle.utlar" \
                "$(TST_ADMIN_URL)/admin/bundle"

  - stage: Production
    condition: succeeded()
    jobs:
      - deployment: Deploy
        environment: production
        strategy:
          runOnce:
            deploy:
              steps:
                - task: AzureCLI@2
                  inputs:
                    azureSubscription: 'my-service-connection'
                    scriptType: bash
                    inlineScript: |
                      az storage file upload \
                        --share-name utlxe-data \
                        --account-name $(PRD_STORAGE) \
                        --source $(Pipeline.Workspace)/bundle.utlar \
                        --path bundle.utlar
                      az containerapp revision restart \
                        -n utlxe -g $(PRD_RESOURCE_GROUP)
```

== Rollback

To rollback: deploy the previous `.utlar` from your artifact store or git history.

```bash
# Re-deploy previous version
az storage file upload \
  --share-name utlxe-data \
  --account-name $STORAGE_ACCOUNT \
  --source bundle-v1.2.0.utlar \
  --path bundle.utlar

az containerapp revision restart -n utlxe -g $RESOURCE_GROUP
```

The `.utlar` is the single deployment artifact. Version control it alongside your CI/CD pipeline.

== Complete Example: Terraform End-to-End

This example deploys everything a customer needs for a working UTLXe integration: the Marketplace offering, Service Bus queues, persistent storage with the `.utlar` bundle, Dapr components, and RBAC. After `terraform apply`, messages flow.

Prerequisites: an existing `.utlar` bundle (e.g., `mybusinesssolution.utlar`) and an Azure subscription.

```hcl
variable "location" {
  default = "westeurope"
}

variable "admin_key" {
  type      = string
  sensitive = true
}

variable "bundle_file" {
  description = "Path to the .utlar bundle file"
  type        = string
  default     = "mybusinesssolution.utlar"
}

locals {
  prefix = "utlxe-prd"
}

resource "azurerm_resource_group" "rg" {
  name     = "rg-${local.prefix}"
  location = var.location
}

# ── Service Bus (your queues) ──

resource "azurerm_servicebus_namespace" "sb" {
  name                = "sb-${local.prefix}"
  location            = var.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "Standard"
}

resource "azurerm_servicebus_queue" "orders_in" {
  name               = "orders-in"
  namespace_id       = azurerm_servicebus_namespace.sb.id
  max_delivery_count = 100
}

resource "azurerm_servicebus_queue" "orders_out" {
  name               = "orders-out"
  namespace_id       = azurerm_servicebus_namespace.sb.id
  max_delivery_count = 100
}

# ── Storage for persistent bundles ──

resource "azurerm_storage_account" "storage" {
  name                     = "st${replace(local.prefix, "-", "")}"
  resource_group_name      = azurerm_resource_group.rg.name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_storage_share" "data" {
  name               = "utlxe-data"
  storage_account_id = azurerm_storage_account.storage.id
  quota              = 1
}

# ── Deploy the .utlar bundle to storage ──

resource "azurerm_storage_share_file" "bundle" {
  name             = "bundle.utlar"
  storage_share_id = azurerm_storage_share.data.id
  source           = var.bundle_file
}

# ── UTLXe from the Azure Marketplace ──
# Deploy via the Portal Marketplace wizard first, then import:
#   az containerapp show -n utlxe -g rg-utlxe-prd > utlxe-app.json
#   terraform import azurerm_container_app.utlxe /subscriptions/.../utlxe
#
# Or use the azurerm_resource_group_template_deployment to deploy
# the Marketplace ARM template directly:

resource "azurerm_resource_group_template_deployment" "utlxe" {
  name                = "utlxe-marketplace"
  resource_group_name = azurerm_resource_group.rg.name
  deployment_mode     = "Incremental"

  # The Marketplace ARM template (mainTemplate.json)
  template_content = file("${path.module}/mainTemplate.json")

  parameters_content = jsonencode({
    location          = { value = var.location }
    adminKey          = { value = var.admin_key }
    enablePersistence = { value = true }
  })

  depends_on = [
    azurerm_storage_share_file.bundle
  ]
}

# ── RBAC: UTLXe Managed Identity → Service Bus ──
# After the Marketplace deployment creates the Container App,
# grant it access to Service Bus

data "azurerm_container_app" "utlxe" {
  name                = "utlxe"
  resource_group_name = azurerm_resource_group.rg.name

  depends_on = [azurerm_resource_group_template_deployment.utlxe]
}

resource "azurerm_role_assignment" "sb_sender" {
  scope                = azurerm_servicebus_namespace.sb.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = data.azurerm_container_app.utlxe.identity[0].principal_id
}

resource "azurerm_role_assignment" "sb_receiver" {
  scope                = azurerm_servicebus_namespace.sb.id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = data.azurerm_container_app.utlxe.identity[0].principal_id
}

# ── Dapr component: connect UTLXe to Service Bus ──

resource "azurerm_container_app_environment_dapr_component" "orders_in" {
  name                         = "orders-in"
  container_app_environment_id = data.azurerm_container_app.utlxe.container_app_environment_id
  component_type               = "bindings.azure.servicebusqueues"
  version                      = "v1"

  metadata {
    name  = "namespaceName"
    value = "${azurerm_servicebus_namespace.sb.name}.servicebus.windows.net"
  }
  metadata {
    name  = "queueName"
    value = "orders-in"
  }
  metadata {
    name  = "direction"
    value = "input, output"
  }

  scopes = ["utlxe"]
}

resource "azurerm_container_app_environment_dapr_component" "orders_out" {
  name                         = "orders-out"
  container_app_environment_id = data.azurerm_container_app.utlxe.container_app_environment_id
  component_type               = "bindings.azure.servicebusqueues"
  version                      = "v1"

  metadata {
    name  = "namespaceName"
    value = "${azurerm_servicebus_namespace.sb.name}.servicebus.windows.net"
  }
  metadata {
    name  = "queueName"
    value = "orders-out"
  }
  metadata {
    name  = "direction"
    value = "input, output"
  }

  scopes = ["utlxe"]
}

# ── Outputs ──

output "service_bus" {
  value = azurerm_servicebus_namespace.sb.name
}

output "storage_account" {
  value = azurerm_storage_account.storage.name
}
```

Deploy:

```bash
terraform init
terraform apply \
  -var="admin_key=my-secret-key" \
  -var="bundle_file=mybusinesssolution.utlar"
```

After `terraform apply` completes:

+ Service Bus namespace with `orders-in` and `orders-out` queues --- created
+ Storage with `bundle.utlar` uploaded --- created
+ UTLXe from Marketplace --- deployed in locked mode (`.utlar` found on disk)
+ Managed Identity with Service Bus RBAC --- configured
+ Dapr components connecting UTLXe to the queues --- configured
+ Messages sent to `orders-in` are transformed and forwarded to `orders-out`

The customer provides one file (`mybusinesssolution.utlar`) and one command (`terraform apply`). Everything works.

== Advanced: Custom Infrastructure

For customers who need to customize the infrastructure beyond what the Marketplace provides (custom VNet, private endpoints, workload profiles), the deployment templates are available on request. Contact us for Enterprise deployment support.
