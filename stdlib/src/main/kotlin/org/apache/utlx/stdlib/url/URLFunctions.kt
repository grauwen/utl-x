package org.apache.utlx.stdlib.url

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.encoding.EncodingFunctions
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * URL Parsing and Manipulation Functions
 * 
 * Complete URL utility module for parsing, constructing, and manipulating URLs.
 * Provides functionality similar to DataWeave's dw::core::URL module.
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/url/URLFunctions.kt
 */
object URLFunctions {
    
    // ==================== URL PARSING ====================
    
    @UTLXFunction(
        description = "Parse URL into components",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "url: Url value"
        ],
        returns = "Result of the operation",
        example = "parseURL(\"https://example.com:8080/path?key=value#section\")",
        notes = """Result: {
protocol: "https",
host: "example.com",
port: 8080,
path: "/path",
query: "key=value",
fragment: "section",
queryParams: {key: "value"}
}
CRITICAL: Primary function for URL decomposition""",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse URL into components
     * 
     * Usage: parseURL("https://example.com:8080/path?key=value#section")
     * Result: {
     *   protocol: "https",
     *   host: "example.com",
     *   port: 8080,
     *   path: "/path",
     *   query: "key=value",
     *   fragment: "section",
     *   queryParams: {key: "value"}
     * }
     * 
     * CRITICAL: Primary function for URL decomposition
     */
    fun parseURL(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("parseURL expects 1 argument, got ${args.size}")
        }
        
        val url = args[0]
        if (url !is UDM.Scalar || url.value !is String) {
            throw IllegalArgumentException("parseURL expects a string URL")
        }
        
        val urlString = url.value as String
        
