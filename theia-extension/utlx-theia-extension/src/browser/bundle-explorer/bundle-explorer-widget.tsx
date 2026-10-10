/**
 * Bundle Explorer (IF03, Phase 1)
 *
 * Recognizes a UTL-X bundle project — a directory containing `transformations/`
 * (the EF09 open-mode contract) — and shows its tree: transformations, shared
 * `schemas/`, and the manifest. Clicking a node opens the file (routed through the
 * existing UTLXOpenHandler / Monaco). File access is via Theia's FileService, the
 * file-backed approach IF03 specifies (no browser Web Storage for documents).
 *
 * Phase 1 scope: recognize + list + open-as-file. Binding the 3-panel
 * (Input|Transform|Output) view to the *active* transformation, build/export `.utlar`,
 * and the `/admin/*` deploy operations are later phases (see IF03 / IF05).
 */

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';
import { ApplicationShell, OpenerService, open } from '@theia/core/lib/browser';
import URI from '@theia/core/lib/common/uri';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import { FileDialogService } from '@theia/filesystem/lib/browser';
import { FileStat } from '@theia/filesystem/lib/common/files';
import { UTLXEditorWidget } from '../editor/utlx-editor-widget';
import { MultiInputPanelWidget } from '../input-panel/multi-input-panel-widget';
import { parseUTLXHeaders } from '../parser/utlx-header-parser';
import { UTLXService, UTLX_SERVICE_SYMBOL } from '../../common/protocol';

interface BundleTx {
    name: string;
    utlxUri?: string;
    configUri?: string;   // transform.yaml (if present) — editable config
    sampleCount: number;
}
interface BundleSchemaFile { name: string; uri: string; }
interface BundleModel {
    rootUri: string;
    manifest?: { version?: string; created?: string };
    manifestUri?: string;
    engineUri?: string;   // engine.yaml at the bundle root — editable config
    transformations: BundleTx[];
    schemas: BundleSchemaFile[];
}

@injectable()
export class BundleExplorerWidget extends ReactWidget {
    static readonly ID = 'utlx-bundle-explorer';
    static readonly LABEL = 'UTL-X Bundle';
    private static readonly LAST_BUNDLE_KEY = 'utlx.bundle.lastUri';

    @inject(MessageService) protected readonly messageService!: MessageService;
    @inject(FileService) protected readonly fileService!: FileService;
    @inject(FileDialogService) protected readonly fileDialogService!: FileDialogService;
    @inject(OpenerService) protected readonly openerService!: OpenerService;
    @inject(ApplicationShell) protected readonly shell!: ApplicationShell;
    @inject(UTLX_SERVICE_SYMBOL) protected readonly utlxService!: UTLXService;

    private bundle: BundleModel | null = null;
    private loading = false;
    private building = false;
    private error?: string;

    constructor() {
        super();
        this.id = BundleExplorerWidget.ID;
        this.title.label = BundleExplorerWidget.LABEL;
        this.title.caption = BundleExplorerWidget.LABEL;
        this.title.iconClass = 'codicon codicon-package';
        this.title.closable = true;
        this.addClass('utlx-bundle-explorer');
    }

    @postConstruct()
    protected init(): void {
        this.update();
        // Re-open the last bundle on startup (best-effort).
        const last = localStorage.getItem(BundleExplorerWidget.LAST_BUNDLE_KEY);
        if (last) {
            this.loadBundle(new URI(last)).catch(() => { /* stale path — ignore */ });
        }
    }

    /** "Open Bundle…" — pick a folder, then recognize + load it. */
    private async openBundleDialog(): Promise<void> {
        let startFolder: FileStat | undefined;
        try {
            const last = localStorage.getItem(BundleExplorerWidget.LAST_BUNDLE_KEY);
            if (last) {
                startFolder = await this.fileService.resolve(new URI(last).parent);
            }
        } catch { /* fall through to default */ }
        const result = await this.fileDialogService.showOpenDialog({
            title: 'Open UTL-X Bundle (a folder containing transformations/)',
            canSelectFiles: false,
            canSelectFolders: true,
            canSelectMany: false,
            openLabel: 'Open Bundle'
        }, startFolder);
        const uri = Array.isArray(result) ? result[0] : result;
        if (uri) {
            await this.loadBundle(uri);
        }
    }

