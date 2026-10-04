# UTL-X Test Corpora

The shared input + expectation store for the UTL-X parser and serializer test suites.
One store, many consumers — see `docs/architecture/utlx-test-corpora.md` for the strategy
(§5.0 suite architecture, §5.5 compositional proof, §5.7 malformed/malicious/leniency).

> **Golden rule.** The reproducible-evidence set must be **frozen and regenerable even if
> upstream disappears.** That is a stronger requirement than "store everything," and it is what
> this layout and the storage policy below are built to guarantee.

---

## 1. Layout

```
test-corpora/
├── README.md               # this file
├── SOURCES.lock            # per-corpus: url, pin, license, storage tier, content hash
├── LICENSES/               # a copy of each corpus's license text
├── scripts/                # fetch / verify / snapshot (see scripts/README.md)
│
├── corpora/                # ── the shared INPUT asset (consumed by every suite) ──
│   ├── conformance/{json,yaml,xml,csv,odata}/   # deterministic accept/reject (layer 1)
│   ├── malformed/{json,yaml,xml,csv}/           # benign-broken input (§5.7)
│   ├── security/{json,yaml,xml,csv}/            # well-formed-but-abusive: bombs, XXE (§5.7)
│   ├── fuzz/
│   │   ├── seeds/{json,yaml,xml,csv}/           # robustness-suite fuzz seeds
│   │   └── dictionaries/                        # token dictionaries
│   ├── realworld/{json,yaml,xml,csv}/           # sampled volume snapshot (not full dumps)
│   └── performance/{json,yaml,xml,csv}/         # large files for the performance suite
│
├── cross-format/           # ── the GOLDEN backbone (§5.5) ──
│   └── <dataset>/
│       ├── data.xml  data.json  data.yaml  …    # the SAME data in each format
│       └── canonical.udm.json                   # the hand-verified reference UDM
│
└── expectations/           # ── the ORACLES ──
    ├── conformance/<suite>/                     # expected accept/reject + expected outputs
    ├── profiles/
    │   ├── strict/                              # malformed-input → expected rejection
    │   └── lenient/                             # malformed-input → expected recovered UDM
    ├── known-deviations.yaml                    # legitimate cross-format losses (§5.5)
    └── bounds.yaml                              # resource limits per profile (§5.7)
```

The directory names map 1:1 onto the strategy doc: `conformance/` + `cross-format/` feed the
**conformance suite**; `malformed/` + `security/` + `fuzz/` feed the **robustness & security
suite**; `realworld/` + `performance/` feed the **performance suite**. `expectations/profiles/`
holds the per-profile oracles from §5.7.

---

## 2. Storage policy — local vs. remote (the link-rot decision)

Remote corpora fade: repos get deleted, renamed, force-pushed or rebased; services (e.g. the OData
reference services) go offline; files mutate. Over a few years a meaningful fraction *will* vanish.
For evidence that must stay regenerable — the robustness & security results are an accreditation
artifact — "just link to it" is not acceptable. But vendoring *everything* into git is also wrong:
some corpora are gigabytes, and some carry per-file or non-redistributable licenses.

So the policy is **tiered per corpus**, decided by three factors — *size*, *redistributability*,
and *criticality to reproducible evidence*:

| Tier | Store where | Use for | Rule |
|---|---|---|---|
| **Vendor (in-repo / Git LFS)** | here, committed | conformance suites, the golden cross-format corpus, `known-deviations.yaml`, fuzz seeds & dictionaries, security attack cases | small **and** permissively licensed **and** critical to a deterministic suite |
| **Mirror (internal artifact store)** | an owned object store / release asset, pinned by content hash; fetched by `scripts/fetch.sh` | large-but-redistributable volume & performance corpora needed for reproducibility | too big for git, license allows us to hold a copy |
| **Fetch-on-demand + snapshot** | referenced remote, pinned; a **sampled subset frozen** into our mirror as the evidence set | huge and/or non-redistributable (The Stack, Common Crawl) | never redistribute the whole; keep only the exact sampled bytes we actually tested |

**Decision in one line:** do *not* commit everything to git, but *do* guarantee a **local, hashed,
frozen copy** of everything the reproducible/accreditation evidence depends on — vendor the small
critical sets, mirror the large redistributable ones, and for the huge/non-redistributable ones
snapshot exactly the sampled subset you tested. Where storage lives matters less than that the
**exact bytes are frozen and content-hashed**.

---

## 3. Integrity & provenance

`SOURCES.lock` is the source of truth for every corpus: upstream URL, the pin (commit hash or
download date), the license, the **storage tier**, and a **content hash** (a manifest of per-file
SHA-256, or a single archive hash). Integrity — not location — is what makes evidence trustworthy:

- `scripts/verify.sh` recomputes hashes and fails on any drift from `SOURCES.lock`.
- A corpus referenced only by `url@commit` is weaker evidence than one frozen by content hash;
  anything in the accreditation path must be hashed and held locally (vendor or mirror).

## 4. Licensing

Corpora licenses differ, and some (notably The Stack) are per-file. Before a corpus is vendored or
mirrored: record its license in `SOURCES.lock`, drop its license text in `LICENSES/`, and confirm
redistribution is permitted. Non-redistributable corpora stay **fetch-on-demand** and are never
committed; only hashes/pointers and a license-respecting sample are retained.

## 5. Workflow

```
scripts/fetch.sh      # pinned fetch of mirror + fetch-on-demand corpora into place
scripts/verify.sh     # recompute hashes, compare to SOURCES.lock (run in CI)
scripts/snapshot.sh   # freeze a sampled subset of a fetch-on-demand corpus as evidence
```

Large corpora are **not** committed to the main repo; they are fetched (and verified) on demand.
Vendored corpora and all expectations **are** committed, because they are small and must be present
for the deterministic suites to run offline and reproducibly.
