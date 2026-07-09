pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal {
            content {
                // ADR-002: Portal nur für Ktlint-Gradle (Marker + Implementierung),
                // nicht auf Maven Central publiziert (Stand 12.1.2).
                includeGroupByRegex("org\\.jlleitschuh\\.gradle(\\.ktlint)?")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "puzzlewerk"

include(":app")
include(":game")
include(":data")
include(":core")
