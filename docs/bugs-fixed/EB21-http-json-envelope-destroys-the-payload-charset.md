# EB21 — the HTTP data plane's JSON envelope destroys the payload charset, and cannot carry binary at all

**Status:** **FIXED (2026-11-19)** — all six `setPayload` sites in `HttpTransport` corrected, with
`EB21PayloadCharsetTest` (7 tests) and the full engine suite green.
**Priority:** High — silent data corruption on any non-UTF-8 payload. **Revised upward after the
first draft of this report**: the Dapr paths were originally described here as correct, and they
were not. They were the only affected paths reachable in the shipped Azure offering.
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

## Correction: the Dapr paths were NOT fine

The first draft of this report said the Dapr paths were correct, on the evidence that they call
`call.receive<ByteArray>()`. They do — and then discard the result on the next line:

```
grep -n "payloadBytes|rawBodyBytes"
  425:  val payloadBytes = call.receive<ByteArray>()
  427:  val payload = String(payloadBytes, Charsets.UTF_8)   ← the ONLY use
  638:  val rawBodyBytes = call.receive<ByteArray>()
  639:  val rawBody = String(rawBodyBytes, Charsets.UTF_8)   ← the ONLY use
```

Both then built the proto from the String. So **all six `setPayload` sites** used
`copyFromUtf8(<String>)` and not one passed raw bytes. The `receive<ByteArray>()` calls looked
like EB01's fix and were inert.

The Dapr form is strictly worse than the enveloped form, because the decode is hardcoded UTF-8
with no charset consulted at all, so an invalid sequence is **destroyed rather than mis-decoded**:

```
Service Bus body (Latin-1):    b'<Item>Caf\xe9</Item>'
String(bytes, UTF_8):           '<Item>Caf\ufffd</Item>'      ← invalid UTF-8 → U+FFFD
bytes handed to the engine:     b'<Item>Caf\xef\xbf\xbd</Item>'
original 0xE9 recoverable?      False
```

And it was the reachable one:

| path | line | reachable in the shipped Azure offering? |
|---|---|---|
| `POST /api/transform` | 112 | no — port 8085 is not published |
| `POST /api/execute/{id}` | 168 | no |
| `POST /api/execute-batch/{id}` | 210 | no |
| `POST /api/execute-pipeline` | 244 | no |
| **`handleDaprInput()` — Service Bus queue in** | **457** | **yes** |
| **`handlePubSubInput()` — Service Bus topic in** | **683** | **yes** |

So the severity was backwards in the first draft. Any customer feeding ISO-8859-1 or UTF-16 XML
over Service Bus — ordinary in European ERP and EDI traffic — was getting corrupted input with
HTTP 200 and no error.

`POST /admin/transformations/{name}/test` is genuinely correct: it passes
`ByteString.copyFrom(inputBytes)` and never decodes.

**The wrappers are unaffected.** Both the .NET and Go clients spawn `--mode stdio-proto`
(`wrappers/dotnet/src/UtlxClient/UtlxeProcess.cs:46`,
`wrappers/go/utlxclient/client.go:71`), so they submit protobuf `bytes` and reach the correct
handler. gRPC is likewise unaffected: its `ExecuteRequest.payload` is proto `bytes`.

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

Two shapes, because the two groups lose the bytes differently.

**The Dapr paths still HAVE the original bytes** — pass them:

```kotlin
.setPayload(ByteString.copyFrom(payloadBytes))                 // handleDaprInput
```

Pub/sub needs one distinction: when the payload came out of a *structured* CloudEvents document it
was a JSON string field, so the raw body is the envelope rather than the payload.
`unwrapCloudEvents` returns `rawBody` unchanged when there is no envelope, which makes the test
reliable:

```kotlin
.setPayload(
    if (payload == rawBody) ByteString.copyFrom(rawBodyBytes)
    else encodeDeclared(payload, contentType)
)
```

**The enveloped routes only ever had text** — re-encode it with the charset the caller declared,
which closes the round trip, since the caller decoded the document to text to put it in a JSON
string in the first place:

```kotlin
private fun encodeDeclared(text: String, contentType: String): ByteString {
    val charset = TransportHandlers.parseCharsetFromContentType(contentType) ?: Charsets.UTF_8
    return ByteString.copyFrom(text.toByteArray(charset))
}
```

`TransportHandlers.parseCharsetFromContentType` changed from `private` to `internal` to be reused;
no new charset logic was written.

This does **not** make binary formats expressible on the enveloped routes — a JSON string holds no
arbitrary bytes, so Avro and Protobuf still cannot be submitted that way. That is an API-shape
limitation, recorded below, not a charset bug.

## Verification

`EB21PayloadCharsetTest` — 7 tests, `:modules:engine:test` 1036 tests and `:modules:core:test` 441
tests, 0 failures.

Proven load-bearing by restoring the defect at all six sites:

```
dapr binding preserves an ISO-8859-1 payload()      FAILED
dapr binding preserves a UTF-16 payload()           FAILED
enveloped execute honours the declared charset()    FAILED
dapr binding still handles plain UTF-8()            PASSED   ← regression guard, correctly insensitive
the guard transformation really discriminates()     PASSED   ← negative control
the fixture is actually non-ASCII()                 PASSED   ← vacuity guard
```

Three details of the test worth keeping:

- **The Dapr route returns a messaging acknowledgement, not the output** — in a real flow the
  output goes to the output binding. So the transformation itself is the detector: it returns
  `"intact"` when the accented character survived and divides by zero when it did not, turning the
  question into the ack's `success` field. A failed transformation answers HTTP 500, which is how
  Dapr learns to abandon the message, so the test accepts 200 or 500 and reads the flag.
- **The negative control is the important one.** Latin-1 bytes *declared as UTF-8* must fail.
  Without it, a transformation that returned `"intact"` for everything would make the three
  positive tests vacuous.
- **Every payload is non-ASCII on purpose**, and one test asserts that the fixture's Latin-1 and
  UTF-8 encodings differ in length. ASCII is byte-identical under both, so an ASCII fixture passes
  against the defect and proves nothing.

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
