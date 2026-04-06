package org.apache.utlx.engine.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

data class TransformConfig(
    val strategy: String = "TEMPLATE",
    val validationPolicy: String = "SKIP",
    val inputs: List<InputSlot> = emptyList(),
    val output: OutputSlot = OutputSlot(),
    val maxConcurrent: Int = 1
) {
    companion object {
        private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
            registerModule(kotlinModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        fun load(path: Path): TransformConfig {
            return yamlMapper.readValue(path.toFile())
        }
    }
}

data class InputSlot(
    val name: String = "",
    val schema: String? = null
)

data class OutputSlot(
    val schema: String? = null
)
