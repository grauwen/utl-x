/**
 * UTL-X Theia Extension Protocol
 *
 * Defines the API contract between frontend and backend for UTL-X operations.
 * Supports both Design-Time and Runtime modes.
 */

import { DirectiveRegistry } from './usdl-types';

/**
 * Service path for RPC communication
 */
export const UTLX_SERVICE_PATH = '/services/utlx';
export const UTLX_SERVICE_SYMBOL = Symbol('UTLXService');

/**
 * Execution modes
 */
export enum UTLXMode {
    /** Design-Time: Schema-based type checking (for tier-1 formats) */
    DESIGN_TIME = 'design-time',
    /** Runtime: Data transformation execution */
    RUNTIME = 'runtime'
}

/**
 * Format types
 */
export type DataFormat = 'xml' | 'json' | 'yaml' | 'csv' | 'xsd' | 'jsch' | 'avro' | 'proto' | 'auto';
export type SchemaFormat = 'xsd' | 'jsch' | 'avro' | 'proto';
export type Tier1Format = 'xml' | 'json' | 'yaml' | 'csv';
export type Tier2Format = 'xsd' | 'jsch' | 'avsc' | 'proto';

/**
 * Position in a text document
 */
export interface Position {
    line: number;
    column: number;
}

/**
 * Range in a text document
 */
export interface Range {
    start: Position;
    end: Position;
}

/**
 * Diagnostic severity levels
 */
export enum DiagnosticSeverity {
    Error = 1,
    Warning = 2,
    Information = 3,
    Hint = 4
}

/**
 * Diagnostic message (error, warning, info)
 */
export interface Diagnostic {
    severity: DiagnosticSeverity;
    range: Range;
    message: string;
    source?: string;
    code?: string | number;
}

/**
 * Input document for transformation
 */
export interface InputDocument {
    id: string;
    name: string;
    content: string;
    format: DataFormat;
    uri?: string;
    encoding?: string;  // Character encoding: UTF-8, UTF-16LE, UTF-16BE, ISO-8859-1, etc.
    bom?: boolean;      // Whether the input has a Byte Order Mark
}

/**
 * Schema document for design-time mode
 */
export interface SchemaDocument {
    format: SchemaFormat;
    content: string;
    uri?: string;
}

/**
 * Parse result
 */
export interface ParseResult {
    success: boolean;
    ast?: any; // AST structure (opaque for now)
    errors: Diagnostic[];
    warnings: Diagnostic[];
}

/**
 * Validation result
 */
export interface ValidationResult {
    valid: boolean;
    diagnostics: Diagnostic[];
    typeErrors?: Diagnostic[];
}

/**
 * Execution result (runtime mode)
 */
export interface ExecutionResult {
    success: boolean;
    output?: string;
    format?: DataFormat;
    executionTimeMs?: number;
    error?: string;
    diagnostics?: Diagnostic[];
}

/**
 * UDM validation request
 */
export interface ValidateUdmRequest {
    content: string;
    format: DataFormat;
    csvHeaders?: boolean;
    csvDelimiter?: string;
}

/**
 * UDM validation result
 */
export interface ValidateUdmResult {
    success: boolean;
    error?: string;
    diagnostics?: Diagnostic[];
    udmLanguage?: string;  // The parsed UDM representation (when success=true)
}

/**
 * Schema inference result (design-time mode)
 */
export interface SchemaInferenceResult {
    success: boolean;
    schema?: string; // JSON Schema or XSD
    schemaFormat?: SchemaFormat;
    error?: string;  // Error message when success=false
    typeErrors?: Diagnostic[];
    warnings?: Diagnostic[];
}

/**
 * Hover information
 */
export interface HoverInfo {
    contents: string[]; // Markdown strings
    range?: Range;
}

/**
 * Completion item kind
 */
export enum CompletionItemKind {
    Text = 1,
    Method = 2,
    Function = 3,
    Constructor = 4,
    Field = 5,
    Variable = 6,
    Class = 7,
    Interface = 8,
    Module = 9,
    Property = 10,
    Unit = 11,
    Value = 12,
    Enum = 13,
    Keyword = 14,
    Snippet = 15,
    Color = 16,
    File = 17,
    Reference = 18,
    Folder = 19,
    EnumMember = 20,
    Constant = 21,
    Struct = 22,
    Event = 23,
    Operator = 24,
    TypeParameter = 25
}

/**
 * Completion item
 */
export interface CompletionItem {
    label: string;
    kind: CompletionItemKind;
    detail?: string;
    documentation?: string;
    insertText?: string;
    sortText?: string;
    filterText?: string;
}

/**
 * Function information from standard library
 */
export interface FunctionInfo {
    name: string;
    category: string;
    signature: string;
    description: string;
    parameters: ParameterInfo[];
    returnType: string;
    examples?: string[];
}

/**
 * Parameter information
 */
export interface ParameterInfo {
    name: string;
    type: string;
    description?: string;
    optional?: boolean;
    defaultValue?: string;
}

/**
 * Operator information
 */
export interface OperatorInfo {
    symbol: string;
    name: string;
    category: string;
    description: string;
    syntax: string;
    precedence: number;
    associativity: 'left' | 'right';
    examples: string[];
    tooltip: string;
    unary?: boolean;
}

/**
 * Mode configuration
 */
export interface ModeConfiguration {
    mode: UTLXMode;
    inputSchema?: SchemaDocument;
    autoInferSchema?: boolean;
    enableTypeChecking?: boolean;
}

