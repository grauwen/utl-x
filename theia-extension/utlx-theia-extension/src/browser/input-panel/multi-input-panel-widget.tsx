/**
 * Multi-Input Panel Widget
 *
 * Enhanced input panel supporting multiple inputs with vertical tabs
 * According to detailed-theia-design.txt specifications:
 * - Vertical tabs for multiple inputs
 * - Add/delete input functionality
 * - Format dropdowns (csv, json, xml, yaml, xsd, jsch, avro, proto)
 * - Execution MODE: Single format per input
 * - Message Contract MODE: Instance + Schema tabs with format linking
 */

import * as React from 'react';
import { injectable, inject, postConstruct, optional } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import { FileDialogService } from '@theia/filesystem/lib/browser';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import URI from '@theia/core/lib/common/uri';
import {
    UTLXService, UTLX_SERVICE_SYMBOL,
    UTLXMode,
    InputDocument,
    SchemaDocument,
    DataFormat,
    SchemaFormat,
    INPUT_PANEL_ID
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';
import { inferSchemaFromJson, inferSchemaFromXml, inferTableSchemaFromCsv, inferEdmxFromOData, formatSchema } from '../utils/schema-inferrer';
import { parseJsonSchemaToFieldTree, parseXsdToFieldTree, parseOSchToFieldTree, parseTschToFieldTree, SchemaFieldInfo } from '../utils/schema-field-tree-parser';
import { toHeaderIdentifier } from '../utils/header-identifier';
import { loadSession, saveSession, capContent, SESSION_KEYS } from '../utils/session-persistence';
import { buildAbstractForInput } from '../utils/input-abstract';

// Input panel format types (separate from protocol types)
// Data formats - used for actual data instances
export type InputDataFormatType = 'csv' | 'json' | 'xml' | 'yaml' | 'odata';

// Schema formats - used for schema definitions
export type InputSchemaFormatType = 'xsd' | 'jsch' | 'avro' | 'proto' | 'osch' | 'tsch';

// All 8 formats (tier1 data + tier2 schema formats)
export type AllInputFormats = InputDataFormatType | InputSchemaFormatType;

// Execution mode: All 8 formats allowed
export type RuntimeInstanceFormat = AllInputFormats;

// Message Contract mode: Instance tab - ALL 8 formats allowed
// (tier2 formats like xsd/jsch/avro/proto can be inputs for schema-to-schema transformations)
export type DesignTimeInstanceFormat = AllInputFormats;

// Message Contract mode: Schema tab - linked formats based on instance selection
export type DesignTimeSchemaFormat = InputSchemaFormatType;

// Combined types for state storage (backward compatibility)
export type InstanceFormat = AllInputFormats;
export type SchemaFormatType = InputSchemaFormatType;

export interface InputTab {
    id: string;
    name: string;
    instanceContent: string;
    instanceFormat: InstanceFormat;
    schemaContent: string;
    schemaFormat: SchemaFormatType;
    // MC mode: this input has no sample instance — only its schema (the contract).
    // Hides the Instance tab; the input contributes only its schema to coverage/generation.
    schemaOnly?: boolean;
    // CSV-specific parameters
    csvHeaders?: boolean;      // Default true
    csvDelimiter?: string;     // Default ","
    // Encoding parameters (COMMENTED OUT - encoding/BOM are auto-detected for inputs, not manually set)
    // encoding?: string;         // Character encoding (UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1, Windows-1252)
    // bom?: boolean;             // Byte Order Mark (default false)
    // Format detection status (pre-validation)
    formatDetected?: string | null;     // Detected format (e.g., 'xml', 'json', 'csv')
    formatMatchStatus?: 'match' | 'mismatch' | 'unknown';  // match = ✓, mismatch = ⚠, unknown = ?
    // UDM parse status (actual validation)
    udmParsed?: boolean;       // true = success (green check), false = error (red X), undefined = not checked yet
    udmValidating?: boolean;   // true = currently validating (pulse icon)
    udmError?: string;         // Error message for tooltip
    udmLanguage?: string;      // The parsed UDM representation of the input data (from /api/udm/export)
}

/**
 * IF09 session snapshot: input CONTENT keyed by input NAME. The structure
 * (which inputs exist, their formats) is the editor header's job (restored via
 * syncFromHeaders); this only carries the data the header can't hold. Capped per
 * field — `*Dropped` flags a sample that was too large to persist.
 */
interface PersistedInputContent {
    instanceContent?: string;
    schemaContent?: string;
    udmLanguage?: string;
    instanceDropped?: boolean;
    schemaDropped?: boolean;
}
type InputContentSnapshot = { [name: string]: PersistedInputContent };

export interface MultiInputPanelState {
    mode: UTLXMode;
    inputs: InputTab[];
    activeInputId: string;
    activeSubTab: 'instance' | 'schema'; // For Message Contract mode
    loading: boolean;
    udmDialogOpen?: boolean;
    abstractOpen?: boolean;  // IF10: "input summary" (deterministic abstract) overlay
    // Bumped only when a load auto-renames the active input from the filename,
    // so the (uncontrolled) name field remounts to show it. Typing never bumps
    // it, so the caret is never disturbed mid-edit.
    inputNameVersion?: number;
    // Same idea for the content editor: bumped only on PROGRAMMATIC content changes
    // (load, clear, infer, header-sync) so the uncontrolled textarea remounts to show
    // them. Typing never bumps it → the caret is never reset to the end.
    contentVersion?: number;
}

@injectable()
export class MultiInputPanelWidget extends ReactWidget {
    static readonly ID = INPUT_PANEL_ID;
    static readonly LABEL = 'Input';
    static readonly BUILD_VERSION = '2025-11-08T07:00:00Z'; // Build version for deployment verification

    @inject(UTLX_SERVICE_SYMBOL) @optional()
    protected readonly utlxService?: UTLXService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    @inject(FileDialogService)
    protected readonly fileDialogService!: FileDialogService;

    @inject(FileService)
    protected readonly fileService!: FileService;

    private state: MultiInputPanelState = {
        mode: UTLXMode.EXECUTION,
        inputs: [{
            id: 'input-1',
            name: 'input',
            instanceContent: '',
            instanceFormat: 'json',
            schemaContent: '',
            schemaFormat: 'jsch'
        }],
        activeInputId: 'input-1',
        activeSubTab: 'instance',
        loading: false,
        udmDialogOpen: false
    };

    private nextInputId = 2;

    // IF09: content snapshot loaded on init, consumed (one-shot) by the restore —
    // applied to the default input here and to tabs syncFromHeaders builds from the
    // restored header. Cleared after the restore window so later header edits don't
    // re-inject stale content.
    private pendingRestore?: InputContentSnapshot;
    // IF03 Phase 2: a transformation's input samples keyed by input name, to OVERWRITE
    // matching tabs (unlike the conservative IF09 restore). Set before the editor loads
    // the .utlx; consumed by syncFromHeaders as it rebuilds tabs from the header.
    private pendingBundleSamples?: { [name: string]: string };
    private persistTimer?: number;
    private udmDialogPosition = { x: 0, y: 0 };
    private isDraggingDialog = false;
    private dragOffset = { x: 0, y: 0 };
    private udmViewMode: 'raw' | 'pretty' = 'pretty';

    constructor() {
        super();
        this.id = MultiInputPanelWidget.ID;
        this.title.label = MultiInputPanelWidget.LABEL;
        this.title.caption = 'Input Data/Schema';
        this.title.closable = false;
        this.addClass('utlx-multi-input-panel');
    }

    @postConstruct()
    protected init(): void {
        // Log build version for deployment verification
        console.log(`[MultiInputPanelWidget] BUILD VERSION: ${MultiInputPanelWidget.BUILD_VERSION}`);
        console.log('[MultiInputPanelWidget] Widget initialized with vertical tabs layout');

        // IF09: load the per-tab content snapshot and fill any already-present input
        // (the default tab). Tabs that syncFromHeaders rebuilds from the restored
        // editor header get filled there. Cleared after a window so a later manual
        // header edit can't re-inject stale content.
        this.pendingRestore = loadSession<InputContentSnapshot>(SESSION_KEYS.inputPanel);
        if (this.pendingRestore) {
            const filled = this.state.inputs.map(i => this.applyPendingContent(i));
            this.state = { ...this.state, inputs: filled };
            window.setTimeout(() => { this.pendingRestore = undefined; }, 5000);
            window.setTimeout(() => this.refireRestoredUdm(), 300);
        }

        this.update();

        // Subscribe to mode changes
        this.eventService.onModeChanged(event => {
            console.log('[MultiInputPanelWidget] Mode changed to:', event.mode);
            // When entering Message Contract, sync schemaFormat to match instanceFormat
            // (the auto-linking only fires on instance format changes while already in DT mode).
            // We do NOT silently change a chosen instance format — if it isn't valid for MC
            // the dropdown flags it red (see formatInvalid in render) so the user fixes it.
            const updatedInputs = event.mode === UTLXMode.MESSAGE_CONTRACT
                ? this.state.inputs.map(input => ({
                    ...input,
                    schemaFormat: this.getDefaultSchemaFormat(input.instanceFormat)
                }))
                : this.state.inputs;
            this.setState({
                mode: event.mode,
                inputs: updatedInputs,
                activeSubTab: 'instance'
            });
        });

        // Subscribe to UDM requests (e.g., when Function Builder opens)
        this.eventService.onRequestCurrentUdm(() => {
            console.log('[MultiInputPanelWidget] Received request for current UDM');
            // Re-fire UDM events for all inputs that have UDM data
            this.state.inputs.forEach(input => {
                if (input.udmLanguage && input.udmParsed === true) {
                    console.log('[MultiInputPanelWidget] Re-firing UDM for input:', input.name);
                    this.eventService.fireInputUdmUpdated({
                        inputId: input.id,
                        inputName: input.name,
                        udmLanguage: input.udmLanguage,
                        format: input.instanceFormat
                    });
                }
            });
        });
    }

    protected render(): React.ReactNode {
        const { mode, inputs, activeInputId, activeSubTab, loading, udmDialogOpen } = this.state;
        const activeInput = inputs.find(input => input.id === activeInputId);

        if (!activeInput) {
            return <div>Error: No active input</div>;
        }

        // Determine current content and format based on mode and active sub-tab
        const isMessageContract = mode === UTLXMode.MESSAGE_CONTRACT;
        const currentContent = isMessageContract && activeSubTab === 'schema'
            ? activeInput.schemaContent
            : activeInput.instanceContent;
        const currentFormat = isMessageContract && activeSubTab === 'schema'
            ? activeInput.schemaFormat
            : activeInput.instanceFormat;

        // Which formats are valid for the current mode/tab — and is the current one invalid?
        // We don't silently change a stale format (e.g. a schema format left over from
        // Execution mode); instead we keep it selected and flag it red so the user fixes it.
        const allowedFormats: string[] = isMessageContract
            ? (activeSubTab === 'schema'
                ? ['jsch', 'xsd', 'tsch', 'avro', 'proto', 'osch']
                : ['json', 'xml', 'yaml', 'csv', 'odata'])
            : ['json', 'xml', 'yaml', 'csv', 'odata', 'jsch', 'xsd', 'avro', 'proto', 'osch', 'tsch'];
        const formatInvalid = !!currentFormat && !allowedFormats.includes(currentFormat);

        return (
            <div className='utlx-multi-input-panel-container'>
                {/* Vertical Tabs for Multiple Inputs - Fixed on Left */}
                <div className='utlx-vertical-tabs'>
                    {inputs.map(input => (
                        <div
                            key={input.id}
                            className={`utlx-vertical-tab ${input.id === activeInputId ? 'active' : ''}`}
                            onClick={() => this.handleSelectInput(input.id)}
                        >
                            <span className='utlx-vertical-tab-name' title={input.name}>
                                {input.name}
                            </span>
                            {inputs.length > 1 && (
                                <button
                                    className='utlx-vertical-tab-delete'
                                    onClick={(e) => this.handleDeleteInput(input.id, e)}
                                    title='Delete input'
                                >
                                    ×
                                </button>
                            )}
                        </div>
                    ))}
                    <button
                        className='utlx-vertical-tab-add'
                        onClick={() => this.handleAddInput()}
                        title='Add input'
                    >
                        +
                    </button>
                </div>

                {/* Main Content Area - Right Side */}
                <div className='utlx-input-content-area'>
                    {/* Header - Input name and action buttons */}
                    <div className='utlx-panel-header'>
                        <div className='utlx-header-left'>
                            <input
                                type='text'
                                className='utlx-input-name-editor'
                                // Uncontrolled: the DOM owns the caret. A controlled
                                // value= re-applied by ReactWidget's async update()
                                // reset the cursor to end on every keystroke (the
                                // "backspace deletes p but jumps to end" bug).
                                // key tied to the input id re-seeds defaultValue when
                                // the user switches inputs, without re-seeding mid-type.
                                // inputNameVersion forces a remount when a load
                                // auto-renames the input (programmatic change).
                                key={`name-${activeInputId}-${this.state.inputNameVersion ?? 0}`}
                                defaultValue={activeInput.name}
                                onChange={(e) => this.handleRenameInput(activeInputId, e.target.value)}
                                onBlur={(e) => this.commitRenameInput(activeInputId, e.target.value)}
                                placeholder='Input name'
                            />
                            {/* Format Detection Indicator */}
                            {activeInput.formatMatchStatus === 'match' && (
                                <span
                                    className='utlx-format-indicator'
                                    style={{ color: '#50fa7b', fontSize: '11px', marginRight: '8px' }}
                                    title={`Format detection: ${activeInput.formatDetected?.toUpperCase()} (matches declared format)`}
                                >
                                    ✓ Format
                                </span>
                            )}
                            {activeInput.formatMatchStatus === 'mismatch' && (
                                <span
                                    className='utlx-format-indicator'
                                    style={{ color: '#ff5555', fontSize: '11px', marginRight: '8px' }}
                                    title={`Format mismatch: Declared ${activeInput.instanceFormat.toUpperCase()}, detected ${activeInput.formatDetected?.toUpperCase() || 'unknown'}`}
                                >
                                    ⚠ Format
                                </span>
                            )}
                            {activeInput.formatMatchStatus === 'unknown' && (
                                <span
                                    className='utlx-format-indicator'
                                    style={{ color: '#f1fa8c', fontSize: '11px', marginRight: '8px' }}
                                    title={`Format could not be auto-detected. Using declared format: ${activeInput.instanceFormat.toUpperCase()}`}
                                >
                                    ? Format
                                </span>
                            )}

                            {/* UDM Parse Status Indicator */}
                            {activeInput.udmValidating && (
                                <span
                                    className='utlx-udm-indicator'
                                    style={{ color: '#6272a4' }}
                                    title='Validating UDM...'
                                >
                                    ⟳ UDM
                                </span>
                            )}
                            {!activeInput.udmValidating && activeInput.udmParsed === true && (
                                <button
                                    className='utlx-udm-indicator utlx-udm-clickable'
                                    style={{ color: '#50fa7b' }}
                                    title={`UDM parsed successfully - Click to re-validate`}
                                    onClick={() => this.validateInput(activeInputId)}
                                >
                                    ✓ UDM
                                </button>
                            )}
                            {!activeInput.udmValidating && activeInput.udmParsed === false && (
                                <button
                                    className='utlx-udm-indicator utlx-udm-clickable'
                                    style={{ color: '#ff5555' }}
                                    title={`UDM parse error: ${activeInput.udmError || 'Unknown error'} - Click to retry`}
                                    onClick={() => this.validateInput(activeInputId)}
                                >
                                    ✗ UDM
                                </button>
                            )}
                        </div>
                        <div className='utlx-panel-actions'>
                            {activeInput.udmParsed === true && (
                                <button
                                    onClick={() => this.handleViewUdm()}
                                    title='View UDM representation'
                                >
                                    <span className='codicon codicon-eye' style={{fontSize: '11px'}}></span>
                                    {' '}UDM
                                </button>
                            )}
                            {/* IF10: deterministic input summary — enabled once the input
                                 has parsed UDM (instance data) OR a schema to describe */}
                            {(() => {
                                const isSchemaFmt = ['jsch', 'xsd', 'osch', 'tsch'].includes(activeInput.instanceFormat);
                                const canSummarize = activeInput.udmParsed === true
                                    || (isSchemaFmt && !!activeInput.instanceContent?.trim())
                                    || !!activeInput.schemaContent?.trim();
                                return (
                            <button
                                onClick={() => this.setState({ abstractOpen: true })}
                                disabled={!canSummarize}
                                title={canSummarize
                                    ? 'Show input summary (shape, fields, arrays, depth)'
                                    : 'Parse the UDM (or load a schema) first to summarize this input'}
                            >
                                <span className='codicon codicon-info' style={{fontSize: '11px'}}></span>
                                {' '}Info
                            </button>
                                );
                            })()}
                            {/* Infer Schema button - only in design-time mode on schema tab with instance content */}
                            {isMessageContract && activeSubTab === 'schema' && !this.isSchemaTabDisabled(activeInput.instanceFormat) && activeInput.instanceContent && (
                                <button
                                    onClick={() => this.handleInferInputSchema()}
                                    title='Infer input schema from instance data'
                                >
                                    <span className='codicon codicon-symbol-structure' style={{fontSize: '11px'}}></span>
                                    {' '}Infer Schema
                                </button>
                            )}
                            <button
                                onClick={() => this.handleLoadFile()}
                                disabled={loading}
                                title='Load from file'
                            >
                                <span className='codicon codicon-folder-opened' style={{fontSize: '11px'}}></span>
                                {' '}Load
                            </button>
                            <button
                                onClick={() => this.handleClear()}
                                disabled={loading || !currentContent}
                                title='Clear input'
                            >
                                <span className='codicon codicon-clear-all' style={{fontSize: '11px'}}></span>
                                {' '}Clear
                            </button>
                        </div>
                    </div>

                    {/* Instance/Schema Horizontal Tabs - Below header */}
                    <div className='utlx-horizontal-tabs'>
                        {isMessageContract ? (
                            <>
                                {/* Message Contract: Schema is the contract (primary). The
                                    Instance tab (an optional sample) is hidden when the
                                    input is marked "schema only". The Schema tab is reachable
                                    except when the instance carries a schema FORMAT (an invalid
                                    leftover from Execution) — then it's blocked until fixed. */}
                                {!activeInput.schemaOnly && (
                                    <button
                                        className={`utlx-horizontal-tab ${activeSubTab === 'instance' ? 'active' : ''}`}
                                        onClick={() => this.handleSubTabSwitch('instance')}
                                    >
                                        Instance
                                    </button>
                                )}
                                {(() => {
                                    const schemaBlocked = !activeInput.schemaOnly && this.isSchemaTabDisabled(activeInput.instanceFormat);
                                    return (
                                        <button
                                            className={`utlx-horizontal-tab ${activeSubTab === 'schema' ? 'active' : ''} ${schemaBlocked ? 'disabled' : ''}`}
                                            onClick={() => !schemaBlocked && this.handleSubTabSwitch('schema')}
                                            disabled={schemaBlocked}
                                            title={schemaBlocked
                                                ? 'Schema not available: the instance format is a schema format — fix the instance format first'
                                                : 'View input schema'}
                                        >
                                            Schema
                                        </button>
                                    );
                                })()}
                                <label
                                    className='utlx-schema-only-toggle'
                                    title='This input has no sample instance — use only its schema (the contract). Hides the Instance tab.'
                                >
                                    <input
                                        type='checkbox'
                                        checked={!!activeInput.schemaOnly}
                                        onChange={() => this.toggleSchemaOnly()}
                                    />
                                    schema only
                                </label>
                            </>
                        ) : (
                            <>
                                {/* Runtime: Show only Instance label (non-clickable) */}
                                <div className='utlx-horizontal-tab active'>
                                    Instance
                                </div>
                            </>
                        )}
                    </div>

                    {/* Format Selector and CSV parameters */}
                    <div className='utlx-panel-toolbar'>
                        <label>
                            Format:
                            <select
                                className={formatInvalid ? 'utlx-format-invalid' : ''}
                                value={currentFormat}
                                onChange={(e) => this.handleFormatChange(e.target.value as any)}
                                disabled={loading}
                                title={formatInvalid ? `“${currentFormat}” is not valid for this ${activeSubTab} in Message Contract mode — pick a valid format` : undefined}
                            >
                                {formatInvalid && (
                                    <option value={currentFormat}>{currentFormat} — not valid here</option>
                                )}
                                {isMessageContract && activeSubTab === 'schema' ? (
                                    // Message Contract Schema tab: schema formats only
                                    <>
                                        <option value='jsch'>jsch</option>
                                        <option value='xsd'>xsd</option>
                                        <option value='tsch'>tsch</option>
                                        <option value='avro'>avro</option>
                                        <option value='proto'>proto</option>
                                        <option value='osch'>osch</option>
                                    </>
                                ) : isMessageContract ? (
                                    // Message Contract Instance tab: data formats only
                                    <>
                                        <option value='json'>json</option>
                                        <option value='xml'>xml</option>
                                        <option value='yaml'>yaml</option>
                                        <option value='csv'>csv</option>
                                        <option value='odata'>odata</option>
                                    </>
                                ) : (
                                    // Execution mode: all formats (data + schema-as-data)
                                    <>
                                        <option value='json'>json</option>
                                        <option value='xml'>xml</option>
                                        <option value='yaml'>yaml</option>
                                        <option value='csv'>csv</option>
                                        <option value='odata'>odata</option>
                                        <option value='jsch'>jsch</option>
                                        <option value='xsd'>xsd</option>
                                        <option value='avro'>avro</option>
                                        <option value='proto'>proto</option>
                                        <option value='osch'>osch</option>
                                        <option value='tsch'>tsch</option>
                                    </>
                                )}
                            </select>
                        </label>

                        {/* CSV-specific parameters - shown inline to the right of format */}
                        {currentFormat === 'csv' && activeSubTab === 'instance' && (
                            <>
                                <label>
                                    Headers:
                                    <select
                                        value={activeInput.csvHeaders === false ? 'false' : 'true'}
                                        onChange={(e) => this.handleCsvHeadersChange(e.target.value === 'true')}
                                        disabled={loading}
                                    >
                                        <option value='true'>Yes</option>
                                        <option value='false'>No</option>
                                    </select>
                                </label>
                                <label>
                                    Delimiter:
                                    <select
                                        value={activeInput.csvDelimiter || ','}
                                        onChange={(e) => this.handleCsvDelimiterChange(e.target.value)}
                                        disabled={loading}
                                    >
                                        <option value=','>Comma (,)</option>
                                        <option value=';'>Semicolon (;)</option>
                                        <option value='\t'>Tab (\t)</option>
                                        <option value='|'>Pipe (|)</option>
                                    </select>
                                </label>
                            </>
                        )}

                        {/* Encoding parameters - shown for all formats in instance tab */}
                        {activeSubTab === 'instance' && (
                            <>
                                {/* ENCODING/BOM CONTROLS REMOVED - These are auto-detected for inputs, not manually set
                                <label>
                                    Encoding:
                                    <select
                                        value={activeInput.encoding || 'UTF-8'}
                                        onChange={(e) => this.handleEncodingChange(e.target.value)}
                                        disabled={loading}
                                    >
                                        <option value='UTF-8'>UTF-8</option>
                                        <option value='UTF-16LE'>UTF-16LE</option>
                                        <option value='UTF-16BE'>UTF-16BE</option>
                                        <option value='ISO-8859-1'>ISO-8859-1</option>
                                        <option value='Windows-1252'>Windows-1252</option>
                                    </select>
                                </label>
                                <label>
                                    BOM:
                                    <select
                                        value={activeInput.bom ? 'true' : 'false'}
                                        onChange={(e) => this.handleBomChange(e.target.value === 'true')}
                                        disabled={loading || !this.isBomSupported(activeInput.encoding)}
                                        title={!this.isBomSupported(activeInput.encoding) ? 'BOM not applicable for this encoding' : 'Byte Order Mark'}
                                    >
                                        <option value='false'>No</option>
                                        <option value='true'>Yes</option>
                                    </select>
                                </label>
                                */}
                            </>
                        )}
                    </div>

                    {/* Content Editor */}
                    <div className='utlx-panel-content'>
                        <textarea
                            className='utlx-input-editor'
                            // Uncontrolled (defaultValue + key) — a controlled value= is
                            // re-applied by ReactWidget's async update() and resets the
                            // caret to the end on every keystroke (same bug as the name
                            // field). The key re-seeds only when the active input/sub-tab
                            // changes or content is set programmatically (contentVersion),
                            // never while typing.
                            key={`editor-${activeInputId}-${activeSubTab}-${this.state.contentVersion ?? 0}`}
                            defaultValue={currentContent}
                            onChange={(e) => this.handleContentChange(e.target.value)}
                            onPaste={(e) => this.handlePaste(e)}
                            placeholder={this.getPlaceholder(activeInput, activeSubTab)}
                            disabled={loading}
                            spellCheck={false}
                        />
                    </div>

                    {/* Footer */}
                    <div className='utlx-panel-footer'>
                        <span className='utlx-status'>
                            {loading
                                ? 'Loading...'
                                : currentContent
                                ? `${currentContent.length} characters`
                                : 'No input loaded'}
                        </span>
                    </div>
                </div>

                {/* UDM View Dialog */}
                {udmDialogOpen && activeInput.udmLanguage && (
                    <div className='utlx-udm-dialog-overlay'>
                        <div
                            className='utlx-udm-dialog'
                            style={{
                                transform: `translate(calc(-50% + ${this.udmDialogPosition.x}px), calc(-50% + ${this.udmDialogPosition.y}px))`,
                                cursor: this.isDraggingDialog ? 'grabbing' : 'default'
                            }}
                        >
                            <div
                                className='utlx-udm-dialog-header'
                                onMouseDown={(e) => this.handleDragStart(e)}
                                style={{ cursor: 'grab' }}
                            >
                                <h3>UDM Representation</h3>
                                <button
                                    className='utlx-udm-dialog-close'
                                    onClick={() => this.handleCloseUdmDialog()}
                                    title='Close'
                                >
                                    <span className='codicon codicon-close'></span>
                                </button>
                            </div>
                            <div className='utlx-udm-dialog-toolbar'>
                                <label>
                                    View:
                                    <select
                                        value={this.udmViewMode}
                                        onChange={(e) => {
                                            this.udmViewMode = (e.target as HTMLSelectElement).value as 'raw' | 'pretty';
                                            this.update();
                                        }}
                                    >
                                        <option value='raw'>Raw</option>
                                        <option value='pretty'>Pretty</option>
                                    </select>
                                </label>
                            </div>
                            <div className='utlx-udm-dialog-content'>
                                <textarea
                                    className='utlx-udm-viewer'
                                    value={this.udmViewMode === 'pretty' ? this.formatUdm(activeInput.udmLanguage) : activeInput.udmLanguage}
                                    readOnly
                                    spellCheck={false}
                                />
                            </div>
                            <div className='utlx-udm-dialog-footer'>
                                <button
                                    onClick={() => this.handleCopyUdmToClipboard()}
                                    title='Copy UDM to clipboard'
                                >
                                    <span className='codicon codicon-copy' style={{fontSize: '11px'}}></span>
                                    {' '}Copy to Clipboard
                                </button>
                                <button
                                    onClick={() => this.handleSaveUdm()}
                                    title='Save UDM to file'
                                >
                                    <span className='codicon codicon-save' style={{fontSize: '11px'}}></span>
                                    {' '}Save
                                </button>
                                <button
                                    onClick={() => this.handleCloseUdmDialog()}
                                    title='Close dialog'
                                >
                                    <span className='codicon codicon-close' style={{fontSize: '11px'}}></span>
                                    {' '}Close
                                </button>
                            </div>
                        </div>
                    </div>
                )}
                {/* IF10: input summary (deterministic abstract — no AI) */}
                {this.state.abstractOpen && (
                    <div className='utlx-udm-dialog-overlay' onClick={() => this.setState({ abstractOpen: false })}>
                        <div className='utlx-udm-dialog utlx-abstract-dialog' onClick={(e) => e.stopPropagation()}>
                            <div className='utlx-udm-dialog-header'>
                                <h3>Input summary — ${activeInput.name}</h3>
                                <button
                                    className='utlx-udm-dialog-close'
                                    onClick={() => this.setState({ abstractOpen: false })}
                                    title='Close'
                                >
                                    <span className='codicon codicon-close'></span>
                                </button>
                            </div>
                            <div className='utlx-udm-dialog-content'>
                                <pre className='utlx-abstract-text'>
                                    {this.abstractForActiveInput(activeInput) || 'No structure available for this input.'}
                                </pre>
                            </div>
                            <div className='utlx-udm-dialog-footer'>
                                <button
                                    onClick={() => this.setState({ abstractOpen: false })}
                                    title='Close'
                                >
                                    <span className='codicon codicon-close' style={{fontSize: '11px'}}></span>
                                    {' '}Close
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        );
    }

    /**
     * Check if schema tab should be disabled (blurred)
     * Schema formats (xsd, jsch, avro, proto, osch) when selected as instance should blur the schema tab
     */
    private isSchemaTabDisabled(instanceFormat: InstanceFormat | SchemaFormatType): boolean {
        return ['xsd', 'jsch', 'avro', 'proto', 'osch', 'tsch'].includes(instanceFormat);
    }

    private getPlaceholder(input: InputTab, activeSubTab: 'instance' | 'schema'): string {
        const isSchema = activeSubTab === 'schema';
        const format = isSchema ? input.schemaFormat : input.instanceFormat;

        if (isSchema) {
            switch (format) {
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'jsch':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
                case 'avro':
                    return '{\n  "type": "record",\n  "name": "Example",\n  "fields": [\n    ...\n  ]\n}';
                case 'proto':
                    return 'syntax = "proto3";\n\nmessage Example {\n  ...\n}';
                case 'osch':
                    return '<?xml version="1.0" encoding="utf-8"?>\n<edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">\n  <edmx:DataServices>\n    <Schema Namespace="Example" xmlns="http://docs.oasis-open.org/odata/ns/edm">\n      ...\n    </Schema>\n  </edmx:DataServices>\n</edmx:Edmx>';
                case 'tsch':
                    return '{\n  "fields": [\n    {"name": "id", "type": "integer", "constraints": {"required": true}},\n    {"name": "name", "type": "string"}\n  ],\n  "primaryKey": "id"\n}';
                default:
                    return 'Paste or load your schema here...';
            }
        } else {
            // Instance format placeholders (including schema formats for Execution mode)
            switch (format) {
                case 'xml':
                    return '<root>\n  <element>data</element>\n</root>';
                case 'json':
                    return '{\n  "key": "value"\n}';
                case 'yaml':
                    return 'key: value\nlist:\n  - item1\n  - item2';
                case 'csv':
                    return 'name,age,email\nJohn,30,john@example.com';
                case 'xsd':
                    return '<?xml version="1.0"?>\n<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">\n  <!-- Define your XML schema here -->\n</xs:schema>';
                case 'jsch':
                    return '{\n  "$schema": "http://json-schema.org/draft-07/schema#",\n  "type": "object",\n  "properties": {\n    ...\n  }\n}';
                case 'avro':
                    return '{\n  "type": "record",\n  "name": "Example",\n  "fields": [\n    ...\n  ]\n}';
                case 'proto':
                    return 'syntax = "proto3";\n\nmessage Example {\n  ...\n}';
                case 'odata':
                    return '{\n  "@odata.context": "$metadata#Products",\n  "value": [\n    { "ID": 1, "Name": "Widget" }\n  ]\n}';
                case 'osch':
                    return '<?xml version="1.0" encoding="utf-8"?>\n<edmx:Edmx Version="4.0" xmlns:edmx="http://docs.oasis-open.org/odata/ns/edmx">\n  <edmx:DataServices>\n    <Schema Namespace="Example" xmlns="http://docs.oasis-open.org/odata/ns/edm">\n      <EntityType Name="Product">\n        <Key>\n          <PropertyRef Name="ID"/>\n        </Key>\n        <Property Name="ID" Type="Edm.Int32" Nullable="false"/>\n        <Property Name="Name" Type="Edm.String"/>\n      </EntityType>\n    </Schema>\n  </edmx:DataServices>\n</edmx:Edmx>';
                case 'tsch':
                    return '{\n  "fields": [\n    {"name": "id", "type": "integer", "constraints": {"required": true}},\n    {"name": "name", "type": "string"}\n  ],\n  "primaryKey": "id"\n}';
                default:
                    return 'Paste or load your input data here...';
            }
        }
    }

    private handleSelectInput(inputId: string): void {
        // A schema-only input has no Instance tab, so never leave the sub-tab on
        // 'instance' when selecting one (keeps state consistent with what's shown).
        const selected = this.state.inputs.find(i => i.id === inputId);
        const forceSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && !!selected?.schemaOnly;
        this.setState({
            activeInputId: inputId,
            ...(forceSchema ? { activeSubTab: 'schema' as const } : {})
        });
    }

    /**
     * Feature: toggle "schema only" for the active input (MC mode). When on, the input
     * carries just its schema (the contract) — the Instance tab is hidden and the input
     * contributes only its schema to coverage/generation. Enabling jumps to the Schema tab.
     */
    private toggleSchemaOnly(): void {
        const id = this.state.activeInputId;
        const next = !this.state.inputs.find(i => i.id === id)?.schemaOnly;
        this.setState({
            inputs: this.state.inputs.map(i => i.id === id ? { ...i, schemaOnly: next } : i),
            ...(next ? { activeSubTab: 'schema' as const } : {})
        });
    }

    private handleAddInput(): void {
        // Name preference is "input{nextInputId}", but a manual rename may already
        // hold that name (e.g. tab renamed to "input5" before adding the 5th input).
        // freeDefaultName guarantees a name no existing tab uses. The id stays
        // "input-{nextInputId}" (monotonic, always unique) even if name diverges.
        const taken = new Set(this.state.inputs.map(i => i.name.toLowerCase()));
        const newInput: InputTab = {
            id: `input-${this.nextInputId}`,
            name: this.freeDefaultName(taken, `input${this.nextInputId}`),
            instanceContent: '',
            instanceFormat: 'json',
            schemaContent: '',
            schemaFormat: 'jsch'
        };
        this.nextInputId++;

        this.setState({
            inputs: [...this.state.inputs, newInput],
            activeInputId: newInput.id
        });

        this.messageService.info(`Added ${newInput.name}`);

        // Fire input added event
        this.eventService.fireInputAdded({
            inputId: newInput.id,
            name: newInput.name
        });

        // Fire format changed event for the new input
        this.eventService.fireInputFormatChanged({
            format: newInput.instanceFormat,
            inputId: newInput.id,
            isSchema: false
        });
    }

    private handleDeleteInput(inputId: string, event: React.MouseEvent): void {
        // Prevent event from bubbling to parent (which would select the input)
        event.stopPropagation();
        event.preventDefault();

        if (this.state.inputs.length <= 1) {
            this.messageService.warn('Cannot delete the last input');
            return;
        }

        const inputToDelete = this.state.inputs.find(i => i.id === inputId);
        if (!inputToDelete) return;

        // Perform the delete directly (no confirmation dialog to avoid issues)
        // The user clicked the X button deliberately, so we trust their intent
        this.doDeleteInput(inputToDelete);
    }

    private doDeleteInput(inputToDelete: InputTab): void {
        const newInputs = this.state.inputs.filter(input => input.id !== inputToDelete.id);
        let newActiveId = this.state.activeInputId;

        if (this.state.activeInputId === inputToDelete.id) {
            newActiveId = newInputs[0]?.id || '';
        }

        // Update state
        this.setState({
            inputs: newInputs,
            activeInputId: newActiveId
        });

        this.messageService.info(`Deleted ${inputToDelete.name}`);

        // Use setTimeout to ensure state update is processed before firing event
        setTimeout(() => {
            this.eventService.fireInputDeleted({
                inputId: inputToDelete.id,
                name: inputToDelete.name
            });
        }, 0);
    }

    /**
     * Live (per-keystroke) rename. The name field is UNCONTROLLED, so we must NOT
     * call this.update() here — re-rendering mid-edit would reset the caret to the
     * end (the bug this fixes). We only update the model in place for valid names,
     * and fire the name-changed event so dependent panels track it as you type.
     * Validation/revert of empty or duplicate names is deferred to onBlur
     * (commitRenameInput), where re-seeding the field is harmless.
     */
    private handleRenameInput(inputId: string, newName: string): void {
        const oldInput = this.state.inputs.find(i => i.id === inputId);
        if (!oldInput) return;

        const oldName = oldInput.name;

        // Skip live-commit for empty or duplicate names — keep typing; onBlur
        // will warn and revert. (No caret-disrupting re-render here.)
        const trimmed = newName.trim();
        if (trimmed === '') return;
        const duplicate = this.state.inputs.some(
            input => input.id !== inputId && input.name.toLowerCase() === trimmed.toLowerCase()
        );
        if (duplicate) return;

        // Update the model WITHOUT re-rendering (uncontrolled input owns the DOM).
        this.state = {
            ...this.state,
            inputs: this.state.inputs.map(input =>
                input.id === inputId ? { ...input, name: newName } : input
            )
        };

        if (oldName !== newName) {
            this.eventService.fireInputNameChanged({ inputId, oldName, newName });
        }
    }

    /**
     * Commit on blur: validate the final value. Empty or duplicate → warn and
     * revert (re-render to restore the previous valid name; the `key` on the
     * field re-seeds defaultValue). Valid → ensure the model matches.
     */
    private commitRenameInput(inputId: string, newName: string): void {
        const input = this.state.inputs.find(i => i.id === inputId);
        if (!input) return;

        const trimmed = newName.trim();

        if (trimmed === '') {
            this.messageService.warn('Input name cannot be empty');
            this.update(); // revert field to the last valid name
            return;
        }

        const duplicate = this.state.inputs.some(
            i => i.id !== inputId && i.name.toLowerCase() === trimmed.toLowerCase()
        );
        if (duplicate) {
            this.messageService.warn(`Input name "${trimmed}" is already in use. Please choose a different name.`);
            this.update(); // revert field to the last valid name
            return;
        }

        // Normalize to the trimmed value and re-render once (caret no longer matters on blur).
        if (input.name !== trimmed) {
            const oldName = input.name;
            this.setState({
                inputs: this.state.inputs.map(i =>
                    i.id === inputId ? { ...i, name: trimmed } : i
                )
            });
            this.eventService.fireInputNameChanged({ inputId, oldName, newName: trimmed });
        }
    }

    private handleSubTabSwitch(subTab: 'instance' | 'schema'): void {
        this.setState({ activeSubTab: subTab });
    }

    private handleContentChange(content: string): void {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput) return;

        const isSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';

        // Update the model IN PLACE without re-rendering — the uncontrolled textarea
        // owns the DOM/caret. Re-rendering mid-keystroke (the old setState) reset the
        // cursor to the end. Downstream status (UDM, char count) refreshes on the next
        // render (button click, blur, validate) — same tradeoff as the name field.
        this.state = {
            ...this.state,
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? { ...input, [isSchema ? 'schemaContent' : 'instanceContent']: content }
                    : input
            )
        };
    }

    private handlePaste(e: React.ClipboardEvent<HTMLTextAreaElement>): void {
        console.log('[MultiInputPanel] Paste detected');

        // Only validate instance content, not schema content
        const isSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';
        if (isSchema) {
            console.log('[MultiInputPanel] Paste in schema tab - skipping UDM validation');
            return;
        }

        // Wait a bit for the paste to complete and state to update
        setTimeout(() => {
            console.log('[MultiInputPanel] Triggering UDM validation after paste');
            this.validateInput(this.state.activeInputId);
        }, 100);
    }

    private handleFormatChange(format: InstanceFormat | SchemaFormatType): void {
        const isSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        console.log('[MultiInputPanel] Format changed:', { format, isSchema });

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        [isSchema ? 'schemaFormat' : 'instanceFormat']: format,
                        // When instance format changes, update schema format according to linking rules
                        ...((!isSchema && this.state.mode === UTLXMode.MESSAGE_CONTRACT) ? {
                            schemaFormat: this.getDefaultSchemaFormat(format as InstanceFormat)
                        } : {})
                    }
                    : input
            )
        });

        // Fire event to notify editor widget to update headers
        console.log('[MultiInputPanel] Firing input format changed event');
        this.eventService.fireInputFormatChanged({
            format,
            isSchema,
            inputId: this.state.activeInputId,
            csvHeaders: activeInput?.csvHeaders,
            csvDelimiter: activeInput?.csvDelimiter
        });

        // Trigger UDM validation after format change (only for instance format, not schema)
        if (!isSchema && activeInput?.instanceContent.trim()) {
            console.log('[MultiInputPanel] Format changed - triggering UDM validation');
            // Use setTimeout to ensure state has been updated
            setTimeout(() => {
                this.validateInput(this.state.activeInputId);
            }, 100);
        }
    }

    /**
     * Get default schema format based on instance format (format linking)
     */
    private getDefaultSchemaFormat(instanceFormat: InstanceFormat): SchemaFormatType {
        switch (instanceFormat) {
            case 'xml':
                return 'xsd';
            case 'json':
            case 'yaml':
            case 'odata':
                return 'jsch';
            case 'csv':
                return 'jsch'; // Default to jsch, though tsch is mentioned in design
            default:
                return 'jsch';
        }
    }

    /**
     * Validate input content against UDM parser
     */
    /**
     * Detect actual format of content based on content analysis
     */
    private detectContentFormat(content: string): string | null {
        const trimmed = content.trim();
        if (!trimmed) return null;

        // EDMX/CSDL detection - OData metadata has specific namespace and elements
        // Must check BEFORE generic XML since EDMX is XML
        const withoutComments = trimmed.replace(/<!--[\s\S]*?-->/g, '').trim();
        if (/<edmx:Edmx/i.test(trimmed) ||
            trimmed.includes('docs.oasis-open.org/odata/ns/edmx') ||
            trimmed.includes('schemas.microsoft.com/ado/2007/06/edmx')) {
            return 'osch';
        }

        // XSD detection - XML Schema has specific namespace and elements
        // Must check BEFORE generic XML since XSD is XML
        // XSD must have <xs:schema> or <xsd:schema> or <schema> as ROOT element with XMLSchema namespace
        // Don't confuse with regular XML that just uses xmlns:xsi for validation
        if (/<xs:schema|<xsd:schema/i.test(trimmed) ||
            (withoutComments.includes('<schema') &&
             withoutComments.includes('xmlns') &&
             trimmed.includes('http://www.w3.org/2001/XMLSchema') &&
             !trimmed.includes('xmlns:xsi'))) {  // xmlns:xsi is used in regular XML for validation, not schema definition
            return 'xsd';
        }

        // XML detection - check for XML patterns
        // Valid XML can start with:
        // - <?xml declaration
        // - <!-- comment -->
        // - <!DOCTYPE ...>
        // - <element> (root element)
        if (trimmed.startsWith('<?xml') ||                    // XML declaration
            trimmed.startsWith('<!DOCTYPE') ||                // DOCTYPE
            trimmed.startsWith('<!--') ||                     // Comment first
            (trimmed.startsWith('<') &&                       // Root element
             !trimmed.startsWith('<![CDATA[') &&              // Not CDATA
             trimmed.includes('>') &&                         // Has closing bracket
             /^<[a-zA-Z]/.test(withoutComments))) {           // Element name starts with letter
            return 'xml';
        }

        // JSON Schema / OData / generic JSON detection
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            try {
                const parsed = JSON.parse(trimmed);
                // Check for JSON Schema markers
                if (parsed && typeof parsed === 'object') {
                    if (parsed.$schema ||
                        parsed.type === 'object' && (parsed.properties || parsed.required) ||
                        parsed.type === 'array' && parsed.items ||
                        parsed.definitions ||
                        parsed.$defs) {
                        return 'jsch';
                    }
                    // Check for OData JSON markers (@odata.context, @odata.type, @odata.id)
                    if (parsed['@odata.context'] || parsed['@odata.type'] || parsed['@odata.id'] ||
                        (parsed.value && Array.isArray(parsed.value) && parsed['@odata.context'])) {
                        return 'odata';
                    }
                    // Check for Table Schema markers (fields array with name/type objects)
                    if (parsed.fields && Array.isArray(parsed.fields) &&
                        parsed.fields.length > 0 && parsed.fields[0].name && parsed.fields[0].type) {
                        return 'tsch';
                    }
                }
                // Regular JSON
                return 'json';
            } catch {
                // Not valid JSON, could be YAML with JSON-like syntax
            }
        }

        // Protocol Buffers detection
        // Proto files have "syntax", "package", "message", "service" keywords
        if (/^\s*syntax\s*=\s*"proto[23]"/m.test(trimmed) ||
            (/^\s*package\s+[\w.]+;/m.test(trimmed) && /^\s*message\s+\w+\s*\{/m.test(trimmed)) ||
            (/^\s*message\s+\w+\s*\{/m.test(trimmed) && /^\s*\w+\s+\w+\s*=\s*\d+;/m.test(trimmed))) {
            return 'proto';
        }

        // Avro Schema detection
        // Avro schemas are JSON with specific structure
        if (trimmed.startsWith('{')) {
            try {
                const parsed = JSON.parse(trimmed);
                if (parsed && typeof parsed === 'object') {
                    // Avro has "type" field with values like "record", "enum", "array", "map"
                    // and "namespace", "name", "fields" for records
                    if ((parsed.type === 'record' && parsed.fields) ||
                        (parsed.type === 'enum' && parsed.symbols) ||
                        (parsed.type && typeof parsed.type === 'string' &&
                         ['record', 'enum', 'array', 'map', 'fixed'].includes(parsed.type)) ||
                        parsed.protocol) {  // Avro protocol
                        return 'avro';
                    }
                }
            } catch {
                // Not valid JSON
            }
        }

        // CSV detection - detect various delimiters (comma, semicolon, tab, pipe)
        const lines = trimmed.split('\n').filter(l => l.trim());
        if (lines.length >= 2) {
            // Common CSV delimiters
            const delimiters = [',', ';', '\t', '|'];

            for (const delimiter of delimiters) {
                const escapedDelimiter = delimiter === '|' ? '\\|' : delimiter;
                const delimiterRegex = new RegExp(escapedDelimiter, 'g');

                const firstLineCount = (lines[0].match(delimiterRegex) || []).length;
                const secondLineCount = (lines[1].match(delimiterRegex) || []).length;

                // Must have at least one delimiter and consistent count across lines
                if (firstLineCount > 0 && firstLineCount === secondLineCount) {
                    // Additional check: CSV typically doesn't have unquoted colons followed by space
                    // (to distinguish from YAML key: value syntax)
                    // But allow if the delimiter is NOT comma (semicolon CSV can have colons)
                    if (delimiter !== ',') {
                        return 'csv';
                    }

                    // For comma-delimited, check it's not YAML
                    const hasYamlPattern = /^\s*[\w-]+:\s+[\w]/m.test(trimmed);
                    if (!hasYamlPattern) {
                        return 'csv';
                    }
                }
            }
        }

        // YAML detection - has key: value patterns (but not JSON)
        if (!trimmed.startsWith('{') && !trimmed.startsWith('[') && !trimmed.startsWith('<')) {
            // Check for YAML patterns: "key:" at start of lines or "- item" for lists
            const yamlPattern = /^\s*[\w-]+:\s*|^\s*-\s+/m;
            if (yamlPattern.test(trimmed)) {
                return 'yaml';
            }
        }

        return null;
    }

    private async validateInput(inputId: string): Promise<void> {
        console.log('[MultiInputPanel] ========================================');
        console.log('[MultiInputPanel] validateInput() CALLED');
        console.log('[MultiInputPanel] Input ID:', inputId);

        const input = this.state.inputs.find(i => i.id === inputId);

        if (!input) {
            console.warn('[MultiInputPanel] Input not found:', inputId);
            return;
        }

        console.log('[MultiInputPanel] Input found:', {
            name: input.name,
            format: input.instanceFormat,
            contentLength: input.instanceContent.length,
            hasContent: !!input.instanceContent.trim()
        });

        if (!input.instanceContent.trim()) {
            console.log('[MultiInputPanel] Empty content - clearing validation status');
            this.updateInputValidation(inputId, undefined, false);
            return;
        }

        // PRE-VALIDATION: Detect actual content format and warn if mismatch
        console.log('[MultiInputPanel] ────────────────────────────────────────');
        console.log('[MultiInputPanel] 🔍 PRE-VALIDATION FORMAT DETECTION');
        console.log('[MultiInputPanel] (Before sending to UDM parser)');
        console.log('[MultiInputPanel] ────────────────────────────────────────');

        const detectedFormat = this.detectContentFormat(input.instanceContent);
        console.log('[MultiInputPanel] Declared format:', input.instanceFormat);
        console.log('[MultiInputPanel] Detected format:', detectedFormat || 'unknown');

        let formatMatchStatus: 'match' | 'mismatch' | 'unknown';

        if (detectedFormat && detectedFormat !== input.instanceFormat.toLowerCase()) {
            formatMatchStatus = 'mismatch';
            console.warn('[MultiInputPanel] ⚠️  FORMAT MISMATCH DETECTED!');
            console.warn('[MultiInputPanel]   Declared: ' + input.instanceFormat.toUpperCase());
            console.warn('[MultiInputPanel]   Detected: ' + detectedFormat.toUpperCase());
            console.warn('[MultiInputPanel]   → Content may be parsed incorrectly!');
            console.log('[MultiInputPanel] ────────────────────────────────────────');

            // Show warning to user
            this.messageService.warn(
                `Format mismatch: Input "${input.name}" is set to ${input.instanceFormat.toUpperCase()} ` +
                `but content appears to be ${detectedFormat.toUpperCase()}. ` +
                `This may cause unexpected parsing results.`
            );
        } else if (detectedFormat === input.instanceFormat.toLowerCase()) {
            formatMatchStatus = 'match';
            console.log('[MultiInputPanel] ✅ Format match confirmed');
            console.log('[MultiInputPanel]   Declared and detected formats align');
            console.log('[MultiInputPanel] ────────────────────────────────────────');
        } else {
            formatMatchStatus = 'unknown';
            console.warn('[MultiInputPanel] ⚠️  Could not auto-detect format');
            console.warn('[MultiInputPanel]   Proceeding with declared format: ' + input.instanceFormat.toUpperCase());
            console.log('[MultiInputPanel] ────────────────────────────────────────');
        }

        // Update format detection state
        this.updateFormatDetection(inputId, detectedFormat, formatMatchStatus);

        if (!this.utlxService) {
            console.error('[MultiInputPanel] UTLXService not available for validation!');
            this.updateInputValidation(inputId, false, false, 'UTLXService not available');
            return;
        }

        console.log('[MultiInputPanel] Setting validating state...');
        this.updateInputValidation(inputId, undefined, true);

        try {
            // Build request - only include CSV parameters for CSV format
            const request: any = {
                content: input.instanceContent,
                format: input.instanceFormat
            };

            // Only add CSV parameters if the format is actually CSV
            if (input.instanceFormat.toLowerCase() === 'csv') {
                request.csvHeaders = input.csvHeaders !== undefined ? input.csvHeaders : true;
                request.csvDelimiter = input.csvDelimiter || ',';
            }

            console.log('[MultiInputPanel] Calling utlxService.validateUdm() with:', {
                format: request.format,
                contentLength: request.content.length,
                csvHeaders: request.csvHeaders,
                csvDelimiter: request.csvDelimiter,
                note: input.instanceFormat.toLowerCase() === 'csv' ? 'CSV parameters included' : 'CSV parameters EXCLUDED'
            });

            // Log first few lines of content for debugging
            const firstLines = request.content.split('\n').slice(0, 5);
            console.log('[MultiInputPanel] Content preview (first 5 lines):');
            firstLines.forEach((line: string, idx: number) => {
                console.log(`  Line ${idx + 1}: ${line.substring(0, 100)}`);
            });

            const result = await this.utlxService.validateUdm(request);

            console.log('[MultiInputPanel] ═══════════════════════════════════════');
            if (result.success) {
                console.log('[MultiInputPanel] ✅ Validation result: SUCCESS');
            } else {
                console.error('[MultiInputPanel] ❌ Validation result: FAILED');
            }
            console.log('[MultiInputPanel] Success:', result.success);
            console.log('[MultiInputPanel] Has Diagnostics:', !!result.diagnostics);
            console.log('[MultiInputPanel] Has UDM Language:', !!result.udmLanguage);
            console.log('[MultiInputPanel] UDM Language Length:', result.udmLanguage?.length || 0);

            if (result.error) {
                console.error('[MultiInputPanel] ❌ ERROR:', result.error);
                // Try to parse and display error details
                try {
                    const errorObj = JSON.parse(result.error.replace('HTTP 500: ', ''));
                    if (errorObj.error) {
                        console.error('[MultiInputPanel] Error details:', errorObj.error);
                    }
                } catch (e) {
                    // Not JSON, just show as-is
                }
            }

            if (result.udmLanguage) {
                console.log('[MultiInputPanel] UDM preview (first 500 chars):');
                console.log(result.udmLanguage.substring(0, 500));
                console.log('[MultiInputPanel] UDM preview (last 200 chars):');
                console.log(result.udmLanguage.substring(Math.max(0, result.udmLanguage.length - 200)));
            } else {
                console.warn('[MultiInputPanel] ⚠️ NO UDM LANGUAGE in result!');
                if (!result.success) {
                    console.warn('[MultiInputPanel] This is expected when validation fails');
                }
            }
            console.log('[MultiInputPanel] ═══════════════════════════════════════');

            // NOTE: We used to verify the UDM is parseable by the frontend parser here,
            // but the daemon may generate UDM with metadata/features that the frontend
            // parser doesn't support yet (e.g., JSCH metadata: __schemaType, __version).
            // Since the daemon already validated the input, we trust its result.
            // If the daemon says success=true and returned UDM, it's valid.

            this.updateInputValidation(
                inputId,
                result.success,
                false,
                result.error,
                result.udmLanguage
            );

            // Fire event if UDM was successfully parsed AND is valid
            if (result.success && result.udmLanguage && input) {
                console.log('[MultiInputPanel] Firing UDM updated event');
                this.eventService.fireInputUdmUpdated({
                    inputId: input.id,
                    inputName: input.name,
                    udmLanguage: result.udmLanguage,
                    format: input.instanceFormat
                });
            }

            console.log('[MultiInputPanel] Validation state updated');
            console.log('[MultiInputPanel] ========================================');
        } catch (error) {
            console.error('[MultiInputPanel] Validation exception:', error);
            console.error('[MultiInputPanel] Error stack:', error instanceof Error ? error.stack : 'N/A');
            this.updateInputValidation(
                inputId,
                false,
                false,
                String(error)
            );
            console.log('[MultiInputPanel] ========================================');
        }
    }

    /**
     * Update input validation state
     */
    private updateFormatDetection(
        inputId: string,
        formatDetected: string | null,
        formatMatchStatus: 'match' | 'mismatch' | 'unknown'
    ): void {
        console.log('[MultiInputPanel] updateFormatDetection() called:', {
            inputId,
            formatDetected,
            formatMatchStatus
        });

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === inputId
                    ? { ...input, formatDetected, formatMatchStatus }
                    : input
            )
        });
    }

    private updateInputValidation(
        inputId: string,
        udmParsed?: boolean,
        udmValidating?: boolean,
        udmError?: string,
        udmLanguage?: string
    ): void {
        console.log('[MultiInputPanel] updateInputValidation() called:', {
            inputId,
            udmParsed,
            udmValidating,
            udmError,
            hasUdmLanguage: !!udmLanguage,
            udmLanguageLength: udmLanguage?.length
        });

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === inputId
                    ? { ...input, udmParsed, udmValidating, udmError, udmLanguage }
                    : input
            )
        });
    }

    /**
     * Parse schema content and fire schema field tree event for Message Contract mode.
     * This allows the Function Builder to display schema structure.
     *
     * @param inputId - The input ID to parse schema for
     * @param forceFireWithInstance - If true, fire event even when instance content exists.
     *                                Used for "Infer Schema" to show BOTH instance samples AND schema types.
     */
    private parseAndFireSchemaFieldTree(inputId: string, forceFireWithInstance: boolean = false): void {
        console.log('[MultiInputPanel] parseAndFireSchemaFieldTree called:', {
            inputId,
            forceFireWithInstance,
            mode: this.state.mode
        });

        const input = this.state.inputs.find(i => i.id === inputId);
        if (!input) {
            console.warn('[MultiInputPanel] parseAndFireSchemaFieldTree: Input not found:', inputId);
            return;
        }

        console.log('[MultiInputPanel] parseAndFireSchemaFieldTree: Found input:', {
            name: input.name,
            schemaFormat: input.schemaFormat,
            schemaContentLength: input.schemaContent?.length || 0,
            instanceContentLength: input.instanceContent?.length || 0
        });

        // Only process if:
        // 1. We're in Message Contract mode
        // 2. There's schema content
        if (this.state.mode !== UTLXMode.MESSAGE_CONTRACT) {
            console.log('[MultiInputPanel] parseAndFireSchemaFieldTree: Not in Message Contract mode, skipping');
            return;
        }

        if (!input.schemaContent || input.schemaContent.trim().length === 0) {
            console.log('[MultiInputPanel] parseAndFireSchemaFieldTree: No schema content, skipping');
            return;
        }

        // If instance content exists and we're not forcing, skip (UDM takes priority for structure)
        // But when forceFireWithInstance=true (Infer Schema), we fire to show BOTH samples AND types
        if (!forceFireWithInstance && input.instanceContent && input.instanceContent.trim().length > 0) {
            console.log('[MultiInputPanel] parseAndFireSchemaFieldTree: Instance content exists, UDM takes priority (use forceFireWithInstance=true to override)');
            return;
        }

        console.log('[MultiInputPanel] ════════════════════════════════════════');
        console.log('[MultiInputPanel] 📊 PARSING SCHEMA TO FIELD TREE');
        console.log('[MultiInputPanel] Input:', input.name);
        console.log('[MultiInputPanel] Schema format:', input.schemaFormat);
        console.log('[MultiInputPanel] Schema content length:', input.schemaContent.length);
        console.log('[MultiInputPanel] ════════════════════════════════════════');

        try {
            let fieldTree: SchemaFieldInfo[] = [];

            if (input.schemaFormat === 'jsch') {
                fieldTree = parseJsonSchemaToFieldTree(input.schemaContent);
                console.log('[MultiInputPanel] Parsed JSON Schema, got', fieldTree.length, 'top-level fields');
            } else if (input.schemaFormat === 'xsd') {
                fieldTree = parseXsdToFieldTree(input.schemaContent);
                console.log('[MultiInputPanel] Parsed XSD, got', fieldTree.length, 'top-level fields');
            } else if (input.schemaFormat === 'osch') {
                fieldTree = parseOSchToFieldTree(input.schemaContent);
                console.log('[MultiInputPanel] Parsed EDMX/CSDL, got', fieldTree.length, 'top-level fields');
            } else if (input.schemaFormat === 'tsch') {
                fieldTree = parseTschToFieldTree(input.schemaContent);
                console.log('[MultiInputPanel] Parsed Table Schema, got', fieldTree.length, 'fields');
            } else {
                console.warn('[MultiInputPanel] Schema format not supported for field tree:', input.schemaFormat);
                return;
            }

            if (fieldTree.length > 0) {
                console.log('[MultiInputPanel] Firing schema field tree event');
                this.eventService.fireInputSchemaFieldTree({
                    inputId: input.id,
                    inputName: input.name,
                    fieldTree,
                    schemaFormat: input.schemaFormat as 'jsch' | 'xsd' | 'osch' | 'tsch'
                });
            } else {
                console.warn('[MultiInputPanel] No fields extracted from schema');
            }
        } catch (error) {
            console.error('[MultiInputPanel] Failed to parse schema to field tree:', error);
        }
    }

    private handleCsvHeadersChange(hasHeaders: boolean): void {
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        csvHeaders: hasHeaders
                    }
                    : input
            )
        });

        // Fire event to notify about CSV parameter change
        this.eventService.fireInputFormatChanged({
            format: 'csv',
            isSchema: false,
            inputId: this.state.activeInputId,
            csvHeaders: hasHeaders,
            csvDelimiter: activeInput?.csvDelimiter
        });
    }

    private handleCsvDelimiterChange(delimiter: string): void {
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        csvDelimiter: delimiter
                    }
                    : input
            )
        });

        // Fire event to notify about CSV parameter change
        this.eventService.fireInputFormatChanged({
            format: 'csv',
            isSchema: false,
            inputId: this.state.activeInputId,
            csvHeaders: activeInput?.csvHeaders,
            csvDelimiter: delimiter
        });
    }

    // ENCODING/BOM HANDLERS COMMENTED OUT - These are for outputs, not inputs (inputs auto-detect encoding/BOM)
    // Keeping code here in case we want to use it for output panel later
    /*
    private handleEncodingChange(encoding: string): void {
        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        encoding: encoding,
                        // Reset BOM if changing to encoding that doesn't support it
                        bom: this.isBomSupported(encoding) ? input.bom : false
                    }
                    : input
            )
        });
    }

    private handleBomChange(hasBom: boolean): void {
        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        bom: hasBom
                    }
                    : input
            )
        });
    }

    private isBomSupported(encoding?: string): boolean {
        if (!encoding) return false;
        // BOM is only meaningful for UTF-8 and UTF-16 variants
        return ['UTF-8', 'UTF-16LE', 'UTF-16BE'].includes(encoding);
    }
    */

    /**
     * Infer input schema from instance data
     * Uses the schema inferrer to generate a JSON Schema from the instance content
     */
    private handleInferInputSchema(): void {
        const activeInput = this.state.inputs.find(input => input.id === this.state.activeInputId);
        if (!activeInput) {
            this.messageService.warn('No active input');
            return;
        }

        if (!activeInput.instanceContent || activeInput.instanceContent.trim().length === 0) {
            this.messageService.warn('No instance content to infer schema from');
            return;
        }

        try {
            let schemaString: string;
            let schemaFormat: SchemaFormatType;

            if (activeInput.instanceFormat === 'json' || activeInput.instanceFormat === 'yaml') {
                // Infer JSON Schema from JSON/YAML
                const schema = inferSchemaFromJson(activeInput.instanceContent);
                schemaString = formatSchema(schema);
                schemaFormat = 'jsch';
            } else if (activeInput.instanceFormat === 'xml') {
                // Infer XSD from XML
                schemaString = inferSchemaFromXml(activeInput.instanceContent);
                schemaFormat = 'xsd';
            } else if (activeInput.instanceFormat === 'csv') {
                // Infer Table Schema from CSV
                schemaString = inferTableSchemaFromCsv(activeInput.instanceContent);
                schemaFormat = 'tsch';
            } else if (activeInput.instanceFormat === 'odata') {
                // Infer EDMX/CSDL from OData JSON
                schemaString = inferEdmxFromOData(activeInput.instanceContent);
                schemaFormat = 'osch';
            } else {
                this.messageService.warn(`Schema inference not supported for format: ${activeInput.instanceFormat}`);
                return;
            }

            // Update the schema content for this input
            const inputId = this.state.activeInputId;
            this.setState({
                inputs: this.state.inputs.map(input =>
                    input.id === this.state.activeInputId
                        ? {
                            ...input,
                            schemaContent: schemaString,
                            schemaFormat: schemaFormat
                        }
                        : input
                ),
                // Remount the content editor to show the inferred schema.
                contentVersion: (this.state.contentVersion ?? 0) + 1
            });

            this.messageService.info(`${schemaFormat.toUpperCase()} schema inferred from instance data`);

            // Fire schema field tree event AFTER state update
            // Use requestAnimationFrame to ensure React has processed setState
            // This allows Function Builder to show BOTH instance samples AND schema types
            requestAnimationFrame(() => {
                console.log('[MultiInputPanel] Infer Schema: Firing schema field tree after state update');
                this.parseAndFireSchemaFieldTree(inputId, true);
            });
        } catch (error) {
            console.error('[MultiInputPanel] Schema inference error:', error);
            this.messageService.error(`Failed to infer schema: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Compute a default name to restore on Clear, GUARANTEED unique among the other
     * tabs. Prefers the id-derived name ('input-1' -> 'input', 'input-N' -> 'input{N}')
     * when that is free; otherwise picks the lowest free 'input' / 'input{n}'.
     *
     * The uniqueness scan matters because not every id is the canonical 'input-N'
     * form (header-sync uses 'input-<timestamp>-<i>'), and tabs can be renamed or
     * deleted — so the id-derived name alone could collide with a tab that already
     * holds it. This never hands out a name another tab currently uses.
     */
    private defaultNameForInput(id: string): string {
        const taken = new Set(
            this.state.inputs.filter(i => i.id !== id).map(i => i.name.toLowerCase())
        );
        // Preferred name from a canonical 'input-N' id (else just lowest free).
        const m = id.match(/^input-(\d+)$/);
        const preferred = m ? (parseInt(m[1], 10) === 1 ? 'input' : `input${m[1]}`) : undefined;
        return this.freeDefaultName(taken, preferred);
    }

    /**
     * Lowest-free 'input' / 'input{n}' given the set of already-taken (lowercased)
     * names, preferring `preferred` when it is free. Shared by add and clear so a
     * manually-renamed tab (e.g. "input5") can never be duplicated by either path.
     */
    private freeDefaultName(taken: Set<string>, preferred?: string): string {
        if (preferred && !taken.has(preferred.toLowerCase())) {
            return preferred;
        }
        if (!taken.has('input')) {
            return 'input';
        }
        for (let n = 2; ; n++) {
            const candidate = `input${n}`;
            if (!taken.has(candidate.toLowerCase())) {
                return candidate;
            }
        }
    }

    private handleClear(): void {
        const isSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);

        // Clear also restores the input's default name (e.g. a load-derived "test"
        // reverts to "input"). defaultNameForInput guarantees a name no other tab
        // currently uses, so no separate dup-check is needed here.
        let renameTo: string | undefined;
        if (activeInput) {
            const def = this.defaultNameForInput(activeInput.id);
            if (activeInput.name !== def) {
                renameTo = def;
            }
        }

        this.setState({
            inputs: this.state.inputs.map(input =>
                input.id === this.state.activeInputId
                    ? {
                        ...input,
                        [isSchema ? 'schemaContent' : 'instanceContent']: '',
                        // Also clear UDM when clearing instance content
                        ...(isSchema ? {} : { udmLanguage: undefined, udmParsed: false, udmError: undefined }),
                        // Restore the default name on clear
                        ...(renameTo ? { name: renameTo } : {})
                    }
                    : input
            ),
            // Remount the content editor to show the cleared (empty) content.
            contentVersion: (this.state.contentVersion ?? 0) + 1,
            // Remount the name field so the restored default is visible
            ...(renameTo ? { inputNameVersion: (this.state.inputNameVersion ?? 0) + 1 } : {})
        });

        // Fire events to notify editor widget
        if (activeInput) {
            if (isSchema) {
                // Clear schema - fire event to clear schema field tree
                console.log('[MultiInputPanel] Clear: Firing schema content cleared for:', activeInput.name);
                this.eventService.fireInputSchemaContentChanged({
                    inputId: this.state.activeInputId,
                    content: ''
                });
            } else {
                // Clear instance - fire event with empty UDM to clear the field tree
                console.log('[MultiInputPanel] Clear: Firing UDM cleared for:', activeInput.name);
                this.eventService.fireInputUdmUpdated({
                    inputId: this.state.activeInputId,
                    inputName: activeInput.name,
                    udmLanguage: '',  // Empty UDM signals clear
                    format: activeInput.instanceFormat || 'json'
                });

                // Also fire instance content changed
                this.eventService.fireInputInstanceContentChanged({
                    inputId: this.state.activeInputId,
                    content: ''
                });

                // If schema exists and no instance, fire schema field tree event
                if (activeInput.schemaContent && activeInput.schemaContent.trim().length > 0) {
                    console.log('[MultiInputPanel] Clear: Instance cleared, triggering schema field tree parse');
                    // Delay to ensure state is updated
                    setTimeout(() => this.parseAndFireSchemaFieldTree(this.state.activeInputId), 100);
                }
            }
        }

        // Restore the default name LAST: clear events above used the still-current
        // name; now rename (updates header + renames body refs, e.g. "test" -> "input").
        if (renameTo && activeInput) {
            this.eventService.fireInputNameChanged({
                inputId: activeInput.id,
                oldName: activeInput.name,
                newName: renameTo
            });
        }
    }

    /**
     * IF10: the "Input summary" text for an input. For a SCHEMA-format input (jsch/
     * xsd/osch/tsch) the structure is the declared contract → build from the schema
     * field tree; otherwise build from the instance UDM. (A loaded JSCH has no instance
     * UDM to walk, which is why it showed "no structure" before.)
     */
    private abstractForActiveInput(input: InputTab): string | undefined {
        return buildAbstractForInput(input);  // schema-aware; shared with the AI dialog (IF10)
    }

    private handleViewUdm(): void {
        this.udmDialogPosition = { x: 0, y: 0 }; // Reset position when opening
        this.udmViewMode = 'pretty'; // Default to pretty view
        this.setState({ udmDialogOpen: true });
    }

    private formatUdm(content: string): string {
        if (!content || !content.trim()) {
            return content;
        }

        try {
            return this.prettyPrintUdm(content);
        } catch (e) {
            // If pretty printing fails, return original
            console.error('[MultiInputPanel] UDM pretty print error:', e);
            return content;
        }
    }

    /**
     * Pretty print UDM format with proper indentation
     * Based on UDM Language Spec v1.0
     */
    private prettyPrintUdm(udm: string): string {
        const lines: string[] = [];
        let indent = 0;
        const tab = '  '; // 2 spaces per indent level

        // Split into tokens while preserving structure
        let i = 0;
        let currentLine = '';

        while (i < udm.length) {
            const char = udm[i];

            // Handle opening braces/brackets
            if (char === '{' || char === '[') {
                // Flush current line if it has content
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                // Add opening brace
                lines.push(tab.repeat(indent) + char);
                indent++;
                i++;
                // Skip whitespace after opening
                while (i < udm.length && /\s/.test(udm[i])) i++;
                continue;
            }

            // Handle closing braces/brackets
            if (char === '}' || char === ']') {
                // Flush current line
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                indent = Math.max(0, indent - 1);
                lines.push(tab.repeat(indent) + char);
                i++;
                // Handle trailing comma
                while (i < udm.length && /[\s,]/.test(udm[i])) i++;
                continue;
            }

            // Handle commas
            if (char === ',') {
                currentLine += char;
                lines.push(tab.repeat(indent) + currentLine.trim());
                currentLine = '';
                i++;
                // Skip whitespace after comma
                while (i < udm.length && /\s/.test(udm[i])) i++;
                continue;
            }

            // Handle newlines in original
            if (char === '\n') {
                if (currentLine.trim()) {
                    lines.push(tab.repeat(indent) + currentLine.trim());
                    currentLine = '';
                }
                i++;
                continue;
            }

            // Regular character
            currentLine += char;
            i++;
        }

        // Flush any remaining content
        if (currentLine.trim()) {
            lines.push(tab.repeat(indent) + currentLine.trim());
        }

        return lines.join('\n');
    }

    private handleCloseUdmDialog(): void {
        this.isDraggingDialog = false;
        this.setState({ udmDialogOpen: false });
    }

    private handleDragStart(e: React.MouseEvent): void {
        // Don't start drag if clicking on the close button
        if ((e.target as HTMLElement).closest('.utlx-udm-dialog-close')) {
            return;
        }

        this.isDraggingDialog = true;
        const startX = e.clientX;
        const startY = e.clientY;
        const startPosX = this.udmDialogPosition.x;
        const startPosY = this.udmDialogPosition.y;

        // Add document-level mouse move and mouse up handlers
        const handleMouseMove = (moveEvent: MouseEvent) => {
            if (this.isDraggingDialog) {
                this.udmDialogPosition = {
                    x: startPosX + (moveEvent.clientX - startX),
                    y: startPosY + (moveEvent.clientY - startY)
                };
                this.update();
            }
        };

        const handleMouseUp = () => {
            this.isDraggingDialog = false;
            this.update();
            document.removeEventListener('mousemove', handleMouseMove);
            document.removeEventListener('mouseup', handleMouseUp);
        };

        document.addEventListener('mousemove', handleMouseMove);
        document.addEventListener('mouseup', handleMouseUp);

        e.preventDefault();
    }

    private async handleCopyUdmToClipboard(): Promise<void> {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.udmLanguage) {
            this.messageService.error('No UDM data to copy');
            return;
        }

        try {
            // Get the content based on current view mode (raw or pretty)
            const content = this.udmViewMode === 'pretty'
                ? this.formatUdm(activeInput.udmLanguage)
                : activeInput.udmLanguage;

            // Copy to clipboard
            await navigator.clipboard.writeText(content);
            this.messageService.info('UDM copied to clipboard');
        } catch (error) {
            console.error('[MultiInputPanel] Failed to copy UDM to clipboard:', error);
            this.messageService.error('Failed to copy UDM to clipboard');
        }
    }

    private async handleSaveUdm(): Promise<void> {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.udmLanguage) {
            this.messageService.error('No UDM data to save');
            return;
        }

        try {
            // Show save dialog
            const defaultFileName = `${activeInput.name}.udm`;
            const folder = await this.resolveDialogFolder();
            const saveUri = await this.fileDialogService.showSaveDialog({
                title: 'Save UDM File',
                saveLabel: 'Save',
                filters: { 'UDM Files': ['udm'], 'All Files': ['*'] },
                inputValue: defaultFileName
            }, folder);

            if (!saveUri) {
                // User cancelled
                return;
            }

            // Write file content
            const content = activeInput.udmLanguage;
            await this.fileService.write(
                saveUri,
                content
            );
            this.eventService.setLastUsedDirectoryUri(saveUri.parent.toString());

            this.messageService.info(`UDM saved to ${saveUri.path.base}`);
        } catch (error) {
            this.messageService.error(`Failed to save UDM file: ${error}`);
            console.error('[MultiInputPanel] Error saving UDM:', error);
        }
    }

    private async handleLoadFile(): Promise<void> {
        try {
            // Determine if we're loading into schema tab in design mode
            const isSchemaTab = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';
            const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);

            // Create file input element
            const input = document.createElement('input');
            input.type = 'file';

            // Set file filter based on context
            if (isSchemaTab && activeInput) {
                // In schema tab, filter by expected schema format based on instance format
                const schemaExtensions = this.getSchemaFileExtensions(activeInput.schemaFormat);
                input.accept = schemaExtensions;
            } else {
                // In instance tab or runtime mode, allow all data formats
                input.accept = '.csv,.json,.xml,.yaml,.yml,.xsd,.avsc,.proto,text/*';
            }

            // Handle file selection
            input.onchange = async (e: Event) => {
                const target = e.target as HTMLInputElement;
                const file = target.files?.[0];
                if (!file) return;

                this.setState({ loading: true });

                try {
                    // Read file content
                    const content = await file.text();

                    // Determine if we're loading into instance or schema
                    const isSchema = this.state.mode === UTLXMode.MESSAGE_CONTRACT && this.state.activeSubTab === 'schema';

                    // Auto-detect format: start with file extension, then refine with content analysis
                    let detectedFormat = this.detectFormatFromFilename(file.name);

                    // Content-based detection can refine generic filename extensions
                    // e.g., OData JSON files have .json extension but content reveals @odata.* markers
                    // However, compound extensions (.tsch.json, .schema.json) are already specific
                    // and should NOT be overridden by content detection
                    const filenameIsSpecific = detectedFormat && detectedFormat !== 'json' && detectedFormat !== 'xml';
                    if (!filenameIsSpecific) {
                        const contentDetectedFormat = this.detectContentFormat(content);
                        if (contentDetectedFormat) {
                            detectedFormat = contentDetectedFormat as InstanceFormat | SchemaFormatType;
                        }
                    }

                    // Special handling for schema tab: .json files are JSON Schema (jsch)
                    if (isSchema && detectedFormat === 'json') {
                        detectedFormat = 'jsch';
                    }

                    console.log('[MultiInputPanel] ════════════════════════════════════════');
                    console.log('[MultiInputPanel] FILE LOAD - Format detection');
                    console.log('[MultiInputPanel] File:', file.name);
                    console.log('[MultiInputPanel] isSchema:', isSchema);
                    console.log('[MultiInputPanel] Format from filename:', this.detectFormatFromFilename(file.name) || 'none');
                    console.log('[MultiInputPanel] Format from content:', filenameIsSpecific ? '(skipped - filename specific)' : 'applied');
                    console.log('[MultiInputPanel] Final detected format:', detectedFormat || 'none');
                    console.log('[MultiInputPanel] Current format in state:', this.state.inputs.find(i => i.id === this.state.activeInputId)?.instanceFormat);
                    console.log('[MultiInputPanel] ════════════════════════════════════════');

                    // Get current input for event firing
                    const currentInput = this.state.inputs.find(i => i.id === this.state.activeInputId);

                    // Auto-name the input from the loaded filename — but ONLY when it
                    // still has a default name (input, input2, …). A manually chosen
                    // name is never clobbered. Instance load only; schema load leaves
                    // the name alone. "007-test.json" -> "test" (see toHeaderIdentifier).
                    let renameTo: string | undefined;
                    if (!isSchema && currentInput && /^input\d*$/i.test(currentInput.name)) {
                        const stem = file.name.replace(/\.[^./\\]+$/, ''); // drop the last extension
                        const derived = toHeaderIdentifier(stem);
                        const duplicate = this.state.inputs.some(
                            i => i.id !== currentInput.id && i.name.toLowerCase() === derived.toLowerCase()
                        );
                        if (derived !== '' && !duplicate && derived !== currentInput.name) {
                            renameTo = derived;
                        }
                    }

                    // Update content and optionally format. The instance and schema
                    // slots are INDEPENDENT in MC mode: loading one must NOT wipe the
                    // other, so a sample instance and the contract schema can coexist.
                    this.setState({
                        inputs: this.state.inputs.map(input =>
                            input.id === this.state.activeInputId
                                ? {
                                    ...input,
                                    [isSchema ? 'schemaContent' : 'instanceContent']: content,
                                    ...(detectedFormat && !isSchema ? { instanceFormat: detectedFormat } : {}),
                                    ...(detectedFormat && isSchema ? { schemaFormat: detectedFormat as SchemaFormatType } : {}),
                                    // Auto-name from the filename when the name was still a default
                                    ...(renameTo ? { name: renameTo } : {})
                                }
                                : input
                        ),
                        loading: false,
                        // Remount the content editor (and name field) to show loaded content.
                        contentVersion: (this.state.contentVersion ?? 0) + 1,
                        // Remount the name field so the auto-rename is visible.
                        ...(renameTo ? { inputNameVersion: (this.state.inputNameVersion ?? 0) + 1 } : {})
                    });

                    this.messageService.info(`Loaded file: ${file.name}${detectedFormat ? ` (format: ${detectedFormat})` : ''}`);

                    // Notify dependent panels/editor of the auto-rename (updates the
                    // header and renames body references), same as a manual rename.
                    if (renameTo && currentInput) {
                        this.eventService.fireInputNameChanged({
                            inputId: currentInput.id,
                            oldName: currentInput.name,
                            newName: renameTo
                        });
                    }

                    // Fire format changed event if format was auto-detected
                    if (detectedFormat && !isSchema) {
                        this.eventService.fireInputFormatChanged({
                            format: detectedFormat,
                            inputId: this.state.activeInputId,
                            isSchema: false
                        });
                    }

                    // Fire content changed event. Instance and schema are independent —
                    // loading one does NOT clear the other (so both can be present).
                    if (isSchema) {
                        this.eventService.fireInputSchemaContentChanged({
                            inputId: this.state.activeInputId,
                            content
                        });

                        // Parse schema to field tree for Function Builder (Message Contract mode)
                        // Wait for state to update before parsing
                        await new Promise(resolve => setTimeout(resolve, 50));
                        this.parseAndFireSchemaFieldTree(this.state.activeInputId);
                    } else {
                        this.eventService.fireInputInstanceContentChanged({
                            inputId: this.state.activeInputId,
                            content
                        });

                        // Wait for state to update before validation
                        await new Promise(resolve => setTimeout(resolve, 50));

                        console.log('[MultiInputPanel] ════════════════════════════════════════');
                        console.log('[MultiInputPanel] FILE LOAD - About to validate');
                        console.log('[MultiInputPanel] Format in state NOW:', this.state.inputs.find(i => i.id === this.state.activeInputId)?.instanceFormat);
                        console.log('[MultiInputPanel] ════════════════════════════════════════');

                        // Trigger UDM parsing for instance content (AFTER state update completes)
                        await this.validateInput(this.state.activeInputId);
                    }
                } catch (error) {
                    this.setState({ loading: false });
                    this.messageService.error(`Failed to load file: ${error}`);
                }
            };

            // Trigger file dialog
            input.click();
        } catch (error) {
            this.messageService.error(`Failed to open file dialog: ${error}`);
        }
    }

    /**
     * Resolve the initial folder for file dialogs.
     * Uses the last-used directory (shared across panels), falling back to the examples directory.
     */
    private async resolveDialogFolder(): Promise<import('@theia/filesystem/lib/common/files').FileStat | undefined> {
        try {
            const lastUri = this.eventService.lastUsedDirectoryUri;
            if (lastUri) {
                return await this.fileService.resolve(new URI(lastUri));
            }
            const examplesUri = new URI('file:///').resolve('Users/magr/data/mapping/github-git/utl-x/examples');
            return await this.fileService.resolve(examplesUri);
        } catch {
            return undefined;
        }
    }

    /**
     * Get file extensions filter for schema format
     * Used when loading schema files in Design Mode
     */
    private getSchemaFileExtensions(schemaFormat: SchemaFormatType): string {
        switch (schemaFormat) {
            case 'xsd':
                return '.xsd';
            case 'jsch':
                return '.json,.schema.json';
            case 'avro':
                return '.avsc,.avro,.json';
            case 'proto':
                return '.proto';
            case 'osch':
                return '.edmx,.xml';
            case 'tsch':
                return '.tsch.json,.json';
            default:
                return '.xsd,.json,.avsc,.proto,.edmx';
        }
    }

    /**
     * Auto-detect format from file extension
     * Returns null if format cannot be detected
     */
    private detectFormatFromFilename(filename: string): InstanceFormat | SchemaFormatType | null {
        const lowerFilename = filename.toLowerCase();

        // Check compound extensions first (before .pop() loses them)
        if (lowerFilename.endsWith('.tsch.json')) return 'tsch';
        if (lowerFilename.endsWith('.schema.json')) return 'jsch';

        const ext = lowerFilename.split('.').pop();

        switch (ext) {
            case 'csv':
                return 'csv';
            case 'json':
                return 'json';
            case 'xml':
                return 'xml';
            case 'yaml':
            case 'yml':
                return 'yaml';
            case 'xsd':
                return 'xsd';
            case 'avsc':
            case 'avro':
                return 'avro';
            case 'proto':
                return 'proto';
            case 'edmx':
                return 'osch';
            case 'tsch':
                return 'tsch';
            case 'jsonschema':
            case 'schema':
                return 'jsch';
            default:
                return null; // Unknown extension, don't change format
        }
    }

    private setState(partial: Partial<MultiInputPanelState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
        this.schedulePersist();
    }

    /** IF09: debounced persist of the per-tab content snapshot. */
    private schedulePersist(): void {
        if (this.persistTimer) {
            window.clearTimeout(this.persistTimer);
        }
        this.persistTimer = window.setTimeout(() => this.persistContent(), 400);
    }

    private persistContent(): void {
        const snapshot: InputContentSnapshot = {};
        for (const input of this.state.inputs) {
            const inst = capContent(input.instanceContent);
            const sch = capContent(input.schemaContent);
            const udm = capContent(input.udmLanguage);
            if (inst.dropped) {
                console.warn(`[input-panel] sample for "${input.name}" exceeds cap — not persisted (reload the file after refresh)`);
            }
            snapshot[input.name] = {
                instanceContent: inst.content,
                instanceDropped: inst.dropped,
                schemaContent: sch.content,
                schemaDropped: sch.dropped,
                udmLanguage: udm.content,
            };
        }
        saveSession<InputContentSnapshot>(SESSION_KEYS.inputPanel, snapshot);
    }

    /**
     * IF09: fill an input's content from the one-shot restore snapshot, by name,
     * only when the input is currently empty (don't clobber live content). Marks
     * udmParsed so the restored UDM drives the field tree / status icon.
     */
    private applyPendingContent(input: InputTab): InputTab {
        const saved = this.pendingRestore?.[input.name];
        if (!saved) {
            return input;
        }
        const hasInstance = !!(input.instanceContent && input.instanceContent.length > 0);
        const hasSchema = !!(input.schemaContent && input.schemaContent.length > 0);
        return {
            ...input,
            instanceContent: !hasInstance && saved.instanceContent ? saved.instanceContent : input.instanceContent,
            schemaContent: !hasSchema && saved.schemaContent ? saved.schemaContent : input.schemaContent,
            udmLanguage: input.udmLanguage || saved.udmLanguage,
            udmParsed: input.udmLanguage || saved.udmLanguage ? true : input.udmParsed,
        };
    }

    /**
     * IF03 Phase 2: load a transformation's input samples (instance content keyed by
     * input name). Call this BEFORE the editor loads the .utlx — syncFromHeaders (driven
     * by the header parse) then OVERWRITES each matching tab's content as it rebuilds the
     * tabs. Also applied immediately to any already-matching tab (covers re-opening the
     * same transformation, where no resync fires). Cleared one-shot.
     */
    public loadBundleSamples(byName: { [name: string]: string }): void {
        this.pendingBundleSamples = byName;
        this.applyBundleSamplesToCurrent();
        // Backstop: drop the pending samples if no resync consumes them shortly, so a
        // later manual header edit can't re-inject a previous transformation's data.
        window.setTimeout(() => { this.pendingBundleSamples = undefined; }, 5000);
    }

    /** Overwrite current tabs' instance content from pendingBundleSamples (by name). */
    private applyBundleSamplesToCurrent(): void {
        const byName = this.pendingBundleSamples;
        if (!byName) {
            return;
        }
        let changed = false;
        const inputs = this.state.inputs.map(input => {
            if (Object.prototype.hasOwnProperty.call(byName, input.name)) {
                changed = true;
                return {
                    ...input,
                    instanceContent: byName[input.name],
                    // Force UDM re-parse for the new content.
                    udmLanguage: undefined,
                    udmParsed: undefined,
                    udmError: undefined,
                };
            }
            return input;
        });
        if (!changed) {
            return;
        }
        this.setState({ inputs, contentVersion: (this.state.contentVersion ?? 0) + 1 });
        // Re-validate (UDM) each filled input + notify dependents.
        for (const input of this.state.inputs) {
            if (Object.prototype.hasOwnProperty.call(byName, input.name) && input.instanceContent.trim()) {
                this.eventService.fireInputInstanceContentChanged({ inputId: input.id, content: input.instanceContent });
                this.validateInput(input.id);
            }
        }
    }

    /** IF09: after a restore, re-fire UDM so the editor/canvas field trees rebuild. */
    private refireRestoredUdm(): void {
        this.state.inputs.forEach(input => {
            if (input.udmLanguage) {
                this.eventService.fireInputUdmUpdated({
                    inputId: input.id,
                    inputName: input.name,
                    udmLanguage: input.udmLanguage,
                    format: input.instanceFormat
                });
            }
        });
    }

    /**
     * Get all input documents (for runtime mode)
     * PUBLIC: Called by frontend contribution for execution
     * Note: encoding and BOM are auto-detected for inputs, so we use defaults here
     */
    public getInputDocuments(): InputDocument[] {
        return this.state.inputs
            .filter(input => input.instanceContent)
            .map(input => ({
                id: input.id,
                name: input.name,
                content: input.instanceContent,
                format: this.mapInstanceFormatToDataFormat(input.instanceFormat),
                encoding: 'UTF-8',  // Default, will be auto-detected by backend
                bom: false           // Default, will be auto-detected by backend
            }));
    }

    /**
     * Get all input tabs (for header generation)
     * PUBLIC: Called by frontend contribution for UTLX header generation
     */
    public getAllInputTabs(): Array<{id: string; name: string; format: string; csvHeaders?: boolean; csvDelimiter?: string}> {
        return this.state.inputs.map(input => ({
            id: input.id,
            name: input.name,
            format: input.instanceFormat,
            csvHeaders: input.csvHeaders,
            csvDelimiter: input.csvDelimiter
        }));
    }

    /**
     * Map custom instance format to protocol DataFormat
     */
    private mapInstanceFormatToDataFormat(format: InstanceFormat | SchemaFormatType): DataFormat {
        const formatMap: { [key: string]: DataFormat } = {
            'csv': 'csv',
            'json': 'json',
            'odata': 'odata',
            'osch': 'osch',
            'xml': 'xml',
            'yaml': 'yaml',
            'xsd': 'xsd',
            'jsch': 'jsch',
            'avro': 'avro',
            'proto': 'proto'
        };
        return formatMap[format] || 'json';
    }

    /**
     * Get schema document for active input (for design-time mode)
     */
    getSchemaDocument(): SchemaDocument | null {
        const activeInput = this.state.inputs.find(i => i.id === this.state.activeInputId);
        if (!activeInput || !activeInput.schemaContent) {
            return null;
        }

        return {
            format: this.mapSchemaFormatToProtocol(activeInput.schemaFormat),
            content: activeInput.schemaContent
        };
    }

    /**
     * Map custom schema format to protocol SchemaFormat
     */
    private mapSchemaFormatToProtocol(format: SchemaFormatType): SchemaFormat {
        const formatMap: { [key: string]: SchemaFormat } = {
            'xsd': 'xsd',
            'jsch': 'jsch',
            'avro': 'avro',
            'proto': 'proto',
            'osch': 'osch'
        };
        return formatMap[format] || 'jsch';
    }

    /**
     * Set mode from external source
     */
    setMode(mode: UTLXMode): void {
        this.setState({
            mode,
            activeSubTab: mode === UTLXMode.MESSAGE_CONTRACT ? 'instance' : 'instance'
        });
    }

    /**
     * Sync input tabs from parsed UTLX headers (for copy/paste support)
     *
     * This completely syncs the panel to match the UTLX header structure:
     * - UTLX defines the order and structure (source of truth)
     * - Content is preserved where names match
     * - Tabs are reordered to match UTLX order
     * - Extra tabs not in UTLX are removed
     * - Missing tabs in UTLX are created empty
     */
    public syncFromHeaders(parsedInputs: Array<{
        name: string;
        format: string;
        csvHeaders?: boolean;
        csvDelimiter?: string;
        xmlArrays?: string[];
    }>): void {
        console.log('[MultiInputPanelWidget] Syncing from headers:', parsedInputs);
        console.log('[MultiInputPanelWidget] Current inputs before sync:', this.state.inputs.map(i => ({name: i.name, format: i.instanceFormat})));

        const currentInputs = this.state.inputs;

        // Validate for duplicate names in parsed inputs
        const nameSet = new Set<string>();
        const duplicates = new Set<string>();
        parsedInputs.forEach(input => {
            const lowerName = input.name.toLowerCase();
            if (nameSet.has(lowerName)) {
                duplicates.add(input.name);
            } else {
                nameSet.add(lowerName);
            }
        });

        if (duplicates.size > 0) {
            const duplicateList = Array.from(duplicates).join(', ');
            this.messageService.warn(`Duplicate input names detected in UTLX header: ${duplicateList}. Please ensure all input names are unique.`);
            console.warn('[MultiInputPanelWidget] Duplicate input names in parsed headers:', duplicates);
            // Continue anyway, but user has been warned
        }

        // Build new input tabs based on parsed headers
        // The order comes from parsedInputs (UTLX is source of truth)
        const newInputs: InputTab[] = parsedInputs.map((parsedInput, index) => {
            // Try to find existing tab with matching name to preserve content
            let existingInput = currentInputs.find(input => input.name === parsedInput.name);

            // Special case: single input on both sides with different names
            // Treat them as the same input (user renamed or pasted different UTLX)
            if (!existingInput && parsedInputs.length === 1 && currentInputs.length === 1) {
                existingInput = currentInputs[0];
                console.log(`[MultiInputPanelWidget] Single input case: renaming "${existingInput.name}" to "${parsedInput.name}"`);
            }

            if (existingInput) {
                // Found matching name - reuse it and update format/options.
                // applyPendingContent (IF09) fills content on the initial restore if
                // this tab is still empty; a no-op once the restore window has passed.
                console.log(`[MultiInputPanelWidget] Reusing existing input "${parsedInput.name}" (preserving content)`);
                return this.applyPendingContent({
                    ...existingInput,
                    name: parsedInput.name, // Update to new name from UTLX
                    instanceFormat: parsedInput.format as InstanceFormat,
                    csvHeaders: parsedInput.csvHeaders,
                    csvDelimiter: parsedInput.csvDelimiter
                });
            } else {
                // No matching name - create new input (IF09: filled from the restore
                // snapshot by name when this is the initial restore, else empty).
                console.log(`[MultiInputPanelWidget] Creating new input "${parsedInput.name}"`);
                const newId = `input-${Date.now()}-${index}`;
                const schemaFormat: SchemaFormatType =
                    (parsedInput.format === 'xsd' || parsedInput.format === 'jsch' ||
                     parsedInput.format === 'avro' || parsedInput.format === 'proto')
                    ? parsedInput.format as SchemaFormatType
                    : 'jsch';
                return this.applyPendingContent({
                    id: newId,
                    name: parsedInput.name,
                    instanceContent: '',
                    instanceFormat: parsedInput.format as InstanceFormat,
                    schemaContent: '',
                    schemaFormat: schemaFormat,
                    csvHeaders: parsedInput.csvHeaders,
                    csvDelimiter: parsedInput.csvDelimiter
                });
            }
        });

        // Determine which input should be active
        // Priority: 1) Previously active input if it still exists (by name)
        //          2) First input
        let newActiveInputId = '';
        if (newInputs.length > 0) {
            const currentActive = currentInputs.find(input => input.id === this.state.activeInputId);
            if (currentActive) {
                // Find the new input with the same name as the previously active one
                const matchingNew = newInputs.find(input => input.name === currentActive.name);
                newActiveInputId = matchingNew ? matchingNew.id : newInputs[0].id;
            } else {
                newActiveInputId = newInputs[0].id;
            }
        }

        // Update state - this completely replaces the inputs array
        this.setState({
            inputs: newInputs,
            activeInputId: newActiveInputId,
            // Remount the content editor — the active input may have changed content.
            contentVersion: (this.state.contentVersion ?? 0) + 1
        });

        // IF09: the restore is one-shot — once syncFromHeaders has rebuilt the tabs
        // from the restored header and filled their content, consume the snapshot so
        // later manual header edits don't re-inject stale data. Re-fire UDM so the
        // restored field trees rebuild.
        if (this.pendingRestore) {
            this.pendingRestore = undefined;
            window.setTimeout(() => this.refireRestoredUdm(), 100);
        }

        // IF03 Phase 2: now that the tabs match the new transformation's header, overwrite
        // their content with the bundle samples (by name) and consume them (one-shot).
        if (this.pendingBundleSamples) {
            this.applyBundleSamplesToCurrent();
            this.pendingBundleSamples = undefined;
        }

        console.log('[MultiInputPanelWidget] Sync complete:', newInputs.map(i => ({name: i.name, format: i.instanceFormat})));
    }
}
