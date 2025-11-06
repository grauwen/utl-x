# Eclipse Theia - Complete Panes Overview

## Application Shell Structure

Eclipse Theia uses an **Application Shell** layout system that organizes the UI into distinct areas. Here's a complete breakdown of all panes and their names.

---

## Visual Layout Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         TOP PANEL (Menu Bar)                            │
│  File  Edit  View  Selection  Terminal  Run  Help                      │
├─────────────────────────────────────────────────────────────────────────┤
│                    TOOLBAR (Optional - v1.23+)                          │
│  [Buttons] [Widgets] [Custom UI Elements]                              │
├────────┬────────────────────────────────────────────────────────┬──────┤
│        │                                                        │      │
│  LEFT  │                                                        │RIGHT │
│ PANEL  │                  MAIN AREA                             │PANEL │
│        │              (Editor Area)                             │      │
│        │                                                        │      │
│ [Tabs] │  ┌──────────────────────────────────────────┐        │[Tabs]│
│        │  │                                            │        │      │
│ View 1 │  │         Editor / Widget Content           │        │View N│
│ View 2 │  │                                            │        │      │
│ View 3 │  │                                            │        │      │
│        │  └──────────────────────────────────────────┘        │      │
│        │                                                        │      │
├────────┴────────────────────────────────────────────────────────┴──────┤
│                        BOTTOM PANEL                                     │
│  Problems | Output | Debug Console | Terminal                          │
│                                                                         │
│  [Panel Content Area]                                                  │
│                                                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                         STATUS BAR                                      │
│  Branch | Errors ⚠ | Warnings ⚠ | Ln 1, Col 1 | UTF-8 | LF | TypeScript│
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Pane Names and IDs

### 1. **Top Panel** (`theia-top-panel`)
- **Purpose**: Main menu bar
- **Contains**: File, Edit, View, Selection, Terminal, Run, Help menus
- **CSS ID**: `#theia-top-panel`
- **Type**: Fixed, always visible
- **Position**: Top of the application

---

### 2. **Toolbar** (Optional, v1.23+)
- **Purpose**: Quick access buttons and custom widgets
- **Contains**: Customizable buttons and UI widgets
- **Type**: Optional, can be toggled
- **Toggle**: View → Toggle Toolbar
- **Position**: Below top panel

---

### 3. **Left Panel** (`theia-left-content-panel`)
- **Purpose**: Side panel for navigation and views
- **CSS ID**: `#theia-left-content-panel`
- **Type**: Collapsible side panel
- **Orientation**: Vertical tabs
- **Default Width**: Configurable

#### Common Left Panel Views:
- **Explorer** (`navigator`)
  - File tree/project browser
  - Shows workspace files and folders
  
- **Search** (`search-in-workspace.view`)
  - Search and replace across workspace
  
- **Source Control / SCM** (`scm-view`)
  - Git integration
  - Stage, commit, push/pull
  
- **Debug** (`debug-view`)
  - Debug configurations
  - Breakpoints
  - Variables
  
- **Extensions** (`extensions`)
  - Manage VS Code extensions
  
- **Outline** (`outline-view`)
  - Document structure
  - Symbols in current file

---

### 4. **Main Area** / **Main Panel** (`theia-main-content-panel`)
- **Purpose**: Primary work area for editors
- **CSS ID**: `#theia-main-content-panel`
- **Area ID**: `MAIN_AREA_ID`
- **Type**: Main content area
- **Features**: 
  - Supports multiple editors
  - Tab-based interface
  - Split editors (horizontal/vertical)
  - Drag & drop support

#### Editor Features:
- **Monaco Editor Integration**
  - Syntax highlighting
  - IntelliSense
  - Code completion
  - Error markers
  - Multiple cursors

---

### 5. **Right Panel** (`theia-right-content-panel`)
- **Purpose**: Secondary side panel (less commonly used)
- **CSS ID**: `#theia-right-content-panel`
- **Type**: Collapsible side panel
- **Orientation**: Vertical tabs
- **Default State**: Usually collapsed/empty

#### Potential Right Panel Views:
- Custom widgets
- Preview panels
- Documentation views
- Additional tool panels

---

### 6. **Bottom Panel** (`theia-bottom-content-panel`)
- **Purpose**: Output, debugging, and terminal
- **CSS ID**: `#theia-bottom-content-panel`
- **Area ID**: `BOTTOM_AREA_ID`
- **Type**: Collapsible bottom panel
- **Features**: Tab-based, splittable

#### Common Bottom Panel Views:
- **Problems** (`problems`)
  - Errors and warnings
  - Validation issues
  
- **Output** (`outputView`)
  - Build output
  - Extension logs
  - Server messages
  
- **Debug Console** (`debug.console`)
  - Debug REPL
  - Debug output
  
- **Terminal** (`terminal-view`)
  - Integrated terminal
  - Multiple terminal instances
  - Split terminals
  
- **Search Results**
  - Search matches
  - Replace preview

---

### 7. **Status Bar** (`theia-statusbar`)
- **Purpose**: Status information and quick actions
- **CSS ID**: `#theia-statusbar`
- **Type**: Fixed, always visible
- **Position**: Bottom of the application

#### Status Bar Sections:

**Left Side:**
- Git branch
- Sync status
- Error count (🔴 number)
- Warning count (⚠️ number)

