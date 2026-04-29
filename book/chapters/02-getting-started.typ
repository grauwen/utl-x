= Getting Started

== Installation
// - Homebrew (macOS/Linux): brew tap grauwen/utlx && brew install utlx
// - Chocolatey (Windows): choco install utlx
// - Direct download: GitHub Releases (Linux, macOS ARM, Windows)
// - Building from source: JDK 17 + Gradle
// - GraalVM native binary vs JVM JAR

== Your First Transformation
// - Create input.xml with a simple order
// - Create transform.utlx
// - Run: utlx transform transform.utlx input.xml
// - Examine the output

== The .utlx File Structure
// - Header: %utlx 1.0
// - Input declaration: input xml
// - Output declaration: output json
// - Separator: ---
// - Body: the transformation expression

== Identity Mode (The Flip)
// - cat data.xml | utlx → auto-converts to JSON
// - cat data.json | utlx → auto-converts to XML
// - cat data.csv | utlx --to yaml → explicit target format
// - The smart flip: how UTL-X detects input format

== Expression Mode (-e)
// - echo '{"name":"Alice"}' | utlx -e '.name' -r
// - The dot shorthand: . = $input
// - Raw output: -r strips quotes
// - Pipe-friendly: works with shell pipelines
// - jq-like experience but for all formats

== The CLI Reference
// - utlx transform — script-based transformation
// - utlx -e — inline expressions
// - utlx repl — interactive mode
// - utlx validate — syntax checking
// - utlx functions — stdlib browser
// - utlx version — version info
// - Common flags: --from, --to, --output, --verbose, --debug

== VS Code Extension
// - Installing the extension
// - Syntax highlighting
// - Live preview
// - Error diagnostics
// - Function autocomplete

== Project Structure
// - Single file: transform.utlx
// - Multi-file: imports and modules (future)
// - Config: transform.yaml for UTLXe engine
// - Schemas: input/output validation schemas
