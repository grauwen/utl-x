/**
 * Runtime and System Information Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/core/RuntimeFunctions.kt
 * 
 * Provides access to environment variables, system properties, and runtime information.
 * Essential for environment-specific configuration and diagnostics.
 * 
 * Use Cases:
 * - Reading environment-specific configuration (API URLs, credentials)
 * - Feature flags based on environment
 * - Diagnostics and debugging information
 * - Cross-environment compatibility
 * 
 * Example:
 *   {
 *     apiUrl: env("API_URL") ?: "http://localhost:8080",
 *     debugMode: env("DEBUG") == "true",
 *     platform: platform()
 *   }
 */

package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import java.lang.management.ManagementFactory
import java.util.Properties
import org.apache.utlx.stdlib.annotations.UTLXFunction

object RuntimeFunctions {
    
    // Version information (should be read from build.gradle.kts or MANIFEST.MF)
    private const val UTLX_VERSION = "1.0.0"
    
    @UTLXFunction(
        description = "Get environment variable value",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "key: Key value",
        "default: Default value"
        ],
        returns = "Result of the operation",
        example = "env(...) => result",
        notes = """Example:
env("PATH")
env("API_KEY")""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get environment variable value
     * 
     * @param args[0] key - Environment variable name
     * @return String value or null if not found
     * 
     * Example:
     *   env("PATH")
     *   env("API_KEY")
     */
    fun env(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "env requires 1 argument: key" }
        
        val key = args[0].asString()
        val value = System.getenv(key)
        
        return if (value != null) {
            UDM.fromNative(value)
        } else {
            UDM.Scalar(null)
        }
    }
    
    @UTLXFunction(
        description = "Get environment variable with default fallback",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "key: Key value",
        "default: Default value"
        ],
        returns = "Result of the operation",
        example = "envOrDefault(...) => result",
        notes = """Example:
envOrDefault("API_URL", "http://localhost:8080")
envOrDefault("TIMEOUT", "30")""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get environment variable with default fallback
     * 
     * @param args[0] key - Environment variable name
     * @param args[1] default - Default value if not found
     * @return String value or default
     * 
     * Example:
     *   envOrDefault("API_URL", "http://localhost:8080")
     *   envOrDefault("TIMEOUT", "30")
     */
    fun envOrDefault(args: List<UDM>): UDM {
        require(args.size >= 2) { "envOrDefault requires 2 arguments: key, default" }
        
        val key = args[0].asString()
        val default = args[1].asString()
        val value = System.getenv(key) ?: default
        
        return UDM.fromNative(value)
    }
    
    @UTLXFunction(
        description = "Get all environment variables",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "key: Key value"
        ],
        returns = "Result of the operation",
        example = "envAll(...) => result",
        notes = """Example:
envAll()
// Returns: {PATH: "...", HOME: "...", ...}""",
        tags = ["core", "predicate"],
        since = "1.0"
    )
    /**
     * Get all environment variables
     * 
     * @return Map of all environment variables
     * 
     * Example:
     *   envAll()
     *   // Returns: {PATH: "...", HOME: "...", ...}
     */
    fun envAll(args: List<UDM>): UDM {
        val allEnv = System.getenv()
        return UDM.fromNative(allEnv)
    }
    
    @UTLXFunction(
        description = "Check if environment variable exists",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "key: Key value",
        "default: Default value"
        ],
        returns = "Boolean indicating the result",
        example = "hasEnv(...) => result",
        notes = """Example:
hasEnv("API_KEY")""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Check if environment variable exists
     * 
     * @param args[0] key - Environment variable name
     * @return Boolean true if exists
     * 
     * Example:
     *   hasEnv("API_KEY")
     */
    fun hasEnv(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "hasEnv requires 1 argument: key" }
        
        val key = args[0].asString()
        val exists = System.getenv(key) != null
        
        return UDM.fromNative(exists)
    }
    
    @UTLXFunction(
        description = "Get Java system property",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "key: Key value",
        "default: Default value"
        ],
        returns = "Result of the operation",
        example = "systemProperty(...) => result",
        notes = """Example:
