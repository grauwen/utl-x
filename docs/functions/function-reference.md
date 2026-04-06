# UTL-X Standard Library Reference

**Generated**: 2026-04-06 23:22:42  
**Total Functions**: 652  
**Version**: 1.0.0

## Overview

UTL-X provides 652 built-in functions across 18 categories for data transformation, processing, and integration tasks.

## Quick Reference

| Category | Functions | Description |
|----------|-----------|-------------|
| Core | 49 | Utility functions |
| String | 83 | Text processing, case conversion, and string manipulation |
| Array | 67 | Functional operations, filtering, mapping, and array manipulation |
| Math | 37 | Mathematical operations, arithmetic, and numeric functions |
| Date | 68 | Date/time parsing, formatting, and manipulation |
| Type | 27 | Utility functions |
| Other | 76 | Utility functions, system operations, and specialized tools |
| Object | 1 | Utility functions |
| Encoding | 30 | Base64, URL encoding/decoding, and cryptographic hashing |
| XML | 60 | XML parsing, encoding detection, and namespace handling |
| Security | 16 | Utility functions |
| JSON | 6 | JSON canonicalization, formatting, and processing |
| Binary | 47 | Utility functions |
| Utility | 27 | Utility functions |
| Financial | 16 | Utility functions |
| Geospatial | 8 | Utility functions |
| CSV | 12 | Utility functions |
| YAML | 22 | Utility functions |

## Function Categories

### Array Functions (67)

Functional operations, filtering, mapping, and array manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `avg` | -1 | Calculate the average (mean) of all numeric elements in an array |  |
| `avgBy` | 2 | Calculates the average value using a mapping function. |  |
| `chunk` | 1 | Chunks array into arrays of specified size |  |
| `compact` | 1 | Remove null, undefined, and empty values from array |  |
| `concat` | 1 | Concatenate multiple arrays |  |
| `count` | -1 | Count the number of elements in an array |  |
| `countBy` | 2 | Counts the number of elements in an array that match a predicate. |  |
| `crossJoin` | 2 | Cross join - Cartesian product of both arrays |  |
| `difference` | 1 | Performs difference operation |  |
| `distinct` | 1 | Removes duplicate elements from array |  |
| `distinctBy` | 1 | Removes duplicates based on function result |  |
| `distinctBy` | 2 | Similar to distinct(), but uses a key function to determine uniqueness. |  |
| `drop` | 1 | Drop first n elements |  |
| `every` | 1 | Check if all elements match predicate |  |
| `find` | 1 | Find first element matching predicate |  |
| `findIndex` | 1 | Find index of first element matching predicate |  |
| `findIndex` | 2 | Find the index of the first element matching predicate |  |
| `findLastIndex` | 2 | Find the index of the last element matching predicate |  |
| `first` | 1 | Get first element |  |
| `flatMap` | 1 | Maps each element using function, then flattens result |  |
| `flatten` | 1 | Flatten nested arrays |  |
| `flattenDeep` | 1 | Deep flatten - flattens all nested levels |  |
| `fullOuterJoin` | 4 | Full outer join - returns all items from both arrays |  |
| `get` | 1 | Gets element at specific index (0-based) |  |
| `groupBy` | 2 | Groups array elements by a key function. |  |
| `includes` | 2 | Check if array includes value |  |
| `indexOf` | 1 | Find index of value in array (simple equality) |  |
| `insertAfter` | 1 | Insert element after index in array |  |
| `insertBefore` | 1 | Insert element before index in array |  |
| `intersect` | 1 | Performs intersect operation |  |
| `join` | 4 | Inner join - returns only items that have matches in both arrays |  |
| `joinToString` | 1 | Joins array elements into string |  |
| `joinWith` | 5 | Join with custom combiner function |  |
| `last` | 1 | Get last element |  |
| `lastIndexOf` | 2 | Find last index of value in array |  |
| `leftJoin` | 4 | Left join - returns all items from left array, with matching items from right |  |
| `map` | 1 | Map function over array |  |
| `max` | -1 | Find the maximum value from an array or multiple arguments |  |
| `maxBy` | 2 | Finds the element with the maximum value according to a comparator function. |  |
| `min` | -1 | Find the minimum value from an array or multiple arguments |  |
| `minBy` | 2 | Finds the element with the minimum value according to a comparator function. |  |
| `partition` | 2 | Splits an array into two groups based on a predicate function. |  |
| `reduce` | 1 | Reduce array to single value |  |
| `remove` | 1 | Remove element at index from array |  |
| `reverse` | 1 | Reverse array |  |
| `rightJoin` | 4 | Right join - returns all items from right array, with matching items from left |  |
| `scan` | 3 | Scan - like reduce but returns all intermediate results |  |
| `size` | 1 | Performs size operation |  |
| `slice` | 2 | Slice array from start to end index |  |
| `some` | 1 | Check if any element matches predicate |  |
| `sort` | 1 | Sort array |  |
| `sortBy` | 1 | Sort array by key function |  |
| `sum` | -1 | Calculate the sum of all numeric elements in an array |  |
| `sumBy` | 2 | Maps each element with a function and sums the results. |  |
| `symmetricDifference` | 1 | Symmetric difference - elements in either array but not both |  |
| `tail` | 1 | Performs tail operation |  |
| `take` | 1 | Take first n elements |  |
| `transpose` | 1 | Transpose a 2D array (swap rows and columns) |  |
| `union` | 1 | Combines two arrays and removes duplicates (set union: A ∪ B) |  |
| `unique` | 1 | Get unique elements |  |
| `unzip` | 1 | Unzip array of pairs into two arrays (inverse of zip) |  |
| `unzipN` | 1 | Unzip array of N-tuples into N arrays |  |
| `windowed` | 3 | Windowed - create sliding window over array |  |
| `zip` | 1 | Zip two arrays together |  |
| `zipAll` | 2 | ZipAll - zip arrays with padding for different lengths |  |
| `zipWith` | 1 | Zip multiple arrays together (generalized zip) |  |
| `zipWithIndex` | 1 | Zip arrays with indices |  |

