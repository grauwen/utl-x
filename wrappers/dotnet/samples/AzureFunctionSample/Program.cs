using Glomidco.Utlx;
using Microsoft.Azure.Functions.Worker;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

var host = new HostBuilder()
    .ConfigureFunctionsWebApplication()
    .ConfigureServices(services =>
    {
        // Register UtlxeClient as a singleton — keeps the JVM alive for the function app lifetime.
        // The JAR path is resolved from UTLXE_JAR_PATH environment variable or app settings.
        services.AddSingleton(sp =>
        {
            var loggerFactory = sp.GetRequiredService<ILoggerFactory>();
            var jarPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH")
                ?? throw new InvalidOperationException(
                    "UTLXE_JAR_PATH environment variable not set. " +
                    "Set it to the path of the utlxe JAR file.");

            var client = new UtlxeClient(new UtlxeClientOptions
            {
                JarPath = jarPath,
                Workers = 1,
                LoggerFactory = loggerFactory,
                StartupTimeout = TimeSpan.FromSeconds(30)
            });

            // Start synchronously during DI — JVM must be ready before handling requests
            client.StartAsync().GetAwaiter().GetResult();
            return client;
        });
    })
    .Build();

host.Run();
