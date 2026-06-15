#!/bin/bash
# Build the "Many to One" theory book.
#
# Usage: ./build.sh
#
# Produces:
#   Many to One - The Theory of N to 1 Data Mapping.pdf

set -e
cd "$(dirname "$0")"

OUTPUT="Many to One - The Theory of N to 1 Data Mapping.pdf"

echo "=== Building \"$OUTPUT\" ==="
typst compile main.typ "$OUTPUT"

# Report page count: prefer pdfinfo (accurate); fall back to mdls (Spotlight, may lag).
if command -v pdfinfo >/dev/null 2>&1; then
    PAGES=$(pdfinfo "$OUTPUT" 2>/dev/null | awk '/^Pages:/{print $2}')
else
    PAGES=$(mdls -name kMDItemNumberOfPages "$OUTPUT" 2>/dev/null | sed 's/.*= //')
fi

echo "  $OUTPUT — ${PAGES:-?} pages"
echo ""
echo "Done."
