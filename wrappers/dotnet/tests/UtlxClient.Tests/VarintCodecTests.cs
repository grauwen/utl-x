using Xunit;
using Glomidco.Utlx;

namespace Glomidco.Utlx.Tests;

public class VarintCodecTests
{
    [Theory]
    [InlineData(0)]
    [InlineData(1)]
    [InlineData(127)]
    [InlineData(128)]
    [InlineData(300)]
    [InlineData(16384)]
    [InlineData(int.MaxValue)]
    public void Roundtrip_WriteThenRead(int value)
    {
        using var ms = new MemoryStream();
        VarintCodec.WriteVarint32(ms, value);

        ms.Position = 0;
        var result = VarintCodec.ReadVarint32(ms);

        Assert.Equal(value, result);
    }

    [Fact]
    public void ReadVarint32_EmptyStream_ReturnsMinusOne()
    {
        using var ms = new MemoryStream([]);
        var result = VarintCodec.ReadVarint32(ms);
        Assert.Equal(-1, result);
    }

    [Fact]
    public void WriteVarint32_SmallValue_SingleByte()
    {
        using var ms = new MemoryStream();
        VarintCodec.WriteVarint32(ms, 42);
        Assert.Equal(1, ms.Position); // values < 128 = 1 byte
    }

    [Fact]
    public void WriteVarint32_128_TwoBytes()
    {
        using var ms = new MemoryStream();
        VarintCodec.WriteVarint32(ms, 128);
        Assert.Equal(2, ms.Position); // 128 requires 2 bytes
    }

    [Fact]
    public void MultipleVarintsRoundtrip()
    {
        using var ms = new MemoryStream();
        VarintCodec.WriteVarint32(ms, 10);
        VarintCodec.WriteVarint32(ms, 300);
        VarintCodec.WriteVarint32(ms, 1);

        ms.Position = 0;
        Assert.Equal(10, VarintCodec.ReadVarint32(ms));
        Assert.Equal(300, VarintCodec.ReadVarint32(ms));
        Assert.Equal(1, VarintCodec.ReadVarint32(ms));
        Assert.Equal(-1, VarintCodec.ReadVarint32(ms)); // EOF
    }
}
