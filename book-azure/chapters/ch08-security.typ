= Security

_Network isolation, authentication, and secure configuration for production deployments._

== Network Architecture

// Port 8085 (data plane): exposed via Container App ingress
// Port 8081 (admin + health): internal to VNet only
// Dapr sidecar: communicates on localhost
// Diagram: VNet boundary, ingress, internal ports

== Admin API Authentication

// UTLXE_ADMIN_KEY environment variable
// Set via Container App secrets (encrypted at rest)
// X-Admin-Key header on every admin request
// No key set = all admin endpoints return 403 (locked by default)
// Health and metrics remain unauthenticated (needed by probes)

== Container App Secrets

// az containerapp secret set
// Reference in environment variables
// Rotated without redeployment

== VNet Integration

// Container App Environment with VNet
// Admin port (8081) accessible only within VNet
// Data plane (8085) accessible via ingress (public or internal)
// Private endpoints for Service Bus and Storage Account

== Non-Root Container

// UTLXe runs as non-root user (utlxe:utlxe)
// No elevated privileges
// Read-only filesystem except /utlxe/data/

== Secrets in Transformations

// Use env() function to access environment variables
// Never hardcode secrets in .utlx files
// Container App secrets → environment variables → env("API_KEY")
// Key Vault integration (future — F11)

== TLS / HTTPS

// Container App ingress provides TLS termination
// UTLXe itself runs HTTP (no TLS config needed)
// Internal traffic (Dapr, admin) is within the VNet — no TLS needed
