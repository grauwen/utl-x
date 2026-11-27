import groovy.json.JsonOutput
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":modules:core"))
    implementation("org.apache.poi:poi-ooxml:6.0.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

kotlin {
    jvmToolchain(17)
}

val nativeMetadataDirectory = layout.buildDirectory.dir("generated/native-image")
val schemaArchives = configurations.runtimeClasspath.map { classpath ->
    classpath.filter { it.name.startsWith("poi-ooxml-lite-") }
}

// XMLBeans instantiates generated schema implementations reflectively. Register the pinned schema
// dependency's constructors and enum tables, including worksheet features absent from our fixtures.
val generateXlsxNativeMetadata by tasks.registering {
    inputs.files(schemaArchives)
    outputs.dir(nativeMetadataDirectory)
    doLast {
        val archives = schemaArchives.get().files
        check(archives.size == 1) { "Expected exactly one POI OOXML schema archive." }
        val registrations = ZipFile(archives.single()).use { archive ->
            val classes = archive.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toSet()
            classes.mapNotNull { name ->
                val className = name.removeSuffix(".class").replace('/', '.')
                when {
                    name.contains("/impl/") && name.endsWith("Impl.class") ->
                        mapOf("name" to className, "allDeclaredConstructors" to true)
                    name.endsWith("\$Enum.class") && name.removeSuffix("\$Enum.class") + ".class" in classes ->
                        mapOf("name" to className, "fields" to listOf(mapOf("name" to "table")))
                    else -> null
                }
            }
        }
        val holders = listOf("sXMLCONFIG", "sXMLLANG", "sXMLSCHEMA", "sXMLTOOLS").map { system ->
            mapOf(
                "name" to "org.apache.xmlbeans.metadata.system.$system.TypeSystemHolder",
                "fields" to listOf(mapOf("name" to "typeSystem"))
            )
        }
        val directory = nativeMetadataDirectory.get().asFile
            .resolve("META-INF/native-image/com.glomidco.utlx/formats-xlsx")
        directory.mkdirs()
        val metadata = (registrations + holders).sortedBy { it["name"].toString() }
        directory.resolve("reflect-config.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
    }
}

sourceSets.main {
    resources.srcDir(nativeMetadataDirectory)
}

tasks.processResources {
    dependsOn(generateXlsxNativeMetadata)
}
