using System.Collections.Concurrent;
using System.Threading.Channels;
using Google.Protobuf;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Utlxe.V1;

namespace Glomidco.Utlx;

/// <summary>
/// Client for UTL-X Engine (UTLXe). Spawns UTLXe as a long-running subprocess
/// and communicates via varint-delimited protobuf over stdin/stdout (stdio-proto mode).
/// Thread-safe for concurrent Execute calls (correlation ID-based response matching).
/// Keeps the JVM alive for the lifetime of this client.
/// </summary>
public sealed class UtlxeClient : IAsyncDisposable, IDisposable
{
    private readonly UtlxeClientOptions _options;
    private readonly ILogger _logger;
    private UtlxeProcess? _process;
    private readonly SemaphoreSlim _writeLock = new(1, 1);
    // Sequential channel for init-time messages (Load, Unload, Health) — ordered, one at a time
    private readonly Channel<StdioEnvelope> _sequentialChannel = Channel.CreateUnbounded<StdioEnvelope>();
    // Correlation-based dispatch for Execute/ExecuteBatch — supports concurrent out-of-order responses
    private readonly ConcurrentDictionary<string, TaskCompletionSource<StdioEnvelope>> _pendingRequests = new();
    private long _correlationCounter;
    private Task? _readerTask;
    private bool _started;

    public UtlxeClient(UtlxeClientOptions options)
    {
        _options = options ?? throw new ArgumentNullException(nameof(options));
        _logger = options.LoggerFactory?.CreateLogger<UtlxeClient>() ?? NullLogger<UtlxeClient>.Instance;
    }

    /// <summary>
    /// Start the UTLXe subprocess and wait until it is ready.
    /// Sends a HealthRequest and waits for HealthResponse to confirm the JVM has started.
    /// </summary>
    public async Task StartAsync(CancellationToken ct = default)
    {
        if (_started) throw new InvalidOperationException("Client already started");

        var jarPath = ResolveJarPath();
        _process = new UtlxeProcess(jarPath, _options.JavaHome, _options.Workers, _options.LoggerFactory);
        _process.Start();

        // Start background reader
        _readerTask = Task.Run(() => ReaderLoop(), CancellationToken.None);

        // Wait for engine readiness by sending a health check
        using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(_options.StartupTimeout);

        try
        {
            var health = await HealthAsync(cts.Token);
            _logger.LogInformation("UTLXe ready: state={State}, uptime={Uptime}ms", health.State, health.UptimeMs);
            _started = true;
        }
        catch (OperationCanceledException)
        {
            await DisposeAsync();
            throw new UtlxeException($"UTLXe did not become ready within {_options.StartupTimeout}");
        }
    }

    /// <summary>
    /// Load (compile) a UTL-X transformation. Call once per transformation at init time.
    /// </summary>
    public async Task<LoadTransformationResponse> LoadTransformationAsync(
        string transformationId,
        string utlxSource,
        string strategy = "TEMPLATE",
        CancellationToken ct = default)
    {
        var req = new LoadTransformationRequest
        {
            TransformationId = transformationId,
            UtlxSource = utlxSource,
            Strategy = strategy
        };

        var resp = await SendSequentialAsync(
            MessageType.LoadTransformationRequest, req,
            MessageType.LoadTransformationResponse, ct);

        return LoadTransformationResponse.Parser.ParseFrom(resp.Payload);
    }

    /// <summary>
    /// Execute a pre-loaded transformation against a payload.
    /// Thread-safe — multiple calls can be in flight concurrently when workers &gt; 1.
    /// Responses are matched by correlation ID for out-of-order delivery.
    /// </summary>
    public async Task<ExecuteResponse> ExecuteAsync(
        string transformationId,
        byte[] payload,
        string contentType = "application/json",
        string? correlationId = null,
        CancellationToken ct = default)
    {
        var corrId = correlationId ?? GenerateCorrelationId();
        var req = new ExecuteRequest
        {
            TransformationId = transformationId,
            Payload = ByteString.CopyFrom(payload),
            ContentType = contentType,
            CorrelationId = corrId
        };

        var resp = await SendCorrelatedAsync(
            MessageType.ExecuteRequest, req, corrId, ct);

        return ExecuteResponse.Parser.ParseFrom(resp.Payload);
    }

