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
    StatusBarAlignment,
    CommonMenus,
    AbstractDialog,
    QuickInputService
} from '@theia/core/lib/browser';
import { FileDialogService, OpenFileDialogProps, SaveFileDialogProps } from '@theia/filesystem/lib/browser';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import { EditorManager } from '@theia/editor/lib/browser';
import { WorkspaceService } from '@theia/workspace/lib/browser';
import { TerminalService } from '@theia/terminal/lib/browser/base/terminal-service';
import URI from '@theia/core/lib/common/uri';
import {
    Command,
    CommandContribution,
    CommandRegistry,
    MenuContribution,
    MenuModelRegistry
} from '@theia/core/lib/common';
import { KeybindingContribution, KeybindingRegistry } from '@theia/core/lib/browser';
import { MessageService } from '@theia/core';
import { UTLXCommands, UTLXService, UTLX_SERVICE_SYMBOL, UTLXMode, InputDocument, DataFormat } from '../common/protocol';
import { HealthMonitorWidget } from './health-monitor/health-monitor-widget';
import { MultiInputPanelWidget } from './input-panel/multi-input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { UTLXEditorWidget } from './editor/utlx-editor-widget';
import { DocsDialog } from './docs/docs-dialog';
import { UTLXEventService } from './events/utlx-event-service';
import { parseUTLXHeaders } from './parser/utlx-header-parser';
import { buildSavePlan, dataExt, SaveInput, extToSchemaFormat, parseTransformYamlRefs } from './bundle/transformation-io';
import { inferSchemaFromJson, inferSchemaFromYaml, inferSchemaFromXml, inferEdmxFromOData, inferTableSchemaFromCsv, formatSchema } from './utils/schema-inferrer';
import { generateScaffoldFromStructure } from './utils/scaffold-generator';
import { compareSchemas } from './utils/schema-comparator';
import {
    parseJsonSchemaToFieldTree,
    parseXsdToFieldTree,
    parseOSchToFieldTree,
    parseTschToFieldTree
} from './utils/schema-field-tree-parser';
import { getFileDialogMode, setFileDialogMode, getNameOnLoadMode, setNameOnLoadMode } from './utils/feature-flags';

/** Command id for the runtime file-Load dialog "semaphore" (toggled from the status bar). */
const TOGGLE_FILE_DIALOG_MODE_COMMAND = 'utlx.toggleFileDialogMode';

/** Command id for the runtime "name on load" semaphore (inherit the file's name vs keep the slot name). */
const TOGGLE_NAME_ON_LOAD_MODE_COMMAND = 'utlx.toggleNameOnLoadMode';

/**
 * Where the Help → Demo scripts live: the **version-controlled repo** `playwright/` dir — NOT the
 * Theia workspace (which holds the user's `.utlx` work and shouldn't carry tooling). This is the
 * single source of truth (`cli-demo.sh`, `run-demo.sh`). The path is resolved on the BACKEND host
 * (where the integrated terminal's shell runs). Promote to a preference later if it needs to vary
 * per machine. The Demo IDE kiosk can only live here anyway (it needs node_modules + Chromium).
 */
const DEMO_DIR = '/Users/magr/data/mapping/github-git/utl-x/theia-extension/playwright';

/**
 * Minimal OK-only modal alert. Theia's `ConfirmDialog` always renders an OK **and** a Cancel button;
 * for a pure warning there's nothing to "cancel", so we use a one-button dialog (plus the title 'x').
 * Preserves newlines in the message via `white-space: pre-wrap`.
 */
