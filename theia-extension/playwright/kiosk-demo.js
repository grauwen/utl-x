/* eslint-disable */
/**
 * UTL-X IDE — talk demo (kiosk).  Scenario: playwright/scenarios/scenario1.md
 *
 *   "Write once → output JSON, then XML, then YAML."  Shows the language + IDE ease.
 *
 * What it does (per scenario 1):
 *   precondition: Load Theia, Name Keep  → set via localStorage so the input stays named
 *                 "input" (so $input works — see B24).
 *   1. put a JSON input in the Input panel        (examples/json/00-enterprise-order.json)
 *   2. output format defaults to json
 *   3. type a small UTL-X transform in Monaco
 *   4. Execute → show result
 *   5. switch output → xml ; 6. Execute
 *   7. switch output → yaml; 8. Execute
 *   9. load the full enterprise transform (examples/json/00-enterprise-order-to-fulfillment-ticket.utlx)
 *   10. slow-scroll Monaco to show its complexity
 *   11. Execute → full fulfillment ticket
 *   12. slow-scroll the result
 *   (loop, with a pause, until you Ctrl-C)
 *
 * Run (Playwright resolves from playwright-mcp-server):
 *   cd theia-extension/playwright && npm i && node kiosk-demo.js
 *   # or: NODE_PATH=../../playwright-mcp-server/node_modules node kiosk-demo.js
 *
 * PREREQ: Theia must be running on :4000 with utlxd (run rebuild-and-start-mcp.sh first).
 *
 * NOTE (trial 1): selectors are derived from the CURRENT widget source, but VERIFY on first
 * run — every selector is a named constant in CONFIG so a mismatch is a one-line fix. The
 * two fragile spots are flagged: (a) filling the input textarea, (b) focusing Monaco.
 */
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright');
const { execSync } = require('child_process');
const { SEL, PRECONDITION } = require('./selectors');   // single selector source of truth (mirrors ui-map.md)

// Best-effort screen size in logical px (Chrome --window-size + osascript desktop bounds both use
// logical points, so they match on Retina). macOS via osascript; falls back to 1920×1080.
function screenSize() {
  try {
    if (process.platform === 'darwin') {
      const out = execSync(`osascript -e 'tell application "Finder" to get bounds of window of desktop'`, { encoding: 'utf8' }).trim();
      const p = out.split(',').map(s => parseInt(s.trim(), 10));
      if (p[2] > 0 && p[3] > 0) return { w: p[2], h: p[3] };
    }
  } catch { /* fall through to default */ }
  return { w: 1920, h: 1080 };
}

const REPO = path.resolve(__dirname, '../..');

const CONFIG = {
  baseURL: 'http://localhost:4000',
  inputFile: path.join(REPO, 'examples/json/00-enterprise-order.json'),
  // Step 9: the matching real-world transform for the SAME input — shows the language at scale.
  exampleUtlxFile: path.join(REPO, 'examples/json/00-enterprise-order-to-fulfillment-ticket.utlx'),
  // Step 28: a second input loaded alongside the order (name inherited from the file → employee-roster).
  rosterCsvFile: path.join(REPO, 'examples/csv/01-employee-roster.csv'),
  // Step 30: a small mapping that reads BOTH inputs.
  dualInputTransform: [
    'orderId: $input.orderId,',
    'customer: $input.customer.companyName,',
    'employeeCount: count($employee-roster)'
  ].join('\n'),

  // The transform BODY (inner fields only). Clear provides the `{ }` wrapper + header;
  // we replace only the `// Your transformation code here` placeholder. Header is never touched.
  transform: [
    'orderId: $input.orderId,',
    'customer: $input.customer.companyName,',
    'status: $input.orderStatus,',
    'total: $input.totalAmount,',
    'currency: $input.currency'
  ].join('\n'),

  outputFormats: ['json', 'xml', 'yaml'],   // steps 2/5/7

  // Selectors come from ./selectors.js (the source of truth; full catalog in ui-map.md).
  // These are data-testid based → they require the extension to be rebuilt (rebuild-and-start-mcp.sh).
  sel: SEL,

  // ── pacing (demo speed) ──────────────────────────────────────────────────
  slowMo: 380,            // ms per Playwright action (visible, watchable)
  typeDelay: 65,          // ms per keystroke when typing the transform
  beat: 1800,             // pause between steps (narration room)
  afterExecute: 4200,     // pause to let the audience read each result (extra time after every Execute)
  betweenLoops: 6000,     // pause before repeating

  // Run modes (default = one pass, then leave the browser OPEN — best for a talk):
  //   (none)    → one pass, then leave the browser open (Ctrl-C to close + flush video)
  //   DEMO_LOOP → repeat until Ctrl-C (kiosk attract mode)
  //   DEMO_ONCE → one pass, then close (flush video — for recording)
  loop: !!process.env.DEMO_LOOP,
  stayOpen: !process.env.DEMO_LOOP && !process.env.DEMO_ONCE,
  recordVideoDir: path.join(__dirname, 'recordings'),  // talk-safe fallback video
};

