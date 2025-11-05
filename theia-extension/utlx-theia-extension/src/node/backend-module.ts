/**
 * Backend Module
 *
 * Registers backend services and bindings for the UTL-X Theia extension.
 */

import { ContainerModule } from 'inversify';
import { ConnectionHandler, RpcConnectionHandler } from '@theia/core';
import { UTLXService, UTLX_SERVICE_PATH, UTLX_SERVICE_SYMBOL } from '../common/protocol';
import { UTLXServiceImpl } from './services/utlx-service-impl';
import { UTLXDaemonClient } from './daemon/utlx-daemon-client';

export default new ContainerModule(bind => {
    // Bind daemon client as singleton
    bind(UTLXDaemonClient).toSelf().inSingletonScope();

    // Bind service implementation
    bind(UTLX_SERVICE_SYMBOL).to(UTLXServiceImpl).inSingletonScope();

    // Create RPC connection handler for frontend-backend communication
    bind(ConnectionHandler).toDynamicValue(ctx =>
        new RpcConnectionHandler(UTLX_SERVICE_PATH, () => {
            return ctx.container.get<UTLXService>(UTLX_SERVICE_SYMBOL);
        })
    ).inSingletonScope();

    console.log('UTL-X backend module loaded');
});
