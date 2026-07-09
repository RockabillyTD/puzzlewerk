import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.puzzlewerk.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.puzzlewerk"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
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
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
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
}
