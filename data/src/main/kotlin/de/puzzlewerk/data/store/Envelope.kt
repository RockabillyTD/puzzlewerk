package de.puzzlewerk.data.store

import de.puzzlewerk.data.PersistenceFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal const val ENVELOPE_VERSION_FIELD: String = "version"
internal const val ENVELOPE_PAYLOAD_FIELD: String = "payload"
internal const val ENVELOPE_FIRST_VERSION: Int = 1

/**
 * Kodiert [value] als JSON-Envelope `{"version": N, "payload": …}` (ADR-007).
 * Deterministisch, damit Golden-Dateien byte-genau vergleichbar sind.
 */
internal fun <T> StoreSchema<T>.encodeStoreText(value: T): String =
    Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(
            mapOf(
                ENVELOPE_VERSION_FIELD to JsonPrimitive(currentVersion),
                ENVELOPE_PAYLOAD_FIELD to encode(value),
            ),
        ),
    )

/**
 * Dekodiert einen Store-Dateiinhalt: Envelope zuerst, dann Payload gemäß
 * Version inklusive Migrationskette (ADR-007). Alle Fehler sind Werte.
 */
internal fun <T> StoreSchema<T>.decodeStoreText(text: String): StoreState<T> =
    when (val envelope = parseEnvelope(text, storeName)) {
        is EnvelopeParse.Failure -> StoreState.Failed(envelope.failure)
        is EnvelopeParse.Parsed -> decodeVersionedPayload(envelope.version, envelope.payload)
    }

private fun <T> StoreSchema<T>.decodeVersionedPayload(
    version: Int,
    payload: JsonElement,
): StoreState<T> {
    if (version > currentVersion) {
        return StoreState.Failed(PersistenceFailure.UnsupportedVersion(version, currentVersion))
    }
    val migrated =
        (version until currentVersion).fold(payload) { current, fromVersion ->
            migrations[fromVersion - ENVELOPE_FIRST_VERSION].migrate(current)
        }
    return when (val result = decode(migrated)) {
        is PayloadDecodeResult.Decoded -> StoreState.Loaded(result.value)
        is PayloadDecodeResult.Invalid -> StoreState.Failed(PersistenceFailure.Corrupted(result.details))
    }
}

private sealed interface EnvelopeParse {
    data class Parsed(
        val version: Int,
        val payload: JsonElement,
    ) : EnvelopeParse

    data class Failure(
        val failure: PersistenceFailure,
    ) : EnvelopeParse
}

private fun parseEnvelope(
    text: String,
    storeName: String,
): EnvelopeParse {
    val envelope =
        parseStrictEnvelopeObject(text)
            ?: return corrupted("$storeName: Envelope ist kein JSON-Objekt mit genau den Feldern version und payload")
    val version =
        envelopeVersion(envelope)
            ?: return corrupted("$storeName: version ist keine Ganzzahl")
    return if (version < ENVELOPE_FIRST_VERSION) {
        corrupted("$storeName: version muss >= $ENVELOPE_FIRST_VERSION sein")
    } else {
        EnvelopeParse.Parsed(version, envelope.getValue(ENVELOPE_PAYLOAD_FIELD))
    }
}

private fun corrupted(details: String): EnvelopeParse.Failure =
    EnvelopeParse.Failure(
        PersistenceFailure.Corrupted(details),
    )

// runCatching statt catch (e: SerializationException): die Parser-Exception darf nicht in die
// Diagnose (ihre Message enthält Dateiauszüge = Nutzdaten) und wird deshalb bewusst verworfen.
private fun parseStrictEnvelopeObject(text: String): JsonObject? {
    val root = runCatching { Json.parseToJsonElement(text) }.getOrNull()
    return (root as? JsonObject)
        ?.takeIf { it.keys == setOf(ENVELOPE_VERSION_FIELD, ENVELOPE_PAYLOAD_FIELD) }
}

private fun envelopeVersion(envelope: JsonObject): Int? {
    val primitive = envelope[ENVELOPE_VERSION_FIELD] as? JsonPrimitive ?: return null
    // Strikt (S4): "1" als String ist KEINE gültige Versionsangabe.
    return if (primitive.isString) null else primitive.intOrNull
}
