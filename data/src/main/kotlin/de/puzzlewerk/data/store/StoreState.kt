package de.puzzlewerk.data.store

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure

/**
 * In-Memory-Zustand eines DataStores: entweder der dekodierte Bestand oder ein
 * Fehlerwert (ADR-007: Korruption/Versionskonflikt sind Werte, nie Exceptions).
 * Persistiert wird ausschließlich [Loaded]; [Failed] entsteht nur beim Lesen.
 */
internal sealed interface StoreState<out T> {
    /** Erfolgreich dekodierter Bestand. */
    data class Loaded<T>(
        val value: T,
    ) : StoreState<T>

    /** Bestand nicht nutzbar; [failure] beschreibt die Ursache. */
    data class Failed(
        val failure: PersistenceFailure,
    ) : StoreState<Nothing>
}

internal fun <T> StoreState<T>.toDataResult(): DataResult<T> =
    when (this) {
        is StoreState.Loaded -> DataResult.Success(value)
        is StoreState.Failed -> DataResult.Failure(failure)
    }
