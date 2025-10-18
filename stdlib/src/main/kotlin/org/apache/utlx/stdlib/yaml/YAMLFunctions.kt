/**
 * YAML Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/yaml/YAMLFunctions.kt
 * 
 * Provides YAML-specific operations for transformation scenarios.
 * Includes support for multi-document handling, path operations, deep merge,
 * dynamic/arbitrary keys, and structural manipulation.
 * 
 * Function Categories:
 * - Multi-Document (split, merge, get)
 * - Path Operations (query, set, delete, exists)
 * - Deep Merge (merge objects)
 * - Dynamic Key Operations (keys, values, mapping, filtering)
 * - Formatting (sort, minimize, indent)
 * - Validation (validate syntax, key patterns)
 */

package org.apache.utlx.stdlib.yaml

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * YAML Functions for data manipulation and analysis
 * 
 * All functions follow UDM compliance pattern for stdlib registration.
 * Provides YAML-specific operations for transformation scenarios.
 */
object YAMLFunctions {

    @UTLXFunction(
        description = "Split multi-document YAML into array of documents",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "yamlSplitDocuments(...) => result",
        notes = "Example:\n```\nyamlSplitDocuments(multiDocYaml)\n// Returns: UDM.Array of separate documents\n```",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Split multi-document YAML into array of documents
     * 
     * @param args List containing: [yaml]
     * @return UDM Array of documents
     * 
     * Example:
     * ```
     * yamlSplitDocuments(multiDocYaml)
     * // Returns: UDM.Array of separate documents
     * ```
     */
    fun yamlSplitDocuments(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlSplitDocuments")
        val yaml = args[0].asString()
        
        return try {
            // Split by document separator (---)
            val docSeparator = Regex("^---\\s*$", RegexOption.MULTILINE)
            val documents = yaml.split(docSeparator)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            UDM.Array(documents.map { doc ->
                parseYAMLString(doc)
            })
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to split YAML documents: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Merge multiple YAML documents into single multi-document string",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "yamlMergeDocuments(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Merge multiple YAML documents into single multi-document string
     * 
     * @param args List containing: [documents]
     * @return UDM Scalar with multi-document YAML string
     */
    fun yamlMergeDocuments(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlMergeDocuments")
        val documents = args[0]
        
        return try {
            val yamlDocs = when (documents) {
                is UDM.Array -> documents.elements.map { doc ->
                    renderYAMLToString(doc)
                }
                else -> throw FunctionArgumentException("Expected array of documents")
            }
            
            UDM.Scalar(yamlDocs.joinToString("\n---\n"))
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to merge YAML documents: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Get specific document from multi-document YAML by index",
        minArgs = 3,
        maxArgs = 3,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "index: Index value"
        ],
        returns = "Result of the operation",
        example = "yamlGetDocument(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Get specific document from multi-document YAML by index
     * 
     * @param args List containing: [yaml, index]
     * @return UDM object for that document
     */
    fun yamlGetDocument(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlGetDocument")
        val yaml = args[0].asString()
        val index = args[1].asInt()
        
        return try {
            val docs = yamlSplitDocuments(listOf(args[0]))
            
            when (docs) {
                is UDM.Array -> {
                    if (index < 0 || index >= docs.elements.size) {
                        throw FunctionArgumentException("Document index $index out of bounds")
                    }
                    docs.elements[index]
                }
                else -> throw FunctionArgumentException("Failed to split YAML documents")
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to get YAML document: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Query YAML using path expression",
        minArgs = 3,
        maxArgs = 3,
        category = "YAML",
        parameters = [
            "yaml: Yaml value",
        "path: Path value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "yamlPath(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Query YAML using path expression
     * 
     * @param args List containing: [yaml, path]
     * @return UDM value at path, or null if not found
     */
    fun yamlPath(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlPath")
        val yaml = args[0]
        val path = args[1].asString()
        
        return try {
            val result = yamlPathInternal(yaml, path)
            result ?: UDM.Scalar(null)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to query YAML path: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Set value at YAML path",
        minArgs = 3,
        maxArgs = 3,
        category = "YAML",
        parameters = [
            "yaml: Yaml value",
        "path: Path value",
        "value: Value value"
        ],
        returns = "Result of the operation",
        example = "yamlSet(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Set value at YAML path
     * 
     * @param args List containing: [yaml, path, value]
     * @return UDM with value set at path
     */
    fun yamlSet(args: List<UDM>): UDM {
        if (args.size != 3) {
            throw FunctionArgumentException("yamlSet expects 3 arguments (yaml, path, value), got ${args.size}")
        }
        
        val yaml = args[0]
        val path = args[1].asString()
        val value = args[2]
        
        return try {
            val cleanPath = if (path.startsWith(".")) path.substring(1) else path
            val segments = parsePathSegments(cleanPath)
            setAtPath(yaml, segments, 0, value)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to set YAML path: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Delete value at YAML path",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "path: Path value"
        ],
        returns = "Result of the operation",
        example = "yamlDelete(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Delete value at YAML path
     * 
     * @param args List containing: [yaml, path]
     * @return UDM with path removed
     */
    fun yamlDelete(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlDelete")
        val yaml = args[0]
        val path = args[1].asString()
        
        return try {
            val cleanPath = if (path.startsWith(".")) path.substring(1) else path
            val segments = parsePathSegments(cleanPath)
            deleteAtPath(yaml, segments, 0)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to delete YAML path: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Check if path exists in YAML",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "path: Path value"
        ],
        returns = "Result of the operation",
        example = "yamlExists(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Check if path exists in YAML
     * 
     * @param args List containing: [yaml, path]
     * @return UDM Scalar Boolean indicating if path exists
     */
    fun yamlExists(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlExists")
        val yaml = args[0]
        val path = args[1].asString()
        
        return try {
            val result = yamlPathInternal(yaml, path)
            UDM.Scalar(result != null)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Get all keys from a YAML object",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "yamlKeys(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Get all keys from a YAML object
     * 
     * @param args List containing: [yaml]
     * @return UDM Array of key strings
     */
    fun yamlKeys(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlKeys")
        val yaml = args[0]
        
        return when (yaml) {
            is UDM.Object -> UDM.Array(
                yaml.properties.keys.map { UDM.Scalar(it) }
            )
            else -> UDM.Array(emptyList())
        }
    }

    @UTLXFunction(
        description = "Get all values from a YAML object",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "yamlValues(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Get all values from a YAML object
     * 
     * @param args List containing: [yaml]
     * @return UDM Array of values
     */
    fun yamlValues(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlValues")
        val yaml = args[0]
        
        return when (yaml) {
            is UDM.Object -> UDM.Array(yaml.properties.values.toList())
            else -> UDM.Array(emptyList())
        }
    }

    @UTLXFunction(
        description = "Get entries (key-value pairs) from a YAML object",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "yamlEntries(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Get entries (key-value pairs) from a YAML object
     * 
     * @param args List containing: [yaml]
     * @return UDM Array of objects with "key" and "value" properties
     */
    fun yamlEntries(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlEntries")
        val yaml = args[0]
        
        return when (yaml) {
            is UDM.Object -> {
                val entries = yaml.properties.map { (key, value) ->
                    UDM.Object(mapOf(
                        "key" to UDM.Scalar(key),
                        "value" to value
                    ))
                }
                UDM.Array(entries)
            }
            else -> UDM.Array(emptyList())
        }
    }

    @UTLXFunction(
        description = "Filter YAML object by key pattern",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "pattern: Pattern value"
        ],
        returns = "New array with filtered elements",
        example = "yamlFilterByKeyPattern(...) => result",
        tags = ["filter", "yaml"],
        since = "1.0"
    )
    /**
     * Filter YAML object by key pattern
     * 
     * @param args List containing: [yaml, pattern]
     * @return UDM Object with filtered properties
     */
    fun yamlFilterByKeyPattern(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlFilterByKeyPattern")
        val yaml = args[0]
        val pattern = args[1].asString()
        
        return try {
            val regex = Regex(pattern)
            when (yaml) {
                is UDM.Object -> {
                    val filtered = yaml.properties.filter { (key, _) ->
                        regex.matches(key)
                    }
                    UDM.Object(filtered)
                }
                else -> yaml
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to filter by key pattern: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Select specific keys from YAML object",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "keys: Keys value"
        ],
        returns = "Result of the operation",
        example = "yamlSelectKeys(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Select specific keys from YAML object
     * 
     * @param args List containing: [yaml, keys]
     * @return UDM Object with only selected keys
     */
    fun yamlSelectKeys(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlSelectKeys")
        val yaml = args[0]
        val keys = args[1].asStringArray()
        
        return when (yaml) {
            is UDM.Object -> {
                val selected = yaml.properties.filter { (key, _) ->
                    key in keys
                }
                UDM.Object(selected)
            }
            else -> yaml
        }
    }

    @UTLXFunction(
        description = "Omit specific keys from YAML object",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "keys: Keys value"
        ],
        returns = "Result of the operation",
        example = "yamlOmitKeys(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Omit specific keys from YAML object
     * 
     * @param args List containing: [yaml, keys]
     * @return UDM Object without specified keys
     */
    fun yamlOmitKeys(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlOmitKeys")
        val yaml = args[0]
        val keys = args[1].asStringArray()
        
        return when (yaml) {
            is UDM.Object -> {
                val filtered = yaml.properties.filter { (key, _) ->
                    key !in keys
                }
                UDM.Object(filtered)
            }
            else -> yaml
        }
    }

    @UTLXFunction(
        description = "Create YAML object from entries array",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "overlay: Overlay value"
        ],
        returns = "Result of the operation",
        example = "yamlFromEntries(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Create YAML object from entries array
     * 
     * @param args List containing: [entries]
     * @return UDM Object created from entries
     */
    fun yamlFromEntries(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlFromEntries")
        val entries = args[0]
        
        return try {
            when (entries) {
                is UDM.Array -> {
                    val pairs = entries.elements.mapNotNull { entry ->
                        if (entry is UDM.Object) {
                            val key = entry.properties["key"]
                            val value = entry.properties["value"]
                            if (key is UDM.Scalar && value != null) {
                                (key.value?.toString() ?: "") to value
                            } else null
                        } else null
                    }
                    UDM.Object(pairs.toMap())
                }
                else -> throw FunctionArgumentException("Expected array of entries")
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to create object from entries: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Deep merge two YAML objects",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "overlay: Overlay value"
        ],
        returns = "Result of the operation",
        example = "yamlMerge(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Deep merge two YAML objects
     * 
     * @param args List containing: [base, overlay]
     * @return UDM with merged content
     */
    fun yamlMerge(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlMerge")
        val base = args[0]
        val overlay = args[1]
        
        return yamlMergeInternal(base, overlay)
    }

    @UTLXFunction(
        description = "Merge multiple YAML documents in order",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "fieldName: Fieldname value"
        ],
        returns = "Result of the operation",
        example = "yamlMergeAll(...) => result",
        tags = ["predicate", "yaml"],
        since = "1.0"
    )
    /**
     * Merge multiple YAML documents in order
     * 
     * @param args List containing: [yamls]
     * @return UDM with merged content
     */
    fun yamlMergeAll(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlMergeAll")
        val yamls = args[0]
        
        return try {
            when (yamls) {
                is UDM.Array -> {
                    if (yamls.elements.isEmpty()) {
                        UDM.Object(emptyMap())
                    } else {
                        yamls.elements.reduce { acc, yaml ->
                            yamlMergeInternal(acc, yaml)
                        }
                    }
                }
                else -> throw FunctionArgumentException("Expected array of YAML documents")
            }
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to merge YAML documents: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Find all values in YAML structure by field name",
        minArgs = 2,
        maxArgs = 2,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "fieldName: Fieldname value"
        ],
        returns = "First matching element, or null if none found",
        example = "yamlFindByField(...) => result",
        tags = ["search", "yaml"],
        since = "1.0"
    )
    /**
     * Find all values in YAML structure by field name
     * 
     * @param args List containing: [yaml, fieldName]
     * @return UDM Array of all values with that field name
     */
    fun yamlFindByField(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlFindByField")
        val yaml = args[0]
        val fieldName = args[1].asString()
        
        return try {
            val results = mutableListOf<UDM>()
            findByFieldRecursive(yaml, fieldName, results)
            UDM.Array(results)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to find by field: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Find all objects containing specific field",
        minArgs = 2,
        maxArgs = 2,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "fieldName: Fieldname value"
        ],
        returns = "First matching element, or null if none found",
        example = "yamlFindObjectsWithField(...) => result",
        tags = ["search", "yaml"],
        since = "1.0"
    )
    /**
     * Find all objects containing specific field
     * 
     * @param args List containing: [yaml, fieldName]
     * @return UDM Array of objects containing that field
     */
    fun yamlFindObjectsWithField(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlFindObjectsWithField")
        val yaml = args[0]
        val fieldName = args[1].asString()
        
        return try {
            val results = mutableListOf<UDM>()
            findObjectsWithFieldRecursive(yaml, fieldName, results)
            UDM.Array(results)
        } catch (e: Exception) {
            throw FunctionArgumentException("Failed to find objects with field: ${e.message}")
        }
    }

    @UTLXFunction(
        description = "Sort YAML object keys alphabetically",
        minArgs = 2,
        maxArgs = 2,
        category = "YAML",
        parameters = [
            "yaml: Yaml value",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "yamlSort(...) => result",
        tags = ["sort", "yaml"],
        since = "1.0"
    )
    /**
     * Sort YAML object keys alphabetically
     * 
     * @param args List containing: [yaml, recursive?]
     * @return UDM with sorted keys
     */
    fun yamlSort(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException("yamlSort expects 1 or 2 arguments (yaml, recursive?), got ${args.size}")
        }
        
        val yaml = args[0]
        val recursive = if (args.size > 1) args[1].asBoolean() else true
        
        return yamlSortInternal(yaml, recursive)
    }

    @UTLXFunction(
        description = "Validate YAML syntax",
        minArgs = 2,
        maxArgs = 2,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "yamlValidate(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Validate YAML syntax
     * 
     * @param args List containing: [yaml]
     * @return UDM Scalar Boolean indicating if valid
     */
    fun yamlValidate(args: List<UDM>): UDM {
        requireArgs(args, 1, "yamlValidate")
        val yaml = args[0].asString()
        
        return try {
            parseYAMLString(yaml)
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Validate all keys match a pattern",
        minArgs = 2,
        maxArgs = 2,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "pattern: Pattern value"
        ],
        returns = "Result of the operation",
        example = "yamlValidateKeyPattern(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Validate all keys match a pattern
     * 
     * @param args List containing: [yaml, pattern, recursive?]
     * @return UDM Scalar Boolean indicating if all keys match
     */
    fun yamlValidateKeyPattern(args: List<UDM>): UDM {
        if (args.size < 2 || args.size > 3) {
            throw FunctionArgumentException("yamlValidateKeyPattern expects 2 or 3 arguments (yaml, pattern, recursive?), got ${args.size}")
        }
        
        val yaml = args[0]
        val pattern = args[1].asString()
        val recursive = if (args.size > 2) args[2].asBoolean() else true
        
        return try {
            val regex = Regex(pattern)
            val result = validateKeyPatternRecursive(yaml, regex, recursive)
            UDM.Scalar(result)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Check if YAML has required fields",
        minArgs = 1,
        maxArgs = 1,
        category = "YAML",
        parameters = [
            "array: Input array to process",
        "requiredFields: Requiredfields value"
        ],
        returns = "Result of the operation",
        example = "yamlHasRequiredFields(...) => result",
        tags = ["yaml"],
        since = "1.0"
    )
    /**
     * Check if YAML has required fields
     * 
     * @param args List containing: [yaml, requiredFields]
     * @return UDM Scalar Boolean indicating if all required fields exist
     */
    fun yamlHasRequiredFields(args: List<UDM>): UDM {
        requireArgs(args, 2, "yamlHasRequiredFields")
        val yaml = args[0]
        val requiredFields = args[1].asStringArray()
        
        return try {
            val result = requiredFields.all { field ->
                yamlPathInternal(yaml, field) != null
            }
            UDM.Scalar(result)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    // Helper functions
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException("$functionName expects $expected argument(s), got ${args.size}")
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString() ?: throw FunctionArgumentException("Expected string value")
        else -> throw FunctionArgumentException("Expected string value, got ${this::class.simpleName}")
    }
    
    private fun UDM.asInt(): Int = when (this) {
        is UDM.Scalar -> (value as? Number)?.toInt() ?: throw FunctionArgumentException("Expected integer value")
        else -> throw FunctionArgumentException("Expected integer value, got ${this::class.simpleName}")
    }
    
    private fun UDM.asBoolean(): Boolean = when (this) {
        is UDM.Scalar -> value as? Boolean ?: throw FunctionArgumentException("Expected boolean value")
        else -> throw FunctionArgumentException("Expected boolean value, got ${this::class.simpleName}")
    }
    
    private fun UDM.asStringArray(): List<String> = when (this) {
        is UDM.Array -> elements.map { 
            when (it) {
                is UDM.Scalar -> it.value?.toString() ?: ""
                else -> throw FunctionArgumentException("Expected array of strings")
            }
        }
        else -> throw FunctionArgumentException("Expected array value, got ${this::class.simpleName}")
    }

    // Path parsing internals
    private sealed class PathSegment {
        data class Property(val name: String) : PathSegment()
        data class ArrayIndex(val index: Int) : PathSegment()
    }

    private fun parsePathSegments(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        var current = ""
        var i = 0
        
        while (i < path.length) {
            when (path[i]) {
                '.' -> {
                    if (current.isNotEmpty()) {
                        segments.add(PathSegment.Property(current))
                        current = ""
                    }
                }
                '[' -> {
                    if (current.isNotEmpty()) {
                        segments.add(PathSegment.Property(current))
                        current = ""
                    }
                    // Find closing ]
                    val closeIdx = path.indexOf(']', i)
                    if (closeIdx == -1) {
                        throw FunctionArgumentException("Unclosed array index at position $i")
                    }
                    val indexStr = path.substring(i + 1, closeIdx)
                    val index = indexStr.toIntOrNull()
                        ?: throw FunctionArgumentException("Invalid array index: $indexStr")
                    segments.add(PathSegment.ArrayIndex(index))
                    i = closeIdx
                }
                else -> {
                    current += path[i]
                }
            }
            i++
        }
        
        if (current.isNotEmpty()) {
            segments.add(PathSegment.Property(current))
        }
        
        return segments
    }

    private fun yamlPathInternal(yaml: UDM, path: String): UDM? {
        if (path.isEmpty() || path == ".") return yaml
        
        val cleanPath = if (path.startsWith(".")) path.substring(1) else path
        val segments = parsePathSegments(cleanPath)
        
        var current: UDM? = yaml
        for (segment in segments) {
            current = when (segment) {
                is PathSegment.Property -> {
                    when (current) {
                        is UDM.Object -> current.properties[segment.name]
                        else -> null
                    }
                }
                is PathSegment.ArrayIndex -> {
                    when (current) {
                        is UDM.Array -> current.elements.getOrNull(segment.index)
                        else -> null
                    }
                }
            }
            
            if (current == null) return null
        }
        
        return current
    }

    private fun setAtPath(udm: UDM, segments: List<PathSegment>, index: Int, value: UDM): UDM {
        if (index >= segments.size) {
            return value
        }
        
        val segment = segments[index]
        
        return when (segment) {
            is PathSegment.Property -> {
                when (udm) {
                    is UDM.Object -> {
                        val currentValue = udm.properties[segment.name] ?: UDM.Object(emptyMap())
                        val newValue = setAtPath(currentValue, segments, index + 1, value)
                        UDM.Object(udm.properties + (segment.name to newValue))
                    }
                    else -> throw FunctionArgumentException("Cannot set property on non-object")
                }
            }
            is PathSegment.ArrayIndex -> {
                when (udm) {
                    is UDM.Array -> {
                        val elements = udm.elements.toMutableList()
                        if (segment.index >= elements.size) {
                            throw FunctionArgumentException("Array index ${segment.index} out of bounds")
                        }
                        elements[segment.index] = setAtPath(elements[segment.index], segments, index + 1, value)
                        UDM.Array(elements)
                    }
                    else -> throw FunctionArgumentException("Cannot index non-array")
                }
            }
        }
    }

    private fun deleteAtPath(udm: UDM, segments: List<PathSegment>, index: Int): UDM {
        if (index >= segments.size) {
            return UDM.Scalar(null)
        }
        
        if (index == segments.size - 1) {
            val segment = segments[index]
            return when (segment) {
                is PathSegment.Property -> {
                    when (udm) {
                        is UDM.Object -> UDM.Object(udm.properties - segment.name)
                        else -> udm
                    }
                }
                is PathSegment.ArrayIndex -> {
                    when (udm) {
                        is UDM.Array -> {
                            val elements = udm.elements.toMutableList()
                            if (segment.index < elements.size) {
                                elements.removeAt(segment.index)
                            }
                            UDM.Array(elements)
                        }
                        else -> udm
                    }
                }
            }
        }
        
        val segment = segments[index]
        return when (segment) {
            is PathSegment.Property -> {
                when (udm) {
                    is UDM.Object -> {
                        val currentValue = udm.properties[segment.name] ?: return udm
                        val newValue = deleteAtPath(currentValue, segments, index + 1)
                        UDM.Object(udm.properties + (segment.name to newValue))
                    }
                    else -> udm
                }
            }
            is PathSegment.ArrayIndex -> {
                when (udm) {
                    is UDM.Array -> {
                        val elements = udm.elements.toMutableList()
                        if (segment.index < elements.size) {
                            elements[segment.index] = deleteAtPath(elements[segment.index], segments, index + 1)
                        }
                        UDM.Array(elements)
                    }
                    else -> udm
                }
            }
        }
    }

    private fun yamlMergeInternal(base: UDM, overlay: UDM): UDM {
        return when {
            overlay is UDM.Scalar && overlay.value == null -> base
            base is UDM.Scalar && base.value == null -> overlay
            base is UDM.Object && overlay is UDM.Object -> {
                val merged = mutableMapOf<String, UDM>()
                merged.putAll(base.properties)
                
                overlay.properties.forEach { (key, value) ->
                    merged[key] = if (key in base.properties) {
                        yamlMergeInternal(base.properties[key]!!, value)
                    } else {
                        value
                    }
                }
                
                UDM.Object(merged)
            }
            else -> overlay
        }
    }

    private fun findByFieldRecursive(udm: UDM, fieldName: String, results: MutableList<UDM>) {
        when (udm) {
            is UDM.Object -> {
                udm.properties[fieldName]?.let { results.add(it) }
                udm.properties.values.forEach { findByFieldRecursive(it, fieldName, results) }
            }
            is UDM.Array -> {
                udm.elements.forEach { findByFieldRecursive(it, fieldName, results) }
            }
            else -> { /* scalar values - skip */ }
        }
    }

    private fun findObjectsWithFieldRecursive(udm: UDM, fieldName: String, results: MutableList<UDM>) {
        when (udm) {
            is UDM.Object -> {
                if (fieldName in udm.properties) {
                    results.add(udm)
                }
                udm.properties.values.forEach { findObjectsWithFieldRecursive(it, fieldName, results) }
            }
            is UDM.Array -> {
                udm.elements.forEach { findObjectsWithFieldRecursive(it, fieldName, results) }
            }
            else -> { /* scalar values - skip */ }
        }
    }

    private fun yamlSortInternal(yaml: UDM, recursive: Boolean): UDM {
        return when (yaml) {
            is UDM.Object -> {
                val sortedProps = yaml.properties.toSortedMap()
                val finalProps = if (recursive) {
                    sortedProps.mapValues { (_, value) ->
                        yamlSortInternal(value, recursive)
                    }
                } else {
                    sortedProps
                }
                UDM.Object(finalProps)
            }
            is UDM.Array -> {
                if (recursive) {
                    UDM.Array(yaml.elements.map { yamlSortInternal(it, recursive) })
                } else {
                    yaml
                }
            }
            else -> yaml
        }
    }

    private fun validateKeyPatternRecursive(udm: UDM, regex: Regex, recursive: Boolean): Boolean {
        return when (udm) {
            is UDM.Object -> {
                val keysValid = udm.properties.keys.all { regex.matches(it) }
                if (!keysValid) return false
                
                if (recursive) {
                    udm.properties.values.all { validateKeyPatternRecursive(it, regex, recursive) }
                } else {
                    true
                }
            }
            is UDM.Array -> {
                if (recursive) {
                    udm.elements.all { validateKeyPatternRecursive(it, regex, recursive) }
                } else {
                    true
                }
            }
            else -> true
        }
    }

    // Simple YAML parsing and rendering (basic implementation)
    private fun parseYAMLString(yaml: String): UDM {
        // This is a simplified YAML parser for basic functionality
        // In production, you would use a proper YAML library
        val lines = yaml.trim().split("\n")
        if (lines.isEmpty()) return UDM.Object(emptyMap())
        
        val result = mutableMapOf<String, Any>()
        var currentKey: String? = null
        var currentIndent = 0
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            
            val indent = line.takeWhile { it == ' ' }.length
            
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":", limit = 2)
                val key = parts[0].trim()
                val value = if (parts.size > 1) parts[1].trim() else ""
                
                if (value.isNotEmpty()) {
                    result[key] = parseYAMLValue(value)
                } else {
                    currentKey = key
                    currentIndent = indent
                }
            }
        }
        
        return UDM.Object(result.mapValues { (_, v) -> 
            when (v) {
                is String -> UDM.Scalar(v)
                is Number -> UDM.Scalar(v)
                is Boolean -> UDM.Scalar(v)
                else -> UDM.Scalar(v.toString())
            }
        })
    }

    private fun parseYAMLValue(value: String): Any {
        return when {
            value == "true" -> true
            value == "false" -> false
            value == "null" || value == "~" -> ""
            value.toDoubleOrNull() != null -> value.toDouble()
            value.startsWith("\"") && value.endsWith("\"") -> value.substring(1, value.length - 1)
            value.startsWith("'") && value.endsWith("'") -> value.substring(1, value.length - 1)
            else -> value
        }
    }

    private fun renderYAMLToString(udm: UDM, indent: Int = 0): String {
        val indentStr = " ".repeat(indent)
        
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    null -> "null"
                    is Boolean -> value.toString()
                    is Number -> value.toString()
                    is String -> if (value.contains('\n') || value.contains(':')) "\"$value\"" else value
                    else -> value.toString()
                }
            }
            is UDM.Array -> {
                if (udm.elements.isEmpty()) "[]"
                else udm.elements.joinToString("\n") { element ->
                    "${indentStr}- ${renderYAMLToString(element, indent + 2)}"
                }
            }
            is UDM.Object -> {
                if (udm.properties.isEmpty()) "{}"
                else udm.properties.entries.joinToString("\n") { (key, value) ->
                    "$indentStr$key: ${renderYAMLToString(value, indent + 2)}"
                }
            }
            else -> ""
        }
    }
}