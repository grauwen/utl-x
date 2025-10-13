plugins {
    kotlin("jvm") version "1.9.21"
    `java-library`
    `maven-publish`
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
    withJavadocJar()
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
