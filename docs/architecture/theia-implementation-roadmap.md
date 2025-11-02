# UTL-X Theia Extension: Implementation Roadmap

**Version:** 1.0
**Date:** 2025-11-02
**Status:** Planning Document
**Related Documents:**
- [theia-extension-design-with-design-time.md](theia-extension-design-with-design-time.md) - Architecture design
- [theia-extension-api-reference.md](theia-extension-api-reference.md) - API specification
- [theia-extension-implementation-guide.md](theia-extension-implementation-guide.md) - Implementation steps
- [theia-io-explained.md](theia-io-explained.md) - I/O communication details

---

## Executive Summary

This document tracks the implementation status of the UTL-X Theia Extension, mapping completed work to future planned features. The Theia extension provides a three-panel IDE for developing and testing UTL-X transformations with both design-time (schema-based) and runtime (data-based) modes.

**Current Status:** Foundation components implemented (CLI daemon, LSP protocol, core engine). Theia frontend not yet started.

---

## Implementation Status

### ✅ **Completed** - Foundation Infrastructure

#### 1. Core Transformation Engine
- **Location:** `modules/core/`
- **Status:** ✅ Production-ready
- **Features:**
  - UTL-X parser (lexer + parser)
  - AST representation
  - Type system (basic)
  - Transformation executor
  - Error handling and diagnostics

#### 2. CLI Interface
- **Location:** `modules/cli/`
- **Status:** ✅ Production-ready
- **Commands:**
  - `transform` - Execute transformations
  - `validate` - Validate UTL-X syntax
  - `lint` - Style and best practice checking
  - `functions` - Function registry explorer
  - `design` - Design-time tools (graph, schema generation)
  - `capture` - Test capture management

#### 3. LSP Daemon Server
- **Location:** `modules/daemon/`
- **Status:** ✅ Implemented and tested
- **Features:**
  - JSON-RPC 2.0 protocol over stdio
  - Language server protocol methods:
    - `textDocument/didOpen`, `didChange`, `didClose`
    - `textDocument/completion` (autocomplete)
    - `textDocument/hover` (type hints)
    - `textDocument/publishDiagnostics` (errors/warnings)
  - State management per document
  - Schema-only document support (for design-time mode)
  - Semantic diagnostics (path validation, type checking)

**Test Coverage:** 10/10 LSP conformance tests passing (100%)

#### 4. Standard Library
- **Location:** `stdlib/`
- **Status:** ✅ Comprehensive (400+ functions)
- **Categories:**
  - Array, String, Math, Date, Object
  - Serialization (JSON, XML, CSV, YAML)
  - Crypto, Encoding, Compression
  - Schema operations

**Test Coverage:** 465/465 conformance tests passing (100%)

#### 5. Format Support
- **Status:** ✅ All major formats implemented
- **Tier-1 Formats** (data): XML, JSON, YAML, CSV
- **Tier-2 Formats** (metadata): XSD, JSON Schema, Avro Schema, Protobuf

---

### 🚧 **Partially Implemented** - Design-Time Features

#### 6. Type System & Analysis
- **Location:** `modules/analysis/`
- **Status:** 🚧 Basic implementation, needs enhancement
- **Implemented:**
  - Type definitions (ObjectType, ArrayType, StringType, etc.)
  - Basic type inference
  - Type compatibility checking
- **Missing for Design-Time Mode:**
  - Schema parser (XSD → TypeEnvironment) - **Needs implementation**
  - Full type inference engine - **Needs enhancement**
  - Output schema generation (AST → JSON Schema) - **Needs implementation**
  - Union types and generics - **Needs enhancement**

**Priority:** High (required for design-time mode)

---

### 📋 **Planned** - Theia Extension

#### 7. Theia Extension Package Structure
- **Location:** `theia-extension/` (to be created)
- **Status:** 📋 Not started
- **Estimated Timeline:** 8 weeks
- **Components:**
  - `utlx-theia-extension/` - Main extension package
    - `src/browser/` - Frontend (React + Theia widgets)
    - `src/node/` - Backend (Node.js daemon client)
    - `src/common/` - Shared types and protocols
  - `utlx-theia-app/` - Test application

