# MCP-Assisted UTL-X Generation - Documentation Index

This directory contains comprehensive architecture documentation for implementing AI-assisted UTL-X transformation generation using the Model Context Protocol (MCP).

## Overview

The MCP-assisted generation system enables users to create UTL-X transformations using natural language prompts, with AI assistance powered by Large Language Models (Claude, GPT-4, etc.) that have access to UTL-X context through standardized MCP tools.

**Vision**: Users provide input data/schema + natural language description → AI generates valid, type-safe UTL-X transformation code.

**Integration Point**: Theia IDE with 3-panel layout (Input | UTLX | Output)

## Documentation Structure

### 1. Core Architecture
📄 **[mcp-assisted-generation.md](./mcp-assisted-generation.md)** (Main Document)
- Problem statement and solution overview
- System architecture and components
- MCP tool specifications (6 tools)
- LLM integration strategy
- Theia IDE integration
- Implementation roadmap (4 phases, ~12 weeks)
- Security and deployment considerations
- Success metrics and evaluation criteria

**Start here** for complete architectural understanding.

### 2. Implementation Guide
📋 **[mcp-implementation-checklist.md](./mcp-implementation-checklist.md)**
- Detailed checklist for all 4 implementation phases
- Week-by-week breakdown of tasks
- Success metrics for each phase
- Dependencies and timeline
- Resource requirements

**Use this** to track implementation progress.

### 3. Technical Reference
🔧 **[mcp-tools-reference.md](./mcp-tools-reference.md)**
- Complete specification for each of the 6 MCP tools
- API contracts (parameters, return types)
- Implementation notes
- Example usage for each tool
- Error handling patterns
- Performance targets
- Testing strategy

**Use this** when implementing individual tools.

### 4. Project Structure
📁 **[mcp-server-project-structure.md](./mcp-server-project-structure.md)**
- Complete directory structure for MCP server
- TypeScript project setup
- File-by-file implementation templates
- Configuration examples
- Docker deployment
- CI/CD integration
- Development workflow

**Use this** to scaffold the MCP server project.

### 5. Prompt Engineering
💬 **[mcp-prompt-templates.md](./mcp-prompt-templates.md)**
- System prompt for LLM
- Request/response patterns
- Error recovery strategies
- Multi-turn conversation examples
- Best practice suggestions
- Example-based learning prompts

**Use this** to configure LLM behavior and responses.

### 6. Schema-to-Schema Analysis (NEW)
🔍 **[mcp-schema-to-schema-analysis.md](./mcp-schema-to-schema-analysis.md)**
- Coverage analysis (input → output schema compatibility)
- Gap identification (missing required fields)
- Transformation variant generation (speed vs. memory vs. readability)
- 2 additional MCP tools (Tool 7 & 8)
- Daemon API extensions
- UI integration for analysis workflow

**Use this** for advanced schema-driven transformation generation.

### 7. Agentic AI Integration (NEW)
🤖 **[mcp-agentic-ai-integration.md](./mcp-agentic-ai-integration.md)**
- **Strategic assessment**: Can MCP-UTLX be packaged as reusable agent?
- **Gap analysis**: What's missing for enterprise agent adoption
- **Required enhancements**: REST/gRPC APIs, multi-tenancy, security, observability
- **Integration patterns**: API Gateway, Event-Driven, Orchestration, Service Mesh
- **Deployment architectures**: Kubernetes, multi-region
- **Roadmap**: 4-6 weeks to agent-ready status

**Use this** to evaluate MCP-UTLX as an enterprise agentic AI asset.

### 8. Implementation Order (NEW)
📅 **[mcp-implementation-order.md](./mcp-implementation-order.md)**
- **Critical decision**: MCP Server first vs. Theia IDE first?
- **Recommended approach**: Backend-first (MCP → LLM → Theia)
- **Risk analysis**: Why MCP-first reduces technical risk
- **Testing strategy**: How to validate MCP without Theia
- **Timeline**: Week-by-week breakdown

**Use this** to plan development sequence.

### 9. Standalone Usage (NEW)
🔧 **[mcp-standalone-usage.md](./mcp-standalone-usage.md)**
- **Can MCP work without LLM?** YES! 7 of 8 tools work standalone
- **Standalone capabilities**: Schema analysis, validation, execution, testing
- **Practical use cases**: CI/CD, documentation, testing frameworks
- **Incremental adoption**: Use immediately, add LLM later
- **Value without AI**: Comprehensive UTL-X toolkit

