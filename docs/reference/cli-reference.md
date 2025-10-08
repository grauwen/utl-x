
# Command-Line Interface Reference

Complete reference for the `utlx` CLI tool.

## Installation

```bash
# macOS/Linux
brew install utl-x

# Windows
choco install utl-x

# Universal installer
curl -fsSL https://utl-x.site/install.sh | bash
```

## Commands

### utlx transform

Transform data using UTL-X script.

```bash
utlx transform <script> <input> [options]
```

**Arguments:**
- `<script>` - UTL-X transformation script (.utlx file)
- `<input>` - Input data file

**Options:**
- `-o, --output <file>` - Output file (default: stdout)
- `--output-format <format>` - Output format (json|xml|csv|yaml)
- `--input-format <format>` - Input format (auto|json|xml|csv|yaml)
- `--pretty` - Pretty-print output
- `--indent <n>` - Indentation spaces (default: 2)
- `--validate` - Validate before transforming
- `--profile` - Show performance profile
- `--debug` - Enable debug mode

**Examples:**

```bash
# Basic transformation
utlx transform script.utlx input.json

# With output file
utlx transform script.utlx input.xml -o output.json

# Override output format
utlx transform script.utlx input.xml --output-format csv

# Pretty print
utlx transform script.utlx input.json --pretty

# Profile performance
utlx transform script.utlx input.xml --profile
```

### utlx validate

Validate UTL-X script syntax.

```bash
utlx validate <script>
```

**Options:**
- `--strict` - Strict validation mode
- `--warnings` - Show warnings

**Examples:**

```bash
utlx validate script.utlx
utlx validate script.utlx --strict
```

### utlx compile

Compile UTL-X script.

```bash
utlx compile <script> [options]
```

**Options:**
- `-o, --output <file>` - Compiled output file
- `--target <target>` - Target runtime (jvm|js|native)
- `--optimize` - Enable optimizations

**Examples:**

```bash
utlx compile script.utlx -o compiled.class
utlx compile script.utlx --target js -o script.js
```

### utlx format

Format UTL-X script.

```bash
utlx format <script> [options]
```

**Options:**
- `-w, --write` - Write formatted output to file
- `--indent <n>` - Indentation spaces
- `--check` - Check if formatted (exit 1 if not)

**Examples:**

```bash
utlx format script.utlx
utlx format script.utlx --write
utlx format script.utlx --check
```

### utlx migrate

Migrate from other transformation languages.

```bash
utlx migrate <source> [options]
```

**Options:**
- `--from <lang>` - Source language (xslt|dataweave|jq|jsonata)
- `-o, --output <file>` - Output UTL-X file
- `--interactive` - Interactive migration

**Examples:**

```bash
utlx migrate transform.xsl --from xslt -o transform.utlx
utlx migrate script.dwl --from dataweave -o script.utlx
```

### utlx version

Show version information.

```bash
utlx version
```

### utlx help

Show help information.

```bash
utlx help [command]
```

## Global Options

Available for all commands:

- `-h, --help` - Show help
- `-v, --version` - Show version
- `--verbose` - Verbose output
- `--quiet` - Suppress output
- `--color` - Force color output
- `--no-color` - Disable color output

## Environment Variables

- `UTLX_HOME` - UTL-X installation directory
- `UTLX_CACHE_DIR` - Cache directory
- `UTLX_CONFIG` - Configuration file path

## Configuration File

`~/.utlxrc` or `.utlxrc` in project directory:

```yaml
format:
  indent: 2
  pretty: true

compile:
  optimize: true
  target: jvm

transform:
  validate: true
  profile: false
```

## Exit Codes

- `0` - Success
- `1` - General error
- `2` - Syntax error
- `3` - Type error
- `4` - Runtime error
- `5` - File not found

---
