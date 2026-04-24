#!/bin/bash
# UTL-X Deployment Artifact Builder
#
# Builds all deployment artifacts from source:
#   1. UTLXe engine fat JAR
#   2. Docker image
#   3. ARM template (compiled from Bicep)
#
# Usage:
#   ./deploy/build.sh              # build all
#   ./deploy/build.sh jar          # JAR only
#   ./deploy/build.sh docker       # Docker image only
#   ./deploy/build.sh bicep        # ARM template only

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$REPO_ROOT/deploy/dist"

mkdir -p "$OUTPUT_DIR"

build_jar() {
    echo "=== Building UTLXe JAR ==="
    cd "$REPO_ROOT"
    ./gradlew :modules:engine:jar --quiet
    JAR=$(ls modules/engine/build/libs/utlxe-*.jar 2>/dev/null | head -1)
    if [ -z "$JAR" ]; then
        echo "ERROR: JAR not found"
        exit 1
    fi
    cp "$JAR" "$OUTPUT_DIR/utlxe.jar"
    echo "  JAR: $OUTPUT_DIR/utlxe.jar ($(du -h "$OUTPUT_DIR/utlxe.jar" | cut -f1))"
}

build_docker() {
    echo "=== Building Docker Image ==="
    cd "$REPO_ROOT"
    docker build -f deploy/docker/Dockerfile.engine -t utlxe:latest .
    echo "  Image: utlxe:latest ($(docker images utlxe:latest --format '{{.Size}}'))"
}

build_bicep() {
    echo "=== Compiling Bicep → ARM Template ==="
    if ! command -v az &>/dev/null; then
        echo "ERROR: Azure CLI not installed. Install with: brew install azure-cli"
        exit 1
    fi
    cd "$REPO_ROOT"
    az bicep build --file deploy/azure/main.bicep --outfile "$OUTPUT_DIR/mainTemplate.json" 2>/dev/null
    echo "  ARM: $OUTPUT_DIR/mainTemplate.json ($(wc -l < "$OUTPUT_DIR/mainTemplate.json") lines)"
}

# Parse arguments
if [ $# -eq 0 ]; then
    build_jar
    build_docker
    build_bicep
else
    for arg in "$@"; do
        case "$arg" in
            jar)    build_jar ;;
            docker) build_docker ;;
            bicep)  build_bicep ;;
            *)      echo "Unknown target: $arg. Valid: jar, docker, bicep"; exit 1 ;;
        esac
    done
fi

echo ""
echo "=== Build Artifacts ==="
ls -lh "$OUTPUT_DIR"/ 2>/dev/null
echo ""
echo "Done."
