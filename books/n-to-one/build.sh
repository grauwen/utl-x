#!/bin/bash
# Build the "Many to One" theory book.
#
# Usage: ./build.sh
#
# Produces:
#   Many to One - The Theory of N to 1 Data Mapping.pdf

set -e
cd "$(dirname "$0")"

# Render Graphviz diagrams (DOT -> SVG) if graphviz is installed; otherwise reuse existing SVGs.
if command -v dot >/dev/null 2>&1; then
    for d in diagrams/*.dot; do
        [ -e "$d" ] || continue
        dot -Tsvg "$d" -o "${d%.dot}.svg"
    done
    echo "  diagrams: rendered diagrams/*.dot -> *.svg"
else
    echo "  diagrams: graphviz 'dot' not found — reusing existing diagrams/*.svg"
fi

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
