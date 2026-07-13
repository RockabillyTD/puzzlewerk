package de.puzzlewerk.data.daily

import de.puzzlewerk.game.score.Score
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Serien-Semantik §10.3: Serie, verpasster Tag, R38-Idempotenz, Datum rückwärts. */
class DailyStatsStateTest {
    // §7.2-konsistente Fixture: Punkte/Sterne folgen aus moves und par.
    private fun record(moves: Int = 3): DailyRecord = consistentDailyRecord(moves = moves, par = 3)

    @Test
    fun `Erstloesung startet die Serie bei 1`() {
        val state = DailyStatsState.EMPTY.withSolved(100, record())
        state.currentStreak shouldBe 1
        state.longestStreak shouldBe 1
        state.toStats().solvedTotal shouldBe 1
    }

    @Test
    fun `aufeinanderfolgende Tage verlaengern die Serie`() {
        val state =
            DailyStatsState.EMPTY
                .withSolved(100, record())
                .withSolved(101, record())
                .withSolved(102, record())
        state.currentStreak shouldBe 3
        state.longestStreak shouldBe 3
    }

    @Test
    fun `verpasster Tag setzt die Serie beim naechsten Loesen auf 1, laengsteSerie bleibt`() {
        val state =
            DailyStatsState.EMPTY
                .withSolved(100, record())
                .withSolved(101, record())
                .withSolved(103, record())
        state.currentStreak shouldBe 1
        state.longestStreak shouldBe 2
        state.toStats().solvedTotal shouldBe 3
    }

    @Test
    fun `frueheres Datum (R38) laesst die Serie unveraendert, Ergebnis zaehlt trotzdem`() {
        val state =
            DailyStatsState.EMPTY
                .withSolved(100, record())
                .withSolved(101, record())
                .withSolved(99, record())
        state.currentStreak shouldBe 2
        state.longestStreak shouldBe 2
        state.toStats().solvedTotal shouldBe 3
        state.resultByEpochDay.keys shouldBe setOf(99L, 100L, 101L)
    }

    @Test
    fun `Luecke fuellen zaehlt nicht rueckwirkend als laengere Serie (Paragraf 10_3)`() {
        val state =
            DailyStatsState.EMPTY
                .withSolved(5, record())
                .withSolved(7, record())
                .withSolved(6, record())
        // Serie endet am zuletzt GELOESTEN Tag (7); das nachgeholte Datum ändert sie nicht.
        state.currentStreak shouldBe 1
        state.longestStreak shouldBe 1
    }

    @Test
    fun `Zweitloesung desselben Tages ist ein No-op (R38)`() {
        val once = DailyStatsState.EMPTY.withSolved(100, record())
        val twice = once.withSolved(100, record(moves = 9))
        twice shouldBe once
    }

    @Test
    fun `negative epochDays sind gueltige Kalendertage (R37)`() {
        val state =
            DailyStatsState.EMPTY
                .withSolved(-5, record())
                .withSolved(-4, record())
        state.currentStreak shouldBe 2
        state.longestStreak shouldBe 2
    }

    @Test
    fun `Serie saettigt bei Int MAX_VALUE statt ueberzulaufen`() {
        val atMax =
            DailyStatsState(
                playedEpochDays = emptySet(),
                currentStreak = Int.MAX_VALUE,
                longestStreak = Int.MAX_VALUE,
                resultByEpochDay = mapOf(100L to record()),
            )
        val next = atMax.withSolved(101, record())
        next.currentStreak shouldBe Int.MAX_VALUE
        next.longestStreak shouldBe Int.MAX_VALUE
        next.toStats().solvedTotal shouldBe 2
    }

    @Test
    fun `withPlayed ist idempotent pro Datum (R38)`() {
        val state =
            DailyStatsState.EMPTY
                .withPlayed(100)
                .withPlayed(100)
                .withPlayed(101)
        state.toStats().playedTotal shouldBe 2
    }

    @Test
    fun `toStats leitet playedTotal und solvedTotal aus den Mengen ab`() {
        val state =
            DailyStatsState.EMPTY
                .withPlayed(100)
                .withSolved(100, record())
        val stats = state.toStats()
        stats shouldBe
            DailyStats(
                playedTotal = 1,
                solvedTotal = 1,
                currentStreak = 1,
                longestStreak = 1,
                resultByEpochDay = mapOf(100L to record()),
            )
    }

    @Test
    fun `ungueltige DailyRecords sind Programmierfehler (Regel C3)`() {
        shouldThrow<IllegalArgumentException> {
            requireValidDailyRecord(DailyRecord(moves = 0, par = 3, score = Score(points = 1500, stars = 3)))
        }
        shouldThrow<IllegalArgumentException> {
            requireValidDailyRecord(DailyRecord(moves = 3, par = 0, score = Score(points = 1500, stars = 3)))
        }
        shouldThrow<IllegalArgumentException> {
            requireValidDailyRecord(DailyRecord(moves = 3, par = 15, score = Score(points = 1500, stars = 3)))
        }
        shouldThrow<IllegalArgumentException> {
            requireValidDailyRecord(DailyRecord(moves = 3, par = 3, score = Score(points = 999, stars = 1)))
        }
    }

    @Test
    fun `gueltige Grenzwerte passieren die Vorbedingungen`() {
        // par=1/moves=1 ⇒ 1500/3; par=14/moves=99 ⇒ 1000/1 (§7.2-Grenzen beider Bereiche)
        requireValidDailyRecord(consistentDailyRecord(moves = 1, par = 1))
        requireValidDailyRecord(consistentDailyRecord(moves = 99, par = 14))
    }
}
