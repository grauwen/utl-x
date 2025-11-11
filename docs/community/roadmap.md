# UTL-X Roadmap

This roadmap outlines the planned development of UTL-X from alpha to stable release.

**Last Updated:** January 2026  
**Current Version:** 0.1.0-SNAPSHOT (Alpha)

---

## Vision

Build the **best open-source, format-agnostic transformation language** that:
- Works seamlessly with XML, JSON, CSV, YAML, and custom formats
- Combines XSLT's declarative power with modern functional programming
- Provides excellent developer experience with strong tooling
- Maintains high performance and reliability
- Remains truly open source (no vendor lock-in)

---

## Release Timeline

```
2026 Q1          Q2          Q3          Q4          2027
  │             │           │           │            │
  ├─ v0.1.0     ├─ v0.2.0   ├─ v0.3.0   ├─ v1.0.0    ├─ v1.1.0
  │  Alpha      │  Beta     │  RC       │  Stable    │  Features
  │             │           │           │            │
  └─────────────┴───────────┴───────────┴────────────┴─────────▶
     Now        3 months    6 months    9 months    12 months
```

---

## Current Status: (Alpha)

**Released:** Novemeber 2025  
**Status:** 🚧 Early Development

### What Works

- ✅ Core trasnformation language syntax and parser %UTLX 1.0
- ✅ XML input/output (Tier 1)
- ✅ JSON input/output (Tier 1)
- ✅ CSV Input/Output (Tier 1)
- ✅ YAML Input/Output (Tier 1)
- ✅ Core schema language syntax and parser %USDL 1.0
- ✅ XSD Input/Output (Tier 2)
- ✅ JSCH (JSON Schema) Input/Output (Tier 2)
- ✅ AVRO Input/Output (Tier 2)
- ✅ PROTObuf Input/Output (Tier 2)
- ✅ Basic selectors and navigation
- ✅ Multiple inputs
- ✅ Pipeline operator
- ✅ Higher-order functions (map, filter, reduce)
- ✅ User-defined functions 
- ✅ Lammbda functions
- ✅ Type inference
- ✅ UTLX CLI tool (basic)
- ✅ Build system (Gradle)
- ✅ stdlib with more than 650 functions, unmatched by any other tool or product

### Known Limitations