### Binary Functions (47)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `binaryConcat` | 3 | Concatenate binary data |  |
| `binaryEquals` | 2 | Compare two binary values |  |
| `binaryLength` | 1 | Get binary length in bytes |  |
| `binarySlice` | 3 | Slice binary data |  |
| `binaryToString` | 2 | Convert binary to string |  |
| `bitwiseAnd` | 2 | Performs bitwise AND operation on two binaries |  |
| `bitwiseNot` | 2 | Performs bitwise NOT operation (inversion) |  |
| `bitwiseOr` | 2 | Performs bitwise OR operation on two binaries |  |
| `bitwiseXor` | 2 | Performs bitwise XOR operation on two binaries |  |
| `compress` | 1 | Compress data using specified algorithm |  |
| `decompress` | 1 | Decompress data using specified algorithm |  |
| `deflate` | 1 | Compress data using Deflate algorithm (raw) |  |
| `equals` | 2 | Compares two binaries for equality |  |
| `fromBase64` | 1 | Create binary from Base64 string |  |
| `fromBytes` | 1 | Create binary from byte array |  |
| `fromHex` | 1 | Create binary from hex string |  |
| `gunzip` | 1 | Decompress Gzip data |  |
| `gzip` | 1 | Compress data using Gzip |  |
| `inflate` | 1 | Decompress Deflate data |  |
| `isGzipped` | 1 | Check if data is gzip compressed |  |
| `isJarFile` | 1 | Check if data is a JAR file |  |
| `isZipArchive` | 2 | Check if data is a zip archive |  |
| `listJarEntries` | 1 | List all entries in a JAR file |  |
| `listZipEntries` | 1 | List all entries in a zip archive |  |
| `readByte` | 2 | Read single byte |  |
| `readDouble` | 2 | Read double (64-bit) from binary |  |
| `readFloat` | 2 | Read float (32-bit) from binary |  |
| `readInt16` | 2 | Read 16-bit integer from binary (big endian) |  |
| `readInt32` | 2 | Read 32-bit integer from binary (big endian) |  |
| `readInt64` | 2 | Read 64-bit integer from binary (big endian) |  |
| `readJarEntry` | 2 | Read a single entry from a JAR file |  |
| `readJarManifest` | 1 | Read JAR manifest |  |
| `readZipEntry` | 2 | Read a single entry from a zip archive |  |
| `shiftLeft` | 2 | Shifts bits left by specified positions |  |
| `shiftRight` | 2 | Shifts bits right by specified positions |  |
| `toBase64` | 1 | Convert binary to Base64 string |  |
| `toBinary` | 2 | Create binary from string |  |
| `toBytes` | 1 | Convert binary to byte array |  |
| `toHex` | 1 | Convert binary to hex string |  |
| `unzipArchive` | 2 | Extract all files from a zip archive |  |
| `writeByte` | 1 | Write single byte |  |
| `writeDouble` | 1 | Write double (64-bit) to binary |  |
| `writeFloat` | 1 | Write float (32-bit) to binary |  |
| `writeInt16` | 1 | Write 16-bit integer to binary (big endian) |  |
| `writeInt32` | 1 | Write 32-bit integer to binary (big endian) |  |
| `writeInt64` | 1 | Write 64-bit integer to binary (big endian) |  |
| `zipArchive` | 1 | Create a zip archive from multiple files |  |

### CSV Functions (12)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `csvAddColumn` | 3 | Add a new column to CSV with computed values |  |
| `csvCell` | 3 | Get a specific cell value |  |
| `csvColumn` | 1 | Get a specific column as array |  |
| `csvColumns` | 1 | Get all column names/headers from CSV data |  |
| `csvFilter` | 4 | Filter CSV rows based on column value |  |
| `csvRemoveColumns` | 1 | Remove columns from CSV |  |
| `csvRow` | 3 | Get a specific row by index |  |
| `csvRows` | 1 | Get all rows from CSV data |  |
| `csvSelectColumns` | 2 | Select/project specific columns from CSV |  |
| `csvSort` | 3 | Sort CSV by specified columns |  |
| `csvSummarize` | 2 | Calculate summary statistics for CSV columns |  |
| `csvTranspose` | 4 | Transpose CSV (swap rows and columns) |  |

### Core Functions (49)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `assert` | 1 | Asserts a condition is true |  |
| `assertEqual` | 2 | Asserts two values are equal |  |
| `availableProcessors` | 1 | Get number of available CPU cores/processors |  |
| `clearLogs` | 1 | Clears all log entries |  |
| `coalesce` | 1 | Coalesce - return first non-null value |  |
| `concat` | 1 | Concatenates values of the same type |  |
| `contains` | 2 | Checks if a value contains an element, substring, or key |  |
| `currentDir` | 1 | Get current working directory |  |
| `debug` | 1 | Logs with DEBUG level |  |
| `default` | 1 | Default value if undefined or null |  |
| `endTimer` | 1 | Logs elapsed time since startTimer() |  |
| `env` | 2 | Get environment variable value |  |
| `envAll` | 1 | Get all environment variables |  |
| `envOrDefault` | 2 | Get environment variable with default fallback |  |
| `environment` | 1 | Get current environment name |  |
| `error` | 1 | Logs with ERROR level |  |
| `filter` | 1 | Generic filter function that works on arrays, objects, and strings |  |
| `generateUuid` | 1 | Generate UUID/GUID |  |
| `getLogs` | 1 | Retrieves all log entries |  |
| `hasEnv` | 2 | Check if environment variable exists |  |
| `homeDir` | 1 | Get user home directory |  |
| `ifThenElse` | 1 | Inline if-then-else conditional |  |
| `info` | 1 | Logs with INFO level |  |
| `isDebugMode` | 1 | Check if running in debug mode |  |
| `isEmpty` | 1 | Checks if a value is empty |  |
| `isNotEmpty` | 2 | Checks if a value is not empty (inverse of isEmpty) |  |
| `javaVersion` | 1 | Get Java/JVM version |  |
| `log` | 1 | Logs a value with optional message (passthrough) |  |
| `logCount` | 1 | Example: |  |
| `logPretty` | 1 | Logs a pretty-printed representation of a value |  |
| `logSize` | 1 | Logs the size/length of a value |  |
| `logType` | 1 | Logs the type of a value |  |
| `memoryInfo` | 1 | Get JVM memory information in bytes |  |
| `osArch` | 1 | Get operating system architecture |  |
| `osVersion` | 1 | Get operating system version |  |
| `platform` | 1 | Get platform/operating system name |  |
| `runtimeInfo` | 1 | Get comprehensive runtime information |  |
| `setConsoleLogging` | 1 | Enables or disables console logging |  |
| `setLogLevel` | 1 | Sets the minimum log level |  |
| `startTimer` | 1 | Logs execution time of a block |  |
| `systemPropertiesAll` | 1 | Get all system properties |  |
| `systemProperty` | 2 | Get Java system property |  |
| `systemPropertyOrDefault` | 2 | Get system property with default fallback |  |
| `tempDir` | 1 | Get temporary directory |  |
| `trace` | 1 | Logs with TRACE level |  |
| `uptime` | 1 | Get JVM uptime in milliseconds |  |
| `username` | 1 | Get current username |  |
| `version` | 1 | Get UTL-X version |  |
| `warn` | 1 | Logs with WARN level |  |

