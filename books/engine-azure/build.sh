#!/bin/bash
# Build the Azure deployment guide
# Usage: ./build.sh [watch]

set -e

OUTPUT="UTLXe on Azure.pdf"

if [ "$1" = "watch" ]; then
    echo "Watching for changes..."
    typst watch main.typ "$OUTPUT"
else
    echo "Compiling..."
    typst compile main.typ "$OUTPUT"
    echo "Done: $OUTPUT"
fi
