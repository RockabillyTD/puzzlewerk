package de.puzzlewerk.game.element

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class ElementTest {
    @Test
    fun `nur Spiegel und Splitter sind drehbar (Paragraf 4)`() {
        Element.Mirror(Orientation.ZERO).isRotatable.shouldBeTrue()
        Element.Splitter(Orientation.ZERO).isRotatable.shouldBeTrue()

        Element.Source(LightColor.WHITE, Direction.EAST).isRotatable.shouldBeFalse()
        Element.Prism.isRotatable.shouldBeFalse()
        Element.Filter(LightColor.RED).isRotatable.shouldBeFalse()
        Element.Portal(pairId = 0).isRotatable.shouldBeFalse()
        Element.Crystal(LightColor.YELLOW).isRotatable.shouldBeFalse()
        Element.Wall.isRotatable.shouldBeFalse()
    }

    @Test
    fun `withOrientation liefert eine Kopie mit neuer Orientierung`() {
        val mirror = Element.Mirror(Orientation(5))
        val splitter = Element.Splitter(Orientation(1))

        val rotatedMirror = mirror.withOrientation(Orientation(1))
        val rotatedSplitter = splitter.withOrientation(Orientation(2))

        rotatedMirror shouldBe Element.Mirror(Orientation(1))
        rotatedSplitter shouldBe Element.Splitter(Orientation(2))
        // Eingaben bleiben unveraendert (pure Werte, Regel C1)
        mirror.orientation shouldBe Orientation(5)
        splitter.orientation shouldBe Orientation(1)
    }

    @Test
    fun `withOrientation erhaelt den konkreten Typ`() {
        val rotatable: Element.Rotatable = Element.Splitter(Orientation.ZERO)

        rotatable.withOrientation(Orientation(3)).shouldBeInstanceOf<Element.Splitter>()
    }

    @Test
    fun `Elemente sind Werte mit struktureller Gleichheit`() {
        Element.Source(LightColor.WHITE, Direction.EAST) shouldBe
            Element.Source(LightColor.WHITE, Direction.EAST)
        Element.Crystal(LightColor.RED) shouldNotBe Element.Crystal(LightColor.BLUE)
        Element.Portal(pairId = 0) shouldNotBe Element.Portal(pairId = 1)
        Element.Prism shouldBe Element.Prism
        Element.Wall shouldBe Element.Wall
        Element.Prism.toString() shouldBe "Prism"
        Element.Wall.toString() shouldBe "Wall"
    }

    @Test
    fun `Spiegel und Splitter mit gleicher Orientierung sind verschiedene Elemente`() {
        val mirror: Element = Element.Mirror(Orientation.ZERO)
        val splitter: Element = Element.Splitter(Orientation.ZERO)

        mirror shouldNotBe splitter
    }
}
