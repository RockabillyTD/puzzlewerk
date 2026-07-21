package de.puzzlewerk.data.settings

import de.puzzlewerk.data.WriteResult
import kotlinx.coroutines.flow.Flow

/**
 * Nutzereinstellungen (Design §12.5). Die Defaults sind hier normativ:
 * Barrierefreiheits-Kanäle (Farbsymbole, Strahlmuster) sind bewusst AN
 * (§13.1/§13.2). Sprache folgt dem System und wird nicht gespeichert.
 *
 * BREAKING §13.11 (PW-4.8): Der frühere Schalter `soundEnabled` ist durch
 * die getrennten Schalter [musicEnabled] (Stems) und [sfxEnabled] (alle SFX
 * inkl. sfx_laser_loop) ersetzt, beide Default AN; Migration des alten
 * Werts: AUS ⇒ beide AUS, AN oder Feld fehlt ⇒ beide AN (Schema v1 → v2).
 */
data class Settings(
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val colorSymbolsEnabled: Boolean = true,
    val beamPatternsEnabled: Boolean = true,
) {
    companion object {
        /** Auslieferungszustand (§12.5, §13). */
        val DEFAULT: Settings = Settings()
    }
}

/**
 * Speichert die Einstellungen dauerhaft (Design §12.5; Persistenz-Semantik:
 * ADR-007). Bewusste Abweichung von den anderen Repositories: Der
 * Settings-Bestand ist nicht schützenswert (5 Booleans) — bei Korruption
 * oder Versionskonflikt fällt [settings] auf [Settings.DEFAULT] zurück
 * (definiertes Verhalten laut ADR-007) statt einen Fehlerwert zu liefern.
 *
 * Implementierung: Ticket PW-3.2 (DataStore) + In-Memory-Fake für Tests.
 */
interface SettingsRepository {
    /**
     * Beobachtbare Einstellungen; emittiert sofort und bei jeder Änderung.
     * Erststart und Korruptions-Rückfall liefern [Settings.DEFAULT].
     */
    val settings: Flow<Settings>

    /**
     * Wendet [transform] atomar auf den aktuellen Stand an und speichert das
     * Ergebnis (DataStore-`updateData`-Semantik, ADR-007). [transform] muss
     * eine pure Funktion sein und kann bei konkurrierenden Updates erneut
     * ausgeführt werden.
     */
    suspend fun update(transform: (Settings) -> Settings): WriteResult
}
