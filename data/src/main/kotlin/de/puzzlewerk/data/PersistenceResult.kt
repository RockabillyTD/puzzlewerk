package de.puzzlewerk.data

/**
 * Erwartbare Fehlerursachen der Persistenz als Werte (Regel C3, S4, R43;
 * Semantik verbindlich in ADR-007). Programmierfehler (z. B. ungültige
 * Parameter) sind KEINE [PersistenceFailure], sondern `require`-Verstöße.
 */
sealed interface PersistenceFailure {
    /**
     * Datei vorhanden, aber nicht dekodierbar: kaputtes JSON, Schema-Verstoß,
     * Wertebereichs-Verstoß im Mapping oder Duplikat-Schlüssel (ADR-007).
     * Nutzdaten werden dabei NIE stillschweigend überschrieben.
     *
     * @property details Diagnose für Log/Fehleranzeige, niemals Nutzdaten.
     */
    data class Corrupted(
        val details: String,
    ) : PersistenceFailure

    /**
     * Die Datei stammt aus einer neueren Schema-Version als diese
     * App-Installation unterstützt (Downgrade-Fall, ADR-007).
     */
    data class UnsupportedVersion(
        val storedVersion: Int,
        val supportedVersion: Int,
    ) : PersistenceFailure

    /**
     * E/A-Fehler beim Lesen oder Schreiben (z. B. Speicher voll).
     *
     * @property details Diagnose für Log/Fehleranzeige, niemals Nutzdaten.
     */
    data class Io(
        val details: String,
    ) : PersistenceFailure
}

/**
 * Ergebnis einer Leseoperation: entweder der aktuelle Bestand oder ein
 * definierter Fehler als Wert (Regel C3).
 */
sealed interface DataResult<out T> {
    /** Erfolgreich gelesen (bei leerem Bestand: der dokumentierte Leerwert). */
    data class Success<T>(
        val value: T,
    ) : DataResult<T>

    /** Lesen fehlgeschlagen; [failure] beschreibt die Ursache. */
    data class Failure(
        val failure: PersistenceFailure,
    ) : DataResult<Nothing>
}

/** Ergebnis einer Schreiboperation (Fehler als Werte, Regel C3). */
sealed interface WriteResult {
    /** Änderung dauerhaft gespeichert (atomarer Write, ADR-007). */
    data object Success : WriteResult

    /** Schreiben fehlgeschlagen; der vorherige Bestand bleibt unverändert. */
    data class Failure(
        val failure: PersistenceFailure,
    ) : WriteResult
}
