package de.puzzlewerk.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.store.EnvelopeSerializer
import de.puzzlewerk.data.store.StoreState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException

/**
 * [SettingsRepository] auf Basis eines typisierten DataStores mit
 * JSON-Envelope-Schema v1 (ADR-007). Bewusste Abweichung von den anderen
 * Repositories (dokumentiert in ADR-007 und im Interface-KDoc): Der Bestand
 * ist nicht schützenswert — bei Korruption, Versionskonflikt oder Lesefehler
 * fällt [settings] auf [Settings.DEFAULT] zurück, und das nächste [update]
 * überschreibt die unbrauchbare Datei mit einem gültigen v1-Bestand.
 *
 * @param scope Coroutine-Scope des DataStores (Lebensdauer der App bzw. des
 *   Tests); Dispatcher-Wahl liegt beim Aufrufer (Composition Root, ADR-006).
 * @param produceFile liefert die Store-Datei im App-Sandbox-Verzeichnis (S2).
 */
class DataStoreSettingsRepository(
    scope: CoroutineScope,
    produceFile: () -> File,
) : SettingsRepository {
    private val store: DataStore<StoreState<Settings>> =
        DataStoreFactory.create(
            serializer = EnvelopeSerializer(SettingsSchemaV1, StoreState.Loaded(Settings.DEFAULT)),
            scope = scope,
            produceFile = produceFile,
        )

    override val settings: Flow<Settings> =
        store.data
            .map { state -> state.valueOrDefault() }
            .catch { cause ->
                if (cause is IOException) emit(Settings.DEFAULT) else throw cause
            }

    override suspend fun update(transform: (Settings) -> Settings): WriteResult =
        try {
            store.updateData { current -> StoreState.Loaded(transform(current.valueOrDefault())) }
            WriteResult.Success
        } catch (cause: IOException) {
            WriteResult.Failure(PersistenceFailure.Io(cause.message ?: "E/A-Fehler beim Schreiben der Einstellungen"))
        }
}

private fun StoreState<Settings>.valueOrDefault(): Settings =
    when (this) {
        is StoreState.Loaded -> value
        is StoreState.Failed -> Settings.DEFAULT
    }
