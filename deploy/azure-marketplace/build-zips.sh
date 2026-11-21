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

# ── inner-scoped nested templates must declare every parameter they reference ──
# `expressionEvaluationOptions.scope: "inner"` means a nested template sees ONLY its own
# parameters. A `parameters('x')` inside it that the nested template does not declare is not a
# syntax error and arm-ttk does not report it -- it fails at DEPLOYMENT, after the resource group
# and the environment have been created. That is the most expensive moment to find it.
#
# This has already happened once: the 1.4.4 fix that made the Dapr component `scopes` follow
# `namePrefix` referenced it inside the inner-scoped dapr-components template, which did not
# declare it. arm-ttk passed, the packages built, and nothing would have surfaced it before a
# deployment failed. EB18 was the same class of bug one template earlier.
python3 - "${PLANS[@]}" <<'PY1'
import json, re, sys

bad = False
for plan in sys.argv[1:]:
    d = json.load(open(f"{plan}/mainTemplate.json"))
    for res in d.get("resources", []):
        pr = res.get("properties", {})
        if pr.get("expressionEvaluationOptions", {}).get("scope") != "inner":
            continue
        tpl = pr.get("template", {})
        declared = set(tpl.get("parameters", {}).keys())
        passed = set(pr.get("parameters", {}).keys())
        refs = set(re.findall(r"parameters\('([A-Za-z0-9_]+)'\)", json.dumps(tpl)))

        undeclared = sorted(refs - declared)
        if undeclared:
            print(f"ERROR: {plan} nested template '{res.get('name')}' is inner-scoped and "
                  f"references undeclared parameter(s): {', '.join(undeclared)}", file=sys.stderr)
            bad = True

        unpassed = sorted(declared - passed)
        if unpassed:
            print(f"ERROR: {plan} nested template '{res.get('name')}' declares "
                  f"{', '.join(unpassed)} but the parent passes no value", file=sys.stderr)
            bad = True

sys.exit(1 if bad else 0)
PY1

# ── every offered size must add up to a legal Container Apps total ──
# Azure refuses a replica whose containers do not sum to one of its allowed CPU/memory pairs, and
# in every pair memory is exactly 2x CPU. The engine's cpuCores/memoryGi are only PART of the
# replica — the 0.25 vCPU / 0.5 Gi admin-UI sidecar is the rest — so a size that looks reasonable
# on its own can be rejected at deployment.
#
# That is not hypothetical: professional shipped asking for 4.0 + 0.25 = 4.25 vCPU, one notch over
# the Consumption maximum, and the deployment failed with ContainerAppInvalidResourceTotal after
# the resource group and environment had already been created. arm-ttk does not catch it; only a
# real deployment did. This checks every value the templates OFFER, not just the default.
python3 - "${PLANS[@]}" <<'PYSZ'
import json, sys

SIDECAR_CPU, SIDECAR_MEM = 0.25, 0.5
LEGAL = {(round(0.25 * i, 2), round(0.5 * i, 2)) for i in range(1, 17)}   # up to 4.0 / 8.0

bad = False
for plan in sys.argv[1:]:
    pr = json.load(open(f"{plan}/mainTemplate.json"))["parameters"]
    cpus = pr["cpuCores"]["allowedValues"]
    mems = pr["memoryGi"]["allowedValues"]

    if len(cpus) != len(mems):
        print(f"ERROR: {plan} offers {len(cpus)} CPU values and {len(mems)} memory values; they "
              f"are chosen as a PAIR, so the lists must be the same length", file=sys.stderr)
        bad = True
        continue

    for c, m in zip(cpus, mems):
        total = (round(float(c) + SIDECAR_CPU, 2), round(float(m) + SIDECAR_MEM, 2))
        if total not in LEGAL:
            print(f"ERROR: {plan} offers engine {c} vCPU / {m} Gi; with the sidecar that is "
                  f"{total[0]} / {total[1]}, which Azure rejects "
                  f"(ContainerAppInvalidResourceTotal)", file=sys.stderr)
            bad = True

    for key in ("cpuCores", "memoryGi"):
        dv = pr[key]["defaultValue"]
        if dv not in pr[key]["allowedValues"]:
            print(f"ERROR: {plan} {key} default {dv!r} is not in its own allowedValues",
                  file=sys.stderr)
            bad = True

    heap = json.loads(json.dumps(
        json.load(open(f"{plan}/mainTemplate.json"))["resources"][1]["properties"]["template"]["variables"]["jvmHeapMb"]))
    missing = [m for m in mems if m not in heap]
    if missing:
        print(f"ERROR: {plan} jvmHeapMb has no entry for memoryGi {', '.join(missing)} — the "
              f"template would resolve -Xmx to nothing", file=sys.stderr)
        bad = True

