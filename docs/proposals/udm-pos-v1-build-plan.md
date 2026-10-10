# Build Plan — `udm-pos-v1` (first interlink form-class) + the type-library foundation beneath it

| Field | Value |
|---|---|
| Document | udm-pos-v1-build-plan |
| Status | Plan — **foundation-first, one vertical slice before the real form**; see Phase 0 |
| Scope | Build the first bounded binary **form-class** for the split-guard interlink (`udm-pos-v1`, a position report) **and** the shared type library it rests on (elements → composites → message forms) + the encode/verify toolchain |
| Spec | `../hardware/interlink-protocol-v1.md` §6 (canonical simple forms), **§6.1** (two transport modes) and **§6.2** (form-class type library — elements/composites/message forms, the share-definitions-not-variability rule) |
| Related | `../architecture/utlx-test-corpora.md` (§5.5, §5.7 — golden vectors, malformed); `utlx-1_1-build-plan.md` (the foundation-first pattern this mirrors); `../architecture/civilian-guard.md` (what crosses the link). *Defence deployments:* the bit-level **BINF** form notation and the content-guard detail live in the separately-governed `utlx-mil` repo. |

> **The one reframing:** `udm-pos-v1` is the **last** thing built, not the first. It sits on a shared
> element catalogue, two composites, an encoding spec, and a tool that can encode **and** independently
> verify. A mistake in the catalogue propagates into every future form, so the real first deliverable is
> the **foundation** — proven end-to-end on one trivial form — *then* the real position form.

## Decisions this plan rests on (already made — not reopened)

- A form-class is a **write-once, human-authored, design-time artefact**; nothing is generated on the
  fly. Unprovisioned type ⇒ reject (`R_FORM`) or Mode-2 generic UDM stream (lower assurance). *(ICD §6.2.)*
- **Share definitions, never variability.** Each message form is a *fixed, closed* composition; the
  library is compiled against, not a runtime union. Variance goes into *which named form*, never into the
  *shape* of a form. *(ICD §6.2.)*
- The form encodes the **output UDM exactly**; information loss (if any) lives in the mapping's
  projection, not the form. Per-field precision/range is therefore a **design choice made up front**.
- **Two implementations, written independently** (ICD principle 6): A's **encoder** and B's **verifier**
  (HW9) do not share code. A single mistake must not be replicated in both halves.
- **Elements and composites are versioned and pinned** (`udm-pos-v1` embeds `Position-v1`); adding/re-cutting
  a form is a config change under ICD §10 (both owners sign), not a frame `version` bump.
- "pos" names the **content**; `udm-` is a constant flavour prefix. Not `-out/-fpga/-formclass/-transformed`
  (role/deployment/category/property — true of every form, so they identify none).

---

## Phase 0 — Foundation: encoding rules, element catalogue, toolchain skeleton, one trivial slice · ~1–2 weeks

Build the ground the library stands on, and prove the **whole chain works on one throwaway-simple form**
before the real position form exists. This is not a detour: the harness and toolchain built here are what
every later form reuses.

- **0a — Form-class definition language + encoding-rules doc.** The single biggest prerequisite. Fix *how*
  forms are described and encoded before writing any: notation (**BINF** vs an ASN.1-/SBE-style schema —
  see §6.2 prior art), endianness (**match the frame: big-endian**), alignment/packing, fixed-point
  conventions, string encoding (fixed vs length-prefixed + max len), enum encoding, and the **null/sentinel
  convention** (how "present but empty" is represented in a *fixed* layout). Builds on the ICD's own
  encoding rules (`../hardware/interlink-protocol-v1.md` §4.1); defence deployments use the bit-level
  **BINF** notation in the `utlx-mil` repo.
- **0b — Element catalogue (shared bounded primitives).** Define the reusable primitives once:
  `fixedpoint-lat`/`fixedpoint-lon` (bit width + scale, e.g. 1e-7°), `altitude` (units/range/ceiling),
  `utc-millis` (epoch base, width, resolution), bounded enums, `utf8(maxlen)`. These are reused by *every*
  future form, so correctness here is the real foundational work.
- **0c — Toolchain skeleton (both halves, software-first):**
  - **Encoder** — UTL-X `output <form-class>`: a writer taking output UDM + form-class definition → bytes
    (canonical write). Decide: existing serializer, or code-gen from the definition?
  - **Independent reference verifier** — the HW9 logic as software first (FPGA later), written *independently*
    of the encoder.
  - **Conformance harness** — encoder output == golden bytes; verifier accepts-valid / rejects-malformed with
    the right reason code; round-trip `UDM → bytes → UDM` lossless to the designed precision. (Reuses
    `test-corpora/`.)
