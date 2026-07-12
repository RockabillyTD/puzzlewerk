import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    // ADR-007: kotlinx.serialization für die versionierten Persistenz-Schemata.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.puzzlewerk.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":game"))

    // Flow ist Teil der öffentlichen Repository-API → api statt implementation.
    api(libs.kotlinx.coroutines.core)

    // ADR-007: typisierter DataStore + JSON-Codec.
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    // ADR-009: Flow-/Coroutine-Tests für die Repository-Implementierungen.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
