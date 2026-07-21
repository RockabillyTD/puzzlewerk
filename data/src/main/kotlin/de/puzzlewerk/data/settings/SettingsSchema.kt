package de.puzzlewerk.data.settings

import de.puzzlewerk.data.store.PayloadDecodeResult
import de.puzzlewerk.data.store.PayloadMigration
import de.puzzlewerk.data.store.StoreSchema
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Payload v2 des Settings-Stores (§13.11, PW-4.8): `soundEnabled` (v1) ist
 * durch die getrennten Schalter `musicEnabled`/`sfxEnabled` ersetzt.
 */
@Serializable
internal data class SettingsPayloadV2(
    val musicEnabled: Boolean,
    val sfxEnabled: Boolean,
    val hapticsEnabled: Boolean,
    val colorSymbolsEnabled: Boolean,
    val beamPatternsEnabled: Boolean,
)

private const val LEGACY_SOUND_FIELD = "soundEnabled"
private const val MUSIC_FIELD = "musicEnabled"
private const val SFX_FIELD = "sfxEnabled"

/**
 * Migration v1 → v2 (§13.11, normativ): alter Wert AUS ⇒ beide AUS; alter
 * Wert AN ODER Feld fehlt ⇒ beide AN. Alle übrigen Felder wandern unverändert
 * mit; Nicht-Objekte werden durchgereicht und scheitern anschließend an der
 * strikten v2-Dekodierung (Fehler bleibt ein Wert, C3/ADR-007).
 */
internal val settingsMigrationV1ToV2: PayloadMigration =
    PayloadMigration { payload ->
        val fields = (payload as? JsonObject) ?: return@PayloadMigration payload
        val legacy = (fields[LEGACY_SOUND_FIELD] as? JsonPrimitive)?.booleanOrNull ?: true
        JsonObject(
            fields.filterKeys { it != LEGACY_SOUND_FIELD } +
                mapOf(
                    MUSIC_FIELD to JsonPrimitive(legacy),
                    SFX_FIELD to JsonPrimitive(legacy),
                ),
        )
    }

/**
 * Schema des Settings-Stores (ADR-007, §12.5/§13.11): strikte Dekodierung;
 * der Korruptions-RÜCKFALL auf [Settings.DEFAULT] liegt bewusst im
 * Repository (Dokumentationsort laut `SettingsRepository`-KDoc), nicht hier —
 * das Schema meldet Verstöße wie jedes andere als Wert.
 */
internal object SettingsSchema : StoreSchema<Settings> {
    override val storeName: String = "settings"
    override val currentVersion: Int = 2
    override val migrations: List<PayloadMigration> = listOf(settingsMigrationV1ToV2)

    // Json.Default ist strikt: ignoreUnknownKeys = false, kein Lenient-Modus (S4, ADR-007).
    private val json = Json

    override fun encode(value: Settings): JsonElement =
        json.encodeToJsonElement(
            SettingsPayloadV2(
                musicEnabled = value.musicEnabled,
                sfxEnabled = value.sfxEnabled,
                hapticsEnabled = value.hapticsEnabled,
                colorSymbolsEnabled = value.colorSymbolsEnabled,
                beamPatternsEnabled = value.beamPatternsEnabled,
            ),
        )

    override fun decode(payload: JsonElement): PayloadDecodeResult<Settings> {
        // Streaming-Decoder + runCatching: siehe ProgressSchemaV1 (S4, keine Nutzdaten in Diagnosen).
        val dto =
            runCatching { json.decodeFromString<SettingsPayloadV2>(payload.toString()) }.getOrNull()
                ?: return PayloadDecodeResult.Invalid("$storeName: Payload verletzt das v2-Schema")
        return PayloadDecodeResult.Decoded(
            Settings(
                musicEnabled = dto.musicEnabled,
                sfxEnabled = dto.sfxEnabled,
                hapticsEnabled = dto.hapticsEnabled,
                colorSymbolsEnabled = dto.colorSymbolsEnabled,
                beamPatternsEnabled = dto.beamPatternsEnabled,
            ),
        )
    }
}
