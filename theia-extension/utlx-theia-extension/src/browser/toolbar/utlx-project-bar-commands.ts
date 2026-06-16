// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 The UTLX IDE Authors
//
// IF24 — registers the Project Bar's items into Theia's TabBarToolbarRegistry. Items reuse the EXISTING
// project/transformation commands (registered by UTLXFrontendContribution, IF18/IF22) — no new
// behavior, just toolbar affordances. `isVisible` targets the Project Bar (so the items never appear in
// a view's tab bar); `group` ('project' | 'transformation') tells the Project Bar widget which labelled
// zone to put them in — Project on the left, Transformation in the centre (over the editor).

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

        // ── PROJECT zone (LEFT): create / open a .utlxp project ──
        registry.registerItem({
            id: 'utlx.project-bar.openProject',
            command: 'utlx.project.open',
            icon: 'codicon codicon-folder-opened',
            tooltip: 'Open UTLX Project…',
            group: 'project',
            priority: 0,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.openRecent',
            command: 'workspace:openRecent',   // built-in (relabeled to "Open Recent UTLX Project…")
            icon: 'codicon codicon-history',
            tooltip: 'Open Recent UTLX Project…',
            group: 'project',
            priority: 1,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.newProject',
            command: 'utlx.project.new',
            icon: 'codicon codicon-new-folder',
            tooltip: 'New UTLX Project…',
            group: 'project',
            priority: 2,   // moved two places right (after Open + Open Recent)
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.saveProject',
            command: 'utlx.transformation.save',
            icon: 'codicon codicon-save',
            tooltip: 'Save UTLX Project',
            group: 'project',
            priority: 3,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.saveProjectAs',
            command: 'utlx.transformation.saveAs',
            icon: 'codicon codicon-save-as',
            tooltip: 'Save UTLX Project As…',
            group: 'project',
            priority: 4,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.closeProject',
            command: 'utlx.project.close',
            icon: 'codicon codicon-close',
            tooltip: 'Close UTLX Project',
            group: 'project',
            priority: 5,
            isVisible: onBar,
        });

        // ── TRANSFORMATION zone (CENTRE, over the editor): operate within the open project ──
        registry.registerItem({
            id: 'utlx.project-bar.switchTransformation',
            command: 'utlx.project.switchTransformation',
            icon: 'codicon codicon-list-selection',
            tooltip: 'Switch Transformation…',
            group: 'transformation',
            priority: 0,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.newTransformation',
            command: 'utlx.project.newTransformation',
            icon: 'codicon codicon-new-file',
            tooltip: 'New Transformation…',
            group: 'transformation',
            priority: 1,
            isVisible: onBar,
        });

        // ── UTLX ARCHIVE zone (after Project): the project's YAML config ──
        registry.registerItem({
            id: 'utlx.project-bar.editTransform',
            command: 'utlx.config.editTransform',
            icon: 'codicon codicon-settings-gear',
            tooltip: 'Edit transform.yaml',
            group: 'config',
            priority: 0,
            isVisible: onBar,
        });
        registry.registerItem({
            id: 'utlx.project-bar.editEngine',
            command: 'utlx.config.editEngine',
            icon: 'codicon codicon-server',
            tooltip: 'Edit engine.yaml',
            group: 'config',
            priority: 1,
            isVisible: onBar,
        });

        // ── EDIT zone: the standard editor commands (Edit menu). These are core.* commands; codicon
        //    lacks undo/cut/paste/find glyphs, so closest matches are used. cut/copy/paste go through
        //    document.execCommand and depend on editor focus (may be inert when clicked from the bar).
        const edit: Array<[string, string, string, number]> = [
            ['utlx.project-bar.undo',    'core.undo',    'codicon codicon-discard', 0],
            ['utlx.project-bar.redo',    'core.redo',    'codicon codicon-redo',    1],
            ['utlx.project-bar.cut',     'core.cut',     'codicon codicon-remove',  2],
            ['utlx.project-bar.copy',    'core.copy',    'codicon codicon-copy',    3],
            ['utlx.project-bar.paste',   'core.paste',   'codicon codicon-clippy',  4],
            ['utlx.project-bar.find',    'core.find',    'codicon codicon-search',  5],
            ['utlx.project-bar.replace', 'core.replace', 'codicon codicon-replace', 6],
        ];
        for (const [id, command, icon, priority] of edit) {
            registry.registerItem({
                id, command, icon,
                tooltip: command.replace('core.', '').replace(/^\w/, c => c.toUpperCase()),
                group: 'edit',
                priority,
                isVisible: onBar,
            });
        }
    }
}
