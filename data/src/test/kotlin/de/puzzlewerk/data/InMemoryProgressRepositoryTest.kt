package de.puzzlewerk.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InMemoryProgressRepositoryTest {
    @Test
    fun `ungespieltes Level hat keinen Bestwert`() {
        val repository = InMemoryProgressRepository()

        repository.bestScore(levelId = 1).shouldBeNull()
    }

    @Test
    fun `saveResult speichert nur Verbesserungen`() {
        val repository = InMemoryProgressRepository()

        repository.saveResult(levelId = 1, score = 100)
        repository.saveResult(levelId = 1, score = 50)
        repository.bestScore(levelId = 1) shouldBe 100

        repository.saveResult(levelId = 1, score = 150)
        repository.bestScore(levelId = 1) shouldBe 150
    }

    @Test
    fun `ohne Fortschritt ist Level 1 freigeschaltet`() {
        val repository = InMemoryProgressRepository()

        repository.highestUnlockedLevel() shouldBe 1
    }

    @Test
    fun `abgeschlossenes Level schaltet das naechste frei`() {
        val repository = InMemoryProgressRepository()

        repository.saveResult(levelId = 1, score = 10)
        repository.saveResult(levelId = 2, score = 20)

        repository.highestUnlockedLevel() shouldBe 3
    }

    @Test
    fun `ungueltige Parameter sind Programmierfehler`() {
        val repository = InMemoryProgressRepository()

        shouldThrow<IllegalArgumentException> { repository.saveResult(levelId = 0, score = 1) }
        shouldThrow<IllegalArgumentException> { repository.saveResult(levelId = 1, score = -1) }
    }
}
