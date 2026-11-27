#!/usr/bin/env python3
"""Fail when a release version is written down anywhere but build.gradle.kts.

This defect has now been fixed three times in this repository:

  EB19  /admin/info answered the literal "1.0.1" — wrong on every release since it was typed
  CB08  the CLI banner said 1.4.0 while running from cli-1.4.3.jar
  CB14  four wrapper scripts pinned cli-1.4.4.jar / utlxd-1.4.4.jar, so a bump would silently
        keep launching the previous build; and the daemon and engine restated "1.4.0" in five
        more places, including /api/health and the LSP initialize handshake
  CB15  six CI steps pinned a jar by version, including the UTLXE_JAR_PATH both wrapper suites
        read — and those suites SKIP when the jar is missing, so they had quietly stopped
        running while CI reported success

Each fix was correct and none prevented the next, because nothing checked. The information is
never missing: every jar manifest carries Implementation-Version, stamped from project.version.
So the rule is simply that nobody restates it.

Run from the repository root:  python3 scripts/check-no-pinned-versions.py
"""
import re
import sys
from pathlib import Path

# A version literal assigned or mapped in Kotlin source.
KOTLIN_VERSION = re.compile(r'(?:=|to)\s*"\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?"')
# A jar named with its version inside a launcher script.
PINNED_JAR = re.compile(r'(?:cli|utlxd|utlxe)-\d+\.\d+\.\d+\.jar')

# Where a version legitimately appears: the single source of truth, plus docs and tests that
# describe or assert specific historical releases.
SOURCE_OF_TRUTH = {"build.gradle.kts"}

def scan() -> list[str]:
    problems: list[str] = []

    for module_src in sorted(Path("modules").glob("*/src/main/kotlin")):
        for path in sorted(module_src.rglob("*.kt")):
            for lineno, line in enumerate(path.read_text(errors="replace").split("\n"), 1):
                if line.lstrip().startswith(("//", "*", "/*")):
                    continue                      # a comment may cite a version
                if KOTLIN_VERSION.search(line):
                    problems.append(
                        f"{path}:{lineno}: restates a version — read it from the jar manifest "
                        f"(`X::class.java.`package`?.implementationVersion ?: \"dev\"`)\n"
                        f"    {line.strip()[:110]}"
                    )

    # CI is the place this mattered most and the place the first version of this check missed.
    # `.github/workflows/go-ci.yml` and `dotnet-ci.yml` set UTLXE_JAR_PATH to utlxe-1.4.0.jar one
    # step after building utlxe-<project.version>.jar. The wrapper integration tests **skip** when
    # the jar is absent, so both suites silently stopped running while CI stayed green — and the
    # Go workflow exists precisely because "nothing ran the Go tests" (WB02). A pinned jar in a
    # workflow is therefore worse than one in a launcher: it turns a test suite into a no-op.
    for workflow in sorted(Path(".github/workflows").glob("*.yml")):
        for lineno, line in enumerate(workflow.read_text(errors="replace").split("\n"), 1):
            if line.lstrip().startswith("#"):
                continue
            if PINNED_JAR.search(line):
                problems.append(
                    f"{workflow}:{lineno}: pins a jar by version — resolve the built jar instead "
                    f"(`ls -t …/utlxe-*.jar | head -n 1`), and fail if there is none\n"
                    f"    {line.strip()[:110]}"
                )

    for scripts_dir in sorted(Path("modules").glob("*/scripts")):
        for path in sorted(scripts_dir.iterdir()):
            if not path.is_file():
                continue
            for lineno, line in enumerate(path.read_text(errors="replace").split("\n"), 1):
                if line.lstrip().startswith(("#", "REM", "::")):
                    continue
                if PINNED_JAR.search(line):
                    problems.append(
                        f"{path}:{lineno}: pins a jar by version — resolve the newest instead, "
                        f"or the next release silently runs the previous build\n"
                        f"    {line.strip()[:110]}"
                    )
    return problems

def main() -> int:
    if not Path("build.gradle.kts").exists():
        print("FAIL  run this from the repository root", file=sys.stderr)
        return 2
    problems = scan()
    if problems:
        print(f"FAIL  {len(problems)} place(s) restate a release version:\n")
        for p in problems:
            print(f"  {p}")
        print("\n  The single source of truth is build.gradle.kts; every jar manifest already")
        print("  carries Implementation-Version. See docs/bugs-fixed/CB14 for the three times")
        print("  this has been fixed by hand.")
        return 1
    print("ok    no module source or launcher script restates a release version")
    return 0

if __name__ == "__main__":
    sys.exit(main())
