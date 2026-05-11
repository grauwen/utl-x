namespace Glomidco.Utlx.Engine;

/// <summary>
/// High-level interface for UTLXe transformation engine.
/// Manages subprocess lifecycle, bundle loading, and transformation execution.
/// </summary>
public interface IUtlxEngine : IAsyncDisposable, IDisposable
{
    /// <summary>Start the engine and wait for readiness.</summary>
    Task StartAsync(CancellationToken ct = default);

    /// <summary>Load a bundle from the configured bundle store.</summary>
    Task LoadBundleAsync(string bundleId, CancellationToken ct = default);

    /// <summary>Execute a transformation against a payload.</summary>
    Task<TransformResult> TransformAsync(
        byte[] input,
        string transformationId,
        string contentType = "application/json",
        IDictionary<string, string>? parameters = null,
        string? correlationId = null,
        CancellationToken ct = default);

    /// <summary>Execute a transformation with multiple named inputs.</summary>
    Task<TransformResult> TransformMultiAsync(
        string transformationId,
        IDictionary<string, byte[]> namedInputs,
        string contentType = "application/json",
        IDictionary<string, string>? parameters = null,
        string? correlationId = null,
        CancellationToken ct = default);

    /// <summary>Query engine health.</summary>
    Task<HealthResult> HealthAsync(CancellationToken ct = default);
}
