#!/usr/bin/env bash
# Audit every GitHub Action this repo uses and report its Node runtime.
#
# GitHub deprecates Node runtimes on a rolling basis (Node 16, then 20, ...).
# When a runtime goes away, every workflow pinned to an action built on it
# starts failing at once. This finds them before that happens.
#
# Dogfooding note: the JSON here is read with UTL-X, not jq. `output csv` on a
# bare scalar emits raw text, which is the `jq -r` equivalent. See
# scripts/utlx/*.utlx.
set -uo pipefail

UTLX="${UTLX:-modules/cli/build/native/nativeCompile/utlx}"
if [[ ! -x "$UTLX" ]]; then
    echo "utlx not found at $UTLX" >&2
    echo "build it with: ./gradlew :modules:cli:nativeCompile   (or set UTLX=...)" >&2
    exit 2
fi
command -v gh >/dev/null || { echo "gh CLI is required" >&2; exit 2; }

WORK=$(mktemp -d); trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/using.utlx" <<'EOF'
%utlx 1.0
// GitHub returns file contents base64-encoded in `.content`; we only need to
// know whether the decoded action.yml says node16/node20/node24. Decode here
// so the shell never has to.
input json
output csv
---
input.content
EOF

printf '%-32s %-6s %-9s %s\n' ACTION REF RUNTIME WORKFLOWS
printf '%s\n' "------------------------------------------------------------------------"

stale=0
# collect "action@ref file" triples from every workflow
grep -ohE 'uses:[[:space:]]*[A-Za-z0-9._-]+/[A-Za-z0-9._-]+@[A-Za-z0-9._-]+' .github/workflows/*.y*ml \
  | sed -E 's/uses:[[:space:]]*//' | sort -u | while read -r ref; do
    action="${ref%@*}"; tag="${ref#*@}"
    files=$(grep -lE "uses:[[:space:]]*${action}@${tag}([[:space:]]|$)" .github/workflows/*.y*ml \
            | xargs -n1 basename | paste -sd, -)
    runtime="?"
    for fn in action.yml action.yaml; do
        b64=$(gh api "repos/${action}/contents/${fn}?ref=${tag}" --jq '.content' 2>/dev/null) || continue
        [[ -z "$b64" ]] && continue
        # action.yml quotes this inconsistently across publishers:
        #   actions/checkout  ->  using: node24
        #   softprops/...     ->  using: "node24"
        # so accept either quote style, or none.
        runtime=$(printf '%s' "$b64" | base64 -d 2>/dev/null \
                  | grep -oE "using:[[:space:]]*[\"']?[A-Za-z0-9.]+" | head -1 \
                  | sed -E "s/using:[[:space:]]*[\"']?//")
        break
    done
    flag=""
    case "$runtime" in
        node24|composite|docker) ;;
        *) flag="  <-- deprecated runtime" ;;
    esac
    printf '%-32s %-6s %-9s %s%s\n' "$action" "$tag" "$runtime" "$files" "$flag"
done
