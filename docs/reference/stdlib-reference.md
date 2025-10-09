# Standard Library Reference

Complete reference for all built-in functions in UTL-X.

## String Functions

### upper(str: String): String
Convert string to uppercase.

```utlx
upper("hello world")  // "HELLO WORLD"
upper("Test123")      // "TEST123"
```

**Parameters:**
- `str` - Input string

**Returns:** Uppercase string

**Errors:** None

---

### lower(str: String): String
Convert string to lowercase.

```utlx
lower("HELLO WORLD")  // "hello world"
lower("Test123")      // "test123"
```

**Parameters:**
- `str` - Input string

**Returns:** Lowercase string

---

### trim(str: String): String
Remove leading and trailing whitespace.

```utlx
trim("  hello  ")     // "hello"
trim("\n\ttext\t\n")  // "text"
```

**Parameters:**
- `str` - Input string

**Returns:** Trimmed string

---

### substring(str: String, start: Number, end?: Number): String
Extract substring from start to end (exclusive).

```utlx
substring("hello", 0, 3)  // "hel"
substring("hello", 2)     // "llo" (to end)
substring("hello", 1, 4)  // "ell"
```

**Parameters:**
- `str` - Input string
- `start` - Starting index (0-based)
- `end` - Ending index (optional, defaults to string length)

**Returns:** Extracted substring

---

### split(str: String, delimiter: String): Array<String>
Split string into array using delimiter.

```utlx
split("a,b,c", ",")           // ["a", "b", "c"]
split("one two three", " ")   // ["one", "two", "three"]
```

**Parameters:**
- `str` - Input string
- `delimiter` - Delimiter string

**Returns:** Array of strings

---

### join(array: Array<String>, delimiter: String): String
Join array elements into string with delimiter.

```utlx
join(["a", "b", "c"], ",")    // "a,b,c"
join(["one", "two"], " ")     // "one two"
```

**Parameters:**
- `array` - Array of strings
- `delimiter` - Delimiter string

**Returns:** Joined string

---

### concat(str1: String, str2: String, ...): String
Concatenate multiple strings.

```utlx
concat("Hello", " ", "World")  // "Hello World"
concat("a", "b", "c")          // "abc"
```

**Parameters:**
- Variable number of strings

**Returns:** Concatenated string

---

### replace(str: String, pattern: String, replacement: String): String
Replace all occurrences of pattern with replacement.

```utlx
replace("hello world", "world", "there")  // "hello there"
replace("aaa", "a", "b")                   // "bbb"
```

**Parameters:**
- `str` - Input string
- `pattern` - Pattern to find
- `replacement` - Replacement string

**Returns:** Modified string

---

### matches(str: String, pattern: String): Boolean
Test if string matches regex pattern.

```utlx
matches("hello", "^h")                      // true
matches("test@example.com", ".*@.*\\..*")  // true
```

**Parameters:**
- `str` - Input string
- `pattern` - Regular expression pattern

**Returns:** True if matches, false otherwise

---

### startsWith(str: String, prefix: String): Boolean
Check if string starts with prefix.

```utlx
startsWith("hello", "hel")  // true
startsWith("world", "wor")  // true
startsWith("test", "xyz")   // false
```

---

### endsWith(str: String, suffix: String): Boolean
Check if string ends with suffix.

```utlx
endsWith("hello", "llo")  // true
endsWith("world", "rld")  // true
endsWith("test", "xyz")   // false
```

---

### length(str: String): Number
Get string length.

```utlx
length("hello")  // 5
length("")       // 0
```

---

### indexOf(str: String, search: String): Number
Find first index of search string.

```utlx
indexOf("hello world", "world")  // 6
indexOf("hello", "xyz")          // -1 (not found)
```

---

### lastIndexOf(str: String, search: String): Number
Find last index of search string.

```utlx
lastIndexOf("hello hello", "hello")  // 6
```

---

## Array Functions

### map(array: Array<T>, fn: (T) => U): Array<U>
Transform each element using function.

```utlx
map([1, 2, 3], n => n * 2)  // [2, 4, 6]
```

**Parameters:**
- `array` - Input array
- `fn` - Transformation function

**Returns:** Transformed array

---

### filter(array: Array<T>, fn: (T) => Boolean): Array<T>
Keep elements where function returns true.

