# UTL-X Grammar

Complete EBNF grammar specification for UTL-X v1.0.

## Notation

- `::=` - Definition
- `|` - Alternative
- `()` - Grouping
- `[]` - Optional
- `{}` - Zero or more
- `<>` - Non-terminal
- `''` - Terminal (literal)

## Lexical Grammar

```ebnf
(* Comments *)
comment ::= line-comment | block-comment
line-comment ::= '//' {any-character-except-newline} newline
block-comment ::= '/*' {any-character} '*/'

(* Whitespace *)
whitespace ::= ' ' | '\t' | '\r' | '\n'

(* Identifiers *)
identifier ::= (letter | '_') {letter | digit | '_'}
letter ::= 'a'..'z' | 'A'..'Z'
digit ::= '0'..'9'

(* Keywords *)
keyword ::= 'let' | 'function' | 'if' | 'else' | 'match'
          | 'input' | 'output'
          | 'true' | 'false' | 'null' | 'try' | 'catch'
          | 'return' | 'import' | 'export' | 'typeof'

(* Literals *)
string-literal ::= '"' {string-char} '"' | "'" {string-char} "'"
string-char ::= any-character-except-quote | escape-sequence
escape-sequence ::= '\n' | '\t' | '\r' | '\\' | '\"' | "\'"

number-literal ::= integer-literal | decimal-literal | scientific-literal
integer-literal ::= ['-'] digit {digit}
decimal-literal ::= ['-'] digit {digit} '.' digit {digit}
scientific-literal ::= (integer-literal | decimal-literal) ('e' | 'E') ['+' | '-'] digit {digit}

boolean-literal ::= 'true' | 'false'
null-literal ::= 'null'

(* Operators *)
operator ::= arithmetic-op | comparison-op | logical-op | special-op
arithmetic-op ::= '+' | '-' | '*' | '/' | '%' | '**'
comparison-op ::= '==' | '!=' | '<' | '>' | '<=' | '>='
logical-op ::= '&&' | '||' | '!'
special-op ::= '|>' | '?.' | '??' | '@' | '=>'

(* Punctuation *)
punctuation ::= '(' | ')' | '{' | '}' | '[' | ']' | ',' | ':' | ';' | '---'
```

## Syntactic Grammar

### Program Structure

```ebnf
program ::= header configuration '---' expression

header ::= '%utlx' version-number
version-number ::= digit '.' digit

configuration ::= input-config output-config
input-config ::= 'input' format-spec [options-block]
output-config ::= 'output' (format-spec | output-formats) [options-block]

format-spec ::= identifier
output-formats ::= '{' output-format-list '}'
output-format-list ::= identifier ':' identifier {',' identifier ':' identifier}

options-block ::= '{' option-list '}'
option-list ::= option {',' option}
option ::= identifier ':' expression
```

### Expressions

