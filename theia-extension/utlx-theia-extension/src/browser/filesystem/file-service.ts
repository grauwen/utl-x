/**
 * File Service
 *
 * Handles file operations for loading and saving input/output files.
 */

import { injectable, inject } from 'inversify';
import { MessageService } from '@theia/core';
import { FileService } from '@theia/filesystem/lib/browser/file-service';
import { FileDialogService, OpenFileDialogProps, SaveFileDialogProps } from '@theia/filesystem/lib/browser';
import URI from '@theia/core/lib/common/uri';

export interface FileContent {
    uri: URI;
    content: string;
    name: string;
}

@injectable()
export class UTLXFileService {

    @inject(FileService)
    protected readonly fileService!: FileService;

    @inject(FileDialogService)
    protected readonly fileDialog!: FileDialogService;

    @inject(MessageService)
    protected readonly messageService!: MessageService;

    /**
     * Open file dialog and load content
     */
    async openFile(props?: Partial<OpenFileDialogProps>): Promise<FileContent | undefined> {
        try {
            const uri = await this.fileDialog.showOpenDialog({
                title: 'Open File',
                canSelectMany: false,
                ...props
            });

            if (!uri) {
                return undefined;
            }

            const content = await this.readFile(uri);
            return {
                uri,
                content,
                name: uri.path.base
            };
        } catch (error) {
            this.messageService.error(`Failed to open file: ${error}`);
            throw error;
        }
    }

    /**
     * Read file content from URI
     */
    async readFile(uri: URI): Promise<string> {
        try {
            const fileResource = await this.fileService.read(uri);
            return fileResource.value;
        } catch (error) {
            this.messageService.error(`Failed to read file ${uri.path.base}: ${error}`);
            throw error;
        }
    }

    /**
     * Save file dialog and write content
     */
    async saveFile(content: string, props?: Partial<SaveFileDialogProps>): Promise<URI | undefined> {
        try {
            const uri = await this.fileDialog.showSaveDialog({
                title: 'Save File',
                ...props
            });

            if (!uri) {
                return undefined;
            }

            await this.writeFile(uri, content);
            this.messageService.info(`File saved: ${uri.path.base}`);
            return uri;
        } catch (error) {
            this.messageService.error(`Failed to save file: ${error}`);
            throw error;
        }
    }

    /**
     * Write content to file
     */
    async writeFile(uri: URI, content: string): Promise<void> {
        try {
            const encoder = new TextEncoder();
            const data = encoder.encode(content);

            await this.fileService.write(uri, data);
        } catch (error) {
            this.messageService.error(`Failed to write file ${uri.path.base}: ${error}`);
            throw error;
        }
    }

    /**
     * Check if file exists
     */
    async exists(uri: URI): Promise<boolean> {
        try {
            const stat = await this.fileService.exists(uri);
            return stat;
        } catch {
            return false;
        }
    }

    /**
     * Get file extension
     */
    getExtension(uri: URI): string {
        return uri.path.ext;
    }

    /**
     * Detect format from file extension
     */
    detectFormat(uri: URI): string {
        const ext = this.getExtension(uri).toLowerCase();
        switch (ext) {
            case '.xml':
                return 'xml';
            case '.json':
                return 'json';
            case '.yaml':
            case '.yml':
                return 'yaml';
            case '.csv':
                return 'csv';
            case '.xsd':
                return 'xsd';
            case '.avsc':
                return 'avro-schema';
            case '.proto':
                return 'protobuf';
            default:
                return 'auto';
        }
    }

    /**
     * Load multiple files
     */
    async openMultipleFiles(props?: Partial<OpenFileDialogProps>): Promise<FileContent[]> {
        try {
            const uris = await this.fileDialog.showOpenDialog({
                title: 'Open Files',
                canSelectMany: true,
                ...props
            });

            if (!uris || uris.length === 0) {
                return [];
            }

            const files: FileContent[] = [];
            for (const uri of uris) {
                try {
                    const content = await this.readFile(uri);
                    files.push({
                        uri,
                        content,
                        name: uri.path.base
                    });
                } catch (error) {
                    console.error(`Failed to read ${uri.path.base}:`, error);
                }
            }

            return files;
        } catch (error) {
            this.messageService.error(`Failed to open files: ${error}`);
            throw error;
        }
    }
}