```utlx
filter([1, 2, 3, 4], n => n > 2)  // [3, 4]
```

**Parameters:**
- `array` - Input array
- `fn` - Predicate function

**Returns:** Filtered array

---

### reduce(array: Array<T>, fn: (acc: U, elem: T) => U, initial: U): U
Reduce array to single value.

```utlx
reduce([1, 2, 3], (acc, n) => acc + n, 0)  // 6
```

**Parameters:**
- `array` - Input array
- `fn` - Reducer function
- `initial` - Initial accumulator value

**Returns:** Reduced value

---

### sum(array: Array<Number>): Number
Sum numeric array.

```utlx
sum([1, 2, 3, 4])   // 10
sum([10.5, 20.3])   // 30.8
```

---

### avg(array: Array<Number>): Number
Calculate average.

```utlx
avg([1, 2, 3, 4])  // 2.5
avg([10, 20, 30])  // 20
```

---

### min(array: Array<Number>): Number
Find minimum value.

```utlx
min([3, 1, 4, 1, 5])  // 1
```

---

### max(array: Array<Number>): Number
Find maximum value.

```utlx
max([3, 1, 4, 1, 5])  // 5
```

---

### count(array: Array<T>): Number
Count elements.

```utlx
count([1, 2, 3])  // 3
count([])         // 0
```

---

### first(array: Array<T>): T
Get first element.

```utlx
first([1, 2, 3])  // 1
```

---

### last(array: Array<T>): T
Get last element.

```utlx
last([1, 2, 3])  // 3
```

---

### take(array: Array<T>, n: Number): Array<T>
Take first n elements.

```utlx
take([1, 2, 3, 4, 5], 3)  // [1, 2, 3]
```

---

### drop(array: Array<T>, n: Number): Array<T>
Drop first n elements.

```utlx
drop([1, 2, 3, 4, 5], 2)  // [3, 4, 5]
```

---

### sort(array: Array<T>): Array<T>
Sort array ascending.

```utlx
sort([3, 1, 4, 1, 5])     // [1, 1, 3, 4, 5]
sort(["c", "a", "b"])     // ["a", "b", "c"]
```

---

### sortBy(array: Array<T>, fn: (T) => U): Array<T>
Sort by function result.

```utlx
sortBy([{age: 30}, {age: 20}], item => item.age)
// [{age: 20}, {age: 30}]
```

---

### reverse(array: Array<T>): Array<T>
Reverse array.

```utlx
reverse([1, 2, 3])  // [3, 2, 1]
```

---

### flatten(array: Array<Array<T>>): Array<T>
Flatten nested arrays (one level).

```utlx
flatten([[1, 2], [3, 4]])  // [1, 2, 3, 4]
```

---

### flatMap(array: Array<T>, fn: (T) => Array<U>): Array<U>
Map and flatten.

```utlx
flatMap([1, 2, 3], n => [n, n * 2])  // [1, 2, 2, 4, 3, 6]
```

---

### distinct(array: Array<T>): Array<T>
Remove duplicates.

```utlx
distinct([1, 2, 2, 3, 3, 3])  // [1, 2, 3]
```

---

### groupBy(array: Array<T>, fn: (T) => K): Object
Group by key function.

```utlx
groupBy([{type: "A", val: 1}, {type: "B", val: 2}], item => item.type)
// {A: [{type: "A", val: 1}], B: [{type: "B", val: 2}]}
```

---

### contains(array: Array<T>, value: T): Boolean
Check if array contains value.

```utlx
contains([1, 2, 3], 2)  // true
contains([1, 2, 3], 5)  // false
```

---

## Math Functions

### abs(n: Number): Number
Absolute value.

```utlx
abs(-5)   // 5
abs(5)    // 5
```

---

### round(n: Number): Number
Round to nearest integer.

```utlx
round(3.7)  // 4
round(3.2)  // 3
```

---

### ceil(n: Number): Number
Round up.

```utlx
ceil(3.2)  // 4
ceil(3.9)  // 4
```

---

### floor(n: Number): Number
Round down.

```utlx
floor(3.9)  // 3
floor(3.2)  // 3
```

---

### pow(base: Number, exponent: Number): Number
Exponentiation.

```utlx
pow(2, 3)    // 8
pow(10, 2)   // 100
```

---

### sqrt(n: Number): Number
Square root.

```utlx
sqrt(16)  // 4
sqrt(2)   // 1.414...
```

