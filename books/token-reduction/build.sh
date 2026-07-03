#!/bin/bash
# Build the "UTLX for AI Token Reduction" article (PDF) from the Markdown source.
#
# Usage: ./build.sh
#
# Single source of truth: utlx-for-ai-token-reduction.md
#   - the body is regenerated to typst via pandoc (chapters/body.typ)
#   - the cover / colophon / TOC live in main.typ
#
# Produces: UTLX for AI Token Reduction.pdf

set -e
cd "$(dirname "$0")"

MD="utlx-for-ai-token-reduction.md"
OUTPUT="UTLX for AI Token Reduction.pdf"

# Regenerate the typst body from the Markdown, if pandoc is available.
if command -v pandoc >/dev/null 2>&1; then
    mkdir -p chapters
    # Body = from the first numbered section onward (title + thesis live on the cover, in main.typ).
    awk '/^## 1\. Tokens/{p=1} p' "$MD" > /tmp/tr-body.md
    pandoc /tmp/tr-body.md -t typst --shift-heading-level-by=-1 -o chapters/body.typ
    # Post-process: (1) define the thematic-break marker pandoc emits for `---`,
    #               (2) shrink table text so long tokens fit,
    #               (3) replace pandoc's percentage column widths with fractional ones that wrap cleanly.
    perl -0pi -e 's/\A/#let horizontalrule = align(center)[#v(0.5em) #line(length: 35%, stroke: 0.5pt + luma(170)) #v(0.5em)]\n#show table: set text(size: 8pt)\n\n/' chapters/body.typ
    perl -pi -e 's/columns: \(4\.55%[^)]*\),/columns: (auto, 1.7fr, 1fr, 1.3fr, 0.85fr, 1.1fr),/' chapters/body.typ
    perl -pi -e 's/columns: \(13\.11%[^)]*\),/columns: (1.1fr, 1fr, 0.7fr, 1fr, 1.3fr),/' chapters/body.typ
    echo "  body: regenerated chapters/body.typ from $MD"
else
    echo "  body: pandoc not found — reusing existing chapters/body.typ"
fi

echo "=== Building \"$OUTPUT\" ==="
typst compile main.typ "$OUTPUT"

# Report page count.
if command -v pdfinfo >/dev/null 2>&1; then
    PAGES=$(pdfinfo "$OUTPUT" 2>/dev/null | awk '/^Pages:/{print $2}')
else
    PAGES=$(mdls -name kMDItemNumberOfPages "$OUTPUT" 2>/dev/null | sed 's/.*= //')
fi

echo "  $OUTPUT — ${PAGES:-?} pages"
echo ""
echo "Done."
