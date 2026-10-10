plugins {
    kotlin("jvm") version "1.9.21"
    `java-library`
}

group = "com.glomidco.utlx"
version = "1.3.0"

// IF19: shared, file-level Bundle Management layer. Intentionally dependency-light — it
// manages bundle files (.utlx / transform.yaml / schemas / engine.yaml) as blobs so BOTH
// utlxd (over the IDE workspace) and, later, utlxe/EF03 can depend on it without either
// dragging in the other's runtime. No dependency on :modules:engine or :modules:core.
dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
