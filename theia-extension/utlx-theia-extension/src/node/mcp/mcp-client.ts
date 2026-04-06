/**
 * MCP Client for calling the UTL-X MCP Server
 *
 * Communicates with the MCP server via HTTP to invoke tools like generate_utlx_from_prompt
 */

import { injectable } from 'inversify';
import * as http from 'http';

export interface MCPToolCallRequest {
    jsonrpc: '2.0';
    id: number | string;
    method: 'tools/call';
    params: {
        name: string;
        arguments: Record<string, any>;
    };
}

export interface MCPToolCallResponse {
    jsonrpc: '2.0';
    id: number | string;
    result?: {
        content: Array<{
            type: string;
            text: string;
        }>;
        isError?: boolean;
    };
    error?: {
        code: number;
        message: string;
        data?: any;
    };
}

@injectable()
export class MCPClient {
    private mcpServerUrl: string;
    private requestTimeout: number;
    private nextRequestId: number = 1;

    constructor() {
        // MCP server runs on HTTP transport by default
        const mcpPort = process.env.UTLX_MCP_PORT || '3001';
        this.mcpServerUrl = `http://localhost:${mcpPort}`;
        this.requestTimeout = 300000; // 5 minutes for large LLM models (e.g., codellama:70b)
        console.log('[MCPClient] Initialized with URL:', this.mcpServerUrl);
        console.log('[MCPClient] Request timeout:', this.requestTimeout / 1000, 'seconds');
    }

    /**
     * Call an MCP tool
     */
    async callTool(toolName: string, args: Record<string, any>): Promise<any> {
        return this.callToolInternal(toolName, args, false);
    }

    /**
     * Call an MCP tool with SSE progress notifications
     */
    async callToolWithProgress(
        toolName: string,
        args: Record<string, any>,
        onProgress: (progress: number, message?: string) => void
    ): Promise<any> {
        return this.callToolInternal(toolName, args, true, onProgress);
    }

    /**
     * Internal method to call MCP tool with optional SSE support
     */
    private async callToolInternal(
        toolName: string,
        args: Record<string, any>,
        useSSE: boolean,
        onProgress?: (progress: number, message?: string) => void
    ): Promise<any> {
        const requestId = this.nextRequestId++;

        const request: MCPToolCallRequest = {
            jsonrpc: '2.0',
            id: requestId,
            method: 'tools/call',
            params: {
                name: toolName,
                arguments: args,
            },
        };

        console.log('[MCPClient] Calling tool:', toolName, 'with request ID:', requestId, 'SSE:', useSSE);

        const response = useSSE
            ? await this.sendRequestWithSSE(request, onProgress)
            : await this.sendRequest(request);

        if (response.error) {
            throw new Error(`MCP tool call failed: ${response.error.message}`);
        }

        if (!response.result) {
            throw new Error('MCP tool returned no result');
        }

        // Extract text from content array
        if (response.result.content && response.result.content.length > 0) {
            const textContent = response.result.content
                .filter(c => c.type === 'text')
                .map(c => c.text)
                .join('\n');

            try {
                // Try to parse as JSON
                return JSON.parse(textContent);
            } catch {
                // Return raw text if not JSON
                return textContent;
            }
        }

        return response.result;
    }

    /**
     * Check if MCP server is available
     */
    async ping(): Promise<boolean> {
        try {
            const request = {
                jsonrpc: '2.0',
                id: 0,
                method: 'ping',
                params: {},
            };

            const response = await this.sendRequest(request);
            return !response.error;
        } catch (error) {
            console.error('[MCPClient] Ping failed:', error);
            return false;
        }
    }

    /**
     * Send HTTP request to MCP server
     */
    private async sendRequest(request: any): Promise<MCPToolCallResponse> {
        return new Promise((resolve, reject) => {
            const postData = JSON.stringify(request);

            const options = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(postData),
                },
                timeout: this.requestTimeout,
            };

            const req = http.request(this.mcpServerUrl, options, (res) => {
                let data = '';

                res.on('data', (chunk) => {
                    data += chunk;
                });

                res.on('end', () => {
                    try {
                        const response = JSON.parse(data);
                        resolve(response);
                    } catch (error) {
                        reject(new Error(`Failed to parse MCP response: ${error}`));
                    }
                });
            });

            req.on('error', (error) => {
                reject(new Error(`MCP request failed: ${error.message}`));
            });

            req.on('timeout', () => {
                req.destroy();
                reject(new Error('MCP request timed out'));
            });

            req.write(postData);
            req.end();
        });
    }

    /**
     * Send HTTP request with SSE support for progress notifications
     */
    private async sendRequestWithSSE(
        request: any,
        onProgress?: (progress: number, message?: string) => void
    ): Promise<MCPToolCallResponse> {
        return new Promise((resolve, reject) => {
            const postData = JSON.stringify(request);

            const options = {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(postData),
                    'Accept': 'text/event-stream', // Request SSE
                },
                timeout: this.requestTimeout,
            };

            console.log('[MCPClient] Sending SSE request...');

            const req = http.request(this.mcpServerUrl, options, (res) => {
                let buffer = '';

                res.on('data', (chunk) => {
                    buffer += chunk.toString();

                    // Process SSE events
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || ''; // Keep incomplete line in buffer

                    for (const line of lines) {
                        if (line.startsWith('data: ')) {
                            const data = line.substring(6);
                            try {
                                const event = JSON.parse(data);

                                // Check if it's a progress notification
                                if (event.method === 'notifications/progress' && event.params && onProgress) {
                                    console.log('[MCPClient] Progress:', event.params.progress, event.params.message);
                                    onProgress(event.params.progress, event.params.message);
                                }
                                // Check if it's the final result
                                else if (event.jsonrpc === '2.0' && (event.result || event.error)) {
                                    console.log('[MCPClient] Received final result via SSE');
                                    resolve(event);
                                }
                            } catch (error) {
                                console.error('[MCPClient] Failed to parse SSE event:', error);
                            }
                        }
                    }
                });

                res.on('end', () => {
                    console.log('[MCPClient] SSE stream ended');
                });
            });

            req.on('error', (error) => {
                reject(new Error(`MCP SSE request failed: ${error.message}`));
            });

            req.on('timeout', () => {
                req.destroy();
                reject(new Error('MCP SSE request timed out'));
            });

            req.write(postData);
            req.end();
        });
    }
}
