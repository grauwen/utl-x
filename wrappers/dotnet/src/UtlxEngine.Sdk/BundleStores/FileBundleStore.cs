namespace Glomidco.Utlx.Engine.BundleStores;

/// <summary>
/// Loads bundles from the local filesystem. For development and testing.
/// </summary>
public class FileBundleStore : IBundleStore
{
    private readonly string _basePath;

    /// <param name="basePath">Directory containing bundle directories or ZIP files.</param>
    public FileBundleStore(string basePath)
    {
        _basePath = basePath ?? throw new ArgumentNullException(nameof(basePath));
    }

    public Task<BundleData> FetchAsync(string bundleId, CancellationToken ct = default)
    {
        var zipPath = Path.Combine(_basePath, $"{bundleId}.zip");
        if (File.Exists(zipPath))
        {
            var bytes = File.ReadAllBytes(zipPath);
            return Task.FromResult(new BundleData(bytes, bundleId, null, null));
        }

        var dirPath = Path.Combine(_basePath, bundleId);
        if (Directory.Exists(dirPath))
        {
            // Bundle is a directory — create ZIP in memory
            using var ms = new MemoryStream();
            System.IO.Compression.ZipFile.CreateFromDirectory(dirPath, ms);
            return Task.FromResult(new BundleData(ms.ToArray(), bundleId, null, null));
        }

        throw new FileNotFoundException($"Bundle not found: {bundleId} (looked in {_basePath})");
    }

    public Task<BundleVersion> GetVersionAsync(string bundleId, CancellationToken ct = default)
    {
        var zipPath = Path.Combine(_basePath, $"{bundleId}.zip");
        if (File.Exists(zipPath))
        {
            var info = new FileInfo(zipPath);
            return Task.FromResult(new BundleVersion(bundleId, null, null, info.LastWriteTimeUtc));
        }

        var dirPath = Path.Combine(_basePath, bundleId);
        if (Directory.Exists(dirPath))
        {
            var info = new DirectoryInfo(dirPath);
            return Task.FromResult(new BundleVersion(bundleId, null, null, info.LastWriteTimeUtc));
        }

        throw new FileNotFoundException($"Bundle not found: {bundleId}");
    }
}
