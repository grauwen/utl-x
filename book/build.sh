#!/bin/bash
# Build both versions of the UTL-X book
#
# Usage: ./build.sh
#
# Produces:
#   utlx-book.pdf          — full book (all chapters, including outlines)
#   utlx-book-draft.pdf    — written chapters only (no blank pages)

cd "$(dirname "$0")"

echo "=== Building full book ==="
typst compile main.typ utlx-book.pdf 2>&1
if [ $? -eq 0 ]; then
    PAGES=$(python3 -c "
import subprocess
r = subprocess.run(['mdls', '-name', 'kMDItemNumberOfPages', 'utlx-book.pdf'], capture_output=True, text=True)
print(r.stdout.strip().split('= ')[1])
" 2>/dev/null)
    echo "  utlx-book.pdf — $PAGES pages (full, including outlines)"
else
    echo "  ERROR: full book failed to compile"
fi

echo ""
echo "=== Building draft (written chapters only) ==="

# Generate main-draft.typ: include only chapters with substantial content
python3 -c "
import os

# Chapters with real written text (>15 non-comment, non-empty lines)
written = set()
for f in sorted(os.listdir('chapters')):
    if not f.endswith('.typ'):
        continue
    path = os.path.join('chapters', f)
    with open(path) as fh:
        lines = fh.readlines()
    content_lines = [l for l in lines if not l.strip().startswith('//') and l.strip()]
    if len(content_lines) >= 15:
        written.add(f)

# Read main.typ and filter
with open('main.typ') as f:
    main = f.readlines()

with open('main-draft.typ', 'w') as out:
    skip_next_pagebreak = False
    for line in main:
        # Check if this is an include of an unwritten chapter
        if '#include' in line:
            # Extract filename
            fname = line.split('\"')[1].split('/')[-1] if '\"' in line else ''
            if fname and fname not in written:
                skip_next_pagebreak = True
                continue  # skip this include

        # Skip pagebreak after skipped chapter
        if skip_next_pagebreak and 'pagebreak' in line:
            skip_next_pagebreak = False
            continue

        skip_next_pagebreak = False
        out.write(line)
"

typst compile main-draft.typ utlx-book-draft.pdf 2>&1
if [ $? -eq 0 ]; then
    PAGES=$(python3 -c "
import subprocess
r = subprocess.run(['mdls', '-name', 'kMDItemNumberOfPages', 'utlx-book-draft.pdf'], capture_output=True, text=True)
print(r.stdout.strip().split('= ')[1])
" 2>/dev/null)
    echo "  utlx-book-draft.pdf — $PAGES pages (written chapters only)"
else
    echo "  ERROR: draft failed to compile"
fi

rm -f main-draft.typ

echo ""
echo "Done."
