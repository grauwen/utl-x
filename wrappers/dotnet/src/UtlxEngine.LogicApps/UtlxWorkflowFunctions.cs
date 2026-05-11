using Glomidco.Utlx.Engine;

namespace Glomidco.Utlx.LogicApps;

/// <summary>
/// Sample custom functions for Logic Apps Standard workflows.
/// Inject IUtlxEngine via DI (registered by AddUtlxEngine).
///
/// Usage in a Logic Apps Standard custom function project:
///
///   [FunctionName("TransformJson")]
///   public async Task&lt;WorkflowActionResult&gt; TransformJson(
///       [WorkflowActionTrigger] string inputJson,
///       string transformationId)
///   {
///       return await _functions.TransformJsonAsync(inputJson, transformationId);
///   }
/// </summary>
public class UtlxWorkflowFunctions
{
    private readonly IUtlxEngine _engine;

    public UtlxWorkflowFunctions(IUtlxEngine engine)
    {
        _engine = engine;
    }

    /// <summary>Transform JSON input.</summary>
    public async Task<WorkflowActionResult> TransformJsonAsync(
        string inputJson, string transformationId, CancellationToken ct = default)
    {
        var result = await _engine.TransformAsync(
            System.Text.Encoding.UTF8.GetBytes(inputJson),
            transformationId,
            "application/json",
            ct: ct);

        return WorkflowActionResult.FromTransformResult(result);
    }

    /// <summary>Transform XML input.</summary>
    public async Task<WorkflowActionResult> TransformXmlAsync(
        string inputXml, string transformationId, CancellationToken ct = default)
    {
        var result = await _engine.TransformAsync(
            System.Text.Encoding.UTF8.GetBytes(inputXml),
            transformationId,
            "application/xml",
            ct: ct);

        return WorkflowActionResult.FromTransformResult(result);
    }

    /// <summary>Transform with auto-detected content type.</summary>
    public async Task<WorkflowActionResult> TransformAsync(
        byte[] input, string transformationId, string contentType,
        IDictionary<string, string>? parameters = null,
        string? correlationId = null,
        CancellationToken ct = default)
    {
        var result = await _engine.TransformAsync(
            input, transformationId, contentType, parameters, correlationId, ct);

        return WorkflowActionResult.FromTransformResult(result);
    }
}
