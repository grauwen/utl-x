# UTL-X Architectural Split: CLI vs Daemon Server

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Architectural Design - Approved for Implementation

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Problem Statement](#problem-statement)
3. [Current State Analysis](#current-state-analysis)
4. [Proposed Architecture](#proposed-architecture)
5. [Gradle Organization](#gradle-organization)
6. [Feature Distribution](#feature-distribution)
7. [Benefits Analysis](#benefits-analysis)
8. [Implementation Plan](#implementation-plan)
9. [Migration Guide](#migration-guide)
10. [Testing Strategy](#testing-strategy)

---

## Executive Summary

**Objective**: Split UTL-X into two separate executables to optimize for different use cases:

- **`utlx`**: Lightweight CLI tool for transforms, validation, CI/CD pipelines
- **`utlxd`**: Daemon server with LSP, REST API, session management for IDE integration

**Timeline**: 2 weeks (4 phases)

**Impact**:
- CLI startup: 1.5s → <100ms (native image)
- CLI size: 50MB → 15MB
- Deployment flexibility: Small Docker images, independent scaling
- Clear separation of concerns

---

## Problem Statement

### Current Issues

1. **CLI Bloat**: `utlx` CLI includes all daemon dependencies even for simple transforms
   ```kotlin
   // modules/cli/build.gradle.kts:15
   implementation(project(":modules:daemon"))  // ← Problem: coupling
   ```

2. **Slow Startup**: CLI startup is ~1.5s because of daemon overhead
   - Users running `utlx transform` in CI/CD wait unnecessarily
   - GraalVM native image could optimize to <100ms for CLI-only

3. **Large Footprint**: Single JAR is ~50MB
   - Docker images are larger than needed
   - CI/CD environments download more than necessary

4. **Mixed Concerns**: `DesignCommand` in CLI uses daemon features
   ```kotlin
   // modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt:42
   "design", "d" -> DesignCommand.execute(commandArgs)  // Uses daemon!
   ```

5. **Deployment Inflexibility**:
   - CI/CD only needs transform/validate → shouldn't bundle LSP server
   - IDE integration needs daemon → doesn't need CLI commands
   - Can't scale independently

### Vision

**Separate executables optimized for different use cases**:

```
┌────────────────────────────────────────────────────┐
│              Use Case: CI/CD Pipeline              │
│  utlx transform input.json -o output.json          │
│  ├─ Fast startup: <100ms (native image)            │
│  ├─ Small size: 15MB                               │
│  └─ No daemon overhead                             │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│         Use Case: Theia IDE Integration            │
│  utlxd start --lsp --rest-api --port 7778          │
│  ├─ LSP server for Monaco editor                   │
│  ├─ REST API for MCP server                        │
│  ├─ Session management                             │
│  └─ Design-time features                           │
└────────────────────────────────────────────────────┘
```

---

## Current State Analysis

### Module Structure

```
modules/
├── cli/              ← CLI entry point (depends on daemon)
├── daemon/           ← LSP server implementation
├── core/             ← Shared parser, evaluator
├── analysis/         ← Shared type inference
└── ...
```

### Dependency Graph

```
modules/cli/
  └─→ modules/daemon/  ✗ Problem: CLI doesn't need LSP!
      └─→ modules/core/
      └─→ modules/analysis/

modules/daemon/
  └─→ modules/core/
  └─→ modules/analysis/
```

### CLI Dependencies (build.gradle.kts)

```kotlin
dependencies {
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))
    implementation(project(":modules:daemon"))        // ← COUPLING
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":stdlib"))

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")

    // JLine for REPL
    implementation("org.jline:jline:3.24.1")
}
```

**Problem**: Including `:modules:daemon` brings in:
- LSP server code (unused in CLI)
- JSON-RPC libraries (unused in CLI)
- Session management (unused in CLI)

---

## Proposed Architecture

### New Module Structure

```
modules/
├── cli/              ← Refactored: NO daemon dependency
│   └─→ core, analysis, formats, stdlib
│
├── server/           ← NEW: Daemon with REST API
│   └─→ daemon, core, analysis, formats, stdlib
│
├── daemon/           ← Existing: LSP implementation only
│   └─→ core, analysis
│
├── core/             ← Shared: parser, evaluator
├── analysis/         ← Shared: type inference
├── formats/          ← Shared: XML, JSON, CSV, YAML
└── stdlib/           ← Shared: standard library
```

### New Dependency Graph

```
modules/cli/
  └─→ modules/core/
  └─→ modules/analysis/
  └─→ formats/*
  └─→ stdlib

modules/server/
  └─→ modules/daemon/
  └─→ modules/core/
  └─→ modules/analysis/
  └─→ formats/*
  └─→ stdlib

modules/daemon/
  └─→ modules/core/
  └─→ modules/analysis/
```

**Key Change**: CLI no longer depends on daemon module!

---

## Gradle Organization

### settings.gradle.kts

**Add new module**:

```kotlin
// Current modules
include("modules:core")
include("modules:cli")
include("modules:daemon")
include("modules:analysis")

// NEW: Server module
include("modules:server")  // ← Add this
```

### modules/cli/build.gradle.kts (Refactored)

**Remove daemon dependency**:

```kotlin
dependencies {
    // Core dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))
    // REMOVED: implementation(project(":modules:daemon"))

    // Format parsers
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))

    // Standard library
    implementation(project(":stdlib"))

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

    // JLine for REPL
    implementation("org.jline:jline:3.24.1")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

// JAR configuration for lightweight CLI
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.apache.utlx.cli.Main"
    }
    archiveBaseName.set("utlx-cli")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// GraalVM native image for fast startup
tasks.register("nativeImage") {
    dependsOn("jar")
    doLast {
        exec {
            commandLine(
                "native-image",
                "-jar", tasks.jar.get().archiveFile.get().asFile.absolutePath,
                "-o", "utlx",
                "--no-fallback",
                "--report-unsupported-elements-at-runtime"
            )
        }
    }
}
```

### modules/server/build.gradle.kts (NEW)

**Create new server module with daemon + REST API**:

```kotlin
plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("org.apache.utlx.server.Main")
}

dependencies {
    // Daemon module (LSP server)
    implementation(project(":modules:daemon"))

    // Core dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:analysis"))

    // Format parsers
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))

    // Standard library
    implementation(project(":stdlib"))

    // Ktor for REST API
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-jackson:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-call-logging:2.3.7")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
}

// JAR configuration for daemon server
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.apache.utlx.server.Main"
    }
    archiveBaseName.set("utlxd")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
```

### Root build.gradle.kts

**Add convenience tasks**:

```kotlin
tasks.register("buildCli") {
    dependsOn(":modules:cli:jar")
    doLast {
        println("Built utlx CLI: modules/cli/build/libs/utlx-cli.jar")
    }
}

tasks.register("buildDaemon") {
    dependsOn(":modules:server:jar")
    doLast {
        println("Built utlxd daemon: modules/server/build/libs/utlxd.jar")
    }
}

tasks.register("buildAll") {
    dependsOn("buildCli", "buildDaemon")
}

tasks.register("nativeCliImage") {
    dependsOn(":modules:cli:nativeImage")
    doLast {
        println("Built native utlx: modules/cli/utlx")
    }
}
```

---

## Feature Distribution

### utlx CLI Features

**Commands**:
- ✅ `transform` - Execute UTLX transformations
- ✅ `validate` - Validate UTLX syntax and types
- ✅ `lint` - Lint UTLX code for best practices
- ✅ `functions` - List available stdlib functions
- ✅ `repl` - Interactive UTLX shell
- ❌ `design` - **REMOVED** (moved to utlxd)

**Use Cases**:
- CI/CD pipelines
- Command-line transformations
- Batch processing
- Scripting and automation
- Quick validation

**Example Usage**:
```bash
# Transform data
utlx transform input.json -t transform.utlx -o output.json

# Validate UTLX
utlx validate transform.utlx

# Interactive REPL
utlx repl

# List functions
utlx functions --category date
```

### utlxd Daemon Features

**Servers**:
- ✅ **LSP Server** - Language Server Protocol for IDE integration
- ✅ **REST API Server** - HTTP/JSON API for MCP and tools
- ✅ **Session Management** - State tracking for long-running connections

**Commands**:
- ✅ `start` - Start daemon with LSP and/or REST API
- ✅ `stop` - Stop running daemon
- ✅ `status` - Check daemon status
- ✅ `design` - Design-time commands (graph, analyze, etc.)

**Use Cases**:
- Theia IDE integration
- MCP server integration
- Language server for editors
- Design-time analysis
- Schema inference and validation

**Example Usage**:
```bash
# Start daemon with both LSP and REST API
utlxd start --lsp --rest-api --port 7778

# Start daemon with LSP only (STDIO transport)
utlxd start --lsp --transport stdio

# Start daemon with REST API only
utlxd start --rest-api --port 7778

# Design-time graph visualization
utlxd design graph transform.utlx -o ast.dot

# Check daemon status
utlxd status
```

---

## Benefits Analysis

### 1. Performance Improvements

#### CLI Startup Time

**Before** (with daemon dependency):
```bash
time ./utlx transform input.json
# Real: 1.5s (JVM warmup + class loading)
```

**After** (CLI only, native image):
```bash
time ./utlx transform input.json
# Real: <100ms (native binary, no JVM)
```

**Improvement**: **15x faster startup**

#### Binary Size

**Before**:
```
utlx.jar: 50MB (includes LSP, REST API, session management)
```

**After**:
```
utlx-cli.jar:  15MB (transform, validate, lint only)
utlxd.jar:     50MB (LSP, REST API, full daemon)
utlx (native): 25MB (native image, <100ms startup)
```

**Improvement**: **3x smaller CLI binary**

### 2. Deployment Flexibility

#### Docker Images

**Before**:
```dockerfile
FROM openjdk:17-alpine
COPY utlx.jar /app/
# Image size: 200MB (JDK + 50MB JAR)
```

**After** (CLI-only image):
```dockerfile
FROM alpine:3.18
COPY utlx /app/
# Image size: 35MB (native binary, no JDK!)
```

**Improvement**: **6x smaller Docker image for CI/CD**

#### Independent Scaling

```
CI/CD Pipeline (stateless):
  └─→ Deploy utlx CLI only (15MB, fast startup)
  └─→ Scale horizontally (1000s of instances)
  └─→ No daemon overhead

IDE Integration (stateful):
  └─→ Deploy utlxd daemon (50MB, session management)
  └─→ One instance per developer/workspace
  └─→ Long-running, maintains state
```

### 3. Separation of Concerns

**Before**:
```
utlx
  ├─ CLI commands (transform, validate, lint)
  ├─ REPL
  ├─ Design commands (graph, analyze)
  ├─ LSP server
  ├─ REST API server
  └─ Session management

Problem: Mixed concerns, hard to maintain
```

**After**:
```
utlx (CLI)
  ├─ CLI commands (transform, validate, lint)
  └─ REPL

utlxd (Daemon)
  ├─ LSP server
  ├─ REST API server
  ├─ Session management
  └─ Design commands (graph, analyze)

Benefit: Clear separation, easier to maintain
```

### 4. Development Workflow

**Testing**:
```bash
# Test CLI changes (fast rebuild, no daemon code)
./gradlew :modules:cli:test

# Test daemon changes (no CLI code affected)
./gradlew :modules:server:test

# Test both
./gradlew buildAll
```

**CI/CD**:
```yaml
# GitHub Actions - separate jobs
jobs:
  test-cli:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew :modules:cli:test

  test-daemon:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew :modules:server:test
```

---

## Implementation Plan

### Phase 1: Create Server Module (Week 1, Days 1-3)

**Tasks**:

1. **Create module structure** (1 hour)
   ```bash
   mkdir -p modules/server/src/main/kotlin/org/apache/utlx/server
   mkdir -p modules/server/src/test/kotlin/org/apache/utlx/server
   ```

2. **Create build.gradle.kts** (30 minutes)
   - Add dependencies (daemon, core, analysis, formats, stdlib, Ktor)
   - Configure JAR task with Main-Class manifest
   - Set archiveBaseName to "utlxd"

3. **Create Main.kt entry point** (2 hours)
   ```kotlin
   // modules/server/src/main/kotlin/org/apache/utlx/server/Main.kt
   package org.apache.utlx.server

   import org.apache.utlx.daemon.DaemonServer
   import org.apache.utlx.server.rest.RestApiServer

   fun main(args: Array<String>) {
       val command = args.getOrNull(0) ?: "help"

       when (command) {
           "start" -> StartCommand.execute(args.drop(1))
           "stop" -> StopCommand.execute(args.drop(1))
           "status" -> StatusCommand.execute(args.drop(1))
           "design" -> DesignCommand.execute(args.drop(1))
           else -> HelpCommand.execute()
       }
   }
   ```

4. **Implement StartCommand** (4 hours)
   ```kotlin
   // modules/server/src/main/kotlin/org/apache/utlx/server/commands/StartCommand.kt
   object StartCommand {
       fun execute(args: List<String>): CommandResult {
           val options = parseOptions(args)

           val daemon = UTLXDaemon(
               lspTransportType = options.lspTransport,
               lspPort = options.lspPort,
               enableRestApi = options.enableRestApi,
               restApiPort = options.restApiPort
           )

           daemon.start()

           println("UTL-X Daemon started")
           if (options.enableLsp) {
               println("  LSP: ${options.lspTransport} (port ${options.lspPort})")
           }
           if (options.enableRestApi) {
               println("  REST API: http://localhost:${options.restApiPort}")
           }

           // Block until shutdown signal
           Runtime.getRuntime().addShutdownHook(Thread { daemon.stop() })
           Thread.currentThread().join()

           return CommandResult.Success
       }
   }
   ```

5. **Move DesignCommand from CLI to server** (2 hours)
   - Copy `modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/DesignCommand.kt`
   - Update package to `org.apache.utlx.server.commands`
   - Update references

6. **Update settings.gradle.kts** (5 minutes)
   ```kotlin
   include("modules:server")
   ```

7. **Build and test** (1 hour)
   ```bash
   ./gradlew :modules:server:build
   java -jar modules/server/build/libs/utlxd.jar start --help
   ```

**Deliverable**: Working `utlxd` daemon with start/stop/status/design commands

---

### Phase 2: Refactor CLI Module (Week 1, Days 4-5)

**Tasks**:

1. **Remove daemon dependency** (15 minutes)
   ```kotlin
   // modules/cli/build.gradle.kts
   dependencies {
       // REMOVE: implementation(project(":modules:daemon"))
   }
   ```

2. **Update Main.kt** (30 minutes)
   ```kotlin
   // modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt
   when (command.lowercase()) {
       "transform", "t" -> TransformCommand.execute(commandArgs)
       "validate", "v" -> ValidateCommand.execute(commandArgs)
       "lint", "l" -> LintCommand.execute(commandArgs)
       "functions", "fn" -> FunctionsCommand.execute(commandArgs)
       "repl", "r" -> ReplCommand.execute(commandArgs)

       // CHANGE: Redirect design command to utlxd
       "design", "d" -> {
           println("Design-time features are only available in utlxd")
           println("Run: utlxd start --help")
           CommandResult.Failure("Use utlxd for design-time features", 1)
       }

       else -> HelpCommand.execute()
   }
   ```

3. **Delete DesignCommand.kt from CLI** (5 minutes)
   ```bash
   rm modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/DesignCommand.kt
   ```

4. **Update HelpCommand** (15 minutes)
   - Remove design command from help text
   - Add note about utlxd for design features

5. **Build and verify** (30 minutes)
   ```bash
   ./gradlew :modules:cli:clean :modules:cli:build

   # Verify no daemon dependencies in classpath
   jar tf modules/cli/build/libs/utlx-cli.jar | grep daemon
   # Should return empty
   ```

6. **Measure improvements** (15 minutes)
   ```bash
   # Check JAR size
   ls -lh modules/cli/build/libs/utlx-cli.jar
   # Expected: ~15MB (was ~50MB)

   # Test startup time
   time java -jar modules/cli/build/libs/utlx-cli.jar transform --help
   # Expected: <1s (was ~1.5s)
   ```

**Deliverable**: Lightweight `utlx` CLI without daemon dependency

---

### Phase 3: Build Configuration (Week 2, Days 1-2)

**Tasks**:

1. **Add root build tasks** (30 minutes)
   ```kotlin
   // build.gradle.kts (root)
   tasks.register("buildCli") { ... }
   tasks.register("buildDaemon") { ... }
   tasks.register("buildAll") { ... }
   ```

2. **Configure native image for CLI** (2 hours)
   ```kotlin
   // modules/cli/build.gradle.kts
   tasks.register("nativeImage") {
       dependsOn("jar")
       doLast {
           exec {
               commandLine("native-image", "-jar", ...)
           }
       }
   }
   ```

3. **Test native image build** (1 hour)
   ```bash
   ./gradlew :modules:cli:nativeImage
   ./modules/cli/utlx transform --help
   time ./modules/cli/utlx transform input.json
   # Expected: <100ms
   ```

4. **Create distribution scripts** (2 hours)
   ```bash
   # scripts/build-distributions.sh
   #!/bin/bash

   # Build CLI (JAR + native)
   ./gradlew :modules:cli:jar
   ./gradlew :modules:cli:nativeImage

   # Build daemon (JAR only)
   ./gradlew :modules:server:jar

   # Package distributions
   mkdir -p dist/utlx-cli
   mkdir -p dist/utlxd

   cp modules/cli/build/libs/utlx-cli.jar dist/utlx-cli/
   cp modules/cli/utlx dist/utlx-cli/
   cp modules/server/build/libs/utlxd.jar dist/utlxd/

   # Create launch scripts
   cat > dist/utlx-cli/utlx.sh << 'EOF'
   #!/bin/bash
   DIR="$(cd "$(dirname "$0")" && pwd)"
   exec "$DIR/utlx" "$@"
   EOF

   cat > dist/utlxd/utlxd.sh << 'EOF'
   #!/bin/bash
   DIR="$(cd "$(dirname "$0")" && pwd)"
   exec java -jar "$DIR/utlxd.jar" "$@"
   EOF

   chmod +x dist/utlx-cli/utlx.sh
   chmod +x dist/utlxd/utlxd.sh
   ```

5. **Create Docker images** (3 hours)
   ```dockerfile
   # docker/utlx-cli.Dockerfile
   FROM alpine:3.18
   COPY modules/cli/utlx /usr/local/bin/utlx
   RUN chmod +x /usr/local/bin/utlx
   ENTRYPOINT ["/usr/local/bin/utlx"]

   # docker/utlxd.Dockerfile
   FROM eclipse-temurin:17-jre-alpine
   COPY modules/server/build/libs/utlxd.jar /app/utlxd.jar
   EXPOSE 7777 7778
   ENTRYPOINT ["java", "-jar", "/app/utlxd.jar"]
   ```

6. **Test distributions** (2 hours)
   ```bash
   # Test CLI distribution
   dist/utlx-cli/utlx.sh transform input.json

   # Test daemon distribution
   dist/utlxd/utlxd.sh start --rest-api --port 7778 &
   curl http://localhost:7778/api/stdlib

   # Test Docker images
   docker build -f docker/utlx-cli.Dockerfile -t utlx-cli .
   docker run --rm utlx-cli transform --help

   docker build -f docker/utlxd.Dockerfile -t utlxd .
   docker run -p 7778:7778 --rm utlxd start --rest-api
   ```

**Deliverable**: Production-ready build and distribution system

---

### Phase 4: Documentation and Testing (Week 2, Days 3-5)

**Tasks**:

1. **Create README for CLI** (1 hour)
   ```markdown
   # modules/cli/README.md

   # UTL-X CLI

   Lightweight command-line tool for UTL-X transformations.

   ## Installation

   ### Native Binary (Recommended)
   ```bash
   curl -LO https://github.com/apache/utlx/releases/latest/download/utlx
   chmod +x utlx
   sudo mv utlx /usr/local/bin/
   ```

   ### JAR (Requires JDK 17+)
   ```bash
   java -jar utlx-cli.jar transform input.json
   ```

   ## Commands

   - `transform` - Execute transformations
   - `validate` - Validate UTLX syntax
   - `lint` - Lint UTLX code
   - `functions` - List stdlib functions
   - `repl` - Interactive shell

   ## Examples

   ... (see full README)
   ```

2. **Create README for daemon** (1 hour)
   ```markdown
   # modules/server/README.md

   # UTL-X Daemon (utlxd)

   Language server and REST API for UTL-X IDE integration.

   ## Features

   - LSP server (Language Server Protocol)
   - REST API (HTTP/JSON)
   - Session management
   - Design-time analysis

   ## Quick Start

   ... (see full README)
   ```

3. **Update main documentation** (2 hours)
   - Update `/README.md` with new architecture
   - Update `/docs/architecture/README-MCP.md` references
   - Create migration guide

4. **Write migration guide** (2 hours)
   ```markdown
   # docs/migration/cli-daemon-split-migration.md

   # Migration Guide: Single utlx → Split Architecture

   ## For Users

   ### Before
   ```bash
   utlx transform input.json
   utlx design daemon --port 7778
   ```

   ### After
   ```bash
   utlx transform input.json       # CLI (same)
   utlxd start --rest-api --port 7778  # Daemon (new)
   ```

   ## For CI/CD Pipelines

   ... (see full migration guide)
   ```

5. **Integration testing** (4 hours)
   ```kotlin
   // modules/server/src/test/kotlin/org/apache/utlx/server/IntegrationTest.kt

   @Test
   fun `daemon starts with both LSP and REST API`() {
       val process = ProcessBuilder("java", "-jar", "utlxd.jar", "start", "--lsp", "--rest-api")
           .start()

       // Wait for startup
       Thread.sleep(2000)

       // Test REST API
       val response = URL("http://localhost:7778/api/stdlib").readText()
       assertContains(response, "functions")

       // Test LSP (connect via socket)
       val socket = Socket("localhost", 7777)
       assertTrue(socket.isConnected)

       process.destroy()
   }
   ```

6. **End-to-end testing** (4 hours)
   ```bash
   # test/e2e/cli-daemon-split.sh

   # Test CLI
   ./utlx transform test/data/input.json -t test/data/transform.utlx

   # Test daemon
   ./utlxd start --rest-api --port 7778 &
   DAEMON_PID=$!

   # Test REST API
   curl http://localhost:7778/api/validate -d '{"transformation": "..."}'

   # Cleanup
   kill $DAEMON_PID
   ```

7. **Performance benchmarking** (2 hours)
   ```bash
   # benchmark/cli-startup.sh

   # Measure CLI startup (10 iterations)
   for i in {1..10}; do
       time ./utlx transform --help
   done | grep real

   # Expected: <100ms per iteration
   ```

**Deliverable**: Complete documentation, migration guide, and test coverage

---

## Migration Guide

### For End Users

#### CLI Commands (No Change)

```bash
# These commands work exactly the same
utlx transform input.json -t transform.utlx -o output.json
utlx validate transform.utlx
utlx lint transform.utlx
utlx functions
utlx repl
```

#### Design Commands (Now in utlxd)

**Before**:
```bash
utlx design daemon --port 7778
utlx design graph transform.utlx -o ast.dot
```

**After**:
```bash
utlxd start --rest-api --port 7778
utlxd design graph transform.utlx -o ast.dot
```

### For CI/CD Pipelines

#### Docker Images

**Before**:
```dockerfile
FROM openjdk:17-alpine
COPY utlx.jar /app/
CMD ["java", "-jar", "/app/utlx.jar", "transform", "input.json"]
```

**After** (Smaller, Faster):
```dockerfile
FROM alpine:3.18
COPY utlx /app/
CMD ["/app/utlx", "transform", "input.json"]
```

**Benefit**: 200MB → 35MB (6x smaller), <100ms startup

#### GitHub Actions

**Before**:
```yaml
- name: Run transformation
  run: java -jar utlx.jar transform input.json
```

**After**:
```yaml
- name: Run transformation
  run: ./utlx transform input.json
```

### For Theia IDE Integration

**Before**:
```bash
java -jar utlx.jar design daemon --lsp --rest-api --port 7778
```

**After**:
```bash
utlxd start --lsp --rest-api --port 7778
```

**Configuration** (no change):
```json
{
  "languageServers": {
    "utlx": {
      "command": "utlxd",
      "args": ["start", "--lsp", "--transport", "stdio"]
    }
  }
}
```

### For MCP Server Integration

**REST API calls remain the same**:

```typescript
// No change required
const response = await axios.post('http://localhost:7778/api/validate', {
  transformation: utlxCode
});
```

---

## Testing Strategy

### Unit Tests

```kotlin
// modules/cli/src/test/kotlin/org/apache/utlx/cli/MainTest.kt

@Test
fun `CLI does not include daemon classes`() {
    // Verify daemon classes are not in classpath
    assertThrows<ClassNotFoundException> {
        Class.forName("org.apache.utlx.daemon.DaemonServer")
    }
}

@Test
fun `design command redirects to utlxd`() {
    val output = captureOutput {
        Main.main(arrayOf("design", "graph", "test.utlx"))
    }
    assertContains(output, "Design-time features are only available in utlxd")
}
```

```kotlin
// modules/server/src/test/kotlin/org/apache/utlx/server/StartCommandTest.kt

@Test
fun `daemon starts with LSP and REST API`() {
    val daemon = UTLXDaemon(
        lspTransportType = TransportType.SOCKET,
        lspPort = 7777,
        enableRestApi = true,
        restApiPort = 7778
    )

    daemon.start()

    // Verify LSP server running
    assertTrue(daemon.isLspRunning())

    // Verify REST API running
    assertTrue(daemon.isRestApiRunning())

    daemon.stop()
}
```

### Integration Tests

```bash
# test/integration/cli-transform.sh

#!/bin/bash
set -e

# Build CLI
./gradlew :modules:cli:jar

# Test transform
java -jar modules/cli/build/libs/utlx-cli.jar transform \
  test/data/input.json \
  -t test/data/transform.utlx \
  -o /tmp/output.json

# Verify output
diff /tmp/output.json test/data/expected-output.json
```

```bash
# test/integration/daemon-rest-api.sh

#!/bin/bash
set -e

# Build daemon
./gradlew :modules:server:jar

# Start daemon
java -jar modules/server/build/libs/utlxd.jar start --rest-api --port 7778 &
DAEMON_PID=$!

# Wait for startup
sleep 2

# Test REST API endpoints
curl http://localhost:7778/api/stdlib
curl -X POST http://localhost:7778/api/validate -d '{"transformation": "..."}'

# Cleanup
kill $DAEMON_PID
```

### Performance Tests

```bash
# benchmark/cli-startup-time.sh

#!/bin/bash

echo "Testing CLI startup time (10 iterations)"

# JAR version
echo "JAR:"
for i in {1..10}; do
  time java -jar modules/cli/build/libs/utlx-cli.jar transform --help 2>&1 > /dev/null
done | grep real

# Native version
echo "Native:"
for i in {1..10}; do
  time modules/cli/utlx transform --help 2>&1 > /dev/null
done | grep real

# Expected:
# JAR:    ~1.0s per iteration
# Native: <100ms per iteration (10x faster)
```

```bash
# benchmark/binary-size.sh

#!/bin/bash

echo "Binary sizes:"
echo "CLI JAR:      $(du -h modules/cli/build/libs/utlx-cli.jar | cut -f1)"
echo "CLI Native:   $(du -h modules/cli/utlx | cut -f1)"
echo "Daemon JAR:   $(du -h modules/server/build/libs/utlxd.jar | cut -f1)"

# Expected:
# CLI JAR:    ~15MB
# CLI Native: ~25MB
# Daemon JAR: ~50MB
```

### End-to-End Tests

```bash
# test/e2e/full-workflow.sh

#!/bin/bash
set -e

echo "=== E2E Test: Full Workflow ==="

# 1. Build both distributions
echo "1. Building distributions..."
./gradlew buildAll

# 2. Test CLI
echo "2. Testing CLI..."
./modules/cli/utlx validate test/data/transform.utlx
./modules/cli/utlx transform test/data/input.json -t test/data/transform.utlx -o /tmp/output.json

# 3. Start daemon
echo "3. Starting daemon..."
java -jar modules/server/build/libs/utlxd.jar start --rest-api --port 7778 &
DAEMON_PID=$!
sleep 2

# 4. Test REST API
echo "4. Testing REST API..."
curl http://localhost:7778/api/stdlib | jq '.functions | length'
curl -X POST http://localhost:7778/api/validate \
  -H "Content-Type: application/json" \
  -d "{\"transformation\": \"$(cat test/data/transform.utlx)\"}" | jq '.valid'

# 5. Cleanup
echo "5. Cleanup..."
kill $DAEMON_PID

echo "=== E2E Test: SUCCESS ==="
```

---

## FAQ

### Q1: Can I still use the old `utlx` JAR?

**A**: Yes, for a transition period, we'll maintain backward compatibility. However, we recommend:
- Use **`utlx`** (new CLI) for transforms, validation, CI/CD
- Use **`utlxd`** (new daemon) for IDE integration, design features

### Q2: Will this break my existing scripts?

**A**: CLI commands remain the same:
```bash
utlx transform input.json  # Still works
utlx validate transform.utlx  # Still works
```

Only **design** command changes:
```bash
# Old
utlx design daemon --port 7778

# New
utlxd start --rest-api --port 7778
```

### Q3: Do I need both executables?

**A**: Depends on your use case:
- **CI/CD pipelines**: Only need `utlx` CLI (smaller, faster)
- **IDE integration**: Only need `utlxd` daemon (LSP, REST API)
- **Development**: Need both (CLI for testing, daemon for IDE)

### Q4: Can I still build a single JAR?

**A**: Technically yes, but not recommended. The split architecture provides significant benefits (performance, size, deployment flexibility).

If you really need a single JAR:
```bash
# Build both modules into one JAR (not recommended)
./gradlew :modules:cli:jar :modules:server:jar
# Then manually merge JARs (complex, defeats the purpose)
```

### Q5: Will native image support all platforms?

**A**: GraalVM native image supports:
- ✅ Linux (x86_64, ARM64)
- ✅ macOS (x86_64, ARM64/M1)
- ✅ Windows (x86_64)

We'll provide pre-built binaries for all platforms in GitHub releases.

### Q6: How does this affect the MCP implementation?

**A**: MCP server will call `utlxd` REST API (not affected):

```typescript
// MCP server calls daemon REST API (no change)
const response = await axios.post('http://localhost:7778/api/validate', {
  transformation: utlxCode
});
```

The split is transparent to MCP server integration.

---

## Timeline Summary

**Total Duration**: 2 weeks

### Week 1
- **Days 1-3**: Create `modules/server/` module with daemon + REST API
- **Days 4-5**: Refactor `modules/cli/` to remove daemon dependency

### Week 2
- **Days 1-2**: Build configuration (native image, distributions, Docker)
- **Days 3-5**: Documentation, testing, migration guide

---

## Success Criteria

- ✅ `utlx` CLI JAR is ≤20MB (target: 15MB)
- ✅ `utlx` native image startup is <100ms
- ✅ `utlxd` daemon includes all LSP + REST API features
- ✅ All existing CLI commands work without changes
- ✅ Daemon starts with both LSP and REST API concurrently
- ✅ Docker image for CLI is ≤50MB (target: 35MB)
- ✅ No breaking changes to existing scripts/CI/CD
- ✅ Complete migration guide and documentation
- ✅ 100% test coverage for both modules

---

## Next Steps

1. **Review and approve this architecture document**
2. **Begin Phase 1**: Create `modules/server/` module
3. **Implement Phases 2-4** following the plan
4. **Release beta** versions for testing
5. **Gather feedback** from users and integrate improvements
6. **Release stable** versions with updated documentation

---

## Related Documentation

- [MCP Architecture Overview](/docs/architecture/README-MCP.md)
- [Daemon REST API Implementation Guide](/docs/architecture/daemon-rest-api-implementation-guide.md)
- [LSP Communication Patterns](/docs/architecture/lsp-communication-patterns-clarification.md)
- [Theia IDE Integration](/docs/architecture/theia-monaco-lsp-mcp-integration.md)

---

**Version History**:
- **v1.0** (2025-11-03): Initial architecture design - approved for implementation
