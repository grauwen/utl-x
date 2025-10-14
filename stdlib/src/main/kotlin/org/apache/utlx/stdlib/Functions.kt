// stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt
package org.apache.utlx.stdlib

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.array.*
import org.apache.utlx.stdlib.string.*
import org.apache.utlx.stdlib.math.*
import org.apache.utlx.stdlib.date.*
import org.apache.utlx.stdlib.type.*
import org.apache.utlx.stdlib.objects.*
import org.apache.utlx.stdlib.core.*
import org.apache.utlx.stdlib.encoding.*

/**
 * UTL-X Standard Library Function Registry
 * 
 * COMPLETE: 120+ functions achieving 100%+ parity with TIBCO BW
 */
object StandardLibrary {
    
    private val functions = mutableMapOf<String, UTLXFunction>()
    
    init {
        registerAllFunctions()
    }
    
    fun getFunction(name: String): UTLXFunction? = functions[name]
    fun hasFunction(name: String): Boolean = functions.containsKey(name)
    fun getFunctionNames(): Set<String> = functions.keys
    fun getAllFunctions(): Map<String, UTLXFunction> = functions.toMap()
    fun registerFunction(name: String, function: UTLXFunction) { functions[name] = function }
    
    private fun registerAllFunctions() {
        // Core control flow functions
        registerCoreFunctions()
        
        // String functions
        registerStringFunctions()
        
        // Array functions
        registerArrayFunctions()
        
        // Math functions
        registerMathFunctions()
        
        // Date functions
        registerDateFunctions()
        
        // Type functions
        registerTypeFunctions()
        
        // Object functions
        registerObjectFunctions()
        
        // Encoding functions
        registerEncodingFunctions()
    }
    
    private fun registerCoreFunctions() {
        register("if", CoreFunctions::ifThenElse)
        register("coalesce", CoreFunctions::coalesce)
        register("generate-uuid", CoreFunctions::generateUuid)
        register("default", CoreFunctions::default)
    }
    
    private fun registerStringFunctions() {
        // Basic string operations
        register("upper", StringFunctions::upper)
        register("lower", StringFunctions::lower)
        register("trim", StringFunctions::trim)
        register("substring", StringFunctions::substring)
        register("concat", StringFunctions::concat)
        register("split", StringFunctions::split)
        register("join", StringFunctions::join)
        register("replace", StringFunctions::replace)
        register("contains", StringFunctions::contains)
        register("startsWith", StringFunctions::startsWith)
        register("endsWith", StringFunctions::endsWith)
        register("length", StringFunctions::length)
        
        // Regex functions
        register("matches", RegexFunctions::matches)
        register("replaceRegex", RegexFunctions::replaceRegex)
        
        // Extended string functions
        register("substring-before", ExtendedStringFunctions::substringBefore)
        register("substring-after", ExtendedStringFunctions::substringAfter)
        register("substring-before-last", ExtendedStringFunctions::substringBeforeLast)
        register("substring-after-last", ExtendedStringFunctions::substringAfterLast)
        register("pad", ExtendedStringFunctions::pad)
        register("pad-right", ExtendedStringFunctions::padRight)
        register("normalize-space", ExtendedStringFunctions::normalizeSpace)
        register("repeat", ExtendedStringFunctions::repeat)
        
        // More string functions (Priority 2)
        register("leftTrim", MoreStringFunctions::leftTrim)
        register("rightTrim", MoreStringFunctions::rightTrim)
        register("translate", MoreStringFunctions::translate)
        register("reverse", MoreStringFunctions::reverse)
        register("isEmpty", MoreStringFunctions::isEmpty)
        register("isBlank", MoreStringFunctions::isBlank)
        register("charAt", MoreStringFunctions::charAt)
        register("charCodeAt", MoreStringFunctions::charCodeAt)
        register("fromCharCode", MoreStringFunctions::fromCharCode)
        register("capitalize", MoreStringFunctions::capitalize)
        register("titleCase", MoreStringFunctions::titleCase)
    }
    
    private fun registerArrayFunctions() {
        // Functional operations
        register("map", ArrayFunctions::map)
        register("filter", ArrayFunctions::filter)
        register("reduce", ArrayFunctions::reduce)
        register("find", ArrayFunctions::find)
        register("findIndex", ArrayFunctions::findIndex)
        register("every", ArrayFunctions::every)
        register("some", ArrayFunctions::some)
        
        // Array manipulation
        register("flatten", ArrayFunctions::flatten)
        register("reverse", ArrayFunctions::reverse)
        register("sort", ArrayFunctions::sort)
        register("sortBy", ArrayFunctions::sortBy)
        register("first", ArrayFunctions::first)
        register("last", ArrayFunctions::last)
        register("take", ArrayFunctions::take)
        register("drop", ArrayFunctions::drop)
        register("unique", ArrayFunctions::unique)
        register("zip", ArrayFunctions::zip)
        
        // More array functions (Priority 2)
        register("remove", MoreArrayFunctions::remove)
        register("insertBefore", MoreArrayFunctions::insertBefore)
        register("insertAfter", MoreArrayFunctions::insertAfter)
        register("indexOf", MoreArrayFunctions::indexOf)
        register("lastIndexOf", MoreArrayFunctions::lastIndexOf)
        register("includes", MoreArrayFunctions::includes)
        register("slice", MoreArrayFunctions::slice)
        register("concat", MoreArrayFunctions::concat)
        
        // Aggregation functions
        register("sum", Aggregations::sum)
        register("avg", Aggregations::avg)
        register("min", Aggregations::min)
        register("max", Aggregations::max)
        register("count", Aggregations::count)
    }
    