const MOD = process.platform === 'darwin' ? 'Meta' : 'Control';
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function narrate(msg) { console.log(`\n▶ ${msg}`); }

async function setOutputFormat(page, fmt) {
  await page.locator(CONFIG.sel.outputFormatSelect).first().selectOption(fmt);
}

// Write the transform body WITHOUT ever touching the header. The IDE's Clear button
// (clearContent) keeps the header lines and resets the body to `{ // Your transformation code
// here }`. So: click Clear, then replace just that placeholder line with the body. The header
// is never recreated or edited.
const BODY_PLACEHOLDER = '// Your transformation code here';
async function setTransformBody(page, body) {
  // 1. Clear → preserves the header, body becomes `{ <placeholder> }`.
  await page.locator(SEL.editorClearBtn).first().click();
  await sleep(500);
  // 2. Select the placeholder and type the body over it (header untouched; typing stays visible).
  await page.locator(SEL.monaco).first().click();          // focus Monaco
  await page.keyboard.press(`${MOD}+f`);                   // open find
  await page.keyboard.type(BODY_PLACEHOLDER);
  await page.keyboard.press('Enter');                      // select the placeholder
  await page.keyboard.press('Escape');                     // close find (placeholder still selected)
  await page.keyboard.type(body, { delay: CONFIG.typeDelay });
}

async function execute(page) {
  await page.locator(CONFIG.sel.executeBtn).first().click();
  // Wait for a fresh result (or an error) to render.
  await page.locator(`${CONFIG.sel.outputResult}, ${CONFIG.sel.outputError}`).first()
    .waitFor({ state: 'visible', timeout: 15000 }).catch(() => {});
  await sleep(CONFIG.afterExecute);
}

// Step 9: load a complete .utlx via the REAL editor Load button. In 'browser' file-dialog mode the
// button opens a native <input type=file>, which Playwright drives through the filechooser event +
// setFiles. This goes through the IDE's loadFile(), so the file's own %utlx header is preserved
// (unlike pasting into Monaco, where the IDE re-applies its managed header and the file's is lost).
async function loadTransformViaButton(page, filePath) {
  const [chooser] = await Promise.all([
    page.waitForEvent('filechooser', { timeout: 15000 }),
    page.locator(SEL.editorLoadBtn).first().click(),
  ]);
  await chooser.setFiles(filePath);
}

// Slow, audience-friendly scroll. Prefer driving the element's own scrollTop in small beats — that's
// reliable for DOM-scrollable containers (the FB `.function-tree`, the result <pre>). Only fall back
// to mouse-wheel for things that aren't scrollTop-scrollable (e.g. Monaco's virtualized viewport),
// where the cursor must be over the surface.
async function slowScroll(page, selector, { total = 2000, step = 110, pause = 380 } = {}) {
  const el = page.locator(selector).first();
  if (!(await el.count().catch(() => 0))) return;
  const max = await el.evaluate(n => n.scrollHeight - n.clientHeight).catch(() => 0);
  if (max > 10) {
    const steps = Math.max(1, Math.ceil(total / step));
    for (let i = 1; i <= steps; i++) {
      await el.evaluate((n, top) => { n.scrollTop = top; }, Math.round((max * i) / steps)).catch(() => {});
      await sleep(pause);
    }
    return;
  }
  await el.hover().catch(() => {});
  for (let scrolled = 0; scrolled < total; scrolled += step) {
    await page.mouse.wheel(0, step);
    await sleep(pause);
  }
}

