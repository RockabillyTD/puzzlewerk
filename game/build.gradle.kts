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

// PW-2.7: CLI-Demo-Runner als Phase-2-Gate-Nachweis (docs/plan.md Abschnitt 7).
// Aufruf: ./gradlew :game:runDemo [-PdemoArgs="<seed> <D1..D7>"] — keine neuen
// Dependencies, kein application-Plugin; nur der Main-Klassenpfad des Moduls.
tasks.register<JavaExec>("runDemo") {
    group = "application"
    description = "Fuehrt den CLI-Demo-Runner aus (Args via -PdemoArgs=\"<seed> <tier>\")"
    mainClass.set("de.puzzlewerk.game.demo.DemoRunnerKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args =
        providers
            .gradleProperty("demoArgs")
            .orNull
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            .orEmpty()
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
