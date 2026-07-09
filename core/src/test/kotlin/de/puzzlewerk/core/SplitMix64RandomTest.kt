package de.puzzlewerk.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Golden-Tests nach ADR-003: Die Referenzwerte stammen aus einer unabhängigen
 * Implementierung des SplitMix64-Pseudocodes (docs/game-design.md §8). Schlägt einer
 * dieser Tests an, hat sich die normative Sequenz geändert — das ist IMMER ein Fehler.
 */
class SplitMix64RandomTest {
    @Test
    fun `Golden - Seed 0 liefert die publizierte Referenzsequenz`() {
        val random = SplitMix64Random(seed = 0L)

        // 0xE220A8397B1DCDAF, 0x6E789E6AA1B965F4, 0x06C45D188009454F, 0xF88BB8A8724C81EC
        random.nextLong() shouldBe -2152535657050944081L
        random.nextLong() shouldBe 7960286522194355700L
        random.nextLong() shouldBe 487617019471545679L
        random.nextLong() shouldBe -537132696929009172L
    }

    @Test
    fun `Golden - Seed 42 liefert die erwartete Sequenz`() {
        val random = SplitMix64Random(seed = 42L)

        random.nextLong() shouldBe -4767286540954276203L
        random.nextLong() shouldBe 2949826092126892291L
        random.nextLong() shouldBe 5139283748462763858L
        random.nextLong() shouldBe 6349198060258255764L
    }

    @Test
    fun `Golden - negativer Seed funktioniert ohne Sonderfall`() {
        val random = SplitMix64Random(seed = -1L)

        random.nextLong() shouldBe -1956407806741107680L
        random.nextLong() shouldBe -1612297016619662647L
    }

    @Test
    fun `Golden - nextInt nutzt floorMod-Semantik (Design Paragraf 8)`() {
        val random = SplitMix64Random(seed = 42L)

        val values = List(8) { random.nextInt(untilExclusive = 6) }

        values shouldBe listOf(3, 1, 0, 0, 4, 2, 1, 4)
    }

    @Test
    fun `gleicher Seed erzeugt identische Sequenzen`() {
        val first = SplitMix64Random(seed = 20643L)
        val second = SplitMix64Random(seed = 20643L)

        List(100) { first.nextLong() } shouldBe List(100) { second.nextLong() }
    }

    @Test
    fun `nextInt bleibt auch bei negativen Rohwerten im Wertebereich`() {
        val random = SplitMix64Random(seed = 0L)

        repeat(1000) {
            val value = random.nextInt(untilExclusive = 7)
            value shouldBeGreaterThanOrEqual 0
            value shouldBeLessThan 7
        }
    }

    @Test
    fun `nextInt mit Grenze 1 liefert immer 0`() {
        val random = SplitMix64Random(seed = 99L)

        List(10) { random.nextInt(untilExclusive = 1) } shouldBe List(10) { 0 }
    }

    @Test
    fun `nextInt mit ungueltiger Grenze ist ein Programmierfehler`() {
        val random = SplitMix64Random(seed = 1L)

        shouldThrow<IllegalArgumentException> { random.nextInt(untilExclusive = 0) }
        shouldThrow<IllegalArgumentException> { random.nextInt(untilExclusive = -3) }
    }
}