**Use this** to understand MCP's value independent of LLM.

### 10. Theia Monaco LSP MCP Integration (NEW)
🎨 **[theia-monaco-lsp-mcp-integration.md](./theia-monaco-lsp-mcp-integration.md)**
- **LSP vs MCP**: Separate, parallel systems with different responsibilities
- **Monaco Editor Integration**: Direct LSP connection to daemon (NOT through MCP)
- **VS Code Plugin Compatibility**: Theia supports most VS Code extensions
- **Architecture Principles**: Why LSP should NOT route through MCP
- **Implementation Details**: WebSocket LSP setup, MCP client setup, AI Assistant panel
- **XML/XSD Extensions**: Reusing existing VS Code extensions in Theia

**Use this** to understand Theia IDE integration architecture and avoid common pitfalls.

### 11. LSP Communication Patterns Clarification (NEW)
🔌 **[lsp-communication-patterns-clarification.md](./lsp-communication-patterns-clarification.md)**
- **Critical Clarification**: MCP does NOT communicate with LSP - uses separate REST API
- **Daemon Sessions**: ONE LSP session (Theia) + N REST sessions (MCP, tools)
- **Current State**: LSP already works (STDIO/Socket), REST API needs to be added
- **Required Changes**: Add HTTP/REST API server to daemon (2-3 days work)
- **Implementation Guide**: Ktor-based REST API with validation, execution, schema endpoints
- **Architecture Decision**: Run both LSP and REST API servers in single daemon process

**Use this** to understand daemon communication architecture and required REST API implementation.

### 12. Daemon REST API Implementation Guide (NEW)
⚙️ **[daemon-rest-api-implementation-guide.md](./daemon-rest-api-implementation-guide.md)**
- **What to Build**: Comprehensive task breakdown (dependencies, server, endpoints, CLI args)
- **REST vs gRPC**: Technology choice analysis (REST wins for MCP compatibility, DX, speed)
- **Testing Strategy**: 4-level approach (unit, integration, manual, MCP integration)
- **Dual Server Mode**: LSP and REST API run concurrently in single daemon process
- **Implementation Order**: Daemon → MCP → Theia (bottom-up, 12 weeks total)
- **Design Impact**: Minimal changes to UTL-X core (additive only, backward compatible)
- **Complete Code Examples**: Ktor server, DTOs, tests, curl commands

**Use this** as the implementation blueprint for adding REST API to the daemon.

### 13. CLI/Daemon Split Architecture (NEW)
🔀 **[cli-daemon-split-architecture.md](./cli-daemon-split-architecture.md)**
- **Architectural Split**: Separate `utlx` (CLI) and `utlxd` (daemon server) executables
- **Problem Analysis**: CLI bloat, slow startup, large footprint, mixed concerns
- **Proposed Architecture**: New `modules/server/` with daemon + REST API
- **Gradle Organization**: Multi-module build with shared core modules
- **Benefits**: 15MB CLI (vs 50MB), <100ms startup (vs 1.5s), deployment flexibility
- **Implementation Plan**: 4 phases over 2 weeks with detailed tasks
- **Migration Guide**: For users, CI/CD pipelines, Theia IDE, MCP server
- **Testing Strategy**: Unit, integration, performance, end-to-end tests

**Use this** to understand the architectural split and implementation roadmap.

## Quick Start

### For Decision Makers
1. Read **Section 1-3** of `mcp-assisted-generation.md` (Problem, Solution, Architecture)
2. Review **Section 7** (Implementation Roadmap) for timeline and resources
3. Check **Section 9** (Success Metrics) for expected outcomes

### For Architects
1. Read complete `mcp-assisted-generation.md`
2. Review `mcp-tools-reference.md` for technical specifications
3. Study `mcp-server-project-structure.md` for system design

### For Developers
1. Read `mcp-implementation-checklist.md` for task breakdown
2. Use `mcp-server-project-structure.md` to set up project
3. Refer to `mcp-tools-reference.md` for implementation details
4. Configure LLM using `mcp-prompt-templates.md`

### For DevOps/SRE
1. Review **Section 8** of `mcp-assisted-generation.md` (Security & Deployment)
2. Check Docker configuration in `mcp-server-project-structure.md`
3. Review monitoring and observability requirements

