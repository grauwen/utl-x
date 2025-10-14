// tools/gradle-plugin/src/main/kotlin/org/apache/utlx/gradle/UTLXSchemaPlugin.kt
package org.apache.utlx.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import java.io.File

/**
 * Gradle plugin for UTL-X schema generation and validation
 * 
 * Usage:
 * ```kotlin
 * plugins {
 *     id("org.apache.utlx.schema") version "0.9.0-beta"
 * }
 * 
 * utlxSchema {
 *     transformations {
 *         register("orderToInvoice") {
 *             inputSchema.set(file("schemas/order.xsd"))
 *             transform.set(file("transforms/order-to-invoice.utlx"))
 *             outputFormat.set("json-schema")
 *             outputFile.set(file("build/schemas/invoice.json"))
 *         }
 *     }
 * }
 * ```
 */
class UTLXSchemaPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Create extension
        val extension = project.extensions.create<UTLXSchemaExtension>("utlxSchema")
        
        // Register transformation container
        val transformations = project.container(TransformationSpec::class.java) { name ->
            TransformationSpec(name, project)
        }
        extension.transformations.set(transformations)
        
        // Create tasks after evaluation
        project.afterEvaluate {
            extension.transformations.get().all { spec ->
                createTasksForTransformation(project, spec)
            }
            
            // Create aggregate tasks
            createAggregateGenerateTask(project, extension)
            createAggregateValidateTask(project, extension)
        }
    }
    
    /**
     * Create tasks for a single transformation
     */
    private fun createTasksForTransformation(project: Project, spec: TransformationSpec) {
        val taskName = spec.name
        
        // Generate schema task
        val generateTask = project.tasks.register<GenerateSchemaTask>(
            "generateSchema${taskName.capitalize()}"
        ) {
            group = "utlx schema"
            description = "Generate output schema for ${spec.name} transformation"
            
            inputSchema.set(spec.inputSchema)
            transformFile.set(spec.transform)
            outputFormat.set(spec.outputFormat)
            outputFile.set(spec.outputFile)
            inputFormat.set(spec.inputFormat)
            pretty.set(spec.pretty)
            includeComments.set(spec.includeComments)
        }
        
        // Validate transformation task
        val validateTask = project.tasks.register<ValidateTransformTask>(
            "validateTransform${taskName.capitalize()}"
        ) {
            group = "utlx schema"
            description = "Validate ${spec.name} transformation against schemas"
            
            inputSchema.set(spec.inputSchema)
            transformFile.set(spec.transform)
            expectedOutputSchema.set(spec.expectedOutputSchema)
            strictMode.set(spec.strictMode)
            failOnWarnings.set(spec.failOnWarnings)
        }
        
        // Add dependency if expected output schema is generated
        if (spec.expectedOutputSchema.isPresent && 
            spec.expectedOutputSchema.get().asFile == spec.outputFile.get().asFile) {
            validateTask.configure {
                dependsOn(generateTask)
            }
        }
    }
    
    /**
     * Create aggregate task that generates all schemas
     */
    private fun createAggregateGenerateTask(project: Project, extension: UTLXSchemaExtension) {
        project.tasks.register("generateAllSchemas") {
            group = "utlx schema"
            description = "Generate all UTL-X schemas"
            
            dependsOn(project.tasks.withType<GenerateSchemaTask>())
        }
    }
    
    /**
     * Create aggregate task that validates all transformations
     */
    private fun createAggregateValidateTask(project: Project, extension: UTLXSchemaExtension) {
        project.tasks.register("validateAllTransforms") {
            group = "utlx schema"
            description = "Validate all UTL-X transformations"
            
            dependsOn(project.tasks.withType<ValidateTransformTask>())
        }
    }
}

/**
 * Extension for configuring UTL-X schema plugin
 */
open class UTLXSchemaExtension {
    val transformations = mutableListOf<TransformationSpec>()
}

/**
 * Specification for a single transformation
 */
