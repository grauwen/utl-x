# UTL-X Functions Command - Implementation Guide

## Overview

The `utlx functions` command provides a comprehensive interface for exploring the UTL-X standard library with support for multiple output formats, making it perfect for both human use and tool integration (VS Code plugins, IDEs, etc.).

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│                    utlx functions                           │
│                 (CLI Command)                               │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ├─→ Human-readable output (default)
                         ├─→ JSON output (--format json)
                         ├─→ YAML output (--format yaml)
                         └─→ Compact JSON (--format json-compact)
                         │
                         ▼
            ┌────────────────────────────┐
            │  Bundled Registry JSON     │
            │  (in JAR resources)        │
            └────────────┬───────────────┘
                         │
                         │ Generated at build time from
                         ▼
            ┌────────────────────────────┐
            │  $UTLXFunction Annotations │
            │  (in stdlib/*.kt files)    │
            └────────────────────────────┘
```

### Files Created

1. **`stdlib/src/main/kotlin/org/apache/utlx/stdlib/FunctionRegistry.kt`**
   - Data classes for registry structure
   - `FunctionRegistry` - top-level registry
   - `FunctionInfo` - individual function metadata
   - `ParameterInfo` - parameter details
   - `ReturnInfo` - return value details

2. **`modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/FunctionsCommand.kt`**
   - Main command implementation
   - Subcommands: list, search, info, stats, categories
   - Output formats: text, json, json-compact, yaml
   - Loads bundled registry from JAR resources

3. **`modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt`** (updated)
   - Wired up `functions` and `fn` commands to `FunctionsCommand`

## Usage

### Human-Readable Output (Default)

```bash
# List all functions
utlx functions
utlx functions list

# Search functions
utlx functions search xml
utlx functions search "parse.*"

# Show detailed info
utlx functions info map
utlx functions info xmlParse

# Show statistics
utlx functions stats

# List categories
utlx functions categories

# Filter by category
utlx functions list --category Array
```

### JSON Output (For Tools/Plugins)

```bash
# Get all functions as JSON
utlx functions list --format json

# Get specific function as JSON
utlx functions info map --format json

# Search results as JSON
utlx functions search xml --format json

# Compact JSON (one-line, no pretty-print)
utlx functions list --format json-compact
```

### YAML Output

```bash
# YAML format
utlx functions list --format yaml
utlx functions info map --format yaml
```

## VS Code Plugin Integration

### Recommended Approach: Bundle Registry JSON

**Step 1: Extract Registry from CLI**
```bash
# During extension build, extract registry
utlx functions list --format json > vscode-extension/resources/utlx-functions.json
```

**Step 2: TypeScript Integration**
```typescript
// vscode-extension/src/functionProvider.ts
import functionRegistry from '../resources/utlx-functions.json';

export class UTLXFunctionProvider implements CompletionItemProvider {
    private functions: FunctionInfo[];

    constructor() {
        // Load bundled registry (fast, offline, no CLI dependency)
        this.functions = functionRegistry.functions;
    }

    provideCompletionItems(
        document: TextDocument,
        position: Position
    ): CompletionItem[] {
        return this.functions.map(func => ({
            label: func.name,
            kind: CompletionItemKind.Function,
            detail: `${func.category} - ${func.description}`,
            documentation: new MarkdownString(
                this.formatDocumentation(func)
            ),
            insertText: new SnippetString(
                this.formatInsertText(func)
            )
        }));
    }

    provideHover(document: TextDocument, position: Position): Hover {
        const word = document.getText(document.getWordRangeAtPosition(position));
        const func = this.functions.find(f => f.name === word);

        if (!func) return null;

        return new Hover(new MarkdownString(this.formatHoverInfo(func)));
    }

    private formatDocumentation(func: FunctionInfo): string {
        return `
**${func.name}** - ${func.description}

\`\`\`
${func.signature}
\`\`\`

**Category:** ${func.category}
**Tags:** ${func.tags.join(', ')}

**Examples:**
${func.examples.map(ex => `- \`${ex}\``).join('\n')}

${func.notes ? `**Notes:** ${func.notes}` : ''}

${func.seeAlso.length > 0 ? `**See Also:** ${func.seeAlso.join(', ')}` : ''}
        `.trim();
    }

    private formatInsertText(func: FunctionInfo): string {
        // Generate snippet with tab stops
        const params = func.parameters.map((p, i) => `\${${i+1}:${p.name}}`).join(', ');
        return `${func.name}(${params})$0`;
    }

    private formatHoverInfo(func: FunctionInfo): string {
        return `
**${func.name}**
${func.description}

\`\`\`utlx
${func.signature}
\`\`\`

${func.examples[0] ? `**Example:** \`${func.examples[0]}\`` : ''}
        `.trim();
    }
}
```

## Registry JSON Schema

```json
{
  "version": "1.0.0",
  "generated": "2025-10-19T01:30:00Z",
  "totalFunctions": 612,
  "functions": [
    {
      "name": "map",
      "category": "Array",
      "description": "Transform array elements",
      "signature": "map(array: Array, fn: (element) => any) => Array",
      "minArgs": 2,
      "maxArgs": 2,
      "parameters": [
        {
          "name": "array",
          "type": "Array",
          "description": "Input array to process"
        },
        {
          "name": "fn",
          "type": "Function",
          "description": "Transformation function (element) => any"
        }
      ],
      "returns": {
        "type": "Array",
        "description": "New array with transformed elements"
      },
      "examples": [
        "map([1, 2, 3], x => x * 2) => [2, 4, 6]",
        "map(users, u => u.name) => [\"Alice\", \"Bob\"]"
      ],
      "notes": "Preserves array order. Does not modify original array.",
      "tags": ["array", "transform", "map"],
      "seeAlso": ["filter", "reduce", "forEach"],
      "since": "1.0",
      "deprecated": false
    }
  ],
  "categories": {
    "Array": [...],
    "String": [...],
    "Math": [...],
    ...
  }
}
```

## Build Integration

### Gradle Configuration Needed

Add to `modules/cli/build.gradle.kts`:

```kotlin
tasks.named("processResources") {
    dependsOn(":stdlib:generateFunctionRegistry")

    from("${project(":stdlib").buildDir}/generated/function-registry") {
        include("utlx-functions.json")
        into("function-registry")
    }
}
```

This ensures the registry JSON is bundled into the CLI JAR at build time.

## Testing

```bash
# Build everything
./gradlew :stdlib:generateFunctionRegistry
./gradlew :modules:cli:build

# Test the command
./gradlew :modules:cli:run --args="functions list"
./gradlew :modules:cli:run --args="functions search xml"
./gradlew :modules:cli:run --args="functions info map"
./gradlew :modules:cli:run --args="functions list --format json"

# Test VS Code integration
utlx functions list --format json > test-registry.json
cat test-registry.json | jq '.functions | length'
cat test-registry.json | jq '.functions[0]'
```

## Distribution Strategy

### 1. CLI Distribution
- Registry JSON bundled in JAR
- Users can query with `utlx functions --format json`
- Always in sync with CLI version

### 2. VS Code Extension
- Extract registry during extension build: `utlx functions --format json`
- Bundle with extension for offline use
- Update when extension updates

### 3. NPM Package (Optional)
```bash
# Publish registry as standalone package
npm publish @utlx/function-registry
```

```typescript
// Other tools can use it
import { registry } from '@utlx/function-registry';
```

### 4. CDN (Optional)
- Host registry at `https://registry.utlx-lang.org/v1/functions.json`
- Extensions can fetch latest
- Fallback to bundled version if offline

## Migration from `./utlx-functions`

The standalone `./utlx-functions` bash script is now deprecated. Users should migrate to:

```bash
# Old (deprecated)
./utlx-functions list
./utlx-functions search xml
./utlx-functions info map

# New (integrated)
utlx functions list
utlx functions search xml
utlx functions info map
```

Benefits:
- ✅ One unified CLI tool
- ✅ Richer output (JSON, YAML)
- ✅ Faster (no file I/O)
- ✅ Always available
- ✅ Professional UX

## Next Steps

1. **Fix FunctionRegistryGenerator compilation errors**
   - Add stub `exportRegistry()` method to StandardLibrary/Functions.kt
   - Implement registry generation from $UTLXFunction annotations

2. **Configure build to bundle registry**
   - Update CLI gradle to include registry in resources
   - Test that registry loads from JAR

3. **Test the command**
   - Verify all output formats work
   - Test search functionality
   - Validate JSON schema

4. **Update documentation**
   - Add examples to main README
   - Update CLI help text
   - Document for VS Code extension developers

5. **VS Code Extension**
   - Create function provider
   - Add autocomplete
   - Add hover tooltips
   - Add function browser view

## Status

✅ Data classes created (`FunctionRegistry.kt`)
✅ Command implemented (`FunctionsCommand.kt`)
✅ Main.kt updated
⏳ Registry generator needs `exportRegistry()` implementation
⏳ Build configuration needs update
⏳ Testing pending
