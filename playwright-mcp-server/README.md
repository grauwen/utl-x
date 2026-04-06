# Playwright MCP Server

**Real-time browser debugging for UTLX via Model Context Protocol**

This MCP server connects to the running UTLX Theia extension browser instance and provides Claude/LLMs with access to console logs, JavaScript errors, network activity, and screenshots for immediate debugging assistance.

## Features

- 📋 **Console Logs** - Access browser console output with filtering
- ❌ **Error Tracking** - JavaScript errors with stack traces
- 🌐 **Network Monitoring** - HTTP requests/responses and failures
- 📸 **Screenshots** - Visual debugging on demand
- 📄 **Page Info** - Current URL and title
- 🎬 **Trace Capture** - Full Playwright traces for complex debugging

## Quick Start

### 1. Installation

```bash
cd playwright-mcp-server
npm install
npm run build
```

### 2. Start UTLX Theia with Remote Debugging

The MCP server connects to your browser via Chrome DevTools Protocol (CDP). You need to launch the browser with remote debugging enabled on port 9222.

#### Option A: Electron App (Theia Desktop)

Add to your Theia launch configuration:

```bash
# In theia-extension/browser-app/
electron . --remote-debugging-port=9222
```

#### Option B: Chrome/Chromium Browser

```bash
# Launch Chrome with remote debugging
google-chrome --remote-debugging-port=9222 http://localhost:3000

# Or Chromium
chromium --remote-debugging-port=9222 http://localhost:3000
```

### 3. Configure Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "utlx-playwright": {
      "command": "node",
      "args": ["/absolute/path/to/utl-x/playwright-mcp-server/dist/index.js"],
      "env": {
        "CDP_URL": "http://localhost:9222",
        "LOG_BUFFER_SIZE": "500",
        "SCREENSHOT_PATH": "./screenshots"
      }
    }
  }
}
```

### 4. Start the MCP Server

The server will automatically start when Claude Desktop launches. You can also test it manually:

```bash
npm start
```

## Usage

### Example Debugging Session

**You:** "I see brackets in the JSON output that shouldn't be there"

**Claude:** Let me check the console logs for you.

*Claude calls `get_console_logs` tool*

**Claude:** I found several errors in the browser console:

```
❌ ERROR - 2024-11-19T15:30:45.123Z
TypeError: Cannot read property 'toString' of undefined
    at formatters.ts:45:12
    at Array.map
📍 Location: http://localhost:3000/formatters.ts:45:12
```

It looks like the JSON serializer is trying to call `.toString()` on an undefined value. This is happening in the array formatting code...

### Available Tools

#### 1. `get_console_logs`

Retrieve browser console logs with filtering.

**Parameters:**
- `level` (optional): Filter by log level (`log`, `warn`, `error`, `info`, `debug`, `all`)
- `limit` (optional): Maximum number of logs (default: 50)
- `since` (optional): ISO 8601 timestamp to get logs since

**Example prompts:**
- "Check the console for errors"
- "Show me recent console warnings"
- "Get all console logs from the last 5 minutes"

#### 2. `get_errors`

Get JavaScript errors and exceptions only.

**Parameters:**
- `limit` (optional): Maximum number of errors (default: 20)
- `includeStackTrace` (optional): Include full stack traces (default: true)

**Example prompts:**
- "Show me all JavaScript errors"
- "Get the last error with stack trace"
- "Check for uncaught exceptions"

#### 3. `get_network_logs`

Retrieve network requests and responses.

**Parameters:**
- `filter` (optional): Filter type (`all`, `failed`, `xhr`, `fetch`)
- `limit` (optional): Maximum number of logs (default: 30)

**Example prompts:**
- "Show me failed network requests"
- "Get recent API calls"
- "Check XHR requests to the backend"

#### 4. `take_screenshot`

Capture a screenshot of the current page.

**Parameters:**
- `fullPage` (optional): Capture the full scrollable page (default: false)
- `path` (optional): File path to save screenshot

**Example prompts:**
- "Take a screenshot of the current page"
- "Show me what the UI looks like"
- "Capture the error dialog"

#### 5. `get_page_info`

Get current page URL and title.

**Example prompts:**
- "What page is currently loaded?"
- "Get the current URL"

#### 6. `capture_trace`

Start or stop Playwright trace capture.

**Parameters:**
- `action` (required): `start` or `stop`
- `outputPath` (optional/required): Path to save trace file (required when action=stop)

**Example prompts:**
- "Start trace capture"
- "Stop trace and save to debug-trace.zip"

View traces with: `npx playwright show-trace <file>.zip`

## Configuration

### Environment Variables

- `CDP_URL` - Chrome DevTools Protocol URL (default: `http://localhost:9222`)
- `LOG_BUFFER_SIZE` - Maximum number of logs to buffer (default: `500`)
- `SCREENSHOT_PATH` - Directory for screenshots (default: `./screenshots`)

### Example `.env` file

```env
CDP_URL=http://localhost:9222
LOG_BUFFER_SIZE=1000
SCREENSHOT_PATH=/tmp/utlx-screenshots
```