- **0d — Registry + governance scaffolding.** The `forms/` directory the ICD points at; `payload_format` ID
  allocation; composite-version-pinning policy; the both-owners sign-off step.
- **0e — One trivial vertical slice.** Author a throwaway minimal form (e.g. a two-field `probe-v1`), run it
  through encoder → verifier → golden vectors end-to-end. Proves 0a–0d before any mission content is at stake.

**Exit:** encoding-rules doc signed; element catalogue defined; encoder + independent verifier + harness run a
trivial form green end-to-end; `forms/` registry and sign-off process live.

## Phase 1 — Position content: source semantics, output UDM, precision budget · ~1 week

- **1a — Authoritative source semantics.** Name the model a "position" is defined by (e.g. AIS types 1–3,
  ADS-B, or the fused-track model) — fields come from a documented model, not invented ad hoc.
- **1b — Output-UDM contract for position.** Pin the UDM shape the mapping produces (names, types, nesting).
  `udm-pos-v1` is derived *from this*.
- **1c — Per-field precision/range budget.** For each field (lat/lon resolution, alt ceiling, time precision)
  write the target precision. This is the design decision that determines round-trip fidelity; getting it
  wrong means re-cutting the form later.

**Exit:** position source, output-UDM contract, and precision budget documented and reviewed.

## Phase 2 — Compose and author `udm-pos-v1` · ~1 week

- **2a — Composites:** define `Position-v1` and `Identity-v1` in the catalogue's terms (reusable, not inlined).
- **2b — Message form:** `udm-pos-v1 = Identity-v1 + Position-v1`, a fixed closed layout, registered with a
  `payload_format` ID.
- **2c — Encoder mapping:** the UTL-X mapping `… output udm-pos-v1` from the output UDM (1b).
- **2d — Independent verifier entry** for `udm-pos-v1` (HW9 reference), written independently of 2c.

**Exit:** `udm-pos-v1` authored, encoder maps to it, independent verifier checks it.

## Phase 3 — Prove it · ~1 week

- **3a — Golden vectors:** valid, **boundary** (min/max of each field), and **malformed** position messages
  with expected encoded bytes (feeds `test-corpora/`, §5.5/§5.7).
- **3b — Conformance run:** encoder == golden; verifier accepts all valid / rejects all malformed with the
  correct reason code; round-trip lossless to the Phase-1c precision.
- **3c — Fuzz the verifier:** random/mutated payloads — no crash/hang; every input accepted-valid or
  rejected-with-reason (mirrors ICD §11 T21/T26).

**Exit:** `udm-pos-v1` green across golden + fuzz; round-trip fidelity matches the budget; ready to register
as a live flow (ICD §9) under both owners' signatures.

---

## Invariants (hold across all phases)

1. **Every message form is a fixed, closed shape** — no optional/variable-length layout (HW9 must verify in
   fixed logic). Variance ⇒ a *new named form* or an explicit null in a fixed layout.
2. **Encoder and verifier stay independent** — no shared code (ICD principle 6).
3. **The definition never rides the wire** — it is out-of-band, provisioned to both halves + the verifier,
   written once per form/version.
4. **The form encodes the output UDM exactly** — loss is a mapping choice, surfaced in the precision budget,
   never an accident of the form.
5. **Shared composites ⇒ shared verification primitives** — define `Position` once so B's verifier implements
   "verify a Position block" once and reuses it (keeps the silicon small).

## Risks

| Risk | Mitigation |
|---|---|
| Precision budget (1c) wrong ⇒ silent fidelity loss, re-cut later | Decide numbers up front from the source model (1a); round-trip test to the budget (3b) |
| Encoding rules (0a) underspecified ⇒ encoder/verifier disagree bit-for-bit | Canonicalization pinned in 0a; proven on the trivial slice (0e) before real content |
| Encoder and verifier accidentally converge (shared helper) ⇒ a bug in both halves | Enforce independent implementations; different author/team if possible |
| Scope creep into a "super form" with optionality | Hold invariant 1; "lite"/variant ⇒ a new closed form with its own ID |
| Jumping to FPGA too early | Verifier software-first (0c); FPGA after the software reference + golden vectors are stable |

## Open questions

- Definition language: **BINF** vs an ASN.1-/SBE-style schema for the catalogue? (0a — on the critical path.)
- Position source model (1a): AIS 1–3, ADS-B, or the fused-track model as the first `udm-pos-v1`?
- Does the UTL-X engine already have a form-class serializer, or is code-gen from the definition needed? (0c.)
- Who writes B's independent verifier (second team vs hand-written reference)? (Mirrors ICD §12.)
