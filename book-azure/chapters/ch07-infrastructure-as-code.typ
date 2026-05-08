= Infrastructure as Code: Deploying UTLXe with Terraform and Bicep

The Marketplace offering deploys UTLXe via the Azure Portal wizard. But enterprise customers manage infrastructure as code --- repeatable, version-controlled, reviewable. This chapter shows how to provision the complete UTLXe stack (Container App, Dapr, Service Bus, storage, networking) using Terraform and Bicep, integrated into CI/CD pipelines.

== What Gets Provisioned

A production UTLXe deployment consists of:

#table(
  columns: (auto, 1fr),
  [*Resource*], [*Purpose*],
  [Container App Environment], [Hosting environment with VNet integration],
  [Container App (UTLXe)], [The engine --- runs the UTLXe container image with Dapr sidecar],
  [Azure Files share], [Persistent storage for transformations (or `.utlar` in locked mode)],
  [Service Bus namespace], [Message queues and/or topics],
  [Dapr components], [Binding and pub/sub configuration connecting UTLXe to Service Bus],
  [Managed Identity], [Secretless authentication to Service Bus],
  [Log Analytics workspace], [Container logs and metrics],
)

== Bicep: Complete Example

This Bicep template provisions everything for one environment. Parameterize `env` to deploy Dev, Test, Acc, and Prd from the same template.

