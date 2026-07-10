package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unabhaengige adversariale Tests (QS-Pass PW-2.2-QS), direkt aus dem
 * Design-Dokument abgeleitet (Paragraf 2, 4, 5 und Randfaelle Paragraf 15):
 * handverifizierte 36er-Reflexionsmatrix fuer Spiegel UND Splitter,
 * FIFO-/Enqueue-Ordnung als exakte Golden-Segmentlisten, Portal-Aufloesung
 * bei zwei Paaren sowie mehrstufig verkettete visited-Farbsemantik (R18).
 */
class TracerAdversarialTest {
    /**
     * Handverifizierte Reflexionstabelle `dOutTable[m][dIn]` (Paragraf 4_2):
     * Spiegelung des Richtungswinkels `dIn * 60` Grad an der Spiegelgeraden
     * `m * 30` Grad. Bewusst als Literal-Tabelle notiert, NICHT ueber die
     * Formel berechnet — faengt Vorzeichendreher (`dIn - m` statt `m - dIn`)
     * und Modulo-Fehler (`%` statt floorMod bei negativen Werten).
     * Zeilen: m = 0..5, Spalten: dIn = 0..5.
     */
    private val dOutTable =
        listOf(
            listOf(0, 5, 4, 3, 2, 1),
            listOf(1, 0, 5, 4, 3, 2),
            listOf(2, 1, 0, 5, 4, 3),
            listOf(3, 2, 1, 0, 5, 4),
            listOf(4, 3, 2, 1, 0, 5),
            listOf(5, 4, 3, 2, 1, 0),
        )

    /** Richtungs-Deltas aus der Tabelle Paragraf 2_2, unabhaengig vom Direction-Enum notiert. */
    private val deltas = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    private val origin = HexCoord(0, 0)
    private val cyan = LightColor.CYAN
    private val red = LightColor.RED

    /** Zelle, von der aus ein Strahl das Zentrum in Laufrichtung [dIn] betritt. */
    private fun entryCell(dIn: Int) = HexCoord(-deltas[dIn].first, -deltas[dIn].second)

    /** Nachbarzelle des Zentrums in Richtung [dOut]. */
    private fun exitCell(dOut: Int) = HexCoord(deltas[dOut].first, deltas[dOut].second)

    @Test
    fun `Spiegel-36er-Matrix - jede Orientierung aus jeder Richtung gegen die Handtabelle (Paragraf 4_2)`() {
        for (m in 0..5) {
            for (dIn in 0..5) {
                val result =
                    trace(
                        boardOf(
                            2,
                            entryCell(dIn) to Element.Source(cyan, Direction.fromIndex(dIn)),
                            origin to Element.Mirror(Orientation(m)),
                        ),
                    )

                withClue("Spiegel m=$m, d_in=$dIn") {
                    result.segments[0] shouldBe Segment(entryCell(dIn), origin, cyan)
                    result.segments[1] shouldBe Segment(origin, exitCell(dOutTable[m][dIn]), cyan)
                }
            }
        }
    }

    @Test
    fun `Splitter-36er-Matrix - Transmission vor Reflexion, Parallelfall ohne Kopie (Paragraf 4_3, R05)`() {
        for (m in 0..5) {
            for (dIn in 0..5) {
                val result =
                    trace(
                        boardOf(
                            2,
                            entryCell(dIn) to Element.Source(cyan, Direction.fromIndex(dIn)),
                            origin to Element.Splitter(Orientation(m)),
                        ),
                    )

                val dOut = dOutTable[m][dIn]
                val expectedExits = if (dOut == dIn) listOf(dIn) else listOf(dIn, dOut)
                withClue("Splitter m=$m, d_in=$dIn") {
                    result.segments.filter { it.from == origin } shouldBe
                        expectedExits.map { Segment(origin, exitCell(it), cyan) }
                }
            }
        }
    }

