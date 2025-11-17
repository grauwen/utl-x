import { Page, Locator } from '@playwright/test';

/**
 * Helper utilities for interacting with Theia UI in tests
 */

export class TheiaHelpers {
  constructor(private page: Page) {}

  /**
   * Wait for Theia to fully load
   */
  async waitForTheiaReady(timeout = 30000): Promise<void> {
    await this.page.waitForSelector('.theia-preload', { state: 'hidden', timeout });
    await this.page.waitForSelector('#theia-app-shell', { state: 'visible', timeout });
  }

  /**
   * Open a panel by its ID
   */
  async openPanel(panelId: string): Promise<void> {
    // Try to find panel in the UI
    const panel = this.page.locator(`#${panelId}`);

    if (await panel.isVisible()) {
      return; // Already open
    }

    // Otherwise, use View menu to open it
    await this.openViewMenu();
    // TODO: Navigate to the specific panel
  }

  /**
   * Open the View menu
   */
  async openViewMenu(): Promise<void> {
    const viewMenu = this.page.locator('.p-MenuBar-item:has-text("View")');
    await viewMenu.click();
  }

  /**
   * Find the Multi Input Panel
   */
  getMultiInputPanel(): Locator {
    return this.page.locator('.utlx-multi-input-panel');
  }

  /**
   * Find the UTLX Editor
   */
  getUTLXEditor(): Locator {
    return this.page.locator('.utlx-editor-widget');
  }

  /**
   * Find the Output Panel
   */
  getOutputPanel(): Locator {
    return this.page.locator('.utlx-output-panel');
  }

  /**
   * Execute a keyboard shortcut
   */
  async executeShortcut(modifiers: string[], key: string): Promise<void> {
    const isMac = process.platform === 'darwin';
    const modifier = isMac ? 'Meta' : 'Control';

    const keys = [...modifiers.map(m => m === 'Mod' ? modifier : m), key];
    await this.page.keyboard.press(keys.join('+'));
  }

  /**
   * Wait for a specific time (use sparingly, prefer waitForSelector)
   */
  async wait(ms: number): Promise<void> {
    await this.page.waitForTimeout(ms);
  }
}

/**
 * Helper utilities for interacting with the Multi Input Panel
 */
export class InputPanelHelpers {
  constructor(private page: Page, private theia: TheiaHelpers) {}

  /**
   * Get the input panel element
   */
  getPanel(): Locator {
    return this.page.locator('.utlx-multi-input-panel');
  }

  /**
   * Get all input tabs
   */
  getInputTabs(): Locator {
    return this.page.locator('.utlx-input-tab');
  }

  /**
   * Get a specific input tab by name
   */
  getInputTab(name: string): Locator {
    return this.page.locator(`.utlx-input-tab:has-text("${name}")`);
  }

  /**
   * Get the active input tab
   */
  getActiveInputTab(): Locator {
    return this.page.locator('.utlx-input-tab.active');
  }

  /**
   * Click the "Add Input" button
   */
  async addInput(): Promise<void> {
    const addButton = this.page.locator('.utlx-add-input-button');
    await addButton.click();
    await this.theia.wait(100); // Wait for UI to update
  }

  /**
   * Set the name of the active input
   */
  async setInputName(name: string): Promise<void> {
    const nameInput = this.page.locator('.utlx-input-name-editor');
    await nameInput.click();
    await nameInput.fill('');
    await nameInput.type(name);
    await nameInput.press('Enter');
    await this.theia.wait(100);
  }

  /**
   * Set the format of the active input
   */
  async setInputFormat(format: string): Promise<void> {
    const formatSelect = this.page.locator('.utlx-format-selector');
    await formatSelect.selectOption(format);
    await this.theia.wait(100);
  }

  /**
   * Set the content of the active input
   */
  async setInputContent(content: string): Promise<void> {
    // Find Monaco editor for the input
    const editor = this.page.locator('.utlx-input-editor .monaco-editor');
    await editor.click();

    // Use Monaco's setValue command
    await this.page.evaluate((text) => {
      const editors = (window as any).monaco?.editor?.getEditors?.() || [];
      if (editors.length > 0) {
        const editor = editors[0];
        editor.setValue(text);
      }
    }, content);

    await this.theia.wait(200); // Wait for validation
  }

  /**
   * Load a file into the active input
   */
  async loadFile(filePath: string): Promise<void> {
    const loadButton = this.page.locator('.utlx-load-file-button');
    await loadButton.click();

    // TODO: Handle file picker dialog
    // For now, we'll use setInputContent as a workaround
  }

  /**
   * Get the format detection indicator
   */
  getFormatIndicator(): Locator {
    return this.page.locator('.utlx-format-indicator');
  }

  /**
   * Get the UDM validation indicator
   */
  getUDMIndicator(): Locator {
    return this.page.locator('.utlx-udm-indicator');
  }

  /**
   * Wait for UDM validation to complete
   */
  async waitForValidation(timeout = 5000): Promise<void> {
    // Wait for the validation spinner to disappear
    await this.page.waitForSelector('.utlx-udm-indicator:not(:has-text("⟳"))', { timeout });
  }

