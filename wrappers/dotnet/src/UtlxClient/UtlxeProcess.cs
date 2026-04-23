using System.Diagnostics;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Glomidco.Utlx;

/// <summary>
/// Manages the UTLXe JVM subprocess lifecycle.
/// Spawns: java -jar utlxe.jar --mode stdio-proto --workers N
/// </summary>
internal sealed class UtlxeProcess : IAsyncDisposable, IDisposable
{
    private readonly string _jarPath;
    private readonly string? _javaHome;
    private readonly int _workers;
    private readonly ILogger _logger;
    private Process? _process;
    private Task? _stderrTask;

    public Stream InputStream => _process?.StandardInput.BaseStream
        ?? throw new InvalidOperationException("Process not started");

    public Stream OutputStream => _process?.StandardOutput.BaseStream
        ?? throw new InvalidOperationException("Process not started");

    public bool IsRunning => _process is { HasExited: false };

    public UtlxeProcess(string jarPath, string? javaHome, int workers, ILoggerFactory? loggerFactory)
    {
        _jarPath = jarPath;
        _javaHome = javaHome;
        _workers = Math.Max(1, workers);
        _logger = loggerFactory?.CreateLogger<UtlxeProcess>() ?? NullLogger<UtlxeProcess>.Instance;
    }

    public void Start()
    {
        var javaExe = ResolveJava();

        _logger.LogInformation("Starting UTLXe: {Java} -jar {Jar} --mode stdio-proto --workers {Workers}",
            javaExe, _jarPath, _workers);

        var psi = new ProcessStartInfo
        {
            FileName = javaExe,
            Arguments = $"-jar \"{_jarPath}\" --mode stdio-proto --workers {_workers}",
            UseShellExecute = false,
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        };

        _process = Process.Start(psi)
            ?? throw new UtlxeException("Failed to start UTLXe process");

        // Pipe stderr to logger on background thread
        _stderrTask = Task.Run(async () =>
        {
            try
            {
                while (await _process.StandardError.ReadLineAsync() is { } line)
                {
                    _logger.LogDebug("[UTLXe] {Line}", line);
                }
            }
            catch (Exception) when (!IsRunning)
            {
                // Process exited, ignore
            }
        });

        _logger.LogInformation("UTLXe process started (PID {Pid})", _process.Id);
    }

    private string ResolveJava()
    {
        // 1. Explicit JavaHome
        if (_javaHome != null)
        {
            var path = Path.Combine(_javaHome, "bin", "java");
            if (File.Exists(path)) return path;
        }

        // 2. JAVA_HOME environment variable
        var javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (javaHome != null)
        {
            var path = Path.Combine(javaHome, "bin", "java");
            if (File.Exists(path)) return path;
        }

        // 3. Fall back to java on PATH
        return "java";
    }

    public async ValueTask DisposeAsync()
    {
        if (_process == null) return;

        if (!_process.HasExited)
        {
            _logger.LogInformation("Stopping UTLXe process (PID {Pid})...", _process.Id);
            try
            {
                // Close stdin to signal EOF — UTLXe exits cleanly on pipe close
                _process.StandardInput.Close();
                var exited = _process.WaitForExit(5000);
                if (!exited)
                {
                    _logger.LogWarning("UTLXe did not exit gracefully, killing");
                    _process.Kill(entireProcessTree: true);
                }
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error stopping UTLXe process");
            }
        }

        if (_stderrTask != null)
        {
            try { await _stderrTask.WaitAsync(TimeSpan.FromSeconds(2)); }
            catch { /* ignore */ }
        }

        _process.Dispose();
        _process = null;
    }

    public void Dispose() => DisposeAsync().AsTask().GetAwaiter().GetResult();
}
