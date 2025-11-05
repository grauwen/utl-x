/**
 * UTL-X Language Contribution
 *
 * Registers UTL-X as a language in Monaco editor with syntax highlighting and basic features.
 */

import { injectable } from 'inversify';
import { LanguageGrammarDefinitionContribution, TextmateRegistry } from '@theia/monaco/lib/browser/textmate';
import { MonacoLanguages } from '@theia/monaco/lib/browser/monaco-languages';

@injectable()
export class UTLXLanguageContribution implements LanguageGrammarDefinitionContribution {

    readonly id = 'utlx';
    readonly scopeName = 'source.utlx';

    registerTextmateLanguage(registry: TextmateRegistry): void {
        monaco.languages.register({
            id: this.id,
            extensions: ['.utlx'],
            aliases: ['UTL-X', 'utlx'],
            mimetypes: ['text/x-utlx']
        });

        // Configure language features
        monaco.languages.setLanguageConfiguration(this.id, {
            comments: {
                lineComment: '//',
                blockComment: ['/*', '*/']
            },
            brackets: [
                ['{', '}'],
                ['[', ']'],
                ['(', ')']
            ],
            autoClosingPairs: [
                { open: '{', close: '}' },
                { open: '[', close: ']' },
                { open: '(', close: ')' },
                { open: '"', close: '"' },
                { open: "'", close: "'" }
            ],
            surroundingPairs: [
                { open: '{', close: '}' },
                { open: '[', close: ']' },
                { open: '(', close: ')' },
                { open: '"', close: '"' },
                { open: "'", close: "'" }
            ],
            folding: {
                markers: {
                    start: /^\s*\/\/#region/,
                    end: /^\s*\/\/#endregion/
                }
            }
        });

        // Register syntax highlighting
        monaco.languages.setMonarchTokensProvider(this.id, {
            defaultToken: '',
            tokenPostfix: '.utlx',

            keywords: [
                'function', 'let', 'if', 'else', 'match', 'case',
                'true', 'false', 'null', 'now'
            ],

            operators: [
                '=', '>', '<', '!', '~', '?', ':',
                '==', '<=', '>=', '!=', '&&', '||',
                '+', '-', '*', '/', '%',
                '|>', '=>', '@', '$', '..'
            ],

            // Regular expression patterns
            symbols: /[=><!~?:&|+\-*\/\^%]+/,
            escapes: /\\(?:[abfnrtv\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

            tokenizer: {
                root: [
                    // Header directives
                    [/%utlx\s+\d+\.\d+/, 'keyword.directive'],
                    [/^(input|output)\s+(xml|json|yaml|csv|xsd|jsch|avsc|proto|auto)/, 'keyword.directive'],

                    // Separator
                    [/^---$/, 'keyword.separator'],

                    // Identifiers and keywords
                    [/[a-z_$][\w$]*/, {
                        cases: {
                            '@keywords': 'keyword',
                            '@default': 'identifier'
                        }
                    }],

                    // User-defined functions (PascalCase)
                    [/[A-Z][\w$]*/, 'type.identifier'],

                    // Whitespace
                    { include: '@whitespace' },

                    // Delimiters and operators
                    [/[{}()\[\]]/, '@brackets'],
                    [/[<>](?!@symbols)/, '@brackets'],
                    [/@symbols/, {
                        cases: {
                            '@operators': 'operator',
                            '@default': ''
                        }
                    }],

                    // Numbers
                    [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
                    [/0[xX][0-9a-fA-F]+/, 'number.hex'],
                    [/\d+/, 'number'],

                    // Delimiter: after number because of .\d floats
                    [/[;,.]/, 'delimiter'],

                    // Strings
                    [/"([^"\\]|\\.)*$/, 'string.invalid'],  // non-terminated string
                    [/"/, 'string', '@string_double'],
                    [/'([^'\\]|\\.)*$/, 'string.invalid'],  // non-terminated string
                    [/'/, 'string', '@string_single'],

                    // Variables and paths
                    [/\$[a-zA-Z_][\w]*/, 'variable'],
                    [/@[a-zA-Z_][\w]*/, 'variable.attribute']
                ],

                whitespace: [
                    [/[ \t\r\n]+/, ''],
                    [/\/\*/, 'comment', '@comment'],
                    [/\/\/.*$/, 'comment']
                ],

                comment: [
                    [/[^\/*]+/, 'comment'],
                    [/\*\//, 'comment', '@pop'],
                    [/[\/*]/, 'comment']
                ],

                string_double: [
                    [/[^\\"]+/, 'string'],
                    [/@escapes/, 'string.escape'],
                    [/\\./, 'string.escape.invalid'],
                    [/"/, 'string', '@pop']
                ],

                string_single: [
                    [/[^\\']+/, 'string'],
                    [/@escapes/, 'string.escape'],
                    [/\\./, 'string.escape.invalid'],
                    [/'/, 'string', '@pop']
                ]
            }
        });
    }
}
