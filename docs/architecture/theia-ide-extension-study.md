# Eclipse Theia IDE Extension for UTL-X: Comprehensive Design Study

**Document Version:** 1.0
**Last Updated:** 2025-10-29
**Status:** Design Study & Architecture Proposal
**Author:** UTL-X Architecture Team
**Related Documents:**
- [validation-lsp-integration-architecture.md](./validation-lsp-integration-architecture.md)
- [validation-and-analysis-study.md](./validation-and-analysis-study.md)

---

## Executive Summary

This document presents a comprehensive design study for an **Eclipse Theia extension** that provides an integrated transformation development environment for UTL-X. The extension features a unique **three-panel layout** that revolutionizes how developers write and test data transformations:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Eclipse Theia - UTL-X IDE                        │
├─────────────┬─────────────────────────────┬──────────────────────────┤
│   INPUT     │       TRANSFORMATION        │        OUTPUT            │
│   PANEL     │       (UTL-X Editor)        │        PANEL             │
│             │       with LSP              │                          │
│  ┌────────┐ │  ┌────────────────────────┐ │  ┌──────────────────┐   │
│  │ input1 │ │  │ %utlx 1.0              │ │  │ Output Preview   │   │
│  │ .xml   │ │  │ input xml              │ │  │                  │   │
│  │        │ │  │ output json            │ │  │ Live execution   │   │
│  │ [View] │ │  │ ---                    │ │  │ or validation    │   │
│  └────────┘ │  │ {                      │ │  │                  │   │
│             │  │   result: $input.data  │ │  │ [JSON Viewer]    │   │
│  ┌────────┐ │  │ }                      │ │  │                  │   │
│  │ input2 │ │  │                        │ │  │ Diff view        │   │
│  │ .json  │ │  │ [Diagnostics inline]   │ │  │ available        │   │
│  │        │ │  │                        │ │  │                  │   │
│  │ [View] │ │  │ [Autocomplete]         │ │  └──────────────────┘   │
│  └────────┘ │  │                        │ │                          │
│             │  │ [Syntax highlighting]  │ │  ┌──────────────────┐   │
│  [+ Add]    │  │                        │ │  │ Error Details    │   │
│             │  └────────────────────────┘ │  │                  │   │
│             │                             │  │ • Line 12: ...   │   │
│             │  [Status: ✓ Valid]          │  │ • Suggestion:... │   │
│             │                             │  └──────────────────┘   │
└─────────────┴─────────────────────────────┴──────────────────────────┘
```

**Key Features:**

1. **Multi-Input Management** (Left Panel)
   - Add/remove multiple input documents
   - Support for XML, JSON, CSV, YAML, XSD, JSON Schema
   - Quick preview and validation
   - Schema association

2. **Smart UTL-X Editor** (Center Panel)
   - Full LSP integration (diagnostics, completion, hover)
   - Real-time validation
   - Syntax highlighting
   - Multi-input variable assistance

3. **Live Output Preview** (Right Panel)
   - Real-time transformation execution
   - Multiple output formats
   - Diff view (expected vs actual)
   - Error correlation with source

**Why Theia vs VS Code?**

| Feature | VS Code | Eclipse Theia |
|---------|---------|---------------|
| **Extensibility** | Limited to extension API | Full IDE customization |
| **Custom Layouts** | ❌ Fixed layout | ✅ Custom panel layouts |
| **Web Deployment** | ❌ Desktop only | ✅ Browser + Desktop |
| **Custom UI** | Limited | Full control |
| **Multi-root Workspace** | Basic | Advanced |
| **IDE Integration** | Standalone | Can embed in products |

**Theia enables the three-panel layout that VS Code cannot provide.**

---

## Table of Contents

1. [Eclipse Theia Background](#1-eclipse-theia-background)
2. [Extension Architecture](#2-extension-architecture)
3. [Three-Panel Layout Design](#3-three-panel-layout-design)
4. [Input Panel Implementation](#4-input-panel-implementation)
5. [Center Editor Panel](#5-center-editor-panel)
6. [Output Panel Implementation](#6-output-panel-implementation)
7. [LSP Integration](#7-lsp-integration)
8. [Real-Time Execution Engine](#8-real-time-execution-engine)
9. [Multi-Input Coordination](#9-multi-input-coordination)
10. [User Experience Workflows](#10-user-experience-workflows)
11. [Advanced Features](#11-advanced-features)
12. [Deployment Options](#12-deployment-options)
13. [Implementation Roadmap](#13-implementation-roadmap)
14. [Comparison with Alternatives](#14-comparison-with-alternatives)

---

## 1. Eclipse Theia Background

### 1.1 What is Eclipse Theia?

**Eclipse Theia** is an open-source cloud and desktop IDE framework that can be extended to create custom IDEs.

**Key Properties:**
- Built on the same technologies as VS Code (Monaco editor, TypeScript, Electron)
- Fully extensible architecture (not just extension API)
- Supports both desktop (Electron) and browser deployments
- Compatible with VS Code extensions (mostly)
- Modular dependency injection architecture

**Theia vs VS Code:**

```
VS Code Architecture:
┌────────────────────────────────┐
│      VS Code Core              │
│      (Closed)                  │
│  ┌──────────────────────────┐  │
│  │  Extension API           │  │ ← Limited customization
│  │  (Limited surface area)  │  │
│  └──────────────────────────┘  │
└────────────────────────────────┘

Theia Architecture:
┌────────────────────────────────┐
│      Theia Core                │
│      (Open, Modular)           │
│  ┌──────────────────────────┐  │
│  │  Extensions              │  │
│  │  (Full access)           │  │
│  ├──────────────────────────┤  │
│  │  Custom Frontends        │  │ ← Full customization
│  │  Custom Layouts          │  │
│  │  Custom Services         │  │
│  └──────────────────────────┘  │
└────────────────────────────────┘
```

### 1.2 Theia Extension Types

**Three levels of customization:**

1. **VS Code Extensions** (Limited)
   - Runs existing VS Code extensions
   - Limited to VS Code extension API
   - Can't customize layout

2. **Theia Extensions** (Powerful)
   - Full access to Theia architecture
   - Can add custom views, panels, services
   - Can modify workbench layout

3. **Theia Applications** (Complete Control)
   - Custom IDE built on Theia
   - Brand your own product
   - Full UI/UX control

**For UTL-X:** We'll use **Theia Extensions** to add custom three-panel layout.

### 1.3 Theia Technology Stack

```
┌─────────────────────────────────────────┐
│         Frontend (Browser/Electron)     │
│  ┌────────────────────────────────────┐ │
│  │  Monaco Editor                     │ │
│  │  React Components                  │ │
│  │  Theia Workbench                   │ │
│  │  Custom Panels (UTL-X)             │ │
│  └────────────────────────────────────┘ │
└─────────────────┬───────────────────────┘
                  │ JSON-RPC
┌─────────────────┴───────────────────────┐
│         Backend (Node.js)               │
│  ┌────────────────────────────────────┐ │
│  │  Language Servers (LSP)            │ │
│  │  File System                       │ │
│  │  Process Management                │ │
│  │  UTL-X Execution Engine            │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

**Key Technologies:**
- **TypeScript**: All extension code
- **Monaco Editor**: Code editing (same as VS Code)
- **Inversify**: Dependency injection
- **JSON-RPC**: Frontend ↔ Backend communication
- **LSP**: Language server protocol

### 1.4 Why Theia for UTL-X?

**Requirements that demand Theia:**

1. ✅ **Custom Layout**: Three-panel side-by-side layout
2. ✅ **Live Preview**: Real-time transformation execution
3. ✅ **Multi-Input UI**: Specialized input document management
4. ✅ **Output Visualization**: Custom output rendering (JSON tree, XML formatted)
5. ✅ **Integrated Workflow**: Tight coupling between inputs, transform, and output
6. ✅ **Web Deployment**: Cloud-hosted IDE option

**VS Code cannot provide:**
- ❌ Custom workbench layouts (fixed sidebar + editor + panel)
- ❌ Synchronized multi-panel updates
- ❌ Embedded transformation execution UI

**Alternative considered: JetBrains MPS**
- ✅ Powerful language workbench
- ❌ Desktop only (no web)
- ❌ Steeper learning curve
- ❌ JVM-based (heavier)

---

## 2. Extension Architecture

### 2.1 High-Level Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                    THEIA WORKBENCH                                │
├───────────────┬──────────────────────────┬────────────────────────┤
│               │                          │                        │
│  INPUT PANEL  │   EDITOR AREA            │   OUTPUT PANEL         │
│  (Custom)     │   (Monaco + LSP)         │   (Custom)             │
│               │                          │                        │
│  React        │   Monaco Editor          │   React                │
│  Component    │   + UTL-X LSP Client     │   Component            │
│               │                          │                        │
└───────┬───────┴─────────┬────────────────┴────────┬───────────────┘
        │                 │                         │
        └─────────────────┴─────────────────────────┘
                          │
┌─────────────────────────┼─────────────────────────────────────────┐
│              THEIA BACKEND (Node.js)                              │
│                                                                   │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐ │
│  │  Input Manager   │  │  UTL-X LSP       │  │  Execution     │ │
│  │  Service         │  │  Server          │  │  Engine        │ │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬───────┘ │
│           │                     │                      │         │
│           └─────────────────────┴──────────────────────┘         │
│                                 │                                │
│                    ┌────────────▼──────────────┐                 │
│                    │  Coordination Service     │                 │
│                    │  - Multi-input tracking   │                 │
│                    │  - Live execution         │                 │
│                    │  - Output management      │                 │
│                    └───────────────────────────┘                 │
└───────────────────────────────────────────────────────────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │  UTL-X Runtime (JVM)      │
                    │  - Parse & validate       │
                    │  - Execute transformation │
                    │  - Format output          │
                    └───────────────────────────┘
