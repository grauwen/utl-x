# UTL-X Reference Deployment — Terraform Module Sketch

**Document purpose:** Give you a concrete, implementable structure for a
Terraform module that deploys the UTL-X engine + Dapr sidecar + Azure
Service Bus + Event Hubs + supporting resources as a reproducible
reference architecture. Suitable for Brainboard, plain `terraform apply`,
or any Terraform-running CI.

**Companion documents:**
- `dapr-abstract.md` — the architecture this module deploys
- `utlx-bundle-bootstrap.md` — bundle distribution patterns
- `utlx-cpi-correlation.md` — message correlation behavior

**Target providers:**
- `hashicorp/azurerm` ≥ 4.0
- `azure/azapi` ≥ 2.0 (for one specific gap — see §6)

---

## 1. Module philosophy

Three principles drive the structure below:

1. **One module = one deployable reference.** Not a generic library; not abstracted to death. A specific opinionated stack that matches `dapr-abstract.md` and can be `terraform apply`'d in 6–8 minutes.
2. **Inputs that vary per-deployment, defaults that don't.** The user supplies a name prefix and a region; everything else has a working default that produces a working deployment.
3. **`destroy` is first-class.** Every resource lives in the module's RG; `terraform destroy` is the cost-control mechanism. No external dependencies, no shared resources.

---

## 2. File layout

```
utlx-reference/
├── README.md                       Quickstart, cost estimate, gotchas
├── versions.tf                     Provider version pins
├── providers.tf                    Provider configuration
├── variables.tf                    Input variables
├── locals.tf                       Derived names, tags, conventions
├── main.tf                         Resource group, naming wiring
├── outputs.tf                      Endpoints, IDs, connection info
│
├── network.tf                      (Optional) VNet + subnet for ACA
├── identity.tf                     User-assigned managed identity + role assignments
├── keyvault.tf                     Key Vault + secrets + access policies
├── observability.tf                Log Analytics + Application Insights
│
├── messaging-servicebus.tf         Service Bus namespace + queue + auth rules
├── messaging-eventhubs.tf          Event Hubs namespace + hub + consumer group + checkpoint storage
│
├── containerapp-environment.tf     ACA environment + Dapr enablement
├── containerapp-utlx.tf            UTL-X container app + Dapr block
├── dapr-components.tf              Dapr component definitions (Service Bus, Event Hubs, Key Vault)
│
├── bundle-storage.tf               (Optional, for Pattern C from bundle-bootstrap doc)
│
└── examples/
    ├── starter/                    Minimal deployment, ~$25/month at min=0
    │   ├── main.tf
    │   └── terraform.tfvars
    └── professional/               Higher SKUs, ~$120/month
        ├── main.tf
        └── terraform.tfvars
```

