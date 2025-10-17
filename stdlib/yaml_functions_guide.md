# YAML Functions for UTL-X - Complete Usage Guide

## Overview

YAML Functions provide comprehensive support for YAML transformations, with special focus on **DevOps**, **Kubernetes**, and **configuration management** use cases.

**Key Capabilities:**
- Multi-document YAML handling
- Path-based queries and updates (like `yq`)
- Deep merge for config overlays
- Kubernetes-specific helpers
- Format

ting and validation

---

## Function Categories

| Category | Functions | Use Cases |
|----------|-----------|-----------|
| **Multi-Document** | `yamlSplitDocuments`, `yamlMergeDocuments`, `yamlGetDocument` | K8s manifests, CI/CD configs |
| **Path Operations** | `yamlPath`, `yamlSet`, `yamlDelete`, `yamlExists` | Query/update YAML structures |
| **Deep Merge** | `yamlMerge`, `yamlMergeAll` | Config overlays, environment-specific settings |
| **Kubernetes** | `yamlExtractByKind`, `yamlExtractByName`, `yamlFilterResources`, `yamlGetResourceNames` | K8s resource manipulation |
| **Formatting** | `yamlSort`, `yamlMinimize`, `yamlIndent` | Normalize YAML output |
| **Validation** | `yamlValidate`, `yamlValidateK8s` | Syntax and structure checks |

**Total:** 18 functions

---

## Multi-Document Operations

### `yamlSplitDocuments(yaml: String)` - Split Multi-Document YAML

**Input:**
```yaml
---
apiVersion: v1
kind: Pod
metadata:
  name: my-pod
---
apiVersion: v1
kind: Service
metadata:
  name: my-service
```

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  documents: yamlSplitDocuments(input),
  // => [podUDM, serviceUDM]
  
  documentCount: yamlSplitDocuments(input).length
  // => 2
}
```

**Use Case:** Kubernetes multi-resource files

---

### `yamlMergeDocuments(docs: Array)` - Combine Documents

**UTL-X:**
```utlx
%utlx 1.0
input json
output yaml
---
{
  // Combine separate resources into multi-document YAML
  combined: yamlMergeDocuments([
    input.pod,
    input.service,
    input.deployment
  ])
  // => "---\napiVersion: v1\nkind: Pod\n---\n..."
}
```

**Use Case:** Generating K8s manifests from separate components

---

### `yamlGetDocument(yaml: String, index: Int)` - Extract Specific Document

**UTL-X:**
```utlx
%utlx 1.0
input yaml  # Multi-document
output json
---
{
  firstResource: yamlGetDocument(input, 0),
  secondResource: yamlGetDocument(input, 1)
}
```

---

## Path Operations

### `yamlPath(yaml: UDM, path: String)` - Query by Path

**Kubernetes Example:**
```utlx
%utlx 1.0
input yaml  # K8s Pod
output json
---
{
  // Simple paths
  podName: yamlPath(input, ".metadata.name"),
  // => "my-pod"
  
  namespace: yamlPath(input, ".metadata.namespace"),
  // => "default"
  
  // Array access
  firstContainerImage: yamlPath(input, ".spec.containers[0].image"),
  // => "nginx:latest"
  
  // Nested paths
  cpuLimit: yamlPath(input, ".spec.containers[0].resources.limits.cpu"),
  // => "500m"
}
```

**CI/CD Example:**
```utlx
%utlx 1.0
input yaml  # GitHub Actions workflow
output json
---
{
  workflowName: yamlPath(input, ".name"),
  triggerBranches: yamlPath(input, ".on.push.branches"),
  firstJobName: yamlPath(input, ".jobs.build.name")
}
```

---

### `yamlSet(yaml: UDM, path: String, value: Any)` - Update by Path

**Update Container Image:**
```utlx
%utlx 1.0
input yaml  # K8s Deployment
output yaml
---
{
  // Update image tag
  updated: yamlSet(
    input,
    ".spec.template.spec.containers[0].image",
    "nginx:1.21"
  )
}
```

**Update Replicas:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  scaledUp: yamlSet(input, ".spec.replicas", 5)
}
```