```

### 2.2 Frontend Components

**Package Structure:**

```
utlx-theia-extension/
├── frontend/
│   ├── input-panel/
│   │   ├── input-panel-widget.tsx          # Main input panel
│   │   ├── input-document-item.tsx         # Individual input
│   │   ├── input-selector.tsx              # Add input dialog
│   │   └── input-panel-service.ts          # State management
│   │
│   ├── output-panel/
│   │   ├── output-panel-widget.tsx         # Main output panel
│   │   ├── output-viewer.tsx               # Format-specific viewer
│   │   ├── diff-viewer.tsx                 # Expected vs actual
│   │   └── error-correlation.tsx           # Link errors to source
│   │
│   ├── editor/
│   │   ├── utlx-editor-contribution.ts     # Editor customization
│   │   ├── utlx-language-client.ts         # LSP client
│   │   └── utlx-commands.ts                # Custom commands
│   │
│   ├── workbench/
│   │   ├── utlx-workbench-layout.ts        # Three-panel layout
│   │   └── utlx-perspective.ts             # Default perspective
│   │
│   └── frontend-module.ts                  # DI bindings
│
├── backend/
│   ├── services/
│   │   ├── input-manager-service.ts        # Input document tracking
│   │   ├── execution-engine-service.ts     # Transform execution
│   │   ├── output-manager-service.ts       # Output handling
│   │   └── coordination-service.ts         # Orchestration
│   │
│   ├── lsp/
│   │   ├── utlx-language-server.ts         # LSP server wrapper
│   │   └── utlx-lsp-contribution.ts        # LSP registration
│   │
│   └── backend-module.ts                   # DI bindings
│
└── common/
    ├── protocol.ts                         # Frontend ↔ Backend protocol
    ├── types.ts                            # Shared types
    └── constants.ts                        # Constants
```

### 2.3 Dependency Injection (Inversify)

**Theia uses Inversify for DI:**

```typescript
// frontend-module.ts
import { ContainerModule } from 'inversify';
import { WidgetFactory } from '@theia/core/lib/browser';

export default new ContainerModule(bind => {
    // Input Panel
    bind(InputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: INPUT_PANEL_ID,
        createWidget: () => ctx.container.get(InputPanelWidget)
    })).inSingletonScope();

    // Output Panel
    bind(OutputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: OUTPUT_PANEL_ID,
        createWidget: () => ctx.container.get(OutputPanelWidget)
    })).inSingletonScope();

    // Services
    bind(InputPanelService).toSelf().inSingletonScope();
    bind(UTLXWorkbenchLayout).toSelf().inSingletonScope();
    bind(CoordinationService).toSelf().inSingletonScope();

    // Commands
    bind(CommandContribution).to(UTLXCommandContribution);
    bind(MenuContribution).to(UTLXMenuContribution);
});
```

### 2.4 Communication Protocol

**Frontend ↔ Backend via JSON-RPC:**

```typescript
// common/protocol.ts
export const UTLX_SERVICE_PATH = '/services/utlx';

export const UTLXService = Symbol('UTLXService');

export interface UTLXService {
    /**
     * Add input document
     */
    addInputDocument(name: string, content: string, format: string): Promise<InputDocument>;

    /**
     * Remove input document
     */
    removeInputDocument(id: string): Promise<void>;

    /**
     * Execute transformation with current inputs
     */
    executeTransformation(transformCode: string, inputs: InputDocument[]): Promise<ExecutionResult>;

    /**
     * Validate transformation
     */
    validateTransformation(transformCode: string): Promise<ValidationResult>;

    /**
     * Get available stdlib functions
     */
    getStdlibFunctions(): Promise<StdlibFunction[]>;
}

export interface InputDocument {
    id: string;
    name: string;
    content: string;
    format: 'xml' | 'json' | 'csv' | 'yaml';
    schema?: string;  // Optional XSD/JSON Schema
}

export interface ExecutionResult {
    success: boolean;
    output?: string;
    outputFormat?: string;
    errors?: ExecutionError[];
    executionTime?: number;
}

export interface ValidationResult {
    valid: boolean;
    diagnostics: Diagnostic[];
}

export interface Diagnostic {
    severity: 'error' | 'warning' | 'info' | 'hint';
    line: number;
    column: number;
    message: string;
    code?: string;
    quickFixes?: QuickFix[];
}
```

---

## 3. Three-Panel Layout Design

### 3.1 Layout Configuration

**Theia Layout Areas:**

```
┌─────────────────────────────────────────────────────────────┐
│  Top Area (Menu Bar, Toolbar)                              │
├──────────┬────────────────────────────────┬─────────────────┤
│          │                                │                 │
│  Left    │   Main Area (Editor)           │   Right         │
│  Sidebar │                                │   Sidebar       │
│          │                                │                 │
│  (Input  │   (UTL-X Editor with LSP)      │   (Output       │
│   Panel) │                                │    Panel)       │
│          │                                │                 │
│          │                                │                 │
├──────────┴────────────────────────────────┴─────────────────┤
│  Bottom Area (Problems, Terminal, etc.)                     │
└─────────────────────────────────────────────────────────────┘
```

**UTL-X Custom Layout:**

```typescript
// workbench/utlx-workbench-layout.ts
import { injectable, inject } from 'inversify';
import { ApplicationShell } from '@theia/core/lib/browser';
import { INPUT_PANEL_ID, OUTPUT_PANEL_ID } from '../common/constants';

@injectable()
export class UTLXWorkbenchLayout {

    constructor(
        @inject(ApplicationShell) private readonly shell: ApplicationShell
    ) {}

    /**
     * Setup three-panel layout
     */
    async setupLayout(): Promise<void> {
        // Open input panel in left sidebar
        const inputPanel = await this.shell.openWidget(INPUT_PANEL_ID, {
            area: 'left',
            rank: 100
        });

        // Open output panel in right sidebar
        const outputPanel = await this.shell.openWidget(OUTPUT_PANEL_ID, {
            area: 'right',
            rank: 100
        });

        // Activate input panel
        this.shell.activateWidget(inputPanel.id);

        // Set sidebar widths
        this.shell.leftPanelHandler.resize(400);  // 400px for input
        this.shell.rightPanelHandler.resize(500); // 500px for output
    }

    /**
     * Toggle input panel
     */
    toggleInputPanel(): void {
        const widget = this.shell.getWidgets('left')
            .find(w => w.id === INPUT_PANEL_ID);

        if (widget) {
            if (this.shell.isVisible(widget)) {
                this.shell.closeWidget(widget.id);
            } else {
                this.shell.activateWidget(widget.id);
            }
        }
    }

    /**
     * Toggle output panel
     */
    toggleOutputPanel(): void {
        const widget = this.shell.getWidgets('right')
            .find(w => w.id === OUTPUT_PANEL_ID);

        if (widget) {
            if (this.shell.isVisible(widget)) {
                this.shell.closeWidget(widget.id);
            } else {
                this.shell.activateWidget(widget.id);
            }
        }
    }
}
```

### 3.2 Panel Sizing and Responsiveness

**Responsive layout rules:**

```typescript
/**
 * Dynamic panel sizing based on window width
 */
export class ResponsiveLayoutManager {

    private readonly MIN_INPUT_WIDTH = 300;
    private readonly MIN_OUTPUT_WIDTH = 350;
    private readonly MIN_EDITOR_WIDTH = 500;

    updateLayout(windowWidth: number): void {
        if (windowWidth < 1200) {
            // Small screen: Stack panels vertically
            this.shell.leftPanelHandler.collapse();
            this.shell.bottomPanelHandler.expand();
            this.moveInputPanelToBottom();
        } else if (windowWidth < 1600) {
            // Medium screen: Standard three-panel
            this.shell.leftPanelHandler.resize(300);
            this.shell.rightPanelHandler.resize(400);
        } else {
            // Large screen: Wider panels
            this.shell.leftPanelHandler.resize(400);
            this.shell.rightPanelHandler.resize(600);
        }
    }
}
```

### 3.3 Panel State Persistence

**Save/restore panel states:**

```typescript
// workbench/utlx-perspective.ts
import { injectable } from 'inversify';
import { FrontendApplicationStateService } from '@theia/core/lib/browser';

@injectable()
export class UTLXPerspective {

    private readonly STORAGE_KEY = 'utlx.layout.state';

    /**
     * Save current layout state
     */
    saveLayoutState(): void {
        const state = {
            inputPanelWidth: this.shell.leftPanelHandler.getSize(),
            outputPanelWidth: this.shell.rightPanelHandler.getSize(),
            inputPanelVisible: this.shell.isVisible(INPUT_PANEL_ID),
            outputPanelVisible: this.shell.isVisible(OUTPUT_PANEL_ID),
            bottomPanelExpanded: !this.shell.bottomPanelHandler.isCollapsed()
        };

        localStorage.setItem(this.STORAGE_KEY, JSON.stringify(state));
    }

    /**
     * Restore saved layout state
     */
    async restoreLayoutState(): Promise<void> {
        const savedState = localStorage.getItem(this.STORAGE_KEY);

        if (savedState) {
            const state = JSON.parse(savedState);

            // Restore panel widths
            this.shell.leftPanelHandler.resize(state.inputPanelWidth);
            this.shell.rightPanelHandler.resize(state.outputPanelWidth);

            // Restore visibility
            if (!state.inputPanelVisible) {
                this.shell.closeWidget(INPUT_PANEL_ID);
            }

            if (!state.outputPanelVisible) {
                this.shell.closeWidget(OUTPUT_PANEL_ID);
            }

            // Restore bottom panel
            if (state.bottomPanelExpanded) {
                this.shell.bottomPanelHandler.expand();
            }
        }
    }
}
```

---

## 4. Input Panel Implementation

### 4.1 Input Panel Widget

```typescript
// frontend/input-panel/input-panel-widget.tsx
import * as React from 'react';
import { injectable, inject } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { InputPanelService } from './input-panel-service';
import { InputDocument } from '../../common/protocol';

@injectable()
export class InputPanelWidget extends ReactWidget {

    static readonly ID = 'utlx-input-panel';
    static readonly LABEL = 'Inputs';