## Architecture

```
UTLX Theia Extension (Browser)
    ↓ Console, Errors, Network (Chrome DevTools Protocol)
Playwright Browser Context
    ↓ Event Listeners & Log Collection
PlaywrightClient (src/browser/playwright-client.ts)
    ↓ Buffered Logs & Tools
MCP Server (src/index.ts)
    ↓ MCP Protocol (stdio)
Claude Desktop / Claude Code
```

## Troubleshooting

### "Failed to connect to browser"

**Cause:** The browser is not running with remote debugging enabled, or the CDP port is different.

**Solution:**
1. Ensure the browser/Theia is launched with `--remote-debugging-port=9222`
2. Check if port 9222 is in use: `lsof -i:9222`
3. Try connecting to a different port by setting `CDP_URL` environment variable

### "No pages found in browser context"

**Cause:** The browser is running but no pages are open.

**Solution:**
1. Ensure UTLX Theia is fully loaded
2. Navigate to `http://localhost:3000` or your Theia URL
3. Refresh the page

### "Not connected to browser"

**Cause:** The MCP server lost connection to the browser.

**Solution:**
1. Restart the browser with remote debugging
2. Restart the MCP server
3. Check browser console for crashes

### MCP Server Not Listed in Claude

**Cause:** Configuration error in `claude_desktop_config.json`

**Solution:**
1. Check the JSON syntax is valid
2. Ensure the path to `dist/index.js` is absolute
3. Restart Claude Desktop
4. Check Claude Desktop logs

## Development

### Build

```bash
npm run build
```

### Watch Mode

```bash
npm run watch
```

### Clean

```bash
npm run clean
```

## How It Works

### 1. Browser Connection

The server uses Playwright's `connectOverCDP()` to attach to a running Chrome/Chromium instance via the Chrome DevTools Protocol on port 9222.

### 2. Event Collection

Once connected, the server sets up event listeners for:
- `console` - All console messages (log, warn, error, info, debug)
- `pageerror` - Uncaught JavaScript exceptions
- `request` / `response` - Network activity

### 3. Log Buffering

Events are buffered in-memory with a configurable size (default: 500 entries). Older entries are removed when the buffer is full.

### 4. MCP Tool Invocation

When Claude calls a tool (e.g., `get_console_logs`), the server:
1. Retrieves logs from the buffer
2. Filters based on parameters (level, time, limit)
3. Formats as markdown for readability
4. Returns via MCP protocol

## Real-World Use Cases

### Debugging Runtime Errors

**Scenario:** User sees unexpected behavior in the UI.

**Workflow:**
1. User reports: "The transformation output looks wrong"
2. Claude: "Let me check the console logs"
3. Claude calls `get_console_logs` and `get_errors`
4. Claude analyzes error: "I see a TypeError in the JSON serializer..."
5. Claude explains the issue and suggests fixes

### Diagnosing Network Issues

**Scenario:** API calls are failing.

**Workflow:**
1. User: "The backend isn't responding"
2. Claude: "Let me check network requests"
3. Claude calls `get_network_logs` with filter `failed`
4. Claude: "I see 5 failed requests to `/api/execute` with 500 status..."
5. Claude suggests checking backend logs

### Visual Debugging

**Scenario:** UI rendering issue.

**Workflow:**
1. User: "The layout is broken"
2. Claude: "Let me take a screenshot"
3. Claude calls `take_screenshot`
4. Claude analyzes UI state
5. Claude: "I see the panel is collapsed. Check the CSS for `.utlx-panel`..."

### Comprehensive Trace

**Scenario:** Complex multi-step bug.

**Workflow:**
1. Claude: "Let me capture a full trace"
2. Claude calls `capture_trace` with action=`start`
3. User reproduces the bug
4. Claude calls `capture_trace` with action=`stop`
5. Claude: "Trace saved to debug-trace.zip. View it with `npx playwright show-trace`"

## Limitations

- **Single Page:** Only monitors the first page/tab in the browser context
- **Buffer Size:** Older logs are discarded when buffer fills
- **No Interactivity:** Cannot click buttons or interact with the UI (read-only monitoring)
- **CDP Required:** Requires Chrome/Chromium with DevTools Protocol support

## Future Enhancements

Potential features for future versions:

- [ ] Multi-page monitoring
- [ ] Interactive browser automation (click, type, navigate)
- [ ] Performance metrics (Core Web Vitals)
- [ ] HAR (HTTP Archive) export
- [ ] WebSocket message monitoring
- [ ] Service Worker debugging
- [ ] Local storage/session storage inspection

## Contributing

Contributions are welcome! Please ensure:

1. TypeScript code compiles without errors
2. All tools follow the existing pattern
3. Error handling is comprehensive
4. Documentation is updated

## License

Apache License 2.0 - See LICENSE file for details

## Related Projects

- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) - Protocol specification
- [Playwright](https://playwright.dev/) - Browser automation library
- [UTLX](https://github.com/utlx) - Universal Transformation Language

---

**Need Help?** Check the troubleshooting section or open an issue on GitHub.
