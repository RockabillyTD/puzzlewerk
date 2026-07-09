import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :game ist ein reines Kotlin/JVM-Modul OHNE Android-Abhängigkeiten (Regel C2 / ADR-001).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Regel C6: öffentliche API ist bewusst und explizit (erzwingt `public` + KDoc-Disziplin)
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":core"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule {
                // Quality Gate: Spiellogik muss nahezu vollständig getestet sein
                minBound(90)
            }
        }
    }
}
