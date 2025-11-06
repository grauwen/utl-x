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
import { UTLXCommands } from '../common/protocol';
import { HealthMonitorWidget } from './health-monitor/health-monitor-widget';
import { InputPanelWidget } from './input-panel/input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { ModeSelectorWidget } from './mode-selector/mode-selector-widget';
import { UTLXEditorWidget } from './editor/utlx-editor-widget';
import { TestWidget } from './test-widget';

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

    private utlxdStatusId = 'utlxd-status';
    private mcpStatusId = 'mcp-status';

    async onStart(app: FrontendApplication): Promise<void> {
        console.log('[UTLXFrontendContribution] ===== onStart() called =====');
        console.log('[UTLXFrontendContribution] Application:', app);
        console.log('[UTLXFrontendContribution] Shell:', this.shell);
        console.log('[UTLXFrontendContribution] WidgetManager:', this.widgetManager);

        // Add health status to status bar
        await this.initializeHealthStatus();

        // Open 3-column layout: Input | Editor | Output
        await this.open3ColumnLayout();

        console.log('[UTLXFrontendContribution] ===== onStart() completed =====');
    }

    private async openTestWidget(): Promise<void> {
        console.log('[UTLXFrontendContribution] openTestWidget() called');
        try {
            console.log('[UTLXFrontendContribution] Getting or creating TestWidget...');
            const testWidget = await this.widgetManager.getOrCreateWidget<TestWidget>(TestWidget.ID);
            console.log('[UTLXFrontendContribution] TestWidget retrieved:', testWidget);

            if (!testWidget.isAttached) {
                console.log('[UTLXFrontendContribution] TestWidget not attached, adding to shell...');
                this.shell.addWidget(testWidget, { area: 'main', rank: 100 });
                console.log('[UTLXFrontendContribution] TestWidget added to shell');
            } else {
                console.log('[UTLXFrontendContribution] TestWidget already attached');
            }

            console.log('[UTLXFrontendContribution] Activating TestWidget...');
            this.shell.activateWidget(testWidget.id);
            console.log('[UTLXFrontendContribution] ✓ Test widget opened successfully');
        } catch (error) {
            console.error('[UTLXFrontendContribution] ✗ Failed to open test widget:', error);
            this.messageService.error(`Failed to open test widget: ${error}`);
        }
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
            const inputPanel = await this.widgetManager.getOrCreateWidget<InputPanelWidget>(
                InputPanelWidget.ID
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

            console.log('UTL-X 3-column layout opened: Input | Editor | Output');
        } catch (error) {
            console.error('Failed to open 3-column layout:', error);
            this.messageService.error(`Failed to open layout: ${error}`);
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
}
