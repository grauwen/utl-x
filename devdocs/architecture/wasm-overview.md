# WebAssembly (WASM) Overview

## What is WebAssembly?

WebAssembly is a binary instruction format for a stack-based virtual machine. Code is compiled from higher-level languages (Rust, C++, Go, Kotlin, etc.) into compact WASM bytecode, which is then executed by a WASM runtime using JIT or AOT compilation.

**Key properties:**
- **Portable** -- runs identically across platforms and architectures without recompilation
- **Sandboxed** -- isolated execution with restricted memory and capabilities
- **Near-native speed** -- AOT-compiled WASM achieves 80-95% of native performance
- **Small footprint** -- binaries typically 1-5 MB (vs 30-200 MB for containers)
- **Fast startup** -- cold-start times of 20-100ms (vs 300-1000ms for containers)

## Standards and Specifications

| Version | Date | Key Features |
|---|---|---|
| WASM 1.0 | 2017 | Original W3C MVP specification |
| WASM 2.0 | March 2025 | 236 vector instructions (128-bit SIMD), reference types, non-trapping conversions |
| WASM 3.0 | September 2025 | GC, threads, Memory64, exception handling, relaxed SIMD |

### Key Proposals (all shipped in WASM 3.0)

| Proposal | What it enables |
|---|---|
| **GC (Garbage Collection)** | Native runtime-managed memory for high-level languages (Java, Kotlin, Dart, C#) |
| **Threads** | SharedArrayBuffer and atomic operations for parallel computation |
| **Memory64** | i64 addressing, expanding memory limit from 4GB to 16 exabytes |
| **Exception Handling** | Native try-catch mechanisms |
| **SIMD** | 128-bit vector operations for data-parallel workloads |
| **Tail Call Optimization** | Efficient recursive algorithms |

## WASI (WebAssembly System Interface)

WASI is a standardized system interface that enables WASM modules to interact with OS resources (files, sockets, environment variables) in a portable, capability-safe manner. Same binary runs on Linux, Windows, macOS, and IoT devices.

| Version | Date | Status |
|---|---|---|
| WASI Preview 1 (0.1) | 2019 | Stable, basic system capabilities |
| WASI Preview 2 (0.2) | January 2024 | Stable. Component Model, async I/O, resource management |
| WASI Preview 3 (0.3) | February 2026 (expected) | Native async/await, thread support |
| WASI 1.0 | Late 2026 / early 2027 (target) | Formal standardization milestone |

**Component Model**: A specification for composing WASM modules into larger applications with language-neutral, type-safe interfaces.

## Browser Support (2026)

Core WebAssembly is supported universally in modern browsers.

| Feature | Chrome | Firefox | Safari |
|---|---|---|---|
| Core WASM | All modern | All modern | All modern |
| WasmGC | 119+ | 120+ | 18.2+ |
| Threads | Supported | Supported | Limited |
| SIMD | Baseline | Baseline | Baseline |
| Memory64 | Supported | Supported | Supported |
| Exception Handling | Baseline | Baseline | Baseline |

WasmGC achieved baseline support across all major browsers by December 2024, enabling garbage-collected languages (Kotlin, Java, Dart) to run efficiently in browsers.

## Runtimes Beyond the Browser

| Runtime | Focus | Notes |
|---|---|---|
| **Wasmtime** | Reference implementation | Bytecode Alliance. AOT via Cranelift. Embeddable in Rust, Python, C |
| **Wasmer** | Meta-runtime | Pluggable backends (LLVM, Cranelift, V8). Desktop, cloud, edge, IoT |
| **WasmEdge** | Edge computing | CNCF project. Lightweight, high-performance. IoT, edge, serverless |
| **WAMR** | Embedded systems | Minimal footprint. Microcontrollers and constrained environments |
| **Node.js** | Server-side JS | Native WASM module support via V8 |
| **Deno** | Server-side JS/TS | Native WASM support |

## Use Cases

### Server-Side and Cloud
- Microservices and backend APIs via WASI
- Serverless functions (Cloudflare Workers, Fastly Compute, Fermyon Cloud)
- Plugin systems and extensible architectures

### Edge Computing
- Sub-10ms cold-start functions at CDN edge nodes
- Request/response transformation, A/B testing, dynamic routing
- Distributed compute closer to users

### Plugins and Extensions
- Sandboxed third-party code execution (Figma plugins, Envoy proxy filters)
- Safer than native code loading with strong isolation boundaries
- Language-agnostic plugin development

### Blockchain and Smart Contracts
- Runtime for Radix, Near, EOSIO platforms
- Multi-language smart contract development (Rust, C++, Kotlin)

### AI/ML Inference
- In-browser and on-device model inference (TensorFlow.js, WebLLM, llama.cpp)
- Edge inference for real-time applications
- Privacy-preserving inference without server transmission
- GPU acceleration via WebGPU

### Data Processing
- DuckDB-WASM for in-browser SQL queries
- Real-time data transformation and aggregation
- Database UDFs (SingleStore Code Engine)

### Gaming
- Complex game logic, physics engines, AI systems in browsers
- Near-native performance for compute-heavy game code

## Who Uses WASM in Production?

| Company/Project | How |
|---|---|
| **Figma** | Plugin sandbox execution in desktop app |
| **Cloudflare Workers** | Millions of WASM functions globally, sub-10ms cold starts |
| **Fastly Compute** | Edge CDN with custom request/response logic |
| **Docker** | Docker Desktop integrates WASM alongside containers |
| **Shopify Functions** | Custom backend logic in any WASM-compiled language |
| **Envoy Proxy** | WASM extensions for proxy filters (used in Istio service mesh) |
| **SingleStore** | WASM-powered UDFs and table-valued functions |
| **DuckDB** | In-browser SQL query execution |
| **Google** | Meet (background blur), YouTube (AR), Photos (editing), all via WASM ML |
| **Adobe** | Photoshop for Web |
| **AutoCAD** | Web version |
| **American Express** | Production deployment |

## Language Support

| Language | Maturity | Notes |
|---|---|---|
| **Rust** | Mature (best-in-class) | Native WASM support, smallest binaries, preferred for production |
| **C/C++** | Mature | Via Emscripten, vast legacy codebase support |
| **Go** | Mature | Official support since Go 1.11 (2018) |
| **C#/.NET** | Maturing | Blazor framework, IDE debugging support |
| **Kotlin** | Maturing | Kotlin/Wasm target, benefits from WasmGC |
| **AssemblyScript** | Mature | TypeScript-like syntax purpose-built for WASM |
| **Zig** | Emerging | Direct WASM compilation, systems programming |
| **Java** | Emerging | Enabled by WasmGC |
| **Dart** | Emerging | WasmGC support |
| **Swift** | Emerging | Experimental WASM target |
| **OCaml, Scala** | Emerging | Enabled by WasmGC |

## Adoption and Popularity (2025-2026)

### Current Numbers
- 0.35% of desktop websites, 0.28% of mobile websites (~43,000 sites)
- 4.5% of Chrome-visited web applications (up from 0.04% in 2021)
- 41% of developers using WASM in production
- 28% piloting or planning adoption

### Growth Trajectory
- Year-over-year increase from 4.5% to 5.5% in 2025
- Industry projections: 50% adoption by 2030
- Growth is primarily "behind-the-scenes" -- utilities within larger applications

### Developer Benefits (survey data)
- 47% cite faster code execution
- 46% cite cross-platform compatibility
- 45% cite improved security

### Top Use Cases (survey data)
- 71% web development
- 32% plugin environments
- 24% backend services

## Limitations and Challenges

### Binary Size
- Without optimization, modules can be 100-500 KB
- Debug information can constitute 66% of binary size
- Mitigations: strip debug info, use `wasm-opt`, lazy loading

### GC Overhead (for GC languages)
- WasmGC is more efficient than bundled JS GC, but still carries runtime cost
- Unpredictable pause times in latency-sensitive applications

### Debugging
- Stack traces are cryptic, hard to map to source code
- Memory leak detection is challenging
- Improving: IDE debugging for .NET, source maps

### Limited I/O
- No direct DOM access (requires JavaScript bridge)
- Filesystem I/O abstracted through WASI
- Network I/O depends on bridge quality

### Ecosystem Maturity
- Library ecosystem significantly smaller than JavaScript or native languages
- Dependency management tools still evolving
- Finding frameworks with full WASM support requires effort

## WASM vs. Alternatives

### WASM vs. Containers

| Aspect | WASM | Containers |
|---|---|---|
| Binary size | 1-5 MB | 30-200 MB |
| Cold start | 20-100 ms | 300-1000 ms |
| Memory footprint | 10-50 MB | 100-300 MB |
| Steady-state performance | 80-95% of native | Matches native |
| Isolation | Sandboxed, limited syscalls | Full process isolation |
| Flexibility | Constrained by WASI spec | Arbitrary binary execution |
| Best for | Edge, serverless, plugins, sub-second latency | Long-running services, complex I/O |

### WASM vs. Native Binaries

| Aspect | WASM | Native |
|---|---|---|
| Performance | 80-95% of native | 100% (baseline) |
| Portability | Universal | Platform-specific |
| Security | Sandboxed, capability-based | Full system access |
| Distribution | Smallest artifact | Medium to large |

### WASM vs. JavaScript
- WASM excels for compute-heavy work (up to 10x faster for CPU-bound tasks)
- JavaScript better for DOM manipulation and async I/O
- Common pattern: WASM modules called from JavaScript for intensive portions

## Key 2026 Milestones

1. **WASM 3.0** shipping in all major browsers with GC, threads, Memory64, exception handling
2. **WasmGC baseline** achieved, enabling Kotlin, Java, Dart in browsers
3. **WASI 0.3.0** expected February 2026 with native async/await
4. **WASI 1.0** targeted for late 2026 / early 2027
5. **Production scale**: trillions of function invocations annually (Cloudflare, Fastly, Fermyon)
6. **Adoption trajectory**: targeting 50% by 2030
