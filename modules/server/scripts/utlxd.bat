@echo off
set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%..\build\libs\utlxd-1.0.0-SNAPSHOT.jar

if not exist "%JAR%" (
    echo Error: JAR not found at %JAR%
    echo Run 'gradlew :modules:server:jar' first
    exit /b 1
)

java -jar "%JAR%" %*