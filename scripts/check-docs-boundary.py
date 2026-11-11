#!/usr/bin/env python3
"""
Enforce the docs/ <-> devdocs/ boundary.

    docs/     ships to main. Must be self-contained.
    devdocs/  development only. Never reaches main.

Rule 1 (hard failure): no file under docs/ may link into devdocs/.
    A link from docs/ into devdocs/ is a link that will be BROKEN on main,
    silently, because devdocs/ is not merged there. This is how main
    accumulated 22 broken documentation links before the boundary existed.

Rule 2 (regression guard): the number of broken relative links under docs/
    may not exceed BASELINE. Lower BASELINE as they get fixed; never raise it.
"""
import os, re, sys

LINK = re.compile(r'\]\(([^)\s]+?)(?:\s+"[^"]*")?\)')
BASELINE = 59          # pre-existing broken links under docs/ at the time the
                       # boundary was introduced. Ratchet downwards only.

def md_files(root):
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith('.md'):
                yield os.path.join(d, f)

def targets(path):
    with open(path, encoding='utf-8', errors='surrogateescape') as fh:
        body = fh.read()
    for raw in LINK.findall(body):
        if re.match(r'^(https?:|mailto:|#|/)', raw):
            continue
        rel = raw.split('#')[0]
        if rel:
            yield raw, os.path.normpath(os.path.join(os.path.dirname(path), rel))

def main():
    if not os.path.isdir('docs'):
        print('docs/ not found - run from the repository root', file=sys.stderr)
        return 2

    crossings, broken = [], []
    for path in sorted(md_files('docs')):
        for raw, tgt in targets(path):
            if tgt.startswith('devdocs' + os.sep) or tgt.startswith('devdocs/'):
                crossings.append((path, raw))
            elif not os.path.exists(tgt):
                broken.append((path, raw))

    status = 0
    if crossings:
        status = 1
        print('FAIL  docs/ must never link into devdocs/ - %d violation(s):' % len(crossings))
        for path, raw in crossings:
            print('        %s -> %s' % (path, raw))
        print()
        print('      Fix by one of:')
        print('        * promote the target into docs/ (if a shipping doc needs it, it ships)')
        print('        * de-link it, keeping the text')
        print('        * drop the reference')
    else:
        print('ok    docs/ does not link into devdocs/')

    if len(broken) > BASELINE:
        status = 1
        print('FAIL  broken relative links under docs/: %d (baseline %d)'
              % (len(broken), BASELINE))
        for path, raw in broken[:20]:
            print('        %s -> %s' % (path, raw))
        if len(broken) > 20:
            print('        ... +%d more' % (len(broken) - 20))
    else:
        print('ok    broken relative links under docs/: %d (baseline %d)'
              % (len(broken), BASELINE))
        if len(broken) < BASELINE:
            print('      BASELINE in %s can be lowered to %d' % (__file__, len(broken)))

    return status

if __name__ == '__main__':
    sys.exit(main())
