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

    // Relocate bundled third-party libraries into a private namespace so they can't
    // clash with the host's own copies. The JetBrains Database tool runs the driver in
    // a process that already has guava/jackson/protobuf/asm/etc.; without relocation the
    // version skew surfaces as a bare java.lang.UnsupportedOperationException during
    // schema introspection. Calcite (the engine), jHDF and our own code stay in place.
    val shaded = "com.h5viewer.jdbc.shaded"
    relocate("com.google", "$shaded.google")            // guava, protobuf, errorprone, …
    relocate("com.fasterxml.jackson", "$shaded.jackson")
    relocate("org.apache.commons", "$shaded.apachecommons")
    relocate("org.apache.hc", "$shaded.apachehc")
    relocate("org.objectweb.asm", "$shaded.asm")
    relocate("org.checkerframework", "$shaded.checkerframework")
    relocate("org.codehaus", "$shaded.codehaus")        // janino / commons-compiler
    relocate("com.jayway.jsonpath", "$shaded.jsonpath")
    relocate("net.minidev", "$shaded.minidev")
    relocate("org.json", "$shaded.json")
    relocate("org.yaml", "$shaded.yaml")
    relocate("org.slf4j", "$shaded.slf4j")
    relocate("com.yahoo", "$shaded.yahoo")
    relocate("org.locationtech", "$shaded.locationtech")
    relocate("org.pentaho", "$shaded.pentaho")
    relocate("org.apiguardian", "$shaded.apiguardian")
    relocate("org.joou", "$shaded.joou")
    relocate("org.publicsuffix", "$shaded.publicsuffix")
}

// `build` should produce the fat driver jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}