// ── Function Builder helpers (selectors verified against the live FB dialog) ─────────────────
// FB = the modal dialog. Tabs: button.tab-button (Available Inputs | Operators | Standard Library).
// Operators categories: .operator-category > .category-header (items: .operator-item/.operator-name).
// Stdlib categories:     .category          > .category-header (items: .function-item-compact/.function-name).
const FB = '.utlx-function-builder-dialog';
async function openFunctionBuilder(page) {
  await page.locator('[data-testid="utlx-editor-function-builder"]').first().click();
  await page.locator(FB).first().waitFor({ state: 'visible', timeout: 15000 });
  await sleep(CONFIG.beat);
}
async function fbTab(page, name) {
  await page.locator(`${FB} button.tab-button`, { hasText: name }).first().click();
  await sleep(CONFIG.beat);
}
// Toggle a category open/closed by clicking its header. scope = '.operator-category' or '.category'.
// Scroll it into view first and bound the click — a category below the fold must not hang (30s) or
// abort the whole pass.
async function fbToggleCategory(page, scope, name) {
  const cat = page.locator(`${FB} ${scope}`, { hasText: name }).first();
  await cat.scrollIntoViewIfNeeded().catch(() => {});
  await cat.locator('.category-header').first().click({ timeout: 8000 }).catch(() => {});
}
async function closeFunctionBuilder(page) {
  await page.locator(`${FB} button.close-btn-footer`).first().click().catch(() => {});
  await page.locator(FB).first().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
}

