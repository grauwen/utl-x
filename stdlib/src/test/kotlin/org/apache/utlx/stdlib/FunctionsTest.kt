package org.apache.utlx.stdlib

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class FunctionsTest {

    @Test
    fun testStandardLibraryInitialization() {
        // Test that StandardLibrary initializes without errors
        val functionCount = StandardLibrary.getFunctionCount()
        assertTrue(functionCount > 0, "StandardLibrary should have registered functions")
        assertTrue(functionCount >= 100, "Should have at least 100 functions registered")
    }

    @Test
    fun testGetAllFunctionNames() {
        val functionNames = StandardLibrary.getAllFunctionNames()
        
        assertTrue(functionNames.isNotEmpty())
        assertTrue(functionNames.size >= 100) // Should have many functions
        
        // Test some core functions are present
        assertTrue(functionNames.contains("if"))
        assertTrue(functionNames.contains("map"))
        assertTrue(functionNames.contains("filter"))
        assertTrue(functionNames.contains("reduce"))
        assertTrue(functionNames.contains("upper"))
        assertTrue(functionNames.contains("lower"))
        assertTrue(functionNames.contains("abs"))
        assertTrue(functionNames.contains("now"))
        assertTrue(functionNames.contains("getType"))
    }

    @Test
    fun testHasFunction() {
        // Test core functions
        assertTrue(StandardLibrary.hasFunction("if"))
        assertTrue(StandardLibrary.hasFunction("coalesce"))
        assertTrue(StandardLibrary.hasFunction("isEmpty"))
        assertTrue(StandardLibrary.hasFunction("contains"))
        
        // Test string functions
        assertTrue(StandardLibrary.hasFunction("upper"))
        assertTrue(StandardLibrary.hasFunction("lower"))
        assertTrue(StandardLibrary.hasFunction("trim"))
        assertTrue(StandardLibrary.hasFunction("split"))
        
        // Test array functions
        assertTrue(StandardLibrary.hasFunction("map"))
        assertTrue(StandardLibrary.hasFunction("filter"))
        assertTrue(StandardLibrary.hasFunction("reduce"))
        assertTrue(StandardLibrary.hasFunction("flatten"))
        
        // Test math functions
        assertTrue(StandardLibrary.hasFunction("abs"))
        assertTrue(StandardLibrary.hasFunction("round"))
        assertTrue(StandardLibrary.hasFunction("ceil"))
        assertTrue(StandardLibrary.hasFunction("floor"))
        
        // Test non-existent function
        assertTrue(!StandardLibrary.hasFunction("nonExistentFunction"))
    }

    @Test
    fun testGetFunction() {
        // Test getting a function
        val mapFunction = StandardLibrary.getFunction("map")
        assertNotNull(mapFunction)
        assertEquals("map", mapFunction.name)
        
        val upperFunction = StandardLibrary.getFunction("upper")
        assertNotNull(upperFunction)
        assertEquals("upper", upperFunction.name)
        
        // Test non-existent function
        val nonExistent = StandardLibrary.getFunction("nonExistentFunction")
        assertEquals(null, nonExistent)
    }

    @Test
    fun testLookupFunction() {
        // Test lookup returns implementation
        val mapImpl = StandardLibrary.lookup("map")
        assertNotNull(mapImpl)
        
        val upperImpl = StandardLibrary.lookup("upper")
        assertNotNull(upperImpl)
        
        // Test non-existent function
        val nonExistentImpl = StandardLibrary.lookup("nonExistentFunction")
        assertEquals(null, nonExistentImpl)
    }

    @Test
    fun testGetAllFunctions() {
        val allFunctions = StandardLibrary.getAllFunctions()
        
        assertTrue(allFunctions.isNotEmpty())
        assertTrue(allFunctions.size >= 100)
        
        // Test that functions are properly mapped
        assertTrue(allFunctions.containsKey("map"))
        assertTrue(allFunctions.containsKey("filter"))
        assertTrue(allFunctions.containsKey("upper"))
        assertTrue(allFunctions.containsKey("abs"))
        
        // Test function objects are properly created
        val mapFunc = allFunctions["map"]
        assertNotNull(mapFunc)
        assertEquals("map", mapFunc.name)
    }

    @Test
    fun testCoreFunctions() {
        // Test that all core control flow functions are registered
        assertTrue(StandardLibrary.hasFunction("if"))
        assertTrue(StandardLibrary.hasFunction("coalesce"))
        assertTrue(StandardLibrary.hasFunction("generateUuid"))
        assertTrue(StandardLibrary.hasFunction("default"))
        assertTrue(StandardLibrary.hasFunction("isEmpty"))
        assertTrue(StandardLibrary.hasFunction("isNotEmpty"))
        assertTrue(StandardLibrary.hasFunction("contains"))
        assertTrue(StandardLibrary.hasFunction("concat"))
    }

    @Test
    fun testStringFunctions() {
        val stringFunctions = listOf(
            "upper", "lower", "trim", "substring", "split", "joinBy", "replace",
            "startsWith", "endsWith", "length", "matches", "replaceRegex",
            "substringBefore", "substringAfter", "pad", "repeat",
            "leftTrim", "rightTrim", "translate", "reverse", "isBlank",
            "charAt", "capitalize", "titleCase", "camelize", "pascalCase",
            "kebabCase", "snakeCase", "constantCase", "dotCase", "pathCase",
            "uncamelize", "slugify", "pluralize", "singularize"
        )
        
        stringFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing string function: $funcName")
        }
    }

    @Test
    fun testArrayFunctions() {
        val arrayFunctions = listOf(
            "map", "filter", "reduce", "find", "findIndex", "every", "some",
            "flatten", "reverse", "sort", "sortBy", "first", "head", "last",
            "take", "drop", "unique", "zip", "size", "get", "tail", "distinct",
            "distinctBy", "union", "intersect", "difference", "symmetricDifference",
            "flatMap", "flattenDeep", "chunk", "joinToString", "unzip",
            "compact", "windowed", "zipAll"
        )
        
        arrayFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing array function: $funcName")
        }
    }

    @Test
    fun testMathFunctions() {
        val mathFunctions = listOf(
            "abs", "round", "ceil", "floor", "pow", "sqrt", "random",
            "formatNumber", "parseInt", "parseFloat", "median", "mode",
            "stdDev", "variance", "percentile", "quartiles", "iqr",
            "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
            "sinh", "cosh", "tanh", "ln", "log", "log10", "log2", "exp",
            "toRadians", "toDegrees", "pi", "e", "goldenRatio"
        )
        
        mathFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing math function: $funcName")
        }
    }

    @Test
    fun testDateFunctions() {
        val dateFunctions = listOf(
            "now", "parseDate", "formatDate", "addDays", "addHours", "diffDays",
            "day", "month", "year", "hours", "minutes", "seconds",
            "addMonths", "addYears", "addMinutes", "addSeconds", "getTimezone",
            "diffHours", "diffMinutes", "diffSeconds", "currentDate", "currentTime",
            "convertTimezone", "toUTC", "fromUTC", "addWeeks", "addQuarters",
            "startOfDay", "endOfDay", "startOfWeek", "endOfWeek", "startOfMonth",
            "endOfMonth", "startOfYear", "endOfYear", "dayOfWeek", "quarter",
            "isLeapYear", "daysInMonth", "isBefore", "isAfter", "isSameDay",
            "isToday", "isWeekend", "age"
        )
        
        dateFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing date function: $funcName")
        }
    }

    @Test
    fun testTypeFunctions() {
        val typeFunctions = listOf(
            "getType", "isString", "isNumber", "isBoolean", "isArray", "isObject",
            "isNull", "isDefined", "parseNumber", "toNumber", "parseInt",
            "parseFloat", "parseDouble", "toString", "parseBoolean", "toBoolean",
            "toArray", "toObject", "numberOrDefault", "stringOrDefault"
        )
        
        typeFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing type function: $funcName")
        }
    }

    @Test
    fun testObjectFunctions() {
        val objectFunctions = listOf(
            "keys", "values", "entries", "merge", "pick", "omit",
            "containsKey", "containsValue", "invert", "deepMerge", "deepMergeAll",
            "deepClone", "getPath", "setPath", "divideBy", "someEntry",
            "everyEntry", "mapEntries", "filterEntries", "reduceEntries",
            "countEntries", "mapKeys", "mapValues"
        )
        
        objectFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing object function: $funcName")
        }
    }

    @Test
    fun testEncodingFunctions() {
        val encodingFunctions = listOf(
            "base64Encode", "base64Decode", "urlEncode", "urlDecode",
            "hexEncode", "hexDecode", "md5", "sha1", "sha256", "sha512",
            "hash", "hmac", "hmacMD5", "hmacSHA1", "hmacSHA256", "hmacSHA384",
            "hmacSHA512", "encryptAES", "decryptAES", "sha224", "sha384",
            "generateIV", "generateKey"
        )
        
        encodingFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing encoding function: $funcName")
        }
    }

    @Test
    fun testXMLFunctions() {
        val xmlFunctions = listOf(
            "localName", "namespaceUri", "qualifiedName", "namespacePrefix",
            "resolveQname", "createQname", "hasNamespace", "getNamespaces",
            "matchesQname", "nodeType", "textContent", "attributes", "attribute",
            "hasAttribute", "childCount", "childNames", "parent", "elementPath",
            "isEmptyElement", "xmlEscape", "xmlUnescape", "createCDATA", "isCDATA",
            "extractCDATA", "unwrapCDATA", "c14n", "c14nWithComments", "excC14n",
            "canonicalizeWithAlgorithm"
        )
        
        xmlFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing XML function: $funcName")
        }
    }

    @Test
    fun testLogicalFunctions() {
        val logicalFunctions = listOf(
            "not", "xor", "and", "or", "nand", "nor", "xnor", "implies",
            "all", "any", "none"
        )
        
        logicalFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing logical function: $funcName")
        }
    }

    @Test
    fun testUtilityFunctions() {
        val utilityFunctions = listOf(
            "parseURL", "getProtocol", "getHost", "getPort", "getPath",
            "getQuery", "getFragment", "getQueryParams", "parseQueryString",
            "buildQueryString", "buildURL", "addQueryParam", "removeQueryParam",
            "isValidURL", "decodeJWT", "getJWTClaims", "getJWTClaim",
            "isJWTExpired", "getJWTSubject", "getJWTIssuer", "getJWTAudience"
        )
        
        utilityFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing utility function: $funcName")
        }
    }

    @Test
    fun testAdvancedFunctions() {
        val advancedFunctions = listOf(
            "treeMap", "treeFilter", "treeFlatten", "treeDepth", "treePaths",
            "treeFind", "coerce", "tryCoerce", "canCoerce", "coerceAll",
            "smartCoerce", "timerStart", "timerStop", "timerCheck", "timerReset",
            "update", "mask", "pick", "omit", "defaultValue", "diff",
            "deepEquals", "patch", "getMimeType", "getExtension",
            "parseContentType", "buildContentType", "parseBoundary",
            "buildMultipart", "generateBoundary", "createPart"
        )
        
        advancedFunctions.forEach { funcName ->
            assertTrue(StandardLibrary.hasFunction(funcName), "Missing advanced function: $funcName")
        }
    }

    @Test
    fun testUTLXFunctionWrapper() {
        val testFunction = UTLXFunction(
            name = "testFunc",
            implementation = { args -> UDM.Scalar("test") },
            minArgs = 1,
            maxArgs = 3,
            description = "Test function"
        )
        
        assertEquals("testFunc", testFunction.name)
        assertEquals("Test function", testFunction.description)
        assertEquals(1, testFunction.minArgs)
        assertEquals(3, testFunction.maxArgs)
        
        // Test execution with valid args
        val result = testFunction.execute(listOf(UDM.Scalar("arg1")))
        assertEquals("test", (result as UDM.Scalar).value)
        
        // Test execution with too few args
        try {
            testFunction.execute(listOf())
            assertTrue(false, "Should have thrown exception for too few args")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 1 arguments") == true)
        }
        
        // Test execution with too many args
        try {
            testFunction.execute(listOf(UDM.Scalar("1"), UDM.Scalar("2"), UDM.Scalar("3"), UDM.Scalar("4")))
            assertTrue(false, "Should have thrown exception for too many args")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at most 3 arguments") == true)
        }
    }

    @Test
    fun testUTLXFunctionWrapperNoLimits() {
        val testFunction = UTLXFunction(
            name = "unlimitedFunc",
            implementation = { args -> UDM.Scalar(args.size) }
        )
        
        // Should accept any number of arguments
        assertEquals(0, (testFunction.execute(listOf()) as UDM.Scalar).value)
        assertEquals(1, (testFunction.execute(listOf(UDM.Scalar("arg1"))) as UDM.Scalar).value)
        assertEquals(5, (testFunction.execute(listOf(
            UDM.Scalar("1"), UDM.Scalar("2"), UDM.Scalar("3"), UDM.Scalar("4"), UDM.Scalar("5")
        )) as UDM.Scalar).value)
    }

    @Test
    fun testFunctionArgumentException() {
        val exception = FunctionArgumentException("Test argument error")
        assertTrue(exception is FunctionArgumentException)
        assertEquals("Test argument error", exception.message)
    }

    @Test
    fun testFunctionExecutionException() {
        val cause = RuntimeException("Original error")
        val exception = FunctionExecutionException("Test execution error", cause)
        assertTrue(exception is RuntimeException)
        assertEquals("Test execution error", exception.message)
        assertEquals(cause, exception.cause)
        
        val exceptionNoCause = FunctionExecutionException("Error without cause")
        assertEquals("Error without cause", exceptionNoCause.message)
        assertEquals(null, exceptionNoCause.cause)
    }

    @Test
    fun testRegisterFunction() {
        val originalCount = StandardLibrary.getFunctionCount()
        
        val customFunction = UTLXFunction(
            name = "customTest",
            implementation = { args -> UDM.Scalar("custom result") }
        )
        
        StandardLibrary.registerFunction("customTest", customFunction)
        
        assertEquals(originalCount + 1, StandardLibrary.getFunctionCount())
        assertTrue(StandardLibrary.hasFunction("customTest"))
        
        val retrievedFunction = StandardLibrary.getFunction("customTest")
        assertNotNull(retrievedFunction)
        assertEquals("customTest", retrievedFunction.name)
        
        val result = retrievedFunction.execute(listOf())
        assertEquals("custom result", (result as UDM.Scalar).value)
    }

    @Test
    fun testFunctionCountIsReasonable() {
        val count = StandardLibrary.getFunctionCount()

        // Based on the comprehensive function registry, we should have well over 150 functions
        assertTrue(count >= 150, "Expected at least 150 functions, got $count")

        // Updated upper bound to reflect current stdlib size (664 functions as of 2025-10-29)
        // Allows for growth while still catching unreasonable explosion
        assertTrue(count >= 600, "Expected at least 600 functions, got $count")
        assertTrue(count <= 800, "Expected at most 800 functions, got $count")

        println("Total registered functions: $count")
    }
}