**Add Environment Variable:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  withEnv: yamlSet(
    input,
    ".spec.containers[0].env[2]",
    {
      name: "NEW_VAR",
      value: "new_value"
    }
  )
}
```

---

### `yamlDelete(yaml: UDM, path: String)` - Remove by Path

**Remove Annotations:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  cleaned: yamlDelete(input, ".metadata.annotations")
}
```

**Remove Node Selector:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  noNodeSelector: yamlDelete(input, ".spec.template.spec.nodeSelector")
}
```

---

### `yamlExists(yaml: UDM, path: String)` - Check Path Existence

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  hasLabels: yamlExists(input, ".metadata.labels"),
  hasAnnotations: yamlExists(input, ".metadata.annotations"),
  hasNodeSelector: yamlExists(input, ".spec.nodeSelector"),
  
  // Conditional logic
  ensureLabel: if (yamlExists(input, ".metadata.labels")) {
    input
  } else {
    yamlSet(input, ".metadata.labels", {})
  }
}
```

---

## Deep Merge Operations

### `yamlMerge(base: UDM, overlay: UDM)` - Config Overlay Pattern

**Base Config:**
```yaml
database:
  host: localhost
  port: 5432
  timeout: 30
  pool:
    min: 5
    max: 20
```

**Production Overlay:**
```yaml
database:
  host: prod-db.example.com
  timeout: 60
  pool:
    max: 50
  ssl: true
```

**UTL-X:**
```utlx
%utlx 1.0
input json {
  base: "base-config.yaml",
  prod: "prod-overlay.yaml"
}
output yaml
---
{
  config: yamlMerge(input.base, input.prod)
}
```

**Output:**
```yaml
database:
  host: prod-db.example.com      # From prod
  port: 5432                      # From base
  timeout: 60                     # From prod (overridden)
  pool:
    min: 5                        # From base
    max: 50                       # From prod (overridden)
  ssl: true                       # From prod (added)
```

**Key Behavior:**
- Objects are merged recursively
- Arrays are replaced (not merged)
- Overlay values win on conflicts
- New keys from overlay are added

---

### `yamlMergeAll(yamls: Array)` - Multi-Layer Merge

**Environment Cascade:**
```utlx
%utlx 1.0
input json {
  base: "base-config.yaml",
  dev: "dev-config.yaml",
  local: "local-config.yaml"
}
output yaml
---
{
  // Apply in order: base -> dev -> local
  finalConfig: yamlMergeAll([
    input.base,
    input.dev,
    input.local
  ])
}
```

**Use Case:** Development workflow with multiple config layers

---

## Kubernetes-Specific Helpers

### `yamlExtractByKind(yaml: UDM, kind: String)` - Filter by Resource Type

**UTL-X:**
```utlx
%utlx 1.0
input yaml  # k8s-manifests.yaml (multi-document)
output json
---
{
  // Extract all Pods
  pods: yamlExtractByKind(input, "Pod"),
  
  // Extract all Services
  services: yamlExtractByKind(input, "Service"),
  
  // Extract all Deployments
  deployments: yamlExtractByKind(input, "Deployment"),
  
  // Count by type
  stats: {
    podCount: yamlExtractByKind(input, "Pod").length,
    serviceCount: yamlExtractByKind(input, "Service").length
  }
}
```

---

