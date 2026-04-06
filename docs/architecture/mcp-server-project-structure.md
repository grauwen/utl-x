# MCP Server - Project Structure

This document outlines the recommended project structure for the UTL-X MCP Server implementation.

## Directory Structure

```
utl-x/
├── mcp-server/                          # NEW: MCP Server implementation
│   ├── src/
│   │   ├── index.ts                     # Entry point
│   │   ├── server/
│   │   │   ├── MCPServer.ts            # Main MCP server class
│   │   │   ├── JSONRPCHandler.ts       # JSON-RPC message handling
│   │   │   └── ToolRegistry.ts         # Tool registration and dispatch
│   │   ├── daemon/
│   │   │   ├── DaemonClient.ts         # HTTP client for daemon API
│   │   │   ├── types.ts                # Daemon API types
│   │   │   └── errors.ts               # Daemon-specific errors
│   │   ├── tools/
│   │   │   ├── index.ts                # Export all tools
│   │   │   ├── GetInputSchema.ts       # Tool 1
│   │   │   ├── GetStdlibFunctions.ts   # Tool 2
│   │   │   ├── ValidateUTLX.ts         # Tool 3
│   │   │   ├── InferOutputSchema.ts    # Tool 4
│   │   │   ├── ExecuteTransformation.ts # Tool 5
│   │   │   └── GetExamples.ts          # Tool 6
│   │   ├── llm/
│   │   │   ├── LLMClient.ts            # Abstract LLM client
│   │   │   ├── ClaudeClient.ts         # Anthropic Claude API
│   │   │   ├── OpenAIClient.ts         # OpenAI API
│   │   │   ├── OllamaClient.ts         # Local Ollama
│   │   │   └── prompts/
│   │   │       ├── system.ts           # System prompt templates
│   │   │       ├── generation.ts       # UTLX generation prompts
│   │   │       └── refinement.ts       # Error correction prompts
│   │   ├── indexing/
│   │   │   ├── ConformanceIndexer.ts   # Index conformance suite tests
│   │   │   ├── SimilaritySearch.ts     # TF-IDF or embedding search
│   │   │   └── types.ts                # Index data structures
│   │   ├── utils/
│   │   │   ├── logger.ts               # Winston logger setup
│   │   │   ├── config.ts               # Configuration loading
│   │   │   ├── cache.ts                # Response caching
│   │   │   └── errors.ts               # Error handling utilities
│   │   └── types/
│   │       ├── mcp.ts                  # MCP protocol types
│   │       ├── tools.ts                # Tool parameter/response types
│   │       └── schema.ts               # Schema types
│   ├── test/
│   │   ├── unit/
│   │   │   ├── tools/
│   │   │   │   ├── GetInputSchema.test.ts
│   │   │   │   ├── GetStdlibFunctions.test.ts
│   │   │   │   └── ...
│   │   │   ├── daemon/
│   │   │   │   └── DaemonClient.test.ts
│   │   │   └── llm/
│   │   │       └── prompts.test.ts
│   │   ├── integration/
│   │   │   ├── tools-integration.test.ts
│   │   │   ├── daemon-integration.test.ts
│   │   │   └── llm-integration.test.ts
│   │   ├── e2e/
│   │   │   ├── generation-workflow.test.ts
│   │   │   └── conversation-flow.test.ts
│   │   └── fixtures/
│   │       ├── sample-schemas/
│   │       ├── sample-transformations/
│   │       └── mock-responses/
│   ├── config/
│   │   ├── default.yaml               # Default configuration
│   │   ├── development.yaml           # Development overrides
│   │   └── production.yaml            # Production overrides
│   ├── scripts/
│   │   ├── build-index.ts             # Build conformance suite index
│   │   ├── start-daemon.sh            # Helper to start daemon
│   │   └── test-tools.ts              # Manual tool testing
│   ├── package.json
│   ├── tsconfig.json
│   ├── .eslintrc.js
│   ├── .prettierrc
│   ├── jest.config.js
│   ├── Dockerfile
│   └── README.md
│
├── modules/daemon/                      # EXISTING: UTL-X Daemon
│   └── src/main/kotlin/...
│       └── (Add new API endpoints for MCP integration)
│
└── docs/architecture/                   # EXISTING: Architecture docs
    ├── mcp-assisted-generation.md       # Created
    ├── mcp-implementation-checklist.md  # Created
    └── mcp-tools-reference.md           # Created
```

## Key Files Breakdown

