package de.puzzlewerk.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.app.audio.AudioEngine
import de.puzzlewerk.app.audio.AudioManagerFocusRequester
import de.puzzlewerk.app.audio.DefaultAudioEngine
import de.puzzlewerk.app.audio.MediaCodecStemDecoder
import de.puzzlewerk.app.audio.SoundPoolSfxPlayer
import de.puzzlewerk.app.audio.audioTrackSinkOrNull
import de.puzzlewerk.app.ui.game.GameViewModel
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.DefaultLevelGenerator
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.score.ScoreCalculator
import de.puzzlewerk.game.trace.DefaultTracer

/**
 * Composition Root (ADR-006): die EINZIGE Stelle, die Produktions-
 * Implementierungen konstruiert. Exponiert ausschließlich Interfaces;
 * Tests und Previews bauen ihre Objekte direkt mit Fakes.
 */
class AppContainer {
    /**
     * Kampagnenfortschritt (§7.2, §11). Bis die DataStore-Implementierung
     * aus PW-3.2 verdrahtet ist (PW-3.5/PW-3.6), hält eine Übergangs-
     * Implementierung den Fortschritt nur im Speicher — Verhalten laut
     * Interface-Vertrag, aber ohne Persistenz über den Prozess hinaus.
     * Issue: docs/backlog.md („DataStore-Repositories verdrahten", PW-3.3).
     */
    val progressRepository: ProgressRepository = InMemoryProgressRepository()

    /** Zug-/Trace-Semantik (§6) mit dem `:game`-Default-Tracer — kein Tracer in :app (ui-arch §4). */
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)

    /** Level-Generator (§9) und Score-Formel (§7); beide deterministische `:game`-Defaults. */
    private val generator: LevelGenerator = DefaultLevelGenerator
    private val scoreCalculator: ScoreCalculator = DefaultScoreCalculator

    /** Gemeinsame Factory der parameterlosen ViewModels (Home/LevelSelect, ADR-006). */
    val viewModelFactory: ViewModelProvider.Factory = PuzzlewerkViewModelFactory(this)

    private var audioEngineInstance: AudioEngine? = null

    /**
     * [AudioEngine] (ADR-010), einmal je Prozess: Aufbau beim App-Start lädt
     * die SoundPool-SFX vor; die Stems dekodiert der Mixer-Thread beim ersten
     * `enterGame`. Konsumiert wird die Engine ab PW-4.6 (Aktions-Feedback).
     */
    internal fun audioEngine(context: Context): AudioEngine =
        audioEngineInstance ?: run {
            val appContext = context.applicationContext
            DefaultAudioEngine(
                decoder = MediaCodecStemDecoder(appContext),
                sinkFactory = ::audioTrackSinkOrNull,
                sfxPlayer = SoundPoolSfxPlayer(appContext),
                focus = AudioManagerFocusRequester(appContext),
            ).also { audioEngineInstance = it }
        }

    /**
     * Request-parametrierte Factory des [GameViewModel] (ui-architektur §3:
     * „Übergabe über die ViewModel-Factory, keine globale Ablage"). Manuell
     * konstruiert, keine Reflection; welche Partie gespielt wird, bestimmt
     * allein [request] (ADR-008).
     */
    fun gameViewModelFactory(request: LevelRequest): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
                    "gameViewModelFactory baut nur GameViewModel, nicht ${modelClass.name}"
                }
                val viewModel: ViewModel =
                    GameViewModel(
                        request = request,
                        engine = engine,
                        generator = generator,
                        scoreCalculator = scoreCalculator,
                        progressRepository = progressRepository,
                    )
                return requireNotNull(modelClass.cast(viewModel))
            }
        }
}
