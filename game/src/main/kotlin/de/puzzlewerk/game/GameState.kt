package de.puzzlewerk.game

/**
 * Unveränderlicher Spielzustand.
 *
 * HINWEIS (Phase 0): Dies ist ein bewusst minimales Platzhalter-Modell, das den
 * Architektur-Stil vorgibt (immutable data class, Seed-basiert). Das echte Modell
 * (Brett, Kacheln, Züge) wird in Phase 2 exakt nach docs/game-design.md
 * implementiert und ersetzt dieses Gerüst.
 *
 * @property levelId Nummer des Levels, beginnend bei 1.
 * @property seed Seed, aus dem alle Zufallselemente des Levels deterministisch
 *   abgeleitet werden (Regel C2).
 * @property score Aktueller Punktestand, niemals negativ.
 * @property movesPlayed Anzahl der bislang angewendeten Züge.
 * @property status Aktueller Spielstatus.
 */
public data class GameState(
    val levelId: Int,
    val seed: Long,
    val score: Int = 0,
    val movesPlayed: Int = 0,
    val status: GameStatus = GameStatus.RUNNING,
)

/** Status einer Partie. */
public enum class GameStatus {
    RUNNING,
    WON,
    LOST,
}
