= Persistence and Scaling

_How transformations survive container restarts, and how to scale UTLXe for throughput._

== The Persistence Problem

// Containers have ephemeral filesystems
// Restart = /utlxe/data/ is wiped
// Three solutions: volume mount, CI/CD re-deploy, ephemeral

== Azure Files Volume Mount (Recommended)

// Azure File Share mounted at /utlxe/data/ by the platform
// Container just sees a directory — standard file I/O
// No mount API, no unix commands, no security concern
// Diagram: container → mount point → Azure File Share
// Enable via createUiDefinition checkbox: "Persistent transformation storage"

== CI/CD Re-Deploy Pattern

// No volume mount — pipeline uploads bundle after each start
// Pipeline detects ready=false, uploads, waits for ready=true
// Bundle lives in git repo or artifact store
// Container is truly stateless

== Startup Sequence

// 1. Javalin on :8081 (health + admin)
// 2. Scan /utlxe/data/ (load if volume-backed)
// 3. Start data plane on :8085
// 4. Readiness probe gates traffic

== Memory Sizing

// Starter (2GB): UTLXE_HEAP_SIZE=1536m — messages up to ~50KB
// Professional (4GB): UTLXE_HEAP_SIZE=3072m — messages up to ~200KB
// Rule of thumb: 75% of container memory to JVM heap
// -XX:+AlwaysPreTouch: fail fast if not enough RAM

== Scaling

// Horizontal: multiple container replicas behind Azure load balancer
// Each replica has its own bundle (volume mount or CI/CD deploy to each)
// Service Bus partitions distribute across replicas
// Event Hub consumer groups distribute partitions across replicas

== Never Use Swap

// Container swap + GC = catastrophic performance
// GC scans swapped pages = 1000x slower
// Azure Container Apps: set memory limit = memory request (no swap)
// Prefer OOM kill over swap thrashing

== Poison Messages

// maxInputSize config rejects oversized messages before parsing
// Default: 5MB — raise only if you know your message sizes
// A 2GB message can OOM the JVM — maxInputSize is the first line of defense
