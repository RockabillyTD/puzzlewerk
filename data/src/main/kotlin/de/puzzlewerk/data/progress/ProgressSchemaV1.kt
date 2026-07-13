package de.puzzlewerk.data.progress

import de.puzzlewerk.data.store.PayloadDecodeResult
import de.puzzlewerk.data.store.PayloadMigration
import de.puzzlewerk.data.store.SCORE_POINTS_RANGE
import de.puzzlewerk.data.store.SCORE_STARS_RANGE
import de.puzzlewerk.data.store.StoreSchema
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.score.Score
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Bestwert eines gelösten Kampagnenlevels im Payload v1. Entry-Array statt
 * dynamischer JSON-Schlüssel (ADR-007, §16.2/2) — Duplikate prüft der Mapper.
 */
@Serializable
internal data class ProgressEntryV1(
    val level: Int,
    val points: Int,
    val stars: Int,
)

/** Payload v1 des Fortschritts-Stores: `{"entries":[…]}` (ADR-007). */
@Serializable
internal data class ProgressPayloadV1(
    val entries: List<ProgressEntryV1>,
)

/**
 * Schema v1 des Fortschritts-Stores (ADR-007, §7.2, §11): strikte Dekodierung
 * (`ignoreUnknownKeys = false`), Wertebereichs- und Duplikat-Checks im Mapper.
 */
internal object ProgressSchemaV1 : StoreSchema<CampaignProgress> {
    override val storeName: String = "progress"
    override val currentVersion: Int = 1
    override val migrations: List<PayloadMigration> = emptyList()

    // Json.Default ist strikt: ignoreUnknownKeys = false, kein Lenient-Modus (S4, ADR-007).
    private val json = Json

    override fun encode(value: CampaignProgress): JsonElement =
        json.encodeToJsonElement(
            ProgressPayloadV1(
                value.bestByLevel.entries
                    .sortedBy { it.key }
                    .map { (level, score) -> ProgressEntryV1(level, score.points, score.stars) },
            ),
        )

    override fun decode(payload: JsonElement): PayloadDecodeResult<CampaignProgress> {
        // Streaming-Decoder statt decodeFromJsonElement: der Tree-Decoder ist insgesamt
        // laxer (u. a. Null-/Typ-Behandlung). Quotierte Zahlen ("1" als Int) akzeptieren
        // BEIDE Decoder — dokumentierte kotlinx-Toleranz, siehe Known-Behavior-Test in
        // EnvelopeTest. runCatching: die Parser-Exception darf nicht in die Diagnose
        // (ihre Message enthält Nutzdaten).
        val dto =
            runCatching { json.decodeFromString<ProgressPayloadV1>(payload.toString()) }.getOrNull()
                ?: return PayloadDecodeResult.Invalid("$storeName: Payload verletzt das v1-Schema")
        return mapEntries(dto.entries)
    }

    private fun mapEntries(entries: List<ProgressEntryV1>): PayloadDecodeResult<CampaignProgress> {
        val bestByLevel = LinkedHashMap<Int, Score>(entries.size)
        for (entry in entries) {
            val violation = entry.rangeViolation()
            if (violation != null) return PayloadDecodeResult.Invalid(violation)
            if (bestByLevel.put(entry.level, Score(entry.points, entry.stars)) != null) {
                return PayloadDecodeResult.Invalid("$storeName: doppelter level-Eintrag (§16.2/2)")
            }
        }
        return PayloadDecodeResult.Decoded(CampaignProgress(bestByLevel))
    }

    private fun ProgressEntryV1.rangeViolation(): String? =
        when {
            level !in 1..CAMPAIGN_LEVEL_COUNT -> "$storeName: level außerhalb 1..$CAMPAIGN_LEVEL_COUNT"
            points !in SCORE_POINTS_RANGE -> "$storeName: points außerhalb $SCORE_POINTS_RANGE (§7.2)"
            stars !in SCORE_STARS_RANGE -> "$storeName: stars außerhalb $SCORE_STARS_RANGE (§7.2)"
            else -> null
        }
}
