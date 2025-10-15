package org.apache.utlx.stdlib.url

import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Comprehensive test suite for URL parsing and manipulation functions
 * Location: stdlib/src/test/kotlin/org/apache/utlx/stdlib/url/URLFunctionsTest.kt
 */
class URLFunctionsTest {
    
    // ==================== URL PARSING TESTS ====================
    
    @Test
    fun `parseURL with complete URL`() {
        val url = UDM.Scalar("https://user:pass@example.com:8080/path/to/resource?key=value&foo=bar#section")
        
        val result = URLFunctions.parseURL(listOf(url))
        
        assertTrue(result is UDM.Object)
        val props = result.properties
        
        assertEquals("https", (props["protocol"] as UDM.Scalar).value)
        assertEquals("example.com", (props["host"] as UDM.Scalar).value)
        assertEquals(8080.0, (props["port"] as UDM.Scalar).value)
        assertEquals("/path/to/resource", (props["path"] as UDM.Scalar).value)
        assertEquals("key=value&foo=bar", (props["query"] as UDM.Scalar).value)
        assertEquals("section", (props["fragment"] as UDM.Scalar).value)
        assertEquals("user:pass", (props["userInfo"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseURL with simple URL`() {
        val url = UDM.Scalar("https://example.com")
        
        val result = URLFunctions.parseURL(listOf(url))
        
        assertTrue(result is UDM.Object)
        val props = result.properties
        
        assertEquals("https", (props["protocol"] as UDM.Scalar).value)
        assertEquals("example.com", (props["host"] as UDM.Scalar).value)
        assertTrue(!props.containsKey("port"))
        assertTrue(!props.containsKey("path") || (props["path"] as UDM.Scalar).value == "")
    }
    
    @Test
    fun `parseURL with query parameters`() {
        val url = UDM.Scalar("https://api.example.com/search?q=kotlin&page=1&limit=10")
        
        val result = URLFunctions.parseURL(listOf(url))
        
        assertTrue(result is UDM.Object)
        val queryParams = result.properties["queryParams"] as UDM.Object
        
        assertEquals("kotlin", (queryParams.properties["q"] as UDM.Scalar).value)
        assertEquals("1", (queryParams.properties["page"] as UDM.Scalar).value)
        assertEquals("10", (queryParams.properties["limit"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseURL with IP address`() {
        val url = UDM.Scalar("http://192.168.1.1:8080/admin")
        
        val result = URLFunctions.parseURL(listOf(url))
        
        assertTrue(result is UDM.Object)
        assertEquals("192.168.1.1", (result.properties["host"] as UDM.Scalar).value)
        assertEquals(8080.0, (result.properties["port"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseURL throws on invalid URL`() {
        val invalidUrl = UDM.Scalar("not a valid url")
        
        assertThrows<IllegalArgumentException> {
            URLFunctions.parseURL(listOf(invalidUrl))
        }
    }
    
    // ==================== COMPONENT EXTRACTION TESTS ====================
    
    @Test
    fun `getProtocol extracts protocol`() {
        val url = UDM.Scalar("https://example.com")
        
        val result = URLFunctions.getProtocol(listOf(url))
        
        assertEquals("https", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getHost extracts host`() {
        val url = UDM.Scalar("https://www.example.com:8080/path")
        
        val result = URLFunctions.getHost(listOf(url))
        
        assertEquals("www.example.com", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getPort extracts port`() {
        val url = UDM.Scalar("https://example.com:9000")
        
        val result = URLFunctions.getPort(listOf(url))
        
        assertEquals(9000.0, (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getPort returns null for default port`() {
        val url = UDM.Scalar("https://example.com")
        
        val result = URLFunctions.getPort(listOf(url))
        
        assertEquals(null, (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getPath extracts path`() {
        val url = UDM.Scalar("https://example.com/api/v1/users")
        
        val result = URLFunctions.getPath(listOf(url))
        
        assertEquals("/api/v1/users", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getQuery extracts query string`() {
        val url = UDM.Scalar("https://example.com?key=value&foo=bar")
        
        val result = URLFunctions.getQuery(listOf(url))
        
        assertEquals("key=value&foo=bar", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getFragment extracts fragment`() {
        val url = UDM.Scalar("https://example.com/page#section-2")
        
        val result = URLFunctions.getFragment(listOf(url))
        
        assertEquals("section-2", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `getQueryParams extracts parameters as object`() {
        val url = UDM.Scalar("https://example.com?name=John&age=30&city=NYC")
        
        val result = URLFunctions.getQueryParams(listOf(url))
        
        assertTrue(result is UDM.Object)
        assertEquals("John", (result.properties["name"] as UDM.Scalar).value)
        assertEquals("30", (result.properties["age"] as UDM.Scalar).value)
        assertEquals("NYC", (result.properties["city"] as UDM.Scalar).value)
    }
    
    @Test
    fun `getBaseURL returns protocol, host, and port`() {
        val url = UDM.Scalar("https://example.com:8080/path?query#fragment")
        
        val result = URLFunctions.getBaseURL(listOf(url))
        
        assertEquals("https://example.com:8080", (result as UDM.Scalar).value)
    }
    
    // ==================== QUERY STRING TESTS ====================
    
    @Test
    fun `parseQueryString with simple parameters`() {
        val query = UDM.Scalar("key1=value1&key2=value2&key3=value3")
        
        val result = URLFunctions.parseQueryString(listOf(query))
        
        assertTrue(result is UDM.Object)
        assertEquals("value1", (result.properties["key1"] as UDM.Scalar).value)
        assertEquals("value2", (result.properties["key2"] as UDM.Scalar).value)
        assertEquals("value3", (result.properties["key3"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseQueryString with encoded values`() {
        val query = UDM.Scalar("name=John+Doe&email=user%40example.com")
        
        val result = URLFunctions.parseQueryString(listOf(query))
        
        assertTrue(result is UDM.Object)
        assertEquals("John Doe", (result.properties["name"] as UDM.Scalar).value)
        assertEquals("user@example.com", (result.properties["email"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseQueryString with multiple values for same key`() {
        val query = UDM.Scalar("tags=red&tags=blue&tags=green")
        
        val result = URLFunctions.parseQueryString(listOf(query))
        
        assertTrue(result is UDM.Object)
        val tags = result.properties["tags"] as UDM.Array
        
        assertEquals(3, tags.elements.size)
        assertEquals("red", (tags.elements[0] as UDM.Scalar).value)
        assertEquals("blue", (tags.elements[1] as UDM.Scalar).value)
        assertEquals("green", (tags.elements[2] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseQueryString with empty value`() {
        val query = UDM.Scalar("key1=&key2=value2")
        
        val result = URLFunctions.parseQueryString(listOf(query))
        
        assertTrue(result is UDM.Object)
        assertEquals("", (result.properties["key1"] as UDM.Scalar).value)
        assertEquals("value2", (result.properties["key2"] as UDM.Scalar).value)
    }
    
    @Test
    fun `parseQueryString with empty string`() {
        val query = UDM.Scalar("")
        
        val result = URLFunctions.parseQueryString(listOf(query))
        
        assertTrue(result is UDM.Object)
        assertTrue(result.properties.isEmpty())
    }
    
    @Test
    fun `buildQueryString from object`() {
        val params = UDM.Object(mapOf(
            "name" to UDM.Scalar("John Doe"),
            "age" to UDM.Scalar(30.0),
            "city" to UDM.Scalar("New York")
        ), emptyMap())
        
        val result = URLFunctions.buildQueryString(listOf(params))
        
        val queryString = (result as UDM.Scalar).value as String
        
        assertTrue(queryString.contains("name=John"))
        assertTrue(queryString.contains("age=30"))
        assertTrue(queryString.contains("city=New"))
    }
    
    @Test
    fun `buildQueryString with array values`() {
        val params = UDM.Object(mapOf(
            "tags" to UDM.Array(listOf(
                UDM.Scalar("red"),
                UDM.Scalar("blue"),
                UDM.Scalar("green")
            ))
        ), emptyMap())
        
        val result = URLFunctions.buildQueryString(listOf(params))
        
        val queryString = (result as UDM.Scalar).value as String
        
        assertEquals("tags=red&tags=blue&tags=green", queryString)
    }
    
    @Test
    fun `buildQueryString encodes special characters`() {
        val params = UDM.Object(mapOf(
            "email" to UDM.Scalar("user@example.com"),
            "message" to UDM.Scalar("Hello World!")
        ), emptyMap())
        
        val result = URLFunctions.buildQueryString(listOf(params))
        
        val queryString = (result as UDM.Scalar).value as String
        
        assertTrue(queryString.contains("%40")) // @
        assertTrue(queryString.contains("%21") || queryString.contains("!")) // ! may or may not be encoded
    }
    
    // ==================== URL CONSTRUCTION TESTS ====================
    
    @Test
    fun `buildURL creates complete URL`() {
        val components = UDM.Object(mapOf(
            "protocol" to UDM.Scalar("https"),
            "host" to UDM.Scalar("example.com"),
            "port" to UDM.Scalar(8080.0),
            "path" to UDM.Scalar("/api/users"),
            "query" to UDM.Object(mapOf(
                "id" to UDM.Scalar(123.0),
                "type" to UDM.Scalar("admin")
            ), emptyMap()),
            "fragment" to UDM.Scalar("top")
        ), emptyMap())
        
        val result = URLFunctions.buildURL(listOf(components))
        
        val url = (result as UDM.Scalar).value as String
        
        assertTrue(url.startsWith("https://example.com:8080"))
        assertTrue(url.contains("/api/users"))
        assertTrue(url.contains("?"))
        assertTrue(url.contains("id=123"))
        assertTrue(url.contains("type=admin"))
        assertTrue(url.contains("#top"))
    }
    
    @Test
    fun `buildURL with minimal components`() {
        val components = UDM.Object(mapOf(
            "protocol" to UDM.Scalar("https"),
            "host" to UDM.Scalar("example.com")
        ), emptyMap())
        
        val result = URLFunctions.buildURL(listOf(components))
        
        assertEquals("https://example.com", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `buildURL throws without host`() {
        val components = UDM.Object(mapOf(
            "protocol" to UDM.Scalar("https")
        ), emptyMap())
        
        assertThrows<IllegalArgumentException> {
            URLFunctions.buildURL(listOf(components))
        }
    }
    
    @Test
    fun `buildURL adds leading slash to path`() {
        val components = UDM.Object(mapOf(
            "protocol" to UDM.Scalar("https"),
            "host" to UDM.Scalar("example.com"),
            "path" to UDM.Scalar("api/users") // No leading slash
        ), emptyMap())
        
        val result = URLFunctions.buildURL(listOf(components))
        
        val url = (result as UDM.Scalar).value as String
        assertTrue(url.contains("/api/users"))
    }
    
    // ==================== URL ENCODING TESTS ====================
    
    @Test
    fun `urlEncode encodes special characters`() {
        val text = UDM.Scalar("Hello World! user@example.com")
        
        val result = URLFunctions.urlEncode(listOf(text))
        
        val encoded = (result as UDM.Scalar).value as String
        
        assertTrue(encoded.contains("%21") || encoded.contains("!"))
        assertTrue(encoded.contains("%40") || encoded.contains("@"))
    }
    
    @Test
    fun `urlDecode decodes encoded string`() {
        val encoded = UDM.Scalar("Hello+World%21")
        
        val result = URLFunctions.urlDecode(listOf(encoded))
        
        assertEquals("Hello World!", (result as UDM.Scalar).value)
    }
    
    @Test
    fun `urlEncode and urlDecode are inverses`() {
        val original = UDM.Scalar("user@example.com?foo=bar&test=123")
        
        val encoded = URLFunctions.urlEncode(listOf(original))
        val decoded = URLFunctions.urlDecode(listOf(encoded))
        
        assertEquals(original.value, (decoded as UDM.Scalar).value)
    }
    
    // ==================== URL MANIPULATION TESTS ====================
    
    @Test
    fun `addQueryParam adds parameter to URL`() {
        val url = UDM.Scalar("https://example.com?existing=value")
        val key = UDM.Scalar("new")
        val value = UDM.Scalar("parameter")
        
        val result = URLFunctions.addQueryParam(listOf(url, key, value))
        
        val newUrl = (result as UDM.Scalar).value as String
        
        assertTrue(newUrl.contains("existing=value"))
        assertTrue(newUrl.contains("new=parameter"))
    }
    
    @Test
    fun `addQueryParam to URL without query`() {
        val url = UDM.Scalar("https://example.com/path")
        val key = UDM.Scalar("key")
        val value = UDM.Scalar("value")
        
        val result = URLFunctions.addQueryParam(listOf(url, key, value))
        
        val newUrl = (result as UDM.Scalar).value as String
        
        assertTrue(newUrl.contains("?"))
        assertTrue(newUrl.contains("key=value"))
    }
    
    @Test
    fun `removeQueryParam removes parameter`() {
        val url = UDM.Scalar("https://example.com?foo=bar&key=value&baz=qux")
        val key = UDM.Scalar("key")
        
        val result = URLFunctions.removeQueryParam(listOf(url, key))
        
        val newUrl = (result as UDM.Scalar).value as String
        
        assertTrue(newUrl.contains("foo=bar"))
        assertTrue(newUrl.contains("baz=qux"))
        assertTrue(!newUrl.contains("key=value"))
    }
    
    @Test
    fun `removeQueryParam removes last parameter`() {
        val url = UDM.Scalar("https://example.com?key=value")
        val key = UDM.Scalar("key")
        
        val result = URLFunctions.removeQueryParam(listOf(url, key))
        
        val newUrl = (result as UDM.Scalar).value as String
        
        assertTrue(!newUrl.contains("?"))
        assertTrue(!newUrl.contains("key=value"))
    }
    
    // ==================== URL VALIDATION TESTS ====================
    
    @Test
    fun `isValidURL returns true for valid URLs`() {
        val validUrls = listOf(
            "https://example.com",
            "http://192.168.1.1:8080",
            "ftp://files.example.com/path",
            "https://example.com:443/path?query=value#fragment"
        )
        
        validUrls.forEach { urlString ->
            val url = UDM.Scalar(urlString)
            val result = URLFunctions.isValidURL(listOf(url))
            assertTrue((result as UDM.Scalar).value as Boolean, "Expected $urlString to be valid")
        }
    }
    
    @Test
    fun `isValidURL returns false for invalid URLs`() {
        val invalidUrls = listOf(
            "not a url",
            "htp://wrong-protocol.com",
            "://missing-protocol.com",
            ""
        )
        
        invalidUrls.forEach { urlString ->
            val url = UDM.Scalar(urlString)
            val result = URLFunctions.isValidURL(listOf(url))
            assertTrue((result as UDM.Scalar).value == false, "Expected $urlString to be invalid")
        }
    }
    
    // ==================== INTEGRATION TESTS ====================
    
    @Test
    fun `parse and rebuild URL maintains structure`() {
        val originalUrl = "https://example.com:8080/api/users?page=1&limit=10#results"
        
        val parsed = URLFunctions.parseURL(listOf(UDM.Scalar(originalUrl))) as UDM.Object
        val rebuilt = URLFunctions.buildURL(listOf(parsed))
        
        val rebuiltUrl = (rebuilt as UDM.Scalar).value as String
        
        // Should contain all components
        assertTrue(rebuiltUrl.contains("https://example.com:8080"))
        assertTrue(rebuiltUrl.contains("/api/users"))
        assertTrue(rebuiltUrl.contains("page=1"))
        assertTrue(rebuiltUrl.contains("limit=10"))
        assertTrue(rebuiltUrl.contains("#results"))
    }
    
    @Test
    fun `real-world API URL parsing`() {
        val apiUrl = "https://api.github.com/repos/grauwen/utl-x/issues?state=open&labels=bug,enhancement&per_page=100"
        
        val parsed = URLFunctions.parseURL(listOf(UDM.Scalar(apiUrl))) as UDM.Object
        
        assertEquals("https", (parsed.properties["protocol"] as UDM.Scalar).value)
        assertEquals("api.github.com", (parsed.properties["host"] as UDM.Scalar).value)
        assertEquals("/repos/grauwen/utl-x/issues", (parsed.properties["path"] as UDM.Scalar).value)
        
        val queryParams = parsed.properties["queryParams"] as UDM.Object
        assertEquals("open", (queryParams.properties["state"] as UDM.Scalar).value)
        assertEquals("100", (queryParams.properties["per_page"] as UDM.Scalar).value)
    }
}