### Date Functions (68)

Date/time parsing, formatting, and manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `addDays` | 2 | Add days to a date or datetime |  |
| `addHours` | 1 | Performs addHours operation |  |
| `addMinutes` | 1 | Add minutes to date |  |
| `addMonths` | 1 | Add months to date |  |
| `addQuarters` | 1 | Add quarters to date |  |
| `addSeconds` | 1 | Add seconds to date |  |
| `addWeeks` | 1 | Add weeks to date |  |
| `addYears` | 1 | Add years to date |  |
| `age` | 1-2 | Calculate age in years from birthdate |  |
| `compareDates` | 1 | Compare two dates |  |
| `convertTimezone` | 3 | Convert datetime between timezones |  |
| `currentDate` | 1 | Get current date only (no time component) |  |
| `currentTime` | 1 | Get current time only (no date component) |  |
| `day` | 1 | Extract day component |  |
| `dayOfMonth` | 1 | Extract day component (Common alias for day-of-month extraction) |  |
| `dayOfWeek` | 1 | Get day of week (1=Monday, 7=Sunday) |  |
| `dayOfWeekName` | 1 | Get day of week name |  |
| `dayOfYear` | 1 | Get day of year (1-365/366) |  |
| `daysBetween` | 2 | Calculate difference in days between two dates (DataWeave compatibility) |  |
| `daysInMonth` | 1 | Get days in month |  |
| `daysInYear` | 1 | Get days in year |  |
| `diffDays` | 2 | Calculate difference in days between two dates |  |
| `diffHours` | 1 | Difference in hours between two dates |  |
| `diffMinutes` | 1 | Difference in minutes between two dates |  |
| `diffMonths` | 1 | Difference in months between two dates (approximate) |  |
| `diffSeconds` | 1 | Difference in seconds between two dates |  |
| `diffWeeks` | 1 | Difference in weeks between two dates |  |
| `diffYears` | 1 | Difference in years between two dates |  |
| `endOfDay` | 1 | Get end of day |  |
| `endOfMonth` | 1 | Get end of month |  |
| `endOfQuarter` | 1 | Get end of quarter |  |
| `endOfWeek` | 1 | Get end of week (Sunday) |  |
| `endOfYear` | 1 | Get end of year |  |
| `formatDate` | 1-3 | Format a date/time value with optional pattern and locale |  |
| `formatDateTimeInTimezone` | 2 | Format datetime in specific timezone |  |
| `fromUTC` | 1 | Get local datetime from UTC in specified timezone |  |
| `getTimezone` | 1 | Get timezone offset from datetime |  |
| `getTimezoneName` | 1 | Get timezone name/ID for current system |  |
| `getTimezoneOffsetHours` | 2 | Get timezone offset in hours |  |
| `getTimezoneOffsetSeconds` | 1 | Get timezone offset in seconds |  |
| `hours` | 1 | Extract hours component (0-23) |  |
| `isAfter` | 1 | Check if date is after another date |  |
| `isBefore` | 1 | Check if date is before another date |  |
| `isBetween` | 1 | Check if date is between two dates (inclusive) |  |
| `isLeapYearFunc` | 1 | Check if leap year |  |
| `isSameDay` | 1 | Check if dates are same day |  |
| `isToday` | 1 | Check if date is today |  |
| `isValidTimezone` | 1 | Check if timezone is valid |  |
| `isWeekday` | 1 | Check if date is weekday (Monday-Friday) |  |
| `isWeekend` | 1 | Check if date is weekend (Saturday or Sunday) |  |
| `minutes` | 1 | Extract minutes component (0-59) |  |
| `month` | 1 | Extract month component (1-12) |  |
| `monthName` | 1 | Get month name |  |
| `now` | 1 | Performs now operation |  |
| `parseDate` | 1-3 | Smart date parser with auto-detection and custom pattern support |  |
| `parseDateTimeWithTimezone` | 2 | Parse datetime with timezone |  |
| `quarter` | 1 | Get quarter (1-4) |  |
| `seconds` | 1 | Extract seconds component (0-59) |  |
| `startOfDay` | 1 | Get start of day |  |
| `startOfMonth` | 1 | Get start of month |  |
| `startOfQuarter` | 1 | Get start of quarter |  |
| `startOfWeek` | 1 | Get start of week (Monday) |  |
| `startOfYear` | 1 | Get start of year |  |
| `toDate` | 1 | Convert string to date (alias for parseDate, simpler for single argument) |  |
| `toUTC` | 1 | Get UTC datetime from local datetime and timezone |  |
| `validateDate` | 1-2 | Validate date string (ISO 8601 format or custom pattern) |  |
| `weekOfYear` | 1 | Get week of year (ISO week) |  |
| `year` | 1 | Extract year component |  |

### Encoding Functions (30)

