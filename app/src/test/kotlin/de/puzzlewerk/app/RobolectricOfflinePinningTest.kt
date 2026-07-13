package de.puzzlewerk.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Wächter für die S6-Auflage aus ADR-009: Test-Tasks laufen mit
 * `robolectric.offline=true` gegen die per Gradle gepinnten android-all-Jars
 * (app/build.gradle.kts). Schlägt an, wenn die Pinnung still entfernt wird —
 * dann würde Robolectric zur Laufzeit ungeprüfte Artefakte nachladen.
 */
class RobolectricOfflinePinningTest {
    @Test
    fun `Robolectric laeuft offline gegen die gepinnten Artefakte`() {
        assertEquals("true", System.getProperty("robolectric.offline"))

        val dependencyDir = System.getProperty("robolectric.dependency.dir")
        assertNotNull("robolectric.dependency.dir muss gesetzt sein (ADR-009/S6)", dependencyDir)
        val pinnedJars =
            File(checkNotNull(dependencyDir))
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith("android-all-instrumented") && it.extension == "jar" }
        assertTrue("Gepinntes android-all-Jar fehlt in $dependencyDir", pinnedJars.isNotEmpty())
    }
}
