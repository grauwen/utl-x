namespace Glomidco.Utlx.Engine.BundleStores;

/// <summary>
/// Loads bundles from a running UTLXe Admin API (sidecar pattern).
/// Fetches via GET /admin/bundle/export.
/// </summary>
public class HttpBundleStore : IBundleStore
{
    private readonly HttpClient _http;
    private readonly string _baseUrl;

    /// <param name="baseUrl">UTLXe Admin API base URL (e.g., "http://localhost:8081").</param>
    public HttpBundleStore(string baseUrl, HttpClient? httpClient = null)
    {
        _baseUrl = baseUrl.TrimEnd('/');
        _http = httpClient ?? new HttpClient();
    }

    public async Task<BundleData> FetchAsync(string bundleId, CancellationToken ct = default)
    {
        var resp = await _http.GetAsync($"{_baseUrl}/admin/bundle/export", ct);
        resp.EnsureSuccessStatusCode();
        var bytes = await resp.Content.ReadAsByteArrayAsync(ct);
        return new BundleData(bytes, bundleId, null, null);
    }

    public async Task<BundleVersion> GetVersionAsync(string bundleId, CancellationToken ct = default)
    {
        var resp = await _http.GetAsync($"{_baseUrl}/admin/info", ct);
        resp.EnsureSuccessStatusCode();
        return new BundleVersion(bundleId, null, null, DateTimeOffset.UtcNow);
    }
}
