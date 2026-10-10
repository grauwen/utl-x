#!/usr/bin/env python3
"""
UTL-X test-corpora harness — Phase 0a (runner) + Phase 0b (per-profile baseline).

What it does
------------
For every corpus input, run the CURRENT parser (an identity-transform probe via the utlx CLI),
record whether it ACCEPTED or REJECTED the input, then compare that against each profile's
*expected* verdict in `expectations/profiles/<profile>/parse-verdicts.yaml`.

Why "current parser vs profile spec"
------------------------------------
The lenient|standard|strict PROFILES are not implemented in the parsers yet (see
docs/architecture/parser-strictness-profiles.md §3). So this run measures ONE current parser
behaviour against each profile's SPEC. The per-profile MISMATCHES are the Phase-0c hardening
backlog — e.g. `strict` wants a trailing comma REJECTED but today's parser accepts it. That gap,
per profile, is the whole point of the Phase-0b baseline.

Verdict model
-------------
  accept  — parser returned 0 (input parsed to a UDM)
  reject  — parser returned non-zero with a clean error (desired failure mode)
  error   — timeout / crash / non-clean failure (a ROBUSTNESS problem even if it "rejects")

A profile expects `accept` or `reject`. `error` never satisfies an expectation — a reject-via-crash
is still a hardening issue and is reported separately.

Usage
-----
  python3 test-corpora/scripts/harness.py [--profile strict] [--cli ./utlx] [--json report.json] [-v]

Requires: python3, pyyaml; and a built utlx CLI (./gradlew :modules:cli:jar) for the --cli probe.
"""

import argparse
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.stderr.write("pyyaml required: pip3 install pyyaml\n")
    sys.exit(2)

SCRIPT_DIR = Path(__file__).resolve().parent
CORPORA_ROOT = SCRIPT_DIR.parent                      # test-corpora/
REPO_ROOT = CORPORA_ROOT.parent                       # utl-x/
PROFILES_DIR = CORPORA_ROOT / "expectations" / "profiles"

EXT_FORMAT = {".json": "json", ".xml": "xml", ".csv": "csv", ".yaml": "yaml", ".yml": "yaml"}

# UTL-X format tokens. Several SHARE a file extension (odata/jsch/osch are .json; xsd/tsch are .xml),
# so extension alone can't identify them — see resolve_format().
KNOWN_FORMATS = {"json", "xml", "csv", "yaml", "odata", "jsch", "xsd", "osch", "tsch", "avro", "protobuf"}


def resolve_format(rel, profiles):
    """Format detection, most-explicit first:
       1. an explicit `format:` on the oracle case (wins — for anything ambiguous),
       2. the format directory in the corpus path `corpora/<class>/<format>/<file>`
          (the immediate parent dir, e.g. .../odata/x.json -> odata),
       3. fallback: the file extension.
    Extension is last precisely because odata/jsch/xsd/… reuse .json/.xml."""
    for cases in profiles.values():
        c = cases.get(rel)
        if c and c.get("format"):
            return c["format"]
    parent = Path(rel).parent.name
    if parent in KNOWN_FORMATS:
        return parent
    return EXT_FORMAT.get(Path(rel).suffix.lower())

ACCEPT, REJECT, ERROR = "accept", "reject", "error"


def discover_profiles():
    """Every expectations/profiles/<name>/parse-verdicts.yaml defines a profile."""
    out = {}
    if not PROFILES_DIR.is_dir():
        return out
    for p in sorted(PROFILES_DIR.iterdir()):
        f = p / "parse-verdicts.yaml"
        if f.is_file():
            doc = yaml.safe_load(f.read_text()) or {}
            out[p.name] = {c["input"]: c for c in doc.get("cases", [])}
    return out