```bicep
@description('Environment name (dev, tst, acc, prd)')
param env string

@description('Azure region')
param location string = resourceGroup().location

@description('UTLXe container image')
param utlxeImage string = 'ghcr.io/grauwen/utlxe:latest'

@description('UTLXe admin API key')
@secure()
param adminKey string

// ── Naming convention ──
var prefix = 'utlxe-${env}'
var sbNamespace = 'sb-${prefix}'
var storageName = 'st${replace(prefix, '-', '')}' // no hyphens in storage names

// ── Log Analytics ──
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: '${prefix}-logs'
  location: location
  properties: { sku: { name: 'PerGB2018' } }
}

// ── Container App Environment ──
resource containerEnv 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${prefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
    daprAIInstrumentationKey: ''
  }
}

// ── Storage Account + File Share ──
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-05-01' = {
  name: storageName
  location: location
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
}

resource fileService 'Microsoft.Storage/storageAccounts/fileServices@2023-05-01' = {
  parent: storageAccount
  name: 'default'
}

resource fileShare 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-05-01' = {
  parent: fileService
  name: 'utlxe-data'
  properties: { shareQuota: 1 }
}

// ── Mount storage in Container App Environment ──
resource envStorage 'Microsoft.App/managedEnvironments/storages@2024-03-01' = {
  parent: containerEnv
  name: 'utlxe-storage'
  properties: {
    azureFile: {
      accountName: storageAccount.name
      accountKey: storageAccount.listKeys().keys[0].value
      shareName: 'utlxe-data'
      accessMode: 'ReadWrite'
    }
  }
}

// ── Service Bus ──
resource serviceBus 'Microsoft.ServiceBus/namespaces@2022-10-01-preview' = {
  name: sbNamespace
  location: location
  sku: { name: 'Standard', tier: 'Standard' }
}

resource queueOrdersIn 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = {
  parent: serviceBus
  name: 'orders-in'
  properties: { maxDeliveryCount: 100 }
}

resource queueOrdersOut 'Microsoft.ServiceBus/namespaces/queues@2022-10-01-preview' = {
  parent: serviceBus
  name: 'orders-out'
  properties: { maxDeliveryCount: 100 }
}

// ── Managed Identity for Service Bus ──
resource utlxeApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: prefix
  location: location
  identity: { type: 'SystemAssigned' }
  properties: {
    managedEnvironmentId: containerEnv.id
    configuration: {
      dapr: {
        enabled: true
        appId: 'utlxe'
        appPort: 8085
        appProtocol: 'http'
      }
      ingress: {
        external: true
        targetPort: 8085
        transport: 'http'
        additionalPortMappings: [
          { targetPort: 8081, exposedPort: 8081, external: false }
        ]
      }
      secrets: [
        { name: 'admin-key', value: adminKey }
      ]
    }
    template: {
      containers: [
        {
          name: 'utlxe'
          image: utlxeImage
          resources: { cpu: json('1.0'), memory: '2Gi' }
          env: [
            { name: 'UTLXE_ADMIN_KEY', secretRef: 'admin-key' }
          ]
          command: [
            'java', '-jar', '/utlxe/utlxe.jar'
            '--mode', 'http'
            '--admin-port', '8081'
            '--http-port', '8085'
            '--data-dir', '/utlxe/data'
          ]
          volumeMounts: [
            { volumeName: 'data', mountPath: '/utlxe/data' }
          ]
        }
      ]
      scale: { minReplicas: 1, maxReplicas: 3 }
      volumes: [
        { name: 'data', storageName: 'utlxe-storage', storageType: 'AzureFile' }
      ]
    }
  }
}

// ── RBAC: Managed Identity → Service Bus ──
var sbDataSenderRole = '69a216fc-b8fb-44d8-bc22-1f3c2cd27a39'
var sbDataReceiverRole = '4f6d3b9b-027b-4f4c-9142-0e5a2a2247e0'

resource sbSender 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(utlxeApp.id, sbDataSenderRole, serviceBus.id)
  scope: serviceBus
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions', sbDataSenderRole)
    principalId: utlxeApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

resource sbReceiver 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(utlxeApp.id, sbDataReceiverRole, serviceBus.id)
  scope: serviceBus
  properties: {
    roleDefinitionId: subscriptionResourceId(
      'Microsoft.Authorization/roleDefinitions', sbDataReceiverRole)
    principalId: utlxeApp.identity.principalId
    principalType: 'ServicePrincipal'
  }
}

// ── Dapr Component: Service Bus binding (Managed Identity) ──
resource daprOrdersIn 'Microsoft.App/managedEnvironments/daprComponents@2024-03-01' = {
  parent: containerEnv
  name: 'orders-in'
  properties: {
    componentType: 'bindings.azure.servicebusqueues'
    version: 'v1'
    metadata: [
      { name: 'namespaceName', value: '${sbNamespace}.servicebus.windows.net' }
      { name: 'queueName', value: 'orders-in' }
      { name: 'direction', value: 'input, output' }
    ]
    scopes: [ 'utlxe' ]
  }
}

resource daprOrdersOut 'Microsoft.App/managedEnvironments/daprComponents@2024-03-01' = {
  parent: containerEnv
  name: 'orders-out'
  properties: {
    componentType: 'bindings.azure.servicebusqueues'
    version: 'v1'
    metadata: [
      { name: 'namespaceName', value: '${sbNamespace}.servicebus.windows.net' }
      { name: 'queueName', value: 'orders-out' }
      { name: 'direction', value: 'input, output' }
    ]
    scopes: [ 'utlxe' ]
  }
}

// ── Dapr Resiliency (pause/429 circuit breaker) ──
resource daprResiliency 'Microsoft.App/managedEnvironments/daprComponents@2024-03-01' = {
  parent: containerEnv
  name: 'utlxe-resiliency'
  properties: {
    componentType: 'resiliency.azure'
    version: 'v1'
    metadata: []
    scopes: [ 'utlxe' ]
  }
}

// ── Outputs ──
output containerAppFqdn string = utlxeApp.properties.configuration.ingress.fqdn
output serviceBusNamespace string = serviceBus.name
output storageAccount string = storageAccount.name
```

Deploy all four environments:

```bash
# Dev
az deployment group create -g rg-utlxe-dev \
  -f main.bicep -p env=dev adminKey='dev-key-123'

# Test
az deployment group create -g rg-utlxe-tst \
  -f main.bicep -p env=tst adminKey='tst-key-456'

# Acceptance
az deployment group create -g rg-utlxe-acc \
  -f main.bicep -p env=acc adminKey='acc-key-789'

# Production
az deployment group create -g rg-utlxe-prd \
  -f main.bicep -p env=prd adminKey='prd-key-secure'
```

