package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.Immutable
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.color.crystalFill
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.Segment
import de.puzzlewerk.game.trace.TraceResult

/**
 * Render-fertiger Spielfeld-Zustand für [BoardCanvas] — die einzige Wahrheit
 * des Brett-Renderings (unidirektionaler Datenfluss; ab PW-3.5 Teil des
 * `GameUiState`, hier per [boardUiState] aus `:game`-Werten abgeleitet).
 *
 * @property radius Brettradius `R ∈ 2..5` (§2.1).
 * @property cells Alle Brettzellen in deterministischer Lesereihenfolge
 *   (r aufsteigend, dann q), inklusive leerer Zellen.
 * @property beams Beleuchtete Kanten aus `TraceResult.segments` in der
 *   normativen Erzeugungsreihenfolge (§5.2).
 */
@Immutable
data class BoardUiState(
    val radius: Int,
    val cells: List<BoardCell>,
    val beams: List<Segment>,
)

/**
 * Eine Brettzelle für das Rendering.
 *
 * @property coord Axial-Koordinate der Zelle (§2.1).
 * @property element Zellinhalt oder `null` für eine leere Zelle (§2.3).
 * @property crystal Kristall-Renderzustand — genau dann gesetzt, wenn
 *   [element] ein Kristall ist.
 */
@Immutable
data class BoardCell(
    val coord: HexCoord,
    val element: Element?,
    val crystal: CrystalCellState? = null,
)

/**
 * Renderzustand eines Kristalls (§4.7, §12.3).
 *
 * @property required Sollfarbe ∈ 1..7.
 * @property received Empfangenes Licht oder `null` = dunkel (fehlender
 *   Eintrag in `TraceResult.received`, §5.4).
 * @property fill Klassifikation dunkel/teilerfüllt/erfüllt/übersättigt —
 *   berechnet in `:game` ([crystalFill]), nie in der UI.
 */
@Immutable
data class CrystalCellState(
    val required: LightColor,
    val received: LightColor?,
    val fill: CrystalFill,
)

/**
 * Abbildung `:game`-Zustand → [BoardUiState]. Reines Umpacken plus
 * Kristall-Klassifikation über [crystalFill] aus `:game` — KEINE Spiellogik
 * in der UI (CLAUDE.md; ab PW-3.5 ruft das `GameViewModel` diese Funktion).
 */
fun boardUiState(
    board: Board,
    trace: TraceResult,
): BoardUiState =
    BoardUiState(
        radius = board.radius,
        cells = HexGeometry.boardCells(board.radius).map { coord -> boardCell(coord, board[coord], trace) },
        beams = trace.segments,
    )

private fun boardCell(
    coord: HexCoord,
    element: Element?,
    trace: TraceResult,
): BoardCell {
    val crystal =
        (element as? Element.Crystal)?.let {
            val received = trace.received[coord]
            CrystalCellState(it.required, received, crystalFill(it.required, received))
        }
    return BoardCell(coord = coord, element = element, crystal = crystal)
}
