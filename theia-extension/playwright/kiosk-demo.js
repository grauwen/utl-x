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

  loop: !process.env.DEMO_ONCE,   // `npm run demo:once` = one pass then exit (good for recording)
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

// Slow, audience-friendly scroll of a scrollable element (Monaco surface or the result <pre>):
// hover it so the wheel targets it, then wheel down in small beats.
async function slowScroll(page, selector, { total = 2000, step = 110, pause = 380 } = {}) {
  const el = page.locator(selector).first();
  await el.hover().catch(() => {});
  for (let scrolled = 0; scrolled < total; scrolled += step) {
    await page.mouse.wheel(0, step);
    await sleep(pause);
  }
}

async function runOnce(page, inputJson) {
  // 1. Load the JSON input. Name Keep ⇒ the slot stays "input" (so $input resolves).
  //    (trial 1) We fill the input textarea directly — robust + same visible result as a
  //    file Load. To show the actual "Load from file" gesture, replace this with a click on
  //    button[title="Load from file"] and drive the Theia open dialog.
  narrate('Step 1 — put a JSON order in the Input panel');
  const ta = page.locator(CONFIG.sel.inputTextarea).first();
  await ta.waitFor({ state: 'visible', timeout: 15000 });
  await ta.click();
  await page.keyboard.press(`${MOD}+A`);
  await page.keyboard.press('Delete');
  await ta.fill(inputJson);
  await ta.blur();
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

  narrate('Simple mapping → real-world transform. Done.');
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

  await context.close();   // flushes the recording to recordings/
  await browser.close();
  console.log(`\n✓ video saved under ${CONFIG.recordVideoDir}`);
})();