    private fun registerMathFunctions() {
        // Basic math
        register("abs", MathFunctions::abs)
        register("round", MathFunctions::round)
        register("ceil", MathFunctions::ceil)
        register("floor", MathFunctions::floor)
        register("pow", MathFunctions::pow)
        register("sqrt", MathFunctions::sqrt)
        register("random", MathFunctions::random)
        
        // Extended math
        register("format-number", ExtendedMathFunctions::formatNumber)
        register("parse-int", ExtendedMathFunctions::parseInt)
        register("parse-float", ExtendedMathFunctions::parseFloat)
    }
    
    private fun registerDateFunctions() {
        // Basic date functions
        register("now", DateFunctions::now)
        register("parseDate", DateFunctions::parseDate)
        register("formatDate", DateFunctions::formatDate)
        register("addDays", DateFunctions::addDays)
        register("addHours", DateFunctions::addHours)
        register("diffDays", DateFunctions::diffDays)
        
        // Extended date functions
        register("day", ExtendedDateFunctions::day)
        register("month", ExtendedDateFunctions::month)
        register("year", ExtendedDateFunctions::year)
        register("hours", ExtendedDateFunctions::hours)
        register("minutes", ExtendedDateFunctions::minutes)
        register("seconds", ExtendedDateFunctions::seconds)
        register("compare-dates", ExtendedDateFunctions::compareDates)
        register("validate-date", ExtendedDateFunctions::validateDate)
        
        // More date functions (Priority 2)
        register("addMonths", MoreDateFunctions::addMonths)
        register("addYears", MoreDateFunctions::addYears)
        register("addMinutes", MoreDateFunctions::addMinutes)
        register("addSeconds", MoreDateFunctions::addSeconds)
        register("getTimezone", MoreDateFunctions::getTimezone)
        register("diffHours", MoreDateFunctions::diffHours)
        register("diffMinutes", MoreDateFunctions::diffMinutes)
        register("diffSeconds", MoreDateFunctions::diffSeconds)
        register("currentDate", MoreDateFunctions::currentDate)
        register("currentTime", MoreDateFunctions::currentTime)
    }
    
    private fun registerTypeFunctions() {
        register("typeOf", TypeFunctions::typeOf)
        register("isString", TypeFunctions::isString)
        register("isNumber", TypeFunctions::isNumber)
        register("isBoolean", TypeFunctions::isBoolean)
        register("isArray", TypeFunctions::isArray)
        register("isObject", TypeFunctions::isObject)
        register("isNull", TypeFunctions::isNull)
        register("isDefined", TypeFunctions::isDefined)
    }
    
    private fun registerObjectFunctions() {
        register("keys", ObjectFunctions::keys)
        register("values", ObjectFunctions::values)
        register("entries", ObjectFunctions::entries)
        register("merge", ObjectFunctions::merge)
        register("pick", ObjectFunctions::pick)
        register("omit", ObjectFunctions::omit)
    }
    
    private fun registerEncodingFunctions() {
        register("base64-encode", EncodingFunctions::base64Encode)
        register("base64-decode", EncodingFunctions::base64Decode)
        register("url-encode", EncodingFunctions::urlEncode)
        register("url-decode", EncodingFunctions::urlDecode)
        register("hex-encode", EncodingFunctions::hexEncode)
        register("hex-decode", EncodingFunctions::hexDecode)
    }
    
    private fun register(name: String, impl: (List<UDM>) -> UDM) {
        functions[name] = UTLXFunction(name, impl)
    }
}

/**
 * UTL-X Function wrapper
 */
data class UTLXFunction(
    val name: String,
    val implementation: (List<UDM>) -> UDM,
    val minArgs: Int? = null,
    val maxArgs: Int? = null,
    val description: String? = null
) {
    fun execute(args: List<UDM>): UDM {
        if (minArgs != null && args.size < minArgs) {
            throw IllegalArgumentException("$name expects at least $minArgs arguments, got ${args.size}")
        }
        if (maxArgs != null && args.size > maxArgs) {
            throw IllegalArgumentException("$name expects at most $maxArgs arguments, got ${args.size}")
        }
        return implementation(args)
    }
}

class FunctionArgumentException(message: String) : IllegalArgumentException(message)
class FunctionExecutionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
