= Cloud Deployment

#block(
  fill: rgb("#E3F2FD"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *UTLXe engine feature.* Cloud deployment uses the UTLXe production engine, packaged as a Docker container. The CLI (`utlx`) is for development; UTLXe is for production.
]

UTLXe is designed for containerized deployment on any cloud platform. The Docker image runs on Azure Container Apps, GCP Cloud Run, AWS ECS/Fargate, or any Kubernetes cluster. This chapter covers deployment on each platform, health monitoring, and operational patterns.

== The Docker Image

UTLXe is published as a container image:

```bash
docker pull ghcr.io/utlx-lang/utlxe:latest
```

The image contains:
- UTLXe engine (JVM-based, GraalVM runtime)
- All format parsers and serializers (XML, JSON, CSV, YAML, OData + 6 schema formats)
- Full stdlib (652 functions)
- Health endpoint and Prometheus metrics

=== Transport Modes

UTLXe supports multiple transport modes for receiving messages:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Mode*], [*Flag*], [*Use case*],
  [HTTP], [`--mode http`], [REST API, Cloud Run, webhooks, Pub/Sub push],
  [gRPC], [`--mode grpc`], [High-throughput service-to-service],
  [Stdio (protobuf)], [`--mode stdio-proto`], [Dapr sidecar, Container Apps],
  [Stdio (JSON)], [`--mode stdio-json`], [Testing, simple integrations],
)

=== Basic Docker Run

```bash
docker run -p 8080:8080 \
  -v ./transforms:/transforms \
  ghcr.io/utlx-lang/utlxe:latest \
  --bundle /transforms \
  --mode http \
  --workers 8
```

This starts UTLXe in HTTP mode, loads all transformations from the `/transforms` directory, and processes messages with 8 worker threads.

== Azure Deployment

UTLXe is available on the Azure Marketplace as a Managed Application, deployable directly from the Azure Portal.

=== Azure Container Apps

The recommended Azure deployment uses Container Apps with a Dapr sidecar:

```
Azure Service Bus → Dapr Sidecar → UTLXe Container → Dapr → Target
       (queue)      (input binding)    (transform)    (output binding)
```

The Bicep template provisions:
- Container Apps Environment with Dapr enabled
- UTLXe container with configurable workers
- Dapr components for Service Bus or Event Hub bindings
- KEDA auto-scaling based on queue depth or HTTP concurrency
- Managed identity (no credentials in the container)

=== Marketplace Plans

#table(
  columns: (auto, auto, auto, auto),
  align: (left, left, left, left),
  [*Plan*], [*Price*], [*Workers*], [*Use case*],
  [Starter], [\$35/month], [Up to 4], [Development, testing, low-volume integration],
  [Professional], [\$105/month], [Up to 32], [Production workloads, multi-pipeline],
)

Pricing covers the UTLXe license. Azure compute costs (Container Apps, Service Bus) are separate and billed by Azure directly.

=== Deployment via Azure Portal

+ Search "UTL-X" in Azure Marketplace
+ Select the plan (Starter or Professional)
+ Configure: resource group, container settings, worker count
+ Deploy — the Bicep template creates all resources

=== Deployment via CLI

```bash
az deployment group create \
  --resource-group myRG \
  --template-file deploy/azure/main.bicep \
  --parameters workers=8 plan=starter
```

== GCP Deployment

=== Cloud Run

The simplest GCP deployment — serverless, scales to zero, pay per request:

```bash
gcloud run deploy utlxe \
  --image ghcr.io/utlx-lang/utlxe:latest \
  --port 8080 \
  --args="--mode,http,--bundle,/transforms,--workers,4" \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 10
```

Cloud Run handles auto-scaling, TLS, and load balancing. UTLXe's HTTP mode receives requests directly — no sidecar needed.

=== Pub/Sub Integration

GCP Pub/Sub can push messages directly to Cloud Run via HTTP:

```
Pub/Sub Topic → Push Subscription → Cloud Run (UTLXe) → Target API
```

Configure a push subscription with the Cloud Run URL as the endpoint. Each Pub/Sub message becomes an HTTP POST to UTLXe. The response is the transformed result.

=== Terraform Module

A Terraform module is included for infrastructure-as-code deployment:

```hcl
module "utlxe" {
  source = "./deploy/gcp"

  project_id = "my-project"
  region     = "europe-west1"
  workers    = 8
  plan       = "starter"
}
```

This provisions Cloud Run, IAM roles, and optional Pub/Sub integration.

== AWS Deployment

=== ECS/Fargate

AWS deployment uses ECS with Fargate (serverless containers):

```
SQS Queue → EventBridge → ECS Task (UTLXe) → Target
```

The CloudFormation template provisions:
- ECS cluster with Fargate launch type
- Task definition with UTLXe container
- Application Load Balancer (for HTTP mode)
- SQS queue and EventBridge rule (for event-driven mode)
- Auto-scaling based on queue depth or CPU utilization

