# UTL-X Support for YAML with Dynamic Keys

**Date:** 2025-10-24
**Status:** ✅ **FULLY SUPPORTED** - All capabilities already implemented
**Author:** UTL-X Documentation Team

---

## Executive Summary

This document analyzes UTL-X's support for YAML files with dynamic/arbitrary keys, particularly focusing on specifications like [DataContract v1.2.1](https://datacontract.com) where map keys are user-defined and unknown at transformation design time.

**Key Finding:** UTL-X **already provides complete support** for all dynamic key patterns through:
1. Native UDM (Universal Data Model) object representation
2. Comprehensive stdlib functions (`keys`, `values`, `hasKey`, `entries`, `mapEntries`, etc.)
3. Multiple access patterns (static, wildcard, bracket notation)
4. Format-agnostic transformation (same patterns work for JSON, XML, CSV, YAML)

---

## Table of Contents

1. [Background: The Dynamic Keys Challenge](#background)
2. [Current UTL-X Capabilities](#capabilities)
3. [The Six YAML Variant Patterns](#variants)
4. [DataContract Specification Examples](#datacontract-examples)
5. [Function Reference](#function-reference)
6. [Best Practices](#best-practices)
7. [Comparison with Other Tools](#comparison)

---

## Background: The Dynamic Keys Challenge {#background}

### What Are Dynamic Keys?

Dynamic keys occur when YAML/JSON map keys are:
- **User-defined** - Not known at design time (e.g., environment names, IDs, labels)
- **Arbitrary** - Can be any valid string/identifier
- **Variable** - Different across documents/instances

### Example: DataContract Servers

```yaml
# datacontract.yaml
servers:
  production:        # ← Dynamic key (environment name)
    type: postgres
    host: prod.db.example.com
    port: 5432
  staging:           # ← Another dynamic key
    type: postgres
    host: staging.db.example.com
    port: 5432
  custom-qa-env:     # ← User can add any environment name
    type: postgres
    host: qa.db.example.com
    port: 5432
```

### Why This Is Challenging

1. **Static Type Systems** - Languages with static typing struggle with unknown property names
2. **JSON Schema Limitations** - Cannot fully validate arbitrary keys (must use `additionalProperties`)
3. **XPath/JSONPath** - Path expressions require known keys
4. **Code Generation** - Cannot generate strongly-typed accessors

### UTL-X's Advantage

UTL-X's **Universal Data Model (UDM)** naturally handles dynamic keys:
- Maps are represented as `UDM.Object(properties: Map<String, UDM>)`
- **Any** string can be a key - no schema restriction
- Rich stdlib for introspection and manipulation
- Format-agnostic approach works identically for JSON, YAML, XML

---

## Current UTL-X Capabilities {#capabilities}

### 1. Parser Support ✅

**YAMLParser** (already implemented):
```kotlin
// From: formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLParser.kt
is Map<*, *> -> {
    obj.forEach { (key, value) ->
        val keyStr = key?.toString() ?: ""
        properties[keyStr] = convertToUDM(value, options)
    }
    UDM.Object(properties, emptyMap())
}
```

**Result:** All YAML keys → UDM.Object properties map (string→UDM)

### 2. Serializer Support ✅

**YAMLSerializer** (already implemented):
```kotlin
// From: formats/yaml/src/main/kotlin/org/apache/utlx/formats/yaml/YAMLSerializer.kt
is UDM.Object -> {
    val map = LinkedHashMap<String, Any?>()
    udm.properties.forEach { (key, value) ->
        map[key] = convertFromUDM(value, options)
    }
    map
}
```

**Result:** UDM.Object → YAML with arbitrary keys preserved

### 3. Static Access ✅

**Pattern:** Access known keys directly

```utlx
%utlx 1.0
input yaml
output json
---
{
  prodHost: $input.servers.production.host,
  stagingHost: $input.servers.staging.host
}
```

**Implementation:** `evaluateMemberAccess` (interpreter.kt:375-430)

### 4. Wildcard Selection ✅

**Pattern:** Access all values regardless of key names

```utlx
{
  allServers: $input.servers.*,
  allHosts: $input.servers.* |> map(s => s.host)
}
```

**Implementation:** Wildcard `*` returns array of all property values (interpreter.kt:392-404)

### 5. Bracket Notation ✅

**Pattern:** Access keys using variables/expressions

```utlx
let env = "production"
{
  server: $input.servers[env],
  conditional: $input.servers[
    if ($input.isProd) "production" else "staging"
  ]
}
```

**Implementation:** `evaluateIndexAccess` with string index (interpreter.kt:524-545)

### 6. Key Introspection ✅

**Pattern:** Get key names, check existence

```utlx
{
  environments: keys($input.servers),        # ["production", "staging", ...]
  envCount: count($input.servers),           # 3
  hasProd: hasKey($input.servers, "production"), # true
  allValues: values($input.servers)          # array of all server objects
}
```

**Functions:** `keys()`, `values()`, `hasKey()`, `count()` - all in stdlib

### 7. Key-Value Transformation ✅

**Pattern:** Transform object while preserving/changing keys

```utlx
{
  servers: entries($input.servers) |> map(entry => {
    environment: entry.key,
    connectionString: entry.value.host + ":" + toString(entry.value.port),
    type: entry.value.type
  }),

  # Or using mapEntries (transforms in-place)
  serversUpperKeys: mapEntries($input.servers, (k, v) => {
    key: upper(k),
    value: v
  })
}
```

**Functions:** `entries()`, `mapEntries()`, `filterEntries()`, `reduceEntries()` - all in stdlib

---

## The Six YAML Variant Patterns {#variants}

### Variant 1: Static Known Keys

**Use Case:** You know exactly which keys exist

**YAML Input:**
```yaml
servers:
  production:
    type: postgres
    host: prod.db.example.com
    port: 5432
  staging:
    type: postgres
    host: staging.db.example.com
    port: 5432
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  production: {
    server: $input.servers.production.host,
    port: $input.servers.production.port,
    type: $input.servers.production.type
  },
  staging: {
    server: $input.servers.staging.host,
    port: $input.servers.staging.port,
    type: $input.servers.staging.type
  }
}
```

**Output:**
```json
{
  "production": {
    "server": "prod.db.example.com",
    "port": 5432,
    "type": "postgres"
  },
  "staging": {
    "server": "staging.db.example.com",
    "port": 5432,
    "type": "postgres"
  }
}
```

**Status:** ✅ **FULLY SUPPORTED** via member access (`.` notation)

---

### Variant 2: Unknown Keys with Wildcard

**Use Case:** Process all keys regardless of names

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  # Get all server objects as array
  allServers: $input.servers.*,

  # Transform all servers
  serverList: $input.servers.* |> map(server => {
    type: server.type,
    endpoint: server.host + ":" + toString(server.port)
  }),

  # Filter servers
  postgresServers: $input.servers.* |> filter(s => s.type == "postgres"),

  # Aggregate
  totalServers: count($input.servers)
}
```

**Output:**
```json
{
  "allServers": [
    {"type": "postgres", "host": "prod.db.example.com", "port": 5432},
    {"type": "postgres", "host": "staging.db.example.com", "port": 5432}
  ],
  "serverList": [
    {"type": "postgres", "endpoint": "prod.db.example.com:5432"},
    {"type": "postgres", "endpoint": "staging.db.example.com:5432"}
  ],
  "postgresServers": [
    {"type": "postgres", "host": "prod.db.example.com", "port": 5432},
    {"type": "postgres", "host": "staging.db.example.com", "port": 5432}
  ],
  "totalServers": 2
}
```

**Status:** ✅ **FULLY SUPPORTED** via wildcard selector (`*`)

---

### Variant 3: Dynamic Key Access by Variable

**Use Case:** Select key based on runtime value/condition

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  # Simple variable access
  targetEnv: let env = "production" in $input.servers[env],

  # Conditional selection
  activeServer: match $input.environment {
    "prod" => $input.servers["production"],
    "stage" => $input.servers["staging"],
    "dev" => $input.servers["development"],
    _ => $input.servers["staging"]
  },

  # Function-based selection
  serverByName: let getName = () => "production" in $input.servers[getName()]
}
```

**Additional Input:**
```yaml
environment: prod
servers:
  production:
    type: postgres
    host: prod.db.example.com
  staging:
    type: postgres
    host: staging.db.example.com
```

**Output:**
```json
{
  "targetEnv": {
    "type": "postgres",
    "host": "prod.db.example.com"
  },
  "activeServer": {
    "type": "postgres",
    "host": "prod.db.example.com"
  },
  "serverByName": {
    "type": "postgres",
    "host": "prod.db.example.com"
  }
}
```

**Status:** ✅ **FULLY SUPPORTED** via bracket notation (`[expression]`)

---

### Variant 4: Key Name Introspection

**Use Case:** Need to know what keys exist, check for specific keys

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  # Get all environment names
  environments: keys($input.servers),

  # Count environments
  environmentCount: count($input.servers),

  # Check for specific environments
  hasProd: hasKey($input.servers, "production"),
  hasStaging: hasKey($input.servers, "staging"),
  hasDev: hasKey($input.servers, "development"),

  # Get all server objects (without keys)
  allServerConfigs: values($input.servers),

  # Conditional logic based on key existence
  primaryServer: if (hasKey($input.servers, "production"))
                   $input.servers.production
                 else if (hasKey($input.servers, "staging"))
                   $input.servers.staging
                 else
                   $input.servers[first(keys($input.servers))]
}
```

**Output:**
```json
{
  "environments": ["production", "staging"],
  "environmentCount": 2,
  "hasProd": true,
  "hasStaging": true,
  "hasDev": false,
  "allServerConfigs": [
    {"type": "postgres", "host": "prod.db.example.com", "port": 5432},
    {"type": "postgres", "host": "staging.db.example.com", "port": 5432}
  ],
  "primaryServer": {
    "type": "postgres",
    "host": "prod.db.example.com",
    "port": 5432
  }
}
```

**Status:** ✅ **FULLY SUPPORTED** via `keys()`, `values()`, `hasKey()`, `count()`

---

### Variant 5: Object Transformation with Key Preservation

**Use Case:** Transform object structure while keeping or changing keys

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  # Convert to array of key-value pairs
  serverEntries: entries($input.servers) |> map(entry => {
    environment: entry.key,
    connectionString: entry.value.host + ":" + toString(entry.value.port),
    database: entry.value.type
  }),

  # Transform keys and values in-place
  serversUppercase: mapEntries($input.servers, (envName, config) => {
    key: upper(envName),
    value: {
      type: config.type,
      endpoint: config.host + ":" + toString(config.port)
    }
  }),

  # Filter object by predicate
  productionServers: filterEntries($input.servers, (name, cfg) =>
    contains(name, "prod") || contains(name, "production")
  ),

  # Reduce to single value
  allHosts: reduceEntries($input.servers, (acc, name, cfg) =>
    acc + [cfg.host], []
  )
}
```

**Output:**
```json
{
  "serverEntries": [
    {
      "environment": "production",
      "connectionString": "prod.db.example.com:5432",
      "database": "postgres"
    },
    {
      "environment": "staging",
      "connectionString": "staging.db.example.com:5432",
      "database": "postgres"
    }
  ],
  "serversUppercase": {
    "PRODUCTION": {
      "type": "postgres",
      "endpoint": "prod.db.example.com:5432"
    },
    "STAGING": {
      "type": "postgres",
      "endpoint": "staging.db.example.com:5432"
    }
  },
  "productionServers": {
    "production": {
      "type": "postgres",
      "host": "prod.db.example.com",
      "port": 5432
    }
  },
  "allHosts": [
    "prod.db.example.com",
    "staging.db.example.com"
  ]
}
```

**Status:** ✅ **FULLY SUPPORTED** via `entries()`, `mapEntries()`, `filterEntries()`, `reduceEntries()`

---

### Variant 6: YAML Output with Dynamic Keys

**Use Case:** Generate YAML with dynamic keys from transformation

**UTL-X Transformation:**
```utlx
%utlx 1.0
input json
output yaml
---
{
  servers: {
    production: {
      type: "postgres",
      host: "prod.db.example.com",
      port: 5432
    },
    staging: {
      type: "postgres",
      host: "staging.db.example.com",
      port: 5432
    }
  },

  # Can also generate keys dynamically
  environments: fromEntries(
    $input.configs |> map(cfg => {
      key: cfg.name,
      value: {
        type: cfg.database,
        host: cfg.hostname
      }
    })
  )
}
```

**Input JSON:**
```json
{
  "configs": [
    {"name": "prod-eu", "database": "postgres", "hostname": "eu.db.com"},
    {"name": "prod-us", "database": "postgres", "hostname": "us.db.com"}
  ]
}
```

**Output YAML:**
```yaml
---
servers:
  production:
    type: postgres
    host: prod.db.example.com
    port: 5432
  staging:
    type: postgres
    host: staging.db.example.com
    port: 5432
environments:
  prod-eu:
    type: postgres
    host: eu.db.com
  prod-us:
    type: postgres
    host: us.db.com
```

**Status:** ✅ **FULLY SUPPORTED** via YAMLSerializer + `fromEntries()`

---

## DataContract Specification Examples {#datacontract-examples}

### DataContract v1.2.1 Structure

[DataContract](https://datacontract.com) uses dynamic keys extensively:

```yaml
dataContractSpecification: 1.2.1
id: orders-datacontract
info:
  title: Orders Data Contract
  version: 1.0.0

servers:
  production:          # ← Dynamic environment name
    type: postgres
    host: prod.db.example.com
    port: 5432
    database: orders_db
    schema: public
  staging:             # ← Another dynamic environment
    type: postgres
    host: staging.db.example.com
    port: 5432
    database: orders_db_staging
    schema: public

models:
  orders:
    type: table
    fields:
      order_id:        # ← Dynamic field name
        type: integer
        required: true
        primaryKey: true
      customer_id:     # ← Another dynamic field
        type: integer
        required: true
      order_date:
        type: timestamp
        required: true

  customers:
    type: table
    fields:
      customer_id:     # ← Dynamic fields per model
        type: integer
        required: true
        primaryKey: true
      name:
        type: varchar
        required: true
      email:
        type: varchar
        required: false
```

### Example 1: Extract All Server Configurations

**Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  servers: entries($input.servers) |> map(entry => {
    environment: entry.key,
    connection: {
      type: entry.value.type,
      host: entry.value.host,
      port: entry.value.port,
      database: entry.value.database,
      schema: entry.value.schema
    },
    connectionString: entry.value.type + "://" +
                      entry.value.host + ":" +
                      toString(entry.value.port) + "/" +
                      entry.value.database
  })
}
```

**Output:**
```json
{
  "servers": [
    {
      "environment": "production",
      "connection": {
        "type": "postgres",
        "host": "prod.db.example.com",
        "port": 5432,
        "database": "orders_db",
        "schema": "public"
      },
      "connectionString": "postgres://prod.db.example.com:5432/orders_db"
    },
    {
      "environment": "staging",
      "connection": {
        "type": "postgres",
        "host": "staging.db.example.com",
        "port": 5432,
        "database": "orders_db_staging",
        "schema": "public"
      },
      "connectionString": "postgres://staging.db.example.com:5432/orders_db_staging"
    }
  ]
}
```

### Example 2: Extract Model Schema

**Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  models: entries($input.models) |> map(modelEntry => {
    tableName: modelEntry.key,
    type: modelEntry.value.type,
    fields: entries(modelEntry.value.fields) |> map(fieldEntry => {
      name: fieldEntry.key,
      type: fieldEntry.value.type,
      required: fieldEntry.value.required,
      primaryKey: fieldEntry.value.primaryKey ?? false
    }),
    primaryKeys: entries(modelEntry.value.fields)
      |> filter(fe => fe.value.primaryKey == true)
      |> map(fe => fe.key)
  })
}
```

**Output:**
```json
{
  "models": [
    {
      "tableName": "orders",
      "type": "table",
      "fields": [
        {"name": "order_id", "type": "integer", "required": true, "primaryKey": true},
        {"name": "customer_id", "type": "integer", "required": true, "primaryKey": false},
        {"name": "order_date", "type": "timestamp", "required": true, "primaryKey": false}
      ],
      "primaryKeys": ["order_id"]
    },
    {
      "tableName": "customers",
      "type": "table",
      "fields": [
        {"name": "customer_id", "type": "integer", "required": true, "primaryKey": true},
        {"name": "name", "type": "varchar", "required": true, "primaryKey": false},
        {"name": "email", "type": "varchar", "required": false, "primaryKey": false}
      ],
      "primaryKeys": ["customer_id"]
    }
  ]
}
```

### Example 3: Generate SQL DDL from DataContract

**Transformation:**
```utlx
%utlx 1.0
input yaml
output json
---
{
  ddl: entries($input.models) |> map(modelEntry => {
    let tableName = modelEntry.key
    let fields = modelEntry.value.fields

    createTable: "CREATE TABLE " + tableName + " (" +
      join(
        entries(fields) |> map(fieldEntry =>
          fieldEntry.key + " " +
          upper(fieldEntry.value.type) +
          (if (fieldEntry.value.required) " NOT NULL" else "") +
          (if (fieldEntry.value.primaryKey ?? false) " PRIMARY KEY" else "")
        ),
        ", "
      ) + ");"
  })
}
```

**Output:**
```json
{
  "ddl": [
    {
      "createTable": "CREATE TABLE orders (order_id INTEGER NOT NULL PRIMARY KEY, customer_id INTEGER NOT NULL, order_date TIMESTAMP NOT NULL);"
    },
    {
      "createTable": "CREATE TABLE customers (customer_id INTEGER NOT NULL PRIMARY KEY, name VARCHAR NOT NULL, email VARCHAR);"
    }
  ]
}
```

---

## Function Reference {#function-reference}

### Core Object Functions

#### `keys(obj: Object): Array<String>`

Returns array of all property names in an object.

```utlx
keys({a: 1, b: 2, c: 3})
→ ["a", "b", "c"]

keys($input.servers)
→ ["production", "staging", "development"]
```

**Use Cases:**
- Discover unknown key names
- Iterate over dynamic properties
- Validate key existence patterns

---

#### `values(obj: Object): Array<Any>`

Returns array of all property values in an object.

```utlx
values({a: 1, b: 2, c: 3})
→ [1, 2, 3]

values($input.servers)
→ [<production_config>, <staging_config>, ...]
```

**Use Cases:**
- Process all values regardless of keys
- Aggregate data from dynamic objects
- Convert object to array for filtering/mapping

---

#### `hasKey(obj: Object, key: String): Boolean`

Checks if object contains a specific key.

```utlx
hasKey({a: 1, b: 2}, "a")  → true
hasKey({a: 1, b: 2}, "c")  → false

hasKey($input.servers, "production")  → true
```

**Use Cases:**
- Conditional logic based on key existence
- Safe navigation (check before access)
- Validation and error handling

---

#### `entries(obj: Object): Array<Object>`

Returns array of key-value pair objects with `{key, value}` structure.

```utlx
entries({a: 1, b: 2})
→ [{key: "a", value: 1}, {key: "b", value: 2}]

entries($input.servers) |> map(e => e.key)
→ ["production", "staging"]
```

**Use Cases:**
- Transform objects to arrays while preserving keys
- Iterate with access to both key and value
- Restructure data

---

#### `fromEntries(array: Array<Object>): Object`

Inverse of `entries()` - creates object from array of key-value pairs.

```utlx
fromEntries([{key: "a", value: 1}, {key: "b", value: 2}])
→ {a: 1, b: 2}
```

**Use Cases:**
- Build objects dynamically
- Inverse transformation of entries()
- Generate objects from processed arrays

---

### Advanced Object Functions

#### `mapEntries(obj: Object, fn: (key, value) => {key, value}): Object`

Transforms each entry in an object, can change both keys and values.

```utlx
mapEntries({a: 1, b: 2}, (k, v) => {
  key: upper(k),
  value: v * 2
})
→ {A: 2, B: 4}

mapEntries($input.servers, (env, cfg) => {
  key: env + "_server",
  value: cfg.host
})
→ {production_server: "prod.db.com", staging_server: "stage.db.com"}
```

**Use Cases:**
- Transform object keys (rename, uppercase, prefix)
- Transform object values with key context
- Restructure nested objects

---

#### `filterEntries(obj: Object, predicate: (key, value) => Boolean): Object`

Filters object to include only entries that satisfy the predicate.

```utlx
filterEntries({a: 1, b: 2, c: 3}, (k, v) => v > 1)
→ {b: 2, c: 3}

filterEntries($input.servers, (env, cfg) =>
  contains(env, "prod")
)
→ {production: <config>}
```

**Use Cases:**
- Remove unwanted properties
- Extract subset of object based on criteria
- Filter by key pattern or value condition

---

#### `reduceEntries(obj: Object, fn: (acc, key, value) => acc, initial): Any`

Reduces all entries in an object to a single value.

```utlx
reduceEntries({a: 1, b: 2, c: 3}, (acc, k, v) => acc + v, 0)
→ 6

reduceEntries($input.servers, (acc, env, cfg) =>
  acc + [cfg.host], []
)
→ ["prod.db.com", "staging.db.com"]
```

**Use Cases:**
- Aggregate values from object
- Build arrays/objects from entries
- Calculate totals, counts, concatenations

---

### YAML-Specific Functions

UTL-X also provides YAML-specific variants that work with YAML data:

- `yamlKeys(obj)` - Get keys from YAML object
- `yamlValues(obj)` - Get values from YAML object
- `yamlEntries(obj)` - Get entries from YAML object
- `yamlFromEntries(array)` - Create YAML object from entries

These functions are optimized for YAML-specific handling and maintain YAML semantics.

---

## Best Practices {#best-practices}

### 1. Choose the Right Access Pattern

**Static Access** - When you know the keys:
```utlx
✅ GOOD: $input.servers.production.host
```

**Wildcard** - When processing all values:
```utlx
✅ GOOD: $input.servers.* |> map(s => s.host)
```

**Bracket Notation** - When keys are dynamic:
```utlx
✅ GOOD: $input.servers[$input.environment]
```

**Introspection** - When keys are unknown:
```utlx
✅ GOOD: entries($input.servers) |> map(e => {...})
```

---

### 2. Safe Navigation with hasKey()

Always check for key existence before accessing optional keys:

```utlx
❌ RISKY:
{
  prodHost: $input.servers.production.host  # Fails if key missing
}

✅ SAFE:
{
  prodHost: if (hasKey($input.servers, "production"))
              $input.servers.production.host
            else
              "not-configured"
}

✅ BETTER (nullish coalescing):
{
  prodHost: $input.servers.production?.host ?? "not-configured"
}
```

---

### 3. Use entries() for Key-Value Iteration

When you need both keys and values, use `entries()`:

```utlx
❌ INCOMPLETE:
{
  hosts: $input.servers.* |> map(s => s.host)
  # Problem: Lost the environment names (keys)!
}

✅ COMPLETE:
{
  hosts: entries($input.servers) |> map(e => {
    environment: e.key,
    host: e.value.host
  })
}
```

---

### 4. Leverage Pattern Matching for Selection

Use pattern matching for cleaner conditional key selection:

```utlx
❌ VERBOSE:
{
  server: if ($input.env == "prod")
            $input.servers.production
          else if ($input.env == "stage")
            $input.servers.staging
          else
            $input.servers.development
}

✅ CLEAN:
{
  server: match $input.env {
    "prod" => $input.servers.production,
    "stage" => $input.servers.staging,
    "dev" => $input.servers.development,
    _ => $input.servers[first(keys($input.servers))]
  }
}
```

---

### 5. Use fromEntries() for Dynamic Object Construction

Build objects from arrays using `fromEntries()`:

```utlx
{
  # Convert array to object with dynamic keys
  serversById: fromEntries(
    $input.serverList |> map(s => {
      key: s.id,
      value: {
        host: s.hostname,
        port: s.port
      }
    })
  )
}
```

**Input:**
```yaml
serverList:
  - id: srv-001
    hostname: server1.com
    port: 8080
  - id: srv-002
    hostname: server2.com
    port: 8081
```

**Output:**
```json
{
  "serversById": {
    "srv-001": {"host": "server1.com", "port": 8080},
    "srv-002": {"host": "server2.com", "port": 8081}
  }
}
```

---

### 6. Validate Key Patterns

Use key introspection for validation:

```utlx
{
  # Validate all environment names follow convention
  validation: {
    allKeys: keys($input.servers),
    invalidKeys: keys($input.servers) |> filter(k =>
      !matches(k, "^(production|staging|development|test)$")
    ),
    isValid: count(
      keys($input.servers) |> filter(k =>
        !matches(k, "^(production|staging|development|test)$")
      )
    ) == 0
  }
}
```

---

## Comparison with Other Tools {#comparison}

### vs. DataWeave (MuleSoft)

| Feature | DataWeave | UTL-X |
|---------|-----------|-------|
| Dynamic key access | ✅ `payload.servers[var]` | ✅ `$input.servers[var]` |
| Key introspection | ✅ `keysOf(obj)` | ✅ `keys(obj)` |
| Object mapping | ✅ `payload mapObject (value, key) -> {...}` | ✅ `mapEntries(obj, (key, value) => {...})` |
| Wildcard selection | ✅ `payload.*` | ✅ `$input.*` |
| License | ⚠️ Proprietary | ✅ AGPL-3.0 / Commercial |
| Format support | ✅ XML, JSON, CSV, Java | ✅ XML, JSON, CSV, YAML |

**Winner:** Tie - Both provide equivalent functionality

---

### vs. jq (JSON processor)

| Feature | jq | UTL-X |
|---------|-----|-------|
| Dynamic key access | ✅ `.servers[$var]` | ✅ `$input.servers[$var]` |
| Key introspection | ✅ `keys` | ✅ `keys()` |
| Object mapping | ✅ `to_entries \| map(...)` | ✅ `entries() \|> map(...)` |
| Wildcard selection | ✅ `.servers[]` | ✅ `$input.servers.*` |
| YAML support | ⚠️ Via plugins | ✅ Native |
| XML support | ❌ None | ✅ Native |
| Type system | ❌ Dynamic only | ✅ Type inference |

**Winner:** UTL-X - Better format support, type system

---

### vs. XSLT (XML only)

| Feature | XSLT | UTL-X |
|---------|------|-------|
| Dynamic key access | ⚠️ Complex XPath | ✅ `$input.servers[var]` |
| Key introspection | ❌ Difficult | ✅ `keys()` |
| Object iteration | ⚠️ `<xsl:for-each>` | ✅ `entries() \|> map(...)` |
| JSON/YAML support | ❌ XML only | ✅ All formats |
| Learning curve | ❌ Steep | ✅ Moderate |

**Winner:** UTL-X - Better ergonomics, format support

---

### vs. JSONata

| Feature | JSONata | UTL-X |
|---------|---------|-------|
| Dynamic key access | ✅ `servers[$var]` | ✅ `$input.servers[$var]` |
| Key introspection | ✅ `$keys(obj)` | ✅ `keys(obj)` |
| Object mapping | ✅ `servers.$spread()` | ✅ `mapEntries()` |
| YAML support | ❌ JSON only | ✅ Native |
| XML support | ❌ None | ✅ Native |
| License | ✅ MIT | ✅ AGPL-3.0 / Commercial |

**Winner:** UTL-X - Better format support

---

## Conclusion

UTL-X provides **complete, production-ready support** for YAML files with dynamic keys like DataContract specifications. All necessary capabilities are already implemented:

✅ **Parser** - UDM naturally handles arbitrary keys
✅ **Serializer** - Preserves dynamic keys in output
✅ **Access Patterns** - Static, wildcard, bracket notation
✅ **Introspection** - `keys()`, `values()`, `hasKey()`, `entries()`
✅ **Transformation** - `mapEntries()`, `filterEntries()`, `reduceEntries()`
✅ **Format Agnostic** - Same patterns work for JSON, XML, CSV, YAML

No implementation work is required - users can start using these patterns immediately.

---

## References

1. [DataContract Specification v1.2.1](https://datacontract.com)
2. [UTL-X YAML Functions Guide](./yaml_functions_guide.md)
3. [UTL-X Stdlib Reference](./stlib_complete_reference.md)
4. [YAML Maps and JSON Schema Limitations](../stdlib/yaml-maps-json-schema-summary.md)

---

**Document Version:** 1.0
**Last Updated:** 2025-10-24
**Status:** Production Ready