async function runOnce(page, inputJson) {
  // 1. Load the JSON input via the REAL Load button (filechooser → the actual file). This runs the
  //    IDE's input parse pipeline — format detection + UDM build + the ✓ Format / ✓ UDM indicators —
  //    so the Function Builder's $input tree has real CONTENTS. (A plain textarea fill drops text in
  //    the Instance tab but DOESN'T parse → $input shows but is empty.) Name Keep ⇒ slot stays
  //    "input" (so $input resolves). Browser file-dialog mode ⇒ native picker, drivable via filechooser.
  narrate('Step 1 — Load the JSON order via the input Load button (parses → UDM)');
  try {
    const [chooser] = await Promise.all([
      page.waitForEvent('filechooser', { timeout: 15000 }),
      page.locator(CONFIG.sel.inputLoadBtn).first().click(),
    ]);
    await chooser.setFiles(CONFIG.inputFile);
  } catch (e) {
    // Fallback (less faithful — no parse pipeline): fill the textarea directly.
    console.error('  step 1: Load button failed, falling back to textarea fill:', e.message);
    const ta = page.locator(CONFIG.sel.inputTextarea).first();
    await ta.waitFor({ state: 'visible', timeout: 15000 });
    await ta.click();
    await page.keyboard.press(`${MOD}+A`);
    await page.keyboard.press('Delete');
    await ta.fill(inputJson);
    await ta.blur();
  }
  await sleep(CONFIG.beat);

  // 2. Output format defaults to json.
  narrate('Step 2 — output is JSON by default');
  await setOutputFormat(page, 'json');
  await sleep(CONFIG.beat);

  // 3. Write the transform — BODY ONLY (the header is IDE-managed; never overwrite it).
  narrate('Step 3 — write a small UTL-X transform (body only; header kept)');
  await setTransformBody(page, CONFIG.transform);
  await sleep(CONFIG.beat);

  // 4. Execute → JSON result.
  narrate('Step 4 — Execute → JSON output');
  await execute(page);

  // 5/6. Switch output to XML → Execute.
  narrate('Step 5/6 — same transform, output XML');
  await setOutputFormat(page, 'xml');
  await sleep(CONFIG.beat);
  await execute(page);

  // 7/8. Switch output to YAML → Execute.
  narrate('Step 7/8 — same transform, output YAML');
  await setOutputFormat(page, 'yaml');
  await sleep(CONFIG.beat);
  await execute(page);

  narrate('One transform, three formats — now scale up.');

  // 9. Load the matching real-world transform via the REAL Load button (preserves the file header).
  narrate('Step 9 — load the enterprise order → fulfillment-ticket transform (Load button)');
  await loadTransformViaButton(page, CONFIG.exampleUtlxFile);
  await sleep(CONFIG.beat);

  // 10. Slowly scroll the editor to show the transform's real-world complexity.
  narrate('Step 10 — scroll the editor to show the complexity');
  await slowScroll(page, SEL.monaco, { total: 2200, step: 110, pause: 380 });
  await sleep(CONFIG.beat);

  // 11. Execute → the full fulfillment ticket (JSON).
  narrate('Step 11 — Execute the enterprise transform');
  await setOutputFormat(page, 'json');
  await sleep(CONFIG.beat);
  await execute(page);

  // 12. Slowly scroll the result to reveal the full ticket.
  narrate('Step 12 — scroll the result to show the full fulfillment ticket');
  await slowScroll(page, SEL.outputResult, { total: 2200, step: 110, pause: 380 });

  // 13. Open the Function Builder — defaults to the "Available Inputs" tab.
  narrate('Step 13 — open the Function Builder (Available Inputs)');
  await openFunctionBuilder(page);

  // 14–20. Drill into the $input field tree. Structure (field-tree.tsx):
  //   $input row : .input-node > .input-header  (chevron codicon-chevron-right/down)
  //   field row  : .field-node > .field-header   (expand chevron = [data-testid=utlx-field-toggle],
  //                .field-name = the label, insert = .insert-btn). A single object input auto-expands.
  narrate('Step 14 — the $input tree (Available Inputs)');
  console.log('   $input nodes present:', await page.locator(`${FB} .input-node`).count().catch(() => 0));

  // 15. Ensure $input is expanded (single input auto-expands; click the header only if collapsed).
  narrate('Step 15 — expand $input');
  if (await page.locator(`${FB} .input-node .input-header .codicon-chevron-right`).count().catch(() => 0)) {
    await page.locator(`${FB} .input-node .input-header`).first().click().catch(() => {});
  }
  await sleep(2000);

  // A field row addressed by its EXACT name, via its direct header child (avoids ancestor matches).
  const fieldRow = (name) => page.locator(`${FB} .field-node`).filter({
    has: page.locator('> .field-header > .field-name', { hasText: new RegExp(`^${name}$`) })
  }).first();
  const expandField = async (name) => {
    const r = fieldRow(name);
    await r.scrollIntoViewIfNeeded().catch(() => {});
    await r.locator('> .field-header [data-testid="utlx-field-toggle"]').first().click().catch(() => {});
  };

  // 16–18. Expand customer → primaryContact → address (the data uses lowercase primaryContact).
  for (const f of ['customer', 'primaryContact', 'address']) {
    narrate(`Step — expand ${f}`);
    await expandField(f);
    await sleep(2000);
  }

  // 19. Select postalCode (clicking the row shows its "Available Data").
  narrate('Step 19 — select postalCode (show available data)');
  {
    const r = fieldRow('postalCode');
    await r.scrollIntoViewIfNeeded().catch(() => {});
    await r.locator('> .field-header').first().click().catch(() => {});
    await sleep(2000);
  }

  // 20. Click the insert icon on the postalCode line.
  narrate('Step 20 — insert postalCode');
  await fieldRow('postalCode').locator('> .field-header > .insert-btn').first().click().catch(() => {});
  await sleep(4000);

  // 15. Operators tab.
  narrate('Step 21 — Operators tab');
  await fbTab(page, 'Operators');

  // 16–21. Open each operator category for 5s, then close it.
  for (const cat of ['Arithmetic', 'Comparison', 'Logical']) {
    narrate(`Step — ${cat}: open (5s) then close`);
    await fbToggleCategory(page, '.operator-category', cat); await sleep(5000);
    await fbToggleCategory(page, '.operator-category', cat); await sleep(CONFIG.beat);
  }

  // 22. Special: open, then click each operator (3s between, 3s after the last).
  narrate('Step 28 — Special: open and click each operator');
  await fbToggleCategory(page, '.operator-category', 'Special'); await sleep(CONFIG.beat);
  {
    const items = page.locator(`${FB} .operator-category`, { hasText: 'Special' }).locator('.operator-item');
    const c = await items.count().catch(() => 0);
    for (let i = 0; i < c; i++) { await items.nth(i).click().catch(() => {}); await sleep(3000); }
  }

  // 23. Standard Library tab; scroll down, then bring Geospatial back into view.
  narrate('Step 29 — Standard Library: scroll down, back up to Geospatial');
  await fbTab(page, 'Standard Library');
  // The scrollable list inside the Standard Library tab is `.function-tree` (not the dialog root).
  await slowScroll(page, `${FB} .function-tree`, { total: 1800, step: 110, pause: 320 });
  await page.locator(`${FB} .category`, { hasText: 'Geospatial' }).first().scrollIntoViewIfNeeded().catch(() => {});
  await sleep(CONFIG.beat);

  // 24. Open Geospatial.
  narrate('Step 30 — open Geospatial');
  await fbToggleCategory(page, '.category', 'Geospatial'); await sleep(2000);

  // 25. Click destinationPoint.
  narrate('Step 31 — destinationPoint');
  await page.locator(`${FB} .function-item-compact`, { hasText: 'destinationPoint' }).first().click().catch(() => {});
  await sleep(3000);

  // 26. Close the Function Builder.
  narrate('Step 32 — close the Function Builder');
  await closeFunctionBuilder(page);

  // 27. Flip Name keep → inherit (status-bar switch) so the next loaded file names its own input.
  narrate('Step 33 — switch Name keep → inherit');
  await page.locator(SEL.sbNameOnLoadMode).first().click().catch(() => {});
  await sleep(CONFIG.beat);

  // 28. Add a second input and load the employee-roster CSV (real Load button → filechooser).
  narrate('Step 34 — add a second input: load 01-employee-roster.csv');
  await page.locator(SEL.inputAddBtn).first().click().catch(() => {});
  await sleep(CONFIG.beat);
  try {
    const [chooser] = await Promise.all([
      page.waitForEvent('filechooser', { timeout: 15000 }),
      page.locator(SEL.inputLoadBtn).last().click(),   // last() = the just-added input's Load
    ]);
    await chooser.setFiles(CONFIG.rosterCsvFile);
  } catch (e) { console.error('  step 28 load failed:', e.message); }
  await sleep(3000);

  // 29. Clear the transform.
  narrate('Step 35 — clear the UTL-X');
  await page.locator(SEL.editorClearBtn).first().click().catch(() => {});
  await sleep(CONFIG.beat);

  // 30. Write a mapping that reads BOTH inputs.
  narrate('Step 36 — a mapping that uses $input and $employee-roster');
  await setTransformBody(page, CONFIG.dualInputTransform);
  await sleep(CONFIG.beat);

  // 31. Execute the multi-input mapping.
  narrate('Step 37 — Execute the multi-input mapping');
  await setOutputFormat(page, 'json');
  await execute(page);

  narrate('Simple mapping → real-world transform → Function Builder → multi-input. Done.');
}