- ⚠️ UTLX IDE with genAI UTLX support via MCP server component (SOON)
- ⚠️ No IDE plugins
- ⚠️ Template matching (put on hold)
- ⚠️ Not optimized for performance (mutliple runtime optimized components planned)
- ⚠️ Limited error messages (WIP)
- ⚠️ Validate implemented but limted
- ⚠️ No streaming support yet
- ⚠️ Alpha stability (expect bugs)
- ⚠️ TSCH (CSV Meta Data not yet implemented
- ⚠️ JSON-LD not yet implemented
- ⚠️ brew & chocolate dsitribution planned but not yet supported
- ⚠️ VS Code extension not yet available on marketplace
- ⚠️ IntelliJ plugin not yet available on JetBrains marketplace
- ⚠️ Several websites with help, tips, tricks not live yet
- ⚠️ No SaaS version (Theia Cloud) of UTLX IDE yet available

### Use Cases

**Good for:**
- ✅ Prototyping transformations
- ✅ Meta Data to Meta Data mappings
- ✅ Learning the language
- ✅ Internal development tools
- ✅ Experiments and proof-of-concepts

**Not ready yet for:**
- ❌ Production systems (2000+ tests passing, non-prod could be tried)
- ❌ Mission-critical applications (STP processing needs more burn in before advised)
- ❌ High-volume workloads (work on optimized runtimes ongoing)
- ❌ Customer-facing services (UTLX IDE is being evaluated)

---

### Core Features

#### Format Support
- 🎯 **CSV Input/Output**
  - Support for various CSV dialects
  - Header handling
  - Quote and escape character configuration
  - Custom delimiters

- 🎯 **YAML Input/Output**
  - YAML 1.2 support
  - Anchors and aliases
  - Multi-document files



- 🎯 **Expanded Standard Library**
  - Date/time functions
  - More string operations
  - Collection utilities
  - Math functions

#### Developer Experience
- 🎯 **VS Code Extension**
  - Syntax highlighting
  - Auto-completion
  - Error checking
  - Code snippets

- 🎯 **IntelliJ IDEA Plugin**
  - Syntax highlighting
  - Code completion
  - Quick fixes
  - Refactoring support

- 🎯 **Better Error Messages**
  - Clear, actionable error descriptions
  - Line/column information
  - Suggestions for fixes

#### Performance
- 🎯 **Basic Optimization**
  - Constant folding
  - Dead code elimination
  - Simple pattern optimizations

### Success Criteria

- ✅ CSV and YAML transformations work reliably
- ✅ 100% of planned stdlib functions implemented
- ✅ Error messages are clear and helpful
- ✅ 10+ production-ready examples
- ✅ Performance within 2x of hand-coded transformations

---

## Q3 2026: v0.3.0 (Release Candidate)

**Target Release:** July 2026  
**Status:** 🔮 Future

### Core Features

#### Language Features
- 🎯 **Enhanced Template Matching**
  - Priority rules
  - Mode support (like XSLT modes)
  - Named templates

#### Stability & Quality
- 🔮 **Comprehensive Testing**
  - 95%+ code coverage
  - Property-based testing
  - Fuzzing tests
  - Performance benchmarks

- 🔮 **Stability Improvements**
  - No breaking API changes
  - Bug fixes from beta feedback
  - Memory leak fixes
  - Thread safety guarantees

#### Advanced Features
- 🔮 **Streaming Support**
  - Process large files without loading into memory
  - Stream XML/JSON/CSV/YAML parsing
  - Chunked output

- 🔮 **Error Handling**
  - Try-catch expressions
  - Custom error messages
  - Error recovery strategies

- 🔮 **Advanced Template Features**
  - Template imports
  - Cross-template variables
  - Recursive templates optimization

#### Tooling
- 🔮 **Online Playground**
  - Try UTL-X in browser
  - Share transformations
  - Interactive tutorials

- 🔮 **Documentation Site**
  - Comprehensive API docs
  - Interactive examples
  - Search functionality
  - Multiple languages

- 🔮 **CLI Enhancements**
  - Watch mode
  - Batch processing
  - Debug mode
  - Profiling

#### Integration
- 🔮 **Maven/Gradle Plugins**
  - Generate transformations at build time
  - Validate transformations
  - Package transformations

- 🔮 **Spring Boot Integration**
  - Auto-configuration
  - REST endpoint support
  - Actuator integration

### Success Criteria

- ✅ No critical bugs
- ✅ Performance targets met (< 5ms avg transformation)
- ✅ Comprehensive documentation
- ✅ 50+ real-world examples
- ✅ Community feedback incorporated
- ✅ Beta testers satisfied
- ✅ Compatible with JDK 11, 17, 21

---

## Q4 2026: v1.0.0 (Stable)

**Target Release:** October 2026  
**Status:** 🎯 Major Milestone

### The 1.0 Promise

**Stable API:** No breaking changes in 1.x releases  
**Production Ready:** Suitable for mission-critical systems  
**Long-term Support:** Security updates and bug fixes for 2+ years

### Final Features

#### Core
- 🎯 **Stable Language Specification**
  - Frozen grammar
  - Complete semantics
  - Formal type system

- 🎯 **Performance Optimization**
  - JIT compilation optimizations
  - Memory efficiency
  - Parallel processing where safe

- 🎯 **Production Monitoring**
  - Metrics and logging
  - Performance profiling
  - Memory tracking

#### Formats
- 🎯 **Additional Formats**
  - Properties files
  - TOML (optional)
  - EDI (plugin)

- 🎯 **Format Plugins API**
  - Easy custom format development
  - Plugin distribution mechanism
  - Plugin registry

#### Enterprise Features
- 🎯 **Security**
  - Input validation
  - Resource limits
  - Sandboxed execution

- 🎯 **Reliability**
  - Graceful error handling
  - Transaction support
  - Retry mechanisms

### Success Criteria

- ✅ 1000+ GitHub stars
- ✅ 100+ production deployments
- ✅ 50+ contributors
- ✅ < 10 open critical bugs
- ✅ Comprehensive test suite
- ✅ Full documentation coverage
- ✅ Community satisfaction > 85%
- ✅ Performance benchmarks met

---

## 2027: v1.x Enhancements

### v1.1.0 - Native Compilation

**Target:** Q1 2027

- 🔮 **GraalVM Native Image**
  - Standalone executables
  - No JVM required
  - Faster startup (< 100ms)
  - Lower memory usage

- 🔮 **Platform Binaries**
  - Linux (x64, ARM64)
  - macOS (Intel, Apple Silicon)
  - Windows (x64)

### v1.2.0 - JavaScript Runtime

**Target:** Q2 2027

- 🔮 **Transpile to JavaScript**
  - Run UTL-X in Node.js
  - Run UTL-X in browsers
  - npm package

- 🔮 **Use Cases**
  - Frontend data transformation
  - Serverless functions
  - Cross-platform tooling

### v1.3.0 - Advanced Features

**Target:** Q3 2027

- 🔮 **Module System**
  - Import/export functions
  - Package management
  - Dependency resolution

- 🔮 **Macros**
  - Compile-time code generation
  - DSL creation
  - Code reuse

---

## Long-term Vision (v2.0+)

### 2028 and Beyond

#### v2.0.0 - Major Evolution

- 🌟 **Type Classes**
  - Polymorphic functions
  - Generic programming
  - Trait-like behavior

- 🌟 **Query Language**
  - SQL-like queries for data
  - Graph queries
  - Complex filtering

- 🌟 **Visual Editor**
  - Drag-and-drop transformations
  - Visual data mapping
  - Code generation

#### Ecosystem Growth

- 🌟 **Plugin Ecosystem**
  - Third-party plugins
  - Format converters
  - Integration adapters

- 🌟 **Commercial Tools**
  - IDE with debugging
  - Team collaboration features
  - Cloud-based playground

- 🌟 **Integration Platform**
  - Apache Camel integration
  - Spring Integration support
  - Kafka Connect

---

## Community Milestones

### Growth Targets

**2026:**
- 1,000 GitHub stars
- 100 contributors
- 10 companies using in production
- 50+ blog posts/articles

**2027:**
- 5,000 GitHub stars
- 500 contributors
- 100 companies using in production
- First UTL-X conference talk

**2028:**
- 10,000 GitHub stars
- 1,000 contributors
- 1,000 companies using in production
- UTL-X meetups worldwide

### Governance

#### Open Source Foundation

**Target:** 2027

Consider joining:
- Apache Software Foundation
- Eclipse Foundation
- Linux Foundation

**Benefits:**
- Neutral governance
- Legal protection
- Brand recognition
- Community trust

---

## How You Can Help

### Contribute to Development

**Priority areas:**

1. **Core Features**
   - CSV parser implementation
   - YAML support
   - Standard library functions

2. **Documentation**
   - Tutorials and guides
   - Examples
   - Translation

3. **Tooling**
   - IDE plugins
   - Build tool integration
   - CI/CD examples

4. **Testing**
   - Write tests
   - Report bugs
   - Performance testing

### Provide Feedback

- 💬 [Discussions](https://github.com/grauwen/utl-x/discussions) - Share ideas
- 🐛 [Issues](https://github.com/grauwen/utl-x/issues) - Report bugs
- 📧 [Email](mailto:community@glomidco.com) - General feedback

### Spread the Word

- ⭐ Star on GitHub
- 🐦 Share on Twitter
- 📝 Write blog posts
- 🎤 Present at meetups
- 👥 Tell colleagues

---

## Funding & Sustainability

### Current Funding

**Bootstrapped by Glomidco B.V.**
- Initial development self-funded
- Commercial licenses generate revenue
- Reinvested in development

### Future Funding

**Potential sources:**
- 💰 Commercial license sales
- 💰 Support contracts
- 💰 Training and consulting
- 💰 Sponsorships (GitHub Sponsors)
- 💰 Grants (foundation support)

### Sustainability Goals

**Target:** Self-sustaining by v1.0

- 2-3 full-time developers
- Community manager
- Technical writer
- Infrastructure costs covered

---

## Release Principles

### Semantic Versioning

We follow [Semantic Versioning](https://semver.org/):

- **Major (x.0.0):** Breaking changes
- **Minor (0.x.0):** New features, backwards compatible
- **Patch (0.0.x):** Bug fixes only

### Release Schedule

- **Major releases:** Annually
- **Minor releases:** Every 3 months
- **Patch releases:** As needed (critical bugs)

### Stability Guarantees

**1.0.0+ Promises:**
- No breaking changes in 1.x
- Deprecation warnings 6 months before removal
- Security updates for 2 years minimum
- Bug fixes for critical issues

---

## Staying Updated

### Follow Progress

- 📊 [GitHub Projects](https://github.com/grauwen/utl-x/projects) - Sprint boards
- 📝 [Changelog](changelog.md) - Release notes
- 🐦 [Twitter](https://twitter.com/UTLXLang) - Announcements
- 📧 [Mailing List](mailto:announce@glomidco.com) - Email updates

### Monthly Updates

We publish monthly progress reports:
- What shipped
- What's in progress
- Community highlights
- Next month's goals

Subscribe: announce@glomidco.com

---

## Questions?

- 💬 [Discussions](https://github.com/grauwen/utl-x/discussions)
- 📧 [Email](mailto:community@glomidco.com)
- 🐦 [Twitter](https://twitter.com/UTLXLang)

**Your feedback shapes the roadmap!**

---

**This roadmap is subject to change based on community feedback and practical considerations.**

*Last updated: January 2026*