### 1. Entry Point (`src/index.ts`)

```typescript
import { MCPServer } from './server/MCPServer';
import { loadConfig } from './utils/config';
import { logger } from './utils/logger';
import { DaemonClient } from './daemon/DaemonClient';
import { ConformanceIndexer } from './indexing/ConformanceIndexer';

async function main() {
  try {
    // Load configuration
    const config = await loadConfig();
    logger.info('Configuration loaded', { config });

    // Initialize daemon client
    const daemonClient = new DaemonClient({
      url: config.daemon.url,
      timeout: config.daemon.timeout
    });

    // Check daemon health
    await daemonClient.healthCheck();
    logger.info('Connected to UTL-X daemon');

    // Build/load conformance suite index
    const indexer = new ConformanceIndexer(config.conformance.path);
    await indexer.buildIndex();
    logger.info('Conformance suite index ready');

    // Initialize MCP server
    const server = new MCPServer({
      port: config.server.port,
      daemonClient,
      indexer,
      config
    });

    // Start server
    await server.start();
    logger.info(`MCP server listening on port ${config.server.port}`);

  } catch (error) {
    logger.error('Failed to start MCP server', { error });
    process.exit(1);
  }
}

main();
```

### 2. MCP Server (`src/server/MCPServer.ts`)

```typescript
import express from 'express';
import { JSONRPCHandler } from './JSONRPCHandler';
import { ToolRegistry } from './ToolRegistry';
import { DaemonClient } from '../daemon/DaemonClient';
import { ConformanceIndexer } from '../indexing/ConformanceIndexer';
import * as tools from '../tools';

export class MCPServer {
  private app: express.Application;
  private rpcHandler: JSONRPCHandler;
  private toolRegistry: ToolRegistry;

  constructor(options: {
    port: number;
    daemonClient: DaemonClient;
    indexer: ConformanceIndexer;
    config: any;
  }) {
    this.app = express();
    this.toolRegistry = new ToolRegistry();

    // Register all tools
    this.registerTools(options.daemonClient, options.indexer);

    // Initialize JSON-RPC handler
    this.rpcHandler = new JSONRPCHandler(this.toolRegistry);

    // Setup middleware
    this.setupMiddleware();

    // Setup routes
    this.setupRoutes();
  }

  private registerTools(daemon: DaemonClient, indexer: ConformanceIndexer) {
    this.toolRegistry.register('get_input_schema', new tools.GetInputSchema(daemon));
    this.toolRegistry.register('get_stdlib_functions', new tools.GetStdlibFunctions());
    this.toolRegistry.register('validate_utlx', new tools.ValidateUTLX(daemon));
    this.toolRegistry.register('infer_output_schema', new tools.InferOutputSchema(daemon));
    this.toolRegistry.register('execute_transformation', new tools.ExecuteTransformation(daemon));
    this.toolRegistry.register('get_examples', new tools.GetExamples(indexer));
  }

  private setupMiddleware() {
    this.app.use(express.json({ limit: '10mb' }));
    this.app.use(this.logRequests.bind(this));
  }

  private setupRoutes() {
    // Main MCP endpoint
    this.app.post('/mcp', async (req, res) => {
      const response = await this.rpcHandler.handle(req.body);
      res.json(response);
    });

    // Health check
    this.app.get('/health', (req, res) => {
      res.json({ status: 'ok' });
    });
  }

  async start(): Promise<void> {
    return new Promise((resolve) => {
      this.app.listen(this.port, () => {
        resolve();
      });
    });
  }
}
```

### 3. Tool Interface (`src/tools/index.ts`)

```typescript
export interface MCPTool {
  name: string;
  description: string;
  parameters: ToolParameters;
  execute(params: any): Promise<any>;
}

export interface ToolParameters {
  type: 'object';
  properties: Record<string, ParameterSpec>;
  required?: string[];
}

export interface ParameterSpec {
  type: string;
  description: string;
  enum?: string[];
  default?: any;
}

// Export all tool implementations
export { GetInputSchema } from './GetInputSchema';
export { GetStdlibFunctions } from './GetStdlibFunctions';
export { ValidateUTLX } from './ValidateUTLX';
export { InferOutputSchema } from './InferOutputSchema';
export { ExecuteTransformation } from './ExecuteTransformation';
export { GetExamples } from './GetExamples';
```

### 4. Example Tool Implementation (`src/tools/GetInputSchema.ts`)

