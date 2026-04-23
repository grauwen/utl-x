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
    /// Write a varint-encoded 32-bit integer to the stream.
    /// </summary>
    public static void WriteVarint32(Stream stream, int value)
    {
        uint v = (uint)value;
        while (v >= 0x80)
        {
            stream.WriteByte((byte)(v | 0x80));
            v >>= 7;
        }
        stream.WriteByte((byte)v);
    }
}
