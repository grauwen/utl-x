#!/bin/bash
# Generate resource-bounds / abuse seeds (large or deep inputs) for corpora/security/.
# These exercise the ALWAYS-ON bounds axis (expectations/bounds.yaml), orthogonal to the grammar
# profile: well-formed input that must still be cut off (deep nesting, DoS). Idempotent.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # test-corpora/
S="$DIR/corpora/security/json"
mkdir -p "$S"

# Deeply-nested JSON arrays: '[' * N + '1' + ']' * N  → nesting depth N (well-formed, but deep).
#  100   → exceeds strict max_depth (64), under lenient (256): strict reject / standard,lenient accept
#  50000 → exceeds every bound AND risks unbounded recursion (stack overflow): reject in all, must NOT crash
python3 -c "n=100;   open('$S/deep_nesting_100.json','w').write('['*n + '1' + ']'*n + '\n')"
python3 -c "n=50000; open('$S/deep_nesting_50000.json','w').write('['*n + '1' + ']'*n + '\n')"

# Deeply-nested YAML flow sequences: '[' * N + '1' + ']' * N  → nesting depth N.
# SnakeYAML's nestingDepthLimit (now raised to the profile max_depth) bounds this CLEANLY — a limit
# exception, never a StackOverflowError (SnakeYAML is not a hand-written recursive descent; cf B32).
#  100 → > SnakeYAML's old default (50) and > strict (64), < standard/lenient (256): proves the raise
#  300 → > every profile's max_depth (256): reject in ALL profiles, and must classify as reject NOT error
Y="$DIR/corpora/security/yaml"
mkdir -p "$Y"
python3 -c "n=100; open('$Y/deep_nesting_100.yaml','w').write('['*n + '1' + ']'*n + '\n')"
python3 -c "n=300; open('$Y/deep_nesting_300.yaml','w').write('['*n + '1' + ']'*n + '\n')"

echo "wrote:"
echo "  $S/deep_nesting_100.json     (depth 100,   $(wc -c < "$S/deep_nesting_100.json" | tr -d ' ') bytes)"
echo "  $S/deep_nesting_50000.json   (depth 50000, $(wc -c < "$S/deep_nesting_50000.json" | tr -d ' ') bytes)"
echo "  $Y/deep_nesting_100.yaml     (depth 100,   $(wc -c < "$Y/deep_nesting_100.yaml" | tr -d ' ') bytes)"
echo "  $Y/deep_nesting_300.yaml     (depth 300,   $(wc -c < "$Y/deep_nesting_300.yaml" | tr -d ' ') bytes)"