systemProperty("java.version")
systemProperty("user.home")""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get Java system property
     * 
     * @param args[0] key - System property name
     * @return String value or null if not found
     * 
     * Example:
     *   systemProperty("java.version")
     *   systemProperty("user.home")
     */
    fun systemProperty(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "systemProperty requires 1 argument: key" }
        
        val key = args[0].asString()
        val value = System.getProperty(key)
        
        return if (value != null) {
            UDM.fromNative(value)
        } else {
            UDM.Scalar(null)
        }
    }
    
    @UTLXFunction(
        description = "Get system property with default fallback",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "key: Key value",
        "default: Default value"
        ],
        returns = "Result of the operation",
        example = "systemPropertyOrDefault(...) => result",
        notes = """Example:
systemPropertyOrDefault("java.io.tmpdir", "/tmp")""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get system property with default fallback
     * 
     * @param args[0] key - System property name
     * @param args[1] default - Default value if not found
     * @return String value or default
     * 
     * Example:
     *   systemPropertyOrDefault("java.io.tmpdir", "/tmp")
     */
    fun systemPropertyOrDefault(args: List<UDM>): UDM {
        require(args.size >= 2) { "systemPropertyOrDefault requires 2 arguments: key, default" }
        
        val key = args[0].asString()
        val default = args[1].asString()
        val value = System.getProperty(key) ?: default
        
        return UDM.fromNative(value)
    }
    
    @UTLXFunction(
        description = "Get all system properties",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "systemPropertiesAll(...) => result",
        notes = """Example:
systemPropertiesAll()""",
        tags = ["core", "predicate"],
        since = "1.0"
    )
    /**
     * Get all system properties
     * 
     * @return Map of all system properties
     * 
     * Example:
     *   systemPropertiesAll()
     */
    fun systemPropertiesAll(args: List<UDM>): UDM {
        val props = System.getProperties()
        val propsMap = props.entries.associate { 
            it.key.toString() to it.value.toString() 
        }
        return UDM.fromNative(propsMap)
    }
    
    @UTLXFunction(
        description = "Get UTL-X version",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "version(...) => result",
        notes = """Example:
version()  // Returns: "1.0.0"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get UTL-X version
     * 
     * @return String version number
     * 
     * Example:
     *   version()  // Returns: "1.0.0"
     */
    fun version(args: List<UDM>): UDM {
        return UDM.fromNative(UTLX_VERSION)
    }
    
    @UTLXFunction(
        description = "Get platform/operating system name",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "platform(...) => result",
        notes = """Example:
platform()  // Returns: "Linux", "Mac OS X", "Windows 10"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get platform/operating system name
     * 
     * @return String OS name
     * 
     * Example:
     *   platform()  // Returns: "Linux", "Mac OS X", "Windows 10"
     */
    fun platform(args: List<UDM>): UDM {
        val osName = System.getProperty("os.name")
        return UDM.fromNative(osName)
    }
    
    @UTLXFunction(
        description = "Get operating system version",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "osVersion(...) => result",
        notes = """Example:
osVersion()  // Returns: "10.15.7"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get operating system version
     * 
     * @return String OS version
     * 
     * Example:
     *   osVersion()  // Returns: "10.15.7"
     */
    fun osVersion(args: List<UDM>): UDM {
        val osVersion = System.getProperty("os.version")
        return UDM.fromNative(osVersion)
    }
    
    @UTLXFunction(
        description = "Get operating system architecture",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "osArch(...) => result",
        notes = """Example:
osArch()  // Returns: "x86_64"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get operating system architecture
     * 
     * @return String architecture (x86_64, aarch64, etc.)
     * 
     * Example:
     *   osArch()  // Returns: "x86_64"
     */
    fun osArch(args: List<UDM>): UDM {
        val osArch = System.getProperty("os.arch")
        return UDM.fromNative(osArch)
    }
    
    @UTLXFunction(
        description = "Get Java/JVM version",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "javaVersion(...) => result",
        notes = """Example:
javaVersion()  // Returns: "11.0.12"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get Java/JVM version
     * 
     * @return String Java version
     * 
     * Example:
     *   javaVersion()  // Returns: "11.0.12"
     */
    fun javaVersion(args: List<UDM>): UDM {
        val javaVersion = System.getProperty("java.version")
        return UDM.fromNative(javaVersion)
    }
    
    @UTLXFunction(
        description = "Get number of available CPU cores/processors",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "availableProcessors(...) => result",
        notes = """Example:
availableProcessors()  // Returns: 8""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get number of available CPU cores/processors
     * 
     * @return Integer number of processors
     * 
     * Example:
     *   availableProcessors()  // Returns: 8
     */
    fun availableProcessors(args: List<UDM>): UDM {
        val processors = Runtime.getRuntime().availableProcessors()
        return UDM.fromNative(processors)
    }
    
    @UTLXFunction(
        description = "Get JVM memory information in bytes",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "memoryInfo(...) => result",
        notes = """Example:
memoryInfo()
// Returns: {
//   maxMemory: 4294967296,
//   totalMemory: 2147483648,
//   freeMemory: 1073741824,
//   usedMemory: 1073741824
// }""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get JVM memory information in bytes
     * 
     * @return Map with maxMemory, totalMemory, freeMemory, usedMemory
     * 
     * Example:
     *   memoryInfo()
     *   // Returns: {
     *   //   maxMemory: 4294967296,
     *   //   totalMemory: 2147483648,
     *   //   freeMemory: 1073741824,
     *   //   usedMemory: 1073741824
     *   // }
     */
    fun memoryInfo(args: List<UDM>): UDM {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        
        val info = mapOf(
            "maxMemory" to maxMemory,
            "totalMemory" to totalMemory,
            "freeMemory" to freeMemory,
            "usedMemory" to usedMemory,
            "maxMemoryMB" to maxMemory / (1024 * 1024),
            "totalMemoryMB" to totalMemory / (1024 * 1024),
            "freeMemoryMB" to freeMemory / (1024 * 1024),
            "usedMemoryMB" to usedMemory / (1024 * 1024)
        )
        
        return UDM.fromNative(info)
    }
    
    @UTLXFunction(
        description = "Get current working directory",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "currentDir(...) => result",
        notes = """Example:
currentDir()  // Returns: "/home/user/project"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get current working directory
     * 
     * @return String path to current directory
     * 
     * Example:
     *   currentDir()  // Returns: "/home/user/project"
     */
    fun currentDir(args: List<UDM>): UDM {
        val userDir = System.getProperty("user.dir")
        return UDM.fromNative(userDir)
    }
    
    @UTLXFunction(
        description = "Get user home directory",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "homeDir(...) => result",
        notes = """Example:
homeDir()  // Returns: "/home/user"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get user home directory
     * 
     * @return String path to home directory
     * 
     * Example:
     *   homeDir()  // Returns: "/home/user"
     */
    fun homeDir(args: List<UDM>): UDM {
        val userHome = System.getProperty("user.home")
        return UDM.fromNative(userHome)
    }
    
    @UTLXFunction(
        description = "Get temporary directory",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "tempDir(...) => result",
        notes = """Example:
tempDir()  // Returns: "/tmp"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get temporary directory
     * 
     * @return String path to temp directory
     * 
     * Example:
     *   tempDir()  // Returns: "/tmp"
     */
    fun tempDir(args: List<UDM>): UDM {
        val tmpDir = System.getProperty("java.io.tmpdir")
        return UDM.fromNative(tmpDir)
    }
    
    @UTLXFunction(
        description = "Get current username",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "username(...) => result",
        notes = """Example:
username()  // Returns: "john"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get current username
     * 
     * @return String username
     * 
     * Example:
     *   username()  // Returns: "john"
     */
    fun username(args: List<UDM>): UDM {
        val userName = System.getProperty("user.name")
        return UDM.fromNative(userName)
    }
    
    @UTLXFunction(
        description = "Get JVM uptime in milliseconds",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "uptime(...) => result",
        notes = """Example:
uptime()  // Returns: 12345678""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get JVM uptime in milliseconds
     * 
     * @return Long milliseconds since JVM started
     * 
     * Example:
     *   uptime()  // Returns: 12345678
     */
    fun uptime(args: List<UDM>): UDM {
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val uptime = runtimeMXBean.uptime
        return UDM.fromNative(uptime)
    }
    
    @UTLXFunction(
        description = "Get comprehensive runtime information",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "runtimeInfo(...) => result",
        notes = """Example:
runtimeInfo()
// Returns: {
//   utlxVersion: "1.0.0",
//   javaVersion: "11.0.12",
//   osName: "Linux",
//   osVersion: "5.10.0",
//   osArch: "x86_64",
//   availableProcessors: 8,
//   maxMemory: 4294967296,
//   currentDir: "/home/user/project",
//   userHome: "/home/user",
//   userName: "john"
// }""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get comprehensive runtime information
     * 
     * @return Map with all runtime info
     * 
     * Example:
     *   runtimeInfo()
     *   // Returns: {
     *   //   utlxVersion: "1.0.0",
     *   //   javaVersion: "11.0.12",
     *   //   osName: "Linux",
     *   //   osVersion: "5.10.0",
     *   //   osArch: "x86_64",
     *   //   availableProcessors: 8,
     *   //   maxMemory: 4294967296,
     *   //   currentDir: "/home/user/project",
     *   //   userHome: "/home/user",
     *   //   userName: "john"
     *   // }
     */
    fun runtimeInfo(args: List<UDM>): UDM {
        val runtime = Runtime.getRuntime()
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        
        val info = mapOf(
            // UTL-X info
            "utlxVersion" to UTLX_VERSION,
            
            // Java info
            "javaVersion" to System.getProperty("java.version"),
            "javaVendor" to System.getProperty("java.vendor"),
            "javaHome" to System.getProperty("java.home"),
            
            // OS info
            "osName" to System.getProperty("os.name"),
            "osVersion" to System.getProperty("os.version"),
            "osArch" to System.getProperty("os.arch"),
            
            // System resources
            "availableProcessors" to runtime.availableProcessors(),
            "maxMemory" to runtime.maxMemory(),
            "totalMemory" to runtime.totalMemory(),
            "freeMemory" to runtime.freeMemory(),
            "usedMemory" to (runtime.totalMemory() - runtime.freeMemory()),
            
            // Directories
            "currentDir" to System.getProperty("user.dir"),
            "userHome" to System.getProperty("user.home"),
            "tempDir" to System.getProperty("java.io.tmpdir"),
            
            // User info
            "userName" to System.getProperty("user.name"),
            
            // Runtime info
            "uptimeMs" to runtimeMXBean.uptime,
            "startTime" to runtimeMXBean.startTime,
            "pid" to runtimeMXBean.name.split("@").firstOrNull()?.toLongOrNull()
        )
        
        return UDM.fromNative(info)
    }
    
    @UTLXFunction(
        description = "Check if running in debug mode",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Boolean indicating the result",
        example = "isDebugMode(...) => result",
        notes = """Checks for common debug flags in environment and system properties
Example:
isDebugMode()""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Check if running in debug mode
     * Checks for common debug flags in environment and system properties
     * 
     * @return Boolean true if debug mode detected
     * 
     * Example:
     *   isDebugMode()
     */
    fun isDebugMode(args: List<UDM>): UDM {
        val debugFlags = listOf(
            System.getenv("DEBUG"),
            System.getenv("DEBUG_MODE"),
            System.getProperty("debug"),
            System.getProperty("debug.mode")
        )
        
        val isDebug = debugFlags.any { 
            it?.lowercase() in listOf("true", "1", "yes", "on") 
        }
        
        return UDM.fromNative(isDebug)
    }
    
    @UTLXFunction(
        description = "Get current environment name",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        returns = "Result of the operation",
        example = "environment(...) => result",
        notes = """Checks common environment variable patterns
Example:
environment()  // Returns: "production"""",
        tags = ["core"],
        since = "1.0"
    )
    /**
     * Get current environment name
     * Checks common environment variable patterns
     * 
     * @return String environment name (dev, test, staging, prod) or "unknown"
     * 
     * Example:
     *   environment()  // Returns: "production"
     */
    fun environment(args: List<UDM>): UDM {
        val envNames = listOf(
            System.getenv("ENVIRONMENT"),
            System.getenv("ENV"),
            System.getenv("NODE_ENV"),
            System.getenv("RAILS_ENV"),
            System.getProperty("environment"),
            System.getProperty("env")
        )
        
        val env = envNames.firstOrNull { it != null } ?: "unknown"
        
        return UDM.fromNative(env)
    }
}

// ============================================================================
// INTEGRATION INTO Functions.kt
// 
// Add to stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt:
// ============================================================================

/*

import org.apache.utlx.stdlib.core.RuntimeFunctions

// In registerCoreFunctions() or create new registerRuntimeFunctions():
private fun registerRuntimeFunctions() {
    // Environment variables
    register("env", RuntimeFunctions::env)
    register("envOrDefault", RuntimeFunctions::envOrDefault)
    register("envAll", RuntimeFunctions::envAll)
    register("hasEnv", RuntimeFunctions::hasEnv)
    
    // System properties
    register("systemProperty", RuntimeFunctions::systemProperty)
    register("systemPropertyOrDefault", RuntimeFunctions::systemPropertyOrDefault)
    register("systemPropertiesAll", RuntimeFunctions::systemPropertiesAll)
    
    // Version and platform
    register("version", RuntimeFunctions::version)
    register("platform", RuntimeFunctions::platform)
    register("osVersion", RuntimeFunctions::osVersion)
    register("osArch", RuntimeFunctions::osArch)
    register("javaVersion", RuntimeFunctions::javaVersion)
    
    // System resources
    register("availableProcessors", RuntimeFunctions::availableProcessors)
    register("memoryInfo", RuntimeFunctions::memoryInfo)
    
    // Directories
    register("currentDir", RuntimeFunctions::currentDir)
    register("homeDir", RuntimeFunctions::homeDir)
    register("tempDir", RuntimeFunctions::tempDir)
    
    // User info
    register("username", RuntimeFunctions::username)
    
    // Runtime info
    register("uptime", RuntimeFunctions::uptime)
    register("runtimeInfo", RuntimeFunctions::runtimeInfo)
    
    // Helpers
    register("isDebugMode", RuntimeFunctions::isDebugMode)
    register("environment", RuntimeFunctions::environment)
}

// Don't forget to call it in registerAllFunctions():
private fun registerAllFunctions() {
    // ... existing registrations ...
    registerRuntimeFunctions()
}

*/