```typescript
import { MCPTool, ToolParameters } from './index';
import { DaemonClient } from '../daemon/DaemonClient';
import { logger } from '../utils/logger';

export class GetInputSchema implements MCPTool {
  name = 'get_input_schema';
  description = 'Parse input data or schema files to extract structural information';

  parameters: ToolParameters = {
    type: 'object',
    properties: {
      source: {
        type: 'string',
        description: 'File path or inline data'
      },
      format: {
        type: 'string',
        description: 'Input format',
        enum: ['xml', 'json', 'csv', 'yaml', 'xsd', 'json-schema', 'avro', 'protobuf']
      },
      mode: {
        type: 'string',
        description: 'Parse mode',
        enum: ['instance', 'schema'],
        default: 'auto'
      }
    },
    required: ['source', 'format']
  };

  constructor(private daemon: DaemonClient) {}

  async execute(params: {
    source: string;
    format: string;
    mode?: 'instance' | 'schema';
  }): Promise<any> {
    logger.info('Executing get_input_schema', { params });

    try {
      // Determine if source is file path or inline data
      const isFilePath = !params.source.includes('\n') && params.source.length < 500;
      const content = isFilePath
        ? await this.readFile(params.source)
        : params.source;

      // Call appropriate daemon endpoint
      if (params.mode === 'schema' || this.isSchemaFormat(params.format)) {
        return await this.parseSchema(content, params.format);
      } else {
        return await this.inferSchema(content, params.format);
      }
    } catch (error) {
      logger.error('Error in get_input_schema', { error });
      throw error;
    }
  }

  private isSchemaFormat(format: string): boolean {
    return ['xsd', 'json-schema', 'avro', 'protobuf'].includes(format);
  }

  private async parseSchema(content: string, format: string): Promise<any> {
    const response = await this.daemon.post('/api/parse-schema', {
      format,
      content
    });

    return this.normalizeSchema(response, format);
  }

  private async inferSchema(content: string, format: string): Promise<any> {
    const response = await this.daemon.post('/api/infer-schema', {
      format,
      data: content
    });

    return this.normalizeSchema(response, format);
  }

  private normalizeSchema(schema: any, format: string): any {
    // Convert daemon's schema representation to normalized format
    // ... implementation details
    return {
      format,
      structure: schema,
      summary: this.generateSummary(schema)
    };
  }

  private generateSummary(schema: any): string {
    // Generate human-readable summary for LLM
    // ... implementation details
    return 'Schema summary';
  }

  private async readFile(path: string): Promise<string> {
    // Read file from filesystem
    // ... implementation details
    return '';
  }
}
```

### 5. Daemon Client (`src/daemon/DaemonClient.ts`)

```typescript
import axios, { AxiosInstance } from 'axios';
import { logger } from '../utils/logger';
import { DaemonError } from './errors';

export class DaemonClient {
  private client: AxiosInstance;

  constructor(options: { url: string; timeout: number }) {
    this.client = axios.create({
      baseURL: options.url,
      timeout: options.timeout,
      headers: {
        'Content-Type': 'application/json'
      }
    });

    // Add response interceptor for error handling
    this.client.interceptors.response.use(
      response => response,
      error => {
        logger.error('Daemon request failed', { error });
        throw new DaemonError(error.message, error.response?.status);
      }
    );
  }

  async post(endpoint: string, data: any): Promise<any> {
    const response = await this.client.post(endpoint, data);
    return response.data;
  }

  async healthCheck(): Promise<boolean> {
    try {
      await this.client.get('/health');
      return true;
    } catch {
      return false;
    }
  }
}
```

### 6. Configuration (`config/default.yaml`)

```yaml
# Default MCP Server Configuration

server:
  port: 7779
  log_level: info
  max_request_size: 10mb

daemon:
  url: http://localhost:7778
  timeout: 10000  # milliseconds
  retry_attempts: 3
  retry_delay: 1000

conformance:
  path: ../conformance-suite/utlx/tests
  index_path: ~/.utlx/conformance-index.json
  rebuild_on_startup: false

stdlib:
  function_registry: ../stdlib/build/generated/function-registry/utlx-functions.json

tools:
  get_input_schema:
    max_file_size: 5mb

  validate_utlx:
    strict_mode: true

  execute_transformation:
    timeout: 5000
    max_memory_mb: 100

  get_examples:
    max_results: 20
    similarity_threshold: 0.5

llm:
  provider: claude  # claude | openai | ollama
  api_key: ${ANTHROPIC_API_KEY}  # From environment
  model: claude-3-5-sonnet-20241022
  temperature: 0.2
  max_tokens: 4000

cache:
  enabled: true
  ttl: 3600  # seconds
  max_size: 100mb
```

