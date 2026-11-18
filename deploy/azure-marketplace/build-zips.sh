#!/usr/bin/env bash
#
# Build the Azure Marketplace managed-application packages.
#
#   ./build-zips.sh            # version taken from the image tag in starter/mainTemplate.json
#   ./build-zips.sh 1.4.0      # or stated explicitly (must match the templates)
#
# Each plan ships as a flat zip holding exactly `mainTemplate.json` and
# `createUiDefinition.json` at the archive root — Partner Center rejects any other layout.
#
# WHY THIS SCRIPT EXISTS
# ----------------------
# The 1.1.0 -> 1.2.1 bump was made by hand, and the edit landed in the zips but not in the
# plan directories. By 1.4.0 the `professional/` directory had drifted far enough to describe
# a Professional plan with the STARTER worker count (8 instead of 32) — a package that would
# have billed as Professional and run at a quarter of the advertised concurrency. Nothing
# caught it, because nothing rebuilt from the directories.
#
# So: the plan directories are the source of truth, this script is the only way to produce a
# package from them, and the checks below fail the build rather than shipping a wrong one.

set -euo pipefail
cd "$(dirname "$0")"

PLANS=(starter professional)
OUT=zips

# ── version: read it off the templates rather than trusting an argument ──
tag_of() {
  python3 -c "
import json,sys
d = json.load(open('$1/mainTemplate.json'))
print(d['parameters']['containerImage']['defaultValue'].rsplit(':', 1)[1])
"
}

VERSION="${1:-$(tag_of starter)}"

echo "Building UTLXe Marketplace packages — version ${VERSION}"

# ── checks ──
fail() { echo "ERROR: $*" >&2; exit 1; }