### `yamlExtractByName(yaml: UDM, name: String)` - Find by Name

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  // Find resource by name
  myApp: yamlExtractByName(input, "my-app"),
  
  // Get first match
  appResource: yamlExtractByName(input, "my-app")[0]
}
```

---

### `yamlFilterResources(yaml: UDM, predicate: Function)` - Custom Filter

**Filter by Namespace:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  productionResources: yamlFilterResources(input) { resource =>
    yamlPath(resource, ".metadata.namespace") == "production"
  },
  
  // Resources with specific labels
  labeledResources: yamlFilterResources(input) { resource =>
    yamlPath(resource, ".metadata.labels.app") == "my-app"
  },
  
  // Pods with high CPU limits
  highCpuPods: yamlFilterResources(input) { resource =>
    yamlPath(resource, ".kind") == "Pod" &&
    parseFloat(yamlPath(resource, ".spec.containers[0].resources.limits.cpu")) > 1000
  }
}
```

---

### `yamlGetResourceNames(yaml: UDM)` - List All Names

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  allNames: yamlGetResourceNames(input),
  // => ["my-pod", "my-service", "my-deployment"]
  
  nameCount: yamlGetResourceNames(input).length
}
```

---

## Formatting Functions

### `yamlSort(yaml: UDM)` - Sort Keys Alphabetically

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  sorted: yamlSort(input)
  // Keys appear in alphabetical order
}
```

**Use Case:** Normalize YAML for version control

---

### `yamlMinimize(yaml: String)` - Remove Whitespace

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  minimized: yamlMinimize(input)
  // Compact format
}
```

---

### `yamlIndent(yaml: String, spaces: Int)` - Change Indentation

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  twoSpace: yamlIndent(input, 2),
  fourSpace: yamlIndent(input, 4)
}
```

---

## Validation Functions

### `yamlValidate(yaml: String)` - Syntax Check

**UTL-X:**
```utlx
%utlx 1.0
input json
output json
---
{
  isValid: yamlValidate(input.yamlString),
  
  // Conditional processing
  processed: if (yamlValidate(input.yamlString)) {
    parseYaml(input.yamlString)
  } else {
    { error: "Invalid YAML" }
  }
}
```

---

### `yamlValidateK8s(yaml: UDM)` - Kubernetes Structure Check

