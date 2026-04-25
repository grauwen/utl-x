// UTLXe Container App
// Deploys the UTL-X transformation engine as an Azure Container App

@description('Azure region')
param location string

@description('Container Apps Environment ID')
param environmentId string

@description('Docker image to deploy')
param containerImage string = 'ghcr.io/utlx-lang/utlxe:latest'

@description('CPU cores per instance')
param cpuCores string = '1.0'

@description('Memory per instance (Gi)')
param memoryGi string = '2.0'

@description('Minimum number of instances (0 = scale to zero)')
param minReplicas int = 1

@description('Maximum number of instances')
param maxReplicas int = 5

@description('UTL-X license key')
@secure()
param licenseKey string = ''

@description('Name prefix for resources')
param namePrefix string = 'utlxe'

@description('Enable Dapr sidecar for messaging integration (Service Bus, Event Hub, etc.)')
param enableDapr bool = false

@description('Service Bus connection string (required when enableDapr + Service Bus scaler)')
@secure()
param serviceBusConnection string = ''

@description('Service Bus input queue name (for KEDA scaling)')
param inputQueueName string = 'utlx-input'

@description('Scaling mode: http, servicebus, or both')
@allowed(['http', 'servicebus', 'both'])
param scalingMode string = 'http'

@description('Worker threads for transformation processing (Starter: 8, Professional: 32, Enterprise: 64+)')
@allowed([8, 32, 64, 128])
param workers int = 8

// JVM heap set to 75% of container memory — leaves room for JVM overhead, GC, Netty buffers
var jvmHeapMb = {
  '1.0': '768'
  '2.0': '1536'
  '4.0': '3072'
  '8.0': '6144'
}
var javaOpts = '-Xmx${jvmHeapMb[memoryGi]}m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxGCPauseMillis=200'

// Service Bus KEDA scaler (only when scaling mode includes servicebus)
var serviceBusScaleRule = {
  name: 'servicebus-scale'
  custom: {
    type: 'azure-servicebus'
    metadata: {
      queueName: inputQueueName
      messageCount: '5'
      activationMessageCount: '1'
    }
    auth: [
      {
        secretRef: 'servicebus-connection'
        triggerParameter: 'connection'
      }
    ]
  }
}

var httpScaleRule = {
  name: 'http-scale'
  http: {
    metadata: {
      concurrentRequests: '10'
    }
  }
}

// Build scale rules based on scaling mode
var scaleRules = scalingMode == 'http' ? [httpScaleRule] : scalingMode == 'servicebus' ? [serviceBusScaleRule] : [httpScaleRule, serviceBusScaleRule]

// ── Container App ──
resource containerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${namePrefix}-app'
  location: location
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      // Dapr sidecar — enabled for messaging integration
      dapr: enableDapr ? {
        enabled: true
        appId: '${namePrefix}-app'
        appPort: 8085
        appProtocol: 'http'
      } : {
        enabled: false
      }
      // External ingress — accessible from the internet
      ingress: {
        external: true
        targetPort: 8085
        transport: 'http'
        allowInsecure: false
      }
      // Secrets
      secrets: concat([
        {
          name: 'license-key'
          value: licenseKey
        }
      ], enableDapr && !empty(serviceBusConnection) ? [
        {
          name: 'servicebus-connection'
          value: serviceBusConnection
        }
      ] : [])
    }
    template: {
      containers: [
        {
          name: 'utlxe'
          image: containerImage
          resources: {
            cpu: json(cpuCores)
            memory: '${memoryGi}Gi'
          }
          // Container args: set transport mode and worker count based on tier
          args: ['--mode', 'http', '--workers', string(workers)]
          env: [
            {
              name: 'JAVA_OPTS'
              value: javaOpts
            }
            {
              name: 'UTLX_LICENSE_KEY'
              secretRef: 'license-key'
            }
          ]
          // Health probes — Container Apps uses these for lifecycle management
          probes: [
            {
              type: 'Liveness'
              httpGet: {
                port: 8081
                path: '/health/live'
              }
              initialDelaySeconds: 15
              periodSeconds: 10
              failureThreshold: 3
            }
            {
              type: 'Readiness'
              httpGet: {
                port: 8081
                path: '/health/ready'
              }
              initialDelaySeconds: 10
              periodSeconds: 5
              failureThreshold: 3
            }
            {
              type: 'Startup'
              httpGet: {
                port: 8081
                path: '/health/live'
              }
              initialDelaySeconds: 5
              periodSeconds: 3
              failureThreshold: 10
            }
          ]
        }
      ]
      // Scaling rules — configured based on scalingMode parameter
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
        rules: scaleRules
      }
    }
  }
}

output containerAppUrl string = 'https://${containerApp.properties.configuration.ingress.fqdn}'
output containerAppName string = containerApp.name
