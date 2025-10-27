# YAML Functions for UTL-X - Analysis & Design

## Research Summary

### DataWeave YAML Support
**Findings:** DataWeave treats YAML as just another format - parse and serialize only
- `input application/yaml` - parses YAML
- `output application/yaml` - serializes YAML
- No YAML-specific functions or operations

**Conclusion:** DataWeave provides minimal YAML support - just format conversion.

---

### yq - The Industry Standard YAML Processor

**Most Common Operations (from yq):**
1. **Path queries** - `.metadata.name`, `.spec.containers[0].image`
2. **Select/Filter** - `select(.kind == "Pod")`
3. **Merge** - Merge multiple YAML files/documents
4. **Update** - `.spec.replicas = 3`
5. **Delete** - `del(.metadata.annotations)`
6. **Multi-document** - Handle `---` separated documents
7. **Recursive search** - `.. | select(.image)`
8. **Array operations** - Map, filter, reduce
9. **Kubernetes-specific** - Extract resources by kind

---

### Real-World YAML Use Cases

#### 1. **Kubernetes Manifests** (Most Common)
- Multi-resource YAML files
- Filter by kind (Pod, Service, Deployment)
- Update image tags, replicas, env vars
- Merge overlays (Kustomize-like)
- Extract by namespace/name

#### 2. **CI/CD Configurations**
- GitHub Actions, GitLab CI, CircleCI
- Merge base + environment-specific configs
- Template variable substitution
- Validation against schemas

#### 3. **Configuration Management**
- Ansible, Helm charts
- Deep merge configs
- Override patterns
- Environment-specific values

#### 4. **API Specifications**
- OpenAPI/Swagger YAML
- Extract paths, schemas
- Validate structure

#### 5. **Docker Compose**
- Merge compose files
- Override services
- Environment substitution

---

## Proposed YAML Functions for UTL-X

### Category 1: Multi-Document Operations (Critical)

YAML supports multiple documents in one file separated by `---`:
```yaml
---
apiVersion: v1
kind: Pod
metadata:
  name: pod1
---
apiVersion: v1
kind: Service
metadata:
  name: svc1
```

**Functions:**
1. `yamlSplitDocuments(yaml: String)` - Split into array of documents
2. `yamlMergeDocuments(docs: Array)` - Combine documents into one
3. `yamlGetDocument(yaml: String, index: Int)` - Get specific document

---

### Category 2: Path-Based Access (Essential)

Like yq's path queries:

**Functions:**
4. `yamlPath(yaml: UDM, path: String)` - Query using path (`.metadata.name`)
5. `yamlSet(yaml: UDM, path: String, value: Any)` - Set value at path
6. `yamlDelete(yaml: UDM, path: String)` - Delete at path
7. `yamlExists(yaml: UDM, path: String)` - Check if path exists

---

### Category 3: Deep Merge (Essential)

Critical for config management patterns:

**Functions:**
8. `yamlMerge(base: UDM, overlay: UDM)` - Deep merge objects
9. `yamlMergeAll(yamls: Array)` - Merge multiple YAMLs

---

### Category 4: Kubernetes-Specific (High Value)

Given K8s dominance in YAML usage:

**Functions:**
10. `yamlExtractByKind(yaml: UDM, kind: String)` - Get resources by kind
11. `yamlExtractByName(yaml: UDM, name: String)` - Get by metadata.name
12. `yamlFilterResources(yaml: UDM, predicate: Function)` - Filter K8s resources
13. `yamlGetResourceNames(yaml: UDM)` - List all resource names

---

### Category 5: Formatting & Utilities (Medium)

**Functions:**
14. `yamlSort(yaml: UDM)` - Sort keys alphabetically
15. `yamlMinimize(yaml: String)` - Remove unnecessary whitespace
16. `yamlIndent(yaml: String, spaces: Int)` - Change indentation

---

### Category 6: Validation (Medium)

**Functions:**
17. `yamlValidate(yaml: String)` - Check if valid YAML
18. `yamlValidateK8s(yaml: UDM)` - Validate Kubernetes resource structure

---

## Function Count Summary

| Category | Functions | Priority |
|----------|-----------|----------|
| Multi-Document | 3 | **Critical** |
| Path Operations | 4 | **Critical** |
| Merge | 2 | **Critical** |
| Kubernetes | 4 | **High** |
| Formatting | 3 | **Medium** |
| Validation | 2 | **Medium** |
| **TOTAL** | **18** | - |

---

## Comparison with CSV Functions

| Aspect | CSV Functions | YAML Functions |
|--------|---------------|----------------|
| Count | 19 functions | 18 functions |
| Primary Use | Tabular data, spreadsheets | Configs, K8s, CI/CD |
| Conflicts | 4 renamed (csv prefix) | None expected |
| Complexity | Medium | Medium-High |
| Enterprise Value | High | Very High |

---

## Design Decisions

### ✅ What to Include

1. **Multi-document** - YAML's unique feature, critical for K8s
2. **Path operations** - Core yq functionality, very common
3. **Deep merge** - Essential config pattern
4. **K8s helpers** - Represents 70%+ of YAML usage
5. **Basic formatting** - Useful utilities

### ❌ What NOT to Include

1. **Anchor/Alias manipulation** - Too low-level, rarely needed
2. **Tag handling** - Advanced, niche use case
3. **Comment preservation** - Complex, parser-dependent
4. **Flow/Block style control** - Formatting detail, not transformation logic
5. **Schema generation** - Beyond stdlib scope

### 🤔 Deferred to Future

