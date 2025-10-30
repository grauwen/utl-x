@echo off
REM UTL-X CLI wrapper script for Windows

setlocal

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "JAR_PATH=%SCRIPT_DIR%modules\cli\build\libs\cli-1.0.0-SNAPSHOT.jar"

REM Check if JAR exists
if not exist "%JAR_PATH%" (
    echo Error: UTL-X CLI JAR not found at %JAR_PATH%
    echo Please run 'gradlew.bat :modules:cli:jar' first
    exit /b 1
)

REM Run the JAR with all arguments passed through
java -jar "%JAR_PATH%" %*