    /// <summary>
    /// Execute a pre-loaded transformation against multiple payloads in one call.
    /// The entire batch is a single request/response — no correlation ID matching needed.
    /// </summary>
    public async Task<ExecuteBatchResponse> ExecuteBatchAsync(
        string transformationId,
        IReadOnlyList<(byte[] Payload, string ContentType, string CorrelationId)> items,
        CancellationToken ct = default)
    {
        var batchCorrId = GenerateCorrelationId();
        var req = new ExecuteBatchRequest { TransformationId = transformationId };
        foreach (var (payload, contentType, correlationId) in items)
        {
            req.Items.Add(new BatchItem
            {
                Payload = ByteString.CopyFrom(payload),
                ContentType = contentType,
                CorrelationId = correlationId
            });
        }

        var resp = await SendCorrelatedAsync(
            MessageType.ExecuteBatchRequest, req, batchCorrId, ct);

        return ExecuteBatchResponse.Parser.ParseFrom(resp.Payload);
    }

    /// <summary>
    /// Unload a previously loaded transformation.
    /// </summary>
    public async Task<UnloadTransformationResponse> UnloadTransformationAsync(
        string transformationId,
        CancellationToken ct = default)
    {
        var req = new UnloadTransformationRequest { TransformationId = transformationId };

        var resp = await SendSequentialAsync(
            MessageType.UnloadTransformationRequest, req,
            MessageType.UnloadTransformationResponse, ct);

        return UnloadTransformationResponse.Parser.ParseFrom(resp.Payload);
    }

    /// <summary>
    /// Query engine health and statistics.
    /// </summary>
    public async Task<HealthResponse> HealthAsync(CancellationToken ct = default)
    {
        var req = new HealthRequest();

        var resp = await SendSequentialAsync(
            MessageType.HealthRequest, req,
            MessageType.HealthResponse, ct);

        return HealthResponse.Parser.ParseFrom(resp.Payload);
    }

    // =========================================================================
    // Internal: Send/Receive
    // =========================================================================

    private string GenerateCorrelationId() =>
        $"utlx-{Interlocked.Increment(ref _correlationCounter)}";

    /// <summary>
    /// Send a sequential request (Load, Unload, Health) — responses arrive in order.
    /// These are init/lifecycle messages that must not be interleaved with concurrent Execute calls.
    /// </summary>
    private async Task<StdioEnvelope> SendSequentialAsync(
        MessageType requestType, IMessage request,
        MessageType expectedResponseType, CancellationToken ct)
    {
        if (_process == null || !_process.IsRunning)
            throw new UtlxeException("UTLXe process is not running");

        var envelope = new StdioEnvelope
        {
            Type = requestType,
            Payload = request.ToByteString()
        };

        await _writeLock.WaitAsync(ct);
        try
        {
            await WriteEnvelopeAsync(envelope, ct);
        }
        finally
        {
            _writeLock.Release();
        }

        // Sequential messages go through the ordered channel
        var response = await _sequentialChannel.Reader.ReadAsync(ct);
        return response;
    }

    /// <summary>
    /// Send a correlated request (Execute, ExecuteBatch) — responses matched by correlation ID.
    /// Multiple calls can be in flight concurrently; out-of-order responses are dispatched correctly.
    /// </summary>
    private async Task<StdioEnvelope> SendCorrelatedAsync(
        MessageType requestType, IMessage request,
        string correlationId, CancellationToken ct)
    {
        if (_process == null || !_process.IsRunning)
            throw new UtlxeException("UTLXe process is not running");

        var tcs = new TaskCompletionSource<StdioEnvelope>(TaskCreationOptions.RunContinuationsAsynchronously);
        _pendingRequests[correlationId] = tcs;

        var envelope = new StdioEnvelope
        {
            Type = requestType,
            Payload = request.ToByteString()
        };

        try
        {
            await _writeLock.WaitAsync(ct);
            try
            {
                await WriteEnvelopeAsync(envelope, ct);
            }
            finally
            {
                _writeLock.Release();
            }

            // Wait for the reader to dispatch our response by correlation ID
            using var ctr = ct.Register(() => tcs.TrySetCanceled(ct));
            return await tcs.Task;
        }
        finally
        {
            _pendingRequests.TryRemove(correlationId, out _);
        }
    }

    private async Task WriteEnvelopeAsync(StdioEnvelope envelope, CancellationToken ct)
    {
        var stream = _process!.InputStream;
        var bytes = envelope.ToByteArray();
        VarintCodec.WriteVarint32(stream, bytes.Length);
        await stream.WriteAsync(bytes, ct);
        await stream.FlushAsync(ct);
    }

    /// <summary>
    /// Extract correlation ID from an ExecuteResponse or ExecuteBatchResponse payload.
    /// </summary>
    private string? ExtractCorrelationId(StdioEnvelope envelope)
    {
        try
        {
            if (envelope.Type == MessageType.ExecuteResponse)
            {
                var resp = ExecuteResponse.Parser.ParseFrom(envelope.Payload);
                return string.IsNullOrEmpty(resp.CorrelationId) ? null : resp.CorrelationId;
            }
        }
        catch
        {
            // Can't parse — fall through
        }
        return null;
    }