for plan in "${PLANS[@]}"; do
  for f in mainTemplate.json createUiDefinition.json; do
    [ -f "$plan/$f" ] || fail "$plan/$f is missing"
    python3 -m json.tool "$plan/$f" >/dev/null || fail "$plan/$f is not valid JSON"
  done

  # Every image reference in the plan must carry the version being built. Three references
  # exist per plan (two in mainTemplate, one in createUiDefinition) and the 1.2.1 bump proves
  # how easy it is to update some and not others.
  stale=$(grep -ohI -E "ghcr\.io/utlx-lang/[a-z-]+:[0-9][0-9A-Za-z.-]*" "$plan"/*.json |
          grep -v ":${VERSION}\$" || true)
  [ -z "$stale" ] || fail "$plan pins images off the ${VERSION} line:
$stale"

  [ "$(tag_of "$plan")" = "$VERSION" ] ||
    fail "$plan/mainTemplate.json pins $(tag_of "$plan"), not ${VERSION}"
done

# ── the drift guard: the plans must not be copies of each other ──
python3 - "${PLANS[@]}" <<'PY'
import json, sys

plans = sys.argv[1:]
vals  = {}
for p in plans:
    d = json.load(open(f"{p}/mainTemplate.json"))["parameters"]
    vals[p] = {k: d[k]["defaultValue"] for k in ("workers", "cpuCores", "memoryGi")}

expected = {
    "starter":      {"workers": 8,  "cpuCores": "2.0", "memoryGi": "4.0"},
    "professional": {"workers": 32, "cpuCores": "4.0", "memoryGi": "8.0"},
}

bad = False
for p in plans:
    want, got = expected.get(p), vals[p]
    if want and want != got:
        print(f"ERROR: {p} sizing is {got}, expected {want}", file=sys.stderr)
        bad = True

# The failure that actually happened: professional silently carrying starter's numbers.
if len({tuple(sorted(v.items())) for v in vals.values()}) != len(vals):
    print("ERROR: two plans have identical sizing — one was copied over the other",
          file=sys.stderr)
    bad = True

sys.exit(1 if bad else 0)
PY

# ── the plans must be identical apart from sizing ──
# The sizing check above catches a plan carrying the WRONG numbers. It cannot catch a plan
# carrying the wrong everything-else, and that has now happened twice:
#
#   1.1.0 -> 1.2.1   professional/ kept starter's worker count for two releases
#   1.4.3            a `break` in an edit script renamed an output in starter only, and
#                    arm-ttk failed professional alone
#
# Both were found by accident. Compare the two plans directly: outside the three sizing
# parameters and the plan-specific wording, they must agree exactly.
python3 - "${PLANS[@]}" <<'PY2'
import json, sys

SIZING = {"workers", "cpuCores", "memoryGi", "maxReplicas"}
plans = sys.argv[1:]
if len(plans) != 2:
    sys.exit(0)   # only meaningful for a two-plan offer

def normalise(path):
    d = json.load(open(path))
    for k in list(d.get("parameters", {})):
        if k in SIZING:
            d["parameters"][k].pop("defaultValue", None)
    return d

a, b = (normalise(f"{p}/mainTemplate.json") for p in plans)
if a != b:
    # Report WHERE, not just THAT — a bare "they differ" on a 900-line template is useless.
    sa, sb = json.dumps(a, indent=1).split("\n"), json.dumps(b, indent=1).split("\n")
    import difflib
    diff = [l for l in difflib.unified_diff(sa, sb, plans[0], plans[1], lineterm="", n=1)][:40]
    print(f"ERROR: {plans[0]} and {plans[1]} differ outside sizing:", file=sys.stderr)
    for l in diff:
        print("  " + l, file=sys.stderr)
    sys.exit(1)
PY2

# ── arm-ttk, when it is available ──
# Microsoft runs the ARM template test toolkit against these files under certification policy
# 300.4.1.1. The 1.4.2 submission was rejected by it, and the round trip cost two days for
# failures the toolkit names in thirty seconds. Run it here when pwsh is present; skip loudly
# when it is not, so a missing toolkit never silently becomes a passing build.
ARM_TTK="${ARM_TTK:-$HOME/arm-ttk/arm-ttk/arm-ttk.psd1}"
[ -f "$ARM_TTK" ] || ARM_TTK="/tmp/arm-ttk/arm-ttk/arm-ttk.psd1"

if command -v pwsh >/dev/null 2>&1 && [ -f "$ARM_TTK" ]; then
  for plan in "${PLANS[@]}"; do
    echo "  arm-ttk: $plan"
    out=$(pwsh -NoProfile -c "Import-Module '$ARM_TTK'; Test-AzTemplate -TemplatePath './$plan'" 2>&1) || true
    fails=$(printf '%s\n' "$out" | grep -E "^Fail\s*:" | grep -oE "[0-9]+" | head -1)
    if [ "${fails:-0}" != "0" ]; then
      printf '%s\n' "$out" | grep -B1 -A3 "^\s*\[-\]" || true
      fail "arm-ttk reported ${fails} failure(s) for ${plan}.
  Fix them before packaging — certification runs the same suite and the round trip is two days.
  Note: [?] apiVersion results are indeterminate, not failures: the toolkit ships a version
  cache that lags the live providers."
    fi
  done
else
  printf '  %s\n' "NOTE: arm-ttk not run (needs pwsh and the toolkit)."
  printf '  %s\n' "      brew install powershell && git clone --depth 1 https://github.com/Azure/arm-ttk.git /tmp/arm-ttk"
  printf '  %s\n' "      Certification runs it regardless; a clean build here does not mean a clean submission."
fi

# ── build ──
mkdir -p "$OUT"
for plan in "${PLANS[@]}"; do
  zipfile="$OUT/utlxe-${plan}-${VERSION}.zip"
  rm -f "$zipfile"
  # -j flattens: the JSON must sit at the archive root, not under a directory.
  # -X drops extra file attributes so the archive is reproducible.
  (cd "$plan" && zip -q -j -X "../$zipfile" mainTemplate.json createUiDefinition.json)
  printf '  %-44s %6s bytes\n' "$zipfile" "$(wc -c < "$zipfile" | tr -d ' ')"
  unzip -l "$zipfile" | sed -n '4,5p'
done

echo
echo "Done. Upload these in Partner Center under the matching plan:"
echo "  offer:  UTL-X Transformation Engine (44ab35db-fe99-48ef-b182-ffe9d0c919e3)"
echo "  plans:  starter / professional"
echo
echo "The images must be PUBLIC on ghcr.io before the package is submitted — the templates"
echo "carry no registryCredentials, so Azure pulls anonymously."
