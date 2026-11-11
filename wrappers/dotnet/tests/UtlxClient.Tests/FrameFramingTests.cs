using Xunit;
using Glomidco.Utlx;
using Utlxe.V1;

namespace Glomidco.Utlx.Tests;

/// <summary>
/// WB01 (GitHub issue #6) — a length-delimited frame must reach the pipe as one ordered unit.
///
/// The original code wrote the varint length with a synchronous <c>WriteByte</c> and the payload
/// with an asynchronous <c>WriteAsync</c>. On Windows the pipe handle is opened for overlapped
/// operation once async I/O is used, so the two were not ordered and the payload could arrive
/// first: <c>08 05 02</c> instead of <c>02 08 05</c>.
///
/// These tests pin the encoder and the framing shape. The ordering itself is now structural — the
/// frame is composed in one buffer and written once — so there is no interleaving left to test.
/// </summary>
public class FrameFramingTests
{
    /// <summary>A stream that records each individual Write call, so framing can be observed.</summary>
    private sealed class RecordingStream : Stream
    {
        public List<byte[]> Writes { get; } = new();
        public byte[] All => Writes.SelectMany(w => w).ToArray();

        public override void Write(byte[] buffer, int offset, int count)
            => Writes.Add(buffer.Skip(offset).Take(count).ToArray());
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken ct = default)
        { Writes.Add(buffer.ToArray()); return ValueTask.CompletedTask; }
        public override void WriteByte(byte value) => Writes.Add(new[] { value });

        public override bool CanRead => false;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() { }
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
    }

    [Fact]
    public void VarintSize_MatchesWhatTheEncoderWrites()
    {
        foreach (var v in new[] { 0, 1, 127, 128, 300, 16383, 16384, int.MaxValue })
        {
            Span<byte> buf = stackalloc byte[5];
            int n = VarintCodec.WriteVarint32(buf, v);
            Assert.Equal(VarintCodec.Varint32Size(v), n);
        }
    }

    [Fact]
    public void BufferAndStreamEncodersAgree()
    {
        // One encoder, not two: the stream overload delegates to the buffer one.
        foreach (var v in new[] { 0, 1, 127, 128, 300, 16384, int.MaxValue })
        {
            Span<byte> buf = stackalloc byte[5];
            int n = VarintCodec.WriteVarint32(buf, v);
            var viaBuffer = buf[..n].ToArray();

            using var ms = new MemoryStream();
            VarintCodec.WriteVarint32(ms, v);
            Assert.Equal(viaBuffer, ms.ToArray());
        }
    }

    [Fact]
    public void AFrameIsLengthPrefixedThenPayload()
    {
        // The reported case: a 2-byte payload 08 05 must go out as 02 08 05.
        var payload = new byte[] { 0x08, 0x05 };
        var frame = new byte[VarintCodec.Varint32Size(payload.Length) + payload.Length];
        int offset = VarintCodec.WriteVarint32(frame, payload.Length);
        payload.CopyTo(frame, offset);

        Assert.Equal(new byte[] { 0x02, 0x08, 0x05 }, frame);
    }

    [Fact]
    public async Task AFrameIsWrittenAsASingleWrite()
    {
        // The property that makes the Windows ordering bug structurally impossible: one Write call,
        // so there are no two operations whose order the OS could decide.
        var payload = new byte[] { 0x08, 0x05 };
        var frame = new byte[VarintCodec.Varint32Size(payload.Length) + payload.Length];
        int offset = VarintCodec.WriteVarint32(frame, payload.Length);
        payload.CopyTo(frame, offset);

        var stream = new RecordingStream();
        await stream.WriteAsync(frame);

        Assert.Single(stream.Writes);
        Assert.Equal(new byte[] { 0x02, 0x08, 0x05 }, stream.All);
    }

    [Fact]
    public void ALargePayloadStillFramesCorrectly()
    {
        // 300 bytes needs a two-byte varint (AC 02), which is where an off-by-one in the size
        // calculation would show up.
        var payload = new byte[300];
        var frame = new byte[VarintCodec.Varint32Size(payload.Length) + payload.Length];
        int offset = VarintCodec.WriteVarint32(frame, payload.Length);
        payload.CopyTo(frame, offset);

        Assert.Equal(302, frame.Length);
        Assert.Equal(new byte[] { 0xAC, 0x02 }, frame[..2]);

        using var ms = new MemoryStream(frame);
        Assert.Equal(300, VarintCodec.ReadVarint32(ms));
    }
}

/// <summary>
/// WB03 — which response types carry a correlation id.
///
/// `ExecuteBatchAsync` was sent through the correlated path, but `ExecuteBatchResponse` is
/// `repeated ExecuteResponse results = 1;` — no id field at all. The reader could not match it, so
/// it went to the sequential channel while the caller awaited a `TaskCompletionSource` nothing
/// would ever complete: a hang, or a `ChannelClosedException` if the reader loop ended first.
///
/// This classification is the piece of knowledge that bug cost, so it gets a test. If a future
/// message type is added to the correlated path, it needs a case in `ExtractRequestId` too — and
/// if it has no id field, it belongs on the sequential path instead.
/// </summary>
public class CorrelationClassificationTests
{
    [Fact]
    public void ExecuteResponse_CarriesACorrelationId()
    {
        Assert.True(UtlxeClient.CarriesCorrelationId(MessageType.ExecuteResponse));
    }

    [Fact]
    public void ExecuteBatchResponse_DoesNot()
    {
        // The whole of WB03 in one assertion: batch has no correlation id, so it must never be
        // awaited by one.
        Assert.False(UtlxeClient.CarriesCorrelationId(MessageType.ExecuteBatchResponse));
    }

    [Theory]
    [InlineData(MessageType.LoadTransformationResponse)]
    [InlineData(MessageType.UnloadTransformationResponse)]
    [InlineData(MessageType.HealthResponse)]
    public void SequentialResponseTypes_DoNot(MessageType type)
    {
        Assert.False(UtlxeClient.CarriesCorrelationId(type));
    }
}