    constructor(
        @inject(InputPanelService) private readonly service: InputPanelService
    ) {
        super();
        this.id = InputPanelWidget.ID;
        this.title.label = InputPanelWidget.LABEL;
        this.title.closable = true;
        this.title.iconClass = 'fa fa-file-import';

        // Listen to input changes
        this.service.onInputsChanged(() => this.update());
    }

    protected render(): React.ReactNode {
        const inputs = this.service.getInputs();

        return (
            <div className='utlx-input-panel'>
                <div className='utlx-input-panel-toolbar'>
                    <button
                        className='theia-button primary'
                        onClick={() => this.handleAddInput()}
                    >
                        <i className='fa fa-plus' /> Add Input
                    </button>

                    <button
                        className='theia-button secondary'
                        onClick={() => this.handleValidateInputs()}
                        disabled={inputs.length === 0}
                    >
                        <i className='fa fa-check' /> Validate All
                    </button>
                </div>

                <div className='utlx-input-list'>
                    {inputs.length === 0 ? (
                        <div className='utlx-input-empty'>
                            <p>No input documents</p>
                            <p className='hint'>Add input files to test your transformation</p>
                        </div>
                    ) : (
                        inputs.map(input => (
                            <InputDocumentItem
                                key={input.id}
                                input={input}
                                onView={() => this.handleViewInput(input)}
                                onEdit={() => this.handleEditInput(input)}
                                onRemove={() => this.handleRemoveInput(input)}
                            />
                        ))
                    )}
                </div>

                <div className='utlx-input-panel-status'>
                    {inputs.length} input(s)
                </div>
            </div>
        );
    }

    private async handleAddInput(): Promise<void> {
        const dialog = new InputSelectorDialog();
        const result = await dialog.open();

        if (result) {
            await this.service.addInput(result);
        }
    }

    private async handleViewInput(input: InputDocument): Promise<void> {
        // Open in read-only editor
        await this.editorManager.open(
            new URI(`utlx-input:${input.id}`),
            { mode: 'open', preview: true }
        );
    }

    private async handleEditInput(input: InputDocument): Promise<void> {
        // Open in editable editor
        await this.editorManager.open(
            new URI(`utlx-input:${input.id}`),
            { mode: 'open', preview: false }
        );
    }

    private async handleRemoveInput(input: InputDocument): Promise<void> {
        const confirmed = await this.messageService.confirm({
            title: 'Remove Input',
            msg: `Remove input '${input.name}'?`
        });

        if (confirmed) {
            await this.service.removeInput(input.id);
        }
    }

    private async handleValidateInputs(): Promise<void> {
        await this.service.validateAllInputs();
    }
}
```

### 4.2 Input Document Item Component

```typescript
// frontend/input-panel/input-document-item.tsx
import * as React from 'react';
import { InputDocument } from '../../common/protocol';

export interface InputDocumentItemProps {
    input: InputDocument;
    onView: () => void;
    onEdit: () => void;
    onRemove: () => void;
}

export const InputDocumentItem: React.FC<InputDocumentItemProps> = ({
    input,
    onView,
    onEdit,
    onRemove
}) => {
    const [expanded, setExpanded] = React.useState(false);

    const formatIcon = {
        xml: 'fa-file-code',
        json: 'fa-file-code',
        csv: 'fa-file-csv',
        yaml: 'fa-file-code'
    }[input.format] || 'fa-file';

    const formatColor = {
        xml: '#e34c26',
        json: '#f0db4f',
        csv: '#2ecc71',
        yaml: '#cb171e'
    }[input.format] || '#555';

    return (
        <div className='utlx-input-item'>
            <div
                className='utlx-input-item-header'
                onClick={() => setExpanded(!expanded)}
            >
                <i className={`fa ${formatIcon}`} style={{ color: formatColor }} />
                <span className='utlx-input-item-name'>{input.name}</span>
                <span className='utlx-input-item-format'>{input.format.toUpperCase()}</span>

                <div className='utlx-input-item-actions'>
                    <button
                        className='theia-button icon'
                        onClick={(e) => { e.stopPropagation(); onView(); }}
                        title='View'
                    >
                        <i className='fa fa-eye' />
                    </button>

                    <button
                        className='theia-button icon'
                        onClick={(e) => { e.stopPropagation(); onEdit(); }}
                        title='Edit'
                    >
                        <i className='fa fa-edit' />
                    </button>

                    <button
                        className='theia-button icon danger'
                        onClick={(e) => { e.stopPropagation(); onRemove(); }}
                        title='Remove'
                    >
                        <i className='fa fa-trash' />
                    </button>
                </div>
            </div>

            {expanded && (
                <div className='utlx-input-item-details'>
                    <div className='utlx-input-item-preview'>
                        <pre>{input.content.substring(0, 200)}...</pre>
                    </div>

                    {input.schema && (
                        <div className='utlx-input-item-schema'>
                            <i className='fa fa-check-circle' style={{ color: '#2ecc71' }} />
                            Schema: {input.schema}
                        </div>
                    )}

                    <div className='utlx-input-item-stats'>
                        Size: {formatBytes(input.content.length)}
                    </div>
                </div>
            )}
        </div>
    );
};

function formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
```

### 4.3 Input Selector Dialog

```typescript
// frontend/input-panel/input-selector.tsx
import * as React from 'react';
import { DialogProps } from '@theia/core/lib/browser';
import { ReactDialog } from '@theia/core/lib/browser/dialogs/react-dialog';

export interface InputSelection {
    name: string;
    content: string;
    format: 'xml' | 'json' | 'csv' | 'yaml';
    schema?: string;
}

export class InputSelectorDialog extends ReactDialog<InputSelection> {

    protected readonly nameInput = React.createRef<HTMLInputElement>();
    protected readonly contentInput = React.createRef<HTMLTextAreaElement>();
    protected readonly formatSelect = React.createRef<HTMLSelectElement>();
    protected readonly schemaInput = React.createRef<HTMLInputElement>();

    constructor() {
        super({
            title: 'Add Input Document'
        });

        this.appendAcceptButton('Add');
        this.appendCloseButton('Cancel');
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-input-selector'>
                <div className='theia-form-group'>
                    <label>Name:</label>
                    <input
                        ref={this.nameInput}
                        type='text'
                        className='theia-input'
                        placeholder='e.g., input1, order-data'
                        autoFocus
                    />
                </div>

                <div className='theia-form-group'>
                    <label>Format:</label>
                    <select ref={this.formatSelect} className='theia-select'>
                        <option value='xml'>XML</option>
                        <option value='json'>JSON</option>
                        <option value='csv'>CSV</option>
                        <option value='yaml'>YAML</option>
                    </select>
                </div>

                <div className='theia-form-group'>
                    <label>Content:</label>
                    <div className='utlx-input-content-options'>
                        <button
                            className='theia-button secondary'
                            onClick={() => this.handleUploadFile()}
                        >
                            <i className='fa fa-upload' /> Upload File
                        </button>

                        <button
                            className='theia-button secondary'
                            onClick={() => this.handlePasteContent()}
                        >
                            <i className='fa fa-clipboard' /> Paste
                        </button>

                        <button
                            className='theia-button secondary'
                            onClick={() => this.handleSelectFromWorkspace()}
                        >
                            <i className='fa fa-folder-open' /> From Workspace
                        </button>
                    </div>

                    <textarea
                        ref={this.contentInput}
                        className='theia-input'
                        rows={10}
                        placeholder='Paste content or upload file...'
                    />
                </div>

                <div className='theia-form-group'>
                    <label>
                        Schema (optional):
                        <span className='hint'>XSD or JSON Schema file</span>
                    </label>
                    <input
                        ref={this.schemaInput}
                        type='text'
                        className='theia-input'
                        placeholder='path/to/schema.xsd'
                    />
                </div>
            </div>
        );
    }

    protected get value(): InputSelection {
        return {
            name: this.nameInput.current?.value || '',
            content: this.contentInput.current?.value || '',
            format: (this.formatSelect.current?.value as any) || 'xml',
            schema: this.schemaInput.current?.value || undefined
        };
    }

    protected isValid(): boolean {
        const name = this.nameInput.current?.value;
        const content = this.contentInput.current?.value;

        return !!name && name.trim().length > 0 &&
               !!content && content.trim().length > 0;
    }

    private async handleUploadFile(): Promise<void> {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.xml,.json,.csv,.yaml,.yml';

        input.onchange = async (e) => {
            const file = (e.target as HTMLInputElement).files?.[0];
            if (file) {
                const content = await file.text();
                this.contentInput.current!.value = content;

                // Auto-detect format from extension
                const ext = file.name.split('.').pop()?.toLowerCase();
                if (ext && ['xml', 'json', 'csv', 'yaml', 'yml'].includes(ext)) {
                    this.formatSelect.current!.value = ext === 'yml' ? 'yaml' : ext;
                }

                // Auto-fill name
                if (!this.nameInput.current?.value) {
                    this.nameInput.current!.value = file.name.replace(/\.[^/.]+$/, '');
                }
            }
        };

        input.click();
    }

    private async handlePasteContent(): Promise<void> {
        try {
            const text = await navigator.clipboard.readText();
            this.contentInput.current!.value = text;

            // Try to auto-detect format
            const format = this.detectFormat(text);
            if (format) {
                this.formatSelect.current!.value = format;
            }
        } catch (error) {
            console.error('Failed to read clipboard:', error);
        }
    }

    private async handleSelectFromWorkspace(): Promise<void> {
        // Open file dialog to select from workspace
        const uri = await this.fileDialogService.showOpenDialog({
            title: 'Select Input File',
            filters: {
                'Data Files': ['xml', 'json', 'csv', 'yaml', 'yml']
            }
        });

        if (uri) {
            const content = await this.fileService.read(uri);
            this.contentInput.current!.value = content.value;

            const fileName = uri.path.base;
            if (!this.nameInput.current?.value) {
                this.nameInput.current!.value = fileName.replace(/\.[^/.]+$/, '');
            }

            const ext = uri.path.ext.substring(1).toLowerCase();
            if (['xml', 'json', 'csv', 'yaml', 'yml'].includes(ext)) {
                this.formatSelect.current!.value = ext === 'yml' ? 'yaml' : ext;
            }
        }
    }

    private detectFormat(content: string): 'xml' | 'json' | 'csv' | 'yaml' | null {
        const trimmed = content.trim();

        if (trimmed.startsWith('<')) return 'xml';
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) return 'json';
        if (trimmed.split('\n')[0].includes(',')) return 'csv';
        if (trimmed.includes(': ') || trimmed.includes('- ')) return 'yaml';

        return null;
    }
}
```

### 4.4 Input Panel Service

```typescript
// frontend/input-panel/input-panel-service.ts
import { injectable, inject } from 'inversify';
import { Emitter, Event } from '@theia/core';
import { UTLXService, InputDocument } from '../../common/protocol';