**Right Side:**
- Line and column number (Ln X, Col Y)
- File encoding (UTF-8)
- End of line sequence (LF/CRLF)
- Language mode (JavaScript, TypeScript, etc.)
- Indentation (Spaces: 4)
- Bottom panel toggle

---

## Panel Behaviors and Features

### Collapsible Panels
All side panels (left, right, bottom) can be:
- ✅ Collapsed/Expanded by clicking panel titles
- ✅ Hidden when empty
- ✅ Resized by dragging sashes/dividers
- ✅ Auto-hidden during drag operations

### Widget Management
- **Widgets** = Generic term for views/editors in Theia
- **Singleton Widgets** = Views that open only once (Explorer, Git)
- **Multi-instance Widgets** = Editors (can have multiple instances)

### Drag & Drop Support
- Drag widgets between any areas
- Drag from left panel → main area
- Drag from main area → bottom panel
- Drag from bottom → right panel
- Panels auto-show during drag when hidden

### Layout Persistence
- Layout is saved per workspace
- Restored on next session
- Can be reset: Command Palette → "Reset Workbench Layout"

---

## CSS Classes and IDs Reference

### Shell Structure
```css
.theia-ApplicationShell          /* Root application shell */
.theia-app-sides                 /* Left and right panels container */
.theia-app-centers               /* Main and bottom area container */
.theia-app-main                  /* Main area */
.theia-app-bottom                /* Bottom area */
```

### Panel IDs
```css
#theia-top-panel                 /* Top menu bar */
#theia-left-content-panel        /* Left side panel */
#theia-right-content-panel       /* Right side panel */
#theia-main-content-panel        /* Main editor area */
#theia-bottom-content-panel      /* Bottom panel */
#theia-statusbar                 /* Status bar */
#theia-bottom-split-panel        /* Main + Bottom split */
#theia-left-right-split-panel    /* Left + Center + Right split */
```

### Side Panel Components
```css
.theia-side-panel                /* Generic side panel */
.p-TabBar                        /* Tab bar widget */
.p-DockPanel                     /* Docking panel */
.p-SplitPanel                    /* Split panel */
```

---

## Panel Customization Examples

### Hide/Show Panels via Commands
```typescript
// Toggle bottom panel
commands.executeCommand('core.toggle.bottom.panel');

// Toggle left panel
commands.executeCommand('workbench.action.toggleSidebarVisibility');
```

### Add Custom Top Panel
Override `ApplicationShell.createLayout()` to add custom panels

### Customize Initial Layout
Contribute to `initialLayout` configuration point

---

## Common View IDs

### Built-in Views
- `navigator` - File Explorer
- `outline-view` - Outline
- `scm-view` - Source Control
- `search-in-workspace.view` - Search
- `debug-view` - Debug
- `problems` - Problems
- `terminal-view` - Terminal
- `outputView` - Output
- `debug.console` - Debug Console

---

## Architecture Notes

### Framework
- Built on **Lumino** (formerly Phosphor.js)
- Uses **widgets** for all UI components
- **Dependency injection** via InversifyJS

### Panel Types
1. **Fixed Panels**: Top panel, Status bar
2. **Collapsible Panels**: Left, Right, Bottom
3. **Main Area**: Special docking area for editors

### Layout System
- **SplitPanel**: Horizontal/vertical splits
- **DockPanel**: Tab-based docking
- **BoxLayout**: Top-to-bottom layout

---

## Key Differences from VS Code

| Feature | Theia | VS Code |
|---------|-------|---------|
| Perspectives | ❌ Not supported | ❌ Not supported |
| Right Panel | ✅ Supported | ❌ Not available |
| Toolbar | ✅ Optional (v1.23+) | ❌ Not available |
| Panel Splitting | ✅ All panels | ⚠️ Limited |
| Customization | ✅ Full control | ⚠️ Limited API |

---

## Terminology Mapping

| Theia Term | VS Code Term | Eclipse IDE Term |
|------------|--------------|------------------|
| Widget | View/Editor | View/Editor |
| Main Area | Editor Group | Editor Area |
| Left Panel | Sidebar | View Stack |
| Bottom Panel | Panel | View Stack |
| Application Shell | Workbench | Workbench |

---

## Tips and Best Practices

1. **Use Command Palette (F1)** to discover available views
2. **Drag tabs** to customize your layout
3. **Double-click tab titles** to maximize/restore
4. **Right-click tabs** for context menu options
5. **Use keyboard shortcuts** to toggle panels:
   - `Ctrl+B` - Toggle left sidebar
   - `Ctrl+J` - Toggle bottom panel
   - `Ctrl+\`` - Toggle terminal

---

## Resources

- **Official Docs**: https://theia-ide.org/docs/
- **Widget Guide**: https://theia-ide.org/docs/widgets/
- **ApplicationShell Source**: `packages/core/src/browser/shell/application-shell.ts`
- **Layout Guide**: https://www.typefox.io/blog/flexible-window-layout-in-theia-ide/

---

## Summary

Eclipse Theia provides a **flexible, modular shell** with these main areas:

1. **Top Panel** - Menu bar
2. **Toolbar** - Optional quick actions (v1.23+)
3. **Left Panel** - Primary navigation (Explorer, SCM, Search, Debug)
4. **Main Area** - Editor workspace
5. **Right Panel** - Secondary views (optional)
6. **Bottom Panel** - Output, Terminal, Problems, Debug Console
7. **Status Bar** - Status information and quick actions

All panels are customizable, and the layout is fully flexible through drag & drop.
