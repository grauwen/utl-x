#!/bin/bash
# Run tests for a specific category
if [[ $# -lt 1 ]]; then
    echo "Usage: $0 CATEGORY [OPTIONS]"
    echo "Example: $0 stdlib/array"
    exit 1
fi

category="$1"
shift
exec "$(dirname "$0")/run-tests.sh" "$category" "$@"