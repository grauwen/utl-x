#!/bin/bash
# Build the UTL-X language book.
#
# Usage: ./build.sh
#
# Produces the single canonical artifact:
#   UTL-X One Language All Formats.pdf   — full book (all chapters)

set -e
cd "$(dirname "$0")"

OUTPUT="UTL-X One Language All Formats.pdf"

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
