package de.puzzlewerk.core

import io.kotest.matchers.longs.shouldBeGreaterThan
import org.junit.jupiter.api.Test

class SystemWallClockTest {

    @Test
    fun `liefert plausible Epoch-Zeit`() {
        val clock = SystemWallClock()

        // 2024-01-01T00:00:00Z — jede reale Systemuhr liegt darüber.
        clock.nowMillis() shouldBeGreaterThan 1_704_067_200_000L
    }
}
