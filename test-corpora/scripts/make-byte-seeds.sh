#!/bin/bash
# Generate corpus seeds that contain raw bytes a text editor would mangle (BOM, control chars).
# Idempotent. Run from anywhere.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # test-corpora/
M="$DIR/corpora/malformed/json"
mkdir -p "$M"

# Leading UTF-8 BOM (EF BB BF) before an otherwise-valid object.
printf '\xEF\xBB\xBF{"id":42,"name":"alice"}\n' > "$M/leading_bom.json"

# A raw U+0001 control character inside a JSON string (unescaped — illegal per RFC 8259).
printf '{"note":"a\x01b"}\n' > "$M/control_char.json"

# YAML indented with a TAB (0x09) — illegal: YAML forbids tabs for indentation.
Y="$DIR/corpora/malformed/yaml"
mkdir -p "$Y"
printf 'parent:\n\tchild: x\n' > "$Y/tab_indent.yaml"

echo "wrote:"
echo "  $M/leading_bom.json   ($(wc -c < "$M/leading_bom.json" | tr -d ' ') bytes)"
echo "  $M/control_char.json  ($(wc -c < "$M/control_char.json" | tr -d ' ') bytes)"
echo "  $Y/tab_indent.yaml    ($(wc -c < "$Y/tab_indent.yaml" | tr -d ' ') bytes)"
