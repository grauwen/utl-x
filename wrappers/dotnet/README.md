# UTL-X .NET Wrapper

C# client for the UTL-X Engine (UTLXe). Spawns UTLXe as a long-running JVM subprocess and communicates via varint-delimited protobuf over stdin/stdout.

## How It Works

```
Your .NET Application
    │
    │  UtlxeClient (C#)
    │  └── varint-delimited protobuf over stdin/stdout
    │
    └── UTLXe subprocess (JVM)
         └── Compiled UTL-X programs in memory
```

1. `UtlxeClient` spawns `java -jar utlxe.jar --mode stdio-proto`
2. The JVM starts once and stays alive for the application lifetime
3. `.utlx` templates are compiled once via `LoadTransformationAsync()`
4. Payloads are transformed via `ExecuteAsync()` (~1ms per call)
5. On disposal, the JVM exits gracefully

## Prerequisites

- .NET 9.0+ SDK
- Java 17+ (for the UTLXe JVM subprocess)
- UTLXe JAR file (built from the utl-x repo)

## Building

```bash
# Build the UTLXe JAR (from repo root)
./gradlew :modules:engine:jar

# Build the .NET wrapper
cd wrappers/dotnet
dotnet build
```

## Running the Demo

```bash
export UTLXE_JAR_PATH=../../modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar
dotnet run --project samples/Demo/Demo.csproj
```

## API Usage

```csharp
using Glomidco.Utlx;

// Start the engine (JVM boots, ~2s first time)
await using var client = new UtlxeClient(new UtlxeClientOptions
{
    JarPath = "/path/to/utlxe.jar"
});
await client.StartAsync();

// Load a transformation (compiled once, cached)
await client.LoadTransformationAsync("my-transform", """
    %utlx 1.0
    input json
    output json
    ---
    {
      name: concat($input.firstName, " ", $input.lastName),
      email: lowerCase($input.email)
    }
    """);

// Execute (per message, ~1ms)
var result = await client.ExecuteAsync(
    "my-transform",
    Encoding.UTF8.GetBytes("""{"firstName": "Alice", "lastName": "Smith", "email": "ALICE@CORP.COM"}""")
);

Console.WriteLine(result.Output.ToStringUtf8());
// {"name": "Alice Smith", "email": "alice@corp.com"}
```

## Demo Examples

The demo (`samples/Demo/`) shows five transformation patterns:

### Demo 1: JSON Restructuring

Full `.utlx` template with stdlib functions (`concat`, `lowerCase`) and computed fields.

```
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
```

**Input:** `{"firstName": "Marcel", "lastName": "Grauwen", "email": "MARCEL@GLOMIDCO.COM", "age": 45}`

**Output:** `{"fullName": "Marcel Grauwen", "email": "marcel@glomidco.com", "age": 45, "isAdult": true}`

### Demo 2: JSON to XML

Format conversion — UTL-X handles the format switch natively.

```
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
```

**Input:** `{"orderId": "ORD-2026-001", "customerName": "Contoso Ltd", "total": 1499.85, "currency": "EUR"}`

**Output:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<order>
  <id>ORD-2026-001</id>
  <customer>Contoso Ltd</customer>
  <total>1499.85</total>
  <currency>EUR</currency>
</order>
```

### Demo 3: Multi-Input (Two JSON Sources)

Two named inputs merged into one output. The payload is sent as a JSON envelope with keys matching the input names.

```
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
```

**Input envelope:**
```json
{
  "customer": {"name": "Contoso Ltd", "street": "Keizersgracht 100", "city": "Amsterdam", "country": "Netherlands"},
  "order": {"id": "ORD-2026-042", "items": [{"product": "Sensor"}, {"product": "Power Supply"}, {"product": "Kit"}]}
}
```

**Output:** `{"shipment": {"recipient": "Contoso Ltd", "address": "Keizersgracht 100, Amsterdam, Netherlands", "orderId": "ORD-2026-042", "itemCount": 3}}`

### Demo 4: Batch Processing

Same transformation applied to 5 messages in one batch call — simulates a production message stream.

```csharp
var batchResult = await client.ExecuteBatchAsync("person-transform", items);
// All 5 results returned with correlation IDs for matching
```

Each result includes `CorrelationId` for matching responses back to requests in async/multiplexed scenarios.

### Demo 5: JSON to CSV

Array of JSON objects transformed to CSV with headers.

```
%utlx 1.0
input json
output csv
---
map($input, (row) -> {
  name: row.name,
  email: lowerCase(row.email),
  department: row.dept
})
```

**Input:** `[{"name":"Alice","email":"ALICE@CORP.COM","dept":"Engineering"}, ...]`

**Output:**
```csv
name,email,department
Alice,alice@corp.com,Engineering
Bob,bob@corp.com,Sales
Charlie,charlie@corp.com,Engineering
```

## UTL-X Template Syntax

```
%utlx 1.0              ← version declaration
input json              ← input format (json, xml, csv, yaml, auto)
output xml              ← output format
---                     ← separator between header and body
{                       ← transformation expression
  field: $input.path    ← $input references the input payload
}
```

**Single input:** `input json` — payload accessed as `$input`

**Multiple inputs:** `input: name1 json, name2 xml` — accessed as `$name1`, `$name2`. Payload sent as JSON envelope with matching keys.

**Lambda syntax:** `(param) -> expression`

**652 stdlib functions** available: string manipulation, math, date/time, collections, type coercion, and more.

## Testing

```bash
# Unit tests (no JVM needed)
dotnet test --filter "FullyQualifiedName~VarintCodec"

# Integration tests (requires UTLXe JAR)
export UTLXE_JAR_PATH=../../modules/engine/build/libs/utlxe-1.0.0-SNAPSHOT.jar
dotnet test
```

## NuGet Packaging

```bash
dotnet pack src/UtlxClient/UtlxClient.csproj -c Release
```

The NuGet package contains only the C# client library (~50KB). The UTLXe JAR (~50MB) is distributed separately.

## Project Structure

```
wrappers/dotnet/
├── src/UtlxClient/           # Client library
│   ├── UtlxeClient.cs        # Public API
│   ├── UtlxeProcess.cs       # JVM subprocess management
│   ├── VarintCodec.cs        # Protobuf varint framing
│   └── UtlxeException.cs     # Typed exceptions
├── tests/UtlxClient.Tests/   # xUnit tests (19 tests)
├── samples/
│   ├── Demo/                 # Console demo (5 transformation patterns)
│   └── AzureFunctionSample/  # Azure Function HTTP endpoint
└── README.md
```
