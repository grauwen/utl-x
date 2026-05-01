// stdlib/src/main/kotlin/org/apache/utlx/stdlib/restructuring/DataRestructuringFunctions.kt
package org.apache.utlx.stdlib.restructuring

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Data Restructuring Functions
 *
 * Functions that change the SHAPE of data — from flat to grouped, from array to keyed map,
 * from references to enriched records.
 *
 * Functions:
 * - groupBy: Array → Object (keyed map for O(1) lookup)
 * - mapGroups: Array → Array (group + transform each group for reporting)
 * - lookupBy: Array → Object or null (find one matching record by key)
 *
 * Future (F03-F06):
 * - nestBy: nest children under parents by key (1:N)
 * - chunkBy: group sequential records by position (no keys)
 * - unnest: expand nested children alongside parents (reverse of nestBy)
 */
object DataRestructuringFunctions {

    /**
     * Helper: extract a grouping key from an element using either a string property name
     * or a lambda key selector.
     */
    private fun extractKey(element: UDM, keySelector: UDM): String {
        return when {
            keySelector is UDM.Scalar && keySelector.value is String -> {
                val propertyName = keySelector.value as String
                when (element) {
                    is UDM.Object -> element.properties[propertyName]?.asString() ?: "null"
                    else -> "null"
                }
            }
            keySelector is UDM.Lambda -> {
                val result = keySelector.apply(listOf(element))
                result.asString()
            }
            else -> "null"
        }
    }

    /**
     * Helper: group array elements into a map by key.
     * Shared by groupBy and mapGroups.
     */
    private fun buildGroups(array: UDM.Array, keySelector: UDM): Map<String, MutableList<UDM>> {
        val groups = mutableMapOf<String, MutableList<UDM>>()
        array.elements.forEach { element ->
            val key = extractKey(element, keySelector)
            groups.getOrPut(key) { mutableListOf() }.add(element)
        }
        return groups
    }

