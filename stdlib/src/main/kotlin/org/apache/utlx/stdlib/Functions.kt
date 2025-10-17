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
import org.apache.utlx.stdlib.util.*
import org.apache.utlx.stdlib.binary.*
import org.apache.utlx.stdlib.serialization.*
import org.apache.utlx.stdlib.finance.*
import org.apache.utlx.stdlib.geo.*
import org.apache.utlx.stdlib.jwt.*
import org.apache.utlx.stdlib.json.*
import org.apache.utlx.stdlib.jws.*
import org.apache.utlx.stdlib.csv.*
import org.apache.utlx.stdlib.yaml.*



/**
 * UTL-X Standard Library Function Registry
 * 
 * ENTERPRISE EDITION: 188+ functions - Industry-leading transformation library
 * Includes comprehensive XML/QName support for enterprise XML transformations
 */
object StandardLibrary {
    
    private val functions = mutableMapOf<String, UTLXFunction>()

    private val registry = mutableMapOf<String, (List<UDM>) -> UDM>()
    
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

        // Canonicalization of XML (often abbreviated as XML C14N)
        registerC14NFunctions()
        
        // Logical functions
        registerLogicalFunctions()

        //Conversion functions
        registerConversionFunctions()

        //URL funcions
        registerURLFunctions() 

        // JWT functions
        registerJWTFunctions()

        // JSON Canonicalization functions (RFC 8785)
        registerJSONFunctions()
        
        // JWS (JSON Web Signature) functions
        registerJWSFunctions()

        // Tree functions
        registerTreeFunctions() 

        // CoercionFunctions
        registerCoercionFunctions()     

        //TimerFunctions
        registerTimerFunctions()    

        // Value functions
        registerValueFunctions()     

        //Diff functions
        registerDiffFunctions()    

        //Mime functions
        registerMimeFunctions()      

        //MultiPart functions
        registerMultipartFunctions()

        //Binary functions
        registerBinaryFunctions()

        //Debug functions
        registerDebugFunctions()

        //Serialization
        registerSerializationFunctions()

        // Collection JOIN operations
        registerJoinFunctions()  

        // Runtime/system information
        registerRuntimeFunctions()   

        // UUID v7 support
        registerUUIDFunctions()        

        //Compression support alike gunzip
        registerCompressionFunctions()

        // Advanced math functions
        registerAdvancedMathFunctions()
    
        // Enhanced object functions
        registerEnhancedObjectFunctions()
    
       // Character classification functions
       registerCharacterFunctions()
    
       // Enhanced array functions
       registerEnhancedArrayFunctions()
    
       // Financial functions
       registerFinancialFunctions()

       //geo
       registerGeospatialFunctions()
       
      //AdvancedRegex
       registerAdvancedRegexFunctions()
       
       // CSV functions
       registerCSVFunctions()
       