    @Test
    fun `FIFO-Ordnung - zwei Quellen erzeugen streng alternierende Segmente (Paragraf 5_2)`() {
        val green = LightColor.GREEN
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(-2, 1) to Element.Source(green, Direction.EAST),
                ),
            )

        // FIFO-Queue (BFS): die Wellenfronten beider Quellen wechseln sich ab.
        // Eine Stack-Implementierung (DFS) wuerde erst eine Quelle komplett abarbeiten.
        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-2, 1, -1, 1, green),
                seg(-1, 0, 0, 0, red),
                seg(-1, 1, 0, 1, green),
                seg(0, 0, 1, 0, red),
                seg(0, 1, 1, 1, green),
                seg(1, 0, 2, 0, red),
            )
    }

    @Test
    fun `Prisma-Tiefenordnung - R G B faechern auf und bleiben ueber alle Wellen interleaved (Paragraf 5_2)`() {
        val white = LightColor.WHITE
        val green = LightColor.GREEN
        val blue = LightColor.BLUE
        val result =
            trace(
                boardOf(
                    3,
                    HexCoord(-3, 0) to Element.Source(white, Direction.EAST),
                    origin to Element.Prism,
                ),
            )

        // Falsche Enqueue-Ordnung (z. B. G vor R oder B vor G) oder DFS statt
        // FIFO ergaebe eine andere Segmentliste ab Index 3.
        result.segments shouldBe
            listOf(
                seg(-3, 0, -2, 0, white),
                seg(-2, 0, -1, 0, white),
                seg(-1, 0, 0, 0, white),
                seg(0, 0, 1, -1, red),
                seg(0, 0, 1, 0, green),
                seg(0, 0, 0, 1, blue),
                seg(1, -1, 2, -2, red),
                seg(1, 0, 2, 0, green),
                seg(0, 1, 0, 2, blue),
                seg(2, -2, 3, -3, red),
                seg(2, 0, 3, 0, green),
                seg(0, 2, 0, 3, blue),
            )
    }

    @Test
    fun `Zwei Portalpaare - Strahl nimmt den Zwilling mit GLEICHER Paar-ID (Paragraf 4_6)`() {
        // Paar 0 steht bewusst ZUERST in der Map und abseits des Strahlwegs:
        // eine Zwillingssuche ohne Paar-ID-Abgleich wuerde dorthin teleportieren.
        val result =
            trace(
                boardOf(
                    3,
                    HexCoord(0, 2) to Element.Portal(0),
                    HexCoord(1, 1) to Element.Portal(0),
                    HexCoord(-3, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(-1, 0) to Element.Portal(1),
                    HexCoord(1, -2) to Element.Portal(1),
                    HexCoord(3, -2) to Element.Crystal(red),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-3, 0, -2, 0, red),
                seg(-2, 0, -1, 0, red),
                seg(1, -2, 2, -2, red),
                seg(2, -2, 3, -2, red),
            )
        result.received shouldBe mapOf(HexCoord(3, -2) to red)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `Portal-Zwilling direkt neben Kristall - Teleport fuettert den Kristall (Paragraf 4_6, R20)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(-1, 0) to Element.Portal(0),
                    HexCoord(1, 0) to Element.Portal(0),
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        // Kein Segment auf (0,0): der Strahl ueberspringt die Strecke zwischen
        // den Zwillingen; der Austritt trifft den Kristall unmittelbar.
        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(1, 0, 2, 0, red),
            )
        result.received shouldBe mapOf(HexCoord(2, 0) to red)
        result.solved.shouldBeTrue()
    }

    private fun visitedChainBoard() =
        boardOf(
            3,
            // Gelb wird am Rot-Filter zu Rot; Gruen wird per Splitter (m=2) auf
            // dieselbe Ost-Linie reflektiert. Ab (-1,0) existieren Zustaende,
            // die sich NUR in der Farbe unterscheiden — durch Portal verkettet.
            HexCoord(-3, 0) to Element.Source(LightColor.YELLOW, Direction.EAST),
            HexCoord(-2, 0) to Element.Filter(red),
            HexCoord(-1, 2) to Element.Source(LightColor.GREEN, Direction.NORTH_WEST),
            HexCoord(-1, 0) to Element.Splitter(Orientation(2)),
            HexCoord(1, 0) to Element.Portal(0),
            HexCoord(1, 1) to Element.Portal(0),
            HexCoord(2, 1) to Element.Crystal(LightColor.YELLOW),
        )

    @Test
    fun `Visited-Farbsemantik mehrstufig - Filter, Splitter, Portal, gleiche Kanten (R18 verkettet)`() {
        val green = LightColor.GREEN
        val result = trace(visitedChainBoard())

        // Ein visited-Set OHNE Farbe wuerde die zweite Farbe auf jeder der
        // geteilten Kanten verwerfen und der Kristall bliebe teilerfuellt.
        result.segments shouldBe
            listOf(
                seg(-3, 0, -2, 0, LightColor.YELLOW),
                seg(-1, 2, -1, 1, green),
                seg(-2, 0, -1, 0, red),
                seg(-1, 1, -1, 0, green),
                seg(-1, 0, 0, 0, red),
                seg(-1, 0, -1, -1, red),
                seg(-1, 0, -1, -1, green),
                seg(-1, 0, 0, 0, green),
                seg(0, 0, 1, 0, red),
                seg(-1, -1, -1, -2, red),
                seg(-1, -1, -1, -2, green),
                seg(0, 0, 1, 0, green),
                seg(1, 1, 2, 1, red),
                seg(1, 1, 2, 1, green),
            )
        result.received shouldBe mapOf(HexCoord(2, 1) to LightColor.YELLOW)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `Filter vereinigt Farbzustaende - stromabwaerts genau einmal beleuchtet (Paragraf 5_3)`() {
        // Gelb und Rot laufen auf derselben Ost-Linie in den Rot-Filter; beide
        // werden zu (0,0)-Ost-Rot. Der zweite identische Zustand MUSS am
        // visited-Set enden — sonst waeren die Folgekanten doppelt beleuchtet.
        val result =
            trace(
                boardOf(
                    3,
                    HexCoord(-3, 0) to Element.Source(LightColor.YELLOW, Direction.EAST),
                    HexCoord(-1, 2) to Element.Source(red, Direction.NORTH_WEST),
                    HexCoord(-1, 0) to Element.Splitter(Orientation(2)),
                    origin to Element.Filter(red),
                ),
            )

        result.segments.filter { it.to == origin } shouldBe
            listOf(
                seg(-1, 0, 0, 0, LightColor.YELLOW),
                seg(-1, 0, 0, 0, red),
            )
        result.segments.filter { it.from == origin } shouldBe listOf(seg(0, 0, 1, 0, red))
        result.segments.count { it.from == HexCoord(1, 0) } shouldBe 1
        result.segments.count { it.from == HexCoord(2, 0) } shouldBe 1
    }
}
