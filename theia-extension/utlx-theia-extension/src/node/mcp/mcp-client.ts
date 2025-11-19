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
        const mcpPort = process.env.UTLX_MCP_PORT || '3000';
        this.mcpServerUrl = `http://localhost:${mcpPort}`;
        this.requestTimeout = 60000; // 60 seconds for LLM calls
        console.log('[MCPClient] Initialized with URL:', this.mcpServerUrl);
    }

    /**
     * Call an MCP tool
     */
    async callTool(toolName: string, args: Record<string, any>): Promise<any> {
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

        console.log('[MCPClient] Calling tool:', toolName, 'with request ID:', requestId);

        const response = await this.sendRequest(request);

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
}
