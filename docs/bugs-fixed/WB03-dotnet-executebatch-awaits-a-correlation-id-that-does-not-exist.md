# WB03: `ExecuteBatchAsync` awaits a correlation ID the protocol never sends

> **WB = wrapper bug** (`wrappers/*` — the first-party client libraries that drive the engine).
> This is **WB03**.

**Status:** **FIXED** (2026-11-11), verified on Windows.
**Priority:** High for .NET users. `ExecuteBatchAsync` could not complete — it either hung forever
or failed instantly, depending on timing, and it took the whole test suite down with it.
**Component:** `wrappers/dotnet` — `UtlxeClient.ExecuteBatchAsync`.
**Found by:** running the .NET test suite on Windows while verifying
[WB01](WB01-dotnet-wrapper-writes-frame-bytes-out-of-order.md). Not reported by a user — batch is
presumably not in use, because it has never worked.

> **One-line:** batch went down the correlated path, but `ExecuteBatchResponse` carries no
> `request_id`, so the response was routed to the sequential channel while the caller waited on a
> `TaskCompletionSource` nothing would ever complete.

## Two symptoms, one cause

Observed on the same build, minutes apart:

| run | result |
|---|---|
| inside a loop of filtered runs | **Failed in 128 ms** |
| on its own, immediately after | **hung > 10 minutes** |

Fails fast sometimes, hangs other times — that pattern is a race, and it was. Both come from the
same defect seen from different sides:

- **Hang**: the response is delivered somewhere the caller is not waiting, so the `await` never
  returns.
- **Fast failure**: if the reader loop ends first (process exit, disposal), its `finally` faults
  every entry in `_pendingRequests`, so the same `await` returns `ChannelClosedException` in
  milliseconds.

Which one you get depends only on whether the process outlives the call. That is why it looked
flaky.

It also explains why a **full** `dotnet test` run stalled at 6 of 10 integration tests with no
summary line: the batch test was holding the run open.

## Root cause

`ExecuteBatchAsync` sent through `SendCorrelatedAsync`, which registers a `TaskCompletionSource`
under a request ID and waits for the reader loop to match a response to it.

The reader matches by calling `ExtractRequestId`:

```csharp
private string? ExtractRequestId(StdioEnvelope envelope)
{
    if (envelope.Type == MessageType.ExecuteResponse)   // <-- only this one
    {
        var resp = ExecuteResponse.Parser.ParseFrom(envelope.Payload);
        if (!string.IsNullOrEmpty(resp.RequestId)) return resp.RequestId;
        return string.IsNullOrEmpty(resp.CorrelationId) ? null : resp.CorrelationId;
    }
    return null;                                        // <-- every batch response lands here
}
```

And it cannot be extended, because **there is nothing to extract.** The message has no such field:

```protobuf
message ExecuteBatchResponse {
  repeated ExecuteResponse results = 1;
}
```

No `request_id`, no `correlation_id` — the batch is a single request/response at the protocol level
and was never given a batch-level identifier. So `ExtractRequestId` returns null, the reader takes
its else branch, and the envelope goes to `_sequentialChannel` — a queue the caller is not reading
from. The `TaskCompletionSource` in `_pendingRequests` is never completed by anyone.

### The method already said what it should do

```csharp
/// Execute a pre-loaded transformation against multiple payloads in one call.
/// The entire batch is a single request/response — no correlation ID matching needed.
```

The summary is correct and the body did the opposite. Worth noting because it is the cheapest kind
of bug to catch in review and the easiest to skim past.

## The fix

Send it sequentially, which is where the reader was putting the response all along:

```csharp
var resp = await SendSequentialAsync(
    MessageType.ExecuteBatchRequest, req, MessageType.ExecuteBatchResponse, ct);
```

One line. `batchReqId` is still generated and stamped onto each `BatchItem` — that is per-item
correlation for the results, and unrelated to how the envelope is routed.

### Why the Go client was unaffected

`wrappers/go` has the same `TestExecuteBatch` and it **passes, every run, in 2.48 s**. Its client is
strictly sequential — `sendAndReceive` takes a write lock, writes, then reads the next message —
so there is no correlation layer to be incomplete. Same protocol, same engine, same operation: one
client worked and the other could not.

That contrast is what located this quickly, and it is an argument for keeping the two clients
behaviourally comparable: a difference between them is a lead.

## Verification

All on Windows 11 arm64, .NET 10.0.404, JDK 21, against a real engine over the stdio pipe.

| | before | after |
|---|---|---|
| `ExecuteBatch_ProcessesMultipleItems`, 3 consecutive runs | failed in 128 ms / hung > 10 min | **passed** — 262 ms, 325 ms, 294 ms |
| full `dotnet test` | stalled at 6 of 10 integration tests, no summary | **`Passed! Failed: 0, Passed: 26, Total: 26`** in 33 s |

The full suite completing at all is new: it has not produced a summary line on any platform in this
session until now.

## What this says about the correlated path

The fix routes batch away from the correlation layer rather than teaching the layer about batch,
because the protocol gives it nothing to correlate on. But the layer still has a weakness worth
recording: **`ExtractRequestId` returns null for any message type it does not recognise, and the
reader silently diverts those to the sequential channel.** That is a reasonable fallback for an
unknown message, and a silent trap for a *correlated* request whose response type was overlooked —
exactly what happened here.

If a future message type is added to the correlated path, it needs a case in `ExtractRequestId`, or
it will hang the same way. **That is now guarded three ways:**

**1. The classification is explicit, named, and documented.**

```csharp
internal static bool CarriesCorrelationId(MessageType type) =>
    type == MessageType.ExecuteResponse;
```

It was previously implicit in two `if` conditions that disagreed with each other. The XML doc on it
says what to do when adding a type: give it a case in `ExtractRequestId` too, or — if it has no id
field — put it on the sequential path.

**2. The silent fallback talks.** The reader now distinguishes the two cases it used to conflate:

- a type that **has** no correlation id (`ExecuteBatchResponse`) → routed sequentially, no noise,
  because that is now the correct and intended path;
- a type that **should** have had one → `LogWarning`:

  > *A {Type} arrived with no extractable request id. It is being routed to the sequential channel,
  > so any caller awaiting it by correlation will hang. Give this type a case in ExtractRequestId,
  > or send it via SendSequentialAsync.*

  and separately, a response whose id matches nothing pending → *"late, duplicate, or already
  cancelled"*.

  Either message would have named this bug on the first run, instead of it presenting as a flaky
  test that failed in 128 ms one time and hung for ten minutes the next.

**3. The classification is pinned by tests.** `CorrelationClassificationTests` asserts
`ExecuteResponse` carries an id, `ExecuteBatchResponse` does not — *that assertion is the whole of
WB03 in one line* — and that the three sequential response types do not either. The project already
had `InternalsVisibleTo`, so this needed no new test seam.

Verified on Windows: **31/31 pass** (26 existing + the 5 new classification cases), and the warning
does not fire on the batch path, which is the thing that would have made it noise.

## Related

- [WB01](WB01-dotnet-wrapper-writes-frame-bytes-out-of-order.md) — found while verifying it; the
  two are independent defects in the same client.
- [WB02](WB02-go-client-panics-on-import.md) — the Go client's own blocker, fixed first, which is
  what made the Go/.NET comparison above possible.
