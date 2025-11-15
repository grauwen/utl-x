/**
 * UTLX Open Handler
 *
 * Prevents .utlx files from being opened in tabs.
 * Users should use the UTLX Transformation widget instead.
 */

import { injectable, inject } from 'inversify';
import URI from '@theia/core/lib/common/uri';
import { OpenHandler, OpenerOptions } from '@theia/core/lib/browser';
import { MaybePromise } from '@theia/core';
import { MessageService } from '@theia/core';

@injectable()
export class UTLXOpenHandler implements OpenHandler {
    readonly id = 'utlx-open-handler';
    readonly label = 'UTLX Open Handler';

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    canHandle(uri: URI): MaybePromise<number> {
        // Only handle .utlx files
        if (uri.path.ext === '.utlx') {
            // Return high priority to intercept before default editor
            return 1000;
        }
        return 0;
    }

    async open(uri: URI, options?: OpenerOptions): Promise<undefined> {
        // Show message instead of opening the file
        this.messageService.warn(
            'UTLX files cannot be opened directly. Please use the UTLX Transformation widget to edit UTLX code.'
        );
        return undefined;
    }
}
