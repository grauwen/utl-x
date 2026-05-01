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
}
