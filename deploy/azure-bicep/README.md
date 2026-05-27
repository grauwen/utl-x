# UTL-X Azure Deployment

Bicep templates for deploying UTLXe as an Azure Container App.

## What Gets Deployed

```
Azure Resource Group
├── Log Analytics Workspace (logging & monitoring)
├── Container Apps Environment (hosting platform)
└── Container App: utlxe
    ├── Port 8085: HTTP REST API (transformation)
    ├── Port 8081: Health probes + Prometheus metrics
    ├── Liveness probe: /health/live
    ├── Readiness probe: /health/ready
    └── Auto-scaling: 1-5 instances based on HTTP traffic
```

## Deploy

```bash
# Prerequisites: Azure CLI + Bicep
az login
az group create -n utlxe-rg -l westeurope

# Deploy
az deployment group create \
  -g utlxe-rg \
  -f deploy/azure/main.bicep \
  -p containerImage='ghcr.io/utlx-lang/utlxe:latest' \
     cpuCores='1.0' \
     memoryGi='2.0' \
     minReplicas=1 \
     maxReplicas=5

# Get the URL
az deployment group show -g utlxe-rg -n main --query properties.outputs.utlxeUrl.value -o tsv
```

## Test

```bash
# Health check
curl https://<url>/api/health

# Transform
curl -X POST https://<url>/api/transform \
  -H "Content-Type: application/json" \
  -d '{
    "transformationId": "hello",
    "utlxSource": "%utlx 1.0\ninput json\noutput json\n---\n{greeting: concat(\"Hello, \", $input.name, \"!\")}",
    "payload": "{\"name\": \"World\"}",
    "strategy": "TEMPLATE"
  }'
```

## Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `location` | Resource group location | Azure region |
| `containerImage` | `ghcr.io/utlx-lang/utlxe:latest` | Docker image |
| `cpuCores` | `1.0` | CPU per instance (0.5/1.0/2.0/4.0) |
| `memoryGi` | `2.0` | Memory per instance (1.0/2.0/4.0/8.0) |
| `minReplicas` | `1` | Min instances (0 = scale to zero) |
| `maxReplicas` | `5` | Max instances for auto-scaling |
| `licenseKey` | — | UTL-X license key (stored as secret) |
| `namePrefix` | `utlxe` | Prefix for all resource names |

## Clean Up

```bash
az group delete -n utlxe-rg --yes
```