    /** Read the bundle structure from disk via FileService and render the tree. */
    private async loadBundle(rootUri: URI): Promise<void> {
        this.loading = true;
        this.error = undefined;
        this.update();
        try {
            const root = await this.fileService.resolve(rootUri);
            const txDirStat = (root.children || []).find(c => c.isDirectory && c.name === 'transformations');
            if (!txDirStat) {
                throw new Error('Not a UTL-X bundle: no transformations/ directory here.');
            }

            // transformations/<name>/{<name>.utlx | *.utlx, transform.yaml, samples/}
            const txStat = await this.fileService.resolve(txDirStat.resource);
            const transformations: BundleTx[] = [];
            for (const child of (txStat.children || [])) {
                if (!child.isDirectory) { continue; }
                const dir = await this.fileService.resolve(child.resource);
                const kids = dir.children || [];
                const named = kids.find(k => k.isFile && k.name === `${child.name}.utlx`);
                const anyUtlx = named || kids.find(k => k.isFile && k.name.endsWith('.utlx'));
                const configFile = kids.find(k => k.isFile && k.name === 'transform.yaml');
                // Samples: the IF03 `samples/` dir, plus sibling data files (real bundles
                // keep e.g. `test-input-*.json` next to the .utlx) — anything that isn't
                // the source or the config counts as a sample.
                let sampleCount = kids.filter(k =>
                    k.isFile && !k.name.endsWith('.utlx') && k.name !== 'transform.yaml').length;
                const samplesDir = kids.find(k => k.isDirectory && k.name === 'samples');
                if (samplesDir) {
                    const s = await this.fileService.resolve(samplesDir.resource);
                    sampleCount += (s.children || []).filter(x => x.isFile).length;
                }
                transformations.push({
                    name: child.name,
                    utlxUri: anyUtlx?.resource.toString(),
                    configUri: configFile?.resource.toString(),
                    sampleCount
                });
            }
            transformations.sort((a, b) => a.name.localeCompare(b.name));

            // shared schemas/
            const schemas: BundleSchemaFile[] = [];
            const schemasDirStat = (root.children || []).find(c => c.isDirectory && c.name === 'schemas');
            if (schemasDirStat) {
                const s = await this.fileService.resolve(schemasDirStat.resource);
                for (const f of (s.children || []).filter(x => x.isFile)) {
                    schemas.push({ name: f.name, uri: f.resource.toString() });
                }
                schemas.sort((a, b) => a.name.localeCompare(b.name));
            }

            // manifest.json (open mode may carry one at root; in a .utlar it lives inside)
            let manifest: { version?: string; created?: string } | undefined;
            let manifestUri: string | undefined;
            const manifestStat = (root.children || []).find(c => c.isFile && c.name === 'manifest.json');
            if (manifestStat) {
                manifestUri = manifestStat.resource.toString();
                try {
                    const content = await this.fileService.read(manifestStat.resource);
                    const m = JSON.parse(content.value);
                    manifest = { version: m.version, created: m.created };
                } catch { /* tolerate an unreadable/invalid manifest */ }
            }

            // engine.yaml at the bundle root (editable engine config).
            const engineStat = (root.children || []).find(c => c.isFile && c.name === 'engine.yaml');
            const engineUri = engineStat?.resource.toString();

            this.bundle = { rootUri: rootUri.toString(), transformations, schemas, manifest, manifestUri, engineUri };
            localStorage.setItem(BundleExplorerWidget.LAST_BUNDLE_KEY, rootUri.toString());
        } catch (e) {
            this.bundle = null;
            this.error = e instanceof Error ? e.message : String(e);
        } finally {
            this.loading = false;
            this.update();
        }
    }

