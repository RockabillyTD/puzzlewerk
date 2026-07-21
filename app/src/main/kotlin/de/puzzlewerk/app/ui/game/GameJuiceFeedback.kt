package de.puzzlewerk.app.ui.game

import androidx.compose.ui.graphics.toArgb
import de.puzzlewerk.app.ui.juice.BurstOrigin
import de.puzzlewerk.app.ui.juice.EndpointSpark
import de.puzzlewerk.app.ui.juice.JuiceEvent
import de.puzzlewerk.app.ui.juice.JuiceEventQueue
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.trace.BeamEndpoint

/**
 * Juice-Ereignisdaten einer Partie (PW-4.6): das [GameViewModel] übersetzt
 * `MoveResult.Applied` + `juiceDelta` (ADR-012) in diese Effects; der
 * GameScreen mappt sie mit Brett-Geometrie und Palette in [JuiceEvent]s
 * ([offerJuiceEvents]) — der Juice-Kern kennt weder Hex- noch
 * Pixel-Koordinaten (ADR-011).
 */
internal sealed interface JuiceFeedback {
    /**
     * Partie-Start (`newGame`, auch „Nochmal"): setzt Puls-Nullpunkt und
     * initiale Funken-Emitter; löst nie Bursts aus (ADR-012, `before = null`).
     */
    data class BoardEntered(
        val levelSeed: Long,
        val endpoints: List<BeamEndpoint>,
    ) : JuiceFeedback

    /**
     * Angewandter Zug (§13.9/§13.10).
     *
     * @property moveNumber Zugzähler NACH dem Zug (Seed-Bestandteil, ADR-011).
     * @property rotatedCell Gedrehte Zelle bei gültiger Drehung, sonst `null`
     *   (Undo/Reset lösen keinen Dreh-Blitz aus).
     * @property rotatedOrientationDegrees Neue Element-Orientierung in Grad —
     *   Bezugssystem der drei Dreh-Funken (§13.9).
     * @property newlyFulfilled Neu erfüllte Kristalle in Kaskadenreihenfolge
     *   (r, dann q — Sortierung aus `JuiceDelta.newlyFulfilled`, ADR-012).
     * @property endpoints Strahl-Endpunkte des frischen trace (§13.8a).
     * @property solved Lösungs-Daten des Zugs oder `null` (§13.10).
     */
    data class MoveApplied(
        val moveNumber: Int,
        val rotatedCell: HexCoord?,
        val rotatedOrientationDegrees: Float,
        val newlyFulfilled: List<CrystalBurstData>,
        val endpoints: List<BeamEndpoint>,
        val solved: SolvedData?,
    ) : JuiceFeedback

    /** R49: laufende Effekte sofort verwerfen („Nochmal"/„Weiter"/Zurück). */
    data object EffectsDismissed : JuiceFeedback
}

/** Neu erfüllter Kristall: Zelle plus SOLL-Farbe (§13.9, ADR-012). */
internal data class CrystalBurstData(
    val cell: HexCoord,
    val required: LightColor,
)

/** Lösungs-Daten (§13.10): K und die Soll-Farben aller Kristalle in Brett-Reihenfolge (r, dann q). */
internal data class SolvedData(
    val crystalCount: Int,
    val paletteRequired: List<LightColor>,
)

/**
 * Abbildungskontext GameScreen → Juice-Kern: Zellzentren werden über
 * [geometry] (Canvas-Pixel) und [density] in dp relativ zur linken oberen
 * Canvas-Ecke gemappt (Koordinaten-Vertrag `drawJuiceEffects`, PW-4.5),
 * `LightColor` über [colors] (§13.4) in ARGB aufgelöst.
 */
internal class JuiceEventMapping(
    val geometry: BoardGeometry,
    val density: Float,
    val reduceMotion: Boolean,
    val colors: BoardColors = BoardColors(),
) {
    private fun xDp(cell: HexCoord): Float = geometry.center(cell).x / density

    private fun yDp(cell: HexCoord): Float = geometry.center(cell).y / density

    fun spark(endpoint: BeamEndpoint): EndpointSpark =
        EndpointSpark(xDp(endpoint.cell), yDp(endpoint.cell), colors.beam(endpoint.color).toArgb())

    fun burst(data: CrystalBurstData): BurstOrigin =
        BurstOrigin(xDp(data.cell), yDp(data.cell), colors.beam(data.required).toArgb())

    fun rotateFlash(feedback: JuiceFeedback.MoveApplied): JuiceEvent.RotateFlash? =
        feedback.rotatedCell?.let {
            JuiceEvent.RotateFlash(xDp(it), yDp(it), feedback.rotatedOrientationDegrees)
        }
}

/**
 * Übersetzt ein [JuiceFeedback] in [JuiceEvent]s und reiht sie in EINEM Rutsch
 * ein — ein `drain()` = ein Frame, damit gilt der [JuiceEvent.Solved]-Kontrakt
 * (Solved im selben Frame NACH den [JuiceEvent.CrystalBursts]).
 */
internal fun offerJuiceEvents(
    queue: JuiceEventQueue,
    feedback: JuiceFeedback,
    mapping: JuiceEventMapping,
) {
    when (feedback) {
        is JuiceFeedback.BoardEntered ->
            queue.offer(
                JuiceEvent.ScreenEntered(
                    levelSeed = feedback.levelSeed,
                    reduceMotion = mapping.reduceMotion,
                    endpoints = feedback.endpoints.map(mapping::spark),
                ),
            )
        is JuiceFeedback.MoveApplied -> offerMove(queue, feedback, mapping)
        JuiceFeedback.EffectsDismissed -> queue.offer(JuiceEvent.Dismissed)
    }
}

private fun offerMove(
    queue: JuiceEventQueue,
    feedback: JuiceFeedback.MoveApplied,
    mapping: JuiceEventMapping,
) {
    mapping.rotateFlash(feedback)?.let(queue::offer)
    if (feedback.newlyFulfilled.isNotEmpty()) {
        queue.offer(JuiceEvent.CrystalBursts(feedback.moveNumber, feedback.newlyFulfilled.map(mapping::burst)))
    }
    queue.offer(JuiceEvent.EndpointsChanged(feedback.moveNumber, feedback.endpoints.map(mapping::spark)))
    feedback.solved?.let { solved ->
        queue.offer(
            JuiceEvent.Solved(
                moveNumber = feedback.moveNumber,
                crystalCount = solved.crystalCount,
                paletteArgb = solved.paletteRequired.map { mapping.colors.beam(it).toArgb() },
            ),
        )
    }
}
