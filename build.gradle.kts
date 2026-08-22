import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.h5viewer"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile against IntelliJ IDEA Community. The plugin only uses the
        // core platform APIs, so it also loads in any other IntelliJ-based IDE.
        intellijIdeaCommunity("2025.2.6")

        // Plugin Verifier, run by the release workflow.
        pluginVerifier()
    }

    // Pure-Java HDF5 reader (no native libraries). Bundled into the plugin.
    // slf4j-api is provided by the IntelliJ Platform, so don't bundle a second copy.
    implementation("io.jhdf:jhdf:0.9.4") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Kept wide so the plugin loads in most reasonably recent IDEs.
            sinceBuild = "242"
            untilBuild = "262.*"
        }
    }

    // Marketplace publishing token (set as the PUBLISH_TOKEN CI secret).
    // The plugin is published unsigned; JetBrains Marketplace handles distribution.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // Which IDEs the IntelliJ Plugin Verifier checks against in the release job.
    pluginVerification {
        ides {
            recommended()
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    wrapper {
        gradleVersion = "9.7.1"
    }
}