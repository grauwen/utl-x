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

@description('Enable Dapr sidecar for Service Bus / Event Hub messaging')
param enableDapr bool = false

@description('Service Bus connection string (required when enableDapr is true)')
@secure()
param serviceBusConnection string = ''

@description('Input queue name for Service Bus')
param inputQueueName string = 'utlx-input'

@description('Output topic name for Service Bus')
param outputTopicName string = 'utlx-output'

@description('Scaling mode: http (default), servicebus, or both')
@allowed(['http', 'servicebus', 'both'])
param scalingMode string = 'http'

@description('Worker threads for transformation processing (determines tier: Starter=8, Professional=32, Enterprise=64+)')
@allowed([8, 32, 64, 128])
param workers int = 8

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
    enableDapr: enableDapr
    serviceBusConnection: serviceBusConnection
    inputQueueName: inputQueueName
    scalingMode: scalingMode
    workers: workers
  }
}

// Dapr components (Service Bus bindings) — only deployed when Dapr is enabled
module daprComponents 'modules/dapr-components.bicep' = if (enableDapr) {
  name: 'dapr-components'
  params: {
    environmentId: environment.outputs.environmentId
    serviceBusConnection: serviceBusConnection
    inputQueueName: inputQueueName
    outputTopicName: outputTopicName
    enableServiceBus: enableDapr && scalingMode != 'http'
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
