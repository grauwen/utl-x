# Functions

UTL-X has two kinds of functions:

| | Stdlib Functions | User-Defined Functions |
|--|-----------------|----------------------|
| **Naming** | lowercase/camelCase | **PascalCase (required)** |
| **Examples** | `sum`, `map`, `filter`, `parseNumber`, `formatDate` | `CalculateTax`, `FormatName`, `ProcessOrder` |
| **Count** | 652 built-in | Unlimited |
| **Availability** | Always available, no import needed | Defined in your `.utlx` script |

**Rule:** User-defined functions must start with an uppercase letter. Lowercase names are reserved for the 652 stdlib functions.

```utlx
// Stdlib (lowercase) — built-in, always available
sum([1, 2, 3])
upper("hello")
formatDate(now(), "yyyy-MM-dd")

// User-defined (PascalCase) — you write these
function CalculateTotal(price: Number, qty: Number): Number {
  price * qty
}
```

See the [Stdlib Reference](../stdlib/stdlib-complete-reference.md) for all 652 built-in functions.

---

## Built-in Functions

### String Functions

#### upper(str: String): String
Convert string to uppercase.

```utlx
upper("hello")                   // "HELLO"
upper("Hello World")             // "HELLO WORLD"
```

#### lower(str: String): String
Convert string to lowercase.

```utlx
lower("HELLO")                   // "hello"
lower("Hello World")             // "hello world"
```

#### trim(str: String): String
Remove leading and trailing whitespace.

```utlx
trim("  hello  ")                // "hello"
trim("\n\ttext\t\n")            // "text"
```

#### substring(str: String, start: Number, end: Number): String
Extract substring.

```utlx
substring("hello", 0, 3)         // "hel"
substring("hello", 2, 5)         // "llo"
substring("hello", 1)            // "ello" (to end)
```

#### split(str: String, delimiter: String): Array<String>
Split string into array.

```utlx
split("a,b,c", ",")              // ["a", "b", "c"]
split("one two three", " ")      // ["one", "two", "three"]
```

#### join(array: Array<String>, delimiter: String): String
Join array into string.

```utlx
join(["a", "b", "c"], ",")       // "a,b,c"
join(["one", "two"], " ")        // "one two"
```

#### concat(str1: String, ...): String
Concatenate strings.

```utlx
concat("Hello", " ", "World")    // "Hello World"
concat("a", "b", "c")            // "abc"
```

#### replace(str: String, pattern: String, replacement: String): String
Replace text.

```utlx
replace("hello world", "world", "there")  // "hello there"
replace("aaa", "a", "b")                   // "bbb"
```

#### matches(str: String, pattern: String): Boolean
Test if string matches regex pattern.

```utlx
matches("hello", "^h")           // true
matches("test@example.com", ".*@.*\\..*")  // true
```

#### startsWith(str: String, prefix: String): Boolean
Check if string starts with prefix.

```utlx
startsWith("hello", "hel")       // true
startsWith("world", "wor")       // true
```

#### endsWith(str: String, suffix: String): Boolean
Check if string ends with suffix.

```utlx
endsWith("hello", "llo")         // true
endsWith("world", "rld")         // true
```

#### length(str: String): Number
Get string length.

```utlx
length("hello")                  // 5
length("")                       // 0
```

### Array Functions

#### map(array: Array<T>, fn: (T) => U): Array<U>
Transform each element.

```utlx
map([1, 2, 3], n => n * 2)       // [2, 4, 6]
[1, 2, 3] |> map(n => n * 2)    // [2, 4, 6] (with pipe)
```

#### filter(array: Array<T>, fn: (T) => Boolean): Array<T>
Keep elements matching predicate.

```utlx
filter([1, 2, 3, 4], n => n > 2) // [3, 4]
[1, 2, 3, 4] |> filter(n => n > 2)  // [3, 4]
```

#### reduce(array: Array<T>, fn: (acc: U, elem: T) => U, initial: U): U
Reduce array to single value.

```utlx
reduce([1, 2, 3], (acc, n) => acc + n, 0)  // 6
reduce(["a", "b"], (acc, s) => acc + s, "") // "ab"
```

#### sum(array: Array<Number>): Number
Sum numeric array.

```utlx
sum([1, 2, 3, 4])                // 10
sum([10.5, 20.3])                // 30.8
```

#### avg(array: Array<Number>): Number
Calculate average.

```utlx
avg([1, 2, 3, 4])                // 2.5
avg([10, 20, 30])                // 20
```

#### min(array: Array<Number>): Number
Find minimum value.

