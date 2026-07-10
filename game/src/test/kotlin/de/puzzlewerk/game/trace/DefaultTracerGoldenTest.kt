package de.puzzlewerk.game.trace

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.color.LightColor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pflicht-Golden-Test: Beispiel-Level "Drei Farben" aus Design Paragraf 7.3.
 * Alle drei Trace-Laeufe (Start m=5, nach Zug 1 m=0, nach Zug 2 m=1) mit
 * exakten Segmentlisten und received-Maps — byte-identisch reproduzierbar
 * (normative Enqueue-Reihenfolgen, Paragraf 5.2).
 */
class DefaultTracerGoldenTest {
    private val white = LightColor.WHITE

    @Test
    fun `Startzustand m=5 - Strahl laeuft nach Suedost ins Aus, alle Kristalle dunkel`() {
        val result = trace(DreiFarbenLevel.board(mirrorSteps = 5))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, white),
                seg(-1, 0, 0, 0, white),
                seg(0, 0, 0, 1, white),
                seg(0, 1, 0, 2, white),
            )
        result.received.shouldBeEmpty()
        result.solved.shouldBeFalse()
    }

    @Test
    fun `Nach Zug 1 m=0 - Parallelfall, Strahl laeuft geradeaus ins Aus`() {
        val result = trace(DreiFarbenLevel.board(mirrorSteps = 0))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, white),
                seg(-1, 0, 0, 0, white),
                seg(0, 0, 1, 0, white),
                seg(1, 0, 2, 0, white),
            )
        result.received.shouldBeEmpty()
        result.solved.shouldBeFalse()
    }

    @Test
    fun `Nach Zug 2 m=1 - Prisma zerlegt Weiss, alle drei Kristalle exakt erfuellt`() {
        val result = trace(DreiFarbenLevel.board(mirrorSteps = 1))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, white),
                seg(-1, 0, 0, 0, white),
                seg(0, 0, 1, -1, white),
                // Prisma-Reihenfolge R, G, B (Paragraf 5.2)
                seg(1, -1, 1, -2, LightColor.RED),
                seg(1, -1, 2, -2, LightColor.GREEN),
                seg(1, -1, 2, -1, LightColor.BLUE),
            )
        result.received shouldBe
            mapOf(
                DreiFarbenLevel.redCrystal to LightColor.RED,
                DreiFarbenLevel.greenCrystal to LightColor.GREEN,
                DreiFarbenLevel.blueCrystal to LightColor.BLUE,
            )
        result.solved.shouldBeTrue()
    }

    @Test
    fun `Die uebrigen Spiegel-Orientierungen loesen das Level nicht (Par-Argument aus 7_3)`() {
        for (steps in listOf(2, 3, 4)) {
            trace(DreiFarbenLevel.board(mirrorSteps = steps)).solved.shouldBeFalse()
        }
    }
}