/**
 * Main service interface
 */
export interface UTLXService {
    /**
     * Parse UTL-X source code
     */
    parse(source: string, documentId?: string): Promise<ParseResult>;

    /**
     * Validate UTL-X source code
     */
    validate(source: string): Promise<ValidationResult>;

    /**
     * Execute transformation (runtime mode)
     */
    execute(source: string, inputs: InputDocument[]): Promise<ExecutionResult>;

    /**
     * Infer output schema (design-time mode)
     */
    inferSchema(source: string, inputSchema?: SchemaDocument): Promise<SchemaInferenceResult>;

    /**
     * Validate if input data can be parsed to UDM
     * Uses /api/udm/validate endpoint
     */
    validateUdm(request: ValidateUdmRequest): Promise<ValidateUdmResult>;

    /**
     * Get hover information at position
     */
    getHover(source: string, position: Position): Promise<HoverInfo | null>;

    /**
     * Get completion suggestions at position
     */
    getCompletions(source: string, position: Position): Promise<CompletionItem[]>;

    /**
     * Get available standard library functions
     */
    getFunctions(): Promise<FunctionInfo[]>;

    /**
     * Get available UTLX operators
     */
    getOperators(): Promise<OperatorInfo[]>;

    /**
     * Get USDL directive registry
     */
    getUsdlDirectives(): Promise<DirectiveRegistry>;

    /**
     * Set mode configuration
     */
    setMode(config: ModeConfiguration): Promise<void>;

    /**
     * Get current mode configuration
     */
    getMode(): Promise<ModeConfiguration>;

    /**
     * Ping daemon to check if alive
     */
    ping(): Promise<boolean>;

    /**
     * Restart daemon
     */
    restart(): Promise<void>;

    /**
     * Generate UTLX code from natural language prompt using AI
     */
    generateUtlxFromPrompt(request: GenerateUtlxRequest): Promise<GenerateUtlxResponse>;

    /**
     * Check if LLM provider is configured and available
     */
    checkLlmStatus(): Promise<LlmStatusResponse>;
}

/**
 * JSON-RPC types for daemon communication
 */
export interface JsonRpcRequest {
    jsonrpc: '2.0';
    id: number | string;
    method: string;
    params?: any;
}

export interface JsonRpcResponse {
    jsonrpc: '2.0';
    id: number | string;
    result?: any;
    error?: JsonRpcError;
}

export interface JsonRpcError {
    code: number;
    message: string;
    data?: any;
}

/**
 * Daemon status
 */
export interface DaemonStatus {
    running: boolean;
    version?: string;
    uptime?: number;
    mode?: UTLXMode;
}

/**
 * MCP Integration types
 */
export interface MCPToolRequest {
    tool: string;
    arguments: Record<string, any>;
}

export interface MCPToolResponse {
    success: boolean;
    result?: any;
    error?: string;
}

/**
 * AI Code Generation types
 */
export interface GenerateUtlxInput {
    name: string;
    format: string;
    originalData?: string;
    udm?: string;
}

export interface GenerateUtlxRequest {
    prompt: string;
    inputs: GenerateUtlxInput[];
    outputFormat: string;
    originalHeader: string;  // Original UTLX header from editor (required for validation)
}

export interface GenerateUtlxResponse {
    success: boolean;
    utlx?: string;
    validation?: {
        valid: boolean;
        diagnostics?: Diagnostic[];
        message?: string;
        warning?: string;
        attempts?: number;  // Number of LLM attempts made
    };
    usage?: {
        inputTokens: number;
        outputTokens: number;
    };
    error?: string;
}

/**
 * LLM status check response
 */
export interface LlmStatusResponse {
    configured: boolean;
    available: boolean;
    provider?: string;
    model?: string;
    message?: string;
    error?: string;
}

/**
 * Widget IDs
 */
export const INPUT_PANEL_ID = 'utlx-input-panel';
export const OUTPUT_PANEL_ID = 'utlx-output-panel';
export const EDITOR_ID = 'utlx-editor';

/**
 * Command IDs
 */
export namespace UTLXCommands {
    export const EXECUTE_TRANSFORMATION = 'utlx.executeTransformation';
    export const VALIDATE_CODE = 'utlx.validateCode';
    export const INFER_SCHEMA = 'utlx.inferSchema';
    export const TOGGLE_MODE = 'utlx.toggleMode';
    export const LOAD_INPUT = 'utlx.loadInput';
    export const LOAD_SCHEMA = 'utlx.loadSchema';
    export const SAVE_OUTPUT = 'utlx.saveOutput';
    export const CLEAR_PANELS = 'utlx.clearPanels';
    export const RESTART_DAEMON = 'utlx.restartDaemon';
    export const SHOW_FUNCTIONS = 'utlx.showFunctions';
}

/**
 * Preference IDs
 */
export namespace UTLXPreferences {
    export const DAEMON_PATH = 'utlx.daemon.path';
    export const DAEMON_LOG_FILE = 'utlx.daemon.logFile';
    export const AUTO_EXECUTE = 'utlx.autoExecute';
    export const AUTO_EXECUTE_DELAY = 'utlx.autoExecuteDelay';
    export const DEFAULT_MODE = 'utlx.defaultMode';
    export const ENABLE_TYPE_CHECKING = 'utlx.enableTypeChecking';
}
