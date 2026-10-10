#!/usr/bin/env python3
"""
Find, per format, the nesting depth where the parser stops cleanly ACCEPTING — by binary search.

For each format it reports:
  - max_ok      : the deepest input that parses (accept)
  - first_fail  : one level deeper, where it stops accepting
  - state       : what happens there —
                    'crash'  = StackOverflowError / OOM / uncaught (unbounded recursion — cf B32)
                    'reject' = a clean, bounded parse error (the DESIRED state once a depth bound exists)

So this doubles as a **B32 regression probe**: today we expect `crash`; after a `max_depth` bound is
added it should flip to `reject` at the configured limit.

Method: exponential search up to the first non-accepting depth, then bisect — O(log depth) parser
invocations (not linear). Depth is measured with the DEFAULT JVM stack (whatever `./utlx` uses); the
absolute number scales with `-Xss`, so treat it as "with default stack," not a fixed parser constant.

Usage:
  python3 test-corpora/scripts/find-crash-depth.py [--formats json,xml,yaml] [--cli ./utlx] [--cap N] [-v]
"""
import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
CRASH_MARKERS = ("StackOverflowError", "OutOfMemoryError", 'Exception in thread "main"')
EXT = {"json": "json", "yaml": "yaml", "xml": "xml"}


def nested(fmt, n):
    if fmt in ("json", "yaml"):   # JSON array / YAML flow sequence, n levels deep
        return "[" * n + "1" + "]" * n + "\n"
    if fmt == "xml":
        return "<a>" * n + "x" + "</a>" * n + "\n"
    raise SystemExit(f"nesting generator not defined for '{fmt}'")


def classify(cli, fmt, n, timeout):
    """accept | reject | crash — identity-transform probe at nesting depth n."""
    with tempfile.NamedTemporaryFile("w", suffix=f".{EXT[fmt]}", delete=False) as df:
        df.write(nested(fmt, n)); data_path = df.name
    with tempfile.NamedTemporaryFile("w", suffix=".utlx", delete=False) as sf:
        sf.write(f"%utlx 1.0\ninput {fmt}\noutput json\n---\n$input\n"); script_path = sf.name
    try:
        r = subprocess.run(cli + [script_path, data_path], capture_output=True, text=True, timeout=timeout)
        if r.returncode == 0:
            return "accept"
        err = r.stderr or r.stdout or ""
        return "crash" if any(m in err for m in CRASH_MARKERS) else "reject"
    except subprocess.TimeoutExpired:
        return "crash"   # a hang on deep input is a DoS too
    finally:
        os.unlink(data_path); os.unlink(script_path)


def find_boundary(cli, fmt, cap, timeout, verbose):
    probes = 0

    def accepts(n):
        nonlocal probes
        probes += 1
        c = classify(cli, fmt, n, timeout)
        if verbose:
            print(f"      depth {n:>9}: {c}")
        return c == "accept", c

    ok, c0 = accepts(1)
    if not ok:
        return {"fmt": fmt, "max_ok": 0, "first_fail": 1, "state": c0, "probes": probes}

    lo, hi, state = 1, 2, None
    while True:                                   # exponential: climb until a non-accept
        ok, c = accepts(hi)
        if not ok:
            state = c
            break
        lo = hi
        hi *= 2
        if hi > cap:
            return {"fmt": fmt, "max_ok": f">={lo}", "first_fail": None,
                    "state": f"no-fail-under-cap({cap})", "probes": probes}

    while hi - lo > 1:                            # bisect between lo(accept) and hi(fail)
        mid = (lo + hi) // 2
        ok, c = accepts(mid)
        if ok:
            lo = mid
        else:
            hi, state = mid, c
    return {"fmt": fmt, "max_ok": lo, "first_fail": hi, "state": state, "probes": probes}


def main():
    ap = argparse.ArgumentParser(description="Binary-search the parser's nesting-depth limit per format")
    ap.add_argument("--formats", default="json,xml,yaml")
    ap.add_argument("--cli", default=str(REPO_ROOT / "utlx"))
    ap.add_argument("--cap", type=int, default=2_000_000, help="give up searching above this depth")
    ap.add_argument("--timeout", type=float, default=20.0)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    cli = args.cli.split()

    print("=" * 72)
    print("Parser nesting-depth boundary (default JVM stack) — binary search")
    print(f"CLI: {' '.join(cli)}")
    print("=" * 72)
    rows = []
    for fmt in [f.strip() for f in args.formats.split(",") if f.strip()]:
        print(f"\n[{fmt}]")
        r = find_boundary(cli, fmt, args.cap, args.timeout, args.verbose)
        rows.append(r)
        print(f"   max accepted depth : {r['max_ok']}")
        print(f"   first failing depth: {r['first_fail']}   → {r['state']}")
        print(f"   (parser invocations: {r['probes']})")
    print("\n" + "=" * 72)
    for r in rows:
        verdict = ("CRASH (unbounded recursion — B32)" if r["state"] == "crash"
                   else "clean reject (bounded ✓)" if r["state"] == "reject"
                   else r["state"])
        print(f"  {r['fmt']:5} deepest-ok={r['max_ok']:>10}   beyond → {verdict}")
    print("Note: absolute depths scale with the JVM -Xss; this is 'with default stack'.")


if __name__ == "__main__":
    sys.exit(main())
