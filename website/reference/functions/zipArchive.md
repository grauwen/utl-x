---
title: zipArchive
description: "zipArchive — UTL-X Binary function. Create a zip archive from an object of entries (name -> binary"
pageClass: stdlib-page
---

# zipArchive

<p class="stdlib-meta"><code>zipArchive(entries) → binary</code> · <a href="/reference/stdlib#binary">Binary</a></p>

Create a zip archive from an object of entries (name -\> binary
content).

- `entries` (required): object mapping file names to binary content

``` utlx
let archive = zipArchive({
  "data.json": toBinary(renderJson($input), "UTF-8"),
  "meta.txt": toBinary("generated", "UTF-8")
})
{
  archiveSize: binaryLength(archive)
}
```

## Functions Not Listed Individually

The following function groups follow the same patterns as their listed
counterparts. Use the MCP server (`get_function_info`) or the IDE's
function browser for signatures and examples.

- **Geospatial (12):** `distance`, `bearing`, `midpoint`,
  `destinationPoint`, `boundingBox`, `inCircle`, `inPolygon`,
  `isValidCoordinates`, `geoBearing`, `geoDistance`, `geoMidpoint`,
  `geoBounds`

- **Binary (12):** `binaryConcat`, `binaryEquals`, `binaryLength`,
  `binarySlice`, `binaryToString`, `readByte`, `readInt16`, `readInt32`,
  `readInt64`, `writeByte`, `writeInt16`, `writeInt32`

- **URL (16):** `buildURL`, `parseURL`, `getHost`, `getPath`, `getPort`,
  `getProtocol`, `getQuery`, `getFragment`, `getQueryParams`,
  `buildQueryString`, `addQueryParam`, `removeQueryParam`, `getBaseURL`,
  `getURLPath`, `parseQueryString`, `getExtension`

- **JWT/JWS (18):** `createJWT`, `verifyJWT`, `decodeJWT`,
  `getJWTClaim`, `getJWTClaims`, `getJWTIssuer`, `getJWTSubject`,
  `getJWTAudience`, `isJWTExpired`, `validateJWTStructure`, `decodeJWS`,
  `getJWSHeader`, `getJWSPayload`, `getJWSAlgorithm`, `getJWSKeyId`,
  `isJWSFormat`, `getJWSInfo`, `getJWSSigningInput`

- **Encryption (6):** `encryptAES`, `decryptAES`, `encryptAES256`,
  `decryptAES256`, `generateKey`, `generateIV`

- **Compression (6):** `compress`, `decompress`, `gzip`, `gunzip`,
  `deflate`, `inflate`

- **Timer/Debug (14):** `timerStart`, `timerStop`, `timerStats`,
  `timerCheck`, `timerReset`, `timerClear`, `timerList`, `debug`,
  `debugPrint`, `trace`, `warn`, `info`, `log`, `measure`

- **Tree (7):** `treeMap`, `treeFilter`, `treeFind`, `treeFlatten`,
  `treePaths`, `treeDepth`, `mapTree`

- **Case conversion (12):** `camelCase`, `snakeCase`, `kebabCase`,
  `pascalCase`, `titleCase`, `dotCase`, `pathCase`, `constantCase`,
  `wordCase`, `slugify`, `fromCamelCase`, `fromSnakeCase`

- **Math (12):** `pow`, `sqrt`, `exp`, `ln`, `log10`, `log2`, `sin`,
  `cos`, `tan`, `asin`, `acos`, `atan`

- **Bitwise (6):** `bitwiseAnd`, `bitwiseOr`, `bitwiseXor`,
  `bitwiseNot`, `shiftLeft`, `shiftRight`

- **Financial (6):** `simpleInterest`, `compoundInterest`,
  `presentValue`, `futureValue`, `calculateTax`, `calculateDiscount`

Total: **692 functions** across 16 categories.