**UTL-X:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  isValidK8s: yamlValidateK8s(input),
  
  // Validate all resources
  validResources: yamlSplitDocuments(input)
    |> map(doc => yamlValidateK8s(doc))
}
```

**Checks:**
- Has `apiVersion` field
- Has `kind` field
- Has `metadata` object
- Has `metadata.name` field

---

## Real-World Examples

### Example 1: Update All Container Images

**Scenario:** Bump all container images to new version

```utlx
%utlx 1.0
input yaml  # k8s-manifests.yaml
output yaml
---
{
  // Split documents
  let docs = yamlSplitDocuments(input),
  
  // Update only Deployments
  let updated = docs |> map(doc => 
    if (yamlPath(doc, ".kind") == "Deployment") {
      // Update image tag
      yamlSet(
        doc,
        ".spec.template.spec.containers[0].image",
        "myapp:v2.0"
      )
    } else {
      doc
    }
  ),
  
  // Merge back
  result: yamlMergeDocuments(updated)
}
```

---

### Example 2: Environment-Specific Configuration

**Scenario:** Apply environment overlays

```utlx
%utlx 1.0
input json {
  base: "config.yaml",
  env: "production"
}
output yaml
---
{
  let baseConfig = input.base,
  let envOverlay = parseYaml(loadFile(`config.${input.env}.yaml`)),
  
  config: yamlMerge(baseConfig, envOverlay)
}
```

---

### Example 3: Kubernetes Resource Audit

**Scenario:** Audit K8s resources for compliance

```utlx
%utlx 1.0
input yaml
output json
---
{
  audit: {
    totalResources: yamlSplitDocuments(input).length,
    
    // Count by type
    byKind: {
      pods: yamlExtractByKind(input, "Pod").length,
      services: yamlExtractByKind(input, "Service").length,
      deployments: yamlExtractByKind(input, "Deployment").length
    },
    
    // Check for missing labels
    missingLabels: yamlFilterResources(input) { resource =>
      !yamlExists(resource, ".metadata.labels")
    },
    
    // Check for resource limits
    noResourceLimits: yamlFilterResources(input) { resource =>
      yamlPath(resource, ".kind") == "Pod" &&
      !yamlExists(resource, ".spec.containers[0].resources.limits")
    },
    
    // List all namespaces
    namespaces: yamlSplitDocuments(input)
      |> map(doc => yamlPath(doc, ".metadata.namespace"))
      |> unique()
  }
}
```

---

### Example 4: CI/CD Workflow Transformation

**Scenario:** Update GitHub Actions workflow

```utlx
%utlx 1.0
input yaml  # .github/workflows/ci.yml
output yaml
---
{
  // Update Node.js version
  let updated = yamlSet(
    input,
    ".jobs.build.steps[?(@.uses contains 'actions/setup-node')].with.node-version",
    "20"
  ),
  
  // Add caching
  let withCache = yamlSet(
    updated,
    ".jobs.build.steps[1]",
    {
      name: "Cache dependencies",
      uses: "actions/cache@v3",
      with: {
        path: "~/.npm",
        key: "${{ runner.os }}-node-${{ hashFiles('**/package-lock.json') }}"
      }
    }
  ),
  
  result: withCache
}
```

---

### Example 5: Embedded YAML in JSON API

**Scenario:** API returns YAML as string

```utlx
%utlx 1.0
input json  # API response
output json
---
{
  // API returns:
  // {
  //   "config_format": "yaml",
  //   "config_data": "apiVersion: v1\nkind: Pod\n..."
  // }
  
  // Parse embedded YAML
  let config = parseYaml(input.config_data),
  
  // Extract information
  analysis: {
    kind: yamlPath(config, ".kind"),
    name: yamlPath(config, ".metadata.name"),
    image: yamlPath(config, ".spec.containers[0].image"),
    
    // Validate
    isValid: yamlValidateK8s(config),
    
    // Transform
    withLabel: yamlSet(
      config,
      ".metadata.labels.managed-by",
      "utl-x"
    )
  },
  
  // Return updated YAML
  updatedYaml: renderYaml(analysis.withLabel)
}
```

---

### Example 6: Helm-like Templating

**Scenario:** Simple config templating

```utlx
%utlx 1.0
input json {
  template: "deployment-template.yaml",
  values: {
    appName: "my-app",
    replicas: 3,
    image: "myapp:v1.0",
    namespace: "production"
  }
}
output yaml
---
{
  let template = input.template,
  
  // Apply values
  let withName = yamlSet(template, ".metadata.name", input.values.appName),
  let withNamespace = yamlSet(withName, ".metadata.namespace", input.values.namespace),
  let withReplicas = yamlSet(withNamespace, ".spec.replicas", input.values.replicas),
  let withImage = yamlSet(withReplicas, ".spec.template.spec.containers[0].image", input.values.image),
  
  result: withImage
}
```

---

## Migration from `yq`

### yq Command → UTL-X Function

| yq Command | UTL-X Equivalent |
|------------|------------------|
| `yq '.metadata.name'` | `yamlPath(yaml, ".metadata.name")` |
| `yq '.spec.replicas = 3'` | `yamlSet(yaml, ".spec.replicas", 3)` |
| `yq 'del(.metadata.annotations)'` | `yamlDelete(yaml, ".metadata.annotations")` |
| `yq '.kind == "Pod"'` | `yamlPath(yaml, ".kind") == "Pod"` |
| `yq -n 'load("a.yaml") * load("b.yaml")'` | `yamlMerge(aYaml, bYaml)` |
| `yq ea '. as $item ireduce ({}; . * $item)' *.yml` | `yamlMergeAll(allYamls)` |

---

## Function Registration

```kotlin
// In stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt

