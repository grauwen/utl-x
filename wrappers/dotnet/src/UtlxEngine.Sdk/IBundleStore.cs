namespace Glomidco.Utlx.Engine;

/// <summary>
/// Abstraction for loading transformation bundles from different sources.
/// </summary>
public interface IBundleStore
{
    /// <summary>Fetch the bundle content (ZIP bytes).</summary>
    Task<BundleData> FetchAsync(string bundleId, CancellationToken ct = default);

    /// <summary>Get the current version of a bundle (for change detection).</summary>
    Task<BundleVersion> GetVersionAsync(string bundleId, CancellationToken ct = default);
}

public record BundleData(byte[] Content, string BundleId, string? Version, string? Checksum);

public record BundleVersion(string BundleId, string? Version, string? Checksum, DateTimeOffset? LastModified);
