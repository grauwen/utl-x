/**
 * UTLX Toolbar Widget
 *
 * Top toolbar for:
 * - Mode switching (Execution ↔ Message Contract)
 * - Current mode indicator
 * - MCP prompt for AI assistance
 */

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import { ApplicationShell } from '@theia/core/lib/browser';
import {
    UTLXMode,
    UTLXService,
    UTLX_SERVICE_SYMBOL,
    GenerateUtlxRequest,
    AiActivityEntry,
    GenerateUtlxAttempt,
    CoverageMappingHint
} from '../../common/protocol';
import { UTLXEventService } from '../events/utlx-event-service';
import { buildAbstractForInput } from '../utils/input-abstract';
import { MultiInputPanelWidget } from '../input-panel/multi-input-panel-widget';
import { OutputPanelWidget } from '../output-panel/output-panel-widget';
import { UTLXEditorWidget } from '../editor/utlx-editor-widget';
import {
    CoverageReport, buildContractCoverage, parseSchemaToFields,
    flattenSchemaLeaves, mergeCoverageSuggestions,
} from '../utils/coverage';
import { extractInputPaths, formatPathsAsSimpleList } from '../utils/udm-path-extractor';
import { SchemaComparisonResult } from '../utils/schema-comparator';
import { ValidationResultDialog } from './validation-result-dialog';

export interface PromptHistoryEntry {
    timestamp: string;
    prompt: string;
    result?: string;  // The generated UTLX code
    // IDE mode this prompt was created in (IF08). Absent on legacy entries —
    // treated as EXECUTION (the only mode AI assist worked in before IF08).
    mode?: UTLXMode;
}

export interface ToolbarState {
    currentMode: UTLXMode;
    showMCPDialog: boolean;
    mcpPrompt: string;
    mcpLoading: boolean;
    mcpProgressMessage: string;
    mcpProgressPercent: number;  // 0-100
    // AI activity monitor: the live step-by-step log polled from the backend.
    mcpActivity: AiActivityEntry[];
    // Result quality (for the preview warning) + per-attempt detail.
    mcpResultValid?: boolean;       // false => show a red "may not run" warning
    mcpResultStatus?: string;       // short status line for the warning banner
    mcpAttempts: GenerateUtlxAttempt[];
    // IF10: per-input semantic abstract list shown above the prompt (snapshotted on open).
    dialogInputs: Array<{ name: string; format: string; abstract?: string }>;
    expandedInputs: string[];       // input names whose abstract is expanded
    inputGloss: Record<string, string>;  // IF10 v2: opt-in LLM "what is this" per input
    inputGlossLoading: string[];          // input names whose gloss is being fetched
    // IF11: MC-mode coverage analysis (output contract vs input schemas). Snapshotted on open.
    coverage?: CoverageReport;            // undefined = not computed / no output schema
    coverageExpanded: boolean;            // show the full per-field list (collapsed = summary only)
    coverageRefining: boolean;            // an LLM gap-refinement call is in flight
    coverageRefined: boolean;             // gaps have been refined by the LLM (button consumed)
    mcpDialogMode: 'prompt' | 'preview';  // 'prompt' = input, 'preview' = show result
    // IF08: snapshot of the editor body the user opted to share via "Load current
    // UTLX". undefined = not loaded (body is NOT sent). Set at click time.
    loadedBody?: string;
    generatedCode: string;  // The generated code to preview
    promptHistory: PromptHistoryEntry[];  // History of prompts
    systemStatus: {
        mcpServer: boolean | null;  // null = checking, true = ok, false = error
        utlxd: boolean | null;
        llm: boolean | null;
        llmProvider?: string;
        llmModel?: string;
    };
    showValidationDialog: boolean;
    validationResult: SchemaComparisonResult | null;
}

@injectable()
export class UTLXToolbarWidget extends ReactWidget {
    static readonly ID = 'utlx-toolbar';
    static readonly LABEL = 'UTLX Toolbar';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    @inject(ApplicationShell)
    protected readonly shell!: ApplicationShell;

    private static readonly PROMPT_HISTORY_KEY = 'utlx.ai-assistant.prompt-history';
    private static readonly MAX_HISTORY_ENTRIES = 10;
    private static readonly STATUS_CHECK_INTERVAL_MS = 2000;  // Check status every 2 seconds

    private statusCheckInterval?: NodeJS.Timeout;  // Timer for periodic status checks
    private aiActivityPoll?: number;  // Timer polling the AI activity log during generation
    private static readonly AI_ACTIVITY_POLL_MS = 400;
    // Monotonic id of the current generation. Bumping it abandons any in-flight run:
    // its eventual result/error is discarded (see submitMCPPrompt guards). The backend
    // request keeps running to completion — we just stop waiting on it. True
    // cross-process cancellation is a documented follow-up (IF08).
    private genSeq = 0;

    private state: ToolbarState = {
        currentMode: UTLXMode.EXECUTION,
        showMCPDialog: false,
        mcpPrompt: '',
        mcpLoading: false,
        mcpProgressMessage: '',
        mcpProgressPercent: 0,
        mcpActivity: [],
        mcpAttempts: [],
        dialogInputs: [],
        expandedInputs: [],
        inputGloss: {},
        inputGlossLoading: [],
        coverageExpanded: false,
        coverageRefining: false,
        coverageRefined: false,
        mcpDialogMode: 'prompt',
        generatedCode: '',
        promptHistory: this.loadPromptHistory(),
        systemStatus: {
            mcpServer: null,
            utlxd: null,
            llm: null,
            llmProvider: undefined,
            llmModel: undefined
        },
        showValidationDialog: false,
        validationResult: null
    };

    constructor() {
        super();
        this.id = UTLXToolbarWidget.ID;
        this.title.label = UTLXToolbarWidget.LABEL;
        this.title.closable = false;
        this.addClass('utlx-toolbar');
    }

    @postConstruct()
    protected init(): void {
        this.update();

        // Subscribe to validation result events
        this.eventService.onValidationResult(event => {
            this.setState({
                showValidationDialog: true,
                validationResult: event.result
            });
        });

        // Sync the toolbar (badge + backend) when the mode is changed EXTERNALLY — e.g. opening a
        // .utlxp project forces Message-Contract (IF03). The toolbar emits onModeChanged from its own
        // toggle, so the guard ignores its own echo (event.mode already equals currentMode by then).
        this.eventService.onModeChanged(event => {
            if (event.mode === this.state.currentMode) {
                return;
            }
            this.setState({ currentMode: event.mode });
            this.utlxService.setMode({
                mode: event.mode,
                autoInferSchema: false,
                enableTypeChecking: true
            }).catch(err => console.error('[UTLXToolbar] Failed to sync external mode:', err));
        });

        // Sync backend mode with toolbar's default on startup
        this.utlxService.setMode({
            mode: this.state.currentMode,
            autoInferSchema: false,
            enableTypeChecking: true
        }).catch(err => console.error('[UTLXToolbar] Failed to sync initial mode:', err));
    }

    dispose(): void {
        // Clean up periodic status checks
        this.stopStatusChecks();
        this.stopAiActivityPoll();
        super.dispose();
    }

    /**
     * Load prompt history from localStorage
     */
    private loadPromptHistory(): PromptHistoryEntry[] {
        try {
            const stored = localStorage.getItem(UTLXToolbarWidget.PROMPT_HISTORY_KEY);
            if (stored) {
                const history = JSON.parse(stored) as PromptHistoryEntry[];
                console.log('[UTLXToolbar] Loaded', history.length, 'prompt history entries');
                return history;
            }
        } catch (error) {
            console.error('[UTLXToolbar] Failed to load prompt history:', error);
        }
        return [];
    }

