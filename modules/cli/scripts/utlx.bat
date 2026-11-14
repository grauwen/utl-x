@echo off
set SCRIPT_DIR=%~dp0
set LIB_DIR=%SCRIPT_DIR%..\build\libs

REM Resolve the most recently built cli-*.jar instead of pinning a version -- a
REM pinned version silently keeps running the previous release's jar after a bump.
set "JAR="
for /f "delims=" %%F in ('dir /b /o-d "%LIB_DIR%\cli-*.jar" 2^>nul') do (
    if not defined JAR set "JAR=%LIB_DIR%\%%F"
)

if not defined JAR (
    echo Error: no cli JAR found in %LIB_DIR%
    echo Run 'gradlew :modules:cli:jar' first
    exit /b 1
)

java -jar "%JAR%" %*