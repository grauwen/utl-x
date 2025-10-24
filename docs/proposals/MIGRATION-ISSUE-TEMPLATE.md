# GitHub Issue Template: Migrate to $ for Input References

**Title:** [v2.0] Migrate from @ to $ for input references

**Labels:** breaking-change, enhancement, v2.0, migration, good-first-issue

---

## Summary

Migrate UTL-X from using `@` for input references to using `$`, while keeping `@` exclusively for XML attributes. This change:
- Eliminates ambiguity between inputs and attributes
- Aligns with 25+ years of industry standards (XSLT, XPath, JSONata, JQ)
- Improves code readability and reduces cognitive load

## Problem

Currently, `@` has two different meanings:
1. Input reference: `$orders.Order`
2. XML attribute: `Order.@id`

This creates confusion in expressions like:
```utlx
$orders.Order[0].@customerId
  ↑              ↑
  input          attribute
  (two different meanings!)
```

## Proposed Solution

**Before:**
```utlx
$orders.Order[0].@id
$customers |> filter(c => c.@id == $orders[0].@customerId)
```

**After:**
```utlx
$orders.Order[0].@id
$customers |> filter(c => c.@id == $orders[0].@customerId)
```

**Clear separation:**
- `$` = data sources (inputs, variables)
- `@` = metadata (XML attributes)

## Implementation Plan

### Phase 1: Parser Support (v1.5)
- [ ] Update lexer to recognize `$` token
- [ ] Update parser to accept both `@` and `$` for input references
- [ ] Add deprecation warnings for `$inputName` usage
- [ ] Update error messages to suggest `$` syntax

**Files to modify:**
- `modules/core/src/main/kotlin/org/apache/utlx/core/lexer/lexer_impl.kt`
- `modules/core/src/main/kotlin/org/apache/utlx/core/parser/parser_impl.kt`
- `modules/core/src/main/kotlin/org/apache/utlx/core/ast/ast_nodes.kt`

### Phase 2: Migration Tooling (v1.5)
- [ ] Create migration script: `scripts/migrate-at-to-dollar.sh`
- [ ] Add `utlx migrate` command to CLI
- [ ] Add linter rule to detect legacy `@` syntax
- [ ] Create automated tests for migration tool

**Files to create:**
- `scripts/migrate-at-to-dollar.sh` (created)
- `tools/migrate/src/main/kotlin/Main.kt` (new)
- `modules/cli/src/main/kotlin/commands/MigrateCommand.kt` (new)

### Phase 3: Documentation (v1.5)
- [ ] Update README.md with new syntax
- [ ] Update language guide documentation
- [ ] Create migration guide with examples
- [ ] Update all code examples in docs
- [ ] Add deprecation notice to multi-input docs

**Files to update:**
- `README.md`
- `docs/language-guide/multiple-inputs-outputs.md`
- `docs/language-guide/quick-reference-multi-$input.md`
- `docs/language-guide/README.md`
- All files in `docs/examples/`

### Phase 4: Test Migration (v1.5)
- [ ] Migrate conformance test suite (286 tests)
- [ ] Migrate example files
- [ ] Verify all tests still pass
- [ ] Update test documentation

**Test files to migrate:**
- `conformance-suite/tests/**/*.yaml` (~286 files)
- `examples/**/*.utlx`
- Documentation examples

### Phase 5: Breaking Change (v2.0)
- [ ] Remove support for `$inputName` syntax
- [ ] Parser error for `@` before identifier (except after `.`)
- [ ] Update error messages
- [ ] Final documentation sweep

## Migration Path for Users

### Step 1: Update to v1.5
```bash
# Install v1.5
brew upgrade utlx

# Or npm
npm install -g utlx@1.5.0
```

### Step 2: Run Migration Tool
```bash
# Dry run first
utlx migrate --dry-run mytransform.utlx

# Apply migration
utlx migrate mytransform.utlx

# Migrate entire directory
utlx migrate --at-to-dollar ./transformations/
```

