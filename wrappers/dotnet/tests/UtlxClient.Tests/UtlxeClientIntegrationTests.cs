using System.Text;
using Xunit;
using Glomidco.Utlx;

namespace Glomidco.Utlx.Tests;

/// <summary>
/// Integration tests that spawn a real UTLXe process.
/// Requires: UTLXE_JAR_PATH environment variable pointing to the built utlxe JAR,
/// or the JAR at ../../modules/engine/build/libs/utlxe-*.jar relative to the repo root.
/// </summary>
[Collection("UTLXe")]
public class UtlxeClientIntegrationTests : IAsyncLifetime
{
    private UtlxeClient _client = null!;

    private const string IdentityUtlx = "%utlx 1.0\ninput json\noutput json\n---\n$input\n";

    public async Task InitializeAsync()
    {
        var jarPath = FindJarPath();
        if (jarPath == null)
        {
            throw new SkipException("UTLXe JAR not found. Build with: ./gradlew :modules:engine:jar");
        }

        _client = new UtlxeClient(new UtlxeClientOptions
        {
            JarPath = jarPath,
            Workers = 1
        });
        await _client.StartAsync();
    }

    public async Task DisposeAsync()
    {
        await _client.DisposeAsync();
    }

    [Fact]
    public async Task Health_ReturnsEngineState()
    {
        var health = await _client.HealthAsync();

        Assert.NotEmpty(health.State);
        Assert.True(health.UptimeMs >= 0);
    }

    [Fact]
    public async Task LoadTransformation_Succeeds()
    {
        var resp = await _client.LoadTransformationAsync("test-load", IdentityUtlx);

        Assert.True(resp.Success, $"Load failed: {resp.Error}");
        Assert.True(resp.Metrics.TotalDurationUs > 0);
    }

    [Fact]
    public async Task LoadAndExecute_ReturnsTransformedOutput()
    {
        await _client.LoadTransformationAsync("exec-test", IdentityUtlx);

        var result = await _client.ExecuteAsync(
            "exec-test",
            Encoding.UTF8.GetBytes("""{"name": "Alice", "age": 30}"""),
            "application/json",
            "corr-001"
        );

        Assert.True(result.Success, $"Execute failed: {result.Error}");
        Assert.Equal("corr-001", result.CorrelationId);

        var output = result.Output.ToStringUtf8();
        Assert.Contains("Alice", output);
    }

    [Fact]
    public async Task Execute_UnknownTransformation_ReturnsError()
    {
        var result = await _client.ExecuteAsync(
            "nonexistent",
            Encoding.UTF8.GetBytes("{}"),
            "application/json"
        );

        Assert.False(result.Success);
        Assert.Contains("nonexistent", result.Error);
    }

    [Fact]
    public async Task LoadTransformation_InvalidSource_Fails()
    {
        var resp = await _client.LoadTransformationAsync("bad-src", "this is not valid utlx");

        Assert.False(resp.Success);
        Assert.NotEmpty(resp.Error);
    }

    [Fact]
    public async Task ExecuteBatch_ProcessesMultipleItems()
    {
        await _client.LoadTransformationAsync("batch-test", IdentityUtlx);

        var items = new List<(byte[], string, string)>
        {
            (Encoding.UTF8.GetBytes("""{"i": 1}"""), "application/json", "b-1"),
            (Encoding.UTF8.GetBytes("""{"i": 2}"""), "application/json", "b-2"),
            (Encoding.UTF8.GetBytes("""{"i": 3}"""), "application/json", "b-3")
        };

        var result = await _client.ExecuteBatchAsync("batch-test", items);

        Assert.Equal(3, result.Results.Count);
        Assert.All(result.Results, r => Assert.True(r.Success));
        Assert.Equal("b-1", result.Results[0].CorrelationId);
        Assert.Equal("b-3", result.Results[2].CorrelationId);
    }

    [Fact]
    public async Task UnloadTransformation_Succeeds()
    {
        await _client.LoadTransformationAsync("to-unload", IdentityUtlx);
        var resp = await _client.UnloadTransformationAsync("to-unload");

        Assert.True(resp.Success);
    }

    [Fact]
    public async Task CopyStrategy_LoadAndExecute()
    {
        var utlx = "%utlx 1.0\ninput json\noutput json\n---\n{name: concat($input.first, \" \", $input.last)}\n";
        var load = await _client.LoadTransformationAsync("copy-test", utlx, strategy: "COPY");
        Assert.True(load.Success, $"COPY load failed: {load.Error}");

        var result = await _client.ExecuteAsync(
            "copy-test",
            Encoding.UTF8.GetBytes("""{"first": "Marcel", "last": "Grauwen"}"""));
        Assert.True(result.Success, $"COPY execute failed: {result.Error}");
        Assert.Contains("Marcel Grauwen", result.Output.ToStringUtf8());
    }

    [Fact]
    public async Task CopyStrategy_MultipleExecutions_Reuses_Skeleton()
    {
        var utlx = "%utlx 1.0\ninput json\noutput json\n---\n{id: $input.id, done: true}\n";
        await _client.LoadTransformationAsync("copy-reuse", utlx, strategy: "COPY");

        for (int i = 1; i <= 5; i++)
        {
            var result = await _client.ExecuteAsync(
                "copy-reuse",
                Encoding.UTF8.GetBytes($"{{\"id\": {i}}}"));
            Assert.True(result.Success);
            Assert.Contains($"{i}", result.Output.ToStringUtf8());
        }
    }

    [Fact]
    public async Task FullLifecycle_LoadExecuteHealthUnload()
    {
        // Load
        var load = await _client.LoadTransformationAsync("lifecycle", IdentityUtlx);
        Assert.True(load.Success);

        // Execute
        var exec = await _client.ExecuteAsync(
            "lifecycle",
            Encoding.UTF8.GetBytes("""{"test": true}"""));
        Assert.True(exec.Success);

        // Health
        var health = await _client.HealthAsync();
        Assert.True(health.LoadedTransformations >= 1);

        // Unload
        var unload = await _client.UnloadTransformationAsync("lifecycle");
        Assert.True(unload.Success);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static string? FindJarPath()
    {
        // 1. Environment variable
        var envPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH");
        if (envPath != null && File.Exists(envPath)) return envPath;

        // 2. Build output relative to repo root
        var repoRoot = FindRepoRoot();
        if (repoRoot != null)
        {
            var buildDir = Path.Combine(repoRoot, "modules", "engine", "build", "libs");
            if (Directory.Exists(buildDir))
            {
                var jar = Directory.GetFiles(buildDir, "utlxe-*.jar").FirstOrDefault();
                if (jar != null) return jar;
            }
        }

        return null;
    }

    private static string? FindRepoRoot()
    {
        var dir = Directory.GetCurrentDirectory();
        while (dir != null)
        {
            if (File.Exists(Path.Combine(dir, "settings.gradle.kts"))) return dir;
            dir = Path.GetDirectoryName(dir);
        }
        // Try from test assembly location
        dir = Path.GetDirectoryName(typeof(UtlxeClientIntegrationTests).Assembly.Location);
        for (int i = 0; i < 10 && dir != null; i++)
        {
            if (File.Exists(Path.Combine(dir, "settings.gradle.kts"))) return dir;
            dir = Path.GetDirectoryName(dir);
        }
        return null;
    }
}

/// <summary>
/// xUnit skip exception — tests that throw this are marked as skipped, not failed.
/// </summary>
public class SkipException : Exception
{
    public SkipException(string message) : base(message) { }
}
