package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.HexCoord

/**
 * Eingaben der Spielerin (Design §6.1/§6.2). Es gibt genau einen spielenden
 * Zugtyp ([Rotate]); [Undo] und [Reset] verwalten den Verlauf.
 */
public sealed interface Move {
    /**
     * Tap auf ein drehbares Element auf [cell]: `m → (m + 1) mod 6`, Zugzähler +1
     * (Design §6.1). Es gibt keine Gegenrichtung — 6 Taps = Ausgangslage (R30).
     *
     * @property cell Zielzelle; leer, nicht drehbar oder außerhalb des Bretts ⇒
     *   [MoveResult.Invalid] mit [InvalidMoveReason.NO_ELEMENT] bzw.
     *   [InvalidMoveReason.NOT_ROTATABLE] (R27).
     */
    public data class Rotate(
        val cell: HexCoord,
    ) : Move

    /**
     * Nimmt den letzten [Rotate]-Zug zurück: `m → (m + 5) mod 6`, Zähler −1,
     * Verlaufseintrag entfernt (Design §6.2, Invariante I10). Unbegrenzt bis der
     * Verlauf leer ist (dann [InvalidMoveReason.HISTORY_EMPTY], R28). Kein Redo in V1.
     */
    public data object Undo : Move

    /**
     * Stellt den Startzustand des Levels her: Orientierungen wie geladen,
     * Zähler 0, Verlauf leer (Design §6.2, R29 — bei 0 Zügen ein gültiges No-op).
     */
    public data object Reset : Move
}
