using System.Text;
using Glomidco.Utlx;

// ============================================================
// UTL-X .NET Demo — Real transformations via UTLXe subprocess
// ============================================================

var jarPath = Environment.GetEnvironmentVariable("UTLXE_JAR_PATH")
    ?? throw new Exception("Set UTLXE_JAR_PATH to the utlxe JAR path");

Console.WriteLine("=== UTL-X .NET Integration Demo ===\n");

// Start the UTLXe engine (JVM subprocess, kept alive for all transforms)
Console.WriteLine("Starting UTLXe engine...");
await using var client = new UtlxeClient(new UtlxeClientOptions { JarPath = jarPath });
await client.StartAsync();

var health = await client.HealthAsync();
Console.WriteLine($"Engine ready: state={health.State}, uptime={health.UptimeMs}ms\n");

// ─────────────────────────────────────────────────────────────
// Demo 1: JSON Restructuring (full .utlx template)
// ─────────────────────────────────────────────────────────────
Console.WriteLine("── Demo 1: JSON Restructuring ──");
Console.WriteLine("   .utlx template with concat(), lowerCase(), computed boolean\n");

var personTransform = """
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

var load1 = await client.LoadTransformationAsync("person-transform", personTransform);
Console.WriteLine(load1.Success ? $"  Compiled in {load1.Metrics?.TotalDurationUs}μs" : $"  ERROR: {load1.Error}");

var input1 = """{"firstName": "Marcel", "lastName": "Grauwen", "email": "MARCEL@GLOMIDCO.COM", "age": 45}""";
Console.WriteLine($"  Input:  {input1}");

var result1 = await client.ExecuteAsync("person-transform", Encoding.UTF8.GetBytes(input1));
Console.WriteLine($"  Output: {Oneline(result1.Output.ToStringUtf8())}");
Console.WriteLine($"  Duration: {result1.Metrics.ExecuteDurationUs}μs\n");

// ─────────────────────────────────────────────────────────────
// Demo 2: JSON → XML format conversion
// ─────────────────────────────────────────────────────────────
Console.WriteLine("── Demo 2: JSON → XML Format Conversion ──");
Console.WriteLine("   Input JSON, output XML — UTL-X handles the format switch\n");

var jsonToXml = """
    %utlx 1.0
    input json
    output xml
    ---
    {
      order: {
        id: $input.orderId,
        customer: $input.customerName,
        total: $input.total,
        currency: $input.currency
      }
    }
    """;

var load2 = await client.LoadTransformationAsync("json-to-xml", jsonToXml);
Console.WriteLine(load2.Success ? $"  Compiled in {load2.Metrics?.TotalDurationUs}μs" : $"  ERROR: {load2.Error}");

var input2 = """{"orderId": "ORD-2026-001", "customerName": "Contoso Ltd", "total": 1499.85, "currency": "EUR"}""";
Console.WriteLine($"  Input:  {input2}");

var result2 = await client.ExecuteAsync("json-to-xml", Encoding.UTF8.GetBytes(input2));
Console.WriteLine($"  Output:\n{Indent(result2.Output.ToStringUtf8(), "    ")}");
Console.WriteLine($"  Duration: {result2.Metrics.ExecuteDurationUs}μs\n");

// ─────────────────────────────────────────────────────────────
// Demo 3: Multi-Input — JSON customer + JSON order → combined
// ─────────────────────────────────────────────────────────────
Console.WriteLine("── Demo 3: Multi-Input Transformation ──");
Console.WriteLine("   Two named inputs (customer + order) merged into one output\n");

var multiInput = """
    %utlx 1.0
    input: customer json, order json
    output json
    ---
    {
      shipment: {
        recipient: $customer.name,
        address: concat($customer.street, ", ", $customer.city, ", ", $customer.country),
        orderId: $order.id,
        itemCount: size($order.items)
      }
    }
    """;

var load3 = await client.LoadTransformationAsync("multi-input", multiInput);
Console.WriteLine(load3.Success ? $"  Compiled in {load3.Metrics?.TotalDurationUs}μs" : $"  ERROR: {load3.Error}");

// Multi-input: send as JSON envelope with keys matching input names
var multiPayload = """
    {
      "customer": {
        "name": "Contoso Ltd",
        "street": "Keizersgracht 100",
        "city": "Amsterdam",
        "country": "Netherlands"
      },
      "order": {
        "id": "ORD-2026-042",
        "items": [
          {"product": "Sensor Module", "weight": 0.5},
          {"product": "Power Supply", "weight": 2.1},
          {"product": "Mounting Kit", "weight": 0.3}
        ]
      }
    }
    """;
Console.WriteLine("  Inputs: customer (JSON) + order (JSON) as envelope");

if (load3.Success)
{
    var result3 = await client.ExecuteAsync("multi-input", Encoding.UTF8.GetBytes(multiPayload));
    Console.WriteLine($"  Output: {Oneline(result3.Output.ToStringUtf8())}");
    Console.WriteLine($"  Duration: {result3.Metrics?.ExecuteDurationUs}μs");
}
Console.WriteLine();

// ─────────────────────────────────────────────────────────────
// Demo 4: Batch Processing — same transform, multiple messages
// ─────────────────────────────────────────────────────────────
Console.WriteLine("── Demo 4: Batch Processing (production stream simulation) ──");
Console.WriteLine("   5 messages through 'person-transform' in one batch call\n");

var batchItems = new List<(byte[], string, string)>
{
    (Encoding.UTF8.GetBytes("""{"firstName":"Alice","lastName":"Smith","email":"ALICE@CORP.COM","age":28}"""), "application/json", "msg-001"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Bob","lastName":"Jones","email":"BOB@CORP.COM","age":35}"""), "application/json", "msg-002"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Charlie","lastName":"Brown","email":"CHARLIE@CORP.COM","age":17}"""), "application/json", "msg-003"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Diana","lastName":"Prince","email":"DIANA@CORP.COM","age":42}"""), "application/json", "msg-004"),
    (Encoding.UTF8.GetBytes("""{"firstName":"Eve","lastName":"Adams","email":"EVE@CORP.COM","age":15}"""), "application/json", "msg-005"),
};

