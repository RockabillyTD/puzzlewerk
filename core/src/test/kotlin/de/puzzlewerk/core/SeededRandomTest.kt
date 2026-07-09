package de.puzzlewerk.core

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import org.junit.jupiter.api.Test

class SeededRandomTest {

    @Test
    fun `gleicher Seed erzeugt identische Sequenz`() {
        val first = SeededRandom(seed = 42L)
        val second = SeededRandom(seed = 42L)

        val firstSequence = List(100) { first.nextInt(1000) }
        val secondSequence = List(100) { second.nextInt(1000) }

        firstSequence shouldContainExactly secondSequence
    }

    @Test
    fun `Werte liegen immer im Bereich 0 bis exklusiv Obergrenze`() {
        val random = SeededRandom(seed = 7L)

        repeat(1000) {
            val value = random.nextInt(10)
            value shouldBeGreaterThanOrEqual 0
            value shouldBeLessThan 10
        }
    }
}