    @UTLXFunction(
        description = "Group array elements by key. Returns an Object for O(1) lookup by key.",
        minArgs = 2,
        maxArgs = 2,
        category = "Data Restructuring",
        parameters = [
            "array: Input array to group",
            "keyFunction: Lambda (item) -> key, or string property name"
        ],
        returns = "Object keyed by group name, values are arrays of matching elements",
        example = "groupBy(items, (i) -> i.dept) => {Eng: [...], Sales: [...]}",
        notes = "Returns an Object where keys are group names and values are arrays of group members.\n" +
                "Use groupBy for O(1) lookup: groups[\"Engineering\"].\n" +
                "Use mapGroups for iteration/reporting.\n" +
                "Use lookupBy for 1:1 enrichment (find one record).\n" +
                "Key function can be a lambda or a string property name shorthand.",
        tags = ["restructuring", "grouping", "lookup"],
        since = "1.0"
    )
    fun groupBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException(
                "groupBy() requires 2 arguments: array, keySelector. " +
                "Example: groupBy(\$input.lines, (l) -> l.orderId)"
            )
        }

        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("groupBy() first argument must be an array")
        }

        val groups = buildGroups(array, args[1])

        return UDM.Object(groups.mapValues { (_, elements) -> UDM.Array(elements) as UDM })
    }

    @UTLXFunction(
        description = "Group array elements by key and transform each group. Returns array of transformed results.",
        minArgs = 3,
        maxArgs = 3,
        category = "Data Restructuring",
        parameters = [
            "array: Input array to group",
            "keySelector: Key function (lambda) or property name (string)",
            "transform: Lambda receiving group object with .key and .value properties"
        ],
        returns = "Array of transformed group results",
        example = "mapGroups(employees, \"department\", group => {dept: group.key, count: count(group.value)})",
        notes = "Groups array elements by key, then applies transform to each group.\n" +
                "The transform lambda receives {key: groupKey, value: [groupMembers]}.\n" +
                "Use mapGroups for iteration/reporting.\n" +
                "Use groupBy for O(1) lookup.",
        tags = ["restructuring", "grouping", "reporting"],
        since = "1.0"
    )
    fun mapGroups(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException(
                "mapGroups() requires 3 arguments: array, keySelector, transform. " +
                "Example: mapGroups(items, \"category\", group => {name: group.key, count: count(group.value)})"
            )
        }

        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("mapGroups() first argument must be an array")
        }

        val transform = args[2]
        if (transform !is UDM.Lambda) {
            throw IllegalArgumentException("mapGroups() third argument must be a lambda/function")
        }

        val groups = buildGroups(array, args[1])

        val results = groups.map { (key, elements) ->
            val groupObject = UDM.Object(mapOf(
                "key" to UDM.Scalar(key),
                "value" to UDM.Array(elements)
            ))
            transform.apply(listOf(groupObject))
        }

        return UDM.Array(results)
    }

    @UTLXFunction(
        description = "Find one matching record from a reference array by key. Returns the first match or null.",
        minArgs = 3,
        maxArgs = 3,
        category = "Data Restructuring",
        parameters = [
            "searchValue: The value to search for (e.g., a customer ID)",
            "referenceArray: The array to search in (e.g., all customers)",
            "keyFunction: Lambda that extracts the comparison key from each reference record"
        ],
        returns = "The first matching record, or null if no match found",
        example = "lookupBy(order.customerId, customers, (c) -> c.id) => {id: \"C-42\", name: \"Acme Corp\"}",
        notes = "1:1 record enrichment — find ONE matching record by key.\n" +
                "Use lookupBy for enrichment (add customer name to order).\n" +
                "Use groupBy for 1:N grouping (all lines per order).\n" +
                "Use nestBy (F03, planned) for nesting children under parents.",
        tags = ["restructuring", "lookup", "enrichment"],
        since = "1.0"
    )
    fun lookupBy(args: List<UDM>): UDM {
        if (args.size < 3) {
            throw IllegalArgumentException(
                "lookupBy() requires 3 arguments: searchValue, referenceArray, keyFunction. " +
                "Example: lookupBy(order.customerId, \$input.customers, (c) -> c.id)"
            )
        }

        val searchValue = args[0]
        val referenceArray = args[1]
        val keyFunction = args[2]

        if (referenceArray !is UDM.Array) {
            throw IllegalArgumentException(
                "lookupBy() second argument must be an array (the reference table to search in)"
            )
        }

        if (keyFunction !is UDM.Lambda) {
            throw IllegalArgumentException(
                "lookupBy() third argument must be a lambda/function (the key extractor)"
            )
        }

        val searchKey = searchValue.asString()

        for (element in referenceArray.elements) {
            val elementKey = keyFunction.apply(listOf(element)).asString()
            if (elementKey == searchKey) {
                return element
            }
        }

        return UDM.Scalar(null)
    }

    @UTLXFunction(
        description = "Nest children under parents by matching keys. Creates a 1:N parent-child hierarchy.",
        minArgs = 5,
        maxArgs = 5,
        category = "Data Restructuring",
        parameters = [
            "parents: Array of parent records (e.g., orders)",
            "children: Array of child records (e.g., order lines)",
            "parentKeyFn: Lambda extracting the join key from each parent",
            "childKeyFn: Lambda extracting the join key from each child",
            "childPropertyName: String name for the new property added to each parent"
        ],
        returns = "Array of parent objects, each with a new property containing its matched children",
        example = "nestBy(orders, lines, (o) -> o.orderId, (l) -> l.orderId, \"lines\")",
        notes = "Flat-to-hierarchical restructuring — the most common integration pattern.\n" +
                "Performance: O(N + M) — builds a hash index of children, then iterates parents.\n" +
                "Parents with no matching children get an empty array.\n" +
                "The 5th parameter is a string that becomes the property name on each parent.\n" +
                "Use nestBy for 1:N nesting. Use lookupBy for 1:1 enrichment. Use groupBy for keyed map.",
        tags = ["restructuring", "nesting", "join", "hierarchy"],
        since = "1.0"
    )
    fun nestBy(args: List<UDM>): UDM {
        if (args.size < 5) {
            throw IllegalArgumentException(
                "nestBy() requires 5 arguments: parents, children, parentKeyFn, childKeyFn, childPropertyName. " +
                "Example: nestBy(\$input.orders, \$input.lines, (o) -> o.orderId, (l) -> l.orderId, \"lines\")"
            )
        }

        val parents = args[0]
        val children = args[1]
        val parentKeyFn = args[2]
        val childKeyFn = args[3]
        val childPropName = args[4]

        if (parents !is UDM.Array) {
            throw IllegalArgumentException("nestBy() first argument (parents) must be an array")
        }
        if (children !is UDM.Array) {
            throw IllegalArgumentException("nestBy() second argument (children) must be an array")
        }
        if (parentKeyFn !is UDM.Lambda) {
            throw IllegalArgumentException("nestBy() third argument (parentKeyFn) must be a lambda")
        }
        if (childKeyFn !is UDM.Lambda) {
            throw IllegalArgumentException("nestBy() fourth argument (childKeyFn) must be a lambda")
        }
        if (childPropName !is UDM.Scalar || childPropName.value !is String) {
            throw IllegalArgumentException(
                "nestBy() fifth argument (childPropertyName) must be a string. " +
                "Example: \"lines\" — this becomes the property name on each parent."
            )
        }

        val propName = childPropName.value as String

        // Step 1: Build hash index of children by key — O(M)
        val childIndex = mutableMapOf<String, MutableList<UDM>>()
        children.elements.forEach { child ->
            val key = childKeyFn.apply(listOf(child)).asString()
            childIndex.getOrPut(key) { mutableListOf() }.add(child)
        }

        // Step 2: Iterate parents, attach matched children — O(N) with O(1) lookup per parent
        val result = parents.elements.map { parent ->
            if (parent !is UDM.Object) {
                // Non-object parents pass through unchanged
                parent
            } else {
                val parentKey = parentKeyFn.apply(listOf(parent)).asString()
                val matchedChildren = childIndex[parentKey] ?: emptyList()

                // Create new object with all parent properties + the children property
                val newProperties = parent.properties.toMutableMap()
                newProperties[propName] = UDM.Array(matchedChildren)

                UDM.Object(newProperties, parent.attributes, parent.name, parent.metadata)
            }
        }

        return UDM.Array(result)
    }

    @UTLXFunction(
        description = "Split a flat sequence into chunks by position. A new chunk starts whenever the predicate returns true.",
        minArgs = 2,
        maxArgs = 2,
        category = "Data Restructuring",
        parameters = [
            "array: Flat sequence of records to chunk",
            "isNewChunkPredicate: Lambda (element) -> boolean — returns true to start a new chunk"
        ],
        returns = "Array of arrays — each inner array is a chunk of consecutive elements",
        example = "chunkBy(segments, (seg) -> seg.type == \"HEADER\") => [[HEADER, LINE, LINE], [HEADER, LINE]]",
        notes = "For positional/sequential grouping where parent-child is determined by position, not key.\n" +
                "Use chunkBy for SAP IDocs, EDI segments, log files, bank statements.\n" +
                "Use nestBy for key-based nesting. Use groupBy for key-based grouping.\n" +
                "Performance: O(N) — single sequential scan.",
        tags = ["restructuring", "chunking", "positional", "sequential"],
        since = "1.0"
    )
    fun chunkBy(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException(
                "chunkBy() requires 2 arguments: array, isNewChunkPredicate. " +
                "Example: chunkBy(\$input.segments, (seg) -> seg.type == \"E1EDK01\")"
            )
        }

        val array = args[0]
        val predicate = args[1]

        if (array !is UDM.Array) {
            throw IllegalArgumentException("chunkBy() first argument must be an array")
        }
        if (predicate !is UDM.Lambda) {
            throw IllegalArgumentException("chunkBy() second argument must be a lambda/function")
        }

        if (array.elements.isEmpty()) {
            return UDM.Array(emptyList())
        }

        val chunks = mutableListOf<MutableList<UDM>>()
        var currentChunk: MutableList<UDM>? = null

        for (element in array.elements) {
            val isNewChunk = predicate.apply(listOf(element))
            val startNew = when (isNewChunk) {
                is UDM.Scalar -> isNewChunk.value == true
                else -> false
            }

            if (startNew || currentChunk == null) {
                currentChunk = mutableListOf()
                chunks.add(currentChunk)
            }

            currentChunk.add(element)
        }

        return UDM.Array(chunks.map { UDM.Array(it) })
    }

    @UTLXFunction(
        description = "Flatten nested children alongside parent fields. Reverse of nestBy — hierarchical to flat.",
        minArgs = 2,
        maxArgs = 2,
        category = "Data Restructuring",
        parameters = [
            "array: Array of parent objects with nested child arrays",
            "childPropertyName: String name of the property containing the nested children"
        ],
        returns = "Flat array where each child is merged with its parent's fields (child overrides on collision)",
        example = "unnest(orders, \"lines\") => [{orderId: \"A\", product: \"Widget\", qty: 10}, ...]",
        notes = "Reverse of nestBy — takes hierarchical data and produces flat rows.\n" +
                "Parent fields are repeated for each child (denormalization).\n" +
                "The child property itself is removed from the output.\n" +
                "Child fields override parent fields on name collision.\n" +
                "Parents with no/empty/null children are excluded (no rows to produce).\n" +
                "For multi-level: chain unnest calls: unnest(unnest(data, \"lines\"), \"schedules\")",
        tags = ["restructuring", "flatten", "denormalize", "csv"],
        since = "1.0"
    )
    fun unnest(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw IllegalArgumentException(
                "unnest() requires 2 arguments: array, childPropertyName. " +
                "Example: unnest(\$input.orders, \"lines\")"
            )
        }

        val array = args[0]
        val childPropName = args[1]

        if (array !is UDM.Array) {
            throw IllegalArgumentException("unnest() first argument must be an array")
        }
        if (childPropName !is UDM.Scalar || childPropName.value !is String) {
            throw IllegalArgumentException(
                "unnest() second argument must be a string (the property name containing nested children)"
            )
        }

        val propName = childPropName.value as String
        val result = mutableListOf<UDM>()

        for (parent in array.elements) {
            if (parent !is UDM.Object) continue

            // Get the children array from the named property
            val childrenUdm = parent.properties[propName]
            val children = when (childrenUdm) {
                is UDM.Array -> childrenUdm.elements
                null -> continue  // property doesn't exist — skip parent
                else -> continue  // property is not an array — skip parent
            }

            if (children.isEmpty()) continue  // no children — skip parent (no rows to produce)

            // Parent fields WITHOUT the child property
            val parentFields = parent.properties.filterKeys { it != propName }

            for (child in children) {
                if (child is UDM.Object) {
                    // Merge: parent fields + child fields (child overrides on collision)
                    val merged = parentFields + child.properties
                    result.add(UDM.Object(merged, parent.attributes, null, parent.metadata))
                } else {
                    // Scalar child — just add parent fields + a "value" key
                    val merged = parentFields + mapOf("value" to child)
                    result.add(UDM.Object(merged))
                }
            }
        }

        return UDM.Array(result)
    }
}