### 7. Package.json

```json
{
  "name": "@utlx/mcp-server",
  "version": "0.1.0",
  "description": "MCP Server for AI-assisted UTL-X generation",
  "main": "dist/index.js",
  "scripts": {
    "build": "tsc",
    "watch": "tsc -w",
    "start": "node dist/index.js",
    "dev": "nodemon --watch src --exec ts-node src/index.ts",
    "test": "jest",
    "test:watch": "jest --watch",
    "test:coverage": "jest --coverage",
    "lint": "eslint src --ext .ts",
    "format": "prettier --write \"src/**/*.ts\"",
    "build-index": "ts-node scripts/build-index.ts"
  },
  "dependencies": {
    "@anthropic-ai/sdk": "^0.20.0",
    "axios": "^1.6.0",
    "express": "^4.18.0",
    "winston": "^3.11.0",
    "yaml": "^2.3.0",
    "zod": "^3.22.0"
  },
  "devDependencies": {
    "@types/express": "^4.17.0",
    "@types/jest": "^29.5.0",
    "@types/node": "^20.10.0",
    "@typescript-eslint/eslint-plugin": "^6.0.0",
    "@typescript-eslint/parser": "^6.0.0",
    "eslint": "^8.50.0",
    "jest": "^29.7.0",
    "nodemon": "^3.0.0",
    "prettier": "^3.0.0",
    "ts-jest": "^29.1.0",
    "ts-node": "^10.9.0",
    "typescript": "^5.3.0"
  },
  "engines": {
    "node": ">=18.0.0"
  }
}
```

### 8. TypeScript Config (`tsconfig.json`)

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "commonjs",
    "lib": ["ES2022"],
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "declaration": true,
    "declarationMap": true,
    "sourceMap": true,
    "moduleResolution": "node",
    "types": ["node", "jest"]
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "test"]
}
```

### 9. Dockerfile

```dockerfile
FROM node:18-alpine

WORKDIR /app

# Copy package files
COPY package*.json ./
COPY tsconfig.json ./

# Install dependencies
RUN npm ci --only=production

# Copy source
COPY src ./src
COPY config ./config

# Build
RUN npm run build

# Expose MCP server port
EXPOSE 7779

# Start server
CMD ["node", "dist/index.js"]
```

## Development Workflow

### Initial Setup
```bash
cd utl-x
mkdir mcp-server
cd mcp-server
npm init -y
npm install <dependencies>
```

### Development
```bash
npm run dev        # Start with hot reload
npm run test:watch # Run tests in watch mode
```

### Testing
```bash
npm test           # Run all tests
npm run test:coverage  # With coverage
```

### Build
```bash
npm run build      # Compile TypeScript
npm start          # Run compiled code
```

### Docker
```bash
docker build -t utlx-mcp-server .
docker run -p 7779:7779 \
  -e UTLX_DAEMON_URL=http://host.docker.internal:7778 \
  -e ANTHROPIC_API_KEY=your-key \
  utlx-mcp-server
```

## Integration with Existing UTL-X Project

### Gradle Integration (Optional)

Add to root `build.gradle.kts`:

```kotlin
tasks.register("buildMCPServer") {
    description = "Build MCP Server"
    group = "build"

    doLast {
        exec {
            workingDir = file("mcp-server")
            commandLine("npm", "run", "build")
        }
    }
}

tasks.register("testMCPServer") {
    description = "Test MCP Server"
    group = "verification"

    doLast {
        exec {
            workingDir = file("mcp-server")
            commandLine("npm", "test")
        }
    }
}
```

### CI/CD Integration

Add to `.github/workflows/ci.yml`:

```yaml
jobs:
  test-mcp-server:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - name: Install dependencies
        working-directory: mcp-server
        run: npm ci
      - name: Run tests
        working-directory: mcp-server
        run: npm test
      - name: Build
        working-directory: mcp-server
        run: npm run build
```

## Next Steps

1. Create `mcp-server/` directory
2. Initialize npm project with `package.json`
3. Set up TypeScript configuration
4. Implement basic MCP server skeleton
5. Implement daemon client
6. Implement first tool (`get_stdlib_functions` - easiest, no daemon required)
7. Add comprehensive tests
8. Integrate with existing UTL-X daemon
