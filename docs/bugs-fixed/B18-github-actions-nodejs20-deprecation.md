# B18: GitHub Actions — Node.js 20 Deprecation Warning

**Status:** Open  
**Priority:** Medium  
**Created:** May 2026  
**Deadline:** Before June 2, 2026 (forced migration) / September 16, 2026 (removal)

---

## Problem

GitHub Actions workflows use actions running on Node.js 20, which is deprecated:

```
Node.js 20 actions are deprecated. Actions will be forced to run with Node.js 24 
by default starting June 2nd, 2026. Node.js 20 will be removed from the runner 
on September 16th, 2026.
```

Affected actions:
- `actions/checkout@v4`
- `actions/setup-java@v4`
- `actions/upload-artifact@v4`
- `actions/cache@v4`

## Affected Files

| File | Actions used |
|------|-------------|
| `.github/workflows/release.yml` | checkout, setup-java, upload-artifact, cache |
| `.github/workflows/cli-ci.yml` | checkout, setup-java, cache |

## Fix

Update to Node.js 24 compatible versions when available:

```yaml
# From:
actions/checkout@v4
actions/setup-java@v4
actions/upload-artifact@v4
actions/cache@v4

# To (check availability):
actions/checkout@v5
actions/setup-java@v5
actions/upload-artifact@v5
actions/cache@v5
```

Or as a quick interim fix, add to each workflow:

```yaml
env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true
```

## Timeline

- **Now:** Warning only — builds work
- **June 2, 2026:** Node.js 24 forced as default — builds may break if actions are incompatible
- **September 16, 2026:** Node.js 20 removed entirely

---

*Bug B18. May 2026. Not urgent today but must fix before June 2, 2026.*
