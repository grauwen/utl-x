using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Glomidco.Utlx;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Azure.Functions.Worker.Http;
using Microsoft.Extensions.Logging;

namespace UtlxAzureFunction;

/// <summary>
/// Azure Function that exposes UTL-X transformations via HTTP.
///
/// POST /api/transform
/// {
///   "transformationId": "my-transform",
///   "utlxSource": "%utlx 1.0\ninput json\noutput json\n---\n{name: $input.name}",
///   "payload": "{\"name\": \"Alice\", \"age\": 30}",
///   "contentType": "application/json"
/// }
///
/// The function loads the transformation on first use and caches it for subsequent calls.
/// </summary>
public class TransformFunction
{
    private readonly UtlxeClient _client;
    private readonly ILogger<TransformFunction> _logger;
    private readonly HashSet<string> _loadedTransformations = new();
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    public TransformFunction(UtlxeClient client, ILogger<TransformFunction> logger)
    {
        _client = client;
        _logger = logger;
    }

    [Function("Transform")]
    public async Task<HttpResponseData> Run(
        [HttpTrigger(AuthorizationLevel.Function, "post", Route = "transform")] HttpRequestData req)
    {
        TransformRequest? body;
        try
        {
            body = await JsonSerializer.DeserializeAsync<TransformRequest>(req.Body, JsonOptions);
        }
        catch (JsonException ex)
        {
            return await ErrorResponse(req, HttpStatusCode.BadRequest, $"Invalid JSON: {ex.Message}");
        }

        if (body == null || string.IsNullOrEmpty(body.TransformationId) || string.IsNullOrEmpty(body.UtlxSource))
        {
            return await ErrorResponse(req, HttpStatusCode.BadRequest,
                "Required fields: transformationId, utlxSource, payload");
        }

        try
        {
            // Load transformation if not already loaded
            if (!_loadedTransformations.Contains(body.TransformationId))
            {
                var loadResp = await _client.LoadTransformationAsync(
                    body.TransformationId,
                    body.UtlxSource,
                    body.Strategy ?? "TEMPLATE");

                if (!loadResp.Success)
                {
                    return await ErrorResponse(req, HttpStatusCode.BadRequest,
                        $"Failed to compile transformation: {loadResp.Error}");
                }

                _loadedTransformations.Add(body.TransformationId);
                _logger.LogInformation("Loaded transformation '{Id}' in {Duration}μs",
                    body.TransformationId, loadResp.Metrics?.TotalDurationUs);
            }

            // Execute transformation
            var payload = Encoding.UTF8.GetBytes(body.Payload ?? "{}");
            var result = await _client.ExecuteAsync(
                body.TransformationId,
                payload,
                body.ContentType ?? "application/json");

            if (!result.Success)
            {
                return await ErrorResponse(req, HttpStatusCode.UnprocessableEntity,
                    $"Transformation failed: {result.Error}");
            }

            var response = req.CreateResponse(HttpStatusCode.OK);
            response.Headers.Add("Content-Type", "application/json");
            var responseBody = new TransformResponse
            {
                Output = result.Output.ToStringUtf8(),
                DurationUs = result.Metrics?.ExecuteDurationUs ?? 0
            };
            await response.WriteStringAsync(JsonSerializer.Serialize(responseBody, JsonOptions));
            return response;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Transform error");
            return await ErrorResponse(req, HttpStatusCode.InternalServerError, ex.Message);
        }
    }

    [Function("Health")]
    public async Task<HttpResponseData> Health(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get", Route = "health")] HttpRequestData req)
    {
        var health = await _client.HealthAsync();
        var response = req.CreateResponse(HttpStatusCode.OK);
        response.Headers.Add("Content-Type", "application/json");
        await response.WriteStringAsync(JsonSerializer.Serialize(new
        {
            state = health.State,
            uptimeMs = health.UptimeMs,
            loadedTransformations = health.LoadedTransformations,
            totalExecutions = health.TotalExecutions,
            totalErrors = health.TotalErrors
        }, JsonOptions));
        return response;
    }

    private static async Task<HttpResponseData> ErrorResponse(
        HttpRequestData req, HttpStatusCode status, string message)
    {
        var response = req.CreateResponse(status);
        response.Headers.Add("Content-Type", "application/json");
        await response.WriteStringAsync(JsonSerializer.Serialize(new { error = message }, JsonOptions));
        return response;
    }
}

public record TransformRequest
{
    public string? TransformationId { get; init; }
    public string? UtlxSource { get; init; }
    public string? Payload { get; init; }
    public string? ContentType { get; init; }
    public string? Strategy { get; init; }
}

public record TransformResponse
{
    public string? Output { get; init; }
    public long DurationUs { get; init; }
}
