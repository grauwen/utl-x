= YAML Transformations

== YAML in UTL-X
// - YAML is a superset of JSON — same UDM mapping for most structures
// - Additional YAML features: anchors, aliases, multi-document, tags
// - Block style vs flow style output
// - How UTL-X handles YAML-specific constructs

== Reading YAML
// - Property access: same as JSON ($input.server.port)
// - Nested structures: deep dot notation
// - Arrays: indexed access and iteration
// - Multi-document: accessing individual documents
// - Anchors and aliases: resolved during parsing (transparent to transformation)

== Writing YAML
// - Block style (default): human-readable, indented
// - Flow style: compact, JSON-like inline
// - Explicit start (---) and end (...) markers
// - Unicode handling: allowUnicode option
// - Line width control

== YAML Output Options
// - {pretty: true} — block style (default)
// - {pretty: false} — flow style
// - {explicitStart: true} — add --- marker
// - {writeAttributes: true} — preserve XML attributes (when converting from XML)

== YAML-Specific Functions
// - parseYaml: parse YAML string within a transformation
// - renderYaml: serialize value to YAML string
// - Multi-document parsing and rendering

== Common YAML Patterns
// - Kubernetes manifest transformation
//   - Modify Deployment replicas, image tags, env vars
//   - Add/remove annotations and labels
//   - Merge base config + overlay (like Kustomize but in UTL-X)
// - Docker Compose file manipulation
//   - Service configuration update
//   - Environment variable injection
//   - Volume mount modification
// - OpenAPI / Swagger spec processing
//   - Extract endpoints, schemas, parameters
//   - Transform between OpenAPI 2.0 and 3.0
//   - Generate documentation from spec
// - CI/CD pipeline configuration
//   - GitHub Actions workflow generation
//   - GitLab CI pipeline transformation
//   - Azure DevOps pipeline manipulation
// - Helm values.yaml transformation
//   - Environment-specific overrides
//   - Secret injection patterns
// - Ansible playbook transformation
// - CloudFormation YAML templates

== YAML ↔ JSON Conversion
// - Lossless round-trip (YAML is superset of JSON)
// - When to use YAML vs JSON output
// - Handling YAML-specific features in JSON output (anchors lost, comments lost)
