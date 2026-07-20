package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.crystalFill
import de.puzzlewerk.game.element.Element

/**
 * Ereignis-Datum des Juice-Layers (§13.9–§13.11, ADR-012): Ergebnis des
 * Vorher/Nachher-Vergleichs zweier trace-Ergebnisse via [juiceDelta].
 *
 * @property newlyFulfilled Kristalle, die NACH dem Zug erfüllt sind und
 *   VORHER nicht (R46: nach Undo erneut möglich) — sortiert aufsteigend
 *   nach (r, dann q), exakt die Burst-/SFX-Kaskadenreihenfolge §13.9.
 * @property newlyLit Kristalle, deren `received` von „kein Eintrag"
 *   auf ≥ 1 Komponente wechselt (§13.9 sfx_beam_connect-Bedingung:
 *   `newlyLit.isNotEmpty() && newlyFulfilled.isEmpty()`).
 * @property fulfilled ALLE nach dem Zug erfüllten Kristalle — L der
 *   Stem-Tabelle §13.11 ist `fulfilled.size`, auch abwärts nach
 *   Undo/Reset (R46, keine Hysterese).
 * @property crystalTotal K = Kristallzahl des Levels (§13.11, §13.10).
 */
public data class JuiceDelta(
    val newlyFulfilled: List<HexCoord>,
    val newlyLit: Set<HexCoord>,
    val fulfilled: Set<HexCoord>,
    val crystalTotal: Int,
) {
    /** Combo-Größe N des Zugs (§13.9): Anzahl neu erfüllter Kristalle. */
    public val comboSize: Int get() = newlyFulfilled.size
}

/**
 * Vorher/Nachher-Vergleich zweier trace-Ergebnisse desselben Levels —
 * das Ereignis-Datum des Juice-Layers (§13.9–§13.11). Pure Funktion:
 * kein Zustand, keine Seiteneffekte; „erfüllt" heißt exakt
 * [CrystalFill.FULFILLED] (Übersättigung zählt NICHT als erfüllt).
 *
 * @param before trace VOR dem Zug; `null` beim Partie-Start (dann sind
 *   [JuiceDelta.newlyFulfilled] und [JuiceDelta.newlyLit] leer — das
 *   Laden eines Levels löst keine Bursts aus, auch im R31-Fall nicht).
 * @param after frischer trace NACH dem Zug ([MoveResult.Applied.trace]).
 * @param board effektives Brett des NEUEN Zustands
 *   ([GameState.currentBoard]) — Quelle der Soll-Farben und von K.
 */
public fun juiceDelta(
    before: TraceResult?,
    after: TraceResult,
    board: Board,
): JuiceDelta {
    val fulfilled = fulfilledCrystals(after, board)
    val newlyFulfilled =
        if (before == null) {
            emptyList()
        } else {
            (fulfilled - fulfilledCrystals(before, board)).sortedWith(compareBy({ it.r }, { it.q }))
        }
    return JuiceDelta(
        newlyFulfilled = newlyFulfilled,
        newlyLit = if (before == null) emptySet() else after.received.keys - before.received.keys,
        fulfilled = fulfilled,
        crystalTotal = board.elements.values.count { it is Element.Crystal },
    )
}

/** Kristallzellen von [board], die in [trace] exakt erfüllt sind (§5.4, §4.7). */
private fun fulfilledCrystals(
    trace: TraceResult,
    board: Board,
): Set<HexCoord> =
    board.elements
        .filter { (cell, element) ->
            element is Element.Crystal &&
                crystalFill(element.required, trace.received[cell]) == CrystalFill.FULFILLED
        }.keys