Base64, URL encoding/decoding, and cryptographic hashing

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `base64Decode` | 1 | Base64 decode |  |
| `base64Encode` | 1 | Base64 encode |  |
| `decryptAES` | 3 | Decrypts data using AES-128-CBC |  |
| `decryptAES256` | 3 | Decrypts data using AES-256-CBC |  |
| `encryptAES` | 3 | Encrypts data using AES-128-CBC |  |
| `encryptAES256` | 3 | Encrypts data using AES-256-CBC (requires key length of 32 bytes) |  |
| `generateIV` | 1 | Generates a random initialization vector (IV) for encryption |  |
| `generateKey` | 1 | Generates a random encryption key |  |
| `hash` | 1 | Calculate hash using specified algorithm |  |
| `hexDecode` | 1 | Hex decode |  |
| `hexEncode` | 1 | Hex encode |  |
| `hmac` | 2 | Calculate HMAC (Hash-based Message Authentication Code) |  |
| `hmacBase64` | 2 | Computes HMAC and returns as base64 |  |
| `hmacMD5` | 2 | Computes HMAC-MD5 hash |  |
| `hmacSHA1` | 2 | Computes HMAC-SHA1 hash |  |
| `hmacSHA256` | 2 | Computes HMAC-SHA256 hash |  |
| `hmacSHA384` | 2 | Computes HMAC-SHA384 hash |  |
| `hmacSHA512` | 2 | Computes HMAC-SHA512 hash |  |
| `md5` | 1 | Calculate MD5 hash of a string |  |
| `sha1` | 1 | Performs sha1 operation |  |
| `sha224` | 1 | Computes SHA-224 hash |  |
| `sha256` | 1 | Calculate SHA-256 hash of a string |  |
| `sha384` | 1 | Computes SHA-384 hash |  |
| `sha3_256` | 1 | Computes SHA3-256 hash (if available) |  |
| `sha3_512` | 1 | Computes SHA3-512 hash (if available) |  |
| `sha512` | 1 | Calculate SHA-512 hash of a string |  |
| `urlDecode` | 1 | URL decode |  |
| `urlDecodeComponent` | 1 | URL decode component (RFC 3986) - decodes %20 as spaces |  |
| `urlEncode` | 1 | URL encode |  |
| `urlEncodeComponent` | 1 | URL encode component (RFC 3986) - encodes spaces as %20 for URI paths |  |

### Financial Functions (16)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `addTax` | 2 | Calculates the total amount including tax. |  |
| `calculateDiscount` | 2 | Calculates the price after applying a discount. |  |
| `calculateTax` | 2 | Calculates tax amount for a given amount and rate. |  |
| `compoundInterest` | 3 | Calculates compound interest. |  |
| `formatCurrency` | 1 | Formats a number as currency with locale-specific formatting. |  |
| `futureValue` | 3 | Calculates the future value of a present amount. |  |
| `getCurrencyDecimals` | 1 | Gets the number of decimal places for a currency. |  |
| `isValidAmount` | 1 | Validates if an amount is within acceptable range. |  |
| `isValidCurrency` | 1 | Validates if a currency code is valid (ISO 4217). |  |
| `parseCurrency` | 1 | Parses a currency string to a number. |  |
| `percentageChange` | 2 | Calculates the percentage change between two values. |  |
| `presentValue` | 3 | Calculates the present value of a future amount. |  |
| `removeTax` | 2 | Calculates the original amount from a total that includes tax. |  |
| `roundToCents` | 2 | Rounds up to the nearest cent (2 decimal places). |  |
| `roundToDecimalPlaces` | 2 | Rounds a number to a specified number of decimal places using banker's rounding. |  |
| `simpleInterest` | 3 | Calculates simple interest. |  |

### Geospatial Functions (8)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `bearing` | 4 | Calculates the initial bearing (forward azimuth) from point1 to point2. |  |
| `boundingBox` | 1 | Calculates the bounding box (min/max lat/lon) for an array of coordinates. |  |
| `destinationPoint` | 4 | Calculates a destination point given a starting point, bearing, and distance. |  |
| `distance` | 4 | Calculates the distance between two geographic coordinates using the Haversine formula. |  |
| `isPointInCircle` | 5 | Checks if a point is within a circular radius from a center point. |  |
| `isPointInPolygon` | 3 | Checks if a point is inside a polygon using the ray casting algorithm. |  |
| `isValidCoordinates` | 2 | Checks if coordinates are valid. |  |
| `midpoint` | 4 | Calculates the midpoint between two coordinates. |  |

### JSON Functions (6)

JSON canonicalization, formatting, and processing

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `canonicalJSONHash` | 2 | Canonicalizes JSON and computes cryptographic hash |  |
| `canonicalJSONSize` | 1 | Gets the canonical form size in bytes (UTF-8) |  |
| `canonicalizeJSON` | 2 | Canonicalizes JSON according to RFC 8785 (JSON Canonicalization Scheme) |  |
| `isCanonicalJSON` | 1 | Validates that a string is valid canonical JSON per RFC 8785 |  |
| `jcs` | 2 | Alias for canonicalizeJSON - shorter form (JCS) |  |
| `jsonEquals` | 1 | Compares two JSON values for semantic equality using canonicalization |  |

### Math Functions (37)

Mathematical operations, arithmetic, and numeric functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `abs` | 1 | Performs abs operation |  |
| `acos` | 1 | Example: |  |
| `asin` | 1 | Example: |  |
| `atan` | 2 | Example: |  |
| `atan2` | 2 | coordinates (x, y) to polar coordinates (r, theta). |  |
| `ceil` | 1 | Performs ceil operation |  |
| `cos` | 1 | Example: |  |
| `cosh` | 1 | Example: |  |
| `e` | 1 | Approximately 2.71828182845904523536 |  |
| `exp` | 1 | Example: |  |
| `floor` | 1 | Performs floor operation |  |
| `formatNumber` | 1 | Format number with pattern |  |
| `goldenRatio` | 1 | Approximately 1.61803398874989484820 |  |
| `iqr` | 1 | Calculate interquartile range (IQR) |  |
| `ln` | 1 | Example: |  |
| `log` | 1 | If no base is provided, uses natural logarithm (base e). |  |
| `log10` | 1 | Example: |  |
| `log2` | 1 | Example: |  |
| `median` | 1 | Calculate median (middle value) |  |
| `mode` | 1 | Calculate mode (most frequent value) |  |
| `parseFloat` | 1 | Parse float from string |  |
| `parseInt` | 1 | Parse integer from string |  |
| `percentile` | 2 | Calculate percentile |  |
| `pi` | 1 | Approximately 3.14159265358979323846 |  |
| `pow` | 1 | Performs pow operation |  |
| `quartiles` | 1 | Calculate quartiles (Q1, Q2/median, Q3) |  |
| `random` | 1 | Performs random operation |  |
| `round` | 1 | Performs round operation |  |
| `sin` | 1 | Example: |  |
| `sinh` | 1 | Example: |  |
| `sqrt` | 1 | Performs sqrt operation |  |
| `stdDev` | 1 | Calculate standard deviation |  |
| `tan` | 1 | Example: |  |
| `tanh` | 1 | Example: |  |
| `toDegrees` | 1 | Converts radians to degrees. |  |
| `toRadians` | 1 | Converts degrees to radians. |  |
| `variance` | 1 | Calculate variance |  |

