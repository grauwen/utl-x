// UTL-X Azure Deployment — Main Template
//
// Deploys UTLXe transformation engine as an Azure Container App.
// One-click deployment for Azure Marketplace Solution Template.
//
// Deploy manually:
//   az group create -n utlxe-rg -l westeurope
//   az deployment group create -g utlxe-rg -f deploy/azure/main.bicep
//
// After deployment:
//   curl https://<output-url>/api/health

targetScope = 'resourceGroup'

// ── Parameters (customer configures at deployment time) ──

@description('Azure region for all resources')
param location string = resourceGroup().location

@description('Docker image to deploy')
param containerImage string = 'ghcr.io/utlx-lang/utlxe:latest'

@description('CPU cores per instance (0.25, 0.5, 1.0, 2.0, 4.0)')
@allowed(['0.5', '1.0', '2.0', '4.0'])
param cpuCores string = '1.0'

@description('Memory per instance in Gi (0.5, 1.0, 2.0, 4.0, 8.0)')
@allowed(['1.0', '2.0', '4.0', '8.0'])
param memoryGi string = '2.0'

@description('Minimum instances (0 = scale to zero when idle, 1 = always on)')
@minValue(0)
@maxValue(10)
param minReplicas int = 1

@description('Maximum instances for auto-scaling')
@minValue(1)
@maxValue(30)
param maxReplicas int = 5

@description('UTL-X license key')
@secure()
param licenseKey string = ''

@description('Resource name prefix')
param namePrefix string = 'utlxe'

// ── Modules ──

module environment 'modules/environment.bicep' = {
  name: 'environment'
  params: {
    location: location
    namePrefix: namePrefix
  }
}

module containerApp 'modules/container-app.bicep' = {
  name: 'container-app'
  params: {
    location: location
    environmentId: environment.outputs.environmentId
    containerImage: containerImage
    cpuCores: cpuCores
    memoryGi: memoryGi
    minReplicas: minReplicas
    maxReplicas: maxReplicas
    licenseKey: licenseKey
    namePrefix: namePrefix
  }
}

// ── Outputs ──

@description('UTL-X API endpoint URL')
output utlxeUrl string = containerApp.outputs.containerAppUrl

@description('Container App name (for CLI management)')
output containerAppName string = containerApp.outputs.containerAppName

@description('Health check URL')
output healthUrl string = '${containerApp.outputs.containerAppUrl}/api/health'

@description('Prometheus metrics URL (internal only — use port-forward or VNet)')
output metricsNote string = 'Prometheus metrics available at port 8081 inside the container (not exposed via ingress)'
