package org.apache.utlx.engine.bundle

import org.apache.utlx.engine.config.TransformConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

data class DiscoveredTransformation(
    val name: String,
    val source: String,
    val config: TransformConfig
)

class BundleLoader {

    private val logger = LoggerFactory.getLogger(BundleLoader::class.java)

    fun load(bundlePath: Path): List<DiscoveredTransformation> {
        require(bundlePath.exists()) { "Bundle path does not exist: $bundlePath" }
        require(bundlePath.isDirectory()) { "Bundle path is not a directory: $bundlePath" }

        val transformationsDir = bundlePath.resolve("transformations")
        if (!transformationsDir.exists() || !transformationsDir.isDirectory()) {
            logger.warn("No 'transformations/' directory found in bundle: {}", bundlePath)
            return emptyList()
        }

        val discovered = mutableListOf<DiscoveredTransformation>()

        Files.list(transformationsDir).use { stream ->
            stream.filter { it.isDirectory() }
                .sorted()
                .forEach { txDir ->
                    try {
                        val tx = loadTransformation(txDir)
                        if (tx != null) {
                            discovered.add(tx)
                            logger.info("Discovered transformation: '{}'", tx.name)
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to load transformation from {}: {}", txDir, e.message)
                    }
                }
        }

        logger.info("Bundle scan complete: {} transformation(s) discovered", discovered.size)
        return discovered
    }

    private fun loadTransformation(txDir: Path): DiscoveredTransformation? {
        val name = txDir.name
        val configPath = txDir.resolve("transform.yaml")

        if (!configPath.exists()) {
            logger.warn("No transform.yaml in {}, skipping", txDir)
            return null
        }

        val config = TransformConfig.load(configPath)

        // Find the .utlx source file
        val sourceFile = findUtlxSource(txDir, name)
        if (sourceFile == null) {
            logger.warn("No .utlx source file found in {}, skipping", txDir)
            return null
        }

        val source = sourceFile.readText(Charsets.UTF_8)

        return DiscoveredTransformation(
            name = name,
            source = source,
            config = config
        )
    }

    private fun findUtlxSource(txDir: Path, name: String): Path? {
        // Try name-matching file first, then any .utlx file
        val namedFile = txDir.resolve("$name.utlx")
        if (namedFile.exists()) return namedFile

        return Files.list(txDir).use { stream ->
            stream.filter { it.name.endsWith(".utlx") }
                .findFirst()
                .orElse(null)
        }
    }
}