### Object Functions (1)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `mapTree` | 2 | Recursively transforms all values in a nested structure (objects and arrays) by applying a transformer function. |  |

### Other Functions (76)

Utility functions, system operations, and specialized tools

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `addQueryParam` | 3 | Add query parameter to URL |  |
| `all` | 1 | Check if all values in array are true |  |
| `and` | 1 | Logical AND (variadic) |  |
| `any` | 1 | Check if any value in array is true |  |
| `buildQueryString` | 1 | Build query string from object |  |
| `buildURL` | 1 | Build URL from components |  |
| `compactCSV` | 2 | Compacts a CSV string (removes extra whitespace) |  |
| `compactJSON` | 3 | Compacts a JSON string (removes all unnecessary whitespace) |  |
| `compactXML` | 3 | Compacts an XML string (removes unnecessary whitespace) |  |
| `containsKey` | 1 | Checks if an object contains a specific key |  |
| `containsValue` | 1 | Checks if an object contains a specific value |  |
| `countEntries` | 2 | [1] predicate function (key, value) => boolean |  |
| `debugPrint` | 3 | Creates a human-readable debug representation of UDM |  |
| `debugPrintCompact` | 2 | Creates a compact single-line debug representation |  |
| `deepClone` | 1 | Deep clone an object (recursive copy) |  |
| `deepMerge` | 2 | Deep merge objects recursively |  |
| `deepMergeAll` | 1 | Deep merge multiple objects |  |
| `divideBy` | 2 | Divides an object into sub-objects containing n key-value pairs each. |  |
| `entries` | 1 | Performs entries operation |  |
| `everyEntry` | 2 | The predicate function receives two arguments: key and value. |  |
| `filterEntries` | 2 | Filters an object to include only entries that satisfy the predicate. |  |
| `fromEntries` | 1 | Create object from array of [key, value] pairs (inverse of entries) |  |
| `getBaseURL` | 1 | Get base URL (protocol + host + port) |  |
| `getFragment` | 1 | Get fragment/hash from URL |  |
| `getHost` | 1 | Get host from URL |  |
| `getPath` | 2-3 | Get nested value using path |  |
| `getPath` | 1 | Get path from URL |  |
| `getPort` | 1 | Get port from URL |  |
| `getProtocol` | 1 | Get protocol/scheme from URL |  |
| `getQuery` | 1 | Get query string from URL |  |
| `getQueryParams` | 1 | Get query parameters as object |  |
| `hasKey` | 2 | Check if object has a specific key/property |  |
| `hasKey` | 1 | Checks if an object contains a specific key (Common object method name) |  |
| `implies` | 1 | Logical IMPLIES (Material Implication) |  |
| `invert` | 1 | Invert object - swap keys and values |  |
| `isValidURL` | 1 | Validate URL |  |
| `keys` | 1 | Performs keys operation |  |
| `mapEntries` | 2 | Transforms each entry in the object using a mapping function. |  |
| `mapKeys` | 2 | Transforms an object's keys using a mapping function. |  |
| `mapValues` | 2 | Transforms an object's values using a mapping function. |  |
| `merge` | 1 | Performs merge operation |  |
| `nand` | 1 | Logical NAND (NOT AND) |  |
| `none` | 1 | Check if no values in array are true (all false) |  |
| `nor` | 1 | Logical NOR (NOT OR) |  |
| `not` | 1 | Logical NOT |  |
| `omit` | 1 | Performs omit operation |  |
| `or` | 1 | Logical OR (variadic) |  |
| `parse` | 1 | Auto-detect format and parse |  |
| `parseCsv` | 1 | Parse a CSV string into a UDM array |  |
| `parseJson` | 1 | Parse a JSON string into a UDM object |  |
| `parseQueryString` | 1 | Parse query string from string (public wrapper) |  |
| `parseURL` | 1 | Parse URL into components |  |
| `parseXml` | 1 | Parse an XML string into a UDM object |  |
| `parseYaml` | 1 | Parse a YAML string into a UDM object |  |
| `pick` | 1 | Performs pick operation |  |
| `prettyPrint` | 2 | Automatically detects format and pretty-prints |  |
| `prettyPrintCSV` | 3 | Formats a CSV string with aligned columns |  |
| `prettyPrintFormat` | 2 | Pretty-prints a UDM object in the specified format |  |
| `prettyPrintJSON` | 2 | Pretty-prints a JSON string with optional indentation |  |
| `prettyPrintXML` | 3 | Pretty-prints an XML string with optional formatting options |  |
| `prettyPrintYAML` | 3 | Pretty-prints a YAML string with optional formatting options |  |
| `reduceEntries` | 3 | Reduces all entries in an object to a single value. |  |
| `removeQueryParam` | 2 | Remove query parameter from URL |  |
| `render` | 2 | Render object to specified format |  |
| `renderCsv` | 1 | Render a UDM array as a CSV string |  |
| `renderJson` | 1 | Render a UDM object as a JSON string |  |
| `renderXml` | 1 | Render a UDM object as an XML string |  |
| `renderYaml` | 1 | Render a UDM object as a YAML string |  |
| `setPath` | 3 | Set nested value using path |  |
| `someEntry` | 2 | The predicate function receives two arguments: key and value. |  |
| `udmToJSON` | 2 | Pretty-prints a UDM object as JSON |  |
| `udmToXML` | 3 | Pretty-prints a UDM object as XML |  |
| `udmToYAML` | 3 | Pretty-prints a UDM object as YAML |  |
| `values` | 1 | Performs values operation |  |
| `xnor` | 1 | Logical XNOR (Exclusive NOR / Equivalence) |  |
| `xor` | 1 | Logical XOR (Exclusive OR) |  |