sys.exit(1 if bad else 0)
PYSZ

# ── the engine and the templates must agree on Dapr component names ──
# Dapr component names are a CONTRACT between the app and its sidecar config, not Azure resource
# names. The engine addresses components by name; the templates create them by name; nothing
# checked that the two sets relate. They did not: the engine's pub/sub path addresses
# "utlxe-servicebus" (EF26) and no template has ever created it, so that whole path is unreachable
# in Azure and stayed that way unnoticed.
#
# Fails on an ORPHAN — a component the templates create that the engine never addresses, which is
# money spent on nothing and a sign of a rename gone half-done. Only NOTES the reverse, because
# the pub/sub component is a known gap rather than a regression; promote it to a failure when
# EF26 item 2a lands.
python3 - "${PLANS[@]}" <<'PY2A'
import json, re, sys, pathlib

root = pathlib.Path("../..")
engine_src = list((root / "modules/engine/src/main/kotlin").rglob("*.kt")) + \
             list((root / "modules/core/src/main/kotlin").rglob("*.kt"))
blob = "\n".join(p.read_text(errors="ignore") for p in engine_src)

# names the engine addresses: the pub/sub constant, plus any output-binding env mapping the
# templates set (UTLXE_OUTPUT_BINDING_<INPUT> = <output component>).
m = re.search(r'DEFAULT_PUBSUB_NAME\s*=\s*"([^"]+)"', blob)
engine_pubsub = m.group(1) if m else None

bad = False
for plan in sys.argv[1:]:
    d = json.loads(pathlib.Path(f"{plan}/mainTemplate.json").read_text())
    created, addressed = set(), set()
    for res in d["resources"]:
        for r in res.get("properties", {}).get("template", {}).get("resources", []):
            if r.get("type", "").endswith("daprComponents"):
                nm = r["name"]
                created.add(nm.split("'")[-2] if "'" in nm else nm)
    # A component is addressed if the engine is pointed at it. Two ways that happens:
    #   - the template names it in a nested template's `variables` (containerEnv builds the
    #     UTLXE_OUTPUT_BINDING_* env var from it);
    #   - it is an INPUT component, which Dapr delivers to directly without the engine naming it.
    # Matching on the variables blob rather than parsing ARM expressions: the first version of
    # this guard used a regex that stopped at the first ')' inside parameters(...), so it missed
    # `if(parameters('enableEventHubs'), 'eventhub-output', 'servicebus-output')` and reported
    # both outputs as orphans. A guard that cries wolf gets deleted.
    variables_blob = json.dumps([res.get("properties", {}).get("template", {}).get("variables", {})
                                 for res in d["resources"]])
    addressed.update(n for n in created if n in variables_blob)
    addressed.update(n for n in created if n.endswith("-input"))
    # the pub/sub component is addressed by the engine through DaprComponents.pubsubName, not by
    # anything in the template, so match it against the constant read out of the engine source
    if engine_pubsub:
        addressed.add(engine_pubsub)

    orphans = sorted(created - addressed)
    if orphans:
        print(f"ERROR: {plan} creates Dapr component(s) nothing addresses: {', '.join(orphans)}",
              file=sys.stderr)
        bad = True
    if engine_pubsub and engine_pubsub not in created:
        print(f"  NOTE: {plan} creates no '{engine_pubsub}' component, so the engine's pub/sub "
              f"path is unreachable there (EF26 item 2a).")

