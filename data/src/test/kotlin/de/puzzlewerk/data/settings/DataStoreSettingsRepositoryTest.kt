package de.puzzlewerk.data.settings

import app.cash.turbine.test
import de.puzzlewerk.data.WriteResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreSettingsRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private fun storeFile(): File = File(tempDir, "settings.json")

    private fun TestScope.newRepository(): DataStoreSettingsRepository =
        DataStoreSettingsRepository(
            backgroundScope,
            ::storeFile,
        )

    private fun goldenText(name: String): String =
        requireNotNull(
            javaClass.getResource("/golden/$name"),
        ).readText().trim()

    @Test
    fun `Erststart ohne Datei liefert die Defaults (Paragraf 12_5)`() =
        runTest {
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `update speichert atomar und der Flow emittiert den neuen Stand`() =
        runTest {
            val repository = newRepository()
            repository.settings.test {
                awaitItem() shouldBe Settings.DEFAULT
                repository.update { it.copy(hapticsEnabled = false) } shouldBe WriteResult.Success
                awaitItem() shouldBe Settings.DEFAULT.copy(hapticsEnabled = false)
            }
        }

    @Test
    fun `geschriebene Datei entspricht der Golden-Datei v2`() =
        runTest {
            newRepository().update { it.copy(hapticsEnabled = false) } shouldBe WriteResult.Success
            storeFile().readText() shouldBe goldenText("settings_v2.json")
        }

    @Test
    fun `Golden-Datei v2 wird zu den erwarteten Einstellungen gelesen`() =
        runTest {
            storeFile().writeText(goldenText("settings_v2.json"))
            newRepository().settings.first() shouldBe Settings.DEFAULT.copy(hapticsEnabled = false)
        }

    @Test
    fun `Golden-Datei v1 migriert soundEnabled AN auf Musik UND Effekte AN (Paragraf 13_11)`() =
        runTest {
            storeFile().writeText(goldenText("settings_v1.json"))
            newRepository().settings.first() shouldBe
                Settings.DEFAULT.copy(musicEnabled = true, sfxEnabled = true, hapticsEnabled = false)
        }

    @Test
    fun `v1 mit soundEnabled AUS migriert auf Musik UND Effekte AUS (Paragraf 13_11)`() =
        runTest {
            storeFile().writeText(
                """{"version":1,"payload":{"soundEnabled":false,"hapticsEnabled":true,""" +
                    """"colorSymbolsEnabled":true,"beamPatternsEnabled":true}}""",
            )
            newRepository().settings.first() shouldBe
                Settings.DEFAULT.copy(musicEnabled = false, sfxEnabled = false)
        }

    @Test
    fun `v1 ohne soundEnabled-Feld migriert auf beide AN (Paragraf 13_11)`() =
        runTest {
            storeFile().writeText(
                """{"version":1,"payload":{"hapticsEnabled":true,""" +
                    """"colorSymbolsEnabled":true,"beamPatternsEnabled":true}}""",
            )
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `korrupte Datei faellt auf die Defaults zurueck (ADR-007-Ausnahme fuer Settings)`() =
        runTest {
            storeFile().writeText("{")
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `neuere Schemaversion faellt auf die Defaults zurueck`() =
        runTest {
            storeFile().writeText("""{"version":3,"payload":{}}""")
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `update nach Korruption ueberschreibt die Datei mit gueltigem v2-Bestand`() =
        runTest {
            storeFile().writeText("kein json")
            val repository = newRepository()
            repository.update { it.copy(hapticsEnabled = false) } shouldBe WriteResult.Success
            storeFile().readText() shouldBe goldenText("settings_v2.json")
            repository.settings.first() shouldBe Settings.DEFAULT.copy(hapticsEnabled = false)
        }

    @Test
    fun `unbekannte Payload-Felder gelten als Korruption und fallen auf Defaults zurueck`() =
        runTest {
            storeFile().writeText(
                """{"version":2,"payload":{"musicEnabled":true,"sfxEnabled":true,"hapticsEnabled":true,""" +
                    """"colorSymbolsEnabled":true,"beamPatternsEnabled":true,"extra":1}}""",
            )
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `unbekannte v1-Felder ueberleben die Migration nicht und fallen auf Defaults zurueck`() =
        runTest {
            storeFile().writeText(
                """{"version":1,"payload":{"soundEnabled":true,"hapticsEnabled":true,""" +
                    """"colorSymbolsEnabled":true,"beamPatternsEnabled":true,"extra":1}}""",
            )
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }
}