1. **Diff/Patch** - Complex, could be separate module
2. **Template engine** - Helm-like, very complex
3. **Advanced K8s** - CRD validation, operator patterns

---

## Integration Architecture

```
YAML FILE → Format Parser → UDM → YAML Functions
                ✅ parseYaml           ↓
                                  Operations
                                      ↓
                              renderYaml ✅
                                      ↓
                                  YAML FILE
```

**Existing (SerializationFunctions):**
- `parseYaml(yaml: String)` → UDM
- `renderYaml(udm: UDM)` → String

**New (YAMLFunctions):**
- Operates on UDM structures
- Returns UDM or String as appropriate
- Uses existing parse/render for format conversion

---

## Example Use Cases

### Use Case 1: Kubernetes Multi-Resource File

```utlx
%utlx 1.0
input yaml  # k8s-resources.yaml (multi-document)
output yaml
---
{
  // Split multi-document YAML
  let docs = yamlSplitDocuments(input),
  
  // Extract all Pods
  pods: docs |> filter(d => yamlPath(d, ".kind") == "Pod"),
  
  // Update all Deployments to 3 replicas
  deployments: docs 
    |> filter(d => yamlPath(d, ".kind") == "Deployment")
    |> map(d => yamlSet(d, ".spec.replicas", 3)),
  
  // Merge back into multi-document
  updated: yamlMergeDocuments(pods ++ deployments)
}
```

### Use Case 2: Config Overlay Pattern

```utlx
%utlx 1.0
input json {
  base: "base-config.yaml",
  prod: "prod-overlay.yaml"
}
output yaml
---
{
  // Deep merge base + production overrides
  config: yamlMerge(input.base, input.prod)
}
```

### Use Case 3: Extract Kubernetes Resources

```utlx
%utlx 1.0
input yaml  # All K8s resources
output json
---
{
  // Extract by type
  pods: yamlExtractByKind(input, "Pod"),
  services: yamlExtractByKind(input, "Service"),
  
  // Get all resource names
  allNames: yamlGetResourceNames(input),
  
  // Filter by namespace
  defaultNamespace: yamlFilterResources(input) { resource =>
    yamlPath(resource, ".metadata.namespace") == "default"
  }
}
```

### Use Case 4: Embedded YAML in JSON

```utlx
%utlx 1.0
input json  # API response with YAML string
output json
---
{
  // JSON contains:
  // {
  //   "config_format": "yaml",
  //   "config_data": "apiVersion: v1\nkind: Pod..."
  // }
  
  // Parse embedded YAML
  let yamlConfig = parseYaml(input.config_data),
  
  // Extract image
  image: yamlPath(yamlConfig, ".spec.containers[0].image"),
  
  // Update replica count
  let updated = yamlSet(yamlConfig, ".spec.replicas", 5),
  
  // Serialize back
  updatedYaml: renderYaml(updated)
}
```

### Use Case 5: CI/CD Pipeline Transformation

```utlx
%utlx 1.0
input yaml  # .github/workflows/ci.yml
output yaml
---
{
  // Update all workflow jobs to use Node 20
  let jobs = yamlPath(input, ".jobs"),
  
  updatedJobs: jobs |> map(job => 
    yamlSet(job, ".steps[?(@.uses contains 'actions/setup-node')].with.node-version", "20")
  ),
  
  // Merge back
  result: yamlSet(input, ".jobs", updatedJobs)
}
```

---

## Implementation Priority

### Phase 1: Core Operations (Week 1)
- ✅ Multi-document: split, merge, get
- ✅ Path operations: yamlPath, yamlSet, yamlDelete, yamlExists
- ✅ Integration with parseYaml/renderYaml

### Phase 2: Merge & K8s (Week 2)
- ✅ Deep merge: yamlMerge, yamlMergeAll
- ✅ K8s helpers: extractByKind, extractByName, filterResources

### Phase 3: Utilities (Week 3)
- ✅ Formatting: sort, minimize, indent
- ✅ Validation: validate, validateK8s

### Phase 4: Testing & Docs (Week 4)
- ✅ Comprehensive tests
- ✅ Real-world examples
- ✅ Integration guide

---

## Naming Conventions

**No conflicts expected** - YAML functions are unique enough:
- `yamlSplitDocuments` - Clear YAML-specific
- `yamlPath` - Not like JSONPath or XPath
- `yamlMerge` - Different from array merge
- `yamlExtractByKind` - K8s-specific

**Consistent prefix:** All functions use `yaml` prefix for clarity.

---

## Dependencies

**Existing:**
- ✅ `parseYaml()` from SerializationFunctions
- ✅ `renderYaml()` from SerializationFunctions
- ✅ UDM core structure

**New:**
- Path evaluation (like JSONPath)
- Deep merge algorithm
- Multi-document parsing (may need parser enhancement)
- K8s structure knowledge

---

## Success Metrics

**Adoption:**
- Enable 80% of common YAML transformation use cases
- Support Kubernetes manifest manipulation
- Handle CI/CD config transformations
- Enable config management patterns

**Performance:**
- Path queries: O(depth) time
- Merge operations: O(n) where n = object size
- Multi-document: O(d) where d = document count

**Quality:**
- 100% test coverage
- Real-world K8s examples
- Integration with existing stdlib

---

## Next Steps

1. ✅ Review this analysis
2. Create YAMLFunctions.kt with 18 functions
3. Implement path evaluation engine
4. Add deep merge algorithm
5. Create K8s-specific helpers
6. Write comprehensive tests
7. Document with real-world examples

---

**Recommendation:** Proceed with implementation - YAML functions provide critical enterprise value, especially for DevOps and Kubernetes workflows. 🎯
