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
