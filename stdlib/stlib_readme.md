# UTL-X Standard Library

The UTL-X Standard Library provides built-in functions for data transformation operations. All functions are automatically available in UTL-X transformations without requiring imports.

## Categories

- **String Functions** - Text manipulation and formatting
- **Array Functions** - Collection operations (map, filter, reduce, etc.)
- **Math Functions** - Mathematical operations
- **Date Functions** - Date/time manipulation
- **Type Functions** - Type checking and conversion
- **Object Functions** - Object/map operations

---

## String Functions

### `upper(str: String): String`
Convert string to uppercase.
```utlx
upper("hello") // => "HELLO"
```

### `lower(str: String): String`
Convert string to lowercase.
```utlx
lower("HELLO") // => "hello"
```

### `trim(str: String): String`
Remove whitespace from both ends.
```utlx
trim("  hello  ") // => "hello"
```

### `substring(str: String, start: Number, end?: Number): String`
Extract substring from start index to end index (or end of string).
```utlx
substring("hello world", 0, 5) // => "hello"
substring("hello world", 6)    // => "world"
```

### `concat(...strings: String[]): String`
Concatenate multiple strings.
```utlx
concat("hello", " ", "world") // => "hello world"
```

### `split(str: String, delimiter: String): Array<String>`
Split string by delimiter.
```utlx
split("a,b,c", ",") // => ["a", "b", "c"]
```

### `join(array: Array, delimiter: String): String`
Join array elements with delimiter.
```utlx
join(["a", "b", "c"], ",") // => "a,b,c"
```

### `replace(str: String, search: String, replacement: String): String`
Replace occurrences in string.
```utlx
replace("hello world", "world", "there") // => "hello there"
```

### `contains(str: String, search: String): Boolean`
Check if string contains substring.
```utlx
contains("hello world", "world") // => true
```

### `startsWith(str: String, prefix: String): Boolean`
Check if string starts with prefix.
```utlx
startsWith("hello", "he") // => true
```

### `endsWith(str: String, suffix: String): Boolean`
Check if string ends with suffix.
```utlx
endsWith("hello", "lo") // => true
```

### `length(str: String): Number`
Get string length.
```utlx
length("hello") // => 5
```

### `matches(str: String, pattern: String): Boolean`
Check if string matches regex pattern.
```utlx
matches("hello123", "^[a-z]+[0-9]+$") // => true
```

### `replaceRegex(str: String, pattern: String, replacement: String): String`
Replace using regex pattern.
```utlx
replaceRegex("hello123world456", "[0-9]+", "X") // => "helloXworldX"
```

---

## Array Functions

### `map(array: Array, fn: Function): Array`
Transform each element.
```utlx
map([1, 2, 3], x => x * 2) // => [2, 4, 6]
```

### `filter(array: Array, fn: Function): Array`
Keep elements matching predicate.
```utlx
filter([1, 2, 3, 4], x => x > 2) // => [3, 4]
```

### `reduce(array: Array, fn: Function, initial: Any): Any`
Reduce array to single value.
```utlx
reduce([1, 2, 3, 4], (acc, x) => acc + x, 0) // => 10
```

### `find(array: Array, fn: Function): Any`
Find first matching element.
```utlx
find([1, 2, 3, 4], x => x > 2) // => 3
```

### `findIndex(array: Array, fn: Function): Number`
Find index of first matching element.
```utlx
findIndex([1, 2, 3, 4], x => x > 2) // => 2
```

### `every(array: Array, fn: Function): Boolean`
Check if all elements match.
```utlx
every([2, 4, 6], x => x % 2 == 0) // => true
```

### `some(array: Array, fn: Function): Boolean`
Check if any element matches.
```utlx
some([1, 2, 3], x => x > 2) // => true
```

### `flatten(array: Array): Array`
Flatten nested arrays one level.
```utlx
flatten([[1, 2], [3, 4]]) // => [1, 2, 3, 4]
```

### `reverse(array: Array): Array`
Reverse array order.
```utlx
reverse([1, 2, 3]) // => [3, 2, 1]
```

### `sort(array: Array): Array`
Sort array in natural order.
```utlx
sort([3, 1, 2]) // => [1, 2, 3]
```

### `sortBy(array: Array, fn: Function): Array`
Sort by key function.
```utlx
sortBy([{age: 30}, {age: 20}], x => x.age) // => [{age: 20}, {age: 30}]
```

### `first(array: Array): Any`
Get first element.
```utlx
first([1, 2, 3]) // => 1
```

### `last(array: Array): Any`
Get last element.
```utlx
last([1, 2, 3]) // => 3
```

### `take(array: Array, n: Number): Array`
Take first n elements.
```utlx
take([1, 2, 3, 4], 2) // => [1, 2]
```

### `drop(array: Array, n: Number): Array`
Drop first n elements.
```utlx
drop([1, 2, 3, 4], 2) // => [3, 4]
```

### `unique(array: Array): Array`
Get unique elements.
```utlx
unique([1, 2, 2, 3, 1]) // => [1, 2, 3]
```

### `zip(array1: Array, array2: Array): Array`
Zip two arrays together.
```utlx
zip([1, 2], ["a", "b"]) // => [[1, "a"], [2, "b"]]
```

---

## Aggregation Functions