### Security Functions (16)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `decodeJWS` | 1 | Decodes a JWS token WITHOUT verifying the signature |  |
| `decodeJWT` | 1 | Decodes a JWT token WITHOUT verification |  |
| `getJWSAlgorithm` | 1 | Gets the algorithm from a JWS token header |  |
| `getJWSHeader` | 1 | Extracts the header from a JWS token |  |
| `getJWSInfo` | 1 | Gets information about the JWS token structure |  |
| `getJWSKeyId` | 1 | Gets the Key ID (kid) from a JWS token header |  |
| `getJWSPayload` | 1 | Extracts the payload from a JWS token WITHOUT verification |  |
| `getJWSSigningInput` | 1 | Extracts the signing input from a JWS token |  |
| `getJWSTokenType` | 1 | Gets the token type from a JWS token header |  |
| `getJWTAudience` | 1 | Gets the audience (aud) claim from JWT |  |
| `getJWTClaim` | 1 | Gets a specific claim from JWT |  |
| `getJWTClaims` | 1 | Extracts claims from JWT payload WITHOUT verification |  |
| `getJWTIssuer` | 1 | Gets the issuer (iss) claim from JWT |  |
| `getJWTSubject` | 1 | Gets the subject (sub) claim from JWT |  |
| `isJWSFormat` | 1 | Checks if a string is in valid JWS format |  |
| `isJWTExpired` | 1 | Checks if JWT is expired based on 'exp' claim |  |

### String Functions (83)

Text processing, case conversion, and string manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `analyzeString` | 2 | Analyzes a string with a regex pattern and returns detailed match information. |  |
| `camelCase` | 1 | Convert string to camelCase |  |
| `camelize` | 1 | Convert string to camelCase (Legacy naming - use camelCase) |  |
| `capitalize` | 1 | Capitalize first letter of string |  |
| `charAt` | 1 | Get character at index |  |
| `charCodeAt` | 1 | Get character code (Unicode code point) at index |  |
| `concat` | 1 | Concatenate strings |  |
| `constantCase` | 1 | Convert string to CONSTANT_CASE |  |
| `contains` | 1 | Check if string contains substring |  |
| `dotCase` | 1 | Convert string to dot.case |  |
| `endsWith` | 1 | Check if string ends with suffix |  |
| `extractBetween` | 3 | Extract substring between two delimiters |  |
| `findAllMatches` | 2 | Finds all matches of a pattern and returns them with positions. |  |
| `formatPlural` | 2 | Creates a formatted string with count and correctly pluralized word |  |
| `fromCamelCase` | 1 | Convert from camelCase to separate words |  |
| `fromCharCode` | 1 | Create string from character code |  |
| `fromConstantCase` | 1 | Convert from CONSTANT_CASE to separate words |  |
| `fromDotCase` | 1 | Convert from dot.case to separate words |  |
| `fromKebabCase` | 1 | Convert from kebab-case to separate words |  |
| `fromPascalCase` | 1 | Convert from PascalCase to separate words |  |
| `fromPathCase` | 1 | Convert from path/case to separate words |  |
| `fromSnakeCase` | 1 | Convert from snake_case to separate words |  |
| `fromTitleCase` | 1 | Convert from Title Case to separate lowercase words |  |
| `hasAlpha` | 1 | Example: |  |
| `hasNumeric` | 1 | Example: |  |
| `isAlpha` | 1 | Alphabetic characters include A-Z, a-z, and Unicode letters. |  |
| `isAlphanumeric` | 1 | Combines isAlpha and isNumeric: characters must be A-Z, a-z, 0-9, or Unicode letters. |  |
| `isAscii` | 1 | ASCII includes standard English letters, digits, and punctuation. |  |
| `isBlank` | 1 | Check if string is blank (empty or only whitespace) |  |
| `isEmpty` | 1 | Check if string is empty (length 0) |  |
| `isHexadecimal` | 1 | Example: |  |
| `isLowerCase` | 1 | Non-alphabetic characters (digits, spaces, etc.) are ignored. |  |
| `isNumeric` | 1 | Only recognizes 0-9 as numeric digits. |  |
| `isPlural` | 1 | Checks if a word is in plural form |  |
| `isPrintable` | 1 | Printable characters include letters, digits, punctuation, and spaces. |  |
| `isSingular` | 2 | Checks if a word is in singular form |  |
| `isUpperCase` | 1 | Non-alphabetic characters (digits, spaces, etc.) are ignored. |  |
| `isWhitespace` | 1 | Whitespace includes spaces, tabs, newlines, and other Unicode whitespace. |  |
| `join` | 1 | Join array elements with delimiter |  |
| `kebabCase` | 1 | Convert string to kebab-case |  |
| `leftTrim` | 1 | Remove leading whitespace only |  |
| `length` | 1 | Get length of string or array |  |
| `lower` | 1 | Convert string to lowercase (Legacy naming - use lowerCase) |  |
| `lowerCase` | 1 | Convert string to lowercase |  |
| `matches` | 1 | Performs matches operation |  |
| `matchesWhole` | 2 | Tests if a string matches a pattern completely (not just contains). |  |
| `normalizeSpace` | 1 | Normalize whitespace (collapse multiple spaces to single space) |  |
| `pad` | 1 | Pad string to length with character |  |
| `padLeft` | 1 | Pad string to length with character (Explicit left padding - pad() defaults to left) |  |
| `padRight` | 1 | Pad string on right |  |
| `pascalCase` | 1 | Convert string to PascalCase (also called UpperCamelCase) |  |
| `pathCase` | 1 | Convert string to path/case |  |
| `pluralize` | 1 | Converts a singular noun to its plural form |  |
| `pluralizeWithCount` | 2 | Examples: |  |
| `regexGroups` | 2 | Extracts all capture groups from the first match of a pattern. |  |
| `regexNamedGroups` | 2 | Extracts named capture groups from the first match. |  |
| `repeat` | 1 | Repeat string n times |  |
| `replace` | 2-3 | Replace occurrences in string. Supports single replacement or multiple replacements via object/array. |  |
| `replaceRegex` | 1 | Performs replaceRegex operation |  |
| `replaceWithFunction` | 3 | Replaces all matches with results from a function. |  |
| `reverse` | 1 | Reverse a string |  |
| `rightTrim` | 1 | Remove trailing whitespace only |  |
| `singularize` | 1 | Converts a plural noun to its singular form |  |
| `slugify` | 1 | Convert string to URL-safe slug |  |
| `snakeCase` | 1 | Convert string to snake_case |  |
| `split` | 1 | Split string by delimiter |  |
| `splitWithMatches` | 2 | Splits a string by a pattern, but keeps the matched parts. |  |
| `startsWith` | 1 | Check if string starts with prefix |  |
| `substring` | 2 | Extract substring |  |
| `substringAfter` | 1 | Substring after first occurrence |  |
| `substringAfterLast` | 1 | Substring after last occurrence |  |
| `substringBefore` | 1 | Substring before first occurrence |  |
| `substringBeforeLast` | 1 | Substring before last occurrence |  |
| `titleCase` | 1 | Capitalize first letter of each word |  |
| `titleCase` | 1 | Convert string to Title Case |  |
| `toTitleCase` | 1 | Convert string to title case (capitalize first letter of each word) |  |
| `translate` | 1 | Translate characters in string (character mapping) |  |
| `trim` | 2 | Trim whitespace from both ends |  |
| `truncate` | 2-3 | Truncate string with ellipsis |  |
| `uncamelize` | 1 | Convert from camelCase to separate words (Legacy naming - use fromCamelCase) |  |
| `upper` | 1 | Convert string to uppercase (Legacy naming - use upperCase) |  |
| `upperCase` | 1 | Convert string to uppercase |  |
| `wordCase` | 1 | Convert string to word case (capitalize first letter, rest lowercase) |  |

