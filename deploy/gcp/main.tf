# UTL-X GCP Deployment — Cloud Run
#
# Deploys UTLXe transformation engine as a Cloud Run service.
#
# Usage:
#   terraform init
#   terraform apply -var-file=starter.tfvars     # Starter tier
#   terraform apply -var-file=professional.tfvars # Professional tier
#
# Or manually:
#   gcloud run deploy utlxe --image=ghcr.io/utlx-lang/utlxe:latest --port=8085
#
# After deployment:
#   curl https://<service-url>/api/health

terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 5.0"
    }
  }
}

# ── Variables ──

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region for Cloud Run"
  type        = string
  default     = "europe-west1"
}

variable "container_image" {
  description = "Docker image to deploy"
  type        = string
  default     = "ghcr.io/utlx-lang/utlxe:latest"
}

variable "workers" {
  description = "Worker threads (Starter: 8, Professional: 32, Enterprise: 64)"
  type        = number
  default     = 8
}

variable "cpu" {
  description = "CPU per instance (1, 2, 4)"
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory per instance (1Gi, 2Gi, 4Gi, 8Gi)"
  type        = string
  default     = "2Gi"
}

variable "min_instances" {
  description = "Minimum instances (0 = scale to zero)"
  type        = number
  default     = 0
}

variable "max_instances" {
  description = "Maximum instances for auto-scaling"
  type        = number
  default     = 2
}

variable "allow_unauthenticated" {
  description = "Allow unauthenticated access (true for public API, false for IAM-protected)"
  type        = bool
  default     = false
}

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "utlxe"
}

# ── JVM heap sizing (75% of container memory) ──

locals {
  jvm_heap_mb = {
    "1Gi" = "768"
    "2Gi" = "1536"
    "4Gi" = "3072"
    "8Gi" = "6144"
  }
  java_opts = "-Xmx${local.jvm_heap_mb[var.memory]}m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxGCPauseMillis=200"
}

# ── Cloud Run Service ──

resource "google_cloud_run_v2_service" "utlxe" {
  name     = var.service_name
  location = var.region
  project  = var.project_id

  template {
    scaling {
      min_instance_count = var.min_instances
      max_instance_count = var.max_instances
    }

    containers {
      image = var.container_image

      ports {
        container_port = 8085
      }

      args = ["--mode", "http", "--workers", tostring(var.workers)]

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
      }

      env {
        name  = "JAVA_OPTS"
        value = local.java_opts
      }

      liveness_probe {
        http_get {
          path = "/health/live"
          port = 8081
        }
        initial_delay_seconds = 15
        period_seconds        = 10
        failure_threshold     = 3
      }

      startup_probe {
        http_get {
          path = "/health/live"
          port = 8081
        }
        initial_delay_seconds = 5
        period_seconds        = 3
        failure_threshold     = 10
      }
    }
  }
}

# ── Public access (optional) ──

resource "google_cloud_run_v2_service_iam_member" "public" {
  count    = var.allow_unauthenticated ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.utlxe.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# ── Outputs ──

output "service_url" {
  description = "UTL-X API endpoint URL"
  value       = google_cloud_run_v2_service.utlxe.uri
}

output "health_url" {
  description = "Health check URL"
  value       = "${google_cloud_run_v2_service.utlxe.uri}/api/health"
}

output "service_name" {
  description = "Cloud Run service name"
  value       = google_cloud_run_v2_service.utlxe.name
}
