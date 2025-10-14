// stdlib/src/test/kotlin/org/apache/utlx/stdlib/array/ArrayFunctionsTest.kt
package org.apache.utlx.stdlib.array

import org.apache.utlx.core.udm.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for Array Functions (25 functions)
 */
class ArrayFunctionsTest {
    
    private lateinit var testArray: UDM.Array
    private lateinit var emptyArray: UDM.Array
    private lateinit var numberArray: UDM.Array
    
    @BeforeEach
    fun setup() {
        testArray = UDM.Array(listOf(
            UDM.Scalar(1),
            UDM.Scalar(2),
            UDM.Scalar(3),
            UDM.Scalar(4),
            UDM.Scalar(5)
        ))
        
        emptyArray = UDM.Array(emptyList())
        
        numberArray = UDM.Array(listOf(
            UDM.Scalar(10),
            UDM.Scalar(20),
            UDM.Scalar(30),
            UDM.Scalar(40),
            UDM.Scalar(50)
        ))
    }
    
    // ============================================================================
    // Array Access Functions
    // ============================================================================
    
    @Nested
    @DisplayName("Array Access Operations")
    inner class AccessOperations {
        
        @Test
        fun `size() returns array length`() {
            val result = ArrayFunctions.size(testArray)
            assertEquals(5, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `get() retrieves element at index`() {
            val index = UDM.Scalar(2)
            val result = ArrayFunctions.get(testArray, index)
            assertEquals(3, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `get() throws on out of bounds`() {
            assertThrows<IndexOutOfBoundsException> {
                ArrayFunctions.get(testArray, UDM.Scalar(10))
            }
        }
        
        @Test
        fun `first() returns first element`() {
            val result = ArrayFunctions.first(testArray)
            assertEquals(1, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `last() returns last element`() {
            val result = ArrayFunctions.last(testArray)
            assertEquals(5, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `head() returns first element`() {
            val result = ArrayFunctions.head(testArray)
            assertEquals(1, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `tail() returns all except first`() {
            val result = ArrayFunctions.tail(testArray) as UDM.Array
            assertEquals(4, result.elements.size)
            assertEquals(2, (result.elements[0] as UDM.Scalar).value)
            assertEquals(5, (result.elements[3] as UDM.Scalar).value)
        }
        
        @Test
        fun `slice() extracts subarray`() {
            val start = UDM.Scalar(1)
            val end = UDM.Scalar(4)
            val result = ArrayFunctions.slice(testArray, start, end) as UDM.Array
            
            assertEquals(3, result.elements.size)
            assertEquals(2, (result.elements[0] as UDM.Scalar).value)
            assertEquals(4, (result.elements[2] as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // Array Transformation Functions
    // ============================================================================
    
    @Nested
    @DisplayName("Array Transformation Operations")
    inner class TransformationOperations {
        
        @Test
        fun `map() transforms each element`() {
            val mapper: (UDM) -> UDM = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                UDM.Scalar(value * 2)
            }
            
            val result = ArrayFunctions.map(testArray, mapper) as UDM.Array
            
            assertEquals(5, result.elements.size)
            assertEquals(2, (result.elements[0] as UDM.Scalar).value)
            assertEquals(10, (result.elements[4] as UDM.Scalar).value)
        }
        
        @Test
        fun `filter() keeps matching elements`() {
            val predicate: (UDM) -> Boolean = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                value > 2
            }
            
            val result = ArrayFunctions.filter(testArray, predicate) as UDM.Array
            
            assertEquals(3, result.elements.size)
            assertEquals(3, (result.elements[0] as UDM.Scalar).value)
            assertEquals(5, (result.elements[2] as UDM.Scalar).value)
        }
        
        @Test
        fun `reduce() aggregates array`() {
            val reducer: (UDM, UDM) -> UDM = { acc, elem ->
                val accValue = (acc as UDM.Scalar).value as Int
                val elemValue = (elem as UDM.Scalar).value as Int
                UDM.Scalar(accValue + elemValue)
            }
            
            val initial = UDM.Scalar(0)
            val result = ArrayFunctions.reduce(testArray, reducer, initial)
            assertEquals(15, (result as UDM.Scalar).value) // 1+2+3+4+5
        }
        
        @Test
        fun `flatMap() flattens and maps`() {
            val array = UDM.Array(listOf(
                UDM.Scalar(1),
                UDM.Scalar(2),
                UDM.Scalar(3)
            ))
            
            val mapper: (UDM) -> UDM = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                UDM.Array(listOf(
                    UDM.Scalar(value),
                    UDM.Scalar(value * 10)
                ))
            }
            
            val result = ArrayFunctions.flatMap(array, mapper) as UDM.Array
            assertEquals(6, result.elements.size) // [1,10,2,20,3,30]
        }
        
        @Test
        fun `flatten() removes nesting`() {
            val nested = UDM.Array(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Array(listOf(UDM.Scalar(3), UDM.Scalar(4)))
            ))
            
            val result = ArrayFunctions.flatten(nested) as UDM.Array
            assertEquals(4, result.elements.size)
        }
    }
    
    // ============================================================================
    // Array Aggregation Functions
    // ============================================================================
    
    @Nested
    @DisplayName("Array Aggregation Operations")
    inner class AggregationOperations {
        
        @Test
        fun `sum() adds all numbers`() {
            val result = Aggregations.sum(numberArray)
            assertEquals(150, (result as UDM.Scalar).value) // 10+20+30+40+50
        }
        
        @Test
        fun `avg() calculates average`() {
            val result = Aggregations.avg(numberArray)
            assertEquals(30.0, (result as UDM.Scalar).value) // 150/5
        }
        
        @Test
        fun `min() finds smallest value`() {
            val result = Aggregations.min(numberArray)
            assertEquals(10, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `max() finds largest value`() {
            val result = Aggregations.max(numberArray)
            assertEquals(50, (result as UDM.Scalar).value)
        }
        
        @Test
        fun `count() returns element count`() {
            val result = Aggregations.count(testArray)
            assertEquals(5, (result as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // Array Ordering Functions
    // ============================================================================
    
    @Nested
    @DisplayName("Array Ordering Operations")
    inner class OrderingOperations {
        
        @Test
        fun `sort() orders elements ascending`() {
            val unsorted = UDM.Array(listOf(
                UDM.Scalar(3),
                UDM.Scalar(1),
                UDM.Scalar(4),
                UDM.Scalar(2)
            ))
            
            val result = ArrayFunctions.sort(unsorted) as UDM.Array
            assertEquals(1, (result.elements[0] as UDM.Scalar).value)
            assertEquals(4, (result.elements[3] as UDM.Scalar).value)
        }
        
        @Test
        fun `sortBy() orders by custom key`() {
            val objects = UDM.Array(listOf(
                UDM.Object(mapOf("age" to UDM.Scalar(30)), emptyMap()),
                UDM.Object(mapOf("age" to UDM.Scalar(20)), emptyMap()),
                UDM.Object(mapOf("age" to UDM.Scalar(25)), emptyMap())
            ))
            
            val keyExtractor: (UDM) -> Comparable<*> = { elem ->
                ((elem as UDM.Object).properties["age"] as UDM.Scalar).value as Int
            }
            
            val result = ArrayFunctions.sortBy(objects, keyExtractor) as UDM.Array
            val firstAge = ((result.elements[0] as UDM.Object)
                .properties["age"] as UDM.Scalar).value
            assertEquals(20, firstAge)
        }
        
        @Test
        fun `reverse() reverses order`() {
            val result = ArrayFunctions.reverse(testArray) as UDM.Array
            assertEquals(5, (result.elements[0] as UDM.Scalar).value)
            assertEquals(1, (result.elements[4] as UDM.Scalar).value)
        }
    }
    
    // ============================================================================
    // Array Set Operations
    // ============================================================================
    
    @Nested
    @DisplayName("Array Set Operations")
    inner class SetOperations {
        
        @Test
        fun `distinct() removes duplicates`() {
            val withDupes = UDM.Array(listOf(
                UDM.Scalar(1),
                UDM.Scalar(2),
                UDM.Scalar(1),
                UDM.Scalar(3),
                UDM.Scalar(2)
            ))
            
            val result = ArrayFunctions.distinct(withDupes) as UDM.Array
            assertEquals(3, result.elements.size)
        }
        
        @Test
        fun `distinctBy() removes duplicates by key`() {
            val objects = UDM.Array(listOf(
                UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("A")), emptyMap()),
                UDM.Object(mapOf("id" to UDM.Scalar(1), "name" to UDM.Scalar("B")), emptyMap()),
                UDM.Object(mapOf("id" to UDM.Scalar(2), "name" to UDM.Scalar("C")), emptyMap())
            ))
            
            val keyExtractor: (UDM) -> Any = { elem ->
                ((elem as UDM.Object).properties["id"] as UDM.Scalar).value!!
            }
            
            val result = ArrayFunctions.distinctBy(objects, keyExtractor) as UDM.Array
            assertEquals(2, result.elements.size)
        }
        
        @Test
        fun `union() combines arrays`() {
            val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
            val array2 = UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3)))
            
            val result = ArrayFunctions.union(array1, array2) as UDM.Array
            assertEquals(3, result.elements.size) // [1,2,3] - distinct
        }
        
        @Test
        fun `intersect() finds common elements`() {
            val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
            val array2 = UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
            
            val result = ArrayFunctions.intersect(array1, array2) as UDM.Array
            assertEquals(2, result.elements.size) // [2,3]
        }
        
        @Test
        fun `diff() finds elements only in first array`() {
            val array1 = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
            val array2 = UDM.Array(listOf(UDM.Scalar(2), UDM.Scalar(3), UDM.Scalar(4)))
            
            val result = ArrayFunctions.diff(array1, array2) as UDM.Array
            assertEquals(1, result.elements.size) // [1]
        }
    }
    
    // ============================================================================
    // Array Predicates
    // ============================================================================
    
    @Nested
    @DisplayName("Array Predicate Operations")
    inner class PredicateOperations {
        
        @Test
        fun `isEmpty() detects empty array`() {
            assertTrue(ArrayFunctions.isEmpty(emptyArray) as Boolean)
            assertFalse(ArrayFunctions.isEmpty(testArray) as Boolean)
        }
        
        @Test
        fun `contains() finds element`() {
            val search = UDM.Scalar(3)
            assertTrue(ArrayFunctions.contains(testArray, search))
            
            val missing = UDM.Scalar(10)
            assertFalse(ArrayFunctions.contains(testArray, missing))
        }
        
        @Test
        fun `every() checks all elements match`() {
            val predicate: (UDM) -> Boolean = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                value > 0
            }
            
            assertTrue(ArrayFunctions.every(testArray, predicate))
        }
        
        @Test
        fun `some() checks any element matches`() {
            val predicate: (UDM) -> Boolean = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                value > 4
            }
            
            assertTrue(ArrayFunctions.some(testArray, predicate))
        }
        
        @Test
        fun `none() checks no elements match`() {
            val predicate: (UDM) -> Boolean = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                value > 10
            }
            
            assertTrue(ArrayFunctions.none(testArray, predicate))
        }
    }
    
    // ============================================================================
    // Advanced Operations
    // ============================================================================
    
    @Nested
    @DisplayName("Advanced Array Operations")
    inner class AdvancedOperations {
        
        @Test
        fun `groupBy() groups elements by key`() {
            val items = UDM.Array(listOf(
                UDM.Object(mapOf(
                    "category" to UDM.Scalar("fruit"),
                    "name" to UDM.Scalar("apple")
                ), emptyMap()),
                UDM.Object(mapOf(
                    "category" to UDM.Scalar("veggie"),
                    "name" to UDM.Scalar("carrot")
                ), emptyMap()),
                UDM.Object(mapOf(
                    "category" to UDM.Scalar("fruit"),
                    "name" to UDM.Scalar("banana")
                ), emptyMap())
            ))
            
            val keyExtractor: (UDM) -> String = { elem ->
                ((elem as UDM.Object).properties["category"] as UDM.Scalar).value as String
            }
            
            val result = MoreArrayFunctions.groupBy(items, keyExtractor) as UDM.Object
            
            val fruitGroup = result.properties["fruit"] as UDM.Array
            assertEquals(2, fruitGroup.elements.size)
            
            val veggieGroup = result.properties["veggie"] as UDM.Array
            assertEquals(1, veggieGroup.elements.size)
        }
        
        @Test
        fun `zip() combines two arrays`() {
            val keys = UDM.Array(listOf(UDM.Scalar("a"), UDM.Scalar("b"), UDM.Scalar("c")))
            val values = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
            
            val result = MoreArrayFunctions.zip(keys, values) as UDM.Array
            assertEquals(3, result.elements.size)
            
            val firstPair = result.elements[0] as UDM.Array
            assertEquals("a", (firstPair.elements[0] as UDM.Scalar).value)
            assertEquals(1, (firstPair.elements[1] as UDM.Scalar).value)
        }
        
        @Test
        fun `partition() splits by predicate`() {
            val predicate: (UDM) -> Boolean = { elem ->
                val value = (elem as UDM.Scalar).value as Int
                value % 2 == 0
            }
            
            val result = MoreArrayFunctions.partition(testArray, predicate) as UDM.Array
            
            val evens = result.elements[0] as UDM.Array
            val odds = result.elements[1] as UDM.Array
            
            assertEquals(2, evens.elements.size) // [2, 4]
            assertEquals(3, odds.elements.size)  // [1, 3, 5]
        }
    }
    
    // ============================================================================
    // Edge Cases
    // ============================================================================
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        fun `operations on empty array`() {
            assertThrows<NoSuchElementException> {
                ArrayFunctions.first(emptyArray)
            }
            
            assertThrows<NoSuchElementException> {
                ArrayFunctions.last(emptyArray)
            }
            
            assertEquals(0, (ArrayFunctions.size(emptyArray) as UDM.Scalar).value)
        }
        
        @Test
        fun `handles nested arrays`() {
            val nested = UDM.Array(listOf(
                UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
                UDM.Array(listOf(UDM.Scalar(3), UDM.Scalar(4)))
            ))
            
            val result = ArrayFunctions.flatten(nested) as UDM.Array
            assertEquals(4, result.elements.size)
        }
        
        @Test
        fun `handles mixed types in array`() {
            val mixed = UDM.Array(listOf(
                UDM.Scalar(1),
                UDM.Scalar("hello"),
                UDM.Scalar(true),
                UDM.Scalar(null)
            ))
            
            assertEquals(4, (ArrayFunctions.size(mixed) as UDM.Scalar).value)
        }
    }
}
