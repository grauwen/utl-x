# Test Capture Enable/Disable Scripts

Scripts to control UTL-X automatic test capture feature.

## ⚠️ Important: Disabled by Default

**Test capture is DISABLED by default** for production performance. You must explicitly enable it.

## Quick Reference

### Linux/macOS

```bash
# Enable test capture
./scripts/enable-test-capture.sh

# Disable test capture
./scripts/disable-test-capture.sh
```

### Windows

```cmd
REM Enable test capture
scripts\enable-test-capture.bat

REM Disable test capture
scripts\disable-test-capture.bat
```

## What These Scripts Do

### Enable Script
1. Creates `~/.utlx/` directory if needed
2. Creates `~/.utlx/capture-config.yaml` with `enabled: true`
3. Sets up default configuration

### Disable Script
1. Updates existing config file to set `enabled: false`
2. If no config exists, confirms capture is already disabled

## Environment Variable (Temporary Override)

For temporary control without changing config file:

### Linux/macOS
```bash
# Enable for one command
export UTLX_CAPTURE_TESTS=true
utlx transform script.utlx
unset UTLX_CAPTURE_TESTS

# Or inline
UTLX_CAPTURE_TESTS=true utlx transform script.utlx

# Disable for one command (when config has it enabled)
export UTLX_CAPTURE_TESTS=false
utlx transform script.utlx
unset UTLX_CAPTURE_TESTS
```

### Windows (PowerShell)
```powershell
$env:UTLX_CAPTURE_TESTS = "true"
utlx transform script.utlx
Remove-Item Env:\UTLX_CAPTURE_TESTS
```

### Windows (CMD)
```cmd
set UTLX_CAPTURE_TESTS=true
utlx transform script.utlx
set UTLX_CAPTURE_TESTS=
```

## Configuration Priority

Settings are checked in this order (highest priority first):

1. **Environment Variable** `UTLX_CAPTURE_TESTS` - Overrides everything
2. **Config File** `~/.utlx/capture-config.yaml` - Persistent setting
3. **Default** - DISABLED

## Use Cases

### Development/Testing
```bash
# Enable permanently for dev work
./scripts/enable-test-capture.sh

# Now all transformations are captured
utlx transform script1.utlx
utlx transform script2.utlx
utlx transform script3.utlx
```

### Production
```bash
# Keep disabled (default) or explicitly disable
./scripts/disable-test-capture.sh

# No capture overhead - maximum performance
utlx transform production-script.utlx
```

### Debugging Specific Issue
```bash
# Temporarily enable for one command
UTLX_CAPTURE_TESTS=true utlx transform buggy-script.utlx

# Capture is saved, but config remains unchanged
```

### CI/CD Pipeline
```yaml
# Example GitHub Actions workflow
jobs:
  test:
    steps:
      - name: Enable test capture for CI
        run: ./scripts/enable-test-capture.sh

      - name: Run transformations (captured as tests)
        run: |
          utlx transform test1.utlx
          utlx transform test2.utlx

      - name: Commit captured tests
        run: |
          git add conformance-suite/tests/auto-captured/
          git commit -m "Auto-captured tests from CI"
```

## Checking Current Status

### Linux/macOS
```bash
# Check if config file exists and what it says
cat ~/.utlx/capture-config.yaml

# Check environment variable
echo $UTLX_CAPTURE_TESTS
```

### Windows (PowerShell)
```powershell
# Check config file
Get-Content ~\.utlx\capture-config.yaml

# Check environment variable
$env:UTLX_CAPTURE_TESTS
```

### Windows (CMD)
```cmd
REM Check config file
type %USERPROFILE%\.utlx\capture-config.yaml

REM Check environment variable
echo %UTLX_CAPTURE_TESTS%
```

## Configuration File Location

- **Linux/macOS**: `~/.utlx/capture-config.yaml` (typically `/home/username/.utlx/`)
- **Windows**: `%USERPROFILE%\.utlx\capture-config.yaml` (typically `C:\Users\username\.utlx\`)

## Config File Example

```yaml
# UTL-X Test Capture Configuration
enabled: true  # or false

capture_location: "conformance-suite/tests/auto-captured/"
deduplicate: true
capture_failures: true
max_tests_per_function: 50

ignore_patterns:
  - "**/tmp/**"
  - "**/test_*.utlx"
  - "**/debug_*.utlx"

verbose: false  # Set to true to see capture info
```

## Troubleshooting

### Scripts Don't Have Execute Permission (Linux/macOS)
```bash
chmod +x scripts/enable-test-capture.sh
chmod +x scripts/disable-test-capture.sh
```

### Config File Not Being Created
```bash
# Manually create directory
mkdir -p ~/.utlx

# Run enable script
./scripts/enable-test-capture.sh
```

### Environment Variable Not Working
```bash
# Must be exported
export UTLX_CAPTURE_TESTS=true
utlx transform script.utlx

# Not just set in current shell
UTLX_CAPTURE_TESTS=true  # Wrong - won't work
```

### Capture Still Happening After Disable
```bash
# Check for environment variable override
echo $UTLX_CAPTURE_TESTS

# Unset it
unset UTLX_CAPTURE_TESTS

# Now disable will work
./scripts/disable-test-capture.sh
```

## Performance Impact

| Mode | Overhead | Use Case |
|------|----------|----------|
| **Disabled (Default)** | 0ms | Production, high-volume |
| **Enabled** | <5ms per transform | Development, testing |

## Learn More

See full documentation: `docs/test-capture-system.md`
