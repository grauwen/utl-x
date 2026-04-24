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

@description('JVM memory options')
param javaOpts string = '-Xmx1536m -XX:+UseG1GC -XX:+UseContainerSupport'

// ── Container App ──
resource containerApp 'Microsoft.App/containerApps@2024-03-01' = {
  name: '${namePrefix}-app'
  location: location
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      // External ingress — accessible from the internet
      ingress: {
        external: true
        targetPort: 8085
        transport: 'http'
        allowInsecure: false
      }
      // Secrets
      secrets: [
        {
          name: 'license-key'
          value: licenseKey
        }
      ]
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
      // Scaling rules
      scale: {
        minReplicas: minReplicas
        maxReplicas: maxReplicas
        rules: [
          {
            name: 'http-scale'
            http: {
              metadata: {
                concurrentRequests: '10'
              }
            }
          }
        ]
      }
    }
  }
}

output containerAppUrl string = 'https://${containerApp.properties.configuration.ingress.fqdn}'
output containerAppName string = containerApp.name
