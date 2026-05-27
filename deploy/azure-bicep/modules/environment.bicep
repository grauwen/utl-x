// Container Apps Environment + Log Analytics workspace
// The environment is the hosting platform for one or more Container Apps

@description('Azure region for all resources')
param location string

@description('Name prefix for resources')
param namePrefix string = 'utlxe'

// ── Log Analytics Workspace (required by Container Apps for logging) ──
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: '${namePrefix}-logs'
  location: location
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
  }
}

// ── Container Apps Environment ──
resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${namePrefix}-env'
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalytics.properties.customerId
        sharedKey: logAnalytics.listKeys().primarySharedKey
      }
    }
  }
}

output environmentId string = environment.id
output environmentName string = environment.name
output logAnalyticsId string = logAnalytics.id
