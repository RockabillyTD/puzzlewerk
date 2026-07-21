package de.puzzlewerk.app.audio

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Der aufzeichnende Fake (ADR-010) ist die Testoberfläche von PW-4.9 — hier sein Eigenvertrag. */
class FakeAudioEngineTest {
    @Test
    fun `zeichnet alle Aufrufe in Reihenfolge auf`() {
        val fake = FakeAudioEngine()
        fake.enterGame(musicEnabled = true, sfxEnabled = false)
        fake.setStemMix(StemMix.BASE)
        fake.setStemMix(StemMix(1f, 1f, 0f, 0f))
        fake.duckForSolve()
        fake.playSfx(SoundEffect.SOLVE_EXPLOSION)
        fake.setLaserLoopActive(true)
        fake.setHostVisible(false)
        fake.exitGame()
        fake.release()

        assertEquals(listOf(FakeAudioEngine.EnterGameCall(true, false)), fake.enterGameCalls)
        assertEquals(listOf(StemMix.BASE, StemMix(1f, 1f, 0f, 0f)), fake.stemMixHistory)
        assertEquals(1, fake.duckCount)
        assertEquals(listOf(SoundEffect.SOLVE_EXPLOSION), fake.playedEffects)
        assertTrue(fake.laserLoopActive)
        assertEquals(listOf(false), fake.hostVisibleHistory)
        assertEquals(1, fake.exitGameCount)
        assertEquals(1, fake.releaseCount)
    }

    @Test
    fun `emitIssue erreicht Abnehmer des issues-Flows`() =
        runTest {
            val fake = FakeAudioEngine()
            fake.issues.test {
                fake.emitIssue(AudioIssue.FocusLost)
                assertEquals(AudioIssue.FocusLost, awaitItem())
                fake.emitIssue(AudioIssue.AssetUnavailable("music_stem1_urig"))
                assertEquals(AudioIssue.AssetUnavailable("music_stem1_urig"), awaitItem())
            }
        }

    @Test
    fun `Laser-Loop-Zustand folgt dem letzten Aufruf`() {
        val fake = FakeAudioEngine()
        fake.setLaserLoopActive(true)
        fake.setLaserLoopActive(false)
        assertFalse(fake.laserLoopActive)
    }
}