---

### random(): Number
Random number between 0 and 1.

```utlx
random()  // 0.547... (random)
```

---

### min(a: Number, b: Number, ...): Number
Minimum of numbers.

```utlx
min(1, 5, 3)  // 1
```

---

### max(a: Number, b: Number, ...): Number
Maximum of numbers.

```utlx
max(1, 5, 3)  // 5
```

---

## Date Functions

### now(): Date
Current date and time.

```utlx
now()  // 2025-10-09T14:30:00Z
```

---

### parseDate(str: String, format: String): Date
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

---

### formatDate(date: Date, format: String): String
Format date as string.

```utlx
formatDate(now(), "yyyy-MM-dd")  // "2025-10-09"
```

---

### addDays(date: Date, days: Number): Date
Add days to date.

```utlx
addDays(now(), 7)   // 7 days from now
addDays(now(), -7)  // 7 days ago
```

---

### addMonths(date: Date, months: Number): Date
Add months to date.

```utlx
addMonths(now(), 3)  // 3 months from now
```

---

### addYears(date: Date, years: Number): Date
Add years to date.

```utlx
addYears(now(), 1)  // 1 year from now
```

---

### diffDays(date1: Date, date2: Date): Number
Difference in days.

```utlx
diffDays(parseDate("2025-10-15", "yyyy-MM-dd"), 
         parseDate("2025-10-09", "yyyy-MM-dd"))  // 6
```

---

## Type Functions

### typeOf(value: Any): String
Get type name.

```utlx
typeOf(42)          // "number"
typeOf("hello")     // "string"
typeOf(true)        // "boolean"
typeOf([1, 2])      // "array"
typeOf({a: 1})      // "object"
typeOf(null)        // "null"
```

---

### isString(value: Any): Boolean
Check if string.

```utlx
isString("hello")  // true
isString(42)       // false
```

---

### isNumber(value: Any): Boolean
Check if number.

```utlx
isNumber(42)    // true
isNumber("42")  // false
```

---

### isBoolean(value: Any): Boolean
Check if boolean.

```utlx
isBoolean(true)  // true
isBoolean(1)     // false
```

---

### isArray(value: Any): Boolean
Check if array.

```utlx
isArray([1, 2])     // true
isArray("array")    // false
```

---

### isObject(value: Any): Boolean
Check if object.

```utlx
isObject({a: 1})  // true
isObject([1, 2])  // false
```

---

### isNull(value: Any): Boolean
Check if null.

```utlx
isNull(null)  // true
isNull(0)     // false
```

---

### parseNumber(str: String): Number
Parse number from string.

```utlx
parseNumber("42")     // 42
parseNumber("3.14")   // 3.14
```

**Errors:** Throws error if string is not a valid number

---

### toString(value: Any): String
Convert to string.

```utlx
toString(42)      // "42"
toString(true)    // "true"
toString([1, 2])  // "[1, 2]"
```

---

## Object Functions

### keys(obj: Object): Array<String>
Get object keys.

```utlx
keys({name: "Alice", age: 30})  // ["name", "age"]
```

---

### values(obj: Object): Array<Any>
Get object values.

```utlx
values({name: "Alice", age: 30})  // ["Alice", 30]
```

---

### entries(obj: Object): Array<Array<Any>>
Get key-value pairs.

```utlx
entries({name: "Alice", age: 30})
// [["name", "Alice"], ["age", 30]]
```

---

### merge(obj1: Object, obj2: Object): Object
Merge objects (obj2 overwrites obj1).

```utlx
merge({a: 1}, {b: 2})              // {a: 1, b: 2}
merge({a: 1, b: 2}, {b: 3, c: 4})  // {a: 1, b: 3, c: 4}
```

---

### isEmpty(obj: Object): Boolean
Check if object is empty.

```utlx
isEmpty({})         // true
isEmpty({a: 1})     // false
```

---

## Utility Functions

### console.log(value: Any): Void
Log value to console (for debugging).

```utlx
console.log("Debug info")
console.log(input.customer)
```

---

### error(message: String): Never
Throw error with message.

```utlx
if (input.value < 0) {
  error("Value cannot be negative")
}
```

---

### assert(condition: Boolean, message: String): Void
Assert condition is true.

```utlx
assert(input.value > 0, "Value must be positive")
```

---
