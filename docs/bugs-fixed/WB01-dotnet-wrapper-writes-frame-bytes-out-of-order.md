# WB01: the .NET wrapper writes a stdio frame's bytes out of order

> **WB = wrapper bug** (`wrappers/*` — the first-party client libraries that drive the engine).
> This is **WB01**, the first of the prefix.

**Status:** **FIXED** in both wrappers (2026-11-11). Fixed in-house rather than left to the
reporter, because the same pattern was present in the Go client and a convention is better set once
than contributed piecemeal — see *Why this was not left to the reporter*.
**Priority:** High for .NET users on Windows — the demo cannot start, so it is the first thing a new
user meets.
**Component:** `wrappers/dotnet` — `src/UtlxClient/UtlxeClient.cs`. **Not `modules/engine`:** the
engine behaved correctly throughout.
**Reported by:** GitHub issue **#6** (`Eikel1980`), with captured bytes, a replay against the same
JAR in both orders, an environment note, and a working local fix.

> **One-line:** the length prefix is written synchronously and the payload asynchronously on the same
> pipe, so on Windows the payload can reach the engine first — `08 05 02` instead of `02 08 05`.

## What happens

```
expected:  02 08 05      length first -> engine replies RUNNING, demo proceeds
observed:  08 05 02      payload first -> "Protocol message contained an invalid tag (zero)"
                                       -> ChannelClosedException on the .NET side
```

The demo stops at the startup health check. No input file or transformation is needed to reproduce
it — the failure is before any transformation runs.

## Root cause

`UtlxeClient.cs:280-287`:

```csharp
private async Task WriteEnvelopeAsync(StdioEnvelope envelope, CancellationToken ct)
{
    var stream = _process!.InputStream;
    var bytes = envelope.ToByteArray();
    VarintCodec.WriteVarint32(stream, bytes.Length);   // SYNCHRONOUS — stream.WriteByte
    await stream.WriteAsync(bytes, ct);                // ASYNCHRONOUS
    await stream.FlushAsync(ct);
}
```

`VarintCodec.WriteVarint32` uses `stream.WriteByte`, which is synchronous. The payload then goes out
through `WriteAsync`. **Mixing synchronous and asynchronous I/O on one stream is the fault**, and it
is a documented .NET hazard rather than anything specific to this code.

It is Windows-only for a concrete reason: `Process.StandardInput.BaseStream` is an anonymous pipe,
and once asynchronous I/O is used the handle is opened for **overlapped** operation. The synchronous
`WriteByte` then takes a different path to the same handle, and the two are not ordered with respect
to each other. On Linux and macOS both paths are ordinary blocking writes, so the bug is invisible —
which is why it survived until someone ran the demo on Windows.

## The engine is not at fault

Worth stating plainly, because the issue arrived as "UTLXe fails to start" and the obvious filing
would have been `EB`:

`StdioProtoTransport` called `StdioEnvelope.parseDelimitedFrom`, which saw `0x08` where a length
prefix belongs, and reported *"Protocol message contained an invalid tag (zero)"*. **That is the
correct response to a malformed frame.** The engine refused bad bytes; it did not produce them. The
reporter confirmed this himself by replaying the expected sequence against the same JAR and getting
`RUNNING`.

Filing this as `EB` would have named the component where the symptom surfaced rather than the one
that is wrong — the exact mistake this folder's README exists to prevent, and the one that put nine
reports under the wrong prefix earlier in this branch.

## The fix: one frame, one write

The reporter's own change — await the length write before the payload, async for both — is correct
and he confirmed it works end to end. What shipped goes one step further: the length prefix and the
payload are composed into **one buffer** and written **once**.

```csharp
var bytes = envelope.ToByteArray();
var frame = new byte[VarintCodec.Varint32Size(bytes.Length) + bytes.Length];
int offset = VarintCodec.WriteVarint32(frame, bytes.Length);
bytes.CopyTo(frame, offset);
await stream.WriteAsync(frame, ct);
await stream.FlushAsync(ct);
```

Better than two correctly-ordered writes for two reasons beyond style: the frame is **atomic by
construction**, so no later refactor can reintroduce the interleaving — the bug becomes
unexpressible rather than merely absent — and it is one syscall instead of two on a per-message
path.

`VarintCodec` grew a buffer encoder (`WriteVarint32(Span<byte>, int)`) and a `Varint32Size`. The
**existing stream overload now delegates to the buffer one**, so there is a single encoder rather
than two that could drift — the same discipline as B38 §1b, B45 and B47 in the engine.

## The Go wrapper had the same shape — also fixed

