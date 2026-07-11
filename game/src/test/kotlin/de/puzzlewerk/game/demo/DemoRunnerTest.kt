package de.puzzlewerk.game.demo

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.generator.DefaultLevelGenerator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

private const val DEMO_SEED = 20260711L

/**
 * CLI-Demo-Runner (PW-2.7, Phase-2-Gate): Arg-Validierung (S4-Geist),
 * deterministischer Report, Brett-Rendering und Engine-Abspielen der
 * gefundenen Loesung inklusive Score/Sterne (Design §7.2).
 */
class DemoRunnerTest {
    // --- Arg-Validierung (definierte Fehlermeldung statt Crash) ---

    @Test
    fun `ohne Argumente laufen Default-Seed 20260711 und Tier D2`() {
        val report = runDemo(emptyList())
        report shouldContain "Seed 20260711"
        report shouldContain "Tier D2"
    }

    @Test
    fun `parseDemoArgs liefert Seed und Tier typisiert zurueck - negative Seeds sind gueltig`() {
        parseDemoArgs(listOf("-5", "D7")) shouldBe DemoArgs.Valid(seed = -5L, tier = Difficulty.D7)
    }

    @Test
    fun `ungueltiger Seed liefert definierte Fehlermeldung statt Crash`() {
        val report = runDemo(listOf("abc"))
        report shouldContain "Ungueltiger Seed 'abc'"
        report shouldContain "Nutzung:"
    }

    @Test
    fun `ungueltiges Tier liefert definierte Fehlermeldung statt Crash`() {
        runDemo(listOf("1", "D9")) shouldContain "Ungueltiges Tier 'D9'"
    }

    @Test
    fun `Tier-Validierung ist strikt - Kleinschreibung wird abgelehnt`() {
        runDemo(listOf("1", "d2")) shouldContain "Ungueltiges Tier 'd2'"
    }

    @Test
    fun `zu viele Argumente werden abgelehnt`() {
        runDemo(listOf("1", "D2", "extra")) shouldContain "Zu viele Argumente (3)"
    }

    @Test
    fun `explizite Argumente werden uebernommen`() {
        val report = runDemo(listOf("42", "D1"))
        report shouldContain "Seed 42"
        report shouldContain "Tier D1"
    }

    // --- Gate-Kriterium: Demo loest ein generiertes Level ---

    @Test
    fun `Demo loest Level fuer Tier D1 bis D3 mit Par Zuegen und voller Wertung`() {
        for (tier in listOf(Difficulty.D1, Difficulty.D2, Difficulty.D3)) {
            val par = DefaultLevelGenerator.generate(DEMO_SEED, tier).par
            val report = runDemo(listOf(DEMO_SEED.toString(), tier.name))
            report shouldContain "Validator: OK"
            report shouldContain "Geloest: true | Zuege: $par | Par: $par | Score: 1500 | Sterne: 3"
        }
    }

    @Test
    fun `Report protokolliert jeden Zug einzeln als Rotate`() {
        runDemo(listOf(DEMO_SEED.toString(), "D1")) shouldContain "Zug 1: Rotate(q="
    }

    @Test
    fun `Report ist deterministisch - gleiche Argumente ergeben identische Ausgabe`() {
        runDemo(listOf("7", "D2")) shouldBe runDemo(listOf("7", "D2"))
    }

    @Test
    fun `solveReport meldet definierten Fehler wenn bis Par keine Loesung existiert`() {
        // Drei-Farben-Level (§7.3) braucht 2 Zuege; ein absichtlich falsches
        // Par von 1 begrenzt die Suche und erzwingt den Fehlerpfad
        val level = LevelDefinition(board = DreiFarbenLevel.board(), par = 1, tier = Difficulty.D1, seed = 0L)
        solveReport(level).single() shouldContain "keine Loesung"
    }

    // --- Brett-Rendering ---

    @Test
    fun `renderBoard rendert eine Zeile je r und alle Element-Kuerzel`() {
        val rows = renderBoard(allElementsBoard())
        rows.size shouldBe 5
        val joined = rows.joinToString("\n")
        joined shouldContain "S0RGB" // Quelle Weiss, Richtung 0
        joined shouldContain "M3" // Spiegel m=3
        joined shouldContain "T1" // Splitter m=1
        joined shouldContain "P" // Prisma
        joined shouldContain "FR" // Filter Rot
        joined shouldContain "O0" // Portal Paar 0
        joined shouldContain "KRG" // Kristall Soll Gelb = R+G
        joined shouldContain "W" // Wand
        joined shouldContain "." // leere Zelle
    }

    // --- main-Wrapper ---

    @Test
    fun `main druckt den vollstaendigen Report auf stdout`() {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true, Charsets.UTF_8))
        try {
            main(arrayOf(DEMO_SEED.toString(), "D1"))
        } finally {
            System.setOut(original)
        }
        buffer.toString(Charsets.UTF_8) shouldContain "Geloest: true"
    }

    /** Radius-2-Brett mit allen acht Elementtypen fuer die Legenden-Pruefung. */
    private fun allElementsBoard(): Board =
        Board(
            radius = 2,
            elements =
                mapOf(
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(-1, 0) to Element.Mirror(Orientation(3)),
                    HexCoord(0, 0) to Element.Splitter(Orientation(1)),
                    HexCoord(1, 0) to Element.Prism,
                    HexCoord(2, 0) to Element.Filter(LightColor.RED),
                    HexCoord(0, -2) to Element.Portal(pairId = 0),
                    HexCoord(1, -2) to Element.Portal(pairId = 0),
                    HexCoord(2, -2) to Element.Crystal(LightColor.YELLOW),
                    HexCoord(0, 2) to Element.Wall,
                ),
        )
}
