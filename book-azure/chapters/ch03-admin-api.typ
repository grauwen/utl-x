= The Admin API

_Manage transformations, schemas, and bundles via REST on port 8081. This is the primary interface for deploying and operating UTLXe on Azure._

== Architecture: Two Ports, Two Purposes

// Port 8081: Admin API + health + metrics (internal, VNet only)
// Port 8085: Data plane (ingress, client-facing)
// Diagram showing the separation

== Authentication

// X-Admin-Key header
// UTLXE_ADMIN_KEY environment variable
// Locked by default (no key = 403 on all admin endpoints)
// Health and metrics remain unauthenticated

== Uploading Transformations

=== Single .utlx File (Simplest)

// POST /admin/transformations/{name}
// Just the source — config defaults apply
// curl example

=== With Configuration

// POST /admin/transformations/{name}
// multipart: source + config (transform.yaml)
// Strategy, validation policy, maxConcurrent

=== ZIP Bundle (Batch)

// POST /admin/bundle
// Replaces all transformations and schemas atomically
// Bundle ZIP format: schemas/ + transformations/ + engine.yaml

== Managing Schemas

// Schemas are shared resources — uploaded independently
// POST /admin/schemas/{filename}
// Referenced from .utlx headers: input json {schema: "order.xsd"}
// Update a schema without recompiling transformations

== Testing Before Go-Live

// POST /admin/transformations/{name}/test
// Send sample input, get output or error
// Not counted in Prometheus metrics
// The most important step after uploading

== Listing and Inspecting

// GET /admin/transformations — list all with status, strategy, metrics
// GET /admin/transformations/{name} — details including source
// GET /admin/schemas — list all uploaded schemas

== Updating Transformations (Hot-Swap)

// POST same name again → atomic replace
// In-flight messages drain on old version
// New messages use new version immediately
// Zero downtime

== Deleting

// DELETE /admin/transformations/{name}
// DELETE /admin/schemas/{filename}
// DELETE /admin/bundle — remove everything

== Exporting the Bundle

// GET /admin/bundle → downloadable ZIP
// Use for version control, backup, or migrating to another container

== Data Plane Discovery

// GET /api/transformations on port 8085
// No admin key required — part of the public API
// Client applications discover available transformations

== Engine Info

// GET /admin/info
// Version, uptime, transformation count, persistence mode, ready flag

== Sequence Diagram: Deploy → Test → Go Live

// Mermaid or Typst diagram showing the full flow
