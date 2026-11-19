# EB21 — the HTTP data plane's JSON envelope destroys the payload charset, and cannot carry binary at all

**Status:** Open — found 2026-11-19
**Priority:** High — silent data corruption on any non-UTF-8 payload that declares its charset
honestly; and a published API shape that would be expensive to change later
**Component:** `:modules:engine` — `transport/HttpTransport.kt`, `transport/HttpModels.kt`
**Found:** 2026-11-19, while deciding what the Azure offering should publish as its data plane
(EF24). Not by review and not by any test.
**Relates to:** [EB01](EB01-engine-encoding-passthrough.md) — this is the half of EB01 that was
never finished

## Summary

Three code paths reach `TransportHandlers.handleExecute`. Two read the request body as raw
bytes. The third — the only one intended as the public HTTP API — requires the caller to escape
the whole document into a JSON string field, and then re-encodes that string as UTF-8 before
handing it on:

```kotlin
// HttpTransport.kt:164 — POST /api/execute/{id}
val req = call.receive<ExecuteRequestBody>()
val proto = ExecuteRequest.newBuilder()
    .setTransformationId(id)
    .setPayload(ByteString.copyFromUtf8(req.payload))   // <-- the defect
    .setContentType(req.contentType)
```

```kotlin
// HttpModels.kt:39
data class ExecuteRequestBody(
    val payload: String,                                 // <-- a String, not bytes
    val contentType: String = "application/json",
    val correlationId: String? = null
)
```

Two consequences, one of them silent corruption.

**1. The declared charset is invalidated, and then trusted.** The handler is correct — EB01's
fix landed there (`TransportHandlers.kt:193`):

```kotlin
val rawBytes     = req.payload.toByteArray()
val charset      = parseCharsetFromContentType(contentType)
val payloadBytes = PayloadBytes(rawBytes, charset, contentType)
```

So the handler faithfully applies the charset the caller declared — to bytes the transport has
already transcoded to UTF-8. The content type is a lie by the time it is read.

**2. Binary payloads are inexpressible.** A JSON string cannot hold arbitrary bytes and there is
no base64 field, so Avro and Protobuf — two of the ten supported formats — cannot be submitted
over the HTTP data plane at all.

## Failure scenario

A caller submits Latin-1 XML and declares it correctly:

```json
{
  "payload": "<Item>Café</Item>",
  "contentType": "application/xml; charset=ISO-8859-1"
}
```

```
caller escapes the document into a JSON string
  -> Jackson decodes it to a Java String (UTF-16)
  -> copyFromUtf8 re-encodes to UTF-8:  3c 49 ... 43 61 66 C3 A9 ...
  -> handler reads charset=ISO-8859-1 from the content type
  -> PayloadBytes.decodeToString() = String(bytes, ISO_8859_1)
```

`decodeToString()` is `String(bytes, detectedCharset ?: Charsets.UTF_8)`
(`udm_core.kt:21`), so the two UTF-8 bytes `C3 A9` are decoded as two Latin-1 characters:

```
  in :  <Item>Café</Item>
  out:  <Item>CafÃ©</Item>
```

Mojibake, with HTTP 200 and no error anywhere. The transformation succeeds; the data is wrong.

It only works when the caller declares `charset=UTF-8` (or omits the charset, which defaults to
UTF-8) — which is why nobody has hit it. The bug fires precisely on the caller who is being
careful.

`/api/transform` carries the same `payload: String` field and the same defect.
`/api/execute-batch/{id}` and `/api/execute-pipeline` carry it per item.

## Why the other paths are fine

| path | reads body as | correct |
|---|---|---|
| `POST /{bindingName}` — Dapr input binding, `HttpTransport.kt:425` | `call.receive<ByteArray>()` | yes |
| `POST /pubsub/{name}` — Dapr pub/sub, `HttpTransport.kt:638` | `call.receive<ByteArray>()` | yes |
| `POST /admin/transformations/{name}/test`, `AdminEndpoint.kt:263` | `call.receive<ByteArray>()` | yes |
| **`POST /api/execute/{id}`**, `HttpTransport.kt:164` | **`ExecuteRequestBody.payload: String`** | **no** |