## Key Components

### MCP Server (NEW - TypeScript)
- Implements 6 MCP tools
- Manages LLM communication
- Coordinates with UTL-X daemon
- Handles conformance suite indexing

### UTL-X Daemon (EXISTING - Kotlin)
- Provides validation API
- Provides type inference API
- Provides execution API
- Provides schema parsing API

### Theia Extension (NEW - TypeScript)
- AI Assistant panel UI
- Prompt input and conversation history
- UTLX preview and insertion
- Integration with existing LSP features

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Theia IDE (Browser)                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   Input     │  │    UTLX     │  │   Output    │     │
│  │   Panel     │  │   Editor    │  │   Panel     │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
│         │                 │                │             │
│         └─────────────────┴────────────────┘             │
│                      │                                   │
│              ┌───────────────┐                           │
│              │ AI Assistant  │                           │
│              │    Panel      │                           │
│              └───────┬───────┘                           │
└──────────────────────┼───────────────────────────────────┘
                       │ MCP Protocol (JSON-RPC)
                       │
         ┌─────────────▼─────────────┐
         │  UTLX MCP Server (NEW)    │
         │  - Tool Registry          │
         │  - LLM Client             │
         │  - Conformance Indexer    │
         └─────────┬─────────────────┘
                   │
          ┌────────┴────────┐
          │                 │
    Daemon API         LLM API
          │                 │
