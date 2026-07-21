package de.puzzlewerk.data.settings

import app.cash.turbine.test
import de.puzzlewerk.data.WriteResult
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeSettingsRepositoryTest {
    @Test
    fun `Erststart liefert die normativen Defaults (Paragraf 12_5 und 13_11)`() =
        runTest {
            FakeSettingsRepository().settings.first() shouldBe Settings.DEFAULT
            Settings.DEFAULT shouldBe
                Settings(
                    musicEnabled = true,
                    sfxEnabled = true,
                    hapticsEnabled = true,
                    colorSymbolsEnabled = true,
                    beamPatternsEnabled = true,
                )
        }

    @Test
    fun `update wendet die Transformation an und emittiert den neuen Stand`() =
        runTest {
            val repository = FakeSettingsRepository()
            repository.settings.test {
                awaitItem() shouldBe Settings.DEFAULT
                repository.update { it.copy(musicEnabled = false) } shouldBe WriteResult.Success
                awaitItem() shouldBe Settings.DEFAULT.copy(musicEnabled = false)
                repository.update { it.copy(sfxEnabled = false) } shouldBe WriteResult.Success
                awaitItem() shouldBe Settings.DEFAULT.copy(musicEnabled = false, sfxEnabled = false)
            }
        }

    @Test
    fun `Initialstand ist konfigurierbar (Previews)`() =
        runTest {
            val initial =
                Settings(
                    musicEnabled = false,
                    sfxEnabled = true,
                    hapticsEnabled = false,
                    colorSymbolsEnabled = true,
                    beamPatternsEnabled = true,
                )
            FakeSettingsRepository(initial).settings.first() shouldBe initial
        }
}
