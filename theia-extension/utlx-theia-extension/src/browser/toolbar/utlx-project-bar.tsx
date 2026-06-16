// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 The UTLX IDE Authors
//
// IF24 — the "Project Bar": an Arduino-style, full-width toolbar shown as its OWN row directly under
// the menu + Action Bar. Built on Theia's TabBarToolbarRegistry (the data source Arduino uses), ported
// to the Theia 1.64 toolbar API. ARCHITECTURE B placement: the widget is inserted as a dedicated row in
// the shell's vertical layout (see shell/utlx-application-shell.ts) — so it never touches the top
// panel's CSS, and the menu + Action Bar are unaffected.
//
// A single widget renders BOTH sides; items choose a side via their `group` ('left' | 'right').

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget, Widget, Message } from '@theia/core/lib/browser';
import { TabBarToolbarRegistry, TabBarToolbarAction } from '@theia/core/lib/browser/shell/tab-bar-toolbar';
// `TabBarToolbarItem` (the runtime item interface) is not re-exported from the barrel in 1.64.
import { TabBarToolbarItem } from '@theia/core/lib/browser/shell/tab-bar-toolbar/tab-toolbar-item';
import { CommandRegistry } from '@theia/core/lib/common';

@injectable()
export class UtlxProjectBar extends ReactWidget {
    static readonly ID = 'utlx-project-bar';

    @inject(TabBarToolbarRegistry)
    protected readonly registry!: TabBarToolbarRegistry;

    @inject(CommandRegistry)
    protected readonly commands!: CommandRegistry;

    protected items: TabBarToolbarItem[] = [];

    /** Type guard so items can target this bar in their `isVisible`. */
    static is(widget?: Widget | null): widget is UtlxProjectBar {
        return widget instanceof UtlxProjectBar;
    }

    @postConstruct()
    protected init(): void {
        this.id = UtlxProjectBar.ID;
        this.addClass('utlx-project-bar');
        this.title.closable = false;
        this.refresh();
        // Re-collect when items are (un)registered; re-render when the command set changes.
        this.toDispose.push(this.registry.onDidChange(() => this.refresh()));
        this.toDispose.push(this.commands.onCommandsChanged(() => this.update()));
    }

    protected override onAfterAttach(msg: Message): void {
        super.onAfterAttach(msg);
        // By attach time the registry/contributions are populated — re-collect so the bar isn't empty
        // if it was constructed before the toolbar items registered.
        this.refresh();
    }

    protected refresh(): void {
        this.items = this.registry.visibleItems(this).sort(TabBarToolbarAction.PRIORITY_COMPARATOR);
        this.update();
    }

    protected render(): React.ReactNode {
        // Theia 1.64: each runtime item carries render(widget) — it draws the icon button and wires
        // click / enabled / toggled / tooltip itself (resolving the command via CommandRegistry).
        //
        // Labelled zones so the bar is self-explanatory even when the menu bar is detached (Electron/
        // macOS native menu). LEFT: PROJECT, a divider, then UTLX ARCHIVE (the project's config —
        // transform.yaml / engine.yaml). CENTRE: TRANSFORMATION (over the editor). RIGHT: EDIT (the
        // standard editor commands — undo/redo/cut/copy/paste/find/replace).
        const group = (g: string) => this.items
            .filter(i => i.group === g)
            .map(i => <React.Fragment key={i.id}>{i.render(this)}</React.Fragment>);
        return (
            <div className='utlx-project-bar-container'>
                <div className='utlx-pb-zone utlx-pb-left'>
                    <span className='utlx-pb-label'>Project</span>
                    <div className='utlx-project-bar-items'>{group('project')}</div>
                    <span className='utlx-pb-divider' />
                    <span className='utlx-pb-label'>UTLX Archive</span>
                    <div className='utlx-project-bar-items'>{group('config')}</div>
                </div>
                <div className='utlx-pb-zone utlx-pb-center'>
                    <span className='utlx-pb-label'>Transformation</span>
                    <div className='utlx-project-bar-items'>{group('transformation')}</div>
                </div>
                <div className='utlx-pb-zone utlx-pb-right'>
                    <span className='utlx-pb-label'>Edit</span>
                    <div className='utlx-project-bar-items'>{group('edit')}</div>
                </div>
            </div>
        );
    }
}
