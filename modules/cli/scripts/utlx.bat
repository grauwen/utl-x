@echo off
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%..\build\libs\cli-1.0.0-SNAPSHOT.jar

if not exist "%JAR%" (
    echo Error: JAR not found at %JAR%
    echo Run 'gradlew :modules:cli:jar' first
    exit /b 1
)

java -jar "%JAR%" %*