    /**
     * Open a transformation (IF03 Phase 2): load its `.utlx` into the UTL-X editor AND
     * bind its input samples to the input panel. We parse the header for the declared
     * input names, match each to a sample file in the transformation folder (by
     * convention: `<name>.*`, `test-input-<name>.*`, or `samples/<name>.*`), push those
     * into the input panel (overwrite by name), THEN load the source — so the header
     * sync rebuilds the tabs and fills them with the matched samples.
     */
    private async openTransformation(t: BundleTx): Promise<void> {
        if (!t.utlxUri) {
            this.messageService.warn(`Transformation "${t.name}" has no .utlx file.`);
            return;
        }
        try {
            const utlxUri = new URI(t.utlxUri);
            const source = (await this.fileService.read(utlxUri)).value;

            const editor = this.shell.getWidgets('main').find(w => w instanceof UTLXEditorWidget) as UTLXEditorWidget | undefined;
            if (!editor) {
                this.messageService.warn('UTL-X editor not available.');
                return;
            }

            // Match declared inputs → sample files, and load them into the input panel.
            let boundCount = 0;
            try {
                const parsed = parseUTLXHeaders(source);
                if (parsed.valid && parsed.inputs.length > 0) {
                    const candidates = await this.collectSampleFiles(utlxUri);
                    const byName: { [name: string]: string } = {};
                    for (const input of parsed.inputs) {
                        const match = this.matchSample(candidates, input.name);
                        if (match) {
                            byName[input.name] = (await this.fileService.read(match.resource)).value;
                        }
                    }
                    boundCount = Object.keys(byName).length;
                    if (boundCount > 0) {
                        const inputPanel = this.shell.getWidgets('left').find(w => w instanceof MultiInputPanelWidget) as MultiInputPanelWidget | undefined;
                        inputPanel?.loadBundleSamples(byName);
                    }
                }
            } catch (e) {
                console.warn('[BundleExplorer] sample binding skipped:', e);
            }

            // Load the source last → header sync rebuilds tabs and fills the samples.
            editor.setContent(source);
            this.shell.activateWidget(editor.id);
            this.messageService.info(
                `Loaded transformation: ${t.name}` + (boundCount > 0 ? ` (+${boundCount} input sample${boundCount === 1 ? '' : 's'})` : ''));
        } catch (e) {
            this.messageService.error('Could not open transformation: ' + (e instanceof Error ? e.message : String(e)));
        }
    }

    /** Files that could be input samples: data files in the tx folder + a samples/ dir. */
    private async collectSampleFiles(utlxUri: URI): Promise<FileStat[]> {
        const out: FileStat[] = [];
        try {
            const dir = await this.fileService.resolve(utlxUri.parent);
            for (const c of (dir.children || [])) {
                if (c.isFile && !c.name.endsWith('.utlx') && c.name !== 'transform.yaml') {
                    out.push(c);
                }
            }
            const samplesDir = (dir.children || []).find(c => c.isDirectory && c.name === 'samples');
            if (samplesDir) {
                const s = await this.fileService.resolve(samplesDir.resource);
                for (const c of (s.children || [])) {
                    if (c.isFile) { out.push(c); }
                }
            }
        } catch { /* none */ }
        return out;
    }

    /** Pick the best sample file for an input name: exact base, then test-input-<name>, then contains. */
    private matchSample(files: FileStat[], inputName: string): FileStat | undefined {
        const base = (n: string) => n.replace(/\.[^.]+$/, '').toLowerCase();
        const target = inputName.toLowerCase();
        return files.find(f => base(f.name) === target)
            || files.find(f => base(f.name) === `test-input-${target}` || base(f.name) === `testinput-${target}`)
            || files.find(f => base(f.name).includes(target));
    }

    /**
     * IF03 Phase 3: build/export a deployable `.utlar` from the open bundle (backend zips
     * manifest.json + transformations/ + schemas/ + engine.yaml, engine-loadable per EF09).
     */
    private async buildBundle(): Promise<void> {
        if (!this.bundle || this.building) { return; }
        this.building = true; this.update();
        try {
            const res = await this.utlxService.buildBundle(this.bundle.rootUri);
            if (res.success) {
                const out = res.outPath ? new URI(res.outPath).path.base : '.utlar';
                this.messageService.info(
                    `Built ${out} — ${res.transformations?.length ?? 0} transformation(s), ${res.schemaCount ?? 0} schema(s).`);
            } else {
                this.messageService.error('Build .utlar failed: ' + (res.error || 'unknown error'));
            }
        } catch (e) {
            this.messageService.error('Build .utlar failed: ' + (e instanceof Error ? e.message : String(e)));
        } finally {
            this.building = false; this.update();
        }
    }

    /** Open a non-transformation file (schema, manifest) in a normal editor tab. */
    private async openFile(uriStr?: string): Promise<void> {
        if (!uriStr) { return; }
        try {
            await open(this.openerService, new URI(uriStr));
        } catch (e) {
            this.messageService.error('Could not open: ' + (e instanceof Error ? e.message : String(e)));
        }
    }

