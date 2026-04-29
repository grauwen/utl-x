= Getting Started

This chapter gets UTL-X running on your machine in under five minutes. By the end, you'll have installed the CLI, run your first transformation, and understood the structure of a `.utlx` file.

== Installation

UTL-X is distributed as a native binary — a single file, no JVM required, instant startup. Choose the method that fits your platform.

=== macOS (Homebrew) — Recommended

```bash
brew tap grauwen/utlx
brew install utlx
```

Verify:

```bash
utlx --version
# UTL-X CLI v1.0.2
# Universal Transformation Language Extended
```

Homebrew installs the GraalVM native binary. Startup time is under 10 milliseconds — as fast as `grep` or `jq`.

To update later:

```bash
brew update && brew upgrade utlx
```

=== Windows (Chocolatey)

```powershell
choco install utlx
```

Verify:

```powershell
utlx --version
```

If Chocolatey is not available, download the binary directly (see below).

=== Linux / macOS / Windows — Direct Download

Native binaries are available from GitHub Releases for every platform:

```bash
# macOS (Apple Silicon)
curl -L https://github.com/grauwen/utl-x/releases/download/v1.0.2/utlx-macos-arm64 -o utlx
chmod +x utlx
sudo mv utlx /usr/local/bin/

# Linux (x64)
curl -L https://github.com/grauwen/utl-x/releases/download/v1.0.2/utlx-linux-x64 -o utlx
chmod +x utlx
sudo mv utlx /usr/local/bin/

# Windows: download utlx-windows-x64.exe from
# https://github.com/grauwen/utl-x/releases/tag/v1.0.2
# and place in a directory on your PATH
```

=== Building from Source

If you prefer to build UTL-X yourself, or need the JVM version for development:

*Prerequisites:*
- JDK 17 or later (`java -version` to check)
- Git

*Steps:*

```bash
# Clone the repository
git clone https://github.com/grauwen/utl-x.git
cd utl-x

# Build the CLI JAR
./gradlew :modules:cli:jar

# Run using the wrapper script
./utlx --version
```

The wrapper script (`utlx`) automatically finds and runs the compiled JAR. On Windows, use `utlx.bat` (Command Prompt) or `utlx.ps1` (PowerShell).

*Building the native binary* (requires GraalVM):

```bash
# Install GraalVM (macOS)
brew install --cask graalvm/tap/graalvm-community-jdk22

# Build native image
./gradlew :modules:cli:nativeCompile

# Binary at: modules/cli/build/native/nativeCompile/utlx
```

The native binary starts in under 10ms and uses ~40MB of memory, compared to ~250ms startup and ~150MB for the JVM JAR. For development, the JAR is fine. For scripting and CI/CD, use the native binary.

=== Native Binary vs JVM JAR

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Aspect*], [*Native binary (GraalVM)*], [*JVM JAR*],
  [Startup time], [< 10ms], [~250ms],
  [Memory], [~40 MB], [~150 MB],
  [File size], [~80 MB (single file)], [~38 MB JAR + JVM],
  [Requires JVM?], [No], [Yes (JDK 17+)],
  [Install method], [brew, choco, download], [Build from source],
  [Best for], [CLI use, scripting, CI/CD], [Development, debugging],
)

When in doubt, use the native binary. It's what `brew install utlx` gives you.

== Your First Transformation

Create a file called `hello.utlx`:

```utlx
%utlx 1.0
input json
output json
---
{
  greeting: concat("Hello, ", $input.name, "!"),
  reversed: reverse($input.name),
  length: length($input.name)
}
```

Run it:

```bash
echo '{"name": "World"}' | utlx transform hello.utlx
```

Output:

```json
{
  "greeting": "Hello, World!",
  "reversed": "dlroW",
  "length": 5
}
```

Congratulations — you've just run your first UTL-X transformation.

== The .utlx File Structure

Every UTL-X transformation file has the same structure:

```
┌─────────────────────────────────┐
│ %utlx 1.0                      │ ← Version declaration
│ input json                      │ ← Input format
│ output xml                      │ ← Output format (+ options)
│ ---                             │ ← Separator
│ {                               │
│   name: $input.customer.name,   │ ← Transformation body
│   total: sum($input.items.price)│
│ }                               │
└─────────────────────────────────┘
```

The *header* (above `---`) declares:
- `%utlx 1.0` — the UTL-X language version (currently always 1.0)
- `input <format>` — what format the input data is in (xml, json, csv, yaml, odata)
- `output <format>` — what format to produce, with optional settings

The *body* (below `---`) is the transformation expression. It's a single expression that produces the output. No `return` keyword — the entire body IS the result.

=== Input Format Options

```utlx
input json                          // standard JSON
input xml                           // XML (auto-detects encoding)
input csv                           // CSV (comma-delimited, with headers)
input csv {delimiter: ";"}          // semicolon-delimited
input yaml                          // YAML
input odata                         // OData JSON
```

=== Output Format Options

```utlx
output json                              // pretty-printed JSON
output json {writeAttributes: true}      // preserve XML attributes
output xml {encoding: "UTF-8"}           // XML with encoding declaration
output csv {delimiter: ";", headers: true, regionalFormat: "european"}
output yaml                              // YAML block style
```

=== Multi-Input

When your transformation needs data from multiple sources:

```utlx
%utlx 1.0
input: orders xml, customers json
output json
---
{
  orderCount: count($orders.Order),
  customerName: $customers.name
}
```

Each input gets its own variable name (prefixed with `$`).

== Trying It Out: Five Quick Examples

Here are five transformations you can run right now to see UTL-X in action:

*1. XML to JSON* — the most common integration task:

```bash
echo '<User><Name>Alice</Name><Age>30</Age></User>' | utlx --from xml --to json
```

*2. JSON to YAML* — configuration format conversion:

```bash
echo '{"server": "prod-01", "port": 8080, "debug": false}' | utlx --from json --to yaml
```

*3. Extract a field* — like jq but for any format:

```bash
echo '{"users": [{"name": "Alice"}, {"name": "Bob"}]}' | utlx -e 'map($input.users, (u) -> u.name)'
```

*4. CSV to JSON* — data import:

```bash
echo 'name,age\nAlice,30\nBob,25' | utlx --from csv --to json
```

*5. XML attribute access* — the `@` syntax:

```bash
echo '<Product id="P-001" price="29.99">Widget</Product>' | utlx --from xml -e '{id: $input.Product.@id, name: $input.Product, price: toNumber($input.Product.@price)}'
```

Each of these runs in under 10 milliseconds on a native binary. That's the UTL-X developer experience: instant feedback, any format.

== What's Next

You now have UTL-X installed and running. The next chapters cover:

- *Chapter 5*: UTL-X on the command line — expression mode, identity flip, shell scripting
- *Chapter 6*: The three executables — CLI, daemon, and production engine
- *Chapter 7*: The IDE — VS Code extension, live preview, UDM tree browser
