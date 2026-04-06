plugins {
    kotlin("jvm") version "1.9.21"
    `java-library`
    `maven-publish`
    antlr
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

// Repository management is handled in settings.gradle.kts

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    // ANTLR
    antlr("org.antlr:antlr4:4.13.1")
    implementation("org.antlr:antlr4-runtime:4.13.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.11")  // implementation (not runtime) for DebugConfig

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

// Configure ANTLR
tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-long-messages")
    outputDirectory = file("${project.buildDir}/generated-src/antlr/main/org/apache/utlx/core/udm/parser")
}

// Ensure generated sources are included
sourceSets {
    main {
        java {
            srcDir("${project.buildDir}/generated-src/antlr/main")
        }
    }
}

// Ensure Java compilation happens after ANTLR generation
tasks.named("compileJava") {
    dependsOn("generateGrammarSource")
}

// Ensure test Kotlin compilation depends on test ANTLR generation
tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
}

// Ensure sourcesJar depends on ANTLR generation (must come after java block)
tasks.named("sourcesJar") {
    dependsOn("generateGrammarSource")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("UTL-X Core")
                description.set("Core language implementation for UTL-X transformation language")
                url.set("https://github.com/grauwen/utl-x")
                
                licenses {
                    license {
                        name.set("AGPL-3.0")
                        url.set("https://www.gnu.org/licenses/agpl-3.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("grauwen")
                        name.set("Ir. Marcel A. Grauwen")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/grauwen/utl-x.git")
                    developerConnection.set("scm:git:ssh://github.com/grauwen/utl-x.git")
                    url.set("https://github.com/grauwen/utl-x")
                }
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}
