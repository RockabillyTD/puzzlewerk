package de.puzzlewerk.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * `StemMix.forProgress` gegen die Tabelle §13.11 — jede Schwelle BEIDSEITIG
 * (letzter Wert davor, erster Wert danach), dazu R50 (K ≤ 2) und R46
 * (abwärts identisch, keine Hysterese — pure Funktion ohne Gedächtnis).
 */
class StemMixProgressTest {
    private fun mix(
        fulfilled: Int,
        total: Int,
    ): StemMix = StemMix.forProgress(fulfilled, total)

    @Test
    fun `Ebene 1 laeuft immer, ohne Fortschritt sonst Stille`() {
        assertEquals(StemMix(1f, 0f, 0f, 0f), mix(0, 8))
        assertEquals(StemMix.BASE, mix(0, 1))
    }

    @Test
    fun `Ebene 2 beidseitig - aus bei L gleich 0, an ab dem ersten Kristall`() {
        assertEquals(0f, mix(0, 8).stem2Kalimba, 0f)
        assertEquals(1f, mix(1, 8).stem2Kalimba, 0f)
    }

    @Test
    fun `Ebene 3 beidseitig um die 50-Prozent-Schwelle (gerades K)`() {
        assertEquals(0f, mix(3, 8).stem3Bass, 0f) // 37,5 % < 50 %
        assertEquals(1f, mix(4, 8).stem3Bass, 0f) // exakt 50 %
    }

    @Test
    fun `Ebene 3 beidseitig bei ungeradem K - 2L muss K erreichen`() {
        assertEquals(0f, mix(2, 5).stem3Bass, 0f) // 40 % < 50 %
        assertEquals(1f, mix(3, 5).stem3Bass, 0f) // 60 % >= 50 %
    }

    @Test
    fun `Ebene 4 beidseitig - erst beim letzten fehlenden Kristall`() {
        assertEquals(0f, mix(6, 8).stem4Modern, 0f)
        assertEquals(1f, mix(7, 8).stem4Modern, 0f)
        assertEquals(1f, mix(8, 8).stem4Modern, 0f)
    }

    @Test
    fun `R50 - K gleich 2 aktiviert die Ebenen 2 bis 4 gemeinsam bei L gleich 1`() {
        assertEquals(StemMix(1f, 0f, 0f, 0f), mix(0, 2))
        assertEquals(StemMix(1f, 1f, 1f, 1f), mix(1, 2))
    }

    @Test
    fun `R50 - K gleich 1 aktiviert die Ebenen 2 bis 4 erst mit der Loesung`() {
        assertEquals(StemMix(1f, 0f, 0f, 0f), mix(0, 1))
        assertEquals(StemMix(1f, 1f, 1f, 1f), mix(1, 1))
    }

    @Test
    fun `R46 - abwaerts liefert dieselben Werte wie aufwaerts (pure Funktion)`() {
        val up = (0..8).map { mix(it, 8) }
        val down = (8 downTo 0).map { mix(it, 8) }
        assertEquals(up, down.reversed())
    }

    @Test
    fun `ungueltige Eingaben sind Programmierfehler`() {
        assertThrows(IllegalArgumentException::class.java) { mix(0, 0) }
        assertThrows(IllegalArgumentException::class.java) { mix(-1, 3) }
        assertThrows(IllegalArgumentException::class.java) { mix(4, 3) }
    }
}
