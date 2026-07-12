package de.puzzlewerk.data.store

import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressSchemaV1
import de.puzzlewerk.game.score.Score
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test

/** Envelope-Semantik nach ADR-007, geprüft am Progress-Schema v1. */
class EnvelopeTest {
    private val schema = ProgressSchemaV1

    private val sampleProgress =
        CampaignProgress(
            mapOf(
                1 to Score(points = 1500, stars = 3),
                2 to Score(points = 1400, stars = 2),
            ),
        )

    @Test
    fun `Roundtrip encode decode ist verlustfrei`() {
        schema.decodeStoreText(schema.encodeStoreText(sampleProgress)) shouldBe
            StoreState.Loaded(sampleProgress)
    }

    @Test
    fun `encode ist deterministisch und nach Level sortiert`() {
        val reordered = CampaignProgress(sampleProgress.bestByLevel.entries.reversed().associate { it.key to it.value })
        schema.encodeStoreText(reordered) shouldBe schema.encodeStoreText(sampleProgress)
    }

    @Test
    fun `kaputtes JSON ist Corrupted`() {
        schema.decodeStoreText("{").shouldBeCorrupted()
        schema.decodeStoreText("").shouldBeCorrupted()
        schema.decodeStoreText("kein json").shouldBeCorrupted()
    }

    @Test
    fun `Envelope muss ein Objekt mit genau version und payload sein`() {
        schema.decodeStoreText("[]").shouldBeCorrupted()
        schema.decodeStoreText("42").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":1}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"payload":{"entries":[]}}""").shouldBeCorrupted()
        schema
            .decodeStoreText("""{"version":1,"payload":{"entries":[]},"extra":true}""")
            .shouldBeCorrupted()
    }

    @Test
    fun `version muss eine Ganzzahl sein`() {
        schema.decodeStoreText("""{"version":"1","payload":{"entries":[]}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":1.5,"payload":{"entries":[]}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":null,"payload":{"entries":[]}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":{},"payload":{"entries":[]}}""").shouldBeCorrupted()
    }

    @Test
    fun `version kleiner 1 ist Corrupted`() {
        schema.decodeStoreText("""{"version":0,"payload":{"entries":[]}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":-1,"payload":{"entries":[]}}""").shouldBeCorrupted()
    }

    @Test
    fun `neuere Version ist UnsupportedVersion mit beiden Versionsnummern`() {
        val state = schema.decodeStoreText("""{"version":2,"payload":{"entries":[]}}""")
        state shouldBe
            StoreState.Failed(
                PersistenceFailure.UnsupportedVersion(storedVersion = 2, supportedVersion = 1),
            )
    }

    @Test
    fun `unbekannte Payload-Felder sind Corrupted (ignoreUnknownKeys=false)`() {
        schema
            .decodeStoreText("""{"version":1,"payload":{"entries":[],"bonus":1}}""")
            .shouldBeCorrupted()
    }

    @Test
    fun `fehlende oder falsch typisierte Payload-Felder sind Corrupted`() {
        schema.decodeStoreText("""{"version":1,"payload":{}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":1,"payload":{"entries":{}}}""").shouldBeCorrupted()
        schema.decodeStoreText("""{"version":1,"payload":null}""").shouldBeCorrupted()
        schema
            .decodeStoreText("""{"version":1,"payload":{"entries":[{"level":true,"points":1500,"stars":3}]}}""")
            .shouldBeCorrupted()
    }

    @Test
    fun `quotierte Zahlen im Payload akzeptiert der Codec (dokumentierte kotlinx-Toleranz)`() {
        // kotlinx.serialization dekodiert "1" auch OHNE Lenient-Modus als Int — bewusst
        // festgehalten, damit eine Verhaltensänderung der Bibliothek sichtbar würde.
        // Der Envelope-eigene version-Check bleibt strikt (siehe eigener Test oben).
        val quotedLevel = """{"version":1,"payload":{"entries":[{"level":"1","points":1500,"stars":3}]}}"""
        schema.decodeStoreText(quotedLevel) shouldBe
            StoreState.Loaded(CampaignProgress(mapOf(1 to Score(points = 1500, stars = 3))))
    }

    @Test
    fun `Migrationskette wird von der gespeicherten bis zur aktuellen Version durchlaufen`() {
        // Kunst-Schema mit Version 2: v1-Payloads laufen durch genau eine Migration.
        val migratingSchema =
            object : StoreSchema<CampaignProgress> by ProgressSchemaV1 {
                override val currentVersion: Int = 2
                override val migrations: List<PayloadMigration> =
                    listOf(
                        PayloadMigration { payload -> renameEntriesField(payload) },
                    )
            }
        val v1Text = """{"version":1,"payload":{"eintraege":[{"level":1,"points":1500,"stars":3}]}}"""
        migratingSchema.decodeStoreText(v1Text) shouldBe
            StoreState.Loaded(CampaignProgress(mapOf(1 to Score(points = 1500, stars = 3))))
    }

    private fun renameEntriesField(payload: JsonElement): JsonElement =
        buildJsonObject {
            put("entries", (payload as JsonObject).getValue("eintraege"))
        }
}

internal fun StoreState<*>.shouldBeCorrupted() {
    shouldBeInstanceOf<StoreState.Failed>().failure.shouldBeInstanceOf<PersistenceFailure.Corrupted>()
}
