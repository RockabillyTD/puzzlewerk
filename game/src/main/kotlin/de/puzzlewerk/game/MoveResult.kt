package de.puzzlewerk.game

/**
 * Ergebnis eines Zugversuchs — erwartbare Fehler sind Werte, keine Exceptions (Regel C3).
 */
public sealed interface MoveResult {
    /** Der Zug war gültig; [state] ist der neue Spielzustand. */
    public data class Applied(val state: GameState) : MoveResult

    /** Der Zug wurde abgelehnt; [reason] benennt die Ursache. */
    public data class Rejected(val reason: RejectionReason) : MoveResult
}

/** Gründe, aus denen ein Zug abgelehnt wird. */
public enum class RejectionReason {
    /** Die Partie ist bereits gewonnen oder verloren. */
    GAME_ALREADY_FINISHED,

    /** Negative Punktwerte sind nicht zulässig. */
    NEGATIVE_POINTS,
}