       // YAML functions
       registerYAMLFunctions()
   
    }

    private fun registerAdvancedRegexFunctions() {
        // Core regex analysis
        register("analyzeString", AdvancedRegexFunctions::analyzeString)
        // register("analyze-string", AdvancedRegexFunctions::analyzeString) // XSLT compatibility
        
        // Group extraction
        register("regexGroups", AdvancedRegexFunctions::regexGroups)
        // register("regex-groups", AdvancedRegexFunctions::regexGroups)
        register("regexNamedGroups", AdvancedRegexFunctions::regexNamedGroups)
        // register("regex-named-groups", AdvancedRegexFunctions::regexNamedGroups)
        
        // Advanced matching
        register("findAllMatches", AdvancedRegexFunctions::findAllMatches)
        // register("find-all-matches", AdvancedRegexFunctions::findAllMatches)
        register("splitWithMatches", AdvancedRegexFunctions::splitWithMatches)
        // register("split-with-matches", AdvancedRegexFunctions::splitWithMatches)
        
        // Validation and replacement
        register("matchesWhole", AdvancedRegexFunctions::matchesWhole)
        // register("matches-whole", AdvancedRegexFunctions::matchesWhole)
        register("replaceWithFunction", AdvancedRegexFunctions::replaceWithFunction)
        //register("replace-with-function", AdvancedRegexFunctions::replaceWithFunction)
    }
    
    private fun registerGeospatialFunctions() {
        // Distance calculations
        register("distance", GeospatialFunctions::distance)
        register("geoDistance", GeospatialFunctions::distance) // Alias
        // register("geo-distance", GeospatialFunctions::distance)
        
        register("bearing", GeospatialFunctions::bearing)
        register("geoBearing", GeospatialFunctions::bearing)
        // register("geo-bearing", GeospatialFunctions::bearing)
        
        // Geofencing
        register("isPointInCircle", GeospatialFunctions::isPointInCircle)
        // register("is-point-in-circle", GeospatialFunctions::isPointInCircle)
        register("inCircle", GeospatialFunctions::isPointInCircle) // Short alias
        
        register("isPointInPolygon", GeospatialFunctions::isPointInPolygon)
        // register("is-point-in-polygon", GeospatialFunctions::isPointInPolygon)
        register("inPolygon", GeospatialFunctions::isPointInPolygon) // Short alias
        
        // Utilities
        register("midpoint", GeospatialFunctions::midpoint)
        register("geoMidpoint", GeospatialFunctions::midpoint)
        // register("geo-midpoint", GeospatialFunctions::midpoint)
        
        register("destinationPoint", GeospatialFunctions::destinationPoint)
        // register("destination-point", GeospatialFunctions::destinationPoint)
        register("geoDestination", GeospatialFunctions::destinationPoint)
        
        register("boundingBox", GeospatialFunctions::boundingBox)
        // register("bounding-box", GeospatialFunctions::boundingBox)
        register("geoBounds", GeospatialFunctions::boundingBox)
        
        // Validation
        register("isValidCoordinates", GeospatialFunctions::isValidCoordinates)
        // register("is-valid-coordinates", GeospatialFunctions::isValidCoordinates) 
        register("validCoords", GeospatialFunctions::isValidCoordinates) // Short alias
    }
     
    private fun registerCoreFunctions() {
        // CoreFunctions already use List<UDM> signature, so these work directly
        register("if", CoreFunctions::ifThenElse)
        register("coalesce", CoreFunctions::coalesce)
        // register("generate-uuid", CoreFunctions::generateUuid)//alias
        register("generateUuid", CoreFunctions::generateUuid)
        register("default", CoreFunctions::default)
        register("isEmpty", CoreFunctions::isEmpty)
        register("isNotEmpty", CoreFunctions::isNotEmpty)
        register("contains", CoreFunctions::contains)
        register("concat", CoreFunctions::concat)
     
    }

    private fun registerDebugFunctions() {
        // Configuration
        register("setLogLevel", DebugFunctions::setLogLevel)
        register("setConsoleLogging", DebugFunctions::setConsoleLogging)
        
        // Basic logging
        register("log", DebugFunctions::log)
        register("trace", DebugFunctions::trace)
        register("debug", DebugFunctions::debug)
        register("info", DebugFunctions::info)
        register("warn", DebugFunctions::warn)
        register("error", DebugFunctions::error)
        
        // Inspection
        register("logType", DebugFunctions::logType)
        register("logSize", DebugFunctions::logSize)
        register("logPretty", DebugFunctions::logPretty)
        
        // Timing
        register("startDebugTimer", DebugFunctions::startTimer)
        register("endDebugTimer", DebugFunctions::endTimer)
        
        // Log management
        register("getLogs", DebugFunctions::getLogs)
        register("clearLogs", DebugFunctions::clearLogs)
        register("logCount", DebugFunctions::logCount)
        
        // Assertions
        register("assert", DebugFunctions::assert)
        register("assertEqual", DebugFunctions::assertEqual)
    }
    
    private fun registerStringFunctions() {
        // Basic string operations
        register("upper", StringFunctions::upper)
        register("lower", StringFunctions::lower)
        register("trim", StringFunctions::trim)
        register("substring", StringFunctions::substring)
        //register("concat", StringFunctions::concat) // concat now resides in coreFunctions to handel concat of string, array, obejct, binary etc
        register("split", StringFunctions::split)
        register("joinBy", StringFunctions::join)  //join used in SQL style join, rename to joinBy according to DW style
        register("replace", StringFunctions::replace)
        //register("contains", StringFunctions::contains)//contains function moved to UDM layer so now it can work with Strings and Arrays, with convention: objects check keys only
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
        //register("isEmpty", MoreStringFunctions::isEmpty)// now generic UDM isEmpty check for all types: strings, arrays, objects, binary, numbers, datetime
        register("isBlank", MoreStringFunctions::isBlank)
        register("charAt", MoreStringFunctions::charAt)
        register("charCodeAt", MoreStringFunctions::charCodeAt)
        register("fromCharCode", MoreStringFunctions::fromCharCode)
        register("capitalize", MoreStringFunctions::capitalize)
        register("titleCase", MoreStringFunctions::titleCase)

        // Case conversion functions
        register("camelize", CaseFunctions::camelize)
        register("pascalCase", CaseFunctions::pascalCase)
        register("kebabCase", CaseFunctions::kebabCase)
        register("snakeCase", CaseFunctions::snakeCase)
        register("constantCase", CaseFunctions::constantCase)
        register("titleCase", CaseFunctions::titleCase)
        register("dotCase", CaseFunctions::dotCase)
        register("pathCase", CaseFunctions::pathCase)
        
        // Additional case conversion functions
        register("uncamelize", CaseConversionFunctions::uncamelize)
        register("slugify", CaseConversionFunctions::slugify)

        // Pluralization functions
        register("pluralize", PluralizationFunctions::pluralize)
        register("singularize", PluralizationFunctions::singularize)
        register("pluralizeWithCount", PluralizationFunctions::pluralizeWithCount)
        register("isPlural", PluralizationFunctions::isPlural)
        register("isSingular", PluralizationFunctions::isSingular)
        register("formatPlural", PluralizationFunctions::formatPlural)
                 
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
        register("head", ArrayFunctions::first) //alias for first
        register("last", ArrayFunctions::last)
        register("take", ArrayFunctions::take)
        register("drop", ArrayFunctions::drop)
        register("unique", ArrayFunctions::unique)
        register("zip", ArrayFunctions::zip) //not related to gzip or gunzip, pure array operations
        
        // Additional array functions
        register("size", ArrayFunctions::size)
        register("get", ArrayFunctions::get)
        register("tail", ArrayFunctions::tail)
        register("distinct", ArrayFunctions::distinct)
        register("distinctBy", ArrayFunctions::distinctBy)
        register("union", ArrayFunctions::union)
        register("intersect", ArrayFunctions::intersect)
        register("difference", ArrayFunctions::difference)
        register("symmetricDifference", ArrayFunctions::symmetricDifference)
        register("flatMap", ArrayFunctions::flatMap)
        register("flattenDeep", ArrayFunctions::flattenDeep)
        register("chunk", ArrayFunctions::chunk)
        register("joinToString", ArrayFunctions::joinToString)
        
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
        register("containsKey", ObjectFunctions::containsKey)
        register("containsValue", ObjectFunctions::containsValue)
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
       // basic crypto
        register("md5", EncodingFunctions::md5)
        register("sha256", EncodingFunctions::sha256)
        register("sha512", EncodingFunctions::sha512)
        register("sha1", EncodingFunctions::sha1)
        register("hash", EncodingFunctions::hash)
        register("hmac", EncodingFunctions::hmac)

        //AdvancedCryptoFunctions
         
        // HMAC functions
        register("hmacMD5", AdvancedCryptoFunctions::hmacMD5)
        register("hmacSHA1", AdvancedCryptoFunctions::hmacSHA1)
        register("hmacSHA256", AdvancedCryptoFunctions::hmacSHA256)
        register("hmacSHA384", AdvancedCryptoFunctions::hmacSHA384)
        register("hmacSHA512", AdvancedCryptoFunctions::hmacSHA512)
        register("hmacBase64", AdvancedCryptoFunctions::hmacBase64)
        
        // Encryption
        register("encryptAES", AdvancedCryptoFunctions::encryptAES)
        register("decryptAES", AdvancedCryptoFunctions::decryptAES)
        register("encryptAES256", AdvancedCryptoFunctions::encryptAES256)
        register("decryptAES256", AdvancedCryptoFunctions::decryptAES256)
        
        // Additional hashes
        register("sha224", AdvancedCryptoFunctions::sha224)
        register("sha384", AdvancedCryptoFunctions::sha384)
        register("sha3_256", AdvancedCryptoFunctions::sha3_256)
        register("sha3_512", AdvancedCryptoFunctions::sha3_512)
        
        // Utilities
        register("generateIV", AdvancedCryptoFunctions::generateIV)
        register("generateKey", AdvancedCryptoFunctions::generateKey)
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
        
        // XML Serialization Options functions
        register("enforceNamespacePrefixes", XMLSerializationOptionsFunctions::enforceNamespacePrefixes)
        register("formatEmptyElements", XMLSerializationOptionsFunctions::formatEmptyElements)
        register("addNamespaceDeclarations", XMLSerializationOptionsFunctions::addNamespaceDeclarations)
        register("createSOAPEnvelope", XMLSerializationOptionsFunctions::createSOAPEnvelope)
        
        // CDATA functions
        register("createCDATA", CDATAFunctions::createCDATA)
        register("isCDATA", CDATAFunctions::isCDATA)
        register("extractCDATA", CDATAFunctions::extractCDATA)
        register("unwrapCDATA", CDATAFunctions::unwrapCDATA)
        register("shouldUseCDATA", CDATAFunctions::shouldUseCDATA)
        register("wrapIfNeeded", CDATAFunctions::wrapIfNeeded)
        register("escapeXML", CDATAFunctions::escapeXML)
        register("unescapeXML", CDATAFunctions::unescapeXML)

    }

    private fun registerC14NFunctions() {
        // Canonicalization of XML (often abbreviated as XML C14N)
        // C14N 1.0
        register("c14n", XMLCanonicalizationFunctions::c14n)
        register("c14nWithComments", XMLCanonicalizationFunctions::c14nWithComments)
        
        // Exclusive C14N
        register("excC14n", XMLCanonicalizationFunctions::excC14n)
        register("excC14nWithComments", XMLCanonicalizationFunctions::excC14nWithComments)
        
        // C14N 1.1
        register("c14n11", XMLCanonicalizationFunctions::c14n11)
        register("c14n11WithComments", XMLCanonicalizationFunctions::c14n11WithComments)
        
        // Physical C14N
        register("c14nPhysical", XMLCanonicalizationFunctions::c14nPhysical)
        
        // Generic
        register("canonicalizeWithAlgorithm", XMLCanonicalizationFunctions::canonicalizeWithAlgorithm)
        
        // XPath subset
        register("c14nSubset", XMLCanonicalizationFunctions::c14nSubset)
        
        // Hash and comparison
        register("c14nHash", XMLCanonicalizationFunctions::c14nHash)
        register("c14nEquals", XMLCanonicalizationFunctions::c14nEquals)
        register("c14nFingerprint", XMLCanonicalizationFunctions::c14nFingerprint)
        
        // Digital signatures
        register("prepareForSignature", XMLCanonicalizationFunctions::prepareForSignature)
        register("validateDigest", XMLCanonicalizationFunctions::validateDigest)
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
        
        // URL manipulation
        register("addQueryParam", URLFunctions::addQueryParam)
        register("removeQueryParam", URLFunctions::removeQueryParam)
        register("isValidURL", URLFunctions::isValidURL)
    }

    private fun registerJWTFunctions() {
        // JWT decoding and parsing
        register("decodeJWT", JWTFunctions::decodeJWT)
        register("getJWTClaims", JWTFunctions::getJWTClaims)
        register("getJWTClaim", JWTFunctions::getJWTClaim)
        
        // JWT validation
        register("isJWTExpired", JWTFunctions::isJWTExpired)
        
        // Standard JWT claims
        register("getJWTSubject", JWTFunctions::getJWTSubject)
        register("getJWTIssuer", JWTFunctions::getJWTIssuer)
        register("getJWTAudience", JWTFunctions::getJWTAudience)
        
        // JWT verification and creation functions
        // moved them out of stlib to stdlib-security to keep the footprint of the stloib small
        //register("verifyJWT", JWTVerification::verifyJWT)
        //register("verifyJWTWithJWKS", JWTVerification::verifyJWTWithJWKS)
        //register("createJWT", JWTVerification::createJWT)
        //register("validateJWTStructure", JWTVerification::validateJWTStructure)
    }

      private fun registerTreeFunctions() {
          register("treeMap", TreeFunctions::treeMap)
          register("treeFilter", TreeFunctions::treeFilter)
          register("treeFlatten", TreeFunctions::treeFlatten)
          register("treeDepth", TreeFunctions::treeDepth)
          register("treePaths", TreeFunctions::treePaths)
          register("treeFind", TreeFunctions::treeFind)
      }

     private fun registerCoercionFunctions() {
          register("coerce", CoercionFunctions::coerce)
          register("tryCoerce", CoercionFunctions::tryCoerce)
          register("canCoerce", CoercionFunctions::canCoerce)
          register("coerceAll", CoercionFunctions::coerceAll)
          register("smartCoerce", CoercionFunctions::smartCoerce)
     }

    private fun registerTimerFunctions() {
          register("timerStart", TimerFunctions::timerStart)
          register("timerStop", TimerFunctions::timerStop)
          register("timerCheck", TimerFunctions::timerCheck)
          register("timerReset", TimerFunctions::timerReset)
          register("timerStats", TimerFunctions::timerStats)
          register("timerList", TimerFunctions::timerList)
          register("timerClear", TimerFunctions::timerClear)
          register("timestamp", TimerFunctions::timestamp)
          register("measure", TimerFunctions::measure)
     }

      private fun registerValueFunctions() {
          register("update", ValueFunctions::update)
          register("mask", ValueFunctions::mask)
          register("pick", ValueFunctions::pick)
          register("omit", ValueFunctions::omit)
          register("defaultValue", ValueFunctions::defaultValue)
      }
     
      private fun registerDiffFunctions() {
          register("diff", DiffFunctions::diff)
          register("deepEquals", DiffFunctions::deepEquals)
          register("patch", DiffFunctions::patch)
     }
     
      private fun registerMimeFunctions() {
         register("getMimeType", MimeFunctions::getMimeType)
         register("getExtension", MimeFunctions::getExtension)
         register("parseContentType", MimeFunctions::parseContentType)
         register("buildContentType", MimeFunctions::buildContentType)
      }
      
      private fun registerMultipartFunctions() {
          register("parseBoundary", MultipartFunctions::parseBoundary)
          register("buildMultipart", MultipartFunctions::buildMultipart)
          register("generateBoundary", MultipartFunctions::generateBoundary)
          register("createPart", MultipartFunctions::createPart)
     }

     private fun registerBinaryFunctions() {
          // Binary creation
          register("toBinary", BinaryFunctions::toBinary)
          register("fromBytes", BinaryFunctions::fromBytes)
          register("fromBase64", BinaryFunctions::fromBase64)
          register("fromHex", BinaryFunctions::fromHex)
          
          // Binary conversion
          register("binaryToString", BinaryFunctions::binaryToString)
          register("toBytes", BinaryFunctions::toBytes)
          register("toBase64", BinaryFunctions::toBase64)
          register("toHex", BinaryFunctions::toHex)
          
          // Binary operations
          register("binaryLength", BinaryFunctions::binaryLength)
          register("binaryConcat", BinaryFunctions::binaryConcat)
          register("binarySlice", BinaryFunctions::binarySlice)
          register("binaryEquals", BinaryFunctions::binaryEquals)
          
          // Binary reading
          register("readInt16", BinaryFunctions::readInt16)
          register("readInt32", BinaryFunctions::readInt32)
          register("readInt64", BinaryFunctions::readInt64)
          register("readFloat", BinaryFunctions::readFloat)
          register("readDouble", BinaryFunctions::readDouble)
          register("readByte", BinaryFunctions::readByte)
          
          // Binary writing
          register("writeInt16", BinaryFunctions::writeInt16)
          register("writeInt32", BinaryFunctions::writeInt32)
          register("writeInt64", BinaryFunctions::writeInt64)
          register("writeFloat", BinaryFunctions::writeFloat)
          register("writeDouble", BinaryFunctions::writeDouble)
          register("writeByte", BinaryFunctions::writeByte)

          //bit operation
          register("bitwiseAnd", BinaryFunctions::bitwiseAnd)
          register("bitwiseOr", BinaryFunctions::bitwiseOr)
          register("bitwiseXor", BinaryFunctions::bitwiseXor)
          register("bitwiseNot", BinaryFunctions::bitwiseNot)
          register("shiftLeft", BinaryFunctions::shiftLeft)
          register("shiftRight", BinaryFunctions::shiftRight)

           // BINARY COMPARISON
          register("equalsBinary", BinaryFunctions::equals)
      }


    private fun  registerSerializationFunctions(){
        //
        register("parseJson", SerializationFunctions::parseJson)
        register("renderJson", SerializationFunctions::renderJson)
        register("parseXml", SerializationFunctions::parseXml)
        register("renderXml", SerializationFunctions::renderXml)
        register("parseYaml", SerializationFunctions::parseYaml)
        register("renderYaml", SerializationFunctions::renderYaml)
        register("parseCsv", SerializationFunctions::parseCsv)
        register("renderCsv", SerializationFunctions::renderCsv)
        register("parse", SerializationFunctions::parse)
        register("render", SerializationFunctions::render)

        // Aliases for compatibility with Tibco BW\
        register("tibco_parse", SerializationFunctions::parse)
        register("tibco_render", SerializationFunctions::render)
        
        // Pretty-Print functions
        register("prettyPrintJSON", PrettyPrintFunctions::prettyPrintJSON)
        register("udmToJSON", PrettyPrintFunctions::udmToJSON)
        register("compactJSON", PrettyPrintFunctions::compactJSON)
        register("prettyPrintXML", PrettyPrintFunctions::prettyPrintXML)
        register("udmToXML", PrettyPrintFunctions::udmToXML)
        register("compactXML", PrettyPrintFunctions::compactXML)
        register("prettyPrintYAML", PrettyPrintFunctions::prettyPrintYAML)
        register("udmToYAML", PrettyPrintFunctions::udmToYAML)
        register("prettyPrintCSV", PrettyPrintFunctions::prettyPrintCSV)
        register("compactCSV", PrettyPrintFunctions::compactCSV)
        register("prettyPrint", PrettyPrintFunctions::prettyPrint)
        register("prettyPrintFormat", PrettyPrintFunctions::prettyPrintFormat)
        register("debugPrint", PrettyPrintFunctions::debugPrint)
        register("debugPrintCompact", PrettyPrintFunctions::debugPrintCompact)
    }

