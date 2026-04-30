= YAML Transformations

YAML is the configuration language of the cloud-native era. Kubernetes manifests, Docker Compose files, GitHub Actions workflows, Helm charts, Ansible playbooks, OpenAPI specs — they're all YAML. If you work in DevOps, platform engineering, or API management, you will transform YAML.

YAML is a superset of JSON — every valid JSON document is also valid YAML. This means YAML maps to UDM the same way JSON does for most structures: objects, arrays, strings, numbers, booleans, and null. But YAML adds features that JSON lacks: anchors and aliases, multi-document streams, block vs flow styles, and comments. UTL-X handles all of these.

== YAML and UDM

Like JSON, YAML maps naturally to UDM:

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*YAML*], [*UDM*], [*Notes*],
  [Mapping (key: value)], [Object], [Key order preserved],
  [Sequence (- item)], [Array], [Direct mapping],
  [String], [Scalar (String)], [Quoted or unquoted],
  [Integer], [Scalar (Number)], [Parsed as Long],
  [Float], [Scalar (Number)], [Parsed as Double],
  [Boolean (true/false)], [Scalar (Boolean)], [Also yes/no, on/off],
  [Null (null/~)], [Null], [Also empty value],
  [Timestamp], [DateTime], [Auto-parsed by default],
)

=== Anchors and Aliases

YAML anchors (`&name`) and aliases (`*name`) are resolved transparently during parsing. By the time your transformation sees the data, aliases have been replaced with the actual values:

```yaml
defaults: &defaults
  timeout: 30
  retries: 3

production:
  <<: *defaults
  timeout: 60
```

In your transformation, `$input.production` is a plain object with `timeout: 60` and `retries: 3`. The anchor mechanism is invisible — you work with the resolved data.

=== Multi-Document Streams

YAML supports multiple documents in one stream, separated by `---`:

```yaml
---
name: Alice
role: admin
---
name: Bob
role: user
```

UTL-X parses multi-document YAML as an array of documents. Use the `yamlSplitDocuments` function or the multi-document parser option to access individual documents:

```utlx
let docs = yamlSplitDocuments($input)
docs[0].name    // "Alice"
docs[1].role    // "user"
```

== Reading YAML

YAML input works identically to JSON — dot notation, bracket notation, safe navigation:

```utlx
$input.server.port              // 8080
$input.database.credentials.password   // nested access
$input.services[0].name         // first service name
$input.features?.experimental?.enabled ?? false  // safe with default
```

=== Timestamps

YAML has native timestamp support. Values like `2026-04-30` and `2026-04-30T14:30:00Z` are automatically parsed as date/time values — unlike JSON where dates are always strings:

```yaml
created: 2026-04-30
updated: 2026-04-30T14:30:00Z
```

```utlx
$input.created    // already a date — no parseDate() needed
$input.updated    // already a datetime
```

This auto-parsing can occasionally be surprising — a value like `2024-01-01` that you intended as a string becomes a date. If this causes issues, the parser can be configured to disable timestamp parsing.

=== Boolean Variants

YAML is more liberal with booleans than JSON. All of these are parsed as boolean `true`: `true`, `True`, `TRUE`, `yes`, `Yes`, `YES`, `on`, `On`, `ON`. And for false: `false`, `False`, `FALSE`, `no`, `No`, `NO`, `off`, `Off`, `OFF`.

This matters when processing configuration files that use `yes/no` or `on/off` conventions — they arrive as booleans in your transformation, not strings.

== Writing YAML

=== Block Style (Default)

UTL-X outputs YAML in block style by default — human-readable, indented with 2 spaces:

```utlx
%utlx 1.0
input json
output yaml
---
{
  server: $input.hostname,
  port: $input.port,
  database: {
    host: $input.db.host,
    name: $input.db.name,
    credentials: {
      username: $input.db.user,
      password: $input.db.pass
    }
  }
}
```

Output:

```yaml
server: prod-01
port: 8080
database:
  host: db.internal
  name: myapp
  credentials:
    username: admin
    password: secret123
```

=== Number Formatting

Whole numbers are output without a decimal point — `42`, not `42.0`. This matches YAML conventions and prevents downstream tools from interpreting integers as floats.

=== XML Attributes in YAML

When converting XML to YAML, attributes use the `@` prefix — the same convention as JSON output:

```yaml
'@id': ORD-001
Customer: Alice
Total: 299.99
```

With `writeAttributes: true`, leaf elements with attributes preserve both:

```yaml
Total:
  '@currency': EUR
  '#text': 299.99
```

See Chapter 22 for the full attribute handling design.

== YAML Output Options

```utlx
output yaml                              // block style (default)
output yaml {writeAttributes: true}      // preserve XML attributes in output
```

