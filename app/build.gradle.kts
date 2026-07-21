import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // ADR-009: Coverage-Gate :app ≥ 70 % Line, scharf ab PW-3.3.
    alias(libs.plugins.kover)
}

android {
    namespace = "de.puzzlewerk.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.puzzlewerk"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // PW-4.10: Phase-4-Gate — SemVer minor (Juice-Update), versionCode monoton.
        versionCode = 2
        versionName = "0.4.0"
    }

    buildTypes {
        release {
            // Regel S8: Release-Härtung
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // ADR-009: Robolectric-Compose-Tests brauchen die Android-Ressourcen.
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // "Neuere Version verfügbar" ist kein Codefehler und würde Builds
        // zeitabhängig brechen — Updates laufen kontrolliert über Renovate/ADR.
        disable += listOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
        // OldTargetApi ist umgebungsabhängig: Lint leitet die "neueste" API aus den
        // im SDK installierten Platforms ab. Auf ubuntu-latest (vorinstalliertes SDK
        // mit neueren Platforms) schlug targetSdk 36 als Error auf, lokal nicht —
        // gleicher Commit, unterschiedliches Ergebnis (Reproduzierbarkeitsbruch,
        // CI-Run 29031584439). targetSdk-Anhebungen laufen kontrolliert pro
        // Android-Release; der harte Play-Store-Gate ExpiredTargetSdkVersion bleibt
        // aktiv. Issue: docs/backlog.md ("Jährlicher targetSdk-Bump", PW-0.5).
        disable += "OldTargetApi"
        // UseTomlInstead: Einzig org.robolectric:android-all-instrumented steht
        // bewusst direkt in diesem Skript (Testlaufzeit-Pinnung, ADR-009/S6) —
        // die Ticket-Dateimenge PW-3.3 lässt den Version Catalog unangetastet
        // (docs/phase3-tickets.md, Querschnitts-Regeln). Issue: docs/backlog.md
        // („android-all-Koordinate in den Katalog umziehen", PW-3.3).
        disable += "UseTomlInstead"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

androidComponents {
    // Unit-/UI-Tests laufen gegen die Debug-Variante (die Test-Activity aus
    // ui-test-manifest ist debugImplementation, ADR-009). Die Release-Variante
    // unterscheidet sich nur durch R8/Shrinking — ein doppelter Testlauf ohne
    // Test-Manifest würde scheitern und brächte keinen Erkenntnisgewinn.
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.enableUnitTest = false
    }
}

// ADR-009/S6: Robolectric lädt seine android-all-Jars standardmäßig zur
// TESTLAUFZEIT von Maven Central — an der Dependency Verification vorbei.
// Stattdessen wird das Artefakt hier als normale Gradle-Konfiguration
// aufgelöst (damit SHA-256-gepinnt in gradle/verification-metadata.xml) und
// den Tests per robolectric.offline/robolectric.dependency.dir untergeschoben.
// Kein Test-Task darf zur Laufzeit ungeprüfte Artefakte nachladen.
val robolectricAndroidAll: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val robolectricJarsDir = layout.buildDirectory.dir("robolectric-android-all")

val syncRobolectricAndroidAll by tasks.registering(Sync::class) {
    description = "Stellt die gepinnten android-all-Jars für den Robolectric-Offline-Betrieb bereit (ADR-009/S6)."
    from(robolectricAndroidAll)
    into(robolectricJarsDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricAndroidAll)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.get().asFile.absolutePath)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":game"))
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    debugImplementation(libs.compose.ui.tooling)
    // ADR-009: Test-Activity für createComposeRule unter Robolectric.
    debugImplementation(libs.compose.ui.test.manifest)

    // ADR-009: UI-Test-Stack — Robolectric ist ein JUnit4-Runner; JUnit 5
    // bleibt Standard in :game/:core/:data.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // ADR-009 (S6-Auflage): android-all für @Config(sdk = [35]) und
    // Robolectric 4.15.1 — Version aus org.robolectric.plugins.DefaultSdkProvider.
    // Bewusst NICHT im Version Catalog: Ticket-Dateimenge PW-3.3 beschränkt die
    // Dependency-Änderung auf dieses Modul + verification-metadata (Ausnahme in
    // docs/phase3-tickets.md, Querschnitts-Regeln, ausdrücklich vorgesehen).
    robolectricAndroidAll("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}

kover {
    reports {
        filters {
            excludes {
                // Previews sind reine Tooling-Einstiege ohne Laufzeitpfad —
                // keine Suppression, sondern Definitionslücke des Messobjekts.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
        verify {
            rule {
                // Quality Gate ADR-009/plan.md §4.3: :app ≥ 70 % Line.
                minBound(70)
            }
        }
    }
}
