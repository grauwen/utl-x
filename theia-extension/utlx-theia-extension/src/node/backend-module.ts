/**
 * Backend Module
 *
 * Registers backend services and bindings for the UTL-X Theia extension.
 * Now includes automatic service lifecycle management for UTLXD and MCP server.
 */

import { ContainerModule } from 'inversify';
import { ConnectionHandler, RpcConnectionHandler } from '@theia/core';
import { BackendApplicationContribution } from '@theia/core/lib/node';
import { UTLXService, UTLX_SERVICE_PATH, UTLX_SERVICE_SYMBOL } from '../common/protocol';
import { UTLXServiceImpl } from './services/utlx-service-impl';
import { UTLXDaemonClient } from './daemon/utlx-daemon-client';
import { ServiceLifecycleManager } from './services/service-lifecycle-manager';

// Import the service starter
import './services/auto-start-services';

export default new ContainerModule(bind => {
    console.log('[Backend Module] Loading UTL-X backend module...');

    // Bind daemon client as singleton
    bind(UTLXDaemonClient).toSelf().inSingletonScope();
    console.log('[Backend Module] Daemon client bound');

    // Bind service lifecycle manager directly as BackendApplicationContribution
    bind(BackendApplicationContribution).to(ServiceLifecycleManager).inSingletonScope();
    console.log('[Backend Module] Service lifecycle manager bound as BackendApplicationContribution');

    // Bind service implementation
    bind(UTLX_SERVICE_SYMBOL).to(UTLXServiceImpl).inSingletonScope();
    console.log('[Backend Module] Service implementation bound');

    // Create RPC connection handler for frontend-backend communication
    bind(ConnectionHandler).toDynamicValue(ctx =>
        new RpcConnectionHandler(UTLX_SERVICE_PATH, () => {
            return ctx.container.get<UTLXService>(UTLX_SERVICE_SYMBOL);
        })
    ).inSingletonScope();
    console.log('[Backend Module] RPC connection handler bound');

    console.log('[Backend Module] ✓ UTL-X backend module loaded with service lifecycle management');
});