    protected render(): React.ReactNode {
        return (
            <div className='utlx-bundle-explorer-body'>
                <div className='utlx-bundle-toolbar'>
                    <button className='utlx-bundle-btn' onClick={() => this.openBundleDialog()} title='Open a bundle folder'>
                        <span className='codicon codicon-folder-opened'></span> Open Bundle…
                    </button>
                    <button
                        className='utlx-bundle-btn'
                        onClick={() => this.bundle && this.loadBundle(new URI(this.bundle.rootUri))}
                        disabled={!this.bundle}
                        title='Refresh'
                    ><span className='codicon codicon-refresh'></span></button>
                    <button
                        className='utlx-bundle-btn'
                        onClick={() => this.buildBundle()}
                        disabled={!this.bundle || this.building}
                        title='Build / Export a deployable .utlar archive from this bundle'
                    >
                        <span className={`codicon ${this.building ? 'codicon-loading codicon-modifier-spin' : 'codicon-package'}`}></span>
                        {this.building ? ' Building…' : ' Build .utlar'}
                    </button>
                </div>
                {this.loading && <div className='utlx-bundle-status'>Loading…</div>}
                {this.error && <div className='utlx-bundle-error'><span className='codicon codicon-warning'></span> {this.error}</div>}
                {!this.bundle && !this.loading && !this.error &&
                    <div className='utlx-bundle-empty'>No bundle open. Use “Open Bundle…” to pick a folder containing <code>transformations/</code>.</div>}
                {this.bundle && this.renderTree(this.bundle)}
            </div>
        );
    }

    private renderTree(b: BundleModel): React.ReactNode {
        const rootName = new URI(b.rootUri).path.base || b.rootUri;
        return (
            <div className='utlx-bundle-tree'>
                <div className='utlx-bundle-root' title={b.rootUri}>
                    <span className='codicon codicon-package'></span> {rootName}{b.manifest?.version ? `  v${b.manifest.version}` : ''}
                </div>

                <div className='utlx-bundle-group'>transformations ({b.transformations.length})</div>
                {b.transformations.map(t => (
                    <div
                        key={t.name}
                        className={`utlx-bundle-node tx${t.utlxUri ? '' : ' missing'}`}
                        title={t.utlxUri || 'no .utlx file in this transformation folder'}
                        onClick={() => this.openTransformation(t)}
                    >
                        <span className='utlx-bundle-icon codicon codicon-file-code'></span>
                        <span className='utlx-bundle-name'>{t.name}</span>
                        {t.configUri &&
                            <span
                                className='utlx-bundle-badge clickable codicon codicon-gear'
                                title='Edit transform.yaml'
                                onClick={e => { e.stopPropagation(); this.openFile(t.configUri); }}
                            ></span>}
                        {t.sampleCount > 0 && <span className='utlx-bundle-meta'>{t.sampleCount} sample{t.sampleCount === 1 ? '' : 's'}</span>}
                        {!t.utlxUri && <span className='utlx-bundle-warn'>no .utlx</span>}
                    </div>
                ))}

                <div className='utlx-bundle-group'>schemas ({b.schemas.length})</div>
                {b.schemas.map(s => (
                    <div key={s.name} className='utlx-bundle-node schema' title={s.uri} onClick={() => this.openFile(s.uri)}>
                        <span className='utlx-bundle-icon codicon codicon-symbol-structure'></span>
                        <span className='utlx-bundle-name'>{s.name}</span>
                    </div>
                ))}

                <div className='utlx-bundle-group'>config</div>
                {b.engineUri &&
                    <div className='utlx-bundle-node config' title={b.engineUri} onClick={() => this.openFile(b.engineUri)}>
                        <span className='utlx-bundle-icon codicon codicon-gear'></span>
                        <span className='utlx-bundle-name'>engine.yaml</span>
                    </div>}
                {b.manifestUri &&
                    <div className='utlx-bundle-node manifest' title={b.manifestUri} onClick={() => this.openFile(b.manifestUri)}>
                        <span className='utlx-bundle-icon codicon codicon-json'></span>
                        <span className='utlx-bundle-name'>manifest.json</span>
                    </div>}
                {!b.engineUri && !b.manifestUri &&
                    <div className='utlx-bundle-node' style={{ opacity: 0.6, cursor: 'default' }}>
                        <span className='utlx-bundle-icon codicon codicon-dash'></span>
                        <span className='utlx-bundle-name'>(no engine.yaml / manifest)</span>
                    </div>}
            </div>
        );
    }
}
