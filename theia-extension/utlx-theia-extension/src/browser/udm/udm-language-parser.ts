/**
 * UDM Language Parser
 *
 * Simple recursive descent parser for UDM Language (.udm files)
 * Ported from modules/core/src/main/kotlin/org/apache/utlx/core/udm/UDMLanguageParser.kt
 *
 * Parses .udm format back into UDM objects, enabling round-trip serialization:
 * UDM → .udm (via UDMLanguageSerializer) → UDM (via this parser)
 *
 * This is a hand-written parser (not using ANTLR) for simplicity, better error messages,
 * and smaller bundle size (~10KB vs ~250KB for ANTLR4).
 *
 * Usage:
 * ```typescript
 * const udmString = await fs.readFile('example.udm', 'utf-8');
 * const udm = UDMLanguageParser.parse(udmString);
 * ```
 */

import { UDM, UDMFactory } from './udm-core';

/**
 * Exception thrown when UDM parsing fails
 */
export class UDMParseException extends Error {
    constructor(message: string, public readonly line?: number, public readonly col?: number) {
        super(message);
        this.name = 'UDMParseException';
    }
}

/**
 * Token types for UDM Language lexer
 */
enum TokenType {
    STRING,
    NUMBER,
    BOOLEAN,
    NULL,
    IDENTIFIER,
    LEFT_BRACE,      // {
    RIGHT_BRACE,     // }
    LEFT_BRACKET,    // [
    RIGHT_BRACKET,   // ]
    LEFT_PAREN,      // (
    RIGHT_PAREN,     // )
    COLON,           // :
    COMMA,           // ,
    LEFT_ANGLE,      // <
    RIGHT_ANGLE,     // >
    EOF
}

/**
 * Token with type, value, and position
 */
interface Token {
    type: TokenType;
    value: string;
    line: number;
    col: number;
}

/**
 * UDM Language Parser
 */
export class UDMLanguageParser {
    /**
     * Parse a .udm format string into a UDM object
     *
     * @param input The .udm format string
     * @returns The parsed UDM object
     * @throws UDMParseException if parsing fails
     */
    static parse(input: string): UDM {
        const tokens = this.tokenize(input);
        const parser = new Parser(tokens);
        return parser.parseFile();
    }

