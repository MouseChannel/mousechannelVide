package dev.neovidecaret

import org.junit.Assert.*
import org.junit.Test

class FrameClockTest {
    @Test fun `sixty fps deadlines do not accumulate millisecond rounding drift`() {
        val clock = FrameClock()
        clock.start(0, 60)
        assertEquals(16, clock.delayMs(0))
        clock.advance(17_000_000)
        assertEquals(16, clock.delayMs(17_000_000))
        clock.advance(34_000_000)
        assertEquals(15, clock.delayMs(34_000_000))
    }

    @Test fun `late frames skip elapsed deadlines without a catch up storm`() {
        val clock = FrameClock()
        clock.start(0, 60)
        clock.advance(100_000_000)
        assertTrue(clock.delayMs(100_000_000) in 1..16)
    }

    @Test fun `legacy low fps values are raised to sixty`() {
        val clock = FrameClock()
        clock.start(0, 30)
        assertEquals(16, clock.delayMs(0))
    }
}