== Terraform: Complete Example

The same stack in Terraform, using the AzureRM provider.

```hcl
variable "env" {
  description = "Environment name (dev, tst, acc, prd)"
  type        = string
}

variable "location" {
  description = "Azure region"
  type        = string
  default     = "westeurope"
}

variable "admin_key" {
  description = "UTLXe admin API key"
  type        = string
  sensitive   = true
}

variable "utlxe_image" {
  description = "UTLXe container image"
  type        = string
  default     = "ghcr.io/grauwen/utlxe:latest"
}

locals {
  prefix       = "utlxe-${var.env}"
  sb_namespace = "sb-${local.prefix}"
  storage_name = "st${replace(local.prefix, "-", "")}"
}

resource "azurerm_resource_group" "rg" {
  name     = "rg-${local.prefix}"
  location = var.location
}

# ── Log Analytics ──
resource "azurerm_log_analytics_workspace" "logs" {
  name                = "${local.prefix}-logs"
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "PerGB2018"
}

# ── Container App Environment ──
resource "azurerm_container_app_environment" "env" {
  name                       = "${local.prefix}-env"
  location                   = azurerm_resource_group.rg.location
  resource_group_name        = azurerm_resource_group.rg.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.logs.id
}

# ── Storage ──
resource "azurerm_storage_account" "storage" {
  name                     = local.storage_name
  resource_group_name      = azurerm_resource_group.rg.name
  location                 = azurerm_resource_group.rg.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
}

resource "azurerm_storage_share" "data" {
  name               = "utlxe-data"
  storage_account_id = azurerm_storage_account.storage.id
  quota              = 1
}

resource "azurerm_container_app_environment_storage" "data" {
  name                         = "utlxe-storage"
  container_app_environment_id = azurerm_container_app_environment.env.id
  account_name                 = azurerm_storage_account.storage.name
  share_name                   = azurerm_storage_share.data.name
  access_key                   = azurerm_storage_account.storage.primary_access_key
  access_mode                  = "ReadWrite"
}

# ── Service Bus ──
resource "azurerm_servicebus_namespace" "sb" {
  name                = local.sb_namespace
  location            = azurerm_resource_group.rg.location
  resource_group_name = azurerm_resource_group.rg.name
  sku                 = "Standard"
}

resource "azurerm_servicebus_queue" "orders_in" {
  name                = "orders-in"
  namespace_id        = azurerm_servicebus_namespace.sb.id
  max_delivery_count  = 100
}

resource "azurerm_servicebus_queue" "orders_out" {
  name                = "orders-out"
  namespace_id        = azurerm_servicebus_namespace.sb.id
  max_delivery_count  = 100
}

# ── Container App ──
resource "azurerm_container_app" "utlxe" {
  name                         = local.prefix
  container_app_environment_id = azurerm_container_app_environment.env.id
  resource_group_name          = azurerm_resource_group.rg.name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  dapr {
    app_id   = "utlxe"
    app_port = 8085
  }

  ingress {
    external_enabled = true
    target_port      = 8085
    transport        = "http"
  }

  secret {
    name  = "admin-key"
    value = var.admin_key
  }

  template {
    container {
      name   = "utlxe"
      image  = var.utlxe_image
      cpu    = 1.0
      memory = "2Gi"

      env {
        name        = "UTLXE_ADMIN_KEY"
        secret_name = "admin-key"
      }

      command = [
        "java", "-jar", "/utlxe/utlxe.jar",
        "--mode", "http",
        "--admin-port", "8081",
        "--http-port", "8085",
        "--data-dir", "/utlxe/data",
      ]

      volume_mounts {
        name = "data"
        path = "/utlxe/data"
      }
    }

    volume {
      name         = "data"
      storage_name = azurerm_container_app_environment_storage.data.name
      storage_type = "AzureFile"
    }

    min_replicas = 1
    max_replicas = 3
  }
}

# ── RBAC: Managed Identity → Service Bus ──
resource "azurerm_role_assignment" "sb_sender" {
  scope                = azurerm_servicebus_namespace.sb.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_container_app.utlxe.identity[0].principal_id
}

resource "azurerm_role_assignment" "sb_receiver" {
  scope                = azurerm_servicebus_namespace.sb.id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = azurerm_container_app.utlxe.identity[0].principal_id
}

# ── Deploy bundle.utlar (locked mode) ──
resource "azurerm_storage_share_file" "bundle" {
  count            = fileexists("${path.module}/bundle.utlar") ? 1 : 0
  name             = "bundle.utlar"
  storage_share_id = azurerm_storage_share.data.id
  source           = "${path.module}/bundle.utlar"
}

# ── Outputs ──
output "fqdn" {
  value = azurerm_container_app.utlxe.ingress[0].fqdn
}

output "service_bus" {
  value = azurerm_servicebus_namespace.sb.name
}
```

