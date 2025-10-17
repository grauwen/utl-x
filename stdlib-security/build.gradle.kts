plugins {
    kotlin("jvm")
    `java-library`
}

group = "org.apache.utlx"
version = "0.1.0-SNAPSHOT"

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib"))
    
    // UTL-X Core
    implementation(project(":modules:core"))
    
    // JWT libraries (commenting out for now to avoid external dependencies)
    // implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    // implementation("com.auth0:java-jwt:4.4.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}