def probe(cli, input_path, fmt, timeout_s):
    """Identity-transform probe: does the CURRENT parser accept this input?"""
    # output json isolates the PARSE step (we measure input acceptance, not serializer round-trip)
    script = f"%utlx 1.0\ninput {fmt}\noutput json\n---\n$input\n"
    with tempfile.NamedTemporaryFile("w", suffix=".utlx", delete=False) as tf:
        tf.write(script)
        script_path = tf.name
    try:
        r = subprocess.run(
            cli + [script_path, str(input_path)],
            capture_output=True, text=True, timeout=timeout_s,
        )
        return (ACCEPT, "") if r.returncode == 0 else (REJECT, (r.stderr or r.stdout)[:400])
    except subprocess.TimeoutExpired:
        return (ERROR, f"timeout>{timeout_s}s")
    except Exception as e:  # crash / OSError
        return (ERROR, f"{type(e).__name__}: {e}")
    finally:
        os.unlink(script_path)


def main():
    ap = argparse.ArgumentParser(description="UTL-X test-corpora harness + per-profile baseline")
    ap.add_argument("--profile", help="only this profile (default: all discovered)")
    ap.add_argument("--cli", default=str(REPO_ROOT / "utlx"),
                    help="utlx CLI command (default: ./utlx); space-separated for e.g. 'java -jar x.jar'")
    ap.add_argument("--timeout", type=float, default=15.0, help="per-probe timeout seconds")
    ap.add_argument("--json", help="write the machine-readable baseline report here")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    cli = args.cli.split()
    profiles = discover_profiles()
    if args.profile:
        profiles = {k: v for k, v in profiles.items() if k == args.profile}
    if not profiles:
        sys.stderr.write("no profiles found under expectations/profiles/*/parse-verdicts.yaml\n")
        return 2

    # the union of all inputs any profile has an expectation for — probe each ONCE
    all_inputs = sorted({inp for cases in profiles.values() for inp in cases})
    actual = {}
    for rel in all_inputs:
        ip = CORPORA_ROOT / rel
        if not ip.is_file():
            actual[rel] = (ERROR, "input file missing")
            continue
        fmt = resolve_format(rel, profiles)
        if not fmt:
            actual[rel] = (ERROR, f"unknown format for {rel}")
            continue
        actual[rel] = probe(cli, ip, fmt, args.timeout)

    report = {"note": "current un-profiled parser vs each profile's spec; mismatches = Phase-0c backlog",
              "cli": " ".join(cli), "profiles": {}}
    for name, cases in profiles.items():
        rows, match, mismatch, errors = [], 0, 0, 0
        for rel, exp in cases.items():
            exp_v = exp["verdict"]
            act_v, detail = actual[rel]
            ok = (act_v == exp_v)
            if act_v == ERROR:
                errors += 1
            if ok:
                match += 1
            else:
                mismatch += 1
            rows.append({"input": rel, "expected": exp_v, "actual": act_v,
                         "match": ok, "reason": exp.get("reason"), "detail": detail})
        report["profiles"][name] = {"total": len(cases), "match": match,
                                    "mismatch": mismatch, "errors": errors, "cases": rows}

    # human summary
    print("=" * 72)
    print("UTL-X parser baseline — current behaviour vs profile spec")
    print(f"CLI: {' '.join(cli)}")
    print("=" * 72)
    for name, r in report["profiles"].items():
        print(f"\n[{name}]  {r['match']}/{r['total']} match   "
              f"({r['mismatch']} mismatch, {r['errors']} robustness-errors)")
        for row in r["cases"]:
            if not row["match"] or args.verbose:
                flag = "ok " if row["match"] else "GAP"
                print(f"   {flag}  {row['input']}")
                print(f"         expected={row['expected']}  actual={row['actual']}"
                      + (f"  ({row['reason']})" if row.get('reason') else ""))
                if row["actual"] == ERROR and row["detail"]:
                    print(f"         !! {row['detail']}")
    print("\n" + "=" * 72)
    print("Mismatches are EXPECTED here (profiles not yet implemented); each GAP is Phase-0c work.")

    if args.json:
        Path(args.json).write_text(json.dumps(report, indent=2))
        print(f"report → {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
