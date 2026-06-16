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
import { ApplicationShell } from '@theia/core/lib/browser';
import { TheiaSplitPanel } from '@theia/core/lib/browser/shell/theia-split-panel';
import { UtlxProjectBar } from '../toolbar/utlx-project-bar';

@injectable()
export class UtlxApplicationShell extends ApplicationShell {

    @inject(UtlxProjectBar)
    protected readonly utlxProjectBar!: UtlxProjectBar;

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

        // [menu/top panel] → [Project Bar row, fixed height] → [left|main|right] → [status bar]
        return this.createBoxLayout(
            [this.topPanel, this.utlxProjectBar, panelForSideAreas, this.statusBar],
            [0, 0, 1, 0],
            { direction: 'top-to-bottom', spacing: 0 });
    }
}
