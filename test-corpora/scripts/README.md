# scripts/ — fetch, verify, snapshot

Three scripts manage the corpora that are not vendored directly in git. They are the mechanism
behind the storage policy in `../README.md` §2. (Contracts below; implement in the project's
chosen tooling.)

## `fetch.sh`
Pull every `mirror` and `fetch` corpus listed in `../SOURCES.lock` to its `location`, at the exact
`pin` (commit hash or dated snapshot). Idempotent. Never fetches `vendor` corpora (already in git).
Must run offline-friendly where possible (cache). Exits non-zero if a pin is missing.

## `verify.sh`
Recompute the content hash of every corpus and compare to `hash` in `../SOURCES.lock`. Fail on any
drift. This is the integrity gate — run it in CI before any suite. A corpus in the accreditation
path (`corpora/security/`, `corpora/fuzz/`) with tier `fetch` and no frozen snapshot is an error.

## `snapshot.sh`
For a `fetch`-tier corpus (huge / non-redistributable), draw the sampled subset actually used by a
suite, freeze it into our mirror, and write its hash back to `../SOURCES.lock`. This is what makes
the evidence reproducible even if upstream disappears — we keep the exact bytes we tested, not a
link. Must respect per-file licenses (e.g. The Stack opt-outs).

---

Guiding rule: **integrity over location.** A corpus is trustworthy evidence when its exact bytes
are frozen and content-hashed — whether that copy lives in git, in our mirror, or in a snapshot.
