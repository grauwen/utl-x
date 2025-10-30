#!/usr/bin/env pwsh
# UTL-X CLI wrapper script for Windows PowerShell

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$JarPath = Join-Path $ScriptDir "modules\cli\build\libs\cli-1.0.0-SNAPSHOT.jar"

if (-not (Test-Path $JarPath)) {
    Write-Error "Error: UTL-X CLI JAR not found at $JarPath"
    Write-Host "Please run '.\gradlew.bat :modules:cli:jar' first" -ForegroundColor Yellow
    exit 1
}

# Run the JAR with all arguments passed through
& java -jar $JarPath $args
