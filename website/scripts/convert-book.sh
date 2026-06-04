#!/usr/bin/env bash
#
# Convert the UTL-X language book (Typst) → Markdown for the VitePress site.
# Run from anywhere: website/scripts/convert-book.sh
#
# - Each books/language/chapters/<ch>.typ → website/guide/<ch>.md (pandoc, GFM)
# - Image paths (#figure(image("../pictures/..."))) are rewritten to /pictures/...
# - books/language/pictures/ is copied to website/public/pictures/ (served at /pictures/)
#
# The generated Markdown is committed so Coolify only needs Node (no pandoc at build time).
# Re-run this whenever the book chapters change.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"   # repo root
SRC="$ROOT/books/language/chapters"
OUT="$ROOT/website/guide"
PUB="$ROOT/website/public"

command -v pandoc >/dev/null || { echo "pandoc not found — install it (brew install pandoc)"; exit 1; }

mkdir -p "$OUT" "$PUB"

# Copy chapter images into the site's public dir (served at site root).
rm -rf "$PUB/pictures"
cp -R "$ROOT/books/language/pictures" "$PUB/pictures"

# Lowercase every picture dir/file name. The book's image references are lowercase (e.g.
# /pictures/ch10-udm/…) but some source dirs are not (pictures/ch10-UDM). macOS is
# case-insensitive and hides this; Linux (Docker/Coolify) is case-sensitive and the build
# fails to resolve the image. Normalize the files to lowercase to match the references.
find "$PUB/pictures" -depth | while IFS= read -r p; do
    b="$(basename "$p")"; lb="$(printf '%s' "$b" | tr '[:upper:]' '[:lower:]')"
    if [ "$b" != "$lb" ]; then mv "$p" "$(dirname "$p")/$lb"; fi
done

count=0; failed=0
for f in "$SRC"/*.typ; do
    base="$(basename "$f" .typ)"
    # The stdlib appendix is published as the per-function /reference/ (see build-stdlib.mjs),
    # not as guide chapters — skip it here to avoid duplicate content.
    case "$base" in ch50-stdlib-*) continue ;; esac
    # gfm-raw_html: disable raw-HTML passthrough so pandoc emits pipe tables + markdown images
    # (raw <table>/<figure> break VitePress's Vue compiler — e.g. a `…` cell became a bad attr).
    if pandoc -f typst -t gfm-raw_html "$f" -o "$OUT/$base.md" 2>/tmp/pandoc-err; then
        # Normalize image paths — markdown ![](…pictures/…) and any <img src> — to /pictures/…
        sed -i '' -E -e 's#\]\([^)]*pictures/#](/pictures/#g' -e 's#src="[^"]*pictures/#src="/pictures/#g' "$OUT/$base.md"
        # Smart-typography chars the author used in the Typst source break VitePress's compiler
        # (a `…` cell becomes a bad Vue attribute) and corrupt code examples (curly quotes).
        # Normalize to ASCII: … → ..., curly quotes → straight.
        perl -CSD -i -pe 's/\x{2026}/.../g; s/[\x{201C}\x{201D}]/"/g; s/[\x{2018}\x{2019}]/\x27/g;' "$OUT/$base.md"
        # Lowercase the image-path references (match the lowercased picture files; case-safe on Linux).
        perl -CSD -i -pe 's{(\]\(/pictures/)([^)]+)}{$1.lc($2)}ge; s{(src="/pictures/)([^"]+)}{$1.lc($2)}ge;' "$OUT/$base.md"
        count=$((count + 1))
    else
        echo "  ⚠ skipped $base — pandoc: $(head -1 /tmp/pandoc-err)"
        rm -f "$OUT/$base.md"
        failed=$((failed + 1))
    fi
done

echo "Converted $count chapters → $OUT  ($failed skipped)"
echo "Copied images → $PUB/pictures"