### `sum(array: Array<Number>): Number`
Sum of all elements.
```utlx
sum([1, 2, 3, 4]) // => 10
```

### `avg(array: Array<Number>): Number`
Average of all elements.
```utlx
avg([1, 2, 3, 4]) // => 2.5
```

### `min(array: Array<Number>): Number`
Minimum value.
```utlx
min([3, 1, 4, 1, 5]) // => 1
```

### `max(array: Array<Number>): Number`
Maximum value.
```utlx
max([3, 1, 4, 1, 5]) // => 5
```

### `count(array: Array): Number`
Count of elements.
```utlx
count([1, 2, 3, 4]) // => 4
```

---

## Math Functions

### `abs(n: Number): Number`
Absolute value.
```utlx
abs(-5) // => 5
```

### `round(n: Number): Number`
Round to nearest integer.
```utlx
round(3.7) // => 4
```

### `ceil(n: Number): Number`
Round up to integer.
```utlx
ceil(3.2) // => 4
```

### `floor(n: Number): Number`
Round down to integer.
```utlx
floor(3.8) // => 3
```

### `pow(base: Number, exp: Number): Number`
Raise to power.
```utlx
pow(2, 3) // => 8
```

### `sqrt(n: Number): Number`
Square root.
```utlx
sqrt(16) // => 4
```

### `random(): Number`
Random number between 0 and 1.
```utlx
random() // => 0.547...
```

---

## Date Functions

### `now(): DateTime`
Current date/time.
```utlx
now() // => 2025-10-14T12:30:00Z
```

### `parseDate(str: String, format?: String): DateTime`
Parse date string.
```utlx
parseDate("2025-10-14T12:00:00Z")
```

### `formatDate(date: DateTime, format?: String): String`
Format date to string.
```utlx
formatDate(now(), "yyyy-MM-dd") // => "2025-10-14"
```

### `addDays(date: DateTime, days: Number): DateTime`
Add days to date.
```utlx
addDays(now(), 7) // => 7 days from now
```

### `addHours(date: DateTime, hours: Number): DateTime`
Add hours to date.
```utlx
addHours(now(), 2) // => 2 hours from now
```

### `diffDays(date1: DateTime, date2: DateTime): Number`
Days difference between dates.
```utlx
diffDays(date1, date2) // => 7.5
```

---

## Type Functions

### `typeOf(value: Any): String`
Get type name.
```utlx
typeOf("hello")  // => "string"
typeOf(42)       // => "number"
typeOf([1, 2])   // => "array"
```

### `isString(value: Any): Boolean`
Check if value is string.
```utlx
isString("hello") // => true
```

### `isNumber(value: Any): Boolean`
Check if value is number.
```utlx
isNumber(42) // => true
```

### `isBoolean(value: Any): Boolean`
Check if value is boolean.
```utlx
isBoolean(true) // => true
```

### `isArray(value: Any): Boolean`
Check if value is array.
```utlx
isArray([1, 2, 3]) // => true
```

### `isObject(value: Any): Boolean`
Check if value is object.
```utlx
isObject({name: "Alice"}) // => true
```

### `isNull(value: Any): Boolean`
Check if value is null.
```utlx
isNull(null) // => true
```

### `isDefined(value: Any): Boolean`
Check if value is not null.
```utlx
isDefined(42) // => true
```

---

## Object Functions

### `keys(obj: Object): Array<String>`
Get object keys.
```utlx
keys({name: "Alice", age: 30}) // => ["name", "age"]
```

### `values(obj: Object): Array`
Get object values.
```utlx
values({name: "Alice", age: 30}) // => ["Alice", 30]
```

### `entries(obj: Object): Array<[String, Any]>`
Get key-value pairs.
```utlx
entries({name: "Alice", age: 30}) // => [["name", "Alice"], ["age", 30]]
```

### `merge(...objects: Object[]): Object`
Merge objects (later objects override earlier).
```utlx
merge({a: 1}, {b: 2}, {a: 3}) // => {a: 3, b: 2}
```

### `pick(obj: Object, keys: Array<String>): Object`
Pick specific keys.
```utlx
pick({name: "Alice", age: 30, city: "NYC"}, ["name", "age"]) 
// => {name: "Alice", age: 30}
```

### `omit(obj: Object, keys: Array<String>): Object`
Omit specific keys.
```utlx
omit({name: "Alice", age: 30, city: "NYC"}, ["city"]) 
// => {name: "Alice", age: 30}
```

---

## Usage in Transformations

```utlx
%utlx 1.0
input json
output json
---
{
  // String functions
  name: upper(input.user.name),
  
  // Array functions
  total: sum(input.items.*.price),
  count: count(input.items),
  expensive: filter(input.items, item => item.price > 100),
  
  // Math functions
  rounded: round(input.subtotal),
  
  // Date functions
  processedAt: now(),
  dueDate: addDays(now(), 30),
  
  // Type functions
  hasEmail: isDefined(input.user.email),
  
  // Object functions
  userFields: keys(input.user)
}
```

---

## Building

```bash
# Build stdlib module
./gradlew :stdlib:build

# Run tests
./gradlew :stdlib:test

# Generate documentation
./gradlew :stdlib:dokkaHtml
```

## Testing

See tests in `stdlib/src/test/kotlin/` for comprehensive examples of each function.

## License

Dual-licensed under AGPL-3.0 / Commercial License.
