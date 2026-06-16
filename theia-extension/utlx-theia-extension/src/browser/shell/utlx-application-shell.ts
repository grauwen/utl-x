// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 The UTLX IDE Authors
//
// IF24 (Architecture B) — places the Project Bar as its OWN full-width row in the shell's vertical
// layout, directly under the menu/top panel and above the left|main|right area. This is the robust
// placement (the CSS flex-wrap approach disrupted the menu row + the Action Bar's width:100%): it
// touches no global CSS, so the menu and the Action Bar are completely unaffected.
//
// We override createLayout() — replicating the base body faithfully (panel ids, stretch arrays,
// spacing) and only inserting `utlxProjectBar` as a fixed-height (stretch 0) row. createLayout() runs
// from @postConstruct init(), after inversify has set property-injected fields, so `utlxProjectBar` is
// defined here. (Same pattern as @theia/toolbar / Arduino's ToolbarApplicationShell.)

import { injectable, inject } from 'inversify';
import { ApplicationShell, Widget } from '@theia/core/lib/browser';
import { Panel } from '@theia/core/shared/@lumino/widgets';
import { TheiaSplitPanel } from '@theia/core/lib/browser/shell/theia-split-panel';
import { UtlxProjectBar } from '../toolbar/utlx-project-bar';

@injectable()
export class UtlxApplicationShell extends ApplicationShell {

    @inject(UtlxProjectBar)
    protected readonly utlxProjectBar!: UtlxProjectBar;

    // An EMPTY slot row for the Action Bar. We must NOT inject UTLXToolbarWidget here — it injects
    // ApplicationShell, so referencing it during shell construction cycles → renderer crash. Instead the
    // slot is a plain Panel created in createLayout(); the Action Bar is mounted into it AFTER startup
    // (mountActionBar), once the shell is fully built (no cycle).
    protected actionBarSlot!: Panel;

    protected override createLayout() {
        const bottomSplitLayout = this.createSplitLayout(
            [this.mainPanel, this.bottomPanel], [1, 0],
            { orientation: 'vertical', spacing: 0 });
        const panelForBottomArea = new TheiaSplitPanel({ layout: bottomSplitLayout });
        panelForBottomArea.id = 'theia-bottom-split-panel';

        const leftRightSplitLayout = this.createSplitLayout(
            [this.leftPanelHandler.container, panelForBottomArea, this.rightPanelHandler.container],
            [0, 1, 0],
            { orientation: 'horizontal', spacing: 0 });
        const panelForSideAreas = new TheiaSplitPanel({ layout: leftRightSplitLayout });
        panelForSideAreas.id = 'theia-left-right-split-panel';

        this.actionBarSlot = new Panel();
        this.actionBarSlot.id = 'utlx-action-bar-slot';
        this.actionBarSlot.addClass('utlx-action-bar-slot');

        // [menu/top panel] → [Action Bar slot] → [Project Bar] → [left|main|right] → [status bar]
        // Both toolbar rows are fixed-height (stretch 0); the side areas take the rest (stretch 1).
        return this.createBoxLayout(
            [this.topPanel, this.actionBarSlot, this.utlxProjectBar, panelForSideAreas, this.statusBar],
            [0, 0, 0, 1, 0],
            { direction: 'top-to-bottom', spacing: 0 });
    }

    /**
     * Mount the Action Bar into its slot. Called AFTER startup (from the frontend contribution) so the
     * Action Bar — which injects ApplicationShell — is resolved only once the shell is fully built,
     * avoiding the construction cycle. Panel.addWidget handles attach lifecycle/timing.
     */
    mountActionBar(widget: Widget): void {
        if (widget.parent === this.actionBarSlot) { return; }
        this.actionBarSlot.addWidget(widget);
    }
}
