package de.puzzlewerk.data

/**
 * Speichert den Spielfortschritt (bester Punktestand pro Level, freigeschaltete Levels).
 *
 * Erwartete Fehlerfälle liefern Werte statt Exceptions (Regel C3); ungültige
 * Parameter (negativer Score, Level < 1) sind Programmierfehler.
 */
interface ProgressRepository {

    /** Bester gespeicherter Punktestand für [levelId], oder `null`, wenn nie gespielt. */
    fun bestScore(levelId: Int): Int?

    /** Speichert [score] für [levelId], falls er den bisherigen Bestwert übertrifft. */
    fun saveResult(levelId: Int, score: Int)

    /** Höchstes freigeschaltetes Level (mindestens 1). */
    fun highestUnlockedLevel(): Int
}

/**
 * In-Memory-Implementierung für Phase 0.
 *
 * Wird in Phase 3 durch eine Room-basierte Implementierung ersetzt (ADR folgt);
 * die Schnittstelle bleibt dabei stabil.
 */
class InMemoryProgressRepository : ProgressRepository {

    private val bestScores = mutableMapOf<Int, Int>()

    override fun bestScore(levelId: Int): Int? = bestScores[levelId]

    override fun saveResult(levelId: Int, score: Int) {
        require(levelId >= 1) { "levelId muss >= 1 sein, war $levelId" }
        require(score >= 0) { "score darf nicht negativ sein, war $score" }

        val previous = bestScores[levelId]
        if (previous == null || score > previous) {
            bestScores[levelId] = score
        }
    }

    override fun highestUnlockedLevel(): Int = (bestScores.keys.maxOrNull() ?: 0) + 1
}
