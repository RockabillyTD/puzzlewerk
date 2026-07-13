package de.puzzlewerk.data.store

import kotlinx.serialization.json.JsonElement

/** Ergebnis des Payload-Mappings JSON → Domäne (Fehler als Werte, Regel C3). */
internal sealed interface PayloadDecodeResult<out T> {
    /** Payload dekodiert, alle Wertebereichs- und Duplikat-Checks bestanden. */
    data class Decoded<T>(
        val value: T,
    ) : PayloadDecodeResult<T>

    /**
     * Schema-, Wertebereichs- oder Duplikat-Verstoß (ADR-007 ⇒ `Corrupted`).
     *
     * @property details Diagnose für Log/Fehleranzeige — niemals Nutzdaten.
     */
    data class Invalid(
        val details: String,
    ) : PayloadDecodeResult<Nothing>
}

/**
 * Reine Migrationsfunktion `payload_vN → payload_vN+1` (ADR-007). Migrationen
 * werden beim Laden verkettet, bis die aktuelle Version erreicht ist.
 */
internal fun interface PayloadMigration {
    fun migrate(payload: JsonElement): JsonElement
}

/**
 * Versioniertes Persistenz-Schema eines Stores (ADR-007): kodiert die Domäne
 * als Payload der aktuellen Version und dekodiert versionierte Payloads
 * inklusive aller Validierung an der Vertrauensgrenze (S4).
 */
internal interface StoreSchema<T> {
    /** Name für Diagnose-Meldungen (z. B. `progress`). */
    val storeName: String

    /** Aktuelle Schema-Version (startet bei 1, ADR-007). */
    val currentVersion: Int

    /** Migrationskette: Index `i` migriert Version `i + 1` auf `i + 2`. */
    val migrations: List<PayloadMigration>

    /** Kodiert [value] als Payload der [currentVersion] (deterministisch). */
    fun encode(value: T): JsonElement

    /** Dekodiert einen Payload der [currentVersion] strikt (S4, ADR-007). */
    fun decode(payload: JsonElement): PayloadDecodeResult<T>
}
