using Glomidco.Utlx.Engine;

namespace Glomidco.Utlx.LogicApps;

/// <summary>
/// Workflow-friendly result type for Logic Apps designer.
/// Flat JSON with string fields — no proto types, no binary, no nested objects.
/// Serializable directly into the Logic Apps action output.
/// </summary>
public class WorkflowActionResult
{
    public bool Success { get; set; }
    public string? Output { get; set; }
    public string? ContentType { get; set; }
    public string? Error { get; set; }
    public string? ErrorCode { get; set; }
    public string? ErrorPhase { get; set; }
    public long DurationUs { get; set; }
    public string? MessageId { get; set; }
    public string? CorrelationId { get; set; }

    // Business metadata (from OutputMetadata)
    public string? ApplicationId { get; set; }
    public string? MessageType { get; set; }

    /// <summary>
    /// Create a WorkflowActionResult from a TransformResult.
    /// </summary>
    public static WorkflowActionResult FromTransformResult(TransformResult result)
    {
        return new WorkflowActionResult
        {
            Success = result.Success,
            Output = result.Output != null ? System.Text.Encoding.UTF8.GetString(result.Output) : null,
            ContentType = result.OutputContentType,
            Error = result.Error,
            ErrorCode = result.ErrorCode,
            ErrorPhase = result.ErrorPhase,
            DurationUs = result.DurationUs,
            MessageId = result.MessageId,
            CorrelationId = result.CorrelationId,
            ApplicationId = result.OutputMetadata?.ApplicationId,
            MessageType = result.OutputMetadata?.MessageType
        };
    }
}