    /**
     * Tokenize the input string
     */
    private static tokenize(input: string): Token[] {
        const tokens: Token[] = [];
        let pos = 0;
        let line = 1;
        let col = 1;

        const peek = (offset: number = 0): string | null =>
            pos + offset < input.length ? input[pos + offset] : null;

        const advance = (): string => {
            const ch = input[pos];
            pos++;
            if (ch === '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            return ch;
        };

        while (pos < input.length) {
            const ch = peek()!;

            // Whitespace
            if (/\s/.test(ch)) {
                advance();
                continue;
            }

            // Comments
            if (ch === '/' && peek(1) === '/') {
                while (peek() !== null && peek() !== '\n') advance();
                continue;
            }

            // Strings
            if (ch === '"') {
                advance(); // skip opening "
                const sb: string[] = [];
                while (peek() !== '"') {
                    if (peek() === null) {
                        throw new UDMParseException(`Unterminated string at line ${line}:${col}`, line, col);
                    }
                    if (peek() === '\\') {
                        advance();
                        const escaped = peek();
                        switch (escaped) {
                            case 'n': advance(); sb.push('\n'); break;
                            case 'r': advance(); sb.push('\r'); break;
                            case 't': advance(); sb.push('\t'); break;
                            case '\\': advance(); sb.push('\\'); break;
                            case '"': advance(); sb.push('"'); break;
                            default:
                                throw new UDMParseException(`Invalid escape sequence at line ${line}:${col}`, line, col);
                        }
                    } else {
                        sb.push(advance());
                    }
                }
                advance(); // skip closing "
                tokens.push({ type: TokenType.STRING, value: sb.join(''), line, col });
                continue;
            }

            // Numbers
            if (/\d/.test(ch) || (ch === '-' && peek(1) !== null && /\d/.test(peek(1)!))) {
                const sb: string[] = [];
                if (ch === '-') sb.push(advance());
                while (peek() !== null && /\d/.test(peek()!)) sb.push(advance());
                if (peek() === '.') {
                    sb.push(advance());
                    while (peek() !== null && /\d/.test(peek()!)) sb.push(advance());
                }
                if (peek() === 'e' || peek() === 'E') {
                    sb.push(advance());
                    if (peek() === '+' || peek() === '-') sb.push(advance());
                    while (peek() !== null && /\d/.test(peek()!)) sb.push(advance());
                }
                tokens.push({ type: TokenType.NUMBER, value: sb.join(''), line, col });
                continue;
            }

            // Identifiers and keywords
            if (/[a-zA-Z@_]/.test(ch)) {
                const sb: string[] = [];
                while (peek() !== null && /[a-zA-Z0-9_@-]/.test(peek()!)) {
                    sb.push(advance());
                }
                const text = sb.join('');
                let type: TokenType;
                if (text === 'true' || text === 'false') {
                    type = TokenType.BOOLEAN;
                } else if (text === 'null') {
                    type = TokenType.NULL;
                } else {
                    type = TokenType.IDENTIFIER;
                }
                tokens.push({ type, value: text, line, col });
                continue;
            }

            // Punctuation
            switch (ch) {
                case '{': advance(); tokens.push({ type: TokenType.LEFT_BRACE, value: '{', line, col }); continue;
                case '}': advance(); tokens.push({ type: TokenType.RIGHT_BRACE, value: '}', line, col }); continue;
                case '[': advance(); tokens.push({ type: TokenType.LEFT_BRACKET, value: '[', line, col }); continue;
                case ']': advance(); tokens.push({ type: TokenType.RIGHT_BRACKET, value: ']', line, col }); continue;
                case '(': advance(); tokens.push({ type: TokenType.LEFT_PAREN, value: '(', line, col }); continue;
                case ')': advance(); tokens.push({ type: TokenType.RIGHT_PAREN, value: ')', line, col }); continue;
                case ':': advance(); tokens.push({ type: TokenType.COLON, value: ':', line, col }); continue;
                case ',': advance(); tokens.push({ type: TokenType.COMMA, value: ',', line, col }); continue;
                case '<': advance(); tokens.push({ type: TokenType.LEFT_ANGLE, value: '<', line, col }); continue;
                case '>': advance(); tokens.push({ type: TokenType.RIGHT_ANGLE, value: '>', line, col }); continue;
                default:
                    throw new UDMParseException(`Unexpected character '${ch}' at line ${line}:${col}`, line, col);
            }
        }

        tokens.push({ type: TokenType.EOF, value: '', line, col });
        return tokens;
    }
}

/**
 * Recursive descent parser for UDM tokens
 */
class Parser {
    private pos = 0;

    constructor(private readonly tokens: Token[]) {}

    private peek(offset: number = 0): Token {
        if (this.pos + offset < this.tokens.length) {
            return this.tokens[this.pos + offset];
        }
        return this.tokens[this.tokens.length - 1]; // EOF
    }

    private advance(): Token {
        return this.tokens[this.pos++];
    }

    private expect(type: TokenType): Token {
        const token = this.peek();
        if (token.type !== type) {
            throw new UDMParseException(
                `Expected ${TokenType[type]} but found ${TokenType[token.type]} at line ${token.line}:${token.col}`,
                token.line,
                token.col
            );
        }
        return this.advance();
    }

    parseFile(): UDM {
        // Parse header - skip all @ directives at file level
        while (this.peek().type === TokenType.IDENTIFIER && this.peek().value.startsWith('@')) {
            const directive = this.peek().value;
            // Skip header directives: @udm-version, @source, @parsed-at, etc.
            if (directive.startsWith('@udm') ||
                directive.startsWith('@source') ||
                directive.startsWith('@parsed')) {
                this.advance(); // skip directive
                if (this.peek().type === TokenType.COLON) {
                    this.advance(); // skip colon
                    this.advance(); // skip value
                }
            } else {
                // Not a header directive, break and parse as value
                break;
            }
        }

        // Parse main value
        return this.parseValue();
    }

