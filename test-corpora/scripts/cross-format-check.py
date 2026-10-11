#!/usr/bin/env python3
"""
Cross-format compositional proof (§5.5 of utlx-test-corpora.md).

The SAME logical data expressed in multiple formats must parse to the SAME UDM. For each dataset under
`cross-format/<name>/`, parse every `data.<fmt>` through the CLI to canonical JSON, normalise (sorted
keys), and check all formats agree. Divergences are either real bugs OR legitimate, documentable losses
(`expectations/known-deviations.yaml`) — the checker surfaces them; a human classifies which.

This is the N-readers-agree check: N format readers against one expected UDM, not N² pairwise mappings.

Usage:
  python3 test-corpora/scripts/cross-format-check.py [--cli ./utlx] [-v]
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
CORPORA_ROOT = SCRIPT_DIR.parent
REPO_ROOT = CORPORA_ROOT.parent
XF_ROOT = CORPORA_ROOT / "cross-format"
EXT_FORMAT = {".json": "json", ".yaml": "yaml", ".yml": "yaml", ".xml": "xml", ".csv": "csv"}


def to_udm_json(cli, path, fmt, timeout):
    """Parse `path` as `fmt` → canonical JSON (normalised), or (None, error)."""
    with tempfile.NamedTemporaryFile("w", suffix=".utlx", delete=False) as sf:
        sf.write(f"%utlx 1.0\ninput {fmt}\noutput json\n---\n$input\n")
        script_path = sf.name
    try:
        r = subprocess.run(cli + [script_path, str(path)], capture_output=True, text=True, timeout=timeout)
        if r.returncode != 0:
            return None, "parse error: " + (r.stderr or r.stdout or "").strip().splitlines()[0][:160]
        try:
            return json.dumps(json.loads(r.stdout), sort_keys=True), None
        except Exception as e:
            return None, f"non-JSON output: {e}"
    except subprocess.TimeoutExpired:
        return None, "timeout"
    finally:
        os.unlink(script_path)


def main():
    ap = argparse.ArgumentParser(description="Cross-format compositional proof (same data → same UDM)")
    ap.add_argument("--cli", default=str(REPO_ROOT / "utlx"))
    ap.add_argument("--timeout", type=float, default=15.0)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    cli = args.cli.split()

    datasets = sorted(d for d in XF_ROOT.iterdir() if d.is_dir()) if XF_ROOT.is_dir() else []
    if not datasets:
        sys.stderr.write("no datasets under cross-format/\n")
        return 1

    print("=" * 64)
    print("Cross-format compositional proof — same data must yield the same UDM")
    print("=" * 64)
    overall_ok = True
    for ds in datasets:
        files = sorted(f for f in ds.iterdir() if f.is_file() and f.name.startswith("data."))
        print(f"\n[{ds.name}]  ({', '.join(f.suffix.lstrip('.') for f in files)})")
        groups, errors = {}, {}
        for f in files:
            fmt = EXT_FORMAT.get(f.suffix.lower())
            if not fmt:
                continue
            nj, err = to_udm_json(cli, f, fmt, args.timeout)
            if err:
                errors[fmt] = err
            else:
                groups.setdefault(nj, []).append(fmt)
        for fmt, e in errors.items():
            overall_ok = False
            print(f"   ERROR  {fmt}: {e}")
        if len(groups) == 1 and not errors:
            fmts = next(iter(groups.values()))
            print(f"   ✓ agree: {', '.join(fmts)}")
            if args.verbose:
                print(f"     UDM(json) = {next(iter(groups))}")
        elif len(groups) > 1:
            overall_ok = False
            print(f"   ✗ DIVERGE into {len(groups)} UDM shape(s) — bug or known-deviation:")
            for nj, fmts in groups.items():
                print(f"       {', '.join(fmts):10} → {nj}")

    print("\n" + "=" * 64)
    print("All datasets agree ✓" if overall_ok
          else "Divergences/errors — classify each as a bug or a known-deviation (known-deviations.yaml).")
    return 0 if overall_ok else 1


if __name__ == "__main__":
    sys.exit(main())
