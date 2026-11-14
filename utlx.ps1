#!/usr/bin/env pwsh
# UTL-X CLI wrapper script for Windows PowerShell

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$LibDir = Join-Path $ScriptDir "modules\cli\build\libs"

# Resolve the most recently built cli-*.jar instead of pinning a version -- a pinned
# version silently keeps running the previous release's jar after a bump.
$JarPath = Get-ChildItem -Path (Join-Path $LibDir "cli-*.jar") -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

if (-not $JarPath) {
    Write-Error "Error: no UTL-X CLI JAR found in $LibDir"
    Write-Host "Please run '.\gradlew.bat :modules:cli:jar' first" -ForegroundColor Yellow
    exit 1
}

# Run the JAR with all arguments passed through
& java -jar $JarPath $args