/**
     * Register collection JOIN functions
     * SQL-style joins for combining arrays based on key matching
     */
    private fun registerJoinFunctions() {
        register("join", JoinFunctions::join)
        register("leftJoin", JoinFunctions::leftJoin)
        register("rightJoin", JoinFunctions::rightJoin)
        register("fullOuterJoin", JoinFunctions::fullOuterJoin)
        register("crossJoin", JoinFunctions::crossJoin)
        register("joinWith", JoinFunctions::joinWith)
    }
    
    /**
     * Register runtime and system information functions
     * Environment variables, system properties, platform info
     */
    private fun registerRuntimeFunctions() {
        // Environment variables
        register("env", RuntimeFunctions::env)
        register("envOrDefault", RuntimeFunctions::envOrDefault)
        register("envAll", RuntimeFunctions::envAll)
        register("hasEnv", RuntimeFunctions::hasEnv)
        
        // System properties
        register("systemProperty", RuntimeFunctions::systemProperty)
        register("systemPropertyOrDefault", RuntimeFunctions::systemPropertyOrDefault)
        register("systemPropertiesAll", RuntimeFunctions::systemPropertiesAll)
        
        // Version and platform
        register("version", RuntimeFunctions::version)
        register("platform", RuntimeFunctions::platform)
        register("osVersion", RuntimeFunctions::osVersion)
        register("osArch", RuntimeFunctions::osArch)
        register("javaVersion", RuntimeFunctions::javaVersion)
        
        // System resources
        register("availableProcessors", RuntimeFunctions::availableProcessors)
        register("memoryInfo", RuntimeFunctions::memoryInfo)
        
        // Directories
        register("currentDir", RuntimeFunctions::currentDir)
        register("homeDir", RuntimeFunctions::homeDir)
        register("tempDir", RuntimeFunctions::tempDir)
        
        // User info
        register("username", RuntimeFunctions::username)
        
        // Runtime info
        register("uptime", RuntimeFunctions::uptime)
        register("runtimeInfo", RuntimeFunctions::runtimeInfo)
        
        // Helpers
        register("isDebugMode", RuntimeFunctions::isDebugMode)
        register("environment", RuntimeFunctions::environment)
    }
    
    /**
     * Register UUID generation functions
     * UUID v4 (random) and UUID v7 (time-ordered)
     */
    private fun registerUUIDFunctions() {
        // UUID v7 generation (time-ordered, sortable)
        register("generateUuidV7", UUIDFunctions::generateUuidV7)
        register("generateUuidV7Batch", UUIDFunctions::generateUuidV7Batch)
        
        // UUID utilities
        register("extractTimestampFromUuidV7", UUIDFunctions::extractTimestampFromUuidV7)
        register("isUuidV7", UUIDFunctions::isUuidV7)
        register("getUuidVersion", UUIDFunctions::getUuidVersion)
        register("isValidUuid", UUIDFunctions::isValidUuid)
        
        // Alternative: also provide v4 explicitly
        register("generateUuidV4", UUIDFunctions::generateUuidV4) // might be a duplication
    }

    private fun registerCompressionFunctions() {
    // Gzip compression
    register("gzip", CompressionFunctions::gzip)
    register("gunzip", CompressionFunctions::gunzip)
    register("isGzipped", CompressionFunctions::isGzipped)
    
    // Deflate compression
    register("deflate", CompressionFunctions::deflate)
    register("inflate", CompressionFunctions::inflate)
    
    // Generic compression
    register("compress", CompressionFunctions::compress)
    register("decompress", CompressionFunctions::decompress)
    
    // Zip archive operations
    register("zipArchive", CompressionFunctions::zipArchive)
    register("unzipArchive", CompressionFunctions::unzipArchive)
    register("readZipEntry", CompressionFunctions::readZipEntry)
    register("listZipEntries", CompressionFunctions::listZipEntries)
    register("isZipArchive", CompressionFunctions::isZipArchive)
    
    // JAR file operations
    register("readJarEntry", CompressionFunctions::readJarEntry)
    register("listJarEntries", CompressionFunctions::listJarEntries)
    register("isJarFile", CompressionFunctions::isJarFile)
    register("readJarManifest", CompressionFunctions::readJarManifest)
}

    //******