open class TransformationSpec(
    val name: String,
    private val project: Project
) {
    val inputSchema = project.objects.fileProperty()
    val transform = project.objects.fileProperty()
    val outputFormat = project.objects.property<String>().convention("json-schema")
    val outputFile = project.objects.fileProperty()
    val inputFormat = project.objects.property<String>()
    val expectedOutputSchema = project.objects.fileProperty()
    
    // Generation options
    val pretty = project.objects.property<Boolean>().convention(true)
    val includeComments = project.objects.property<Boolean>().convention(true)
    
    // Validation options
    val strictMode = project.objects.property<Boolean>().convention(true)
    val failOnWarnings = project.objects.property<Boolean>().convention(false)
}

/**
 * Task to generate output schema from transformation
 */
@CacheableTask
abstract class GenerateSchemaTask : DefaultTask() {
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSchema: RegularFileProperty
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract val transformFile: RegularFileProperty
    
    @Input
    abstract val outputFormat: Property<String>
    
    @OutputFile
    abstract val outputFile: RegularFileProperty
    
    @Input
    @Optional
    abstract val inputFormat: Property<String>
    
    @Input
    @Optional
    abstract val pretty: Property<Boolean>
    
    @Input
    @Optional
    abstract val includeComments: Property<Boolean>
    
    @TaskAction
    fun generate() {
        logger.lifecycle("Generating schema for ${transformFile.get().asFile.name}")
        
        try {
            // Parse input schema
            val inputSchemaContent = inputSchema.get().asFile.readText()
            val detectedInputFormat = inputFormat.getOrElse(
                detectFormat(inputSchema.get().asFile)
            )
            
            val inputType = parseInputSchema(inputSchemaContent, detectedInputFormat)
            
            // Parse transformation
            val transformContent = transformFile.get().asFile.readText()
            val program = parseTransformation(transformContent)
            
            // Infer output type
            val outputType = inferOutputType(program, inputType)
            
            // Generate output schema
            val schema = generateSchema(
                outputType,
                outputFormat.get(),
                pretty.getOrElse(true),
                includeComments.getOrElse(true)
            )
            
            // Write output
            outputFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(schema)
            }
            
            logger.lifecycle("✓ Schema generated: ${outputFile.get().asFile.absolutePath}")
            
        } catch (e: Exception) {
            logger.error("Failed to generate schema: ${e.message}", e)
            throw TaskExecutionException(this, e)
        }
    }
    
    private fun detectFormat(file: File): String {
        return when (file.extension.lowercase()) {
            "xsd" -> "xsd"
            "json" -> "json-schema"
            else -> throw IllegalArgumentException("Cannot detect format for: ${file.name}")
        }
    }
    
    private fun parseInputSchema(content: String, format: String): org.apache.utlx.analysis.types.TypeDefinition {
        return when (format.lowercase()) {
            "xsd" -> {
                val parser = org.apache.utlx.analysis.schema.XSDSchemaParser()
                parser.parse(content, org.apache.utlx.analysis.schema.SchemaFormat.XSD)
            }
            "json-schema" -> {
                val parser = org.apache.utlx.analysis.schema.JSONSchemaParser()
                parser.parse(content, org.apache.utlx.analysis.schema.SchemaFormat.JSON_SCHEMA)
            }
            else -> throw IllegalArgumentException("Unsupported input format: $format")
        }
    }
    
    private fun parseTransformation(content: String): org.apache.utlx.core.ast.Program {
        val parser = org.apache.utlx.core.parser.Parser()
        return parser.parse(content)
    }
    
    private fun inferOutputType(
        program: org.apache.utlx.core.ast.Program,
        inputType: org.apache.utlx.analysis.types.TypeDefinition
    ): org.apache.utlx.analysis.types.TypeDefinition {
        val inference = org.apache.utlx.analysis.types.AdvancedTypeInference(inputType)
        return inference.inferOutputType(program)
    }
    
    private fun generateSchema(
        type: org.apache.utlx.analysis.types.TypeDefinition,
        format: String,
        pretty: Boolean,
        includeComments: Boolean
    ): String {
        return when (format.lowercase()) {
            "json-schema" -> {
                val generator = org.apache.utlx.analysis.schema.JSONSchemaGenerator()
                generator.generate(
                    type,
                    org.apache.utlx.analysis.schema.SchemaFormat.JSON_SCHEMA,
                    org.apache.utlx.analysis.schema.GeneratorOptions(
                        pretty = pretty,
                        includeComments = includeComments
                    )
                )
            }
            else -> throw IllegalArgumentException("Unsupported output format: $format")
        }
    }
}

