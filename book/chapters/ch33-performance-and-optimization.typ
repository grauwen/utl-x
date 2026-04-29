= Performance and Optimization

== Execution Strategies
// - TEMPLATE (INTERPRETED): interprets AST — simplest, good for dev
// - COPY: pre-built DOM from schema, auto-compiles — Tibco BW style
// - COMPILED: AST → JVM bytecode via ASM — maximum throughput
// - AUTO: schema → COPY, no schema → TEMPLATE
// - When to use which strategy

== Performance Characteristics
// - 86K+ msg/s (COMPILED, 8 workers, 1 vCPU)
// - Throughput scales linearly with workers
// - Memory: 10-50x message expansion in UDM (XML/JSON parsing)
// - Memory sizing: 100KB XML ≈ 5-10MB heap

== Memory Optimization
// - JVM heap sizing: 75% of container memory
// - G1GC configuration for containerized environments
// - Message size vs worker count trade-off
// - When to increase memory vs add instances

== GraalVM Native Image
// - CLI: <10ms startup, 40MB memory
// - Engine: not yet native (deferred — reflection challenges)
// - When native matters: scale-to-zero, serverless, CLI distribution

== Benchmarking
// - Built-in: conformance suite throughput tests
// - External: wrk, hey, k6 for HTTP endpoint
// - Measuring: latency (p50, p95, p99), throughput, memory

== Production Tuning
// - Worker count: match to CPU cores (8 per vCPU recommended)
// - Instance scaling: horizontal (more instances) vs vertical (more workers)
// - Back-pressure: ArrayBlockingQueue + CallerRunsPolicy
// - Connection pooling for downstream HTTP calls