`wrappers/go/utlxclient/client.go:314-318`:

```go
if err := writeVarint(c.stdin, len(envData)); err != nil { … }   // one Write
if _, err := c.stdin.Write(envData); err != nil { … }            // another Write
```

Go has no sync/async hazard, so this was **not broken** — but it is the same unnecessary
non-atomicity, and an `io.Writer` is free to be a buffered or short-writing thing where a split
frame matters. It now composes through `appendFrame(dst, payload)` and issues one `Write`, so both
clients share one convention: **one frame, one write.** `writeVarint` is retained for callers that
genuinely have only a writer, with a comment saying why the framing path does not use it.

## Why this was not left to the reporter

He asked whether it would make a good first contribution, and on its own it would have. It was taken
in-house for one reason: **the same pattern existed in the Go client**, and the right outcome is a
convention both wrappers follow, not a .NET fix plus a later Go fix that may or may not match. A
shared rule is a maintainer's job to set.

He should still be told what was done and why — he found it, diagnosed it correctly, and his
analysis is what this report rests on. Better first issues for him are in
`docs/bugs-fixed/WB02-*` (the Go client does not start at all) and the engine-side diagnostic below,
both of which are self-contained and neither of which has a convention to settle.

## An engine-side improvement this suggests — separate, and genuinely `EB`

The diagnostic could have saved him the byte capture. `StdioProtoTransport` catches the parse
exception and the channel dies; the .NET side sees only `ChannelClosedException`. A message along
the lines of *"malformed frame: first byte 0x08 is not a valid length prefix — is the client writing
the payload before the length?"* would name the likely cause at the point of failure.

That is a real improvement and it belongs in `modules/engine`, so it would be an `EB`. It must not
be bundled with this report: a better error message does not fix a client that sends bad bytes, and
treating it as the fix would leave the actual defect in place.

## Verification

| | |
|---|---|
| `wrappers/dotnet` builds | **yes** — `dotnet build`, 0 warnings, 0 errors |
| test project compiles with the new tests | **yes** |
| framing behaviour of the shipped `VarintCodec` | **verified by direct execution** — payload `08 05` frames as `02 08 05`, the exact sequence the issue requires; `Varint32Size` agrees with the encoder across 8 values; the buffer and stream encoders agree across 7; a 300-byte payload yields the `ac 02` two-byte prefix and reads back as 300 |
| `dotnet test` run | **not obtained** — the xUnit runner hangs on this machine (>10 min; `dotnet build` of the same project takes 0.8s). The hang is the runner, not the code: the tests compile, and the behaviour they assert was verified by running the real `VarintCodec` directly. **Run `dotnet test` on a working machine before release.** |
| `wrappers/go` builds and vets | **yes** — `go build ./...`, `go vet ./...`, both clean |
| `go test` run | **✅ passes** — including the five new framing tests, *and* the twelve pre-existing integration tests that spawn the real engine and speak the stdio protobuf protocol (`ok … 30.441s`). This was blocked by [WB02](WB02-go-client-panics-on-import.md) until that was fixed. |

So the Go half is verified **end to end against a running engine**, which is stronger than planned:
`TestHealth`, `TestExecute`, `TestExecuteBatch`, `TestFullLifecycle` and the rest all drive the new
single-write framing over a real pipe and pass.

The .NET half is verified against the real `VarintCodec` by direct execution, but its **test runner
has not been executed here** — see the row above. That is the one gap remaining, and Windows is the
right place to close it, since Windows is also where the original race only ever appeared.

Note on revert-verification: the Go change is a behaviour-preserving refactor (two writes to one),
so reverting it does not fail a test — it simply restores the older shape. The evidence that matters
there is the twelve integration tests passing *with* the new framing against a real engine. The .NET
change is likewise ordering, not output; the byte sequence `02 08 05` is what the framing tests pin.

## Reproduction (from the issue, unmodified)

```powershell
.\gradlew.bat :modules:engine:jar
$env:UTLXE_JAR_PATH = (Resolve-Path 'modules/engine/build/libs/utlxe-1.3.0.jar').Path
dotnet run --project wrappers/dotnet/samples/Demo/Demo.csproj --configuration Release
```

Windows x64, .NET 10 SDK 10.0.402, OpenJDK 17, engine JAR from `development` at `8846f760`.

## Related

- [WB02](WB02-go-client-panics-on-import.md) — found while trying to run the Go tests for this fix.
  The Go client cannot be imported at all, which is why the Go half is verified by compilation only.
- The README's [prefix rules](README.md) — `WB` was added for this report, because every other
  prefix names a JVM module and a wrapper defect had no honest home.