    /**
     * Save prompt history to localStorage
     */
    private savePromptHistory(history: PromptHistoryEntry[]): void {
        try {
            // Keep only the most recent entries
            const toSave = history.slice(-UTLXToolbarWidget.MAX_HISTORY_ENTRIES);
            localStorage.setItem(UTLXToolbarWidget.PROMPT_HISTORY_KEY, JSON.stringify(toSave));
            console.log('[UTLXToolbar] Saved', toSave.length, 'prompt history entries');
        } catch (error) {
            console.error('[UTLXToolbar] Failed to save prompt history:', error);
        }
    }

    /**
     * Add a new entry to prompt history
     */
    private addToPromptHistory(prompt: string, result?: string): void {
        const entry: PromptHistoryEntry = {
            timestamp: new Date().toISOString(),
            prompt,
            result,
            mode: this.state.currentMode  // IF08: tag with the mode it was created in
        };

        const newHistory = [...this.state.promptHistory, entry];
        this.savePromptHistory(newHistory);

        this.setState({
            promptHistory: newHistory
        });
    }

    protected render(): React.ReactNode {
        const { currentMode, showMCPDialog, mcpPrompt, mcpLoading } = this.state;

        return (
            <div className='utlx-toolbar-container'>
                {/* Left section: Mode indicator and switcher */}
                <div className='utlx-toolbar-left'>
                    <div className='utlx-mode-indicator'>
                        <span className={`utlx-mode-badge ${currentMode === UTLXMode.EXECUTION ? 'execution' : 'message-contract'}`}>
                            {currentMode === UTLXMode.EXECUTION ? '▶️ Execution Mode' : '📋 Message Contract Mode'}
                        </span>
                    </div>

                    <div className='utlx-mode-switcher'>
                        <label className='utlx-toggle-switch'>
                            <input
                                data-testid='utlx-mode-toggle'
                                type='checkbox'
                                checked={currentMode === UTLXMode.MESSAGE_CONTRACT}
                                onChange={() => this.toggleMode()}
                                title={`Switch to ${currentMode === UTLXMode.EXECUTION ? 'Message Contract' : 'Execution'} mode`}
                            />
                            <span className='utlx-toggle-slider'>
                                <span className='utlx-toggle-label-left'>Execution</span>
                                <span className='utlx-toggle-label-right'>Contract</span>
                            </span>
                        </label>
                    </div>
                </div>

                {/* Center section: Info */}
                <div className='utlx-toolbar-center'>
                    <span className='utlx-mode-description'>
                        {this.getModeDescription(currentMode)}
                    </span>
                </div>

                {/* Right section: Actions */}
                <div className='utlx-toolbar-right'>
                    <button
                        data-testid='utlx-ai-assist'
                        className='utlx-toolbar-button utlx-mcp-button'
                        onClick={() => this.openMCPDialog()}
                        title='Ask AI to help write UTLX transformation'
                        disabled={mcpLoading}
                    >
                        🤖 AI Assist
                    </button>

                    <button
                        data-testid='utlx-execute'
                        className='utlx-toolbar-button'
                        onClick={() => this.handleExecute()}
                        title={currentMode === UTLXMode.EXECUTION ? 'Execute transformation' : 'Validate output schema'}
                    >
                        {currentMode === UTLXMode.EXECUTION ? '▶️ Execute' : '✅ Validate'}
                    </button>
                </div>

                {/* MCP Dialog */}
                {showMCPDialog && (
                    <div className='utlx-dialog-overlay' onClick={() => this.closeMCPDialog()}>
                        <div className='utlx-dialog' onClick={(e) => e.stopPropagation()}>
                            <div className='utlx-dialog-header'>
                                <h3>🤖 AI Assistant for UTLX</h3>
                                <div className='utlx-status-indicators'>
                                    {this.renderStatusIndicator('MCP Server', this.state.systemStatus.mcpServer)}
                                    {this.renderStatusIndicator('UTLXD', this.state.systemStatus.utlxd)}
                                    {this.renderStatusIndicator(
                                        this.state.systemStatus.llmModel
                                            ? `${this.state.systemStatus.llmProvider} (${this.state.systemStatus.llmModel})`
                                            : 'LLM',
                                        this.state.systemStatus.llm
                                    )}
                                </div>
                                <button
                                    data-testid='utlx-mcp-close'
                                    className='utlx-dialog-close'
                                    onClick={() => this.closeMCPDialog()}
                                >
                                    ✕
                                </button>
                            </div>

                            {/* Prompt Input Mode */}
                            {this.state.mcpDialogMode === 'prompt' && (
                                <>
                                    <div className='utlx-dialog-body'>
                                        {/* IF10: inputs the AI will use, each expandable to its abstract */}
                                        {this.state.dialogInputs.length > 0 && (
                                            <div className='utlx-dialog-inputs'>
                                                <div className='utlx-dialog-inputs-title'>
                                                    Inputs ({this.state.dialogInputs.length}) — the AI will use these
                                                </div>
                                                {this.state.dialogInputs.map((inp, idx) => {
                                                    const expanded = this.state.expandedInputs.includes(inp.name);
                                                    return (
                                                        <div key={idx} className='utlx-dialog-input-row'>
                                                            <button
                                                                data-testid='utlx-mcp-input-expand'
                                                                className='utlx-dialog-input-head'
                                                                onClick={() => this.toggleInputExpanded(inp.name)}
                                                                title='What is this input?'
                                                            >
                                                                <span className='utlx-dialog-input-chevron'>{expanded ? '▾' : '▸'}</span>
                                                                <span className='utlx-dialog-input-name'>${inp.name}</span>
                                                                <span className='utlx-dialog-input-format'>{inp.format}</span>
                                                            </button>
                                                            {expanded && (
                                                                <div className='utlx-dialog-input-detail'>
                                                                    <pre className='utlx-dialog-input-abstract'>
                                                                        {inp.abstract || 'No data loaded for this input — load a sample to summarize it.'}
                                                                    </pre>
                                                                    {/* IF10 v2: opt-in LLM "what is this" gloss */}
                                                                    {this.state.inputGloss[inp.name] !== undefined ? (
                                                                        <div className='utlx-dialog-input-gloss'>💡 {this.state.inputGloss[inp.name]}</div>
                                                                    ) : this.state.inputGlossLoading.includes(inp.name) ? (
                                                                        <div className='utlx-dialog-input-gloss'>⏳ Describing…</div>
                                                                    ) : (inp.abstract && this.state.systemStatus.llm !== false) ? (
                                                                        <button
                                                                            data-testid='utlx-mcp-explain'
                                                                            className='utlx-explain-ai-btn'
                                                                            title='Ask the AI what kind of message this is (one LLM call)'
                                                                            onClick={() => this.explainInput(inp)}
                                                                        >
                                                                            ✨ Explain (AI)
                                                                        </button>
                                                                    ) : null}
                                                                </div>
                                                            )}
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        )}
                                        {/* IF11: MC-mode coverage of the output contract by the inputs */}
                                        {currentMode === UTLXMode.MESSAGE_CONTRACT && this.renderCoverage()}

                                        <p>{currentMode === UTLXMode.MESSAGE_CONTRACT
                                            ? 'The AI will map the inputs onto the output contract. Add optional guidance below (e.g. defaults for gaps, lookups) — or just press “Map to output contract”.'
                                            : 'Describe what transformation you want to create:'}</p>

                                        {/* Prompt History Dropdown — IF08: only the
                                            current mode's prompts (legacy untagged = Execution) */}
                                        {(() => {
                                            const modeHistory = this.state.promptHistory
                                                .map((entry, index) => ({ entry, index }))
                                                .filter(({ entry }) => (entry.mode ?? UTLXMode.EXECUTION) === currentMode);
                                            if (modeHistory.length < 1) {
                                                return null;
                                            }
                                            return (
                                            <div className='utlx-prompt-history'>
                                                <label>Previous prompts:</label>
                                                <select
                                                    data-testid='utlx-mcp-history'
                                                    className='utlx-prompt-history-select'
                                                    onChange={(e) => {
                                                        const index = parseInt(e.target.value, 10);
                                                        if (index >= 0 && index < this.state.promptHistory.length) {
                                                            this.setState({ mcpPrompt: this.state.promptHistory[index].prompt });
                                                        }
                                                    }}
                                                    disabled={mcpLoading}
                                                >
                                                    <option value="">-- Select a previous prompt --</option>
                                                    {modeHistory.slice().reverse().map(({ entry, index }) => {
                                                        const date = new Date(entry.timestamp);
                                                        const timeStr = date.toLocaleString();
                                                        const preview = entry.prompt.length > 60
                                                            ? entry.prompt.substring(0, 60) + '...'
                                                            : entry.prompt;
                                                        return (
                                                            <option key={index} value={index}>
                                                                {timeStr}: {preview}
                                                            </option>
                                                        );
                                                    })}
                                                </select>
                                            </div>
                                            );
                                        })()}

                                        <textarea
                                            data-testid='utlx-mcp-prompt'
                                            className='utlx-mcp-prompt-input'
                                            value={mcpPrompt}
                                            onChange={(e) => this.setState({ mcpPrompt: (e.target as HTMLTextAreaElement).value })}
                                            placeholder={currentMode === UTLXMode.MESSAGE_CONTRACT
                                                ? 'Optional guidance — e.g. "default chargeBearer to SHAR", "look up currencyName from currencyCode". Leave empty to just map what you can.'
                                                : 'Example: "Transform XML customer data to JSON, extracting name, email, and address fields"'}
                                            rows={6}
                                            disabled={mcpLoading}
                                            autoFocus
                                        />
                                        {!mcpLoading && (
                                            <div className='utlx-mcp-loadbody'>
                                                {this.state.loadedBody ? (
                                                    <div className='utlx-mcp-loadbody-chip'>
                                                        <span>📎</span>
                                                        <span className='utlx-mcp-loadbody-label'>
                                                            Current UTLX loaded ({this.state.loadedBody.split('\n').length} lines
                                                            {this.state.loadedBody.includes('???(') ? ', scaffold' : ''})
                                                        </span>
                                                        <button
                                                            data-testid='utlx-mcp-clear-body'
                                                            className='utlx-mcp-loadbody-remove'
                                                            title='Remove — generate without the current UTLX'
                                                            onClick={() => this.clearLoadedBody()}
                                                        >
                                                            ×
                                                        </button>
                                                    </div>
                                                ) : (
                                                    <div className='utlx-mcp-loadbody-actions'>
                                                        <button
                                                            data-testid='utlx-mcp-load-current'
                                                            className='utlx-mcp-loadbody-btn'
                                                            title='Send the current editor body to the AI as a starting point'
                                                            onClick={() => this.loadCurrentUtlx()}
                                                        >
                                                            📎 Load current UTLX
                                                        </button>
                                                        {this.getCurrentEditorBody()?.includes('???(') && (
                                                            <span className='utlx-mcp-loadbody-hint'>
                                                                Scaffold detected — load it to have AI fill it in.
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                        {mcpLoading && this.state.mcpProgressMessage && (
                                            <div className='utlx-mcp-progress'>
                                                <div className='utlx-mcp-progress-spinner'>⏳</div>
                                                <div className='utlx-mcp-progress-message'>{this.state.mcpProgressMessage}</div>
                                            </div>
                                        )}
                                        {/* AI activity monitor: live step-by-step log of the generation */}
                                        {this.state.mcpActivity.length > 0 && (
                                            <div className='utlx-ai-activity'>
                                                <div className='utlx-ai-activity-title'>
                                                    <span>AI activity</span>
                                                    <button
                                                        data-testid='utlx-mcp-copy-log'
                                                        className='utlx-ai-activity-copy'
                                                        title='Copy the AI activity log (+ per-attempt code/errors)'
                                                        onClick={() => this.copyAiLog()}
                                                    >
                                                        ⧉ Copy
                                                    </button>
                                                </div>
                                                <div className='utlx-ai-activity-log'>
                                                    {this.state.mcpActivity.map((entry, idx) => {
                                                        const t0 = this.state.mcpActivity[0]?.ts ?? entry.ts;
                                                        const rel = ((entry.ts - t0) / 1000).toFixed(1);
                                                        const isLast = idx === this.state.mcpActivity.length - 1;
                                                        return (
                                                            <div
                                                                key={idx}
                                                                className={`utlx-ai-activity-step${isLast && mcpLoading ? ' active' : ''}`}
                                                            >
                                                                <span className='utlx-ai-activity-time'>+{rel}s</span>
                                                                <span className='utlx-ai-activity-msg'>{entry.message}</span>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            </div>
                                        )}
                                    </div>

                                    <div className='utlx-dialog-footer'>
                                        <button
                                            data-testid='utlx-mcp-cancel-prompt'
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => {
                                                console.log('[UTLXToolbar] 🔘 Cancel button clicked');
                                                // Dual purpose: while generating, Cancel stops the
                                                // run AND closes; otherwise it just closes.
                                                if (mcpLoading) {
                                                    this.stopGeneration(true);
                                                } else {
                                                    this.closeMCPDialog();
                                                }
                                            }}
                                            title={mcpLoading ? 'Stop the generation and close' : 'Close'}
                                        >
                                            Cancel
                                        </button>
                                        {mcpLoading ? (
                                            <button
                                                data-testid='utlx-mcp-stop'
                                                className='utlx-dialog-button utlx-dialog-button-stop'
                                                onClick={() => this.stopGeneration(false)}
                                                title='Stop the generation but keep this dialog open so you can edit the prompt and retry'
                                            >
                                                ■ Stop
                                            </button>
                                        ) : (
                                            <button
                                                data-testid='utlx-mcp-submit'
                                                className='utlx-dialog-button utlx-dialog-button-primary'
                                                onClick={() => this.submitMCPPrompt()}
                                                // IF11: prompt is optional in MC mode (goal is fixed: map to the contract).
                                                disabled={currentMode === UTLXMode.EXECUTION && !mcpPrompt.trim()}
                                            >
                                                {currentMode === UTLXMode.MESSAGE_CONTRACT ? '✨ Map to output contract' : '✨ Generate UTLX'}
                                            </button>
                                        )}
                                    </div>
                                </>
                            )}

                            {/* Preview Mode */}
                            {this.state.mcpDialogMode === 'preview' && (
                                <>
                                    <div className='utlx-dialog-body'>
                                        {/* Red warning when the result didn't validate/run — still show the code */}
                                        {this.state.mcpResultValid === false && (
                                            <div className='utlx-result-warning'>
                                                <span className='utlx-result-warning-icon'>⚠️</span>
                                                <span>
                                                    This UTLX did <strong>not</strong> validate/run — review and fix before applying.
                                                    {this.state.mcpResultStatus ? ` ${this.state.mcpResultStatus}` : ''}
                                                </span>
                                            </div>
                                        )}
                                        <p>Review the generated UTLX transformation:</p>
                                        <textarea
                                            className={`utlx-mcp-prompt-input utlx-preview-code${this.state.mcpResultValid === false ? ' invalid' : ''}`}
                                            value={this.state.generatedCode}
                                            readOnly
                                            rows={15}
                                        />
                                        {/* Per-attempt detail (code + errors) for prompt-tuning */}
                                        {this.state.mcpAttempts.length > 0 && (
                                            <details className='utlx-attempts'>
                                                <summary>
                                                    Attempts ({this.state.mcpAttempts.length}) — code &amp; errors per attempt
                                                </summary>
                                                {this.state.mcpAttempts.map((a, i) => (
                                                    <div key={i} className={`utlx-attempt utlx-attempt-${a.status}`}>
                                                        <div className='utlx-attempt-head'>
                                                            Attempt {a.attempt}: <strong>{a.status}</strong>
                                                        </div>
                                                        {a.errors && a.errors.length > 0 && (
                                                            <ul className='utlx-attempt-errors'>
                                                                {a.errors.map((e, j) => <li key={j}>{e}</li>)}
                                                            </ul>
                                                        )}
                                                        <pre className='utlx-attempt-code'>{a.code}</pre>
                                                    </div>
                                                ))}
                                            </details>
                                        )}
                                    </div>

                                    <div className='utlx-dialog-footer'>
                                        <button
                                            data-testid='utlx-mcp-cancel'
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => this.cancelGeneration()}
                                        >
                                            Cancel
                                        </button>
                                        <button
                                            data-testid='utlx-mcp-retry'
                                            className='utlx-dialog-button utlx-dialog-button-secondary'
                                            onClick={() => this.retryGeneration()}
                                        >
                                            🔄 Retry
                                        </button>
                                        <button
                                            data-testid='utlx-mcp-apply'
                                            className='utlx-dialog-button utlx-dialog-button-primary'
                                            onClick={() => this.applyGeneratedCode()}
                                        >
                                            ✅ Apply
                                        </button>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>
                )}

                {/* Validation Result Dialog */}
                {this.state.showValidationDialog && this.state.validationResult && (
                    <ValidationResultDialog
                        result={this.state.validationResult}
                        onClose={() => this.setState({ showValidationDialog: false, validationResult: null })}
                    />
                )}
            </div>
        );
    }

    private getModeDescription(mode: UTLXMode): string {
        switch (mode) {
            case UTLXMode.MESSAGE_CONTRACT:
                return 'Contract validation • Type checking • No data execution';
            case UTLXMode.EXECUTION:
                return 'Data transformation • Real execution • Performance metrics';
        }
    }

    private async toggleMode(): Promise<void> {
        const newMode = this.state.currentMode === UTLXMode.EXECUTION
            ? UTLXMode.MESSAGE_CONTRACT
            : UTLXMode.EXECUTION;

        this.setState({ currentMode: newMode });

        const modeName = newMode === UTLXMode.EXECUTION ? 'Execution' : 'Message Contract';
        this.messageService.info(`✓ Switched to ${modeName} mode`);

        // Update backend mode
        try {
            await this.utlxService.setMode({
                mode: newMode,
                autoInferSchema: false,
                enableTypeChecking: true
            });
            console.log('[UTLXToolbar] Backend mode updated to:', newMode);
        } catch (error) {
            console.error('[UTLXToolbar] Failed to update backend mode:', error);
        }

        // Fire event for other widgets to update
        this.eventService.fireModeChanged({ mode: newMode });
    }

    private openMCPDialog(): void {
        console.log('[UTLXToolbar] 🤖 Opening MCP dialog');

        // Get the most recent prompt from history
        const lastPrompt = this.state.promptHistory.length > 0
            ? this.state.promptHistory[this.state.promptHistory.length - 1].prompt
            : '';

        if (lastPrompt) {
            console.log('[UTLXToolbar] Restoring last prompt:', lastPrompt.substring(0, 50) + '...');
        }

        this.setState({
            showMCPDialog: true,
            mcpPrompt: lastPrompt,  // Restore last prompt
            dialogInputs: this.snapshotDialogInputs(),  // IF10: input list + abstracts
            expandedInputs: [],
            // IF11: in MC mode, analyze how well the inputs cover the output contract.
            coverage: this.state.currentMode === UTLXMode.MESSAGE_CONTRACT ? this.snapshotCoverage() : undefined,
            coverageExpanded: false,
            coverageRefining: false,
            coverageRefined: false,
            systemStatus: {
                mcpServer: null,
                utlxd: null,
                llm: null,
                llmProvider: undefined,
                llmModel: undefined
            }
        });
        console.log('[UTLXToolbar] ✅ MCP dialog state updated, showMCPDialog:', true);

        // Check system status immediately
        this.checkSystemStatus();

        // Start periodic status checks every 2 seconds
        this.startStatusChecks();
    }

    private async checkSystemStatus(): Promise<void> {
        console.log('[UTLXToolbar] Checking system status...');

        // Check all services in parallel
        let mcpServerOk = false;
        let utlxdOk = false;
        let llmAvailable = false;
        let llmProvider: string | undefined;
        let llmModel: string | undefined;

        try {
            // Check LLM status (which also validates MCP server connection)
            const llmStatus = await this.utlxService.checkLlmStatus();
            console.log('[UTLXToolbar] LLM status result:', llmStatus);

            // MCP Server is OK if we got a valid response from checkLlmStatus
            mcpServerOk = true;
            llmAvailable = llmStatus.available;
            llmProvider = llmStatus.provider;
            llmModel = llmStatus.model;
        } catch (llmError) {
            console.warn('[UTLXToolbar] LLM/MCP check failed:', llmError);
            mcpServerOk = false;
            llmAvailable = false;
        }

        try {
            // Check UTLXD separately
            utlxdOk = await this.utlxService.ping();
            console.log('[UTLXToolbar] UTLXD ping result:', utlxdOk);
        } catch (pingError) {
            console.warn('[UTLXToolbar] UTLXD ping failed:', pingError);
            utlxdOk = false;
        }

        // Update state with all results
        this.setState({
            systemStatus: {
                mcpServer: mcpServerOk,
                utlxd: utlxdOk,
                llm: llmAvailable,
                llmProvider: llmProvider,
                llmModel: llmModel
            }
        });
    }

    private startStatusChecks(): void {
        // Clear any existing interval
        this.stopStatusChecks();

        // Start periodic checks
        this.statusCheckInterval = setInterval(() => {
            console.log('[UTLXToolbar] Periodic status check triggered');
            this.checkSystemStatus();
        }, UTLXToolbarWidget.STATUS_CHECK_INTERVAL_MS);

        console.log('[UTLXToolbar] Started periodic status checks every', UTLXToolbarWidget.STATUS_CHECK_INTERVAL_MS, 'ms');
    }

    private stopStatusChecks(): void {
        if (this.statusCheckInterval) {
            clearInterval(this.statusCheckInterval);
            this.statusCheckInterval = undefined;
            console.log('[UTLXToolbar] Stopped periodic status checks');
        }
    }

    // ── AI activity monitor ──────────────────────────────────────────────────

    /** Poll the backend's AI step log while a generation is running. */
    private startAiActivityPoll(): void {
        this.stopAiActivityPoll();
        this.aiActivityPoll = window.setInterval(() => {
            void this.refreshAiActivity();
        }, UTLXToolbarWidget.AI_ACTIVITY_POLL_MS);
    }

    private stopAiActivityPoll(): void {
        if (this.aiActivityPoll !== undefined) {
            window.clearInterval(this.aiActivityPoll);
            this.aiActivityPoll = undefined;
        }
    }

    /** Copy the AI activity timeline + per-attempt detail to the clipboard. */
    private copyAiLog(): void {
        const lines: string[] = ['# AI activity'];
        const t0 = this.state.mcpActivity[0]?.ts;
        for (const e of this.state.mcpActivity) {
            const rel = t0 ? `+${((e.ts - t0) / 1000).toFixed(1)}s ` : '';
            lines.push(`${rel}${e.message}`);
        }
        if (this.state.mcpAttempts.length > 0) {
            lines.push('', '# Attempts');
            for (const a of this.state.mcpAttempts) {
                lines.push('', `## Attempt ${a.attempt} — ${a.status}`);
                if (a.errors && a.errors.length) {
                    lines.push('errors:', ...a.errors.map(e => `  - ${e}`));
                }
                lines.push('code:', a.code);
            }
        }
        const text = lines.join('\n');
        try {
            void navigator.clipboard.writeText(text);
            this.messageService.info('AI activity log copied to clipboard');
        } catch {
            this.messageService.warn('Could not copy to clipboard');
        }
    }

    /** Pull the latest step log from the backend; only re-render when it grew. */
    private async refreshAiActivity(): Promise<void> {
        try {
            const activity = await this.utlxService.getAiActivity();
            if (activity.length !== this.state.mcpActivity.length) {
                this.setState({ mcpActivity: activity });
            }
        } catch {
            /* polling is best-effort; ignore transient RPC errors */
        }
    }

    private renderStatusIndicator(label: string, status: boolean | null): React.ReactNode {
        let icon: string;
        let className: string;

        if (status === null) {
            // Checking
            icon = '⏳';
            className = 'utlx-status-checking';
        } else if (status) {
            // OK
            icon = '✓';
            className = 'utlx-status-ok';
        } else {
            // Error
            icon = '✕';
            className = 'utlx-status-error';
        }

        return (
            <div className={`utlx-status-indicator ${className}`} title={label}>
                <span className='utlx-status-icon'>{icon}</span>
                <span className='utlx-status-label'>{label}</span>
            </div>
        );
    }

    private closeMCPDialog(): void {
        console.log('[UTLXToolbar] 🚪 Closing MCP dialog');

        // Abandon any in-flight generation so a late result can't reopen/overwrite.
        this.genSeq++;
        // Stop periodic status checks
        this.stopStatusChecks();
        this.stopAiActivityPoll();

        this.setState({
            showMCPDialog: false,
            mcpPrompt: '',
            mcpLoading: false,
            mcpDialogMode: 'prompt',
            generatedCode: '',
            mcpActivity: [],
            mcpAttempts: [],
            mcpResultValid: undefined,
            mcpResultStatus: undefined,
            dialogInputs: [],
            expandedInputs: [],
            inputGloss: {},
            inputGlossLoading: [],
            loadedBody: undefined  // IF08: drop the shared body when the dialog closes
        });
        console.log('[UTLXToolbar] ✅ MCP dialog state updated to closed');
    }

    /**
     * IF10: snapshot the inputs (name + format) and compute each one's deterministic
     * semantic abstract from its UDM. No AI — just a walk of the parsed structure.
     */
    private snapshotDialogInputs(): Array<{ name: string; format: string; abstract?: string }> {
        const inputPanel = this.shell.getWidgets('left')
            .find((w: any) => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
        if (!inputPanel) {
            return [];
        }
        const tabs = inputPanel.getAllInputTabs();
        const fullInputs = (inputPanel as any).state?.inputs || [];
        return tabs.map((t: any) => {
            const full = fullInputs.find((i: any) => i.id === t.id);
            // Schema-aware abstract (shared with the input-panel Info button).
            const abstract = buildAbstractForInput({
                name: t.name,
                instanceContent: full?.instanceContent,
                instanceFormat: full?.instanceFormat,
                schemaContent: full?.schemaContent,
                schemaFormat: full?.schemaFormat,
                udmLanguage: full?.udmLanguage,
            });
            return { name: t.name, format: t.format, abstract };
        });
    }

    /**
     * IF11: render the MC-mode coverage panel — a summary line, the delta (gaps),
     * and an expandable per-field list. Guides the user (and grounds generation)
     * before they describe the mapping.
     */
    private renderCoverage(): React.ReactNode {
        const cov = this.state.coverage;
        if (!cov) {
            return (
                <div className='utlx-coverage utlx-coverage-empty'>
                    <span className='utlx-coverage-title'>Contract coverage</span>
                    <span className='utlx-coverage-hint'>
                        Load an output schema (and input schemas) to see how well the inputs cover the output contract.
                    </span>
                </div>
            );
        }
        const c = cov.counts;
        const expanded = this.state.coverageExpanded;
        const icon = (s: string) => (s === 'direct' ? '✓' : s === 'derivable' ? '~' : '✗');
        return (
            <div className='utlx-coverage'>
                <button
                    data-testid='utlx-coverage-toggle'
                    className='utlx-coverage-head'
                    onClick={() => this.setState({ coverageExpanded: !expanded })}
                    title='How well the inputs cover the output contract'
                >
                    <span className='utlx-coverage-chevron'>{expanded ? '▾' : '▸'}</span>
                    <span className='utlx-coverage-title'>Contract coverage</span>
                    <span className='utlx-coverage-counts'>
                        <span className='cov-direct'>✓ {c.direct}</span>
                        <span className='cov-derivable'>~ {c.derivable}</span>
                        <span className='cov-gap'>✗ {c.gap}</span>
                        <span className='cov-total'>of {c.total}</span>
                    </span>
                </button>

                {/* The delta is the headline: required fields with no source. Always shown. */}
                {cov.delta.length > 0 ? (
                    <div className='utlx-coverage-delta'>
                        ⚠ {cov.delta.length} required field{cov.delta.length === 1 ? '' : 's'} with no source —
                        needs a lookup, an extra input, or a default:
                        <span className='utlx-coverage-delta-list'> {cov.delta.map(e => e.outputPath).join(', ')}</span>
                    </div>
                ) : (
                    <div className='utlx-coverage-ok'>✓ Every required output field has a candidate source.</div>
                )}

                {/* IF11: opt-in LLM gap refinement — resolve name-mismatched gaps semantically. */}
                {(() => {
                    const gapCount = cov.entries.filter(e => e.status === 'gap').length;
                    if (gapCount === 0) return null;
                    if (this.state.coverageRefining) {
                        return <div className='utlx-coverage-refine-status'>⏳ Refining gaps with AI…</div>;
                    }
                    if (this.state.coverageRefined) {
                        return <div className='utlx-coverage-refine-status'>✨ Gaps refined by AI — review the suggested sources.</div>;
                    }
                    if (this.state.systemStatus.llm === false) return null;
                    return (
                        <button
                            data-testid='utlx-coverage-refine'
                            className='utlx-coverage-refine-btn'
                            title='Ask the AI to resolve the remaining gaps to source fields (one LLM call)'
                            onClick={() => this.refineCoverageGaps()}
                        >
                            ✨ Refine gaps (AI)
                        </button>
                    );
                })()}

                {expanded && (
                    <div className='utlx-coverage-list'>
                        {cov.entries.map((e, i) => (
                            <div key={i} className={`utlx-coverage-row cov-${e.status}`}>
                                <span className='cov-icon'>{icon(e.status)}</span>
                                <span className='cov-path'>{e.outputPath}{e.required ? '*' : ''}</span>
                                <span className='cov-type'>({e.type})</span>
                                {e.bySuggestion && <span className='cov-ai' title='AI-suggested — review'>✨</span>}
                                {e.source && <span className='cov-source'>← {e.expression || e.source}</span>}
                                {e.note && <span className='cov-note'>{e.note}</span>}
                            </div>
                        ))}
                        <div className='utlx-coverage-legend'>✓ direct · ~ derivable · ✗ gap · * required · ✨ AI-suggested</div>
                    </div>
                )}
            </div>
        );
    }

    /**
     * IF11: deterministic coverage of the output contract by the input schema(s).
     * Pulls each input's schema (MC mode is schema→schema) + the output panel's
     * expected schema, then classifies every output leaf direct/derivable/gap.
     * No AI — pure name/type matching. Undefined when there's no output schema.
     */
    private snapshotCoverage(): CoverageReport | undefined {
        const inputPanel = this.shell.getWidgets('left')
            .find((w: any) => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
        const outputPanel = this.shell.getWidgets('right')
            .find((w: any) => w instanceof OutputPanelWidget) as OutputPanelWidget | undefined;
        if (!inputPanel || !outputPanel) {
            return undefined;
        }
        const expected = outputPanel.getExpectedSchema();
        if (!expected) {
            return undefined;  // no output contract to cover against
        }
        const tabs = inputPanel.getAllInputTabs();
        const fullInputs = (inputPanel as any).state?.inputs || [];
        const inputs = tabs.map((t: any) => {
            const full = fullInputs.find((i: any) => i.id === t.id);
            return { name: t.name, content: full?.schemaContent, format: full?.schemaFormat };
        });
        return buildContractCoverage(inputs, expected.content, expected.format);
    }

    /**
     * IF11: the Message Contract context attached to a generation request — the output
     * CONTRACT schema (the fixed target) and the current coverage plan (output field →
     * source / gap), so the model performs constrained synthesis against the contract.
     */
    private buildMCContractContext(): Partial<GenerateUtlxRequest> {
        const outputPanel = this.shell.getWidgets('right')
            .find((w: any) => w instanceof OutputPanelWidget) as OutputPanelWidget | undefined;
        const expected = outputPanel?.getExpectedSchema();
        const ctx: Partial<GenerateUtlxRequest> = {};
        if (expected) {
            ctx.outputSchema = { content: expected.content, format: expected.format };
        }
        const cov = this.state.coverage;
        if (cov) {
            ctx.coverage = cov.entries.map<CoverageMappingHint>(e => ({
                outputPath: e.outputPath,
                type: e.type,
                required: e.required,
                status: e.status,
                source: e.source,
                expression: e.expression,
            }));
        }
        return ctx;
    }

    /**
     * IF11: opt-in LLM gap refinement. Sends the deterministic gaps + the available
     * input fields to the AI, which suggests a semantic source/derivation per gap.
     * Suggestions are merged back (marked ✨, gaps that resolve leave the delta).
     * One cached call per dialog session. Graceful: no LLM / failure leaves coverage
     * unchanged and the button consumed.
     */
    private async refineCoverageGaps(): Promise<void> {
        const cov = this.state.coverage;
        if (!cov || this.state.coverageRefining || this.state.coverageRefined) {
            return;
        }
        const gaps = cov.entries
            .filter(e => e.status === 'gap')
            .map(e => ({ path: e.outputPath, type: e.type, required: e.required }));
        if (gaps.length === 0) {
            return;
        }

        // Re-gather the input fields (same source as snapshotCoverage) and flatten them.
        const inputPanel = this.shell.getWidgets('left')
            .find((w: any) => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
        const tabs = inputPanel ? inputPanel.getAllInputTabs() : [];
        const fullInputs = (inputPanel as any)?.state?.inputs || [];
        const inputs = tabs
            .map((t: any) => {
                const full = fullInputs.find((i: any) => i.id === t.id);
                const fields = parseSchemaToFields(full?.schemaContent, full?.schemaFormat);
                return fields && fields.length
                    ? { name: t.name, fields: flattenSchemaLeaves(fields) }
                    : undefined;
            })
            .filter(Boolean) as Array<{ name: string; fields: Array<{ path: string; name: string; type: string }> }>;

        this.setState({ coverageRefining: true });
        let suggestions: import('../../common/protocol').CoverageSuggestion[] = [];
        try {
            suggestions = await this.utlxService.refineCoverage({ gaps, inputs });
        } catch (error) {
            console.error('[UTLXToolbar] refineCoverage failed:', error);
        }
        const current = this.state.coverage;
        const merged = current && suggestions.length > 0
            ? mergeCoverageSuggestions(current, suggestions)
            : current;
        this.setState({ coverage: merged, coverageRefining: false, coverageRefined: true });
    }

    /**
     * IF10 v2: opt-in LLM gloss for one input ("Explain (AI)"). One call, cached per
     * input for the dialog session. Graceful: failures/no-LLM yield a short note.
     */
    private async explainInput(inp: { name: string; format: string; abstract?: string }): Promise<void> {
        if (!inp.abstract) {
            return;
        }
        if (this.state.inputGloss[inp.name] !== undefined || this.state.inputGlossLoading.includes(inp.name)) {
            return;  // cached or in-flight
        }
        this.setState({ inputGlossLoading: [...this.state.inputGlossLoading, inp.name] });
        let gloss = '';
        try {
            gloss = await this.utlxService.describeInput(inp.abstract, inp.format);
        } catch {
            gloss = '';
        }
        this.setState({
            inputGloss: { ...this.state.inputGloss, [inp.name]: gloss || '(could not describe this input)' },
            inputGlossLoading: this.state.inputGlossLoading.filter(n => n !== inp.name),
        });
    }

    /** IF10: toggle the inline abstract for one input row. */
    private toggleInputExpanded(name: string): void {
        const set = new Set(this.state.expandedInputs);
        if (set.has(name)) {
            set.delete(name);
        } else {
            set.add(name);
        }
        this.setState({ expandedInputs: Array.from(set) });
    }

    /**
     * IF08: read the current editor body (sync), split off the header.
     * Used by the "Load current UTLX" action and the stateless scaffold hint.
     */
    private getCurrentEditorBody(): string | undefined {
        const editor = this.shell.getWidgets('main')
            .find((w: any) => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
        if (!editor) {
            return undefined;
        }
        try {
            const { body } = this.splitHeaderAndBody(editor.getContent());
            return body;
        } catch {
            return undefined;
        }
    }

    /**
     * IF08: snapshot the current editor body into the request (opt-in). The body
     * is sent ONLY after this — never automatically.
     */
    private loadCurrentUtlx(): void {
        const trimmed = (this.getCurrentEditorBody() ?? '').trim();
        if (!trimmed) {
            this.messageService.info('The editor body is empty — nothing to load.');
            return;
        }
        this.setState({ loadedBody: trimmed });
    }

    /** IF08: drop the shared body so generation ignores the current editor. */
    private clearLoadedBody(): void {
        this.setState({ loadedBody: undefined });
    }

    private applyGeneratedCode(): void {
        console.log('[UTLXToolbar] ✅ Applying generated code to editor');

        // Get editor widget
        const editor = this.shell.getWidgets('main').find((w: any) => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
        if (!editor) {
            this.messageService.error('No active UTLX editor found');
            return;
        }

        // Apply the generated code
        editor.setContent(this.state.generatedCode);

        // Fire event
        this.eventService.fireUTLXGenerated({
            prompt: this.state.mcpPrompt,
            utlx: this.state.generatedCode
        });

        this.messageService.info('✨ UTLX transformation applied to editor!');
        this.closeMCPDialog();
    }

    private retryGeneration(): void {
        console.log('[UTLXToolbar] 🔄 Retrying generation - back to prompt');
        this.setState({
            mcpDialogMode: 'prompt',
            generatedCode: '',
            mcpLoading: false
        });
    }

    private cancelGeneration(): void {
        console.log('[UTLXToolbar] ❌ Cancelling - discarding generated code');
        this.closeMCPDialog();
    }

    /**
     * Stop an in-flight generation. Bumping genSeq makes submitMCPPrompt discard the
     * eventual result/error of the run it started. The backend request still finishes
     * on its own (no cross-process abort yet — see IF08), but the UI is unblocked
     * immediately so the user can fix the prompt and retry, or close the dialog.
     *
     * @param closeAfter true = stop and close the dialog; false = stop but stay on the
     *                   prompt so the user can edit and regenerate.
     */
    private stopGeneration(closeAfter: boolean): void {
        console.log('[UTLXToolbar] ■ Stopping generation (abandon in-flight)');
        this.genSeq++;  // invalidate the running generation
        this.stopAiActivityPoll();
        if (closeAfter) {
            this.closeMCPDialog();
            return;
        }
        this.setState({
            mcpLoading: false,
            mcpProgressMessage: 'Generation stopped. Edit your prompt and try again.',
            mcpProgressPercent: 0,
        });
    }

    private async submitMCPPrompt(): Promise<void> {
        const { mcpPrompt } = this.state;
        const isMC = this.state.currentMode === UTLXMode.MESSAGE_CONTRACT;

        // The prompt is REQUIRED in Execution mode (the user invents the output) but
        // OPTIONAL in Message Contract mode (the goal is fixed: map the inputs onto the
        // output contract). IF11.
        if (!isMC && !mcpPrompt.trim()) {
            return;
        }

        // Token for this run; if Stop/Cancel/close bumps genSeq, we discard this run's
        // result (the backend keeps going, but the UI ignores it).
        const myGen = ++this.genSeq;
        const abandoned = () => myGen !== this.genSeq;

        this.setState({ mcpLoading: true, mcpProgressMessage: 'Initializing AI assistant...' });

        try {
            // Call MCP service to generate UTLX
            this.messageService.info('🤖 Asking AI to generate UTLX transformation...');

            // Step 0: Check LLM status first
            console.log('[Toolbar] Step 0: Checking LLM availability...');
            this.setState({ mcpProgressMessage: 'Checking LLM provider...' });

            const statusResult = await this.utlxService.checkLlmStatus();
            console.log('[Toolbar] LLM status:', statusResult);

            if (!statusResult.available) {
                const errorMsg = statusResult.error || `${statusResult.provider || 'LLM'} is not available`;
                throw new Error(errorMsg);
            }

            this.setState({ mcpProgressMessage: `✓ ${statusResult.provider} is ready` });
            await new Promise(resolve => setTimeout(resolve, 500)); // Brief pause to show success

            console.log('[Toolbar] Step 1: Finding widgets...');
            this.setState({ mcpProgressMessage: 'Collecting input context...' });

            // Get input panel widget to collect context
            const inputPanel = this.shell.getWidgets('left').find((w: any) => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
            if (!inputPanel) {
                console.error('[Toolbar] Input panel not found');
                throw new Error('Input panel not found');
            }
            console.log('[Toolbar] ✓ Input panel found');

            // Get editor widget to determine output format
            const editor = this.shell.getWidgets('main').find((w: any) => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
            if (!editor) {
                console.error('[Toolbar] Editor not found');
                throw new Error('Editor not found');
            }
            console.log('[Toolbar] ✓ Editor found');

            console.log('[Toolbar] Step 2: Collecting input context...');

            // Collect input context
            const inputs = inputPanel.getAllInputTabs();
            console.log('[Toolbar] Input tabs:', inputs);

            if (inputs.length === 0) {
                throw new Error('No inputs defined. Please add at least one input.');
            }

            // Get existing editor content and extract header
            const editorContent = editor.getContent();
            const { header, body } = this.splitHeaderAndBody(editorContent);
            // IB05: in MC mode the editor's `output` directive is the CONTRACT SCHEMA format
            // (jsch/xsd/usdl/…). The transformation must emit a DATA INSTANCE conforming to the
            // contract, so generation's output format must be the DATA format the schema
            // describes (xsd→xml, jsch→json, …) — NOT the schema format (else the model returns
            // the schema/USDL instead of a mapping). Execution mode is left as-is.
            const rawOutputFormat = this.extractOutputFormat(editorContent);
            const outputFormat = isMC ? this.schemaToDataFormat(rawOutputFormat) : rawOutputFormat;

            console.log('[Toolbar] Step 3: Building request...');
            console.log('[Toolbar] Existing header:', header.substring(0, 100));
            console.log('[Toolbar] Generating UTLX with:', {
                prompt: mcpPrompt.substring(0, 100),
                inputCount: inputs.length,
                outputFormat
            });

            this.setState({ mcpProgressMessage: `Preparing request with ${inputs.length} input(s)...` });

            // Build the request
            const request: GenerateUtlxRequest = {
                prompt: mcpPrompt,
                inputs: inputs.map(input => {
                    // Find the full input tab to get UDM
                    const fullInput = (inputPanel as any).state?.inputs?.find((i: any) => i.id === input.id);
                    console.log('[Toolbar] Processing input:', input.name, {
                        hasFullInput: !!fullInput,
                        hasContent: !!fullInput?.instanceContent,
                        hasUDM: !!fullInput?.udmLanguage
                    });

                    // Extract compact path structure from UDM instead of sending full UDM
                    let pathStructure: string | undefined;
                    if (fullInput?.udmLanguage) {
                        try {
                            const paths = extractInputPaths(fullInput.udmLanguage, input.name);
                            pathStructure = formatPathsAsSimpleList(paths);
                            console.log('[Toolbar] Extracted', paths.length, 'paths from UDM for', input.name);
                        } catch (error) {
                            console.error('[Toolbar] Failed to extract paths from UDM:', error);
                            // Fallback to sending full UDM
                            pathStructure = undefined;
                        }
                    }

                    return {
                        name: input.name,
                        format: input.format,
                        originalData: fullInput?.instanceContent,
                        udm: pathStructure || fullInput?.udmLanguage,  // Use paths if available, fallback to UDM
                        // IF11: in MC mode the source structure comes from the schema.
                        ...(fullInput?.schemaContent ? { schema: fullInput.schemaContent } : {})
                    };
                }),
                outputFormat,
                originalHeader: header,  // Send original header for validation
                // IF08: tag the request with the current IDE mode, and include the
                // editor body ONLY when the user opted in via "Load current UTLX".
                mode: this.state.currentMode,
                ...(this.state.loadedBody ? { existingBody: this.state.loadedBody } : {}),
                // IF11: Message Contract mode — attach the output contract + coverage plan
                // so generation is constrained synthesis against the fixed target.
                ...(isMC ? this.buildMCContractContext() : {})
            };

            console.log('[Toolbar] Step 4: Calling backend service...');
            console.log('[Toolbar] Request:', {
                prompt: request.prompt.substring(0, 50),
                inputCount: request.inputs.length,
                outputFormat: request.outputFormat,
                hasOriginalHeader: !!request.originalHeader,
                originalHeaderPreview: request.originalHeader?.substring(0, 100)
            });
            this.setState({ mcpProgressMessage: 'Generating transformation with AI (this may take 30-60 seconds)...', mcpProgressPercent: 0, mcpActivity: [] });

            // AI activity monitor: poll the backend's step log while it generates.
            this.startAiActivityPoll();

            // If the user stopped while we were collecting context / checking the LLM,
            // don't even launch the generation.
            if (abandoned()) {
                console.log('[Toolbar] Generation abandoned before launch — skipping');
                return;
            }

            // Call the backend service (no progress simulation - backend will iterate)
            let result;
            try {
                result = await this.utlxService.generateUtlxFromPrompt(request);
            } finally {
                this.stopAiActivityPoll();
                if (!abandoned()) {
                    await this.refreshAiActivity();  // capture the final events
                }
            }

            // The user pressed Stop/Cancel while the AI was working — discard the result.
            if (abandoned()) {
                console.log('[Toolbar] Generation result discarded (stopped by user)');
                return;
            }

            console.log('[Toolbar] Step 5: Backend returned:', result);

            if (!result.success) {
                throw new Error(result.error || 'Generation failed');
            }

            if (!result.utlx) {
                throw new Error('No UTLX code returned');
            }

            // Show validation status
            if (result.validation) {
                if (result.validation.valid) {
                    this.setState({ mcpProgressMessage: `Code generated and validated successfully! (${result.validation.attempts || 1} attempt(s))`, mcpProgressPercent: 0 });
                } else {
                    this.setState({ mcpProgressMessage: `Code generated but validation has issues (${result.validation.attempts || 1} attempt(s))`, mcpProgressPercent: 0 });
                }
            } else {
                this.setState({ mcpProgressMessage: 'Transformation generated successfully!', mcpProgressPercent: 0 });
            }

            // ALWAYS put back the original header, no matter what AI returned
            const generatedBody = this.extractBodyFromGenerated(result.utlx);
            const finalCode = header + '---\n' + generatedBody;

            console.log('[Toolbar] Original header (preserved):', header);
            console.log('[Toolbar] Extracted body:', generatedBody);
            console.log('[Toolbar] Final code:', finalCode);

            // Save prompt to history (skip empty MC "just map it" runs — nothing to recall).
            if (mcpPrompt.trim()) {
                this.addToPromptHistory(mcpPrompt, finalCode);
            }

            // Result quality: surface a clear warning when it didn't validate or
            // failed to run, but STILL show the code (better than nothing — the user
            // can review/fix it). valid + executed≠false → clean.
            const v = result.validation;
            const resultValid = v ? (v.valid && v.executed !== false) : true;
            const resultStatus = !v ? undefined
                : v.executed === false && v.runtimeError ? `Did not run: ${v.runtimeError}`
                : !v.valid ? (v.message || 'Validation failed — this UTLX may not run')
                : undefined;

            // Show preview mode instead of directly updating editor
            this.setState({
                mcpLoading: false,
                mcpProgressMessage: '',
                mcpProgressPercent: 0,
                mcpDialogMode: 'preview',
                generatedCode: finalCode,
                mcpResultValid: resultValid,
                mcpResultStatus: resultStatus,
                mcpAttempts: result.attempts || []
            });
        } catch (error) {
            // A run the user already stopped shouldn't surface its error.
            if (abandoned()) {
                console.log('[Toolbar] Suppressing error from abandoned generation');
                return;
            }
            console.error('[Toolbar] ❌ Generate UTLX error:', error);
            console.error('[Toolbar] Error type:', error instanceof Error ? 'Error' : typeof error);
            console.error('[Toolbar] Error message:', error instanceof Error ? error.message : String(error));
            console.error('[Toolbar] Error stack:', error instanceof Error ? error.stack : 'No stack trace');

            const errorMsg = error instanceof Error ? error.message : String(error);
            this.messageService.error(`Failed to generate UTLX: ${errorMsg}`);
            this.setState({ mcpLoading: false, mcpProgressMessage: '', mcpProgressPercent: 0 });
        }
    }

    private splitHeaderAndBody(editorContent: string): { header: string; body: string } {
        const separatorIndex = editorContent.indexOf('---');

        if (separatorIndex === -1) {
            // No separator found - treat entire content as header
            return {
                header: editorContent.trim() + '\n',
                body: ''
            };
        }

        const header = editorContent.substring(0, separatorIndex).trim() + '\n';
        const body = editorContent.substring(separatorIndex + 3).trim(); // Skip '---'

        return { header, body };
    }

    private extractBodyFromGenerated(generatedCode: string): string {
        let code = generatedCode.trim();

        // Step 1: Find separator (---) and DELETE EVERYTHING BEFORE IT
        const separatorIndex = code.indexOf('---');
        if (separatorIndex !== -1) {
            // Found separator - take everything after it
            code = code.substring(separatorIndex + 3).trim();
        }
        // If no separator, assume entire response is body (LLM followed instructions)

        // Step 2: Remove markdown code blocks at start/end
        code = code.replace(/^```(?:utlx|javascript|js)?\s*/gm, '');
        code = code.replace(/\s*```\s*$/gm, '');

        // Step 3: Remove trailing explanation text (after empty line)
        const lines = code.split('\n');
        let endIndex = lines.length;

        // Find where code ends and explanation starts
        let foundEmptyLine = false;
        for (let i = 0; i < lines.length; i++) {
            const trimmed = lines[i].trim();

            if (trimmed === '') {
                foundEmptyLine = true;
                continue;
            }

            // After empty line, check if it's explanation
            if (foundEmptyLine) {
                if (trimmed.toLowerCase().startsWith('this ') ||
                    trimmed.toLowerCase().startsWith('the ') ||
                    trimmed.toLowerCase().includes('correct') ||
                    trimmed.toLowerCase().includes('will ')) {
                    // This is explanation, cut here
                    endIndex = i;
                    // Find the last non-empty line before explanation
                    while (endIndex > 0 && lines[endIndex - 1].trim() === '') {
                        endIndex--;
                    }
                    break;
                }
                foundEmptyLine = false; // Reset if not explanation
            }
        }

        return lines.slice(0, endIndex).join('\n').trim();
    }

    private extractOutputFormat(editorContent: string): string {
        // Parse the UTLX header to extract output format
        // Header format: "output format" or "output: format"
        const lines = editorContent.split('\n');
        for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('output')) {
                // Extract format: "output json" or "output: json"
                const match = trimmed.match(/^output:?\s+(\w+)/);
                if (match) {
                    return match[1];
                }
            }
            // Stop at separator
            if (trimmed === '---') {
                break;
            }
        }
        return 'json'; // Default
    }

    /**
     * IB05: map a schema format to the DATA (instance) format it describes. In MC mode the
     * generated transformation must output a data instance conforming to the contract — not the
     * schema itself — so the request's output format must be a data format. Non-schema formats
     * pass through unchanged. (Mirrors schemaFormatToInstanceFormat in the frontend contribution.)
     */
    private schemaToDataFormat(fmt: string): string {
        switch ((fmt || '').toLowerCase()) {
            case 'jsch':  return 'json';
            case 'xsd':   return 'xml';
            case 'osch':  return 'odata';
            case 'tsch':  return 'csv';
            case 'avro':  return 'json';
            case 'proto': return 'json';
            case 'usdl':  return 'json';
            default:      return fmt;   // already a data format
        }
    }

    private async handleExecute(): Promise<void> {
        const mode = this.state.currentMode === UTLXMode.EXECUTION ? 'execute' : 'evaluate';
        const modeLabel = this.state.currentMode === UTLXMode.EXECUTION ? 'Executing' : 'Validating';

        this.messageService.info(`${mode === 'execute' ? '▶️' : '✅'} ${modeLabel} transformation...`);

        // Fire event for frontend contribution to coordinate execution
        this.eventService.fireExecuteTransformation({ mode });
    }

    private setState(partial: Partial<ToolbarState>): void {
        console.log('[UTLXToolbar] setState called with:', partial);
        const oldState = { ...this.state };
        this.state = { ...this.state, ...partial };
        console.log('[UTLXToolbar] State updated from:', oldState, 'to:', this.state);
        this.update();
        console.log('[UTLXToolbar] update() called, triggering re-render');
    }

    /**
     * Get current mode (for other widgets to query)
     */
    getCurrentMode(): UTLXMode {
        return this.state.currentMode;
    }
}