    private async Task ReaderLoop()
    {
        try
        {
            var stream = _process!.OutputStream;
            while (_process.IsRunning)
            {
                var length = VarintCodec.ReadVarint32(stream);
                if (length < 0) break; // EOF

                var buffer = new byte[length];
                int read = 0;
                while (read < length)
                {
                    var n = await stream.ReadAsync(buffer.AsMemory(read, length - read));
                    if (n == 0) break; // EOF mid-message
                    read += n;
                }

                if (read < length) break; // incomplete

                var envelope = StdioEnvelope.Parser.ParseFrom(buffer);

                // Route response: correlated (Execute) or sequential (Load/Unload/Health)
                if (envelope.Type == MessageType.ExecuteResponse ||
                    envelope.Type == MessageType.ExecuteBatchResponse)
                {
                    var corrId = ExtractCorrelationId(envelope);
                    if (corrId != null && _pendingRequests.TryRemove(corrId, out var tcs))
                    {
                        tcs.TrySetResult(envelope);
                    }
                    else
                    {
                        // No matching pending request — could be batch response or fallback
                        // Route to sequential channel as fallback
                        await _sequentialChannel.Writer.WriteAsync(envelope);
                    }
                }
                else
                {
                    // Init-time responses (Load, Unload, Health) — sequential
                    await _sequentialChannel.Writer.WriteAsync(envelope);
                }
            }
        }
        catch (Exception ex) when (_process?.IsRunning != true)
        {
            _logger.LogDebug("Reader loop ended (process exited): {Message}", ex.Message);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Reader loop error");
        }
        finally
        {
            _sequentialChannel.Writer.TryComplete();
            // Fault all pending requests
            foreach (var kvp in _pendingRequests)
            {
                kvp.Value.TrySetException(new UtlxeException("UTLXe process exited"));
            }
            _pendingRequests.Clear();
        }
    }

    // =========================================================================
    // JAR Resolution
    // =========================================================================

    private string ResolveJarPath()
    {
        // 1. Explicit path in options
        if (_options.JarPath != null)
        {
            if (!File.Exists(_options.JarPath))
                throw new FileNotFoundException($"UTLXe JAR not found: {_options.JarPath}");
            return _options.JarPath;
        }

        // 2. UTLXE_JAR_PATH environment variable
        var envPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH");
        if (envPath != null && File.Exists(envPath))
            return envPath;

        // 3. Sibling to calling assembly
        var assemblyDir = Path.GetDirectoryName(typeof(UtlxeClient).Assembly.Location) ?? ".";
        var siblingJar = Path.Combine(assemblyDir, "utlxe.jar");
        if (File.Exists(siblingJar))
            return siblingJar;

        throw new FileNotFoundException(
            "UTLXe JAR not found. Set UtlxeClientOptions.JarPath, UTLXE_JAR_PATH env var, " +
            "or place utlxe.jar next to the application assembly.");
    }

    // =========================================================================
    // Disposal
    // =========================================================================

    public async ValueTask DisposeAsync()
    {
        if (_process != null)
        {
            await _process.DisposeAsync();
            _process = null;
        }
        if (_readerTask != null)
        {
            try { await _readerTask.WaitAsync(TimeSpan.FromSeconds(2)); }
            catch { /* ignore */ }
        }
        _writeLock.Dispose();
    }

    public void Dispose() => DisposeAsync().AsTask().GetAwaiter().GetResult();
}

/// <summary>
/// Configuration options for UtlxeClient.
/// </summary>
public record UtlxeClientOptions
{
    /// <summary>Path to the utlxe JAR file. If null, resolves from UTLXE_JAR_PATH env or assembly sibling.</summary>
    public string? JarPath { get; init; }

    /// <summary>Path to Java home directory. Falls back to JAVA_HOME env, then 'java' on PATH.</summary>
    public string? JavaHome { get; init; }

    /// <summary>Number of worker threads in UTLXe. Default 1 (sequential).</summary>
    public int Workers { get; init; } = 1;

    /// <summary>Timeout for UTLXe JVM startup. Default 30 seconds.</summary>
    public TimeSpan StartupTimeout { get; init; } = TimeSpan.FromSeconds(30);

    /// <summary>Logger factory for diagnostics. Optional.</summary>
    public ILoggerFactory? LoggerFactory { get; init; }
}
