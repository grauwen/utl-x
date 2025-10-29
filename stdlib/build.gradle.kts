// stdlib/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
}

group = "org.apache.utlx"
version = "0.9.0-beta"

// Repository management is handled in settings.gradle.kts

dependencies {
    // Core module dependency
    implementation(project(":modules:core"))

    // Format module dependencies (for schema serialization functions)
    implementation(project(":formats:avro"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:protobuf"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:yaml"))

    // Kotlin standard library
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Date/time support
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.assertj:assertj-core:3.24.2")

    // Apache XML Security for C14N
    implementation("org.apache.santuario:xmlsec:3.0.3")
    
    // JSON/YAML for function registry generation
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// Function Registry Generation Task
tasks.register<JavaExec>("generateFunctionRegistry") {
    group = "build"
    description = "Generate UTL-X function registry for external tools"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.apache.utlx.stdlib.buildtools.FunctionRegistryGeneratorKt")
    
    val outputDir = file("$buildDir/generated/function-registry")
    args(outputDir.absolutePath)
    
    outputs.dir(outputDir)
    dependsOn(tasks.compileKotlin)
    
    doFirst {
        println("Generating UTL-X function registry...")
    }
}

// Auto-generate registry after compilation
tasks.jar {
    dependsOn("generateFunctionRegistry")

    from("$buildDir/generated/function-registry") {
        into("function-registry")
    }
}
