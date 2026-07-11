import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // Liefert den Root-`check`-Lifecycle-Task als Anker für checkModuleGraph (ADR-004).
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        parallel = true
    }

    extensions.configure<KtlintExtension> {
        android.set(true)
    }
}

// PW-2.6-impl: maschinelle Erzwingung des Schichtenmodells (ADR-004, Whitelist abschließend).
val moduleGraphWhitelist =
    mapOf(
        ":app" to setOf(":data", ":game", ":core"),
        ":data" to setOf(":game", ":core"),
        ":game" to setOf(":core"),
        ":core" to emptySet(),
    )
val pureJvmModules = setOf(":game", ":core")
val forbiddenAndroidPlugins = setOf("com.android.application", "com.android.library")

// Regel-5-Sperrliste für externe Koordinaten in :game/:core (ADR-004, erweitert um den
// gesamten Google-Namensraum durch ADR-005 — schließt Security-Finding L3, das android.jar-Stub
// `com.google.android:android`). Änderungen an dieser Liste nur per neuem ADR.
val forbiddenCoordinatePrefixes = setOf("androidx.", "com.android", "com.google.")

fun moduleGraphViolations(project: Project): List<String> {
    val allowed =
        moduleGraphWhitelist[project.path]
            ?: return listOf("${project.path}: unbekanntes Modul — neues Modul braucht ADR + Whitelist-Erweiterung")
    val violations = mutableListOf<String>()
    val isPureJvm = project.path in pureJvmModules
    if (isPureJvm) {
        forbiddenAndroidPlugins.filter(project.plugins::hasPlugin).forEach { id ->
            violations += "${project.path}: verbotenes Plugin «$id»"
        }
    }
    project.configurations.forEach { configuration ->
        configuration.dependencies.forEach { dependency ->
            val group = dependency.group.orEmpty()
            // Selbstkanten (z. B. AGP-Testvarianten, Kover) können das Schichtenmodell nicht verletzen.
            if (dependency is ProjectDependency && dependency.path != project.path && dependency.path !in allowed) {
                violations += "${project.path} → ${dependency.path} (Konfiguration «${configuration.name}»)"
            } else if (isPureJvm && dependency !is ProjectDependency &&
                forbiddenCoordinatePrefixes.any(group::startsWith)
            ) {
                violations += "${project.path}: verbotene Koordinate «$group:${dependency.name}» " +
                    "(Konfiguration «${configuration.name}»)"
            }
        }
    }
    return violations
}

val checkModuleGraph by tasks.registering {
    group = "verification"
    description = "Prüft Modul-Kanten und Android-Freiheit von :game/:core gegen die Whitelist aus ADR-004/ADR-005."
    doLast {
        val violations = subprojects.flatMap(::moduleGraphViolations)
        check(violations.isEmpty()) {
            violations.joinToString(
                prefix =
                    "Schichtenmodell verletzt (siehe docs/decisions/adr-004-schichtenmodell.md " +
                        "und adr-005-android-koordinaten-sperre.md):\n",
                separator = "\n",
            ) { "  - $it — verstößt gegen ADR-004/ADR-005" }
        }
    }
}

tasks.named("check") {
    dependsOn(checkModuleGraph)
}
