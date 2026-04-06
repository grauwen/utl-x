/**
 * Health Monitor Widget
 *
 * Displays real-time health status of UTLXD and MCP servers in the bottom panel.
 * Pings both servers every 2 seconds to verify they are alive.
 */

import * as React from 'react';
import { injectable, inject, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';
import { MessageService } from '@theia/core';

interface ServerHealth {
    name: string;
    status: 'online' | 'offline' | 'checking';
    lastPing?: number;
    responseTime?: number;
    error?: string;
}

interface HealthState {
    utlxd: ServerHealth;
    mcp: ServerHealth;
}

@injectable()
export class HealthMonitorWidget extends ReactWidget {
    static readonly ID = 'utlx-health-monitor';
    static readonly LABEL = 'UTL-X Health Monitor';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    private state: HealthState = {
        utlxd: {
            name: 'UTLXD LSP',
            status: 'checking'
        },
        mcp: {
            name: 'MCP Server',
            status: 'checking'
        }
    };

    private pingInterval?: NodeJS.Timeout;

    constructor() {
        console.log('[HealthMonitorWidget] Constructor called');
        super();
        this.id = HealthMonitorWidget.ID;
        this.title.label = HealthMonitorWidget.LABEL;
        this.title.caption = 'Server Health Status';
        this.title.closable = false;
        this.addClass('utlx-health-monitor');
        console.log('[HealthMonitorWidget] Constructor completed, id:', this.id);
    }

    @postConstruct()
    protected init(): void {
        console.log('[HealthMonitorWidget] init() called');
        this.update();
        this.startHealthMonitoring();
        console.log('[HealthMonitorWidget] init() completed');
    }

    dispose(): void {
        console.log('[HealthMonitorWidget] dispose() called');
        this.stopHealthMonitoring();
        super.dispose();
    }

    protected render(): React.ReactNode {
        console.log('[HealthMonitorWidget] render() called');
        const { utlxd, mcp } = this.state;

        return (
            <div className='utlx-health-monitor-container'>
                <div className='health-status-row'>
                    <div className='health-item'>
                        {this.renderHealthStatus(utlxd)}
                    </div>
                    <div className='health-separator'>|</div>
                    <div className='health-item'>
                        {this.renderHealthStatus(mcp)}
                    </div>
                </div>
            </div>
        );
    }

    private renderHealthStatus(server: ServerHealth): React.ReactNode {
        const statusIcon = server.status === 'online' ? '🟢' :
                          server.status === 'offline' ? '🔴' : '🟡';

        return (
            <div className='health-status'>
                <span className='health-icon'>{statusIcon}</span>
                <span className='health-name'>{server.name}</span>
                {server.responseTime !== undefined && (
                    <span className='health-time'>{server.responseTime}ms</span>
                )}
                {server.error && (
                    <span className='health-error' title={server.error}>⚠️</span>
                )}
            </div>
        );
    }

    private startHealthMonitoring(): void {
        console.log('[HealthMonitorWidget] Starting health monitoring...');
        // Initial ping
        this.pingServers();

        // Ping every 2 seconds
        this.pingInterval = setInterval(() => {
            this.pingServers();
        }, 2000);
        console.log('[HealthMonitorWidget] Health monitoring started');
    }

    private stopHealthMonitoring(): void {
        console.log('[HealthMonitorWidget] Stopping health monitoring...');
        if (this.pingInterval) {
            clearInterval(this.pingInterval);
            this.pingInterval = undefined;
        }
    }

    private async pingServers(): Promise<void> {
        console.log('[HealthMonitorWidget] Pinging servers...');
        // Ping UTLXD LSP (socket-based ping)
        this.pingUTLXD();

        // Ping MCP (HTTP-based ping)
        this.pingMCP();
    }

    private async pingUTLXD(): Promise<void> {
        const startTime = Date.now();

        try {
            // Ping UTLXD API health endpoint
            const response = await fetch('http://localhost:7779/api/health');

            if (response.ok) {
                const responseTime = Date.now() - startTime;
                this.setState({
                    utlxd: {
                        name: 'UTLXD LSP',
                        status: 'online',
                        lastPing: Date.now(),
                        responseTime
                    }
                });
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.setState({
                utlxd: {
                    name: 'UTLXD LSP',
                    status: 'offline',
                    lastPing: Date.now(),
                    error: error instanceof Error ? error.message : 'Connection failed'
                }
            });
        }
    }

    private async pingMCP(): Promise<void> {
        const startTime = Date.now();

        try {
            const response = await fetch('http://localhost:3001/health');

            if (response.ok) {
                const responseTime = Date.now() - startTime;
                this.setState({
                    mcp: {
                        name: 'MCP Server',
                        status: 'online',
                        lastPing: Date.now(),
                        responseTime
                    }
                });
            } else {
                throw new Error(`HTTP ${response.status}`);
            }
        } catch (error) {
            this.setState({
                mcp: {
                    name: 'MCP Server',
                    status: 'offline',
                    lastPing: Date.now(),
                    error: error instanceof Error ? error.message : 'Connection failed'
                }
            });
        }
    }

    private setState(partial: Partial<HealthState>): void {
        this.state = { ...this.state, ...partial };
        this.update();
    }
}
