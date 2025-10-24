# UTL-X Auto-Test-Capture System

## Overview

The Auto-Test-Capture System automatically captures every CLI transformation execution and generates conformance test cases. This creates a living, growing test suite that documents real usage patterns and captures bugs as reproducible test cases.

## Features

### ✅ Automatic Capture
- **Every transformation is captured** - No manual test writing required
- **Runs transparently** - Capture happens automatically during normal CLI usage
- **Zero overhead** - Capture is fast and doesn't slow down transformations

### ✅ Smart Deduplication
- **Content-based hashing** - Tests are identified by transformation + input + format
- **Prevents duplicates** - Running the same transformation twice only creates one test
- **Category limits** - Maximum tests per function (default: 50) prevents bloat

### ✅ Auto-Categorization
- **Function detection** - Automatically detects functions used (e.g., `upper`, `contains`)
- **Smart categorization** - Tests filed under correct category (e.g., `stdlib/string/`)
- **Format-based fallback** - XML-to-JSON transforms grouped appropriately

### ✅ Known Issues Tracking
- **Failing tests captured** - Errors are saved as "known issues"
- **Bug documentation** - Each failure includes error message and context
- **Resolution tracking** - When bug is fixed, test becomes passing

### ✅ Ignore Patterns
- **Skip temp files** - `/tmp/*`, `test_*.utlx` automatically ignored
- **Configurable** - Add your own ignore patterns
- **Verbose mode** - See what's being skipped with `-v` flag

## Enable/Disable Capture

### ⚠️ **IMPORTANT: Capture is DISABLED by Default**

For production performance, test capture is **disabled by default**. You must explicitly enable it.

### Quick Enable/Disable

**Linux/macOS:**
```bash
# Enable (creates config file)
./scripts/enable-test-capture.sh

# Disable (updates config file)
./scripts/disable-test-capture.sh
```

**Windows:**
```cmd
REM Enable (creates config file)
scripts\enable-test-capture.bat

REM Disable (updates config file)
scripts\disable-test-capture.bat
```

### Temporary Enable (Environment Variable)

Enable for a single command without changing config:

**Linux/macOS:**
```bash
# Enable for one command
export UTLX_CAPTURE_TESTS=true
utlx transform script.utlx
unset UTLX_CAPTURE_TESTS

# Or inline
UTLX_CAPTURE_TESTS=true utlx transform script.utlx
```

**Windows (PowerShell):**
```powershell
# Enable for one command
$env:UTLX_CAPTURE_TESTS = "true"
utlx transform script.utlx
Remove-Item Env:\UTLX_CAPTURE_TESTS
```

**Windows (CMD):**
```cmd
set UTLX_CAPTURE_TESTS=true
utlx transform script.utlx
set UTLX_CAPTURE_TESTS=
```

### Configuration Priority

Settings are checked in this order (highest priority first):

1. **Environment Variable** `UTLX_CAPTURE_TESTS` - Overrides everything
2. **Config File** `~/.utlx/capture-config.yaml` - Persistent setting
3. **Default** - DISABLED (for production safety)

## Usage

### Basic Usage (When Enabled)

Once enabled, capture happens automatically:

```bash
# Create a transformation
cat > my_transform.utlx <<'EOF'
%utlx 1.0
input json
output json
---
upper($input)
EOF

# Run it - test is auto-captured
echo '"hello world"' | utlx transform my_transform.utlx

# Output:
"HELLO WORLD"
  ✓ Test captured: stdlib/string/upper_auto_464d50f7.yaml (passing)
```

### What Gets Captured

For every transformation, the system captures:
- **Transformation script** - Full UTL-X code
- **Input data and format** - JSON, XML, CSV, etc.
- **Output data and format** - Result of transformation
- **Execution metrics** - Duration, success/failure
- **Error messages** - For failing transformations
- **Function names** - Auto-detected from script

### Generated Test Format

Captured tests are saved as YAML in `conformance-suite/tests/auto-captured/`:

