namespace Glomidco.Utlx;

/// <summary>
/// Reads and writes base-128 varint-encoded lengths for protobuf delimited framing.
/// C# protobuf (unlike Java) does not expose parseDelimitedFrom/writeDelimitedTo,
/// so we implement the varint framing manually. Wire-compatible with Java's
/// MessageLite.writeDelimitedTo / MessageLite.parseDelimitedFrom.
/// </summary>
internal static class VarintCodec
{
    /// <summary>
    /// Read a varint-encoded 32-bit integer from the stream.
    /// Returns -1 on EOF (first byte not available).
    /// </summary>
    public static int ReadVarint32(Stream stream)
    {
        int result = 0;
        int shift = 0;

        for (int i = 0; i < 5; i++) // max 5 bytes for 32-bit varint
        {
            int b = stream.ReadByte();
            if (b == -1)
            {
                if (i == 0) return -1; // clean EOF
                throw new IOException("Unexpected EOF in varint");
            }

            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }

        throw new IOException("Varint too long (>5 bytes for 32-bit value)");
    }

    /// <summary>
    /// Number of bytes <see cref="WriteVarint32(Span{byte}, int)"/> will write for <paramref name="value"/>.
    /// </summary>
    public static int Varint32Size(int value)
    {
        uint v = (uint)value;
        int n = 1;
        while (v >= 0x80) { v >>= 7; n++; }
        return n;
    }

    /// <summary>
    /// Write a varint-encoded 32-bit integer into <paramref name="destination"/>, returning the
    /// number of bytes written.
    ///
    /// WB01: the framing path composes the length prefix and the payload into ONE buffer and issues
    /// a single write, so a frame reaches the pipe atomically. The stream overload below exists for
    /// callers that genuinely have only a stream, and delegates here — one encoder, not two.
    /// </summary>
    public static int WriteVarint32(Span<byte> destination, int value)
    {
        uint v = (uint)value;
        int i = 0;
        while (v >= 0x80)
        {
            destination[i++] = (byte)(v | 0x80);
            v >>= 7;
        }
        destination[i++] = (byte)v;
        return i;
    }

    /// <summary>
    /// Write a varint-encoded 32-bit integer to the stream.
    /// </summary>
    public static void WriteVarint32(Stream stream, int value)
    {
        Span<byte> buf = stackalloc byte[5];
        int n = WriteVarint32(buf, value);
        stream.Write(buf[..n]);
    }
}
