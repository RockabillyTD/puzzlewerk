package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * PW-4.9-QS, Pflichtpunkt 1 (§13.13, ADR-011): Determinismus-Property über
 * 1000 Frames — gleiche Seeds + gleiche Event-Folge ⇒ identische
 * JuiceState-FOLGE (jeder Frame, nicht nur der Endzustand), inklusive Glow,
 * Emitter, geplanter Bursts, Flash und Puls. Die Event-Folge deckt ALLE
 * Event-Typen ab (auch Mid-Session-Reduce-Motion-Umschaltung und Dismissed).
 * Reine JVM-`step`-Aufrufe mit fester dt-Folge, feste Seeds, keine Uhr.
 */
class JuiceDeterminismQsTest {
    private data class ScriptFrame(
        val events: List<JuiceEvent>,
        val dtMillis: Long,
    )

    /** Deterministisch aus festem Seed erzeugtes 1000-Frame-Drehbuch mit allen Event-Typen. */
    private fun script(scriptSeed: Long): List<ScriptFrame> {
        val rng = Random(scriptSeed)
        return List(FRAME_COUNT) { frame ->
            val events =
                buildList {
                    if (frame == 0) add(JuiceEvent.ScreenEntered(LEVEL_SEED, false, endpoints(rng)))
                    when (rng.nextInt(12)) {
                        0 -> add(JuiceEvent.RotateFlash(rng.nextFloat() * AREA, rng.nextFloat() * AREA, 60f))
                        1 -> addAll(comboWithSolve(frame, rng))
                        2 -> add(JuiceEvent.EndpointsChanged(frame, endpoints(rng)))
                        3 -> add(JuiceEvent.MotionPreferenceChanged(reduceMotion = rng.nextBoolean()))
                        4 -> add(JuiceEvent.Dismissed)
                        else -> Unit // reiner Integrations-Frame
                    }
                }
            ScriptFrame(events, dtMillis = rng.nextLong(1L, 34L))
        }
    }

    /** Kaskade + Solved im SELBEN Frame mit gleicher zugNummer (Kontrakt an [JuiceEvent.Solved]). */
    private fun comboWithSolve(
        moveNumber: Int,
        rng: Random,
    ): List<JuiceEvent> {
        val bursts = List(rng.nextInt(1, 7)) { BurstOrigin(it.toFloat(), it.toFloat(), PALETTE[it % PALETTE.size]) }
        val combo = JuiceEvent.CrystalBursts(moveNumber, bursts)
        if (rng.nextInt(3) != 0) return listOf(combo)
        return listOf(combo, JuiceEvent.Solved(moveNumber, crystalCount = bursts.size, paletteArgb = PALETTE))
    }

    private fun endpoints(rng: Random): List<EndpointSpark> =
        List(rng.nextInt(0, 9)) { EndpointSpark(rng.nextFloat() * AREA, rng.nextFloat() * AREA, PALETTE[it % 3]) }

    private fun run(script: List<ScriptFrame>): List<JuiceState> {
        val stepper = DefaultJuiceStepper()
        var state = JuiceState.EMPTY
        return script.map { frame ->
            state = stepper.step(state, frame.events, frame.dtMillis)
            state
        }
    }

    private fun assertSameState(
        frame: Int,
        a: JuiceState,
        b: JuiceState,
    ) {
        assertEquals("Frame $frame: elapsed", a.elapsedMillis, b.elapsedMillis)
        assertEquals("Frame $frame: reduceMotion", a.reduceMotion, b.reduceMotion)
        assertEquals("Frame $frame: haloPulseFactor", a.haloPulseFactor, b.haloPulseFactor, 0f)
        assertEquals("Frame $frame: flashAlpha", a.flashAlpha, b.flashAlpha, 0f)
        assertEquals("Frame $frame: flashRemaining", a.flashRemainingMillis, b.flashRemainingMillis)
        assertEquals("Frame $frame: emitters", a.emitters, b.emitters)
        assertEquals("Frame $frame: pendingBursts", a.pendingBursts, b.pendingBursts)
        assertEquals("Frame $frame: glows", a.glows, b.glows)
        assertSameParticles(frame, a.particles, b.particles)
    }

    private fun assertSameParticles(
        frame: Int,
        pa: ParticleSnapshot,
        pb: ParticleSnapshot,
    ) {
        assertEquals("Frame $frame: count", pa.count, pb.count)
        assertTrue("Frame $frame: xDp", pa.xDp.contentEquals(pb.xDp))
        assertTrue("Frame $frame: yDp", pa.yDp.contentEquals(pb.yDp))
        assertTrue("Frame $frame: sizeDp", pa.sizeDp.contentEquals(pb.sizeDp))
        assertTrue("Frame $frame: alpha", pa.alpha.contentEquals(pb.alpha))
        assertTrue("Frame $frame: colorArgb", pa.colorArgb.contentEquals(pb.colorArgb))
        assertTrue("Frame $frame: vxDp", pa.vxDp.contentEquals(pb.vxDp))
        assertTrue("Frame $frame: vyDp", pa.vyDp.contentEquals(pb.vyDp))
        assertTrue("Frame $frame: gravity", pa.gravityDpPerSec2.contentEquals(pb.gravityDpPerSec2))
        assertTrue("Frame $frame: fade", pa.alphaFadePerMillis.contentEquals(pb.alphaFadePerMillis))
    }

    @Test
    fun `1000 Frames - gleiche Seeds und Event-Folge liefern die identische JuiceState-Folge`() {
        val script = script(SCRIPT_SEED)
        val runA = run(script)
        val runB = run(script)
        runA.indices.forEach { assertSameState(it, runA[it], runB[it]) }
        // Das Drehbuch muss die Effektpfade tatsächlich getroffen haben, sonst prüft es nichts.
        assertTrue("Partikel kamen vor", runA.any { it.particles.count > 0 })
        assertTrue("Glows kamen vor", runA.any { it.glows.isNotEmpty() })
        assertTrue("Flash kam vor", runA.any { it.flashAlpha > 0f })
        assertTrue("Reduce-Motion-Phasen kamen vor", runA.any { it.reduceMotion } && runA.any { !it.reduceMotion })
    }

    @Test
    fun `1000 Frames - andere levelSeed veraendert die Partikel, nicht die Zeitachse`() {
        val script = script(SCRIPT_SEED)
        val other =
            script.mapIndexed { i, frame ->
                if (i == 0) {
                    ScriptFrame(
                        listOf(JuiceEvent.ScreenEntered(LEVEL_SEED + 1, false, emptyList())) + frame.events.drop(1),
                        frame.dtMillis,
                    )
                } else {
                    frame
                }
            }
        val runA = run(script)
        val runB = run(other)
        assertEquals("Zeitachse identisch", runA.map { it.elapsedMillis }, runB.map { it.elapsedMillis })
        val diverged =
            runA.indices.any { i ->
                runA[i].particles.count > 0 && !runA[i].particles.vxDp.contentEquals(runB[i].particles.vxDp)
            }
        assertTrue("Anderer Seed muss andere Partikel erzeugen (Seed-Kette ADR-011)", diverged)
    }

    private companion object {
        const val FRAME_COUNT = 1000
        const val SCRIPT_SEED = 20_260_721L
        const val LEVEL_SEED = 4711L
        const val AREA = 300f
        val PALETTE = listOf(0xFFE5484D.toInt(), 0xFF30A46C.toInt(), 0xFF3E63DD.toInt())
    }
}