```utlx
min([3, 1, 4, 1, 5])             // 1
min([10.5, 2.3, 8.7])            // 2.3
```

#### max(array: Array<Number>): Number
Find maximum value.

```utlx
max([3, 1, 4, 1, 5])             // 5
max([10.5, 2.3, 8.7])            // 10.5
```

#### count(array: Array<T>): Number
Count elements.

```utlx
count([1, 2, 3])                 // 3
count([])                        // 0
```

#### first(array: Array<T>): T
Get first element.

```utlx
first([1, 2, 3])                 // 1
first(["a", "b"])                // "a"
```

#### last(array: Array<T>): T
Get last element.

```utlx
last([1, 2, 3])                  // 3
last(["a", "b"])                 // "b"
```

#### take(array: Array<T>, n: Number): Array<T>
Take first n elements.

```utlx
take([1, 2, 3, 4, 5], 3)         // [1, 2, 3]
```

#### drop(array: Array<T>, n: Number): Array<T>
Drop first n elements.

```utlx
drop([1, 2, 3, 4, 5], 2)         // [3, 4, 5]
```

#### sort(array: Array<T>): Array<T>
Sort array ascending.

```utlx
sort([3, 1, 4, 1, 5])            // [1, 1, 3, 4, 5]
sort(["c", "a", "b"])            // ["a", "b", "c"]
```

#### sortBy(array: Array<T>, fn: (T) => U): Array<T>
Sort by function result.

```utlx
sortBy([{age: 30}, {age: 20}], item => item.age)
// [{age: 20}, {age: 30}]
```

#### reverse(array: Array<T>): Array<T>
Reverse array.

```utlx
reverse([1, 2, 3])               // [3, 2, 1]
```

#### flatten(array: Array<Array<T>>): Array<T>
Flatten nested arrays.

```utlx
flatten([[1, 2], [3, 4]])        // [1, 2, 3, 4]
flatten([[1], [2, 3], [4, 5, 6]]) // [1, 2, 3, 4, 5, 6]
```

#### flatMap(array: Array<T>, fn: (T) => Array<U>): Array<U>
Map and flatten.

```utlx
flatMap([1, 2, 3], n => [n, n * 2])  // [1, 2, 2, 4, 3, 6]
```

#### distinct(array: Array<T>): Array<T>
Remove duplicates.

```utlx
distinct([1, 2, 2, 3, 3, 3])     // [1, 2, 3]
distinct(["a", "b", "a"])        // ["a", "b"]
```

#### groupBy(array: Array<T>, fn: (T) => K): Object
Group by key function.

```utlx
groupBy([{type: "A", val: 1}, {type: "B", val: 2}, {type: "A", val: 3}], 
        item => item.type)
// {A: [{type: "A", val: 1}, {type: "A", val: 3}], 
//  B: [{type: "B", val: 2}]}
```

### Math Functions

#### abs(n: Number): Number
Absolute value.

```utlx
abs(-5)                          // 5
abs(5)                           // 5
```

#### round(n: Number): Number
Round to nearest integer.

```utlx
round(3.7)                       // 4
round(3.2)                       // 3
```

#### ceil(n: Number): Number
Round up.

```utlx
ceil(3.2)                        // 4
ceil(3.9)                        // 4
```

#### floor(n: Number): Number
Round down.

```utlx
floor(3.9)                       // 3
floor(3.2)                       // 3
```

#### pow(base: Number, exponent: Number): Number
Exponentiation.

```utlx
pow(2, 3)                        // 8
pow(10, 2)                       // 100
```

#### sqrt(n: Number): Number
Square root.

```utlx
sqrt(16)                         // 4
sqrt(2)                          // 1.414...
```

#### random(): Number
Random number between 0 and 1.

```utlx
random()                         // 0.547... (random)
```

### Date Functions

#### now(): Date
Current date and time.

```utlx
now()                            // 2025-10-09T14:30:00Z
```

#### parseDate(str: String, format: String): Date
Parse date from string.

```utlx
parseDate("2025-10-09", "yyyy-MM-dd")
parseDate("Oct 09, 2025", "MMM dd, yyyy")
```

**Format Codes:**
- `yyyy` - 4-digit year
- `MM` - 2-digit month
- `dd` - 2-digit day
- `HH` - 24-hour
- `mm` - Minutes
- `ss` - Seconds

#### formatDate(date: Date, format: String): String
Format date as string.

```utlx
formatDate(now(), "yyyy-MM-dd")           // "2025-10-09"
formatDate(now(), "MMM dd, yyyy")         // "Oct 09, 2025"
formatDate(now(), "yyyy-MM-dd HH:mm:ss")  // "2025-10-09 14:30:00"
```

