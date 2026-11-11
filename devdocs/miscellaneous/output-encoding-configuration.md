# Output Encoding Configuration

## Overview

UTL-X supports flexible output encoding configuration for XML output with three levels of precedence:

1. **Script-level** (highest priority) - Explicit `output xml {encoding: "..."}` in transformation
2. **Global config** (future) - `.utlx-config.yaml` for project/user defaults
3. **Built-in default** (lowest priority) - Metadata preservation or UTF-8

## Current Implementation (Phase 1)

### Script-Level Configuration

#### Explicit Encoding

Specify exact encoding for XML output:

```utlx
%utlx 1.0
input json
output xml {encoding: "ISO-8859-1"}
---
$input
```

**Output:**
```xml
<?xml version="1.0" encoding="ISO-8859-1"?>
<root>...</root>
```

#### Suppress Encoding Declaration

Use `"NONE"` to omit encoding from declaration:

```utlx
%utlx 1.0
input json
output xml {encoding: "NONE"}
---
$input
```

**Output:**
```xml
<?xml version="1.0"?>
<root>...</root>
```

Per XML spec, this means UTF-8 or UTF-16 (detected by BOM/byte order).

#### Default Behavior

When no encoding specified, uses metadata (for XML input) or UTF-8:

```utlx
%utlx 1.0
input json
output xml
---
$input
```

**Output:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<root>...</root>
```

### Encoding Precedence Rules

```
Explicit encoding > Input metadata > UTF-8 default
```

**Example:**
```utlx
%utlx 1.0
input xml                          # Input has ISO-8859-1 encoding
output xml {encoding: "UTF-16"}    # Override with UTF-16
---
$input
```

Result: Output uses **UTF-16** (explicit overrides metadata)

## Future Implementation (Phase 2)

### Global Configuration File

**Project-level:** `.utlx-config.yaml` in project root
**User-level:** `~/.utlx/config.yaml`

#### Configuration Format

```yaml
# .utlx-config.yaml
defaults:
  xml:
    output_encoding: "UTF-8"     # Default encoding for XML output
    pretty_print: true           # Pretty print by default
    include_declaration: true    # Include <?xml...?> declaration

  json:
    pretty_print: true
    indent: "  "

  csv:
    delimiter: ","
    quote: "\""
    headers: true
```

#### Precedence with Global Config

```
Script-level > Global config > Built-in default
```

**Example Scenario:**

1. **Global config** sets: `output_encoding: "ISO-8859-1"`
2. **Script** doesn't specify encoding
3. **Result**: Output uses ISO-8859-1 (from global config)

4. **Script** specifies: `output xml {encoding: "UTF-16"}`
5. **Result**: Output uses UTF-16 (script overrides global)

6. **Script** specifies: `output xml {encoding: "NONE"}`
7. **Result**: No encoding declaration (script overrides global)

### Configuration Loading Order

1. **Built-in defaults** (hardcoded in UTL-X)
2. **User-level config** `~/.utlx/config.yaml` (overrides built-in)
3. **Project-level config** `.utlx-config.yaml` (overrides user-level)
4. **Script-level config** `output xml {encoding: "..."}` (highest priority)

### Environment Variables

Alternative to config files for CI/CD environments:

```bash
export UTLX_XML_OUTPUT_ENCODING="ISO-8859-1"
export UTLX_JSON_PRETTY_PRINT="true"

utlx transform script.utlx $input.json
```

Precedence:
```
Script > Environment variables > Global config > Built-in default
```

## Use Cases

### Use Case 1: SAP Integration (Enterprise)

**Requirement:** All XML output must use ISO-8859-1

**Solution:**
```yaml
# .utlx-config.yaml (project-level)
defaults:
  xml:
    output_encoding: "ISO-8859-1"
```

All transformations default to ISO-8859-1 unless explicitly overridden.

### Use Case 2: Mixed Encoding Environments

**Requirement:**
- System A requires UTF-16
- System B requires ISO-8859-1
- Default to UTF-8

**Solution:**
```yaml
# .utlx-config.yaml
defaults:
  xml:
    output_encoding: "UTF-8"  # Safe default
```

```utlx
# transform-system-a.utlx
%utlx 1.0
output xml {encoding: "UTF-16"}
---
...

