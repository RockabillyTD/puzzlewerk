package de.puzzlewerk.data.daily

import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.store.StoreState
import de.puzzlewerk.data.store.decodeStoreText
import de.puzzlewerk.data.store.encodeStoreText
import de.puzzlewerk.data.store.shouldBeCorrupted
import de.puzzlewerk.game.score.Score
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Wertebereichs- und Duplikat-Checks des Daily-v1-Mappers (ADR-007, §10.3, §16.2/2). */
class DailySchemaV1Test {
    private fun payloadText(
        currentStreak: Int = 0,
        longestStreak: Int = 0,
        playedEpochDays: String = "[]",
        results: String = "[]",
    ): String =
        """{"version":1,"payload":{"currentStreak":$currentStreak,"longestStreak":$longestStreak,""" +
            """"playedEpochDays":$playedEpochDays,"results":$results}}"""

    private fun resultEntry(
        epochDay: Long,
        moves: Int = 3,
        par: Int = 3,
        points: Int = 1500,
        stars: Int = 3,
    ): String = """{"epochDay":$epochDay,"moves":$moves,"par":$par,"points":$points,"stars":$stars}"""

    private fun goldenText(): String =
        requireNotNull(
            javaClass.getResource("/golden/daily_stats_v1.json"),
        ).readText().trim()

    private fun goldenState(): DailyStatsState =
        DailyStatsState(
            playedEpochDays = setOf(20508L, 20509L, 20510L, 20511L),
            currentStreak = 1,
            longestStreak = 2,
            resultByEpochDay =
                mapOf(
                    20508L to DailyRecord(moves = 3, par = 3, score = Score(points = 1500, stars = 3)),
                    20509L to DailyRecord(moves = 6, par = 3, score = Score(points = 1350, stars = 2)),
                    20511L to DailyRecord(moves = 14, par = 4, score = Score(points = 1000, stars = 1)),
                ),
        )

    @Test
    fun `Golden-Datei v1 dekodiert zum erwarteten Zustand`() {
        DailySchemaV1.decodeStoreText(goldenText()) shouldBe StoreState.Loaded(goldenState())
    }

    @Test
    fun `encode reproduziert die Golden-Datei byte-genau`() {
        DailySchemaV1.encodeStoreText(goldenState()) shouldBe goldenText()
    }

    @Test
    fun `leerer Bestand ist gueltig`() {
        DailySchemaV1.decodeStoreText(payloadText()) shouldBe StoreState.Loaded(DailyStatsState.EMPTY)
    }

    @Test
    fun `negative epochDays sind gueltig (R37)`() {
        val state =
            DailySchemaV1.decodeStoreText(
                payloadText(
                    currentStreak = 1,
                    longestStreak = 1,
                    playedEpochDays = "[-3]",
                    results = "[${resultEntry(-3)}]",
                ),
            )
        state shouldBe
            StoreState.Loaded(
                DailyStatsState(
                    playedEpochDays = setOf(-3L),
                    currentStreak = 1,
                    longestStreak = 1,
                    resultByEpochDay =
                        mapOf(
                            -3L to DailyRecord(moves = 3, par = 3, score = Score(points = 1500, stars = 3)),
                        ),
                ),
            )
    }

    @Test
    fun `negative Serienzaehler sind Corrupted`() {
        DailySchemaV1.decodeStoreText(payloadText(currentStreak = -1)).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(longestStreak = -1)).shouldBeCorrupted()
    }

    @Test
    fun `doppelte playedEpochDays sind Corrupted (Paragraf 16_2)`() {
        DailySchemaV1.decodeStoreText(payloadText(playedEpochDays = "[100,100]")).shouldBeCorrupted()
    }

    @Test
    fun `doppelte results-Eintraege sind Corrupted (Paragraf 16_2)`() {
        DailySchemaV1
            .decodeStoreText(payloadText(results = "[${resultEntry(100)},${resultEntry(100, moves = 5)}]"))
            .shouldBeCorrupted()
    }

    @Test
    fun `moves kleiner 1 ist Corrupted`() {
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, moves = 0)}]")).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, moves = -2)}]")).shouldBeCorrupted()
    }

    @Test
    fun `par ausserhalb 1 bis 14 ist Corrupted`() {
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, par = 0)}]")).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, par = 15)}]")).shouldBeCorrupted()
    }

    @Test
    fun `Punkte oder Sterne ausserhalb Paragraf 7_2 sind Corrupted`() {
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, points = 999)}]")).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, points = 1501)}]")).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, stars = 0)}]")).shouldBeCorrupted()
        DailySchemaV1.decodeStoreText(payloadText(results = "[${resultEntry(100, stars = 4)}]")).shouldBeCorrupted()
    }

    @Test
    fun `unbekannte oder fehlende Payload-Felder sind Corrupted`() {
        DailySchemaV1.decodeStoreText("""{"version":1,"payload":{}}""").shouldBeCorrupted()
        DailySchemaV1
            .decodeStoreText(
                """{"version":1,"payload":{"currentStreak":0,"longestStreak":0,""" +
                    """"playedEpochDays":[],"results":[],"x":1}}""",
            ).shouldBeCorrupted()
    }

    @Test
    fun `neuere Version ist UnsupportedVersion`() {
        DailySchemaV1.decodeStoreText("""{"version":2,"payload":{}}""") shouldBe
            StoreState.Failed(PersistenceFailure.UnsupportedVersion(storedVersion = 2, supportedVersion = 1))
    }
}
