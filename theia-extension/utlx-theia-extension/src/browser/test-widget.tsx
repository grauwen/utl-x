import * as React from 'react';
import { injectable, postConstruct } from 'inversify';
import { ReactWidget } from '@theia/core/lib/browser/widgets/react-widget';

@injectable()
export class TestWidget extends ReactWidget {
    static readonly ID = 'test-widget';
    static readonly LABEL = 'Test';

    constructor() {
        console.log('[TestWidget] Constructor called');
        super();
        this.id = TestWidget.ID;
        this.title.label = TestWidget.LABEL;
        this.title.closable = false;
        console.log('[TestWidget] Constructor completed, id:', this.id);
    }

    @postConstruct()
    protected init(): void {
        console.log('[TestWidget] init() called');
        this.update();
        console.log('[TestWidget] init() completed');
    }

    protected render(): React.ReactNode {
        console.log('[TestWidget] render() called');
        return (
            <div style={{ padding: '20px', background: '#1e1e1e', color: '#fff', height: '100%' }}>
                <h2>Test Widget Works!</h2>
                <p>If you see this, the widget system is functional.</p>
            </div>
        );
    }
}
