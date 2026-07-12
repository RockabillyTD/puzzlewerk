package de.puzzlewerk.data.settings

import de.puzzlewerk.data.WriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-Memory-[SettingsRepository] für :app-Tests und Compose-Previews
 * (Ticket PW-3.2). Wie die DataStore-Implementierung liefert es immer einen
 * nutzbaren [Settings]-Wert (§12.5; Korruptions-Rückfall entfällt in-memory).
 */
class FakeSettingsRepository(
    initial: Settings = Settings.DEFAULT,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<Settings> = state.asStateFlow()

    override suspend fun update(transform: (Settings) -> Settings): WriteResult {
        state.update(transform)
        return WriteResult.Success
    }
}