  /**
   * Check if format detection shows a match
   */
  async isFormatMatch(): Promise<boolean> {
    const indicator = this.getFormatIndicator();
    const text = await indicator.textContent();
    return text?.includes('✓') || false;
  }

  /**
   * Check if format detection shows a mismatch
   */
  async isFormatMismatch(): Promise<boolean> {
    const indicator = this.getFormatIndicator();
    const text = await indicator.textContent();
    return text?.includes('⚠') || false;
  }

  /**
   * Check if format detection is unknown
   */
  async isFormatUnknown(): Promise<boolean> {
    const indicator = this.getFormatIndicator();
    const text = await indicator.textContent();
    return text?.includes('?') || false;
  }

  /**
   * Check if UDM validation succeeded
   */
  async isUDMValid(): Promise<boolean> {
    const indicator = this.getUDMIndicator();
    const text = await indicator.textContent();
    return text?.includes('✓') || false;
  }

  /**
   * Check if UDM validation failed
   */
  async isUDMInvalid(): Promise<boolean> {
    const indicator = this.getUDMIndicator();
    const text = await indicator.textContent();
    return text?.includes('✗') || false;
  }

  /**
   * Get the detected format
   */
  async getDetectedFormat(): Promise<string | null> {
    const indicator = this.getFormatIndicator();
    const title = await indicator.getAttribute('title');

    if (title) {
      // Extract format from title like "Format detection: XML (matches declared format)"
      const match = title.match(/Format detection: (\w+)/i);
      return match ? match[1].toLowerCase() : null;
    }

    return null;
  }
}

/**
 * Helper utilities for interacting with the UTLX Editor
 */
export class EditorHelpers {
  constructor(private page: Page, private theia: TheiaHelpers) {}

  /**
   * Get the editor element
   */
  getEditor(): Locator {
    return this.page.locator('.utlx-editor-widget');
  }

  /**
   * Set the editor content
   */
  async setContent(content: string): Promise<void> {
    const editor = this.getEditor().locator('.monaco-editor');
    await editor.click();

    await this.page.evaluate((text) => {
      const editors = (window as any).monaco?.editor?.getEditors?.() || [];
      for (const editor of editors) {
        const model = editor.getModel();
        if (model && model.getLanguageId?.() === 'utlx') {
          editor.setValue(text);
          break;
        }
      }
    }, content);

    await this.theia.wait(100);
  }

  /**
   * Get the editor content
   */
  async getContent(): Promise<string> {
    return await this.page.evaluate(() => {
      const editors = (window as any).monaco?.editor?.getEditors?.() || [];
      for (const editor of editors) {
        const model = editor.getModel();
        if (model && model.getLanguageId?.() === 'utlx') {
          return editor.getValue();
        }
      }
      return '';
    });
  }

  /**
   * Execute the transformation
   */
  async execute(): Promise<void> {
    const executeButton = this.page.locator('.utlx-execute-button');
    await executeButton.click();
    await this.theia.wait(500); // Wait for execution
  }

  /**
   * Check if there are syntax errors
   */
  async hasSyntaxErrors(): Promise<boolean> {
    const errorMarkers = this.page.locator('.monaco-editor .squiggly-error');
    return (await errorMarkers.count()) > 0;
  }
}

/**
 * Helper utilities for interacting with the Output Panel
 */
export class OutputPanelHelpers {
  constructor(private page: Page) {}

  /**
   * Get the output panel element
   */
  getPanel(): Locator {
    return this.page.locator('.utlx-output-panel');
  }

  /**
   * Get the output content
   */
  async getContent(): Promise<string> {
    const content = this.page.locator('.utlx-output-content');
    return await content.textContent() || '';
  }

  /**
   * Get the output format
   */
  async getFormat(): Promise<string> {
    const formatIndicator = this.page.locator('.utlx-output-format');
    return await formatIndicator.textContent() || '';
  }

  /**
   * Check if execution succeeded
   */
  async isSuccess(): Promise<boolean> {
    const successIndicator = this.page.locator('.utlx-execution-success');
    return await successIndicator.isVisible();
  }

  /**
   * Check if execution failed
   */
  async isError(): Promise<boolean> {
    const errorIndicator = this.page.locator('.utlx-execution-error');
    return await errorIndicator.isVisible();
  }

  /**
   * Get error message if execution failed
   */
  async getErrorMessage(): Promise<string | null> {
    const errorMsg = this.page.locator('.utlx-error-message');
    if (await errorMsg.isVisible()) {
      return await errorMsg.textContent();
    }
    return null;
  }
}

/**
 * Main helper factory - creates all helpers
 */
export function createHelpers(page: Page) {
  const theia = new TheiaHelpers(page);
  return {
    theia,
    inputPanel: new InputPanelHelpers(page, theia),
    editor: new EditorHelpers(page, theia),
    output: new OutputPanelHelpers(page),
  };
}