        return try {
            val uri = URI(urlString)
            
            val components = mutableMapOf<String, UDM>()
            
            // Protocol/Scheme
            uri.scheme?.let { components["protocol"] = UDM.Scalar(it) }
            
            // Host
            uri.host?.let { components["host"] = UDM.Scalar(it) }
            
            // Port
            if (uri.port != -1) {
                components["port"] = UDM.Scalar(uri.port.toDouble())
            }
            
            // Path
            uri.path?.let { 
                if (it.isNotEmpty()) {
                    components["path"] = UDM.Scalar(it)
                }
            }
            
            // Query string
            uri.query?.let { 
                components["query"] = UDM.Scalar(it)
                components["queryParams"] = parseQueryString(it)
            }
            
            // Fragment
            uri.fragment?.let { components["fragment"] = UDM.Scalar(it) }
            
            // Authority (userInfo@host:port)
            uri.authority?.let { components["authority"] = UDM.Scalar(it) }
            
            // User info
            uri.userInfo?.let { components["userInfo"] = UDM.Scalar(it) }
            
            // Full URL
            components["url"] = UDM.Scalar(urlString)
            
            UDM.Object(components, emptyMap())
            
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $urlString - ${e.message}")
        }
    }
    
    @UTLXFunction(
        description = "Get protocol/scheme from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getProtocol(\"https://example.com\") => \"https\"",
        additionalExamples = [
            "getProtocol(\"ftp://files.example.com\") => \"ftp\""
        ],
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get protocol/scheme from URL
     * 
     * Usage: getProtocol("https://example.com") => "https"
     * Usage: getProtocol("ftp://files.example.com") => "ftp"
     */
    fun getProtocol(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getProtocol expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["protocol"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get host from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getHost(\"https://example.com:8080/path\") => \"example.com\"",
        additionalExamples = [
            "getHost(\"http://192.168.1.1\") => \"192.168.1.1\""
        ],
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get host from URL
     * 
     * Usage: getHost("https://example.com:8080/path") => "example.com"
     * Usage: getHost("http://192.168.1.1") => "192.168.1.1"
     */
    fun getHost(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getHost expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["host"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get port from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getPort(\"https://example.com:8080\") => 8080",
        additionalExamples = [
            "getPort(\"https://example.com\") => null  (default port)"
        ],
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get port from URL
     * 
     * Usage: getPort("https://example.com:8080") => 8080
     * Usage: getPort("https://example.com") => null  (default port)
     */
    fun getPort(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getPort expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["port"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get path from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getPath(\"https://example.com/api/users\") => \"/api/users\"",
        additionalExamples = [
            "getPath(\"https://example.com\") => null"
        ],
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get path from URL
     * 
     * Usage: getPath("https://example.com/api/users") => "/api/users"
     * Usage: getPath("https://example.com") => null
     */
    fun getPath(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getPath expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["path"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get query string from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getQuery(\"https://example.com?key=value&foo=bar\") => \"key=value&foo=bar\"",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get query string from URL
     * 
     * Usage: getQuery("https://example.com?key=value&foo=bar") => "key=value&foo=bar"
     */
    fun getQuery(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getQuery expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["query"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get fragment/hash from URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getFragment(\"https://example.com#section\") => \"section\"",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get fragment/hash from URL
     * 
     * Usage: getFragment("https://example.com#section") => "section"
     */
    fun getFragment(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getFragment expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["fragment"] ?: UDM.Scalar(null)
    }
    
    @UTLXFunction(
        description = "Get query parameters as object",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getQueryParams(\"https://example.com?name=John&age=30\")",
        notes = "Result: {name: \"John\", age: \"30\"}",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get query parameters as object
     * 
     * Usage: getQueryParams("https://example.com?name=John&age=30")
     * Result: {name: "John", age: "30"}
     */
    fun getQueryParams(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getQueryParams expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        return parsed.properties["queryParams"] ?: UDM.Object(emptyMap(), emptyMap())
    }
    
    // ==================== QUERY STRING PARSING ====================
    
    /**
     * Parse query string to object
     * 
     * Usage: parseQueryString("key1=value1&key2=value2")
     * Result: {key1: "value1", key2: "value2"}
     * 
     * Usage: parseQueryString("tags=red&tags=blue&tags=green")
     * Result: {tags: ["red", "blue", "green"]}
     * 
     * Handles:
     * - Multiple values for same key (becomes array)
     * - URL-encoded values
     * - Empty values
     */
    fun parseQueryString(queryString: String): UDM {
        if (queryString.isEmpty()) {
            return UDM.Object(emptyMap(), emptyMap())
        }
        
        val params = mutableMapOf<String, MutableList<String>>()
        
        queryString.split("&").forEach { pair ->
            if (pair.isNotEmpty()) {
                val parts = pair.split("=", limit = 2)
                val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
                val value = if (parts.size > 1) {
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                } else {
                    ""
                }
                
                params.getOrPut(key) { mutableListOf() }.add(value)
            }
        }
        
        // Convert to UDM
        val result = params.mapValues { (_, values) ->
            if (values.size == 1) {
                UDM.Scalar(values[0])
            } else {
                UDM.Array(values.map { UDM.Scalar(it) })
            }
        }
        
        return UDM.Object(result, emptyMap())
    }
    
    @UTLXFunction(
        description = "Parse query string from string (public wrapper)",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "parseQueryString(...) => result",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Parse query string from string (public wrapper)
     */
    fun parseQueryString(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("parseQueryString expects 1 argument, got ${args.size}")
        }
        
        val query = args[0]
        if (query !is UDM.Scalar || query.value !is String) {
            throw IllegalArgumentException("parseQueryString expects a string")
        }
        
        return parseQueryString(query.value as String)
    }
    
    @UTLXFunction(
        description = "Build query string from object",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "buildQueryString({name: \"John Doe\", age: 30})",
        additionalExamples = [
            "buildQueryString({tags: [\"red\", \"blue\", \"green\"]})"
        ],
        notes = """Result: "name=John%20Doe&age=30"
Result: "tags=red&tags=blue&tags=green"""",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Build query string from object
     * 
     * Usage: buildQueryString({name: "John Doe", age: 30})
     * Result: "name=John%20Doe&age=30"
     * 
     * Usage: buildQueryString({tags: ["red", "blue", "green"]})
     * Result: "tags=red&tags=blue&tags=green"
     */
    fun buildQueryString(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("buildQueryString expects 1 argument, got ${args.size}")
        }
        
        val params = args[0]
        if (params !is UDM.Object) {
            throw IllegalArgumentException("buildQueryString expects an object")
        }
        
        val pairs = mutableListOf<String>()
        
        params.properties.forEach { (key, value) ->
            val encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
            
            when (value) {
                is UDM.Scalar -> {
                    val encodedValue = URLEncoder.encode(
                        value.value?.toString() ?: "", 
                        StandardCharsets.UTF_8
                    )
                    pairs.add("$encodedKey=$encodedValue")
                }
                is UDM.Array -> {
                    value.elements.forEach { elem ->
                        if (elem is UDM.Scalar) {
                            val encodedValue = URLEncoder.encode(
                                elem.value?.toString() ?: "", 
                                StandardCharsets.UTF_8
                            )
                            pairs.add("$encodedKey=$encodedValue")
                        }
                    }
                }
                else -> {
                    // Skip complex objects
                }
            }
        }
        
        return UDM.Scalar(pairs.joinToString("&"))
    }
    
    // ==================== URL CONSTRUCTION ====================
    
    @UTLXFunction(
        description = "Build URL from components",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "components: Components value"
        ],
        returns = "Result of the operation",
        example = "buildURL({",
        notes = """protocol: "https",
host: "example.com",
port: 8080,
path: "/api/users",
query: {id: 123, type: "admin"}
})
Result: "https://example.com:8080/api/users?id=123&type=admin"""",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Build URL from components
     * 
     * Usage: buildURL({
     *   protocol: "https",
     *   host: "example.com",
     *   port: 8080,
     *   path: "/api/users",
     *   query: {id: 123, type: "admin"}
     * })
     * Result: "https://example.com:8080/api/users?id=123&type=admin"
     */
    fun buildURL(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("buildURL expects 1 argument, got ${args.size}")
        }
        
        val components = args[0]
        if (components !is UDM.Object) {
            throw IllegalArgumentException("buildURL expects an object")
        }
        
        val props = components.properties
        
        val url = buildString {
            // Protocol
            (props["protocol"] as? UDM.Scalar)?.value?.let {
                append(it)
                append("://")
            }
            
            // Host
            (props["host"] as? UDM.Scalar)?.value?.let {
                append(it)
            } ?: throw IllegalArgumentException("buildURL requires 'host' property")
            
            // Port
            (props["port"] as? UDM.Scalar)?.value?.let {
                if (it is Number) {
                    append(":")
                    append(it.toInt())
                }
            }
            
            // Path
            (props["path"] as? UDM.Scalar)?.value?.let {
                val path = it.toString()
                if (!path.startsWith("/")) {
                    append("/")
                }
                append(path)
            }
            
            // Query
            val queryString = when (val query = props["query"]) {
                is UDM.Scalar -> query.value?.toString()
                is UDM.Object -> {
                    val qs = buildQueryString(listOf(query)) as UDM.Scalar
                    qs.value as? String
                }
                else -> null
            }
            
            queryString?.let {
                if (it.isNotEmpty()) {
                    append("?")
                    append(it)
                }
            }
            
            // Fragment
            (props["fragment"] as? UDM.Scalar)?.value?.let {
                append("#")
                append(it)
            }
        }
        
        return UDM.Scalar(url)
    }
    
    // ==================== URL ENCODING/DECODING ====================
    
    // ==================== URL MANIPULATION ====================
    
    @UTLXFunction(
        description = "Add query parameter to URL",
        minArgs = 3,
        maxArgs = 3,
        category = "Other",
        parameters = [
            "url: Url value",
        "key: Key value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "addQueryParam(\"https://example.com?foo=bar\", \"key\", \"value\")",
        notes = "Result: \"https://example.com?foo=bar&key=value\"",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Add query parameter to URL
     * 
     * Usage: addQueryParam("https://example.com?foo=bar", "key", "value")
     * Result: "https://example.com?foo=bar&key=value"
     */
    fun addQueryParam(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw IllegalArgumentException("addQueryParam expects 3 arguments (url, key, value), got ${args.size}")
        }
        
        val url = args[0]
        val key = args[1]
        val value = args[2]
        
        if (url !is UDM.Scalar || url.value !is String) {
            throw IllegalArgumentException("addQueryParam expects string URL")
        }
        if (key !is UDM.Scalar) {
            throw IllegalArgumentException("addQueryParam expects scalar key")
        }
        
        val parsed = parseURL(listOf(url)) as UDM.Object
        val queryParams = (parsed.properties["queryParams"] as? UDM.Object)?.properties?.toMutableMap() 
            ?: mutableMapOf()
        
        // Add or update parameter
        queryParams[key.value.toString()] = value
        
        // Rebuild URL
        val components = parsed.properties.toMutableMap()
        components["query"] = UDM.Object(queryParams, emptyMap())
        
        return buildURL(listOf(UDM.Object(components, emptyMap())))
    }
    
    @UTLXFunction(
        description = "Remove query parameter from URL",
        minArgs = 2,
        maxArgs = 2,
        category = "Other",
        parameters = [
            "url: Url value",
        "key: Key value"
        ],
        returns = "Result of the operation",
        example = "removeQueryParam(\"https://example.com?foo=bar&key=value\", \"key\")",
        notes = "Result: \"https://example.com?foo=bar\"",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Remove query parameter from URL
     * 
     * Usage: removeQueryParam("https://example.com?foo=bar&key=value", "key")
     * Result: "https://example.com?foo=bar"
     */
    fun removeQueryParam(args: List<UDM>): UDM {
        if (args.size != 2) {
            throw IllegalArgumentException("removeQueryParam expects 2 arguments (url, key), got ${args.size}")
        }
        
        val url = args[0]
        val key = args[1]
        
        if (url !is UDM.Scalar || url.value !is String) {
            throw IllegalArgumentException("removeQueryParam expects string URL")
        }
        if (key !is UDM.Scalar) {
            throw IllegalArgumentException("removeQueryParam expects scalar key")
        }
        
        val parsed = parseURL(listOf(url)) as UDM.Object
        val queryParams = (parsed.properties["queryParams"] as? UDM.Object)?.properties?.toMutableMap() 
            ?: mutableMapOf()
        
        // Remove parameter
        queryParams.remove(key.value.toString())
        
        // Rebuild URL
        val components = parsed.properties.toMutableMap()
        if (queryParams.isEmpty()) {
            components.remove("query")
            components.remove("queryParams")
        } else {
            components["query"] = UDM.Object(queryParams, emptyMap())
        }
        
        return buildURL(listOf(UDM.Object(components, emptyMap())))
    }
    
    @UTLXFunction(
        description = "Validate URL",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        parameters = [
            "url: Url value"
        ],
        returns = "Boolean indicating the result",
        example = "isValidURL(\"https://example.com\") => true",
        additionalExamples = [
            "isValidURL(\"not a url\") => false"
        ],
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Validate URL
     * 
     * Usage: isValidURL("https://example.com") => true
     * Usage: isValidURL("not a url") => false
     */
    fun isValidURL(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("isValidURL expects 1 argument, got ${args.size}")
        }
        
        val url = args[0]
        if (url !is UDM.Scalar || url.value !is String) {
            return UDM.Scalar(false)
        }

        val urlString = url.value as String
        if (urlString.isEmpty()) {
            return UDM.Scalar(false)
        }

        return try {
            val uri = URI(urlString)
            // URI must have a scheme to be a valid URL
            UDM.Scalar(uri.scheme != null)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }
    
    @UTLXFunction(
        description = "Get base URL (protocol + host + port)",
        minArgs = 1,
        maxArgs = 1,
        category = "Other",
        returns = "Result of the operation",
        example = "getBaseURL(\"https://example.com:8080/path?query#fragment\")",
        notes = "Result: \"https://example.com:8080\"",
        tags = ["other"],
        since = "1.0"
    )
    /**
     * Get base URL (protocol + host + port)
     * 
     * Usage: getBaseURL("https://example.com:8080/path?query#fragment")
     * Result: "https://example.com:8080"
     */
    fun getBaseURL(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("getBaseURL expects 1 argument, got ${args.size}")
        }
        
        val parsed = parseURL(args) as UDM.Object
        val props = parsed.properties
        
        val baseURL = buildString {
            (props["protocol"] as? UDM.Scalar)?.value?.let {
                append(it)
                append("://")
            }
            
            (props["host"] as? UDM.Scalar)?.value?.let {
                append(it)
            }
            
            (props["port"] as? UDM.Scalar)?.value?.let {
                if (it is Number) {
                    append(":")
                    append(it.toInt())
                }
            }
        }
        
        return UDM.Scalar(baseURL)
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to a new registerURLFunctions() method:
 * 
 * private fun registerURLFunctions() {
 *     // URL parsing
 *     register("parseURL", URLFunctions::parseURL)
 *     register("getProtocol", URLFunctions::getProtocol)
 *     register("getHost", URLFunctions::getHost)
 *     register("getPort", URLFunctions::getPort)
 *     register("getPath", URLFunctions::getPath)
 *     register("getQuery", URLFunctions::getQuery)
 *     register("getFragment", URLFunctions::getFragment)
 *     register("getQueryParams", URLFunctions::getQueryParams)
 *     register("getBaseURL", URLFunctions::getBaseURL)
 *     
 *     // Query string handling
 *     register("parseQueryString", URLFunctions::parseQueryString)
 *     register("buildQueryString", URLFunctions::buildQueryString)
 *     
 *     // URL construction
 *     register("buildURL", URLFunctions::buildURL)
 *     
 *     // URL encoding/decoding (may already exist in encoding module)
 *     register("urlEncode", URLFunctions::urlEncode)
 *     register("urlDecode", URLFunctions::urlDecode)
 *     
 *     // URL manipulation
 *     register("addQueryParam", URLFunctions::addQueryParam)
 *     register("removeQueryParam", URLFunctions::removeQueryParam)
 *     register("isValidURL", URLFunctions::isValidURL)
 * }
 * 
 * Then call in init block:
 * init {
 *     registerConversionFunctions()
 *     registerURLFunctions()  // ADD THIS!
 *     registerStringFunctions()
 *     // ... rest
 * }
 */
