@echo off
REM UTL-X CLI wrapper script for Windows

setlocal

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "LIB_DIR=%SCRIPT_DIR%modules\cli\build\libs"

REM Resolve the most recently built cli-*.jar instead of pinning a version -- a
REM pinned version silently keeps running the previous release's jar after a bump.
set "JAR_PATH="
for /f "delims=" %%F in ('dir /b /o-d "%LIB_DIR%\cli-*.jar" 2^>nul') do (
    if not defined JAR_PATH set "JAR_PATH=%LIB_DIR%\%%F"
)

REM Check if JAR exists
if not defined JAR_PATH (
    echo Error: no UTL-X CLI JAR found in %LIB_DIR%
    echo Please run 'gradlew.bat :modules:cli:jar' first
    exit /b 1
)

REM Run the JAR with all arguments passed through
java -jar "%JAR_PATH%" %*
