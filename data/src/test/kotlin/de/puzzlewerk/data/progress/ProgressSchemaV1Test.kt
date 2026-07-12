package de.puzzlewerk.data.progress

import de.puzzlewerk.data.store.StoreState
import de.puzzlewerk.data.store.decodeStoreText
import de.puzzlewerk.data.store.encodeStoreText
import de.puzzlewerk.data.store.shouldBeCorrupted
import de.puzzlewerk.game.score.Score
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Wertebereichs- und Duplikat-Checks des v1-Mappers (ADR-007, §7.2, §16.2/2). */
class ProgressSchemaV1Test {
    private fun storeText(vararg entries: String): String =
        """{"version":1,"payload":{"entries":[${entries.joinToString(",")}]}}"""

    private fun entry(
        level: Int,
        points: Int = 1500,
        stars: Int = 3,
    ): String = """{"level":$level,"points":$points,"stars":$stars}"""

    @Test
    fun `Golden-Datei v1 dekodiert zum erwarteten Fortschritt`() {
        val golden = requireNotNull(javaClass.getResource("/golden/progress_v1.json")).readText().trim()
        ProgressSchemaV1.decodeStoreText(golden) shouldBe
            StoreState.Loaded(
                CampaignProgress(
                    mapOf(
                        1 to Score(points = 1500, stars = 3),
                        2 to Score(points = 1200, stars = 2),
                        5 to Score(points = 1000, stars = 1),
                    ),
                ),
            )
    }

    @Test
    fun `encode reproduziert die Golden-Datei byte-genau`() {
        val golden = requireNotNull(javaClass.getResource("/golden/progress_v1.json")).readText().trim()
        val domain = (ProgressSchemaV1.decodeStoreText(golden) as StoreState.Loaded).value
        ProgressSchemaV1.encodeStoreText(domain) shouldBe golden
    }

    @Test
    fun `leerer Bestand ist gueltig`() {
        ProgressSchemaV1.decodeStoreText(storeText()) shouldBe StoreState.Loaded(CampaignProgress.EMPTY)
    }

    @Test
    fun `Grenzwerte sind gueltig`() {
        val state =
            ProgressSchemaV1.decodeStoreText(
                storeText(entry(level = 1, points = 1000, stars = 1), entry(level = 50, points = 1500, stars = 3)),
            )
        state shouldBe
            StoreState.Loaded(
                CampaignProgress(
                    mapOf(
                        1 to Score(points = 1000, stars = 1),
                        50 to Score(points = 1500, stars = 3),
                    ),
                ),
            )
    }

    @Test
    fun `Level ausserhalb 1 bis 50 ist Corrupted`() {
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 0))).shouldBeCorrupted()
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 51))).shouldBeCorrupted()
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = -3))).shouldBeCorrupted()
    }

    @Test
    fun `Punkte ausserhalb 1000 bis 1500 sind Corrupted`() {
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 1, points = 999))).shouldBeCorrupted()
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 1, points = 1501))).shouldBeCorrupted()
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 1, points = 0))).shouldBeCorrupted()
    }

    @Test
    fun `Sterne ausserhalb 1 bis 3 sind Corrupted`() {
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 1, stars = 0))).shouldBeCorrupted()
        ProgressSchemaV1.decodeStoreText(storeText(entry(level = 1, stars = 4))).shouldBeCorrupted()
    }

    @Test
    fun `doppelter Level-Eintrag ist Corrupted (Paragraf 16_2)`() {
        ProgressSchemaV1
            .decodeStoreText(storeText(entry(level = 7, points = 1500), entry(level = 7, points = 1000, stars = 1)))
            .shouldBeCorrupted()
        // auch bei identischen Werten: Duplikat bleibt Duplikat
        ProgressSchemaV1
            .decodeStoreText(storeText(entry(level = 7), entry(level = 7)))
            .shouldBeCorrupted()
    }
}