### Type Functions (27)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `getType` | 1 | Returns the type of a value as a string |  |
| `isArray` | 1 | Performs isArray operation |  |
| `isBoolean` | 1 | Performs isBoolean operation |  |
| `isDate` | 1 | Check if value is a date (date-only, no time) |  |
| `isDateTime` | 1 | Check if value is a datetime (with timezone) |  |
| `isDefined` | 1 | Performs isDefined operation |  |
| `isLocalDateTime` | 1 | Check if value is a local datetime (no timezone) |  |
| `isNull` | 1 | Performs isNull operation |  |
| `isNumber` | 1 | Performs isNumber operation |  |
| `isObject` | 1 | Performs isObject operation |  |
| `isString` | 1 | Performs isString operation |  |
| `isTime` | 1 | Check if value is a time (time-only, no date) |  |
| `numberOrDefault` | 2 | Safely convert to number with default |  |
| `parseBoolean` | 2 | Parse boolean from string or number |  |
| `parseDate` | 3 | Parse date from string with optional format |  |
| `parseDouble` | 1 | Parse double from string (alias for parseNumber) |  |
| `parseFloat` | 1 | Parse float/decimal from string (alias for parseNumber) |  |
| `parseInt` | 2 | Parse integer from string |  |
| `parseNumber` | 2 | This is the PRIMARY function for converting XML/CSV string values to numbers |  |
| `stringOrDefault` | 2 | Safely convert to string with default |  |
| `toArray` | 1 | Convert value to array |  |
| `toBoolean` | 1 | Convert value to boolean (stricter than parseBoolean) |  |
| `toNumber` | 1 | Convert value to number (alias for parseNumber, stricter) |  |
| `toObject` | 1 | Try to convert value to object |  |
| `toString` | 1 | Convert any value to string |  |
| `typeOf` | 1 | Returns the type of a value as a string (UTL-X camelCase naming convention) |  |
| `typeof` | 1 | Returns the type of a value as a string (JavaScript/TypeScript compatibility) |  |

### Utility Functions (27)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `canCoerce` | 2 | Check if value can be coerced to type |  |
| `coerce` | 3 | Coerce value to target type with default |  |
| `coerceAll` | 3 | Coerce all values in array to target type |  |
| `extractTimestampFromUuidV7` | 1 | Extract timestamp from UUID v7 |  |
| `generateUuidV4` | 0 | Generate UUID v4 (random) |  |
| `generateUuidV7` | 0-1 | Generate UUID v7 (time-ordered) |  |
| `generateUuidV7Batch` | 1 | Generate batch of UUID v7s with monotonic guarantee |  |
| `getUuidVersion` | 1 | Get UUID version number |  |
| `isUuidV7` | 1 | Check if UUID is version 7 |  |
| `isValidUuid` | 1 | Validate UUID format |  |
| `measure` | 1 | Measure execution time of expression (would need runtime support) |  |
| `smartCoerce` | 1 | Smart coercion - infers target type from context |  |
| `timerCheck` | 1 | Get elapsed time without stopping timer |  |
| `timerClear` | 1 | Clear all timers and measurements |  |
| `timerList` | 1 | List all active timers |  |
| `timerReset` | 1 | Reset a timer |  |
| `timerStart` | 1 | Start a named timer |  |
| `timerStats` | 1 | Get statistics for a timer |  |
| `timerStop` | 1 | Stop a named timer and get elapsed time |  |
| `timestamp` | 1 | Get current timestamp |  |
| `treeDepth` | 1 | Get tree depth (maximum nesting level) |  |
| `treeFilter` | 2 | Filter tree nodes by predicate |  |
| `treeFind` | 2 | Find node by path in tree |  |
| `treeFlatten` | 1 | Flatten tree to array of leaf nodes |  |
| `treeMap` | 2 | Map over all nodes in a tree structure |  |
| `treePaths` | 1 | Get all paths in tree |  |
| `tryCoerce` | 2 | Try to coerce, return null on failure |  |

### XML Functions (60)