    private parseValue(): UDM {
        const token = this.peek();

        switch (token.type) {
            case TokenType.STRING:
                return UDMFactory.scalar(this.advance().value);

            case TokenType.NUMBER: {
                const text = this.advance().value;
                const value = text.includes('.') ? parseFloat(text) : parseInt(text, 10);
                return UDMFactory.scalar(value);
            }

            case TokenType.BOOLEAN:
                return UDMFactory.scalar(this.advance().value === 'true');

            case TokenType.NULL:
                this.advance();
                return UDMFactory.scalar(null);

            case TokenType.LEFT_BRACKET:
                return this.parseArray();

            case TokenType.LEFT_BRACE:
                return this.parseObject();

            case TokenType.IDENTIFIER:
                if (token.value.startsWith('@DateTime')) return this.parseDateTime();
                if (token.value.startsWith('@Date')) return this.parseDate();
                if (token.value.startsWith('@LocalDateTime')) return this.parseLocalDateTime();
                if (token.value.startsWith('@Time')) return this.parseTime();
                if (token.value.startsWith('@Binary')) return this.parseBinary();
                if (token.value.startsWith('@Lambda')) return this.parseLambda();
                if (token.value.startsWith('@Object')) return this.parseAnnotatedObject();
                if (token.value.startsWith('@Scalar')) return this.parseAnnotatedScalar();
                throw new UDMParseException(
                    `Unexpected identifier: ${token.value} at line ${token.line}:${token.col}`,
                    token.line,
                    token.col
                );

            default:
                throw new UDMParseException(
                    `Unexpected token: ${TokenType[token.type]} at line ${token.line}:${token.col}`,
                    token.line,
                    token.col
                );
        }
    }

    private parseArray(): UDM {
        this.expect(TokenType.LEFT_BRACKET);
        const elements: UDM[] = [];

        while (this.peek().type !== TokenType.RIGHT_BRACKET) {
            elements.push(this.parseValue());
            if (this.peek().type === TokenType.COMMA) this.advance();
        }

        this.expect(TokenType.RIGHT_BRACKET);
        return UDMFactory.array(elements);
    }

    private parseObject(): UDM {
        this.expect(TokenType.LEFT_BRACE);
        const properties = new Map<string, UDM>();

        // Check if this has attributes/properties sections
        let hasAttributesSection = false;
        let hasPropertiesSection = false;
        let lookAheadPos = this.pos;

        while (lookAheadPos < this.tokens.length && this.tokens[lookAheadPos].type !== TokenType.RIGHT_BRACE) {
            if (this.tokens[lookAheadPos].value === 'attributes') hasAttributesSection = true;
            if (this.tokens[lookAheadPos].value === 'properties') hasPropertiesSection = true;
            lookAheadPos++;
        }

        if (hasAttributesSection || hasPropertiesSection) {
            // Parse structured object
            const attributes = new Map<string, string>();

            if (this.peek().value === 'attributes') {
                this.advance(); // skip "attributes"
                this.expect(TokenType.COLON);
                this.expect(TokenType.LEFT_BRACE);

                while (this.peek().type !== TokenType.RIGHT_BRACE) {
                    // Accept both IDENTIFIER and STRING as property keys
                    const key = this.peek().type === TokenType.STRING
                        ? this.advance().value
                        : this.expect(TokenType.IDENTIFIER).value;
                    this.expect(TokenType.COLON);

                    const valueToken = this.peek();
                    let value: string;
                    if (valueToken.type === TokenType.STRING ||
                        valueToken.type === TokenType.NUMBER ||
                        valueToken.type === TokenType.BOOLEAN) {
                        value = this.advance().value;
                    } else {
                        value = 'null';
                    }
                    attributes.set(key, value);
                    if (this.peek().type === TokenType.COMMA) this.advance();
                }

                this.expect(TokenType.RIGHT_BRACE);
                if (this.peek().type === TokenType.COMMA) this.advance();
            }

            if (this.peek().value === 'properties') {
                this.advance(); // skip "properties"
                this.expect(TokenType.COLON);
                this.expect(TokenType.LEFT_BRACE);

                while (this.peek().type !== TokenType.RIGHT_BRACE) {
                    // Accept both IDENTIFIER and STRING as property keys
                    const key = this.peek().type === TokenType.STRING
                        ? this.advance().value
                        : this.expect(TokenType.IDENTIFIER).value;
                    this.expect(TokenType.COLON);
                    properties.set(key, this.parseValue());
                    if (this.peek().type === TokenType.COMMA) this.advance();
                }

                this.expect(TokenType.RIGHT_BRACE);
            }

            this.expect(TokenType.RIGHT_BRACE);
            return UDMFactory.object(properties, attributes);
        } else {
            // Parse simple object (shorthand format - no attributes/properties sections)
            while (this.peek().type !== TokenType.RIGHT_BRACE) {
                // Accept both IDENTIFIER and STRING as property keys
                const key = this.peek().type === TokenType.STRING
                    ? this.advance().value
                    : this.expect(TokenType.IDENTIFIER).value;
                this.expect(TokenType.COLON);
                properties.set(key, this.parseValue());
                if (this.peek().type === TokenType.COMMA) this.advance();
            }

            this.expect(TokenType.RIGHT_BRACE);
            return UDMFactory.object(properties);
        }
    }

