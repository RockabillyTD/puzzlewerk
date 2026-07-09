package de.puzzlewerk.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Golden-Tests für den mix64-Finalizer (ADR-003, docs/game-design.md §8) —
 * Grundlage der Seed-Ableitung für Daily (§10.1) und Kampagne (§11.1).
 */
class Mix64Test {
    @Test
    fun `Golden - bekannte Eingaben liefern gepinnte Ausgaben`() {
        mix64(0L) shouldBe 0L
        mix64(1L) shouldBe 6238072747940578789L
        mix64(42L) shouldBe -6387817139659442654L
        mix64(-1L) shouldBe -5417735806833148549L
    }

    @Test
    fun `mix64 ist eine pure Funktion (gleiche Eingabe, gleiche Ausgabe)`() {
        mix64(20643L) shouldBe mix64(20643L)
    }
}