```yaml
name: "upper_auto_464d50f7"
category: "stdlib/string"
description: "Auto-captured test for upper"
tags: ["auto-generated", "upper"]
auto_generated: true
captured_at: "2025-10-19T12:45:33.102197Z"

input:
  format: json
  data: "\"hello world\""

transformation: |
  %utlx 1.0
  input json
  output json
  ---
  upper($input)

expected:
  format: json
  data: "\"HELLO WORLD\""

performance_limits:
  max_duration_ms: 42  # 2x actual execution time
  max_memory_mb: 10

metadata:
  author: "UTL-X Auto-Capture"
  captured_date: "2025-10-19"
  references: ["Auto-generated test case"]
```

### Failing Tests (Known Issues)

When a transformation fails, it's captured as a "known issue":

```yaml
name: "transform_auto_29cfa913"
category: "auto-captured/json-transform"
description: "Auto-captured test for transformation"
tags: ["auto-generated"]
auto_generated: true

input:
  format: json
  data: "\"test\""

transformation: |
  %utlx 1.0
  input json
  output json
  ---
  nonExistentFunction($input)

known_issue:
  status: "failing"
  captured_output: "Undefined function: nonExistentFunction"
  issue_description: "Undefined function: nonExistentFunction"
  captured_at: "2025-10-19T12:46:11.856686Z"
```

## Configuration

### Default Configuration

The system uses these defaults:

```yaml
# DISABLED by default for production performance
enabled: false

capture_location: "conformance-suite/tests/auto-captured/"
deduplicate: true
capture_failures: true
max_tests_per_function: 50
ignore_patterns:
  - "**/tmp/**"
  - "**/test_*.utlx"
verbose: false
```

**To enable capture, you must:**
1. Run `./scripts/enable-test-capture.sh` (creates config file), OR
2. Set environment variable: `export UTLX_CAPTURE_TESTS=true`

### Custom Configuration

Create `~/.utlx/capture-config.yaml` to customize:

```yaml
# Enable/disable capture
enabled: true

# Where to save captured tests
capture_location: "conformance-suite/tests/auto-captured/"

# Prevent duplicate tests
deduplicate: true

# Capture failing transformations as known issues
capture_failures: true

# Max auto-tests per function category
max_tests_per_function: 50

# Patterns to ignore
ignore_patterns:
  - "**/tmp/**"
  - "**/test_*.utlx"
  - "**/debug_*.utlx"

# Show capture info
verbose: false
```

### Disabling Capture

Temporarily disable capture:

```bash
# Create config file
mkdir -p ~/.utlx
cat > ~/.utlx/capture-config.yaml <<EOF
enabled: false
EOF
```

## Directory Structure

```
conformance-suite/tests/
├── stdlib/                    # Hand-written tests
│   ├── string/
│   ├── array/
│   └── ...
└── auto-captured/             # Auto-generated tests
    ├── stdlib/
    │   └── string/
    │       ├── upper_auto_464d50f7.yaml
    │       ├── lower_auto_abc123de.yaml
    │       └── ...
    └── auto-captured/
        └── json-transform/
            └── transform_auto_29cfa913.yaml
```

## Test Statistics

View capture statistics:

```bash
# Count auto-captured tests
find conformance-suite/tests/auto-captured -name "*.yaml" | wc -l

# Count by category
find conformance-suite/tests/auto-captured -name "*.yaml" -exec dirname {} \; | sort | uniq -c

# Find known issues
grep -r "known_issue:" conformance-suite/tests/auto-captured
```

## Running Auto-Captured Tests

Auto-captured tests run just like regular tests:

```bash
# Run all tests (including auto-captured)
cd conformance-suite
python3 runners/cli-runner/simple-runner.py

# Run only auto-captured tests
python3 runners/cli-runner/simple-runner.py auto-captured/

# Run specific auto-captured test
python3 runners/cli-runner/simple-runner.py auto-captured/stdlib/string upper_auto_464d50f7
```

## Benefits

### 1. Automatic Regression Testing
Every bug you encounter becomes a permanent test case that ensures it never happens again.

### 2. Real Usage Documentation
Tests capture how functions are actually used in practice, not just how they're supposed to be used.

### 3. No Test Writing Overhead
Developers don't need to manually write tests - just use the CLI normally.

### 4. Growing Test Coverage
The test suite grows organically as the system is used, automatically increasing coverage.

### 5. Bug Tracking
Known issues are documented with reproduction steps, making debugging easier.

