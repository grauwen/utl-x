// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 The UTLX IDE Authors
//
// IF24 — registers the Project Bar's items into Theia's TabBarToolbarRegistry. Items reuse the EXISTING
// project/transformation commands (registered by UTLXFrontendContribution, IF18/IF22) — no new
// behavior, just toolbar affordances. `isVisible` targets the Project Bar (so the items never appear in
// a view's tab bar); `group` ('left' | 'right') tells the single Project Bar widget which side to put
// them on.

import { injectable } from 'inversify';
import { Widget } from '@theia/core/lib/browser';
import {
    TabBarToolbarContribution,
    TabBarToolbarRegistry,
} from '@theia/core/lib/browser/shell/tab-bar-toolbar';
import { UtlxProjectBar } from './utlx-project-bar';

@injectable()
export class UtlxProjectBarItems implements TabBarToolbarContribution {

    registerToolbarItems(registry: TabBarToolbarRegistry): void {
        const onBar = (w?: Widget) => UtlxProjectBar.is(w);

        // ── LEFT: transformation actions (operate within the open project) ──
        registry.registerItem({
            id: 'utlx.project-bar.switchTransformation',
            command: 'utlx.project.switchTransformation',
            icon: 'codicon codicon-list-selection',
            tooltip: 'Switch Transformation…',
            group: 'left',
            priority: 0,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.newTransformation',
            command: 'utlx.project.newTransformation',
            icon: 'codicon codicon-new-file',
            tooltip: 'New Transformation…',
            group: 'left',
            priority: 1,
            isVisible: onBar,
        });

        // ── RIGHT: project actions (create / open a .utlxp project) ──
        registry.registerItem({
            id: 'utlx.project-bar.newProject',
            command: 'utlx.project.new',
            icon: 'codicon codicon-new-folder',
            tooltip: 'New UTLX Project…',
            group: 'right',
            priority: 0,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.openProject',
            command: 'utlx.project.open',
            icon: 'codicon codicon-folder-opened',
            tooltip: 'Open UTLX Project…',
            group: 'right',
            priority: 1,
            isVisible: onBar,
        });
    }
}
