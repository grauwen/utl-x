# USDL Directive Naming Analysis

## Executive Summary

**Total USDL Directives:** 120
**Stdlib Functions:** 633
**Naming Conflicts:** 12 (mitigated by `%` prefix)
**Recommended Changes:** 3

---

## 1. Direct Name Overlaps (USDL vs Stdlib)

These USDL directives share names with stdlib functions (but are safe due to `%` prefix):

| USDL Directive | Stdlib Function | Safe? | Recommendation |
|----------------|-----------------|-------|----------------|
| `%all` | `all()` | ✅ Yes | Keep - `%` prefix prevents conflict |
| `%default` | `default()` | ✅ Yes | Keep - common concept |
| `%enum` | No conflict | ✅ Yes | Keep |
| `%format` | `formatDate()`, `formatNumber()` | ✅ Yes | Keep - different context |
| `%index` | `indexOf()` | ✅ Yes | Keep - different context |
| `%map` | `map()` | ✅ Yes | Keep - Protobuf map type |
| `%name` | No direct conflict | ✅ Yes | Keep |
| `%pattern` | Used in regex | ✅ Yes | Keep |
| `%size` | `size()` | ✅ Yes | Keep - Avro fixed size |
| `%type` | `getType()`, `isType()` | ✅ Yes | Keep - core concept |
| `%values` | `values()` | ✅ Yes | Keep - enumeration values |
| `%version` | `version()` | ✅ Yes | Keep |

**Verdict:** All are **SAFE** - The `%` prefix clearly distinguishes USDL directives from stdlib functions.

---

## 2. Context-Confusing Directives

### 2.1 SQL vs CSV Confusion

**Issue:** `%column` could confuse users when working with CSV files (which also have columns).

| Directive | Context | Potential Confusion |
|-----------|---------|---------------------|
| `%column` | SQL DDL (database column name) | ⚠️ **CSV also has columns** |
| `%table` | SQL DDL (database table name) | ⚠️ **CSV data can be called "tables"** |

**Recommendation:**
- **Option 1 (Recommended):** Rename to **`%dbColumn`** (matches `%dbSchema` pattern)
- **Option 2:** Keep `%column` but add clear documentation
- **Option 3:** Rename to **`%sqlColumn`** (very explicit)

**Rationale for `%dbColumn`:**
- Consistent with `%dbSchema` (database-specific prefix)
- Clear disambiguation from CSV columns
- Still concise and readable

---

### 2.2 Overloaded "Key" Concept

**Issue:** `%key` has multiple meanings across different contexts.

| Directive | Context | Meaning |
|-----------|---------|---------|
| `%key` | SQL DDL | Primary key field (deprecated, use `%primaryKey`) |
| `%key` | OData | Entity key |
| `%key` | GraphQL | Key field |
| Object keys | Stdlib `keys()` | Object property keys |
| API keys | `%security` | Authentication keys |

**Recommendation:**
- ✅ **Already deprecated** in favor of `%primaryKey`
- Consider removing `%key` entirely in USDL 2.0
- Current status: Keep for backward compatibility but mark as deprecated

---

### 2.3 Schema Disambiguation (RESOLVED ✅)

| Old Name | New Name | Context |
|----------|----------|---------|
| ~~`%schema`~~ | **`%dbSchema`** | SQL database schema namespace |
| ~~`%schema`~~ | **`%apiSchema`** | REST API response/parameter schema |
| N/A | XSD | XML Schema format |
| N/A | JSON Schema | JSON Schema format |

**Status:** ✅ **RESOLVED** - Clear naming now in place.

---

## 3. Recommendations

### High Priority Changes

**1. Rename `%column` → `%dbColumn`**
```diff
- %column: Database column name
+ %dbColumn: Database column name
```

**Rationale:**
- Matches `%dbSchema` pattern
- Eliminates CSV confusion
- Clear SQL/database context

**Impact:** Low - Only used in SQL DDL (not yet implemented)

---

**2. Deprecate `%key` (already done)**

Already deprecated in favor of `%primaryKey`. Status: ✅ Complete

---

**3. Add Clarifying Documentation**

For directives that share concepts across formats:
- `%table` - Clarify "database table" vs "CSV data table"
- `%type` - Clarify "schema type definition" vs `getType()` function
- `%format` - Clarify "data format specification" vs `formatDate()` function

---

### Low Priority Observations

**Safe Generic Names:**
These are common schema concepts and are fine to keep:
- `%name`, `%description`, `%documentation` - Universal
- `%required`, `%nullable`, `%array` - Common type modifiers
- `%fields`, `%values`, `%enum` - Standard schema terminology
- `%version`, `%namespace` - Standard metadata