/**
     * Register advanced mathematical functions
     * Trigonometric, logarithmic, hyperbolic, and angle conversions
     */
    private fun registerAdvancedMathFunctions() {
        // Trigonometric functions
        register("sin", AdvancedMathFunctions::sin)
        register("cos", AdvancedMathFunctions::cos)
        register("tan", AdvancedMathFunctions::tan)
        register("asin", AdvancedMathFunctions::asin)
        register("acos", AdvancedMathFunctions::acos)
        register("atan", AdvancedMathFunctions::atan)
        register("atan2", AdvancedMathFunctions::atan2)
        
        // Hyperbolic functions
        register("sinh", AdvancedMathFunctions::sinh)
        register("cosh", AdvancedMathFunctions::cosh)
        register("tanh", AdvancedMathFunctions::tanh)
        
        // Logarithmic functions
        register("ln", AdvancedMathFunctions::ln)
        register("log", AdvancedMathFunctions::log)
        register("log10", AdvancedMathFunctions::log10)
        register("log2", AdvancedMathFunctions::log2)
        register("exp", AdvancedMathFunctions::exp)
        
        // Angle conversion
        register("toRadians", AdvancedMathFunctions::toRadians)
        register("toDegrees", AdvancedMathFunctions::toDegrees)
        
        // Mathematical constants
        register("pi", AdvancedMathFunctions::pi)
        register("e", AdvancedMathFunctions::e)
        register("goldenRatio", AdvancedMathFunctions::goldenRatio)
    }
    
    /**
     * Register enhanced object functions
     * Advanced object introspection and manipulation
     */
    private fun registerEnhancedObjectFunctions() {
        register("divideBy", EnhancedObjectFunctions::divideBy)
        register("someEntry", EnhancedObjectFunctions::someEntry)
        register("everyEntry", EnhancedObjectFunctions::everyEntry)
        register("mapEntries", EnhancedObjectFunctions::mapEntries)
        register("filterEntries", EnhancedObjectFunctions::filterEntries)
        register("reduceEntries", EnhancedObjectFunctions::reduceEntries)
        register("countEntries", EnhancedObjectFunctions::countEntries)
        register("mapKeys", EnhancedObjectFunctions::mapKeys)
        register("mapValues", EnhancedObjectFunctions::mapValues)
    }
    
    /**
     * Register character classification functions
     * String character class tests for validation
     */
    private fun registerCharacterFunctions() {
        register("isAlpha", CharacterFunctions::isAlpha)
        register("isNumeric", CharacterFunctions::isNumeric)
        register("isAlphanumeric", CharacterFunctions::isAlphanumeric)
        register("isWhitespace", CharacterFunctions::isWhitespace)
        register("isUpperCase", CharacterFunctions::isUpperCase)
        register("isLowerCase", CharacterFunctions::isLowerCase)
        register("isPrintable", CharacterFunctions::isPrintable)
        register("isAscii", CharacterFunctions::isAscii)
        register("isHexadecimal", CharacterFunctions::isHexadecimal)
        register("hasAlpha", CharacterFunctions::hasAlpha)
        register("hasNumeric", CharacterFunctions::hasNumeric)
    }
    
    /**
     * Register enhanced array functions
     * Advanced array operations for functional programming
     */
    private fun registerEnhancedArrayFunctions() {
        register("partition", EnhancedArrayFunctions::partition)
        register("countBy", EnhancedArrayFunctions::countBy)
        register("sumBy", EnhancedArrayFunctions::sumBy)
        register("maxBy", EnhancedArrayFunctions::maxBy)
        register("minBy", EnhancedArrayFunctions::minBy)
        register("groupBy", EnhancedArrayFunctions::groupBy)
        register("distinctBy", EnhancedArrayFunctions::distinctBy)
        register("avgBy", EnhancedArrayFunctions::avgBy)
    }
    
    /**
     * Register financial functions
     * UNIQUE FEATURE: The only transformation language with built-in financial functions
     */
    private fun registerFinancialFunctions() {
        // Currency formatting
        register("formatCurrency", FinancialFunctions::formatCurrency)
        register("parseCurrency", FinancialFunctions::parseCurrency)
        
        // Rounding
        register("roundToDecimalPlaces", FinancialFunctions::roundToDecimalPlaces)
        register("roundToCents", FinancialFunctions::roundToCents)
        
        // Tax calculations
        register("calculateTax", FinancialFunctions::calculateTax)
        register("addTax", FinancialFunctions::addTax)
        register("removeTax", FinancialFunctions::removeTax)
        
        // Discount and percentage
        register("calculateDiscount", FinancialFunctions::calculateDiscount)
        register("percentageChange", FinancialFunctions::percentageChange)
        
        // Time value of money
        register("presentValue", FinancialFunctions::presentValue)
        register("futureValue", FinancialFunctions::futureValue)
        register("compoundInterest", FinancialFunctions::compoundInterest)
        register("simpleInterest", FinancialFunctions::simpleInterest)
        
        // Validation
        register("isValidCurrency", FinancialFunctions::isValidCurrency)
        register("getCurrencyDecimals", FinancialFunctions::getCurrencyDecimals)
        register("isValidAmount", FinancialFunctions::isValidAmount)
    }

    //******
    
    private fun register(name: String, impl: (List<UDM>) -> UDM) {
        functions[name] = UTLXFunction(name, impl)
    }

    /**
     * Looks up a function by name.
     */
    fun lookup(name: String): ((List<UDM>) -> UDM)? = functions[name]?.implementation

    /**
     * Returns all registered function names.
     */
    fun getAllFunctionNames(): Set<String> = functions.keys.toSet()
    
    /**
     * Returns the total number of registered functions.
     */
    fun getFunctionCount(): Int = functions.size
    
    /**
     * Register JSON Canonicalization functions
     * RFC 8785 - JSON Canonicalization Scheme for digital signatures and cryptographic operations
     */
    private fun registerJSONFunctions() {
        register("canonicalizeJSON", JSONCanonicalizationFunctions::canonicalizeJSON)
        register("jcs", JSONCanonicalizationFunctions::jcs)
        register("canonicalJSONHash", JSONCanonicalizationFunctions::canonicalJSONHash)
        register("jsonEquals", JSONCanonicalizationFunctions::jsonEquals)
        register("isCanonicalJSON", JSONCanonicalizationFunctions::isCanonicalJSON)
        register("canonicalJSONSize", JSONCanonicalizationFunctions::canonicalJSONSize)
    }
    
    /**
     * Register JWS (JSON Web Signature) functions
     * RFC 7515 - Decoding and inspection of JWS tokens (no signature verification)
     */
    private fun registerJWSFunctions() {
        register("decodeJWS", JWSBasicFunctions::decodeJWS)
        register("getJWSPayload", JWSBasicFunctions::getJWSPayload)
        register("getJWSHeader", JWSBasicFunctions::getJWSHeader)
        register("getJWSAlgorithm", JWSBasicFunctions::getJWSAlgorithm)
        register("getJWSKeyId", JWSBasicFunctions::getJWSKeyId)
        register("getJWSTokenType", JWSBasicFunctions::getJWSTokenType)
        register("isJWSFormat", JWSBasicFunctions::isJWSFormat)
        register("getJWSSigningInput", JWSBasicFunctions::getJWSSigningInput)
        register("getJWSInfo", JWSBasicFunctions::getJWSInfo)
    }
    
    /**
     * Register CSV Functions
     * Advanced CSV data manipulation and analysis functions
     */
    private fun registerCSVFunctions() {
        // Structure access functions
        register("csvRows", CSVFunctions::csvRows)
        register("csvColumns", CSVFunctions::csvColumns)
        register("csvColumn", CSVFunctions::csvColumn)
        register("csvRow", CSVFunctions::csvRow)
        register("csvCell", CSVFunctions::csvCell)
        
        // Transformation functions
        register("csvTranspose", CSVFunctions::csvTranspose)
        
        // Filter and sort functions
        register("csvFilter", CSVFunctions::csvFilter)
        register("csvSort", CSVFunctions::csvSort)
        
        // Column operations
        register("csvAddColumn", CSVFunctions::csvAddColumn)
        register("csvRemoveColumns", CSVFunctions::csvRemoveColumns)
        register("csvSelectColumns", CSVFunctions::csvSelectColumns)
        
        // Aggregation functions
        register("csvSummarize", CSVFunctions::csvSummarize)
    }
    
    /**
     * Register YAML Functions
     * YAML-specific operations for transformation scenarios
     */
    private fun registerYAMLFunctions() {
        // Multi-document operations
        register("yamlSplitDocuments", YAMLFunctions::yamlSplitDocuments)
        register("yamlMergeDocuments", YAMLFunctions::yamlMergeDocuments)
        register("yamlGetDocument", YAMLFunctions::yamlGetDocument)
        
        // Path operations
        register("yamlPath", YAMLFunctions::yamlPath)
        register("yamlSet", YAMLFunctions::yamlSet)
        register("yamlDelete", YAMLFunctions::yamlDelete)
        register("yamlExists", YAMLFunctions::yamlExists)
        
        // Dynamic key operations
        register("yamlKeys", YAMLFunctions::yamlKeys)
        register("yamlValues", YAMLFunctions::yamlValues)
        register("yamlEntries", YAMLFunctions::yamlEntries)
        register("yamlFilterByKeyPattern", YAMLFunctions::yamlFilterByKeyPattern)
        register("yamlSelectKeys", YAMLFunctions::yamlSelectKeys)
        register("yamlOmitKeys", YAMLFunctions::yamlOmitKeys)
        register("yamlFromEntries", YAMLFunctions::yamlFromEntries)
        
        // Deep merge operations
        register("yamlMerge", YAMLFunctions::yamlMerge)
        register("yamlMergeAll", YAMLFunctions::yamlMergeAll)
        
        // Structural query operations
        register("yamlFindByField", YAMLFunctions::yamlFindByField)
        register("yamlFindObjectsWithField", YAMLFunctions::yamlFindObjectsWithField)
        
        // Formatting and validation
        register("yamlSort", YAMLFunctions::yamlSort)
        register("yamlValidate", YAMLFunctions::yamlValidate)
        register("yamlValidateKeyPattern", YAMLFunctions::yamlValidateKeyPattern)
        register("yamlHasRequiredFields", YAMLFunctions::yamlHasRequiredFields)
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
