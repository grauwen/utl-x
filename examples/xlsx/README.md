# XLSX input and output

For an isolated Linux UTLXe deployment built from GitHub, see the
[Docker test instructions](docker/README.md). This test setup leaves My Bottle
Collection and its deployment unchanged.

Run this from the UTL-X checkout after building the CLI:

```powershell
.\utlx.bat transform examples/xlsx/bottle-collection-preview.utlx collection.xlsx -o preview.json
```

The example selects the `Collection` worksheet from a My Bottle Collection export.
For an inventory from another workbook, change `sheet` to that workbook's inventory
sheet. When omitted, `sheet` defaults to the first visible sheet.

Use row 1 for distinct text headers. Each nonempty data row becomes one record with
those headers as keys. Blank cells are null. Excel numbers and booleans retain their
types, and date-formatted cells use ISO strings. Formulas use saved results. Set
`rowNumbers: true` to add the original one-based Excel row number as `_rowNumber`.

The JSON is suitable for reviewing and mapping inventory rows before import.
Quantity expansion, duplicate checks and saving bottles belong to the app's import
workflow. A workbook may contain other sheets and embedded photos. This example
reads only the selected sheet's cells.

The reader accepts files up to 128 MiB compressed and 128 MiB expanded, with up to
10,000 data rows and headers in columns A through IV. Large photo-heavy exports
can exceed those limits. The CLI opens workbook files from disk and spools binary
stdin to a temporary file. Allow space for one upload per concurrent stdin import
on a disk-backed temporary volume.
When reading XLSX from stdin or a file without an `.xlsx` extension, pass
`--from xlsx` to select binary input handling.

## Write a workbook

Build the CLI, then transform a JSON array of records into an XLSX file:

```powershell
.\utlx.bat transform examples/xlsx/json-to-workbook.utlx collection.json -o collection.xlsx
```

For a script-free conversion, specify the output format explicitly:

```powershell
.\utlx.bat convert -i collection.json --to xlsx -o collection.xlsx
```

The writer creates one worksheet. It infers columns in their first-seen order
across the records. To choose or reorder columns, declare them in the output
header:

```utlx
output xlsx { sheet: "Collection", columns: ["Name", "Remaining", "Purchased"] }
```

An empty array requires explicit columns. Output supports up to 10,000 records
and 256 columns. Each record must be an object whose selected values are scalar
or date/time values. Flatten nested objects and arrays in the transformation.

Strings are written as text, including strings that begin with `=`. Booleans
and ordinary numbers become typed Excel cells. Numbers exceeding Excel's
15-significant-digit precision or numeric range are written as text. UDM dates
become date-formatted cells. Other date/time values use ISO text to retain their
time and timezone information. Missing values and nulls produce blank cells.
The reader skips completely blank rows.

The writer streams older worksheet rows to temporary disk files and removes
them when it closes. The final workbook is returned as bytes. The CLI writes
those bytes to the output file or stdout, with diagnostics on stderr.

## UTLXe and the .NET client

Build the engine with `./gradlew :modules:engine:jar` or
`.\gradlew.bat :modules:engine:jar`. The TEMPLATE, COPY and COMPILED strategies
accept workbook bytes and return binary XLSX output. Batches, named protobuf
inputs and pipelines retain those bytes.

The existing .NET client uses stdio-proto with a persistent engine process.
Set `UTLXE_JAR_PATH` to the built engine JAR. A minimal export uses:

```csharp
await using var client = new Glomidco.Utlx.UtlxeClient(
    new Glomidco.Utlx.UtlxeClientOptions { Workers = 2 });
await client.StartAsync();
var load = await client.LoadTransformationAsync(
    "export", await File.ReadAllTextAsync("examples/xlsx/json-to-workbook.utlx"), "COMPILED");
if (!load.Success) throw new InvalidOperationException(load.Error);

var result = await client.ExecuteAsync(
    "export", await File.ReadAllBytesAsync("collection.json"), "application/json");
if (!result.Success) throw new InvalidOperationException(result.Error);
await File.WriteAllBytesAsync("collection.xlsx", result.Output.ToByteArray());
```

For reading, load the preview script and pass the workbook bytes to
`ExecuteAsync` with media type
`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`.
Use `ExecuteNamedAsync` for a mapping that combines workbook and other named
inputs. Each input's format is declared in the UTL-X header. The high-level
SDK's `TransformMultiAsync` uses this same binary path.

The raw HTTP route `POST /api/transformations/{id}/run` accepts and returns
workbook bytes with the XLSX media type. JSON HTTP APIs accept a base64
`payload` when `payloadEncoding` is `"base64"`. Binary responses carry base64
`output`, `outputEncoding: "base64"` and `outputContentType`.

Existing deployment limits still apply. The HTTP body limit and a
transformation's `maxInputSize` can be lower than the reader's 128 MiB bound.
The gRPC inbound frame limit allows a 128 MiB workbook plus protocol overhead.
Use disk-backed temporary storage and measure actual workloads when sizing
the JVM heap for two workers in a 2 GiB container. File size alone does not
predict the memory used by parsed worksheet cells.

Line-oriented stdio-json and Dapr message forwarding currently require text
output. Use stdio-proto, gRPC or the HTTP routes for XLSX output.

## Native CLI verification

The XLSX CLI has been built and tested on Windows x64 with GraalVM Community
21.0.2, Visual Studio's MSVC 19.51 compiler and the Windows SDK. Tests cover
10,000-row exports and imports, Unicode, precise numbers, dates, cached formulas,
shared strings, hidden/selected sheets, binary stdin/stdout, invalid files,
worksheet limits and temporary-file cleanup. The 115 existing format conformance
cases also pass against this native executable.

Use Visual Studio Developer PowerShell with the x64 C++ tools loaded. Set the
paths to your Java 17 and GraalVM installations, then run:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:GRAALVM_HOME = 'C:\path\to\graalvm-jdk-21'
.\gradlew.bat -I examples/xlsx/native-build.init.gradle :modules:cli:nativeCompile
python scripts/test-xlsx-native.py modules/cli/build/native/nativeCompile/utlx.exe --rows 10000
```

The optional init script uses `GRAALVM_HOME` for native-image while retaining
Java 17 source compilation. It caps the builder heap at 8 GiB. The tested
executable retains the project's existing 256 MiB runtime heap default; its
total process memory includes additional native allocations and mapped code.
These results do not establish a container memory budget for larger workloads.
Keep the generated companion DLLs alongside `utlx.exe`.

Native metadata belongs to the XLSX format module. The build generates reflection
registrations from the pinned OOXML schema dependency. Resource configuration
includes XMLBeans schemas and message bundles. The JNI entries were recorded
with the tracing agent for the streaming writer's font-library initialization.
Review this metadata and repeat native tests when changing POI or GraalVM.
To collect fresh tracing output using the same fixtures:

```powershell
python scripts/test-xlsx-native.py "$env:GRAALVM_HOME\bin\java.exe" `
  --jar modules/cli/build/libs/cli-1.4.4.jar --agent-directory build/native-agent/xlsx
```

This verifies the Windows native CLI. Linux native builds and a native UTLXe
engine remain separate work. UTLXe and its .NET process client continue to use
the Java engine build.
