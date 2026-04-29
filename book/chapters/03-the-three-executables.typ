= The Three Executables: utlx, utlxd, and utlxe

== One Language, Three Runtimes
// - UTL-X is one transformation language
// - Three different executables serve three different purposes
// - Same parser, same UDM, same stdlib — different deployment models
// - Choose based on your use case: development, IDE integration, or production

== utlx — The CLI (Command Line Interface)
// - Purpose: developer tool for writing, testing, and running transformations
// - When to use: development, scripting, CI/CD pipelines, one-off transformations
// - Runs on: developer laptop, build server, shell scripts
// - Distribution: native binary (GraalVM) or JVM JAR
// - Modes:
//   - Transform: utlx transform script.utlx input.xml
//   - Expression: echo '{"name":"Alice"}' | utlx -e '.name' -r
//   - Identity (flip): cat data.xml | utlx (auto-converts to JSON)
//   - REPL: utlx repl (interactive mode)
//   - Validate: utlx validate script.utlx
//   - Functions: utlx functions --search date
// - Key characteristics:
//   - Single invocation per transformation (no persistent process)
//   - Reads from stdin or files, writes to stdout or files
//   - GraalVM native: <10ms startup, 40MB memory
//   - JVM JAR: ~250ms startup, 150MB memory
//   - Best for: shell pipelines, CI/CD, ad-hoc data conversion

== utlxd — The Daemon (IDE Integration)
// - Purpose: long-running process that serves the VS Code extension and IDE features
// - When to use: during development, when using the VS Code extension
// - Runs on: developer laptop (background process)
// - Distribution: JVM JAR only (not native — needs full reflection for LSP)
// - Protocol: Language Server Protocol (LSP) over stdio
// - Features:
//   - Syntax highlighting and diagnostics (real-time error detection)
//   - Autocompletion (function names, property paths)
//   - Hover information (function signatures, type info)
//   - Go-to-definition
//   - Live preview (transform as you type)
//   - Format document
//   - Code actions (quick fixes)
// - Key characteristics:
//   - Long-running process (starts with VS Code, stops when VS Code closes)
//   - Communicates via LSP with the VS Code extension
//   - Shares the same parser, interpreter, and stdlib as utlx
//   - Not used in production — development only
//   - JVM-only: needs full Kotlin reflection for language features

== utlxe — The Engine (Production Runtime)
// - Purpose: production transformation engine for cloud deployment
// - When to use: Azure Container Apps, GCP Cloud Run, AWS Fargate, Kubernetes
// - Runs on: containers in customer's cloud subscription
// - Distribution: Docker image (ghcr.io/utlx-lang/utlxe)
// - Transport modes:
//   - HTTP REST API (--mode http, port 8085)
//   - gRPC (--mode grpc)
//   - stdio-proto (--mode stdio-proto, for wrapper integration)
//   - stdio-json (--mode stdio-json, backward compatible)
// - Key features:
//   - Multi-threaded: 8-128 concurrent workers
//   - Execution strategies: TEMPLATE, COPY, COMPILED, AUTO
//   - Hot reload: load/update/unload transformations via API without restart
//   - Schema validation: pre and post validation orchestrator
//   - Pipeline chaining: multi-step in-process transformation
//   - Back-pressure: ArrayBlockingQueue + CallerRunsPolicy
//   - Health probes: /health/live, /health/ready (port 8081)
//   - Prometheus metrics: request count, latency, error rate
//   - Dapr integration: Service Bus, Event Hub, Kafka bindings
// - Key characteristics:
//   - Long-running process (starts with container, runs indefinitely)
//   - Designed for throughput: 86K+ msg/s per instance
//   - Auto-scales via KEDA (Azure) or native scaling (GCP Cloud Run)
//   - Stateless: can run behind a load balancer without sticky sessions
//   - Azure Marketplace: Starter ($35/month), Professional ($105/month)

== Comparison Table

// | Aspect | utlx (CLI) | utlxd (Daemon) | utlxe (Engine) |
// |--------|-----------|----------------|----------------|
// | Purpose | Development | IDE integration | Production |
// | Lifecycle | Single invocation | Long-running | Long-running |
// | Runtime | Native or JVM | JVM only | JVM (Docker) |
// | Startup | <10ms (native) | ~1s | ~3s |
// | Workers | 1 (single-threaded) | 1 | 8-128 |
// | Protocol | stdin/stdout/files | LSP over stdio | HTTP / gRPC / proto |
// | Strategies | TEMPLATE only | TEMPLATE only | TEMPLATE, COPY, COMPILED |
// | Hot reload | No (single run) | Yes (LSP) | Yes (HTTP API) |
// | Validation | Basic | Real-time diagnostics | Full orchestrator |
// | Metrics | None | None | Prometheus |
// | Distribution | brew, choco, binary | VS Code extension | Docker, Marketplace |
// | Branch | main | development | development |

== Architecture: Shared Core, Different Shells

// All three executables share:
// ┌─────────────────────────────────────────────┐
// │              Shared Core                     │
// │  ├── Parser (ANTLR grammar → AST)           │
// │  ├── Interpreter (AST → RuntimeValue)        │
// │  ├── UDM (Universal Data Model)              │
// │  ├── Format parsers (XML, JSON, CSV, YAML)   │
// │  ├── Format serializers (XML, JSON, CSV, YAML)│
// │  ├── Standard library (652 functions)         │
// │  └── Schema validators (7 formats)            │
// └─────────────────────────────────────────────┘
//           │              │              │
//     ┌─────┴─────┐  ┌────┴────┐  ┌─────┴──────┐
//     │   utlx    │  │  utlxd  │  │   utlxe    │
//     │   CLI     │  │  Daemon │  │   Engine   │
//     │ stdin/out │  │  LSP    │  │  HTTP/gRPC │
//     │ native OK │  │  JVM    │  │  Docker    │
//     └───────────┘  └─────────┘  └────────────┘
//
// A bug fix in the shared core (e.g., B13, B14) fixes all three.
// A new stdlib function is available in all three immediately.

== When to Use Which

// | Scenario | Use |
// |----------|-----|
// | Writing a transformation | utlxd (via VS Code) + utlx (to test) |
// | One-off data conversion | utlx -e or identity mode |
// | Shell script pipeline | utlx (native binary) |
// | CI/CD validation | utlx validate + utlx transform |
// | Production API | utlxe --mode http |
// | Azure Service Bus integration | utlxe + Dapr sidecar |
// | C# / Go application embedding | utlxe --mode stdio-proto (via wrapper) |
// | Performance testing | utlx (single) or utlxe (throughput) |

== The Wrapper Pattern
// - C# wrapper: spawns utlxe as subprocess, communicates via stdio-proto
// - Go wrapper: same pattern, different language
// - Why: embed UTL-X in .NET / Go applications without JVM dependency in the host
// - The wrapper handles: process lifecycle, varint framing, correlation IDs, multiplexing
// - Used for: Azure Functions, Go services, .NET applications
