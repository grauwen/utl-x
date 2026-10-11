# F22: Iterative / indexed format parsing — bounded, non-recursive (close the DataWeave depth gap; stream large inputs)

> **Status:** strategy / design — **DEFERRED, low priority, gated on a real requirement.** The B32
> crash is already mitigated (safety cap + per-profile `ParseOptions.maxDepth`), so this is *not urgent*;
> pursue it only if a concrete need appears for **deep documents** (>256 nesting, e.g. DataWeave
> migration) or **large inputs** (streaming/multi-GB). See [Trade-offs](#trade-offs--is-this-worth-doing)
> — the two phases are **separate go/no-go** decisions, and Phase 2 may not fit UTL-X at all.
>
> A future **parser-architecture** item (not a config change): convert the `formats/*` readers from
> recursive-descent to **iterative** (explicit-stack) parsing so depth is **heap-bounded, not
> JVM-stack-bounded**, and (phase 2) optionally **indexed/streaming** for large inputs.
>
> **Depends on / pairs with:** **B32** (the StackOverflow crash class this eliminates), **B29/B30**
> (parser hardening), `core/format/ParseOptions` (`maxDepth` — the bound this makes safe to raise),
> `docs/architecture/parser-strictness-profiles.md` §4/§11.5, `test-corpora/expectations/bounds.yaml`,
> and the readers `formats/{json,xml,yaml,csv}`.
>
> **This is utlx-code / `formats/*` (an `F##`, like B29/B30/B32 are `B##`) — not an engine (`EF`) feature.**

## Summary

The JSON/XML readers are **hand-written recursive descent**, so nesting depth consumes the **JVM call
stack** and a deeply-nested document **stack-overflows** (measured ~5032 JSON / ~3778 XML at the default
2 MB `-Xss`, B32). That forces **conservative depth bounds** (strict 64 / standard 128 / lenient 256,
well below the crash) and couples `max_depth` to `-Xss`. It also **materialises the whole UDM** in memory.

F22 removes both limits at the source: parse **iteratively** with an explicit, heap-allocated work
stack — depth is then bounded by `ParseOptions.maxDepth` (checked against a counter), **independent of
`-Xss`**, with **no StackOverflow possible**. Phase 2 adds an **indexed/streaming** reader for large
inputs (random access without full materialisation). Net: the crash class disappears, `max_depth` can be
raised safely to thousands (match/exceed DataWeave), and large-input memory improves.

## Problem

- **Stack-bound depth → crash (B32).** Recursive descent overflows the stack; the only current defence is
  a conservative cap kept far below the crash point, and the cap must track `-Xss`.
- **Tighter than peers — especially the competitor.** Verified defaults: .NET `System.Text.Json` **64**,
  serde_json **128** (a de-facto value), Jackson (≥2.15) **1000**, **DataWeave (indexed readers) 4096**
  (not configurable; + 4 GB max value, 2³²−1 tokens). UTL-X's 64/128/256 is on the conservative end **by
  necessity**, not choice — the recursive readers can't safely go higher at a 2 MB stack.
- **Full in-memory materialisation.** No streaming/random-access path for large inputs.

## Why DataWeave can do 4096 and we can't (yet)

DataWeave uses **indexed readers** — non-recursive, random-access, streaming — so depth doesn't touch a
call stack; it affords 4096. UTL-X's recursive descent is stack-bound. So the depth gap is an
**architecture difference**, and closing it is this feature, not a constant bump.

## Design (phased)

**Phase 1 — iterative conversion (unblocks depth; eliminates the crash class).**
Replace the recursive `parseValue → parseArray/parseObject → parseValue` (JSON) and
`parseElementChecked → parseElement → parseElementChecked` (XML) chains with an **explicit work-stack
loop**. Depth = the heap stack's size, checked against `ParseOptions.maxDepth`; exceeding it throws a
clean, structured parse error (never StackOverflow). JSON and XML first. YAML is SnakeYAML — already
iterative/bounded; just set its `LoaderOptions.nestingDepthLimit` from `ParseOptions`.

**Phase 2 — indexed / streaming reader (unblocks large inputs).**
A DataWeave-style index of token offsets enabling random access **without materialising the whole UDM**,
plus streaming — above a configurable size threshold. Raises the practical input-size ceiling and cuts
peak memory.

## Benefits

- **Eliminates the B32 crash class** — depth is heap-bounded; StackOverflow is structurally impossible.
- **`max_depth` can match/exceed DataWeave's 4096 safely** — set purely via `ParseOptions.maxDepth`, with
  the `-Xss` coupling gone. (The guard still *chooses* tight bounds; the point is it's now a free choice.)
- **Large-input memory** — streaming/indexed reading instead of full materialisation.
- **Removes the `max_depth` / `-Xss` / concurrency coupling** caveat documented in `bounds.yaml`.

## Interaction with profiles / bounds

`ParseOptions.maxDepth` remains the single depth knob — now heap-bounded, so its ceiling can rise. F22
**supersedes B32's recursive-depth stop-gap** and removes the "keep `max_depth` well below the stack
crash" margin rule from `bounds.yaml` (that rule exists only because the readers are recursive today).

## Scope / non-goals

- **Not** a language or stdlib change; purely `formats/*` reader architecture + `ParseOptions` wiring.
- **Guard:** still wants tight bounds, but gains the removed crash class and predictable memory.

## Trade-offs — is this worth doing?

The two phases have **different** value/risk/fit, and should be decided separately.

### Phase 1 — iterative (explicit-stack)
**Pros:** eliminates the B32 crash class (depth heap-bounded, StackOverflow impossible); decouples
`max_depth` from `-Xss` (deterministic across JVMs/platforms); supports deep nesting (match DataWeave
4096) without a bigger stack; no UDM/output change; especially good for the **guard** (removes a DoS
vector).
**Cons:** rewrite of the most **security-critical** code → real regression risk; recursive descent maps
1:1 to the grammar and is far more **readable/maintainable** than an explicit work-stack loop (a
permanent tax); the hand-written **XML** parser is a big lift; **does not** reduce memory by itself
(still materialises the whole UDM).
**GraalVM:** safe — a plain loop + heap stack adds no reflection/`Unsafe`/lazy-init, so it native-compiles
as cleanly as today's parsers.

### Phase 2 — indexed / streaming (lazy, random-access)
**Pros:** large-input memory (stream, don't materialise — DataWeave's 4 GB-value path); lazy evaluation
(only build the UDM the mapping touches).
**Cons (heavier, and some conflict with UTL-X's own design):**
- big subsystem + **UDM-model impact** (eager → lazy ripples into interpreter/stdlib/serializers);
- **tension with the guard** — LANGSEC wants *complete recognition before acting*; lazy/streaming
  *defers* recognition, so Phase 2 is good for the **mapper** but arguably **wrong for the guard**
  (which should stay eager/fully-recognising);
- **tension with the single-pass guarantee** (random access wants the whole input buffered/re-readable);
- more complexity = more attack surface (bad for the guard);
- **GraalVM native-image risk.** Lazy/indexed readers typically use memory-mapping, `Unsafe`/direct
  buffers, lazy thunks, or method-handle/reflection dispatch — the classic native-image breakers.
  UTL-X's current parsers deliberately use **none** of these (verified: zero reflection/`Unsafe`/mmap),
  which is *why* they native-compile cleanly; a lazy reader would reintroduce the **B17/B19/B25** class of
  native-image problems — and the **guard runs as a native binary**, so Phase 2 breaking native-image
  hurts the guard specifically.

### Is it even needed now? (the YAGNI check)
The crash is **already** handled, so F22's marginal value is only: higher safe depth + remove the `-Xss`
coupling + large-input memory. **If customers don't need >256 depth or multi-GB inputs, F22 earns
nothing** over the current cap + per-profile bounds. Gate it on a real requirement.

**Cheaper partial alternative:** raise `-Xss` (a JVM flag) → deeper nesting with no rewrite. But it only
*moves* the crash deeper (doesn't remove the class), costs memory per thread, keeps depth coupled to
`-Xss`, and does nothing for large-input memory. Stop-gap, not fix.

### Verdict
Keep this documented (it records *why* UTL-X is conservative and the DataWeave gap), but **deferred**:
- **Phase 1** — do **only** when deep-document support or `-Xss`-decoupling becomes a real need; the
  crash is already mitigated without it.
- **Phase 2** — gate on an actual **large-input** requirement, and make it **mapper-only** (the guard
  stays eager). It may never be worth it for UTL-X.

## Guard safety constraint (invariant)

The two phases affect the **content guard** oppositely — a hard rule for any implementer:

- **Phase 1 (iterative) — permitted, and beneficial.** It stays **eager and fully-recognising**; it only
  removes the stack-overflow DoS class and makes the depth bound deterministic. On a guard this is a *win*.
- **Phase 2 (indexed / lazy / streaming) — MAPPER-ONLY. It must never be on the guard's data path.**
  Lazy recognition breaks the guard's core principles:
  - *complete recognition before acting* (LANGSEC) — lazy parsing defers recognition;
  - *fail-closed / no pass-through* — a region the mapping never touches goes unvalidated;
  - *single-pass* — random access needs the input buffered/re-readable;
  - *rebuild-clean / CDR* — the guard materialises and canonically rebuilds the **whole** message;
  - *minimal attack surface* — an indexed reader is a large new subsystem.

> **Invariant:** the guard parses **eagerly, completely, and within tight bounds — Phase 1 at most,
> never Phase 2.** A lazy/indexed reader on a guard is a correctness-and-security defect, not an
> optimisation.

This mirrors the broader guard-vs-mapper split (strictness profile, bounds): the two consumers
legitimately want different parser *behaviour* **and** different parser *architecture*.

## Open questions

1. Iterative-rewrite effort per reader (JSON tractable; the hand-written XML parser is larger).
2. Indexed-reader **size threshold** (DataWeave switches above a configurable size) and whether to keep a
   materialised UDM or move to a streaming UDM.
3. Re-measure the crash-free max depth after Phase 1 (should be bounded only by `maxDepth`/heap, not `-Xss`).
4. Does Phase 2 change the UDM API (random-access vs eager)? Keep the eager UDM as the default.

## Sources
- [DataWeave — Indexed Readers (4096 depth, 4 GB value, 2³²−1 tokens)](https://docs.mulesoft.com/dataweave/latest/dataweave-indexed-readers)
- [Jackson `StreamReadConstraints` (default max depth 1000)](https://javadoc.io/static/com.fasterxml.jackson.core/jackson-core/2.15.0/com/fasterxml/jackson/core/StreamReadConstraints.html)
- [.NET `JsonSerializerOptions.MaxDepth` (default 64)](https://learn.microsoft.com/en-us/dotnet/api/system.text.json.jsonserializeroptions.maxdepth)
- serde_json 128-level cap (de-facto; e.g. [napi-rs #3568](https://github.com/napi-rs/napi-rs/pull/3568)).
- Internal: **B32** (measured crashes), `docs/architecture/parser-strictness-profiles.md`, `test-corpora/expectations/bounds.yaml`.
