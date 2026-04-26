# UTL-X GCP Deployment

Terraform module for deploying UTLXe as a Cloud Run service.

## Quick Start

```bash
# Prerequisites: gcloud CLI + Terraform
gcloud auth application-default login

# Deploy Starter tier
terraform init
terraform apply -var="project_id=my-project" -var-file=starter.tfvars

# Get the URL
terraform output service_url
```

## Deploy

```bash
# Starter (8 workers, scale to zero, max 2 instances)
terraform apply -var="project_id=my-project" -var-file=starter.tfvars

# Professional (32 workers, always-on, max 10 instances)
terraform apply -var="project_id=my-project" -var-file=professional.tfvars
```

## With Pub/Sub Messaging

```bash
terraform apply \
  -var="project_id=my-project" \
  -var-file=starter.tfvars \
  -var="enable_pubsub=true" \
  -var="input_topic=orders-in" \
  -var="output_topic=orders-out" \
  -var="transform_name=order-transform"
```

This creates:
- Input topic (`orders-in`) with push subscription to Cloud Run
- Output topic (`orders-out`) for transformed results
- Dead letter topic for failed messages
- Service account with Cloud Run invoker permissions

## Test

```bash
# Health check
curl $(terraform output -raw service_url)/api/health

# Transform
curl -X POST $(terraform output -raw service_url)/api/transform \
  -H "Content-Type: application/json" \
  -d '{
    "transformationId": "hello",
    "utlxSource": "%utlx 1.0\ninput json\noutput json\n---\n{greeting: concat(\"Hello, \", $input.name, \"!\")}",
    "payload": "{\"name\": \"World\"}",
    "strategy": "TEMPLATE"
  }'
```

## Without Terraform

```bash
gcloud run deploy utlxe \
  --image=ghcr.io/utlx-lang/utlxe:latest \
  --port=8085 \
  --cpu=1 \
  --memory=2Gi \
  --min-instances=0 \
  --max-instances=2 \
  --args="--mode,http,--workers,8" \
  --set-env-vars="JAVA_OPTS=-Xmx1536m -XX:+UseG1GC" \
  --region=europe-west1 \
  --allow-unauthenticated
```

## Clean Up

```bash
terraform destroy -var="project_id=my-project"
```
