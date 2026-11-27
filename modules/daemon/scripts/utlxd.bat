@echo off
set SCRIPT_DIR=%~dp0
set LIB_DIR=%SCRIPT_DIR%..\build\libs

REM CB14: newest jar, not a pinned version - see the shell script above.
set "JAR="
for /f "delims=" %%F in ('dir /b /o-d "%LIB_DIR%\utlxd-*.jar" 2^^>nul') do (
    if not defined JAR set "JAR=%LIB_DIR%\%%F"
)

if not defined JAR (
    echo Error: no utlxd JAR found in %LIB_DIR%
    echo Run 'gradlew :modules:daemon:jar' first
    exit /b 1
)

java -jar "%JAR%" %*