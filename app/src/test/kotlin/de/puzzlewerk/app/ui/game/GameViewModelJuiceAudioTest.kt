package de.puzzlewerk.app.ui.game

import app.cash.turbine.test
import de.puzzlewerk.app.audio.FakeAudioEngine
import de.puzzlewerk.app.audio.SoundEffect
import de.puzzlewerk.app.audio.StemMix
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.progress.FakeProgressRepository
import de.puzzlewerk.data.settings.FakeSettingsRepository
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.GameState
import de.puzzlewerk.game.engine.InvalidMoveReason
import de.puzzlewerk.game.engine.Move
import de.puzzlewerk.game.engine.MoveResult
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.trace.BeamEndpoint
import de.puzzlewerk.game.trace.TraceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Choreografie-Tests des PW-4.6-Aktions-Feedbacks (§13.9/§13.11): JuiceFeedback-
 * und AudioEngine-Verdrahtung des [GameViewModel] gegen einen SKRIPTETEN
 * [GameEngine] mit handgebauten traces (2-Kristall-Combo, Lösung, ungültiger
 * Zug) plus [FakeAudioEngine] (R45-Ketten, R46-Stem-Folgen). Reine JVM-Tests
 * (ADR-009), deterministisch: StandardTestDispatcher, feste Daten, kein Sleep.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain/runCurrent: Standardweg laut ADR-009-Test-Stack
class GameViewModelJuiceAudioTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Skript: `newGame` ⇒ Startzustand; jeder Zug ⇒ das nächste Applied bzw. Invalid. */
    private class ScriptedEngine(
        private val start: MoveResult.Applied,
        private val onMove: (Move) -> MoveResult,
    ) : GameEngine {
        override fun newGame(level: LevelDefinition): MoveResult.Applied = start

        override fun applyMove(
            state: GameState,
            move: Move,
        ): MoveResult = onMove(move)
    }

    private fun viewModel(
        engine: GameEngine,
        audio: FakeAudioEngine,
    ): GameViewModel =
        GameViewModel(
            request = LevelRequest.Campaign(1),
            engine = engine,
            generator = LevelGenerator { _, _ -> LEVEL },
            scoreCalculator = DefaultScoreCalculator,
            progressRepository = FakeProgressRepository(),
            audio = GameAudioChoreographer(audio, FakeSettingsRepository()),
            dispatcher = dispatcher,
        )

    @Test
    fun `Applied mit 2 neuen Kristallen emittiert 2 CrystalBursts, SFX-Kette und Stem-Wechsel`() =
        runTest(dispatcher) {
            val audio = FakeAudioEngine()
            val vm = viewModel(ScriptedEngine(START) { MoveResult.Applied(COMBO_STATE, COMBO_TRACE) }, audio)
            runCurrent()

            vm.juiceFeedback.test {
                val entered = awaitItem() as JuiceFeedback.BoardEntered
                assertEquals(LEVEL_SEED, entered.levelSeed)

                vm.onIntent(GameIntent.TapCell(MIRROR))

                val move = awaitItem() as JuiceFeedback.MoveApplied
                assertEquals(1, move.moveNumber)
                assertEquals(MIRROR, move.rotatedCell)
                // 2 Bursts in Kaskadenreihenfolge (r, dann q) mit Soll-Farben (ADR-012).
                assertEquals(listOf(CRYSTAL_A, CRYSTAL_B), move.newlyFulfilled.map { it.cell })
                assertEquals(listOf(LightColor.RED, LightColor.GREEN), move.newlyFulfilled.map { it.required })
                assertEquals(2, move.endpoints.size)
                assertNull(move.solved) // nicht gelöst — kein Feuerwerk-Feedback
            }
            // SFX-Kette §13.9/§13.11: Drehung, dann Burst 1 = lit, Burst 2 = combo_up1.
            assertEquals(
                listOf(SoundEffect.ROTATE_TICK, SoundEffect.CRYSTAL_LIT, SoundEffect.COMBO_UP1),
                audio.playedEffects,
            )
            // Stem-Tabelle §13.11: Start L=0 ⇒ BASE, danach L=2/K=2 ⇒ alle Ebenen an.
            assertEquals(
                listOf(StemMix.forProgress(0, 2), StemMix.forProgress(2, 2)),
                audio.stemMixHistory,
            )
            assertEquals(0, audio.duckCount)
        }

    @Test
    fun `Loesender Zug spielt solve_explosion, duckt und liefert Solved-Feedback nach den Bursts`() =
        runTest(dispatcher) {
            val audio = FakeAudioEngine()
            val vm = viewModel(ScriptedEngine(START) { MoveResult.Applied(SOLVED_STATE, SOLVED_TRACE) }, audio)
            runCurrent()

            vm.juiceFeedback.test {
                awaitItem() // BoardEntered
                vm.onIntent(GameIntent.TapCell(MIRROR))
                val move = awaitItem() as JuiceFeedback.MoveApplied
                val solved = move.solved
                assertNotNull(solved)
                assertEquals(2, solved!!.crystalCount)
                // Feuerwerkspalette: Soll-Farben in Brett-Reihenfolge (r, dann q — §13.10).
                assertEquals(listOf(LightColor.RED, LightColor.GREEN), solved.paletteRequired)
            }
            assertEquals(
                listOf(
                    SoundEffect.ROTATE_TICK,
                    SoundEffect.CRYSTAL_LIT,
                    SoundEffect.COMBO_UP1,
                    SoundEffect.SOLVE_EXPLOSION,
                ),
                audio.playedEffects,
            )
            assertEquals(1, audio.duckCount)
        }

    @Test
    fun `Ungueltiger Zug spielt rotate_invalid und sonst nichts`() =
        runTest(dispatcher) {
            val audio = FakeAudioEngine()
            val vm = viewModel(ScriptedEngine(START) { MoveResult.Invalid(InvalidMoveReason.NOT_ROTATABLE) }, audio)
            runCurrent()
            audio.playedEffects.clear()

            vm.onIntent(GameIntent.TapCell(MIRROR))

            assertEquals(listOf(SoundEffect.ROTATE_INVALID), audio.playedEffects)
        }

    @Test
    fun `Screen betreten und verlassen ruft enterGame mit Settings und exitGame`() =
        runTest(dispatcher) {
            val audio = FakeAudioEngine()
            val vm = viewModel(ScriptedEngine(START) { MoveResult.Invalid(InvalidMoveReason.NOT_ROTATABLE) }, audio)
            runCurrent()

            vm.onScreenEntered()
            runCurrent()
            val expected = FakeAudioEngine.EnterGameCall(musicEnabled = true, sfxEnabled = true)
            assertEquals(listOf(expected), audio.enterGameCalls)

            vm.onScreenLeft()
            assertEquals(1, audio.exitGameCount)
        }

    @Test
    fun `Replay verwirft Effekte (R49) und startet die Audio-Session neu`() =
        runTest(dispatcher) {
            val audio = FakeAudioEngine()
            val vm = viewModel(ScriptedEngine(START) { MoveResult.Applied(SOLVED_STATE, SOLVED_TRACE) }, audio)
            runCurrent()
            vm.onIntent(GameIntent.TapCell(MIRROR))

            vm.juiceFeedback.test {
                awaitItem() // BoardEntered (Start)
                awaitItem() // MoveApplied (Lösung)

                vm.onIntent(GameIntent.Replay)
                runCurrent()

                assertEquals(JuiceFeedback.EffectsDismissed, awaitItem())
                assertTrue(awaitItem() is JuiceFeedback.BoardEntered)
            }
            assertEquals(1, audio.exitGameCount)
            assertEquals(1, audio.enterGameCalls.size)
            // Zugzähler/Logik unverändert: frische Partie desselben Levels.
            assertEquals(0, vm.state.value.moves)
        }

    private companion object {
        val MIRROR = HexCoord(0, 0)

        /** Kristall A liegt VOR B in Kaskadenreihenfolge: erst r (−1 < 0), dann q. */
        val CRYSTAL_A = HexCoord(q = 0, r = -1)
        val CRYSTAL_B = HexCoord(q = 1, r = 0)
        const val LEVEL_SEED = 99L

        val BOARD =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        MIRROR to Element.Mirror(Orientation(0)),
                        CRYSTAL_A to Element.Crystal(LightColor.RED),
                        CRYSTAL_B to Element.Crystal(LightColor.GREEN),
                    ),
            )
        val LEVEL = LevelDefinition(board = BOARD, par = 1, tier = Difficulty.D1, seed = LEVEL_SEED)

        val EMPTY_TRACE = TraceResult(emptyList(), emptyMap(), solved = false, endpoints = emptyList())
        val START =
            MoveResult.Applied(
                GameState(LEVEL, mapOf(MIRROR to Orientation(0)), history = emptyList(), solved = false),
                EMPTY_TRACE,
            )

        /** Nach dem Zug: beide Kristalle empfangen exakt ihre Sollfarbe ⇒ neu erfüllt (§13.9). */
        val FULL_RECEIVED = mapOf(CRYSTAL_A to LightColor.RED, CRYSTAL_B to LightColor.GREEN)
        val FULL_ENDPOINTS =
            listOf(BeamEndpoint(CRYSTAL_A, LightColor.RED), BeamEndpoint(CRYSTAL_B, LightColor.GREEN))

        val COMBO_STATE =
            GameState(LEVEL, mapOf(MIRROR to Orientation(1)), history = listOf(MIRROR), solved = false)
        val COMBO_TRACE = TraceResult(emptyList(), FULL_RECEIVED, solved = false, endpoints = FULL_ENDPOINTS)

        val SOLVED_STATE =
            GameState(LEVEL, mapOf(MIRROR to Orientation(1)), history = listOf(MIRROR), solved = true)
        val SOLVED_TRACE = TraceResult(emptyList(), FULL_RECEIVED, solved = true, endpoints = FULL_ENDPOINTS)
    }
}
