/**
 * Complete Frontend Module
 *
 * Registers all frontend widgets, contributions, language support, and bindings.
 */

import { ContainerModule } from 'inversify';
import {
    WebSocketConnectionProvider,
    FrontendApplicationContribution,
    WidgetFactory,
    CommandContribution,
    MenuContribution,
    KeybindingContribution
} from '@theia/core/lib/browser';
import { LanguageGrammarDefinitionContribution } from '@theia/monaco/lib/browser/textmate';
import { LanguageClientContribution } from '@theia/languages/lib/browser';

import { UTLXService, UTLX_SERVICE_PATH } from '../common/protocol';
import { InputPanelWidgetEnhanced } from './input-panel/input-panel-widget-enhanced';
import { OutputPanelWidget } from './output-panel/output-panel-widget';
import { ModeSelectorWidget } from './mode-selector/mode-selector-widget';
import { UTLXWorkbenchWidget } from './workbench/utlx-workbench-widget';
import { UTLXFrontendContribution } from './utlx-frontend-contribution';
import { UTLXLanguageContribution } from './language/utlx-language-contribution';
import { UTLXLanguageClientContribution } from './language/utlx-language-client-contribution';
import { UTLXFileService } from './filesystem/file-service';

export default new ContainerModule(bind => {
    // Bind service proxy for RPC communication with backend
    bind(UTLXService).toDynamicValue(ctx => {
        const connection = ctx.container.get(WebSocketConnectionProvider);
        return connection.createProxy<UTLXService>(UTLX_SERVICE_PATH);
    }).inSingletonScope();

    // Bind file service
    bind(UTLXFileService).toSelf().inSingletonScope();

    // Bind widgets with enhanced versions
    bind(InputPanelWidgetEnhanced).toSelf();
    bind(WidgetFactory).toDynamicValue(ctx => ({
        id: InputPanelWidgetEnhanced.ID,
        createWidget: () => ctx.container.get<InputPanelWidgetEnhanced>(InputPanelWidgetEnhanced)
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

    // Bind language contributions
    bind(UTLXLanguageContribution).toSelf().inSingletonScope();
    bind(LanguageGrammarDefinitionContribution).toService(UTLXLanguageContribution);

    bind(UTLXLanguageClientContribution).toSelf().inSingletonScope();
    bind(LanguageClientContribution).toService(UTLXLanguageClientContribution);

    // Bind frontend contribution (commands, menus, keybindings)
    bind(UTLXFrontendContribution).toSelf().inSingletonScope();
    bind(FrontendApplicationContribution).toService(UTLXFrontendContribution);
    bind(CommandContribution).toService(UTLXFrontendContribution);
    bind(MenuContribution).toService(UTLXFrontendContribution);
    bind(KeybindingContribution).toService(UTLXFrontendContribution);

    console.log('UTL-X frontend module loaded (complete)');
});