sys.exit(1 if bad else 0)
PY2A

# ── guards for two traps removed in 1.4.4 ──
# Both were invisible for several releases, and both are one careless edit away from returning.
python3 - "${PLANS[@]}" <<'PY0'
import json, sys

bad = False
for plan in sys.argv[1:]:
    raw = open(f"{plan}/mainTemplate.json").read()
    d = json.loads(raw)

    # (a) --workers is not read in HTTP mode. Passing it is how the plans came to be sold on a
    #     number that controls nothing. The parameter is gone; keep it gone.
    if "workers" in raw:
        print(f"ERROR: {plan} mentions 'workers'. --workers is read only by StdioProtoTransport;"
              f" the offering runs --mode http, where it has no effect.", file=sys.stderr)
        bad = True

    # (b) Dapr component `scopes` must track the appId, which is format('{0}-app', namePrefix).
    #     A literal here matches only while namePrefix happens to be its default: any other prefix
    #     leaves the components scoped to an appId that does not exist, so the input binding never
    #     delivers and the output binding fails -- silently, at runtime.
    for res in json.dumps(d).split('"scopes":')[1:]:
        scope = res.split(']')[0] + ']'
        if "parameters('namePrefix')" not in scope:
            print(f"ERROR: {plan} has a hardcoded Dapr scope {scope.strip()};"
                  f" use \"[format('{{0}}-app', parameters('namePrefix'))]\"", file=sys.stderr)
            bad = True

sys.exit(1 if bad else 0)
PY0

# ── the drift guard: the plans must not be copies of each other ──
python3 - "${PLANS[@]}" <<'PY'
import json, sys

plans = sys.argv[1:]
vals  = {}
for p in plans:
    d = json.load(open(f"{p}/mainTemplate.json"))["parameters"]
    vals[p] = {k: d[k]["defaultValue"] for k in ("cpuCores", "memoryGi")}

# `workers` used to appear here. It was removed from the templates in 1.4.4: the offering runs
# `--mode http`, and --workers is read only by StdioProtoTransport, so the 8-vs-32 difference this
# guard was checking controlled nothing while the listing sold it as the difference between plans.
# These are the ENGINE's share, not the replica's. The replica also carries the 0.25 vCPU /
# 0.5 Gi admin-UI sidecar, and Azure validates the TOTAL. professional used to read 4.0 / 8.0
# here, which totalled 4.25 / 8.5 and was rejected at deployment with
# ContainerAppInvalidResourceTotal — so the plan could not be deployed at all. It is now 3.75 /
# 7.5, which totals exactly 4.0 / 8.0: the largest a Consumption replica may be.
expected = {
    "starter":      {"cpuCores": "1.75", "memoryGi": "3.5"},   # total 2.0  / 4.0
    "professional": {"cpuCores": "3.75", "memoryGi": "7.5"},   # total 4.0  / 8.0
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

SIZING = {"cpuCores", "memoryGi", "maxReplicas"}
plans = sys.argv[1:]
if len(plans) != 2:
    sys.exit(0)   # only meaningful for a two-plan offer

def normalise(path):
    d = json.load(open(path))
    for k in list(d.get("parameters", {})):
        if k in SIZING:
            # Both the default AND the offered range are plan-specific: that is the whole point
            # of having two plans. Only defaultValue used to be stripped, which was enough while
            # the plans happened to offer identical lists. Since professional's sizes had to
            # change (its old default totalled 4.25 vCPU and could not deploy), the lists differ
            # legitimately and the guard must stop reading that as drift.
            d["parameters"][k].pop("defaultValue", None)
            d["parameters"][k].pop("allowedValues", None)
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
