= Cloud Deployment

== UTLXe: The Production Engine
// - utlx (CLI) vs utlxe (engine) — when to use which
// - Transport modes: HTTP, gRPC, stdio-proto, stdio-json
// - Execution strategies: TEMPLATE, COPY, COMPILED, AUTO
// - Worker threads and back-pressure

== Azure Deployment
// - Azure Container Apps (Managed Application from Marketplace)
// - Bicep/ARM templates
// - Dapr sidecar for Service Bus / Event Hub
// - KEDA auto-scaling (HTTP and queue-depth)
// - Tier-based pricing: Starter ($35) / Professional ($105) / Enterprise
// - Marketplace listing and deployment wizard

== GCP Deployment
// - Cloud Run (serverless, scale to zero)
// - Terraform module
// - Pub/Sub push (native HTTP — no Dapr needed)
// - Eventarc integration

== AWS Deployment
// - ECS/Fargate + Application Load Balancer
// - CloudFormation template
// - EventBridge / SQS integration
// - Pricing considerations (20% container fee)

== Docker and Kubernetes
// - Docker image: ghcr.io/utlx-lang/utlxe
// - Kubernetes Deployment + Service + HPA
// - Helm chart
// - Dapr sidecar on Kubernetes

== Health and Monitoring
// - /health/live and /health/ready probes
// - Prometheus metrics endpoint (:8081)
// - Key metrics: request count, latency, error rate, worker utilization
// - Integration with Grafana, Azure Monitor, Cloud Monitoring

== Hot Reload
// - Load transformations via HTTP API
// - Update without restart
// - Bundle loading at startup
// - Load balancing considerations (stateless design)
