plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
    id("com.google.protobuf")
    application
}

group = "org.apache.utlx"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Internal dependencies
    implementation(project(":modules:core"))
    implementation(project(":modules:cli"))  // For TransformationService
    implementation(project(":modules:analysis"))

    // Format parsers/serializers
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    implementation(project(":formats:yaml"))
    implementation(project(":formats:xsd"))
    implementation(project(":formats:jsch"))
    implementation(project(":formats:avro"))
    implementation(project(":formats:protobuf"))
    implementation(project(":formats:odata"))
    implementation(project(":formats:osch"))
    implementation(project(":formats:tsch"))

    // Schema module
    implementation(project(":schema"))

    // Standard library
    implementation(project(":stdlib"))

    // Kotlin stdlib
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // Protobuf (for stdio-proto and grpc transport modes)
    implementation("com.google.protobuf:protobuf-java:3.25.3")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3")

    // gRPC (for grpc transport mode)
    implementation("io.grpc:grpc-protobuf:1.60.1")
    implementation("io.grpc:grpc-stub:1.60.1")
    implementation("io.grpc:grpc-netty-shaded:1.60.1")
    // Required for generated gRPC stubs at compile time
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Jackson for YAML/JSON config and serialization
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Ktor for health endpoint
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-jackson:2.3.7")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.ktor:ktor-server-tests:2.3.7")
    testImplementation("io.ktor:ktor-server-test-host:2.3.7")
    testImplementation("io.grpc:grpc-testing:1.60.1")
}

application {
    mainClass.set("org.apache.utlx.engine.MainKt")

    applicationDefaultJvmArgs = listOf(
        "-Xmx2048m",
        "-XX:+UseG1GC"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.apache.utlx.engine.MainKt",
            "Implementation-Title" to "UTL-X Engine (UTLXE)",
            "Implementation-Version" to project.version
        )
    }

    archiveBaseName.set("utlxe")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    dependsOn(configurations.runtimeClasspath)
}

tasks.test {
    useJUnitPlatform()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.60.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}
