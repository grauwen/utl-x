using System.Text;
using Glomidco.Utlx;

// ============================================================
// UTL-X .NET Demo — Real transformation via UTLXe subprocess
// ============================================================

var jarPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH")
    ?? throw new Exception("Set UTLXE_JAR_PATH to the utlxe JAR path");

Console.WriteLine("=== UTL-X .NET Integration Demo ===\n");

// 1. Start the UTLXe engine
Console.WriteLine("Starting UTLXe engine...");
await using var client = new UtlxeClient(new UtlxeClientOptions { JarPath = jarPath });
await client.StartAsync();

var health = await client.HealthAsync();
Console.WriteLine($"Engine ready: state={health.State}, uptime={health.UptimeMs}ms\n");

// ── Demo 1: JSON restructuring ──
Console.WriteLine("── Demo 1: JSON Restructuring ──");

var utlxSource1 = """
    %utlx 1.0
    input json
    output json
    ---
    {
      fullName: concat($input.firstName, " ", $input.lastName),
      email: lowerCase($input.email),
      age: $input.age,
      isAdult: $input.age >= 18
    }
    """;

var load1 = await client.LoadTransformationAsync("person-transform", utlxSource1);
Console.WriteLine($"Loaded 'person-transform' in {load1.Metrics.TotalDurationUs}μs");

var input1 = """{"firstName": "Marcel", "lastName": "Grauwen", "email": "MARCEL@GLOMIDCO.COM", "age": 45}""";
Console.WriteLine($"Input:  {input1}");

var result1 = await client.ExecuteAsync("person-transform", Encoding.UTF8.GetBytes(input1));
Console.WriteLine($"Output: {result1.Output.ToStringUtf8()}");
Console.WriteLine($"Duration: {result1.Metrics.ExecuteDurationUs}μs\n");

// ── Demo 2: JSON to XML ──
Console.WriteLine("── Demo 2: JSON → XML ──");

var utlxSource2 = """
    %utlx 1.0
    input json
    output xml
    ---
    {
      order: {
        id: $input.orderId,
        customer: $input.customerName,
        total: $input.amount
      }
    }
    """;

var load2 = await client.LoadTransformationAsync("json-to-xml", utlxSource2);
Console.WriteLine($"Loaded 'json-to-xml' in {load2.Metrics.TotalDurationUs}μs");

var input2 = """{"orderId": "ORD-2026-001", "customerName": "Contoso Ltd", "amount": 1499.99}""";
Console.WriteLine($"Input:  {input2}");

var result2 = await client.ExecuteAsync("json-to-xml", Encoding.UTF8.GetBytes(input2));
Console.WriteLine($"Output: {result2.Output.ToStringUtf8()}");
Console.WriteLine($"Duration: {result2.Metrics.ExecuteDurationUs}μs\n");

// ── Demo 3: Batch processing ──
Console.WriteLine("── Demo 3: Batch Processing (3 items) ──");

var items = new List<(byte[], string, string)>
{
    (Encoding.UTF8.GetBytes("""{"firstName":"Alice","lastName":"Smith","email":"ALICE@TEST.COM","age":28}"""), "application/json", "batch-1"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Bob","lastName":"Jones","email":"BOB@TEST.COM","age":35}"""), "application/json", "batch-2"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Charlie","lastName":"Brown","email":"CHARLIE@TEST.COM","age":17}"""), "application/json", "batch-3"),
};

var batchResult = await client.ExecuteBatchAsync("person-transform", items);
foreach (var r in batchResult.Results)
{
    Console.WriteLine($"  [{r.CorrelationId}] {r.Output.ToStringUtf8()}");
}

// ── Summary ──
Console.WriteLine();
var finalHealth = await client.HealthAsync();
Console.WriteLine($"=== Done: {finalHealth.LoadedTransformations} transforms loaded, " +
                  $"{finalHealth.TotalExecutions} executions, {finalHealth.TotalErrors} errors ===");
