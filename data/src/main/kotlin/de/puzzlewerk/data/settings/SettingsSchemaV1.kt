package de.puzzlewerk.data.settings

import de.puzzlewerk.data.store.PayloadDecodeResult
import de.puzzlewerk.data.store.PayloadMigration
import de.puzzlewerk.data.store.StoreSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Payload v1 des Settings-Stores: die vier Booleans aus §12.5 (ADR-007). */
@Serializable
internal data class SettingsPayloadV1(
    val soundEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val colorSymbolsEnabled: Boolean,
    val beamPatternsEnabled: Boolean,
)

/**
 * Schema v1 des Settings-Stores (ADR-007, §12.5): strikte Dekodierung;
 * der Korruptions-RÜCKFALL auf [Settings.DEFAULT] liegt bewusst im
 * Repository (Dokumentationsort laut `SettingsRepository`-KDoc), nicht hier —
 * das Schema meldet Verstöße wie jedes andere als Wert.
 */
internal object SettingsSchemaV1 : StoreSchema<Settings> {
    override val storeName: String = "settings"
    override val currentVersion: Int = 1
    override val migrations: List<PayloadMigration> = emptyList()

    // Json.Default ist strikt: ignoreUnknownKeys = false, kein Lenient-Modus (S4, ADR-007).
    private val json = Json

    override fun encode(value: Settings): JsonElement =
        json.encodeToJsonElement(
            SettingsPayloadV1(
                soundEnabled = value.soundEnabled,
                hapticsEnabled = value.hapticsEnabled,
                colorSymbolsEnabled = value.colorSymbolsEnabled,
                beamPatternsEnabled = value.beamPatternsEnabled,
            ),
        )

    override fun decode(payload: JsonElement): PayloadDecodeResult<Settings> {
        // Streaming-Decoder + runCatching: siehe ProgressSchemaV1 (S4, keine Nutzdaten in Diagnosen).
        val dto =
            runCatching { json.decodeFromString<SettingsPayloadV1>(payload.toString()) }.getOrNull()
                ?: return PayloadDecodeResult.Invalid("$storeName: Payload verletzt das v1-Schema")
        return PayloadDecodeResult.Decoded(
            Settings(
                soundEnabled = dto.soundEnabled,
                hapticsEnabled = dto.hapticsEnabled,
                colorSymbolsEnabled = dto.colorSymbolsEnabled,
                beamPatternsEnabled = dto.beamPatternsEnabled,
            ),
        )
    }
}
