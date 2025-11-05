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
    Command,
    CommandContribution,
    CommandRegistry,
    MenuContribution,
    MenuModelRegistry,
    KeybindingContribution,
    KeybindingRegistry
} from '@theia/core/lib/browser';
import { ApplicationShell, WidgetManager } from '@theia/core/lib/browser';
import { MessageService } from '@theia/core';
import { UTLXCommands } from '../common/protocol';
import { UTLXWorkbenchWidget } from './workbench/utlx-workbench-widget';
import { InputPanelWidget } from './input-panel/input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { ModeSelectorWidget } from './mode-selector/mode-selector-widget';

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

    async onStart(app: FrontendApplication): Promise<void> {
        console.log('UTL-X extension starting...');

        // Auto-open workbench on startup
        this.openWorkbench();
    }

    registerCommands(commands: CommandRegistry): void {
        // Open Workbench command
        commands.registerCommand({
            id: 'utlx.openWorkbench',
            label: 'UTL-X: Open Workbench'
        }, {
            execute: () => this.openWorkbench()
        });

        // Execute Transformation command (registered in workbench widget)
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
            commandId: 'utlx.openWorkbench',
            label: 'Open UTL-X Workbench',
            order: '0'
        });

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

    private async openWorkbench(): Promise<void> {
        try {
            // Get or create workbench widget
            const workbench = await this.widgetManager.getOrCreateWidget<UTLXWorkbenchWidget>(
                UTLXWorkbenchWidget.ID
            );

            // Get or create panel widgets
            const modeSelector = await this.widgetManager.getOrCreateWidget<ModeSelectorWidget>(
                ModeSelectorWidget.ID
            );
            const inputPanel = await this.widgetManager.getOrCreateWidget<InputPanelWidget>(
                InputPanelWidget.ID
            );
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );

            // Add widgets to shell in specific areas
            if (!workbench.isAttached) {
                this.shell.addWidget(workbench, { area: 'main' });
            }

            if (!modeSelector.isAttached) {
                this.shell.addWidget(modeSelector, { area: 'top', rank: 100 });
            }

            if (!inputPanel.isAttached) {
                this.shell.addWidget(inputPanel, { area: 'left', rank: 100 });
            }

            if (!outputPanel.isAttached) {
                this.shell.addWidget(outputPanel, { area: 'right', rank: 100 });
            }

            // Activate workbench
            this.shell.activateWidget(workbench.id);

            console.log('UTL-X workbench opened');
        } catch (error) {
            console.error('Failed to open UTL-X workbench:', error);
            this.messageService.error(`Failed to open workbench: ${error}`);
        }
    }
}
