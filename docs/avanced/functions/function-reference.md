# UTL-X Standard Library Reference

**Generated**: 2025-10-19 00:12:56  
**Total Functions**: 635  
**Version**: 1.0

## Overview

UTL-X provides 635 built-in functions across 8 categories for data transformation, processing, and integration tasks.

## Quick Reference

| Category | Functions | Description |
|----------|-----------|-------------|
| Math | 8 | Mathematical operations, arithmetic, and numeric functions |
| Other | 574 | Utility functions, system operations, and specialized tools |
| Encoding | 12 | Base64, URL encoding/decoding, and cryptographic hashing |
| JSON | 8 | JSON canonicalization, formatting, and processing |
| XML | 11 | XML parsing, encoding detection, and namespace handling |
| String | 9 | Text processing, case conversion, and string manipulation |
| Array | 10 | Functional operations, filtering, mapping, and array manipulation |
| Date | 3 | Date/time parsing, formatting, and manipulation |

## Function Categories

### Array Functions (10)

Functional operations, filtering, mapping, and array manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `every` | 2 | Check if all elements match a condition |  |
| `filter` | 2 | Filter array elements that match a condition |  |
| `find` | 2 | Find first element matching a condition |  |
| `findIndex` | 2 | Find index of first element matching condition |  |
| `join` | var | No description available |  |
| `length` | var | No description available |  |
| `map` | 2 | Transform each element of an array using a function |  |
| `reduce` | 3 | Reduce array to single value using accumulator function |  |
| `slice` | var | No description available |  |
| `some` | 2 | Check if any element matches a condition |  |

### Date Functions (3)

Date/time parsing, formatting, and manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `formatDate` | var | No description available |  |
| `now` | var | No description available |  |
| `parseDate` | var | No description available |  |

### Encoding Functions (12)

Base64, URL encoding/decoding, and cryptographic hashing

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `base64Decode` | 1 | Decode Base64 encoded string |  |
| `base64Encode` | 1 | Encode string to Base64 format |  |
| `md5` | var | No description available |  |
| `sha1` | var | No description available |  |
| `sha224` | var | No description available |  |
| `sha256` | var | No description available |  |
| `sha384` | var | No description available |  |
| `sha3_256` | var | No description available |  |
| `sha3_512` | var | No description available |  |
| `sha512` | var | No description available |  |
| `urlDecode` | 1 | URL decode encoded string |  |
| `urlEncode` | 1 | URL encode string for web safety |  |

### JSON Functions (8)

JSON canonicalization, formatting, and processing

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `canonicalJSONHash` | var | No description available |  |
| `canonicalJSONSize` | var | No description available |  |
| `canonicalizeJSON` | var | No description available |  |
| `compactJSON` | var | No description available |  |
| `isCanonicalJSON` | var | No description available |  |
| `jsonEquals` | var | No description available |  |
| `prettyPrintJSON` | var | No description available |  |
| `udmToJSON` | var | No description available |  |

### Math Functions (8)

Mathematical operations, arithmetic, and numeric functions

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `abs` | 1 | Return absolute value of a number |  |
| `avg` | 1 | Calculate average of numeric array elements |  |
| `ceil` | 1 | Round number up to next integer |  |
| `floor` | 1 | Round number down to previous integer |  |
| `max` | 1 | Find maximum value in numeric array |  |
| `min` | 1 | Find minimum value in numeric array |  |
| `round` | 1 | Round number to nearest integer |  |
| `sum` | 1 | Calculate sum of numeric array elements |  |

### Other Functions (574)