// Multi-Document
register("yamlSplitDocuments") { args ->
    requireString(args[0], "yamlSplitDocuments")
    YAMLFunctions.yamlSplitDocuments((args[0] as UDM.String).value)
}

register("yamlMergeDocuments") { args ->
    requireArray(args[0], "yamlMergeDocuments")
    YAMLFunctions.yamlMergeDocuments(args[0] as UDM.Array)
}

register("yamlGetDocument") { args ->
    requireString(args[0], "yamlGetDocument")
    requireNumber(args[1], "yamlGetDocument")
    YAMLFunctions.yamlGetDocument(
        (args[0] as UDM.String).value,
        (args[1] as UDM.Number).value.toInt()
    )
}

// Path Operations
register("yamlPath") { args ->
    requireString(args[1], "yamlPath")
    YAMLFunctions.yamlPath(args[0], (args[1] as UDM.String).value)
}

register("yamlSet") { args ->
    requireString(args[1], "yamlSet")
    YAMLFunctions.yamlSet(args[0], (args[1] as UDM.String).value, args[2])
}

register("yamlDelete") { args ->
    requireString(args[1], "yamlDelete")
    YAMLFunctions.yamlDelete(args[0], (args[1] as UDM.String).value)
}

register("yamlExists") { args ->
    requireString(args[1], "yamlExists")
    UDM.Boolean(YAMLFunctions.yamlExists(args[0], (args[1] as UDM.String).value))
}

// Deep Merge
register("yamlMerge") { args ->
    YAMLFunctions.yamlMerge(args[0], args[1])
}

register("yamlMergeAll") { args ->
    requireArray(args[0], "yamlMergeAll")
    YAMLFunctions.yamlMergeAll(args[0] as UDM.Array)
}

// Kubernetes
register("yamlExtractByKind") { args ->
    requireString(args[1], "yamlExtractByKind")
    YAMLFunctions.yamlExtractByKind(args[0], (args[1] as UDM.String).value)
}

register("yamlExtractByName") { args ->
    requireString(args[1], "yamlExtractByName")
    YAMLFunctions.yamlExtractByName(args[0], (args[1] as UDM.String).value)
}

register("yamlFilterResources") { args ->
    // Predicate function handling
    YAMLFunctions.yamlFilterResources(args[0], args[1])
}

register("yamlGetResourceNames") { args ->
    YAMLFunctions.yamlGetResourceNames(args[0])
}

// Formatting
register("yamlSort") { args ->
    YAMLFunctions.yamlSort(args[0])
}

register("yamlMinimize") { args ->
    requireString(args[0], "yamlMinimize")
    YAMLFunctions.yamlMinimize((args[0] as UDM.String).value)
}

register("yamlIndent") { args ->
    requireString(args[0], "yamlIndent")
    requireNumber(args[1], "yamlIndent")
    YAMLFunctions.yamlIndent(
        (args[0] as UDM.String).value,
        (args[1] as UDM.Number).value.toInt()
    )
}

// Validation
register("yamlValidate") { args ->
    requireString(args[0], "yamlValidate")
    UDM.Boolean(YAMLFunctions.yamlValidate((args[0] as UDM.String).value))
}

register("yamlValidateK8s") { args ->
    UDM.Boolean(YAMLFunctions.yamlValidateK8s(args[0]))
}
```

---

## Summary

**18 YAML Functions:**
- ✅ Multi-document handling (3)
- ✅ Path operations (4) 
- ✅ Deep merge (2)
- ✅ Kubernetes helpers (4)
- ✅ Formatting (3)
- ✅ Validation (2)

**Key Differentiators:**
- More comprehensive than DataWeave (which only does parse/serialize)
- Covers most common `yq` use cases
- Kubernetes-focused (70% of YAML usage)
- Config management patterns (merge, overlay)
- DevOps/CI-CD friendly

**Enterprise Value:** Very High - enables critical DevOps and Kubernetes workflows 🎯
