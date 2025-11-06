# Study Guide: YAML Metadata Definition in UTL-X 1.0
## Using %USDL 1.0 (Universal Schema Definition Language)

**Author Context**: Marcel A. Grauwen (UTL-X Project)  
**Version**: 1.0 Draft  
**Date**: November 2025

---

## Executive Summary

**Context**: UTL-X 1.0 already has **%USDL (Universal Schema Definition Language)** working successfully for `jsch` (JSON Schema), `xsd` (XML Schema), `avro` (Apache Avro), and `tsch` (Table Schema for CSV). USDL serves as a format-agnostic metadata definition layer.

**The YAML Challenge**: Unlike CSV, YAML **already has an established metadata solution**: JSON Schema. Because YAML is a superset of JSON and can be seamlessly converted to JSON without loss of information, the industry standard is to use JSON Schema for YAML validation.

**Recommendation**: Use **`jsch`** (JSON Schema) for YAML metadata in UTL-X, treating YAML as an alternative serialization of JSON data structures.

**Why JSON Schema for YAML?**
1. **Industry standard**: Universally accepted approach for YAML validation
2. **Lossless conversion**: YAML → JSON conversion is trivial and error-free
3. **Existing infrastructure**: Leverages USDL's existing `jsch` support
4. **Tool ecosystem**: Extensive IDE support (VS Code Red Hat YAML extension, etc.)
5. **No reinvention needed**: No need to create a separate YAML-specific schema language

**Type Identifier**: **`jsch`** (same as JSON) - YAML uses identical schemas as JSON

---

## Table of Contents

