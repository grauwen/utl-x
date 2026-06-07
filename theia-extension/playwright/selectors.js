/* eslint-disable */
/**
 * UTLX IDE — Playwright selector source-of-truth.  Mirror of ui-map.md.
 *
 * One place to fix a selector when a widget changes. Importable from both:
 *   - kiosk-demo.js (CommonJS):   const { SEL, TID, CMD, KEY, WIDGET } = require('./selectors');
 *   - tests/*.spec.ts (TS/ESM):   import { SEL, TID, CMD, KEY, WIDGET } from '../selectors';
 *
 * Anchoring order (most → least stable):
 *   1. CMD / KEY  — invoke a Theia command / keybinding; bypasses the DOM entirely.
 *   2. TID        — data-testid via page.getByTestId(TID.x)  (Playwright's default testIdAttribute).
 *   3. SEL        — CSS string via page.locator(SEL.x); same testids as [data-testid="..."],
 *                   plus the two non-React exceptions (Monaco, status-bar switches).
 *
 * Verified against widget source on branch feature/ide (June 2026). Full catalog: ui-map.md.
 */

// ── Stable contracts ────────────────────────────────────────────────────────
const CMD = {
  execute:        'utlx.executeTransformation',
  validate:       'utlx.validateCode',
  inferSchema:    'utlx.inferSchema',
  toggleMode:     'utlx.toggleMode',
  loadInput:      'utlx.loadInput',
  loadSchema:     'utlx.loadSchema',
  saveOutput:     'utlx.saveOutput',
  clearPanels:    'utlx.clearPanels',
  restartDaemon:  'utlx.restartDaemon',
  showFunctions:  'utlx.showFunctions',
  toggleFileDialogMode: 'utlx.toggleFileDialogMode',
  toggleNameOnLoadMode: 'utlx.toggleNameOnLoadMode',
};

// Keybindings (Playwright form). NB: no Ctrl+Enter — execute is shift+e.
// Press with the platform Mod: 'Meta' on darwin, 'Control' elsewhere.
const KEY = {
  execute:     'Shift+E',
  validate:    'Shift+V',
  toggleMode:  'Shift+M',
  clearPanels: 'Shift+C',
};

const WIDGET = {
  toolbar: 'utlx-toolbar',
  input:   'utlx-input-panel',
  editor:  'utlx-editor',
  output:  'utlx-output-panel',
  health:  'utlx-health-monitor',
};

// localStorage preconditions (set via context.addInitScript before page load)
const PRECONDITION = {
  fileDialogMode: 'utlx.fileDialogMode', // 'theia' | 'browser'
  nameOnLoadMode: 'utlx.nameOnLoadMode', // 'inherit' | 'keep'
};

// ── data-testid registry (use with page.getByTestId) ─────────────────────────
const TID = {
  // Toolbar
  modeToggle: 'utlx-mode-toggle',
  aiAssist:   'utlx-ai-assist',
  execute:    'utlx-execute',          // label is "Execute" / "Validate" by mode

  // Input pane
  inputTextarea:   'utlx-input',
  inputName:       'utlx-input-name',
  inputFormat:     'utlx-input-format',
  inputLoad:       'utlx-input-load',
  inputClear:      'utlx-input-clear',
  inputAdd:        'utlx-input-add',
  inputDelete:     'utlx-input-delete',
  inputTab:        'utlx-input-tab',
  inputTabInstance:'utlx-input-tab-instance',
  inputTabSchema:  'utlx-input-tab-schema',
  inputSchemaOnly: 'utlx-input-schema-only',
  inputInferSchema:'utlx-input-infer-schema',
  inputValidate:   'utlx-input-validate',
  inputViewUdm:    'utlx-input-view-udm',
  inputInfo:       'utlx-input-info',
  inputCsvHeaders: 'utlx-input-csv-headers',
  inputCsvDelim:   'utlx-input-csv-delimiter',

  // Transform / editor toolbar
  editorViewClassic:   'utlx-editor-view-classic',
  editorViewCanvas:    'utlx-editor-view-canvas',
  editorScaffold:      'utlx-editor-scaffold',
  editorFunctionBuilder:'utlx-editor-function-builder',
  editorLoad:          'utlx-editor-load',
  editorSave:          'utlx-editor-save',
  editorClear:         'utlx-editor-clear',
  monaco:              'utlx-monaco',   // the Monaco mount container

  // Output pane
  outputName:        'utlx-output-name',
  outputFormat:      'utlx-output-format',
  outputViewMode:    'utlx-output-view-mode',
  outputCopy:        'utlx-output-copy',
  outputLoad:        'utlx-output-load',
  outputSave:        'utlx-output-save',
  outputClear:       'utlx-output-clear',
  outputInferSchema: 'utlx-output-infer-schema',
  outputTabInstance: 'utlx-output-tab-instance',
  outputTabSchema:   'utlx-output-tab-schema',
  outputSchemaOnly:  'utlx-output-schema-only',
  output:            'utlx-output',        // success output <pre>  (assertion target)
  outputError:       'utlx-output-error',  // error <pre>           (assertion target)
  // format-option controls: utlx-output-csv-headers|csv-delimiter|csv-bom|xml-encoding|
  //                         odata-metadata|odata-context|odata-wrap   (see ui-map.md)
};

// ── CSS selectors (use with page.locator) ────────────────────────────────────
// Mostly [data-testid="..."]; two exceptions are framework-owned DOM (no testid):
//   - Monaco's inner editor surface (testid is on the container; .monaco-editor is the child)
//   - the status-bar switches (Theia StatusBar exposes only className)
const td = (id) => `[data-testid="${id}"]`;

const SEL = {
  // Toolbar
  executeBtn:  td(TID.execute),
  modeToggle:  td(TID.modeToggle),   // prefer CMD.toggleMode for driving
  aiAssistBtn: td(TID.aiAssist),

  // Input pane
  inputTextarea:    td(TID.inputTextarea),
  inputNameEditor:  td(TID.inputName),
  inputFormatSelect:td(TID.inputFormat),
  inputLoadBtn:     td(TID.inputLoad),
  inputClearBtn:    td(TID.inputClear),
  inputAddBtn:      td(TID.inputAdd),
  inputTab:         td(TID.inputTab),

  // Transform / Monaco
  monacoContainer: td(TID.monaco),
  monaco:          `${td(TID.monaco)} .monaco-editor`,  // click/type target
  editorLoadBtn:   td(TID.editorLoad),
  editorSaveBtn:   td(TID.editorSave),
  editorClearBtn:  td(TID.editorClear),

  // Output pane
  outputFormatSelect: td(TID.outputFormat),
  outputViewMode:     td(TID.outputViewMode),
  outputCopyBtn:      td(TID.outputCopy),
  outputClearBtn:     td(TID.outputClear),
  outputResult:       td(TID.output),       // success <pre>
  outputError:        td(TID.outputError),  // error <pre>

  // Status bar (framework-owned DOM — className hooks, not testids)
  sbFileDialogMode: '.utlx-sb-file-dialog-mode',
  sbNameOnLoadMode: '.utlx-sb-name-on-load-mode',
};

// Monaco model URI (read text via window.monaco.editor.getModels)
const MONACO_URI_FRAGMENT = 'utlx-editor'; // inmemory://utlx-editor/transformation.utlx

module.exports = { SEL, TID, CMD, KEY, WIDGET, PRECONDITION, MONACO_URI_FRAGMENT };
