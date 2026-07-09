import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule {
                minBound(85)
            }
        }
    }
}