┌─────────▼──────┐    ┌────▼──────────┐
│ UTL-X Daemon   │    │   LLM         │
│ (EXISTING)     │    │ Claude/GPT-4  │
│ - Validator    │    │ or Local      │
│ - Type Checker │    │               │
│ - Executor     │    │               │
└────────────────┘    └───────────────┘
```

## MCP Tools Summary

| Tool | Purpose | Daemon API |
|------|---------|------------|
| `get_input_schema` | Parse schemas/data | `/api/parse-schema`, `/api/infer-schema` |
| `get_stdlib_functions` | Retrieve UTL-X functions | Local JSON registry |
| `validate_utlx` | Syntax & type checking | `/api/validate` |
| `infer_output_schema` | Output schema inference | `/api/infer-schema` |
| `execute_transformation` | Test transformations | `/api/execute` |
| `get_examples` | Find similar tests | Local conformance index |
| **`analyze_schema_compatibility`** | **Schema coverage analysis** | **`/api/analyze-compatibility`** |
| **`generate_transformation_variants`** | **Generate optimized variants** | **`/api/generate-variants`** |

## Implementation Phases

### Phase 1: MCP Server Foundation (3-4 weeks)
- Set up TypeScript project
- Implement 6 MCP tools
- Integrate with daemon
- Build conformance suite index

### Phase 2: LLM Integration (2-3 weeks)
- Implement LLM clients (Claude, OpenAI, Ollama)
- Develop prompt engineering
- Create generation pipeline
- Build validation feedback loop

### Phase 3: Theia IDE Integration (2-3 weeks)
- Develop Theia extension
- Create AI Assistant UI
- Connect to MCP server
- Implement user workflows

### Phase 4: Advanced Features (3-4 weeks)
- Learn from user edits
- Smart suggestions
- Performance optimization
- Deployment & monitoring

**Total Timeline**: ~12-14 weeks

## Success Metrics

- **Generation Quality**: >80% of transformations valid on first attempt
- **User Satisfaction**: >4.0/5.0 rating
- **Performance**: <5s simple, <15s complex transformations
- **Adoption**: >50% of new transformations use AI assistance within 3 months
- **Error Rate**: <5% of requests result in errors

## Technology Stack

### MCP Server
- **Language**: TypeScript
- **Runtime**: Node.js 18+
- **Framework**: Express.js
- **Testing**: Jest
- **Validation**: Zod
- **Logging**: Winston

### Daemon Integration
- **Protocol**: HTTP/JSON-RPC
- **Client**: Axios
- **Error Handling**: Custom daemon error types

### LLM Integration
- **Providers**: Anthropic Claude, OpenAI, Ollama
- **SDKs**: @anthropic-ai/sdk, openai
- **Prompting**: Template-based with few-shot examples

### Theia Extension
- **Framework**: Eclipse Theia extension API
- **Language**: TypeScript
- **UI**: React components
- **State**: MobX or Redux

## Security Considerations

- ✅ On-premise deployment option (no cloud LLM required)
- ✅ Data stays within organization (local Ollama support)
- ✅ Validation before execution (sandbox environment)
- ✅ Schema validation and type checking
- ✅ Rate limiting and authentication
- ✅ Audit logging for compliance

## Dependencies

### External Services
- UTL-X Daemon (must be running)
- LLM API (Claude, OpenAI, or local Ollama)

### Build Dependencies
- Node.js 18+
- TypeScript 5.3+
- Gradle (for daemon builds)
- Java 17+ (for daemon)

### Runtime Dependencies
- Conformance suite (for examples)
- Function registry (stdlib metadata)

## Getting Started

1. **Read the architecture document**:
   ```bash
   open docs/architecture/mcp-assisted-generation.md
   ```

2. **Review implementation checklist**:
   ```bash
   open docs/architecture/mcp-implementation-checklist.md
   ```

3. **Set up development environment**:
   ```bash
   # Create MCP server project
   mkdir mcp-server
   cd mcp-server
   npm init -y

   # Install dependencies
   npm install @anthropic-ai/sdk axios express winston yaml zod
   npm install -D @types/express @types/node typescript jest ts-jest
   ```

4. **Start UTL-X daemon**:
   ```bash
   # Build daemon
   ./gradlew :modules:daemon:jar

   # Start daemon
   java -jar modules/daemon/build/libs/daemon-1.0.0-SNAPSHOT.jar \
     --socket 7778 --verbose
   ```

5. **Implement first MCP tool** (get_stdlib_functions - easiest):
   ```bash
   # Create tool file
   touch src/tools/GetStdlibFunctions.ts

   # Implement based on mcp-tools-reference.md
   ```

## Next Steps

After reviewing this documentation:

1. **Decision to Proceed**: Evaluate if MCP-assisted generation aligns with project goals
2. **Resource Allocation**: Assign 2-3 developers for ~12 weeks
3. **Environment Setup**: Prepare development environment and dependencies
4. **Phase 1 Kickoff**: Begin MCP server foundation implementation
5. **Iterative Development**: Follow checklist, build incrementally, test continuously

## Questions and Feedback

For questions or feedback on this architecture:
- Review the detailed documents listed above
- Check the implementation checklist for specific tasks
- Consult the tools reference for technical details
- Refer to prompt templates for LLM configuration

## Related Documentation

- `/docs/architecture/theia-extension-design-with-design-time.md` - Existing Theia IDE architecture
- `/docs/architecture/theia-monaco-lsp-mcp-integration.md` - Monaco Editor, LSP, and MCP integration guide
- `/docs/architecture/cli-daemon-split-architecture.md` - CLI/Daemon architectural split guide
- `/docs/gen-ai/CLAUDE.md` - Project overview for Claude Code
- `/modules/daemon/README.md` - Daemon API documentation (if exists)
- `/stdlib/README.md` - Standard library documentation (if exists)

## Version History

- **v0.1.0** (2025-11-02): Initial architecture documentation
  - Core architecture document
  - Implementation checklist
  - Tools reference (6 tools)
  - Project structure
  - Prompt templates

- **v0.2.0** (2025-11-03): Schema-to-schema analysis extension
  - Added schema compatibility analysis
  - Added transformation variant generation
  - 2 additional MCP tools (Tool 7 & 8)
  - Daemon API extensions for coverage analysis
  - UI integration for gap resolution workflow

- **v0.3.0** (2025-11-03): Enterprise readiness and integration architecture
  - Agentic AI integration assessment (enterprise agent readiness)
  - Implementation order guidance (backend-first approach)
  - Standalone usage documentation (MCP without LLM)
  - Theia Monaco LSP MCP integration guide
  - VS Code plugin compatibility documentation

- **v0.4.0** (2025-11-03): CLI/Daemon architectural split
  - CLI/Daemon split architecture guide
  - Problem analysis: bloat, slow startup, large footprint
  - New `modules/server/` with daemon + REST API
  - Benefits: 15MB CLI, <100ms startup, deployment flexibility
  - Implementation plan: 4 phases over 2 weeks
  - Migration guide for users, CI/CD, IDE integration

---

**Status**: 📝 Documentation Complete, Ready for Implementation

**Next Action**: Review with stakeholders, then begin Phase 1 implementation
