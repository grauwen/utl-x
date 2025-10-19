#!/bin/bash
# Disable UTL-X test capture
# Works on Linux, macOS, and Windows (via Git Bash or WSL)

CONFIG_DIR="$HOME/.utlx"
CONFIG_FILE="$CONFIG_DIR/capture-config.yaml"

if [ -f "$CONFIG_FILE" ]; then
    # Update existing config file
    if grep -q "^enabled:" "$CONFIG_FILE"; then
        # Replace enabled line
        sed -i.bak 's/^enabled:.*/enabled: false/' "$CONFIG_FILE"
        rm -f "$CONFIG_FILE.bak"
        echo "✓ Test capture DISABLED"
        echo "  Updated: $CONFIG_FILE"
    else
        # Add enabled: false line
        echo "enabled: false" >> "$CONFIG_FILE"
        echo "✓ Test capture DISABLED"
        echo "  Updated: $CONFIG_FILE"
    fi
else
    # No config file exists - capture is already disabled by default
    echo "✓ Test capture is already DISABLED (no config file)"
    echo "  Default: Capture is off unless explicitly enabled"
fi

echo ""
echo "To temporarily enable for a single command:"
echo "  export UTLX_CAPTURE_TESTS=true"
echo "  utlx transform script.utlx"
echo ""
echo "To enable permanently:"
echo "  ./scripts/enable-test-capture.sh"
