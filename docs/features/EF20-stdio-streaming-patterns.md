# EF20: Stdio Streaming Patterns — gRPC Parity Assessment

**Status:** Assessed — no implementation needed today  
**Priority:** Low (no current use case requires streaming)  
**Created:** May 2026  
**Related:** EF07 (parallel transports), EF18 (request_id pipe matching)

---

## Summary

The stdio-proto transport currently supports **multiplexed unary** (concurrent request/response with out-of-order delivery via `request_id`). This assessment evaluates what would be needed to support gRPC's four call patterns over stdio, and whether any of them are needed.

## Current state: multiplexed unary works

The stdio-proto transport already handles concurrent execution:

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

- Reader thread reads envelopes from stdin sequentially
- Execute/Batch/Pipeline dispatched to worker pool (`--workers N`)
- Responses arrive **out of order** — matched by `request_id` (EF18)
- Back-pressure propagates: response queue full → worker blocks → task queue full → reader blocks → pipe buffer fills → caller blocks

## What the four gRPC patterns need and where we stand

| Pattern | gRPC | Current stdio | Gap |
|---|---|---|---|
| **Unary** (1 req → 1 resp) | Native | **Works** — request_id matching, worker pool, out-of-order responses | None |
| **Server streaming** (1 req → N resp) | Native (stream ID) | **Not supported** — no stream ID in StdioEnvelope, no END_STREAM flag | Need stream framing |
| **Client streaming** (N req → 1 resp) | Native (stream ID) | **Not supported** | Need stream framing |
| **Bidi streaming** (N req ↔ N resp) | Native (HTTP/2 streams) | **Not supported** | Need stream framing + flow control |

## What would be needed for full streaming

To support server/client/bidi streaming over stdio, `StdioEnvelope` would need:

```protobuf
message StdioEnvelope {
  MessageType type = 1;
  bytes payload = 2;
  uint64 stream_id = 3;    // NEW: per-stream multiplexing (like HTTP/2 stream ID)
  bool end_stream = 4;     // NEW: last message for this stream
}
```

Plus:
- **Stream ID allocation convention** — wrapper uses even IDs, engine uses odd IDs (mirrors HTTP/2)
- **Per-stream state map** on both sides — `stream_id → handler/queue`
- **Reader thread demultiplexes** by `stream_id` into per-stream queues
- **Flow control** — optional but important for long-lived streams with uneven consumption rates. Without it, a slow consumer on one stream head-of-line-blocks all streams sharing the pipe.
- **Cancellation** — ability to signal "abort stream N" so resources are freed

This is ~200-300 lines of code on each side — essentially a miniature HTTP/2 multiplexer.

## Do we need streaming today?

**No.** All current consumers use unary request/response:

| Consumer | Pattern | Why unary is sufficient |
|---|---|---|
| **Open-M wrapper** | 1 ExecuteRequest per MPPM message | Pipeline is message-oriented. Scaling is horizontal (more pods), not multiplexed streams. |
| **.NET SDK** | Concurrent unary via request_id | BizTalk pipeline component fires one transform per message. Concurrency via thread pool, not streams. |
| **Go SDK** | Same | One call per message. |
| **Batch** | 1 request with N items, 1 response with N results | Still unary at the envelope level — items are internal. |
| **Pipeline** | 1 request, 1 response | Stages are internal to UTLXe. |
| **Dapr** | HTTP — inherently unary | Each Service Bus/Event Hub message is one HTTP call. |

### Streaming would matter for:

| Use case | Pattern needed | Likelihood |
|---|---|---|
| Large result sets (e.g., query → stream of records) | Server streaming | **Low** — UTLXe is a transformation engine, not a query engine |
| Incremental input (e.g., very large file uploaded in chunks) | Client streaming | **Low** — messages in integration are typically < 1 MB |
| Real-time bidirectional transformation | Bidi streaming | **None identified** |

## Recommendation

**No implementation needed.** The current multiplexed unary over stdio covers all production use cases. If streaming is ever required:

1. **gRPC is already available.** `--mode grpc` or `--also-grpc` gives all four patterns over TCP or Unix domain socket. Any client that needs streaming can use gRPC — it's battle-tested and gives codegen, flow control, deadlines, and cancellation for free.

2. **Don't reinvent gRPC over stdio.** If you need stream framing, flow control, and cancellation over a byte pipe, you've rebuilt gRPC. At that point, use gRPC.

3. **Stdio is for subprocess IPC.** It's appropriate when the engine is a tightly-coupled subprocess (Open-M wrapper, SDK subprocess). For these, unary is the right pattern — the wrapper manages message-level orchestration, UTLXe handles one-at-a-time transformation.

## When to revisit

- If a customer needs to stream very large documents (> 100 MB) through UTLXe without buffering the entire document in memory
- If a real-time event processing use case emerges where latency of individual unary calls is too high
- If Open-M moves to a streaming pipeline model (currently message-oriented, no plans to change)

---

*Feature EF20. May 2026. Assessment — no implementation needed. gRPC already covers streaming if ever required.*
