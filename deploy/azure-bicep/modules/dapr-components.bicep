// Dapr Components for Azure Container Apps
// Configures input/output bindings for Service Bus and Event Hub

@description('Container Apps Environment ID')
param environmentId string

@description('Service Bus connection string')
@secure()
param serviceBusConnection string = ''

@description('Input queue name for Service Bus')
param inputQueueName string = 'utlx-input'

@description('Output topic name for Service Bus')
param outputTopicName string = 'utlx-output'

@description('Enable Service Bus bindings')
param enableServiceBus bool = false

// ── Service Bus Input Binding ──
// Dapr reads messages from this queue and POSTs them to /api/dapr/input/servicebus-input
resource serviceBusInput 'Microsoft.App/managedEnvironments/daprComponents@2024-03-01' = if (enableServiceBus) {
  name: 'servicebus-input'
  parent: environment
  properties: {
    componentType: 'bindings.azure.servicebus.queues'
    version: 'v1'
    metadata: [
      {
        name: 'connectionString'
        secretRef: 'servicebus-connection'
      }
      {
        name: 'queueName'
        value: inputQueueName
      }
      {
        name: 'direction'
        value: 'input'
      }
      {
        name: 'maxConcurrentHandlers'
        value: '8'
      }
    ]
    secrets: [
      {
        name: 'servicebus-connection'
        value: serviceBusConnection
      }
    ]
    scopes: [
      'utlxe-app'
    ]
  }
}

// ── Service Bus Output Binding ──
// UTL-X sends transformed results to this topic via Dapr HTTP API
resource serviceBusOutput 'Microsoft.App/managedEnvironments/daprComponents@2024-03-01' = if (enableServiceBus) {
  name: 'servicebus-output'
  parent: environment
  properties: {
    componentType: 'bindings.azure.servicebus.topics'
    version: 'v1'
    metadata: [
      {
        name: 'connectionString'
        secretRef: 'servicebus-connection-out'
      }
      {
        name: 'topicName'
        value: outputTopicName
      }
      {
        name: 'direction'
        value: 'output'
      }
    ]
    secrets: [
      {
        name: 'servicebus-connection-out'
        value: serviceBusConnection
      }
    ]
    scopes: [
      'utlxe-app'
    ]
  }
}

// Reference to existing environment
resource environment 'Microsoft.App/managedEnvironments@2024-03-01' existing = {
  name: last(split(environmentId, '/'))
}
