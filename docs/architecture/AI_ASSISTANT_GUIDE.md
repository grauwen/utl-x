# UTLX AI Assistant Guide

Complete guide for using AI-powered UTLX code generation in both Theia IDE and Claude Desktop.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Setup for Theia AI Assistant](#setup-for-theia-ai-assistant)
4. [Setup for Claude Desktop](#setup-for-claude-desktop)
5. [Usage Examples](#usage-examples)
6. [Troubleshooting](#troubleshooting)

---

## Overview

The UTLX MCP (Model Context Protocol) server provides two distinct AI-powered workflows:

### 1. **Theia AI Assistant** (Integrated IDE Experience)
- Click "🤖 AI Assist" button in Theia toolbar
- Describe transformation in natural language
- AI generates complete UTLX code
- Code appears directly in editor
- **Requires**: LLM provider (Claude API or local Ollama)

### 2. **Claude Desktop** (Interactive Chat Experience)
- Chat with Claude about UTLX in desktop app
- Validate, execute, and debug transformations
- Learn UTLX syntax interactively
- Search examples and documentation
- **Requires**: Claude Desktop app (Claude is the LLM)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    THEIA AI ASSISTANT                           │
│                                                                 │
│  User clicks "AI Assist" → Enters prompt                       │
│                              ↓                                  │
│  Theia collects:                                               │
│    - Input data + formats + UDM                                │
│    - Output format                                             │
│    - User's natural language request                           │
│                              ↓                                  │
│              [Theia Backend Service]                           │
│                              ↓                                  │
│              [MCP Client - HTTP]                               │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ↓ HTTP (port 3000)
┌─────────────────────────────┴───────────────────────────────────┐
│                    MCP SERVER (Node.js)                         │
│                                                                 │
│  Tool: generate_utlx_from_prompt                               │
│         ↓                                                       │
│  1. Build context with:                                        │
│     - UTLX syntax guide                                        │
│     - Input original data                                      │
│     - Input UDM structure                                      │
│     - User prompt                                              │
│         ↓                                                       │
│  2. Call LLM:                                                  │
│     ┌─────────────────┬────────────────┐                      │
│     ↓                 ↓                ↓                       │
│  Claude API      Ollama Local    [Other LLMs]                 │
│  (cloud)         (localhost)                                   │
│     ↓                 ↓                                        │
│  Generated UTLX code                                           │
│         ↓                                                       │
│  3. Validate with UTLX Daemon                                  │
│         ↓                                                       │
│  4. If invalid: Retry with error feedback                      │
│         ↓                                                       │
│  Return validated code                                         │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────┴───────────────────────────────────┐
│                    CLAUDE DESKTOP                               │
│                                                                 │
│  User chats with Claude                                         │
│                    ↓                                            │
│  Claude calls MCP tools:                                        │
│    - validate_utlx                                             │
│    - execute_transformation                                     │
│    - get_operators                                             │
│    - get_functions                                             │
│    - get_examples                                              │
│    - etc...                                                    │
│                    ↓                                            │
│  Claude provides contextual help                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Setup for Theia AI Assistant

The Theia AI Assistant generates UTLX code directly in your IDE. It requires an LLM provider.

### Prerequisites

1. **UTLX Daemon running** (port 7779)
2. **MCP Server** (port 3000)
3. **LLM Provider**: Choose ONE of the following options

---

### Option A: Using Claude API (Cloud-based)

**Best for**: Production use, highest quality code generation

#### Step 1: Get Anthropic API Key
```bash
# Sign up at https://console.anthropic.com/
# Create an API key
# Copy the key (starts with sk-ant-...)
```

#### Step 2: Configure Environment
```bash
# Set LLM provider to Claude
export UTLX_LLM_PROVIDER=claude

# Set your API key
export ANTHROPIC_API_KEY=sk-ant-api03-...

# Optional: Choose model (default: claude-3-5-sonnet-20241022)
export UTLX_LLM_MODEL=claude-3-5-sonnet-20241022

# Optional: Set max tokens (default: 4096)
export UTLX_LLM_MAX_TOKENS=4096
```

#### Step 3: Start MCP Server
```bash
cd /path/to/utl-x/mcp-server

# Set HTTP transport for Theia
export UTLX_MCP_TRANSPORT=http
export UTLX_MCP_PORT=3000

# Set daemon URL
export UTLX_DAEMON_URL=http://localhost:7779

# Start server
npm start
```

#### Step 4: Verify Configuration
```bash
# Check MCP server logs for:
[INFO] LLM provider initialized successfully { provider: 'Claude' }
[INFO] HTTP server listening on port 3000
```

**Cost**: ~$0.003 per request (varies by prompt/code size)

---

### Option B: Using Ollama (Local, Free)

**Best for**: Development, offline work, privacy, no API costs

#### Step 1: Install Ollama
```bash
# Download from https://ollama.ai
# Or on macOS:
brew install ollama

# On Linux:
curl -fsSL https://ollama.ai/install.sh | sh
```

#### Step 2: Download a Code Model
```bash
# Start Ollama service
ollama serve

# In another terminal, download a model:

# Option 1: CodeLlama (recommended, 7B parameters)
ollama pull codellama

# Option 2: CodeLlama 13B (better quality, slower)
ollama pull codellama:13b

# Option 3: DeepSeek Coder (alternative)
ollama pull deepseek-coder

# Option 4: Qwen Coder (fast, good quality)
ollama pull qwen:7b-coder

# Test the model
ollama run codellama "Write a hello world function"
```

#### Step 3: Configure Environment
```bash
# Set LLM provider to Ollama
export UTLX_LLM_PROVIDER=ollama

# Set Ollama endpoint (default: http://localhost:11434)
export OLLAMA_ENDPOINT=http://localhost:11434

# Set model name (must match pulled model)
export OLLAMA_MODEL=codellama

# Optional: Set max tokens
export UTLX_LLM_MAX_TOKENS=4096
```

#### Step 4: Start MCP Server
```bash
cd /path/to/utl-x/mcp-server

# Set HTTP transport for Theia
export UTLX_MCP_TRANSPORT=http
export UTLX_MCP_PORT=3000

# Set daemon URL
export UTLX_DAEMON_URL=http://localhost:7779

# Start server
npm start
```

#### Step 5: Verify Configuration
```bash
# Check MCP server logs for:
[INFO] LLM provider initialized successfully { provider: 'Ollama' }
[INFO] HTTP server listening on port 3000

# Check Ollama is responding:
curl http://localhost:11434/api/tags
```

**Cost**: Free, runs locally

**Performance Tips**:
- 7B models: ~2-5 seconds per generation
- 13B models: ~5-10 seconds per generation
- Requires: 8GB+ RAM for 7B, 16GB+ for 13B

---

### Configuration File Alternative

Instead of environment variables, create `llm-config.json`:

**For Claude:**
```json
{
  "type": "claude",
  "claude": {
    "apiKey": "sk-ant-api03-...",
    "model": "claude-3-5-sonnet-20241022",
    "maxTokens": 4096
  }
}
```

**For Ollama:**
```json
{
  "type": "ollama",
  "ollama": {
    "endpoint": "http://localhost:11434",
    "model": "codellama",
    "maxTokens": 4096
  }
}
```

Then set the config path:
```bash
export UTLX_LLM_CONFIG=/path/to/llm-config.json
```

---

### Complete Startup Script (Theia)

Create `start-theia-ai.sh`:

```bash
#!/bin/bash

# Step 1: Start UTLX Daemon
echo "Starting UTLX Daemon..."
cd /path/to/utl-x/modules/daemon/build/libs
java -jar utlxd-1.0.0-SNAPSHOT.jar start --lsp --api &
DAEMON_PID=$!

# Wait for daemon to be ready
sleep 3

# Step 2: Start MCP Server with LLM

# OPTION A: Claude API
export UTLX_LLM_PROVIDER=claude
export ANTHROPIC_API_KEY=sk-ant-your-key-here

# OPTION B: Ollama (uncomment to use)
# export UTLX_LLM_PROVIDER=ollama
# export OLLAMA_ENDPOINT=http://localhost:11434
# export OLLAMA_MODEL=codellama

# Common settings
export UTLX_MCP_TRANSPORT=http
export UTLX_MCP_PORT=3000
export UTLX_DAEMON_URL=http://localhost:7779

echo "Starting MCP Server with AI generation..."
cd /path/to/utl-x/mcp-server
npm start &
MCP_PID=$!

# Wait for MCP server to be ready
sleep 2

# Step 3: Start Theia
echo "Starting Theia..."
cd /path/to/utl-x/theia-extension
yarn start &
THEIA_PID=$!

echo "==================================="
echo "UTLX AI Assistant Ready!"
echo "==================================="
echo "UTLX Daemon:  http://localhost:7779"
echo "MCP Server:   http://localhost:3000"
echo "Theia:        http://localhost:3001"
echo ""
echo "Press Ctrl+C to stop all services"

# Wait for interrupt
trap "kill $DAEMON_PID $MCP_PID $THEIA_PID" EXIT
wait
```

Make executable:
```bash
chmod +x start-theia-ai.sh
./start-theia-ai.sh
```

---

## Setup for Claude Desktop

Claude Desktop provides an interactive chat interface for UTLX development.

### Prerequisites

1. **UTLX Daemon running** (port 7779)
2. **MCP Server** (stdio mode)
3. **Claude Desktop app**

**Note**: No LLM configuration needed - Claude Desktop **is** the LLM!

---

### Step 1: Install Claude Desktop

Download from: https://claude.ai/download

Available for:
- macOS (Apple Silicon & Intel)
- Windows
- Linux

---

### Step 2: Build MCP Server

```bash
cd /path/to/utl-x/mcp-server

# Install dependencies
npm install

# Build TypeScript
npm run build

# Verify build
ls -la dist/index.js
```

---

### Step 3: Configure Claude Desktop

Edit Claude Desktop configuration file:

**macOS/Linux:**
```bash
nano ~/Library/Application\ Support/Claude/claude_desktop_config.json
```

**Windows:**
```
%APPDATA%\Claude\claude_desktop_config.json
```

**Configuration:**
```json
{
  "mcpServers": {
    "utlx": {
      "command": "node",
      "args": [
        "/absolute/path/to/utl-x/mcp-server/dist/index.js"
      ],
      "env": {
        "UTLX_DAEMON_URL": "http://localhost:7779",
        "UTLX_MCP_TRANSPORT": "stdio",
        "UTLX_LOG_LEVEL": "info"
      }
    }
  }
}
```

**Important**: Use absolute paths, not relative or `~`

---

### Step 4: Start Services

```bash
# Terminal 1: Start UTLX Daemon
cd /path/to/utl-x/modules/daemon/build/libs
java -jar utlxd-1.0.0-SNAPSHOT.jar start --lsp --api

# Terminal 2: Start Claude Desktop
# Launch from Applications or command line
open -a "Claude"  # macOS
```

---

### Step 5: Verify Connection

In Claude Desktop, you should see:
- 🔌 icon in the bottom-right indicating MCP is connected
- Click the icon to see available tools

If connected successfully, you'll see 8 tools:
- ✓ get_input_schema
- ✓ validate_utlx
- ✓ execute_transformation
- ✓ get_stdlib_functions
- ✓ get_operators
- ✓ get_usdl_directives
- ✓ infer_output_schema
- ✓ get_examples

---

### Complete Startup Script (Claude Desktop)

Create `start-claude-desktop.sh`:

```bash
#!/bin/bash

# Step 1: Verify MCP server is built
if [ ! -f "/path/to/utl-x/mcp-server/dist/index.js" ]; then
  echo "MCP server not built. Building..."
  cd /path/to/utl-x/mcp-server
  npm run build
fi

# Step 2: Start UTLX Daemon
echo "Starting UTLX Daemon..."
cd /path/to/utl-x/modules/daemon/build/libs
java -jar utlxd-1.0.0-SNAPSHOT.jar start --lsp --api &
DAEMON_PID=$!

# Wait for daemon
sleep 3

# Step 3: Check Claude Desktop config
CONFIG_FILE="$HOME/Library/Application Support/Claude/claude_desktop_config.json"
if [ ! -f "$CONFIG_FILE" ]; then
  echo "⚠️  Claude Desktop config not found!"
  echo "Please create: $CONFIG_FILE"
  echo "See configuration section above."
  kill $DAEMON_PID
  exit 1
fi

# Step 4: Start Claude Desktop
echo "Starting Claude Desktop..."
open -a "Claude"

echo "==================================="
echo "Claude Desktop with UTLX Ready!"
echo "==================================="
echo "UTLX Daemon: http://localhost:7779"
echo "Claude Desktop: Check for 🔌 icon"
echo ""
echo "In Claude, try: 'Can you help me with UTLX?'"
echo ""
echo "Press Ctrl+C to stop daemon"

# Wait for interrupt
trap "kill $DAEMON_PID" EXIT
wait
```

Make executable:
```bash
chmod +x start-claude-desktop.sh
./start-claude-desktop.sh
```

---

## Usage Examples

### Example 1: Theia AI Assistant - Simple Transformation

**Setup:**
1. Open Theia at `http://localhost:3001`
2. Add input data in left panel:
   - Name: `customers`
   - Format: `xml`
   - Data:
   ```xml
   <customers>
     <customer id="1">
       <name>John Doe</name>
       <email>john@example.com</email>
     </customer>
     <customer id="2">
       <name>Jane Smith</name>
       <email>jane@example.com</email>
     </customer>
   </customers>
   ```

3. Click "🤖 AI Assist" button
4. Enter prompt:
   ```
   Transform the XML customers to JSON array. Extract id, name, and email fields.
   ```

5. Click "✨ Generate UTLX"

**Generated Code:**
```utlx
%utlx 1.0
input: customers xml
output json
---
{
  customers: $customers.customers.customer |> map(c => {
    id: c.@id,
    name: c.name,
    email: c.email
  })
}
```

**Result:**
- Code appears in editor
- Message: "✨ UTLX transformation generated! (1234 tokens)"
- Can immediately execute with ▶️ Execute button

---

### Example 2: Theia AI Assistant - Multi-Input Join

**Setup:**
1. Add first input:
   - Name: `customers`
   - Format: `xml`
   - Data: (customer XML)

2. Click "+ Add Input"
3. Add second input:
   - Name: `orders`
   - Format: `csv`
   - Data:
   ```csv
   OrderID,CustomerID,Amount
   O001,1,100.50
   O002,1,75.25
   O003,2,200.00
   ```

4. Click "🤖 AI Assist"
5. Enter prompt:
   ```
   Join customers with their orders. Each customer should have an array of their orders with orderId and amount.
   ```

**Generated Code:**
```utlx
%utlx 1.0
input: customers xml, orders csv
output json
---
{
  customers: $customers.customers.customer |> map(c => {
    id: c.@id,
    name: c.name,
    email: c.email,
    orders: $orders |> filter(o => o.CustomerID == c.@id) |> map(o => {
      orderId: o.OrderID,
      amount: o.Amount
    })
  })
}
```

---

### Example 3: Claude Desktop - Interactive Development

**Chat Flow:**

```
👤 You: Can you help me transform XML product data to JSON?

🤖 Claude: I'd be happy to help! Could you share your XML structure?
I can validate the format and help design the transformation.

👤 You: Here's my XML:
<products>
  <product id="P001" category="electronics">
    <name>Laptop</name>
    <price>999.99</price>
  </product>
</products>

🤖 Claude: Let me parse that structure.
[Calls: get_input_schema with your XML]

Great! I can see:
- Root element: products
- Child elements: product (with id and category attributes)
- Fields: name, price

Here's the UTLX transformation:

%utlx 1.0
input: products xml
output json
---
{
  products: $products.products.product |> map(p => {
    id: p.@id,
    category: p.@category,
    name: p.name,
    price: p.price
  })
}

Let me validate this code:
[Calls: validate_utlx]

✓ The code is valid! Would you like me to test it with your data?

👤 You: Yes, please test it.

🤖 Claude:
[Calls: execute_transformation]

Here's the output:
{
  "products": [
    {
      "id": "P001",
      "category": "electronics",
      "name": "Laptop",
      "price": "999.99"
    }
  ]
}

The transformation completed successfully in 23ms!
```

---

### Example 4: Claude Desktop - Learning UTLX

```
👤 You: I'm new to UTLX. How do I filter arrays?

🤖 Claude: Let me get the operator documentation for you.
[Calls: get_operators]

The **filter** operator is used to keep only elements that match a condition:

**Syntax:**
$array |> filter(element => condition)

**Example 1: Filter by age**
$users |> filter(u => u.age > 18)

**Example 2: Filter by status**
$orders |> filter(o => o.status == "active")

Let me find some real examples:
[Calls: get_examples with query "filter"]

Here's a complete example from the conformance suite:

%utlx 1.0
input: employees csv
output json
---
{
  activeEmployees: $employees
    |> filter(e => e.Status == "Active")
    |> map(e => {
      id: e.EmployeeID,
      name: e.Name
    })
}

Would you like to try this with your own data?
```

---

### Example 5: Claude Desktop - Debugging

```
👤 You: This code isn't working:
%utlx 1.0
input: data xml
output json
---
$data.items.item |> map(i => {
  id: i.id,
  name: i.name
})

🤖 Claude: Let me validate that code.
[Calls: validate_utlx]

I found validation errors:
- Line 5: "i.id" - if 'id' is an XML attribute, use @id instead
- Line 6: "i.name" - verify this is an element, not attribute

Let me check your XML structure:
[Calls: get_input_schema if you provide XML]

Looking at your XML, 'id' is indeed an attribute. Here's the corrected code:

%utlx 1.0
input: data xml
output json
---
$data.items.item |> map(i => {
  id: i.@id,        ← Changed to @id
  name: i.name
})

[Calls: validate_utlx on corrected code]

✓ The corrected code validates successfully!

The key rule: XML attributes always use @ prefix in UTLX.
Would you like me to test this with your data?
```

---

## Troubleshooting

### Theia AI Assistant Issues

#### Problem: "MCP server is not available"

**Cause**: MCP server not running or wrong port

**Solution**:
```bash
# Check if MCP server is running
curl http://localhost:3000/health

# Expected response:
{"status":"ok","service":"utlx-mcp-server","transport":"http","port":3000}

# If not running, check logs:
cd /path/to/utl-x/mcp-server
npm start

# Check for errors in logs
```

#### Problem: "LLM provider is not configured"

**Cause**: Missing environment variables

**Solution**:
```bash
# Check current environment
echo $UTLX_LLM_PROVIDER
echo $ANTHROPIC_API_KEY  # or OLLAMA_ENDPOINT

# Set provider
export UTLX_LLM_PROVIDER=claude  # or ollama

# Set credentials
export ANTHROPIC_API_KEY=your-key  # for Claude
# OR
export OLLAMA_ENDPOINT=http://localhost:11434  # for Ollama
export OLLAMA_MODEL=codellama

# Restart MCP server
```

#### Problem: "Claude API error: 401 Unauthorized"

**Cause**: Invalid or expired API key

**Solution**:
```bash
# Verify API key format (should start with sk-ant-)
echo $ANTHROPIC_API_KEY

# Test API key directly
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -H "content-type: application/json" \
  -d '{
    "model": "claude-3-5-sonnet-20241022",
    "max_tokens": 10,
    "messages": [{"role": "user", "content": "Hi"}]
  }'

# If error, get new key from https://console.anthropic.com/
```

#### Problem: "Ollama API error: Connection refused"

**Cause**: Ollama not running

**Solution**:
```bash
# Check if Ollama is running
curl http://localhost:11434/api/tags

# If not running, start Ollama
ollama serve

# Check available models
ollama list

# If no models, pull one
ollama pull codellama

# Test Ollama
ollama run codellama "test"
```

#### Problem: Generated code has validation errors

**Cause**: LLM generated invalid syntax

**Check Logs**:
```bash
# MCP server logs show validation errors and retry
[INFO] Generated code has validation errors
[INFO] Attempting to regenerate with error feedback
[INFO] Regenerated code is valid
```

**Manual Fix**:
- AI automatically retries with error feedback
- If still invalid, code is returned with warnings
- You can manually fix in editor
- Click "▶️ Execute" to validate

#### Problem: "Input panel not found" error

**Cause**: Widget initialization issue

**Solution**:
```bash
# Restart Theia
# Ensure Theia extension is built
cd /path/to/utl-x/theia-extension/utlx-theia-extension
yarn build

# Check browser console for errors
# Open DevTools → Console
```

---

### Claude Desktop Issues

#### Problem: No 🔌 icon in Claude Desktop

**Cause**: MCP server not configured or not connecting

**Solution**:
```bash
# 1. Check config file exists
ls -la "$HOME/Library/Application Support/Claude/claude_desktop_config.json"

# 2. Verify config syntax (must be valid JSON)
cat "$HOME/Library/Application Support/Claude/claude_desktop_config.json" | jq .

# 3. Check MCP server path is absolute
# ❌ Bad: "~/utl-x/mcp-server/dist/index.js"
# ✅ Good: "/Users/username/utl-x/mcp-server/dist/index.js"

# 4. Verify UTLX Daemon is running
curl http://localhost:7779/api/health

# 5. Restart Claude Desktop completely
# Quit (Cmd+Q) and reopen
```

#### Problem: MCP tools show errors when called

**Cause**: UTLX Daemon not running or not accessible

**Solution**:
```bash
# Check daemon status
curl http://localhost:7779/api/health

# Expected: {"status":"ok","version":"1.0.0-SNAPSHOT"}

# If not running, start daemon
cd /path/to/utl-x/modules/daemon/build/libs
java -jar utlxd-1.0.0-SNAPSHOT.jar start --lsp --api

# Check MCP server logs
# Claude Desktop → Settings → Developer → Show Logs
# Look for connection errors
```

#### Problem: "validate_utlx tool failed"

**Cause**: Invalid UTLX syntax or daemon communication error

**Debug**:
```bash
# Test daemon directly
curl -X POST http://localhost:7779/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "utlx": "%utlx 1.0\ninput: data json\noutput json\n---\n$data",
    "strict": false
  }'

# Check response for errors
```

#### Problem: Claude Desktop shows old tools after update

**Cause**: Cache issue

**Solution**:
```bash
# 1. Quit Claude Desktop completely
# 2. Clear cache (macOS)
rm -rf "$HOME/Library/Application Support/Claude/mcp_cache"

# 3. Restart Claude Desktop
# 4. Verify tools list (should show 8 tools)
```

---

### General Issues

#### Problem: Port already in use

```bash
# Find process using port 7779 (UTLX Daemon)
lsof -ti:7779

# Kill process
kill -9 $(lsof -ti:7779)

# Find process using port 3000 (MCP Server)
lsof -ti:3000
kill -9 $(lsof -ti:3000)
```

#### Problem: Out of memory (Ollama)

**Symptoms**: Ollama crashes or very slow

**Solution**:
```bash
# Use smaller model
ollama pull codellama:7b  # Instead of 13b or 34b

# Check available memory
free -h  # Linux
vm_stat  # macOS

# Restart Ollama
killall ollama
ollama serve
```

#### Problem: Slow generation (Ollama)

**Expected Performance**:
- 7B models: 2-5 seconds
- 13B models: 5-10 seconds
- First call may be slower (model loading)

**Optimization**:
```bash
# Keep model loaded in memory
ollama run codellama ""  # Preload

# Or use smaller model
ollama pull tinyllama  # Very fast, lower quality

# Check CPU usage
top -o cpu
```

---

### Debug Mode

Enable detailed logging:

```bash
# MCP Server debug logs
export UTLX_LOG_LEVEL=debug
npm start

# Theia backend logs
# Check browser DevTools → Console
# Check Theia terminal output

# Claude Desktop logs
# Settings → Developer → Show Logs
```

---

## Performance Comparison

### Theia AI Assistant

| Provider | Speed | Quality | Cost | Offline |
|----------|-------|---------|------|---------|
| **Claude 3.5 Sonnet** | ~2-3s | Excellent | ~$0.003/req | ❌ |
| **Ollama CodeLlama 7B** | ~2-5s | Good | Free | ✅ |
| **Ollama CodeLlama 13B** | ~5-10s | Very Good | Free | ✅ |
| **Ollama DeepSeek** | ~3-6s | Good | Free | ✅ |

### Claude Desktop

| Aspect | Performance |
|--------|-------------|
| **Response Time** | ~1-2s (Claude infrastructure) |
| **Tool Calls** | ~100-500ms per call |
| **Quality** | Excellent (GPT-4 class) |
| **Cost** | Included in Claude subscription |

---

## Best Practices

### Theia AI Assistant

1. **Provide Sample Data**: More context = better code
   - Add actual input data in input panel
   - Let daemon validate and create UDM
   - AI sees both original and UDM structure

2. **Be Specific in Prompts**:
   - ✅ "Transform XML customers to JSON. Extract id, name, email. Filter active customers only."
   - ❌ "Transform data"

3. **Iterate**: Generated code may need tweaks
   - Review generated code
   - Manually adjust if needed
   - Use ▶️ Execute to test

4. **Choose Right Provider**:
   - Development: Ollama (free, fast)
   - Production: Claude API (highest quality)

### Claude Desktop

1. **Provide Context**: Share your data structure early
2. **Ask for Validation**: Request code validation after generation
3. **Test Incrementally**: Build complex transformations step-by-step
4. **Learn Interactively**: Ask "why" and "how" questions
5. **Use Examples**: Request examples for unfamiliar patterns

---

## Security Notes

### API Keys
- Never commit API keys to version control
- Use environment variables or config files
- Rotate keys periodically

### Data Privacy
- **Claude API**: Data sent to Anthropic (see their privacy policy)
- **Ollama**: Fully local, no data leaves your machine
- **Claude Desktop**: Same as Claude API

### Network Security
- MCP Server HTTP mode: Only bind to localhost
- Use firewall rules if exposing ports
- Consider VPN for remote access

---

## Support

### Getting Help

**Theia AI Assistant**:
- Check MCP server logs: `npm start` output
- Check Theia logs: Browser DevTools → Console
- Test MCP endpoint: `curl http://localhost:3000/health`

**Claude Desktop**:
- Claude Desktop → Settings → Developer → Show Logs
- Check daemon logs: UTLX Daemon output
- Verify tools list: Click 🔌 icon

### Documentation
- UTLX Language: `/docs/utlx-language-spec.md`
- MCP Protocol: https://modelcontextprotocol.io
- Claude API: https://docs.anthropic.com
- Ollama: https://ollama.ai/docs

### Community
- GitHub Issues: Report bugs and feature requests
- Discussions: Ask questions and share tips

---

## Appendix: Complete Configuration Examples

### Theia + Claude API
```bash
# .env file or export commands

# UTLX Daemon
UTLX_DAEMON_URL=http://localhost:7779

# MCP Server
UTLX_MCP_TRANSPORT=http
UTLX_MCP_PORT=3000
UTLX_LOG_LEVEL=info

# LLM Provider
UTLX_LLM_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
UTLX_LLM_MODEL=claude-3-5-sonnet-20241022
UTLX_LLM_MAX_TOKENS=4096
```

### Theia + Ollama
```bash
# .env file or export commands

# UTLX Daemon
UTLX_DAEMON_URL=http://localhost:7779

# MCP Server
UTLX_MCP_TRANSPORT=http
UTLX_MCP_PORT=3000
UTLX_LOG_LEVEL=info

# LLM Provider
UTLX_LLM_PROVIDER=ollama
OLLAMA_ENDPOINT=http://localhost:11434
OLLAMA_MODEL=codellama
UTLX_LLM_MAX_TOKENS=4096
```

### Claude Desktop
```json
{
  "mcpServers": {
    "utlx": {
      "command": "node",
      "args": [
        "/Users/username/utl-x/mcp-server/dist/index.js"
      ],
      "env": {
        "UTLX_DAEMON_URL": "http://localhost:7779",
        "UTLX_MCP_TRANSPORT": "stdio",
        "UTLX_LOG_LEVEL": "info"
      }
    }
  }
}
```

---

**Version**: 1.0.0
**Last Updated**: 2025-11-21
**Compatibility**: UTLX 1.0, MCP Server 1.0.0, Claude Desktop 2024+
