plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.h5viewer"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // SQL engine + JDBC front-end (Avatica). Provides everything except the data.
    implementation("org.apache.calcite:calcite-core:1.42.0")
    // Pure-Java HDF5 reader.
    implementation("io.jhdf:jhdf:0.9.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.shadowJar {
    // Single drop-in driver jar for DBeaver / DataGrip / the JetBrains Database tool.
    archiveBaseName = "h5-jdbc"
    archiveClassifier = "all"
    // Calcite and our own code both ship META-INF/services/java.sql.Driver — merge them.
    mergeServiceFiles()
}

// `build` should produce the fat driver jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}
