@echo off
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%..\build\libs\utlxd-1.3.0.jar

if not exist "%JAR%" (
    echo Error: JAR not found at %JAR%
    echo Run 'gradlew :modules:daemon:jar' first
    exit /b 1
)

java -jar "%JAR%" %*