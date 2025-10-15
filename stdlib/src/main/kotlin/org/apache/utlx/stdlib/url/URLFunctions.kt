package org.apache.utlx.stdlib.url

import org.apache.utlx.core.udm.UDM
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
    
    /**
     * URL encode a string
     * 
     * Usage: urlEncode("Hello World!") => "Hello+World%21"
     * Usage: urlEncode("user@example.com") => "user%40example.com"
     */
    fun urlEncode(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("urlEncode expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar) {
            throw IllegalArgumentException("urlEncode expects a scalar value")
        }
        
        val str = value.value?.toString() ?: ""
        val encoded = URLEncoder.encode(str, StandardCharsets.UTF_8)
        
        return UDM.Scalar(encoded)
    }
    
    /**
     * URL decode a string
     * 
     * Usage: urlDecode("Hello+World%21") => "Hello World!"
     * Usage: urlDecode("user%40example.com") => "user@example.com"
     */
    fun urlDecode(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("urlDecode expects 1 argument, got ${args.size}")
        }
        
        val value = args[0]
        if (value !is UDM.Scalar || value.value !is String) {
            throw IllegalArgumentException("urlDecode expects a string")
        }
        
        val decoded = URLDecoder.decode(value.value as String, StandardCharsets.UTF_8)
        
        return UDM.Scalar(decoded)
    }
    
    // ==================== URL MANIPULATION ====================
    
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
        
        return try {
            URI(url.value as String)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }
    
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