var batchResult = await client.ExecuteBatchAsync("person-transform", batchItems);
foreach (var r in batchResult.Results)
{
    Console.WriteLine($"  [{r.CorrelationId}] {Oneline(r.Output.ToStringUtf8())}");
}
Console.WriteLine();

// ─────────────────────────────────────────────────────────────
// Demo 5: CSV output
// ─────────────────────────────────────────────────────────────
Console.WriteLine("── Demo 5: JSON → CSV ──");
Console.WriteLine("   Transform JSON array to CSV format\n");

var jsonToCsv = """
    %utlx 1.0
    input json
    output csv
    ---
    map($input, (row) -> {
      name: row.name,
      email: lowerCase(row.email),
      department: row.dept
    })
    """;

var load5 = await client.LoadTransformationAsync("json-to-csv", jsonToCsv);
Console.WriteLine(load5.Success ? $"  Compiled in {load5.Metrics?.TotalDurationUs}μs" : $"  ERROR: {load5.Error}");

var input5 = """[{"name":"Alice","email":"ALICE@CORP.COM","dept":"Engineering"},{"name":"Bob","email":"BOB@CORP.COM","dept":"Sales"},{"name":"Charlie","email":"CHARLIE@CORP.COM","dept":"Engineering"}]""";
Console.WriteLine($"  Input:  (JSON array, 3 employees)");

if (load5.Success)
{
    var result5 = await client.ExecuteAsync("json-to-csv", Encoding.UTF8.GetBytes(input5));
    Console.WriteLine($"  Output:\n{Indent(result5.Output.ToStringUtf8(), "    ")}");
    Console.WriteLine($"  Duration: {result5.Metrics?.ExecuteDurationUs}μs");
}
Console.WriteLine();

// ─────────────────────────────────────────────────────────────
// Summary
// ─────────────────────────────────────────────────────────────
var finalHealth = await client.HealthAsync();
Console.WriteLine($"=== Summary: {finalHealth.LoadedTransformations} transforms loaded, " +
                  $"{finalHealth.TotalExecutions} total executions, " +
                  $"{finalHealth.TotalErrors} errors ===");

// ── Helpers ──
static string Oneline(string s) => s.ReplaceLineEndings("").Replace("  ", " ").Trim();
static string Indent(string s, string prefix) =>
    string.Join("\n", s.Split('\n').Select(line => prefix + line));
