@echo off
REM Enable UTL-X test capture on Windows

set CONFIG_DIR=%USERPROFILE%\.utlx
set CONFIG_FILE=%CONFIG_DIR%\capture-config.yaml

REM Create directory if it doesn't exist
if not exist "%CONFIG_DIR%" mkdir "%CONFIG_DIR%"

REM Create config file
(
echo # UTL-X Test Capture Configuration
echo # This file enables automatic test capture for all transformations
echo.
echo # Enable test capture ^(set to false to disable^)
echo enabled: true
echo.
echo # Where to save captured tests
echo capture_location: "conformance-suite/tests/auto-captured/"
echo.
echo # Prevent duplicate test captures
echo deduplicate: true
echo.
echo # Capture failing transformations as known issues
echo capture_failures: true
echo.
echo # Maximum auto-tests per function category ^(prevents bloat^)
echo max_tests_per_function: 50
echo.
echo # Patterns to ignore ^(tests in these locations won't be captured^)
echo ignore_patterns:
echo   - "**/tmp/**"
echo   - "**/test_*.utlx"
echo   - "**/debug_*.utlx"
echo.
echo # Show capture info during transformations
echo verbose: false
) > "%CONFIG_FILE%"

echo ✓ Test capture ENABLED
echo   Config file: %CONFIG_FILE%
echo.
echo To temporarily disable for a single command:
echo   set UTLX_CAPTURE_TESTS=false
echo   utlx transform script.utlx
echo.
echo To disable permanently:
echo   scripts\disable-test-capture.bat
