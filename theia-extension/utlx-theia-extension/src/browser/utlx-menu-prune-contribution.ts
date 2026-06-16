// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 The UTLX IDE Authors
//
// Removes default Theia top-level menus that don't apply to the UTL-X UI.
//
// `unregisterMenuAction(id)` removes ANY menu node with that id from the tree — including a whole
// SUBMENU and its children (Theia 1.64 "new menu model": removeActionInSubtree walks from the root
// and calls parent.removeNode without distinguishing actions from submenus). The underlying COMMANDS
// and KEYBINDINGS are untouched — only the menu entries disappear.
//
// Done in registerMenus (runs while the menu model is built, before the menubar first renders). Order
// is fine because this extension loads after @theia/core / @theia/editor / @theia/monaco, so their
// menus already exist when this runs. (If a removal ever silently no-ops, guard with
// menus.getMenuNode(...) or move this to a FrontendApplicationContribution.onStart.)

import { injectable } from '@theia/core/shared/inversify';
import { MenuContribution, MenuModelRegistry } from '@theia/core/lib/common';
import { CommonMenus } from '@theia/core/lib/browser';
import { EditorMainMenu } from '@theia/editor/lib/browser/editor-menu';

@injectable()
export class UtlxMenuPruneContribution implements MenuContribution {

    registerMenus(menus: MenuModelRegistry): void {
        const lastSegment = (path: string[]) => path[path.length - 1];
        // Menus with no value for the single-`.utlx` UTL-X UI:
        //  • Go (@theia/editor) — editor-group/file/symbol navigation.
        //  • Edit (core) — Undo/Redo/Cut/Copy/Paste/Find/Replace; already on the Project Bar + shortcuts.
        //  • Selection (@theia/monaco, '3_selection') — advanced multi-cursor/line ops; on keybindings +
        //    the editor right-click context menu.
        //  • View (core) — workbench toggles/appearance; reachable via the Command Palette (Ctrl/Cmd+Shift+P).
        const ids = [
            lastSegment(EditorMainMenu.GO),   // '5_go'
            lastSegment(CommonMenus.EDIT),    // '2_edit'
            '3_selection',                    // MonacoMenus.SELECTION (avoid importing the monaco menu module)
            lastSegment(CommonMenus.VIEW),    // '4_view'
        ];
        for (const id of ids) {
            menus.unregisterMenuAction(id);
        }
    }
}
