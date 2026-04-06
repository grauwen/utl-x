# UTLX Language Reference for LLMs

**Purpose**: Compact, precise reference for code generation. No examples - only syntax rules.

## Document Structure

```
%utlx 1.0
input <name> <format>
[input <name2> <format2>, ...]
output <format>
---
<transformation-expression>
```

**Rules**:
- Header is NEVER generated or modified
- Only generate transformation expression after `---`
- Multiple inputs: `input: name1 format1, name2 format2, name3 format3`

## Supported Formats
`xml`, `json`, `csv`, `yaml`, `xsd`, `jsch`, `avro`, `proto`

## Literals

### Number
- Syntax: `42`, `3.14`, `-5`, `1.5e10`
- Decimal only (no hex/octal/binary)
- Optional negative sign, optional fractional part, optional exponent

### String
- Syntax: `"text"` or `'text'`
- Escape sequences: `\n`, `\t`, `\r`, `\\`, `\"`, `\'`

### Boolean
- Syntax: `true` | `false`

### Null
- Syntax: `null`

### Array Literal
- Syntax: `[expr1, expr2, ...]`
- Empty: `[]`
- Spread: `[...arr1, item, ...arr2]`

### Object Literal
- Syntax: `{ key1: expr1, key2: expr2, ... }`
- Empty: `{}`
- Keys: identifiers or strings
- Computed keys: `[expr]: value`
- Spread: `{ ...obj1, key: value, ...obj2 }`
- Let bindings inside: `{ let x = value; key: x }`

## Data Access

### Input Reference
- Syntax: `$inputName`
- References named input from header
- Example: For `input employees csv` use `$employees`

### Property Access
- Syntax: `expr.property`
- Chains: `$input.level1.level2.level3`

### XML Attribute Access
- Syntax: `expr.@attributeName`
- Example: `$input.element.@id`

### Array Index
- Syntax: `expr[index]`
- Index is integer or expression
- Example: `$input.items[0]`, `$input[i]`

### Safe Navigation
- Syntax: `expr?.property`
- Returns `null` if expr is `null`, otherwise accesses property

## Operators

### Arithmetic
- `+` (addition, string concatenation)
- `-` (subtraction)
- `*` (multiplication)
- `/` (division)
- `%` (modulo)
- `**` (exponentiation)

### Comparison
- `==` (equal)
- `!=` (not equal)
- `<` (less than)
- `<=` (less or equal)
- `>` (greater than)
- `>=` (greater or equal)

### Logical
- `&&` (and)
- `||` (or)
- `!` (not)

### Null Handling
- `??` (null coalescing: returns right if left is null)

### Pipe
- `|>` (pipe operator)
- Syntax: `expr |> func`
- Passes left expression as first argument to right
- Example: `$input.items |> map(x => x.id)`
- Chains: `$input |> filter(f) |> map(m) |> sum()`

### Spread
- `...expr` (in arrays or objects)
- Arrays: `[...arr]` flattens array
- Objects: `{...obj}` merges object properties

## Operator Precedence (highest to lowest)
1. Member access (`.`, `.@`, `[...]`)
2. Function call `()`
3. Unary `-`, `!`
4. Exponent `**`
5. Multiply/Divide/Mod `*`, `/`, `%`
6. Add/Subtract `+`, `-`
7. Comparison `<`, `<=`, `>`, `>=`
8. Equality `==`, `!=`
9. Logical AND `&&`
10. Logical OR `||`
11. Null coalesce `??`
12. Ternary `? :`
13. Pipe `|>`

## Control Flow

### Ternary Operator
- Syntax: `condition ? thenExpr : elseExpr`
- Returns thenExpr if condition is truthy, else elseExpr

### If-Else Expression
- Syntax: `if (condition) thenExpr else elseExpr`
- `else` is required
- Evaluates to value of chosen branch

### Match Expression
- Syntax: `match value { pattern1 => expr1, pattern2 => expr2, ... }`
- Patterns: literals, `_` (wildcard), variable binding
- Optional guard: `pattern if condition => expr`
- First matching pattern wins
- Example: `match x { 0 => "zero", _ => "other" }`

## Functions

### Function Call
- Syntax: `functionName(arg1, arg2, ...)`
- Zero or more arguments