**Prerequisites:**
- Node.js 18+
- Theia framework 1.45+
- TypeScript 5.0+

#### 8. Backend Implementation (Node.js)
- **Status:** 📋 Planned
- **Estimated Timeline:** 2-3 weeks
- **Components:**
  - `UTLXDaemonClient` - Spawn and manage CLI daemon process
  - `UTLXService` - Backend service for frontend consumption
  - stdio pipe management
  - JSON-RPC protocol implementation
  - Error handling and logging

**Key Files:**
- `src/node/daemon/utlx-daemon-client.ts`
- `src/node/services/utlx-service.ts`
- `src/node/backend-module.ts`

#### 9. Frontend Implementation (Browser)
- **Status:** 📋 Planned
- **Estimated Timeline:** 3-4 weeks
- **Components:**

##### Three-Panel Layout
- **Left Panel** - `InputPanelWidget`
  - Design-Time: Schema editor (XSD, JSON Schema)
  - Runtime: Data editor (XML, JSON, YAML, CSV)
  - File upload and workspace integration
  - Multiple input support

- **Middle Panel** - UTL-X Editor
  - Monaco editor with UTL-X syntax highlighting
  - LSP integration (autocomplete, hover, diagnostics)
  - Real-time validation
  - Mode-aware features

- **Right Panel** - `OutputPanelWidget`
  - Design-Time: Generated schema display (JSON Schema)
  - Runtime: Transformation result
  - View modes (pretty, raw, tree)
  - Export and copy functionality

##### Mode Switching UI
- Mode selector toggle (Design-Time ↔ Runtime)
- Mode-aware panel labels
- State persistence per mode
- Automatic mode detection (based on UTLX `input`/`output` declaration)

