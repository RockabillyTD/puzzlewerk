package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TraceApiTest {
    @Test
    fun `Segment und TraceResult sind Werte`() {
        val segment = Segment(from = HexCoord(0, 0), to = HexCoord(1, 0), color = LightColor.RED)

        segment shouldBe Segment(HexCoord(0, 0), HexCoord(1, 0), LightColor.RED)
        segment.copy(color = LightColor.BLUE).color shouldBe LightColor.BLUE

        val result =
            TraceResult(
                segments = listOf(segment),
                received = mapOf(HexCoord(1, 0) to LightColor.RED),
                solved = true,
                endpoints = listOf(BeamEndpoint(HexCoord(1, 0), LightColor.RED)),
            )

        result shouldBe result.copy()
        result.segments.size shouldBe 1
    }

    @Test
    fun `fehlender received-Eintrag bedeutet dunkler Kristall (Paragraf 5_4)`() {
        val result = TraceResult(segments = emptyList(), received = emptyMap(), solved = false, endpoints = emptyList())

        result.received shouldNotContainKey HexCoord(0, 0)
        result.received[HexCoord(0, 0)] shouldBe null
    }

    @Test
    fun `MAX_TRACE_STEPS liegt ueber dem maximalen Zustandsraum (Paragraf 5_3)`() {
        // 91 Zellen x 6 Richtungen x 7 Farben = 3822 < 4000
        val maxStates = Board.cellCount(Board.MAX_RADIUS) * 6 * 7

        maxStates shouldBe 3822
        MAX_TRACE_STEPS shouldBe 4000
    }

    @Test
    fun `Tracer ist als fun interface implementierbar`() {
        val empty = TraceResult(emptyList(), emptyMap(), solved = false, endpoints = emptyList())
        val tracer = Tracer { _ -> empty }

        tracer.trace(Board(radius = 2, elements = emptyMap())) shouldBe empty
    }
}
