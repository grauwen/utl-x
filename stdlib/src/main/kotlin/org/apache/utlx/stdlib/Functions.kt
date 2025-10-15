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
import org.apache.utlx.stdlib.xml.*
import org.apache.utlx.stdlib.logical.*
import org.apache.utlx.stdlib.url.*

/**
 * UTL-X Standard Library Function Registry
 * 
 * ENTERPRISE EDITION: 188+ functions - Industry-leading transformation library
 * Includes comprehensive XML/QName support for enterprise XML transformations
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
        
        // XML & QName functions
        registerXmlFunctions()
        
        // Logical functions
        registerLogicalFunctions()

        //Conversion functions
        registerConversionFunctions()

        //URL funcions
        registerURLFunctions() 
        
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
        register("substringBefore", ExtendedStringFunctions::substringBefore)
        register("substringAfter", ExtendedStringFunctions::substringAfter)
        register("substringBeforeLast", ExtendedStringFunctions::substringBeforeLast)
        register("substringAfterLast", ExtendedStringFunctions::substringAfterLast)
        register("pad", ExtendedStringFunctions::pad)
        register("padRight", ExtendedStringFunctions::padRight)
        register("normalizeSpace", ExtendedStringFunctions::normalizeSpace)
        register("repeat", ExtendedStringFunctions::repeat)
        
        // More string functions
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

        // CaseFunctions
        register("camelize", CaseFunctions::camelize)
        register("pascalCase", CaseFunctions::pascalCase)
        register("kebabCase", CaseFunctions::kebabCase)
        register("snakeCase", CaseFunctions::snakeCase)
        register("constantCase", CaseFunctions::constantCase)
        register("titleCase", CaseFunctions::titleCase)
        register("dotCase", CaseFunctions::dotCase)
        register("pathCase", CaseFunctions::pathCase)

         // Case conversion
        register("camelize", CaseConversionFunctions::camelize)
        register("snakeCase", CaseConversionFunctions::snakeCase)
        register("titleCase", CaseConversionFunctions::titleCase)
        register("uncamelize", CaseConversionFunctions::uncamelize)
 
        // Utilities
        register("truncate", CaseConversionFunctions::truncate)
        register("slugify", CaseConversionFunctions::slugify)
                 
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
        register("zip", ArrayFunctions::zip) //not related to gzip or gunzip, pure array operations
        
        // Zip/Unzip operations
        register("unzip", UnzipFunctions::unzip)
        register("unzipN", UnzipFunctions::unzipN)
        register("transpose", UnzipFunctions::transpose)
        register("zipWith", UnzipFunctions::zipWith)
        register("zipWithIndex", UnzipFunctions::zipWithIndex)
        
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

        // Critical array utilities
        register("compact", CriticalArrayFunctions::compact)
        register("findIndex", CriticalArrayFunctions::findIndex)
        register("findLastIndex", CriticalArrayFunctions::findLastIndex)
 
       // Advanced functional operations
       register("scan", CriticalArrayFunctions::scan)
       register("windowed", CriticalArrayFunctions::windowed)
       register("zipAll", CriticalArrayFunctions::zipAll)
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
        register("formatNumber", ExtendedMathFunctions::formatNumber)
        register("parseInt", ExtendedMathFunctions::parseInt)
        register("parseFloat", ExtendedMathFunctions::parseFloat)

        // Statistical functions
        register("median", StatisticalFunctions::median)
        register("mode", StatisticalFunctions::mode)
        register("stdDev", StatisticalFunctions::stdDev)
        register("variance", StatisticalFunctions::variance)
        register("percentile", StatisticalFunctions::percentile)
        register("quartiles", StatisticalFunctions::quartiles)
        register("iqr", StatisticalFunctions::iqr)
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
        register("compareDates", ExtendedDateFunctions::compareDates)
        register("validateDate", ExtendedDateFunctions::validateDate)
        
        // More date functions
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
        
        // Timezone functions
        register("convertTimezone", TimezoneFunctions::convertTimezone)
        register("getTimezoneName", TimezoneFunctions::getTimezoneName)
        register("getTimezoneOffsetSeconds", TimezoneFunctions::getTimezoneOffsetSeconds)
        register("getTimezoneOffsetHours", TimezoneFunctions::getTimezoneOffsetHours)
        register("parseDateTimeWithTimezone", TimezoneFunctions::parseDateTimeWithTimezone)
        register("formatDateTimeInTimezone", TimezoneFunctions::formatDateTimeInTimezone)
        register("isValidTimezone", TimezoneFunctions::isValidTimezone)
        register("toUTC", TimezoneFunctions::toUTC)
        register("fromUTC", TimezoneFunctions::fromUTC)
        
        // Rich date arithmetic
        register("addWeeks", RichDateFunctions::addWeeks)
        register("addQuarters", RichDateFunctions::addQuarters)
        register("diffWeeks", RichDateFunctions::diffWeeks)
        register("diffMonths", RichDateFunctions::diffMonths)
        register("diffYears", RichDateFunctions::diffYears)
        
        // Start/end of period
        register("startOfDay", RichDateFunctions::startOfDay)
        register("endOfDay", RichDateFunctions::endOfDay)
        register("startOfWeek", RichDateFunctions::startOfWeek)
        register("endOfWeek", RichDateFunctions::endOfWeek)
        register("startOfMonth", RichDateFunctions::startOfMonth)
        register("endOfMonth", RichDateFunctions::endOfMonth)
        register("startOfYear", RichDateFunctions::startOfYear)
        register("endOfYear", RichDateFunctions::endOfYear)
        register("startOfQuarter", RichDateFunctions::startOfQuarter)
        register("endOfQuarter", RichDateFunctions::endOfQuarter)
        
        // Date information
        register("dayOfWeek", RichDateFunctions::dayOfWeek)
        register("dayOfWeekName", RichDateFunctions::dayOfWeekName)
        register("dayOfYear", RichDateFunctions::dayOfYear)
        register("weekOfYear", RichDateFunctions::weekOfYear)
        register("quarter", RichDateFunctions::quarter)
        register("monthName", RichDateFunctions::monthName)
        register("isLeapYear", RichDateFunctions::isLeapYearFunc)
        register("daysInMonth", RichDateFunctions::daysInMonth)
        register("daysInYear", RichDateFunctions::daysInYear)
        
        // Date comparisons
        register("isBefore", RichDateFunctions::isBefore)
        register("isAfter", RichDateFunctions::isAfter)
        register("isSameDay", RichDateFunctions::isSameDay)
        register("isBetween", RichDateFunctions::isBetween)
        register("isToday", RichDateFunctions::isToday)
        register("isWeekend", RichDateFunctions::isWeekend)
        register("isWeekday", RichDateFunctions::isWeekday)
        
        // Age calculation
        register("age", RichDateFunctions::age)
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
        //added
        register("invert", CriticalObjectFunctions::invert)
        register("deepMerge", CriticalObjectFunctions::deepMerge)
        register("deepMergeAll", CriticalObjectFunctions::deepMergeAll)
        register("deepClone", CriticalObjectFunctions::deepClone)
        register("getPath", CriticalObjectFunctions::getPath)
        register("setPath", CriticalObjectFunctions::setPath)
    }
    
    private fun registerEncodingFunctions() {
        register("base64Encode", EncodingFunctions::base64Encode)
        register("base64Decode", EncodingFunctions::base64Decode)
        register("urlEncode", EncodingFunctions::urlEncode)
        register("urlDecode", EncodingFunctions::urlDecode)
        register("hexEncode", EncodingFunctions::hexEncode)
        register("hexDecode", EncodingFunctions::hexDecode)
       // added
        register("md5", EncodingFunctions::md5)
        register("sha256", EncodingFunctions::sha256)
        register("sha512", EncodingFunctions::sha512)
        register("sha1", EncodingFunctions::sha1)
        register("hash", EncodingFunctions::hash)
        register("hmac", EncodingFunctions::hmac)
        
    }
    
    private fun registerXmlFunctions() {
        // QName functions
        register("localName", QNameFunctions::localName)
        register("namespaceUri", QNameFunctions::namespaceUri)
        register("qualifiedName", QNameFunctions::qualifiedName)
        register("namespacePrefix", QNameFunctions::namespacePrefix)
        register("resolveQname", QNameFunctions::resolveQName)
        register("createQname", QNameFunctions::createQName)
        register("hasNamespace", QNameFunctions::hasNamespace)
        register("getNamespaces", QNameFunctions::getNamespaces)
        register("matchesQname", QNameFunctions::matchesQName)
        
        // XML utility functions
        register("nodeType", XmlUtilityFunctions::nodeType)
        register("textContent", XmlUtilityFunctions::textContent)
        register("attributes", XmlUtilityFunctions::attributes)
        register("attribute", XmlUtilityFunctions::attribute)
        register("hasAttribute", XmlUtilityFunctions::hasAttribute)
        register("childCount", XmlUtilityFunctions::childCount)
        register("childNames", XmlUtilityFunctions::childNames)
        register("parent", XmlUtilityFunctions::parent)
        register("elementPath", XmlUtilityFunctions::elementPath)
        register("isEmptyElement", XmlUtilityFunctions::isEmptyElement)
        register("xmlEscape", XmlUtilityFunctions::xmlEscape)
        register("xmlUnescape", XmlUtilityFunctions::xmlUnescape)
    }
    
    private fun registerLogicalFunctions() {
        // Basic logical operations
        register("not", LogicalFunctions::not)
        register("xor", LogicalFunctions::xor)
        register("and", LogicalFunctions::and)
        register("or", LogicalFunctions::or)
        
        // Advanced logical gates
        register("nand", LogicalFunctions::nand)
        register("nor", LogicalFunctions::nor)
        register("xnor", LogicalFunctions::xnor)
        register("implies", LogicalFunctions::implies)
        
        // Array boolean operations
        register("all", LogicalFunctions::all)
        register("any", LogicalFunctions::any)
        register("none", LogicalFunctions::none)
    }
    
    private fun register(name: String, impl: (List<UDM>) -> UDM) {
        functions[name] = UTLXFunction(name, impl)
    }

    private fun registerConversionFunctions() {
    // CRITICAL: Number parsing (used in examples!)
         register("parseNumber", ConversionFunctions::parseNumber)
         register("toNumber", ConversionFunctions::toNumber)
         register("parseInt", ConversionFunctions::parseInt)
         register("parseFloat", ConversionFunctions::parseFloat)
         register("parseDouble", ConversionFunctions::parseDouble)  
         
         // String conversion
         register("toString", ConversionFunctions::toString)
       
         // Boolean conversion
         register("parseBoolean", ConversionFunctions::parseBoolean)
         register("toBoolean", ConversionFunctions::toBoolean)
       
         // Date conversion
         register("parseDate", ConversionFunctions::parseDate)
       
         // Collection conversion
         register("toArray", ConversionFunctions::toArray)
         register("toObject", ConversionFunctions::toObject)
       
         // Safe conversion with defaults
         register("numberOrDefault", ConversionFunctions::numberOrDefault)
         register("stringOrDefault", ConversionFunctions::stringOrDefault)
     }

    // Add new registration method
    private fun registerURLFunctions() {
        // URL parsing
        register("parseURL", URLFunctions::parseURL)
        register("getProtocol", URLFunctions::getProtocol)
        register("getHost", URLFunctions::getHost)
        register("getPort", URLFunctions::getPort)
        register("getPath", URLFunctions::getPath)
        register("getQuery", URLFunctions::getQuery)
        register("getFragment", URLFunctions::getFragment)
        register("getQueryParams", URLFunctions::getQueryParams)
        register("getBaseURL", URLFunctions::getBaseURL)
        
        // Query string handling
        register("parseQueryString", URLFunctions::parseQueryString)
        register("buildQueryString", URLFunctions::buildQueryString)
        
        // URL construction
        register("buildURL", URLFunctions::buildURL)
        
        // URL encoding/decoding
        // NOTE: If these already exist in encoding module, skip these registrations
        register("urlEncode", URLFunctions::urlEncode)
        register("urlDecode", URLFunctions::urlDecode)
        
        // URL manipulation
        register("addQueryParam", URLFunctions::addQueryParam)
        register("removeQueryParam", URLFunctions::removeQueryParam)
        register("isValidURL", URLFunctions::isValidURL)
    }
    
    /**
     * Registration in Functions.kt:
     * 
     * Add these to a new registerConversionFunctions() method:
     * 
     * private fun registerConversionFunctions() {
     *     // CRITICAL: Number parsing (used in examples!)
     *     register("parseNumber", ConversionFunctions::parseNumber)
     *     register("toNumber", ConversionFunctions::toNumber)
     *     register("parseInt", ConversionFunctions::parseInt)
     *     register("parseFloat", ConversionFunctions::parseFloat)
     *     register("parseDouble", ConversionFunctions::parseDouble)
     *     
     *     // String conversion
     *     register("toString", ConversionFunctions::toString)
     *     
     *     // Boolean conversion
     *     register("parseBoolean", ConversionFunctions::parseBoolean)
     *     register("toBoolean", ConversionFunctions::toBoolean)
     *     
     *     // Date conversion
     *     register("parseDate", ConversionFunctions::parseDate)
     *     
     *     // Collection conversion
     *     register("toArray", ConversionFunctions::toArray)
     *     register("toObject", ConversionFunctions::toObject)
     *     
     *     // Safe conversion with defaults
     *     register("numberOrDefault", ConversionFunctions::numberOrDefault)
     *     register("stringOrDefault", ConversionFunctions::stringOrDefault)
     * }
     * 
     * Then call this in the init block:
     * init {
     *     registerConversionFunctions()  // ADD THIS!
     *     registerStringFunctions()
     *     registerArrayFunctions()
     *     // ... rest
     * }
     */
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