Deploy all environments:

```bash
# Dev (no .utlar → open mode)
terraform workspace select dev
terraform apply -var="env=dev" -var="admin_key=dev-key-123"

# Production (with .utlar → locked mode)
cp artifacts/bundle.utlar .
terraform workspace select prd
terraform apply -var="env=prd" -var="admin_key=prd-key-secure"
```

== CI/CD: Full Pipeline with Infrastructure + Bundle

A complete pipeline manages both layers:

#table(
  columns: (auto, 1fr, 1fr),
  [*Layer*], [*What changes*], [*How deployed*],
  [Infrastructure], [Container App, Service Bus, Dapr components, RBAC], [Terraform/Bicep --- changes rarely],
  [Bundle], [Transformations, schemas, messaging config], [`.utlar` to Azure Files --- changes frequently],
)

=== GitHub Actions: Infrastructure + Bundle Pipeline

```yaml
name: Full Deployment
on:
  push:
    branches: [main]

jobs:
  # ── Infrastructure (only when infra files change) ──
  infrastructure:
    if: contains(github.event.head_commit.modified, 'infra/')
    runs-on: ubuntu-latest
    strategy:
      matrix:
        env: [tst, acc]    # prd requires manual trigger
    steps:
      - uses: actions/checkout@v4
      - uses: hashicorp/setup-terraform@v3

      - name: Terraform Apply
        working-directory: infra
        env:
          ARM_CLIENT_ID: ${{ secrets.AZURE_CLIENT_ID }}
          ARM_TENANT_ID: ${{ secrets.AZURE_TENANT_ID }}
          ARM_SUBSCRIPTION_ID: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
        run: |
          terraform init
          terraform workspace select ${{ matrix.env }}
          terraform apply -auto-approve \
            -var="env=${{ matrix.env }}" \
            -var="admin_key=${{ secrets[format('ADMIN_KEY_{0}', matrix.env)] }}"

  # ── Bundle (when transformation files change) ──
  bundle:
    if: contains(github.event.head_commit.modified, 'transformations/')
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build bundle
        run: |
          cd transformations
          zip -r ../bundle.utlar manifest.json transformations/ schemas/

      - name: Deploy to Test
        run: |
          az storage file upload \
            --account-name ${{ secrets.TST_STORAGE }} \
            --share-name utlxe-data \
            --source bundle.utlar --path bundle.utlar
          az containerapp revision restart \
            -n utlxe-tst -g rg-utlxe-tst

      - name: Verify Test
        run: |
          sleep 10
          STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "X-Admin-Key: ${{ secrets.ADMIN_KEY_tst }}" \
            "https://utlxe-tst.${{ secrets.TST_ENV_FQDN }}/health/ready")
          [ "$STATUS" = "200" ] || exit 1

      - name: Deploy to Acceptance
        run: |
          az storage file upload \
            --account-name ${{ secrets.ACC_STORAGE }} \
            --share-name utlxe-data \
            --source bundle.utlar --path bundle.utlar
          az containerapp revision restart \
            -n utlxe-acc -g rg-utlxe-acc

  # ── Production (manual approval) ──
  production:
    needs: bundle
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to Production
        run: |
          az storage file upload \
            --account-name ${{ secrets.PRD_STORAGE }} \
            --share-name utlxe-data \
            --source bundle.utlar --path bundle.utlar
          az containerapp revision restart \
            -n utlxe-prd -g rg-utlxe-prd
```

