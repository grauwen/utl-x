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
BASELINE = 26          # pre-existing broken links under docs/ (targets that were never written,
                       # e.g. formats/xml.md). Set when the boundary was introduced; the
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

    # Rule 3: a de-link marker inside devdocs/ is always a mistake. devdocs/ files
    # may link to each other freely; a marker there means a link was stripped while
    # the file still lived in docs/ and the file was moved afterwards.
    import re as _re
    MARK = _re.compile(r'\*\((?:design notes — development branch|development branch|IDE — development branch)\)\*')
    stranded = []
    for path in sorted(md_files('devdocs')) if os.path.isdir('devdocs') else []:
        with open(path, encoding='utf-8', errors='surrogateescape') as fh:
            hits = len(MARK.findall(fh.read()))
        if hits:
            stranded.append((path, hits))

    # Rule 4: a de-link stub in docs/ whose target now exists in docs/ is wrong.
    # This catches the ordering mistake where a link is stripped and the target is
    # later promoted back into docs/ -- the stub is then pure loss.
    import subprocess as _sp
    recoverable = []
    _LINKRX = _re.compile(r'\[([^\]]+)\]\(([^)\s]+?)(?:\s+"[^"]*")?\)')
    for path in sorted(md_files('docs')):
        with open(path, encoding='utf-8', errors='surrogateescape') as fh:
            body = fh.read()
        if not MARK.search(body):
            continue
        prev = _sp.run(['git', 'show', 'HEAD:' + path], capture_output=True)
        if prev.returncode != 0:
            continue
        old = prev.stdout.decode('utf-8', 'surrogateescape')
        for _m in _LINKRX.finditer(old):
            text, tgt = _m.group(1).strip(), _m.group(2)
            if _re.match(r'^(https?:|mailto:|#|/)', tgt):
                continue
            # The FULL stub text must equal this link's text. Matching a mere
            # suffix makes "CLI Reference" collide with a "Reference" link and
            # "UDM Documentation" with a "Documentation" link.
            hit = False
            for _sm in MARK.finditer(body):
                before = body[:_sm.start()].rstrip()
                before = before[before.rfind('\n') + 1:]
                # strip list bullets, numbering, bold and leading emoji/symbols
                cleaned = _re.sub(r'^\s*(?:[-*+]|\d+\.)\s*', '', before)
                cleaned = cleaned.replace('**', '').strip()
                cleaned = _re.sub(r'^[^\w(]+\s*', '', cleaned).strip()
                if cleaned == text:
                    hit = True
                    break
            if not hit:
                continue
            cand = os.path.normpath(os.path.join(os.path.dirname(path), tgt.split('#')[0]))
            if os.path.isfile(cand) and cand.startswith('docs' + os.sep):
                recoverable.append((path, text, cand))

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

    if stranded:
        status = 1
        print('FAIL  de-link markers stranded inside devdocs/ - %d file(s):'
              % len(stranded))
        for path, hits in stranded:
            print('        %s (%d)' % (path, hits))
        print('      These files may link to devdocs/ directly. Restore the links.')
    else:
        print('ok    no stranded de-link markers in devdocs/')

    if recoverable:
        status = 1
        print('FAIL  %d de-link stub(s) in docs/ whose target is on main - restore them:'
              % len(recoverable))
        for path, text, cand in recoverable[:15]:
            print('        %s: "%s" -> %s' % (path, text, cand))
    else:
        print('ok    no recoverable de-link stubs in docs/')

    return status

if __name__ == '__main__':
    sys.exit(main())
