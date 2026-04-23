# UTL-X Go Client

Go client for the UTL-X Engine (UTLXe). Spawns UTLXe as a long-running JVM subprocess and communicates via varint-delimited protobuf over stdin/stdout.

## Installation

```bash
go get github.com/grauwen/utl-x/wrappers/go/utlxclient
```

## Prerequisites

- Java 17+ (for the UTLXe JVM subprocess)
- UTLXe JAR file (built from the utl-x repo)

## Usage

```go
package main

import (
    "fmt"
    "log"

    "github.com/grauwen/utl-x/wrappers/go/utlxclient"
)

func main() {
    // Start UTLXe (JVM boots, ~2s first time)
    client, err := utlxclient.New(utlxclient.Options{
        JarPath: "/path/to/utlxe.jar",
        Workers: 1,
    })
    if err != nil {
        log.Fatal(err)
    }
    defer client.Close()

    // Load a transformation (compiled once, cached)
    _, err = client.LoadTransformation("my-transform", `
        %utlx 1.0
        input json
        output json
        ---
        {
            name: concat($input.firstName, " ", $input.lastName),
            email: lowerCase($input.email)
        }
    `, "TEMPLATE")
    if err != nil {
        log.Fatal(err)
    }

    // Execute (per message, ~1ms)
    result, err := client.Execute("my-transform",
        []byte(`{"firstName": "Alice", "lastName": "Smith", "email": "ALICE@CORP.COM"}`),
        "application/json")
    if err != nil {
        log.Fatal(err)
    }

    fmt.Println(string(result.Output))
    // {"name": "Alice Smith", "email": "alice@corp.com"}
}
```

## API

### `New(opts Options) (*Client, error)`
Creates and starts a UTLXe subprocess. The JVM boots and a health check confirms readiness before returning.

### `LoadTransformation(id, utlxSource, strategy string) (*LoadTransformationResponse, error)`
Compile a `.utlx` source and register it. Strategies: `"TEMPLATE"`, `"COPY"`, `"AUTO"`.

### `Execute(transformationID string, payload []byte, contentType string) (*ExecuteResponse, error)`
Run a pre-loaded transformation against a payload.

### `ExecuteWithCorrelation(..., correlationID string) (*ExecuteResponse, error)`
Same as Execute but with an explicit correlation ID for response matching in concurrent scenarios.

### `ExecuteBatch(transformationID string, items []*pb.BatchItem) (*ExecuteBatchResponse, error)`
Run a transformation against multiple payloads in one call.

### `ExecutePipeline(transformationIDs []string, payload []byte, contentType, correlationID string) (*ExecutePipelineResponse, error)`
Run a chain of transformations. Output of each stage feeds the next — in-process, no serialization roundtrips.

### `UnloadTransformation(id string) (*UnloadTransformationResponse, error)`
Remove a previously loaded transformation.

### `Health() (*HealthResponse, error)`
Query engine state and statistics.

### `Close() error`
Shut down the UTLXe subprocess gracefully.

## Strategies

| Strategy | Description |
|----------|-------------|
| `TEMPLATE` | Interpret `.utlx` AST at runtime. Default, works without schema. |
| `COPY` | Pre-build UDM skeleton from schema at init-time, deep-copy per message. Faster for high-volume. |
| `AUTO` | Schema provided → COPY, no schema → TEMPLATE. |

## Hot Reload

Update a running transformation without restart:

```go
// Load new version with same ID — atomically replaces the old one
client.LoadTransformation("order-transform", newUtlxSource, "TEMPLATE")
// Next Execute calls use the new version
```

## Building

```bash
# Build the UTLXe JAR (from repo root)
./gradlew :modules:engine:jar

# Run tests
export UTLXE_JAR_PATH=../../modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar
cd wrappers/go/utlxclient
go test -v ./...
```

## How It Works

```
Your Go Application
    │
    │  utlxclient.Client
    │  └── varint-delimited protobuf over stdin/stdout
    │
    └── UTLXe subprocess (JVM)
         └── Compiled UTL-X programs in memory
```

The JVM starts once and stays alive for the application lifetime. Transformations are compiled once via `LoadTransformation` and executed per message via `Execute`. The protobuf framing matches the Open-M integration protocol.

## Thread Safety

The client serializes writes and reads with mutexes. For concurrent Execute calls with `Workers > 1`, use `ExecuteWithCorrelation` with unique correlation IDs to match out-of-order responses.
