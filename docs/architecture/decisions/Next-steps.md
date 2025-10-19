See [Grammar](https://github.com/grauwen/utl-x/blob/main/docs/reference/grammar.md)
----

Grammar Implementation Gap Analysis Complete

A comprehensive analysis comparing [docs/reference/grammar.md](https://github.com/grauwen/utl-x/blob/main/docs/reference/grammar.md) (the original grammar specification) with the current implementation.

# Key Findings:

Implementation Coverage: ~40-50% of grammar specification    
                                                                                                                                                                                 
## Critical Gaps Blocking Tests:                     
1. Function definitions (***function*** keyword) - 6 tests failing 
	- Grammar specifies function name(params) { body }
	- Parser only supports let name = (params) => expr
	- AST node exists but unused: Statement.FunctionDef
2. Match expressions - AST exists but parser missing
3. Try-catch - Keywords and parser missing 
4. Ternary operator (? :) - Token types missing

#Missing Operators: 
 - Exponentiation (**)
 - Safe navigation (?.)
 - Nullish coalescing (??)
 - Spread operator (...)
 
 ## Missing Features:
 - Template definitions and apply()
 - Type annotation enforcement (AST exists, not validated)
 - Block expressions with multiple statements
 - Import/Export statements
 - Return statements
 - Typeof operator

## What Works Well:            
✅ Core expressions (literals, identifiers, objects, arrays)   

✅ Lambda expressions (single and multi-param)     

✅ Pipe operator

✅ Binary/unary operators (arithmetic, comparison, logical)  

✅ Let bindings    

✅ If expressions  

✅ Function calls  

✅ Member/index access

✅ XML attribute syntax

#Recommended Next Steps: 

##Option 1: Fix Immediate Test Failures  
- Implement function keyword parsing to unblock 6 failing tests
- Quick win, highest ROI

##Option 2: Complete Core Features
- Add match expressions
- Add try-catch
- Add ternary operator
- Brings implementation to ~60% coverage

##Option 3: Full Grammar Implementation 
- Systematically implement all missing features
- Long-term goal for v1.0 compliance
  
The grammar specification in docs/reference/grammar.md defines a comprehensive language, but the current implementation has only implemented approximately 40-50% of the specified features. The parser focuses on core expression evaluation and basic transformations, while advanced features like pattern matching, templates, type annotations, and several operators remain unimplemented.

  ---
  1. LEXICAL GRAMMAR GAPS

  ✅ Fully Implemented

  - Comments (line comments // and block comments /* */)
  - Whitespace handling
  - Identifiers
  - String literals with escape sequences
  - Number literals (integer, decimal, scientific notation)
  - Boolean literals (true, false)
  - Null literal
  - Basic arithmetic operators: +, -, *, /, %
  - Comparison operators: ==, !=, <, <=, >, >=
  - Logical operators: &&, ||, !
  - Assignment: =
  - Arrow: =>
  - Pipe: |>
  - Attribute access: @
  - Recursive descent: ..

  ❌ Missing Token Types

  - Exponentiation operator: ** (grammar line 52)
  - Safe navigation: ?. (grammar line 55)
  - Nullish coalescing: ?? (grammar line 55)
  - Ternary operator: ? and : separately for ? : construct (grammar line 97)
  - Spread operator: ... (grammar line 150)

  ❌ Missing Keywords

  - try (grammar line 34)
  - catch (grammar line 34)
  - return (grammar line 35)
  - export (grammar line 35)
  - typeof (grammar line 35)

  ⚠️ Partially Implemented

  - Keywords recognized but not fully parsed: template, match, apply

  ---
  2. SYNTACTIC GRAMMAR GAPS

  ✅ Fully Implemented Features

  Program Structure

  - Header parsing (%utlx 1.0)
  - Input/output configuration with format specs
  - Format options blocks
  - Body expression parsing

  Basic Expressions

  - Number, string, boolean, null literals
  - Identifiers
  - Object literals: {key: value, ...}
  - Array literals: [element1, element2, ...]
  - Parenthesized expressions
  - Member access: object.property
  - Index access: array[0]
  - Function calls: functionName(arg1, arg2)
  - Let bindings: let x = value
  - If expressions: if (condition) thenExpr else elseExpr
  - Lambda expressions:
    - Single parameter: x => x * 2
    - Multiple parameters: (x, y) => x + y

  Operators

  - Pipe operator: |>
  - Binary operators:
    - Arithmetic: +, -, *, /, %
    - Comparison: ==, !=, <, <=, >, >=
    - Logical: &&, ||
  - Unary operators: !, -
  - Attribute access: @attribute

  Special Features

  - XML attribute syntax in object literals: @id: value or "@id": value ✅ (recently added)
  - Recursive descent: ..propertyName

  ---
  ❌ Missing Features (Specified but NOT Implemented)

  Expression Types

  1. Ternary Operator (grammar line 97)
  ternary-expression ::= logical-or-expression ['?' expression ':' expression]
    - Status: ❌ Not implemented
    - Impact: High - common pattern in many languages
  2. Match Expressions (grammar lines 103-107)
  match-expression ::= 'match' expression '{' match-arm-list '}'
  match-arm ::= pattern [guard] '=>' expression
  pattern ::= literal | identifier | '_'
  guard ::= 'if' expression
    - Status: ❌ Parser does not handle match keyword
    - AST Node: ✅ Expression.Match exists but unused
    - Impact: High - pattern matching is a core functional programming feature
  3. Try-Catch Expressions (grammar line 154)
  try-catch-expression ::= 'try' block 'catch' ['(' identifier ')'] block
    - Status: ❌ Not implemented (keywords missing)
    - Impact: High - error handling is critical
  4. Blocks with Statements (grammar lines 156-157)
  block ::= '{' {statement} expression '}'
  statement ::= let-binding | expression
    - Status: ⚠️ Partial - object literals use {...} but not multi-statement blocks
    - Impact: Medium - limits sequential operations
  5. Spread Operator (grammar line 150)
  property ::= '...' expression  (* spread operator *)
    - Status: ❌ Not implemented
    - Impact: Medium - useful for object merging

  Operators

  6. Exponentiation (grammar line 115)
  exponentiation-expression ::= unary-expression ['**' exponentiation-expression]
    - Status: ❌ ** token not defined
    - Impact: Low - can use pow() function instead
  7. Safe Navigation (grammar line 125)
  safe-navigation ::= '?.' identifier
    - Status: ❌ ?. token not defined
    - Impact: Medium - useful for null-safe access
  8. Nullish Coalescing (grammar line 211)
  Nullish Coalescing (`??`)
    - Status: ❌ ?? token not defined
    - Impact: Low - can use || or if instead

  Type System

  9. Type Annotations (grammar lines 160-179)
  type-annotation ::= ':' type
  type ::= primitive-type | array-type | object-type | function-type | union-type | nullable-type
    - Status: ❌ Parser accepts type annotations in Parameter but doesn't validate
    - AST Nodes: ✅ Type sealed class exists but not enforced
    - Impact: Medium - type safety not guaranteed

  Function & Template Definitions

  10. Function Definitions (grammar lines 181-188)
  function-definition ::= 'function' identifier '(' [parameter-list] ')' [type-annotation] block
    - Status: ❌ NOT implemented in parser
    - AST Node: ✅ Statement.FunctionDef exists but unused
    - Impact: HIGH - 6 conformance tests fail because they use def instead of let for functions
    - Note: Current workaround is let funcName = (params) => expression
  11. Template Definitions (grammar lines 190-198)
  template-definition ::= 'template' 'match' '=' string-literal [priority] block
  template-application ::= 'apply' '(' expression [',' mode] ')'
    - Status: ❌ Parser does not handle template definitions
    - AST Nodes:
        - ✅ Statement.TemplateDef exists
      - ✅ Expression.TemplateApplication exists
    - Impact: High - templates are a key XSLT-inspired feature

  Other Missing Features

  12. Import/Export Statements (grammar line 35)
    - Status: ❌ Keywords exist but no parser support
    - Impact: Medium - limits modularity
  13. Return Statements (grammar line 35)
    - Status: ❌ Not implemented
    - Impact: Low - expressions return last value
  14. Typeof Operator (grammar line 35)
    - Status: ❌ Keyword missing
    - Impact: Low - typeOf() function exists in stdlib

  ---
  3. CURRENT PARSE TREE STRUCTURE

  Example: input.items |> filter(x => x.price > 100)

  Grammar Specifies (lines 222-243):
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

  Current Implementation Produces:
  Expression.Pipe(
    source = Expression.MemberAccess(
      target = Expression.Identifier("input"),
      property = "items"
    ),
    target = Expression.FunctionCall(
      function = Expression.Identifier("filter"),
      arguments = [
        Expression.Lambda(
          parameters = [Parameter("x", null)],
          body = Expression.BinaryOp(
            left = Expression.MemberAccess(
              target = Expression.Identifier("x"),
              property = "price"
            ),
            operator = BinaryOperator.GREATER_THAN,
            right = Expression.NumberLiteral(100.0)
          )
        )
      ]
    )
  )

  ✅ Structure matches grammar specification perfectly

  ---
  Example: Object Literal with Attributes

  Input: {Order: {@id: "ORD-123", Customer: "John"}}

  Current Implementation:
  Expression.ObjectLiteral(
    properties = [
      Property(
        key = "Order",
        value = Expression.ObjectLiteral(
          properties = [
            Property(key = "id", value = Expression.StringLiteral("ORD-123"), isAttribute = true),
            Property(key = "Customer", value = Expression.StringLiteral("John"), isAttribute = false)
          ]
        ),
        isAttribute = false
      )
    ]
  )

  ✅ Attribute syntax working correctly (recently implemented)

  ---
  Example: Let Binding Chain

  Input: let x = 10, let y = 20, x + y

  Current Implementation:
  Expression.LetBinding(
    name = "x",
    value = Expression.NumberLiteral(10.0),
    // Nested let binding
    Expression.LetBinding(
      name = "y",
      value = Expression.NumberLiteral(20.0),
      // Final expression
      Expression.BinaryOp(
        left = Expression.Identifier("x"),
        operator = BinaryOperator.PLUS,
        right = Expression.Identifier("y")
      )
    )
  )

  ⚠️ Non-standard nesting - grammar doesn't specify this structure

  ---
  4. CRITICAL GAPS AFFECTING CURRENT TESTS

  Failing Tests Due to Missing function Keyword

  6 tests fail because they use this syntax:
  def functionName(param1, param2) {
    // body
  }

  Problem:
  - Grammar specifies function keyword (line 184)
  - Parser doesn't recognize function or def for function definitions
  - Current workaround: let functionName = (param1, param2) => expression

  AST exists but unused:
  data class FunctionDef(
      val name: String,
      val parameters: List<Parameter>,
      val returnType: Type?,
      val body: Expression,
      override val location: Location
  ) : Statement()

  ---
  5. OPERATOR PRECEDENCE

  Grammar Specifies (lines 200-214):
  1. Member access (.), Index ([]), Call (()) ✅
  2. Unary (!, -, +) ✅
  3. Exponentiation (**) ❌
  4. Multiplicative (*, /, %) ✅
  5. Additive (+, -) ✅
  6. Relational (<, >, <=, >=) ✅
  7. Equality (==, !=) ✅
  8. Logical AND (&&) ✅
  9. Logical OR (||) ✅
  10. Nullish Coalescing (??) ❌
  11. Ternary (? :) ❌
  12. Pipe (|>) ✅
  13. Assignment (=) ✅

  Implementation Coverage: 10/13 (77%)

  ---
  6. SUMMARY TABLE

  | Feature Category  | Specified in Grammar  | Implemented     | Coverage |
  |-------------------|-----------------------|-----------------|----------|
  | Lexical Tokens    | 30+ token types       | ~25 token types | ~83%     |
  | Keywords          | 17 keywords           | 11 keywords     | 65%      |
  | Operators         | 13 precedence levels  | 10 levels       | 77%      |
  | Expression Types  | 20+ types             | 12 types        | 60%      |
  | Literals          | 4 types               | 4 types         | 100%     |
  | Function Features | Definitions + lambdas | Lambdas only    | 50%      |
  | Pattern Matching  | Match expressions     | None            | 0%       |
  | Templates         | Definitions + apply   | None            | 0%       |
  | Type System       | Full annotations      | AST only        | 25%      |
  | Error Handling    | Try-catch             | None            | 0%       |

  Overall Implementation: ~40-50% of grammar specification

  ---
  7. PRIORITIZED IMPLEMENTATION GAPS

  High Priority (Blocking Tests)

  1. ✅ Attribute syntax - DONE (just implemented)
  2. ❌ Function definitions (function keyword) - Blocks 6 tests
  3. ❌ Match expressions - Core functional feature
  4. ❌ Try-catch - Error handling essential
  5. ❌ Ternary operator - Common pattern

  Medium Priority (Nice to Have)

  6. ❌ Safe navigation (?.) - Null safety
  7. ❌ Template definitions - XSLT heritage feature
  8. ❌ Spread operator (...) - Object merging
  9. ❌ Block expressions - Multi-statement sequences
  10. ❌ Type annotations enforcement - Type safety

  Low Priority (Workarounds Exist)

  11. ❌ Exponentiation (**) - Can use pow(base, exp)
  12. ❌ Nullish coalescing (??) - Can use || or if
  13. ❌ Return statement - Expressions return last value
  14. ❌ Typeof operator - typeOf() function exists
  15. ❌ Import/Export - Modularity feature
