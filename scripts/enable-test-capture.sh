#!/bin/bash
# Enable UTL-X test capture by creating config file
# Works on Linux, macOS, and Windows (via Git Bash or WSL)

CONFIG_DIR="$HOME/.utlx"
CONFIG_FILE="$CONFIG_DIR/capture-config.yaml"

# Create directory if it doesn't exist
mkdir -p "$CONFIG_DIR"

# Create config file
cat > "$CONFIG_FILE" <<'EOF'
# UTL-X Test Capture Configuration
# This file enables automatic test capture for all transformations

# Enable test capture (set to false to disable)
enabled: true

# Where to save captured tests
capture_location: "conformance-suite/tests/auto-captured/"

# Prevent duplicate test captures
deduplicate: true

# Capture failing transformations as known issues
capture_failures: true

# Maximum auto-tests per function category (prevents bloat)
max_tests_per_function: 50

# Patterns to ignore (tests in these locations won't be captured)
ignore_patterns:
  - "**/tmp/**"
  - "**/test_*.utlx"
  - "**/debug_*.utlx"

# Show capture info during transformations
verbose: false
EOF

echo "✓ Test capture ENABLED"
echo "  Config file: $CONFIG_FILE"
echo ""
echo "To temporarily disable for a single command:"
echo "  export UTLX_CAPTURE_TESTS=false"
echo "  utlx transform script.utlx"
echo ""
echo "To disable permanently:"
echo "  ./scripts/disable-test-capture.sh"