=== Azure DevOps: Bicep + Bundle Pipeline

```yaml
trigger:
  branches: { include: [main] }

stages:
  - stage: Infrastructure
    condition: |
      contains(variables['Build.SourceVersionMessage'], '[infra]')
    jobs:
      - job: DeployInfra
        strategy:
          matrix:
            tst: { env: tst }
            acc: { env: acc }
        steps:
          - task: AzureCLI@2
            inputs:
              azureSubscription: 'my-service-connection'
              scriptType: bash
              inlineScript: |
                az deployment group create \
                  -g rg-utlxe-$(env) \
                  -f infra/main.bicep \
                  -p env=$(env) adminKey=$(ADMIN_KEY_$(env))

  - stage: Bundle
    condition: always()
    jobs:
      - job: DeployBundle
        steps:
          - script: |
              cd transformations
              zip -r $(Build.ArtifactStagingDirectory)/bundle.utlar \
                manifest.json transformations/ schemas/
            displayName: Build bundle

          - task: AzureCLI@2
            displayName: Deploy to Test
            inputs:
              azureSubscription: 'my-service-connection'
              scriptType: bash
              inlineScript: |
                az storage file upload \
                  --account-name $(TST_STORAGE) \
                  --share-name utlxe-data \
                  --source $(Build.ArtifactStagingDirectory)/bundle.utlar \
                  --path bundle.utlar
                az containerapp revision restart \
                  -n utlxe-tst -g rg-utlxe-tst

  - stage: Production
    condition: succeeded()
    jobs:
      - deployment: DeployProd
        environment: production    # approval gate
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
                        --account-name $(PRD_STORAGE) \
                        --share-name utlxe-data \
                        --source $(Pipeline.Workspace)/bundle.utlar \
                        --path bundle.utlar
                      az containerapp revision restart \
                        -n utlxe-prd -g rg-utlxe-prd
```

== Key Decisions

=== Terraform vs. Bicep

#table(
  columns: (auto, 1fr, 1fr),
  [*Aspect*], [*Terraform*], [*Bicep*],
  [State management], [Remote state (Azure Storage backend)], [No state --- ARM manages it],
  [Multi-cloud], [Yes --- same tool for AWS, GCP], [Azure only],
  [Dapr components], [Via `azurerm_container_app_environment_dapr_component`], [Via nested resource],
  [Workspaces/DTAP], [Terraform workspaces or tfvars], [Parameter files per environment],
  [Learning curve], [HCL syntax], [ARM-like, familiar to Azure teams],
)

Both work. Use what your team already knows.

=== Bundle Deployment: Azure Files vs Admin API

#table(
  columns: (auto, 1fr, 1fr),
  [*Method*], [*Azure Files (`.utlar`)*], [*Admin API (`POST /admin/bundle`)*],
  [Mode], [Locked (production)], [Open (dev/test)],
  [Requires restart?], [Yes --- container restarts to load `.utlar`], [No --- hot-swap, zero downtime],
  [Immutable?], [Yes --- Admin API is read-only], [No --- can be changed at any time],
  [Best for], [Acc/Prd --- CI/CD deploys artifact], [Dev/Tst --- interactive development],
)

Production uses Azure Files + `.utlar` (immutable, locked). Development uses the Admin API (dynamic, interactive). The same transformations work in both --- the deployment method differs, not the content.