class AlertDialog extends AbstractDialog<void> {
    constructor(title: string, message: string) {
        super({ title });
        const body = document.createElement('div');
        body.style.whiteSpace = 'pre-wrap';
        body.style.maxWidth = '34rem';
        body.textContent = message;
        this.contentNode.appendChild(body);
        this.appendAcceptButton('OK');
    }
    get value(): void { return undefined; }
}

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

    @inject(FileDialogService)
    protected readonly fileDialogService!: FileDialogService;

    @inject(FileService)
    protected readonly fileService!: FileService;

    @inject(EditorManager)
    protected readonly editorManager!: EditorManager;

    @inject(TerminalService)
    protected readonly terminalService!: TerminalService;

    @inject(WorkspaceService)
    protected readonly workspaceService!: WorkspaceService;

    @inject(MenuModelRegistry)
    protected readonly menus!: MenuModelRegistry;

    @inject(QuickInputService)
    protected readonly quickInput!: QuickInputService;

    // The currently-open UTLX project (set by Open UTLX Project), so the Edit-config commands know
    // where transform.yaml (in the tx dir) and engine.yaml (project root) live.
    private currentProjectRoot?: URI;
    private currentTxDir?: URI;

    private utlxdStatusId = 'utlxd-status';
    private mcpStatusId = 'mcp-status';
    private fileDialogModeId = 'utlx-file-dialog-mode';
    private nameOnLoadModeId = 'utlx-name-on-load-mode';
    private inputs: Map<string, { name: string; format: string; csvHeaders?: boolean; csvDelimiter?: string }> = new Map(); // inputId -> {name, format, csvHeaders, csvDelimiter}
    private outputFormat: string = 'json';
    private outputName: string = ''; // empty / 'output' => no custom name in header
    private currentMode: UTLXMode = UTLXMode.EXECUTION;
    private isUpdatingFromParsedHeaders: boolean = false; // Flag to prevent circular updates
    private canvasFullScreen: boolean = false;

    async onStart(app: FrontendApplication): Promise<void> {
        console.log('[UTLXFrontendContribution] ===== onStart() called =====');
        console.log('[UTLXFrontendContribution] Application:', app);
        console.log('[UTLXFrontendContribution] Shell:', this.shell);
        console.log('[UTLXFrontendContribution] WidgetManager:', this.widgetManager);

        // Add health status to status bar
        await this.initializeHealthStatus();

        // Show the file-Load dialog mode toggle in the status bar (bottom).
        this.updateFileDialogModeStatus();

        // Show the "name on load" (inherit / keep) toggle in the status bar (bottom).
        this.updateNameOnLoadModeStatus();

        // Subscribe to format change events for header coordination
        this.subscribeToFormatChanges();

        // Subscribe to execute/evaluate events
        this.subscribeToExecuteEvents();

        // Open toolbar at top
        await this.openToolbar();

        // Relabel the built-in @theia/workspace "Open Recent Workspace…" File-menu entry to
        // "Open Recent UTLX Project…" — in our unified model a workspace IS a .utlxp project (IF22).
        // Done here (after MenuModelRegistry.onStart has run all registerMenus) so it's order-safe;
        // command/behavior are unchanged (still workspace:openRecent). (The Command Palette keeps the
        // original command label.)
        this.relabelOpenRecent();

        // Open 3-column layout: Input | Editor | Output
        await this.open3ColumnLayout();

        // If the Theia workspace IS a .utlxp project (set by "Open UTL-X Project", which reloads into
        // the project), auto-load the transformation + Message-Contract mode (IF03). Fire-and-forget —
        // it awaits workspaceService.ready internally.
        void this.maybeLoadProjectWorkspace();

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
            label: 'UTL-X: Toggle Execution/Message Contract Mode'
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

        // File-load dialog mode (the runtime "semaphore") — toggled by clicking the status-bar
        // item (bottom bar). Theia dialog = workspace/bundle files + full path; browser = upload.
        commands.registerCommand({
            id: TOGGLE_FILE_DIALOG_MODE_COMMAND,
            label: 'UTL-X: Toggle File Load Dialog (Theia / Browser)'
        }, {
            execute: () => {
                const next = getFileDialogMode() === 'theia' ? 'browser' : 'theia';
                setFileDialogMode(next);
                this.updateFileDialogModeStatus();
                this.messageService.info(
                    next === 'theia'
                        ? 'File Load: Theia dialog (browse workspace/bundle files)'
                        : 'File Load: browser picker (upload a local file)'
                );
            }
        });

        // "Name on load" semaphore — toggled by clicking the status-bar item. 'inherit' = the
        // loaded file's name becomes the input/output name; 'keep' = the slot name is unchanged
        // (e.g. so $input stays $input — see B24).
        commands.registerCommand({
            id: TOGGLE_NAME_ON_LOAD_MODE_COMMAND,
            label: 'UTL-X: Toggle Name on Load (Inherit / Keep)'
        }, {
            execute: () => {
                const next = getNameOnLoadMode() === 'inherit' ? 'keep' : 'inherit';
                setNameOnLoadMode(next);
                this.updateNameOnLoadModeStatus();
                this.messageService.info(
                    next === 'inherit'
                        ? 'Name on Load: Inherit (loaded file renames the input/output)'
                        : 'Name on Load: Keep (input/output name is unchanged on load)'
                );
            }
        });

        // ── File menu: New / Open / Save Transformation (bundle-format §7, IF18) ──
        commands.registerCommand(
            { id: 'utlx.project.new', label: 'UTL-X: New Project…' },
            { execute: () => this.newProject() }
        );
        commands.registerCommand(
            { id: 'utlx.project.open', label: 'UTL-X: Open Project…' },
            { execute: () => this.openProject() }
        );
        commands.registerCommand(
            { id: 'utlx.transformation.save', label: 'UTL-X: Save Project' },
            { execute: () => this.saveTransformation(false) }
        );
        commands.registerCommand(
            { id: 'utlx.transformation.saveAs', label: 'UTL-X: Save Project As…' },
            { execute: () => this.saveTransformation(true) }
        );

        // Edit the project's YAML config in the BOTTOM panel (where the Terminal lives).
        commands.registerCommand(
            { id: 'utlx.config.editTransform', label: 'UTL-X: Edit transform.yaml (bottom panel)' },
            { execute: () => this.openConfigInBottom('transform.yaml') }
        );
        commands.registerCommand(
            { id: 'utlx.config.editEngine', label: 'UTL-X: Edit engine.yaml (bottom panel)' },
            { execute: () => this.openConfigInBottom('engine.yaml') }
        );

        // Help → UTLX Language (modal docs dialog) + demos.
        commands.registerCommand(
            { id: 'utlx.help.language', label: 'UTL-X: UTLX Language (Documentation)' },
            { execute: () => { new DocsDialog().open(); } }
        );
        commands.registerCommand(
            { id: 'utlx.demo.cli', label: 'UTL-X: Demo CLI (terminal)' },
            { execute: () => this.runInTerminal('UTL-X CLI Demo', `bash "${DEMO_DIR}/cli-demo.sh"`) }
        );
        commands.registerCommand(
            { id: 'utlx.demo.ide', label: 'UTL-X: Demo IDE (walkthrough)' },
            { execute: () => this.runInTerminal('UTL-X IDE Demo', `bash "${DEMO_DIR}/run-demo.sh" --once`) }
        );
    }

    /** Run a shell command in a fresh integrated terminal (Help → Demo commands). The scripts live in
     *  the repo's playwright/ dir (DEMO_DIR), not the workspace: cli-demo.sh (self-narrating CLI demo)
     *  and run-demo.sh (Playwright IDE walkthrough; --once = one pass then close). */
    private async runInTerminal(title: string, command: string): Promise<void> {
        try {
            const terminal = await this.terminalService.newTerminal({ title });
            await terminal.start();
            this.terminalService.open(terminal);
            terminal.sendText(command + '\n');
        } catch (err) {
            this.messageService.error(`Could not start "${title}": ${err instanceof Error ? err.message : String(err)}`);
        }
    }

    /**
     * Open a project config file in the BOTTOM panel area (where the Terminal lives), via Theia's
     * EditorManager (`area: 'bottom'`). transform.yaml resolves against the open transformation dir;
     * engine.yaml against the project root. The `utlx-config` language (utlx-language-support) provides
     * highlighting + schema validation on the opened file.
     */
    private async openConfigInBottom(which: 'transform.yaml' | 'engine.yaml'): Promise<void> {
        const uri = which === 'transform.yaml'
            ? this.currentTxDir?.resolve('transform.yaml')
            : this.currentProjectRoot?.resolve('engine.yaml');
        if (!uri) {
            this.messageService.warn('Open a UTLX Project first (File → Open UTLX Project…).');
            return;
        }
        try {
            if (!(await this.fileService.exists(uri))) {
                this.messageService.warn(`No ${which} in the open project.`);
                return;
            }
            await this.editorManager.open(uri, { widgetOptions: { area: 'bottom' } });
        } catch (err) {
            this.messageService.error(`Could not open ${which}: ${err instanceof Error ? err.message : String(err)}`);
        }
    }

    registerMenus(menus: MenuModelRegistry): void {
        // File menu — documents (New/Open/Save Transformation), per IF18 + bundle-format §7.
        menus.registerMenuAction(CommonMenus.FILE_OPEN, {
            commandId: 'utlx.project.new',
            label: 'New UTLX Project…',
            order: 'a0'
        });
        menus.registerMenuAction(CommonMenus.FILE_OPEN, {
            commandId: 'utlx.project.open',
            label: 'Open UTLX Project…',
            order: 'a1'
        });
        menus.registerMenuAction(CommonMenus.FILE_SAVE, {
            commandId: 'utlx.transformation.save',
            label: 'Save UTLX Project',
            order: 'a1'
        });
        menus.registerMenuAction(CommonMenus.FILE_SAVE, {
            commandId: 'utlx.transformation.saveAs',
            label: 'Save UTLX Project As…',
            order: 'a2'
        });
        menus.registerMenuAction(CommonMenus.FILE_SAVE, {
            commandId: 'utlx.config.editTransform',
            label: 'Edit transform.yaml',
            order: 'c1'
        });
        menus.registerMenuAction(CommonMenus.FILE_SAVE, {
            commandId: 'utlx.config.editEngine',
            label: 'Edit engine.yaml',
            order: 'c2'
        });

        // Help → UTLX Language + Demos
        menus.registerMenuAction(CommonMenus.HELP, {
            commandId: 'utlx.help.language',
            label: 'UTLX Language',
            order: 'a1'
        });
        menus.registerMenuAction(CommonMenus.HELP, {
            commandId: 'utlx.demo.cli',
            label: 'Demo CLI',
            order: 'z1'
        });
        menus.registerMenuAction(CommonMenus.HELP, {
            commandId: 'utlx.demo.ide',
            label: 'Demo IDE',
            order: 'z2'
        });

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

    /**
     * File → Save Transformation (bundle-format §7). Writes the constellation as a `.utlxp`:
     * transformations/<name>/{<name>.utlx, transform.yaml, test-input-<slot>.<ext>} (+ schemas/ when
     * contracts are loaded). One name = the project = the single transformation (single-tx default).
     */
    private async saveTransformation(_saveAs: boolean): Promise<void> {
        try {
            const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(UTLXEditorWidget.ID);
            const inputPanel = await this.widgetManager.getOrCreateWidget<MultiInputPanelWidget>(MultiInputPanelWidget.ID);
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(OutputPanelWidget.ID);

            const utlx = editor.getContent();
            if (!utlx || !utlx.trim()) {
                this.messageService.warn('Nothing to save — the transformation editor is empty.');
                return;
            }

            // Full input constellation (instances + contracts) from panel state.
            const stateInputs: Array<{ name: string; instanceContent?: string; instanceFormat?: string; schemaContent?: string; schemaFormat?: string }> =
                (inputPanel as unknown as { state?: { inputs?: any[] } }).state?.inputs || [];
            const inputs: SaveInput[] = stateInputs.map(i => ({
                name: i.name,
                content: i.instanceContent || '',
                format: i.instanceFormat || 'json',
                schemaContent: i.schemaContent || undefined,
                schemaFormat: i.schemaFormat || undefined,
            }));
            const outExpected = outputPanel.getExpectedSchema();

            const folder = await this.resolveDialogFolder();
            const props: SaveFileDialogProps = {
                title: 'Save Transformation — name the .utlxp project',
                inputValue: 'transformation.utlxp',
                saveLabel: 'Save'
            };
            const target = await this.fileDialogService.showSaveDialog(props, folder);
            if (!target) return;

            const root = target;                               // the <name>.utlxp directory
            const txName = target.path.name || 'transformation'; // basename without .utlxp
            const plan = buildSavePlan({
                txName,
                utlx,
                inputs,
                outputSchemaContent: outExpected?.content,
                outputSchemaFormat: outExpected?.format
            });

            for (const dir of plan.dirs) {
                await this.fileService.createFolder(root.resolve(dir));
            }
            for (const f of plan.files) {
                await this.fileService.write(root.resolve(f.path), f.content);
            }

            editor.setTransformationName(txName);
            this.messageService.info(`✓ Saved transformation "${txName}" → ${root.path.base}`);
        } catch (err) {
            console.error('[UTLXFrontendContribution] Save Transformation failed:', err);
            this.messageService.error(`Save Transformation failed: ${err instanceof Error ? err.message : String(err)}`);
        }
    }

    /** Modal warning popup (blocks until acknowledged) — for "wrong folder"-type project errors,
     *  more visible than a transient toast. Falls back to a toast if the dialog can't open. */
    private async warnDialog(title: string, msg: string): Promise<void> {
        try {
            await new AlertDialog(title, msg).open();
        } catch {
            this.messageService.warn(`${title}: ${msg}`);
        }
    }

    /**
     * File → Open UTL-X Project (bundle-format §7). Select the **`.utlxp` project directory** (the
     * root, NOT a single `.utlx`) and load the COMPLETE transformation setup: the `.utlx` into the
     * editor, the input slots from its header, each slot's `test-input-<slot>` instance + its schema
     * (from `transform.yaml` refs), and the output contract. Phase 1 opens the first transformation
     * in the project. (A single-`.utlx` load is already available via the editor's Load button.)
     */
    /**
     * File → New UTL-X Project. Pick a parent folder + name, scaffold a MINIMAL `.utlxp` bundle
     * (one transformation with a stub `%utlx 1.0` header, `transform.yaml`, no schemas yet) and adopt
     * it AS the Theia workspace (like VS Code "New Project") — the window reloads and the startup hook
     * `maybeLoadProjectWorkspace()` loads it into the panels in Message-Contract mode (mode = f(workspace)).
     * Creating a project IS opening it as the workspace (IF22 unified model).
     */
    private async newProject(): Promise<void> {
        try {
            const folder = await this.resolveDialogFolder();
            const props: SaveFileDialogProps = {
                title: 'New UTL-X Project — name the .utlxp project',
                inputValue: 'untitled.utlxp',
                saveLabel: 'Create'
            };
            const target = await this.fileDialogService.showSaveDialog(props, folder);
            if (!target) return;

            // Always ensure the `.utlxp` suffix on the project directory. The user browses to a parent
            // folder and types a name — a new subdir name OR an existing plain dir name — WITHOUT
            // `.utlxp`; we append it (a sibling `<name>.utlxp`). If they already typed `.utlxp`, keep it.
            // (Use `path.base`, not `path.name`, so a dotted name like `my.data` isn't truncated.)
            const typed = target.path.base;
            const hasExt = typed.toLowerCase().endsWith('.utlxp');
            const root = hasExt ? target : target.parent.resolve(`${typed}.utlxp`);   // the <name>.utlxp directory
            const txName = (hasExt ? typed.slice(0, -'.utlxp'.length) : typed) || 'transformation'; // name w/o .utlxp

            // Don't clobber an existing project — point the user at Open instead.
            if (await this.fileService.exists(root)) {
                await this.warnDialog(
                    'Already exists',
                    `"${root.path.base}" already exists.\n\nPick a new name, or use Open UTLX Project to open it.`);
                return;
            }

            // Minimal, valid skeleton — one input slot + a JSON output, empty body.
            const utlx =
`%utlx 1.0
input payload json
output json
---
// New UTL-X transformation — map $payload to the output contract.
{
}
`;
            const plan = buildSavePlan({
                txName,
                utlx,
                inputs: [{ name: 'payload', content: '', format: 'json' }]
            });
            for (const dir of plan.dirs) {
                await this.fileService.createFolder(root.resolve(dir));
            }
            for (const f of plan.files) {
                await this.fileService.write(root.resolve(f.path), f.content);
            }

            // Adopt the new project AS the workspace (reloads). Startup then loads it + Message-Contract.
            this.workspaceService.open(root);
        } catch (err) {
            console.error('[UTLXFrontendContribution] New UTL-X Project failed:', err);
            this.messageService.error(`New UTL-X Project failed: ${err instanceof Error ? err.message : String(err)}`);
        }
    }

    /**
     * Relabel the built-in `workspace:openRecent` File-menu action to "Open Recent UTLX Project…".
     * Pure menu-label override (unregister the workspace-provided action, re-register the same
     * commandId with our label + same order) — the command and its behavior are untouched. Run from
     * onStart so all menu contributions have registered (order-safe). Best-effort: never block startup.
     */
    private relabelOpenRecent(): void {
        try {
            this.menus.unregisterMenuAction('workspace:openRecent', CommonMenus.FILE_OPEN);
            this.menus.registerMenuAction(CommonMenus.FILE_OPEN, {
                commandId: 'workspace:openRecent',
                label: 'Open Recent UTLX Project…',
                order: 'a20'   // keep the original slot (right after Open Workspace, a10)
            });
        } catch (err) {
            console.warn('[UTLXFrontendContribution] Could not relabel Open Recent Workspace:', err);
        }
    }

    private async openProject(): Promise<void> {
        try {
            const folder = await this.resolveDialogFolder();
            const props: OpenFileDialogProps = {
                title: 'Open UTL-X Project — select the .utlxp directory',
                canSelectFiles: false,
                canSelectFolders: true,
                canSelectMany: false
            };
            const selected = await this.fileDialogService.showOpenDialog(props, folder);
            const root = Array.isArray(selected) ? selected[0] : selected;
            if (!root) return;

            // Validate it's a bundle project (has transformations/).
            try {
                await this.fileService.resolve(root.resolve('transformations'));
            } catch {
                await this.warnDialog(
                    'Not a UTL-X Project',
                    'The selected folder is not a UTL-X project — it has no "transformations/" directory.\n\n' +
                    'Pick a .utlxp project directory (the folder that contains transformations/, schemas/, engine.yaml).');
                return;
            }

            // Open the project AS the Theia workspace (Explorer/git root) — like VS Code "Open Folder",
            // this RELOADS the window. The startup hook maybeLoadProjectWorkspace() then auto-loads the
            // transformation + enforces Message-Contract mode (mode is DERIVED from the workspace).
            this.workspaceService.open(root);
        } catch (err) {
            console.error('[UTLXFrontendContribution] Open UTL-X Project failed:', err);
            this.messageService.error(`Open UTL-X Project failed: ${err instanceof Error ? err.message : String(err)}`);
        }
    }

    /**
     * Startup hook: if the current Theia workspace IS a `.utlxp` project (has transformations/), load
     * the transformation into the panels and switch to Message-Contract (IF03: a bundle is always MC).
     * Mode is DERIVED from the workspace — durable across reloads — not asserted as a transient toggle.
     */
    private async maybeLoadProjectWorkspace(): Promise<void> {
        try {
            await this.workspaceService.ready;
            const roots = this.workspaceService.tryGetRoots();
            const root = roots && roots[0] ? roots[0].resource : undefined;
            if (!root) return;
            try { await this.fileService.resolve(root.resolve('transformations')); }
            catch { return; }   // not a bundle workspace → stay in default (Execution) mode
            await this.loadProjectFromRoot(root);
        } catch (err) {
            console.error('[UTLXFrontendContribution] auto-load project workspace failed:', err);
        }
    }

    /**
     * Load a `.utlxp` project rooted at `root` into the editor + panels and switch to MC. Used by the
     * startup hook (after "Open UTL-X Project" reloads into the project workspace). Opens the first
     * transformation; binds each input's test-input instance + schema (transform.yaml refs) + the
     * output contract. (A single-`.utlx` load is still available via the editor's Load button.)
     */
    private async loadProjectFromRoot(root: URI): Promise<void> {
            const txParentStat = await this.fileService.resolve(root.resolve('transformations'));
            const txDirs = (txParentStat.children || []).filter(c => c.isDirectory)
                .sort((a, b) => a.resource.path.base.localeCompare(b.resource.path.base));
            if (txDirs.length === 0) { return; }   // empty project — nothing to load

            // One transformation → open it. Multiple → ask which one (a project can bundle several
            // mappings, e.g. order-to-invoice + order-to-picklist). The picker runs at startup after
            // the workspace reload; dismissing it falls back to the first (alphabetical) so the project
            // still opens.
            let txStat = txDirs[0];
            if (txDirs.length > 1) {
                const items = txDirs.map(d => ({
                    label: d.resource.path.base,
                    description: 'transformation',
                    stat: d
                }));
                const picked = await this.quickInput.showQuickPick(items, {
                    title: 'Open UTL-X Project — choose a transformation',
                    placeholder: `This project bundles ${txDirs.length} transformations. Select one to open.`
                });
                if (picked) { txStat = picked.stat; }
            }
            const txName = txStat.resource.path.base;
            const multiNote = txDirs.length > 1 ? ` (project has ${txDirs.length} transformations; opened "${txName}")` : '';
            this.currentProjectRoot = root;        // engine.yaml lives here
            this.currentTxDir = txStat.resource;   // transform.yaml lives here

            // Find the .utlx (prefer <tx>.utlx, else the first *.utlx).
            const txChildren = (await this.fileService.resolve(txStat.resource)).children || [];
            const utlxFiles = txChildren.filter(c => !c.isDirectory && c.resource.path.ext === '.utlx');
            const utlxStat = utlxFiles.find(c => c.resource.path.name === txName) || utlxFiles[0];
            if (!utlxStat) { return; }   // no .utlx source — nothing to load

            const utlx = (await this.fileService.read(utlxStat.resource)).value.toString();
            const parsed = parseUTLXHeaders(utlx);

            const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(UTLXEditorWidget.ID);
            const inputPanel = await this.widgetManager.getOrCreateWidget<MultiInputPanelWidget>(MultiInputPanelWidget.ID);
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(OutputPanelWidget.ID);

            editor.setContent(utlx);
            editor.setTransformationName(txName);
            try { this.shell.activateWidget(editor.id); } catch { /* editor model may not be attached yet */ }

            // transform.yaml → per-input + output schema refs (relative to the project root).
            let refs: ReturnType<typeof parseTransformYamlRefs> = { inputs: [] };
            try {
                const y = (await this.fileService.read(txStat.resource.resolve('transform.yaml'))).value.toString();
                refs = parseTransformYamlRefs(y);
            } catch { /* no/unreadable transform.yaml — instances only */ }

            // Tolerant instance matching — support multiple file-naming standards:
            //   test-input-<slot>.<ext> · <slot>.<ext> · kebab/camel variants (normalized) ·
            //   and a single-input bundle's bare test-input.<ext>.
            const norm = (s: string) => (s || '').toLowerCase().replace(/[^a-z0-9]/g, '');
            const dataFileExts = ['json', 'xml', 'csv', 'yaml', 'yml', 'txt'];
            const instanceFiles = txChildren.filter(c =>
                !c.isDirectory &&
                c.resource.path.base !== 'transform.yaml' &&
                dataFileExts.includes((c.resource.path.ext || '').replace(/^\./, '').toLowerCase())
            );
            const slotKey = (nameNoExt: string) => norm(nameNoExt.replace(/^test-?input-?/i, ''));
            const findInstance = (slot: string) => {
                const key = norm(slot);
                return instanceFiles.find(c => slotKey(c.resource.path.name) === key)
                    || instanceFiles.find(c => norm(c.resource.path.name) === key)
                    || (parsed.inputs.length === 1 && instanceFiles.length === 1 ? instanceFiles[0] : undefined);
            };

            // Per slot: load instance + schema; CLEAR (empty string) when not found, so a previous
            // session's content for a same-named slot can never linger → no garbage in Execution mode.
            const samples: Array<{ name: string; content: string; format: string; schemaContent: string; schemaFormat?: string }> = [];
            for (const inp of parsed.inputs) {
                let content = '';
                const hit = findInstance(inp.name);
                if (hit) {
                    try { content = (await this.fileService.read(hit.resource)).value.toString(); } catch { content = ''; }
                }
                let schemaContent = '';
                let schemaFormat: string | undefined;
                const ref = refs.inputs.find(r => r.name === inp.name)?.schemaRef;
                if (ref) {
                    try {
                        const schUri = root.resolve(ref);
                        if (await this.fileService.exists(schUri)) {
                            schemaContent = (await this.fileService.read(schUri)).value.toString();
                            schemaFormat = extToSchemaFormat(ref);
                        }
                    } catch { /* skip missing schema */ }
                }
                samples.push({ name: inp.name, content, format: inp.format, schemaContent, schemaFormat });
            }

            // ORDER (per design): the .utlx header drives the input/output TABS first; THEN each
            // instance + schema is loaded into the known tabs (empty tabs when a project has no
            // schemas). The editor (re)parses the loaded header on a 500ms debounce to build the tabs,
            // so we wait for that to settle and then apply tabs + instances/schemas as the LAST write —
            // otherwise the async header-parse can clobber them (→ "no inputs"/garbage).
            await new Promise(resolve => setTimeout(resolve, 650));
            inputPanel.syncFromHeaders(parsed.inputs);   // authoritative input set + display order (header owns these)
            outputPanel.syncFromHeaders(parsed.output);
            inputPanel.loadBundleSamples(samples);       // fill each instance + schema into its tab

            // Output contract → apply via the SAME path the "Load output schema" button uses
            // (the output panel does not consume fireOutputPresetOn).
            let outLoaded = false;
            if (refs.outputSchemaRef) {
                try {
                    const outUri = root.resolve(refs.outputSchemaRef);
                    if (await this.fileService.exists(outUri)) {
                        const schema = (await this.fileService.read(outUri)).value.toString();
                        const sf = extToSchemaFormat(refs.outputSchemaRef);
                        const schemaFormat = (['xsd', 'jsch', 'avro', 'proto', 'osch', 'tsch'].includes(sf) ? sf : 'jsch') as 'xsd' | 'jsch' | 'avro' | 'proto' | 'osch' | 'tsch';
                        outputPanel.displaySchemaResult({
                            success: true,
                            schema,
                            schemaFormat,
                            fileName: outUri.path.base,
                            filePath: outUri.path.toString()
                        });
                        outLoaded = true;
                    }
                } catch { /* skip missing output schema */ }
            }

            // IF03: a bundle is always Message-Contract. Mode is DERIVED from the workspace; the
            // toolbar/editor/panels all listen to onModeChanged, so the whole IDE follows.
            this.eventService.fireModeChanged({ mode: UTLXMode.MESSAGE_CONTRACT });

            const loaded = samples.filter(s => s.content.trim()).length;
            const schemas = samples.filter(s => s.schemaContent.trim()).length + (outLoaded ? 1 : 0);
            this.messageService.info(`✓ Opened project "${root.path.name}" → "${txName}"${multiNote} — ${loaded} input(s), ${schemas} schema(s)`);
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
            await this.shell.leftPanelHandler.expand();
            await this.shell.rightPanelHandler.expand();

            // Hide the right panel's sidebar strip (tab bar + menus) so the Output Panel
            // extends fully to the right edge. Also close the Outline view which is not
            // useful for the custom UTL-X editor.
            const cleanupRightPanel = () => {
                try {
                    // Close the Outline widget if present
                    const outlineWidget = this.shell.getWidgets('right')
                        .find(w => w.id === 'outline-view');
                    if (outlineWidget) {
                        outlineWidget.close();
                    }

                    // Hide the right sidebar container (tab bar strip) so Output Panel
                    // fills to the window edge. The container is the first child of the
                    // rightPanelHandler's container BoxLayout.
                    const handler = this.shell.rightPanelHandler as any;
                    if (handler && handler.container) {
                        const layout = handler.container.layout;
                        if (layout && layout.widgets) {
                            for (let i = 0; i < layout.widgets.length; i++) {
                                const w = layout.widgets[i];
                                if (w.node && w.node.classList.contains('theia-app-sidebar-container')) {
                                    w.hide();
                                    break;
                                }
                            }
                        }
                    }

                    this.shell.activateWidget(outputPanel.id);
                } catch (e) {
                    console.error('[UTLXFrontendContribution] cleanupRightPanel error:', e);
                }
            };
            // Run deferred — OutlineViewContribution.initializeLayout() runs after onStart()
            setTimeout(cleanupRightPanel, 500);
            setTimeout(cleanupRightPanel, 1500);
            setTimeout(cleanupRightPanel, 3000);

            // Monitor when active widget changes — restore Input/Output panels, but only when the
            // user POSITIVELY moves focus into the editor (main) or output (right). Earlier this
            // restored on `activeArea !== 'left'`, which also fired on a transient/undefined active
            // change — e.g. RIGHT-CLICKING in the Explorer opens a context menu that blurs to no shell
            // widget (activeArea === undefined), so it yanked the input panel back over the Explorer
            // mid-right-click. Now: an undefined active (context menu/popup) restores NOTHING, and an
            // active left-area view (Explorer in use — new file/folder, etc.) is left alone. The panels
            // come back when the user clicks into the editor/output (or toggles the Explorer icon off).
            this.shell.onDidChangeActiveWidget(() => {
                if (this.canvasFullScreen) return;
                // Run as a MICROTASK (not setTimeout) so the restore lands in the SAME frame as the
                // collapse — a 100ms timer painted the collapsed state (editor slid left) and only then
                // restored the input panel (editor slid back), a visible two-step flicker. A microtask
                // flushes before the browser paints, so collapse + restore reflow once.
                void Promise.resolve().then(() => {
                    if (this.canvasFullScreen) return;
                    const active = this.shell.activeWidget;
                    const activeArea = active && this.shell.getAreaFor(active);

                    // Is another LEFT-area view (the Explorer/Navigator) still showing on top of the
                    // left panel? This is the discriminator between two cases that BOTH blur focus to no
                    // shell widget (activeArea === undefined):
                    //   • right-click in the Explorer → context menu, Explorer STILL visible → keep it.
                    //   • collapse the Explorer (icon) → Explorer now HIDDEN → fall back to input panel.
                    const otherLeftVisible = this.shell.getWidgets('left')
                        .some(w => w !== inputPanel && w.isVisible);

                    // INPUT panel (left): bring it back when the user moved focus to the editor (main) or
                    // output (right) — a positive "clicked away" — OR when no other left view is showing
                    // (Explorer collapsed/closed, so the left panel falls back to the input panel). NOT
                    // while the Explorer is the visible left tab with only a transient blur (its menu).
                    const movedToMainOrRight = activeArea === 'main' || activeArea === 'right';
                    if (inputPanel.isAttached && !inputPanel.isVisible &&
                        (movedToMainOrRight || !otherLeftVisible)) {
                        this.shell.activateWidget(inputPanel.id);
                    }

                    // Output panel (right): restore when something else (e.g. Outline) took its slot and
                    // the user moved to the editor or the left panel.
                    if (outputPanel.isAttached && !outputPanel.isVisible &&
                        (activeArea === 'main' || activeArea === 'left')) {
                        this.shell.activateWidget(outputPanel.id);
                    }
                });
            });

            // Set initial panel sizes for 1:1:1 ratio (equal thirds)
            // Wait a bit for the layout to settle, then set sizes
            setTimeout(() => {
                const totalWidth = window.innerWidth;
                const leftWidth = Math.floor(totalWidth * (1 / 3));
                const rightWidth = Math.floor(totalWidth * (1 / 3));

                // Set panel sizes (in pixels)
                this.shell.leftPanelHandler.resize(leftWidth);
                this.shell.rightPanelHandler.resize(rightWidth);

                console.log('[UTLXFrontendContribution] Set initial panel sizes:', {
                    totalWidth,
                    leftWidth,
                    rightWidth,
                    ratio: '1:1:1'
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

    /**
     * Render the file-Load dialog mode in the status bar (bottom). Clicking it runs the toggle
     * command (the runtime "semaphore"): Theia dialog (workspace/bundle files + full path) ↔
     * Browser picker (client upload, basename only).
     */
    private updateFileDialogModeStatus(): void {
        const mode = getFileDialogMode();
        this.statusBar.setElement(this.fileDialogModeId, {
            text: mode === 'theia' ? '$(folder-opened) Load: Theia' : '$(device-desktop) Load: Browser',
            // Stable hook for demos/tests (StatusBar has no data-testid; className is the anchor).
            className: 'utlx-sb-file-dialog-mode',
            alignment: StatusBarAlignment.RIGHT,
            priority: 98,
            tooltip: mode === 'theia'
                ? 'File Load: Theia dialog (workspace/bundle files, full path). Click to switch to Browser (upload).'
                : 'File Load: Browser picker (upload a local file, basename only). Click to switch to Theia (workspace).',
            command: TOGGLE_FILE_DIALOG_MODE_COMMAND
        });
    }

    /**
     * Render the "name on load" mode in the status bar (bottom), next to the file-dialog toggle.
     * Clicking it runs the toggle command (the runtime "semaphore"): Inherit (loaded file renames
     * the input/output) ↔ Keep (the slot name is left unchanged — e.g. $input stays $input, B24).
     */
    private updateNameOnLoadModeStatus(): void {
        const mode = getNameOnLoadMode();
        this.statusBar.setElement(this.nameOnLoadModeId, {
            text: mode === 'inherit' ? '$(tag) Name: Inherit' : '$(lock) Name: Keep',
            // Stable hook for demos/tests (StatusBar has no data-testid; className is the anchor).
            className: 'utlx-sb-name-on-load-mode',
            alignment: StatusBarAlignment.RIGHT,
            priority: 97,
            tooltip: mode === 'inherit'
                ? 'Name on Load: Inherit — a loaded file renames the input/output (e.g. "order.json" → "order"). Click to switch to Keep.'
                : 'Name on Load: Keep — the input/output name is unchanged on load (e.g. $input stays $input). Click to switch to Inherit.',
            command: TOGGLE_NAME_ON_LOAD_MODE_COMMAND
        });
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
        // Liveness comes from the Theia backend over JSON-RPC — the frontend
        // never touches the daemon/MCP ports directly (would break under
        // remote/cloud Theia).
        let daemonOnline = false;
        let mcpOnline = false;
        try {
            const health = await this.utlxService.getServicesHealth();
            daemonOnline = health.daemon.online;
            mcpOnline = health.mcp.online;
        } catch (error) {
            // Backend unreachable → both shown offline.
        }

        this.statusBar.setElement(this.utlxdStatusId, {
            text: daemonOnline ? '$(check) UTLXD' : '$(x) UTLXD',
            alignment: StatusBarAlignment.RIGHT,
            priority: 100,
            tooltip: `UTLXD LSP Server: ${daemonOnline ? 'Online' : 'Offline'}`,
            color: daemonOnline ? '#50fa7b' : '#ff5555'
        });

        this.statusBar.setElement(this.mcpStatusId, {
            text: mcpOnline ? '$(check) MCP' : '$(x) MCP',
            alignment: StatusBarAlignment.RIGHT,
            priority: 99,
            tooltip: `MCP Server: ${mcpOnline ? 'Online' : 'Offline'}`,
            color: mcpOnline ? '#50fa7b' : '#ff5555'
        });
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

        // Track current mode
        this.eventService.onModeChanged(event => {
            this.currentMode = event.mode;
        });

        // Toggle side panels when editor view mode changes (Classic ↔ Canvas)
        this.eventService.onEditorViewModeChanged(event => {
            if (event.viewMode === 'canvas') {
                if (event.fullScreen !== false) {
                    // Default: canvas collapses panels (backward compat)
                    this.canvasFullScreen = true;
                    // Delay collapse so any already-pending panel-restore timers (100ms) run first
                    // and get blocked by the canvasFullScreen guard
                    setTimeout(() => {
                        if (!this.canvasFullScreen) return;
                        this.shell.leftPanelHandler.collapse();
                        this.shell.rightPanelHandler.collapse();
                    }, 150);
                } else {
                    // User toggled off full-screen while staying in canvas
                    this.canvasFullScreen = false;
                    this.shell.leftPanelHandler.expand();
                    this.shell.rightPanelHandler.expand();
                }
            } else {
                this.canvasFullScreen = false;
                this.shell.leftPanelHandler.expand();
                this.shell.rightPanelHandler.expand();
            }
        });

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

        // Subscribe to output name changes (Execution mode - rewrites "output <name> <format>")
        this.eventService.onOutputNameChanged(event => {
            if (this.isUpdatingFromParsedHeaders) return;
            console.log('[UTLXFrontendContribution] Output name changed:', event);
            this.outputName = event.newName;
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

        // Subscribe to output schema format changes
        this.eventService.onOutputSchemaFormatChanged(event => {
            console.log('[UTLXFrontendContribution] Output schema format changed:', event);

            // IB06: this event ALWAYS carries a SCHEMA format (the output Schema tab's format —
            // jsch/xsd/osch/tsch). The `output` directive (and the execute request) is ALWAYS a DATA
            // format — you execute *to* data, never *to* a schema. So map schema→data in BOTH modes
            // (jsch→json, xsd→xml, …; a data format passes through). This must not depend on
            // currentMode: when an MC project loads, this fires BEFORE fireModeChanged(MC) sets the
            // mode, so a mode-gated branch would leak `jsch` into the Execution output format and
            // break Execute with "UDM does not represent valid JSON Schema".
            this.outputFormat = this.schemaFormatToInstanceFormat(event.format);

            // Update editor headers
            this.updateEditorHeaders();
        });

        // Subscribe to request output schema inference (design-time mode)
        this.eventService.onRequestOutputSchemaInference(async event => {
            console.log('[UTLXFrontendContribution] Request output schema inference:', event);
            await this.executeSchemaInference(event.schemaFormat);
        });

        // Subscribe to request load output schema (design-time mode)
        this.eventService.onRequestLoadOutputSchema(async event => {
            console.log('[UTLXFrontendContribution] Request load output schema:', event);
            await this.loadOutputSchemaFromFile(event.schemaFormat);
        });

        // Subscribe to headers parsed from editor (copy/paste sync)
        this.eventService.onHeadersParsed(event => {
            console.log('[UTLXFrontendContribution] Headers parsed from editor:', event);
            this.updatePanelsFromParsedHeaders(event);
        });

        // Subscribe to scaffold output request
        this.eventService.onScaffoldOutput(async () => {
            console.log('[UTLXFrontendContribution] Scaffold Output event received');
            await this.handleScaffoldOutput();
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
                outputFormat: this.outputFormat,
                outputName: this.outputName
            });

            editor.updateHeaders(inputLines, this.outputFormat, this.outputName);
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
            odataMetadata?: string;
            odataContext?: string;
            odataWrapCollection?: boolean;
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

        // OData options
        if (format === 'odata') {
            const metadata = options.odataMetadata ?? 'minimal'; // Default minimal
            const context = options.odataContext;
            const wrapCollection = options.odataWrapCollection ?? true; // Default true

            if (metadata !== 'minimal') {
                params.push(`metadata: "${metadata}"`);
            }
            if (context) {
                // Strip user-provided quotes if present
                const cleanContext = context.replace(/^["']|["']$/g, '');
                params.push(`context: "${cleanContext}"`);
            }
            if (wrapCollection === false) {
                params.push(`wrapCollection: false`);
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

                // 2a. Handle EVALUATE/VALIDATE mode (Message Contract) - validate output schema
                if (event.mode === 'evaluate') {
                    console.log('[Validate] Message Contract mode - validating output schema');

                    // Step 1: Get expected output schema from output panel
                    const expectedSchema = outputPanel.getExpectedSchema();
                    if (!expectedSchema) {
                        this.messageService.warn('Please define an output schema first (use the Schema tab in the Output panel)');
                        return;
                    }
                    console.log('[Validate] Expected schema format:', expectedSchema.format);
                    console.log('[Validate] Expected schema content length:', expectedSchema.content.length);
                    console.log('[Validate] Expected schema content preview:', expectedSchema.content.substring(0, 300));

                    // Step 2: Get input schema if available (optional for inference)
                    const inputSchema = inputPanel.getSchemaDocument();
                    console.log('[Validate] Input schema:', inputSchema ? `${inputSchema.format} (${inputSchema.content.length} chars)` : 'none');

                    // Step 3: Infer output schema via service
                    this.messageService.info('✅ Validating transformation output...');
                    const startTime = Date.now();

                    try {
                        const result = await this.utlxService.inferSchema(utlxCode, inputSchema || undefined);
                        const executionTime = Date.now() - startTime;

                        console.log('[Validate] Schema inference result:', {
                            success: result.success,
                            schemaLength: result.schema?.length,
                            schemaFormat: result.schemaFormat,
                            error: result.error
                        });

                        if (!result.success || !result.schema) {
                            this.messageService.error(`✗ Schema inference failed: ${result.error || 'Unknown error'}`);
                            outputPanel.displaySchemaResult({
                                success: false,
                                error: result.error || 'Failed to infer schema',
                                typeErrors: result.typeErrors
                            });
                            return;
                        }

                        // Step 4: Parse both schemas to SchemaFieldInfo[]
                        console.log('[Validate] Parsing expected schema: format =', expectedSchema.format, 'normalized =', this.normalizeSchemaFormat(expectedSchema.format));
                        const expectedFields = this.parseSchemaToFieldTree(expectedSchema.content, expectedSchema.format);
                        console.log('[Validate] Parsing inferred schema: format =', result.schemaFormat || 'jsch');
                        const inferredFields = this.parseSchemaToFieldTree(result.schema, result.schemaFormat || 'jsch');

                        console.log('[Validate] Expected fields:', expectedFields.length, 'top-level');
                        console.log('[Validate] Inferred fields:', inferredFields.length, 'top-level');
                        if (expectedFields.length > 0) {
                            console.log('[Validate] Expected field names:', expectedFields.map(f => f.name));
                        }
                        if (inferredFields.length > 0) {
                            console.log('[Validate] Inferred field names:', inferredFields.map(f => f.name));
                        }

                        if (expectedFields.length === 0) {
                            const normalizedFmt = this.normalizeSchemaFormat(expectedSchema.format);
                            this.messageService.warn(
                                `Could not parse expected output schema (format: ${expectedSchema.format}→${normalizedFmt}, ` +
                                `${expectedSchema.content.length} chars). Check that the Schema tab contains a valid schema.`
                            );
                            return;
                        }

                        // Step 5: Compare schemas
                        const comparison = compareSchemas(expectedFields, inferredFields);
                        console.log('[Validate] Comparison result:', {
                            matchCount: comparison.matchCount,
                            missingCount: comparison.missingCount,
                            extraCount: comparison.extraCount,
                            typeMismatchCount: comparison.typeMismatchCount,
                            isValid: comparison.isValid
                        });

                        // Step 6: Fire validation result event (toolbar will show dialog)
                        this.eventService.fireValidationResult({ result: comparison });

                        // Step 7: Show summary toast
                        if (comparison.isValid) {
                            this.messageService.info(`✓ Validation passed — ${comparison.matchCount} fields matched (${executionTime}ms)`);
                        } else {
                            const issues = comparison.missingCount + comparison.typeMismatchCount;
                            this.messageService.warn(`✗ ${issues} issue(s) found (${comparison.missingCount} missing, ${comparison.typeMismatchCount} type mismatch)`);
                        }

                    } catch (error) {
                        console.error('[Validate] Validation threw exception:', error);
                        this.messageService.error(`✗ Validation failed: ${error instanceof Error ? error.message : String(error)}`);
                    }

                    return; // Exit early - don't continue to execute path
                }

                // 3. Collect input documents (for EXECUTE mode only)
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

    /**
     * Execute schema inference (design-time mode)
     *
     * Strategy:
     * 1. If instance output exists (from transformation execution), infer schema from that
     * 2. Otherwise, fall back to static UTLX code analysis
     */
    private async executeSchemaInference(schemaFormat: string): Promise<void> {
        console.log('[UTLXFrontendContribution] ========================================');
        console.log('[UTLXFrontendContribution] SCHEMA INFERENCE REQUESTED');
        console.log('[UTLXFrontendContribution] Schema format:', schemaFormat);
        console.log('[UTLXFrontendContribution] ========================================');

        try {
            // Get output panel
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );

            // Check if we have instance output to infer from
            const instanceData = outputPanel.getInstanceData();
            console.log('[SchemaInference] Instance data:', {
                hasContent: !!instanceData.content,
                contentLength: instanceData.content?.length || 0,
                format: instanceData.format
            });

            // If we have instance content, infer schema from it
            if (instanceData.content && instanceData.content.trim().length > 0) {
                console.log('[SchemaInference] Using instance-based inference');
                this.messageService.info('Inferring schema from instance output...');

                try {
                    if (instanceData.format === 'json') {
                        // Infer JSON Schema from JSON
                        const schema = inferSchemaFromJson(instanceData.content);
                        const schemaString = formatSchema(schema);

                        outputPanel.displaySchemaResult({
                            success: true,
                            schema: schemaString,
                            schemaFormat: 'jsch'
                        });

                        this.messageService.info('JSON Schema inferred from JSON instance');
                        return;
                    } else if (instanceData.format === 'yaml') {
                        // Infer JSON Schema from YAML (parsed client-side)
                        const schema = inferSchemaFromYaml(instanceData.content);
                        const schemaString = formatSchema(schema);

                        outputPanel.displaySchemaResult({
                            success: true,
                            schema: schemaString,
                            schemaFormat: 'jsch'
                        });

                        this.messageService.info('JSON Schema inferred from YAML instance');
                        return;
                    } else if (instanceData.format === 'xml') {
                        // Infer XSD from XML
                        const xsdString = inferSchemaFromXml(instanceData.content);

                        outputPanel.displaySchemaResult({
                            success: true,
                            schema: xsdString,
                            schemaFormat: 'xsd'
                        });

                        this.messageService.info('XSD schema inferred from instance output');
                        return;
                    } else if (instanceData.format === 'odata') {
                        // Infer EDMX/CSDL from OData JSON
                        const edmxString = inferEdmxFromOData(instanceData.content);

                        outputPanel.displaySchemaResult({
                            success: true,
                            schema: edmxString,
                            schemaFormat: 'osch'
                        });

                        this.messageService.info('EDMX schema inferred from OData output');
                        return;
                    } else if (instanceData.format === 'csv') {
                        // Infer Table Schema from CSV
                        const tschString = inferTableSchemaFromCsv(instanceData.content);

                        outputPanel.displaySchemaResult({
                            success: true,
                            schema: tschString,
                            schemaFormat: 'tsch'
                        });

                        this.messageService.info('Table Schema inferred from CSV output');
                        return;
                    } else {
                        console.log('[SchemaInference] Unsupported format for instance inference:', instanceData.format);
                    }
                } catch (instanceError) {
                    console.warn('[SchemaInference] Instance-based inference failed, falling back to static:', instanceError);
                }
            }

            // Fall back to static UTLX analysis
            console.log('[SchemaInference] Using static UTLX analysis');

            const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(
                UTLXEditorWidget.ID
            );
            const utlxCode = editor.getContent();

            if (!utlxCode || utlxCode.trim().length === 0) {
                this.messageService.warn('No UTLX code and no instance output. Please execute transformation first or enter UTLX code.');
                return;
            }

            this.messageService.info('Inferring output schema from UTLX code...');
            const result = await this.utlxService.inferSchema(utlxCode);

            console.log('[SchemaInference] Static analysis result:', result);
            outputPanel.displaySchemaResult(result);

            if (result.success) {
                this.messageService.info('Schema inference completed (static analysis)');
            } else {
                this.messageService.warn('Schema inference completed with issues');
            }

        } catch (error) {
            console.error('[SchemaInference] Error:', error);
            this.messageService.error(`Schema inference failed: ${error instanceof Error ? error.message : String(error)}`);

            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );
            outputPanel.displayError(error instanceof Error ? error.message : String(error));
        }
    }

    /**
     * Load output schema from file (design-time mode)
     * Opens a file dialog to let the user select a schema file
     */
    /** Load an output schema via the plain HTML file picker (client upload; basename only, no path). */
    private loadOutputSchemaViaBrowserPicker(schemaFormat: string): void {
        try {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = this.getSchemaFileExtensions(schemaFormat).map(e => '.' + e).join(',') || '*';
            input.onchange = async (e: Event) => {
                const file = (e.target as HTMLInputElement).files?.[0];
                if (!file) return;
                try {
                    const content = await file.text();
                    const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                        OutputPanelWidget.ID
                    );
                    outputPanel.displaySchemaResult({
                        success: true,
                        schema: content,
                        schemaFormat: schemaFormat as 'xsd' | 'jsch' | 'avro' | 'proto' | 'osch' | 'tsch',
                        fileName: file.name  // browser picker → no full path
                    });
                    this.messageService.info(`Schema loaded from ${file.name}`);
                } catch (error) {
                    this.messageService.error(`Failed to load schema: ${error instanceof Error ? error.message : String(error)}`);
                }
            };
            input.click();
        } catch (error) {
            this.messageService.error(`Failed to load schema: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async loadOutputSchemaFromFile(schemaFormat: string): Promise<void> {
        console.log('[UTLXFrontendContribution] ========================================');
        console.log('[UTLXFrontendContribution] LOAD OUTPUT SCHEMA FROM FILE');
        console.log('[UTLXFrontendContribution] Schema format:', schemaFormat);
        console.log('[UTLXFrontendContribution] ========================================');

        // The runtime FILE_DIALOG_MODE "semaphore" (status-bar toggle) picks the loader.
        if (getFileDialogMode() === 'browser') {
            this.loadOutputSchemaViaBrowserPicker(schemaFormat);
            return;
        }

        try {
            // Determine file extensions based on schema format
            const extensions = this.getSchemaFileExtensions(schemaFormat);
            const formatLabel = schemaFormat.toUpperCase();
            const folder = await this.resolveDialogFolder();

            // Open file dialog
            const dialogProps: OpenFileDialogProps = {
                title: `Load ${formatLabel} Schema`,
                canSelectFiles: true,
                canSelectFolders: false,
                canSelectMany: false,
                filters: {
                    [`${formatLabel} Schema Files`]: extensions
                }
            };

            const selectedUri = await this.fileDialogService.showOpenDialog(dialogProps, folder);

            if (!selectedUri) {
                console.log('[LoadSchema] User cancelled file selection');
                return;
            }

            // Handle both single URI and array (canSelectMany: false means single)
            const uri = Array.isArray(selectedUri) ? selectedUri[0] : selectedUri;
            this.eventService.setLastUsedDirectoryUri(uri.parent.toString());
            console.log('[LoadSchema] Selected file:', uri.toString());

            // Read file content
            const fileContent = await this.fileService.read(uri);
            const content = fileContent.value;

            console.log('[LoadSchema] File content length:', content.length);

            // Get output panel to display the loaded schema
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );

            // Display the loaded schema (cast to proper type)
            outputPanel.displaySchemaResult({
                success: true,
                schema: content,
                schemaFormat: schemaFormat as 'xsd' | 'jsch' | 'avro' | 'proto' | 'osch' | 'tsch',
                fileName: uri.path.base,          // provenance for the output panel body
                filePath: uri.path.toString()     // full path (for hover)
            });

            this.messageService.info(`Schema loaded from ${uri.path.base}`);

        } catch (error) {
            console.error('[LoadSchema] Error:', error);
            this.messageService.error(`Failed to load schema: ${error instanceof Error ? error.message : String(error)}`);

            // Display error in output panel
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );
            outputPanel.displayError(error instanceof Error ? error.message : String(error));
        }
    }

    /**
     * Get file extensions for a schema format
     */
    private getSchemaFileExtensions(schemaFormat: string): string[] {
        switch (schemaFormat) {
            case 'jsch':
                return ['json', 'jsch', 'schema.json'];
            case 'xsd':
                return ['xsd', 'xml'];
            case 'avro':
                return ['avsc', 'json'];
            case 'proto':
                return ['proto'];
            case 'osch':
                return ['edmx', 'xml'];
            case 'tsch':
                return ['tsch.json', 'json'];
            default:
                return ['*'];
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
     * Parse a schema string to SchemaFieldInfo[] based on its format.
     * Supports jsch, xsd, osch, tsch formats, plus instance-format aliases
     * (json→jsch, xml→xsd, yaml→jsch) for when the output panel stores
     * an instance-format name instead of a schema-format name.
     */
    private parseSchemaToFieldTree(schemaContent: string, schemaFormat: string): import('./utils/schema-field-tree-parser').SchemaFieldInfo[] {
        const format = this.normalizeSchemaFormat(schemaFormat);
        console.log('[UTLXFrontendContribution] parseSchemaToFieldTree: input format =', schemaFormat, '→ normalized =', format);
        try {
            switch (format) {
                case 'jsch':
                    return parseJsonSchemaToFieldTree(schemaContent);
                case 'xsd':
                    return parseXsdToFieldTree(schemaContent);
                case 'osch':
                    return parseOSchToFieldTree(schemaContent);
                case 'tsch':
                    return parseTschToFieldTree(schemaContent);
                default: {
                    // Last resort: try to auto-detect from content
                    const detected = this.detectSchemaFormat(schemaContent);
                    if (detected) {
                        console.log('[UTLXFrontendContribution] Auto-detected schema format:', detected);
                        return this.parseSchemaToFieldTree(schemaContent, detected);
                    }
                    console.warn('[UTLXFrontendContribution] Unknown schema format for parsing:', schemaFormat);
                    return [];
                }
            }
        } catch (error) {
            console.error('[UTLXFrontendContribution] Failed to parse schema:', error);
            return [];
        }
    }

    /**
     * Map schema format to the instance format it describes.
     * e.g. jsch→json, xsd→xml, osch→odata, tsch→csv
     * Used in Message Contract mode so the UTLX header shows the data output format,
     * not the schema notation format.
     */
    private schemaFormatToInstanceFormat(schemaFormat: string): string {
        switch (schemaFormat.toLowerCase()) {
            case 'jsch':  return 'json';
            case 'xsd':   return 'xml';
            case 'osch':  return 'odata';
            case 'tsch':  return 'csv';
            default:      return schemaFormat;  // Not a schema format, pass through
        }
    }

    /**
     * Normalize schema format: map instance-format names to their schema equivalents.
     * e.g. 'json' → 'jsch', 'xml' → 'xsd', 'yaml' → 'jsch'
     */
    private normalizeSchemaFormat(format: string): string {
        switch (format.toLowerCase()) {
            case 'jsch':  return 'jsch';
            case 'xsd':   return 'xsd';
            case 'osch':  return 'osch';
            case 'tsch':  return 'tsch';
            case 'json':  return 'jsch';  // JSON content on schema tab is likely JSON Schema
            case 'yaml':  return 'jsch';  // YAML schema → JSON Schema
            case 'xml':   return 'xsd';   // XML content on schema tab is likely XSD
            case 'odata': return 'osch';  // OData schema → EDMX/CSDL
            case 'csv':   return 'tsch';  // CSV schema → Table Schema
            default:      return format.toLowerCase();
        }
    }

    /**
     * Try to auto-detect schema format from content.
     * Returns null if detection fails.
     */
    private detectSchemaFormat(content: string): string | null {
        const trimmed = content.trim();
        if (trimmed.startsWith('{')) {
            // Could be JSON Schema, Table Schema, or OData
            try {
                const parsed = JSON.parse(trimmed);
                if (parsed.$schema || parsed.type || parsed.properties) return 'jsch';
                if (parsed.fields && Array.isArray(parsed.fields)) return 'tsch';
            } catch { /* not valid JSON */ }
        }
        if (trimmed.startsWith('<')) {
            // Could be XSD or EDMX
            if (trimmed.includes('xs:schema') || trimmed.includes('xsd:schema') || trimmed.includes('<schema')) return 'xsd';
            if (trimmed.includes('edmx:Edmx') || trimmed.includes('<Edmx') || trimmed.includes('EntityType')) return 'osch';
        }
        return null;
    }

    /**
     * Handle scaffold output request
     * Generates UTLX code structure from output schema/instance
     */
    private async handleScaffoldOutput(): Promise<void> {
        console.log('[UTLXFrontendContribution] ========================================');
        console.log('[UTLXFrontendContribution] SCAFFOLD OUTPUT REQUESTED');
        console.log('[UTLXFrontendContribution] ========================================');

        try {
            // 1. Get output panel to retrieve structure
            const outputPanel = await this.widgetManager.getOrCreateWidget<OutputPanelWidget>(
                OutputPanelWidget.ID
            );

            // 2. Get structure for scaffolding (schema preferred, instance fallback)
            const structure = outputPanel.getStructureForScaffold();

            if (!structure) {
                this.messageService.warn('No output schema or instance data available for scaffolding (JSON/XML only)');
                return;
            }

            console.log('[Scaffold] Structure retrieved:', {
                fieldCount: structure.fields.length,
                format: structure.format
            });

            // 3. Generate scaffold code
            const result = generateScaffoldFromStructure(structure.fields, structure.format);

            if (!result.success || !result.code) {
                this.messageService.error(`Scaffold generation failed: ${result.error || 'Unknown error'}`);
                return;
            }

            console.log('[Scaffold] Generated code:', result.code);

            // 4. Get editor and insert scaffold code
            const editor = await this.widgetManager.getOrCreateWidget<UTLXEditorWidget>(
                UTLXEditorWidget.ID
            );

            // Get current content to preserve headers
            const currentContent = editor.getContent();
            const separatorIndex = currentContent.indexOf('---');

            if (separatorIndex !== -1) {
                // Has header - preserve it and replace body with scaffold
                const header = currentContent.substring(0, separatorIndex + 3); // Include '---'
                const newContent = header + '\n' + result.code;
                editor.setContent(newContent);
            } else {
                // No header - just set the scaffold code
                editor.setContent(result.code);
            }

            this.messageService.info(`✓ UTLX scaffold generated (${structure.fields.length} fields)`);

        } catch (error) {
            console.error('[Scaffold] Error:', error);
            this.messageService.error(`Scaffold generation failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}