# transform-system-b.utlx
%utlx 1.0
output xml {encoding: "ISO-8859-1"}
---
...
```

### Use Case 3: Testing Environments

**Requirement:** No encoding declarations in test XMLs

**Solution:**
```bash
# CI/CD pipeline
export UTLX_XML_OUTPUT_ENCODING="NONE"
./run-tests.sh
```

Or per-script:
```utlx
%utlx 1.0
output xml {encoding: "NONE"}
---
...
```

## Implementation Notes

### Current Architecture

- **FormatSpec.options**: Already supports `Map<String, Any>` for format options
- **Parser**: Already parses `output xml {encoding: "..."}` syntax
- **XMLSerializer**: Updated to accept `outputEncoding` parameter
- **TransformCommand**: Passes encoding from FormatSpec to serializer

### Phase 2 TODO

1. Create `ConfigurationManager` class to load YAML configs
2. Add config file discovery (project → user → built-in)
3. Add environment variable support
4. Update `TransformCommand` to consult config before serialization
5. Add CLI commands for config management:
   ```bash
   utlx config set xml.output_encoding ISO-8859-1
   utlx config get xml.output_encoding
   utlx config list
   ```

### Backwards Compatibility

All changes are **backwards compatible**:
- No configuration = UTF-8 default (existing behavior)
- Existing scripts without `{encoding: "..."}` continue to work
- Global config is **opt-in** (no config file = no change)

## Special Values

| Value | Meaning | Output |
|-------|---------|--------|
| `"UTF-8"` | UTF-8 encoding | `<?xml version="1.0" encoding="UTF-8"?>` |
| `"ISO-8859-1"` | Latin-1 encoding | `<?xml version="1.0" encoding="ISO-8859-1"?>` |
| `"UTF-16"` | UTF-16 encoding | `<?xml version="1.0" encoding="UTF-16"?>` |
| `"NONE"` | No encoding declaration | `<?xml version="1.0"?>` |
| *(not specified)* | Use metadata or UTF-8 | `<?xml version="1.0" encoding="UTF-8"?>` |

## Metadata Behavior

### XML Input → XML Output

Input encoding is preserved in **metadata** (not serialized):

```utlx
%utlx 1.0
input xml
output xml   # No explicit encoding
---
$input
```

If input has `encoding="ISO-8859-1"`, output also has `encoding="ISO-8859-1"`.

### JSON/YAML/CSV Input → XML Output

No input metadata, defaults to UTF-8:

```utlx
%utlx 1.0
input json
output xml   # No explicit encoding
---
$input
```

Output: `<?xml version="1.0" encoding="UTF-8"?>`

### Mixed Inputs (Future Enhancement)

When merging multiple XML inputs with different encodings:

```utlx
%utlx 1.0
input xml
output xml
---
{
  file1: $input,           # ISO-8859-1 metadata
  file2: $otherInput       # UTF-16 metadata
}
```

**Behavior:** Root object has no metadata → defaults to UTF-8 (or global config)

**Recommendation:** Use explicit `output xml {encoding: "..."}` for clarity.

## Testing

### Test Matrix

| Input Encoding | Output Config | Expected Output |
|----------------|---------------|-----------------|
| *(none)* | *(default)* | UTF-8 |
| *(none)* | `{encoding: "ISO-8859-1"}` | ISO-8859-1 |
| *(none)* | `{encoding: "NONE"}` | No encoding |
| ISO-8859-1 | *(default)* | ISO-8859-1 (metadata) |
| ISO-8859-1 | `{encoding: "UTF-16"}` | UTF-16 (override) |
| ISO-8859-1 | `{encoding: "NONE"}` | No encoding (override) |

### Test Examples

See `/tmp/test_*.utlx` for working examples:
- `test_explicit_encoding.utlx` - Explicit ISO-8859-1
- `test_no_encoding.utlx` - Suppress encoding with "NONE"
- `test_default_encoding.utlx` - Default UTF-8
- `test_metadata_preserve.utlx` - Preserve input encoding
- `test_override.utlx` - Override metadata with explicit

---

**Status:** Phase 1 (Script-level configuration) ✅ COMPLETE
**Next:** Phase 2 (Global configuration) 📋 PLANNED