#### 10. Design-Time Mode Features
- **Status:** 📋 Planned (depends on #6 Type System)
- **Estimated Timeline:** 4-6 weeks
- **Features:**
  - Schema loading and parsing (XSD, JSON Schema)
  - Type environment visualization
  - Schema-based autocomplete
  - Path validation against schema
  - Output schema inference
  - Type error highlighting
  - Schema tree view

**Critical Dependencies:**
- Schema parser implementation (XSD → TypeEnvironment)
- Type inference engine
- JSON Schema generator

#### 11. Runtime Mode Features
- **Status:** 📋 Planned
- **Estimated Timeline:** 2 weeks (simpler than design-time)
- **Features:**
  - Data loading (file, workspace, clipboard)
  - Transformation execution
  - Output rendering
  - Execution statistics
  - Auto-execution on change
  - Performance profiling

#### 12. Extension Points & Customization
- **Status:** 📋 Planned
- **Estimated Timeline:** 1-2 weeks
- **Features:**
  - Custom input providers (HTTP, database, REST API)
  - Custom output renderers
  - Extension configuration (preferences)
  - Command contributions
  - Keybinding contributions

---

## Implementation Phases

### **Phase 1: Runtime Mode Foundation** (Weeks 1-4)
**Goal:** Basic Theia extension with runtime transformation capabilities

**Deliverables:**
1. ✅ CLI daemon server with stdio protocol (COMPLETED)
2. Node.js daemon client implementation
3. Basic Theia extension setup
4. Three-panel layout (runtime mode only)
5. Input panel with file loading
6. UTL-X editor with basic syntax highlighting
7. Output panel with result display
8. Execute transformation button

**Success Criteria:**
- Can load XML/JSON input
- Can write UTL-X transformation
- Can execute and see output
- LSP provides basic autocomplete

### **Phase 2: LSP Integration & Polish** (Weeks 5-6)
**Goal:** Full LSP features for better developer experience

**Deliverables:**
1. Real-time validation and diagnostics
2. Hover hints with type information
3. Advanced autocomplete (context-aware)
4. Go-to-definition support
5. Error highlighting in editor
6. Auto-execution on change
7. Configuration preferences

**Success Criteria:**
- Autocomplete suggests valid paths
- Hover shows type information
- Errors appear in real-time
- Editor behaves like modern IDE

### **Phase 3: Design-Time Mode Foundation** (Weeks 7-10)
**Goal:** Enable schema-based type checking

**Prerequisites:**
- ⚠️ Requires implementing schema parser (modules/analysis)
- ⚠️ Requires type inference engine enhancement

**Deliverables:**
1. Mode selector UI
2. Schema parser (XSD → TypeEnvironment)
3. Schema loading in left panel
4. Type inference from transformation
5. Output schema generation
6. Mode state management
7. Schema tree view (basic)

**Success Criteria:**
- Can toggle between design-time and runtime modes
- Can load XSD schema
- Type checker validates paths against schema
- Right panel shows inferred JSON Schema

### **Phase 4: Advanced Design-Time Features** (Weeks 11-14)
**Goal:** Full design-time capabilities

**Deliverables:**
1. JSON Schema support (in addition to XSD)
2. Advanced schema tree view
3. Type graph visualization
4. Quick fixes for type errors
5. Schema comparison tool
6. Sample data generation from schema
7. API documentation export

**Success Criteria:**
- Supports XSD and JSON Schema
- Visual type graph shows data flow
- Quick fixes auto-correct common issues
- Can generate test data from schemas

### **Phase 5: Production Readiness** (Weeks 15-16)
**Goal:** Polish and deployment

**Deliverables:**
1. Comprehensive testing (unit + integration)
2. Performance optimization
3. Error handling hardening
4. User documentation
5. Example transformations
6. Packaging and distribution
7. VS Code compatibility layer (optional)

**Success Criteria:**
- All tests passing
- No memory leaks
- Fast startup (<2s)
- Complete user guide
- Published to npm/marketplace

---

## Technical Debt & Known Gaps

### Current Limitations

1. **No Theia Extension Yet**
   - All design documents exist, but no actual Theia code
   - Users must use CLI for transformations

2. **Schema Parser Missing**
   - Design-time mode requires XSD → TypeEnvironment converter
   - Currently no schema parsing infrastructure
   - **Estimated effort:** 2-3 weeks

3. **Type Inference Limited**
   - Basic type inference works
   - Missing union types, generics, advanced inference
   - **Estimated effort:** 2-3 weeks

4. **Output Schema Generation Missing**
   - Cannot generate JSON Schema from transformation AST
   - Required for design-time mode
   - **Estimated effort:** 2 weeks

5. **No UI Components**
   - Schema tree view
   - Type graph visualization
   - Mode switching UI
   - **Estimated effort:** 3-4 weeks

### Risks & Mitigation

**Risk 1:** Theia framework complexity
- **Impact:** High learning curve, slower development
- **Mitigation:** Start with runtime mode (simpler), use Theia examples
- **Probability:** Medium

**Risk 2:** Schema parsing complexity (XSD)
- **Impact:** XSD is complex, edge cases difficult
- **Mitigation:** Focus on common XSD subset first, extend later
- **Probability:** High

**Risk 3:** Performance with large documents
- **Impact:** Slow editor, laggy autocomplete
- **Mitigation:** Implement debouncing, caching, incremental parsing
- **Probability:** Medium

**Risk 4:** Daemon stability
- **Impact:** Crashes, memory leaks affect user experience
- **Mitigation:** Comprehensive error handling, auto-restart, memory monitoring
- **Probability:** Low (already tested extensively)

---

## Alternative Approaches Considered

### Option A: VS Code Extension (Instead of Theia)
**Pros:**
- Larger user base
- Better marketplace distribution
- Simpler extension API

**Cons:**
- Less flexible UI customization
- Can't create custom three-panel layout easily

**Decision:** Keep Theia for maximum flexibility, consider VS Code port later

### Option B: Web-Based Transformation Playground
**Pros:**
- No installation required
- Easier to share transformations
- Lower barrier to entry

**Cons:**
- Limited file system access
- Can't integrate with user's development workflow

**Decision:** Complementary to Theia extension, not replacement

### Option C: CLI-Only Workflow
**Pros:**
- Already implemented
- Works for power users
- Scriptable and automatable

**Cons:**
- No visual feedback
- Harder for beginners
- No design-time mode

**Decision:** Keep CLI as foundation, add Theia for better UX

---

## Resource Requirements

### Development Team
- **Backend Developer** (Kotlin/Java): 0.5 FTE
  - Focus: Schema parser, type inference enhancement
  - Timeline: Weeks 7-10 (Phase 3)

- **Frontend Developer** (TypeScript/React): 1.0 FTE
  - Focus: Theia extension implementation
  - Timeline: Weeks 1-16 (all phases)

- **QA Engineer**: 0.25 FTE
  - Focus: Testing, conformance verification
  - Timeline: Ongoing

### Infrastructure
- Node.js development environment
- Theia test application
- CI/CD for extension builds
- Documentation site

### External Dependencies
- Theia framework (Eclipse Foundation)
- Monaco editor (Microsoft)
- Kotlin stdlib (JetBrains)

---

## Success Metrics

### Phase 1 (Runtime Mode)
- ✅ Extension loads without errors
- ✅ Can execute basic transformations (XML → JSON)
- ✅ LSP provides autocomplete
- ✅ User can complete workflow in <2 minutes

### Phase 2 (LSP Integration)
- ✅ Real-time diagnostics within 500ms
- ✅ Autocomplete suggestions within 100ms
- ✅ 90%+ user satisfaction with editor experience

### Phase 3 (Design-Time Mode)
- ✅ Schema parsing works for 80% of XSD features
- ✅ Type checking catches 95% of path errors
- ✅ Output schema inference accuracy >90%

### Phase 4 (Advanced Features)
- ✅ Visual type graph renders correctly
- ✅ Quick fixes resolve 70% of common errors
- ✅ Sample data generation works for all supported schemas

### Phase 5 (Production)
- ✅ Unit test coverage >80%
- ✅ Integration test coverage >70%
- ✅ Performance: Startup <2s, autocomplete <100ms
- ✅ Zero critical bugs in production

---

## Migration Path

### For Current CLI Users
1. Continue using CLI for automation and scripting
2. Try Theia extension for interactive development
3. Gradually adopt design-time mode for new projects

### For New Users
1. Start with Theia extension (easier learning curve)
2. Learn runtime mode first (simpler concept)
3. Progress to design-time mode for schema-driven development

---

## Next Steps

### Immediate Actions (Week 1)
1. ✅ Review and commit design documents
2. Set up `theia-extension/` directory structure
3. Initialize npm packages
4. Create Theia application for testing

### Short-term (Weeks 2-4)
1. Implement Node.js daemon client
2. Create basic three-panel layout
3. Integrate LSP daemon with Theia
4. First prototype demo

### Medium-term (Weeks 5-10)
1. Complete runtime mode implementation
2. Implement schema parser (modules/analysis)
3. Begin design-time mode implementation
4. User testing and feedback

### Long-term (Weeks 11-16)
1. Advanced design-time features
2. Performance optimization
3. Production hardening
4. Documentation and examples
5. Public release

---

## Conclusion

The UTL-X Theia extension architecture is well-designed with comprehensive documentation. The foundation components (CLI daemon, LSP protocol, core engine) are production-ready with 100% test coverage.

**Current State:** Infrastructure complete, Theia frontend pending
**Estimated Timeline:** 16 weeks for full implementation
**Key Blocker:** Schema parser for design-time mode
**Recommendation:** Start with Phase 1 (Runtime Mode) to deliver value quickly, then invest in schema parser for Phase 3 (Design-Time Mode)

---

**Version:** 1.0
**Last Updated:** 2025-11-02
**Next Review:** After Phase 1 completion
**Maintainers:** UTL-X Architecture Team
