package org.apache.utlx.stdlib.string

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PluralizationFunctionsTest {

    @Test
    fun testRegularPluralization() {
        // Test simple s-ending words
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("cat")))
        assertEquals("cats", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("dog")))
        assertEquals("dogs", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("car")))
        assertEquals("cars", (result3 as UDM.Scalar).value)

        // Test words ending in s, x, z, ch, sh (add es)
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("box")))
        assertEquals("boxes", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("bus")))
        assertEquals("buses", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("quiz")))
        assertEquals("quizzes", (result6 as UDM.Scalar).value)

        val result7 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("church")))
        assertEquals("churches", (result7 as UDM.Scalar).value)

        val result8 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("dish")))
        assertEquals("dishes", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testYEndingWords() {
        // Test consonant + y -> ies
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("city")))
        assertEquals("cities", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("baby")))
        assertEquals("babies", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("lady")))
        assertEquals("ladies", (result3 as UDM.Scalar).value)

        // Test vowel + y -> s
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("boy")))
        assertEquals("boys", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("day")))
        assertEquals("days", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("toy")))
        assertEquals("toys", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testFEndingWords() {
        // Test f -> ves
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("leaf")))
        assertEquals("leaves", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("half")))
        assertEquals("halves", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("shelf")))
        assertEquals("shelves", (result3 as UDM.Scalar).value)

        // Test fe -> ves
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("wife")))
        assertEquals("wives", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("knife")))
        assertEquals("knives", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("life")))
        assertEquals("lives", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testOEndingWords() {
        // Test consonant + o -> es
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("hero")))
        assertEquals("heroes", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("echo")))
        assertEquals("echoes", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("potato")))
        assertEquals("potatoes", (result3 as UDM.Scalar).value)

        // Test vowel + o -> s
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("radio")))
        assertEquals("radios", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("video")))
        assertEquals("videos", (result5 as UDM.Scalar).value)
    }

    @Test
    fun testIrregularPlurals() {
        // Test common irregular plurals
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("man")))
        assertEquals("men", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("woman")))
        assertEquals("women", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("child")))
        assertEquals("children", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("foot")))
        assertEquals("feet", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("tooth")))
        assertEquals("teeth", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("mouse")))
        assertEquals("mice", (result6 as UDM.Scalar).value)

        val result7 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("goose")))
        assertEquals("geese", (result7 as UDM.Scalar).value)

        val result8 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("person")))
        assertEquals("people", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testUnchangedPlurals() {
        // Test words that don't change in plural form
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("sheep")))
        assertEquals("sheep", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("deer")))
        assertEquals("deer", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("fish")))
        assertEquals("fish", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("moose")))
        assertEquals("moose", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("series")))
        assertEquals("series", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("species")))
        assertEquals("species", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testUncountableNouns() {
        // Test uncountable nouns (should remain unchanged)
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("water")))
        assertEquals("water", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("money")))
        assertEquals("money", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("information")))
        assertEquals("information", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("equipment")))
        assertEquals("equipment", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("furniture")))
        assertEquals("furniture", (result5 as UDM.Scalar).value)
    }

    @Test
    fun testPluralizeWithCount() {
        // Test count = 1 (should return singular)
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("cat"), UDM.Scalar(1)))
        assertEquals("cat", (result1 as UDM.Scalar).value)

        // Test count > 1 (should return plural)
        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("cat"), UDM.Scalar(5)))
        assertEquals("cats", (result2 as UDM.Scalar).value)

        // Test count = 0 (should return plural)
        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("dog"), UDM.Scalar(0)))
        assertEquals("dogs", (result3 as UDM.Scalar).value)

        // Test with irregular plural
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("child"), UDM.Scalar(3)))
        assertEquals("children", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("child"), UDM.Scalar(1)))
        assertEquals("child", (result5 as UDM.Scalar).value)
    }

    @Test
    fun testSingularization() {
        // Test regular singularization
        val result1 = PluralizationFunctions.singularize(listOf(UDM.Scalar("cats")))
        assertEquals("cat", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.singularize(listOf(UDM.Scalar("dogs")))
        assertEquals("dog", (result2 as UDM.Scalar).value)

        // Test es-ending words
        val result3 = PluralizationFunctions.singularize(listOf(UDM.Scalar("boxes")))
        assertEquals("box", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.singularize(listOf(UDM.Scalar("churches")))
        assertEquals("church", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.singularize(listOf(UDM.Scalar("dishes")))
        assertEquals("dish", (result5 as UDM.Scalar).value)

        // Test ies-ending words
        val result6 = PluralizationFunctions.singularize(listOf(UDM.Scalar("cities")))
        assertEquals("city", (result6 as UDM.Scalar).value)

        val result7 = PluralizationFunctions.singularize(listOf(UDM.Scalar("babies")))
        assertEquals("baby", (result7 as UDM.Scalar).value)

        // Test ves-ending words
        val result8 = PluralizationFunctions.singularize(listOf(UDM.Scalar("leaves")))
        assertEquals("leaf", (result8 as UDM.Scalar).value)

        val result9 = PluralizationFunctions.singularize(listOf(UDM.Scalar("knives")))
        assertEquals("knife", (result9 as UDM.Scalar).value)
    }

    @Test
    fun testIrregularSingularization() {
        // Test irregular singularization
        val result1 = PluralizationFunctions.singularize(listOf(UDM.Scalar("men")))
        assertEquals("man", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.singularize(listOf(UDM.Scalar("women")))
        assertEquals("woman", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.singularize(listOf(UDM.Scalar("children")))
        assertEquals("child", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.singularize(listOf(UDM.Scalar("feet")))
        assertEquals("foot", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.singularize(listOf(UDM.Scalar("teeth")))
        assertEquals("tooth", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.singularize(listOf(UDM.Scalar("mice")))
        assertEquals("mouse", (result6 as UDM.Scalar).value)

        val result7 = PluralizationFunctions.singularize(listOf(UDM.Scalar("geese")))
        assertEquals("goose", (result7 as UDM.Scalar).value)

        val result8 = PluralizationFunctions.singularize(listOf(UDM.Scalar("people")))
        assertEquals("person", (result8 as UDM.Scalar).value)
    }

    @Test
    fun testPluralizeWithCountFunction() {
        // Test pluralizeWithCount function
        val result1 = PluralizationFunctions.pluralizeWithCount(listOf(UDM.Scalar("item"), UDM.Scalar(1)))
        assertEquals("item", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralizeWithCount(listOf(UDM.Scalar("item"), UDM.Scalar(5)))
        assertEquals("items", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralizeWithCount(listOf(UDM.Scalar("child"), UDM.Scalar(1)))
        assertEquals("child", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralizeWithCount(listOf(UDM.Scalar("child"), UDM.Scalar(3)))
        assertEquals("children", (result4 as UDM.Scalar).value)
    }

    @Test
    fun testIsPlural() {
        // Test detecting plural forms
        val result1 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("cats")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)

        val result2 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("cat")))
        assertFalse((result2 as UDM.Scalar).value as Boolean)

        val result3 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("children")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)

        val result4 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("child")))
        assertFalse((result4 as UDM.Scalar).value as Boolean)

        val result5 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("boxes")))
        assertTrue((result5 as UDM.Scalar).value as Boolean)

        val result6 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("cities")))
        assertTrue((result6 as UDM.Scalar).value as Boolean)

        // Test uncountable nouns (ambiguous - returns false)
        val result7 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("sheep")))
        assertFalse((result7 as UDM.Scalar).value as Boolean)

        val result8 = PluralizationFunctions.isPlural(listOf(UDM.Scalar("information")))
        assertFalse((result8 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testIsSingular() {
        // Test detecting singular forms
        val result1 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("cat")))
        assertTrue((result1 as UDM.Scalar).value as Boolean)

        val result2 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("cats")))
        assertFalse((result2 as UDM.Scalar).value as Boolean)

        val result3 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("child")))
        assertTrue((result3 as UDM.Scalar).value as Boolean)

        val result4 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("children")))
        assertFalse((result4 as UDM.Scalar).value as Boolean)

        val result5 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("box")))
        assertTrue((result5 as UDM.Scalar).value as Boolean)

        val result6 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("city")))
        assertTrue((result6 as UDM.Scalar).value as Boolean)

        // Test uncountable nouns (ambiguous - returns false)
        val result7 = PluralizationFunctions.isSingular(listOf(UDM.Scalar("sheep")))
        assertFalse((result7 as UDM.Scalar).value as Boolean)
    }

    @Test
    fun testFormatPlural() {
        // Test formatted output
        val result1 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(1), UDM.Scalar("cat")))
        assertEquals("1 cat", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(5), UDM.Scalar("cat")))
        assertEquals("5 cats", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(0), UDM.Scalar("item")))
        assertEquals("0 items", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(3), UDM.Scalar("child")))
        assertEquals("3 children", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(1), UDM.Scalar("box")))
        assertEquals("1 box", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.formatPlural(listOf(UDM.Scalar(2), UDM.Scalar("box")))
        assertEquals("2 boxes", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testCasePreservation() {
        // Test that case is preserved
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("Cat")))
        assertEquals("Cats", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("CAT")))
        assertEquals("CATS", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("Child")))
        assertEquals("Children", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("CHILD")))
        assertEquals("CHILDREN", (result4 as UDM.Scalar).value)

        // Test singularization case preservation
        val result5 = PluralizationFunctions.singularize(listOf(UDM.Scalar("Cats")))
        assertEquals("Cat", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.singularize(listOf(UDM.Scalar("CATS")))
        assertEquals("CAT", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testEdgeCases() {
        // Test empty string
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("")))
        assertEquals("", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.singularize(listOf(UDM.Scalar("")))
        assertEquals("", (result2 as UDM.Scalar).value)

        // Test null values
        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar(null)))
        assertEquals(null, (result3 as UDM.Scalar).value)

        // Test non-string values
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar(123)))
        assertEquals("123s", (result4 as UDM.Scalar).value) // Should convert to string

        // Test single character words
        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("a")))
        assertEquals("as", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("I")))
        assertEquals("Is", (result6 as UDM.Scalar).value)
    }

    @Test
    fun testArgumentValidation() {
        // Test missing arguments
        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.pluralize(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.singularize(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.pluralizeWithCount(listOf(UDM.Scalar("test")))
        }

        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.formatPlural(listOf(UDM.Scalar(1)))
        }

        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.isPlural(emptyList())
        }

        assertThrows<FunctionArgumentException> {
            PluralizationFunctions.isSingular(emptyList())
        }
    }

    @Test
    fun testComplexWords() {
        // Test compound words
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("schoolbus")))
        assertEquals("schoolbuses", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("butterfly")))
        assertEquals("butterflies", (result2 as UDM.Scalar).value)

        // Test words with numbers
        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("mp3")))
        assertEquals("mp3s", (result3 as UDM.Scalar).value)

        // Test hyphenated words
        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("mother-in-law")))
        assertEquals("mother-in-laws", (result4 as UDM.Scalar).value)

        // Test technical terms
        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("analysis")))
        assertEquals("analyses", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("criterion")))
        assertEquals("criteria", (result6 as UDM.Scalar).value)

        val result7 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("phenomenon")))
        assertEquals("phenomena", (result7 as UDM.Scalar).value)
    }

    @Test
    fun testRoundTripConversions() {
        // Test that pluralize -> singularize returns original
        val words = listOf("cat", "dog", "box", "city", "baby", "leaf", "wife")
        
        words.forEach { word ->
            val plural = PluralizationFunctions.pluralize(listOf(UDM.Scalar(word)))
            val backToSingular = PluralizationFunctions.singularize(listOf(plural))
            assertEquals(word, (backToSingular as UDM.Scalar).value, "Round-trip failed for: $word")
        }

        // Test irregular words
        val irregularWords = listOf("child", "man", "woman", "foot", "tooth", "mouse")
        
        irregularWords.forEach { word ->
            val plural = PluralizationFunctions.pluralize(listOf(UDM.Scalar(word)))
            val backToSingular = PluralizationFunctions.singularize(listOf(plural))
            assertEquals(word, (backToSingular as UDM.Scalar).value, "Irregular round-trip failed for: $word")
        }
    }

    @Test
    fun testScientificTerms() {
        // Test Latin/Greek scientific terms
        val result1 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("cactus")))
        assertEquals("cacti", (result1 as UDM.Scalar).value)

        val result2 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("focus")))
        assertEquals("foci", (result2 as UDM.Scalar).value)

        val result3 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("nucleus")))
        assertEquals("nuclei", (result3 as UDM.Scalar).value)

        val result4 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("index")))
        assertEquals("indices", (result4 as UDM.Scalar).value)

        val result5 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("matrix")))
        assertEquals("matrices", (result5 as UDM.Scalar).value)

        val result6 = PluralizationFunctions.pluralize(listOf(UDM.Scalar("vertex")))
        assertEquals("vertices", (result6 as UDM.Scalar).value)

        // Test reverse
        val result7 = PluralizationFunctions.singularize(listOf(UDM.Scalar("cacti")))
        assertEquals("cactus", (result7 as UDM.Scalar).value)

        val result8 = PluralizationFunctions.singularize(listOf(UDM.Scalar("indices")))
        assertEquals("index", (result8 as UDM.Scalar).value)
    }
}