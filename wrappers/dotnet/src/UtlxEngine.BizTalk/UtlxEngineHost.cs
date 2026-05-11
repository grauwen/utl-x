using Glomidco.Utlx.Engine;
using Glomidco.Utlx.Engine.BundleStores;

namespace Glomidco.Utlx.BizTalk;

/// <summary>
/// Process-wide singleton for the UTLXe engine.
/// BizTalk pipeline components share one engine instance across the host process.
/// Initialized lazily on first use; shutdown on AppDomain unload.
/// </summary>
public static class UtlxEngineHost
{
    private static readonly object Lock = new();
    private static UtlxEngine? _engine;
    private static bool _initialized;

    /// <summary>
    /// Get the shared engine instance. Initializes on first call.
    /// Configuration is read from environment variables:
    ///   UTLXE_JAR_PATH — path to utlxe.jar or native binary
    ///   UTLXE_BUNDLE_PATH — path to bundle directory (optional)
    ///   UTLXE_BUNDLE_ID — bundle ID to auto-load (optional)
    ///   UTLXE_WORKERS — worker count (default: 1)
    /// </summary>
    public static UtlxEngine Shared
    {
        get
        {
            if (_initialized && _engine != null) return _engine;
            lock (Lock)
            {
                if (_initialized && _engine != null) return _engine;

                var jarPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH");
                var bundlePath = Environment.GetEnvironmentVariable("UTLXE_BUNDLE_PATH");
                var bundleId = Environment.GetEnvironmentVariable("UTLXE_BUNDLE_ID");
                var workers = int.TryParse(Environment.GetEnvironmentVariable("UTLXE_WORKERS"), out var w) ? w : 1;

                IBundleStore? store = null;
                if (bundlePath != null)
                    store = new FileBundleStore(bundlePath);

                _engine = new UtlxEngine(new UtlxEngineOptions
                {
                    JarPath = jarPath,
                    Workers = workers,
                    BundleStore = store,
                    BundleId = bundleId
                });

                _engine.StartAsync().GetAwaiter().GetResult();
                _initialized = true;

                AppDomain.CurrentDomain.DomainUnload += (_, _) =>
                {
                    _engine?.Dispose();
                    _engine = null;
                };

                return _engine;
            }
        }
    }

    /// <summary>
    /// Initialize with explicit options. Call once before any pipeline component uses the engine.
    /// Optional — if not called, Shared initializes from environment variables.
    /// </summary>
    public static void Initialize(UtlxEngineOptions options)
    {
        lock (Lock)
        {
            if (_initialized)
                throw new InvalidOperationException("UtlxEngineHost already initialized");

            _engine = new UtlxEngine(options);
            _engine.StartAsync().GetAwaiter().GetResult();
            _initialized = true;
        }
    }
}
