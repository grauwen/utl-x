namespace Glomidco.Utlx.Engine;

/// <summary>
/// Result of a transformation execution. Hides proto types — all fields are plain .NET types.
/// </summary>
public class TransformResult
{
    public bool Success { get; init; }
    public byte[]? Output { get; init; }
    public string? OutputContentType { get; init; }
    public string? Error { get; init; }
    public string? ErrorClass { get; init; }
    public string? ErrorPhase { get; init; }
    public string? ErrorCode { get; init; }
    public List<TransformValidationError> ValidationErrors { get; init; } = [];
    public long DurationUs { get; init; }

    // MPPM triad
    public string? MessageId { get; init; }
    public string? CorrelationId { get; init; }
    public string? CausationId { get; init; }

    // Forwarded metadata
    public Dictionary<string, string> Metadata { get; init; } = [];

    // Rule-emitted business metadata
    public OutputMetadataResult? OutputMetadata { get; set; }
}

public class TransformValidationError
{
    public string Message { get; init; } = "";
    public string? Path { get; init; }
    public string Severity { get; init; } = "ERROR";
}

public class OutputMetadataResult
{
    public string? ApplicationId { get; init; }
    public string? MessageType { get; init; }
    public string? CustomStatus { get; init; }
    public Dictionary<string, string> CustomIdentifiers { get; init; } = [];
    public string? Sender { get; init; }
    public string? Receiver { get; init; }
}

public class HealthResult
{
    public string State { get; init; } = "";
    public long UptimeMs { get; init; }
    public int LoadedTransformations { get; init; }
    public long TotalExecutions { get; init; }
    public long TotalErrors { get; init; }
}