@injectable()
export class InputPanelService {

    private readonly inputs = new Map<string, InputDocument>();
    private readonly onInputsChangedEmitter = new Emitter<void>();

    readonly onInputsChanged: Event<void> = this.onInputsChangedEmitter.event;

    constructor(
        @inject(UTLXService) private readonly utlxService: UTLXService
    ) {}

    /**
     * Get all inputs
     */
    getInputs(): InputDocument[] {
        return Array.from(this.inputs.values());
    }

    /**
     * Get input by ID
     */
    getInput(id: string): InputDocument | undefined {
        return this.inputs.get(id);
    }

    /**
     * Add new input
     */
    async addInput(selection: InputSelection): Promise<InputDocument> {
        const input = await this.utlxService.addInputDocument(
            selection.name,
            selection.content,
            selection.format
        );

        this.inputs.set(input.id, input);
        this.onInputsChangedEmitter.fire();

        return input;
    }

    /**
     * Update input content
     */
    async updateInput(id: string, content: string): Promise<void> {
        const input = this.inputs.get(id);
        if (input) {
            input.content = content;
            this.onInputsChangedEmitter.fire();
        }
    }

    /**
     * Remove input
     */
    async removeInput(id: string): Promise<void> {
        await this.utlxService.removeInputDocument(id);
        this.inputs.delete(id);
        this.onInputsChangedEmitter.fire();
    }

    /**
     * Clear all inputs
     */
    async clearAllInputs(): Promise<void> {
        for (const id of this.inputs.keys()) {
            await this.utlxService.removeInputDocument(id);
        }

        this.inputs.clear();
        this.onInputsChangedEmitter.fire();
    }

    /**
     * Validate all inputs against their schemas
     */
    async validateAllInputs(): Promise<Map<string, ValidationResult>> {
        const results = new Map<string, ValidationResult>();

        for (const input of this.inputs.values()) {
            if (input.schema) {
                // Validate against schema
                // TODO: Implement schema validation
            }
        }

        return results;
    }

    /**
     * Export inputs to JSON (for saving workspace)
     */
    exportInputs(): string {
        const inputArray = Array.from(this.inputs.values());
        return JSON.stringify(inputArray, null, 2);
    }

    /**
     * Import inputs from JSON
     */
    async importInputs(json: string): Promise<void> {
        const inputArray = JSON.parse(json) as InputDocument[];

        for (const input of inputArray) {
            await this.addInput(input);
        }
    }
}
```

---

## 5. Center Editor Panel

### 5.1 UTL-X Editor Contribution

```typescript
// frontend/editor/utlx-editor-contribution.ts
import { injectable, inject } from 'inversify';
import { EditorContribution } from '@theia/editor/lib/browser';
import { MonacoEditor } from '@theia/monaco/lib/browser/monaco-editor';
import { InputPanelService } from '../input-panel/input-panel-service';

@injectable()
export class UTLXEditorContribution implements EditorContribution {

    constructor(
        @inject(InputPanelService) private readonly inputService: InputPanelService
    ) {}

    /**
     * Called when editor is created
     */
    onEditorCreated(editor: MonacoEditor): void {
        if (editor.document.languageId !== 'utlx') {
            return;
        }

        // Add custom decorations
        this.addInputVariableHighlighting(editor);

        // Add context menu items
        this.addContextMenuItems(editor);

        // Add keyboard shortcuts
        this.addKeyboardShortcuts(editor);
    }

    /**
     * Highlight $input variables with input names
     */
    private addInputVariableHighlighting(editor: MonacoEditor): void {
        const inputs = this.inputService.getInputs();

        // Listen to input changes
        this.inputService.onInputsChanged(() => {
            this.updateInputHighlighting(editor);
        });

        // Initial highlighting
        this.updateInputHighlighting(editor);
    }

    private updateInputHighlighting(editor: MonacoEditor): void {
        const model = editor.getControl().getModel();
        if (!model) return;

        const inputs = this.inputService.getInputs();
        const inputNames = inputs.map(i => i.name);

        // Find all $input references in code
        const text = model.getValue();
        const regex = /\$(\w+)/g;
        let match;

        const decorations: monaco.editor.IModelDeltaDecoration[] = [];

        while ((match = regex.exec(text)) !== null) {
            const varName = match[1];

            if (inputNames.includes(varName)) {
                const startPos = model.getPositionAt(match.index);
                const endPos = model.getPositionAt(match.index + match[0].length);

                decorations.push({
                    range: new monaco.Range(
                        startPos.lineNumber,
                        startPos.column,
                        endPos.lineNumber,
                        endPos.column
                    ),
                    options: {
                        inlineClassName: 'utlx-input-variable-valid',
                        hoverMessage: {
                            value: `Input: ${varName} (${inputs.find(i => i.name === varName)?.format})`
                        }
                    }
                });
            } else if (varName !== 'input') {
                // Invalid input reference
                const startPos = model.getPositionAt(match.index);
                const endPos = model.getPositionAt(match.index + match[0].length);

                decorations.push({
                    range: new monaco.Range(
                        startPos.lineNumber,
                        startPos.column,
                        endPos.lineNumber,
                        endPos.column
                    ),
                    options: {
                        inlineClassName: 'utlx-input-variable-invalid',
                        hoverMessage: {
                            value: `⚠️ Unknown input: ${varName}`
                        }
                    }
                });
            }
        }

        editor.getControl().deltaDecorations([], decorations);
    }

    /**
     * Add context menu items
     */
    private addContextMenuItems(editor: MonacoEditor): void {
        editor.getControl().addAction({
            id: 'utlx.executeTransformation',
            label: 'Execute Transformation',
            keybindings: [
                monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter
            ],
            contextMenuGroupId: 'utlx',
            contextMenuOrder: 1,
            run: (ed) => {
                this.executeTransformation(editor);
            }
        });

        editor.getControl().addAction({
            id: 'utlx.validateTransformation',
            label: 'Validate Transformation',
            keybindings: [
                monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.KEY_V
            ],
            contextMenuGroupId: 'utlx',
            contextMenuOrder: 2,
            run: (ed) => {
                this.validateTransformation(editor);
            }
        });

        editor.getControl().addAction({
            id: 'utlx.insertInputVariable',
            label: 'Insert Input Variable',
            keybindings: [
                monaco.KeyMod.CtrlCmd | monaco.KeyCode.KEY_I
            ],
            contextMenuGroupId: 'utlx',
            contextMenuOrder: 3,
            run: (ed) => {
                this.showInputVariablePicker(editor);
            }
        });
    }

    private async executeTransformation(editor: MonacoEditor): Promise<void> {
        // Trigger execution via coordination service
        // This will be handled by the coordination service
        this.commands.executeCommand('utlx.execute');
    }

    private async validateTransformation(editor: MonacoEditor): Promise<void> {
        this.commands.executeCommand('utlx.validate');
    }

    private async showInputVariablePicker(editor: MonacoEditor): Promise<void> {
        const inputs = this.inputService.getInputs();

        if (inputs.length === 0) {
            this.messageService.warn('No inputs defined. Add inputs first.');
            return;
        }

        // Show quick pick
        const selected = await this.quickPickService.show(
            inputs.map(input => ({
                label: `$${input.name}`,
                description: input.format.toUpperCase(),
                value: input.name
            })),
            {
                placeholder: 'Select input variable to insert'
            }
        );

        if (selected) {
            const position = editor.getControl().getPosition();
            if (position) {
                editor.getControl().executeEdits('insert-input', [{
                    range: new monaco.Range(
                        position.lineNumber,
                        position.column,
                        position.lineNumber,
                        position.column
                    ),
                    text: `$${selected.value}`
                }]);
            }
        }
    }
}
```

### 5.2 Multi-Input Completion Provider

```typescript
// frontend/editor/multi-input-completion.ts
import * as monaco from 'monaco-editor-core';
import { injectable, inject } from 'inversify';
import { InputPanelService } from '../input-panel/input-panel-service';

@injectable()
export class MultiInputCompletionProvider implements monaco.languages.CompletionItemProvider {

    constructor(
        @inject(InputPanelService) private readonly inputService: InputPanelService
    ) {}

    provideCompletionItems(
        model: monaco.editor.ITextModel,
        position: monaco.Position,
        context: monaco.languages.CompletionContext,
        token: monaco.CancellationToken
    ): monaco.languages.ProviderResult<monaco.languages.CompletionList> {

        const textUntilPosition = model.getValueInRange({
            startLineNumber: position.lineNumber,
            startColumn: 1,
            endLineNumber: position.lineNumber,
            endColumn: position.column
        });

        // Check if we're typing $
        if (!textUntilPosition.endsWith('$')) {
            return { suggestions: [] };
        }

        const inputs = this.inputService.getInputs();

        const suggestions: monaco.languages.CompletionItem[] = inputs.map(input => ({
            label: input.name,
            kind: monaco.languages.CompletionItemKind.Variable,
            detail: `Input: ${input.format.toUpperCase()}`,
            documentation: {
                value: `**Input Document**: ${input.name}\n\n**Format**: ${input.format}\n\n**Size**: ${input.content.length} bytes`
            },
            insertText: input.name,
            range: {
                startLineNumber: position.lineNumber,
                startColumn: position.column,
                endLineNumber: position.lineNumber,
                endColumn: position.column
            }
        }));

        return { suggestions };
    }
}
```

---

## 6. Output Panel Implementation

### 6.1 Output Panel Widget

```typescript
// frontend/output-panel/output-panel-widget.tsx
import * as React from 'react';
import { injectable, inject } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { CoordinationService } from '../../backend/services/coordination-service';
import { ExecutionResult } from '../../common/protocol';

