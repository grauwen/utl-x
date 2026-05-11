using System.Reflection;

namespace Glomidco.Utlx.Engine.BundleStores;

/// <summary>
/// Loads bundles embedded in the deployment package (assembly resources or relative path).
/// For BizTalk MSI or Logic Apps zip deployments where the bundle ships with the application.
/// </summary>
public class EmbeddedBundleStore : IBundleStore
{
    private readonly Assembly _assembly;
    private readonly string? _resourcePrefix;
    private readonly string? _relativePath;

    /// <summary>Load from assembly embedded resources.</summary>
    public EmbeddedBundleStore(Assembly assembly, string resourcePrefix)
    {
        _assembly = assembly;
        _resourcePrefix = resourcePrefix;
    }

    /// <summary>Load from a path relative to the calling assembly's location.</summary>
    public EmbeddedBundleStore(string relativePath)
    {
        _assembly = Assembly.GetCallingAssembly();
        _relativePath = relativePath;
    }

    public Task<BundleData> FetchAsync(string bundleId, CancellationToken ct = default)
    {
        if (_relativePath != null)
        {
            var assemblyDir = Path.GetDirectoryName(_assembly.Location) ?? ".";
            var fullPath = Path.Combine(assemblyDir, _relativePath, $"{bundleId}.zip");
            if (!File.Exists(fullPath))
                fullPath = Path.Combine(assemblyDir, _relativePath, bundleId);
            if (File.Exists(fullPath))
            {
                var bytes = File.ReadAllBytes(fullPath);
                return Task.FromResult(new BundleData(bytes, bundleId, null, null));
            }
            if (Directory.Exists(fullPath))
            {
                using var ms = new MemoryStream();
                System.IO.Compression.ZipFile.CreateFromDirectory(fullPath, ms);
                return Task.FromResult(new BundleData(ms.ToArray(), bundleId, null, null));
            }
            throw new FileNotFoundException($"Embedded bundle not found at {fullPath}");
        }

        // Assembly resource
        var resourceName = $"{_resourcePrefix}.{bundleId}.zip";
        using var stream = _assembly.GetManifestResourceStream(resourceName)
            ?? throw new FileNotFoundException($"Resource not found: {resourceName}");
        using var ms2 = new MemoryStream();
        stream.CopyTo(ms2);
        return Task.FromResult(new BundleData(ms2.ToArray(), bundleId, null, null));
    }

    public Task<BundleVersion> GetVersionAsync(string bundleId, CancellationToken ct = default)
    {
        // Embedded bundles don't change at runtime
        return Task.FromResult(new BundleVersion(bundleId, "embedded", null, null));
    }
}