=== Pricing Note

AWS adds a ~20% container overhead compared to Azure and GCP for equivalent compute. Factor this into cost planning: a \$35 Starter workload on Azure costs approximately \$44 on AWS.

== Kubernetes (Any Cloud)

For organizations running their own Kubernetes clusters:

=== Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: utlxe
spec:
  replicas: 2
  selector:
    matchLabels:
      app: utlxe
  template:
    metadata:
      labels:
        app: utlxe
    spec:
      containers:
      - name: utlxe
        image: ghcr.io/utlx-lang/utlxe:latest
        args: ["--mode", "http", "--bundle", "/transforms", "--workers", "8"]
        ports:
        - containerPort: 8080
          name: http
        - containerPort: 8081
          name: metrics
        livenessProbe:
          httpGet:
            path: /health/live
            port: 8080
          initialDelaySeconds: 5
        readinessProbe:
          httpGet:
            path: /health/ready
            port: 8080
          initialDelaySeconds: 10
        resources:
          requests:
            memory: "256Mi"
            cpu: "500m"
          limits:
            memory: "512Mi"
            cpu: "1000m"
        volumeMounts:
        - name: transforms
          mountPath: /transforms
      volumes:
      - name: transforms
        configMap:
          name: utlxe-transforms
```

=== Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: utlxe-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: utlxe
  minReplicas: 1
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

=== Dapr on Kubernetes

Add Dapr annotations for message broker integration:

```yaml
metadata:
  annotations:
    dapr.io/enabled: "true"
    dapr.io/app-id: "utlxe"
    dapr.io/app-port: "8080"
```

Dapr provides the pub/sub binding — UTLXe receives messages via Dapr, transforms them, and publishes results via Dapr. The broker (Kafka, RabbitMQ, Service Bus) is configured in Dapr components, not in UTLXe.

== Health and Monitoring

=== Health Endpoints

UTLXe exposes two health endpoints on the main port:

- `/health/live` — liveness probe: is the process alive?
- `/health/ready` — readiness probe: is the engine initialized and ready to accept messages?

Use these for Kubernetes probes, Cloud Run health checks, and load balancer health monitoring.

=== Prometheus Metrics

UTLXe exposes Prometheus metrics on port 8081:

```
# Total messages processed
utlxe_messages_processed_total{transformation="order-to-invoice"} 42531

# Failed transformations
utlxe_messages_failed_total{transformation="order-to-invoice"} 12

# Processing duration (histogram)
utlxe_transformation_duration_seconds_bucket{le="0.01"} 38000
utlxe_transformation_duration_seconds_bucket{le="0.05"} 42000
utlxe_transformation_duration_seconds_bucket{le="0.1"} 42500

# Active workers (gauge)
utlxe_active_workers 6
```

=== Grafana Dashboard

A typical UTLXe Grafana dashboard shows:
- *Throughput:* messages per second (rate of `utlxe_messages_processed_total`)
- *Error rate:* failures as percentage of total
- *Latency:* p50, p95, p99 from the duration histogram
- *Workers:* active vs total, queue depth
- *Per-transformation:* breakdown by transformation name (label)

=== Integration with Cloud Monitoring

- *Azure Monitor:* Container Apps metrics are collected automatically. Add Prometheus scraping via Azure Monitor managed Prometheus for UTLXe-specific metrics.
- *GCP Cloud Monitoring:* Cloud Run metrics are built-in. Use the Prometheus sidecar pattern or OpenTelemetry for custom metrics.
- *AWS CloudWatch:* ECS task metrics are built-in. Use the CloudWatch agent with Prometheus scraping for UTLXe metrics.

== Hot Reload

UTLXe supports loading transformations at runtime without restart:

=== Bundle Loading (Startup)

```bash
utlxe --bundle /transforms
```

All `.utlx` files and `transform.yaml` configurations in the directory are loaded at startup. This is the standard deployment pattern.

=== Dynamic Loading (Runtime)

The HTTP API accepts new transformations while the engine is running:

```bash
curl -X POST http://localhost:8080/api/load \
  -H "Content-Type: application/json" \
  -d '{"id": "new-transform", "source": "...", "config": {...}}'
```

The new transformation is parsed, compiled (if COMPILED strategy), and registered — all without affecting in-flight messages on other transformations.

=== Stateless Design

UTLXe is stateless — no data is stored in the container. Transformations are loaded from the bundle (mounted volume or ConfigMap) or via the API. This means:

- *Scale horizontally:* add more replicas, each loads the same bundle
- *Rolling updates:* replace containers one at a time, no coordination needed
- *Recovery:* a crashed container restarts and reloads the bundle automatically
- *No sticky sessions:* any replica can handle any message