**Format-Specific Prefixes (Consider for Future):**
- `%sql*` prefix for SQL-specific directives (e.g., `%sqlType`, `%sqlDialect`)
- `%db*` prefix for database directives (e.g., `%dbSchema`, `%dbColumn`)
- `%api*` prefix for API directives (e.g., `%apiSchema`)

---

## 4. Complete USDL Directive Inventory (120 directives)

### Tier 1: Core (8)
`%namespace`, `%version`, `%types`, `%kind`, `%name`, `%type`, `%description`, `%documentation`

### Tier 2: Common (42 + 16 messaging + 14 REST = 72)

**Data Modeling:**
`%fields`, `%values`, `%itemType`, `%baseType`, `%default`, `%required`, `%array`, `%nullable`

**Constraints:**
`%constraints`, `%minLength`, `%maxLength`, `%pattern`, `%minimum`, `%maximum`, `%exclusiveMinimum`, `%exclusiveMaximum`, `%enum`, `%format`, `%multipleOf`

**Messaging (16):**
`%servers`, `%channels`, `%operations`, `%messages`, `%host`, `%protocol`, `%address`, `%subscribe`, `%publish`, `%bindings`, `%contentType`, `%headers`, `%payload`, `%action`, `%channel`, `%message`

**REST API (14):**
`%paths`, `%tags`, `%path`, `%method`, `%operationId`, `%summary`, `%requestBody`, `%responses`, `%statusCode`, `%apiSchema`, `%parameters`, `%in`, `%security`, `%securitySchemes`

**Common:**
`%example`

### Tier 3: Format-Specific (35)

**Binary Serialization:**
`%fieldNumber`, `%fieldId`, `%ordinal`, `%packed`, `%reserved`, `%oneof`, `%map`, `%precision`

**Big Data:**
`%logicalType`, `%aliases`, `%scale`, `%size`, `%repetition`, `%encoding`, `%compression`

**Database/SQL (18):**
`%table`, `%dbSchema`, `%column`, `%primaryKey`, `%key` (deprecated), `%autoIncrement`, `%unique`, `%index`, `%foreignKey`, `%references`, `%onDelete`, `%onUpdate`, `%check`, `%sqlType`, `%sqlDialect`, `%engine`, `%charset`, `%collation`

**REST/OData:**
`%entityType`, `%navigation`, `%target`, `%cardinality`, `%referentialConstraint`

**GraphQL:**
`%implements`, `%resolver`

**OpenAPI:**
`%readOnly`, `%writeOnly`, `%discriminator`, `%propertyName`, `%mapping`, `%externalDocs`, `%url`, `%examples`, `%xml`

**XML:**
`%attributeFormDefault`, `%elementFormDefault`

### Tier 4: Reserved (15)
`%allOf`, `%anyOf`, `%oneOf`, `%not`, `%if`, `%then`, `%else`, `%deprecated`, `%reason`, `%replacedBy`, `%title`, `%comment`, `%ref`, `%extends`, `%typedef`, `%choice`, `%alignment`, `%generic`, `%all`, `%options`

---

## 5. Final Recommendations

### Immediate Action Required

✅ **1. Rename `%column` → `%dbColumn`**
- Eliminates CSV confusion
- Matches `%dbSchema` pattern
- Consistent database namespace

### Status: Already Complete

✅ **2. Schema disambiguation** - `%dbSchema` and `%apiSchema` now in place
✅ **3. Deprecate `%key`** - Already marked as deprecated

### Documentation Updates Needed

📝 **4. Add disambiguation notes for:**
- `%table` - "Database table (SQL DDL), not CSV data table"
- `%column` → `%dbColumn` - "Database column name"
- `%apiSchema` - "API response/parameter schema (not XSD/JSON Schema format)"

---

## 6. Conclusion

**Overall Assessment:** ✅ **GOOD**

- The `%` prefix effectively prevents conflicts with stdlib functions
- Recent changes (`%dbSchema`, `%apiSchema`) significantly improved clarity
- Only one remaining issue: `%column` → `%dbColumn`

**Conflict Score:** **8/10** (Excellent)
- ✅ No actual naming collisions (% prefix protects)
- ✅ Recent disambiguation improvements
- ⚠️ One remaining CSV confusion (`%column`)

**Next Steps:**
1. Rename `%column` → `%dbColumn` (5 min)
2. Update SQL DDL documentation (10 min)
3. Add disambiguation notes to USDL docs (15 min)

**Total Effort:** ~30 minutes to complete all recommendations.
