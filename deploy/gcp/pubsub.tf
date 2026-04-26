# UTL-X GCP — Pub/Sub Integration (optional)
#
# Creates input/output topics and a push subscription that delivers
# messages directly to UTLXe's HTTP endpoint on Cloud Run.
# No Dapr sidecar needed — Pub/Sub push is native HTTP.
#
# Enable by setting: enable_pubsub = true

variable "enable_pubsub" {
  description = "Enable Pub/Sub input/output topics with push subscription"
  type        = bool
  default     = false
}

variable "input_topic" {
  description = "Pub/Sub input topic name"
  type        = string
  default     = "utlx-input"
}

variable "output_topic" {
  description = "Pub/Sub output topic name"
  type        = string
  default     = "utlx-output"
}

variable "transform_name" {
  description = "Transformation name to invoke (maps to /api/dapr/input/{name})"
  type        = string
  default     = "default"
}

# ── Service account for Pub/Sub to invoke Cloud Run ──

resource "google_service_account" "pubsub_invoker" {
  count        = var.enable_pubsub ? 1 : 0
  project      = var.project_id
  account_id   = "${var.service_name}-pubsub"
  display_name = "UTL-X Pub/Sub Invoker"
}

resource "google_cloud_run_v2_service_iam_member" "pubsub_invoker" {
  count    = var.enable_pubsub ? 1 : 0
  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.utlxe.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.pubsub_invoker[0].email}"
}

# ── Input topic + push subscription ──

resource "google_pubsub_topic" "input" {
  count   = var.enable_pubsub ? 1 : 0
  project = var.project_id
  name    = var.input_topic
}

resource "google_pubsub_subscription" "utlxe_push" {
  count   = var.enable_pubsub ? 1 : 0
  project = var.project_id
  name    = "${var.service_name}-push"
  topic   = google_pubsub_topic.input[0].name

  push_config {
    push_endpoint = "${google_cloud_run_v2_service.utlxe.uri}/api/dapr/input/${var.transform_name}"

    oidc_token {
      service_account_email = google_service_account.pubsub_invoker[0].email
    }
  }

  ack_deadline_seconds = 30

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }

  dead_letter_policy {
    dead_letter_topic     = google_pubsub_topic.dead_letter[0].id
    max_delivery_attempts = 5
  }
}

# ── Output topic ──

resource "google_pubsub_topic" "output" {
  count   = var.enable_pubsub ? 1 : 0
  project = var.project_id
  name    = var.output_topic
}

# ── Dead letter topic ──

resource "google_pubsub_topic" "dead_letter" {
  count   = var.enable_pubsub ? 1 : 0
  project = var.project_id
  name    = "${var.input_topic}-dead-letter"
}

# ── Outputs ──

output "input_topic" {
  description = "Pub/Sub input topic (publish messages here)"
  value       = var.enable_pubsub ? google_pubsub_topic.input[0].name : null
}

output "output_topic" {
  description = "Pub/Sub output topic (transformed results)"
  value       = var.enable_pubsub ? google_pubsub_topic.output[0].name : null
}
