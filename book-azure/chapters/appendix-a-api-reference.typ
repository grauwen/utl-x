= Appendix A: Admin API Reference

_Complete reference for all Admin API endpoints on port 8081 and data plane endpoints on port 8085._

== Authentication

// All /admin/* endpoints require: X-Admin-Key: {value of UTLXE_ADMIN_KEY}
// /health and /metrics do not require authentication
// /api/* endpoints on port 8085 do not require authentication

== Bundle Endpoints

// POST   /admin/bundle              Upload ZIP bundle (replaces everything)
// GET    /admin/bundle              Export current state as ZIP
// DELETE /admin/bundle              Remove all transformations and schemas
// POST   /admin/bundle/validate     Upload and validate without deploying (dry run)

== Transformation Endpoints

// GET    /admin/transformations                      List all
// GET    /admin/transformations/{name}               Details (config, status, metrics, source)
// POST   /admin/transformations/{name}               Deploy or update (.utlx required, config optional)
// POST   /admin/transformations/{name}/config        Update config only (no recompile)
// DELETE /admin/transformations/{name}               Remove

== Testing Endpoint

// POST   /admin/transformations/{name}/test          Execute with sample input (not counted in metrics)

== Operational Endpoints

// POST   /admin/transformations/{name}/pause         Stop processing (503 on data plane)
// POST   /admin/transformations/{name}/resume        Resume processing
// GET    /admin/transformations/{name}/errors         Recent errors (ring buffer, last 100)

== Validation Override Endpoints

// GET    /admin/transformations/{name}/validation    Effective validation state
// POST   /admin/transformations/{name}/validation    Set runtime override (ephemeral)
// DELETE /admin/transformations/{name}/validation    Remove override (revert to config)

== Schema Endpoints

// GET    /admin/schemas                              List all schemas
// GET    /admin/schemas/{filename}                   Download a schema file
// POST   /admin/schemas/{filename}                   Upload or replace a schema
// DELETE /admin/schemas/{filename}                   Remove a schema

== Engine Endpoints

// GET    /admin/info                                 Version, uptime, config, status
// GET    /admin/config                               Current engine configuration
// POST   /admin/config                               Update runtime-safe config fields

== Health Endpoints (no authentication)

// GET    /health                                     Status, transformation count, ready flag
// GET    /metrics                                    Prometheus metrics

== Data Plane Endpoints (port 8085, no authentication)

// GET    /api/transformations                        List available transformations (discovery)
// POST   /api/transform/{name}                       Execute transformation

== Response Codes

// 200  Success
// 400  Bad request (compilation error, invalid input)
// 403  Forbidden (missing or wrong admin key)
// 404  Transformation not found
// 503  Transformation paused