### Step 3: Test & Verify
```bash
# Run your tests
utlx test

# Check for warnings
utlx lint
```

### Step 4: Update to v2.0 (when ready)
```bash
# v2.0 only accepts $ syntax
brew upgrade utlx@2
```

## Timeline

| Milestone | Target | Deliverables |
|-----------|--------|--------------|
| **v1.5-alpha** | Week 4 | Parser changes, both syntaxes work |
| **v1.5-beta** | Week 6 | Migration tool, linter, docs updated |
| **v1.5-rc** | Week 8 | All tests migrated, verified |
| **v1.5 Release** | Week 10 | Deprecation period begins |
| **Migration Period** | 6 months | Community migration support |
| **v2.0 Release** | +6 months | Breaking change, $ only |

## Success Criteria

**v1.5 Release:**
- ✅ Both `@` and `$` work for inputs
- ✅ Deprecation warnings shown for `@`
- ✅ Migration tool available and tested
- ✅ All documentation updated
- ✅ 100% of tests use new `$` syntax

**v2.0 Release:**
- ✅ Only `$` works for inputs
- ✅ `@` only for attributes
- ✅ Clear error messages
- ✅ Community adoption >90%
- ✅ Positive feedback on clarity

## Documentation

- **Full Proposal:** [docs/proposals/dollar-sign-input-prefix-migration.md](./dollar-sign-input-prefix-migration.md)
- **Quick Reference:** [docs/proposals/at-vs-dollar-quick-reference.md](./at-vs-dollar-quick-reference.md)
- **Migration Script:** [scripts/migrate-at-to-dollar.sh](../../scripts/migrate-at-to-dollar.sh)

## References

- Industry standards: XSLT, XPath, JSONata, JQ all use `$` for variables
- XPath Specification: https://www.w3.org/TR/xpath/
- Conformance test investigation: 261/286 passing (91.3%)
- Community discussion: [Link to discussion thread]

## Breaking Change Notice

**⚠️ This is a breaking change planned for v2.0**

**Affected code:**
- Any use of `$inputName` for input references
- Multi-input transformations using `$orders`, `$customers`, etc.

**Not affected:**
- XML attribute access: `element.@id` (no change)
- Single input using `input` keyword (no change)
- Function definitions and calls (no change)

**Migration required:**
- Automated: Use `utlx migrate` command
- Manual: Replace `$inputName` with `$inputName`

## Community Feedback

Please comment on this issue with:
- ✅ Support for this change
- ❓ Questions or concerns
- 💡 Suggestions for improvement
- 🐛 Issues with migration tool
- 📝 Documentation feedback

## Checklist for Implementers

### Parser Team
- [ ] Implement `$` token in lexer
- [ ] Update parser to accept both `@` and `$`
- [ ] Add deprecation warnings
- [ ] Update error messages
- [ ] Write parser tests

### Tooling Team
- [ ] Create migration shell script
- [ ] Implement `utlx migrate` CLI command
- [ ] Add linter rules
- [ ] Write tooling tests

### Documentation Team
- [ ] Update README
- [ ] Update language guide
- [ ] Create migration guide
- [ ] Update all examples
- [ ] Review all docs

### Testing Team
- [ ] Migrate conformance suite
- [ ] Verify all tests pass
- [ ] Test migration tool
- [ ] Add regression tests

### Release Team
- [ ] Version planning (v1.5, v2.0)
- [ ] Release notes
- [ ] Announcement post
- [ ] Community communication

## Related Issues

- #XXX - Conformance test failures investigation
- #XXX - @ symbol ambiguity report
- #XXX - Parser improvements

## Next Steps

1. ✅ Approve proposal
2. Create feature branch: `feature/dollar-input-syntax`
3. Implement parser changes
4. Create migration tool
5. Update documentation
6. Migrate tests
7. Release v1.5-alpha for testing

---

**Questions?** Comment below or join the discussion in [Discussions]