## Implementation Details

### Architecture

```
TransformCommand.execute()
    ↓
[Transformation runs]
    ↓
TestCaptureService.captureExecution()
    ↓
├─ TestCategorizer.categorize()  # Detect functions, categorize
├─ TestDeduplicator.generateTestId()  # Check for duplicates
└─ ConformanceGenerator.generateYaml()  # Create YAML file
    ↓
[Test saved to auto-captured/]
```

### Key Components

1. **TestCaptureService.kt** - Main capture orchestration
2. **ConformanceGenerator.kt** - YAML generation
3. **TestCategorizer.kt** - Function detection and categorization
4. **TestDeduplicator.kt** - Duplicate prevention
5. **CapturedTest.kt** - Data models
6. **TransformCommand.kt** - Integration hooks

### Performance Impact

#### When DISABLED (Default - Production Mode)
- **Overhead**: **0ms** - Completely bypassed, no performance impact
- **File I/O**: None
- **Memory**: None
- **Best for**: Production deployments, high-volume transformations

#### When ENABLED (Development/Test Mode)
- **Capture time**: < 5ms per transformation
- **File I/O**: One YAML write per unique transformation
- **Memory**: Minimal - only stores test data during capture
- **Deduplication**: Fast MD5 hash check
- **Best for**: Development, debugging, test suite building

#### Production Recommendations

**✅ DO:**
- Keep capture DISABLED in production (default)
- Enable only in dev/test environments
- Use environment variable for temporary captures
- Monitor captured test count (max 50 per function)

**❌ DON'T:**
- Enable capture in high-volume production systems
- Capture in performance-critical paths
- Leave capture enabled in CI/CD pipelines (unless intentional)

## Future Enhancements

### Planned Features

1. **Review Command** - `utlx test-capture review` to review captured tests
2. **Promote Command** - Move good auto-tests to official test suite
3. **Resolve Command** - Update known issues when bugs are fixed
4. **Statistics Dashboard** - View capture statistics and trends
5. **Smart Expected Values** - Manually specify expected output for known issues

## Troubleshooting

### Test Not Being Captured

**Check if file matches ignore pattern:**
```bash
# Scripts in /tmp are ignored by default
./utlx transform /tmp/my_test.utlx  # NOT captured

# Use a different location
./utlx transform my_test.utlx  # Captured!
```

**Enable verbose mode:**
```bash
./utlx transform my_test.utlx -v
# Shows: [Capture] Skipping (matches ignore pattern): /tmp/my_test.utlx
```

### Duplicate Tests Being Created

**Ensure deduplication is enabled in config:**
```yaml
deduplicate: true
```

**Check test ID generation:**
Tests are identified by: transformation + input + format. Changing any of these creates a new test.

### Capture Failed Silently

**Check directory permissions:**
```bash
ls -la conformance-suite/tests/
# Ensure write permissions
```

**Check disk space:**
```bash
df -h
```

## Examples

### Example 1: String Function Test

```bash
# Transformation
echo '"hello"' | utlx transform <(cat <<'EOF'
%utlx 1.0
input json
output json
---
upper($input)
EOF
)

# Result: Creates stdlib/string/upper_auto_<hash>.yaml
```

### Example 2: Array Transformation

```bash
# Transformation
echo '[1,2,3]' | utlx transform <(cat <<'EOF'
%utlx 1.0
input json
output json
---
map($input, x => x * 2)
EOF
)

# Result: Creates stdlib/array/map_auto_<hash>.yaml
```

### Example 3: Capturing a Bug

```bash
# Transformation with bug
echo '"test"' | utlx transform <(cat <<'EOF'
%utlx 1.0
input json
output json
---
buggyFunction($input)
EOF
)

# Result: Creates auto-captured/.../transform_auto_<hash>.yaml
# with known_issue section documenting the bug
```

## Summary

The Auto-Test-Capture System transforms every CLI use into a potential test case, automatically building a comprehensive test suite that:

- ✅ Grows organically with usage
- ✅ Documents real-world patterns
- ✅ Captures bugs as reproducible tests
- ✅ Prevents regressions automatically
- ✅ Requires zero manual effort

**It's test-driven development on autopilot!** 🚀
