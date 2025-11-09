/**
 * Frontend Module
 *
 * Registers frontend widgets, contributions, and bindings for the UTL-X Theia extension.
 */

import './style/index.css';
import { ContainerModule } from 'inversify';
import { WebSocketConnectionProvider, FrontendApplicationContribution, WidgetFactory } from '@theia/core/lib/browser';
import { UTLXService, UTLX_SERVICE_PATH, UTLX_SERVICE_SYMBOL } from '../common/protocol';
import { MultiInputPanelWidget } from './input-panel/multi-input-panel-widget';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { UTLXEditorWidget } from './editor/utlx-editor-widget';
import { HealthMonitorWidget } from './health-monitor/health-monitor-widget';
import { UTLXToolbarWidget } from './toolbar/utlx-toolbar-widget';
import { UTLXFrontendContribution } from './utlx-frontend-contribution';
import { UTLXEventService } from './events/utlx-event-service';

export default new ContainerModule(bind => {
    console.log('[UTLX Frontend Module] ===== LOADING STARTED =====');

    try {
        // Bind service proxy for RPC communication with backend
        console.log('[UTLX Frontend Module] Binding UTLX service proxy...');
        bind(UTLX_SERVICE_SYMBOL).toDynamicValue(ctx => {
            console.log('[UTLX Frontend Module] Creating UTLX service proxy...');
            const connection = ctx.container.get(WebSocketConnectionProvider);
            return connection.createProxy<UTLXService>(UTLX_SERVICE_PATH);
        }).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ UTLX service proxy bound');

        // Bind event service for widget communication
        console.log('[UTLX Frontend Module] Binding UTLXEventService...');
        bind(UTLXEventService).toSelf().inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ UTLXEventService bound');

        // Bind widgets
        console.log('[UTLX Frontend Module] Binding MultiInputPanelWidget...');
        bind(MultiInputPanelWidget).toSelf();
        bind(WidgetFactory).toDynamicValue(ctx => ({
            id: MultiInputPanelWidget.ID,
            createWidget: () => {
                console.log('[UTLX Frontend Module] Creating MultiInputPanelWidget instance...');
                try {
                    const widget = ctx.container.get<MultiInputPanelWidget>(MultiInputPanelWidget);
                    console.log('[UTLX Frontend Module] ✓ MultiInputPanelWidget created successfully');
                    return widget;
                } catch (error) {
                    console.error('[UTLX Frontend Module] ✗ Failed to create MultiInputPanelWidget:', error);
                    throw error;
                }
            }
        })).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ MultiInputPanelWidget bound');

        console.log('[UTLX Frontend Module] Binding OutputPanelWidget...');
        bind(OutputPanelWidget).toSelf();
        bind(WidgetFactory).toDynamicValue(ctx => ({
            id: OutputPanelWidget.ID,
            createWidget: () => {
                console.log('[UTLX Frontend Module] Creating OutputPanelWidget instance...');
                try {
                    const widget = ctx.container.get<OutputPanelWidget>(OutputPanelWidget);
                    console.log('[UTLX Frontend Module] ✓ OutputPanelWidget created successfully');
                    return widget;
                } catch (error) {
                    console.error('[UTLX Frontend Module] ✗ Failed to create OutputPanelWidget:', error);
                    throw error;
                }
            }
        })).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ OutputPanelWidget bound');

        console.log('[UTLX Frontend Module] Binding UTLXEditorWidget...');
        bind(UTLXEditorWidget).toSelf().inSingletonScope();
        bind(WidgetFactory).toDynamicValue(ctx => ({
            id: UTLXEditorWidget.ID,
            createWidget: () => {
                console.log('[UTLX Frontend Module] Creating UTLXEditorWidget instance...');
                try {
                    const widget = ctx.container.get<UTLXEditorWidget>(UTLXEditorWidget);
                    console.log('[UTLX Frontend Module] ✓ UTLXEditorWidget created successfully');
                    return widget;
                } catch (error) {
                    console.error('[UTLX Frontend Module] ✗ Failed to create UTLXEditorWidget:', error);
                    throw error;
                }
            }
        })).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ UTLXEditorWidget bound');

        console.log('[UTLX Frontend Module] Binding HealthMonitorWidget...');
        bind(HealthMonitorWidget).toSelf();
        bind(WidgetFactory).toDynamicValue(ctx => ({
            id: HealthMonitorWidget.ID,
            createWidget: () => {
                console.log('[UTLX Frontend Module] Creating HealthMonitorWidget instance...');
                try {
                    const widget = ctx.container.get<HealthMonitorWidget>(HealthMonitorWidget);
                    console.log('[UTLX Frontend Module] ✓ HealthMonitorWidget created successfully');
                    return widget;
                } catch (error) {
                    console.error('[UTLX Frontend Module] ✗ Failed to create HealthMonitorWidget:', error);
                    throw error;
                }
            }
        })).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ HealthMonitorWidget bound');

        console.log('[UTLX Frontend Module] Binding UTLXToolbarWidget...');
        bind(UTLXToolbarWidget).toSelf();
        bind(WidgetFactory).toDynamicValue(ctx => ({
            id: UTLXToolbarWidget.ID,
            createWidget: () => {
                console.log('[UTLX Frontend Module] Creating UTLXToolbarWidget instance...');
                try {
                    const widget = ctx.container.get<UTLXToolbarWidget>(UTLXToolbarWidget);
                    console.log('[UTLX Frontend Module] ✓ UTLXToolbarWidget created successfully');
                    return widget;
                } catch (error) {
                    console.error('[UTLX Frontend Module] ✗ Failed to create UTLXToolbarWidget:', error);
                    throw error;
                }
            }
        })).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ UTLXToolbarWidget bound');

        // Bind frontend contribution
        console.log('[UTLX Frontend Module] Binding frontend contribution...');
        bind(FrontendApplicationContribution).to(UTLXFrontendContribution).inSingletonScope();
        console.log('[UTLX Frontend Module] ✓ Frontend contribution bound');

        console.log('[UTLX Frontend Module] ===== ALL BINDINGS SUCCESSFUL =====');
    } catch (error) {
        console.error('[UTLX Frontend Module] ===== BINDING FAILED =====', error);
        throw error;
    }
});
