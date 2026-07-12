package de.puzzlewerk.data.daily

import de.puzzlewerk.data.store.PayloadDecodeResult
import de.puzzlewerk.data.store.PayloadMigration
import de.puzzlewerk.data.store.SCORE_POINTS_RANGE
import de.puzzlewerk.data.store.SCORE_STARS_RANGE
import de.puzzlewerk.data.store.StoreSchema
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.score.Score
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ergebnis der Erstlösung EINES Tages im Payload v1. Entry-Array statt
 * dynamischer JSON-Schlüssel (ADR-007, §16.2/2) — Duplikate prüft der Mapper.
 * `epochDay` darf negativ sein (R37).
 */
@Serializable
internal data class DailyResultEntryV1(
    val epochDay: Long,
    val moves: Int,
    val par: Int,
    val points: Int,
    val stars: Int,
)

/**
 * Payload v1 des Daily-Stores (§10.3): Serienzähler plus gespielte Tage
 * (für die R38-Idempotenz von `recordPlayed`); `playedTotal`/`solvedTotal`
 * sind abgeleitet und werden bewusst NICHT redundant persistiert.
 */
@Serializable
internal data class DailyPayloadV1(
    val currentStreak: Int,
    val longestStreak: Int,
    val playedEpochDays: List<Long>,
    val results: List<DailyResultEntryV1>,
)

/**
 * Schema v1 des Daily-Stores (ADR-007, §10.3): strikte Dekodierung,
 * Wertebereichs- und Duplikat-Checks im Mapper.
 */
internal object DailySchemaV1 : StoreSchema<DailyStatsState> {
    override val storeName: String = "daily_stats"
    override val currentVersion: Int = 1
    override val migrations: List<PayloadMigration> = emptyList()

    // Json.Default ist strikt: ignoreUnknownKeys = false, kein Lenient-Modus (S4, ADR-007).
    private val json = Json

    override fun encode(value: DailyStatsState): JsonElement =
        json.encodeToJsonElement(
            DailyPayloadV1(
                currentStreak = value.currentStreak,
                longestStreak = value.longestStreak,
                playedEpochDays = value.playedEpochDays.sorted(),
                results =
                    value.resultByEpochDay.entries
                        .sortedBy { it.key }
                        .map { (epochDay, record) ->
                            DailyResultEntryV1(
                                epochDay = epochDay,
                                moves = record.moves,
                                par = record.par,
                                points = record.score.points,
                                stars = record.score.stars,
                            )
                        },
            ),
        )

    override fun decode(payload: JsonElement): PayloadDecodeResult<DailyStatsState> {
        // Streaming-Decoder statt decodeFromJsonElement: nur er unterscheidet strikt
        // zwischen String- und Zahl-Literalen (S4). runCatching: die Parser-Exception
        // darf nicht in die Diagnose (ihre Message enthält Nutzdaten).
        val dto =
            runCatching { json.decodeFromString<DailyPayloadV1>(payload.toString()) }.getOrNull()
                ?: return PayloadDecodeResult.Invalid("$storeName: Payload verletzt das v1-Schema")
        return mapPayload(dto)
    }

    private fun mapPayload(dto: DailyPayloadV1): PayloadDecodeResult<DailyStatsState> {
        if (dto.currentStreak < 0 || dto.longestStreak < 0) {
            return PayloadDecodeResult.Invalid("$storeName: Serienzähler dürfen nicht negativ sein (§10.3)")
        }
        val played = dto.playedEpochDays.toSet()
        if (played.size != dto.playedEpochDays.size) {
            return PayloadDecodeResult.Invalid("$storeName: doppelter playedEpochDays-Eintrag (§16.2/2)")
        }
        return mapResults(dto, played)
    }

    private fun mapResults(
        dto: DailyPayloadV1,
        played: Set<Long>,
    ): PayloadDecodeResult<DailyStatsState> {
        val results = LinkedHashMap<Long, DailyRecord>(dto.results.size)
        for (entry in dto.results) {
            val violation = entry.rangeViolation()
            if (violation != null) return PayloadDecodeResult.Invalid(violation)
            val record = DailyRecord(entry.moves, entry.par, Score(entry.points, entry.stars))
            if (results.put(entry.epochDay, record) != null) {
                return PayloadDecodeResult.Invalid("$storeName: doppelter results-Eintrag (§16.2/2)")
            }
        }
        return PayloadDecodeResult.Decoded(
            DailyStatsState(
                playedEpochDays = played,
                currentStreak = dto.currentStreak,
                longestStreak = dto.longestStreak,
                resultByEpochDay = results,
            ),
        )
    }

    private fun DailyResultEntryV1.rangeViolation(): String? =
        when {
            moves < 1 -> "$storeName: moves muss >= 1 sein"
            par !in LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR ->
                "$storeName: par außerhalb ${LevelDefinition.MIN_PAR}..${LevelDefinition.MAX_PAR} (§7.1)"
            points !in SCORE_POINTS_RANGE -> "$storeName: points außerhalb $SCORE_POINTS_RANGE (§7.2)"
            stars !in SCORE_STARS_RANGE -> "$storeName: stars außerhalb $SCORE_STARS_RANGE (§7.2)"
            else -> null
        }
}
