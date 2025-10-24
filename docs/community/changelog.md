# Changelog

All notable changes to UTL-X will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Changed
- **BREAKING**: Let bindings in block expressions now require semicolons or commas when followed by non-property expressions
  - **Reason**: Without terminators, the parser cannot distinguish `let x = val; [array]` from `let x = val[array]` (array indexing)
  - **Migration**: Add semicolons after each let binding in lambda bodies: `let x = 10;` instead of `let x = 10`
  - **Example**:
    ```utlx
    // Old (no longer valid):
    map(items, item => {
      let price = item.Price
      let tax = price * 0.08
      [price, tax]
    })

    // New (required):
    map(items, item => {
      let price = item.Price;
      let tax = price * 0.08;
      [price, tax]
    })
    ```
  - **Note**: Object literals still use commas: `{ let x = 10, prop: x }` works as before

### Fixed
- Block expressions now properly parse when returning arrays or objects
- Parser correctly distinguishes between array indexing and array literals after let bindings

### Planned
- CSV input/output support
- YAML input/output support
- VS Code extension
- IntelliJ IDEA plugin
- Expanded standard library
- Performance optimizations
- Streaming support

---

## [0.1.0] - 2026-01-15

### Added
- Initial alpha release 🎉
- Core language parser and compiler
- XML input/output support
- JSON input/output support
- Basic selector syntax (paths, attributes, arrays, predicates)
- Pipeline operator (`|>`)
- Higher-order functions (map, filter, reduce)
- Aggregation functions (sum, avg, min, max, count)
- String functions (upper, lower, trim, concat, split, join)
- Array functions (first, last, take, drop, sort, reverse)
- User-defined functions
- Template matching (basic)
- Type inference system
- Let bindings for variables
- If-else expressions
- Match expressions (pattern matching)
- CLI tool for transformations
- Gradle build system
- Unit test framework
- Basic documentation
- Contributing guidelines
- Contributor License Agreement (CLA)
- Dual licensing (AGPL-3.0 + Commercial)

### Language Features
- Format-agnostic syntax
- Functional programming paradigm
- Immutable data structures
- Expression-based (no statements)
- Strong type system with inference
- Comments (single-line and multi-line)

### Known Issues
- Limited error messages
- No CSV/YAML support yet
- Performance not optimized
- No streaming for large files
- Limited standard library
- Template matching basic only

### Breaking Changes
- N/A (initial release)

### Deprecated
- N/A (initial release)

### Removed
- N/A (initial release)

### Fixed
- N/A (initial release)

### Security
- No known security issues

---

## Version History

| Version | Release Date | Status | Notes |
|---------|--------------|--------|-------|
| 0.1.0 | 2026-01-15 | Alpha | Initial release |

---

## Changelog Categories

We use these categories for changes:

- **Added** - New features
- **Changed** - Changes to existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Security** - Security fixes
- **Breaking Changes** - Changes that break backward compatibility

---

## Release Schedule

- **Patch releases (0.x.y):** As needed for bug fixes
- **Minor releases (0.x.0):** Every 3 months
- **Major releases (x.0.0):** Annually (starting with 1.0.0)

---

## How to Upgrade

### From Development to 0.1.0

If you were using development builds:

1. **Backup your transformations**
2. **Update installation:**
   ```bash
   cd utl-x
   git pull origin main
   ./gradlew clean build
   ```
3. **Test your transformations:**
   ```bash
   utlx transform $input.xml script.utlx
   ```
4. **No breaking changes expected** - all development features remain

---

## Future Versions

### v0.2.0 (Planned - April 2026)

**Focus:** Format expansion and developer tooling

- CSV input/output
- YAML input/output
- VS Code extension (syntax highlighting, autocompletion)
- IntelliJ IDEA plugin
- Enhanced error messages
- Expanded standard library (50+ functions)
- Performance improvements (basic optimizations)

### v0.3.0 (Planned - July 2026)

**Focus:** Stability and advanced features

- Streaming support for large files
- Advanced template matching features
- Error handling (try-catch)
- Comprehensive testing (95%+ coverage)
- Online playground
- Documentation website
- Maven/Gradle plugins
- Spring Boot integration

### v1.0.0 (Planned - October 2026)

**Focus:** Production readiness

- Stable API (no breaking changes in 1.x)
- Performance optimized (< 5ms avg transformation)
- Format plugins API
- Production monitoring and metrics
- Comprehensive documentation
- Enterprise features (security, reliability)
- Long-term support commitment

See [Roadmap](roadmap.md) for complete future plans.

---

## Migration Guides

### Migrating to v0.2.0 (Future)

*To be written when v0.2.0 is released*

### Migrating to v1.0.0 (Future)

*To be written when v1.0.0 is released*

---

## Deprecation Policy

Starting from v1.0.0:

1. **Deprecation notice** - Feature marked deprecated, warning issued
2. **Grace period** - Minimum 6 months before removal
3. **Removal** - Feature removed in next major version

Example timeline:
- v1.5.0 (Jan 2027) - Feature X deprecated
- v1.6.0 (Apr 2027) - Still works, warnings continue
- v1.7.0 (Jul 2027) - Still works, warnings continue
- v2.0.0 (Oct 2027) - Feature X removed

---

## Breaking Changes Policy

**Before v1.0.0:** Breaking changes may occur in minor releases (0.x.0)

**After v1.0.0:** Breaking changes only in major releases (x.0.0)

**Communication:**
- Announced in changelog
- Documented in migration guide
- Discussed in release notes
- Community notice via mailing list and discussions

---

## Reporting Issues

Found a bug? [Open an issue](https://github.com/grauwen/utl-x/issues/new)

**Include:**
- UTL-X version
- Operating system
- Java version
- Steps to reproduce
- Expected vs actual behavior

---

## Contributing

Want to contribute? See [CONTRIBUTING.md](../../CONTRIBUTING.md)

**Areas needing help:**
- Bug fixes
- Feature implementation
- Documentation
- Testing
- Examples

---

## Acknowledgments

### v0.1.0 Contributors

Special thanks to:
- **Ir. Marcel A. Grauwen** - Project creator and lead developer
- **Glomidco B.V.** - Project sponsor
- All early testers and feedback providers

Want to be listed here? [Contribute](../../CONTRIBUTING.md)!

---

## Stay Updated

- 📧 **Email list:** announce@glomidco.com
- 🐦 **Twitter:** [@UTLXLang](https://twitter.com/UTLXLang)
- 💬 **Discussions:** [GitHub Discussions](https://github.com/grauwen/utl-x/discussions)
- 📰 **Blog:** [glomidco.com/blog](https://glomidco.com/blog)

---

## Links

- [Installation Guide](../getting-started/installation.md)
- [Roadmap](roadmap.md)
- [FAQ](faq.md)
- [GitHub Releases](https://github.com/grauwen/utl-x/releases)

---

*This changelog follows [Keep a Changelog](https://keepachangelog.com/) principles and [Semantic Versioning](https://semver.org/).*