    private parseAnnotatedObject(): UDM {
        this.expect(TokenType.IDENTIFIER); // @Object
        let name: string | undefined;
        const metadata = new Map<string, string>();

        if (this.peek().type === TokenType.LEFT_PAREN) {
            this.advance();

            while (this.peek().type !== TokenType.RIGHT_PAREN) {
                const key = this.expect(TokenType.IDENTIFIER).value;
                this.expect(TokenType.COLON);

                if (key === 'name') {
                    name = this.expect(TokenType.STRING).value;
                } else if (key === 'metadata') {
                    this.expect(TokenType.LEFT_BRACE);
                    while (this.peek().type !== TokenType.RIGHT_BRACE) {
                        const metaKey = this.expect(TokenType.IDENTIFIER).value;
                        this.expect(TokenType.COLON);
                        const metaValue = this.expect(TokenType.STRING).value;
                        metadata.set(metaKey, metaValue);
                        if (this.peek().type === TokenType.COMMA) this.advance();
                    }
                    this.expect(TokenType.RIGHT_BRACE);
                } else {
                    // Skip unknown metadata
                    this.parseValue();
                }

                if (this.peek().type === TokenType.COMMA) this.advance();
            }

            this.expect(TokenType.RIGHT_PAREN);
        }

        // Parse body
        const body = this.parseObject();
        if (body.type !== 'object') {
            throw new UDMParseException('Expected object body after @Object annotation');
        }

        return UDMFactory.object(body.properties, body.attributes, name, metadata);
    }

    private parseAnnotatedScalar(): UDM {
        this.advance(); // skip @Scalar
        if (this.peek().type === TokenType.LEFT_ANGLE) {
            this.advance(); // skip <
            this.advance(); // skip type name
            this.advance(); // skip >
        }
        this.expect(TokenType.LEFT_PAREN);
        const value = this.parseValue();
        this.expect(TokenType.RIGHT_PAREN);
        return value;
    }

    private parseDateTime(): UDM {
        this.advance(); // skip @DateTime
        this.expect(TokenType.LEFT_PAREN);
        const value = this.expect(TokenType.STRING).value;
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.datetime(value);
    }

    private parseDate(): UDM {
        this.advance(); // skip @Date
        this.expect(TokenType.LEFT_PAREN);
        const value = this.expect(TokenType.STRING).value;
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.date(value);
    }

    private parseLocalDateTime(): UDM {
        this.advance(); // skip @LocalDateTime
        this.expect(TokenType.LEFT_PAREN);
        const value = this.expect(TokenType.STRING).value;
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.localdatetime(value);
    }

    private parseTime(): UDM {
        this.advance(); // skip @Time
        this.expect(TokenType.LEFT_PAREN);
        const value = this.expect(TokenType.STRING).value;
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.time(value);
    }

    private parseBinary(): UDM {
        this.advance(); // skip @Binary
        this.expect(TokenType.LEFT_PAREN);
        // Skip size info
        while (this.peek().type !== TokenType.RIGHT_PAREN) this.advance();
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.binary(new Uint8Array(0));
    }

    private parseLambda(): UDM {
        this.advance(); // skip @Lambda
        this.expect(TokenType.LEFT_PAREN);
        this.expect(TokenType.RIGHT_PAREN);
        return UDMFactory.lambda();
    }
}