1. [Background: YAML and Metadata](#1-background-yaml-and-metadata)
2. [Why JSON Schema is the Standard for YAML](#2-why-json-schema-is-the-standard-for-yaml)
3. [USDL Integration for YAML](#3-usdl-integration-for-yaml)
4. [JSON Schema for YAML Structures](#4-json-schema-for-yaml-structures)
5. [Integration Patterns](#5-integration-patterns)
6. [Implementation Considerations](#6-implementation-considerations)
7. [Examples and Use Cases](#7-examples-and-use-cases)
8. [Alternative Approaches (Not Recommended)](#8-alternative-approaches-not-recommended)
9. [Conclusion](#9-conclusion)

---

## 1. Background: YAML and Metadata

### 1.1 What is YAML?

**YAML** (YAML Ain't Markup Language) is a human-friendly data serialization language designed for configuration files and data exchange.

**Key Characteristics**:
- Human-readable syntax with significant whitespace
- Superset of JSON (most JSON is valid YAML)
- Support for comments, anchors, and references
- Native support for complex data types

**Common Use Cases**:
- Configuration files (Kubernetes, Docker Compose, CI/CD)
- Data serialization between services
- Infrastructure as Code (Ansible, CloudFormation)
- Application settings and manifests

### 1.2 The YAML-JSON Relationship

**Critical Insight**: YAML and JSON share the same data model - they're different serializations of the same structure.

```yaml
# YAML representation
person:
  name: John Doe
  age: 30
  active: true
  hobbies:
    - reading
    - hiking
```

**Equivalent JSON**:
```json
{
  "person": {
    "name": "John Doe",
    "age": 30,
    "active": true,
    "hobbies": ["reading", "hiking"]
  }
}
```

**Key Point**: Both represent identical tree structures, making JSON Schema applicable to both formats.

### 1.3 YAML Schema Validation Landscape

**Historical Context**:
- Early attempts: Kwalify (abandoned ~2015), Rx (experimental)
- Current reality: JSON Schema has become the de facto standard
- Tool support: All major YAML validators use JSON Schema

**Why No Native YAML Schema Language?**
1. YAML's data model maps perfectly to JSON
2. JSON Schema already handles all validation needs
3. No loss of information in YAML → JSON conversion
4. Industry consolidation around JSON Schema

---

## 2. Why JSON Schema is the Standard for YAML

### 2.1 Technical Reasons

**Lossless Conversion**:
```
YAML File → YAML Parser → Abstract Tree → JSON Schema Validator → Results
```

The abstract tree representation is identical whether parsed from YAML or JSON, making JSON Schema validation work seamlessly.

**Data Type Mapping**:
| YAML Type | JSON Type | JSON Schema Type |
|-----------|-----------|------------------|
| Scalar (string) | string | "string" |
| Scalar (number) | number | "number" or "integer" |
| Scalar (boolean) | boolean | "boolean" |
| Scalar (null) | null | "null" |
| Sequence | array | "array" |
| Mapping | object | "object" |

### 2.2 Industry Adoption

**Tool Ecosystem**:
- **VS Code**: Red Hat YAML extension with JSON Schema support
- **JetBrains IDEs**: Built-in JSON Schema validation for YAML
- **CLI Tools**: `yajsv`, `ajv-cli`, `pajv` (all use JSON Schema)
- **CI/CD**: GitHub Actions, GitLab CI (JSON Schema based)

**Schema Store Integration**:
- [schemastore.org](https://schemastore.org) provides 600+ JSON Schemas
- Automatic schema detection for common YAML files
- Examples: Kubernetes manifests, GitHub Actions, Docker Compose

**Production Usage**:
- Kubernetes: Uses JSON Schema for API validation
- OpenAPI/Swagger: JSON Schema for YAML specifications
- Ansible: JSON Schema for playbook validation
- Cloud providers: AWS CloudFormation, Azure ARM templates

### 2.3 Advantages Over Custom YAML Schema

**Standardization**:
- ✅ One schema language for JSON and YAML
- ✅ Extensive documentation and examples
- ✅ Active maintenance and evolution

**Tooling**:
- ✅ IDE autocomplete and validation
- ✅ Command-line validators
- ✅ Library support in all major languages

**Ecosystem**:
- ✅ Reusable schemas from SchemaStore
- ✅ Schema composition and $ref support
- ✅ Format validators (email, uri, date, etc.)

---

## 3. USDL Integration for YAML

### 3.1 USDL Architecture with YAML

UTL-X already supports JSON Schema (`jsch`) for JSON files. The same infrastructure works for YAML:

```
YAML File + jsch (JSON Schema) → USDL Internal Representation → UTL-X Type System
JSON File + jsch (JSON Schema) → USDL Internal Representation → UTL-X Type System
```

**Key Point**: Both YAML and JSON use the **same schema type** (`jsch`) because they share the same data model.

### 3.2 Format vs. Schema Separation

**Understanding the Distinction**:

| Aspect | Format | Schema |
|--------|--------|--------|
| **Purpose** | Serialization syntax | Data validation |
| **YAML** | How data is written | JSON Schema |
| **JSON** | How data is written | JSON Schema |
| **CSV** | How data is written | Table Schema (`tsch`) |
| **XML** | How data is written | XML Schema (`xsd`) |

**In UTL-X**:
- `input yaml` = Parse YAML syntax
- `schema file.json type:jsch` = Validate using JSON Schema
- Result: Validated, type-safe data in UDM

### 3.3 Header Directive Usage

**Basic YAML transformation with schema**:
```utlx
%utlx 1.0
input yaml
output json
schema config-schema.json type:jsch
---
// Transformation with validated YAML input
{
  processedConfig: {
    appName: $input.application.name,
    version: $input.application.version,
    features: $input.features |> filter(f => f.enabled)
  }
}
```

**Auto-detection**:
```utlx
%utlx 1.0
input yaml
output json
schema config-schema.json    // Auto-detects as jsch
---
// Same transformation
```

### 3.4 YAML-Specific Considerations in USDL

**Advanced YAML Features**:

While JSON Schema validates the data structure, some YAML-specific features need handling:

1. **Anchors and Aliases**: Resolved during parsing, invisible to schema
2. **Multi-document streams**: May require special handling
3. **Custom tags**: Should be resolved before validation

**Example - YAML anchors**:
```yaml
# input.yaml
defaults: &defaults
  timeout: 30
  retries: 3

service1:
  <<: *defaults
  name: api-service

service2:
  <<: *defaults
  name: worker-service
```

**After parsing** (what JSON Schema sees):
```json
{
  "defaults": {"timeout": 30, "retries": 3},
  "service1": {"timeout": 30, "retries": 3, "name": "api-service"},
  "service2": {"timeout": 30, "retries": 3, "name": "worker-service"}
}
```

---

## 4. JSON Schema for YAML Structures

### 4.1 Basic Schema Example

**YAML Configuration File**:
```yaml
# config.yaml
application:
  name: MyApp
  version: 1.2.0
  debug: false

database:
  host: localhost
  port: 5432
  username: dbuser

features:
  - name: feature1
    enabled: true
  - name: feature2
    enabled: false
```

**JSON Schema** (config-schema.json):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/config-schema.json",
  "title": "Application Configuration",
  "description": "Schema for application configuration file",
  "type": "object",
  "required": ["application", "database"],
  "properties": {
    "application": {
      "type": "object",
      "required": ["name", "version"],
      "properties": {
        "name": {
          "type": "string",
          "minLength": 1,
          "description": "Application name"
        },
        "version": {
          "type": "string",
          "pattern": "^\\d+\\.\\d+\\.\\d+$",
          "description": "Semantic version"
        },
        "debug": {
          "type": "boolean",
          "default": false
        }
      }
    },
    "database": {
      "type": "object",
      "required": ["host", "port", "username"],
      "properties": {
        "host": {
          "type": "string",
          "format": "hostname"
        },
        "port": {
          "type": "integer",
          "minimum": 1,
          "maximum": 65535
        },
        "username": {
          "type": "string",
          "minLength": 1
        },
        "password": {
          "type": "string",
          "description": "Optional password"
        }
      }
    },
    "features": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["name", "enabled"],
        "properties": {
          "name": {
            "type": "string"
          },
          "enabled": {
            "type": "boolean"
          }
        }
      }
    }
  }
}
```

### 4.2 Complex Schema Patterns

**Schema Composition with $ref**:
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "definitions": {
    "service": {
      "type": "object",
      "required": ["name", "port"],
      "properties": {
        "name": {"type": "string"},
        "port": {"type": "integer", "minimum": 1, "maximum": 65535},
        "replicas": {"type": "integer", "minimum": 1, "default": 1}
      }
    }
  },
  "type": "object",
  "properties": {
    "services": {
      "type": "array",
      "items": {"$ref": "#/definitions/service"}
    }
  }
}
```

**Validates this YAML**:
```yaml
services:
  - name: web
    port: 8080
    replicas: 3
  - name: api
    port: 3000
    replicas: 2
```

### 4.3 Common Validation Patterns

**Enum Validation**:
```json
{
  "logLevel": {
    "type": "string",
    "enum": ["DEBUG", "INFO", "WARN", "ERROR"],
    "description": "Logging level"
  }
}
```

**Pattern Validation**:
```json
{
  "email": {
    "type": "string",
    "format": "email"
  },
  "ipAddress": {
    "type": "string",
    "format": "ipv4"
  },
  "url": {
    "type": "string",
    "format": "uri"
  }
}
```

**Conditional Validation**:
```json
{
  "type": "object",
  "properties": {
    "protocol": {
      "type": "string",
      "enum": ["http", "https"]
    },
    "tlsCert": {
      "type": "string"
    }
  },
  "if": {
    "properties": {"protocol": {"const": "https"}}
  },
  "then": {
    "required": ["tlsCert"]
  }
}
```

---

## 5. Integration Patterns

### 5.1 External Schema Files

**Directory Structure**:
```
project/
├── schemas/
│   ├── app-config-schema.json      # jsch (JSON Schema)
│   ├── deployment-schema.json      # jsch
│   └── pipeline-schema.json        # jsch
├── transforms/
│   ├── config_processor.utlx
│   ├── deployment_generator.utlx
│   └── pipeline_validator.utlx
└── data/
    ├── config.yaml
    ├── deployment.yaml
    └── pipeline.yaml
```

**Transformation Example**:
```utlx
%utlx 1.0
input yaml
output json
schema ../schemas/app-config-schema.json type:jsch
---
{
  config: {
    name: $input.application.name,
    version: $input.application.version,
    database: {
      connectionString: "postgresql://" + 
        $input.database.host + ":" + 
        $input.database.port
    },
    enabledFeatures: $input.features 
      |> filter(f => f.enabled) 
      |> map(f => f.name)
  }
}
```

### 5.2 Schema Store Integration

**Leveraging SchemaStore.org**:

Many common YAML file types have schemas already available:

```utlx
%utlx 1.0
input yaml
output json
schema "https://json.schemastore.org/github-workflow.json" type:jsch
---
// Transform GitHub Actions workflow
{
  workflowSummary: {
    name: $input.name,
    triggers: $input.on |> keys(),
    jobs: $input.jobs |> keys(),
    totalSteps: sum($input.jobs.* |> map(job => count(job.steps)))
  }
}
```

**Common SchemaStore Schemas for YAML**:
- GitHub Actions: `github-workflow.json`
- Docker Compose: `docker-compose.json`
- Kubernetes: Various schemas by resource type
- Ansible: `ansible-playbook.json`
- Azure Pipelines: `azure-pipelines.json`

### 5.3 Multi-Document YAML Streams

**YAML Multi-Document**:
```yaml
# multi-doc.yaml
---
apiVersion: v1
kind: Service
metadata:
  name: myapp-service
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp-deployment
```

**UTL-X Handling**:
```utlx
%utlx 1.0
input yaml-stream    // Multi-document YAML
output json
schema k8s-resource-schema.json type:jsch
---
{
  resources: $input.documents |> map(doc => {
    kind: doc.kind,
    name: doc.metadata.name,
    apiVersion: doc.apiVersion
  })
}
```

### 5.4 Schema Generation for YAML

**Generating JSON Schema from YAML examples**:

```bash
# UTL-X CLI feature (proposed)
utlx describe config.yaml --output config-schema.json --format jsch

# With inference options
utlx describe config.yaml \
  --output config-schema.json \
  --format jsch \
  --infer-types \
  --require-all \
  --sample-size 100
```

**Generated Schema** (example):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$comment": "Auto-generated from config.yaml",
  "type": "object",
  "required": ["application", "database"],
  "properties": {
    "application": {
      "type": "object",
      "properties": {
        "name": {"type": "string"},
        "version": {"type": "string", "pattern": "^\\d+\\.\\d+\\.\\d+$"}
      }
    }
  }
}
```

---

## 6. Implementation Considerations

### 6.1 YAML Parser Integration

**USDL must handle YAML-specific parsing before validation**:

```kotlin
// Pseudo-code for YAML processing in USDL

fun parseYAMLWithSchema(
    yamlFile: File, 
    schema: JsonSchema
): UdmValue {
    // 1. Parse YAML to abstract tree (resolving anchors, aliases)
    val yamlTree = yamlParser.parse(yamlFile)
    
    // 2. Convert to JSON-compatible structure
    val jsonTree = yamlToJsonConverter.convert(yamlTree)
    
    // 3. Validate against JSON Schema
    val validationResult = schema.validate(jsonTree)
    if (!validationResult.isValid) {
        throw ValidationException(validationResult.errors)
    }
    
    // 4. Convert to UDM
    return udmConverter.fromJsonTree(jsonTree)
}
```

### 6.2 Advanced YAML Features

**Handling Special Cases**:

1. **YAML Tags** (e.g., `!!python/object`):
   - Strip or resolve before validation
   - May indicate incompatibility with JSON Schema

2. **Binary Data** (!!binary):
   - Convert to base64 string for validation
   - Handle appropriately in UDM

3. **Timestamps** (!!timestamp):
   - Parse to ISO 8601 format
   - Validate as date-time strings

4. **Merge Keys** (<<):
   - Resolve during parsing
   - Transparent to schema validation

### 6.3 Error Reporting

**Map JSON Schema errors back to YAML source**:

```
Validation Error:
  File: config.yaml
  Line: 15
  Path: /database/port
  Error: Value 999999 exceeds maximum of 65535
  Schema: config-schema.json (jsch)
```

**Implementation consideration**: Track line numbers during YAML parsing to provide accurate error locations.

### 6.4 Performance Optimization

**Caching Strategy**:
```kotlin
// Schema cache
val schemaCache = ConcurrentHashMap<String, JsonSchema>()

fun getOrLoadSchema(schemaPath: String): JsonSchema {
    return schemaCache.getOrPut(schemaPath) {
        jsonSchemaFactory.load(schemaPath)
    }
}
```

**Batch Validation**:
For multiple YAML files with same schema:
```kotlin
val schema = getOrLoadSchema("config-schema.json")
yamlFiles.parallelStream()
    .map { file -> validateYAML(file, schema) }
    .collect(Collectors.toList())
```

---

## 7. Examples and Use Cases

### 7.1 Kubernetes Manifest Validation

**Scenario**: Validate and transform Kubernetes deployment YAML

**deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels:
    app: nginx
spec:
  replicas: 3
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
      - name: nginx
        image: nginx:1.14.2
        ports:
        - containerPort: 80
```

**k8s-deployment-schema.json** (simplified):
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["apiVersion", "kind", "metadata", "spec"],
  "properties": {
    "apiVersion": {
      "type": "string",
      "const": "apps/v1"
    },
    "kind": {
      "type": "string",
      "const": "Deployment"
    },
    "metadata": {
      "type": "object",
      "required": ["name"],
      "properties": {
        "name": {"type": "string"},
        "labels": {"type": "object"}
      }
    },
    "spec": {
      "type": "object",
      "required": ["replicas", "selector", "template"],
      "properties": {
        "replicas": {"type": "integer", "minimum": 1},
        "selector": {"type": "object"},
        "template": {"type": "object"}
      }
    }
  }
}
```

**transform.utlx**:
```utlx
%utlx 1.0
input yaml
output json
schema k8s-deployment-schema.json type:jsch
---
{
  deployment: {
    name: $input.metadata.name,
    replicas: $input.spec.replicas,
    image: $input.spec.template.spec.containers[0].image,
    ports: $input.spec.template.spec.containers[0].ports 
      |> map(p => p.containerPort),
    labels: $input.metadata.labels
  },
  validation: {
    hasMinReplicas: $input.spec.replicas >= 3,
    hasHealthCheck: exists($input.spec.template.spec.containers[0].livenessProbe)
  }
}
```

### 7.2 CI/CD Pipeline Configuration

**Scenario**: Validate GitHub Actions workflow

**.github/workflows/ci.yaml**:
```yaml
name: CI Pipeline
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Setup Node
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm install
      - run: npm test
```

**transform.utlx**:
```utlx
%utlx 1.0
input yaml
output json
schema "https://json.schemastore.org/github-workflow.json" type:jsch
---
{
  pipelineAnalysis: {
    name: $input.name,
    triggers: {
      push: $input.on.push.branches,
      pullRequest: $input.on.pull_request.branches
    },
    jobs: $input.jobs |> map((job, name) => {
      name: name,
      runner: job."runs-on",
      stepCount: count(job.steps),
      usesActions: job.steps 
        |> filter(s => exists(s.uses)) 
        |> map(s => s.uses)
    })
  }
}
```

### 7.3 Application Configuration Management

**Scenario**: Multi-environment configuration with validation

**config-base.yaml**:
```yaml
application:
  name: MyApp
  logging:
    level: INFO
    format: json

database:
  poolSize: 10
  timeout: 30

features:
  featureA: true
  featureB: false
```

**config-prod.yaml**:
```yaml
# Inherits from config-base.yaml
application:
  logging:
    level: WARN

database:
  poolSize: 50
  timeout: 60
  ssl: true
```

**config-schema.json**:
```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["application", "database"],
  "properties": {
    "application": {
      "type": "object",
      "properties": {
        "name": {"type": "string"},
        "logging": {
          "type": "object",
          "properties": {
            "level": {
              "type": "string",
              "enum": ["DEBUG", "INFO", "WARN", "ERROR"]
            },
            "format": {
              "type": "string",
              "enum": ["json", "text"]
            }
          }
        }
      }
    },
    "database": {
      "type": "object",
      "properties": {
        "poolSize": {"type": "integer", "minimum": 1, "maximum": 100},
        "timeout": {"type": "integer", "minimum": 1},
        "ssl": {"type": "boolean"}
      }
    },
    "features": {
      "type": "object",
      "additionalProperties": {"type": "boolean"}
    }
  }
}
```

**transform.utlx**:
```utlx
%utlx 1.0
input yaml as base from "config-base.yaml"
input yaml as prod from "config-prod.yaml"
output json
schema-base config-schema.json type:jsch
schema-prod config-schema.json type:jsch
---
// Merge configurations with prod overriding base
{
  finalConfig: merge($base, $prod),
  differences: {
    loggingLevel: {
      base: $base.application.logging.level,
      prod: $prod.application.logging.level
    },
    databasePool: {
      base: $base.database.poolSize,
      prod: $prod.database.poolSize
    }
  },
  validation: {
    bothValid: true,  // Both passed jsch validation
    prodEnhanced: exists($prod.database.ssl)
  }
}
```

### 7.4 Data Migration with Schema Evolution

**Scenario**: Migrating configuration from v1 to v2 format

**Old Format (v1)**:
```yaml
# config-v1.yaml
app_name: MyApp
db_host: localhost
db_port: 5432
log_level: 2  # 0=DEBUG, 1=INFO, 2=WARN, 3=ERROR
```

**New Format (v2)**:
```yaml
# config-v2.yaml
application:
  name: MyApp
database:
  host: localhost
  port: 5432
logging:
  level: WARN
```

**Migration transformation**:
```utlx
%utlx 1.0
input yaml
output yaml
schema config-v1-schema.json type:jsch
---
{
  application: {
    name: $input.app_name
  },
  database: {
    host: $input.db_host,
    port: $input.db_port
  },
  logging: {
    level: match($input.log_level) {
      0 => "DEBUG",
      1 => "INFO",
      2 => "WARN",
      3 => "ERROR"
    }
  }
}
```

**Validation**:
```bash
# Validate input against v1 schema
utlx validate --schema config-v1-schema.json --type jsch --input config-v1.yaml

# Transform
utlx transform migration.utlx --input config-v1.yaml --output config-v2.yaml

# Validate output against v2 schema
utlx validate --schema config-v2-schema.json --type jsch --input config-v2.yaml
```

---

## 8. Alternative Approaches (Not Recommended)

For completeness, here are alternative YAML validation approaches and why they're not recommended for UTL-X:

### 8.1 Kwalify

**What it was**: Ruby-based YAML validator with its own schema language

**Example Schema**:
```yaml
type: map
mapping:
  name:
    type: str
    required: yes
  email:
    type: str
    pattern: /@/
  age:
    type: int
    range: { min: 0, max: 150 }
```

**Why Not Recommended**:
- ❌ Project abandoned (last update ~2015)
- ❌ Limited features compared to JSON Schema
- ❌ Ruby dependency
- ❌ No IDE support
- ❌ Restrictive (doesn't allow spaces in keys)

### 8.2 Rx (Regular/Required/Repeated)

**What it is**: Multi-language schema system

**Status**: Still experimental, not production-ready

**Why Not Recommended**:
- ❌ Not widely adopted
- ❌ Limited tooling
- ❌ Authors warn against production use
- ❌ Less expressive than JSON Schema

### 8.3 StrictYAML (Python)

**What it is**: Python library with built-in schema validation

**Example**:
```python
from strictyaml import Map, Str, Int, load

schema = Map({
    "name": Str(),
    "age": Int(),
    "email": Str()
})

config = load(yaml_string, schema)
```

**Why Not Recommended**:
- ❌ Python-specific (not portable)
- ❌ Code-based schemas (not declarative files)
- ❌ Limited to Python ecosystem
- ❌ Restrictive YAML subset

### 8.4 Custom YAML Schema Language

**Hypothetical Approach**: Create a new YAML-specific schema language

**Why Not Recommended**:
- ❌ Reinventing the wheel
- ❌ No existing tooling or ecosystem
- ❌ Maintenance burden
- ❌ No clear advantage over JSON Schema
- ❌ Would fragment the ecosystem

---

## 9. Conclusion

### 9.1 Summary of Recommendations

**Primary Recommendation**: Use **JSON Schema (`jsch`)** for YAML validation in UTL-X

**Rationale**:
1. **Industry Standard**: JSON Schema is the universally accepted approach for YAML validation
2. **Technical Soundness**: YAML and JSON share the same data model, making JSON Schema a perfect fit
3. **Existing Infrastructure**: Leverages USDL's existing `jsch` support - no new implementation needed
4. **Tool Ecosystem**: Extensive IDE support, CLI tools, and library integrations
5. **No Alternatives**: No viable YAML-specific schema language exists

### 9.2 Implementation in UTL-X

**What UTL-X Needs**:
1. **YAML Parser**: Parse YAML files (with anchor/alias resolution)
2. **YAML-to-JSON Converter**: Convert parsed YAML to JSON-compatible structure
3. **Reuse Existing USDL**: Use existing `jsch` (JSON Schema) infrastructure
4. **Error Mapping**: Map validation errors back to YAML source locations

**What UTL-X Does NOT Need**:
- ❌ New schema type identifier (use `jsch`)
- ❌ New schema language
- ❌ New validation logic
- ❌ YAML-specific metadata format

### 9.3 Type Identifier Clarification

**Format vs. Schema**:
```utlx
%utlx 1.0
input yaml              # ← Format (how data is serialized)
output json
schema config.json type:jsch   # ← Schema type (how data is validated)
---
// Both YAML and JSON use jsch for schema validation
```

**Comparison Table**:
| Input Format | Schema Type | Identifier | Notes |
|--------------|-------------|------------|-------|
| JSON | JSON Schema | `jsch` | Standard |
| YAML | JSON Schema | `jsch` | Same as JSON |
| CSV | Table Schema | `tsch` | Different data model |
| XML | XML Schema | `xsd` | Different data model |
| Avro | Avro Schema | `avro` | Different data model |

### 9.4 Benefits for UTL-X Users

**Immediate Value**:
- ✅ Validate YAML configuration files
- ✅ Use schemas from SchemaStore.org (600+ schemas)
- ✅ IDE autocomplete and validation for YAML
- ✅ Type-safe transformations from YAML to other formats

**Long-term Advantages**:
- ✅ Single schema language for JSON and YAML
- ✅ Reduced learning curve (already know JSON Schema)
- ✅ Better interoperability with existing tools
- ✅ Future-proof (JSON Schema continues to evolve)

### 9.5 Migration from Other Systems

**For users coming from other tools**:

**From Kwalify**:
- Convert Kwalify schemas to JSON Schema (manual process)
- Use JSON Schema validators for YAML files

**From StrictYAML (Python)**:
- Export StrictYAML schemas to JSON Schema (if possible)
- Rewrite validation logic as JSON Schema

**From custom validators**:
- Identify validation rules
- Express in JSON Schema format
- Gain portability and tool support

### 9.6 Next Steps

1. **Enhance YAML Parser**: Ensure UTL-X's YAML parser handles all YAML 1.2 features
2. **Integrate JSON Schema Validation**: Connect YAML parser output to existing `jsch` validation
3. **Document YAML + jsch Pattern**: Update documentation showing YAML with JSON Schema
4. **Add Examples**: Provide common YAML validation examples (K8s, CI/CD, configs)
5. **SchemaStore Integration**: Document how to use schemas from schemastore.org

### 9.7 Final Recommendation

**DO NOT create a separate YAML metadata type.** Instead:

✅ Use `input yaml` for parsing YAML format  
✅ Use `schema file.json type:jsch` for validation  
✅ Leverage existing JSON Schema infrastructure  
✅ Document this as the recommended pattern  

**This approach is**:
- Technically sound
- Industry standard
- Immediately implementable
- Future-proof
- Tool-friendly

**The beauty of YAML + JSON Schema**: You get human-readable configuration files (YAML) validated by a mature, well-supported schema language (JSON Schema), with no need to invent new metadata systems.

---

## Appendix A: JSON Schema Quick Reference for YAML

### Basic Types

```json
{
  "properties": {
    "stringField": {"type": "string"},
    "numberField": {"type": "number"},
    "integerField": {"type": "integer"},
    "booleanField": {"type": "boolean"},
    "nullField": {"type": "null"},
    "arrayField": {"type": "array"},
    "objectField": {"type": "object"}
  }
}
```

### Common Constraints

```json
{
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100
    },
    "port": {
      "type": "integer",
      "minimum": 1,
      "maximum": 65535
    },
    "level": {
      "type": "string",
      "enum": ["low", "medium", "high"]
    },
    "email": {
      "type": "string",
      "format": "email"
    }
  },
  "required": ["name", "port"]
}
```

### Format Validators

```json
{
  "date": {"type": "string", "format": "date"},
  "time": {"type": "string", "format": "time"},
  "datetime": {"type": "string", "format": "date-time"},
  "email": {"type": "string", "format": "email"},
  "hostname": {"type": "string", "format": "hostname"},
  "ipv4": {"type": "string", "format": "ipv4"},
  "ipv6": {"type": "string", "format": "ipv6"},
  "uri": {"type": "string", "format": "uri"},
  "uuid": {"type": "string", "format": "uuid"}
}
```

### Schema Composition

```json
{
  "definitions": {
    "address": {
      "type": "object",
      "properties": {
        "street": {"type": "string"},
        "city": {"type": "string"}
      }
    }
  },
  "properties": {
    "homeAddress": {"$ref": "#/definitions/address"},
    "workAddress": {"$ref": "#/definitions/address"}
  }
}
```

---

## Appendix B: Resources and References

### JSON Schema
- **Official Site**: https://json-schema.org/
- **Understanding JSON Schema**: https://json-schema.org/understanding-json-schema/
- **JSON Schema Store**: https://schemastore.org/ (600+ schemas)
- **Specification**: https://json-schema.org/specification.html

### Tools
- **ajv-cli**: Command-line JSON Schema validator
- **yajsv**: Fast JSON Schema validator for YAML
- **pajv**: Polyglot validator supporting multiple formats

### IDE Support
- **VS Code**: Red Hat YAML extension (JSON Schema support)
- **JetBrains**: Built-in JSON Schema validation for YAML
- **yaml-language-server**: Used by multiple editors

### UTL-X Project
- **GitHub**: http://github.com/grauwen/utl-x
- **Documentation**: See CLAUDE.md in repository
- **Project Lead**: Ir. Marcel A. Grauwen

### Articles and Guides
- "JSON Schema Everywhere - YAML": https://json-schema-everywhere.github.io/yaml
- "YAML Schemas: Validating Data without Writing Code": https://www.codethink.co.uk/articles/2021/yaml-schemas/
- "YAML Data Validation - Infrastructure as Code": https://infrastructureascode.ch/yaml_validation.html

---

**Document Version**: 1.0  
**Last Updated**: November 6, 2025  
**Author**: Study guide for UTL-X project by Marcel A. Grauwen  
**Status**: Recommendation for YAML metadata handling in UTL-X
