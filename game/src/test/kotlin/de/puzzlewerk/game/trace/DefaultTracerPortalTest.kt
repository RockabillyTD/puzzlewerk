package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Portal- und Strahlkreuzungs-Randfaelle R14-R18 (Design Paragraf 15). */
class DefaultTracerPortalTest {
    private val red = LightColor.RED
    private val green = LightColor.GREEN

    @Test
    fun `R14 - Portal-Schleife terminiert ueber das visited-Set`() {
        // Spiegel an beiden Enden, Splitter speist die Schleife: der Strahl kreist
        // durch das Portalpaar, bis alle Zustaende besucht sind.
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-1, 2) to Element.Source(red, Direction.NORTH_WEST),
                    HexCoord(-2, 0) to Element.Mirror(Orientation(3)),
                    HexCoord(-1, 0) to Element.Splitter(Orientation(2)),
                    HexCoord(0, 0) to Element.Portal(0),
                    HexCoord(1, 0) to Element.Portal(0),
                    HexCoord(2, 0) to Element.Mirror(Orientation(3)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-1, 2, -1, 1, red),
                seg(-1, 1, -1, 0, red),
                seg(-1, 0, -1, -1, red),
                seg(-1, 0, 0, 0, red),
                seg(1, 0, 2, 0, red),
                seg(2, 0, 1, 0, red),
                seg(0, 0, -1, 0, red),
                seg(-1, 0, -2, 0, red),
                seg(-1, 0, -1, 1, red),
                seg(-2, 0, -1, 0, red),
                seg(-1, 1, -1, 2, red),
            )
        result.received.shouldBeEmpty()
    }

    @Test
    fun `R15 - benachbarte Portal-Zwillinge, Strahl pendelt und terminiert`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Portal(0),
                    HexCoord(1, 0) to Element.Portal(0),
                    HexCoord(2, 0) to Element.Mirror(Orientation(3)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, LightColor.WHITE),
                seg(-1, 0, 0, 0, LightColor.WHITE),
                seg(1, 0, 2, 0, LightColor.WHITE),
                seg(2, 0, 1, 0, LightColor.WHITE),
                seg(0, 0, -1, 0, LightColor.WHITE),
                seg(-1, 0, -2, 0, LightColor.WHITE),
            )
    }

    @Test
    fun `R16 - Strahl verlaesst Portal-Zwilling direkt ins Brett-Aus`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Portal(1),
                    HexCoord(2, 0) to Element.Portal(1),
                ),
            )

        // Nach dem Teleport zeigt der Strahl von (2,0) nach Ost direkt ins Aus
        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
            )
    }

    @Test
    fun `Portale sind symmetrisch und erhalten Richtung und Farbe (Paragraf 4_6)`() {
        val pair = listOf(HexCoord(0, 0), HexCoord(0, 3))
        for (direction in Direction.entries) {
            // Quelle zwei Zellen vor dem ersten Zwilling, Strahl betritt (0,0)
            val sourceCell = HexCoord(0, 0).neighbor(direction.opposite).neighbor(direction.opposite)
            val result =
                trace(
                    boardOf(
                        3,
                        sourceCell to Element.Source(LightColor.CYAN, direction),
                        pair[0] to Element.Portal(0),
                        pair[1] to Element.Portal(0),
                    ),
                )

            val exit = HexCoord(0, 3).neighbor(direction)
            if (exit.isWithinRadius(3)) {
                result.segments shouldContain Segment(HexCoord(0, 3), exit, LightColor.CYAN)
            } else {
                result.segments.none { it.from == HexCoord(0, 3) }.shouldBeTrue()
            }
            // Kein Strahl laeuft durch die Portalzelle hindurch
            result.segments.none { it.from == HexCoord(0, 0) }.shouldBeTrue()
        }
    }

    @Test
    fun `R17 - Strahlen kreuzen sich in einer leeren Zelle ohne Interaktion`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 2) to Element.Source(green, Direction.NORTH_WEST),
                    HexCoord(2, 0) to Element.Crystal(red),
                    HexCoord(0, -2) to Element.Crystal(green),
                ),
            )

        // Beide Strahlen kreuzen (0,0), mischen sich aber nicht
        result.received shouldBe
            mapOf(
                HexCoord(2, 0) to red,
                HexCoord(0, -2) to green,
            )
        result.solved.shouldBeTrue()
        result.segments.none { it.color == LightColor.YELLOW }.shouldBeTrue()
    }

    @Test
    fun `R18 - gleiche Zelle und Richtung mit anderer Farbe sind eigene Zustaende`() {
        // Ein Splitter fuehrt beide Quellstrahlen auf dieselbe Kante nach Ost
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 2) to Element.Source(green, Direction.NORTH_WEST),
                    HexCoord(0, 0) to Element.Splitter(Orientation(2)),
                    HexCoord(2, 0) to Element.Crystal(LightColor.YELLOW),
                ),
            )

        // Dieselbe Kante (0,0) -> (1,0) wird von Rot UND Gruen durchlaufen
        result.segments.filter { it.from == HexCoord(0, 0) && it.to == HexCoord(1, 0) } shouldBe
            listOf(
                seg(0, 0, 1, 0, red),
                seg(0, 0, 1, 0, green),
            )
        result.received shouldBe mapOf(HexCoord(2, 0) to LightColor.YELLOW)
        result.solved.shouldBeTrue()
    }
}