@injectable()
export class OutputPanelWidget extends ReactWidget {

    static readonly ID = 'utlx-output-panel';
    static readonly LABEL = 'Output';

    private executionResult: ExecutionResult | null = null;
    private viewMode: 'preview' | 'raw' | 'diff' = 'preview';
    private expectedOutput: string | null = null;

    constructor(
        @inject(CoordinationService) private readonly coordination: CoordinationService
    ) {
        super();
        this.id = OutputPanelWidget.ID;
        this.title.label = OutputPanelWidget.LABEL;
        this.title.closable = true;
        this.title.iconClass = 'fa fa-file-export';

        // Listen to execution results
        this.coordination.onExecutionComplete(result => {
            this.executionResult = result;
            this.update();
        });
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-output-panel'>
                <div className='utlx-output-toolbar'>
                    <div className='utlx-output-view-selector'>
                        <button
                            className={`theia-button ${this.viewMode === 'preview' ? 'active' : ''}`}
                            onClick={() => this.setViewMode('preview')}
                        >
                            <i className='fa fa-eye' /> Preview
                        </button>

                        <button
                            className={`theia-button ${this.viewMode === 'raw' ? 'active' : ''}`}
                            onClick={() => this.setViewMode('raw')}
                        >
                            <i className='fa fa-code' /> Raw
                        </button>

                        <button
                            className={`theia-button ${this.viewMode === 'diff' ? 'active' : ''}`}
                            onClick={() => this.setViewMode('diff')}
                            disabled={!this.expectedOutput}
                        >
                            <i className='fa fa-columns' /> Diff
                        </button>
                    </div>

                    <div className='utlx-output-actions'>
                        <button
                            className='theia-button secondary'
                            onClick={() => this.handleCopyOutput()}
                            disabled={!this.executionResult?.output}
                        >
                            <i className='fa fa-copy' /> Copy
                        </button>

                        <button
                            className='theia-button secondary'
                            onClick={() => this.handleSaveOutput()}
                            disabled={!this.executionResult?.output}
                        >
                            <i className='fa fa-save' /> Save
                        </button>

                        <button
                            className='theia-button secondary'
                            onClick={() => this.handleSetExpected()}
                            disabled={!this.executionResult?.output}
                        >
                            <i className='fa fa-check' /> Set as Expected
                        </button>
                    </div>
                </div>

                <div className='utlx-output-content'>
                    {this.renderContent()}
                </div>

                {this.renderStatus()}
            </div>
        );
    }

    private renderContent(): React.ReactNode {
        if (!this.executionResult) {
            return (
                <div className='utlx-output-empty'>
                    <i className='fa fa-play-circle' style={{ fontSize: '48px', color: '#888' }} />
                    <p>No output yet</p>
                    <p className='hint'>Execute transformation to see output</p>
                    <button
                        className='theia-button primary'
                        onClick={() => this.handleExecute()}
                    >
                        <i className='fa fa-play' /> Execute Transformation
                    </button>
                </div>
            );
        }

        if (!this.executionResult.success) {
            return (
                <div className='utlx-output-error'>
                    <h3>
                        <i className='fa fa-exclamation-triangle' /> Execution Failed
                    </h3>
                    {this.executionResult.errors?.map((error, index) => (
                        <div key={index} className='utlx-error-item'>
                            <div className='utlx-error-header'>
                                Line {error.line}:{error.column} - {error.message}
                            </div>
                            {error.suggestion && (
                                <div className='utlx-error-suggestion'>
                                    💡 {error.suggestion}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            );
        }

        switch (this.viewMode) {
            case 'preview':
                return this.renderPreview();
            case 'raw':
                return this.renderRaw();
            case 'diff':
                return this.renderDiff();
        }
    }

    private renderPreview(): React.ReactNode {
        const { output, outputFormat } = this.executionResult!;

        switch (outputFormat) {
            case 'json':
                return <JSONViewer content={output!} />;
            case 'xml':
                return <XMLViewer content={output!} />;
            case 'csv':
                return <CSVViewer content={output!} />;
            case 'yaml':
                return <YAMLViewer content={output!} />;
            default:
                return <pre className='utlx-output-raw'>{output}</pre>;
        }
    }

    private renderRaw(): React.ReactNode {
        return (
            <pre className='utlx-output-raw'>
                {this.executionResult!.output}
            </pre>
        );
    }

    private renderDiff(): React.ReactNode {
        if (!this.expectedOutput) {
            return <div>No expected output set</div>;
        }

        return (
            <DiffViewer
                expected={this.expectedOutput}
                actual={this.executionResult!.output!}
                format={this.executionResult!.outputFormat!}
            />
        );
    }

    private renderStatus(): React.ReactNode {
        if (!this.executionResult) {
            return null;
        }

        const { success, executionTime, output } = this.executionResult;

        return (
            <div className='utlx-output-status'>
                <span className={`utlx-status-badge ${success ? 'success' : 'error'}`}>
                    {success ? '✓ Success' : '✗ Failed'}
                </span>

                {executionTime && (
                    <span className='utlx-execution-time'>
                        ⏱️ {executionTime}ms
                    </span>
                )}

                {output && (
                    <span className='utlx-output-size'>
                        📄 {formatBytes(output.length)}
                    </span>
                )}
            </div>
        );
    }

    private setViewMode(mode: 'preview' | 'raw' | 'diff'): void {
        this.viewMode = mode;
        this.update();
    }

    private async handleExecute(): Promise<void> {
        await this.coordination.executeTransformation();
    }

    private async handleCopyOutput(): Promise<void> {
        if (this.executionResult?.output) {
            await navigator.clipboard.writeText(this.executionResult.output);
            this.messageService.info('Output copied to clipboard');
        }
    }

    private async handleSaveOutput(): Promise<void> {
        if (this.executionResult?.output) {
            const uri = await this.fileDialogService.showSaveDialog({
                title: 'Save Output',
                filters: {
                    'All Files': ['*']
                }
            });

            if (uri) {
                await this.fileService.write(uri, this.executionResult.output);
                this.messageService.info(`Output saved to ${uri.path.base}`);
            }
        }
    }

    private handleSetExpected(): void {
        if (this.executionResult?.output) {
            this.expectedOutput = this.executionResult.output;
            this.messageService.info('Expected output set');
            this.update();
        }
    }
}

function formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
```

### 6.2 Format-Specific Viewers

```typescript
// frontend/output-panel/output-viewer.tsx
import * as React from 'react';

/**
 * JSON tree viewer with collapsible nodes
 */
export const JSONViewer: React.FC<{ content: string }> = ({ content }) => {
    const [json, setJson] = React.useState<any>(null);
    const [error, setError] = React.useState<string | null>(null);

    React.useEffect(() => {
        try {
            const parsed = JSON.parse(content);
            setJson(parsed);
            setError(null);
        } catch (e) {
            setError((e as Error).message);
        }
    }, [content]);

    if (error) {
        return (
            <div className='utlx-viewer-error'>
                <i className='fa fa-exclamation-triangle' />
                Invalid JSON: {error}
            </div>
        );
    }

    return (
        <div className='utlx-json-viewer'>
            <JSONNode data={json} expanded={true} />
        </div>
    );
};

const JSONNode: React.FC<{ data: any; expanded?: boolean }> = ({ data, expanded = false }) => {
    const [isExpanded, setExpanded] = React.useState(expanded);

    if (data === null) {
        return <span className='json-null'>null</span>;
    }

    if (typeof data === 'boolean') {
        return <span className='json-boolean'>{data.toString()}</span>;
    }

    if (typeof data === 'number') {
        return <span className='json-number'>{data}</span>;
    }

    if (typeof data === 'string') {
        return <span className='json-string'>"{data}"</span>;
    }

    if (Array.isArray(data)) {
        return (
            <div className='json-array'>
                <span className='json-bracket' onClick={() => setExpanded(!isExpanded)}>
                    {isExpanded ? '▼' : '▶'} [
                </span>
                {isExpanded && (
                    <div className='json-children'>
                        {data.map((item, index) => (
                            <div key={index} className='json-item'>
                                <span className='json-index'>{index}:</span>
                                <JSONNode data={item} />
                                {index < data.length - 1 && ','}
                            </div>
                        ))}
                    </div>
                )}
                <span className='json-bracket'>]</span>
            </div>
        );
    }

    if (typeof data === 'object') {
        const keys = Object.keys(data);
        return (
            <div className='json-object'>
                <span className='json-bracket' onClick={() => setExpanded(!isExpanded)}>
                    {isExpanded ? '▼' : '▶'} {'{'}
                </span>
                {isExpanded && (
                    <div className='json-children'>
                        {keys.map((key, index) => (
                            <div key={key} className='json-property'>
                                <span className='json-key'>"{key}"</span>:
                                <JSONNode data={data[key]} />
                                {index < keys.length - 1 && ','}
                            </div>
                        ))}
                    </div>
                )}
                <span className='json-bracket'>{'}'}</span>
            </div>
        );
    }

    return <span>{String(data)}</span>;
};

/**
 * XML viewer with syntax highlighting and folding
 */
export const XMLViewer: React.FC<{ content: string }> = ({ content }) => {
    // Use Monaco editor in read-only mode for XML
    return (
        <div className='utlx-xml-viewer'>
            <MonacoEditor
                value={content}
                language='xml'
                options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    folding: true,
                    wordWrap: 'on'
                }}
            />
        </div>
    );
};

/**
 * CSV table viewer
 */
export const CSVViewer: React.FC<{ content: string }> = ({ content }) => {
    const [rows, setRows] = React.useState<string[][]>([]);

    React.useEffect(() => {
        const lines = content.split('\n').filter(line => line.trim());
        const parsed = lines.map(line => line.split(',').map(cell => cell.trim()));
        setRows(parsed);
    }, [content]);

    if (rows.length === 0) {
        return <div>Empty CSV</div>;
    }

    const headers = rows[0];
    const data = rows.slice(1);

    return (
        <div className='utlx-csv-viewer'>
            <table className='utlx-csv-table'>
                <thead>
                    <tr>
                        {headers.map((header, index) => (
                            <th key={index}>{header}</th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                    {data.map((row, rowIndex) => (
                        <tr key={rowIndex}>
                            {row.map((cell, cellIndex) => (
                                <td key={cellIndex}>{cell}</td>
                            ))}
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
};

/**
 * YAML viewer (using Monaco)
 */
export const YAMLViewer: React.FC<{ content: string }> = ({ content }) => {
    return (
        <div className='utlx-yaml-viewer'>
            <MonacoEditor
                value={content}
                language='yaml'
                options={{
                    readOnly: true,
                    minimap: { enabled: false },
                    lineNumbers: 'on',
                    wordWrap: 'on'
                }}
            />
        </div>
    );
};
```

### 6.3 Diff Viewer

```typescript
// frontend/output-panel/diff-viewer.tsx
import * as React from 'react';
import * as monaco from 'monaco-editor-core';

export interface DiffViewerProps {
    expected: string;
    actual: string;
    format: string;
}

export const DiffViewer: React.FC<DiffViewerProps> = ({ expected, actual, format }) => {
    const containerRef = React.useRef<HTMLDivElement>(null);
    const editorRef = React.useRef<monaco.editor.IStandaloneDiffEditor | null>(null);

    React.useEffect(() => {
        if (!containerRef.current) return;

        // Create diff editor
        editorRef.current = monaco.editor.createDiffEditor(containerRef.current, {
            readOnly: true,
            renderSideBySide: true,
            enableSplitViewResizing: true,
            originalEditable: false,
            automaticLayout: true
        });

        // Set models
        const originalModel = monaco.editor.createModel(expected, format);
        const modifiedModel = monaco.editor.createModel(actual, format);

        editorRef.current.setModel({
            original: originalModel,
            modified: modifiedModel
        });

        return () => {
            originalModel.dispose();
            modifiedModel.dispose();
            editorRef.current?.dispose();
        };
    }, [expected, actual, format]);

    return (
        <div className='utlx-diff-viewer'>
            <div className='utlx-diff-header'>
                <span className='utlx-diff-label expected'>Expected</span>
                <span className='utlx-diff-label actual'>Actual</span>
            </div>
            <div ref={containerRef} className='utlx-diff-container' />
        </div>
    );
};
```

---

## 7. LSP Integration

### 7.1 UTL-X Language Client

```typescript
// frontend/editor/utlx-language-client.ts
import { injectable, inject } from 'inversify';
import { BaseLanguageClientContribution, Workspace, Languages, LanguageClientFactory } from '@theia/languages/lib/browser';
import { UTLX_LANGUAGE_ID, UTLX_LANGUAGE_NAME } from '../../common/constants';

@injectable()
export class UTLXLanguageClientContribution extends BaseLanguageClientContribution {

    readonly id = UTLX_LANGUAGE_ID;
    readonly name = UTLX_LANGUAGE_NAME;

    constructor(
        @inject(Workspace) protected readonly workspace: Workspace,
        @inject(Languages) protected readonly languages: Languages,
        @inject(LanguageClientFactory) protected readonly languageClientFactory: LanguageClientFactory
    ) {
        super(workspace, languages, languageClientFactory);
    }

    protected get globPatterns(): string[] {
        return ['**/*.utlx'];
    }

    protected get documentSelector(): string[] {
        return [UTLX_LANGUAGE_ID];
    }
}
```

### 7.2 LSP Server Contribution (Backend)

```typescript
// backend/lsp/utlx-lsp-contribution.ts
import { injectable } from 'inversify';
import { BaseLanguageServerContribution, IConnection, LanguageServerStartOptions } from '@theia/languages/lib/node';
import { UTLX_LANGUAGE_ID, UTLX_LANGUAGE_NAME } from '../../common/constants';
import * as path from 'path';

@injectable()
export class UTLXLSPContribution extends BaseLanguageServerContribution {

    readonly id = UTLX_LANGUAGE_ID;
    readonly name = UTLX_LANGUAGE_NAME;

    start(clientConnection: IConnection): void {
        const serverPath = path.join(__dirname, '..', '..', 'server', 'utlx-language-server.jar');

        const args: string[] = [
            '-jar',
            serverPath
        ];

        const serverConnection = this.createProcessStreamConnection('java', args);

        this.forward(clientConnection, serverConnection);
    }

    protected onDidFailStartLanguageServer(error: Error): void {
        console.error('Failed to start UTL-X language server:', error);
    }
}
```

---

## 8. Real-Time Execution Engine

### 8.1 Execution Engine Service (Backend)

```typescript
// backend/services/execution-engine-service.ts
import { injectable } from 'inversify';
import { ExecutionResult, InputDocument } from '../../common/protocol';
import * as child_process from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

@injectable()
export class ExecutionEngineService {

    private readonly utlxCli = path.join(__dirname, '..', '..', 'bin', 'utlx');

    /**
     * Execute transformation with given inputs
     */
    async executeTransformation(
        transformCode: string,
        inputs: InputDocument[]
    ): Promise<ExecutionResult> {
        const startTime = Date.now();

        try {
            // Create temporary directory
            const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'utlx-'));

            try {
                // Write transform file
                const transformFile = path.join(tmpDir, 'transform.utlx');
                fs.writeFileSync(transformFile, transformCode, 'utf8');

                // Write input files
                const inputFiles: string[] = [];
                for (const input of inputs) {
                    const ext = this.getExtension(input.format);
                    const inputFile = path.join(tmpDir, `${input.name}.${ext}`);
                    fs.writeFileSync(inputFile, input.content, 'utf8');
                    inputFiles.push(inputFile);
                }

                // Build command
                const args = ['transform', transformFile];

                // Add input flags
                inputs.forEach((input, index) => {
                    args.push('--input', `${input.name}=${inputFiles[index]}`);
                });

                // Execute
                const result = await this.executeCommand(this.utlxCli, args);

                const executionTime = Date.now() - startTime;

                if (result.exitCode === 0) {
                    // Parse output format from transform header
                    const outputFormat = this.extractOutputFormat(transformCode);

                    return {
                        success: true,
                        output: result.stdout,
                        outputFormat,
                        executionTime
                    };
                } else {
                    // Parse errors from stderr
                    const errors = this.parseErrors(result.stderr);

                    return {
                        success: false,
                        errors,
                        executionTime
                    };
                }
            } finally {
                // Cleanup temp directory
                fs.rmSync(tmpDir, { recursive: true, force: true });
            }
        } catch (error) {
            return {
                success: false,
                errors: [{
                    line: 0,
                    column: 0,
                    message: (error as Error).message
                }],
                executionTime: Date.now() - startTime
            };
        }
    }

    /**
     * Execute command and return result
     */
    private executeCommand(
        command: string,
        args: string[]
    ): Promise<{ exitCode: number; stdout: string; stderr: string }> {
        return new Promise((resolve) => {
            const process = child_process.spawn(command, args);

            let stdout = '';
            let stderr = '';

            process.stdout.on('data', (data) => {
                stdout += data.toString();
            });

            process.stderr.on('data', (data) => {
                stderr += data.toString();
            });

            process.on('close', (exitCode) => {
                resolve({
                    exitCode: exitCode || 0,
                    stdout,
                    stderr
                });
            });
        });
    }

    private getExtension(format: string): string {
        return {
            'xml': 'xml',
            'json': 'json',
            'csv': 'csv',
            'yaml': 'yaml'
        }[format] || 'txt';
    }

    private extractOutputFormat(transformCode: string): string {
        const match = transformCode.match(/output\s+(\w+)/);
        return match ? match[1] : 'text';
    }

    private parseErrors(stderr: string): Array<{ line: number; column: number; message: string }> {
        const errors: Array<{ line: number; column: number; message: string }> = [];

        const lines = stderr.split('\n');

        for (const line of lines) {
            // Parse error format: "Line X:Y - Message"
            const match = line.match(/Line\s+(\d+):(\d+)\s*-\s*(.+)/);

            if (match) {
                errors.push({
                    line: parseInt(match[1]),
                    column: parseInt(match[2]),
                    message: match[3]
                });
            }
        }

        return errors;
    }
}
```

---

## 9. Multi-Input Coordination

### 9.1 Coordination Service

```typescript
// backend/services/coordination-service.ts
import { injectable, inject } from 'inversify';
import { Emitter, Event } from '@theia/core';
import { InputDocument, ExecutionResult } from '../../common/protocol';
import { ExecutionEngineService } from './execution-engine-service';
import { InputManagerService } from './input-manager-service';

@injectable()
export class CoordinationService {

    private readonly onExecutionCompleteEmitter = new Emitter<ExecutionResult>();
    readonly onExecutionComplete: Event<ExecutionResult> = this.onExecutionCompleteEmitter.event;

    private currentTransformCode: string = '';
    private autoExecute: boolean = false;
    private executionDebounceTimer: NodeJS.Timeout | null = null;

    constructor(
        @inject(ExecutionEngineService) private readonly executionEngine: ExecutionEngineService,
        @inject(InputManagerService) private readonly inputManager: InputManagerService
    ) {
        // Listen to input changes
        this.inputManager.onInputsChanged(() => {
            if (this.autoExecute) {
                this.scheduleExecution();
            }
        });
    }

    /**
     * Set current transformation code
     */
    setTransformCode(code: string): void {
        this.currentTransformCode = code;

        if (this.autoExecute) {
            this.scheduleExecution();
        }
    }

    /**
     * Enable/disable auto-execution
     */
    setAutoExecute(enabled: boolean): void {
        this.autoExecute = enabled;
    }

    /**
     * Execute transformation with current inputs
     */
    async executeTransformation(): Promise<ExecutionResult> {
        const inputs = this.inputManager.getInputs();

        if (inputs.length === 0) {
            return {
                success: false,
                errors: [{
                    line: 0,
                    column: 0,
                    message: 'No inputs defined'
                }]
            };
        }

        if (!this.currentTransformCode.trim()) {
            return {
                success: false,
                errors: [{
                    line: 0,
                    column: 0,
                    message: 'No transformation code'
                }]
            };
        }

        const result = await this.executionEngine.executeTransformation(
            this.currentTransformCode,
            inputs
        );

        this.onExecutionCompleteEmitter.fire(result);

        return result;
    }

    /**
     * Schedule execution after debounce delay
     */
    private scheduleExecution(): void {
        if (this.executionDebounceTimer) {
            clearTimeout(this.executionDebounceTimer);
        }

        this.executionDebounceTimer = setTimeout(() => {
            this.executeTransformation();
        }, 1000); // 1 second debounce
    }
}
```

---

## 10. User Experience Workflows

### 10.1 Typical Development Workflow

**Scenario:** Developer creating XML to JSON transformation

```
1. Open Theia with UTL-X extension
   └─→ Three-panel layout appears

2. Add input file (Left Panel)
   └─→ Click "Add Input"
   └─→ Upload order.xml
   └─→ Input appears in list with preview

3. Start writing transformation (Center)
   └─→ Type: %utlx 1.0
   └─→ Type: input xml
   └─→ LSP suggests: "output json" (autocomplete)
   └─→ Type: ---
   └─→ Type: {
   └─→ Type: $
   └─→ LSP shows completion: $order (from input)

4. Real-time validation (Center)
   └─→ LSP highlights errors as you type
   └─→ Hover shows suggestions
   └─→ Quick fixes available

5. Execute transformation
   └─→ Press Ctrl+Enter or click "Execute"
   └─→ Right panel updates with JSON output
   └─→ Output shown in JSON tree viewer

6. Iterate and refine
   └─→ Modify transformation
   └─→ Output updates automatically (if auto-execute enabled)
   └─→ Compare with expected output (diff view)

7. Save and test with multiple inputs
   └─→ Add second input file
   └─→ Execute again
   └─→ Verify both outputs
```

### 10.2 Testing Workflow

**Scenario:** Testing transformation with multiple test cases

```
1. Open transformation file
   └─→ transform.utlx already contains transformation logic

2. Add test cases (Left Panel)
   └─→ Add input: test1-order.xml
   └─→ Add input: test2-large-order.xml
   └─→ Add input: test3-empty-order.xml

3. Set expected outputs (Right Panel)
   └─→ Execute with test1
   └─→ Verify output
   └─→ Click "Set as Expected"
   └─→ Repeat for test2 and test3

4. Run all tests
   └─→ Click "Run All Tests" in toolbar
   └─→ Each input executed sequentially
   └─→ Output panel shows test results:
       • test1: ✓ Pass (output matches expected)
       • test2: ✓ Pass
       • test3: ✗ Fail (diff shown)

5. Fix failing test
   └─→ Select test3 from results
   └─→ Diff view shows expected vs actual
   └─→ Click on error in diff
   └─→ Editor jumps to problematic line
   └─→ Fix transformation
   └─→ Re-run test3

6. Export test suite
   └─→ Click "Export Tests"
   └─→ Saves inputs and expected outputs
   └─→ Can be checked into version control
```

### 10.3 Multi-Input Transformation Workflow

**Scenario:** Joining data from multiple sources

```
1. Add multiple inputs (Left Panel)
   └─→ Add input: orders.json (named "orders")
   └─→ Add input: customers.xml (named "customers")
   └─→ Add input: products.csv (named "products")

2. Write multi-input transformation (Center)
   └─→ %utlx 1.0
   └─→ input: orders json, customers xml, products csv
   └─→ output json
   └─→ ---
   └─→ {
   └─→   enrichedOrders: $orders |> map(order => {
   └─→     let customer = ...
   └─→     let product = ...
   └─→     ...
   └─→   })
   └─→ }

3. LSP assistance (Center)
   └─→ Type $
   └─→ Autocomplete shows: $orders, $customers, $products
   └─→ Select $orders
   └─→ Type .
   └─→ LSP shows available fields (if schema available)

4. Execute and verify (Right Panel)
   └─→ Press Ctrl+Enter
   └─→ Output shows joined data
   └─→ Verify customer names and product details included

5. Debug correlation issues
   └─→ If data missing, click on output record
   └─→ Editor highlights corresponding code
   └─→ Error correlation shows which input caused issue
```

---

## 11. Advanced Features

### 11.1 Schema Awareness

**Automatic schema detection and validation:**

```typescript
/**
 * Schema-aware input manager
 */
export class SchemaAwareInputManager {

    /**
     * Auto-detect schema when adding input
     */
    async addInputWithSchemaDetection(input: InputDocument): Promise<void> {
        if (input.format === 'xml') {
            // Check for xsi:schemaLocation in XML
            const schemaLocation = this.extractSchemaLocation(input.content);
            if (schemaLocation) {
                input.schema = schemaLocation;
            }
        } else if (input.format === 'json') {
            // Check for $schema property
            const schema = this.extractJSONSchema(input.content);
            if (schema) {
                input.schema = schema;
            }
        }

        // Validate against schema
        if (input.schema) {
            const validation = await this.validateAgainstSchema(input);
            if (!validation.valid) {
                // Show validation errors in input panel
                this.showValidationErrors(input, validation.errors);
            }
        }

        await this.addInput(input);
    }

    /**
     * Provide field suggestions based on schema
     */
    async getFieldSuggestions(inputName: string, path: string): Promise<string[]> {
        const input = this.getInput(inputName);
        if (!input?.schema) {
            return [];
        }

        // Parse schema and extract fields at given path
        const schema = await this.loadSchema(input.schema);
        return this.extractFields(schema, path);
    }
}
```

### 11.2 Live Preview Mode

**Auto-execute on code/input changes:**

```typescript
/**
 * Live preview controller
 */
export class LivePreviewController {

    private autoExecuteEnabled: boolean = false;
    private debounceTimer: NodeJS.Timeout | null = null;

    /**
     * Toggle live preview
     */
    toggleLivePreview(enabled: boolean): void {
        this.autoExecuteEnabled = enabled;

        if (enabled) {
            // Execute immediately
            this.executeTransformation();
        }
    }

    /**
     * Called when editor content changes
     */
    onEditorChange(content: string): void {
        if (!this.autoExecuteEnabled) {
            return;
        }

        // Debounce execution (wait for user to stop typing)
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }

        this.debounceTimer = setTimeout(() => {
            this.executeTransformation();
        }, 1000);
    }

    /**
     * Called when inputs change
     */
    onInputsChange(): void {
        if (this.autoExecuteEnabled) {
            this.executeTransformation();
        }
    }
}
```

### 11.3 Performance Profiling

**Show execution metrics:**

```typescript
/**
 * Performance profiler for transformations
 */
export class PerformanceProfiler {

    /**
     * Execute with profiling
     */
    async executeWithProfiling(
        transformCode: string,
        inputs: InputDocument[]
    ): Promise<ProfilingResult> {
        const startTime = performance.now();

        const parseStart = performance.now();
        // Parse transformation
        const parseEnd = performance.now();

        const executionStart = performance.now();
        // Execute transformation
        const executionEnd = performance.now();

        const serializeStart = performance.now();
        // Serialize output
        const serializeEnd = performance.now();

        const totalTime = performance.now() - startTime;

        return {
            totalTime,
            parseTime: parseEnd - parseStart,
            executionTime: executionEnd - executionStart,
            serializeTime: serializeEnd - serializeStart,
            inputSizes: inputs.map(i => i.content.length),
            outputSize: 0 // filled by result
        };
    }

    /**
     * Show profiling report in output panel
     */
    showProfilingReport(result: ProfilingResult): void {
        console.log(`
Performance Report:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total Time:       ${result.totalTime.toFixed(2)}ms
  Parse:          ${result.parseTime.toFixed(2)}ms (${this.percent(result.parseTime, result.totalTime)}%)
  Execution:      ${result.executionTime.toFixed(2)}ms (${this.percent(result.executionTime, result.totalTime)}%)
  Serialization:  ${result.serializeTime.toFixed(2)}ms (${this.percent(result.serializeTime, result.totalTime)}%)

Input Sizes:      ${result.inputSizes.map(s => this.formatBytes(s)).join(', ')}
Output Size:      ${this.formatBytes(result.outputSize)}
Throughput:       ${this.formatBytes(result.outputSize / (result.totalTime / 1000))}/s
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        `);
    }

    private percent(part: number, total: number): string {
        return ((part / total) * 100).toFixed(1);
    }

    private formatBytes(bytes: number): string {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    }
}
```

### 11.4 Collaborative Features

**Real-time collaboration (future):**

```typescript
/**
 * Collaborative editing support
 */
export class CollaborationService {

    /**
     * Share transformation session
     */
    async createSharedSession(): Promise<string> {
        // Generate unique session ID
        const sessionId = this.generateSessionId();

        // Start WebSocket server for collaboration
        await this.startCollaborationServer(sessionId);

        // Return shareable URL
        return `https://utlx-ide.theia.cloud/session/${sessionId}`;
    }

    /**
     * Join shared session
     */
    async joinSession(sessionId: string): Promise<void> {
        // Connect to collaboration server
        await this.connectToSession(sessionId);

        // Sync inputs, transformation, and output
        await this.syncState();

        // Listen for changes from other participants
        this.listenForRemoteChanges();
    }

    /**
     * Broadcast local changes to participants
     */
    broadcastChange(change: Change): void {
        this.collaborationServer.broadcast({
            type: 'change',
            author: this.currentUser,
            timestamp: Date.now(),
            change
        });
    }
}
```

---

## 12. Deployment Options

### 12.1 Desktop Application (Electron)

**Package as standalone desktop app:**

```bash
# Build Theia application
npm run build

# Package for desktop
npm run package

# Outputs:
# - utlx-ide-1.0.0.dmg (macOS)
# - utlx-ide-1.0.0.exe (Windows)
# - utlx-ide-1.0.0.AppImage (Linux)
```

**Distribution:**
- Direct download from utlx.dev
- Homebrew cask (macOS)
- Chocolatey (Windows)
- Snap/Flatpak (Linux)

### 12.2 Cloud-Hosted IDE

**Deploy to cloud with multi-tenancy:**

```typescript
/**
 * Cloud deployment with workspace isolation
 */
export class CloudDeployment {

    /**
     * Create user workspace
     */
    async createWorkspace(userId: string): Promise<WorkspaceInfo> {
        // Provision isolated container
        const container = await this.containerOrchestrator.create({
            image: 'utlx-theia:latest',
            userId,
            resources: {
                cpu: '1',
                memory: '2Gi',
                storage: '10Gi'
            }
        });

        // Setup user-specific configuration
        await this.setupUserConfig(container.id, userId);

        // Generate access URL
        const url = `https://${userId}.utlx-ide.theia.cloud`;

        return {
            containerId: container.id,
            url,
            expiresIn: 3600 // 1 hour
        };
    }

    /**
     * Auto-scale based on usage
     */
    async autoScale(): Promise<void> {
        const activeWorkspaces = await this.getActiveWorkspaces();

        if (activeWorkspaces.length > this.maxCapacity * 0.8) {
            // Scale up
            await this.addNodes(2);
        } else if (activeWorkspaces.length < this.maxCapacity * 0.3) {
            // Scale down
            await this.removeNodes(1);
        }
    }
}
```

**Infrastructure:**
- Kubernetes cluster
- One pod per user workspace
- Persistent volume for user files
- Ingress for routing
- Auto-scaling based on load

### 12.3 Hybrid Deployment

**Desktop + cloud sync:**

```typescript
/**
 * Hybrid mode: Local IDE with cloud backup
 */
export class HybridDeployment {

    /**
     * Sync local workspace to cloud
     */
    async syncToCloud(): Promise<void> {
        const localFiles = await this.getLocalFiles();

        // Upload to cloud storage
        await this.cloudStorage.upload(localFiles);

        // Update cloud workspace
        await this.cloudWorkspace.sync();
    }

    /**
     * Restore from cloud
     */
    async restoreFromCloud(): Promise<void> {
        const cloudFiles = await this.cloudStorage.list();

        // Download to local
        await this.downloadFiles(cloudFiles);

        // Merge with local changes
        await this.mergeChanges();
    }

    /**
     * Auto-sync on save
     */
    onSave(file: string): void {
        if (this.autoSyncEnabled) {
            this.syncToCloud();
        }
    }
}
```

---

## 13. Implementation Roadmap

### Phase 1: Core Extension (Weeks 1-4)

**Goals:**
- Basic three-panel layout working
- Input panel with add/remove functionality
- Center editor with UTL-X syntax highlighting
- Output panel with raw text display

**Tasks:**
1. Week 1: Project setup
   - Initialize Theia extension project
   - Setup build system
   - Configure DI containers

2. Week 2: Input panel
   - InputPanelWidget implementation
   - Input selector dialog
   - Input management service

3. Week 3: Output panel
   - OutputPanelWidget implementation
   - Raw text viewer
   - Status display

4. Week 4: Layout integration
   - Three-panel layout
   - Panel resizing
   - State persistence

**Deliverables:**
- ✅ Basic three-panel IDE
- ✅ Can add inputs manually
- ✅ Can view raw output

### Phase 2: LSP Integration (Weeks 5-7)

**Goals:**
- Full LSP integration
- Real-time diagnostics
- Autocomplete and hover
- Multi-input variable support

**Tasks:**
1. Week 5: LSP server integration
   - Language client contribution
   - LSP server launcher
   - Basic diagnostics

2. Week 6: Advanced LSP features
   - Multi-input completion
   - Input variable highlighting
   - Go to definition

3. Week 7: Testing and refinement
   - Test LSP features
   - Fix bugs
   - Performance tuning

**Deliverables:**
- ✅ LSP fully working
- ✅ Real-time validation
- ✅ Multi-input awareness

### Phase 3: Execution Engine (Weeks 8-10)

**Goals:**
- Real-time transformation execution
- Format-specific output viewers
- Diff view for testing

**Tasks:**
1. Week 8: Execution engine
   - Backend execution service
   - CLI integration
   - Error parsing

2. Week 9: Output viewers
   - JSON tree viewer
   - XML viewer
   - CSV table viewer

3. Week 10: Testing features
   - Diff viewer
   - Expected output management
   - Test suite support

**Deliverables:**
- ✅ Can execute transformations
- ✅ Rich output visualization
- ✅ Testing workflow

### Phase 4: Advanced Features (Weeks 11-13)

**Goals:**
- Live preview mode
- Performance profiling
- Schema awareness
- Workspace management

**Tasks:**
1. Week 11: Live preview
   - Auto-execution
   - Debouncing
   - Performance optimization

2. Week 12: Schema support
   - Schema detection
   - Schema validation
   - Field suggestions

3. Week 13: Polish
   - UI/UX improvements
   - Documentation
   - Tutorial integration

**Deliverables:**
- ✅ Production-ready IDE
- ✅ Full feature set
- ✅ Documentation

### Phase 5: Deployment (Weeks 14-16)

**Goals:**
- Package for desktop
- Deploy cloud version
- Setup CI/CD

**Tasks:**
1. Week 14: Desktop packaging
   - Electron builds
   - Installers
   - Auto-updates

2. Week 15: Cloud deployment
   - Kubernetes setup
   - Container orchestration
   - Multi-tenancy

3. Week 16: Release
   - Final testing
   - Marketing materials
   - Launch

**Deliverables:**
- ✅ Desktop installers
- ✅ Cloud IDE at utlx-ide.theia.cloud
- ✅ Public release

---

## 14. Comparison with Alternatives

### 14.1 Theia vs VS Code Extension

| Aspect | VS Code Extension | Theia Extension |
|--------|-------------------|-----------------|
| **Custom Layout** | ❌ Limited to sidebar | ✅ Full control |
| **Three Panels** | ❌ Not possible | ✅ Native support |
| **Live Preview** | ⚠️ Webview only | ✅ Native panel |
| **Web Deployment** | ❌ Desktop only | ✅ Browser + Desktop |
| **UI Customization** | ⚠️ Limited | ✅ Complete |
| **Development** | ✅ Easier (API) | ⚠️ More complex |
| **Distribution** | ✅ Marketplace | ⚠️ Self-hosted |
| **User Base** | ✅ Huge | ⚠️ Smaller |

**Verdict:** Theia wins for our three-panel requirement, despite higher complexity.

### 14.2 Theia vs JetBrains MPS

| Aspect | JetBrains MPS | Eclipse Theia |
|--------|---------------|---------------|
| **Language Workbench** | ✅ Powerful | ⚠️ Basic |
| **Web Deployment** | ❌ Desktop only | ✅ Browser support |
| **Custom DSL** | ✅ Excellent | ⚠️ Manual |
| **Learning Curve** | ⚠️ Steep | ⚠️ Moderate |
| **License** | ⚠️ Commercial | ✅ Open source |
| **Runtime** | JVM | Node.js |

**Verdict:** Theia better for web deployment and open-source requirements.

### 14.3 Theia vs Custom Web IDE

| Aspect | Custom Web IDE | Eclipse Theia |
|--------|----------------|---------------|
| **Development Time** | ⚠️ 6-12 months | ✅ 3-4 months |
| **Features** | Custom | ✅ Out-of-box |
| **Maintenance** | ⚠️ High | ✅ Lower |
| **Monaco Editor** | Manual integration | ✅ Built-in |
| **LSP Support** | Manual | ✅ Built-in |
| **File System** | Manual | ✅ Built-in |

**Verdict:** Theia significantly reduces development time while providing professional features.

---

## Conclusion

### Key Architectural Decisions

1. **Eclipse Theia Platform**: Enables three-panel custom layout that VS Code cannot provide
2. **Three-Panel Design**: Input (left) → Transform (center) → Output (right) optimizes transformation workflow
3. **LSP Integration**: Full language server support with multi-input awareness
4. **Real-Time Execution**: Live preview with automatic execution on changes
5. **Format-Specific Viewers**: JSON tree, XML formatted, CSV table, YAML viewers

### Benefits of Theia Extension

✅ **Custom Layout**: Three panels side-by-side (impossible in VS Code)
✅ **Web + Desktop**: Single codebase for browser and Electron
✅ **Full Control**: Customize every aspect of IDE
✅ **Professional**: Built on proven open-source platform
✅ **Monaco Integration**: Same editor as VS Code
✅ **LSP Native**: First-class language server support

### Expected Impact

**Developer Productivity:**
- 50% faster transformation development (compared to CLI + manual testing)
- Real-time feedback eliminates save-execute-view cycle
- Multi-input testing streamlined

**Learning Curve:**
- Visual feedback accelerates learning
- Inline documentation via hover
- Example-driven development

**Testing:**
- Side-by-side comparison
- Multiple test cases managed easily
- Regression testing built-in

### Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Development time reduction | 50% | User surveys |
| Adoption rate | 60% of UTL-X users | Download stats |
| Session time | 30+ min average | Telemetry |
| Satisfaction | 4.5/5.0 | User feedback |
| Cloud usage | 100+ active sessions | Analytics |

### Next Steps

1. ✅ Review this design document
2. ✅ Approve architecture
3. ⏭️ Start Phase 1 implementation
4. ⏭️ Recruit beta testers
5. ⏭️ Launch MVP in 4 months

---

**Document Status:** Ready for Implementation
**Author:** UTL-X Architecture Team
**Last Updated:** 2025-10-29
**Version:** 1.0
**Estimated Implementation:** 16 weeks (4 months)
