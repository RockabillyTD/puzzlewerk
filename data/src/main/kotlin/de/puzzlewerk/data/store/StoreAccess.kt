package de.puzzlewerk.data.store

import androidx.datastore.core.DataStore
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Beobachtbarer Bestand als [DataResult]-Werte: dekodierte Zustände direkt,
 * E/A-Fehler des Lesens als [PersistenceFailure.Io] (nie Crash, R43-Geist).
 */
internal fun <T> DataStore<StoreState<T>>.dataResults(): Flow<DataResult<T>> =
    data
        .map { state -> state.toDataResult() }
        .catch { cause ->
            if (cause is IOException) {
                emit(DataResult.Failure(PersistenceFailure.Io(cause.ioDetails())))
            } else {
                throw cause
            }
        }

/**
 * Wendet [transform] atomar auf einen GELADENEN Bestand an. Ist der Bestand
 * [StoreState.Failed], bleibt die Datei unangetastet (ADR-007: kein stilles
 * Überschreiben) und der Fehler kommt als [WriteResult.Failure] zurück —
 * der definierte Ausweg ist `reset()` bzw. [overwriteWith].
 */
internal suspend fun <T> DataStore<StoreState<T>>.updateLoaded(transform: (T) -> T): WriteResult =
    writeResultOf {
        updateData { current ->
            when (current) {
                is StoreState.Loaded -> StoreState.Loaded(transform(current.value))
                is StoreState.Failed -> current
            }
        }
    }

/**
 * Ersetzt den Bestand vorbehaltlos durch [value] — ausschließlich für die
 * EXPLIZITEN Reset-Pfade der Repositories definiert (§12.5, ADR-007).
 */
internal suspend fun <T> DataStore<StoreState<T>>.overwriteWith(value: T): WriteResult =
    writeResultOf { updateData { StoreState.Loaded(value) } }

private suspend fun <T> writeResultOf(write: suspend () -> StoreState<T>): WriteResult =
    try {
        when (val state = write()) {
            is StoreState.Loaded -> WriteResult.Success
            is StoreState.Failed -> WriteResult.Failure(state.failure)
        }
    } catch (cause: IOException) {
        WriteResult.Failure(PersistenceFailure.Io(cause.ioDetails()))
    }

private fun IOException.ioDetails(): String = message ?: this::class.simpleName ?: "IOException"