The `writeAttributes` option is the only YAML-specific output option currently wired from the header. The serializer uses sensible defaults: 2-space indent, 80-character line width, Unix line breaks, block style, Unicode allowed.

== YAML-Specific Functions

=== parseYaml

Parse a YAML string embedded within a transformation:

```utlx
let config = parseYaml(yamlString)
config.server.port    // 8080
```

Use this when YAML is a _value_ inside your data — a YAML string in a JSON field, or YAML embedded in an API response. For normal YAML file processing, use `input yaml` in the header.

=== Multi-Document Functions

```utlx
// Split a multi-document YAML stream into individual documents
let docs = yamlSplitDocuments(yamlString)

// Get a specific document by index
let second = yamlGetDocument(yamlString, 1)

// Merge multiple documents back into a stream
let stream = yamlMergeDocuments(docs)
```

=== Query and Modify Functions

```utlx
// Query a path in a YAML structure
yamlPath($input, "database.host")

// Set a value at a path (returns new structure)
yamlSet($input, "database.port", 5433)

// Delete a path (returns new structure)
yamlDelete($input, "database.credentials.password")

// Check if a path exists
yamlExists($input, "features.experimental")

// Get all keys or values
yamlKeys($input.database)       // ["host", "name", "port", ...]
yamlValues($input.database)     // ["db.internal", "myapp", 5432, ...]
```

=== Deep Merge

Merge two YAML structures, with the second overriding the first — useful for configuration overlays:

```utlx
let base = parseYaml(baseConfig)
let overlay = parseYaml(envConfig)
let merged = yamlDeepMerge(base, overlay)
```

This is the UTL-X equivalent of Helm's values merging or Kustomize's strategic merge — but as a function you control in your transformation.

== Common YAML Patterns

=== Kubernetes Manifest Transformation

Update a Deployment's image tag and replica count:

```utlx
%utlx 1.0
input yaml
output yaml
---
{
  ...$input,
  spec: {
    ...$input.spec,
    replicas: 3,
    template: {
      ...$input.spec.template,
      spec: {
        ...$input.spec.template.spec,
        containers: map($input.spec.template.spec.containers, (c) -> {
          ...c,
          image: if (c.name == "app") "myapp:v2.1.0" else c.image
        })
      }
    }
  }
}
```

The spread operator (`...`) copies all existing fields and lets you override specific ones — essential for YAML transformations where you want to change one field without listing all the others.

=== Docker Compose Environment Injection

Add environment variables to a service:

```utlx
%utlx 1.0
input yaml
output yaml
---
{
  ...$input,
  services: {
    ...$input.services,
    app: {
      ...$input.services.app,
      environment: {
        ...$input.services.app.environment,
        DATABASE_URL: "postgres://prod-db:5432/myapp",
        LOG_LEVEL: "warn"
      }
    }
  }
}
```

=== Configuration File Migration (JSON to YAML)

Convert a JSON config to YAML — often needed when migrating tools:

```utlx
%utlx 1.0
input json
output yaml
---
$input
```

A pass-through transformation is all you need. JSON is a subset of YAML, so every JSON structure converts to valid YAML. The YAML output is more readable — no quotes around keys, indentation instead of braces.

=== OpenAPI Spec Processing

Extract all endpoints from an OpenAPI 3.0 spec:

```utlx
%utlx 1.0
input yaml
output json
---
flatten(map(keys($input.paths), (path) ->
  map(keys($input.paths[path]), (method) -> {
    let op = $input.paths[path][method]
    path: path,
    method: toUpperCase(method),
    operationId: op.operationId,
    summary: op.summary ?? "",
    tags: op.tags ?? []
  })
))
```

=== Helm Values Override

Generate environment-specific Helm values from a base config:

```utlx
%utlx 1.0
input: base yaml, env yaml
output yaml
---
yamlDeepMerge($base, $env)
```

The base `values.yaml` provides defaults. The environment overlay (`production.yaml`) overrides specific values. `yamlDeepMerge` handles nested structures — it merges recursively, not just at the top level.

== YAML vs JSON: When to Use Which

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Use YAML when*], [*Use JSON when*],
  [Human readability matters (configs, docs)], [Machine consumption (APIs, data exchange)],
  [Comments are needed], [Strict parsing is required],
  [Deep nesting (YAML indentation is cleaner)], [Interop with JavaScript/web tooling],
  [Multi-document streams], [Single-document payloads],
  [Kubernetes, Helm, Ansible, CI/CD], [REST APIs, message queues, databases],
)

UTL-X treats both as first-class formats. Converting between them is trivial — the UDM is the same. The only loss going YAML to JSON: comments and anchors disappear (JSON has neither). Going JSON to YAML: lossless — YAML is a superset.
