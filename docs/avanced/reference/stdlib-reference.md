# UTL-X Standard Library - Complete Function Reference

**Version:** 1.1  
**Last Updated:** October 17, 2025  
**Total Functions:** 247+

This document provides comprehensive documentation for all functions in the UTL-X standard library, organized by category.

---

## Table of Contents

1. [String Functions](#string-functions) (40+ functions)
2. [Array Functions](#array-functions) (35+ functions)
3. [Math Functions](#math-functions) (25+ functions)
4. [Date/Time Functions](#datetime-functions) (35+ functions)
5. [Object Functions](#object-functions) (20+ functions)
6. [Type Functions](#type-functions) (15+ functions)
7. [Binary Functions](#binary-functions) (20+ functions)
8. [Encoding & Crypto Functions](#encoding--crypto-functions) (15+ functions)
9. [JSON Functions](#json-functions) (10+ functions)
10. [XML Functions](#xml-functions) (25+ functions)
11. [CSV Functions](#csv-functions) (8+ functions)
12. [YAML Functions](#yaml-functions) (18+ functions)
13. [Geospatial Functions](#geospatial-functions) (8 functions)
14. [Financial Functions](#financial-functions) (10+ functions)
15. [Logical Functions](#logical-functions) (10+ functions)
16. [URL Functions](#url-functions) (8+ functions)
17. [JWT/JWS Functions](#jwtjws-functions) (10+ functions)
18. [Utility Functions](#utility-functions) (10+ functions)

---

## String Functions

### Basic Operations

#### `upper(str: String): String`
Convert string to uppercase.

**Example:**
```utlx
upper("hello")           // "HELLO"
upper("Hello World!")    // "HELLO WORLD!"
```

#### `lower(str: String): String`
Convert string to lowercase.

**Example:**
```utlx
lower("HELLO")           // "hello"
lower("Hello World!")    // "hello world!"
```

#### `capitalize(str: String): String`
Capitalize first letter of string.

**Example:**
```utlx
capitalize("hello")      // "Hello"
capitalize("world")      // "World"
```

#### `trim(str: String): String`
Remove leading and trailing whitespace.

**Example:**
```utlx
trim("  hello  ")        // "hello"
trim("\n\ttext\t\n")     // "text"
```

#### `trimStart(str: String): String`
Remove leading whitespace.

**Example:**
```utlx
trimStart("  hello")     // "hello"
```

#### `trimEnd(str: String): String`
Remove trailing whitespace.

**Example:**
```utlx
trimEnd("hello  ")       // "hello"
```

#### `substring(str: String, start: Number, end: Number): String`
Extract substring from start to end index.

**Example:**
```utlx
substring("hello", 0, 3)     // "hel"
substring("hello", 2, 5)     // "llo"
substring("hello", 1)        // "ello" (to end)
```

#### `charAt(str: String, index: Number): String`
Get character at specific index.

**Example:**
```utlx
charAt("hello", 0)       // "h"
charAt("hello", 4)       // "o"
```

#### `length(str: String): Number`
Get string length.

**Example:**
```utlx
length("hello")          // 5
length("")               // 0
```

### String Manipulation

#### `concat(str1: String, str2: String, ...): String`
Concatenate multiple strings.

**Example:**
```utlx
concat("Hello", " ", "World")    // "Hello World"
concat("a", "b", "c")            // "abc"
```

#### `repeat(str: String, count: Number): String`
Repeat string n times.

**Example:**
```utlx
repeat("ha", 3)          // "hahaha"
repeat("-", 5)           // "-----"
```

#### `reverse(str: String): String`
Reverse string.

**Example:**
```utlx
reverse("hello")         // "olleh"
reverse("12345")         // "54321"
```

#### `replace(str: String, search: String, replacement: String): String`
Replace first occurrence of search string.

**Example:**
```utlx
replace("hello world", "world", "there")    // "hello there"
replace("aaa", "a", "b")                     // "baa"
```

#### `replaceAll(str: String, search: String, replacement: String): String`
Replace all occurrences of search string.

**Example:**
```utlx
replaceAll("aaa", "a", "b")      // "bbb"
replaceAll("hello hello", "hello", "hi")    // "hi hi"
```

#### `split(str: String, delimiter: String): Array<String>`
Split string into array by delimiter.

**Example:**
```utlx
split("a,b,c", ",")              // ["a", "b", "c"]
split("one two three", " ")      // ["one", "two", "three"]
```

#### `join(array: Array<String>, delimiter: String): String`
Join array elements into string with delimiter.

**Example:**
```utlx
join(["a", "b", "c"], ",")       // "a,b,c"
join(["one", "two"], " ")        // "one two"
```

### String Searching

#### `startsWith(str: String, prefix: String): Boolean`
Check if string starts with prefix.

**Example:**
```utlx
startsWith("hello", "hel")       // true
startsWith("world", "wor")       // true
startsWith("hello", "bye")       // false
```

#### `endsWith(str: String, suffix: String): Boolean`
Check if string ends with suffix.

**Example:**
```utlx
endsWith("hello", "llo")         // true
endsWith("world", "rld")         // true
endsWith("hello", "bye")         // false
```

#### `contains(str: String, substring: String): Boolean`
Check if string contains substring.

**Example:**
```utlx
contains("hello world", "world")     // true
contains("hello", "bye")             // false
```

#### `indexOf(str: String, substring: String): Number`
Find first index of substring, returns -1 if not found.

**Example:**
```utlx
indexOf("hello", "l")            // 2
indexOf("hello", "o")            // 4
indexOf("hello", "x")            // -1
```

#### `lastIndexOf(str: String, substring: String): Number`
Find last index of substring.

**Example:**
```utlx
lastIndexOf("hello", "l")        // 3
lastIndexOf("hello hello", "hello")  // 6
```

### Case Conversion

#### `titleCase(str: String): String`
Convert to title case (capitalize first letter of each word).

**Example:**
```utlx
titleCase("hello world")         // "Hello World"
titleCase("the quick brown fox") // "The Quick Brown Fox"
```

#### `camelCase(str: String): String`
Convert to camelCase.

**Example:**
```utlx
camelCase("hello world")         // "helloWorld"
camelCase("first-name")          // "firstName"
```

#### `snakeCase(str: String): String`
Convert to snake_case.

**Example:**
```utlx
snakeCase("helloWorld")          // "hello_world"
snakeCase("firstName")           // "first_name"
```

#### `kebabCase(str: String): String`
Convert to kebab-case.

**Example:**
```utlx
kebabCase("helloWorld")          // "hello-world"
kebabCase("firstName")           // "first-name"
```

### Regular Expressions

#### `matches(str: String, pattern: String): Boolean`
Test if string matches regex pattern.

**Example:**
```utlx
matches("hello", "^h")           // true
matches("test@example.com", ".*@.*\\..*")  // true
matches("123", "\\d+")           // true
```

#### `scan(str: String, pattern: String): Array<String>`
Find all matches of regex pattern.

**Example:**
```utlx
scan("abc123def456", "\\d+")     // ["123", "456"]
scan("hello world", "\\w+")      // ["hello", "world"]
```

#### `regexReplace(str: String, pattern: String, replacement: String): String`
Replace using regex pattern.

**Example:**
```utlx
regexReplace("hello123world456", "\\d+", "X")    // "helloXworldX"
```

### Advanced Regex (v1.1+)

#### `lookahead(str: String, pattern: String): Boolean`
Positive lookahead regex match.

**Example:**
```utlx
lookahead("hello world", "hello(?=\\s)")     // true
```

#### `lookbehind(str: String, pattern: String): Boolean`
Positive lookbehind regex match.

**Example:**
```utlx
lookbehind("hello world", "(?<=hello\\s)world")  // true
```

#### `namedGroups(str: String, pattern: String): Object`
Extract named capture groups from regex.

**Example:**
```utlx
namedGroups("John Doe", "(?<first>\\w+)\\s(?<last>\\w+)")
// {first: "John", last: "Doe"}
```

### String Analysis

#### `isAlpha(str: String): Boolean`
Check if string contains only alphabetic characters.

**Example:**
```utlx
isAlpha("hello")         // true
isAlpha("hello123")      // false
```

#### `isNumeric(str: String): Boolean`
Check if string contains only numeric characters.

**Example:**
```utlx
isNumeric("123")         // true
isNumeric("12.34")       // false
```

#### `isAlphanumeric(str: String): Boolean`
Check if string contains only alphanumeric characters.

**Example:**
```utlx
isAlphanumeric("hello123")   // true
isAlphanumeric("hello-123")  // false
```

#### `isBlank(str: String): Boolean`
Check if string is empty or contains only whitespace.

**Example:**
```utlx
isBlank("")              // true
isBlank("   ")           // true
isBlank("hello")         // false
```

#### `pluralize(str: String): String`
Convert singular word to plural.

**Example:**
```utlx
pluralize("cat")         // "cats"
pluralize("person")      // "people"
pluralize("child")       // "children"
```

#### `singularize(str: String): String`
Convert plural word to singular.

**Example:**
```utlx
singularize("cats")      // "cat"
singularize("people")    // "person"
```

---

## Array Functions

### Basic Operations

#### `size(array: Array): Number`
Get array length.

**Example:**
```utlx
size([1, 2, 3])          // 3
size([])                 // 0
```

#### `get(array: Array, index: Number): Any`
Get element at index.

**Example:**
```utlx
get([1, 2, 3], 0)        // 1
get([1, 2, 3], 2)        // 3
```

#### `head(array: Array): Any`
Get first element.

**Example:**
```utlx
head([1, 2, 3])          // 1
head(["a", "b"])         // "a"
```

#### `tail(array: Array): Array`
Get all elements except first.

**Example:**
```utlx
tail([1, 2, 3])          // [2, 3]
tail([1])                // []
```

#### `first(array: Array): Any`
Get first element (alias for head).

**Example:**
```utlx
first([1, 2, 3])         // 1
```

#### `last(array: Array): Any`
Get last element.

**Example:**
```utlx
last([1, 2, 3])          // 3
last(["a", "b"])         // "b"
```

#### `take(array: Array, n: Number): Array`
Take first n elements.

**Example:**
```utlx
take([1, 2, 3, 4], 2)    // [1, 2]
take([1, 2, 3], 5)       // [1, 2, 3]
```

#### `drop(array: Array, n: Number): Array`
Drop first n elements.

**Example:**
```utlx
drop([1, 2, 3, 4], 2)    // [3, 4]
drop([1, 2, 3], 1)       // [2, 3]
```

### Transformation

#### `map(array: Array, fn: Function): Array`
Transform each element using function.

**Example:**
```utlx
map([1, 2, 3], x => x * 2)           // [2, 4, 6]
map(["a", "b"], x => upper(x))       // ["A", "B"]
```

#### `filter(array: Array, fn: Function): Array`
Keep only elements matching predicate.

**Example:**
```utlx
filter([1, 2, 3, 4], x => x > 2)     // [3, 4]
filter(["a", "ab", "abc"], x => length(x) > 1)  // ["ab", "abc"]
```

#### `reduce(array: Array, fn: Function, initial: Any): Any`
Reduce array to single value.

**Example:**
```utlx
reduce([1, 2, 3], (acc, x) => acc + x, 0)    // 6
reduce([1, 2, 3], (acc, x) => acc * x, 1)    // 6
```

#### `flatMap(array: Array, fn: Function): Array`
Map then flatten results.

**Example:**
```utlx
flatMap([1, 2, 3], x => [x, x * 2])  // [1, 2, 2, 4, 3, 6]
```

#### `flatten(array: Array, depth: Number): Array`
Flatten nested arrays by depth levels.

**Example:**
```utlx
flatten([[1, 2], [3, 4]])            // [1, 2, 3, 4]
flatten([[[1]], [[2]]], 2)           // [1, 2]
```

### Sorting and Ordering

#### `sort(array: Array): Array`
Sort array in natural order.

**Example:**
```utlx
sort([3, 1, 2])          // [1, 2, 3]
sort(["c", "a", "b"])    // ["a", "b", "c"]
```

#### `sortBy(array: Array, fn: Function): Array`
Sort by function result.

**Example:**
```utlx
sortBy([{x: 2}, {x: 1}], obj => obj.x)   // [{x: 1}, {x: 2}]
sortBy(["abc", "a", "ab"], x => length(x))  // ["a", "ab", "abc"]
```

#### `reverse(array: Array): Array`
Reverse array order.

**Example:**
```utlx
reverse([1, 2, 3])       // [3, 2, 1]
reverse(["a", "b"])      // ["b", "a"]
```

### Set Operations

#### `distinct(array: Array): Array`
Remove duplicates.

**Example:**
```utlx
distinct([1, 2, 2, 3])   // [1, 2, 3]
distinct(["a", "b", "a"])    // ["a", "b"]
```

#### `distinctBy(array: Array, fn: Function): Array`
Remove duplicates by function result.

**Example:**
```utlx
distinctBy([{x: 1}, {x: 1}, {x: 2}], obj => obj.x)  // [{x: 1}, {x: 2}]
```

#### `union(array1: Array, array2: Array): Array`
Combine arrays removing duplicates.

**Example:**
```utlx
union([1, 2], [2, 3])    // [1, 2, 3]
```

#### `intersect(array1: Array, array2: Array): Array`
Get common elements.

**Example:**
```utlx
intersect([1, 2, 3], [2, 3, 4])  // [2, 3]
```

#### `diff(array1: Array, array2: Array): Array`
Get elements in first array not in second.

**Example:**
```utlx
diff([1, 2, 3], [2, 3, 4])   // [1]
```

### Aggregation

#### `sum(array: Array<Number>): Number`
Sum all numbers in array.

**Example:**
```utlx
sum([1, 2, 3, 4])        // 10
sum([])                  // 0
```

#### `avg(array: Array<Number>): Number`
Calculate average.

**Example:**
```utlx
avg([1, 2, 3, 4])        // 2.5
avg([10, 20])            // 15
```

#### `min(array: Array<Number>): Number`
Find minimum value.

**Example:**
```utlx
min([3, 1, 2])           // 1
min([10, 5, 20])         // 5
```

#### `max(array: Array<Number>): Number`
Find maximum value.

**Example:**
```utlx
max([3, 1, 2])           // 3
max([10, 5, 20])         // 20
```

#### `count(array: Array): Number`
Count elements (alias for size).

**Example:**
```utlx
count([1, 2, 3])         // 3
```

### Searching

#### `find(array: Array, fn: Function): Any`
Find first element matching predicate.

**Example:**
```utlx
find([1, 2, 3, 4], x => x > 2)   // 3
```

#### `findIndex(array: Array, fn: Function): Number`
Find index of first matching element.

**Example:**
```utlx
findIndex([1, 2, 3, 4], x => x > 2)  // 2
```

#### `contains(array: Array, value: Any): Boolean`
Check if array contains value.

**Example:**
```utlx
contains([1, 2, 3], 2)   // true
contains([1, 2, 3], 5)   // false
```

#### `every(array: Array, fn: Function): Boolean`
Check if all elements match predicate.

**Example:**
```utlx
every([2, 4, 6], x => x % 2 == 0)    // true
every([1, 2, 3], x => x > 0)         // true
```

#### `some(array: Array, fn: Function): Boolean`
Check if any element matches predicate.

**Example:**
```utlx
some([1, 2, 3], x => x > 2)      // true
some([1, 2, 3], x => x > 5)      // false
```

#### `none(array: Array, fn: Function): Boolean`
Check if no elements match predicate.

**Example:**
```utlx
none([1, 2, 3], x => x > 5)      // true
none([1, 2, 3], x => x > 0)      // false
```

#### `isEmpty(array: Array): Boolean`
Check if array is empty.

**Example:**
```utlx
isEmpty([])              // true
isEmpty([1])             // false
```

### Advanced Operations

#### `zip(array1: Array, array2: Array): Array`
Combine two arrays into pairs.

**Example:**
```utlx
zip([1, 2], ["a", "b"])  // [[1, "a"], [2, "b"]]
```

#### `unzip(array: Array): Array`
Split array of pairs into two arrays.

**Example:**
```utlx
unzip([[1, "a"], [2, "b"]])  // [[1, 2], ["a", "b"]]
```

#### `partition(array: Array, fn: Function): Array`
Split array into two based on predicate.

**Example:**
```utlx
partition([1, 2, 3, 4], x => x % 2 == 0)  // [[2, 4], [1, 3]]
```

#### `groupBy(array: Array, fn: Function): Object`
Group elements by function result.

**Example:**
```utlx
groupBy([1, 2, 3, 4], x => x % 2)
// {0: [2, 4], 1: [1, 3]}
```

#### `chunk(array: Array, size: Number): Array`
Split array into chunks of specified size.

**Example:**
```utlx
chunk([1, 2, 3, 4, 5], 2)    // [[1, 2], [3, 4], [5]]
```

#### `pluck(array: Array<Object>, key: String): Array`
Extract property from each object.

**Example:**
```utlx
pluck([{name: "John"}, {name: "Jane"}], "name")  // ["John", "Jane"]
```

#### `orderBy(array: Array, keys: Array<String>, orders: Array<String>): Array`
Sort by multiple keys and directions.

**Example:**
```utlx
orderBy(users, ["age", "name"], ["asc", "desc"])
```

---

## Math Functions

### Basic Arithmetic

#### `abs(n: Number): Number`
Absolute value.

**Example:**
```utlx
abs(-5)                  // 5
abs(3.14)                // 3.14
```

#### `ceil(n: Number): Number`
Round up to nearest integer.

**Example:**
```utlx
ceil(4.3)                // 5
ceil(-4.7)               // -4
```

#### `floor(n: Number): Number`
Round down to nearest integer.

**Example:**
```utlx
floor(4.7)               // 4
floor(-4.3)              // -5
```

#### `round(n: Number, decimals: Number): Number`
Round to specified decimal places.

**Example:**
```utlx
round(3.14159, 2)        // 3.14
round(2.5)               // 3
```

#### `pow(base: Number, exponent: Number): Number`
Raise to power.

**Example:**
```utlx
pow(2, 3)                // 8
pow(10, 2)               // 100
```

#### `sqrt(n: Number): Number`
Square root.

**Example:**
```utlx
sqrt(16)                 // 4
sqrt(2)                  // 1.414...
```

#### `mod(a: Number, b: Number): Number`
Modulo operation.

**Example:**
```utlx
mod(10, 3)               // 1
mod(15, 4)               // 3
```

### Trigonometry

#### `sin(n: Number): Number`
Sine (radians).

**Example:**
```utlx
sin(0)                   // 0
sin(PI / 2)              // 1
```

#### `cos(n: Number): Number`
Cosine (radians).

**Example:**
```utlx
cos(0)                   // 1
cos(PI)                  // -1
```

#### `tan(n: Number): Number`
Tangent (radians).

**Example:**
```utlx
tan(0)                   // 0
```

#### `asin(n: Number): Number`
Arc sine.

#### `acos(n: Number): Number`
Arc cosine.

#### `atan(n: Number): Number`
Arc tangent.

#### `atan2(y: Number, x: Number): Number`
Two-argument arc tangent.

### Logarithms

#### `log(n: Number): Number`
Natural logarithm (base e).

**Example:**
```utlx
log(E)                   // 1
log(1)                   // 0
```

#### `log10(n: Number): Number`
Base-10 logarithm.

**Example:**
```utlx
log10(100)               // 2
log10(1000)              // 3
```

#### `exp(n: Number): Number`
e raised to power.

**Example:**
```utlx
exp(0)                   // 1
exp(1)                   // 2.718...
```

### Statistical

#### `mean(array: Array<Number>): Number`
Arithmetic mean (alias for avg).

#### `median(array: Array<Number>): Number`
Middle value when sorted.

**Example:**
```utlx
median([1, 2, 3, 4, 5])  // 3
median([1, 2, 3, 4])     // 2.5
```

#### `mode(array: Array<Number>): Number`
Most frequent value.

**Example:**
```utlx
mode([1, 2, 2, 3])       // 2
```

#### `stddev(array: Array<Number>): Number`
Standard deviation.

**Example:**
```utlx
stddev([2, 4, 4, 4, 5, 5, 7, 9])  // 2.138...
```

#### `variance(array: Array<Number>): Number`
Variance.

**Example:**
```utlx
variance([2, 4, 4, 4, 5, 5, 7, 9])  // 4.571...
```

### Random

#### `random(): Number`
Random number between 0 and 1.

**Example:**
```utlx
random()                 // 0.437... (random)
```

#### `randomInt(min: Number, max: Number): Number`
Random integer between min and max (inclusive).

**Example:**
```utlx
randomInt(1, 10)         // 7 (random)
```

### Constants

- `PI` = 3.14159265359...
- `E` = 2.71828182846...
- `PHI` = 1.61803398875... (golden ratio)

---

## Date/Time Functions

### Current Date/Time

#### `now(): Date`
Get current date and time.

**Example:**
```utlx
now()  // 2025-10-17T14:30:00Z
```

#### `today(): Date`
Get current date (midnight).

**Example:**
```utlx
today()  // 2025-10-17T00:00:00Z
```

#### `currentTime(): String`
Get current time as string.

**Example:**
```utlx
currentTime()  // "14:30:00"
```

### Parsing and Formatting

#### `parseDate(str: String, format: String): Date`
Parse string to date using format.

**Example:**
```utlx
parseDate("2025-10-17", "yyyy-MM-dd")
parseDate("17/10/2025", "dd/MM/yyyy")
```

**Format Patterns:**
- `yyyy` - 4-digit year
- `MM` - 2-digit month
- `dd` - 2-digit day
- `HH` - 2-digit hour (24h)
- `mm` - 2-digit minute
- `ss` - 2-digit second

#### `formatDate(date: Date, format: String): String`
Format date as string.

**Example:**
```utlx
formatDate(now(), "yyyy-MM-dd")              // "2025-10-17"
formatDate(now(), "MMMM dd, yyyy")           // "October 17, 2025"
formatDate(now(), "dd/MM/yyyy HH:mm:ss")     // "17/10/2025 14:30:00"
```

#### `toISO8601(date: Date): String`
Convert to ISO 8601 format.

**Example:**
```utlx
toISO8601(now())  // "2025-10-17T14:30:00Z"
```

#### `fromISO8601(str: String): Date`
Parse ISO 8601 string.

**Example:**
```utlx
fromISO8601("2025-10-17T14:30:00Z")
```

### Date Arithmetic

#### `addDays(date: Date, days: Number): Date`
Add days to date.

**Example:**
```utlx
addDays(today(), 7)      // 7 days from today
addDays(today(), -1)     // yesterday
```

#### `addMonths(date: Date, months: Number): Date`
Add months to date.

**Example:**
```utlx
addMonths(today(), 1)    // next month
addMonths(today(), -3)   // 3 months ago
```

#### `addYears(date: Date, years: Number): Date`
Add years to date.

**Example:**
```utlx
addYears(today(), 1)     // next year
```

#### `addHours(date: Date, hours: Number): Date`
Add hours to date/time.

**Example:**
```utlx
addHours(now(), 2)       // 2 hours from now
```

#### `addMinutes(date: Date, minutes: Number): Date`
Add minutes to date/time.

**Example:**
```utlx
addMinutes(now(), 30)    // 30 minutes from now
```

#### `addSeconds(date: Date, seconds: Number): Date`
Add seconds to date/time.

**Example:**
```utlx
addSeconds(now(), 60)    // 60 seconds from now
```

### Date Difference

#### `diffDays(date1: Date, date2: Date): Number`
Calculate difference in days.

**Example:**
```utlx
diffDays(today(), addDays(today(), 7))  // 7
```

#### `diffMonths(date1: Date, date2: Date): Number`
Calculate difference in months.

#### `diffYears(date1: Date, date2: Date): Number`
Calculate difference in years.

#### `diffHours(date1: Date, date2: Date): Number`
Calculate difference in hours.

#### `diffMinutes(date1: Date, date2: Date): Number`
Calculate difference in minutes.

#### `diffSeconds(date1: Date, date2: Date): Number`
Calculate difference in seconds.

### Date Components

#### `year(date: Date): Number`
Extract year.

**Example:**
```utlx
year(now())              // 2025
```

#### `month(date: Date): Number`
Extract month (1-12).

**Example:**
```utlx
month(now())             // 10
```

#### `day(date: Date): Number`
Extract day of month (1-31).

**Example:**
```utlx
day(now())               // 17
```

#### `hour(date: Date): Number`
Extract hour (0-23).

**Example:**
```utlx
hour(now())              // 14
```

#### `minute(date: Date): Number`
Extract minute (0-59).

**Example:**
```utlx
minute(now())            // 30
```

#### `second(date: Date): Number`
Extract second (0-59).

**Example:**
```utlx
second(now())            // 0
```

#### `dayOfWeek(date: Date): Number`
Get day of week (1=Monday, 7=Sunday).

**Example:**
```utlx
dayOfWeek(now())         // 5 (Friday)
```

#### `dayOfYear(date: Date): Number`
Get day of year (1-366).

**Example:**
```utlx
dayOfYear(now())         // 290
```

#### `weekOfYear(date: Date): Number`
Get week of year (1-53).

**Example:**
```utlx
weekOfYear(now())        // 42
```

### Date Manipulation

#### `startOfDay(date: Date): Date`
Get start of day (midnight).

**Example:**
```utlx
startOfDay(now())        // 2025-10-17T00:00:00Z
```

#### `endOfDay(date: Date): Date`
Get end of day.

**Example:**
```utlx
endOfDay(now())          // 2025-10-17T23:59:59.999Z
```

#### `startOfMonth(date: Date): Date`
Get first day of month.

**Example:**
```utlx
startOfMonth(now())      // 2025-10-01T00:00:00Z
```

#### `endOfMonth(date: Date): Date`
Get last day of month.

**Example:**
```utlx
endOfMonth(now())        // 2025-10-31T23:59:59.999Z
```

#### `startOfYear(date: Date): Date`
Get first day of year.

**Example:**
```utlx
startOfYear(now())       // 2025-01-01T00:00:00Z
```

#### `endOfYear(date: Date): Date`
Get last day of year.

**Example:**
```utlx
endOfYear(now())         // 2025-12-31T23:59:59.999Z
```

### Timezone Functions

#### `toTimezone(date: Date, timezone: String): Date`
Convert date to timezone.

**Example:**
```utlx
toTimezone(now(), "America/New_York")
toTimezone(now(), "Europe/London")
toTimezone(now(), "Asia/Tokyo")
```

#### `timezoneOffset(timezone: String): Number`
Get timezone offset in minutes.

**Example:**
```utlx
timezoneOffset("America/New_York")  // -300 (UTC-5)
```

#### `toUTC(date: Date): Date`
Convert to UTC timezone.

**Example:**
```utlx
toUTC(now())
```

### Date Validation

#### `isLeapYear(year: Number): Boolean`
Check if year is leap year.

**Example:**
```utlx
isLeapYear(2024)         // true
isLeapYear(2025)         // false
```

#### `isWeekend(date: Date): Boolean`
Check if date is Saturday or Sunday.

**Example:**
```utlx
isWeekend(now())
```

#### `isBusinessDay(date: Date): Boolean`
Check if date is Monday-Friday.

**Example:**
```utlx
isBusinessDay(now())
```

---

## Object Functions

### Property Access

#### `keys(obj: Object): Array<String>`
Get all property keys.

**Example:**
```utlx
keys({name: "John", age: 30})  // ["name", "age"]
```

#### `values(obj: Object): Array`
Get all property values.

**Example:**
```utlx
values({name: "John", age: 30})  // ["John", 30]
```

#### `entries(obj: Object): Array`
Get key-value pairs.

**Example:**
```utlx
entries({name: "John", age: 30})
// [["name", "John"], ["age", 30]]
```

#### `get(obj: Object, key: String): Any`
Get property value by key.

**Example:**
```utlx
get({name: "John"}, "name")  // "John"
```

#### `has(obj: Object, key: String): Boolean`
Check if object has property.

**Example:**
```utlx
has({name: "John"}, "name")  // true
has({name: "John"}, "age")   // false
```

### Object Manipulation

#### `merge(obj1: Object, obj2: Object): Object`
Merge two objects (shallow).

**Example:**
```utlx
merge({a: 1}, {b: 2})  // {a: 1, b: 2}
merge({a: 1}, {a: 2})  // {a: 2} (second wins)
```

#### `deepMerge(obj1: Object, obj2: Object): Object`
Deep merge objects.

**Example:**
```utlx
deepMerge(
  {user: {name: "John"}},
  {user: {age: 30}}
)
// {user: {name: "John", age: 30}}
```

#### `pick(obj: Object, keys: Array<String>): Object`
Select only specified keys.

**Example:**
```utlx
pick({name: "John", age: 30, city: "NYC"}, ["name", "age"])
// {name: "John", age: 30}
```

#### `omit(obj: Object, keys: Array<String>): Object`
Remove specified keys.

**Example:**
```utlx
omit({name: "John", age: 30, city: "NYC"}, ["age"])
// {name: "John", city: "NYC"}
```

#### `mapValues(obj: Object, fn: Function): Object`
Transform all values.

**Example:**
```utlx
mapValues({a: 1, b: 2}, v => v * 2)  // {a: 2, b: 4}
```

#### `mapKeys(obj: Object, fn: Function): Object`
Transform all keys.

**Example:**
```utlx
mapKeys({name: "John"}, k => upper(k))  // {NAME: "John"}
```

#### `filterObject(obj: Object, fn: Function): Object`
Filter object by predicate.

**Example:**
```utlx
filterObject({a: 1, b: 2, c: 3}, v => v > 1)  // {b: 2, c: 3}
```

### Object Analysis

#### `isEmpty(obj: Object): Boolean`
Check if object has no properties.

**Example:**
```utlx
isEmpty({})              // true
isEmpty({a: 1})          // false
```

#### `size(obj: Object): Number`
Count properties.

**Example:**
```utlx
size({a: 1, b: 2})       // 2
```

#### `isEqual(obj1: Object, obj2: Object): Boolean`
Deep equality check.

**Example:**
```utlx
isEqual({a: 1}, {a: 1})  // true
isEqual({a: 1}, {a: 2})  // false
```

### Conversion

#### `fromEntries(array: Array): Object`
Create object from key-value pairs.

**Example:**
```utlx
fromEntries([["name", "John"], ["age", 30]])
// {name: "John", age: 30}
```

#### `toJSON(obj: Object): String`
Convert object to JSON string.

**Example:**
```utlx
toJSON({name: "John"})   // '{"name":"John"}'
```

#### `fromJSON(str: String): Object`
Parse JSON string to object.

**Example:**
```utlx
fromJSON('{"name":"John"}')  // {name: "John"}
```

---

## Type Functions

### Type Checking

#### `getType(value: Any): String`
Get type name.

**Example:**
```utlx
getType("hello")          // "String"
getType(123)              // "Number"
getType(true)             // "Boolean"
getType([1, 2])           // "Array"
getType({a: 1})           // "Object"
getType(null)             // "Null"
```

#### `isString(value: Any): Boolean`
Check if value is string.

**Example:**
```utlx
isString("hello")        // true
isString(123)            // false
```

#### `isNumber(value: Any): Boolean`
Check if value is number.

**Example:**
```utlx
isNumber(123)            // true
isNumber("123")          // false
```

#### `isBoolean(value: Any): Boolean`
Check if value is boolean.

**Example:**
```utlx
isBoolean(true)          // true
isBoolean(1)             // false
```

#### `isArray(value: Any): Boolean`
Check if value is array.

**Example:**
```utlx
isArray([1, 2])          // true
isArray({a: 1})          // false
```

#### `isObject(value: Any): Boolean`
Check if value is object.

**Example:**
```utlx
isObject({a: 1})         // true
isObject([1, 2])         // false
```

#### `isNull(value: Any): Boolean`
Check if value is null.

**Example:**
```utlx
isNull(null)             // true
isNull(undefined)        // false
```

#### `isDate(value: Any): Boolean`
Check if value is date.

**Example:**
```utlx
isDate(now())            // true
isDate("2025-10-17")     // false
```

### Type Conversion

#### `toString(value: Any): String`
Convert value to string.

**Example:**
```utlx
toString(123)            // "123"
toString(true)           // "true"
toString([1, 2])         // "[1, 2]"
```

#### `toNumber(value: Any): Number`
Convert value to number.

**Example:**
```utlx
toNumber("123")          // 123
toNumber("3.14")         // 3.14
toNumber("abc")          // null (invalid)
```

#### `toBoolean(value: Any): Boolean`
Convert value to boolean.

**Example:**
```utlx
toBoolean(1)             // true
toBoolean(0)             // false
toBoolean("true")        // true
toBoolean("false")       // false
toBoolean("")            // false
```

#### `toArray(value: Any): Array`
Convert value to array.

**Example:**
```utlx
toArray("hello")         // ["h", "e", "l", "l", "o"]
toArray(123)             // [123]
```

### Advanced Type Operations

#### `coerce(value: Any, type: String): Any`
Coerce value to specified type.

**Example:**
```utlx
coerce("123", "Number")  // 123
coerce(123, "String")    // "123"
```

#### `cast(value: Any, type: String): Any`
Cast value to type (strict).

**Example:**
```utlx
cast("123", "Number")    // 123
cast("abc", "Number")    // throws error
```

---

## Binary Functions

### Encoding

#### `toBase64(data: Binary): String`
Encode binary data as Base64.

**Example:**
```utlx
toBase64("hello".bytes)  // "aGVsbG8="
```

#### `fromBase64(str: String): Binary`
Decode Base64 string.

**Example:**
```utlx
fromBase64("aGVsbG8=")   // hello (binary)
```

#### `toHex(data: Binary): String`
Encode as hexadecimal.

**Example:**
```utlx
toHex("hello".bytes)     // "68656c6c6f"
```

#### `fromHex(str: String): Binary`
Decode hexadecimal string.

**Example:**
```utlx
fromHex("68656c6c6f")    // hello (binary)
```

### Hashing

#### `md5(data: Binary): String`
Calculate MD5 hash.

**Example:**
```utlx
md5("hello".bytes)       // "5d41402abc4b2a76b9719d911017c592"
```

#### `sha1(data: Binary): String`
Calculate SHA-1 hash.

**Example:**
```utlx
sha1("hello".bytes)
```

#### `sha256(data: Binary): String`
Calculate SHA-256 hash.

**Example:**
```utlx
sha256("hello".bytes)
```

#### `sha512(data: Binary): String`
Calculate SHA-512 hash.

**Example:**
```utlx
sha512("hello".bytes)
```

### Compression

#### `gzip(data: Binary): Binary`
Compress using gzip.

**Example:**
```utlx
gzip("large text...".bytes)
```

#### `gunzip(data: Binary): Binary`
Decompress gzip data.

**Example:**
```utlx
gunzip(compressed)
```

#### `deflate(data: Binary): Binary`
Compress using deflate.

**Example:**
```utlx
deflate("data".bytes)
```

#### `inflate(data: Binary): Binary`
Decompress deflate data.

**Example:**
```utlx
inflate(compressed)
```

### HMAC

#### `hmacSHA256(data: Binary, key: Binary): String`
Calculate HMAC-SHA256.

**Example:**
```utlx
hmacSHA256("message".bytes, "secret".bytes)
```

#### `hmacSHA512(data: Binary, key: Binary): String`
Calculate HMAC-SHA512.

---

## Encoding & Crypto Functions

### URL Encoding

#### `urlEncode(str: String): String`
URL-encode string.

**Example:**
```utlx
urlEncode("hello world")     // "hello%20world"
urlEncode("a+b=c")           // "a%2Bb%3Dc"
```

#### `urlDecode(str: String): String`
URL-decode string.

**Example:**
```utlx
urlDecode("hello%20world")   // "hello world"
```

### HTML Encoding

#### `htmlEncode(str: String): String`
HTML-encode special characters.

**Example:**
```utlx
htmlEncode("<div>")          // "&lt;div&gt;"
htmlEncode("A & B")          // "A &amp; B"
```

#### `htmlDecode(str: String): String`
HTML-decode entities.

**Example:**
```utlx
htmlDecode("&lt;div&gt;")    // "<div>"
```

### Advanced Cryptography

#### `aesEncrypt(data: Binary, key: Binary): Binary`
Encrypt using AES.

**Example:**
```utlx
aesEncrypt("secret".bytes, key)
```

#### `aesDecrypt(data: Binary, key: Binary): Binary`
Decrypt using AES.

**Example:**
```utlx
aesDecrypt(encrypted, key)
```

#### `rsaEncrypt(data: Binary, publicKey: String): Binary`
Encrypt using RSA public key.

#### `rsaDecrypt(data: Binary, privateKey: String): Binary`
Decrypt using RSA private key.

#### `rsaSign(data: Binary, privateKey: String): String`
Sign data using RSA private key.

#### `rsaVerify(data: Binary, signature: String, publicKey: String): Boolean`
Verify RSA signature.

---

## JSON Functions

### JSON Operations

#### `parseJSON(str: String): Object`
Parse JSON string.

**Example:**
```utlx
parseJSON('{"name":"John"}')  // {name: "John"}
```

#### `toJSON(obj: Object, prettyPrint: Boolean): String`
Serialize object to JSON.

**Example:**
```utlx
toJSON({name: "John"})                    // '{"name":"John"}'
toJSON({name: "John"}, true)              // Pretty-printed
```

#### `prettyPrintJSON(str: String, indent: Number): String`
Format JSON string with indentation.

**Example:**
```utlx
prettyPrintJSON('{"a":1,"b":2}', 2)
// {
//   "a": 1,
//   "b": 2
// }
```

#### `minifyJSON(str: String): String`
Remove whitespace from JSON.

**Example:**
```utlx
minifyJSON('{\n  "a": 1\n}')  // '{"a":1}'
```

### JSON Canonicalization (RFC 8785)

#### `canonicalizeJSON(obj: Object): String`
Create canonical JSON representation.

**Example:**
```utlx
canonicalizeJSON({b: 2, a: 1})  // '{"a":1,"b":2}' (keys sorted)
```

#### `jcs(obj: Object): String`
JSON Canonicalization Scheme (alias).

**Example:**
```utlx
jcs({name: "John", age: 30})
```

### JSON Schema

#### `validateJSONSchema(data: Object, schema: Object): Boolean`
Validate against JSON Schema.

**Example:**
```utlx
validateJSONSchema(
  {name: "John", age: 30},
  {
    type: "object",
    properties: {
      name: {type: "string"},
      age: {type: "number"}
    }
  }
)  // true
```

---

## XML Functions

### XML Operations

#### `parseXML(str: String): Object`
Parse XML string to UDM.

**Example:**
```utlx
parseXML('<person><name>John</name></person>')
```

#### `toXML(obj: Object, prettyPrint: Boolean): String`
Serialize UDM to XML.

**Example:**
```utlx
toXML({person: {name: "John"}})
// <person><name>John</name></person>
```

#### `prettyPrintXML(str: String, indent: Number): String`
Format XML with indentation.

**Example:**
```utlx
prettyPrintXML('<root><child>text</child></root>', 2)
// <root>
//   <child>text</child>
// </root>
```

#### `minifyXML(str: String): String`
Remove whitespace from XML.

### XML Canonicalization (C14N)

#### `c14n(xml: String): String`
Canonical XML (C14N 1.0).

**Example:**
```utlx
c14n('<root xmlns="http://example.com" />')
```

#### `c14nExclusive(xml: String): String`
Exclusive C14N.

#### `c14n11(xml: String): String`
C14N 1.1 specification.

#### `c14nWithComments(xml: String): String`
C14N with comments preserved.

### QName Functions

#### `qname(localName: String, namespaceURI: String, prefix: String): QName`
Create qualified name.

**Example:**
```utlx
qname("element", "http://example.com", "ex")  // ex:element
```

#### `localName(qname: QName): String`
Extract local name from QName.

#### `namespaceURI(qname: QName): String`
Extract namespace URI from QName.

#### `prefix(qname: QName): String`
Extract prefix from QName.

### CDATA Functions

#### `wrapCDATA(text: String): String`
Wrap text in CDATA section.

**Example:**
```utlx
wrapCDATA("<script>alert('hi')</script>")
// <![CDATA[<script>alert('hi')</script>]]>
```

#### `unwrapCDATA(cdata: String): String`
Extract text from CDATA.

**Example:**
```utlx
unwrapCDATA("<![CDATA[text]]>")  // "text"
```

#### `isCDATA(str: String): Boolean`
Check if string is CDATA section.

#### `escapeCDATA(text: String): String`
Escape CDATA end markers in text.

### XML Serialization Options

#### `serializeXML(obj: Object, options: XMLOptions): String`
Serialize with custom options.

**Options:**
- `prettyPrint: Boolean` - Format output
- `indent: Number` - Indentation spaces
- `omitXmlDeclaration: Boolean` - Skip <?xml?> declaration
- `encoding: String` - Character encoding
- `standalone: Boolean` - Standalone attribute
- `cdataStrategy: String` - When to use CDATA ("auto", "never", "always")

---

## CSV Functions

### CSV Operations

#### `parseCSV(str: String, options: CSVOptions): Array`
Parse CSV string.

**Example:**
```utlx
parseCSV("name,age\nJohn,30\nJane,25", {headers: true})
// [{name: "John", age: "30"}, {name: "Jane", age: "25"}]
```

**Options:**
- `headers: Boolean` - First row is headers
- `delimiter: String` - Field separator (default: ",")
- `quote: String` - Quote character (default: "\"")
- `escape: String` - Escape character
- `skipEmptyLines: Boolean`

#### `toCSV(array: Array, options: CSVOptions): String`
Serialize array to CSV.

**Example:**
```utlx
toCSV([{name: "John", age: 30}], {headers: true})
// "name,age\nJohn,30"
```

#### `prettyPrintCSV(str: String): String`
Format CSV for readability.

#### `csvToJSON(csv: String): Array<Object>`
Convert CSV to JSON array.

**Example:**
```utlx
csvToJSON("name,age\nJohn,30")
// [{name: "John", age: "30"}]
```

#### `jsonToCSV(array: Array<Object>): String`
Convert JSON array to CSV.

**Example:**
```utlx
jsonToCSV([{name: "John", age: 30}])
// "name,age\nJohn,30"
```

---

## YAML Functions

### YAML Operations

#### `parseYAML(str: String): Object`
Parse YAML string.

**Example:**
```utlx
parseYAML("name: John\nage: 30")
// {name: "John", age: 30}
```

#### `toYAML(obj: Object, prettyPrint: Boolean): String`
Serialize object to YAML.

**Example:**
```utlx
toYAML({name: "John", age: 30})
// name: John
// age: 30
```

#### `prettyPrintYAML(str: String, indent: Number): String`
Format YAML with custom indentation.

### Multi-Document YAML

#### `yamlSplitDocuments(yaml: String): Array`
Split multi-document YAML.

**Example:**
```utlx
yamlSplitDocuments("---\ndoc1\n---\ndoc2")
// [doc1, doc2]
```

#### `yamlMergeDocuments(docs: Array): String`
Merge multiple YAML documents.

**Example:**
```utlx
yamlMergeDocuments([doc1, doc2])
// ---
// doc1
// ---
// doc2
```

### YAML Path Operations

#### `yamlPath(yaml: Object, path: String): Any`
Query YAML using path.

**Example:**
```utlx
yamlPath({metadata: {name: "app"}}, ".metadata.name")
// "app"
```

#### `yamlSet(yaml: Object, path: String, value: Any): Object`
Set value at path.

**Example:**
```utlx
yamlSet(yaml, ".spec.replicas", 3)
```

#### `yamlDelete(yaml: Object, path: String): Object`
Delete value at path.

**Example:**
```utlx
yamlDelete(yaml, ".metadata.annotations")
```

### Dynamic Keys

#### `yamlKeys(yaml: Object): Array<String>`
Get all keys from YAML object.

**Example:**
```utlx
yamlKeys({production: {}, staging: {}})  // ["production", "staging"]
```

#### `yamlValues(yaml: Object): Array`
Get all values from YAML object.

**Example:**
```utlx
yamlValues({a: 1, b: 2})  // [1, 2]
```

#### `yamlEntries(yaml: Object): Array`
Get key-value pairs.

**Example:**
```utlx
yamlEntries({a: 1, b: 2})  // [["a", 1], ["b", 2]]
```

### Deep Merge

#### `yamlMerge(base: Object, overlay: Object): Object`
Deep merge two YAML objects.

**Example:**
```utlx
yamlMerge(
  {database: {host: "localhost"}},
  {database: {port: 5432}}
)
// {database: {host: "localhost", port: 5432}}
```

#### `yamlMergeAll(yamls: Array): Object`
Merge multiple YAML documents.

### Kubernetes Helpers

#### `yamlExtractByKind(yaml: Array, kind: String): Array`
Extract Kubernetes resources by kind.

**Example:**
```utlx
yamlExtractByKind(k8sResources, "Deployment")
```

#### `yamlExtractByName(yaml: Array, name: String): Object`
Find resource by metadata.name.

#### `yamlFilterResources(yaml: Array, predicate: Function): Array`
Filter resources by custom criteria.

#### `yamlGetResourceNames(yaml: Array): Array<String>`
List all resource names.

---

## Geospatial Functions

### Distance Calculation

#### `distance(lat1: Number, lon1: Number, lat2: Number, lon2: Number): Number`
Calculate distance between two points (km).

**Example:**
```utlx
distance(40.7128, -74.0060, 34.0522, -118.2437)  // ~3936 km (NYC to LA)
```

#### `distanceHaversine(lat1: Number, lon1: Number, lat2: Number, lon2: Number): Number`
Haversine formula distance.

#### `distanceVincenty(lat1: Number, lon1: Number, lat2: Number, lon2: Number): Number`
Vincenty formula distance (more accurate).

### Geospatial Operations

#### `bearing(lat1: Number, lon1: Number, lat2: Number, lon2: Number): Number`
Calculate initial bearing between points.

**Example:**
```utlx
bearing(40.7128, -74.0060, 34.0522, -118.2437)  // ~260° (west-southwest)
```

#### `destination(lat: Number, lon: Number, distance: Number, bearing: Number): Object`
Calculate destination point.

**Example:**
```utlx
destination(40.7128, -74.0060, 100, 90)  // 100km east of NYC
// {lat: 40.7128, lon: -72.7165}
```

#### `midpoint(lat1: Number, lon1: Number, lat2: Number, lon2: Number): Object`
Calculate midpoint between two points.

**Example:**
```utlx
midpoint(40.7128, -74.0060, 34.0522, -118.2437)
// {lat: 37.5951, lon: -96.6886}
```

### Area Calculations

#### `boundingBox(points: Array): Object`
Calculate bounding box for points.

**Example:**
```utlx
boundingBox([
  {lat: 40.7128, lon: -74.0060},
  {lat: 34.0522, lon: -118.2437}
])
// {north: 40.7128, south: 34.0522, east: -74.0060, west: -118.2437}
```

#### `isPointInPolygon(lat: Number, lon: Number, polygon: Array): Boolean`
Check if point is inside polygon.

**Example:**
```utlx
isPointInPolygon(40.7128, -74.0060, nyPolygon)  // true if in NYC
```

---

## Financial Functions

### Interest Calculations

#### `simpleInterest(principal: Number, rate: Number, time: Number): Number`
Calculate simple interest.

**Example:**
```utlx
simpleInterest(1000, 0.05, 2)  // $100 (5% for 2 years)
```

#### `compoundInterest(principal: Number, rate: Number, time: Number, frequency: Number): Number`
Calculate compound interest.

**Example:**
```utlx
compoundInterest(1000, 0.05, 2, 12)  // Compounded monthly
```

### Loan Calculations

#### `loanPayment(principal: Number, rate: Number, periods: Number): Number`
Calculate periodic loan payment.

**Example:**
```utlx
loanPayment(200000, 0.04, 360)  // 30-year mortgage at 4%
// ~$955/month
```

#### `loanBalance(principal: Number, rate: Number, periods: Number, periodsPaid: Number): Number`
Calculate remaining loan balance.

#### `totalInterest(principal: Number, rate: Number, periods: Number): Number`
Calculate total interest paid over loan life.

### Investment Calculations

#### `futureValue(payment: Number, rate: Number, periods: Number): Number`
Calculate future value of regular payments.

**Example:**
```utlx
futureValue(500, 0.07, 360)  // $500/month for 30 years at 7%
// ~$566,000
```

#### `presentValue(futureValue: Number, rate: Number, periods: Number): Number`
Calculate present value.

#### `npv(rate: Number, cashFlows: Array<Number>): Number`
Net present value.

**Example:**
```utlx
npv(0.10, [-1000, 300, 400, 500])  // ~$45.91
```

#### `irr(cashFlows: Array<Number>): Number`
Internal rate of return.

**Example:**
```utlx
irr([-1000, 300, 400, 500])  // ~0.134 (13.4%)
```

### Currency

#### `formatCurrency(amount: Number, currency: String): String`
Format number as currency.

**Example:**
```utlx
formatCurrency(1234.56, "USD")  // "$1,234.56"
formatCurrency(1234.56, "EUR")  // "€1,234.56"
```

#### `exchangeRate(from: String, to: String): Number`
Get exchange rate between currencies (requires external API).

---

## Logical Functions

### Boolean Operations

#### `and(a: Boolean, b: Boolean): Boolean`
Logical AND.

**Example:**
```utlx
and(true, true)          // true
and(true, false)         // false
```

#### `or(a: Boolean, b: Boolean): Boolean`
Logical OR.

**Example:**
```utlx
or(true, false)          // true
or(false, false)         // false
```

#### `not(a: Boolean): Boolean`
Logical NOT.

**Example:**
```utlx
not(true)                // false
not(false)               // true
```

#### `xor(a: Boolean, b: Boolean): Boolean`
Logical XOR (exclusive or).

**Example:**
```utlx
xor(true, false)         // true
xor(true, true)          // false
```

### Conditional Operations

#### `if(condition: Boolean, thenValue: Any, elseValue: Any): Any`
Conditional expression.

**Example:**
```utlx
if(age > 18, "adult", "minor")
```

#### `unless(condition: Boolean, value: Any, elseValue: Any): Any`
Inverted conditional.

**Example:**
```utlx
unless(isEmpty(str), str, "default")
```

#### `coalesce(value1: Any, value2: Any, ...): Any`
Return first non-null value.

**Example:**
```utlx
coalesce(null, null, "default")  // "default"
coalesce(value, "fallback")      // value if not null
```

#### `defaultTo(value: Any, default: Any): Any`
Return default if value is null.

**Example:**
```utlx
defaultTo(null, "default")       // "default"
defaultTo("value", "default")    // "value"
```

### Comparison

#### `equals(a: Any, b: Any): Boolean`
Deep equality check.

**Example:**
```utlx
equals([1, 2], [1, 2])   // true
equals({a: 1}, {a: 1})   // true
```

#### `compare(a: Any, b: Any): Number`
Compare values (-1, 0, 1).

**Example:**
```utlx
compare(1, 2)            // -1
compare(2, 1)            // 1
compare(1, 1)            // 0
```

---

## URL Functions

### URL Parsing

#### `parseURL(url: String): Object`
Parse URL into components.

**Example:**
```utlx
parseURL("https://example.com:8080/path?key=value#section")
// {
//   protocol: "https",
//   host: "example.com",
//   port: 8080,
//   path: "/path",
//   query: {key: "value"},
//   fragment: "section"
// }
```

#### `buildURL(components: Object): String`
Build URL from components.

**Example:**
```utlx
buildURL({
  protocol: "https",
  host: "example.com",
  path: "/api/users"
})
// "https://example.com/api/users"
```

### Query String

#### `parseQueryString(query: String): Object`
Parse query string to object.

**Example:**
```utlx
parseQueryString("key1=value1&key2=value2")
// {key1: "value1", key2: "value2"}
```

#### `buildQueryString(obj: Object): String`
Build query string from object.

**Example:**
```utlx
buildQueryString({key1: "value1", key2: "value2"})
// "key1=value1&key2=value2"
```

### URL Manipulation

#### `joinURL(base: String, path: String): String`
Join URL with path.

**Example:**
```utlx
joinURL("https://example.com", "/api/users")
// "https://example.com/api/users"
```

#### `resolveURL(base: String, relative: String): String`
Resolve relative URL.

**Example:**
```utlx
resolveURL("https://example.com/path/", "../other")
// "https://example.com/other"
```

#### `normalizeURL(url: String): String`
Normalize URL (remove redundant parts).

**Example:**
```utlx
normalizeURL("https://example.com//path/./file")
// "https://example.com/path/file"
```

#### `isValidURL(url: String): Boolean`
Check if string is valid URL.

**Example:**
```utlx
isValidURL("https://example.com")  // true
isValidURL("not a url")            // false
```

---

## JWT/JWS Functions

### JWT Operations

#### `createJWT(payload: Object, secret: String): String`
Create JWT token.

**Example:**
```utlx
createJWT({sub: "user123", exp: now() + 3600}, secret)
// "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### `verifyJWT(token: String, secret: String): Object`
Verify and decode JWT.

**Example:**
```utlx
verifyJWT(token, secret)  // {sub: "user123", ...}
```

#### `decodeJWT(token: String): Object`
Decode JWT without verification.

**Example:**
```utlx
decodeJWT(token)  // {header: {...}, payload: {...}}
```

### JWS Operations

#### `signJWS(payload: String, privateKey: String): String`
Sign data using JWS.

**Example:**
```utlx
signJWS(data, privateKey)
```

#### `verifyJWS(signature: String, data: String, publicKey: String): Boolean`
Verify JWS signature.

**Example:**
```utlx
verifyJWS(signature, data, publicKey)  // true/false
```

---

## Utility Functions

### UUID Generation

#### `uuid(): String`
Generate UUID v4.

**Example:**
```utlx
uuid()  // "550e8400-e29b-41d4-a716-446655440000"
```

#### `uuidv7(): String`
Generate UUID v7 (time-ordered).

**Example:**
```utlx
uuidv7()  // "01899b5d-2f37-7000-8000-000000000000"
```

### Debug Functions

#### `debug(value: Any, label: String): Any`
Print debug information and return value.

**Example:**
```utlx
debug(result, "Final result")  // Logs and returns result
```

#### `trace(value: Any): Any`
Log stack trace and return value.

#### `profile(fn: Function): Object`
Profile function execution.

**Example:**
```utlx
profile(() => expensiveOperation())
// {result: ..., time: 1234}
```

### Runtime Information

#### `version(): String`
Get UTL-X version.

**Example:**
```utlx
version()  // "1.1.0"
```

#### `environment(): Object`
Get runtime environment info.

**Example:**
```utlx
environment()
// {runtime: "jvm", version: "11", os: "linux"}
```

---

## Appendix: Function Index by Module

### String Module (40+ functions)
`upper`, `lower`, `capitalize`, `trim`, `substring`, `split`, `join`, `replace`, `matches`, `camelCase`, `snakeCase`, `kebabCase`, etc.

### Array Module (35+ functions)
`map`, `filter`, `reduce`, `sort`, `distinct`, `union`, `intersect`, `flatten`, `chunk`, `zip`, etc.

### Math Module (25+ functions)
`abs`, `ceil`, `floor`, `round`, `pow`, `sqrt`, `sin`, `cos`, `mean`, `median`, `stddev`, etc.

### Date Module (35+ functions)
`now`, `parseDate`, `formatDate`, `addDays`, `diffDays`, `toTimezone`, `isLeapYear`, etc.

### Object Module (20+ functions)
`keys`, `values`, `entries`, `merge`, `pick`, `omit`, `mapValues`, `isEmpty`, etc.

### Type Module (15+ functions)
`typeOf`, `isString`, `isNumber`, `toString`, `toNumber`, `coerce`, `cast`, etc.

### Binary Module (20+ functions)
`toBase64`, `fromBase64`, `md5`, `sha256`, `gzip`, `hmacSHA256`, etc.

### JSON Module (10+ functions)
`parseJSON`, `toJSON`, `prettyPrintJSON`, `canonicalizeJSON`, `validateJSONSchema`, etc.

### XML Module (25+ functions)
`parseXML`, `toXML`, `c14n`, `qname`, `wrapCDATA`, `prettyPrintXML`, etc.

### CSV Module (8+ functions)
`parseCSV`, `toCSV`, `csvToJSON`, `jsonToCSV`, `prettyPrintCSV`, etc.

### YAML Module (18+ functions)
`parseYAML`, `toYAML`, `yamlPath`, `yamlMerge`, `yamlExtractByKind`, etc.

### Geo Module (8 functions)
`distance`, `bearing`, `destination`, `midpoint`, `boundingBox`, `isPointInPolygon`, etc.

### Finance Module (10+ functions)
`simpleInterest`, `compoundInterest`, `loanPayment`, `npv`, `irr`, `formatCurrency`, etc.

### Logical Module (10+ functions)
`and`, `or`, `not`, `xor`, `if`, `coalesce`, `equals`, `compare`, etc.

### URL Module (8+ functions)
`parseURL`, `buildURL`, `parseQueryString`, `joinURL`, `normalizeURL`, etc.

### JWT/JWS Module (10+ functions)
`createJWT`, `verifyJWT`, `decodeJWT`, `signJWS`, `verifyJWS`, etc.

### Utility Module (10+ functions)
`uuid`, `uuidv7`, `debug`, `trace`, `profile`, `version`, `environment`, etc.

---

## Version History

- **v1.0.0** (December 2025) - Initial release with 188 functions
- **v1.1.0** (October 2025) - Added Geo, Finance, YAML, Advanced Regex functions (247+ total)

---

## License

UTL-X is dual-licensed:
- **GNU Affero General Public License v3.0 (AGPL-3.0)** - Open source use
- **Commercial License** - Proprietary applications

See [LICENSE.md](../../LICENSE.md) for details.

---

**For complete examples and integration guides, see:**
- [Language Guide](../language-guide/)
- [Examples](../examples/)
- [API Reference](api-reference.md)
- [GitHub Repository](https://github.com/grauwen/utl-x)