(async () => {
  const inputJson = fs.readFileSync(CONFIG.inputFile, 'utf-8');
  fs.mkdirSync(CONFIG.recordVideoDir, { recursive: true });

  // Fill the screen: explicit window-size/position (Playwright + --kiosk alone isn't reliably
  // wide), plus fullscreen for a clean, chrome-less talk view.
  const { w, h } = screenSize();
  const browser = await chromium.launch({
    headless: false,
    slowMo: CONFIG.slowMo,
    args: [
      '--start-fullscreen',
      '--disable-infobars',
      '--window-position=0,0',
      `--window-size=${w},${h}`,
    ],
  });
  const context = await browser.newContext({
    viewport: null,                                                        // page uses the full window
    recordVideo: { dir: CONFIG.recordVideoDir, size: { width: w, height: h } },  // wide video (else ~800px default)
  });

  // Precondition (semaphores): Load Theia + Name Keep — set before any page script runs.
  await context.addInitScript(({ keys }) => {
    // Step 9 uses the REAL editor Load button. 'browser' mode makes it open a native
    // <input type=file>, which Playwright drives via the filechooser event (the Theia dialog
    // is a widget Playwright can't reliably drive). Either mode loads via loadFile() so the
    // file's %utlx header is preserved.
    localStorage.setItem(keys.fileDialogMode, 'browser'); // scenario1: Load via native file picker
    localStorage.setItem(keys.nameOnLoadMode, 'keep');    // scenario1: Name Keep
  }, { keys: PRECONDITION });

  const page = await context.newPage();
  await page.goto(CONFIG.baseURL, { waitUntil: 'domcontentloaded' });
  // Let Theia's shell + the UTL-X panels mount.
  await page.locator(CONFIG.sel.executeBtn).first().waitFor({ state: 'visible', timeout: 60000 });

  let n = 0;
  do {
    n++;
    narrate(`══ Demo pass #${n} ══`);
    try {
      await runOnce(page, inputJson);
    } catch (e) {
      console.error('  pass failed, resetting:', e.message);
      await page.reload({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await page.locator(CONFIG.sel.executeBtn).first().waitFor({ state: 'visible', timeout: 60000 }).catch(() => {});
    }
    if (CONFIG.loop) await sleep(CONFIG.betweenLoops);
  } while (CONFIG.loop);

  if (CONFIG.stayOpen) {
    console.log('\n✓ demo complete — browser left OPEN (no repeat). Press Ctrl-C to close it and flush the video.');
    await new Promise(resolve => process.once('SIGINT', resolve));
    console.log('\nClosing…');
  }
  await context.close();   // flushes the recording to recordings/
  await browser.close();
  console.log(`\n✓ video saved under ${CONFIG.recordVideoDir}`);
})();