#### addDays(date: Date, days: Number): Date
Add days to date.

```utlx
addDays(now(), 7)                // 7 days from now
addDays(now(), -7)               // 7 days ago
```

#### addMonths(date: Date, months: Number): Date
Add months to date.

```utlx
addMonths(now(), 3)              // 3 months from now
```

#### addYears(date: Date, years: Number): Date
Add years to date.

```utlx
addYears(now(), 1)               // 1 year from now
```

#### diffDays(date1: Date, date2: Date): Number
Difference in days.

```utlx
diffDays(parseDate("2025-10-15", "yyyy-MM-dd"), 
         parseDate("2025-10-09", "yyyy-MM-dd"))  // 6
```

### Type Functions

#### getType(value: Any): String
Get type name.

```utlx
getType(42)                      // "number"
getType("hello")                 // "string"
getType(true)                    // "boolean"
getType([1, 2])                  // "array"
getType({a: 1})                  // "object"
getType(null)                    // "null"
```

#### isString(value: Any): Boolean
Check if string.

```utlx
isString("hello")                // true
isString(42)                     // false
```

#### isNumber(value: Any): Boolean
Check if number.

```utlx
isNumber(42)                     // true
isNumber("42")                   // false
```

#### isBoolean(value: Any): Boolean
Check if boolean.

```utlx
isBoolean(true)                  // true
isBoolean(1)                     // false
```

#### isArray(value: Any): Boolean
Check if array.

```utlx
isArray([1, 2])                  // true
isArray("array")                 // false
```

#### isObject(value: Any): Boolean
Check if object.

```utlx
isObject({a: 1})                 // true
isObject([1, 2])                 // false
```

#### isNull(value: Any): Boolean
Check if null.

```utlx
isNull(null)                     // true
isNull(0)                        // false
```

#### parseNumber(str: String): Number
Parse number from string.

```utlx
parseNumber("42")                // 42
parseNumber("3.14")              // 3.14
parseNumber("invalid")           // Error (use try/catch)
```

#### toString(value: Any): String
Convert to string.

```utlx
toString(42)                     // "42"
toString(true)                   // "true"
toString([1, 2])                 // "[1, 2]"
```

### Object Functions

#### keys(obj: Object): Array<String>
Get object keys.

```utlx
keys({name: "Alice", age: 30})   // ["name", "age"]
```

#### values(obj: Object): Array<Any>
Get object values.

```utlx
values({name: "Alice", age: 30}) // ["Alice", 30]
```

#### entries(obj: Object): Array<Array<Any>>
Get key-value pairs.

```utlx
entries({name: "Alice", age: 30})
// [["name", "Alice"], ["age", 30]]
```

#### merge(obj1: Object, obj2: Object): Object
Merge objects.

```utlx
merge({a: 1}, {b: 2})            // {a: 1, b: 2}
merge({a: 1, b: 2}, {b: 3, c: 4}) // {a: 1, b: 3, c: 4}
```

## User-Defined Functions

### Naming Convention: PascalCase Required

User-defined functions **must** start with an uppercase letter (PascalCase). This prevents collisions with the 652 stdlib functions, which all use lowercase/camelCase.

```utlx
// ✅ CORRECT - user functions start with uppercase
function Double(n: Number): Number {
  n * 2
}

function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

function FormatName(first: String, last: String): String {
  upper(first) + " " + upper(last)
}

// ❌ WRONG - lowercase is reserved for stdlib
function double(n: Number): Number {  // ERROR: conflicts with stdlib
  n * 2
}
```

**Why PascalCase?**
- All stdlib functions use lowercase/camelCase (`sum`, `map`, `filter`, `parseNumber`, etc.)
- Uppercase makes it immediately clear which functions are user-defined
- Prevents name collisions as the stdlib grows

### Function Declaration

```utlx
function Name(param1: Type1, param2: Type2): ReturnType {
  // Function body - last expression is returned
}
```

### Multiple Statements

```utlx
function CalculateTotal(price: Number, quantity: Number, taxRate: Number): Number {
  let subtotal = price * quantity,
  let tax = subtotal * taxRate,
  
  subtotal + tax
}
```

### Early Return

```utlx
function Divide(x: Number, y: Number): Number {
  if (y == 0) {
    return 0  // Early return
  }
  x / y
}
```

### Anonymous Functions (Lambdas)

#### Single Parameter

```utlx
(n) => n * 2
n => n * 2       // Parentheses optional for single param
```

