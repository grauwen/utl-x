package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeFunctionsTest {

    @Test
    fun testEnv() {
        // Test getting a common environment variable that should exist
        val pathResult = RuntimeFunctions.env(listOf(UDM.Scalar("PATH")))
        assertTrue(pathResult is UDM.Scalar)
        val pathValue = (pathResult as UDM.Scalar).value
        assertTrue(pathValue is String || pathValue == null)
        
        // Test getting a non-existent environment variable
        val nonExistentResult = RuntimeFunctions.env(listOf(UDM.Scalar("NON_EXISTENT_ENV_VAR_12345")))
        assertTrue(nonExistentResult is UDM.Scalar)
        assertEquals(null, (nonExistentResult as UDM.Scalar).value)
    }

    @Test
    fun testEnvOrDefault() {
        // Test with existing environment variable
        val pathResult = RuntimeFunctions.envOrDefault(listOf(UDM.Scalar("PATH"), UDM.Scalar("default")))
        assertTrue(pathResult is UDM.Scalar)
        val pathValue = (pathResult as UDM.Scalar).value
        assertTrue(pathValue is String)
        assertTrue(pathValue != "default") // PATH should exist and not be the default
        
        // Test with non-existent environment variable
        val defaultResult = RuntimeFunctions.envOrDefault(listOf(
            UDM.Scalar("NON_EXISTENT_ENV_VAR_12345"), 
            UDM.Scalar("my_default_value")
        ))
        assertEquals("my_default_value", (defaultResult as UDM.Scalar).value)
    }

    @Test
    fun testEnvAll() {
        val result = RuntimeFunctions.envAll(listOf())
        
        assertTrue(result is UDM.Object)
        val envVars = (result as UDM.Object).properties
        assertTrue(envVars.isNotEmpty(), "Should have some environment variables")
        
        // Check that PATH exists (should be present on all systems)
        assertTrue(envVars.containsKey("PATH") || envVars.containsKey("Path"), "PATH should exist in environment")
    }

    @Test
    fun testHasEnv() {
        // Test with existing environment variable
        val pathExists = RuntimeFunctions.hasEnv(listOf(UDM.Scalar("PATH")))
        assertEquals(true, (pathExists as UDM.Scalar).value)
        
        // Test with non-existent environment variable
        val nonExistentExists = RuntimeFunctions.hasEnv(listOf(UDM.Scalar("NON_EXISTENT_ENV_VAR_12345")))
        assertEquals(false, (nonExistentExists as UDM.Scalar).value)
    }

    @Test
    fun testSystemProperty() {
        // Test getting Java version (should always exist)
        val javaVersionResult = RuntimeFunctions.systemProperty(listOf(UDM.Scalar("java.version")))
        assertTrue(javaVersionResult is UDM.Scalar)
        val javaVersion = (javaVersionResult as UDM.Scalar).value
        assertTrue(javaVersion is String && javaVersion.isNotEmpty())
        
        // Test non-existent system property
        val nonExistentResult = RuntimeFunctions.systemProperty(listOf(UDM.Scalar("non.existent.property")))
        assertEquals(null, (nonExistentResult as UDM.Scalar).value)
    }

    @Test
    fun testSystemPropertyOrDefault() {
        // Test with existing system property
        val javaVersionResult = RuntimeFunctions.systemPropertyOrDefault(listOf(
            UDM.Scalar("java.version"), 
            UDM.Scalar("unknown")
        ))
        val javaVersion = (javaVersionResult as UDM.Scalar).value
        assertTrue(javaVersion is String && javaVersion != "unknown")
        
        // Test with non-existent system property
        val defaultResult = RuntimeFunctions.systemPropertyOrDefault(listOf(
            UDM.Scalar("non.existent.property"), 
            UDM.Scalar("default_value")
        ))
        assertEquals("default_value", (defaultResult as UDM.Scalar).value)
    }

    @Test
    fun testSystemPropertiesAll() {
        val result = RuntimeFunctions.systemPropertiesAll(listOf())
        
        assertTrue(result is UDM.Object)
        val properties = (result as UDM.Object).properties
        assertTrue(properties.isNotEmpty(), "Should have system properties")
        
        // Check for some common system properties
        assertTrue(properties.containsKey("java.version"))
        assertTrue(properties.containsKey("os.name"))
        assertTrue(properties.containsKey("user.home"))
    }

    @Test
    fun testVersion() {
        val result = RuntimeFunctions.version(listOf())
        
        assertTrue(result is UDM.Scalar)
        val version = (result as UDM.Scalar).value
        assertTrue(version is String && version.isNotEmpty())
        // Should contain version-like format
        assertTrue(version.toString().matches(Regex(".*\\d+\\.\\d+.*")))
    }

    @Test
    fun testPlatform() {
        val result = RuntimeFunctions.platform(listOf())
        
        assertTrue(result is UDM.Scalar)
        val platform = (result as UDM.Scalar).value as String
        assertTrue(platform.isNotEmpty())
        // Should be one of the common platforms
        val validPlatforms = setOf("windows", "linux", "macos", "unix", "other")
        assertTrue(validPlatforms.any { platform.toLowerCase().contains(it) })
    }

    @Test
    fun testOsVersion() {
        val result = RuntimeFunctions.osVersion(listOf())
        
        assertTrue(result is UDM.Scalar)
        val osVersion = (result as UDM.Scalar).value
        assertTrue(osVersion is String && osVersion.isNotEmpty())
    }

    @Test
    fun testOsArch() {
        val result = RuntimeFunctions.osArch(listOf())
        
        assertTrue(result is UDM.Scalar)
        val arch = (result as UDM.Scalar).value as String
        assertTrue(arch.isNotEmpty())
        // Common architectures
        val validArchs = setOf("x86", "x64", "amd64", "arm", "aarch64")
        assertTrue(validArchs.any { arch.toLowerCase().contains(it) })
    }

    @Test
    fun testJavaVersion() {
        val result = RuntimeFunctions.javaVersion(listOf())
        
        assertTrue(result is UDM.Scalar)
        val javaVersion = (result as UDM.Scalar).value as String
        assertTrue(javaVersion.isNotEmpty())
        // Should contain version numbers
        assertTrue(javaVersion.matches(Regex(".*\\d+.*")))
    }

    @Test
    fun testAvailableProcessors() {
        val result = RuntimeFunctions.availableProcessors(listOf())
        
        assertTrue(result is UDM.Scalar)
        val processors = (result as UDM.Scalar).value as Number
        assertTrue(processors.toInt() >= 1, "Should have at least 1 processor")
        assertTrue(processors.toInt() <= 256, "Reasonable upper bound for processors")
    }

    @Test
    fun testMemoryInfo() {
        val result = RuntimeFunctions.memoryInfo(listOf())
        
        assertTrue(result is UDM.Object)
        val memInfo = (result as UDM.Object).properties
        
        assertTrue(memInfo.containsKey("totalMemory"))
        assertTrue(memInfo.containsKey("freeMemory"))
        assertTrue(memInfo.containsKey("maxMemory"))
        assertTrue(memInfo.containsKey("usedMemory"))
        
        val totalMemory = (memInfo["totalMemory"] as UDM.Scalar).value as Number
        val freeMemory = (memInfo["freeMemory"] as UDM.Scalar).value as Number
        val maxMemory = (memInfo["maxMemory"] as UDM.Scalar).value as Number
        val usedMemory = (memInfo["usedMemory"] as UDM.Scalar).value as Number
        
        assertTrue(totalMemory.toLong() > 0)
        assertTrue(freeMemory.toLong() >= 0)
        assertTrue(maxMemory.toLong() > 0)
        assertTrue(usedMemory.toLong() >= 0)
        assertTrue(totalMemory.toLong() >= freeMemory.toLong())
    }

    @Test
    fun testCurrentDir() {
        val result = RuntimeFunctions.currentDir(listOf())
        
        assertTrue(result is UDM.Scalar)
        val currentDir = (result as UDM.Scalar).value as String
        assertTrue(currentDir.isNotEmpty())
        // Should be an absolute path
        assertTrue(currentDir.startsWith("/") || currentDir.matches(Regex("[A-Za-z]:\\\\.*")))
    }

    @Test
    fun testHomeDir() {
        val result = RuntimeFunctions.homeDir(listOf())
        
        assertTrue(result is UDM.Scalar)
        val homeDir = (result as UDM.Scalar).value as String
        assertTrue(homeDir.isNotEmpty())
        // Should be an absolute path
        assertTrue(homeDir.startsWith("/") || homeDir.matches(Regex("[A-Za-z]:\\\\.*")))
    }

    @Test
    fun testTempDir() {
        val result = RuntimeFunctions.tempDir(listOf())
        
        assertTrue(result is UDM.Scalar)
        val tempDir = (result as UDM.Scalar).value as String
        assertTrue(tempDir.isNotEmpty())
        // Should be an absolute path
        assertTrue(tempDir.startsWith("/") || tempDir.matches(Regex("[A-Za-z]:\\\\.*")))
    }

    @Test
    fun testUsername() {
        val result = RuntimeFunctions.username(listOf())
        
        assertTrue(result is UDM.Scalar)
        val username = (result as UDM.Scalar).value
        assertTrue(username is String || username == null)
        if (username is String) {
            assertTrue(username.isNotEmpty())
        }
    }

    @Test
    fun testUptime() {
        val result = RuntimeFunctions.uptime(listOf())
        
        assertTrue(result is UDM.Scalar)
        val uptime = (result as UDM.Scalar).value as Number
        assertTrue(uptime.toLong() >= 0, "Uptime should be non-negative")
    }

    @Test
    fun testRuntimeInfo() {
        val result = RuntimeFunctions.runtimeInfo(listOf())
        
        assertTrue(result is UDM.Object)
        val runtimeInfo = (result as UDM.Object).properties
        
        // Should contain comprehensive runtime information
        assertTrue(runtimeInfo.containsKey("javaVersion"))
        assertTrue(runtimeInfo.containsKey("osName"))
        assertTrue(runtimeInfo.containsKey("osVersion"))
        assertTrue(runtimeInfo.containsKey("osArch"))
        assertTrue(runtimeInfo.containsKey("availableProcessors"))
        assertTrue(runtimeInfo.containsKey("maxMemory"))
        assertTrue(runtimeInfo.containsKey("totalMemory"))
        assertTrue(runtimeInfo.containsKey("freeMemory"))
        assertTrue(runtimeInfo.containsKey("currentDir"))
        assertTrue(runtimeInfo.containsKey("homeDir"))
        assertTrue(runtimeInfo.containsKey("tempDir"))
        assertTrue(runtimeInfo.containsKey("uptime"))
    }

    @Test
    fun testIsDebugMode() {
        val result = RuntimeFunctions.isDebugMode(listOf())
        
        assertTrue(result is UDM.Scalar)
        val isDebug = (result as UDM.Scalar).value
        assertTrue(isDebug is Boolean)
    }

    @Test
    fun testEnvironment() {
        val result = RuntimeFunctions.environment(listOf())
        
        assertTrue(result is UDM.Scalar)
        val environment = (result as UDM.Scalar).value as String
        assertTrue(environment.isNotEmpty())
        // Should be one of common environment types
        val validEnvironments = setOf("development", "production", "testing", "staging", "unknown")
        assertTrue(validEnvironments.contains(environment.toLowerCase()))
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testEnvInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.env(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.env(listOf(UDM.Array(emptyList())))
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.env(listOf(UDM.Scalar("VAR1"), UDM.Scalar("VAR2")))
        }
    }

    @Test
    fun testEnvOrDefaultInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.envOrDefault(listOf(UDM.Scalar("VAR")))
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.envOrDefault(listOf(UDM.Object(emptyMap(), emptyMap()), UDM.Scalar("default")))
        }
    }

    @Test
    fun testEnvAllInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.envAll(listOf(UDM.Scalar("unexpected")))
        }
    }

    @Test
    fun testHasEnvInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.hasEnv(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.hasEnv(listOf(UDM.Scalar(null)))
        }
    }

    @Test
    fun testSystemPropertyInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.systemProperty(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.systemProperty(listOf(UDM.Scalar(123)))
        }
    }

    @Test
    fun testNoArgumentFunctionsWithArguments() {
        // Functions that should take no arguments
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.version(listOf(UDM.Scalar("unexpected")))
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.platform(listOf(UDM.Scalar("unexpected")))
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.availableProcessors(listOf(UDM.Scalar("unexpected")))
        }
        
        assertThrows<FunctionArgumentException> {
            RuntimeFunctions.memoryInfo(listOf(UDM.Scalar("unexpected")))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testEnvWithEmptyString() {
        val result = RuntimeFunctions.env(listOf(UDM.Scalar("")))
        assertEquals(null, (result as UDM.Scalar).value)
    }

    @Test
    fun testEnvCaseSensitivity() {
        // Environment variables are typically case-sensitive on Unix, case-insensitive on Windows
        val pathLower = RuntimeFunctions.env(listOf(UDM.Scalar("path")))
        val pathUpper = RuntimeFunctions.env(listOf(UDM.Scalar("PATH")))
        
        // At least one should exist
        val pathLowerValue = (pathLower as UDM.Scalar).value
        val pathUpperValue = (pathUpper as UDM.Scalar).value
        assertTrue(pathLowerValue != null || pathUpperValue != null)
    }

    @Test
    fun testSystemPropertyWithJavaPrefix() {
        val result = RuntimeFunctions.systemProperty(listOf(UDM.Scalar("java.home")))
        
        assertTrue(result is UDM.Scalar)
        val javaHome = (result as UDM.Scalar).value
        assertTrue(javaHome is String && javaHome.isNotEmpty())
    }

    @Test
    fun testMemoryInfoValues() {
        val result = RuntimeFunctions.memoryInfo(listOf())
        val memInfo = (result as UDM.Object).properties
        
        val totalMemory = (memInfo["totalMemory"] as UDM.Scalar).value as Number
        val freeMemory = (memInfo["freeMemory"] as UDM.Scalar).value as Number
        val maxMemory = (memInfo["maxMemory"] as UDM.Scalar).value as Number
        val usedMemory = (memInfo["usedMemory"] as UDM.Scalar).value as Number
        
        // Logical consistency checks
        assertTrue(usedMemory.toLong() <= totalMemory.toLong(), "Used memory should not exceed total memory")
        assertTrue(totalMemory.toLong() <= maxMemory.toLong(), "Total memory should not exceed max memory")
        assertTrue(freeMemory.toLong() <= totalMemory.toLong(), "Free memory should not exceed total memory")
    }

    @Test
    fun testRuntimeInfoConsistency() {
        val result = RuntimeFunctions.runtimeInfo(listOf())
        val runtimeInfo = (result as UDM.Object).properties
        
        // Cross-check with individual function calls
        val separateJavaVersion = RuntimeFunctions.javaVersion(listOf())
        val separateOsName = RuntimeFunctions.platform(listOf())
        val separateProcessors = RuntimeFunctions.availableProcessors(listOf())
        
        assertEquals(
            (separateJavaVersion as UDM.Scalar).value,
            (runtimeInfo["javaVersion"] as UDM.Scalar).value
        )
        
        assertEquals(
            (separateProcessors as UDM.Scalar).value,
            (runtimeInfo["availableProcessors"] as UDM.Scalar).value
        )
    }
}