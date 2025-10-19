@echo off
REM Disable UTL-X test capture on Windows

set CONFIG_DIR=%USERPROFILE%\.utlx
set CONFIG_FILE=%CONFIG_DIR%\capture-config.yaml

if exist "%CONFIG_FILE%" (
    REM Create temporary file with enabled: false
    powershell -Command "(Get-Content '%CONFIG_FILE%') -replace '^enabled:.*', 'enabled: false' | Set-Content '%CONFIG_FILE%.tmp'"
    move /Y "%CONFIG_FILE%.tmp" "%CONFIG_FILE%" >nul
    echo ✓ Test capture DISABLED
    echo   Updated: %CONFIG_FILE%
) else (
    echo ✓ Test capture is already DISABLED ^(no config file^)
    echo   Default: Capture is off unless explicitly enabled
)

echo.
echo To temporarily enable for a single command:
echo   set UTLX_CAPTURE_TESTS=true
echo   utlx transform script.utlx
echo.
echo To enable permanently:
echo   scripts\enable-test-capture.bat