#### Multiple Parameters

```utlx
(x, y) => x + y
```

#### No Parameters

```utlx
() => 42
```

#### Block Body

```utlx
(n) => {
  let doubled = n * 2
  doubled + 1
}
```

### Higher-Order Functions

Functions that take or return functions:

```utlx
function Apply(fn: (Number) => Number, value: Number): Number {
  fn(value)
}

let result = Apply(n => n * 2, 5)  // 10
```

### Closures

Functions can capture variables from outer scope:

```utlx
function MakeMultiplier(factor: Number): (Number) => Number {
  (n) => n * factor  // Captures 'factor'
}

let double = MakeMultiplier(2)
let triple = MakeMultiplier(3)

double(5)  // 10
triple(5)  // 15
```

## Function Composition

### Pipe Operator

Chain function calls:

```utlx
let result = $input.items
  |> filter(item => item.price > 50)
  |> map(item => item.name)
  |> sort()
  |> take(5)
```

### Compose Function (Future)

```utlx
let processItems = compose(
  items => filter(items, item => item.price > 50),
  items => map(items, item => item.name),
  names => sort(names)
)

processItems($input.items)
```

## Recursion

Functions can call themselves:

```utlx
function Factorial(n: Number): Number {
  if (n <= 1)
    1
  else
    n * factorial(n - 1)
}

factorial(5)  // 120
```

### Tail Recursion

Optimized recursive calls:

```utlx
function sum(numbers: Array<Number>, acc: Number): Number {
  if (isEmpty(numbers))
    acc
  else
    sum(rest(numbers), acc + first(numbers))
}
```

## Partial Application (Future)

Create functions with pre-filled arguments:

```utlx
function Add(x: Number, y: Number): Number {
  x + y
}

let add5 = partial(Add, 5)
add5(10)  // 15
```

## Default Parameters (Future)

```utlx
function greet(name: String, greeting: String = "Hello"): String {
  greeting + ", " + name
}

greet("Alice")              // "Hello, Alice"
greet("Bob", "Hi")          // "Hi, Bob"
```

## Rest Parameters (Future)

```utlx
function Sum(...numbers: Number): Number {
  reduce(numbers, (acc, n) => acc + n, 0)
}

sum(1, 2, 3, 4)             // 10
```

## Function Examples

### Transform Array of Objects

```utlx
function TransformOrders(orders: Array<Object>): Array<Object> {
  orders |> map(order => {
    id: order.id,
    total: sum(order.items.*.price),
    itemCount: count(order.items)
  })
}
```

### Calculate Discounts

```utlx
function CalculateDiscount(total: Number, customerType: String): Number {
  if (customerType == "VIP")
    total * 0.20
  else if (customerType == "Premium")
    total * 0.10
  else if (total > 1000)
    total * 0.05
  else
    0
}
```

### Format Currency

```utlx
function FormatCurrency(amount: Number, currency: String): String {
  let formatted = toString(round(amount * 100) / 100)
  currency + " " + formatted
}

formatCurrency(29.99, "USD")  // "USD 29.99"
```

### Validate Email

```utlx
function IsValidEmail(email: String): Boolean {
  matches(email, "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
}
```

## Best Practices

### 1. Use Type Annotations

```utlx
// ✅ Good - PascalCase, typed parameters
function CalculateTax(amount: Number, rate: Number): Number {
  amount * rate
}

// ❌ Bad - lowercase name, no types
function calculateTax(amount, rate) {
  amount * rate
}
```

### 2. Keep Functions Small

```utlx
// ✅ Good - single responsibility
function CalculateSubtotal(items: Array<Object>): Number {
  sum(items.*.price)
}

function CalculateTax(subtotal: Number): Number {
  subtotal * 0.08
}

// ❌ Bad - doing too much
function ProcessOrder(order: Object): Object {
  // 100 lines...
}
```

### 3. Use Descriptive Names

```utlx
// ✅ Good
function CalculateShippingCost(weight: Number, distance: Number): Number { ... }

// ❌ Bad
function Calc(w: Number, d: Number): Number { ... }
```

### 4. Handle Edge Cases

```utlx
function Divide(x: Number, y: Number): Number {
  if (y == 0) {
    return 0  // Or throw error
  }
  x / y
}
```

### 5. Use Pure Functions

```utlx
// ✅ Good - pure (same input = same output)
function Add(x: Number, y: Number): Number {
  x + y
}

// ❌ Bad - impure (depends on external state)
let counter = 0
function Increment(): Number {
  counter = counter + 1
  counter
}
```

---
