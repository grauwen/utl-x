# UTL-X Proposals

This directory contains proposals for significant changes to the UTL-X language and implementation.

## Active Proposals

### [Dollar Sign ($) Input Prefix Migration](./dollar-sign-input-prefix-migration.md)

**Status:** Draft (2025-10-24)
**Target Version:** v2.0
**Type:** Breaking Change

Proposes migrating from `@` to `$` for input references to eliminate ambiguity with XML attributes and align with industry standards (XSLT, XPath, JSONata, JQ).

**Key Documents:**
1. **[Full Proposal](./dollar-sign-input-prefix-migration.md)** - Complete specification, rationale, implementation plan
2. **[Quick Reference](./at-vs-dollar-quick-reference.md)** - Side-by-side comparison and migration guide
3. **[GitHub Issue Template](./MIGRATION-ISSUE-TEMPLATE.md)** - For tracking implementation
4. **[Migration Script](../../scripts/migrate-at-to-dollar.sh)** - Automated migration tool

**Summary:**

Current (confusing):
```utlx
$orders.Order[0].@id
  ↑              ↑
  input          attribute
  (same symbol!)
```

Proposed (clear):
```utlx
$orders.Order[0].@id
 ↑              ↑
 input          attribute
 (different symbols!)
```

**Impact:**
- ✅ Eliminates ambiguity
- ✅ Industry standard alignment
- ✅ Improved readability
- ⚠️ Breaking change (v2.0)
- 🔧 Automated migration available

**Next Steps:**
1. Review and approve proposal
2. Implement parser changes (v1.5)
3. Create migration tooling (v1.5)
4. 6-month deprecation period
5. Breaking change release (v2.0)

### [COBOL Copybook & Fixed-Width (Flat File) Format Support](./copybook-flatfile-format-support.md)

**Status:** Draft (2026-06-21)
**Target Version:** v1.2 (text/positional) · v1.3 (binary/EBCDIC)
**Type:** New format module (non-breaking)

Proposes a `formats/copybook` module reading positional / fixed-width records into UDM, driven by a
COBOL copybook (or inline layout) as schema — including **EBCDIC + packed/zoned/binary decimal**
(mainframe z/OS and IBM i / **AS-400**). Complementary to the reverseXSL study: reverseXSL covers
*text* EDI (regex-on-text); this module covers *binary* flat files (which regex cannot decode).
Closes the DataWeave "Flat File (fixed-width, positional)" gap and Open-M inventory #161. Proposed by
the Open-M peer project.

### [UTL-X 1.1 — Semantic Validation (`validate.*`)](./utlx-1_1-semantic-validation.md)

**Status:** Proposal · **Target Version:** v1.1 (`%utlx 1.1`) · **Type:** Additive (minor version)

Adds the `validate.*` standard-library namespace and the `ValidationResult` UDM node — semantic
validation that keeps every 1.0 guarantee (pure, stateless, deterministic, single-pass). **1.1 is
core language and lives in this repo, alongside 1.0** (the `utl-x-infer` engine imports `utl-x` and
therefore supports `%utlx 1.1` out of the box; only the probabilistic `ai.*` of 2.0 lives in
`utl-x-infer`).

**Key documents (this directory):**
1. **[Semantic validation spec](./utlx-1_1-semantic-validation.md)** — the `validate.*` namespace and `ValidationResult`.
2. **[`validate.*` naming & guard profile](./utlx-validate-naming-and-guard-profile.md)** — descriptive camelCase convention + the two-tier (core / guard-profile) vocabulary.
2a. **[`validate.*` vocabulary coverage](./utlx-validate-vocabulary-coverage.md)** — completeness audit vs Schematron & FEEL; the Phase-2 design gate for the function set.
3. **[Language versioning & validation](./utlx-language-versioning-validation.md)** — the 1.0 / 1.1 / 2.0 version model, packaging (UTLXe vs UTLXS), and engine-compatibility matrix.
4. **[Build plan](./utlx-1_1-build-plan.md)** — the phased implementation roadmap (spike → foundation → functions → conformance → routing → tooling).

Testing for the 1.1 parsers/serializers and the `validate.*` layer is covered by
[`../architecture/utlx-test-corpora.md`](../architecture/utlx-test-corpora.md); the de-risking spike by
[`../architecture/validate-module-spike.md`](../architecture/validate-module-spike.md).

## Proposal Process

### 1. Draft Phase
- Create proposal document in this directory
- Include problem statement, solution, alternatives
- Document implementation plan
- Estimate impact and timeline

### 2. Review Phase
- Share with team for feedback
- Create GitHub discussion
- Incorporate feedback
- Revise proposal

### 3. Approval Phase
- Team decision on acceptance
- Create GitHub issue for tracking
- Plan implementation milestones

### 4. Implementation Phase
- Follow implementation plan
- Regular status updates
- Testing and validation

### 5. Release Phase
- Documentation updates
- Release notes
- Community communication
- Post-release support

## Template

New proposals should follow this structure:

```markdown
# Proposal: [Title]

**Status:** Draft | Review | Approved | Implemented
**Author:** [Name]
**Date:** YYYY-MM-DD
**Target Version:** vX.Y

## Executive Summary
Brief overview of the proposal

## Problem Statement
What problem are we solving?

## Proposed Solution
Detailed solution with examples

## Alternatives Considered
What else did we consider?

## Implementation Plan
Phased approach with timeline

## Impact Analysis
- Breaking changes?
- Migration required?
- Performance impact?

## Success Metrics
How do we measure success?

## Risks and Mitigation
What could go wrong?

## References
Links to related docs, issues, standards
```

## Historical Proposals

(None yet - this is the first!)

## Contributing

To submit a proposal:

1. Create a new markdown file in `docs/proposals/`
2. Follow the template above
3. Create a pull request
4. Tag with `proposal` label
5. Team will review and provide feedback

## Questions?

- Join the discussion on GitHub Discussions
- Create an issue with `proposal` label
- Contact the core team

---

**Last Updated:** 2025-10-24
**Active Proposals:** 1 (Dollar Sign Migration)
