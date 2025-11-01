# UTL-X Theia Extension: VS Code Plugin Integration Strategy

**Version:** 1.0
**Date:** 2025-11-01
**Status:** Design Proposal
**Authors:** UTL-X Architecture Team

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [License Compatibility Analysis](#license-compatibility-analysis)
3. [Tier-1 Format Extensions (Instance Data)](#tier-1-format-extensions-instance-data)
4. [Tier-2 Format Extensions (Schema/Metadata)](#tier-2-format-extensions-schemametadata)
5. [Recommended Integration Strategy](#recommended-integration-strategy)
6. [Implementation Architecture](#implementation-architecture)
7. [Licensing Compliance](#licensing-compliance)
8. [Alternatives if Extensions Unavailable](#alternatives-if-extensions-unavailable)
9. [Conclusion and Recommendations](#conclusion-and-recommendations)

---

## Executive Summary

### Context

The UTL-X Theia extension uses **Monaco Editor** for the three-panel layout:
- **Left Panel**: Input data (runtime) or input schema (design-time)
- **Middle Panel**: UTL-X transformation editor
- **Right Panel**: Output data (runtime) or output schema (design-time)

### Question

Should we leverage existing **VS Code extensions** for format-specific editing capabilities in the left and right panels?

### Key Considerations

1. **Theia compatibility**: Theia supports VS Code extensions via Open VSX registry
2. **Monaco editor**: Both VS Code and Theia use Monaco as the underlying editor
3. **AGPL v3 license**: UTL-X is licensed under AGPL v3, which has specific compatibility requirements
4. **Format coverage**: Need support for tier-1 (XML, JSON, YAML, CSV) and tier-2 (XSD, JSON Schema, Avro, Protobuf)

### High-Level Recommendation

**YES, selectively use VS Code extensions** with the following approach:

✅ **USE**: MIT and Apache 2.0 licensed extensions (fully AGPL compatible)
⚠️ **EVALUATE**: EPL 2.0 licensed extensions (legal review recommended)
🛠️ **BUILD**: Custom Monaco language modes for simple formats (CSV) or where license incompatible

---

## License Compatibility Analysis

### UTL-X Project License

**AGPL v3** (GNU Affero General Public License v3)
- **Copyleft**: Modifications must be open-sourced
- **Network provision**: Source code must be provided to network users
- **Compatible with**: MIT, Apache 2.0, certain EPL variants
- **Incompatible with**: Proprietary licenses, GPL v2 (without upgrade), certain EPL variants

### Compatible Licenses (✅ SAFE TO USE)

#### MIT License
**Status**: ✅ **Fully Compatible**

**Characteristics**:
- Permissive license
- Can be combined with AGPL v3
- Combined work must be distributed under AGPL v3
- Original MIT code retains MIT license when used separately

**Examples**:
- `monaco-yaml` (MIT)
- `vscode-proto3` (MIT)
- Monaco Editor core (MIT)

**Legal Risk**: **Low** - Well-established compatibility

#### Apache License 2.0
**Status**: ✅ **Fully Compatible**

**Characteristics**:
- Permissive license with patent grant
- GPLv3/AGPLv3 introduced patent clauses specifically for Apache 2.0 compatibility
- Combined work subject to AGPL v3
- Requires preservation of Apache license notices

**Examples**:
- Apache Avro (Apache 2.0)
- Various Avro-related extensions

**Legal Risk**: **Low** - Explicitly designed for compatibility

### Potentially Problematic Licenses (⚠️ REQUIRES EVALUATION)

#### Eclipse Public License (EPL) 2.0
**Status**: ⚠️ **Compatibility Uncertain**

**Characteristics**:
- Weak copyleft license
- EPL 1.0 is **incompatible** with GPLv2
- EPL 2.0 has "secondary license" mechanism for compatibility
- Red Hat extensions often use EPL 2.0

**Examples**:
- `vscode-xml` by Red Hat (EPL 2.0)
- LemMinX XML Language Server (EPL 2.0)

**Legal Risk**: **Medium to High** - Requires legal review

**Recommendation**:
1. Consult legal counsel for EPL 2.0 + AGPL v3 combination
2. Consider building custom XSD support if incompatible
3. Check if extension offers dual-licensing options

### Incompatible Licenses (❌ DO NOT USE)

- **Proprietary/Closed-Source**: Cannot be combined with AGPL
- **GPL v2 only**: Incompatible with AGPL v3 (unless "or later" clause)
- **Microsoft VS Code Marketplace**: Proprietary extensions (Live Share, Copilot)

---

## Tier-1 Format Extensions (Instance Data)

### JSON (Instance Data)

**Built-in Monaco Support**: ✅

**License**: MIT

**Recommendation**: **✅ USE BUILT-IN**

**Features**:
- Syntax highlighting
- JSON validation
- Bracket matching
- Auto-completion
- Format on paste

**No extension needed** - Monaco has excellent built-in JSON support.

**Implementation**:
```typescript
monaco.languages.register({ id: 'json' });
monaco.editor.create(containerElement, {
  value: jsonContent,
  language: 'json'
});
```

---

### XML (Instance Data)

**Extension Option 1**: Red Hat `vscode-xml`

**License**: EPL 2.0 ⚠️

**Features**:
- XML validation
- XSD/DTD validation
- Autocomplete from XSD
- Documentation on hover
- Tag autocomplete
- Formatting

**Underlying Server**: LemMinX (Eclipse Foundation, EPL 2.0)

**Recommendation**: ⚠️ **EVALUATE OR BUILD ALTERNATIVE**

**Concerns**:
1. EPL 2.0 compatibility with AGPL v3 uncertain
2. LemMinX is Java-based (extra dependency)
3. vscode-xml 0.15.0+ provides native binary (reduces Java dependency)

**Alternative Approach**:
Build custom Monaco XML language mode using TextMate grammar:

```typescript
// Custom XML support
monaco.languages.register({ id: 'xml' });
monaco.languages.setMonarchTokensProvider('xml', {
  tokenizer: {
    root: [
      [/<\?[\w\W]+?\?>/, 'tag'],
      [/<!\[CDATA\[[\w\W]*?\]\]>/, 'string.cdata'],
      [/<!--/, 'comment', '@comment'],
      [/<(\w+)/, { token: 'tag.open', next: '@tag' }],
      // ... additional rules
    ]
  }
});
```

**Decision Criteria**:
- If legal review approves EPL 2.0 → Use `vscode-xml`
- If EPL incompatible → Build custom XML language mode

---

### YAML (Instance Data)

**Extension**: `monaco-yaml`

**License**: MIT ✅

**Repository**: https://github.com/remcohaszing/monaco-yaml

**Recommendation**: **✅ USE**

**Features**:
- Syntax highlighting
- YAML validation
- Schema validation (JSON Schema for YAML)
- Autocomplete based on schema
- Documentation on hover

**Implementation**:
```typescript
import { configureMonacoYaml } from 'monaco-yaml';

configureMonacoYaml(monaco, {
  schemas: [
    {
      uri: 'http://myserver/foo-schema.json',
      fileMatch: ['*.yaml', '*.yml'],
      schema: yamlSchemaObject
    }
  ]
});
```

**Legal Risk**: **Low** (MIT license)

---

### CSV (Instance Data)

**Extension Options**: Limited/None suitable

**Recommendation**: **🛠️ BUILD CUSTOM**

**Rationale**:
1. CSV is simple enough for custom Monaco language mode
2. Most CSV extensions focus on viewing/editing tables (not source editing)
3. Licensing uncertainties with available extensions

**Custom Implementation**:
```typescript
monaco.languages.register({ id: 'csv' });
monaco.languages.setMonarchTokensProvider('csv', {
  tokenizer: {
    root: [
      [/"[^"]*"/, 'string'],
      [/[^,\r\n]+/, 'text'],
      [/,/, 'delimiter']
    ]
  }
});

// Add CSV-specific autocomplete
monaco.languages.registerCompletionItemProvider('csv', {
  provideCompletionItems: (model, position) => {
    // Suggest column headers from first row
    const suggestions = getColumnHeaders(model.getValue());
    return { suggestions };
  }
});
```

**Estimated Effort**: 1-2 days for basic highlighting, 3-5 days for advanced features

---

## Tier-2 Format Extensions (Schema/Metadata)

### JSON Schema (Metadata)

**Built-in Monaco Support**: ✅ (via JSON language mode)

**License**: MIT

**Recommendation**: **✅ USE BUILT-IN + schemastore**

**Extension**: `vscode-schemastore` (optional)

**License**: MIT ✅

**Features**:
- JSON Schema syntax highlighting (via JSON mode)
- Validation against JSON meta-schema
- Autocomplete for JSON Schema keywords
- Documentation on hover

**Implementation**:
```typescript
monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
  validate: true,
  schemas: [
    {
      uri: 'http://json-schema.org/draft-07/schema#',
      fileMatch: ['*.schema.json'],
      schema: jsonSchemaMetaSchema
    }
  ]
});
```

**Legal Risk**: **Low**

---

### XSD (XML Schema Definition)

**Same as XML Instance Data** (see above)

**Extension**: Red Hat `vscode-xml`

**License**: EPL 2.0 ⚠️

**Recommendation**: ⚠️ **EVALUATE OR BUILD ALTERNATIVE**

**Key Difference from XML Instance**:
- XSD files are more complex (namespaces, complex types)
- Schema validation features especially valuable
- Higher value proposition for using `vscode-xml` despite licensing concerns

**Alternative**: Custom TextMate grammar for XSD

```typescript
// XSD-specific highlighting (extends XML)
monaco.languages.register({ id: 'xsd' });
monaco.languages.setLanguageConfiguration('xsd', {
  // ... XML configuration
});
// Add XSD-specific token provider for xs: namespace
```

---

### Avro Schema (.avsc)

**Extension Option 1**: `tgriesser.avro-schemas`

**License**: Not explicitly stated (GitHub repo MIT assumed)

**Repository**: https://github.com/tgriesser/vscode-avro

**Recommendation**: **✅ USE (verify license)**

**Features**:
- Syntax highlighting for .avsc files
- Autocomplete for Avro types
- Validation

**Extension Option 2**: `streetsidesoftware.avro` (Avro IDL)

**License**: MIT ✅

**Recommendation**: **✅ USE** (if Avro IDL support needed)

**Implementation**:
Install via Open VSX or bundle with Theia extension.

**Legal Risk**: **Low** (assuming MIT)

---

### Protobuf (.proto)

**Extension**: `vscode-proto3`

**License**: MIT ✅

**Repository**: https://github.com/zxh0/vscode-proto3

**Recommendation**: **✅ USE**

**Features**:
- Syntax highlighting for .proto files
- Protobuf 3 syntax support
- Basic validation

**Alternative**: `peterj.proto`

**License**: MIT ✅

**Implementation**:
```typescript
// Theia extension loads proto3 extension from Open VSX
{
  "extensionDependencies": [
    "zxh404.vscode-proto3"
  ]
}
```

**Legal Risk**: **Low**

---

## Recommended Integration Strategy

### Phase 1: Use Compatible Extensions (1-2 weeks)

**Immediate Use** (MIT/Apache 2.0):
- ✅ Built-in JSON (Monaco)
- ✅ `monaco-yaml` (MIT)
- ✅ `vscode-proto3` (MIT)
- ✅ Built-in JSON Schema (Monaco JSON mode)
- ✅ Avro extensions (verify MIT)

**Deferred** (Requires legal review):
- ⏸️ Red Hat `vscode-xml` (EPL 2.0)

### Phase 2: Build Custom Language Modes (2-4 weeks)

**Custom Implementations**:
- 🛠️ CSV language mode (1-2 days)
- 🛠️ XML/XSD fallback (if EPL incompatible) (1-2 weeks)

**Approach**:
1. Start with TextMate grammar for syntax highlighting
2. Add Language Server Protocol (LSP) integration for advanced features
3. Implement autocomplete providers
4. Add validation using existing libraries (e.g., `fast-xml-parser` for XML)

### Phase 3: Enhanced Integration (Ongoing)

**Custom Features**:
- Schema-aware autocomplete in design-time mode
- Validation against loaded schemas
- Format-specific actions (e.g., "Prettify XML")
- Integration with UTL-X daemon for format conversion

---

## Implementation Architecture

### Theia Extension Loading

```typescript
// packages/theia-extension/package.json
{
  "name": "@utlx/theia-extension",
  "keywords": ["theia-extension"],
  "dependencies": {
    "@theia/core": "^1.40.0",
    "@theia/monaco": "^1.40.0",
    "monaco-yaml": "^5.0.0"  // Direct dependency
  },
  "theiaExtensions": [
    {
      "frontend": "lib/browser/utlx-frontend-module"
    }
  ],
  // Load VS Code extensions from Open VSX
  "extensionDependencies": [
    "zxh404.vscode-proto3",    // Protobuf
    "tgriesser.avro-schemas"   // Avro
  ]
}
```

### Monaco Editor Language Registration

```typescript
// packages/browser/src/language-registration.ts
import * as monaco from 'monaco-editor';
import { configureMonacoYaml } from 'monaco-yaml';

export function registerLanguages() {
  // JSON (built-in, just configure)
  monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
    validate: true,
    allowComments: false
  });

  // YAML (monaco-yaml)
  configureMonacoYaml(monaco, {
    enableSchemaRequest: true,
    schemas: []
  });

  // CSV (custom)
  registerCSVLanguage();

  // XML (custom or wait for vscode-xml approval)
  if (USE_CUSTOM_XML) {
    registerCustomXMLLanguage();
  } else {
    // vscode-xml extension loaded via Theia
  }
}

function registerCSVLanguage() {
  monaco.languages.register({ id: 'csv', extensions: ['.csv'] });
  monaco.languages.setMonarchTokensProvider('csv', {
    tokenizer: {
      root: [
        [/"(?:[^"\\]|\\.)*"/, 'string'],
        [/[^,\r\n]+/, 'text'],
        [/,/, 'delimiter.csv']
      ]
    }
  });
}
```

### Panel-Specific Extension Activation

```typescript
// packages/browser/src/input-panel-widget.tsx
export class InputPanelWidget extends ReactWidget {
  private editor: monaco.editor.IStandaloneCodeEditor;

  protected render(): React.ReactNode {
    return (
      <div ref={this.containerRef} className="input-panel">
        <MonacoEditor
          language={this.getLanguageForFormat(this.props.inputFormat)}
          value={this.props.inputContent}
          options={{
            minimap: { enabled: false },
            lineNumbers: 'on',
            formatOnPaste: true
          }}
          onChange={this.handleEditorChange}
        />
      </div>
    );
  }

  private getLanguageForFormat(format: string): string {
    const languageMap: Record<string, string> = {
      'xml': 'xml',
      'json': 'json',
      'yaml': 'yaml',
      'csv': 'csv',
      'xsd': 'xsd',      // Custom or vscode-xml
      'jsch': 'json',    // JSON Schema uses JSON mode
      'avsc': 'avro',    // Avro extension
      'proto': 'proto3'  // Protobuf extension
    };
    return languageMap[format] || 'plaintext';
  }
}
```

### LSP Integration for Advanced Features

```typescript
// packages/backend/src/language-server-contribution.ts
@injectable()
export class UTLXLanguageServerContribution implements LanguageServerContribution {
  readonly id = 'utlx';
  readonly name = 'UTLX Language Server';

  async start(clientConnection: IConnection): Promise<void> {
    // Start UTL-X daemon in LSP mode
    const serverProcess = spawn('utlx', ['daemon', '--lsp']);

    // Forward messages between client and server
    serverProcess.stdout.on('data', data => {
      clientConnection.send(data.toString());
    });

    clientConnection.listen(message => {
      serverProcess.stdin.write(JSON.stringify(message) + '\n');
    });
  }
}
```

---

## Licensing Compliance

### Attribution Requirements

#### For MIT Licensed Extensions

**Required**:
1. Include MIT license text in NOTICES file
2. Preserve copyright notices
3. No trademark claims

**Example NOTICES Entry**:
```
This product includes software developed by:

monaco-yaml (https://github.com/remcohaszing/monaco-yaml)
Copyright (c) 2020 Remco Haszing
Licensed under MIT License

vscode-proto3 (https://github.com/zxh0/vscode-proto3)
Copyright (c) 2017 Zhenghao Wang
Licensed under MIT License
```

#### For Apache 2.0 Licensed Extensions

**Required**:
1. Include Apache 2.0 license text
2. Preserve NOTICE file content
3. State modifications (if any)
4. Preserve copyright, patent, trademark notices

**Example**:
```
Apache Avro
Copyright 2010-2025 The Apache Software Foundation
Licensed under Apache License 2.0

This product includes software developed at
The Apache Software Foundation (http://www.apache.org/).
```

#### For EPL 2.0 Licensed Extensions (If Approved)

**Required**:
1. Include EPL 2.0 license text
2. Provide source code availability notice
3. Preserve Eclipse Foundation attribution

**Source Code Provision**:
Since UTL-X is AGPL v3, source code is already provided to users, satisfying EPL requirements.

### AGPL v3 Source Code Disclosure

**Requirement**: Provide complete source code to network users

**Implementation**:
1. **Repository**: GitHub public repository (https://github.com/apache/utl-x)
2. **In-app notice**:
   ```
   This is free software licensed under AGPL v3.
   Source code: https://github.com/apache/utl-x
   ```
3. **Extension bundling**: Include extension source in repository or link to Open VSX

### Extension Bundling vs Dynamic Loading

**Option 1: Bundle Extensions** (Recommended for MIT/Apache)
- Include extension code directly in Theia build
- Satisfies AGPL source disclosure (extension source in repo)
- Simpler deployment

**Option 2: Dynamic Loading from Open VSX**
- Load extensions at runtime from Open VSX registry
- Users can verify extension licenses independently
- More complex, but clearer license boundaries

**Recommendation**: **Bundle MIT/Apache extensions**, **dynamically load** EPL extensions (if approved)

---

## Alternatives if Extensions Unavailable

### Building Custom Monaco Language Modes

#### Approach 1: TextMate Grammar (Syntax Highlighting Only)

**Effort**: 1-3 days per language

**Features**:
- Syntax highlighting
- Bracket matching
- Comment toggling

**Implementation**:
```typescript
monaco.languages.register({ id: 'xml' });
monaco.languages.setMonarchTokensProvider('xml', xmlTokenProvider);
```

**Limitations**:
- No autocomplete
- No validation
- No hover documentation

#### Approach 2: Language Server Protocol (Full IDE Features)

**Effort**: 1-2 weeks per language

**Features**:
- Syntax highlighting
- Autocomplete
- Validation
- Hover documentation
- Go to definition

**Implementation**:
1. Use existing open-source language servers:
   - XML: `xml-language-server` (if not EPL)
   - JSON: Built-in Monaco
   - YAML: `yaml-language-server` (used by monaco-yaml)

2. Integrate via Theia's LSP infrastructure:
```typescript
@injectable()
export class XMLLanguageServerContribution implements LanguageServerContribution {
  readonly id = 'xml';
  readonly name = 'XML Language Server';

  async start(clientConnection: IConnection): Promise<void> {
    const serverProcess = spawn('xml-language-server', ['--stdio']);
    forward(clientConnection, serverProcess);
  }
}
```

#### Approach 3: Monaco Completion/Hover Providers (Hybrid)

**Effort**: 3-5 days per language

**Features**:
- Syntax highlighting (TextMate)
- Autocomplete (custom)
- Validation (custom)
- Hover docs (custom)

**Implementation**:
```typescript
// CSV autocomplete example
monaco.languages.registerCompletionItemProvider('csv', {
  provideCompletionItems: (model, position) => {
    const line = model.getLineContent(1); // Header row
    const columns = parseCSVLine(line);

    return {
      suggestions: columns.map((col, idx) => ({
        label: col,
        kind: monaco.languages.CompletionItemKind.Field,
        insertText: col,
        detail: `Column ${idx + 1}`
      }))
    };
  }
});

// XML validation example
monaco.editor.onDidChangeModelContent(model => {
  if (model.getLanguageId() === 'xml') {
    const errors = validateXML(model.getValue());
    monaco.editor.setModelMarkers(model, 'xml', errors.map(err => ({
      severity: monaco.MarkerSeverity.Error,
      startLineNumber: err.line,
      startColumn: err.column,
      endLineNumber: err.line,
      endColumn: err.column + err.length,
      message: err.message
    })));
  }
});
```

### Using Existing Parsing Libraries

For validation without full LSP:

**XML**:
- `fast-xml-parser` (MIT) - Fast XML parsing and validation
- `xmldom` (MIT/LGPL dual) - DOM-based XML parsing

**XSD**:
- `xsd-schema-validator` (MIT) - XSD validation in JavaScript
- `libxmljs` (MIT) - LibXML bindings for Node.js

**CSV**:
- `papaparse` (MIT) - CSV parsing with error detection
- Native string split (no dependencies)

**Avro**:
- `avsc` (MIT) - Avro schema validation and encoding

**Protobuf**:
- `protobufjs` (BSD-3) - Protobuf parsing and validation

**Example**:
```typescript
import { XMLParser } from 'fast-xml-parser';

function validateXML(xmlContent: string): Diagnostic[] {
  const parser = new XMLParser({ ignoreAttributes: false });
  try {
    parser.parse(xmlContent);
    return []; // Valid
  } catch (error) {
    return [{
      line: error.line,
      column: error.column,
      message: error.message,
      severity: 'error'
    }];
  }
}
```

---

## Conclusion and Recommendations

### Final Recommendations

#### ✅ RECOMMENDED FOR IMMEDIATE USE

| Format | Extension/Approach | License | Risk Level |
|--------|-------------------|---------|-----------|
| JSON | Monaco built-in | MIT | ✅ Low |
| YAML | `monaco-yaml` | MIT | ✅ Low |
| Protobuf | `vscode-proto3` | MIT | ✅ Low |
| JSON Schema | Monaco JSON mode | MIT | ✅ Low |
| Avro | `tgriesser.avro-schemas` | MIT (verify) | ✅ Low |

#### ⚠️ REQUIRES LEGAL REVIEW

| Format | Extension | License | Action Required |
|--------|-----------|---------|----------------|
| XML/XSD | Red Hat `vscode-xml` | EPL 2.0 | Legal review for EPL + AGPL compatibility |

#### 🛠️ BUILD CUSTOM IMPLEMENTATION

| Format | Rationale | Estimated Effort |
|--------|-----------|-----------------|
| CSV | Simple format, no suitable extension | 1-2 days (basic)<br>3-5 days (advanced) |
| XML/XSD (fallback) | If EPL incompatible | 1-2 weeks (LSP)<br>3-5 days (basic) |

### Implementation Phases

**Phase 1** (Week 1-2): MIT/Apache Extensions
- Integrate `monaco-yaml`
- Integrate `vscode-proto3`
- Configure built-in JSON/JSON Schema
- Integrate Avro extensions (verify license)

**Phase 2** (Week 3): Legal Review & Custom CSV
- Submit EPL 2.0 compatibility question to legal
- Build custom CSV language mode
- Test extension loading via Open VSX

**Phase 3** (Week 4-5): XML/XSD Decision
- If EPL approved: Integrate `vscode-xml`
- If EPL not approved: Build custom XML/XSD support
- Implement LSP integration if custom approach

**Phase 4** (Week 6+): Enhanced Features
- Schema-aware autocomplete in design-time mode
- Format-specific validation
- Integration with UTL-X daemon for conversions

### Legal Checklist

Before production release:

- [ ] Legal review of EPL 2.0 + AGPL v3 compatibility
- [ ] NOTICES file with all MIT/Apache attributions
- [ ] Source code availability notice in UI
- [ ] Verify all extension licenses in Open VSX match expectations
- [ ] Document extension bundling decisions
- [ ] Review Theia extension license compatibility
- [ ] Confirm Monaco Editor license (MIT) attribution

### Success Metrics

1. **Format Coverage**: All tier-1 and tier-2 formats supported
2. **License Compliance**: 100% AGPL-compatible stack
3. **User Experience**: Syntax highlighting, validation, autocomplete for all formats
4. **Performance**: Editor loads < 2s, validation < 100ms
5. **Maintainability**: Clear license documentation, minimal custom code

---

**Document Version:** 1.0
**Last Updated:** 2025-11-01
**Related Documents:**
- `theia-extension-design-with-design-time.md` - Design-time vs runtime architecture
- `theia-extension-api-reference.md` - Theia extension API
- `io-theia-explained.md` - Theia backend/daemon communication

**Next Steps:**
1. Legal review of EPL 2.0 compatibility
2. Create technical spike for `monaco-yaml` integration
3. Build proof-of-concept CSV language mode
4. Test extension loading from Open VSX