/**
 * Task to validate transformation against schemas
 */
abstract class ValidateTransformTask : DefaultTask() {
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputSchema: RegularFileProperty
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract val transformFile: RegularFileProperty
    
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    @Optional
    abstract val expectedOutputSchema: RegularFileProperty
    
    @Input
    @Optional
    abstract val strictMode: Property<Boolean>
    
    @Input
    @Optional
    abstract val failOnWarnings: Property<Boolean>
    
    @TaskAction
    fun validate() {
        logger.lifecycle("Validating transformation ${transformFile.get().asFile.name}")
        
        try {
            // Parse schemas and transformation (similar to GenerateSchemaTask)
            val inputSchemaContent = inputSchema.get().asFile.readText()
            val inputType = parseInputSchema(inputSchemaContent)
            
            val transformContent = transformFile.get().asFile.readText()
            val program = parseTransformation(transformContent)
            
            val expectedOutputType = if (expectedOutputSchema.isPresent) {
                val outputSchemaContent = expectedOutputSchema.get().asFile.readText()
                parseOutputSchema(outputSchemaContent)
            } else {
                null
            }
            
            // Validate
            val validator = org.apache.utlx.analysis.schema.TransformValidator()
            val result = validator.validate(program, inputType, expectedOutputType)
            
            // Report results
            if (result.isValid) {
                logger.lifecycle("✓ Validation successful")
                
                if (result.warnings.isNotEmpty()) {
                    logger.warn("Warnings:")
                    result.warnings.forEach { warning ->
                        logger.warn("  ⚠ $warning")
                    }
                    
                    if (failOnWarnings.getOrElse(false)) {
                        throw TaskExecutionException(
                            this,
                            Exception("Validation warnings found (failOnWarnings=true)")
                        )
                    }
                }
            } else {
                logger.error("✗ Validation failed")
                logger.error("Errors:")
                result.errors.forEach { error ->
                    logger.error("  ✗ $error")
                }
                
                if (result.warnings.isNotEmpty()) {
                    logger.warn("Warnings:")
                    result.warnings.forEach { warning ->
                        logger.warn("  ⚠ $warning")
                    }
                }
                
                throw TaskExecutionException(
                    this,
                    Exception("Transformation validation failed")
                )
            }
            
        } catch (e: TaskExecutionException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to validate transformation: ${e.message}", e)
            throw TaskExecutionException(this, e)
        }
    }
    
    private fun parseInputSchema(content: String): org.apache.utlx.analysis.types.TypeDefinition {
        // Detect and parse input schema
        val parser = org.apache.utlx.analysis.schema.XSDSchemaParser()
        return parser.parse(content, org.apache.utlx.analysis.schema.SchemaFormat.XSD)
    }
    
    private fun parseOutputSchema(content: String): org.apache.utlx.analysis.types.TypeDefinition {
        // Detect and parse output schema
        val parser = org.apache.utlx.analysis.schema.JSONSchemaParser()
        return parser.parse(content, org.apache.utlx.analysis.schema.SchemaFormat.JSON_SCHEMA)
    }
    
    private fun parseTransformation(content: String): org.apache.utlx.core.ast.Program {
        val parser = org.apache.utlx.core.parser.Parser()
        return parser.parse(content)
    }
}

/**
 * Extension to support Kotlin DSL configuration
 */
fun Project.utlxSchema(configure: UTLXSchemaExtension.() -> Unit) {
    extensions.configure<UTLXSchemaExtension>(configure)
}
