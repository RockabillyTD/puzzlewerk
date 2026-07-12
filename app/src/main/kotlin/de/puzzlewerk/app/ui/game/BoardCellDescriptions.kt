package de.puzzlewerk.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.puzzlewerk.app.R
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element

/**
 * TalkBack-Beschreibung einer Brettzelle nach dem §13.5-Muster, z. B.
 * „Spiegel, drehbar, Orientierung 2 von 6, Reihe 0, Spalte 1" bzw.
 * „Kristall, benötigt Gelb (Rot und Grün), empfängt Rot". Alle Texte aus
 * strings_game.xml (de/en) — keine Hardcoded Strings.
 */
@Composable
internal fun cellContentDescription(cell: BoardCell): String {
    val position = stringResource(R.string.board_cell_position, cell.coord.r, cell.coord.q)
    val crystal = cell.crystal
    return if (crystal != null) {
        crystalDescription(crystal, position)
    } else {
        elementDescription(cell.element, position)
    }
}

@Composable
private fun elementDescription(
    element: Element?,
    position: String,
): String =
    when (element) {
        null, is Element.Wall, is Element.Prism ->
            stringResource(R.string.board_cell_simple, stringResource(staticNameRes(element)), position)
        is Element.Source ->
            stringResource(
                R.string.board_cell_source,
                colorName(element.color),
                directionName(element.direction),
                position,
            )
        is Element.Rotatable -> rotatableDescription(element, position)
        is Element.Filter -> stringResource(R.string.board_cell_filter, colorName(element.color), position)
        is Element.Portal -> stringResource(R.string.board_cell_portal, portalPairName(element.pairId), position)
        // Kristalle laufen über cellContentDescription; Fallback für handgebaute Zustände ohne CrystalCellState.
        is Element.Crystal ->
            crystalDescription(CrystalCellState(element.required, null, CrystalFill.DARK), position)
    }

/**
 * Drehbares Element §13.5. Anzeige der Orientierung als `m + 1` von 6
 * (menschenlesbar 1..6 statt des internen Index 0..5).
 */
@Composable
private fun rotatableDescription(
    element: Element.Rotatable,
    position: String,
): String {
    val nameRes = if (element is Element.Mirror) R.string.board_element_mirror else R.string.board_element_splitter
    return stringResource(
        R.string.board_cell_rotatable,
        stringResource(nameRes),
        (element.orientation.steps + 1).toString(),
        Orientation.COUNT.toString(),
        position,
    )
}

@Composable
private fun crystalDescription(
    crystal: CrystalCellState,
    position: String,
): String {
    val received = crystal.received
    return stringResource(
        R.string.board_cell_crystal,
        colorName(crystal.required),
        if (received == null) stringResource(R.string.board_crystal_receives_nothing) else colorName(received),
        stringResource(fillStateRes(crystal.fill)),
        position,
    )
}

@StringRes
private fun staticNameRes(element: Element?): Int =
    when (element) {
        is Element.Wall -> R.string.board_element_wall
        is Element.Prism -> R.string.board_element_prism
        else -> R.string.board_element_empty
    }

@StringRes
private fun fillStateRes(fill: CrystalFill): Int =
    when (fill) {
        CrystalFill.DARK -> R.string.board_crystal_state_dark
        CrystalFill.PARTIAL -> R.string.board_crystal_state_partial
        CrystalFill.FULFILLED -> R.string.board_crystal_state_fulfilled
        CrystalFill.OVERSATURATED -> R.string.board_crystal_state_oversaturated
    }

/** Farbname §3.1/§13.5 — Mischfarben nennen ihre Komponenten. */
@Composable
private fun colorName(color: LightColor): String =
    stringResource(
        when (color.bits) {
            LightColor.RED.bits -> R.string.board_color_red
            LightColor.GREEN.bits -> R.string.board_color_green
            LightColor.YELLOW.bits -> R.string.board_color_yellow
            LightColor.BLUE.bits -> R.string.board_color_blue
            LightColor.MAGENTA.bits -> R.string.board_color_magenta
            LightColor.CYAN.bits -> R.string.board_color_cyan
            else -> R.string.board_color_white
        },
    )

@Composable
private fun directionName(direction: Direction): String =
    stringResource(
        when (direction) {
            Direction.EAST -> R.string.board_direction_east
            Direction.NORTH_EAST -> R.string.board_direction_north_east
            Direction.NORTH_WEST -> R.string.board_direction_north_west
            Direction.WEST -> R.string.board_direction_west
            Direction.SOUTH_WEST -> R.string.board_direction_south_west
            Direction.SOUTH_EAST -> R.string.board_direction_south_east
        },
    )

@Composable
private fun portalPairName(pairId: Int): String =
    stringResource(if (pairId == 0) R.string.board_portal_pair_a else R.string.board_portal_pair_b)
