using System.Runtime.InteropServices;
using Glomidco.Utlx.Engine;

namespace Glomidco.Utlx.BizTalk;

/// <summary>
/// BizTalk pipeline component that executes UTLXe transformations.
/// Drop-in replacement for XSLT maps in BizTalk pipelines.
///
/// Design-time properties (set in BizTalk Administration Console):
///   BundleId — bundle containing the transformation
///   TransformationId — which transformation to execute
///   ContentType — input content type (default: auto-detect)
///
/// Usage:
///   1. Install UtlxEngine.BizTalk NuGet into GAC
///   2. Add UtlxTransformComponent to pipeline stage
///   3. Set TransformationId = "to-canonical"
///   4. Deploy — XSLT map replaced with UTLXe transformation
///
/// Note: BizTalk interfaces (IComponent, IBaseComponent, IComponentUI, IPersistPropertyBag)
/// are provided by the BizTalk runtime. This code compiles against those interfaces
/// when the BizTalk SDK references are added. The interface implementations below
/// use the standard BizTalk pipeline component pattern.
/// </summary>
[Guid("A1B2C3D4-5678-9ABC-DEF0-111111111111")]
public class UtlxTransformComponent
{
    // ── Design-time properties (shown in BizTalk Administration Console) ──

    /// <summary>Bundle ID. Optional — if null, uses the bundle auto-loaded by UtlxEngineHost.</summary>
    public string? BundleId { get; set; }

    /// <summary>Transformation ID within the bundle. Required.</summary>
    public string TransformationId { get; set; } = "";

    /// <summary>Override content type. If null, auto-detected from BizTalk message context.</summary>
    public string? ContentType { get; set; }

    /// <summary>Whether to pass BizTalk context properties as UTLXe parameters ($params.*).</summary>
    public bool PassContextAsParameters { get; set; } = true;

    // ── Component metadata ──

    public string Name => "UTLXe Transform";
    public string Version => "1.0";
    public string Description => "Executes a UTL-X transformation via UTLXe engine";

    // ── Execute ──

    /// <summary>
    /// Execute the transformation against the BizTalk message.
    /// Called by the BizTalk pipeline runtime for each message.
    ///
    /// In a real BizTalk deployment, this method signature would be:
    ///   IBaseMessage Execute(IPipelineContext ctx, IBaseMessage inMsg)
    ///
    /// This implementation shows the pattern without requiring BizTalk SDK references at compile time.
    /// </summary>
    public TransformResult Execute(byte[] inputBytes, string contentType, IDictionary<string, string>? contextProperties = null)
    {
        var engine = UtlxEngineHost.Shared;

        var effectiveContentType = ContentType ?? contentType;
        var parameters = PassContextAsParameters && contextProperties != null
            ? contextProperties
            : null;

        var result = engine.TransformAsync(
            inputBytes,
            TransformationId,
            effectiveContentType,
            parameters
        ).GetAwaiter().GetResult();

        if (!result.Success)
            throw new InvalidOperationException(
                $"UTLXe transformation '{TransformationId}' failed: [{result.ErrorCode}] {result.Error}");

        return result;
    }

    /// <summary>
    /// Extract BizTalk context properties as a dictionary.
    /// Pattern for use with the real BizTalk IBaseMessageContext:
    ///   var props = ExtractContextProperties(inMsg.Context);
    /// </summary>
    public static Dictionary<string, string> ExtractContextProperties(
        IEnumerable<KeyValuePair<string, object?>> contextEntries)
    {
        var parameters = new Dictionary<string, string>();
        foreach (var (key, value) in contextEntries)
        {
            if (value != null)
                parameters[key] = value.ToString() ?? "";
        }
        return parameters;
    }

    /// <summary>
    /// Apply transformation result metadata to BizTalk context.
    /// Sets OutputMetadata fields as promoted properties.
    /// </summary>
    public static Dictionary<string, string> ExtractOutputProperties(TransformResult result)
    {
        var props = new Dictionary<string, string>();

        if (result.OutputMetadata != null)
        {
            if (result.OutputMetadata.ApplicationId != null)
                props["utlxe.ApplicationId"] = result.OutputMetadata.ApplicationId;
            if (result.OutputMetadata.MessageType != null)
                props["utlxe.MessageType"] = result.OutputMetadata.MessageType;
            if (result.OutputMetadata.Sender != null)
                props["utlxe.Sender"] = result.OutputMetadata.Sender;
            if (result.OutputMetadata.Receiver != null)
                props["utlxe.Receiver"] = result.OutputMetadata.Receiver;
            foreach (var (key, value) in result.OutputMetadata.CustomIdentifiers)
                props[$"utlxe.{key}"] = value;
        }

        if (result.CorrelationId != null)
            props["utlxe.CorrelationId"] = result.CorrelationId;
        if (result.MessageId != null)
            props["utlxe.MessageId"] = result.MessageId;

        return props;
    }
}