XML parsing, encoding detection, and namespace handling

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `addBOM` | 1 | Add BOM to data for specified encoding |  |
| `addNamespaceDeclarations` | 2 | Adds namespace declarations to XML |  |
| `attribute` | 1 | Get specific attribute value |  |
| `attributes` | 1 | Get all attributes from XML element as object |  |
| `c14n` | 1 | Canonicalizes XML using Canonical XML 1.0 (without comments) |  |
| `c14n11` | 1 | Canonicalizes XML using Canonical XML 1.1 (without comments) |  |
| `c14n11WithComments` | 1 | Canonicalizes XML using Canonical XML 1.1 (with comments) |  |
| `c14nEquals` | 2 | Compares two XML documents for canonical equivalence |  |
| `c14nFingerprint` | 1 | Creates a normalized fingerprint of XML for deduplication |  |
| `c14nHash` | 1 | Canonicalizes XML and computes its hash |  |
| `c14nPhysical` | 2 | Canonicalizes XML using Physical Canonical XML |  |
| `c14nSubset` | 2 | Canonicalizes a subset of XML selected by XPath |  |
| `c14nWithComments` | 1 | Canonicalizes XML using Canonical XML 1.0 (with comments) |  |
| `canonicalizeWithAlgorithm` | 2 | Canonicalizes XML using specified algorithm |  |
| `childCount` | 1 | Count child elements |  |
| `childNames` | 1 | Get child element names |  |
| `convertXMLEncoding` | 1 | Convert XML from one encoding to another |  |
| `createCDATA` | 1 | Creates a CDATA section with the given content |  |
| `createQName` | 2 | Create QName string from local name and namespace URI |  |
| `createSOAPEnvelope` | 2 | Creates SOAP envelope with proper namespace prefixes |  |
| `detectBOM` | 1 | Detect BOM type from binary data |  |
| `detectXMLEncoding` | 1 | Detect encoding from XML declaration or BOM |  |
| `elementPath` | 1 | Get element path (like XPath) |  |
| `enforceNamespacePrefixes` | 1 | Enforces specific namespace prefixes on an XML string |  |
| `escapeXML` | 1 | Escapes text for XML without using CDATA |  |
| `excC14n` | 1 | Canonicalizes XML using Exclusive Canonical XML (without comments) |  |
| `excC14nWithComments` | 1 | Canonicalizes XML using Exclusive Canonical XML (with comments) |  |
| `extractCDATA` | 2 | Extracts content from a CDATA section |  |
| `formatEmptyElements` | 1 | Formats empty elements according to specified style |  |
| `getBOMBytes` | 1 | Get BOM bytes for encoding |  |
| `getNamespaces` | 1 | Get all namespace declarations from element |  |
| `hasAttribute` | 1 | Check if element has attribute |  |
| `hasBOM` | 1 | Check if data starts with BOM |  |
| `hasContent` | 1 | Check if XML element has any content (child elements or text) |  |
| `hasNamespace` | 1 | Check if element has namespace |  |
| `isCDATA` | 2 | Checks if a string is a CDATA section |  |
| `isEmptyElement` | 1 | Check if element is empty (no children, no text) |  |
| `localName` | 1 | Get local name (without namespace prefix) from XML element |  |
| `matchesQName` | 1 | Match element by qualified name |  |
| `namespacePrefix` | 1 | Get namespace prefix from XML element |  |
| `namespaceUri` | 1 | Get namespace URI from XML element |  |
| `nodeType` | 1 | Get node type (element, attribute, text, etc.) |  |
| `normalizeBOM` | 1 | Convert to target encoding with BOM handling |  |
| `normalizeXMLEncoding` | 1 | Auto-detect and convert to target encoding |  |
| `parent` | 1 | Get parent element (if metadata available) |  |
| `prepareForSignature` | 1 | Prepares XML for digital signature (XMLDSig) |  |
| `qualifiedName` | 1 | Get qualified name (with prefix) from XML element |  |
| `removeBOM` | 1 | Remove BOM if present |  |
| `resolveQName` | 2 | Resolve QName string to full qualified name with namespace |  |
| `shouldUseCDATA` | 2 | Determines if content should be wrapped in CDATA |  |
| `stripBOM` | 1 | Remove BOM character from string (U+FEFF) |  |
| `textContent` | 1 | Get text content from XML element (all text nodes concatenated) |  |
| `unescapeXML` | 1 | Unescapes XML entities |  |
| `unwrapCDATA` | 2 | Unwraps CDATA if present, otherwise returns original |  |
| `updateXMLEncoding` | 1 | Update encoding in XML declaration |  |
| `validateDigest` | 2 | Validates that XML digest matches expected value |  |
| `validateEncoding` | 1 | Check if encoding name is valid |  |
| `wrapIfNeeded` | 3 | Automatically wraps content in CDATA if beneficial |  |
| `xmlEscape` | 1 | Escape XML special characters |  |
| `xmlUnescape` | 1 | Unescape XML special characters |  |

### YAML Functions (22)

Utility functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `yamlDelete` | 1 | Delete value at YAML path |  |
| `yamlEntries` | 1 | Get entries (key-value pairs) from a YAML object |  |
| `yamlExists` | 1 | Check if path exists in YAML |  |
| `yamlFilterByKeyPattern` | 1 | Filter YAML object by key pattern |  |
| `yamlFindByField` | 2 | Find all values in YAML structure by field name |  |
| `yamlFindObjectsWithField` | 2 | Find all objects containing specific field |  |
| `yamlFromEntries` | 1 | Create YAML object from entries array |  |
| `yamlGetDocument` | 3 | Get specific document from multi-document YAML by index |  |
| `yamlHasRequiredFields` | 1 | Check if YAML has required fields |  |
| `yamlKeys` | 1 | Get all keys from a YAML object |  |
| `yamlMerge` | 1 | Deep merge two YAML objects |  |
| `yamlMergeAll` | 1 | Merge multiple YAML documents in order |  |
| `yamlMergeDocuments` | 1 | Merge multiple YAML documents into single multi-document string |  |
| `yamlOmitKeys` | 1 | Omit specific keys from YAML object |  |
| `yamlPath` | 3 | Query YAML using path expression |  |
| `yamlSelectKeys` | 1 | Select specific keys from YAML object |  |
| `yamlSet` | 3 | Set value at YAML path |  |
| `yamlSort` | 2 | Sort YAML object keys alphabetically |  |
| `yamlSplitDocuments` | 1 | Split multi-document YAML into array of documents |  |
| `yamlValidate` | 2 | Validate YAML syntax |  |
| `yamlValidateKeyPattern` | 2 | Validate all keys match a pattern |  |
| `yamlValues` | 1 | Get all values from a YAML object |  |