Utility functions, system operations, and specialized tools

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `acos` | var | No description available |  |
| `addBOM` | var | No description available |  |
| `addDays` | var | No description available |  |
| `addHours` | var | No description available |  |
| `addMinutes` | var | No description available |  |
| `addMonths` | var | No description available |  |
| `addNamespaceDeclarations` | var | No description available |  |
| `addQuarters` | var | No description available |  |
| `addQueryParam` | var | No description available |  |
| `addSeconds` | var | No description available |  |
| `addTax` | var | No description available |  |
| `addWeeks` | var | No description available |  |
| `addYears` | var | No description available |  |
| `age` | var | No description available |  |
| `all` | var | No description available |  |
| `analyzeString` | var | No description available |  |
| `and` | var | No description available |  |
| `any` | var | No description available |  |
| `asin` | var | No description available |  |
| `assert` | var | No description available |  |
| `assertEqual` | var | No description available |  |
| `atan` | var | No description available |  |
| `atan2` | var | No description available |  |
| `attribute` | var | No description available |  |
| `attributes` | var | No description available |  |
| `availableProcessors` | var | No description available |  |
| `avgBy` | var | No description available |  |
| `bearing` | var | No description available |  |
| `binaryConcat` | var | No description available |  |
| `binaryEquals` | var | No description available |  |
| `binaryLength` | var | No description available |  |
| `binarySlice` | var | No description available |  |
| `binaryToString` | var | No description available |  |
| `bitwiseAnd` | var | No description available |  |
| `bitwiseNot` | var | No description available |  |
| `bitwiseOr` | var | No description available |  |
| `bitwiseXor` | var | No description available |  |
| `boundingBox` | var | No description available |  |
| `buildContentType` | var | No description available |  |
| `buildMultipart` | var | No description available |  |
| `buildQueryString` | var | No description available |  |
| `buildURL` | var | No description available |  |
| `c14n` | var | No description available |  |
| `c14n11` | var | No description available |  |
| `c14n11WithComments` | var | No description available |  |
| `c14nEquals` | var | No description available |  |
| `c14nFingerprint` | var | No description available |  |
| `c14nHash` | var | No description available |  |
| `c14nPhysical` | var | No description available |  |
| `c14nSubset` | var | No description available |  |
| `c14nWithComments` | var | No description available |  |
| `calculateDiscount` | var | No description available |  |
| `calculateTax` | var | No description available |  |
| `camelize` | var | No description available |  |
| `canCoerce` | var | No description available |  |
| `canonicalizeWithAlgorithm` | var | No description available |  |
| `capitalize` | var | No description available |  |
| `charAt` | var | No description available |  |
| `charCodeAt` | var | No description available |  |
| `childCount` | var | No description available |  |
| `childNames` | var | No description available |  |
| `chunk` | var | No description available |  |
| `clearLogs` | var | No description available |  |
| `coalesce` | var | No description available |  |
| `coerce` | var | No description available |  |
| `coerceAll` | var | No description available |  |
| `compact` | var | No description available |  |
| `compactCSV` | var | No description available |  |
| `compareDates` | var | No description available |  |
| `compoundInterest` | var | No description available |  |
| `compress` | var | No description available |  |
| `concat` | var | No description available |  |
| `constantCase` | var | No description available |  |
| `containsKey` | var | No description available |  |
| `containsValue` | var | No description available |  |
| `convertTimezone` | var | No description available |  |
| `cos` | var | No description available |  |
| `cosh` | var | No description available |  |
| `count` | var | No description available |  |
| `countBy` | var | No description available |  |
| `countEntries` | var | No description available |  |
| `createCDATA` | var | No description available |  |
| `createPart` | var | No description available |  |
| `createQname` | var | No description available |  |
| `createSOAPEnvelope` | var | No description available |  |
| `crossJoin` | var | No description available |  |
| `csvAddColumn` | var | No description available |  |
| `csvCell` | var | No description available |  |
| `csvColumn` | var | No description available |  |
| `csvColumns` | var | No description available |  |
| `csvFilter` | var | No description available |  |
| `csvRemoveColumns` | var | No description available |  |
| `csvRow` | var | No description available |  |
| `csvRows` | var | No description available |  |
| `csvSelectColumns` | var | No description available |  |
| `csvSort` | var | No description available |  |
| `csvSummarize` | var | No description available |  |
| `csvTranspose` | var | No description available |  |
| `currentDate` | var | No description available |  |
| `currentDir` | var | No description available |  |
| `currentTime` | var | No description available |  |
| `day` | var | No description available |  |
| `dayOfWeek` | var | No description available |  |
| `dayOfWeekName` | var | No description available |  |
| `dayOfYear` | var | No description available |  |
| `daysInMonth` | var | No description available |  |
| `daysInYear` | var | No description available |  |
| `debug` | var | No description available |  |
| `debugPrint` | var | No description available |  |
| `debugPrintCompact` | var | No description available |  |
| `decodeJWS` | var | No description available |  |
| `decodeJWT` | var | No description available |  |
| `decompress` | var | No description available |  |
| `decryptAES` | var | No description available |  |
| `decryptAES256` | var | No description available |  |
| `deepClone` | var | No description available |  |
| `deepEquals` | var | No description available |  |
| `deepMerge` | var | No description available |  |
| `deepMergeAll` | var | No description available |  |
| `default` | var | No description available |  |
| `defaultValue` | var | No description available |  |
| `deflate` | var | No description available |  |
| `destinationPoint` | var | No description available |  |
| `detectBOM` | var | No description available |  |
| `diff` | var | No description available |  |
| `diffDays` | var | No description available |  |
| `diffHours` | var | No description available |  |
| `diffMinutes` | var | No description available |  |
| `diffMonths` | var | No description available |  |
| `diffSeconds` | var | No description available |  |
| `diffWeeks` | var | No description available |  |
| `diffYears` | var | No description available |  |
| `difference` | var | No description available |  |
| `distance` | var | No description available |  |
| `distinct` | var | No description available |  |
| `distinctBy` | var | No description available |  |
| `divideBy` | var | No description available |  |
| `dotCase` | var | No description available |  |
| `drop` | var | No description available |  |
| `e` | var | No description available |  |
| `elementPath` | var | No description available |  |
| `encryptAES` | var | No description available |  |
| `encryptAES256` | var | No description available |  |
| `endDebugTimer` | var | No description available |  |
| `endOfDay` | var | No description available |  |
| `endOfMonth` | var | No description available |  |
| `endOfQuarter` | var | No description available |  |
| `endOfWeek` | var | No description available |  |
| `endOfYear` | var | No description available |  |
| `enforceNamespacePrefixes` | var | No description available |  |
| `entries` | var | No description available |  |
| `env` | var | No description available |  |
| `envAll` | var | No description available |  |
| `envOrDefault` | var | No description available |  |
| `environment` | var | No description available |  |
| `equalsBinary` | var | No description available |  |
| `error` | var | No description available |  |
| `everyEntry` | var | No description available |  |
| `excC14n` | var | No description available |  |
| `excC14nWithComments` | var | No description available |  |
| `exp` | var | No description available |  |
| `extractCDATA` | var | No description available |  |
| `extractTimestampFromUuidV7` | var | No description available |  |
| `filterEntries` | var | No description available |  |
| `findAllMatches` | var | No description available |  |
| `findLastIndex` | var | No description available |  |
| `first` | var | No description available |  |
| `flatMap` | var | No description available |  |
| `flatten` | var | No description available |  |
| `flattenDeep` | var | No description available |  |
| `formatCurrency` | var | No description available |  |
| `formatDateTimeInTimezone` | var | No description available |  |
| `formatEmptyElements` | var | No description available |  |
| `formatNumber` | var | No description available |  |
| `formatPlural` | var | No description available |  |
| `fromBase64` | var | No description available |  |
| `fromBytes` | var | No description available |  |
| `fromCharCode` | var | No description available |  |
| `fromHex` | var | No description available |  |
| `fromUTC` | var | No description available |  |
| `fullOuterJoin` | var | No description available |  |
| `futureValue` | var | No description available |  |
| `generateBoundary` | var | No description available |  |
| `generateIV` | var | No description available |  |
| `generateKey` | var | No description available |  |
| `generateUuid` | var | No description available |  |
| `generateUuidV4` | var | No description available |  |
| `generateUuidV7` | var | No description available |  |
| `generateUuidV7Batch` | var | No description available |  |
| `geoBearing` | var | No description available |  |
| `geoBounds` | var | No description available |  |
| `geoDestination` | var | No description available |  |
| `geoDistance` | var | No description available |  |
| `geoMidpoint` | var | No description available |  |
| `get` | var | No description available |  |
| `getBOMBytes` | var | No description available |  |
| `getBaseURL` | var | No description available |  |
| `getCurrencyDecimals` | var | No description available |  |
| `getExtension` | var | No description available |  |
| `getFragment` | var | No description available |  |
| `getHost` | var | No description available |  |
| `getJWSAlgorithm` | var | No description available |  |
| `getJWSHeader` | var | No description available |  |
| `getJWSInfo` | var | No description available |  |
| `getJWSKeyId` | var | No description available |  |
| `getJWSPayload` | var | No description available |  |
| `getJWSSigningInput` | var | No description available |  |
| `getJWSTokenType` | var | No description available |  |
| `getJWTAudience` | var | No description available |  |
| `getJWTClaim` | var | No description available |  |
| `getJWTClaims` | var | No description available |  |
| `getJWTIssuer` | var | No description available |  |
| `getJWTSubject` | var | No description available |  |
| `getLogs` | var | No description available |  |
| `getMimeType` | var | No description available |  |
| `getNamespaces` | var | No description available |  |
| `getPath` | var | No description available |  |
| `getPort` | var | No description available |  |
| `getProtocol` | var | No description available |  |
| `getQuery` | var | No description available |  |
| `getQueryParams` | var | No description available |  |
| `getTimezone` | var | No description available |  |
| `getTimezoneName` | var | No description available |  |
| `getTimezoneOffsetHours` | var | No description available |  |
| `getTimezoneOffsetSeconds` | var | No description available |  |
| `getURLPath` | var | No description available |  |
| `getUuidVersion` | var | No description available |  |
| `goldenRatio` | var | No description available |  |
| `groupBy` | var | No description available |  |
| `gunzip` | var | No description available |  |
| `gzip` | var | No description available |  |
| `hasAlpha` | var | No description available |  |
| `hasAttribute` | var | No description available |  |
| `hasBOM` | var | No description available |  |
| `hasEnv` | var | No description available |  |
| `hasNamespace` | var | No description available |  |
| `hasNumeric` | var | No description available |  |
| `hash` | var | No description available |  |
| `head` | var | No description available |  |
| `hexDecode` | var | No description available |  |
| `hexEncode` | var | No description available |  |
| `hmac` | var | No description available |  |
| `hmacBase64` | var | No description available |  |
| `hmacMD5` | var | No description available |  |
| `hmacSHA1` | var | No description available |  |
| `hmacSHA256` | var | No description available |  |
| `hmacSHA384` | var | No description available |  |
| `hmacSHA512` | var | No description available |  |
| `homeDir` | var | No description available |  |
| `hours` | var | No description available |  |
| `if` | var | No description available |  |
| `implies` | var | No description available |  |
| `inCircle` | var | No description available |  |
| `inPolygon` | var | No description available |  |
| `includes` | var | No description available |  |
| `indexOf` | var | No description available |  |
| `inflate` | var | No description available |  |
| `info` | var | No description available |  |
| `insertAfter` | var | No description available |  |
| `insertBefore` | var | No description available |  |
| `intersect` | var | No description available |  |
| `invert` | var | No description available |  |
| `iqr` | var | No description available |  |
| `isAfter` | var | No description available |  |
| `isAlpha` | var | No description available |  |
| `isAlphanumeric` | var | No description available |  |
| `isArray` | var | No description available |  |
| `isAscii` | var | No description available |  |
| `isBefore` | var | No description available |  |
| `isBetween` | var | No description available |  |
| `isBlank` | var | No description available |  |
| `isBoolean` | var | No description available |  |
| `isCDATA` | var | No description available |  |
| `isDebugMode` | var | No description available |  |
| `isDefined` | var | No description available |  |
| `isEmpty` | var | No description available |  |
| `isEmptyElement` | var | No description available |  |
| `isGzipped` | var | No description available |  |
| `isHexadecimal` | var | No description available |  |
| `isJWSFormat` | var | No description available |  |
| `isJWTExpired` | var | No description available |  |
| `isJarFile` | var | No description available |  |
| `isLeapYear` | var | No description available |  |
| `isLowerCase` | var | No description available |  |
| `isNotEmpty` | var | No description available |  |
| `isNull` | var | No description available |  |
| `isNumber` | var | No description available |  |
| `isNumeric` | var | No description available |  |
| `isObject` | var | No description available |  |
| `isPlural` | var | No description available |  |
| `isPointInCircle` | var | No description available |  |
| `isPointInPolygon` | var | No description available |  |
| `isPrintable` | var | No description available |  |
| `isSameDay` | var | No description available |  |
| `isSingular` | var | No description available |  |
| `isString` | var | No description available |  |
| `isToday` | var | No description available |  |
| `isUpperCase` | var | No description available |  |
| `isUuidV7` | var | No description available |  |
| `isValidAmount` | var | No description available |  |
| `isValidCoordinates` | var | No description available |  |
| `isValidCurrency` | var | No description available |  |
| `isValidTimezone` | var | No description available |  |
| `isValidURL` | var | No description available |  |
| `isValidUuid` | var | No description available |  |
| `isWeekday` | var | No description available |  |
| `isWeekend` | var | No description available |  |
| `isWhitespace` | var | No description available |  |
| `isZipArchive` | var | No description available |  |
| `javaVersion` | var | No description available |  |
| `jcs` | var | No description available |  |
| `joinBy` | var | No description available |  |
| `joinToString` | var | No description available |  |
| `joinWith` | var | No description available |  |
| `kebabCase` | var | No description available |  |
| `keys` | var | No description available |  |
| `last` | var | No description available |  |
| `lastIndexOf` | var | No description available |  |
| `leftJoin` | var | No description available |  |
| `leftTrim` | var | No description available |  |
| `listJarEntries` | var | No description available |  |
| `listZipEntries` | var | No description available |  |
| `ln` | var | No description available |  |
| `localName` | var | No description available |  |
| `log` | var | No description available |  |
| `log10` | var | No description available |  |
| `log2` | var | No description available |  |
| `logBase` | var | No description available |  |
| `logCount` | var | No description available |  |
| `logPretty` | var | No description available |  |
| `logSize` | var | No description available |  |
| `logType` | var | No description available |  |
| `mapEntries` | var | No description available |  |
| `mapKeys` | var | No description available |  |
| `mapValues` | var | No description available |  |
| `mask` | var | No description available |  |
| `matches` | var | No description available |  |
| `matchesQname` | var | No description available |  |
| `matchesWhole` | var | No description available |  |
| `maxBy` | var | No description available |  |
| `measure` | var | No description available |  |
| `median` | var | No description available |  |
| `memoryInfo` | var | No description available |  |
| `merge` | var | No description available |  |
| `midpoint` | var | No description available |  |
| `minBy` | var | No description available |  |
| `minutes` | var | No description available |  |
| `mode` | var | No description available |  |
| `month` | var | No description available |  |
| `monthName` | var | No description available |  |
| `namespacePrefix` | var | No description available |  |
| `namespaceUri` | var | No description available |  |
| `nand` | var | No description available |  |
| `nodeType` | var | No description available |  |
| `none` | var | No description available |  |
| `nor` | var | No description available |  |
| `normalizeBOM` | var | No description available |  |
| `normalizeSpace` | var | No description available |  |
| `not` | var | No description available |  |
| `numberOrDefault` | var | No description available |  |
| `omit` | var | No description available |  |
| `or` | var | No description available |  |
| `osArch` | var | No description available |  |
| `osVersion` | var | No description available |  |
| `pad` | var | No description available |  |
| `padRight` | var | No description available |  |
| `parent` | var | No description available |  |
| `parse` | var | No description available |  |
| `parseBoolean` | var | No description available |  |
| `parseBoundary` | var | No description available |  |
| `parseContentType` | var | No description available |  |
| `parseCsv` | var | No description available |  |
| `parseCurrency` | var | No description available |  |
| `parseDateTimeWithTimezone` | var | No description available |  |
| `parseDouble` | var | No description available |  |
| `parseFloat` | var | No description available |  |
| `parseInt` | var | No description available |  |
| `parseJson` | var | No description available |  |
| `parseNumber` | var | No description available |  |
| `parseQueryString` | var | No description available |  |
| `parseURL` | var | No description available |  |
| `parseXml` | var | No description available |  |
| `parseYaml` | var | No description available |  |
| `partition` | var | No description available |  |
| `pascalCase` | var | No description available |  |
| `patch` | var | No description available |  |
| `pathCase` | var | No description available |  |
| `percentageChange` | var | No description available |  |
| `percentile` | var | No description available |  |
| `pi` | var | No description available |  |
| `pick` | var | No description available |  |
| `platform` | var | No description available |  |
| `pluralize` | var | No description available |  |
| `pluralizeWithCount` | var | No description available |  |
| `pow` | var | No description available |  |
| `prepareForSignature` | var | No description available |  |
| `presentValue` | var | No description available |  |
| `prettyPrint` | var | No description available |  |
| `prettyPrintCSV` | var | No description available |  |
| `prettyPrintFormat` | var | No description available |  |
| `prettyPrintYAML` | var | No description available |  |
| `qualifiedName` | var | No description available |  |
| `quarter` | var | No description available |  |
| `quartiles` | var | No description available |  |
| `random` | var | No description available |  |
| `readByte` | var | No description available |  |
| `readDouble` | var | No description available |  |
| `readFloat` | var | No description available |  |
| `readInt16` | var | No description available |  |
| `readInt32` | var | No description available |  |
| `readInt64` | var | No description available |  |
| `readJarEntry` | var | No description available |  |
| `readJarManifest` | var | No description available |  |
| `readZipEntry` | var | No description available |  |
| `reduceEntries` | var | No description available |  |
| `regexGroups` | var | No description available |  |
| `regexNamedGroups` | var | No description available |  |
| `remove` | var | No description available |  |
| `removeBOM` | var | No description available |  |
| `removeQueryParam` | var | No description available |  |
| `removeTax` | var | No description available |  |
| `render` | var | No description available |  |
| `renderCsv` | var | No description available |  |
| `renderJson` | var | No description available |  |
| `renderXml` | var | No description available |  |
| `renderYaml` | var | No description available |  |
| `repeat` | var | No description available |  |
| `replaceRegex` | var | No description available |  |
| `replaceWithFunction` | var | No description available |  |
| `resolveQname` | var | No description available |  |
| `reverse` | var | No description available |  |
| `reverseString` | var | No description available |  |
| `rightJoin` | var | No description available |  |
| `rightTrim` | var | No description available |  |
| `roundToCents` | var | No description available |  |
| `roundToDecimalPlaces` | var | No description available |  |
| `runtimeInfo` | var | No description available |  |
| `scan` | var | No description available |  |
| `seconds` | var | No description available |  |
| `setConsoleLogging` | var | No description available |  |
| `setLogLevel` | var | No description available |  |
| `setPath` | var | No description available |  |
| `shiftLeft` | var | No description available |  |
| `shiftRight` | var | No description available |  |
| `shouldUseCDATA` | var | No description available |  |
| `simpleInterest` | var | No description available |  |
| `sin` | var | No description available |  |
| `singularize` | var | No description available |  |
| `sinh` | var | No description available |  |
| `size` | var | No description available |  |
| `slugify` | var | No description available |  |
| `smartCoerce` | var | No description available |  |
| `snakeCase` | var | No description available |  |
| `someEntry` | var | No description available |  |
| `sort` | var | No description available |  |
| `sortBy` | var | No description available |  |
| `split` | var | No description available |  |
| `splitWithMatches` | var | No description available |  |
| `sqrt` | var | No description available |  |
| `startDebugTimer` | var | No description available |  |
| `startOfDay` | var | No description available |  |
| `startOfMonth` | var | No description available |  |
| `startOfQuarter` | var | No description available |  |
| `startOfWeek` | var | No description available |  |
| `startOfYear` | var | No description available |  |
| `stdDev` | var | No description available |  |
| `stripBOM` | var | No description available |  |
| `substringAfter` | var | No description available |  |
| `substringAfterLast` | var | No description available |  |
| `substringBefore` | var | No description available |  |
| `substringBeforeLast` | var | No description available |  |
| `sumBy` | var | No description available |  |
| `symmetricDifference` | var | No description available |  |
| `systemPropertiesAll` | var | No description available |  |
| `systemProperty` | var | No description available |  |
| `systemPropertyOrDefault` | var | No description available |  |
| `tail` | var | No description available |  |
| `take` | var | No description available |  |
| `tan` | var | No description available |  |
| `tanh` | var | No description available |  |
| `tempDir` | var | No description available |  |
| `textContent` | var | No description available |  |
| `tibco_parse` | var | No description available |  |
| `tibco_render` | var | No description available |  |
| `timerCheck` | var | No description available |  |
| `timerClear` | var | No description available |  |
| `timerList` | var | No description available |  |
| `timerReset` | var | No description available |  |
| `timerStart` | var | No description available |  |
| `timerStats` | var | No description available |  |
| `timerStop` | var | No description available |  |
| `timestamp` | var | No description available |  |
| `titleCase` | var | No description available |  |
| `toArray` | var | No description available |  |
| `toBase64` | var | No description available |  |
| `toBinary` | var | No description available |  |
| `toBoolean` | var | No description available |  |
| `toBytes` | var | No description available |  |
| `toDegrees` | var | No description available |  |
| `toHex` | var | No description available |  |
| `toNumber` | var | No description available |  |
| `toObject` | var | No description available |  |
| `toRadians` | var | No description available |  |
| `toString` | var | No description available |  |
| `toUTC` | var | No description available |  |
| `trace` | var | No description available |  |
| `translate` | var | No description available |  |
| `transpose` | var | No description available |  |
| `treeDepth` | var | No description available |  |
| `treeFilter` | var | No description available |  |
| `treeFind` | var | No description available |  |
| `treeFlatten` | var | No description available |  |
| `treeMap` | var | No description available |  |
| `treePaths` | var | No description available |  |
| `tryCoerce` | var | No description available |  |
| `typeOf` | var | No description available |  |
| `udmToYAML` | var | No description available |  |
| `uncamelize` | var | No description available |  |
| `union` | var | No description available |  |
| `unique` | var | No description available |  |
| `unwrapCDATA` | var | No description available |  |
| `unzip` | var | No description available |  |
| `unzipArchive` | var | No description available |  |
| `unzipN` | var | No description available |  |
| `update` | var | No description available |  |
| `uptime` | var | No description available |  |
| `username` | var | No description available |  |
| `validCoords` | var | No description available |  |
| `validateDate` | var | No description available |  |
| `validateDigest` | var | No description available |  |
| `validateEncoding` | var | No description available |  |
| `values` | var | No description available |  |
| `variance` | var | No description available |  |
| `version` | var | No description available |  |
| `warn` | var | No description available |  |
| `weekOfYear` | var | No description available |  |
| `windowed` | var | No description available |  |
| `wrapIfNeeded` | var | No description available |  |
| `writeByte` | var | No description available |  |
| `writeDouble` | var | No description available |  |
| `writeFloat` | var | No description available |  |
| `writeInt16` | var | No description available |  |
| `writeInt32` | var | No description available |  |
| `writeInt64` | var | No description available |  |
| `xnor` | var | No description available |  |
| `xor` | var | No description available |  |
| `yamlDelete` | var | No description available |  |
| `yamlEntries` | var | No description available |  |
| `yamlExists` | var | No description available |  |
| `yamlFilterByKeyPattern` | var | No description available |  |
| `yamlFindByField` | var | No description available |  |
| `yamlFindObjectsWithField` | var | No description available |  |
| `yamlFromEntries` | var | No description available |  |
| `yamlGetDocument` | var | No description available |  |
| `yamlHasRequiredFields` | var | No description available |  |
| `yamlKeys` | var | No description available |  |
| `yamlMerge` | var | No description available |  |
| `yamlMergeAll` | var | No description available |  |
| `yamlMergeDocuments` | var | No description available |  |
| `yamlOmitKeys` | var | No description available |  |
| `yamlPath` | var | No description available |  |
| `yamlSelectKeys` | var | No description available |  |
| `yamlSet` | var | No description available |  |
| `yamlSort` | var | No description available |  |
| `yamlSplitDocuments` | var | No description available |  |
| `yamlValidate` | var | No description available |  |
| `yamlValidateKeyPattern` | var | No description available |  |
| `yamlValues` | var | No description available |  |
| `year` | var | No description available |  |
| `zip` | var | No description available |  |
| `zipAll` | var | No description available |  |
| `zipArchive` | var | No description available |  |
| `zipWith` | var | No description available |  |
| `zipWithIndex` | var | No description available |  |

### String Functions (9)

Text processing, case conversion, and string manipulation

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `contains` | var | No description available |  |
| `endsWith` | var | No description available |  |
| `lower` | 1 | Convert string to lowercase |  |
| `replace` | var | No description available |  |
| `startsWith` | var | No description available |  |
| `stringOrDefault` | var | No description available |  |
| `substring` | var | No description available |  |
| `trim` | var | No description available |  |
| `upper` | 1 | Convert string to uppercase |  |

### XML Functions (11)

XML parsing, encoding detection, and namespace handling

| Function | Args | Description | Example |
|----------|------|-------------|----------|
| `compactXML` | var | No description available |  |
| `convertXMLEncoding` | var | No description available |  |
| `detectXMLEncoding` | var | No description available |  |
| `escapeXML` | var | No description available |  |
| `normalizeXMLEncoding` | var | No description available |  |
| `prettyPrintXML` | var | No description available |  |
| `udmToXML` | var | No description available |  |
| `unescapeXML` | var | No description available |  |
| `updateXMLEncoding` | var | No description available |  |
| `xmlEscape` | var | No description available |  |
| `xmlUnescape` | var | No description available |  |

