using Utlxe.V1;

namespace Glomidco.Utlx;

/// <summary>
/// Exception thrown when a UTLXe operation fails.
/// </summary>
public class UtlxeException : Exception
{
    public ErrorClass ErrorClass { get; }
    public ErrorPhase ErrorPhase { get; }

    public UtlxeException(string message, ErrorClass errorClass = ErrorClass.Unspecified, ErrorPhase errorPhase = ErrorPhase.Unspecified)
        : base(message)
    {
        ErrorClass = errorClass;
        ErrorPhase = errorPhase;
    }

    public UtlxeException(string message, Exception innerException)
        : base(message, innerException)
    {
        ErrorClass = ErrorClass.Unspecified;
        ErrorPhase = ErrorPhase.Internal;
    }
}
