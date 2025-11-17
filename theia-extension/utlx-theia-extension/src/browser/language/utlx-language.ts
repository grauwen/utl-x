import { injectable } from '@theia/core/shared/inversify';
import { LanguageGrammarDefinitionContribution, TextmateRegistry } from '@theia/monaco/lib/browser/textmate';
import * as monaco from '@theia/monaco-editor-core';

export const UTLX_LANGUAGE_ID = 'utlx';
export const UTLX_LANGUAGE_NAME = 'UTL-X';

@injectable()
export class UTLXLanguageGrammarContribution implements LanguageGrammarDefinitionContribution {

    registerTextmateLanguage(registry: TextmateRegistry): void {
        monaco.languages.register({
            id: UTLX_LANGUAGE_ID,
            extensions: ['.utlx', '.UTLX'],
            aliases: [UTLX_LANGUAGE_NAME, 'utlx'],
            mimetypes: ['text/x-utlx']
        });

        // Basic configuration for Monaco editor
        monaco.languages.setLanguageConfiguration(UTLX_LANGUAGE_ID, {
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
                    start: new RegExp('^\\s*//\\s*#?region\\b'),
                    end: new RegExp('^\\s*//\\s*#?endregion\\b')
                }
            },
            wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\-\=\+\[\{\]\}\\\|\;\:\'\"\,\.\<\>\/\?\s]+)/g
        });

        // Register a basic tokenization provider for syntax highlighting
        monaco.languages.setMonarchTokensProvider(UTLX_LANGUAGE_ID, {
            keywords: [
                'let', 'def', 'function', 'if', 'then', 'else', 'match', 'try', 'catch',
                'for', 'in', 'return', 'true', 'false', 'null'
            ],

            // Data format keywords
            formats: [
                'json', 'xml', 'yaml', 'csv', 'avro', 'proto', 'xsd', 'jsch'
            ],

            // Only actual UTLX operators
            operators: [
                '==', '!=', '<=', '>=', '<', '>',  // Comparison operators
                '&&', '||', '!',                    // Logical operators
                '+', '-', '*', '/', '%',            // Arithmetic operators
                '?:'                                 // Ternary operator (as a pair)
            ],

            symbols: /[=><!?:+\-*\/%]+/,
            escapes: /\\(?:[abfnrtv\\\"']|x[0-9A-Fa-f]{1,4}|u[0-9A-Fa-f]{4}|U[0-9A-Fa-f]{8})/,

            tokenizer: {
                root: [
                    // UTLX separator - transition to code section
                    [/^---\s*$/, { token: 'keyword.utlx-separator', next: '@code' }],

                    // Everything else in header is blue (keyword color)
                    // Except format keywords which are purple
                    [/\b(json|xml|yaml|csv|avro|proto|xsd|jsch)\b/, 'type'],

                    // Version numbers
                    [/\d+\.\d+/, 'number'],

                    // Strings in options
                    [/"([^"\\]|\\.)*"/, 'string'],
                    [/'([^'\\]|\\.)*'/, 'string'],

                    // Everything else (input, output, names, etc.) is keyword color (blue)
                    // Allow hyphens in identifiers: [\w$-]* instead of [\w$]*
                    [/[a-zA-Z_$%][\w$-]*/, 'keyword'],

                    // Whitespace and punctuation
                    { include: '@whitespace' },
                    [/[{}()\[\]:,]/, 'delimiter']
                ],

                code: [
                    // Input references - allow hyphens in input names: $input-name
                    [/\$[\w-]+/, 'variable.input'],

                    // Identifiers and keywords - allow hyphens
                    [/[a-z_$][\w$-]*/, {
                        cases: {
                            '@keywords': 'keyword',
                            '@default': 'identifier'
                        }
                    }],

                    // Whitespace
                    { include: '@whitespace' },

                    // Numbers
                    [/\d*\.\d+([eE][\-+]?\d+)?/, 'number.float'],
                    [/0[xX][0-9a-fA-F]+/, 'number.hex'],
                    [/\d+/, 'number'],

                    // Strings
                    [/"([^"\\]|\\.)*$/, 'string.invalid'],
                    [/"/, 'string', '@string_double'],
                    [/'([^'\\]|\\.)*$/, 'string.invalid'],
                    [/'/, 'string', '@string_single'],

                    // Operators and symbols
                    [/@symbols/, {
                        cases: {
                            '@operators': 'operator',
                            '@default': ''
                        }
                    }],

                    // Delimiters
                    [/[{}\(\)\[\]]/, '@brackets'],
                    [/[;,.]/, 'delimiter']
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
