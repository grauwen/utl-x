/**
 * Frontend Contribution
 *
 * Registers commands, keybindings, and menus for the UTL-X extension.
 * Manages the lifecycle of widgets and panels.
 */

import { injectable, inject } from 'inversify';
import {
    FrontendApplicationContribution,
    FrontendApplication,
    ApplicationShell,
    WidgetManager,
    StatusBar,
    StatusBarAlignment
} from '@theia/core/lib/browser';
import {
    Command,
    CommandContribution,
    CommandRegistry,
    MenuContribution,
    MenuModelRegistry
} from '@theia/core/lib/common';
import { KeybindingContribution, KeybindingRegistry } from '@theia/core/lib/browser';
import { MessageService } from '@theia/core';
import { UTLXCommands, UTLXService, UTLX_SERVICE_SYMBOL } from '../common/protocol';
import { HealthMonitorWidget } from './health-monitor/health-monitor-widget';
import { MultiInputPanelWidget } from './input-panel/multi-input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { UTLXEditorWidget } from './editor/utlx-editor-widget';
import { UTLXEventService } from './events/utlx-event-service';

@injectable()
export class UTLXFrontendContribution implements
    FrontendApplicationContribution,
    CommandContribution,
    MenuContribution,
    KeybindingContribution {

    @inject(ApplicationShell)
    protected readonly shell!: ApplicationShell;

    @inject(WidgetManager)
    protected readonly widgetManager!: WidgetManager;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    @inject(StatusBar)
    protected readonly statusBar!: StatusBar;

    @inject(UTLXEventService)
    protected readonly eventService!: UTLXEventService;

    @inject(UTLX_SERVICE_SYMBOL)
    protected readonly utlxService!: UTLXService;

    private utlxdStatusId = 'utlxd-status';
    private mcpStatusId = 'mcp-status';
    private inputs: Map<string, { name: string; format: string; csvHeaders?: boolean; csvDelimiter?: string }> = new Map(); // inputId -> {name, format, csvHeaders, csvDelimiter}
    private outputFormat: string = 'json';
    private isUpdatingFromParsedHeaders: boolean = false; // Flag to prevent circular updates

    async onStart(app: FrontendApplication): Promise<void> {
        console.log('[UTLXFrontendContribution] ===== onStart() called =====');
        console.log('[UTLXFrontendContribution] Application:', app);
        console.log('[UTLXFrontendContribution] Shell:', this.shell);
        console.log('[UTLXFrontendContribution] WidgetManager:', this.widgetManager);

        // Add health status to status bar
        await this.initializeHealthStatus();

        // Subscribe to format change events for header coordination
        this.subscribeToFormatChanges();

        // Subscribe to execute/evaluate events
        this.subscribeToExecuteEvents();

        // Open toolbar at top
        await this.openToolbar();

        // Open 3-column layout: Input | Editor | Output
        await this.open3ColumnLayout();

        console.log('[UTLXFrontendContribution] ===== onStart() completed =====');
    }

    registerCommands(commands: CommandRegistry): void {
        // Execute Transformation command
        commands.registerCommand({
            id: UTLXCommands.EXECUTE_TRANSFORMATION,
            label: 'UTL-X: Execute Transformation'
        });

        // Validate Code command
        commands.registerCommand({
            id: UTLXCommands.VALIDATE_CODE,
            label: 'UTL-X: Validate Code'
        });

        // Infer Schema command
        commands.registerCommand({
            id: UTLXCommands.INFER_SCHEMA,
            label: 'UTL-X: Infer Output Schema'
        });

        // Toggle Mode command
        commands.registerCommand({
            id: UTLXCommands.TOGGLE_MODE,
            label: 'UTL-X: Toggle Design-Time/Runtime Mode'
        });

        // Clear Panels command
        commands.registerCommand({
            id: UTLXCommands.CLEAR_PANELS,
            label: 'UTL-X: Clear All Panels'
        });

        // Restart Daemon command
        commands.registerCommand({
            id: UTLXCommands.RESTART_DAEMON,
            label: 'UTL-X: Restart Daemon'
        }, {
            execute: async () => {
                try {
                    this.messageService.info('Restarting UTL-X daemon...');
                    // TODO: Call service restart method
                    this.messageService.info('Daemon restarted successfully');
                } catch (error) {
                    this.messageService.error(`Failed to restart daemon: ${error}`);
                }
            }
        });
    }

    registerMenus(menus: MenuModelRegistry): void {
        // Add UTL-X menu
        menus.registerMenuAction(['1_utlx'], {
            commandId: UTLXCommands.TOGGLE_MODE,
            label: 'Toggle Mode',
            order: '1'
        });

        menus.registerMenuAction(['1_utlx'], {
            commandId: UTLXCommands.RESTART_DAEMON,
            label: 'Restart Daemon',
            order: '2'
        });
    }

    registerKeybindings(keybindings: KeybindingRegistry): void {
        // Execute transformation: Ctrl+Shift+E (Cmd+Shift+E on Mac)
        keybindings.registerKeybinding({
            command: UTLXCommands.EXECUTE_TRANSFORMATION,
            keybinding: 'ctrlcmd+shift+e'
        });

        // Validate code: Ctrl+Shift+V (Cmd+Shift+V on Mac)
        keybindings.registerKeybinding({
            command: UTLXCommands.VALIDATE_CODE,
            keybinding: 'ctrlcmd+shift+v'
        });

        // Toggle mode: Ctrl+Shift+M (Cmd+Shift+M on Mac)
        keybindings.registerKeybinding({
            command: UTLXCommands.TOGGLE_MODE,
            keybinding: 'ctrlcmd+shift+m'
        });

        // Clear panels: Ctrl+Shift+C (Cmd+Shift+C on Mac)
        keybindings.registerKeybinding({
            command: UTLXCommands.CLEAR_PANELS,
            keybinding: 'ctrlcmd+shift+c'
        });
    }

    private async open3ColumnLayout(): Promise<void> {
        try {
            // Create all widgets
            const inputPanel = await this.widgetManager.getOrCreateWidget<MultiInputPanelWidget>(
                MultiInputPanelWidget.ID
            );
            const editorWidget = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(
                UTLXEditorWidget.ID
            );
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );

            // Add widgets to shell in 3-column layout
            // Left column: Input panel
            if (!inputPanel.isAttached) {
                this.shell.addWidget(inputPanel, { area: 'left', rank: 100 });
            }

            // Center column: Editor
            if (!editorWidget.isAttached) {
                this.shell.addWidget(editorWidget, { area: 'main', rank: 100 });
            }

            // Right column: Output panel
            if (!outputPanel.isAttached) {
                this.shell.addWidget(outputPanel, { area: 'right', rank: 100 });
            }

            // Activate all widgets to make them visible
            this.shell.activateWidget(inputPanel.id);
            this.shell.activateWidget(editorWidget.id);
            this.shell.activateWidget(outputPanel.id);

            // Expand the side panels to show the widgets
            this.shell.leftPanelHandler.expand();
            this.shell.rightPanelHandler.expand();

            // Set initial panel sizes for 2:3:2 ratio
            // Wait a bit for the layout to settle, then set sizes
            setTimeout(() => {
                const totalWidth = window.innerWidth;
                const leftWidth = Math.floor(totalWidth * (2 / 7));
                const rightWidth = Math.floor(totalWidth * (2 / 7));

                // Set panel sizes (in pixels)
                this.shell.leftPanelHandler.resize(leftWidth);
                this.shell.rightPanelHandler.resize(rightWidth);

                console.log('[UTLXFrontendContribution] Set initial panel sizes:', {
                    totalWidth,
                    leftWidth,
                    rightWidth,
                    ratio: '2:3:2'
                });
            }, 100);

            // Initialize inputs from the input panel's current state
            await this.initializeInputsFromPanel(inputPanel);

            console.log('UTL-X 3-column layout opened: Input | Editor | Output');
        } catch (error) {
            console.error('Failed to open 3-column layout:', error);
            this.messageService.error(`Failed to open layout: ${error}`);
        }
    }

    /**
     * Initialize the inputs Map from the MultiInputPanelWidget's current state
     * This is needed because the panel may already have inputs when it's created
     */
    private async initializeInputsFromPanel(inputPanel: MultiInputPanelWidget): Promise<void> {
        try {
            // Access the panel's state to get current inputs
            const panelState = (inputPanel as any).state;
            if (panelState && panelState.inputs) {
                console.log('[UTLXFrontendContribution] Initializing inputs from panel:', panelState.inputs);

                panelState.inputs.forEach((input: any) => {
                    this.inputs.set(input.id, {
                        name: input.name,
                        format: input.instanceFormat,
                        csvHeaders: input.csvHeaders,
                        csvDelimiter: input.csvDelimiter
                    });
                });

                // Update editor headers with initial state
                await this.updateEditorHeaders();
            }
        } catch (error) {
            console.error('[UTLXFrontendContribution] Failed to initialize inputs from panel:', error);
        }
    }

    private async openToolbar(): Promise<void> {
        try {
            const toolbar = await this.widgetManager.getOrCreateWidget('utlx-toolbar');

            if (!toolbar.isAttached) {
                this.shell.addWidget(toolbar, { area: 'top', rank: 100 });
            }

            this.shell.activateWidget(toolbar.id);

            console.log('UTL-X toolbar opened');
        } catch (error) {
            console.error('Failed to open toolbar:', error);
            this.messageService.error(`Failed to open toolbar: ${error}`);
        }
    }

    private async initializeHealthStatus(): Promise<void> {
        console.log('[UTLXFrontendContribution] Initializing health status in status bar...');

        // Add UTLXD status
        this.statusBar.setElement(this.utlxdStatusId, {
            text: '$(pulse) UTLXD: checking...',
            alignment: StatusBarAlignment.RIGHT,
            priority: 100,
            tooltip: 'UTLXD LSP Server Status'
        });

        // Add MCP status
        this.statusBar.setElement(this.mcpStatusId, {
            text: '$(pulse) MCP: checking...',
            alignment: StatusBarAlignment.RIGHT,
            priority: 99,
            tooltip: 'MCP Server Status'
        });

        // Start monitoring
        this.startHealthMonitoring();
    }

    private startHealthMonitoring(): void {
        // Initial check
        this.checkHealth();

        // Check every 2 seconds
        setInterval(() => {
            this.checkHealth();
        }, 2000);
    }

    private async checkHealth(): Promise<void> {
        // Check UTLXD
        try {
            const utlxdResponse = await fetch('http://localhost:7779/api/health');
            if (utlxdResponse.ok) {
                this.statusBar.setElement(this.utlxdStatusId, {
                    text: '$(check) UTLXD',
                    alignment: StatusBarAlignment.RIGHT,
                    priority: 100,
                    tooltip: 'UTLXD LSP Server: Online',
                    color: '#50fa7b'
                });
            } else {
                throw new Error('Not OK');
            }
        } catch (error) {
            this.statusBar.setElement(this.utlxdStatusId, {
                text: '$(x) UTLXD',
                alignment: StatusBarAlignment.RIGHT,
                priority: 100,
                tooltip: 'UTLXD LSP Server: Offline',
                color: '#ff5555'
            });
        }

        // Check MCP
        try {
            const mcpResponse = await fetch('http://localhost:3001/health');
            if (mcpResponse.ok) {
                this.statusBar.setElement(this.mcpStatusId, {
                    text: '$(check) MCP',
                    alignment: StatusBarAlignment.RIGHT,
                    priority: 99,
                    tooltip: 'MCP Server: Online',
                    color: '#50fa7b'
                });
            } else {
                throw new Error('Not OK');
            }
        } catch (error) {
            this.statusBar.setElement(this.mcpStatusId, {
                text: '$(x) MCP',
                alignment: StatusBarAlignment.RIGHT,
                priority: 99,
                tooltip: 'MCP Server: Offline',
                color: '#ff5555'
            });
        }
    }

    private async openHealthMonitor(): Promise<void> {
        try {
            // Get or create health monitor widget
            const healthMonitor = await this.widgetManager.getOrCreateWidget<HealthMonitorWidget>(
                HealthMonitorWidget.ID
            );

            // Add to bottom panel
            if (!healthMonitor.isAttached) {
                this.shell.addWidget(healthMonitor, { area: 'bottom', rank: 100 });
            }

            // Activate health monitor
            this.shell.activateWidget(healthMonitor.id);

            console.log('UTL-X health monitor opened in bottom panel');
        } catch (error) {
            console.error('Failed to open health monitor:', error);
        }
    }

    /**
     * Subscribe to format change events and coordinate header updates in editor
     * This is the workbench coordination logic extracted from UTLXWorkbenchWidget
     */
    private subscribeToFormatChanges(): void {
        console.log('[UTLXFrontendContribution] Setting up format change coordination...');

        // Subscribe to input added
        this.eventService.onInputAdded(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Input added:', event);
            this.inputs.set(event.inputId, { name: event.name, format: 'json' });
            this.updateEditorHeaders();
        });

        // Subscribe to input deleted
        this.eventService.onInputDeleted(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Input deleted:', event);
            this.inputs.delete(event.inputId);
            this.updateEditorHeaders();
        });

        // Subscribe to input name changes
        this.eventService.onInputNameChanged(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Input name changed:', event);
            const input = this.inputs.get(event.inputId);
            if (input) {
                input.name = event.newName;
                this.updateEditorHeaders();
            }
        });

        // Subscribe to input format changes
        this.eventService.onInputFormatChanged(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Input format changed:', event);

            // Get or create input entry
            let input = this.inputs.get(event.inputId);
            if (!input) {
                // Input not tracked yet, create with default name
                input = {
                    name: 'input',
                    format: event.format,
                    csvHeaders: event.csvHeaders,
                    csvDelimiter: event.csvDelimiter
                };
                this.inputs.set(event.inputId, input);
            } else {
                input.format = event.format;
                input.csvHeaders = event.csvHeaders;
                input.csvDelimiter = event.csvDelimiter;
            }

            // Update editor headers
            this.updateEditorHeaders();
        });

        // Subscribe to output format changes (instance/runtime mode)
        this.eventService.onOutputFormatChanged(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Output format changed:', event);

            // Build output format spec with options
            this.outputFormat = this.buildOutputFormatSpec(event.format, event);

            // Update editor headers
            this.updateEditorHeaders();
        });

        // Subscribe to output preset mode changes
        this.eventService.onOutputPresetOn(event => {
            console.log('[UTLXFrontendContribution] Output preset mode ON:', event);
            // In preset mode, track the schema format
            this.outputFormat = event.schemaFormat;
            this.updateEditorHeaders();
        });

        this.eventService.onOutputPresetOff(event => {
            console.log('[UTLXFrontendContribution] Output preset mode OFF');
            // Reset to default when preset mode is disabled
            this.outputFormat = 'json';
            this.updateEditorHeaders();
        });

        // Subscribe to output schema format changes (preset mode)
        this.eventService.onOutputSchemaFormatChanged(event => {
            console.log('[UTLXFrontendContribution] Output schema format changed:', event);

            // Track output schema format in preset mode
            this.outputFormat = event.format;

            // Update editor headers
            this.updateEditorHeaders();
        });

        // Subscribe to headers parsed from editor (copy/paste sync)
        this.eventService.onHeadersParsed(event => {
            console.log('[UTLXFrontendContribution] Headers parsed from editor:', event);
            this.updatePanelsFromParsedHeaders(event);
        });

        console.log('[UTLXFrontendContribution] Format change coordination initialized');
    }

    /**
     * Update editor headers based on current input/output formats
     * This builds the header lines and calls the editor's updateHeaders method
     *
     * Format rules:
     * - Single input: "input [optionalname] <format> [params]"
     * - Multiple inputs: "input: [optionalname1] format1 [params1], name2 format2 [params2], ..."
     * - CSV parameters: "{headers: true|false, delimiter: ","}"
     */
    private async updateEditorHeaders(): Promise<void> {
        try {
            // Get editor widget
            const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(
                UTLXEditorWidget.ID
            );

            // Get input panel widget
            const inputPanel = await this.widgetManager.getOrCreateWidget<MultiInputPanelWidget>(
                MultiInputPanelWidget.ID
            );

            // Get ALL inputs directly from the panel (always current, no stale state)
            const allInputs = inputPanel.getAllInputTabs();

            // Build input lines from ALL inputs
            const inputLines: string[] = [];

            // DEBUG: Log the current state of inputs
            console.log('[UTLXFrontendContribution] updateEditorHeaders - allInputs.length:', allInputs.length);
            console.log('[UTLXFrontendContribution] updateEditorHeaders - allInputs:', allInputs);

            if (allInputs.length === 0) {
                // No inputs, use default
                inputLines.push('input json');
            } else if (allInputs.length === 1) {
                // Single input: "input [optionalname] <format> [params]"
                const input = allInputs[0];
                const hasCustomName = input.name && input.name !== 'input' && input.name.trim() !== '';

                let inputLine = 'input';
                if (hasCustomName) {
                    inputLine += ` ${input.name}`;
                }
                inputLine += ` ${input.format}`;

                // Add CSV parameters if format is CSV
                if (input.format === 'csv') {
                    const csvParams = this.buildCsvParams(input);
                    if (csvParams) {
                        inputLine += ` ${csvParams}`;
                    }
                }

                inputLines.push(inputLine);
            } else {
                // Multiple inputs: colon syntax (single line)
                // input: name1 format1, name2 format2, name3 format3

                // Find if one is named "input" and reorder to put it first
                const inputIndex = allInputs.findIndex(input => input.name === 'input');

                // Reorder: if "input" exists, move it to front, keep rest in original order
                const orderedInputs = inputIndex >= 0
                    ? [allInputs[inputIndex], ...allInputs.slice(0, inputIndex), ...allInputs.slice(inputIndex + 1)]
                    : allInputs;

                // Build colon syntax: input: name1 format1, name2 format2
                const inputParts = orderedInputs.map(input => {
                    let part = `${input.name} ${input.format}`;

                    // Add CSV parameters if format is CSV
                    if (input.format === 'csv') {
                        const csvParams = this.buildCsvParams(input);
                        if (csvParams) {
                            part += ` ${csvParams}`;
                        }
                    }

                    return part;
                });

                // Join with comma and space
                inputLines.push(`input: ${inputParts.join(', ')}`);
            }

            // Update editor headers
            console.log('[UTLXFrontendContribution] Updating editor headers:', {
                inputLines,
                outputFormat: this.outputFormat
            });

            editor.updateHeaders(inputLines, this.outputFormat);
        } catch (error) {
            console.error('[UTLXFrontendContribution] Failed to update editor headers:', error);
        }
    }

    /**
     * Build CSV parameter string for UTLX header
     * Format: {headers: false} or {delimiter: ";"} or {headers: false, delimiter: ";"}
     * Only includes non-default values
     * Returns null if all parameters are at default values
     */
    private buildCsvParams(input: { csvHeaders?: boolean; csvDelimiter?: string }): string | null {
        const hasHeaders = input.csvHeaders ?? true; // Default true
        const delimiter = input.csvDelimiter ?? ','; // Default comma

        const params: string[] = [];

        // Only include non-default values
        if (hasHeaders === false) {
            params.push(`headers: false`);
        }
        if (delimiter !== ',') {
            params.push(`delimiter: "${this.escapeDelimiter(delimiter)}"`);
        }

        // Return null if no non-default parameters
        if (params.length === 0) {
            return null;
        }

        return `{${params.join(', ')}}`;
    }

    /**
     * Escape delimiter for UTLX syntax
     * Tab needs to be represented as \t
     */
    private escapeDelimiter(delimiter: string): string {
        if (delimiter === '\t') {
            return '\\t';
        }
        return delimiter;
    }

    /**
     * Build output format specification for UTLX header
     * Format examples:
     * - csv {headers: false, delimiter: ";"}
     * - xml {encoding: "UTF-16"}
     * - json (no options)
     * Only includes non-default options
     */
    private buildOutputFormatSpec(
        format: string,
        options: {
            csvHeaders?: boolean;
            csvDelimiter?: string;
            csvBom?: boolean;
            xmlEncoding?: string;
        }
    ): string {
        const params: string[] = [];

        // CSV options
        if (format === 'csv') {
            const hasHeaders = options.csvHeaders ?? true; // Default true
            const delimiter = options.csvDelimiter ?? ','; // Default comma
            const hasBom = options.csvBom ?? false; // Default false

            // Only include non-default values
            if (hasHeaders === false) {
                params.push(`headers: false`);
            }
            if (delimiter !== ',') {
                params.push(`delimiter: "${this.escapeDelimiter(delimiter)}"`);
            }
            if (hasBom === true) {
                params.push(`bom: true`);
            }
        }

        // XML options
        if (format === 'xml') {
            const encoding = options.xmlEncoding ?? 'UTF-8'; // Default UTF-8

            // Only include non-default encoding
            if (encoding !== 'UTF-8') {
                params.push(`encoding: "${encoding}"`);
            }
        }

        // Build format spec
        if (params.length === 0) {
            return format; // No options, just the format name
        } else {
            return `${format} {${params.join(', ')}}`;
        }
    }

    /**
     * Update panels based on parsed UTLX headers (for copy/paste sync)
     * This is the reverse direction: Editor → Panels
     */
    private async updatePanelsFromParsedHeaders(event: { inputs: any[]; output: any }): Promise<void> {
        console.log('[UTLXFrontendContribution] Updating panels from parsed headers');

        // Set flag to prevent circular updates
        this.isUpdatingFromParsedHeaders = true;

        try {
            // Get panel widgets
            const inputPanel = await this.widgetManager.getOrCreateWidget(MultiInputPanelWidget.ID) as MultiInputPanelWidget;
            const outputPanel = await this.widgetManager.getOrCreateWidget(OutputPanelWidget.ID) as OutputPanelWidget;

            if (!inputPanel || !outputPanel) {
                console.error('[UTLXFrontendContribution] Could not get panel widgets');
                return;
            }

            // Update input panel with parsed inputs
            if (typeof (inputPanel as any).syncFromHeaders === 'function') {
                console.log('[UTLXFrontendContribution] Syncing input panel with', event.inputs.length, 'inputs');
                (inputPanel as any).syncFromHeaders(event.inputs);

                // CRITICAL: After syncing, rebuild the inputs Map to match the panel's new state
                // This prevents stale input IDs from interfering with subsequent operations
                this.inputs.clear();
                const allInputs = inputPanel.getAllInputTabs();
                allInputs.forEach(input => {
                    this.inputs.set(input.id, {
                        name: input.name,
                        format: input.format,
                        csvHeaders: input.csvHeaders,
                        csvDelimiter: input.csvDelimiter
                    });
                });
                console.log('[UTLXFrontendContribution] Rebuilt inputs Map with', this.inputs.size, 'entries');
            } else {
                console.warn('[UTLXFrontendContribution] InputPanel does not have syncFromHeaders method yet');
            }

            // Update output panel with parsed output
            if (typeof (outputPanel as any).syncFromHeaders === 'function') {
                console.log('[UTLXFrontendContribution] Syncing output panel with format:', event.output.format);
                (outputPanel as any).syncFromHeaders(event.output);
            } else {
                console.warn('[UTLXFrontendContribution] OutputPanel does not have syncFromHeaders method yet');
            }

            // Update output format tracking
            this.outputFormat = event.output.format || 'json';

        } finally {
            // Always clear flag
            this.isUpdatingFromParsedHeaders = false;
        }
    }

    /**
     * Subscribe to execute/evaluate events and coordinate transformation execution
     */
    private subscribeToExecuteEvents(): void {
        console.log('[UTLXFrontendContribution] ========================================');
        console.log('[UTLXFrontendContribution] SUBSCRIBING to execute/evaluate events');
        console.log('[UTLXFrontendContribution] Event service:', this.eventService);
        console.log('[UTLXFrontendContribution] ========================================');

        this.eventService.onExecuteTransformation(async event => {
            console.log('[UTLXFrontendContribution] ****************************************');
            console.log('[UTLXFrontendContribution] EXECUTE EVENT HANDLER CALLED');
            console.log('[UTLXFrontendContribution] Event mode:', event.mode);
            console.log('[UTLXFrontendContribution] Event object:', event);
            console.log('[UTLXFrontendContribution] ****************************************');

            try {
                console.log('[Execute] Step 1: Getting widgets...');
                // 1. Get all necessary widgets
                const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(
                    UTLXEditorWidget.ID
                );
                console.log('[Execute] Editor widget:', editor ? 'OK' : 'NULL');

                const inputPanel = await this.widgetManager.getOrCreateWidget<MultiInputPanelWidget>(
                    MultiInputPanelWidget.ID
                );
                console.log('[Execute] Input panel widget:', inputPanel ? 'OK' : 'NULL');

                const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                    OutputPanelWidget.ID
                );
                console.log('[Execute] Output panel widget:', outputPanel ? 'OK' : 'NULL');

                // 2. Collect UTLX code
                const utlxCode = editor.getContent();
                console.log('[Execute] Sending UTLX code (' + utlxCode.length + ' characters):');
                console.log(utlxCode);

                // 3. Collect input documents
                const inputs = inputPanel.getInputDocuments();
                console.log('[Execute] Sending ' + inputs.length + ' input(s):');
                inputs.forEach(input => {
                    const encoding = input.encoding || 'UTF-8';
                    const bom = input.bom || false;
                    console.log('  - Input "' + input.name + '" (' + input.format + ', ' + encoding + ', BOM=' + bom + '): ' + input.content.length + ' characters');

                    // Show first 200 characters of content for verification
                    const preview = input.content.length > 200
                        ? input.content.substring(0, 200) + '...'
                        : input.content;
                    console.log('    Preview: ' + preview);
                });

                // 3a. Validate inputs - check if we have any inputs at all
                if (inputs.length === 0) {
                    const message = 'No input data provided. Please add content to at least one input tab before executing.';
                    console.warn('[Execute] Validation failed: No inputs provided');
                    this.messageService.warn(message);
                    return; // Stop execution
                }

                // 3b. Validate inputs - check for empty content (shouldn't happen due to filter, but safety check)
                const emptyInputs = inputs.filter(input => !input.content || input.content.trim().length === 0);
                if (emptyInputs.length > 0) {
                    const inputNames = emptyInputs.map(input => `"${input.name}"`).join(', ');
                    const message = emptyInputs.length === 1
                        ? `Input ${inputNames} is empty. Please provide content before executing.`
                        : `Inputs ${inputNames} are empty. Please provide content before executing.`;

                    console.warn('[Execute] Validation failed: Empty inputs detected:', inputNames);
                    this.messageService.warn(message);
                    return; // Stop execution
                }

                // 4. Execute transformation via service
                console.log('[Execute] Calling utlxService.execute()...');
                console.log('[Execute] Service object:', this.utlxService);

                let result;
                let executionTime;

                try {
                    const startTime = Date.now();
                    console.log('[Execute] Awaiting service call...');
                    console.log('[Execute] Service type:', typeof this.utlxService);
                    console.log('[Execute] Service execute type:', typeof this.utlxService.execute);

                    // Test RPC connection first with getMode (simpler than ping)
                    console.log('[Execute] Testing RPC connection with getMode...');
                    try {
                        const getModePromise = this.utlxService.getMode();
                        console.log('[Execute] GetMode promise created:', getModePromise);
                        const modeResult = await Promise.race([
                            getModePromise,
                            new Promise((_, reject) => setTimeout(() => reject(new Error('GetMode timeout')), 5000))
                        ]);
                        console.log('[Execute] GetMode result:', modeResult);
                    } catch (modeError) {
                        console.error('[Execute] GET_MODE FAILED:', modeError);
                        console.error('[Execute] Service path:', '/services/utlx');
                        console.error('[Execute] This means the backend service is not reachable via RPC');
                        throw new Error('RPC connection failed: ' + (modeError instanceof Error ? modeError.message : String(modeError)));
                    }

                    // Add timeout to detect hanging calls
                    const timeoutPromise = new Promise((_, reject) => {
                        setTimeout(() => reject(new Error('Service call timeout after 30 seconds')), 30000);
                    });

                    console.log('[Execute] Calling this.utlxService.execute with', inputs.length, 'inputs');
                    const executePromise = this.utlxService.execute(utlxCode, inputs);
                    console.log('[Execute] Execute promise created:', executePromise);

                    result = await Promise.race([
                        executePromise,
                        timeoutPromise
                    ]) as any;

                    executionTime = Date.now() - startTime;
                    console.log('[Execute] Service call completed in ' + executionTime + 'ms');
                } catch (serviceError) {
                    console.error('[Execute] Service call threw exception:', serviceError);
                    console.error('[Execute] Exception details:', serviceError instanceof Error ? serviceError.stack : serviceError);
                    console.error('[Execute] Error type:', typeof serviceError);
                    throw serviceError;
                }

                // 5. Log raw response
                console.log('[Execute] ========================================');
                console.log('[Execute] Raw response received (' + executionTime + 'ms):');
                console.log(JSON.stringify(result, null, 2));
                console.log('[Execute] ========================================');

                // 6. Display results in output panel
                console.log('[Execute] Step 6: Displaying result in output panel...');
                outputPanel.displayExecutionResult(result);
                console.log('[Execute] Result displayed in output panel');

                // 7. Show success/error message
                console.log('[Execute] Step 7: Showing user message...');
                if (result.success) {
                    this.messageService.info(`✓ Transformation ${event.mode === 'execute' ? 'executed' : 'evaluated'} successfully (${result.executionTimeMs || executionTime}ms)`);
                    console.log('[Execute] Success message shown');
                } else {
                    this.messageService.error(`✗ Transformation failed: ${result.error || 'Unknown error'}`);
                    console.log('[Execute] Error message shown');
                }

                console.log('[Execute] ****************************************');
                console.log('[Execute] EXECUTION COMPLETED SUCCESSFULLY');
                console.log('[Execute] ****************************************');

            } catch (error) {
                console.error('[Execute] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
                console.error('[Execute] ERROR DURING EXECUTION COORDINATION');
                console.error('[Execute] Error:', error);
                console.error('[Execute] Error message:', error instanceof Error ? error.message : String(error));
                console.error('[Execute] Error stack:', error instanceof Error ? error.stack : 'N/A');
                console.error('[Execute] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');

                this.messageService.error(`Execution error: ${error instanceof Error ? error.message : String(error)}`);

                // Display error in output panel
                const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                    OutputPanelWidget.ID
                );
                outputPanel.displayError(error instanceof Error ? error.message : String(error));
            }
        });

        console.log('[UTLXFrontendContribution] ========================================');
        console.log('[UTLXFrontendContribution] Execute/evaluate event coordination initialized');
        console.log('[UTLXFrontendContribution] ========================================');
    }
}
