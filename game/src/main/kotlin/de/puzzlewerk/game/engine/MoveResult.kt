package de.puzzlewerk.game.engine

import de.puzzlewerk.game.trace.TraceResult

/**
 * Ergebnis eines Zugversuchs — erwartbare Ablehnungen sind Werte, keine
 * Exceptions (Design §6.1, Regel C3).
 */
public sealed interface MoveResult {
    /**
     * Der Zug wurde angewandt.
     *
     * @property state Der neue Partie-Zustand; `state.solved` zeigt an, ob die
     *   Partie mit diesem Zug in den Zustand Gelöst gewechselt ist (§6.1).
     * @property trace Frische trace-Auswertung des neuen Brettzustands — die UI
     *   rendert Strahlen nach JEDEM Zug direkt hieraus (§12.3).
     */
    public data class Applied(
        val state: GameState,
        val trace: TraceResult,
    ) : MoveResult

    /**
     * Der Zug ändert NICHTS: Zustand, Zähler und Verlauf bleiben unverändert
     * (Design §6.1, Invariante I7). Kein Fehler-Popup — nur sanftes UI-Feedback.
     */
    public data class Invalid(
        val reason: InvalidMoveReason,
    ) : MoveResult
}

/** Gründe für einen wirkungslosen Zug (Design §6.1/§6.2; R27/R28/R32). */
public enum class InvalidMoveReason {
    /** Rotate auf leerer Zelle oder außerhalb des Bretts (R27). */
    NO_ELEMENT,

    /** Rotate auf einem nicht drehbaren Element (Quelle, Prisma, Filter, Portal, Kristall, Wand; R27). */
    NOT_ROTATABLE,

    /** Partie ist bereits gelöst — Rotate, Undo und Reset sind gesperrt (§6.2, R32). */
    ALREADY_SOLVED,

    /** Undo bei leerem Verlauf (R28). */
    HISTORY_EMPTY,
}
