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

    private fun goldenText(): String =
        requireNotNull(
            javaClass.getResource("/golden/settings_v1.json"),
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
    fun `geschriebene Datei entspricht der Golden-Datei v1`() =
        runTest {
            newRepository().update { it.copy(hapticsEnabled = false) } shouldBe WriteResult.Success
            storeFile().readText() shouldBe goldenText()
        }

    @Test
    fun `Golden-Datei v1 wird zu den erwarteten Einstellungen gelesen`() =
        runTest {
            storeFile().writeText(goldenText())
            newRepository().settings.first() shouldBe Settings.DEFAULT.copy(hapticsEnabled = false)
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
            storeFile().writeText("""{"version":2,"payload":{}}""")
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }

    @Test
    fun `update nach Korruption ueberschreibt die Datei mit gueltigem v1-Bestand`() =
        runTest {
            storeFile().writeText("kein json")
            val repository = newRepository()
            repository.update { it.copy(hapticsEnabled = false) } shouldBe WriteResult.Success
            storeFile().readText() shouldBe goldenText()
            repository.settings.first() shouldBe Settings.DEFAULT.copy(hapticsEnabled = false)
        }

    @Test
    fun `unbekannte Payload-Felder gelten als Korruption und fallen auf Defaults zurueck`() =
        runTest {
            storeFile().writeText(
                """{"version":1,"payload":{"soundEnabled":true,"hapticsEnabled":true,""" +
                    """"colorSymbolsEnabled":true,"beamPatternsEnabled":true,"extra":1}}""",
            )
            newRepository().settings.first() shouldBe Settings.DEFAULT
        }
}
