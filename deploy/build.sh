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

build_marketplace_plan() {
    local plan="$1"
    PLANS_DIR="$REPO_ROOT/deploy/azure/marketplace/plans"
    MARKETPLACE_DIR="$OUTPUT_DIR/marketplace"
    TRACKING_FILE="$PLANS_DIR/tracking-guids.json"

    PLAN_UI="$PLANS_DIR/${plan}-createUiDefinition.json"
    if [ ! -f "$PLAN_UI" ]; then
        echo "  ERROR: $PLAN_UI not found"
        exit 1
    fi

    # Compile Bicep if mainTemplate.json doesn't exist yet
    if [ ! -f "$OUTPUT_DIR/mainTemplate.json" ]; then
        build_bicep
    fi

    PLAN_DIR="$MARKETPLACE_DIR/$plan"
    mkdir -p "$PLAN_DIR"

    # Inject customer usage attribution tracking resource with literal GUID.
    # Partner Center requires the tracking deployment name to be a literal string,
    # not a parameter reference — so we patch the compiled ARM JSON per plan.
    TRACKING_GUID=$(python3 -c "import json; print(json.load(open('$TRACKING_FILE'))['$plan'])")
    python3 -c "
import json, sys
with open('$OUTPUT_DIR/mainTemplate.json') as f:
    arm = json.load(f)
tracking = {
    'type': 'Microsoft.Resources/deployments',
    'apiVersion': '2021-04-01',
    'name': 'pid-${TRACKING_GUID}-partnercenter',
    'properties': {
        'mode': 'Incremental',
        'template': {
            '\$schema': 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
            'contentVersion': '1.0.0.0',
            'resources': []
        }
    }
}
# Remove any existing tracking resource, then add the plan-specific one
arm['resources'] = [r for r in arm['resources'] if not r.get('name','').startswith('pid-')] + [tracking]
with open('$PLAN_DIR/mainTemplate.json', 'w') as f:
    json.dump(arm, f, indent=2)
"
    cp "$PLAN_UI" "$PLAN_DIR/createUiDefinition.json"

    cd "$PLAN_DIR"
    zip -q "$OUTPUT_DIR/utlxe-${plan}.zip" mainTemplate.json createUiDefinition.json
    cd "$REPO_ROOT"

    echo "  Plan: $plan → $OUTPUT_DIR/utlxe-${plan}.zip (tracking: $TRACKING_GUID)"
}

build_marketplace() {
    echo "=== Packaging Marketplace Artifacts (all plans) ==="
    build_bicep
    for plan in starter professional enterprise; do
        build_marketplace_plan "$plan"
    done
    echo ""
    echo "  Upload each ZIP to its corresponding plan in Microsoft Partner Center."
}

# Parse arguments
if [ $# -eq 0 ]; then
    build_jar
    build_docker
    build_bicep
else
    for arg in "$@"; do
        case "$arg" in
            jar)         build_jar ;;
            docker)      build_docker ;;
            bicep)       build_bicep ;;
            marketplace)           build_marketplace ;;
            marketplace:starter)   echo "=== Packaging Starter ===" ; build_bicep ; build_marketplace_plan starter ;;
            marketplace:professional) echo "=== Packaging Professional ===" ; build_bicep ; build_marketplace_plan professional ;;
            marketplace:enterprise)   echo "=== Packaging Enterprise ===" ; build_bicep ; build_marketplace_plan enterprise ;;
            *)           echo "Unknown target: $arg"; echo "Valid: jar, docker, bicep, marketplace, marketplace:starter, marketplace:professional, marketplace:enterprise"; exit 1 ;;
        esac
    done
fi

echo ""
echo "=== Build Artifacts ==="
ls -lh "$OUTPUT_DIR"/ 2>/dev/null
echo ""
echo "Done."