```ebnf
expression ::= assignment-expression

assignment-expression ::= let-binding | pipe-expression

let-binding ::= 'let' identifier ['<image>data:image/s3,"s3://crabby-images/90dea/90dea97cb9a3b6f3df283ee4b685942b01b62a96" alt="type-annotation"] '=' expression [',' assignment-expression]

pipe-expression ::= conditional-expression ['|>' pipe-expression]

conditional-expression ::= ternary-expression | if-expression | match-expression

ternary-expression ::= logical-or-expression ['?' expression ':' expression]

if-expression ::= 'if' '(' expression ')' expression 
                  {'else' 'if' '(' expression ')' expression}
                  'else' expression

match-expression ::= 'match' expression '{' match-arm-list '}'
match-arm-list ::= match-arm {',' match-arm}
match-arm ::= pattern [guard] '=>' expression
pattern ::= literal | identifier | '_'
guard ::= 'if' expression

logical-or-expression ::= logical-and-expression {'||' logical-and-expression}
logical-and-expression ::= equality-expression {'&&' equality-expression}
equality-expression ::= relational-expression {('==' | '!=') relational-expression}
relational-expression ::= additive-expression {('<' | '>' | '<=' | '>=') additive-expression}
additive-expression ::= multiplicative-expression {('+' | '-') multiplicative-expression}
multiplicative-expression ::= exponentiation-expression {('*' | '/' | '%') exponentiation-expression}
exponentiation-expression ::= unary-expression ['**' exponentiation-expression]

unary-expression ::= postfix-expression | unary-operator unary-expression
unary-operator ::= '!' | '-' | '+'

postfix-expression ::= primary-expression {postfix-operator}
postfix-operator ::= member-access | index-access | safe-navigation | call-operator | attribute-access

member-access ::= '.' identifier
index-access ::= '[' expression ']'
safe-navigation ::= '?.' identifier
call-operator ::= '(' [argument-list] ')'
attribute-access ::= '@' identifier

argument-list ::= expression {',' expression}

primary-expression ::= literal
                     | identifier
                     | lambda-expression
                     | array-literal
                     | object-literal
                     | parenthesized-expression
                     | try-catch-expression

literal ::= string-literal | number-literal | boolean-literal | null-literal

lambda-expression ::= (identifier | parameter-list) '=>' (expression | block)
parameter-list ::= '(' [identifier {',' identifier}] ')'

array-literal ::= '[' [expression-list] ']'
expression-list ::= expression {',' expression}

object-literal ::= '{' [property-list] '}'
property-list ::= property {',' property}
property ::= (identifier | string-literal | '[' expression ']') ':' expression
           | '...' expression  (* spread operator *)

parenthesized-expression ::= '(' expression ')'

try-catch-expression ::= 'try' block 'catch' ['(' identifier ')'] block

block ::= '{' {statement} expression '}'
statement ::= let-binding | expression
```

### Type Annotations

```ebnf
type-annotation ::= ':' type

type ::= primitive-type
       | array-type
       | object-type
       | function-type
       | union-type
       | nullable-type

primitive-type ::= 'String' | 'Number' | 'Boolean' | 'Null' | 'Date' | 'Any'
array-type ::= 'Array' '<' type '>'
object-type ::= 'Object'
function-type ::= '(' [type-list] ')' '=>' type
type-list ::= type {',' type}
union-type ::= type '|' type
nullable-type ::= type '?'
```

### Function Definitions

```ebnf
function-definition ::= 'function' identifier '(' [parameter-list] ')' [type-annotation] block

parameter-list ::= parameter {',' parameter}
parameter ::= identifier [type-annotation]
```

## Operator Precedence (High to Low)

1. Member access (`.`), Index (`[]`), Call (`()`)
2. Unary (`!`, `-`, `+`)
3. Exponentiation (`**`)
4. Multiplicative (`*`, `/`, `%`)
5. Additive (`+`, `-`)
6. Relational (`<`, `>`, `<=`, `>=`)
7. Equality (`==`, `!=`)
8. Logical AND (`&&`)
9. Logical OR (`||`)
10. Nullish Coalescing (`??`)
11. Ternary (`? :`)
12. Pipe (`|>`)
13. Assignment (`=`)

## Associativity

- Left-associative: Most operators
- Right-associative: Exponentiation (`**`), ternary (`? :`), pipe (`|>`)

## Example Parse Tree

For the expression: `input.items |> filter(x => x.price > 100)`

```
pipe-expression
├── postfix-expression
│   ├── primary-expression (identifier: "input")
│   └── member-access (identifier: "items")
└── pipe-expression
    └── postfix-expression
        ├── primary-expression (identifier: "filter")
        └── call-operator
            └── argument-list
                └── lambda-expression
                    ├── parameter: x
                    └── relational-expression
                        ├── postfix-expression
                        │   ├── identifier: x
                        │   └── member-access: price
                        ├── operator: >
                        └── number-literal: 100
```

---
