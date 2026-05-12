# EB01: Engine Encoding Passthrough

**Status:** Implemented on branch `feature/EB01-binary`  
**Severity:** High (blocks SAP/BizTalk integration)  
**Scope:** Engine transports, ValidationOrchestrator, CLI, utlxd  
**Depends on:** B20 (core `PayloadBytes` type must exist first)  
**Created:** May 2026

---

## Summary

The engine layer (transports, validation, CLI) converts incoming bytes to Java `String` at the entry point, losing the original encoding. After B20 introduces `PayloadBytes` in core, the engine must thread raw bytes through its pipeline instead of pre-decoding.

## What changes

### Transports

| Transport | Current | Fixed |
|-----------|---------|-------|
| `HttpTransport` | `call.receiveText()` → String | `call.receive<ByteArray>()` → PayloadBytes (charset from Content-Type header) |
| `StdioProtoTransport` | `req.payload.toStringUtf8()` → String | `req.payload.toByteArray()` → PayloadBytes (charset from content_type field or UTF-8 default) |
| `GrpcTransport` | Same as proto | Same fix as proto |
| `StdioJsonTransport` | Reads lines as String | Read raw bytes, detect charset |
| CLI (`utlx`) | `System.in` read as text | Read raw bytes from stdin, detect charset from `--charset` flag or BOM |

### ValidationOrchestrator

```kotlin
// Current (re-encodes to UTF-8)
input.toByteArray(Charsets.UTF_8)

// Fixed (use original bytes)
input.bytes  // from PayloadBytes — never re-encoded
```

The validators already accept `ByteArray` — the interface is correct. Only the caller needs to change.

### ExecutionStrategy

```kotlin
// Current
fun execute(input: String): ExecutionResult

// Fixed (after B20)
fun execute(input: PayloadBytes): ExecutionResult
```

All three strategies (Compiled, Template, Copy) pass the bytes to the core format parser, which handles encoding detection (B20).

### Dapr input handler

```kotlin
// Current
val payload = call.receiveText()

// Fixed
val payloadBytes = call.receive<ByteArray>()
val charset = call.request.contentType().parameter("charset")
    ?.let { Charset.forName(it) }
val payload = PayloadBytes(payloadBytes, charset, call.request.contentType().toString())
```

### Output encoding

The Dapr output binding and HTTP responses must respect the output encoding declared in the .utlx header:

```kotlin
// Current (always UTF-8)
val output = execResp.output.toStringUtf8()

// Fixed
val outputBytes = execResp.outputBytes  // raw bytes in declared encoding
val outputCharset = execResp.outputCharset  // from .utlx {encoding: "UTF-16"} or UTF-8 default
call.respondBytes(outputBytes, ContentType.parse(execResp.outputContentType))
```

### CLI

```kotlin
// Current
val input = System.`in`.bufferedReader().readText()

// Fixed
val inputBytes = System.`in`.readBytes()
val charset = args.charset ?: detectCharset(inputBytes) ?: Charsets.UTF_8
val payload = PayloadBytes(inputBytes, charset, contentType)
```

Add `--charset` CLI flag for explicit charset override (useful for piped files where charset can't be detected from headers).

### utlxd (IDE daemon)

Same pattern as CLI — the LSP/REST interface receives file content. If the file has a BOM or Content-Type, use that. Otherwise default to UTF-8. Add charset detection to the file reader.

## Files to modify

| File | Change |
|------|--------|
| `modules/engine/.../transport/HttpTransport.kt` | `receive<ByteArray>()` instead of `receiveText()`, charset from Content-Type |
| `modules/engine/.../transport/StdioProtoTransport.kt` | `toByteArray()` instead of `toStringUtf8()` |
| `modules/engine/.../transport/GrpcTransport.kt` | Same as proto |
| `modules/engine/.../transport/StdioJsonTransport.kt` | Raw byte reading |
| `modules/engine/.../transport/TransportHandlers.kt` | Pass `PayloadBytes` to strategies |
| `modules/engine/.../validation/ValidationOrchestrator.kt` | Use `PayloadBytes.bytes` directly for validators |
| `modules/engine/.../strategy/CompiledStrategy.kt` | Accept `PayloadBytes` |
| `modules/engine/.../strategy/TemplateStrategy.kt` | Accept `PayloadBytes` |
| `modules/engine/.../strategy/CopyStrategy.kt` | Accept `PayloadBytes` |
| `modules/engine/.../Main.kt` | Add `--charset` CLI flag |
| `modules/cli/.../` | Raw stdin reading with charset detection |
| All transport tests | Update to use `PayloadBytes` |

## Effort

2-3 days (after B20 is complete).

## Relationship

- **B20** must be implemented first — introduces `PayloadBytes` type and updates format parsers
- **EB01** consumes B20's types and threads them through the engine
- **EF08** (.NET SDK/BizTalk) depends on EB01 — BizTalk customers send UTF-16 content

---

*Bug EB01. May 2026. Companion to B20. Implement after B20, before EF08.*
