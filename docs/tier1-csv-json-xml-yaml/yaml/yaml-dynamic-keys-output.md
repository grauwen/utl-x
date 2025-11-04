# Generating YAML with Dynamic Keys (OUTPUT)

**Date:** 2025-10-24
**Companion Document:** `yaml-dynamic-keys-support.md` (covers INPUT side)
**Focus:** How to CREATE YAML/JSON/objects with dynamic/unknown keys in UTL-X

---

## Executive Summary

When transforming data **TO** formats like YAML or JSON, you often need to generate objects where:
- **Keys are not known at design time** (e.g., environment names, IDs, user-defined labels)
- **Keys come from input data** (e.g., convert array to object keyed by ID)
- **Keys are computed dynamically** (e.g., concatenate prefix + value)

UTL-X provides **three primary patterns** for creating dynamic keys in output:

1. **`fromEntries()`** - Build object from `[key, value]` pairs ✅ Most Flexible
2. **`mapEntries()`** - Transform existing object's keys and values ✅ Best for Renaming
3. **Direct Computation** - Inline key generation (limited support) ⚠️ Use with caution

**Use Case:** Generating DataContract YAML specifications, configuration files, API responses

---

## Table of Contents

1. [Pattern 1: fromEntries() - Build from Array](#pattern1)
2. [Pattern 2: mapEntries() - Transform Object](#pattern2)
3. [Pattern 3: Direct Key Computation](#pattern3)
4. [Complete DataContract Example](#datacontract-example)
5. [Best Practices](#best-practices)
6. [Common Patterns](#common-patterns)
7. [Limitations](#limitations)

---

## Pattern 1: fromEntries() - Build from Array {#pattern1}

### Concept

Convert an **array of items** into an **object** where keys are dynamically determined.

**Signature:**
```
fromEntries(array: Array<[key, value]>) => Object
```

**Input:** Array of `[key, value]` pairs (2-element arrays)
**Output:** Object with dynamic keys

---

### Example 1: Array to Object by ID

**Problem:** Convert array of servers to object keyed by environment name

**Input:**
```yaml
servers:
  - environment: production
    host: prod.db.com
    port: 5432
  - environment: staging
    host: stage.db.com
    port: 5432
  - environment: development
    host: dev.db.com
    port: 5432
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  dataContractSpecification: "1.2.1",
  id: "generated-contract",

  servers: fromEntries(
    $input.servers |> map(server => [
      server.environment,
      {
        type: "postgres",
        host: server.host,
        port: server.port,
        database: "app_db"
      }
    ])
  )
}
```

**Output YAML:**
```yaml
dataContractSpecification: "1.2.1"
id: generated-contract
servers:
  production:
    type: postgres
    host: prod.db.com
    port: 5432
    database: app_db
  staging:
    type: postgres
    host: stage.db.com
    port: 5432
    database: app_db
  development:
    type: postgres
    host: dev.db.com
    port: 5432
    database: app_db
```

**Key Points:**
- Array element `[key, value]` where `key` = `server.environment`
- Value can be any expression (object, array, scalar)
- Keys are dynamically extracted from data

---

### Example 2: Computed Keys

**Problem:** Create object with keys computed from multiple fields

**Input:**
```json
{
  "applications": [
    {"region": "us", "env": "prod", "host": "app1.com"},
    {"region": "eu", "env": "prod", "host": "app2.com"},
    {"region": "us", "env": "stage", "host": "app3.com"}
  ]
}
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input json
output yaml
---
{
  endpoints: fromEntries(
    $input.applications |> map(app => [
      app.region + "-" + app.env,
      {
        host: app.host,
        url: "https://" + app.host
      }
    ])
  )
}
```

**Output YAML:**
```yaml
endpoints:
  us-prod:
    host: app1.com
    url: https://app1.com
  eu-prod:
    host: app2.com
    url: https://app2.com
  us-stage:
    host: app3.com
    url: https://app3.com
```

**Key Points:**
- Keys computed by concatenation: `region + "-" + env`
- Demonstrates dynamic key generation from multiple fields

---

### Example 3: Nested fromEntries

**Problem:** Create nested objects with dynamic keys at multiple levels

**UTL-X Transformation:**
```utlx
{
  models: fromEntries(
    $input.tables |> map(table => [
      table.name,
      {
        type: "table",
        fields: fromEntries(
          table.columns |> map(col => [
            col.name,
            {
              type: col.dataType,
              required: col.notNull,
              primaryKey: col.isPrimaryKey ?? false
            }
          ])
        )
      }
    ])
  )
}
```

**Key Points:**
- Outer `fromEntries()` creates table names as keys
- Inner `fromEntries()` creates field names as keys
- Handles 2-level dynamic key nesting

---

## Pattern 2: mapEntries() - Transform Object {#pattern2}

### Concept

Transform an **existing object** by changing both keys and values.

**Signature:**
```
mapEntries(obj: Object, fn: (key, value) => {key, value}) => Object
```

**Input:** Object with any keys
**Mapper:** Function receiving `(key, value)` and returning `{key: newKey, value: newValue}`
**Output:** Object with transformed keys/values

---

### Example 1: Rename Keys

**Problem:** Transform environment names (short → full)

**Input:**
```yaml
servers:
  prod:
    host: prod.db.com
  stage:
    host: stage.db.com
  dev:
    host: dev.db.com
```

**UTL-X Transformation:**
```utlx
%utlx 1.0
input yaml
output yaml
---
{
  servers: mapEntries($input.servers, (envKey, config) => {
    key: match envKey {
      "prod" => "production",
      "stage" => "staging",
      "dev" => "development",
      _ => envKey
    },
    value: config
  })
}
```

**Output YAML:**
```yaml
servers:
  production:
    host: prod.db.com
  staging:
    host: stage.db.com
  development:
    host: dev.db.com
```

**Key Points:**
- Input keys: `prod`, `stage`, `dev`
- Output keys: `production`, `staging`, `development`
- Values unchanged (passed through)

---

### Example 2: Add Prefix to All Keys

**Problem:** Add environment prefix to all server keys

**UTL-X Transformation:**
```utlx
{
  servers: mapEntries($input.servers, (name, config) => {
    key: "env-" + name,
    value: config
  })
}
```

**Input:** `{production: {...}, staging: {...}}`
**Output:** `{env-production: {...}, env-staging: {...}}`

---

### Example 3: Transform Both Keys and Values

**Problem:** Uppercase keys and add metadata to values

**UTL-X Transformation:**
```utlx
{
  servers: mapEntries($input.servers, (env, config) => {
    key: upper(env),
    value: {
      environment: env,
      host: config.host,
      port: config.port,
      connectionString: config.host + ":" + toString(config.port)
    }
  })
}
```

**Input:**
```yaml
servers:
  production: {host: prod.db.com, port: 5432}
  staging: {host: stage.db.com, port: 5432}
```

**Output:**
```yaml
servers:
  PRODUCTION:
    environment: production
    host: prod.db.com
    port: 5432
    connectionString: prod.db.com:5432
  STAGING:
    environment: staging
    host: stage.db.com
    port: 5432
    connectionString: stage.db.com:5432
```

**Key Points:**
- Keys transformed: `upper(env)`
- Values enriched: added `environment` and `connectionString`

---

## Pattern 3: Direct Key Computation {#pattern3}

### Concept

⚠️ **Limited Support** - UTL-X object literals require **static keys** at parse time.

**What DOES NOT Work:**
```utlx
{
  (computedKey): "value"  # ❌ Syntax error
}

{
  [$input.keyName]: "value"  # ❌ Not supported
}
```

**Workaround:** Use `fromEntries()` or `mapEntries()` instead.

---

### Example: Dynamic Key from Variable

**Problem:** Create object with key from variable

**❌ This Does NOT Work:**
```utlx
let envName = "production"
{
  ($envName): {host: "prod.db.com"}  # ❌ Parse error
}
```

**✅ Use fromEntries Instead:**
```utlx
let envName = "production"

fromEntries([
  [envName, {host: "prod.db.com"}]
])
```

**Output:**
```yaml
production:
  host: prod.db.com
```

---

## Complete DataContract Example {#datacontract-example}

### Problem

Generate a complete DataContract v1.2.1 YAML specification from structured data.

### Input (JSON)

```json
{
  "contractInfo": {
    "id": "orders-api-v2",
    "title": "Orders API Data Contract",
    "version": "2.0.0"
  },
  "serverConfigs": [
    {
      "env": "production",
      "type": "postgres",
      "host": "prod-db.company.com",
      "port": 5432,
      "database": "orders_prod",
      "schema": "public"
    },
    {
      "env": "staging",
      "type": "postgres",
      "host": "stage-db.company.com",
      "port": 5432,
      "database": "orders_stage",
      "schema": "public"
    }
  ],
  "tableDefinitions": [
    {
      "tableName": "orders",
      "tableType": "table",
      "columns": [
        {"name": "order_id", "type": "integer", "required": true, "isPrimaryKey": true},
        {"name": "customer_id", "type": "integer", "required": true, "isPrimaryKey": false},
        {"name": "order_date", "type": "timestamp", "required": true, "isPrimaryKey": false}
      ]
    },
    {
      "tableName": "customers",
      "tableType": "table",
      "columns": [
        {"name": "customer_id", "type": "integer", "required": true, "isPrimaryKey": true},
        {"name": "name", "type": "varchar", "required": true, "isPrimaryKey": false},
        {"name": "email", "type": "varchar", "required": false, "isPrimaryKey": false}
      ]
    }
  ]
}
```

### UTL-X Transformation

```utlx
%utlx 1.0
input json
output yaml
---
{
  dataContractSpecification: "1.2.1",
  id: $input.contractInfo.id,
  info: {
    title: $input.contractInfo.title,
    version: $input.contractInfo.version
  },

  servers: fromEntries(
    $input.serverConfigs |> map(server => [
      server.env,
      {
        type: server.type,
        host: server.host,
        port: server.port,
        database: server.database,
        schema: server.schema
      }
    ])
  ),

  models: fromEntries(
    $input.tableDefinitions |> map(table => [
      table.tableName,
      {
        type: table.tableType,
        fields: fromEntries(
          table.columns |> map(col => [
            col.name,
            {
              type: col.type,
              required: col.required,
              primaryKey: col.isPrimaryKey
            }
          ])
        )
      }
    ])
  )
}
```

### Output (DataContract YAML)

```yaml
dataContractSpecification: "1.2.1"
id: orders-api-v2
info:
  title: Orders API Data Contract
  version: "2.0.0"

servers:
  production:
    type: postgres
    host: prod-db.company.com
    port: 5432
    database: orders_prod
    schema: public
  staging:
    type: postgres
    host: stage-db.company.com
    port: 5432
    database: orders_stage
    schema: public

models:
  orders:
    type: table
    fields:
      order_id:
        type: integer
        required: true
        primaryKey: true
      customer_id:
        type: integer
        required: true
        primaryKey: false
      order_date:
        type: timestamp
        required: true
        primaryKey: false
  customers:
    type: table
    fields:
      customer_id:
        type: integer
        required: true
        primaryKey: true
      name:
        type: varchar
        required: true
        primaryKey: false
      email:
        type: varchar
        required: false
        primaryKey: false
```

### Key Techniques Used

1. **`fromEntries()` for servers** - Dynamic environment keys from array
2. **`fromEntries()` for models** - Dynamic table names from array
3. **Nested `fromEntries()` for fields** - Dynamic column names from nested array
4. **Triple nesting** - servers/models/fields all use dynamic keys

---

## Best Practices {#best-practices}

### 1. Choose the Right Pattern

**Use `fromEntries()` when:**
- ✅ Building object from array
- ✅ Keys come from data fields
- ✅ Need to compute keys from multiple fields
- ✅ Converting array to lookup object

**Use `mapEntries()` when:**
- ✅ Transforming existing object
- ✅ Renaming keys (short → long names)
- ✅ Adding prefixes/suffixes to keys
- ✅ Enriching values while preserving keys

**Avoid direct key computation:**
- ❌ Not supported in object literals
- ✅ Use `fromEntries()` instead

---

### 2. Validate Key Uniqueness

When using `fromEntries()`, ensure keys are unique:

```utlx
let servers = $input.serverConfigs

{
  servers: fromEntries(
    servers |> map(s => [
      s.env,
      {host: s.host}
    ])
  ),

  validation: {
    totalServers: count(servers),
    uniqueEnvs: count(unique(servers |> map(s => s.env))),
    hasduplicates: count(servers) != count(unique(servers |> map(s => s.env)))
  }
}
```

**If duplicates exist, last value wins** (overwrite behavior).

---

### 3. Handle Missing/Null Keys

**Problem:** What if key field is null/missing?

**Solution:** Filter or provide defaults

```utlx
{
  servers: fromEntries(
    $input.serverConfigs
      |> filter(s => s.env != null && s.env != "")
      |> map(s => [
           s.env ?? "unknown",
           {host: s.host}
         ])
  )
}
```

---

### 4. Combine with Static Keys

You can mix static and dynamic keys:

```utlx
{
  dataContractSpecification: "1.2.1",
  id: "my-contract",
  info: {
    title: "My Contract"
  },

  servers: fromEntries(
    $input.servers |> map(s => [s.env, s.config])
  ),

  models: fromEntries(
    $input.tables |> map(t => [t.name, t.definition])
  )
}
```

**Result:** `dataContractSpecification`, `id`, `info` are static, `servers` and `models` are dynamic.

---

## Common Patterns {#common-patterns}

### Pattern A: Array to Lookup Object

**Use Case:** Convert array to object keyed by ID for fast lookup

```utlx
{
  users: fromEntries(
    $input.userList |> map(user => [
      toString(user.id),
      user
    ])
  )
}
```

**Input:** `[{id: 1, name: "Alice"}, {id: 2, name: "Bob"}]`
**Output:** `{"1": {id: 1, name: "Alice"}, "2": {id: 2, name: "Bob"}}`

---

### Pattern B: Group By Key

**Use Case:** Group items by a field value

```utlx
{
  serversByRegion: fromEntries(
    $input.regions |> map(region => [
      region,
      $input.servers |> filter(s => s.region == region)
    ])
  )
}
```

---

### Pattern C: Denormalize Nested Data

**Use Case:** Flatten nested structures with dynamic keys

```utlx
{
  endpoints: fromEntries(
    $input.services |> flatMap(service =>
      service.endpoints |> map(endpoint => [
        service.name + "-" + endpoint.path,
        {
          method: endpoint.method,
          url: service.baseUrl + endpoint.path
        }
      ])
    )
  )
}
```

---

### Pattern D: Conditional Key Inclusion

**Use Case:** Only include certain keys based on conditions

```utlx
{
  servers: fromEntries(
    $input.allServers
      |> filter(s => s.active == true)
      |> map(s => [s.name, s.config])
  )
}
```

---

## Limitations {#limitations}

### What You CANNOT Do

1. **Computed keys in object literals**
   ```utlx
   {($variable): "value"}  # ❌ Not supported
   ```

2. **Dynamic property access in literals**
   ```utlx
   {[expr]: value}  # ❌ Not supported
   ```

3. **Spread with computed keys**
   ```utlx
   {...obj, [$key]: value}  # ❌ Not supported
   ```

### Workarounds

**All cases → Use `fromEntries()` or `mapEntries()`**

---

## Summary

| Pattern | Use Case | Flexibility | Performance |
|---------|----------|-------------|-------------|
| `fromEntries()` | Build from array | ⭐⭐⭐⭐⭐ Highest | ⭐⭐⭐⭐ Good |
| `mapEntries()` | Transform object | ⭐⭐⭐⭐ High | ⭐⭐⭐⭐⭐ Best |
| Direct computation | Not supported | ❌ N/A | N/A |

**Recommendation:** Master `fromEntries()` for maximum flexibility in generating dynamic keys.

---

## See Also

- [YAML Dynamic Keys Support (INPUT)](./yaml-dynamic-keys-support.md) - Reading dynamic keys
- [DataContract Specification v1.2.1](https://datacontract.com)
- [UTL-X Function Reference](./stlib_complete_reference.md)

---

**Document Version:** 1.0
**Last Updated:** 2025-10-24
**Status:** Production Ready
