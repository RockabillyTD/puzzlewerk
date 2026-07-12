package de.puzzlewerk.app.ui.navigation

import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT

/**
 * Welche Partie der Spiel-Screen spielt (ADR-008): typisierte Argumente
 * statt Routen-Strings. Auswertung durch das GameViewModel ab PW-3.5.
 */
sealed interface LevelRequest {
    /** Kampagnenlevel §11; [levelNumber] außerhalb 1..50 ist ein Programmierfehler (C3). */
    data class Campaign(
        val levelNumber: Int,
    ) : LevelRequest {
        init {
            require(levelNumber in 1..CAMPAIGN_LEVEL_COUNT) {
                "Kampagnenlevel muss in 1..$CAMPAIGN_LEVEL_COUNT liegen, war $levelNumber"
            }
        }
    }

    /** Tägliches Prisma §10 für den Tag [epochDay] (Tage seit 1970-01-01, nie negativ). */
    data class Daily(
        val epochDay: Long,
    ) : LevelRequest {
        init {
            require(epochDay >= 0) { "epochDay darf nicht negativ sein, war $epochDay" }
        }
    }
}

/**
 * Die fünf Navigationsziele der App (§12.1) als Wertobjekte —
 * verbindliche Form aus ADR-008, keine Navigation-Dependency.
 */
sealed interface Screen {
    /** Startbildschirm §12.2; immer die Wurzel des Backstacks. */
    data object Home : Screen

    /** Levelauswahl §12.4 (Inhalt: Ticket PW-3.6). */
    data object LevelSelect : Screen

    /** Spiel-Screen §12.3 für [request] (Inhalt: Tickets PW-3.4/PW-3.5). */
    data class Game(
        val request: LevelRequest,
    ) : Screen

    /** Daily-Screen §12.4 (Phase 4). */
    data object Daily : Screen

    /** Einstellungen §12.5 (Phase 4). */
    data object Settings : Screen
}