The split into many small `.tf` files (rather than one big `main.tf`) is
deliberate — each file maps to a concept in `dapr-abstract.md`, so a
reader (or Brainboard's visual canvas) can navigate by concern.

---

## 3. Variables — the input surface

```hcl
# variables.tf

variable "name_prefix" {
  description = "Prefix for all resource names. Lowercase, 3-12 chars."
  type        = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,11}$", var.name_prefix))
    error_message = "name_prefix must be 3-12 lowercase alphanumeric chars, starting with a letter."
  }
}

variable "location" {
  description = "Azure region. westeurope and eastus2 are tested."
  type        = string
  default     = "westeurope"
}

variable "environment" {
  description = "Environment label for tags (test/dev/staging/prod)."
  type        = string
  default     = "test"
}

variable "owner_email" {
  description = "Email tagged on resources for ownership/cleanup."
  type        = string
}

variable "delete_after" {
  description = "ISO date for cleanup automation (informational tag)."
  type        = string
  default     = null
}

# --- Engine sizing ---

variable "utlx_image" {
  description = "Container image for the UTL-X engine."
  type        = string
  default     = "ghcr.io/glomidco/utlxe:latest"   # adjust to your registry
}

variable "utlx_cpu" {
  description = "vCPU per UTL-X instance."
  type        = number
  default     = 1.0
}

variable "utlx_memory" {
  description = "Memory per UTL-X instance, e.g. '2Gi', '4Gi'."
  type        = string
  default     = "4Gi"
}

variable "utlx_min_replicas" {
  description = "0 = scale-to-zero, 1+ = always-on."
  type        = number
  default     = 0
}

variable "utlx_max_replicas" {
  description = "Upper bound for autoscale."
  type        = number
  default     = 2
}

# --- Messaging sizing ---

variable "servicebus_sku" {
  description = "Service Bus tier: Basic, Standard, Premium."
  type        = string
  default     = "Standard"
}

variable "eventhubs_sku" {
  description = "Event Hubs tier: Basic, Standard, Premium."
  type        = string
  default     = "Standard"
}

variable "eventhubs_throughput_units" {
  description = "Throughput units for the Event Hubs namespace."
  type        = number
  default     = 1
}

# --- Optional bundle store (Pattern C) ---

variable "enable_bundle_storage" {
  description = "Provision an Azure Blob container for shared bundle distribution."
  type        = bool
  default     = false
}

# --- Network isolation (off by default for tests) ---

variable "enable_vnet_integration" {
  description = "Deploy ACA into a VNet. Adds cost and complexity; off for tests."
  type        = bool
  default     = false
}
```

**Why these specifically:** every variable here corresponds to a real
deployment-time decision someone will need to make. Nothing here is
"because Terraform best practice says you should parameterize this."

The defaults match the **starter plan** numbers from your Marketplace
preview form (1 core, 4 GiB, min=0). Set them differently in the
`examples/professional` tfvars to match the professional plan.

---

## 4. Locals — naming and tags

```hcl
# locals.tf

locals {
  # All names derived from prefix to keep them aligned.
  rg_name        = "${var.name_prefix}-rg"
  identity_name  = "${var.name_prefix}-id"
  kv_name        = substr("${var.name_prefix}kv${random_string.suffix.result}", 0, 24)
  law_name       = "${var.name_prefix}-law"
  ai_name        = "${var.name_prefix}-ai"
  sb_name        = "${var.name_prefix}-sb-${random_string.suffix.result}"
  eh_name        = "${var.name_prefix}-eh-${random_string.suffix.result}"
  storage_name   = substr("${var.name_prefix}st${random_string.suffix.result}", 0, 24)
  aca_env_name   = "${var.name_prefix}-cae"
  utlx_app_name  = "${var.name_prefix}-utlx"

  common_tags = merge({
    project      = "utlx-reference"
    environment  = var.environment
    owner        = var.owner_email
    managed_by   = "terraform"
  }, var.delete_after != null ? { delete_after = var.delete_after } : {})
}

resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
  numeric = true
}
```

The `random_string` suffix on globally-unique names (Key Vault, Service
Bus, Event Hubs, Storage) saves you from "name already taken" errors —
the common gotcha when tearing down and redeploying the same stack
multiple times. Soft-deleted Key Vaults in particular keep their names
reserved for ~90 days.

---

## 5. The eight resource concerns, mapped to files

### 5.1 `main.tf` — resource group

```hcl
resource "azurerm_resource_group" "main" {
  name     = local.rg_name
  location = var.location
  tags     = local.common_tags
}
```

That's it. Every other resource has `resource_group_name = azurerm_resource_group.main.name`.

### 5.2 `identity.tf` — managed identity + role assignments

One user-assigned managed identity carries every permission. Fewer moving
parts than per-resource system identities, and easier to audit.

```hcl
resource "azurerm_user_assigned_identity" "utlx" {
  name                = local.identity_name
  resource_group_name = azurerm_resource_group.main.name
  location            = var.location
  tags                = local.common_tags
}

# Service Bus: send + receive on the namespace
resource "azurerm_role_assignment" "sb_sender" {
  scope                = azurerm_servicebus_namespace.main.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}

resource "azurerm_role_assignment" "sb_receiver" {
  scope                = azurerm_servicebus_namespace.main.id
  role_definition_name = "Azure Service Bus Data Receiver"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}

# Event Hubs: send + receive
resource "azurerm_role_assignment" "eh_sender" {
  scope                = azurerm_eventhub_namespace.main.id
  role_definition_name = "Azure Event Hubs Data Sender"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}

resource "azurerm_role_assignment" "eh_receiver" {
  scope                = azurerm_eventhub_namespace.main.id
  role_definition_name = "Azure Event Hubs Data Receiver"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}

# Storage: blob contributor on the checkpoint container
resource "azurerm_role_assignment" "storage_blob" {
  scope                = azurerm_storage_account.checkpoints.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}

# Key Vault: read secrets
resource "azurerm_role_assignment" "kv_secrets_user" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.utlx.principal_id
}
```

### 5.3 `keyvault.tf` — secret store

```hcl
resource "azurerm_key_vault" "main" {
  name                       = local.kv_name
  location                   = var.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  enable_rbac_authorization  = true
  soft_delete_retention_days = 7
  tags                       = local.common_tags
}

data "azurerm_client_config" "current" {}
```

Note `enable_rbac_authorization = true` — RBAC roles, not legacy access
policies. Cleaner permission model and matches the role assignments in
`identity.tf`.

### 5.4 `messaging-servicebus.tf`

```hcl
resource "azurerm_servicebus_namespace" "main" {
  name                = local.sb_name
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = var.servicebus_sku
  tags                = local.common_tags
}

resource "azurerm_servicebus_queue" "orders_in" {
  name              = "utlx-orders-in"
  namespace_id      = azurerm_servicebus_namespace.main.id
  partitioning_enabled = false   # not supported on Standard tier
  max_delivery_count   = 5
  lock_duration        = "PT1M"
  default_message_ttl  = "P14D"
}

resource "azurerm_servicebus_queue" "orders_out" {
  name              = "utlx-orders-out"
  namespace_id      = azurerm_servicebus_namespace.main.id
  max_delivery_count = 5
}
```

### 5.5 `messaging-eventhubs.tf`

```hcl
resource "azurerm_eventhub_namespace" "main" {
  name                = local.eh_name
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = var.eventhubs_sku
  capacity            = var.eventhubs_throughput_units
  tags                = local.common_tags
}

resource "azurerm_eventhub" "stream" {
  name              = "utlx-stream"
  namespace_id      = azurerm_eventhub_namespace.main.id
  partition_count   = 2
  message_retention = 1
}

resource "azurerm_eventhub_consumer_group" "utlx" {
  name                = "utlx"
  namespace_name      = azurerm_eventhub_namespace.main.name
  eventhub_name       = azurerm_eventhub.stream.name
  resource_group_name = azurerm_resource_group.main.name
}

# Checkpoint store (mandatory for Event Hubs binding consumers)
resource "azurerm_storage_account" "checkpoints" {
  name                     = local.storage_name
  resource_group_name      = azurerm_resource_group.main.name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  min_tls_version          = "TLS1_2"
  tags                     = local.common_tags
}

resource "azurerm_storage_container" "checkpoints" {
  name                  = "utlx-checkpoints"
  storage_account_id    = azurerm_storage_account.checkpoints.id
  container_access_type = "private"
}
```

### 5.6 `observability.tf`

```hcl
resource "azurerm_log_analytics_workspace" "main" {
  name                = local.law_name
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.common_tags
}

resource "azurerm_application_insights" "main" {
  name                = local.ai_name
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "other"
  tags                = local.common_tags
}
```

### 5.7 `containerapp-environment.tf`

```hcl
resource "azurerm_container_app_environment" "main" {
  name                       = local.aca_env_name
  location                   = var.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = local.common_tags
}
```

The Container Apps environment is the boundary that hosts both the UTL-X
app and its Dapr components. Dapr is enabled per-app, but components live
at the environment level.

### 5.8 `containerapp-utlx.tf`

```hcl
resource "azurerm_container_app" "utlx" {
  name                         = local.utlx_app_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  tags                         = local.common_tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.utlx.id]
  }

  template {
    min_replicas = var.utlx_min_replicas
    max_replicas = var.utlx_max_replicas

    container {
      name   = "utlx"
      image  = var.utlx_image
      cpu    = var.utlx_cpu
      memory = var.utlx_memory

      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.main.connection_string
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.utlx.client_id
      }
    }
  }

  ingress {
    external_enabled = false   # internal-only; Dapr-fronted
    target_port      = 8080
    transport        = "auto"
    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }

  dapr {
    app_id       = "utlx"
    app_port     = 8080
    app_protocol = "http"
  }
}
```

`external_enabled = false` is deliberate — the UTL-X app port is the
**Dapr-facing** port from `dapr-abstract.md`. Customers reach it through
Dapr (service invocation, bindings) or through a separate ingress
gateway, not directly. If you also want the **admin API** from
`utlx-bundle-bootstrap.md` exposed, that's a second port — and the
current `azurerm_container_app` `ingress` block only supports one
external port per app, so the admin API needs either a second container
app or a sidecar pattern. Note this for now and decide later.

### 5.9 `dapr-components.tf` — the actual integration

This is the file where the two SAP / BizTalk / Dapr documents'
infrastructure recommendations meet Terraform.

```hcl
# Service Bus binding (input + output)
resource "azurerm_container_app_environment_dapr_component" "servicebus_orders_in" {
  name                         = "utlx-orders-queue"
  container_app_environment_id = azurerm_container_app_environment.main.id
  component_type               = "bindings.azure.servicebusqueues"
  version                      = "v1"
  scopes                       = ["utlx"]

  metadata {
    name  = "namespaceName"
    value = "${azurerm_servicebus_namespace.main.name}.servicebus.windows.net"
  }
  metadata {
    name  = "queueName"
    value = azurerm_servicebus_queue.orders_in.name
  }
  metadata {
    name  = "azureClientId"
    value = azurerm_user_assigned_identity.utlx.client_id
  }
  metadata {
    name  = "direction"
    value = "input,output"
  }
}

# Event Hubs binding
resource "azurerm_container_app_environment_dapr_component" "eventhubs_stream" {
  name                         = "utlx-events-hub"
  container_app_environment_id = azurerm_container_app_environment.main.id
  component_type               = "bindings.azure.eventhubs"
  version                      = "v1"
  scopes                       = ["utlx"]

  metadata {
    name  = "eventHubNamespace"
    value = azurerm_eventhub_namespace.main.name
  }
  metadata {
    name  = "eventHub"
    value = azurerm_eventhub.stream.name
  }
  metadata {
    name  = "consumerGroup"
    value = azurerm_eventhub_consumer_group.utlx.name
  }
  metadata {
    name  = "storageAccountName"
    value = azurerm_storage_account.checkpoints.name
  }
  metadata {
    name  = "storageContainerName"
    value = azurerm_storage_container.checkpoints.name
  }
  metadata {
    name  = "azureClientId"
    value = azurerm_user_assigned_identity.utlx.client_id
  }
}

# Key Vault secret store
resource "azurerm_container_app_environment_dapr_component" "keyvault" {
  name                         = "utlx-keyvault"
  container_app_environment_id = azurerm_container_app_environment.main.id
  component_type               = "secretstores.azure.keyvault"
  version                      = "v1"
  scopes                       = ["utlx"]

  metadata {
    name  = "vaultName"
    value = azurerm_key_vault.main.name
  }
  metadata {
    name  = "azureClientId"
    value = azurerm_user_assigned_identity.utlx.client_id
  }
}
```

These three components are the practical embodiment of the multi-cloud
portability story: replace the `component_type` and metadata, and the
same UTL-X engine talks to AWS / GCP / on-prem brokers without any
container change.

---

## 6. Known gotchas

Things that will cost you time if you don't know them up front.

### 6.1 Dapr `app_port` was required for a long time

Older `azurerm` versions required `app_port` even for headless apps. Fixed
in recent versions. If you hit this on Brainboard's Terraform version, the
workaround is the AzAPI provider:

```hcl
resource "azapi_update_resource" "utlx_dapr_no_app_port" {
  type      = "Microsoft.App/containerApps@2024-03-01"
  resource_id = azurerm_container_app.utlx.id
  body = jsonencode({
    properties = {
      configuration = {
        dapr = {
          appPort = null
        }
      }
    }
  })
}
```

Not needed for UTL-X (it has an app port), but mentioned because it's
the most common ACA + Dapr Terraform issue.

### 6.2 Soft-deleted resources block redeployment

Key Vault and Service Bus retain their names for 7–90 days after
deletion. Hence the `random_string` suffix in `locals.tf`. If you forget
this and reuse a fixed name across destroys, you'll see `name already
taken` errors that look mysterious until you remember soft-delete.

To purge a soft-deleted Key Vault sooner:

```bash
az keyvault purge --name <kv-name>
```

### 6.3 Role assignments take 1–5 minutes to propagate

The Container App may start before its managed identity has effective
permissions on Service Bus / Event Hubs / Key Vault. First few requests
may fail with `Forbidden`. Two options:

- Add a `time_sleep` resource between role assignments and the Container App.
- Make the Container App tolerant of startup auth retries. The Dapr sidecar already retries, so often this resolves on its own.

```hcl
resource "time_sleep" "wait_for_role_propagation" {
  depends_on      = [
    azurerm_role_assignment.sb_sender,
    azurerm_role_assignment.sb_receiver,
    azurerm_role_assignment.eh_sender,
    azurerm_role_assignment.eh_receiver,
    azurerm_role_assignment.storage_blob,
    azurerm_role_assignment.kv_secrets_user,
  ]
  create_duration = "120s"
}

# Then in containerapp-utlx.tf:
# depends_on = [time_sleep.wait_for_role_propagation]
```

### 6.4 Container Apps + private registries

If `var.utlx_image` lives in a private ACR or a private GHCR, the
Container App needs registry credentials. Add a `registry` block:

```hcl
registry {
  server   = "myregistry.azurecr.io"
  identity = azurerm_user_assigned_identity.utlx.id
}
```

And grant the identity `AcrPull` on the registry. Public images don't
need this.

### 6.5 Dapr partitioning on Service Bus Standard

Service Bus Standard tier doesn't support partitioned queues or topics in
all regions and configurations. The example above sets
`partitioning_enabled = false`. If you switch to Premium, you can enable
partitioning, but it's overkill for testing.

### 6.6 Event Hubs Basic tier doesn't support consumer groups

If you set `eventhubs_sku = "Basic"`, Terraform will fail when creating
the consumer group resource (Basic only supports `$Default`). Use
Standard or higher, or remove the consumer group resource and use
`$Default` in the Dapr component metadata.

---

## 7. Outputs

```hcl
# outputs.tf

output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "utlx_app_url" {
  value = "https://${azurerm_container_app.utlx.latest_revision_fqdn}"
}

output "servicebus_namespace_fqdn" {
  value = "${azurerm_servicebus_namespace.main.name}.servicebus.windows.net"
}

output "servicebus_queue_in" {
  value = azurerm_servicebus_queue.orders_in.name
}

output "eventhub_namespace_fqdn" {
  value = "${azurerm_eventhub_namespace.main.name}.servicebus.windows.net"
}

output "eventhub_name" {
  value = azurerm_eventhub.stream.name
}

output "key_vault_uri" {
  value = azurerm_key_vault.main.vault_uri
}

output "managed_identity_client_id" {
  value = azurerm_user_assigned_identity.utlx.client_id
}

output "log_analytics_workspace_id" {
  value = azurerm_log_analytics_workspace.main.id
}

output "estimated_monthly_cost_usd" {
  value = "Approximately $25 (min=0) to $120 (min=1, professional sizing). Verify with Azure Pricing Calculator."
}
```

The outputs are designed for use by **smoke-test scripts** — your test
harness can `terraform output -json` and feed the values into a script
that publishes a Service Bus message and verifies it transforms.

---

## 8. The smoke-test loop

Once `terraform apply` completes, this is what verifies the deployment
actually works:

```bash
#!/usr/bin/env bash
set -euo pipefail

# 1. Pull outputs
SB_FQDN=$(terraform output -raw servicebus_namespace_fqdn)
SB_QUEUE=$(terraform output -raw servicebus_queue_in)
EH_FQDN=$(terraform output -raw eventhub_namespace_fqdn)
EH_NAME=$(terraform output -raw eventhub_name)
APP_URL=$(terraform output -raw utlx_app_url)

# 2. Health check
curl -sf "${APP_URL}/healthz" || { echo "engine not healthy"; exit 1; }

# 3. Send Service Bus message via Azure CLI
az servicebus queue message send \
  --namespace-name "${SB_FQDN%%.*}" \
  --queue-name "$SB_QUEUE" \
  --body '{"sample":"payload"}'

# 4. Wait, then check Event Hubs for the transformed output
sleep 10
# (use az eventhubs ... or a small consumer script to verify)

# 5. Tear down (the entire point)
terraform destroy -auto-approve
```

This sequence — apply, smoke test, destroy — should run end-to-end in
under 15 minutes for the starter sizing. That's your CI loop and your
manual test loop.

---

## 9. What's deliberately not in this sketch

- **Networking with VNet integration.** The variable exists, but the
  implementation isn't sketched — VNet integration adds NAT gateway,
  NSGs, and DNS zones, doubling the resource count. Add when needed.
- **Custom domain + cert.** The reference deployment uses the default
  ACA-assigned FQDN. Custom domains are a separate concern.
- **Policy assignments.** Azure Policy can enforce tagging and SKU
  restrictions; useful for production, noise for a reference.
- **Backup / DR.** Out of scope for a reference deployment.
- **CI/CD integration.** The Terraform is meant to be run from
  Brainboard, a developer laptop, or a CI runner — but the CI plumbing
  itself isn't in the module.

---

## 10. How to use this in Brainboard

1. **Create a new project** on Brainboard. Pick "Import from Terraform"
   if available (saves manual canvas drawing) or start from a blank
   canvas if not.
2. **Copy the file structure from §2** into the project. Brainboard's
   visual canvas will auto-arrange the resources.
3. **Wire the references** — Brainboard usually picks up `depends_on`
   and resource references from the HCL automatically.
4. **Set the inputs** in `variables.tf` defaults or via Brainboard's
   variable editor.
5. **Generate the plan** and view the cost estimate in Brainboard's
   sidebar. Should be ~$0.80–$5/day depending on min_replicas and SKU.
6. **Apply through Brainboard's CI/CD integration**, or export the
   Terraform and run locally.

The visual canvas is genuinely useful here — the architecture in
`dapr-abstract.md` has 11 boxes and 8 arrows, and Brainboard will draw
all of them automatically once the resources are in place. Take a
screenshot of the canvas; it's the architecture diagram for the
Marketplace listing's screenshots tab.

---

## 11. Suggested first hour

If you sit down with this sketch and want a deployment by end of hour:

1. **0:00–0:10** — create the file layout, paste the variable definitions, set name_prefix to something like `utlxtest` and owner_email to your address.
2. **0:10–0:25** — paste the resource group, identity, Key Vault, Log Analytics, and App Insights blocks. `terraform init` and `terraform plan`. Fix any provider version issues.
3. **0:25–0:40** — paste Service Bus, Event Hubs, Storage. Plan again.
4. **0:40–0:50** — paste Container App environment, Container App, Dapr components. Plan one more time.
5. **0:50–1:00** — `terraform apply`. Wait ~6–8 minutes for resources to provision. Run the smoke test from §8. Run `terraform destroy`. Verify costs are zero in Cost Management 24 hours later.

That's a full deploy/test/teardown cycle for under $1. Repeat as needed
for each engine version you want to validate.

---

## 12. References

**Provider docs:**
- `azurerm_container_app` — https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/resources/container_app
- `azurerm_container_app_environment_dapr_component` — https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/resources/container_app_environment_dapr_component
- AzAPI provider — https://registry.terraform.io/providers/azure/azapi/latest/docs

**Microsoft samples:**
- container-apps-azapi-terraform — https://github.com/Azure-Samples/container-apps-azapi-terraform
- Dapr in Azure Container Apps — https://learn.microsoft.com/en-us/azure/container-apps/dapr-overview

**Companion documents:**
- `dapr-abstract.md`
- `utlx-bundle-bootstrap.md`
- `utlxe-cpi-correlation.md`

---

*Maintainer: UTL-X platform team. Update when ACA Dapr support adds
new component types relevant to UTL-X (e.g. when Tier 1 expands), or
when the `azurerm` provider changes Container App / Dapr resource
shapes.*