The `/admin/.../test` call site even carries the comment `// EB01: Read raw bytes for test
execute — supports non-UTF-8 payloads`. The fix was applied beside the broken path and not to
it.

**The wrappers are not affected.** Both the .NET and Go clients spawn
`--mode stdio-proto` (`wrappers/dotnet/src/UtlxClient/UtlxeProcess.cs:46`,
`wrappers/go/utlxclient/client.go:71`), so they submit protobuf `bytes` and reach the correct
handler. Only the HTTP transport's JSON models are involved. gRPC is likewise unaffected: its
`ExecuteRequest.payload` is proto `bytes`.

## Root cause

`ExecuteRequestBody.payload` is typed `String`. The proto field it feeds is `bytes`; the
handler that consumes it wants bytes; the two Dapr paths and the admin test path all supply
bytes. The JSON model is the only place in the engine where a payload is modelled as text, and
modelling it as text is what loses the encoding — exactly the finding EB01 recorded for
`receiveText()`. `copyFromUtf8` is `receiveText()` by another name.

The deeper problem is interface shape rather than a single field. UTL-X's premise is that no
format is privileged, and the UDM exists to keep transformation logic away from the source
format. An envelope that requires every document to be escaped into a JSON string privileges
JSON at the API boundary and demotes the other nine formats to escaped text — or, for the
binary two, excludes them.

## Fix

The shape that works already exists three times over. Give single execute the same shape:

- transformation id in the path (it already is)
- raw body, `call.receive<ByteArray>()` — not a JSON field
- `Content-Type` read from the header, which is what the handler already does
- `correlationId` from a header (`X-Correlation-Id`), as Dapr already passes metadata

`ExecuteRequestBody` then has no remaining field and can go. The structured envelope stays
correct for `/api/execute-batch/{id}` and `/api/execute-pipeline`, where several payloads
genuinely need a body with structure — though those should take base64 or move to proto if
binary formats are ever to work in batch.

This changes a request shape that is documented, so it needs a deprecation path rather than a
silent swap:

1. Add the raw-body route alongside the existing one.
2. Keep `/api/execute/{id}` accepting the envelope, but when `contentType` declares a charset
   other than UTF-8, either honour it (decode the JSON string back to the declared charset
   before handing bytes on) or reject with 415. Silently corrupting is the one behaviour to
   remove immediately.
3. Document the raw-body route as the data plane; retire the envelope at the next major.

## Tests

`EB21HttpPayloadCharsetTest.kt`, with a vacuity guard — a fixture asserting only ASCII would
pass against the defect:

1. Latin-1 XML submitted with `charset=ISO-8859-1` via the enveloped route returns mojibake
   today. The test pins the correct output and must fail before the fix.
2. The same document via `POST /admin/transformations/{name}/test` already returns the right
   answer — proves the handler is innocent and the transport is at fault.
3. UTF-16 input, since `parseCharsetFromContentType` advertises `charset=UTF-16` support in its
   own doc comment.
4. A payload with a byte sequence that is not valid UTF-8 at all, which the envelope cannot
   represent — asserts a clear error rather than silent substitution.
5. **Non-ASCII must be in the fixture.** A pure-ASCII payload is byte-identical under every
   charset involved and would make the whole test vacuous.

Use `ServerSocket(0)` for the port.

## Notes

Found while deciding what the Azure Marketplace offering should publish as its data plane.
The conclusion recorded in
`devdocs/azure/utlxe-deployment-modes.md` and in EF24 is that the offering must **not** publish
`/api/execute/{id}`: the credential gap is an afternoon's work in nginx, but a published
request shape is permanent. (`devdocs/` is not merged to `main`, so that is a path rather than
a link.)

A secondary observation, not a bug: `/admin/transformations/{name}/test` is the engine's best
HTTP data-plane interface. It is filed under `/admin/`, named "test", deliberately excluded
from metrics (`// Test calls are NOT counted in metrics`), and gated behind the key that can
also deploy and delete transformations. Nobody evaluating the product would find it, and anyone
who did would assume it was not meant for production traffic.