### Lambda Expression
- Syntax: `param => expression`
- Multiple params: `(p1, p2) => expression`
- With block: `(p1, p2) => { let x = p1 + p2; x * 2 }`

### Function Definition (in object)
- Not top-level, use let binding for reusable functions
- Syntax: `let funcName = (params) => body`

## Variable Binding

### Let Binding
- Syntax: `let varName = expr`
- In object: `{ let x = value; prop: x }`
- Multiple: `let x = 1, let y = 2`
- Scoped to containing block/object

## Comments
- Single line: `// comment`
- Multi-line: `/* comment */`
- Hash: `# comment` (alternative single-line)

## Type System (Design-Time Mode)
- Not for code generation - runtime operates on UDM
- Types: `string`, `number`, `boolean`, `null`, `date`, `any`
- Array: `type[]`
- Nullable: `type?`
- Union: `type1 | type2`
- Object: `{ prop: type, ... }`

## Standard Library Access
- Functions available at runtime (names provided dynamically)
- Call pattern: `functionName(args)`
- Pipe pattern: `expr |> functionName(args)`
- Common categories: array operations, string operations, math, aggregation, type conversion, date/time

## Multi-Input Transformations
- Access inputs by name: `$input1`, `$input2`
- Join pattern: create lookup maps, then iterate
- Example structure: `let map = $input1 |> ...; $input2 |> map(x => { ...use map... })`

## Error Handling
- Try-catch: `try expr catch (errorVar) { handler }`
- Nullish coalesce for defaults: `expr ?? default`
- Safe navigation for optional paths: `obj?.prop?.nested`

## Key Constraints
1. **NEVER modify or output headers** - only transformation body
2. **Always use exact input names** from header (`$employees` not `$input`)
3. **Immutable data** - no assignments, only expressions
4. **No loops** - use `map`, `filter`, `reduce` (from stdlib)
5. **No regex literals** - use `match()` or `replace()` functions
6. **No date literals** - use `date()` or `parseDate()` functions
7. **Identifiers can contain hyphens**: `my-variable`, `input-name`

## Data Format Specifics

### XML
- Access text content: `$input.element` (text inside element)
- Access attributes: `$input.element.@attr`
- Nested elements: `$input.root.child.grandchild`
- Arrays from repeated elements: `$input.root.items` (auto-array if multiple <items>)

### JSON
- Direct property access: `$input.property`
- Arrays: `$input.items[0]` or `$input.items |> map(...)`
- Nested: `$input.level1.level2.level3`

### CSV
- Input is array of objects: `$input` is `[{col1: val, col2: val}, ...]`
- Headers become property names
- Iterate: `$input |> map(row => ...)`

### YAML
- Same as JSON (parsed to same UDM structure)

## Output Generation Rules
1. Return single expression (object, array, or value)
2. Object for structured output: `{ field1: expr1, field2: expr2 }`
3. Array for list output: `[expr1, expr2, ...]`
4. Use output format from header for serialization
5. No explicit format conversion needed - UDM handles it

## Expression Evaluation
- Everything is an expression (returns value)
- Last expression in block is returned
- Side effects not allowed (pure functions only)
- Lazy evaluation for conditionals and boolean ops
- Pipe left-to-right: `a |> b |> c` means `c(b(a))`

## Common Patterns

### Transform Array
`$input |> map(item => { ... })`

### Filter Array
`$input |> filter(item => condition)`

### Aggregate
`$input |> map(item => item.value) |> sum()`

### Conditional Property
`{ key: condition ? value1 : value2 }`

### Merge Objects
`{ ...baseObj, overrideKey: newValue }`

### Flatten Nested Arrays
`$input.items |> flatten()`

### Group and Aggregate
`$input |> groupBy(x => x.category)`

### Lookup Join
```
let lookup = $input1 |> map(x => [x.id, x]) |> reduce((acc, [k,v]) => {...acc, [k]: v}, {});
$input2 |> map(y => { ...y, joined: lookup[y.foreignId] })
```

## Metadata Access (Advanced)
- `expr.^metadata` - access format-specific metadata
- Use case: XML namespaces, JSON-LD context, etc.
- Not commonly needed for simple transformations

## Validation
- Runtime mode: validates against actual data structure
- Design-time mode: validates against input schema
- Errors: property not found, type mismatch, function arity
