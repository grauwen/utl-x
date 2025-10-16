// Quick validation test for real functions
import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.string.AdvancedRegexFunctions

fun main() {
    // Test a simple regex function that exists
    val text = UDM.Scalar("test123abc456")
    val pattern = UDM.Scalar("([a-z]+)(\\d+)")
    
    println("Testing analyzeString...")
    val result = AdvancedRegexFunctions.analyzeString(listOf(text, pattern))
    val matches = result as UDM.Array
    println("Found ${matches.elements.size} matches")
    
    // Test regexNamedGroups to see what it actually returns
    println("\nTesting regexNamedGroups...")
    val namedText = UDM.Scalar("user@example.com")
    val namedPattern = UDM.Scalar("(?<user>\\w+)@(?<domain>[\\w.]+)")
    val namedResult = AdvancedRegexFunctions.regexNamedGroups(listOf(namedText, namedPattern))
    val namedGroups = namedResult as UDM.Object
    println("Named groups found: ${namedGroups.properties.keys}")
    namedGroups.properties.forEach { (key, value) ->
        println("  $key: ${(value as UDM.Scalar).value}")
    }
}