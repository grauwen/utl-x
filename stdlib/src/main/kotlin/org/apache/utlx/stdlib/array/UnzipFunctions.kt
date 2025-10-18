package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.annotations.UTLXFunction

/**
 * Additional array transformation functions including unzip
 */
object UnzipFunctions {
    
    @UTLXFunction(
        description = "Unzip array of pairs into two arrays (inverse of zip)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "unzip([[1, \"a\"], [2, \"b\"], [3, \"c\"]])",
        notes = """Result: [[1, 2, 3], ["a", "b", "c"]]
This is the fundamental inverse operation of zip:
- zip([1,2,3], ["a","b","c"]) => [[1,"a"], [2,"b"], [3,"c"]]
- unzip([[1,"a"], [2,"b"], [3,"c"]]) => [[1,2,3], ["a","b","c"]]
Common use cases:
- Separating paired data back into separate columns
- Processing results from zip operations
- Data transformation pipelines
- Converting row-oriented to column-oriented data""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Unzip array of pairs into two arrays (inverse of zip)
     * 
     * Usage: unzip([[1, "a"], [2, "b"], [3, "c"]])
     * Result: [[1, 2, 3], ["a", "b", "c"]]
     * 
     * This is the fundamental inverse operation of zip:
     * - zip([1,2,3], ["a","b","c"]) => [[1,"a"], [2,"b"], [3,"c"]]
     * - unzip([[1,"a"], [2,"b"], [3,"c"]]) => [[1,2,3], ["a","b","c"]]
     * 
     * Common use cases:
     * - Separating paired data back into separate columns
     * - Processing results from zip operations
     * - Data transformation pipelines
     * - Converting row-oriented to column-oriented data
     */
    fun unzip(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("unzip expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("unzip expects an array, got ${array::class.simpleName}")
        }
        
        // Handle empty array
        if (array.elements.isEmpty()) {
            return UDM.Array(listOf(
                UDM.Array(emptyList()),
                UDM.Array(emptyList())
            ))
        }
        
        val firstArray = mutableListOf<UDM>()
        val secondArray = mutableListOf<UDM>()
        
        for (pair in array.elements) {
            if (pair !is UDM.Array) {
                throw IllegalArgumentException(
                    "unzip expects array of arrays (pairs), but found ${pair::class.simpleName}"
                )
            }
            
            if (pair.elements.size < 2) {
                throw IllegalArgumentException(
                    "unzip expects pairs (2-element arrays), but found array with ${pair.elements.size} elements"
                )
            }
            
            firstArray.add(pair.elements[0])
            secondArray.add(pair.elements[1])
        }
        
        return UDM.Array(listOf(
            UDM.Array(firstArray),
            UDM.Array(secondArray)
        ))
    }
    
    @UTLXFunction(
        description = "Unzip array of N-tuples into N arrays",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "unzipN([[1, \"a\", true], [2, \"b\", false]])",
        notes = """Result: [[1, 2], ["a", "b"], [true, false]]
Generalized version of unzip that works with tuples of any size.
All tuples must have the same length.""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Unzip array of N-tuples into N arrays
     * 
     * Usage: unzipN([[1, "a", true], [2, "b", false]])
     * Result: [[1, 2], ["a", "b"], [true, false]]
     * 
     * Generalized version of unzip that works with tuples of any size.
     * All tuples must have the same length.
     */
    fun unzipN(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("unzipN expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("unzipN expects an array, got ${array::class.simpleName}")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        // Get the first tuple to determine size
        val firstTuple = array.elements[0]
        if (firstTuple !is UDM.Array) {
            throw IllegalArgumentException("unzipN expects array of arrays")
        }
        
        val tupleSize = firstTuple.elements.size
        if (tupleSize == 0) {
            throw IllegalArgumentException("unzipN requires non-empty tuples")
        }
        
        // Initialize result arrays
        val resultArrays = List(tupleSize) { mutableListOf<UDM>() }
        
        // Process each tuple
        for (tuple in array.elements) {
            if (tuple !is UDM.Array) {
                throw IllegalArgumentException("unzipN expects array of arrays")
            }
            
            if (tuple.elements.size != tupleSize) {
                throw IllegalArgumentException(
                    "unzipN requires all tuples to have the same size. " +
                    "Expected $tupleSize elements, found ${tuple.elements.size}"
                )
            }
            
            // Add each element to its respective result array
            tuple.elements.forEachIndexed { index, element ->
                resultArrays[index].add(element)
            }
        }
        
        // Convert to UDM.Array
        return UDM.Array(
            resultArrays.map { UDM.Array(it) }
        )
    }
    
    @UTLXFunction(
        description = "Transpose a 2D array (swap rows and columns)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "transpose([[1, 2, 3], [4, 5, 6]])",
        notes = """Result: [[1, 4], [2, 5], [3, 6]]
This is essentially unzipN but works with jagged arrays (different row lengths)
and is semantically clearer for matrix operations.
Similar to unzip but:
- Works for any number of arrays (not just pairs)
- Handles jagged arrays (rows with different lengths)
- More intuitive name for matrix transformations""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Transpose a 2D array (swap rows and columns)
     * 
     * Usage: transpose([[1, 2, 3], [4, 5, 6]])
     * Result: [[1, 4], [2, 5], [3, 6]]
     * 
     * This is essentially unzipN but works with jagged arrays (different row lengths)
     * and is semantically clearer for matrix operations.
     * 
     * Similar to unzip but:
     * - Works for any number of arrays (not just pairs)
     * - Handles jagged arrays (rows with different lengths)
     * - More intuitive name for matrix transformations
     */
    fun transpose(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("transpose expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("transpose expects an array, got ${array::class.simpleName}")
        }
        
        if (array.elements.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        // Get all rows as arrays
        val rows = array.elements.map { row ->
            if (row !is UDM.Array) {
                throw IllegalArgumentException("transpose expects array of arrays (2D array)")
            }
            row.elements
        }
        
        if (rows.isEmpty()) {
            return UDM.Array(emptyList())
        }
        
        // Find the maximum length to handle jagged arrays
        val maxLength = rows.maxOf { it.size }
        
        // Transpose: column i becomes row i
        val transposed = (0 until maxLength).map { col ->
            val column = rows.mapNotNull { row ->
                if (col < row.size) row[col] else null
            }
            UDM.Array(column)
        }
        
        return UDM.Array(transposed)
    }
    
    @UTLXFunction(
        description = "Zip multiple arrays together (generalized zip)",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "zipWith([1, 2], [\"a\", \"b\"], [true, false])",
        notes = """Result: [[1, "a", true], [2, "b", false]]
Inverse of unzipN. Takes N arrays and creates array of N-tuples.
Stops at the length of the shortest array.""",
        tags = ["array"],
        since = "1.0"
    )
    /**
     * Zip multiple arrays together (generalized zip)
     * 
     * Usage: zipWith([1, 2], ["a", "b"], [true, false])
     * Result: [[1, "a", true], [2, "b", false]]
     * 
     * Inverse of unzipN. Takes N arrays and creates array of N-tuples.
     * Stops at the length of the shortest array.
     */
    fun zipWith(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw IllegalArgumentException("zipWith expects at least 1 array")
        }
        
        // Convert all arguments to arrays
        val arrays = args.map { arg ->
            if (arg !is UDM.Array) {
                throw IllegalArgumentException("zipWith expects only arrays, got ${arg::class.simpleName}")
            }
            arg.elements
        }
        
        if (arrays.any { it.isEmpty() }) {
            return UDM.Array(emptyList())
        }
        
        // Find minimum length (zip stops at shortest array)
        val minLength = arrays.minOf { it.size }
        
        // Create tuples
        val zipped = (0 until minLength).map { index ->
            val tuple = arrays.map { array -> array[index] }
            UDM.Array(tuple)
        }
        
        return UDM.Array(zipped)
    }
    
    @UTLXFunction(
        description = "Zip arrays with indices",
        minArgs = 1,
        maxArgs = 1,
        category = "Array",
        parameters = [
            "array: Input array to process",
        "array: Array to search",
        "predicate: Condition to match"
        ],
        returns = "Index of the element",
        example = "zipWithIndex([10, 20, 30])",
        notes = """Result: [[0, 10], [1, 20], [2, 30]]
Useful for tracking positions during transformations.""",
        tags = ["array", "index"],
        since = "1.0"
    )
    /**
     * Zip arrays with indices
     * 
     * Usage: zipWithIndex([10, 20, 30])
     * Result: [[0, 10], [1, 20], [2, 30]]
     * 
     * Useful for tracking positions during transformations.
     */
    fun zipWithIndex(args: List<UDM>): UDM {
        if (args.size != 1) {
            throw IllegalArgumentException("zipWithIndex expects 1 argument, got ${args.size}")
        }
        
        val array = args[0]
        if (array !is UDM.Array) {
            throw IllegalArgumentException("zipWithIndex expects an array, got ${array::class.simpleName}")
        }
        
        val indexed = array.elements.mapIndexed { index, element ->
            UDM.Array(listOf(
                UDM.Scalar(index.toDouble()),
                element
            ))
        }
        
        return UDM.Array(indexed)
    }
}

/**
 * Registration in Functions.kt:
 * 
 * Add these to the registerArrayFunctions() method:
 * 
 * // Zip/Unzip operations
 * register("unzip", UnzipFunctions::unzip)
 * register("unzipN", UnzipFunctions::unzipN)
 * register("transpose", UnzipFunctions::transpose)
 * register("zipWith", UnzipFunctions::zipWith)
 * register("zipWithIndex", UnzipFunctions::zipWithIndex)
 */
