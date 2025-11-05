/**
 * Frontend Module
 *
 * Registers frontend widgets, contributions, and bindings for the UTL-X Theia extension.
 */

import { ContainerModule } from 'inversify';
import { WebSocketConnectionProvider, FrontendApplicationContribution, WidgetFactory } from '@theia/core/lib/browser';
import { UTLXService, UTLX_SERVICE_PATH, UTLX_SERVICE_SYMBOL } from '../common/protocol';
import { InputPanelWidget } from './input-panel/input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { ModeSelectorWidget } from './mode-selector/mode-selector-widget';
import { UTLXWorkbenchWidget } from './workbench/utlx-workbench-widget';
import { UTLXFrontendContribution } from './utlx-frontend-contribution';

export default new ContainerModule(bind => {
    // Bind service proxy for RPC communication with backend
    bind(UTLX_SERVICE_SYMBOL).toDynamicValue(ctx => {
        const connection = ctx.container.get(WebSocketConnectionProvider);
        return connection.createProxy<UTLXService>(UTLX_SERVICE_PATH);
    }).inSingletonScope();

    // Bind widgets
    bind(InputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: InputPanelWidget.ID,
        createWidget: () => ctx.container.get<InputPanelWidget>(InputPanelWidget)
    })).inSingletonScope();

    bind(OutputPanelWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: OutputPanelWidget.ID,
        createWidget: () => ctx.container.get<OutputPanelWidget>(OutputPanelWidget)
    })).inSingletonScope();

    bind(ModeSelectorWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: ModeSelectorWidget.ID,
        createWidget: () => ctx.container.get<ModeSelectorWidget>(ModeSelectorWidget)
    })).inSingletonScope();

    bind(UTLXWorkbenchWidget).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: UTLXWorkbenchWidget.ID,
        createWidget: () => ctx.container.get<UTLXWorkbenchWidget>(UTLXWorkbenchWidget)
    })).inSingletonScope();

    // Bind frontend contribution
    bind(FrontendApplicationContribution).to(UTLXFrontendContribution).inSingletonScope();

    console.log('UTL-X frontend module loaded');
});
