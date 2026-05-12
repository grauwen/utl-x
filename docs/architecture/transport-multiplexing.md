# UTLXe Transport Multiplexing Architecture

**Last reviewed:** May 2026

---

## Transport Overview

UTLXe supports four transports. Each has different multiplexing characteristics:

| Transport | Wire | Multiplexing | Use case |
|---|---|---|---|
| **HTTP** (Dapr) | HTTP/1.1 per request | One request per HTTP call — inherently unary | Azure Container Apps, Dapr bindings, pub/sub |
| **stdio-proto** | Varint-delimited protobuf over stdin/stdout | Multiplexed unary via worker pool + `request_id` | Open-M wrapper, .NET SDK, Go SDK |
| **gRPC** | HTTP/2 | Full 4-pattern support (unary, server/client/bidi streaming) | SDK clients needing streaming, high-throughput direct integration |
| **stdio-json** | JSON lines over stdin/stdout | Sequential (one at a time) | CLI backward compat, simple piping |

## stdio-proto: Multiplexed Unary

The stdio-proto transport already handles concurrent multiplexed execution:

```
                  ┌──────────────┐
  stdin ────────► │ Reader thread │ ──┬──► Worker 1 ──┐
  (varint-        │ (main thread) │   ├──► Worker 2 ──┤
   delimited)     └──────────────┘   ├──► Worker 3 ──┤
                                      └──► Worker N ──┤
                                                      ▼
                                            ┌──────────────────┐
  stdout ◄──────────────────────────────────│ Response queue    │
  (varint-                                  │ (bounded, FIFO)   │
   delimited)     ┌──────────────┐          └──────────────────┘
            ◄──── │ Writer thread │ ◄───────────────┘
                  └──────────────┘
```

### How it works

1. **Reader thread** reads envelopes from stdin sequentially
2. Init-time messages (Load, Unload, Health) are handled synchronously on the reader thread
3. Execute/Batch/Pipeline requests are dispatched to the **worker pool** (configurable via `--workers`)
4. Workers process requests concurrently — responses arrive **out of order**
5. **Writer thread** is the sole owner of stdout — drains the bounded response queue
6. Each response carries `request_id` (EF18) — the caller matches responses to requests

### Back-pressure chain

```
Writer slow → response queue full → workers block on put()
→ task queue full → CallerRunsPolicy → reader thread executes task
→ reader blocks → stdin pipe buffer fills → Go wrapper's write() blocks
→ upstream source throttled
```

Pressure propagates end-to-end without configuration. When the writer catches up, everything unblocks.

### Why this is sufficient

The wrapper sends N ExecuteRequests rapidly. UTLXe processes them on N workers. Responses come back in whatever order they finish. The wrapper dispatches by `request_id`. This is **multiplexed unary RPC** — functionally equivalent to what gRPC provides for unary calls.

## What about gRPC's streaming patterns?

The four gRPC call types:

| Pattern | What | Needed for UTLXe? |
|---|---|---|
| **Unary** (1 req → 1 resp) | Standard request/response | **Yes** — this is what all transports do |
| **Server streaming** (1 req → N resp) | Large result sets, event feeds | **No** — mapping produces one output per input |
| **Client streaming** (N req → 1 resp) | Aggregation, upload | **No** — each message is independent |
| **Bidi streaming** (N ↔ N) | Chat, real-time sync | **No** — no interactive transformation use case |

### What streaming over stdio would require

Adding streaming to `StdioEnvelope` would need:

```protobuf
message StdioEnvelope {
  MessageType type = 1;
  bytes payload = 2;
  uint64 stream_id = 3;    // per-stream multiplexing (like HTTP/2 stream ID)
  bool end_stream = 4;     // last message for this stream
}
```

Plus: stream ID allocation, per-stream state maps, demultiplexing reader, and flow control. This is ~200-300 lines on each side — a miniature HTTP/2 multiplexer.

### Why we don't build it

1. **No use case today.** All consumers (Open-M, .NET SDK, Go SDK) use unary request/response.
2. **gRPC already exists.** `--mode grpc` or `--also-grpc` gives all four patterns for free over TCP or Unix domain socket. Any client that needs streaming can use gRPC.
3. **Dapr is unary by design.** Each Service Bus/Event Hub message is one HTTP call — no streaming needed.
4. **Complexity cost.** Stream framing, flow control, and cancellation are subtle to get right. gRPC has years of battle-testing. Rolling our own buys nothing.

## Transport selection guide

| Scenario | Transport | Why |
|---|---|---|
| Azure Container Apps + Dapr | HTTP (`--mode http`) | Dapr sidecar delivers via HTTP. Native Azure integration. |
| Open-M pipeline | stdio-proto | Go wrapper manages subprocess lifecycle. Multiplexed unary via request_id. |
| .NET SDK / Go SDK | stdio-proto | Subprocess model. Same multiplexing. |
| High-throughput SDK client | gRPC (`--mode grpc`) | When the caller needs >1000 msg/s or streaming. Unix socket avoids TCP overhead. |
| CLI piping | stdio-json | `cat data.json \| utlxe --bundle ...` — simple, human-readable. |
| Hybrid (Open-M + admin) | stdio-proto + HTTP (`--also-http`) | Primary transport is pipe, admin API on HTTP. |

## Identity model across transports

All transports use the same three-ID model from the proto:

| Field | Purpose | Set by |
|---|---|---|
| `request_id` | Pipe-level response dispatch (EF18) | Caller (unique per call) |
| `correlation_id` | MPPM transaction grouping | Originator at ingress (shared across fan-out) |
| `message_id` | Message identity (UUIDv7) | Per hop |
| `causation_id` | Causal parent | Per hop |

For HTTP/Dapr, `request_id` is irrelevant (HTTP is inherently request/response). For stdio-proto and gRPC, `request_id` enables multiplexed dispatch.

---

*Architecture document. May 2026. Covers all four UTLXe transports and their multiplexing characteristics.*
