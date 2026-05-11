using Glomidco.Utlx.Engine;
using Microsoft.Extensions.DependencyInjection;

namespace Glomidco.Utlx.LogicApps;

/// <summary>
/// DI registration for UTLXe in Logic Apps Standard (or any ASP.NET Core host).
/// </summary>
public static class ServiceCollectionExtensions
{
    /// <summary>
    /// Register UTLXe as a singleton IUtlxEngine.
    /// The engine is started on first resolution and disposed with the host.
    /// </summary>
    public static IServiceCollection AddUtlxEngine(
        this IServiceCollection services,
        Action<UtlxEngineOptions> configure)
    {
        var options = new UtlxEngineOptions();
        configure(options);

        services.AddSingleton<IUtlxEngine>(sp =>
        {
            var engine = new UtlxEngine(options);
            engine.StartAsync().GetAwaiter().GetResult();
            return engine;
        });

        return services;
    }

    /// <summary>
    /// Register UTLXe with pre-built options.
    /// </summary>
    public static IServiceCollection AddUtlxEngine(
        this IServiceCollection services,
        UtlxEngineOptions options)
    {
        services.AddSingleton<IUtlxEngine>(sp =>
        {
            var engine = new UtlxEngine(options);
            engine.StartAsync().GetAwaiter().GetResult();
            return engine;
        });

        return services;
    }
}
